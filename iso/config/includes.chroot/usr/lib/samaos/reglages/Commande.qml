// Lance une commande du système et rend sa sortie (stdout) : lancer("cmd", function (sortie) { … }).
import QtQuick
import org.kde.plasma.plasma5support as P5Support

P5Support.DataSource {
    id: executeur
    engine: "executable"
    property var rappels: ({})
    // Met un texte entre apostrophes pour le passer sans risque à une commande
    function q(texte) { return "'" + String(texte).replace(/'/g, "'\\''") + "'" }
    function lancer(commande, rappel) {
        var r = rappels
        r[commande] = rappel || null
        rappels = r
        connectSource(commande)
    }
    onNewData: (source, donnees) => {
        var rappel = rappels[source]
        disconnectSource(source)
        if (rappel) rappel(String(donnees["stdout"] || ""), Number(donnees["exit code"] || 0))
    }
}
