// Fenêtre plein écran de la Cour : barre de recherche, puis les applications rangées
// par sections (Essentiels, Bureautique, Gestion, Outils, Système…), ou les résultats de recherche quand on tape
// (maquette bur-01) : groupés en Applications, Fichiers, Réglages, Actions ; ↑ ↓ pour choisir, Entrée pour
// ouvrir, Échap pour fermer.

import QtQuick
import QtQml.Models
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.kirigami as Kirigami
import org.kde.plasma.private.kicker as Kicker
import org.kde.plasma.plasma5support as P5Support
import "file:///usr/lib/samaos/reglages/sections.js" as Reglages

Kicker.DashboardWindow {
    id: fenetre

    property var modeleRacine
    property var modeleRecherche
    property var modeleApps: null
    property var modeleCategories

    // Sections de Sama : les catégories de KDE sont regroupées, les applications Sama placées à la main
    readonly property var ordreSections: ["Essentiels", "Bureautique", "Gestion", "Éducation", "Multimédia", "Jeux", "Outils", "Système", "Autres"]
    readonly property var placesSama: ({
        "samaos-griot.desktop": "Essentiels", "samaos-fichiers.desktop": "Essentiels",
        "samaos-photos.desktop": "Essentiels", "samaos-sugu.desktop": "Essentiels",
        "samaos-docs.desktop": "Bureautique", "samaos-sheet.desktop": "Bureautique",
        "samaos-presentations.desktop": "Bureautique", "org.kde.okular.desktop": "Bureautique",
        "org.kde.ark.desktop": "Outils", "org.kde.spectacle.desktop": "Outils", "samaos-capture.desktop": "Outils",
        "org.kde.konsole.desktop": "Outils", "org.kde.plasma-systemmonitor.desktop": "Outils", "samaos-moniteur.desktop": "Outils",
        "systemsettings.desktop": "Système", "samaos-reglages.desktop": "Système", "org.kde.khelpcenter.desktop": "Système"
    })
    // Catégories de KDE (noms traduits) → sections de Sama
    function sectionDeCategorie(nom) {
        var n = String(nom).toLowerCase()
        if (n.indexOf("bureautique") >= 0 || n.indexOf("office") >= 0) return "Bureautique"
        if (n.indexOf("gestion") >= 0 || n.indexOf("finance") >= 0) return "Gestion"
        if (n.indexOf("éducation") >= 0 || n.indexOf("education") >= 0 || n.indexOf("science") >= 0) return "Éducation"
        if (n.indexOf("multimédia") >= 0 || n.indexOf("multimedia") >= 0 || n.indexOf("graphi") >= 0) return "Multimédia"
        if (n.indexOf("jeu") >= 0 || n.indexOf("game") >= 0) return "Jeux"
        if (n.indexOf("internet") >= 0) return "Essentiels"
        if (n.indexOf("utilitaire") >= 0 || n.indexOf("développement") >= 0 || n.indexOf("utilit") >= 0) return "Outils"
        if (n.indexOf("système") >= 0 || n.indexOf("system") >= 0 || n.indexOf("paramètre") >= 0 || n.indexOf("setting") >= 0) return "Système"
        return "Autres"
    }
    function identifiant(modele, ligne) {
        var id = String(modele.data(modele.index(ligne, 0), Qt.UserRole + 3) || "")   // favoriteId (« applications:xxx.desktop »)
        if (!id) id = String(modele.data(modele.index(ligne, 0), Qt.DisplayRole) || "")
        return id.replace(/^applications:/, "")
    }

    property var sections: []
    // Pastille de filtre choisie (« Toutes » ou le titre d'une section)
    property string filtre: "Toutes"
    // Applications affichées : toutes (ordre alphabétique) ou celles de la catégorie choisie
    readonly property var appsAffichees: {
        var liste = []
        for (var i = 0; i < sections.length; i++) {
            if (filtre === "Toutes" || sections[i].titre === filtre) liste = liste.concat(sections[i].apps)
        }
        if (filtre === "Toutes") liste.sort(function (x, y) { return x.nom.localeCompare(y.nom) })
        return liste
    }
    function construireSections() {
        if (!modeleCategories || modeleCategories.count === 0) { relanceSections.restart(); return }
        var parSection = {}
        var vus = {}
        for (var c = 0; c < modeleCategories.count; c++) {
            var categorie = modeleCategories.modelForRow(c)
            if (!categorie) continue
            var nomCategorie = String(modeleCategories.data(modeleCategories.index(c, 0), Qt.DisplayRole) || "")
            for (var a = 0; a < categorie.count; a++) {
                if (categorie.modelForRow && categorie.modelForRow(a)) continue   // sous-catégorie : ignorée
                var id = identifiant(categorie, a)
                if (!id || vus[id]) continue
                vus[id] = true
                var section = placesSama[id] || (id.indexOf("install") >= 0 ? "Système" : sectionDeCategorie(nomCategorie))
                if (!parSection[section]) parSection[section] = []
                parSection[section].push({ modele: categorie, ligne: a,
                                           nom: String(categorie.data(categorie.index(a, 0), Qt.DisplayRole) || "") })
            }
        }
        var liste = []
        for (var i = 0; i < ordreSections.length; i++) {
            var apps = parSection[ordreSections[i]]
            if (!apps || apps.length === 0) continue
            apps.sort(function (x, y) { return x.nom.localeCompare(y.nom) })
            liste.push({ titre: ordreSections[i], apps: apps })
        }
        sections = liste
    }
    property Timer relanceSections: Timer { interval: 400; onTriggered: fenetre.construireSections() }

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property bool recherche: champ.text.length > 0

    // Couleurs de la maquette (clair / sombre)
    readonly property color couleurPanneau: sombre ? "#232839" : "#FCFAF7"
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color texte3: sombre ? "#8C867A" : "#8A8277"
    readonly property color encre: sombre ? "#F0B392" : "#93401F"
    readonly property color trait: sombre ? Qt.rgba(1, 1, 1, 0.10) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)

    // ——— Recherche ———
    // groupes : [{ titre, sources: [{ modele (résultats d'un module de recherche) | reglages (liste), debut, nombre }] }]
    // « debut » : position du premier résultat de la source dans la liste à plat (pour ↑ ↓ et Entrée)
    property var groupes: []
    property int totalResultats: 0
    property int choix: 0
    property Timer regroupement: Timer { interval: 40; onTriggered: fenetre.regrouper() }
    readonly property var limites: ({ "Applications": 6, "Fichiers": 5, "Réglages": 4, "Actions": 4 })

    // Les modules de recherche répondent l'un après l'autre : on regroupe à chaque nouvelle réponse
    property Instantiator surveillance: Instantiator {
        model: fenetre.modeleRecherche
        delegate: QtObject {
            required property int index
            readonly property var sous: fenetre.modeleRecherche ? fenetre.modeleRecherche.modelForRow(index) : null
            readonly property int nombre: sous ? sous.count : 0
            onNombreChanged: fenetre.regroupement.restart()
        }
        onObjectAdded: fenetre.regroupement.restart()
        onObjectRemoved: fenetre.regroupement.restart()
    }

    function sansAccents(t) { return String(t).normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase() }
    // Met en gras la partie tapée (« Budg » dans « Budget_2026.xlsx »)
    function surligner(texte, q) {
        var t = String(texte || ""), k = sansAccents(q).trim()
        var echapper = function (x) { return x.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;") }
        var i = k ? sansAccents(t).indexOf(k) : -1
        if (i < 0) return echapper(t)
        return echapper(t.slice(0, i)) + "<b>" + echapper(t.slice(i, i + k.length)) + "</b>" + echapper(t.slice(i + k.length))
    }
    // D'après le nom (traduit) du module de recherche
    function groupeDe(sous) {
        var nom = sansAccents(sous.name || "")
        if (nom.indexOf("application") >= 0) return "Applications"
        if (/fichier|dossier|document|emplacement|bureau|baloo|recent/.test(nom)) return "Fichiers"
        return "Actions"     // calculs, conversions, éteindre, verrouiller…
    }
    // Sections et sous-pages des Réglages de Sama (sections.js, partagé avec les Réglages)
    function reglagesTrouves(q) {
        var k = sansAccents(q).trim()
        if (!k) return []
        var liste = []
        Reglages.sections.forEach(function (s) {
            if (sansAccents(s.titre + " " + s.mots + " " + s.detail).indexOf(k) >= 0)
                liste.push({ titre: s.titre, detail: s.detail, picto: s.picto, cible: s.id })
        })
        Reglages.raccourcis.forEach(function (r) {
            if (sansAccents(r.titre + " " + r.mots + " " + r.detail).indexOf(k) >= 0) {
                var section = Reglages.sections.filter(function (s) { return s.id === r.section })[0]
                liste.push({ titre: r.titre, detail: r.detail, picto: section ? section.picto : "", cible: r.cible })
            }
        })
        return liste
    }
    function regrouper() {
        var ordre = ["Applications", "Fichiers", "Réglages", "Actions"]
        var parGroupe = { "Applications": [], "Fichiers": [], "Réglages": [], "Actions": [] }
        if (recherche && modeleRecherche) {
            for (var i = 0; i < modeleRecherche.count; i++) {
                var sous = modeleRecherche.modelForRow(i)
                if (sous && sous.count > 0) parGroupe[groupeDe(sous)].push({ modele: sous, total: sous.count })
            }
            var r = reglagesTrouves(champ.text)
            if (r.length > 0) parGroupe["Réglages"].push({ reglages: r, total: r.length })
        }
        var liste = [], position = 0
        ordre.forEach(function (titre) {
            var reste = limites[titre], sources = []
            parGroupe[titre].forEach(function (src) {
                var n = Math.min(src.total, reste)
                if (n <= 0) return
                src.debut = position
                src.nombre = n
                position += n
                reste -= n
                sources.push(src)
            })
            // Le groupe Applications reste affiché (« Aucune application ne correspond ») dès qu'il y a d'autres résultats
            if (sources.length > 0 || titre === "Applications") liste.push({ titre: titre, sources: sources })
        })
        totalResultats = position
        groupes = position > 0 ? liste : []
        if (choix >= position) choix = Math.max(0, position - 1)
    }
    function lancerChoix() {
        for (var g = 0; g < groupes.length; g++) {
            var sources = groupes[g].sources
            for (var s = 0; s < sources.length; s++) {
                var src = sources[s]
                if (choix < src.debut || choix >= src.debut + src.nombre) continue
                if (src.modele) lancer(src.modele, choix - src.debut)
                else ouvrirReglage(src.reglages[choix - src.debut].cible)
                return
            }
        }
    }
    property P5Support.DataSource executeur: P5Support.DataSource {
        engine: "executable"
        onNewData: source => disconnectSource(source)
    }
    function ouvrirReglage(cible) {
        executeur.connectSource("sama-reglages " + cible)
        fenetre.toggle()
    }

    // Fond dessiné par la Cour elle-même (la couleur de la fenêtre de Plasma reste trop transparente)
    backgroundColor: "transparent"
    keyEventProxy: champ

    onKeyEscapePressed: fenetre.toggle()

    onVisibleChanged: {
        champ.text = ""
        if (visible) {
            ouverture.restart()
            filtre = "Toutes"
            trouverApplications()
            construireSections()
            champ.forceActiveFocus()
        }
    }

    // Le modèle « toutes les applications » apparaît après le chargement du module kicker
    function trouverApplications() {
        if (!modeleRacine) return
        for (var i = 0; i < modeleRacine.count; ++i) {
            var m = modeleRacine.modelForRow(i)
            if (m && m.description === "KICKER_ALL_MODEL") {
                modeleApps = m
                return
            }
        }
        if (!modeleApps && modeleRacine.count > 0) {
            modeleApps = modeleRacine.modelForRow(0)
        }
        if (!modeleApps) fenetre.relance.restart()
    }
    // (déclaré comme propriété : cette fenêtre n'accepte que des éléments visuels comme enfants)
    property Timer relance: Timer { interval: 500; onTriggered: fenetre.trouverApplications() }

    function lancer(modele, index) {
        if (modele) {
            modele.trigger(index, "", null)
        }
        fenetre.toggle()
    }

    mainItem: MouseArea {
        id: fond
        anchors.fill: parent
        onClicked: fenetre.toggle()

        // Ouverture : la Cour apparaît en fondu, son contenu monte légèrement (Qt Quick : ne dépend pas de la carte graphique)
        ParallelAnimation {
            id: ouverture
            NumberAnimation { target: fond; property: "opacity"; from: 0; to: 1; duration: 180; easing.type: Easing.OutCubic }
            NumberAnimation { target: contenuCour; property: "scale"; from: 0.97; to: 1; duration: 260; easing.type: Easing.OutCubic }
            NumberAnimation { target: decalageCour; property: "y"; from: 18; to: 0; duration: 260; easing.type: Easing.OutCubic }
        }

        // Fond plein : sans flou disponible, la moindre transparence laisse voir le texte des fenêtres
        Rectangle {
            anchors.fill: parent
            color: fenetre.sombre ? "#151A2B" : "#F3ECE2"
        }

        ColumnLayout {
            id: contenuCour
            transform: Translate { id: decalageCour }
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: parent.top
            anchors.topMargin: parent.height * 0.1
            anchors.bottom: parent.bottom
            anchors.bottomMargin: Kirigami.Units.gridUnit * 6
            width: Math.min(parent.width - Kirigami.Units.gridUnit * 4, Kirigami.Units.gridUnit * 52)
            spacing: Kirigami.Units.gridUnit * 2

            // Barre de recherche (maquette bur-01 : 620 × 56, liseré latérite quand on tape)
            Rectangle {
                id: barre
                Layout.alignment: Qt.AlignHCenter
                Layout.preferredWidth: Math.min(parent.width, 620)
                Layout.preferredHeight: 56
                radius: height / 2
                color: fenetre.couleurPanneau
                border.width: champ.activeFocus ? 2 : 0
                border.color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.22)

                MouseArea { anchors.fill: parent; onClicked: champ.forceActiveFocus() }

                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 22
                    anchors.rightMargin: 16
                    spacing: 14

                    Kirigami.Icon {
                        Layout.preferredWidth: 20
                        Layout.preferredHeight: 20
                        source: "search"
                        color: fenetre.texte2
                    }
                    QQC2.TextField {
                        id: champ
                        Layout.fillWidth: true
                        background: null
                        font.pixelSize: 16
                        color: Kirigami.Theme.textColor
                        placeholderText: "Rechercher des applications, des fichiers, des réglages"
                        placeholderTextColor: fenetre.texte3
                        onTextChanged: {
                            if (fenetre.modeleRecherche) fenetre.modeleRecherche.query = text
                            fenetre.choix = 0
                            fenetre.regroupement.restart()
                        }
                        function valider() {
                            if (fenetre.recherche) {
                                fenetre.lancerChoix()
                            } else if (fenetre.appsAffichees.length > 0) {
                                var premiere = fenetre.appsAffichees[0]
                                fenetre.lancer(premiere.modele, premiere.ligne)
                            }
                        }
                        Keys.onReturnPressed: valider()
                        Keys.onEnterPressed: valider()
                        Keys.onDownPressed: {
                            if (fenetre.recherche) fenetre.choix = Math.min(fenetre.choix + 1, fenetre.totalResultats - 1)
                            else defilement.contentY = Math.min(defilement.contentY + 120, Math.max(0, defilement.contentHeight - defilement.height))
                        }
                        Keys.onUpPressed: {
                            if (fenetre.recherche) fenetre.choix = Math.max(0, fenetre.choix - 1)
                            else defilement.contentY = Math.max(0, defilement.contentY - 120)
                        }
                    }
                    Text {
                        visible: fenetre.recherche && fenetre.totalResultats > 0
                        text: fenetre.totalResultats + (fenetre.totalResultats > 1 ? " résultats" : " résultat")
                        font.pixelSize: 12
                        color: fenetre.texte3
                    }
                    // Effacer
                    MouseArea {
                        visible: fenetre.recherche
                        Layout.preferredWidth: 24
                        Layout.preferredHeight: 24
                        cursorShape: Qt.PointingHandCursor
                        onClicked: { champ.text = ""; champ.forceActiveFocus() }
                        Rectangle {
                            anchors.fill: parent
                            radius: 12
                            color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.09)
                        }
                        Text { anchors.centerIn: parent; text: "×"; font.pixelSize: 15; color: fenetre.texte2 }
                    }
                }
            }

            // Résultats, rangés par groupe : Applications, Fichiers, Réglages, Actions
            Rectangle {
                id: panneauResultats
                visible: fenetre.recherche
                Layout.alignment: Qt.AlignHCenter
                Layout.preferredWidth: Math.min(parent.width, 620)
                Layout.preferredHeight: Math.min(contenuResultats.implicitHeight + 10 + piedResultats.height, fond.height * 0.66)
                Layout.topMargin: 14 - parent.spacing
                radius: 20
                color: fenetre.couleurPanneau
                clip: true

                Flickable {
                    id: defilementResultats
                    anchors.top: parent.top
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.bottom: piedResultats.top
                    anchors.topMargin: 10
                    anchors.leftMargin: 10
                    anchors.rightMargin: 10
                    contentHeight: contenuResultats.implicitHeight
                    clip: true
                    boundsBehavior: Flickable.StopAtBounds
                    function montrer(element) {
                        var p = element.mapToItem(contenuResultats, 0, 0)
                        if (p.y < contentY) contentY = p.y
                        else if (p.y + element.height > contentY + height) contentY = p.y + element.height - height
                    }

                    Column {
                        id: contenuResultats
                        width: parent.width
                        spacing: 2

                        // Rien du tout
                        Column {
                            visible: fenetre.totalResultats === 0
                            width: parent.width
                            topPadding: 18
                            bottomPadding: 18
                            spacing: 4
                            Text {
                                anchors.horizontalCenter: parent.horizontalCenter
                                text: "Aucun résultat pour « " + champ.text + " »"
                                font.pixelSize: 14
                                font.weight: Font.Medium
                                color: Kirigami.Theme.textColor
                            }
                            Text {
                                anchors.horizontalCenter: parent.horizontalCenter
                                text: "Vérifiez l'orthographe ou essayez un autre mot"
                                font.pixelSize: 12
                                color: fenetre.texte3
                            }
                        }

                        Repeater {
                            model: fenetre.totalResultats > 0 ? fenetre.groupes : []
                            delegate: Column {
                                id: groupe
                                required property var modelData
                                width: contenuResultats.width
                                spacing: 2
                                Text {
                                    text: groupe.modelData.titre.toUpperCase()
                                    leftPadding: 12
                                    topPadding: 10
                                    bottomPadding: 4
                                    font.pixelSize: 11
                                    font.weight: Font.DemiBold
                                    font.letterSpacing: 0.3
                                    color: fenetre.texte3
                                }
                                Text {
                                    visible: groupe.modelData.sources.length === 0
                                    height: 36
                                    leftPadding: 12
                                    verticalAlignment: Text.AlignVCenter
                                    text: "Aucune application ne correspond à « " + champ.text + " »"
                                    font.pixelSize: 13
                                    color: fenetre.texte3
                                }
                                Repeater {
                                    model: groupe.modelData.sources
                                    delegate: Column {
                                        id: blocSource
                                        required property var modelData
                                        width: contenuResultats.width
                                        spacing: 2
                                        Repeater {
                                            model: blocSource.modelData.modele ? blocSource.modelData.modele : blocSource.modelData.reglages
                                            delegate: Resultat {
                                                width: contenuResultats.width
                                                origine: blocSource.modelData
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Item { width: 1; height: 8 }
                    }
                }

                // Aide clavier
                Rectangle {
                    id: piedResultats
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.bottom: parent.bottom
                    height: 40
                    // Coins du bas arrondis comme le panneau (le rectangle du haut cache les coins supérieurs)
                    radius: 20
                    color: fenetre.sombre ? "#262B3C" : "#F6F3EF"
                    Rectangle { anchors.top: parent.top; width: parent.width; height: 20; color: parent.color }
                    Rectangle { anchors.top: parent.top; width: parent.width; height: 1; color: fenetre.trait }
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 22
                        anchors.rightMargin: 22
                        spacing: 6
                        Touche { text: "Entrée" }
                        Text { text: "pour ouvrir"; font.pixelSize: 12; color: fenetre.texte2; Layout.rightMargin: 12 }
                        Touche { text: "↑" }
                        Touche { text: "↓" }
                        Text { text: "pour naviguer"; font.pixelSize: 12; color: fenetre.texte2 }
                        Item { Layout.fillWidth: true }
                        Touche { text: "Échap" }
                        Text { text: "pour fermer"; font.pixelSize: 12; color: fenetre.texte2 }
                    }
                }
            }

            // En recherche, l'espace libre reste sous les résultats (la barre garde sa place en haut)
            Item {
                visible: fenetre.recherche
                Layout.fillHeight: true
            }

            // Pastilles de tri, comme le Launchpad de macOS
            Row {
                visible: !fenetre.recherche && fenetre.sections.length > 1
                Layout.alignment: Qt.AlignHCenter
                spacing: 8
                Repeater {
                    model: ["Toutes"].concat(fenetre.sections.map(function (x) { return x.titre }))
                    delegate: MouseArea {
                        id: pastille
                        readonly property bool choisie: fenetre.filtre === modelData
                        width: libelle.implicitWidth + 28
                        height: 30
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: fenetre.filtre = modelData
                        Rectangle {
                            anchors.fill: parent
                            radius: height / 2
                            color: pastille.choisie ? "#B5532F"
                                 : Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b,
                                           pastille.containsMouse ? 0.12 : 0.06)
                        }
                        Text {
                            id: libelle
                            anchors.centerIn: parent
                            text: modelData
                            font.pixelSize: 13
                            font.weight: pastille.choisie ? Font.DemiBold : Font.Medium
                            color: pastille.choisie ? "#FFFFFF" : Kirigami.Theme.textColor
                        }
                    }
                }
            }

            // Applications (toutes, ou la catégorie choisie)
            Flickable {
                id: defilement
                visible: !fenetre.recherche
                Layout.fillWidth: true
                Layout.fillHeight: true
                contentHeight: colonneSections.implicitHeight
                clip: true
                boundsBehavior: Flickable.StopAtBounds

                readonly property int colonnes: Math.max(4, Math.floor(width / (Kirigami.Units.gridUnit * 8)))
                readonly property real largeurTuile: Math.floor(width / colonnes)

                // Une seule grille, comme avant : les pastilles ne font que filtrer
                Flow {
                    id: colonneSections
                    width: parent.width
                    Repeater {
                        model: fenetre.appsAffichees
                        delegate: MouseArea {
                            id: tuile
                            width: defilement.largeurTuile
                            height: Kirigami.Units.gridUnit * 8
                            hoverEnabled: true
                            onClicked: fenetre.lancer(modelData.modele, modelData.ligne)

                            Rectangle {
                                anchors.fill: parent
                                anchors.margins: Kirigami.Units.smallSpacing
                                radius: Kirigami.Units.gridUnit
                                color: Kirigami.Theme.textColor
                                opacity: tuile.containsMouse ? 0.07 : 0
                            }
                            ColumnLayout {
                                anchors.centerIn: parent
                                width: parent.width - Kirigami.Units.largeSpacing * 2
                                spacing: Kirigami.Units.largeSpacing
                                Kirigami.Icon {
                                    Layout.alignment: Qt.AlignHCenter
                                    Layout.preferredWidth: Kirigami.Units.iconSizes.huge
                                    Layout.preferredHeight: Layout.preferredWidth
                                    source: modelData.modele.data(modelData.modele.index(modelData.ligne, 0), Qt.DecorationRole)
                                    scale: tuile.pressed ? 0.94 : 1
                                }
                                Text {
                                    Layout.fillWidth: true
                                    text: modelData.nom
                                    horizontalAlignment: Text.AlignHCenter
                                    elide: Text.ElideRight
                                    maximumLineCount: 2
                                    wrapMode: Text.WordWrap
                                    font.weight: Font.Medium
                                    color: Kirigami.Theme.textColor
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Ligne de résultat (maquette : 48 px, tuile 30 px, partie tapée en gras ; « Ouvrir ↵ » sur la ligne choisie)
    // (pas de propriétés « required » : index, model et modelData viennent du Repeater, que le modèle soit
    //  une liste de réglages ou les résultats d'un module de recherche)
    component Resultat: MouseArea {
        id: resultat
        property var origine
        readonly property int rang: index
        readonly property bool reglage: !origine.modele
        readonly property var element: reglage ? modelData : null
        readonly property int position: origine.debut + rang
        readonly property bool selectionne: fenetre.choix === position
        readonly property string titre: reglage ? element.titre : String(model.display || "")
        readonly property string detail: reglage ? element.detail : String(model.description || "")
        readonly property var icone: reglage ? "" : model.decoration
        visible: rang < origine.nombre
        height: visible ? 48 : 0
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onClicked: { fenetre.choix = position; fenetre.lancerChoix() }
        onSelectionneChanged: if (selectionne) defilementResultats.montrer(resultat)

        Rectangle {
            anchors.fill: parent
            radius: 12
            color: resultat.selectionne ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, fenetre.sombre ? 0.2 : 0.1)
                 : Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, resultat.containsMouse ? 0.05 : 0)
        }
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 12
            anchors.rightMargin: 12
            spacing: 12
            Item {
                Layout.preferredWidth: 30
                Layout.preferredHeight: 30
                // Réglage : tuile grise et pictogramme des Réglages
                Rectangle {
                    visible: resultat.reglage
                    anchors.fill: parent
                    radius: 9
                    color: "#7D766C"
                    Canvas {
                        anchors.centerIn: parent
                        width: 16
                        height: 16
                        onPaint: {
                            var c = getContext("2d")
                            c.reset()
                            c.scale(16 / 24, 16 / 24)
                            c.strokeStyle = "#FBF8F3"
                            c.lineWidth = 1.8
                            c.lineCap = "round"
                            c.lineJoin = "round"
                            c.path = resultat.reglage ? resultat.element.picto : ""
                            c.stroke()
                        }
                    }
                }
                Kirigami.Icon {
                    visible: !resultat.reglage
                    anchors.fill: parent
                    source: resultat.icone
                }
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 1
                Text {
                    Layout.fillWidth: true
                    textFormat: Text.StyledText
                    text: fenetre.surligner(resultat.titre, champ.text)
                    elide: Text.ElideRight
                    font.pixelSize: 14
                    color: Kirigami.Theme.textColor
                }
                Text {
                    Layout.fillWidth: true
                    visible: resultat.detail !== "" && resultat.detail !== resultat.titre
                    textFormat: Text.StyledText
                    text: fenetre.surligner(resultat.detail, champ.text)
                    elide: Text.ElideRight
                    font.pixelSize: 12
                    color: fenetre.texte2
                }
            }
            Text { visible: resultat.selectionne; text: "Ouvrir"; font.pixelSize: 12; font.weight: Font.Medium; color: fenetre.encre }
            Touche { visible: resultat.selectionne; text: "↵" }
            Text { visible: !resultat.selectionne && resultat.reglage; text: "›"; font.pixelSize: 18; color: fenetre.texte3 }
        }
    }

    // Touche de clavier dessinée (aide en bas des résultats)
    component Touche: Rectangle {
        property alias text: libelleTouche.text
        implicitWidth: Math.max(20, libelleTouche.implicitWidth + 12)
        implicitHeight: 20
        radius: 5
        color: fenetre.sombre ? "#2B3044" : "#FFFFFF"
        border.width: 1
        border.color: fenetre.trait
        Text { id: libelleTouche; anchors.centerIn: parent; font.pixelSize: 11; font.weight: Font.DemiBold; color: fenetre.texte2 }
    }
}
