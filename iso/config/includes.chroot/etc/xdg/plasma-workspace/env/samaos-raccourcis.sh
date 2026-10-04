#!/bin/sh
# Lancé à l'ouverture de session, avant Plasma (plasma-workspace/env) :
# - Méta+Tab ouvre la vue d'ensemble des Espaces (la Natte) au lieu du passage d'activité en activité de Plasma ;
# - le menu du clic droit sur le bureau ne propose plus le sélecteur d'activités de KDE (les Espaces le remplacent).
# Une seule fois par compte : un changement fait ensuite par l'utilisateur est respecté.
marqueur="${XDG_CONFIG_HOME:-$HOME/.config}/samaos/raccourcis-faits"
if [ ! -e "$marqueur" ]; then
	kwriteconfig6 --file kglobalshortcutsrc --group plasmashell --key "next activity" "none,Meta+Tab,Parcourir les activités"
	kwriteconfig6 --file kglobalshortcutsrc --group plasmashell --key "previous activity" "none,Meta+Shift+Tab,Parcourir les activités (en sens inverse)"
	kwriteconfig6 --file plasma-org.kde.plasma.desktop-appletsrc --group ActionPlugins --group 0 \
		--group "RightButton;NoModifier" --key "manage activities" false
	# Pas de menu « Modifier la barre » au clic droit autour de la Natte
	kwriteconfig6 --file plasma-org.kde.plasma.desktop-appletsrc --group ActionPlugins --group 1 \
		--key "RightButton;NoModifier" --delete
	mkdir -p "$(dirname "$marqueur")"
	touch "$marqueur"
fi
