#!/bin/sh
# Lancé à l'ouverture de session, avant Plasma (plasma-workspace/env) :
# Méta+Tab ouvre la vue d'ensemble des Espaces (la Natte) au lieu du passage d'activité en activité de Plasma.
# Une seule fois par compte : un changement fait ensuite par l'utilisateur est respecté.
marqueur="${XDG_CONFIG_HOME:-$HOME/.config}/samaos/raccourcis-faits"
if [ ! -e "$marqueur" ]; then
	kwriteconfig6 --file kglobalshortcutsrc --group plasmashell --key "next activity" "none,Meta+Tab,Parcourir les activités"
	kwriteconfig6 --file kglobalshortcutsrc --group plasmashell --key "previous activity" "none,Meta+Shift+Tab,Parcourir les activités (en sens inverse)"
	mkdir -p "$(dirname "$marqueur")"
	touch "$marqueur"
fi
