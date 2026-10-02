// Le Pouls — capsule d'état de Sama OS (Plasma 6).
// Langue · réseau · économie de data · batterie · heure, avec les icônes de la maquette.
// Un clic ouvre le panneau de contrôle (Panneau.qml).
// Les sources de données (réseau, batterie, clavier) sont chargées à part, chacune dans un Loader :
// si l'une manque, la capsule reste affichée avec une valeur par défaut.

import QtQuick
import QtQuick.Layouts
import org.kde.plasma.plasmoid
import org.kde.kirigami as Kirigami

PlasmoidItem {
    id: racine

    readonly property color encre: Kirigami.Theme.textColor
    readonly property color encreDouce: Kirigami.Theme.disabledTextColor
    readonly property color foret: "#2F6B57"
    readonly property color laterite: "#B5532F"

    // Valeurs exposées par les sources (avec repli)
    readonly property string langue: clavier.item && clavier.item.code ? clavier.item.code
                                     : Qt.locale().name.substring(0, 2).toUpperCase()
    readonly property string etatReseau: reseau.item ? reseau.item.etat : "inconnu"   // wifi, filaire, coupe, inconnu
    readonly property bool aBatterie: batterie.item ? batterie.item.presente : false
    readonly property int niveauBatterie: batterie.item ? batterie.item.niveau : 100
    property string heure: Qt.formatTime(new Date(), "hh:mm")

    Timer {
        interval: 5000
        running: true
        repeat: true
        triggeredOnStart: true
        onTriggered: racine.heure = Qt.formatTime(new Date(), "hh:mm")
    }

    Loader { id: clavier; source: "Clavier.qml" }
    Loader { id: reseau; source: "Reseau.qml" }
    Loader { id: batterie; source: "Batterie.qml" }

    function icone(nom) {
        return Qt.resolvedUrl("../icons/" + nom + ".svg")
    }

    preferredRepresentation: compactRepresentation
    toolTipMainText: "Le Pouls"
    toolTipSubText: ""

    compactRepresentation: MouseArea {
        id: capsule
        hoverEnabled: true
        onClicked: racine.expanded = !racine.expanded

        Layout.minimumWidth: contenu.implicitWidth + Kirigami.Units.largeSpacing * 2
        Layout.preferredWidth: Layout.minimumWidth
        Layout.fillHeight: true

        Rectangle {
            anchors.verticalCenter: parent.verticalCenter
            width: parent.width
            height: Math.min(parent.height, Kirigami.Units.iconSizes.medium + Kirigami.Units.smallSpacing * 3)
            radius: height / 2
            color: racine.encre
            opacity: capsule.containsMouse || racine.expanded ? 0.11 : 0.06
        }

        RowLayout {
            id: contenu
            anchors.centerIn: parent
            spacing: Kirigami.Units.largeSpacing

            Text {
                text: racine.langue
                font.pixelSize: Kirigami.Theme.smallFont.pixelSize
                font.weight: Font.DemiBold
                font.letterSpacing: 0.4
                color: racine.encreDouce
            }

            Kirigami.Icon {
                Layout.preferredWidth: Kirigami.Units.iconSizes.small
                Layout.preferredHeight: Layout.preferredWidth
                isMask: true
                color: racine.encre
                opacity: racine.etatReseau === "coupe" ? 0.5 : 1
                source: racine.icone(racine.etatReseau === "filaire" ? "filaire"
                                     : racine.etatReseau === "coupe" ? "wifi-coupe" : "wifi")
            }

            // Économie de data
            Kirigami.Icon {
                Layout.preferredWidth: Kirigami.Units.iconSizes.small
                Layout.preferredHeight: Layout.preferredWidth
                isMask: true
                color: racine.foret
                source: racine.icone("feuille")
            }

            // Batterie (seulement si l'ordinateur en a une)
            Item {
                visible: racine.aBatterie
                Layout.preferredWidth: Kirigami.Units.iconSizes.small * 1.25
                Layout.preferredHeight: Kirigami.Units.iconSizes.small

                Kirigami.Icon {
                    anchors.fill: parent
                    isMask: true
                    color: racine.niveauBatterie <= 15 ? racine.laterite : racine.encre
                    source: racine.icone("batterie")
                }
                // Niveau de charge à l'intérieur du contour
                Rectangle {
                    x: parent.width * 0.21
                    y: parent.height * 0.42
                    height: parent.height * 0.16
                    width: parent.width * 0.5 * Math.max(0.08, racine.niveauBatterie / 100)
                    radius: 1
                    color: racine.niveauBatterie <= 15 ? racine.laterite : racine.encre
                }
            }

            Text {
                text: racine.heure
                font.pixelSize: Kirigami.Theme.defaultFont.pixelSize + 1
                font.weight: Font.DemiBold
                color: racine.encre
            }
        }
    }

    fullRepresentation: Loader {
        source: "Panneau.qml"
        Layout.preferredWidth: Kirigami.Units.gridUnit * 20
        Layout.preferredHeight: item ? item.implicitHeight : Kirigami.Units.gridUnit * 14
    }
}
