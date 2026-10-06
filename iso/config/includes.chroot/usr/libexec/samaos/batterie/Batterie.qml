// « Batterie à 10 % » (maquette ale-03) : carte posée en haut de l'écran, sur un voile léger. Lancée par
// /usr/libexec/samaos/batterie.py, qui lui passe en JSON { pourcentage, minutes, economie, dejaEconomie } et lit le
// choix (« SAMA_CHOIX=economie » ou « ignorer »).
import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtQuick.Effects

Window {
    id: fenetre
    visibility: Window.FullScreen
    flags: Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint
    color: "transparent"
    title: "Batterie faible"

    readonly property var infos: {
        var a = Qt.application.arguments
        try { return JSON.parse(String(a[a.length - 1])) } catch (e) { return { pourcentage: 10 } }
    }
    readonly property int minutes: infos.minutes || 0
    readonly property int economie: infos.economie || 0
    readonly property bool deja: !!infos.dejaEconomie

    SystemPalette { id: palette }
    readonly property bool sombre: palette.window.hslLightness < 0.5
    readonly property color fond: sombre ? "#1E2233" : "#FBF9F6"
    readonly property color texte: sombre ? "#F1EBE1" : "#1F1C18"
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color laterite: "#B5532F"
    readonly property color foret: sombre ? "#A3D6C1" : "#2F6B57"
    readonly property color trait: Qt.rgba(31 / 255, 28 / 255, 24 / 255, sombre ? 0.3 : 0.05)

    // « 25 min », « 1 h 10 »
    function duree(m) { return m < 60 ? m + " min" : Math.floor(m / 60) + " h" + (m % 60 ? " " + String(m % 60).padStart(2, "0") : "") }
    function choisir(choix) { console.log("SAMA_CHOIX=" + choix); Qt.quit() }
    Shortcut { sequence: "Escape"; onActivated: fenetre.choisir("ignorer") }

    Rectangle {
        anchors.fill: parent
        color: fenetre.sombre ? Qt.rgba(21 / 255, 26 / 255, 43 / 255, 0.45) : Qt.rgba(243 / 255, 236 / 255, 226 / 255, 0.4)
        opacity: 0
        Component.onCompleted: opacity = 1
        Behavior on opacity { NumberAnimation { duration: 250; easing.type: Easing.OutCubic } }
    }

    component Picto: Canvas {
        property string trace
        property color encre: fenetre.texte
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
            c.strokeStyle = encre; c.lineWidth = 1.7; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }
    component Bouton: QQC2.AbstractButton {
        id: bouton
        property bool principal: false
        implicitHeight: 38
        implicitWidth: libelle.implicitWidth + 42
        hoverEnabled: true
        background: Rectangle {
            radius: 19
            color: bouton.principal ? fenetre.laterite : Qt.rgba(31 / 255, 28 / 255, 24 / 255, bouton.hovered ? 0.09 : 0.05)
            opacity: bouton.principal && bouton.down ? 0.85 : (bouton.principal && bouton.hovered ? 0.93 : 1)
        }
        contentItem: Text {
            id: libelle
            text: bouton.text
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            font.pixelSize: 13
            font.weight: bouton.principal ? Font.DemiBold : Font.Medium
            color: bouton.principal ? "#FFFFFF" : fenetre.texte
        }
    }
    component Legende: RowLayout {
        property color teinte
        property string texte
        spacing: 6
        Rectangle { width: 8; height: 8; radius: 4; color: teinte }
        Text { text: parent.texte; font.pixelSize: 12; color: fenetre.texte2 }
    }

    MultiEffect {
        source: carte
        anchors.fill: carte
        shadowEnabled: true
        shadowColor: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.24)
        shadowBlur: 1.0
        blurMax: 64
        shadowVerticalOffset: 18
        opacity: carte.opacity
    }
    Rectangle {
        id: carte
        anchors.horizontalCenter: parent.horizontalCenter
        y: 54
        width: 460
        height: colonne.implicitHeight + 52
        radius: 22
        color: fenetre.fond
        border.width: 0.5
        border.color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.12)
        opacity: 0
        Component.onCompleted: opacity = 1
        Behavior on opacity { NumberAnimation { duration: 260; easing.type: Easing.OutCubic } }

        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 26
            spacing: 18

            RowLayout {
                spacing: 16
                Rectangle {
                    Layout.preferredWidth: 60
                    Layout.preferredHeight: 60
                    radius: 18
                    color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.1)
                    Picto {
                        anchors.centerIn: parent
                        width: 34; height: 34
                        encre: fenetre.laterite
                        trace: "M4 8h14a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z M21 11v2 M6 11v2"
                    }
                }
                ColumnLayout {
                    spacing: 2
                    Text { text: "Batterie à " + (fenetre.infos.pourcentage || 0) + " %"; font.pixelSize: 22; font.weight: Font.Medium; color: fenetre.texte }
                    Text {
                        text: fenetre.minutes ? "environ " + fenetre.duree(fenetre.minutes) + " d'autonomie" : "autonomie en cours d'estimation"
                        font.pixelSize: 14
                        color: fenetre.texte2
                    }
                }
            }

            Text {
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                lineHeight: 1.2
                text: "Branchez votre ordinateur dès que le courant est disponible. " + (fenetre.deja
                      ? "L'économie maximale est déjà en marche."
                      : "L'économie maximale prolonge l'autonomie : écran moins lumineux, processeur ménagé, tâches de fond en pause. Tout revient quand vous rebranchez.")
                font.pixelSize: 13
                color: fenetre.texte2
            }

            // Autonomie maintenant et avec l'économie
            Rectangle {
                visible: !fenetre.deja && fenetre.minutes > 0
                Layout.fillWidth: true
                Layout.preferredHeight: estimation.implicitHeight + 28
                radius: 14
                color: fenetre.trait
                ColumnLayout {
                    id: estimation
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.leftMargin: 16
                    anchors.rightMargin: 16
                    spacing: 10
                    readonly property real echelle: Math.max(fenetre.economie * 1.25, 60)
                    RowLayout {
                        Text { Layout.fillWidth: true; text: "Autonomie estimée"; font.pixelSize: 12; color: fenetre.texte2 }
                        Text { text: "≈ " + fenetre.duree(fenetre.economie) + " avec l'économie"; font.pixelSize: 12; font.weight: Font.DemiBold; color: fenetre.foret }
                    }
                    Item {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 8
                        Rectangle { anchors.fill: parent; radius: 4; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, fenetre.sombre ? 0.4 : 0.09) }
                        Rectangle { height: 8; radius: 4; width: parent.width * Math.min(1, fenetre.economie / estimation.echelle); color: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.3) }
                        Rectangle { height: 8; radius: 4; width: parent.width * Math.min(1, fenetre.minutes / estimation.echelle); color: fenetre.laterite }
                    }
                    RowLayout {
                        spacing: 16
                        Legende { teinte: fenetre.laterite; texte: "Maintenant · " + fenetre.duree(fenetre.minutes) }
                        Legende { teinte: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.45); texte: "Économie maximale · " + fenetre.duree(fenetre.economie) }
                    }
                }
            }

            RowLayout {
                Layout.alignment: Qt.AlignRight
                spacing: 10
                Bouton { visible: !fenetre.deja; text: "Ignorer"; onClicked: fenetre.choisir("ignorer") }
                Bouton {
                    principal: true
                    text: fenetre.deja ? "Compris" : "Activer l'économie maximale"
                    onClicked: fenetre.choisir(fenetre.deja ? "ignorer" : "economie")
                }
            }
        }
    }
}
