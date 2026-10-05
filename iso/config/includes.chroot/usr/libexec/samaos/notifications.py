#!/usr/bin/env python3
"""Réglages des notifications, application par application (Réglages Sama → Notifications).

Les choix sont ceux de Plasma (fichier plasmanotifyrc), lus par la Natte (bulles et centre de notifications) :
  ShowPopups            bulles à l'écran
  ShowInHistory         gardées dans le centre de notifications
  ShowPopupsInDndMode   bulles même en Ne pas déranger

  notifications.py liste                                  JSON : applications et services du système
  notifications.py regler app|service <id> <clé> true|false
"""
import configparser
import json
import os
import subprocess
import sys

CONFIG = os.path.join(os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config")), "plasmanotifyrc")
DOSSIERS = ["/usr/local/share/applications", "/usr/share/applications", os.path.expanduser("~/.local/share/applications")]

# Applications proposées même si elles n'ont encore rien envoyé (nom Sama des logiciels provisoires)
APPLICATIONS = [
    ("chromium", "Griot"), ("org.kde.dolphin", "Fichiers"), ("org.kde.discover", "Sugu"),
    ("libreoffice-writer", "Sama Docs"), ("org.kde.konsole", None), ("org.kde.spectacle", None),
    ("org.kde.okular", None), ("org.kde.ark", None), ("samaos-reglages", None),
]
# Tuile Sama des logiciels provisoires (leur fichier .desktop d'origine garde l'icône du logiciel)
ICONES_SAMA = {"chromium": "griot", "org.kde.dolphin": "fichiers", "org.kde.discover": "sugu", "libreoffice-writer": "docs"}
# Notifications du système : fichier .notifyrc → nom clair
SERVICES = [
    ("samaos", "Sama", "Forfait data, mises à jour, alertes du système"),
    ("networkmanagement", "Réseau", "Connexion et déconnexion, Wi-Fi"),
    ("bluedevil", "Bluetooth", "Appareils connectés et fichiers reçus"),
    ("powerdevil", "Énergie", "Batterie faible, branchement"),
    ("devicenotifications", "Périphériques", "Clés USB, disques et appareils branchés"),
    ("freespacenotifier", "Espace disque", "Disque presque plein"),
    ("kdeconnect", "Téléphone", "Messages et appels du téléphone connecté"),
]
ICONES_SERVICES = {"samaos": "/usr/share/samaos/logo-cour.svg", "networkmanagement": "network-wireless",
                   "bluedevil": "preferences-system-bluetooth", "powerdevil": "battery", "devicenotifications": "drive-removable-media",
                   "freespacenotifier": "drive-harddisk", "kdeconnect": "smartphone"}


def lire_config():
    c = configparser.RawConfigParser(strict=False)
    c.optionxform = str
    try:
        c.read(CONFIG, encoding="utf-8")
    except configparser.Error:
        pass
    return c


def valeur(c, groupe, cle, defaut=True):
    try:
        return c.get(groupe, cle).strip().lower() != "false"
    except (configparser.NoSectionError, configparser.NoOptionError):
        return defaut


def bureau(id_app):
    """Nom et icône d'une application d'après son fichier .desktop (copie Sama renommée en priorité)."""
    for dossier in DOSSIERS:
        chemin = os.path.join(dossier, id_app + ".desktop")
        if not os.path.isfile(chemin):
            continue
        nom = nom_fr = icone = ""
        dans_entree = False
        for ligne in open(chemin, encoding="utf-8", errors="replace"):
            ligne = ligne.strip()
            if ligne.startswith("["):
                dans_entree = ligne == "[Desktop Entry]"
                continue
            if not dans_entree:
                continue
            if ligne.startswith("Name[fr]="):
                nom_fr = ligne.split("=", 1)[1]
            elif ligne.startswith("Name="):
                nom = nom or ligne.split("=", 1)[1]
            elif ligne.startswith("Icon="):
                icone = icone or ligne.split("=", 1)[1]
        return (nom_fr or nom), icone
    return None, None


def liste():
    c = lire_config()
    vus = []
    # Applications qui ont déjà envoyé des notifications (Plasma les note dans [Applications][…])
    for groupe in c.sections():
        if groupe.startswith("Applications]["):
            vus.append(groupe.split("][", 1)[1].rstrip("]"))
    resultat, deja = [], set()
    for id_app, nom_sama in APPLICATIONS + [(v, None) for v in vus]:
        if id_app in deja:
            continue
        nom, icone = bureau(id_app)
        if nom is None and id_app not in vus:
            continue   # pas installée
        deja.add(id_app)
        g = "Applications][" + id_app
        if id_app in ICONES_SAMA:
            icone = "/usr/share/samaos/icones/%s.svg" % ICONES_SAMA[id_app]
        resultat.append({"type": "app", "id": id_app, "nom": nom_sama or nom or id_app, "icone": icone or "application-x-executable",
                         "detail": "", "bulles": valeur(c, g, "ShowPopups"), "centre": valeur(c, g, "ShowInHistory"),
                         "npd": valeur(c, g, "ShowPopupsInDndMode", False)})
    resultat.sort(key=lambda a: a["nom"].lower())
    for id_service, nom, detail in SERVICES:
        if not os.path.isfile("/usr/share/knotifications6/%s.notifyrc" % id_service):
            continue
        g = "Services][" + id_service
        resultat.append({"type": "service", "id": id_service, "nom": nom, "icone": ICONES_SERVICES.get(id_service, ""),
                         "detail": detail, "bulles": valeur(c, g, "ShowPopups"), "centre": valeur(c, g, "ShowInHistory"),
                         "npd": valeur(c, g, "ShowPopupsInDndMode", id_service == "samaos")})
    print(json.dumps(resultat, ensure_ascii=False))


def regler(genre, id_, cle, etat):
    groupe = "Applications" if genre == "app" else "Services"
    cles = {"bulles": "ShowPopups", "centre": "ShowInHistory", "npd": "ShowPopupsInDndMode"}
    subprocess.run(["kwriteconfig6", "--notify", "--file", "plasmanotifyrc", "--group", groupe, "--group", id_,
                    "--key", cles[cle], "--type", "bool", etat], check=True)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "liste":
        liste()
    elif len(sys.argv) == 6 and sys.argv[1] == "regler":
        regler(*sys.argv[2:])
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
