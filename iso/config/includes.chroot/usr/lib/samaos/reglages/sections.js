// Sections des Réglages de Sama : partagées par la fenêtre des Réglages et par la recherche de la Cour.
// id, titre, pictogramme (grille 24, trait 1.7), page QML, mots-clés pour la recherche, détail (sous le titre)
.pragma library

var sections = [
    { id: "reseau", titre: "Réseau et Internet", picto: "M5 10a10 10 0 0 1 14 0 M8 13.5a5.5 5.5 0 0 1 8 0 M12 17.5h.01", page: "Reseau", mots: "wifi wi-fi internet vpn proxy connexion", detail: "Wi-Fi, connexions, partage depuis le téléphone" },
    { id: "bluetooth", titre: "Bluetooth et appareils", picto: "M7 7l10 10l-5 5V2l5 5L7 17", page: "Bluetooth", mots: "bluetooth casque souris clavier appareils", detail: "Casques, souris, claviers et téléphones" },
    { id: "affichage", titre: "Affichage", picto: "M3 5h18v11H3z M8 20h8 M12 16v4", page: "Affichage", mots: "écran résolution échelle luminosité sombre clair apparence nuit fond", detail: "Résolution, luminosité, clair ou sombre, fond d'écran" },
    { id: "son", titre: "Son", picto: "M4 9h4l5-4v14l-5-4H4z M16 9a4 4 0 0 1 0 6 M18.5 6.5a8 8 0 0 1 0 11", page: "Son", mots: "son volume haut-parleur micro casque", detail: "Volume, haut-parleurs et micro" },
    { id: "langue", titre: "Langue et région", picto: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z", page: "Langue", mots: "langue français anglais swahili clavier heure date fuseau région", detail: "Langue, clavier, heure et formats" },
    { id: "comptes", titre: "Comptes", picto: "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M4 20a8 8 0 0 1 16 0", page: "Comptes", mots: "compte utilisateur mot de passe photo", detail: "Votre compte et les autres personnes" },
    { id: "confidentialite", titre: "Confidentialité", picto: "M12 3l7 3v5c0 4.5-3 8.5-7 10c-4-1.5-7-5.5-7-10V6z", page: "Confidentialite", mots: "confidentialité vie privée historique localisation", detail: "Historique, localisation, autorisations" },
    { id: "data", titre: "Data et mises à jour", picto: "M5 19c0-8 5-13 14-14c-1 9-6 14-14 14z M5 19l7-7", page: "Data", mots: "data forfait économie mises à jour mobile", detail: "Suivre mon budget data, mises à jour" },
    { id: "energie", titre: "Énergie", picto: "M13 3L5 14h6l-1 7l8-11h-6z", page: "Energie", mots: "énergie batterie veille performance économie", detail: "Batterie, veille et performances" },
    { id: "imprimantes", titre: "Imprimantes", picto: "M7 9V4h10v5 M5 9h14v7h-3 M8 16H5V9 M8 13h8v7H8z", page: "Imprimantes", mots: "imprimante imprimer scanner", detail: "Ajouter une imprimante, imprimante par défaut" },
    { id: "sauvegarde", titre: "Sauvegarde", picto: "M7 18a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z M12 11v5 M9.5 13.5L12 11l2.5 2.5", page: "Sauvegarde", mots: "sauvegarde grenier cloud copie", detail: "Copies de vos documents dans Sama Grenier" },
    { id: "accessibilite", titre: "Accessibilité", picto: "M12 4.5a1.5 1.5 0 1 0 0 .01 M5 8h14 M12 8v6 M9 21l3-7l3 7", page: "Accessibilite", mots: "accessibilité texte contraste lecteur loupe", detail: "Taille du texte, loupe, lecteur d'écran" },
    { id: "organisation", titre: "Organisation", picto: "M4 20V8l8-4l8 4v12 M9 20v-6h6v6 M4 20h16", page: "Organisation", mots: "organisation école entreprise administration gestion parc", detail: "Ordinateurs gérés par une école ou une entreprise" },
    { id: "apropos", titre: "À propos", picto: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8h.01", page: "APropos", mots: "à propos version système ordinateur processeur mémoire mises à jour", detail: "Version de Sama, ordinateur, mises à jour" }
]

// Sous-pages trouvées aussi par la recherche de la Cour : « cible » = section/sous-page (sama-reglages cible)
var raccourcis = [
    { cible: "reseau/vpn", section: "reseau", titre: "VPN", detail: "Réseau privé de l'entreprise ou de l'école", mots: "vpn openvpn wireguard entreprise école tunnel" },
    { cible: "reseau/proxy", section: "reseau", titre: "Proxy", detail: "Passerelle imposée par certaines administrations", mots: "proxy passerelle mandataire" },
    { cible: "reseau/partage", section: "reseau", titre: "Partage de connexion", detail: "Internet depuis le téléphone, par câble ou Bluetooth", mots: "partage connexion téléphone modem usb point d'accès hotspot" },
    { cible: "langue/clavier", section: "langue", titre: "Clavier", detail: "Dispositions du clavier (AZERTY, QWERTY…)", mots: "clavier azerty qwerty disposition touches" },
    { cible: "comptes/monCompte", section: "comptes", titre: "Mon compte", detail: "Photo, nom affiché et mot de passe", mots: "mon compte photo nom mot de passe profil" },
    { cible: "comptes/nouvelUtilisateur", section: "comptes", titre: "Ajouter un utilisateur", detail: "Un compte pour une autre personne", mots: "ajouter utilisateur nouveau compte personne famille" }
]
