// Icône d'un fichier (maquette fic-01) : dossier ocre du thème Sama, page blanche avec une étiquette de couleur
// (DOCX, XLSX, PDF…), ou vignette de l'image.
import QtQuick
import org.kde.kirigami as Kirigami
import "Types.js" as Types

Item {
    id: icone
    property string nom
    property bool dossier: false
    property url apercu: ""          // image : sa vignette
    readonly property string famille: Types.famille(nom, dossier)
    readonly property string extension: Types.extension(nom).toUpperCase()
    readonly property color teinte: Types.familles[famille].teinte

    Kirigami.Icon {
        visible: icone.dossier
        anchors.fill: parent
        source: "folder"
    }
    // Vignette d'image
    Rectangle {
        visible: !icone.dossier && icone.famille === "image" && vignette.status === Image.Ready
        anchors.centerIn: parent
        width: parent.width * 0.86
        height: parent.height * 0.86
        radius: 6
        color: "transparent"
        clip: true
        Image {
            id: vignette
            anchors.fill: parent
            source: !icone.dossier && icone.famille === "image" ? icone.apercu : ""
            sourceSize.width: 192
            sourceSize.height: 192
            fillMode: Image.PreserveAspectCrop
            asynchronous: true
        }
        Rectangle { anchors.fill: parent; radius: 6; color: "transparent"; border.width: 1; border.color: Qt.rgba(0, 0, 0, 0.08) }
    }
    // Page avec étiquette
    Item {
        visible: !icone.dossier && !(icone.famille === "image" && vignette.status === Image.Ready)
        anchors.centerIn: parent
        width: parent.height * 0.72
        height: parent.height * 0.92
        Rectangle {
            anchors.fill: parent
            radius: Math.max(3, width * 0.1)
            color: "#FFFFFF"
            border.width: 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
            Column {
                x: parent.width * 0.2
                y: parent.height * 0.18
                spacing: parent.height * 0.08
                Repeater {
                    model: 3
                    delegate: Rectangle { width: icone.width * 0.4 * (index === 2 ? 0.6 : 1); height: Math.max(1, icone.height * 0.03); radius: height / 2; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12) }
                }
            }
        }
        Rectangle {
            visible: icone.extension !== "" && icone.width >= 28
            x: -parent.width * 0.12
            y: parent.height * 0.62
            width: etiquette.implicitWidth + 8
            height: Math.max(11, icone.height * 0.17)
            radius: 3
            color: icone.teinte
            Text {
                id: etiquette
                anchors.centerIn: parent
                text: icone.extension.length > 4 ? icone.extension.substring(0, 4) : icone.extension
                font.pixelSize: Math.max(7, icone.height * 0.11)
                font.weight: Font.Bold
                color: "#FFFFFF"
            }
        }
    }
}
