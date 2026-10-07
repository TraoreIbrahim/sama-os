// Les commentaires dans la grille : un petit triangle ocre au coin des cases commentées, et une bulle à côté de la case
// (quand la case est choisie, ou que la souris passe dessus) : qui, quand, quoi ; Modifier, Supprimer. Écrire un
// commentaire ouvre la bulle en écriture (Ctrl+Entrée pour le garder, Échap pour laisser).
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: grilleCom
    required property var moteur
    required property var vueGrille
    readonly property var com: fenetre.commentaires
    anchors.fill: parent

    function bande(c, l) {
        var bc = vueGrille.parLettres[fenetre.tableaux.lettres(c)], bl = vueGrille.parNumero[String(l + 1)]
        return bc && bl && bc.fin > bc.debut && bl.fin > bl.debut ? { x: bc.debut, y: bl.debut, w: bc.fin - bc.debut, h: bl.fin - bl.debut } : null
    }

    // ——— Les marques ———
    Repeater {
        model: grilleCom.com.surFeuille
        delegate: Canvas {
            readonly property var b: grilleCom.bande(modelData.c, modelData.l)
            visible: b !== null
            width: 9
            height: 9
            x: b ? b.x + b.w - width - grilleCom.moteur.vueX : 0
            y: b ? b.y - grilleCom.moteur.vueY + 1 : 0
            onPaint: {
                var g = getContext("2d")
                g.reset()
                g.fillStyle = "#E2A62B"
                g.beginPath(); g.moveTo(0, 0); g.lineTo(width, 0); g.lineTo(width, height); g.closePath(); g.fill()
            }
        }
    }

    // ——— Survol : le commentaire de la case sous la souris ———
    property var survol: null
    // (la souris quitte la feuille : on l'oublie)
    property bool sourisDedans: true
    onSourisDedansChanged: if (!sourisDedans) oublierSurvol.restart()
    property bool surBulle: false
    Timer { id: oublierSurvol; interval: 350; onTriggered: if (!grilleCom.surBulle) grilleCom.survol = null }
    Connections {
        target: grilleCom.moteur
        function onSurvole(x, y) {
            var trouve = null, px = x + grilleCom.moteur.vueX, py = y + grilleCom.moteur.vueY
            for (var i = 0; i < grilleCom.com.surFeuille.length; i++) {
                var k = grilleCom.com.surFeuille[i], b = grilleCom.bande(k.c, k.l)
                if (b && px >= b.x && px < b.x + b.w && py >= b.y && py < b.y + b.h) { trouve = k; break }
            }
            if (trouve) { oublierSurvol.stop(); grilleCom.survol = trouve }
            else if (grilleCom.survol) oublierSurvol.restart()
        }
        function onCurseurTexteVisibleChanged() { if (grilleCom.moteur.curseurTexteVisible) grilleCom.survol = null }
    }

    // ——— Écrire ———
    // { id (ou ""), c, l, texte } pendant qu'on écrit
    property var edition: null
    Connections {
        target: grilleCom.com
        function onEcrire() {
            var k = grilleCom.com.courant
            grilleCom.edition = { id: k ? k.id : "", c: grilleCom.moteur.colonne, l: grilleCom.moteur.ligne, texte: k ? k.texte : "" }
            Qt.callLater(function () { zoneTexte.forceActiveFocus(); zoneTexte.cursorPosition = zoneTexte.length })
        }
    }
    function garder() {
        var e = edition, t = zoneTexte.text.trim()
        edition = null
        if (!e) return
        if (e.id && !t) com.supprimer(e.id)
        else if (e.id) { if (t !== e.texte) com.modifier(e.id, t) }
        else if (t) com.ajouter(t)
        moteur.forceActiveFocus()
    }
    function laisser() { edition = null; moteur.forceActiveFocus() }
    // (la case change : on oublie la case survolée, on laisse ce qu'on écrivait)
    property string caseVue: ""
    Connections {
        target: grilleCom.moteur
        function onCurseurChanged() {
            // (le moteur redit parfois la même case : on ne compte que les vrais changements)
            var ici = grilleCom.moteur.colonne + ":" + grilleCom.moteur.ligne
            if (ici !== grilleCom.caseVue) { grilleCom.caseVue = ici; grilleCom.survol = null }
            if (grilleCom.edition && (grilleCom.moteur.colonne !== grilleCom.edition.c || grilleCom.moteur.ligne !== grilleCom.edition.l)) grilleCom.edition = null
        }
    }

    // ——— La bulle ———
    readonly property var montre: edition ? edition : (survol ? survol : com.courant)
    readonly property bool ecriture: edition !== null
    readonly property bool deLaCase: com.courant !== null && montre !== null && montre.id === com.courant.id
    readonly property var case_: montre ? bande(montre.c, montre.l) : null
    function resoudre(k) {
        com.supprimer(k.id)
        survol = null
        message.montrer("Commentaire retiré · Ctrl+Z pour le retrouver")
        moteur.forceActiveFocus()
    }
    Item {
        id: bulle
        visible: grilleCom.montre !== null && grilleCom.case_ !== null && !grilleCom.moteur.curseurTexteVisible && grilleCom.moteur.objet.width <= 0
        width: 280
        height: grilleCom.ecriture ? redaction.height : lecture.height
        readonly property real droite: grilleCom.case_ ? grilleCom.case_.x + grilleCom.case_.w - grilleCom.moteur.vueX + 8 : 0
        readonly property real gauche: grilleCom.case_ ? grilleCom.case_.x - grilleCom.moteur.vueX - width - 8 : 0
        x: droite + width <= grilleCom.width - 6 ? droite : Math.max(6, gauche)
        y: grilleCom.case_ ? Math.max(6, Math.min(grilleCom.case_.y - grilleCom.moteur.vueY, grilleCom.height - height - 6)) : 0
        // (ombre douce)
        Rectangle { x: -2; y: 3; width: parent.width + 4; height: parent.height + 4; radius: 14; color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.08) }
        HoverHandler { onHoveredChanged: { grilleCom.surBulle = hovered; if (!hovered) oublierSurvol.restart() } }

        CarteCommentaire {
            id: lecture
            visible: !grilleCom.ecriture
            width: parent.width
            height: implicitHeight
            k: grilleCom.edition ? null : grilleCom.montre
            actions: grilleCom.deLaCase
            onModifier: grilleCom.com.demanderEcriture()
            onResoudre: grilleCom.resoudre(grilleCom.montre)
        }

        // Écrire (ou changer) le commentaire de la case
        Rectangle {
            id: redaction
            visible: grilleCom.ecriture
            width: parent.width
            height: colonneRedaction.implicitHeight + 24
            radius: 12
            color: Couleurs.champ
            border.width: 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
            ColumnLayout {
                id: colonneRedaction
                x: 14
                y: 12
                width: parent.width - 28
                spacing: 8
                Text {
                    text: grilleCom.edition && grilleCom.edition.id ? "Modifier le commentaire" : "Commentaire sur " + (grilleCom.edition ? fenetre.tableaux.lettres(grilleCom.edition.c) + (grilleCom.edition.l + 1) : "")
                    font.pixelSize: 13
                    font.weight: Font.DemiBold
                    color: Couleurs.texte
                }
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: Math.max(64, zoneTexte.implicitHeight + 16)
                    radius: 8
                    color: Couleurs.champ
                    border.width: zoneTexte.activeFocus ? 1.5 : 1
                    border.color: zoneTexte.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
                    TextEdit {
                        id: zoneTexte
                        x: 10
                        y: 8
                        width: parent.width - 20
                        text: grilleCom.edition ? grilleCom.edition.texte : ""
                        font.pixelSize: 13
                        color: Couleurs.texte
                        wrapMode: TextEdit.Wrap
                        selectByMouse: true
                        selectionColor: fenetre.accentFond
                        selectedTextColor: Couleurs.texte
                        Keys.onPressed: e => {
                            if ((e.key === Qt.Key_Return || e.key === Qt.Key_Enter) && (e.modifiers & Qt.ControlModifier)) { grilleCom.garder(); e.accepted = true }
                            else if (e.key === Qt.Key_Escape) { grilleCom.laisser(); e.accepted = true }
                        }
                        Text { visible: !parent.text && !parent.preeditText; text: "Une question, un rappel…"; font: parent.font; color: Couleurs.texte3 }
                    }
                }
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 6
                    Text { Layout.fillWidth: true; text: "Ctrl+Entrée"; font.pixelSize: 11; color: Couleurs.texte3 }
                    Outil {
                        text: "Annuler"
                        background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }
                        onClicked: grilleCom.laisser()
                    }
                    QQC2.AbstractButton {
                        id: boutonCommenter
                        implicitHeight: 30
                        leftPadding: 12
                        rightPadding: 12
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        onClicked: grilleCom.garder()
                        background: Rectangle { radius: 8; color: boutonCommenter.hovered ? fenetre.accentEncre : fenetre.accent }
                        contentItem: Text {
                            text: grilleCom.edition && grilleCom.edition.id ? "Enregistrer" : "Commenter"
                            verticalAlignment: Text.AlignVCenter
                            font.pixelSize: 13
                            font.weight: Font.DemiBold
                            color: "#FFFFFF"
                        }
                    }
                }
            }
        }
    }
}
