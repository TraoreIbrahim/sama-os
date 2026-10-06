# Sama OS mobile — prototype

Premières surfaces système de Sama en Kotlin + Jetpack Compose, dessinées avec Banco :
l'Accueil, la Natte, la Cour, le Pouls et les Espaces. Le module `accueil` s'installe comme
application d'accueil d'un téléphone ou d'un émulateur Android (Android 8 minimum).

## Lancer sur l'émulateur

La version optimisée (R8) donne une idée juste de la fluidité ; la version de débogage est
nettement plus lente avec Compose.

```bash
./gradlew :accueil:assembleRelease
adb install -r accueil/build/outputs/apk/release/accueil-release.apk
adb shell cmd role add-role-holder android.app.role.HOME africa.samaos.accueil 0
adb shell input keyevent KEYCODE_HOME
```

Après une réinstallation, Android peut redemander l'appli d'accueil : refaire la ligne `role`.

Le Pouls a besoin de deux accès qu'une appli ordinaire demande à la personne. Il ouvre
lui-même les bons réglages ; sur l'émulateur, on peut aussi les accorder d'un coup :

```bash
adb shell cmd notification allow_listener africa.samaos.accueil/africa.samaos.accueil.Ecouteur
adb shell appops set africa.samaos.accueil WRITE_SETTINGS allow
```

Pour revenir à l'accueil d'origine du Pixel :

```bash
adb shell cmd role add-role-holder android.app.role.HOME com.google.android.apps.nexuslauncher 0
```

## Sama en couche système (émulateur sans Google)

Un pas vers Sama OS : sur un émulateur « Android Open Source » (API 36, arm64-v8a, créé dans
Android Studio sous le nom `Sama`), Sama s'installe dans le système au lieu d'être une simple appli.

```bash
~/Library/Android/sdk/emulator/emulator -avd Sama -writable-system -no-snapshot-load
outils/sama-systeme.sh
```

Le script installe l'Accueil dans `/system/priv-app` avec ses droits système
(`systeme/privapp-permissions-samaos.xml`), l'animation de démarrage Banco
(`systeme/animation/fabriquer.py`), le français de Côte d'Ivoire et la navigation par gestes.
Ensuite, pour voir une modification : `./gradlew :accueil:assembleRelease` puis
`adb install -r accueil/build/outputs/apk/release/accueil-release.apk` (l'appli reste système).
Attention : un nouveau droit privilégié dans le manifeste doit aussi entrer dans la liste
`privapp-permissions-samaos.xml`, sinon Android refuse de démarrer ; relancer alors le script.

Le script pose aussi la surcouche Android de Sama (`systeme/surcouche-android`) : des réglages de
constructeur (pas d'écran « Passage à … » d'Android, le paysage Banco comme fond d'écran par défaut).

Dans ce mode :

- Sur l'Accueil, le Pouls est le seul volet (celui d'Android est coupé tant que l'Accueil est affiché).
- Le fond d'écran et l'écran de verrouillage prennent le paysage de l'Espace, de jour ou de nuit.
- Les écrans d'Android (Paramètres, verrouillage) tirent leurs couleurs de la latérite, et le thème
  sombre d'Android suit la Nuit de Banco : au coucher, de 19 h à 6 h (Android attend que l'écran
  s'éteigne pour basculer). La tuile Nuit du Pouls fait basculer tout le téléphone.
- Il n'y a pas d'Espace tout fait : au départ, Maison seule. « Nouvel Espace » (vue des Espaces)
  crée un vrai profil Android avec son nom, sa couleur et son paysage (Lagune, Savane, Nuit) ;
  on y passe par un écran Banco, puis on le protège avec le code d'Android, qui chiffre ses données.
- « Verrouiller avec » (Nouvel Espace, puis réglages de l'Espace) : schéma ou code, code, ou mot de passe.
  Android ne laisse pas imposer le type exact, mais il fait respecter ce niveau quand on choisit le code
  (il grise ce qui ne suffit pas et refuse les codes trop simples comme 1234).
- « Applis de départ » (Nouvel Espace) : les applis du téléphone qu'aura l'Espace ; le téléphone, les
  SMS et les Paramètres y sont toujours. Sama les pose au premier démarrage de l'Espace : il masque les
  autres (rien n'est désinstallé) et copie celles qui n'y étaient pas. Dans l'Espace, Réglages >
  Applis de l'Espace permet d'en rajouter ou d'en masquer.
- Dans chaque nouvel Espace, le Pouls demande une fois l'accès aux notifications.
- Réglages des Espaces (vue des Espaces) : nom, couleur et paysage de chaque Espace. Le code d'un
  Espace se change seulement depuis l'Espace lui-même (personne ne peut le changer d'ailleurs) ;
  un Espace se supprime depuis Maison, après le code de Maison s'il y en a un.

## Le premier démarrage

À l'allumage d'un téléphone neuf, `DemarrageActivity` passe avant l'Accueil : bienvenue, langue du
téléphone, SIM et Wi-Fi, compte Sama (pas encore ouvert : « Sans compte »), code. Le rond Accessibilité
de la bienvenue ouvre une étape à part (maquette l5-acces-demarrage) : texte plus grand, loupe, Plein soleil,
appliqués tout de suite. Une fois fini, il marque le téléphone configuré et se désactive. Pour le revoir sur l'émulateur déjà configuré :

```bash
adb shell pm enable --user 0 africa.samaos.accueil/.DemarrageActivity
adb shell am start -n africa.samaos.accueil/.DemarrageActivity --ez revoir true
```

## Les gestes

- Glisser vers le haut, n'importe où sur l'Accueil : la Cour monte et suit le doigt.
  Vers le bas dans la Cour (liste en haut) : elle redescend. Le bouton éléphant de la Natte l'ouvre aussi.
- Glisser vers le bas sur l'Accueil : le Pouls descend (heure, tuiles, luminosité, notifications).
  Vers le haut, ou une touche sous la crête : il remonte. Une notification se balaie pour l'effacer.
- Trois doigts à l'horizontale : on passe d'un Espace à l'autre, dans l'ordre de leur création.
  La souris de l'émulateur ne fait que deux doigts : `outils/trois_doigts.py -400` simule le geste.
  On peut aussi toucher le galet de l'Espace, sous la date, pour ouvrir la vue des Espaces.
  La tuile Espaces du Pouls ramène à Maison ; les autres Espaces redemandent alors leur code.

Les Espaces demandent que Sama soit le système : installé comme simple appli d'accueil sur un
téléphone ordinaire, Sama n'a que Maison.

## L'Accueil, la Cour et le Pouls au quotidien

- Tenir une appli (Accueil, Cour, dossier) : son menu (raccourcis de l'appli, Infos, Retirer de l'Accueil
  ou Sur l'Accueil, Vers un Espace depuis Maison, Désinstaller pour les applis qui ne sont pas du système).
- Tenir puis glisser une appli de l'Accueil : on la range ; lâchée sur une autre, elle fait un dossier.
  Un dossier s'ouvre d'une touche ; son nom se change dans le dossier.
- Tenir le paysage : « Modifier l'Accueil » (Paysage, Widgets, Réglages). Les widgets viennent des applis
  du téléphone et se posent sous l'heure ; on en tient un pour le retirer.
- Chercher dans la Cour : applis, contacts (Appeler prépare l'appel, Message ouvre la conversation),
  réglages, SMS, noms de fichiers, puis le web avec le navigateur du téléphone.
- Le Pouls : tenir une tuile pour « Modifier le Pouls » (ranger, retirer, ajouter : mode avion, point
  d'accès, économiseur, Ne pas déranger, rotation) ; le lecteur de ce qui joue, avec « Écouter sur » ;
  les notifications groupées, qu'on reporte (appui long) ou auxquelles on répond sans ouvrir l'appli.

« Vers un Espace » ne fait qu'une demande : Android ne laisse pas Maison toucher aux applis d'un autre
profil, c'est l'Accueil de cet Espace qui pose l'appli à la prochaine entrée.

### L'appli d'essai (émulateur seulement)

Le module `essais` joue une leçon avec une vraie session média et envoie un message auquel on peut
répondre, pour essayer le lecteur et la réponse du Pouls sans appli tierce :

```bash
./gradlew :essais:assembleDebug && adb install -r essais/build/outputs/apk/debug/essais-debug.apk
adb shell pm grant africa.samaos.essais android.permission.POST_NOTIFICATIONS
adb shell am start -n africa.samaos.essais/.Essais --es essai lecture
adb shell am start -n africa.samaos.essais/.Essais --es essai message
adb shell am start -n africa.samaos.essais/.Essais --es essai arret
```

## Les Réglages de Sama

Le module `reglages` (paquet `africa.samaos.reglages`) remplace les Paramètres d'Android : il reçoit leurs
intentions (priorité 10, appli système) et dessine en Banco les pages du lot 3 — réseau et data (Wi-Fi,
SIM, partage de connexion, VPN et DNS privé, mode avion), appareils (Bluetooth, USB), applications
(infos, autorisations, par défaut, accès spéciaux, en pause), notifications, batterie, stockage, son,
affichage (thème, Plein soleil, taille du texte…), fond d'écran, sécurité, localisation, comptes, système.
S'y ajoutent des pages des lots 4 et 5 :
- **Bien-être** : le temps d'écran du jour (heure par heure, appli par appli, d'après les événements d'usage
  d'Android), les minuteurs d'applis (Android prévient quand la limite est atteinte, l'appli se met en pause
  jusqu'à minuit avec le message de Sama), la Concentration (des applis en pause jusqu'à une heure choisie).
  Téléphone, Messages, Réglages, Horloge et les services de l'opérateur ne se mettent jamais en pause.
- **Accessibilité** : couleurs et contraste (fort contraste, inversion, correction des couleurs), loupe,
  sans animations, appui long, son mono, menu d'accessibilité d'Android.
- **Urgence** : fiche médicale et contacts d'urgence, gardés lisibles avant le premier déverrouillage ;
  « Urgence » sur l'écran verrouillé ouvre la page de Sama (SAMU 185, pompiers 180, police 170, fiche, proches).
  **SOS** : cinq appuis sur le bouton marche (geste d'Android) ouvrent le SOS de Sama, même verrouillé :
  cinq secondes pour annuler, puis l'appel du numéro choisi (SAMU par défaut) et la position envoyée par SMS
  aux contacts d'urgence, toutes les 15 minutes pendant 2 heures (service au premier plan, notification
  « Arrêter le partage »). La surcouche SystemUI (`systeme/surcouche-systemui`, réglage
  `config_preferredEmergencySosPackage`) confie le geste aux Réglages. « Essayer sans rien envoyer » montre
  le déroulé sans appeler ni envoyer. Sur l'émulateur : `adb shell "input keyevent 26; …"` cinq fois
  dans une seule commande (des appuis trop rapprochés sont ignorés par Android).
- **Réinitialiser** : réseaux et Bluetooth, un Espace, ou tout le téléphone (avec la liste de ce qui partira).
- **Stockage presque plein** : la notification d'Android « stockage bientôt saturé » (ACTION_MANAGE_STORAGE)
  ouvre la page de Sama : vider les fichiers temporaires, trier dans Fichiers › Nettoyer, ranger sur la carte SD.
  Essai : `adb shell cmd devicestoragemonitor force-low -f` (puis `reset`).
- **Surchauffe** : l'Accueil suit l'état thermique d'Android ; à « sévère », il baisse la luminosité et prévient,
  l'Appareil photo se ferme ; la page « Le téléphone chauffe » dit ce qui a été fait (`sama_chaleur`) et tout
  reprend au frais. Essai : `adb shell cmd thermalservice override-status 3` et `dumpsys battery set temp 460`.

Ce que Sama ne fait pas encore repart vers la page correspondante des Paramètres d'Android.
Attention : une appli de /system/priv-app ne voit compter la priorité d'un nouveau filtre d'intention qu'une
fois sa nouvelle version posée dans le système (`outils/sama-systeme.sh`), et toute permission
« signature|privileged » doit figurer dans `systeme/privapp-permissions-samaos.xml`, sinon l'émulateur
ne démarre plus (`outils/droits-reglages.py` la recopie si elle porte `tools:ignore="ProtectedPermissions"`).

Il est signé avec la clé « platform » de test d'AOSP (`systeme/cles`, publique, émulateur seulement) :
il a ainsi les droits des Paramètres d'Android (se connecter à un Wi-Fi, associer un appareil Bluetooth,
couper le mode avion…). `outils/sama-systeme.sh` le pose dans `/system/priv-app/SamaReglages` ;
ensuite `adb install -r reglages/build/outputs/apk/release/reglages-release.apk` suffit.
`outils/droits-reglages.py` recopie ses droits protégés dans la liste privapp à chaque construction.

Le module `banco` porte le système de design commun à l'Accueil et aux Réglages.

## Les applis natives (lot 2)

Chaque appli a son module, son identité de couleurs (`banco/appli/Appli.kt`, d'après `applis.css` des
maquettes) et la même ossature : tête, lignes, puces, feuilles d'actions, dialogues. Elles sont signées avec
la clé de test de la plateforme et installées comme des applis ordinaires par `outils/sama-systeme.sh`, qui
leur donne leurs droits, leurs rôles (téléphone, SMS) et met de côté les applis d'Android qu'elles
remplacent (sans les effacer : `pm enable` les remet). L'Accueil cache l'icône de ces dernières.

| Module | Ce qu'il fait |
| --- | --- |
| `telephone` | Clavier, journal, favoris, messagerie, appel entrant et en cours (InCallService), filtrage, codes USSD ; les numéros des secours sont nommés (SAMU · 185). |
| `contacts` | Liste, fiche, modification, import de la SIM ; « Choisir un contact / un numéro / une adresse » pour les autres applis (ACTION_PICK, GET_CONTENT), qui ne reçoivent que ce qui a été touché ; fiches reçues (.vcf, vCard 2.1 à 4.0) ajoutées d'un geste, celles qui se font passer pour un opérateur ou un service client décochées et signalées. |
| `messages` | SMS (appli SMS par défaut) : conversations, expéditeurs de service à part, réponse depuis la notification. |
| `horloge` | Alarmes (elles sonnent téléphone en veille), horloge du monde, minuteur, chrono, heure du coucher. |
| `calculatrice` | Calculs exacts, conversion F CFA ↔ € au taux fixe. |
| `notes` | Notes et listes, rappels à l'heure dite. |
| `agenda` | Le mois, le jour, les événements avec rappel et répétition, dans un agenda du téléphone ; reçoit les rappels d'Android (EVENT_REMINDER) et « Ajouter à l'agenda » des autres applis. |
| `fichiers` | Place prise par sorte, dossiers, reçus (avec les téléchargements en cours et « Attendre le Wi-Fi au-delà de 50 Mo »), corbeille de 30 jours, Nettoyer (vieilles vidéos reçues, doublons par empreinte, applis inutilisées, fichiers temporaires). Une appli reçue (APK) est signalée et son installation demande une confirmation qui explique le risque. Lecteur de PDF (moteur d'Android : pages, loupe, partage, impression ; aussi pour les autres applis). Archives .zip : contenu, « Extraire ici » (jamais hors du dossier, jamais plus que la place libre ; Android refuse déjà les chemins en « .. »). |
| `photos` | La pellicule jour par jour, les albums, la visionneuse (zoom, vidéos), favoris, corbeille, retouche (recadrer, redresser, lumière, filtres, annoter) enregistrée en copie. Avant de partager une photo qui garde le lieu de la prise de vue, Photos propose de l'envoyer sans le lieu. |
| `dictaphone` | Enregistrement au premier plan (il continue écran éteint), pause, repères, réécoute avec saut aux repères. |
| `appareil` | L'Appareil photo (Camera2) : photo (retardateur, formats 3:4, 9:16, 1:1, grille, zoom, mise au point au toucher, flash), vidéo (temps restant réel), scanner de QR codes (lien, Wi-Fi, numéro, code d'opérateur… avec mises en garde : lien raccourci, adresse trompeuse, code qui lance un transfert) et de documents (album « Documents scannés »). Touches de volume pour déclencher. Appli système (`/system/app`) : Android ne laisse que l'appareil du système répondre aux applis qui demandent une photo. Sur l'émulateur, les affiches de la scène virtuelle servent à essayer le scanner (`-virtualscene-poster wall=…`). |
| `griot` | Le navigateur (WebView du système), navigateur par défaut : onglets, historique et favoris sur le téléphone, recherche DuckDuckGo par défaut, téléchargements par Android (ils arrivent dans Fichiers › Reçus avec le site d'origine), Mode léger (les images se chargent au toucher, compteur d'images évitées). Domaine réel affiché, adresses en lettres d'autres alphabets montrées sous leur vraie forme, pages non chiffrées signalées, certificats douteux refusés, appli à télécharger précédée d'une mise en garde. Les positions (geo:) des autres applis s'ouvrent sur OpenStreetMap. |
| `lecteur` | Musique en arrière-plan (MediaSession : notification, Pouls, écouteurs ; pause pendant un appel ou quand on débranche les écouteurs), aléatoire, répétition ; vidéos qui reprennent là où on s'était arrêté. |

## Le bouclier anti-arnaques (innovation 2)

Le module `bouclier` (bibliothèque, paquet `africa.samaos.bouclier`) porte les règles, communes aux applis.
Tout se lit sur le téléphone : ni SMS, ni appel, ni page ne part ailleurs. Les réglages (`sama_bouclier`),
le journal (`sama_bouclier_journal`) et les demandes d'installation (`sama_bouclier_delais`) sont dans les
réglages globaux d'Android, partagés entre les applis signées avec la clé de la plateforme.

| Où | Ce que fait le bouclier |
| --- | --- |
| `messages` | Un SMS qui imite un opérateur depuis un numéro ordinaire (même d'un contact), « je me suis trompé, renvoyez-moi l'argent », une demande de code, un lien qui se fait passer pour un opérateur : carte sous le message avec le vrai solde (dernier SMS de l'expéditeur officiel), Bloquer, Signaler ; notification « Arnaque probable » ou « Lien piégé ». |
| `telephone` | Un inconnu qui appelle pour la 3ᵉ fois en 24 h (ou qui rappelle la nuit) : carte « Appel suspect » et bouton Bloquer. Un code de mobile money (#144#, *133#, *155#…) tapé pendant un appel avec un inconnu ou dans les 30 s qui suivent : mise en garde, « Raccrocher », puis le code n'est possible que 30 s après la fin de l'appel. |
| `griot` | Une page qui imite un service de mobile money et demande le code secret, hors des sites officiels : « Page dangereuse » à la place de la page (champs désactivés, rien ne s'envoie), barre d'adresse en rouge, page retirée de l'historique. Une adresse qui cite un opérateur reste voilée le temps de la lecture ; un champ de code ajouté après coup relance la lecture. « Signaler une erreur » retient le site comme sûr, sauf pendant un appel. |
| `reglages` | Réglages › Bouclier anti-arnaques : les quatre surveillances, le bilan de la semaine (regroupé par numéro ou site), les arnaques courantes, « Signaler anonymement » (pour la future liste commune, désactivé au départ). Autoriser une appli à installer hors de Sugu crée une demande qui n'est possible que 24 h plus tard, et seulement avec l'heure du réseau (avancer l'horloge ne suffit pas) ; le programme d'installation d'Android y est renvoyé (`MANAGE_UNKNOWN_APP_SOURCES`). |

Pour essayer sur l'émulateur : `adb emu sms send 0759331208 "Orange Money : vous avez recu 25 000 F…"`,
`adb emu gsm call 0599401277` (trois fois), une page de test servie en local (`adb reverse tcp:8088 tcp:8088`).
Pas encore fait : « J'ai été arnaqué » (il faut les numéros et codes officiels des opérateurs), la liste
commune des numéros signalés (serveur), « Protéger aussi un proche ».

## Soldes et forfaits (innovation 1, premier pas)

La bibliothèque `soldes` (paquet `africa.samaos.soldes`) lit le crédit, la data restante, le forfait et sa date
de fin dans les SMS des expéditeurs officiels des 30 derniers jours (et dans la dernière réponse USSD, notée
dans `sama_soldes_ussd`). Les SMS restent la seule source : rien n'est recopié ; le solde mobile money n'est
jamais gardé, il est relu dans le dernier SMS de transaction au moment de l'afficher.

- **Pouls** : « Mes SIM » (crédit, data avec jauge, fin du forfait) et « Mobile money · Solde masqué », montré
  après le code du téléphone, caché de nouveau quand le Pouls se referme.
- **Réglages › Soldes et forfaits** : ce qui a été lu pour chaque SIM, le code de solde de l'opérateur (saisi par
  la personne ; seul un code simple comme #123# est accepté, un code à étoiles pourrait acheter), « Lire mon solde
  maintenant » (USSD sans écran d'opérateur), la data des 7 derniers jours, les alertes.
- **Messages** : à la réception d'un SMS d'opérateur, alerte si la data passe sous 20 % ou le crédit sous 200 F ;
  rappel 2 h 30 avant la fin d'un forfait qui a encore de la data (maquette i1-alerte-expiration).

Pas encore fait : codes de solde vérifiés par opérateur (pour lire tout seul, une fois par heure), conseil de
forfait (i1-forfait-conseil), achat (i1-achat), carte de recharge scannée (i1-carte-recharge). Les règles de
lecture des SMS sont écrites d'après des formulations courantes : à valider sur de vrais SMS d'Orange, MTN et Moov.

## Contenu

Dans `banco` : `Banco.kt` (palettes Aube, Nuit, Savane, Plein soleil ; polices), `Paysage.kt` (ciel, astre,
collines, crête), `Icones.kt`, `Espace.kt` (paysages et couleurs d'Espace), `Composants.kt` (gabarit des
réglages, rangées, pastilles, cases, interrupteur, boutons…).

Dans `accueil` :
- `Panneau.kt` : un panneau qui suit le doigt (la Cour, le Pouls), avec élan et défilement imbriqué.
- `Accueil.kt` : l'heure, la date, l'Espace actif, les widgets, les applis et la Natte ; les gestes.
- `Disposition.kt`, `GrilleAccueil.kt` : l'Accueil rangé par la personne (applis, dossiers, widgets),
  le rangement au doigt et les dossiers.
- `MenuAppli.kt` : le menu d'une appli tenue. `ModifierAccueil.kt` : Modifier l'Accueil et le choix des widgets.
- `Widgets.kt` : l'hôte des widgets d'Android.
- `Cour.kt`, `Recherche.kt` : toutes les applis de l'Espace et la recherche.
- `Pouls.kt`, `TuilesPouls.kt`, `Systeme.kt` : le Pouls, ses tuiles et Modifier le Pouls, l'état du téléphone.
- `Ecouteur.kt`, `NotificationsPouls.kt` : les notifications (groupées, reportées, réponse directe).
- `Media.kt` : le lecteur du Pouls et « Écouter sur ».
- `Espaces.kt`, `Bascule.kt`, `VueEspaces.kt` : les Espaces, le geste à trois doigts et la vue des Espaces.
- `Profils.kt`, `EcransProfils.kt` : les profils Android et les réglages d'Android que Sama tient ;
  Nouvel Espace, passage d'un Espace à l'autre, Protéger l'Espace.
- `ReglagesEspaces.kt` : les Réglages des Espaces, ceux d'un Espace et sa suppression.
- `ChoixApplis.kt` : le choix des applis d'un Espace (à sa création, puis dans ses réglages).
- `ChoixVerrou.kt` : « Verrouiller avec » et l'ouverture du choix du code d'Android au bon niveau.
- `Reglages.kt` : les réglages de l'Accueil, lisibles avant le déverrouillage du profil.
- `Invite.kt` : « Prêter le téléphone » (maquette l5-invite), un utilisateur invité éphémère d'Android :
  appels et SMS permis, Griot posé, effacé dès qu'on revient dans Maison.
- `Demarrage.kt` : le premier démarrage (maquettes dem-01 à dem-06).
- `FondEcran.kt` : le paysage Banco en fond d'écran du système.
- `Applis.kt` : lecture des applis installées et choix de celles de la Natte.
- `PleinSoleil.kt` : le thème Plein soleil et le geste à trois doigts, réglés dans les Réglages.

La police Noto Sans est sous licence SIL OFL 1.1 (`licences/OFL-NotoSans.txt`).
