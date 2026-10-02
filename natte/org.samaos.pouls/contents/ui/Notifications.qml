// Notifications de Sama OS : réception, bulles à l'écran, historique.
// Remplace le module de notifications de la zone d'icônes de KDE (retirée de la Natte).
//  - démarre le serveur de notifications de Plasma ;
//  - affiche chaque nouvelle notification en bulle, en bas à droite au-dessus de la Natte ;
//  - garde l'historique, lu par le panneau du Pouls (ListeNotifications.qml).

import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.core as PlasmaCore
import org.kde.plasma.plasmoid
import org.kde.notificationmanager as NotificationManager

Item {
    id: notifications

    // Valeurs lues par la capsule et le panneau du Pouls
    readonly property int nonLues: historique.unreadNotificationsCount
    readonly property int nombre: historique.count
    readonly property alias historique: historique

    function marquerLues() {
        historique.lastRead = new Date()
    }
    function toutEffacer() {
        historique.clear(NotificationManager.Notifications.ClearExpired | NotificationManager.Notifications.ClearDismissed)
    }

    // Le serveur de notifications est démarré par Plasma lui-même (plasmashell)

    // Historique : toutes les notifications reçues (hors tâches de fond)
    NotificationManager.Notifications {
        id: historique
        showExpired: true
        showDismissed: true
        showJobs: false
        showNotifications: true
        groupMode: NotificationManager.Notifications.GroupDisabled
        sortMode: NotificationManager.Notifications.SortByDate
        limit: 30
    }

    // Bulles : seulement les notifications nouvelles, trois au maximum
    NotificationManager.Notifications {
        id: bulles
        showExpired: false
        showDismissed: false
        showJobs: false
        showNotifications: true
        groupMode: NotificationManager.Notifications.GroupDisabled
        sortMode: NotificationManager.Notifications.SortByDate
        limit: 3
    }

    readonly property rect zoneEcran: Plasmoid.containment ? Plasmoid.containment.availableScreenRect : Qt.rect(0, 0, 1280, 800)
    readonly property int largeurBulle: Kirigami.Units.gridUnit * 20
    readonly property int marge: Kirigami.Units.gridUnit

    Instantiator {
        model: bulles

        delegate: PlasmaCore.Dialog {
            id: bulle

            readonly property int duree: model.timeout > 0 ? model.timeout : 6000

            type: PlasmaCore.Dialog.Notification
            flags: Qt.WindowDoesNotAcceptFocus | Qt.WindowStaysOnTopHint
            location: PlasmaCore.Types.Floating
            backgroundHints: PlasmaCore.Types.StandardBackground
            visible: true

            x: notifications.zoneEcran.x + notifications.zoneEcran.width - width - notifications.marge
            y: notifications.zoneEcran.y + notifications.zoneEcran.height - (height + notifications.marge / 2) * (index + 1) - notifications.marge

            mainItem: MouseArea {
                id: survol

                // Disparition automatique (en pause tant que la souris survole la bulle)
                Timer {
                    interval: bulle.duree
                    running: !survol.containsMouse
                    onTriggered: bulles.expire(bulles.index(index, 0))
                }

                width: notifications.largeurBulle
                height: contenu.implicitHeight + Kirigami.Units.largeSpacing * 2
                hoverEnabled: true
                onClicked: {
                    if (model.hasDefaultAction) {
                        bulles.invokeDefaultAction(bulles.index(index, 0))
                    }
                    bulles.expire(bulles.index(index, 0))
                }

                RowLayout {
                    id: contenu
                    anchors.fill: parent
                    anchors.margins: Kirigami.Units.largeSpacing
                    spacing: Kirigami.Units.largeSpacing

                    // Icône de l'application dans une tuile arrondie
                    Rectangle {
                        Layout.alignment: Qt.AlignTop
                        Layout.preferredWidth: Kirigami.Units.iconSizes.medium + Kirigami.Units.smallSpacing
                        Layout.preferredHeight: Layout.preferredWidth
                        radius: width * 0.3
                        color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)
                        Kirigami.Icon {
                            anchors.centerIn: parent
                            width: Kirigami.Units.iconSizes.smallMedium
                            height: width
                            source: model.image ? model.image : (model.iconName || model.applicationIconName || "preferences-desktop-notification")
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 2

                        RowLayout {
                            Layout.fillWidth: true
                            Text {
                                Layout.fillWidth: true
                                text: model.applicationName || "Sama"
                                elide: Text.ElideRight
                                font.pixelSize: Kirigami.Theme.smallFont.pixelSize
                                color: Kirigami.Theme.disabledTextColor
                            }
                            Text {
                                text: "×"
                                font.pixelSize: Kirigami.Theme.defaultFont.pixelSize + 2
                                color: Kirigami.Theme.disabledTextColor
                                MouseArea {
                                    anchors.fill: parent
                                    anchors.margins: -6
                                    onClicked: bulles.close(bulles.index(index, 0))
                                }
                            }
                        }
                        Text {
                            Layout.fillWidth: true
                            text: model.summary
                            visible: text.length > 0
                            wrapMode: Text.WordWrap
                            maximumLineCount: 2
                            elide: Text.ElideRight
                            font.weight: Font.DemiBold
                            color: Kirigami.Theme.textColor
                        }
                        Text {
                            Layout.fillWidth: true
                            text: model.body
                            visible: text.length > 0
                            textFormat: Text.StyledText
                            wrapMode: Text.WordWrap
                            maximumLineCount: 3
                            elide: Text.ElideRight
                            color: Kirigami.Theme.textColor
                            opacity: 0.85
                        }
                    }
                }
            }
        }
    }
}
