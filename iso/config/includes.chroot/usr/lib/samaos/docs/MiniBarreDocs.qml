// La mini-barre de la sélection (maquette « Docs · Le document libre », comme celle de Word) : après un choix de texte
// à la souris ou un double-clic sur un mot, au-dessus de lui : gras, italique, surligner, lien, style, commenter. Elle
// s'efface quand on tape, qu'on défile, et à mesure que la souris s'en éloigne.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "../sheet"

Item {
    id: mini
    required property var doc
    property real decalage: 0          // (la place du document dans la page)
    signal lien()
    readonly property var actions: fenetre.actions
    property bool ouverte: false
    // Proximité de la souris : 1 tout près, 0 loin (la barre est alors fermée)
    property real proximite: 1
    property point souris: Qt.point(0, 0)
    readonly property bool voletOuvert: menuStyles.opened
    anchors.fill: parent
    visible: opacity > 0
    opacity: ouverte ? (voletOuvert ? 1 : Math.max(0.15, proximite)) : 0
    Behavior on opacity { NumberAnimation { duration: 120 } }

    function fermer() { attente.stop(); ouverte = false }
    // (après un clic : le temps qu'un double-clic ait lieu)
    Timer { id: attente; interval: 380; onTriggered: mini.montrer() }
    function montrer() {
        var s = doc.selection
        if (!s || !s.length) return
        var x1 = 1e9, y1 = 1e9, x2 = -1e9, y2 = -1e9
        s.forEach(function (r) { x1 = Math.min(x1, r.x); y1 = Math.min(y1, r.y); x2 = Math.max(x2, r.x + r.width); y2 = Math.max(y2, r.y + r.height) })
        if (x2 - x1 < 2) return
        var ox = decalage - doc.vueX, oy = -doc.vueY
        // (une grande sélection : la barre se pose près de la souris, pas à son bord)
        var grande = y2 - y1 > 120
        var haut = grande ? souris.y - 14 : y1 + oy
        var bas = grande ? souris.y + 14 : y2 + oy
        var cx = grande || x2 - x1 > 360 ? souris.x : (x1 + x2) / 2 + ox
        barre.x = Math.max(6, Math.min(cx - barre.width / 2, width - barre.width - 6))
        var y = haut - barre.height - 10
        barre.y = y >= 6 ? y : Math.min(bas + 10, height - barre.height - 6)
        proximite = 1
        depart = distance(souris.x, souris.y)
        ouverte = true
    }
    // Distance de la souris à la barre ; elle ne s'efface qu'au-delà de celle où la souris était à son apparition
    property real depart: 0
    function distance(x, y) {
        var dx = Math.max(barre.x - x, 0, x - (barre.x + barre.width))
        var dy = Math.max(barre.y - y, 0, y - (barre.y + barre.height))
        return Math.sqrt(dx * dx + dy * dy)
    }
    function approcher(x, y) {
        if (!ouverte || voletOuvert) return
        var d = distance(x, y)
        if (d < depart) depart = d
        var e = d - depart
        proximite = e <= 30 ? 1 : e >= 150 ? 0 : 1 - (e - 30) / 120
        if (proximite <= 0) ouverte = false
    }
    Connections {
        target: mini.doc
        function onPointeurAppuye() { mini.fermer() }
        function onPointeurRelache(x, y) { mini.souris = Qt.point(x + mini.decalage, y); attente.restart() }
        function onDoubleClique() { attente.restart() }
        function onClavierUtilise() { mini.fermer() }
        function onVueChanged() { if (!mini.voletOuvert) mini.fermer() }
        function onZoomChanged() { mini.fermer() }
        function onSurvole(x, y) { mini.approcher(x + mini.decalage, y) }
    }

    readonly property string fond: String(doc.etats[".uno:CharBackColor"] || "")
    readonly property bool surligne: fond !== "" && fond !== "-1" && fond !== "16777215"
    component Trait: Rectangle { Layout.preferredWidth: 1; Layout.preferredHeight: 18; Layout.leftMargin: 3; Layout.rightMargin: 3; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }

    Rectangle {
        id: barre
        width: rangee.implicitWidth + 12
        height: 40
        radius: 12
        color: Couleurs.champ
        border.width: 1
        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
        // (ombre douce, comme dans la maquette)
        Rectangle { z: -1; anchors.fill: parent; anchors.topMargin: 6; anchors.margins: -3; radius: 15; color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.06) }
        Rectangle { z: -1; anchors.fill: parent; anchors.topMargin: 3; anchors.margins: -1; radius: 13; color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.08) }
        HoverHandler { onHoveredChanged: if (hovered) mini.proximite = 1 }
        RowLayout {
            id: rangee
            anchors.centerIn: parent
            spacing: 2
            Outil {
                implicitWidth: 30
                text: "B"
                police.pixelSize: 14
                police.bold: true
                actif: mini.doc.etats[".uno:Bold"] === "true"
                aide: "Gras (Ctrl+B)"
                onClicked: mini.actions.lancer("gras")
            }
            Outil {
                implicitWidth: 30
                text: "I"
                police.pixelSize: 15
                police.italic: true
                police.family: "Noto Serif"
                actif: mini.doc.etats[".uno:Italic"] === "true"
                aide: "Italique (Ctrl+I)"
                onClicked: mini.actions.lancer("italique")
            }
            Outil {
                implicitWidth: 30
                picto: "M9 15l-3 5h6l1-2 M9 15l8-8 3 3-8 8z"
                actif: mini.surligne
                aide: mini.surligne ? "Retirer le surlignage" : "Surligner"
                onClicked: mini.actions.surligner(mini.surligne ? "" : "#F2D27A")
                Rectangle { anchors.horizontalCenter: parent.horizontalCenter; y: parent.height - 7; width: 14; height: 3; radius: 1.5; color: "#E2A62B" }
            }
            Outil {
                implicitWidth: 30
                picto: "M10 14a4 4 0 0 0 6 0l3-3a4 4 0 0 0-6-6l-1 1 M14 10a4 4 0 0 0-6 0l-3 3a4 4 0 0 0 6 6l1-1"
                aide: "Lien"
                onClicked: { mini.fermer(); mini.lien() }
            }
            Trait {}
            Outil {
                id: style
                text: "Style ▾"
                police.pixelSize: 13
                aide: "Style du paragraphe"
                ouvert: menuStyles.opened
                onClicked: menuStyles.popup(style, 0, style.height + 6)
                MenuSama {
                    id: menuStyles
                    largeur: 220
                    ElementMenu { text: "Texte normal"; coche: mini.actions.styleActuel() === text; onTriggered: { mini.actions.appliquerStyle("Standard"); mini.fermer() } }
                    ElementMenu { text: "Titre"; coche: mini.actions.styleActuel() === text; onTriggered: { mini.actions.appliquerStyle("Title"); mini.fermer() } }
                    ElementMenu { text: "Sous-titre"; coche: mini.actions.styleActuel() === text; onTriggered: { mini.actions.appliquerStyle("Subtitle"); mini.fermer() } }
                    ElementMenu { text: "Titre 1"; coche: mini.actions.styleActuel() === text; onTriggered: { mini.actions.appliquerStyle("Heading 1"); mini.fermer() } }
                    ElementMenu { text: "Titre 2"; coche: mini.actions.styleActuel() === text; onTriggered: { mini.actions.appliquerStyle("Heading 2"); mini.fermer() } }
                    ElementMenu { text: "Titre 3"; coche: mini.actions.styleActuel() === text; onTriggered: { mini.actions.appliquerStyle("Heading 3"); mini.fermer() } }
                    ElementMenu { text: "Citation"; coche: mini.actions.styleActuel() === text; onTriggered: { mini.actions.appliquerStyle("Quotations"); mini.fermer() } }
                }
            }
            Trait {}
            Outil {
                text: "Commenter"
                picto: "M4 5h16v11H9l-5 4z M12 8v5 M9.5 10.5h5"
                police.pixelSize: 13
                aide: "Commentaire (Ctrl+Alt+M)"
                onClicked: { mini.fermer(); fenetre.commenter() }
            }
        }
    }
}
