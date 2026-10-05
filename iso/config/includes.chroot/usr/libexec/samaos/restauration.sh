#!/bin/sh
# Ouverture de session après une restauration du système (voir /usr/libexec/samaos/instantanes.py) : la fenêtre
# « Sama a été restauré » (maquette ses-06), une fois par compte et seulement dans la semaine qui suit.
NOTE=/var/lib/samaos/restauration.json
[ -r "$NOTE" ] || exit 0
[ "$(id -un)" = sama-invite ] && exit 0
quand=$(sed -n 's/.*"quand": *\([0-9]*\).*/\1/p' "$NOTE")
[ -n "$quand" ] || exit 0
[ $(( $(date +%s) - quand )) -lt 604800 ] || exit 0
vu="${XDG_STATE_HOME:-$HOME/.local/state}/samaos/restauration-vue"
[ "$(cat "$vu" 2>/dev/null)" = "$quand" ] && exit 0
mkdir -p "$(dirname "$vu")" && echo "$quand" >"$vu"

# Laisser le bureau s'installer
sleep 3
version=$(. /etc/os-release && echo "$VERSION_ID")
etat=$(python3 /usr/libexec/samaos/instantanes.py etat)
[ -n "$etat" ] || etat='{}'
exec qml6 /usr/libexec/samaos/restauration/Restauration.qml -- \
	"{\"note\": $(cat "$NOTE"), \"etat\": $etat, \"version\": \"$version\"}"
