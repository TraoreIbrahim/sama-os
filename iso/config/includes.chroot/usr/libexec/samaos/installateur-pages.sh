#!/bin/sh
# Installateur de Sama : pages en QML (dessinées par Sama dans /etc/calamares/branding/samaos) à la place des
# pages en widgets de Calamares. Les modules « …q » remplacent les modules d'origine, pour l'affichage comme
# pour les tâches d'installation qu'ils fournissent. Le disque reste la page d'origine (habillée par stylesheet.qss).
# Lancé par la construction de l'ISO (et par le mode direct).
set -e
REGLAGES=/etc/calamares/settings.conf
MODULES=/etc/calamares/modules
[ -f "$REGLAGES" ] || exit 0

sed -i -e 's/^\(\s*- \)welcome$/\1welcomeq/' -e 's/^\(\s*- \)locale$/\1localeq/' -e 's/^\(\s*- \)keyboard$/\1keyboardq/' \
       -e 's/^\(\s*- \)users$/\1usersq/' -e 's/^\(\s*- \)summary$/\1summaryq/' -e 's/^\(\s*- \)finished$/\1finishedq/' "$REGLAGES"

# Réglages des modules d'origine repris par leurs versions QML
for m in welcome users finished; do
	[ -f "$MODULES/$m.conf" ] && [ ! -f "$MODULES/${m}q.conf" ] && cp "$MODULES/$m.conf" "$MODULES/${m}q.conf"
done
# Région par défaut : Côte d'Ivoire (Abidjan), sans géolocalisation par Internet
cat > "$MODULES/localeq.conf" <<'FIN'
region: "Africa"
zone: "Abidjan"
FIN
true
