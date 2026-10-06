// Moniteur système de Sama (maquette app-09) : Applications (ce que consomme chacune, « Forcer à quitter »),
// Performances (courbes), Démarrage (ce qui s'ouvre avec la session), Réseau (débits et data).
// Les mesures viennent de /usr/libexec/samaos/moniteur.py. Lancement : sama-moniteur

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.kirigami as Kirigami
import "../reglages"

Window {
    id: fenetre
    width: 1180
    height: 760
    minimumWidth: 840
    minimumHeight: 560
    visible: false      // montrée une fois l'identité de l'application posée
    title: "Moniteur système"
    color: Couleurs.fond

    readonly property string moteur: "python3 /usr/libexec/samaos/moniteur.py "
    property string onglet: "applications"      // applications | performances | demarrage | reseau
    property var etat: null
    property var infosData: null
    property var demarrage: []
    property string selection: ""               // application choisie (son .desktop ; « services » : les services système)
    property string recherche: ""
    property string tri: "cpu"                  // cpu | nom | memoire | energie
    // Historique des 60 dernières mesures (2 minutes)
    property var histoCpu: []
    property var histoMemoire: []
    property var histoLecture: []
    property var histoEcriture: []
    property var histoRecu: []
    property var histoEnvoye: []

    readonly property color rouge: "#A3322A"
    readonly property color ambre: Couleurs.sombre ? "#F2C879" : "#7A4E0C"

    // ——— Mise en forme ———
    function virgule(x, n) { return Number(x).toFixed(n).replace(".", ",") }
    function taille(o) {
        if (o >= 1073741824) return virgule(o / 1073741824, 1) + " Go"
        if (o >= 1048576) return Math.round(o / 1048576) + " Mo"
        return Math.max(0, Math.round(o / 1024)) + " Ko"
    }
    function debit(o) {
        if (o >= 1048576) return virgule(o / 1048576, 1) + " Mo/s"
        return Math.round(o / 1024) + " Ko/s"
    }
    function pourcent(x) { return virgule(x, 1) + " %" }
    function duree(s) {
        var j = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60)
        if (j) return j + " j " + h + " h"
        if (h) return h + " h " + String(m).padStart(2, "0")
        return m + " min"
    }
    function couleurEnergie(e) { return e === "Élevée" ? rouge : e === "Moyenne" ? ambre : e === "Faible" ? Couleurs.foret : Couleurs.texte3 }
    function ajouter(liste, v) { var l = liste.slice(-59); l.push(v); return l }

    // Lignes du tableau : les applications et les services système, triées, filtrées par la recherche.
    // Tant que la souris est sur la liste, l'ordre ne bouge pas (on viserait sinon la mauvaise ligne).
    property bool survolListe: false
    property var ordre: []                      // clés, dans l'ordre affiché
    readonly property var toutes: {
        if (!etat) return []
        var l = etat.applications.slice()
        var s = etat.services
        l.push({ cle: "services", nom: "Services système", icone: "", cpu: s.cpu, memoire: s.memoire, energie: s.energie,
                 processus: s.processus, detail: s.processus + " processus", noms: s.noms })
        return l
    }
    function trier(force) {
        var rang = { "Nulle": 0, "Faible": 1, "Moyenne": 2, "Élevée": 3 }
        var l = toutes.slice()
        if (survolListe && !force) {
            // (figé : les nouvelles applications s'ajoutent à la fin)
            var o = ordre.slice()
            l.forEach(function (a) { if (o.indexOf(a.cle) < 0) o.push(a.cle) })
            ordre = o
            return
        }
        l.sort(function (a, b) {
            if (tri === "nom") return a.nom.localeCompare(b.nom)
            if (tri === "memoire") return b.memoire - a.memoire
            if (tri === "energie") return (rang[b.energie] - rang[a.energie]) || (b.cpu - a.cpu)
            return b.cpu - a.cpu
        })
        ordre = l.map(function (a) { return a.cle })
    }
    onToutesChanged: trier(false)
    onTriChanged: trier(true)
    onSurvolListeChanged: if (!survolListe) trier(false)
    readonly property var lignes: {
        var q = recherche.trim().toLowerCase()
        var l = toutes.filter(function (a) {
            return !q || a.nom.toLowerCase().indexOf(q) >= 0 || a.noms.some(function (n) { return n.toLowerCase().indexOf(q) >= 0 })
        })
        var o = ordre
        l.sort(function (a, b) { return o.indexOf(a.cle) - o.indexOf(b.cle) })
        return l
    }
    readonly property var choisie: {
        for (var i = 0; i < lignes.length; i++) if (lignes[i].cle === selection) return lignes[i]
        return null
    }

    Commande { id: commande }
    function relever() {
        commande.lancer(moteur + "etat", function (s) {
            var e
            try { e = JSON.parse(s) } catch (x) { return }
            fenetre.etat = e
            histoCpu = ajouter(histoCpu, e.processeur.pourcentage)
            histoMemoire = ajouter(histoMemoire, e.memoire.utilisee)
            histoLecture = ajouter(histoLecture, e.disque.lecture)
            histoEcriture = ajouter(histoEcriture, e.disque.ecriture)
            histoRecu = ajouter(histoRecu, e.reseau.recu)
            histoEnvoye = ajouter(histoEnvoye, e.reseau.envoye)
        })
    }
    function releverData() { commande.lancer(moteur + "data", function (s) { try { fenetre.infosData = JSON.parse(s) } catch (x) {} }) }
    function releverDemarrage() { commande.lancer(moteur + "demarrage", function (s) { try { fenetre.demarrage = JSON.parse(s) } catch (x) {} }) }
    Timer { interval: 2000; running: true; repeat: true; triggeredOnStart: true; onTriggered: fenetre.relever() }
    Timer { interval: 30000; running: true; repeat: true; triggeredOnStart: true; onTriggered: fenetre.releverData() }
    onOngletChanged: if (onglet === "demarrage") releverDemarrage()

    Component.onCompleted: {
        // Identité Wayland « samaos-moniteur » (comme le fichier .desktop) : icône dans la barre de titre et la Natte
        Qt.application.domain = ""
        Qt.application.name = "samaos-moniteur"
        visible = true
    }
    Shortcut { sequence: StandardKey.Find; onActivated: { fenetre.onglet = "applications"; champRecherche.forceActiveFocus() } }

    // ——— Barre du haut : onglets et recherche ———
    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 64
            Layout.leftMargin: 24
            Layout.rightMargin: 24
            spacing: 14
            Rectangle {
                implicitWidth: onglets.implicitWidth + 6
                implicitHeight: 36
                radius: 18
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : 0.05)
                Row {
                    id: onglets
                    anchors.centerIn: parent
                    Repeater {
                        model: [["applications", "Applications"], ["performances", "Performances"], ["demarrage", "Démarrage"], ["reseau", "Réseau"]]
                        delegate: MouseArea {
                            readonly property bool actif: fenetre.onglet === modelData[0]
                            width: libelle.implicitWidth + 32
                            height: 30
                            cursorShape: Qt.PointingHandCursor
                            onClicked: fenetre.onglet = modelData[0]
                            Rectangle {
                                anchors.fill: parent
                                radius: 15
                                visible: parent.actif
                                color: Couleurs.champ
                                border.width: 0.5
                                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                            }
                            Text {
                                id: libelle
                                anchors.centerIn: parent
                                text: modelData[1]
                                font.pixelSize: 13
                                font.weight: parent.actif ? Font.DemiBold : Font.Normal
                                color: parent.actif ? Couleurs.texte : Couleurs.texte2
                            }
                        }
                    }
                }
            }
            Item { Layout.fillWidth: true }
            Rectangle {
                visible: fenetre.onglet === "applications"
                Layout.preferredWidth: 240
                Layout.preferredHeight: 34
                radius: 10
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : 0.05)
                border.width: champRecherche.activeFocus ? 1.5 : 0
                border.color: Couleurs.laterite
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 10
                    anchors.rightMargin: 8
                    spacing: 8
                    Picto { trace: "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z M20 20l-4.8-4.8"; encre: Couleurs.texte3 }
                    QQC2.TextField {
                        id: champRecherche
                        Layout.fillWidth: true
                        placeholderText: "Rechercher un processus"
                        placeholderTextColor: Couleurs.texte3
                        color: Couleurs.texte
                        font.pixelSize: 12
                        background: null
                        leftPadding: 0
                        onTextChanged: fenetre.recherche = text
                        Keys.onEscapePressed: { text = ""; focus = false }
                    }
                }
            }
        }

        Loader {
            Layout.fillWidth: true
            Layout.fillHeight: true
            source: fenetre.onglet === "performances" ? "VuePerformances.qml" : fenetre.onglet === "demarrage" ? "VueDemarrage.qml"
                  : fenetre.onglet === "reseau" ? "VueReseau.qml" : "VueApplications.qml"
        }
    }

    // ——— Détails d'une application : ses processus ———
    property var processus: []
    property bool detailsOuverts: false
    function ouvrirDetails() {
        if (!choisie) return
        processus = []
        detailsOuverts = true
        var cle = choisie.cle
        commande.lancer(moteur + "details " + commande.q(cle), function (s) {
            if (cle !== fenetre.selection) return
            try { fenetre.processus = JSON.parse(s) } catch (x) { fenetre.processus = [] }
        })
    }
    Item {
        anchors.fill: parent
        z: 90
        visible: opacity > 0
        opacity: fenetre.detailsOuverts ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: 160; easing.type: Easing.OutCubic } }
        Shortcut { sequence: "Escape"; enabled: fenetre.detailsOuverts; onActivated: fenetre.detailsOuverts = false }
        Rectangle {
            anchors.fill: parent
            color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.45 : 0.28)
            MouseArea { anchors.fill: parent; onClicked: fenetre.detailsOuverts = false }
        }
        Rectangle {
            anchors.centerIn: parent
            width: Math.min(parent.width - 60, 560)
            height: Math.min(parent.height - 60, colonneDetails.implicitHeight + 48)
            radius: 20
            color: Couleurs.fond
            border.width: 0.5
            border.color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.14)
            MouseArea { anchors.fill: parent }
            ColumnLayout {
                id: colonneDetails
                anchors.fill: parent
                anchors.margins: 24
                spacing: 14
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 12
                    Icone { appli: fenetre.choisie; cote: 36 }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 2
                        Text { text: fenetre.choisie ? fenetre.choisie.nom : ""; font.pixelSize: 17; font.weight: Font.Medium; color: Couleurs.texte }
                        Text {
                            text: fenetre.choisie ? fenetre.choisie.processus + (fenetre.choisie.processus > 1 ? " processus · " : " processus · ")
                                  + fenetre.taille(fenetre.choisie.memoire) + " de mémoire" : ""
                            font.pixelSize: 12
                            color: Couleurs.texte2
                        }
                    }
                    MouseArea {
                        Layout.preferredWidth: 30
                        Layout.preferredHeight: 30
                        cursorShape: Qt.PointingHandCursor
                        onClicked: fenetre.detailsOuverts = false
                        Picto { anchors.centerIn: parent; trace: "M7 7l10 10 M17 7L7 17" }
                    }
                }
                Rectangle {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.preferredHeight: Math.min(360, 36 + fenetre.processus.length * 34)
                    radius: 14
                    color: Couleurs.champ
                    border.width: 0.5
                    border.color: Couleurs.ligne
                    clip: true
                    ColumnLayout {
                        anchors.fill: parent
                        spacing: 0
                        RowLayout {
                            Layout.fillWidth: true
                            Layout.preferredHeight: 34
                            Layout.leftMargin: 14
                            Layout.rightMargin: 14
                            Repeater {
                                model: [["Processus", -1], ["PID", 70], ["Processeur", 90], ["Mémoire", 90], ["Lancé à", 70]]
                                Text {
                                    Layout.fillWidth: modelData[1] < 0
                                    Layout.preferredWidth: modelData[1]
                                    horizontalAlignment: modelData[1] < 0 ? Text.AlignLeft : Text.AlignRight
                                    text: modelData[0].toUpperCase()
                                    font.pixelSize: 10
                                    font.weight: Font.DemiBold
                                    font.letterSpacing: 0.3
                                    color: Couleurs.texte3
                                }
                            }
                        }
                        Rectangle { Layout.fillWidth: true; height: 0.5; color: Couleurs.ligne }
                        ListView {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            clip: true
                            model: fenetre.processus
                            QQC2.ScrollBar.vertical: QQC2.ScrollBar {}
                            delegate: Item {
                                width: ListView.view.width
                                height: 34
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 14
                                    anchors.rightMargin: 14
                                    Text { Layout.fillWidth: true; text: modelData.nom; elide: Text.ElideRight; font.pixelSize: 13; color: Couleurs.texte }
                                    Text { Layout.preferredWidth: 70; horizontalAlignment: Text.AlignRight; text: modelData.pid; font.pixelSize: 12; color: Couleurs.texte2; font.features: { "tnum": 1 } }
                                    Text { Layout.preferredWidth: 90; horizontalAlignment: Text.AlignRight; text: fenetre.pourcent(modelData.cpu); font.pixelSize: 13; color: Couleurs.texte; font.features: { "tnum": 1 } }
                                    Text { Layout.preferredWidth: 90; horizontalAlignment: Text.AlignRight; text: fenetre.taille(modelData.memoire); font.pixelSize: 13; color: Couleurs.texte; font.features: { "tnum": 1 } }
                                    Text { Layout.preferredWidth: 70; horizontalAlignment: Text.AlignRight; text: new Date(modelData.debut * 1000).toLocaleTimeString(Qt.locale(), "HH:mm"); font.pixelSize: 12; color: Couleurs.texte2 }
                                }
                            }
                            Text {
                                anchors.centerIn: parent
                                visible: fenetre.processus.length === 0
                                text: "Mesure en cours…"
                                font.pixelSize: 12
                                color: Couleurs.texte3
                            }
                        }
                    }
                }
            }
        }
    }

    // ——— Forcer à quitter ———
    Confirmation {
        id: confirmationQuitter
        property string cle: ""
        property string nom: ""
        titre: "Forcer " + nom + " à quitter ?"
        texte: "Ce qui n'a pas été enregistré dans " + nom + " sera perdu. À faire seulement si l'application ne répond plus."
        action: "Forcer à quitter"
        teinte: fenetre.rouge
        picto: "M12 3v8 M6.3 6.8a8 8 0 1 0 11.4 0"
        onConfirme: {
            occupe = true
            commande.lancer(fenetre.moteur + "quitter " + commande.q(cle), function () {
                confirmationQuitter.occupe = false
                confirmationQuitter.ouverte = false
                fenetre.selection = ""
                fenetre.relever()
            })
        }
    }
    function forcerAQuitter() {
        if (!choisie || choisie.cle === "services") return
        confirmationQuitter.cle = choisie.cle
        confirmationQuitter.nom = choisie.nom
        confirmationQuitter.ouverte = true
    }
}
