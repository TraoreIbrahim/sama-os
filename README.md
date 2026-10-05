# Sama OS

Sama OS est un système d'exploitation africain, ouvert et souverain, basé sur Debian et le noyau Linux.
Ce dépôt construit **l'ISO live 0.1 (prototype)** : une Debian 13 avec KDE Plasma 6, habillée aux couleurs de Sama, qui démarre depuis une clé USB.

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
| Réglages Sama : réseau, Bluetooth, affichage, Bureau et Natte, notifications, son, langue et clavier, comptes, confidentialité, data et mises à jour, énergie, imprimantes, sauvegarde, accessibilité, à propos | fait (Organisation : à venir) |
| Fenêtre « Autorisation requise » de Sama (à la place de celle de KDE) | fait |
| Installateur aux couleurs de Sama (Calamares, pages en QML d'après la maquette) | fait |
| Accueil du premier démarrage : Espaces, puis réglages pour bien démarrer | fait |
| Data : suivi du forfait au mois (vnstat), alertes à 80 % et 100 % | fait |
| Mises à jour la nuit (sur secteur, hors forfait) ; celles qui dérangeraient une session ouverte, au redémarrage (écran de démarrage) | fait |
| Instantanés du système (Btrfs) avant chaque mise à jour et chaque semaine ; une mise à jour coupée net est annulée au démarrage suivant | fait (à essayer sur un Sama installé) |
| Sauvegarde sur clé USB ou disque externe | fait (Sama Grenier, le nuage : à venir) |
| Session invitée | fait |
| Fichiers : favoris et clés USB, grille ou liste, recherche, filtres, corbeille, copies et conflits, glisser-déposer, aperçus des PDF et des vidéos, « Ouvrir avec… » | fait |
| Suite Sama (Griot, Sugu, Sama Docs…) | à faire : des logiciels provisoires renommés les remplacent (Chromium, Discover, LibreOffice en français, Haruna, KWrite) |

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

## Prochaines étapes

1. Construire et tester l'ISO avec tout ce qui précède (paquets ajoutés : unattended-upgrades, vnstat, rsync, poppler-utils,
   ffmpegthumbnailer, haruna, libreoffice-kf6, libreoffice-l10n-fr, hunspell-fr, hyphen-fr), puis l'installer pour
   essayer le retour en arrière des instantanés (menu de démarrage) sur un vrai disque Btrfs.
2. Applications de la suite Sama après Fichiers.
3. Natte sur les côtés de l'écran, Sama Grenier, partage des mises à jour en réseau local.
4. Préparer la forge, la chaîne de construction automatique et les dépôts souverains.
