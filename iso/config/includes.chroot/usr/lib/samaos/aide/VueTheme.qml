// Un thème de l'aide : sa tuile, son résumé, ses articles.
import QtQuick
import QtQuick.Layouts
import "../reglages"

Item {
    readonly property var theme: fenetre.themeDe(fenetre.page.id)
    implicitHeight: colonne.implicitHeight + 48

    ColumnLayout {
        id: colonne
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.top: parent.top
        anchors.topMargin: 28
        width: Math.min(parent.width - 88, 760)
        spacing: 20
        RowLayout {
            spacing: 16
            Rectangle {
                Layout.preferredWidth: 56
                Layout.preferredHeight: 56
                radius: 17
                color: theme ? theme.fond : "transparent"
                Picto { anchors.centerIn: parent; width: 26; height: 26; trace: theme ? theme.picto : ""; encre: theme ? theme.encre : Couleurs.texte }
            }
            ColumnLayout {
                spacing: 3
                Text { text: theme ? theme.titre : ""; font.pixelSize: 26; font.weight: Font.Medium; color: Couleurs.texte }
                Text { text: theme ? theme.resume : ""; font.pixelSize: 14; color: Couleurs.texte2 }
            }
        }
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 10
            Repeater {
                model: theme ? theme.articles.map(function (a) { return fenetre.articleDe(a.id) }) : []
                delegate: CarteArticle { Layout.fillWidth: true; article: modelData }
            }
        }
    }
}
