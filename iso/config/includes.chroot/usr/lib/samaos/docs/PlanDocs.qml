// Le plan du document (maquette « Docs · Le document libre ») : ses titres, dans l'ordre, en retrait selon leur niveau ;
// celui de la partie où est le curseur en vert ; un clic y mène. Relu un instant après chaque changement.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import Sama.Moteur
import "../reglages"

Rectangle {
    id: plan
    color: Couleurs.fond
    property var titres: []
    property int courant: -1

    function lire() {
        if (fenetre.doc.etat !== DocumentLO.Pret || !visible) return
        fenetre.appeler("SamaDocs.Plan", [], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{") return
            try { var d = JSON.parse(v); plan.titres = d.titres; plan.courant = d.courant } catch (e) { }
        })
    }
    Timer { id: relire; interval: 700; onTriggered: plan.lire() }
    Connections {
        target: fenetre.doc
        function onRevisionChanged() { relire.restart() }
        function onCurseurTexteChanged() { relire.restart() }
        function onEtatChanged() { plan.titres = []; relire.restart() }
    }
    onVisibleChanged: if (visible) relire.restart()

    Rectangle { anchors.right: parent.right; width: 0.5; height: parent.height; color: Couleurs.bord }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 12
        anchors.topMargin: 16
        spacing: 2
        Text { text: "Plan"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte2; Layout.leftMargin: 8; Layout.bottomMargin: 8 }
        QQC2.ScrollView {
            id: defilement
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: availableWidth
            QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff
            Column {
                width: defilement.availableWidth
                spacing: 2
                Repeater {
                    model: plan.titres
                    delegate: QQC2.AbstractButton {
                        id: titre
                        required property var modelData
                        required property int index
                        readonly property bool actuel: index === plan.courant
                        width: parent.width
                        implicitHeight: Math.max(34, nom.implicitHeight + 14)
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        onClicked: fenetre.appeler("SamaDocs.AllerAuTitre", [String(index)], function () { fenetre.doc.forceActiveFocus() })
                        background: Rectangle {
                            radius: 9
                            color: titre.actuel ? fenetre.accentFond : titre.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                        }
                        contentItem: Text {
                            id: nom
                            leftPadding: 10 + (titre.modelData.niveau - 1) * 14
                            rightPadding: 8
                            text: titre.modelData.texte
                            font.pixelSize: titre.modelData.niveau > 1 ? 13 : 14
                            font.weight: titre.actuel ? Font.DemiBold : Font.Normal
                            color: titre.actuel ? fenetre.accentEncre : titre.modelData.niveau > 1 ? Couleurs.texte2 : Couleurs.texte
                            elide: Text.ElideRight
                            maximumLineCount: 2
                            wrapMode: Text.Wrap
                            verticalAlignment: Text.AlignVCenter
                        }
                    }
                }
            }
        }
        Rectangle {
            visible: plan.titres.length === 0
            Layout.fillWidth: true
            Layout.preferredHeight: aide.implicitHeight + 24
            radius: 12
            color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.06) : "#F3ECE2"
            Text {
                id: aide
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                anchors.margins: 12
                text: "<b>Les titres font le plan.</b> Choisissez le style Titre 1 ou Titre 2 (Ctrl+1, Ctrl+2) : ils apparaissent ici, et un clic y mène."
                textFormat: Text.StyledText
                font.pixelSize: 13
                lineHeight: 17
                lineHeightMode: Text.FixedHeight
                color: Couleurs.texte2
                wrapMode: Text.Wrap
            }
        }
    }
}
