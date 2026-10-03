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
```

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
