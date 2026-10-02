#!/bin/sh
# Construit l'ISO live de Sama OS dans un conteneur Debian (Docker).
#
# Usage : scripts/construire-iso.sh [amd64|arm64]
#   amd64 : la cible principale (PC). Sur un Mac Apple Silicon, elle passe par l'émulation : compter plusieurs heures.
#   arm64 : pour tester vite dans une machine virtuelle sur un Mac Apple Silicon.
#
# L'ISO est déposée dans sortie/.
set -eu

ARCH="${1:-amd64}"
racine="$(cd "$(dirname "$0")/.." && pwd)"
inclus="$racine/iso/config/includes.chroot"

echo "==> Copie de l'identité visuelle et de la Natte dans l'arborescence de l'ISO"
mkdir -p "$inclus/usr/share/samaos/fonds" "$inclus/usr/share/plasma/plasmoids"
cp "$racine/branding/logo-sama.svg" "$racine/branding/logo-cour.svg" "$inclus/usr/share/samaos/"
cp "$racine"/branding/fonds/*.svg "$inclus/usr/share/samaos/fonds/"
mkdir -p "$inclus/usr/share/samaos/icones" "$inclus/usr/share/samaos/demarrage"
cp "$racine"/branding/demarrage/*.svg "$inclus/usr/share/samaos/demarrage/"
mkdir -p "$inclus/usr/share/samaos/installateur"
cp "$racine"/branding/installateur/*.svg "$inclus/usr/share/samaos/installateur/"
cp "$racine"/branding/icones/*.svg "$inclus/usr/share/samaos/icones/"
for widget in org.samaos.natte org.samaos.pouls; do
	rm -rf "$inclus/usr/share/plasma/plasmoids/$widget"
	cp -R "$racine/natte/$widget" "$inclus/usr/share/plasma/plasmoids/"
done

echo "==> Préparation de l'environnement de construction ($ARCH)"
docker build --platform "linux/$ARCH" -t "samaos-construction:$ARCH" "$racine/docker"

mkdir -p "$racine/sortie"

echo "==> Construction de l'ISO (cela prend du temps)"
# La construction se fait dans le système de fichiers du conteneur (live-build a besoin d'un vrai système Linux),
# puis seule l'ISO est recopiée vers sortie/. Le cache des paquets est gardé dans un volume Docker.
docker run --rm --privileged --platform "linux/$ARCH" \
	-e SAMA_ARCH="$ARCH" \
	-e SAMA_MIROIR="${SAMA_MIROIR:-http://ftp.fr.debian.org/debian/}" \
	-v "$racine/iso:/source:ro" \
	-v "$racine/sortie:/sortie" \
	-v "samaos-cache-$ARCH:/construction/cache" \
	"samaos-construction:$ARCH" \
	sh -c 'cp -a /source/. /construction/ && cd /construction && lb config && lb build && cp build.log /sortie/build-$SAMA_ARCH.log && for iso in ./*.iso; do nom=$(basename "$iso"); cp "$iso" "/sortie/.$nom.tmp" && mv "/sortie/.$nom.tmp" "/sortie/$nom"; done'

echo "==> Terminé :"
ls -lh "$racine"/sortie/*.iso
