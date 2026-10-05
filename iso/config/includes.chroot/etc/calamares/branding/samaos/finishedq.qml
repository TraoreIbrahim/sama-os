/* Installateur de Sama — dernière étape : « Sama est installé » (redémarrer ou continuer l'essai),
 * ou, si l'installation a échoué, le message de Calamares et ses détails.
 * Les boutons du bas de l'installateur sont masqués à cette étape : la page a les siens.
 */
import io.calamares.core 1.0
import io.calamares.ui 1.0
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls

Rectangle {
    id: page
    color: "#FBF9F6"
    readonly property bool echec: config.failed === true
    property bool details: false

    // Calamares redémarre en quittant (bouton « Continuer l'essai », croix de la fenêtre, Alt+F4) tant que
    // « redémarrer » est coché, ce que fait le réglage de Debian (restartNowChecked) : on le décoche dès
    // l'arrivée sur la page. « Redémarrer maintenant » force le redémarrage (doRestart(true)).
    Component.onCompleted: config.restartNowWanted = false
    function onActivate() { config.restartNowWanted = false }

    component Pilule: AbstractButton {
        id: bouton
        property bool principal: false
        implicitHeight: 42
        implicitWidth: libelle.implicitWidth + 48
        hoverEnabled: true
        background: Rectangle {
            radius: 21
            color: bouton.principal ? "#B5532F" : Qt.rgba(31 / 255, 28 / 255, 24 / 255, bouton.hovered ? 0.09 : 0.05)
            opacity: bouton.down ? 0.85 : 1
        }
        contentItem: Text {
            id: libelle
            text: bouton.text
            font.pixelSize: 14
            font.weight: bouton.principal ? Font.DemiBold : Font.Medium
            color: bouton.principal ? "#FFFFFF" : "#1F1C18"
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
        }
    }

    ColumnLayout {
        id: contenu
        anchors.centerIn: parent
        anchors.verticalCenterOffset: -10
        width: Math.min(parent.width - 80, 520)
        spacing: 0

        // Pastille : coche verte, ou point d'exclamation
        Rectangle {
            id: pastille
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: 76
            Layout.preferredHeight: 76
            radius: 38
            color: page.echec ? Qt.rgba(163 / 255, 50 / 255, 42 / 255, 0.1) : Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.12)
            scale: 0.6
            opacity: 0
            Component.onCompleted: apparition.start()
            ParallelAnimation {
                id: apparition
                NumberAnimation { target: pastille; property: "scale"; to: 1; duration: 420; easing.type: Easing.OutBack }
                NumberAnimation { target: pastille; property: "opacity"; to: 1; duration: 220 }
            }
            Rectangle {
                anchors.centerIn: parent
                width: 48
                height: 48
                radius: 24
                color: page.echec ? "#A3322A" : "#2F6B57"
                Canvas {
                    anchors.centerIn: parent
                    width: 26
                    height: 26
                    onPaint: {
                        var c = getContext("2d"); c.reset(); c.scale(26 / 24, 26 / 24)
                        c.strokeStyle = "#FFFFFF"; c.lineWidth = 2.6; c.lineCap = "round"; c.lineJoin = "round"
                        c.path = page.echec ? "M12 6v8 M12 18h0.01" : "M5 12.5l4.5 4.5L19 7.5"
                        c.stroke()
                    }
                }
            }
        }

        Text {
            Layout.topMargin: 22
            Layout.alignment: Qt.AlignHCenter
            text: page.echec ? "L'installation n'a pas abouti" : "Sama est installé"
            font.pixelSize: 28
            font.weight: Font.Medium
            color: "#1F1C18"
        }
        Text {
            Layout.topMargin: 10
            Layout.fillWidth: true
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
            lineHeight: 1.25
            font.pixelSize: 14
            color: "#665E54"
            text: page.echec
                ? (String(config.failureMessage || "") || "Une étape de l'installation a échoué.")
                  + "\nVous pouvez continuer à utiliser Sama depuis la clé, puis réessayer."
                : "Redémarrez l'ordinateur pour commencer. Retirez la clé USB (ou le DVD) quand l'écran s'éteint."
        }

        // Détails de l'échec
        Text {
            visible: page.echec && String(config.failureDetails || "") !== ""
            Layout.topMargin: 14
            Layout.alignment: Qt.AlignHCenter
            text: (page.details ? "▾  " : "▸  ") + "Voir les détails"
            font.pixelSize: 13
            font.weight: Font.Medium
            color: "#665E54"
            MouseArea { anchors.fill: parent; anchors.margins: -4; cursorShape: Qt.PointingHandCursor; onClicked: page.details = !page.details }
        }
        Rectangle {
            visible: page.echec && page.details
            Layout.topMargin: 8
            Layout.fillWidth: true
            Layout.preferredHeight: 140
            radius: 12
            color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
            ScrollView {
                anchors.fill: parent
                anchors.margins: 12
                clip: true
                TextArea {
                    readOnly: true
                    background: null
                    padding: 0
                    wrapMode: Text.WrapAnywhere
                    selectByMouse: true
                    text: String(config.failureDetails || "")
                    font.pixelSize: 11
                    font.family: "Noto Sans Mono"
                    color: "#665E54"
                }
            }
        }

        RowLayout {
            Layout.topMargin: 30
            Layout.alignment: Qt.AlignHCenter
            spacing: 12
            Pilule {
                text: page.echec ? "Fermer l'installateur" : "Continuer l'essai"
                onClicked: {
                    config.restartNowWanted = false
                    ViewManager.quit()
                }
            }
            Pilule {
                visible: !page.echec
                principal: true
                text: "Redémarrer maintenant"
                onClicked: config.doRestart(true)
            }
        }
    }
}
