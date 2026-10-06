// « Que voulez-vous faire ? » (Ctrl+K, ou Alt+/) : on écrit ce qu'on cherche (« trier », « fcfa », « somme si »…), Sama
// propose les commandes et les fonctions. Flèches pour choisir, Entrée pour faire, Échap pour revenir à la grille.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: recherche
    implicitWidth: 380
    implicitHeight: 32
    property int choisi: 0
    readonly property var resultats: champ.activeFocus ? fenetre.actions.chercher(champ.text) : []
    onResultatsChanged: choisi = 0

    function ouvrir() { champ.forceActiveFocus(); champ.selectAll() }
    function fermer() { champ.text = ""; fenetre.doc.forceActiveFocus() }
    function faire(r) {
        if (!r) return
        fermer()
        r.faire()
    }

    Rectangle {
        anchors.fill: parent
        radius: 9
        color: champ.activeFocus ? Couleurs.champ : Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.25 : 0.055)
        border.width: champ.activeFocus ? 1.5 : 0
        border.color: fenetre.vert
    }
    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 10
        anchors.rightMargin: 6
        spacing: 8
        Picto { width: 15; height: 15; trace: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4"; encre: Couleurs.texte2 }
        TextInput {
            id: champ
            Layout.fillWidth: true
            verticalAlignment: TextInput.AlignVCenter
            font.pixelSize: 13
            color: Couleurs.texte
            clip: true
            selectByMouse: true
            Accessible.name: "Que voulez-vous faire ?"
            Text {
                anchors.verticalCenter: parent.verticalCenter
                visible: !champ.text
                text: "Que voulez-vous faire ? Une commande, une fonction…"
                font.pixelSize: 13
                color: Couleurs.texte3
            }
            Keys.onDownPressed: recherche.choisi = Math.min(recherche.choisi + 1, recherche.resultats.length - 1)
            Keys.onUpPressed: recherche.choisi = Math.max(recherche.choisi - 1, 0)
            Keys.onReturnPressed: recherche.faire(recherche.resultats[recherche.choisi])
            Keys.onEnterPressed: recherche.faire(recherche.resultats[recherche.choisi])
            Keys.onEscapePressed: recherche.fermer()
        }
        Text {
            visible: !champ.activeFocus
            text: "Ctrl+K"
            font.pixelSize: 11
            font.weight: Font.DemiBold
            color: Couleurs.texte2
            Rectangle { anchors.fill: parent; anchors.margins: -3; anchors.leftMargin: -5; anchors.rightMargin: -5; radius: 5; color: Couleurs.champ; z: -1 }
        }
    }

    // Les propositions
    QQC2.Popup {
        y: recherche.height + 6
        x: 0
        width: Math.max(recherche.width, 460)
        visible: champ.activeFocus && recherche.resultats.length > 0
        closePolicy: QQC2.Popup.NoAutoClose
        focus: false
        padding: 6
        background: Item {
            Rectangle { anchors.fill: parent; anchors.topMargin: 3; anchors.margins: -1; radius: 13; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.06) }
            Rectangle { anchors.fill: parent; radius: 12; color: Couleurs.champ; border.width: 0.5; border.color: Couleurs.bord }
        }
        contentItem: ColumnLayout {
            spacing: 0
            Text {
                visible: !champ.text
                text: "Souvent utilisées"
                font.pixelSize: 11
                font.weight: Font.DemiBold
                color: Couleurs.texte2
                Layout.margins: 6
            }
            Repeater {
                model: recherche.resultats
                delegate: Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 36
                    radius: 8
                    color: index === recherche.choisi ? fenetre.vertFond : zone.containsMouse ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 10
                        anchors.rightMargin: 10
                        spacing: 12
                        Text {
                            Layout.fillWidth: true
                            text: modelData.nom
                            font.pixelSize: 13
                            font.weight: index === recherche.choisi ? Font.DemiBold : Font.Normal
                            color: index === recherche.choisi ? fenetre.vertEncre : Couleurs.texte
                            elide: Text.ElideRight
                        }
                        Text {
                            Layout.maximumWidth: 200
                            text: modelData.raccourci ? modelData.raccourci : modelData.menu
                            font.pixelSize: 11
                            color: Couleurs.texte3
                            elide: Text.ElideRight
                        }
                    }
                    MouseArea {
                        id: zone
                        anchors.fill: parent
                        hoverEnabled: true
                        onClicked: recherche.faire(modelData)
                    }
                }
            }
        }
    }
}
