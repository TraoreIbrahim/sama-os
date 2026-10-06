// Résultats de la recherche dans l'aide (titres, mots-clés, résumés et textes, sans tenir compte des accents).
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    readonly property var trouves: fenetre.resultats(fenetre.page.q)
    implicitHeight: colonne.implicitHeight + 48

    ColumnLayout {
        id: colonne
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.top: parent.top
        anchors.topMargin: 24
        width: Math.min(parent.width - 88, 760)
        spacing: 16
        // Nouvelle recherche
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 46
            radius: 23
            color: Couleurs.champ
            border.width: champ.activeFocus ? 1.5 : 0.5
            border.color: champ.activeFocus ? Couleurs.laterite : Couleurs.bord
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 18
                anchors.rightMargin: 18
                spacing: 10
                Picto { trace: "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z M20 20l-4.8-4.8"; encre: Couleurs.texte3 }
                QQC2.TextField {
                    id: champ
                    Layout.fillWidth: true
                    text: fenetre.page.q
                    color: Couleurs.texte
                    font.pixelSize: 15
                    background: null
                    leftPadding: 0
                    onAccepted: if (text.trim() && text.trim() !== fenetre.page.q) fenetre.chercher(text)
                }
            }
        }
        Text {
            text: trouves.length ? trouves.length + (trouves.length > 1 ? " articles" : " article") + " pour « " + fenetre.page.q + " »"
                                 : "Aucun article pour « " + fenetre.page.q + " »"
            font.pixelSize: 15
            font.weight: Font.DemiBold
            color: Couleurs.texte
        }
        Text {
            visible: trouves.length === 0
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            text: "Essayez avec d'autres mots, plus simples : « clé », « data », « mot de passe », « coupure »… ou parcourez les thèmes depuis l'accueil de l'aide."
            font.pixelSize: 13
            color: Couleurs.texte2
        }
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 10
            Repeater {
                model: trouves
                delegate: CarteArticle { Layout.fillWidth: true; article: modelData; avecTheme: true }
            }
        }
    }
}
