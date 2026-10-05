#!/usr/bin/env python3
"""Data consommée, pour la carte Data du bureau et Réglages › Data et mises à jour.

  data.py etat      JSON : forfait, data utilisée depuis le renouvellement, 7 derniers jours

La mesure vient de vnstat (historique jour par jour, gardé d'un allumage à l'autre). Sans vnstat (ou tant
qu'il n'a rien enregistré), seule la data depuis l'allumage est connue (compteurs des cartes réseau).
Le forfait et son jour de renouvellement sont réglés dans les Réglages (samaosrc, groupe Data).
Toutes les cartes réseau comptent, sauf celles qui doubleraient le compte (VPN, machines virtuelles…).
"""
import configparser
import datetime
import glob
import json
import os
import subprocess
import sys

MOIS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre",
        "novembre", "décembre"]
JOURS = ["lun", "mar", "mer", "jeu", "ven", "sam", "dim"]
IGNOREES = ("lo", "tun", "tap", "wg", "virbr", "vnet", "veth", "docker", "br-", "vboxnet", "zt")
MO = 1048576


def reglages():
    c = configparser.RawConfigParser(strict=False)
    c.optionxform = str
    chemin = os.path.join(os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config")), "samaosrc")
    try:
        c.read(chemin, encoding="utf-8")
    except configparser.Error:
        pass

    def lire(cle, defaut):
        try:
            return int(c.get("Data", cle).strip())
        except (configparser.Error, ValueError):
            return defaut
    forfait = lire("forfaitMo", 1024)
    jour = min(28, max(1, lire("renouvellement", 1)))
    return forfait, jour, c


def periode(aujourdhui, jour):
    """Début de la période de forfait en cours et date du prochain renouvellement."""
    if aujourdhui.day >= jour:
        debut = aujourdhui.replace(day=jour)
    else:
        mois = aujourdhui.month - 1 or 12
        debut = aujourdhui.replace(year=aujourdhui.year - (mois == 12), month=mois, day=jour)
    mois = debut.month % 12 + 1
    fin = debut.replace(year=debut.year + (mois == 1), month=mois)
    return debut, fin


def date_lisible(d):
    return ("1er" if d.day == 1 else str(d.day)) + " " + MOIS[d.month - 1]


def depuis_allumage():
    total = 0
    for f in glob.glob("/sys/class/net/*/statistics/rx_bytes") + glob.glob("/sys/class/net/*/statistics/tx_bytes"):
        if f.split("/")[4].startswith(IGNOREES):
            continue
        try:
            total += int(open(f).read())
        except (OSError, ValueError):
            pass
    return total / MO


def jours_vnstat():
    """{date: octets} d'après vnstat (toutes les cartes réseau retenues), ou None."""
    essai = os.environ.get("SAMA_VNSTAT_JSON")     # (tests)
    try:
        sortie = open(essai).read() if essai else subprocess.run(
            ["vnstat", "--json", "d"], capture_output=True, text=True, timeout=10).stdout
        donnees = json.loads(sortie)
    except (OSError, ValueError, subprocess.SubprocessError):
        return None
    jours = {}
    for interface in donnees.get("interfaces", []):
        if str(interface.get("name", "")).startswith(IGNOREES):
            continue
        for j in interface.get("traffic", {}).get("day", []):
            d = j.get("date", {})
            try:
                cle = datetime.date(d["year"], d["month"], d["day"])
            except (KeyError, TypeError, ValueError):
                continue
            jours[cle] = jours.get(cle, 0) + int(j.get("rx", 0)) + int(j.get("tx", 0))
    return jours or None


def etat():
    forfait, jour, config = reglages()
    aujourdhui = datetime.date.today()
    if os.environ.get("SAMA_AUJOURDHUI"):     # (tests)
        aujourdhui = datetime.date.fromisoformat(os.environ["SAMA_AUJOURDHUI"])
    debut, fin = periode(aujourdhui, jour)
    jours = jours_vnstat()
    resultat = {"forfaitMo": forfait, "renouvellement": jour, "periodeDebut": debut.isoformat(),
                "prochainRenouvellement": date_lisible(fin), "allumageMo": round(depuis_allumage(), 1)}
    if jours:
        semaine = []
        for i in range(6, -1, -1):
            d = aujourdhui - datetime.timedelta(days=i)
            semaine.append({"nom": JOURS[d.weekday()], "mo": round(jours.get(d, 0) / MO, 1), "aujourdhui": i == 0})
        resultat.update(source="vnstat",
                        utiliseMo=round(sum(o for d, o in jours.items() if debut <= d <= aujourdhui) / MO, 1),
                        aujourdhuiMo=semaine[-1]["mo"], semaineMo=round(sum(j["mo"] for j in semaine), 1),
                        jours=semaine)
    else:
        resultat.update(source="allumage", utiliseMo=resultat["allumageMo"], jours=[])
    # Alerte de forfait déjà donnée pendant cette période (80 ou 100), pour ne pas la répéter à chaque allumage
    try:
        deja = config.get("Data", "alertePeriode").strip() == debut.isoformat()
        resultat["alerteSeuil"] = int(config.get("Data", "alerteSeuil")) if deja else 0
    except (configparser.Error, ValueError):
        resultat["alerteSeuil"] = 0
    print(json.dumps(resultat, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "etat":
        etat()
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
