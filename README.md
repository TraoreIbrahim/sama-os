# Sama OS

Sama OS est un système d'exploitation africain, ouvert et souverain, basé sur Debian et le noyau Linux.
Ce dépôt construit **l'ISO live 0.1 (prototype)** : une Debian 13 avec KDE Plasma 6, habillée aux couleurs de Sama, qui démarre depuis une clé USB.

- Cahier des charges : https://claude.ai/code/artifact/40f0b8a8-8a5d-4400-85d1-1a79bbcf1a5d
- Prototype des écrans : https://claude.ai/artifact/TbcYm7U1R36ZajrUVcB9Jj

## Ce que contient l'ISO 0.1

| Élément | État |
|---|---|
| Identité système (`Sama OS 0.1`, `ID=samaos`, `ID_LIKE=debian`) | fait |
| Fonds d'écran Aube et Nuit, logo de l'éléphant | fait |
| Palettes de couleurs Sama Clair et Sama Sombre | fait |
| Barre flottante en bas (première approximation de la Natte) | fait, avec les composants standards de Plasma |
| Espaces Travail, École, Maison (activités Plasma) créés au premier démarrage | fait |
| Langues : français par défaut, anglais, swahili ; police Andika pour l'apprentissage | fait |
| Écran de connexion avec le fond Sama | fait |
| La Natte maison (widget QML) | squelette expérimental, installé mais pas encore dans la disposition par défaut |
| Installateur aux couleurs de Sama (Calamares) | à faire : il garde l'apparence Debian |
| Écran de démarrage (GRUB, Plymouth) aux couleurs de Sama | à faire |
| Suite Sama (Griot, Sugu, Sama Docs, Sama Sheet…) | à faire : des logiciels provisoires les remplacent (Chromium, LibreOffice, Discover) |

## Arborescence

```
branding/            Identité visuelle : logo, fonds d'écran, palette (source unique)
natte/               La Natte, barre de navigation de Sama (widget Plasma en QML)
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

1. Construire et tester la première ISO, corriger ce qui ne démarre pas.
2. Tester la Natte maison et la mettre dans la disposition par défaut.
3. Habiller l'installateur, GRUB et l'écran de démarrage.
4. Préparer la forge, la chaîne de construction automatique et les dépôts souverains.
