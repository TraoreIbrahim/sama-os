// « À récupérer » (l'accueil de Sama Docs et de Sama Sheet) : les documents que l'application n'avait pas enregistrés
// quand elle s'est arrêtée (coupure, plantage, arrêt forcé), d'après leurs copies de secours (Secours.qml). Un clic
// rouvre la copie ; « Ne pas garder » l'efface, après confirmation.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

ColumnLayout {
    id: liste
    property var copies: []
    signal recuperer(var copie)
    signal oublier(var copie)
    property string aEffacer: ""          // (le dossier dont on confirme l'effacement)
    visible: copies.length > 0
    spacing: 4

    function quand(secondes) {
        var d = new Date(secondes * 1000), maintenant = new Date()
        var jours = Math.floor((new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate())
                                - new Date(d.getFullYear(), d.getMonth(), d.getDate())) / 86400000)
        var h = Qt.formatTime(d, "hh:mm")
        var j = Qt.locale("fr_FR").dayName(d.getDay())
        return jours <= 0 ? "Aujourd'hui à " + h : jours === 1 ? "Hier à " + h
               : jours < 7 ? j.charAt(0).toUpperCase() + j.slice(1) + " à " + h : "Le " + Qt.formatDate(d, "d MMMM")
    }

    Text { text: "À récupérer"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte2; Layout.leftMargin: 10 }
    Text {
        Layout.fillWidth: true
        Layout.leftMargin: 10
        Layout.bottomMargin: 6
        text: liste.copies.length > 1 ? "Ils n'avaient pas été enregistrés quand l'application s'est arrêtée."
                                      : "Il n'avait pas été enregistré quand l'application s'est arrêtée."
        font.pixelSize: 12
        lineHeight: 16
        lineHeightMode: Text.FixedHeight
        color: Couleurs.texte3
        wrapMode: Text.Wrap
    }
    Repeater {
        model: liste.copies.slice(0, 4)
        delegate: Rectangle {
            id: copie
            required property var modelData
            readonly property bool confirmer: liste.aEffacer === modelData.dossier
            Layout.fillWidth: true
            implicitHeight: colonne.implicitHeight + 20
            radius: 12
            color: Qt.rgba(fenetre.accent.r, fenetre.accent.g, fenetre.accent.b, zone.containsMouse && !confirmer ? 0.12 : 0.07)
            MouseArea {
                id: zone
                anchors.fill: parent
                hoverEnabled: true
                enabled: !copie.confirmer
                cursorShape: Qt.PointingHandCursor
                onClicked: liste.recuperer(copie.modelData)
            }
            ColumnLayout {
                id: colonne
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                anchors.leftMargin: 12
                anchors.rightMargin: 8
                spacing: 2
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 6
                    Text {
                        Layout.fillWidth: true
                        text: copie.modelData.titre
                        font.pixelSize: 14
                        font.weight: Font.DemiBold
                        color: Couleurs.texte
                        elide: Text.ElideRight
                    }
                    QQC2.AbstractButton {
                        id: croix
                        visible: !copie.confirmer
                        implicitWidth: 24
                        implicitHeight: 24
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        onClicked: liste.aEffacer = copie.modelData.dossier
                        QQC2.ToolTip.visible: hovered
                        QQC2.ToolTip.text: "Ne pas garder"
                        background: Rectangle { radius: 7; color: croix.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08) : "transparent" }
                        contentItem: Text { text: "×"; font.pixelSize: 16; color: Couleurs.texte2; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                    }
                }
                Text {
                    Layout.fillWidth: true
                    visible: !copie.confirmer
                    text: liste.quand(copie.modelData.quand) + (copie.modelData.original ? "" : " · jamais enregistré")
                    font.pixelSize: 12
                    color: Couleurs.texte2
                    wrapMode: Text.Wrap
                }
                // (effacer : on demande d'abord)
                Text {
                    Layout.fillWidth: true
                    visible: copie.confirmer
                    text: "Effacer ce qui n'avait pas été enregistré ?"
                    font.pixelSize: 12
                    color: Couleurs.texte2
                    wrapMode: Text.Wrap
                }
                RowLayout {
                    visible: copie.confirmer
                    spacing: 6
                    Layout.topMargin: 4
                    QQC2.AbstractButton {
                        id: effacer
                        implicitHeight: 28
                        implicitWidth: texteEffacer.implicitWidth + 20
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        onClicked: { liste.aEffacer = ""; liste.oublier(copie.modelData) }
                        background: Rectangle { radius: 8; color: effacer.hovered ? "#8E2B24" : "#A3322A" }
                        contentItem: Text { id: texteEffacer; text: "Effacer"; font.pixelSize: 12; font.weight: Font.DemiBold; color: "#FFFFFF"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                    }
                    QQC2.AbstractButton {
                        id: garder
                        implicitHeight: 28
                        implicitWidth: texteGarder.implicitWidth + 20
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        onClicked: liste.aEffacer = ""
                        background: Rectangle { radius: 8; color: garder.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08) : "transparent" }
                        contentItem: Text { id: texteGarder; text: "Garder"; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                    }
                }
            }
        }
    }
    Item { Layout.preferredHeight: 14 }
}
