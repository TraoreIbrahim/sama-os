// Imprimantes : imprimantes installées, ajouter une imprimante.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Imprimantes"
    Commande { id: commande }
    property var liste: []
    property bool lu: false
    Component.onCompleted: commande.lancer("LC_ALL=C lpstat -p 2>/dev/null | awk '/^printer/ {print $2 \"|\" ($0 ~ /disabled/ ? \"Désactivée\" : ($0 ~ /idle/ ? \"Prête\" : \"Occupée\"))}'", function (s) {
        page.liste = s.trim() ? s.trim().split("\n").map(function (l) { var p = l.split("|"); return { nom: p[0].replace(/_/g, " "), etat: p[1] } }) : []
        page.lu = true
    })

    Groupe {
        Repeater {
            model: page.liste
            delegate: Ligne { titre: modelData.nom; detail: modelData.etat }
        }
        Ligne {
            visible: page.lu && page.liste.length === 0
            titre: "Aucune imprimante"
            detail: "Branchez une imprimante USB ou allumez une imprimante du réseau : elle est souvent reconnue d'elle-même"
        }
        LigneAvancee { titre: "Ajouter une imprimante"; detail: "USB, Wi-Fi ou réseau de l'entreprise"; module: "kcm_printer_manager"; derniere: true }
    }
}
