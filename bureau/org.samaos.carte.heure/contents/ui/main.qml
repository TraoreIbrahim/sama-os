// Carte « Heure et salutation » du bureau de Sama OS.
// L'heure en grand, la date, puis « Bonjour, <prénom> » (Bonsoir après 18 h).

import QtQuick
import QtQuick.Layouts
import org.kde.plasma.plasmoid
import org.kde.plasma.core as PlasmaCore
import org.kde.kirigami as Kirigami
import org.kde.coreaddons as KCoreAddons

PlasmoidItem {
    id: racine

    Plasmoid.backgroundHints: PlasmaCore.Types.NoBackground
    preferredRepresentation: fullRepresentation

    property date maintenant: new Date()
    Timer {
        interval: 10000
        running: true
        repeat: true
        onTriggered: racine.maintenant = new Date()
    }

    KCoreAddons.KUser { id: utilisateur }

    readonly property string prenom: {
        var nom = utilisateur.fullName || utilisateur.loginName || ""
        return nom.split(" ")[0]
    }
    readonly property string salutation: {
        var h = maintenant.getHours()
        var mot = (h >= 18 || h < 4) ? "Bonsoir" : "Bonjour"
        return prenom ? mot + ", " + prenom : mot
    }

    fullRepresentation: Item {
        Layout.minimumWidth: Kirigami.Units.gridUnit * 14
        Layout.minimumHeight: carte.implicitHeight
        Layout.preferredWidth: Kirigami.Units.gridUnit * 17
        Layout.preferredHeight: carte.implicitHeight

        Rectangle {
            id: fond
            anchors.fill: parent
            radius: Kirigami.Units.gridUnit * 1.3
            color: Kirigami.Theme.backgroundColor
            opacity: 0.72
            border.width: 1
            border.color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)
        }

        ColumnLayout {
            id: carte
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: Kirigami.Units.gridUnit * 1.3
            spacing: Kirigami.Units.smallSpacing
            implicitHeight: colonne.implicitHeight + Kirigami.Units.gridUnit * 2.6

            ColumnLayout {
                id: colonne
                spacing: Kirigami.Units.smallSpacing

                Text {
                    text: Qt.formatTime(racine.maintenant, "hh:mm")
                    font.pixelSize: Kirigami.Units.gridUnit * 3.2
                    font.weight: Font.Light
                    font.letterSpacing: -1.5
                    color: Kirigami.Theme.textColor
                }
                Text {
                    text: {
                        var d = racine.maintenant.toLocaleDateString(Qt.locale(), "dddd d MMMM")
                        return d.charAt(0).toUpperCase() + d.slice(1)
                    }
                    font.pixelSize: Kirigami.Theme.defaultFont.pixelSize
                    color: Kirigami.Theme.disabledTextColor
                }
                Text {
                    Layout.topMargin: Kirigami.Units.largeSpacing
                    text: racine.salutation
                    font.pixelSize: Kirigami.Theme.defaultFont.pixelSize * 1.3
                    font.weight: Font.Medium
                    color: Kirigami.Theme.textColor
                }
            }
        }
    }
}
