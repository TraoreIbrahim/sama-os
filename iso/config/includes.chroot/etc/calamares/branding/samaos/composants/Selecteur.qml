// Bouton de choix de l'installateur (maquette dem-03) : libellé au-dessus, valeur, détail, chevron.
import QtQuick
import QtQuick.Layouts

ColumnLayout {
    id: selecteur
    property string libelle
    property string valeur
    property string detail
    property string picto     // chemin SVG 24 × 24
    signal ouvrir()
    spacing: 6
    Text { text: selecteur.libelle; font.pixelSize: 12; font.weight: Font.Medium; color: "#665E54" }
    MouseArea {
        id: zone
        Layout.fillWidth: true
        Layout.preferredHeight: selecteur.detail ? 58 : 44
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onClicked: selecteur.ouvrir()
        Rectangle {
            anchors.fill: parent
            radius: 12
            color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, zone.containsMouse ? 0.08 : 0.05)
        }
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 14
            anchors.rightMargin: 14
            spacing: 10
            Canvas {
                visible: selecteur.picto !== ""
                Layout.preferredWidth: 18
                Layout.preferredHeight: 18
                onPaint: {
                    var c = getContext("2d"); c.reset(); c.scale(18 / 24, 18 / 24)
                    c.strokeStyle = "#665E54"; c.lineWidth = 1.7; c.lineCap = "round"; c.lineJoin = "round"
                    c.path = selecteur.picto; c.stroke()
                }
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text { Layout.fillWidth: true; text: selecteur.valeur; elide: Text.ElideRight; font.pixelSize: 14; color: "#1F1C18" }
                Text { visible: selecteur.detail !== ""; Layout.fillWidth: true; text: selecteur.detail; elide: Text.ElideRight; font.pixelSize: 12; color: "#665E54" }
            }
            Text { text: "⌄"; font.pixelSize: 16; color: "#8A8277" }
        }
    }
}
