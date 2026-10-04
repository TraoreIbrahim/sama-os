// Carte « Agenda » du bureau de Sama OS (d'après la maquette) : les prochains rendez-vous, avec leur barre de couleur.
// En attendant l'application Agenda de Sama, la carte garde ses propres événements :
//  - « + » : titre, jour et heure, saisis dans la carte ; survol d'un événement : « × » pour le retirer ;
//  - les jours fériés de Côte d'Ivoire à date fixe sont ajoutés d'office ;
//  - les événements passés disparaissent d'eux-mêmes.

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

    readonly property var couleurs: ["#B5532F", "#3D5A99", "#2F6B57", "#C08A1E", "#7A5C99"]

    // Jours fériés à date fixe (Côte d'Ivoire) ; les fêtes mobiles viendront avec l'application Agenda
    readonly property var feries: [
        { mois: 1, jour: 1, titre: "Jour de l'an" },
        { mois: 5, jour: 1, titre: "Fête du Travail" },
        { mois: 8, jour: 7, titre: "Fête de l'Indépendance" },
        { mois: 8, jour: 15, titre: "Assomption" },
        { mois: 11, jour: 1, titre: "Toussaint" },
        { mois: 11, jour: 15, titre: "Journée nationale de la paix" },
        { mois: 12, jour: 25, titre: "Noël" }
    ]

    property date maintenant: new Date()
    Timer {
        interval: 60000
        running: true
        repeat: true
        onTriggered: racine.maintenant = new Date()
    }

    readonly property var evenements: {
        try { return JSON.parse(Plasmoid.configuration.evenements || "[]") } catch (e) { return [] }
    }

    function deuxChiffres(n) { return (n < 10 ? "0" : "") + n }
    function cleJour(d) { return d.getFullYear() + "-" + deuxChiffres(d.getMonth() + 1) + "-" + deuxChiffres(d.getDate()) }
    function dateDe(cle) { var p = cle.split("-"); return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2])) }

    // Prochains événements (les siens + jours fériés des 14 prochains jours), du plus proche au plus lointain
    readonly property var prochains: {
        var aujourdhui = cleJour(maintenant)
        var heure = deuxChiffres(maintenant.getHours()) + ":" + deuxChiffres(maintenant.getMinutes())
        var liste = []
        for (var i = 0; i < evenements.length; i++) {
            var e = evenements[i]
            if (e.date < aujourdhui) continue
            if (e.date === aujourdhui && (e.fin || e.debut || "23:59") < heure) continue
            liste.push({ titre: e.titre, date: e.date, debut: e.debut || "", fin: e.fin || "", couleur: e.couleur, index: i })
        }
        for (var j = 0; j < 14; j++) {
            var d = new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate() + j)
            for (var k = 0; k < feries.length; k++) {
                if (feries[k].mois === d.getMonth() + 1 && feries[k].jour === d.getDate()) {
                    liste.push({ titre: feries[k].titre, date: cleJour(d), debut: "", fin: "", couleur: "#8A8277", index: -1, ferie: true })
                }
            }
        }
        liste.sort(function (a, b) { return (a.date + a.debut).localeCompare(b.date + b.debut) })
        return liste.slice(0, 3)
    }

    function libelleJour(cle) {
        var d = dateDe(cle)
        var ecart = Math.round((d.getTime() - new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate()).getTime()) / 86400000)
        if (ecart === 0) return "Aujourd'hui"
        if (ecart === 1) return "Demain"
        var t = d.toLocaleDateString(Qt.locale(), "ddd d MMM")
        return t.charAt(0).toUpperCase() + t.slice(1)
    }
    function libelleEvenement(e) {
        var j = libelleJour(e.date)
        if (e.ferie) return j + " · Jour férié"
        if (e.debut && e.fin) return j + " · " + e.debut + " – " + e.fin
        if (e.debut) return j + " · " + e.debut
        return j
    }

    function enregistrer(liste) { Plasmoid.configuration.evenements = JSON.stringify(liste) }
    function retirer(index) {
        var liste = evenements.slice()
        liste.splice(index, 1)
        enregistrer(liste)
    }
    // Heures saisies librement : « 9h », « 9h30 », « 09:30 », « 9h – 10h », « 9h à 10h30 »…
    function heures(texte) {
        var r = []
        var re = /(\d{1,2})(?:\s*[h:.]\s*(\d{2})?)?/g
        var m
        while ((m = re.exec(String(texte))) !== null && r.length < 2) {
            var h = Number(m[1]), mn = m[2] ? Number(m[2]) : 0
            if (h <= 23 && mn <= 59) r.push(deuxChiffres(h) + ":" + deuxChiffres(mn))
        }
        return r
    }
    function ajouter(titre, decalageJours, horaire) {
        titre = String(titre).trim()
        if (!titre) return false
        var h = heures(horaire)
        var debut = h.length > 0 ? h[0] : ""
        var fin = h.length > 1 ? h[1] : ""
        var d = new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate() + decalageJours)
        var liste = evenements.slice()
        liste.push({ titre: titre, date: cleJour(d), debut: debut, fin: fin, couleur: couleurs[liste.length % couleurs.length] })
        enregistrer(liste)
        return true
    }

    property bool saisie: false
    property int jourChoisi: 0
    Plasmoid.status: saisie ? PlasmaCore.Types.AcceptingInputStatus : PlasmaCore.Types.ActiveStatus

    fullRepresentation: Item {
        id: carte
        readonly property int marge: Kirigami.Units.gridUnit * 1.2
        Layout.minimumWidth: Kirigami.Units.gridUnit * 14
        Layout.preferredWidth: Kirigami.Units.gridUnit * 17
        Layout.minimumHeight: colonne.implicitHeight + marge * 2
        Layout.preferredHeight: colonne.implicitHeight + marge * 2

        Rectangle {
            anchors.fill: parent
            radius: Kirigami.Units.gridUnit * 1.3
            color: Kirigami.Theme.backgroundColor
            opacity: 0.72
            border.width: 1
            border.color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)
        }

        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: carte.marge
            spacing: 12

            RowLayout {
                Layout.fillWidth: true
                Text {
                    Layout.fillWidth: true
                    text: "Agenda"
                    font.pixelSize: 12
                    color: Kirigami.Theme.disabledTextColor
                }
                // Ajouter un événement
                MouseArea {
                    Layout.preferredWidth: 24
                    Layout.preferredHeight: 24
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: { racine.jourChoisi = 0; racine.saisie = !racine.saisie }
                    Rectangle {
                        anchors.fill: parent
                        radius: 12
                        color: racine.saisie ? "#B5532F" : Kirigami.Theme.textColor
                        opacity: racine.saisie ? 1 : (parent.containsMouse ? 0.12 : 0.06)
                    }
                    Text {
                        anchors.centerIn: parent
                        anchors.verticalCenterOffset: -1
                        text: racine.saisie ? "×" : "+"
                        font.pixelSize: 16
                        color: racine.saisie ? "#FFFFFF" : Kirigami.Theme.textColor
                    }
                }
            }

            // Saisie d'un événement
            ColumnLayout {
                visible: racine.saisie
                Layout.fillWidth: true
                spacing: 8
                QQC2.TextField {
                    id: champTitre
                    background: Rectangle {
                        radius: 10
                        color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)
                        border.width: parent.activeFocus ? 1.5 : 0
                        border.color: "#B5532F"
                    }
                    leftPadding: 12
                    rightPadding: 12
                    Layout.fillWidth: true
                    placeholderText: "Quoi ? (ex. Réunion de service)"
                    Layout.preferredHeight: 34
                    font.pixelSize: 13
                    onVisibleChanged: if (visible) { text = ""; champHeure.text = ""; forceActiveFocus() }
                    onAccepted: champHeure.forceActiveFocus()
                    Keys.onEscapePressed: racine.saisie = false
                }
                // Jour : aujourd'hui, demain, puis les jours suivants
                Flow {
                    Layout.fillWidth: true
                    spacing: 6
                    Repeater {
                        model: 5
                        delegate: MouseArea {
                            id: pilule
                            readonly property bool choisi: racine.jourChoisi === index
                            width: etiquette.implicitWidth + 18
                            height: 24
                            cursorShape: Qt.PointingHandCursor
                            onClicked: racine.jourChoisi = index
                            Rectangle {
                                anchors.fill: parent
                                radius: 12
                                color: pilule.choisi ? "#B5532F"
                                     : Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)
                            }
                            Text {
                                id: etiquette
                                anchors.centerIn: parent
                                text: index === 0 ? "Aujourd'hui" : index === 1 ? "Demain"
                                    : new Date(racine.maintenant.getFullYear(), racine.maintenant.getMonth(), racine.maintenant.getDate() + index)
                                          .toLocaleDateString(Qt.locale(), "ddd d")
                                font.pixelSize: 11
                                font.weight: Font.Medium
                                color: pilule.choisi ? "#FFFFFF" : Kirigami.Theme.textColor
                            }
                        }
                    }
                }
                QQC2.TextField {
                    id: champHeure
                    background: Rectangle {
                        radius: 10
                        color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)
                        border.width: parent.activeFocus ? 1.5 : 0
                        border.color: "#B5532F"
                    }
                    leftPadding: 12
                    rightPadding: 12
                    Layout.fillWidth: true
                    placeholderText: "Heure, facultative (ex. 9h – 10h)"
                    Layout.preferredHeight: 34
                    font.pixelSize: 12
                    onAccepted: if (racine.ajouter(champTitre.text, racine.jourChoisi, text)) racine.saisie = false
                    Keys.onEscapePressed: racine.saisie = false
                }
                QQC2.AbstractButton {
                    Layout.alignment: Qt.AlignRight
                    Layout.preferredHeight: 30
                    enabled: champTitre.text.trim().length > 0
                    contentItem: Text {
                        text: "Ajouter"
                        font.pixelSize: 13
                        font.weight: Font.DemiBold
                        color: "#FFFFFF"
                        leftPadding: 16
                        rightPadding: 16
                        verticalAlignment: Text.AlignVCenter
                    }
                    background: Rectangle { radius: 15; color: "#B5532F"; opacity: parent.enabled ? 1 : 0.4 }
                    onClicked: if (racine.ajouter(champTitre.text, racine.jourChoisi, champHeure.text)) racine.saisie = false
                }
            }

            // Prochains événements
            Text {
                visible: !racine.saisie && racine.prochains.length === 0
                text: "Rien de prévu pour l'instant"
                font.pixelSize: 13
                color: Kirigami.Theme.disabledTextColor
            }
            Repeater {
                model: racine.saisie ? [] : racine.prochains
                delegate: MouseArea {
                    id: ligne
                    Layout.fillWidth: true
                    Layout.preferredHeight: infos.implicitHeight
                    hoverEnabled: true
                    RowLayout {
                        anchors.fill: parent
                        spacing: 12
                        Rectangle {
                            Layout.preferredWidth: 3
                            Layout.fillHeight: true
                            radius: 2
                            color: modelData.couleur
                        }
                        ColumnLayout {
                            id: infos
                            Layout.fillWidth: true
                            spacing: 1
                            Text {
                                Layout.fillWidth: true
                                text: modelData.titre
                                elide: Text.ElideRight
                                font.pixelSize: 14
                                font.weight: Font.Medium
                                color: Kirigami.Theme.textColor
                            }
                            Text {
                                Layout.fillWidth: true
                                text: racine.libelleEvenement(modelData)
                                elide: Text.ElideRight
                                font.pixelSize: 12
                                color: Kirigami.Theme.disabledTextColor
                            }
                        }
                        Text {
                            visible: ligne.containsMouse && modelData.index >= 0
                            text: "×"
                            font.pixelSize: 16
                            color: Kirigami.Theme.disabledTextColor
                            MouseArea {
                                anchors.fill: parent
                                anchors.margins: -6
                                cursorShape: Qt.PointingHandCursor
                                onClicked: racine.retirer(modelData.index)
                            }
                        }
                    }
                }
            }
        }
    }
}
