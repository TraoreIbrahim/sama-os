#!/bin/sh
# Organise le menu des applications de Sama OS (la Cour) :
#  - masque les outils techniques de KDE et les logiciels provisoires remplacés par un raccourci Sama ;
#  - donne un nom clair en français aux applications gardées.
# Les fichiers des paquets ne sont pas modifiés : une copie dans /usr/local/share/applications
# prend le dessus sur l'original. À lancer en administrateur (construction de l'ISO, mode direct).
set -e

SOURCE=/usr/share/applications
CIBLE=/usr/local/share/applications
mkdir -p "$CIBLE"

masquer() {
	for app in "$@"; do
		[ -f "$SOURCE/$app.desktop" ] || continue
		sed '/^NoDisplay=/d; /^\[Desktop Entry\]/a NoDisplay=true' "$SOURCE/$app.desktop" > "$CIBLE/$app.desktop"
	done
}

# renommer <application> <nom> [icône Sama] : nom clair en français et tuile aux couleurs de Sama
renommer() {
	app="$1"
	nom="$2"
	icone="${3:-}"
	[ -f "$SOURCE/$app.desktop" ] || return 0
	if [ -n "$icone" ]; then
		sed -e '/^Name\(\[[^]]*\]\)\?=/d' -e '/^Icon=/d' \
			-e "/^\[Desktop Entry\]/a Name=$nom\nIcon=/usr/share/samaos/icones/$icone.svg" \
			"$SOURCE/$app.desktop" > "$CIBLE/$app.desktop"
	else
		sed -e '/^Name\(\[[^]]*\]\)\?=/d' -e "/^\[Desktop Entry\]/a Name=$nom" \
			"$SOURCE/$app.desktop" > "$CIBLE/$app.desktop"
	fi
}

# Logiciels provisoires remplacés par Griot, Sama Docs, Sama Sheet, Fichiers, Sugu, Photos, Réglages
masquer chromium libreoffice-writer libreoffice-calc libreoffice-impress \
	org.kde.dolphin org.kde.discover org.kde.gwenview

# Outils techniques, doublons et utilitaires réservés aux experts
masquer org.kde.drkonqi org.kde.drkonqi.coredump.gui org.kde.kmenuedit org.kde.kwalletmanager \
	org.kde.konqueror konqbrowser org.kde.kfind org.kde.kwrite org.kde.kate org.kde.kinfocenter \
	system-config-printer org.kde.partitionmanager org.kde.kdeconnect.app org.kde.kdeconnect.sms \
	org.kde.kdeconnect-settings org.kde.kdeconnect.nonplasma libreoffice-draw libreoffice-math \
	libreoffice-startcenter libreoffice-xsltfilter org.kde.kcolorschemeeditor org.kde.kfontview \
	org.kde.keditbookmarks org.kde.plasma.emojier org.kde.knetattach org.kde.bluedevilsendfile \
	org.kde.bluedevilwizard org.kde.gwenview_importer org.kde.vpnimport vim python3.13 \
	org.kde.kdialog org.kde.plasmawindowed systemsettings

# Noms clairs pour les applications gardées
renommer org.kde.konsole "Terminal" terminal
renommer org.kde.khelpcenter "Aide" aide
renommer org.kde.okular "Lecteur PDF" pdf
renommer org.kde.ark "Archives" archives
renommer org.kde.plasma-systemmonitor "Moniteur système" moniteur
renommer org.kde.spectacle "Capture d'écran" capture
