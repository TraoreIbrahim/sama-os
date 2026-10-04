// Imprimantes : installées (par défaut, page de test, supprimer) et détectées (réseau, USB récentes),
// ajoutées sans pilote (IPP Everywhere). Ajouter ou supprimer demande le mot de passe administrateur.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Imprimantes"
    property bool cups: true
    property var installees: []
    property string parDefaut: ""
    property var trouvees: []
    property bool recherche: false
    property string message: ""

    Commande { id: commande }
    function relire() {
        commande.lancer("command -v lpstat >/dev/null && echo oui", function (s) {
            page.cups = s.trim() === "oui"
            if (!page.cups) return
            commande.lancer("LC_ALL=C lpstat -p 2>/dev/null | awk '/^printer/ {print $2 \"|\" ($0 ~ /disabled/ ? \"Désactivée\" : ($0 ~ /idle/ ? \"Prête\" : \"Occupée\"))}'", function (t) {
                page.installees = t.trim() ? t.trim().split("\n").map(function (l) { var p = l.split("|"); return { id: p[0], nom: p[0].replace(/_/g, " "), etat: p[1] } }) : []
            })
            commande.lancer("LC_ALL=C lpstat -d 2>/dev/null | sed -n 's/.*: *//p'", function (t) { page.parDefaut = t.trim() })
        })
    }
    function chercher() {
        page.recherche = true
        commande.lancer("driverless 2>/dev/null; lpinfo -v 2>/dev/null | awk '$2 ~ /^(usb|ipp|ipps|dnssd):/ {print $2}'", function (s) {
            var vues = {}
            page.trouvees = s.trim().split("\n").filter(function (u) { return u && !vues[u] && (vues[u] = true) }).map(function (u) {
                var nom = decodeURIComponent(u.replace(/^[a-z]+:\/\//, "").split(/[/?._]/)[0]).replace(/%20/g, " ")
                return { uri: u, nom: nom || u }
            })
            page.recherche = false
        })
    }
    function ajouter(t) {
        var id = t.nom.replace(/[^A-Za-z0-9]+/g, "_").replace(/^_|_$/g, "") || "Imprimante"
        page.message = "Ajout de « " + t.nom + " »…"
        commande.lancer("pkexec lpadmin -p '" + id + "' -E -v '" + t.uri + "' -m everywhere 2>&1", function (s, code) {
            page.message = code === 0 ? "« " + t.nom + " » est prête" : "Ajout impossible : " + s.trim().split("\n").pop()
            page.relire()
        })
    }
    Component.onCompleted: { relire(); if (page.cups) chercher() }

    Groupe {
        visible: !page.cups
        Ligne {
            titre: "Le service d'impression arrive avec la prochaine version de Sama"
            detail: "Il reconnaîtra tout seul la plupart des imprimantes récentes, en Wi-Fi, en réseau ou en USB"
            derniere: true
        }
    }

    Groupe {
        visible: page.cups
        titre: "Installées"
        Repeater {
            model: page.installees
            delegate: Ligne {
                titre: modelData.nom
                detail: modelData.etat + (modelData.id === page.parDefaut ? " · par défaut" : "")
                BoutonSama { visible: modelData.id !== page.parDefaut; text: "Par défaut"; onClicked: commande.lancer("lpoptions -d '" + modelData.id + "'", function () { page.relire() }) }
                BoutonSama { text: "Page de test"; onClicked: commande.lancer("lp -d '" + modelData.id + "' /usr/share/cups/data/testprint", function () { page.message = "Page de test envoyée" }) }
                BoutonSama { text: "Supprimer"; onClicked: commande.lancer("pkexec lpadmin -x '" + modelData.id + "'", function () { page.relire() }) }
            }
        }
        Ligne { visible: page.installees.length === 0; titre: "Aucune imprimante installée"; derniere: true }
    }

    Groupe {
        visible: page.cups
        titre: "Détectées autour de vous"
        Repeater {
            model: page.trouvees
            delegate: Ligne {
                titre: modelData.nom
                detail: modelData.uri.indexOf("usb") === 0 ? "Branchée en USB" : "Sur le réseau"
                BoutonSama { principal: true; text: "Ajouter"; onClicked: page.ajouter(modelData) }
            }
        }
        Ligne {
            titre: page.recherche ? "Recherche…" : (page.trouvees.length === 0 ? "Aucune imprimante détectée" : "Chercher à nouveau")
            detail: page.message || "Allumez l'imprimante et reliez-la au même réseau, ou branchez-la en USB"
            derniere: true
            BoutonSama { text: "Chercher"; enabled: !page.recherche; onClicked: page.chercher() }
        }
    }

    Groupe {
        visible: page.cups
        titre: "Ajouter par adresse"
        Ligne {
            titre: "Adresse de l'imprimante"
            detail: "Adresse IP affichée par l'imprimante ou fournie par votre service informatique (ex. 192.168.1.20)"
            derniere: true
            ChampSama { id: champAdresse; width: 170; placeholderText: "192.168.1.20" }
            BoutonSama {
                principal: true
                text: "Ajouter"
                enabled: champAdresse.text.trim() !== ""
                onClicked: {
                    var a = champAdresse.text.trim()
                    var uri = a.indexOf("://") > 0 ? a : "ipp://" + a + "/ipp/print"
                    page.ajouter({ uri: uri, nom: "Imprimante " + a.replace(/^[a-z]+:\/\//, "").split("/")[0] })
                }
            }
        }
    }
}
