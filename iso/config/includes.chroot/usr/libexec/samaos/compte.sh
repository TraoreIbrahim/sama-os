#!/bin/sh
# Opérations sur les comptes pour les Réglages de Sama (service AccountsService ; le système demande
# le mot de passe quand il le faut). Les mots de passe arrivent par l'entrée standard, jamais en paramètre.
#   compte.sh liste                         uid|identifiant|nom|admin(0/1)|photo, une ligne par personne
#   compte.sh nom UID "Prénom Nom"          compte.sh photo UID /chemin/image   (chemin vide : retirer)
#   compte.sh motdepasse UID  < secret      compte.sh type UID 0|1               (1 = administrateur)
#   compte.sh creer identifiant "Nom complet" 0|1  < secret
#   compte.sh supprimer UID garder|effacer  (effacer : supprime aussi ses documents)
#   compte.sh connexion-auto identifiant|aucun
#   compte.sh connexion-auto-actuelle
set -e
A="org.freedesktop.Accounts"
appel() { busctl --allow-interactive-authorization=yes call "$A" "$@"; }
chemin() { appel /org/freedesktop/Accounts "$A" FindUserById x "$1" | awk '{print $2}' | tr -d '"'; }
hache() { openssl passwd -6 -stdin; }
lire_secret() { IFS= read -r secret || true; [ -n "$secret" ] || { echo "Mot de passe vide" >&2; exit 1; }; }
conf_auto=/etc/sddm.conf.d/60-sama-connexion-auto.conf

case "$1" in
liste)
	# (le compte de la session invitée a sa propre ligne dans les Réglages : invite.sh)
	getent passwd | awk -F: '$3 >= 1000 && $3 < 60000 && $1 != "sama-invite" {print $3 "|" $1 "|" $5}' | while IFS='|' read -r uid id nom; do
		admin=0; id -nG "$id" | tr ' ' '\n' | grep -qx sudo && admin=1
		photo=$(busctl get-property "$A" "$(chemin "$uid")" "$A.User" IconFile 2>/dev/null | sed 's/^s "//; s/"$//')
		[ -f "$photo" ] || photo=""
		echo "$uid|$id|${nom%%,*}|$admin|$photo"
	done
	;;
nom)        appel "$(chemin "$2")" "$A.User" SetRealName s "$3" ;;
photo)
	appel "$(chemin "$2")" "$A.User" SetIconFile s "$3"
	# Plasma lit aussi ~/.face.icon pour la personne connectée
	if [ "$2" = "$(id -u)" ]; then
		if [ -n "$3" ]; then cp -f "$3" "$HOME/.face.icon"; else rm -f "$HOME/.face.icon"; fi
	fi
	;;
type)       appel "$(chemin "$2")" "$A.User" SetAccountType i "$3" ;;
motdepasse)
	lire_secret
	appel "$(chemin "$2")" "$A.User" SetPassword ss "$(printf '%s' "$secret" | hache)" ""
	;;
creer)
	lire_secret
	nouveau=$(appel /org/freedesktop/Accounts "$A" CreateUser ssi "$2" "$3" "$4" | awk '{print $2}' | tr -d '"')
	appel "$nouveau" "$A.User" SetPassword ss "$(printf '%s' "$secret" | hache)" ""
	;;
supprimer)
	[ "$2" != "$(id -u)" ] || { echo "Impossible de supprimer son propre compte" >&2; exit 1; }
	effacer=false; [ "$3" = effacer ] && effacer=true
	appel /org/freedesktop/Accounts "$A" DeleteUser xb "$2" "$effacer"
	;;
connexion-auto)
	# (l'installateur l'écrit dans /etc/sddm.conf, qui passe avant sddm.conf.d : retirée de là, elle est réglée ici)
	case "$2" in aucun | [a-z_]*) ;; *) exit 1 ;; esac
	printf '%s' "$2" | grep -qx '[a-z_][a-z0-9_-]*' || exit 1      # (un identifiant de compte, rien d'autre)
	retirer="sed -i '/^\[Autologin\]/,/^\[/{/^User=/d;/^Session=/d}' /etc/sddm.conf 2>/dev/null; true"
	if [ "$2" = aucun ]; then
		pkexec sh -c "rm -f '$conf_auto'; $retirer"
	else
		pkexec sh -c "printf '[Autologin]\nUser=%s\nSession=plasma\nRelogin=false\n' '$2' > '$conf_auto'; $retirer"
	fi
	;;
connexion-auto-actuelle)
	for f in "$conf_auto" /etc/sddm.conf; do
		u=$(sed -n '/^\[Autologin\]/,/^\[/s/^User=//p' "$f" 2>/dev/null | head -n 1)
		[ -n "$u" ] && { echo "$u"; break; }
	done
	true
	;;
*) echo "Usage : $0 liste|nom|photo|type|motdepasse|creer|supprimer|connexion-auto …" >&2; exit 1 ;;
esac
