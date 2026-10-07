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

// ——— Tableaux : ce qui reste à payer, et le résumé de la barre d'état ———

// Colonne calculée « A − B » (Reste = Part − Versé) : { reste, du, verse } (rangs des colonnes), ou null
function progression(colonnes) {
    for (var i = 0; i < colonnes.length; i++) {
        if (!colonnes[i].calcul) continue
        var m = /\[\[#[^\]]*\];\[([^\]]+)\]\]\s*-\s*[^\[;]+\[\[#[^\]]*\];\[([^\]]+)\]\]/.exec(colonnes[i].formule)
        if (!m) continue
        var du = -1, verse = -1
        for (var j = 0; j < colonnes.length; j++) { if (colonnes[j].nom === m[1]) du = j; if (colonnes[j].nom === m[2]) verse = j }
        if (du >= 0 && verse >= 0) return { reste: i, du: du, verse: verse }
    }
    return null
}

// Un nombre à la française : 45 000 ; 3 200,50
function enChiffres(x) {
    var entier = Math.abs(x - Math.round(x)) < 0.005
    return Number(x).toLocaleString(Qt.locale("fr_FR"), "f", entier ? 0 : 2)
}

// Statistiques du moteur (« Moyenne : 45833,3333333333 ; Somme : 275000 ») arrondies et groupées par milliers
function statistiques(texte) {
    return String(texte || "").split(";").map(function (s) {
        s = s.trim().replace(/\s*:\s*/, " : ").replace(/^NbVal/i, "Nombre").replace(/^Nb\b/, "Nombre")
        // (le nombre seul : chiffres par groupes de trois, sans l'espace qui le sépare de « F »)
        return s.replace(/-?\d+(?:[\s\u00a0\u202f]\d{3})*(?:,\d+)?/, function (n) {
            var x = Number(n.replace(/[\s  ]/g, "").replace(",", "."))
            return isNaN(x) ? n : enChiffres(x)
        })
    }).filter(function (s) { return /\d/.test(s) }).join("   ·   ")
}

// Ce que montre la barre d'état pour un tableau de versements (maquette « Un tableau dans la grille ») :
// « <b>9 membres sur 12</b> ont tout versé · il reste 45 000 F à recevoir », ou "" si le tableau n'en est pas un.
// d : SamaTableaux.Lignes ({ colonnes, lignes })
function resume(d) {
    var p = progression(d.colonnes)
    if (!p) return ""
    var cols = d.colonnes
    // (un calcul de ce qui reste à payer, pas un stock : Entrées − Sorties n'en est pas un)
    if (!/vers|pay|r[ée]gl|rembours|cotis|acompte|re[çc]u/i.test(cols[p.verse].nom) && !/reste|d[ûu]$|solde|impay|[àa] payer|manque/i.test(cols[p.reste].nom)) return ""
    var lignes = d.lignes.filter(function (r) { return !r.vide })
    var n = lignes.length
    if (!n) return ""
    var soldes = 0, reste = 0
    for (var i = 0; i < lignes.length; i++) {
        var x = lignes[i].n[p.reste], du = lignes[i].n[p.du]
        if (x !== null && x <= 0 && du > 0) soldes++
        if (x > 0) reste += x
    }
    var nom = String(cols[0].nom || "").toLowerCase()
    if (/^(nom|pr[ée]nom)/.test(nom) || !nom) nom = "personne"
    var noms = /[sxz]$/.test(nom) ? nom : nom + "s"
    var nomVerse = cols[p.verse].nom
    var participe = /vers/i.test(nomVerse) ? "versé" : /pay/i.test(nomVerse) ? "payé" : /rembours/i.test(nomVerse) ? "remboursé" : "réglé"
    var montant = cols[p.reste].genre === "montant"
    var resteTexte = enChiffres(reste) + (montant ? " F" : "")
    if (soldes === n) return "<b>" + (n > 1 ? "Les " + n + " " + noms + " ont" : "1 " + nom + " sur 1 a") + " tout " + participe + "</b> · plus rien à recevoir"
    var debut = soldes === 0 ? "<b>Personne n'a encore tout " + participe + "</b>"
                : "<b>" + soldes + " " + (soldes > 1 ? noms : nom) + " sur " + n + "</b> " + (soldes > 1 ? "ont" : "a") + " tout " + participe
    return debut + " · il reste " + resteTexte + " à recevoir"
}

// ——— La vue Image (Envoyer) : les images qu'on peut tirer d'un tableau, d'après ses colonnes ———
// Une image : { cle, nom (pour la choisir), titre (la phrase), sousTitre, legende: [[mot, couleur]],
//               barres: [{ nom, court, parts: [{ v, c }], texte, encre }] }

var LAGUNE = "#1F5E7A", OCRE = "#E2A62B", VERT = "#2F6B57", ENCRE = "#1E2740"

function estTelephone(nom) { return /t[ée]l[ée]?phone|^t[ée]l\b|portable|whatsapp/i.test(String(nom)) }
// « 07 12 34 56 78 » → « 07 •• •• •• 78 »
function masquer(v) {
    var c = String(v).replace(/\D/g, "")
    return c.length < 6 ? String(v) : c.slice(0, 2) + " •• •• •• " + c.slice(-2)
}
// « Aminata Diabaté » → « Aminata D. » (l'aperçu du téléphone)
function court(nom) {
    var m = String(nom || "").trim().split(/\s+/)
    return m.length > 1 ? m[0] + " " + m[1].charAt(0) + "." : m[0]
}
function sommeDe(liste, f) { var s = 0; for (var i = 0; i < liste.length; i++) s += f(liste[i]); return s }

function images(d) {
    var res = []
    if (!d || !d.colonnes || !d.colonnes.length) return res
    var cols = d.colonnes
    var lignes = d.lignes.filter(function (r) { return !r.vide && r.v[0] })
    if (!lignes.length) return res
    var nom = String(cols[0].nom || "").toLowerCase()
    if (/^(nom|pr[ée]nom)/.test(nom) || !nom) nom = "personne"
    var noms = /[sxz]$/.test(nom) ? nom : nom + "s"
    var Nom = nom.charAt(0).toUpperCase() + nom.slice(1)
    function en(x, montant) { return enChiffres(x) + (montant ? " F" : "") }

    // Un tableau de versements (Reste = Part − Versé)
    var p = progression(cols)
    var versements = p !== null && (/vers|pay|r[ée]gl|rembours|cotis|acompte|re[çc]u/i.test(cols[p.verse].nom)
                                    || /reste|d[ûu]$|solde|impay|[àa] payer|manque/i.test(cols[p.reste].nom))
    if (versements) {
        var montant = cols[p.reste].genre === "montant" || cols[p.verse].genre === "montant"
        var nomVerse = cols[p.verse].nom, nomReste = cols[p.reste].nom
        var participe = /vers/i.test(nomVerse) ? "versé" : /pay/i.test(nomVerse) ? "payé" : /rembours/i.test(nomVerse) ? "remboursé" : "réglé"
        var dus = [], soldes = []
        for (var i = 0; i < lignes.length; i++) {
            var e = { r: lignes[i], v: lignes[i].n[p.verse] || 0, x: lignes[i].n[p.reste] || 0 }
            if (e.x > 0) dus.push(e); else soldes.push(e)
        }
        dus.sort(function (a, b) { return b.x - a.x })
        var reste = sommeDe(dus, function (e) { return e.x })
        res.push({
            cle: "membres",
            nom: Nom + " par " + nom,
            titre: dus.length ? "Il reste " + en(reste, montant) + " à recevoir, chez " + dus.length + " " + (dus.length > 1 ? noms : nom) + "."
                              : "Tout est " + participe + " : plus rien à recevoir.",
            sousTitre: nomVerse + " et " + nomReste.toLowerCase() + ", " + nom + " par " + nom + " · d'après les colonnes " + nomVerse + " et " + nomReste,
            legende: [[nomVerse, LAGUNE], [nomReste, OCRE]],
            barres: dus.concat(soldes).map(function (e) {
                return { nom: e.r.v[0], court: court(e.r.v[0]), parts: [{ v: Math.max(0, e.v), c: LAGUNE }, { v: Math.max(0, e.x), c: OCRE }],
                         texte: e.x > 0 ? nomReste.toLowerCase() + " " + en(e.x, montant) : "soldé", encre: e.x > 0 ? ENCRE : VERT }
            })
        })
        res.push({
            cle: "solde",
            nom: "Soldé ou pas : " + soldes.length + " et " + dus.length,
            titre: soldes.length + " " + (soldes.length > 1 ? noms : nom) + " sur " + lignes.length + " " + (soldes.length > 1 ? "ont" : "a") + " tout " + participe + ".",
            sousTitre: dus.length ? "Il reste " + en(reste, montant) + " à recevoir · d'après la colonne " + nomReste : "Plus rien à recevoir · d'après la colonne " + nomReste,
            legende: [],
            barres: [{ nom: "Tout " + participe, court: "Tout " + participe, parts: [{ v: soldes.length, c: VERT }], texte: String(soldes.length), encre: VERT },
                     { nom: "Doivent encore", court: "Doivent encore", parts: [{ v: dus.length, c: OCRE }],
                       texte: dus.length + (dus.length ? " · " + en(reste, montant) : ""), encre: ENCRE }]
        })
        // Jour après jour (s'il y a une colonne de dates)
        var dj = -1
        for (var k = 0; k < cols.length; k++) if (cols[k].genre === "date") { dj = k; break }
        if (dj >= 0) {
            var jours = {}, ordre = []
            for (var m = 0; m < lignes.length; m++) {
                var jour = lignes[m].n[dj]
                if (jour === null) continue
                if (!(jour in jours)) { jours[jour] = { texte: lignes[m].v[dj], v: 0 }; ordre.push(jour) }
                jours[jour].v += lignes[m].n[p.verse] || 0
            }
            ordre.sort(function (a, b) { return a - b })
            if (ordre.length) {
                var recu = sommeDe(ordre, function (j) { return jours[j].v })
                res.push({
                    cle: "jours",
                    nom: cols[p.verse].nom + " jour après jour",
                    titre: en(recu, montant) + " reçus en " + ordre.length + " jour" + (ordre.length > 1 ? "s" : "") + ".",
                    sousTitre: "Ce qui a été " + participe + ", jour après jour · d'après les colonnes " + cols[dj].nom + " et " + nomVerse,
                    legende: [],
                    barres: ordre.map(function (j) {
                        return { nom: jours[j].texte, court: jours[j].texte, parts: [{ v: jours[j].v, c: LAGUNE }], texte: en(jours[j].v, montant), encre: ENCRE }
                    })
                })
            }
        }
    }

    // Une colonne de nombres, ligne par ligne (le stock de chaque article…)
    if (!versements) {
        for (var c = 1; c < cols.length; c++) {
            var col = cols[c]
            if (!col.nombre || col.genre === "date" || col.genre === "heure") continue
            var vals = lignes.filter(function (r) { return r.n[c] !== null }).map(function (r) { return { r: r, x: r.n[c] } })
            if (!vals.length) continue
            vals.sort(function (a, b) { return b.x - a.x })
            var enMontant = col.genre === "montant"
            res.push({
                cle: "valeurs:" + c,
                nom: col.nom + " par " + nom,
                titre: col.nom + " : " + en(sommeDe(vals, function (e) { return e.x }), enMontant) + " en tout.",
                sousTitre: vals[0].r.v[0] + " en tête, avec " + vals[0].r.v[c] + " · d'après la colonne " + col.nom,
                legende: [],
                barres: vals.map(function (e) {
                    return { nom: e.r.v[0], court: court(e.r.v[0]), parts: [{ v: Math.max(0, e.x), c: LAGUNE }], texte: e.r.v[c], encre: ENCRE }
                })
            })
        }
    }

    // La répartition d'une colonne à choix (Mobile money, Espèces…)
    for (var q = 1; q < cols.length; q++) {
        var cq = cols[q]
        if (cq.nombre || cq.calcul || estTelephone(cq.nom) || cq.genre === "date") continue
        var comptes = {}, valeurs = [], n = 0
        for (var w = 0; w < lignes.length; w++) {
            var val = lignes[w].v[q]
            if (!val) continue
            n++
            if (!(val in comptes)) { comptes[val] = 0; valeurs.push(val) }
            comptes[val]++
        }
        if (n < 2 || valeurs.length < 2 || valeurs.length > 8) continue
        valeurs.sort(function (a, b) { return comptes[b] - comptes[a] })
        res.push({
            cle: "repartition:" + q,
            nom: "Répartition : " + cq.nom,
            titre: valeurs[0] + " en tête : " + comptes[valeurs[0]] + " sur " + n + ".",
            sousTitre: cq.nom + ", " + noms + " par réponse · d'après la colonne " + cq.nom,
            legende: [],
            barres: valeurs.map(function (v) {
                return { nom: v, court: v, parts: [{ v: comptes[v], c: LAGUNE }], texte: String(comptes[v]), encre: ENCRE }
            })
        })
    }
    return res
}
