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
    property string format: ""
    property bool formatChange: false
    // Fuseaux horaires d'Afrique d'abord (noms clairs), puis quelques autres
    readonly property var fuseaux: [
        { id: "Africa/Abidjan", nom: "Abidjan, Dakar, Bamako (GMT)" }, { id: "Africa/Lagos", nom: "Lagos, Douala, Kinshasa (GMT+1)" },
        { id: "Africa/Cairo", nom: "Le Caire (GMT+2)" }, { id: "Africa/Johannesburg", nom: "Johannesburg, Lubumbashi (GMT+2)" },
        { id: "Africa/Nairobi", nom: "Nairobi, Addis-Abeba (GMT+3)" }, { id: "Africa/Casablanca", nom: "Casablanca" },
        { id: "Europe/Paris", nom: "Paris, Bruxelles" }, { id: "Europe/London", nom: "Londres" }, { id: "America/New_York", nom: "New York" }
    ]
    readonly property var formats: [
        { locale: "fr_CI.UTF-8", nom: "Côte d'Ivoire" }, { locale: "fr_SN.UTF-8", nom: "Sénégal" }, { locale: "fr_ML.UTF-8", nom: "Mali" },
        { locale: "fr_BF.UTF-8", nom: "Burkina Faso" }, { locale: "fr_BJ.UTF-8", nom: "Bénin" }, { locale: "fr_TG.UTF-8", nom: "Togo" },
        { locale: "fr_NE.UTF-8", nom: "Niger" }, { locale: "fr_GN.UTF-8", nom: "Guinée" }, { locale: "fr_CM.UTF-8", nom: "Cameroun" },
        { locale: "fr_CD.UTF-8", nom: "RD Congo" }, { locale: "fr_GA.UTF-8", nom: "Gabon" }, { locale: "sw_KE.UTF-8", nom: "Kenya" },
        { locale: "fr_FR.UTF-8", nom: "France" }, { locale: "en_US.UTF-8", nom: "États-Unis" }, { locale: "en_GB.UTF-8", nom: "Royaume-Uni" }
    ]

    Commande { id: commande }
    Component.onCompleted: {
        commande.lancer("kreadconfig6 --file plasma-localerc --group Translations --key LANGUAGE", function (s) {
            var l = s.trim().split(":")[0]
            page.langue = l || (Qt.locale().name.indexOf("fr") === 0 ? "fr" : Qt.locale().name.indexOf("sw") === 0 ? "sw" : "en_US")
        })
        commande.lancer("kreadconfig6 --file kxkbrc --group Layout --key LayoutList; localectl status | sed -n 's/.*X11 Layout: *//p'", function (s) {
            var l = s.trim().split("\n").filter(function (x) { return x }); page.clavier = (l[0] || "fr").toUpperCase().replace(/,/g, ", ") })
        commande.lancer("timedatectl show -p Timezone --value", function (s) { page.fuseau = s.trim() })
        commande.lancer("kreadconfig6 --file plasma-localerc --group Formats --key LC_MONETARY --default fr_CI.UTF-8", function (s) { page.format = s.trim() })
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
        LigneSousPage { titre: "Clavier"; detail: page.clavier ? "Actuellement : " + page.clavier : ""; sousPage: "Clavier" }
        Ligne {
            titre: "Fuseau horaire"
            detail: "Heure de l'ordinateur ; le système vous demandera le mot de passe administrateur"
            ListeDeroulante {
                implicitWidth: 230
                model: page.fuseaux.map(function (f) { return f.nom })
                currentIndex: Math.max(0, page.fuseaux.map(function (f) { return f.id }).indexOf(page.fuseau))
                onActivated: index => commande.lancer("timedatectl set-timezone " + page.fuseaux[index].id, function () {
                    commande.lancer("timedatectl show -p Timezone --value", function (s) { page.fuseau = s.trim() })
        commande.lancer("kreadconfig6 --file plasma-localerc --group Formats --key LC_MONETARY --default fr_CI.UTF-8", function (s) { page.format = s.trim() }) })
            }
        }
        Ligne {
            titre: "Formats régionaux"
            detail: page.formatChange ? "S'appliquera à la prochaine ouverture de session" : "Date, nombres et monnaie (franc CFA…)"
            derniere: true
            ListeDeroulante {
                implicitWidth: 230
                model: page.formats.map(function (f) { return f.nom })
                currentIndex: Math.max(0, page.formats.map(function (f) { return f.locale }).indexOf(page.format))
                onActivated: index => {
                    var l = page.formats[index].locale
                    page.format = l
                    page.formatChange = true
                    var g = "kwriteconfig6 --file plasma-localerc --group Formats --key "
                    commande.lancer(["LC_NUMERIC", "LC_TIME", "LC_MONETARY", "LC_MEASUREMENT", "LC_PAPER"].map(function (k) { return g + k + " " + l }).join(" && "))
                }
            }
        }
    }
}
