// Une rangée de tuiles dans un menu (ajouter une ligne au-dessus, un graphique en secteurs…) : on voit ce que fait la
// commande avant de la choisir. Un clic la lance et ferme le menu.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: tuiles
    property var menu: null
    property var cles: []
    property bool sansNom: false            // tuiles sans nom (gras, italique, alignements…) : le nom en bulle
    implicitWidth: 300
    implicitHeight: (sansNom ? 38 : 60) + 4

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 4
        anchors.rightMargin: 4
        anchors.bottomMargin: 4
        spacing: 6
        Repeater {
            model: tuiles.cles
            delegate: QQC2.AbstractButton {
                id: tuile
                readonly property var commande: fenetre.actions.trouver(modelData)
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredWidth: 10
                enabled: commande !== null && (!commande.actif || commande.actif())
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                Accessible.name: commande ? commande.nom : ""
                onClicked: {
                    if (tuiles.menu) tuiles.menu.dismiss()
                    fenetre.actions.lancer(modelData)
                }
                QQC2.ToolTip.visible: hovered && (tuiles.sansNom || (fenetre.actions.courts[modelData] || "") !== "")
                QQC2.ToolTip.delay: 500
                QQC2.ToolTip.text: commande ? commande.nom + (commande.raccourci ? "  (" + commande.raccourci + ")" : "") : ""
                background: Rectangle {
                    radius: 10
                    color: tuile.down ? Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.22)
                         : tuile.hovered ? fenetre.vertFond : Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.2 : 0.04)
                }
                contentItem: Item {
                    opacity: tuile.enabled ? 1 : 0.38
                    ColumnLayout {
                        anchors.centerIn: parent
                        width: parent.width - 6
                        spacing: 5
                        Picto {
                            Layout.alignment: Qt.AlignHCenter
                            width: tuiles.sansNom ? 17 : 20
                            height: width
                            trace: fenetre.actions.pictos[modelData] || ""
                            encre: tuile.hovered ? fenetre.vertEncre : Couleurs.texte
                            trait: /^gr/.test(modelData) && modelData !== "grLignes" && modelData !== "grSecteurs" && modelData !== "grAires" ? 3 : 1.7
                        }
                        Text {
                            visible: !tuiles.sansNom
                            Layout.fillWidth: true
                            horizontalAlignment: Text.AlignHCenter
                            text: fenetre.actions.courts[modelData] || (tuile.commande ? tuile.commande.nom : "")
                            font.pixelSize: 11
                            color: tuile.hovered ? fenetre.vertEncre : Couleurs.texte2
                            elide: Text.ElideRight
                        }
                    }
                }
            }
        }
    }
}
