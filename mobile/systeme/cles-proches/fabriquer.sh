#!/bin/sh
# La clé d'éditeur de TEST de Proche en proche (émulateur seulement). Les paquets d'essai sont signés avec la
# clé privée (jamais versionnée) ; la clé publique va dans la bibliothèque `proches`, qui ne fait confiance
# qu'aux éditeurs qu'elle connaît. Les vraies clés de Sama et de Sugu seront gardées dans un HSM, et il faudra
# deux personnes pour signer.
# Sans la clé privée (dépôt tout juste copié), le script crée une nouvelle paire et remplace la clé publique.
set -e
cd "$(dirname "$0")"
PUB=../../proches/src/main/assets/editeurs/test-emulateur.pem
[ -f editeur-test.pem ] && [ -f "$PUB" ] && exit 0
openssl ecparam -name prime256v1 -genkey -noout -out editeur-test.pem
openssl ec -in editeur-test.pem -pubout -out "$PUB" 2>/dev/null
echo "Nouvelle clé d'éditeur de test : $PUB"
