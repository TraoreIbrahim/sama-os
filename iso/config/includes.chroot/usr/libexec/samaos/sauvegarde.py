#!/usr/bin/env python3
"""Sauvegarde de Sama sur une clé USB ou un disque externe (Réglages › Sauvegarde, maquette reg-11).

  sauvegarde.py etat [rapide]        JSON : disque choisi, branché ou non, dernière sauvegarde, dossiers, réglages
                                     (rapide : sans la taille des dossiers, pour suivre une sauvegarde en cours)
  sauvegarde.py disques              JSON : clés et disques externes branchés
  sauvegarde.py choisir UUID         disque de sauvegarde (vide : aucun)
  sauvegarde.py sauvegarder          sauvegarde maintenant (le disque est monté au besoin)
  sauvegarde.py ouvrir               dossier de la sauvegarde sur le disque (monté au besoin), pour Fichiers
  sauvegarde.py auto                 minuteur samaos-sauvegarde.timer : sauvegarde si c'est l'heure et le disque est là

Sur le disque : « Sauvegarde Sama/<compte>@<ordinateur>/Actuelle/<dossier> », copie à l'identique de vos dossiers ;
les fichiers modifiés ou effacés depuis la sauvegarde précédente sont gardés dans « Versions précédentes/<date> »
(les 20 dernières).
Fonctionne avec les clés formatées sous Windows (FAT32, exFAT, NTFS) : ni droits ni liens, seulement les fichiers.
Réglages (samaosrc, groupe Sauvegarde) : disque, nomDisque, auto, frequence (heure|jour|semaine), dossiers.
"""
import configparser
import fcntl
import json
import os
import socket
import subprocess
import sys
import time

CONFIG = os.path.join(os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config")), "samaosrc")
ETAT = os.path.join(os.environ.get("XDG_STATE_HOME", os.path.expanduser("~/.local/state")), "samaos", "sauvegarde.json")
VERROU = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "samaos-sauvegarde.verrou")
# Dossiers proposés : clé XDG, nom affiché, sauvegardé par défaut
DOSSIERS = [("DOCUMENTS", "Documents", True), ("PICTURES", "Images", True), ("DESKTOP", "Bureau", True),
            ("MUSIC", "Musique", True), ("VIDEOS", "Vidéos", False), ("DOWNLOAD", "Téléchargements", False)]
INTERVALLES = {"heure": 3600, "jour": 86400, "semaine": 7 * 86400}
VERSIONS_GARDEES = 20


def lire_config():
    c = configparser.RawConfigParser(strict=False)
    c.optionxform = str
    try:
        c.read(CONFIG, encoding="utf-8")
    except configparser.Error:
        pass
    return c


def reglage(c, cle, defaut=""):
    try:
        return c.get("Sauvegarde", cle).strip()
    except configparser.Error:
        return defaut


def ecrire(cle, valeur):
    subprocess.run(["kwriteconfig6", "--file", "samaosrc", "--group", "Sauvegarde", "--key", cle, str(valeur)], check=False)


def chemin_xdg(cle, defaut):
    try:
        p = subprocess.run(["xdg-user-dir", cle], capture_output=True, text=True, timeout=5).stdout.strip()
        if p and p != os.path.expanduser("~"):
            return p
    except (OSError, subprocess.SubprocessError):
        pass
    return os.path.expanduser("~/" + defaut)


def dossiers_choisis(c):
    # (réglage absent : dossiers par défaut ; « aucun » : rien de coché)
    choisis = reglage(c, "dossiers")
    noms = set(choisis.split(",")) if choisis else {nom for _, nom, defaut in DOSSIERS if defaut}
    noms.discard("aucun")
    return [(nom, chemin_xdg(cle, nom)) for cle, nom, _ in DOSSIERS if nom in noms], noms


def taille(chemin):
    total = 0
    for racine, _, fichiers in os.walk(chemin):
        for f in fichiers:
            try:
                total += os.lstat(os.path.join(racine, f)).st_size
            except OSError:
                pass
    return total


def disques():
    """Partitions des clés et disques externes (branchés à chaud ou amovibles), avec un système de fichiers."""
    try:
        sortie = subprocess.run(["lsblk", "-J", "-b", "-o", "PATH,LABEL,UUID,MOUNTPOINT,HOTPLUG,RM,SIZE,FSTYPE,TYPE,MODEL"],
                                capture_output=True, text=True, timeout=10).stdout
        arbre = json.loads(sortie).get("blockdevices", [])
    except (OSError, ValueError, subprocess.SubprocessError):
        return []
    trouves = []

    def vrai(v):     # selon la version de lsblk : true/false ou "1"/"0"
        return v in (True, 1, "1", "true")

    def parcourir(appareils, externe, modele):
        for a in appareils:
            ext = externe or vrai(a.get("hotplug")) or vrai(a.get("rm"))
            mod = a.get("model") or modele
            if ext and a.get("uuid") and a.get("fstype") not in (None, "swap", "crypto_LUKS", "iso9660", "squashfs"):
                nom = a.get("label") or (mod or "Disque externe").strip()
                trouves.append({"uuid": a["uuid"], "nom": nom, "chemin": a.get("path"), "montage": a.get("mountpoint") or "",
                                "taille": int(a.get("size") or 0), "fs": a.get("fstype")})
            parcourir(a.get("children") or [], ext, mod)
    parcourir(arbre, False, "")
    return trouves


def lire_etat():
    try:
        return json.load(open(ETAT))
    except (OSError, ValueError):
        return {}


def etat(rapide=False):
    c = lire_config()
    uuid = reglage(c, "disque")
    branches = disques()
    disque = next((d for d in branches if d["uuid"] == uuid), None)
    dossiers, noms = dossiers_choisis(c)
    e = lire_etat()
    print(json.dumps({
        "disque": uuid, "nomDisque": reglage(c, "nomDisque"), "branche": disque is not None,
        "libre": libre(disque["montage"]) if disque and disque["montage"] else -1,
        "auto": reglage(c, "auto", "true") != "false", "frequence": reglage(c, "frequence", "jour"),
        "derniere": e.get("derniere", 0), "volume": e.get("volume", 0), "erreur": e.get("erreur", ""),
        "enCours": en_cours(),
        "dossiers": [{"nom": nom, "chemin": chemin_xdg(cle, nom), "choisi": nom in noms,
                      "taille": None if rapide else taille(chemin_xdg(cle, nom))} for cle, nom, _ in DOSSIERS],
        "disques": branches,
    }, ensure_ascii=False))


def libre(montage):
    try:
        s = os.statvfs(montage)
        return s.f_bavail * s.f_frsize
    except OSError:
        return -1


def en_cours():
    try:
        with open(VERROU, "a") as v:
            fcntl.flock(v, fcntl.LOCK_EX | fcntl.LOCK_NB)
            fcntl.flock(v, fcntl.LOCK_UN)
        return False
    except OSError:
        return True


def notifier(titre, texte, icone="drive-removable-media"):
    subprocess.run(["gdbus", "call", "--session", "--dest", "org.freedesktop.Notifications",
                    "--object-path", "/org/freedesktop/Notifications", "--method", "org.freedesktop.Notifications.Notify",
                    "Sama", "0", icone, titre, texte, "[]", "{}", "5000"], capture_output=True, check=False)


def monter(disque):
    """Point de montage du disque ; les clés branchées ne sont pas montées d'office : udisks le fait pour
    la personne connectée."""
    if disque["montage"]:
        return disque["montage"]
    subprocess.run(["udisksctl", "mount", "--no-user-interaction", "-b", disque["chemin"]], capture_output=True)
    return next((d["montage"] for d in disques() if d["uuid"] == disque["uuid"]), "")


def dossier_sauvegarde(montage):
    return os.path.join(montage, "Sauvegarde Sama", "%s@%s" % (os.environ.get("USER") or os.getlogin(), socket.gethostname()))


def ouvrir():
    uuid = reglage(lire_config(), "disque")
    disque = next((d for d in disques() if d["uuid"] == uuid), None) if uuid else None
    montage = monter(disque) if disque else ""
    if montage:
        chemin = dossier_sauvegarde(montage)
        print(chemin if os.path.isdir(chemin) else montage)
    return 0 if montage else 1


def sauvegarder(automatique=False):
    c = lire_config()
    uuid = reglage(c, "disque")
    disque = next((d for d in disques() if d["uuid"] == uuid), None) if uuid else None
    if disque is None:
        print("Disque de sauvegarde absent", file=sys.stderr)
        return 1
    montage = monter(disque)
    if not montage:
        enregistrer(erreur="Le disque « %s » n'a pas pu être ouvert." % disque["nom"])
        return 1
    with open(VERROU, "a") as verrou:
        # La page des Réglages prend le verrou un instant pour savoir si une sauvegarde tourne : quelques essais
        for essai in range(10):
            try:
                fcntl.flock(verrou, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except OSError:
                time.sleep(0.5)
        else:
            return 0     # une sauvegarde est déjà en cours
        enregistrer(derniereTentative=int(time.time()))
        base = dossier_sauvegarde(montage)
        date = time.strftime("%Y-%m-%d %Hh%M")
        erreurs = 0
        for nom, chemin in dossiers_choisis(c)[0]:
            if not os.path.isdir(chemin):
                continue
            destination = os.path.join(base, "Actuelle", nom)
            os.makedirs(destination, exist_ok=True)
            # Fichiers seulement (clés FAT32/exFAT) ; ce qui change ou disparaît part dans « Versions précédentes »
            r = subprocess.run(["rsync", "-rt", "--modify-window=2", "--delete", "--backup",
                                "--backup-dir=" + os.path.join(base, "Versions précédentes", date, nom),
                                "--exclude=.cache/", "--exclude=.Trash-*/", chemin.rstrip("/") + "/", destination + "/"],
                               capture_output=True, text=True)
            # 23/24 : quelques fichiers ignorés (nom interdit sur la clé, fichier effacé pendant la copie)
            if r.returncode not in (0, 23, 24):
                erreurs += 1
        # Les plus anciennes versions précédentes laissent la place (les 20 dernières sont gardées)
        anciennes = os.path.join(base, "Versions précédentes")
        if os.path.isdir(anciennes):
            for vieille in sorted(os.listdir(anciennes))[:-VERSIONS_GARDEES]:
                subprocess.run(["rm", "-rf", os.path.join(anciennes, vieille)], check=False)
        subprocess.run(["sync", "-f", base], check=False)
        volume = taille(os.path.join(base, "Actuelle"))
        if erreurs:
            deja = lire_etat().get("erreur")
            enregistrer(erreur="La sauvegarde n'a pas pu copier tous les dossiers (disque plein ?).", volume=volume)
            if not deja:     # (une seule fois, pas à chaque tentative automatique)
                notifier("Sauvegarde incomplète", "Certains dossiers n'ont pas pu être copiés sur « %s »." % disque["nom"], "dialog-warning")
            return 1
        enregistrer(derniere=int(time.time()), volume=volume, erreur="")
        if automatique:
            notifier("Sauvegarde terminée", "Vos documents sont copiés sur « %s »." % disque["nom"])
    return 0


def enregistrer(**valeurs):
    e = lire_etat()
    e.update(valeurs)
    os.makedirs(os.path.dirname(ETAT), exist_ok=True)
    json.dump(e, open(ETAT, "w"))


def auto():
    c = lire_config()
    if not reglage(c, "disque") or reglage(c, "auto", "true") == "false":
        return 0
    intervalle = INTERVALLES.get(reglage(c, "frequence", "jour"), 86400)
    e = lire_etat()
    if time.time() - max(e.get("derniere", 0), e.get("derniereTentative", 0)) < intervalle:
        return 0
    if not any(d["uuid"] == reglage(c, "disque") for d in disques()):
        return 0
    return sauvegarder(automatique=True)


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    if action == "etat":
        etat(rapide=len(sys.argv) > 2 and sys.argv[2] == "rapide")
    elif action == "disques":
        print(json.dumps(disques(), ensure_ascii=False))
    elif action == "choisir" and len(sys.argv) > 2:
        d = next((d for d in disques() if d["uuid"] == sys.argv[2]), None)
        ecrire("disque", sys.argv[2])
        ecrire("nomDisque", d["nom"] if d else "")
    elif action == "sauvegarder":
        sys.exit(sauvegarder())
    elif action == "auto":
        sys.exit(auto())
    elif action == "ouvrir":
        sys.exit(ouvrir())
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
