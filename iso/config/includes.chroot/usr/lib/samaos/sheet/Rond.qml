// Bouton radio de Sama Sheet, avec une explication dessous s'il le faut : vue Image, impression.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

QQC2.AbstractButton {
    id: rond
    property bool choisi: false
    property string detail: ""
    hoverEnabled: true
    focusPolicy: Qt.NoFocus
    Layout.fillWidth: true
    implicitHeight: contenuRond.implicitHeight + 8
    background: null
    contentItem: RowLayout {
        id: contenuRond
        spacing: 9
        Rectangle {
            Layout.alignment: Qt.AlignTop
            Layout.topMargin: 2
            Layout.preferredWidth: 18; Layout.preferredHeight: 18
            radius: 9
            color: Couleurs.champ
            border.width: rond.choisi ? 5 : 1.5
            border.color: rond.choisi ? fenetre.accent : rond.hovered ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.3)
        }
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2
            Text { Layout.fillWidth: true; text: rond.text; font.pixelSize: 14; color: Couleurs.texte }
            Text { visible: rond.detail !== ""; Layout.fillWidth: true; text: rond.detail; font.pixelSize: 12; color: Couleurs.texte2; wrapMode: Text.Wrap }
        }
    }
}
