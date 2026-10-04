// Carte « Agenda » du bureau de Sama OS (d'après la maquette) : les prochains rendez-vous, avec leur barre de couleur.
// En attendant l'application Agenda de Sama, la carte garde ses propres événements :
//  - « + » : titre, jour et heure, saisis dans la carte ; survol d'un événement : « × » pour le retirer ;
//  - les jours fériés de Côte d'Ivoire à date fixe sont ajoutés d'office ;
//  - les événements passés disparaissent d'eux-mêmes ;
//  - un clic sur la carte ouvre la semaine et tous les rendez-vous à venir (en attendant l'application Agenda).

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

    // Tous les événements des 14 prochains jours (panneau détaillé)
    readonly property var aVenir: {
        var aujourdhui = cleJour(maintenant)
        var liste = []
        for (var i = 0; i < evenements.length; i++) {
            if (evenements[i].date >= aujourdhui) liste.push({ titre: evenements[i].titre, date: evenements[i].date,
                debut: evenements[i].debut || "", fin: evenements[i].fin || "", couleur: evenements[i].couleur, index: i })
        }
        for (var j = 0; j < 14; j++) {
            var d = new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate() + j)
            for (var k = 0; k < feries.length; k++) {
                if (feries[k].mois === d.getMonth() + 1 && feries[k].jour === d.getDate())
                    liste.push({ titre: feries[k].titre, date: cleJour(d), debut: "", fin: "", couleur: "#8A8277", index: -1, ferie: true })
            }
        }
        liste.sort(function (a, b) { return (a.date + a.debut).localeCompare(b.date + b.debut) })
        return liste
    }
    function aDesEvenements(cle) {
        for (var i = 0; i < aVenir.length; i++) if (aVenir[i].date === cle) return true
        return false
    }
    property string jourFiltre: ""   // jour choisi dans la semaine du panneau (vide : tous)

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

        // Clic sur la carte : la semaine et tous les rendez-vous
        MouseArea {
            anchors.fill: parent
            enabled: !racine.saisie
            cursorShape: Qt.PointingHandCursor
            onClicked: { racine.jourFiltre = ""; details.visible = !details.visible }
        }

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

                RowLayout {
                    Layout.fillWidth: true
                    Text {
                        Layout.fillWidth: true
                        text: {
                            var t = racine.maintenant.toLocaleDateString(Qt.locale(), "MMMM yyyy")
                            return t.charAt(0).toUpperCase() + t.slice(1)
                        }
                        font.pixelSize: 17
                        font.weight: Font.Medium
                        color: Kirigami.Theme.textColor
                    }
                    QQC2.AbstractButton {
                        Layout.preferredHeight: 28
                        contentItem: Text {
                            text: "+ Ajouter"
                            font.pixelSize: 12
                            font.weight: Font.DemiBold
                            color: "#FFFFFF"
                            leftPadding: 12
                            rightPadding: 12
                            verticalAlignment: Text.AlignVCenter
                        }
                        background: Rectangle { radius: 14; color: "#B5532F" }
                        onClicked: { details.visible = false; racine.jourChoisi = 0; racine.saisie = true }
                    }
                }

                // La semaine : aujourd'hui en latérite, un point sous les jours qui ont des rendez-vous
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 4
                    Repeater {
                        model: 7
                        delegate: MouseArea {
                            id: jour
                            readonly property date date: new Date(racine.maintenant.getFullYear(), racine.maintenant.getMonth(), racine.maintenant.getDate() + index)
                            readonly property string cle: racine.cleJour(date)
                            readonly property bool choisi: racine.jourFiltre === cle
                            Layout.fillWidth: true
                            Layout.preferredHeight: 58
                            cursorShape: Qt.PointingHandCursor
                            onClicked: racine.jourFiltre = choisi ? "" : cle
                            Rectangle {
                                anchors.fill: parent
                                radius: 12
                                color: jour.choisi ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.14) : "transparent"
                            }
                            Column {
                                anchors.centerIn: parent
                                spacing: 4
                                Text {
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    text: jour.date.toLocaleDateString(Qt.locale(), "ddd").substring(0, 3)
                                    font.pixelSize: 11
                                    color: Kirigami.Theme.disabledTextColor
                                }
                                Rectangle {
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    width: 26
                                    height: 26
                                    radius: 13
                                    color: index === 0 ? "#B5532F" : "transparent"
                                    Text {
                                        anchors.centerIn: parent
                                        text: jour.date.getDate()
                                        font.pixelSize: 13
                                        font.weight: Font.Medium
                                        color: index === 0 ? "#FFFFFF" : Kirigami.Theme.textColor
                                    }
                                }
                                Rectangle {
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    width: 4
                                    height: 4
                                    radius: 2
                                    color: "#B5532F"
                                    opacity: racine.aDesEvenements(jour.cle) ? 1 : 0
                                }
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

                // Rendez-vous, jour par jour
                Repeater {
                    model: racine.aVenir.filter(function (e) { return !racine.jourFiltre || e.date === racine.jourFiltre })
                    delegate: ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 6
                        readonly property bool nouveauJour: {
                            var l = racine.aVenir.filter(function (e) { return !racine.jourFiltre || e.date === racine.jourFiltre })
                            return index === 0 || l[index - 1].date !== modelData.date
                        }
                        Text {
                            visible: parent.nouveauJour
                            Layout.topMargin: index === 0 ? 0 : 4
                            text: racine.libelleJour(modelData.date).toUpperCase()
                            font.pixelSize: 11
                            font.weight: Font.DemiBold
                            font.letterSpacing: 0.4
                            color: Kirigami.Theme.disabledTextColor
                        }
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 12
                            Rectangle { Layout.preferredWidth: 3; Layout.preferredHeight: 34; radius: 2; color: modelData.couleur }
                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 1
                                Text { Layout.fillWidth: true; text: modelData.titre; elide: Text.ElideRight; font.pixelSize: 14; font.weight: Font.Medium; color: Kirigami.Theme.textColor }
                                Text {
                                    text: modelData.ferie ? "Jour férié" : (modelData.debut ? modelData.debut + (modelData.fin ? " – " + modelData.fin : "") : "Toute la journée")
                                    font.pixelSize: 12
                                    color: Kirigami.Theme.disabledTextColor
                                }
                            }
                            Text {
                                visible: modelData.index >= 0
                                text: "×"
                                font.pixelSize: 16
                                color: Kirigami.Theme.disabledTextColor
                                MouseArea { anchors.fill: parent; anchors.margins: -6; cursorShape: Qt.PointingHandCursor; onClicked: racine.retirer(modelData.index) }
                            }
                        }
                    }
                }
                Text {
                    visible: racine.aVenir.filter(function (e) { return !racine.jourFiltre || e.date === racine.jourFiltre }).length === 0
                    text: racine.jourFiltre ? "Rien de prévu ce jour-là" : "Rien de prévu ces deux prochaines semaines"
                    font.pixelSize: 13
                    color: Kirigami.Theme.disabledTextColor
                }
            }
        }

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
