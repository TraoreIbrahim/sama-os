// Les commandes de Sama Docs, en un seul endroit : les menus, la barre d'outils et « Que voulez-vous faire ? »
// (Ctrl+K) les lancent par leur clé. Chaque commande : nom affiché, menu, mots pour la retrouver, raccourci.
// (Même forme que celles de Sama Sheet : ses menus et ses boutons servent ici aussi.)
import QtQuick

QtObject {
    id: actions
    required property var doc
    required property var fenetre

    function uno(c, args) { doc.commande(c, args || {}) }
    function chaine(nom, valeur) { var a = {}; a[nom] = { type: "string", value: String(valeur) }; return a }

    // Historique : ce qu'on peut annuler vient de la liste des actions du moteur
    property bool peutAnnuler: false
    property bool peutRetablir: false
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

    // ——— Styles de paragraphe : le nom montré, celui du moteur ———
    readonly property var styles: [
        ["Texte normal", "Standard"], ["Titre", "Title"], ["Sous-titre", "Subtitle"], ["Titre 1", "Heading 1"],
        ["Titre 2", "Heading 2"], ["Titre 3", "Heading 3"], ["Citation", "Quotations"]
    ]
    function appliquerStyle(nomMoteur) {
        uno(".uno:StyleApply", { Style: { type: "string", value: nomMoteur }, FamilyName: { type: "string", value: "ParagraphStyles" } })
    }
    // Le style de la ligne du curseur, en mots (le moteur dit son nom, parfois traduit)
    function styleActuel() {
        var e = String(doc.etats[".uno:StyleApply"] || "").split(";")[0]
        for (var i = 0; i < styles.length; i++)
            if (e === styles[i][1] || e === styles[i][0]) return styles[i][0]
        var traduits = { "Style par défaut": "Texte normal", "Default Paragraph Style": "Texte normal", "Corps de texte": "Texte normal",
                         "Text Body": "Texte normal", "Body Text": "Texte normal", "Titre principal": "Titre", "Sous-titre": "Sous-titre" }
        if (/par d[ée]faut|^Default/i.test(e)) return "Texte normal"
        if (/^Titre ([1-6])$/.test(e) || /^Heading ([1-6])$/.test(e)) return "Titre " + e.slice(-1)
        return traduits[e] || e || "Texte normal"
    }
    function police(nom) { uno(".uno:CharFontName", { "CharFontName.FamilyName": { type: "string", value: nom } }) }
    function taille(n) { uno(".uno:FontHeight", { "FontHeight.Height": { type: "float", value: n } }) }
    function valeurCouleur(c) { return c ? parseInt(c.slice(1), 16) : -1 }
    function colorerTexte(c) { uno(".uno:Color", { Color: { type: "long", value: valeurCouleur(c) } }) }
    function surligner(c) { uno(".uno:CharBackColor", { CharBackColor: { type: "long", value: valeurCouleur(c) } }) }
    // (un tableau de Sama : traits clairs, la ligne des titres teintée et répétée sur chaque page)
    function tableau(colonnes, lignes) { fenetre.appeler("SamaDocs.Tableau", [String(colonnes), String(lignes)]) }

    // ——— Le catalogue ———
    function c(cle, nom, menu, mots, raccourci, faire, actif) {
        return { cle: cle, nom: nom, menu: menu, mots: mots || "", raccourci: raccourci || "", faire: faire, actif: actif }
    }
    readonly property var catalogue: [
        c("nouveau", "Nouveau document", "Fichier", "créer vierge page blanche", "Ctrl+N", function () { fenetre.nouvelleFenetre() }),
        c("modeles", "Nouveau à partir d'un modèle…", "Fichier", "modèle lettre cv compte rendu attestation annonce devoir", "", function () { fenetre.nouvelleFenetre(true) }),
        c("ouvrir", "Ouvrir…", "Fichier", "fichier word docx odt", "Ctrl+O", function () { fenetre.ouvrirDialogue("ouvrir") }),
        c("enregistrer", "Enregistrer", "Fichier", "sauver sauvegarder", "Ctrl+S", function () { fenetre.enregistrer() }),
        c("enregistrerSous", "Enregistrer sous…", "Fichier", "copie autre nom format odt word", "Ctrl+Maj+S", function () { fenetre.ouvrirDialogue("enregistrer") }),
        c("pdf", "Exporter en PDF…", "Fichier", "imprimer pdf envoyer", "", function () { fenetre.ouvrirDialogue("pdf") }),
        c("fermer", "Fermer", "Fichier", "quitter", "Ctrl+W", function () { fenetre.close() }),

        c("annuler", "Annuler", "Édition", "revenir défaire", "Ctrl+Z", function () { uno(".uno:Undo") }, function () { return actions.peutAnnuler }),
        c("retablir", "Rétablir", "Édition", "refaire", "Ctrl+Y", function () { uno(".uno:Redo") }, function () { return actions.peutRetablir }),
        c("couper", "Couper", "Édition", "", "Ctrl+X", function () { doc.copier(true) }),
        c("copier", "Copier", "Édition", "", "Ctrl+C", function () { doc.copier(false) }),
        c("coller", "Coller", "Édition", "", "Ctrl+V", function () { doc.coller(false) }),
        c("collerTexte", "Coller sans la mise en forme", "Édition", "texte seul brut", "Ctrl+Maj+V", function () { doc.coller(true) }),
        c("toutSelectionner", "Tout sélectionner", "Édition", "", "Ctrl+A", function () { uno(".uno:SelectAll") }),

        c("plan", "Le plan du document", "Affichage", "titres sommaire navigation panneau", "", function () { fenetre.planOuvert = !fenetre.planOuvert }),
        c("marge", "Commentaires et suggestions", "Affichage", "marge remarques notes relecture", "", function () { fenetre.page.margeOuverte = !fenetre.page.margeOuverte }),
        c("zoomPlus", "Agrandir", "Affichage", "zoom plus gros", "Ctrl++", function () { doc.zoom = doc.zoom * 1.1 }),
        c("zoomMoins", "Réduire", "Affichage", "zoom plus petit", "Ctrl+-", function () { doc.zoom = doc.zoom / 1.1 }),
        c("zoom100", "Taille réelle (100 %)", "Affichage", "zoom", "Ctrl+0", function () { doc.zoom = 1 }),

        c("tableau", "Tableau", "Insertion", "lignes colonnes grille", "", function () { actions.tableau(3, 3) }),
        c("image", "Image…", "Insertion", "photo illustration logo", "", function () { fenetre.ouvrirDialogue("image") }),
        c("sautPage", "Saut de page", "Insertion", "nouvelle page", "Ctrl+Entrée", function () { uno(".uno:InsertPagebreak") }),
        c("date", "Date du jour", "Insertion", "aujourd'hui", "", function () { uno(".uno:InsertDateField") }),
        c("numeroPage", "Numéro de page", "Insertion", "pagination", "", function () { uno(".uno:InsertPageNumberField") }),
        c("commentaire", "Commentaire", "Insertion", "note remarque", "Ctrl+Alt+M", function () { fenetre.commenter() }),
        c("lien", "Lien…", "Insertion", "adresse site internet url courriel", "", function () { fenetre.page.liens.ouvrirSaisie("") }),

        c("gras", "Gras", "Format", "", "Ctrl+B", function () { uno(".uno:Bold") }),
        c("italique", "Italique", "Format", "", "Ctrl+I", function () { uno(".uno:Italic") }),
        c("souligne", "Souligné", "Format", "", "Ctrl+U", function () { uno(".uno:Underline") }),
        c("barre", "Barré", "Format", "", "", function () { uno(".uno:Strikeout") }),
        c("texteNormal", "Texte normal", "Format", "style paragraphe", "", function () { actions.appliquerStyle("Standard") }),
        c("titre", "Titre du document", "Format", "style grand titre", "", function () { actions.appliquerStyle("Title") }),
        c("titre1", "Titre 1", "Format", "style partie chapitre", "Ctrl+1", function () { actions.appliquerStyle("Heading 1") }),
        c("titre2", "Titre 2", "Format", "style sous-partie", "Ctrl+2", function () { actions.appliquerStyle("Heading 2") }),
        c("titre3", "Titre 3", "Format", "style", "Ctrl+3", function () { actions.appliquerStyle("Heading 3") }),
        c("citation", "Citation", "Format", "style retrait", "", function () { actions.appliquerStyle("Quotations") }),
        c("gauche", "Aligner à gauche", "Format", "alignement", "Ctrl+L", function () { uno(".uno:LeftPara") }),
        c("centre", "Centrer", "Format", "alignement milieu", "Ctrl+E", function () { uno(".uno:CenterPara") }),
        c("droite", "Aligner à droite", "Format", "alignement", "Ctrl+R", function () { uno(".uno:RightPara") }),
        c("justifier", "Justifier", "Format", "alignement", "Ctrl+J", function () { uno(".uno:JustifyPara") }),
        c("puces", "Liste à puces", "Format", "liste points", "", function () { uno(".uno:DefaultBullet") }),
        c("numeros", "Liste numérotée", "Format", "liste numéros 1 2 3", "", function () { uno(".uno:DefaultNumbering") }),
        c("interligne1", "Interligne simple", "Format", "espacement lignes", "", function () { uno(".uno:SpacePara1") }),
        c("interligne15", "Interligne 1,5", "Format", "espacement lignes", "", function () { uno(".uno:SpacePara15") }),
        c("interligne2", "Interligne double", "Format", "espacement lignes", "", function () { uno(".uno:SpacePara2") }),
        c("effacerFormat", "Effacer la mise en forme", "Format", "enlever retirer", "Ctrl+Espace", function () { uno(".uno:ResetAttributes") }),

        c("suggestion", "Mode Suggestion", "Outils", "suivi des modifications proposer relecture changements", "", function () { fenetre.choisirMode(fenetre.mode === "Suggestion" ? "Modification" : "Suggestion") }),
        c("orthographe", "Souligner les fautes", "Outils", "orthographe correcteur", "", function () { fenetre.orthographe = !fenetre.orthographe }),
        c("toutAccepter", "Accepter toutes les suggestions", "Outils", "suivi modifications valider relecture", "", function () { fenetre.page.marge.toutAccepter() }, function () { return fenetre.page.marge.groupes.length > 0 }),
        c("toutRefuser", "Refuser toutes les suggestions", "Outils", "suivi modifications rejeter relecture", "", function () { fenetre.page.marge.toutRefuser() }, function () { return fenetre.page.marge.groupes.length > 0 }),

        c("aide", "Aide de Sama", "Aide", "comment faire", "F1", function () { fenetre.ouvrirAide("") }),
        c("chercher", "Que voulez-vous faire ?", "Aide", "commande chercher", "Ctrl+K", function () { fenetre.chercherCommande() })
    ]
    readonly property var pictos: ({
        nouveau: "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M14 3v5h5",
        modeles: "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z",
        ouvrir: "M3 7h6l2 2h10v10H3z",
        enregistrer: "M5 4h11l3 3v13H5z M8 4v5h7V4 M8 20v-6h8v6",
        enregistrerSous: "M5 4h11l3 3v13H5z M8 4v5h7V4 M12 13v5 M9.5 15.5l2.5 2.5 2.5-2.5",
        pdf: "M7 3h7l5 5v13H7z M14 3v5h5 M9 14h6 M9 17h4",
        fermer: "M7 7l10 10 M17 7L7 17",
        annuler: "M9 14L4 9l5-5 M4 9h10a6 6 0 0 1 0 12h-3",
        retablir: "M15 14l5-5-5-5 M20 9H10a6 6 0 0 0 0 12h3",
        couper: "M6 7a2 2 0 1 0 0.01 0z M6 17a2 2 0 1 0 0.01 0z M8 8l12 8 M8 16L20 8",
        copier: "M8 8h11v12H8z M5 16V4h11",
        coller: "M8 4h8v3H8z M6 6H5v15h14V6h-1",
        collerTexte: "M8 4h8v3H8z M6 6H5v15h14V6h-1 M9 12h6 M9 16h4",
        plan: "M4 6h16 M7 11h13 M7 16h13 M4 11v.1 M4 16v.1",
        tableau: "M4 5h16v14H4z M4 10h16 M10 5v14",
        image: "M4 5h16v14H4z M4 16l5-5 4 4 3-3 4 4 M15 9v.1",
        sautPage: "M6 3v6h12V3 M6 21v-6h12v6 M3 12h3 M9 12h2 M14 12h2 M19 12h2",
        date: "M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4",
        numeroPage: "M7 3h10v18H7z M11 15h2 M12 15v-5l-1 1",
        commentaire: "M4 5h16v11H9l-5 4z M12 8v5 M9.5 10.5h5",
        marge: "M4 4h16v16H4z M14 4v16 M16.5 8h1.5 M16.5 11h1.5",
        lien: "M10 14a4 4 0 0 0 6 0l3-3a4 4 0 0 0-6-6l-1 1 M14 10a4 4 0 0 0-6 0l-3 3a4 4 0 0 0 6 6l1-1",
        toutAccepter: "M5 12.5l4.5 4.5L19 7.5",
        toutRefuser: "M6 6l12 12 M18 6L6 18",
        texteNormal: "M5 6h14 M5 10h14 M5 14h14 M5 18h9",
        titre: "M6 6h12 M12 6v12",
        titre1: "M5 6v12 M12 6v12 M5 12h7 M16 9l2-2v11",
        titre2: "M4 6v12 M11 6v12 M4 12h7 M15 9a2 2 0 0 1 4 0c0 2-4 4-4 9h4",
        titre3: "M4 6v12 M11 6v12 M4 12h7 M15 7h4l-2 4a3 3 0 1 1-2 5",
        citation: "M7 8h4v5H8l-1 3 M14 8h4v5h-3l-1 3",
        gauche: "M4 6h16 M4 10h10 M4 14h16 M4 18h10",
        centre: "M4 6h16 M7 10h10 M4 14h16 M7 18h10",
        droite: "M4 6h16 M10 10h10 M4 14h16 M10 18h10",
        justifier: "M4 6h16 M4 10h16 M4 14h16 M4 18h16",
        puces: "M9 7h11 M9 12h11 M9 17h11 M4.5 7v.1 M4.5 12v.1 M4.5 17v.1",
        numeros: "M10 7h10 M10 12h10 M10 17h10 M4 5l1.5-1v5 M3.5 14.5a1.5 1.5 0 0 1 3 0c0 1-3 2-3 3.5h3",
        interligne1: "M10 6h10 M10 12h10 M10 18h10 M5 5v14 M3 7l2-2 2 2 M3 17l2 2 2-2",
        interligne15: "M10 6h10 M10 12h10 M10 18h10 M5 5v14 M3 7l2-2 2 2 M3 17l2 2 2-2",
        interligne2: "M10 6h10 M10 12h10 M10 18h10 M5 5v14 M3 7l2-2 2 2 M3 17l2 2 2-2",
        effacerFormat: "M6 5h12 M12 5l-3 14 M5 19l14-14",
        suggestion: "M14 4l6 6L10 20H4v-6z",
        orthographe: "M4 16l4-10 4 10 M5.5 12h5 M14 19c1.5-1 2.5-1 4 0s2.5 1 4 0",
        aide: "M12 3a9 9 0 1 0 0.01 0z M9.5 9.5a2.5 2.5 0 0 1 5 0c0 2-2.5 2-2.5 4 M12 17v.1",
        chercher: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4"
    })
    // (les noms courts, sous les tuiles des menus)
    readonly property var courts: ({ annuler: "Annuler", retablir: "Rétablir", couper: "Couper", copier: "Copier", coller: "Coller" })
    readonly property var details: ({
        modeles: "Lettre officielle, CV, compte rendu, attestation…",
        collerTexte: "Le texte seul, dans le style du document",
        plan: "Les titres du document, à gauche",
        tableau: "Trois colonnes, trois lignes, à compléter",
        sautPage: "La suite commence sur une nouvelle page",
        commentaire: "Une question, une remarque dans la marge",
        marge: "À droite de la page, à la hauteur de leur texte",
        lien: "Sur le texte choisi ; un clic dessus dit où il mène",
        toutAccepter: "Les changements proposés entrent dans le texte",
        titre1: "Une partie : elle apparaît dans le plan",
        suggestion: "Vos changements sont proposés, pas imposés",
        orthographe: "Les mots inconnus soulignés en rouge"
    })
    function trouver(cle) {
        for (var i = 0; i < catalogue.length; i++) if (catalogue[i].cle === cle) return catalogue[i]
        return null
    }
    function lancer(cle) {
        var a = trouver(cle)
        if (a && (!a.actif || a.actif())) a.faire()
    }

    // « Que voulez-vous faire ? » : les commandes dont le nom ou les mots contiennent tous les mots tapés (sans tenir
    // compte des accents)
    function normaliser(s) { return String(s).toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "") }
    readonly property var suggestions: ["titre1", "tableau", "image", "pdf", "suggestion", "numeros"]
    function chercher(texte) {
        var mots = normaliser(texte).split(/\s+/).filter(function (m) { return m.length > 0 })
        if (!mots.length) return suggestions.map(function (k) { return trouver(k) })
        var res = catalogue.filter(function (a) {
            var t = normaliser(a.nom + " " + a.mots + " " + a.menu)
            return mots.every(function (m) { return t.indexOf(m) >= 0 })
        })
        var debut = mots[0]
        res.sort(function (x, y) { return (normaliser(x.nom).indexOf(debut) === 0 ? 0 : 1) - (normaliser(y.nom).indexOf(debut) === 0 ? 0 : 1) })
        return res.slice(0, 9)
    }
}
