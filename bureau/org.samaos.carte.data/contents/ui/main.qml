// Carte « Data consommée » du bureau de Sama OS.
// Mesure réelle (/usr/libexec/samaos/data.py) : data utilisée depuis le renouvellement du forfait, d'après
// l'historique de vnstat ; à défaut, depuis l'allumage. Comparée au forfait réglé dans les Réglages.
// Un clic ouvre les Réglages de Sama, section Data (forfait, renouvellement, connexion mesurée…).
// Alerte (bulle de notification) à 80 % puis à 100 % du forfait, une seule fois par période de forfait.

import QtQuick
import QtQuick.Layouts
import QtQuick.Shapes
import org.kde.plasma.plasmoid
import org.kde.plasma.core as PlasmaCore
import org.kde.plasma.plasma5support as P5Support
import org.kde.kirigami as Kirigami
import org.kde.notification

PlasmoidItem {
    id: racine

    Plasmoid.backgroundHints: PlasmaCore.Types.NoBackground
    preferredRepresentation: fullRepresentation

    readonly property color foret: "#2F6B57"
    // Forfait choisi dans les Réglages (Data et mises à jour), relu avec le compteur
    property int forfaitMo: 1024
    property real consommeMo: 0
    property bool duMois: false          // mesure depuis le renouvellement (vnstat), sinon depuis l'allumage
    property string periode: ""
    readonly property real part: forfaitMo > 0 ? Math.min(1, consommeMo / forfaitMo) : 0

    // Alertes de forfait (seuil déjà annoncé pendant la période, gardé dans samaosrc)
    property int dernierSeuil: 0
    function verifierSeuil() {
        var seuil = part >= 1 ? 100 : (part >= 0.8 ? 80 : 0)
        if (seuil > dernierSeuil) {
            dernierSeuil = seuil
            executeur.connectSource("kwriteconfig6 --file samaosrc --group Data --key alertePeriode " + periode
                                    + " && kwriteconfig6 --file samaosrc --group Data --key alerteSeuil " + seuil)
            alerte.title = seuil === 100 ? "Forfait data atteint" : "Forfait data presque épuisé"
            alerte.text = seuil === 100
                ? "Vous avez utilisé " + lisible(consommeMo) + " sur " + lisible(forfaitMo) + ". Les gros téléchargements peuvent vous coûter cher."
                : "Vous avez utilisé " + Math.round(part * 100) + " % de votre forfait (" + lisible(consommeMo) + " sur " + lisible(forfaitMo) + ")."
            alerte.sendEvent()
        }
    }
    Notification {
        id: alerte
        componentName: "samaos"      // /usr/share/knotifications6/samaos.notifyrc : signé « Sama »
        eventId: "forfait"
        iconName: "network-mobile-80"
        urgency: Notification.HighUrgency
        autoDelete: false
    }

    function lisible(mo) {
        if (mo >= 1024) return (mo / 1024).toLocaleString(Qt.locale(), "f", 1) + " Go"
        return Math.round(mo) + " Mo"
    }

    // Lecture toutes les 30 secondes
    P5Support.DataSource {
        id: lecteur
        engine: "executable"
        connectedSources: ["python3 /usr/libexec/samaos/data.py etat"]
        interval: 30000
        onNewData: (source, data) => {
            try {
                var m = JSON.parse(String(data["stdout"]))
                if (m.forfaitMo > 0) racine.forfaitMo = m.forfaitMo
                racine.consommeMo = m.utiliseMo
                racine.duMois = m.source === "vnstat"
                if (racine.periode !== m.periodeDebut) {
                    racine.periode = m.periodeDebut
                    racine.dernierSeuil = m.alerteSeuil || 0     // nouvelle période : les alertes repartent
                }
                racine.verifierSeuil()
            } catch (e) {}
        }
    }

    P5Support.DataSource {
        id: executeur
        engine: "executable"
        onNewData: source => disconnectSource(source)
    }

    fullRepresentation: Item {
        Layout.minimumWidth: Kirigami.Units.gridUnit * 14
        Layout.preferredWidth: Kirigami.Units.gridUnit * 17
        Layout.minimumHeight: Kirigami.Units.gridUnit * 6.5
        Layout.preferredHeight: Kirigami.Units.gridUnit * 6.5

        MouseArea {
            anchors.fill: parent
            cursorShape: Qt.PointingHandCursor
            onClicked: executeur.connectSource("sama-reglages data")
        }

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
                    text: racine.duMois ? "Data ce mois-ci" : "Data depuis l'allumage"
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
