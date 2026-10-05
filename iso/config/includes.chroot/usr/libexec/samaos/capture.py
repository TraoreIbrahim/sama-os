#!/usr/bin/python3
"""Capture d'écran de Sama (maquette ale-07), sur la touche Impr. : l'écran est figé, on choisit une zone (poignées,
taille affichée), une fenêtre ou l'écran entier, avec un minuteur ou en vidéo. La capture est enregistrée dans
Images › Captures d'écran, copiée (wl-copy) et annoncée par une notification « Copiée » avec « Annoter ».
Les prises de vue elles-mêmes sont faites par Spectacle (autorisé par KWin), en arrière-plan.

  capture.py            prendre une capture (sama-capture)
"""
import datetime
import fcntl
import json
import os
import shutil
import subprocess
import sys
import time

TRAVAIL = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "samaos-capture")
QML = "/usr/libexec/samaos/capture/Capture.qml"
MOIS = ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."]


def dossier_captures():
    images = subprocess.run(["xdg-user-dir", "PICTURES"], capture_output=True, text=True).stdout.strip() \
        or os.path.expanduser("~/Images")
    d = os.path.join(images, "Captures d'écran")
    os.makedirs(d, exist_ok=True)
    return d


def nouveau_nom():
    t = datetime.datetime.now()
    return os.path.join(dossier_captures(), "Capture du %d %s %d à %02d.%02d.%02d.png" % (
        t.day, MOIS[t.month - 1], t.year, t.hour, t.minute, t.second))


def spectacle(options, sortie, delai=0):
    """Prise de vue par Spectacle, en arrière-plan, sans sa notification."""
    commande = ["spectacle", "-i", "-b", "-n"] + options + ["-o", sortie]     # (-i : même si l'éditeur est ouvert)
    if delai:
        commande += ["-d", str(int(delai * 1000))]
    try:
        subprocess.run(commande, timeout=60 + delai, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except (OSError, subprocess.SubprocessError):
        return False
    return os.path.isfile(sortie) and os.path.getsize(sortie) > 0


def interface(donnees, horsEcran=False):
    """La fenêtre de capture (ou son mode « découpe », sans écran) ; rend ce qu'elle a choisi."""
    env = dict(os.environ, QT_QPA_PLATFORM="offscreen") if horsEcran else None
    p = subprocess.run(["qml6", QML, "--", json.dumps(donnees, ensure_ascii=False)], capture_output=True, text=True, env=env)
    for ligne in (p.stdout + p.stderr).splitlines():
        if "SAMA_CAPTURE=" in ligne:
            return json.loads(ligne.split("SAMA_CAPTURE=", 1)[1])
    return {}


def notifier(titre, texte, image="", actions=(), attente=60):
    """Notification de Plasma (D-Bus), avec l'aperçu de la capture ; rend l'action choisie (ou "")."""
    try:
        from gi.repository import Gio, GLib
    except ImportError:
        return ""
    bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
    indices = {"image-path": GLib.Variant("s", "file://" + image)} if image else {}
    indices["desktop-entry"] = GLib.Variant("s", "samaos-capture")
    r = bus.call_sync("org.freedesktop.Notifications", "/org/freedesktop/Notifications", "org.freedesktop.Notifications",
                      "Notify", GLib.Variant("(susssasa{sv}i)", ("Capture d'écran", 0, "/usr/share/samaos/icones/capture.svg", titre, texte,
                                                                  list(actions), indices, 8000)),
                      GLib.VariantType("(u)"), Gio.DBusCallFlags.NONE, -1, None)
    numero = r.unpack()[0]
    if not actions:
        return ""
    boucle, choix = GLib.MainLoop(), [""]

    def signal(connexion, envoyeur, chemin, interface, nom, parametres):
        p = parametres.unpack()
        if p[0] != numero:
            return
        if nom == "ActionInvoked":
            choix[0] = p[1]
            boucle.quit()
        elif nom == "NotificationClosed":
            GLib.timeout_add(400, boucle.quit)   # (l'action cliquée peut arriver juste après la fermeture)
    bus.signal_subscribe("org.freedesktop.Notifications", "org.freedesktop.Notifications", None,
                         "/org/freedesktop/Notifications", None, Gio.DBusSignalFlags.NONE, signal)
    GLib.timeout_add_seconds(attente, boucle.quit)
    boucle.run()
    return choix[0]


def detacher(commande, nom):
    """Dans sa propre unité : survit à la fin de la capture (systemd arrête l'unité de celle-ci, et tout ce qu'elle a lancé)."""
    subprocess.run(["systemd-run", "--user", "--quiet", "--collect", "--slice=app.slice", "-p", "ExitType=cgroup",
                    "--unit=app-%s@%d.service" % (nom, int(time.time() * 1000))] + commande, check=False)


def finir(fichier):
    """Copiée, puis notification « Copiée · Annoter » (l'annotation est celle de Spectacle)."""
    if shutil.which("wl-copy"):              # (wl-copy garde l'image tant que rien d'autre n'est copié)
        detacher(["sh", "-c", 'exec wl-copy --foreground --type image/png < "$1"', "sh", fichier], "samaos\\x2dcapture\\x2dcopie")
    choix = notifier("Copiée", "Enregistrée dans Images › Captures d'écran", fichier,
                     ["annoter", "Annoter", "ouvrir", "Ouvrir le dossier"])
    if choix == "annoter":
        detacher(["spectacle", "-E", fichier], "org.kde.spectacle")
    elif choix == "ouvrir":
        detacher(["sama-fichiers", os.path.dirname(fichier)], "samaos\\x2dfichiers")


def main():
    os.makedirs(TRAVAIL, exist_ok=True)
    verrou = open(os.path.join(TRAVAIL, "verrou"), "w")
    try:
        fcntl.flock(verrou, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        return 0                                  # (une capture est déjà en cours)
    ecran = os.path.join(TRAVAIL, "ecran.png")
    try:
        os.remove(ecran)
    except OSError:
        pass
    if not spectacle(["-m"], ecran):
        notifier("Capture impossible", "L'écran n'a pas pu être pris.")
        return 1
    sortie = nouveau_nom()
    choix = interface({"image": ecran, "sortie": sortie})
    mode, delai = choix.get("mode", ""), int(choix.get("delai", 0))
    fait = ""
    if mode == "zone" and choix.get("fait"):
        fait = choix["fait"]
    elif mode == "zone" and choix.get("zone"):
        # Zone avec minuteur : l'écran est repris après le délai, puis découpé de la même façon
        time.sleep(delai)
        if spectacle(["-m"], ecran):
            fait = interface({"image": ecran, "sortie": sortie, "decoupe": choix["zone"]}, horsEcran=True).get("fait", "")
    elif mode == "ecran":
        time.sleep(0.3)                           # (le temps que la fenêtre de capture disparaisse)
        fait = sortie if spectacle(["-m"], sortie, delai) else ""
    elif mode == "fenetre":
        time.sleep(0.3)
        fait = sortie if spectacle(["-a", "-S"], sortie, delai) else ""      # (sans l'ombre de la fenêtre)
    elif mode == "video":
        time.sleep(delai)
        detacher(["spectacle", "-R", {"zone": "region", "fenetre": "window"}.get(choix.get("cadre"), "screen")], "org.kde.spectacle")
    if fait and os.path.isfile(fait):
        verrou.close()
        finir(fait)
    return 0


if __name__ == "__main__":
    sys.exit(main())
