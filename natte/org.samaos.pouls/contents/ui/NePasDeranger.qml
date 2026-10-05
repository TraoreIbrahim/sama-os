// Ne pas déranger : suspend les bulles de notification jusqu'à désactivation (réglage partagé avec Plasma).
import QtQuick
import org.kde.notificationmanager as NotificationManager

QtObject {
    property date maintenant: new Date()
    readonly property bool actif: {
        var jusqua = reglages.notificationsInhibitedUntil
        return !!jusqua && !isNaN(jusqua.getTime()) && jusqua.getTime() > maintenant.getTime()
    }
    // live : suit les changements faits ailleurs (Réglages → Notifications)
    property NotificationManager.Settings reglages: NotificationManager.Settings { live: true }
    function basculer() {
        maintenant = new Date()
        if (actif) {
            reglages.notificationsInhibitedUntil = undefined
        } else {
            // « Jusqu'à désactivation » : une date très lointaine, comme le fait Plasma
            reglages.notificationsInhibitedUntil = new Date(maintenant.getTime() + 10 * 365 * 24 * 3600 * 1000)
        }
        reglages.save()
        maintenant = new Date()
    }
}
