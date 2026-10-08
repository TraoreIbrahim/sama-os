// Ajouter un bloc (maquette « Docs · Le document libre ») : « / » au début d'une ligne, ou le + à gauche d'une ligne
// vide, ouvre le choix des blocs (titre, listes, tableau, liste de présence, encadré, image, signature…) ; on tape pour
// chercher, Entrée choisit. « @ » propose une date (aujourd'hui, demain, « sam » : samedi prochain, « 25 » : le 25) ou
// une personne (vous, les auteurs des commentaires). Le moteur garde le clavier : Sama lit la ligne derrière lui.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import Sama.Moteur
import "../reglages"
import "../sheet"

Item {
    id: blocs
    required property var doc
    property real decalage: 0
    anchors.fill: parent
    property string genre: ""          // « / », « @ », « + » (le bouton) ; "" : fermé
    property string filtre: ""
    property int debut: 0              // (la place du « / » ou du « @ » dans la ligne)
    property int choix: 0
    property var place: null
    readonly property bool ouvert: genre !== ""
    readonly property var actions: fenetre.actions

    // ——— Les blocs ———
    readonly property var tousLesBlocs: [
        { cle: "titre1", nom: "Titre", detail: "Une partie, dans le plan", mots: "titre 1 partie chapitre", picto: "M6 6h12 M12 6v12" },
        { cle: "titre2", nom: "Sous-titre", detail: "Titre 2", mots: "titre 2 sous partie", picto: "M4 6v12 M11 6v12 M4 12h7 M15 9a2 2 0 0 1 4 0c0 2-4 4-4 9h4" },
        { cle: "puces", nom: "Liste à puces", detail: "", mots: "liste points", picto: "M9 7h11 M9 12h11 M9 17h11 M4.5 7v.1 M4.5 12v.1 M4.5 17v.1" },
        { cle: "numeros", nom: "Liste numérotée", detail: "1, 2, 3", mots: "liste numeros", picto: "M10 7h10 M10 12h10 M10 17h10 M4 5l1.5-1v5 M3.5 14.5a1.5 1.5 0 0 1 3 0c0 1-3 2-3 3.5h3" },
        { cle: "tableau", nom: "Tableau", detail: "Lignes et colonnes", mots: "grille", picto: "M4 5h16v14H4z M4 10h16 M10 5v14" },
        { cle: "presence", nom: "Liste de présence", detail: "Noms et signatures", mots: "presents membres emargement reunion", picto: "M9 4a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z M3 19c.8-3.2 3.2-5 6-5s5.2 1.8 6 5 M15 12l2 2 4-4" },
        { cle: "encadre", nom: "Encadré", detail: "À retenir", mots: "important note cadre", picto: "M4 5h16v14H4z M8 10h8 M8 14h5" },
        { cle: "citation", nom: "Citation", detail: "En retrait", mots: "citer", picto: "M7 8h4v5H8l-1 3 M14 8h4v5h-3l-1 3" },
        { cle: "image", nom: "Image", detail: "Depuis l'ordinateur", mots: "photo logo illustration", picto: "M4 5h16v14H4z M4 16l5-5 4 4 3-3 4 4 M15 9v.1" },
        { cle: "date", nom: "Date du jour", detail: "", mots: "aujourd'hui", picto: "M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4" },
        { cle: "signature", nom: "Signature", detail: "Depuis Mes informations", mots: "signer nom", picto: "M4 18c3-1 4-6 6-6s1 5 3 5 3-3 4-3 2 2 3 2 M4 21h16" },
        { cle: "saut", nom: "Saut de page", detail: "La suite sur une page neuve", mots: "nouvelle page", picto: "M6 3v6h12V3 M6 21v-6h12v6 M3 12h3 M9 12h2 M14 12h2 M19 12h2" }
    ]
    function normaliser(s) { return String(s).toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "") }

    // ——— Les dates et les personnes (@) ———
    readonly property var mois: ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]
    function enMots(d, avecJour) {
        return (avecJour ? Qt.locale("fr_FR").dayName(d.getDay()) + " " : "") + (d.getDate() === 1 ? "1er" : d.getDate()) + " " + mois[d.getMonth()] + " " + d.getFullYear()
    }
    function mentions() {
        var l = [], aujourdhui = new Date(), f = normaliser(filtre)
        function jour(n) { var d = new Date(aujourdhui); d.setDate(d.getDate() + n); return d }
        var dateDuJour = enMots(aujourdhui, false)
        if (!f || "aujourd'hui".indexOf(f) === 0 || "date".indexOf(f) === 0)
            l.push({ cle: "texte", nom: "Aujourd'hui", detail: dateDuJour, texte: dateDuJour, picto: "M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4" })
        if (!f || "demain".indexOf(f) === 0)
            l.push({ cle: "texte", nom: "Demain", detail: enMots(jour(1), true), texte: enMots(jour(1), true), picto: "M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4" })
        // (les deux mois qui viennent : « sam » → samedi prochain, « 25 » → le 25, « nov » → novembre)
        if (f) {
            for (var n = 2; n < 62 && l.length < 6; ++n) {
                var d = jour(n), t = enMots(d, true)
                var mots = normaliser(t).split(" ")
                if (mots.some(function (m) { return m.indexOf(f) === 0 }) || normaliser(t).indexOf(f) === 0)
                    l.push({ cle: "texte", nom: t.charAt(0).toUpperCase() + t.slice(1), detail: n < 7 ? "Dans " + n + " jours" : "", texte: t, picto: "M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4" })
            }
        }
        // Les personnes : vous, et ceux qui ont commenté
        var personnes = [fenetre.moi.nom]
        fenetre.page.marge.fils.forEach(function (fil) {
            [fil.auteur].concat(fil.reponses.map(function (r) { return r.auteur })).forEach(function (a) { if (a && personnes.indexOf(a) < 0) personnes.push(a) })
        })
        personnes.forEach(function (p) {
            if (p && (!f || normaliser(p).split(" ").some(function (m) { return m.indexOf(f) === 0 })) && l.length < 8)
                l.push({ cle: "texte", nom: p, detail: p === fenetre.moi.nom ? "Vous" : "", texte: p, picto: "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M4 20c1-4 4.5-6 8-6s7 2 8 6", personne: true })
        })
        return l
    }
    // (les blocs dont le nom commence par ce qu'on tape d'abord, puis un de leurs mots, puis le reste : « enc » donne
    // Encadré avant Liste de présence)
    function rang(b, f) {
        var nom = normaliser(b.nom)
        if (nom.indexOf(f) === 0) return 0
        if (nom.split(/[\s-]+/).some(function (m) { return m.indexOf(f) === 0 })) return 1
        if (normaliser(b.detail + " " + b.mots).split(/[\s,]+/).some(function (m) { return m.indexOf(f) === 0 })) return 2
        return nom.indexOf(f) >= 0 ? 3 : -1
    }
    readonly property var liste: {
        if (genre === "@") return mentions()
        var f = normaliser(filtre)
        if (!f) return tousLesBlocs
        return tousLesBlocs.map(function (b, i) { return { b: b, r: rang(b, f), i: i } })
                           .filter(function (x) { return x.r >= 0 })
                           .sort(function (x, y) { return x.r - y.r || x.i - y.i })
                           .map(function (x) { return x.b })
    }
    onListeChanged: choix = Math.max(0, Math.min(choix, liste.length - 1))
    onFiltreChanged: choix = 0

    // ——— Ouvrir, suivre la frappe, fermer ———
    function placeCurseur() {
        var c = doc.curseurTexte
        return { x: c.x - doc.vueX + decalage, y: c.y - doc.vueY, h: c.height }
    }
    function ouvrir(g, d) { genre = g; debut = d; filtre = ""; choix = 0; place = placeCurseur(); sourisDepart = null; sourisBougee = false }
    // (la souris ne choisit qu'après avoir vraiment bougé : pas une liste qui se réordonne sous elle)
    property var sourisDepart: null
    property bool sourisBougee: false
    function survol(p, i) {
        if (!sourisBougee) {
            if (!sourisDepart) { sourisDepart = p; return }
            if (Math.abs(p.x - sourisDepart.x) + Math.abs(p.y - sourisDepart.y) < 4) return
            sourisBougee = true
        }
        choix = i
    }
    function fermer() { genre = ""; filtre = ""; validerApres = false }
    // Après une touche : la ligne derrière le curseur (le moteur l'a écrite)
    Timer {
        id: relire
        interval: 60
        onTriggered: { blocs.lectures++; garde.restart(); fenetre.appeler("SamaDocs.Ligne", [], function (ok, v) {
            blocs.lectures--
            if (!ok || !v || v.charAt(0) !== "{") return
            var d = JSON.parse(v), avant = d.avant
            blocs.ligneVide = d.vide && !d.tableau
            if (!blocs.ouvert) {
                // (« / » au début de la ligne ; « @ » au début d'un mot ; on a pu taper la suite entre-temps)
                var i = avant.lastIndexOf("@")
                if (blocs.attendu === "/" && /^\/\S{0,24}$/.test(avant)) blocs.ouvrir("/", 0)
                else if (blocs.attendu === "@" && i >= 0 && (i === 0 || /\s/.test(avant.charAt(i - 1))) && avant.length - i <= 20) blocs.ouvrir("@", i)
                blocs.attendu = ""
                // (pas de menu, finalement : l'Entrée retenue va au texte)
                if (!blocs.ouvert) { if (blocs.validerApres) { blocs.validerApres = false; blocs.doc.touche(1280, 13) } return }
            }
            if (blocs.genre === "+") { blocs.validerApres = false; return }
            // (le « / » ou le « @ » effacé, une espace après un bloc : on ferme)
            if (avant.length <= blocs.debut || avant.charAt(blocs.debut) !== blocs.genre) { blocs.fermer(); return }
            var f = avant.slice(blocs.debut + 1)
            if (f.length > 24 || (blocs.genre === "/" && /\s/.test(f)) || /\s\s/.test(f)) { blocs.fermer(); return }
            blocs.filtre = f
            // (Entrée tapée avant la fin de la lecture : on choisit maintenant, d'après tout ce qui a été tapé)
            if (blocs.validerApres && !relire.running && blocs.lectures === 0) {
                blocs.validerApres = false
                if (blocs.liste.length) blocs.valider(blocs.choix)
                else { blocs.fermer(); blocs.doc.touche(1280, 13) }
            }
        }) }
    }
    property int lectures: 0
    Timer { id: garde; interval: 1200; onTriggered: blocs.lectures = 0 }   // (une lecture restée sans réponse)
    property bool validerApres: false
    property string attendu: ""
    property bool ligneVide: false
    // (appelée par la page avant que le moteur reçoive la touche ; true : la touche est prise ici)
    function touche(e) {
        if (ouvert) {
            if (e.key === Qt.Key_Down) { choix = (choix + 1) % Math.max(1, liste.length); return true }
            if (e.key === Qt.Key_Up) { choix = (choix + liste.length - 1) % Math.max(1, liste.length); return true }
            if (e.key === Qt.Key_Escape) { fermer(); return true }
            if (e.key === Qt.Key_Return || e.key === Qt.Key_Enter || e.key === Qt.Key_Tab) {
                if (relire.running || lectures > 0) { validerApres = true; return true }
                if (liste.length) { valider(choix); return true }
            }
            if (genre === "+" && e.text) fermer()
            relire.restart()
            return false
        }
        if (e.text === "/" || e.text === "@") { attendu = e.text; relire.restart() }
        else if (attendu !== "") {
            // (le menu n'est pas encore ouvert : la suite tapée compte, Entrée attend qu'il s'ouvre)
            if (e.key === Qt.Key_Return || e.key === Qt.Key_Enter) { validerApres = true; return true }
            relire.restart()
        }
        return false
    }
    Connections {
        target: blocs.doc
        function onPointeurAppuye() { blocs.fermer() }
        function onVueChanged() { if (blocs.ouvert) blocs.place = blocs.placeCurseur() }
        function onCurseurTexteChanged() { if (!blocs.ouvert) vide.restart() }
    }
    // (le + à gauche d'une ligne vide : on relit la ligne quand le curseur s'arrête)
    Timer { id: vide; interval: 250; onTriggered: if (!blocs.ouvert && blocs.doc.etat === DocumentLO.Pret && !blocs.doc.selection.length) relire.restart() }

    // ——— Choisir ———
    function valider(i) {
        var b = liste[i]
        if (!b) return
        var n = genre === "+" ? 0 : filtre.length + 1, g = genre
        fermer()
        if (g === "@" || b.cle === "date") {
            var t = b.cle === "date" ? enMots(new Date(), false) : b.texte
            fenetre.appeler("SamaDocs.RemplacerAvant", [String(n), t + (g === "@" ? " " : "")], function () { blocs.doc.forceActiveFocus() })
            return
        }
        fenetre.appeler("SamaDocs.RemplacerAvant", [String(n), ""], function () {
            blocs.doc.forceActiveFocus()
            if (b.cle === "titre1") actions.appliquerStyle("Heading 1")
            else if (b.cle === "titre2") actions.appliquerStyle("Heading 2")
            else if (b.cle === "citation") actions.appliquerStyle("Quotations")
            else if (b.cle === "puces") actions.lancer("puces")
            else if (b.cle === "numeros") actions.lancer("numeros")
            else if (b.cle === "tableau") actions.tableau(3, 3)
            else if (b.cle === "image") fenetre.ouvrirDialogue("image")
            else if (b.cle === "saut") actions.lancer("sautPage")
            else if (b.cle === "presence") fenetre.appeler("SamaDocs.ListePresence", [])
            else if (b.cle === "encadre") fenetre.appeler("SamaDocs.Encadre", [])
            else if (b.cle === "signature") fenetre.appeler("SamaDocs.Signature", [fenetre.moi.nom])
        })
    }

    // ——— Le + d'une ligne vide ———
    QQC2.AbstractButton {
        id: plus
        readonly property var c: blocs.placeCurseur()
        visible: blocs.ligneVide && !blocs.ouvert && blocs.doc.curseurTexteVisible && blocs.doc.activeFocus && !blocs.doc.selection.length && blocs.doc.curseurTexte.height > 0
        x: c.x - 34
        y: c.y + (c.h - height) / 2
        width: 24
        height: 24
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        opacity: hovered ? 1 : 0.55
        QQC2.ToolTip.visible: hovered
        QQC2.ToolTip.text: "Ajouter un bloc (ou tapez /)"
        QQC2.ToolTip.delay: 500
        onClicked: { blocs.ouvrir("+", 0); blocs.doc.forceActiveFocus() }
        background: Rectangle { radius: 7; color: fenetre.accent }
        contentItem: Item { Picto { anchors.centerIn: parent; width: 14; height: 14; trace: "M12 5v14 M5 12h14"; encre: "#FFFFFF"; trait: 2.4 } }
    }

    // ——— Le choix ———
    Rectangle {
        id: carte
        visible: blocs.ouvert && blocs.place !== null && (blocs.liste.length > 0 || blocs.genre !== "@")
        width: 300
        height: colonne.implicitHeight + 16
        x: blocs.place ? Math.max(6, Math.min(blocs.place.x - 8, blocs.width - width - 6)) : 0
        y: blocs.place ? (blocs.place.y + blocs.place.h + height + 10 < blocs.height ? blocs.place.y + blocs.place.h + 6 : Math.max(6, blocs.place.y - height - 6)) : 0
        radius: 14
        color: Couleurs.champ
        border.width: 1
        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
        Rectangle { z: -1; anchors.fill: parent; anchors.topMargin: 8; anchors.margins: -4; radius: 18; color: Qt.rgba(40 / 255, 25 / 255, 10 / 255, 0.07) }
        Rectangle { z: -1; anchors.fill: parent; anchors.topMargin: 3; anchors.margins: -1; radius: 15; color: Qt.rgba(40 / 255, 25 / 255, 10 / 255, 0.08) }
        MouseArea { anchors.fill: parent }
        ColumnLayout {
            id: colonne
            x: 8
            y: 8
            width: parent.width - 16
            spacing: 0
            Text {
                Layout.leftMargin: 8
                Layout.topMargin: 2
                Layout.bottomMargin: 6
                text: blocs.genre === "@" ? "Une date, une personne · tapez pour chercher" : "Ajouter un bloc · tapez pour chercher"
                font.pixelSize: 12
                color: Couleurs.texte2
            }
            Text {
                visible: blocs.liste.length === 0
                Layout.leftMargin: 8
                Layout.bottomMargin: 6
                text: "Aucun bloc pour « " + blocs.filtre + " »"
                font.pixelSize: 13
                color: Couleurs.texte3
            }
            Repeater {
                model: blocs.liste
                delegate: QQC2.AbstractButton {
                    id: ligne
                    required property var modelData
                    required property int index
                    Layout.fillWidth: true
                    implicitHeight: 38
                    hoverEnabled: true
                    focusPolicy: Qt.NoFocus
                    onClicked: blocs.valider(index)
                    // (la souris qui bouge choisit la ligne ; pas une liste qui se réordonne sous une souris immobile)
                    MouseArea { anchors.fill: parent; hoverEnabled: true; acceptedButtons: Qt.NoButton; onPositionChanged: (m) => blocs.survol(mapToGlobal(m.x, m.y), ligne.index) }
                    background: Rectangle { radius: 9; color: blocs.choix === ligne.index ? fenetre.accentFond : "transparent" }
                    contentItem: RowLayout {
                        spacing: 12
                        Rectangle {
                            Layout.leftMargin: 8
                            Layout.preferredWidth: 26
                            Layout.preferredHeight: 26
                            radius: ligne.modelData.personne ? 13 : 7
                            color: Couleurs.champ
                            border.width: 1
                            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                            Picto { anchors.centerIn: parent; width: 15; height: 15; trace: ligne.modelData.picto; encre: fenetre.accentEncre }
                        }
                        Text { Layout.fillWidth: true; text: ligne.modelData.nom; font.pixelSize: 14; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideRight }
                        Text { Layout.rightMargin: 8; text: ligne.modelData.detail; font.pixelSize: 12; color: Couleurs.texte2; elide: Text.ElideRight; Layout.maximumWidth: 150 }
                    }
                }
            }
        }
    }
}
