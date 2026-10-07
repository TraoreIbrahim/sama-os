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
                ElementMenu { cle: "envoyer" }
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
                TitreMenu { text: "" ; implicitHeight: 9 }
                ElementMenu { cle: "commentaires"; coche: fenetre.panneauCommentaires }
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
                ElementMenu { cle: "commentaire" }
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
                ElementMenu { cle: "formulaire" }
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

        // Les commentaires (maquette « En-tête Sheet ») : le panneau, et leur nombre
        Outil {
            id: boutonCommentaires
            Layout.preferredWidth: 32
            Layout.preferredHeight: 32
            picto: "M4 5h16v11H9l-5 4z"
            actif: fenetre.panneauCommentaires
            aide: fenetre.commentaires.liste.length ? fenetre.commentaires.liste.length + (fenetre.commentaires.liste.length > 1 ? " commentaires" : " commentaire") : "Commentaires"
            onClicked: fenetre.voirCommentaires(!fenetre.panneauCommentaires)
            Rectangle {
                visible: fenetre.commentaires.liste.length > 0
                x: parent.width - width + 1
                y: 1
                width: Math.max(15, nombreCommentaires.implicitWidth + 8)
                height: 15
                radius: 7.5
                color: "#E2A62B"
                border.width: 1.5
                border.color: Couleurs.fond
                Text { id: nombreCommentaires; anchors.centerIn: parent; text: fenetre.commentaires.liste.length; font.pixelSize: 9; font.weight: Font.Bold; color: "#3B2A08" }
            }
        }

        // Envoyer (maquettes) : le tableau en image, à partager
        QQC2.AbstractButton {
            id: boutonEnvoyer
            Layout.preferredHeight: 30
            leftPadding: 12
            rightPadding: 14
            hoverEnabled: true
            focusPolicy: Qt.NoFocus
            onClicked: fenetre.envoyer()
            QQC2.ToolTip.visible: hovered
            QQC2.ToolTip.delay: 600
            QQC2.ToolTip.text: "Le tableau en image, à envoyer (sans les numéros de téléphone)"
            background: Rectangle { radius: 8; color: boutonEnvoyer.down ? Qt.darker(fenetre.accent, 1.2) : boutonEnvoyer.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent }
            contentItem: Row {
                spacing: 6
                Picto { anchors.verticalCenter: parent.verticalCenter; width: 14; height: 14; trace: "M21 3L10 14 M21 3l-7 18-4-7-7-4z"; encre: "#FFFFFF"; trait: 1.9 }
                Text { anchors.verticalCenter: parent.verticalCenter; text: "Envoyer"; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#FFFFFF" }
            }
        }
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
