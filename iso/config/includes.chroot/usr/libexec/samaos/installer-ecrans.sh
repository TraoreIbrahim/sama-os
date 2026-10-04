#!/bin/sh
# Installe les écrans de Sama à la place de ceux du bureau Plasma :
#  - l'écran de verrouillage (lockscreen/LockScreenUi.qml) ;
#  - la galerie de widgets (explorer/WidgetExplorer.qml).
# Les fichiers d'origine sont détournés (dpkg-divert) : une mise à jour de Plasma ne remet pas les anciens écrans.
# Relancer le script est sans effet de bord. À lancer en administrateur (construction de l'ISO, mode direct).
set -e
coquille=/usr/share/plasma/shells/org.kde.plasma.desktop/contents

remplacer() {
	cible="$coquille/$1"
	dpkg-divert --quiet --local --rename --divert "$cible.plasma" --add "$cible"
	cp "$2" "$cible"
}

remplacer lockscreen/LockScreenUi.qml /usr/libexec/samaos/verrouillage/LockScreenUi.qml
remplacer explorer/WidgetExplorer.qml /usr/libexec/samaos/galerie/WidgetExplorer.qml
