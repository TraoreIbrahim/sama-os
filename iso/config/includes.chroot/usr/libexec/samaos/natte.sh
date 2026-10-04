#!/bin/sh
# Réglages « Bureau et Natte » : pilote la Natte et le bureau de l'Espace actif (interface de script de Plasma).
#   natte.sh etat                                  état courant, en JSON
#   natte.sh taille compacte|normale|grande         taille de la Natte (Cour, Espaces, Pouls et barre)
#   natte.sh masquage none|dodgewindows|autohide    toujours visible / s'efface sous les fenêtres / masquée
#   natte.sh element afficherEspaces|afficherTelechargements|afficherCorbeille true|false
#   natte.sh carte org.samaos.carte.xxx oui|non     carte du bureau de l'Espace actif
#   natte.sh ranger                                 remet les cartes en colonne
set -e
plasma() { qdbus6 org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "$1"; }
# La barre qui contient la Natte
barre='var p = panels().filter(function (x) { return x.widgets("org.samaos.natte").length > 0; })[0];'

case "$1" in
etat)
	plasma "$barre
var n = p.widgets('org.samaos.natte')[0];
n.currentConfigGroup = ['General'];
var d = desktopForScreen(0), cartes = {};
['heure', 'data', 'meteo', 'agenda'].forEach(function (c) { cartes[c] = d.widgets('org.samaos.carte.' + c).length > 0; });
print(JSON.stringify({
    taille: String(n.readConfig('taille', 'normale')),
    masquage: String(p.hiding),
    afficherEspaces: String(n.readConfig('afficherEspaces', true)) !== 'false',
    afficherTelechargements: String(n.readConfig('afficherTelechargements', true)) !== 'false',
    afficherCorbeille: String(n.readConfig('afficherCorbeille', true)) !== 'false',
    cartes: cartes
}));"
	;;
taille)
	case "$2" in compacte) h=52 ;; grande) h=68 ;; *) set -- "$1" normale; h=60 ;; esac
	plasma "$barre
['org.samaos.cour', 'org.samaos.natte', 'org.samaos.pouls'].forEach(function (type) {
    p.widgets(type).forEach(function (w) { w.currentConfigGroup = ['General']; w.writeConfig('taille', '$2'); w.reloadConfig(); });
});
p.height = $h;"
	;;
masquage)
	plasma "$barre p.hiding = '$2';"
	;;
element)
	plasma "$barre
var n = p.widgets('org.samaos.natte')[0];
n.currentConfigGroup = ['General'];
n.writeConfig('$2', $3);
n.reloadConfig();"
	;;
carte)
	if [ "$3" = oui ]; then
		plasma "var d = desktopForScreen(0); if (d.widgets('$2').length === 0) d.addWidget('$2', 0, 0, 100, 100);"
	else
		plasma "desktopForScreen(0).widgets('$2').forEach(function (w) { w.remove(); });"
	fi
	sleep 1
	sh /usr/libexec/samaos/preparer-espace.sh forcer
	;;
ranger)
	sh /usr/libexec/samaos/preparer-espace.sh forcer
	;;
*) echo "Usage : $0 etat|taille|masquage|element|carte|ranger …" >&2; exit 1 ;;
esac
