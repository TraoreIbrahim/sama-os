// Première ligne de Sama Sheet : les menus (tout y est, rangé comme on l'attend), « Que voulez-vous faire ? » au
// milieu, et Enregistrer.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: barre
    implicitHeight: 42
    color: Couleurs.fond
    property alias recherche: recherche

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 8
        anchors.rightMargin: 12
        spacing: 8

        QQC2.MenuBar {
            id: menus
            Layout.alignment: Qt.AlignVCenter
            background: null
            delegate: QQC2.MenuBarItem {
                id: titreMenu
                implicitHeight: 28
                leftPadding: 9
                rightPadding: 9
                background: Rectangle {
                    radius: 7
                    color: titreMenu.highlighted || titreMenu.down ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.25 : 0.07) : "transparent"
                }
                contentItem: Text {
                    text: titreMenu.text
                    font.pixelSize: 13
                    color: Couleurs.texte
                    verticalAlignment: Text.AlignVCenter
                }
            }

            MenuSama {
                id: menuFichier
                title: "Fichier"
                ElementMenu { cle: "nouveau" }
                ElementMenu { cle: "modeles" }
                ElementMenu { cle: "ouvrir" }
                TitreMenu { text: "Enregistrer" }
                ElementMenu { cle: "enregistrer" }
                ElementMenu { cle: "enregistrerSous" }
                ElementMenu { cle: "pdf" }
                TitreMenu { text: "" ; implicitHeight: 9 }
                ElementMenu { cle: "fermer" }
            }
            MenuSama {
                id: menuEdition
                title: "Édition"
                TuilesMenu { menu: menuEdition; cles: ["annuler", "retablir", "couper", "copier", "coller"] }
                ElementMenu { cle: "collerValeurs" }
                TitreMenu { text: "Supprimer" }
                ElementMenu { cle: "effacer" }
                ElementMenu { cle: "supprimerLigne" }
                ElementMenu { cle: "supprimerColonne" }
                TitreMenu { text: "" ; implicitHeight: 9 }
                ElementMenu { cle: "toutSelectionner" }
            }
            MenuSama {
                id: menuAffichage
                title: "Affichage"
                ElementMenu { cle: "formules" }
                TitreMenu { text: "Garder visible en défilant" }
                ElementMenu { cle: "figerLigne" }
                ElementMenu { cle: "figerColonne" }
                ElementMenu { cle: "figer" }
                TitreMenu { text: "Zoom" }
                ElementMenu { cle: "zoomPlus" }
                ElementMenu { cle: "zoomMoins" }
                ElementMenu { cle: "zoom100" }
            }
            MenuSama {
                id: menuInsertion
                title: "Insertion"
                TitreMenu { text: "Une ligne, une colonne"; trait: false }
                TuilesMenu { menu: menuInsertion; cles: ["ligneAvant", "ligneApres", "colonneAvant", "colonneApres"] }
                ElementMenu { cle: "feuille" }
                TitreMenu { text: "Un graphique, à partir des cases choisies" }
                TuilesMenu { menu: menuInsertion; cles: ["grColonnes", "grBarres", "grLignes", "grSecteurs", "grAires"] }
                TitreMenu { text: "Un calcul sur les cases au-dessus" }
                TuilesMenu { menu: menuInsertion; cles: ["somme", "moyenne", "minimum", "maximum", "nombre"] }
                TitreMenu { text: "" ; implicitHeight: 9 }
                ElementMenu { cle: "date" }
            }
            MenuSama {
                id: menuFormat
                title: "Format"
                TuilesMenu { menu: menuFormat; sansNom: true; cles: ["gras", "italique", "souligne", "barre"] }
                TitreMenu { text: "Nombre" }
                PastillesMenu { menu: menuFormat; cles: ["formatStandard", "nombreFormat", "fcfa", "pourcentage", "dateFormat", "heureFormat", "decimalesPlus", "decimalesMoins"] }
                TitreMenu { text: "Alignement" }
                TuilesMenu { menu: menuFormat; sansNom: true; cles: ["gauche", "centre", "droite", "haut", "milieu", "bas"] }
                ElementMenu { cle: "retourLigne" }
                ElementMenu { cle: "fusionner" }
                ElementMenu { cle: "largeur" }
                TitreMenu { text: "Couleurs et traits" }
                TuilesMenu { menu: menuFormat; cles: ["couleurTexte", "remplissage", "bordures"] }
                TitreMenu { text: "" ; implicitHeight: 9 }
                ElementMenu { cle: "pinceau" }
                ElementMenu { cle: "effacerFormat" }
            }
            MenuSama {
                id: menuDonnees
                title: "Données"
                ElementMenu { cle: "tableau" }
                ElementMenu { cle: "fiches" }
                ElementMenu { cle: "ligneTableau" }
                ElementMenu { cle: "totauxTableau" }
                TitreMenu { text: "" ; implicitHeight: 9 }
                ElementMenu { cle: "analyse" }
                TitreMenu { text: "Trier" }
                ElementMenu { cle: "trierAZ" }
                ElementMenu { cle: "trierZA" }
            }
            MenuSama {
                id: menuAide
                title: "Aide"
                ElementMenu { cle: "chercher" }
                ElementMenu { cle: "aide" }
            }
        }

        Item { Layout.fillWidth: true }
        Recherche {
            id: recherche
            Layout.preferredWidth: Math.min(420, barre.width * 0.36)
        }
        Item { Layout.fillWidth: true }

        Outil {
            text: "Enregistrer"
            picto: "M5 4h11l3 3v13H5z M8 4v5h7V4 M8 20v-6h8v6"
            aide: "Enregistrer (Ctrl+S)"
            encre: fenetre.accentEncre
            background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.2) : fenetre.accentFond }
            onClicked: fenetre.enregistrer()
        }
    }
}
