// Partage de connexion depuis le téléphone : par câble USB ou Bluetooth, Sama se connecte tout seul.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Partage de connexion"
    property string telephone: ""
    Commande { id: commande }
    function relire() {
        commande.lancer("nmcli -t -f DEVICE,TYPE,STATE device | grep -E '^(usb|rndis|enx|bnep)|:gsm:|:bt:' | head -1", function (s) {
            var p = s.trim().split(":"); page.telephone = s.trim() ? (p[2] === "connected" ? "Téléphone connecté (" + p[0] + ")" : "Téléphone détecté (" + p[0] + ")") : "" })
    }
    Component.onCompleted: relire()
    Timer { interval: 3000; running: true; repeat: true; onTriggered: page.relire() }

    Groupe {
        Ligne { titre: "État"; detail: page.telephone || "Aucun téléphone branché"; derniere: true }
    }
    Groupe {
        titre: "Par câble USB (le plus fiable)"
        Ligne { titre: "1. Branchez le téléphone avec son câble"; detail: "Un câble qui transfère les données, pas seulement la charge" }
        Ligne { titre: "2. Activez le partage sur le téléphone"; detail: "Android : Paramètres › Connexions › Partage de connexion › Partage via USB" }
        Ligne { titre: "3. C'est tout"; detail: "Sama se connecte tout seul ; pensez à indiquer la connexion comme mesurée dans Data"; derniere: true }
    }
    Groupe {
        titre: "Par Bluetooth"
        Ligne { titre: "Associez le téléphone"; detail: "Dans Bluetooth et appareils, puis activez « Partage Bluetooth » sur le téléphone"; derniere: true
            BoutonSama { text: "Bluetooth"; onClicked: fenetre.sectionCourante = "bluetooth" } }
    }
}
