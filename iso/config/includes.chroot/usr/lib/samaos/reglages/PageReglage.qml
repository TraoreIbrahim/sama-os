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
        // Lien de retour vers la section (dans une sous-page)
        Text {
            visible: fenetre.sousPage !== ""
            text: "‹  " + fenetre.section.titre
            font.pixelSize: 13
            font.weight: Font.Medium
            color: Couleurs.lateriteEncre
            MouseArea { anchors.fill: parent; anchors.margins: -6; cursorShape: Qt.PointingHandCursor; onClicked: fenetre.sousPage = "" }
        }
        Text {
            Layout.bottomMargin: 4
            text: page.titre
            font.pixelSize: 24
            font.weight: Font.Medium
            color: Couleurs.texte
        }
    }
}
