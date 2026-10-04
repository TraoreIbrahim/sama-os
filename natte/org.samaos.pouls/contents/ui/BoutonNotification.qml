// Bouton d'action d'une notification (« Répondre », « Installer »…) : pilule, latérite pour l'action principale.
import QtQuick
import org.kde.kirigami as Kirigami

MouseArea {
    id: bouton
    property string texte
    property bool principal: false
    property bool petit: false
    implicitWidth: libelle.implicitWidth + (petit ? 24 : 34)
    implicitHeight: petit ? 26 : 30
    hoverEnabled: true
    cursorShape: Qt.PointingHandCursor
    Rectangle {
        anchors.fill: parent
        radius: height / 2
        color: bouton.principal ? "#B5532F"
             : Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, bouton.containsMouse ? 0.1 : 0.05)
        opacity: bouton.pressed ? 0.85 : 1
    }
    Text {
        id: libelle
        anchors.centerIn: parent
        text: bouton.texte
        font.pixelSize: bouton.petit ? 12 : 13
        font.weight: bouton.principal ? Font.DemiBold : Font.Medium
        color: bouton.principal ? "#FFFFFF" : Kirigami.Theme.textColor
    }
}
