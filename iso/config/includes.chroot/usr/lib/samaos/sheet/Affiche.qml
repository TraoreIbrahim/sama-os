// L'image qui part (vue Image, Envoyer) : en hauteur, 1080 pixels de large, pour se lire sur un téléphone, dans une
// discussion. La phrase, les barres, le tableau complet s'il est joint (numéros masqués si on le demande), la date.
// Toujours sur fond clair, quel que soit le thème : c'est une image qu'on envoie.
import QtQuick
import QtQuick.Layouts

Rectangle {
    id: affiche
    property var image: null
    property string entete: ""
    property string pied: ""
    // Le tableau complet (facultatif) : { titres: [...], lignes: [[...]] }
    property var tableau: null
    width: 1080
    height: colonne.implicitHeight + 144
    color: "#FFFFFF"

    // Largeur de chaque colonne du tableau joint : ce que demande son plus long texte (mesuré), le reste de la place
    // partagé ; s'il n'y a pas assez de place, chacune cède à proportion
    FontMetrics { id: mesure; font.pixelSize: 30 }
    FontMetrics { id: mesureGras; font.pixelSize: 30; font.weight: Font.DemiBold }
    readonly property var largeurs: {
        if (!tableau) return []
        var besoins = [], somme = 0, place = colonne.width - 28
        for (var c = 0; c < tableau.titres.length; c++) {
            var w = mesureGras.advanceWidth(String(tableau.titres[c]))
            for (var l = 0; l < tableau.lignes.length; l++) w = Math.max(w, mesure.advanceWidth(String(tableau.lignes[l][c] || "")))
            w = Math.min(w, place * 0.45) + 20
            besoins.push(w)
            somme += w
        }
        var facteur = place / somme
        return besoins.map(function (w) { return w * facteur })
    }
    readonly property real plusGrande: {
        var m = 0
        if (!image) return 1
        for (var i = 0; i < image.barres.length; i++) {
            var s = 0
            for (var k = 0; k < image.barres[i].parts.length; k++) s += image.barres[i].parts[k].v
            m = Math.max(m, s)
        }
        return m || 1
    }

    ColumnLayout {
        id: colonne
        x: 72
        y: 72
        width: parent.width - 144
        spacing: 30

        Text { Layout.fillWidth: true; text: affiche.entete; font.pixelSize: 34; color: "#665E54"; elide: Text.ElideRight }
        Text {
            Layout.fillWidth: true
            text: affiche.image ? affiche.image.titre : ""
            font.pixelSize: 76
            font.weight: Font.DemiBold
            font.letterSpacing: -0.8
            lineHeight: 1.08
            color: "#1F1C18"
            wrapMode: Text.Wrap
        }
        Text { Layout.fillWidth: true; text: affiche.image ? affiche.image.sousTitre : ""; font.pixelSize: 34; color: "#665E54"; wrapMode: Text.Wrap }
        Row {
            visible: affiche.image !== null && affiche.image.legende.length > 0
            spacing: 34
            Repeater {
                model: affiche.image ? affiche.image.legende : []
                delegate: Row {
                    spacing: 12
                    Rectangle { width: 32; height: 32; radius: 7; color: modelData[1]; anchors.verticalCenter: parent.verticalCenter }
                    Text { text: modelData[0]; font.pixelSize: 36; color: "#1F1C18"; anchors.verticalCenter: parent.verticalCenter }
                }
            }
        }

        // Les barres
        Column {
            Layout.fillWidth: true
            Layout.topMargin: 6
            spacing: 24
            Repeater {
                model: affiche.image ? affiche.image.barres : []
                delegate: Row {
                    spacing: 22
                    Text {
                        width: 300
                        anchors.verticalCenter: parent.verticalCenter
                        horizontalAlignment: Text.AlignRight
                        text: modelData.court
                        font.pixelSize: 40
                        color: "#1F1C18"
                        elide: Text.ElideRight
                    }
                    Rectangle {
                        width: 360
                        height: 48
                        radius: 10
                        anchors.verticalCenter: parent.verticalCenter
                        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
                        clip: true
                        Row {
                            Repeater {
                                model: modelData.parts
                                delegate: Rectangle { width: 360 * modelData.v / affiche.plusGrande; height: 48; color: modelData.c }
                            }
                        }
                    }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: modelData.texte
                        font.pixelSize: 34
                        font.weight: Font.DemiBold
                        font.features: { "tnum": 1 }
                        color: modelData.encre
                    }
                }
            }
        }

        // Le tableau complet
        ColumnLayout {
            visible: affiche.tableau !== null
            Layout.fillWidth: true
            Layout.topMargin: 22
            spacing: 0
            Text { text: "Le tableau"; font.pixelSize: 40; font.weight: Font.DemiBold; color: "#1F1C18"; Layout.bottomMargin: 14 }
            Repeater {
                model: affiche.tableau ? [affiche.tableau.titres].concat(affiche.tableau.lignes) : []
                delegate: Rectangle {
                    id: ligneTableau
                    readonly property bool titres: index === 0
                    Layout.fillWidth: true
                    Layout.preferredHeight: 64
                    color: ligneTableau.titres ? "#E4ECEF" : index % 2 === 0 ? Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.045) : "#FFFFFF"
                    Row {
                        anchors.verticalCenter: parent.verticalCenter
                        x: 14
                        Repeater {
                            model: modelData
                            delegate: Text {
                                width: affiche.largeurs[index] || 0
                                text: modelData
                                font.pixelSize: 30
                                font.weight: ligneTableau.titres ? Font.DemiBold : Font.Normal
                                font.features: { "tnum": 1 }
                                color: ligneTableau.titres ? "#194B62" : "#1F1C18"
                                elide: Text.ElideRight
                                rightPadding: 16
                            }
                        }
                    }
                    Rectangle { visible: ligneTableau.titres; anchors.bottom: parent.bottom; width: parent.width; height: 3; color: "#1F5E7A" }
                }
            }
        }

        Text { Layout.fillWidth: true; Layout.topMargin: 18; text: affiche.pied; font.pixelSize: 30; color: "#8A8277" }
    }
}
