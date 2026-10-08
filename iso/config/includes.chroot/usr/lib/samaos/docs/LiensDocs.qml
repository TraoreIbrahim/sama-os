// Les liens de Sama Docs. Un clic sur un lien n'ouvre rien : une carte dit où il mène (le site en clair) et prévient
// si le texte affiché annonce un autre site, si le lien est raccourci, pas chiffré ou d'une forme inhabituelle (les
// arnaques passent par là ; les mêmes règles que Sama Sheet, AnalyseLien.js) ; puis Ouvrir, Copier, Modifier,
// Retirer. Le champ d'adresse (la mini-barre, Insertion › Lien) pose un lien sur le texte choisi.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "../sheet"
import "../sheet/AnalyseLien.js" as AnalyseLien

Item {
    id: liens
    required property var doc
    property real decalage: 0
    anchors.fill: parent
    property var lien: null            // { adresse, texte, site, ouvrable, alertes }
    property bool saisie: false
    property var place: null           // { x, y, h } dans la page
    signal annonce(string texte)

    function placeCurseur() {
        var c = doc.curseurTexte
        return { x: c.x - doc.vueX + decalage, y: c.y - doc.vueY, h: c.height }
    }
    function placeSelection() {
        var s = doc.selection
        if (!s || !s.length) return placeCurseur()
        var r = s[s.length - 1]
        return { x: s[0].x - doc.vueX + decalage, y: s[0].y - doc.vueY, h: r.y + r.height - s[0].y }
    }
    // Après un clic simple : le lien sous le curseur, s'il y en a un
    Timer {
        id: lire
        interval: 300
        onTriggered: {
            if (liens.doc.selection.length) return
            fenetre.appeler("SamaDocs.Lien", [], function (ok, v) {
                if (!ok || !v || v.charAt(0) !== "{") { liens.lien = null; return }
                var d = JSON.parse(v), a = AnalyseLien.analyser(d.adresse, d.texte)
                liens.place = liens.placeCurseur()
                liens.lien = { adresse: d.adresse, texte: d.texte, site: a.site, ouvrable: a.ouvrable, alertes: a.alertes }
            })
        }
    }
    Connections {
        target: liens.doc
        function onPointeurAppuye() { lire.stop(); liens.lien = null }
        function onPointeurRelache(x, y) { lire.restart() }
        function onDoubleClique() { lire.stop(); liens.lien = null }
        function onClavierUtilise() { liens.lien = null }
        function onVueChanged() { liens.lien = null; liens.saisie = false }
    }

    // ——— Poser un lien ———
    function ouvrirSaisie(adresse) {
        place = lien ? place : placeSelection()
        lien = null
        champ.text = adresse || ""
        saisie = true
        champ.forceActiveFocus()
        champ.selectAll()
    }
    function poser() {
        var url = AnalyseLien.normaliser(champ.text)
        saisie = false
        if (!url) { doc.forceActiveFocus(); return }
        fenetre.appeler("SamaDocs.PoserLien", [url], function () { liens.doc.forceActiveFocus() })
    }
    function retirer() {
        lien = null
        fenetre.appeler("SamaDocs.PoserLien", [""], function () { liens.doc.forceActiveFocus(); liens.annonce("Lien retiré : le texte reste") })
    }
    TextEdit { id: pressePapiers; visible: false }
    function copier() {
        pressePapiers.text = lien.adresse
        pressePapiers.selectAll()
        pressePapiers.copy()
        annonce("Lien copié")
        lien = null
        doc.forceActiveFocus()
    }

    // (sous le texte ; au-dessus s'il n'y a plus de place)
    function yCarte(h) {
        if (!place) return 0
        return place.y + place.h + h + 10 < height ? place.y + place.h + 6 : Math.max(6, place.y - h - 6)
    }
    component Carte: Rectangle {
        width: 340
        x: liens.place ? Math.max(6, Math.min(liens.place.x - 12, liens.width - width - 6)) : 0
        radius: 12
        color: Couleurs.champ
        border.width: 1
        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
        Rectangle { z: -1; x: -2; y: 3; width: parent.width + 4; height: parent.height + 4; radius: 14; color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.08) }
        MouseArea { anchors.fill: parent; acceptedButtons: Qt.AllButtons }
    }
    component BoutonLien: Outil {
        property bool fort: false
        encre: fort ? fenetre.accentEncre : Couleurs.texte
        background: Rectangle {
            radius: 8
            color: parent.fort ? (parent.hovered ? Qt.rgba(fenetre.accent.r, fenetre.accent.g, fenetre.accent.b, 0.2) : fenetre.accentFond)
                               : (parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : Couleurs.champ)
            border.width: parent.fort ? 0 : 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
        }
    }

    // ——— Où mène le lien ———
    Carte {
        id: carteLien
        visible: liens.lien !== null && !liens.saisie
        height: contenu.implicitHeight + 22
        y: liens.yCarte(height)
        ColumnLayout {
            id: contenu
            x: 12
            y: 11
            width: parent.width - 24
            spacing: 6
            RowLayout {
                Layout.fillWidth: true
                spacing: 8
                Rectangle {
                    Layout.preferredWidth: 28
                    Layout.preferredHeight: 28
                    radius: 8
                    color: fenetre.accentFond
                    Picto { anchors.centerIn: parent; width: 15; height: 15; trace: "M10 14a4 4 0 0 0 6 0l3-3a4 4 0 0 0-6-6l-1 1 M14 10a4 4 0 0 0-6 0l-3 3a4 4 0 0 0 6 6l1-1"; encre: fenetre.accentEncre }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 0
                    Text { Layout.fillWidth: true; text: liens.lien ? "Ce lien mène à " + (liens.lien.site || liens.lien.adresse) : ""; font.pixelSize: 14; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideMiddle }
                    Text { Layout.fillWidth: true; text: liens.lien ? liens.lien.adresse : ""; font.pixelSize: 11; color: Couleurs.texte3; elide: Text.ElideMiddle }
                }
                Outil { Layout.preferredWidth: 26; Layout.preferredHeight: 26; picto: "M7 7l10 10 M17 7L7 17"; aide: "Fermer"; onClicked: { liens.lien = null; liens.doc.forceActiveFocus() } }
            }
            // Les mises en garde
            Repeater {
                model: liens.lien ? liens.lien.alertes : []
                delegate: RowLayout {
                    required property string modelData
                    Layout.fillWidth: true
                    spacing: 6
                    Picto { Layout.alignment: Qt.AlignTop; Layout.topMargin: 1; width: 14; height: 14; trace: "M12 4l9 16H3z M12 10v4 M12 17v.5"; encre: "#A86A12" }
                    Text { Layout.fillWidth: true; text: modelData; font.pixelSize: 12; color: Couleurs.sombre ? "#E9B65A" : "#7A4E0E"; wrapMode: Text.Wrap }
                }
            }
            RowLayout {
                Layout.fillWidth: true
                Layout.topMargin: 2
                spacing: 6
                BoutonLien { text: "Modifier"; onClicked: liens.ouvrirSaisie(liens.lien.adresse) }
                BoutonLien { text: "Retirer"; onClicked: liens.retirer() }
                Item { Layout.fillWidth: true }
                BoutonLien { text: "Copier"; onClicked: liens.copier() }
                BoutonLien {
                    visible: liens.lien !== null && liens.lien.ouvrable
                    fort: true
                    text: liens.lien && liens.lien.alertes.length ? "Ouvrir quand même" : "Ouvrir"
                    onClicked: { Qt.openUrlExternally(liens.lien.adresse); liens.lien = null; liens.doc.forceActiveFocus() }
                }
            }
        }
    }

    // ——— L'adresse d'un lien ———
    Carte {
        visible: liens.saisie
        height: 52
        y: liens.yCarte(height)
        RowLayout {
            anchors.fill: parent
            anchors.margins: 10
            spacing: 8
            QQC2.TextField {
                id: champ
                Layout.fillWidth: true
                Layout.preferredHeight: 32
                placeholderText: "Adresse : wave.com, aya@exemple.ci…"
                placeholderTextColor: Couleurs.texte3
                font.pixelSize: 13
                color: Couleurs.texte
                leftPadding: 10
                background: Rectangle { radius: 8; color: Couleurs.champ; border.width: champ.activeFocus ? 1.5 : 1; border.color: champ.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16) }
                onAccepted: liens.poser()
                Keys.onEscapePressed: { liens.saisie = false; liens.doc.forceActiveFocus() }
            }
            BoutonLien { fort: true; text: "Appliquer"; onClicked: liens.poser() }
        }
    }
}
