#!/bin/sh
# Télécharge la dernière ISO PC construite par GitHub Actions, en reprenant après chaque coupure.
# Utile sur une connexion instable : le navigateur, lui, recommence de zéro à la moindre coupure.
#
# Usage : scripts/telecharger-iso-pc.sh
# Prérequis : l'outil gh (GitHub CLI) connecté au compte. L'ISO arrive dans sortie/.
set -eu

DEPOT="TraoreIbrahim/sama-os"
racine="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$racine/sortie"
zip="$racine/sortie/samaos-pc-amd64.zip"

# Dernier artefact « samaos-pc-amd64 » encore disponible
id=$(gh api "repos/$DEPOT/actions/artifacts?name=samaos-pc-amd64&per_page=1" --jq '.artifacts[0].id')
taille=$(gh api "repos/$DEPOT/actions/artifacts/$id" --jq '.size_in_bytes')
echo "==> Artefact $id : $((taille / 1048576)) Mo"

tentative=0
while :; do
	deja=$(stat -f%z "$zip" 2>/dev/null || stat -c%s "$zip" 2>/dev/null || echo 0)
	[ "$deja" -ge "$taille" ] && break
	tentative=$((tentative + 1))
	echo "==> Tentative $tentative : $((deja / 1048576)) Mo sur $((taille / 1048576)) Mo"
	# Le lien de téléchargement fourni par GitHub n'est valable qu'une minute : on en redemande un à chaque reprise
	lien=$(curl -s -o /dev/null -w '%{redirect_url}' -H "Authorization: Bearer $(gh auth token)" \
		"https://api.github.com/repos/$DEPOT/actions/artifacts/$id/zip")
	[ -n "$lien" ] || { sleep 10; continue; }
	curl --fail --location --continue-at - --output "$zip" \
		--speed-limit 1024 --speed-time 60 --connect-timeout 30 "$lien" || sleep 5
done

echo "==> Téléchargement complet, extraction de l'ISO"
cd "$racine/sortie"
unzip -o "$zip" && rm -f "$zip"
ls -lh "$racine"/sortie/*amd64*.iso
