// Choix entre quelques vues ou modes (Grille | Fiches | Formulaire, Remplir | Composer) : des pastilles avec une
// icône sur un fond discret (« Segments » des réglages n'en a pas) ; celle qui est choisie est claire, avec une ombre
// légère. De l'air partout : entre le bord du fond et la pastille, entre le bord de la pastille et l'icône, entre
// l'icône et le mot ; la place du mot en gras est gardée, rien ne bouge quand on change.
import QtQuick
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: segments
    // [[clé, nom, tracé (facultatif)]]
    property var modele: []
    property string courant: ""
    property real hauteur: 32
    property int taille: 12
    signal choisi(string cle)
    implicitWidth: rangee.implicitWidth + 8
    implicitHeight: hauteur
    radius: 10
    color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.25 : 0.06)

    Row {
        id: rangee
        anchors.centerIn: parent
        spacing: 2
        Repeater {
            model: segments.modele
            delegate: QQC2.AbstractButton {
                id: segment
                readonly property bool choisi: segments.courant === modelData[0]
                readonly property bool avecPicto: modelData.length > 2 && modelData[2] !== ""
                height: segments.hauteur - 8
                leftPadding: avecPicto ? 11 : 14
                rightPadding: 14
                implicitWidth: leftPadding + rightPadding + contenu.width
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                Accessible.name: modelData[1]
                onClicked: segments.choisi(modelData[0])
                TextMetrics { id: gras; font.pixelSize: segments.taille; font.weight: Font.DemiBold; text: modelData[1] }
                background: Item {
                    Rectangle { visible: segment.choisi; anchors.fill: parent; anchors.topMargin: 1; anchors.bottomMargin: -1; radius: 8; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.1) }
                    Rectangle {
                        anchors.fill: parent
                        radius: 8
                        color: segment.choisi ? Couleurs.champ : segment.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                    }
                }
                contentItem: Item {
                    Row {
                        id: contenu
                        anchors.verticalCenter: parent.verticalCenter
                        spacing: 7
                        Picto {
                            visible: segment.avecPicto
                            width: 14
                            height: 14
                            anchors.verticalCenter: parent.verticalCenter
                            trace: segment.avecPicto ? modelData[2] : ""
                            encre: segment.choisi ? fenetre.accentEncre : Couleurs.texte2
                        }
                        Text {
                            width: Math.ceil(gras.advanceWidth)
                            anchors.verticalCenter: parent.verticalCenter
                            horizontalAlignment: Text.AlignHCenter
                            text: modelData[1]
                            font.pixelSize: segments.taille
                            font.weight: segment.choisi ? Font.DemiBold : Font.Normal
                            color: segment.choisi ? fenetre.accentEncre : segment.hovered ? Couleurs.texte : Couleurs.texte2
                        }
                    }
                }
            }
        }
    }
}
