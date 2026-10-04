/*
    Mode édition du bureau de Sama OS (remplace celui de Plasma).
    Pas de flou ni de bureau réduit : le bureau reste en place, une barre flottante en haut propose
    « Ajouter des widgets », « Fond d'écran » et « Terminé » ; les cartes portent un « − » (ConfigOverlay.qml).

    D'après DesktopEditMode.qml de Plasma — SPDX-FileCopyrightText: 2024 Marco Martin <mart@kde.org>
    SPDX-License-Identifier: GPL-2.0-or-later
*/

import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.kirigami 2.20 as Kirigami

Item {
    id: edition

    // Plasma centre le bureau sur ces coordonnées : on le laisse à sa place
    property real centerX: Math.round(root.width / 2)
    property real centerY: Math.round(root.height / 2)

    property bool open: false
    Component.onCompleted: open = Qt.binding(() => containment.plasmoid.corona.editMode)

    // Bureau à taille réelle pendant l'édition (Plasma le réduit d'ordinaire)
    Binding {
        target: containmentParent
        property: "scale"
        value: 1
        restoreMode: Binding.RestoreBinding
    }

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color texte: Kirigami.Theme.textColor

    // Barre flottante
    Rectangle {
        id: barre
        anchors.horizontalCenter: parent.horizontalCenter
        y: edition.open ? 24 : -height - 20
        Behavior on y { NumberAnimation { duration: 250; easing.type: Easing.OutCubic } }
        width: rangee.implicitWidth + 16
        height: 52
        radius: 26
        color: edition.sombre ? Qt.rgba(30 / 255, 34 / 255, 51 / 255, 0.96) : Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.96)
        border.width: 1
        border.color: edition.sombre ? Qt.rgba(1, 1, 1, 0.1) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)

        component Bouton: QQC2.AbstractButton {
            id: bouton
            property bool principal: false
            implicitHeight: 36
            hoverEnabled: true
            contentItem: Text {
                text: bouton.text
                font.pixelSize: 13
                font.weight: bouton.principal ? Font.DemiBold : Font.Medium
                color: bouton.principal ? "#FFFFFF" : edition.texte
                leftPadding: 16
                rightPadding: 16
                verticalAlignment: Text.AlignVCenter
            }
            background: Rectangle {
                radius: 18
                color: bouton.principal ? "#B5532F"
                     : Qt.rgba(edition.texte.r, edition.texte.g, edition.texte.b, bouton.hovered ? 0.1 : 0.05)
            }
        }

        RowLayout {
            id: rangee
            anchors.centerIn: parent
            spacing: 8

            Text {
                Layout.leftMargin: 12
                Layout.rightMargin: 6
                text: "Déplacez les cartes, ou retirez-les avec −"
                font.pixelSize: 13
                color: Kirigami.Theme.disabledTextColor
            }
            Bouton {
                text: "Ajouter des widgets"
                onClicked: containment.plasmoid.internalAction("add widgets").trigger()
            }
            Bouton {
                text: "Fond d'écran"
                onClicked: containment.plasmoid.internalAction("configure").trigger()
            }
            Bouton {
                text: "Terminé"
                principal: true
                onClicked: containment.plasmoid.corona.editMode = false
            }
        }
    }
}
