// Sama Sheet, le tableur de Sama : interface de Sama, moteur de LibreOffice (module Sama.Moteur).
// Menus et « Que voulez-vous faire ? » (Ctrl+K) ; barre d'outils sur une ligne ; barre de formule avec l'aide des
// fonctions ; grille ; feuilles ; barre d'état (somme, moyenne, nombre de la sélection).
// Lancement : sama-sheet [fichier]  (sans fichier : un classeur vide)

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtQuick.Dialogs
import QtCore
import Sama.Moteur
import "../reglages"

Window {
    id: fenetre
    width: 1180
    height: 760
    minimumWidth: 760
    minimumHeight: 480
    visible: false
    title: (doc.modifie ? "• " : "") + nomFichier + " — Sama Sheet"
    color: Couleurs.fond

    // Identité de Sama Sheet : le vert des tableaux
    readonly property color vert: "#2F6B57"
    readonly property color vertEncre: Couleurs.sombre ? "#A3D6C1" : "#1F5544"
    readonly property color vertFond: Qt.rgba(47 / 255, 107 / 255, 87 / 255, Couleurs.sombre ? 0.25 : 0.12)

    readonly property var doc: grille.doc
    readonly property string nomFichier: doc.chemin ? doc.chemin.split("/").pop() : "Nouveau classeur"
    readonly property string extension: doc.chemin ? doc.chemin.split(".").pop().toLowerCase() : ""
    property bool fermetureDemandee: false
    readonly property alias actions: lesActions
    Actions { id: lesActions; doc: fenetre.doc; fenetre: fenetre }

    function etat(c) { return doc.etats[c] }
    function actif(c) { return doc.etats[c] === "true" }
    function chaine(nom, valeur) { var a = {}; a[nom] = { type: "string", value: String(valeur) }; return a }
    function entier(nom, valeur) { var a = {}; a[nom] = { type: "long", value: valeur }; return a }
    function chemin(url) { return decodeURIComponent(String(url).replace(/^file:\/\//, "")) }

    // ——— Fichiers ———
    function enregistrer() {
        if (!doc.chemin || ["xlsx", "ods", "xls", "csv"].indexOf(extension) < 0) dialogueEnregistrer.open()
        else doc.enregistrer()
    }
    // Ouvrir dans cette fenêtre si elle est vide et intacte, sinon dans une nouvelle
    function ouvrirFichier(c) {
        if (!doc.chemin && !doc.modifie) doc.ouvrir(c)
        else commande.lancer("systemd-run --user --quiet --collect --slice=app.slice "
                             + commande.q("--unit=app-samaos\\x2dsheet@" + Date.now() + ".service") + " sama-sheet " + commande.q(c))
    }
    function nouvelleFenetre() {
        commande.lancer("systemd-run --user --quiet --collect --slice=app.slice "
                        + commande.q("--unit=app-samaos\\x2dsheet@" + Date.now() + ".service") + " sama-sheet")
    }
    Commande { id: commande }
    function ouvrirDialogue(genre) {
        if (genre === "ouvrir") dialogueOuvrir.open()
        else if (genre === "pdf") dialoguePdf.open()
        else dialogueEnregistrer.open()
    }
    function ouvrirVolet(nom) { barreOutils.ouvrirVolet(nom) }
    function ouvrirAide() {
        commande.lancer("systemd-run --user --quiet --collect --slice=app.slice "
                        + commande.q("--unit=app-samaos\\x2daide@" + Date.now() + ".service") + " sama-aide")
    }
    function ajouterFeuille() {
        var n = doc.nomsParties.length + 1
        while (doc.nomsParties.indexOf("Feuille " + n) >= 0) n++
        var a = chaine("Name", "Feuille " + n)
        a.Index = { type: "long", value: doc.nomsParties.length + 1 }
        doc.commande(".uno:Insert", a)
    }
    // Commencer une formule dans la barre de formule (« =MOYENNE() », le curseur entre les parenthèses)
    function commencerFormule(t) {
        champFormule.text = t
        champFormule.forceActiveFocus()
        champFormule.cursorPosition = t.indexOf("(") + 1
    }

    FileDialog {
        id: dialogueOuvrir
        title: "Ouvrir un classeur"
        fileMode: FileDialog.OpenFile
        nameFilters: ["Classeurs (*.xlsx *.xls *.ods *.csv)", "Tous les fichiers (*)"]
        onAccepted: fenetre.ouvrirFichier(fenetre.chemin(selectedFile))
    }
    FileDialog {
        id: dialogueEnregistrer
        title: "Enregistrer le classeur"
        fileMode: FileDialog.SaveFile
        nameFilters: ["Classeur Excel (*.xlsx)", "Classeur OpenDocument (*.ods)", "Texte CSV (*.csv)", "PDF (*.pdf)"]
        currentFile: doc.chemin ? "file://" + doc.chemin
                     : StandardPaths.writableLocation(StandardPaths.DocumentsLocation) + "/Nouveau classeur.xlsx"
        onAccepted: {
            var c = fenetre.chemin(selectedFile)
            var formats = ["xlsx", "ods", "csv", "pdf"]
            var f = c.indexOf(".") > 0 ? c.split(".").pop().toLowerCase() : formats[Math.max(0, selectedNameFilter.index)]
            if (formats.indexOf(f) < 0) f = "xlsx"
            if (!c.toLowerCase().endsWith("." + f)) c += "." + f
            doc.enregistrerSous(c, f)
        }
    }

    FileDialog {
        id: dialoguePdf
        title: "Exporter en PDF"
        fileMode: FileDialog.SaveFile
        nameFilters: ["PDF (*.pdf)"]
        currentFile: (doc.chemin ? "file://" + doc.chemin.replace(/\.[^.\/]*$/, "")
                      : StandardPaths.writableLocation(StandardPaths.DocumentsLocation) + "/Nouveau classeur") + ".pdf"
        onAccepted: {
            var c = fenetre.chemin(selectedFile)
            if (!c.toLowerCase().endsWith(".pdf")) c += ".pdf"
            doc.enregistrerSous(c, "pdf")
        }
    }

    Connections {
        target: fenetre.doc
        function onEnregistre(reussi, c) {
            if (!reussi && !c) { dialogueEnregistrer.open(); return }
            message.montrer(reussi ? (c.endsWith(".pdf") ? "Exporté en PDF : " + c.split("/").pop() : "Enregistré")
                                   : "L'enregistrement a échoué")
            if (reussi && fenetre.fermetureDemandee && !c.endsWith(".pdf")) Qt.quit()
        }
        function onEtatChanged() {
            if (fenetre.doc.etat === DocumentLO.Erreur) message.montrer(fenetre.doc.erreur || "Ce fichier n'a pas pu être ouvert")
            if (fenetre.doc.etat === DocumentLO.Pret) fenetre.doc.forceActiveFocus()
        }
    }

    Component.onCompleted: {
        var a = Qt.application.arguments
        var dernier = a.length > 0 ? String(a[a.length - 1]) : ""
        if (dernier.indexOf("file://") === 0) dernier = chemin(dernier)
        if (dernier && dernier.indexOf(".qml") < 0 && dernier.indexOf("/") === 0) doc.ouvrir(dernier)
        else doc.nouveau("calc")
        Qt.application.domain = ""
        Qt.application.name = "samaos-sheet"
        visible = true
    }

    // Fermer avec des modifications non enregistrées : on demande
    onClosing: close => {
        if (doc.modifie && !fermetureDemandee) {
            close.accepted = false
            confirmationFermer.ouverte = true
        }
    }

    // ——— Raccourcis (les autres, comme Ctrl+B ou Ctrl+Z, vont au moteur) ———
    Shortcut { sequence: StandardKey.Save; onActivated: fenetre.enregistrer() }
    Shortcut { sequence: StandardKey.SaveAs; onActivated: dialogueEnregistrer.open() }
    Shortcut { sequence: StandardKey.Open; onActivated: dialogueOuvrir.open() }
    Shortcut { sequence: StandardKey.New; onActivated: fenetre.nouvelleFenetre() }
    Shortcut { sequences: ["Ctrl+=", "Ctrl++"]; onActivated: fenetre.doc.zoom = fenetre.doc.zoom * 1.1 }
    Shortcut { sequence: "Ctrl+-"; onActivated: fenetre.doc.zoom = fenetre.doc.zoom / 1.1 }
    Shortcut { sequence: "Ctrl+0"; onActivated: fenetre.doc.zoom = 1 }
    // (Alt+/ comme Google Sheets ; sur un clavier AZERTY, « / » demande Maj : Ctrl+K est plus simple)
    Shortcut { sequences: ["Ctrl+K", "Alt+/", "Alt+Shift+/"]; onActivated: barreMenus.recherche.ouvrir() }
    Shortcut { sequence: "F1"; onActivated: fenetre.ouvrirAide() }
    Shortcut { sequence: "Ctrl+W"; onActivated: fenetre.close() }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        BarreMenus {
            id: barreMenus
            Layout.fillWidth: true
        }
        BarreOutils {
            id: barreOutils
            Layout.fillWidth: true
        }

        // ——— Barre de formule : adresse, fx, contenu de la cellule ———
        Rectangle {
            id: barreFormule
            Layout.fillWidth: true
            Layout.preferredHeight: 36
            color: Couleurs.sombre ? "#1B1F2E" : "#FFFFFF"
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 14
                anchors.rightMargin: 14
                spacing: 10
                Rectangle {
                    Layout.preferredWidth: 72
                    Layout.preferredHeight: 26
                    radius: 7
                    color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : 0.05)
                    border.width: champAdresse.activeFocus ? 1.5 : 0
                    border.color: fenetre.vert
                    TextInput {
                        id: champAdresse
                        anchors.fill: parent
                        anchors.margins: 4
                        horizontalAlignment: TextInput.AlignHCenter
                        verticalAlignment: TextInput.AlignVCenter
                        text: fenetre.doc.adresse
                        font.pixelSize: 12
                        font.weight: Font.DemiBold
                        color: Couleurs.texte
                        selectByMouse: true
                        // (on y entre pour taper une autre adresse : l'ancienne est choisie, pas complétée)
                        onActiveFocusChanged: if (activeFocus) Qt.callLater(selectAll)
                        onAccepted: { fenetre.doc.allerA(text.toUpperCase()); fenetre.doc.forceActiveFocus() }
                        Keys.onEscapePressed: { text = fenetre.doc.adresse; fenetre.doc.forceActiveFocus() }
                    }
                }
                Rectangle { width: 0.5; height: 18; color: Couleurs.bord }
                Text { text: "fx"; font.pixelSize: 13; font.italic: true; color: Couleurs.texte3 }
                TextInput {
                    id: champFormule
                    Layout.fillWidth: true
                    verticalAlignment: TextInput.AlignVCenter
                    // (suit la case courante, sauf pendant qu'on y écrit ; pas de liaison, que la saisie romprait)
                    Connections {
                        target: fenetre.doc
                        function onFormuleChanged() { if (!champFormule.activeFocus) champFormule.text = fenetre.doc.formule }
                    }
                    onActiveFocusChanged: if (!activeFocus) text = fenetre.doc.formule
                    font.pixelSize: 13
                    font.features: { "tnum": 1 }
                    color: Couleurs.texte
                    clip: true
                    selectByMouse: true
                    // (propositions de fonctions : flèches pour choisir, Tab ou Entrée pour prendre)
                    Keys.onPressed: e => {
                        if (!aideFonction.proposer) return
                        if (e.key === Qt.Key_Down) { aideFonction.suivante(); e.accepted = true }
                        else if (e.key === Qt.Key_Up) { aideFonction.precedente(); e.accepted = true }
                        else if (e.key === Qt.Key_Tab || e.key === Qt.Key_Return || e.key === Qt.Key_Enter) { aideFonction.accepter(); e.accepted = true }
                    }
                    onAccepted: { fenetre.doc.saisir(text); fenetre.doc.forceActiveFocus() }
                    Keys.onEscapePressed: { text = fenetre.doc.formule; fenetre.doc.forceActiveFocus() }
                }
            }
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: Couleurs.bord }
        }

        Grille {
            id: grille
            Layout.fillWidth: true
            Layout.fillHeight: true
        }

        // ——— Feuilles ———
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 34
            color: Couleurs.fond
            Rectangle { width: parent.width; height: 0.5; color: Couleurs.bord }
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 10
                anchors.rightMargin: 10
                spacing: 4
                Outil {
                    Layout.preferredWidth: 26
                    Layout.preferredHeight: 24
                    picto: "M12 5v14 M5 12h14"
                    aide: "Ajouter une feuille"
                    onClicked: fenetre.ajouterFeuille()
                }
                Repeater {
                    model: fenetre.doc.nomsParties
                    delegate: MouseArea {
                        id: ongletFeuille
                        readonly property bool courante: index === fenetre.doc.partie
                        property bool edition: false
                        Layout.preferredHeight: 24
                        Layout.preferredWidth: (edition ? champNom.implicitWidth + 30 : nom.implicitWidth + 24)
                        acceptedButtons: Qt.LeftButton
                        onClicked: fenetre.doc.allerPartie(index)
                        onDoubleClicked: { fenetre.doc.allerPartie(index); edition = true; champNom.text = modelData; champNom.forceActiveFocus(); champNom.selectAll() }
                        Rectangle {
                            anchors.fill: parent
                            radius: 7
                            color: ongletFeuille.courante ? Couleurs.champ : ongletFeuille.containsMouse ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                            border.width: ongletFeuille.courante ? 0.5 : 0
                            border.color: Couleurs.bord
                        }
                        hoverEnabled: true
                        Text {
                            id: nom
                            visible: !ongletFeuille.edition
                            anchors.centerIn: parent
                            text: modelData
                            font.pixelSize: 12
                            font.weight: ongletFeuille.courante ? Font.DemiBold : Font.Normal
                            color: ongletFeuille.courante ? fenetre.vertEncre : Couleurs.texte2
                        }
                        TextInput {
                            id: champNom
                            visible: ongletFeuille.edition
                            anchors.centerIn: parent
                            font.pixelSize: 12
                            color: Couleurs.texte
                            onAccepted: {
                                if (text.trim()) fenetre.doc.commande(".uno:RenameTable", fenetre.chaine("Name", text.trim()))
                                ongletFeuille.edition = false
                                fenetre.doc.forceActiveFocus()
                            }
                            onActiveFocusChanged: if (!activeFocus) ongletFeuille.edition = false
                            Keys.onEscapePressed: { ongletFeuille.edition = false; fenetre.doc.forceActiveFocus() }
                        }
                    }
                }
                Item { Layout.fillWidth: true }
            }
        }

        // ——— Barre d'état ———
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 28
            color: Couleurs.fond
            Rectangle { width: parent.width; height: 0.5; color: Couleurs.bord }
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 16
                anchors.rightMargin: 12
                spacing: 8
                Picto {
                    width: 14; height: 14
                    trace: fenetre.doc.modifie || !fenetre.doc.chemin ? "M12 7v5l3 2 M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z" : "M5 12.5l4.5 4.5L19 7.5"
                    encre: fenetre.doc.modifie || !fenetre.doc.chemin ? Couleurs.texte3 : fenetre.vertEncre
                }
                Text {
                    Layout.fillWidth: true
                    elide: Text.ElideRight
                    text: fenetre.doc.etat === DocumentLO.Chargement ? "Ouverture…"
                          : !fenetre.doc.chemin ? "Nouveau classeur, pas encore enregistré"
                          : fenetre.doc.modifie ? "Modifications non enregistrées"
                          : "Enregistré" + (fenetre.extension === "xlsx" ? " · compatible Excel (.xlsx)" : fenetre.extension === "ods" ? " · format ouvert (.ods)" : "")
                    font.pixelSize: 11
                    color: Couleurs.texte2
                }
                Text {
                    id: statistiques
                    // (somme et moyenne d'une plage, données par le moteur : « Moyenne: 1 200 F; Somme: 3 600 F »)
                    visible: fenetre.doc.adresse.indexOf(":") > 0 && text !== ""
                    text: String(fenetre.etat(".uno:StateTableCell") || "").split(";").map(function (s) {
                              return s.trim().replace(/\s*:\s*/, " : ").replace(/^NbVal/i, "Nombre").replace(/^Nb\b/, "Nombre")
                          }).filter(function (s) { return /\d/.test(s) }).join("   ·   ")
                    font.pixelSize: 11
                    color: Couleurs.texte2
                }
                Text { visible: statistiques.visible; text: "·"; font.pixelSize: 11; color: Couleurs.texte3 }
                Outil { Layout.preferredHeight: 22; Layout.preferredWidth: 22; picto: "M5 12h14"; aide: "Réduire"; onClicked: fenetre.doc.zoom = fenetre.doc.zoom / 1.1 }
                Text { text: Math.round(fenetre.doc.zoom * 100) + " %"; font.pixelSize: 11; color: Couleurs.texte2 }
                Outil { Layout.preferredHeight: 22; Layout.preferredWidth: 22; picto: "M12 5v14 M5 12h14"; aide: "Agrandir"; onClicked: fenetre.doc.zoom = fenetre.doc.zoom * 1.1 }
            }
        }
    }

    // ——— Aide des fonctions pendant la saisie d'une formule (barre de formule ou case) ———
    AideFonction {
        id: aideFonction
        x: 104
        y: barreFormule.y + barreFormule.height + 6
        z: 10
        texte: champFormule.activeFocus ? champFormule.text.slice(0, champFormule.cursorPosition)
               : fenetre.doc.curseurTexteVisible ? fenetre.doc.formule : ""
        completer: champFormule.activeFocus
        onProposition: (nom, prefixe) => {
            var avant = champFormule.text.slice(0, champFormule.cursorPosition - prefixe.length)
            var apres = champFormule.text.slice(champFormule.cursorPosition)
            champFormule.text = avant + nom + "(" + apres
            champFormule.cursorPosition = avant.length + nom.length + 1
        }
    }

    // ——— Message bref (enregistré, erreur) ———
    Rectangle {
        id: message
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 76
        width: texteMessage.implicitWidth + 32
        height: 34
        radius: 17
        color: "#1E2740"
        opacity: 0
        visible: opacity > 0
        Behavior on opacity { NumberAnimation { duration: 200 } }
        function montrer(t) { texteMessage.text = t; opacity = 1; delaiMessage.restart() }
        Text { id: texteMessage; anchors.centerIn: parent; font.pixelSize: 13; color: "#F1EBE1" }
        Timer { id: delaiMessage; interval: 2400; onTriggered: message.opacity = 0 }
    }

    // ——— Fermer sans perdre son travail ———
    Confirmation {
        id: confirmationFermer
        titre: "Enregistrer « " + fenetre.nomFichier + " » ?"
        texte: "Les modifications seront perdues si vous fermez sans enregistrer."
        action: "Enregistrer"
        teinte: fenetre.vert
        picto: "M5 4h11l3 3v13H5z M8 4v5h7V4 M8 20v-6h8v6"
        onConfirme: { ouverte = false; fenetre.fermetureDemandee = true; fenetre.enregistrer() }
        Outil {
            text: "Fermer sans enregistrer"
            encre: "#A3322A"
            onClicked: { fenetre.fermetureDemandee = true; Qt.quit() }
        }
    }
}
