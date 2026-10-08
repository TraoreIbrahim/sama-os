// Ce que les fichiers Excel apportent dans leurs cases, montré par Sama.
// Les listes de choix (validation) : un petit ▾ au bord de la case courante ; il ouvre la liste, la valeur actuelle
// cochée. Alt+Bas l'ouvre aussi, et, sans liste, propose les textes déjà écrits dans la colonne (comme Excel).
// Les liens : un clic sur un lien n'ouvre rien tout de suite ; une carte dit où il mène (le site en clair), prévient
// si le texte affiché annonce un autre site, si le lien est raccourci, pas chiffré ou d'une forme inhabituelle (les
// arnaques passent par là), puis Ouvrir ou Copier le lien.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "Fonctions.js" as Fonctions
import "AnalyseLien.js" as AnalyseLien

Item {
    id: couche
    required property var moteur
    required property var vueGrille
    readonly property var tableaux: fenetre.tableaux
    anchors.fill: parent

    function bande(c, l) {
        var bc = vueGrille.parLettres[tableaux.lettres(c)], bl = vueGrille.parNumero[String(l + 1)]
        return bc && bl && bc.fin > bc.debut && bl.fin > bl.debut ? { x: bc.debut, y: bl.debut, w: bc.fin - bc.debut, h: bl.fin - bl.debut } : null
    }

    // ——— Les listes de choix ———
    property var bouton: null          // { c, l } : la case courante a une liste
    // (la dernière case qui a montré sa liste : le moteur cache le ▾ pendant qu'on écrit ; on s'en souvient jusqu'à ce
    // qu'on change de case — pour dire « pas dans la liste » quand il refuse une valeur)
    property var caseListe: null       // { f, c, l }
    Connections {
        target: couche.moteur
        function onBoutonListe(c, l, visible) {
            couche.bouton = visible ? { c: c, l: l } : null
            if (visible) couche.caseListe = { f: couche.moteur.partie, c: c, l: l }
        }
        function onCurseurChanged() {
            var cl = couche.caseListe, m = couche.moteur
            if (cl && m.colonne >= 0 && (m.colonne !== cl.c || m.ligne !== cl.l || m.partie !== cl.f)) couche.caseListe = null
            if (couche.lien && (couche.moteur.colonne !== couche.lien.c || couche.moteur.ligne !== couche.lien.l)) couche.lien = null
        }
        function onLienClique(adresse, c, l) { couche.montrerLien(adresse, c, l) }
    }
    readonly property var caseBouton: bouton ? bande(bouton.c, bouton.l) : null
    QQC2.AbstractButton {
        id: fleche
        visible: couche.caseBouton !== null && !couche.moteur.curseurTexteVisible
        readonly property real cote: couche.caseBouton ? Math.max(14, Math.min(20, couche.caseBouton.h - 6)) : 18
        x: couche.caseBouton ? couche.caseBouton.x + couche.caseBouton.w - couche.moteur.vueX - cote - 3 : 0
        y: couche.caseBouton ? couche.caseBouton.y - couche.moteur.vueY + (couche.caseBouton.h - cote) / 2 : 0
        width: cote
        height: cote
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        QQC2.ToolTip.visible: hovered
        QQC2.ToolTip.delay: 500
        QQC2.ToolTip.text: "Choisir dans la liste (Alt+Bas)"
        onClicked: couche.ouvrirListe()
        background: Rectangle {
            radius: 5
            color: fleche.hovered || liste.opened ? fenetre.accentFond : Couleurs.champ
            border.width: 1
            border.color: fleche.hovered || liste.opened ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.25)
        }
        contentItem: Item {
            Picto { anchors.centerIn: parent; width: 12; height: 12; trace: "M6 9l6 6 6-6"; encre: fenetre.accentEncre; trait: 2 }
        }
    }

    // La liste : celle de la case, ou les textes de la colonne
    readonly property bool listeOuverte: liste.opened
    property var choix: []
    property string valeur: ""
    property bool validation: false
    property string filtre: ""
    readonly property var choixVus: filtre ? choix.filter(function (x) { return Fonctions.normaliser(x).indexOf(Fonctions.normaliser(filtre)) >= 0 }) : choix
    function ouvrirListe() {
        var c = moteur.colonne, l = moteur.ligne, f = moteur.partie
        tableaux.appeler("SamaListes.Choix", [String(f), String(c), String(l)], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{") return
            var d
            try { d = JSON.parse(v) } catch (e) { return }
            if (!d.choix.length) { message.montrer("Pas de choix à proposer pour cette case"); return }
            couche.choix = d.trie ? d.choix.slice().sort(function (a, b) { return a.localeCompare(b, "fr") }) : d.choix
            couche.valeur = d.valeur
            couche.validation = d.validation
            couche.filtre = ""
            var b = couche.bande(c, l)
            liste.x = b ? Math.min(b.x - couche.moteur.vueX, couche.width - liste.width - 6) : 40
            liste.y = b ? b.y - couche.moteur.vueY + b.h + 2 : 40
            liste.minimumLargeur = b ? b.w : 0
            liste.open()
        })
    }
    function choisir(v) {
        liste.close()
        if (v !== valeur) moteur.saisir(v)
        moteur.forceActiveFocus()
    }
    QQC2.Popup {
        id: liste
        property real minimumLargeur: 0
        width: Math.max(220, Math.min(360, minimumLargeur))
        height: Math.min(colonneListe.implicitHeight + 12, couche.height - y - 8)
        padding: 6
        focus: true
        onOpened: { if (couche.choix.length > 8) filtreListe.forceActiveFocus(); else vueListe.forceActiveFocus(); vueListe.currentIndex = Math.max(0, couche.choixVus.indexOf(couche.valeur)) }
        onClosed: couche.moteur.forceActiveFocus()
        background: Item {
            Rectangle { anchors.fill: parent; anchors.topMargin: 4; anchors.margins: -2; radius: 14; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.06) }
            Rectangle { anchors.fill: parent; radius: 12; color: Couleurs.champ; border.width: 0.5; border.color: Couleurs.bord }
        }
        contentItem: ColumnLayout {
            id: colonneListe
            spacing: 4
            Text {
                Layout.leftMargin: 8
                Layout.topMargin: 2
                text: couche.validation ? "Choisir dans la liste" : "Déjà écrit dans la colonne"
                font.pixelSize: 11
                font.weight: Font.DemiBold
                color: Couleurs.texte3
            }
            Rectangle {
                visible: couche.choix.length > 8
                Layout.fillWidth: true
                Layout.preferredHeight: 30
                radius: 8
                color: Couleurs.champ
                border.width: filtreListe.activeFocus ? 1.5 : 1
                border.color: filtreListe.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                TextInput {
                    id: filtreListe
                    anchors.fill: parent
                    anchors.leftMargin: 9
                    anchors.rightMargin: 9
                    verticalAlignment: TextInput.AlignVCenter
                    font.pixelSize: 13
                    color: Couleurs.texte
                    clip: true
                    onTextEdited: { couche.filtre = text; vueListe.currentIndex = 0 }
                    Keys.onDownPressed: vueListe.incrementCurrentIndex()
                    Keys.onUpPressed: vueListe.decrementCurrentIndex()
                    Keys.onReturnPressed: if (vueListe.currentIndex >= 0 && couche.choixVus.length) couche.choisir(couche.choixVus[vueListe.currentIndex])
                    Keys.onEscapePressed: liste.close()
                    Text { visible: !parent.text; anchors.verticalCenter: parent.verticalCenter; text: "Chercher"; font: parent.font; color: Couleurs.texte3 }
                }
            }
            ListView {
                id: vueListe
                Layout.fillWidth: true
                Layout.preferredHeight: Math.min(contentHeight, 34 * 9)
                clip: true
                model: couche.choixVus
                keyNavigationWraps: true
                QQC2.ScrollBar.vertical: QQC2.ScrollBar { policy: vueListe.contentHeight > vueListe.height ? QQC2.ScrollBar.AsNeeded : QQC2.ScrollBar.AlwaysOff }
                Keys.onReturnPressed: if (currentIndex >= 0) couche.choisir(couche.choixVus[currentIndex])
                Keys.onEscapePressed: liste.close()
                delegate: QQC2.AbstractButton {
                    id: element
                    required property string modelData
                    required property int index
                    readonly property bool choisi: modelData === couche.valeur
                    width: vueListe.width
                    height: 34
                    hoverEnabled: true
                    focusPolicy: Qt.NoFocus
                    onHoveredChanged: if (hovered) vueListe.currentIndex = index
                    onClicked: couche.choisir(modelData)
                    background: Rectangle {
                        radius: 8
                        color: vueListe.currentIndex === element.index ? fenetre.accentFond : "transparent"
                    }
                    contentItem: RowLayout {
                        spacing: 8
                        Text {
                            Layout.fillWidth: true
                            Layout.leftMargin: 8
                            text: element.modelData
                            font.pixelSize: 13
                            font.weight: element.choisi ? Font.DemiBold : Font.Normal
                            color: vueListe.currentIndex === element.index ? fenetre.accentEncre : Couleurs.texte
                            elide: Text.ElideRight
                        }
                        Picto { visible: element.choisi; width: 14; height: 14; Layout.rightMargin: 8; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: fenetre.accentEncre; trait: 2.2 }
                    }
                }
            }
        }
    }

    // ——— Les liens ———
    property var lien: null            // { adresse, c, l, site, ouvrable, alertes: [] }
    function montrerLien(adresse, c, l) {
        // (le texte affiché : celui de la formule LIEN.HYPERTEXTE, sinon la case)
        var affiche = moteur.formule
        var f = /^=(?:LIEN\.HYPERTEXTE|HYPERLINK)\(\s*"[^"]*"\s*[;,]\s*"([^"]*)"/i.exec(affiche)
        if (f) affiche = f[1]
        var a = AnalyseLien.analyser(adresse, affiche)
        lien = { adresse: adresse, c: c >= 0 ? c : moteur.colonne, l: l >= 0 ? l : moteur.ligne, site: a.site, ouvrable: a.ouvrable, alertes: a.alertes }
    }
    TextEdit { id: pressePapiers; visible: false }
    function copierLien() {
        pressePapiers.text = lien.adresse
        pressePapiers.selectAll()
        pressePapiers.copy()
        message.montrer("Lien copié")
        lien = null
        moteur.forceActiveFocus()
    }
    readonly property var caseLien: lien ? bande(lien.c, lien.l) : null
    Rectangle {
        id: carteLien
        visible: couche.lien !== null && couche.caseLien !== null && !couche.moteur.curseurTexteVisible
        width: 320
        height: contenuLien.implicitHeight + 22
        x: couche.caseLien ? Math.max(6, Math.min(couche.caseLien.x - couche.moteur.vueX, couche.width - width - 6)) : 0
        y: couche.caseLien ? (couche.caseLien.y - couche.moteur.vueY + couche.caseLien.h + height + 8 < couche.height
                             ? couche.caseLien.y - couche.moteur.vueY + couche.caseLien.h + 6
                             : couche.caseLien.y - couche.moteur.vueY - height - 6) : 0
        radius: 12
        color: Couleurs.champ
        border.width: 1
        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
        Rectangle { z: -1; x: -2; y: 3; width: parent.width + 4; height: parent.height + 4; radius: 14; color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.08) }
        MouseArea { anchors.fill: parent; acceptedButtons: Qt.AllButtons }
        ColumnLayout {
            id: contenuLien
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
                    Text { Layout.fillWidth: true; text: couche.lien ? couche.lien.site || couche.lien.adresse : ""; font.pixelSize: 14; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideMiddle }
                    Text { Layout.fillWidth: true; text: couche.lien ? couche.lien.adresse : ""; font.pixelSize: 11; color: Couleurs.texte3; elide: Text.ElideMiddle }
                }
                Outil { Layout.preferredWidth: 26; Layout.preferredHeight: 26; picto: "M7 7l10 10 M17 7L7 17"; aide: "Fermer"; onClicked: { couche.lien = null; couche.moteur.forceActiveFocus() } }
            }
            // Les mises en garde
            Repeater {
                model: couche.lien ? couche.lien.alertes : []
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
                Item { Layout.fillWidth: true }
                Outil {
                    text: "Copier le lien"
                    background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }
                    onClicked: couche.copierLien()
                }
                Outil {
                    visible: couche.lien !== null && couche.lien.ouvrable
                    text: couche.lien && couche.lien.alertes.length ? "Ouvrir quand même" : "Ouvrir"
                    encre: fenetre.accentEncre
                    background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.2) : fenetre.accentFond }
                    onClicked: { Qt.openUrlExternally(couche.lien.adresse); couche.lien = null; couche.moteur.forceActiveFocus() }
                }
            }
        }
    }
}
