// Des pastilles dans un menu (formats de nombre montrés par un exemple, calculs automatiques…). Un clic lance la commande
// et ferme le menu.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: pastilles
    property var menu: null
    property var cles: []
    property bool avecPicto: false
    implicitWidth: 300
    implicitHeight: flux.implicitHeight + 6

    Flow {
        id: flux
        x: 4
        width: parent.width - 8
        spacing: 6
        Repeater {
            model: pastilles.cles
            delegate: QQC2.AbstractButton {
                id: pastille
                readonly property var commande: fenetre.actions.trouver(modelData)
                implicitHeight: 30
                implicitWidth: contenu.implicitWidth + 22
                enabled: commande !== null && (!commande.actif || commande.actif())
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                Accessible.name: commande ? commande.nom : ""
                onClicked: {
                    if (pastilles.menu) pastilles.menu.dismiss()
                    fenetre.actions.lancer(modelData)
                }
                QQC2.ToolTip.visible: hovered
                QQC2.ToolTip.delay: 500
                QQC2.ToolTip.text: commande ? commande.nom + (commande.raccourci ? "  (" + commande.raccourci + ")" : "") : ""
                background: Rectangle {
                    radius: 9
                    color: pastille.down ? Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.22)
                         : pastille.hovered ? fenetre.vertFond : Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.2 : 0.04)
                }
                contentItem: Item {
                    opacity: pastille.enabled ? 1 : 0.38
                    RowLayout {
                        id: contenu
                        anchors.centerIn: parent
                        spacing: 6
                        Picto {
                            visible: pastilles.avecPicto
                            width: 14
                            height: 14
                            trace: fenetre.actions.pictos[modelData] || ""
                            encre: pastille.hovered ? fenetre.vertEncre : Couleurs.texte2
                        }
                        Text {
                            text: fenetre.actions.courts[modelData] || (pastille.commande ? pastille.commande.nom : "")
                            font.pixelSize: 13
                            font.features: { "tnum": 1 }
                            color: pastille.hovered ? fenetre.vertEncre : Couleurs.texte
                        }
                    }
                }
            }
        }
    }
}
