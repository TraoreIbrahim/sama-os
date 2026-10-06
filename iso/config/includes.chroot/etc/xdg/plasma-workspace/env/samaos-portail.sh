# Fenêtres « Ouvrir » et « Enregistrer sous » : les applications KDE et Qt passent par le portail de bureau, donc par
# le sélecteur de fichiers de Sama (/usr/libexec/samaos/portail-fichiers). LibreOffice (Sama Docs, Sheet,
# Présentations) garde celle de KDE : ses lanceurs remettent la variable à 0 (voir organiser-applications.sh).
export PLASMA_INTEGRATION_USE_PORTAL=1
