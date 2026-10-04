// Liste déroulante Sama : champ blanc arrondi, flèche discrète.
import QtQuick
import QtQuick.Controls as QQC2

QQC2.ComboBox {
    id: liste
    implicitWidth: 170
    implicitHeight: 32
    font.pixelSize: 13
    background: Rectangle {
        radius: 9
        color: Couleurs.champ
        border.width: 1
        border.color: liste.activeFocus ? Couleurs.laterite : Couleurs.bord
    }
    contentItem: Text {
        leftPadding: 12
        rightPadding: 28
        text: liste.displayText
        font: liste.font
        color: Couleurs.texte
        verticalAlignment: Text.AlignVCenter
        elide: Text.ElideRight
    }
    indicator: Text {
        x: liste.width - width - 12
        y: (liste.height - height) / 2
        text: "⌄"
        font.pixelSize: 14
        color: Couleurs.texte3
    }
}
