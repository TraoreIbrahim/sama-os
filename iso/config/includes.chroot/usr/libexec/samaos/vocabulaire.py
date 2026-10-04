#!/usr/bin/env python3
"""Remplace, dans les traductions françaises de KDE, les mots qui ne sont pas ceux de Sama
(« activités » → « Espaces »…). Lancé à la construction de l'ISO (et par le mode direct) :
  vocabulaire.py [dossier des traductions]      (par défaut /usr/share/locale/fr/LC_MESSAGES)
Les fichiers .mo sont réécrits sur place, en gardant l'ordre et toutes les autres traductions."""
import os
import struct
import sys

# domaine → { texte anglais d'origine : traduction Sama }
VOCABULAIRE = {
    "kwin": {
        "Show in &Activities": "&Espaces",
        "&All Activities": "Tous les &Espaces",
        "Activity": "Espace",
        "&More Actions": "&Plus d'actions",
    },
}


def lire(chemin):
    donnees = open(chemin, "rb").read()
    magie = struct.unpack("<I", donnees[:4])[0]
    ordre = "<" if magie == 0x950412DE else ">"
    _, n, debut_o, debut_t = struct.unpack(ordre + "4I", donnees[4:20])
    entrees = []
    for i in range(n):
        lo, po = struct.unpack(ordre + "2I", donnees[debut_o + 8 * i:debut_o + 8 * i + 8])
        lt, pt = struct.unpack(ordre + "2I", donnees[debut_t + 8 * i:debut_t + 8 * i + 8])
        entrees.append([donnees[po:po + lo], donnees[pt:pt + lt]])
    return entrees


def ecrire(chemin, entrees):
    n = len(entrees)
    debut_o, debut_t = 28, 28 + 8 * n
    position = 28 + 16 * n
    table_o, table_t, corps = b"", b"", b""
    for texte, _ in entrees:
        table_o += struct.pack("<2I", len(texte), position + len(corps))
        corps += texte + b"\0"
    for _, traduction in entrees:
        table_t += struct.pack("<2I", len(traduction), position + len(corps))
        corps += traduction + b"\0"
    entete = struct.pack("<7I", 0x950412DE, 0, n, debut_o, debut_t, 0, position)
    temporaire = chemin + ".sama"
    with open(temporaire, "wb") as f:
        f.write(entete + table_o + table_t + corps)
    os.replace(temporaire, chemin)


def main():
    dossier = sys.argv[1] if len(sys.argv) > 1 else "/usr/share/locale/fr/LC_MESSAGES"
    for domaine, mots in VOCABULAIRE.items():
        chemin = os.path.join(dossier, domaine + ".mo")
        if not os.path.exists(chemin):
            continue
        entrees = lire(chemin)
        changes = 0
        for entree in entrees:
            texte = entree[0].split(b"\0")[0].decode("utf-8", "replace")
            if texte in mots:
                entree[1] = mots[texte].encode("utf-8")
                changes += 1
        ecrire(chemin, entrees)
        print(f"{domaine} : {changes} expression(s) remplacée(s)")


if __name__ == "__main__":
    main()
