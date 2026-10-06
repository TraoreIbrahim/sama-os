// Volet des couleurs (texte ou remplissage des cellules) : couleurs de Sama, rangées du plus clair au plus soutenu,
// et « Automatique » (texte) ou « Aucun remplissage ».
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Volet {
    id: nuancier
    // Rangées de [couleur, nom]
    property var rangees: []
    property string aucune: "Automatique"
    property string aucunePicto: ""
    // couleur choisie (« » : automatique ou aucun remplissage)
    signal choisie(string couleur)

    QQC2.AbstractButton {
        id: boutonAucune
        width: grille.width
        height: 30
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        onClicked: { nuancier.close(); nuancier.choisie("") }
        background: Rectangle { radius: 8; color: boutonAucune.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06) : "transparent"; border.width: 0.5; border.color: Couleurs.bord }
        contentItem: RowLayout {
            spacing: 8
            Item { Layout.preferredWidth: 2 }
            Picto { trace: nuancier.aucunePicto; visible: nuancier.aucunePicto !== "" }
            Text { text: nuancier.aucune; font.pixelSize: 12; color: Couleurs.texte }
            Item { Layout.fillWidth: true }
        }
    }
    Grid {
        id: grille
        columns: nuancier.rangees.length ? nuancier.rangees[0].length : 8
        spacing: 6
        Repeater {
            model: [].concat.apply([], nuancier.rangees)
            delegate: QQC2.AbstractButton {
                id: pastille
                width: 24
                height: 24
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                QQC2.ToolTip.visible: hovered
                QQC2.ToolTip.text: modelData[1]
                QQC2.ToolTip.delay: 500
                onClicked: { nuancier.close(); nuancier.choisie(modelData[0]) }
                background: Rectangle {
                    radius: 6
                    color: modelData[0]
                    border.width: pastille.hovered ? 2 : 0.5
                    border.color: pastille.hovered ? Couleurs.texte : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.18)
                }
            }
        }
    }
}
