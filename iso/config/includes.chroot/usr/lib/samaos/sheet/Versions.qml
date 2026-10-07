// Les versions du classeur (le bouton Versions de l'en-tête, maquette « En-tête Sheet ») : une copie du fichier à chaque
// enregistrement, et à l'ouverture s'il a changé depuis (un fichier reçu, modifié ailleurs), gardée sur l'ordinateur
// par versions.py, jamais dans le fichier. Ce qui a changé depuis l'une d'elles (ou par rapport à un autre fichier) :
// la macro SamaVersions.Comparer, ligne par ligne dans les tableaux. Restaurer (l'état d'avant est gardé d'abord),
// ouvrir une copie, nommer.
import QtQuick
import Sama.Moteur
import "../reglages"

Item {
    id: versions
    visible: false
    required property var doc
    required property var tableaux
    signal message(string texte)

    readonly property string outil: "/usr/libexec/samaos/versions.py"
    readonly property string extension: doc.chemin ? doc.chemin.split(".").pop().toLowerCase() : ""
    // (un classeur enregistré, dans un format de classeur)
    readonly property bool possibles: !!doc.chemin && ["xlsx", "ods", "xls", "csv"].indexOf(extension) >= 0
    // [{ id, quand, raison, nom, source, taille, fichier }], les plus récentes d'abord
    property var liste: []
    // La comparaison montrée : { id, fichier, nomFichier, etat : « attente » | « prete » | « erreur », total,
    // changements, erreur }
    property var comparaison: null

    Commande { id: commande }
    function lancer(args, suite) {
        // (« # » : chaque appel est une commande à part, même répété)
        commande.lancer(outil + " " + args.map(commande.q).join(" ") + " #" + Date.now(), function (sortie, code) {
            var r = null
            try { r = JSON.parse(sortie) } catch (e) {}
            if (suite) suite(r, code)
        })
    }

    function charger() {
        if (!possibles) { liste = []; return }
        lancer(["liste", doc.chemin], function (r) { versions.liste = r && r.versions ? r.versions : [] })
    }
    function garder(raison) {
        if (!possibles) return
        lancer(["garder", doc.chemin, raison], function () { versions.charger() })
    }

    // ——— Comparer ———
    function comparer(v) {
        comparaison = { id: v.id, fichier: v.fichier, nomFichier: "", etat: "attente" }
        demanderComparaison()
    }
    function comparerFichier(chemin) {
        comparaison = { id: "fichier", fichier: chemin, nomFichier: chemin.split("/").pop(), etat: "attente" }
        demanderComparaison()
    }
    // (après des changements dans le classeur)
    function recomparer() {
        if (!comparaison) return
        var c = Object.assign({}, comparaison)
        c.etat = "attente"
        comparaison = c
        demanderComparaison()
    }
    function demanderComparaison() {
        var voulue = comparaison
        tableaux.appeler("SamaVersions.Comparer", [voulue.fichier], function (ok, v) {
            if (comparaison !== voulue) return
            var c = Object.assign({}, voulue)
            if (!ok || !v || v.charAt(0) === "!") {
                c.etat = "erreur"
                c.erreur = v && v.charAt(0) === "!" ? v.slice(1) : "Sama n'a pas pu comparer"
            } else {
                try {
                    var r = JSON.parse(v)
                    c.etat = "prete"
                    c.total = r.total
                    c.changements = r.changements
                } catch (e) { c.etat = "erreur"; c.erreur = "Sama n'a pas pu comparer" }
            }
            comparaison = c
        })
    }
    function oublierComparaison() { comparaison = null }

    // ——— Restaurer, ouvrir une copie, nommer ———
    // (des modifications pas encore enregistrées : on enregistre d'abord, pour qu'elles soient dans une version)
    property var restaurationApres: null
    function restaurer(v) {
        if (doc.modifie) { restaurationApres = v; doc.enregistrer(); return }
        faireRestaurer(v)
    }
    function faireRestaurer(v) {
        var c = doc.chemin
        lancer(["restaurer", c, v.id], function (r) {
            if (!r || !r.restauree) { versions.message("La version n'a pas pu être restaurée"); return }
            comparaison = null
            doc.ouvrir(c)
            versions.message("Version " + quandCourt(v.quand) + " restaurée · l'état d'avant est gardé dans les versions")
            versions.charger()
        })
    }
    function ouvrirCopie(v) {
        var dossier = doc.chemin.substring(0, doc.chemin.lastIndexOf("/"))
        var base = doc.chemin.split("/").pop().replace(/\.[^.]*$/, "")
        var d = new Date(v.quand * 1000)
        var destination = doc.cheminLibre(dossier, base + " (version du " + d.getDate() + " " + moisCourts[d.getMonth()] + " " + heure(v.quand).replace(":", " h ") + ")", extension)
        lancer(["copier", doc.chemin, v.id, destination], function (r) {
            if (!r || !r.copie) { versions.message("La copie n'a pas pu être faite"); return }
            fenetre.ouvrirFichier(r.copie)
            versions.message("Copie enregistrée à côté du classeur : " + r.copie.split("/").pop())
        })
    }
    function nommer(v, nom) { lancer(["nommer", doc.chemin, v.id, nom], function () { versions.charger() }) }

    // ——— Les mots ———
    readonly property var mois: ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]
    readonly property var moisCourts: ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."]
    readonly property var joursSemaine: ["dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"]
    function deux(n) { return (n < 10 ? "0" : "") + n }
    function heure(q) { var d = new Date(q * 1000); return deux(d.getHours()) + ":" + deux(d.getMinutes()) }
    function memeJour(a, b) { return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate() }
    function jour(q) {
        var d = new Date(q * 1000), m = new Date(), h = new Date(m.getTime() - 86400000)
        if (memeJour(d, m)) return "Aujourd'hui"
        if (memeJour(d, h)) return "Hier"
        var s = joursSemaine[d.getDay()] + " " + d.getDate() + " " + mois[d.getMonth()] + (d.getFullYear() !== m.getFullYear() ? " " + d.getFullYear() : "")
        return s.charAt(0).toUpperCase() + s.slice(1)
    }
    // « de 14:32 », « d'hier à 14:32 », « du 5 oct. à 14:32 »
    function quandCourt(q) {
        var d = new Date(q * 1000), m = new Date()
        if (memeJour(d, m)) return "de " + heure(q)
        if (memeJour(d, new Date(m.getTime() - 86400000))) return "d'hier à " + heure(q)
        return "du " + d.getDate() + " " + moisCourts[d.getMonth()] + " à " + heure(q)
    }
    function versionDe(id) {
        for (var i = 0; i < liste.length; i++) if (liste[i].id === id) return liste[i]
        return null
    }
    function raison(v) {
        if (v.raison === "ouverture") return "À l'ouverture du fichier"
        if (v.raison === "avant-restauration") return "Avant une restauration"
        if (v.raison === "restauree") {
            var s = versionDe(v.source)
            return s ? "Version " + quandCourt(s.quand) + " rétablie" : "Une version rétablie"
        }
        return "Enregistrée"
    }

    Connections {
        target: versions.doc
        function onEtatChanged() {
            versions.comparaison = null
            if (versions.doc.etat === DocumentLO.Pret && versions.possibles) versions.garder("ouverture")
            else if (!versions.possibles) versions.liste = []
        }
        function onEnregistre(reussi, c) {
            if (!reussi || c !== versions.doc.chemin || !versions.possibles) {
                versions.restaurationApres = null
                return
            }
            if (versions.restaurationApres) {
                var v = versions.restaurationApres
                versions.restaurationApres = null
                versions.faireRestaurer(v)
            } else versions.garder("enregistrement")
        }
    }
}
