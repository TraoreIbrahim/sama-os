// Nom du réseau connecté (Wi-Fi ou filaire), lu avec nmcli à l'ouverture du panneau.
import QtQuick
import org.kde.plasma.plasma5support as P5Support

QtObject {
    property string nom: ""
    property string type: ""      // wifi, filaire ou vide
    function relire() { lecteur.connectSource(commande) }
    readonly property string commande: "nmcli -t -f NAME,TYPE connection show --active"

    property P5Support.DataSource lecteur: P5Support.DataSource {
        engine: "executable"
        connectedSources: [commande]
        onNewData: (source, donnees) => {
            var lignes = String(donnees["stdout"]).trim().split("\n")
            nom = ""; type = ""
            for (var i = 0; i < lignes.length; i++) {
                var champs = lignes[i].split(":")
                var t = champs[champs.length - 1]
                var n = champs.slice(0, champs.length - 1).join(":")
                if (t.indexOf("wireless") >= 0) { nom = n; type = "wifi"; break }
                if (t.indexOf("ethernet") >= 0 && !nom) { nom = n; type = "filaire" }
            }
            disconnectSource(source)
        }
    }
}
