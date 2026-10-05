#!/usr/bin/python3
"""Mises à jour au redémarrage (maquettes ses-04 et ses-05). La nuit, Sama installe ce qui ne dérange pas une session
ouverte ; le reste (noyau, bureau, applications qui peuvent être ouvertes…) est téléchargé, puis installé au
redémarrage suivant, avant que le bureau ne s'ouvre, sur l'écran de démarrage (« Installation des mises à jour —
3 sur 5 »). Un instantané est pris juste avant : une coupure de courant pendant l'installation est annulée au
démarrage suivant (voir instantanes.py).

Le redémarrage passe par system-update.target de systemd : /system-update (lien) le déclenche.

  maj-redemarrage.py preparer      après les mises à jour de la nuit : prépare celles déjà téléchargées
  maj-redemarrage.py etat          JSON : {nombre, taille} des mises à jour prévues au redémarrage (0 : aucune)
  maj-redemarrage.py redemarrer    (pkexec, menu d'extinction) installer puis redémarrer
  maj-redemarrage.py eteindre      (pkexec, menu d'extinction) installer puis éteindre
  maj-redemarrage.py plus-tard     (pkexec, menu d'extinction) ne pas installer à ce redémarrage-ci
  maj-redemarrage.py installer     samaos-mises-a-jour-redemarrage.service, au redémarrage
  maj-redemarrage.py essai [N]     montre l'écran de mise à jour avec N paquets fictifs (rien n'est installé)
"""
import json
import os
import re
import subprocess
import sys
import time

LIEN = "/system-update"
PREVUES = "/var/lib/samaos/maj-redemarrage.json"
ACTION = "/var/lib/samaos/maj-redemarrage-action"
APT = ["apt-get", "-q", "-y", "-o", "Dpkg::Options::=--force-confdef", "-o", "Dpkg::Options::=--force-confold"]
ENV = dict(os.environ, DEBIAN_FRONTEND="noninteractive", LANG="fr_FR.UTF-8")

# Noms connus des personnes (les autres paquets gardent le leur)
NOMS = [
    (r"^libreoffice-writer$", "Sama Docs"), (r"^libreoffice-calc$", "Sama Sheet"),
    (r"^libreoffice-impress$", "Sama Présentations"), (r"^libreoffice", "LibreOffice"),
    (r"^chromium", "Griot"), (r"^linux-image", "Noyau Linux"), (r"^(plasma-|kwin)", "Bureau Plasma"),
    (r"^systemd", "systemd"), (r"^okular", "Lecteur PDF"), (r"^haruna", "Lecteur vidéo"),
    (r"^gwenview", "Photos"), (r"^plasma-discover", "Sugu"),
]


def taille(octets):
    if octets >= 1048576:
        return ("%.1f Mo" % (octets / 1048576)).replace(".0 Mo", " Mo").replace(".", ",")
    return "%d Ko" % max(1, round(octets / 1024))


def a_installer():
    """Mises à jour en attente : [(paquet, nouvelle version)] (simulation d'APT)."""
    r = subprocess.run(["apt-get", "-s", "-o", "Debug::NoLocking=1", "upgrade", "--with-new-pkgs"],
                       capture_output=True, text=True, env=ENV)
    return re.findall(r"^Inst (\S+) (?:\[[^\]]*\] )?\((\S+)", r.stdout, re.M)


def tailles(paquets):
    """Taille de téléchargement de chaque paquet (apt-cache), en octets."""
    if not paquets:
        return {}
    r = subprocess.run(["apt-cache", "show", "--no-all-versions"] + paquets, capture_output=True, text=True)
    res, nom = {}, None
    for ligne in r.stdout.splitlines():
        if ligne.startswith("Package: "):
            nom = ligne.split(": ", 1)[1]
        elif ligne.startswith("Size: ") and nom:
            res[nom] = int(ligne.split(": ", 1)[1])
    return res


def preparer():
    paquets = a_installer()
    if not paquets:
        retirer()
        return
    t = tailles([p for p, v in paquets])
    os.makedirs(os.path.dirname(PREVUES), exist_ok=True)
    with open(PREVUES, "w") as f:
        json.dump({"nombre": len(paquets), "taille": sum(t.values()),
                   "paquets": [[p, v, t.get(p, 0)] for p, v in paquets]}, f, ensure_ascii=False)
    if not os.path.lexists(LIEN):
        os.symlink("/var/cache/apt/archives", LIEN)


def retirer():
    for f in (LIEN, PREVUES, ACTION):
        try:
            os.remove(f)
        except OSError:
            pass


def etat():
    try:
        prevues = json.load(open(PREVUES)) if os.path.lexists(LIEN) else {}
    except (OSError, ValueError):
        prevues = {}
    print(json.dumps({"nombre": prevues.get("nombre", 0), "taille": prevues.get("taille", 0)}))


# ——— Écran de démarrage (thème Plymouth de Sama : samaos.script) ———

def plymouth(*args):
    subprocess.run(["plymouth"] + list(args), check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def statut(cle, texte):
    plymouth("update", "--status=sama-%s:%s" % (cle, texte))


def avancement(pourcent):
    plymouth("system-update", "--progress=%d" % max(0, min(100, pourcent)))


def nom_lisible(paquet, version, octets):
    nom = next((n for motif, n in NOMS if re.search(motif, paquet)), paquet)
    v = re.sub(r"^\d+:", "", version)                 # (époque)
    v = re.sub(r"[-+~](deb|ubuntu|build|really).*$|-[^-]*$", "", v) or v
    return "%s %s · %s" % (nom, v, taille(octets)) if octets else "%s %s" % (nom, v)


def temps_restant(debut, pourcent):
    if pourcent < 3:
        return "estimation du temps…"
    reste = (time.time() - debut) * (100 - pourcent) / pourcent
    if reste < 60:
        return "moins d'une minute"
    minutes = round(reste / 60)
    return "environ %d minute%s" % (minutes, "s" if minutes > 1 else "")


def installer(essai=0):
    """Au redémarrage (system-update.target) : instantané, installation avec l'avancement à l'écran, puis
    redémarrage ou extinction."""
    if not essai:
        try:
            os.remove(LIEN)               # (avant tout : jamais deux fois de suite, même en cas d'erreur)
        except OSError:
            pass
    try:
        action = open(ACTION).read().strip()
    except OSError:
        action = "redemarrer"
    statut("fin", "L'ordinateur s'éteindra à la fin de l'installation" if action == "eteindre"
                  else "L'ordinateur redémarrera à la fin de l'installation")
    statut("titre", "Préparation des mises à jour…")
    statut("paquet", " ")
    statut("temps", " ")
    avancement(0)

    if essai:
        paquets = [("libreoffice-writer", "4:25.2.3-2+deb13u8", 39845888), ("chromium", "141.0.7390.54-1~deb13u1", 76546048),
                   ("linux-image-6.12.48+deb13-arm64", "6.12.48-1", 41943040), ("plasma-workspace", "4:6.3.6-2", 8912896),
                   ("okular", "4:24.12.3-2", 3145728)][:essai]
        tailles_ = {p: o for p, v, o in paquets}
        paquets = [(p, v) for p, v, o in paquets]
    else:
        paquets = a_installer()
        tailles_ = tailles([p for p, v in paquets])
    n = len(paquets)
    versions = dict(paquets)
    ordre = [p for p, v in paquets]
    code = 0
    if n:
        sys.path.insert(0, "/usr/libexec/samaos")
        if not essai:
            try:
                import instantanes
                instantanes.avant_maj(n, "redemarrage")
            except Exception as e:                      # (pas d'instantané : on installe quand même)
                print("Instantané impossible : %s" % e, file=sys.stderr)
        debut = time.time()
        vus = []

        def montrer(paquet, pourcent):
            p = paquet.split(":")[0]
            if p in versions and p not in vus:
                vus.append(p)
            i = max(1, len(vus))
            statut("titre", "Installation des mises à jour — %d sur %d" % (min(i, n), n))
            if p in versions:
                statut("paquet", nom_lisible(p, versions[p], tailles_.get(p, 0)))
            statut("temps", temps_restant(debut, pourcent))
            avancement(pourcent)

        if essai:
            for k in range(101):
                montrer(ordre[min(n - 1, k * n // 101)], k)
                time.sleep(0.12)
        else:
            lecture, ecriture = os.pipe()
            proc = subprocess.Popen(APT + ["--no-download", "-o", "APT::Status-Fd=%d" % ecriture, "upgrade", "--with-new-pkgs"],
                                    env=ENV, pass_fds=(ecriture,), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            os.close(ecriture)
            with os.fdopen(lecture) as flux:
                for ligne in flux:
                    # « pmstatus:libreoffice-writer:42.8571:Installation de libreoffice-writer (arm64) »
                    champs = ligne.strip().split(":", 3)
                    if len(champs) >= 3 and champs[0] == "pmstatus":
                        try:
                            montrer(champs[1], int(float(champs[2])))
                        except ValueError:
                            pass
            code = proc.wait()
            try:
                import instantanes
                instantanes.apres_maj("redemarrage")
            except Exception:
                pass
        statut("titre", "Mises à jour installées" if code == 0 else "Une partie des mises à jour n'a pas pu s'installer")
        statut("paquet", "%d paquet%s" % (n, "s" if n > 1 else "") if code == 0 else "Elles seront retentées la nuit prochaine")
        statut("temps", " ")
        avancement(100)
        time.sleep(3)
    if not essai:
        retirer()
        subprocess.run(["systemctl", "poweroff" if action == "eteindre" else "reboot"], check=False)
    return code


def main(a):
    action = a[0] if a else ""
    # Par pkexec (menu d'extinction, n'importe quel compte assis devant) : seulement le moment de l'installation
    if os.environ.get("PKEXEC_UID") and action not in ("redemarrer", "eteindre", "plus-tard"):
        print("Action réservée au système.", file=sys.stderr)
        return 1
    if action == "preparer":
        preparer()
    elif action == "etat":
        etat()
    elif action in ("redemarrer", "eteindre"):
        if os.path.lexists(LIEN):
            with open(ACTION, "w") as f:
                f.write(action + "\n")
    elif action == "plus-tard":
        try:
            os.remove(LIEN)                # (la nuit suivante les préparera de nouveau)
        except OSError:
            pass
    elif action == "installer":
        return installer()
    elif action == "essai":
        return installer(int(a[1]) if len(a) > 1 and a[1].isdigit() else 5)
    else:
        print(__doc__, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
