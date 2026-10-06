#!/usr/bin/python3
"""Aide hors ligne de Sama (maquette app-10) : les articles sont des fichiers Markdown rangés par thème, lus sur
l'ordinateur, sans Internet. Une langue = un dossier ; traduire l'aide, c'est traduire ces fichiers (dans le dépôt :
aide/<langue>/, copiés dans /usr/share/samaos/aide à la construction de l'ISO).

  /usr/share/samaos/aide/<langue>/<thème>/_theme.md      titre, resume, picto, fond, encre (ordre : nom du dossier)
  /usr/share/samaos/aide/<langue>/<thème>/<NN-nom>.md    un article : en-tête (titre, resume, mots), puis le texte

  aide.py index [langue]        JSON : langues disponibles, thèmes et leurs articles (texte compris)
"""
import datetime
import glob
import html
import json
import os
import re
import sys

RACINE = os.environ.get("SAMA_AIDE", "/usr/share/samaos/aide")
MOIS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]
NOMS = {"fr": "Français", "dyu": "Julakan", "wo": "Wolof", "sw": "Kiswahili", "en": "English"}


def lire(chemin):
    """En-tête « clé: valeur » entre deux lignes « --- », puis le texte."""
    texte = open(chemin, encoding="utf-8").read()
    entete = {}
    if texte.startswith("---\n"):
        fin = texte.find("\n---\n", 4)
        if fin > 0:
            for ligne in texte[4:fin].splitlines():
                cle, _, valeur = ligne.partition(":")
                if cle.strip():
                    entete[cle.strip()] = valeur.strip()
            texte = texte[fin + 5:]
    return entete, texte.strip()


def en_html(texte):
    """Le Markdown des articles (paragraphes, listes à puces ou numérotées, **gras**, [liens](sama:…)) en HTML pour
    Qt ; « COULEUR_LIEN » est remplacé par la couleur des liens du thème clair ou sombre, à l'affichage."""
    def ligne(s):
        s = html.escape(s, quote=False)
        s = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", s)
        return re.sub(r"\[(.+?)\]\((.+?)\)", r'<a href="\2">\1</a>', s)
    blocs = []
    for bloc in re.split(r"\n\s*\n", texte.strip()):
        lignes = [l.strip() for l in bloc.strip().splitlines() if l.strip()]
        if all(l.startswith("- ") for l in lignes):
            blocs.append("<ul>" + "".join("<li>%s</li>" % ligne(l[2:]) for l in lignes) + "</ul>")
        elif all(re.match(r"\d+\. ", l) for l in lignes):
            blocs.append("<ol>" + "".join("<li>%s</li>" % ligne(l.split(" ", 1)[1]) for l in lignes) + "</ol>")
        else:
            blocs.append("<p>%s</p>" % "<br>".join(ligne(l) for l in lignes))
    return ("<style>a { color: COULEUR_LIEN; text-decoration: none; font-weight: 600; } p { margin-bottom: 12px; } "
            "li { margin-bottom: 6px; } ul, ol { margin-bottom: 12px; }</style>" + "".join(blocs))


def index(langue):
    dossier = os.path.join(RACINE, langue)
    if not os.path.isdir(dossier):
        langue, dossier = "fr", os.path.join(RACINE, "fr")
    themes, recent = [], 0
    for d in sorted(glob.glob(os.path.join(dossier, "*", ""))):
        ident = os.path.basename(os.path.dirname(d))
        theme = ident.split("-", 1)[-1]              # (« 1-premiers-pas » : le chiffre ne sert qu'à l'ordre)
        entete = lire(os.path.join(d, "_theme.md"))[0] if os.path.exists(os.path.join(d, "_theme.md")) else {}
        articles = []
        for f in sorted(glob.glob(os.path.join(d, "*.md"))):
            if os.path.basename(f) == "_theme.md":
                continue
            recent = max(recent, os.path.getmtime(f))
            e, corps = lire(f)
            nom = os.path.basename(f)[:-3]
            articles.append({"id": theme + "/" + nom.split("-", 1)[-1], "titre": e.get("titre", nom), "resume": e.get("resume", ""),
                             "mots": e.get("mots", ""), "corps": corps, "html": en_html(corps),
                             "premier": e.get("premier", "") == "oui"})
        themes.append({"id": theme, "titre": entete.get("titre", ident), "resume": entete.get("resume", ""),
                       "picto": entete.get("picto", ""), "fond": entete.get("fond", "#E4E0DA"),
                       "encre": entete.get("encre", "#665E54"), "articles": articles})
    date = datetime.date.fromtimestamp(recent) if recent else datetime.date.today()
    langues = [{"code": l, "nom": NOMS.get(l, l)} for l in sorted(os.listdir(RACINE)) if os.path.isdir(os.path.join(RACINE, l))] \
        if os.path.isdir(RACINE) else []
    print(json.dumps({"langue": langue, "langues": langues, "themes": themes,
                      "misAJour": "%s %s" % ("1er" if date.day == 1 else date.day, MOIS[date.month - 1])}, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "index":
        index(sys.argv[2] if len(sys.argv) > 2 else "fr")
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
