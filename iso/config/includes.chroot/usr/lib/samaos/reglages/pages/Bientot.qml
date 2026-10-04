// Section pas encore refaite pour Sama : explication et accès aux réglages avancés (KDE) en attendant.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: fenetre.section.titre

    // Module de réglages de KDE correspondant à chaque section
    readonly property var modules: ({
        "comptes": "kcm_users", "confidentialite": "kcm_kactivities", "energie": "kcm_powerdevilprofilesconfig",
        "imprimantes": "kcm_printer_manager", "accessibilite": "kcm_access", "bluetooth": "kcm_bluetooth",
        "reseau": "kcm_networkmanagement", "langue": "kcm_regionandlang", "sauvegarde": "", "organisation": ""
    })
    readonly property string module: modules[fenetre.section.id] || ""

    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: texte.implicitHeight + 44
        radius: 14
        color: Couleurs.carte
        Text {
            id: texte
            anchors.fill: parent
            anchors.margins: 22
            wrapMode: Text.WordWrap
            lineHeight: 1.3
            text: page.module
                ? "Cette section arrive bientôt dans les Réglages de Sama. En attendant, ses réglages restent disponibles dans les réglages avancés."
                : "Cette section arrive avec une prochaine version de Sama."
            font.pixelSize: 14
            color: Couleurs.texte2
        }
    }
    BoutonSama {
        visible: page.module !== ""
        principal: true
        text: "Ouvrir les réglages avancés"
        onClicked: commande.lancer("kcmshell6 " + page.module)
    }
    Commande { id: commande }
}
