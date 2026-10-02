// État du réseau (Wi-Fi, filaire, coupé), lu depuis NetworkManager via le module de Plasma.
import QtQuick
import org.kde.plasma.networkmanagement as PlasmaNM

Item {
    readonly property string etat: {
        var nom = String(icone.connectionIcon)
        if (nom.indexOf("wireless") >= 0 && nom.indexOf("disconnected") < 0 && nom.indexOf("offline") < 0) return "wifi"
        if (nom.indexOf("wired") >= 0 && nom.indexOf("disconnected") < 0) return "filaire"
        if (nom.indexOf("offline") >= 0 || nom.indexOf("disconnected") >= 0 || nom.indexOf("unavailable") >= 0) return "coupe"
        return "wifi"
    }
    readonly property bool wifiActive: connexions.wirelessEnabled

    PlasmaNM.ConnectionIcon { id: icone }
    PlasmaNM.EnabledConnections { id: connexions }
    PlasmaNM.Handler { id: gestionnaire }

    function activerWifi(actif) {
        gestionnaire.enableWireless(actif)
    }
}
