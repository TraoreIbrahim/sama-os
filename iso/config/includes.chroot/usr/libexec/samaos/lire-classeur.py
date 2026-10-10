#!/usr/bin/python3
"""Lit un classeur pour Sama Docs (le bloc « Tableau de Sheet ») dans un LibreOffice à part, invisible, avec son propre
profil : le moteur de Sama Docs, ouvert sur un document texte, ne sait pas ouvrir un classeur à côté (il s'arrête). Le
classeur est ouvert caché, en lecture seule, sans ses macros ni ses liens (il ne contacte personne).

  lire-classeur.py liste FICHIER        JSON : [{nom, lignes, colonnes: [...], feuille?}] (ses tableaux ; sans tableau,
                                        ses feuilles)
  lire-classeur.py lire FICHIER NOM     JSON : {lignes: [[texte…]…], nombres: ["0101"…], brut: [[valeur…]…], totaux,
                                        titre} — les cases telles qu'on les voit (« 25 000 F ») et leurs valeurs (25000),
                                        lignes vides et masquées par un filtre en moins ; titre : le texte au-dessus du
                                        tableau dans la feuille (« Membres de la coopérative »)
En cas d'échec : {"erreur": "…"}.
"""
import json
import os
import subprocess
import sys
import time

import uno
from com.sun.star.beans import PropertyValue
from com.sun.star.connection import NoConnectException

SOFFICE = "/usr/lib/libreoffice/program/soffice"
MAX_LIGNES, MAX_COLONNES = 400, 15


def propriete(nom, valeur):
    p = PropertyValue()
    p.Name, p.Value = nom, valeur
    return p


def demarrer():
    """Un LibreOffice invisible, à nous (profil dans le dossier de session, tube à notre nom)."""
    profil = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "samaos-lecture-classeur")
    tube = "samaos_lecture_%d_%d" % (os.getuid(), os.getpid())
    proc = subprocess.Popen([SOFFICE, "--headless", "--invisible", "--nologo", "--norestore", "--nodefault", "--nolockcheck",
                             "-env:UserInstallation=file://" + profil, "--accept=pipe,name=%s;urp;" % tube],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    local = uno.getComponentContext()
    resolveur = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
    for _ in range(120):
        try:
            ctx = resolveur.resolve("uno:pipe,name=%s;urp;StarOffice.ComponentContext" % tube)
            return proc, ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
        except NoConnectException:
            if proc.poll() is not None:
                break
            time.sleep(0.25)
    proc.kill()
    raise RuntimeError("LibreOffice n'a pas démarré")


def ouvrir(bureau, chemin):
    args = (propriete("Hidden", True), propriete("ReadOnly", True),
            propriete("MacroExecutionMode", 0),       # NEVER_EXECUTE
            propriete("UpdateDocMode", 0))            # NO_UPDATE : ni liens ni sites
    doc = bureau.loadComponentFromURL(uno.systemPathToFileUrl(os.path.abspath(chemin)), "_blank", 0, args)
    if doc is None or not doc.supportsService("com.sun.star.sheet.SpreadsheetDocument"):
        if doc is not None:
            doc.close(True)
        raise ValueError("Ce fichier n'est pas un classeur")
    return doc


def vides(f, c1, l1, c2, l2):
    """Les rangs des lignes entièrement vides (un modèle prépare des lignes à remplir)."""
    if l2 < l1:
        return set()
    donnees = f.getCellRangeByPosition(c1, l1, c2, l2).getDataArray()
    return {l1 + i for i, ligne in enumerate(donnees) if all(v == "" for v in ligne)}


def remplies(f, c1, l1, c2, l2):
    return max(0, l2 - l1 + 1 - len(vides(f, c1, l1, c2, l2)))


def tableaux(doc):
    """Les tableaux (plages de base de données de Sama Sheet) ; sans tableau, les feuilles remplies."""
    res = []
    plages = doc.DatabaseRanges
    for i in range(plages.Count):
        p = plages.getByIndex(i)
        if p.Name.startswith("__"):
            continue
        a = p.DataArea
        f = doc.Sheets.getByIndex(a.Sheet)
        res.append({"nom": p.Name, "lignes": remplies(f, a.StartColumn, a.StartRow + 1, a.EndColumn, a.EndRow),
                    "colonnes": [f.getCellByPosition(c, a.StartRow).String for c in range(a.StartColumn, a.EndColumn + 1)]})
    if not res:
        for i in range(doc.Sheets.Count):
            f = doc.Sheets.getByIndex(i)
            cur = f.createCursor()
            cur.gotoEndOfUsedArea(False)
            fin = cur.RangeAddress
            if fin.EndRow > 0 or fin.EndColumn > 0:
                res.append({"nom": f.Name, "feuille": True, "lignes": remplies(f, 0, 1, fin.EndColumn, fin.EndRow),
                            "colonnes": [f.getCellByPosition(c, 0).String for c in range(fin.EndColumn + 1)]})
    return res


def lire(doc, nom):
    totaux = False
    if doc.DatabaseRanges.hasByName(nom):
        p = doc.DatabaseRanges.getByName(nom)
        a = p.DataArea
        f = doc.Sheets.getByIndex(a.Sheet)
        totaux = bool(getattr(p, "TotalsRow", False))
        c1, l1, c2, l2 = a.StartColumn, a.StartRow, a.EndColumn, a.EndRow
    elif doc.Sheets.hasByName(nom):
        f = doc.Sheets.getByName(nom)
        cur = f.createCursor()
        cur.gotoEndOfUsedArea(False)
        c1, l1, c2, l2 = 0, 0, cur.RangeAddress.EndColumn, cur.RangeAddress.EndRow
    else:
        raise ValueError("Le tableau « %s » n'est plus dans ce classeur" % nom)
    if c2 - c1 + 1 > MAX_COLONNES or l2 - l1 + 1 > MAX_LIGNES:
        raise ValueError("Ce tableau est trop grand pour une page (%d lignes, %d colonnes)" % (l2 - l1 + 1, c2 - c1 + 1))
    lignes, nombres, brut = [], [], []
    sans = vides(f, c1, l1 + 1, c2, l2)
    valeurs = f.getCellRangeByPosition(c1, l1, c2, l2).getDataArray()
    for l in range(l1, l2 + 1):
        if l in sans or not f.Rows.getByIndex(l).IsVisible:
            continue
        brut.append(list(valeurs[l - l1]))
        textes, n = [], ""
        for c in range(c1, c2 + 1):
            case = f.getCellByPosition(c, l)
            textes.append(case.String)
            genre = case.Type.value
            n += "1" if genre == "VALUE" or (genre == "FORMULA" and case.FormulaResultType2 == 1) else "0"
        lignes.append(textes)
        nombres.append(n)
    # (le titre : la première case remplie au-dessus du tableau, dans ses trois lignes d'avant)
    titre = ""
    for l in range(l1 - 1, max(-1, l1 - 4), -1):
        t = f.getCellByPosition(c1, l).String.strip()
        if t:
            titre = t
    return {"lignes": lignes, "nombres": nombres, "brut": brut, "totaux": totaux, "titre": titre}


def main(args):
    if len(args) < 2 or args[0] not in ("liste", "lire") or (args[0] == "lire" and len(args) < 3):
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    if not os.path.isfile(args[1]):
        print(json.dumps({"erreur": "Ce fichier n'existe plus"}, ensure_ascii=False))
        return
    proc = bureau = doc = None
    try:
        proc, bureau = demarrer()
        doc = ouvrir(bureau, args[1])
        res = tableaux(doc) if args[0] == "liste" else lire(doc, args[2])
    except ValueError as e:
        res = {"erreur": str(e)}
    except Exception:
        res = {"erreur": "Ce classeur n'a pas pu être lu"}
    finally:
        try:
            if doc is not None:
                doc.close(True)
            if bureau is not None:
                bureau.terminate()
        except Exception:
            pass
        if proc is not None:
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
    print(json.dumps(res, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1:])
