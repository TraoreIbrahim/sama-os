// La marge des commentaires et des suggestions (maquette « Docs · Le document libre »), à droite de la page : chaque
// carte à la hauteur de son texte (poussée plus bas si elle chevauche la précédente ; celle qu'on lit, à sa place
// exacte). Les commentaires en fils (réponses, Résoudre, Modifier, Supprimer) ; les suggestions du mode Suggestion
// (les modifications suivies du moteur) regroupées par auteur, en mots (« Remplacer « deux » par « trois » »), à
// accepter ou refuser. Le moteur les annonce (commentairesChanges, suggestionsChangees) ; Sama relit leur liste.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import Sama.Moteur
import "../reglages"
import "../sheet"

Item {
    id: marge
    property var doc
    // ——— Ce que la marge montre ———
    property var commentaires: []      // la liste du moteur (.uno:ViewAnnotations)
    property var modifications: []     // (.uno:AcceptTrackedChanges)
    property bool montrerResolus: false
    property string active: ""         // la carte qu'on lit (« c3 », « s7 », « nouveau »)
    property real yNouveau: -1         // le nouveau commentaire : à la hauteur de la sélection (pixels du document)
    readonly property bool ecrit: yNouveau >= 0
    readonly property var fils: construireFils(commentaires)
    readonly property var groupes: grouper(modifications)
    readonly property int resolus: fils.filter(function (f) { return f.resolu }).length
    readonly property var filsVus: fils.filter(function (f) { return !f.resolu || montrerResolus })
    readonly property int nombre: filsVus.length + groupes.length
    readonly property bool utile: fils.length > 0 || groupes.length > 0 || ecrit
    signal annonce(string texte)

    // ——— Lire ———
    function relire() { if (doc.etat === DocumentLO.Pret) { doc.demanderValeurs(".uno:ViewAnnotations"); doc.demanderValeurs(".uno:AcceptTrackedChanges") } }
    Timer { id: aRelire; interval: 200; onTriggered: marge.relire() }
    Connections {
        target: marge.doc
        function onCommentairesChanges() { aRelire.restart() }
        function onSuggestionsChangees() { aRelire.restart() }
        function onEtatChanged() { marge.commentaires = []; marge.modifications = []; marge.yNouveau = -1; aRelire.restart() }
        function onValeurs(commande, reponse) {
            if (commande === ".uno:ViewAnnotations") marge.commentaires = (reponse && reponse.comments) || []
            else if (commande === ".uno:AcceptTrackedChanges") marge.modifications = (reponse && reponse.redlines) || []
        }
        // (le curseur dans le texte d'un commentaire ou d'une suggestion : sa carte)
        function onCurseurTexteChanged() { suivreCurseur.restart() }
    }
    Timer { id: suivreCurseur; interval: 120; onTriggered: marge.carteAuCurseur() }

    // ——— Mettre en forme ———
    function texteDe(html) {
        return String(html || "").replace(/<\/(div|p)>\s*<(div|p)[^>]*>/g, "\n").replace(/<br\s*\/?>/g, "\n").replace(/<[^>]+>/g, "")
            .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, "\"").replace(/&#39;/g, "'").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").trim()
    }
    function dateDe(t) {
        var m = String(t || "").match(/^(\d+)-(\d+)-(\d+)T(\d+):(\d+):(\d+)/)
        return m ? new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]) : new Date()
    }
    function quand(d) {
        var maintenant = new Date(), s = (maintenant - d) / 1000
        if (s < 60) return "À l'instant"
        if (s < 3600) return "Il y a " + Math.floor(s / 60) + " min"
        var jours = Math.round((new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate()) - new Date(d.getFullYear(), d.getMonth(), d.getDate())) / 86400000)
        var h = Qt.formatTime(d, "hh:mm")
        return jours === 0 ? "Aujourd'hui, " + h : jours === 1 ? "Hier, " + h : Qt.formatDate(d, "d MMM") + ", " + h
    }
    function initiales(nom) { return fenetre.initiales(nom) }
    // (une teinte par personne, toujours la même)
    readonly property var teintes: [["#F0E7DB", "#665E54"], ["#E3E8F3", "#2A4278"], ["#E2ECE7", "#265646"], ["#F3E3DA", "#8A3A1E"], ["#EDE6F2", "#5A3E78"]]
    function teinte(nom) {
        var h = 0
        for (var i = 0; i < nom.length; ++i) h = (h * 31 + nom.charCodeAt(i)) % 997
        return teintes[h % teintes.length]
    }
    function premierRect(twips) {
        var p = String(twips || "").split(";")[0].split(",")
        return p.length >= 4 ? { x: +p[0], y: +p[1], l: +p[2], h: +p[3] } : null
    }

    // Les fils : un commentaire et ses réponses (le moteur chaîne chaque réponse au commentaire auquel elle répond)
    function construireFils(liste) {
        var parId = {}, racines = []
        liste.forEach(function (c) { parId[String(c.id)] = c })
        function racine(c) {
            var r = c, n = 0
            while (String(r.parentId) !== "0" && parId[String(r.parentId)] && n++ < 50) r = parId[String(r.parentId)]
            return r
        }
        var fils = {}
        liste.forEach(function (c) {
            var r = racine(c), cle = String(r.id)
            if (!fils[cle]) {
                var rect = premierRect(r.textRange) || premierRect(r.anchorPos)
                fils[cle] = { cle: "c" + cle, id: cle, auteur: r.author || "", date: dateDe(r.dateTime), texte: texteDe(r.html || r.text),
                              resolu: String(r.resolved) === "true", plage: r.textRange || r.anchorPos || "", y: rect ? rect.y : 0, x: rect ? rect.x : 0, reponses: [] }
                racines.push(fils[cle])
            }
            if (c !== r) fils[cle].reponses.push({ id: String(c.id), auteur: c.author || "", date: dateDe(c.dateTime), texte: texteDe(c.html || c.text) })
        })
        racines.forEach(function (f) { f.reponses.sort(function (a, b) { return a.date - b.date }) })
        return racines
    }

    // Les suggestions : dans l'ordre du texte ; une suppression et un ajout côte à côte deviennent un remplacement ; les
    // changements voisins d'une même personne, une seule carte
    function extrait(description) {
        var d = String(description || ""), i = d.indexOf("«"), j = d.lastIndexOf("»")
        return i >= 0 && j > i ? d.slice(i + 1, j).trim() : d
    }
    function grouper(liste) {
        var m = liste.map(function (r) {
            var rect = premierRect(r.textRange) || { x: 0, y: 0, l: 0, h: 0 }
            return { index: String(r.index), auteur: r.author || "", date: dateDe(r.dateTime), genre: r.type, texte: extrait(r.description),
                     description: r.description, plage: r.textRange || "", x: rect.x, y: rect.y, l: rect.l }
        }).sort(function (a, b) { return Math.abs(a.y - b.y) > 60 ? a.y - b.y : a.x - b.x })
        var changements = []
        for (var i = 0; i < m.length; ++i) {
            var a = m[i], b = m[i + 1]
            if (b && a.auteur === b.auteur && Math.abs(a.y - b.y) < 60
                    && ((a.genre === "Delete" && b.genre === "Insert") || (a.genre === "Insert" && b.genre === "Delete"))
                    && Math.abs(b.x - (a.x + a.l)) < 200) {
                var sup = a.genre === "Delete" ? a : b, ajout = a.genre === "Delete" ? b : a
                changements.push({ genre: "Remplacer", ancien: sup.texte, nouveau: ajout.texte, index: [a.index, b.index], auteur: a.auteur,
                                   date: a.date > b.date ? a.date : b.date, y: a.y, plage: a.plage + ";" + b.plage })
                ++i
            } else changements.push({ genre: a.genre, texte: a.texte, description: a.description, index: [a.index], auteur: a.auteur, date: a.date, y: a.y, plage: a.plage })
        }
        var groupes = []
        changements.forEach(function (c) {
            var g = groupes.length ? groupes[groupes.length - 1] : null
            if (g && g.auteur === c.auteur && c.y - g.yFin < 1500) {
                g.changements.push(c)
                g.yFin = c.y
                if (c.date > g.date) g.date = c.date
            } else groupes.push({ cle: "s" + c.index[0], auteur: c.auteur, date: c.date, y: c.y, yFin: c.y, changements: [c] })
        })
        return groupes
    }
    function phrase(c) {
        function q(t) { return "« <font color=\"#3D5A99\"><b>" + echapper(t) + "</b></font> »" }
        if (c.genre === "Remplacer") return "Remplacer « " + echapper(c.ancien) + " » par " + q(c.nouveau)
        if (c.genre === "Insert") return "Ajouter " + q(c.texte)
        if (c.genre === "Delete") return "Supprimer « <s>" + echapper(c.texte) + "</s> »"
        if (c.genre === "Format" || c.genre === "Attributes" || c.genre === "ParagraphFormat") return "Mettre en forme « " + echapper(c.texte) + " »"
        return echapper(c.description || c.texte)
    }
    function echapper(t) { return String(t).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;") }

    // ——— Agir ———
    function chaine(v) { return { type: "string", value: String(v) } }
    function commenter() {
        var s = doc.selection
        var y = s && s.length ? s[0].y : doc.curseurTexte.y
        yNouveau = Math.max(0, y)
        active = "nouveau"
    }
    function publier(texte) {
        var t = String(texte).trim()
        yNouveau = -1
        active = ""
        if (t) doc.commande(".uno:InsertAnnotation", { Text: chaine(t) })
        doc.forceActiveFocus()
    }
    function repondre(f, texte) {
        var t = String(texte).trim()
        if (t) doc.commande(".uno:ReplyComment", { Id: chaine(f.id), Text: chaine(t) })
    }
    function resoudre(f) { doc.commande(".uno:ResolveComment", { Id: chaine(f.id) }); if (!f.resolu) annonce("Résolu : le fil se range (« 1 résolu », en haut de la marge)") }
    function modifier(id, texte) { if (String(texte).trim()) doc.commande(".uno:EditAnnotation", { Id: chaine(id), Text: chaine(String(texte).trim()) }) }
    function supprimer(f) { doc.commande(".uno:DeleteCommentThread", { Id: chaine(f.id) }) }
    function supprimerReponse(id) { doc.commande(".uno:DeleteComment", { Id: chaine(id) }) }
    function accepter(g) {
        g.changements.forEach(function (c) { c.index.forEach(function (i) { doc.commande(".uno:AcceptTrackedChange", { AcceptTrackedChange: { type: "unsigned short", value: String(i) } }) }) })
        active = ""
    }
    function refuser(g) {
        g.changements.forEach(function (c) { c.index.forEach(function (i) { doc.commande(".uno:RejectTrackedChange", { RejectTrackedChange: { type: "unsigned short", value: String(i) } }) }) })
        active = ""
    }
    function toutAccepter() { doc.commande(".uno:AcceptAllTrackedChanges"); active = "" }
    function toutRefuser() { doc.commande(".uno:RejectAllTrackedChanges"); active = "" }

    // La carte de ce que le curseur touche ; sinon aucune (sauf si l'on écrit dans une carte)
    function carteAuCurseur() {
        if (!doc.curseurTexteVisible || yNouveau >= 0) return
        var c = doc.curseurTexte, cx = c.x + 1, cy = c.y + c.height / 2
        function touche(plage) {
            return doc.rectangles(plage).some(function (r) { return cx >= r.x - 2 && cx <= r.x + r.width + 2 && cy >= r.y && cy <= r.y + r.height })
        }
        for (var i = 0; i < filsVus.length; ++i) if (touche(filsVus[i].plage)) { active = filsVus[i].cle; return }
        for (var j = 0; j < groupes.length; ++j)
            for (var k = 0; k < groupes[j].changements.length; ++k) if (touche(groupes[j].changements[k].plage)) { active = groupes[j].cle; return }
        if (!cartes.enEcriture) active = ""
    }

    // ——— Les cartes, à la hauteur de leur texte ———
    // (positions en pixels du document : le contenu défile avec la page)
    readonly property real echelle: doc.zoom / 15
    readonly property var cartesModele: {
        var l = []
        filsVus.forEach(function (f) { l.push({ cle: f.cle, genre: "fil", fil: f, groupe: null, y: f.y * marge.echelle }) })
        groupes.forEach(function (g) { l.push({ cle: g.cle, genre: "suggestion", fil: null, groupe: g, y: g.y * marge.echelle }) })
        if (yNouveau >= 0) l.push({ cle: "nouveau", genre: "nouveau", fil: null, groupe: null, y: yNouveau })
        return l.sort(function (a, b) { return a.y - b.y })
    }
    clip: true

    // En-tête : le nombre ; Tout accepter, les résolus
    ColumnLayout {
        id: entete
        z: 2
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.topMargin: 14
        spacing: 0
        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 30
            spacing: 8
            Text { text: "Commentaires et suggestions"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte }
            Rectangle {
                visible: marge.nombre > 0
                implicitWidth: compte.implicitWidth + 14
                implicitHeight: 18
                radius: 9
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
                Text { id: compte; anchors.centerIn: parent; text: marge.nombre; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte }
            }
            Item { Layout.fillWidth: true }
        }
        RowLayout {
            visible: marge.groupes.length > 0 || marge.resolus > 0
            Layout.fillWidth: true
            spacing: 14
            LienMarge { visible: marge.groupes.length > 0; text: "Tout accepter"; fort: true; onClicked: marge.toutAccepter() }
            LienMarge { visible: marge.groupes.length > 0; text: "Tout refuser"; onClicked: marge.toutRefuser() }
            Item { Layout.fillWidth: true }
            LienMarge { visible: marge.resolus > 0; text: marge.montrerResolus ? "Cacher les résolus" : (marge.resolus > 1 ? marge.resolus + " résolus" : "1 résolu"); onClicked: marge.montrerResolus = !marge.montrerResolus }
        }
    }
    component LienMarge: QQC2.AbstractButton {
        id: lien
        property bool fort: false
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        implicitHeight: 24
        implicitWidth: texteLien.implicitWidth + 4
        contentItem: Text {
            id: texteLien
            text: lien.text
            font.pixelSize: 12
            font.weight: lien.fort ? Font.DemiBold : Font.Normal
            font.underline: lien.hovered
            color: lien.fort ? fenetre.accentEncre : Couleurs.texte2
        }
    }

    // (sous l'en-tête : les cartes y glissent avec la page)
    Item {
        id: zoneCartes
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: entete.bottom
        anchors.topMargin: 6
        anchors.bottom: parent.bottom
        clip: true
    Item {
        id: cartes
        anchors.left: parent.left
        anchors.right: parent.right
        y: -marge.doc.vueY - zoneCartes.y
        height: marge.doc.hauteurDocument + 400
        property bool enEcriture: false
        // (le placement : chacune à sa hauteur, sans se chevaucher ; la carte active à sa place exacte, les autres
        // poussées au-dessus et au-dessous)
        function placer() {
            var l = []
            for (var i = 0; i < repeteur.count; ++i) { var c = repeteur.itemAt(i); if (c) l.push(c) }
            if (!l.length) return
            l.sort(function (a, b) { return a.yVoulu - b.yVoulu })
            // (les places d'abord, puis on les donne : les cartes glissent jusqu'à elles)
            var ecart = 10, y = l.map(function (c) { return c.yVoulu })
            var ia = l.findIndex(function (c) { return c.cle === marge.active })
            var pivot = ia >= 0 ? ia : 0
            for (var j = pivot + 1; j < l.length; ++j) y[j] = Math.max(l[j].yVoulu, y[j - 1] + l[j - 1].height + ecart)
            for (var k = pivot - 1; k >= 0; --k) y[k] = Math.min(l[k].yVoulu, y[k + 1] - l[k].height - ecart)
            // (rien au-dessus du haut du document : on repousse vers le bas)
            if (y[0] < 0) {
                y[0] = 0
                for (var n = 1; n < l.length; ++n) y[n] = Math.max(y[n], y[n - 1] + l[n - 1].height + ecart)
            }
            for (var m = 0; m < l.length; ++m) l[m].y = y[m]
        }
        Timer { id: replacer; interval: 0; onTriggered: cartes.placer() }
        Repeater {
            id: repeteur
            model: marge.cartesModele
            onItemAdded: replacer.restart()
            onItemRemoved: replacer.restart()
            delegate: Carte { }
        }
    }
    }
    onActiveChanged: replacer.restart()
    onCartesModeleChanged: replacer.restart()

    // ——— Une carte ———
    component Carte: Rectangle {
        id: carte
        required property var modelData
        readonly property string cle: modelData.cle
        readonly property real yVoulu: modelData.y
        readonly property bool estActive: marge.active === cle
        readonly property var fil: modelData.fil
        readonly property var groupe: modelData.groupe
        readonly property string auteur: fil ? fil.auteur : groupe ? groupe.auteur : ""
        readonly property var couleurs: marge.teinte(auteur)
        x: estActive ? 0 : 8
        width: marge.width - 8
        height: colonne.implicitHeight + 24
        onHeightChanged: replacer.restart()
        Behavior on y { NumberAnimation { duration: 140; easing.type: Easing.OutCubic } }
        Behavior on x { NumberAnimation { duration: 140 } }
        radius: 12
        color: Couleurs.sombre ? "#2B2722" : "#FFFFFF"
        border.width: 1
        border.color: estActive ? Qt.rgba(fenetre.accent.r, fenetre.accent.g, fenetre.accent.b, 0.45) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
        opacity: fil && fil.resolu ? 0.7 : 1
        // (un clic sur la carte : c'est elle qu'on lit, et la page montre son texte)
        MouseArea {
            anchors.fill: parent
            onClicked: {
                marge.active = carte.cle
                var y = carte.yVoulu
                if (y < marge.doc.vueY + 40 || y > marge.doc.vueY + marge.height - 80) marge.doc.vueY = Math.max(0, y - marge.height / 3)
            }
        }
        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 12
            anchors.leftMargin: 14
            anchors.rightMargin: 12
            spacing: 8

            // ——— Qui, quand ———
            RowLayout {
                visible: carte.modelData.genre !== "nouveau"
                Layout.fillWidth: true
                spacing: 8
                Rectangle {
                    Layout.preferredWidth: 26
                    Layout.preferredHeight: 26
                    radius: 13
                    color: carte.couleurs[0]
                    Text { anchors.centerIn: parent; text: marge.initiales(carte.auteur); font.pixelSize: 10; font.weight: Font.Bold; color: carte.couleurs[1] }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 0
                    Text {
                        Layout.fillWidth: true
                        text: carte.auteur + (carte.groupe ? " propose" : "")
                        font.pixelSize: 13
                        font.weight: Font.DemiBold
                        color: Couleurs.texte
                        elide: Text.ElideRight
                    }
                    Text {
                        Layout.fillWidth: true
                        text: carte.fil ? marge.quand(carte.fil.date) + (carte.fil.resolu ? " · résolu" : "")
                              : carte.groupe ? marge.quand(carte.groupe.date) + (carte.groupe.changements.length > 1 ? " · " + carte.groupe.changements.length + " changements" : "") : ""
                        font.pixelSize: 11
                        color: Couleurs.texte2
                        elide: Text.ElideRight
                    }
                }
                // Résoudre (ou rouvrir), et le reste
                Outil {
                    visible: !!carte.fil
                    Layout.preferredWidth: 28
                    Layout.preferredHeight: 28
                    picto: carte.fil && carte.fil.resolu ? "M4 12a8 8 0 1 0 2.3-5.6 M4 4v4h4" : "M5 12.5l4.5 4.5L19 7.5"
                    encre: fenetre.accent
                    aide: carte.fil && carte.fil.resolu ? "Rouvrir" : "Résoudre"
                    onClicked: marge.resoudre(carte.fil)
                }
                Outil {
                    visible: !!carte.fil
                    Layout.preferredWidth: 28
                    Layout.preferredHeight: 28
                    picto: "M6 12h.01 M12 12h.01 M18 12h.01"
                    aide: "Plus"
                    onClicked: menuFil.popup()
                    MenuSama {
                        id: menuFil
                        ElementMenu { text: "Modifier"; picto: "M4 20h4l10-10-4-4L4 16z M14 6l4 4"; onTriggered: { edition.text = carte.fil.texte; carte.enEdition = true; edition.forceActiveFocus() } }
                        ElementMenu { text: "Supprimer le fil"; picto: "M5 7h14 M10 7V5h4v2 M7 7l1 13h8l1-13"; danger: true; onTriggered: marge.supprimer(carte.fil) }
                    }
                }
            }

            // ——— Un commentaire : son texte, ses réponses, Répondre ———
            Text {
                visible: !!carte.fil && !carte.enEdition
                Layout.fillWidth: true
                text: carte.fil ? carte.fil.texte : ""
                font.pixelSize: 13
                lineHeight: 18
                lineHeightMode: Text.FixedHeight
                color: Couleurs.texte
                wrapMode: Text.Wrap
            }
            ChampCarte {
                id: edition
                visible: carte.enEdition
                Layout.fillWidth: true
                indice: "Le commentaire"
                onValide: t => { marge.modifier(carte.fil.id, t); carte.enEdition = false }
                onAbandon: carte.enEdition = false
            }
            Repeater {
                model: carte.fil ? carte.fil.reponses : []
                delegate: ColumnLayout {
                    id: reponse
                    required property var modelData
                    Layout.fillWidth: true
                    Layout.topMargin: 2
                    spacing: 2
                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 6
                        Rectangle {
                            Layout.preferredWidth: 20
                            Layout.preferredHeight: 20
                            radius: 10
                            color: marge.teinte(reponse.modelData.auteur)[0]
                            Text { anchors.centerIn: parent; text: marge.initiales(reponse.modelData.auteur); font.pixelSize: 8; font.weight: Font.Bold; color: marge.teinte(reponse.modelData.auteur)[1] }
                        }
                        Text { text: reponse.modelData.auteur; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideRight; Layout.maximumWidth: 150 }
                        Text { Layout.fillWidth: true; text: marge.quand(reponse.modelData.date); font.pixelSize: 11; color: Couleurs.texte2; elide: Text.ElideRight }
                    }
                    Text {
                        Layout.fillWidth: true
                        Layout.leftMargin: 26
                        text: reponse.modelData.texte
                        font.pixelSize: 13
                        lineHeight: 18
                        lineHeightMode: Text.FixedHeight
                        color: Couleurs.texte
                        wrapMode: Text.Wrap
                    }
                }
            }
            ChampCarte {
                visible: !!carte.fil && !carte.enEdition && !carte.fil.resolu
                Layout.fillWidth: true
                indice: "Répondre…"
                compact: !activeFocus && !text
                onActiveFocusChanged: if (activeFocus) marge.active = carte.cle
                onValide: t => { marge.repondre(carte.fil, t); text = "" }
                onAbandon: { text = ""; marge.doc.forceActiveFocus() }
            }

            // ——— Une suggestion : ses changements, Accepter, Refuser ———
            Text {
                visible: !!carte.groupe
                Layout.fillWidth: true
                text: carte.groupe ? carte.groupe.changements.map(marge.phrase).join("<br>") : ""
                textFormat: Text.StyledText
                font.pixelSize: 13
                lineHeight: 19
                lineHeightMode: Text.FixedHeight
                color: Couleurs.texte
                wrapMode: Text.Wrap
            }
            RowLayout {
                visible: !!carte.groupe
                spacing: 8
                BoutonCarte { text: "Accepter"; plein: true; picto: "M5 12.5l4.5 4.5L19 7.5"; onClicked: marge.accepter(carte.groupe) }
                BoutonCarte { text: "Refuser"; onClicked: marge.refuser(carte.groupe) }
            }

            // ——— Un nouveau commentaire ———
            RowLayout {
                visible: carte.modelData.genre === "nouveau"
                Layout.fillWidth: true
                spacing: 8
                Rectangle {
                    Layout.preferredWidth: 26
                    Layout.preferredHeight: 26
                    radius: 13
                    color: marge.teinte(marge.moi)[0]
                    Text { anchors.centerIn: parent; text: marge.initiales(marge.moi); font.pixelSize: 10; font.weight: Font.Bold; color: marge.teinte(marge.moi)[1] }
                }
                Text { Layout.fillWidth: true; text: marge.moi; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideRight }
            }
            ChampCarte {
                id: nouveauTexte
                visible: carte.modelData.genre === "nouveau"
                Layout.fillWidth: true
                indice: "Une question, une remarque…"
                onValide: t => marge.publier(t)
                onAbandon: marge.publier("")
                Component.onCompleted: if (carte.modelData.genre === "nouveau") forceActiveFocus()
                onActiveFocusChanged: cartes.enEcriture = activeFocus
            }
            RowLayout {
                visible: carte.modelData.genre === "nouveau"
                spacing: 8
                Item { Layout.fillWidth: true }
                BoutonCarte { text: "Annuler"; onClicked: marge.publier("") }
                BoutonCarte { text: "Commenter"; plein: true; onClicked: marge.publier(nouveauTexte.text) }
            }
        }
        property bool enEdition: false
    }
    // (le nom de la personne : celui que le moteur met sur ses commentaires)
    property string moi: ""
    Commande { id: commande }
    Component.onCompleted: commande.lancer("getent passwd \"$(id -un)\"", function (sortie) {
        var p = sortie.trim().split(":")
        marge.moi = (p[4] || "").split(",")[0].trim() || p[0] || "Moi"
    })

    // Un champ de carte : Entrée publie, Maj+Entrée va à la ligne, Échap abandonne
    component ChampCarte: QQC2.TextArea {
        id: champ
        property string indice: ""
        property bool compact: false
        signal valide(string texte)
        signal abandon()
        placeholderText: indice
        placeholderTextColor: Couleurs.texte3
        font.pixelSize: 13
        color: Couleurs.texte
        wrapMode: TextEdit.Wrap
        leftPadding: 10
        rightPadding: 10
        topPadding: compact ? 7 : 8
        bottomPadding: compact ? 7 : 8
        background: Rectangle {
            radius: 8
            color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.04) : "#FFFFFF"
            border.width: champ.activeFocus ? 1.5 : 1
            border.color: champ.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
        }
        Keys.onPressed: (e) => {
            if ((e.key === Qt.Key_Return || e.key === Qt.Key_Enter) && !(e.modifiers & Qt.ShiftModifier)) { e.accepted = true; champ.valide(champ.text) }
            else if (e.key === Qt.Key_Escape) { e.accepted = true; champ.abandon() }
        }
    }
    component BoutonCarte: QQC2.AbstractButton {
        id: bouton
        property bool plein: false
        property string picto: ""
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        implicitHeight: 30
        implicitWidth: rangee.implicitWidth + 24
        background: Rectangle {
            radius: 8
            color: bouton.plein ? (bouton.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent)
                                : (bouton.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06) : (Couleurs.sombre ? "transparent" : "#FFFFFF"))
            border.width: bouton.plein ? 0 : 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
        }
        contentItem: Item {
            implicitWidth: rangee.implicitWidth
            Row {
                id: rangee
                anchors.centerIn: parent
                spacing: 5
                Picto { visible: bouton.picto !== ""; anchors.verticalCenter: parent.verticalCenter; width: 14; height: 14; trace: bouton.picto; encre: bouton.plein ? "#FFFFFF" : Couleurs.texte; trait: 2.4 }
                Text { anchors.verticalCenter: parent.verticalCenter; text: bouton.text; font.pixelSize: 13; font.weight: bouton.plein ? Font.DemiBold : Font.Normal; color: bouton.plein ? "#FFFFFF" : Couleurs.texte }
            }
        }
    }
}
