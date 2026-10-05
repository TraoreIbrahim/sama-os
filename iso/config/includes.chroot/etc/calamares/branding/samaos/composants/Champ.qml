// Champ de saisie de l'installateur (maquette dem-05) : libellé (avec un coin à droite pour un lien),
// zone de saisie grise arrondie, aide ou erreur dessous.
import QtQuick
import QtQuick.Layouts

ColumnLayout {
    id: champ
    property string libelle
    property string aide
    property string erreur
    property bool motDePasse: false
    property alias text: saisie.text
    property alias saisie: saisie
    property alias validateur: saisie.validator
    default property alias coin: coin.data     // éléments placés à droite du libellé
    signal modifie(string texte)
    spacing: 6

    RowLayout {
        Layout.fillWidth: true
        spacing: 6
        Text { text: champ.libelle; font.pixelSize: 12; font.weight: Font.Medium; color: "#665E54" }
        Item { Layout.fillWidth: true }
        Row { id: coin; spacing: 6 }
    }
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 42
        radius: 12
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
        border.width: saisie.activeFocus || champ.erreur ? 1.5 : 0
        border.color: champ.erreur ? "#A3322A" : "#B5532F"
        TextInput {
            id: saisie
            anchors.fill: parent
            anchors.leftMargin: 14
            anchors.rightMargin: 14
            verticalAlignment: TextInput.AlignVCenter
            font.pixelSize: 14
            color: "#1F1C18"
            clip: true
            selectByMouse: true
            selectionColor: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.3)
            selectedTextColor: "#1F1C18"
            echoMode: champ.motDePasse ? TextInput.Password : TextInput.Normal
            passwordCharacter: "•"
            onTextEdited: champ.modifie(text)
        }
    }
    Text {
        visible: text !== ""
        Layout.fillWidth: true
        wrapMode: Text.WordWrap
        text: champ.erreur || champ.aide
        font.pixelSize: 12
        color: champ.erreur ? "#A3322A" : "#8A8277"
    }
}
