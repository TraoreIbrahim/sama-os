// Bouton de la barre d'outils de Sama Sheet : pictogramme et/ou texte ; « actif » (gras appliqué…) en vert.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

QQC2.AbstractButton {
    id: outil
    property string picto: ""
    property bool actif: false
    property string aide: ""
    property font police
    property color encre: Couleurs.texte
    implicitHeight: 30
    implicitWidth: Math.max(32, rangee.implicitWidth + (text ? 24 : 12))
    hoverEnabled: true
    focusPolicy: Qt.NoFocus          // (le clavier reste à la grille)
    opacity: enabled ? 1 : 0.4
    QQC2.ToolTip.visible: hovered && aide !== ""
    QQC2.ToolTip.text: aide
    QQC2.ToolTip.delay: 600
    background: Rectangle {
        radius: 8
        color: outil.actif ? fenetre.vertFond : outil.down ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
               : outil.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
    }
    contentItem: Item {
        RowLayout {
            id: rangee
            anchors.centerIn: parent
            spacing: 6
            Picto { visible: outil.picto !== ""; trace: outil.picto; encre: outil.actif ? fenetre.vertEncre : outil.encre }
            Text {
                visible: outil.text !== ""
                text: outil.text
                font.family: outil.police.family
                font.pixelSize: outil.police.pixelSize > 0 ? outil.police.pixelSize : 13
                font.bold: outil.police.bold
                font.italic: outil.police.italic
                font.underline: outil.police.underline
                color: outil.actif ? fenetre.vertEncre : outil.encre
            }
        }
    }
}
