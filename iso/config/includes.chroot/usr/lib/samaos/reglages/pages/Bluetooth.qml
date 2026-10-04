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

    // Recherche d'appareils : l'adaptateur les découvre, ils apparaissent dans la liste (« Disponible »)
    property bool recherche: false
    readonly property var adaptateur: BluezQt.Manager.usableAdapter
    onRechercheChanged: if (adaptateur) { if (recherche) adaptateur.startDiscovery(); else adaptateur.stopDiscovery() }
    Component.onDestruction: if (recherche && adaptateur) adaptateur.stopDiscovery()
    Timer { interval: 60000; running: page.recherche; onTriggered: page.recherche = false }

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
                    principal: !model.Paired
                    text: !model.Paired ? "Associer" : (model.Connected ? "Déconnecter" : "Connecter")
                    onClicked: {
                        if (!model.Paired) { model.Device.trusted = true; model.Device.pair() }
                        else if (model.Connected) model.Device.disconnectFromDevice()
                        else model.Device.connectToDevice()
                    }
                }
                BoutonSama {
                    visible: model.Paired
                    text: "Oublier"
                    onClicked: if (page.adaptateur) page.adaptateur.removeDevice(model.Device)
                }
            }
        }
        Ligne {
            titre: page.recherche ? "Recherche des appareils…" : "Ajouter un appareil"
            detail: page.recherche ? "Mettez l'appareil en mode association (souvent un appui long sur son bouton)" : "Casque, enceinte, souris, clavier, téléphone…"
            derniere: true
            BoutonSama {
                principal: !page.recherche
                text: page.recherche ? "Arrêter" : "Chercher"
                onClicked: page.recherche = !page.recherche
            }
        }
    }
}
