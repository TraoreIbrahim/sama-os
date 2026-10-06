// Barre d'outils de Sama Sheet, sur une ligne : historique, zoom, formats de nombre, police, texte, cases,
// alignement, graphique et calculs. Le reste est dans les menus et dans « Que voulez-vous faire ? ». Dans une
// fenêtre étroite, la barre défile de côté au lieu d'être coupée.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtQml.Models
import "../reglages"

Rectangle {
    id: barre
    implicitHeight: 46
    color: Couleurs.fond
    readonly property var doc: fenetre.doc
    readonly property var actions: fenetre.actions

    function ouvrirVolet(nom) {
        if (nom === "graphique") choixGraphique.open()
        else if (nom === "texte") nuancierTexte.open()
        else if (nom === "fond") nuancierFond.open()
        else if (nom === "bordures") choixBordures.open()
    }

    // Couleurs de Sama : rangées du plus clair au plus soutenu
    readonly property var couleursFond: [
        [["#F4F1EC", "Sable"], ["#DDEBE3", "Vert pâle"], ["#FBE3D3", "Latérite pâle"], ["#FCEFC7", "Or pâle"],
         ["#DCE7F3", "Bleu pâle"], ["#EBE2F0", "Prune pâle"], ["#F7DCDC", "Rose pâle"], ["#EAEAEA", "Gris pâle"]],
        [["#D8CFC2", "Sable foncé"], ["#A3D6C1", "Vert tendre"], ["#F0B392", "Latérite claire"], ["#F2D27A", "Or"],
         ["#A8C4E0", "Bleu clair"], ["#C9B5D6", "Prune claire"], ["#E8A9A9", "Rose"], ["#BDBDBD", "Gris"]],
        [["#665E54", "Brun gris"], ["#2F6B57", "Vert forêt"], ["#B5532F", "Latérite"], ["#D9963A", "Ocre"],
         ["#3E6E9E", "Bleu"], ["#7B5E8C", "Prune"], ["#A3322A", "Rouge"], ["#1F1C18", "Noir"]]
    ]
    readonly property var couleursTexte: [
        [["#1F1C18", "Noir"], ["#665E54", "Brun gris"], ["#1F5544", "Vert foncé"], ["#93401F", "Latérite foncée"],
         ["#9A6420", "Ocre foncé"], ["#2B5079", "Bleu foncé"], ["#5B4069", "Prune foncée"], ["#A3322A", "Rouge"]],
        [["#8A8277", "Gris"], ["#2F6B57", "Vert forêt"], ["#B5532F", "Latérite"], ["#D9963A", "Ocre"],
         ["#3E6E9E", "Bleu"], ["#7B5E8C", "Prune"], ["#C98A9B", "Rose"], ["#FFFFFF", "Blanc"]]
    ]

    component Groupe: Rectangle {
        default property alias contenu: rangee.data
        implicitWidth: rangee.implicitWidth + 12
        implicitHeight: 36
        radius: 12
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.2 : 0.045)
        RowLayout {
            id: rangee
            anchors.centerIn: parent
            spacing: 1
        }
    }
    component Trait: Rectangle { width: 1; height: 18; color: Couleurs.bord; Layout.leftMargin: 4; Layout.rightMargin: 4 }
    component Liste: Outil {
        // Bouton qui ouvre une liste (police, taille, zoom) : son texte dans un champ
        id: liste
        property real largeur: 100
        implicitWidth: largeur
        implicitHeight: 28
        background: Rectangle { radius: 7; color: Couleurs.champ; border.width: 0.5; border.color: liste.hovered ? fenetre.accent : Couleurs.bord }
        contentItem: RowLayout {
            spacing: 4
            Text { Layout.fillWidth: true; Layout.leftMargin: 8; text: liste.text; font.pixelSize: 13; color: Couleurs.texte; elide: Text.ElideRight }
            Text { Layout.rightMargin: 7; text: "▾"; font.pixelSize: 9; color: Couleurs.texte2 }
        }
    }

    Flickable {
        id: defileur
        anchors.fill: parent
        anchors.leftMargin: 10
        anchors.rightMargin: 10
        contentWidth: Math.max(width, outils.implicitWidth)
        contentHeight: height
        flickableDirection: Flickable.HorizontalFlick
        boundsBehavior: Flickable.StopAtBounds
        interactive: contentWidth > width
        clip: true

        RowLayout {
            id: outils
            height: defileur.height
            spacing: 8

            Groupe {
                Outil { picto: "M9 14L4 9l5-5 M4 9h10a6 6 0 0 1 0 12h-3"; aide: "Annuler (Ctrl+Z)"; enabled: actions.peutAnnuler || doc.etats[".uno:Undo"] === "enabled"; onClicked: actions.lancer("annuler") }
                Outil { picto: "M15 14l5-5-5-5 M20 9H10a6 6 0 0 0 0 12h3"; aide: "Rétablir (Ctrl+Y)"; enabled: actions.peutRetablir || doc.etats[".uno:Redo"] === "enabled"; onClicked: actions.lancer("retablir") }
                Outil { picto: "M5 4h11v5H5z M16 6h3v5h-7v3 M12 14v6"; aide: "Reproduire la mise en forme : cliquez ensuite sur les cases à mettre pareil"; actif: fenetre.actif(".uno:FormatPaintbrush"); onClicked: actions.lancer("pinceau") }
                Trait {}
                Liste {
                    id: choixZoom
                    largeur: 76
                    text: Math.round(doc.zoom * 100) + " %"
                    aide: "Zoom"
                    onClicked: menuZoom.popup(choixZoom, 0, choixZoom.height + 4)
                    MenuSama {
                        id: menuZoom
                        implicitWidth: 140
                        Instantiator {
                            model: [50, 75, 100, 125, 150, 200]
                            onObjectAdded: (i, objet) => menuZoom.insertItem(i, objet)
                            onObjectRemoved: (i, objet) => menuZoom.removeItem(objet)
                            ElementMenu { text: modelData + " %"; onTriggered: doc.zoom = modelData / 100 }
                        }
                    }
                }
                Trait {}
                Outil {
                    id: fcfa
                    text: "F CFA"
                    police.bold: true
                    police.pixelSize: 12
                    aide: "Montant en francs CFA"
                    onClicked: actions.lancer("fcfa")
                }
                Outil { text: "%"; aide: "Pourcentage"; onClicked: actions.lancer("pourcentage") }
                Outil { text: ",0"; police.pixelSize: 12; aide: "Une décimale de moins"; onClicked: actions.lancer("decimalesMoins") }
                Outil { text: ",00"; police.pixelSize: 12; aide: "Une décimale de plus"; onClicked: actions.lancer("decimalesPlus") }
                Outil {
                    id: autresFormats
                    text: "123 ▾"
                    police.pixelSize: 12
                    aide: "Autres formats : nombre, date, heure…"
                    onClicked: menuFormats.popup(autresFormats, 0, autresFormats.height + 4)
                    MenuSama {
                        id: menuFormats
                        ElementMenu { cle: "formatStandard" }
                        ElementMenu { cle: "nombreFormat" }
                        ElementMenu { cle: "fcfa" }
                        ElementMenu { cle: "pourcentage" }
                        ElementMenu { cle: "dateFormat" }
                        ElementMenu { cle: "heureFormat" }
                    }
                }
                Trait {}
                Liste {
                    id: choixPolice
                    largeur: 118
                    text: doc.etats[".uno:CharFontName"] || "Noto Sans"
                    aide: "Police"
                    onClicked: menuPolice.popup(choixPolice, 0, choixPolice.height + 4)
                    MenuSama {
                        id: menuPolice
                        implicitWidth: 220
                        Instantiator {
                            model: ["Noto Sans", "Noto Serif", "Liberation Sans", "Liberation Serif", "Liberation Mono"]
                            onObjectAdded: (i, objet) => menuPolice.insertItem(i, objet)
                            onObjectRemoved: (i, objet) => menuPolice.removeItem(objet)
                            ElementMenu {
                                text: modelData
                                onTriggered: doc.commande(".uno:CharFontName", { "CharFontName.FamilyName": { type: "string", value: modelData } })
                            }
                        }
                    }
                }
                Liste {
                    id: choixTaille
                    largeur: 52
                    Layout.leftMargin: 4
                    text: Number(doc.etats[".uno:FontHeight"]) > 0 ? doc.etats[".uno:FontHeight"] : "11"
                    aide: "Taille"
                    onClicked: menuTaille.popup(choixTaille, 0, choixTaille.height + 4)
                    MenuSama {
                        id: menuTaille
                        implicitWidth: 120
                        Instantiator {
                            model: [9, 10, 11, 12, 14, 16, 18, 24, 32]
                            onObjectAdded: (i, objet) => menuTaille.insertItem(i, objet)
                            onObjectRemoved: (i, objet) => menuTaille.removeItem(objet)
                            ElementMenu {
                                text: String(modelData)
                                onTriggered: doc.commande(".uno:FontHeight", { "FontHeight.Height": { type: "float", value: modelData } })
                            }
                        }
                    }
                }
                Trait {}
                Outil { text: "B"; police.bold: true; police.pixelSize: 14; aide: "Gras (Ctrl+B)"; actif: fenetre.actif(".uno:Bold"); onClicked: actions.lancer("gras") }
                Outil { text: "I"; police.italic: true; police.pixelSize: 14; aide: "Italique (Ctrl+I)"; actif: fenetre.actif(".uno:Italic"); onClicked: actions.lancer("italique") }
                Outil { text: "S"; police.strikeout: true; police.pixelSize: 14; aide: "Barré"; actif: fenetre.actif(".uno:Strikeout"); onClicked: actions.lancer("barre") }
                Outil {
                    picto: "M7.5 16L12 5l4.5 11 M9.2 12h5.6"
                    aide: "Couleur du texte"
                    ouvert: nuancierTexte.opened
                    onClicked: nuancierTexte.open()
                    Rectangle {
                        anchors.horizontalCenter: parent.horizontalCenter
                        y: parent.height - 8
                        width: 14; height: 3; radius: 1.5
                        color: actions.couleurCellule(".uno:Color", Couleurs.texte)
                        border.width: color.hslLightness > 0.9 ? 0.5 : 0
                        border.color: Couleurs.bord
                    }
                    Nuancier {
                        id: nuancierTexte
                        titre: "Couleur du texte"
                        rangees: barre.couleursTexte
                        aucune: "Automatique"
                        aucunePicto: "M7.5 16L12 5l4.5 11 M9.2 12h5.6"
                        onChoisie: c => actions.colorerTexte(c)
                    }
                }
                Trait {}
                Outil {
                    picto: "M5 11.5L10.5 6l5.5 5.5l-5.5 5.5z M5 11.5h11 M18.5 13.5c.7 1 1.2 1.8 1.2 2.3a1.2 1.2 0 0 1-2.4 0c0-.5.5-1.3 1.2-2.3z M8.5 4l2 2"
                    aide: "Couleur de remplissage"
                    ouvert: nuancierFond.opened
                    onClicked: nuancierFond.open()
                    Rectangle {
                        anchors.horizontalCenter: parent.horizontalCenter
                        y: parent.height - 8
                        width: 14; height: 3; radius: 1.5
                        color: actions.couleurCellule(".uno:BackgroundColor", "transparent")
                        border.width: 0.5
                        border.color: Couleurs.bord
                    }
                    Nuancier {
                        id: nuancierFond
                        titre: "Remplissage"
                        rangees: barre.couleursFond
                        aucune: "Aucun remplissage"
                        aucunePicto: "M4 4h16v16H4z M4 20L20 4"
                        onChoisie: c => actions.colorerFond(c)
                    }
                }
                Outil {
                    picto: "M4 4h16v16H4z M4 12h16 M12 4v16"
                    aide: "Bordures"
                    ouvert: choixBordures.opened
                    onClicked: choixBordures.open()
                    ChoixBordures { id: choixBordures; onChoisie: g => actions.bordures(g) }
                }
                Outil { picto: "M4 6h16v12H4z M9 12h6 M7 10l-2 2 2 2 M17 10l2 2-2 2"; aide: "Fusionner les cases"; actif: fenetre.actif(".uno:ToggleMergeCells"); onClicked: actions.lancer("fusionner") }
                Trait {}
                Outil {
                    id: alignement
                    picto: fenetre.actif(".uno:AlignHorizontalCenter") ? "M4 6h16 M7 10h10 M4 14h16 M7 18h10"
                           : fenetre.actif(".uno:AlignRight") ? "M4 6h16 M10 10h10 M4 14h16 M10 18h10" : "M4 6h16 M4 10h10 M4 14h16 M4 18h10"
                    text: "▾"
                    police.pixelSize: 9
                    encre: Couleurs.texte
                    aide: "Alignement"
                    onClicked: menuAlignement.popup(alignement, 0, alignement.height + 4)
                    MenuSama {
                        id: menuAlignement
                        ElementMenu { cle: "gauche" }
                        ElementMenu { cle: "centre" }
                        ElementMenu { cle: "droite" }
                        QQC2.MenuSeparator {}
                        ElementMenu { cle: "haut" }
                        ElementMenu { cle: "milieu" }
                        ElementMenu { cle: "bas" }
                    }
                }
                Outil { picto: "M4 6h16 M4 12h13a3 3 0 0 1 0 6h-4 M15 16l-2 2 2 2 M4 18h5"; aide: "Renvoyer à la ligne (texte long sur plusieurs lignes)"; actif: fenetre.actif(".uno:WrapText"); onClicked: actions.lancer("retourLigne") }
                Trait {}
                Outil {
                    picto: "M4 20V11 M10 20V5 M16 20v-6 M3 20h18"
                    aide: "Graphique, à partir des cellules choisies"
                    ouvert: choixGraphique.opened
                    onClicked: choixGraphique.open()
                    ChoixGraphique { id: choixGraphique; onChoisi: type => doc.insererGraphique(type) }
                }
                Outil {
                    id: calculs
                    text: "Σ ▾"
                    police.pixelSize: 14
                    aide: "Somme et autres calculs des cases au-dessus ou à gauche"
                    onClicked: menuCalculs.popup(calculs, 0, calculs.height + 4)
                    MenuSama {
                        id: menuCalculs
                        ElementMenu { cle: "somme" }
                        ElementMenu { cle: "moyenne" }
                        ElementMenu { cle: "minimum" }
                        ElementMenu { cle: "maximum" }
                        ElementMenu { cle: "nombre" }
                    }
                }
            }
            Item { Layout.fillWidth: true }
            // Mettre en tableau (hors d'un tableau ; dedans, sa barre apparaît sous la formule)
            Outil {
                visible: fenetre.tableaux.courant === null
                text: "Tableau"
                picto: "M4 5h16v14H4z M4 10h16 M10 5v14"
                aide: "Mettre en tableau : titres, filtres et ligne des totaux ; enregistré comme un tableau Excel (Ctrl+T)"
                background: Rectangle { radius: 8; color: Couleurs.champ; border.width: 0.5; border.color: parent.hovered ? fenetre.accent : Couleurs.bord }
                onClicked: actions.lancer("tableau")
            }
        }
    }
    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: Couleurs.bord }
}
