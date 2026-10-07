// Un commentaire, en carte (maquette « Docs · Blocs », marge des commentaires) : les initiales, le nom et le moment,
// le texte. Pour la case courante : Modifier, et Résolu (le commentaire est retiré ; Ctrl+Z le rend). Dans la bulle
// de la grille comme dans le panneau des commentaires.
import QtQuick
import QtQuick.Layouts
import "../reglages"

Rectangle {
    id: carte
    property var k: null                 // { id, tab, auteur, date, texte, c, l }
    property string lieu: ""             // (dans le panneau : « B3 », ou « B3 · Feuille 2 »)
    property bool actions: false
    property bool choisie: false
    signal modifier()
    signal resoudre()
    signal clique()
    readonly property var com: fenetre.commentaires
    implicitHeight: contenu.implicitHeight + 24
    radius: 12
    color: Couleurs.champ
    border.width: carte.choisie ? 1.5 : 1
    border.color: carte.choisie ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
    TapHandler { onTapped: carte.clique() }

    ColumnLayout {
        id: contenu
        x: 14
        y: 12
        width: parent.width - 28
        spacing: 8
        RowLayout {
            Layout.fillWidth: true
            spacing: 8
            Rectangle {
                Layout.preferredWidth: 26
                Layout.preferredHeight: 26
                radius: 13
                color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.1) : "#F0E7DB"
                Text {
                    anchors.centerIn: parent
                    text: carte.k ? carte.com.initiales(carte.k.auteur) : ""
                    font.pixelSize: 10
                    font.weight: Font.Bold
                    color: Couleurs.texte2
                }
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 0
                Text {
                    Layout.fillWidth: true
                    text: carte.k ? carte.k.auteur || "" : ""
                    font.pixelSize: 13
                    font.weight: Font.DemiBold
                    color: Couleurs.texte
                    elide: Text.ElideRight
                }
                Text {
                    Layout.fillWidth: true
                    text: carte.k ? [carte.com.quand(carte.k.date), carte.lieu].filter(function (x) { return x }).join(" · ") : ""
                    visible: text !== ""
                    font.pixelSize: 11
                    color: Couleurs.texte2
                    elide: Text.ElideRight
                }
            }
            Outil {
                visible: carte.actions
                Layout.preferredWidth: 28
                Layout.preferredHeight: 28
                picto: "M4 20h4L18.5 9.5l-4-4L4 16z M12.5 7.5l4 4"
                aide: "Modifier le commentaire"
                onClicked: carte.modifier()
            }
            Outil {
                visible: carte.actions
                Layout.preferredWidth: 28
                Layout.preferredHeight: 28
                picto: "M5 12.5l4.5 4.5L19 7.5"
                encre: Couleurs.foret
                aide: "Résolu : retirer le commentaire (Ctrl+Z le rend)"
                onClicked: carte.resoudre()
            }
        }
        Text {
            Layout.fillWidth: true
            text: carte.k ? carte.k.texte || "" : ""
            font.pixelSize: 13
            lineHeight: 18
            lineHeightMode: Text.FixedHeight
            color: Couleurs.texte
            wrapMode: Text.Wrap
        }
    }
}
