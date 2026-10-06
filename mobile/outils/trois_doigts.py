#!/usr/bin/env python3
"""Trois doigts qui glissent à l'horizontale sur l'émulateur, pour changer d'Espace.

La souris de l'émulateur ne fait que deux doigts : ce script passe par sa console (adb emu event send).

    outils/trois_doigts.py -400                # vers la gauche : l'Espace suivant
    outils/trois_doigts.py 400                 # vers la droite : l'Espace précédent
    outils/trois_doigts.py -400 --sans-lever   # s'arrête doigts posés, pour regarder la transition
    outils/trois_doigts.py --lever             # lève les doigts
"""
import os
import re
import subprocess
import sys

ADB = os.path.join(os.environ.get('ANDROID_HOME', os.path.expanduser('~/Library/Android/sdk')), 'platform-tools', 'adb')


def taille_ecran():
    sortie = subprocess.run([ADB, 'shell', 'wm', 'size'], capture_output=True, text=True, check=True).stdout
    tailles = re.findall(r'(\d+)x(\d+)', sortie)
    largeur, hauteur = tailles[-1]  # la taille forcée, s'il y en a une, vient en dernier
    return int(largeur), int(hauteur)


def envoyer(*evenements):
    subprocess.run([ADB, 'emu', 'event', 'send', *evenements, 'EV_SYN:0:0'], check=True, stdout=subprocess.DEVNULL)


def lever():
    ev = []
    for i in range(3):
        ev += [f'EV_ABS:ABS_MT_SLOT:{i}', 'EV_ABS:ABS_MT_TRACKING_ID:-1']
    envoyer(*ev)


def glisser(dx, etapes, sans_lever):
    largeur, hauteur = taille_ecran()
    # L'écran tactile de l'émulateur compte de 0 à 32767 dans chaque sens.
    def rx(x): return int(x * 32767 / largeur)
    def ry(y): return int(y * 32767 / hauteur)
    x0 = largeur * 0.6 if dx < 0 else largeur * 0.3
    doigts = [(x0, hauteur * 0.41), (x0 + largeur * 0.055, hauteur * 0.52), (x0 + largeur * 0.11, hauteur * 0.62)]
    ev = []
    for i, (x, y) in enumerate(doigts):
        ev += [f'EV_ABS:ABS_MT_SLOT:{i}', f'EV_ABS:ABS_MT_TRACKING_ID:{200 + i}', f'EV_ABS:ABS_MT_POSITION_X:{rx(x)}',
               f'EV_ABS:ABS_MT_POSITION_Y:{ry(y)}', 'EV_ABS:ABS_MT_PRESSURE:400']
    envoyer(*ev)
    for k in range(1, etapes + 1):
        ev = []
        for i, (x, _) in enumerate(doigts):
            ev += [f'EV_ABS:ABS_MT_SLOT:{i}', f'EV_ABS:ABS_MT_POSITION_X:{rx(x + dx * k / etapes)}']
        envoyer(*ev)
    if not sans_lever:
        lever()


if __name__ == '__main__':
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    if args[0] == '--lever':
        lever()
    else:
        glisser(int(args[0]), 10, '--sans-lever' in args)
