#!/usr/bin/env python3
"""
Les icônes des applis de Sama (validées le 7 octobre 2026) : un fond en dégradé avec un léger reflet, un dessin
plein. Chaque appli reçoit deux vecteurs (fond, dessin) et son icône adaptative d'Android.

Les dessins sont décrits sur 108 × 108, la partie visible de l'icône ; Android montre les 72 du milieu d'un calque
de 108 : un groupe ramène donc le dessin au centre (× 2/3, décalé de 18). Le fond déborde jusqu'aux bords du calque.

Relancer après une retouche :  python3 mobile/outils/icones.py
"""
import math
import os

ICI = os.path.dirname(os.path.abspath(__file__))
MOBILE = os.path.dirname(ICI)


# ——— Formes ———

def rr(x, y, w, h, r):
    return (f"M{x + r},{y} h{w - 2 * r} a{r},{r} 0 0 1 {r},{r} v{h - 2 * r} a{r},{r} 0 0 1 -{r},{r} "
            f"h-{w - 2 * r} a{r},{r} 0 0 1 -{r},-{r} v-{h - 2 * r} a{r},{r} 0 0 1 {r},-{r} z")


def cercle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1 0 {2 * r},0 a{r},{r} 0 1 0 -{2 * r},0 z"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx},{cy} a{rx},{ry} 0 1 0 {2 * rx},0 a{rx},{ry} 0 1 0 -{2 * rx},0 z"


def trait(x1, y1, x2, y2):
    return f"M{x1:.2f},{y1:.2f} L{x2:.2f},{y2:.2f}"


def lineaire(a, b, y1=0, y2=108, x1=54, x2=54):
    return ("lin", a, b, x1, y1, x2, y2)


def radial(cx, cy, r, *arrets):
    return ("rad", cx, cy, r, arrets)


def plein(d, couleur, alpha=None):
    return ("plein", d, couleur, alpha)


def trace(d, couleur, epaisseur, alpha=None):
    return ("trace", d, couleur, epaisseur, alpha)


def tourne(angle, *elements):
    return ("groupe", angle, elements)


# ——— Les applis ———

def reflet():
    return plein("M-27,-27 h162 v81 h-162 z", lineaire("#2EFFFFFF", "#00FFFFFF", 0, 54))


def fond(a, b, avec_reflet=True):
    l = [plein("M-27,-27 h162 v162 h-162 z", lineaire(a, b))]
    return l + ([reflet()] if avec_reflet else [])


def graduations(n, cx, cy, r_grand, r_petit, r_ext, tous, couleur, ep_grand, ep_petit, alpha_petit=1.0):
    l = []
    for i in range(n):
        a = i * 2 * math.pi / n
        grand = i % tous == 0
        r1 = r_grand if grand else r_petit
        d = trait(cx + r1 * math.sin(a), cy - r1 * math.cos(a), cx + r_ext * math.sin(a), cy - r_ext * math.cos(a))
        l.append(trace(d, couleur, ep_grand if grand else ep_petit, None if grand else alpha_petit))
    return l


ICONES = {
    "agenda": dict(
        fond=fond("#FFFFFF", "#EFE9E1", False),
        dessin=[
            plein(rr(26, 28, 56, 54, 10), "#FFFFFF"),
            trace(rr(26, 28, 56, 54, 10), "#E4DDD3", 1.5),
            plein("M26,38 a10,10 0 0 1 10,-10 h36 a10,10 0 0 1 10,10 v7 h-56 z", "#D9542F"),
            plein(rr(38, 23, 5, 12, 2.5), "#7A2E17"),
            plein(rr(65, 23, 5, 12, 2.5), "#7A2E17"),
        ] + [
            plein(rr(34 + c * 11, 52 + r * 9, 6.5, 5, 1.6), "#D9542F" if (c, r) == (2, 1) else "#3D5A99", None if (c, r) == (2, 1) else 0.32)
            for c in range(4) for r in range(3)
        ],
    ),
    "appareil": dict(
        fond=fond("#4A453F", "#1C1A17"),
        dessin=[
            plein(rr(24, 36, 60, 42, 10), "#EFE6D6"),
            plein("M42,37 l4,-8 h16 l4,8 z", "#EFE6D6"),
            plein(cercle(54, 57, 15), "#2A2724"),
            plein(cercle(54, 57, 11), radial(49.8, 53.7, 17.6, (0, "#6E8FB0"), (0.6, "#22374C"), (1, "#0E1822"))),
            plein(cercle(50, 53, 3), "#FFFFFF", 0.8),
            plein(cercle(74, 44, 2.5), "#D9542F"),
        ],
    ),
    "calculatrice": dict(
        fond=fond("#45403A", "#211E1A"),
        dessin=[
            plein(rr(27 + c * 19, 24 + r * 16, 15, 12.5, 6.2), "#F08A3C" if c == 2 else ("#CFC8BE" if r == 0 else "#5D5650"))
            for r in range(4) for c in range(3)
        ],
    ),
    "contacts": dict(
        fond=fond("#D4A27A", "#93653F"),
        dessin=[
            plein(cercle(54, 44, 13), "#FFFFFF"),
            plein("M28,80 c2,-14 13,-22 26,-22 s24,8 26,22 z", "#FFFFFF"),
        ],
    ),
    "dictaphone": dict(
        fond=fond("#E0634F", "#9A2F2C"),
        dessin=[plein(rr(x - 2.5, 54 - h / 2, 5, h, 2.5), "#FFFFFF") for x, h in [(30, 8), (37, 20), (44, 34), (51, 22), (58, 40), (65, 26), (72, 14), (79, 8)]],
    ),
    "fichiers": dict(
        fond=fond("#FFFFFF", "#EEE7DE", False),
        dessin=[
            plein("M24,38 a6,6 0 0 1 6,-6 h15 l6,7 h27 a6,6 0 0 1 6,6 v29 a6,6 0 0 1 -6,6 H30 a6,6 0 0 1 -6,-6 z", "#E08A2E"),
            plein(rr(24, 42, 60, 38, 6), lineaire("#FFC75A", "#F29A36", 42, 80)),
        ],
    ),
    "griot": dict(
        fond=fond("#2F3C63", "#151B30"),
        dessin=[trace(cercle(54, 54, 27), "#F2C879", 2.5)]
        + graduations(24, 54, 54, 19.5, 22, 24.5, 6, "#F2C879", 2, 1.2, 0.6)
        + [plein("M54,54 L66,40 L58,58 z", "#F2C879"), plein("M54,54 L42,68 L50,50 z", "#FFFFFF")],
    ),
    "horloge": dict(
        fond=fond("#2E2D31", "#0F0F12"),
        dessin=[plein(cercle(54, 54, 29), "#FBF8F3")]
        + graduations(12, 54, 54, 21, 23.5, 26.5, 3, "#1F1C18", 2.6, 1.6)
        + [
            trace(trait(54, 54, 43, 47), "#1F1C18", 3.2),
            trace(trait(54, 54, 66, 38), "#1F1C18", 2.4),
            trace(trait(49, 60, 62, 44), "#D9542F", 1.3),
            plein(cercle(54, 54, 2.6), "#D9542F"),
        ],
    ),
    "lecteur": dict(
        fond=fond("#B57AE0", "#5E3A8A"),
        dessin=[
            trace("M45,33 l28,-6 v35", "#FFFFFF", 5),
            trace("M45,33 v36", "#FFFFFF", 5),
            plein(ellipse(38.5, 70, 8.5, 7), "#FFFFFF"),
            plein(ellipse(66.5, 63, 8.5, 7), "#FFFFFF"),
        ],
    ),
    "mail": dict(
        fond=fond("#FCEEE4", "#F1CDB4", False),
        dessin=[
            plein(rr(24, 33, 60, 42, 8), lineaire("#DC6A3E", "#A9442A", 33, 75)),
            trace("M27,37 l27,20 l27,-20", "#FBE0CF", 3.5),
        ],
    ),
    "messages": dict(
        fond=fond("#76A0F0", "#3A5BA8"),
        dessin=[plein("M54,28 c-16.6,0 -30,10.7 -30,24 c0,7.4 4.2,14 10.8,18.4 c-0.6,3.6 -2.4,6.8 -5.4,9.4 c5.6,0.2 10.4,-1.6 13.8,-4.4 "
                      "c3.4,0.9 7,1.4 10.8,1.4 c16.6,0 30,-10.7 30,-24 S70.6,28 54,28 z", "#FFFFFF")],
    ),
    "meteo": dict(
        fond=fond("#6DBBF5", "#2C7BD6"),
        dessin=[
            plein(cercle(44, 44, 15), radial(41, 39.5, 21, (0, "#FFF1A6"), (1, "#FFC233"))),
            plein("M42,80 h30 a12,12 0 0 0 1.6,-23.9 A16,16 0 0 0 43,54.5 A13,13 0 0 0 42,80 z", "#FFFFFF"),
            plein("M42,80 h30 a12,12 0 0 0 11.6,-9 H32 A13,13 0 0 0 42,80 z", "#DCE9F7"),
        ],
    ),
    "radio": dict(
        fond=fond("#E0A070", "#9A5A32"),
        dessin=[
            trace("M68,42 L84,22", "#FFFFFF", 3.5),
            plein(rr(22, 40, 64, 42, 11), "#FFFFFF"),
            plein(cercle(40, 61, 12), "#9A5A32"),
            plein(cercle(40, 61, 4), "#FFFFFF"),
            trace("M40,61 L47,53", "#FFFFFF", 2.5),
        ] + [plein(rr(58, 50 + r * 6, 22, 3, 1.5), "#C98A62") for r in range(4)],
    ),
    "notes": dict(
        fond=fond("#FFFFFF", "#F3EEE7", False) + [plein("M-27,-27 h162 v61 h-162 z", "#F4B940")],
        dessin=[trace(f"M20,{y} H88", "#DCD5CB", 2) for y in (48, 59, 70, 81)]
        + [plein(cercle(x, 34, 1.6), "#C9A44B") for x in range(4, 108, 10)],
    ),
    "photos": dict(
        fond=fond("#FFE9D2", "#F7C8A0", False) + [
            plein("M-27,74 C0,62 30,60 54,66 S100,72 135,58 V135 H-27 z", "#E2774A"),
            plein("M-27,90 C5,80 35,78 60,84 S105,88 135,80 V135 H-27 z", "#A8442A"),
        ],
        dessin=[plein(cercle(70, 40, 13), "#F59A3C")],
    ),
    "reglages": dict(
        fond=fond("#BDB5AB", "#7E766C"),
        dessin=[tourne(i * 45, plein(rr(49, 24, 10, 14, 3), "#FFFFFF")) for i in range(8)]
        + [plein(cercle(54, 54, 21), "#FFFFFF"), plein(cercle(54, 54, 9), "#9C948A")],
    ),
    "sugu": dict(
        fond=fond("#E98050", "#A9432A"),
        dessin=[
            trace("M42,40 v-3 a12,12 0 0 1 24,0 v3", "#FFFFFF", 4.5),
            plein("M30,42 h48 l-3.5,33 a6,6 0 0 1 -6,5.4 H39.5 a6,6 0 0 1 -6,-5.4 z", "#FFFFFF"),
            plein(cercle(54, 61, 7), "#E98050"),
        ],
    ),
    "telephone": dict(
        fond=fond("#5FCB8C", "#2A7F58"),
        dessin=[plein("M41,29 c-5,0 -10,5 -10,11 c0,22 17,39 39,39 c6,0 11,-5 11,-10 v-6 c0,-2.3 -1.6,-4.2 -3.8,-4.7 l-9,-2 "
                      "c-1.8,-0.4 -3.6,0.2 -4.8,1.6 l-3,3.6 c-7.6,-3.8 -13.8,-10 -17.6,-17.6 l3.6,-3 c1.4,-1.2 2,-3 1.6,-4.8 "
                      "l-2,-9 c-0.5,-2.2 -2.4,-3.8 -4.7,-3.8 z", "#FFFFFF")],
    ),
    "clavier": dict(
        fond=fond("#8C7B6B", "#4E4238"),
        dessin=[plein(rr(22, 34, 64, 42, 9), "#FFFFFF")]
        + [plein(rr(29 + c * 10.4, 41 + r * 9.5, 7, 6, 1.8), "#7A6A5C") for r in range(2) for c in range(5)]
        + [plein(rr(39, 62, 30, 6, 2), "#7A6A5C")],
    ),
    # Le menu de l'opérateur sur la SIM (com.android.stk) : l'Accueil le montre sous ce nom et cette icône.
    "services_sim": dict(
        fond=fond("#F5C451", "#D4901F"),
        dessin=[
            plein("M38,26 h24 l12,12 v38 a6,6 0 0 1 -6,6 H38 a6,6 0 0 1 -6,-6 V32 a6,6 0 0 1 6,-6 z", "#FFFFFF"),
            plein(rr(41, 48, 26, 22, 4), "#E2A62B"),
            trace("M41,59 h26 M54,48 v22", "#FFFFFF", 1.6),
        ],
    ),
}


# ——— Écriture ———

def couleur(c):
    return c


def remplissage(attr, valeur, retrait):
    if isinstance(valeur, str):
        return f' android:{attr}="{couleur(valeur)}"', ""
    g = valeur
    if g[0] == "lin":
        _, a, b, x1, y1, x2, y2 = g
        corps = (f'{retrait}<aapt:attr name="android:{attr}">\n'
                 f'{retrait}    <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}" '
                 f'android:startColor="{a}" android:endColor="{b}" />\n'
                 f'{retrait}</aapt:attr>\n')
    else:
        _, cx, cy, r, arrets = g
        items = "".join(f'{retrait}        <item android:offset="{o}" android:color="{c}" />\n' for o, c in arrets)
        corps = (f'{retrait}<aapt:attr name="android:{attr}">\n'
                 f'{retrait}    <gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}">\n'
                 f'{items}{retrait}    </gradient>\n{retrait}</aapt:attr>\n')
    return "", corps


def element(e, retrait):
    if e[0] == "groupe":
        _, angle, enfants = e
        dedans = "".join(element(x, retrait + "    ") for x in enfants)
        return f'{retrait}<group android:rotation="{angle}" android:pivotX="54" android:pivotY="54">\n{dedans}{retrait}</group>\n'
    if e[0] == "plein":
        _, d, c, alpha = e
        attrs, corps = remplissage("fillColor", c, retrait + "    ")
        a = f' android:fillAlpha="{alpha}"' if alpha is not None else ""
        if corps:
            return f'{retrait}<path android:pathData="{d}"{a}>\n{corps}{retrait}</path>\n'
        return f'{retrait}<path android:pathData="{d}"{attrs}{a} />\n'
    _, d, c, ep, alpha = e
    a = f' android:strokeAlpha="{alpha}"' if alpha is not None else ""
    return (f'{retrait}<path android:pathData="{d}" android:strokeColor="{c}" android:strokeWidth="{ep}"{a} '
            f'android:strokeLineCap="round" android:strokeLineJoin="round" />\n')


def vecteur(elements, commentaire):
    corps = "".join(element(e, "        ") for e in elements)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {commentaire} Fabriqué par mobile/outils/icones.py : retoucher là-bas. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            '    <group android:scaleX="0.6667" android:scaleY="0.6667" android:translateX="18" android:translateY="18">\n'
            f'{corps}    </group>\n</vector>\n')


ADAPTATIVE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/{prefixe}_fond" />
    <foreground android:drawable="@drawable/{prefixe}_dessin" />
</adaptive-icon>
"""

# Le module, le nom de son icône (mipmap), et le nom de l'icône dessinée.
MODULES = {
    "agenda": ("ic_appli", "agenda"), "appareil": ("ic_appli", "appareil"), "calculatrice": ("ic_appli", "calculatrice"),
    "contacts": ("ic_contacts", "contacts"), "dictaphone": ("ic_appli", "dictaphone"), "fichiers": ("ic_appli", "fichiers"),
    "griot": ("ic_appli", "griot"), "horloge": ("ic_horloge", "horloge"), "lecteur": ("ic_appli", "lecteur"),
    "mail": ("ic_appli", "mail"), "messages": ("ic_messages", "messages"), "meteo": ("ic_appli", "meteo"),
    "notes": ("ic_appli", "notes"), "photos": ("ic_appli", "photos"), "reglages": ("ic_reglages", "reglages"),
    "sugu": ("ic_appli", "sugu"), "telephone": ("ic_telephone", "telephone"), "clavier": ("ic_appli", "clavier"),
    "radio": ("ic_appli", "radio"),
}


def ecrire(chemin, texte):
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w") as f:
        f.write(texte)


def main():
    for module, (mipmap, nom) in MODULES.items():
        res = os.path.join(MOBILE, module, "src", "main", "res")
        i = ICONES[nom]
        ecrire(os.path.join(res, "drawable", "ic_appli_fond.xml"), vecteur(i["fond"], f"Le fond de l'icône ({nom})."))
        ecrire(os.path.join(res, "drawable", "ic_appli_dessin.xml"), vecteur(i["dessin"], f"Le dessin de l'icône ({nom})."))
        ecrire(os.path.join(res, "mipmap-anydpi-v26", f"{mipmap}.xml"), ADAPTATIVE.format(prefixe="ic_appli"))
    # Les icônes que l'Accueil pose sur des applis d'Android.
    res = os.path.join(MOBILE, "accueil", "src", "main", "res")
    for nom in ("services_sim", "agenda"):
        i = ICONES[nom]
        ecrire(os.path.join(res, "drawable", f"icone_{nom}_fond.xml"), vecteur(i["fond"], f"Le fond de l'icône ({nom})."))
        ecrire(os.path.join(res, "drawable", f"icone_{nom}_dessin.xml"), vecteur(i["dessin"] if nom != "agenda" else [], f"Le dessin de l'icône ({nom})."))
    print("Icônes écrites :", ", ".join(MODULES))


if __name__ == "__main__":
    main()
