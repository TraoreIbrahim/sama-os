# Lu au début de chaque session Plasma, avant le bureau.
# Dossier du bureau en français : sur la session live, un dossier « Desktop » est créé avant le passage au
# français (avec le raccourci d'installation), à côté de « Bureau » ou à sa place selon l'ordre de démarrage.
# On garde un seul dossier, « Bureau », avec tout le contenu.
(
	case "${LANG:-}" in fr*) ;; *) exit 0 ;; esac
	[ -d "$HOME/Desktop" ] || exit 0
	mkdir -p "$HOME/Bureau"
	for f in "$HOME/Desktop"/* "$HOME/Desktop"/.[!.]*; do
		[ -e "$f" ] && mv -n "$f" "$HOME/Bureau/"
	done
	rmdir "$HOME/Desktop" 2>/dev/null
	xdg-user-dirs-update --set DESKTOP "$HOME/Bureau"
)
