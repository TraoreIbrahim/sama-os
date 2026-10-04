// Section qui arrive avec une prochaine version de Sama (Sauvegarde avec Sama Grenier, Organisation avec Sama Parc).
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: fenetre.section.titre
    readonly property var textes: ({
        "sauvegarde": "La sauvegarde chiffrée de vos documents arrive avec Sama Grenier, le nuage de Sama.",
        "organisation": "Les réglages pour les ordinateurs d'une école, d'une entreprise ou d'une administration (gérés à plusieurs) arrivent avec une prochaine version de Sama."
    })
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: texte.implicitHeight + 44
        radius: 14
        color: Couleurs.carte
        Text {
            id: texte
            anchors.fill: parent
            anchors.margins: 22
            wrapMode: Text.WordWrap
            lineHeight: 1.3
            text: page.textes[fenetre.section.id] || "Cette section arrive avec une prochaine version de Sama."
            font.pixelSize: 14
            color: Couleurs.texte2
        }
    }
}
