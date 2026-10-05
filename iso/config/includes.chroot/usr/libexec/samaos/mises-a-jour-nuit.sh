#!/bin/sh
# Mises à jour la nuit (Réglages › Data et mises à jour, accueil du premier démarrage).
#   mises-a-jour-nuit.sh etat                  « actif » ou « inactif »
#   mises-a-jour-nuit.sh activer|desactiver    administrateur (pkexec : action org.samaos.mises-a-jour)
#   mises-a-jour-nuit.sh lancer                lancé par samaos-mises-a-jour-nuit.timer, entre 1 h et 4 h
# Les mises à jour ne s'installent que si l'ordinateur est branché et sur une connexion illimitée, et jamais
# dans la session d'essai (clé USB). Les minuteurs « apt-daily » de Debian ne font plus rien
# (voir /etc/apt/apt.conf.d/52sama-mises-a-jour) : rien ne se télécharge pendant la journée.
set -u
DRAPEAU=/etc/samaos/mises-a-jour-nuit

# Branché sur secteur, ou ordinateur de bureau (pas de batterie)
sur_secteur() {
	batterie=0
	for a in /sys/class/power_supply/*; do
		[ -e "$a/type" ] || continue
		case "$(cat "$a/type")" in
		Mains | USB) [ "$(cat "$a/online" 2>/dev/null)" = 1 ] && return 0 ;;
		Battery) [ "$(cat "$a/scope" 2>/dev/null)" = Device ] || batterie=1 ;;   # (pas celle d'une souris)
		esac
	done
	[ "$batterie" = 0 ]
}

# Connexion mesurée selon NetworkManager : 1 oui, 3 oui deviné (partage de connexion d'un téléphone…)
connexion_mesuree() {
	m=$(busctl get-property org.freedesktop.NetworkManager /org/freedesktop/NetworkManager \
		org.freedesktop.NetworkManager Metered 2>/dev/null | awk '{ print $2 }')
	[ "$m" = 1 ] || [ "$m" = 3 ]
}

case "${1:-}" in
etat)
	[ -e "$DRAPEAU" ] && echo actif || echo inactif
	;;
activer)
	mkdir -p "$(dirname "$DRAPEAU")" && touch "$DRAPEAU"
	;;
desactiver)
	rm -f "$DRAPEAU"
	;;
lancer)
	if grep -qw 'boot=live' /proc/cmdline; then echo "Session d'essai : pas de mise à jour."; exit 0; fi
	if [ ! -e "$DRAPEAU" ]; then echo "Mises à jour la nuit désactivées."; exit 0; fi
	if ! sur_secteur; then echo "Sur batterie : mises à jour remises à la nuit prochaine."; exit 0; fi
	if connexion_mesuree; then echo "Connexion mesurée : mises à jour remises à une connexion illimitée."; exit 0; fi
	export DEBIAN_FRONTEND=noninteractive
	if ! apt-get -q -o DPkg::Lock::Timeout=600 update; then echo "Pas de connexion aux dépôts : nuit prochaine."; exit 0; fi
	unattended-upgrade
	;;
*)
	echo "Usage : $0 etat|activer|desactiver|lancer" >&2
	exit 1
	;;
esac
