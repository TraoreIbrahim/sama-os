// Volet « Bordures » des cellules choisies : toutes, contour, contour épais, en bas, aucune.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Volet {
    id: choix
    // (côtés en pointillé : lignes laissées telles quelles ou vides)
    readonly property string pointilles: "M4 4h3 M10.5 4h3 M17 4h3v3 M20 10.5v3 M4 7V4 M4 13.5v-3"
    readonly property var genres: [
        ["toutes", "Toutes les bordures", "M4 4h16v16H4z M4 12h16 M12 4v16", 1.7],
        ["contour", "Contour", "M4 4h16v16H4z", 1.7],
        ["epais", "Contour épais", "M4 4h16v16H4z", 3],
        ["bas", "Bordure en bas", "M4 20h16 " + pointilles + " M20 17v0 M4 17v0", 1.7],
        ["aucune", "Aucune bordure", pointilles + " M20 17v3h-3 M13.5 20h-3 M7 20H4v-3", 1.7]
    ]
    signal choisie(string genre)

    Column {
        spacing: 2
        Repeater {
            model: choix.genres
            delegate: QQC2.AbstractButton {
                id: ligne
                width: 200
                height: 32
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                onClicked: { choix.close(); choix.choisie(modelData[0]) }
                background: Rectangle { radius: 8; color: ligne.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06) : "transparent" }
                contentItem: RowLayout {
                    spacing: 10
                    Item { Layout.preferredWidth: 2 }
                    Picto { trace: modelData[2]; trait: modelData[3]; encre: Couleurs.texte }
                    Text { text: modelData[1]; font.pixelSize: 13; color: Couleurs.texte }
                    Item { Layout.fillWidth: true }
                }
            }
        }
    }
}
