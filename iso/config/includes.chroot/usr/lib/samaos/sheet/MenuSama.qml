// Menu de Sama Sheet : carte claire aux coins arrondis, une ombre douce.
import QtQuick
import QtQuick.Controls as QQC2
import "../reglages"

QQC2.Menu {
    id: menu
    padding: 6
    implicitWidth: 300
    delegate: ElementMenu {}
    background: Item {
        implicitWidth: 300
        Rectangle { anchors.fill: parent; anchors.topMargin: 3; anchors.margins: -1; radius: 13; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.06) }
        Rectangle { anchors.fill: parent; anchors.topMargin: 1; radius: 12; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.08) }
        Rectangle { anchors.fill: parent; radius: 12; color: Couleurs.champ; border.width: 0.5; border.color: Couleurs.bord }
    }
}
