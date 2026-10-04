// Champ de saisie Sama : fond blanc arrondi, contour latérite au focus.
import QtQuick
import QtQuick.Controls as QQC2

QQC2.TextField {
    id: champ
    implicitWidth: 220
    implicitHeight: 32
    leftPadding: 12
    rightPadding: 12
    font.pixelSize: 13
    color: Couleurs.texte
    placeholderTextColor: Couleurs.texte3
    selectByMouse: true
    background: Rectangle {
        radius: 9
        color: Couleurs.champ
        border.width: champ.activeFocus ? 1.5 : 1
        border.color: champ.activeFocus ? Couleurs.laterite : Couleurs.bord
    }
}
