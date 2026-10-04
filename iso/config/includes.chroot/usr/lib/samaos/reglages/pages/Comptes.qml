// Comptes : votre compte (nom, photo, mot de passe) et les autres comptes de l'ordinateur.
import QtQuick
import QtQuick.Layouts
import org.kde.coreaddons as KCoreAddons
import ".."

PageReglage {
    id: page
    titre: "Comptes"
    KCoreAddons.KUser { id: utilisateur }
    Commande { id: commande }
    property string autres: ""
    Component.onCompleted: commande.lancer("getent passwd | awk -F: '$3 >= 1000 && $3 < 60000 {print $5 ? $5 : $1}' | cut -d, -f1", function (s) {
        var l = s.trim().split("\n").filter(function (x) { return x && x !== (utilisateur.fullName || utilisateur.loginName) })
        page.autres = l.join(", ")
    })

    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 110
        radius: 14
        color: Couleurs.carte
        RowLayout {
            anchors.fill: parent
            anchors.margins: 22
            spacing: 18
            Rectangle {
                Layout.preferredWidth: 64
                Layout.preferredHeight: 64
                radius: 32
                color: Couleurs.laterite
                Image { anchors.fill: parent; source: utilisateur.faceIconUrl; visible: status === Image.Ready; fillMode: Image.PreserveAspectCrop; sourceSize.width: 128; sourceSize.height: 128 }
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text { Layout.fillWidth: true; text: utilisateur.fullName || utilisateur.loginName; font.pixelSize: 20; font.weight: Font.Medium; color: Couleurs.texte }
                Text { Layout.fillWidth: true; text: "Identifiant : " + utilisateur.loginName; font.pixelSize: 13; color: Couleurs.texte2 }
            }
        }
    }
    Groupe {
        titre: "Votre compte"
        LigneAvancee { titre: "Nom et photo"; detail: "Affichés à la connexion et dans le Pouls"; module: "kcm_users" }
        LigneAvancee { titre: "Mot de passe"; detail: "Demandé à la connexion et pour les changements importants"; module: "kcm_users"; derniere: true }
    }
    Groupe {
        titre: "Autres comptes"
        LigneAvancee { titre: page.autres ? page.autres : "Aucun autre compte"; detail: "Ajouter un compte pour chaque personne qui utilise l'ordinateur"; module: "kcm_users"; derniere: true }
    }
}
