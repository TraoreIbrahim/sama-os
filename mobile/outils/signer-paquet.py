#!/usr/bin/env python3
"""Fabrique la fiche signée d'une appli pour Proche en proche (innovation 6) et la range dans le dossier d'un point.

    outils/signer-paquet.py appli.apk --nom "Essais Sama" [--dossier outils/point-essai]

La fiche dit le paquet, la version, la taille, l'empreinte SHA-256 et le certificat de l'appli ; elle est signée
avec la clé d'éditeur de TEST (systeme/cles-proches/editeur-test.pem, émulateur seulement). Le fichier est rangé
sous son empreinte dans <dossier>/fichiers/, la fiche dans <dossier>/catalogue.json.
"""
import argparse, base64, glob, hashlib, json, os, re, shutil, subprocess, sys, tempfile

ICI = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(ICI)
SDK = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk"))


def outil(nom):
    chemins = sorted(glob.glob(os.path.join(SDK, "build-tools", "*", nom)))
    if not chemins:
        sys.exit(f"{nom} introuvable dans {SDK}/build-tools")
    return chemins[-1]


def main():
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    a.add_argument("apk")
    a.add_argument("--nom", required=True, help="nom lisible de l'appli")
    a.add_argument("--dossier", default=os.path.join(ICI, "point-essai"))
    a.add_argument("--editeur", default="test-emulateur")
    a.add_argument("--cle", default=os.path.join(MOBILE, "systeme", "cles-proches", "editeur-test.pem"))
    # La vitrine de Sugu (signée aussi) : ce que le magasin montre de l'appli.
    a.add_argument("--editeur-nom", default="")
    a.add_argument("--resume", default="")
    a.add_argument("--description", default="")
    a.add_argument("--categorie", default="", help="ecole, commerce, sante, argent, agriculture, langues, outils, jeux")
    a.add_argument("--hors-ligne", action="store_true", help="l'appli marche sans Internet")
    a.add_argument("--sans-traceur", action="store_true", help="aucun traceur publicitaire")
    a.add_argument("--droit", action="append", default=[], help="ce que l'appli demande, en clair (plusieurs fois)")
    a.add_argument("--icone", default=None, help="image PNG de l'icône (servie par son empreinte)")
    o = a.parse_args()
    if not os.path.exists(o.cle):
        subprocess.run(["sh", os.path.join(MOBILE, "systeme", "cles-proches", "fabriquer.sh")], check=True)

    badging = subprocess.run([outil("aapt2"), "dump", "badging", o.apk], capture_output=True, text=True, check=True).stdout
    m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging)
    if not m:
        sys.exit("Impossible de lire le paquet de l'appli")
    paquet, version, version_nom = m.group(1), int(m.group(2)), m.group(3)
    certs = subprocess.run([outil("apksigner"), "verify", "--print-certs", o.apk], capture_output=True, text=True, check=True).stdout
    c = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", certs)
    if not c:
        sys.exit("L'appli n'est pas signée")
    octets = open(o.apk, "rb").read()
    fiche = {
        "type": "appli", "paquet": paquet, "nom": o.nom, "version": version, "versionNom": version_nom,
        "taille": len(octets), "sha256": hashlib.sha256(octets).hexdigest(), "certificat": c.group(1), "editeur": o.editeur,
    }
    canonique = "\n".join(["sama-proches-1", fiche["type"], fiche["paquet"], fiche["nom"], str(fiche["version"]), fiche["versionNom"],
                           str(fiche["taille"]), fiche["sha256"], fiche["certificat"], fiche["editeur"]]).encode("utf-8")
    with tempfile.NamedTemporaryFile() as t:
        t.write(canonique)
        t.flush()
        sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", o.cle, t.name], capture_output=True, check=True).stdout
    fiche["signature"] = base64.b64encode(sig).decode()

    os.makedirs(os.path.join(o.dossier, "fichiers"), exist_ok=True)
    icone = ""
    if o.icone:
        octets_icone = open(o.icone, "rb").read()
        icone = hashlib.sha256(octets_icone).hexdigest()
        open(os.path.join(o.dossier, "fichiers", icone), "wb").write(octets_icone)
    vitrine = {
        "paquet": paquet, "version": version, "editeurNom": o.editeur_nom, "resume": o.resume, "description": o.description,
        "categorie": o.categorie, "horsLigne": o.hors_ligne, "sansTraceur": o.sans_traceur, "icone": icone, "droits": o.droit, "editeur": o.editeur,
    }
    canon_v = "\n".join(["sugu-vitrine-1", paquet, str(version), o.editeur_nom, o.resume, o.description, o.categorie,
                         "1" if o.hors_ligne else "0", "1" if o.sans_traceur else "0", icone, "|".join(o.droit), o.editeur]).encode("utf-8")
    with tempfile.NamedTemporaryFile() as t:
        t.write(canon_v)
        t.flush()
        sig_v = subprocess.run(["openssl", "dgst", "-sha256", "-sign", o.cle, t.name], capture_output=True, check=True).stdout
    vitrine["signature"] = base64.b64encode(sig_v).decode()
    fiche["vitrine"] = vitrine

    os.makedirs(os.path.join(o.dossier, "fichiers"), exist_ok=True)
    shutil.copyfile(o.apk, os.path.join(o.dossier, "fichiers", fiche["sha256"]))
    chemin = os.path.join(o.dossier, "catalogue.json")
    catalogue = json.load(open(chemin)) if os.path.exists(chemin) else {"point": {"nom": "Point Sama d'essai"}, "paquets": []}
    catalogue["paquets"] = [p for p in catalogue["paquets"] if p["paquet"] != paquet] + [fiche]
    json.dump(catalogue, open(chemin, "w"), ensure_ascii=False, indent=1)
    print(f"{o.nom} ({paquet} {version_nom}, {len(octets) // 1024} Ko) ajouté à {chemin}")


if __name__ == "__main__":
    main()
