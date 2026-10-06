// Les commandes de Sama Sheet, en un seul endroit : les menus, la barre d'outils et « Que voulez-vous faire ? »
// (Ctrl+K) les lancent par leur clé. Chaque commande : nom affiché, menu, mots pour la retrouver, raccourci.
import QtQuick
import "Fonctions.js" as Fonctions

QtObject {
    id: actions
    required property var doc
    required property var fenetre

    function uno(c, args) { doc.commande(c, args || {}) }

    // Historique : annuler et rétablir « en réparation » (les changements des macros de Sama s'annulent aussi ; le
    // moteur les grise sinon). Ce qu'on peut annuler vient de la liste des actions du moteur.
    property bool peutAnnuler: false
    property bool peutRetablir: false
    readonly property var reparer: ({ Repair: { type: "boolean", value: true } })
    property Timer relireHistorique: Timer { interval: 300; onTriggered: { actions.doc.demanderValeurs(".uno:Undo"); actions.doc.demanderValeurs(".uno:Redo") } }
    property Connections suivre: Connections {
        target: actions.doc
        function onRevisionChanged() { actions.relireHistorique.restart() }
        function onModifieChanged() { actions.relireHistorique.restart() }
        function onValeurs(commande, reponse) {
            var n = reponse && reponse.actions && reponse.actions.length ? reponse.actions.length : 0
            if (commande === ".uno:Undo") actions.peutAnnuler = n > 0
            else if (commande === ".uno:Redo") actions.peutRetablir = n > 0
        }
    }
    function chaine(nom, valeur) { var a = {}; a[nom] = { type: "string", value: String(valeur) }; return a }

    // ——— Couleurs et bordures des cellules ———
    // (« » : automatique ou aucun remplissage, -1 pour le moteur)
    function valeurCouleur(c) { return c ? parseInt(c.slice(1), 16) : -1 }
    function colorerTexte(c) { uno(".uno:Color", { Color: { type: "long", value: valeurCouleur(c) } }) }
    function colorerFond(c) { uno(".uno:BackgroundColor", { BackgroundColor: { type: "long", value: valeurCouleur(c) } }) }
    // Couleur annoncée par le moteur pour la cellule courante (« -1 » : automatique, aucun remplissage)
    function couleurCellule(commande, defaut) {
        var e = doc.etats[commande]
        var n = Number(e)
        return e === undefined || e === "" || isNaN(n) || n < 0 ? defaut : "#" + ("000000" + n.toString(16)).slice(-6)
    }
    // Bordures : épaisseur en centièmes de millimètre (26 : fine, 53 : épaisse ; 0 : aucune) ; « valides » : lignes à
    // changer, les autres restent (1 haut, 2 bas, 4 gauche, 8 droite, 16 lignes intérieures horizontales, 32 verticales)
    function trait(epaisseur) {
        return { type: "com.sun.star.table.BorderLine2", value: {
            Color: { type: "com.sun.star.util.Color", value: 0x1F1C18 },
            InnerLineWidth: { type: "short", value: 0 }, OuterLineWidth: { type: "short", value: epaisseur },
            LineDistance: { type: "short", value: 0 }, LineStyle: { type: "short", value: 0 },
            LineWidth: { type: "unsigned long", value: 0 } } }
    }
    function poserBordures(dehors, dedans, valides) {
        var zero = { type: "long", value: 0 }
        uno(".uno:SetBorderStyle", {
            OuterBorder: { type: "[]any", value: [trait(dehors), trait(dehors), trait(dehors), trait(dehors), zero, zero, zero, zero, zero] },
            InnerBorder: { type: "[]any", value: [trait(dedans), trait(dedans), { type: "short", value: 0 }, { type: "short", value: valides }, zero] }
        })
    }
    function bordures(genre) {
        if (genre === "toutes") poserBordures(26, 26, 63)
        else if (genre === "contour") poserBordures(26, 0, 15)
        else if (genre === "epais") poserBordures(53, 0, 15)
        else if (genre === "bas") poserBordures(26, 0, 2)
        else poserBordures(0, 0, 63)
    }
    // Calcul automatique sur les cases au-dessus ou à gauche (somme, moyenne, min, max, nombre)
    function calculAuto(fonction) { uno(".uno:AutoSum", fonction === "sum" ? {} : chaine("Function", fonction)) }

    // ——— Le catalogue ———
    function c(cle, nom, menu, mots, raccourci, faire, actif) {
        return { cle: cle, nom: nom, menu: menu, mots: mots || "", raccourci: raccourci || "", faire: faire, actif: actif }
    }
    readonly property var catalogue: [
        c("nouveau", "Nouveau classeur", "Fichier", "créer vierge", "Ctrl+N", function () { fenetre.nouvelleFenetre() }),
        c("ouvrir", "Ouvrir…", "Fichier", "fichier excel", "Ctrl+O", function () { fenetre.ouvrirDialogue("ouvrir") }),
        c("enregistrer", "Enregistrer", "Fichier", "sauver sauvegarder", "Ctrl+S", function () { fenetre.enregistrer() }),
        c("enregistrerSous", "Enregistrer sous…", "Fichier", "copie autre nom format ods csv", "Ctrl+Maj+S", function () { fenetre.ouvrirDialogue("enregistrer") }),
        c("pdf", "Exporter en PDF…", "Fichier", "imprimer pdf envoyer", "", function () { fenetre.ouvrirDialogue("pdf") }),
        c("fermer", "Fermer", "Fichier", "quitter", "Ctrl+W", function () { fenetre.close() }),

        c("annuler", "Annuler", "Édition", "défaire retour", "Ctrl+Z", function () { uno(".uno:Undo", reparer) }, function () { return peutAnnuler || doc.etats[".uno:Undo"] === "enabled" }),
        c("retablir", "Rétablir", "Édition", "refaire", "Ctrl+Y", function () { uno(".uno:Redo", reparer) }, function () { return peutRetablir || doc.etats[".uno:Redo"] === "enabled" }),
        c("couper", "Couper", "Édition", "déplacer", "Ctrl+X", function () { doc.copier(true) }),
        c("copier", "Copier", "Édition", "dupliquer", "Ctrl+C", function () { doc.copier(false) }),
        c("coller", "Coller", "Édition", "", "Ctrl+V", function () { doc.coller(false) }),
        c("collerValeurs", "Coller les valeurs seulement", "Édition", "sans formule sans mise en forme", "Ctrl+Maj+V", function () { doc.coller(true) }),
        c("effacer", "Effacer le contenu", "Édition", "vider supprimer", "Suppr", function () { doc.touche(1286) }),
        c("supprimerLigne", "Supprimer la ligne", "Édition", "enlever ligne", "", function () { uno(".uno:DeleteRows") }),
        c("supprimerColonne", "Supprimer la colonne", "Édition", "enlever colonne", "", function () { uno(".uno:DeleteColumns") }),
        c("toutSelectionner", "Tout sélectionner", "Édition", "", "Ctrl+A", function () { uno(".uno:SelectAll") }),

        c("formules", "Afficher les formules", "Affichage", "voir calculs", "", function () { uno(".uno:ToggleFormula") }),
        c("figerLigne", "Figer la première ligne", "Affichage", "bloquer en-tête titre", "", function () { uno(".uno:FreezePanesFirstRow") }),
        c("figerColonne", "Figer la première colonne", "Affichage", "bloquer", "", function () { uno(".uno:FreezePanesFirstColumn") }),
        c("figer", "Figer jusqu'à la case choisie", "Affichage", "bloquer volets", "", function () { uno(".uno:FreezePanes") }),
        c("zoomPlus", "Agrandir", "Affichage", "zoom plus gros", "Ctrl++", function () { doc.zoom = doc.zoom * 1.1 }),
        c("zoomMoins", "Réduire", "Affichage", "zoom plus petit", "Ctrl+-", function () { doc.zoom = doc.zoom / 1.1 }),
        c("zoom100", "Taille réelle (100 %)", "Affichage", "zoom", "Ctrl+0", function () { doc.zoom = 1 }),

        c("ligneAvant", "Ligne au-dessus", "Insertion", "ajouter insérer ligne", "", function () { uno(".uno:InsertRowsBefore") }),
        c("ligneApres", "Ligne en dessous", "Insertion", "ajouter insérer ligne", "", function () { uno(".uno:InsertRowsAfter") }),
        c("colonneAvant", "Colonne à gauche", "Insertion", "ajouter insérer colonne", "", function () { uno(".uno:InsertColumnsBefore") }),
        c("colonneApres", "Colonne à droite", "Insertion", "ajouter insérer colonne", "", function () { uno(".uno:InsertColumnsAfter") }),
        c("feuille", "Nouvelle feuille", "Insertion", "ajouter onglet page", "", function () { fenetre.ajouterFeuille() }),
        c("graphique", "Graphique…", "Insertion", "courbe diagramme camembert histogramme barres", "", function () { fenetre.ouvrirVolet("graphique") }),
        c("date", "Date du jour", "Insertion", "aujourd'hui", "Ctrl+;", function () { uno(".uno:InsertCurrentDate") }),
        c("somme", "Somme", "Insertion", "total additionner sigma", "Alt+=", function () { calculAuto("sum") }),
        c("moyenne", "Moyenne", "Insertion", "", "", function () { calculAuto("average") }),
        c("minimum", "Minimum", "Insertion", "plus petit", "", function () { calculAuto("min") }),
        c("maximum", "Maximum", "Insertion", "plus grand", "", function () { calculAuto("max") }),
        c("nombre", "Nombre de valeurs", "Insertion", "compter combien", "", function () { calculAuto("count") }),

        c("gras", "Gras", "Format", "épais", "Ctrl+B", function () { uno(".uno:Bold") }),
        c("italique", "Italique", "Format", "penché", "Ctrl+I", function () { uno(".uno:Italic") }),
        c("souligne", "Souligné", "Format", "", "Ctrl+U", function () { uno(".uno:Underline") }),
        c("barre", "Barré", "Format", "rayer", "", function () { uno(".uno:Strikeout") }),
        c("couleurTexte", "Couleur du texte…", "Format", "couleur écriture", "", function () { fenetre.ouvrirVolet("texte") }),
        c("remplissage", "Couleur de remplissage…", "Format", "fond couleur case", "", function () { fenetre.ouvrirVolet("fond") }),
        c("bordures", "Bordures…", "Format", "cadre traits lignes", "", function () { fenetre.ouvrirVolet("bordures") }),
        c("fcfa", "Montant en francs CFA", "Format", "monnaie argent fcfa xof prix", "", function () { uno(".uno:NumberFormatCurrency"); uno(".uno:SetOptimalColumnWidthDirect") }),
        c("pourcentage", "Pourcentage", "Format", "pour cent", "", function () { uno(".uno:NumberFormatPercent") }),
        c("nombreFormat", "Nombre avec séparateur des milliers", "Format", "chiffres", "", function () { uno(".uno:NumberFormatDecimal") }),
        c("dateFormat", "Date", "Format", "jour mois", "", function () { uno(".uno:NumberFormatDate") }),
        c("heureFormat", "Heure", "Format", "", "", function () { uno(".uno:NumberFormatTime") }),
        c("formatStandard", "Format automatique", "Format", "standard normal", "", function () { uno(".uno:NumberFormatStandard") }),
        c("decimalesPlus", "Une décimale de plus", "Format", "virgule", "", function () { uno(".uno:NumberFormatIncDecimals") }),
        c("decimalesMoins", "Une décimale de moins", "Format", "virgule arrondi", "", function () { uno(".uno:NumberFormatDecDecimals") }),
        c("gauche", "Aligner à gauche", "Format", "alignement", "", function () { uno(".uno:AlignLeft") }),
        c("centre", "Centrer", "Format", "alignement milieu", "", function () { uno(".uno:AlignHorizontalCenter") }),
        c("droite", "Aligner à droite", "Format", "alignement", "", function () { uno(".uno:AlignRight") }),
        c("haut", "Aligner en haut", "Format", "vertical", "", function () { uno(".uno:AlignTop") }),
        c("milieu", "Centrer verticalement", "Format", "vertical milieu", "", function () { uno(".uno:AlignVCenter") }),
        c("bas", "Aligner en bas", "Format", "vertical", "", function () { uno(".uno:AlignBottom") }),
        c("retourLigne", "Renvoyer à la ligne", "Format", "texte long plusieurs lignes", "", function () { uno(".uno:WrapText") }),
        c("fusionner", "Fusionner les cases", "Format", "regrouper", "", function () { uno(".uno:ToggleMergeCells") }),
        c("largeur", "Ajuster la largeur des colonnes", "Format", "colonne trop étroite ###", "", function () { uno(".uno:SetOptimalColumnWidthDirect") }),
        c("pinceau", "Reproduire la mise en forme", "Format", "pinceau copier format", "", function () { uno(".uno:FormatPaintbrush") }),
        c("effacerFormat", "Effacer la mise en forme", "Format", "enlever style normal", "Ctrl+M", function () { uno(".uno:ResetAttributes") }),

        c("tableau", "Mettre en tableau", "Données", "tableau liste registre colonnes nommées filtre totaux excel", "Ctrl+T", function () { fenetre.tableaux.creer() }, function () { return fenetre.tableaux.courant === null }),
        c("totauxTableau", "Ligne des totaux du tableau", "Données", "total somme tableau", "", function () { fenetre.tableaux.totaux(fenetre.tableaux.courant, !fenetre.tableaux.courant.totaux) }, function () { return fenetre.tableaux.courant !== null }),
        c("ligneTableau", "Ajouter une ligne au tableau", "Données", "nouvelle ligne tableau", "", function () { fenetre.tableaux.ajouterLigne(fenetre.tableaux.courant) }, function () { return fenetre.tableaux.courant !== null }),
        c("trierAZ", "Trier de A à Z, du plus petit au plus grand", "Données", "ordre croissant ranger", "", function () { uno(".uno:SortAscending") }),
        c("trierZA", "Trier de Z à A, du plus grand au plus petit", "Données", "ordre décroissant ranger", "", function () { uno(".uno:SortDescending") }),

        c("aide", "Aide de Sama Sheet", "Aide", "comment faire apprendre", "F1", function () { fenetre.ouvrirAide() })
    ]

    function trouver(cle) {
        for (var i = 0; i < catalogue.length; i++) if (catalogue[i].cle === cle) return catalogue[i]
        return null
    }
    function lancer(cle) {
        var a = trouver(cle)
        if (a && (!a.actif || a.actif())) a.faire()
    }

    // « Que voulez-vous faire ? » : commandes et fonctions dont le nom ou les mots contiennent tous les mots tapés
    // (sans tenir compte des accents)
    readonly property var suggestions: ["somme", "tableau", "fcfa", "trierAZ", "graphique", "figerLigne"]
    function chercher(texte) {
        var mots = Fonctions.normaliser(texte).split(/\s+/).filter(function (m) { return m.length > 0 })
        if (!mots.length) return suggestions.map(function (k) { return trouver(k) })
        var res = []
        catalogue.forEach(function (a) {
            var t = Fonctions.normaliser(a.nom + " " + a.mots + " " + a.menu)
            if (mots.every(function (m) { return t.indexOf(m) >= 0 })) res.push(a)
        })
        Fonctions.liste.forEach(function (f) {
            var t = Fonctions.normaliser("fonction formule " + f.nom + " " + f.texte + " " + f.mots)
            if (mots.every(function (m) { return t.indexOf(m) >= 0 }))
                res.push({ cle: "", nom: "Fonction " + f.nom, menu: f.texte, raccourci: "", faire: function () { fenetre.commencerFormule("=" + f.nom + "()") } })
        })
        // (ceux dont le nom commence par le premier mot d'abord)
        var debut = mots[0]
        res.sort(function (x, y) {
            var a = Fonctions.normaliser(x.nom).indexOf(debut) === 0 ? 0 : 1, b = Fonctions.normaliser(y.nom).indexOf(debut) === 0 ? 0 : 1
            return a - b
        })
        return res.slice(0, 9)
    }
}
