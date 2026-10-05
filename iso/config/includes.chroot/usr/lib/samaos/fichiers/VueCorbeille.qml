// Corbeille (maquette fic-02) : les éléments jetés, leur origine et le temps qu'il leur reste (30 jours), « Restaurer » ;
// « Vider la corbeille » après confirmation.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "Types.js" as Types

ColumnLayout {
    id: vue
    spacing: 0
    property var elements: []
    property bool confirmer: false
    readonly property real total: elements.reduce(function (t, e) { return t + e.taille }, 0)

    Commande { id: commande }
    function relire() {
        commande.lancer(fenetre.moteur + "corbeille", function (s) { try { vue.elements = JSON.parse(s) } catch (e) {} fenetre.relireCorbeille() })
    }
    Component.onCompleted: relire()
    Timer { interval: 4000; running: true; repeat: true; onTriggered: vue.relire() }

    // « il y a 6 jours »
    function ilYa(secondes) {
        var jours = Math.floor((Date.now() / 1000 - secondes) / 86400)
        if (jours <= 0) return "supprimé aujourd'hui"
        if (jours === 1) return "supprimé hier"
        return "supprimé il y a " + jours + " jours"
    }

    component Picto: Canvas {
        property string trace
        property color encre: Couleurs.texte2
        property real taillePicto: 16
        width: taillePicto
        height: taillePicto
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(taillePicto / 24, taillePicto / 24)
            c.strokeStyle = encre; c.lineWidth = 1.9; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }

    // En-tête : rappel des 30 jours, « Vider la corbeille »
    Item {
        Layout.fillWidth: true
        Layout.preferredHeight: 56
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 20
            anchors.rightMargin: 20
            spacing: 10
            Picto { trace: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8h.01"; encre: Couleurs.texte3 }
            Text { Layout.fillWidth: true; text: "Les éléments sont supprimés définitivement après 30 jours"; font.pixelSize: 13; color: Couleurs.texte2 }
            QQC2.AbstractButton {
                visible: vue.elements.length > 0
                implicitHeight: 32
                implicitWidth: rangeeVider.implicitWidth + 28
                onClicked: vue.confirmer = true
                background: Rectangle { radius: 16; color: Qt.rgba(163 / 255, 50 / 255, 42 / 255, parent.hovered ? 0.16 : 0.1) }
                contentItem: Item {
                    Row {
                        id: rangeeVider
                        anchors.centerIn: parent
                        spacing: 7
                        Picto { anchors.verticalCenter: parent.verticalCenter; taillePicto: 14; trace: "M5 7h14 M10 7V5h4v2 M7 7l1 12a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2l1-12"; encre: "#A3322A" }
                        Text { anchors.verticalCenter: parent.verticalCenter; text: "Vider la corbeille"; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#A3322A" }
                    }
                }
            }
        }
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Couleurs.ligne }
    }

    // Confirmation
    Rectangle {
        visible: vue.confirmer
        Layout.fillWidth: true
        Layout.margins: 20
        Layout.bottomMargin: 0
        Layout.preferredHeight: 64
        radius: 14
        color: Qt.rgba(163 / 255, 50 / 255, 42 / 255, Couleurs.sombre ? 0.2 : 0.09)
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 16
            anchors.rightMargin: 14
            spacing: 12
            Picto { trace: "M12 4l9 16H3z M12 10v4 M12 17h.01"; encre: "#A3322A" }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text {
                    text: "Supprimer définitivement " + (vue.elements.length > 1 ? "ces " + vue.elements.length + " éléments" : "cet élément") + " ?"
                    font.pixelSize: 14; font.weight: Font.DemiBold; color: "#A3322A"
                }
                Text { text: "Cette action est irréversible. " + Types.taille(vue.total) + " seront libérés."; font.pixelSize: 12; color: Couleurs.texte2 }
            }
            BoutonSama { text: "Annuler"; onClicked: vue.confirmer = false }
            QQC2.AbstractButton {
                implicitHeight: 30
                implicitWidth: 70
                onClicked: { vue.confirmer = false; commande.lancer(fenetre.moteur + "vider", function () { vue.relire() }) }
                background: Rectangle { radius: 15; color: "#A3322A"; opacity: parent.down ? 0.85 : 1 }
                contentItem: Text { text: "Vider"; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#FFFFFF"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
            }
        }
    }

    ListView {
        id: liste
        Layout.fillWidth: true
        Layout.fillHeight: true
        Layout.margins: 20
        Layout.topMargin: 14
        clip: true
        spacing: 8
        boundsBehavior: Flickable.StopAtBounds
        model: vue.elements
        QQC2.ScrollBar.vertical: QQC2.ScrollBar {}
        delegate: Rectangle {
            width: liste.width
            height: 58
            radius: 14
            color: Couleurs.carte
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 12
                anchors.rightMargin: 12
                spacing: 14
                Rectangle {
                    Layout.preferredWidth: 34
                    Layout.preferredHeight: 34
                    radius: 9
                    color: Types.familles[modelData.famille].teinte
                    IconeFichier {
                        visible: modelData.famille === "dossier"
                        anchors.centerIn: parent
                        width: 22; height: 22
                        dossier: true
                    }
                    Text {
                        visible: modelData.famille !== "dossier"
                        anchors.centerIn: parent
                        text: Types.extension(modelData.nom).toUpperCase().substring(0, 4)
                        font.pixelSize: 9
                        font.weight: Font.Bold
                        color: "#FFFFFF"
                    }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 2
                    Text { Layout.fillWidth: true; text: modelData.nom; elide: Text.ElideMiddle; font.pixelSize: 14; font.weight: Font.Medium; color: Couleurs.texte }
                    Text {
                        Layout.fillWidth: true
                        elide: Text.ElideRight
                        text: "Origine : " + (modelData.dossierOrigine || "/") + " · " + Types.taille(modelData.taille) + " · " + vue.ilYa(modelData.supprime)
                        font.pixelSize: 12
                        color: Couleurs.texte2
                    }
                }
                Row {
                    spacing: 6
                    Picto { anchors.verticalCenter: parent.verticalCenter; taillePicto: 14; trace: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7v5l3 2"; encre: Couleurs.texte3 }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: modelData.joursRestants > 1 ? "Encore " + modelData.joursRestants + " jours" : "Dernier jour"
                        font.pixelSize: 12
                        color: Couleurs.texte2
                    }
                }
                BoutonSama {
                    text: "Restaurer"
                    onClicked: commande.lancer(fenetre.moteur + "restaurer " + commande.q(modelData.id), function () { vue.relire() })
                }
            }
        }
    }
    Column {
        visible: vue.elements.length === 0
        Layout.alignment: Qt.AlignHCenter
        Layout.bottomMargin: 200
        spacing: 6
        Text { anchors.horizontalCenter: parent.horizontalCenter; text: "La corbeille est vide"; font.pixelSize: 15; font.weight: Font.Medium; color: Couleurs.texte2 }
        Text { anchors.horizontalCenter: parent.horizontalCenter; text: "Ce que vous supprimez y reste 30 jours avant de disparaître"; font.pixelSize: 12; color: Couleurs.texte3 }
    }

    // Barre d'état
    Item {
        Layout.fillWidth: true
        Layout.preferredHeight: 30
        Rectangle { anchors.top: parent.top; width: parent.width; height: 1; color: Couleurs.ligne }
        Text {
            anchors.verticalCenter: parent.verticalCenter
            anchors.left: parent.left
            anchors.leftMargin: 16
            text: vue.elements.length + (vue.elements.length > 1 ? " éléments" : " élément") + (vue.elements.length ? " · " + Types.taille(vue.total) : "")
            font.pixelSize: 11
            color: Couleurs.texte3
        }
    }
}
