// Data consommée depuis l'allumage (toutes les interfaces sauf la boucle locale), relue toutes les 30 s.
import QtQuick
import org.kde.plasma.plasma5support as P5Support

QtObject {
    property real megaOctets: 0
    property real forfait: 1024     // Mo, choisi dans les Réglages (Data et mises à jour)
    readonly property string commande: "awk '{s+=$1} END {print s}' /sys/class/net/[!l]*/statistics/rx_bytes /sys/class/net/[!l]*/statistics/tx_bytes; kreadconfig6 --file samaosrc --group Data --key forfaitMo --default 1024"

    property P5Support.DataSource lecteur: P5Support.DataSource {
        engine: "executable"
        connectedSources: [commande]
        interval: 30000
        onNewData: (source, donnees) => {
            var lignes = String(donnees["stdout"]).trim().split("\n")
            var octets = Number(lignes[0])
            var f = Number(lignes[1])
            if (f > 0) forfait = f
            if (!isNaN(octets)) megaOctets = octets / 1048576
        }
    }
}
