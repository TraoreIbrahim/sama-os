#!/bin/sh
# Session invitée de Sama (Réglages › Comptes, maquette reg-06) : un compte « sama-invite » (affiché « Invité »)
# sans mot de passe ni droits d'administration, dont le dossier repart de zéro à chaque session et s'efface à la
# déconnexion. Le marqueur /var/lib/samaos/session-invitee, posé à l'activation, garantit qu'on n'efface jamais
# le dossier d'un autre compte.
#   invite.sh etat                   « actif » ou « inactif »
#   invite.sh activer|desactiver     administrateur (pkexec : action org.samaos.invite)
#   invite.sh                        appelé par la connexion (pam_exec, /etc/pam.d/sddm) : PAM_USER, PAM_TYPE
#   invite.sh nettoyer               après la déconnexion (lancé hors de la session par systemd-run)
set -u
COMPTE=sama-invite
DOSSIER=/home/$COMPTE
MARQUEUR=/var/lib/samaos/session-invitee
PAM=/etc/pam.d/sddm
LIGNE_PAM="session optional pam_exec.so quiet /usr/libexec/samaos/invite.sh"

# Remise à zéro à l'ouverture et à la fermeture des sessions graphiques. Ligne « optional », placée avant la
# création de la session (common-session) : elle ne peut pas empêcher une connexion, et n'agit que pour l'invité.
brancher_connexion() {
	[ -f "$PAM" ] || return 0
	grep -qF "$LIGNE_PAM" "$PAM" && return 0
	if grep -q '^@include common-session' "$PAM"; then
		awk -v l="$LIGNE_PAM" '!fait && /^@include common-session/ { print l; fait = 1 } { print }' "$PAM" > "$PAM.sama" &&
			cat "$PAM.sama" > "$PAM" && rm -f "$PAM.sama"
	else
		echo "$LIGNE_PAM" >> "$PAM"
	fi
}

# Dossier vide (modèle /etc/skel), fichiers temporaires effacés
remettre_a_zero() {
	[ -e "$MARQUEUR" ] || return 0
	rm -rf "$DOSSIER"
	cp -a /etc/skel "$DOSSIER"
	# Pas d'accueil du premier démarrage : la session invitée s'ouvre directement sur le bureau
	mkdir -p "$DOSSIER/.config/samaos" && touch "$DOSSIER/.config/samaos/bienvenue-faite"
	# Pas de verrouillage automatique : il n'y a pas de mot de passe à retaper
	printf '[Daemon]\nAutolock=false\nLockOnResume=false\n' > "$DOSSIER/.config/kscreenlockerrc"
	chown -R "$COMPTE:$COMPTE" "$DOSSIER"
	chmod 700 "$DOSSIER"
	find /tmp /var/tmp -xdev -user "$COMPTE" -delete 2>/dev/null
	true
}

case "${1:-}" in
etat)
	[ -e "$MARQUEUR" ] && id -u "$COMPTE" >/dev/null 2>&1 && echo actif || echo inactif
	;;
activer)
	if ! id -u "$COMPTE" >/dev/null 2>&1; then
		mkdir -p "$(dirname "$MARQUEUR")" && touch "$MARQUEUR"
		useradd --create-home --comment "Invité" --shell /bin/bash \
			--groups audio,video,plugdev,netdev,bluetooth,scanner,lpadmin "$COMPTE" 2>/dev/null ||
			useradd --create-home --comment "Invité" --shell /bin/bash "$COMPTE"
		passwd --delete "$COMPTE" >/dev/null
	fi
	brancher_connexion
	remettre_a_zero
	;;
desactiver)
	[ -e "$MARQUEUR" ] || exit 0
	loginctl terminate-user "$COMPTE" 2>/dev/null
	sleep 1
	pkill -KILL -u "$COMPTE" 2>/dev/null
	userdel --remove "$COMPTE" 2>/dev/null
	rm -f "$MARQUEUR"
	find /tmp /var/tmp -xdev -nouser -delete 2>/dev/null
	true
	;;
"")
	# Ouverture et fermeture de session (pam_exec) : seulement pour le compte invité
	[ "${PAM_USER:-}" = "$COMPTE" ] || exit 0
	case "${PAM_TYPE:-}" in
	open_session) remettre_a_zero ;;
	# (hors de la session, qui s'arrête avec tous ses processus)
	close_session) systemd-run --quiet --no-block --collect /bin/sh /usr/libexec/samaos/invite.sh nettoyer ;;
	esac
	exit 0
	;;
nettoyer)
	sleep 5
	# Une nouvelle session invitée a pu s'ouvrir entre-temps : elle a déjà son dossier neuf
	loginctl show-user "$COMPTE" -p Sessions --value 2>/dev/null | grep -q . && exit 0
	pkill -KILL -u "$COMPTE" 2>/dev/null
	remettre_a_zero
	;;
*)
	echo "Usage : $0 etat|activer|desactiver" >&2
	exit 1
	;;
esac
