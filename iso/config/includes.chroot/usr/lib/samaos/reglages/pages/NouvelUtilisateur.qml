// Ajouter une personne à l'ordinateur : nom, identifiant (proposé d'après le nom), mot de passe et rôle.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Nouvel utilisateur"
    Commande { id: commande }
    property string message: ""
    property bool identifiantModifie: false

    // « Aminata Traoré » → « aminata »
    function proposerIdentifiant(nom) {
        var base = nom.trim().split(/\s+/)[0] || ""
        base = base.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9_-]/g, "")
        var pris = fenetre.comptes.map(function (c) { return c.identifiant })
        var id = base, n = 2
        while (id && pris.indexOf(id) >= 0) id = base + n++
        return id
    }
    readonly property bool identifiantPris: fenetre.comptes.some(function (c) { return c.identifiant === champId.text })

    Groupe {
        titre: "Qui est-ce ?"
        Ligne {
            titre: "Nom complet"
            detail: "Tel qu'il apparaîtra à l'écran de connexion"
            ChampSama {
                id: champNom
                width: 240
                placeholderText: "ex. Aminata Traoré"
                onTextChanged: if (!page.identifiantModifie) champId.text = page.proposerIdentifiant(text)
                Component.onCompleted: forceActiveFocus()
            }
        }
        Ligne {
            titre: "Identifiant"
            detail: page.identifiantPris ? "Cet identifiant est déjà pris" : "Minuscules, sans espace ni accent ; ne pourra plus être changé"
            derniere: true
            ChampSama {
                id: champId
                width: 240
                validator: RegularExpressionValidator { regularExpression: /[a-z][a-z0-9_-]{0,30}/ }
                onTextEdited: page.identifiantModifie = true
            }
        }
    }

    Groupe {
        titre: "Mot de passe"
        Ligne {
            titre: "Mot de passe"
            detail: mdp1.text === "" ? "Au moins 8 caractères ; la personne pourra le changer" : ForceMotDePasse.libelle(mdp1.text)
            ChampSama { id: mdp1; width: 240; echoMode: TextInput.Password }
        }
        Ligne {
            titre: "Confirmer"
            detail: mdp2.text !== "" && mdp2.text !== mdp1.text ? "Les deux mots de passe ne sont pas identiques" : ""
            derniere: true
            ChampSama { id: mdp2; width: 240; echoMode: TextInput.Password }
        }
    }

    Groupe {
        titre: "Rôle"
        Ligne {
            titre: role.indexChoisi === 1 ? "Administrateur" : "Standard"
            detail: role.indexChoisi === 1
                    ? "Peut installer des logiciels, gérer les comptes et changer tous les réglages"
                    : "Utilise ses applications et ses documents ; demande à un administrateur pour le reste"
            derniere: true
            Segments { id: role; choix: ["Standard", "Administrateur"]; indexChoisi: 0; onChoisi: index => indexChoisi = index }
        }
    }

    RowLayout {
        Layout.fillWidth: true
        Text { Layout.fillWidth: true; text: page.message; font.pixelSize: 13; color: Couleurs.texte2 }
        BoutonSama { text: "Annuler"; onClicked: fenetre.sousPage = "" }
        BoutonSama {
            principal: true
            text: "Créer le compte"
            enabled: champNom.text.trim() !== "" && champId.acceptableInput && !page.identifiantPris && mdp1.text.length >= 8 && mdp1.text === mdp2.text
            onClicked: {
                page.message = "Création…"
                commande.lancer("printf '%s\\n' " + commande.q(mdp1.text) + " | /usr/libexec/samaos/compte.sh creer " + commande.q(champId.text) + " " + commande.q(champNom.text.trim()) + " " + role.indexChoisi,
                                function (s, code) {
                                    fenetre.relireComptes()
                                    if (code === 0) fenetre.sousPage = ""
                                    else page.message = "Le compte n'a pas été créé"
                                })
            }
        }
    }
}
