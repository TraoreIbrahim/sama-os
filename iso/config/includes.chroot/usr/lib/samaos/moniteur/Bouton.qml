// Bouton arrondi du Moniteur, avec pictogramme facultatif.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

QQC2.AbstractButton {
    id: bouton
    property color fond: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : 0.05)
    property color encre: Couleurs.texte
    property string picto: ""
    implicitHeight: 34
    implicitWidth: rangee.implicitWidth + 32
    hoverEnabled: true
    opacity: enabled ? 1 : 0.45
    background: Rectangle { radius: 17; color: bouton.fond; opacity: bouton.down ? 0.8 : (bouton.hovered ? 0.9 : 1) }
    contentItem: Item {
        RowLayout {
            id: rangee
            anchors.centerIn: parent
            spacing: 7
            Picto { visible: bouton.picto !== ""; trace: bouton.picto; encre: bouton.encre }
            Text { text: bouton.text; font.pixelSize: 13; font.weight: Font.Medium; color: bouton.encre }
        }
    }
}
