#!/bin/sh
# Opérations sur les comptes pour les Réglages de Sama (service AccountsService ; le système demande
# le mot de passe quand il le faut).
#   compte.sh nom "Prénom Nom"            compte.sh photo /chemin/image
#   compte.sh motdepasse "secret"         compte.sh creer identifiant "Nom complet" 0|1 "secret"   (1 = administrateur)
#   compte.sh supprimer UID
set -e
A="org.freedesktop.Accounts"
appel() { busctl --allow-interactive-authorization=yes call "$A" "$@"; }
chemin() { appel /org/freedesktop/Accounts "$A" FindUserById x "$1" | awk '{print $2}' | tr -d '"'; }
hache() { printf '%s' "$1" | openssl passwd -6 -stdin; }
moi=$(chemin "$(id -u)")

case "$1" in
nom)        appel "$moi" "$A.User" SetRealName s "$2" ;;
photo)      appel "$moi" "$A.User" SetIconFile s "$2" ;;
motdepasse) appel "$moi" "$A.User" SetPassword ss "$(hache "$2")" "" ;;
creer)
	nouveau=$(appel /org/freedesktop/Accounts "$A" CreateUser ssi "$2" "$3" "$4" | awk '{print $2}' | tr -d '"')
	[ -n "$5" ] && appel "$nouveau" "$A.User" SetPassword ss "$(hache "$5")" ""
	;;
supprimer)  appel /org/freedesktop/Accounts "$A" DeleteUser xb "$2" true ;;
*) echo "Usage : $0 nom|photo|motdepasse|creer|supprimer …" >&2; exit 1 ;;
esac
