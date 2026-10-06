// Ligne d'article : titre, résumé (et thème, dans les résultats de recherche), chevron.
import QtQuick
import QtQuick.Layouts
import "../reglages"

MouseArea {
    id: carte
    property var article
    property bool avecTheme: false
    implicitHeight: colonne.implicitHeight + 26
    hoverEnabled: true
    cursorShape: Qt.PointingHandCursor
    onClicked: fenetre.ouvrirArticle(article.id)
    Rectangle {
        anchors.fill: parent
        radius: 14
        color: Couleurs.champ
        border.width: 0.5
        border.color: carte.containsMouse ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.45) : Couleurs.ligne
    }
    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 16
        anchors.rightMargin: 14
        spacing: 12
        ColumnLayout {
            id: colonne
            Layout.fillWidth: true
            spacing: 3
            Text {
                visible: carte.avecTheme
                text: carte.article ? carte.article.theme.titre.toUpperCase() : ""
                font.pixelSize: 10
                font.weight: Font.DemiBold
                font.letterSpacing: 0.4
                color: carte.article ? carte.article.theme.encre : Couleurs.texte3
            }
            Text { Layout.fillWidth: true; text: carte.article ? carte.article.titre : ""; wrapMode: Text.WordWrap; font.pixelSize: 14; font.weight: Font.Medium; color: Couleurs.texte }
            Text { Layout.fillWidth: true; text: carte.article ? carte.article.resume : ""; wrapMode: Text.WordWrap; font.pixelSize: 12; color: Couleurs.texte2 }
        }
        Picto { trace: "M9 6l6 6l-6 6"; encre: Couleurs.texte3 }
    }
}
