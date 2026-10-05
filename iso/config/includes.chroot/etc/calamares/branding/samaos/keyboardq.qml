/* Installateur de Sama — « Votre clavier » (maquette dem-03, colonne de droite) : les dispositions les plus
 * utilisées en cartes, toutes les autres dans une liste, et un champ pour essayer le clavier.
 */
import io.calamares.core 1.0
import io.calamares.ui 1.0
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls
import "composants"

Rectangle {
    id: page
    color: "#FBF9F6"

    // Dispositions proposées : code XKB, nom, détail, aperçu des touches
    readonly property var proposees: [
        { cle: "fr", nom: "Français", detail: "AZERTY", apercu: "AZERTY" },
        { cle: "ml", nom: "Dioula / Bambara", detail: "AZERTY étendu", apercu: "ɛ ɔ ɲ ŋ" },
        { cle: "sn", nom: "Wolof", detail: "AZERTY étendu", apercu: "ë ñ ŋ" },
        { cle: "us", nom: "Anglais", detail: "QWERTY", apercu: "QWERTY" }
    ]
    // Index de chaque disposition dans le modèle de Calamares (rôle « key » : code XKB)
    property var indexParCle: ({})
    readonly property int courante: config.keyboardLayoutsModel.currentIndex
    property string libelleCourant: ""
    Repeater {
        model: config.keyboardLayoutsModel
        delegate: Item {
            Component.onCompleted: {
                var m = page.indexParCle
                m[String(model.key)] = index
                page.indexParCle = m
                if (index === page.courante) page.libelleCourant = String(model.label)
            }
        }
    }
    onCouranteChanged: libelleCourant = ""
    function choisir(index) { config.keyboardLayoutsModel.currentIndex = index }

    ColumnLayout {
        anchors.fill: parent
        anchors.leftMargin: 40
        anchors.rightMargin: 40
        anchors.topMargin: 30
        spacing: 0

        Text { text: "Votre clavier"; font.pixelSize: 26; font.weight: Font.Medium; color: "#1F1C18" }
        Text {
            Layout.topMargin: 6
            text: "Choisissez la disposition des touches de votre clavier, puis essayez-la."
            font.pixelSize: 14
            color: "#665E54"
        }

        Text { Layout.topMargin: 26; text: "Disposition du clavier"; font.pixelSize: 12; font.weight: Font.Medium; color: "#665E54" }
        GridLayout {
            Layout.topMargin: 6
            Layout.fillWidth: true
            columns: 2
            rowSpacing: 10
            columnSpacing: 10
            Repeater {
                model: page.proposees
                delegate: MouseArea {
                    id: carte
                    readonly property int indexModele: page.indexParCle[modelData.cle] !== undefined ? page.indexParCle[modelData.cle] : -1
                    readonly property bool choisie: indexModele >= 0 && indexModele === page.courante
                    visible: indexModele >= 0
                    Layout.fillWidth: true
                    Layout.preferredHeight: 74
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: page.choisir(indexModele)
                    Rectangle {
                        anchors.fill: parent
                        radius: 14
                        color: carte.choisie ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.1) : "#FFFFFF"
                        border.width: carte.choisie ? 1.5 : 1
                        border.color: carte.choisie ? "#B5532F" : Qt.rgba(31 / 255, 28 / 255, 24 / 255, carte.containsMouse ? 0.3 : 0.16)
                    }
                    // Pastille de choix, nom et détail à gauche, aperçu des touches à droite
                    Rectangle {
                        anchors.left: parent.left
                        anchors.leftMargin: 14
                        anchors.verticalCenter: parent.verticalCenter
                        width: 20
                        height: 20
                        radius: 10
                        color: "#FFFFFF"
                        border.width: carte.choisie ? 6 : 1.5
                        border.color: carte.choisie ? "#B5532F" : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                    }
                    Column {
                        anchors.left: parent.left
                        anchors.leftMargin: 46
                        anchors.right: touches.left
                        anchors.rightMargin: 10
                        anchors.verticalCenter: parent.verticalCenter
                        spacing: 2
                        Text { text: modelData.nom; font.pixelSize: 14; font.weight: Font.Medium; color: "#1F1C18" }
                        Text { text: modelData.detail; font.pixelSize: 12; color: "#665E54" }
                    }
                    Rectangle {
                        id: touches
                        anchors.right: parent.right
                        anchors.rightMargin: 14
                        anchors.verticalCenter: parent.verticalCenter
                        width: apercu.implicitWidth + 16
                        height: 26
                        radius: 8
                        color: carte.choisie ? Qt.rgba(1, 1, 1, 0.7) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
                        Text { id: apercu; anchors.centerIn: parent; text: modelData.apercu; font.pixelSize: 13; font.weight: Font.Medium; color: carte.choisie ? "#93401F" : "#665E54" }
                    }
                }
            }
        }
        // Disposition choisie dans la liste complète (hors cartes)
        Text {
            Layout.topMargin: 10
            visible: page.proposees.every(function (p) { return page.indexParCle[p.cle] !== page.courante })
            text: "Disposition choisie : " + page.libelleCourant
            font.pixelSize: 13
            font.weight: Font.Medium
            color: "#93401F"
        }
        Text {
            Layout.topMargin: 10
            text: "Une autre disposition…"
            font.pixelSize: 13
            font.weight: Font.Medium
            color: "#93401F"
            MouseArea { anchors.fill: parent; anchors.margins: -6; cursorShape: Qt.PointingHandCursor; onClicked: listeDispositions.ouvrir() }
        }

        Text { Layout.topMargin: 18; text: "Testez votre clavier"; font.pixelSize: 12; font.weight: Font.Medium; color: "#665E54" }
        Rectangle {
            Layout.topMargin: 6
            Layout.fillWidth: true
            Layout.preferredHeight: 44
            radius: 12
            color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
            border.width: 1.5
            border.color: essai.activeFocus ? "#B5532F" : Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.5)
            TextInput {
                id: essai
                anchors.fill: parent
                anchors.leftMargin: 14
                anchors.rightMargin: 14
                verticalAlignment: TextInput.AlignVCenter
                font.pixelSize: 15
                color: "#1F1C18"
                clip: true
                Text { visible: !essai.text; anchors.verticalCenter: parent.verticalCenter; text: "Tapez quelques mots, par exemple I ni ce"; font.pixelSize: 15; color: "#8A8277" }
            }
        }
        Text {
            Layout.topMargin: 6
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            text: "Les lettres ɛ, ɔ, ɲ et ŋ s'obtiennent avec la touche AltGr. Ajoutez d'autres dispositions plus tard dans Réglages."
            font.pixelSize: 12
            color: "#8A8277"
        }
        Item { Layout.fillHeight: true }
    }

    Liste {
        id: listeDispositions
        titre: "Disposition du clavier"
        modele: config.keyboardLayoutsModel
        texte: function (e) { return String(e.label) }
        onChoisi: (e, index) => page.choisir(index)
    }
}
