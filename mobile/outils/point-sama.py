#!/usr/bin/env python3
"""Un point Sama d'essai (Proche en proche, innovation 6) : sert le catalogue et les fichiers d'un dossier.

    outils/point-sama.py [--dossier outils/point-essai] [--port 8765] [--nom "Lycée du quartier"]

Protocole, version 1 (le même pour le futur point Sama du bureau et pour un téléphone qui donne) :
    GET /proches/v1/catalogue        → {"point": {"nom": …}, "paquets": [fiches signées]}
    GET /proches/v1/fichier/<sha256> → le fichier ; « Range: bytes=N- » pour reprendre (réponse 206)
Le point ne fait que fournir : il ne peut rien installer ni pousser, et le téléphone vérifie tout.
Depuis l'émulateur, le Mac est à l'adresse 10.0.2.2 : Réglages › Proche en proche › « Ajouter un point par son
adresse » › 10.0.2.2:8765. Sur un vrai réseau, le point s'annonce en mDNS (_samapoint._tcp).
"""
import argparse, json, os, re, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ICI = os.path.dirname(os.path.abspath(__file__))


def main():
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    a.add_argument("--dossier", default=os.path.join(ICI, "point-essai"))
    a.add_argument("--port", type=int, default=8765)
    a.add_argument("--nom", default=None)
    a.add_argument("--debit", type=int, default=0, help="Ko/s, pour simuler une liaison lente (0 : sans limite)")
    o = a.parse_args()

    class Point(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path == "/proches/v1/catalogue":
                c = json.load(open(os.path.join(o.dossier, "catalogue.json")))
                if o.nom:
                    c["point"] = {"nom": o.nom}
                corps = json.dumps(c, ensure_ascii=False).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(corps)))
                self.end_headers()
                self.wfile.write(corps)
                return
            m = re.fullmatch(r"/proches/v1/fichier/([0-9a-f]{64})", self.path)
            chemin = os.path.join(o.dossier, "fichiers", m.group(1)) if m else None
            if not chemin or not os.path.exists(chemin):
                self.send_error(404)
                return
            taille = os.path.getsize(chemin)
            debut = 0
            r = re.fullmatch(r"bytes=(\d+)-", self.headers.get("Range", ""))
            if r and int(r.group(1)) < taille:
                debut = int(r.group(1))
                self.send_response(206)
                self.send_header("Content-Range", f"bytes {debut}-{taille - 1}/{taille}")
            else:
                self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(taille - debut))
            self.end_headers()
            with open(chemin, "rb") as f:
                f.seek(debut)
                try:
                    while True:
                        morceau = f.read(16 * 1024)
                        if not morceau:
                            break
                        self.wfile.write(morceau)
                        if o.debit:
                            time.sleep(len(morceau) / (o.debit * 1024))
                except (BrokenPipeError, ConnectionResetError):
                    pass

        def log_message(self, fmt, *args):
            print(self.address_string(), fmt % args)

    print(f"Point Sama d'essai sur le port {o.port} (dossier {o.dossier})")
    ThreadingHTTPServer(("0.0.0.0", o.port), Point).serve_forever()


if __name__ == "__main__":
    main()
