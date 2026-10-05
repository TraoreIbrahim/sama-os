/* Installateur de Sama — pendant la copie des fichiers (maquette dem-06) : à gauche ce qui se passe, à droite
 * « À découvrir », quatre cartes qui présentent Sama. La barre de progression de Calamares est dessous
 * (habillée par stylesheet.qss).
 */
import QtQuick
import QtQuick.Layouts

Rectangle {
    id: diaporama
    color: "#FBF9F6"

    property bool activatedInCalamares: false
    function onActivate() { activatedInCalamares = true; courante = 0 }
    function onLeave() { activatedInCalamares = false }

    property int courante: 0
    readonly property int nombre: cartes.length
    function aller(n) { courante = (n + nombre) % nombre; minuterie.restart() }

    Timer {
        id: minuterie
        interval: 11000
        running: diaporama.activatedInCalamares
        repeat: true
        onTriggered: diaporama.courante = (diaporama.courante + 1) % diaporama.nombre
    }

    readonly property var cartes: [
        { titre: "Les Espaces : séparez Travail, École et Maison",
          texte: "Chaque Espace garde ses applications et ses fenêtres. Passez de l'un à l'autre depuis la Natte, en bas de l'écran." },
        { titre: "La Cour : tout part de l'éléphant",
          texte: "Touchez l'éléphant dans la Natte, puis tapez quelques lettres : applications, fichiers et réglages se retrouvent au même endroit." },
        { titre: "Pensé pour votre forfait",
          texte: "La carte Data du bureau montre ce que vous avez consommé et ce qu'il reste de votre forfait, sans ouvrir d'application." },
        { titre: "Dans votre langue",
          texte: "Français, English et Kiswahili aujourd'hui ; dioula, wolof, haoussa, yoruba et lingala arrivent. Les lettres ɛ ɔ ɲ ŋ sont déjà au clavier." }
    ]

    // Paysage de fond des illustrations (collines au soleil), dans les teintes de la carte
    component Paysage: Canvas {
        property color ciel: "#F3ECE2"
        property color soleil: "#EDC6A2"
        property var collines: ["#E7D5C1", "#DDC2A6", "#D0AC8B"]
        property real soleilX: 250
        anchors.fill: parent
        onPaint: {
            var c = getContext("2d"); c.reset()
            c.scale(width / 340, height / 150)
            c.fillStyle = ciel; c.fillRect(0, 0, 340, 150)
            c.fillStyle = soleil; c.beginPath(); c.arc(soleilX, 70, 30, 0, Math.PI * 2); c.fill()
            var formes = ["M0 92 C 60 80, 110 84, 160 90 S 260 80, 340 86 L340 150 L0 150 Z",
                          "M0 112 C 70 102, 130 104, 190 110 S 290 100, 340 106 L340 150 L0 150 Z",
                          "M0 132 C 80 124, 150 128, 210 132 S 300 124, 340 128 L340 150 L0 150 Z"]
            for (var i = 0; i < 3; i++) { c.fillStyle = collines[i]; c.path = formes[i]; c.fill() }
        }
    }
    // Pilule claire posée sur l'illustration
    component Pilule: Rectangle {
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 14
        height: 38
        radius: 19
        color: Qt.rgba(250 / 255, 246 / 255, 240 / 255, 0.85)
        border.width: 0.5
        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
    }
    component Elephant: Rectangle {
        width: 28
        height: 28
        radius: 14
        color: "#B5532F"
        Image { anchors.centerIn: parent; width: 16; height: 16; sourceSize.width: 32; sourceSize.height: 32; source: "file:///usr/share/samaos/logo-cour.svg" }
    }

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 40
        anchors.rightMargin: 40
        anchors.topMargin: 30
        anchors.bottomMargin: 10
        spacing: 44

        // ——— Ce qui se passe ———
        ColumnLayout {
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignTop
            spacing: 0
            Text { text: "Installation en cours"; font.pixelSize: 14; color: "#665E54" }
            Text {
                Layout.topMargin: 6
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                text: "Sama s'installe"
                font.pixelSize: 40
                font.weight: Font.Light
                font.letterSpacing: -1
                color: "#1F1C18"
            }
            Text {
                Layout.topMargin: 14
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                lineHeight: 1.3
                text: "Comptez une dizaine de minutes. Vous pouvez laisser l'ordinateur travailler : la progression s'affiche en bas de la fenêtre."
                font.pixelSize: 14
                color: "#665E54"
            }
            Repeater {
                model: [
                    { picto: "M7 3v5 M17 3v5 M5 8h14v3a7 7 0 0 1-14 0z M12 18v3", texte: "Gardez l'ordinateur branché" },
                    { picto: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7v5l3 2", texte: "Ne l'éteignez pas avant la fin" }
                ]
                delegate: RowLayout {
                    Layout.topMargin: index === 0 ? 22 : 10
                    spacing: 12
                    Rectangle {
                        Layout.preferredWidth: 30
                        Layout.preferredHeight: 30
                        radius: 9
                        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
                        Canvas {
                            anchors.centerIn: parent
                            width: 16
                            height: 16
                            onPaint: {
                                var c = getContext("2d"); c.reset(); c.scale(16 / 24, 16 / 24)
                                c.strokeStyle = "#665E54"; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                                c.path = modelData.picto; c.stroke()
                            }
                        }
                    }
                    Text { text: modelData.texte; font.pixelSize: 13; color: "#1F1C18" }
                }
            }
        }

        // ——— À découvrir ———
        ColumnLayout {
            Layout.preferredWidth: 340
            Layout.maximumWidth: 340
            Layout.alignment: Qt.AlignTop
            spacing: 14
            Text {
                text: "À DÉCOUVRIR · " + (diaporama.courante + 1) + " SUR " + diaporama.nombre
                font.pixelSize: 12
                font.weight: Font.Medium
                font.letterSpacing: 0.4
                color: "#8A8277"
            }
            // Illustrations (une par carte, en fondu)
            Item {
                Layout.fillWidth: true
                Layout.preferredHeight: 150
                Rectangle { id: masque; anchors.fill: parent; radius: 14; color: "#F3ECE2"; clip: true
                    // 1. Les Espaces : la Natte avec les trois Espaces
                    Item {
                        anchors.fill: parent
                        opacity: diaporama.courante === 0 ? 1 : 0
                        Behavior on opacity { NumberAnimation { duration: 400 } }
                        Paysage { }
                        Pilule {
                            width: rangee.implicitWidth + 10
                            Row {
                                id: rangee
                                anchors.centerIn: parent
                                spacing: 5
                                Elephant { }
                                Rectangle { width: 1; height: 18; anchors.verticalCenter: parent.verticalCenter; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16) }
                                Rectangle {
                                    height: 28
                                    width: travail.implicitWidth + 3 * 26 + 14
                                    radius: 14
                                    color: "#F2E1D5"
                                    Row {
                                        anchors.verticalCenter: parent.verticalCenter
                                        anchors.left: parent.left
                                        anchors.leftMargin: 9
                                        spacing: 4
                                        Text { id: travail; anchors.verticalCenter: parent.verticalCenter; text: "Travail"; font.pixelSize: 11; font.weight: Font.DemiBold; color: "#93401F" }
                                        Repeater {
                                            model: ["#1E2740", "#2F6B57", "#E8DCCB"]
                                            delegate: Rectangle { width: 22; height: 22; radius: 7; color: modelData }
                                        }
                                    }
                                }
                                Repeater {
                                    model: [{ nom: "École", c: "#3D5A99" }, { nom: "Maison", c: "#2F6B57" }]
                                    delegate: Row {
                                        anchors.verticalCenter: parent.verticalCenter
                                        spacing: 5
                                        leftPadding: 4
                                        rightPadding: 4
                                        Rectangle { width: 6; height: 6; radius: 3; color: modelData.c; anchors.verticalCenter: parent.verticalCenter }
                                        Text { text: modelData.nom; font.pixelSize: 11; color: "#665E54" }
                                    }
                                }
                            }
                        }
                    }
                    // 2. La Cour : l'éléphant et la recherche
                    Item {
                        anchors.fill: parent
                        opacity: diaporama.courante === 1 ? 1 : 0
                        Behavior on opacity { NumberAnimation { duration: 400 } }
                        Paysage { ciel: "#EEF0EA"; soleil: "#F2D59B"; soleilX: 80; collines: ["#DCE3D6", "#C9D4C2", "#B3C2AA"] }
                        Pilule {
                            width: 230
                            Row {
                                anchors.left: parent.left
                                anchors.leftMargin: 5
                                anchors.verticalCenter: parent.verticalCenter
                                spacing: 10
                                Elephant { }
                                Text { anchors.verticalCenter: parent.verticalCenter; text: "fich"; font.pixelSize: 13; color: "#1F1C18" }
                            }
                            Rectangle { x: 72; anchors.verticalCenter: parent.verticalCenter; width: 1.5; height: 16; color: "#B5532F" }
                            Text {
                                anchors.right: parent.right
                                anchors.rightMargin: 14
                                anchors.verticalCenter: parent.verticalCenter
                                text: "Fichiers"
                                font.pixelSize: 12
                                font.weight: Font.Medium
                                color: "#93401F"
                            }
                        }
                    }
                    // 3. Forfait : la jauge de la carte Data
                    Item {
                        anchors.fill: parent
                        opacity: diaporama.courante === 2 ? 1 : 0
                        Behavior on opacity { NumberAnimation { duration: 400 } }
                        Paysage { ciel: "#F1ECF3"; soleil: "#E9C3B4"; soleilX: 290; collines: ["#E4DBE6", "#D7CADB", "#C6B4CB"] }
                        Rectangle {
                            anchors.centerIn: parent
                            anchors.verticalCenterOffset: -4
                            width: 170
                            height: 92
                            radius: 14
                            color: Qt.rgba(250 / 255, 246 / 255, 240 / 255, 0.92)
                            Column {
                                anchors.fill: parent
                                anchors.margins: 14
                                spacing: 6
                                Text { text: "DATA"; font.pixelSize: 10; font.weight: Font.DemiBold; font.letterSpacing: 0.6; color: "#8A8277" }
                                Row {
                                    spacing: 4
                                    Text { text: "3,2"; font.pixelSize: 24; font.weight: Font.Light; color: "#1F1C18" }
                                    Text { anchors.baseline: parent.children[0].baseline; text: "Go sur 10 Go"; font.pixelSize: 11; color: "#665E54" }
                                }
                                Rectangle {
                                    width: parent.width
                                    height: 6
                                    radius: 3
                                    color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.09)
                                    Rectangle { width: parent.width * 0.32; height: 6; radius: 3; color: "#2F6B57" }
                                }
                            }
                        }
                    }
                    // 4. Langues : salutations
                    Item {
                        anchors.fill: parent
                        opacity: diaporama.courante === 3 ? 1 : 0
                        Behavior on opacity { NumberAnimation { duration: 400 } }
                        Paysage { ciel: "#F5EEDF"; soleil: "#F0CF8C"; soleilX: 170; collines: ["#EADBC0", "#E0C9A2", "#D3B482"] }
                        Repeater {
                            model: [{ t: "I ni ce", x: 24, y: 22 }, { t: "Nanga def", x: 196, y: 16 }, { t: "Habari", x: 120, y: 62 },
                                    { t: "Sannu", x: 34, y: 98 }, { t: "Bonjour", x: 222, y: 92 }]
                            delegate: Rectangle {
                                x: modelData.x
                                y: modelData.y
                                width: salut.implicitWidth + 22
                                height: 30
                                radius: 15
                                color: index === 0 ? "#B5532F" : Qt.rgba(250 / 255, 246 / 255, 240 / 255, 0.92)
                                Text { id: salut; anchors.centerIn: parent; text: modelData.t; font.pixelSize: 13; font.weight: Font.Medium; color: index === 0 ? "#FFFFFF" : "#1F1C18" }
                            }
                        }
                    }
                }
            }
            Text {
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                lineHeight: 1.15
                text: diaporama.cartes[diaporama.courante].titre
                font.pixelSize: 17
                font.weight: Font.Medium
                color: "#1F1C18"
            }
            Text {
                Layout.fillWidth: true
                Layout.preferredHeight: 62
                wrapMode: Text.WordWrap
                lineHeight: 1.3
                text: diaporama.cartes[diaporama.courante].texte
                font.pixelSize: 13
                color: "#665E54"
            }
            RowLayout {
                Layout.fillWidth: true
                spacing: 6
                Repeater {
                    model: diaporama.nombre
                    delegate: Rectangle {
                        Layout.preferredWidth: index === diaporama.courante ? 18 : 6
                        Layout.preferredHeight: 6
                        radius: 3
                        color: index === diaporama.courante ? "#B5532F" : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                        Behavior on Layout.preferredWidth { NumberAnimation { duration: 200 } }
                        MouseArea { anchors.fill: parent; anchors.margins: -6; cursorShape: Qt.PointingHandCursor; onClicked: diaporama.aller(index) }
                    }
                }
                Item { Layout.fillWidth: true }
                Repeater {
                    model: [{ p: "M15 6l-6 6l6 6", d: -1 }, { p: "M9 6l6 6l-6 6", d: 1 }]
                    delegate: MouseArea {
                        id: fleche
                        Layout.preferredWidth: 30
                        Layout.preferredHeight: 30
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: diaporama.aller(diaporama.courante + modelData.d)
                        Rectangle { anchors.fill: parent; radius: 15; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, fleche.containsMouse ? 0.09 : 0.05) }
                        Canvas {
                            anchors.centerIn: parent
                            width: 14
                            height: 14
                            onPaint: {
                                var c = getContext("2d"); c.reset(); c.scale(14 / 24, 14 / 24)
                                c.strokeStyle = "#665E54"; c.lineWidth = 2; c.lineCap = "round"; c.lineJoin = "round"
                                c.path = modelData.p; c.stroke()
                            }
                        }
                    }
                }
            }
        }
    }
}
