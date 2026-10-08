// Les copies de secours de Sama Docs et Sama Sheet (secours.py) : chaque minute, si le document a changé depuis la
// dernière, le moteur en écrit une copie à part (le document reste où il est, « modifié ») ; quand on enregistre, ou
// qu'on ferme sans enregistrer, elle s'efface. Après une coupure, un plantage ou un arrêt forcé, elle reste : la carte
// du retour du courant et l'accueil la proposent ; rouverte, l'application la reprend (reprendre) et l'enregistre
// à l'emplacement d'origine.
import QtQuick
import Sama.Moteur
import "../reglages"

Item {
    id: secours
    property var doc
    property string appli: ""              // « samaos-docs.desktop »
    property string format: ""             // celui des copies (celui du fichier, ou docx, xlsx)
    property string titre: ""
    property string original: ""           // l'emplacement d'origine (vide : jamais enregistré)
    property bool permis: true             // (pas pendant une saisie dans une case)
    readonly property string outil: "/usr/libexec/samaos/secours.py"

    property string dossier: ""
    property bool enCours: false
    property int revisionCopiee: -1
    property double derniereCopie: Date.now()
    property double dernierChangement: 0
    // La copie reprise après un arrêt imprévu : { titre, original, quand, fichier, dossier } (sinon null)
    property var reprise: null

    Commande { id: commande }
    function lancer(args, suite) {
        commande.lancer(outil + " " + args.map(function (a) { return a === "$PPID" ? a : commande.q(a) }).join(" "), suite)
    }

    function estUneCopie(chemin) { return String(chemin).indexOf("/.local/state/samaos/secours/") >= 0 }

    // Rouvrir une copie : elle devient celle de cette fenêtre
    function reprendre(fichier, suite) {
        lancer(["reprendre", fichier, "$PPID"], function (sortie) {
            var infos = null
            try { infos = JSON.parse(sortie) } catch (e) { }
            if (infos && infos.dossier) {
                secours.dossier = infos.dossier
                secours.reprise = infos
            }
            suite(secours.reprise)
        })
    }

    function ecrire() {
        if (enCours) return
        enCours = true
        if (!dossier) {
            lancer(["dossier", "$PPID", appli], function (sortie, code) {
                var d = sortie.trim()
                if (code !== 0 || d.indexOf("/") !== 0) { enCours = false; return }
                secours.dossier = d
                secours.enCours = false
                secours.ecrire()
            })
            return
        }
        revisionCopiee = doc.revision
        derniereCopie = Date.now()
        doc.copieDeSecours(dossier + "/nouvelle." + format, format)
    }
    Connections {
        target: secours.doc
        function onSecoursEcrit(reussi, chemin) {
            if (!secours.dossier || chemin.indexOf(secours.dossier + "/") !== 0) { secours.enCours = false; return }
            if (!reussi) {
                // (le dossier de la copie a disparu, effacé à la main : la prochaine en fera un autre)
                var d = secours.dossier
                commande.lancer("test -d " + commande.q(d) + " && echo oui", function (sortie) {
                    if (sortie.trim() !== "oui" && secours.dossier === d) secours.dossier = ""
                    secours.enCours = false
                })
                return
            }
            secours.lancer(["noter", secours.dossier, "$PPID", secours.appli, secours.original, secours.titre, secours.format],
                           function () { secours.enCours = false })
        }
        function onRevisionChanged() { secours.dernierChangement = Date.now() }
        function onModifieChanged() { if (!secours.doc.modifie && !secours.reprise) secours.retirer() }
        function onEnregistre(reussi, c) {
            if (reussi && !String(c).toLowerCase().endsWith(".pdf")) {
                secours.reprise = null
                secours.retirer()
            }
        }
        function onEtatChanged() { secours.derniereCopie = Date.now() }
    }
    // (toutes les 10 secondes : une copie si le document a changé, au plus une par minute, quand on s'arrête un instant
    // d'écrire — ou au bout de trois minutes sans arrêt)
    Timer {
        interval: 10000
        repeat: true
        running: secours.doc && secours.doc.etat === DocumentLO.Pret && secours.doc.modifie && secours.format !== ""
        onTriggered: {
            var maintenant = Date.now()
            if (secours.enCours || !secours.permis || secours.doc.revision === secours.revisionCopiee) return
            if (maintenant - secours.derniereCopie < 60000) return
            if (maintenant - secours.dernierChangement < 2500 && maintenant - secours.derniereCopie < 180000) return
            secours.ecrire()
        }
    }

    // La copie ne sert plus ; suite (facultative) quand c'est fait
    function retirer(suite) {
        if (!dossier) { if (suite) suite(); return }
        var d = dossier
        dossier = ""
        revisionCopiee = -1
        lancer(["retirer", d], function () { if (suite) suite() })
    }
    // Fermer l'application : la copie d'abord (au plus une seconde et demie d'attente)
    function quitter() {
        var fait = false
        var fin = function () { if (!fait) { fait = true; Qt.quit() } }
        retirer(fin)
        delaiQuitter.fin = fin
        delaiQuitter.start()
    }
    Timer { id: delaiQuitter; interval: 1500; property var fin: null; onTriggered: if (fin) fin() }

    // Les copies laissées par cette application (accueil : « À récupérer »)
    property var orphelins: []
    function chercherOrphelins() {
        lancer(["orphelins", appli], function (sortie) {
            try { secours.orphelins = JSON.parse(sortie) } catch (e) { secours.orphelins = [] }
        })
    }
    function oublier(copie, suite) {
        lancer(["retirer", copie.dossier], function () { chercherOrphelins(); if (suite) suite() })
    }
}
