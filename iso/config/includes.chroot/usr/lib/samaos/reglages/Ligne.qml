// Ligne de réglage : titre et détail à gauche, contrôle à droite ; séparateur fin sous la ligne.
import QtQuick
import QtQuick.Layouts

Item {
    id: ligne
    property string titre
    property string detail
    property bool derniere: false
    property bool cliquable: false
    signal clique()
    default property alias controle: zoneControle.data

    Layout.fillWidth: true
    implicitHeight: Math.max(58, textes.implicitHeight + 24)

    MouseArea {
        anchors.fill: parent
        enabled: ligne.cliquable
        hoverEnabled: ligne.cliquable
        cursorShape: ligne.cliquable ? Qt.PointingHandCursor : Qt.ArrowCursor
        onClicked: ligne.clique()
        Rectangle {
            anchors.fill: parent
            color: Couleurs.texte
            opacity: parent.containsMouse ? 0.04 : 0
        }
    }
    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 18
        anchors.rightMargin: 18
        spacing: 14
        ColumnLayout {
            id: textes
            Layout.fillWidth: true
            spacing: 2
            Text {
                Layout.fillWidth: true
                text: ligne.titre
                elide: Text.ElideRight
                font.pixelSize: 14
                font.weight: Font.Medium
                color: Couleurs.texte
            }
            Text {
                Layout.fillWidth: true
                visible: ligne.detail !== ""
                text: ligne.detail
                wrapMode: Text.WordWrap
                font.pixelSize: 12
                color: Couleurs.texte2
            }
        }
        Row {
            id: zoneControle
            Layout.alignment: Qt.AlignVCenter
            spacing: 8
        }
    }
    Rectangle {
        visible: !ligne.derniere
        anchors.bottom: parent.bottom
        anchors.left: parent.left
        anchors.right: parent.right
        height: 1
        color: Couleurs.ligne
    }
}
