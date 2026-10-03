// Data consommée depuis l'allumage (toutes les interfaces sauf la boucle locale), relue toutes les 30 s.
import QtQuick
import org.kde.plasma.plasma5support as P5Support

QtObject {
    property real megaOctets: 0
    readonly property real forfait: 1024     // Mo ; réglage du forfait à venir (Réglages → Data)
    readonly property string commande: "awk '{s+=$1} END {print s}' /sys/class/net/[!l]*/statistics/rx_bytes /sys/class/net/[!l]*/statistics/tx_bytes"

    property P5Support.DataSource lecteur: P5Support.DataSource {
        engine: "executable"
        connectedSources: [commande]
        interval: 30000
        onNewData: (source, donnees) => {
            var octets = Number(String(donnees["stdout"]).trim())
            if (!isNaN(octets)) megaOctets = octets / 1048576
        }
    }
}
