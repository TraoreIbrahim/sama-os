// Comptes (d'après reg-06 de la maquette) : votre compte, les autres personnes de l'ordinateur et la connexion.
// Chaque personne a ses documents, ses Espaces et son mot de passe ; un clic ouvre la gestion du compte.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Comptes"
    Commande { id: commande }
    readonly property string script: "/usr/libexec/samaos/compte.sh "
    readonly property var autres: fenetre.comptes.filter(function (c) { return c.uid !== fenetre.moi.uid })
    property string nomOrdinateur: ""
    property string connexionAuto: ""
    property bool verrouillageAuReveil: false
    property bool invite: false
    property bool inviteEnCours: false

    Component.onCompleted: {
        fenetre.relireComptes()
        commande.lancer("hostname", function (s) { page.nomOrdinateur = s.trim() })
        commande.lancer(script + "connexion-auto-actuelle", function (s) { page.connexionAuto = s.trim() })
        commande.lancer("kreadconfig6 --file kscreenlockerrc --group Daemon --key LockOnResume --default false", function (s) { page.verrouillageAuReveil = s.trim() === "true" })
        commande.lancer("sh /usr/libexec/samaos/invite.sh etat", function (s) { page.invite = s.trim() === "actif" })
    }

    // Votre compte
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 96
        radius: 14
        color: Couleurs.carte
        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: fenetre.ouvrir("MonCompte") }
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 20
            anchors.rightMargin: 20
            spacing: 16
            Avatar { Layout.preferredWidth: 60; Layout.preferredHeight: 60; moi: true; nom: fenetre.moi.nom; photo: fenetre.moi.photo }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4
                Text { Layout.fillWidth: true; text: fenetre.moi.nom; elide: Text.ElideRight; font.pixelSize: 18; font.weight: Font.Medium; color: Couleurs.texte }
                RowLayout {
                    spacing: 8
                    Pastille { text: fenetre.moi.admin ? "Administrateur" : "Standard" }
                    Text { text: "Compte local · " + (page.nomOrdinateur || fenetre.moi.identifiant); font.pixelSize: 12; color: Couleurs.texte2 }
                }
            }
            BoutonSama { text: "Gérer mon compte"; onClicked: fenetre.ouvrir("MonCompte") }
        }
    }

    Groupe {
        titre: "Autres utilisateurs"
        Repeater {
            model: page.autres
            delegate: Ligne {
                titre: modelData.nom
                detail: (modelData.admin ? "Administrateur" : "Standard") + " · " + modelData.identifiant
                cliquable: true
                onClique: fenetre.ouvrir("Utilisateur", modelData.uid)
                Text { text: "›"; font.pixelSize: 20; color: Couleurs.texte3 }
                gauche: Avatar { width: 36; height: 36; uid: modelData.uid; nom: modelData.nom; photo: modelData.photo }
            }
        }
        // Session invitée (invite.sh) : pour prêter l'ordinateur sans partager ses documents
        Ligne {
            titre: "Invité"
            detail: page.inviteEnCours ? (page.invite ? "Activation…" : "Désactivation…")
                  : page.invite ? "Session temporaire, effacée à la déconnexion · proposée à l'écran de connexion"
                  : "Session temporaire, effacée à la déconnexion"
            gauche: Rectangle {
                width: 36
                height: 36
                radius: 18
                color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.1) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
                Canvas {
                    anchors.centerIn: parent
                    width: 18
                    height: 18
                    onPaint: {
                        var c = getContext("2d"); c.reset(); c.scale(18 / 24, 18 / 24)
                        c.strokeStyle = Couleurs.texte2; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                        c.path = "M12 12a4 4 0 1 0 0-8a4 4 0 1 0 0 8z M4.5 20c0-4 3.4-6.5 7.5-6.5s7.5 2.5 7.5 6.5"; c.stroke()
                    }
                }
            }
            Interrupteur {
                enabled: !page.inviteEnCours
                actif: page.invite
                onBascule: a => {
                    page.invite = a
                    page.inviteEnCours = true
                    // Autorisation refusée ou annulée : l'interrupteur revient à l'état réel
                    commande.lancer("pkexec /usr/libexec/samaos/invite.sh " + (a ? "activer" : "desactiver")
                                    + "; sh /usr/libexec/samaos/invite.sh etat",
                                    function (s) { page.invite = s.trim() === "actif"; page.inviteEnCours = false })
                }
            }
        }
        Ligne {
            titre: page.autres.length === 0 ? "Vous êtes seul sur cet ordinateur" : "Ajouter une personne"
            detail: "Chaque personne a ses documents, ses Espaces et son mot de passe"
            derniere: true
            BoutonSama { principal: true; text: "Ajouter un utilisateur"; onClicked: fenetre.ouvrir("NouvelUtilisateur") }
        }
    }

    Groupe {
        titre: "Connexion"
        LigneSousPage {
            titre: "Mot de passe"
            detail: "Le changer, ou en choisir un plus sûr"
            sousPage: "MonCompte"
        }
        Ligne {
            titre: "Connexion automatique"
            detail: page.connexionAuto === fenetre.moi.identifiant
                    ? "Sama s'ouvre sur votre session sans demander le mot de passe"
                    : "Demander le mot de passe au démarrage (recommandé si l'ordinateur est partagé)"
            Interrupteur {
                actif: page.connexionAuto === fenetre.moi.identifiant
                onBascule: actif => commande.lancer(page.script + "connexion-auto " + (actif ? commande.q(fenetre.moi.identifiant) : "aucun"),
                                                    function () { commande.lancer(page.script + "connexion-auto-actuelle", function (s) { page.connexionAuto = s.trim() }) })
            }
        }
        Ligne {
            titre: "Mot de passe au réveil"
            detail: "Demander le mot de passe quand l'ordinateur sort de veille"
            derniere: true
            Interrupteur {
                actif: page.verrouillageAuReveil
                onBascule: actif => {
                    page.verrouillageAuReveil = actif
                    commande.lancer("kwriteconfig6 --file kscreenlockerrc --group Daemon --key LockOnResume " + actif)
                }
            }
        }
    }
}
