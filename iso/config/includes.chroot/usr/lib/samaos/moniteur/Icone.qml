// Tuile d'une application (icône de son fichier .desktop) ; « Services système » : tuile grise.
import QtQuick
import org.kde.kirigami as Kirigami
import "../reglages"

Item {
    property var appli: null
    property int cote: 26
    implicitWidth: cote
    implicitHeight: cote
    readonly property bool systeme: !appli || appli.cle === "services"
    Rectangle {
        anchors.fill: parent
        visible: parent.systeme
        radius: parent.cote * 0.3
        color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.12) : "#E4E0DA"
        Picto {
            anchors.centerIn: parent
            width: parent.width * 0.62
            height: width
            encre: Couleurs.texte2
            trace: "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z M12 3v3 M12 18v3 M3 12h3 M18 12h3 M5.6 5.6l2.1 2.1 M16.3 16.3l2.1 2.1 M5.6 18.4l2.1-2.1 M16.3 7.7l2.1-2.1"
        }
    }
    Kirigami.Icon {
        anchors.fill: parent
        visible: !parent.systeme
        source: !parent.appli || !parent.appli.icone ? "application-x-executable"
                : parent.appli.icone.indexOf("/") === 0 ? "file://" + parent.appli.icone : parent.appli.icone
    }
}
