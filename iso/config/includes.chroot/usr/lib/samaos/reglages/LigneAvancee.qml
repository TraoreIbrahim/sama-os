// Ligne qui ouvre un réglage avancé de KDE (en attendant sa version Sama).
import QtQuick

Ligne {
    id: ligne
    property string module          // ex. « kcm_printer_manager »
    cliquable: true
    onClique: commande.lancer("kcmshell6 " + ligne.module)
    Text { text: "›"; font.pixelSize: 20; color: Couleurs.texte3 }
    Commande { id: commande }
}
