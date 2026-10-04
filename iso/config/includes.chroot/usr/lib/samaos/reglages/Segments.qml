// Boutons segmentés (ex. 100 % · 125 % · 150 %) : le choix courant sur une pastille blanche.
import QtQuick

Rectangle {
    id: segments
    property var choix: []          // libellés
    property int indexChoisi: 0
    signal choisi(int index)
    implicitWidth: rangee.implicitWidth + 6
    implicitHeight: 34
    radius: 10
    color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.08) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.09)
    Row {
        id: rangee
        anchors.centerIn: parent
        spacing: 2
        Repeater {
            model: segments.choix
            delegate: MouseArea {
                width: libelle.implicitWidth + 28
                height: 28
                cursorShape: Qt.PointingHandCursor
                onClicked: segments.choisi(index)
                Rectangle {
                    anchors.fill: parent
                    radius: 8
                    color: index === segments.indexChoisi ? Couleurs.champ : "transparent"
                }
                Text {
                    id: libelle
                    anchors.centerIn: parent
                    text: modelData
                    font.pixelSize: 12
                    font.weight: index === segments.indexChoisi ? Font.DemiBold : Font.Medium
                    color: index === segments.indexChoisi ? Couleurs.texte : Couleurs.texte2
                }
            }
        }
    }
}
