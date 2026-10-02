#!/bin/sh
# Installe l'écran de verrouillage de Sama à la place de l'interface de Plasma.
# Le fichier d'origine est détourné (dpkg-divert) : une mise à jour de Plasma ne remet pas l'ancien écran.
# À lancer en administrateur (construction de l'ISO, mode direct).
set -e
dossier=/usr/share/plasma/shells/org.kde.plasma.desktop/contents/lockscreen
# (relancer le script est sans effet de bord : la diversion existante est conservée)
dpkg-divert --quiet --local --rename --divert "$dossier/LockScreenUi.qml.plasma" --add "$dossier/LockScreenUi.qml"
cp /usr/libexec/samaos/verrouillage/LockScreenUi.qml "$dossier/LockScreenUi.qml"
