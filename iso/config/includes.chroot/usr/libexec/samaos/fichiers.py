#!/usr/bin/env python3
"""Moteur de Fichiers, l'explorateur de Sama (/usr/lib/samaos/fichiers) : ce que le QML ne sait pas faire seul.

  fichiers.py info CHEMIN                 JSON : type, taille, dates, application qui l'ouvre
  fichiers.py appareils                   JSON : disque interne et clés / disques externes (espace libre)
  fichiers.py monter PÉRIPHÉRIQUE         point de montage (la clé est montée au besoin)
  fichiers.py ejecter PÉRIPHÉRIQUE        démonte et met hors tension (la clé peut être retirée)
  fichiers.py corbeille                   JSON : éléments de la corbeille (efface ceux de plus de 30 jours)
  fichiers.py restaurer NOM               remet un élément de la corbeille à sa place d'origine
  fichiers.py vider                       vide la corbeille
  fichiers.py jeter CHEMIN…               met à la corbeille
  fichiers.py renommer CHEMIN NOM         renomme (refuse d'écraser)
  fichiers.py nouveau-dossier DOSSIER     crée « Nouveau dossier » (numéroté s'il existe), affiche son chemin
  fichiers.py recents                     JSON : fichiers ouverts récemment (toutes applications)
  fichiers.py chercher DOSSIER TEXTE      JSON : éléments du dossier et de ses sous-dossiers dont le nom contient tous
                                          les mots du texte (sans tenir compte des accents ni des majuscules)
  fichiers.py analyser PÉRIPHÉRIQUE       JSON : clé montée au besoin, espace utilisé, nombre de photos, documents…
  fichiers.py importer TACHE PÉRIPHÉRIQUE copie les photos de la clé dans Images/« Photos de <clé> <date> »
                                          (avancement comme une copie, voir plus bas)
  fichiers.py copier|deplacer|deposer TACHE DESTINATION SOURCE…
                                          copie ou déplace (« deposer », glisser-déposer : déplace sur le même disque,
                                          copie vers un autre) ; l'avancement est écrit dans TACHE.json (dossier
                                          $XDG_RUNTIME_DIR/samaos-fichiers), les choix en cas de conflit sont lus
                                          dans TACHE.choix (« remplacer|garder|ignorer [tous] »), les ordres dans
                                          TACHE.ordre (« pause|reprendre|annuler »)
"""
import datetime
import json
import os
import shutil
import subprocess
import sys
import time
import unicodedata
import urllib.parse
import xml.etree.ElementTree as ET

CORBEILLE = os.path.join(os.environ.get("XDG_DATA_HOME", os.path.expanduser("~/.local/share")), "Trash")
DUREE_CORBEILLE = 30          # jours
TACHES = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "samaos-fichiers")

# Familles de fichiers : extensions → (type affiché, famille)
FAMILLES = {
    "Document": ("doc docx odt rtf txt md pages wpd", "texte"),
    "Tableur": ("xls xlsx ods csv numbers", "tableur"),
    "Présentation": ("ppt pptx odp key", "presentation"),
    "Document PDF": ("pdf", "pdf"),
    "Image": ("jpg jpeg png gif webp bmp svg heic tif tiff", "image"),
    "Audio": ("mp3 ogg oga wav flac m4a aac opus", "audio"),
    "Vidéo": ("mp4 mkv webm avi mov m4v 3gp", "video"),
    "Archive": ("zip tar gz bz2 xz 7z rar tgz zst", "archive"),
}
# Applications provisoires et leur nom Sama (fichier .desktop par défaut → lanceur Sama)
APPLIS_SAMA = {
    "libreoffice-writer.desktop": "samaos-docs.desktop", "libreoffice-calc.desktop": "samaos-sheet.desktop",
    "libreoffice-impress.desktop": "samaos-presentations.desktop", "org.kde.gwenview.desktop": "samaos-photos.desktop",
    "chromium.desktop": "samaos-griot.desktop",
}
DOSSIERS_APPLIS = ["/usr/share/applications", os.path.expanduser("~/.local/share/applications")]


def sortie(objet):
    print(json.dumps(objet, ensure_ascii=False))


def famille(nom, dossier=False):
    if dossier:
        return "Dossier", "dossier"
    ext = nom.rsplit(".", 1)[-1].lower() if "." in nom else ""
    for libelle, (extensions, fam) in FAMILLES.items():
        if ext in extensions.split():
            return libelle, fam
    return ("Fichier " + ext.upper()) if ext else "Fichier", "autre"


def entree_desktop(identifiant):
    """Nom (français de préférence) et icône d'un fichier .desktop."""
    for d in DOSSIERS_APPLIS:
        chemin = os.path.join(d, identifiant)
        if not os.path.isfile(chemin):
            continue
        nom = nom_fr = icone = ""
        dans = False
        for ligne in open(chemin, encoding="utf-8", errors="replace"):
            ligne = ligne.strip()
            if ligne.startswith("["):
                dans = ligne == "[Desktop Entry]"
            elif dans and ligne.startswith("Name[fr]="):
                nom_fr = ligne.split("=", 1)[1]
            elif dans and ligne.startswith("Name="):
                nom = nom or ligne.split("=", 1)[1]
            elif dans and ligne.startswith("Icon="):
                icone = icone or ligne.split("=", 1)[1]
        return nom_fr or nom, icone
    return "", ""


def appli_par_defaut(chemin):
    try:
        mime = subprocess.run(["xdg-mime", "query", "filetype", chemin], capture_output=True, text=True, timeout=5).stdout.strip()
        appli = subprocess.run(["xdg-mime", "query", "default", mime], capture_output=True, text=True, timeout=5).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return "", "", ""
    if not appli:
        return mime, "", ""
    appli = APPLIS_SAMA.get(appli, appli)
    nom, icone = entree_desktop(appli)
    return mime, nom, icone


def taille_dossier(chemin, limite=20000):
    """Taille et nombre d'éléments (arrêt après 20 000 fichiers : assez pour un ordre de grandeur)."""
    total = n = 0
    for racine, dossiers, fichiers in os.walk(chemin):
        for f in fichiers:
            n += 1
            try:
                total += os.lstat(os.path.join(racine, f)).st_size
            except OSError:
                pass
            if n >= limite:
                return total, n
    return total, n


def info(chemin):
    chemin = os.path.abspath(chemin)
    try:
        st = os.stat(chemin)
    except OSError as e:
        return sortie({"erreur": str(e)})
    dossier = os.path.isdir(chemin)
    libelle, fam = famille(os.path.basename(chemin), dossier)
    resultat = {"nom": os.path.basename(chemin) or chemin, "chemin": chemin, "type": libelle, "famille": fam,
                "modifie": int(st.st_mtime), "emplacement": os.path.basename(os.path.dirname(chemin)) or "/",
                "taille": st.st_size, "elements": 0, "mime": "", "appli": "", "icone": ""}
    if dossier:
        try:
            resultat["elements"] = len([f for f in os.listdir(chemin) if not f.startswith(".")])   # (sans les fichiers cachés)
        except OSError:
            pass
    else:
        resultat["mime"], resultat["appli"], resultat["icone"] = appli_par_defaut(chemin)
    sortie(resultat)


def vrai(v):
    return v in (True, 1, "1", "true")


def appareils():
    res = []
    try:
        s = os.statvfs(os.path.expanduser("~"))
        res.append({"type": "interne", "nom": "Disque interne", "chemin": "", "montage": os.path.expanduser("~"),
                    "taille": s.f_blocks * s.f_frsize, "libre": s.f_bavail * s.f_frsize})
    except OSError:
        pass
    try:
        arbre = json.loads(subprocess.run(["lsblk", "-J", "-b", "-o", "PATH,LABEL,UUID,MOUNTPOINT,HOTPLUG,RM,SIZE,FSTYPE,MODEL"],
                                          capture_output=True, text=True, timeout=10).stdout).get("blockdevices", [])
    except (OSError, ValueError, subprocess.SubprocessError):
        arbre = []

    def parcourir(appareils_, externe, modele):
        for a in appareils_:
            ext = externe or vrai(a.get("hotplug")) or vrai(a.get("rm"))
            mod = (a.get("model") or modele or "").strip()
            if ext and a.get("uuid") and a.get("fstype") not in (None, "swap", "crypto_LUKS", "iso9660", "squashfs"):
                libre = -1
                if a.get("mountpoint"):
                    try:
                        s = os.statvfs(a["mountpoint"])
                        libre = s.f_bavail * s.f_frsize
                    except OSError:
                        pass
                res.append({"type": "externe", "nom": a.get("label") or mod or "Clé USB", "chemin": a.get("path"),
                            "montage": a.get("mountpoint") or "", "taille": int(a.get("size") or 0), "libre": libre})
            parcourir(a.get("children") or [], ext, mod)
    parcourir(arbre, False, "")
    sortie(res)


def monter(peripherique):
    point = subprocess.run(["lsblk", "-no", "MOUNTPOINT", peripherique], capture_output=True, text=True).stdout.strip()
    if not point:
        subprocess.run(["udisksctl", "mount", "--no-user-interaction", "-b", peripherique], capture_output=True)
        point = subprocess.run(["lsblk", "-no", "MOUNTPOINT", peripherique], capture_output=True, text=True).stdout.strip()
    print(point)
    return 0 if point else 1


def ejecter(peripherique):
    r = subprocess.run(["udisksctl", "unmount", "--no-user-interaction", "-b", peripherique], capture_output=True, text=True)
    if r.returncode != 0 and "not mounted" not in r.stderr.lower():
        print(r.stderr.strip(), file=sys.stderr)
        return 1
    # La clé entière (sda pour sda1) est mise hors tension : on peut la retirer sans risque
    disque = subprocess.run(["lsblk", "-no", "PKNAME", peripherique], capture_output=True, text=True).stdout.strip()
    subprocess.run(["udisksctl", "power-off", "--no-user-interaction", "-b", "/dev/" + disque if disque else peripherique],
                   capture_output=True)
    return 0


# ——— Corbeille (spécification freedesktop : Trash/files et Trash/info/*.trashinfo) ———

def elements_corbeille():
    infos = os.path.join(CORBEILLE, "info")
    res = []
    if not os.path.isdir(infos):
        return res
    for f in os.listdir(infos):
        if not f.endswith(".trashinfo"):
            continue
        nom = f[:-len(".trashinfo")]
        origine = date = ""
        for ligne in open(os.path.join(infos, f), encoding="utf-8", errors="replace"):
            if ligne.startswith("Path="):
                origine = urllib.parse.unquote(ligne[5:].strip())
            elif ligne.startswith("DeletionDate="):
                date = ligne[13:].strip()
        try:
            supprime = datetime.datetime.fromisoformat(date)
        except ValueError:
            supprime = datetime.datetime.now()
        res.append({"id": nom, "origine": origine, "supprime": supprime,
                    "fichier": os.path.join(CORBEILLE, "files", nom), "info": os.path.join(infos, f)})
    return res


def effacer(chemin):
    if os.path.isdir(chemin) and not os.path.islink(chemin):
        shutil.rmtree(chemin, ignore_errors=True)
    else:
        try:
            os.remove(chemin)
        except OSError:
            pass


def corbeille():
    maintenant = datetime.datetime.now()
    res = []
    for e in elements_corbeille():
        age = (maintenant - e["supprime"]).days
        if age >= DUREE_CORBEILLE:      # supprimé définitivement après 30 jours
            effacer(e["fichier"])
            effacer(e["info"])
            continue
        dossier = os.path.isdir(e["fichier"])
        taille = taille_dossier(e["fichier"])[0] if dossier else (os.lstat(e["fichier"]).st_size if os.path.lexists(e["fichier"]) else 0)
        libelle, fam = famille(os.path.basename(e["origine"]) or e["id"], dossier)
        res.append({"id": e["id"], "nom": os.path.basename(e["origine"]) or e["id"], "origine": e["origine"],
                    "dossierOrigine": os.path.basename(os.path.dirname(e["origine"])), "taille": taille,
                    "supprime": int(e["supprime"].timestamp()), "joursRestants": DUREE_CORBEILLE - age,
                    "famille": fam, "type": libelle})
    res.sort(key=lambda x: -x["supprime"])
    sortie(res)


def nom_libre(chemin):
    """« Rapport.docx » → « Rapport (2).docx » s'il existe déjà."""
    if not os.path.lexists(chemin):
        return chemin
    dossier, nom = os.path.split(chemin)
    base, ext = (nom.rsplit(".", 1)[0], "." + nom.rsplit(".", 1)[1]) if "." in nom[1:] else (nom, "")
    n = 2
    while os.path.lexists(os.path.join(dossier, "%s (%d)%s" % (base, n, ext))):
        n += 1
    return os.path.join(dossier, "%s (%d)%s" % (base, n, ext))


def restaurer(identifiant):
    e = next((x for x in elements_corbeille() if x["id"] == identifiant), None)
    if not e:
        return 1
    destination = nom_libre(e["origine"])
    os.makedirs(os.path.dirname(destination), exist_ok=True)
    shutil.move(e["fichier"], destination)
    effacer(e["info"])
    print(destination)
    return 0


def vider():
    for sous in ("files", "info", "expunged"):
        d = os.path.join(CORBEILLE, sous)
        if os.path.isdir(d):
            for f in os.listdir(d):
                effacer(os.path.join(d, f))
    # (le cache de taille de la corbeille, utilisé par KDE, repart de zéro)
    effacer(os.path.join(CORBEILLE, "directorysizes"))
    return 0


def jeter(chemins):
    r = subprocess.run(["gio", "trash", "--"] + chemins, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr.strip(), file=sys.stderr)
    return r.returncode


def renommer(chemin, nom):
    if "/" in nom or nom in ("", ".", ".."):
        print("Nom impossible", file=sys.stderr)
        return 1
    destination = os.path.join(os.path.dirname(chemin), nom)
    if os.path.lexists(destination):
        print("Un élément porte déjà ce nom", file=sys.stderr)
        return 1
    os.rename(chemin, destination)
    print(destination)
    return 0


def nouveau_dossier(parent):
    chemin = nom_libre(os.path.join(parent, "Nouveau dossier"))
    os.makedirs(chemin)
    print(chemin)
    return 0


def recents():
    """Fichiers ouverts récemment : par les applications KDE (base des activités de Plasma, qui gère aussi les
    Espaces) et par les autres (recently-used.xbel, LibreOffice, Chromium…)."""
    donnees = os.environ.get("XDG_DATA_HOME", os.path.expanduser("~/.local/share"))
    trouves = {}

    def ajouter(chemin, quand):
        if chemin.startswith("file://"):
            chemin = urllib.parse.unquote(chemin[7:])
        if chemin.startswith("/") and os.path.isfile(chemin):
            trouves[chemin] = max(quand, trouves.get(chemin, 0))
    try:
        import sqlite3
        base = sqlite3.connect("file:" + os.path.join(donnees, "kactivitymanagerd/resources/database") + "?mode=ro", uri=True)
        for ressource, quand in base.execute("SELECT targettedResource, MAX(lastUpdate) FROM ResourceScoreCache "
                                             "WHERE targettedResource LIKE '/%' OR targettedResource LIKE 'file://%' "
                                             "GROUP BY targettedResource"):
            ajouter(ressource, int(quand or 0))
    except Exception:
        pass
    try:
        for b in ET.parse(os.path.join(donnees, "recently-used.xbel")).getroot().findall("bookmark"):
            try:
                quand = int(datetime.datetime.fromisoformat((b.get("visited") or b.get("modified")).replace("Z", "+00:00")).timestamp())
            except (AttributeError, TypeError, ValueError):
                quand = 0
            ajouter(b.get("href", ""), quand)
    except (OSError, ET.ParseError):
        pass
    res = [{"chemin": c, "nom": os.path.basename(c), "quand": q} for c, q in trouves.items()]
    res.sort(key=lambda x: -x["quand"])
    sortie(res[:60])


# ——— Copie et déplacement, avec avancement et conflits ———

# ——— Recherche par nom ———

def simplifier(texte):
    """« Été_Rapport » → « ete rapport » : sans accents, en minuscules, séparateurs en espaces."""
    t = unicodedata.normalize("NFD", texte.lower())
    t = "".join(c for c in t if not unicodedata.combining(c))
    return t.replace("_", " ").replace("-", " ").replace(".", " ")


def chercher(racine, texte, limite=300, duree=3.0):
    mots = simplifier(texte).split()
    if not mots:
        return sortie([])
    debut = time.time()
    trouves = []
    for dossier, dossiers, fichiers in os.walk(racine):
        dossiers[:] = sorted(d for d in dossiers if not d.startswith("."))
        for nom, est_dossier in [(d, True) for d in dossiers] + [(f, False) for f in fichiers if not f.startswith(".")]:
            simple = simplifier(nom)
            if all(m in simple for m in mots):
                chemin = os.path.join(dossier, nom)
                try:
                    st = os.stat(chemin)
                except OSError:
                    continue
                trouves.append({"chemin": chemin, "nom": nom, "dossier": est_dossier, "taille": st.st_size,
                                "modifie": int(st.st_mtime), "parent": os.path.relpath(dossier, racine),
                                "debut": simple.startswith(mots[0])})
                if len(trouves) >= limite:
                    break
        if len(trouves) >= limite or time.time() - debut > duree:
            break
    # Les noms qui commencent par le texte cherché d'abord, puis les dossiers, puis par ordre alphabétique
    trouves.sort(key=lambda r: (not r["debut"], not r["dossier"], simplifier(r["nom"])))
    for r in trouves:
        del r["debut"]
        if r["parent"] == ".":
            r["parent"] = ""
    sortie(trouves)


# ——— Clé USB branchée (carte « Clé USB détectée » du Pouls) ———

def contenu_cle(montage, limite=50000):
    """Fichiers de la clé, par famille (sans les fichiers cachés ni la sauvegarde Sama qu'elle porte peut-être)."""
    familles = {"image": [], "texte": 0, "tableur": 0, "presentation": 0, "pdf": 0, "video": 0, "audio": 0}
    n = 0
    for racine, dossiers, fichiers in os.walk(montage):
        dossiers[:] = [d for d in dossiers if not d.startswith(".") and d not in ("Sauvegarde Sama", "System Volume Information", "$RECYCLE.BIN")]
        for f in fichiers:
            if f.startswith("."):
                continue
            fam = famille(f)[1]
            if fam == "image":
                familles["image"].append(os.path.join(racine, f))
            elif fam in familles:
                familles[fam] += 1
            n += 1
            if n >= limite:
                return familles
    return familles


def analyser(peripherique):
    if monter_silencieux(peripherique) is None:
        return sortie({"erreur": "La clé n'a pas pu être ouverte"})
    montage = subprocess.run(["lsblk", "-no", "MOUNTPOINT", peripherique], capture_output=True, text=True).stdout.strip()
    s = os.statvfs(montage)
    c = contenu_cle(montage)
    sortie({"montage": montage, "taille": s.f_blocks * s.f_frsize, "utilise": (s.f_blocks - s.f_bfree) * s.f_frsize,
            "photos": len(c["image"]), "documents": c["texte"] + c["tableur"] + c["presentation"] + c["pdf"],
            "videos": c["video"], "musiques": c["audio"], "antivirus": shutil.which("clamscan") is not None})


def monter_silencieux(peripherique):
    point = subprocess.run(["lsblk", "-no", "MOUNTPOINT", peripherique], capture_output=True, text=True).stdout.strip()
    if not point:
        subprocess.run(["udisksctl", "mount", "--no-user-interaction", "-b", peripherique], capture_output=True)
        point = subprocess.run(["lsblk", "-no", "MOUNTPOINT", peripherique], capture_output=True, text=True).stdout.strip()
    return point or None


def importer(nom, peripherique):
    montage = monter_silencieux(peripherique)
    if not montage:
        return 1
    photos = contenu_cle(montage)["image"]
    images = subprocess.run(["xdg-user-dir", "PICTURES"], capture_output=True, text=True).stdout.strip() or os.path.expanduser("~/Images")
    etiquette = subprocess.run(["lsblk", "-no", "LABEL", peripherique], capture_output=True, text=True).stdout.strip() or "la clé"
    mois = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]
    jour = datetime.date.today()
    destination = nom_libre(os.path.join(images, "Photos de %s, %d %s %d" % (etiquette, jour.day, mois[jour.month - 1], jour.year)))
    os.makedirs(destination)
    tache = Tache(nom, "copier", destination, photos)
    tache.choix_tous = "garder"       # deux photos du même nom (dossiers différents de la clé) : on garde les deux
    tache.etat["photos"] = len(photos)
    tache.lancer()
    return 0


def point_de_montage(chemin):
    chemin = os.path.realpath(chemin)
    while not os.path.ismount(chemin):
        chemin = os.path.dirname(chemin)
    return chemin


class Tache:
    def __init__(self, nom, operation, destination, sources):
        os.makedirs(TACHES, exist_ok=True)
        self.base = os.path.join(TACHES, nom)
        self.operation, self.destination, self.sources = operation, destination, sources
        self.etat = {"etat": "preparation", "operation": operation, "destination": destination,
                     "nomDestination": os.path.basename(destination.rstrip("/")) or destination,
                     "elements": len(sources), "total": 0, "fait": 0, "fichier": "", "debut": time.time(),
                     "conflit": None, "conflits": 0, "erreurs": 0}
        self.choix_tous = None
        self.dernier = 0

    def ecrire(self, force=False):
        if not force and time.time() - self.dernier < 0.25:
            return
        self.dernier = time.time()
        temporaire = self.base + ".json.nouveau"
        with open(temporaire, "w") as f:
            json.dump(self.etat, f, ensure_ascii=False)
        os.replace(temporaire, self.base + ".json")

    def ordres(self):
        """Pause, reprise, annulation demandées par la fenêtre."""
        while True:
            try:
                ordre = open(self.base + ".ordre").read().strip()
            except OSError:
                ordre = ""
            if ordre == "annuler":
                raise KeyboardInterrupt
            if ordre != "pause":
                if self.etat["etat"] == "pause":
                    self.etat["etat"] = "en_cours"
                    self.ecrire(True)
                return
            if self.etat["etat"] != "pause":
                self.etat["etat"] = "pause"
                self.ecrire(True)
            time.sleep(0.3)

    def decider(self, source, cible):
        """Conflit : on attend le choix de la personne (remplacer, garder les deux, ignorer)."""
        if self.choix_tous:
            return self.choix_tous
        def decrire(p, ou):
            st = os.stat(p)
            return {"nom": os.path.basename(p), "ou": ou, "modifie": int(st.st_mtime), "taille": st.st_size}
        self.etat["conflits"] += 1
        self.etat["conflit"] = {"source": decrire(source, os.path.basename(os.path.dirname(source))),
                                "cible": decrire(cible, os.path.basename(os.path.dirname(cible))),
                                "copie": os.path.basename(nom_libre(cible))}
        self.etat["etat"] = "conflit"
        self.ecrire(True)
        choix_fichier = self.base + ".choix"
        while True:
            self.ordres()
            try:
                choix = open(choix_fichier).read().split()
                os.remove(choix_fichier)
                break
            except OSError:
                time.sleep(0.25)
        if len(choix) > 1 and choix[1] == "tous":
            self.choix_tous = choix[0]
        self.etat["conflit"] = None
        self.etat["etat"] = "en_cours"
        self.ecrire(True)
        return choix[0] if choix else "ignorer"

    def copier_fichier(self, source, cible):
        if os.path.lexists(cible):
            choix = self.decider(source, cible)
            if choix == "ignorer":
                self.etat["fait"] += os.lstat(source).st_size
                return
            if choix == "garder":
                cible = nom_libre(cible)
        self.etat["fichier"] = os.path.basename(source)
        if os.path.islink(source):
            if os.path.lexists(cible):
                os.remove(cible)
            os.symlink(os.readlink(source), cible)
            return
        temporaire = cible + ".sama-copie"
        with open(source, "rb") as fs, open(temporaire, "wb") as fc:
            while True:
                self.ordres()
                bloc = fs.read(1024 * 1024)
                if not bloc:
                    break
                fc.write(bloc)
                self.etat["fait"] += len(bloc)
                self.ecrire()
        try:
            shutil.copystat(source, temporaire)
        except OSError:
            pass      # (clés FAT32 : pas de droits ni de dates fines)
        os.replace(temporaire, cible)

    def copier(self, source, cible):
        if os.path.isdir(source) and not os.path.islink(source):
            if os.path.exists(cible) and not os.path.isdir(cible):
                cible = nom_libre(cible)
            os.makedirs(cible, exist_ok=True)
            for f in sorted(os.listdir(source)):
                self.copier(os.path.join(source, f), os.path.join(cible, f))
        else:
            try:
                self.copier_fichier(source, cible)
            except OSError:
                self.etat["erreurs"] += 1

    def lancer(self):
        try:
            if self.operation == "deposer":
                # Glisser-déposer : déplacer sur le même disque, copier vers une clé ou un autre disque
                # (même point de montage : les numéros de périphérique ne suffisent pas, overlayfs de la session
                # d'essai en donne de différents aux fichiers et aux dossiers d'un même disque)
                disque = point_de_montage(self.destination)
                meme = all(point_de_montage(os.path.dirname(os.path.abspath(s))) == disque for s in self.sources)
                self.operation = "deplacer" if meme else "copier"
                self.etat["operation"] = self.operation
            for s in self.sources:
                self.etat["total"] += taille_dossier(s)[0] if os.path.isdir(s) else os.lstat(s).st_size
            self.etat["etat"] = "en_cours"
            self.ecrire(True)
            for s in self.sources:
                cible = os.path.join(self.destination, os.path.basename(s.rstrip("/")))
                source_abs = os.path.abspath(s)
                if os.path.abspath(self.destination) == source_abs or os.path.abspath(self.destination).startswith(source_abs + "/"):
                    self.etat["erreurs"] += 1           # un dossier ne peut pas aller dans lui-même
                    continue
                if os.path.abspath(cible) == os.path.abspath(s):
                    if self.operation == "deplacer":
                        continue                        # déplacé vers son propre dossier : rien à faire
                    cible = nom_libre(cible)            # copie dans le même dossier : « (2) »
                if self.operation == "deplacer" and not os.path.lexists(cible):
                    try:
                        os.rename(s, cible)             # même disque : instantané
                        self.etat["fait"] += taille_dossier(s)[0] if os.path.isdir(cible) else os.lstat(cible).st_size
                        continue
                    except OSError:
                        pass                            # autre disque : copie puis effacement
                avant = self.etat["erreurs"]
                self.copier(s, cible)
                if self.operation == "deplacer" and self.etat["erreurs"] == avant:
                    effacer(s)
            self.etat["etat"] = "termine"
        except KeyboardInterrupt:
            self.etat["etat"] = "annule"
        except OSError as e:
            self.etat["etat"] = "erreur"
            self.etat["message"] = str(e)
        self.etat["fin"] = time.time()
        self.ecrire(True)
        for suffixe in (".ordre", ".choix"):
            try:
                os.remove(self.base + suffixe)
            except OSError:
                pass


if __name__ == "__main__":
    a = sys.argv[1:]
    action = a[0] if a else ""
    code = 0
    if action == "info" and len(a) > 1:
        info(a[1])
    elif action == "appareils":
        appareils()
    elif action == "monter" and len(a) > 1:
        code = monter(a[1])
    elif action == "ejecter" and len(a) > 1:
        code = ejecter(a[1])
    elif action == "corbeille":
        corbeille()
    elif action == "restaurer" and len(a) > 1:
        code = restaurer(a[1])
    elif action == "vider":
        code = vider()
    elif action == "jeter" and len(a) > 1:
        code = jeter(a[1:])
    elif action == "renommer" and len(a) > 2:
        code = renommer(a[1], a[2])
    elif action == "nouveau-dossier" and len(a) > 1:
        code = nouveau_dossier(a[1])
    elif action == "recents":
        recents()
    elif action == "chercher" and len(a) > 2:
        chercher(a[1], " ".join(a[2:]))
    elif action == "analyser" and len(a) > 1:
        analyser(a[1])
    elif action == "importer" and len(a) > 2:
        code = importer(a[1], a[2])
    elif action in ("copier", "deplacer", "deposer") and len(a) > 3:
        Tache(a[1], action, a[2], a[3:]).lancer()
    else:
        print(__doc__, file=sys.stderr)
        code = 1
    sys.exit(code)
