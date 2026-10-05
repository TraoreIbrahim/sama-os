// Fenêtre de confirmation posée au centre des Réglages, sur un voile (comme celle de la corbeille de Fichiers) :
// pictogramme, titre, explication, contenu facultatif (champ de saisie…), « Annuler » et l'action.
// Échap ou un clic à côté : annuler.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2

Item {
    id: confirmation
    property bool ouverte: false
    property string titre
    property string texte
    property string action: "Confirmer"
    property string picto: ""                 // tracé SVG 24 × 24
    property color teinte: Couleurs.laterite  // couleur du pictogramme et du bouton d'action
    property bool occupe: false               // action en cours : boutons inactifs
    default property alias contenu: zoneContenu.data
    signal confirme()

    parent: fenetre.contentItem
    anchors.fill: parent
    z: 100
    visible: opacity > 0
    opacity: ouverte ? 1 : 0
    Behavior on opacity { NumberAnimation { duration: 160; easing.type: Easing.OutCubic } }

    Shortcut { sequence: "Escape"; enabled: confirmation.ouverte && !confirmation.occupe; onActivated: confirmation.ouverte = false }

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.45 : 0.28)
        MouseArea { anchors.fill: parent; onClicked: if (!confirmation.occupe) confirmation.ouverte = false }
    }

    Rectangle {
        anchors.centerIn: parent
        width: Math.min(parent.width - 60, 440)
        height: colonne.implicitHeight + 56
        radius: 20
        color: Couleurs.fond
        border.width: 1
        border.color: Couleurs.bord
        scale: confirmation.ouverte ? 1 : 0.96
        Behavior on scale { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
        MouseArea { anchors.fill: parent }

        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 28
            spacing: 0

            Rectangle {
                visible: confirmation.picto !== ""
                Layout.preferredWidth: 52
                Layout.preferredHeight: 52
                Layout.bottomMargin: 18
                radius: 16
                color: Qt.rgba(confirmation.teinte.r, confirmation.teinte.g, confirmation.teinte.b, Couleurs.sombre ? 0.25 : 0.12)
                Canvas {
                    anchors.centerIn: parent
                    width: 24
                    height: 24
                    property string trace: confirmation.picto
                    property color encre: confirmation.teinte
                    onTraceChanged: requestPaint()
                    onPaint: {
                        var c = getContext("2d"); c.reset()
                        c.strokeStyle = encre; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                        c.path = trace; c.stroke()
                    }
                }
            }
            Text {
                Layout.fillWidth: true
                text: confirmation.titre
                wrapMode: Text.WordWrap
                font.pixelSize: 20
                font.weight: Font.Medium
                color: Couleurs.texte
            }
            Text {
                visible: confirmation.texte !== ""
                Layout.topMargin: 8
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                lineHeight: 1.2
                text: confirmation.texte
                font.pixelSize: 14
                color: Couleurs.texte2
            }
            ColumnLayout {
                id: zoneContenu
                visible: children.length > 0
                Layout.topMargin: 16
                Layout.fillWidth: true
                spacing: 8
            }
            RowLayout {
                Layout.topMargin: 26
                Layout.alignment: Qt.AlignRight
                spacing: 10
                BoutonSama { text: "Annuler"; implicitHeight: 38; enabled: !confirmation.occupe; onClicked: confirmation.ouverte = false }
                QQC2.AbstractButton {
                    implicitHeight: 38
                    implicitWidth: libelle.implicitWidth + 40
                    enabled: !confirmation.occupe
                    hoverEnabled: true
                    onClicked: confirmation.confirme()
                    background: Rectangle {
                        radius: 19
                        color: confirmation.teinte
                        opacity: !parent.enabled ? 0.6 : parent.down ? 0.85 : (parent.hovered ? 0.93 : 1)
                    }
                    contentItem: Text {
                        id: libelle
                        text: confirmation.action
                        font.pixelSize: 14
                        font.weight: Font.DemiBold
                        color: "#FFFFFF"
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                    }
                }
            }
        }
    }
}
