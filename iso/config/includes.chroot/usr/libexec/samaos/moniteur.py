#!/usr/bin/python3
"""Moniteur système de Sama (maquette app-09) : ce que consomme chaque application, et l'ordinateur en entier.

Chaque application de la session tourne dans son unité systemd (app-<application>@….service) : son groupe de
processus (cgroup) donne son temps de processeur ; sa mémoire est celle, privée, de ses processus. Tout le reste
(session de Plasma, services) forme « Services système ». Les débits (processeur, disque, réseau) se calculent
depuis l'appel précédent, noté dans $XDG_RUNTIME_DIR/samaos-moniteur.json.

  moniteur.py etat                      JSON : processeur, mémoire, disque, réseau, applications, services
  moniteur.py data                      JSON : data d'aujourd'hui et connexion (plus lent : toutes les 30 s)
  moniteur.py details <appli.desktop>|services   JSON : processus de l'application (ou des services)
  moniteur.py quitter <appli.desktop>   forcer l'application à quitter (toutes ses unités)
  moniteur.py demarrage                 JSON : applications ouvertes à l'ouverture de session
  moniteur.py demarrage-regler <fichier.desktop> true|false
"""
import configparser
import glob
import json
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
UID = os.getuid()
SESSION = "/sys/fs/cgroup/user.slice/user-%d.slice/user@%d.service" % (UID, UID)
ETAT = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "samaos-moniteur.json")
PAGE = os.sysconf("SC_PAGE_SIZE")
TIC = os.sysconf("SC_CLK_TCK")
NCPU = os.cpu_count() or 1
IGNOREES = ("lo", "tun", "tap", "wg", "virbr", "vnet", "veth", "docker", "br-", "vboxnet", "zt")
DOSSIERS_APPLIS = [os.path.expanduser("~/.local/share/applications"), "/usr/local/share/applications", "/usr/share/applications"]
AUTOSTART = [os.path.expanduser("~/.config/autostart"), "/etc/xdg/autostart"]
# Fenêtres et services de Sama lancés comme des applications, comptés avec le système
INTERNES = ("samaos-bienvenue", "samaos-restauration", "samaos-courant", "samaos-batterie", "samaos-capture")


def lire(chemin, defaut=""):
    try:
        with open(chemin) as f:
            return f.read()
    except OSError:
        return defaut


def desktop(identifiant, dossiers=DOSSIERS_APPLIS):
    """Le groupe [Desktop Entry] d'un fichier .desktop (le premier trouvé dans les dossiers), ou None."""
    for d in dossiers:
        chemin = os.path.join(d, identifiant)
        if os.path.isfile(chemin):
            infos, dans = {"chemin": chemin}, False
            for ligne in open(chemin, encoding="utf-8", errors="replace"):
                ligne = ligne.strip()
                if ligne.startswith("["):
                    dans = ligne == "[Desktop Entry]"
                elif dans and "=" in ligne:
                    cle, valeur = ligne.split("=", 1)
                    infos.setdefault(cle.strip(), valeur.strip())
            return infos
    return None


def nom_desktop(d, defaut):
    return d.get("Name[fr]") or d.get("Name") or defaut


def application_de(unite):
    """« app-samaos\\x2dfichiers@123.service » → identifiant du .desktop (« samaos-fichiers.desktop »)."""
    m = re.match(r"app-(.+?)@[^@/]*\.service$", unite) or re.match(r"app-(.+?)-[0-9a-f]+\.scope$", unite)
    if not m:
        return ""
    return re.sub(r"\\x([0-9a-f]{2})", lambda x: chr(int(x.group(1), 16)), m.group(1)) + ".desktop"


def appli_visible(cgroup):
    """L'application de la Cour à laquelle appartient un processus (son .desktop), ou "" (services du système)."""
    if "/app.slice/" not in cgroup:
        return ""
    identifiant = application_de(cgroup.rsplit("/", 1)[-1])
    d = desktop(identifiant) if identifiant else None
    if not d or d.get("NoDisplay") == "true" or identifiant.startswith(INTERNES):
        return ""
    return identifiant


# ——— Processus ———
def processus():
    """{pid: {nom, cgroup, memoire (privée), tics}} pour les processus du système (pas les fils du noyau)."""
    res = {}
    for p in os.listdir("/proc"):
        if not p.isdigit():
            continue
        try:
            stat = open("/proc/%s/stat" % p).read()
            fin = stat.rindex(")")
            champs = stat[fin + 2:].split()
            if int(champs[1]) == 2 or p == "2":           # (fils du noyau : parent kthreadd)
                continue
            statm = open("/proc/%s/statm" % p).read().split()
            cgroup = open("/proc/%s/cgroup" % p).read().strip().split("::", 1)[-1]
        except (OSError, ValueError, IndexError):
            continue
        res[int(p)] = {"nom": stat[stat.index("(") + 1:fin], "cgroup": cgroup,
                       "memoire": max(0, int(statm[1]) - int(statm[2])) * PAGE,
                       "tics": int(champs[11]) + int(champs[12]), "debut": int(champs[19])}
    return res


def cpu_unite(chemin):
    m = re.search(r"usage_usec (\d+)", lire(os.path.join(chemin, "cpu.stat")))
    return int(m.group(1)) if m else 0


def mesures():
    """Compteurs bruts à un instant : processeur, disques, réseau, unités d'applications."""
    ligne = lire("/proc/stat").splitlines()[0].split()[1:]
    valeurs = [int(x) for x in ligne[:8]]
    inactif = valeurs[3] + valeurs[4]
    disques = [0, 0]
    for l in lire("/proc/diskstats").splitlines():
        c = l.split()
        if len(c) > 9 and os.path.exists("/sys/block/%s/device" % c[2]):
            disques[0] += int(c[5]) * 512
            disques[1] += int(c[9]) * 512
    reseau = [0, 0]
    for l in lire("/proc/net/dev").splitlines()[2:]:
        nom, _, reste = l.partition(":")
        if nom.strip().startswith(IGNOREES):
            continue
        c = reste.split()
        reseau[0] += int(c[0])
        reseau[1] += int(c[8])
    unites = {}
    # (les unités peuvent être rangées dans une tranche par application : app.slice/app-….slice/app-…@….service)
    for dossier, sous, _ in os.walk(os.path.join(SESSION, "app.slice")):
        for nom in sous:
            if nom.startswith("app-") and nom.endswith((".service", ".scope")):
                unites[nom] = cpu_unite(os.path.join(dossier, nom))
    return {"t": time.monotonic(), "cpu": [sum(valeurs), inactif], "disques": disques, "reseau": reseau, "unites": unites}


def octets(meminfo, cle):
    m = re.search(r"^%s:\s+(\d+) kB" % cle, meminfo, re.M)
    return int(m.group(1)) * 1024 if m else 0


def energie(pourcentage):
    """Effet sur la batterie, d'après la part du processeur."""
    if pourcentage < 0.05:
        return "Nulle"
    if pourcentage < 5:
        return "Faible"
    if pourcentage < 20:
        return "Moyenne"
    return "Élevée"


def frequence_temperature():
    freqs = [int(lire(f, "0").strip() or 0) for f in glob.glob("/sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq")]
    frequence = round(sum(freqs) / len(freqs) / 1e6, 1) if freqs and sum(freqs) else 0
    temperature, prefere = 0, ("x86_pkg_temp", "cpu", "soc", "coretemp", "k10temp", "acpitz")
    zones = sorted(glob.glob("/sys/class/thermal/thermal_zone*"),
                   key=lambda z: next((i for i, p in enumerate(prefere) if lire(z + "/type").strip().startswith(p)), 99))
    for z in zones:
        try:
            t = int(lire(z + "/temp").strip()) / 1000
        except ValueError:
            continue
        if 0 < t < 130:
            temperature = round(t)
            break
    return frequence, temperature


def etat():
    avant = {}
    try:
        avant = json.load(open(ETAT))
    except (OSError, ValueError):
        pass
    maintenant = mesures()
    if not avant or not 0.2 < maintenant["t"] - avant.get("t", 0) < 15:
        avant = maintenant
        time.sleep(0.5)
        maintenant = mesures()
    with open(ETAT, "w") as f:
        json.dump(maintenant, f)
    duree = max(0.1, maintenant["t"] - avant["t"])
    total = maintenant["cpu"][0] - avant["cpu"][0]
    occupe = total - (maintenant["cpu"][1] - avant["cpu"][1])
    cpu = max(0.0, min(100.0, 100.0 * occupe / total)) if total > 0 else 0.0

    procs = processus()
    # Une ligne par application, même si elle a plusieurs unités (Konsole range le shell de chaque onglet à part)
    groupes, services = {}, {"processus": 0, "memoire": 0, "noms": set()}
    for pid, p in procs.items():
        unite = p["cgroup"].rsplit("/", 1)[-1]
        identifiant = appli_visible(p["cgroup"])
        if not identifiant:
            services["processus"] += 1
            services["memoire"] += p["memoire"]
            services["noms"].add(p["nom"])
            continue
        g = groupes.get(identifiant)
        if not g:
            d = desktop(identifiant)
            g = groupes[identifiant] = {"cle": identifiant, "nom": nom_desktop(d, identifiant), "icone": d.get("Icon", ""),
                                        "processus": 0, "memoire": 0, "noms": set(), "unites": set()}
        g["unites"].add(unite)
        g["processus"] += 1
        g["memoire"] += p["memoire"]
        g["noms"].add(p["nom"])
    applications = []
    somme = 0.0
    for g in groupes.values():
        usage = sum(maintenant["unites"].get(u, 0) - avant["unites"].get(u, maintenant["unites"].get(u, 0)) for u in g["unites"])
        part = max(0.0, min(100.0, 100.0 * usage / (duree * 1e6 * NCPU)))
        somme += part
        g.update(cpu=round(part, 1), energie=energie(part), noms=sorted(g["noms"]), unites=sorted(g["unites"]),
                 detail="%d processus" % g["processus"] if g["processus"] > 1 else "")
        applications.append(g)
    applications.sort(key=lambda g: -g["cpu"])
    part_services = max(0.0, cpu - somme)

    meminfo = lire("/proc/meminfo")
    memtotal, disponible = octets(meminfo, "MemTotal"), octets(meminfo, "MemAvailable")
    cache = octets(meminfo, "Cached") + octets(meminfo, "Buffers") + octets(meminfo, "SReclaimable") - octets(meminfo, "Shmem")
    v = os.statvfs("/")
    frequence, temperature = frequence_temperature()
    print(json.dumps({
        "processeur": {"pourcentage": round(cpu, 1), "coeurs": NCPU, "frequence": frequence, "temperature": temperature},
        "memoire": {"utilisee": memtotal - disponible, "totale": memtotal, "cache": max(0, cache)},
        "disque": {"total": v.f_blocks * v.f_frsize, "libre": v.f_bavail * v.f_frsize,
                   "lecture": max(0, (maintenant["disques"][0] - avant["disques"][0]) / duree),
                   "ecriture": max(0, (maintenant["disques"][1] - avant["disques"][1]) / duree)},
        "reseau": {"recu": max(0, (maintenant["reseau"][0] - avant["reseau"][0]) / duree),
                   "envoye": max(0, (maintenant["reseau"][1] - avant["reseau"][1]) / duree)},
        "applications": applications,
        "services": {"processus": services["processus"], "memoire": services["memoire"], "cpu": round(part_services, 1),
                     "energie": energie(part_services), "noms": sorted(services["noms"])},
        "processus": len(procs),
        "depuis": int(float(lire("/proc/uptime", "0").split()[0])),
    }, ensure_ascii=False))


def data():
    """Data d'aujourd'hui (data.py), économie de data, connexion active."""
    res = {}
    try:
        p = subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "data.py"), "etat"],
                           capture_output=True, text=True, timeout=15)
        d = json.loads(p.stdout)
        res["mo"] = d.get("aujourdhuiMo", d.get("allumageMo", 0)) if d.get("source") == "vnstat" else d.get("allumageMo", 0)
        res["source"] = d.get("source", "allumage")
        res["jours"] = d.get("jours", [])
        res["utiliseMo"], res["forfaitMo"] = d.get("utiliseMo", 0), d.get("forfaitMo", 0)
    except (OSError, ValueError, subprocess.SubprocessError):
        res.update(mo=0, source="allumage", jours=[])
    c = configparser.RawConfigParser(strict=False)
    c.read(os.path.expanduser("~/.config/samaosrc"))
    res["economie"] = c.get("Data", "economie", fallback="false").strip() == "true"
    try:
        r = subprocess.run(["nmcli", "-t", "-f", "NAME,TYPE,DEVICE", "connection", "show", "--active"],
                           capture_output=True, text=True, timeout=5).stdout
    except (OSError, subprocess.SubprocessError):
        r = ""
    res["connexion"] = ""
    for l in r.splitlines():
        champs = re.split(r"(?<!\\):", l)
        if len(champs) < 3 or champs[1] == "loopback":
            continue
        nom, genre = champs[0].replace("\\:", ":"), champs[1]
        res["connexion"] = ("Wi-Fi « %s »" % nom if genre == "802-11-wireless" else "Réseau mobile" if genre in ("gsm", "cdma")
                            else "Partage Bluetooth" if genre == "bluetooth" else "Câble réseau" if genre == "802-3-ethernet" else nom)
        break
    print(json.dumps(res, ensure_ascii=False))


def details(cle):
    """Les processus d'une application (identifiant .desktop, ou « services »), avec leur part du processeur."""
    a = processus()
    t0 = time.monotonic()
    time.sleep(0.5)
    b = processus()
    duree = time.monotonic() - t0
    debut_systeme = time.time() - float(lire("/proc/uptime", "0").split()[0])
    res = []
    for pid, p in b.items():
        if appli_visible(p["cgroup"]) != ("" if cle == "services" else cle):
            continue
        tics = p["tics"] - a.get(pid, p)["tics"]
        res.append({"pid": pid, "nom": p["nom"], "memoire": p["memoire"],
                    "cpu": round(max(0.0, 100.0 * tics / TIC / duree / NCPU), 1),
                    "debut": int(debut_systeme + p["debut"] / TIC)})
    res.sort(key=lambda p: (-p["cpu"], -p["memoire"]))
    print(json.dumps(res, ensure_ascii=False))


def quitter(cle):
    """Forcer à quitter : tous les processus de l'application, dans toutes ses unités de la session."""
    if not cle.endswith(".desktop") or "/" in cle:
        sys.exit(2)
    unites = set()
    for pid, p in processus().items():
        if appli_visible(p["cgroup"]) == cle:
            unites.add(p["cgroup"].rsplit("/", 1)[-1])
    ok = True
    for u in unites:
        ok &= subprocess.run(["systemctl", "--user", "kill", "--signal=SIGKILL", u], capture_output=True).returncode == 0
    sys.exit(0 if ok and unites else 1)


# ——— Démarrage ———
def condition_vraie(cond):
    """X-KDE-autostart-condition=fichier:groupe:clé:défaut"""
    morceaux = cond.split(":")
    if len(morceaux) != 4:
        return True
    fichier, groupe, cle, defaut = morceaux
    c = configparser.RawConfigParser(strict=False)
    c.optionxform = str
    c.read(["/etc/xdg/" + fichier, os.path.expanduser("~/.config/" + fichier)])
    return c.get(groupe, cle, fallback=defaut).strip().lower() == "true"


def demarrage():
    vus, res = set(), []
    for dossier in AUTOSTART:
        for chemin in sorted(glob.glob(os.path.join(dossier, "*.desktop"))):
            fichier = os.path.basename(chemin)
            if fichier in vus:
                continue
            vus.add(fichier)
            d = desktop(fichier, AUTOSTART)
            if not d or d.get("NoDisplay") == "true" or fichier.startswith("samaos-"):
                continue
            seulement = [x for x in d.get("OnlyShowIn", "").split(";") if x]
            if (seulement and "KDE" not in seulement) or "KDE" in d.get("NotShowIn", "").split(";"):
                continue
            if not condition_vraie(d.get("X-KDE-autostart-condition", "")):
                continue
            # Icône et nom : ceux de l'application, si elle est connue
            appli = desktop(fichier) or {}
            res.append({"fichier": fichier, "nom": nom_desktop(appli or d, fichier), "icone": appli.get("Icon") or d.get("Icon", ""),
                        "detail": d.get("Comment[fr]") or d.get("Comment", ""), "actif": d.get("Hidden") != "true",
                        "ajoute": d["chemin"].startswith(AUTOSTART[0]) and not os.path.exists(os.path.join(AUTOSTART[1], fichier))})
    res.sort(key=lambda x: x["nom"].lower())
    print(json.dumps(res, ensure_ascii=False))


def demarrage_regler(fichier, actif):
    if "/" in fichier or not fichier.endswith(".desktop"):
        sys.exit(2)
    perso = os.path.join(AUTOSTART[0], fichier)
    systeme = os.path.join(AUTOSTART[1], fichier)
    if actif and os.path.exists(systeme) and os.path.exists(perso):
        os.remove(perso)                                  # (la version du système reprend)
        return
    source = perso if os.path.exists(perso) else systeme
    if not os.path.exists(source):
        sys.exit(1)
    lignes = [l for l in open(source, encoding="utf-8", errors="replace").read().splitlines() if not l.startswith("Hidden=")]
    i = lignes.index("[Desktop Entry]") + 1 if "[Desktop Entry]" in lignes else 0
    lignes.insert(i, "Hidden=" + ("false" if actif else "true"))
    os.makedirs(AUTOSTART[0], exist_ok=True)
    with open(perso, "w", encoding="utf-8") as f:
        f.write("\n".join(lignes) + "\n")


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    if action == "etat":
        etat()
    elif action == "data":
        data()
    elif action == "details" and len(sys.argv) > 2:
        details(sys.argv[2])
    elif action == "quitter" and len(sys.argv) > 2:
        quitter(sys.argv[2])
    elif action == "demarrage":
        demarrage()
    elif action == "demarrage-regler" and len(sys.argv) > 3:
        demarrage_regler(sys.argv[2], sys.argv[3] == "true")
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
