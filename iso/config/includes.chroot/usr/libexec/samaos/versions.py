#!/usr/bin/env python3
"""Versions des classeurs de Sama Sheet : une copie du fichier à chaque enregistrement, et à l'ouverture s'il a changé
depuis (un fichier reçu, modifié ailleurs). Elles restent sur l'ordinateur, dans ~/.local/share/samaos/versions/<clé>/,
jamais dans le fichier : elles ne partent pas avec lui.

  versions.py garder CHEMIN RAISON          copie le fichier s'il a changé depuis la dernière version ; RAISON :
                                            enregistrement | ouverture ; JSON de la version gardée, sinon {}
  versions.py liste CHEMIN                  JSON : {"versions": [{id, quand, raison, nom, source, taille, fichier}]}
                                            (les plus récentes d'abord)
  versions.py nommer CHEMIN ID NOM          un nom pour une version (« Avant la réunion ») : elle est toujours gardée
  versions.py restaurer CHEMIN ID           le fichier redevient cette version ; son état d'avant est gardé d'abord
  versions.py copier CHEMIN ID DESTINATION  une copie de la version ailleurs (Enregistrer une copie)

Gardées : toutes celles des deux derniers jours, puis la dernière de chaque jour pendant 90 jours, et les versions
nommées ; 200 au plus par fichier. Les fichiers de plus de 50 Mo n'ont pas de versions.
"""
import fcntl
import hashlib
import json
import os
import shutil
import sys
import time
import zipfile

DOSSIER = os.path.join(os.environ.get("XDG_DATA_HOME", os.path.expanduser("~/.local/share")), "samaos", "versions")
TAILLE_MAX = 50 * 1024 * 1024
RECENTES = 2 * 86400
JOURS = 90 * 86400
NOMBRE_MAX = 200
# (dans un .xlsx ou un .ods : la date d'enregistrement change à chaque fois, le contenu peut-être pas)
SANS_CONTENU = {"docProps/core.xml", "docProps/app.xml", "meta.xml"}


def dossier_de(chemin):
    cle = hashlib.sha256(os.path.realpath(chemin).encode()).hexdigest()[:24]
    return os.path.join(DOSSIER, cle)


def empreinte(chemin):
    """Le contenu du fichier, sans sa date d'enregistrement"""
    h = hashlib.sha256()
    try:
        with zipfile.ZipFile(chemin) as z:
            for i in sorted(z.infolist(), key=lambda i: i.filename):
                if i.filename not in SANS_CONTENU:
                    h.update(("%s:%08x:%d\n" % (i.filename, i.CRC, i.file_size)).encode())
        return "z:" + h.hexdigest()
    except (zipfile.BadZipFile, OSError):
        pass
    with open(chemin, "rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    return "f:" + h.hexdigest()


class Index:
    """index.json du dossier des versions d'un fichier, verrouillé le temps d'une opération"""

    def __init__(self, chemin):
        self.chemin = os.path.realpath(chemin)
        self.dossier = dossier_de(chemin)
        os.makedirs(self.dossier, mode=0o700, exist_ok=True)
        os.chmod(DOSSIER, 0o700)
        self.verrou = open(os.path.join(self.dossier, ".verrou"), "w")
        fcntl.flock(self.verrou, fcntl.LOCK_EX)
        try:
            with open(os.path.join(self.dossier, "index.json")) as f:
                self.donnees = json.load(f)
        except (OSError, ValueError):
            self.donnees = {}
        self.donnees["chemin"] = self.chemin
        self.versions = self.donnees.setdefault("versions", [])

    def ecrire(self):
        nouveau = os.path.join(self.dossier, "index.json.nouveau")
        with open(nouveau, "w") as f:
            json.dump(self.donnees, f, ensure_ascii=False, indent=1)
        os.replace(nouveau, os.path.join(self.dossier, "index.json"))

    def fichier(self, v):
        return os.path.join(self.dossier, v["fichier"])

    def trouver(self, ident):
        for v in self.versions:
            if v["id"] == ident and os.path.exists(self.fichier(v)):
                return v
        raise SystemExit("version introuvable : " + ident)

    def garder(self, raison, source=""):
        if not os.path.isfile(self.chemin) or os.path.getsize(self.chemin) > TAILLE_MAX:
            return None
        e = empreinte(self.chemin)
        if self.versions and self.versions[-1].get("empreinte") == e and os.path.exists(self.fichier(self.versions[-1])):
            return None
        quand = time.time()
        ident = time.strftime("%Y%m%d-%H%M%S", time.localtime(quand))
        n = 2
        while any(v["id"] == ident for v in self.versions):
            ident = time.strftime("%Y%m%d-%H%M%S", time.localtime(quand)) + "-%d" % n
            n += 1
        extension = os.path.splitext(self.chemin)[1].lower() or ".xlsx"
        v = {"id": ident, "quand": int(quand), "raison": raison, "nom": "", "source": source,
             "taille": os.path.getsize(self.chemin), "empreinte": e, "fichier": ident + extension}
        copie = self.fichier(v) + ".nouveau"
        shutil.copyfile(self.chemin, copie)
        os.chmod(copie, 0o600)
        os.replace(copie, self.fichier(v))
        self.versions.append(v)
        self.elaguer()
        self.ecrire()
        return v

    def elaguer(self):
        maintenant = time.time()
        gardees, jours = [], set()
        for v in sorted(self.versions, key=lambda v: v["quand"], reverse=True):
            age = maintenant - v["quand"]
            jour = time.strftime("%Y%m%d", time.localtime(v["quand"]))
            if v.get("nom") or age < RECENTES or (age < JOURS and jour not in jours):
                gardees.append(v)
            jours.add(jour)
        # (au-delà du nombre permis : les plus anciennes sans nom s'en vont)
        while len(gardees) > NOMBRE_MAX:
            sans_nom = [v for v in gardees if not v.get("nom")]
            if not sans_nom:
                break
            gardees.remove(sans_nom[-1])
        restent = {v["id"] for v in gardees}
        for v in self.versions:
            if v["id"] not in restent:
                try:
                    os.remove(self.fichier(v))
                except OSError:
                    pass
        self.versions[:] = sorted(gardees, key=lambda v: v["quand"])

    def public(self, v):
        r = {k: v.get(k, "") for k in ("id", "quand", "raison", "nom", "source", "taille")}
        r["fichier"] = self.fichier(v)
        return r


def principal(args):
    if len(args) < 2:
        raise SystemExit(__doc__)
    action, chemin = args[0], args[1]
    index = Index(chemin)
    if action == "garder":
        v = index.garder(args[2] if len(args) > 2 else "enregistrement")
        return index.public(v) if v else {}
    if action == "liste":
        return {"versions": [index.public(v) for v in reversed(index.versions) if os.path.exists(index.fichier(v))]}
    if action == "nommer":
        v = index.trouver(args[2])
        v["nom"] = " ".join(args[3:]).strip()[:80]
        index.ecrire()
        return index.public(v)
    if action == "restaurer":
        v = index.trouver(args[2])
        # (l'état actuel d'abord : on peut revenir sur la restauration)
        index.garder("avant-restauration")
        nouveau = index.chemin + ".sama-restauration"
        shutil.copyfile(index.fichier(v), nouveau)
        try:
            shutil.copymode(index.chemin, nouveau)
        except OSError:
            pass
        os.replace(nouveau, index.chemin)
        index.garder("restauree", v["id"])
        return {"restauree": v["id"]}
    if action == "copier":
        v = index.trouver(args[2])
        destination = args[3]
        shutil.copyfile(index.fichier(v), destination)
        return {"copie": destination}
    raise SystemExit(__doc__)


if __name__ == "__main__":
    try:
        print(json.dumps(principal(sys.argv[1:]), ensure_ascii=False))
    except SystemExit as e:
        if isinstance(e.code, str):
            print(json.dumps({"erreur": e.code}, ensure_ascii=False))
            sys.exit(1)
        raise
