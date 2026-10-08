// Où mène un lien, avant de l'ouvrir (Sama Sheet, Sama Docs) : le site en clair, et les mises en garde contre les
// arnaques. analyser(adresse, texteAffiche) → { site, ouvrable, alertes: [] }
.pragma library

var raccourcisseurs = ["bit.ly", "tinyurl.com", "t.co", "cutt.ly", "is.gd", "rb.gy", "goo.gl", "ow.ly", "shorturl.at", "tiny.cc"]

function analyser(adresse, affiche) {
    var m = /^([a-z][a-z0-9+.-]*):(?:\/\/)?([^\/?#]*)/i.exec(adresse)
    var schema = m ? m[1].toLowerCase() : "", autorite = m ? m[2] : "", alertes = []
    var site = autorite.replace(/^.*@/, "").replace(/:\d+$/, "").toLowerCase()
    var ouvrable = ["http", "https", "mailto", "tel"].indexOf(schema) >= 0
    if (!ouvrable) alertes.push("Ce lien n'est pas une page web : Sama ne l'ouvre pas.")
    if (schema === "http") alertes.push("Pas chiffré (http) : n'y tapez ni mot de passe ni code.")
    if (autorite.indexOf("@") >= 0) alertes.push("L'adresse cache un nom avant « @ » : le vrai site est " + site + ".")
    if (/^\d+\.\d+\.\d+\.\d+$/.test(site) || site.indexOf("xn--") >= 0) alertes.push("Adresse inhabituelle : prudence.")
    if (raccourcisseurs.indexOf(site.replace(/^www\./, "")) >= 0) alertes.push("Lien raccourci : on ne voit pas où il mène vraiment.")
    // Le texte affiché annonce-t-il un autre site ? (« wave.com » écrit, « wave-bonus.xyz » au bout du lien)
    var annonce = /((?:[a-z0-9-]+\.)+[a-z]{2,})/i.exec(String(affiche || ""))
    if (annonce && site && (schema === "http" || schema === "https")) {
        var a = annonce[1].toLowerCase().replace(/^www\./, ""), s = site.replace(/^www\./, "")
        if (s !== a && !s.endsWith("." + a)) alertes.push("Le texte affiché annonce « " + a + " », mais le lien mène à « " + s + " ».")
    }
    return { site: schema === "mailto" ? adresse.slice(7) : schema === "tel" ? adresse.slice(4) : site, ouvrable: ouvrable, alertes: alertes }
}

// Ce qu'on tape pour un lien : « wave.com » devient https://wave.com, « aya@exemple.ci » un courriel
function normaliser(texte) {
    var t = String(texte || "").trim()
    if (!t) return ""
    if (/^[a-z][a-z0-9+.-]*:/i.test(t)) return t
    if (/^[^\s@\/]+@[^\s@\/]+\.[a-z]{2,}$/i.test(t)) return "mailto:" + t
    if (/^\+?[\d\s.]{8,}$/.test(t)) return "tel:" + t.replace(/[\s.]/g, "")
    return "https://" + t
}
