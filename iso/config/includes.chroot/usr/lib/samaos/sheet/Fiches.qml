// Le tableau en fiches (maquette « Sheet · Le tableau en fiches ») : une carte par ligne, et la fiche ouverte à droite
// pour saisir sans se tromper de case. Quand une colonne calculée vaut « A − B » (Reste = Part − Versé), la carte
// montre ce qui est versé sur ce qui est dû, et la fiche prépare un rappel. Les numéros de téléphone restent masqués
// tant qu'on ne demande pas à les voir.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "Fonctions.js" as Fonctions

Rectangle {
    id: fiches
    color: Couleurs.fond
    property var tableau: null
    property var donnees: ({ colonnes: [], lignes: [] })
    property int choisie: -1                 // ligne de la feuille (à partir de 0) de la fiche ouverte
    property bool voirNumeros: false
    readonly property var doc: fenetre.doc
    readonly property var tableaux: fenetre.tableaux

    readonly property var remplies: donnees.lignes.filter(function (r) { return !r.vide || r.l === choisie })
    readonly property var fiche: {
        for (var i = 0; i < donnees.lignes.length; i++) if (donnees.lignes[i].l === choisie) return donnees.lignes[i]
        return null
    }
    readonly property int rang: { for (var i = 0; i < remplies.length; i++) if (remplies[i].l === choisie) return i; return -1 }

    // Colonne calculée « A − B » : { reste, du, verse } (rangs des colonnes), ou null
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
    function estTelephone(nom) { return /t[ée]l[ée]?phone|^t[ée]l\b|portable|whatsapp/i.test(nom) }
    function masquer(v) {
        var chiffres = v.replace(/\D/g, "")
        if (chiffres.length < 6) return v
        return chiffres.slice(0, 2) + " •• •• •• " + chiffres.slice(-2)
    }
    function initiales(nom) {
        var mots = String(nom || "").trim().split(/\s+/).filter(function (m) { return m.length > 0 })
        if (!mots.length) return "?"
        return (mots[0].charAt(0) + (mots.length > 1 ? mots[1].charAt(0) : "")).toUpperCase()
    }
    function pourcentage(r) {
        if (!progression || !r.n[progression.du]) return 0
        return Math.max(0, Math.min(1, (r.n[progression.verse] || 0) / r.n[progression.du]))
    }

    // ——— Lire et écrire ———
    function ouvrir(t) {
        tableau = t
        choisie = -1
        voirNumeros = false
        lire()
    }
    function lire() {
        if (!tableau) return
        tableaux.appeler("Lignes", [tableau.nom], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{") return
            try { donnees = JSON.parse(v) } catch (e) { return }
            if (choisie < 0 || !fiche) {
                var r = remplies
                choisie = r.length ? r[0].l : (donnees.lignes.length ? donnees.lignes[0].l : -1)
            }
        })
    }
    Timer { id: relire; interval: 350; onTriggered: fiches.lire() }
    Connections {
        target: fiches.doc
        function onRevisionChanged() { if (fiches.visible) relire.restart() }
    }
    function adresse(colonne, ligne) { return tableaux.lettres(tableau.c1 + colonne) + (ligne + 1) }
    function ecrire(colonne, texte) {
        if (!fiche) return
        var ancien = fiche.v[colonne]
        if (texte === ancien) return
        doc.allerA(adresse(colonne, fiche.l))
        if (texte === "") doc.touche(1286)
        else doc.saisir(texte)
    }
    // Nouvelle fiche : la première ligne vide du tableau, sinon une ligne de plus
    function nouvelle() {
        for (var i = 0; i < donnees.lignes.length; i++) {
            if (donnees.lignes[i].vide) { choisie = donnees.lignes[i].l; Qt.callLater(champNom.forceActiveFocus); return }
        }
        tableaux.appeler("AjouterLigne", [tableau.nom], function (ok, v) {
            if (!tableaux.verifier(ok, v)) return
            var a = v.split(".").pop().replace(/\$/g, "")
            choisie = Number(a.replace(/^[A-Z]+/, "")) - 1
            tableaux.rafraichir()
            lire()
            Qt.callLater(champNom.forceActiveFocus)
        })
    }
    function aller(pas) {
        var r = remplies
        if (!r.length) return
        lacher()
        var i = Math.max(0, Math.min(r.length - 1, rang + pas))
        choisie = r[i].l
    }
    function montrer(l) { lacher(); choisie = l }
    // Enregistre le champ en cours (sa sortie l'écrit dans le tableau) avant de changer de fiche
    function lacher() { if (dansLaFiche(fenetre.activeFocusItem)) fiches.forceActiveFocus() }
    function dansLaFiche(item) {
        for (var it = item; it; it = it.parent) if (it === panneau) return true
        return false
    }

    // ——— Au clavier : Tab et Maj+Tab d'un champ à l'autre, Entrée au suivant, et après le dernier, la fiche suivante ———
    function champs() {
        var liste = [champNom]
        for (var i = 0; i < champsRepetes.count; i++) {
            var c = champsRepetes.itemAt(i)
            if (c && !c.calcule) liste.push(c.entree)
        }
        return liste
    }
    function passer(depuis, pas, ficheSuivante) {
        var liste = champs(), i = liste.indexOf(depuis) + pas
        if (i >= 0 && i < liste.length) { liste[i].forceActiveFocus(); return }
        if (!ficheSuivante) { liste[(i + liste.length) % liste.length].forceActiveFocus(); return }
        // Entrée sur le dernier champ : la fiche d'après (sur la dernière, on valide simplement)
        lacher()
        if (rang < remplies.length - 1) { aller(1); Qt.callLater(champNom.forceActiveFocus) }
    }

    // Presse-papiers (copier le message de rappel)
    TextEdit { id: pressePapiers; visible: false }
    function copier(texte) {
        pressePapiers.text = texte
        pressePapiers.selectAll()
        pressePapiers.copy()
        message.montrer("Message copié")
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // ——— Une carte par ligne ———
        QQC2.ScrollView {
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: availableWidth
            GridLayout {
                id: cartes
                width: parent.width - 40
                x: 20
                y: 20
                columns: Math.max(1, Math.floor(width / 270))
                columnSpacing: 12
                rowSpacing: 12
                Repeater {
                    model: fiches.remplies
                    delegate: QQC2.AbstractButton {
                        id: carte
                        readonly property var r: modelData
                        readonly property bool ouverte: modelData.l === fiches.choisie
                        readonly property real pct: fiches.pourcentage(modelData)
                        readonly property var reste: fiches.progression ? modelData.n[fiches.progression.reste] : null
                        // (une carte garde sa largeur, même seule sur sa rangée)
                        Layout.fillWidth: true
                        Layout.maximumWidth: (cartes.width - (cartes.columns - 1) * cartes.columnSpacing) / cartes.columns
                        Layout.preferredHeight: 122
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        onClicked: fiches.montrer(modelData.l)
                        background: Rectangle {
                            radius: 14
                            color: Couleurs.champ
                            border.width: carte.ouverte ? 2 : 1
                            border.color: carte.ouverte ? fenetre.accent : carte.hovered ? Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.4) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                        }
                        contentItem: ColumnLayout {
                            spacing: 9
                            RowLayout {
                                spacing: 10
                                Layout.leftMargin: 2
                                Rectangle {
                                    Layout.preferredWidth: 32; Layout.preferredHeight: 32
                                    radius: 16
                                    color: Couleurs.sombre ? Couleurs.carte : "#F0E7DB"
                                    Text { anchors.centerIn: parent; text: fiches.initiales(carte.ouverte && champNom.activeFocus ? champNom.text : modelData.v[0]); font.pixelSize: 11; font.weight: Font.Bold; color: Couleurs.texte2 }
                                }
                                Text {
                                    // (pendant qu'on écrit le nom dans la fiche, la carte le montre déjà)
                                    readonly property string nom: carte.ouverte && champNom.activeFocus ? champNom.text : modelData.v[0]
                                    Layout.fillWidth: true
                                    text: nom || "Sans nom"
                                    font.pixelSize: 15
                                    font.weight: Font.DemiBold
                                    color: nom ? Couleurs.texte : Couleurs.texte3
                                    elide: Text.ElideRight
                                }
                            }
                            // Versé sur dû (cotisations…)
                            Rectangle {
                                visible: fiches.progression !== null
                                Layout.fillWidth: true
                                Layout.preferredHeight: 6
                                radius: 3
                                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
                                Rectangle { width: parent.width * carte.pct; height: 6; radius: 3; color: fenetre.accent }
                            }
                            // Sinon : les deux colonnes suivantes
                            Repeater {
                                model: fiches.progression ? [] : [1, 2]
                                delegate: Text {
                                    Layout.fillWidth: true
                                    visible: modelData < fiches.donnees.colonnes.length
                                    text: visible ? fiches.donnees.colonnes[modelData].nom + " : " + (!carte.r.v[modelData] ? "—" : fiches.estTelephone(fiches.donnees.colonnes[modelData].nom) ? fiches.masquer(carte.r.v[modelData]) : carte.r.v[modelData]) : ""
                                    font.pixelSize: 13
                                    color: Couleurs.texte2
                                    elide: Text.ElideRight
                                }
                            }
                            Item { Layout.fillHeight: true }
                            RowLayout {
                                visible: fiches.progression !== null
                                Layout.fillWidth: true
                                Text {
                                    Layout.fillWidth: true
                                    text: fiches.progression ? (modelData.v[fiches.progression.verse] || "0") + " sur " + modelData.v[fiches.progression.du] : ""
                                    font.pixelSize: 13
                                    font.features: { "tnum": 1 }
                                    color: Couleurs.texte2
                                    elide: Text.ElideRight
                                }
                                Rectangle {
                                    visible: carte.reste > 0
                                    Layout.preferredHeight: 22
                                    Layout.preferredWidth: texteReste.implicitWidth + 16
                                    radius: 6
                                    color: Qt.rgba(226 / 255, 166 / 255, 43 / 255, 0.28)
                                    Text { id: texteReste; anchors.centerIn: parent; text: fiches.progression ? fiches.donnees.colonnes[fiches.progression.reste].nom + " " + modelData.v[fiches.progression.reste] : ""; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.sombre ? "#F2D27A" : "#1E2740" }
                                }
                                RowLayout {
                                    visible: carte.reste !== null && carte.reste <= 0 && modelData.n[fiches.progression ? fiches.progression.du : 0] > 0
                                    spacing: 4
                                    Picto { width: 13; height: 13; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: fenetre.vertEncre; trait: 2.2 }
                                    Text { text: "Soldé"; font.pixelSize: 12; font.weight: Font.DemiBold; color: fenetre.vertEncre }
                                }
                            }
                        }
                        padding: 14
                        leftPadding: 16
                        rightPadding: 16
                    }
                }
            }
        }

        // ——— La fiche ouverte ———
        Rectangle {
            id: panneau
            Layout.preferredWidth: 380
            Layout.fillHeight: true
            color: Couleurs.champ
            Rectangle { width: 0.5; height: parent.height; color: Couleurs.bord }

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 22
                anchors.bottomMargin: 16
                spacing: 16
                visible: fiches.fiche !== null

                // En-tête : initiales, nom (on l'écrit ici), place dans le tableau
                RowLayout {
                    spacing: 14
                    Rectangle {
                        Layout.preferredWidth: 48; Layout.preferredHeight: 48
                        radius: 24
                        color: fenetre.accentFond
                        Text { anchors.centerIn: parent; text: fiches.initiales(champNom.text); font.pixelSize: 16; font.weight: Font.Bold; color: fenetre.accentEncre }
                    }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 2
                        TextInput {
                            id: champNom
                            // (une chaîne : relire le tableau sans que le nom change ne touche pas à ce qu'on est en train d'écrire)
                            readonly property string valeur: fiches.fiche ? fiches.fiche.v[0] : ""
                            Layout.fillWidth: true
                            text: valeur
                            font.pixelSize: 24
                            font.weight: Font.DemiBold
                            color: Couleurs.texte
                            clip: true
                            selectByMouse: true
                            activeFocusOnTab: true
                            Accessible.name: fiches.donnees.colonnes.length ? fiches.donnees.colonnes[0].nom : "Nom"
                            Text { visible: !champNom.text; text: fiches.donnees.colonnes.length ? fiches.donnees.colonnes[0].nom + "…" : ""; font: champNom.font; color: Couleurs.texte3 }
                            onEditingFinished: fiches.ecrire(0, text)
                            Keys.onEscapePressed: { text = Qt.binding(function () { return champNom.valeur }); focus = false }
                            Keys.onTabPressed: fiches.passer(champNom, 1, false)
                            Keys.onBacktabPressed: fiches.passer(champNom, -1, false)
                            Keys.onReturnPressed: fiches.passer(champNom, 1, true)
                            Keys.onEnterPressed: fiches.passer(champNom, 1, true)
                        }
                        Text {
                            text: fiches.fiche ? "Ligne " + (fiches.fiche.l + 1) + " du tableau " + fiches.tableau.nom : ""
                            font.pixelSize: 13
                            color: Couleurs.texte2
                        }
                    }
                }

                // Les autres colonnes
                QQC2.ScrollView {
                    id: defilementChamps
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    contentWidth: availableWidth
                    QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff
                    ColumnLayout {
                        // (de la place pour la barre de défilement, à droite)
                        width: defilementChamps.availableWidth - 14
                        spacing: 14
                        Repeater {
                            id: champsRepetes
                            // (un nombre de champs, pas la liste : relire le tableau après chaque saisie ne refait pas les champs,
                            // et le curseur reste dans celui où l'on écrit)
                            model: Math.max(0, fiches.donnees.colonnes.length - 1)
                            delegate: ColumnLayout {
                                id: champ
                                readonly property int colonne: index + 1
                                readonly property var modele: fiches.donnees.colonnes[colonne] || ({ nom: "", calcul: false, formule: "" })
                                readonly property bool calcule: modele.calcul
                                readonly property alias entree: saisie
                                readonly property bool telephone: fiches.estTelephone(champ.modele.nom)
                                readonly property string valeur: fiches.fiche ? fiches.fiche.v[colonne] : ""
                                // (reste à zéro d'une ligne due : soldé)
                                readonly property bool solde: fiches.progression !== null && fiches.progression.reste === colonne && fiches.fiche !== null
                                                              && fiches.fiche.n[colonne] !== null && fiches.fiche.n[colonne] <= 0 && fiches.fiche.n[fiches.progression.du] > 0
                                Layout.fillWidth: true
                                spacing: 6
                                // Colonne calculée : on la lit, on ne l'écrit pas
                                Rectangle {
                                    visible: champ.modele.calcul
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 52
                                    radius: 10
                                    color: "transparent"
                                    border.width: 1
                                    border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.2)
                                    RowLayout {
                                        anchors.fill: parent
                                        anchors.leftMargin: 12
                                        anchors.rightMargin: 12
                                        spacing: 10
                                        Picto { trace: "M6 11h12v9H6z M8 11V8a4 4 0 0 1 8 0v3"; encre: Couleurs.texte2 }
                                        ColumnLayout {
                                            Layout.fillWidth: true
                                            spacing: 1
                                            Text { text: champ.modele.nom + " · calculé"; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte2 }
                                            Text {
                                                Layout.fillWidth: true
                                                text: { var m = Fonctions.morceaux(champ.modele.formule); return m ? m.map(function (x) { return x.texte }).join("").replace(/^=/, "").replace(/-/g, " − ") : champ.modele.formule }
                                                font.pixelSize: 12
                                                color: Couleurs.texte2
                                                elide: Text.ElideRight
                                            }
                                        }
                                        Rectangle {
                                            readonly property bool doit: fiches.progression && fiches.progression.reste === champ.colonne && fiches.fiche && fiches.fiche.n[champ.colonne] > 0
                                            Layout.preferredHeight: 26
                                            Layout.preferredWidth: valeurCalculee.implicitWidth + 18
                                            radius: 6
                                            color: doit ? Qt.rgba(226 / 255, 166 / 255, 43 / 255, 0.28) : fenetre.accentFond
                                            Text { id: valeurCalculee; anchors.centerIn: parent; text: champ.solde ? "Soldé" : (champ.valeur || "—"); font.pixelSize: 15; font.weight: Font.Bold; color: parent.doit ? (Couleurs.sombre ? "#F2D27A" : "#1E2740") : fenetre.accentEncre }
                                        }
                                    }
                                }
                                // Colonne à écrire
                                Text { visible: !champ.modele.calcul; text: champ.modele.nom; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte2 }
                                Rectangle {
                                    visible: !champ.modele.calcul
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 40
                                    radius: 10
                                    color: Couleurs.champ
                                    border.width: saisie.activeFocus ? 1.5 : 1
                                    border.color: saisie.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
                                    RowLayout {
                                        anchors.fill: parent
                                        anchors.leftMargin: 12
                                        anchors.rightMargin: 6
                                        spacing: 8
                                        TextInput {
                                            id: saisie
                                            Layout.fillWidth: true
                                            // (un numéro reste masqué tant qu'on ne l'écrit pas ou qu'on ne demande pas à le voir)
                                            readonly property bool masque: champ.telephone && !fiches.voirNumeros && !activeFocus && champ.valeur !== ""
                                            text: masque ? fiches.masquer(champ.valeur) : champ.valeur
                                            font.pixelSize: 15
                                            font.letterSpacing: masque ? 0.6 : 0
                                            font.features: { "tnum": 1 }
                                            color: Couleurs.texte
                                            clip: true
                                            selectByMouse: true
                                            activeFocusOnTab: !champ.calcule
                                            Accessible.name: champ.modele.nom
                                            onActiveFocusChanged: if (activeFocus) Qt.callLater(selectAll)
                                            onEditingFinished: { if (!masque) fiches.ecrire(champ.colonne, text) }
                                            Keys.onTabPressed: fiches.passer(saisie, 1, false)
                                            Keys.onBacktabPressed: fiches.passer(saisie, -1, false)
                                            Keys.onReturnPressed: fiches.passer(saisie, 1, true)
                                            Keys.onEnterPressed: fiches.passer(saisie, 1, true)
                                            // (Échap : on revient à la valeur du tableau, et la liaison reprend)
                                            Keys.onEscapePressed: { text = Qt.binding(function () { return masque ? fiches.masquer(champ.valeur) : champ.valeur }); focus = false }
                                        }
                                        Outil {
                                            visible: champ.telephone && champ.valeur !== ""
                                            Layout.preferredHeight: 30
                                            text: fiches.voirNumeros ? "Masquer" : "Afficher"
                                            picto: "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
                                            police.pixelSize: 12
                                            background: Rectangle { radius: 7; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) }
                                            onClicked: fiches.voirNumeros = !fiches.voirNumeros
                                        }
                                    }
                                }
                            }
                        }

                        // Rappel (cotisations…) : un message simple, sans lien
                        Rectangle {
                            readonly property var reste: fiches.progression && fiches.fiche ? fiches.fiche.n[fiches.progression.reste] : 0
                            readonly property string message: fiches.fiche ? "Bonjour " + String(fiches.fiche.v[0]).split(/\s+/)[0] + ", il reste "
                                                              + fiches.fiche.v[fiches.progression ? fiches.progression.reste : 0] + " à verser pour « "
                                                              + fiches.tableau.nom.replace(/_/g, " ") + " ». Merci et bonne journée." : ""
                            visible: fiches.fiche !== null && reste > 0 && fiches.fiche.v[0] !== ""
                            Layout.fillWidth: true
                            implicitHeight: rappel.implicitHeight + 28
                            radius: 12
                            color: Couleurs.sombre ? Couleurs.carte : "#F3ECE2"
                            ColumnLayout {
                                id: rappel
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.top: parent.top
                                anchors.margins: 14
                                spacing: 8
                                RowLayout {
                                    spacing: 8
                                    Picto { width: 15; height: 15; trace: "M4 5h16v11H9l-5 4z"; encre: Couleurs.texte }
                                    Text { text: "Préparer un rappel"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte }
                                }
                                Text { Layout.fillWidth: true; text: parent.parent.message; font.pixelSize: 14; color: Couleurs.texte; wrapMode: Text.Wrap }
                                RowLayout {
                                    spacing: 10
                                    Outil {
                                        text: "Copier le message"
                                        picto: "M8 8h11v12H8z M5 16V4h11"
                                        background: Rectangle { radius: 8; color: Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16) }
                                        onClicked: fiches.copier(rappel.parent.message)
                                    }
                                    Text { Layout.fillWidth: true; text: "Sans lien ni code : personne ne le prendra pour une arnaque."; font.pixelSize: 12; color: Couleurs.texte2; wrapMode: Text.Wrap }
                                }
                            }
                        }
                    }
                }

                // Fiche précédente, suivante
                RowLayout {
                    spacing: 8
                    Outil {
                        Layout.preferredWidth: 34
                        Layout.preferredHeight: 34
                        picto: "M15 6l-6 6 6 6"
                        aide: "Fiche précédente"
                        enabled: fiches.rang > 0
                        background: Rectangle { radius: 9; color: Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }
                        onClicked: fiches.aller(-1)
                    }
                    Outil {
                        Layout.preferredWidth: 34
                        Layout.preferredHeight: 34
                        picto: "M9 6l6 6-6 6"
                        aide: "Fiche suivante"
                        enabled: fiches.rang < fiches.remplies.length - 1
                        background: Rectangle { radius: 9; color: Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }
                        onClicked: fiches.aller(1)
                    }
                    Text { text: fiches.rang >= 0 ? "Fiche " + (fiches.rang + 1) + " sur " + fiches.remplies.length : ""; font.pixelSize: 13; color: Couleurs.texte2 }
                    Item { Layout.fillWidth: true }
                    // (pendant la saisie : comment passer au champ suivant ; sinon : c'est enregistré)
                    readonly property bool enSaisie: fiches.dansLaFiche(fenetre.activeFocusItem)
                    Text { visible: parent.enSaisie; text: "Tab ou Entrée : suivant"; font.pixelSize: 12; color: Couleurs.texte3 }
                    Picto { visible: !parent.enSaisie; width: 13; height: 13; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: fenetre.vertEncre; trait: 2 }
                    Text { visible: !parent.enSaisie; text: "Enregistré"; font.pixelSize: 13; color: Couleurs.texte2 }
                }
            }

            // Aucune fiche
            ColumnLayout {
                anchors.centerIn: parent
                width: parent.width - 60
                visible: fiches.fiche === null
                spacing: 8
                Text { Layout.fillWidth: true; text: "Pas encore de fiche"; font.pixelSize: 17; font.weight: Font.DemiBold; color: Couleurs.texte; horizontalAlignment: Text.AlignHCenter }
                Text { Layout.fillWidth: true; text: "« Nouvelle fiche » ajoute une ligne au tableau."; font.pixelSize: 13; color: Couleurs.texte2; horizontalAlignment: Text.AlignHCenter; wrapMode: Text.Wrap }
            }
        }
    }
}
