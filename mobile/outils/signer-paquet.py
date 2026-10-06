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
    shutil.copyfile(o.apk, os.path.join(o.dossier, "fichiers", fiche["sha256"]))
    chemin = os.path.join(o.dossier, "catalogue.json")
    catalogue = json.load(open(chemin)) if os.path.exists(chemin) else {"point": {"nom": "Point Sama d'essai"}, "paquets": []}
    catalogue["paquets"] = [p for p in catalogue["paquets"] if p["paquet"] != paquet] + [fiche]
    json.dump(catalogue, open(chemin, "w"), ensure_ascii=False, indent=1)
    print(f"{o.nom} ({paquet} {version_nom}, {len(octets) // 1024} Ko) ajouté à {chemin}")


if __name__ == "__main__":
    main()
