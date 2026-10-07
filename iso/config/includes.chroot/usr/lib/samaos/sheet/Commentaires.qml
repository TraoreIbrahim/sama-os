// Les commentaires du classeur (des notes du moteur, que le .xlsx garde et qu'Excel montre) : leur liste, celui de la
// case courante, et ce qu'on en fait (ajouter, changer, répondre, retirer). Chacun est signé du nom de la personne et
// daté. Le moteur ne les dessine pas : la grille montre leurs marques et leurs bulles, le panneau les liste tous.
// Les réponses (maquette « Docs · Blocs » : « Répondre… ») sont écrites dans la note, une ligne chacune, que Excel
// montre telle quelle : « ↳ Ibrahim Traoré, 07/10/2026 20:50 : Vu, merci. » ; le commentaire garde son auteur et sa
// date.
import QtQuick
import Sama.Moteur
import "../reglages"

Item {
    id: commentaires
    visible: false
    required property var doc

    // [{ id, tab, auteur, date, texte, reponses: [{ auteur, date, texte }], c, l }] (c, l : colonne et ligne, à partir
    // de 0 ; texte : sans les réponses)
    property var liste: []
    readonly property var surFeuille: liste.filter(function (k) { return k.tab === doc.partie })
    function deCase(c, l) {
        for (var i = 0; i < liste.length; i++) if (liste[i].tab === doc.partie && liste[i].c === c && liste[i].l === l) return liste[i]
        return null
    }
    readonly property var courant: deCase(doc.colonne, doc.ligne)
    // (la grille ouvre l'écriture d'un commentaire sur la case courante)
    signal ecrire()
    function demanderEcriture() { if (doc.etat === DocumentLO.Pret) ecrire() }

    function lire() { if (doc.etat === DocumentLO.Pret) doc.demanderValeurs(".uno:ViewAnnotations") }
    Timer { id: relire; interval: 300; onTriggered: commentaires.lire() }
    Connections {
        target: commentaires.doc
        function onCommentairesChanges() { relire.restart() }
        function onEtatChanged() { if (commentaires.doc.etat === DocumentLO.Pret) relire.restart(); else commentaires.liste = [] }
        function onValeurs(commande, reponse) {
            if (commande !== ".uno:ViewAnnotations" || !reponse || !reponse.comments) return
            commentaires.liste = reponse.comments.map(function (k) {
                var p = String(k.cellRange || k.cellPos || "").split(/[\s,]+/).map(Number)
                var t = commentaires.decouper(k.text)
                return { id: String(k.id), tab: Number(k.tab), auteur: k.author || "", date: k.dateTime || "", texte: t.texte, reponses: t.reponses, c: p[0], l: p[1] }
            })
        }
    }

    function chaine(v) { return { type: "string", value: String(v) } }
    function ajouter(texte) { doc.commande(".uno:InsertAnnotation", { Text: chaine(texte) }); relire.restart() }
    // (le texte change ; ses réponses restent)
    function modifier(id, texte) { doc.commande(".uno:EditAnnotation", { Id: chaine(id), Text: chaine(texte + lignes(parId(id))) }); relire.restart() }
    // Une réponse, signée du nom de la personne et datée ; le commentaire garde son auteur et sa date (Ctrl+Z la retire)
    function repondre(id, texte) {
        var k = parId(id), t = String(texte || "").trim().replace(/\s*\n\s*/g, " ")
        if (!k || !t) return
        var r = { auteur: nomPersonne || "Moi", date: maintenant(), texte: t }
        doc.commande(".uno:EditAnnotation", { Id: chaine(id), Text: chaine(k.texte + lignes({ reponses: k.reponses.concat([r]) })),
                                              Author: chaine(k.auteur), Date: chaine(k.date) })
        relire.restart()
    }
    function parId(id) {
        for (var i = 0; i < liste.length; i++) if (liste[i].id === id) return liste[i]
        return null
    }

    // ——— Les réponses, dans le texte de la note ———
    function decouper(texte) {
        var forme = /^↳ (.+?), (\d{1,2}\/\d{1,2}\/\d{2,4} \d{1,2}:\d{2}) : (.*)$/
        var texteSeul = [], reponses = []
        String(texte || "").split(/\r?\n/).forEach(function (l) {
            var m = forme.exec(l)
            if (m) reponses.push({ auteur: m[1], date: m[2], texte: m[3] })
            else if (reponses.length) reponses[reponses.length - 1].texte += "\n" + l
            else texteSeul.push(l)
        })
        return { texte: texteSeul.join("\n"), reponses: reponses }
    }
    function lignes(k) {
        return k && k.reponses && k.reponses.length
               ? "\n" + k.reponses.map(function (r) { return "↳ " + r.auteur + ", " + r.date + " : " + r.texte.replace(/\n/g, " ") }).join("\n") : ""
    }
    function maintenant() {
        var d = new Date()
        function z(n) { return (n < 10 ? "0" : "") + n }
        return z(d.getDate()) + "/" + z(d.getMonth() + 1) + "/" + d.getFullYear() + " " + z(d.getHours()) + ":" + z(d.getMinutes())
    }
    // Le nom de la personne (le nom complet du compte, sinon son identifiant), comme le moteur en signe les commentaires
    property string nomPersonne: ""
    Commande { id: commande }
    Component.onCompleted: commande.lancer("getent passwd \"$(id -un)\"", function (sortie) {
        var p = sortie.trim().split(":")
        commentaires.nomPersonne = (p[4] || "").split(",")[0].trim() || p[0] || ""
    })
    function supprimer(id) { doc.commande(".uno:DeleteNote", { Id: chaine(id) }); relire.restart() }

    // ——— Pour les montrer ———
    function adresse(k) { return fenetre.tableaux.lettres(k.c) + (k.l + 1) }
    function initiales(nom) {
        var m = String(nom || "").trim().split(/\s+/).filter(function (x) { return x.length > 0 })
        if (!m.length) return "?"
        return (m[0].charAt(0) + (m.length > 1 ? m[1].charAt(0) : "")).toUpperCase()
    }
    // « 07/10/2026 13:04 » → « Aujourd'hui, 13:04 », « Hier, 9:30 », « 3 oct., 9:30 »
    function quand(date) {
        var m = /(\d{1,2})\/(\d{1,2})\/(\d{2,4})\s+(\d{1,2}):(\d{2})/.exec(String(date))
        if (!m) return String(date)
        var a = Number(m[3]) < 100 ? 2000 + Number(m[3]) : Number(m[3])
        var d = new Date(a, Number(m[2]) - 1, Number(m[1]))
        var auj = new Date(); auj.setHours(0, 0, 0, 0)
        var jours = Math.round((auj - d) / 86400000)
        var heure = Number(m[4]) + ":" + m[5]
        if (jours === 0) return "Aujourd'hui, " + heure
        if (jours === 1) return "Hier, " + heure
        return d.toLocaleDateString(Qt.locale("fr_FR"), "d MMM") + (d.getFullYear() !== auj.getFullYear() ? " " + d.getFullYear() : "") + ", " + heure
    }
}
