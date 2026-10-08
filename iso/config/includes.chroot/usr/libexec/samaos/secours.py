#!/usr/bin/python3
"""Copies de secours de Sama Docs et Sama Sheet (le retour du courant, une application qui ne répond plus, un arrêt
forcé). Pendant qu'on travaille, l'application écrit chaque minute une copie du document modifié dans un dossier à
elle, ~/.local/state/samaos/secours/<id>/ : copie.docx (ou .xlsx…) et infos.json (l'application, son processus, le
fichier d'origine, le titre, l'heure). La copie disparaît quand le document est enregistré, ou fermé sans l'être à la
demande. Si l'application s'arrête sans l'avoir retirée (coupure, plantage, arrêt forcé), elle reste « à récupérer » :
la carte du retour du courant, l'accueil de l'application. Au bout de 30 jours, une copie jamais reprise s'efface.

  secours.py dossier PID APPLI                     crée le dossier d'une copie, l'écrit sur la sortie
  secours.py noter DOSSIER PID APPLI ORIGINAL TITRE EXT
                                                   la copie (nouvelle.EXT) vient d'être écrite : elle devient copie.EXT
  secours.py retirer DOSSIER                       la copie ne sert plus (document enregistré, ou fermé)
  secours.py orphelins [APPLI]                     JSON : les copies laissées par une application arrêtée
  secours.py de PID                                JSON : les copies de ce processus (une application qui ne répond plus)
  secours.py reprendre FICHIER PID                 l'application rouvre cette copie, qui devient la sienne ; JSON
"""
import json
import os
import shutil
import sys
import time
import uuid

RACINE = os.path.join(os.environ.get("XDG_STATE_HOME", os.path.expanduser("~/.local/state")), "samaos", "secours")
GARDE = 30 * 86400
APPLIS = {"samaos-docs.desktop": ("Sama Docs", "/usr/share/samaos/icones/docs.svg", "samaos-docs"),
          "samaos-sheet.desktop": ("Sama Sheet", "/usr/share/samaos/icones/sheet.svg", "samaos-sheet")}


def demarrage_id():
    try:
        return open("/proc/sys/kernel/random/boot_id").read().strip()
    except OSError:
        return ""


def ecrire(chemin, donnees):
    """Écrit tout de suite sur le disque (le courant peut sauter la seconde d'après)."""
    temporaire = chemin + ".nouveau"
    with open(temporaire, "w") as f:
        json.dump(donnees, f, ensure_ascii=False)
        f.flush()
        os.fsync(f.fileno())
    os.replace(temporaire, chemin)
    synchroniser(os.path.dirname(chemin))


def synchroniser(dossier):
    d = os.open(dossier, os.O_RDONLY)
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


def a_nous(dossier):
    """Un dossier de copie, dans la racine (jamais ailleurs : retirer efface)."""
    d = os.path.realpath(dossier)
    return os.path.dirname(d) == os.path.realpath(RACINE) and os.path.isdir(d)


def vivant(infos):
    """L'application qui a écrit la copie tourne encore (même démarrage, même processus, même programme)."""
    if infos.get("demarrage") != demarrage_id():
        return False
    try:
        ligne = open("/proc/%d/cmdline" % int(infos.get("pid", 0)), "rb").read().decode(errors="replace")
    except (OSError, ValueError):
        return False
    return APPLIS.get(infos.get("appli", ""), ("", "", "\0"))[2] in ligne


def copies():
    """Toutes les copies : (dossier, infos, fichier de la copie ou None)."""
    res = []
    try:
        noms = os.listdir(RACINE)
    except OSError:
        return res
    for nom in noms:
        dossier = os.path.join(RACINE, nom)
        infos = lire(os.path.join(dossier, "infos.json"))
        if not os.path.isdir(dossier) or infos is None:
            continue
        fichier = os.path.join(dossier, "copie." + infos.get("ext", "")) if infos.get("ext") else None
        res.append((dossier, infos, fichier if fichier and os.path.isfile(fichier) else None))
    return res


def decrire(dossier, infos, fichier):
    appli = APPLIS.get(infos.get("appli", ""), ("Sama Docs", "/usr/share/samaos/icones/docs.svg", ""))
    return {"titre": infos.get("titre") or "Sans titre", "appli": appli[0], "id": infos.get("appli", ""), "icone": appli[1],
            "quand": int(infos.get("quand", 0)), "original": infos.get("original", ""), "fichier": fichier,
            "dossier": dossier, "nonEnregistre": True}


def orphelins(appli=""):
    res = []
    for dossier, infos, fichier in copies():
        if vivant(infos):
            continue
        # (sans copie écrite, ou trop ancienne : rien à récupérer)
        if not fichier or time.time() - infos.get("quand", 0) > GARDE:
            shutil.rmtree(dossier, ignore_errors=True)
            continue
        if not appli or infos.get("appli") == appli:
            res.append(decrire(dossier, infos, fichier))
    return sorted(res, key=lambda d: d["quand"], reverse=True)


def de(pid):
    return sorted([decrire(d, i, f) for d, i, f in copies() if f and str(i.get("pid")) == str(pid) and vivant(i)],
                  key=lambda d: d["quand"], reverse=True)


def main(args):
    action = args[0] if args else ""
    if action == "dossier" and len(args) == 3:
        os.makedirs(RACINE, mode=0o700, exist_ok=True)
        os.chmod(RACINE, 0o700)
        dossier = os.path.join(RACINE, uuid.uuid4().hex)
        os.mkdir(dossier, 0o700)
        ecrire(os.path.join(dossier, "infos.json"), {"pid": int(args[1]), "appli": args[2], "demarrage": demarrage_id()})
        print(dossier)
    elif action == "noter" and len(args) == 7:
        dossier, pid, appli, original, titre, ext = args[1:]
        if not a_nous(dossier):
            sys.exit(1)
        nouvelle, copie = os.path.join(dossier, "nouvelle." + ext), os.path.join(dossier, "copie." + ext)
        with open(nouvelle, "rb") as f:
            os.fsync(f.fileno())
        os.replace(nouvelle, copie)
        ecrire(os.path.join(dossier, "infos.json"), {"pid": int(pid), "appli": appli, "demarrage": demarrage_id(),
                                                     "original": original, "titre": titre, "ext": ext, "quand": int(time.time())})
    elif action == "retirer" and len(args) == 2:
        if a_nous(args[1]):
            shutil.rmtree(args[1], ignore_errors=True)
    elif action == "orphelins":
        print(json.dumps(orphelins(args[1] if len(args) > 1 else ""), ensure_ascii=False))
    elif action == "de" and len(args) == 2:
        print(json.dumps(de(args[1]), ensure_ascii=False))
    elif action == "reprendre" and len(args) == 3:
        dossier = os.path.dirname(os.path.realpath(args[1]))
        infos = lire(os.path.join(dossier, "infos.json"))
        if not a_nous(dossier) or infos is None:
            print("{}")
            return
        infos.update({"pid": int(args[2]), "demarrage": demarrage_id()})
        ecrire(os.path.join(dossier, "infos.json"), infos)
        print(json.dumps(decrire(dossier, infos, args[1]), ensure_ascii=False))
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv[1:])
