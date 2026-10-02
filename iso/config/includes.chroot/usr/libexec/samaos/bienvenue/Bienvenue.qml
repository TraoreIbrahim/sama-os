// Accueil du premier démarrage de Sama OS : présente les Espaces et laisse l'utilisateur choisir les siens.
// Lancé par bienvenue.sh, qui crée ensuite les Espaces choisis (activités Plasma).
// Résultat écrit sur la sortie : une ligne « SAMA_ESPACES=<nom>|<nom>… » (vide si « Plus tard »).

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2

Window {
    id: fenetre

    visibility: Window.FullScreen
    flags: Qt.FramelessWindowHint
    color: "transparent"
    title: "Bienvenue sur Sama"

    // Mode sombre : suit la palette du système (couleurs Sama Clair ou Sama Sombre)
    SystemPalette { id: palette }
    readonly property bool sombre: palette.window.hslLightness < 0.5

    // Palette de la maquette, en clair et en sombre
    readonly property color ciel: sombre ? "#151A2B" : "#F3ECE2"
    readonly property color texte: sombre ? "#F1EBE1" : "#1F1C18"
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color texte3: sombre ? "#8C867A" : "#8A8277"
    readonly property color laterite: "#B5532F"
    readonly property color lateriteEncre: sombre ? "#F0B392" : "#93401F"
    readonly property color ligne: sombre ? Qt.rgba(1, 1, 1, 0.08) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
    readonly property color champFond: sombre ? Qt.rgba(1, 1, 1, 0.06) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)

    // Prénom passé par bienvenue.sh (dernier argument)
    readonly property string prenom: {
        var a = Qt.application.arguments
        var p = a.length > 0 ? String(a[a.length - 1]) : ""
        return p.indexOf(".qml") >= 0 || p.indexOf("-") === 0 ? "" : p
    }

    // Espaces proposés : nom, couleur, fond, encre, applications (pour l'aperçu), phrase d'exemple
    ListModel {
        id: propositions
        ListElement { nom: "Travail"; teinte: "#B5532F"; fond: "#F2E1D5"; encre: "#93401F"; fondSombre: "#3DB5532F"; encreSombre: "#F0B392"; choisi: true
                      exemple: "Documents, tableaux, navigateur : vos dossiers du bureau ou de la boutique."
                      apps: "griot,docs,sheet,fichiers" }
        ListElement { nom: "École"; teinte: "#3D5A99"; fond: "#DFE5F2"; encre: "#2D4682"; fondSombre: "#523D5A99"; encreSombre: "#B4C6EE"; choisi: false
                      exemple: "Cours, devoirs et recherches, à part du reste."
                      apps: "griot,sugu,fichiers,docs" }
        ListElement { nom: "Maison"; teinte: "#2F6B57"; fond: "#DBEAE2"; encre: "#1F5544"; fondSombre: "#522F6B57"; encreSombre: "#A3D6C1"; choisi: false
                      exemple: "Photos, musique, achats et loisirs en famille."
                      apps: "griot,fichiers,photos,sugu" }
    }
    // Espaces créés par l'utilisateur
    ListModel { id: persos }

    readonly property int nombreChoisis: {
        var n = persos.count
        for (var i = 0; i < propositions.count; i++) if (propositions.get(i).choisi) n++
        return n
    }

    function terminer(avecChoix) {
        var noms = []
        if (avecChoix) {
            for (var i = 0; i < propositions.count; i++) if (propositions.get(i).choisi) noms.push(propositions.get(i).nom)
            for (var j = 0; j < persos.count; j++) noms.push(persos.get(j).nom)
        }
        console.log("SAMA_ESPACES=" + noms.join("|"))
        Qt.quit()
    }

    function ajouterPerso() {
        var nom = champ.text.trim().replace(/\|/g, "")
        if (nom.length === 0) return
        for (var i = 0; i < propositions.count; i++) {
            if (propositions.get(i).nom.toLowerCase() === nom.toLowerCase()) {
                propositions.setProperty(i, "choisi", true)
                champ.text = ""
                return
            }
        }
        for (var j = 0; j < persos.count; j++) if (persos.get(j).nom.toLowerCase() === nom.toLowerCase()) return
        persos.append({ nom: nom })
        champ.text = ""
    }

    // Voile ivoire sur le bureau
    Rectangle {
        anchors.fill: parent
        color: fenetre.sombre ? Qt.rgba(21 / 255, 26 / 255, 43 / 255, 0.97) : Qt.rgba(243 / 255, 236 / 255, 226 / 255, 0.97)
        opacity: 0
        Component.onCompleted: opacity = 1
        Behavior on opacity { NumberAnimation { duration: 500; easing.type: Easing.OutCubic } }
    }

    Item {
        anchors.fill: parent
        opacity: 0
        Component.onCompleted: opacity = 1
        Behavior on opacity { NumberAnimation { duration: 700; easing.type: Easing.OutCubic } }

        // Salutation
        ColumnLayout {
            id: entete
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: carte.top
            anchors.bottomMargin: 36
            spacing: 10

            Image {
                Layout.alignment: Qt.AlignHCenter
                source: "file:///usr/share/samaos/logo-sama.svg"
                sourceSize.width: 88
                sourceSize.height: 88
                width: 44
                height: 44
                Layout.preferredWidth: 44
                Layout.preferredHeight: 44
            }
            Text {
                Layout.alignment: Qt.AlignHCenter
                Layout.topMargin: 6
                text: fenetre.prenom ? "I ni ce, " + fenetre.prenom : "I ni ce"
                font.pixelSize: 48
                font.weight: Font.Light
                font.letterSpacing: -1
                color: fenetre.texte
            }
            Text {
                Layout.alignment: Qt.AlignHCenter
                text: "Bienvenue sur Sama"
                font.pixelSize: 20
                color: fenetre.texte2
            }
        }

        // Carte des Espaces
        Rectangle {
            id: carte
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.verticalCenter: parent.verticalCenter
            anchors.verticalCenterOffset: entete.implicitHeight / 2
            width: Math.min(parent.width - 48, 800)
            height: contenuCarte.implicitHeight + 50
            radius: 24
            color: fenetre.sombre ? Qt.rgba(30 / 255, 34 / 255, 51 / 255, 0.98) : Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.98)
            border.width: 1
            border.color: fenetre.sombre ? Qt.rgba(1, 1, 1, 0.1) : Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.1)

            ColumnLayout {
                id: contenuCarte
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.margins: 28
                anchors.topMargin: 26
                spacing: 0

                Text {
                    text: "Organisez votre ordinateur en Espaces"
                    font.pixelSize: 17
                    font.weight: Font.Medium
                    color: fenetre.texte
                }
                Text {
                    Layout.fillWidth: true
                    Layout.topMargin: 6
                    wrapMode: Text.WordWrap
                    lineHeight: 1.3
                    text: "Un Espace regroupe les applications et les fenêtres d'une partie de votre vie. "
                          + "Passez de l'un à l'autre d'un clic dans la Natte, en bas de l'écran : "
                          + "chaque Espace garde ses fenêtres, rien ne se mélange. "
                          + "Pratique aussi quand l'ordinateur est partagé en famille."
                    font.pixelSize: 13
                    color: fenetre.texte2
                }

                // Choix des Espaces proposés
                GridLayout {
                    Layout.fillWidth: true
                    Layout.topMargin: 20
                    columns: 3
                    columnSpacing: 12
                    rowSpacing: 12

                    Repeater {
                        model: propositions
                        delegate: MouseArea {
                            id: choix
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            Layout.alignment: Qt.AlignTop
                            Layout.preferredHeight: colonneChoix.implicitHeight + 32
                            Layout.preferredWidth: 1
                            hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: propositions.setProperty(index, "choisi", !model.choisi)

                            Rectangle {
                                anchors.fill: parent
                                radius: 16
                                color: model.choisi ? (fenetre.sombre ? model.fondSombre : model.fond)
                                     : fenetre.sombre ? Qt.rgba(1, 1, 1, choix.containsMouse ? 0.08 : 0.04)
                                     : (choix.containsMouse ? "#FFFFFF" : Qt.rgba(1, 1, 1, 0.6))
                                border.width: model.choisi ? 1.5 : 1
                                border.color: model.choisi ? model.teinte : fenetre.ligne
                                Behavior on color { ColorAnimation { duration: 150 } }
                            }

                            ColumnLayout {
                                id: colonneChoix
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.top: parent.top
                                anchors.margins: 16
                                spacing: 8

                                RowLayout {
                                    spacing: 8
                                    Rectangle { width: 10; height: 10; radius: 5; color: model.teinte }
                                    Text {
                                        Layout.fillWidth: true
                                        text: model.nom
                                        font.pixelSize: 15
                                        font.weight: Font.DemiBold
                                        color: model.choisi ? (fenetre.sombre ? model.encreSombre : model.encre) : fenetre.texte
                                    }
                                    // Case cochée
                                    Rectangle {
                                        width: 22; height: 22; radius: 11
                                        color: model.choisi ? model.teinte : "transparent"
                                        border.width: model.choisi ? 0 : 1.5
                                        border.color: fenetre.sombre ? Qt.rgba(1, 1, 1, 0.3) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.25)
                                        Text {
                                            anchors.centerIn: parent
                                            visible: model.choisi
                                            text: "✓"
                                            color: "#FFFFFF"
                                            font.pixelSize: 13
                                            font.weight: Font.Bold
                                        }
                                    }
                                }
                                Text {
                                    Layout.fillWidth: true
                                    wrapMode: Text.WordWrap
                                    text: model.exemple
                                    font.pixelSize: 12
                                    lineHeight: 1.25
                                    color: fenetre.texte2
                                }
                                // Aperçu des applications de l'Espace
                                Row {
                                    Layout.topMargin: 4
                                    spacing: 6
                                    Repeater {
                                        model: String(apps).split(",")
                                        delegate: Image {
                                            source: "file:///usr/share/samaos/icones/" + modelData + ".svg"
                                            sourceSize.width: 56
                                            sourceSize.height: 56
                                            width: 28
                                            height: 28
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Espaces créés par l'utilisateur
                Flow {
                    Layout.fillWidth: true
                    Layout.topMargin: persos.count > 0 ? 14 : 0
                    visible: persos.count > 0
                    spacing: 8
                    Repeater {
                        model: persos
                        delegate: Rectangle {
                            height: 32
                            width: lignePerso.implicitWidth + 28
                            radius: 16
                            color: fenetre.sombre ? Qt.rgba(1, 1, 1, 0.08) : "#EDE6DC"
                            Row {
                                id: lignePerso
                                anchors.centerIn: parent
                                spacing: 8
                                Rectangle { anchors.verticalCenter: parent.verticalCenter; width: 8; height: 8; radius: 4; color: "#8A8277" }
                                Text { anchors.verticalCenter: parent.verticalCenter; text: model.nom; font.pixelSize: 13; font.weight: Font.Medium; color: fenetre.texte }
                                Text {
                                    anchors.verticalCenter: parent.verticalCenter
                                    text: "×"
                                    font.pixelSize: 16
                                    color: fenetre.texte3
                                    MouseArea { anchors.fill: parent; anchors.margins: -6; cursorShape: Qt.PointingHandCursor; onClicked: persos.remove(index) }
                                }
                            }
                        }
                    }
                }

                // Créer un Espace à son nom
                RowLayout {
                    Layout.fillWidth: true
                    Layout.topMargin: 14
                    spacing: 10

                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 40
                        radius: 20
                        color: fenetre.champFond
                        QQC2.TextField {
                            id: champ
                            anchors.fill: parent
                            anchors.leftMargin: 16
                            anchors.rightMargin: 16
                            background: null
                            font.pixelSize: 13
                            color: fenetre.texte
                            maximumLength: 24
                            placeholderText: "Créer un autre Espace : Boutique, Église, Association…"
                            placeholderTextColor: fenetre.texte3
                            onAccepted: fenetre.ajouterPerso()
                        }
                    }
                    QQC2.AbstractButton {
                        Layout.preferredHeight: 40
                        enabled: champ.text.trim().length > 0
                        contentItem: Text {
                            text: "Ajouter"
                            font.pixelSize: 13
                            font.weight: Font.DemiBold
                            color: parent.enabled ? fenetre.lateriteEncre : fenetre.texte3
                            verticalAlignment: Text.AlignVCenter
                            leftPadding: 14
                            rightPadding: 14
                        }
                        background: Rectangle { radius: 20; color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, parent.enabled ? 0.1 : 0.04) }
                        onClicked: fenetre.ajouterPerso()
                    }
                }

                Text {
                    Layout.topMargin: 12
                    text: "Vous pourrez ajouter, renommer ou retirer des Espaces à tout moment."
                    font.pixelSize: 12
                    color: fenetre.texte3
                }

                // Boutons
                RowLayout {
                    Layout.fillWidth: true
                    Layout.topMargin: 20
                    spacing: 18

                    Item { Layout.fillWidth: true }
                    QQC2.AbstractButton {
                        contentItem: Text {
                            text: "Plus tard"
                            font.pixelSize: 14
                            font.weight: Font.Medium
                            color: fenetre.texte2
                        }
                        background: null
                        onClicked: fenetre.terminer(false)
                    }
                    QQC2.AbstractButton {
                        Layout.preferredHeight: 40
                        enabled: fenetre.nombreChoisis > 0
                        contentItem: Text {
                            text: fenetre.nombreChoisis > 1 ? "Créer mes " + fenetre.nombreChoisis + " Espaces" : "Commencer"
                            font.pixelSize: 14
                            font.weight: Font.DemiBold
                            color: "#FFFFFF"
                            verticalAlignment: Text.AlignVCenter
                            horizontalAlignment: Text.AlignHCenter
                            leftPadding: 28
                            rightPadding: 28
                        }
                        background: Rectangle {
                            radius: 20
                            color: fenetre.laterite
                            opacity: parent.enabled ? (parent.pressed ? 0.85 : 1) : 0.4
                        }
                        onClicked: fenetre.terminer(true)
                    }
                }
            }
        }
    }
}
