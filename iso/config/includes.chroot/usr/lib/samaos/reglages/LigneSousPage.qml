// Ligne qui ouvre une sous-page des Réglages (ex. VPN dans Réseau et Internet).
import QtQuick

Ligne {
    id: ligne
    property string sousPage
    cliquable: true
    onClique: fenetre.ouvrir(ligne.sousPage)
    Text { text: "›"; font.pixelSize: 20; color: Couleurs.texte3 }
}
