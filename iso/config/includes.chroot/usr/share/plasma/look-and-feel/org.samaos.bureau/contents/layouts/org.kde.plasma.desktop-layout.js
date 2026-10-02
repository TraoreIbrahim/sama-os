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

// La Cour : lanceur avec recherche (applications, fichiers, réglages).
// Provisoire : le lanceur standard de Plasma, en attendant la Cour plein écran de Sama.
var cour = natte.addWidget("org.kde.plasma.kickoff");
cour.currentConfigGroup = ["General"];
cour.writeConfig("icon", "/usr/share/samaos/logo-cour.svg");

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
}
