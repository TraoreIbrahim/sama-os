#!/usr/bin/python3
"""Instantanés du système (Btrfs) : Sama photographie le système avant chaque mise à jour ou installation
d'application, et chaque semaine. Une mise à jour coupée net (coupure de courant) est annulée au démarrage suivant :
le menu de démarrage voit qu'elle n'a pas fini et ramène l'instantané pris juste avant.

Disque préparé par l'installateur : sous-volumes @ (/), @home (/home), @log (/var/log) et @instantanes
(/.instantanes). Chaque instantané : /.instantanes/<AAAAMMJJ-HHMMSS>/systeme (copie de @ en lecture seule) et
info.json. Les documents (/home) ne sont jamais touchés ; au retour en arrière, les comptes, les réseaux Wi-Fi, les
appareils Bluetooth, les imprimantes et le compteur de data sont gardés tels qu'ils sont (voir CONSERVER).

  instantanes.py etat                      JSON : disponible, liste [{id, quand, type, libelle, detail}], libre
  instantanes.py creer TYPE [DETAIL]       manuel | hebdo (administrateur)
  instantanes.py activer | desactiver      instantanés automatiques (avant les mises à jour, chaque semaine)
  instantanes.py supprimer ID
  instantanes.py restaurer-au-demarrage ID le système revient à cet instantané au prochain démarrage
  instantanes.py annuler-restauration
  instantanes.py avant-maj N | apres-maj   autour des mises à jour (la nuit, au redémarrage)
  instantanes.py avant-dpkg | apres-dpkg   crochets d'APT (instantanes-apt), pour Sugu et les installations
  instantanes.py paquets                   crochet d'APT (DPkg::Pre-Install-Pkgs) : nomme l'instantané
  instantanes.py demarrage                 au démarrage : range l'ancien système, nettoie, réécrit le menu
  instantanes.py restaurer ID RAISON       (restauration-init seulement, avant le démarrage du système)
"""
import json
import os
import re
import shutil
import subprocess
import sys
import time

RACINE = os.environ.get("SAMA_RACINE_SYSTEME", "/")        # le système en marche (essais : un autre dossier)
DOSSIER = os.environ.get("SAMA_INSTANTANES", "/.instantanes")
HAUT = os.environ.get("SAMA_HAUT", "/run/samaos/disque")     # où monter le haut du disque Btrfs (sous-volume 5)
SYSTEME, INSTANTANES = "@", "@instantanes"
ETAT = "var/lib/samaos"
MARQUE_MAJ = "maj-en-cours.cfg"          # (lu aussi par le menu de démarrage : syntaxe de GRUB)
MARQUE_RESTAURER = "restaurer.cfg"
NOTE = "restauration.json"               # lue à l'ouverture de session : fenêtre « Sama a été restauré »
DESACTIVES = "etc/samaos/instantanes-desactives"   # (Réglages › Sauvegarde : plus d'instantanés automatiques)

# Combien en garder, par sorte (« maj » : avant les mises à jour et les installations d'applications)
GARDER = {"maj": 5, "hebdo": 4, "manuel": 10, "restauration": 2}
LIBELLES = {"maj": "avant mise à jour", "appli": "avant installation", "hebdo": "Instantané hebdomadaire",
            "manuel": "Manuel", "restauration": "avant restauration"}

# Ce qui appartient aux personnes et non au système : gardé tel quel quand le système revient en arrière
CONSERVER = [
    "etc/passwd", "etc/shadow", "etc/group", "etc/gshadow", "etc/subuid", "etc/subgid",     # comptes
    "var/lib/AccountsService",                                                            # photos, langues
    "etc/NetworkManager/system-connections",                                              # réseaux Wi-Fi
    "var/lib/bluetooth", "etc/cups", "var/lib/vnstat",                                    # appareils, data
    "etc/hostname", "etc/hosts", "etc/machine-info", "etc/localtime", "etc/timezone",
    "etc/default/locale", "etc/default/keyboard",
    "etc/sddm.conf.d/60-sama-connexion-auto.conf", "etc/samaos", "var/lib/samaos",
]

ID_FORME = re.compile(r"^\d{8}-\d{6}$")


def ecrire(chemin, texte):
    """Écrit un fichier d'un coup (fichier temporaire puis renommage), posé sur le disque avant de rendre la main."""
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    temporaire = chemin + ".nouveau"
    with open(temporaire, "w") as f:
        f.write(texte)
        f.flush()
        os.fsync(f.fileno())
    os.replace(temporaire, chemin)
    dossier = os.open(os.path.dirname(chemin), os.O_RDONLY)
    try:
        os.fsync(dossier)
    finally:
        os.close(dossier)


def btrfs(*args):
    subprocess.run(["btrfs"] + list(args), check=True, stdout=subprocess.DEVNULL)


def montage(chemin, champ):
    r = subprocess.run(["findmnt", "-n", "-o", champ, "--target", chemin], capture_output=True, text=True)
    return r.stdout.strip().splitlines()[0] if r.stdout.strip() else ""


def sur_btrfs():
    """Le système tourne sur le sous-volume @ d'un disque Btrfs (pas dans la session d'essai, ni ailleurs)."""
    if os.environ.get("SAMA_RACINE_SYSTEME"):
        return True
    return montage(RACINE, "FSTYPE") == "btrfs" and montage(RACINE, "FSROOT") == "/" + SYSTEME


def dans_un_chroot():
    """Installation en cours (Calamares) ou construction de l'ISO : jamais d'instantané."""
    try:
        return os.stat("/proc/1/root/.") != os.stat("/") if os.path.exists("/proc/1/root") else False
    except OSError:
        return True


def automatiques():
    return not os.path.exists(os.path.join(RACINE, DESACTIVES))


def disponible():
    return sur_btrfs() and os.path.isdir(DOSSIER) and (os.path.ismount(DOSSIER) or bool(os.environ.get("SAMA_INSTANTANES")))


def peripherique():
    """Le disque du système (« /dev/vda2[/@] » → « /dev/vda2 »)."""
    return os.environ.get("SAMA_DISQUE") or re.sub(r"\[.*\]$", "", montage(RACINE, "SOURCE"))


class Haut:
    """Le haut du disque Btrfs (où vivent @, @home, @instantanes…), monté le temps d'une opération."""
    def __enter__(self):
        os.makedirs(HAUT, exist_ok=True)
        self.monte = not os.path.ismount(HAUT)
        if self.monte:
            subprocess.run(["mount", "-o", "subvolid=5", peripherique(), HAUT], check=True)
        return HAUT

    def __exit__(self, *erreur):
        if self.monte:
            subprocess.run(["umount", HAUT], check=False)


# ——— Liste ———

def lire_info(identifiant):
    try:
        with open(os.path.join(DOSSIER, identifiant, "info.json")) as f:
            info = json.load(f)
    except (OSError, ValueError):
        info = {}
    if not info.get("quand"):
        try:
            info["quand"] = int(time.mktime(time.strptime(identifiant, "%Y%m%d-%H%M%S")))
        except ValueError:
            info["quand"] = 0
    info["id"] = identifiant
    info.setdefault("type", "manuel")
    info.setdefault("libelle", LIBELLES.get(info["type"], ""))
    info.setdefault("detail", "")
    return info


def liste():
    """Les instantanés complets, du plus récent au plus ancien."""
    try:
        noms = os.listdir(DOSSIER)
    except OSError:
        return []
    res = [lire_info(n) for n in noms if ID_FORME.match(n) and os.path.isdir(os.path.join(DOSSIER, n, "systeme"))]
    return sorted(res, key=lambda i: i["id"], reverse=True)


def marque(nom):
    """Contenu d'une marque (« set sama_instantane="…" ») : {instantane, proprietaire} ou None."""
    try:
        texte = open(os.path.join(RACINE, ETAT, nom)).read()
    except OSError:
        return None
    return dict(re.findall(r'set sama_(\w+)="([^"]*)"', texte))


def etat():
    libre = -1
    try:
        s = os.statvfs(DOSSIER if os.path.isdir(DOSSIER) else RACINE)
        libre = s.f_bavail * s.f_frsize
    except OSError:
        pass
    restauration = marque(MARQUE_RESTAURER)
    print(json.dumps({"disponible": disponible(), "actif": automatiques(), "liste": liste() if disponible() else [], "libre": libre,
                      "restaurationPrevue": restauration.get("instantane", "") if restauration else ""},
                     ensure_ascii=False))


# ——— Création, nettoyage ———

def version_sama():
    try:
        return dict(re.findall(r'^(\w+)="?([^"\n]*)"?$', open(os.path.join(RACINE, "etc/os-release")).read(), re.M)).get("VERSION_ID", "")
    except OSError:
        return ""


def creer(sorte, detail=""):
    """Instantané du système en marche ; rend son identifiant."""
    if not disponible():
        raise SystemExit("Instantanés indisponibles : le système n'est pas installé sur un disque Btrfs.")
    identifiant = time.strftime("%Y%m%d-%H%M%S")
    while os.path.exists(os.path.join(DOSSIER, identifiant)):
        time.sleep(1)
        identifiant = time.strftime("%Y%m%d-%H%M%S")
    dossier = os.path.join(DOSSIER, identifiant)
    os.makedirs(dossier)
    os.chmod(dossier, 0o755)
    info = {"quand": int(time.time()), "type": sorte, "libelle": LIBELLES.get(sorte, ""), "detail": detail,
            "version": version_sama()}
    ecrire(os.path.join(dossier, "info.json"), json.dumps(info, ensure_ascii=False, indent=1) + "\n")
    btrfs("subvolume", "snapshot", "-r", RACINE, os.path.join(dossier, "systeme"))
    nettoyer(garder=identifiant)
    menu()
    # (l'instantané doit être sur le disque avant que la mise à jour commence)
    subprocess.run(["btrfs", "filesystem", "sync", DOSSIER], check=False, stdout=subprocess.DEVNULL)
    return identifiant


def detruire(chemin):
    """Supprime un sous-volume et ceux qu'il contient (systemd en crée parfois dans /var/lib)."""
    if os.path.isdir(chemin):
        subprocess.run(["btrfs", "subvolume", "delete", "-R", chemin], check=False, stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)
    shutil.rmtree(chemin, ignore_errors=True)


def supprimer(identifiant):
    if not ID_FORME.match(identifiant):
        return 1
    dossier = os.path.join(DOSSIER, identifiant)
    detruire(os.path.join(dossier, "systeme"))
    shutil.rmtree(dossier, ignore_errors=True)
    menu()
    return 0


def nettoyer(garder=""):
    """Garde les plus récents de chaque sorte ; moins encore si le disque est presque plein."""
    proteges = {garder}
    for nom in (MARQUE_MAJ, MARQUE_RESTAURER):
        m = marque(nom)
        if m:
            proteges.add(m.get("instantane", ""))
    try:
        s = os.statvfs(DOSSIER)
        serre = s.f_bavail < s.f_blocks * 0.1
    except OSError:
        serre = False
    comptes = {}
    for i in liste():
        sorte = "maj" if i["type"] == "appli" else i["type"]
        comptes[sorte] = comptes.get(sorte, 0) + 1
        limite = GARDER.get(sorte, 5)
        if serre and sorte != "manuel":
            limite = min(limite, 2)
        if comptes[sorte] > limite and i["id"] not in proteges:
            detruire(os.path.join(DOSSIER, i["id"], "systeme"))
            shutil.rmtree(os.path.join(DOSSIER, i["id"]), ignore_errors=True)


# ——— Menu de démarrage (/.instantanes/grub.cfg, lu par /etc/grub.d/41_samaos_instantanes) ———

MOIS = ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."]


def date_courte(secondes):
    t = time.localtime(secondes)
    return "%d %s %d, %02d:%02d" % (t.tm_mday, MOIS[t.tm_mon - 1], t.tm_year, t.tm_hour, t.tm_min)


def noyau(identifiant):
    """Noyau et initrd de l'instantané (chemins dans l'instantané) ; None s'il n'en a pas."""
    racine = os.path.join(DOSSIER, identifiant, "systeme")
    for lien, sorte in (("vmlinuz", "vmlinuz"), ("boot/vmlinuz", "vmlinuz")):
        p = os.path.join(racine, lien)
        if os.path.islink(p):
            cible = os.path.normpath(os.path.join(os.path.dirname(lien), os.readlink(p))).lstrip("/")
            version = os.path.basename(cible)[len("vmlinuz-"):]
            if os.path.isfile(os.path.join(racine, cible)) and os.path.isfile(os.path.join(racine, "boot/initrd.img-" + version)):
                return "/" + cible, "/boot/initrd.img-" + version
    noyaux = sorted(f for f in os.listdir(os.path.join(racine, "boot")) if f.startswith("vmlinuz-")) if os.path.isdir(os.path.join(racine, "boot")) else []
    for n in reversed(noyaux):
        version = n[len("vmlinuz-"):]
        if os.path.isfile(os.path.join(racine, "boot/initrd.img-" + version)):
            return "/boot/" + n, "/boot/initrd.img-" + version
    return None


def menu():
    if not disponible():
        return
    uuid = os.environ.get("SAMA_UUID") or montage(RACINE, "UUID")
    lignes = [
        "# Instantanés de Sama : écrit par /usr/libexec/samaos/instantanes.py à chaque changement (ne pas modifier).",
        "# Lu par le menu de démarrage (/etc/grub.d/41_samaos_instantanes), où $sama_disque est ce disque.",
        "set sama_raison=menu",
        "export sama_raison",
        "# Retour demandé (Réglages), ou mise à jour coupée net : démarrer directement sur la restauration",
        "if [ -f ($sama_disque)/%s/%s/%s ]; then" % (SYSTEME, ETAT, MARQUE_RESTAURER),
        "\tsource ($sama_disque)/%s/%s/%s" % (SYSTEME, ETAT, MARQUE_RESTAURER),
        "\tset sama_raison=demande",
        "elif [ -f ($sama_disque)/%s/%s/%s ]; then" % (SYSTEME, ETAT, MARQUE_MAJ),
        "\tsource ($sama_disque)/%s/%s/%s" % (SYSTEME, ETAT, MARQUE_MAJ),
        "\tset sama_raison=maj-interrompue",
        "fi",
        "export sama_raison",
        'if [ "$sama_raison" != menu ]; then',
        '\tset default="sama-instantanes>sama-instantane-${sama_instantane}"',
        "\tset timeout=0",
        "\tset timeout_style=hidden",
        "fi",
        'submenu "Revenir à un instantané de Sama" --id sama-instantanes {',
    ]
    for i in liste():
        n = noyau(i["id"])
        if not n:
            continue
        chemin = "/%s/%s/systeme" % (INSTANTANES, i["id"])
        titre = date_courte(i["quand"]) + " · " + (i["libelle"] if i["type"] != "manuel" else "manuel" + (" « %s »" % i["detail"] if i["detail"] else ""))
        titre = titre.replace('"', "'").replace("$", "")
        lignes += [
            '\tmenuentry "%s" --id sama-instantane-%s {' % (titre, i["id"]),
            "\t\tsearch --no-floppy --fs-uuid --set=root %s" % uuid,
            "\t\techo \"Sama revient à l'état du %s…\"" % date_courte(i["quand"]),
            "\t\tlinux %s%s root=UUID=%s ro rootflags=subvol=%s quiet splash init=/usr/libexec/samaos/restauration-init"
            " samaos.instantane=%s samaos.raison=$sama_raison" % (chemin, n[0], uuid, chemin.lstrip("/"), i["id"]),
            "\t\tinitrd %s%s" % (chemin, n[1]),
            "\t}",
        ]
    lignes.append("}")
    ecrire(os.path.join(DOSSIER, "grub.cfg"), "\n".join(lignes) + "\n")


# ——— Autour des mises à jour ———

def poser_marque(nom, identifiant, proprietaire):
    ecrire(os.path.join(RACINE, ETAT, nom),
           "# Posé par /usr/libexec/samaos/instantanes.py ; lu par le menu de démarrage.\n"
           'set sama_instantane="%s"\nset sama_proprietaire="%s"\n' % (identifiant, proprietaire))
    subprocess.run(["btrfs", "filesystem", "sync", RACINE], check=False, stdout=subprocess.DEVNULL)


def oter_marque(nom):
    try:
        os.remove(os.path.join(RACINE, ETAT, nom))
        subprocess.run(["sync", "-f", os.path.join(RACINE, ETAT)], check=False)
    except OSError:
        pass


def avant_maj(nombre, proprietaire="nuit"):
    """Instantané, puis marque « mise à jour en cours » : si elle reste au démarrage, la mise à jour a été coupée."""
    if not disponible() or dans_un_chroot() or not automatiques():
        return
    n = int(nombre) if str(nombre).isdigit() else 0
    detail = "Créé automatiquement avant l'installation de %d mise%s à jour" % (n, "s" if n > 1 else "") if n else \
             "Créé automatiquement avant les mises à jour"
    identifiant = creer("maj", detail)
    poser_marque(MARQUE_MAJ, identifiant, proprietaire)


def apres_maj(proprietaire=None):
    m = marque(MARQUE_MAJ)
    if m and (proprietaire is None or m.get("proprietaire") == proprietaire):
        oter_marque(MARQUE_MAJ)


def avant_dpkg():
    """Installation ou suppression par Sugu, Discover ou apt : instantané (sauf s'il y en a un de moins de 5 minutes)."""
    if not disponible() or dans_un_chroot() or marque(MARQUE_MAJ) or not automatiques():
        return          # (les mises à jour de la nuit ont déjà le leur)
    recents = [i for i in liste() if i["type"] in ("maj", "appli") and time.time() - i["quand"] < 300]
    identifiant = recents[0]["id"] if recents else creer("appli", "Avant des changements d'applications")
    poser_marque(MARQUE_MAJ, identifiant, "dpkg")


def apres_dpkg():
    apres_maj("dpkg")


def paquets():
    """DPkg::Pre-Install-Pkgs (version 2) : « Avant l'installation de gcompris-qt », « Avant la mise à jour de 3 paquets »."""
    m = marque(MARQUE_MAJ)
    if not m or m.get("proprietaire") != "dpkg":
        sys.stdin.read()
        return
    installes, mis_a_jour, supprimes = [], [], []
    corps = False
    for ligne in sys.stdin:
        ligne = ligne.strip()
        if not corps:
            corps = ligne == ""
            continue
        champs = ligne.split()
        if len(champs) != 5:
            continue
        nom, avant, sens, apres, action = champs
        if action == "**REMOVE**":
            supprimes.append(nom)
        elif avant == "-":
            installes.append(nom)
        elif sens == "<":
            mis_a_jour.append(nom)
    def phrase(verbe, noms):
        # (le paquet au nom le plus court est en général l'application, les autres ses morceaux)
        principal, autres = min(noms, key=len), len(noms) - 1
        return "Avant %s de %s%s" % (verbe, principal, "" if not autres else
                                     " et %d autre%s paquet%s" % (autres, "s" if autres > 1 else "", "s" if autres > 1 else ""))
    if installes:
        detail = phrase("l'installation", installes)
    elif supprimes and not mis_a_jour:
        detail = phrase("la suppression", supprimes)
    elif mis_a_jour:
        detail = phrase("la mise à jour", mis_a_jour)
    else:
        return
    chemin = os.path.join(DOSSIER, m["instantane"], "info.json")
    info = lire_info(m["instantane"])
    if info["type"] == "appli" and info["detail"] == "Avant des changements d'applications":
        info["detail"] = detail
        info["libelle"] = LIBELLES["maj"] if mis_a_jour and not installes else LIBELLES["appli"]
        ecrire(chemin, json.dumps({k: v for k, v in info.items() if k != "id"}, ensure_ascii=False, indent=1) + "\n")
        menu()


# ——— Retour en arrière ———

def restaurer_au_demarrage(identifiant):
    """Demandé dans Réglages : le menu de démarrage lancera la restauration (avant que le système ne démarre)."""
    if not ID_FORME.match(identifiant) or not os.path.isdir(os.path.join(DOSSIER, identifiant, "systeme")):
        return 1
    poser_marque(MARQUE_RESTAURER, identifiant, "reglages")
    return 0


def conserver(ancien, nouveau):
    """Recopie dans le système restauré ce qui appartient aux personnes (comptes, Wi-Fi…), tel qu'il était."""
    for chemin in CONSERVER:
        source, cible = os.path.join(ancien, chemin), os.path.join(nouveau, chemin)
        if not os.path.lexists(source):
            continue
        try:
            if os.path.isdir(source) and not os.path.islink(source):
                if os.path.lexists(cible):
                    shutil.rmtree(cible) if os.path.isdir(cible) and not os.path.islink(cible) else os.remove(cible)
                shutil.copytree(source, cible, symlinks=True)
                shutil.copystat(source, cible)
            else:
                os.makedirs(os.path.dirname(cible), exist_ok=True)
                if os.path.lexists(cible):
                    os.remove(cible)
                shutil.copy2(source, cible, follow_symlinks=False)
            st = os.lstat(source)
            os.lchown(cible, st.st_uid, st.st_gid)
            if os.path.isdir(source) and not os.path.islink(source):
                for racine, dossiers, fichiers in os.walk(source):
                    for n in dossiers + fichiers:
                        s = os.lstat(os.path.join(racine, n))
                        os.lchown(os.path.join(cible, os.path.relpath(os.path.join(racine, n), source)), s.st_uid, s.st_gid)
        except OSError as e:
            print("Non conservé : %s (%s)" % (chemin, e), file=sys.stderr)


def restaurer(identifiant, raison):
    """Remplace @ par une copie de l'instantané (échange atomique : jamais de moment sans système), garde ce qui
    appartient aux personnes, laisse une note pour l'ouverture de session. L'ancien système est effacé au
    démarrage suivant. Lancé par restauration-init, avant que le système ne démarre."""
    if not ID_FORME.match(identifiant):
        raise SystemExit("Instantané inconnu : %s" % identifiant)
    with Haut() as haut:
        source = os.path.join(haut, INSTANTANES, identifiant, "systeme")
        actuel, nouveau = os.path.join(haut, SYSTEME), os.path.join(haut, "@nouveau")
        if not os.path.isdir(source) or not os.path.isdir(actuel):
            raise SystemExit("Instantané introuvable : %s" % identifiant)
        detruire(nouveau)            # (reste d'une restauration coupée elle aussi)
        # Retour demandé : l'état présent devient lui-même un instantané, pour pouvoir y revenir
        if raison != "maj-interrompue":
            garde = time.strftime("%Y%m%d-%H%M%S")
            info = {"quand": int(time.time()), "type": "restauration", "libelle": LIBELLES["restauration"],
                    "detail": "État du système avant le retour au " + date_courte(lire_info_haut(haut, identifiant)["quand"])}
            os.makedirs(os.path.join(haut, INSTANTANES, garde), exist_ok=True)
            ecrire(os.path.join(haut, INSTANTANES, garde, "info.json"), json.dumps(info, ensure_ascii=False, indent=1) + "\n")
            btrfs("subvolume", "snapshot", "-r", actuel, os.path.join(haut, INSTANTANES, garde, "systeme"))
        btrfs("subvolume", "snapshot", source, nouveau)
        conserver(actuel, nouveau)
        for nom in (MARQUE_MAJ, MARQUE_RESTAURER):
            try:
                os.remove(os.path.join(nouveau, ETAT, nom))
            except OSError:
                pass
        info = lire_info_haut(haut, identifiant)
        ecrire(os.path.join(nouveau, ETAT, NOTE), json.dumps({
            "quand": int(time.time()), "instantane": identifiant, "raison": raison,
            "date": info["quand"], "libelle": info["libelle"], "detail": info["detail"]}, ensure_ascii=False) + "\n")
        subprocess.run(["btrfs", "filesystem", "sync", haut], check=False, stdout=subprocess.DEVNULL)
        # L'échange : @ devient l'instantané restauré, l'ancien système part sous un autre nom
        subprocess.run(["mv", "--exchange", "--no-target-directory", nouveau, actuel], check=True)
        os.rename(nouveau, os.path.join(haut, "@remplace-" + time.strftime("%Y%m%d-%H%M%S")))
        subprocess.run(["btrfs", "filesystem", "sync", haut], check=False, stdout=subprocess.DEVNULL)


def lire_info_haut(haut, identifiant):
    global DOSSIER
    ancien, DOSSIER = DOSSIER, os.path.join(haut, INSTANTANES)
    try:
        return lire_info(identifiant)
    finally:
        DOSSIER = ancien


def abandonner():
    """La restauration n'a pas abouti : enlever les marques, pour ne pas recommencer à chaque démarrage."""
    with Haut() as haut:
        for nom in (MARQUE_MAJ, MARQUE_RESTAURER):
            try:
                os.remove(os.path.join(haut, SYSTEME, ETAT, nom))
            except OSError:
                pass
        subprocess.run(["sync"], check=False)


def demarrage():
    """Au démarrage : efface l'ancien système remplacé, crée @instantanes s'il manque, nettoie, réécrit le menu."""
    if not sur_btrfs() or dans_un_chroot():
        return
    with Haut() as haut:
        for nom in os.listdir(haut):
            if nom.startswith("@remplace-") or nom == "@nouveau":
                detruire(os.path.join(haut, nom))
        if not os.path.isdir(os.path.join(haut, INSTANTANES)):
            btrfs("subvolume", "create", os.path.join(haut, INSTANTANES))
    if not os.path.ismount(DOSSIER) and not os.environ.get("SAMA_INSTANTANES"):
        uuid = montage(RACINE, "UUID")
        fstab = open("/etc/fstab").read()
        if "subvol=" + INSTANTANES not in fstab and "subvol=/" + INSTANTANES not in fstab:
            with open("/etc/fstab", "a") as f:
                f.write("UUID=%s /.instantanes btrfs subvol=/%s,noatime 0 0\n" % (uuid, INSTANTANES))
        os.makedirs(DOSSIER, exist_ok=True)
        subprocess.run(["mount", DOSSIER], check=False)
    # Une mise à jour coupée que le menu de démarrage n'a pas pu annuler (instantané sans noyau…) : la terminer
    if marque(MARQUE_MAJ):
        oter_marque(MARQUE_MAJ)
        subprocess.run(["dpkg", "--configure", "-a", "--force-confold"], env=dict(os.environ, DEBIAN_FRONTEND="noninteractive"),
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    if disponible():
        os.chmod(DOSSIER, 0o755)
        nettoyer()
        menu()


def main(a):
    action = a[0] if a else ""
    code = 0
    if action == "etat":
        etat()
    elif action == "creer" and len(a) > 1 and a[1] in ("manuel", "hebdo"):
        if a[1] == "hebdo" and (not disponible() or not automatiques()):
            return 0
        print(creer(a[1], " ".join(a[2:])[:60]))
    elif action == "activer":
        try:
            os.remove(os.path.join(RACINE, DESACTIVES))
        except OSError:
            pass
    elif action == "desactiver":
        ecrire(os.path.join(RACINE, DESACTIVES), "")
    elif action == "supprimer" and len(a) > 1:
        code = supprimer(a[1])
    elif action == "restaurer-au-demarrage" and len(a) > 1:
        code = restaurer_au_demarrage(a[1])
    elif action == "annuler-restauration":
        oter_marque(MARQUE_RESTAURER)
    elif action == "avant-maj":
        avant_maj(a[1] if len(a) > 1 else "0")
    elif action == "apres-maj":
        apres_maj("nuit")
    elif action == "avant-dpkg":
        avant_dpkg()
    elif action == "apres-dpkg":
        apres_dpkg()
    elif action == "paquets":
        paquets()
    elif action == "demarrage":
        demarrage()
    elif action == "menu":
        menu()
    elif action == "restaurer" and len(a) > 2:
        restaurer(a[1], a[2])
    elif action == "abandonner":
        abandonner()
    else:
        print(__doc__, file=sys.stderr)
        code = 1
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
