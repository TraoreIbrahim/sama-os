#!/bin/sh
# Premier démarrage de session : l'accueil de Sama présente les Espaces (l'utilisateur choisit les siens), puis
# quelques réglages pour bien démarrer. Ce script crée ensuite les Espaces (activités Plasma) et applique les
# réglages. « Plus tard » : un seul Espace, « Accueil ».

marqueur="${XDG_CONFIG_HOME:-$HOME/.config}/samaos/bienvenue-faite"
[ -e "$marqueur" ] && exit 0

# Session invitée : pas d'accueil (tout est effacé à la déconnexion), juste un Espace « Accueil »
if [ "$(id -un)" = sama-invite ]; then
	sleep 4
	espace=$(qdbus6 org.kde.ActivityManager /ActivityManager/Activities CurrentActivity)
	[ -n "$espace" ] && qdbus6 org.kde.ActivityManager /ActivityManager/Activities SetActivityName "$espace" "Accueil" >/dev/null
	exit 0
fi

# Laisser le bureau et le gestionnaire d'activités démarrer
sleep 4

prenom=$(getent passwd "$(id -un)" | cut -d: -f5 | cut -d, -f1 | cut -d' ' -f1)

# Forfait mobile : clé 3G/4G, partage Bluetooth, ou connexion que NetworkManager devine mesurée
# (point d'accès d'un téléphone)
mobile=false
for type in $(nmcli -t -f TYPE connection show --active 2>/dev/null); do
	case "$type" in gsm | cdma | bluetooth) mobile=true ;; esac
done
mesuree=$(busctl get-property org.freedesktop.NetworkManager /org/freedesktop/NetworkManager \
	org.freedesktop.NetworkManager Metered 2>/dev/null | awk '{ print $2 }')
[ "$mesuree" = 1 ] || [ "$mesuree" = 3 ] && mobile=true

nuit=false
[ "$(sh /usr/libexec/samaos/mises-a-jour-nuit.sh etat)" = actif ] && nuit=true

infos=$(printf '{"prenom": "%s", "mobile": %s, "nuit": %s}' "$(printf '%s' "${prenom:-}" | sed 's/\\/\\\\/g; s/"/\\"/g')" "$mobile" "$nuit")
sortie=$(qml6 /usr/libexec/samaos/bienvenue/Bienvenue.qml -- "$infos" 2>&1)
choix=$(printf '%s\n' "$sortie" | sed -n 's/.*SAMA_ESPACES=//p' | tail -n 1)
reglages=$(printf '%s\n' "$sortie" | sed -n 's/.*SAMA_REGLAGES=//p' | tail -n 1)

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

# Réglages (fenêtre fermée sans répondre : rien ne change)
case "$reglages" in
*economie=1*)
	kwriteconfig6 --file samaosrc --group Data --key economie true
	# La connexion en cours devient « mesurée » : mises à jour et téléchargements automatiques attendent le Wi-Fi
	connexion=$(nmcli -t -f NAME,TYPE connection show --active 2>/dev/null | grep -v ':loopback$' | head -n 1 | cut -d: -f1)
	[ -n "$connexion" ] && nmcli connection modify "$connexion" connection.metered yes
	;;
*economie=0*)
	kwriteconfig6 --file samaosrc --group Data --key economie false
	;;
esac
case "$reglages" in
*nuit=1*) [ "$nuit" = true ] || pkexec /usr/libexec/samaos/mises-a-jour-nuit.sh activer ;;
*nuit=0*) [ "$nuit" = false ] || pkexec /usr/libexec/samaos/mises-a-jour-nuit.sh desactiver ;;
esac

mkdir -p "$(dirname "$marqueur")"
touch "$marqueur"
