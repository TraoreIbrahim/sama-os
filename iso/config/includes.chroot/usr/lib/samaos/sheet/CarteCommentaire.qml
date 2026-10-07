// Un commentaire, en carte (maquette « Docs · Blocs », marge des commentaires) : les initiales, le nom et le moment,
// le texte, puis ses réponses. Pour la case courante : Modifier, Résolu (le commentaire est retiré ; Ctrl+Z le rend),
// et Répondre… (Entrée l'ajoute). Dans la bulle de la grille comme dans le panneau des commentaires.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: carte
    property var k: null                 // { id, tab, auteur, date, texte, reponses, c, l }
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
    // (un clic sur la carte mène à la case ; pas un clic dans le champ de la réponse)
    TapHandler {
        onTapped: (point, bouton) => {
            if (champReponse.visible && champReponse.contains(champReponse.mapFromItem(carte, point.position))) return
            carte.clique()
        }
    }

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

        // Les réponses : les initiales, le nom et le moment, le texte
        Repeater {
            model: carte.k && carte.k.reponses ? carte.k.reponses : []
            delegate: RowLayout {
                id: reponse
                required property var modelData
                Layout.fillWidth: true
                Layout.topMargin: 2
                spacing: 8
                Rectangle {
                    Layout.alignment: Qt.AlignTop
                    Layout.preferredWidth: 22
                    Layout.preferredHeight: 22
                    radius: 11
                    color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.1) : "#F0E7DB"
                    Text {
                        anchors.centerIn: parent
                        text: carte.com.initiales(reponse.modelData.auteur)
                        font.pixelSize: 9
                        font.weight: Font.Bold
                        color: Couleurs.texte2
                    }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 1
                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 6
                        Text {
                            Layout.maximumWidth: parent.width * 0.6
                            text: reponse.modelData.auteur
                            font.pixelSize: 12
                            font.weight: Font.DemiBold
                            color: Couleurs.texte
                            elide: Text.ElideRight
                        }
                        Text {
                            Layout.fillWidth: true
                            text: carte.com.quand(reponse.modelData.date)
                            font.pixelSize: 11
                            color: Couleurs.texte2
                            elide: Text.ElideRight
                        }
                    }
                    Text {
                        Layout.fillWidth: true
                        text: reponse.modelData.texte
                        font.pixelSize: 13
                        lineHeight: 18
                        lineHeightMode: Text.FixedHeight
                        color: Couleurs.texte
                        wrapMode: Text.Wrap
                    }
                }
            }
        }

        // Répondre (maquette « Docs · Blocs ») : Entrée l'ajoute, Échap l'oublie
        QQC2.TextField {
            id: champReponse
            visible: carte.actions && carte.k !== null
            Layout.fillWidth: true
            Layout.preferredHeight: 32
            placeholderText: "Répondre…"
            placeholderTextColor: Couleurs.texte3
            font.pixelSize: 13
            color: Couleurs.texte
            leftPadding: 10
            rightPadding: 10
            maximumLength: 500
            background: Rectangle {
                radius: 8
                color: Couleurs.fond
                border.width: champReponse.activeFocus ? 1.5 : 1
                border.color: champReponse.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
            }
            onAccepted: {
                if (!text.trim()) return
                carte.com.repondre(carte.k.id, text)
                text = ""
                fenetre.doc.forceActiveFocus()
            }
            Keys.onEscapePressed: { text = ""; fenetre.doc.forceActiveFocus() }
        }
    }
}
