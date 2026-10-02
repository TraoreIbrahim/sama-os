#!/usr/bin/env python3
"""Génère le thème d'icônes « Sama » (dossiers, types de fichiers, emplacements, applications).

Langage visuel de la maquette (écran Fichiers) :
  - dossiers ocre à deux tons (onglet #CF9A55, face #E3B373), pictogramme discret pour les dossiers spéciaux ;
  - fichiers : feuille blanche arrondie, trois lignes grises, étiquette colorée avec l'extension ;
  - applications : les tuiles Sama de branding/icones/.
Tout ce qui n'est pas redéfini ici vient de Breeze (Inherits=breeze).

Usage : python3 branding/theme-icones/generer.py
Sortie : iso/config/includes.chroot/usr/share/icons/sama/
"""
import os
import shutil

RACINE = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SORTIE = os.path.join(RACINE, "iso/config/includes.chroot/usr/share/icons/sama")
TUILES = os.path.join(RACINE, "branding/icones")

ONGLET, FACE, TRAIT = "#CF9A55", "#E3B373", "#A8742F"

# Pictogrammes (trait 1.7, grille 24), même famille que les icônes de la Natte et du Pouls
PICTOS = {
    "documents": "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M14 3v5h5 M9 13h6 M9 17h4",
    "telechargements": "M12 4v11 M7 10l5 5l5-5 M5 20h14",
    "musique": "M9 18V6l10-2v12 M9 18a3 3 0 1 1-6 0a3 3 0 0 1 6 0z M19 16a3 3 0 1 1-6 0a3 3 0 0 1 6 0z",
    "images": "M4 5h16v14H4z M4 16l5-5l4 4l3-3l4 4 M15.5 9.5h.01",
    "videos": "M4 6h16v12H4z M10 9.5v5l4.5-2.5z",
    "bureau": "M3 5h18v11H3z M8 20h8 M12 16v4",
    "maison": "M4 11l8-7l8 7 M6 9.5V20h12V9.5 M10 20v-5h4v5",
    "partage": "M18 8a3 3 0 1 0 0-6a3 3 0 0 0 0 6z M6 15a3 3 0 1 0 0-6a3 3 0 0 0 0 6z M18 22a3 3 0 1 0 0-6a3 3 0 0 0 0 6z M8.6 13.5l6.8 4 M15.4 6.5l-6.8 4",
    "modeles": "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z",
    "nuage": "M7 18a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z",
    "corbeille": "M5 7h14 M10 7V5h4v2 M7 7l1 12a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2l1-12",
}


def dossier(picto=None, ouvert=False):
    """Dossier 64×64 : onglet arrière et face avant, pictogramme centré sur la face."""
    face = ("M2 26a4 4 0 0 1 4-4h52a4 4 0 0 1 3.9 4.9l-5 22A4 4 0 0 1 53 52H8a4 4 0 0 1-4-4z" if ouvert
            else "M4 22a4 4 0 0 1 4-4h48a4 4 0 0 1 4 4v26a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z")
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">',
           f'  <path d="M4 14a4 4 0 0 1 4-4h16l6 6h26a4 4 0 0 1 4 4v28a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z" fill="{ONGLET}"/>',
           f'  <path d="{face}" fill="{FACE}"/>']
    if picto:
        svg.append(f'  <g transform="translate(22.4 25.4) scale(0.8)" fill="none" stroke="{TRAIT}" stroke-width="2" '
                   f'stroke-linecap="round" stroke-linejoin="round"><path d="{PICTOS[picto]}"/></g>')
    svg.append("</svg>\n")
    return "\n".join(svg)


def fichier(etiquette=None, couleur="#8A8277"):
    """Feuille 64×64 : papier blanc, trois lignes, étiquette colorée débordant à gauche."""
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">',
           '  <rect x="12.5" y="4.5" width="39" height="55" rx="6" fill="#FFFFFF" stroke="#1F1C18" stroke-opacity="0.18"/>',
           '  <g fill="#1F1C18" fill-opacity="0.09">',
           '    <rect x="20" y="16" width="24" height="3" rx="1.5"/>',
           '    <rect x="20" y="23" width="18" height="3" rx="1.5"/>',
           '    <rect x="20" y="30" width="21" height="3" rx="1.5"/>',
           '  </g>']
    if etiquette:
        largeur = 8 + len(etiquette) * 6.4
        svg += [f'  <rect x="6" y="40" width="{largeur:.1f}" height="13" rx="4" fill="{couleur}"/>',
                f'  <text x="{6 + largeur / 2:.1f}" y="49.6" text-anchor="middle" font-family="Noto Sans, sans-serif" '
                f'font-size="9" font-weight="700" letter-spacing="0.3" fill="#FFFFFF">{etiquette}</text>']
    svg.append("</svg>\n")
    return "\n".join(svg)


def tuile(fond, encre, picto, pastille=False):
    """Tuile Sama 64×64 (même forme que les applications) pour la Corbeille."""
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">',
           f'  <rect width="64" height="64" rx="18" fill="{fond}"/>',
           f'  <g transform="translate(14 14) scale(1.5)" fill="none" stroke="{encre}" stroke-width="1.7" '
           f'stroke-linecap="round" stroke-linejoin="round"><path d="{PICTOS[picto]}"/></g>']
    if pastille:
        svg.append('  <circle cx="50" cy="14" r="7" fill="#B5532F"/>')
    svg.append("</svg>\n")
    return "\n".join(svg)


# --- Emplacements (dossiers) ---------------------------------------------------------------
EMPLACEMENTS = {
    "folder": None, "inode-directory": None, "folder-blue": None, "folder-orange": None,
    "folder-documents": "documents", "folder-text": "documents",
    "folder-download": "telechargements", "folder-downloads": "telechargements",
    "folder-music": "musique", "folder-sound": "musique",
    "folder-pictures": "images", "folder-images": "images",
    "folder-videos": "videos", "folder-video": "videos",
    "user-desktop": "bureau", "folder-desktop": "bureau",
    "user-home": "maison", "folder-home": "maison",
    "folder-publicshare": "partage", "folder-public": "partage",
    "folder-templates": "modeles",
    "folder-cloud": "nuage", "folder-network": "partage",
}

# --- Types de fichiers : (étiquette, couleur) ------------------------------------------------
DOC, TAB, PRES, PDF = "#3D5A99", "#2F6B57", "#B5532F", "#9A3B3B"
IMG, SON, VID, ARC, CODE = "#C98F62", "#7A5C99", "#1E2740", "#8A6A4A", "#4A4F5C"
TYPES = {
    "application-pdf": ("PDF", PDF),
    "application-vnd.oasis.opendocument.text": ("ODT", DOC),
    "application-vnd.openxmlformats-officedocument.wordprocessingml.document": ("DOCX", DOC),
    "application-msword": ("DOC", DOC),
    "x-office-document": ("DOC", DOC),
    "application-rtf": ("RTF", DOC),
    "application-vnd.oasis.opendocument.spreadsheet": ("ODS", TAB),
    "application-vnd.openxmlformats-officedocument.spreadsheetml.sheet": ("XLSX", TAB),
    "application-vnd.ms-excel": ("XLS", TAB),
    "x-office-spreadsheet": ("XLS", TAB),
    "text-csv": ("CSV", TAB),
    "application-vnd.oasis.opendocument.presentation": ("ODP", PRES),
    "application-vnd.openxmlformats-officedocument.presentationml.presentation": ("PPTX", PRES),
    "application-vnd.ms-powerpoint": ("PPT", PRES),
    "x-office-presentation": ("PPT", PRES),
    "image-x-generic": ("IMG", IMG), "image-png": ("PNG", IMG), "image-jpeg": ("JPG", IMG),
    "image-gif": ("GIF", IMG), "image-webp": ("WEBP", IMG), "image-svg+xml": ("SVG", IMG),
    "audio-x-generic": ("SON", SON), "audio-mpeg": ("MP3", SON), "audio-x-wav": ("WAV", SON),
    "audio-ogg": ("OGG", SON), "audio-flac": ("FLAC", SON), "audio-mp4": ("M4A", SON),
    "video-x-generic": ("VID", VID), "video-mp4": ("MP4", VID), "video-x-matroska": ("MKV", VID),
    "video-webm": ("WEBM", VID), "video-x-msvideo": ("AVI", VID),
    "application-zip": ("ZIP", ARC), "application-x-tar": ("TAR", ARC),
    "application-x-compressed-tar": ("TGZ", ARC), "application-x-7z-compressed": ("7Z", ARC),
    "application-x-rar": ("RAR", ARC), "application-vnd.rar": ("RAR", ARC),
    "application-x-archive": ("ZIP", ARC), "package-x-generic": ("ZIP", ARC),
    "application-x-iso9660-image": ("ISO", ARC), "application-x-cd-image": ("ISO", ARC),
    "application-vnd.debian.binary-package": ("DEB", ARC),
    "text-html": ("HTML", CODE), "application-json": ("JSON", CODE), "text-x-python": ("PY", CODE),
    "application-x-shellscript": ("SH", CODE), "text-x-script": ("SH", CODE),
    "text-plain": ("TXT", "#8A8277"), "text-x-generic": ("TXT", "#8A8277"),
    "application-x-desktop": None,
    "unknown": None, "application-octet-stream": None, "application-x-zerosize": None,
}

# --- Applications : icône de la fenêtre et du menu → tuile Sama --------------------------------
APPLICATIONS = {
    "terminal": ["utilities-terminal", "org.kde.konsole", "konsole"],
    "reglages": ["systemsettings", "preferences-system", "org.kde.systemsettings"],
    "aide": ["help-browser", "org.kde.khelpcenter", "khelpcenter"],
    "pdf": ["okular", "org.kde.okular"],
    "archives": ["ark", "org.kde.ark", "utilities-file-archiver"],
    "moniteur": ["utilities-system-monitor", "org.kde.plasma-systemmonitor", "plasma-systemmonitor"],
    "capture": ["spectacle", "org.kde.spectacle", "applets-screenshooter"],
    "fichiers": ["system-file-manager", "org.kde.dolphin", "dolphin"],
    "griot": ["web-browser", "internet-web-browser", "chromium"],
    "photos": ["org.kde.gwenview", "gwenview"],
    "sugu": ["org.kde.discover", "plasmadiscover", "system-software-install"],
    "docs": ["libreoffice-writer"],
    "sheet": ["libreoffice-calc"],
    "presentations": ["libreoffice-impress"],
}


def ecrire(dossier_relatif, nom, contenu):
    chemin = os.path.join(SORTIE, dossier_relatif, nom + ".svg")
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w") as f:
        f.write(contenu)


def main():
    if os.path.isdir(SORTIE):
        shutil.rmtree(SORTIE)

    for nom, picto in EMPLACEMENTS.items():
        ecrire("scalable/places", nom, dossier(picto))
    ecrire("scalable/places", "folder-open", dossier(ouvert=True))
    ecrire("scalable/places", "user-trash", tuile("#E4E0DA", "#5B544B", "corbeille"))
    ecrire("scalable/places", "user-trash-full", tuile("#E4E0DA", "#5B544B", "corbeille", pastille=True))
    ecrire("scalable/places", "trash-empty", tuile("#E4E0DA", "#5B544B", "corbeille"))

    for nom, valeur in TYPES.items():
        ecrire("scalable/mimetypes", nom, fichier(*valeur) if valeur else fichier())

    for tuile_sama, noms in APPLICATIONS.items():
        with open(os.path.join(TUILES, tuile_sama + ".svg")) as f:
            contenu = f.read()
        for nom in noms:
            ecrire("scalable/apps", nom, contenu)

    with open(os.path.join(SORTIE, "index.theme"), "w") as f:
        f.write("""[Icon Theme]
Name=Sama
Comment=Icônes de Sama OS : dossiers ocre, fichiers à étiquette, tuiles des applications Sama
Inherits=breeze,hicolor
Directories=scalable/places,scalable/mimetypes,scalable/apps
FollowsColorScheme=false

[scalable/places]
Size=64
MinSize=8
MaxSize=512
Context=Places
Type=Scalable

[scalable/mimetypes]
Size=64
MinSize=8
MaxSize=512
Context=MimeTypes
Type=Scalable

[scalable/apps]
Size=64
MinSize=8
MaxSize=512
Context=Applications
Type=Scalable
""")
    total = sum(len(fs) for _, _, fs in os.walk(SORTIE)) - 1
    print(f"Thème Sama : {total} icônes dans {os.path.relpath(SORTIE, RACINE)}")


if __name__ == "__main__":
    main()
