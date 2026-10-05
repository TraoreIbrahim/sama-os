/* Boutons du bas de l'installateur de Sama : « Essayer sans installer » sur la première page,
 * « Retour » et « Continuer » (« Installer maintenant » au résumé), en pilules comme la maquette.
 */
import io.calamares.ui 1.0
import io.calamares.core 1.0
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls

Rectangle {
    id: pied
    implicitHeight: 72
    height: 72
    color: "#FBF9F6"

    // Index des étapes de Calamares (voir calamares-sidebar.qml)
    readonly property int etape: ViewManager.currentStepIndex
    readonly property bool resume: etape === 5

    component Pilule: AbstractButton {
        id: bouton
        property bool principal: false
        implicitHeight: 40
        implicitWidth: libelle.implicitWidth + (principal ? 44 : 40)
        hoverEnabled: true
        background: Rectangle {
            radius: 20
            color: bouton.principal ? "#B5532F"
                 : Qt.rgba(31 / 255, 28 / 255, 24 / 255, bouton.hovered ? 0.09 : 0.05)
            opacity: bouton.enabled ? (bouton.down ? 0.85 : 1) : 0.45
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

    Rectangle { visible: pied.etape < 7; anchors.top: parent.top; width: parent.width; height: 1; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08) }

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 28
        anchors.rightMargin: 28
        spacing: 10

        Pilule {
            visible: pied.etape === 0 && ViewManager.quitVisible
            text: "Essayer sans installer"
            onClicked: ViewManager.quit()
        }
        // Pendant l'installation (maquette dem-06)
        RowLayout {
            visible: pied.etape === 6
            spacing: 10
            Rectangle {
                Layout.preferredWidth: 32
                Layout.preferredHeight: 32
                radius: 10
                color: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.12)
                Canvas {
                    anchors.centerIn: parent
                    width: 16
                    height: 16
                    onPaint: {
                        var c = getContext("2d"); c.reset(); c.scale(16 / 24, 16 / 24)
                        c.strokeStyle = "#2F6B57"; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                        c.path = "M9 3h6v6H9z M7 9h10v8a4 4 0 0 1-4 4h-2a4 4 0 0 1-4-4z"; c.stroke()
                    }
                }
            }
            Text { text: "Pas besoin d'Internet : tout est sur la clé."; font.pixelSize: 13; color: "#665E54" }
        }
        Item { Layout.fillWidth: true }
        Pilule {
            visible: pied.etape === 6
            principal: true
            enabled: false
            text: "Continuer"
        }
        Pilule {
            visible: ViewManager.backAndNextVisible && pied.etape > 0 && pied.etape < 6
            enabled: ViewManager.backEnabled
            text: "‹  Retour"
            onClicked: ViewManager.back()
        }
        Pilule {
            visible: ViewManager.backAndNextVisible && pied.etape < 6
            principal: true
            enabled: ViewManager.nextEnabled
            text: pied.resume ? "Installer maintenant" : "Continuer"
            onClicked: ViewManager.next()
        }
    }
}
