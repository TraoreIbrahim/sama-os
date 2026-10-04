/*
    Contenu de la bulle de Sama : icône, barre latérite et pourcentage (volume, luminosité…),
    ou icône et texte (disposition du clavier, mode avion…). Comme les curseurs du centre de contrôle.
    SPDX-License-Identifier: GPL-2.0-or-later
*/
import QtQuick
import QtQuick.Layouts
import org.kde.kirigami 2.20 as Kirigami

Item {
    id: racine

    // Propriétés attendues par Plasma
    property int timeout: 1800
    property var osdValue
    property int osdMaxValue: 100
    property string osdAdditionalText: ""
    property string icon
    property bool showingProgress: false

    readonly property real part: showingProgress && osdMaxValue > 0 ? Math.max(0, Math.min(1, Number(osdValue) / osdMaxValue)) : 0

    width: 300
    height: 44

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 6
        anchors.rightMargin: 6
        spacing: 12

        // Icônes de Sama (les mêmes que le Pouls) pour les bulles courantes, sinon celle demandée par le système
        readonly property string iconeSama: {
            var n = String(racine.icon)
            if (n.indexOf("muted") >= 0) return "muet"
            if (n.indexOf("volume") >= 0 || n.indexOf("audio") >= 0) return "volume"
            if (n.indexOf("brightness") >= 0 || n.indexOf("display") >= 0) return "soleil"
            if (n.indexOf("keyboard") >= 0 || n.indexOf("input") >= 0) return "globe"
            if (n.indexOf("wireless") >= 0 || n.indexOf("wifi") >= 0) return "wifi"
            if (n.indexOf("bluetooth") >= 0) return "bluetooth"
            return ""
        }
        Kirigami.Icon {
            Layout.preferredWidth: 22
            Layout.preferredHeight: 22
            source: parent.iconeSama ? "file:///usr/libexec/samaos/osd/icones/" + parent.iconeSama + ".svg" : racine.icon
            isMask: parent.iconeSama !== ""
            color: Kirigami.Theme.textColor
        }

        // Barre de progression (volume, luminosité…)
        Item {
            visible: racine.showingProgress
            Layout.fillWidth: true
            Layout.preferredHeight: 6
            Rectangle {
                anchors.fill: parent
                radius: 3
                color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.12)
            }
            Rectangle {
                height: parent.height
                radius: 3
                width: parent.width * racine.part
                color: Number(racine.osdValue) > 100 ? "#E2A62B" : "#B5532F"
                Behavior on width { NumberAnimation { duration: 120 } }
            }
        }
        Text {
            visible: racine.showingProgress
            Layout.preferredWidth: 40
            horizontalAlignment: Text.AlignRight
            text: Math.round(Number(racine.osdValue) / Math.max(1, racine.osdMaxValue) * 100) + " %"
            font.pixelSize: 13
            font.weight: Font.Medium
            color: Kirigami.Theme.textColor
        }

        // Texte seul (disposition du clavier, mode avion…)
        Text {
            visible: !racine.showingProgress
            Layout.fillWidth: true
            text: racine.osdValue !== undefined ? String(racine.osdValue) : racine.osdAdditionalText
            elide: Text.ElideRight
            font.pixelSize: 14
            font.weight: Font.Medium
            color: Kirigami.Theme.textColor
        }
    }
}
