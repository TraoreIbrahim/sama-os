/* Installateur de Sama — « Votre région » (maquette dem-03, colonne de gauche) : fuseau horaire, langue du
 * système et format des dates, nombres et monnaie. Par défaut : Abidjan (localeq.conf), français.
 */
import io.calamares.core 1.0
import io.calamares.ui 1.0
import QtQuick
import QtQuick.Layouts
import "composants"

Rectangle {
    id: page
    color: "#FBF9F6"
    property date maintenant: new Date()
    Timer { interval: 20000; running: true; repeat: true; onTriggered: page.maintenant = new Date() }

    function sansEncodage(code) { return String(code || "").split(".")[0].split(" ")[0] }
    function nomLocale(code) {
        var l = Qt.locale(sansEncodage(code))
        var langue = l.nativeLanguageName || code
        langue = langue.charAt(0).toUpperCase() + langue.slice(1)
        return l.nativeTerritoryName ? langue + " (" + l.nativeTerritoryName + ")" : langue
    }
    function ville(fuseau) {
        var morceaux = String(fuseau || "").split("/")
        return morceaux[morceaux.length - 1].replace(/_/g, " ")
    }
    property string regionChoisie: ""
    // Format choisi ; vide au démarrage de Calamares : celui de la langue du système
    readonly property string codeFormat: config.currentLCCode || config.currentLanguageCode

    ColumnLayout {
        anchors.fill: parent
        anchors.leftMargin: 40
        anchors.rightMargin: 40
        anchors.topMargin: 30
        spacing: 0

        Text { text: "Votre région"; font.pixelSize: 26; font.weight: Font.Medium; color: "#1F1C18" }
        Text {
            Layout.topMargin: 6
            text: "Sama règle l'heure, la date et la monnaie selon votre pays."
            font.pixelSize: 14
            color: "#665E54"
        }

        ColumnLayout {
            Layout.topMargin: 26
            Layout.preferredWidth: 420
            spacing: 16
            Selecteur {
                Layout.fillWidth: true
                libelle: "Fuseau horaire"
                picto: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z"
                valeur: page.ville(config.currentTimezoneName)
                detail: "Il est " + Qt.formatTime(page.maintenant, "hh:mm") + " ici"
                onOuvrir: listeRegions.ouvrir()
            }
            Selecteur {
                Layout.fillWidth: true
                libelle: "Langue du système"
                picto: "M4 6h10 M9 4v2 M6 6c1 4 4 7 8 8 M12 6c-1 4-4 7-8 8 M13 20l4-9l4 9 M14.5 17h5"
                valeur: page.nomLocale(config.currentLanguageCode)
                onOuvrir: listeLangues.ouvrir()
            }
            Selecteur {
                Layout.fillWidth: true
                libelle: "Format des dates, nombres et monnaie"
                picto: "M5 6h14v14H5z M5 10h14 M9 4v4 M15 4v4"
                valeur: page.nomLocale(page.codeFormat)
                detail: page.maintenant.toLocaleDateString(Qt.locale(page.sansEncodage(page.codeFormat)), "d MMMM yyyy")
                        + " · " + Number(1250000).toLocaleCurrencyString(Qt.locale(page.sansEncodage(page.codeFormat)))
                onOuvrir: listeFormats.ouvrir()
            }
        }
        Item { Layout.fillHeight: true }
    }

    Liste {
        id: listeRegions
        titre: "Région du monde"
        modele: config.regionModel
        texte: function (e) { return String(e.name) }
        onChoisi: (e) => {
            page.regionChoisie = String(e.name)
            config.regionalZonesModel.region = page.regionChoisie
            listeZones.ouvrir()
        }
    }
    Liste {
        id: listeZones
        titre: "Ville de votre fuseau horaire"
        modele: config.regionalZonesModel
        texte: function (e) { return String(e.name).replace(/_/g, " ") }
        onChoisi: (e) => config.setCurrentLocation(page.regionChoisie, String(e.name))
    }
    Liste {
        id: listeLangues
        titre: "Langue du système"
        modele: config.supportedLocales
        texte: function (e) { return page.nomLocale(e) }
        onChoisi: (e) => config.currentLanguageCode = String(e)
    }
    Liste {
        id: listeFormats
        titre: "Format des dates, nombres et monnaie"
        modele: config.supportedLocales
        texte: function (e) { return page.nomLocale(e) }
        onChoisi: (e) => config.currentLCCode = String(e)
    }
}
