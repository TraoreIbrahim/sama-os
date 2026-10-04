// Le Pouls — capsule d'état de Sama OS (Plasma 6).
// Langue · réseau · économie de data · batterie · heure, avec les icônes de la maquette.
// Deux moitiés, comme sur macOS ou Windows 11 :
//   langue, réseau, data, batterie → centre de contrôle (Panneau.qml) ;
//   heure et compteur latérite → centre de notifications (CentreNotifications.qml).
// Les sources de données (réseau, batterie, clavier) sont chargées à part, chacune dans un Loader :
// si l'une manque, la capsule reste affichée avec une valeur par défaut.

import QtQuick
import QtQuick.Layouts
import org.kde.plasma.plasmoid
import org.kde.kirigami as Kirigami

PlasmoidItem {
    id: racine

    readonly property color encre: Kirigami.Theme.textColor
    readonly property color encreDouce: Kirigami.Theme.disabledTextColor
    readonly property color foret: "#2F6B57"
    readonly property color laterite: "#B5532F"

    // Valeurs exposées par les sources (avec repli)
    readonly property string langue: clavier.item && clavier.item.code ? clavier.item.code
                                     : Qt.locale().name.substring(0, 2).toUpperCase()
    readonly property string etatReseau: reseau.item ? reseau.item.etat : "inconnu"   // wifi, filaire, coupe, inconnu
    readonly property bool aBatterie: batterie.item ? batterie.item.presente : false
    readonly property int niveauBatterie: batterie.item ? batterie.item.niveau : 100
    property string heure: Qt.formatTime(new Date(), "hh:mm")

    Timer {
        interval: 5000
        running: true
        repeat: true
        triggeredOnStart: true
        onTriggered: racine.heure = Qt.formatTime(new Date(), "hh:mm")
    }

    Loader { id: clavier; source: "Clavier.qml" }
    Loader { id: reseau; source: "Reseau.qml" }
    Loader { id: batterie; source: "Batterie.qml" }
    Loader { id: notifs; source: "Notifications.qml" }

    readonly property int nonLues: notifs.item ? notifs.item.nonLues : 0

    // Panneau ouvert : « controle » ou « notifications »
    property string vue: "controle"
    function basculer(choix) {
        if (expanded && vue === choix) { expanded = false; return }
        vue = choix
        expanded = true
    }
    // Ouvrir le centre de notifications marque les notifications comme lues (à la fermeture, pour garder
    // le compteur « nouvelles » lisible pendant qu'il est ouvert)
    onExpandedChanged: if (!expanded && vue === "notifications" && notifs.item) notifs.item.marquerLues()

    function icone(nom) {
        return Qt.resolvedUrl("../icons/" + nom + ".svg")
    }

    preferredRepresentation: compactRepresentation
    // Pas d'infobulle : l'élément parle de lui-même
    toolTipMainText: ""
    toolTipSubText: ""

    compactRepresentation: Item {
        id: capsule

        readonly property int hauteurCapsule: 44   // comme la maquette
        Layout.minimumWidth: etat.width + heure.width + 8
        Layout.preferredWidth: Layout.minimumWidth
        Layout.minimumHeight: hauteurCapsule
        Layout.preferredHeight: hauteurCapsule
        implicitWidth: Layout.minimumWidth
        implicitHeight: hauteurCapsule

        // Fond de la capsule (une seule pilule, comme la maquette)
        Rectangle {
            anchors.verticalCenter: parent.verticalCenter
            width: parent.width
            height: capsule.hauteurCapsule
            radius: height / 2
            color: racine.encre
            opacity: 0.05
        }

        // Moitié gauche : centre de contrôle
        MouseArea {
            id: etat
            anchors.left: parent.left
            anchors.leftMargin: 4
            anchors.verticalCenter: parent.verticalCenter
            width: icones.implicitWidth + 24
            height: capsule.hauteurCapsule - 8
            hoverEnabled: true
            onClicked: racine.basculer("controle")
            Rectangle {
                anchors.fill: parent
                radius: height / 2
                color: racine.encre
                opacity: etat.containsMouse || (racine.expanded && racine.vue === "controle") ? 0.08 : 0
            }
            RowLayout {
                id: icones
                anchors.centerIn: parent
                spacing: 10

                Text {
                    text: racine.langue
                    font.pixelSize: 11
                    font.weight: Font.DemiBold
                    font.letterSpacing: 0.4
                    color: racine.encreDouce
                }

                Kirigami.Icon {
                    Layout.preferredWidth: 16
                    Layout.preferredHeight: Layout.preferredWidth
                    isMask: true
                    color: racine.encre
                    opacity: racine.etatReseau === "coupe" ? 0.5 : 1
                    source: racine.icone(racine.etatReseau === "filaire" ? "filaire"
                                         : racine.etatReseau === "coupe" ? "wifi-coupe" : "wifi")
                }

                // Économie de data
                Kirigami.Icon {
                    Layout.preferredWidth: 16
                    Layout.preferredHeight: Layout.preferredWidth
                    isMask: true
                    color: racine.foret
                    source: racine.icone("feuille")
                }

                // Batterie (seulement si l'ordinateur en a une)
                Item {
                    visible: racine.aBatterie
                    Layout.preferredWidth: 20
                    Layout.preferredHeight: 16

                    Kirigami.Icon {
                        anchors.fill: parent
                        isMask: true
                        color: racine.niveauBatterie <= 15 ? racine.laterite : racine.encre
                        source: racine.icone("batterie")
                    }
                    // Niveau de charge à l'intérieur du contour
                    Rectangle {
                        x: parent.width * 0.21
                        y: parent.height * 0.42
                        height: parent.height * 0.16
                        width: parent.width * 0.5 * Math.max(0.08, racine.niveauBatterie / 100)
                        radius: 1
                        color: racine.niveauBatterie <= 15 ? racine.laterite : racine.encre
                    }
                }
            }
        }

        // Moitié droite : centre de notifications
        MouseArea {
            id: heure
            anchors.right: parent.right
            anchors.rightMargin: 4
            anchors.verticalCenter: parent.verticalCenter
            width: texteHeure.implicitWidth + (compteur.visible ? compteur.width + 8 : 0) + 24
            height: capsule.hauteurCapsule - 8
            hoverEnabled: true
            onClicked: racine.basculer("notifications")
            Rectangle {
                anchors.fill: parent
                radius: height / 2
                color: racine.encre
                opacity: heure.containsMouse || (racine.expanded && racine.vue === "notifications") ? 0.08 : 0
            }
            Row {
                anchors.centerIn: parent
                spacing: 8
                Text {
                    id: texteHeure
                    anchors.verticalCenter: parent.verticalCenter
                    text: racine.heure
                    font.pixelSize: 13
                    font.weight: Font.DemiBold
                    color: racine.encre
                }
                // Compteur latérite : notifications non lues (« 3 », « 9+ »)
                Rectangle {
                    id: compteur
                    visible: racine.nonLues > 0
                    anchors.verticalCenter: parent.verticalCenter
                    width: Math.max(16, chiffre.implicitWidth + 8)
                    height: 16
                    radius: 8
                    color: racine.laterite
                    Text {
                        id: chiffre
                        anchors.centerIn: parent
                        text: racine.nonLues > 9 ? "9+" : racine.nonLues
                        font.pixelSize: 10
                        font.weight: Font.DemiBold
                        color: "#FFFFFF"
                    }
                }
            }
        }
    }

    fullRepresentation: Loader {
        source: racine.vue === "notifications" ? "CentreNotifications.qml" : "Panneau.qml"
        // 360 px avec les marges du cadre, comme la maquette. Hauteur imposée par le contenu :
        // Plasma mémorise sinon la taille d'une ouverture précédente et coupe le panneau.
        readonly property real hauteur: item ? item.implicitHeight : Kirigami.Units.gridUnit * 14
        Layout.minimumWidth: 336
        Layout.preferredWidth: 336
        Layout.maximumWidth: 336
        Layout.minimumHeight: hauteur
        Layout.preferredHeight: hauteur
        Layout.maximumHeight: hauteur
    }
}
