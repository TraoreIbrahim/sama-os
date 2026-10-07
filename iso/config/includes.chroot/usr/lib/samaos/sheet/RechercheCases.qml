// Rechercher (Ctrl+F) et remplacer (Ctrl+H) : ce qu'on cherche, et les cases trouvées surlignées dans la grille (on
// tape dans BarreRecherche, sous la barre de formule). Sama cherche dans ce qu'on voit (« kone » trouve « Koné »,
// « 50 000 » trouve « 50 000 F ») et va à la première case trouvée ; Entrée pour la suivante, Maj+Entrée pour la
// précédente. Sur la feuille ou dans tout le classeur ; la case entière, les majuscules et accents exacts, dans les
// formules. Remplacer change la case trouvée, Tout remplacer toutes : seulement des textes, jamais une formule (sauf
// en cherchant dans les formules) ; Ctrl+Z revient.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: recherche
    required property var moteur
    required property var vueGrille
    readonly property var tableaux: fenetre.tableaux
    anchors.fill: parent

    property bool ouverte: false
    property bool avecRemplacer: false
    property string texte: ""
    property string par: ""
    property bool toutes: false
    property bool entiere: false
    property bool exact: false
    property bool formules: false
    // [{ f, c, l, v }] dans l'ordre de lecture ; total (au-delà des 500 décrites) ; la case où l'on est
    property var resultats: []
    property int total: 0
    property int courant: -1
    property bool enCours: false

    // (la barre de recherche, sous la barre de formule, prend le clavier)
    signal focaliser()
    function ouvrir(remplacer) {
        if (fenetre.vue !== "grille") fenetre.voirGrille()
        ouverte = true
        if (remplacer) avecRemplacer = true
        focaliser()
        if (texte) chercher(null)
    }
    function fermer() {
        ouverte = false
        resultats = []
        total = 0
        courant = -1
        moteur.forceActiveFocus()
    }

    // ——— Le motif : les accents, les majuscules et les espaces des montants n'y comptent pas ———
    readonly property var familles: ({ a: "aàâäáã", e: "eéèêë", i: "iîïíì", o: "oôöóò", u: "uùûüú", c: "cç", y: "yÿ", n: "nñ" })
    function base(ch) {
        var l = ch.toLowerCase()
        for (var k in familles) if (familles[k].indexOf(l) >= 0) return k
        return l
    }
    function motif(t) {
        var r = ""
        var dansEspace = false
        for (var i = 0; i < t.length; i++) {
            var ch = t.charAt(i)
            if (/\s/.test(ch)) {
                // (« 50 000 » : l'espace du montant peut être insécable, fine, ou absente)
                if (!dansEspace) r += "\\s*"
                dansEspace = true
                continue
            }
            dansEspace = false
            if (/[.*+?^${}()|[\]\\]/.test(ch)) { r += "\\" + ch; continue }
            var b = base(ch)
            if (!exact && familles[b]) r += "[" + familles[b] + familles[b].toUpperCase() + "]"
            else r += ch
        }
        return r
    }
    // (le texte de remplacement, pris tel quel : & et $ n'y ont pas de sens caché)
    function remplacement(t) { return t.replace(/\\/g, "\\\\").replace(/&/g, "\\&").replace(/\$/g, "\\$") }

    // ——— Chercher ———
    property int jeton: 0
    Timer { id: attente; interval: 220; onTriggered: recherche.chercher(null) }
    onTexteChanged: if (ouverte) attente.restart()
    onToutesChanged: if (ouverte && texte) chercher(null)
    onEntiereChanged: if (ouverte && texte) chercher(null)
    onExactChanged: if (ouverte && texte) chercher(null)
    onFormulesChanged: if (ouverte && texte) chercher(null)
    function lettres(c) { return tableaux.lettres(c) }
    function avant(a, b) { return a.f !== b.f ? a.f < b.f : a.l !== b.l ? a.l < b.l : a.c < b.c }
    // depart : la position d'où partir (la case courante si null) ; on va au premier résultat qui n'est pas avant elle
    function chercher(depart) {
        if (!texte.trim()) { resultats = []; total = 0; courant = -1; return }
        var j = ++jeton
        enCours = true
        tableaux.appeler("SamaRecherche.Chercher", [motif(texte.trim()), exact ? "1" : "0", entiere ? "1" : "0", formules ? "1" : "0",
                                                   toutes ? "1" : "0", "500"], function (ok, v) {
            if (j !== recherche.jeton) return
            recherche.enCours = false
            if (!ok || !v || v.charAt(0) !== "{") { recherche.resultats = []; recherche.total = 0; recherche.courant = -1; return }
            var d
            try { d = JSON.parse(v) } catch (e) { return }
            var liste = d.cases.sort(function (a, b) { return recherche.avant(a, b) ? -1 : recherche.avant(b, a) ? 1 : 0 })
            recherche.resultats = liste
            recherche.total = d.total
            var p = depart || { f: recherche.moteur.partie, l: recherche.moteur.ligne, c: recherche.moteur.colonne }
            var k = 0
            while (k < liste.length && recherche.avant(liste[k], p)) k++
            recherche.courant = liste.length ? k % liste.length : -1
            if (recherche.courant >= 0) recherche.aller(recherche.courant)
        })
    }
    function aller(i) {
        var r = resultats[i]
        if (!r) return
        courant = i
        if (r.f !== moteur.partie) moteur.allerPartie(r.f)
        moteur.allerA(lettres(r.c) + (r.l + 1))
    }
    function suivant() { if (resultats.length) aller((courant + 1) % resultats.length) }
    function precedent() { if (resultats.length) aller((courant - 1 + resultats.length) % resultats.length) }
    // (le classeur change pendant que la carte est ouverte : on cherche à nouveau, depuis la case trouvée)
    Timer { id: relire; interval: 500; onTriggered: if (recherche.ouverte && recherche.texte) recherche.chercher(recherche.resultats[recherche.courant] || null) }
    Connections {
        target: recherche.moteur
        function onRevisionChanged() { if (recherche.ouverte && !recherche.enCours) relire.restart() }
    }

    // ——— Remplacer ———
    function remplacerIci() {
        var r = resultats[courant]
        if (!r) return
        tableaux.appeler("SamaRecherche.Remplacer", [motif(texte.trim()), remplacement(par), exact ? "1" : "0", entiere ? "1" : "0", formules ? "1" : "0",
                                                    toutes ? "1" : "0", String(r.f), String(r.c), String(r.l)], function (ok, v) {
            if (!tableaux.verifier(ok, v)) return
            if (v === "0") message.montrer("Cette case n'est pas un texte : elle n'est pas changée")
            relire.stop()
            recherche.chercher({ f: r.f, l: r.l, c: r.c + 1 })
        })
    }
    function remplacerTout() {
        if (!texte.trim()) return
        tableaux.appeler("SamaRecherche.Remplacer", [motif(texte.trim()), remplacement(par), exact ? "1" : "0", entiere ? "1" : "0", formules ? "1" : "0",
                                                    toutes ? "1" : "0", "0", "-1", "0"], function (ok, v) {
            if (!tableaux.verifier(ok, v)) return
            var n = Number(v)
            message.montrer(n === 0 ? "Rien à remplacer" : n + (n > 1 ? " cases changées" : " case changée") + " · Ctrl+Z pour revenir")
            relire.stop()
            recherche.chercher(null)
            // (le clavier revient à la grille : Ctrl+Z y annule le remplacement)
            recherche.moteur.forceActiveFocus()
        })
    }

    // ——— Les cases trouvées, surlignées ———
    function bande(c, l) {
        var bc = vueGrille.parLettres[lettres(c)], bl = vueGrille.parNumero[String(l + 1)]
        return bc && bl && bc.fin > bc.debut && bl.fin > bl.debut ? { x: bc.debut, y: bl.debut, w: bc.fin - bc.debut, h: bl.fin - bl.debut } : null
    }
    Repeater {
        model: recherche.ouverte ? recherche.resultats.length : 0
        delegate: Rectangle {
            required property int index
            readonly property var r: recherche.resultats[index]
            readonly property var b: r && r.f === recherche.moteur.partie ? recherche.bande(r.c, r.l) : null
            readonly property bool ici: index === recherche.courant
            visible: b !== null
            x: b ? b.x - recherche.moteur.vueX : 0
            y: b ? b.y - recherche.moteur.vueY : 0
            width: b ? b.w : 0
            height: b ? b.h : 0
            color: Qt.rgba(226 / 255, 166 / 255, 43 / 255, ici ? 0.32 : 0.16)
            border.width: 1
            border.color: Qt.rgba(196 / 255, 136 / 255, 20 / 255, ici ? 0.9 : 0.45)
        }
    }
}
