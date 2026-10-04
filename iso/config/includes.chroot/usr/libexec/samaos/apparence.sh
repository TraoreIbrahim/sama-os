#!/bin/sh
# Bascule l'apparence de Sama OS entre le mode clair et le mode sombre, en une fois :
# couleurs, icônes, style des applications (Kvantum), fond d'écran du bureau et de l'écran de verrouillage.
# Usage : apparence.sh clair|sombre
set -u

case "${1:-}" in
clair)  couleurs=SamaClair;  icones=sama;        fond=SamaAube; style=Sama ;;
sombre) couleurs=SamaSombre; icones=sama-sombre; fond=SamaNuit; style=SamaSombre ;;
*) echo "Usage : $0 clair|sombre" >&2; exit 1 ;;
esac

plasma-apply-colorscheme "$couleurs"

# Style des menus, boutons et cases des applications KDE (les applications ouvertes le prennent en les rouvrant)
mkdir -p "$HOME/.config/Kvantum"
printf '[General]\ntheme=%s\n' "$style" > "$HOME/.config/Kvantum/kvantum.kvconfig"

# Thème d'icônes (outil de Plasma, son chemin dépend de l'architecture)
for outil in /usr/lib/*/libexec/plasma-changeicons /usr/libexec/plasma-changeicons; do
	[ -x "$outil" ] && { "$outil" "$icones"; break; }
done

plasma-apply-wallpaperimage "/usr/share/wallpapers/$fond"
kwriteconfig6 --file kscreenlockerrc --group Greeter --group Wallpaper --group org.kde.image --group General \
	--key Image "file:///usr/share/wallpapers/$fond/"
