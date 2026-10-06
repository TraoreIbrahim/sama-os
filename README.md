# Sama OS

Sama OS est un système d'exploitation africain, ouvert et souverain, basé sur Debian et le noyau Linux.
Ce dépôt construit **l'ISO live 0.1 (prototype)** : une Debian 13 avec KDE Plasma 6, habillée aux couleurs de Sama, qui démarre depuis une clé USB.
Il contient aussi **Sama mobile** (dossier `mobile/`), le prototype du système pour téléphone, bâti sur Android (AOSP) en Kotlin et Jetpack Compose :
voir [mobile/README.md](mobile/README.md).

- Cahier des charges : https://claude.ai/code/artifact/40f0b8a8-8a5d-4400-85d1-1a79bbcf1a5d
- Prototype des écrans : https://claude.ai/artifact/TbcYm7U1R36ZajrUVcB9Jj

## Ce que contient l'ISO 0.1

| Élément | État |
|---|---|
| Identité système (`Sama OS 0.1`, `ID=samaos`, `ID_LIKE=debian`), logo de l'éléphant, fonds Aube et Nuit | fait |
| Thème clair et sombre : couleurs, icônes, fenêtres (style Kvantum Sama), menus, bascule en direct | fait |
| Démarrage : menu (GRUB) et écran animé (la trompe de l'éléphant) | fait |
| Écran de connexion, verrouillage, extinction, changement d'utilisateur | fait |
| La Natte : la Cour (applications et recherche), les Espaces, le Pouls (centre de contrôle et notifications) | fait |
| Bureau : cartes Heure, Data, Météo, Agenda ; vue des Espaces (Méta+Tab), transition entre Espaces | fait |
| Méthode de saisie : d'un clic sur FR dans le Pouls, ou Méta+Espace ; julakan (ɛ ɔ ɲ ŋ avec AltGr), wolof, kiswahili, codes DYU, WO, SW | fait (accents à l'appui long d'une touche : à venir, il faut une vraie méthode de saisie) |
| Fenêtres côte à côte : en déplaçant une fenêtre, dispositions moitiés, tiers, deux tiers, quarts, à 12 px l'une de l'autre ; poignée commune qui redimensionne deux fenêtres voisines ensemble (script KWin) | fait (poignée entre fenêtres du haut et du bas, en quarts : à venir) |
| Réglages Sama : réseau, Bluetooth, affichage, Bureau et Natte, notifications, son, langue et clavier, comptes, confidentialité, data et mises à jour, énergie, imprimantes, sauvegarde, accessibilité, à propos | fait (Organisation : la page dit ce qu'une école ou une entreprise pourrait faire ; l'inscription, avec Sama Parc, à venir) |
| Fenêtre « Autorisation requise » de Sama (à la place de celle de KDE) | fait |
| Installateur aux couleurs de Sama (Calamares, pages en QML d'après la maquette) | fait |
| Accueil du premier démarrage : Espaces, puis réglages pour bien démarrer | fait |
| Data : suivi du forfait au mois (vnstat), alertes à 80 % et 100 % | fait |
| Mises à jour la nuit (sur secteur, hors forfait) ; celles qui dérangeraient une session ouverte, au redémarrage (écran de démarrage) | fait |
| Instantanés du système (Btrfs) avant chaque mise à jour et chaque semaine ; une mise à jour coupée net est annulée au démarrage suivant | fait (essayé sur un Sama installé dans la VM, coupure réelle comprise) |
| Sauvegarde sur clé USB ou disque externe | fait (Sama Grenier, le nuage : à venir) |
| Session invitée | fait |
| Retour du courant : ce qui était ouvert est noté toutes les 30 s ; après une coupure, « Tout rouvrir » (documents de Sama Docs récupérés) | fait |
| Batterie faible : « Batterie à 10 % », autonomie estimée, « Activer l'économie maximale » (mode Économie, écran à 30 %, indexation en pause) ; tout revient après 2 minutes de courant | fait (essayé dans la VM, qui n'a pas de batterie, avec une batterie simulée) |
| Application bloquée : « Sama Docs ne répond pas », dernière sauvegarde des documents, « Attendre » ou « Forcer l'arrêt » (à la place de la fenêtre de KWin) | fait |
| Capture d'écran (Impr.) : zone, fenêtre ou écran entier, minuteur, vidéo ; copiée et rangée dans Images › Captures d'écran, « Annoter » | fait |
| Moniteur système : ce que consomme chaque application (processeur, mémoire, énergie), détails de ses processus, « Forcer à quitter » ; courbes ; applications ouvertes au démarrage ; débits et data. Méta+Échap ou Ctrl+Maj+Échap | fait (data par application : à venir, le noyau ne la compte pas par application sans outil dédié) |
| Aide hors ligne : 28 articles en français, en 6 thèmes (premiers pas, Espaces, data, fichiers et clé USB, sécurité et arnaques, écoles), recherche sans tenir compte des accents, liens qui ouvrent le bon réglage. `sama-aide thème/article` ouvre un article (articles : dossier `aide/`) | fait (julakan, wolof, kiswahili : à traduire, un dossier par langue ; vidéos : à venir) |
| Fichiers : favoris et clés USB, grille ou liste, recherche, filtres, corbeille, copies et conflits, glisser-déposer, aperçus des PDF et des vidéos, « Ouvrir avec… » | fait |
| Fenêtres « Enregistrer sous » et « Ouvrir » de Sama (portail de bureau) : nom, format (Word, OpenDocument…), emplacements et clés USB, dossiers, « Nouveau dossier », confirmation avant de remplacer ; pour Griot et les applications KDE, avec repli sur la fenêtre de KDE | fait (Sama Docs garde la fenêtre de KDE : LibreOffice envoie chaque demande deux fois au portail et rouvre la fenêtre ; « Garder une copie hors-ligne » viendra avec Sama Grenier) |
| Sama Sheet, le tableur : interface de Sama (menus, « Que voulez-vous faire ? » avec Ctrl+K, barre d'outils sur une ligne, barre de formule avec l'aide des fonctions en français et leurs propositions, en-têtes, feuilles, barre d'état avec somme, moyenne et nombre ; tableaux à la manière d'Excel avec Ctrl+T : titres, ▾ pour trier et filtrer, ligne des totaux qui ignore les lignes filtrées, ajout de ligne, renommage, formules lues avec les noms des colonnes, enregistrés comme de vrais tableaux Excel ; macros du moteur dans `usr/lib/samaos/moteur/basic`), moteur de LibreOffice (module Sama.Moteur, `suite/moteur`) ; .xlsx lu et écrit tel quel, fonctions en français, montants en francs CFA, couleurs du texte et du fond (nuancier de Sama), bordures, graphiques (colonnes, barres, lignes, secteurs, aires) aux couleurs de Sama, déplacés et agrandis à la souris ; confirmation avant de fermer sans enregistrer | première version (à venir, d'après les maquettes : panneau du tableau, choix du calcul de chaque total, Analyse rapide, fiches ; impression, recherche, menu du clic droit) |
| Suite Sama (Griot, Sugu, Sama Docs…) | à faire : des logiciels provisoires renommés les remplacent (Chromium, Discover, LibreOffice en français, Haruna, KWrite) ; Sama Docs suivra Sama Sheet, sur le même moteur |

## Arborescence

```
branding/            Identité visuelle : logo, fonds d'écran, palette (source unique)
natte/               La Natte, barre de navigation de Sama : la Cour, les Espaces, le Pouls (widgets Plasma en QML)
bureau/              Cartes du bureau : Heure, Data, Météo, Agenda (widgets Plasma en QML)
dev/                 Mode direct : voir les changements dans une machine virtuelle sans reconstruire l'ISO
iso/                 Configuration live-build de l'ISO
  auto/              Scripts config / build / clean de live-build
  config/package-lists/   Liste des paquets installés
  config/hooks/      Scripts exécutés pendant la construction (identité, fonds d'écran)
  config/includes.chroot/ Fichiers copiés tels quels dans le système (couleurs, apparence, Espaces)
docker/              Environnement de construction (Debian 13 + live-build)
mobile/              Sama mobile : Accueil, Natte, Pouls, Espaces, Réglages, applis natives, bouclier anti-arnaques (Android, Kotlin)
scripts/             Script de construction
sortie/              ISO produites (non versionné)
```

## Construire l'ISO

Prérequis : Docker, environ 15 Go d'espace disque et une bonne connexion (environ 2 Go de paquets à télécharger la première fois).

```bash
scripts/construire-iso.sh amd64
```

- `amd64` est la cible principale, pour les PC. Sur un Mac Apple Silicon, la construction passe par l'émulation et prend plusieurs heures.
- `arm64` construit une version pour tester rapidement dans une machine virtuelle sur un Mac Apple Silicon.

L'ISO et le journal de construction arrivent dans `sortie/`.

## Tester l'ISO

- **Sur un PC :** copier l'ISO sur une clé USB (avec balenaEtcher ou `dd`), puis démarrer dessus.
- **Sur Mac :** dans UTM (gratuit), créer une machine virtuelle Linux à partir de l'ISO (version `arm64` pour aller vite, version `amd64` en mode émulation).

Session live : utilisateur `sama`, sans mot de passe à la connexion automatique.

## Licence et contributions

Sama OS est sous licence [Apache 2.0](LICENSE) ; les quelques fichiers tirés de KDE gardent leur licence, la police
Noto Sans et les fonds d'écran aussi (liste dans [NOTICE](NOTICE)). Pour participer : [CONTRIBUTING.md](CONTRIBUTING.md).

## Prochaines étapes

1. Construire et tester l'ISO avec tout ce qui précède (paquets ajoutés : unattended-upgrades, vnstat, rsync, poppler-utils,
   ffmpegthumbnailer, haruna, libreoffice-kf6, libreoffice-l10n-fr, hunspell-fr, hyphen-fr, pkexec, kde-spectacle,
   wl-clipboard).
2. Applications de la suite Sama après Fichiers.
3. Natte sur les côtés de l'écran, Sama Grenier, partage des mises à jour en réseau local.
4. Préparer la forge, la chaîne de construction automatique et les dépôts souverains.
