// Fonctions de Sama Sheet (noms français du moteur, séparateur « ; ») : aide pendant la saisie d'une formule et
// propositions quand on commence à écrire un nom.
.pragma library

var liste = [
    { nom: "SOMME", args: ["nombre1", "[nombre2]", "…"], texte: "Additionne des nombres, ou toutes les cases d'une plage.", exemple: "=SOMME(D4:D8)", mots: "total additionner addition" },
    { nom: "MOYENNE", args: ["nombre1", "[nombre2]", "…"], texte: "Calcule la moyenne des nombres.", exemple: "=MOYENNE(B2:B13)", mots: "moyenne" },
    { nom: "MIN", args: ["nombre1", "[nombre2]", "…"], texte: "Donne le plus petit des nombres.", exemple: "=MIN(C2:C13)", mots: "minimum plus petit" },
    { nom: "MAX", args: ["nombre1", "[nombre2]", "…"], texte: "Donne le plus grand des nombres.", exemple: "=MAX(C2:C13)", mots: "maximum plus grand" },
    { nom: "NB", args: ["valeur1", "[valeur2]", "…"], texte: "Compte les cases qui contiennent un nombre.", exemple: "=NB(B2:B13)", mots: "compter nombre combien" },
    { nom: "NBVAL", args: ["valeur1", "[valeur2]", "…"], texte: "Compte les cases qui ne sont pas vides.", exemple: "=NBVAL(A2:A13)", mots: "compter non vides combien" },
    { nom: "SI", args: ["test", "valeur_si_vrai", "[valeur_si_faux]"], texte: "Donne une valeur si le test est vrai, une autre sinon.", exemple: "=SI(E2>0;\"Doit\";\"Soldé\")", mots: "condition si alors sinon" },
    { nom: "ET", args: ["test1", "[test2]", "…"], texte: "Vrai si tous les tests sont vrais.", exemple: "=ET(B2>0;C2>0)", mots: "condition et" },
    { nom: "OU", args: ["test1", "[test2]", "…"], texte: "Vrai si au moins un test est vrai.", exemple: "=OU(B2=0;C2=0)", mots: "condition ou" },
    { nom: "ARRONDI", args: ["nombre", "chiffres"], texte: "Arrondit un nombre au nombre de chiffres voulu après la virgule (0 : à l'unité, -2 : à la centaine).", exemple: "=ARRONDI(D9*1,3;-2)", mots: "arrondir" },
    { nom: "ARRONDI.SUP", args: ["nombre", "chiffres"], texte: "Arrondit vers le haut.", exemple: "=ARRONDI.SUP(D9;-2)", mots: "arrondir haut supérieur" },
    { nom: "ARRONDI.INF", args: ["nombre", "chiffres"], texte: "Arrondit vers le bas.", exemple: "=ARRONDI.INF(D9;-2)", mots: "arrondir bas inférieur" },
    { nom: "SOMME.SI", args: ["plage", "critère", "[plage_somme]"], texte: "Additionne les cases qui répondent à un critère.", exemple: "=SOMME.SI(F2:F13;\"Espèces\";C2:C13)", mots: "total condition si" },
    { nom: "NB.SI", args: ["plage", "critère"], texte: "Compte les cases qui répondent à un critère.", exemple: "=NB.SI(E2:E13;\">0\")", mots: "compter condition si combien" },
    { nom: "MOYENNE.SI", args: ["plage", "critère", "[plage_moyenne]"], texte: "Moyenne des cases qui répondent à un critère.", exemple: "=MOYENNE.SI(B2:B13;\">0\")", mots: "moyenne condition si" },
    { nom: "RECHERCHEV", args: ["valeur_cherchée", "tableau", "colonne", "[valeur_proche]"], texte: "Cherche une valeur dans la première colonne d'un tableau et donne ce qui est sur la même ligne.", exemple: "=RECHERCHEV(\"Awa Koné\";A2:E13;4;0)", mots: "chercher recherche trouver" },
    { nom: "SOMMEPROD", args: ["matrice1", "[matrice2]", "…"], texte: "Multiplie les plages case par case, puis additionne.", exemple: "=SOMMEPROD(B4:B8;C4:C8)", mots: "produit total" },
    { nom: "PRODUIT", args: ["nombre1", "[nombre2]", "…"], texte: "Multiplie les nombres entre eux.", exemple: "=PRODUIT(B4;C4)", mots: "multiplier" },
    { nom: "ABS", args: ["nombre"], texte: "Valeur absolue : le nombre sans son signe.", exemple: "=ABS(E2)", mots: "absolu" },
    { nom: "ENT", args: ["nombre"], texte: "Partie entière d'un nombre.", exemple: "=ENT(D9/3)", mots: "entier" },
    { nom: "MOD", args: ["nombre", "diviseur"], texte: "Reste de la division.", exemple: "=MOD(A2;7)", mots: "reste division modulo" },
    { nom: "RANG", args: ["nombre", "plage", "[ordre]"], texte: "Rang d'un nombre dans une liste (1 : le plus grand).", exemple: "=RANG(F2;F$2:F$31)", mots: "classement rang" },
    { nom: "AUJOURDHUI", args: [], texte: "La date du jour.", exemple: "=AUJOURDHUI()", mots: "date jour aujourd'hui" },
    { nom: "MAINTENANT", args: [], texte: "La date et l'heure.", exemple: "=MAINTENANT()", mots: "heure date maintenant" },
    { nom: "DATE", args: ["année", "mois", "jour"], texte: "Fabrique une date.", exemple: "=DATE(2026;10;6)", mots: "date" },
    { nom: "ANNEE", args: ["date"], texte: "L'année d'une date.", exemple: "=ANNEE(F2)", mots: "année date" },
    { nom: "MOIS", args: ["date"], texte: "Le mois d'une date (1 à 12).", exemple: "=MOIS(F2)", mots: "mois date" },
    { nom: "JOUR", args: ["date"], texte: "Le jour d'une date (1 à 31).", exemple: "=JOUR(F2)", mots: "jour date" },
    { nom: "CONCATENER", args: ["texte1", "[texte2]", "…"], texte: "Met des textes bout à bout.", exemple: "=CONCATENER(A2;\" \";B2)", mots: "joindre coller texte" },
    { nom: "GAUCHE", args: ["texte", "[nombre]"], texte: "Les premiers caractères d'un texte.", exemple: "=GAUCHE(A2;3)", mots: "début texte" },
    { nom: "DROITE", args: ["texte", "[nombre]"], texte: "Les derniers caractères d'un texte.", exemple: "=DROITE(A2;2)", mots: "fin texte" },
    { nom: "NBCAR", args: ["texte"], texte: "Nombre de caractères d'un texte.", exemple: "=NBCAR(A2)", mots: "longueur texte" },
    { nom: "MAJUSCULE", args: ["texte"], texte: "Met un texte en majuscules.", exemple: "=MAJUSCULE(A2)", mots: "majuscules texte" },
    { nom: "MINUSCULE", args: ["texte"], texte: "Met un texte en minuscules.", exemple: "=MINUSCULE(A2)", mots: "minuscules texte" },
    { nom: "SIERREUR", args: ["valeur", "valeur_si_erreur"], texte: "Remplace une erreur (#DIV/0!…) par une autre valeur.", exemple: "=SIERREUR(B2/C2;0)", mots: "erreur" }
];

function normaliser(s) {
    return String(s || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

function trouver(nom) {
    var n = String(nom || "").toUpperCase();
    for (var i = 0; i < liste.length; i++) if (liste[i].nom === n) return liste[i];
    return null;
}

// Texte d'une formule jusqu'au curseur → { fonction, argument } (la fonction ouverte la plus intérieure) et
// { prefixe, propositions } (le nom qu'on est en train d'écrire)
function analyser(texte) {
    var r = { fonction: null, argument: 0, prefixe: "", propositions: [] };
    if (!texte || texte.charAt(0) !== "=") return r;
    var pile = [], chaine = false;
    for (var i = 1; i < texte.length; i++) {
        var c = texte.charAt(i);
        if (c === "\"") { chaine = !chaine; continue; }
        if (chaine) continue;
        if (c === "(") {
            var m = /([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ0-9.]*)$/.exec(texte.slice(0, i));
            pile.push({ nom: m ? m[1].toUpperCase() : "", argument: 0 });
        } else if (c === ")") {
            pile.pop();
        } else if (c === ";" && pile.length) {
            pile[pile.length - 1].argument++;
        }
    }
    for (var k = pile.length - 1; k >= 0; k--) {
        var f = trouver(pile[k].nom);
        if (f) { r.fonction = f; r.argument = pile[k].argument; break; }
    }
    // Un nom en cours (des lettres après « = », « ( », « ; » ou un opérateur ; pas une case comme B12)
    var p = /(?:^=|[=(;+\-*\/&^<> ])([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ.]*)$/.exec(texte);
    if (!chaine && p) {
        r.prefixe = p[1];
        var debut = normaliser(p[1]);
        r.propositions = liste.filter(function (f) { return normaliser(f.nom).indexOf(debut) === 0; }).slice(0, 6);
        if (r.propositions.length === 1 && r.propositions[0].nom === p[1].toUpperCase()) r.propositions = [];
    }
    return r;
}

// Références de tableau dans une formule (« Tontine[[#Cette ligne];[Part]] », « Tontine[Part] ») : morceaux de texte
// et puces { texte, puce, detail } ; null s'il n'y en a pas
function morceaux(formule) {
    var re = /([A-Za-zÀ-ÿ_][A-Za-zÀ-ÿ0-9_.]*)\[(?:\[(#[^\]]*)\];)?\[?([^\[\]]*)\]?\]/g
    var res = [], debut = 0, m, trouve = false
    while ((m = re.exec(formule)) !== null) {
        trouve = true
        if (m.index > debut) res.push({ texte: formule.slice(debut, m.index), puce: false, detail: "" })
        var special = (m[2] || "").toLowerCase()
        res.push({ texte: m[3], puce: true, detail: special.indexOf("ligne") >= 0 ? "" : special.indexOf("total") >= 0 ? "total" : "toute la colonne" })
        debut = re.lastIndex
    }
    if (!trouve) return null
    if (debut < formule.length) res.push({ texte: formule.slice(debut), puce: false, detail: "" })
    return res
}
