// Fenêtre plein écran de la Cour : barre de recherche, puis toutes les applications
// en grille, ou les résultats de recherche quand on tape.
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

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property bool recherche: champ.text.length > 0
    readonly property var resultats: recherche && modeleRecherche && modeleRecherche.count > 0
                                     ? modeleRecherche.modelForRow(0) : null

    backgroundColor: sombre ? Qt.rgba(0.063, 0.075, 0.118, 0.84) : Qt.rgba(0.953, 0.925, 0.886, 0.88)
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
            trouverApplications()
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
                            } else if (grille.count > 0) {
                                fenetre.lancer(fenetre.modeleApps, Math.max(0, grille.currentIndex))
                            }
                        }
                        Keys.onDownPressed: {
                            if (fenetre.recherche) listeResultats.forceActiveFocus()
                            else { grille.currentIndex = 0; grille.forceActiveFocus() }
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

            // Toutes les applications
            GridView {
                id: grille
                visible: !fenetre.recherche
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                model: fenetre.modeleApps
                currentIndex: -1
                keyNavigationWraps: true
                highlightMoveDuration: 0

                readonly property int colonnes: Math.max(4, Math.floor(width / (Kirigami.Units.gridUnit * 8)))
                cellWidth: Math.floor(width / colonnes)
                cellHeight: Kirigami.Units.gridUnit * 8

                Keys.onReturnPressed: fenetre.lancer(model, currentIndex)

                delegate: MouseArea {
                    id: tuile
                    width: grille.cellWidth
                    height: grille.cellHeight
                    hoverEnabled: true
                    onClicked: fenetre.lancer(grille.model, index)

                    Rectangle {
                        anchors.fill: parent
                        anchors.margins: Kirigami.Units.smallSpacing
                        radius: Kirigami.Units.gridUnit
                        color: Kirigami.Theme.textColor
                        opacity: tuile.containsMouse || (tuile.GridView.isCurrentItem && grille.activeFocus) ? 0.07 : 0
                    }
                    ColumnLayout {
                        anchors.centerIn: parent
                        width: parent.width - Kirigami.Units.largeSpacing * 2
                        spacing: Kirigami.Units.largeSpacing

                        Kirigami.Icon {
                            Layout.alignment: Qt.AlignHCenter
                            Layout.preferredWidth: Kirigami.Units.iconSizes.huge
                            Layout.preferredHeight: Layout.preferredWidth
                            source: model.decoration
                            scale: tuile.pressed ? 0.94 : 1
                        }
                        Text {
                            Layout.fillWidth: true
                            text: model.display
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
