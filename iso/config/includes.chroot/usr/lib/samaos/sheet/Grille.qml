// Grille de Sama Sheet : le moteur dessine les cellules (et les graphiques) ; Sama dessine les en-têtes (A, B, C… et
// 1, 2, 3…), le curseur de cellule, la sélection, le curseur de saisie, le cadre du graphique choisi et les barres de
// défilement. Un clic sur un en-tête choisit la colonne ou la ligne entière.
import QtQuick
import QtQuick.Layouts
import Sama.Moteur
import "../reglages"

Item {
    id: grille
    property alias doc: document
    readonly property int largeurEntetes: 44
    readonly property int hauteurEntetes: 24
    readonly property color fondEntete: Couleurs.sombre ? "#2A2F42" : "#F4F1EC"
    readonly property color fondEnteteActif: Couleurs.sombre ? Qt.rgba(163 / 255, 214 / 255, 193 / 255, 0.22) : "#DDEBE3"
    readonly property color trait: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)

    // Colonnes et lignes de la sélection, d'après son adresse (« F15 », « B2:D3 », « B:B », « 3:3 »)
    function numeroColonne(l) { var n = 0; for (var i = 0; i < l.length; i++) n = n * 26 + (l.charCodeAt(i) - 64); return n }
    readonly property var plage: {
        var a = document.adresse.replace(/\$/g, "").split(":")
        function lire(s) { var m = /^([A-Z]*)(\d*)$/.exec(s || ""); return m ? { c: m[1] ? numeroColonne(m[1]) : 0, l: m[2] ? Number(m[2]) : 0 } : { c: 0, l: 0 } }
        var d = lire(a[0]), f = a.length > 1 ? lire(a[1]) : d
        return { c1: Math.min(d.c, f.c), c2: Math.max(d.c, f.c), l1: Math.min(d.l, f.l), l2: Math.max(d.l, f.l),
                 colonnesEntieres: d.l === 0, lignesEntieres: d.c === 0 }
    }
    function colonneChoisie(lettres) {
        if (!document.adresse) return false
        var n = numeroColonne(lettres)
        return plage.lignesEntieres || (n >= plage.c1 && n <= plage.c2)
    }
    function ligneChoisie(texte) {
        if (!document.adresse) return false
        var n = Number(texte)
        return plage.colonnesEntieres || (n >= plage.l1 && n <= plage.l2)
    }

    // En-têtes renvoyés par le moteur : [{ text, size }], « size » = fin de la colonne (pixels du document)
    function bandes(liste) {
        var res = []
        if (!liste || liste.length < 2) return res
        for (var k = 1; k < liste.length; k++) {
            var debut = Number(liste[k - 1].size), fin = Number(liste[k].size)
            res.push({ texte: String(liste[k].text), debut: debut, fin: fin })
        }
        return res
    }
    readonly property var colonnes: bandes(document.entetes.columns)
    readonly property var lignes: bandes(document.entetes.rows)

    // Coin
    Rectangle {
        width: grille.largeurEntetes
        height: grille.hauteurEntetes
        color: grille.fondEntete
        Rectangle { anchors.right: parent.right; width: 0.5; height: parent.height; color: grille.trait }
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: grille.trait }
    }

    // En-têtes de colonnes
    Item {
        id: entetesColonnes
        x: grille.largeurEntetes
        width: parent.width - x
        height: grille.hauteurEntetes
        clip: true
        Rectangle { anchors.fill: parent; color: grille.fondEntete }
        Repeater {
            model: grille.colonnes
            delegate: MouseArea {
                readonly property bool courante: grille.colonneChoisie(modelData.texte)
                x: modelData.debut - document.vueX
                width: modelData.fin - modelData.debut
                height: parent.height
                visible: width > 0
                onClicked: { document.allerA(modelData.texte + ":" + modelData.texte); document.forceActiveFocus() }
                Rectangle { anchors.fill: parent; color: parent.courante ? grille.fondEnteteActif : "transparent" }
                Text {
                    anchors.centerIn: parent
                    text: modelData.texte
                    font.pixelSize: 11
                    font.weight: parent.courante ? Font.DemiBold : Font.Normal
                    color: parent.courante ? fenetre.vertEncre : Couleurs.texte2
                }
                Rectangle { anchors.right: parent.right; width: 0.5; height: parent.height; color: grille.trait }
            }
        }
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: grille.trait }
    }

    // En-têtes de lignes
    Item {
        id: entetesLignes
        y: grille.hauteurEntetes
        width: grille.largeurEntetes
        height: parent.height - y
        clip: true
        Rectangle { anchors.fill: parent; color: grille.fondEntete }
        Repeater {
            model: grille.lignes
            delegate: MouseArea {
                readonly property bool courante: grille.ligneChoisie(modelData.texte)
                y: modelData.debut - document.vueY
                height: modelData.fin - modelData.debut
                width: parent.width
                visible: height > 0
                onClicked: { document.allerA(modelData.texte + ":" + modelData.texte); document.forceActiveFocus() }
                Rectangle { anchors.fill: parent; color: parent.courante ? grille.fondEnteteActif : "transparent" }
                Text {
                    anchors.centerIn: parent
                    text: modelData.texte
                    font.pixelSize: 11
                    font.weight: parent.courante ? Font.DemiBold : Font.Normal
                    color: parent.courante ? fenetre.vertEncre : Couleurs.texte2
                }
                Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: grille.trait }
            }
        }
        Rectangle { anchors.right: parent.right; width: 0.5; height: parent.height; color: grille.trait }
    }

    // Les cellules (fond blanc sous les tuiles pas encore dessinées)
    Rectangle {
        id: feuille
        x: grille.largeurEntetes
        y: grille.hauteurEntetes
        width: parent.width - x - 10
        height: parent.height - y - 10
        color: "#FFFFFF"
        clip: true

        DocumentLO {
            id: document
            anchors.fill: parent
            focus: true
        }

        // Sélection (plage de cellules)
        Repeater {
            model: document.selection
            delegate: Rectangle {
                x: modelData.x - document.vueX
                y: modelData.y - document.vueY
                width: modelData.width
                height: modelData.height
                color: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.1)
                border.width: 1
                border.color: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.55)
            }
        }
        // Curseur de cellule (caché quand un graphique est choisi)
        Rectangle {
            visible: document.curseur.width > 0 && document.objet.width <= 0
            x: document.curseur.x - document.vueX - 1
            y: document.curseur.y - document.vueY - 1
            width: document.curseur.width + 2
            height: document.curseur.height + 2
            color: "transparent"
            border.width: 2
            border.color: fenetre.vert
            // Poignée de recopie
            Rectangle {
                width: 7; height: 7
                x: parent.width - 4; y: parent.height - 4
                color: fenetre.vert
                border.width: 1
                border.color: "#FFFFFF"
            }
        }
        // Curseur de saisie (pendant qu'on écrit dans une cellule)
        Rectangle {
            id: caret
            visible: document.curseurTexteVisible && document.curseurTexte.height > 0 && clignote
            property bool clignote: true
            x: document.curseurTexte.x - document.vueX
            y: document.curseurTexte.y - document.vueY
            width: 1.5
            height: document.curseurTexte.height
            color: "#1F1C18"
            Timer { running: document.curseurTexteVisible; interval: 530; repeat: true; onTriggered: caret.clignote = !caret.clignote }
        }

        // Graphique choisi : cadre et poignées (on le déplace en le tirant, on l'agrandit par une poignée ; le moteur le
        // redessine en suivant la souris)
        Item {
            id: cadreObjet
            visible: document.objet.width > 0 && !document.objetActif
            x: document.objet.x - document.vueX
            y: document.objet.y - document.vueY
            width: document.objet.width
            height: document.objet.height
            Rectangle { anchors.fill: parent; anchors.margins: -1; color: "transparent"; border.width: 1.5; border.color: fenetre.vert }
            Repeater {
                model: 8
                delegate: Rectangle {
                    width: 9; height: 9; radius: 4.5
                    x: [0, 0.5, 1, 1, 1, 0.5, 0, 0][index] * cadreObjet.width - 4.5
                    y: [0, 0, 0, 0.5, 1, 1, 1, 0.5][index] * cadreObjet.height - 4.5
                    color: "#FFFFFF"
                    border.width: 1.5
                    border.color: fenetre.vert
                }
            }
        }
        // Actions du graphique choisi, au-dessus (ou en dessous s'il touche le haut)
        Rectangle {
            id: actionsObjet
            visible: cadreObjet.visible && !document.objetTenu
            readonly property bool dessous: cadreObjet.y < height + 10
            x: Math.max(6, Math.min(cadreObjet.x + cadreObjet.width - width, feuille.width - width - 6))
            y: dessous ? cadreObjet.y + cadreObjet.height + 8 : cadreObjet.y - height - 8
            width: rangeeActions.implicitWidth + 8
            height: 34
            radius: 10
            color: Couleurs.champ
            border.width: 0.5
            border.color: Couleurs.bord
            RowLayout {
                id: rangeeActions
                anchors.centerIn: parent
                spacing: 2
                Text { text: "Graphique"; font.pixelSize: 12; font.weight: Font.DemiBold; color: fenetre.vertEncre; Layout.leftMargin: 8; Layout.rightMargin: 4 }
                Outil {
                    Layout.preferredHeight: 26
                    text: "Supprimer"
                    picto: "M5 7h14 M10 7V5h4v2 M7 7l1 12h8l1-12"
                    encre: "#A3322A"
                    aide: "Supprimer le graphique (Suppr)"
                    onClicked: { document.touche(1286); document.forceActiveFocus() }
                }
            }
        }
    }

    // Barres de défilement (la feuille s'agrandit quand on va au-delà : on peut toujours aller plus loin)
    component Barre: Item {
        id: barre
        property bool verticale: true
        property real vue: 0
        property real etendue: 1
        property real total: 1
        signal deplacee(real valeur)
        readonly property real longueur: verticale ? height : width
        readonly property real taillePoignee: Math.max(28, longueur * etendue / Math.max(total, etendue))
        readonly property real position: (longueur - taillePoignee) * Math.min(1, vue / Math.max(1, total - etendue))
        Rectangle {
            x: barre.verticale ? 2 : barre.position
            y: barre.verticale ? barre.position : 2
            width: barre.verticale ? 6 : barre.taillePoignee
            height: barre.verticale ? barre.taillePoignee : 6
            radius: 3
            color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, zone.pressed || zone.containsMouse ? 0.35 : 0.18)
            MouseArea {
                id: zone
                anchors.fill: parent
                anchors.margins: -3
                hoverEnabled: true
                property real depart: 0
                property real vueDepart: 0
                onPressed: m => { depart = barre.verticale ? mapToItem(barre, m.x, m.y).y : mapToItem(barre, m.x, m.y).x; vueDepart = barre.vue }
                onPositionChanged: m => {
                    if (!pressed) return
                    var p = barre.verticale ? mapToItem(barre, m.x, m.y).y : mapToItem(barre, m.x, m.y).x
                    var course = Math.max(1, barre.longueur - barre.taillePoignee)
                    barre.deplacee(Math.max(0, vueDepart + (p - depart) / course * Math.max(1, barre.total - barre.etendue)))
                }
            }
        }
    }
    Barre {
        x: parent.width - 10
        y: grille.hauteurEntetes
        width: 10
        height: feuille.height
        vue: document.vueY
        etendue: feuille.height
        total: Math.max(document.hauteurDocument, document.vueY + feuille.height) + feuille.height
        onDeplacee: v => document.vueY = v
    }
    Barre {
        verticale: false
        x: grille.largeurEntetes
        y: parent.height - 10
        width: feuille.width
        height: 10
        vue: document.vueX
        etendue: feuille.width
        total: Math.max(document.largeurDocument, document.vueX + feuille.width) + feuille.width
        onDeplacee: v => document.vueX = v
    }

    // Le curseur reste visible quand on se déplace au clavier
    Connections {
        target: document
        function onCurseurChanged() {
            var c = document.curseur
            if (c.width <= 0) return
            if (c.x < document.vueX) document.vueX = Math.max(0, c.x - 20)
            else if (c.x + c.width > document.vueX + feuille.width) document.vueX = c.x + c.width - feuille.width + 20
            if (c.y < document.vueY) document.vueY = Math.max(0, c.y - 10)
            else if (c.y + c.height > document.vueY + feuille.height) document.vueY = c.y + c.height - feuille.height + 10
        }
    }
}
