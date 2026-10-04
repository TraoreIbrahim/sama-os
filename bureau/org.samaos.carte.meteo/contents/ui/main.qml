// Carte « Météo » du bureau de Sama OS (d'après la maquette) : icône, ville, ciel, température.
// Données : Open-Meteo (service libre, sans compte ni clé). Mise à jour toutes les 30 minutes.
// Hors ligne : la dernière météo reçue reste affichée avec son âge (« il y a 2 h »).
// Un clic sur le nom de la ville permet d'en choisir une autre ; un clic ailleurs sur la carte ouvre les prévisions
// détaillées (maintenant et 5 jours), en attendant l'application Météo de Sama.

import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.plasma.plasmoid
import org.kde.plasma.core as PlasmaCore
import org.kde.kirigami as Kirigami

PlasmoidItem {
    id: racine

    Plasmoid.backgroundHints: PlasmaCore.Types.NoBackground
    preferredRepresentation: fullRepresentation

    property var meteo: {
        try { return JSON.parse(Plasmoid.configuration.derniereMeteo || "null") } catch (e) { return null }
    }
    property bool hautDeLigne: true       // vrai tant que la dernière demande a réussi
    property bool saisieVille: false
    property string messageVille: ""
    property date maintenant: new Date()

    // Codes météo de l'OMM → libellé et icône
    function ciel(code, jour) {
        if (code === 0) return { texte: jour ? "Ensoleillé" : "Ciel dégagé", icone: jour ? "soleil" : "lune" }
        if (code === 1 || code === 2) return { texte: "Partiellement nuageux", icone: jour ? "soleil-nuage" : "nuage" }
        if (code === 3) return { texte: "Couvert", icone: "nuage" }
        if (code === 45 || code === 48) return { texte: "Brume", icone: "brume" }
        if (code >= 51 && code <= 57) return { texte: "Bruine", icone: "pluie" }
        if (code >= 61 && code <= 67) return { texte: "Pluie", icone: "pluie" }
        if (code >= 71 && code <= 77) return { texte: "Neige", icone: "nuage" }
        if (code >= 80 && code <= 82) return { texte: "Averses", icone: "pluie" }
        if (code >= 95) return { texte: "Orage", icone: "orage" }
        return { texte: "—", icone: "nuage" }
    }
    readonly property var etat: meteo ? ciel(meteo.current.weather_code, meteo.current.is_day === 1) : null

    function age() {
        var t = Number(Plasmoid.configuration.miseAJour || 0)
        if (!t) return ""
        var minutes = Math.round((maintenant.getTime() - t) / 60000)
        if (minutes < 60) return ""
        var heures = Math.round(minutes / 60)
        return heures < 24 ? "il y a " + heures + " h" : "il y a " + Math.round(heures / 24) + " j"
    }

    function actualiser() {
        var url = "https://api.open-meteo.com/v1/forecast?latitude=" + Plasmoid.configuration.latitude
                + "&longitude=" + Plasmoid.configuration.longitude
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5"
        var requete = new XMLHttpRequest()
        requete.onreadystatechange = function () {
            if (requete.readyState !== XMLHttpRequest.DONE) return
            if (requete.status === 200) {
                try {
                    JSON.parse(requete.responseText)
                    Plasmoid.configuration.derniereMeteo = requete.responseText
                    Plasmoid.configuration.miseAJour = String(Date.now())
                    racine.hautDeLigne = true
                } catch (e) { racine.hautDeLigne = false }
            } else {
                racine.hautDeLigne = false
            }
        }
        requete.open("GET", url)
        requete.send()
    }

    // Choisir une ville : recherche par nom (Open-Meteo, géocodage en français)
    function chercherVille(nom) {
        nom = String(nom).trim()
        if (!nom) { saisieVille = false; return }
        messageVille = "Recherche…"
        var requete = new XMLHttpRequest()
        requete.onreadystatechange = function () {
            if (requete.readyState !== XMLHttpRequest.DONE) return
            try {
                var r = JSON.parse(requete.responseText)
                if (r.results && r.results.length > 0) {
                    Plasmoid.configuration.ville = r.results[0].name
                    Plasmoid.configuration.latitude = r.results[0].latitude
                    Plasmoid.configuration.longitude = r.results[0].longitude
                    racine.saisieVille = false
                    racine.messageVille = ""
                    racine.actualiser()
                } else {
                    racine.messageVille = "Ville introuvable"
                }
            } catch (e) {
                racine.messageVille = "Pas de connexion"
            }
        }
        requete.open("GET", "https://geocoding-api.open-meteo.com/v1/search?count=1&language=fr&name=" + encodeURIComponent(nom))
        requete.send()
    }

    Timer {
        interval: 30 * 60 * 1000
        running: true
        repeat: true
        triggeredOnStart: true
        onTriggered: { racine.maintenant = new Date(); racine.actualiser() }
    }
    Timer {
        interval: 60000
        running: true
        repeat: true
        onTriggered: racine.maintenant = new Date()
    }

    // Saisie : la carte doit recevoir le clavier
    Plasmoid.status: saisieVille ? PlasmaCore.Types.AcceptingInputStatus : PlasmaCore.Types.ActiveStatus

    function jourCourt(cle, i) {
        if (i === 0) return "Aujourd'hui"
        var p = String(cle).split("-")
        var t = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2])).toLocaleDateString(Qt.locale(), "ddd d")
        return t.charAt(0).toUpperCase() + t.slice(1)
    }

    fullRepresentation: Item {
        id: carte
        Layout.minimumWidth: Kirigami.Units.gridUnit * 14
        Layout.preferredWidth: Kirigami.Units.gridUnit * 17
        Layout.minimumHeight: Kirigami.Units.gridUnit * 5.5
        Layout.preferredHeight: Kirigami.Units.gridUnit * 5.5

        Rectangle {
            anchors.fill: parent
            radius: Kirigami.Units.gridUnit * 1.3
            color: Kirigami.Theme.backgroundColor
            opacity: 0.72
            border.width: 1
            border.color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)
        }

        // Clic sur la carte : prévisions détaillées
        MouseArea {
            anchors.fill: parent
            enabled: !racine.saisieVille
            cursorShape: Qt.PointingHandCursor
            onClicked: details.visible = !details.visible
        }

        // Prévisions détaillées, à côté de la carte
        PlasmaCore.Dialog {
            id: details
            visualParent: carte
            location: PlasmaCore.Types.LeftEdge
            type: PlasmaCore.Dialog.PopupMenu
            hideOnWindowDeactivate: true
            flags: Qt.WindowStaysOnTopHint
            visible: false
            // Une fois ouvert, le panneau prend le clavier : Échap ou un clic ailleurs le referme
            onVisibleChanged: if (visible) requestActivate()

            mainItem: ColumnLayout {
                width: 340
                height: implicitHeight   // suit le contenu (les prévisions arrivent après l'ouverture)
                spacing: 14
                focus: true
                Keys.onEscapePressed: details.visible = false

                // Maintenant
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 14
                    Image {
                        Layout.preferredWidth: 64
                        Layout.preferredHeight: 64
                        source: Qt.resolvedUrl("../icons/" + (racine.etat ? racine.etat.icone : "nuage") + ".svg")
                        sourceSize.width: 128
                        sourceSize.height: 128
                    }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 0
                        Text { text: Plasmoid.configuration.ville; font.pixelSize: 17; font.weight: Font.Medium; color: Kirigami.Theme.textColor }
                        Text { text: racine.etat ? racine.etat.texte : ""; font.pixelSize: 13; color: Kirigami.Theme.disabledTextColor }
                    }
                    Text {
                        text: racine.meteo ? Math.round(racine.meteo.current.temperature_2m) + "°" : "–"
                        font.pixelSize: 44
                        font.weight: Font.Light
                        color: Kirigami.Theme.textColor
                    }
                }
                // Ressenti, humidité, vent
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8
                    Repeater {
                        model: racine.meteo && racine.meteo.current.apparent_temperature !== undefined ? [
                            { titre: "Ressenti", valeur: Math.round(racine.meteo.current.apparent_temperature) + "°" },
                            { titre: "Humidité", valeur: Math.round(racine.meteo.current.relative_humidity_2m) + " %" },
                            { titre: "Vent", valeur: Math.round(racine.meteo.current.wind_speed_10m) + " km/h" }
                        ] : []
                        delegate: Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: 52
                            radius: 12
                            color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.05)
                            Column {
                                anchors.centerIn: parent
                                spacing: 1
                                Text { anchors.horizontalCenter: parent.horizontalCenter; text: modelData.titre; font.pixelSize: 11; color: Kirigami.Theme.disabledTextColor }
                                Text { anchors.horizontalCenter: parent.horizontalCenter; text: modelData.valeur; font.pixelSize: 15; font.weight: Font.Medium; color: Kirigami.Theme.textColor }
                            }
                        }
                    }
                }
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 1
                    color: Kirigami.Theme.textColor
                    opacity: 0.08
                }
                // 5 prochains jours
                Repeater {
                    model: racine.meteo && racine.meteo.daily.time && racine.meteo.daily.weather_code ? racine.meteo.daily.time.length : 0
                    delegate: RowLayout {
                        Layout.fillWidth: true
                        spacing: 12
                        Text {
                            Layout.preferredWidth: 96
                            text: racine.jourCourt(racine.meteo.daily.time[index], index)
                            font.pixelSize: 13
                            font.weight: index === 0 ? Font.DemiBold : Font.Normal
                            color: Kirigami.Theme.textColor
                        }
                        Image {
                            Layout.preferredWidth: 28
                            Layout.preferredHeight: 28
                            source: Qt.resolvedUrl("../icons/" + racine.ciel(racine.meteo.daily.weather_code[index], true).icone + ".svg")
                            sourceSize.width: 56
                            sourceSize.height: 56
                        }
                        Text {
                            Layout.fillWidth: true
                            text: racine.ciel(racine.meteo.daily.weather_code[index], true).texte
                            elide: Text.ElideRight
                            font.pixelSize: 12
                            color: Kirigami.Theme.disabledTextColor
                        }
                        Text {
                            text: Math.round(racine.meteo.daily.temperature_2m_max[index]) + "°"
                            font.pixelSize: 13
                            font.weight: Font.Medium
                            color: Kirigami.Theme.textColor
                        }
                        Text {
                            text: Math.round(racine.meteo.daily.temperature_2m_min[index]) + "°"
                            font.pixelSize: 13
                            color: Kirigami.Theme.disabledTextColor
                        }
                    }
                }
                Text {
                    visible: !racine.meteo || !racine.meteo.daily.weather_code
                    text: "Prévisions disponibles à la prochaine connexion"
                    font.pixelSize: 12
                    color: Kirigami.Theme.disabledTextColor
                }
                Text {
                    text: "Données Open-Meteo" + (racine.age() ? " · " + racine.age() : "")
                    font.pixelSize: 11
                    color: Kirigami.Theme.disabledTextColor
                }
            }
        }

        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: Kirigami.Units.gridUnit * 1.1
            anchors.rightMargin: Kirigami.Units.gridUnit * 1.3
            spacing: Kirigami.Units.gridUnit * 0.9

            Image {
                Layout.preferredWidth: 52
                Layout.preferredHeight: 52
                source: Qt.resolvedUrl("../icons/" + (racine.etat ? racine.etat.icone : "nuage") + ".svg")
                sourceSize.width: 104
                sourceSize.height: 104
                opacity: racine.meteo ? 1 : 0.4
            }

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2

                // Ville (clic : en changer)
                Text {
                    visible: !racine.saisieVille
                    Layout.fillWidth: true
                    text: "Météo · " + Plasmoid.configuration.ville
                    elide: Text.ElideRight
                    font.pixelSize: 12
                    color: Kirigami.Theme.disabledTextColor
                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        onClicked: racine.saisieVille = true
                    }
                }
                QQC2.TextField {
                    id: champVille
                    background: Rectangle {
                        radius: 10
                        color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)
                        border.width: parent.activeFocus ? 1.5 : 0
                        border.color: "#B5532F"
                    }
                    leftPadding: 12
                    rightPadding: 12
                    visible: racine.saisieVille
                    Layout.fillWidth: true
                    Layout.preferredHeight: 26
                    font.pixelSize: 12
                    placeholderText: "Votre ville"
                    onVisibleChanged: if (visible) { text = ""; forceActiveFocus() }
                    onAccepted: racine.chercherVille(text)
                    Keys.onEscapePressed: { racine.saisieVille = false; racine.messageVille = "" }
                }
                Text {
                    Layout.fillWidth: true
                    text: racine.saisieVille ? (racine.messageVille || "Entrée pour valider")
                        : !racine.meteo ? (racine.hautDeLigne ? "Chargement…" : "Pas encore de données")
                        : racine.etat.texte + (racine.age() ? " · " + racine.age() : "")
                    elide: Text.ElideRight
                    font.pixelSize: 13
                    color: Kirigami.Theme.disabledTextColor
                }
                Text {
                    visible: !!racine.meteo && !racine.saisieVille
                    text: racine.meteo ? "↑ " + Math.round(racine.meteo.daily.temperature_2m_max[0]) + "°  ↓ "
                                         + Math.round(racine.meteo.daily.temperature_2m_min[0]) + "°" : ""
                    font.pixelSize: 11
                    color: Kirigami.Theme.disabledTextColor
                }
            }

            Text {
                text: racine.meteo ? Math.round(racine.meteo.current.temperature_2m) + "°" : "–"
                font.pixelSize: 34
                font.weight: Font.Light
                color: Kirigami.Theme.textColor
            }
        }
    }
}
