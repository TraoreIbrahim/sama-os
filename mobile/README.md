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

Le script installe l'Accueil, les Réglages et Sugu dans `/system/priv-app` avec leurs droits système
(`systeme/privapp-permissions-samaos.xml`), l'animation de démarrage Banco
(`systeme/animation/fabriquer.py`), le français de Côte d'Ivoire et la navigation par gestes.
Ensuite, pour voir une modification : `./gradlew :accueil:assembleRelease` puis
`adb install -r accueil/build/outputs/apk/release/accueil-release.apk` (l'appli reste système).
Attention : un nouveau droit privilégié dans le manifeste doit aussi entrer dans la liste
`privapp-permissions-samaos.xml`, sinon Android refuse de démarrer ; relancer alors le script.

Le script pose aussi la surcouche Android de Sama (`systeme/surcouche-android`) : des réglages de
constructeur (pas d'écran « Passage à … » d'Android, le paysage Banco comme fond d'écran par défaut, une loupe
ronde, comme le verre d'une loupe, quand on tire le curseur ou une poignée de sélection, dans toutes les applis,
et la forme des icônes, aux coins doux).

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

**Le mode simplifié** (maquettes quo-04-mode-simplifie, dem-06-pret, l5-acces-demarrage), pour qui découvre le
smartphone : l'heure en grand, trois proches (les contacts marqués d'une étoile) qu'un toucher appelle, six
grandes tuiles (Appeler, Messages, Photo, Argent, Météo, Lampe ; chacune peut devenir une autre appli) avec le
nombre d'appels manqués ou de messages, et « Besoin d'aide ? » (toutes les applis, appel d'urgence, réglages
du mode). En Andika, la police de SIL faite pour qui apprend à lire ; rien à deviner : la Cour ne s'ouvre que par
l'aide, les Espaces ne changent pas à trois doigts, le Pouls reste tiré du haut. Il se choisit à la dernière étape
du premier démarrage (Standard ou Simplifié), dans son Accessibilité, ou dans Réglages › Accessibilité ›
Mode simplifié (une valeur par Espace, `sama_simple` et `sama_simple_tuiles` dans Settings.Secure). En mode
simplifié, aucune appli ne s'installe hors de Sugu : pas même une demande, qui lancerait le délai de 24 heures.

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
maquettes) et la même ossature : tête, lignes, puces, feuilles d'actions, dialogues. Les mouvements sont
communs : une feuille monte d'un ressort sur un voile qui apparaît en fondu, redescend quand on touche le
voile, appuie sur Retour ou la tire vers le bas (ses boutons la referment par `LocalFermerFeuille`) ; un
dialogue s'ouvre en grandissant un peu ; la barre d'onglets flottante s'efface pendant ce temps ;
interrupteurs, cases et puces glissent ou changent de couleur en douceur. Elles sont signées avec
la clé de test de la plateforme et installées comme des applis ordinaires par `outils/sama-systeme.sh`, qui
leur donne leurs droits, leurs rôles (téléphone, SMS) et met de côté les applis d'Android qu'elles
remplacent (sans les effacer : `pm enable` les remet). L'Accueil cache l'icône de ces dernières.

**Les icônes** (validées le 7 octobre 2026) : un fond en dégradé avec un léger reflet et un dessin plein, une
couleur par appli ; certaines sont de petits objets (le cadran de l'Horloge, les touches de la Calculatrice, la
boussole de Griot, le paysage de Photos). Elles sont écrites par `outils/icones.py` (deux vecteurs et une icône
adaptative par appli) : retoucher le script, puis le relancer. L'Accueil montre l'Agenda avec la date du jour,
le menu de la SIM d'Android sous le nom et l'icône « Services SIM », cache la recherche d'Android (la Cour cherche
déjà) et pose une ombre douce sous les icônes. La forme des icônes de tout le téléphone (`config_icon_mask`, coins
d'environ 24 %) vient de la surcouche Android.

| Module | Ce qu'il fait |
| --- | --- |
| `telephone` | Onglets flottants Favoris, Récents, Clavier, Contacts ; clavier à touches rondes sur un « sol » : l'opérateur du numéro tapé, le contact qui correspond, la SIM la moins chère (avec deux SIM) ; appel entrant et en cours (InCallService), messagerie, filtrage, codes USSD ; les numéros des secours sont nommés (SAMU · 185). « Savoir qui appelle », seulement avec l'accord de la personne (écran d'accord au premier lancement) : un inconnu qui appelle est cherché dans une liste gardée sur le téléphone (rien ne part pendant l'appel, les contacts ne sont jamais envoyés) ; « Arnaque signalée » (combien de signalements, pourquoi, le conseil) ou « Entreprise vérifiée », jusque dans les récents et sur le clavier (« Jamais signalé ») ; « Bloquer et signaler » d'un toucher, le signalement reste sur le téléphone jusqu'au service Sama. Sur l'émulateur seulement, une liste d'essai de numéros fictifs (`adb emu gsm call 0759314208`). |
| `contacts` | Liste en blocs par lettre avec favoris, groupes (Famille, Travail, École) et « Ma fiche » (nom, numéro, photo, code QR vCard pour se faire ajouter) ; fiche et création avec photo (prise, choisie) ou avatar fabriqué (initiales, motif, emoji, 8 couleurs), lien (« Maman », « Collègue »), opérateur de chaque numéro, anniversaire, « Où la trouver », « Reconnaître comme proche » ; import de la SIM ; « Choisir un contact / un numéro / une adresse » pour les autres applis (ACTION_PICK, GET_CONTENT), qui ne reçoivent que ce qui a été touché ; fiches reçues (.vcf, vCard 2.1 à 4.0) ajoutées d'un geste, celles qui se font passer pour un opérateur ou un service client décochées et signalées. |
| `messages` | SMS (appli SMS par défaut). La liste : recherche (nom, numéro, mot), les proches en haut (favoris d'abord, cerclés quand ils ont écrit), filtres Tout / Non lus / Inconnus, opérateurs et services dans une carte à part, un point pour le non-lu (pas de pastille chiffrée), l'arnaque probable signalée dès la liste ; glisser pour supprimer, effacé 6,5 s plus tard (« Annuler » le fait revenir). La conversation : messages groupés, jours séparés, « Pas envoyé · touchez pour réessayer », carte pour un inconnu (ajouter, bloquer), et le nombre de SMS (payés chacun) qu'un long message va coûter. Réponse depuis la notification. |
| `horloge` | Alarmes (elles sonnent téléphone en veille) : la prochaine en grand avec le temps qui reste, réglage à la molette, jours et raccourcis (en semaine, week-end), sonnerie au choix, vibration, durée de « Encore » ; glisser pour supprimer, avec « Annuler ». Horloge du monde : cadran à aiguilles, villes avec soleil ou lune, ajout par recherche. Minuteur : trois molettes, minuteurs prêts et derniers, anneau qui se vide. Chrono : lunette, tours (le plus rapide, le plus lent), il continue appli fermée. Coucher : cadran de 24 h où l'on glisse la lune (coucher) et le soleil (lever), alarme au lever. La sonnerie s'arrête en faisant glisser le bouton. |
| `calculatrice` | Calculs exacts, conversion F CFA ↔ € au taux fixe. Refaite le 8 octobre : des galets doux, l'orange de l'icône pour les opérations (celle qu'on vient de toucher reste allumée), un grand affichage aux chiffres fins qui rapetissent pour tenir ; en tapant, l'expression en grand et ce qu'elle vaut déjà en gris ; « = » fait monter l'expression et pose le résultat ; glisser sur l'écran efface le dernier chiffre, un appui long copie le résultat ; ⌫ devient C après « = » (appui long : tout effacer). Pourcentage des commerçants (200 + 15 % = 230). Couchée, la scientifique comme sur l'iPhone : parenthèses, mémoire (mc, m+, m−, mr), 2nd, x², x³, xʸ, eˣ, 10ˣ / 2ˣ, ¹/x, racines carrée, cubique et y-ième, ln, log₁₀ / log₂ / logᵧ, x!, sin cos tan et leurs réciproques, sinh cosh tanh, e, EE, π, Rand, degrés ou radians ; tourner le téléphone ne perd rien (l'écran n'est pas recréé). L'historique des calculs (200 derniers, sur le téléphone) descend du haut : par jour, un toucher reprend le résultat, un appui long le copie. F CFA ⇄ € : une capsule, la conversion sous le résultat (sur la ligne du haut quand l'écran est couché), un toucher change le sens. |
| `meteo` | La Météo (maquette l2-meteo) : prévisions d'Open-Meteo (données CC BY 4.0), sans compte ni identifiant ; seule la ville part, ou la position arrondie à 0,1° (« Près de vous »). Le temps qu'il fait et la pluie qui vient (« Averses en fin d'après-midi »), ressenti, humidité, vent, UV, les six prochaines heures et les cinq jours suivants, la saison en Côte d'Ivoire (sud et nord). Gardées sur le téléphone, lues sans réseau ; nouvelles prévisions à l'ouverture au-delà de 30 min, et la data qu'elles ont coûtée (« pour 2 Ko de data »). Plusieurs villes, cherchées par leur nom. Sur l'émulateur, si les noms de sites ne se résolvent pas (partage de connexion du Mac), lancer l'émulateur avec `-dns-server 1.1.1.1,8.8.8.8`. |
| `notes` | Les Notes, sur le modèle fonctionnel des Notes de l'iPhone, en plus doux (papier chaud, miel). Refaites le 8 octobre : des dossiers (couleur, renommer, supprimer) et trois tuiles (Toutes, Listes, Rappels) ; « Supprimées récemment » (30 jours, récupérer ou supprimer pour de bon) ; la liste par date (Épinglées, Aujourd'hui, Hier, 7 et 30 jours précédents, mois) ou en galerie, triée par modification, création ou titre ; glisser à droite pour épingler, à gauche pour supprimer (Annuler) ; appui long : épingler, déplacer, partager, supprimer ; recherche dans toutes les notes, le mot surligné. Dans une note on mélange titres, intertitres, texte, puces, numéros et cases rondes (Entrée continue la liste, Entrée sur une ligne vide la termine, Effacer en début de ligne retire la puce puis rejoint la ligne d'avant), le panneau « Aa », « Mettre les cochées en bas », « Tout décocher » ; rappels (choix tout faits ou jour et heure à la molette) ; verrou avec le code du téléphone (contenu chiffré par le coffre d'Android, refermé quand on quitte l'appli) ; un code secret écrit dans une note fait proposer le verrou et rappelle de ne jamais le donner. L'ancien carnet (notes texte ou listes) est repris tel quel. |
| `agenda` | Au premier lancement, à quoi sert l'Agenda (un ou plusieurs usages, modifiables) et les onglets suivent. **École** : emploi du temps de la semaine (cours qui reviennent chaque semaine, couleur par matière, pause de midi repérée, « maintenant »), devoirs et contrôles (« pour le prochain cours de maths » en une touche). **Travail** : la journée heure par heure, les tâches à cocher (urgentes, sans date). **Mon cycle** : anneau du cycle (règles, période fertile, ovulation probable), prochaines règles, symptômes du jour ; chiffré par une clé du Keystore, ouvert avec le code du téléphone, refermé dès qu'on quitte l'appli, et rien du cycle sur « Aujourd'hui » tant qu'il est fermé ; les prévisions ne remplacent pas une contraception. Aujourd'hui rassemble tout (cours, rendez-vous, anniversaires lus dans les contacts, devoirs, tâches, cycle) ; le mois, les événements avec rappel et répétition dans un agenda du téléphone ; rappels d'Android (EVENT_REMINDER) et « Ajouter à l'agenda » des autres applis. Pas de dates scolaires officielles inventées : elles viendront par pays, avec les mises à jour. |
| `fichiers` | Place prise par sorte, dossiers, reçus (avec les téléchargements en cours et « Attendre le Wi-Fi au-delà de 50 Mo »), corbeille de 30 jours, Nettoyer (vieilles vidéos reçues, doublons par empreinte, applis inutilisées, fichiers temporaires). Une appli reçue (APK) est signalée et son installation demande une confirmation qui explique le risque. Lecteur de PDF (moteur d'Android : pages, loupe, partage, impression ; aussi pour les autres applis). Archives .zip : contenu, « Extraire ici » (jamais hors du dossier, jamais plus que la place libre ; Android refuse déjà les chemins en « .. »). |
| `photos` | La pellicule jour par jour, les albums, la visionneuse (zoom, vidéos), favoris, corbeille, retouche (recadrer, redresser, lumière, filtres, annoter) enregistrée en copie. Avant de partager une photo qui garde le lieu de la prise de vue, Photos propose de l'envoyer sans le lieu. Refaite le 8 octobre : Pellicule et Collections dans une capsule flottante, la loupe à côté. La pellicule par jour (par mois quand elle est serrée), qu'on pince pour passer de 2 à 3, 5 ou 7 colonnes (les vignettes glissent à leur place) ; « Choisir » (ou un appui long) puis partager, favoris, corbeille. La photo s'ouvre en grandissant depuis sa vignette et y retourne ; on la tire vers le bas pour la refermer ; une bande de vignettes en bas. Collections : ce jour-là (les années d'avant), les albums, par sorte (vidéos, favoris, captures, documents), faire de la place (la place prise, photos et vidéos ; les doublons reconnus à leur contenu, les grosses vidéos, les captures de plus de 30 jours, ce que WhatsApp a gardé ; ce qu'on propose de jeter est déjà choisi, tout part à la corbeille avec « Annuler »), la corbeille. Partager plus léger (1600 pixels, moins de data pour soi et pour qui reçoit, sans les données de la photo) ou tel quel, et enlever le lieu. Recherche par mots simples : vidéos, favoris, captures, un mois, une année, hier, le nom d'un album. Masquées : « Masquer » (dans ⋯ d'une photo, ou sur plusieurs choisies) copie la photo dans l'espace privé de Photos, vérifie la copie, puis seulement la retire de la pellicule : les autres applis (WhatsApp, la galerie d'un curieux, une appli d'espionnage) ne la voient plus. L'album s'ouvre avec le déverrouillage du téléphone (code, schéma, empreinte), se referme dès qu'on le quitte ou qu'on quitte Photos, et n'apparaît pas dans l'aperçu des applis récentes ; un téléphone sans code est signalé. « Afficher dans la pellicule » la remet dans son dossier d'avant, à sa date ; supprimer une photo masquée le demande d'abord (pas de corbeille). |
| `dictaphone` | Enregistrement au premier plan (il continue écran éteint), pause, repères, réécoute avec saut aux repères. |
| `appareil` | L'Appareil photo (Camera2) : photo (retardateur, formats 3:4, 9:16, 1:1, grille, zoom, mise au point au toucher, flash), vidéo (temps restant réel), scanner de QR codes (lien, Wi-Fi, numéro, code d'opérateur… avec mises en garde : lien raccourci, adresse trompeuse, code qui lance un transfert) et de documents (album « Documents scannés »). Touches de volume pour déclencher. Appli système (`/system/app`) : Android ne laisse que l'appareil du système répondre aux applis qui demandent une photo. Sur l'émulateur, les affiches de la scène virtuelle servent à essayer le scanner (`-virtualscene-poster wall=…`). Refait le 8 octobre, loin du modèle Pixel : l'image sur tout l'écran (la barre d'état se cache), les commandes sur un bloc transparent ; le format « Plein écran » (par défaut, retenu) garde exactement ce qu'on voit, les autres (3:4, 9:16, 1:1) font glisser l'image jusqu'à leur cadre ; les modes (Vidéo, Photo, Document, Code QR) sur une bague graduée autour du déclencheur, qu'on tourne du doigt (un cran à chaque mode, le mode choisi sous un repère d'or) ou en glissant l'aperçu ; les réglages en mots dans un bandeau (flash, retardateur, format : un toucher fait défiler la valeur, le sablier se retourne) ; à chaque photo, un diaphragme se ferme sur le déclencheur et le tirage, bordé de crème, quitte le cadre pour se poser sur la pile des vignettes (la dernière photo de l'appareil s'y trouve dès l'ouverture) ; l'aperçu se retourne comme une carte pour changer de caméra ; le zoom : pincer l'image, ou toucher 1×, 2×, 5× — la bague devient alors celle du zoom (repères 1×, 2×, 3×, 5×… qui attirent le doigt, un doublement pour le même tour) et redevient celle des modes seule ; pendant le tournage, l'anneau du déclencheur fait le tour en une minute ; mise au point au toucher et lumière réglée en glissant de haut en bas ; pour une autre appli, « Reprendre » ou le disque d'or « Utiliser la photo » (la photo affichée à l'endroit). |
| `griot` | Le navigateur (WebView du système), navigateur par défaut : onglets, historique et favoris sur le téléphone, recherche DuckDuckGo par défaut, téléchargements par Android (ils arrivent dans Fichiers › Reçus avec le site d'origine), Mode léger (les images se chargent au toucher, compteur d'images évitées). Domaine réel affiché, adresses en lettres d'autres alphabets montrées sous leur vraie forme, pages non chiffrées signalées, certificats douteux refusés, appli à télécharger précédée d'une mise en garde. Les positions (geo:) des autres applis s'ouvrent sur OpenStreetMap. |
| `mail` | Le Mail (IMAP et SMTP, avec Jakarta Mail) : la boîte de la maquette (recherche, Non lus, Pièces jointes), dossiers, plusieurs boîtes, lecture, réponse, transfert, corbeille, `mailto:` et « Envoyer par mail ». Serveurs trouvés tout seuls (base publique de Thunderbird, puis configuration publiée par le domaine), sinon à la main ; mot de passe chiffré par la puce de sécurité ; pas de connexion en clair vers Internet. Sobre en data : seulement les parties texte, pièces jointes à la demande (confirmation au-delà de 5 Mo sur data), images à distance bloquées. Un mail écrit sans réseau part au retour de la connexion. Bouclier : faux mails d'opérateur (nom affiché sans le domaine officiel), liens piégés ou trompeurs (bloqués), appli en pièce jointe (bloquée), demande de code ; HTML sans JavaScript. Essai : `outils/mail-essai.py` (serveur GreenMail local, comptes fictifs). Refait le 8 octobre, loin de Gmail : on arrive dans la Réception (grand titre, nombre de non lus), « ‹ Boîtes » mène aux dossiers de chaque adresse, à ce qui attend sur le téléphone et à « Ajouter une boîte » ; recherche, un sélecteur Tous / Non lus / Pièces jointes dont le galet glisse ; les mails par jour, un point latérite pour les non lus (plus d'avatars ronds), le mot du bouclier dans la ligne (« Faux mail · ne répondez pas ») ; glisser à droite : lu / non lu, à gauche : archiver ou corbeille avec « Annuler » ; en bas, relever, l'état de la relève (« Hors ligne · … », « Relevé à 14:05 ») et écrire. Lecture : objet en grand, l'expéditeur (« à moi », un toucher pour les détails), la carte du bouclier, les pièces jointes ; une barre en bas (archiver, corbeille, non lu) et « Répondre » (répondre, à tous, transférer) — pour un mail signalé, l'action principale devient « Jeter ce mail ». Écrire monte du bas : « Annuler » garde le brouillon, joindre, envoyer. Les adresses : en haut à droite de la Réception, l'adresse lue (« awa@… ⌄ ») ouvre « Vos adresses mail » (passer de l'une à l'autre, avec ses non lus ; ajouter une adresse ; les réglages) ; Réglages : chaque adresse (ses serveurs, vérifier la connexion, changer le mot de passe ou le nom affiché, la retirer), la relève, les images. |
| `clavier` | Le clavier de Sama (maquettes l3-clavier, l1-emoji, l1-dictee), clavier par défaut du téléphone : AZERTY aux couleurs de Banco (jour, nuit, plein soleil), **julakan** (AZERTY avec ɛ ɔ ɲ ŋ et les tons, ton bas et ton haut posés sur la voyelle) et QWERTY ; appui long pour les accents (é è ê ë ɛ…) ; une seule touche de ponctuation (le point, la virgule et les autres à l'appui long) et, à sa place, la touche de langue (🌐, un toucher passe à la suivante, l'appui long les montre toutes ; la langue choisie est gardée) ; l'espace maintenue ou glissée devient un pavé qui déplace le curseur, aussi de ligne en ligne dans un texte long ; symboles, emoji (teintes de peau d'ici), pavé des numéros avec \* # + pour les codes ; propositions (mots courants du français et mots appris), correction prudente des seuls accents oubliés (« tres » → « très », effacer la défait) ; deux espaces font un point. **Sans accès à Internet** ; rien n'est retenu ni proposé dans un mot de passe ou un code. Réglages › Clavier : langues, propositions, correction, vibration, mots appris (nombre, oublier). La dictée attend un moteur de reconnaissance hors ligne. |
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
commune des numéros signalés (serveur).

**Un proche veille sur vous** (protéger aussi un proche) : dans Réglages › Bouclier, la personne choisit
elle-même un proche reconnu (Proche en proche, par code QR) et son numéro. Quand son bouclier arrête une arnaque
grave (faux SMS ou faux mail d'opérateur, appel suspect, transfert pendant l'appel d'un inconnu, faux site,
demande d'installation hors de Sugu, fausse mise à jour), les Réglages envoient au proche un SMS court : le type
d'arnaque seulement, jamais le contenu, les numéros ni le solde ; trois au plus par jour, une fois par heure pour
la même. Le SMS finit par une signature (`#SA1.<heure>.<code>.<preuve>`, HMAC du secret partagé) : Messages, chez
le proche, la vérifie, notifie « Alerte du bouclier · Koffi » (avec « Appeler ») et marque la bulle
« Alerte vérifiée » ; une imitation est signalée « Fausse alerte ». « Envoyer un essai » vérifie le chemin ; on
arrête quand on veut. Sur l'émulateur, Android ne fait pas passer les SMS d'un émulateur à l'autre : le SMS
parti se relit dans `content://sms` et se remet au second avec `adb emu sms send`.

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

## Sugu, le magasin d'applis (maquettes l2-sug-accueil, l2-sug-fiche, l2-sug-maj, i6-sugu-proches)

Sugu montre et installe les applis signées par Sama ou par Sugu. Pour l'instant, il les trouve sur les
**points Sama** à portée (Proche en proche, sans data) ; le catalogue en ligne viendra avec le serveur de Sama.
Tout ce qu'il affiche est signé : la fiche (ce qui sera installé) et la **vitrine** (éditeur, description,
catégorie, « marche sans connexion », icône, ce que l'appli demande). Un point ne peut donc pas faire passer
une appli pour une autre en changeant sa description.

- **Découvrir** : recherche, filtres (Sans data, Légères, catégories), « Près de vous, sans data », les applis par
  catégorie avec Installer, Mettre à jour ou Ouvrir.
- **Fiche** : version, taille, hors ligne ou non, ce que l'appli demande, d'où elle vient ; l'avancement de
  l'installation, qu'on peut arrêter (elle reprend où elle s'était arrêtée).
- **Autour** : chaque point Sama et ses applis, l'ajout d'un point par son adresse, les points bloqués (et
  « Débloquer »), la data économisée.
- **Mises à jour** : celles que proposent les points, « Tout mettre à jour », « Attendre le Wi-Fi ».
- **Mes applis** : celles installées par Sugu, et celles **venues d'ailleurs** (« Hors Sugu »), reconnues à leur
  signature et non à leur nom : une fausse appli qui s'appelle `africa.samaos.…` y apparaît quand même.
- **Fichier refusé** : un fichier qui ne correspond pas à sa fiche est supprimé sans rien installer ; on peut
  ne plus rien recevoir de ce point.

- **Entre proches** : « Ouvrir à mes proches » (10 minutes, notification avec « Fermer ») ; « Chez Koffi »
  montre ce qu'un proche ouvert peut donner, mises à jour comprises ; « Envoyer à un proche » sur la fiche d'une
  appli qu'on a : le proche reçoit « Koffi vous envoie Dictaphone » et choisit de recevoir.

Sugu est une appli système (`/system/priv-app/SamaSugu`) : il installe sans seconde question ce qu'il a vérifié
(droit INSTALL_PACKAGES), et Android le reconnaît comme magasin (`APP_MARKET`).

## Proche en proche (innovation 6, premier pas)

Recevoir sans data, depuis un **point Sama** du réseau local, les applis signées par Sama ou Sugu. La
bibliothèque `proches` vérifie la fiche signée de l'éditeur, la version (jamais plus ancienne), le fichier entier
(taille, empreinte) et le certificat de l'appli ; un fichier qui ne correspond pas est supprimé sans rien
installer (« Fichier refusé »). C'est Sugu qui reçoit et installe. Le protocole est décrit dans
[proches/PROTOCOLE.md](proches/PROTOCOLE.md).

- **Réglages › Proche en proche** : les points Sama connus (« ouvrir dans Sugu »), l'ajout d'un point par son
  adresse, la fenêtre ouverte aux proches, la reconnaissance des proches (« Oublier »), ce qu'on accepte de
  donner, le bilan. Les proches et leurs secrets restent dans les Réglages (`ProchesFournisseur`).
- **Fichiers** : une appli reçue qui se dit « mise à jour » ou qui porte le nom d'une appli de Sama ouvre
  « Ce n'est pas une mise à jour » ; « Supprimer » la met à la corbeille.

Essai sur l'émulateur (la clé d'éditeur de test se crée avec `systeme/cles-proches/fabriquer.sh`) :

```bash
mobile/outils/signer-paquet.py mobile/dictaphone/build/outputs/apk/release/dictaphone-release.apk --nom Dictaphone --editeur-nom Sama --resume "Enregistrer un cours, une réunion" --categorie ecole --hors-ligne --sans-traceur --droit "Le micro, pour enregistrer"
```

```bash
mobile/outils/point-sama.py --nom "Lycée du quartier" --debit 200
```

puis Sugu › Autour › « Ajouter un point par son adresse » › `10.0.2.2:8765`. Pour proposer une mise à jour,
signer une version plus récente : `./gradlew :calculatrice:assembleRelease -PversionCode=2 -PversionName=0.2`.
**Se reconnaître entre proches** : Réglages › Proche en proche › « Reconnaître un proche » montre le code QR du
téléphone (clé créée dans la puce de sécurité, ni numéro ni compte) ; on scanne celui du proche, on compare à voix
haute le code de vérification à 8 chiffres, puis il fait de même. Un code « proche » scanné avec l'Appareil photo
propose aussi « Reconnaître ».

**Entre téléphones**, sur deux émulateurs reliés au même Wi-Fi (le second, `Sama2`, est une copie de la
configuration de `Sama`, préparée par `outils/sama-systeme.sh` avec `ANDROID_SERIAL=emulator-5556`) :

```bash
~/Library/Android/sdk/emulator/emulator -avd Sama -writable-system -no-snapshot-load -wifi-server-port 9999
```

```bash
~/Library/Android/sdk/emulator/emulator -avd Sama2 -port 5556 -writable-system -no-snapshot-load -wifi-client-port 9999
```

Pour se reconnaître sans caméra, chaque émulateur ouvre le code de l'autre, comme après un scan : le code se lit
avec `adb shell content call --uri content://africa.samaos.reglages.proches --method code`, puis
`adb -s <l'autre> shell "am start -a android.intent.action.VIEW -d '<code>'"`. Ensuite, Sugu › Autour ›
« Ouvrir à mes proches » des deux côtés. Les émulateurs sont débranchés : `adb shell dumpsys battery set ac 1`
pour que les applis soient montrées aux proches. Entre proches, tout est chiffré (AES-GCM, clés tirées du secret
partagé, une par requête) : sur le Wi-Fi, un voisin ne voit ni les applis, ni les catalogues, ni les fichiers
(`proches/PROTOCOLE.md`, « L'enveloppe »). Pour le vérifier, `tcpdump` existe sur l'émulateur (après `adb root`) :
`tcpdump -i wlan0 -w /data/local/tmp/cap.pcap tcp and not port 5555`. Le Wi-Fi Direct (sans routeur) et le point
Sama du bureau restent à faire.

## Contenu

Dans `banco` : `Banco.kt` (palettes Aube, Nuit, Savane, Plein soleil ; polices), `Paysage.kt` (ciel, astre,
collines, crête), `Icones.kt`, `Espace.kt` (paysages et couleurs d'Espace), `Composants.kt` (gabarit des
réglages, rangées, pastilles, cases, interrupteur, boutons…).

Dans `accueil` :
- `Panneau.kt` : un panneau qui suit le doigt (la Cour, le Pouls), avec élan et défilement imbriqué.
- `Accueil.kt` : l'heure, la date, l'Espace actif, les widgets, les applis et la Natte ; les gestes.
- `AccueilSimple.kt` : l'Accueil du mode simplifié (proches, six tuiles, « Besoin d'aide ? »).
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
