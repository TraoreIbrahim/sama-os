// Langue et région : langue de Sama (français, anglais, swahili), clavier, fuseau horaire.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Langue et région"

    readonly property var langues: [
        { nom: "Français", code: "fr", lang: "fr_FR.UTF-8" },
        { nom: "English", code: "en_US", lang: "en_US.UTF-8" },
        { nom: "Kiswahili", code: "sw", lang: "sw_KE.UTF-8" }
    ]
    property string langue: "fr"
    property bool changee: false
    property string clavier: ""
    property string fuseau: ""

    Commande { id: commande }
    Component.onCompleted: {
        commande.lancer("kreadconfig6 --file plasma-localerc --group Translations --key LANGUAGE", function (s) {
            var l = s.trim().split(":")[0]
            page.langue = l || (Qt.locale().name.indexOf("fr") === 0 ? "fr" : Qt.locale().name.indexOf("sw") === 0 ? "sw" : "en_US")
        })
        commande.lancer("kreadconfig6 --file kxkbrc --group Layout --key LayoutList; localectl status | sed -n 's/.*X11 Layout: *//p'", function (s) {
            var l = s.trim().split("\n").filter(function (x) { return x }); page.clavier = (l[0] || "fr").toUpperCase().replace(/,/g, ", ") })
        commande.lancer("timedatectl show -p Timezone --value", function (s) { page.fuseau = s.trim() })
    }

    Groupe {
        titre: "Langue"
        Ligne {
            titre: "Langue de Sama"
            detail: page.changee ? "S'appliquera à la prochaine ouverture de session" : "Menus, applications et messages"
            Segments {
                choix: page.langues.map(function (l) { return l.nom })
                indexChoisi: Math.max(0, page.langues.map(function (l) { return l.code }).indexOf(page.langue))
                onChoisi: index => {
                    var l = page.langues[index]
                    page.langue = l.code
                    page.changee = true
                    commande.lancer("kwriteconfig6 --file plasma-localerc --group Translations --key LANGUAGE " + l.code
                                    + " && kwriteconfig6 --file plasma-localerc --group Formats --key LANG " + l.lang)
                }
            }
        }
        Ligne {
            visible: page.changee
            titre: "Changer de langue maintenant"
            detail: "Ferme la session : enregistrez d'abord vos documents"
            derniere: true
            BoutonSama { principal: true; text: "Fermer la session"; onClicked: commande.lancer("qdbus6 org.kde.LogoutPrompt /LogoutPrompt promptLogout") }
        }
    }

    Groupe {
        titre: "Clavier et heure"
        LigneAvancee { titre: "Disposition du clavier"; detail: page.clavier ? "Actuellement : " + page.clavier : ""; module: "kcm_keyboard" }
        LigneAvancee { titre: "Fuseau horaire"; detail: page.fuseau.replace("Africa/", "Afrique · ").replace("_", " "); module: "kcm_clock" }
        LigneAvancee { titre: "Formats"; detail: "Date, nombres, monnaie (franc CFA…)"; module: "kcm_regionandlang"; derniere: true }
    }
}
