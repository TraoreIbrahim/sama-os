// Gestion d'un autre compte de l'ordinateur : nom, photo, rôle, nouveau mot de passe (s'il l'a oublié), suppression.
// Le système demande le mot de passe d'un administrateur pour ces changements.
import QtQuick
import QtQuick.Layouts
import QtQuick.Dialogs
import ".."

PageReglage {
    id: page
    readonly property var compte: {
        for (var i = 0; i < fenetre.comptes.length; i++) if (fenetre.comptes[i].uid === fenetre.parametre) return fenetre.comptes[i]
        return null
    }
    titre: compte ? compte.nom : "Compte supprimé"
    Commande { id: commande }
    readonly property string script: "/usr/libexec/samaos/compte.sh "
    property string message: ""
    property bool confirmerSuppression: false

    function executer(action, reussite, echec) {
        commande.lancer(page.script + action, function (s, code) { page.message = code === 0 ? reussite : echec; fenetre.relireComptes() })
    }

    FileDialog {
        id: choixPhoto
        title: "Choisir une photo"
        nameFilters: ["Images (*.png *.jpg *.jpeg *.webp)"]
        onAccepted: {
            var chemin = decodeURIComponent(String(selectedFile).replace("file://", ""))
            commande.lancer(page.script + "photo " + page.compte.uid + " " + commande.q(chemin), function (s, code) {
                page.message = code === 0 ? "Photo changée" : "La photo n'a pas pu être changée"
                fenetre.photosModifiees()
            })
        }
    }

    Rectangle {
        visible: page.compte !== null
        Layout.fillWidth: true
        Layout.preferredHeight: 96
        radius: 14
        color: Couleurs.carte
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 20
            anchors.rightMargin: 20
            spacing: 16
            Avatar { Layout.preferredWidth: 60; Layout.preferredHeight: 60; uid: page.compte ? page.compte.uid : 0; nom: page.compte ? page.compte.nom : ""; photo: page.compte ? page.compte.photo : "" }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4
                Text { Layout.fillWidth: true; text: page.compte ? page.compte.nom : ""; elide: Text.ElideRight; font.pixelSize: 18; font.weight: Font.Medium; color: Couleurs.texte }
                RowLayout {
                    spacing: 8
                    Pastille {
                        text: page.compte && page.compte.admin ? "Administrateur" : "Standard"
                        teinte: page.compte && page.compte.admin ? Couleurs.laterite : Couleurs.foret
                        encre: page.compte && page.compte.admin ? Couleurs.lateriteEncre : Couleurs.foret
                    }
                    Text { text: page.message || ("Identifiant « " + (page.compte ? page.compte.identifiant : "") + " »"); font.pixelSize: 12; color: Couleurs.texte2 }
                }
            }
            BoutonSama { text: "Changer la photo"; onClicked: choixPhoto.open() }
        }
    }

    Groupe {
        visible: page.compte !== null
        titre: "Profil"
        Ligne {
            titre: "Nom affiché"
            ChampSama { id: champNom; width: 220; text: page.compte ? page.compte.nom : "" }
            BoutonSama {
                text: "Enregistrer"
                enabled: page.compte && champNom.text.trim() !== "" && champNom.text.trim() !== page.compte.nom
                onClicked: page.executer("nom " + page.compte.uid + " " + commande.q(champNom.text.trim()), "Nom enregistré", "Le nom n'a pas pu être enregistré")
            }
        }
        Ligne {
            titre: "Rôle"
            detail: "Un administrateur peut installer des logiciels, gérer les comptes et changer les réglages de l'ordinateur"
            derniere: true
            Segments {
                choix: ["Standard", "Administrateur"]
                indexChoisi: page.compte && page.compte.admin ? 1 : 0
                onChoisi: index => page.executer("type " + page.compte.uid + " " + index, index === 1 ? "Est maintenant administrateur" : "Est maintenant standard", "Le rôle n'a pas été changé")
            }
        }
    }

    Groupe {
        visible: page.compte !== null
        titre: "Mot de passe oublié ?"
        Ligne {
            titre: "Nouveau mot de passe"
            detail: mdp1.text === "" ? "Donnez-le ensuite à la personne ; elle pourra le changer depuis son compte" : ForceMotDePasse.libelle(mdp1.text)
            ChampSama { id: mdp1; width: 200; echoMode: TextInput.Password; placeholderText: "Nouveau mot de passe" }
        }
        Ligne {
            titre: "Confirmer"
            detail: mdp2.text !== "" && mdp2.text !== mdp1.text ? "Les deux mots de passe ne sont pas identiques" : ""
            ChampSama { id: mdp2; width: 200; echoMode: TextInput.Password; placeholderText: "Retapez-le" }
        }
        Ligne {
            titre: "Le système demandera le mot de passe d'un administrateur"
            derniere: true
            BoutonSama {
                principal: true
                text: "Définir le mot de passe"
                enabled: mdp1.text.length >= 8 && mdp1.text === mdp2.text
                onClicked: commande.lancer("printf '%s\\n' " + commande.q(mdp1.text) + " | " + page.script + "motdepasse " + page.compte.uid, function (s, code) {
                    page.message = code === 0 ? "Nouveau mot de passe défini" : "Le mot de passe n'a pas été changé"
                    if (code === 0) { mdp1.text = ""; mdp2.text = "" }
                })
            }
        }
    }

    Groupe {
        visible: page.compte !== null
        Ligne {
            visible: !page.confirmerSuppression
            titre: "Supprimer ce compte"
            detail: "La personne ne pourra plus se connecter à cet ordinateur"
            derniere: true
            BoutonSama { text: "Supprimer…"; onClicked: page.confirmerSuppression = true }
        }
        Ligne {
            visible: page.confirmerSuppression
            titre: "Que faire de ses documents ?"
            detail: "Les garder permet de les récupérer plus tard"
            derniere: true
            BoutonSama { text: "Annuler"; onClicked: page.confirmerSuppression = false }
            BoutonSama {
                text: "Garder ses documents"
                onClicked: commande.lancer(page.script + "supprimer " + page.compte.uid + " garder", function (s, code) { if (code === 0) fenetre.sousPage = ""; fenetre.relireComptes() })
            }
            BoutonSama {
                principal: true
                text: "Tout effacer"
                onClicked: commande.lancer(page.script + "supprimer " + page.compte.uid + " effacer", function (s, code) { if (code === 0) fenetre.sousPage = ""; fenetre.relireComptes() })
            }
        }
    }
}
