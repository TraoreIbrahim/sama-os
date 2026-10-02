// Disposition par défaut du bureau Sama OS (exécutée à la première ouverture de session).
// Une seule barre flottante, centrée en bas : la Natte.
//   La Cour (éléphant)  |  Espaces + applications (widget Sama)  |  Corbeille  |  le Pouls

var natte = new Panel;
natte.location = "bottom";
natte.floating = true;
natte.alignment = "center";
natte.lengthMode = "fit";
natte.hiding = "none";
natte.height = Math.round(gridUnit * 3.2);

// La Cour : lanceur plein écran de Sama (toutes les applications et recherche unifiée)
natte.addWidget("org.samaos.cour");

// Les Espaces de Sama, leurs applications et la Corbeille
natte.addWidget("org.samaos.natte");

// Le Pouls : langue, réseau, data, batterie et heure dans une capsule ; un clic ouvre le panneau de contrôle
natte.addWidget("org.samaos.pouls");

// Fond d'écran Sama sur tous les bureaux
var bureaux = desktops();
for (var i = 0; i < bureaux.length; i++) {
    var bureau = bureaux[i];
    bureau.wallpaperPlugin = "org.kde.image";
    bureau.currentConfigGroup = ["Wallpaper", "org.kde.image", "General"];
    bureau.writeConfig("Image", "file:///usr/share/wallpapers/SamaAube/");

    // Cartes du bureau, en haut à gauche : heure et salutation, puis data consommée
    var marge = Math.round(gridUnit * 1.5);
    var gauche = Math.round(gridUnit * 7);
    var largeur = Math.round(gridUnit * 17);
    bureau.addWidget("org.samaos.carte.heure", gauche, marge, largeur, Math.round(gridUnit * 10.5));
    bureau.addWidget("org.samaos.carte.data", gauche, marge + Math.round(gridUnit * 11.2), largeur, Math.round(gridUnit * 6.5));
}

// Bureau verrouillé : pas de poignées ni de déplacements accidentels des cartes
// (déverrouillage possible depuis le menu du clic droit sur le bureau).
locked = true;
