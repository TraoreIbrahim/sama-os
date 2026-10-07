// L'Analyse rapide (maquette « Sheet · La grille libre », état analyse ; comme celle d'Excel) : un petit bouton au
// coin d'une sélection de plusieurs cases (ou Ctrl+Q) ouvre une carte. Mise en forme (barres, couleurs, plus grands,
// au-dessus de la moyenne, effacer), graphiques, totaux sous les colonnes, tableau. Survoler une tuile en montre
// l'aperçu dans les cases mêmes ; un clic l'applique, et Ctrl+Z l'annule.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: analyse
    required property var moteur
    required property var vueGrille
    readonly property var actions: fenetre.actions
    readonly property var tableaux: fenetre.tableaux
    anchors.fill: parent
    readonly property bool carteOuverte: carte.opened

    // ——— La sélection : plusieurs cases (sinon pas de bouton) ———
    readonly property rect zone: moteur.zoneSelection
    readonly property bool plusieurs: zone.width > moteur.curseur.width + 1 || zone.height > moteur.curseur.height + 1
    readonly property string adresse: String(moteur.adresse || "").replace(/\$/g, "")
    readonly property bool possible: plusieurs && zone.width > 0 && !moteur.curseurTexteVisible && moteur.objet.width <= 0
    function ouvrir() {
        if (!possible) { message.montrer("Choisissez d'abord plusieurs cases"); return }
        onglet = "forme"
        apercu = ""
        donnees = null
        carte.open()
        tableaux.appeler("SamaAnalyse.Valeurs", [adresse], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{") return
            try { donnees = JSON.parse(v) } catch (e) { }
        })
    }
    onAdresseChanged: if (carte.opened) carte.close()

    // ——— Les nombres choisis, et ce qu'on en tire ———
    property var donnees: null
    readonly property var stats: {
        var d = donnees, res = { n: 0, min: 0, max: 0, moy: 0, seuil: Infinity, k: 1, colonnes: [] }
        if (!d) return res
        var tous = [], somme = 0
        for (var i = 0; i < d.v.length; i++) for (var j = 0; j < d.v[i].length; j++) {
            var x = d.v[i][j]
            if (x === null) continue
            tous.push(x); somme += x
        }
        res.n = tous.length
        if (!tous.length) return res
        tous.sort(function (a, b) { return b - a })
        res.max = tous[0]; res.min = tous[tous.length - 1]; res.moy = somme / tous.length
        // (plus grands : un quart des valeurs, au moins une, au plus dix)
        res.k = Math.max(1, Math.min(10, Math.round(tous.length / 4)))
        res.seuil = tous[res.k - 1]
        // (totaux de chaque colonne)
        for (var c = 0; c < d.v[0].length; c++) {
            var col = [], s = 0
            for (var l = 0; l < d.v.length; l++) if (d.v[l][c] !== null) { col.push(d.v[l][c]); s += d.v[l][c] }
            res.colonnes.push(col.length ? { somme: s, moyenne: s / col.length, nombre: col.length, min: Math.min.apply(null, col), max: Math.max.apply(null, col) } : null)
        }
        return res
    }
    function enChiffres(x) {
        var entier = Math.abs(x - Math.round(x)) < 1e-9
        return Number(x).toLocaleString(Qt.locale("fr_FR"), "f", entier ? 0 : 2)
    }
    // Couleur d'une case pour l'échelle (latérite claire → or pâle → vert tendre)
    function melange(a, b, t) { return Qt.rgba(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, 1) }
    readonly property color bas: "#F0B392"
    readonly property color milieu: "#FCEFC7"
    readonly property color haut: "#A3D6C1"
    function echelle(x) {
        var s = stats
        if (s.max === s.min) return milieu
        var t = (x - s.min) / (s.max - s.min)
        return t < 0.5 ? melange(bas, milieu, t * 2) : melange(milieu, haut, (t - 0.5) * 2)
    }

    // ——— Appliquer ———
    property var enAttente: null
    function appliquer(cle) {
        var a = adresse
        carte.close()
        apercu = ""
        if (cle === "barres" || cle === "couleurs" || cle === "grands" || cle === "moyenne") {
            enAttente = { genre: cle, adresse: a, nombre: String(stats.k) }
            if (cle === "barres") moteur.commandeValidee(".uno:DataBarFormatDialog")
            else if (cle === "couleurs") moteur.commandeValidee(".uno:ColorScaleFormatDialog")
            else moteur.commandeValidee(".uno:ConditionalFormatEasy", { FormatRule: { type: "short", value: cle === "grands" ? 11 : 15 } })
        } else if (cle === "effacer") {
            tableaux.appeler("SamaAnalyse.Effacer", [a], function (ok, v) {
                if (tableaux.verifier(ok, v)) message.montrer(v === "0" ? "Rien à effacer ici" : "Mise en forme effacée")
            })
        } else if (/^gr/.test(cle)) {
            moteur.insererGraphique({ grColonnes: 0, grBarres: 1, grLignes: 5, grSecteurs: 2, grAires: 4 }[cle])
        } else if (/^total:/.test(cle)) {
            tableaux.appeler("SamaAnalyse.Totaux", [a, cle.slice(6)], function (ok, v) {
                if (tableaux.verifier(ok, v)) message.montrer("Totaux sous les colonnes (Ctrl+Z pour revenir)")
            })
        } else if (cle === "tableau") {
            tableaux.creer()
        }
        moteur.forceActiveFocus()
    }
    // Après la fenêtre du moteur : les couleurs de Sama (la règle peut tarder un peu : on redemande, 8 fois au plus)
    property var aHabiller: null
    property int essais: 0
    function habiller() {
        var e = aHabiller
        if (!e) return
        tableaux.appeler("SamaAnalyse.Habiller", [e.adresse, e.genre, e.nombre], function (ok, v) {
            if (ok && v === "absente" && analyse.essais < 8) { analyse.essais++; reessai.restart(); return }
            analyse.aHabiller = null
            if (v === "absente") { message.montrer("La mise en forme n'a pas pu être posée"); return }
            if (analyse.tableaux.verifier(ok, v)) {
                analyse.moteur.redessiner()
                message.montrer("Mise en forme ajoutée (Ctrl+Z pour revenir)")
            }
        })
    }
    Timer { id: reessai; interval: 250; onTriggered: analyse.habiller() }
    Connections {
        target: analyse.moteur
        function onDialogueValide() {
            if (!analyse.enAttente) return
            analyse.aHabiller = analyse.enAttente
            analyse.enAttente = null
            analyse.essais = 0
            reessai.restart()
        }
    }

    // ——— Le bouton, au coin de la sélection ———
    QQC2.AbstractButton {
        id: bouton
        visible: analyse.possible
        // (juste au-delà du coin : il ne cache pas la dernière valeur)
        x: Math.min(analyse.zone.x + analyse.zone.width - analyse.moteur.vueX - 4, analyse.width - width - 4)
        y: Math.min(analyse.zone.y + analyse.zone.height - analyse.moteur.vueY - 4, analyse.height - height - 4)
        width: 26
        height: 26
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        Accessible.name: "Analyse rapide"
        onClicked: carte.opened ? carte.close() : analyse.ouvrir()
        QQC2.ToolTip.visible: hovered && !carte.opened
        QQC2.ToolTip.delay: 500
        QQC2.ToolTip.text: "Analyse rapide (Ctrl+Q)"
        background: Item {
            // (ombre douce)
            Rectangle { anchors.fill: parent; anchors.topMargin: 2; anchors.margins: -1; radius: 8; color: Qt.rgba(40 / 255, 25 / 255, 10 / 255, 0.12) }
            Rectangle {
                anchors.fill: parent
                radius: 7
                color: Couleurs.champ
                border.width: 1
                border.color: bouton.hovered || carte.opened ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.2)
                Rectangle { anchors.fill: parent; radius: 7; color: bouton.hovered || carte.opened ? fenetre.accentFond : "transparent" }
            }
        }
        contentItem: Item {
            Picto { anchors.centerIn: parent; width: 15; height: 15; trace: "M4 5h16v14H4z M4 10h7 M13 9l-3 5h4l-3 5"; encre: fenetre.accent; trait: 1.9 }
        }
    }

    // ——— L'aperçu, dans les cases choisies ———
    property string apercu: ""
    readonly property var lignesVues: {
        var res = [], d = donnees
        if (!d || !apercu) return res
        var b = vueGrille.lignes
        for (var k = 0; k < b.length; k++) {
            var l = Number(b[k].texte) - 1
            if (l >= d.l1 && l <= d.l2 + 1 && b[k].fin > b[k].debut) res.push({ l: l, y: b[k].debut, h: b[k].fin - b[k].debut })
        }
        return res
    }
    readonly property var colonnesVues: {
        var res = [], d = donnees
        if (!d || !apercu) return res
        for (var c = d.c1; c <= d.c2; c++) {
            var b = vueGrille.parLettres[tableaux.lettres(c)]
            if (b && b.fin > b.debut) res.push({ c: c, x: b.debut, w: b.fin - b.debut })
        }
        return res
    }
    Item {
        anchors.fill: parent
        visible: analyse.apercu !== "" && carte.opened
        Repeater {
            model: analyse.lignesVues
            delegate: Item {
                id: ligneApercu
                readonly property var vue: modelData
                readonly property bool dessous: vue.l === analyse.donnees.l2 + 1
                y: vue.y - analyse.moteur.vueY
                height: vue.h
                width: analyse.width
                Repeater {
                    model: analyse.colonnesVues
                    delegate: Item {
                        id: caseApercu
                        readonly property var valeur: ligneApercu.dessous ? null : analyse.donnees.v[ligneApercu.vue.l - analyse.donnees.l1][modelData.c - analyse.donnees.c1]
                        readonly property var total: ligneApercu.dessous ? analyse.stats.colonnes[modelData.c - analyse.donnees.c1] : null
                        x: modelData.x - analyse.moteur.vueX
                        width: modelData.w
                        height: ligneApercu.height
                        // Barres : leur longueur montre la valeur
                        Rectangle {
                            visible: analyse.apercu === "barres" && caseApercu.valeur !== null && analyse.stats.max > 0
                            x: 3
                            y: 4
                            height: parent.height - 8
                            width: visible ? Math.max(2, (parent.width - 6) * Math.max(0, caseApercu.valeur) / analyse.stats.max) : 0
                            radius: 3
                            color: Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.22)
                        }
                        // Couleurs, plus grands, au-dessus de la moyenne
                        Rectangle {
                            anchors.fill: parent
                            anchors.margins: 0.5
                            visible: caseApercu.valeur !== null && (analyse.apercu === "couleurs"
                                     || (analyse.apercu === "grands" && caseApercu.valeur >= analyse.stats.seuil)
                                     || (analyse.apercu === "moyenne" && caseApercu.valeur > analyse.stats.moy))
                            color: analyse.apercu === "couleurs" && caseApercu.valeur !== null ? analyse.echelle(caseApercu.valeur)
                                 : analyse.apercu === "grands" ? "#FCEFC7" : "#E4ECEF"
                            opacity: 0.75
                        }
                        // Totaux : le résultat, dans la ligne en dessous
                        Rectangle {
                            visible: ligneApercu.dessous && /^total:/.test(analyse.apercu) && caseApercu.total !== null && analyse.donnees.dessous
                            anchors.fill: parent
                            anchors.margins: 1
                            radius: 3
                            color: fenetre.accentFond
                            border.width: 1
                            border.color: Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.45)
                            Text {
                                anchors.right: parent.right
                                anchors.rightMargin: 4
                                anchors.verticalCenter: parent.verticalCenter
                                text: caseApercu.total ? analyse.enChiffres(caseApercu.total[analyse.apercu.slice(6)]) : ""
                                font.pointSize: 10 * analyse.moteur.zoom
                                font.weight: Font.Bold
                                font.features: { "tnum": 1 }
                                color: fenetre.accentEncre
                            }
                        }
                        // Tableau : la première ligne en titres, une ligne sur deux teintée
                        Rectangle {
                            anchors.fill: parent
                            visible: analyse.apercu === "tableau" && !ligneApercu.dessous
                                     && (ligneApercu.vue.l === analyse.donnees.l1 || (ligneApercu.vue.l - analyse.donnees.l1) % 2 === 0)
                            color: ligneApercu.vue.l === analyse.donnees.l1 ? "#E4ECEF" : Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.05)
                            opacity: ligneApercu.vue.l === analyse.donnees.l1 ? 0.85 : 1
                            Rectangle { visible: ligneApercu.vue.l === analyse.donnees.l1; anchors.bottom: parent.bottom; width: parent.width; height: 1.5; color: fenetre.accent }
                        }
                    }
                }
            }
        }
    }

    // ——— La carte ———
    property string onglet: "forme"
    readonly property var onglets: [["forme", "Mise en forme"], ["graphiques", "Graphiques"], ["totaux", "Totaux"], ["tableau", "Tableau"]]
    // [clé, nom, tracé, explication]
    readonly property var tuiles: ({
        forme: [["barres", "Barres", "M4 7h12 M4 12h7 M4 17h15", "Des barres dans les cases : leur longueur montre la valeur."],
                ["couleurs", "Couleurs", "M4 5h5v14H4z M10 5h5v14h-5z M16 5h4v14h-4z", "Une couleur par case, de la plus petite valeur (latérite) à la plus grande (vert)."],
                ["grands", "Plus grands", "M12 4v16 M6 10l6-6 6 6", "Les plus grandes valeurs, mises en avant."],
                ["moyenne", "Au-dessus", "M4 14h16 M7 10V6 M12 10V4 M17 10V8", "Les valeurs au-dessus de la moyenne, mises en avant."],
                ["effacer", "Effacer", "M5 7h14 M10 7V5h4v2 M7 7l1 12h8l1-12", "Enlève les barres, les couleurs et les mises en avant de ces cases (Ctrl+Z ne les remet pas)."]],
        graphiques: [["grColonnes", "Colonnes", fenetre.actions.pictos.grColonnes, "Un graphique en colonnes, à partir des cases choisies."],
                     ["grBarres", "Barres", fenetre.actions.pictos.grBarres, "Un graphique en barres, à partir des cases choisies."],
                     ["grLignes", "Lignes", fenetre.actions.pictos.grLignes, "Une courbe : pour suivre une évolution."],
                     ["grSecteurs", "Secteurs", fenetre.actions.pictos.grSecteurs, "Les parts d'un tout."],
                     ["grAires", "Aires", fenetre.actions.pictos.grAires, "Une évolution, et son volume."]],
        totaux: [["total:somme", "Somme", fenetre.actions.pictos.somme, "La somme de chaque colonne, dans la ligne libre en dessous."],
                 ["total:moyenne", "Moyenne", fenetre.actions.pictos.moyenne, "La moyenne de chaque colonne, dans la ligne libre en dessous."],
                 ["total:nombre", "Nombre", fenetre.actions.pictos.nombre, "Combien de nombres dans chaque colonne."],
                 ["total:min", "Min", fenetre.actions.pictos.minimum, "La plus petite valeur de chaque colonne."],
                 ["total:max", "Max", fenetre.actions.pictos.maximum, "La plus grande valeur de chaque colonne."]],
        tableau: [["tableau", "Tableau", "M4 5h16v14H4z M4 10h16 M10 5v14", "Les cases deviennent un tableau : titres, filtres, ligne des totaux, fiches."]]
    })
    readonly property string explication: {
        var liste = tuiles[onglet] || []
        for (var i = 0; i < liste.length; i++) if (liste[i][0] === apercu) return liste[i][3]
        if (onglet === "totaux" && donnees && !donnees.dessous) return "La ligne sous les cases choisies n'est pas vide : les totaux n'y ont pas la place."
        return onglet === "forme" || onglet === "totaux" || onglet === "tableau"
               ? "Survolez une tuile : l'aperçu est dans les cases choisies ; un clic l'applique."
               : "Un graphique à partir des cases choisies ; il se place à côté."
    }

    QQC2.Popup {
        id: carte
        readonly property real gauche: analyse.zone.x - analyse.moteur.vueX
        readonly property real bas: analyse.zone.y + analyse.zone.height - analyse.moteur.vueY
        x: Math.max(6, Math.min(gauche, analyse.width - width - 6))
        // (sous la sélection ; au-dessus s'il n'y a pas la place)
        y: bas + 20 + height <= analyse.height ? bas + 20 : Math.max(6, analyse.zone.y - analyse.moteur.vueY - height - 10)
        width: 452
        padding: 14
        topPadding: 10
        modal: false
        focus: false
        closePolicy: QQC2.Popup.CloseOnEscape | QQC2.Popup.CloseOnPressOutsideParent
        onClosed: analyse.apercu = ""
        background: Item {
            Rectangle { anchors.fill: parent; anchors.topMargin: 8; anchors.margins: -4; radius: 18; color: Qt.rgba(40 / 255, 25 / 255, 10 / 255, 0.06) }
            Rectangle { anchors.fill: parent; anchors.topMargin: 3; anchors.margins: -1; radius: 15; color: Qt.rgba(40 / 255, 25 / 255, 10 / 255, 0.09) }
            Rectangle { anchors.fill: parent; radius: 14; color: Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12) }
        }
        contentItem: ColumnLayout {
            spacing: 12
            // Onglets
            Item {
                Layout.fillWidth: true
                Layout.preferredHeight: 32
                Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1) }
                Row {
                    spacing: 2
                    Repeater {
                        model: analyse.onglets
                        delegate: QQC2.AbstractButton {
                            id: ongletBouton
                            readonly property bool choisi: analyse.onglet === modelData[0]
                            height: 32
                            width: texteOnglet.implicitWidth + 20
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: { analyse.onglet = modelData[0]; analyse.apercu = "" }
                            background: Item {
                                Rectangle { visible: ongletBouton.choisi; anchors.bottom: parent.bottom; width: parent.width; height: 2; color: fenetre.accent }
                            }
                            contentItem: Text {
                                id: texteOnglet
                                text: modelData[1]
                                horizontalAlignment: Text.AlignHCenter
                                verticalAlignment: Text.AlignVCenter
                                font.pixelSize: 13
                                font.weight: ongletBouton.choisi ? Font.DemiBold : Font.Normal
                                color: ongletBouton.choisi ? fenetre.accentEncre : ongletBouton.hovered ? Couleurs.texte : Couleurs.texte2
                            }
                        }
                    }
                }
            }
            // Tuiles
            Row {
                spacing: 8
                Repeater {
                    model: analyse.tuiles[analyse.onglet] || []
                    delegate: QQC2.AbstractButton {
                        id: tuile
                        readonly property bool choisie: analyse.apercu === modelData[0]
                        readonly property bool impossible: /^total:/.test(modelData[0]) && analyse.donnees !== null && !analyse.donnees.dessous
                        width: 76
                        height: 70
                        enabled: !impossible
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        Accessible.name: modelData[1]
                        onHoveredChanged: if (hovered) analyse.apercu = modelData[0]; else if (analyse.apercu === modelData[0]) analyse.apercu = ""
                        onClicked: analyse.appliquer(modelData[0])
                        background: Rectangle {
                            radius: 10
                            color: tuile.choisie ? fenetre.accentFond : Couleurs.champ
                            border.width: tuile.choisie ? 1.5 : 1
                            border.color: tuile.choisie ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                        }
                        contentItem: Item {
                            opacity: tuile.enabled ? 1 : 0.4
                            ColumnLayout {
                                anchors.centerIn: parent
                                spacing: 6
                                Picto {
                                    Layout.alignment: Qt.AlignHCenter
                                    width: 22; height: 22
                                    trace: modelData[2]
                                    encre: tuile.choisie ? fenetre.accentEncre : Couleurs.texte2
                                    trait: /^gr(Colonnes|Barres)$/.test(modelData[0]) ? 3 : 1.75
                                }
                                Text { Layout.alignment: Qt.AlignHCenter; text: modelData[1]; font.pixelSize: 12; color: Couleurs.texte }
                            }
                        }
                    }
                }
            }
            Text {
                Layout.fillWidth: true
                text: analyse.explication
                font.pixelSize: 12
                lineHeight: 1.15
                color: Couleurs.texte2
                wrapMode: Text.Wrap
            }
        }
    }
}
