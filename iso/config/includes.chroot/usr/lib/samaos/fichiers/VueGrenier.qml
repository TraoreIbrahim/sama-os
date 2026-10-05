// Sama Grenier (maquette fic-06) : le nuage de Sama arrive ; en attendant, la sauvegarde sur clé USB des Réglages.
import QtQuick
import QtQuick.Layouts
import "../reglages"

Item {
    ColumnLayout {
        anchors.centerIn: parent
        width: Math.min(parent.width - 80, 440)
        spacing: 10
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: 64
            Layout.preferredHeight: 64
            radius: 20
            color: Couleurs.sombre ? Qt.rgba(61 / 255, 90 / 255, 153 / 255, 0.3) : "#D9E3EA"
            Canvas {
                anchors.centerIn: parent
                width: 30
                height: 30
                onPaint: {
                    var c = getContext("2d"); c.reset(); c.scale(30 / 24, 30 / 24)
                    c.strokeStyle = Couleurs.sombre ? "#B4C6EE" : "#2D4A63"; c.lineWidth = 1.6; c.lineCap = "round"; c.lineJoin = "round"
                    c.path = "M7 18a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z"; c.stroke()
                }
            }
        }
        Text { Layout.alignment: Qt.AlignHCenter; Layout.topMargin: 6; text: "Sama Grenier arrive bientôt"; font.pixelSize: 18; font.weight: Font.Medium; color: Couleurs.texte }
        Text {
            Layout.fillWidth: true
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
            lineHeight: 1.3
            text: "Une copie chiffrée de vos documents, en ligne, à retrouver depuis n'importe quel ordinateur Sama. En attendant, vos dossiers peuvent être sauvegardés sur une clé USB ou un disque externe."
            font.pixelSize: 13
            color: Couleurs.texte2
        }
        BoutonSama {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 8
            text: "Réglages de sauvegarde"
            onClicked: commande.lancer("sama-reglages sauvegarde >/dev/null 2>&1 &")
        }
    }
    Commande { id: commande }
}
