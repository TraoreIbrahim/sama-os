// Data consommée (/usr/libexec/samaos/data.py) : depuis le renouvellement du forfait d'après vnstat,
// sinon depuis l'allumage. Relue toutes les 30 s.
import QtQuick
import org.kde.plasma.plasma5support as P5Support

QtObject {
    property real megaOctets: 0
    property real forfait: 1024     // Mo, choisi dans les Réglages (Data et mises à jour)
    property bool duMois: false      // mesure depuis le renouvellement du forfait

    property P5Support.DataSource lecteur: P5Support.DataSource {
        engine: "executable"
        connectedSources: ["python3 /usr/libexec/samaos/data.py etat"]
        interval: 30000
        onNewData: (source, donnees) => {
            try {
                var m = JSON.parse(String(donnees["stdout"]))
                if (m.forfaitMo > 0) forfait = m.forfaitMo
                megaOctets = m.utiliseMo
                duMois = m.source === "vnstat"
            } catch (e) {}
        }
    }
}
