/* Installateur de Sama — « Prêt à installer » : le résumé des choix (langue et région, clavier, disque,
 * compte) d'après Calamares, avant le bouton « Installer maintenant ». Rien n'est encore modifié à ce stade.
 */
import io.calamares.core 1.0
import io.calamares.ui 1.0
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls

Rectangle {
    id: page
    color: "#FBF9F6"

    // Pictogramme d'après le titre de l'étape (titres traduits par Calamares)
    function picto(titre) {
        var t = String(titre).toLowerCase()
        if (/clavier|keyboard/.test(t)) return "M3 6h18v12H3z M7 10h0.01 M11 10h0.01 M15 10h0.01 M7 14h10"
        if (/partition|disque|disk/.test(t)) return "M4 6h16v12H4z M4 13h16 M8 16h0.01"
        if (/utilisateur|user|compte/.test(t)) return "M12 12a4 4 0 1 0 0-8a4 4 0 1 0 0 8z M4.5 20c0-4 3.4-6.5 7.5-6.5s7.5 2.5 7.5 6.5"
        return "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9 M12 3c-2.5 2.7-3.8 5.7-3.8 9s1.3 6.3 3.8 9"
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.leftMargin: 40
        anchors.rightMargin: 40
        anchors.topMargin: 30
        spacing: 0

        Text { text: "Prêt à installer"; font.pixelSize: 26; font.weight: Font.Medium; color: "#1F1C18" }
        Text {
            Layout.topMargin: 6
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            text: "Vérifiez vos choix. Rien n'est encore modifié sur cet ordinateur : vous pouvez revenir en arrière pour changer ce qui ne convient pas."
            font.pixelSize: 14
            color: "#665E54"
        }

        // Le disque va être modifié
        Rectangle {
            Layout.topMargin: 18
            Layout.fillWidth: true
            Layout.preferredHeight: avertissement.implicitHeight + 24
            radius: 12
            color: Qt.rgba(201 / 255, 138 / 255, 27 / 255, 0.12)
            RowLayout {
                id: avertissement
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                anchors.leftMargin: 14
                anchors.rightMargin: 14
                spacing: 10
                Canvas {
                    Layout.preferredWidth: 18
                    Layout.preferredHeight: 18
                    Layout.alignment: Qt.AlignTop
                    onPaint: {
                        var c = getContext("2d"); c.reset(); c.scale(18 / 24, 18 / 24)
                        c.strokeStyle = "#8A5A0B"; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                        c.path = "M12 4l9 16H3z M12 10v4 M12 17h0.01"; c.stroke()
                    }
                }
                Text {
                    Layout.fillWidth: true
                    wrapMode: Text.WordWrap
                    text: "« Installer maintenant » modifie le disque choisi. Si des fichiers importants s'y trouvent, copiez-les ailleurs avant de continuer."
                    font.pixelSize: 13
                    color: "#6B4708"
                }
            }
        }

        ListView {
            id: liste
            Layout.topMargin: 16
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            spacing: 10
            boundsBehavior: Flickable.StopAtBounds
            model: config.summaryModel
            ScrollBar.vertical: ScrollBar { policy: liste.contentHeight > liste.height ? ScrollBar.AsNeeded : ScrollBar.AlwaysOff }
            footer: Item { height: 16 }
            delegate: Rectangle {
                width: liste.width - (liste.contentHeight > liste.height ? 12 : 0)
                height: ligne.implicitHeight + 28
                radius: 14
                color: "#FFFFFF"
                border.width: 1
                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                RowLayout {
                    id: ligne
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.top: parent.top
                    anchors.margins: 14
                    spacing: 14
                    Rectangle {
                        Layout.preferredWidth: 34
                        Layout.preferredHeight: 34
                        Layout.alignment: Qt.AlignTop
                        radius: 10
                        color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.1)
                        Canvas {
                            anchors.centerIn: parent
                            width: 18
                            height: 18
                            onPaint: {
                                var c = getContext("2d"); c.reset(); c.scale(18 / 24, 18 / 24)
                                c.strokeStyle = "#93401F"; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                                c.path = page.picto(model.title); c.stroke()
                            }
                        }
                    }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 4
                        Text { Layout.fillWidth: true; text: model.title; font.pixelSize: 14; font.weight: Font.Medium; color: "#1F1C18"; wrapMode: Text.WordWrap }
                        Text {
                            Layout.fillWidth: true
                            text: model.message
                            textFormat: Text.StyledText
                            wrapMode: Text.WordWrap
                            lineHeight: 1.2
                            font.pixelSize: 13
                            color: "#665E54"
                        }
                    }
                }
            }
        }
    }
}
