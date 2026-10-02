#!/bin/sh
# Bascule l'apparence de Sama OS entre le mode clair et le mode sombre, en une fois :
# couleurs, icônes, fond d'écran du bureau et de l'écran de verrouillage.
# Usage : apparence.sh clair|sombre
set -u

case "${1:-}" in
clair)  couleurs=SamaClair;  icones=sama;        fond=SamaAube ;;
sombre) couleurs=SamaSombre; icones=sama-sombre; fond=SamaNuit ;;
*) echo "Usage : $0 clair|sombre" >&2; exit 1 ;;
esac

plasma-apply-colorscheme "$couleurs"

# Thème d'icônes (outil de Plasma, son chemin dépend de l'architecture)
for outil in /usr/lib/*/libexec/plasma-changeicons /usr/libexec/plasma-changeicons; do
	[ -x "$outil" ] && { "$outil" "$icones"; break; }
done

plasma-apply-wallpaperimage "/usr/share/wallpapers/$fond"
kwriteconfig6 --file kscreenlockerrc --group Greeter --group Wallpaper --group org.kde.image --group General \
	--key Image "file:///usr/share/wallpapers/$fond/"
