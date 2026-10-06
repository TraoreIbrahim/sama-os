// Accueil de l'aide : « Bonjour Ibrahim, que cherchez-vous ? », thèmes, articles à lire en premier, aide humaine.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: vue
    implicitHeight: colonne.implicitHeight + 40
    readonly property var premiers: fenetre.articles.filter(function (a) { return a.premier })

    ColumnLayout {
        id: colonne
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.leftMargin: 44
        anchors.rightMargin: 44
        spacing: 0

        // ——— En-tête et recherche ———
        ColumnLayout {
            Layout.fillWidth: true
            Layout.topMargin: 24
            Layout.bottomMargin: 22
            spacing: 12
            RowLayout {
                Layout.alignment: Qt.AlignHCenter
                spacing: 10
                Rectangle {
                    implicitWidth: pastille.implicitWidth + 24
                    implicitHeight: 26
                    radius: 13
                    color: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.12)
                    RowLayout {
                        id: pastille
                        anchors.centerIn: parent
                        spacing: 6
                        Picto { width: 14; height: 14; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: Couleurs.sombre ? "#A3D6C1" : "#1F5544" }
                        Text { text: "Disponible sans Internet"; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.sombre ? "#A3D6C1" : "#1F5544" }
                    }
                }
                Text {
                    text: fenetre.articles.length + " articles · mis à jour le " + (fenetre.index ? fenetre.index.misAJour : "")
                    font.pixelSize: 12
                    color: Couleurs.texte3
                }
            }
            Text {
                Layout.fillWidth: true
                horizontalAlignment: Text.AlignHCenter
                text: (fenetre.prenom ? "Bonjour " + fenetre.prenom + ", que" : "Que") + " cherchez-vous ?"
                font.pixelSize: 28
                font.weight: Font.Medium
                font.letterSpacing: -0.2
                color: Couleurs.texte
            }
            Rectangle {
                Layout.alignment: Qt.AlignHCenter
                Layout.preferredWidth: Math.min(620, colonne.width)
                Layout.preferredHeight: 54
                radius: 27
                color: Couleurs.champ
                border.width: champ.activeFocus ? 1.5 : 0.5
                border.color: champ.activeFocus ? Couleurs.laterite : Couleurs.bord
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 22
                    anchors.rightMargin: 8
                    spacing: 12
                    Picto { width: 18; height: 18; trace: "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z M20 20l-4.8-4.8"; encre: Couleurs.texte3 }
                    QQC2.TextField {
                        id: champ
                        Layout.fillWidth: true
                        placeholderText: "Comment pouvons-nous vous aider ?"
                        placeholderTextColor: Couleurs.texte3
                        color: Couleurs.texte
                        font.pixelSize: 16
                        background: null
                        leftPadding: 0
                        focus: true
                        onAccepted: fenetre.chercher(text)
                    }
                    QQC2.AbstractButton {
                        id: boutonChercher
                        implicitHeight: 38
                        implicitWidth: libelle.implicitWidth + 32
                        hoverEnabled: true
                        onClicked: fenetre.chercher(champ.text)
                        background: Rectangle { radius: 19; color: Couleurs.laterite; opacity: boutonChercher.hovered ? 0.92 : 1 }
                        contentItem: Text { id: libelle; text: "Chercher"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#FFFFFF" }
                    }
                }
            }
        }

        // ——— Thèmes ———
        Text { Layout.bottomMargin: 12; text: "Thèmes"; font.pixelSize: 15; font.weight: Font.DemiBold; color: Couleurs.texte }
        GridLayout {
            Layout.fillWidth: true
            columns: colonne.width > 900 ? 3 : 2
            columnSpacing: 12
            rowSpacing: 12
            Repeater {
                model: fenetre.index ? fenetre.index.themes : []
                delegate: MouseArea {
                    id: carteTheme
                    Layout.fillWidth: true
                    Layout.preferredWidth: 1
                    Layout.preferredHeight: 80
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: fenetre.aller({ type: "theme", id: modelData.id })
                    Rectangle {
                        anchors.fill: parent
                        radius: 16
                        color: Couleurs.champ
                        border.width: 0.5
                        border.color: carteTheme.containsMouse ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.45) : Couleurs.ligne
                    }
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 16
                        anchors.rightMargin: 14
                        spacing: 14
                        Rectangle {
                            Layout.preferredWidth: 42
                            Layout.preferredHeight: 42
                            radius: 13
                            color: modelData.fond
                            Picto { anchors.centerIn: parent; width: 20; height: 20; trace: modelData.picto; encre: modelData.encre }
                        }
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 2
                            Text { text: modelData.titre; font.pixelSize: 14; font.weight: Font.Medium; color: Couleurs.texte }
                            Text { Layout.fillWidth: true; text: modelData.resume; elide: Text.ElideRight; font.pixelSize: 12; color: Couleurs.texte2 }
                            Text { text: modelData.articles.length + (modelData.articles.length > 1 ? " articles" : " article"); font.pixelSize: 11; color: Couleurs.texte3 }
                        }
                        Picto { trace: "M9 6l6 6l-6 6"; encre: Couleurs.texte3 }
                    }
                }
            }
        }

        // ——— À lire en premier, et aide humaine ———
        RowLayout {
            Layout.fillWidth: true
            Layout.topMargin: 24
            spacing: 24
            ColumnLayout {
                Layout.fillWidth: true
                Layout.alignment: Qt.AlignTop
                spacing: 10
                RowLayout {
                    spacing: 10
                    Text { text: "À lire en premier"; font.pixelSize: 15; font.weight: Font.DemiBold; color: Couleurs.texte }
                    Text { text: "L'essentiel pour bien démarrer"; font.pixelSize: 12; color: Couleurs.texte3 }
                }
                GridLayout {
                    Layout.fillWidth: true
                    columns: 2
                    columnSpacing: 10
                    rowSpacing: 10
                    Repeater {
                        model: vue.premiers
                        delegate: CarteArticle { Layout.fillWidth: true; Layout.fillHeight: true; Layout.preferredWidth: 1; article: modelData }
                    }
                }
            }
            // Aide humaine (et ce que Sama ne fera jamais)
            Rectangle {
                Layout.preferredWidth: 330
                Layout.alignment: Qt.AlignTop
                Layout.preferredHeight: humaine.implicitHeight + 32
                radius: 18
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : 0.05)
                ColumnLayout {
                    id: humaine
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.top: parent.top
                    anchors.margins: 16
                    anchors.leftMargin: 18
                    anchors.rightMargin: 18
                    spacing: 12
                    RowLayout {
                        spacing: 12
                        Rectangle {
                            Layout.preferredWidth: 40
                            Layout.preferredHeight: 40
                            radius: 20
                            color: Couleurs.laterite
                            Image { anchors.centerIn: parent; width: 26; height: 26; sourceSize: Qt.size(52, 52); source: "file:///usr/share/samaos/icones/elephant-cour.svg" }
                        }
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 2
                            Text { text: "Besoin d'aide humaine ?"; font.pixelSize: 15; font.weight: Font.DemiBold; color: Couleurs.texte }
                            Text { Layout.fillWidth: true; wrapMode: Text.WordWrap; text: "Demandez à une personne de confiance de venir regarder avec vous."; font.pixelSize: 12; color: Couleurs.texte2 }
                        }
                    }
                    Rectangle { Layout.fillWidth: true; height: 0.5; color: Couleurs.ligne }
                    MouseArea {
                        Layout.fillWidth: true
                        Layout.preferredHeight: garde.implicitHeight
                        cursorShape: Qt.PointingHandCursor
                        onClicked: fenetre.ouvrirArticle("securite/arnaques")
                        RowLayout {
                            id: garde
                            width: parent.width
                            spacing: 10
                            Picto { Layout.alignment: Qt.AlignTop; width: 18; height: 18; trace: "M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6z M12 8v5 M12 16h.01"; encre: Couleurs.lateriteEncre }
                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2
                                Text { Layout.fillWidth: true; wrapMode: Text.WordWrap; text: "Sama ne vous appellera jamais"; font.pixelSize: 13; font.weight: Font.Medium; color: Couleurs.texte }
                                Text { Layout.fillWidth: true; wrapMode: Text.WordWrap; text: "et ne vous demandera ni mot de passe, ni code, ni argent."; font.pixelSize: 12; color: Couleurs.texte2 }
                                Text { text: "Reconnaître une arnaque ›"; font.pixelSize: 12; font.weight: Font.Medium; color: Couleurs.lateriteEncre }
                            }
                        }
                    }
                    Rectangle { Layout.fillWidth: true; height: 0.5; color: Couleurs.ligne }
                    RowLayout {
                        spacing: 10
                        Picto { Layout.alignment: Qt.AlignTop; width: 18; height: 18; trace: "M8 11a3 3 0 1 0 0-6a3 3 0 1 0 0 6z M16 11a3 3 0 1 0 0-6a3 3 0 1 0 0 6z M3 20c0-3 2.2-5 5-5s5 2 5 5 M13 16.5c.8-1 1.8-1.5 3-1.5c2.8 0 5 2 5 5"; encre: Couleurs.texte2 }
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 2
                            Text { text: "Communauté Sama"; font.pixelSize: 13; font.weight: Font.Medium; color: Couleurs.texte }
                            Text { Layout.fillWidth: true; wrapMode: Text.WordWrap; text: "Bientôt · nécessitera une connexion Internet"; font.pixelSize: 12; color: Couleurs.texte3 }
                        }
                    }
                }
            }
        }
    }
}
