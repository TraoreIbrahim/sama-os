// Accessibilité : taille du texte, grand pointeur, loupe, animations réduites, lecteur d'écran.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Accessibilité"
    Commande { id: commande }

    readonly property var tailles: [10, 11, 12, 14]
    readonly property var pointeurs: [24, 36, 48]
    property int taille: 10
    property int pointeur: 24
    property bool loupe: false
    property bool sansAnimations: false
    property bool lecteur: false
    property bool orca: true
    property string note: ""

    Component.onCompleted: commande.lancer(
        "kreadconfig6 --file kdeglobals --group General --key font --default 'Noto Sans,10'; "
        + "kreadconfig6 --file kcminputrc --group Mouse --key cursorSize --default 24; "
        + "kreadconfig6 --file kwinrc --group Plugins --key zoomEnabled --default false; "
        + "kreadconfig6 --file kdeglobals --group KDE --key AnimationDurationFactor --default 1; "
        + "kreadconfig6 --file kaccessrc --group ScreenReader --key Enabled --default false; "
        + "command -v orca >/dev/null && echo oui || echo non", function (s) {
            var l = s.trim().split("\n")
            page.taille = Number((l[0] || "").split(",")[1]) || 10
            page.pointeur = Number(l[1]) || 24
            page.loupe = l[2] === "true"
            page.sansAnimations = Number(l[3]) === 0
            page.lecteur = l[4] === "true"
            page.orca = l[5] === "oui"
        })

    function police(points) {
        var f = function (famille) { return "'" + famille + "," + points + ",-1,5,400,0,0,0,0,0,0,0,0,0,0,1'" }
        var g = "kwriteconfig6 --file kdeglobals --group General --key "
        return g + "font " + f("Noto Sans") + " && " + g + "menuFont " + f("Noto Sans") + " && " + g + "toolBarFont " + f("Noto Sans")
               + " && dbus-send --session --type=signal /KGlobalSettings org.kde.KGlobalSettings.notifyChange int32:1 int32:0"
    }

    Groupe {
        titre: "Vue"
        Ligne {
            titre: "Taille du texte"
            detail: "Menus, fenêtres et applications ; certaines applications suivront à leur prochaine ouverture"
            Segments {
                choix: ["Normal", "Grand", "Très grand", "Maximal"]
                indexChoisi: Math.max(0, page.tailles.indexOf(page.taille))
                onChoisi: index => { page.taille = page.tailles[index]; commande.lancer(page.police(page.taille)) }
            }
        }
        Ligne {
            titre: "Taille du pointeur"
            detail: page.note || "Plus facile à suivre des yeux"
            Segments {
                choix: ["Normal", "Grand", "Très grand"]
                indexChoisi: Math.max(0, page.pointeurs.indexOf(page.pointeur))
                onChoisi: index => {
                    page.pointeur = page.pointeurs[index]
                    commande.lancer("kwriteconfig6 --file kcminputrc --group Mouse --key cursorSize " + page.pointeur
                                    + " && dbus-send --session --type=signal /KGlobalSettings org.kde.KGlobalSettings.notifyChange int32:5 int32:0",
                                    function () { page.note = "Complètement appliqué à la prochaine ouverture de session" })
                }
            }
        }
        Ligne {
            titre: "Loupe"
            detail: "Agrandir l'écran : Méta + « = » ; réduire : Méta + « - »"
            Interrupteur {
                actif: page.loupe
                onBascule: a => { page.loupe = a; commande.lancer("kwriteconfig6 --file kwinrc --group Plugins --key zoomEnabled " + a + " && qdbus6 org.kde.KWin /KWin reconfigure") }
            }
        }
        Ligne {
            titre: "Réduire les animations"
            detail: "Fenêtres et menus apparaissent sans effet de mouvement"
            derniere: true
            Interrupteur {
                actif: page.sansAnimations
                onBascule: a => { page.sansAnimations = a; commande.lancer("kwriteconfig6 --file kdeglobals --group KDE --key AnimationDurationFactor " + (a ? 0 : 1)
                                    + " && dbus-send --session --type=signal /KGlobalSettings org.kde.KGlobalSettings.notifyChange int32:3 int32:0") }
            }
        }
    }
    Groupe {
        titre: "Écoute"
        Ligne {
            titre: "Lecteur d'écran"
            detail: page.orca ? "Lit à voix haute ce qui est à l'écran (Orca)" : "Arrive avec la prochaine version de Sama"
            derniere: true
            Interrupteur {
                enabled: page.orca
                actif: page.lecteur
                onBascule: a => { page.lecteur = a; commande.lancer("kwriteconfig6 --file kaccessrc --group ScreenReader --key Enabled " + a + (a ? " && (orca --replace &)" : " && pkill -x orca")) }
            }
        }
    }
}
