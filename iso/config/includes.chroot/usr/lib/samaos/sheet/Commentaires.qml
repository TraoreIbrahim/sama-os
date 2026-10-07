// Les commentaires du classeur (des notes du moteur, que le .xlsx garde et qu'Excel montre) : leur liste, celui de la
// case courante, et ce qu'on en fait (ajouter, changer, retirer). Chacun est signé du nom de la personne et daté. Le
// moteur ne les dessine pas : la grille montre leurs marques et leurs bulles, le panneau les liste tous.
import QtQuick
import Sama.Moteur

Item {
    id: commentaires
    visible: false
    required property var doc

    // [{ id, tab, auteur, date, texte, c, l }] (c, l : colonne et ligne, à partir de 0)
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
                return { id: String(k.id), tab: Number(k.tab), auteur: k.author || "", date: k.dateTime || "", texte: k.text || "", c: p[0], l: p[1] }
            })
        }
    }

    function chaine(v) { return { type: "string", value: String(v) } }
    function ajouter(texte) { doc.commande(".uno:InsertAnnotation", { Text: chaine(texte) }); relire.restart() }
    function modifier(id, texte) { doc.commande(".uno:EditAnnotation", { Id: chaine(id), Text: chaine(texte) }); relire.restart() }
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
