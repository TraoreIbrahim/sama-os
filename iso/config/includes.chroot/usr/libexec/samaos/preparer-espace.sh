#!/bin/sh
# Prépare le bureau de l'Espace actif (chaque Espace a son propre bureau dans Plasma) :
# fond d'écran du mode en cours (clair ou sombre) et cartes Sama (heure, data, météo, agenda) si elles manquent.
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
    var y = marge;
    d.addWidget('org.samaos.carte.heure', gauche, y, largeur, Math.round(gridUnit * 10.5));
    y += Math.round(gridUnit * 11.2);
    d.addWidget('org.samaos.carte.data', gauche, y, largeur, Math.round(gridUnit * 6.5));
    y += Math.round(gridUnit * 7.2);
    d.addWidget('org.samaos.carte.meteo', gauche, y, largeur, Math.round(gridUnit * 5.5));
    y += Math.round(gridUnit * 6.2);
    d.addWidget('org.samaos.carte.agenda', gauche, y, largeur, Math.round(gridUnit * 9));
}
"
qdbus6 org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "$script"
