/*
    Poignées des widgets du bureau de Sama OS en mode édition (remplace celles de Plasma) :
    toute la carte se déplace à la souris, un « − » noir en haut à gauche la retire (comme la maquette),
    une petite roue dentée règle les widgets de KDE qui ont des réglages.

    D'après ConfigOverlay.qml de Plasma — SPDX-FileCopyrightText: 2019 Marco Martin <mart@kde.org>
    SPDX-License-Identifier: LGPL-2.0-or-later
*/

import QtQuick
import org.kde.plasma.core as PlasmaCore
import org.kde.kirigami 2.20 as Kirigami
import org.kde.plasma.private.containmentlayoutmanager 1.0 as ContainmentLayoutManager

// Base sans poignées de redimensionnement : les cartes Sama ont une taille fixe
ContainmentLayoutManager.ConfigOverlay {
    id: overlay

    opacity: open
    Behavior on opacity { OpacityAnimator { duration: 200; easing.type: Easing.InOutQuad } }

    readonly property bool carteSama: applet ? String(applet.plasmoid.pluginName).indexOf("org.samaos.") === 0 : false

    SequentialAnimation {
        id: retrait
        NumberAnimation { target: overlay.itemContainer; property: "scale"; from: 1; to: 0; duration: 200; easing.type: Easing.InCubic }
        ScriptAction {
            script: {
                appletContainer.applet.plasmoid.internalAction("remove").trigger();
                appletContainer.editMode = false;
            }
        }
    }

    // Déplacer : toute la carte
    MouseArea {
        anchors.fill: parent
        drag.target: overlay.itemContainer
        cursorShape: pressed ? Qt.DragMoveCursor : Qt.OpenHandCursor
        hoverEnabled: true
        onPressed: appletsLayout.releaseSpace(overlay.itemContainer)
        onPositionChanged: mouse => {
            if (!pressed) return
            appletsLayout.showPlaceHolderForItem(overlay.itemContainer)
            var p = mapToItem(overlay.itemContainer, mouse.x, mouse.y)
            overlay.itemContainer.userDrag(Qt.point(overlay.itemContainer.x, overlay.itemContainer.y), p)
        }
        onReleased: {
            appletsLayout.hidePlaceHolder()
            appletsLayout.positionItem(overlay.itemContainer)
        }
    }

    // Contour discret de la carte en édition
    Rectangle {
        anchors.fill: parent
        radius: Kirigami.Units.gridUnit * 1.3
        color: "transparent"
        border.width: 1.5
        border.color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.6)
    }

    // Retirer
    MouseArea {
        x: -8
        y: -8
        width: 26
        height: 26
        cursorShape: Qt.PointingHandCursor
        visible: {
            if (!applet) return false
            var a = applet.plasmoid.internalAction("remove")
            return a ? true : false
        }
        onClicked: retrait.restart()
        Rectangle {
            anchors.fill: parent
            radius: 13
            color: "#1F1C18"
            border.width: 1.5
            border.color: "#FFFFFF"
            Rectangle { anchors.centerIn: parent; width: 10; height: 2; radius: 1; color: "#FFFFFF" }
        }
        Component.onCompleted: {
            var a = applet ? applet.plasmoid.internalAction("remove") : null
            if (a) a.enabled = true
        }
    }

    // Régler (widgets de KDE seulement)
    MouseArea {
        x: parent.width - width + 8
        y: -8
        width: 26
        height: 26
        cursorShape: Qt.PointingHandCursor
        visible: !overlay.carteSama && applet && applet.plasmoid.internalAction("configure") !== null
        onClicked: applet.plasmoid.internalAction("configure").trigger()
        Rectangle {
            anchors.fill: parent
            radius: 13
            color: "#FFFFFF"
            border.width: 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.2)
            Kirigami.Icon { anchors.centerIn: parent; width: 14; height: 14; source: "configure" }
        }
    }
}
