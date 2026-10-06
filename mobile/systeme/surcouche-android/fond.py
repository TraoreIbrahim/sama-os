#!/usr/bin/env python3
"""Le fond d'écran par défaut d'Android dans Sama OS : le paysage Banco (Lagune, Aube).
Chaque Espace pose ensuite le sien ; celui-ci se voit le temps qu'un nouvel Espace démarre."""
import os
import sys

from PIL import Image, ImageDraw

L, H = 1080, 2424
SUR = 2
CIEL = (0xF3, 0xEC, 0xE2)
ASTRE = (0xED, 0xC6, 0xA2)
COLLINES = [(0xE7, 0xD5, 0xC1), (0xDD, 0xC2, 0xA6), (0xD0, 0xAC, 0x8B), (0xC3, 0x98, 0x76)]


def bezier(p0, p1, p2, p3, n=48):
    return [((1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t * t * p2[0] + t ** 3 * p3[0],
             (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t * t * p2[1] + t ** 3 * p3[1])
            for t in (i / n for i in range(n + 1))]


def colline(y, sx, sy):
    a = bezier((0, y), (70, y - 20), (140, y - 14), (200, y - 4))
    b = bezier((200, y - 4), (260, y + 6), (320, y - 22), (390, y - 10))
    return [(x * sx, yy * sy) for x, yy in a + b[1:]] + [(390 * sx, 844 * sy), (0, 844 * sy)]


def main(sortie):
    w, h = L * SUR, H * SUR
    sx, sy = w / 390, h / 844
    im = Image.new('RGB', (w, h), CIEL)
    d = ImageDraw.Draw(im)
    r = 70 * sx
    d.ellipse([286 * sx - r, 430 * sy - r, 286 * sx + r, 430 * sy + r], fill=ASTRE)
    for i, y in enumerate((480, 574, 664, 754)):
        d.polygon(colline(y, sx, sy), fill=COLLINES[i])
    os.makedirs(os.path.dirname(sortie), exist_ok=True)
    im.resize((L, H), Image.LANCZOS).save(sortie, optimize=True)


if __name__ == '__main__':
    main(sys.argv[1])
