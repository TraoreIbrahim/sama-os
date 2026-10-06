#!/usr/bin/python3
"""Moteur du sélecteur de fichiers de Sama (/usr/lib/samaos/selecteur/Selecteur.qml, servi par portail-fichiers).

  selecteur.py lister DOSSIER        JSON : sous-dossiers (avec leur nombre d'éléments) puis fichiers, sans les cachés
  selecteur.py emplacements          JSON : dossiers de l'utilisateur et clés USB branchées (montées au besoin à l'ouverture)
"""
import json
import os
import subprocess
import sys
import unicodedata

ICI = os.path.dirname(os.path.abspath(__file__))


def cle_tri(nom):
    return unicodedata.normalize("NFD", nom.casefold()).encode("ascii", "ignore").decode() or nom


def lister(dossier):
    dossiers, fichiers = [], []
    try:
        entrees = list(os.scandir(dossier))
    except OSError:
        print("[]")
        return
    for e in entrees:
        if e.name.startswith("."):
            continue
        try:
            if e.is_dir():
                try:
                    nombre = sum(1 for x in os.scandir(e.path) if not x.name.startswith("."))
                except OSError:
                    nombre = -1
                dossiers.append({"nom": e.name, "chemin": e.path, "dossier": True, "nombre": nombre})
            else:
                s = e.stat()
                fichiers.append({"nom": e.name, "chemin": e.path, "dossier": False, "taille": s.st_size, "date": int(s.st_mtime)})
        except OSError:
            continue
    dossiers.sort(key=lambda x: cle_tri(x["nom"]))
    fichiers.sort(key=lambda x: cle_tri(x["nom"]))
    print(json.dumps(dossiers + fichiers, ensure_ascii=False))


def dossier_xdg(nom, defaut):
    r = subprocess.run(["xdg-user-dir", nom], capture_output=True, text=True).stdout.strip()
    return r if r and r != os.path.expanduser("~") else os.path.expanduser(defaut)


def emplacements():
    res = [{"nom": "Récents", "chemin": "recents", "picto": "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7.5V12l3 2"},
           {"nom": "Documents", "chemin": dossier_xdg("DOCUMENTS", "~/Documents"),
            "picto": "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M14 3v5h5 M9 13h6 M9 17h4"},
           {"nom": "Bureau", "chemin": dossier_xdg("DESKTOP", "~/Bureau"), "picto": "M4 5h16v11H4z M9 20h6 M12 16v4"},
           {"nom": "Téléchargements", "chemin": dossier_xdg("DOWNLOAD", "~/Téléchargements"), "picto": "M12 4v11 M7 10l5 5l5-5 M5 20h14"},
           {"nom": "Images", "chemin": dossier_xdg("PICTURES", "~/Images"), "picto": "M4 5h16v14H4z M4 15l4-4l5 5 M14 13l2-2l4 4 M15 8.5h.01"},
           {"nom": "Accueil", "chemin": os.path.expanduser("~"), "picto": "M4 11l8-7l8 7v9h-5v-6h-6v6H4z"}]
    res = [e for e in res if e["chemin"] == "recents" or os.path.isdir(e["chemin"])]
    try:
        appareils = json.loads(subprocess.run([sys.executable, os.path.join(ICI, "fichiers.py"), "appareils"],
                                              capture_output=True, text=True, timeout=10).stdout or "[]")
    except (ValueError, subprocess.SubprocessError, OSError):
        appareils = []
    for a in appareils:
        if a.get("type") == "externe":
            res.append({"nom": a.get("nom", "Clé USB"), "chemin": a.get("montage") or "", "peripherique": a.get("chemin", ""),
                        "picto": "M9 3h6v6H9z M7 9h10v8a4 4 0 0 1-4 4h-2a4 4 0 0 1-4-4z"})
    print(json.dumps(res, ensure_ascii=False))


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    if action == "lister" and len(sys.argv) > 2:
        lister(sys.argv[2])
    elif action == "emplacements":
        emplacements()
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
