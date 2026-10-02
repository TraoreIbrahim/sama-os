#!/bin/sh
# Crée les Espaces de Sama (activités Plasma) au premier démarrage de session :
# l'activité par défaut devient « Travail », puis « École » et « Maison » sont ajoutées.

marqueur="${XDG_CONFIG_HOME:-$HOME/.config}/samaos/espaces-crees"
[ -e "$marqueur" ] && exit 0

# Laisser le gestionnaire d'activités démarrer
sleep 5

appel() {
	methode="$1"
	shift
	dbus-send --session --print-reply --dest=org.kde.ActivityManager \
		/ActivityManager/Activities "org.kde.ActivityManager.Activities.$methode" "$@"
}

actuelle=$(appel CurrentActivity | sed -n 's/.*string "\(.*\)"/\1/p')
if [ -n "$actuelle" ]; then
	appel SetActivityName string:"$actuelle" string:"Travail" >/dev/null
fi
appel AddActivity string:"École" >/dev/null
appel AddActivity string:"Maison" >/dev/null

mkdir -p "$(dirname "$marqueur")"
touch "$marqueur"
