# Lu au début de chaque session Plasma, avant le bureau.
# Portefeuille de KDE (mots de passe Wi-Fi…) : ouvert sans rien demander à la connexion par mot de passe
# (pam_kwallet5). Sans mot de passe à la connexion (session d'essai, session invitée, connexion automatique),
# il ne protège rien et afficherait son assistant à la première connexion réseau modifiée : il est désactivé
# pour ces sessions-là, et réactivé si la personne repasse à une connexion par mot de passe.
(
	sans_mot_de_passe=false
	grep -qw 'boot=live' /proc/cmdline && sans_mot_de_passe=true
	[ "$(id -un)" = sama-invite ] && sans_mot_de_passe=true
	grep -qs "^User=$(id -un)$" /etc/sddm.conf.d/60-sama-connexion-auto.conf && sans_mot_de_passe=true
	if [ "$sans_mot_de_passe" = true ]; then
		kwriteconfig6 --file kwalletrc --group Wallet --key Enabled false
		kwriteconfig6 --file kwalletrc --group Wallet --key "First Use" false
		kwriteconfig6 --file kwalletrc --group Sama --key DesactiveParSama true
	elif [ "$(kreadconfig6 --file kwalletrc --group Sama --key DesactiveParSama)" = true ]; then
		kwriteconfig6 --file kwalletrc --group Wallet --key Enabled true
		kwriteconfig6 --file kwalletrc --group Sama --key DesactiveParSama --delete
	fi
) >/dev/null 2>&1
