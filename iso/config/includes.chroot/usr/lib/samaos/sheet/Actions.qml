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
        c("modeles", "Nouveau à partir d'un modèle…", "Fichier", "modèle accueil cotisations stock facture budget notes", "", function () {
            if (!doc.chemin && !doc.modifie) fenetre.accueilOuvert = true
            else fenetre.nouvelleFenetre()
        }),
        c("ouvrir", "Ouvrir…", "Fichier", "fichier excel", "Ctrl+O", function () { fenetre.ouvrirDialogue("ouvrir") }),
        c("enregistrer", "Enregistrer", "Fichier", "sauver sauvegarder", "Ctrl+S", function () { fenetre.enregistrer() }),
        c("enregistrerSous", "Enregistrer sous…", "Fichier", "copie autre nom format ods csv", "Ctrl+Maj+S", function () { fenetre.ouvrirDialogue("enregistrer") }),
        c("pdf", "Exporter en PDF…", "Fichier", "imprimer pdf envoyer", "", function () { fenetre.ouvrirDialogue("pdf") }),
        c("envoyer", "Envoyer en image…", "Fichier", "envoyer partager image bilan whatsapp photo pdf", "", function () { fenetre.envoyer() }),
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
        c("grColonnes", "Graphique en colonnes", "Insertion", "graphique histogramme diagramme", "", function () { doc.insererGraphique(0) }),
        c("grBarres", "Graphique en barres", "Insertion", "graphique diagramme horizontal", "", function () { doc.insererGraphique(1) }),
        c("grLignes", "Graphique en lignes", "Insertion", "graphique courbe évolution", "", function () { doc.insererGraphique(5) }),
        c("grSecteurs", "Graphique en secteurs", "Insertion", "graphique camembert part", "", function () { doc.insererGraphique(2) }),
        c("grAires", "Graphique en aires", "Insertion", "graphique surface", "", function () { doc.insererGraphique(4) }),
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
        c("fiches", "Voir le tableau en fiches", "Données", "fiche formulaire saisie carte", "", function () { fenetre.voirFiches(fenetre.tableaux.courant) }, function () { return fenetre.tableaux.courant !== null }),
        c("formulaire", "Remplir par un formulaire", "Données", "formulaire questions saisie enquête inscription google forms", "", function () { fenetre.voirFormulaire(fenetre.tableaux.courant) }, function () { return fenetre.tableaux.courant !== null }),
        c("ligneTableau", "Ajouter une ligne au tableau", "Données", "nouvelle ligne tableau", "", function () { fenetre.tableaux.ajouterLigne(fenetre.tableaux.courant) }, function () { return fenetre.tableaux.courant !== null }),
        c("analyse", "Analyse rapide", "Données", "barres couleurs mise en forme conditionnelle plus grands moyenne totaux graphique", "Ctrl+Q", function () { grille.analyse.ouvrir() }),
        c("trierAZ", "Trier de A à Z, du plus petit au plus grand", "Données", "ordre croissant ranger", "", function () { uno(".uno:SortAscending") }),
        c("trierZA", "Trier de Z à A, du plus grand au plus petit", "Données", "ordre décroissant ranger", "", function () { uno(".uno:SortDescending") }),

        c("aide", "Aide de Sama Sheet", "Aide", "comment faire apprendre", "F1", function () { fenetre.ouvrirAide() }),
        c("chercher", "Que voulez-vous faire ?", "Aide", "chercher commande fonction", "Ctrl+K", function () { barreMenus.recherche.ouvrir() })
    ]

    // ——— Pour les menus : pictogrammes (tracés 24 × 24), une ligne d'explication pour les commandes qui en ont besoin, et
    // les noms courts des tuiles et des pastilles ———
    readonly property var pictos: ({
        nouveau: "M6 3h8l4 4v14H6z M14 3v4h4 M12 11v6 M9 14h6",
        modeles: "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z",
        ouvrir: "M3 7h6l2 2h10v10H3z",
        enregistrer: "M5 4h11l3 3v13H5z M8 4v5h7V4 M8 20v-6h8v6",
        enregistrerSous: "M5 4h11l3 3v4 M5 4v16h6 M8 4v5h7V4 M17 14v7 M13.5 17.5h7",
        pdf: "M6 3h8l4 4v14H6z M14 3v4h4 M9 13h6 M9 17h4",
        fermer: "M7 7l10 10 M17 7L7 17",
        envoyer: "M21 3L10 14 M21 3l-7 18-4-7-7-4z",
        annuler: "M9 14L4 9l5-5 M4 9h10a6 6 0 0 1 0 12h-3",
        retablir: "M15 14l5-5-5-5 M20 9H10a6 6 0 0 0 0 12h3",
        couper: "M6 4a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z M6 15a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z M8.2 7.8L20 16 M8.2 16.2L20 8",
        copier: "M8 8h11v12H8z M5 16V4h11",
        coller: "M9 3h6v3H9z M7 4.5H5V21h14V4.5h-2 M9 12h6 M9 16h4",
        collerValeurs: "M9 3h6v3H9z M7 4.5H5V21h14V4.5h-2 M10.5 12.5l2-1.5v7",
        effacer: "M4 15l8-8 6 6-5.5 5.5H8.5z M8.5 10.5l6 6 M13 19h7",
        supprimerLigne: "M3 9h18v6H3z M10 10.5l4 3 M14 10.5l-4 3",
        supprimerColonne: "M9 3h6v18H9z M10 10l4 4 M14 10l-4 4",
        toutSelectionner: "M4 8V4h4 M16 4h4v4 M20 16v4h-4 M8 20H4v-4 M8.5 8.5h7v7h-7z",
        formules: "M12.5 5.5c-2 0-2.5 1-3 3.5L8 17c-.5 2.5-1 3.5-3 3.5 M6.5 10h6 M14 11l5.5 7 M19.5 11L14 18",
        figerLigne: "M4 4h16v16H4z M4 8h16 M4 10.5h16",
        figerColonne: "M4 4h16v16H4z M8 4v16 M10.5 4v16",
        figer: "M4 4h16v16H4z M4 9.5h16 M9.5 4v16",
        zoomPlus: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4 M8 11h6 M11 8v6",
        zoomMoins: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4 M8 11h6",
        zoom100: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4 M9.5 9l2-1.2v6.4",
        ligneAvant: "M4 12h16v8H4z M4 16h16 M12 2.5v6 M9 5.5h6",
        ligneApres: "M4 4h16v8H4z M4 8h16 M12 15.5v6 M9 18.5h6",
        colonneAvant: "M12 4h8v16h-8z M16 4v16 M2.5 12h6 M5.5 9v6",
        colonneApres: "M4 4h8v16H4z M8 4v16 M15.5 12h6 M18.5 9v6",
        feuille: "M3 19h18 M5 19V7h6l2 3h6v9 M12 12.5v4.5 M9.75 14.75h4.5",
        graphique: "M4 20V11 M10 20V5 M16 20v-6 M3 20h18",
        date: "M4 6h16v14H4z M4 10h16 M8 3v5 M16 3v5 M8 13.5h3v3H8z",
        grColonnes: "M6 20v-7 M12 20V6 M18 20v-10",
        grBarres: "M4 6h9 M4 12h15 M4 18h6",
        grLignes: "M3.5 17l5-6l4 3.5l8-9",
        grSecteurs: "M11 4a8 8 0 1 0 8 8h-8z M14 2.5v7h7a7 7 0 0 0-7-7z",
        grAires: "M3.5 19.5l5-8l4.5 4l7.5-9.5v13.5z",
        somme: "M17 5H7l6 7-6 7h10",
        moyenne: "M4 17l5-6 4 3 7-8 M4 12.5h16",
        minimum: "M12 5v14 M6.5 13.5L12 19l5.5-5.5",
        maximum: "M12 19V5 M6.5 10.5L12 5l5.5 5.5",
        nombre: "M10 4L8 20 M16 4l-2 16 M5 9h15 M4 15h15",
        gras: "M7 5h6a3.5 3.5 0 0 1 0 7H7z M7 12h7a3.5 3.5 0 0 1 0 7H7z",
        italique: "M10 5h8 M6 19h8 M14 5l-4 14",
        souligne: "M7 4v7a5 5 0 0 0 10 0V4 M5 20h14",
        barre: "M16 7.5c-1-2-2.8-2.7-4.5-2.7c-2.2 0-4 1.2-4 3.2 M8.5 16c1 2 2.8 3 4.5 3c2.2 0 4-1.2 4-3.5c0-1-.3-1.8-1-2.5 M4 12h16",
        couleurTexte: "M7.5 15L12 4l4.5 11 M9.2 11h5.6 M5 20h14",
        remplissage: "M5 11.5L10.5 6l5.5 5.5l-5.5 5.5z M5 11.5h11 M18.5 13.5c.7 1 1.2 1.8 1.2 2.3a1.2 1.2 0 0 1-2.4 0c0-.5.5-1.3 1.2-2.3z M8.5 4l2 2",
        bordures: "M4 4h16v16H4z M4 12h16 M12 4v16",
        gauche: "M4 6h16 M4 10h10 M4 14h16 M4 18h10",
        centre: "M4 6h16 M7 10h10 M4 14h16 M7 18h10",
        droite: "M4 6h16 M10 10h10 M4 14h16 M10 18h10",
        haut: "M4 4h16 M12 8v12 M8 12l4-4 4 4",
        milieu: "M4 12h16 M12 3v5.5 M12 15.5V21 M9 5.5l3 3 3-3 M9 18.5l3-3 3 3",
        bas: "M4 20h16 M12 4v12 M8 12l4 4 4-4",
        retourLigne: "M4 6h16 M4 12h13a3 3 0 0 1 0 6h-4 M15 16l-2 2 2 2 M4 18h5",
        fusionner: "M4 6h16v12H4z M9 12h6 M7 10l-2 2 2 2 M17 10l2 2-2 2",
        largeur: "M4 4v16 M20 4v16 M7.5 12h9 M10 9.5L7.5 12l2.5 2.5 M14 9.5l2.5 2.5-2.5 2.5",
        pinceau: "M5 4h11v5H5z M16 6h3v5h-7v3 M12 14v6",
        effacerFormat: "M5 6h11 M10.5 6L8 18 M14 14l6 6 M20 14l-6 6",
        tableau: "M4 5h16v14H4z M4 10h16 M10 5v14",
        analyse: "M4 5h16v14H4z M4 10h7 M13 9l-3 5h4l-3 5",
        totauxTableau: "M4 5h16v14H4z M4 14.5h16 M7 17h5",
        fiches: "M4 5h7v6H4z M13 5h7v6h-7z M4 13h7v6H4z M13 13h7v6h-7z",
        formulaire: "M5 3h14v18H5z M8 8h8 M8 12h8 M8 16h5",
        ligneTableau: "M12 5v14 M5 12h14",
        trierAZ: "M6 4v15 M3 16l3 3 3-3 M12 10l2.5-6 2.5 6 M12.8 8h3.4 M12 14h5l-5 6h5",
        trierZA: "M6 4v15 M3 16l3 3 3-3 M12 4h5l-5 6h5 M12 20l2.5-6 2.5 6 M12.8 18h3.4",
        aide: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .8-1 1.5v.7 M12 17v.1",
        chercher: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4"
    })
    readonly property var details: ({
        modeles: "Cotisations, stock, facture, budget…",
        enregistrer: "Compatible Excel (.xlsx)",
        pdf: "Pour imprimer ou pour envoyer",
        envoyer: "Un bilan à envoyer, sans les numéros",
        collerValeurs: "Sans les formules ni la mise en forme",
        effacer: "La case reste, son contenu part",
        formules: "Les calculs à la place des résultats",
        figer: "Ce qui est au-dessus et à gauche reste visible",
        tableau: "Titres, filtres et totaux, comme dans Excel",
        fiches: "Une carte par ligne, pour saisir sans erreur",
        formulaire: "Une page claire, question après question",
        analyse: "Barres, couleurs, totaux, graphique : sur les cases choisies",
        totauxTableau: "La somme des colonnes, sans les lignes filtrées",
        largeur: "Quand une case affiche ###",
        pinceau: "Puis cliquez sur les cases à mettre pareil",
        aide: "Comment faire, pas à pas",
        chercher: "Une commande ou une fonction, par son nom"
    })
    readonly property var courts: ({
        ligneAvant: "Au-dessus", ligneApres: "En dessous", colonneAvant: "À gauche", colonneApres: "À droite",
        grColonnes: "Colonnes", grBarres: "Barres", grLignes: "Lignes", grSecteurs: "Secteurs", grAires: "Aires",
        somme: "Somme", moyenne: "Moyenne", minimum: "Min", maximum: "Max", nombre: "Nombre",
        formatStandard: "Automatique", nombreFormat: "1 000", fcfa: "50 000 F", pourcentage: "12 %", dateFormat: "06/10/26",
        heureFormat: "14:30", decimalesPlus: ",00 +", decimalesMoins: ",0 −",
        couleurTexte: "Texte", remplissage: "Remplissage", bordures: "Bordures",
        annuler: "Annuler", retablir: "Rétablir", couper: "Couper", copier: "Copier", coller: "Coller"
    })

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
