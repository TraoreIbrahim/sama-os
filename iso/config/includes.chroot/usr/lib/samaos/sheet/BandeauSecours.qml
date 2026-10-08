// Le bandeau d'un document récupéré (Sama Docs, Sama Sheet) : son état au moment de la dernière copie de secours,
// avant que l'application ne s'arrête ; Enregistrer le remet à son emplacement d'origine (ou en demande un).
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: bandeau
    property var reprise: null             // { titre, original, quand }
    property string genre: "Document"      // (« Classeur » dans Sheet)
    signal enregistrer()
    visible: reprise !== null
    implicitHeight: visible ? ligne.implicitHeight + 16 : 0
    color: Qt.rgba(fenetre.accent.r, fenetre.accent.g, fenetre.accent.b, Couleurs.sombre ? 0.2 : 0.09)
    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: Couleurs.bord }

    function quand(secondes) {
        var d = new Date(secondes * 1000), maintenant = new Date()
        var jours = Math.floor((new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate())
                                - new Date(d.getFullYear(), d.getMonth(), d.getDate())) / 86400000)
        var h = Qt.formatTime(d, "hh:mm")
        return jours <= 0 ? "de " + h : jours === 1 ? "d'hier à " + h : "du " + Qt.formatDate(d, "d MMMM") + " à " + h
    }

    RowLayout {
        id: ligne
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.verticalCenter: parent.verticalCenter
        anchors.leftMargin: 18
        anchors.rightMargin: 12
        spacing: 12
        Text {
            Layout.fillWidth: true
            text: bandeau.reprise ? "<b>" + bandeau.genre + " récupéré</b> : son état " + bandeau.quand(bandeau.reprise.quand)
                                    + ", avant l'arrêt de l'application. Il n'est pas encore enregistré"
                                    + (bandeau.reprise.original ? " dans « " + bandeau.reprise.original.split("/").pop() + " »." : ".")
                                  : ""
            textFormat: Text.StyledText
            font.pixelSize: 13
            color: Couleurs.texte
            wrapMode: Text.Wrap
        }
        QQC2.AbstractButton {
            id: bouton
            implicitHeight: 30
            implicitWidth: texteBouton.implicitWidth + 26
            hoverEnabled: true
            focusPolicy: Qt.NoFocus
            onClicked: bandeau.enregistrer()
            background: Rectangle { radius: 9; color: bouton.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent }
            contentItem: Text {
                id: texteBouton
                text: bandeau.reprise && bandeau.reprise.original ? "Enregistrer" : "Enregistrer sous…"
                font.pixelSize: 13
                font.weight: Font.DemiBold
                color: "#FFFFFF"
                horizontalAlignment: Text.AlignHCenter
                verticalAlignment: Text.AlignVCenter
            }
        }
    }
}
