// L'habillage d'un tableau dans la grille (maquette « Sheet · Un tableau dans la grille ») : le moteur dessine les
// cases, Sama pose par-dessus ce que le moteur ne sait pas dessiner. L'onglet du tableau (son nom, Grille ou Fiches)
// sur la ligne libre au-dessus ; les titres avec l'icône du genre de leur colonne et leur ▾ ; une ligne sur deux
// teintée ; les initiales devant les noms ; le reste à payer en pastille ocre, ou « Soldé » ; les valeurs d'une
// colonne à choix en étiquettes ; « Total · 12 membres ». Rien de tout cela n'entre dans le fichier.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: habillage
    required property var t                  // le tableau (SamaTableaux.Decrire)
    required property var vueGrille
    required property var moteur
    readonly property real echelle: moteur.zoom
    readonly property var tableaux: fenetre.tableaux
    anchors.fill: parent

    // ——— Couleurs (maquette) ———
    readonly property color fondTitre: Couleurs.sombre ? "#2B3A42" : "#E4ECEF"
    readonly property color fondTitreActif: Couleurs.sombre ? "#33505C" : "#D2DFE4"
    readonly property color bande: Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.045)
    readonly property color separation: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07)

    // ——— Données : les lignes du tableau, telles qu'affichées ———
    property var donnees: ({ colonnes: [], lignes: [] })
    property var parLigne: ({})
    // Case écrite à l'instant : on laisse voir le moteur jusqu'à la prochaine lecture (pas d'ancienne valeur affichée)
    property var ecrite: null
    function lire() {
        if (!t.nom) return
        tableaux.appeler("Lignes", [t.nom], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{") return
            try { donnees = JSON.parse(v) } catch (e) { return }
            var m = {}
            for (var i = 0; i < donnees.lignes.length; i++) m[donnees.lignes[i].l] = donnees.lignes[i]
            parLigne = m
            ecrite = null
        })
    }
    Timer { id: relire; interval: 200; onTriggered: habillage.lire() }
    Component.onCompleted: lire()
    // (relu quand le tableau change de nom ou de place ; les changements du contenu passent par la révision)
    readonly property string signature: t.nom + ":" + t.c1 + ":" + t.l1 + ":" + t.c2 + ":" + t.l2
    onSignatureChanged: relire.restart()
    Connections {
        target: habillage.moteur
        function onRevisionChanged() { relire.restart() }
        function onCurseurTexteVisibleChanged() {
            // (fin d'une saisie : la case qu'on écrivait)
            if (!habillage.moteur.curseurTexteVisible && habillage.saisie) habillage.ecrite = habillage.saisie
            habillage.saisie = habillage.moteur.curseurTexteVisible ? habillage.caseCourante() : null
        }
    }
    property var saisie: null
    function caseCourante() { var p = vueGrille.plage; return p.c1 > 0 && p.l1 > 0 ? { c: p.c1 - 1, l: p.l1 - 1 } : null }
    function cachee(c, l) {
        if (ecrite && ecrite.c === c && ecrite.l === l) return true
        return saisie !== null && saisie.c === c && saisie.l === l
    }

    // ——— Le genre de chaque colonne : nom de personne, montant, calcul, date, choix, téléphone… ———
    readonly property var progression: {
        var cols = donnees.colonnes
        for (var i = 0; i < cols.length; i++) {
            if (!cols[i].calcul) continue
            var m = /\[\[#[^\]]*\];\[([^\]]+)\]\]\s*-\s*[^\[;]+\[\[#[^\]]*\];\[([^\]]+)\]\]/.exec(cols[i].formule)
            if (!m) continue
            var du = -1, verse = -1
            for (var j = 0; j < cols.length; j++) { if (cols[j].nom === m[1]) du = j; if (cols[j].nom === m[2]) verse = j }
            if (du >= 0 && verse >= 0) return { reste: i, du: du, verse: verse }
        }
        return null
    }
    readonly property var genres: {
        var cols = donnees.colonnes, res = []
        for (var i = 0; i < cols.length; i++) {
            var c = cols[i], g = c.genre || ""
            if (progression && progression.reste === i) g = "reste"
            else if (c.calcul) g = g === "montant" || g === "date" ? g : "calcul"
            else if (/t[ée]l[ée]?phone|^t[ée]l\b|portable|whatsapp/i.test(c.nom)) g = "telephone"
            else if (i === 0 && !c.nombre && /membre|nom|[ée]l[èe]ve|client|personne|pr[ée]nom|agent|vendeu|employ|contact|parent|enfant|participant|cotisant|ami/i.test(c.nom)) g = "personne"
            else if (!c.nombre && g !== "date" && estChoix(i)) g = "choix"
            else if (!g) g = c.nombre ? "nombre" : "texte"
            res.push(g)
        }
        return res
    }
    // Une colonne de texte à choix : peu de valeurs différentes, et courtes (Espèces, Mobile money…)
    function estChoix(i) {
        var vus = {}, n = 0, d = 0
        for (var k = 0; k < donnees.lignes.length; k++) {
            var v = donnees.lignes[k].v[i]
            if (!v) continue
            if (v.length > 24) return false
            n++
            if (!vus[v]) { vus[v] = true; d++ }
        }
        return n >= 2 && d <= Math.min(8, Math.max(2, Math.ceil(n / 2)))
    }
    readonly property var pictos: ({
        personne: "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M5 20c1-4 4-6 7-6s6 2 7 6",
        montant: "M3 7h18v10H3z M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z",
        reste: "M6 10h12 M6 14h12",
        calcul: "M6 10h12 M6 14h12",
        date: "M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4",
        heure: "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 8v4l3 2",
        choix: "M9 7h11 M9 12h11 M9 17h11 M4.5 7v.1 M4.5 12v.1 M4.5 17v.1",
        telephone: "M7 3.5h3l1.5 4-2 1.3a10 10 0 0 0 4.7 4.7l1.3-2 4 1.5v3a2 2 0 0 1-2 2A16 16 0 0 1 5 5.5a2 2 0 0 1 2-2z",
        nombre: "M10 4L8 20 M16 4l-2 16 M5 9h15 M4 15h15",
        pourcent: "M19 5L5 19 M7 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z M17 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z",
        texte: "M5 7V5h14v2 M12 5v14 M9 19h6"
    })
    function initiales(nom) {
        var mots = String(nom || "").trim().split(/\s+/).filter(function (m) { return m.length > 0 })
        if (!mots.length) return ""
        return (mots[0].charAt(0) + (mots.length > 1 ? mots[1].charAt(0) : "")).toUpperCase()
    }
    function pluriel(nom) {
        var n = String(nom || "ligne").toLowerCase()
        return /[sxz]$/.test(n) ? n : n + "s"
    }

    // ——— Où sont les colonnes et les lignes du tableau (seulement celles qu'on voit) ———
    function bandeColonne(c) { return vueGrille.parLettres[tableaux.lettres(c)] || null }
    readonly property int derniere: t.totaux ? t.l2 - 1 : t.l2
    readonly property var lignesVues: {
        var res = [], b = vueGrille.lignes
        for (var k = 0; k < b.length; k++) {
            var l = Number(b[k].texte) - 1
            if (l > t.l1 && l <= derniere && b[k].fin > b[k].debut) res.push({ l: l, y: b[k].debut, h: b[k].fin - b[k].debut, rang: l - t.l1 - 1 })
        }
        return res
    }
    readonly property var colonnesVues: {
        var res = []
        for (var c = t.c1; c <= t.c2; c++) {
            var b = bandeColonne(c)
            if (b && b.fin > b.debut) res.push({ c: c, i: c - t.c1, x: b.debut, w: b.fin - b.debut })
        }
        return res
    }
    readonly property var bandeTitre: vueGrille.parNumero[String(t.l1 + 1)] || null
    readonly property var bandeTotaux: t.totaux ? vueGrille.parNumero[String(t.l2 + 1)] || null : null
    readonly property var bandeOnglet: t.libre && t.l1 > 0 ? vueGrille.parNumero[String(t.l1)] || null : null
    readonly property real gauche: colonnesVues.length ? colonnesVues[0].x : 0
    readonly property real droite: colonnesVues.length ? colonnesVues[colonnesVues.length - 1].x + colonnesVues[colonnesVues.length - 1].w : 0
    readonly property var colonnesDecorees: colonnesVues.filter(function (k) {
        var g = habillage.genres[k.i]
        return g === "personne" || g === "reste" || g === "choix"
    })

    // ——— Les lignes : cases décorées, puis une ligne sur deux teintée ———
    Repeater {
        model: habillage.lignesVues
        delegate: Item {
            id: ligne
            readonly property var vue: modelData
            readonly property var r: habillage.parLigne[vue.l] || null
            x: 0
            y: vue.y - habillage.moteur.vueY
            width: habillage.width
            height: vue.h
            Repeater {
                model: ligne.r ? habillage.colonnesDecorees : []
                delegate: Rectangle {
                    id: decoree
                    readonly property string genre: habillage.genres[modelData.i]
                    readonly property string valeur: ligne.r.v[modelData.i] || ""
                    readonly property var nombre: ligne.r.n[modelData.i]
                    readonly property bool doit: genre === "reste" && nombre !== null && nombre > 0
                    readonly property bool solde: genre === "reste" && nombre !== null && nombre <= 0 && habillage.progression !== null
                                                  && ligne.r.n[habillage.progression.du] > 0
                    x: modelData.x - habillage.moteur.vueX + 0.5
                    width: modelData.w - 1
                    height: ligne.height - 1
                    visible: (valeur !== "" || solde) && !habillage.cachee(modelData.c, ligne.vue.l)
                    color: Couleurs.sombre ? "#1E2230" : "#FFFFFF"
                    clip: true
                    // Initiales et nom
                    Row {
                        // (les initiales, s'il y a la place : colonne assez large, ligne assez haute)
                        readonly property bool large: decoree.width >= 120 * habillage.echelle && decoree.height >= 22 * habillage.echelle
                        visible: decoree.genre === "personne"
                        anchors.verticalCenter: parent.verticalCenter
                        x: (large ? 9 : 3) * habillage.echelle
                        spacing: 8 * habillage.echelle
                        Rectangle {
                            visible: parent.large
                            readonly property real cote: Math.min(20 * habillage.echelle, decoree.height - 4)
                            width: cote; height: cote; radius: cote / 2
                            anchors.verticalCenter: parent.verticalCenter
                            color: Couleurs.sombre ? "#3A3F52" : "#F0E7DB"
                            Text { anchors.centerIn: parent; text: habillage.initiales(decoree.valeur); font.pixelSize: Math.max(6, Math.round(9 * habillage.echelle)); font.weight: Font.Bold; color: Couleurs.texte2 }
                        }
                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            text: decoree.valeur
                            font.pointSize: 10 * habillage.echelle
                            color: Couleurs.texte
                        }
                    }
                    // Reste à payer : pastille ocre ; rien à payer : Soldé
                    Rectangle {
                        visible: decoree.doit
                        anchors.right: parent.right
                        anchors.rightMargin: 6 * habillage.echelle
                        anchors.verticalCenter: parent.verticalCenter
                        width: texteReste.implicitWidth + 14 * habillage.echelle
                        height: Math.min(20 * habillage.echelle, decoree.height - 4)
                        radius: 5 * habillage.echelle
                        color: Qt.rgba(226 / 255, 166 / 255, 43 / 255, 0.3)
                        Text { id: texteReste; anchors.centerIn: parent; text: decoree.valeur; font.pointSize: 9 * habillage.echelle; font.weight: Font.DemiBold; font.features: { "tnum": 1 }; color: Couleurs.sombre ? "#F2D27A" : "#1E2740" }
                    }
                    Row {
                        visible: decoree.solde
                        anchors.right: parent.right
                        anchors.rightMargin: 9 * habillage.echelle
                        anchors.verticalCenter: parent.verticalCenter
                        spacing: 3 * habillage.echelle
                        Picto { width: 13 * habillage.echelle; height: width; anchors.verticalCenter: parent.verticalCenter; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: fenetre.vertEncre; trait: 2.4 }
                        Text { text: "Soldé"; font.pointSize: 9 * habillage.echelle; font.weight: Font.DemiBold; color: fenetre.vertEncre }
                    }
                    // Valeur d'une colonne à choix : une étiquette
                    Rectangle {
                        visible: decoree.genre === "choix"
                        x: 6 * habillage.echelle
                        anchors.verticalCenter: parent.verticalCenter
                        width: Math.min(parent.width - 10 * habillage.echelle, texteChoix.implicitWidth + 14 * habillage.echelle)
                        height: Math.min(20 * habillage.echelle, decoree.height - 4)
                        radius: 5 * habillage.echelle
                        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.25 : 0.06)
                        Text { id: texteChoix; anchors.centerIn: parent; width: Math.min(implicitWidth, parent.width - 10); text: decoree.valeur; elide: Text.ElideRight; font.pointSize: 9 * habillage.echelle; color: Couleurs.texte }
                    }
                    Rectangle { anchors.right: parent.right; anchors.rightMargin: -0.5; width: 1; height: parent.height + 1; color: habillage.separation }
                }
            }
            // Une ligne sur deux
            Rectangle {
                visible: ligne.vue.rang % 2 === 1
                x: habillage.gauche - habillage.moteur.vueX
                width: habillage.droite - habillage.gauche
                height: parent.height
                color: habillage.bande
            }
        }
    }

    // ——— Les titres : icône du genre, nom, ▾ ———
    Item {
        visible: habillage.bandeTitre !== null
        y: habillage.bandeTitre ? habillage.bandeTitre.debut - habillage.moteur.vueY : 0
        height: habillage.bandeTitre ? habillage.bandeTitre.fin - habillage.bandeTitre.debut : 0
        width: habillage.width
        Repeater {
            model: habillage.colonnesVues
            delegate: Rectangle {
                id: titre
                readonly property bool active: habillage.vueGrille.colonneChoisie(habillage.tableaux.lettres(modelData.c))
                readonly property bool filtree: habillage.t.filtres.indexOf(modelData.i) >= 0
                readonly property string nom: habillage.t.colonnes[modelData.i] || ""
                x: modelData.x - habillage.moteur.vueX
                width: modelData.w
                height: parent.height
                visible: !habillage.cachee(modelData.c, habillage.t.l1)
                color: active ? habillage.fondTitreActif : habillage.fondTitre
                clip: true
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: (titre.width >= 100 * habillage.echelle ? 9 : 3) * habillage.echelle
                    anchors.rightMargin: (titre.width >= 100 * habillage.echelle ? 5 : 2) * habillage.echelle
                    spacing: (titre.width >= 100 * habillage.echelle ? 6 : 2) * habillage.echelle
                    // (dans une colonne étroite, le nom passe avant l'icône)
                    Picto {
                        visible: trace !== "" && titre.width >= 100 * habillage.echelle
                        Layout.preferredWidth: 13 * habillage.echelle
                        Layout.preferredHeight: 13 * habillage.echelle
                        trace: habillage.pictos[habillage.genres[modelData.i] || ""] || ""
                        encre: fenetre.accentEncre
                        trait: 2
                        opacity: 0.75
                    }
                    Text {
                        Layout.fillWidth: true
                        text: titre.nom
                        font.pointSize: 10 * habillage.echelle
                        font.weight: Font.Bold
                        color: fenetre.accentEncre
                        elide: Text.ElideRight
                    }
                    // Trier et filtrer
                    QQC2.AbstractButton {
                        id: bouton
                        readonly property real cote: Math.min(22 * habillage.echelle, titre.height - 4)
                        Layout.preferredWidth: cote
                        Layout.preferredHeight: cote
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        Accessible.name: "Trier et filtrer " + titre.nom
                        onClicked: habillage.vueGrille.ouvrirFiltre(habillage.t, modelData.i, bouton)
                        QQC2.ToolTip.visible: hovered
                        QQC2.ToolTip.delay: 600
                        QQC2.ToolTip.text: titre.filtree ? "Filtrée : cliquez pour changer" : "Trier et filtrer"
                        background: Rectangle {
                            radius: 5 * habillage.echelle
                            color: titre.filtree ? fenetre.accent
                                 : bouton.down ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                                 : bouton.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.11) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06)
                        }
                        contentItem: Item {
                            Picto {
                                anchors.centerIn: parent
                                width: 11 * habillage.echelle
                                height: width
                                trace: titre.filtree ? "M5 6h14l-5.5 6.5V18l-3-1.5v-4z" : "M6 9l6 6 6-6"
                                encre: titre.filtree ? "#FFFFFF" : Couleurs.texte2
                                trait: 2.4
                            }
                        }
                    }
                }
                Rectangle { anchors.right: parent.right; width: 1; height: parent.height; color: habillage.separation }
            }
        }
        Rectangle {
            x: habillage.gauche - habillage.moteur.vueX
            width: habillage.droite - habillage.gauche
            anchors.bottom: parent.bottom
            height: 1.5
            color: fenetre.accent
        }
    }

    // ——— Ligne des totaux : « Total · 12 membres » ———
    Rectangle {
        readonly property var k: habillage.colonnesVues.length && habillage.colonnesVues[0].i === 0 ? habillage.colonnesVues[0] : null
        visible: habillage.bandeTotaux !== null && k !== null && !habillage.cachee(habillage.t.c1, habillage.t.l2)
        x: k ? k.x - habillage.moteur.vueX : 0
        y: habillage.bandeTotaux ? habillage.bandeTotaux.debut - habillage.moteur.vueY : 0
        width: k ? k.w - 0.5 : 0
        height: habillage.bandeTotaux ? habillage.bandeTotaux.fin - habillage.bandeTotaux.debut : 0
        color: habillage.fondTitre
        clip: true
        Rectangle { width: parent.width; height: 1.5; color: fenetre.accent }
        Row {
            x: 9 * habillage.echelle
            anchors.verticalCenter: parent.verticalCenter
            spacing: 6 * habillage.echelle
            Text { text: "Total"; font.pointSize: 10 * habillage.echelle; font.weight: Font.Bold; color: fenetre.accentEncre; anchors.baseline: compte.baseline }
            Text {
                id: compte
                readonly property int n: habillage.donnees.lignes.filter(function (r) { return !r.vide }).length
                text: "· " + n + " " + (n > 1 ? habillage.pluriel(habillage.t.colonnes[0]) : String(habillage.t.colonnes[0] || "ligne").toLowerCase())
                font.pointSize: 9 * habillage.echelle
                color: fenetre.accentEncre
            }
        }
    }

    // ——— L'onglet du tableau, sur la ligne libre au-dessus : son nom (▾ : ce qu'on peut faire), Grille ou Fiches ———
    Item {
        id: onglet
        visible: habillage.bandeOnglet !== null && habillage.colonnesVues.length > 0 && habillage.colonnesVues[0].i === 0
        x: habillage.gauche - habillage.moteur.vueX
        y: habillage.bandeOnglet ? habillage.bandeOnglet.debut - habillage.moteur.vueY : 0
        height: habillage.bandeOnglet ? habillage.bandeOnglet.fin - habillage.bandeOnglet.debut : 0
        width: rangeeOnglet.implicitWidth
        Row {
            id: rangeeOnglet
            anchors.bottom: parent.bottom
            spacing: 12 * habillage.echelle
            QQC2.AbstractButton {
                id: nomTableau
                height: Math.min(26 * habillage.echelle, onglet.height)
                width: contenuNom.implicitWidth + 20 * habillage.echelle
                anchors.bottom: parent.bottom
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                Accessible.name: "Tableau " + habillage.t.nom
                onClicked: menuTableau.popup(nomTableau, 0, nomTableau.height)
                background: Item {
                    Rectangle { anchors.fill: parent; radius: 7 * habillage.echelle; color: nomTableau.hovered ? Qt.darker(fenetre.accent, 1.12) : fenetre.accent }
                    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 8; color: nomTableau.hovered ? Qt.darker(fenetre.accent, 1.12) : fenetre.accent }
                }
                contentItem: Item {
                    Row {
                        id: contenuNom
                        anchors.centerIn: parent
                        spacing: 6 * habillage.echelle
                        Picto { width: 14 * habillage.echelle; height: width; anchors.verticalCenter: parent.verticalCenter; trace: "M4 5h16v14H4z M4 10h16 M10 5v14"; encre: "#FFFFFF"; trait: 2 }
                        Text { anchors.verticalCenter: parent.verticalCenter; text: habillage.t.nom.replace(/_/g, " "); font.pixelSize: Math.round(13 * habillage.echelle); font.weight: Font.DemiBold; color: "#FFFFFF" }
                        Text { anchors.verticalCenter: parent.verticalCenter; text: "▾"; font.pixelSize: Math.round(9 * habillage.echelle); color: "#FFFFFF" }
                    }
                }
            }
            // Grille | Fiches
            Rectangle {
                anchors.bottom: parent.bottom
                anchors.bottomMargin: 2 * habillage.echelle
                height: Math.min(28 * habillage.echelle, onglet.height - 2)
                width: vues.implicitWidth + 4 * habillage.echelle
                radius: 9 * habillage.echelle
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.055)
                Row {
                    id: vues
                    anchors.centerIn: parent
                    spacing: 2
                    Repeater {
                        model: [["grille", "Grille"], ["fiches", "Fiches"]]
                        delegate: QQC2.AbstractButton {
                            id: vue
                            readonly property bool choisie: modelData[0] === "grille"
                            height: Math.min(24 * habillage.echelle, onglet.height - 6)
                            width: texteVue.implicitWidth + 20 * habillage.echelle
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: if (!choisie) fenetre.voirFiches(habillage.t)
                            background: Rectangle {
                                radius: 7 * habillage.echelle
                                color: vue.choisie ? Couleurs.champ : vue.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06) : "transparent"
                                border.width: vue.choisie ? 0.5 : 0
                                border.color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.16)
                            }
                            contentItem: Text {
                                id: texteVue
                                text: modelData[1]
                                horizontalAlignment: Text.AlignHCenter
                                verticalAlignment: Text.AlignVCenter
                                font.pixelSize: Math.round(12 * habillage.echelle)
                                font.weight: vue.choisie ? Font.DemiBold : Font.Normal
                                color: vue.choisie ? fenetre.accentEncre : Couleurs.texte2
                            }
                        }
                    }
                }
            }
        }
        MenuSama {
            id: menuTableau
            ElementMenu { text: "Renommer le tableau…"; onTriggered: renommage.ouvrir() }
            ElementMenu { cle: "fiches" }
            ElementMenu { cle: "ligneTableau" }
            ElementMenu { cle: "totauxTableau" }
        }
        // Renommer : un petit champ sous l'onglet
        QQC2.Popup {
            id: renommage
            y: onglet.height + 4
            padding: 10
            function ouvrir() { champNom.text = habillage.t.nom; open(); champNom.forceActiveFocus(); champNom.selectAll() }
            background: Rectangle { radius: 12; color: Couleurs.champ; border.width: 0.5; border.color: Couleurs.bord }
            contentItem: ColumnLayout {
                spacing: 6
                Text { text: "Nom du tableau (les formules s'en servent)"; font.pixelSize: 12; color: Couleurs.texte2 }
                QQC2.TextField {
                    id: champNom
                    Layout.preferredWidth: 240
                    font.pixelSize: 14
                    onAccepted: {
                        if (text.trim() && text !== habillage.t.nom) habillage.tableaux.renommer(habillage.t, text)
                        renommage.close()
                        habillage.moteur.forceActiveFocus()
                    }
                }
            }
        }
    }
}
