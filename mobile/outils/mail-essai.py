#!/usr/bin/env python3
"""Un serveur de mail d'essai pour l'appli Mail, sur l'émulateur : GreenMail (IMAP 3143, SMTP 3025), sans
chiffrement. Mail ne l'accepte que vers le réseau local (l'émulateur voit le Mac en 10.0.2.2).

Les comptes ci-dessous sont des comptes d'essai, sur un domaine qui n'existe pas (sama.test) : jamais de vrais.

    outils/mail-essai.py            démarre le serveur et dépose quelques mails dans la boîte d'Awa
Dans Mail : Serveurs à la main, 10.0.2.2, ports 3143 et 3025, sécurité « Aucune ».
"""
import os
import smtplib
import subprocess
import sys
import time
import urllib.request
from email.message import EmailMessage
from email.utils import formatdate, make_msgid

ICI = os.path.dirname(os.path.abspath(__file__))
DOSSIER = os.path.join(ICI, "mail-essai")
VERSION = "1.6.15"
JAR = os.path.join(DOSSIER, f"greenmail-standalone-{VERSION}.jar")
URL = f"https://repo1.maven.org/maven2/com/icegreen/greenmail-standalone/{VERSION}/greenmail-standalone-{VERSION}.jar"

COMPTES = {"awa@sama.test": "essai-awa-2026", "koffi@sama.test": "essai-koffi-2026"}


def pdf_minimal(texte):
    """Un PDF d'une page, pour la pièce jointe du rapport."""
    flux = f"BT /F1 24 Tf 72 720 Td ({texte}) Tj ET".encode()
    objets = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length %d >>\nstream\n" % len(flux) + flux + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    sortie = b"%PDF-1.4\n"
    positions = []
    for i, o in enumerate(objets, 1):
        positions.append(len(sortie))
        sortie += b"%d 0 obj\n" % i + o + b"\nendobj\n"
    xref = len(sortie)
    sortie += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objets) + 1)
    sortie += b"".join(b"%010d 00000 n \n" % p for p in positions)
    sortie += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objets) + 1, xref)
    return sortie


def mail(de, a, sujet, texte, html=None, pieces=(), il_y_a=0, reponse_a=None):
    m = EmailMessage()
    m["From"] = de
    m["To"] = a
    m["Subject"] = sujet
    m["Date"] = formatdate(time.time() - il_y_a, localtime=True)
    m["Message-ID"] = make_msgid(domain="sama.test")
    if reponse_a:
        m["In-Reply-To"] = reponse_a
    m.set_content(texte)
    if html:
        m.add_alternative(html, subtype="html")
    for nom, type_, octets in pieces:
        principal, secondaire = type_.split("/")
        m.add_attachment(octets, maintype=principal, subtype=secondaire, filename=nom)
    return m


def deposer():
    a = "Awa Traoré <awa@sama.test>"
    mails = [
        mail("Service informatique <si@entreprise.test>", a, "Votre téléphone rejoint la flotte",
             "Bonjour,\n\nSeul l'Espace Travail est géré. Maison reste privé.\n\nLe service informatique", il_y_a=3 * 86400),
        mail("Koffi Brou <koffi@sama.test>", a, "Re : commande de fournitures",
             "C'est noté pour jeudi, livraison au 2e étage.\n\nKoffi\n\n> Peux-tu commander les ramettes de papier ?", il_y_a=86400 + 3600),
        mail("Le Journal du quartier <info@journal.test>", a, "Les nouvelles de la semaine",
             "Le marché déménage samedi. Voir la carte : https://journal.test/marche",
             html="<h2>Les nouvelles de la semaine</h2><p><img src=\"http://journal.test/banniere.png\" alt=\"Bannière\"></p>"
                  "<p>Le marché déménage samedi. <a href=\"https://journal.test/marche\">Voir la carte</a>.</p>"
                  "<p>Concours : <a href=\"http://cadeaux-express.test/r?id=7\">www.orange.ci/jeu</a></p>", il_y_a=7 * 3600),
        mail("Orange Money <bonus@orange-money-promo.test>", a, "Votre bonus de 50 000 F vous attend",
             "Félicitations ! Vous avez gagné un bonus Orange Money de 50 000 F.\n\nPour le recevoir, confirmez votre code secret ici : "
             "http://orange-money-bonus.test/valider\n\nL'équipe Orange Money", il_y_a=5 * 3600),
        mail("Aminata Koné <aminata@entreprise.test>", a, "Rapport du 3e trimestre",
             "Bonjour Awa,\n\nVoici la version corrigée, avec les chiffres de septembre.\n\nBonne journée,\nAminata",
             pieces=[("Rapport_T3.pdf", "application/pdf", pdf_minimal("Rapport du 3e trimestre"))], il_y_a=3 * 3600),
        mail("Support <maj@telephone-securite.test>", a, "Mise à jour urgente de votre téléphone",
             "Votre téléphone est en danger. Installez tout de suite la mise à jour jointe.",
             pieces=[("mise-a-jour-securite.apk", "application/vnd.android.package-archive", b"PK\x03\x04 faux")], il_y_a=2 * 3600),
        mail("Ressources humaines <rh@entreprise.test>", a, "Congés de fin d'année : le calendrier",
             "Bonjour à toutes et à tous,\n\nMerci d'envoyer vos dates avant le 15 octobre.\n\nLes ressources humaines", il_y_a=1800),
    ]
    with smtplib.SMTP("127.0.0.1", 3025) as s:
        for m in mails:
            s.send_message(m)
    print(f"{len(mails)} mails déposés dans la boîte d'Awa.")


def main():
    os.makedirs(DOSSIER, exist_ok=True)
    if not os.path.exists(JAR):
        print("Téléchargement de GreenMail…")
        urllib.request.urlretrieve(URL, JAR)
    utilisateurs = ",".join(f"{adr.split('@')[0]}:{mdp}@{adr.split('@')[1]}" for adr, mdp in COMPTES.items())
    serveur = subprocess.Popen([
        "java", "-Dgreenmail.setup.test.smtp", "-Dgreenmail.setup.test.imap", "-Dgreenmail.hostname=127.0.0.1",
        f"-Dgreenmail.users={utilisateurs}", "-Dgreenmail.users.login=email", "-jar", JAR,
    ])
    for _ in range(60):
        try:
            smtplib.SMTP("127.0.0.1", 3025).quit()
            break
        except OSError:
            time.sleep(0.5)
    if "--vide" not in sys.argv:
        deposer()
    print("Serveur de mail d'essai prêt : IMAP 3143, SMTP 3025 (depuis l'émulateur : 10.0.2.2).")
    try:
        serveur.wait()
    except KeyboardInterrupt:
        serveur.terminate()


if __name__ == "__main__":
    main()
