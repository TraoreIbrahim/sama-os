#!/bin/sh
# Prépare le bureau de l'Espace actif (chaque Espace a son propre bureau dans Plasma).
#
# Usage : preparer-espace.sh            fond du mode en cours + cartes Sama si aucune n'est présente
#         preparer-espace.sh ranger     remet seulement en colonne les cartes mal placées
#
# Plasma retient la position des widgets par résolution d'écran : au premier démarrage, ou quand la
# résolution change (machine virtuelle redimensionnée…), il peut déposer les cartes en haut à gauche,
# sur les icônes, ou les coller les unes aux autres. « ranger » les recrée alors dans la colonne Sama
# en gardant leurs réglages
# (événements de l'agenda, ville de la météo…). Une carte déplacée ailleurs par l'utilisateur n'est pas touchée.
# Deux temps : on retire d'abord les cartes (Plasma libère la place un peu plus tard), puis on les repose.

mode="${1:-preparer}"
plasma() { qdbus6 org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "$1"; }

if [ "$mode" = preparer ]; then
	if kreadconfig6 --file kdeglobals --group General --key ColorScheme | grep -q Sombre; then
		fond=SamaNuit
	else
		fond=SamaAube
	fi
	plasma "
var d = desktopForScreen(0);
d.wallpaperPlugin = 'org.kde.image';
d.currentConfigGroup = ['Wallpaper', 'org.kde.image', 'General'];
d.writeConfig('Image', 'file:///usr/share/wallpapers/$fond/');
"
fi

plan="[['org.samaos.carte.heure', 10.5], ['org.samaos.carte.data', 6.5], ['org.samaos.carte.meteo', 5.5], ['org.samaos.carte.agenda', 9]]"

# 1. Faut-il (re)poser les cartes ? Si oui : sauvegarder leurs réglages et les retirer
sauvegarde=$(plasma "
var d = desktopForScreen(0);
var g = gridUnit, gauche = Math.round(g * 7);
var plan = $plan;
var presentes = 0, malPlacees = false, colonne = [];
plan.forEach(function (p) {
    var w = d.widgets(p[0]);
    if (w.length > 0) {
        presentes++;
        var r = w[0].geometry;
        if (r.x < gauche - g) malPlacees = true;          // déposée sur les icônes
        else if (Math.abs(r.x - gauche) <= g) colonne.push(r)
    }
});
// Cartes de la colonne collées les unes aux autres (Plasma a avalé l'écart en les alignant sur sa grille)
colonne.sort(function (a, b) { return a.y - b.y; });
for (var i = 1; i < colonne.length; i++) {
    if (colonne[i].y - (colonne[i - 1].y + colonne[i - 1].height) < 8) malPlacees = true;
}
var ajouter = ('$mode' === 'preparer' && presentes === 0);
var cartes = [];
if (ajouter || malPlacees) {
    plan.forEach(function (p) {
        var w = d.widgets(p[0]);
        if (w.length === 0 && !ajouter) return;
        var reglages = {};
        if (w.length > 0) {
            w[0].currentConfigGroup = ['General'];
            w[0].configKeys.forEach(function (k) { reglages[k] = w[0].readConfig(k); });
            w[0].remove();
        }
        cartes.push({ type: p[0], hauteur: p[1], reglages: reglages });
    });
}
print(JSON.stringify(cartes));
")

case "$sauvegarde" in
	"[]"|"") exit 0 ;;
esac
sleep 1

# 2. Reposer les cartes en colonne, avec leurs réglages
plasma "
var d = desktopForScreen(0);
var g = gridUnit;
var gauche = Math.round(g * 7), largeur = Math.round(g * 17), y = 32;
var cartes = $sauvegarde;
cartes.forEach(function (c) {
    var h = Math.round(g * c.hauteur);
    var n = d.addWidget(c.type, gauche, y, largeur, h);
    n.currentConfigGroup = ['General'];
    for (var k in c.reglages) n.writeConfig(k, c.reglages[k]);
    // Plasma arrondit tailles et positions à sa grille de 16 px : on fait le même arrondi, plus 16 px d'écart
    y += Math.ceil(h / 16) * 16 + 16;
});
"
