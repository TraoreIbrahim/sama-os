// La mini-barre qui suit la sélection (maquette « Sheet · Un tableau dans la grille », comme celle de Word) : après
// un choix de cases à la souris, les gestes les plus courants au-dessus d'elles. Gras, remplissage, bordures,
// francs CFA, pourcentage, graphique, commentaire. Elle s'efface quand on tape, qu'on défile, qu'on écrit dans une
// case, et à mesure que la souris s'en éloigne.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: mini
    required property var moteur
    readonly property var actions: fenetre.actions
    property bool ouverte: false
    // Proximité de la souris : 1 tout près, 0 loin (la barre est alors fermée)
    property real proximite: 1
    property point souris: Qt.point(0, 0)
    readonly property bool voletOuvert: nuancier.opened || choixBordures.opened || choixGraphique.opened
    anchors.fill: parent
    visible: opacity > 0
    // (une autre carte s'ouvre, l'Analyse rapide : la barre s'efface)
    property bool masquee: false
    onMasqueeChanged: if (masquee) fermer()

    function fermer() { attente.stop(); ouverte = false }
    // (après un clic : le temps qu'un double-clic, qui ouvre la case à l'écriture, ait lieu)
    Timer { id: attente; interval: 380; onTriggered: mini.montrer() }
    function montrer() {
        if (moteur.curseurTexteVisible || moteur.objet.width > 0) return
        var r = moteur.zoneSelection.width > 0 ? moteur.zoneSelection : moteur.curseur
        if (r.width <= 0) return
        var rx = r.x - moteur.vueX, ry = r.y - moteur.vueY
        // (une grande sélection : la barre se pose près de la souris, pas à son bord)
        var grande = r.height > 120
        var haut = grande ? souris.y - 14 : ry
        var bas = grande ? souris.y + 14 : ry + r.height
        var cx = r.width > 320 ? souris.x : rx + r.width / 2
        barre.x = Math.max(6, Math.min(cx - barre.width / 2, width - barre.width - 6))
        var y = haut - barre.height - 8
        // (en dessous : sous le bouton de l'Analyse rapide, au coin de la sélection)
        barre.y = y >= 6 ? y : Math.min(bas + 20, height - barre.height - 6)
        proximite = 1
        depart = distance(souris.x, souris.y)
        ouverte = true
    }
    // Distance de la souris à la barre ; elle ne s'efface qu'à partir de la distance où la souris était quand la barre
    // est apparue (ou de la plus courte depuis) : au bout d'une grande sélection, elle reste nette
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
        target: mini.moteur
        function onPointeurAppuye() { mini.fermer() }
        function onPointeurRelache(x, y) { mini.souris = Qt.point(x, y); attente.restart() }
        function onDoubleClique() { mini.fermer() }
        function onClavierUtilise() { mini.fermer() }
        function onVueChanged() { if (!mini.voletOuvert) mini.fermer() }
        function onZoomChanged() { mini.fermer() }
        function onCurseurTexteVisibleChanged() { if (mini.moteur.curseurTexteVisible) mini.fermer() }
        function onObjetChanged() { if (mini.moteur.objet.width > 0) mini.fermer() }
        function onSurvole(x, y) { mini.approcher(x, y) }
    }
    // (on écrit un commentaire : la barre s'efface)
    Connections {
        target: fenetre.commentaires
        function onEcrire() { mini.fermer() }
    }
    opacity: ouverte ? (voletOuvert ? 1 : Math.max(0.15, proximite)) : 0
    Behavior on opacity { NumberAnimation { duration: 120 } }

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
        // (la souris sur la barre : elle reste nette)
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
                actif: fenetre.actif(".uno:Bold")
                aide: "Gras (Ctrl+B)"
                onClicked: mini.actions.lancer("gras")
            }
            Outil {
                id: remplissage
                implicitWidth: 30
                picto: "M5 11.5L10.5 6l5.5 5.5l-5.5 5.5z M5 11.5h11 M18.5 13.5c.7 1 1.2 1.8 1.2 2.3a1.2 1.2 0 0 1-2.4 0c0-.5.5-1.3 1.2-2.3z M8.5 4l2 2"
                aide: "Couleur de remplissage"
                ouvert: nuancier.opened
                onClicked: nuancier.open()
                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    y: parent.height - 7
                    width: 14; height: 3; radius: 1.5
                    color: mini.actions.couleurCellule(".uno:BackgroundColor", "transparent")
                    border.width: 0.5
                    border.color: Couleurs.bord
                }
                Nuancier {
                    id: nuancier
                    titre: "Remplissage"
                    rangees: barreOutils.couleursFond
                    aucune: "Aucun remplissage"
                    aucunePicto: "M4 4h16v16H4z M4 20L20 4"
                    onChoisie: c => mini.actions.colorerFond(c)
                }
            }
            Outil {
                implicitWidth: 30
                picto: "M4 4h16v16H4z M4 12h16 M12 4v16"
                aide: "Bordures"
                ouvert: choixBordures.opened
                onClicked: choixBordures.open()
                ChoixBordures { id: choixBordures; onChoisie: g => mini.actions.bordures(g) }
            }
            Trait {}
            Outil {
                text: "F CFA"
                police.pixelSize: 12
                police.bold: true
                implicitWidth: 50
                aide: "Montant en francs CFA"
                onClicked: mini.actions.lancer("fcfa")
            }
            Outil {
                implicitWidth: 30
                text: "%"
                aide: "Pourcentage"
                onClicked: mini.actions.lancer("pourcentage")
            }
            Trait {}
            Outil {
                implicitWidth: 30
                picto: "M4 20V11 M10 20V5 M16 20v-6 M3 20h18"
                encre: fenetre.accent
                aide: "Graphique, à partir des cases choisies"
                ouvert: choixGraphique.opened
                onClicked: choixGraphique.open()
                ChoixGraphique { id: choixGraphique; onChoisi: type => { mini.fermer(); mini.moteur.insererGraphique(type) } }
            }
            Outil {
                implicitWidth: 30
                picto: "M4 5h16v11H9l-5 4z M12 8v5 M9.5 10.5h5"
                aide: "Commentaire (Ctrl+Alt+M)"
                onClicked: { mini.fermer(); fenetre.commentaires.demanderEcriture() }
            }
        }
    }
}
