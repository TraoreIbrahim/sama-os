/* En-tête de l'installateur de Sama (maquette dem-02 à dem-06) : tuile éléphant, « Installer Sama », et la
 * ligne des étapes. Les huit étapes de Calamares sont regroupées en six étapes Sama :
 *   bienvenue → Langue ; région, clavier → Clavier ; partitions → Disque ; compte, résumé → Compte ;
 *   installation → Installation ; fin → Terminé.
 * (placé en haut de la fenêtre : « sidebar: qml,top » dans branding.desc)
 */
import io.calamares.ui 1.0
import io.calamares.core 1.0
import QtQuick
import QtQuick.Layouts

Rectangle {
    id: entete
    implicitHeight: 68
    height: 68
    color: "#FBF9F6"

    readonly property var etapes: ["Langue", "Clavier", "Disque", "Compte", "Installation", "Terminé"]
    readonly property var groupes: [0, 1, 1, 2, 3, 3, 4, 5]
    readonly property int courante: groupes[Math.max(0, Math.min(ViewManager.currentStepIndex, groupes.length - 1))]

    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08) }

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 24
        anchors.rightMargin: 28
        spacing: 10

        Rectangle {
            Layout.preferredWidth: 32
            Layout.preferredHeight: 32
            radius: 10
            color: "#B5532F"
            Image {
                anchors.centerIn: parent
                width: 22
                height: 22
                sourceSize.width: 44
                sourceSize.height: 44
                source: "file:///usr/share/samaos/logo-cour.svg"
            }
        }
        Text { text: "Installer Sama"; font.pixelSize: 14; font.weight: Font.DemiBold; color: "#1F1C18" }
        Item { Layout.fillWidth: true }

        Repeater {
            model: entete.etapes
            delegate: Row {
                spacing: 8
                readonly property bool faite: index < entete.courante
                readonly property bool active: index === entete.courante
                // Trait entre deux étapes (vert quand l'étape précédente est faite)
                Rectangle {
                    visible: index > 0
                    anchors.verticalCenter: parent.verticalCenter
                    width: 14
                    height: 1
                    color: index <= entete.courante ? "#2F6B57" : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                }
                Row {
                    spacing: 6
                    anchors.verticalCenter: parent.verticalCenter
                    Rectangle {
                        width: 20
                        height: 20
                        radius: 10
                        color: parent.parent.faite ? "#2F6B57" : (parent.parent.active ? "#B5532F" : "transparent")
                        border.width: parent.parent.faite || parent.parent.active ? 0 : 1
                        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                        Text {
                            anchors.centerIn: parent
                            text: parent.parent.parent.faite ? "✓" : (index + 1)
                            font.pixelSize: 11
                            font.weight: Font.DemiBold
                            color: parent.parent.parent.faite || parent.parent.parent.active ? "#FFFFFF" : "#8A8277"
                        }
                    }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: modelData
                        font.pixelSize: 12
                        font.weight: parent.parent.active ? Font.DemiBold : Font.Normal
                        color: parent.parent.active ? "#1F1C18" : (parent.parent.faite ? "#665E54" : "#8A8277")
                    }
                }
            }
        }
    }
}
