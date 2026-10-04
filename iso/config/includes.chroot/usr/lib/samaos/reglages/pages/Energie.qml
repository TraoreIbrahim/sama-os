// Énergie : mode d'alimentation (économie, équilibré, performances), batterie, veille.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Énergie"

    property string profil: ""
    property var profils: []
    property string batterie: ""

    Commande { id: commande }
    function relire() {
        commande.lancer("powerprofilesctl get; powerprofilesctl list | sed -n 's/^[ *]*\\([a-z-]*\\):$/\\1/p'", function (s) {
            var l = s.trim().split("\n"); page.profil = l[0]; page.profils = l.slice(1) })
        commande.lancer("upower -i $(upower -e | grep -m1 BAT) 2>/dev/null | sed -n 's/^ *percentage: *//p; s/^ *state: *//p'", function (s) {
            var l = s.trim().split("\n").filter(function (x) { return x })
            if (l.length < 2) { page.batterie = ""; return }
            var etats = { "charging": "en charge", "discharging": "sur batterie", "fully-charged": "chargée", "pending-charge": "branchée" }
            page.batterie = l[1] + " · " + (etats[l[0]] || l[0])
        })
    }
    Component.onCompleted: relire()

    readonly property var modes: [
        { id: "power-saver", nom: "Économie" }, { id: "balanced", nom: "Équilibré" }, { id: "performance", nom: "Performances" }
    ].filter(function (m) { return page.profils.length === 0 || page.profils.indexOf(m.id) >= 0 })

    Groupe {
        Ligne {
            titre: "Mode d'alimentation"
            detail: page.profil === "power-saver" ? "La batterie dure plus longtemps" : page.profil === "performance" ? "L'ordinateur va plus vite et chauffe davantage" : "Le bon compromis au quotidien"
            Segments {
                choix: page.modes.map(function (m) { return m.nom })
                indexChoisi: Math.max(0, page.modes.map(function (m) { return m.id }).indexOf(page.profil))
                onChoisi: index => commande.lancer("powerprofilesctl set " + page.modes[index].id, function () { page.relire() })
            }
        }
        Ligne {
            titre: "Batterie"
            detail: page.batterie || "Pas de batterie : l'ordinateur est branché sur le secteur"
            derniere: true
        }
    }

    Groupe {
        titre: "Écran et veille"
        LigneAvancee { titre: "Mise en veille et extinction de l'écran"; detail: "Délais, fermeture du couvercle, coupures de courant"; module: "kcm_powerdevilprofilesconfig"; derniere: true }
    }
}
