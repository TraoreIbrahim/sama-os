// Bureau et Natte : taille et masquage de la Natte, éléments affichés, cartes du bureau de l'Espace actif,
// fond d'écran. Les changements s'appliquent tout de suite (natte.sh, interface de script de Plasma).
import QtQuick
import QtQuick.Layouts
import QtQuick.Dialogs
import ".."

PageReglage {
    id: page
    titre: "Bureau et Natte"
    Commande { id: commande }
    readonly property string script: "/usr/libexec/samaos/natte.sh "

    property var etat: ({ taille: "normale", masquage: "none", afficherEspaces: true, afficherTelechargements: true,
                          afficherCorbeille: true, cartes: { heure: true, data: true, meteo: true, agenda: true } })
    property string nomEspace: ""
    property string fondActuel: ""
    property string fondPersonnel: ""
    property bool rangement: false

    function relire() {
        commande.lancer(script + "etat", function (s) { try { page.etat = JSON.parse(s.trim()) } catch (e) {} })
    }
    function changer(cle, valeur) {
        var e = JSON.parse(JSON.stringify(etat))
        e[cle] = valeur
        etat = e
    }
    Component.onCompleted: {
        relire()
        commande.lancer("qdbus6 org.kde.ActivityManager /ActivityManager/Activities ActivityName "
                        + "\"$(qdbus6 org.kde.ActivityManager /ActivityManager/Activities CurrentActivity)\"",
                        function (s) { page.nomEspace = s.trim() })
        commande.lancer("kreadconfig6 --file samaosrc --group Bureau --key FondPersonnel", function (s) { page.fondPersonnel = s.trim() })
        commande.lancer("qdbus6 org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "
                        + commande.q("var d = desktopForScreen(0); d.currentConfigGroup = ['Wallpaper', 'org.kde.image', 'General']; print(d.readConfig('Image'));"),
                        function (s) { page.fondActuel = s.trim() })
    }

    // ——— La Natte ———
    Groupe {
        titre: "La Natte"
        Ligne {
            titre: "Taille"
            detail: "Des éléments plus grands se touchent plus facilement ; une Natte compacte laisse plus de place"
            Segments {
                choix: ["Compacte", "Normale", "Grande"]
                indexChoisi: ["compacte", "normale", "grande"].indexOf(page.etat.taille) >= 0 ? ["compacte", "normale", "grande"].indexOf(page.etat.taille) : 1
                onChoisi: index => {
                    var t = ["compacte", "normale", "grande"][index]
                    page.changer("taille", t)
                    commande.lancer(page.script + "taille " + t)
                }
            }
        }
        Ligne {
            titre: "Masquer la Natte"
            detail: page.etat.masquage === "autohide" ? "Elle réapparaît quand la souris touche le bas de l'écran"
                  : page.etat.masquage === "dodgewindows" ? "Elle s'efface quand une fenêtre passe dessous, et revient au bord de l'écran"
                  : "Elle reste toujours visible"
            derniere: true
            Segments {
                choix: ["Jamais", "Sous les fenêtres", "Toujours"]
                indexChoisi: page.etat.masquage === "autohide" ? 2 : page.etat.masquage === "dodgewindows" ? 1 : 0
                onChoisi: index => {
                    var m = ["none", "dodgewindows", "autohide"][index]
                    page.changer("masquage", m)
                    commande.lancer(page.script + "masquage " + m)
                }
            }
        }
    }

    Groupe {
        titre: "Dans la Natte"
        Ligne {
            titre: "Les autres Espaces"
            detail: "L'Espace où vous êtes reste toujours affiché, avec ses applications"
            Interrupteur {
                actif: page.etat.afficherEspaces
                onBascule: a => { page.changer("afficherEspaces", a); commande.lancer(page.script + "element afficherEspaces " + a) }
            }
        }
        Ligne {
            titre: "Téléchargements"
            detail: "Ouvre le dossier ; un point signale les nouveaux fichiers"
            Interrupteur {
                actif: page.etat.afficherTelechargements
                onBascule: a => { page.changer("afficherTelechargements", a); commande.lancer(page.script + "element afficherTelechargements " + a) }
            }
        }
        Ligne {
            titre: "Corbeille"
            detail: "Le nombre d'éléments à vider s'affiche dessus"
            derniere: true
            Interrupteur {
                actif: page.etat.afficherCorbeille
                onBascule: a => { page.changer("afficherCorbeille", a); commande.lancer(page.script + "element afficherCorbeille " + a) }
            }
        }
    }

    // ——— Bureau de l'Espace actif ———
    Groupe {
        titre: page.nomEspace ? "Cartes du bureau · Espace « " + page.nomEspace + " »" : "Cartes du bureau"
        Repeater {
            model: [
                { cle: "heure", titre: "Heure", detail: "L'heure, la date et un mot d'accueil" },
                { cle: "data", titre: "Data", detail: "La data consommée et le forfait" },
                { cle: "meteo", titre: "Météo", detail: "Le temps qu'il fait et les prochains jours" },
                { cle: "agenda", titre: "Agenda", detail: "Vos rendez-vous et les jours fériés" }
            ]
            delegate: Ligne {
                titre: modelData.titre
                detail: modelData.detail
                Interrupteur {
                    actif: !!page.etat.cartes[modelData.cle]
                    onBascule: a => {
                        var c = JSON.parse(JSON.stringify(page.etat.cartes))
                        c[modelData.cle] = a
                        page.changer("cartes", c)
                        commande.lancer(page.script + "carte org.samaos.carte." + modelData.cle + " " + (a ? "oui" : "non"))
                    }
                }
            }
        }
        Ligne {
            titre: "Ranger les cartes"
            detail: page.rangement ? "Cartes remises en colonne" : "Les remet en colonne à gauche, bien espacées. Chaque Espace a ses propres cartes."
            derniere: true
            BoutonSama {
                text: "Ranger"
                onClicked: commande.lancer(page.script + "ranger", function () { page.rangement = true })
            }
        }
    }

    // ——— Fond d'écran ———
    FileDialog {
        id: choixImage
        title: "Choisir une image"
        nameFilters: ["Images (*.png *.jpg *.jpeg *.webp *.svg)"]
        onAccepted: {
            var chemin = decodeURIComponent(String(selectedFile).replace("file://", ""))
            page.fondActuel = "file://" + chemin
            page.fondPersonnel = chemin
            commande.lancer("plasma-apply-wallpaperimage " + commande.q(chemin) + "; kwriteconfig6 --file samaosrc --group Bureau --key FondPersonnel " + commande.q(chemin))
        }
    }
    Groupe {
        titre: "Fond d'écran"
        Item {
            Layout.fillWidth: true
            Layout.preferredHeight: 156
            Row {
                anchors.centerIn: parent
                spacing: 18
                Repeater {
                    model: [{ nom: "Sama Aube", dossier: "SamaAube", image: "file:///usr/share/samaos/fonds/sama-aube.svg" },
                            { nom: "Sama Nuit", dossier: "SamaNuit", image: "file:///usr/share/samaos/fonds/sama-nuit.svg" },
                            { nom: "Votre image", dossier: "", image: page.fondPersonnel ? "file://" + page.fondPersonnel : "" }]
                    delegate: MouseArea {
                        readonly property bool choisi: modelData.dossier ? page.fondActuel.indexOf(modelData.dossier) >= 0
                                                                         : (page.fondPersonnel !== "" && page.fondActuel.indexOf(page.fondPersonnel) >= 0)
                        width: 168
                        height: 130
                        cursorShape: Qt.PointingHandCursor
                        onClicked: {
                            if (!modelData.dossier) { choixImage.open(); return }
                            page.fondActuel = "/usr/share/wallpapers/" + modelData.dossier
                            page.fondPersonnel = ""
                            commande.lancer("plasma-apply-wallpaperimage /usr/share/wallpapers/" + modelData.dossier
                                            + "; kwriteconfig6 --file samaosrc --group Bureau --key FondPersonnel ''")
                        }
                        Column {
                            spacing: 8
                            Rectangle {
                                width: 168
                                height: 96
                                radius: 12
                                color: Couleurs.carte
                                border.width: parent.parent.choisi ? 2 : 0
                                border.color: Couleurs.laterite
                                clip: true
                                Image {
                                    anchors.fill: parent
                                    anchors.margins: parent.parent.parent.choisi ? 3 : 0
                                    visible: modelData.image !== ""
                                    source: modelData.image
                                    sourceSize.width: 336
                                    sourceSize.height: 192
                                    fillMode: Image.PreserveAspectCrop
                                }
                                Text {
                                    anchors.centerIn: parent
                                    visible: modelData.image === ""
                                    text: "+"
                                    font.pixelSize: 26
                                    font.weight: Font.Light
                                    color: Couleurs.texte2
                                }
                            }
                            Text { anchors.horizontalCenter: parent.horizontalCenter; text: modelData.nom; font.pixelSize: 12; color: Couleurs.texte2 }
                        }
                    }
                }
            }
        }
        Ligne {
            titre: "Changer avec le mode clair ou sombre"
            detail: page.fondPersonnel ? "Votre image reste en place quand vous changez de mode"
                                       : "Sama Aube le jour, Sama Nuit en mode sombre"
            derniere: true
        }
    }
}
