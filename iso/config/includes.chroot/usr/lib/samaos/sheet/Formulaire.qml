// Le formulaire d'un tableau : une page claire pour saisir une ligne de plus, question après question (Remplir), et ce
// qu'on y règle (Composer) : l'ordre des questions, leur intitulé, une aide, le genre de réponse (texte, nombre,
// montant, date, téléphone, choix, oui ou non), les choix, obligatoire ou non, la valeur par défaut ; une question de
// plus ajoute une colonne au tableau. La définition est gardée dans le classeur (propriétés du document) ; les
// réponses vont dans le tableau. Le formulaire reste sur l'appareil : pas de lien à faire remplir par d'autres.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: formulaire
    color: Couleurs.fond
    property var tableau: null
    property string mode: "remplir"
    readonly property var tableaux: fenetre.tableaux

    // ——— Lire : les colonnes, les lignes (pour les choix), la définition ———
    property var colonnes: []
    property var lignes: []
    property var definition: ({ titre: "", intro: "", champs: [] })
    property var champs: []
    property int aLire: 0
    function ouvrir(t) {
        tableau = t
        mode = "remplir"
        saisies = 0
        choisi = -1
        charger()
    }
    function charger() {
        if (!tableau) return
        var nom = tableau.nom
        aLire = 3
        tableaux.appeler("Colonnes", [nom], function (ok, v) {
            if (ok && v && v.charAt(0) === "{") { try { formulaire.colonnes = JSON.parse(v).colonnes } catch (e) { } }
            formulaire.lu()
        })
        tableaux.appeler("Lignes", [nom], function (ok, v) {
            if (ok && v && v.charAt(0) === "{") { try { formulaire.lignes = JSON.parse(v).lignes } catch (e) { } }
            formulaire.lu()
        })
        tableaux.appeler("Reglage", ["formulaire", nom], function (ok, v) {
            var d = { titre: "", intro: "", champs: [] }
            if (ok && v && v.charAt(0) === "{") { try { d = JSON.parse(v) } catch (e) { } }
            formulaire.definition = d
            formulaire.lu()
        })
    }
    function lu() {
        if (--aLire > 0) return
        champs = fusion()
        vider()
        Qt.callLater(premierChamp)
    }

    // ——— Les questions : celles de la définition, dans son ordre, puis les colonnes nouvelles (sans les calculées) ———
    function colonne(nom) {
        for (var i = 0; i < colonnes.length; i++) if (colonnes[i].nom === nom) return colonnes[i]
        return null
    }
    function rangColonne(nom) {
        for (var i = 0; i < colonnes.length; i++) if (colonnes[i].nom === nom) return i
        return -1
    }
    function valeursDe(nom) {
        var i = rangColonne(nom), vus = {}, res = []
        if (i < 0) return res
        for (var k = 0; k < lignes.length; k++) {
            var v = lignes[k].v[i]
            if (v && !vus[v]) { vus[v] = true; res.push(v) }
        }
        return res
    }
    function deduire(c, i) {
        if (/t[ée]l[ée]?phone|^t[ée]l\b|portable|whatsapp/i.test(c.nom)) return "telephone"
        if (c.genre === "montant") return "montant"
        if (c.genre === "date") return "date"
        if (c.genre === "pourcent" || c.nombre) return "nombre"
        var vals = valeursDe(c.nom), n = 0
        for (var k = 0; k < lignes.length; k++) if (lignes[k].v[i]) n++
        if (vals.length && vals.every(function (v) { return /^(oui|non)$/i.test(v) })) return "ouinon"
        if (i > 0 && n >= 2 && vals.length <= Math.min(8, Math.max(2, Math.ceil(n / 2))) && vals.every(function (v) { return v.length <= 24 })) return "choix"
        return "texte"
    }
    function champDe(c, d, i) {
        var type = d && d.type ? d.type : deduire(c, i)
        return {
            col: c.nom,
            question: d && d.question ? d.question : c.nom,
            aide: d && d.aide ? d.aide : "",
            type: type,
            options: d && d.options && d.options.length ? d.options : (type === "choix" ? valeursDe(c.nom) : []),
            obligatoire: d ? !!d.obligatoire : i === 0,
            defaut: d && d.defaut !== undefined ? d.defaut : (type === "date" ? "aujourdhui" : ""),
            cache: d ? !!d.cache : false
        }
    }
    function fusion() {
        var res = [], vus = {}, def = definition.champs || []
        for (var k = 0; k < def.length; k++) {
            var c = colonne(def[k].col)
            if (c && !c.calcul && !vus[c.nom]) { res.push(champDe(c, def[k], rangColonne(c.nom))); vus[c.nom] = true }
        }
        for (var i = 0; i < colonnes.length; i++) {
            if (!colonnes[i].calcul && !vus[colonnes[i].nom]) res.push(champDe(colonnes[i], null, i))
        }
        return res
    }
    readonly property var visibles: champs.filter(function (c) { return !c.cache })
    readonly property string titre: definition.titre || (tableau ? tableau.nom.replace(/_/g, " ") : "")

    // ——— Composer : chaque changement est gardé (un instant après) ———
    property int choisi: -1
    function changer(i, cle, valeur) {
        var c = champs.slice()
        var nouveau = Object.assign({}, c[i])
        nouveau[cle] = valeur
        c[i] = nouveau
        champs = c
        garder.restart()
    }
    function deplacer(i, pas) {
        var j = i + pas
        if (j < 0 || j >= champs.length) return
        var c = champs.slice(), x = c[i]
        c[i] = c[j]; c[j] = x
        champs = c
        if (choisi === i) choisi = j
        garder.restart()
    }
    function changerTexte(cle, valeur) {
        var d = Object.assign({}, definition)
        d[cle] = valeur
        definition = d
        garder.restart()
    }
    Timer { id: garder; interval: 500; onTriggered: formulaire.garderMaintenant() }
    function garderMaintenant() {
        garder.stop()
        if (!tableau) return
        var d = { titre: definition.titre || "", intro: definition.intro || "", champs: champs }
        definition = d
        tableaux.regler(tableau, "formulaire", JSON.stringify(d))
    }
    // Terminé : ce qu'on était en train d'écrire est pris (le champ lâche le clavier), tout est gardé, et on revient au
    // formulaire à remplir
    function fermerQuestion() {
        formulaire.forceActiveFocus()
        if (garder.running) garderMaintenant()
        choisi = -1
    }
    function terminer() {
        var avant = mode
        formulaire.forceActiveFocus()
        if (garder.running) garderMaintenant()
        choisi = -1
        mode = "remplir"
        vider()
        Qt.callLater(premierChamp)
        if (avant === "composer")
            message.montrer("Formulaire prêt" + (fenetre.doc.modifie ? " · Ctrl+S pour l'enregistrer dans le fichier" : ""))
    }
    // Le genre de réponse change aussi l'affichage de la colonne dans la grille (montant en F, date courte…)
    function changerGenre(i, type) {
        changer(i, "type", type)
        if (type === "choix" && !champs[i].options.length) changer(i, "options", valeursDe(champs[i].col))
        var format = { nombre: "nombre", montant: "montant", date: "date", telephone: "texte" }[type]
        if (format) tableaux.formaterColonne(tableau, champs[i].col, format)
    }
    function ajouterQuestion(titreQuestion) {
        tableaux.appeler("SamaFormulaire.AjouterColonne", [tableau.nom, titreQuestion], function (ok, v) {
            if (!tableaux.verifier(ok, v)) return
            message.montrer("Question ajoutée : une colonne « " + v + " » au bout du tableau")
            tableaux.rafraichir()
            // (on relit, et la nouvelle question s'ouvre)
            formulaire.aOuvrir = v
            formulaire.charger()
        })
    }
    property string aOuvrir: ""
    onChampsChanged: if (aOuvrir) {
        for (var i = 0; i < champs.length; i++) if (champs[i].col === aOuvrir) { choisi = i; break }
        aOuvrir = ""
        garder.restart()
    }

    // ——— Remplir ———
    property var reponses: ({})
    property var erreurs: ({})
    property int saisies: 0
    function aujourdhui() {
        var d = new Date()
        return ("0" + d.getDate()).slice(-2) + "/" + ("0" + (d.getMonth() + 1)).slice(-2) + "/" + d.getFullYear()
    }
    function vider() {
        // (le champ où l'on écrit lâche le clavier : sinon il garderait son texte)
        formulaire.forceActiveFocus()
        var r = {}
        for (var i = 0; i < champs.length; i++) {
            var c = champs[i]
            r[c.col] = c.type === "date" && c.defaut === "aujourdhui" ? aujourdhui() : (c.defaut === "aujourdhui" ? "" : (c.defaut || ""))
        }
        reponses = r
        erreurs = {}
    }
    function repondre(col, v) {
        var r = Object.assign({}, reponses)
        r[col] = v
        reponses = r
        if (erreurs[col]) { var e = Object.assign({}, erreurs); delete e[col]; erreurs = e }
    }
    function nombre(v) {
        var s = String(v).replace(/[\s  ]/g, "").replace(/F(CFA)?$/i, "").replace(",", ".")
        return s === "" || isNaN(Number(s)) ? NaN : Number(s)
    }
    // « 6/10/2026 », « 06/10/26 » → « 2026-10-06 », sinon ""
    function dateIso(v) {
        var m = /^\s*(\d{1,2})[\/.\-](\d{1,2})[\/.\-](\d{2}|\d{4})\s*$/.exec(String(v))
        if (!m) return ""
        var j = Number(m[1]), mo = Number(m[2]), a = Number(m[3])
        if (a < 100) a += 2000
        if (mo < 1 || mo > 12 || j < 1 || j > 31) return ""
        return a + "-" + ("0" + mo).slice(-2) + "-" + ("0" + j).slice(-2)
    }
    function valider() {
        var e = {}
        for (var i = 0; i < visibles.length; i++) {
            var c = visibles[i], v = String(reponses[c.col] || "").trim()
            if (!v) { if (c.obligatoire) e[c.col] = "À remplir"; continue }
            if ((c.type === "nombre" || c.type === "montant") && isNaN(nombre(v))) e[c.col] = "Un nombre, par exemple 25 000"
            else if (c.type === "date" && !dateIso(v)) e[c.col] = "Une date, par exemple " + aujourdhui()
            else if (c.type === "telephone" && v.replace(/\D/g, "").length < 8) e[c.col] = "Un numéro d'au moins 8 chiffres"
        }
        erreurs = e
        return Object.keys(e).length === 0
    }
    function enregistrer() {
        if (!tableau) return
        if (!valider()) { message.montrer("Une réponse manque ou ne va pas"); return }
        var cols = [], genres = [], vals = []
        for (var i = 0; i < visibles.length; i++) {
            var c = visibles[i], v = String(reponses[c.col] || "").trim()
            cols.push(c.col)
            genres.push(c.type)
            vals.push(v === "" ? "" : c.type === "nombre" || c.type === "montant" ? String(nombre(v)) : c.type === "date" ? dateIso(v) : v)
        }
        var sep = String.fromCharCode(31)
        tableaux.appeler("SamaFormulaire.Ajouter", [tableau.nom, cols.join(sep), genres.join(sep), vals.join(sep)], function (ok, v) {
            if (!tableaux.verifier(ok, v)) return
            formulaire.saisies++
            message.montrer("Enregistré dans le tableau, ligne " + v)
            tableaux.rafraichir()
            formulaire.vider()
            Qt.callLater(formulaire.premierChamp)
        })
    }

    // ——— Au clavier : Tab d'une question à l'autre, Entrée à la suivante, et sur la dernière : enregistrer ———
    property var entrees: []
    function inscrire(item, rang) { var e = entrees.slice(); e[rang] = item; entrees = e }
    function premierChamp() {
        for (var i = 0; i < entrees.length; i++) if (entrees[i] && entrees[i].visible) { entrees[i].forceActiveFocus(); return }
    }
    function suivant(rang) {
        for (var i = rang + 1; i < entrees.length; i++) if (entrees[i] && entrees[i].visible) { entrees[i].forceActiveFocus(); return }
        enregistrer()
    }
    Keys.onEscapePressed: fenetre.voirGrille()

    // ——— Pièces ———
    readonly property var genres: [["texte", "Texte", "M5 7V5h14v2 M12 5v14 M9 19h6"],
                                   ["nombre", "Nombre", "M10 4L8 20 M16 4l-2 16 M5 9h15 M4 15h15"],
                                   ["montant", "Montant", "M3 7h18v10H3z M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"],
                                   ["date", "Date", "M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4"],
                                   ["telephone", "Téléphone", "M7 3.5h3l1.5 4-2 1.3a10 10 0 0 0 4.7 4.7l1.3-2 4 1.5v3a2 2 0 0 1-2 2A16 16 0 0 1 5 5.5a2 2 0 0 1 2-2z"],
                                   ["choix", "Choix", "M9 7h11 M9 12h11 M9 17h11 M4.5 7v.1 M4.5 12v.1 M4.5 17v.1"],
                                   ["ouinon", "Oui ou non", "M5 12.5l4.5 4.5L19 7.5"]]
    function genre(type) { for (var i = 0; i < genres.length; i++) if (genres[i][0] === type) return genres[i]; return genres[0] }
    component Pastille: QQC2.AbstractButton {
        id: pastille
        property bool choisie: false
        height: 34
        width: textePastille.implicitWidth + 28
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        background: Rectangle {
            radius: 17
            color: pastille.choisie ? fenetre.accentFond : pastille.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : Couleurs.champ
            border.width: pastille.choisie ? 1.5 : 1
            border.color: pastille.choisie ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
        }
        contentItem: Text {
            id: textePastille
            text: pastille.text
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            font.pixelSize: 14
            font.weight: pastille.choisie ? Font.DemiBold : Font.Normal
            color: pastille.choisie ? fenetre.accentEncre : Couleurs.texte
        }
    }
    component Champ: Rectangle {
        // Un champ de saisie d'une ligne (Composer)
        property alias texte: saisieChamp.text
        property string indice: ""
        signal fini(string valeur)
        signal valide(string valeur)
        Layout.fillWidth: true
        Layout.preferredHeight: 38
        radius: 9
        color: Couleurs.champ
        border.width: saisieChamp.activeFocus ? 1.5 : 1
        border.color: saisieChamp.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
        TextInput {
            id: saisieChamp
            anchors.fill: parent
            anchors.leftMargin: 12
            anchors.rightMargin: 12
            verticalAlignment: TextInput.AlignVCenter
            font.pixelSize: 14
            color: Couleurs.texte
            clip: true
            selectByMouse: true
            activeFocusOnTab: true
            onEditingFinished: parent.fini(text)
            onAccepted: parent.valide(text)
            Text { anchors.verticalCenter: parent.verticalCenter; visible: !parent.text; text: parent.parent.indice; font.pixelSize: 14; color: Couleurs.texte3 }
        }
    }
    component Interrupteur: QQC2.AbstractButton {
        id: inter
        checkable: true
        implicitWidth: 34
        implicitHeight: 20
        focusPolicy: Qt.NoFocus
        background: Rectangle {
            radius: 10
            color: inter.checked ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.2)
            Rectangle { width: 14; height: 14; radius: 7; y: 3; x: inter.checked ? parent.width - width - 3 : 3; color: "#FFFFFF"; Behavior on x { NumberAnimation { duration: 120 } } }
        }
    }

    // ——— La page ———
    QQC2.ScrollView {
        id: defilement
        anchors.fill: parent
        contentWidth: availableWidth
        QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff

        ColumnLayout {
            id: page
            width: defilement.availableWidth
            spacing: 14
            readonly property real largeur: Math.min(680, defilement.availableWidth - 48)

            // Remplir | Composer
            RowLayout {
                // (aligné sur la carte : pas d'étirement sur toute la largeur)
                Layout.fillWidth: false
                Layout.preferredWidth: page.largeur
                Layout.maximumWidth: page.largeur
                Layout.alignment: Qt.AlignHCenter
                Layout.topMargin: 20
                spacing: 10
                ChoixVues {
                    hauteur: 36
                    taille: 13
                    modele: [["remplir", "Remplir", "M4 20h4L18.5 9.5l-4-4L4 16z M12.5 7.5l4 4"], ["composer", "Composer", "M4 6h16 M4 12h16 M4 18h10 M18 15v6 M15 18h6"]]
                    courant: formulaire.mode
                    onChoisi: cle => cle === "remplir" ? formulaire.terminer() : (formulaire.mode = "composer")
                }
                Item { Layout.fillWidth: true }
                Text {
                    visible: formulaire.mode === "remplir" && formulaire.saisies > 0
                    text: formulaire.saisies + (formulaire.saisies > 1 ? " réponses enregistrées" : " réponse enregistrée")
                    font.pixelSize: 13
                    color: Couleurs.texte2
                }
                // (Composer : ce qui est gardé, et Terminé)
                Row {
                    visible: formulaire.mode === "composer"
                    spacing: 6
                    Picto {
                        anchors.verticalCenter: parent.verticalCenter
                        width: 14; height: 14
                        trace: garder.running ? "M12 7v5l3 2 M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z" : "M5 12.5l4.5 4.5L19 7.5"
                        encre: garder.running ? Couleurs.texte3 : fenetre.vertEncre
                        trait: 2
                    }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: garder.running ? "On garde…" : "Gardé dans le classeur"
                        font.pixelSize: 13
                        color: Couleurs.texte2
                    }
                }
                Outil {
                    visible: formulaire.mode === "remplir"
                    text: "Voir le tableau"
                    picto: "M4 5h16v14H4z M4 10h16 M10 5v14"
                    onClicked: fenetre.voirGrille()
                }
                QQC2.AbstractButton {
                    id: boutonTermine
                    visible: formulaire.mode === "composer"
                    Layout.preferredHeight: 36
                    leftPadding: 14
                    rightPadding: 16
                    hoverEnabled: true
                    focusPolicy: Qt.NoFocus
                    onClicked: formulaire.terminer()
                    QQC2.ToolTip.visible: hovered
                    QQC2.ToolTip.delay: 500
                    QQC2.ToolTip.text: "Garder les changements et revenir au formulaire à remplir"
                    background: Rectangle { radius: 10; color: boutonTermine.down ? Qt.darker(fenetre.accent, 1.2) : boutonTermine.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent }
                    contentItem: Row {
                        spacing: 7
                        Picto { anchors.verticalCenter: parent.verticalCenter; width: 15; height: 15; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: "#FFFFFF"; trait: 2.2 }
                        Text { anchors.verticalCenter: parent.verticalCenter; text: "Terminé"; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#FFFFFF" }
                    }
                }
            }

            // ——— La carte du formulaire ———
            Rectangle {
                Layout.preferredWidth: page.largeur
                Layout.preferredHeight: carte.implicitHeight + 56
                Layout.alignment: Qt.AlignHCenter
                radius: 16
                color: Couleurs.champ
                border.width: 1
                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                clip: true
                // (le haut, à la couleur de Sheet)
                Rectangle { width: parent.width; height: 6; color: fenetre.accent }

                ColumnLayout {
                    id: carte
                    x: 28
                    y: 30
                    width: parent.width - 56
                    spacing: 22

                    // Titre et présentation
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 6
                        TextInput {
                            Layout.fillWidth: true
                            text: formulaire.titre
                            readOnly: formulaire.mode !== "composer"
                            font.pixelSize: 26
                            font.weight: Font.DemiBold
                            font.letterSpacing: -0.2
                            color: Couleurs.texte
                            wrapMode: TextInput.Wrap
                            selectByMouse: !readOnly
                            onEditingFinished: if (!readOnly) formulaire.changerTexte("titre", text)
                            Rectangle { visible: !parent.readOnly; anchors.top: parent.bottom; anchors.topMargin: 2; width: parent.width; height: parent.activeFocus ? 2 : 1; color: parent.activeFocus ? fenetre.accent : Couleurs.bord }
                        }
                        TextEdit {
                            id: intro
                            Layout.fillWidth: true
                            Layout.topMargin: 4
                            visible: formulaire.mode === "composer" || text !== ""
                            text: formulaire.definition.intro || ""
                            readOnly: formulaire.mode !== "composer"
                            font.pixelSize: 14
                            color: Couleurs.texte2
                            wrapMode: TextEdit.Wrap
                            selectByMouse: !readOnly
                            onActiveFocusChanged: if (!activeFocus && !readOnly) formulaire.changerTexte("intro", text)
                            Text { visible: !parent.text && !parent.readOnly; text: "Une phrase pour dire à quoi sert ce formulaire (facultatif)"; font: parent.font; color: Couleurs.texte3 }
                            Rectangle { visible: !parent.readOnly; anchors.top: parent.bottom; anchors.topMargin: 2; width: parent.width; height: parent.activeFocus ? 2 : 1; color: parent.activeFocus ? fenetre.accent : Couleurs.bord }
                        }
                        Text {
                            visible: formulaire.mode === "remplir" && formulaire.visibles.some(function (c) { return c.obligatoire })
                            Layout.topMargin: 6
                            text: "* obligatoire"
                            font.pixelSize: 12
                            color: "#B5532F"
                        }
                    }

                    // ——— Remplir : les questions ———
                    // (un nombre, pas la liste : changer une réponse ou un réglage ne refait pas les questions)
                    Repeater {
                        model: formulaire.mode === "remplir" ? formulaire.champs.length : 0
                        delegate: ColumnLayout {
                            id: question
                            readonly property var champ: formulaire.champs[index] || ({ col: "", question: "", aide: "", type: "texte", options: [], obligatoire: false, cache: true })
                            readonly property string erreur: formulaire.erreurs[champ.col] || ""
                            readonly property string valeur: formulaire.reponses[champ.col] || ""
                            visible: !champ.cache
                            Layout.fillWidth: true
                            spacing: 6
                            RowLayout {
                                spacing: 4
                                Text { text: question.champ.question; font.pixelSize: 15; font.weight: Font.DemiBold; color: Couleurs.texte; wrapMode: Text.Wrap; Layout.maximumWidth: carte.width - 20 }
                                Text { visible: question.champ.obligatoire; text: "*"; font.pixelSize: 15; color: "#B5532F" }
                            }
                            Text { visible: question.champ.aide !== ""; Layout.fillWidth: true; text: question.champ.aide; font.pixelSize: 13; color: Couleurs.texte2; wrapMode: Text.Wrap }
                            // Saisie (texte, nombre, montant, date, téléphone)
                            Rectangle {
                                visible: question.champ.type !== "choix" && question.champ.type !== "ouinon"
                                Layout.fillWidth: true
                                Layout.maximumWidth: question.champ.type === "texte" ? carte.width : 320
                                Layout.preferredHeight: 44
                                radius: 10
                                color: Couleurs.champ
                                border.width: reponse.activeFocus || question.erreur ? 1.5 : 1
                                border.color: question.erreur ? "#B5532F" : reponse.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 14
                                    anchors.rightMargin: 8
                                    spacing: 8
                                    TextInput {
                                        id: reponse
                                        Layout.fillWidth: true
                                        // (pas de liaison sur le texte : pendant la frappe, c'est le champ qui fait foi ; il ne
                                        // reprend la réponse que quand elle change d'ailleurs : formulaire vidé, « Aujourd'hui »)
                                        Component.onCompleted: { text = question.valeur; formulaire.inscrire(reponse, index) }
                                        Connections {
                                            target: formulaire
                                            function onReponsesChanged() { if (reponse.text !== question.valeur && !reponse.activeFocus) reponse.text = question.valeur }
                                        }
                                        font.pixelSize: 15
                                        font.features: { "tnum": 1 }
                                        color: Couleurs.texte
                                        clip: true
                                        selectByMouse: true
                                        activeFocusOnTab: true
                                        onTextEdited: formulaire.repondre(question.champ.col, text)
                                        Keys.onReturnPressed: e => { if (e.modifiers & Qt.ControlModifier) formulaire.enregistrer(); else formulaire.suivant(index) }
                                        Keys.onEnterPressed: e => { if (e.modifiers & Qt.ControlModifier) formulaire.enregistrer(); else formulaire.suivant(index) }
                                        Text {
                                            anchors.verticalCenter: parent.verticalCenter
                                            visible: !parent.text
                                            text: question.champ.type === "date" ? "jj/mm/aaaa" : question.champ.type === "telephone" ? "07 00 00 00 00"
                                                  : question.champ.type === "montant" ? "0" : question.champ.type === "nombre" ? "0" : "Votre réponse"
                                            font: parent.font
                                            color: Couleurs.texte3
                                        }
                                    }
                                    Text { visible: question.champ.type === "montant"; text: "F CFA"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte2 }
                                    Outil {
                                        visible: question.champ.type === "date"
                                        Layout.preferredHeight: 30
                                        text: "Aujourd'hui"
                                        police.pixelSize: 12
                                        onClicked: { reponse.text = formulaire.aujourdhui(); formulaire.repondre(question.champ.col, reponse.text) }
                                    }
                                }
                            }
                            // Choix, oui ou non : des pastilles
                            Flow {
                                visible: question.champ.type === "choix" || question.champ.type === "ouinon"
                                Layout.fillWidth: true
                                spacing: 8
                                Repeater {
                                    model: question.champ.type === "ouinon" ? ["Oui", "Non"] : question.champ.options
                                    delegate: Pastille {
                                        text: modelData
                                        choisie: question.valeur === modelData
                                        onClicked: formulaire.repondre(question.champ.col, choisie ? "" : modelData)
                                    }
                                }
                                Text {
                                    visible: question.champ.type === "choix" && !question.champ.options.length
                                    text: "Pas encore de choix : ajoutez-en dans Composer"
                                    font.pixelSize: 13
                                    color: Couleurs.texte3
                                }
                            }
                            Text { visible: question.erreur !== ""; text: question.erreur; font.pixelSize: 12; color: "#B5532F" }
                        }
                    }

                    // ——— Composer : une carte par question ———
                    Repeater {
                        model: formulaire.mode === "composer" ? formulaire.champs.length : 0
                        delegate: Rectangle {
                            id: carteQuestion
                            readonly property var champ: formulaire.champs[index] || ({ col: "", question: "", aide: "", type: "texte", options: [], obligatoire: false, cache: false })
                            readonly property bool ouverte: formulaire.choisi === index
                            Layout.fillWidth: true
                            Layout.preferredHeight: (ouverte ? reglages.implicitHeight : resume.implicitHeight) + 28
                            radius: 12
                            color: ouverte ? Couleurs.champ : champ.cache ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.03) : Couleurs.champ
                            border.width: ouverte ? 1.5 : 1
                            border.color: ouverte ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                            // (fermée : un clic l'ouvre)
                            MouseArea { anchors.fill: parent; enabled: !carteQuestion.ouverte; cursorShape: Qt.PointingHandCursor; onClicked: formulaire.choisi = index }

                            // Fermée : le résumé
                            RowLayout {
                                id: resume
                                visible: !carteQuestion.ouverte
                                x: 14; y: 14
                                width: parent.width - 28
                                spacing: 10
                                Column {
                                    spacing: 0
                                    Outil { width: 26; height: 20; picto: "M7 14l5-5 5 5"; aide: "Monter"; enabled: index > 0; onClicked: formulaire.deplacer(index, -1) }
                                    Outil { width: 26; height: 20; picto: "M7 10l5 5 5-5"; aide: "Descendre"; enabled: index < formulaire.champs.length - 1; onClicked: formulaire.deplacer(index, 1) }
                                }
                                Rectangle {
                                    Layout.preferredWidth: 30; Layout.preferredHeight: 30; radius: 8
                                    color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
                                    Picto { anchors.centerIn: parent; width: 15; height: 15; trace: formulaire.genre(carteQuestion.champ.type)[2]; encre: Couleurs.texte2 }
                                }
                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: 1
                                    Text { Layout.fillWidth: true; text: carteQuestion.champ.question + (carteQuestion.champ.obligatoire ? " *" : ""); font.pixelSize: 14; font.weight: Font.DemiBold; color: carteQuestion.champ.cache ? Couleurs.texte3 : Couleurs.texte; elide: Text.ElideRight }
                                    Text {
                                        Layout.fillWidth: true
                                        text: formulaire.genre(carteQuestion.champ.type)[1] + (carteQuestion.champ.type === "choix" ? " · " + carteQuestion.champ.options.join(", ") : "")
                                              + (carteQuestion.champ.cache ? " · masquée" : "") + (carteQuestion.champ.question !== carteQuestion.champ.col ? " · colonne « " + carteQuestion.champ.col + " »" : "")
                                        font.pixelSize: 12
                                        color: Couleurs.texte2
                                        elide: Text.ElideRight
                                    }
                                }
                                Outil {
                                    picto: carteQuestion.champ.cache ? "M3 3l18 18 M10.6 10.6a2 2 0 0 0 2.8 2.8 M9.9 5.1A10 10 0 0 1 12 5c6 0 10 7 10 7a17 17 0 0 1-2.2 3 M6.6 6.6C3.9 8.4 2 12 2 12s4 7 10 7a9.7 9.7 0 0 0 5.4-1.6"
                                                               : "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
                                    aide: carteQuestion.champ.cache ? "Montrer cette question" : "Masquer cette question (la colonne reste)"
                                    onClicked: formulaire.changer(index, "cache", !carteQuestion.champ.cache)
                                }
                            }

                            // Ouverte : les réglages
                            ColumnLayout {
                                id: reglages
                                visible: carteQuestion.ouverte
                                x: 18; y: 14
                                width: parent.width - 36
                                spacing: 12
                                RowLayout {
                                    Layout.fillWidth: true
                                    Text { Layout.fillWidth: true; text: "Colonne « " + carteQuestion.champ.col + " »"; font.pixelSize: 12; color: Couleurs.texte2 }
                                    Outil { picto: "M7 7l10 10 M17 7L7 17"; aide: "Fermer cette question"; onClicked: formulaire.fermerQuestion() }
                                }
                                Champ {
                                    texte: carteQuestion.champ.question
                                    indice: "La question"
                                    onFini: v => { if (v.trim() && v !== carteQuestion.champ.question) formulaire.changer(index, "question", v.trim()) }
                                }
                                Champ {
                                    texte: carteQuestion.champ.aide
                                    indice: "Une aide sous la question (facultatif) : un exemple, une précision"
                                    onFini: v => { if (v !== carteQuestion.champ.aide) formulaire.changer(index, "aide", v) }
                                }
                                Text { text: "Genre de réponse"; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte2; Layout.topMargin: 2 }
                                Flow {
                                    Layout.fillWidth: true
                                    spacing: 6
                                    Repeater {
                                        model: formulaire.genres
                                        delegate: QQC2.AbstractButton {
                                            id: genreBouton
                                            readonly property bool choisi: carteQuestion.champ.type === modelData[0]
                                            height: 30
                                            width: contenuGenre.implicitWidth + 20
                                            hoverEnabled: true
                                            focusPolicy: Qt.NoFocus
                                            onClicked: if (!choisi) formulaire.changerGenre(carteQuestion.rang, modelData[0])
                                            background: Rectangle {
                                                radius: 8
                                                color: genreBouton.choisi ? fenetre.accentFond : genreBouton.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                                                border.width: genreBouton.choisi ? 0 : 1
                                                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                                            }
                                            contentItem: Item {
                                                Row {
                                                    id: contenuGenre
                                                    anchors.centerIn: parent
                                                    spacing: 6
                                                    Picto { width: 14; height: 14; anchors.verticalCenter: parent.verticalCenter; trace: modelData[2]; encre: genreBouton.choisi ? fenetre.accentEncre : Couleurs.texte2 }
                                                    Text { anchors.verticalCenter: parent.verticalCenter; text: modelData[1]; font.pixelSize: 12; font.weight: genreBouton.choisi ? Font.DemiBold : Font.Normal; color: genreBouton.choisi ? fenetre.accentEncre : Couleurs.texte }
                                                }
                                            }
                                        }
                                    }
                                }
                                // Les choix
                                ColumnLayout {
                                    visible: carteQuestion.champ.type === "choix"
                                    Layout.fillWidth: true
                                    spacing: 8
                                    Text { text: "Les choix"; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte2 }
                                    Flow {
                                        Layout.fillWidth: true
                                        spacing: 6
                                        Repeater {
                                            model: carteQuestion.champ.options
                                            delegate: Rectangle {
                                                height: 30
                                                width: texteOption.implicitWidth + 40
                                                radius: 15
                                                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
                                                Text { id: texteOption; x: 12; anchors.verticalCenter: parent.verticalCenter; text: modelData; font.pixelSize: 13; color: Couleurs.texte }
                                                Outil {
                                                    anchors.right: parent.right
                                                    anchors.rightMargin: 3
                                                    anchors.verticalCenter: parent.verticalCenter
                                                    width: 24; height: 24
                                                    picto: "M8 8l8 8 M16 8l-8 8"
                                                    aide: "Enlever ce choix"
                                                    onClicked: formulaire.changer(carteQuestion.rang, "options", carteQuestion.champ.options.filter(function (o) { return o !== modelData }))
                                                }
                                            }
                                        }
                                    }
                                    RowLayout {
                                        Layout.fillWidth: true
                                        spacing: 8
                                        Champ {
                                            id: nouveauChoix
                                            indice: "Un choix de plus, puis Entrée"
                                            onValide: v => {
                                                var o = v.trim()
                                                if (o && carteQuestion.champ.options.indexOf(o) < 0) formulaire.changer(carteQuestion.rang, "options", carteQuestion.champ.options.concat([o]))
                                                nouveauChoix.texte = ""
                                            }
                                        }
                                        Outil {
                                            text: "Reprendre ceux du tableau"
                                            police.pixelSize: 12
                                            aide: "Les valeurs déjà saisies dans la colonne"
                                            onClicked: formulaire.changer(carteQuestion.rang, "options", formulaire.valeursDe(carteQuestion.champ.col))
                                        }
                                    }
                                }
                                // Obligatoire, défaut, masquer
                                RowLayout {
                                    Layout.fillWidth: true
                                    Layout.topMargin: 4
                                    spacing: 10
                                    Interrupteur { checked: carteQuestion.champ.obligatoire; onToggled: formulaire.changer(carteQuestion.rang, "obligatoire", checked) }
                                    Text { text: "Obligatoire"; font.pixelSize: 13; color: Couleurs.texte }
                                    Item { Layout.preferredWidth: 14 }
                                    Interrupteur {
                                        visible: carteQuestion.champ.type === "date"
                                        checked: carteQuestion.champ.defaut === "aujourdhui"
                                        onToggled: formulaire.changer(carteQuestion.rang, "defaut", checked ? "aujourdhui" : "")
                                    }
                                    Text { visible: carteQuestion.champ.type === "date"; text: "La date du jour, déjà remplie"; font.pixelSize: 13; color: Couleurs.texte }
                                    Item { Layout.fillWidth: true }
                                    Outil {
                                        text: carteQuestion.champ.cache ? "Montrer" : "Masquer"
                                        picto: "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
                                        aide: "Une question masquée n'est pas posée ; sa colonne reste dans le tableau"
                                        onClicked: formulaire.changer(carteQuestion.rang, "cache", !carteQuestion.champ.cache)
                                    }
                                    QQC2.AbstractButton {
                                        id: questionFaite
                                        Layout.preferredHeight: 32
                                        leftPadding: 12
                                        rightPadding: 14
                                        hoverEnabled: true
                                        focusPolicy: Qt.NoFocus
                                        onClicked: formulaire.fermerQuestion()
                                        background: Rectangle { radius: 9; color: questionFaite.hovered ? Qt.darker(fenetre.accentFond, 1.15) : fenetre.accentFond }
                                        contentItem: Row {
                                            spacing: 6
                                            Picto { anchors.verticalCenter: parent.verticalCenter; width: 14; height: 14; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: fenetre.accentEncre; trait: 2.2 }
                                            Text { anchors.verticalCenter: parent.verticalCenter; text: "Terminé"; font.pixelSize: 13; font.weight: Font.DemiBold; color: fenetre.accentEncre }
                                        }
                                    }
                                }
                            }
                            readonly property int rang: index
                        }
                    }

                    // Composer : une question de plus
                    RowLayout {
                        visible: formulaire.mode === "composer"
                        Layout.fillWidth: true
                        spacing: 8
                        Champ {
                            id: nouvelleQuestion
                            indice: "Une question de plus (elle devient une colonne du tableau), puis Entrée"
                            onValide: v => { if (v.trim()) { formulaire.ajouterQuestion(v.trim()); nouvelleQuestion.texte = "" } }
                        }
                        Outil {
                            text: "Ajouter"
                            picto: "M12 5v14 M5 12h14"
                            background: Rectangle { radius: 8; color: parent.hovered ? Qt.darker(fenetre.accentFond, 1.1) : fenetre.accentFond }
                            encre: fenetre.accentEncre
                            onClicked: { if (nouvelleQuestion.texte.trim()) { formulaire.ajouterQuestion(nouvelleQuestion.texte.trim()); nouvelleQuestion.texte = "" } }
                        }
                    }

                    // Remplir : enregistrer
                    RowLayout {
                        visible: formulaire.mode === "remplir"
                        Layout.fillWidth: true
                        Layout.topMargin: 4
                        spacing: 12
                        QQC2.AbstractButton {
                            id: boutonEnregistrer
                            Layout.preferredHeight: 42
                            Layout.preferredWidth: contenuEnregistrer.implicitWidth + 36
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: formulaire.enregistrer()
                            background: Rectangle { radius: 11; color: boutonEnregistrer.down ? Qt.darker(fenetre.accent, 1.2) : boutonEnregistrer.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent }
                            contentItem: Item {
                                Row {
                                    id: contenuEnregistrer
                                    anchors.centerIn: parent
                                    spacing: 8
                                    Picto { width: 16; height: 16; anchors.verticalCenter: parent.verticalCenter; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: "#FFFFFF"; trait: 2.2 }
                                    Text { anchors.verticalCenter: parent.verticalCenter; text: "Enregistrer"; font.pixelSize: 15; font.weight: Font.DemiBold; color: "#FFFFFF" }
                                }
                            }
                        }
                        Text { text: "Entrée sur la dernière question"; font.pixelSize: 12; color: Couleurs.texte3 }
                        Item { Layout.fillWidth: true }
                        QQC2.AbstractButton {
                            focusPolicy: Qt.NoFocus
                            hoverEnabled: true
                            background: null
                            contentItem: Text { text: "Tout effacer"; font.pixelSize: 13; font.weight: Font.DemiBold; font.underline: parent.hovered; color: fenetre.accentEncre }
                            onClicked: { formulaire.vider(); Qt.callLater(formulaire.premierChamp) }
                        }
                    }
                }
            }

            // Où vont les réponses ; et la sécurité
            ColumnLayout {
                Layout.fillWidth: false
                Layout.preferredWidth: page.largeur
                Layout.maximumWidth: page.largeur
                Layout.alignment: Qt.AlignHCenter
                Layout.bottomMargin: 28
                spacing: 4
                Text {
                    Layout.fillWidth: true
                    text: formulaire.mode === "composer"
                          ? "Chaque changement est gardé dans le classeur, avec le tableau (Ctrl+S l'enregistre dans le fichier). Terminé revient au formulaire à remplir."
                          : "Les réponses vont dans le tableau « " + (formulaire.tableau ? formulaire.tableau.nom.replace(/_/g, " ") : "") + " »."
                    font.pixelSize: 12
                    color: Couleurs.texte2
                    wrapMode: Text.Wrap
                }
                Text {
                    Layout.fillWidth: true
                    text: "Ce formulaire reste sur cet appareil : pas de lien à envoyer, personne d'autre ne peut le remplir à votre place."
                    font.pixelSize: 12
                    color: Couleurs.texte3
                    wrapMode: Text.Wrap
                }
            }
        }
    }
}
