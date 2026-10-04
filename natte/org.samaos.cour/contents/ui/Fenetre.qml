// Fenêtre plein écran de la Cour : barre de recherche, puis les applications rangées
// par sections (Essentiels, Bureautique, Gestion, Outils, Système…), ou les résultats de recherche quand on tape.
// Échap efface la recherche, puis ferme la Cour. Entrée lance le premier résultat.

import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.kirigami as Kirigami
import org.kde.plasma.private.kicker as Kicker

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
        "org.kde.ark.desktop": "Outils", "org.kde.spectacle.desktop": "Outils",
        "org.kde.konsole.desktop": "Outils", "org.kde.plasma-systemmonitor.desktop": "Outils",
        "systemsettings.desktop": "Système", "org.kde.khelpcenter.desktop": "Système"
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
    readonly property var resultats: recherche && modeleRecherche && modeleRecherche.count > 0
                                     ? modeleRecherche.modelForRow(0) : null

    // Fond dessiné par la Cour elle-même (la couleur de la fenêtre de Plasma reste trop transparente)
    backgroundColor: "transparent"
    keyEventProxy: champ

    onKeyEscapePressed: {
        if (recherche) {
            champ.text = ""
        } else {
            fenetre.toggle()
        }
    }

    onVisibleChanged: {
        champ.text = ""
        if (visible) {
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

        // Fond plein : sans flou disponible, la moindre transparence laisse voir le texte des fenêtres
        Rectangle {
            anchors.fill: parent
            color: fenetre.sombre ? "#151A2B" : "#F3ECE2"
        }

        ColumnLayout {
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: parent.top
            anchors.topMargin: parent.height * 0.1
            anchors.bottom: parent.bottom
            anchors.bottomMargin: Kirigami.Units.gridUnit * 6
            width: Math.min(parent.width - Kirigami.Units.gridUnit * 4, Kirigami.Units.gridUnit * 52)
            spacing: Kirigami.Units.gridUnit * 2

            // Barre de recherche
            Rectangle {
                Layout.alignment: Qt.AlignHCenter
                Layout.preferredWidth: Math.min(parent.width, Kirigami.Units.gridUnit * 34)
                Layout.preferredHeight: Kirigami.Units.gridUnit * 3
                radius: height / 2
                color: Kirigami.Theme.backgroundColor

                MouseArea { anchors.fill: parent; onClicked: champ.forceActiveFocus() }

                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: Kirigami.Units.gridUnit * 1.2
                    anchors.rightMargin: Kirigami.Units.gridUnit * 1.2
                    spacing: Kirigami.Units.largeSpacing

                    Kirigami.Icon {
                        Layout.preferredWidth: Kirigami.Units.iconSizes.smallMedium
                        Layout.preferredHeight: Layout.preferredWidth
                        source: "search"
                        color: Kirigami.Theme.disabledTextColor
                    }
                    QQC2.TextField {
                        id: champ
                        Layout.fillWidth: true
                        background: null
                        font.pixelSize: Kirigami.Theme.defaultFont.pixelSize * 1.25
                        placeholderText: "Rechercher des applications, des fichiers, des réglages"
                        onTextChanged: if (fenetre.modeleRecherche) fenetre.modeleRecherche.query = text
                        Keys.onReturnPressed: {
                            if (fenetre.recherche) {
                                if (fenetre.resultats && fenetre.resultats.count > 0) fenetre.lancer(fenetre.resultats, 0)
                            } else if (fenetre.appsAffichees.length > 0) {
                                var premiere = fenetre.appsAffichees[0]
                                fenetre.lancer(premiere.modele, premiere.ligne)
                            }
                        }
                        Keys.onDownPressed: {
                            if (fenetre.recherche) listeResultats.forceActiveFocus()
                            else defilement.contentY = Math.min(defilement.contentY + 120, Math.max(0, defilement.contentHeight - defilement.height))
                        }
                    }
                }
            }

            // Résultats de recherche
            Rectangle {
                visible: fenetre.recherche
                Layout.alignment: Qt.AlignHCenter
                Layout.preferredWidth: Math.min(parent.width, Kirigami.Units.gridUnit * 34)
                Layout.preferredHeight: Math.min(listeResultats.contentHeight + Kirigami.Units.largeSpacing * 2,
                                                 Kirigami.Units.gridUnit * 26)
                radius: Kirigami.Units.gridUnit * 1.2
                color: Kirigami.Theme.backgroundColor

                Text {
                    anchors.centerIn: parent
                    visible: !fenetre.resultats || fenetre.resultats.count === 0
                    text: "Aucun résultat"
                    color: Kirigami.Theme.disabledTextColor
                }

                ListView {
                    id: listeResultats
                    anchors.fill: parent
                    anchors.margins: Kirigami.Units.largeSpacing
                    clip: true
                    model: fenetre.resultats
                    currentIndex: 0
                    keyNavigationWraps: true
                    highlightMoveDuration: 0
                    Keys.onReturnPressed: fenetre.lancer(model, currentIndex)
                    Keys.onUpPressed: currentIndex > 0 ? decrementCurrentIndex() : champ.forceActiveFocus()

                    delegate: MouseArea {
                        id: ligne
                        width: ListView.view.width
                        height: Kirigami.Units.gridUnit * 2.6
                        hoverEnabled: true
                        onClicked: fenetre.lancer(ListView.view.model, index)

                        Rectangle {
                            anchors.fill: parent
                            radius: Kirigami.Units.gridUnit * 0.7
                            color: Kirigami.Theme.textColor
                            opacity: ligne.containsMouse || (ligne.ListView.isCurrentItem && listeResultats.activeFocus) ? 0.08 : 0
                        }
                        RowLayout {
                            anchors.fill: parent
                            anchors.leftMargin: Kirigami.Units.largeSpacing
                            anchors.rightMargin: Kirigami.Units.largeSpacing
                            spacing: Kirigami.Units.largeSpacing
                            Kirigami.Icon {
                                Layout.preferredWidth: Kirigami.Units.iconSizes.medium
                                Layout.preferredHeight: Layout.preferredWidth
                                source: model.decoration
                            }
                            Text {
                                Layout.fillWidth: true
                                text: model.display
                                elide: Text.ElideRight
                                font.pixelSize: Kirigami.Theme.defaultFont.pixelSize * 1.05
                                color: Kirigami.Theme.textColor
                            }
                            Text {
                                text: model.description || ""
                                visible: text.length > 0
                                elide: Text.ElideRight
                                Layout.maximumWidth: Kirigami.Units.gridUnit * 12
                                font.pixelSize: Kirigami.Theme.smallFont.pixelSize
                                color: Kirigami.Theme.disabledTextColor
                            }
                        }
                    }
                }
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
}
