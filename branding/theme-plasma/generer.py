#!/usr/bin/env python3
"""Génère les cadres du thème Plasma « samaos » : fenêtres surgissantes (panneau du Pouls, bulles de notification,
menus des widgets) et infobulles. Coins arrondis et ombre douce, comme les fenêtres de la maquette.

Les couleurs viennent du jeu de couleurs (classe ColorScheme-Background) : le même fichier sert en clair et en sombre.
Chaque cadre a une version translucide (utilisée quand le flou d'arrière-plan est disponible) et un masque de flou.

Usage : python3 branding/theme-plasma/generer.py
"""
import os

RACINE = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
THEME = os.path.join(RACINE, "iso/config/includes.chroot/usr/share/plasma/desktoptheme/samaos")


def neuf_parts(prefixe, r, x0=0, y0=0, centre=60):
    """Les 9 morceaux d'un cadre aux coins de rayon r (FrameSvg)."""
    c = centre
    d = r + c
    return f'''    <path id="{prefixe}topleft" d="M{x0 + r} {y0} A{r} {r} 0 0 0 {x0} {y0 + r} L{x0 + r} {y0 + r} Z"/>
    <rect id="{prefixe}top" x="{x0 + r}" y="{y0}" width="{c}" height="{r}"/>
    <path id="{prefixe}topright" d="M{x0 + d} {y0} A{r} {r} 0 0 1 {x0 + d + r} {y0 + r} L{x0 + d} {y0 + r} Z"/>
    <rect id="{prefixe}left" x="{x0}" y="{y0 + r}" width="{r}" height="{c}"/>
    <rect id="{prefixe}center" x="{x0 + r}" y="{y0 + r}" width="{c}" height="{c}"/>
    <rect id="{prefixe}right" x="{x0 + d}" y="{y0 + r}" width="{r}" height="{c}"/>
    <path id="{prefixe}bottomleft" d="M{x0} {y0 + d} A{r} {r} 0 0 0 {x0 + r} {y0 + d + r} L{x0 + r} {y0 + d} Z"/>
    <rect id="{prefixe}bottom" x="{x0 + r}" y="{y0 + d}" width="{c}" height="{r}"/>
    <path id="{prefixe}bottomright" d="M{x0 + d} {y0 + d} L{x0 + d + r} {y0 + d} A{r} {r} 0 0 1 {x0 + d} {y0 + d + r} Z"/>'''


def ombre(r, e, opacite, y0):
    """Ombre douce autour d'un cadre de rayon r, étendue e. Chaque tuile déborde de r sous la fenêtre."""
    t = e + r          # taille des tuiles d'angle
    debut = r / t      # le dégradé commence au bord arrondi
    o = opacite
    # Décroissance douce (proche d'un flou gaussien) entre le bord du cadre et la fin de l'ombre
    paliers = [(0, 1), (0.25, 0.55), (0.5, 0.25), (0.75, 0.07), (1, 0)]
    arrets_r = "".join(f'<stop offset="{debut + (1 - debut) * p:.3f}" stop-color="#000" stop-opacity="{o * k:.3f}"/>'
                       for p, k in paliers)
    fin = e / t
    arrets_l = "".join(f'<stop offset="{fin * (1 - p):.3f}" stop-color="#000" stop-opacity="{o * k:.3f}"/>'
                       for p, k in reversed(paliers))
    defs = f'''  <defs>
    <radialGradient id="angle-hg" cx="{t}" cy="{t}" r="{t}" gradientUnits="userSpaceOnUse">
      {arrets_r}</radialGradient>
    <radialGradient id="angle-hd" cx="0" cy="{t}" r="{t}" gradientUnits="userSpaceOnUse">
      {arrets_r}</radialGradient>
    <radialGradient id="angle-bg" cx="{t}" cy="0" r="{t}" gradientUnits="userSpaceOnUse">
      {arrets_r}</radialGradient>
    <radialGradient id="angle-bd" cx="0" cy="0" r="{t}" gradientUnits="userSpaceOnUse">
      {arrets_r}</radialGradient>
    <linearGradient id="bord-h" x1="0" y1="0" x2="0" y2="1">
      {arrets_l}</linearGradient>
    <linearGradient id="bord-b" x1="0" y1="1" x2="0" y2="0">
      {arrets_l}</linearGradient>
    <linearGradient id="bord-g" x1="0" y1="0" x2="1" y2="0">
      {arrets_l}</linearGradient>
    <linearGradient id="bord-d" x1="1" y1="0" x2="0" y2="0">
      {arrets_l}</linearGradient>
  </defs>
'''
    c = 10
    # Chaque tuile dans son propre repère (translate), pour que les dégradés en userSpaceOnUse restent justes
    def tuile(nom, x, y, w, h, remplissage):
        return (f'    <g transform="translate({x} {y})"><rect id="shadow-{nom}" x="0" y="0" width="{w}" height="{h}" '
                f'fill="url(#{remplissage})"/></g>')
    x = 0
    tuiles = [
        tuile("topleft", 0, y0, t, t, "angle-hg"),
        tuile("top", t + 4, y0, c, t, "bord-h"),
        tuile("topright", t + c + 8, y0, t, t, "angle-hd"),
        tuile("left", 0, y0 + t + 4, t, c, "bord-g"),
        tuile("right", t + c + 8, y0 + t + 4, t, c, "bord-d"),
        tuile("bottomleft", 0, y0 + t + c + 8, t, t, "angle-bg"),
        tuile("bottom", t + 4, y0 + t + c + 8, c, t, "bord-b"),
        tuile("bottomright", t + c + 8, y0 + t + c + 8, t, t, "angle-bd"),
    ]
    hints = f'''    <rect id="shadow-hint-top-margin" x="0" y="{y0 - 20}" width="4" height="{e}"/>
    <rect id="shadow-hint-bottom-margin" x="8" y="{y0 - 20}" width="4" height="{e}"/>
    <rect id="shadow-hint-left-margin" x="16" y="{y0 - 20}" width="{e}" height="4"/>
    <rect id="shadow-hint-right-margin" x="{20 + e}" y="{y0 - 20}" width="{e}" height="4"/>'''
    return defs, "\n".join(tuiles), hints, 2 * t + c + 8


def cadre(r, marge, ombre_e, ombre_o, opacite, commentaire):
    taille = 2 * r + 60
    y_ombre = taille + 40
    defs, tuiles, hints, h_ombre = ombre(r, ombre_e, ombre_o, y_ombre)
    hauteur = y_ombre + h_ombre + 4
    largeur = max(taille * 2 + 20, h_ombre)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{largeur}" height="{hauteur}" viewBox="0 0 {largeur} {hauteur}">
  <!-- {commentaire} -->
  <style type="text/css" id="current-color-scheme">.ColorScheme-Background {{ color:#fbf9f6; }}</style>
{defs}  <g class="ColorScheme-Background" fill="currentColor" fill-opacity="{opacite}">
{neuf_parts("", r)}
  </g>
  <!-- Forme du flou d'arrière-plan -->
  <g fill="#000000">
{neuf_parts("mask-", r, x0=taille + 20)}
  </g>
  <!-- Marges intérieures -->
  <g fill="none">
    <rect id="hint-top-margin" x="0" y="{taille + 4}" width="4" height="{marge}"/>
    <rect id="hint-bottom-margin" x="8" y="{taille + 4}" width="4" height="{marge}"/>
    <rect id="hint-left-margin" x="16" y="{taille + 4}" width="{marge}" height="4"/>
    <rect id="hint-right-margin" x="{20 + marge}" y="{taille + 4}" width="{marge}" height="4"/>
  </g>
  <!-- Ombre douce (dessinée par le gestionnaire de fenêtres autour du cadre) -->
  <g fill="none">
{hints}
  </g>
{tuiles}
</svg>
'''


def ecrire(chemin, contenu):
    chemin = os.path.join(THEME, chemin)
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w") as f:
        f.write(contenu)


CADRES = {
    # chemin : (rayon, marge intérieure, étendue de l'ombre, opacité de l'ombre)
    "dialogs/background.svg": (20, 12, 32, 0.13),
    "widgets/tooltip.svg": (12, 8, 18, 0.1),
}

for chemin, (r, marge, e, o) in CADRES.items():
    ecrire(chemin, cadre(r, marge, e, o, 1, f"Cadre Sama ({chemin}), opaque"))
    ecrire("translucent/" + chemin, cadre(r, marge, e, o, 0.94, f"Cadre Sama ({chemin}), translucide sur flou"))
    print("Thème Plasma :", chemin)
