// Panneau de contrôle du Pouls (première version du centre de contrôle de Sama).
// Notifications ; tuiles Wi-Fi, mode sombre, réseau, réglages ; volume ; langue.
// L'économie de data réelle viendra dans une version suivante.

import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.plasma5support as P5Support

Item {
    id: panneau
    implicitHeight: colonne.implicitHeight + Kirigami.Units.largeSpacing * 2

    readonly property bool modeSombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property bool wifiActif: reseau.item ? reseau.item.wifiActive : true

    P5Support.DataSource {
        id: executeur
        engine: "executable"
        connectedSources: []
        onNewData: (source) => disconnectSource(source)
        function lancer(commande) { connectSource(commande) }
    }

    // Une tuile du centre de contrôle (latérite quand elle est active)
    component Tuile: MouseArea {
        id: tuile
        property string titre
        property string detail
        property string nomIcone
        property bool active: false

        Layout.fillWidth: true
        Layout.preferredHeight: Kirigami.Units.gridUnit * 3.4
        hoverEnabled: true

        Rectangle {
            anchors.fill: parent
            radius: Kirigami.Units.gridUnit
            color: tuile.active ? "#B5532F" : Kirigami.Theme.textColor
            opacity: tuile.active ? (tuile.containsMouse ? 0.9 : 1) : (tuile.containsMouse ? 0.1 : 0.06)
        }
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: Kirigami.Units.largeSpacing
            anchors.rightMargin: Kirigami.Units.smallSpacing
            spacing: Kirigami.Units.smallSpacing * 2

            Kirigami.Icon {
                Layout.preferredWidth: Kirigami.Units.iconSizes.smallMedium
                Layout.preferredHeight: Layout.preferredWidth
                isMask: true
                color: tuile.active ? "white" : Kirigami.Theme.textColor
                source: Qt.resolvedUrl("../icons/" + tuile.nomIcone + ".svg")
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 0
                Text {
                    Layout.fillWidth: true
                    text: tuile.titre
                    elide: Text.ElideRight
                    font.weight: Font.DemiBold
                    color: tuile.active ? "white" : Kirigami.Theme.textColor
                }
                Text {
                    Layout.fillWidth: true
                    text: tuile.detail
                    elide: Text.ElideRight
                    font.pixelSize: Kirigami.Theme.smallFont.pixelSize
                    color: tuile.active ? "#FBE9DF" : Kirigami.Theme.disabledTextColor
                }
            }
        }
    }

    // Une ligne cliquable (langue, réglages)
    component Ligne: MouseArea {
        id: ligne
        property string titre
        property string nomIcone
        Layout.fillWidth: true
        Layout.preferredHeight: Kirigami.Units.gridUnit * 2.4
        hoverEnabled: true

        Rectangle {
            anchors.fill: parent
            radius: Kirigami.Units.gridUnit * 0.7
            color: Kirigami.Theme.textColor
            opacity: ligne.containsMouse ? 0.1 : 0.06
        }
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: Kirigami.Units.largeSpacing
            anchors.rightMargin: Kirigami.Units.largeSpacing
            spacing: Kirigami.Units.smallSpacing * 2
            Kirigami.Icon {
                Layout.preferredWidth: Kirigami.Units.iconSizes.small
                Layout.preferredHeight: Layout.preferredWidth
                isMask: true
                color: Kirigami.Theme.disabledTextColor
                source: Qt.resolvedUrl("../icons/" + ligne.nomIcone + ".svg")
            }
            Text {
                Layout.fillWidth: true
                text: ligne.titre
                color: Kirigami.Theme.textColor
            }
            Kirigami.Icon {
                Layout.preferredWidth: Kirigami.Units.iconSizes.small
                Layout.preferredHeight: Layout.preferredWidth
                isMask: true
                color: Kirigami.Theme.disabledTextColor
                source: Qt.resolvedUrl("../icons/chevron.svg")
            }
        }
    }

    ColumnLayout {
        id: colonne
        anchors.fill: parent
        anchors.margins: Kirigami.Units.largeSpacing
        spacing: Kirigami.Units.largeSpacing

        // Notifications (chargées à part : le panneau reste utilisable si elles manquent)
        Loader {
            Layout.fillWidth: true
            source: "ListeNotifications.qml"
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 1
            color: Kirigami.Theme.textColor
            opacity: 0.08
        }

        GridLayout {
            Layout.fillWidth: true
            columns: 2
            columnSpacing: Kirigami.Units.smallSpacing * 2
            rowSpacing: Kirigami.Units.smallSpacing * 2

            Tuile {
                titre: "Wi-Fi"
                detail: panneau.wifiActif ? "Activé" : "Désactivé"
                nomIcone: panneau.wifiActif ? "wifi" : "wifi-coupe"
                active: panneau.wifiActif
                onClicked: if (reseau.item) reseau.item.activerWifi(!panneau.wifiActif)
            }
            Tuile {
                titre: "Mode sombre"
                detail: panneau.modeSombre ? "Activé" : "Désactivé"
                nomIcone: "lune"
                active: panneau.modeSombre
                // Couleurs, icônes et fonds d'écran basculent ensemble
                onClicked: executeur.lancer("/usr/libexec/samaos/apparence.sh " + (panneau.modeSombre ? "clair" : "sombre"))
            }
            Tuile {
                titre: "Réseaux"
                detail: "Choisir un réseau"
                nomIcone: "globe"
                onClicked: executeur.lancer("systemsettings kcm_networkmanagement")
            }
            Tuile {
                titre: "Économie de data"
                detail: "Bientôt disponible"
                nomIcone: "feuille"
            }
        }

        // Volume (chargé à part : le panneau reste utilisable s'il manque)
        Loader {
            Layout.fillWidth: true
            source: "Volume.qml"
        }

        Ligne {
            titre: "Langue : " + racine.langue
            nomIcone: "globe"
            onClicked: executeur.lancer("systemsettings kcm_regionandlang")
        }
        Ligne {
            titre: "Tous les réglages"
            nomIcone: "reglages"
            onClicked: executeur.lancer("systemsettings")
        }
    }
}
