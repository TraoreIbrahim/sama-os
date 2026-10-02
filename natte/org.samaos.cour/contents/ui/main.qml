// La Cour — lanceur plein écran de Sama OS (Plasma 6).
// Le bouton éléphant de la Natte ouvre une fenêtre plein écran (Fenetre.qml) :
// toutes les applications en grille, et une recherche unifiée (applications, fichiers,
// réglages, calculs). Les données viennent du module « kicker » de Plasma.

import QtQuick
import QtQuick.Layouts
import org.kde.plasma.plasmoid
import org.kde.kirigami as Kirigami
import org.kde.plasma.private.kicker as Kicker

PlasmoidItem {
    id: cour

    preferredRepresentation: fullRepresentation
    toolTipMainText: "La Cour"
    toolTipSubText: "Applications et recherche"

    signal reset()

    // Toutes les applications, en une liste à plat et triée
    Kicker.RootModel {
        id: modeleRacine
        autoPopulate: false
        appNameFormat: 0
        flat: true
        sorted: true
        showSeparators: false
        appletInterface: cour
        showAllApps: true
        showAllAppsCategorized: false
        showTopLevelItems: false
        showRecentApps: false
        showRecentDocs: false
        showPowerSession: false

        Component.onCompleted: favoritesModel.initForClient("org.samaos.cour.favoris-" + Plasmoid.id)
    }

    // Recherche unifiée
    Kicker.RunnerModel {
        id: modeleRecherche
        appletInterface: cour
        favoritesModel: modeleRacine.favoritesModel
        mergeResults: true
        runners: ["krunner_services", "krunner_systemsettings", "baloosearch", "locations",
                  "calculator", "unitconverter", "krunner_sessions", "krunner_powerdevil"]
    }

    property var fenetre: null

    function basculer() {
        if (!fenetre) {
            var composant = Qt.createComponent("Fenetre.qml")
            if (composant.status === Component.Ready) {
                fenetre = composant.createObject(cour, {
                    "visualParent": bouton,
                    "modeleRacine": modeleRacine,
                    "modeleRecherche": modeleRecherche
                })
            } else {
                console.warn("La Cour :", composant.errorString())
                return
            }
        }
        fenetre.toggle()
    }

    fullRepresentation: MouseArea {
        id: bouton
        Layout.minimumWidth: Layout.minimumHeight
        Layout.minimumHeight: Kirigami.Units.iconSizes.medium + Kirigami.Units.smallSpacing * 3
        Layout.preferredWidth: Layout.minimumWidth
        Layout.preferredHeight: Layout.minimumHeight
        hoverEnabled: true
        onClicked: cour.basculer()

        Rectangle {
            anchors.centerIn: parent
            width: Math.min(parent.width, parent.height)
            height: width
            radius: width / 2
            color: "#B5532F"
            scale: bouton.pressed ? 0.94 : (bouton.containsMouse ? 1.04 : 1)
            Behavior on scale { NumberAnimation { duration: Kirigami.Units.shortDuration } }

            Image {
                anchors.centerIn: parent
                width: parent.width * 0.62
                height: width
                sourceSize.width: width * 2
                sourceSize.height: height * 2
                source: "file:///usr/share/samaos/icones/elephant-cour.svg"
            }
        }
    }

    Component.onCompleted: modeleRacine.refresh()
}
