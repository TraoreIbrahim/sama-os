#!/bin/sh
# Session invitée de Sama (Réglages › Comptes, maquette reg-06) : un compte « sama-invite » (affiché « Invité »)
# sans mot de passe ni droits d'administration, dont le dossier repart de zéro à chaque session et s'efface à la
# déconnexion. Le marqueur /var/lib/samaos/session-invitee, posé à l'activation, garantit qu'on n'efface jamais
# le dossier d'un autre compte.
#   invite.sh etat                   « actif » ou « inactif »
#   invite.sh activer|desactiver     administrateur (pkexec : action org.samaos.invite)
#   invite.sh                        ouverture et fermeture de session (pam_exec : PAM_USER, PAM_TYPE), branché
#                                    par le profil PAM « samaos-invite » (/usr/share/pam-configs) quand l'invité est actif
#   invite.sh nettoyer [demarrage]   après la déconnexion (lancé hors de la session par systemd-run), et au démarrage
#                                    de l'ordinateur (samaos-invite-nettoyage.service)
set -u
COMPTE=sama-invite
NOM="Invité"
DOSSIER=/home/$COMPTE
MARQUEUR=/var/lib/samaos/session-invitee

# L'invité a-t-il une session ouverte ? (seules comptent les sessions de classe « user » qui ne sont pas en train de
# se fermer : systemd ouvre aussi une session « manager » pour chaque utilisateur)
session_ouverte() {
	for s in $(loginctl show-user "$COMPTE" -p Sessions --value 2>/dev/null); do
		[ "$(loginctl show-session "$s" -p Class --value 2>/dev/null)" = user ] || continue
		[ "$(loginctl show-session "$s" -p State --value 2>/dev/null)" = closing ] || return 0
	done
	return 1
}

# Dossier vide (modèle /etc/skel), fichiers temporaires effacés
remettre_a_zero() {
	[ -e "$MARQUEUR" ] || return 0
	rm -rf "$DOSSIER"
	cp -a /etc/skel "$DOSSIER"
	# (pas d'accueil du premier démarrage pour l'invité : voir bienvenue.sh)
	mkdir -p "$DOSSIER/.config"
	# Pas de verrouillage automatique : il n'y a pas de mot de passe à retaper
	printf '[Daemon]\nAutolock=false\nLockOnResume=false\n' > "$DOSSIER/.config/kscreenlockerrc"
	chown -R "$COMPTE:$COMPTE" "$DOSSIER"
	chmod 700 "$DOSSIER"
	find /tmp /var/tmp -xdev -user "$COMPTE" -delete 2>/dev/null
	true
}

# Plus aucun processus de l'invité (10 s au plus)
arreter_processus() {
	loginctl terminate-user "$COMPTE" 2>/dev/null
	for i in 1 2 3 4 5 6 7 8 9 10; do
		pgrep -u "$COMPTE" >/dev/null || return 0
		pkill -KILL -u "$COMPTE" 2>/dev/null
		sleep 1
	done
	! pgrep -u "$COMPTE" >/dev/null
}

case "${1:-}" in
etat)
	[ -e "$MARQUEUR" ] && id -u "$COMPTE" >/dev/null 2>&1 && echo actif || echo inactif
	;;
activer)
	if id -u "$COMPTE" >/dev/null 2>&1; then
		# Compte déjà là : le nôtre (activation précédente interrompue), ou un autre compte du même nom
		[ "$(getent passwd "$COMPTE" | cut -d: -f5 | cut -d, -f1)" = "$NOM" ] || {
			echo "Un autre compte s'appelle déjà « $COMPTE »." >&2
			exit 1
		}
	else
		useradd --create-home --comment "$NOM" --shell /bin/bash \
			--groups audio,video,plugdev,netdev,bluetooth,scanner,lpadmin "$COMPTE" 2>/dev/null ||
			useradd --create-home --comment "$NOM" --shell /bin/bash "$COMPTE" || exit 1
		passwd --delete "$COMPTE" >/dev/null
	fi
	mkdir -p "$(dirname "$MARQUEUR")" && touch "$MARQUEUR"
	# Remise à zéro à chaque ouverture et fermeture de session (profil PAM « optional » : il ne peut pas
	# empêcher une connexion, et n'agit que pour l'invité)
	DEBIAN_FRONTEND=noninteractive pam-auth-update --enable samaos-invite
	remettre_a_zero
	;;
desactiver)
	[ -e "$MARQUEUR" ] || exit 0
	if ! arreter_processus || ! userdel --remove "$COMPTE" 2>/dev/null; then
		echo "La session invitée est encore ouverte : réessayez dans un instant." >&2
		exit 1
	fi
	DEBIAN_FRONTEND=noninteractive pam-auth-update --remove samaos-invite
	rm -f "$MARQUEUR"
	find /tmp /var/tmp -xdev -nouser -delete 2>/dev/null
	true
	;;
"")
	# Ouverture et fermeture de session (pam_exec) : seulement pour le compte invité
	[ "${PAM_USER:-}" = "$COMPTE" ] || exit 0
	case "${PAM_TYPE:-}" in
	# (une deuxième session, un terminal par exemple, ne vide pas le dossier de la première)
	open_session) session_ouverte || remettre_a_zero ;;
	# (hors de la session, qui s'arrête avec tous ses processus)
	close_session) systemd-run --quiet --no-block --collect /bin/sh /usr/libexec/samaos/invite.sh nettoyer ;;
	esac
	exit 0
	;;
nettoyer)
	# (au démarrage de l'ordinateur, « nettoyer demarrage » : rien à attendre)
	[ "${2:-}" = demarrage ] || sleep 5
	# Une nouvelle session invitée a pu s'ouvrir entre-temps : elle a déjà son dossier neuf
	session_ouverte && exit 0
	pkill -KILL -u "$COMPTE" 2>/dev/null
	remettre_a_zero
	;;
*)
	echo "Usage : $0 etat|activer|desactiver" >&2
	exit 1
	;;
esac
