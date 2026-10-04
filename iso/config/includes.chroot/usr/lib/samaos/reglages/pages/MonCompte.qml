// Mon compte : photo, nom affiché et mot de passe de la personne connectée.
import QtQuick
import QtQuick.Layouts
import QtQuick.Dialogs
import ".."

PageReglage {
    id: page
    titre: "Mon compte"
    Commande { id: commande }
    readonly property string script: "/usr/libexec/samaos/compte.sh "
    property string messageProfil: ""
    property string messageMotDePasse: ""
    property bool motDePasseReussi: false

    FileDialog {
        id: choixPhoto
        title: "Choisir une photo"
        nameFilters: ["Images (*.png *.jpg *.jpeg *.webp)"]
        onAccepted: {
            var chemin = decodeURIComponent(String(selectedFile).replace("file://", ""))
            commande.lancer(page.script + "photo " + fenetre.moi.uid + " " + commande.q(chemin), function (s, code) {
                page.messageProfil = code === 0 ? "Photo changée" : "La photo n'a pas pu être changée"
                fenetre.photosModifiees()
            })
        }
    }

    // Photo et nom
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 128
        radius: 14
        color: Couleurs.carte
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 24
            anchors.rightMargin: 24
            spacing: 20
            Avatar { Layout.preferredWidth: 84; Layout.preferredHeight: 84; moi: true; nom: fenetre.moi.nom; photo: fenetre.moi.photo }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4
                Text { Layout.fillWidth: true; text: fenetre.moi.nom; elide: Text.ElideRight; font.pixelSize: 20; font.weight: Font.Medium; color: Couleurs.texte }
                Text { Layout.fillWidth: true; text: page.messageProfil || ((fenetre.moi.admin ? "Administrateur" : "Standard") + " · identifiant « " + fenetre.moi.identifiant + " »"); font.pixelSize: 13; color: Couleurs.texte2 }
                Row {
                    Layout.topMargin: 6
                    spacing: 8
                    BoutonSama { text: "Choisir une photo"; onClicked: choixPhoto.open() }
                    BoutonSama {
                        visible: fenetre.moi.photo !== ""
                        text: "Retirer la photo"
                        onClicked: commande.lancer(page.script + "photo " + fenetre.moi.uid + " ''", function () { page.messageProfil = "Photo retirée"; fenetre.photosModifiees() })
                    }
                }
            }
        }
    }

    Groupe {
        titre: "Profil"
        Ligne {
            titre: "Nom affiché"
            detail: "À l'écran de connexion, dans les Réglages et sur vos documents"
            ChampSama { id: champNom; width: 220; text: fenetre.moi.nom; onAccepted: enregistrer.clicked() }
            BoutonSama {
                id: enregistrer
                text: "Enregistrer"
                enabled: champNom.text.trim() !== "" && champNom.text.trim() !== fenetre.moi.nom
                onClicked: commande.lancer(page.script + "nom " + fenetre.moi.uid + " " + commande.q(champNom.text.trim()), function (s, code) {
                    page.messageProfil = code === 0 ? "Nom enregistré" : "Le nom n'a pas pu être enregistré"
                    fenetre.relireComptes()
                })
            }
        }
        Ligne {
            titre: "Identifiant"
            detail: "Le nom du compte pour le système ; il ne peut pas être changé"
            derniere: true
            Text { text: fenetre.moi.identifiant; font.pixelSize: 13; color: Couleurs.texte2 }
        }
    }

    Groupe {
        titre: "Mot de passe"
        Ligne {
            titre: "Nouveau mot de passe"
            detail: mdp1.text === "" ? "Au moins 8 caractères" : ForceMotDePasse.libelle(mdp1.text)
            ChampSama { id: mdp1; width: 220; echoMode: TextInput.Password; placeholderText: "Nouveau mot de passe" }
        }
        Ligne {
            titre: "Confirmer"
            detail: mdp2.text !== "" && mdp2.text !== mdp1.text ? "Les deux mots de passe ne sont pas identiques" : ""
            ChampSama { id: mdp2; width: 220; echoMode: TextInput.Password; placeholderText: "Retapez-le"; onAccepted: changer.clicked() }
        }
        Ligne {
            titre: page.messageMotDePasse || "Le système vous demandera votre mot de passe actuel"
            derniere: true
            BoutonSama {
                id: changer
                principal: true
                text: "Changer le mot de passe"
                enabled: mdp1.text.length >= 8 && mdp1.text === mdp2.text
                onClicked: commande.lancer("printf '%s\\n' " + commande.q(mdp1.text) + " | " + page.script + "motdepasse " + fenetre.moi.uid, function (s, code) {
                    page.messageMotDePasse = code === 0 ? "Mot de passe changé" : "Le mot de passe n'a pas été changé"
                    if (code === 0) { mdp1.text = ""; mdp2.text = "" }
                })
            }
        }
    }
}
