// Volet qui s'ouvre sous un outil du ruban (couleurs, bordures, graphiques) : carte claire aux coins arrondis.
import QtQuick
import QtQuick.Controls as QQC2
import "../reglages"

QQC2.Popup {
    id: volet
    property string titre: ""
    default property alias contenu: colonne.data
    y: parent ? parent.height + 6 : 0
    margins: 10                 // (reste dans la fenêtre, même sous un outil tout à droite)
    padding: 14
    topPadding: titre ? 12 : 14
    modal: false
    focus: false
    closePolicy: QQC2.Popup.CloseOnEscape | QQC2.Popup.CloseOnPressOutside
    background: Item {
        // (ombre douce)
        Rectangle { anchors.fill: parent; anchors.topMargin: 3; anchors.margins: -1; radius: 13; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.06) }
        Rectangle { anchors.fill: parent; anchors.topMargin: 1; radius: 12; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.08) }
        Rectangle { anchors.fill: parent; radius: 12; color: Couleurs.champ; border.width: 0.5; border.color: Couleurs.bord }
    }
    contentItem: Column {
        id: colonne
        spacing: 10
        Text {
            visible: volet.titre !== ""
            text: volet.titre
            font.pixelSize: 12
            font.weight: Font.DemiBold
            color: Couleurs.texte2
        }
    }
}
