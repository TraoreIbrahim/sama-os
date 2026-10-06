// Menu de Sama Sheet : un panneau clair aux coins arrondis, une ombre douce. On y range des lignes (ElementMenu), des
// titres de partie (TitreMenu), des tuiles (TuilesMenu) et des pastilles (PastillesMenu).
import QtQuick
import QtQuick.Controls as QQC2
import "../reglages"

QQC2.Menu {
    id: menu
    padding: 8
    topPadding: 6
    implicitWidth: 344
    delegate: ElementMenu {}
    background: Item {
        implicitWidth: 344
        Rectangle { anchors.fill: parent; anchors.topMargin: 4; anchors.margins: -2; radius: 16; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.05) }
        Rectangle { anchors.fill: parent; anchors.topMargin: 1; anchors.margins: -1; radius: 15; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.07) }
        Rectangle { anchors.fill: parent; radius: 14; color: Couleurs.champ; border.width: 0.5; border.color: Couleurs.bord }
    }
}
