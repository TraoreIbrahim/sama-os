// Bouton en pilule : blanc bordé (par défaut) ou latérite (principal).
import QtQuick
import QtQuick.Controls as QQC2

QQC2.AbstractButton {
    id: bouton
    property bool principal: false
    implicitHeight: 30
    hoverEnabled: true
    contentItem: Text {
        text: bouton.text
        font.pixelSize: 13
        font.weight: bouton.principal ? Font.DemiBold : Font.Medium
        color: bouton.principal ? "#FFFFFF" : Couleurs.texte
        leftPadding: 14
        rightPadding: 14
        verticalAlignment: Text.AlignVCenter
        horizontalAlignment: Text.AlignHCenter
    }
    background: Rectangle {
        radius: 15
        color: bouton.principal ? Couleurs.laterite : Couleurs.champ
        border.width: bouton.principal ? 0 : 1
        border.color: Couleurs.bord
        opacity: bouton.enabled ? (bouton.down ? 0.85 : 1) : 0.5
    }
}
