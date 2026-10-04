// Disposition par défaut du bureau Sama OS (exécutée à la première ouverture de session).
// Une seule barre flottante, centrée en bas : la Natte.
//   La Cour (éléphant)  |  Espaces + applications (widget Sama)  |  Corbeille  |  le Pouls

var natte = new Panel;
natte.location = "bottom";
natte.floating = true;
natte.alignment = "center";
natte.lengthMode = "fit";
natte.hiding = "none";
// 60 px comme la maquette : 8 px de marge (thème) autour des éléments de 44 px
natte.height = 60;

// La Cour : lanceur plein écran de Sama (toutes les applications et recherche unifiée)
natte.addWidget("org.samaos.cour");

// Les Espaces de Sama, leurs applications et la Corbeille
var natteEspaces = natte.addWidget("org.samaos.natte");
// Méta+Tab : vue d'ensemble des Espaces
natteEspaces.globalShortcut = "Meta+Tab";

// Le Pouls : langue, réseau, data, batterie et heure dans une capsule ; un clic ouvre le panneau de contrôle
natte.addWidget("org.samaos.pouls");

// Fond d'écran Sama sur tous les bureaux
var bureaux = desktops();
for (var i = 0; i < bureaux.length; i++) {
    var bureau = bureaux[i];
    bureau.wallpaperPlugin = "org.kde.image";
    bureau.currentConfigGroup = ["Wallpaper", "org.kde.image", "General"];
    bureau.writeConfig("Image", "file:///usr/share/wallpapers/SamaAube/");

    // Cartes du bureau, en haut à gauche : heure et salutation, data consommée, météo, agenda
    var marge = Math.round(gridUnit * 1.5);
    var gauche = Math.round(gridUnit * 7);
    var largeur = Math.round(gridUnit * 17);
    // (si Plasma les dépose ailleurs au premier démarrage, la Natte les remet en colonne : preparer-espace.sh ranger)
    var y = marge;
    [["org.samaos.carte.heure", 10.5], ["org.samaos.carte.data", 6.5],
     ["org.samaos.carte.meteo", 5.5], ["org.samaos.carte.agenda", 9]].forEach(function (c) {
        var h = Math.round(gridUnit * c[1]);
        bureau.addWidget(c[0], gauche, y, largeur, h);
        // Plasma arrondit tailles et positions à sa grille de 16 px : on fait le même arrondi, plus 16 px d'écart
        y += Math.ceil(h / 16) * 16 + 16;
    });
}

// Bureau verrouillé : pas de poignées ni de déplacements accidentels des cartes
// (déverrouillage possible depuis le menu du clic droit sur le bureau).
locked = true;
