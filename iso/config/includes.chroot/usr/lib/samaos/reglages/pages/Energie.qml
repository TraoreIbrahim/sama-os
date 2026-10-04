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
    Component.onCompleted: { relire(); lireDelais() }

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

    // Délais (minutes, 0 = jamais), appliqués sur secteur et sur batterie (powerdevilrc)
    readonly property var delaisEcran: [1, 2, 5, 10, 15, 30, 0]
    readonly property var delaisVeille: [5, 10, 15, 30, 60, 0]
    property int ecran: 10
    property int veille: 15
    function libelle(m) { return m === 0 ? "Jamais" : m < 60 ? m + " min" : (m / 60) + " h" }
    function lireDelais() {
        commande.lancer("kreadconfig6 --file powerdevilrc --group AC --group Display --key TurnOffDisplayWhenIdle --default true; "
                        + "kreadconfig6 --file powerdevilrc --group AC --group Display --key TurnOffDisplayIdleTimeoutSec --default 600; "
                        + "kreadconfig6 --file powerdevilrc --group AC --group SuspendAndShutdown --key AutoSuspendAction --default 1; "
                        + "kreadconfig6 --file powerdevilrc --group AC --group SuspendAndShutdown --key AutoSuspendIdleTimeoutSec --default 900", function (s) {
            var l = s.trim().split("\n")
            page.ecran = l[0] === "false" ? 0 : Math.round(Number(l[1]) / 60)
            page.veille = l[2] === "0" ? 0 : Math.round(Number(l[3]) / 60)
        })
    }
    function ecrire(groupe, cle, valeur) {
        return ["AC", "Battery", "LowBattery"].map(function (p) {
            return "kwriteconfig6 --file powerdevilrc --group " + p + " --group " + groupe + " --key " + cle + " " + valeur }).join(" && ")
    }
    function appliquer() { commande.lancer("qdbus6 org.kde.Solid.PowerManagement /org/kde/Solid/PowerManagement org.kde.Solid.PowerManagement.refreshStatus") }

    Groupe {
        titre: "Écran et veille"
        Ligne {
            titre: "Éteindre l'écran après"
            detail: "Sans utilisation de la souris ni du clavier"
            ListeDeroulante {
                model: page.delaisEcran.map(page.libelle)
                currentIndex: Math.max(0, page.delaisEcran.indexOf(page.ecran))
                onActivated: index => {
                    var m = page.delaisEcran[index]; page.ecran = m
                    commande.lancer(page.ecrire("Display", "TurnOffDisplayWhenIdle", m > 0) + (m > 0 ? " && " + page.ecrire("Display", "TurnOffDisplayIdleTimeoutSec", m * 60) : ""), page.appliquer)
                }
            }
        }
        Ligne {
            titre: "Mettre en veille après"
            detail: "L'ordinateur consomme presque rien et repart en quelques secondes"
            derniere: true
            ListeDeroulante {
                model: page.delaisVeille.map(page.libelle)
                currentIndex: Math.max(0, page.delaisVeille.indexOf(page.veille))
                onActivated: index => {
                    var m = page.delaisVeille[index]; page.veille = m
                    commande.lancer(page.ecrire("SuspendAndShutdown", "AutoSuspendAction", m > 0 ? 1 : 0) + (m > 0 ? " && " + page.ecrire("SuspendAndShutdown", "AutoSuspendIdleTimeoutSec", m * 60) : ""), page.appliquer)
                }
            }
        }
    }
}
