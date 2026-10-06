# Contribuer à Sama OS

Sama OS est un système d'exploitation africain, ouvert et souverain. Le dépôt en contient deux parties :

- **le bureau** : une Debian 13 avec KDE Plasma 6, habillée et complétée par Sama (`branding/`, `natte/`, `bureau/`, `iso/`) ;
- **le mobile** : un système pour téléphone bâti sur Android (AOSP), en Kotlin et Jetpack Compose (`mobile/`).

Merci de votre aide. Ce guide dit comment travailler sur l'une ou l'autre, et ce qui compte pour que votre
contribution soit acceptée.

## Avant de commencer

- **Le projet s'écrit en français** : l'interface, les commentaires, les noms dans le code (`ouvrir`, `Espace`,
  `Pouls`), les messages de commit. Les termes techniques restent tels quels (Intent, Activity, QML).
- **Chaque écran part d'une maquette** du prototype (liens dans le [README](README.md)) ; elles ont un nom
  (`l2-tel-clavier`, `i2-lien-piege`…). Citez-le dans votre proposition.
- **Pour un changement important, ouvrez d'abord une issue** : on en parle avant que vous y passiez du temps.

## Les principes de Sama

Une contribution qui marche mais qui oublie l'un de ces points sera reprise avant d'être acceptée.

1. **Épuré, sans folklore.** Pas de motifs décoratifs « africains » : l'identité de Sama tient à sa langue, à ses
   usages et à son système de design, Banco (ciel et sol, crête ondulée, Noto Sans, bouton Avancer en bas à droite).
2. **Banco pour le système, une identité propre pour chaque appli.** L'Accueil, les Réglages et les écrans du
   système sont en Banco ; les applis natives (Téléphone, Fichiers, Griot…) gardent les couleurs de leur icône
   (`mobile/banco/src/main/java/africa/samaos/banco/appli/Appli.kt`).
3. **Un réglage d'apparence se voit tout de suite**, sans redémarrer ni rouvrir.
4. **Chaque fonction est pensée contre les arnaqueurs** : le prétexte (« je suis agent Orange »), l'urgence
   (« vite, avant minuit »), le repérage (ce qu'un inconnu peut apprendre du téléphone). Pas de « continuer quand
   même » facile, des délais là où l'on pourrait être pressé, des mises en garde pendant un appel.
5. **Rien ne quitte le téléphone ou l'ordinateur sans raison ni accord.** Le traitement se fait sur l'appareil,
   sans pisteur et sans dépendre des services de Google.
6. **Pour la vraie vie** : data chère, réseau instable, téléphones modestes, plusieurs SIM, coupures de courant.
7. **Rien de tiers par défaut** : Sama a sa propre suite (Griot, Sugu, Sama Docs…).

## Sama mobile

Prérequis : Android Studio (ou le SDK Android en ligne de commande) et un JDK 17.

**Construire.** La version release, avec R8 : la version debug de Compose est trop lente sur l'émulateur.

```bash
cd mobile
./gradlew assembleRelease
```

La première construction télécharge les clés de test d'AOSP et vérifie leur empreinte (`mobile/systeme/cles`).
Elles sont publiques : **émulateur seulement**, jamais pour un vrai téléphone ni une version distribuée.

**L'émulateur Sama.** Créez un AVD nommé `Sama` avec une image « Android Open Source Project » API 36
(sans Google), lancez-le avec `-writable-system`, puis :

```bash
mobile/outils/sama-systeme.sh
```

Le script pose l'Accueil et les Réglages dans le système, installe les applis, donne les droits, ajoute les
surcouches Android et SystemUI. Ensuite, pour une appli :

```bash
adb install -r mobile/<module>/build/outputs/apk/release/<module>-release.apk
```

**Pièges connus.**

- Toute permission « signature|privileged » des Réglages doit figurer dans
  `mobile/systeme/privapp-permissions-samaos.xml` (`mobile/outils/droits-reglages.py` la recopie si elle porte
  `tools:ignore="ProtectedPermissions"`), sinon l'émulateur ne démarre plus.
- Un nouveau filtre d'intention d'une appli système ne prend sa priorité qu'une fois l'APK posé dans
  `/system/priv-app` et l'émulateur redémarré.
- Dans les Réglages de l'émulateur, ne touchez pas « Couper le débogage USB » : adb est coupé avec.

**Le code.** Kotlin et Jetpack Compose (bibliothèque foundation, sans Material). Les composants communs sont dans
`mobile/banco`. Suivez le style du fichier que vous modifiez : commentaires courts, en français, qui disent pourquoi.

**Essayer.** `mobile/README.md` donne les commandes d'essai (SMS et appels simulés, surchauffe, stockage plein,
geste SOS…). Joignez des captures d'écran de l'émulateur à votre proposition.

## Sama bureau

- **Voir ses changements tout de suite** : le mode direct (`dev/LISEZMOI.md`) envoie la Natte, les widgets et les
  réglages dans une machine virtuelle, sans reconstruire l'ISO. Utilisez **votre propre clé SSH** : remplacez
  `dev/cle-dev.pub` par la vôtre dans la machine virtuelle (et `~/.ssh/samaos_dev` sur votre poste), sans
  l'envoyer dans le dépôt.
- **Construire l'ISO** : voir la section « Construire l'ISO » du [README](README.md) (Docker, environ 15 Go,
  environ 2 Go de paquets la première fois).

## Proposer une modification

1. Faites une copie du dépôt (fork) et une branche pour votre changement.
2. Des commits en français, qui disent ce que la personne verra (« Messages : la carte d'arnaque montre le vrai
   solde »), pas seulement ce qui a changé dans le code.
3. Ouvrez une demande de fusion (pull request) vers `main` : ce qui change, la maquette concernée, comment vous
   l'avez essayé, des captures.

## Licence

Sama OS est sous licence [Apache 2.0](LICENSE). En proposant une contribution, vous acceptez qu'elle soit publiée
sous cette licence (section 5). Un fichier tiré d'un autre projet garde sa licence : indiquez-la en tête
(`SPDX-License-Identifier`) et ajoutez-le au fichier [NOTICE](NOTICE). Le nom « Sama OS » et l'identité visuelle
sont des marques, que la licence ne couvre pas.

## Une faille de sécurité ?

Ne la décrivez pas dans une issue publique : signalez-la en privé depuis l'onglet **Security** du dépôt,
bouton **Report a vulnerability**. Seuls les mainteneurs la voient ; laissez-leur le temps de corriger avant d'en parler.
