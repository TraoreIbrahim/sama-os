#!/bin/sh
# Installe les écrans de Sama à la place de ceux du bureau Plasma :
#  - l'écran de verrouillage (lockscreen/LockScreenUi.qml) ;
#  - la galerie de widgets (explorer/WidgetExplorer.qml) ;
#  - le mode édition du bureau (views/DesktopEditMode.qml) et les poignées des widgets (ConfigOverlay.qml) ;
#  - la Natte sans bouton de réglage en mode édition ni menu « Modifier la barre » au clic droit
#    (defaults : pas de « ToolBox » ni d'action de clic droit pour les barres).
# Les fichiers d'origine sont détournés (dpkg-divert) : une mise à jour de Plasma ne remet pas les anciens écrans.
# Relancer le script est sans effet de bord. À lancer en administrateur (construction de l'ISO, mode direct).
set -e
coquille=/usr/share/plasma/shells/org.kde.plasma.desktop/contents
bureau=/usr/share/plasma/plasmoids/org.kde.desktopcontainment/contents/ui

detourner() {
	dpkg-divert --quiet --local --rename --divert "$1.plasma" --add "$1"
}
remplacer() {
	detourner "$1"
	cp "$2" "$1"
}

remplacer "$coquille/lockscreen/LockScreenUi.qml" /usr/libexec/samaos/verrouillage/LockScreenUi.qml
remplacer "$coquille/explorer/WidgetExplorer.qml" /usr/libexec/samaos/galerie/WidgetExplorer.qml
remplacer "$coquille/views/DesktopEditMode.qml" /usr/libexec/samaos/edition/DesktopEditMode.qml
remplacer "$bureau/ConfigOverlay.qml" /usr/libexec/samaos/edition/ConfigOverlay.qml

# Réglages par défaut de la coquille : la Natte n'a pas de bouton « configurer la barre » en mode édition
detourner "$coquille/defaults"
sed -e '/^\[Panel\]$/,/^\[/ s/^ToolBox=.*/ToolBox=/' \
	-e '/^\[Panel\]\[ContainmentActions\]$/,/^\[/ { /^RightButton;NoModifier=/d }' \
	"$coquille/defaults.plasma" > "$coquille/defaults"
