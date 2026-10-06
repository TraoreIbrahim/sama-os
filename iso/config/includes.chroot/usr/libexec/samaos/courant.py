#!/usr/bin/python3
"""Retour du courant (maquette ale-04). Toutes les 30 secondes, Sama note ce qui est ouvert : les applications lancées
(chacune a son unité systemd « app-<application>@… » dans la session) et les fichiers qu'elles ont reçus. À la fin
normale de la session (déconnexion, extinction), la note est marquée « propre ». Si, à l'ouverture de session
suivante, l'ordinateur a redémarré sans que la session se soit fermée (coupure de courant, batterie vide), la carte
« Le courant a été coupé à 11:05 » propose de tout rouvrir ; les documents que Sama Docs, Sheet et Présentations
avaient gardés (copies de secours de LibreOffice, chaque minute) sont rouverts avec eux.

  courant.py demarrer      ouverture de session (samaos-courant.desktop) : carte s'il y a eu coupure, puis veille
  courant.py bilan         JSON de la carte : {heure, applis, documents}
  courant.py rouvrir       relance les applications (chacune dans sa propre unité, comme depuis la Natte)
  courant.py vu            la carte a été vue : ne plus la proposer
"""
import json
import os
import re
import signal
import subprocess
import sys
import time
import uuid

ETAT = os.path.join(os.environ.get("XDG_STATE_HOME", os.path.expanduser("~/.local/state")), "samaos")
SESSION = os.path.join(ETAT, "session.json")
COUPURE = os.path.join(ETAT, "coupure.json")
LO = os.path.expanduser("~/.config/libreoffice/4/user/registrymodifications.xcu")
INTERVALLE = 30
DOSSIERS_APPLIS = [os.path.expanduser("~/.local/share/applications"), "/usr/local/share/applications", "/usr/share/applications"]
# Applications de LibreOffice, sous leur nom Sama
MODULES = {"com.sun.star.text.TextDocument": ("Sama Docs", "samaos-docs.desktop", "/usr/share/samaos/icones/docs.svg"),
           "com.sun.star.sheet.SpreadsheetDocument": ("Sama Sheet", "samaos-sheet.desktop", "/usr/share/samaos/icones/sheet.svg"),
           "com.sun.star.presentation.PresentationDocument": ("Sama Présentations", "samaos-presentations.desktop",
                                                              "/usr/share/samaos/icones/presentations.svg")}
LIBREOFFICE = {"samaos-docs.desktop", "samaos-presentations.desktop", "libreoffice-writer.desktop",
               "libreoffice-calc.desktop", "libreoffice-impress.desktop", "libreoffice-startcenter.desktop"}


def ecrire(chemin, donnees):
    """Écrit tout de suite sur le disque (le courant peut sauter la seconde d'après)."""
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    temporaire = chemin + ".nouveau"
    with open(temporaire, "w") as f:
        json.dump(donnees, f, ensure_ascii=False)
        f.flush()
        os.fsync(f.fileno())
    os.replace(temporaire, chemin)
    d = os.open(os.path.dirname(chemin), os.O_RDONLY)
    try:
        os.fsync(d)
    finally:
        os.close(d)


def lire(chemin):
    try:
        with open(chemin) as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def demarrage_id():
    try:
        return open("/proc/sys/kernel/random/boot_id").read().strip()
    except OSError:
        return ""


def desktop(identifiant):
    for d in DOSSIERS_APPLIS:
        chemin = os.path.join(d, identifiant)
        if os.path.isfile(chemin):
            infos, dans = {"chemin": chemin}, False
            for ligne in open(chemin, encoding="utf-8", errors="replace"):
                ligne = ligne.strip()
                if ligne.startswith("["):
                    dans = ligne == "[Desktop Entry]"
                elif dans and "=" in ligne:
                    cle, valeur = ligne.split("=", 1)
                    infos.setdefault(cle, valeur)
            return infos
    return None


def applis_ouvertes():
    """Applications lancées dans la session (pas celles du démarrage automatique) et leurs fichiers."""
    r = subprocess.run(["systemctl", "--user", "list-units", "--no-legend", "--plain", "--state=running", "app-*@*.service"],
                       capture_output=True, text=True)
    unites = [l.split()[0] for l in r.stdout.splitlines() if l.strip() and "@autostart" not in l.split()[0]]
    if not unites:
        return []
    pids = {u: subprocess.run(["systemctl", "--user", "show", "-p", "MainPID", "--value", u], capture_output=True,
                              text=True).stdout.strip() for u in unites}
    res, vus = [], set()
    for unite in unites:
        m = re.match(r"app-(.+)@[^@]+\.service$", unite)
        if not m:
            continue
        identifiant = re.sub(r"\\x([0-9a-f]{2})", lambda x: chr(int(x.group(1), 16)), m.group(1)) + ".desktop"
        d = desktop(identifiant)
        if not d or identifiant in vus or identifiant.startswith(("samaos-bienvenue", "samaos-restauration", "samaos-courant")):
            continue
        vus.add(identifiant)
        fichiers = []
        try:
            args = open("/proc/%s/cmdline" % pids.get(unite, "0"), "rb").read().decode(errors="replace").split("\0")[1:]
        except OSError:
            args = []
        for a in args:
            if a.startswith("file://"):
                a = re.sub(r"%([0-9A-Fa-f]{2})", lambda x: chr(int(x.group(1), 16)), a[7:])
            if a.startswith("/") and not a.startswith(("/usr/", "/etc/", "/proc/", "/tmp/")) and os.path.exists(a):
                fichiers.append(a)
        res.append({"id": identifiant, "nom": d.get("Name[fr]") or d.get("Name", identifiant), "icone": d.get("Icon", ""),
                    "fichiers": fichiers[:10]})
    return res


def documents_secours():
    """Documents que LibreOffice a gardés en secours (liste de récupération), du plus récent au plus ancien."""
    try:
        texte = open(LO, encoding="utf-8", errors="replace").read()
    except OSError:
        return []
    # <item oor:path="/org.openoffice.Office.Recovery/RecoveryList"><node oor:name="recovery_item_1" …>
    #   <prop oor:name="Title" …><value>Sans nom 1</value></prop>…</node></item>
    entrees = {}
    for nom, corps in re.findall(r'<node oor:name="(recovery_item_[^"]+)"[^>]*>(.*?)</node>', texte, re.S):
        entrees[nom] = dict(re.findall(r'<prop oor:name="(\w+)"[^>]*><value>([^<]*)</value>', corps))
    res = []
    for e in entrees.values():
        secours = re.sub(r"%([0-9A-Fa-f]{2})", lambda x: chr(int(x.group(1), 16)), e.get("TempURL", "").replace("file://", ""))
        if not e.get("Title") or not secours or not os.path.exists(secours):
            continue
        appli = MODULES.get(e.get("Module", ""), ("Sama Docs", "samaos-docs.desktop", "/usr/share/samaos/icones/docs.svg"))
        etat = int(e.get("DocumentState", "0") or 0)
        res.append({"titre": e["Title"], "appli": appli[0], "id": appli[1], "icone": appli[2],
                    "quand": int(os.path.getmtime(secours)), "nonEnregistre": bool(etat & 1) or not e.get("OriginalURL")})
    return sorted(res, key=lambda d: d["quand"], reverse=True)


def bilan():
    c = lire(COUPURE)
    if not c or c.get("vu"):
        print("{}")
        return
    docs = documents_secours()
    applis = [a for a in c.get("applis", []) if not (docs and a["id"] in LIBREOFFICE)]
    print(json.dumps({"heure": c.get("heure", 0), "applis": applis, "documents": docs}, ensure_ascii=False))


def lancer(identifiant, fichiers, options=()):
    """Comme depuis la Natte : l'application dans sa propre unité app-<id>@….service (elle survit à la carte)."""
    d = desktop(identifiant)
    if not d or not d.get("Exec"):
        return
    commande = []
    for morceau in d["Exec"].split():
        if morceau in ("%f", "%u"):
            commande += fichiers[:1]
        elif morceau in ("%F", "%U"):
            commande += fichiers
        elif not re.fullmatch(r"%[ickdDnNvm]", morceau):
            commande.append(morceau.replace("%%", "%"))
    commande[1:1] = list(options)
    echappe = subprocess.run(["systemd-escape", identifiant[:-len(".desktop")]], capture_output=True, text=True).stdout.strip()
    subprocess.run(["systemd-run", "--user", "--quiet", "--collect", "--slice=app.slice",
                    "--unit=app-%s@%s.service" % (echappe, uuid.uuid4().hex), "-p", "ExitType=cgroup", "--"] + commande,
                   check=False)


def fermeture_propre_chromium():
    """Griot (Chromium) restaure ses onglets avec --restore-last-session : sa bulle « Restore pages? » est inutile."""
    chemin = os.path.expanduser("~/.config/chromium/Default/Preferences")
    try:
        with open(chemin) as f:
            prefs = json.load(f)
        prefs.setdefault("profile", {}).update({"exit_type": "Normal", "exited_cleanly": True})
        with open(chemin + ".nouveau", "w") as f:
            json.dump(prefs, f)
        os.replace(chemin + ".nouveau", chemin)
    except (OSError, ValueError):
        pass


def rouvrir():
    c = lire(COUPURE) or {}
    deja = set()
    if documents_secours():                      # (Sama Docs rouvre ses documents lui-même, une fois)
        lancer("samaos-docs.desktop", [])
        deja |= LIBREOFFICE
    for a in c.get("applis", []):
        if a["id"] in deja:
            continue
        deja.add(a["id"])
        d = desktop(a["id"]) or {}
        options = []
        if "chromium" in d.get("Exec", ""):
            options = ["--restore-last-session"]
            fermeture_propre_chromium()          # (sinon Griot pose en plus sa propre question, en anglais)
        lancer(a["id"], a.get("fichiers", []), options)
    vu()


def vu():
    c = lire(COUPURE)
    if c is not None:
        c["vu"] = True
        ecrire(COUPURE, c)


def demarrer():
    import pwd
    if pwd.getpwuid(os.getuid()).pw_name == "sama-invite":
        return                                   # (session invitée : tout est effacé à la déconnexion)
    precedente = lire(SESSION)
    # Redémarré sans que la session se soit fermée : coupure de courant (ou batterie vide)
    if precedente and not precedente.get("propre") and precedente.get("demarrage") != demarrage_id() \
            and time.time() - precedente.get("maj", 0) < 7 * 86400 and (precedente.get("applis") or documents_secours()):
        ecrire(COUPURE, {"heure": precedente.get("maj", 0), "applis": precedente.get("applis", [])})
        time.sleep(4)                            # (laisser le bureau s'installer)
        donnees = subprocess.run([sys.executable, __file__, "bilan"], capture_output=True, text=True).stdout.strip()
        if donnees and donnees != "{}":
            subprocess.Popen(["qml6", "/usr/libexec/samaos/courant/Courant.qml", "--", donnees],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    veiller()


def veiller():
    """Note ce qui est ouvert toutes les 30 s ; « propre » à la fin normale de la session."""
    def fin(*_):
        note = lire(SESSION) or {}
        note.update({"propre": True, "maj": int(time.time())})
        ecrire(SESSION, note)
        sys.exit(0)
    signal.signal(signal.SIGTERM, fin)
    signal.signal(signal.SIGHUP, fin)
    affichage = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/run/user/%d" % os.getuid()), os.environ.get("WAYLAND_DISPLAY", "wayland-0"))
    while True:
        if not os.path.exists(affichage):
            fin()                                # (session graphique fermée)
        ecrire(SESSION, {"demarrage": demarrage_id(), "maj": int(time.time()), "propre": False, "applis": applis_ouvertes()})
        time.sleep(INTERVALLE)


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    {"demarrer": demarrer, "bilan": bilan, "rouvrir": rouvrir, "vu": vu, "veiller": veiller}.get(
        action, lambda: (print(__doc__, file=sys.stderr), sys.exit(1)))()
