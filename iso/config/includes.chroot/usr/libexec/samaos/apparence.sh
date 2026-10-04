#!/bin/sh
# Bascule l'apparence de Sama OS entre le mode clair et le mode sombre, en une fois :
# couleurs, icônes, style des applications (Kvantum), fond d'écran du bureau et de l'écran de verrouillage.
# Usage : apparence.sh clair|sombre
set -u

case "${1:-}" in
clair)  couleurs=SamaClair;  icones=sama;        fond=SamaAube; style=kvantum ;;
sombre) couleurs=SamaSombre; icones=sama-sombre; fond=SamaNuit; style=kvantum-dark ;;
*) echo "Usage : $0 clair|sombre" >&2; exit 1 ;;
esac

plasma-apply-colorscheme "$couleurs"

# Style des menus, boutons et cases des applications KDE : thème Kvantum « Sama », variante claire (kvantum)
# ou sombre (kvantum-dark). Le signal StyleChanged fait basculer les applications déjà ouvertes.
mkdir -p "$HOME/.config/Kvantum"
printf '[General]\ntheme=Sama\n' > "$HOME/.config/Kvantum/kvantum.kvconfig"
kwriteconfig6 --notify --file kdeglobals --group KDE --key widgetStyle "$style"
dbus-send --session --type=signal /KGlobalSettings org.kde.KGlobalSettings.notifyChange int32:2 int32:0

# Thème d'icônes (outil de Plasma, son chemin dépend de l'architecture)
for outil in /usr/lib/*/libexec/plasma-changeicons /usr/libexec/plasma-changeicons; do
	[ -x "$outil" ] && { "$outil" "$icones"; break; }
done

# Fond du bureau : Sama Aube ou Sama Nuit, sauf si la personne a choisi sa propre image (Réglages « Bureau et Natte »)
if [ -z "$(kreadconfig6 --file samaosrc --group Bureau --key FondPersonnel)" ]; then
	plasma-apply-wallpaperimage "/usr/share/wallpapers/$fond"
fi
kwriteconfig6 --file kscreenlockerrc --group Greeter --group Wallpaper --group org.kde.image --group General \
	--key Image "file:///usr/share/wallpapers/$fond/"
