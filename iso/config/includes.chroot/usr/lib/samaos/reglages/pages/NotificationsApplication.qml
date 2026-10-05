// Notifications d'une application (ou d'un service du système) : autoriser, bulles, centre, Ne pas déranger.
import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import ".."

PageReglage {
    id: page
    property var element: fenetre.parametre || ({ type: "app", id: "", nom: "", icone: "", detail: "", bulles: true, centre: true, npd: false })
    titre: element.nom
    Commande { id: commande }

    function regler(cle, etat) {
        var e = JSON.parse(JSON.stringify(element))
        e[cle] = etat
        element = e
        commande.lancer("python3 /usr/libexec/samaos/notifications.py regler " + e.type + " " + commande.q(e.id) + " " + cle + " " + etat)
    }
    readonly property bool autorisees: element.bulles || element.centre

    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 84
        radius: 14
        color: Couleurs.carte
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 20
            anchors.rightMargin: 20
            spacing: 16
            Kirigami.Icon { Layout.preferredWidth: 44; Layout.preferredHeight: 44; source: page.element.icone }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text { text: page.element.nom; font.pixelSize: 18; font.weight: Font.Medium; color: Couleurs.texte }
                Text {
                    Layout.fillWidth: true
                    text: page.element.detail || (page.element.type === "app" ? "Application" : "Service du système")
                    wrapMode: Text.WordWrap
                    font.pixelSize: 12
                    color: Couleurs.texte2
                }
            }
        }
    }

    Groupe {
        Ligne {
            titre: "Autoriser les notifications"
            detail: page.autorisees ? "" : "Rien ne s'affiche, et rien n'est gardé dans le centre de notifications"
            derniere: !page.autorisees
            Interrupteur {
                actif: page.autorisees
                onBascule: a => { page.regler("bulles", a); page.regler("centre", a) }
            }
        }
        Ligne {
            visible: page.autorisees
            titre: "Bulles à l'écran"
            detail: "En haut à droite, quelques secondes"
            Interrupteur { actif: page.element.bulles; onBascule: a => page.regler("bulles", a) }
        }
        Ligne {
            visible: page.autorisees
            titre: "Garder dans le centre de notifications"
            detail: "Pour les retrouver plus tard, d'un clic sur l'heure"
            Interrupteur { actif: page.element.centre; onBascule: a => page.regler("centre", a) }
        }
        Ligne {
            visible: page.autorisees
            titre: "Même en Ne pas déranger"
            detail: "Les bulles de cette application s'affichent quand même"
            derniere: true
            Interrupteur { actif: page.element.npd; onBascule: a => page.regler("npd", a) }
        }
    }
}
