#!/bin/sh
# Premier démarrage de session : l'accueil de Sama présente les Espaces, l'utilisateur choisit les siens,
# puis ce script les crée (activités Plasma). « Plus tard » : un seul Espace, « Accueil ».

marqueur="${XDG_CONFIG_HOME:-$HOME/.config}/samaos/bienvenue-faite"
[ -e "$marqueur" ] && exit 0

# Laisser le bureau et le gestionnaire d'activités démarrer
sleep 4

prenom=$(getent passwd "$(id -un)" | cut -d: -f5 | cut -d, -f1 | cut -d' ' -f1)
choix=$(qml6 /usr/libexec/samaos/bienvenue/Bienvenue.qml -- "${prenom:-}" 2>&1 | sed -n 's/.*SAMA_ESPACES=//p' | tail -n 1)

appel() {
	methode="$1"
	shift
	dbus-send --session --print-reply --dest=org.kde.ActivityManager \
		/ActivityManager/Activities "org.kde.ActivityManager.Activities.$methode" "$@"
}

[ -n "$choix" ] || choix="Accueil"

# Le premier Espace choisi reprend l'activité existante, les suivants sont ajoutés
actuelle=$(appel CurrentActivity | sed -n 's/.*string "\(.*\)"/\1/p')
premier=1
ancienIFS=$IFS
IFS='|'
for nom in $choix; do
	if [ "$premier" = 1 ] && [ -n "$actuelle" ]; then
		appel SetActivityName string:"$actuelle" string:"$nom" >/dev/null
	else
		appel AddActivity string:"$nom" >/dev/null
	fi
	premier=0
done
IFS=$ancienIFS

mkdir -p "$(dirname "$marqueur")"
touch "$marqueur"
