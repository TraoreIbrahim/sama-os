// Les menus du clic droit, dans le style des menus de Sama Sheet (tuiles, titres de partie, raccourcis en pastille) :
// sur les cases (couper, copier, coller ; ajouter ; commentaire ; le tableau ; supprimer), sur un en-tête de colonne
// ou de ligne, sur l'onglet d'une feuille, sur un graphique, et dans une case qu'on écrit. Ce qui ne s'applique pas
// à l'endroit cliqué n'y est pas.
import QtQuick
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: menus
    visible: false
    readonly property var doc: fenetre.doc
    readonly property var actions: fenetre.actions
    readonly property var com: fenetre.commentaires
    readonly property var t: fenetre.tableaux.courant

    function cases(item, x, y) {
        if (doc.objet.width > 0 && !doc.objetActif) ouvrir(menuGraphique, item, x, y)
        else if (doc.curseurTexteVisible) ouvrir(menuTexte, item, x, y)
        else ouvrir(menuCases, item, x, y)
    }
    function colonne(item, x, y) { ouvrir(menuColonnes, item, x, y) }
    function ligne(item, x, y) { ouvrir(menuLignes, item, x, y) }
    function feuille(item, x, y) { ouvrir(menuFeuille, item, x, y) }
    // (posé sur la fenêtre : un en-tête cliqué est refait quand la sélection change, le menu se fermerait avec lui)
    function ouvrir(menu, item, x, y) {
        var p = item.mapToItem(fenetre.contentItem, x, y)
        menu.popup(fenetre.contentItem, p.x, p.y)
    }

    // ——— Pièces : une ligne, un titre, des tuiles, qui ne sont là que « si » ———
    component Element: ElementMenu {
        property bool si: true
        bref: true
        visible: si
        height: si ? implicitHeight : 0
    }
    component Titre: TitreMenu {
        property bool si: true
        visible: si
        height: si ? implicitHeight : 0
    }
    component MenuClic: MenuSama {
        largeur: 316
        // (le clavier revient à la grille, sauf si la commande l'a donné à un champ : un commentaire, un nom)
        onClosed: Qt.callLater(function () {
            var f = fenetre.activeFocusItem
            if (!f || f === fenetre.contentItem) menus.doc.forceActiveFocus()
        })
    }

    // Le tableau de la case : est-elle dans ses données (ni les titres, ni les totaux) ?
    readonly property bool dansLesDonnees: t !== null && doc.ligne > t.l1 && doc.ligne <= (t.totaux ? t.l2 - 1 : t.l2)
    // (la valeur de la case, pour « Filtrer : seulement « Espèces » » ; pas pour un nombre ni une formule, qui
    // s'affichent autrement qu'ils s'écrivent)
    readonly property string valeur: {
        var v = doc.formule
        if (!v || v.charAt(0) === "=" || /^-?[\d\s.,]+$/.test(v)) return ""
        return v.length > 22 ? v.slice(0, 21) + "…" : v
    }
    readonly property bool plusieurs: doc.adresse.indexOf(":") > 0

    // ——— Les cases ———
    MenuClic {
        id: menuCases
        TuilesMenu { menu: menuCases; cles: ["couper", "copier", "coller", "collerValeurs"] }
        Titre { text: "Ajouter" }
        TuilesMenu { menu: menuCases; cles: ["ligneAvant", "ligneApres", "colonneAvant", "colonneApres"] }
        Titre { text: ""; implicitHeight: 9 }
        Element {
            cle: "commentaire"
            text: menus.com.courant ? "Modifier le commentaire" : "Commenter la case"
        }
        Element { cle: "retirerCommentaire"; si: menus.com.courant !== null }

        // Dans un tableau
        Titre { si: menus.t !== null; text: menus.t ? "Tableau « " + menus.t.nom.replace(/_/g, " ") + " »" : "" }
        Element {
            si: menus.dansLesDonnees
            text: menus.valeur ? "Filtrer : seulement « " + menus.valeur + " »" : "Filtrer sur la valeur de cette case"
            picto: "M4 5h16l-6 7.5V18l-4 2v-7.5z"
            onTriggered: fenetre.tableaux.filtrerSur(menus.t, menus.doc.colonne, menus.doc.ligne)
        }
        Element {
            si: menus.t !== null && menus.t.filtres.length > 0
            text: "Afficher toutes les lignes"
            picto: "M4 5h16l-6 7.5V18l-4 2v-7.5z M17 15l4 4 M21 15l-4 4"
            onTriggered: fenetre.tableaux.toutAfficher(menus.t)
        }
        Element {
            si: menus.t !== null
            text: "Trier de A à Z, du plus petit au plus grand"
            picto: menus.actions.pictos.trierAZ
            onTriggered: fenetre.tableaux.trier(menus.t, menus.doc.colonne - menus.t.c1, true)
        }
        Element {
            si: menus.t !== null
            text: "Trier de Z à A, du plus grand au plus petit"
            picto: menus.actions.pictos.trierZA
            onTriggered: fenetre.tableaux.trier(menus.t, menus.doc.colonne - menus.t.c1, false)
        }
        Element { si: menus.t !== null; cle: "fiches" }

        // Hors d'un tableau, plusieurs cases choisies
        Titre { si: menus.t === null && menus.plusieurs; text: ""; implicitHeight: 9 }
        Element { si: menus.t === null && menus.plusieurs; cle: "analyse" }
        Element { si: menus.t === null && menus.plusieurs; cle: "tableau" }

        Titre { text: "Supprimer" }
        TuilesMenu { menu: menuCases; cles: ["effacer", "effacerFormat", "supprimerLigne", "supprimerColonne"] }
    }

    // ——— Pendant qu'on écrit dans une case ———
    MenuClic {
        id: menuTexte
        TuilesMenu { menu: menuTexte; cles: ["couper", "copier", "coller"] }
    }

    // ——— Un graphique ———
    MenuClic {
        id: menuGraphique
        TuilesMenu { menu: menuGraphique; cles: ["couper", "copier", "coller"] }
        Titre { text: ""; implicitHeight: 9 }
        Element {
            text: "Supprimer le graphique"
            picto: "M5 7h14 M10 7V5h4v2 M7 7l1 12h8l1-12"
            onTriggered: menus.doc.touche(1286)
        }
    }

    // ——— En-tête de colonne ———
    MenuClic {
        id: menuColonnes
        TuilesMenu { menu: menuColonnes; cles: ["couper", "copier", "coller", "collerValeurs"] }
        Titre { text: "Ajouter une colonne" }
        TuilesMenu { menu: menuColonnes; cles: ["colonneAvant", "colonneApres"] }
        Titre { text: ""; implicitHeight: 9 }
        Element { cle: "largeur"; text: "Ajuster la largeur au contenu" }
        Element { cle: "masquerColonne"; text: menus.plusieurs && grille.plage.c2 > grille.plage.c1 ? "Masquer les colonnes" : "Masquer la colonne" }
        Element { cle: "afficherColonnes"; si: grille.colonnesMasquees.length > 0 }
        Titre { text: "Supprimer" }
        TuilesMenu { menu: menuColonnes; cles: ["effacer", "effacerFormat", "supprimerColonne"] }
    }

    // ——— En-tête de ligne ———
    MenuClic {
        id: menuLignes
        TuilesMenu { menu: menuLignes; cles: ["couper", "copier", "coller", "collerValeurs"] }
        Titre { text: "Ajouter une ligne" }
        TuilesMenu { menu: menuLignes; cles: ["ligneAvant", "ligneApres"] }
        Titre { text: ""; implicitHeight: 9 }
        Element { cle: "hauteur"; text: "Ajuster la hauteur au contenu" }
        Element { cle: "masquerLigne"; text: menus.plusieurs && grille.plage.l2 > grille.plage.l1 ? "Masquer les lignes" : "Masquer la ligne" }
        Element { cle: "afficherLignes"; si: grille.lignesMasquees.length > 0 }
        Titre { text: "Supprimer" }
        TuilesMenu { menu: menuLignes; cles: ["effacer", "effacerFormat", "supprimerLigne"] }
    }

    // ——— Onglet d'une feuille ———
    MenuClic {
        id: menuFeuille
        Element { cle: "feuille" }
        Element { cle: "renommerFeuille" }
        Element { cle: "dupliquerFeuille" }
        Titre { text: "Déplacer" }
        TuilesMenu { menu: menuFeuille; cles: ["feuilleGauche", "feuilleDroite"] }
        Titre { text: ""; implicitHeight: 9 }
        Element { cle: "supprimerFeuille" }
    }
}
