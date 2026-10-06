// Clavier : dispositions installées (la première est celle par défaut), ajouter, retirer, monter en tête.
// Passer d'une disposition à l'autre : Méta+Espace (ou Méta+Alt+K), ou d'un clic sur FR dans le Pouls.
// Langues d'Afrique : julakan (disposition du Mali, AZERTY avec ɛ ɔ ɲ ŋ sur AltGr), wolof, kiswahili ; leur code court
// (DYU, WO, SW) est celui affiché dans le Pouls (DisplayNames de kxkbrc).
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Clavier"

    readonly property var catalogue: [
        { code: "fr", variante: "", nom: "Français (AZERTY)" },
        { code: "ml", variante: "fr-oss", nom: "Julakan (dioula) · AZERTY, ɛ ɔ ɲ ŋ avec AltGr", court: "dyu" },
        { code: "sn", variante: "", nom: "Wolof", court: "wo" },
        { code: "ke", variante: "swa", nom: "Kiswahili (QWERTY)", court: "sw" },
        { code: "us", variante: "", nom: "Anglais · États-Unis (QWERTY)" },
        { code: "gb", variante: "", nom: "Anglais · Royaume-Uni (QWERTY)" },
        { code: "be", variante: "", nom: "Belge (AZERTY)" },
        { code: "ch", variante: "fr", nom: "Suisse romand (QWERTZ)" },
        { code: "ca", variante: "fr", nom: "Canadien français" },
        { code: "fr", variante: "afnor", nom: "Français (AZERTY normalisé)" },
        { code: "ara", variante: "", nom: "Arabe" },
        { code: "pt", variante: "", nom: "Portugais" }
    ]
    property var installees: []      // [{code, variante, nom}]

    Commande { id: commande }
    function nomDe(code, variante) {
        for (var i = 0; i < catalogue.length; i++) if (catalogue[i].code === code && catalogue[i].variante === variante) return catalogue[i].nom
        return code.toUpperCase() + (variante ? " (" + variante + ")" : "")
    }
    function relire() {
        commande.lancer("kreadconfig6 --file kxkbrc --group Layout --key LayoutList; echo; kreadconfig6 --file kxkbrc --group Layout --key VariantList", function (s) {
            var l = s.split("\n")
            var codes = (l[0] || "").trim() ? l[0].trim().split(",") : ["fr"]
            var variantes = (l[2] || l[1] || "").trim().split(",")
            page.installees = codes.map(function (c, i) { var v = variantes[i] || ""; return { code: c, variante: v, nom: page.nomDe(c, v) } })
        })
    }
    function courtDe(code, variante) {
        for (var i = 0; i < catalogue.length; i++) if (catalogue[i].code === code && catalogue[i].variante === variante) return catalogue[i].court || ""
        return ""
    }
    function enregistrer(liste) {
        page.installees = liste
        var codes = liste.map(function (x) { return x.code }).join(",")
        var variantes = liste.map(function (x) { return x.variante }).join(",")
        var courts = liste.map(function (x) { return page.courtDe(x.code, x.variante) }).join(",")
        var g = "kwriteconfig6 --file kxkbrc --group Layout --key "
        commande.lancer(g + "Use true && " + g + "LayoutList " + commande.q(codes) + " && " + g + "VariantList " + commande.q(variantes)
                        + " && " + g + "DisplayNames " + commande.q(courts)
                        + " && dbus-send --session --type=signal /Layouts org.kde.keyboard.reloadConfig")
    }
    Component.onCompleted: relire()

    Groupe {
        titre: "Dispositions"
        Repeater {
            model: page.installees
            delegate: Ligne {
                titre: modelData.nom
                detail: index === 0 ? "Par défaut" : ""
                BoutonSama {
                    visible: index > 0
                    text: "Par défaut"
                    onClicked: { var l = page.installees.slice(); var x = l.splice(index, 1)[0]; l.unshift(x); page.enregistrer(l) }
                }
                BoutonSama {
                    visible: page.installees.length > 1
                    text: "Retirer"
                    onClicked: { var l = page.installees.slice(); l.splice(index, 1); page.enregistrer(l) }
                }
            }
        }
        Ligne {
            titre: "Ajouter une disposition"
            detail: "Passer de l'une à l'autre : Méta + Espace, ou d'un clic sur FR dans le Pouls"
            derniere: true
            ListeDeroulante {
                implicitWidth: 260
                model: ["Choisir…"].concat(page.catalogue.filter(function (c) {
                    return !page.installees.some(function (i) { return i.code === c.code && i.variante === c.variante }) }).map(function (c) { return c.nom }))
                currentIndex: 0
                onActivated: index => {
                    if (index === 0) return
                    var dispo = page.catalogue.filter(function (c) { return !page.installees.some(function (i) { return i.code === c.code && i.variante === c.variante }) })
                    var l = page.installees.slice(); l.push(dispo[index - 1]); page.enregistrer(l)
                    currentIndex = 0
                }
            }
        }
    }
}
