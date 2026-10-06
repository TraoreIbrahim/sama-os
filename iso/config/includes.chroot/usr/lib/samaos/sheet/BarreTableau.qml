// Barre du tableau où se trouve la case courante (comme l'onglet « Création de tableau » d'Excel, en plus simple) :
// son nom (qu'on change en cliquant dessus), sa place, ajouter une ligne, la ligne des totaux.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: barre
    readonly property var tableaux: fenetre.tableaux
    readonly property var t: fenetre.tableauVu
    readonly property bool enFiches: fenetre.vue === "fiches"
    // (dans la grille, l'onglet posé au-dessus du tableau et son panneau la remplacent)
    readonly property bool utile: t !== null && (enFiches || (!t.libre && !fenetre.panneauOuvert))
    implicitHeight: utile ? 38 : 0
    visible: utile
    color: Qt.rgba(31 / 255, 94 / 255, 122 / 255, Couleurs.sombre ? 0.16 : 0.07)
    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: Couleurs.bord }

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 14
        anchors.rightMargin: 14
        spacing: 10

        Rectangle {
            Layout.preferredHeight: 26
            Layout.preferredWidth: titre.implicitWidth + 20
            radius: 7
            color: fenetre.accent
            RowLayout {
                id: titre
                anchors.centerIn: parent
                spacing: 6
                Picto { width: 14; height: 14; trace: "M4 5h16v14H4z M4 10h16 M10 5v14"; encre: "#FFFFFF"; trait: 2 }
                Text { text: "Tableau"; font.pixelSize: 12; color: "#FFFFFF"; opacity: 0.85 }
            }
        }
        // Le nom : un clic pour le changer, Entrée pour le garder
        TextInput {
            id: nom
            text: barre.t ? barre.t.nom : ""
            font.pixelSize: 14
            font.weight: Font.DemiBold
            color: fenetre.accentEncre
            selectByMouse: true
            Layout.preferredWidth: Math.max(80, contentWidth + 4)
            Accessible.name: "Nom du tableau"
            onAccepted: { if (barre.t && text.trim() && text !== barre.t.nom) barre.tableaux.renommer(barre.t, text); fenetre.doc.forceActiveFocus() }
            Keys.onEscapePressed: { text = barre.t ? barre.t.nom : ""; fenetre.doc.forceActiveFocus() }
            onActiveFocusChanged: if (!activeFocus) text = Qt.binding(function () { return barre.t ? barre.t.nom : "" })
            Rectangle { anchors.fill: parent; anchors.margins: -4; radius: 6; z: -1; color: "transparent"; border.width: nom.activeFocus ? 1.5 : 0; border.color: fenetre.accent }
            QQC2.ToolTip.visible: zoneNom.containsMouse && !nom.activeFocus
            QQC2.ToolTip.text: "Cliquez pour renommer le tableau ; les formules s'en servent : " + (barre.t ? barre.t.nom : "") + "[Colonne]"
            QQC2.ToolTip.delay: 700
            MouseArea { id: zoneNom; anchors.fill: parent; hoverEnabled: true; acceptedButtons: Qt.NoButton; cursorShape: Qt.IBeamCursor }
        }
        Text {
            text: barre.t ? barre.tableaux.adresse(barre.t) + " · " + barre.tableaux.lignesDonnees(barre.t) + " lignes" : ""
            font.pixelSize: 12
            color: Couleurs.texte2
        }
        // Grille ou fiches : le même tableau, vu autrement
        Rectangle {
            Layout.preferredHeight: 30
            Layout.preferredWidth: vues.implicitWidth + 6
            radius: 9
            color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.25 : 0.06)
            RowLayout {
                id: vues
                anchors.centerIn: parent
                spacing: 2
                Repeater {
                    model: [["grille", "Grille", "M4 5h16v14H4z M4 10h16 M10 5v14"], ["fiches", "Fiches", "M4 5h7v6H4z M13 5h7v6h-7z M4 13h7v6H4z M13 13h7v6h-7z"]]
                    delegate: QQC2.AbstractButton {
                        id: vue
                        readonly property bool choisie: fenetre.vue === modelData[0]
                        implicitHeight: 24
                        implicitWidth: contenuVue.implicitWidth + 18
                        focusPolicy: Qt.NoFocus
                        hoverEnabled: true
                        onClicked: modelData[0] === "fiches" ? fenetre.voirFiches(barre.t) : fenetre.voirGrille()
                        background: Rectangle { radius: 7; color: vue.choisie ? Couleurs.champ : "transparent"; border.width: vue.choisie ? 0.5 : 0; border.color: Couleurs.bord }
                        contentItem: RowLayout {
                            id: contenuVue
                            spacing: 5
                            Picto { width: 13; height: 13; trace: modelData[2]; encre: vue.choisie ? fenetre.accentEncre : Couleurs.texte2 }
                            Text { text: modelData[1]; font.pixelSize: 12; font.weight: vue.choisie ? Font.DemiBold : Font.Normal; color: vue.choisie ? fenetre.accentEncre : Couleurs.texte2 }
                        }
                    }
                }
            }
        }
        Separateur {}
        Outil {
            text: barre.enFiches ? "Nouvelle fiche" : "Ajouter une ligne"
            picto: "M12 5v14 M5 12h14"
            aide: barre.enFiches ? "Une fiche de plus (une ligne du tableau)" : "Une ligne de plus à la fin du tableau, avec ses formules"
            onClicked: barre.enFiches ? fenetre.nouvelleFiche() : barre.tableaux.ajouterLigne(barre.t)
        }
        Outil {
            id: totaux
            text: "Ligne des totaux"
            picto: barre.t && barre.t.totaux ? "M5 12.5l4.5 4.5L19 7.5" : "M4 5h16v14H4z"
            actif: barre.t ? barre.t.totaux : false
            aide: "Une ligne « Total » sous les données : la somme des colonnes de nombres, sans les lignes cachées par un filtre"
            onClicked: barre.tableaux.totaux(barre.t, !barre.t.totaux)
        }
        Item { Layout.fillWidth: true }
        Text {
            visible: !barre.enFiches
            text: "Trier et filtrer : ▾ dans les titres"
            font.pixelSize: 12
            color: Couleurs.texte3
        }
    }
}
