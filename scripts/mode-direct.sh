#!/bin/sh
# Mode direct : modifier l'interface de Sama OS et la voir tout de suite dans la machine virtuelle,
# sans reconstruire l'ISO (équivalent du « rechargement à chaud » des projets web).
#
# Usage :
#   scripts/mode-direct.sh connecter <adresse>   Mémorise l'adresse de la machine virtuelle et teste l'accès
#   scripts/mode-direct.sh envoyer               Envoie widgets, couleurs, thème et identité, puis recharge le bureau
#   scripts/mode-direct.sh natte                 Recrée la Natte et le bureau d'après la disposition Sama
#   scripts/mode-direct.sh capture [nom]         Capture l'écran de la machine virtuelle dans sortie/captures/
#   scripts/mode-direct.sh commande "<cmd>"      Exécute une commande dans la session Sama
#
# Prérequis dans la machine virtuelle : voir dev/LISEZMOI.md (une commande à taper une fois).
set -eu
# Pas de métadonnées macOS dans les archives envoyées (évite les avertissements de tar côté Linux)
export COPYFILE_DISABLE=1

racine="$(cd "$(dirname "$0")/.." && pwd)"
fichier_adresse="$racine/.mode-direct-adresse"
cle="$HOME/.ssh/samaos_dev"

adresse() {
	[ -f "$fichier_adresse" ] || { echo "Adresse inconnue : lancez d'abord « mode-direct.sh connecter <adresse> »" >&2; exit 1; }
	cat "$fichier_adresse"
}

# Exécute une commande dans la session graphique de l'utilisateur sama (Wayland, D-Bus)
vm() {
	ssh -i "$cle" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR \
		-o ConnectTimeout=8 "sama@$(adresse)" \
		"export XDG_RUNTIME_DIR=/run/user/\$(id -u) WAYLAND_DISPLAY=wayland-0 DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/\$(id -u)/bus QT_QPA_PLATFORM=wayland; $1"
}

case "${1:-}" in
connecter)
	[ -n "${2:-}" ] || { echo "Usage : mode-direct.sh connecter <adresse>" >&2; exit 1; }
	echo "$2" > "$fichier_adresse"
	vm 'echo "Connecté à $(hostname) — $(. /etc/os-release; echo "$PRETTY_NAME")"'
	;;

envoyer)
	inclus="$racine/iso/config/includes.chroot"
	travail="$(mktemp -d)"
	# Widgets de l'utilisateur : ils remplacent ceux de l'ISO sans droits administrateur
	mkdir -p "$travail/plasmoids"
	for dossier in "$racine"/natte/org.samaos.* "$racine"/bureau/org.samaos.*; do
		cp -R "$dossier" "$travail/plasmoids/"
	done
	cp "$racine"/branding/qml/*.qml "$travail/plasmoids/org.samaos.natte/contents/ui/"
	tar --no-xattrs -C "$travail" -czf - plasmoids | vm 'mkdir -p ~/.local/share/plasma && rm -rf ~/.local/share/plasma/plasmoids/org.samaos.* && tar -xzf - -C ~/.local/share/plasma'
	rm -rf "$travail"

	cp "$racine"/branding/qml/ChargementSama.qml "$inclus/usr/share/plasma/look-and-feel/org.samaos.bureau/contents/splash/"
	printf "%s" "$(cat "$inclus/etc/xdg/ksplashrc")" | vm "sudo tee /etc/xdg/ksplashrc >/dev/null"

	# Identité visuelle, couleurs, thème Plasma, apparence (fichiers système : sudo sans mot de passe en session d'essai)
	tar --no-xattrs -C "$racine/branding" -czf - logo-sama.svg logo-cour.svg fonds icones demarrage installateur \
		| vm 'sudo mkdir -p /usr/share/samaos && sudo tar -xzf - -C /usr/share/samaos'
	tar --no-xattrs -C "$inclus/usr/share" -czf - color-schemes plasma/desktoptheme plasma/look-and-feel icons/sama \
		| vm 'sudo tar -xzf - -C /usr/share'
	# Scripts Sama (organisation du menu…) et application du tri des applications
	tar --no-xattrs -C "$inclus/usr/libexec" -czf - samaos | vm 'sudo tar -xzf - -C /usr/libexec && sudo sh /usr/libexec/samaos/organiser-applications.sh && kbuildsycoca6 >/dev/null 2>&1'

	# Recharger le bureau (quelques secondes)
	vm 'systemctl --user restart plasma-plasmashell.service 2>/dev/null || (kquitapp6 plasmashell; sleep 1; setsid plasmashell >/dev/null 2>&1 &)'
	echo "Envoyé, bureau rechargé."
	;;

natte)
	# Supprime les barres et widgets du bureau, puis rejoue la disposition Sama
	disposition="$racine/iso/config/includes.chroot/usr/share/plasma/look-and-feel/org.samaos.bureau/contents/layouts/org.kde.plasma.desktop-layout.js"
	script="panels().forEach(function (p) { p.remove(); }); desktops().forEach(function (d) { d.widgets().forEach(function (w) { w.remove(); }); }); $(cat "$disposition")"
	printf '%s' "$script" | vm 'script=$(cat); qdbus6 org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "$script" || qdbus org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "$script"'
	echo "Natte et bureau recréés."
	;;

capture)
	nom="${2:-capture-$(date +%H%M%S)}"
	mkdir -p "$racine/sortie/captures"
	vm 'spectacle --background --nonotify --fullscreen --output /tmp/sama-capture.png >/dev/null 2>&1; cat /tmp/sama-capture.png' \
		> "$racine/sortie/captures/$nom.png"
	echo "$racine/sortie/captures/$nom.png"
	;;

commande)
	vm "${2:?Usage : mode-direct.sh commande \"<cmd>\"}"
	;;

*)
	sed -n '2,13p' "$0"
	exit 1
	;;
esac
