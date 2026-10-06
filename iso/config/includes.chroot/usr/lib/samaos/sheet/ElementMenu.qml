// Ligne d'un menu : une commande du catalogue (clé), son nom et son raccourci ; ou un sous-menu.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

QQC2.MenuItem {
    id: element
    property string cle: ""
    readonly property var commande: cle ? fenetre.actions.trouver(cle) : null
    text: commande ? commande.nom : subMenu ? subMenu.title : ""
    enabled: !commande || !commande.actif || commande.actif()
    implicitHeight: 32
    implicitWidth: 288
    onTriggered: if (cle) fenetre.actions.lancer(cle)
    background: Rectangle {
        radius: 8
        color: element.highlighted ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.25 : 0.06) : "transparent"
    }
    contentItem: RowLayout {
        spacing: 12
        opacity: element.enabled ? 1 : 0.4
        Text {
            Layout.fillWidth: true
            Layout.leftMargin: 6
            text: element.text
            font.pixelSize: 13
            color: Couleurs.texte
            elide: Text.ElideRight
        }
        Text {
            visible: text !== ""
            text: element.subMenu ? "›" : (element.commande ? element.commande.raccourci : "")
            font.pixelSize: element.subMenu ? 15 : 12
            color: Couleurs.texte3
            Layout.rightMargin: 4
        }
    }
    arrow: null
    indicator: null
}
