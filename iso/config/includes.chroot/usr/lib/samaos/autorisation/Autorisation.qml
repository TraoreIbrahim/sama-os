// Fenêtre « Autorisation requise » de Sama OS (maquette ses-03), ouverte par l'agent d'autorisation
// (/usr/libexec/samaos/agent-autorisation.py) quand une action demande le mot de passe d'un administrateur.
// Argument : JSON { message, nom, role, photo, initiales, erreur, details }.
// Le mot de passe repart vers l'agent par le tube privé qui les relie (sortie d'erreur, ligne « SAMA_MDP=… »).
import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Window {
    id: fenetre
    readonly property var infos: {
        var a = Qt.application.arguments
        try { return JSON.parse(a[a.length - 1]) } catch (e) { return {} }
    }
    property bool detailsOuverts: false

    visible: false
    visibility: Window.FullScreen
    flags: Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint
    color: "transparent"
    title: "Autorisation requise"

    function autoriser() {
        if (champ.text.length === 0) return
        console.warn("SAMA_MDP=" + champ.text)
        champ.text = ""
        Qt.quit()
    }
    function annuler() { Qt.exit(1) }

    Component.onCompleted: {
        Qt.application.name = "samaos-autorisation"
        visible = true
        entree.start()
        if (infos.erreur) secousse.start()
    }

    // Voile sur tout l'écran
    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.25)
        MouseArea { anchors.fill: parent }   // les clics autour ne passent pas aux fenêtres dessous
    }

    // Carte
    Rectangle {
        id: carte
        anchors.centerIn: parent
        width: 440
        height: colonne.implicitHeight + 56
        radius: 18
        color: Couleurs.fond
        border.width: 1
        border.color: Couleurs.ligne
        transform: Translate { id: decalage }

        ParallelAnimation {
            id: entree
            NumberAnimation { target: carte; property: "opacity"; from: 0; to: 1; duration: 180; easing.type: Easing.OutCubic }
            NumberAnimation { target: carte; property: "scale"; from: 0.96; to: 1; duration: 220; easing.type: Easing.OutCubic }
        }
        // Mot de passe incorrect : la carte fait « non » de la tête
        SequentialAnimation {
            id: secousse
            loops: 1
            NumberAnimation { target: decalage; property: "x"; to: -10; duration: 50 }
            NumberAnimation { target: decalage; property: "x"; to: 10; duration: 70 }
            NumberAnimation { target: decalage; property: "x"; to: -6; duration: 60 }
            NumberAnimation { target: decalage; property: "x"; to: 0; duration: 50 }
        }

        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 28
            spacing: 0

            // Cadenas
            Rectangle {
                Layout.preferredWidth: 52
                Layout.preferredHeight: 52
                radius: 16
                color: Couleurs.selection
                Canvas {
                    anchors.centerIn: parent
                    width: 24
                    height: 24
                    onPaint: {
                        var c = getContext("2d")
                        c.reset()
                        c.strokeStyle = Couleurs.laterite
                        c.lineWidth = 1.8
                        c.lineCap = "round"
                        c.lineJoin = "round"
                        c.path = "M7 11V8a5 5 0 0 1 10 0v3 M5 11h14v10H5z M12 15v2"
                        c.stroke()
                    }
                }
            }
            Text {
                Layout.topMargin: 18
                text: "Autorisation requise"
                font.pixelSize: 20
                font.weight: Font.Medium
                color: Couleurs.texte
            }
            Text {
                Layout.topMargin: 8
                Layout.fillWidth: true
                text: (fenetre.infos.message || "Une action demande l'accord d'un administrateur.") + " Saisissez votre mot de passe pour continuer."
                wrapMode: Text.WordWrap
                lineHeight: 1.15
                font.pixelSize: 14
                color: Couleurs.texte2
            }

            // Compte qui autorise
            RowLayout {
                Layout.topMargin: 20
                spacing: 10
                Rectangle {
                    Layout.preferredWidth: 28
                    Layout.preferredHeight: 28
                    radius: 14
                    color: Couleurs.laterite
                    clip: true
                    Text {
                        anchors.centerIn: parent
                        visible: photo.status !== Image.Ready
                        text: fenetre.infos.initiales || ""
                        font.pixelSize: 11
                        font.weight: Font.Medium
                        color: "#FFFFFF"
                    }
                    Image {
                        id: photo
                        anchors.fill: parent
                        source: fenetre.infos.photo ? "file://" + fenetre.infos.photo : ""
                        fillMode: Image.PreserveAspectCrop
                        sourceSize.width: 56
                        sourceSize.height: 56
                        visible: status === Image.Ready
                    }
                }
                Text { text: fenetre.infos.nom || ""; font.pixelSize: 13; font.weight: Font.Medium; color: Couleurs.texte }
                Text { visible: !!fenetre.infos.role; text: "· " + (fenetre.infos.role || ""); font.pixelSize: 12; color: Couleurs.texte3 }
            }

            // Mot de passe
            Rectangle {
                Layout.topMargin: 12
                Layout.fillWidth: true
                Layout.preferredHeight: 42
                radius: 11
                color: Couleurs.carte
                border.width: 1.5
                border.color: fenetre.infos.erreur && champ.text === "" ? "#A3322A" : Couleurs.laterite
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 14
                    anchors.rightMargin: 14
                    spacing: 10
                    Canvas {
                        Layout.preferredWidth: 16
                        Layout.preferredHeight: 16
                        onPaint: {
                            var c = getContext("2d")
                            c.reset()
                            c.scale(16 / 24, 16 / 24)
                            c.strokeStyle = Couleurs.texte3
                            c.lineWidth = 1.8
                            c.lineCap = "round"
                            c.lineJoin = "round"
                            c.path = "M14.5 9.5a4.5 4.5 0 1 1-9 0a4.5 4.5 0 1 1 9 0z M13 13l7 7 M17 17l2-2"
                            c.stroke()
                        }
                    }
                    QQC2.TextField {
                        id: champ
                        Layout.fillWidth: true
                        background: null
                        echoMode: TextInput.Password
                        font.pixelSize: 14
                        color: Couleurs.texte
                        placeholderText: "Mot de passe"
                        placeholderTextColor: Couleurs.texte3
                        focus: true
                        Component.onCompleted: forceActiveFocus()
                        onAccepted: fenetre.autoriser()
                        Keys.onEscapePressed: fenetre.annuler()
                    }
                }
            }
            Text {
                visible: !!fenetre.infos.erreur
                Layout.topMargin: 8
                text: fenetre.infos.erreur || ""
                font.pixelSize: 12
                color: "#A3322A"
            }

            // Détails (action demandée, programme)
            Text {
                Layout.topMargin: 14
                text: (fenetre.detailsOuverts ? "▾  " : "▸  ") + "Voir les détails"
                font.pixelSize: 13
                font.weight: Font.Medium
                color: Couleurs.texte2
                MouseArea { anchors.fill: parent; anchors.margins: -4; cursorShape: Qt.PointingHandCursor; onClicked: fenetre.detailsOuverts = !fenetre.detailsOuverts }
            }
            Text {
                visible: fenetre.detailsOuverts
                Layout.topMargin: 6
                Layout.fillWidth: true
                text: fenetre.infos.details || ""
                wrapMode: Text.WrapAnywhere
                font.pixelSize: 11
                font.family: "Noto Sans Mono"
                color: Couleurs.texte3
            }

            // Boutons
            RowLayout {
                Layout.topMargin: 22
                Layout.alignment: Qt.AlignRight
                spacing: 10
                QQC2.AbstractButton {
                    implicitWidth: libelleAnnuler.implicitWidth + 40
                    implicitHeight: 38
                    onClicked: fenetre.annuler()
                    background: Rectangle { radius: 19; color: Couleurs.carte }
                    contentItem: Text { id: libelleAnnuler; text: "Annuler"; font.pixelSize: 14; font.weight: Font.Medium; color: Couleurs.texte; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                }
                QQC2.AbstractButton {
                    implicitWidth: libelleAutoriser.implicitWidth + 44
                    implicitHeight: 38
                    enabled: champ.text.length > 0
                    onClicked: fenetre.autoriser()
                    background: Rectangle { radius: 19; color: Couleurs.laterite; opacity: parent.enabled ? 1 : 0.5 }
                    contentItem: Text { id: libelleAutoriser; text: "Autoriser"; font.pixelSize: 14; font.weight: Font.DemiBold; color: "#FFFFFF"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                }
            }
        }
    }
}
