// Petite étiquette arrondie (ex. « Administrateur »).
import QtQuick

Rectangle {
    property alias text: libelle.text
    property color teinte: Couleurs.laterite
    property color encre: Couleurs.lateriteEncre
    implicitWidth: libelle.implicitWidth + 18
    implicitHeight: 22
    radius: 11
    color: Qt.rgba(teinte.r, teinte.g, teinte.b, 0.12)
    Text { id: libelle; anchors.centerIn: parent; font.pixelSize: 11; font.weight: Font.DemiBold; color: parent.encre }
}
