/*
    Galerie de widgets de Sama OS (remplace le panneau « Ajouter des widgets » de Plasma, même mécanique).
    D'après l'écran « Galerie de widgets » de la maquette : fenêtre centrée, catégories à gauche,
    aperçus des widgets Sama avec un « + » latérite ; les widgets de KDE restent accessibles dans « Plus de widgets ».

    Basé sur WidgetExplorer.qml de Plasma — SPDX-FileCopyrightText: 2011 Marco Martin <mart@kde.org>
    SPDX-License-Identifier: LGPL-2.0-or-later
*/

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2

import org.kde.plasma.components 3.0 as PC3
import org.kde.plasma.core as PlasmaCore
import org.kde.kwindowsystem 1.0
import org.kde.kirigami 2.20 as Kirigami
import org.kde.plasma.private.shell 2.0

PC3.Page {
    id: main

    width: 900
    height: 720

    // Propriétés attendues par Plasma
    property QtObject containment
    property PlasmaCore.Dialog sidePanel
    property bool draggingWidget: false
    property bool preventWindowHide: draggingWidget
    property bool outputOnly: draggingWidget
    property Item categoryButton
    signal closed()

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color texte: Kirigami.Theme.textColor
    readonly property color texte2: Kirigami.Theme.disabledTextColor
    readonly property color champ: sombre ? Qt.rgba(1, 1, 1, 0.06) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
    readonly property color ligne: sombre ? Qt.rgba(1, 1, 1, 0.08) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
    readonly property color carte: sombre ? "#262B3D" : "#FFFFFF"

    background: Rectangle { color: "transparent" }

    // Widgets Sama : catégorie, taille, aperçu ; « bientot » quand le widget n'existe pas encore
    readonly property var catalogue: [
        { plugin: "org.samaos.carte.heure", nom: "Heure et salutation", taille: "Moyen", categorie: "Informations", apercu: "heure" },
        { plugin: "org.samaos.carte.data", nom: "Data depuis l'allumage", taille: "Moyen", categorie: "Data et énergie", apercu: "data" },
        { plugin: "", nom: "Météo", taille: "Moyen", categorie: "Informations", apercu: "meteo", bientot: true },
        { plugin: "", nom: "Agenda", taille: "Moyen", categorie: "Productivité", apercu: "agenda", bientot: true }
    ]
    readonly property var categories: ["Tous", "Productivité", "Data et énergie", "Informations"]
    property string categorie: "Tous"
    property string recherche: ""

    readonly property var visibles: {
        var r = []
        for (var i = 0; i < catalogue.length; i++) {
            var w = catalogue[i]
            if (categorie !== "Tous" && categorie !== "Plus de widgets" && w.categorie !== categorie) continue
            if (recherche && w.nom.toLowerCase().indexOf(recherche.toLowerCase()) < 0) continue
            r.push(w)
        }
        return r
    }
    function nombre(cat) {
        var n = 0
        for (var i = 0; i < catalogue.length; i++) if (cat === "Tous" || catalogue[i].categorie === cat) n++
        return n
    }

    onClosed: {
        if (root.widgetExplorer.containment && root.widgetExplorer.containment.containmentType == 1
            && !root.widgetExplorer.containment.userConfiguring) {
            root.widgetExplorer.containment.internalAction("configure").trigger()
        }
    }
    onVisibleChanged: if (!visible) KWindowSystem.showingDesktop = false

    Component.onCompleted: {
        if (!root.widgetExplorer) root.widgetExplorer = explorateurComposant.createObject(root)
        // Ouverte sans bureau précis (raccourci, commande) : le bureau de cet écran
        root.widgetExplorer.containment = main.containment || (root.containment ? root.containment.plasmoid : null)
        centrer.start()
    }
    Component.onDestruction: {
        if (root.widgetExplorer) {
            root.widgetExplorer.widgetsModel.searchTerm = ""
            root.widgetExplorer.destroy()
            root.widgetExplorer = null
        }
    }
    // La galerie est une fenêtre centrée, pas un panneau collé au bord gauche
    Timer {
        id: centrer
        interval: 1
        onTriggered: {
            if (!main.sidePanel) return
            main.sidePanel.location = PlasmaCore.Types.Floating
            // Plasma impose toute la hauteur de l'écran : fenêtre transparente, la carte est dessinée au centre
            main.sidePanel.backgroundHints = PlasmaCore.Types.NoBackground
            var s = main.Window.window ? main.Window.window.screen : null
            var gx = s ? s.virtualX : 0
            var gy = s ? s.virtualY : 0
            var lw = s ? s.width : Screen.width
            var lh = s ? s.height : Screen.height
            main.sidePanel.x = gx + Math.round((lw - main.sidePanel.width) / 2)
            main.sidePanel.y = gy
        }
    }

    function ajouter(plugin) {
        if (plugin) root.widgetExplorer.addApplet(plugin)
    }

    QQC2.Action {
        shortcut: "Escape"
        onTriggered: champRecherche.text.length > 0 ? champRecherche.text = "" : main.closed()
    }

    Component {
        id: explorateurComposant
        WidgetExplorer { onShouldClose: main.closed() }
    }

    // Aperçu dessiné d'un widget Sama
    component Apercu: Item {
        id: apercu
        property string type
        Rectangle {
            anchors.fill: parent
            radius: 18
            color: main.carte
            border.width: 1
            border.color: main.ligne
        }
        // Heure et salutation
        Column {
            visible: apercu.type === "heure"
            anchors.left: parent.left
            anchors.verticalCenter: parent.verticalCenter
            anchors.leftMargin: 18
            spacing: 2
            Text { text: Qt.formatTime(new Date(), "hh:mm"); font.pixelSize: 34; font.weight: Font.Light; color: main.texte }
            Text { text: new Date().toLocaleDateString(Qt.locale(), "dddd d MMMM"); font.pixelSize: 11; color: main.texte2 }
            Text { text: "Bonjour"; topPadding: 6; font.pixelSize: 13; font.weight: Font.Medium; color: main.texte }
        }
        // Data
        Row {
            visible: apercu.type === "data"
            anchors.left: parent.left
            anchors.verticalCenter: parent.verticalCenter
            anchors.leftMargin: 18
            spacing: 14
            Rectangle {
                width: 52; height: 52; radius: 26
                color: "transparent"
                border.width: 5
                border.color: Qt.rgba(main.texte.r, main.texte.g, main.texte.b, 0.12)
                Text { anchors.centerIn: parent; text: "31 %"; font.pixelSize: 11; font.weight: Font.DemiBold; color: main.texte }
            }
            Column {
                anchors.verticalCenter: parent.verticalCenter
                Text { text: "Data depuis l'allumage"; font.pixelSize: 11; color: main.texte2 }
                Text { text: "312 Mo"; font.pixelSize: 17; font.weight: Font.Medium; color: main.texte }
                Text { text: "sur 1 Go"; font.pixelSize: 11; color: main.texte2 }
            }
        }
        // Météo
        RowLayout {
            visible: apercu.type === "meteo"
            anchors.fill: parent
            anchors.leftMargin: 18
            anchors.rightMargin: 18
            Rectangle { width: 36; height: 36; radius: 18; color: "#E2A62B" }
            Column {
                Layout.fillWidth: true
                Text { text: "Météo"; font.pixelSize: 11; color: main.texte2 }
                Text { text: "Ensoleillé"; font.pixelSize: 12; color: main.texte2 }
            }
            Text { text: "31°"; font.pixelSize: 30; font.weight: Font.Light; color: main.texte }
        }
        // Agenda
        Column {
            visible: apercu.type === "agenda"
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            anchors.leftMargin: 18
            spacing: 8
            Text { text: "Agenda"; font.pixelSize: 11; color: main.texte2 }
            Row {
                spacing: 10
                Rectangle { width: 3; height: 30; radius: 2; color: "#B5532F" }
                Column {
                    Text { text: "Réunion de service"; font.pixelSize: 12; font.weight: Font.Medium; color: main.texte }
                    Text { text: "09:00 – 10:00"; font.pixelSize: 11; color: main.texte2 }
                }
            }
        }
    }

    // Clic hors de la carte : fermer
    MouseArea {
        anchors.fill: parent
        onClicked: main.closed()
    }

    // Fenêtre de la galerie
    Rectangle {
        anchors.centerIn: parent
        width: parent.width
        height: Math.min(720, parent.height - 80)
        radius: 24
        color: main.sombre ? "#1E2233" : "#FCFAF7"
        border.width: 1
        border.color: main.ligne
        // La carte elle-même ne ferme pas la galerie
        MouseArea { anchors.fill: parent }

        ColumnLayout {
            anchors.fill: parent
            spacing: 0

            // En-tête
            RowLayout {
                Layout.fillWidth: true
                Layout.preferredHeight: 76
                Layout.leftMargin: 28
                Layout.rightMargin: 18
                spacing: 16
                Column {
                    Layout.fillWidth: true
                    spacing: 2
                    Text { text: "Ajouter des widgets"; font.pixelSize: 22; font.weight: Font.Medium; color: main.texte }
                    Text { text: "Choisissez un widget : il s'ajoute sur le bureau, où vous pourrez le déplacer."; font.pixelSize: 13; color: main.texte2 }
                }
                Rectangle {
                    Layout.preferredWidth: 240
                    Layout.preferredHeight: 36
                    radius: 10
                    color: main.champ
                    QQC2.TextField {
                        id: champRecherche
                        anchors.fill: parent
                        leftPadding: 12
                        background: null
                        font.pixelSize: 13
                        color: main.texte
                        placeholderText: "Rechercher un widget"
                        placeholderTextColor: main.texte2
                        onTextChanged: {
                            main.recherche = text
                            root.widgetExplorer.widgetsModel.searchTerm = text
                        }
                        Component.onCompleted: forceActiveFocus()
                    }
                }
                MouseArea {
                    Layout.preferredWidth: 32
                    Layout.preferredHeight: 32
                    hoverEnabled: true
                    onClicked: main.closed()
                    Rectangle { anchors.fill: parent; radius: 16; color: main.champ }
                    Text { anchors.centerIn: parent; text: "×"; font.pixelSize: 18; color: main.texte2 }
                }
            }
            Rectangle { Layout.fillWidth: true; Layout.preferredHeight: 1; color: main.ligne }

            RowLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 0

                // Catégories
                ColumnLayout {
                    Layout.preferredWidth: 170
                    Layout.minimumWidth: 170
                    Layout.maximumWidth: 170
                    Layout.fillWidth: false
                    Layout.fillHeight: true
                    Layout.margins: 10
                    Layout.topMargin: 14
                    spacing: 2

                    Repeater {
                        model: main.categories.concat(["Plus de widgets"])
                        delegate: MouseArea {
                            id: entree
                            readonly property bool choisie: main.categorie === modelData
                            Layout.fillWidth: true
                            Layout.preferredHeight: 34
                            hoverEnabled: true
                            onClicked: main.categorie = modelData
                            Rectangle {
                                anchors.fill: parent
                                radius: 9
                                color: entree.choisie ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.12)
                                     : (entree.containsMouse ? main.champ : "transparent")
                            }
                            RowLayout {
                                anchors.fill: parent
                                anchors.leftMargin: 12
                                anchors.rightMargin: 12
                                Text {
                                    Layout.fillWidth: true
                                    text: modelData
                                    font.pixelSize: 13
                                    font.weight: entree.choisie ? Font.DemiBold : Font.Normal
                                    color: main.texte
                                }
                                Text {
                                    visible: modelData !== "Plus de widgets"
                                    text: main.nombre(modelData)
                                    font.pixelSize: 11
                                    color: entree.choisie ? (main.sombre ? "#F0B392" : "#93401F") : main.texte2
                                }
                            }
                        }
                    }
                    Item { Layout.fillHeight: true }
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: note.implicitHeight + 24
                        radius: 12
                        color: main.champ
                        Text {
                            id: note
                            anchors.fill: parent
                            anchors.margins: 12
                            wrapMode: Text.WordWrap
                            lineHeight: 1.3
                            text: "Les widgets Sama fonctionnent hors ligne et se mettent à jour quand la connexion revient."
                            font.pixelSize: 12
                            color: main.texte2
                        }
                    }
                }
                Rectangle { Layout.fillHeight: true; Layout.preferredWidth: 1; color: main.ligne }

                // Widgets Sama
                Flickable {
                    visible: main.categorie !== "Plus de widgets"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    contentHeight: grille.implicitHeight + 40
                    clip: true
                    boundsBehavior: Flickable.StopAtBounds

                    Grid {
                        id: grille
                        x: 24
                        y: 20
                        width: parent.width - 48
                        columns: 2
                        columnSpacing: 18
                        rowSpacing: 18

                        Repeater {
                            model: main.visibles
                            delegate: Column {
                                width: (grille.width - grille.columnSpacing) / 2
                                spacing: 8
                                Item {
                                    width: parent.width
                                    height: 136
                                    opacity: modelData.bientot ? 0.55 : 1
                                    Apercu {
                                        anchors.fill: parent
                                        type: modelData.apercu
                                    }
                                    // Ajouter
                                    MouseArea {
                                        anchors.fill: parent
                                        enabled: !modelData.bientot
                                        cursorShape: enabled ? Qt.PointingHandCursor : Qt.ArrowCursor
                                        onClicked: main.ajouter(modelData.plugin)
                                    }
                                    Rectangle {
                                        visible: !modelData.bientot
                                        x: -7
                                        y: -7
                                        width: 26
                                        height: 26
                                        radius: 13
                                        color: "#B5532F"
                                        Text { anchors.centerIn: parent; anchors.verticalCenterOffset: -1; text: "+"; font.pixelSize: 18; color: "#FFFFFF" }
                                    }
                                }
                                Text {
                                    text: modelData.nom + "  · " + (modelData.bientot ? "Bientôt" : modelData.taille)
                                    font.pixelSize: 12
                                    font.weight: Font.Medium
                                    color: main.texte
                                }
                            }
                        }
                    }
                    Text {
                        visible: main.visibles.length === 0
                        anchors.centerIn: parent
                        text: "Aucun widget ne correspond"
                        font.pixelSize: 13
                        color: main.texte2
                    }
                }

                // Autres widgets (KDE), en liste simple
                ListView {
                    id: liste
                    visible: main.categorie === "Plus de widgets"
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.margins: 16
                    clip: true
                    spacing: 4
                    model: root.widgetExplorer ? root.widgetExplorer.widgetsModel : null
                    delegate: MouseArea {
                        id: autre
                        width: ListView.view.width
                        visible: String(model.pluginName).indexOf("org.samaos.") !== 0
                        height: visible ? 52 : 0
                        hoverEnabled: true
                        enabled: model.isSupported
                        onClicked: main.ajouter(model.pluginName)
                        Rectangle {
                            anchors.fill: parent
                            radius: 12
                            color: autre.containsMouse ? main.champ : "transparent"
                        }
                        RowLayout {
                            anchors.fill: parent
                            anchors.leftMargin: 12
                            anchors.rightMargin: 12
                            spacing: 12
                            Kirigami.Icon {
                                Layout.preferredWidth: 30
                                Layout.preferredHeight: 30
                                source: model.decoration
                            }
                            Column {
                                Layout.fillWidth: true
                                Text { text: model.name; font.pixelSize: 13; font.weight: Font.Medium; color: main.texte; elide: Text.ElideRight; width: parent.width }
                                Text { text: model.description || ""; font.pixelSize: 11; color: main.texte2; elide: Text.ElideRight; width: parent.width }
                            }
                            Text { text: "Ajouter"; font.pixelSize: 12; font.weight: Font.DemiBold; color: main.sombre ? "#F0B392" : "#93401F"; visible: autre.containsMouse }
                        }
                    }
                }
            }
        }
    }
}
