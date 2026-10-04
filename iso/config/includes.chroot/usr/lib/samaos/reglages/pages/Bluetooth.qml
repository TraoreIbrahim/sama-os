// Bluetooth et appareils : activer, appareils connus (connecter, déconnecter), ajouter un appareil.
import QtQuick
import QtQuick.Layouts
import org.kde.bluezqt as BluezQt
import ".."

PageReglage {
    id: page
    titre: "Bluetooth et appareils"

    readonly property bool disponible: BluezQt.Manager.adapters.length > 0
    readonly property bool actif: disponible && !BluezQt.Manager.bluetoothBlocked
    Commande { id: commande }

    Groupe {
        Ligne {
            titre: "Bluetooth"
            detail: !page.disponible ? "Aucun adaptateur Bluetooth sur cet ordinateur" : (page.actif ? "Activé · visible sous le nom de cet ordinateur" : "Désactivé")
            derniere: true
            Interrupteur {
                enabled: page.disponible
                actif: page.actif
                onBascule: a => BluezQt.Manager.bluetoothBlocked = !a
            }
        }
    }

    Groupe {
        visible: page.actif
        titre: "Appareils"
        Repeater {
            model: BluezQt.DevicesModel {}
            delegate: Ligne {
                titre: model.Name || model.Address
                detail: model.Connected ? "Connecté" : (model.Paired ? "Associé" : "Disponible")
                BoutonSama {
                    text: model.Connected ? "Déconnecter" : "Connecter"
                    onClicked: model.Connected ? model.Device.disconnectFromDevice() : model.Device.connectToDevice()
                }
            }
        }
        Ligne {
            titre: "Ajouter un appareil"
            detail: "Casque, enceinte, souris, clavier, téléphone…"
            derniere: true
            BoutonSama { principal: true; text: "Ajouter"; onClicked: commande.lancer("bluedevil-wizard") }
        }
    }
}
