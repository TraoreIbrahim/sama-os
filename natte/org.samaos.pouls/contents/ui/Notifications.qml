// Notifications de Sama OS : réception, bulles à l'écran, historique.
// Remplace le module de notifications de la zone d'icônes de KDE (retirée de la Natte).
//  - bulles en haut à droite (maquette ale-02), trois au maximum ; au-delà, une bulle « N autres
//    notifications » ouvre le centre de notifications ;
//  - en mode Ne pas déranger, rien ne s'affiche à l'écran sauf les alertes critiques (batterie, forfait) :
//    tout arrive en silence dans le centre ;
//  - l'historique, groupé par application, est lu par le centre de notifications (CentreNotifications.qml).

import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.core as PlasmaCore
import org.kde.plasma.plasmoid
import org.kde.notificationmanager as NotificationManager

Item {
    id: notifications

    // Valeurs lues par la capsule et le centre de notifications
    readonly property int nonLues: historique.unreadNotificationsCount
    readonly property int nombre: historique.count
    readonly property alias historique: historique
    readonly property bool nePasDeranger: npd.actif

    function marquerLues() { historique.lastRead = new Date() }
    function toutEffacer() { historique.clear(NotificationManager.Notifications.ClearExpired | NotificationManager.Notifications.ClearDismissed) }
    function fermer(ligne) { historique.close(historique.index(ligne, 0)) }
    function deplier(ligne, ouvert) { historique.setData(historique.index(ligne, 0), ouvert, NotificationManager.Notifications.IsGroupExpandedRole) }
    function action(ligne, nom) { historique.invokeAction(historique.index(ligne, 0), nom) }
    function ouvrir(ligne) {
        var i = historique.index(ligne, 0)
        if (historique.data(i, NotificationManager.Notifications.HasDefaultActionRole)) historique.invokeDefaultAction(i)
    }

    // « à l'instant », « il y a 5 min », « 14:18 », « Hier », « lun. 28 sept. »
    function quand(date) {
        if (!date || isNaN(date.getTime())) return ""
        var maintenant = new Date(), ecart = (maintenant - date) / 60000
        if (ecart < 1) return "à l'instant"
        if (ecart < 60) return "il y a " + Math.floor(ecart) + " min"
        var hier = new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate() - 1)
        if (date.toDateString() === maintenant.toDateString()) return Qt.formatTime(date, "hh:mm")
        if (date.toDateString() === hier.toDateString()) return "Hier"
        return date.toLocaleDateString(Qt.locale("fr_FR"), "ddd d MMM")
    }

    NePasDeranger { id: npd }
    // Fin du Ne pas déranger : ce qui est arrivé pendant reste dans le centre, sans bulle après coup
    property date finNpd: new Date(0)
    readonly property bool npdActif: npd.actif
    onNpdActifChanged: if (!npdActif) finNpd = new Date()
    Timer { interval: 30000; running: true; repeat: true; onTriggered: npd.maintenant = new Date() }

    // Historique : groupé par application, deux notifications visibles par groupe (« N de plus » pour déplier)
    NotificationManager.Notifications {
        id: historique
        showExpired: true
        showDismissed: true
        showJobs: false
        showNotifications: true
        blacklistedDesktopEntries: npd.reglages.historyBlacklistedApplications
        blacklistedNotifyRcNames: npd.reglages.historyBlacklistedServices
        groupMode: NotificationManager.Notifications.GroupApplicationsFlat
        groupLimit: 2
        expandUnread: false
        sortMode: NotificationManager.Notifications.SortByDate
        limit: 100
    }

    // Filtre des bulles : en Ne pas déranger, seulement les alertes critiques (et les applications autorisées
    // à passer quand même, réglage « Même en Ne pas déranger » des Réglages → Notifications)
    readonly property int urgencesBulles: npd.actif ? NotificationManager.Notifications.CriticalUrgency
        : (NotificationManager.Notifications.CriticalUrgency | NotificationManager.Notifications.NormalUrgency)

    // Bulles : seulement les notifications nouvelles, trois au maximum
    NotificationManager.Notifications {
        id: bulles
        showExpired: false
        showDismissed: false
        showAddedDuringInhibition: false
        showJobs: false
        showNotifications: true
        groupMode: NotificationManager.Notifications.GroupDisabled
        sortMode: NotificationManager.Notifications.SortByDate
        blacklistedDesktopEntries: npd.reglages.popupBlacklistedApplications
        blacklistedNotifyRcNames: npd.reglages.popupBlacklistedServices
        whitelistedDesktopEntries: npd.actif ? npd.reglages.doNotDisturbPopupWhitelistedApplications : []
        whitelistedNotifyRcNames: npd.actif ? npd.reglages.doNotDisturbPopupWhitelistedServices : []
        urgencies: notifications.urgencesBulles
        limit: 3
    }
    // Les mêmes, sans limite : pour savoir combien attendent au-delà des trois bulles
    NotificationManager.Notifications {
        id: toutesLesBulles
        showExpired: false
        showDismissed: false
        showAddedDuringInhibition: false
        showJobs: false
        showNotifications: true
        groupMode: NotificationManager.Notifications.GroupDisabled
        blacklistedDesktopEntries: npd.reglages.popupBlacklistedApplications
        blacklistedNotifyRcNames: npd.reglages.popupBlacklistedServices
        whitelistedDesktopEntries: npd.actif ? npd.reglages.doNotDisturbPopupWhitelistedApplications : []
        whitelistedNotifyRcNames: npd.actif ? npd.reglages.doNotDisturbPopupWhitelistedServices : []
        urgencies: notifications.urgencesBulles
    }
    readonly property int enAttente: Math.max(0, toutesLesBulles.count - bulles.count)

    readonly property rect zoneEcran: Plasmoid.containment ? Plasmoid.containment.availableScreenRect : Qt.rect(0, 0, 1280, 800)
    readonly property int largeurBulle: 372
    readonly property int marge: 20
    property var hauteurs: ({})       // hauteur de chaque bulle, pour les empiler
    function positionBulle(rang) {
        var y = zoneEcran.y + marge
        for (var i = 0; i < rang; i++) y += (hauteurs[i] || 120) + 10
        return y
    }

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color texte3: sombre ? "#8C867A" : "#8A8277"

    Instantiator {
        model: bulles

        delegate: PlasmaCore.Dialog {
            id: bulle

            readonly property int duree: model.timeout > 0 ? model.timeout : 7000
            readonly property int rang: index
            readonly property var actions: model.actionNames || []
            readonly property var libelles: model.actionLabels || []

            type: PlasmaCore.Dialog.Notification
            flags: Qt.WindowDoesNotAcceptFocus | Qt.WindowStaysOnTopHint
            location: PlasmaCore.Types.Floating
            backgroundHints: PlasmaCore.Types.StandardBackground
            visible: true

            x: notifications.zoneEcran.x + notifications.zoneEcran.width - width - notifications.marge
            y: notifications.positionBulle(rang)
            onHeightChanged: { var h = notifications.hauteurs; h[rang] = height; notifications.hauteurs = h }
            Component.onCompleted: if (model.created < notifications.finNpd) bulles.expire(bulles.index(index, 0))

            mainItem: MouseArea {
                id: survol

                // Disparition automatique (en pause tant que la souris survole la bulle)
                Timer {
                    interval: bulle.duree
                    running: !survol.containsMouse
                    onTriggered: bulles.expire(bulles.index(index, 0))
                }

                width: notifications.largeurBulle - 24
                height: contenu.implicitHeight + 8
                hoverEnabled: true
                onClicked: {
                    if (model.hasDefaultAction) bulles.invokeDefaultAction(bulles.index(index, 0))
                    bulles.expire(bulles.index(index, 0))
                }

                RowLayout {
                    id: contenu
                    anchors.fill: parent
                    anchors.margins: 4
                    spacing: 12

                    // Icône de l'application dans une tuile (36 px, comme la maquette)
                    Rectangle {
                        Layout.alignment: Qt.AlignTop
                        Layout.preferredWidth: 36
                        Layout.preferredHeight: 36
                        radius: 11
                        color: notifications.sombre ? "#2B3044" : "#F3ECE3"
                        Kirigami.Icon {
                            anchors.centerIn: parent
                            width: 22
                            height: 22
                            source: model.image ? model.image : (model.iconName || model.applicationIconName || "preferences-desktop-notification")
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 3

                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 8
                            Text {
                                Layout.fillWidth: true
                                text: model.applicationName || "Sama"
                                elide: Text.ElideRight
                                font.pixelSize: 12
                                font.weight: Font.DemiBold
                                color: notifications.texte2
                            }
                            Text { text: notifications.quand(model.created); font.pixelSize: 11; color: notifications.texte3 }
                            Text {
                                text: "×"
                                font.pixelSize: 15
                                color: notifications.texte3
                                MouseArea { anchors.fill: parent; anchors.margins: -6; onClicked: bulles.close(bulles.index(index, 0)) }
                            }
                        }
                        Text {
                            Layout.fillWidth: true
                            text: model.summary
                            visible: text.length > 0
                            wrapMode: Text.WordWrap
                            maximumLineCount: 2
                            elide: Text.ElideRight
                            font.pixelSize: 14
                            font.weight: Font.Medium
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
                            font.pixelSize: 13
                            lineHeight: 1.1
                            color: notifications.texte2
                        }
                        // Boutons d'action (« Répondre », « Installer »…) : le premier en latérite
                        Flow {
                            Layout.fillWidth: true
                            Layout.topMargin: 6
                            visible: bulle.actions.length > 0
                            spacing: 8
                            Repeater {
                                model: bulle.actions.length
                                delegate: BoutonNotification {
                                    principal: index === 0
                                    texte: bulle.libelles[index] || bulle.actions[index]
                                    onClicked: {
                                        bulles.invokeAction(bulles.index(bulle.rang, 0), bulle.actions[index])
                                        bulles.expire(bulles.index(bulle.rang, 0))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Au-delà de trois bulles : une seule bulle de résumé, qui ouvre le centre
    PlasmaCore.Dialog {
        id: resume
        type: PlasmaCore.Dialog.Notification
        flags: Qt.WindowDoesNotAcceptFocus | Qt.WindowStaysOnTopHint
        location: PlasmaCore.Types.Floating
        backgroundHints: PlasmaCore.Types.StandardBackground
        visible: notifications.enAttente > 0
        x: notifications.zoneEcran.x + notifications.zoneEcran.width - width - notifications.marge
        y: notifications.positionBulle(bulles.count)
        mainItem: MouseArea {
            width: notifications.largeurBulle - 24
            height: 30
            onClicked: { racine.vue = "notifications"; racine.expanded = true }
            Text {
                anchors.centerIn: parent
                text: notifications.enAttente === 1 ? "1 autre notification" : notifications.enAttente + " autres notifications"
                font.pixelSize: 13
                font.weight: Font.Medium
                color: Kirigami.Theme.textColor
            }
        }
    }
}
