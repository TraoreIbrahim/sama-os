#!/usr/bin/env python3
"""Fabrique l'animation de démarrage de Sama OS mobile (bootanimation.zip).

Le paysage de Banco en Nuit : le soleil se lève derrière les collines, l'éléphant apparaît
dans le soleil, puis le nom. Pas de motif décoratif : le paysage suffit.

    python3 systeme/animation/fabriquer.py [largeur hauteur]   →  systeme/build/bootanimation.zip
"""
import io
import os
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

ICI = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.normpath(os.path.join(ICI, '..', '..'))
POLICE = os.path.join(MOBILE, 'accueil', 'src', 'main', 'res', 'font', 'noto_sans.ttf')
SORTIE = os.path.join(MOBILE, 'systeme', 'build', 'bootanimation.zip')

L, H = (int(sys.argv[1]), int(sys.argv[2])) if len(sys.argv) > 2 else (1080, 2424)
SUR = 2  # on dessine deux fois plus grand puis on réduit : bords lisses
IPS = 30

# Banco, thème Nuit.
CIEL = (0x15, 0x1A, 0x2B)
ASTRE = (0xE6, 0xDA, 0xC2)
COLLINES = [(0x1B, 0x21, 0x35), (0x20, 0x27, 0x3F), (0x25, 0x2E, 0x4A), (0x2B, 0x35, 0x55)]
ENCRE = (0xF1, 0xEB, 0xE1)


def bezier(p0, p1, p2, p3, n=48):
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        pts.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                    u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
    return pts


def colline(y, sx, sy):
    """La colline de Banco : « M0 y C 70 y-20, 140 y-14, 200 y-4 S 320 y-22, 390 y-10 », fermée vers le bas."""
    a = bezier((0, y), (70, y - 20), (140, y - 14), (200, y - 4))
    b = bezier((200, y - 4), (260, y + 6), (320, y - 22), (390, y - 10))
    pts = [(x * sx, yy * sy) for x, yy in a + b[1:]]
    return pts + [(390 * sx, 844 * sy), (0, 844 * sy)]


def elephant(d, cx, cy, taille, couleur, fond):
    """L'éléphant de Sama sur sa grille de 100, centré en (cx, cy)."""
    s = taille / 100
    ox, oy = cx - 50 * s, cy - 50 * s

    def P(x, y):
        return ox + x * s, oy + y * s

    for x in (25, 75):
        (x0, y0), (x1, y1) = P(x - 21, 19), P(x + 21, 61)
        d.ellipse([x0, y0, x1, y1], fill=couleur)
    (x0, y0), (x1, y1) = P(31, 17), P(69, 63)
    d.ellipse([x0, y0, x1, y1], fill=couleur, outline=fond, width=max(1, round(4 * s)))
    trompe = bezier((50, 58), (50, 70), (63, 66), (63, 77), 24) + bezier((63, 77), (63, 88), (47, 90), (42, 83), 24)[1:]
    pts = [P(x, y) for x, y in trompe]
    d.line(pts, fill=couleur, width=round(10 * s), joint='curve')
    r = 5 * s
    for x, y in (pts[0], pts[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=couleur)
    for x in (43, 57):
        ex, ey = P(x, 36)
        e = 2.6 * s
        d.ellipse([ex - e, ey - e, ex + e, ey + e], fill=fond)


def melange(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


def adoucir(t):
    return 1 - (1 - t) ** 3


def image(soleil_y, elephant_t, nom_t):
    w, h = L * SUR, H * SUR
    sx, sy = w / 390, h / 844
    im = Image.new('RGB', (w, h), CIEL)
    d = ImageDraw.Draw(im)
    # Le soleil, au centre, derrière la première colline.
    r = 74 * sx
    cx, cy = 195 * sx, soleil_y * sy
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ASTRE)
    if elephant_t > 0:
        elephant(d, cx, cy + 2 * sy, 1.18 * r, melange(ASTRE, CIEL, elephant_t), ASTRE)
    for i, y in enumerate((480, 574, 664, 754)):
        d.polygon(colline(y, sx, sy), fill=COLLINES[i])
    if nom_t > 0:
        police = ImageFont.truetype(POLICE, round(46 * sx))
        police.set_variation_by_axes([600, 87.5])  # poids, largeur : le « monument » de Banco
        texte = 'Sama'
        x0, y0, x1, y1 = d.textbbox((0, 0), texte, font=police)
        d.text(((w - (x1 - x0)) / 2 - x0, 600 * sy - (y1 - y0) / 2 - y0 + (1 - nom_t) * 10 * sy), texte,
               font=police, fill=melange(COLLINES[2], ENCRE, nom_t))
    return im.resize((L, H), Image.LANCZOS)


def png(im):
    tampon = io.BytesIO()
    im.save(tampon, 'PNG', optimize=True)
    return tampon.getvalue()


def main():
    os.makedirs(os.path.dirname(SORTIE), exist_ok=True)
    images = []
    # 1,2 s : le soleil se lève.
    for i in range(36):
        images.append(image(560 - 150 * adoucir(i / 35), 0, 0))
    # 0,6 s : l'éléphant apparaît dans le soleil.
    for i in range(18):
        images.append(image(410, adoucir((i + 1) / 18), 0))
    # 0,6 s : le nom monte doucement.
    for i in range(18):
        images.append(image(410, 1, adoucir((i + 1) / 18)))
    fin = image(410, 1, 1)
    with zipfile.ZipFile(SORTIE, 'w', zipfile.ZIP_STORED) as z:
        # Une fois l'intro, puis l'image finale tant que le système démarre.
        z.writestr('desc.txt', f'{L} {H} {IPS}\np 1 0 part0\np 0 0 part1\n')
        for i, im in enumerate(images):
            z.writestr(f'part0/{i:03d}.png', png(im))
        z.writestr('part1/000.png', png(fin))
    print(SORTIE, f'{os.path.getsize(SORTIE) / 1e6:.1f} Mo, {len(images) + 1} images')


if __name__ == '__main__':
    main()
