#!/bin/sh
# Prépare le bureau de l'Espace actif (chaque Espace a son propre bureau dans Plasma) :
# fond d'écran du mode en cours (clair ou sombre) et cartes Sama (heure, data) si elles manquent.
# Appelé par la Natte à la création d'un Espace.

if kreadconfig6 --file kdeglobals --group General --key ColorScheme | grep -q Sombre; then
	fond=SamaNuit
else
	fond=SamaAube
fi

script="
var d = desktopForScreen(0);
d.wallpaperPlugin = 'org.kde.image';
d.currentConfigGroup = ['Wallpaper', 'org.kde.image', 'General'];
d.writeConfig('Image', 'file:///usr/share/wallpapers/$fond/');
if (d.widgets('org.samaos.carte.heure').length === 0) {
    var marge = Math.round(gridUnit * 1.5);
    var gauche = Math.round(gridUnit * 7);
    var largeur = Math.round(gridUnit * 17);
    d.addWidget('org.samaos.carte.heure', gauche, marge, largeur, Math.round(gridUnit * 10.5));
    d.addWidget('org.samaos.carte.data', gauche, marge + Math.round(gridUnit * 11.2), largeur, Math.round(gridUnit * 6.5));
}
"
qdbus6 org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "$script"
