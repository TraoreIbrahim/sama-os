// Carte arrondie qui regroupe des lignes de réglages, avec un titre de section facultatif.
import QtQuick
import QtQuick.Layouts

ColumnLayout {
    id: groupe
    property string titre
    default property alias lignes: interieur.data
    Layout.fillWidth: true
    spacing: 8

    Text {
        visible: groupe.titre !== ""
        Layout.leftMargin: 4
        Layout.topMargin: 6
        text: groupe.titre
        font.pixelSize: 13
        font.weight: Font.DemiBold
        color: Couleurs.texte2
    }
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: interieur.implicitHeight
        radius: 14
        color: Couleurs.carte
        clip: true
        ColumnLayout {
            id: interieur
            width: parent.width
            spacing: 0
        }
    }
}
