// Une page des Réglages : titre, puis le contenu en colonne, avec défilement.
import QtQuick
import QtQuick.Layouts

Flickable {
    id: page
    property string titre
    default property alias contenu: colonne.data

    contentHeight: colonne.implicitHeight + 56
    clip: true
    boundsBehavior: Flickable.StopAtBounds

    ColumnLayout {
        id: colonne
        x: 34
        y: 28
        width: Math.min(page.width - 68, 760)
        spacing: 14
        Text {
            Layout.bottomMargin: 4
            text: page.titre
            font.pixelSize: 24
            font.weight: Font.Medium
            color: Couleurs.texte
        }
    }
}
