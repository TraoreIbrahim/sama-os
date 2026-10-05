// Zone où lâcher des fichiers (glisser-déposer) : un dossier (déplacement ou copie, voir Fichiers.qml), ou la
// Corbeille (« corbeille » : les fichiers y sont mis).
import QtQuick

DropArea {
    property string destination
    keys: ["text/uri-list"]
    enabled: destination !== ""
    onDropped: depot => {
        if (destination === "corbeille") {
            var chemins = []
            for (var i = 0; i < depot.urls.length; i++) chemins.push(fenetre.chemin(String(depot.urls[i])))
            fenetre.jeter(chemins)
        } else fenetre.deposer(depot.urls, destination, depot.proposedAction)
        depot.accept(Qt.CopyAction)        // (l'application d'origine n'a rien à effacer : c'est fait ici)
    }
}
