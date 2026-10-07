// Case à cocher de Sama Sheet (une ligne, et une explication dessous s'il le faut) : vue Image, impression.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

QQC2.AbstractButton {
    id: caseACocher
    property string detail: ""
    checkable: true
    hoverEnabled: true
    focusPolicy: Qt.NoFocus
    Layout.fillWidth: true
    implicitHeight: contenuCase.implicitHeight + 14
    background: null
    contentItem: RowLayout {
        id: contenuCase
        spacing: 10
        Rectangle {
            Layout.alignment: Qt.AlignTop
            Layout.topMargin: 2
            Layout.preferredWidth: 18; Layout.preferredHeight: 18
            radius: 5
            color: caseACocher.checked ? fenetre.accent : Couleurs.champ
            border.width: caseACocher.checked ? 0 : 1.5
            border.color: caseACocher.hovered ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.3)
            Picto { anchors.centerIn: parent; width: 13; height: 13; visible: caseACocher.checked; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: "#FFFFFF"; trait: 2.6 }
        }
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2
            Text { Layout.fillWidth: true; text: caseACocher.text; font.pixelSize: 14; color: Couleurs.texte; wrapMode: Text.Wrap }
            Text { visible: caseACocher.detail !== ""; Layout.fillWidth: true; text: caseACocher.detail; font.pixelSize: 12; color: Couleurs.texte2; wrapMode: Text.Wrap }
        }
    }
}
