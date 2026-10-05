// « Sama Docs ne répond pas » (maquette ale-05) : panneau posé en haut de l'écran, sur un voile, quand une fenêtre ne
// répond plus. Lancé par /usr/libexec/samaos/application-bloquee (à la place de l'aide de KWin), qui lui passe en
// JSON { nom, icone, titre, documents: [{ titre, quand }] } et lit le choix (« SAMA_CHOIX=forcer » ou « attendre »).
import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtQuick.Effects
import org.kde.kirigami as Kirigami

Window {
    id: fenetre
    visibility: Window.FullScreen
    flags: Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint
    color: "transparent"
    title: infos.nom + " ne répond pas"

    readonly property var infos: {
        var a = Qt.application.arguments
        try { return JSON.parse(String(a[a.length - 1])) } catch (e) { return { nom: "L'application" } }
    }
    readonly property var documents: infos.documents || []

    SystemPalette { id: palette }
    readonly property bool sombre: palette.window.hslLightness < 0.5
    readonly property color fond: sombre ? "#1E2233" : "#FBF9F6"
    readonly property color texte: sombre ? "#F1EBE1" : "#1F1C18"
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color foret: sombre ? "#A3D6C1" : "#2F6B57"
    readonly property color rouge: "#A3322A"

    function heure(s) { return new Date(s * 1000).toLocaleTimeString(Qt.locale(), "HH:mm") }
    function choisir(choix) { console.log("SAMA_CHOIX=" + choix); Qt.quit() }
    Shortcut { sequence: "Escape"; onActivated: fenetre.choisir("attendre") }

    Rectangle {
        anchors.fill: parent
        color: fenetre.sombre ? Qt.rgba(21 / 255, 26 / 255, 43 / 255, 0.5) : Qt.rgba(243 / 255, 236 / 255, 226 / 255, 0.5)
    }

    component Picto: Canvas {
        property string trace
        property color encre: fenetre.texte
        onTraceChanged: requestPaint()
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
            c.strokeStyle = encre; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }

    MultiEffect {
        source: panneau
        anchors.fill: panneau
        shadowEnabled: true
        shadowColor: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.24)
        shadowBlur: 1.0
        blurMax: 64
        shadowVerticalOffset: 18
    }
    // Panneau qui descend du haut de l'écran (coins arrondis en bas seulement)
    Item {
        id: panneau
        anchors.horizontalCenter: parent.horizontalCenter
        y: 44
        width: 460
        height: colonne.implicitHeight + 48
        Rectangle { anchors.fill: parent; radius: 18; color: fenetre.fond; border.width: 0.5; border.color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.12) }
        Rectangle { width: parent.width; height: 20; color: fenetre.fond }     // (haut droit, collé à la barre de titre)

        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 26
            anchors.bottomMargin: 22
            spacing: 16

            RowLayout {
                spacing: 14
                Item {
                    Layout.preferredWidth: 48
                    Layout.preferredHeight: 48
                    Kirigami.Icon {
                        id: tuile
                        anchors.fill: parent
                        visible: false
                        source: fenetre.infos.icone && fenetre.infos.icone.indexOf("/") === 0 ? "file://" + fenetre.infos.icone
                                                                                         : (fenetre.infos.icone || "application-x-executable")
                    }
                    MultiEffect { source: tuile; anchors.fill: tuile; saturation: -0.6 }
                    Rectangle {
                        x: 30; y: 30
                        width: 24; height: 24; radius: 12
                        color: fenetre.fond
                        Picto { anchors.centerIn: parent; width: 16; height: 16; encre: "#B5532F"; trace: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7v5l3 2" }
                    }
                }
                Text {
                    Layout.fillWidth: true
                    text: fenetre.infos.nom + " ne répond pas"
                    elide: Text.ElideRight
                    font.pixelSize: 20
                    font.weight: Font.Medium
                    color: fenetre.texte
                }
            }
            Text {
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                lineHeight: 1.2
                text: fenetre.documents.length
                      ? "Vous pouvez attendre ou forcer l'arrêt. Votre travail a été sauvegardé automatiquement à "
                        + fenetre.heure(fenetre.documents[0].quand) + "."
                      : "Vous pouvez attendre qu'elle réponde de nouveau, ou forcer l'arrêt : ce qui n'a pas été enregistré serait perdu."
                font.pixelSize: 14
                color: fenetre.texte2
            }
            // Documents gardés en secours
            Repeater {
                model: fenetre.documents
                delegate: Rectangle {
                    Layout.fillWidth: true
                    implicitHeight: 38
                    radius: 12
                    color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, fenetre.sombre ? 0.25 : 0.05)
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 12
                        anchors.rightMargin: 12
                        spacing: 10
                        Picto { Layout.preferredWidth: 15; Layout.preferredHeight: 15; encre: fenetre.foret; trace: "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M14 3v5h5 M9 13h6 M9 17h4" }
                        Text { Layout.fillWidth: true; text: modelData.titre; elide: Text.ElideMiddle; font.pixelSize: 12; color: fenetre.texte2 }
                        Picto { Layout.preferredWidth: 13; Layout.preferredHeight: 13; encre: fenetre.foret; trace: "M5 12.5l4.5 4.5L19 7.5" }
                        Text { text: "Sauvegardé · " + fenetre.heure(modelData.quand); font.pixelSize: 12; color: fenetre.foret }
                    }
                }
            }
            RowLayout {
                Layout.topMargin: 4
                Layout.fillWidth: true
                spacing: 10
                Item { Layout.fillWidth: true }
                QQC2.AbstractButton {
                    id: attendre
                    implicitHeight: 38
                    implicitWidth: libelleAttendre.implicitWidth + 42
                    hoverEnabled: true
                    focus: true
                    onClicked: fenetre.choisir("attendre")
                    Keys.onReturnPressed: fenetre.choisir("attendre")
                    background: Rectangle { radius: 19; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, attendre.hovered ? 0.09 : 0.05) }
                    contentItem: Text { id: libelleAttendre; text: "Attendre"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; font.pixelSize: 13; font.weight: Font.Medium; color: fenetre.texte }
                }
                QQC2.AbstractButton {
                    id: forcer
                    implicitHeight: 38
                    implicitWidth: libelleForcer.implicitWidth + 42
                    hoverEnabled: true
                    onClicked: fenetre.choisir("forcer")
                    background: Rectangle { radius: 19; color: fenetre.rouge; opacity: forcer.down ? 0.85 : (forcer.hovered ? 0.93 : 1) }
                    contentItem: Text { id: libelleForcer; text: "Forcer l'arrêt"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#FFFFFF" }
                }
            }
        }
    }
}
