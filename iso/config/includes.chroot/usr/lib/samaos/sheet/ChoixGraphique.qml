// Volet « Graphique » : le type de graphique, fait à partir des cellules choisies (le moteur trouve le tableau autour
// de la cellule si une seule est choisie).
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Volet {
    id: choix
    titre: "Insérer un graphique"
    // [rang dans la liste du moteur, nom, tracé, épaisseur du trait]
    readonly property var types: [
        [0, "Colonnes", "M6 20v-7 M12 20V6 M18 20v-10", 3.4],
        [1, "Barres", "M4 6h9 M4 12h15 M4 18h6", 3.4],
        [5, "Lignes", "M3.5 17l5-6l4 3.5l8-9", 1.8],
        [2, "Secteurs", "M11 4a8 8 0 1 0 8 8h-8z M14 2.5v7h7a7 7 0 0 0-7-7z", 1.6],
        [4, "Aires", "M3.5 19.5l5-8l4.5 4l7.5-9.5v13.5z", 1.6]
    ]
    signal choisi(int type)

    Row {
        spacing: 8
        Repeater {
            model: choix.types
            delegate: QQC2.AbstractButton {
                id: carte
                width: 82
                height: 78
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                onClicked: { choix.close(); choix.choisi(modelData[0]) }
                background: Rectangle {
                    radius: 10
                    color: carte.hovered ? fenetre.vertFond : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.035)
                    border.width: carte.hovered ? 1 : 0
                    border.color: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.4)
                }
                contentItem: ColumnLayout {
                    spacing: 6
                    Picto {
                        Layout.alignment: Qt.AlignHCenter
                        Layout.topMargin: 8
                        width: 30; height: 30
                        trace: modelData[2]
                        trait: modelData[3]
                        encre: fenetre.vert
                    }
                    Text {
                        Layout.alignment: Qt.AlignHCenter
                        text: modelData[1]
                        font.pixelSize: 12
                        color: carte.hovered ? fenetre.vertEncre : Couleurs.texte
                    }
                }
            }
        }
    }
    Text {
        text: "À partir des cellules choisies."
        font.pixelSize: 11
        color: Couleurs.texte3
    }
}
