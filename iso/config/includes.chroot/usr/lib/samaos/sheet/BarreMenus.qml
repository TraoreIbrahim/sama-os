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
                title: "Fichier"
                ElementMenu { cle: "nouveau" }
                ElementMenu { cle: "ouvrir" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "enregistrer" }
                ElementMenu { cle: "enregistrerSous" }
                ElementMenu { cle: "pdf" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "fermer" }
            }
            MenuSama {
                title: "Édition"
                ElementMenu { cle: "annuler" }
                ElementMenu { cle: "retablir" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "couper" }
                ElementMenu { cle: "copier" }
                ElementMenu { cle: "coller" }
                ElementMenu { cle: "collerValeurs" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "effacer" }
                ElementMenu { cle: "supprimerLigne" }
                ElementMenu { cle: "supprimerColonne" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "toutSelectionner" }
            }
            MenuSama {
                title: "Affichage"
                ElementMenu { cle: "formules" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "figerLigne" }
                ElementMenu { cle: "figerColonne" }
                ElementMenu { cle: "figer" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "zoomPlus" }
                ElementMenu { cle: "zoomMoins" }
                ElementMenu { cle: "zoom100" }
            }
            MenuSama {
                title: "Insertion"
                ElementMenu { cle: "ligneAvant" }
                ElementMenu { cle: "ligneApres" }
                ElementMenu { cle: "colonneAvant" }
                ElementMenu { cle: "colonneApres" }
                ElementMenu { cle: "feuille" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "graphique" }
                ElementMenu { cle: "date" }
                MenuSama {
                    title: "Calcul automatique"
                    ElementMenu { cle: "somme" }
                    ElementMenu { cle: "moyenne" }
                    ElementMenu { cle: "minimum" }
                    ElementMenu { cle: "maximum" }
                    ElementMenu { cle: "nombre" }
                }
            }
            MenuSama {
                title: "Format"
                ElementMenu { cle: "gras" }
                ElementMenu { cle: "italique" }
                ElementMenu { cle: "souligne" }
                ElementMenu { cle: "barre" }
                QQC2.MenuSeparator {}
                MenuSama {
                    title: "Nombre"
                    ElementMenu { cle: "formatStandard" }
                    ElementMenu { cle: "nombreFormat" }
                    ElementMenu { cle: "fcfa" }
                    ElementMenu { cle: "pourcentage" }
                    ElementMenu { cle: "dateFormat" }
                    ElementMenu { cle: "heureFormat" }
                    QQC2.MenuSeparator {}
                    ElementMenu { cle: "decimalesPlus" }
                    ElementMenu { cle: "decimalesMoins" }
                }
                MenuSama {
                    title: "Alignement"
                    ElementMenu { cle: "gauche" }
                    ElementMenu { cle: "centre" }
                    ElementMenu { cle: "droite" }
                    QQC2.MenuSeparator {}
                    ElementMenu { cle: "haut" }
                    ElementMenu { cle: "milieu" }
                    ElementMenu { cle: "bas" }
                }
                ElementMenu { cle: "retourLigne" }
                ElementMenu { cle: "fusionner" }
                ElementMenu { cle: "largeur" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "couleurTexte" }
                ElementMenu { cle: "remplissage" }
                ElementMenu { cle: "bordures" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "pinceau" }
                ElementMenu { cle: "effacerFormat" }
            }
            MenuSama {
                title: "Données"
                ElementMenu { cle: "tableau" }
                ElementMenu { cle: "totauxTableau" }
                ElementMenu { cle: "ligneTableau" }
                QQC2.MenuSeparator {}
                ElementMenu { cle: "trierAZ" }
                ElementMenu { cle: "trierZA" }
            }
            MenuSama {
                title: "Aide"
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
            encre: fenetre.vertEncre
            background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.2) : fenetre.vertFond }
            onClicked: fenetre.enregistrer()
        }
    }
}
