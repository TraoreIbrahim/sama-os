// Ruban de Sama Sheet : onglets (Accueil, Insertion, Formules, Données) et leurs outils. Chaque outil envoie une commande
// au moteur ; son état (gras appliqué, alignement…) vient du moteur.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

ColumnLayout {
    id: ruban
    spacing: 0
    readonly property var doc: fenetre.doc

    // ——— Onglets ———
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 38
        color: Couleurs.fond
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 14
            anchors.rightMargin: 14
            spacing: 4
            Repeater {
                model: [["accueil", "Accueil"], ["insertion", "Insertion"], ["formules", "Formules"], ["donnees", "Données"]]
                delegate: QQC2.AbstractButton {
                    id: onglet
                    readonly property bool choisi: fenetre.onglet === modelData[0]
                    Layout.preferredHeight: 28
                    implicitWidth: libelle.implicitWidth + 28
                    focusPolicy: Qt.NoFocus
                    hoverEnabled: true
                    onClicked: fenetre.onglet = modelData[0]
                    background: Rectangle {
                        radius: 14
                        color: onglet.choisi ? fenetre.vertFond : onglet.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                    }
                    contentItem: Text {
                        id: libelle
                        text: modelData[1]
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        font.pixelSize: 13
                        font.weight: onglet.choisi ? Font.DemiBold : Font.Normal
                        color: onglet.choisi ? fenetre.vertEncre : Couleurs.texte2
                    }
                }
            }
            Item { Layout.fillWidth: true }
            Outil { picto: "M4 12h10 M10 6l6 6l-6 6 M20 4v16"; text: "Ouvrir"; aide: "Ouvrir un classeur (Ctrl+O)"; onClicked: dialogueOuvrir.open() }
            Outil { picto: "M5 4h11l3 3v13H5z M8 4v5h7V4 M8 20v-6h8v6"; text: "Enregistrer"; aide: "Enregistrer (Ctrl+S)"; onClicked: fenetre.enregistrer() }
        }
    }

    // ——— Outils de l'onglet ———
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 44
        color: Couleurs.fond
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: Couleurs.bord }
        Loader {
            anchors.fill: parent
            anchors.leftMargin: 14
            anchors.rightMargin: 14
            sourceComponent: fenetre.onglet === "insertion" ? insertion : fenetre.onglet === "formules" ? formules
                             : fenetre.onglet === "donnees" ? donnees : accueil
        }
    }

    // Annuler et rétablir (communs aux onglets)
    component Historique: RowLayout {
        spacing: 2
        Outil { picto: "M4 12a8 8 0 1 0 2.3-5.6 M4 5v4h4"; aide: "Annuler (Ctrl+Z)"; enabled: fenetre.etat(".uno:Undo") !== "disabled"; onClicked: ruban.doc.commande(".uno:Undo") }
        Outil { picto: "M20 12a8 8 0 1 1-2.3-5.6 M20 5v4h-4"; aide: "Rétablir (Ctrl+Y)"; enabled: fenetre.etat(".uno:Redo") !== "disabled"; onClicked: ruban.doc.commande(".uno:Redo") }
    }

    Component {
        id: accueil
        RowLayout {
            spacing: 4
            Historique {}
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            // Police et taille
            Outil {
                id: choixPolice
                text: (fenetre.etat(".uno:CharFontName") || "Noto Sans") + "  ·  " + (fenetre.etat(".uno:FontHeight") || "11")
                aide: "Police et taille"
                background: Rectangle { radius: 8; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, choixPolice.hovered ? 0.09 : 0.05) }
                onClicked: menuPolice.popup()
                QQC2.Menu {
                    id: menuPolice
                    Repeater {
                        model: ["Noto Sans", "Noto Serif", "Liberation Sans", "Liberation Serif", "Liberation Mono"]
                        QQC2.MenuItem {
                            text: modelData
                            font.family: modelData
                            onTriggered: ruban.doc.commande(".uno:CharFontName", { "CharFontName.FamilyName": { type: "string", value: modelData } })
                        }
                    }
                    QQC2.MenuSeparator {}
                    Repeater {
                        model: [9, 10, 11, 12, 14, 16, 18, 24, 32]
                        QQC2.MenuItem {
                            text: "Taille " + modelData
                            onTriggered: ruban.doc.commande(".uno:FontHeight", { "FontHeight.Height": { type: "float", value: modelData } })
                        }
                    }
                }
            }
            Item { width: 2 }
            Outil { text: "B"; police.bold: true; police.pixelSize: 14; aide: "Gras (Ctrl+B)"; actif: fenetre.actif(".uno:Bold"); onClicked: ruban.doc.commande(".uno:Bold") }
            Outil { text: "I"; police.italic: true; police.pixelSize: 14; aide: "Italique (Ctrl+I)"; actif: fenetre.actif(".uno:Italic"); onClicked: ruban.doc.commande(".uno:Italic") }
            Outil { text: "U"; police.underline: true; police.pixelSize: 14; aide: "Souligné (Ctrl+U)"; actif: fenetre.actif(".uno:Underline"); onClicked: ruban.doc.commande(".uno:Underline") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { picto: "M4 6h16 M4 10h10 M4 14h16 M4 18h10"; aide: "Aligner à gauche"; actif: fenetre.actif(".uno:AlignLeft"); onClicked: ruban.doc.commande(".uno:AlignLeft") }
            Outil { picto: "M4 6h16 M7 10h10 M4 14h16 M7 18h10"; aide: "Centrer"; actif: fenetre.actif(".uno:AlignHorizontalCenter"); onClicked: ruban.doc.commande(".uno:AlignHorizontalCenter") }
            Outil { picto: "M4 6h16 M10 10h10 M4 14h16 M10 18h10"; aide: "Aligner à droite"; actif: fenetre.actif(".uno:AlignRight"); onClicked: ruban.doc.commande(".uno:AlignRight") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            // Formats de nombre : franc CFA (monnaie par défaut de Sama), pourcentage, décimales
            Outil {
                id: fcfa
                text: "FCFA"
                police.bold: true
                police.pixelSize: 12
                aide: "Montant en francs CFA"
                background: Rectangle { radius: 8; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, fcfa.hovered ? 0.09 : 0.05) }
                // (puis les colonnes s'élargissent pour que les montants ne s'affichent pas « ### »)
                onClicked: { ruban.doc.commande(".uno:NumberFormatCurrency"); ruban.doc.commande(".uno:SetOptimalColumnWidthDirect") }
            }
            Outil { text: "%"; encre: Couleurs.texte2; aide: "Pourcentage"; onClicked: ruban.doc.commande(".uno:NumberFormatPercent") }
            Outil { text: ",00"; police.pixelSize: 12; encre: Couleurs.texte2; aide: "Une décimale de plus"; onClicked: ruban.doc.commande(".uno:NumberFormatIncDecimals") }
            Outil { text: ",0"; police.pixelSize: 12; encre: Couleurs.texte2; aide: "Une décimale de moins"; onClicked: ruban.doc.commande(".uno:NumberFormatDecDecimals") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Somme"; picto: "M17 5H7l6 7l-6 7h10"; aide: "Somme des cellules au-dessus ou à gauche"; onClicked: ruban.doc.commande(".uno:AutoSum") }
            Outil {
                text: "Trier"
                picto: "M4 7h10 M18 7h2 M4 17h4 M12 17h8 M16 5v4 M10 15v4"
                aide: "Trier les lignes"
                onClicked: menuTri.popup()
                QQC2.Menu {
                    id: menuTri
                    QQC2.MenuItem { text: "De A à Z, du plus petit au plus grand"; onTriggered: ruban.doc.commande(".uno:SortAscending") }
                    QQC2.MenuItem { text: "De Z à A, du plus grand au plus petit"; onTriggered: ruban.doc.commande(".uno:SortDescending") }
                }
            }
            Item { Layout.fillWidth: true }
            Outil { text: "Graphique"; picto: "M4 20V10 M10 20V4 M16 20v-7 M3 20h18"; aide: "Bientôt"; enabled: false }
        }
    }

    Component {
        id: insertion
        RowLayout {
            spacing: 4
            Historique {}
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Ligne au-dessus"; picto: "M4 14h16v6H4z M12 4v6 M9 7h6"; onClicked: ruban.doc.commande(".uno:InsertRowsBefore") }
            Outil { text: "Ligne en dessous"; picto: "M4 4h16v6H4z M12 14v6 M9 17h6"; onClicked: ruban.doc.commande(".uno:InsertRowsAfter") }
            Outil { text: "Colonne à gauche"; picto: "M14 4h6v16h-6z M4 12h6 M7 9v6"; onClicked: ruban.doc.commande(".uno:InsertColumnsBefore") }
            Outil { text: "Colonne à droite"; picto: "M4 4h6v16H4z M14 12h6 M17 9v6"; onClicked: ruban.doc.commande(".uno:InsertColumnsAfter") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Supprimer la ligne"; picto: "M4 9h16v6H4z M9 4l6 0"; encre: "#A3322A"; onClicked: ruban.doc.commande(".uno:DeleteRows") }
            Outil { text: "Supprimer la colonne"; picto: "M9 4h6v16H9z"; encre: "#A3322A"; onClicked: ruban.doc.commande(".uno:DeleteColumns") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Fusionner"; picto: "M4 6h16v12H4z M9 12h6 M7 10l-2 2l2 2 M17 10l2 2l-2 2"; actif: fenetre.actif(".uno:ToggleMergeCells"); onClicked: ruban.doc.commande(".uno:ToggleMergeCells") }
            Item { Layout.fillWidth: true }
        }
    }

    Component {
        id: formules
        RowLayout {
            spacing: 4
            Historique {}
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Somme"; picto: "M17 5H7l6 7l-6 7h10"; onClicked: ruban.doc.commande(".uno:AutoSum") }
            Repeater {
                model: [["Moyenne", "=MOYENNE()"], ["Minimum", "=MIN()"], ["Maximum", "=MAX()"], ["Nombre", "=NB()"], ["Si…", "=SI(;;)"], ["Arrondi", "=ARRONDI(;0)"]]
                Outil { text: modelData[0]; onClicked: fenetre.commencerFormule(modelData[1]) }
            }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Afficher les formules"; picto: "M4 6h16 M4 12h10 M4 18h7"; actif: fenetre.actif(".uno:ToggleFormula"); onClicked: ruban.doc.commande(".uno:ToggleFormula") }
            Item { Layout.fillWidth: true }
        }
    }

    Component {
        id: donnees
        RowLayout {
            spacing: 4
            Historique {}
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Trier de A à Z"; picto: "M6 4v16 M3 17l3 3l3-3 M13 6h7 M13 12h5 M13 18h3"; onClicked: ruban.doc.commande(".uno:SortAscending") }
            Outil { text: "Trier de Z à A"; picto: "M6 4v16 M3 7l3-3l3 3 M13 6h3 M13 12h5 M13 18h7"; onClicked: ruban.doc.commande(".uno:SortDescending") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Figer la première ligne"; picto: "M4 4h16v16H4z M4 9h16"; onClicked: ruban.doc.commande(".uno:FreezePanesFirstRow") }
            Outil { text: "Figer jusqu'ici"; picto: "M4 4h16v16H4z M4 10h16 M10 4v16"; actif: fenetre.actif(".uno:FreezePanes"); onClicked: ruban.doc.commande(".uno:FreezePanes") }
            Item { Layout.fillWidth: true }
        }
    }
}
