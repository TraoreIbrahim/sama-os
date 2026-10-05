# Mode direct (développement de l'interface)

Le mode direct permet de modifier l'interface de Sama OS (Natte, Pouls, Cour, widgets, couleurs)
et de voir le résultat **tout de suite** dans la machine virtuelle UTM, sans reconstruire l'ISO.

## Activer l'accès dans la machine virtuelle (une fois par démarrage de la session d'essai)

Dans Sama, ouvrez le terminal **Konsole** et tapez :

```bash
sudo apt-get update && sudo apt-get install -y openssh-server kde-spectacle && mkdir -p ~/.ssh && curl -fsSL https://raw.githubusercontent.com/TraoreIbrahim/sama-os/main/dev/cle-dev.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && ip -4 -brief address
```

La dernière ligne affiche l'adresse de la machine virtuelle (par exemple `192.168.64.5`).

## Depuis le Mac

```bash
scripts/mode-direct.sh connecter 192.168.64.5
scripts/mode-direct.sh envoyer       # envoie les changements et recharge le bureau
scripts/mode-direct.sh natte         # recrée la Natte et les cartes du bureau
scripts/mode-direct.sh capture       # capture d'écran dans sortie/captures/
scripts/mode-direct.sh rattraper     # met une VM démarrée sur une ancienne ISO au niveau du projet
scripts/mode-direct.sh installateur  # envoie les pages de l'installateur et le relance
scripts/mode-direct.sh installateur essai   # idem, avec une installation factice (pauses, aucun disque touché)
```

En mode « essai », « Installer maintenant » ne fait que deux pauses de 20 secondes : on peut parcourir
toutes les pages jusqu'à « Sama est installé » sans risque. Sans « essai », c'est le vrai installateur :
ne jamais cliquer « Installer maintenant » dans la VM de développement (le disque serait effacé).

## Sécurité

`cle-dev.pub` est la partie **publique** d'une clé : seule, elle ne donne accès à rien.
La partie privée reste sur le Mac du développeur (`~/.ssh/samaos_dev`).
Cet accès n'existe que dans la session d'essai de la machine virtuelle de développement,
jamais dans les ISO destinées aux utilisateurs.

## Tester les gestes sans toucher la machine virtuelle

`dev/entrees.py` crée une souris et un clavier virtuels (AZERTY) pour cliquer et taper dans l'interface :

```bash
scripts/mode-direct.sh commande 'sudo apt-get install -y python3-evdev; cat > /tmp/entrees.py' < dev/entrees.py
scripts/mode-direct.sh commande 'sudo python3 /tmp/entrees.py clic 993 1041 -- taper Boutique -- touche entree'
```

## Fenêtres X11 toutes noires

Après de longues heures et plusieurs mises en veille de l'écran, XWayland peut ne plus afficher que du noir dans
la machine virtuelle (Calamares, LibreOffice sans `libreoffice-kf6`, toute application lancée en X11). KWin le
relance aussitôt qu'on l'arrête (les applications X11 ouvertes se ferment) :

```bash
scripts/mode-direct.sh commande 'kill $(pgrep -x Xwayland)'
```

## Instantanés et mises à jour au redémarrage

La session d'essai ne tourne pas sur Btrfs : `dev/essai-instantanes.sh` monte un petit disque Btrfs fictif et y
joue tout le scénario (instantané, mise à jour coupée net, restauration, rangement) avec les variables `SAMA_*`
d'`instantanes.py`. Réglages › Sauvegarde peut afficher ce disque d'essai :

```bash
scripts/mode-direct.sh commande 'E=/tmp/essai-btrfs; systemd-run --user --collect -E SAMA_RACINE_SYSTEME=$E/racine -E SAMA_INSTANTANES=$E/racine/.instantanes sama-reglages sauvegarde'
```

L'écran de mise à jour au redémarrage (ses-05) se voit dans une fenêtre, avec le module X11 de Plymouth
(`plymouth-x11`, 12 Ko) et des paquets fictifs (rien n'est installé) :

```bash
scripts/mode-direct.sh commande 'export DISPLAY=:0 XAUTHORITY=$(ls /run/user/1000/xauth_*); sudo -E plymouthd --mode=boot --kernel-command-line="splash plymouth.ignore-udev plymouth.ignore-serial-consoles"; sudo plymouth show-splash; sudo python3 /usr/libexec/samaos/maj-redemarrage.py essai 5; sudo plymouth quit'
```

Le vrai retour en arrière (menu de démarrage, `restauration-init`) ne s'essaie que sur un Sama installé.

## Sama installé dans la machine virtuelle

Depuis le 5 octobre, la VM a aussi un Sama installé sur son disque (Btrfs) : compte `sama`, mot de passe de test dans
`.vm-identifiants` (fichier local, jamais versionné), connexion automatique, accès SSH de développement et `sudo` sans
mot de passe (`/etc/sudoers.d/90-sama-vm-dev`, propre à cette VM). Sans ISO dans le lecteur CD/DVD d'UTM, la VM
démarre sur ce disque ; pour revenir à la session d'essai, remettre l'ISO dans le lecteur.

Essai d'une coupure de courant pendant une mise à jour : un faux paquet `sama-essai` dont la version 2 coupe la machine
pendant son installation (`echo b > /proc/sysrq-trigger`), servi par un dépôt local en `copy:` (une source `file:` ne
passe pas par le cache d'APT, que l'installation au redémarrage utilise). Au démarrage suivant, le menu de démarrage
ramène l'instantané pris juste avant, et la fenêtre « Sama a été restauré » s'ouvre.
