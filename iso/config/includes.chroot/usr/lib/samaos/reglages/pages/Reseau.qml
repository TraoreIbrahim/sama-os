// Réseau et Internet : Wi-Fi (activer, réseaux disponibles, se connecter), connexion filaire, VPN et proxy.
// Le mot de passe d'un nouveau réseau est demandé par le système au moment de la connexion.
import QtQuick
import QtQuick.Layouts
import org.kde.plasma.networkmanagement as PlasmaNM
import ".."

PageReglage {
    id: page
    titre: "Réseau et Internet"

    PlasmaNM.EnabledConnections { id: etat }
    PlasmaNM.Handler { id: gestion }
    PlasmaNM.AppletProxyModel { id: reseaux; sourceModel: PlasmaNM.NetworkModel {} }

    function estConnecte(m) { return m.ConnectionState === PlasmaNM.Enums.Activated }
    function connecter(m) {
        if (m.ConnectionState === PlasmaNM.Enums.Activated) return
        if (m.Uuid) gestion.activateConnection(m.ConnectionPath, m.DevicePath, m.SpecificPath)
        else gestion.addAndActivateConnection(m.DevicePath, m.SpecificPath, "")
    }

    Groupe {
        Ligne {
            titre: "Wi-Fi"
            detail: !etat.wirelessHwEnabled ? "Désactivé par l'interrupteur de l'ordinateur" : (etat.wirelessEnabled ? "Activé" : "Désactivé")
            derniere: true
            Interrupteur {
                enabled: etat.wirelessHwEnabled
                actif: etat.wirelessEnabled
                onBascule: a => gestion.enableWireless(a)
            }
        }
    }

    Groupe {
        titre: "Connexions"
        Repeater {
            model: reseaux
            delegate: Ligne {
                readonly property bool sansFil: model.Type === PlasmaNM.Enums.Wireless
                readonly property bool connecte: page.estConnecte(model)
                visible: sansFil ? etat.wirelessEnabled : (model.Type === PlasmaNM.Enums.Wired)
                titre: model.Name || model.ItemUniqueName
                detail: (connecte ? "Connecté" : (model.ConnectionState === PlasmaNM.Enums.Activating ? "Connexion…" : ""))
                        + (sansFil ? (connecte ? " · " : "") + (model.SecurityType > 0 ? "Sécurisé" : "Ouvert · non sécurisé")
                                     + (model.Signal ? " · signal " + (model.Signal > 66 ? "fort" : model.Signal > 33 ? "moyen" : "faible") : "")
                                   : (connecte ? " · câble branché" : "Câble"))
                cliquable: !connecte
                onClique: page.connecter(model)
                BoutonSama {
                    visible: connecte
                    text: "Déconnecter"
                    onClicked: gestion.deactivateConnection(model.ConnectionPath, model.DevicePath)
                }
                Text {
                    visible: !connecte
                    text: "Se connecter"
                    font.pixelSize: 12
                    font.weight: Font.DemiBold
                    color: Couleurs.lateriteEncre
                }
            }
        }
        Ligne {
            visible: reseaux.count === 0
            titre: "Aucun réseau trouvé"
            detail: etat.wirelessEnabled ? "Rapprochez-vous d'une borne Wi-Fi ou branchez un câble" : "Activez le Wi-Fi pour voir les réseaux"
            derniere: true
        }
    }

    Groupe {
        titre: "Autres connexions"
        LigneAvancee { titre: "Partage de connexion depuis le téléphone"; detail: "Par câble USB ou Bluetooth"; module: "kcm_networkmanagement" }
        LigneAvancee { titre: "VPN"; detail: "Réseau privé de l'entreprise ou de l'école"; module: "kcm_networkmanagement" }
        LigneAvancee { titre: "Proxy"; detail: "Passerelle imposée par certaines administrations"; module: "kcm_proxy"; derniere: true }
    }
}
