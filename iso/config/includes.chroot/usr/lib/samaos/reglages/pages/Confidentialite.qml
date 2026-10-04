// Confidentialité : ce que Sama garde (fichiers récents) et ce qui sort de l'ordinateur (météo, mises à jour).
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Confidentialité"
    Commande { id: commande }
    property bool historique: true
    property string efface: ""
    Component.onCompleted: commande.lancer("kreadconfig6 --file kactivitymanagerd-pluginsrc --group Plugin-org.kde.ActivityManager.Resources.Scoring --key what-to-remember --default 0", function (s) {
        page.historique = s.trim() !== "2" })

    Groupe {
        titre: "Sur cet ordinateur"
        Ligne {
            titre: "Se souvenir des fichiers ouverts"
            detail: "Pour retrouver vos documents récents dans la Cour et Fichiers"
            Interrupteur {
                actif: page.historique
                onBascule: a => {
                    page.historique = a
                    commande.lancer("kwriteconfig6 --file kactivitymanagerd-pluginsrc --group Plugin-org.kde.ActivityManager.Resources.Scoring --key what-to-remember " + (a ? 0 : 2))
                }
            }
        }
        Ligne {
            titre: "Effacer l'historique"
            detail: page.efface || "Fichiers et applications récents"
            derniere: true
            BoutonSama {
                text: "Effacer"
                onClicked: commande.lancer("rm -f ~/.local/share/kactivitymanagerd/resources/database* ~/.local/share/recently-used.xbel", function () { page.efface = "Historique effacé" })
            }
        }
    }
    Groupe {
        titre: "Ce qui sort de l'ordinateur"
        Ligne { titre: "Météo"; detail: "Seule la ville choisie est envoyée au service Open-Meteo, sans compte ni identifiant" }
        Ligne { titre: "Mises à jour"; detail: "Sama contacte les serveurs de mises à jour pour savoir s'il y a du nouveau" }
        Ligne { titre: "Rapports d'erreur"; detail: "Aucun rapport n'est envoyé sans votre accord"; derniere: true }
    }
}
