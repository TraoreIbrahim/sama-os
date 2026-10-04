// Interrupteur Sama : 44 × 26, latérite quand il est actif.
import QtQuick

MouseArea {
    id: inter
    property bool actif: false
    signal bascule(bool actif)
    width: 44
    height: 26
    cursorShape: Qt.PointingHandCursor
    onClicked: inter.bascule(!inter.actif)
    Rectangle {
        anchors.fill: parent
        radius: 13
        color: inter.actif ? Couleurs.laterite : (Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.2) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.18))
        Behavior on color { ColorAnimation { duration: 150 } }
        Rectangle {
            x: inter.actif ? parent.width - width - 3 : 3
            y: 3
            width: 20
            height: 20
            radius: 10
            color: "#FFFFFF"
            Behavior on x { NumberAnimation { duration: 150; easing.type: Easing.OutCubic } }
        }
    }
}
