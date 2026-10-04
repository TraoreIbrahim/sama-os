#!/usr/bin/env python3
"""Souris et clavier virtuels pour tester l'interface de Sama en mode direct (machine virtuelle de développement).

Usage (en administrateur, dans la VM) :
  entrees.py clic X Y [droit]       clic à la position X, Y (pixels de l'écran)
  entrees.py double X Y             double-clic
  entrees.py bouger X Y             déplace la souris
  entrees.py glisser X1 Y1 X2 Y2    glisser-déposer de (X1, Y1) à (X2, Y2)
  entrees.py taper "texte"          tape un texte (clavier AZERTY)
  entrees.py touche entree|echap|effacer|tab|haut|bas|gauche|droite
  entrees.py raccourci meta+tab     combinaison (meta, ctrl, alt, maj + une touche)
  entrees.py molette X Y 5           fait tourner la molette (5 crans vers le bas ; négatif : vers le haut)
  entrees.py tenir alt+tab 3        combinaison dont la touche de modification reste enfoncée 3 s (ex. Alt+Tab)
Plusieurs actions peuvent s'enchaîner, séparées par « -- ».
"""
import sys
import time
from evdev import UInput, ecodes as e, AbsInfo

LARGEUR, HAUTEUR = 1920, 1080

# AZERTY : caractère → (touche physique, majuscule)
AZERTY = {}
for c, k in zip("azertyuiop", ["Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"]):
    AZERTY[c] = (getattr(e, "KEY_" + k), False)
for c, k in zip("qsdfghjklm", ["A", "S", "D", "F", "G", "H", "J", "K", "L", "SEMICOLON"]):
    AZERTY[c] = (getattr(e, "KEY_" + k), False)
for c, k in zip("wxcvbn", ["Z", "X", "C", "V", "B", "N"]):
    AZERTY[c] = (getattr(e, "KEY_" + k), False)
for c in list(AZERTY):
    AZERTY[c.upper()] = (AZERTY[c][0], True)
AZERTY[" "] = (e.KEY_SPACE, False)
AZERTY["-"] = (e.KEY_6, False)
AZERTY["'"] = (e.KEY_4, False)
AZERTY[":"] = (e.KEY_DOT, False)
AZERTY["é"] = (e.KEY_2, False)
AZERTY["è"] = (e.KEY_7, False)
for i, c in enumerate("1234567890"):
    AZERTY[c] = (getattr(e, "KEY_" + "1234567890"[i]), True)

TOUCHES = {"entree": e.KEY_ENTER, "echap": e.KEY_ESC, "effacer": e.KEY_BACKSPACE, "tab": e.KEY_TAB,
           "haut": e.KEY_UP, "bas": e.KEY_DOWN, "gauche": e.KEY_LEFT, "droite": e.KEY_RIGHT}

capacites = {
    e.EV_KEY: [e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE] + [k for k, _ in AZERTY.values()]
              + list(TOUCHES.values()) + [e.KEY_LEFTSHIFT, e.KEY_LEFTMETA, e.KEY_LEFTCTRL, e.KEY_LEFTALT],
    e.EV_ABS: [(e.ABS_X, AbsInfo(0, 0, LARGEUR - 1, 0, 0, 0)), (e.ABS_Y, AbsInfo(0, 0, HAUTEUR - 1, 0, 0, 0))],
    e.EV_REL: [e.REL_WHEEL],
}
ui = UInput(capacites, name="sama-entrees-test")
time.sleep(0.8)  # laisser le compositeur découvrir le périphérique


def bouger(x, y):
    ui.write(e.EV_ABS, e.ABS_X, int(x))
    ui.write(e.EV_ABS, e.ABS_Y, int(y))
    ui.syn()
    time.sleep(0.15)


def bouton(b):
    ui.write(e.EV_KEY, b, 1); ui.syn(); time.sleep(0.05)
    ui.write(e.EV_KEY, b, 0); ui.syn(); time.sleep(0.1)


def touche(k, maj=False):
    if maj:
        ui.write(e.EV_KEY, e.KEY_LEFTSHIFT, 1); ui.syn()
    ui.write(e.EV_KEY, k, 1); ui.syn(); time.sleep(0.02)
    ui.write(e.EV_KEY, k, 0); ui.syn()
    if maj:
        ui.write(e.EV_KEY, e.KEY_LEFTSHIFT, 0); ui.syn()
    time.sleep(0.04)


actions = " ".join(sys.argv[1:]).split(" -- ") if len(sys.argv) > 1 else []
for action in actions:
    a = action.split(None, 1)
    nom, reste = a[0], (a[1] if len(a) > 1 else "")
    if nom in ("clic", "double", "bouger"):
        p = reste.split()
        bouger(p[0], p[1])
        if nom == "clic":
            bouton(e.BTN_RIGHT if len(p) > 2 and p[2] == "droit" else e.BTN_LEFT)
        elif nom == "double":
            bouton(e.BTN_LEFT); bouton(e.BTN_LEFT)
    elif nom == "glisser":
        x1, y1, x2, y2 = [float(v) for v in reste.split()]
        bouger(x1, y1)
        ui.write(e.EV_KEY, e.BTN_LEFT, 1); ui.syn(); time.sleep(0.2)
        for i in range(1, 31):
            bouger(x1 + (x2 - x1) * i / 30, y1 + (y2 - y1) * i / 30)
        time.sleep(0.3)
        ui.write(e.EV_KEY, e.BTN_LEFT, 0); ui.syn(); time.sleep(0.3)
    elif nom == "taper":
        for c in reste.strip().strip('"'):
            if c in AZERTY:
                touche(*AZERTY[c])
    elif nom == "raccourci":
        mods = {"meta": e.KEY_LEFTMETA, "ctrl": e.KEY_LEFTCTRL, "alt": e.KEY_LEFTALT, "maj": e.KEY_LEFTSHIFT}
        parties = reste.strip().lower().split("+")
        tenus = [mods[p] for p in parties[:-1]]
        for m in tenus:
            ui.write(e.EV_KEY, m, 1); ui.syn(); time.sleep(0.05)
        derniere = parties[-1]
        touche(TOUCHES[derniere] if derniere in TOUCHES else AZERTY[derniere][0])
        for m in reversed(tenus):
            ui.write(e.EV_KEY, m, 0); ui.syn(); time.sleep(0.05)
    elif nom == "tenir":
        mods = {"meta": e.KEY_LEFTMETA, "ctrl": e.KEY_LEFTCTRL, "alt": e.KEY_LEFTALT, "maj": e.KEY_LEFTSHIFT}
        combinaison, duree = (reste.split() + ["2"])[:2]
        parties = combinaison.lower().split("+")
        tenus = [mods[p] for p in parties[:-1]]
        for m in tenus:
            ui.write(e.EV_KEY, m, 1); ui.syn(); time.sleep(0.05)
        derniere = parties[-1]
        touche(TOUCHES[derniere] if derniere in TOUCHES else AZERTY[derniere][0])
        time.sleep(float(duree))
        for m in reversed(tenus):
            ui.write(e.EV_KEY, m, 0); ui.syn(); time.sleep(0.05)
    elif nom == "touche":
        touche(TOUCHES[reste.strip()])
    elif nom == "molette":
        # molette X Y crans  (crans > 0 : vers le bas)
        x, y, crans = reste.split()
        bouger(x, y)
        for i in range(abs(int(crans))):
            ui.write(e.EV_REL, e.REL_WHEEL, -1 if int(crans) > 0 else 1); ui.syn(); time.sleep(0.05)
    time.sleep(0.4)
ui.close()
