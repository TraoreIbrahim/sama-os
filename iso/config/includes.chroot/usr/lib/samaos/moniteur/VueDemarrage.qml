// Onglet Démarrage : les applications ouvertes avec la session, chacune avec son interrupteur
// (~/.config/autostart, Hidden=true pour l'arrêter). Les services de Sama n'y sont pas : ils restent actifs.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

ColumnLayout {
    spacing: 14

    Text {
        Layout.fillWidth: true
        Layout.leftMargin: 28
        Layout.rightMargin: 28
        wrapMode: Text.WordWrap
        text: "Ces applications s'ouvrent d'elles-mêmes avec votre session. Moins il y en a, plus vite l'ordinateur est prêt."
        font.pixelSize: 13
        color: Couleurs.texte2
    }

    Rectangle {
        Layout.fillWidth: true
        Layout.leftMargin: 24
        Layout.rightMargin: 24
        Layout.preferredHeight: fenetre.demarrage.length ? Math.min(liste.contentHeight + 12, 420) : 150
        radius: 16
        color: Couleurs.champ
        border.width: 0.5
        border.color: Couleurs.ligne
        clip: true

        ListView {
            id: liste
            anchors.fill: parent
            anchors.margins: 6
            model: fenetre.demarrage
            QQC2.ScrollBar.vertical: QQC2.ScrollBar {}
            delegate: Item {
                width: ListView.view.width
                height: 58
                Icone { id: tuile; anchors.left: parent.left; anchors.leftMargin: 16; anchors.verticalCenter: parent.verticalCenter; appli: modelData; cote: 30 }
                Interrupteur {
                    id: interrupteur
                    anchors.right: parent.right
                    anchors.rightMargin: 16
                    anchors.verticalCenter: parent.verticalCenter
                    actif: modelData.actif
                    onBascule: actif => commande.lancer(fenetre.moteur + "demarrage-regler " + commande.q(modelData.fichier) + " " + actif,
                                                        function () { fenetre.releverDemarrage() })
                }
                Text {
                    id: etatTexte
                    anchors.right: interrupteur.left
                    anchors.rightMargin: 14
                    anchors.verticalCenter: parent.verticalCenter
                    text: modelData.actif ? "S'ouvre au démarrage" : "Arrêtée"
                    font.pixelSize: 12
                    color: Couleurs.texte3
                }
                Column {
                    anchors.left: tuile.right
                    anchors.leftMargin: 14
                    anchors.right: etatTexte.left
                    anchors.rightMargin: 14
                    anchors.verticalCenter: parent.verticalCenter
                    spacing: 2
                    Text { width: parent.width; text: modelData.nom; elide: Text.ElideRight; font.pixelSize: 13; font.weight: Font.Medium; color: Couleurs.texte }
                    Text {
                        width: parent.width
                        visible: text !== ""
                        text: modelData.detail
                        elide: Text.ElideRight
                        font.pixelSize: 12
                        color: Couleurs.texte3
                    }
                }
                Rectangle { visible: index < fenetre.demarrage.length - 1; anchors.bottom: parent.bottom; x: 16; width: parent.width - 32; height: 0.5; color: Couleurs.ligne }
            }
        }
        ColumnLayout {
            anchors.centerIn: parent
            visible: fenetre.demarrage.length === 0
            spacing: 6
            Text { Layout.alignment: Qt.AlignHCenter; text: "Aucune application ne s'ouvre avec votre session"; font.pixelSize: 14; font.weight: Font.Medium; color: Couleurs.texte }
            Text { Layout.alignment: Qt.AlignHCenter; text: "L'ordinateur est prêt dès l'ouverture de session."; font.pixelSize: 12; color: Couleurs.texte3 }
        }
    }

    RowLayout {
        Layout.leftMargin: 28
        Layout.rightMargin: 28
        spacing: 8
        Picto { Layout.alignment: Qt.AlignTop; trace: "M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6z M9 12l2 2l4-4"; encre: Couleurs.foret }
        Text {
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            text: "Les services de Sama (travail gardé en cas de coupure, alertes de batterie et de data, sauvegardes) ne sont pas dans cette liste : ils restent toujours actifs."
            font.pixelSize: 12
            color: Couleurs.texte3
        }
    }
    Item { Layout.fillHeight: true }
}
