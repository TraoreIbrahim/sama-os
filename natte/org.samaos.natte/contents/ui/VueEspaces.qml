// Vue d'ensemble des Espaces (d'après l'écran « Espaces » de la maquette) :
// une colonne par Espace avec ses fenêtres en miniature et ses applications épinglées.
//  - clic sur une fenêtre : on va dans son Espace et on l'affiche ;
//  - glisser une fenêtre sur une autre colonne : elle change d'Espace ;
//  - clic sur l'en-tête d'une colonne : on va dans cet Espace ;
//  - « Nouvel Espace » en fin de liste ; Échap ou clic dans le vide : fermer.

import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.taskmanager as TaskManager
import org.kde.plasma.private.kicker as Kicker

Kicker.DashboardWindow {
    id: vue

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color texte: Kirigami.Theme.textColor
    readonly property color texte2: Kirigami.Theme.disabledTextColor
    readonly property color carte: sombre ? Qt.rgba(30 / 255, 34 / 255, 51 / 255, 0.86) : Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.86)
    readonly property color ligne: sombre ? Qt.rgba(1, 1, 1, 0.08) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)

    // Espace survolé pendant qu'on fait glisser une fenêtre, et fenêtre glissée
    property string cibleGlisser: ""
    property var glisse: null

    backgroundColor: sombre ? Qt.rgba(21 / 255, 26 / 255, 43 / 255, 0.92) : Qt.rgba(243 / 255, 236 / 255, 226 / 255, 0.92)
    onKeyEscapePressed: vue.toggle()

    function allerA(idEspace) {
        if (idEspace !== infoActivite.currentActivity) espaces.setCurrentActivity(idEspace, function () {})
    }

    mainItem: MouseArea {
        id: fond
        anchors.fill: parent
        onClicked: vue.toggle()

        // Copie flottante de la fenêtre glissée (au-dessus de tout, pas rognée par les colonnes)
        Rectangle {
            id: fantome
            z: 100
            visible: vue.glisse !== null
            width: 180
            height: 112
            radius: 10
            color: vue.sombre ? "#262B3D" : "#FBF9F6"
            border.width: 2
            border.color: "#B5532F"
            opacity: 0.92
            Drag.active: visible
            Drag.keys: ["fenetre-sama"]
            Drag.hotSpot.x: width / 2
            Drag.hotSpot.y: height / 2
            Column {
                anchors.centerIn: parent
                spacing: 8
                Kirigami.Icon {
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 40
                    height: 40
                    source: vue.glisse ? vue.glisse.icone : ""
                }
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: fantome.width - 20
                    horizontalAlignment: Text.AlignHCenter
                    elide: Text.ElideRight
                    text: vue.glisse ? vue.glisse.titre : ""
                    font.pixelSize: 11
                    color: vue.texte
                }
            }
        }

        // Titre et conseil
        Column {
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: parent.top
            anchors.topMargin: 40
            spacing: 10
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: "Espaces"
                font.pixelSize: 24
                font.weight: Font.Medium
                color: vue.texte
            }
            Rectangle {
                anchors.horizontalCenter: parent.horizontalCenter
                height: 30
                width: conseil.implicitWidth + 28
                radius: 15
                color: vue.carte
                border.width: 1
                border.color: vue.ligne
                Text {
                    id: conseil
                    anchors.centerIn: parent
                    text: "Glissez une fenêtre vers un autre Espace"
                    font.pixelSize: 13
                    color: vue.texte2
                }
            }
        }

        // Colonnes, centrées ; défilement horizontal s'il y a beaucoup d'Espaces
        Flickable {
            id: defilement
            anchors.top: parent.top
            anchors.topMargin: 140
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 100
            anchors.horizontalCenter: parent.horizontalCenter
            width: Math.min(parent.width - 64, rangee.implicitWidth)
            contentWidth: rangee.implicitWidth
            contentHeight: height
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            flickableDirection: Flickable.HorizontalFlick

            Row {
                id: rangee
                height: parent.height
                spacing: 24

                Repeater {
                    model: racine.espacesOrdonnes

                    delegate: Rectangle {
                        id: colonne
                        readonly property string idEspace: modelData.id
                        readonly property string nomEspace: modelData.nom
                        readonly property bool actif: idEspace === infoActivite.currentActivity
                        readonly property var infos: racine.infosEspace(idEspace, nomEspace)
                        readonly property bool cible: vue.cibleGlisser === idEspace

                        width: 360
                        height: rangee.height
                        radius: 22
                        color: vue.carte
                        border.width: actif || cible ? 2 : 1
                        border.color: cible ? infos.teinte : (actif ? "#B5532F" : vue.ligne)

                        // Fenêtres et applications épinglées de cet Espace
                        TaskManager.TasksModel {
                            id: fenetres
                            activity: colonne.idEspace
                            filterByActivity: true
                            filterByVirtualDesktop: false
                            filterByScreen: false
                            groupMode: TaskManager.TasksModel.GroupDisabled
                            sortMode: TaskManager.TasksModel.SortLastActivated
                            separateLaunchers: true
                            launcherList: colonne.infos.epingles
                        }
                        readonly property int nombreFenetres: {
                            var n = 0
                            for (var i = 0; i < repeteurFenetres.count; i++) {
                                var it = repeteurFenetres.itemAt(i)
                                if (it && it.estFenetre) n++
                            }
                            return n
                        }

                        // Zone de dépôt : une fenêtre glissée ici rejoint cet Espace
                        DropArea {
                            anchors.fill: parent
                            keys: ["fenetre-sama"]
                            onEntered: vue.cibleGlisser = colonne.idEspace
                            onExited: if (vue.cibleGlisser === colonne.idEspace) vue.cibleGlisser = ""
                            onDropped: drop => {
                                vue.cibleGlisser = ""
                                var g = vue.glisse
                                if (g && g.espaceOrigine !== colonne.idEspace) {
                                    g.modele.requestActivities(g.modele.makeModelIndex(g.indexModele), [colonne.idEspace])
                                }
                            }
                        }
                        // Clic dans la colonne (hors fenêtre) : aller dans l'Espace
                        MouseArea {
                            anchors.fill: parent
                            onClicked: { vue.allerA(colonne.idEspace); vue.toggle() }
                        }

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 18
                            spacing: 16

                            // En-tête : couleur, nom, « Actif », nombre de fenêtres
                            RowLayout {
                                Layout.fillWidth: true
                                spacing: 10
                                Rectangle { width: 10; height: 10; radius: 5; color: colonne.infos.teinte }
                                Text {
                                    text: colonne.nomEspace
                                    font.pixelSize: 17
                                    font.weight: Font.Medium
                                    color: vue.texte
                                }
                                Rectangle {
                                    visible: colonne.actif
                                    height: 20
                                    width: etiquetteActif.implicitWidth + 16
                                    radius: 10
                                    color: vue.sombre ? colonne.infos.fondSombre : colonne.infos.fond
                                    Text {
                                        id: etiquetteActif
                                        anchors.centerIn: parent
                                        text: "Actif"
                                        font.pixelSize: 11
                                        font.weight: Font.DemiBold
                                        color: vue.sombre ? vue.texte : colonne.infos.encre
                                    }
                                }
                                Item { Layout.fillWidth: true }
                                Text {
                                    text: colonne.nombreFenetres === 0 ? "Aucune fenêtre"
                                        : colonne.nombreFenetres === 1 ? "1 fenêtre" : colonne.nombreFenetres + " fenêtres"
                                    font.pixelSize: 12
                                    color: vue.texte2
                                }
                            }

                            // Fenêtres
                            Flickable {
                                Layout.fillWidth: true
                                Layout.fillHeight: true
                                contentHeight: listeFenetres.implicitHeight
                                clip: true
                                boundsBehavior: Flickable.StopAtBounds
                                interactive: contentHeight > height

                                Column {
                                    id: listeFenetres
                                    width: parent.width
                                    spacing: 14

                                    Repeater {
                                        id: repeteurFenetres
                                        model: fenetres

                                        delegate: Item {
                                            id: fenetre
                                            readonly property bool estFenetre: !model.IsLauncher
                                            visible: estFenetre
                                            width: listeFenetres.width
                                            height: estFenetre ? 200 : 0


                                            Rectangle {
                                                id: cadre
                                                width: parent.width
                                                height: parent.height
                                                radius: 10
                                                color: vue.sombre ? "#262B3D" : "#FBF9F6"
                                                border.width: 1
                                                border.color: vue.ligne
                                                clip: true
                                                opacity: vue.glisse && vue.glisse.indexModele === index && vue.glisse.espaceOrigine === colonne.idEspace ? 0.35 : 1
                                                scale: glisser.containsMouse && !vue.glisse ? 1.02 : 1
                                                Behavior on scale { NumberAnimation { duration: 150 } }

                                                // Barre de titre : icône, application · titre, fermer
                                                Rectangle {
                                                    id: barre
                                                    width: parent.width
                                                    height: 26
                                                    color: "transparent"
                                                    RowLayout {
                                                        anchors.fill: parent
                                                        anchors.leftMargin: 10
                                                        anchors.rightMargin: 6
                                                        spacing: 7
                                                        Kirigami.Icon {
                                                            Layout.preferredWidth: 13
                                                            Layout.preferredHeight: 13
                                                            source: model.decoration
                                                        }
                                                        Text {
                                                            Layout.fillWidth: true
                                                            text: {
                                                                var app = model.AppName || ""
                                                                var titre = model.display || ""
                                                                return app && titre && titre !== app ? app + " · " + titre : (titre || app)
                                                            }
                                                            elide: Text.ElideRight
                                                            font.pixelSize: 11
                                                            font.weight: Font.Medium
                                                            color: vue.texte
                                                        }
                                                        Rectangle {
                                                            Layout.preferredWidth: 18
                                                            Layout.preferredHeight: 18
                                                            radius: 9
                                                            color: fermer.containsMouse ? "#B5532F" : "transparent"
                                                            visible: glisser.containsMouse || fermer.containsMouse
                                                            Text {
                                                                anchors.centerIn: parent
                                                                text: "×"
                                                                font.pixelSize: 14
                                                                color: fermer.containsMouse ? "#FFFFFF" : vue.texte2
                                                            }
                                                            MouseArea {
                                                                id: fermer
                                                                anchors.fill: parent
                                                                hoverEnabled: true
                                                                onClicked: fenetres.requestClose(fenetres.makeModelIndex(index))
                                                            }
                                                        }
                                                    }
                                                    Rectangle {
                                                        anchors.bottom: parent.bottom
                                                        width: parent.width
                                                        height: 1
                                                        color: vue.ligne
                                                    }
                                                }

                                                // Contenu : miniature en direct, sinon grande icône
                                                Item {
                                                    anchors.top: barre.bottom
                                                    anchors.left: parent.left
                                                    anchors.right: parent.right
                                                    anchors.bottom: parent.bottom
                                                    Rectangle {
                                                        anchors.fill: parent
                                                        color: vue.sombre ? "#1E2233" : "#F4E8DC"
                                                        visible: !(miniature.item && miniature.item.prete)
                                                        Kirigami.Icon {
                                                            anchors.centerIn: parent
                                                            width: 56
                                                            height: 56
                                                            source: model.decoration
                                                        }
                                                    }
                                                    Loader {
                                                        id: miniature
                                                        anchors.fill: parent
                                                        active: fenetre.estFenetre && vue.visible && model.WinIdList && model.WinIdList.length > 0
                                                        source: "Miniature.qml"
                                                        onLoaded: item.winId = model.WinIdList[0]
                                                    }
                                                }
                                            }

                                            MouseArea {
                                                id: glisser
                                                anchors.fill: parent
                                                anchors.topMargin: 0
                                                hoverEnabled: true
                                                preventStealing: true
                                                cursorShape: vue.glisse ? Qt.ClosedHandCursor : Qt.PointingHandCursor
                                                property point depart
                                                property bool aGlisse: false
                                                onPressed: mouse => { depart = Qt.point(mouse.x, mouse.y); aGlisse = false }
                                                onPositionChanged: mouse => {
                                                    if (!pressed || !fenetre.estFenetre) return
                                                    if (!vue.glisse && Math.abs(mouse.x - depart.x) + Math.abs(mouse.y - depart.y) > 10) {
                                                        aGlisse = true
                                                        vue.glisse = { modele: fenetres, indexModele: index, espaceOrigine: colonne.idEspace,
                                                                       icone: model.decoration, titre: model.display || model.AppName || "" }
                                                    }
                                                    if (vue.glisse) {
                                                        var p = mapToItem(fond, mouse.x, mouse.y)
                                                        fantome.x = p.x - fantome.width / 2
                                                        fantome.y = p.y - fantome.height / 2
                                                    }
                                                }
                                                onReleased: {
                                                    if (vue.glisse) {
                                                        fantome.Drag.drop()
                                                        vue.glisse = null
                                                        vue.cibleGlisser = ""
                                                    }
                                                }
                                                onClicked: {
                                                    if (aGlisse) return
                                                    vue.allerA(colonne.idEspace)
                                                    fenetres.requestActivate(fenetres.makeModelIndex(index))
                                                    vue.toggle()
                                                }
                                                // Le bouton fermer reste cliquable
                                                z: -1
                                            }
                                        }
                                    }

                                    Text {
                                        visible: colonne.nombreFenetres === 0
                                        width: parent.width
                                        topPadding: 30
                                        horizontalAlignment: Text.AlignHCenter
                                        text: "Aucune fenêtre ouverte"
                                        font.pixelSize: 13
                                        color: vue.texte2
                                    }
                                }
                            }

                            // Applications épinglées
                            Rectangle { Layout.fillWidth: true; Layout.preferredHeight: 1; color: vue.ligne }
                            RowLayout {
                                Layout.fillWidth: true
                                spacing: 8
                                Text {
                                    Layout.fillWidth: true
                                    text: "Épinglées"
                                    font.pixelSize: 11
                                    color: vue.texte2
                                }
                                Repeater {
                                    model: fenetres
                                    delegate: Kirigami.Icon {
                                        visible: model.IsLauncher === true
                                        Layout.preferredWidth: visible ? 26 : 0
                                        Layout.preferredHeight: 26
                                        source: model.decoration
                                    }
                                }
                            }
                        }
                    }
                }

                // Nouvel Espace
                MouseArea {
                    id: nouveau
                    width: 200
                    height: rangee.height
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: { vue.toggle(); racine.edition = "nouveau" }
                    Rectangle {
                        anchors.fill: parent
                        radius: 22
                        color: nouveau.containsMouse ? vue.carte : "transparent"
                        border.width: 1.5
                        border.color: vue.ligne
                    }
                    Column {
                        anchors.centerIn: parent
                        spacing: 8
                        Text {
                            anchors.horizontalCenter: parent.horizontalCenter
                            text: "+"
                            font.pixelSize: 34
                            font.weight: Font.Light
                            color: vue.texte2
                        }
                        Text {
                            anchors.horizontalCenter: parent.horizontalCenter
                            text: "Nouvel Espace"
                            font.pixelSize: 13
                            font.weight: Font.Medium
                            color: vue.texte2
                        }
                    }
                }
            }
        }
    }
}
