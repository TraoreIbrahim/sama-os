# Lu au début de chaque session Plasma, avant le bureau.
# Dossier du bureau en français : sur la session live, il est créé en anglais (« Desktop ») avant le passage
# au français, et Fichiers montrait alors « Desktop » à côté de « Bureau ». On le renomme, contenu compris.
(
	dossiers="${XDG_CONFIG_HOME:-$HOME/.config}/user-dirs.dirs"
	case "${LANG:-}" in fr*) ;; *) exit 0 ;; esac
	grep -q '^XDG_DESKTOP_DIR="$HOME/Desktop"' "$dossiers" 2>/dev/null || exit 0
	[ -e "$HOME/Bureau" ] && exit 0
	if [ -d "$HOME/Desktop" ]; then mv "$HOME/Desktop" "$HOME/Bureau"; else mkdir -p "$HOME/Bureau"; fi
	xdg-user-dirs-update --set DESKTOP "$HOME/Bureau"
)
