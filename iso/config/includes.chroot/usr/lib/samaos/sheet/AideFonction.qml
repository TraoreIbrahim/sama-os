// Aide pendant la saisie d'une formule : la fonction ouverte (ses arguments, l'argument en cours en gras, ce qu'elle
// fait, un exemple), et des propositions quand on commence à écrire un nom (Tab ou Entrée pour choisir).
import QtQuick
import QtQuick.Layouts
import "../reglages"
import "Fonctions.js" as Fonctions

Rectangle {
    id: aide
    property string texte: ""          // la formule, jusqu'au curseur
    property bool completer: false     // propositions (dans la barre de formule)
    property int choisie: 0
    readonly property var analyse: Fonctions.analyser(texte)
    readonly property bool proposer: completer && analyse.propositions.length > 0
    readonly property var fonction: analyse.fonction
    signal proposition(string nom, string prefixe)
    function accepter() { if (proposer) proposition(analyse.propositions[choisie].nom, analyse.prefixe) }
    function suivante() { choisie = Math.min(choisie + 1, analyse.propositions.length - 1) }
    function precedente() { choisie = Math.max(choisie - 1, 0) }
    onAnalyseChanged: choisie = 0

    // Signature : SOMME(<b>nombre1</b> ; [nombre2] ; …)
    function signature(f, n) {
        if (!f) return ""
        var args = f.args.slice()
        var i = Math.min(n, args.length - 1)
        if (i >= 0 && args[i] === "…") i--
        return f.nom + "(" + args.map(function (a, k) { return k === i ? "<b>" + a + "</b>" : a }).join(" ; ") + ")"
    }

    visible: proposer || fonction !== null
    width: 400
    height: colonne.implicitHeight + 24
    radius: 12
    color: Couleurs.champ
    border.width: 0.5
    border.color: Couleurs.bord
    Rectangle { anchors.fill: parent; anchors.topMargin: 4; anchors.margins: -1; radius: 13; z: -1; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.1) }

    ColumnLayout {
        id: colonne
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.margins: 12
        spacing: 6

        // Propositions
        Repeater {
            model: aide.proposer ? aide.analyse.propositions : []
            delegate: Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 30
                radius: 7
                color: index === aide.choisie ? fenetre.accentFond : zone.containsMouse ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 8
                    anchors.rightMargin: 8
                    spacing: 10
                    Text { text: modelData.nom; font.pixelSize: 13; font.weight: Font.DemiBold; color: index === aide.choisie ? fenetre.accentEncre : Couleurs.texte }
                    Text { Layout.fillWidth: true; text: modelData.texte; font.pixelSize: 12; color: Couleurs.texte2; elide: Text.ElideRight }
                }
                MouseArea { id: zone; anchors.fill: parent; hoverEnabled: true; onClicked: { aide.choisie = index; aide.accepter() } }
            }
        }
        Text {
            visible: aide.proposer
            text: "Tab ou Entrée pour choisir"
            font.pixelSize: 11
            color: Couleurs.texte3
            Layout.leftMargin: 8
        }

        // La fonction en cours
        Text {
            visible: !aide.proposer && aide.fonction !== null
            Layout.fillWidth: true
            text: aide.signature(aide.fonction, aide.analyse.argument)
            textFormat: Text.StyledText
            font.pixelSize: 14
            color: Couleurs.texte
            wrapMode: Text.Wrap
        }
        Text {
            visible: !aide.proposer && aide.fonction !== null
            Layout.fillWidth: true
            text: aide.fonction ? aide.fonction.texte : ""
            font.pixelSize: 13
            color: Couleurs.texte2
            wrapMode: Text.Wrap
        }
        Text {
            visible: !aide.proposer && aide.fonction !== null
            Layout.fillWidth: true
            text: aide.fonction ? "Exemple : " + aide.fonction.exemple : ""
            font.pixelSize: 12
            color: fenetre.accentEncre
            wrapMode: Text.Wrap
        }
    }
}
