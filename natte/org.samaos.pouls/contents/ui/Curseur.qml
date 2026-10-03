// Curseur du centre de contrôle, comme la maquette : piste de 6 px, remplissage latérite, bouton blanc de 20 px.
import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami

RowLayout {
    id: curseur
    property string nomIcone
    property real valeur: 0          // de 0 à 1
    property bool disponible: true   // faux quand le matériel ne le permet pas (le curseur est alors masqué)
    signal deplace(real valeur)
    spacing: 12

    Kirigami.Icon {
        Layout.preferredWidth: 16
        Layout.preferredHeight: 16
        isMask: true
        color: Kirigami.Theme.disabledTextColor
        source: Qt.resolvedUrl("../icons/" + curseur.nomIcone + ".svg")
    }
    MouseArea {
        id: zone
        Layout.fillWidth: true
        Layout.preferredHeight: 20
        preventStealing: true
        function choisir(x) {
            var v = Math.max(0, Math.min(1, (x - 10) / (width - 20)))
            curseur.deplace(v)
        }
        onPressed: mouse => choisir(mouse.x)
        onPositionChanged: mouse => { if (pressed) choisir(mouse.x) }
        onWheel: wheel => curseur.deplace(Math.max(0, Math.min(1, curseur.valeur + (wheel.angleDelta.y > 0 ? 0.05 : -0.05))))

        Rectangle {
            x: 0; y: 7; width: parent.width; height: 6; radius: 3
            color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.12)
        }
        Rectangle {
            x: 0; y: 7; height: 6; radius: 3
            width: 10 + (parent.width - 20) * curseur.valeur
            color: "#B5532F"
        }
        Rectangle {
            x: (parent.width - 20) * curseur.valeur
            y: 0; width: 20; height: 20; radius: 10
            color: "#FFFFFF"
            border.width: 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.15)
            scale: zone.pressed ? 1.1 : 1
            Behavior on scale { NumberAnimation { duration: 100 } }
        }
    }
    Text {
        Layout.preferredWidth: 34
        horizontalAlignment: Text.AlignRight
        text: Math.round(curseur.valeur * 100) + " %"
        font.pixelSize: 12
        color: Kirigami.Theme.disabledTextColor
    }
}
