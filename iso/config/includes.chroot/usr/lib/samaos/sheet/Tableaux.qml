// Les tableaux du classeur (plages nommées à titres, filtres et ligne des totaux, enregistrées comme des tableaux
// Excel). Sama les lit et les change par les macros SamaTableaux du moteur (usr/lib/samaos/moteur/basic).
import QtQuick
import Sama.Moteur
import "Fonctions.js" as Fonctions

Item {
    id: tableaux
    visible: false
    required property var doc
    signal message(string texte)

    // [{ nom, feuille, c1, l1, c2, l2 (à partir de 0), totaux, colonnes: [titres], filtres: [colonnes filtrées] }]
    property var liste: []
    // Le tableau de la case courante, ou null
    readonly property var courant: {
        for (var i = 0; i < liste.length; i++) {
            var t = liste[i]
            if (t.feuille === doc.partie && doc.colonne >= t.c1 && doc.colonne <= t.c2 && doc.ligne >= t.l1 && doc.ligne <= t.l2) return t
        }
        return null
    }
    // Le tableau vu (celui de la case courante, ou des fiches, du formulaire) et son résumé pour la barre d'état :
    // « <b>9 membres sur 12</b> ont tout versé · il reste 45 000 F à recevoir » (vide si ce n'est pas un tableau de
    // versements)
    property var cible: null
    property string resume: ""
    readonly property string signatureCible: cible ? cible.nom + ":" + cible.l1 + ":" + cible.l2 : ""
    onSignatureCibleChanged: { resume = ""; relireResume.restart() }
    Timer { id: relireResume; interval: 500; onTriggered: tableaux.lireResume() }
    function lireResume() {
        var t = cible
        if (!t) { resume = ""; return }
        appeler("Lignes", [t.nom], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{" || !tableaux.cible || tableaux.cible.nom !== t.nom) return
            try { tableaux.resume = Fonctions.resume(JSON.parse(v)) } catch (e) { tableaux.resume = "" }
        })
    }

    // Ceux de la feuille affichée
    readonly property var visibles: liste.filter(function (t) { return t.feuille === doc.partie })

    function lettres(c) {
        var s = ""
        c = c + 1
        while (c > 0) { var r = (c - 1) % 26; s = String.fromCharCode(65 + r) + s; c = Math.floor((c - 1) / 26) }
        return s
    }
    function adresse(t) { return lettres(t.c1) + (t.l1 + 1) + ":" + lettres(t.c2) + (t.l2 + 1) }
    function lignesDonnees(t) { return t.l2 - t.l1 - (t.totaux ? 1 : 0) }

    // ——— Appels au moteur ———
    property var attentes: ({})
    function appeler(fonction, args, suite) {
        // (« Creer » : une fonction de SamaTableaux ; « SamaModeles.Remplir » : d'un autre module)
        var j = doc.script(fonction.indexOf(".") >= 0 ? fonction : "SamaTableaux." + fonction, args || [])
        if (j >= 0) attentes[j] = suite
    }
    // (« !… » : un message pour la personne)
    function verifier(ok, v) {
        if (!ok || (v && v.charAt(0) === "!")) console.warn("Tableaux :", ok, v)
        if (!ok) { message("Le moteur n'a pas pu faire cette opération"); return false }
        if (v && v.charAt(0) === "!") { message(v.slice(1)); return false }
        return true
    }
    Connections {
        target: tableaux.doc
        function onResultatScript(jeton, reussi, valeur) {
            var suite = tableaux.attentes[jeton]
            delete tableaux.attentes[jeton]
            if (suite) suite(reussi, valeur)
        }
        function onRevisionChanged() { relire.restart(); if (tableaux.cible) relireResume.restart() }
        function onPartiesChanged() { relire.restart() }
        function onEtatChanged() { if (tableaux.doc.etat === DocumentLO.Pret) relire.restart(); else tableaux.liste = [] }
    }
    // (on relit après les changements du contenu, mais pas pendant qu'on écrit dans une case)
    Timer {
        id: relire
        interval: 600
        onTriggered: tableaux.rafraichir()
    }
    function rafraichir() {
        if (doc.etat !== DocumentLO.Pret) return
        if (doc.curseurTexteVisible) { relire.restart(); return }
        appeler("Decrire", [], function (ok, v) {
            if (ok && v && v.charAt(0) === "[") {
                try { liste = JSON.parse(v) } catch (e) { }
            }
        })
    }

    // ——— Opérations ———
    function creer() {
        appeler("Creer", [], function (ok, v) {
            if (!verifier(ok, v)) return
            message("Tableau « " + v + " » : titres, filtres, et la ligne des totaux si besoin")
            rafraichir()
        })
    }
    function totaux(t, oui) {
        appeler("Totaux", [t.nom, oui ? "1" : "0"], function (ok, v) { if (verifier(ok, v)) rafraichir() })
    }
    function ajouterLigne(t) {
        appeler("AjouterLigne", [t.nom], function (ok, v) {
            if (!verifier(ok, v)) return
            // « $Feuille.$B$15 » → B15
            doc.allerA(v.split(".").pop().replace(/\$/g, ""))
            rafraichir()
        })
    }
    function renommer(t, nom) {
        appeler("Renommer", [t.nom, nom], function (ok, v) { if (verifier(ok, v)) rafraichir() })
    }
    function trier(t, colonne, croissant) {
        appeler("Trier", [t.nom, String(colonne), croissant ? "1" : "0"], function (ok, v) { verifier(ok, v) })
    }
    // suite({ valeurs: [...], gardees: [...] ou null })
    function valeurs(t, colonne, suite) {
        appeler("Valeurs", [t.nom, String(colonne)], function (ok, v) {
            if (!verifier(ok, v)) return
            try { suite(JSON.parse(v)) } catch (e) { }
        })
    }
    // ——— Le panneau du tableau ———
    function styler(t, style) {
        appeler("Styler", [t.nom, style], function (ok, v) { if (verifier(ok, v)) rafraichir() })
    }
    // calcul : somme, moyenne, nombre, min, max, aucun
    function totaliser(t, colonne, calcul, suite) {
        appeler("Totaliser", [t.nom, colonne, calcul], function (ok, v) { if (verifier(ok, v)) { rafraichir(); if (suite) suite() } })
    }
    // genre : texte, montant, nombre, date, pourcent
    function formaterColonne(t, colonne, genre, suite) {
        appeler("FormaterColonne", [t.nom, colonne, genre], function (ok, v) { if (verifier(ok, v) && suite) suite() })
    }
    function regler(t, cle, valeur) {
        appeler("Regler", [t.nom, cle, valeur], function (ok, v) { if (verifier(ok, v)) rafraichir() })
    }
    function convertir(t) {
        appeler("Convertir", [t.nom], function (ok, v) {
            if (!verifier(ok, v)) return
            message("« " + t.nom.replace(/_/g, " ") + " » est redevenu des cases ordinaires (Ctrl+Z pour revenir)")
            rafraichir()
        })
    }
    // gardees : valeurs à garder ; null : plus de filtre sur cette colonne
    function filtrer(t, colonne, gardees) {
        appeler("Filtrer", [t.nom, String(colonne), gardees ? gardees.join("\n") : "", gardees ? "0" : "1"],
                function (ok, v) { if (verifier(ok, v)) rafraichir() })
    }
}
