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

# renommer <application> <nom> [icône Sama] : nom clair en français et tuile aux couleurs de Sama.
# L'ancien nom (Konsole, Okular…) reste dans les mots-clés : la recherche de la Cour le trouve encore.
renommer() {
	app="$1"
	nom="$2"
	icone="${3:-}"
	[ -f "$SOURCE/$app.desktop" ] || return 0
	ancien=$(sed -n 's/^Name=//p' "$SOURCE/$app.desktop" | head -n 1)
	if [ -n "$icone" ]; then
		sed -e '/^Name\(\[[^]]*\]\)\?=/d' -e '/^Icon=/d' \
			-e "/^\[Desktop Entry\]/a Name=$nom\nIcon=/usr/share/samaos/icones/$icone.svg" \
			"$SOURCE/$app.desktop" > "$CIBLE/$app.desktop"
	else
		sed -e '/^Name\(\[[^]]*\]\)\?=/d' -e "/^\[Desktop Entry\]/a Name=$nom" \
			"$SOURCE/$app.desktop" > "$CIBLE/$app.desktop"
	fi
	if grep -q '^Keywords\[fr\]=' "$CIBLE/$app.desktop"; then
		sed -i "s/^Keywords\[fr\]=.*/&;$ancien/" "$CIBLE/$app.desktop"
	else
		sed -i "/^\[Desktop Entry\]/a Keywords[fr]=$ancien;" "$CIBLE/$app.desktop"
	fi
}

# Logiciels provisoires remplacés par Griot, Sama Docs, Sama Sheet, Fichiers, Sugu, Photos, Réglages, Capture d'écran,
# Moniteur système, Aide (Spectacle reste l'outil de prise de vue et d'annotation de la Capture d'écran de Sama ;
# l'aide de KDE répond encore aux menus « Aide » des applications de KDE)
masquer chromium libreoffice-writer libreoffice-calc libreoffice-impress \
	org.kde.dolphin org.kde.discover org.kde.gwenview org.kde.spectacle org.kde.plasma-systemmonitor org.kde.khelpcenter
# Impr. va à la Capture d'écran de Sama : Spectacle garde ses autres raccourcis (Maj+Impr., Méta+Impr.…). Les
# raccourcis se déclarent dans /usr/share/kglobalaccel : celui de Spectacle (fichier du paquet) est détourné vers sa
# copie sans raccourci principal, et la Capture de Sama y est ajoutée.
if [ -f "$CIBLE/org.kde.spectacle.desktop" ]; then
	sed -i '/^\[Desktop Entry\]/,/^\[Desktop Action/{/^X-KDE-Shortcuts=/d}' "$CIBLE/org.kde.spectacle.desktop"
	if [ -e /usr/share/kglobalaccel/org.kde.spectacle.desktop ] && ! dpkg-divert --list | grep -q kglobalaccel/org.kde.spectacle.desktop; then
		dpkg-divert --local --rename --divert /usr/share/kglobalaccel/org.kde.spectacle.desktop.kde \
			--add /usr/share/kglobalaccel/org.kde.spectacle.desktop >/dev/null
	fi
	ln -sf "$CIBLE/org.kde.spectacle.desktop" /usr/share/kglobalaccel/org.kde.spectacle.desktop
fi
[ -f /usr/share/applications/samaos-capture.desktop ] && mkdir -p /usr/share/kglobalaccel &&
	ln -sf /usr/share/applications/samaos-capture.desktop /usr/share/kglobalaccel/samaos-capture.desktop
# Méta+Échap va au Moniteur système de Sama (avec Ctrl+Maj+Échap) : la déclaration du moniteur de KDE est détournée
if [ -f /usr/share/kglobalaccel/org.kde.plasma-systemmonitor.desktop ] && ! dpkg-divert --list | grep -q kglobalaccel/org.kde.plasma-systemmonitor.desktop; then
	dpkg-divert --local --rename --divert /usr/share/kglobalaccel/org.kde.plasma-systemmonitor.desktop.kde \
		--add /usr/share/kglobalaccel/org.kde.plasma-systemmonitor.desktop >/dev/null
fi
[ -f /usr/share/applications/samaos-moniteur.desktop ] && mkdir -p /usr/share/kglobalaccel &&
	ln -sf /usr/share/applications/samaos-moniteur.desktop /usr/share/kglobalaccel/samaos-moniteur.desktop

# LibreOffice garde la fenêtre « Enregistrer sous » de KDE : avec le portail (donc le sélecteur de Sama), il envoie
# chaque demande deux fois et rouvre la fenêtre après l'avoir validée (même avec le sélecteur de KDE)
for f in "$CIBLE"/libreoffice-*.desktop; do
	[ -f "$f" ] && sed -i 's/^Exec=libreoffice /Exec=env PLASMA_INTEGRATION_USE_PORTAL=0 libreoffice /' "$f"
done

# Outils techniques, doublons et utilitaires réservés aux experts
masquer org.kde.drkonqi org.kde.drkonqi.coredump.gui org.kde.kmenuedit org.kde.kwalletmanager \
	org.kde.konqueror konqbrowser org.kde.kfind org.kde.kate org.kde.kinfocenter \
	system-config-printer org.kde.partitionmanager org.kde.kdeconnect.app org.kde.kdeconnect.sms \
	org.kde.kdeconnect-settings org.kde.kdeconnect.nonplasma libreoffice-draw libreoffice-math \
	libreoffice-startcenter libreoffice-xsltfilter org.kde.kcolorschemeeditor org.kde.kfontview \
	org.kde.keditbookmarks org.kde.plasma.emojier org.kde.knetattach org.kde.bluedevilsendfile \
	org.kde.bluedevilwizard org.kde.gwenview_importer org.kde.vpnimport vim python3.13 \
	org.kde.kdialog org.kde.plasmawindowed systemsettings kvantummanager

# Noms clairs pour les applications gardées
renommer org.kde.konsole "Terminal" terminal
renommer org.kde.okular "Lecteur PDF" pdf
renommer org.kde.ark "Archives" archives
renommer org.kde.haruna "Lecteur vidéo" lecteur
renommer org.kde.kwrite "Éditeur de texte" editeur
