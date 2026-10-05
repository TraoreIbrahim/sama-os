// Data et mises à jour (maquette reg-08) : forfait du mois et data utilisée depuis le renouvellement,
// 7 derniers jours (/usr/libexec/samaos/data.py, d'après vnstat), état des mises à jour et recherche,
// réglages de data : économie de data (réglage enregistré, à venir dans les applications), connexion mesurée,
// mises à jour la nuit (/usr/libexec/samaos/mises-a-jour-nuit.sh, réglage de l'ordinateur).
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Data et mises à jour"

    property var mesure: ({ forfaitMo: 1024, renouvellement: 1, prochainRenouvellement: "", source: "allumage", utiliseMo: 0, jours: [] })
    property bool mesuree: false
    property string connexion: ""
    property bool economie: false
    property bool nuit: false
    property real verifie: 0          // date de la dernière recherche de mises à jour (secondes)
    property int disponibles: 0
    property bool recherche: false
    readonly property var forfaits: [500, 1024, 2048, 5120, 10240]
    readonly property var libelles: ["500 Mo", "1 Go", "2 Go", "5 Go", "10 Go"]
    readonly property real part: mesure.forfaitMo > 0 ? Math.min(1, mesure.utiliseMo / mesure.forfaitMo) : 0
    readonly property real maxJour: Math.max(1, Math.max.apply(null, (mesure.jours || []).map(function (j) { return j.mo })))

    readonly property var joursDuMois: {
        var l = []
        for (var i = 1; i <= 28; i++) l.push("Le " + (i === 1 ? "1er" : i))
        return l
    }
    function lisible(mo) {
        if (mo >= 1024) return (mo / 1024).toLocaleString(Qt.locale(), "f", 1).replace(/[,.]0$/, "") + " Go"
        return Math.round(mo) + " Mo"
    }
    function reglage(cle, valeur, ensuite) { commande.lancer("kwriteconfig6 --file samaosrc --group Data --key " + cle + " " + valeur, ensuite) }
    function relireData() { commande.lancer("python3 /usr/libexec/samaos/data.py etat", function (s) { try { page.mesure = JSON.parse(s) } catch (e) {} }) }
    function relireMisesAJour() {
        commande.lancer("sh /usr/libexec/samaos/mises-a-jour-nuit.sh statut", function (s) {
            var m = /verifie=(\d+) disponibles=(\d+)/.exec(s)
            if (m) { page.verifie = Number(m[1]); page.disponibles = Number(m[2]) }
        })
    }
    // « aujourd'hui à 09:12 », « hier à 22:40 », « le 3 octobre »
    function quand(secondes) {
        if (!secondes) return ""
        var d = new Date(secondes * 1000)
        var minuitJour = new Date(d.getTime()); minuitJour.setHours(0, 0, 0, 0)
        var minuit = new Date(); minuit.setHours(0, 0, 0, 0)
        var jours = Math.round((minuit.getTime() - minuitJour.getTime()) / 86400000)
        var heure = d.toLocaleTimeString(Qt.locale(), "HH:mm")
        if (jours === 0) return "aujourd'hui à " + heure
        if (jours === 1) return "hier à " + heure
        return "le " + d.toLocaleDateString(Qt.locale(), "d MMMM")
    }

    Commande { id: commande }
    Component.onCompleted: {
        relireData()
        relireMisesAJour()
        commande.lancer("kreadconfig6 --file samaosrc --group Data --key economie --default false", function (s) { page.economie = s.trim() === "true" })
        commande.lancer("sh /usr/libexec/samaos/mises-a-jour-nuit.sh etat", function (s) { page.nuit = s.trim() === "actif" })
        commande.lancer("nmcli -t -f NAME,TYPE connection show --active | grep -v ':loopback$' | head -1 | cut -d: -f1", function (s) {
            page.connexion = s.trim()
            if (page.connexion) commande.lancer("nmcli -g connection.metered connection show " + commande.q(page.connexion), function (m) { page.mesuree = m.trim() === "yes" })
        })
    }
    // Le compteur avance pendant que la page est ouverte
    Timer { interval: 30000; running: true; repeat: true; onTriggered: page.relireData() }

    // ——— Forfait du mois ———
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: forfait.implicitHeight + 32
        radius: 14
        color: Couleurs.carte
        ColumnLayout {
            id: forfait
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 16
            anchors.leftMargin: 18
            anchors.rightMargin: 18
            spacing: 0
            RowLayout {
                Layout.fillWidth: true
                spacing: 12
                Rectangle {
                    Layout.preferredWidth: 34
                    Layout.preferredHeight: 34
                    radius: 10
                    color: Couleurs.sombre ? Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.3) : "#DBEAE2"
                    Canvas {
                        anchors.centerIn: parent
                        width: 18
                        height: 18
                        onPaint: {
                            var c = getContext("2d"); c.reset(); c.scale(18 / 24, 18 / 24)
                            c.strokeStyle = Couleurs.sombre ? "#A3D6C1" : Couleurs.foret; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                            c.path = "M5 19c0-8 5-13 14-14c-1 9-6 14-14 14z M5 19l7-7"; c.stroke()
                        }
                    }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 2
                    Text { text: "Forfait mensuel " + page.lisible(page.mesure.forfaitMo); font.pixelSize: 14; font.weight: Font.Medium; color: Couleurs.texte }
                    Text {
                        Layout.fillWidth: true
                        wrapMode: Text.WordWrap
                        text: page.mesure.source === "vnstat"
                              ? page.lisible(page.mesure.utiliseMo) + " utilisés · renouvellement le " + page.mesure.prochainRenouvellement
                              : page.lisible(page.mesure.utiliseMo) + " depuis l'allumage · le suivi du mois commence"
                        font.pixelSize: 12
                        color: Couleurs.texte2
                    }
                }
                Row {
                    spacing: 4
                    Text {
                        id: restants
                        text: page.lisible(Math.max(0, page.mesure.forfaitMo - page.mesure.utiliseMo))
                        font.pixelSize: 20
                        font.weight: Font.Medium
                        color: Couleurs.texte
                    }
                    Text { anchors.baseline: restants.baseline; text: "restants"; font.pixelSize: 12; color: Couleurs.texte2 }
                }
            }
            Rectangle {
                Layout.topMargin: 14
                Layout.fillWidth: true
                Layout.preferredHeight: 8
                radius: 4
                color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.09)
                Rectangle {
                    width: parent.width * page.part
                    height: parent.height
                    radius: 4
                    color: Couleurs.laterite
                    Behavior on width { NumberAnimation { duration: 300; easing.type: Easing.OutCubic } }
                }
            }
            RowLayout {
                Layout.topMargin: 6
                Layout.fillWidth: true
                Text { text: "0"; font.pixelSize: 11; color: Couleurs.texte3 }
                Item { Layout.fillWidth: true }
                Text { text: "Alerte à " + page.lisible(page.mesure.forfaitMo * 0.8) + " puis à " + page.lisible(page.mesure.forfaitMo); font.pixelSize: 11; color: Couleurs.texte3 }
                Item { Layout.fillWidth: true }
                Text { text: page.lisible(page.mesure.forfaitMo); font.pixelSize: 11; color: Couleurs.texte3 }
            }
        }
    }

    // ——— 7 derniers jours ———
    Rectangle {
        visible: (page.mesure.jours || []).length > 0
        Layout.fillWidth: true
        Layout.preferredHeight: 168
        radius: 14
        color: Couleurs.carte
        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 14
            anchors.leftMargin: 18
            anchors.rightMargin: 18
            spacing: 10
            RowLayout {
                Layout.fillWidth: true
                Text { Layout.fillWidth: true; text: "7 derniers jours"; font.pixelSize: 14; font.weight: Font.Medium; color: Couleurs.texte }
                Text { text: page.lisible(page.mesure.semaineMo || 0) + " · aujourd'hui " + page.lisible(page.mesure.aujourdhuiMo || 0); font.pixelSize: 12; color: Couleurs.texte2 }
            }
            RowLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 10
                Repeater {
                    model: page.mesure.jours || []
                    delegate: ColumnLayout {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        Layout.preferredWidth: 1
                        spacing: 6
                        Item {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            Rectangle {
                                anchors.bottom: parent.bottom
                                anchors.horizontalCenter: parent.horizontalCenter
                                width: Math.min(parent.width, 34)
                                height: Math.max(3, parent.height * modelData.mo / page.maxJour)
                                radius: 5
                                color: modelData.aujourdhui ? Couleurs.laterite
                                       : (Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.16) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14))
                            }
                        }
                        Text {
                            Layout.alignment: Qt.AlignHCenter
                            text: modelData.nom
                            font.pixelSize: 11
                            font.weight: modelData.aujourdhui ? Font.DemiBold : Font.Normal
                            color: modelData.aujourdhui ? Couleurs.texte : Couleurs.texte3
                        }
                    }
                }
            }
        }
    }

    // ——— Mises à jour ———
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 64
        radius: 14
        color: Couleurs.carte
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 16
            anchors.rightMargin: 16
            spacing: 12
            Rectangle {
                Layout.preferredWidth: 30
                Layout.preferredHeight: 30
                radius: 9
                color: page.disponibles > 0 ? Couleurs.selection : (Couleurs.sombre ? Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.3) : "#DBEAE2")
                Canvas {
                    anchors.centerIn: parent
                    width: 16
                    height: 16
                    property bool aFaire: page.disponibles > 0
                    onAFaireChanged: requestPaint()
                    onPaint: {
                        var c = getContext("2d"); c.reset(); c.scale(16 / 24, 16 / 24)
                        c.strokeStyle = aFaire ? Couleurs.lateriteEncre : (Couleurs.sombre ? "#A3D6C1" : Couleurs.foret)
                        c.lineWidth = 2.2; c.lineCap = "round"; c.lineJoin = "round"
                        c.path = aFaire ? "M12 4v11 M7 10l5 5l5-5 M5 20h14" : "M5 12.5l4.5 4.5L19 7.5"
                        c.stroke()
                    }
                }
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text {
                    text: page.recherche ? "Recherche des mises à jour…"
                        : page.disponibles > 1 ? page.disponibles + " mises à jour disponibles"
                        : page.disponibles === 1 ? "1 mise à jour disponible" : "Sama est à jour"
                    font.pixelSize: 14
                    font.weight: Font.Medium
                    color: Couleurs.texte
                }
                Text {
                    visible: text !== ""
                    text: page.verifie ? "Vérifié " + page.quand(page.verifie)
                                         + (page.disponibles > 0 && page.nuit ? " · installées la nuit prochaine" : "") : ""
                    font.pixelSize: 12
                    color: Couleurs.texte2
                }
            }
            BoutonSama {
                visible: page.disponibles > 0
                text: "Voir dans Sugu"
                onClicked: commande.lancer("plasma-discover --mode update >/dev/null 2>&1 &")
            }
            BoutonSama {
                text: "Rechercher"
                enabled: !page.recherche
                onClicked: {
                    page.recherche = true
                    commande.lancer("pkexec /usr/libexec/samaos/mises-a-jour-nuit.sh verifier >/dev/null 2>&1; echo fini", function () {
                        page.recherche = false
                        page.relireMisesAJour()
                    })
                }
            }
        }
    }

    // ——— Forfait ———
    Groupe {
        titre: "Forfait"
        Ligne {
            titre: "Votre forfait data"
            detail: "Sert de repère au compteur, aux alertes et à la carte Data du bureau"
            Segments {
                choix: page.libelles
                indexChoisi: Math.max(0, page.forfaits.indexOf(page.mesure.forfaitMo))
                onChoisi: index => page.reglage("forfaitMo", page.forfaits[index], page.relireData)
            }
        }
        Ligne {
            titre: "Renouvellement"
            detail: "Jour du mois où votre forfait repart à zéro"
            derniere: true
            ListeDeroulante {
                implicitWidth: 130
                model: page.joursDuMois
                currentIndex: page.mesure.renouvellement - 1
                onActivated: index => page.reglage("renouvellement", index + 1, page.relireData)
            }
        }
    }

    // ——— Réglages de data ———
    Groupe {
        titre: "Réglages de data"
        Ligne {
            titre: "Économie de data"
            detail: "Images allégées et vidéos en basse définition dans les applications Sama (bientôt)"
            Interrupteur { actif: page.economie; onBascule: a => { page.economie = a; page.reglage("economie", a) } }
        }
        Ligne {
            titre: "Connexion mesurée"
            detail: page.connexion ? "« " + page.connexion + " » : les mises à jour et téléchargements automatiques attendent une connexion illimitée"
                                   : "Aucune connexion active"
            Interrupteur {
                enabled: page.connexion !== ""
                actif: page.mesuree
                onBascule: a => {
                    page.mesuree = a
                    commande.lancer("nmcli connection modify " + commande.q(page.connexion) + " connection.metered " + (a ? "yes" : "no"))
                }
            }
        }
        Ligne {
            titre: "Mettre à jour la nuit"
            detail: "Entre 1 h et 5 h, quand l'ordinateur est branché et sur une connexion illimitée"
            Interrupteur {
                actif: page.nuit
                onBascule: a => {
                    page.nuit = a
                    // Autorisation refusée ou annulée : l'interrupteur revient à l'état réel
                    commande.lancer("pkexec /usr/libexec/samaos/mises-a-jour-nuit.sh " + (a ? "activer" : "desactiver")
                                    + "; sh /usr/libexec/samaos/mises-a-jour-nuit.sh etat",
                                    function (s) { page.nuit = s.trim() === "actif" })
                }
            }
        }
        Ligne {
            titre: "Partage en réseau local"
            detail: "Recevoir les mises à jour des ordinateurs Sama voisins, sans data"
            Pastille { text: "Bientôt"; teinte: Couleurs.texte3; encre: Couleurs.texte3 }
        }
        Ligne {
            titre: "Recevoir les mises à jour depuis une clé USB"
            detail: "Pour les salles sans connexion"
            derniere: true
            Pastille { text: "Bientôt"; teinte: Couleurs.texte3; encre: Couleurs.texte3 }
        }
    }
}
