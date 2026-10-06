// Ligne d'un menu : une commande du catalogue (clé), avec son pictogramme, son nom, une ligne d'explication s'il le
// faut, et son raccourci en pastille.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

QQC2.MenuItem {
    id: element
    property string cle: ""
    readonly property var commande: cle ? fenetre.actions.trouver(cle) : null
    readonly property string detail: cle ? (fenetre.actions.details[cle] || "") : ""
    readonly property string raccourci: commande ? commande.raccourci : ""
    property string picto: cle ? (fenetre.actions.pictos[cle] || "") : ""
    // (le choix en cours, dans une liste de choix)
    property bool coche: false
    text: commande ? commande.nom : subMenu ? subMenu.title : ""
    enabled: !commande || !commande.actif || commande.actif()
    implicitHeight: detail ? 46 : 36
    implicitWidth: 328
    leftPadding: 6
    rightPadding: 8
    onTriggered: if (cle) fenetre.actions.lancer(cle)
    background: Rectangle {
        radius: 9
        color: element.highlighted ? Qt.rgba(31 / 255, 94 / 255, 122 / 255, Couleurs.sombre ? 0.25 : 0.08) : "transparent"
    }
    contentItem: RowLayout {
        spacing: 11
        opacity: element.enabled ? 1 : 0.38
        Rectangle {
            visible: element.picto !== ""
            Layout.preferredWidth: 28
            Layout.preferredHeight: 28
            radius: 8
            color: element.highlighted ? Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.14) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.2 : 0.045)
            Picto {
                anchors.centerIn: parent
                width: 16
                height: 16
                trace: element.picto
                encre: element.highlighted ? fenetre.accentEncre : Couleurs.texte2
            }
        }
        ColumnLayout {
            Layout.fillWidth: true
            Layout.leftMargin: element.picto === "" ? 6 : 0
            spacing: 1
            Text {
                Layout.fillWidth: true
                text: element.text
                font.pixelSize: 13
                color: element.highlighted ? fenetre.accentEncre : Couleurs.texte
                elide: Text.ElideRight
            }
            Text {
                Layout.fillWidth: true
                visible: element.detail !== ""
                text: element.detail
                font.pixelSize: 12
                color: Couleurs.texte3
                elide: Text.ElideRight
            }
        }
        // Raccourci : une petite touche
        Rectangle {
            visible: element.raccourci !== ""
            Layout.preferredHeight: 20
            Layout.preferredWidth: touche.implicitWidth + 12
            radius: 5
            color: "transparent"
            border.width: 0.5
            border.color: Couleurs.bord
            Text { id: touche; anchors.centerIn: parent; text: element.raccourci; font.pixelSize: 11; color: Couleurs.texte2 }
        }
        Picto {
            visible: element.coche
            width: 15
            height: 15
            trace: "M5 12.5l4.5 4.5L19 7.5"
            encre: fenetre.accentEncre
            trait: 2.2
        }
        Picto {
            visible: element.subMenu !== null
            width: 14
            height: 14
            trace: "M9 6l6 6-6 6"
            encre: Couleurs.texte3
        }
    }
    arrow: null
    indicator: null
}
