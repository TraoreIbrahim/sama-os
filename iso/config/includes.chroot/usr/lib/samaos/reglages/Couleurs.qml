// Couleurs de Sama (maquette), en clair et en sombre, d'après la palette du système.
pragma Singleton
import QtQuick

QtObject {
    property SystemPalette palette: SystemPalette {}
    readonly property bool sombre: palette.window.hslLightness < 0.5

    readonly property color fond: sombre ? "#1E2233" : "#FBF9F6"
    readonly property color barreLaterale: sombre ? Qt.rgba(1, 1, 1, 0.04) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.04)
    readonly property color carte: sombre ? Qt.rgba(1, 1, 1, 0.05) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
    readonly property color champ: sombre ? "#2B3044" : "#FFFFFF"
    readonly property color ligne: sombre ? Qt.rgba(1, 1, 1, 0.08) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
    readonly property color bord: sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
    readonly property color texte: sombre ? "#F1EBE1" : "#1F1C18"
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color texte3: sombre ? "#8C867A" : "#8A8277"
    readonly property color laterite: "#B5532F"
    readonly property color lateriteEncre: sombre ? "#F0B392" : "#93401F"
    readonly property color selection: Qt.rgba(181 / 255, 83 / 255, 47 / 255, sombre ? 0.2 : 0.1)
    readonly property color foret: "#2F6B57"
}
