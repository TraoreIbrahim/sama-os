// Notifications : Ne pas déranger, et pour chaque application ou service du système, ce qui a le droit de
// s'afficher (bulles à l'écran, centre de notifications). Les choix sont ceux de Plasma (plasmanotifyrc),
// suivis par la Natte. Un clic sur une ligne ouvre le détail (NotificationsApplication.qml).
import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.notificationmanager as NotificationManager
import ".."

PageReglage {
    id: page
    titre: "Notifications"
    Commande { id: commande }
    property var elements: []
    readonly property var applications: elements.filter(function (e) { return e.type === "app" })
    readonly property var services: elements.filter(function (e) { return e.type === "service" })

    function relire() {
        commande.lancer("python3 /usr/libexec/samaos/notifications.py liste", function (s) {
            try { page.elements = JSON.parse(s.trim()) } catch (e) {}
        })
    }
    Component.onCompleted: relire()

    function resume(e) {
        if (!e.bulles && !e.centre) return "Désactivées"
        if (e.bulles && e.centre) return "Bulles et centre de notifications"
        return e.centre ? "Dans le centre seulement, sans bulle" : "Bulles seulement"
    }

    // Ne pas déranger (même réglage que le bouton du centre de notifications)
    NotificationManager.Settings { id: reglagesPlasma; live: true }
    property date maintenant: new Date()
    readonly property bool nePasDeranger: {
        var jusqua = reglagesPlasma.notificationsInhibitedUntil
        return !!jusqua && !isNaN(jusqua.getTime()) && jusqua.getTime() > maintenant.getTime()
    }

    Groupe {
        Ligne {
            titre: "Ne pas déranger"
            detail: "Les notifications arrivent en silence dans le centre de notifications. Les alertes importantes (batterie, forfait) s'affichent quand même."
            derniere: true
            Interrupteur {
                actif: page.nePasDeranger
                onBascule: a => {
                    reglagesPlasma.notificationsInhibitedUntil = a ? new Date(Date.now() + 10 * 365 * 24 * 3600 * 1000) : undefined
                    reglagesPlasma.save()
                    page.maintenant = new Date()
                }
            }
        }
    }

    component LigneElement: Ligne {
        required property var modelData
        required property int index
        titre: modelData.nom
        detail: page.resume(modelData)
        cliquable: true
        onClique: fenetre.ouvrir("NotificationsApplication", modelData)
        gauche: Kirigami.Icon { width: 28; height: 28; source: modelData.icone }
        Text { text: "›"; font.pixelSize: 20; color: Couleurs.texte3 }
    }

    Groupe {
        titre: "Applications"
        Repeater {
            model: page.applications
            delegate: LigneElement { derniere: index === page.applications.length - 1 }
        }
    }
    Groupe {
        titre: "Système"
        Repeater {
            model: page.services
            delegate: LigneElement { derniere: index === page.services.length - 1 }
        }
    }
}
