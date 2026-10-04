#!/usr/bin/env python3
"""Style Kvantum de Sama (menus, boutons, cases, barres de défilement des applications Qt/KDE).

Part du thème sobre KvSimplicity (source/, thème libre de Tsu Jan, GPL) :
- couleurs passées dans la palette Sama : bleus → latérite, gris → gris chauds (clair) ou bleu nuit (sombre) ;
- menus redessinés comme les menus de la Natte : coins arrondis 10 px, fond de fenêtre, survol en pastille douce.

Écrit iso/config/includes.chroot/usr/share/Kvantum/Sama et SamaSombre.
"""
import colorsys
import os
import re
import xml.etree.ElementTree as ET

ICI = os.path.dirname(os.path.abspath(__file__))
SORTIE = os.path.join(ICI, "..", "..", "iso", "config", "includes.chroot", "usr", "share", "Kvantum")

ESPACES = {
    "": "http://www.w3.org/2000/svg",
    "xlink": "http://www.w3.org/1999/xlink",
    "inkscape": "http://www.inkscape.org/namespaces/inkscape",
    "sodipodi": "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd",
    "svg": "http://www.w3.org/2000/svg",
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "cc": "http://creativecommons.org/ns#",
    "dc": "http://purl.org/dc/elements/1.1/",
}
for prefixe, uri in ESPACES.items():
    if prefixe != "svg":
        ET.register_namespace(prefixe, uri)

VARIANTES = {
    "Sama": {
        "source": "KvSimplicity",
        "sombre": False,
        "menu": "#FBF9F6",           # fond des menus (= fond des fenêtres)
        "bord": "#E3DDD3",
        "survol": ("#1F1C18", 0.07),  # pastille sous l'élément survolé
        "texte": "#1F1C18",
        "couleurs": {
            "window.color": "#FBF9F6", "base.color": "#FFFFFF", "alt.base.color": "#F5F1EB",
            "button.color": "#FFFFFF", "light.color": "#FFFFFF", "mid.light.color": "#EFE9E1",
            "dark.color": "#BDB3A5", "mid.color": "#DDD5C9", "highlight.color": "#B5532F",
            "inactive.highlight.color": "#D8CFC3", "text.color": "#1F1C18", "window.text.color": "#1F1C18",
            "button.text.color": "#1F1C18", "disabled.text.color": "#1F1C1873", "tooltip.text.color": "#FBF9F6",
            "highlight.text.color": "#FFFFFF", "link.color": "#93401F", "link.visited.color": "#7A3A63",
            "progress.indicator.text.color": "#FFFFFF",
        },
    },
    "SamaSombre": {
        "source": "KvSimplicityDark",
        "sombre": True,
        "menu": "#2B3044",
        "bord": "#3A4057",
        "survol": ("#FFFFFF", 0.08),
        "texte": "#F1EBE1",
        "couleurs": {
            "window.color": "#1E2233", "base.color": "#232839", "alt.base.color": "#272C3E",
            "button.color": "#2B3044", "light.color": "#4A5068", "mid.light.color": "#3A4057",
            "dark.color": "#14172A", "mid.color": "#2E3349", "highlight.color": "#C4673F",
            "inactive.highlight.color": "#4A5068", "tooltip.base.color": "#14172A", "text.color": "#F1EBE1",
            "window.text.color": "#F1EBE1", "button.text.color": "#F1EBE1", "disabled.text.color": "#8C867A",
            "tooltip.text.color": "#F1EBE1", "highlight.text.color": "#FFFFFF", "link.color": "#F0B392",
            "link.visited.color": "#E0A3C8",
        },
    },
}


def teinter(hexa, sombre):
    """Passe une couleur du thème d'origine dans la palette Sama."""
    r, g, b = (int(hexa[i:i + 2], 16) / 255 for i in (1, 3, 5))
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    if s < 0.12:                                   # gris → gris chaud ou bleu nuit
        v = l * 255
        if sombre:
            r2, g2, b2 = v - 6, v - 2, v + 12
        else:
            r2, g2, b2 = v + 3, v - 1, v - 7
    else:                                          # couleur d'accent (bleus, violets) → latérite
        r2, g2, b2 = (c * 255 for c in colorsys.hls_to_rgb(16 / 360, min(max(l, 0.3), 0.7), 0.6))
    return "#%02x%02x%02x" % tuple(max(0, min(255, round(c))) for c in (r2, g2, b2))


def recolorer(texte, sombre):
    return re.sub(r"#[0-9a-fA-F]{6}\b", lambda m: teinter(m.group(0), sombre), texte)


def cadre(nom, x, y, f, largeur, couleur, opacite):
    """Élément « cadre » Kvantum (centre, 4 bords, 4 coins) : rectangle arrondi de rayon f."""
    style = "fill:%s;fill-opacity:%s;stroke:none" % (couleur, opacite)
    vide = "fill:none;stroke:none"
    w = largeur
    morceaux = {
        nom: "M%d,%d h%d v%d h-%d z" % (x + f, y + f, w, w, w),
        nom + "-top": "M%d,%d h%d v%d h-%d z" % (x + f, y, w, f, w),
        nom + "-bottom": "M%d,%d h%d v%d h-%d z" % (x + f, y + f + w, w, f, w),
        nom + "-left": "M%d,%d h%d v%d h-%d z" % (x, y + f, f, w, f),
        nom + "-right": "M%d,%d h%d v%d h-%d z" % (x + f + w, y + f, f, w, f),
        nom + "-topleft": "M%d,%d A%d,%d 0 0 1 %d,%d V%d z" % (x, y + f, f, f, x + f, y, y + f),
        nom + "-topright": "M%d,%d A%d,%d 0 0 1 %d,%d H%d z" % (x + f + w, y, f, f, x + 2 * f + w, y + f, x + f + w),
        nom + "-bottomleft": "M%d,%d A%d,%d 0 0 0 %d,%d V%d z" % (x, y + f + w, f, f, x + f, y + 2 * f + w, y + f + w),
        nom + "-bottomright": "M%d,%d A%d,%d 0 0 1 %d,%d V%d z" % (x + 2 * f + w, y + f + w, f, f, x + f + w, y + 2 * f + w, y + f + w),
    }
    boites = {
        nom: (x + f, y + f, w, w), nom + "-top": (x + f, y, w, f), nom + "-bottom": (x + f, y + f + w, w, f),
        nom + "-left": (x, y + f, f, w), nom + "-right": (x + f + w, y + f, f, w),
        nom + "-topleft": (x, y, f, f), nom + "-topright": (x + f + w, y, f, f),
        nom + "-bottomleft": (x, y + f + w, f, f), nom + "-bottomright": (x + f + w, y + f + w, f, f),
    }
    groupes = []
    for ident, d in morceaux.items():
        g = ET.Element("{%s}g" % ESPACES[""], {"id": ident})
        bx, by, bw, bh = boites[ident]
        # Rectangle invisible : fixe la boîte de l'élément (Kvantum étire chaque morceau selon sa boîte)
        ET.SubElement(g, "{%s}rect" % ESPACES[""], {"x": str(bx), "y": str(by), "width": str(bw), "height": str(bh), "style": vide})
        ET.SubElement(g, "{%s}path" % ESPACES[""], {"d": d, "style": style})
        groupes.append(g)
    return groupes


def ombre(racine, x, y, d, l, sombre):
    """Ombre douce des menus (Kvantum la dessine dans une marge de d px autour du menu)."""
    ns = ESPACES[""]
    defs = racine.find("{%s}defs" % ns)
    force = 0.32 if sombre else 0.13
    def degrade(ident, x1, y1, x2, y2):
        g = ET.SubElement(defs, "{%s}linearGradient" % ns, {"id": ident, "gradientUnits": "userSpaceOnUse",
                          "x1": str(x1), "y1": str(y1), "x2": str(x2), "y2": str(y2)})
        ET.SubElement(g, "{%s}stop" % ns, {"offset": "0", "style": "stop-color:#000000;stop-opacity:%s" % force})
        ET.SubElement(g, "{%s}stop" % ns, {"offset": "1", "style": "stop-color:#000000;stop-opacity:0"})
    def radial(ident, cx, cy):
        g = ET.SubElement(defs, "{%s}radialGradient" % ns, {"id": ident, "gradientUnits": "userSpaceOnUse",
                          "cx": str(cx), "cy": str(cy), "r": str(d), "fx": str(cx), "fy": str(cy)})
        ET.SubElement(g, "{%s}stop" % ns, {"offset": "0", "style": "stop-color:#000000;stop-opacity:%s" % force})
        ET.SubElement(g, "{%s}stop" % ns, {"offset": "1", "style": "stop-color:#000000;stop-opacity:0"})
    a, b = x + d, y + d            # coin intérieur haut-gauche (début du menu)
    c, e = a + l, b + l            # coin intérieur bas-droit
    morceaux = {
        "top": ((a, y, l, d), ("lin", a, b, a, y)),
        "bottom": ((a, e, l, d), ("lin", a, e, a, e + d)),
        "left": ((x, b, d, l), ("lin", a, b, x, b)),
        "right": ((c, b, d, l), ("lin", c, b, c + d, b)),
        "topleft": ((x, y, d, d), ("rad", a, b)),
        "topright": ((c, y, d, d), ("rad", c, b)),
        "bottomleft": ((x, e, d, d), ("rad", a, e)),
        "bottomright": ((c, e, d, d), ("rad", c, e)),
    }
    calque = ET.SubElement(racine, "{%s}g" % ns, {"id": "sama-ombres"})
    for nom, ((bx, by, bw, bh), dg) in morceaux.items():
        ident = "sama-ombre-" + nom
        if dg[0] == "lin":
            degrade(ident, *dg[1:])
        else:
            radial(ident, *dg[1:])
        g = ET.SubElement(calque, "{%s}g" % ns, {"id": "menu-shadow-" + nom})
        ET.SubElement(g, "{%s}rect" % ns, {"x": str(bx), "y": str(by), "width": str(bw), "height": str(bh),
                                            "style": "fill:url(#%s);stroke:none" % ident})


def retirer(racine, motif):
    parents = {enfant: parent for parent in racine.iter() for enfant in parent}
    for el in list(racine.iter()):
        ident = el.get("id", "")
        if re.fullmatch(motif, ident) and el in parents:
            parents[el].remove(el)


def greffer_menus(racine):
    """Le thème sombre d'origine n'a pas de formes de menu : on reprend celles du thème clair
    (avec les dégradés qu'elles utilisent, renommés pour éviter les collisions)."""
    ns = ESPACES[""]
    clair = ET.fromstring(open(os.path.join(ICI, "source", "KvSimplicity", "KvSimplicity.svg")).read())
    defs_clair = {el.get("id"): el for el in clair.find("{%s}defs" % ns)}
    defs = racine.find("{%s}defs" % ns)
    copies = set()

    def references(el):
        refs = set()
        for x in el.iter():
            for valeur in x.attrib.values():
                refs.update(re.findall(r"url\(#([^)]+)\)", valeur))
            href = x.get("{%s}href" % ESPACES["xlink"], "")
            if href.startswith("#"):
                refs.add(href[1:])
        return refs

    def renommer(el):
        for x in el.iter():
            for cle, valeur in list(x.attrib.items()):
                x.set(cle, re.sub(r"url\(#([^)]+)\)", r"url(#sama-\1)", valeur))
            href = x.get("{%s}href" % ESPACES["xlink"], "")
            if href.startswith("#"):
                x.set("{%s}href" % ESPACES["xlink"], "#sama-" + href[1:])

    def copier_def(ident):
        if ident in copies or ident not in defs_clair:
            return
        copies.add(ident)
        el = ET.fromstring(ET.tostring(defs_clair[ident]))
        for ref in references(el):
            copier_def(ref)
        renommer(el)
        el.set("id", "sama-" + ident)
        defs.append(el)

    calque = ET.SubElement(racine, "{%s}g" % ns, {"id": "sama-menus-greffes"})
    for el in list(clair.iter()):
        if re.fullmatch(r"menu-(normal|shadow)(-.*)?", el.get("id", "")):
            copie = ET.fromstring(ET.tostring(el))
            for ref in references(copie):
                copier_def(ref)
            renommer(copie)
            calque.append(copie)


def generer(nom, v):
    dossier_source = os.path.join(ICI, "source", v["source"])
    racine = ET.fromstring(open(os.path.join(dossier_source, v["source"] + ".svg")).read())
    if v["sombre"]:
        greffer_menus(racine)
    racine = ET.fromstring(recolorer(ET.tostring(racine, encoding="unicode"), v["sombre"]))
    # Menus : on garde les formes du thème d'origine (coins arrondis, ombre), passées à la couleur des
    # fenêtres Sama et rendues opaques (sinon un liseré plus sombre apparaît autour du menu)
    for el in racine.iter():
        if re.fullmatch(r"menu-(normal|shadow)(-.*)?", el.get("id", "")):
            for morceau in el.iter():
                style = morceau.get("style", "")
                if "opacity:.94" in style or "fill:url(" in style and el.get("id", "").startswith("menu-normal") \
                        or el.get("id", "").startswith("menu-shadow") and re.search(r"fill:#[0-9a-fA-F]{6}", style):
                    style = re.sub(r"(?<![-\w])opacity:[.0-9]+", "opacity:1", style)
                    style = re.sub(r"fill:(#[0-9a-fA-F]{6}|url\(#[^)]*\))", "fill:" + v["menu"], style)
                elif el.get("id", "").startswith("menu-normal") and "color:#000" in style:
                    style = re.sub(r"fill:#[0-9a-fA-F]{6}", "fill:" + v["bord"], style)   # liseré fin du menu
                morceau.set("style", style)
    retirer(racine, r"menuitem-(normal|pressed|toggled|focused)(-.*)?")
    calque = ET.SubElement(racine, "{%s}g" % ESPACES[""], {"id": "sama-menus"})
    couleur, opacite = v["survol"]
    for etat, y in (("normal", 100), ("pressed", 200), ("toggled", 300)):
        for g in cadre("menuitem-" + etat, 2000, y, 6, 40, couleur, opacite):
            calque.append(g)

    conf = open(os.path.join(dossier_source, v["source"] + ".kvconfig")).read()
    conf = recolorer(conf, v["sombre"])
    conf = re.sub(r"(?m)^author=.*$", "author=Sama OS (d'après KvSimplicity de Tsu Jan)", conf)
    conf = re.sub(r"(?m)^comment=.*$", "comment=Style des applications de Sama OS", conf)
    conf = re.sub(r"(?m)^spread_menuitems=.*$", "spread_menuitems=false\nmenu_separator_height=9", conf)
    conf = re.sub(r"(?m)^group_toolbar_buttons=.*$", "group_toolbar_buttons=false", conf)   # barres d'outils plates
    noir, blanc = ("white", "black") if v["sombre"] else ("black", "white")
    conf = re.sub(r"(?m)=%s$" % noir, "=" + v["texte"], conf)
    # Couleurs générales
    bloc = "[GeneralColors]\n" + "".join("%s=%s\n" % kv for kv in v["couleurs"].items())
    conf = re.sub(r"\[GeneralColors\]\n(?:[^\[\n].*\n|\n)*", bloc + "\n", conf)

    # Menus : marge intérieure de 6 px autour des éléments ; élément survolé dans la couleur du texte
    def section(nom_section, remplacements):
        nonlocal conf
        m = re.search(r"(?ms)^\[%s\]\n(.*?)(?=^\[)" % re.escape(nom_section), conf)
        corps = m.group(1)
        for cle, valeur in remplacements.items():
            if re.search(r"(?m)^%s=" % re.escape(cle), corps):
                corps = re.sub(r"(?m)^%s=.*$" % re.escape(cle), "%s=%s" % (cle, valeur), corps)
            else:
                corps += "%s=%s\n" % (cle, valeur)
        conf = conf[:m.start(1)] + corps + conf[m.end(1):]

    section("Menu", {"frame.top": 6, "frame.bottom": 6, "frame.left": 6, "frame.right": 6})
    section("MenuItem", {"frame.top": 6, "frame.bottom": 6, "frame.left": 6, "frame.right": 6,
                         "text.normal.color": v["texte"], "text.focus.color": v["texte"],
                         "text.margin.top": 2, "text.margin.bottom": 2, "text.margin.left": 6, "text.margin.right": 6})

    dossier = os.path.join(SORTIE, nom)
    os.makedirs(dossier, exist_ok=True)
    ET.ElementTree(racine).write(os.path.join(dossier, nom + ".svg"), encoding="unicode", xml_declaration=True)
    open(os.path.join(dossier, nom + ".kvconfig"), "w").write(conf)
    print("écrit", dossier)


if __name__ == "__main__":
    for nom, v in VARIANTES.items():
        generer(nom, v)
