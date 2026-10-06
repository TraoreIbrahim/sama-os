// Un article de l'aide : thème, titre, résumé, texte (Markdown mis en HTML), puis les autres articles du même thème.
// Les liens « sama:… » ouvrent un article, un réglage ou une application (Aide.qml, suivreLien).
import QtQuick
import QtQuick.Layouts
import "../reglages"

Item {
    readonly property var article: fenetre.article
    implicitHeight: colonne.implicitHeight + 56

    ColumnLayout {
        id: colonne
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.top: parent.top
        anchors.topMargin: 28
        width: Math.min(parent.width - 88, 720)
        spacing: 0

        // Thème (retour à la liste du thème)
        MouseArea {
            Layout.preferredWidth: fil.implicitWidth
            Layout.preferredHeight: fil.implicitHeight
            cursorShape: Qt.PointingHandCursor
            onClicked: fenetre.aller({ type: "theme", id: article.theme.id })
            RowLayout {
                id: fil
                spacing: 8
                Rectangle {
                    Layout.preferredWidth: 24
                    Layout.preferredHeight: 24
                    radius: 7
                    color: article ? article.theme.fond : "transparent"
                    Picto { anchors.centerIn: parent; width: 13; height: 13; trace: article ? article.theme.picto : ""; encre: article ? article.theme.encre : Couleurs.texte }
                }
                Text { text: article ? article.theme.titre : ""; font.pixelSize: 12; font.weight: Font.DemiBold; color: article ? article.theme.encre : Couleurs.texte2 }
            }
        }
        Text {
            Layout.fillWidth: true
            Layout.topMargin: 14
            wrapMode: Text.WordWrap
            text: article ? article.titre : ""
            font.pixelSize: 28
            font.weight: Font.Medium
            font.letterSpacing: -0.2
            color: Couleurs.texte
        }
        Text {
            Layout.fillWidth: true
            Layout.topMargin: 6
            wrapMode: Text.WordWrap
            text: article ? article.resume : ""
            font.pixelSize: 15
            color: Couleurs.texte2
        }
        Rectangle { Layout.fillWidth: true; Layout.topMargin: 20; Layout.bottomMargin: 18; height: 0.5; color: Couleurs.ligne }
        Text {
            id: corps
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            textFormat: Text.RichText         // (HTML préparé par aide.py, liens aux couleurs de Sama)
            text: article ? article.html.replace(/COULEUR_LIEN/g, String(Couleurs.lateriteEncre)) : ""
            font.pixelSize: 15
            lineHeight: 1.25
            color: Couleurs.texte
            onLinkActivated: lien => fenetre.suivreLien(lien)
            HoverHandler { cursorShape: corps.hoveredLink ? Qt.PointingHandCursor : Qt.ArrowCursor }
        }

        // Dans le même thème
        Text {
            visible: autres.count > 0
            Layout.topMargin: 32
            Layout.bottomMargin: 10
            text: "Dans le même thème"
            font.pixelSize: 14
            font.weight: Font.DemiBold
            color: Couleurs.texte
        }
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 8
            Repeater {
                id: autres
                model: article ? article.theme.articles.filter(function (a) { return a.id !== article.id }).map(function (a) { return fenetre.articleDe(a.id) }) : []
                delegate: CarteArticle { Layout.fillWidth: true; article: modelData }
            }
        }
    }
}
