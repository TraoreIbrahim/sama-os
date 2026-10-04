// À propos : version de Sama, matériel de l'ordinateur, mises à jour.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "À propos"

    property string version: ""
    property string base: ""
    property string noyau: ""
    property string nom: ""
    property string processeur: ""
    property string memoire: ""
    property string stockage: ""
    property string graphique: ""

    function go(o) { return o >= 1073741824 ? (o / 1073741824).toLocaleString(Qt.locale(), "f", 1).replace(",0", "") + " Go" : Math.round(o / 1048576) + " Mo" }

    Commande { id: commande }
    Component.onCompleted: {
        commande.lancer(". /etc/os-release; echo \"$VERSION\"", function (s) { page.version = s.trim() })
        commande.lancer("cat /etc/debian_version", function (s) { page.base = s.trim() })
        commande.lancer("uname -r", function (s) { page.noyau = s.trim() })
        commande.lancer("cat /etc/hostname", function (s) { page.nom = s.trim() })
        commande.lancer("LC_ALL=C lscpu | sed -n 's/^Model name: *//p' | head -1; nproc; uname -m", function (s) {
            var l = s.trim().split("\n")
            var modele = (l[0] && l[0] !== "-") ? l[0] : "Processeur " + (l[2] || "")
            page.processeur = modele + (l[1] ? " · " + l[1] + " cœurs" : "") })
        commande.lancer("awk '/MemTotal/ {print $2 * 1024}' /proc/meminfo", function (s) { page.memoire = page.go(Number(s)) })
        commande.lancer("df -B1 --output=size,avail / | tail -1", function (s) {
            var p = s.trim().split(/\s+/); page.stockage = page.go(Number(p[0])) + " · " + page.go(Number(p[1])) + " libres" })
        commande.lancer("lspci 2>/dev/null | sed -n 's/.*\\(VGA\\|3D\\|Display\\)[^:]*: //p' | head -1", function (s) { page.graphique = s.trim() || "—" })
    }

    // En-tête : logo, nom, version
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 120
        radius: 14
        color: Couleurs.carte
        RowLayout {
            anchors.fill: parent
            anchors.margins: 22
            spacing: 20
            Image {
                Layout.preferredWidth: 64
                Layout.preferredHeight: 64
                source: "file:///usr/share/samaos/logo-sama.svg"
                sourceSize.width: 128
                sourceSize.height: 128
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4
                Text { text: "Sama"; font.pixelSize: 26; font.weight: Font.Light; color: Couleurs.texte }
                Text {
                    Layout.fillWidth: true
                    wrapMode: Text.WordWrap
                    text: "Version " + (page.version || "…") + " · basé sur Debian " + page.base + " · noyau Linux " + page.noyau
                    font.pixelSize: 13
                    color: Couleurs.texte2
                }
            }
        }
    }

    Groupe {
        titre: "Cet ordinateur"
        Ligne { titre: "Nom de l'appareil"; detail: "Visible sur le réseau local"; Text { text: page.nom; font.pixelSize: 13; color: Couleurs.texte2 } }
        Ligne { titre: "Processeur"; Text { text: page.processeur; font.pixelSize: 13; color: Couleurs.texte2; elide: Text.ElideRight; width: Math.min(implicitWidth, 360) } }
        Ligne { titre: "Mémoire"; Text { text: page.memoire; font.pixelSize: 13; color: Couleurs.texte2 } }
        Ligne { titre: "Stockage"; Text { text: page.stockage; font.pixelSize: 13; color: Couleurs.texte2 } }
        Ligne { titre: "Carte graphique"; derniere: true; Text { text: page.graphique; font.pixelSize: 13; color: Couleurs.texte2; elide: Text.ElideRight; width: Math.min(implicitWidth, 360) } }
    }

    Groupe {
        titre: "Logiciel"
        Ligne {
            titre: "Mises à jour"
            detail: "Sugu installe les mises à jour de Sama et de vos applications"
            BoutonSama { text: "Rechercher des mises à jour"; onClicked: commande.lancer("plasma-discover --mode update") }
        }
        Ligne {
            titre: "Licences libres"
            detail: "GPL, LGPL, MIT et autres"
            derniere: true
            cliquable: true
            onClique: commande.lancer("xdg-open /usr/share/common-licenses")
            Text { text: "›"; font.pixelSize: 20; color: Couleurs.texte3 }
        }
    }

    Text {
        Layout.alignment: Qt.AlignHCenter
        Layout.topMargin: 10
        text: "Construit en Afrique, pour l'Afrique."
        font.pixelSize: 13
        color: Couleurs.texte3
    }
}
