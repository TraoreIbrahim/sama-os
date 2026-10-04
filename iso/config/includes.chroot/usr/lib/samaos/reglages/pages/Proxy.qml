// Proxy : passerelle imposée par certaines administrations et entreprises (aucun, automatique, manuel).
// Réglage du bureau (kioslaverc), suivi par les applications de Sama et par Griot.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Proxy"
    property int mode: 0          // 0 aucun, 1 manuel, 3 automatique (WPAD)
    property string adresse: ""
    property string port: ""
    property string exceptions: ""
    property string message: ""

    Commande { id: commande }
    function lire(cle, rappel) { commande.lancer("kreadconfig6 --file kioslaverc --group 'Proxy Settings' --key " + cle, rappel) }
    Component.onCompleted: {
        lire("ProxyType", function (s) { page.mode = Number(s.trim()) || 0 })
        lire("httpProxy", function (s) {
            var v = s.trim().replace(/^https?:\/\//, "")
            var p = v.split(/[ :]/); page.adresse = p[0] || ""; page.port = p[1] || "" })
        lire("NoProxyFor", function (s) { page.exceptions = s.trim() })
    }
    function enregistrer() {
        var g = "kwriteconfig6 --file kioslaverc --group 'Proxy Settings' --key "
        var serveur = page.adresse ? "http://" + page.adresse + " " + (page.port || "8080") : ""
        commande.lancer(g + "ProxyType " + page.mode + " && " + g + "httpProxy '" + serveur + "' && " + g + "httpsProxy '" + serveur + "' && "
                        + g + "NoProxyFor '" + page.exceptions + "' && dbus-send --type=signal /KIO/Scheduler org.kde.KIO.Scheduler.reparseSlaveConfiguration string:''",
                        function () { page.message = "Enregistré" })
    }

    Groupe {
        Ligne {
            titre: "Proxy"
            detail: page.mode === 0 ? "Connexion directe à Internet" : page.mode === 3 ? "Trouvé tout seul sur le réseau" : "Adresse fournie par votre service informatique"
            derniere: page.mode !== 1
            Segments {
                choix: ["Aucun", "Automatique", "Manuel"]
                indexChoisi: page.mode === 3 ? 1 : page.mode === 1 ? 2 : 0
                onChoisi: index => { page.mode = [0, 3, 1][index]; page.enregistrer() }
            }
        }
        Ligne {
            visible: page.mode === 1
            titre: "Adresse et port"
            ChampSama { width: 220; placeholderText: "proxy.exemple.ci"; text: page.adresse; onEditingFinished: page.adresse = text.trim() }
            ChampSama { width: 80; placeholderText: "8080"; text: page.port; inputMethodHints: Qt.ImhDigitsOnly; onEditingFinished: page.port = text.trim() }
        }
        Ligne {
            visible: page.mode === 1
            titre: "Sans proxy pour"
            detail: "Adresses séparées par des virgules (ex. intranet.local)"
            derniere: true
            ChampSama { width: 220; text: page.exceptions; onEditingFinished: page.exceptions = text.trim() }
        }
    }
    RowLayout {
        visible: page.mode === 1
        Layout.fillWidth: true
        Text { Layout.fillWidth: true; text: page.message; font.pixelSize: 12; color: Couleurs.texte2 }
        BoutonSama { principal: true; text: "Enregistrer"; onClicked: page.enregistrer() }
    }
}
