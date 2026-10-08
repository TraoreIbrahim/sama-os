// La page de Sama Docs (maquette « Docs · Le document libre ») : le document du moteur au milieu, sur le sable, ses
// pages blanches les unes sous les autres ; Sama dessine le curseur et la sélection (le moteur ne les dessine pas), fait
// défiler à la molette (aussi sur le sable de chaque côté) et suit le curseur quand on écrit en bas de l'écran.
import QtQuick
import Sama.Moteur
import "../reglages"

Item {
    id: page
    readonly property alias doc: document
    readonly property alias marge: laMarge
    readonly property alias liens: lesLiens
    // La marge des commentaires et des suggestions, à droite de la page quand il y en a (Affichage la cache)
    property bool margeOuverte: true
    readonly property bool avecMarge: laMarge.utile && margeOuverte
    readonly property real largeurMarge: 300
    signal annonce(string texte)
    clip: true

    Rectangle { anchors.fill: parent; color: Couleurs.sombre ? "#2A2620" : "#EFE8DD" }

    // (la molette sur le sable de chaque côté fait défiler la page aussi)
    WheelHandler {
        acceptedDevices: PointerDevice.Mouse | PointerDevice.TouchPad
        onWheel: (e) => {
            if (e.modifiers & Qt.ControlModifier) { document.zoom = document.zoom * (e.angleDelta.y > 0 ? 1.1 : 1 / 1.1); return }
            var pas = e.pixelDelta.y !== 0 ? e.pixelDelta.y : e.angleDelta.y / 120 * 60
            document.vueY = Math.max(0, Math.min(page.basMax, document.vueY - pas))
        }
    }
    readonly property real basMax: Math.max(0, document.hauteurDocument - page.height + 40)

    DocumentLO {
        id: document
        // (la largeur du document, au milieu ; le moteur dessine les pages et le sable autour)
        readonly property real largeur: largeurDocument > 0 ? Math.min(page.width, largeurDocument) : page.width
        x: Math.max(0, Math.round((page.width - (page.avecMarge ? page.largeurMarge + 20 : 0) - largeur) / 2))
        Behavior on x { NumberAnimation { duration: 160; easing.type: Easing.OutCubic } }
        width: largeur
        height: page.height
        focus: true
        // (le choix des blocs, ouvert par « / » ou « @ », prend les flèches, Entrée et Échap avant le moteur)
        Keys.onPressed: (e) => { if (lesBlocs.touche(e)) e.accepted = true }
    }

    // ——— Ce que Sama dessine sur le document ———
    Item {
        x: document.x
        width: document.width
        height: page.height
        // Le texte des commentaires (non résolus), surligné d'ocre ; plus fort pour celui qu'on lit
        Repeater {
            model: laMarge.filsVus
            delegate: Repeater {
                id: surlignes
                required property var modelData
                readonly property bool actif: laMarge.active === modelData.cle
                model: document.zoom > 0 && !modelData.resolu ? document.rectangles(modelData.plage) : []
                delegate: Rectangle {
                    required property var modelData
                    x: modelData.x - document.vueX
                    y: modelData.y - document.vueY
                    width: modelData.width
                    height: modelData.height
                    color: Qt.rgba(226 / 255, 166 / 255, 43 / 255, surlignes.actif ? 0.38 : 0.2)
                    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 2; color: "#E2A62B"; visible: surlignes.actif }
                }
            }
        }
        // La sélection
        Repeater {
            model: document.selection
            delegate: Rectangle {
                required property var modelData
                x: modelData.x - document.vueX
                y: modelData.y - document.vueY
                width: modelData.width
                height: modelData.height
                color: Qt.rgba(fenetre.accent.r, fenetre.accent.g, fenetre.accent.b, 0.2)
            }
        }
        // Le curseur
        Rectangle {
            id: caret
            visible: document.curseurTexteVisible && document.curseurTexte.height > 0 && clignote && document.activeFocus
            property bool clignote: true
            x: document.curseurTexte.x - document.vueX
            y: document.curseurTexte.y - document.vueY
            width: 1.5
            height: document.curseurTexte.height
            color: Couleurs.sombre ? "#F1EBE1" : "#1F1C18"
            Timer { running: document.curseurTexteVisible; interval: 530; repeat: true; onTriggered: caret.clignote = !caret.clignote }
        }
    }

    // ——— Suivre le curseur : quand il bouge hors de la vue (on écrit en bas, on descend avec les flèches) ———
    Connections {
        target: document
        function onCurseurTexteChanged() {
            var c = document.curseurTexte
            if (c.height <= 0 || !document.curseurTexteVisible) return
            caret.clignote = true
            if (c.y < document.vueY + 8) document.vueY = Math.max(0, c.y - 60)
            else if (c.y + c.height > document.vueY + page.height - 8) document.vueY = c.y + c.height - page.height + 60
        }
    }

    // ——— Autour du texte : la mini-barre de la sélection, les liens, le choix des blocs ———
    MiniBarreDocs { doc: document; decalage: document.x; z: 5; onLien: lesLiens.ouvrirSaisie("") }
    LiensDocs { id: lesLiens; doc: document; decalage: document.x; z: 6; onAnnonce: t => page.annonce(t) }
    BlocsDocs { id: lesBlocs; doc: document; decalage: document.x; z: 7 }

    // ——— La marge ———
    MargeDocs {
        id: laMarge
        doc: document
        visible: page.avecMarge
        x: Math.min(document.x + document.width + 12, page.width - page.largeurMarge - 10)
        width: page.largeurMarge
        height: page.height
        onAnnonce: t => page.annonce(t)
    }

    // ——— La barre de défilement ———
    Rectangle {
        id: barre
        readonly property real total: Math.max(document.hauteurDocument, page.height)
        visible: document.hauteurDocument > page.height + 2
        anchors.right: parent.right
        anchors.rightMargin: 2
        y: (page.height - height) * (page.basMax > 0 ? document.vueY / page.basMax : 0)
        width: survolBarre.containsMouse || survolBarre.pressed ? 8 : 5
        height: Math.max(36, page.height * page.height / total)
        radius: width / 2
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, survolBarre.containsMouse || survolBarre.pressed ? 0.35 : 0.2)
        Behavior on width { NumberAnimation { duration: 100 } }
        MouseArea {
            id: survolBarre
            anchors.fill: parent
            anchors.margins: -4
            hoverEnabled: true
            property real depart: 0
            property real vueDepart: 0
            onPressed: (m) => { depart = mapToItem(page, m.x, m.y).y; vueDepart = document.vueY }
            onPositionChanged: (m) => {
                if (!pressed) return
                var dy = mapToItem(page, m.x, m.y).y - depart
                var course = page.height - barre.height
                if (course > 0) document.vueY = Math.max(0, Math.min(page.basMax, vueDepart + dy * page.basMax / course))
            }
        }
    }
}
