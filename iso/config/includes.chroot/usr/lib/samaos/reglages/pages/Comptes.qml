// Comptes : votre compte (nom, photo, mot de passe) et les autres comptes de l'ordinateur (créer, supprimer).
import QtQuick
import QtQuick.Layouts
import QtQuick.Dialogs
import org.kde.coreaddons as KCoreAddons
import ".."

PageReglage {
    id: page
    titre: "Comptes"
    KCoreAddons.KUser { id: utilisateur }
    Commande { id: commande }

    property var autres: []
    property string message: ""
    property string messageMotDePasse: ""
    property bool creation: false
    readonly property string script: "/usr/libexec/samaos/compte.sh "

    function relire() {
        commande.lancer("getent passwd | awk -F: '$3 >= 1000 && $3 < 60000 {print $3 \"|\" $1 \"|\" $5}'", function (s) {
            page.autres = s.trim().split("\n").filter(function (l) { return l && l.split("|")[1] !== utilisateur.loginName }).map(function (l) {
                var p = l.split("|"); return { uid: p[0], identifiant: p[1], nom: (p[2] || p[1]).split(",")[0] } })
        })
    }
    Component.onCompleted: relire()

    FileDialog {
        id: choixPhoto
        title: "Choisir une photo"
        nameFilters: ["Images (*.png *.jpg *.jpeg *.webp)"]
        onAccepted: {
            var chemin = decodeURIComponent(String(selectedFile).replace("file://", ""))
            commande.lancer(page.script + "photo " + commande.q(chemin), function (s, code) { page.message = code === 0 ? "Photo changée" : "Photo non changée" })
        }
    }

    // Carte du compte
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 110
        radius: 14
        color: Couleurs.carte
        RowLayout {
            anchors.fill: parent
            anchors.margins: 22
            spacing: 18
            MouseArea {
                Layout.preferredWidth: 64
                Layout.preferredHeight: 64
                cursorShape: Qt.PointingHandCursor
                onClicked: choixPhoto.open()
                Rectangle {
                    anchors.fill: parent
                    radius: 32
                    color: Couleurs.laterite
                    Image { anchors.fill: parent; source: utilisateur.faceIconUrl; visible: status === Image.Ready; fillMode: Image.PreserveAspectCrop; sourceSize.width: 128; sourceSize.height: 128; cache: false }
                }
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text { Layout.fillWidth: true; text: utilisateur.fullName || utilisateur.loginName; font.pixelSize: 20; font.weight: Font.Medium; color: Couleurs.texte }
                Text { Layout.fillWidth: true; text: page.message || ("Identifiant : " + utilisateur.loginName + " · cliquez sur la photo pour la changer"); font.pixelSize: 13; color: Couleurs.texte2 }
            }
        }
    }

    Groupe {
        titre: "Votre compte"
        Ligne {
            titre: "Nom affiché"
            detail: "À la connexion et dans le Pouls"
            ChampSama { id: champNom; width: 220; text: utilisateur.fullName }
            BoutonSama {
                text: "Enregistrer"
                enabled: champNom.text.trim() !== "" && champNom.text.trim() !== utilisateur.fullName
                onClicked: commande.lancer(page.script + "nom " + commande.q(champNom.text.trim()), function (s, code) { page.message = code === 0 ? "Nom enregistré" : "Nom non enregistré" })
            }
        }
        Ligne {
            titre: "Mot de passe"
            detail: page.messageMotDePasse || "Au moins 8 caractères ; le système vous demandera l'actuel"
            derniere: true
            ChampSama { id: mdp1; width: 150; echoMode: TextInput.Password; placeholderText: "Nouveau" }
            ChampSama { id: mdp2; width: 150; echoMode: TextInput.Password; placeholderText: "Confirmer" }
            BoutonSama {
                text: "Changer"
                enabled: mdp1.text.length >= 8 && mdp1.text === mdp2.text
                onClicked: commande.lancer(page.script + "motdepasse " + commande.q(mdp1.text), function (s, code) {
                    page.messageMotDePasse = code === 0 ? "Mot de passe changé" : "Mot de passe non changé"
                    mdp1.text = ""; mdp2.text = ""
                })
            }
        }
    }

    Groupe {
        titre: "Autres comptes"
        Repeater {
            model: page.autres
            delegate: Ligne {
                titre: modelData.nom
                detail: "Identifiant : " + modelData.identifiant
                BoutonSama { text: "Supprimer"; onClicked: commande.lancer(page.script + "supprimer " + modelData.uid, function () { page.relire() }) }
            }
        }
        Ligne {
            visible: !page.creation
            titre: page.autres.length === 0 ? "Vous êtes seul sur cet ordinateur" : "Ajouter une personne"
            detail: "Chaque personne a ses documents, ses Espaces et son mot de passe"
            derniere: true
            BoutonSama { principal: true; text: "Ajouter un compte"; onClicked: page.creation = true }
        }
        Ligne {
            visible: page.creation
            titre: "Nouveau compte"
            ChampSama { id: nouveauNom; width: 160; placeholderText: "Nom complet" }
            ChampSama { id: nouvelId; width: 120; placeholderText: "Identifiant"; validator: RegularExpressionValidator { regularExpression: /[a-z][a-z0-9_-]{0,30}/ } }
        }
        Ligne {
            visible: page.creation
            titre: "Mot de passe et rôle"
            detail: "Un administrateur peut installer des logiciels et changer les réglages de l'ordinateur"
            derniere: true
            ChampSama { id: nouveauMdp; width: 140; echoMode: TextInput.Password; placeholderText: "Mot de passe" }
            Segments { id: role; choix: ["Standard", "Admin"]; indexChoisi: 0; onChoisi: index => indexChoisi = index }
            BoutonSama {
                principal: true
                text: "Créer"
                enabled: nouveauNom.text.trim() !== "" && nouvelId.acceptableInput && nouveauMdp.text.length >= 8
                onClicked: commande.lancer(page.script + "creer " + commande.q(nouvelId.text) + " " + commande.q(nouveauNom.text.trim()) + " " + role.indexChoisi + " " + commande.q(nouveauMdp.text),
                                           function (s, code) { page.message = code === 0 ? "Compte créé" : "Compte non créé"; page.creation = false; page.relire() })
            }
        }
    }
}
