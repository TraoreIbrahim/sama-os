// Sélecteur de fichiers de Sama (maquette fic-03) : « Enregistrer sous », « Ouvrir », « Choisir un dossier ».
// Ouvert par /usr/libexec/samaos/portail-fichiers (portail de bureau), qui lui passe en JSON { mode, titre, app, nom,
// dossier, filtres: [{ nom, extensions }], filtre, multiple, repertoire, bouton, choix, fichiers } et lit sa réponse
// (« SAMA_SELECTEUR={ chemins, filtre, choix } », vide si l'on annule).

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtCore
import "../reglages"

Window {
    id: fenetre
    width: 720
    height: 620
    minimumWidth: 600
    minimumHeight: 480
    visible: false
    flags: Qt.Dialog
    color: Couleurs.fond
    title: (mode === "ouvrir" ? (repertoire ? "Choisir un dossier" : "Ouvrir") : "Enregistrer") + (d.app ? " — " + d.app : "")

    readonly property var d: {
        var a = Qt.application.arguments
        try { return JSON.parse(String(a[a.length - 1])) } catch (e) { return { mode: "ouvrir" } }
    }
    readonly property string mode: d.mode || "ouvrir"            // enregistrer | ouvrir | dossier-enregistrer
    readonly property bool enregistrer: mode !== "ouvrir"
    readonly property bool repertoire: !!d.repertoire || mode === "dossier-enregistrer"
    // (un seul filtre « Tous les fichiers » ne vaut pas une ligne Format)
    readonly property var filtres: (d.filtres || []).length === 1 && !(d.filtres[0].extensions || []).length ? [] : (d.filtres || [])
    property int filtre: 0
    property string dossier: d.dossier || ""
    property var entrees: []
    property var emplacements: []
    property var selection: []                                   // chemins choisis (Ouvrir)
    property var historique: []
    property string remplacer: ""                                // fichier existant à confirmer
    readonly property string moteur: "python3 /usr/libexec/samaos/selecteur.py "

    // ——— Formats ———
    readonly property var connus: ({
        "docx": ["Word", "S'ouvre dans Microsoft Word et sur les postes Windows"],
        "odt": ["OpenDocument", "Le format ouvert de Sama Docs, lisible partout"],
        "xlsx": ["Excel", "S'ouvre dans Microsoft Excel et sur les postes Windows"],
        "ods": ["OpenDocument", "Le format ouvert de Sama Sheet, lisible partout"],
        "pptx": ["PowerPoint", "S'ouvre dans Microsoft PowerPoint et sur les postes Windows"],
        "odp": ["OpenDocument", "Le format ouvert des Présentations Sama, lisible partout"],
        "pdf": ["PDF", "Se lit partout, mais ne se modifie plus"],
        "png": ["PNG", "Image nette, sans perte"], "jpg": ["JPEG", "Image légère, idéale pour les photos"],
        "txt": ["Texte", "Texte seul, sans mise en forme"], "csv": ["CSV", "Tableau en texte, lisible par tous les tableurs"]
    })
    function extension(i) { return i >= 0 && i < filtres.length && filtres[i].extensions.length ? filtres[i].extensions[0] : "" }
    // Les formats les plus courants en boutons (trois au plus), les autres dans une liste
    readonly property var principaux: {
        var res = [], vues = {}
        var ordre = ["docx", "odt", "xlsx", "ods", "pptx", "odp", "pdf", "png", "jpg", "txt", "csv"]
        ordre.forEach(function (e) {
            for (var i = 0; i < filtres.length && res.length < 3; i++)
                if (filtres[i].extensions[0] === e && !vues[e]) { res.push(i); vues[e] = true; break }
        })
        if (!res.length) for (var j = 0; j < Math.min(3, filtres.length); j++) res.push(j)
        return res
    }
    readonly property var autres: filtres.map(function (f, i) { return i }).filter(function (i) { return principaux.indexOf(i) < 0 })
    function nomFormat(i) {
        var e = extension(i)
        if (!e) return "Tous les fichiers"
        return connus[e] ? connus[e][0] : filtres[i].nom.replace(/\s*\(.*\)\s*$/, "")
    }
    readonly property string noteFormat: {
        var e = extension(filtre)
        return !e ? "" : connus[e] ? connus[e][1] : (filtres.length ? filtres[filtre].nom : "")
    }
    function correspond(nom) {
        if (!filtres.length || !filtres[filtre] || !filtres[filtre].extensions.length) return true
        var e = nom.split(".").pop().toLowerCase()
        return filtres[filtre].extensions.indexOf(e) >= 0
    }

    // ——— Navigation ———
    Commande { id: commande }
    function aller(chemin, sansHistorique) {
        if (!sansHistorique && dossier && chemin !== dossier) historique = historique.concat([dossier])
        dossier = chemin
        selection = []
        if (chemin === "recents") {
            commande.lancer("python3 /usr/libexec/samaos/fichiers.py recents", function (s) {
                try { fenetre.entrees = JSON.parse(s).map(function (r) { return { nom: r.nom, chemin: r.chemin, dossier: false, detail: r.chemin.substring(0, r.chemin.lastIndexOf("/")) } }) }
                catch (e) { fenetre.entrees = [] }
            })
        } else {
            commande.lancer(moteur + "lister " + commande.q(chemin), function (s) { try { fenetre.entrees = JSON.parse(s) } catch (e) { fenetre.entrees = [] } })
        }
    }
    function ouvrirEmplacement(e) {
        if (e.chemin) { aller(e.chemin); return }
        commande.lancer("python3 /usr/libexec/samaos/fichiers.py monter " + commande.q(e.peripherique), function (s) { if (s.trim()) fenetre.aller(s.trim()) })
    }
    readonly property var ariane: {
        if (dossier === "recents") return [{ nom: "Récents", chemin: "recents" }]
        var maison = decodeURIComponent(String(StandardPaths.writableLocation(StandardPaths.HomeLocation)).replace(/^file:\/\//, ""))
        var morceaux = [], racine = "/"
        if (dossier.indexOf(maison) === 0) { racine = maison; morceaux.push({ nom: "Accueil", chemin: maison }) }
        else morceaux.push({ nom: "Ordinateur", chemin: "/" })
        var p = racine === "/" ? "" : racine
        dossier.substring(racine.length).split("/").filter(function (x) { return x }).forEach(function (x) { p += "/" + x; morceaux.push({ nom: x, chemin: p }) })
        return morceaux
    }

    // ——— Réponse ———
    property bool repondu: false
    function repondre(chemins) {
        if (repondu) return
        repondu = true
        var choix = {}
        console.log("SAMA_SELECTEUR=" + JSON.stringify(chemins ? { chemins: chemins, filtre: filtre, choix: choix } : {}))
        Qt.quit()
    }
    function nomFinal() {
        var n = champNom.text.trim()
        if (!n) return ""
        var e = n.split(".").pop().toLowerCase()
        var connue = n.indexOf(".") > 0 && filtres.some(function (f) { return f.extensions.indexOf(e) >= 0 })
        return connue || !extension(filtre) ? n : n + "." + extension(filtre)
    }
    function valider(force) {
        if (mode === "enregistrer") {
            var n = nomFinal()
            if (!n || n.indexOf("/") >= 0 || dossier === "recents") return
            var chemin = dossier.replace(/\/$/, "") + "/" + n
            var existe = entrees.some(function (x) { return x.chemin === chemin })
            if (existe && !force) { remplacer = n; return }
            repondre([chemin])
        } else if (repertoire) {
            if (dossier === "recents") return
            var cible = selection.length === 1 && entrees.some(function (x) { return x.chemin === selection[0] && x.dossier }) ? selection[0] : dossier
            if (mode === "dossier-enregistrer") repondre((d.fichiers || []).map(function (f) { return cible.replace(/\/$/, "") + "/" + f }))
            else repondre([cible])
        } else if (selection.length) {
            repondre(selection)
        }
    }

    Component.onCompleted: {
        if (d.filtre >= 0) filtre = d.filtre
        // Nom proposé : « Rapport.docx » → « Rapport », et le format .docx choisi s'il est dans la liste
        var n = d.nom || ""
        var p = n.lastIndexOf(".")
        if (p > 0) {
            var e = n.substring(p + 1).toLowerCase()
            for (var i = 0; i < filtres.length; i++) if (filtres[i].extensions.indexOf(e) >= 0) {
                if (!(d.filtre >= 0)) filtre = i
                n = n.substring(0, p)
                break
            }
        }
        champNom.text = n
        champNom.selectAll()
        commande.lancer(moteur + "emplacements", function (s) { try { fenetre.emplacements = JSON.parse(s) } catch (e) {} })
        aller(dossier, true)
        Qt.application.domain = ""
        Qt.application.name = "samaos-selecteur"
        visible = true
        if (enregistrer && mode === "enregistrer") champNom.forceActiveFocus()
    }
    onClosing: repondre(null)
    Shortcut { sequence: "Escape"; onActivated: if (fenetre.remplacer) fenetre.remplacer = ""; else fenetre.repondre(null) }
    Shortcut { sequences: ["Return", "Enter"]; enabled: !fenetre.remplacer; onActivated: fenetre.valider(false) }

    component Picto: Canvas {
        property string trace
        property color encre: Couleurs.texte2
        width: 16; height: 16
        onTraceChanged: requestPaint()
        onEncreChanged: requestPaint()
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
            c.strokeStyle = encre; c.lineWidth = 1.7; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }
    component Bouton: QQC2.AbstractButton {
        id: bouton
        property bool principal: false
        implicitHeight: 36
        implicitWidth: libelle.implicitWidth + 32
        hoverEnabled: true
        opacity: enabled ? 1 : 0.45
        background: Rectangle {
            radius: 18
            color: bouton.principal ? Couleurs.laterite : Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : (bouton.hovered ? 0.09 : 0.05))
            opacity: bouton.principal && bouton.down ? 0.85 : 1
        }
        contentItem: Text {
            id: libelle
            text: bouton.text
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            font.pixelSize: 13
            font.weight: bouton.principal ? Font.DemiBold : Font.Medium
            color: bouton.principal ? "#FFFFFF" : Couleurs.texte
        }
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // ——— En-tête : titre, nom, format ———
        ColumnLayout {
            Layout.fillWidth: true
            Layout.margins: 24
            Layout.topMargin: 22
            Layout.bottomMargin: 18
            spacing: 16
            Text {
                text: fenetre.mode === "enregistrer" ? "Enregistrer sous" : fenetre.mode === "dossier-enregistrer" ? "Enregistrer dans un dossier"
                      : fenetre.repertoire ? "Choisir un dossier" : "Ouvrir"   // (le titre des applications peut être en anglais)
                font.pixelSize: 20
                font.weight: Font.Medium
                color: Couleurs.texte
            }
            GridLayout {
                Layout.fillWidth: true
                visible: fenetre.mode === "enregistrer" || (fenetre.filtres.length > 1 && !fenetre.repertoire)
                columns: 2
                columnSpacing: 14
                rowSpacing: 12
                Text { visible: fenetre.mode === "enregistrer"; Layout.preferredWidth: 70; text: "Nom"; font.pixelSize: 13; color: Couleurs.texte2 }
                Rectangle {
                    visible: fenetre.mode === "enregistrer"
                    Layout.fillWidth: true
                    Layout.preferredHeight: 38
                    radius: 10
                    color: Couleurs.champ
                    border.width: champNom.activeFocus ? 2 : 1
                    border.color: champNom.activeFocus ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.45) : Couleurs.bord
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 12
                        anchors.rightMargin: 12
                        spacing: 0
                        QQC2.TextField {
                            id: champNom
                            Layout.fillWidth: true
                            font.pixelSize: 14
                            color: Couleurs.texte
                            selectionColor: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.3)
                            selectedTextColor: Couleurs.texte
                            background: null
                            leftPadding: 0
                            rightPadding: 2
                        }
                        Text {
                            visible: fenetre.extension(fenetre.filtre) !== "" && champNom.text.toLowerCase().indexOf("." + fenetre.extension(fenetre.filtre)) < 0
                            text: "." + fenetre.extension(fenetre.filtre)
                            font.pixelSize: 14
                            color: Couleurs.texte3
                        }
                    }
                }
                Text {
                    visible: fenetre.filtres.length > 0
                    Layout.preferredWidth: 70
                    text: fenetre.enregistrer ? "Format" : "Type"
                    font.pixelSize: 13
                    color: Couleurs.texte2
                }
                RowLayout {
                    visible: fenetre.filtres.length > 0
                    Layout.fillWidth: true
                    spacing: 12
                    // Formats courants
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 36
                        radius: 10
                        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : 0.05)
                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 3
                            spacing: 2
                            Repeater {
                                model: fenetre.principaux
                                delegate: MouseArea {
                                    readonly property bool choisi: modelData === fenetre.filtre
                                    Layout.fillWidth: true
                                    Layout.fillHeight: true
                                    cursorShape: Qt.PointingHandCursor
                                    onClicked: fenetre.filtre = modelData
                                    Rectangle {
                                        anchors.fill: parent
                                        radius: 8
                                        visible: parent.choisi
                                        color: Couleurs.champ
                                        border.width: 0.5
                                        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                                    }
                                    Row {
                                        anchors.centerIn: parent
                                        spacing: 5
                                        Text { text: fenetre.nomFormat(modelData); font.pixelSize: 12; font.weight: parent.parent.choisi ? Font.DemiBold : Font.Medium; color: Couleurs.texte }
                                        Text { visible: fenetre.extension(modelData) !== ""; text: "." + fenetre.extension(modelData); font.pixelSize: 12; color: parent.parent.choisi ? Couleurs.texte2 : Couleurs.texte3 }
                                    }
                                }
                            }
                        }
                    }
                    // Autres formats
                    QQC2.ComboBox {
                        visible: fenetre.autres.length > 0
                        Layout.preferredWidth: 190
                        model: ["Autres formats…"].concat(fenetre.autres.map(function (i) { return fenetre.filtres[i].nom }))
                        currentIndex: Math.max(0, fenetre.autres.indexOf(fenetre.filtre) + 1)
                        onActivated: index => { if (index > 0) fenetre.filtre = fenetre.autres[index - 1] }
                    }
                }
                Item { visible: fenetre.filtres.length > 0 && fenetre.noteFormat !== ""; Layout.preferredWidth: 70; Layout.preferredHeight: 1 }
                RowLayout {
                    visible: fenetre.filtres.length > 0 && fenetre.noteFormat !== ""
                    Layout.topMargin: -4
                    spacing: 6
                    Picto { width: 14; height: 14; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: Couleurs.sombre ? "#A3D6C1" : Couleurs.foret }
                    Text { text: fenetre.noteFormat; font.pixelSize: 12; color: Couleurs.texte2 }
                }
            }
        }
        Rectangle { Layout.fillWidth: true; height: 0.5; color: Couleurs.ligne }

        // ——— Emplacements et contenu du dossier ———
        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0
            Rectangle {
                Layout.preferredWidth: 180
                Layout.fillHeight: true
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.25 : 0.05)
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 8
                    anchors.topMargin: 10
                    spacing: 2
                    Text { Layout.leftMargin: 12; Layout.topMargin: 6; Layout.bottomMargin: 4; text: "EMPLACEMENTS"; font.pixelSize: 11; font.weight: Font.DemiBold; font.letterSpacing: 0.3; color: Couleurs.texte3 }
                    Repeater {
                        model: fenetre.emplacements
                        delegate: MouseArea {
                            readonly property bool actif: fenetre.dossier === modelData.chemin && modelData.chemin !== ""
                            Layout.fillWidth: true
                            Layout.preferredHeight: 32
                            hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: fenetre.ouvrirEmplacement(modelData)
                            Rectangle {
                                anchors.fill: parent
                                radius: 8
                                color: parent.actif ? Couleurs.selection : parent.containsMouse ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                            }
                            RowLayout {
                                anchors.fill: parent
                                anchors.leftMargin: 12
                                anchors.rightMargin: 8
                                spacing: 10
                                Picto { trace: modelData.picto; encre: parent.parent.actif ? Couleurs.lateriteEncre : Couleurs.texte2 }
                                Text { Layout.fillWidth: true; text: modelData.nom; elide: Text.ElideRight; font.pixelSize: 13; font.weight: parent.parent.actif ? Font.DemiBold : Font.Normal; color: Couleurs.texte }
                            }
                        }
                    }
                    Item { Layout.fillHeight: true }
                }
            }
            Rectangle { Layout.fillHeight: true; width: 0.5; color: Couleurs.ligne }
            ColumnLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 0
                // Fil d'Ariane et « Nouveau dossier »
                RowLayout {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 40
                    Layout.leftMargin: 10
                    Layout.rightMargin: 12
                    spacing: 4
                    MouseArea {
                        Layout.preferredWidth: 26
                        Layout.preferredHeight: 26
                        enabled: fenetre.historique.length > 0
                        opacity: enabled ? 1 : 0.35
                        cursorShape: Qt.PointingHandCursor
                        onClicked: { var h = fenetre.historique.slice(); var p = h.pop(); fenetre.historique = h; fenetre.aller(p, true) }
                        Picto { anchors.centerIn: parent; trace: "M15 6l-6 6l6 6" }
                    }
                    Repeater {
                        model: fenetre.ariane
                        delegate: Row {
                            spacing: 4
                            Text { visible: index > 0; text: "›"; font.pixelSize: 13; color: Couleurs.texte3 }
                            Text {
                                text: modelData.nom
                                font.pixelSize: 13
                                font.weight: index === fenetre.ariane.length - 1 ? Font.DemiBold : Font.Normal
                                color: index === fenetre.ariane.length - 1 ? Couleurs.texte : Couleurs.texte2
                                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: fenetre.aller(modelData.chemin) }
                            }
                        }
                    }
                    Item { Layout.fillWidth: true }
                    QQC2.AbstractButton {
                        id: nouveau
                        visible: fenetre.enregistrer && fenetre.dossier !== "recents"
                        implicitHeight: 28
                        implicitWidth: rangeeNouveau.implicitWidth + 28
                        hoverEnabled: true
                        background: Rectangle { radius: 14; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, nouveau.hovered ? 0.09 : 0.05) }
                        contentItem: Item {
                            Row {
                                id: rangeeNouveau
                                anchors.centerIn: parent
                                spacing: 6
                                Picto { width: 14; height: 14; trace: "M4 6.5A1.5 1.5 0 0 1 5.5 5H10l2 2h6.5A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z M12 10v6 M9 13h6"; encre: Couleurs.texte }
                                Text { text: "Nouveau dossier"; font.pixelSize: 12; font.weight: Font.Medium; color: Couleurs.texte }
                            }
                        }
                        onClicked: commande.lancer("python3 /usr/libexec/samaos/fichiers.py nouveau-dossier " + commande.q(fenetre.dossier), function (s) {
                            fenetre.aller(fenetre.dossier, true)
                        })
                    }
                }
                Rectangle { Layout.fillWidth: true; height: 0.5; color: Couleurs.ligne }
                ListView {
                    id: liste
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.margins: 6
                    clip: true
                    spacing: 1
                    model: fenetre.entrees.filter(function (x) { return !fenetre.repertoire || x.dossier })
                    QQC2.ScrollBar.vertical: QQC2.ScrollBar {}
                    delegate: MouseArea {
                        id: ligne
                        // (en enregistrant, les fichiers sont là pour repère : un clic reprend leur nom)
                        readonly property bool estompe: !modelData.dossier && (fenetre.enregistrer || !fenetre.correspond(modelData.nom))
                        readonly property bool choisie: fenetre.selection.indexOf(modelData.chemin) >= 0
                        width: ListView.view.width
                        height: 38
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: {
                            if (modelData.dossier && !fenetre.repertoire) { fenetre.aller(modelData.chemin); return }
                            if (fenetre.mode === "enregistrer") { var n = modelData.nom; var p = n.lastIndexOf("."); champNom.text = p > 0 ? n.substring(0, p) : n; return }
                            if (fenetre.d.multiple && !modelData.dossier) {
                                var s = fenetre.selection.slice(), i = s.indexOf(modelData.chemin)
                                if (i >= 0) s.splice(i, 1); else s.push(modelData.chemin)
                                fenetre.selection = s
                            } else fenetre.selection = [modelData.chemin]
                        }
                        onDoubleClicked: {
                            if (modelData.dossier) fenetre.aller(modelData.chemin)
                            else if (fenetre.mode === "ouvrir" && !fenetre.repertoire) fenetre.repondre([modelData.chemin])
                        }
                        Rectangle {
                            anchors.fill: parent
                            radius: 8
                            color: ligne.choisie ? Couleurs.selection : ligne.containsMouse && !ligne.estompe ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.04) : "transparent"
                        }
                        RowLayout {
                            anchors.fill: parent
                            anchors.leftMargin: 10
                            anchors.rightMargin: 10
                            spacing: 10
                            opacity: ligne.estompe ? 0.55 : 1
                            Rectangle {
                                Layout.preferredWidth: 20
                                Layout.preferredHeight: 20
                                radius: 5
                                color: modelData.dossier ? "transparent" : "#3D5A99"
                                Picto {
                                    anchors.centerIn: parent
                                    width: modelData.dossier ? 20 : 13
                                    height: width
                                    encre: modelData.dossier ? "#C98F62" : "#FFFFFF"
                                    trace: modelData.dossier ? "M4 6.5A1.5 1.5 0 0 1 5.5 5H10l2 2h6.5A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z"
                                           : "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M14 3v5h5 M9 13h6 M9 17h4"
                                }
                            }
                            Text { Layout.fillWidth: true; text: modelData.nom; elide: Text.ElideMiddle; font.pixelSize: 13; color: Couleurs.texte }
                            Text {
                                visible: text !== ""
                                text: modelData.dossier ? (modelData.nombre === 0 ? "vide" : modelData.nombre > 0 ? modelData.nombre + (modelData.nombre > 1 ? " éléments" : " élément") : "")
                                      : (modelData.detail || "")
                                elide: Text.ElideLeft
                                Layout.maximumWidth: 200
                                font.pixelSize: 11
                                color: Couleurs.texte3
                            }
                            Picto { visible: modelData.dossier; width: 14; height: 14; trace: "M9 6l6 6l-6 6"; encre: Couleurs.texte3 }
                        }
                    }
                    Text {
                        anchors.centerIn: parent
                        visible: liste.count === 0
                        text: fenetre.dossier === "recents" ? "Aucun fichier ouvert récemment" : "Ce dossier est vide"
                        font.pixelSize: 13
                        color: Couleurs.texte3
                    }
                }
            }
        }
        Rectangle { Layout.fillWidth: true; height: 0.5; color: Couleurs.ligne }

        // ——— Boutons ———
        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 68
            Layout.leftMargin: 24
            Layout.rightMargin: 24
            spacing: 12
            Text {
                Layout.fillWidth: true
                elide: Text.ElideMiddle
                text: fenetre.mode === "enregistrer" && fenetre.nomFinal() && fenetre.dossier !== "recents" ? "Sera enregistré : " + fenetre.ariane[fenetre.ariane.length - 1].nom + " › " + fenetre.nomFinal()
                      : fenetre.mode === "ouvrir" && !fenetre.repertoire && fenetre.selection.length > 1 ? fenetre.selection.length + " fichiers choisis" : ""
                font.pixelSize: 12
                color: Couleurs.texte3
            }
            Bouton { text: "Annuler"; onClicked: fenetre.repondre(null) }
            Bouton {
                principal: true
                text: fenetre.d.bouton ? fenetre.d.bouton : fenetre.mode === "enregistrer" ? "Enregistrer" : fenetre.repertoire ? "Choisir ce dossier" : "Ouvrir"
                enabled: fenetre.dossier !== "recents" || (fenetre.mode === "ouvrir" && fenetre.selection.length > 0)
                onClicked: fenetre.valider(false)
            }
        }
    }

    // ——— « Le fichier existe déjà » ———
    Item {
        anchors.fill: parent
        visible: fenetre.remplacer !== ""
        Rectangle { anchors.fill: parent; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.28); MouseArea { anchors.fill: parent } }
        Rectangle {
            anchors.centerIn: parent
            width: 420
            height: confirmation.implicitHeight + 48
            radius: 18
            color: Couleurs.fond
            border.width: 0.5
            border.color: Couleurs.bord
            ColumnLayout {
                id: confirmation
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.margins: 24
                spacing: 12
                Text { Layout.fillWidth: true; wrapMode: Text.WordWrap; text: "« " + fenetre.remplacer + " » existe déjà"; font.pixelSize: 16; font.weight: Font.Medium; color: Couleurs.texte }
                Text { Layout.fillWidth: true; wrapMode: Text.WordWrap; text: "Le remplacer effacera le fichier qui s'y trouve. Vous pouvez aussi choisir un autre nom."; font.pixelSize: 13; color: Couleurs.texte2 }
                RowLayout {
                    Layout.alignment: Qt.AlignRight
                    Layout.topMargin: 6
                    spacing: 10
                    Bouton { text: "Changer le nom"; onClicked: { fenetre.remplacer = ""; champNom.forceActiveFocus(); champNom.selectAll() } }
                    Bouton { principal: true; text: "Remplacer"; onClicked: fenetre.valider(true) }
                }
            }
        }
    }
}
