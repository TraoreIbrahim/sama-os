// Data et mises à jour : forfait (lu par la carte Data et le centre de contrôle), connexion mesurée,
// économie de data (réglage enregistré, à venir dans les applications) et mises à jour la nuit
// (/usr/libexec/samaos/mises-a-jour-nuit.sh, réglage de l'ordinateur : autorisation d'un administrateur).
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Data et mises à jour"

    property int forfaitMo: 1024
    property real consommeMo: 0
    property bool mesuree: false
    property string connexion: ""
    property bool economie: false
    property bool nuit: false
    readonly property var forfaits: [500, 1024, 2048, 5120, 10240]
    readonly property var libelles: ["500 Mo", "1 Go", "2 Go", "5 Go", "10 Go"]

    function lisible(mo) { return mo >= 1024 ? (mo / 1024).toLocaleString(Qt.locale(), "f", 1).replace(",0", "") + " Go" : Math.round(mo) + " Mo" }
    function reglage(cle, valeur) { commande.lancer("kwriteconfig6 --file samaosrc --group Data --key " + cle + " " + valeur) }

    Commande { id: commande }
    Component.onCompleted: {
        commande.lancer("kreadconfig6 --file samaosrc --group Data --key forfaitMo --default 1024", function (s) { page.forfaitMo = Number(s.trim()) || 1024 })
        commande.lancer("kreadconfig6 --file samaosrc --group Data --key economie --default false", function (s) { page.economie = s.trim() === "true" })
        commande.lancer("sh /usr/libexec/samaos/mises-a-jour-nuit.sh etat", function (s) { page.nuit = s.trim() === "actif" })
        commande.lancer("awk '{ s += $1 } END { print s + 0 }' /sys/class/net/[!l]*/statistics/rx_bytes /sys/class/net/[!l]*/statistics/tx_bytes", function (s) { page.consommeMo = Number(s) / 1048576 })
        commande.lancer("nmcli -t -f NAME,DEVICE connection show --active | grep -v ':lo$' | head -1 | cut -d: -f1", function (s) {
            page.connexion = s.trim()
            if (page.connexion) commande.lancer("nmcli -g connection.metered connection show \"" + page.connexion + "\"", function (m) { page.mesuree = m.trim() === "yes" })
        })
    }

    // Consommation
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 104
        radius: 14
        color: Couleurs.carte
        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 20
            spacing: 10
            RowLayout {
                Layout.fillWidth: true
                Text { Layout.fillWidth: true; text: "Data depuis l'allumage"; font.pixelSize: 13; color: Couleurs.texte2 }
                Text { text: page.lisible(page.consommeMo); font.pixelSize: 22; font.weight: Font.Medium; color: Couleurs.texte }
                Text { text: "sur " + page.lisible(page.forfaitMo); font.pixelSize: 13; color: Couleurs.texte2 }
            }
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 8
                radius: 4
                color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.09)
                Rectangle {
                    height: parent.height
                    radius: 4
                    width: parent.width * Math.min(1, page.consommeMo / page.forfaitMo)
                    color: page.consommeMo / page.forfaitMo > 0.9 ? Couleurs.laterite : Couleurs.foret
                }
            }
            Text { text: "Une alerte vous prévient à 80 % puis à 100 % du forfait."; font.pixelSize: 12; color: Couleurs.texte3 }
        }
    }

    Groupe {
        titre: "Forfait"
        Ligne {
            titre: "Votre forfait data"
            detail: "Sert de repère au compteur et aux alertes"
            Segments {
                choix: page.libelles
                indexChoisi: Math.max(0, page.forfaits.indexOf(page.forfaitMo))
                onChoisi: index => { page.forfaitMo = page.forfaits[index]; page.reglage("forfaitMo", page.forfaits[index]) }
            }
        }
        Ligne {
            titre: "Connexion mesurée"
            detail: page.connexion ? "« " + page.connexion + " » : les mises à jour et téléchargements automatiques attendent une connexion illimitée"
                                   : "Aucune connexion active"
            derniere: true
            Interrupteur {
                enabled: page.connexion !== ""
                actif: page.mesuree
                onBascule: a => {
                    page.mesuree = a
                    commande.lancer("nmcli connection modify \"" + page.connexion + "\" connection.metered " + (a ? "yes" : "no"))
                }
            }
        }
    }

    Groupe {
        titre: "Économies"
        Ligne {
            titre: "Économie de data"
            detail: "Images allégées et lecture automatique désactivée dans les applications Sama (bientôt)"
            Interrupteur { actif: page.economie; onBascule: a => { page.economie = a; page.reglage("economie", a) } }
        }
        Ligne {
            titre: "Mises à jour la nuit"
            detail: "Installées entre 1 h et 5 h, quand l'ordinateur est branché et sur une connexion illimitée"
            derniere: true
            Interrupteur {
                actif: page.nuit
                onBascule: a => {
                    page.nuit = a
                    // Autorisation refusée ou annulée : l'interrupteur revient à l'état réel
                    commande.lancer("pkexec /usr/libexec/samaos/mises-a-jour-nuit.sh " + (a ? "activer" : "desactiver")
                                    + "; sh /usr/libexec/samaos/mises-a-jour-nuit.sh etat",
                                    function (s) { page.nuit = s.trim() === "actif" })
                }
            }
        }
    }
}
