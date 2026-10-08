// Sama Docs, le traitement de texte de Sama : interface de Sama, moteur de LibreOffice (module Sama.Moteur), comme
// Sama Sheet. L'accueil au lancement (maquette « Docs · Nouveau document ») ; puis l'en-tête (menus, « Que voulez-vous
// faire ? », le mode, la barre d'outils), le plan à gauche, la page au milieu, la barre d'état.
// Lancement : sama-docs [fichier]  (sans fichier : l'accueil)

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtQuick.Dialogs
import QtCore
import Sama.Moteur
import "../reglages"
import "../sheet"

Window {
    id: fenetre
    width: 1180
    height: 800
    minimumWidth: 760
    minimumHeight: 480
    visible: false
    title: accueilOuvert ? "Sama Docs" : (doc.modifie ? "• " : "") + nomFichier + " — Sama Docs"
    color: Couleurs.fond

    // Identité de Sama Docs : le vert forêt (celui de son icône)
    readonly property color accent: Couleurs.sombre ? "#5FA88C" : "#2F6B57"
    readonly property color accentEncre: Couleurs.sombre ? "#A3D6C1" : "#265646"
    readonly property color accentFond: Qt.rgba(47 / 255, 107 / 255, 87 / 255, Couleurs.sombre ? 0.3 : 0.12)

    readonly property var doc: page.doc
    // (un document récupéré après un arrêt imprévu : la copie de secours est ouverte, son emplacement d'origine est la cible)
    readonly property var reprise: secours.reprise
    readonly property string cheminCible: reprise ? reprise.original : doc.chemin
    readonly property string nomFichier: reprise ? (reprise.original ? reprise.original.split("/").pop() : reprise.titre)
                                         : doc.chemin ? doc.chemin.split("/").pop() : (nomPropose || "Document sans titre")
    readonly property string extension: cheminCible ? cheminCible.split(".").pop().toLowerCase() : ""
    property bool accueilOuvert: false
    property string nomPropose: ""
    property bool planOuvert: true
    property bool fermetureDemandee: false
    function chemin(url) { return decodeURIComponent(String(url).replace(/^file:\/\//, "")) }
    function initiales(nom) {
        var m = String(nom || "").trim().split(/\s+/).filter(function (x) { return x.length > 0 })
        if (!m.length) return "?"
        return (m[0].charAt(0) + (m.length > 1 ? m[m.length - 1].charAt(0) : "")).toUpperCase()
    }

    readonly property alias actions: lesActions
    ActionsDocs { id: lesActions; doc: fenetre.doc; fenetre: fenetre }
    readonly property alias moi: mesInformations
    Commande { id: commande }
    // Les copies de secours (chaque minute, tant que le document n'est pas enregistré)
    Secours {
        id: secours
        doc: fenetre.doc
        appli: "samaos-docs.desktop"
        format: ["docx", "odt"].indexOf(fenetre.extension) >= 0 ? fenetre.extension : "docx"
        titre: fenetre.nomFichier
        original: fenetre.cheminCible
    }

    // ——— Les macros de Sama (SamaDocs) : la réponse arrive par resultatScript ———
    property var attentes: ({})
    function appeler(fonction, args, suite) {
        var j = doc.script(fonction, args || [])
        if (j >= 0) attentes[j] = suite
    }
    Connections {
        target: fenetre.doc
        function onResultatScript(jeton, reussi, valeur) {
            var suite = fenetre.attentes[jeton]
            delete fenetre.attentes[jeton]
            if (suite) suite(reussi, valeur)
        }
    }

    // ——— Le mode : Modification, ou Suggestion (le suivi des modifications du moteur) ———
    readonly property string mode: doc.etats[".uno:TrackChanges"] === "true" ? "Suggestion" : "Modification"
    function choisirMode(m) {
        if ((m === "Suggestion") !== (mode === "Suggestion")) doc.commande(".uno:TrackChanges")
        doc.forceActiveFocus()
    }
    // (les fautes soulignées en rouge : dans un document, oui ; le moteur les cache dans les classeurs)
    property bool orthographe: true
    onOrthographeChanged: doc.commande(".uno:SpellOnline", { Enable: { type: "boolean", value: orthographe } })

    // ——— Fichiers ———
    function enregistrer() {
        if (!cheminCible || ["docx", "odt", "doc", "rtf"].indexOf(extension) < 0) dialogueEnregistrer.open()
        else if (reprise) doc.enregistrerSous(cheminCible)
        else doc.enregistrer()
    }
    function ouvrirFichier(c) {
        if (!doc.chemin && !doc.modifie) {
            accueilOuvert = false
            if (secours.estUneCopie(c)) secours.reprendre(c, function () { doc.ouvrir(c) })
            else doc.ouvrir(c)
        }
        else commande.lancer("systemd-run --user --quiet --collect --slice=app.slice "
                             + commande.q("--unit=app-samaos\\x2ddocs@" + Date.now() + ".service") + " sama-docs " + commande.q(c))
    }
    // (une nouvelle fenêtre ; avecAccueil : pour choisir un modèle)
    function nouvelleFenetre(avecAccueil) {
        commande.lancer("systemd-run --user --quiet --collect --slice=app.slice "
                        + commande.q("--unit=app-samaos\\x2ddocs@" + Date.now() + ".service") + " sama-docs")
    }
    function ouvrirDialogue(genre) {
        if (genre === "ouvrir") dialogueOuvrir.open()
        else if (genre === "pdf") dialoguePdf.open()
        else if (genre === "image") dialogueImage.open()
        else dialogueEnregistrer.open()
    }
    function ouvrirAide(article) {
        commande.lancer("systemd-run --user --quiet --collect --slice=app.slice "
                        + commande.q("--unit=app-samaos\\x2daide@" + Date.now() + ".service") + " sama-aide" + (article ? " " + commande.q(article) : ""))
    }
    function chercherCommande() { entete.ouvrirRecherche() }
    function commenter() { doc.commande(".uno:InsertAnnotation") }

    // ——— Les documents guidés : un seul à la fois (un second clic le referait) ———
    property string modeleEnCours: ""
    property var modeleEnAttente: null
    function appliquerModele(genre, nom) {
        if (modeleEnCours) return
        modeleEnCours = nom
        if (doc.etat !== DocumentLO.Pret) { modeleEnAttente = [genre, nom]; return }
        appeler("SamaDocs.Modele", [genre, moi.nom, moi.adresse, moi.telephone, moi.courriel, moi.ville], function (ok, v) {
            modeleEnCours = ""
            if (!ok || (v && v.charAt(0) === "!")) { message.montrer(v && v.charAt(0) === "!" ? v.slice(1) : "Le modèle n'a pas pu être préparé"); return }
            accueilOuvert = false
            nomPropose = nom
            doc.forceActiveFocus()
        })
    }
    function fermerAccueil() { accueilOuvert = false; doc.forceActiveFocus() }

    FileDialog {
        id: dialogueOuvrir
        title: "Ouvrir un document"
        fileMode: FileDialog.OpenFile
        nameFilters: ["Documents (*.docx *.doc *.odt *.rtf)", "Tous les fichiers (*)"]
        onAccepted: fenetre.ouvrirFichier(fenetre.chemin(selectedFile))
    }
    FileDialog {
        id: dialogueEnregistrer
        title: "Enregistrer le document"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Document Word (*.docx)", "Document OpenDocument (*.odt)", "PDF (*.pdf)"]
        currentFile: fenetre.cheminCible ? "file://" + fenetre.cheminCible
                     : StandardPaths.writableLocation(StandardPaths.DocumentsLocation) + "/" + fenetre.nomFichier + ".docx"
        onAccepted: {
            var c = fenetre.chemin(selectedFile)
            var formats = ["docx", "odt", "pdf"]
            var f = c.indexOf(".") > 0 ? c.split(".").pop().toLowerCase() : formats[Math.max(0, selectedNameFilter.index)]
            if (formats.indexOf(f) < 0) f = "docx"
            if (!c.toLowerCase().endsWith("." + f)) c += "." + f
            doc.enregistrerSous(c, f)
        }
    }
    FileDialog {
        id: dialoguePdf
        title: "Exporter en PDF"
        fileMode: FileDialog.SaveFile
        nameFilters: ["PDF (*.pdf)"]
        currentFile: (fenetre.cheminCible ? "file://" + fenetre.cheminCible.replace(/\.[^.\/]*$/, "")
                      : StandardPaths.writableLocation(StandardPaths.DocumentsLocation) + "/" + fenetre.nomFichier) + ".pdf"
        onAccepted: {
            var c = fenetre.chemin(selectedFile)
            if (!c.toLowerCase().endsWith(".pdf")) c += ".pdf"
            doc.enregistrerSous(c, "pdf")
        }
    }
    FileDialog {
        id: dialogueImage
        title: "Insérer une image"
        fileMode: FileDialog.OpenFile
        nameFilters: ["Images (*.png *.jpg *.jpeg *.webp *.gif *.svg)", "Tous les fichiers (*)"]
        currentFolder: StandardPaths.writableLocation(StandardPaths.PicturesLocation)
        onAccepted: fenetre.doc.commande(".uno:InsertGraphic", { FileName: { type: "string", value: String(selectedFile) } })
    }

    Connections {
        target: fenetre.doc
        function onEnregistre(reussi, c) {
            if (!reussi && !c) { dialogueEnregistrer.open(); return }
            // (un document récupéré dont l'emplacement d'origine n'existe plus, une clé USB retirée : un autre)
            if (!reussi && fenetre.reprise && !c.endsWith(".pdf")) {
                message.montrer("« " + c.split("/").pop() + " » n'a pas pu être remplacé : choisissez où l'enregistrer")
                dialogueEnregistrer.open()
                return
            }
            message.montrer(reussi ? (c.endsWith(".pdf") ? "Exporté en PDF : " + c.split("/").pop() : "Enregistré") : "L'enregistrement a échoué")
            if (reussi && fenetre.fermetureDemandee && !c.endsWith(".pdf")) secours.quitter()
        }
        function onEtatChanged() {
            if (fenetre.doc.etat === DocumentLO.Erreur) message.montrer(fenetre.doc.erreur || "Ce fichier n'a pas pu être ouvert")
            if (fenetre.doc.etat !== DocumentLO.Pret) return
            if (!fenetre.accueilOuvert) fenetre.doc.forceActiveFocus()
            // (document neuf : les styles de Sama ; les fautes soulignées)
            if (!fenetre.doc.chemin) fenetre.appeler("SamaDocs.Preparer", [])
            fenetre.doc.commande(".uno:SpellOnline", { Enable: { type: "boolean", value: fenetre.orthographe } })
            // (récupéré : pas encore enregistré à son emplacement)
            if (fenetre.reprise) fenetre.doc.commande(".uno:Modified", { Modified: { type: "boolean", value: true } })
            if (fenetre.modeleEnAttente) {
                var m = fenetre.modeleEnAttente
                fenetre.modeleEnAttente = null
                fenetre.modeleEnCours = ""
                fenetre.appliquerModele(m[0], m[1])
            }
        }
    }

    Component.onCompleted: {
        var a = Qt.application.arguments
        var dernier = a.length > 0 ? String(a[a.length - 1]) : ""
        if (dernier.indexOf("file://") === 0) dernier = chemin(dernier)
        if (dernier && dernier.indexOf(".qml") < 0 && dernier.indexOf("/") === 0) {
            if (secours.estUneCopie(dernier)) secours.reprendre(dernier, function () { doc.ouvrir(dernier) })
            else doc.ouvrir(dernier)
        } else { doc.nouveau("writer"); accueilOuvert = true; secours.chercherOrphelins() }
        Qt.application.domain = ""
        Qt.application.name = "samaos-docs"
        visible = true
    }

    // Fermer avec des modifications non enregistrées : on demande
    onClosing: close => {
        if (doc.modifie && !fermetureDemandee && !accueilOuvert) {
            close.accepted = false
            confirmationFermer.ouverte = true
        } else if (secours.dossier) {
            // (la copie de secours d'abord)
            close.accepted = false
            secours.quitter()
        }
    }

    // ——— Raccourcis (les autres, comme Ctrl+B ou Ctrl+1 pour un titre, vont au moteur) ———
    Shortcut { sequence: StandardKey.Save; onActivated: fenetre.enregistrer() }
    Shortcut { sequence: StandardKey.SaveAs; onActivated: dialogueEnregistrer.open() }
    Shortcut { sequence: StandardKey.Open; onActivated: dialogueOuvrir.open() }
    Shortcut { sequence: StandardKey.New; onActivated: fenetre.nouvelleFenetre() }
    Shortcut { sequences: ["Ctrl+=", "Ctrl++"]; onActivated: fenetre.doc.zoom = fenetre.doc.zoom * 1.1 }
    Shortcut { sequence: "Ctrl+-"; onActivated: fenetre.doc.zoom = fenetre.doc.zoom / 1.1 }
    Shortcut { sequences: ["Ctrl+K", "Alt+/", "Alt+Shift+/"]; onActivated: entete.ouvrirRecherche() }
    Shortcut { sequence: "Ctrl+Alt+M"; enabled: !fenetre.accueilOuvert; onActivated: fenetre.commenter() }
    Shortcut { sequence: "F1"; onActivated: fenetre.ouvrirAide("") }

    // ——— L'écran ———
    ColumnLayout {
        anchors.fill: parent
        spacing: 0
        EnTeteDocs { id: entete; Layout.fillWidth: true }
        BandeauSecours { Layout.fillWidth: true; reprise: fenetre.reprise; onEnregistrer: fenetre.enregistrer() }
        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0
            PlanDocs {
                visible: fenetre.planOuvert && fenetre.width > 900
                Layout.preferredWidth: 220
                Layout.fillHeight: true
            }
            PageDocs {
                id: page
                Layout.fillWidth: true
                Layout.fillHeight: true
            }
        }
        // ——— Barre d'état : page, mots, langue ; le mode, le format, le zoom ———
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 30
            color: Couleurs.fond
            Rectangle { width: parent.width; height: 0.5; color: Couleurs.bord }
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 16
                anchors.rightMargin: 12
                spacing: 8
                readonly property string pageNo: String(fenetre.doc.etats[".uno:StatePageNumber"] || "").replace(/\s*\(.*\)\s*$/, "")
                readonly property string mots: String(fenetre.doc.etats[".uno:StateWordCount"] || "").split(",")[0].replace(/\s*mot(s)?\b.*/, " mots").trim()
                readonly property string langue: String(fenetre.doc.etats[".uno:LanguageStatus"] || "").split(";")[0].replace(/\s*\(.*\)$/, "")
                Text { text: [parent.pageNo, parent.mots, parent.langue].filter(function (x) { return x }).join("  ·  "); font.pixelSize: 12; color: Couleurs.texte2 }
                Item { Layout.fillWidth: true }
                Text {
                    text: fenetre.mode === "Suggestion" ? "Mode Suggestion : vos changements sont proposés, pas imposés  ·  " : ""
                    font.pixelSize: 12
                    color: Couleurs.texte2
                }
                Text {
                    text: fenetre.reprise ? "Récupéré, pas encore enregistré"
                          : !fenetre.doc.chemin ? "Nouveau document, pas encore enregistré"
                          : fenetre.doc.modifie ? "Modifications non enregistrées"
                          : fenetre.extension === "docx" || fenetre.extension === "doc" ? "Fichier Word (." + fenetre.extension + ")"
                          : fenetre.extension === "odt" ? "Format ouvert (.odt)" : "Enregistré"
                    font.pixelSize: 12
                    color: Couleurs.texte2
                }
                Text { text: "·"; font.pixelSize: 12; color: Couleurs.texte3 }
                Outil { Layout.preferredWidth: 24; Layout.preferredHeight: 24; picto: "M6 12h12"; aide: "Réduire"; onClicked: fenetre.doc.zoom = fenetre.doc.zoom / 1.1 }
                Text { text: Math.round(fenetre.doc.zoom * 100) + " %"; font.pixelSize: 12; color: Couleurs.texte2 }
                Outil { Layout.preferredWidth: 24; Layout.preferredHeight: 24; picto: "M6 12h12 M12 6v12"; aide: "Agrandir"; onClicked: fenetre.doc.zoom = fenetre.doc.zoom * 1.1 }
            }
        }
    }

    // ——— Accueil : un document vierge, un genre guidé, les récents ———
    AccueilDocs {
        anchors.fill: parent
        z: 50
        visible: fenetre.accueilOuvert
        preparation: fenetre.modeleEnCours
        onVisibleChanged: if (visible) forceActiveFocus()
        onVierge: fenetre.fermerAccueil()
        onModele: (genre, nom) => fenetre.appliquerModele(genre, nom)
        onOuvrir: c => fenetre.ouvrirFichier(c)
        onOuvrirFichier: dialogueOuvrir.open()
        onModifierInfos: mesInformations.ouvrir()
        aRecuperer: secours.orphelins
        onRecuperer: copie => fenetre.ouvrirFichier(copie.fichier)
        onOublier: copie => secours.oublier(copie)
    }
    MesInformations { id: mesInformations }

    // ——— Message bref ———
    Rectangle {
        id: message
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 60
        z: 60
        width: texteMessage.implicitWidth + 32
        height: 34
        radius: 17
        color: "#1E2740"
        opacity: 0
        visible: opacity > 0
        Behavior on opacity { NumberAnimation { duration: 200 } }
        function montrer(t) { texteMessage.text = t; opacity = 1; delaiMessage.interval = Math.min(6000, 1600 + t.length * 45); delaiMessage.restart() }
        Text { id: texteMessage; anchors.centerIn: parent; font.pixelSize: 13; color: "#F1EBE1" }
        Timer { id: delaiMessage; interval: 2400; onTriggered: message.opacity = 0 }
    }

    // ——— Fermer sans perdre son travail ———
    Confirmation {
        id: confirmationFermer
        titre: "Enregistrer « " + fenetre.nomFichier + " » ?"
        texte: fenetre.reprise ? "Ce document récupéré sera perdu si vous fermez sans l'enregistrer."
                               : "Les modifications seront perdues si vous fermez sans enregistrer."
        action: "Enregistrer"
        teinte: fenetre.accent
        picto: "M5 4h11l3 3v13H5z M8 4v5h7V4 M8 20v-6h8v6"
        onConfirme: { ouverte = false; fenetre.fermetureDemandee = true; fenetre.enregistrer() }
        Outil {
            text: "Fermer sans enregistrer"
            encre: "#A3322A"
            onClicked: { fenetre.fermetureDemandee = true; secours.quitter() }
        }
    }
}
