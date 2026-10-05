// Icône d'un fichier (maquette fic-01) : dossier ocre du thème Sama, page blanche avec une étiquette de couleur
// (DOCX, XLSX, PDF…), ou vignette de l'image ; en grand, la première page d'un PDF et une image d'une vidéo.
import QtQuick
import org.kde.kirigami as Kirigami
import "Types.js" as Types

Item {
    id: icone
    property string nom
    property bool dossier: false
    property url apercu: ""          // image : sa vignette
    property string chemin: ""       // PDF, vidéo : l'aperçu est demandé à Fichiers (fichiers.py vignette)
    readonly property string famille: Types.famille(nom, dossier)
    readonly property string extension: Types.extension(nom).toUpperCase()
    readonly property color teinte: Types.familles[famille].teinte

    readonly property bool avecApercu: chemin !== "" && (famille === "pdf" || famille === "video") && width >= 40
    property string sourceApercu: ""
    function demanderApercu() { sourceApercu = avecApercu ? fenetre.apercu(chemin) : "" }
    onCheminChanged: demanderApercu()
    onAvecApercuChanged: demanderApercu()
    Component.onCompleted: demanderApercu()
    Connections {
        target: icone.avecApercu ? fenetre : null
        function onApercuPret(c, source) { if (c === icone.chemin) icone.sourceApercu = source }
    }
    readonly property bool pagePrete: famille === "pdf" && pagePdf.status === Image.Ready
    readonly property bool filmPret: famille === "video" && imageFilm.status === Image.Ready

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
    // Vidéo : une image du film, avec le bouton de lecture
    Rectangle {
        visible: icone.filmPret
        anchors.centerIn: parent
        width: parent.width
        height: Math.round(parent.width * 0.66)
        radius: 6
        color: "#1E2740"
        clip: true
        Image {
            id: imageFilm
            anchors.fill: parent
            source: icone.famille === "video" ? icone.sourceApercu : ""
            sourceSize.width: 256
            fillMode: Image.PreserveAspectCrop
            asynchronous: true
            cache: false
        }
        Rectangle { anchors.fill: parent; radius: 6; color: "transparent"; border.width: 1; border.color: Qt.rgba(0, 0, 0, 0.12) }
        Rectangle {
            anchors.centerIn: parent
            width: Math.round(Math.min(icone.width, icone.height) * 0.34)
            height: width
            radius: width / 2
            color: Qt.rgba(15 / 255, 18 / 255, 28 / 255, 0.6)
            border.width: 1
            border.color: Qt.rgba(1, 1, 1, 0.55)
            Canvas {
                anchors.fill: parent
                onPaint: {
                    var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
                    c.fillStyle = "#FFFFFF"; c.beginPath(); c.moveTo(10, 8); c.lineTo(16.5, 12); c.lineTo(10, 16); c.closePath(); c.fill()
                }
            }
        }
    }
    // Page avec étiquette (PDF : sa première page)
    Item {
        visible: !icone.dossier && !(icone.famille === "image" && vignette.status === Image.Ready) && !icone.filmPret
        anchors.centerIn: parent
        width: parent.height * 0.72
        height: parent.height * 0.92
        Rectangle {
            anchors.fill: parent
            radius: icone.pagePrete ? 2 : Math.max(3, width * 0.1)
            color: "#FFFFFF"
            border.width: 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
            Image {
                id: pagePdf
                anchors.fill: parent
                anchors.margins: 1
                source: icone.famille === "pdf" ? icone.sourceApercu : ""
                sourceSize.width: 192
                fillMode: Image.PreserveAspectCrop
                verticalAlignment: Image.AlignTop
                asynchronous: true
                cache: false
            }
            Column {
                visible: !icone.pagePrete
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
