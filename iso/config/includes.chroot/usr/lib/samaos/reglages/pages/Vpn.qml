// VPN : connexions VPN (OpenVPN, WireGuard…), se connecter / déconnecter, importer un fichier fourni par
// l'entreprise ou l'école, supprimer. Les identifiants sont demandés par le système à la connexion.
import QtQuick
import QtQuick.Layouts
import QtQuick.Dialogs
import ".."

PageReglage {
    id: page
    titre: "VPN"
    property var vpns: []
    property string message: ""
    Commande { id: commande }
    function relire() {
        commande.lancer("nmcli -t -f NAME,TYPE,ACTIVE connection show | grep -E ':(vpn|wireguard):'", function (s) {
            page.vpns = s.trim() ? s.trim().split("\n").map(function (l) {
                var p = l.split(":"); return { nom: p[0], type: p[1] === "wireguard" ? "WireGuard" : "VPN", actif: p[2] === "yes" } }) : []
        })
    }
    Component.onCompleted: relire()
    Timer { interval: 3000; running: true; repeat: true; onTriggered: page.relire() }

    FileDialog {
        id: choixFichier
        title: "Importer une configuration VPN"
        nameFilters: ["Configurations VPN (*.ovpn *.conf)", "Tous les fichiers (*)"]
        onAccepted: {
            var chemin = decodeURIComponent(String(selectedFile).replace("file://", ""))
            var type = chemin.toLowerCase().endsWith(".ovpn") ? "openvpn" : "wireguard"
            commande.lancer("nmcli connection import type " + type + " file \"" + chemin + "\" 2>&1", function (s, code) {
                page.message = code === 0 ? "VPN importé" : "Import impossible : " + s.trim().split("\n").pop()
                page.relire()
            })
        }
    }

    Groupe {
        Repeater {
            model: page.vpns
            delegate: Ligne {
                titre: modelData.nom
                detail: modelData.type + (modelData.actif ? " · connecté" : "")
                BoutonSama {
                    principal: !modelData.actif
                    text: modelData.actif ? "Déconnecter" : "Se connecter"
                    onClicked: commande.lancer("nmcli connection " + (modelData.actif ? "down" : "up") + " \"" + modelData.nom + "\"", function () { page.relire() })
                }
                BoutonSama {
                    text: "Supprimer"
                    onClicked: commande.lancer("nmcli connection delete \"" + modelData.nom + "\"", function () { page.relire() })
                }
            }
        }
        Ligne {
            visible: page.vpns.length === 0
            titre: "Aucun VPN"
            detail: "Un VPN relie l'ordinateur au réseau privé de votre entreprise ou de votre école, à distance"
        }
        Ligne {
            titre: "Importer un VPN"
            detail: page.message || "Fichier .ovpn (OpenVPN) ou .conf (WireGuard) fourni par votre service informatique"
            derniere: true
            BoutonSama { principal: true; text: "Choisir le fichier"; onClicked: choixFichier.open() }
        }
    }
}
