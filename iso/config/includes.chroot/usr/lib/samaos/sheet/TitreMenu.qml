// Titre d'une partie d'un menu (à la place d'un simple trait) : on sait ce qu'on va trouver dessous.
import QtQuick
import "../reglages"

Item {
    property alias text: titre.text
    implicitWidth: 200
    implicitHeight: 30
    Rectangle { x: 6; width: parent.width - 12; y: titre.text ? 3 : Math.round(parent.height / 2); height: 0.5; color: Couleurs.bord; visible: parent.trait }
    property bool trait: true
    Text {
        id: titre
        x: 10
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 6
        font.pixelSize: 11
        font.weight: Font.DemiBold
        font.letterSpacing: 0.2
        color: Couleurs.texte3
    }
}
