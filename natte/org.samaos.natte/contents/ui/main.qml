// La Natte — barre de navigation de Sama OS (Plasma 6).
// Affiche les Espaces (activités Plasma) en pilules colorées :
// l'Espace actif se déplie et montre ses applications (épinglées + ouvertes),
// les autres restent repliés avec le nombre de fenêtres ouvertes.
// La Cour (lanceur plein écran), la Corbeille et le Pouls sont des widgets voisins dans la barre.

import QtQuick
import QtQuick.Layouts
import org.kde.plasma.plasmoid
import org.kde.kirigami as Kirigami
import org.kde.activities as Activities
import org.kde.taskmanager as TaskManager
import QtCore
import Qt.labs.folderlistmodel

PlasmoidItem {
    id: racine

    // Couleur et applications épinglées de chaque Espace (par nom d'activité)
    readonly property var espacesSama: ({
        "Travail": { teinte: "#B5532F", encre: "#93401F", fond: "#F2E1D5", fondSombre: "#3DB5532F",
                     epingles: ["applications:samaos-griot.desktop", "applications:samaos-docs.desktop",
                                "applications:samaos-sheet.desktop", "applications:samaos-fichiers.desktop"] },
        "École":   { teinte: "#3D5A99", encre: "#2D4682", fond: "#DFE5F2", fondSombre: "#523D5A99",
                     epingles: ["applications:samaos-griot.desktop", "applications:samaos-sugu.desktop",
                                "applications:samaos-fichiers.desktop", "applications:samaos-docs.desktop"] },
        "Maison":  { teinte: "#2F6B57", encre: "#1F5544", fond: "#DBEAE2", fondSombre: "#522F6B57",
                     epingles: ["applications:samaos-griot.desktop", "applications:samaos-fichiers.desktop",
                                "applications:samaos-photos.desktop", "applications:samaos-sugu.desktop"] }
    })
    readonly property var espaceParDefaut: ({ teinte: "#8A8277", encre: "#665E54", fond: "#EDE6DC", fondSombre: "#338A8277", epingles: [] })

    function infosEspace(nom) {
        return espacesSama[nom] || espaceParDefaut
    }

    // Mesures de la maquette (px) : éléments de 44, tuiles de 34, 8 d'écart
    readonly property int hauteurPilule: 44
    readonly property int tailleTuile: 34
    readonly property int ecart: 8
    readonly property color encreDouce: Kirigami.Theme.disabledTextColor

    // Applications dont l'icône est déjà une tuile Sama (pas de fond à ajouter)
    readonly property var appsAvecTuile: ["samaos-", "konsole", "systemsettings", "khelpcenter", "okular",
                                          "org.kde.ark", "systemmonitor", "spectacle"]
    function aUneTuileSama(url, appId) {
        var texte = (String(url) + " " + String(appId)).toLowerCase()
        for (var i = 0; i < appsAvecTuile.length; i++) {
            if (texte.indexOf(appsAvecTuile[i]) >= 0) return true
        }
        return false
    }

    // Éléments dans la Corbeille (le dossier est surveillé : le compteur suit en direct)
    FolderListModel {
        id: contenuCorbeille
        folder: StandardPaths.writableLocation(StandardPaths.GenericDataLocation) + "/Trash/files"
        showHidden: true
        showDotAndDotDot: false
    }
    // Tant que la Corbeille n'a jamais servi, son dossier n'existe pas : on vérifie que la liste vient bien de lui
    readonly property int nombreDansCorbeille: contenuCorbeille.count > 0
        && String(contenuCorbeille.get(0, "filePath")).indexOf("/Trash/files/") >= 0 ? contenuCorbeille.count : 0

    preferredRepresentation: fullRepresentation

    Activities.ActivityModel {
        id: espaces
        shownStates: "Running"
    }

    TaskManager.ActivityInfo { id: infoActivite }
    TaskManager.VirtualDesktopInfo { id: infoBureau }

    // Liste des Espaces dans l'ordre de Sama (Travail, École, Maison, puis les autres)
    property var espacesOrdonnes: []
    readonly property var rangEspaces: ({ "Travail": 0, "École": 1, "Maison": 2 })

    function reordonner() {
        var liste = []
        for (var i = 0; i < collecte.count; i++) {
            var o = collecte.objectAt(i)
            if (o && o.idEspace) {
                liste.push({ id: o.idEspace, nom: o.nomEspace })
            }
        }
        liste.sort(function (a, b) {
            var ra = (a.nom in rangEspaces) ? rangEspaces[a.nom] : 9
            var rb = (b.nom in rangEspaces) ? rangEspaces[b.nom] : 9
            return ra - rb
        })
        espacesOrdonnes = liste
    }

    Instantiator {
        id: collecte
        model: espaces
        delegate: QtObject {
            readonly property string idEspace: model.id
            readonly property string nomEspace: model.name
            onNomEspaceChanged: racine.reordonner()
        }
        onObjectAdded: racine.reordonner()
        onObjectRemoved: racine.reordonner()
    }

    // Nom de l'Espace actif
    readonly property string nomActif: {
        for (var i = 0; i < espacesOrdonnes.length; i++) {
            if (espacesOrdonnes[i].id === infoActivite.currentActivity) {
                return espacesOrdonnes[i].nom
            }
        }
        return ""
    }

    // Applications de l'Espace actif : épinglées + fenêtres ouvertes dans cet Espace
    TaskManager.TasksModel {
        id: applications
        activity: infoActivite.currentActivity
        virtualDesktop: infoBureau.currentDesktop
        filterByActivity: true
        separateLaunchers: false
        launchInPlace: true
        groupMode: TaskManager.TasksModel.GroupApplications
        sortMode: TaskManager.TasksModel.SortManual
        launcherList: racine.infosEspace(racine.nomActif).epingles
    }

    component Separateur: Rectangle {
        Layout.preferredWidth: 1
        Layout.preferredHeight: 28
        Layout.leftMargin: 2
        Layout.rightMargin: 2
        color: Kirigami.Theme.textColor
        opacity: 0.12
    }

    fullRepresentation: RowLayout {
        spacing: racine.ecart
        Layout.minimumHeight: racine.hauteurPilule
        Layout.preferredHeight: racine.hauteurPilule
        Layout.maximumHeight: racine.hauteurPilule

        Separateur {}

        Repeater {
            model: racine.espacesOrdonnes

            delegate: Item {
                id: espace

                readonly property string idEspace: modelData.id
                readonly property string nomEspace: modelData.nom
                readonly property bool actif: idEspace === infoActivite.currentActivity
                readonly property var infos: racine.infosEspace(nomEspace)

                Layout.preferredHeight: racine.hauteurPilule
                Layout.preferredWidth: actif ? piluleActive.implicitWidth : piluleRepliee.implicitWidth

                Behavior on Layout.preferredWidth {
                    NumberAnimation { duration: Kirigami.Units.longDuration; easing.type: Easing.OutCubic }
                }

                // Espace actif : nom + applications
                Rectangle {
                    id: piluleActive
                    visible: espace.actif
                    anchors.fill: parent
                    radius: height / 2
                    color: Kirigami.Theme.textColor.hslLightness > 0.5 ? espace.infos.fondSombre : espace.infos.fond
                    implicitWidth: rangeeActive.implicitWidth + 14 + 6

                    RowLayout {
                        id: rangeeActive
                        anchors.top: parent.top
                        anchors.bottom: parent.bottom
                        anchors.left: parent.left
                        anchors.leftMargin: 14
                        spacing: 6

                        Text {
                            text: espace.nomEspace
                            font.weight: Font.DemiBold
                            font.pixelSize: 12
                            color: Kirigami.Theme.textColor.hslLightness > 0.5 ? Kirigami.Theme.textColor : espace.infos.encre
                            Layout.rightMargin: 6
                        }

                        Repeater {
                            model: applications

                            delegate: MouseArea {
                                id: tuile
                                readonly property bool ouverte: !model.IsLauncher
                                readonly property bool tuileSama: racine.aUneTuileSama(model.LauncherUrlWithoutIcon, model.AppId)
                                Layout.preferredWidth: racine.tailleTuile
                                Layout.preferredHeight: racine.hauteurPilule
                                hoverEnabled: true
                                onClicked: applications.requestActivate(applications.makeModelIndex(index))

                                Rectangle {
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    anchors.top: parent.top
                                    anchors.topMargin: 4
                                    width: racine.tailleTuile
                                    height: width
                                    radius: 11
                                    color: tuile.tuileSama ? "transparent" : Kirigami.Theme.backgroundColor
                                    opacity: model.IsStartup === true ? 0.15 : 1
                                    // Survol : la tuile se soulève de 3 px, comme dans la maquette
                                    transform: Translate {
                                        y: tuile.containsMouse ? -3 : 0
                                        Behavior on y { NumberAnimation { duration: 220; easing.type: Easing.OutCubic } }
                                    }

                                    Kirigami.Icon {
                                        anchors.centerIn: parent
                                        width: tuile.tuileSama ? parent.width : parent.width * 0.7
                                        height: width
                                        source: model.decoration
                                    }
                                }

                                // Application en cours d'ouverture : la trompe se balance par-dessus la tuile
                                ChargementSama {
                                    visible: model.IsStartup === true
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    anchors.top: parent.top
                                    anchors.topMargin: 4
                                    width: racine.tailleTuile
                                    height: width
                                    couleur: espace.infos.teinte
                                    fond: Kirigami.Theme.textColor.hslLightness > 0.5 ? "#1E2233" : espace.infos.fond
                                }

                                // Point sous les applications ouvertes
                                Rectangle {
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    anchors.top: parent.top
                                    anchors.topMargin: 4 + racine.tailleTuile + 2
                                    width: 4
                                    height: 4
                                    radius: 2
                                    color: espace.infos.encre
                                    visible: tuile.ouverte
                                }
                            }
                        }
                    }
                }

                // Fenêtres ouvertes dans cet Espace (pour le compteur)
                TaskManager.TasksModel {
                    id: fenetresEspace
                    activity: espace.idEspace
                    filterByActivity: true
                    filterByVirtualDesktop: false
                    groupMode: TaskManager.TasksModel.GroupDisabled
                }

                // Espace replié : point de couleur + nom + nombre de fenêtres
                MouseArea {
                    id: piluleRepliee
                    visible: !espace.actif
                    anchors.fill: parent
                    hoverEnabled: true
                    implicitWidth: rangeeRepliee.implicitWidth + 28
                    onClicked: espaces.setCurrentActivity(espace.idEspace, function () {})

                    Rectangle {
                        anchors.fill: parent
                        radius: height / 2
                        color: Kirigami.Theme.textColor
                        opacity: piluleRepliee.containsMouse ? 0.08 : 0
                    }

                    Row {
                        id: rangeeRepliee
                        anchors.centerIn: parent
                        spacing: 8

                        Rectangle {
                            anchors.verticalCenter: parent.verticalCenter
                            width: 8
                            height: 8
                            radius: 4
                            color: espace.infos.teinte
                        }
                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            text: espace.nomEspace
                            font.pixelSize: 12
                            font.weight: Font.Medium
                            color: racine.encreDouce
                        }
                        Rectangle {
                            anchors.verticalCenter: parent.verticalCenter
                            visible: fenetresEspace.count > 0
                            width: Math.max(18, compteur.implicitWidth + 8)
                            height: 18
                            radius: 9
                            color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.1)
                            Text {
                                id: compteur
                                anchors.centerIn: parent
                                text: fenetresEspace.count
                                font.pixelSize: Kirigami.Theme.smallFont.pixelSize - 1
                                font.weight: Font.DemiBold
                                color: Kirigami.Theme.disabledTextColor
                            }
                        }
                    }
                }
            }
        }

        Separateur {}

        // La Corbeille : bouton rond discret
        MouseArea {
            id: corbeille
            Layout.preferredWidth: racine.hauteurPilule
            Layout.preferredHeight: racine.hauteurPilule
            hoverEnabled: true
            onClicked: Qt.openUrlExternally("trash:/")

            Rectangle {
                anchors.fill: parent
                radius: width / 2
                color: Kirigami.Theme.textColor
                opacity: corbeille.containsMouse ? 0.1 : 0.05
            }
            Kirigami.Icon {
                anchors.centerIn: parent
                width: 20
                height: width
                isMask: true
                color: Kirigami.Theme.textColor
                source: Qt.resolvedUrl("../icons/corbeille.svg")
            }
            // Nombre d'éléments dans la Corbeille
            Rectangle {
                visible: racine.nombreDansCorbeille > 0
                anchors.top: parent.top
                anchors.right: parent.right
                anchors.topMargin: 4
                anchors.rightMargin: 4
                width: Math.max(16, nombreCorbeille.implicitWidth + 8)
                height: 16
                radius: 8
                color: "#B5532F"
                Text {
                    id: nombreCorbeille
                    anchors.centerIn: parent
                    text: racine.nombreDansCorbeille > 99 ? "99+" : racine.nombreDansCorbeille
                    font.pixelSize: 10
                    font.weight: Font.DemiBold
                    color: "#FFFFFF"
                }
            }
        }
    }
}
