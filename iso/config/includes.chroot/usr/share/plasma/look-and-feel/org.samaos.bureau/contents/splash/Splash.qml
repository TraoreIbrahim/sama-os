// Écran d'ouverture de session de Sama OS (remplace « Plasma, par KDE »).
// Fond nuit, l'éléphant dont la trompe se balance, le nom du système.
// KSplash fait avancer « stage » jusqu'à 6, puis ferme l'écran.

import QtQuick

Rectangle {
    id: racine
    color: "#151A2B"

    property int stage

    ChargementSama {
        id: elephant
        anchors.centerIn: parent
        anchors.verticalCenterOffset: -height * 0.25
        width: 96
        height: 96
        couleur: "#E6DAC2"
        fond: racine.color
        opacity: 0
        Component.onCompleted: opacity = 1
        Behavior on opacity { NumberAnimation { duration: 600; easing.type: Easing.OutCubic } }
    }

    Text {
        anchors.top: elephant.bottom
        anchors.topMargin: 28
        anchors.horizontalCenter: parent.horizontalCenter
        text: "Sama OS"
        color: "#F1EBE1"
        opacity: elephant.opacity * 0.9
        font.pixelSize: 22
        font.weight: Font.Light
        font.letterSpacing: 2
    }

    // Fondu de sortie quand le bureau est prêt
    OpacityAnimator {
        running: racine.stage >= 6
        target: racine
        from: 1
        to: 0
        duration: 400
        easing.type: Easing.InCubic
    }
}
