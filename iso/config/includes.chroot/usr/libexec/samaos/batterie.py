#!/usr/bin/python3
"""Batterie faible (maquette ale-03) : quand l'ordinateur est sur batterie et passe sous le seuil de « batterie faible »
de Plasma (10 % par défaut), carte « Batterie à 10 % · environ 25 min d'autonomie » avec « Activer l'économie
maximale » : mode Économie, écran à 30 % au plus, indexation des fichiers (Baloo) en pause. Tout revient comme avant
après 2 minutes de courant (un courant qui saute ne relance ni la carte ni la luminosité). La carte se retire
d'elle-même si l'ordinateur est rebranché pendant qu'elle est ouverte.
(La bulle « Batterie faible » de Plasma ne fait plus que son bruit : /etc/xdg/powerdevil.notifyrc.)

  batterie.py veiller                       lancé à l'ouverture de session
  batterie.py essai [pourcentage] [minutes]  montrer la carte avec ces valeurs (pour essayer sans batterie)
"""
import configparser
import json
import os
import subprocess
import sys
import time

QML = "/usr/libexec/samaos/batterie/Batterie.qml"
ETAT = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "samaos-batterie.json")
GAIN = 1.5            # (autonomie avec l'économie : estimation prudente, annoncée « ≈ »)
LUMINOSITE = 0.3
BRANCHE = 120         # (secondes de courant avant de tout remettre comme avant)
# UPower : Type 2 = batterie ; State 1 = en charge, 2 = se décharge, 4 = chargée, 5 = charge en attente
DECHARGE = 2


def seuil():
    """Le seuil « batterie faible » de Plasma (Réglages d'énergie), 10 % par défaut."""
    c = configparser.ConfigParser(interpolation=None)
    c.read(os.path.expanduser("~/.config/powerdevilrc"))
    try:
        return int(c.get("BatteryManagement", "BatteryLowLevel", fallback="10"))
    except ValueError:
        return 10


def lire(nom):
    try:
        return json.load(open(nom))
    except (OSError, ValueError):
        return {}


def ecrire(nom, donnees):
    with open(nom, "w") as f:
        json.dump(donnees, f)


def profil():
    p = subprocess.run(["powerprofilesctl", "get"], capture_output=True, text=True)
    return p.stdout.strip()


# ——— Luminosité de l'écran (org.kde.ScreenBrightness, celle des Réglages et du Pouls) ———
def ecrans(bus):
    from gi.repository import Gio, GLib
    try:
        r = bus.call_sync("org.kde.ScreenBrightness", "/org/kde/ScreenBrightness", "org.freedesktop.DBus.Properties", "Get",
                          GLib.Variant("(ss)", ("org.kde.ScreenBrightness", "DisplaysDBusNames")), None, Gio.DBusCallFlags.NONE, 3000, None)
        return list(r.unpack()[0])
    except GLib.Error:
        return []


def luminosite(bus, ecran):
    from gi.repository import Gio, GLib
    r = bus.call_sync("org.kde.ScreenBrightness", "/org/kde/ScreenBrightness/" + ecran, "org.freedesktop.DBus.Properties", "GetAll",
                      GLib.Variant("(s)", ("org.kde.ScreenBrightness.Display",)), None, Gio.DBusCallFlags.NONE, 3000, None)
    p = r.unpack()[0]
    return p.get("Brightness", 0), p.get("MaxBrightness", 0)


def regler_luminosite(bus, ecran, valeur):
    from gi.repository import Gio, GLib
    try:
        bus.call_sync("org.kde.ScreenBrightness", "/org/kde/ScreenBrightness/" + ecran, "org.kde.ScreenBrightness.Display",
                      "SetBrightness", GLib.Variant("(iu)", (int(valeur), 0)), None, Gio.DBusCallFlags.NONE, 3000, None)
    except GLib.Error:
        pass


def economiser(bus):
    """L'économie maximale ; ce qui était réglé avant est noté, pour le remettre au rebranchement."""
    avant = {"profil": profil(), "ecrans": {}}
    subprocess.run(["powerprofilesctl", "set", "power-saver"], check=False)
    for e in ecrans(bus):
        try:
            valeur, maxi = luminosite(bus, e)
        except Exception:
            continue
        cible = int(maxi * LUMINOSITE)
        if maxi and valeur > cible:
            avant["ecrans"][e] = [valeur, cible]
            regler_luminosite(bus, e, cible)
    if subprocess.run(["pgrep", "-x", "baloo_file"], capture_output=True).returncode == 0:
        subprocess.run(["balooctl6", "suspend"], capture_output=True, check=False)
        avant["baloo"] = True
    ecrire(ETAT, avant)


def retablir(bus):
    """Rebranché : le mode, la luminosité (si personne n'y a touché entre-temps) et l'indexation reviennent."""
    avant = lire(ETAT)
    if not avant:
        return
    os.remove(ETAT)
    if avant.get("profil") and profil() == "power-saver":
        subprocess.run(["powerprofilesctl", "set", avant["profil"]], check=False)
    for e, (valeur, cible) in avant.get("ecrans", {}).items():
        try:
            if luminosite(bus, e)[0] == cible:
                regler_luminosite(bus, e, valeur)
        except Exception:
            pass
    if avant.get("baloo"):
        subprocess.run(["balooctl6", "resume"], capture_output=True, check=False)


def carte(pourcentage, secondes):
    """Les données de la carte : minutes restantes, et avec l'économie (si elle n'est pas déjà en marche)."""
    minutes = int(round(secondes / 60)) if secondes > 0 else 0
    deja = profil() == "power-saver"
    return {"pourcentage": int(round(pourcentage)), "minutes": minutes, "economie": 0 if deja else int(round(minutes * GAIN)),
            "dejaEconomie": deja}


def montrer(donnees):
    return subprocess.Popen(["qml6", QML, "--", json.dumps(donnees)], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)


def veiller():
    from gi.repository import Gio, GLib
    systeme = Gio.bus_get_sync(Gio.BusType.SYSTEM, None)
    session = Gio.bus_get_sync(Gio.BusType.SESSION, None)
    appareil = Gio.DBusProxy.new_sync(systeme, Gio.DBusProxyFlags.NONE, None, "org.freedesktop.UPower",
                                      "/org/freedesktop/UPower/devices/DisplayDevice", "org.freedesktop.UPower.Device", None)
    etat = {"averti": False, "carte": None, "branche": 0}

    def valeur(nom, defaut=0):
        v = appareil.get_cached_property(nom)
        return v.unpack() if v is not None else defaut

    def fermer_carte():
        if etat["carte"] and etat["carte"].poll() is None:
            etat["carte"].terminate()
        etat["carte"] = None

    def choix_carte(fichier, condition):
        ligne = fichier.readline()
        if not ligne:
            etat["carte"] = None
            return False
        if "SAMA_CHOIX=economie" in ligne:
            economiser(session)
        return True

    def examiner(*_):
        if valeur("Type") != 2 or not valeur("IsPresent", False):
            return
        pourcentage, statut = valeur("Percentage", 100.0), valeur("State")
        if statut != DECHARGE:
            # Rebranché : la carte s'en va. Après 2 minutes de courant (pas pour un courant qui saute), l'économie se
            # retire et l'alerte servira à la prochaine décharge.
            fermer_carte()
            etat["branche"] = etat["branche"] or time.monotonic()
            if statut in (1, 4, 5) and time.monotonic() - etat["branche"] >= BRANCHE:
                etat["averti"] = False
                retablir(session)
            return
        etat["branche"] = 0
        if pourcentage > seuil():
            etat["averti"] = False
        elif not etat["averti"] and pourcentage > 0:
            etat["averti"] = True
            etat["carte"] = montrer(carte(pourcentage, valeur("TimeToEmpty")))
            GLib.io_add_watch(etat["carte"].stderr, GLib.PRIORITY_DEFAULT, GLib.IO_IN | GLib.IO_HUP, choix_carte)

    appareil.connect("g-properties-changed", examiner)
    GLib.timeout_add_seconds(60, lambda: examiner() or True)
    examiner()
    GLib.MainLoop().run()


def essai(pourcentage, minutes):
    p = montrer(carte(pourcentage, minutes * 60))
    for ligne in p.stderr:
        if "SAMA_CHOIX=" in ligne:
            print(ligne.strip().split("SAMA_CHOIX=", 1)[1])
    p.wait()


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else "veiller"
    if action == "essai":
        essai(float(sys.argv[2]) if len(sys.argv) > 2 else 10, int(sys.argv[3]) if len(sys.argv) > 3 else 25)
    else:
        veiller()
