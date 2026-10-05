// Liste de choix en surimpression (fuseaux, formats, dispositions de clavier…), avec recherche.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls

Rectangle {
    id: liste
    property string titre
    property var modele                 // liste JS ou modèle Qt
    property var texte: function (element, index) { return String(element) }
    property bool recherche: true
    signal choisi(var element, int index)
    anchors.fill: parent
    color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.25)
    visible: false
    function ouvrir() { filtre.text = ""; visible = true; filtre.forceActiveFocus() }
    MouseArea { anchors.fill: parent; onClicked: liste.visible = false }

    Rectangle {
        anchors.centerIn: parent
        width: Math.min(parent.width - 80, 440)
        height: Math.min(parent.height - 40, 420)
        radius: 18
        color: "#FBF9F6"
        MouseArea { anchors.fill: parent }
        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 18
            spacing: 10
            RowLayout {
                Layout.fillWidth: true
                Text { Layout.fillWidth: true; text: liste.titre; font.pixelSize: 16; font.weight: Font.Medium; color: "#1F1C18" }
                Text {
                    text: "×"; font.pixelSize: 20; color: "#8A8277"
                    MouseArea { anchors.fill: parent; anchors.margins: -8; cursorShape: Qt.PointingHandCursor; onClicked: liste.visible = false }
                }
            }
            Rectangle {
                visible: liste.recherche
                Layout.fillWidth: true
                Layout.preferredHeight: 38
                radius: 10
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
                TextInput {
                    id: filtre
                    anchors.fill: parent
                    anchors.leftMargin: 12
                    anchors.rightMargin: 12
                    verticalAlignment: TextInput.AlignVCenter
                    font.pixelSize: 14
                    color: "#1F1C18"
                    clip: true
                    Text { visible: !filtre.text; anchors.verticalCenter: parent.verticalCenter; text: "Rechercher"; font.pixelSize: 14; color: "#8A8277" }
                }
            }
            ListView {
                id: vue
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                model: liste.modele
                ScrollBar.vertical: ScrollBar {}
                delegate: MouseArea {
                    id: ligne
                    readonly property string libelle: liste.texte(typeof modelData !== "undefined" ? modelData : model, index)
                    readonly property bool garde: filtre.text === "" || libelle.toLowerCase().indexOf(filtre.text.toLowerCase()) >= 0
                    width: ListView.view.width
                    height: garde ? 38 : 0
                    visible: garde
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: { liste.choisi(typeof modelData !== "undefined" ? modelData : model, index); liste.visible = false }
                    Rectangle { anchors.fill: parent; radius: 9; color: "#1F1C18"; opacity: ligne.containsMouse ? 0.06 : 0 }
                    Text { anchors.verticalCenter: parent.verticalCenter; x: 12; text: ligne.libelle; font.pixelSize: 14; color: "#1F1C18" }
                }
            }
        }
    }
}
