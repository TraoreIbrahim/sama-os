// Carte « Data consommée » du bureau de Sama OS.
// Mesure réelle : octets reçus + envoyés par les cartes réseau depuis l'allumage
// (hors boucle locale), comparés au forfait réglé dans la configuration (1 Go par défaut).

import QtQuick
import QtQuick.Layouts
import QtQuick.Shapes
import org.kde.plasma.plasmoid
import org.kde.plasma.core as PlasmaCore
import org.kde.plasma.plasma5support as P5Support
import org.kde.kirigami as Kirigami

PlasmoidItem {
    id: racine

    Plasmoid.backgroundHints: PlasmaCore.Types.NoBackground
    preferredRepresentation: fullRepresentation

    readonly property color foret: "#2F6B57"
    readonly property int forfaitMo: Plasmoid.configuration.forfaitMo
    property real consommeMo: 0
    readonly property real part: forfaitMo > 0 ? Math.min(1, consommeMo / forfaitMo) : 0

    function lisible(mo) {
        if (mo >= 1024) return (mo / 1024).toLocaleString(Qt.locale(), "f", 1) + " Go"
        return Math.round(mo) + " Mo"
    }

    // Lecture des compteurs réseau toutes les 30 secondes
    readonly property string commande: "awk '{ s += $1 } END { print s + 0 }' /sys/class/net/[!l]*/statistics/rx_bytes /sys/class/net/[!l]*/statistics/tx_bytes"

    P5Support.DataSource {
        id: lecteur
        engine: "executable"
        connectedSources: [racine.commande]
        interval: 30000
        onNewData: (source, data) => {
            var octets = parseFloat(String(data["stdout"]).trim())
            if (!isNaN(octets)) racine.consommeMo = octets / 1048576
        }
    }

    fullRepresentation: Item {
        Layout.minimumWidth: Kirigami.Units.gridUnit * 14
        Layout.preferredWidth: Kirigami.Units.gridUnit * 17
        Layout.minimumHeight: Kirigami.Units.gridUnit * 6.5
        Layout.preferredHeight: Kirigami.Units.gridUnit * 6.5

        Rectangle {
            anchors.fill: parent
            radius: Kirigami.Units.gridUnit * 1.3
            color: Kirigami.Theme.backgroundColor
            opacity: 0.72
            border.width: 1
            border.color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)
        }

        RowLayout {
            anchors.fill: parent
            anchors.margins: Kirigami.Units.gridUnit * 1.2
            spacing: Kirigami.Units.gridUnit

            // Anneau : part du forfait consommée
            Item {
                visible: racine.forfaitMo > 0
                Layout.preferredWidth: Kirigami.Units.gridUnit * 4
                Layout.preferredHeight: Layout.preferredWidth

                Shape {
                    anchors.fill: parent
                    layer.enabled: true
                    layer.samples: 4
                    ShapePath {
                        strokeColor: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.09)
                        strokeWidth: 7
                        fillColor: "transparent"
                        capStyle: ShapePath.RoundCap
                        PathAngleArc {
                            centerX: Kirigami.Units.gridUnit * 2; centerY: centerX
                            radiusX: centerX - 4; radiusY: radiusX
                            startAngle: 0; sweepAngle: 360
                        }
                    }
                    ShapePath {
                        strokeColor: racine.foret
                        strokeWidth: 7
                        fillColor: "transparent"
                        capStyle: ShapePath.RoundCap
                        PathAngleArc {
                            centerX: Kirigami.Units.gridUnit * 2; centerY: centerX
                            radiusX: centerX - 4; radiusY: radiusX
                            startAngle: -90; sweepAngle: Math.max(2, 360 * racine.part)
                        }
                    }
                }
                Text {
                    anchors.centerIn: parent
                    text: Math.round(racine.part * 100) + " %"
                    font.pixelSize: Kirigami.Theme.smallFont.pixelSize + 1
                    font.weight: Font.DemiBold
                    color: Kirigami.Theme.textColor
                }
            }

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text {
                    text: "Data depuis l'allumage"
                    font.pixelSize: Kirigami.Theme.smallFont.pixelSize + 1
                    color: Kirigami.Theme.disabledTextColor
                }
                Text {
                    text: racine.lisible(racine.consommeMo)
                    font.pixelSize: Kirigami.Theme.defaultFont.pixelSize * 1.6
                    font.weight: Font.Medium
                    color: Kirigami.Theme.textColor
                }
                Text {
                    visible: racine.forfaitMo > 0
                    text: "sur " + racine.lisible(racine.forfaitMo) + " de forfait"
                    font.pixelSize: Kirigami.Theme.smallFont.pixelSize + 1
                    color: Kirigami.Theme.disabledTextColor
                }
            }
        }
    }
}
