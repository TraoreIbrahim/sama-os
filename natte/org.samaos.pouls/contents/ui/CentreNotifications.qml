// Centre de notifications de Sama (moitié droite du Pouls : l'heure et le point latérite).
//   en-tête : la date, le nombre de nouveautés, le bouton Ne pas déranger ;
//   Aujourd'hui : les rendez-vous du jour (cartes Agenda de tous les Espaces) ;
//   notifications groupées par application (maquette bur-03) : deux visibles par groupe, « N de plus » pour
//   déplier, × pour effacer une notification ou tout le groupe, « Tout effacer » pour l'historique.

import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.plasma5support as P5Support
import org.kde.notificationmanager as NotificationManager

ColumnLayout {
    id: centre
    spacing: 12

    readonly property var source: notifs.item
    readonly property var modele: source ? source.historique : null
    readonly property int nombre: source ? source.nombre : 0
    // Change à chaque modification de la liste (ajout, suppression, groupe déplié…) : les cartes recalculent
    // alors leurs coins et leur lien « N de plus »
    property int revision: 0
    Connections {
        target: centre.modele
        function onRowsInserted() { centre.revision++ }
        function onRowsRemoved() { centre.revision++ }
        function onRowsMoved() { centre.revision++ }
        function onModelReset() { centre.revision++ }
        function onDataChanged() { centre.revision++ }
    }

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color carte: sombre ? "#2E3344" : "#F0EDE9"
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color texte3: sombre ? "#8C867A" : "#8A8277"
    readonly property color encre: sombre ? "#F0B392" : "#93401F"

    // Rendez-vous du jour, lus dans les cartes Agenda du bureau (une par Espace)
    property var rendezVous: []
    P5Support.DataSource {
        id: executeur
        engine: "executable"
        onNewData: (commande, donnees) => {
            disconnectSource(commande)
            var aujourdhui = Qt.formatDate(new Date(), "yyyy-MM-dd"), vus = {}, liste = []
            String(donnees["stdout"] || "").split("\n").forEach(function (ligne) {
                var i = ligne.indexOf("=")
                if (i < 0) return
                var evenements = []
                try { evenements = JSON.parse(ligne.slice(i + 1)) } catch (e) { return }
                evenements.forEach(function (e) {
                    var cle = e.titre + "|" + e.date + "|" + (e.debut || "")
                    if (e.date !== aujourdhui || vus[cle]) return
                    vus[cle] = true
                    liste.push(e)
                })
            })
            liste.sort(function (a, b) { return (a.debut || "").localeCompare(b.debut || "") })
            centre.rendezVous = liste
        }
    }
    Component.onCompleted: executeur.connectSource("grep -h '^evenements=' \"$HOME/.config/plasma-org.kde.plasma.desktop-appletsrc\"")

    // ——— En-tête ———
    RowLayout {
        Layout.fillWidth: true
        Layout.leftMargin: 6
        Layout.topMargin: 2
        spacing: 10
        ColumnLayout {
            spacing: 1
            Text {
                text: { var d = new Date().toLocaleDateString(Qt.locale(), "dddd d MMMM"); return d.charAt(0).toUpperCase() + d.slice(1) }
                font.pixelSize: 17
                font.weight: Font.Medium
                color: Kirigami.Theme.textColor
            }
            Text {
                text: centre.source && centre.source.nonLues > 0
                      ? (centre.source.nonLues === 1 ? "1 nouvelle notification" : centre.source.nonLues + " nouvelles notifications")
                      : (centre.nombre > 0 ? (centre.nombre === 1 ? "1 notification" : centre.nombre + " notifications") : "Vous êtes à jour")
                font.pixelSize: 12
                color: centre.texte2
            }
        }
        Item { Layout.fillWidth: true }
        // Ne pas déranger
        MouseArea {
            id: boutonNpd
            readonly property bool actif: centre.source ? centre.source.nePasDeranger : false
            Layout.preferredWidth: 36
            Layout.preferredHeight: 36
            hoverEnabled: true
            cursorShape: Qt.PointingHandCursor
            onClicked: npd.basculer()
            Rectangle {
                anchors.fill: parent
                radius: 18
                color: boutonNpd.actif ? "#B5532F"
                     : Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, boutonNpd.containsMouse ? 0.1 : 0.05)
            }
            Kirigami.Icon {
                anchors.centerIn: parent
                width: 18
                height: 18
                isMask: true
                color: boutonNpd.actif ? "#FFFFFF" : Kirigami.Theme.textColor
                source: Qt.resolvedUrl("../icons/ne-pas-deranger.svg")
            }
        }
    }
    NePasDeranger { id: npd }

    // Bandeau quand Ne pas déranger est actif
    Rectangle {
        visible: centre.source ? centre.source.nePasDeranger : false
        Layout.fillWidth: true
        Layout.preferredHeight: texteNpd.implicitHeight + 16
        radius: 12
        color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, centre.sombre ? 0.2 : 0.1)
        Text {
            id: texteNpd
            anchors.fill: parent
            anchors.leftMargin: 12
            anchors.rightMargin: 12
            verticalAlignment: Text.AlignVCenter
            wrapMode: Text.WordWrap
            text: "Ne pas déranger : les notifications arrivent ici en silence. Les alertes importantes s'affichent quand même."
            font.pixelSize: 12
            color: centre.encre
        }
    }

    // ——— Aujourd'hui ———
    Rectangle {
        visible: centre.rendezVous.length > 0
        Layout.fillWidth: true
        Layout.preferredHeight: listeRdv.implicitHeight + 24
        radius: 16
        color: centre.carte
        ColumnLayout {
            id: listeRdv
            anchors.fill: parent
            anchors.margins: 12
            spacing: 10
            Text { text: "Aujourd'hui"; font.pixelSize: 12; color: centre.texte2 }
            Repeater {
                model: centre.rendezVous.slice(0, 3)
                delegate: RowLayout {
                    Layout.fillWidth: true
                    spacing: 12
                    Rectangle { Layout.preferredWidth: 3; Layout.fillHeight: true; radius: 2; color: modelData.couleur || "#B5532F" }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 1
                        Text { Layout.fillWidth: true; text: modelData.titre; elide: Text.ElideRight; font.pixelSize: 13; font.weight: Font.Medium; color: Kirigami.Theme.textColor }
                        Text {
                            text: modelData.debut ? modelData.debut + (modelData.fin ? " – " + modelData.fin : "") : "Toute la journée"
                            font.pixelSize: 12
                            color: centre.texte2
                        }
                    }
                }
            }
            Text {
                visible: centre.rendezVous.length > 3
                text: "+ " + (centre.rendezVous.length - 3) + " autres aujourd'hui"
                font.pixelSize: 12
                color: centre.texte3
            }
        }
    }

    // ——— Notifications ———
    RowLayout {
        Layout.fillWidth: true
        Layout.leftMargin: 6
        Layout.rightMargin: 6
        Text {
            Layout.fillWidth: true
            text: "Notifications"
            font.pixelSize: 13
            font.weight: Font.DemiBold
            color: Kirigami.Theme.textColor
        }
        Text {
            visible: centre.nombre > 0
            text: "Tout effacer"
            font.pixelSize: 12
            color: centre.texte2
            MouseArea { anchors.fill: parent; anchors.margins: -6; cursorShape: Qt.PointingHandCursor; onClicked: centre.source.toutEffacer() }
        }
    }

    // Rien à afficher
    ColumnLayout {
        visible: centre.nombre === 0
        Layout.fillWidth: true
        Layout.topMargin: 14
        Layout.bottomMargin: 18
        spacing: 4
        Text { Layout.alignment: Qt.AlignHCenter; text: "Aucune notification"; font.pixelSize: 14; font.weight: Font.Medium; color: Kirigami.Theme.textColor }
        Text { Layout.alignment: Qt.AlignHCenter; text: "Les messages de vos applications arriveront ici"; font.pixelSize: 12; color: centre.texte3 }
    }

    Flickable {
        visible: centre.nombre > 0
        Layout.fillWidth: true
        Layout.preferredHeight: Math.min(liste.implicitHeight, 460)
        contentHeight: liste.implicitHeight
        clip: true
        boundsBehavior: Flickable.StopAtBounds

        Column {
            id: liste
            width: parent.width

            Repeater {
                model: centre.modele
                delegate: Notification {
                    width: liste.width
                }
            }
        }
    }

    // Une notification (ou l'en-tête d'un groupe) dans la liste à plat du modèle groupé
    component Notification: Item {
        id: element
        required property int index
        required property var model
        readonly property bool enTete: model.isGroup
        readonly property bool dansGroupe: model.isInGroup
        // Début d'une carte : en-tête de groupe ou notification seule ; fin : notification seule ou dernière du groupe
        readonly property bool debutCarte: !dansGroupe
        readonly property bool finCarte: {
            centre.revision   // réévaluer quand la liste change
            if (enTete) return false
            if (!dansGroupe) return true
            var suivante = centre.modele.index(index + 1, 0)
            return index + 1 >= centre.modele.rowCount() || !centre.modele.data(suivante, NotificationManager.Notifications.IsInGroupRole)
        }
        // Pour la dernière d'un groupe replié : combien d'autres sont cachées
        // (groupIndex rend un index d'un modèle intermédiaire : on repasse par son numéro de ligne)
        function ligneDuGroupe() { return centre.modele.groupIndex(centre.modele.index(index, 0)).row }
        function donneeDuGroupe(role) { return centre.modele.data(centre.modele.index(ligneDuGroupe(), 0), role) }
        readonly property int cachees: {
            centre.revision
            if (!dansGroupe || !finCarte) return 0
            return (donneeDuGroupe(NotificationManager.Notifications.GroupChildrenCountRole) || 0)
                 - (donneeDuGroupe(NotificationManager.Notifications.ExpandedGroupChildrenCountRole) || 0)
        }
        readonly property bool groupeOuvert: {
            centre.revision
            if (!dansGroupe || !finCarte) return false
            return !!donneeDuGroupe(NotificationManager.Notifications.IsGroupExpandedRole)
        }
        readonly property var actions: model.actionNames || []
        readonly property var libelles: model.actionLabels || []

        height: carte.height + (debutCarte && index > 0 ? 8 : 0)

        Rectangle {
            id: carte
            anchors.bottom: parent.bottom
            width: parent.width
            height: contenu.implicitHeight + (element.debutCarte ? 10 : 2) + (element.finCarte ? 10 : 4)
            radius: element.debutCarte || element.finCarte ? 16 : 0
            color: centre.carte
            // Coins carrés là où la carte continue (haut ou bas)
            Rectangle { visible: !element.debutCarte && element.finCarte; width: parent.width; height: 16; color: parent.color }
            Rectangle { visible: element.debutCarte && !element.finCarte; anchors.bottom: parent.bottom; width: parent.width; height: 16; color: parent.color }

            MouseArea {
                anchors.fill: parent
                enabled: !element.enTete
                cursorShape: Qt.PointingHandCursor
                onClicked: centre.source.ouvrir(element.index)
            }

            ColumnLayout {
                id: contenu
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.leftMargin: 12
                anchors.rightMargin: 12
                anchors.topMargin: element.debutCarte ? 10 : 2
                spacing: 4

                // En-tête : tuile de l'application, nom (et nombre dans un groupe), heure, ×
                RowLayout {
                    visible: !element.dansGroupe
                    Layout.fillWidth: true
                    spacing: 8
                    Rectangle {
                        Layout.preferredWidth: 20
                        Layout.preferredHeight: 20
                        radius: 6
                        color: centre.sombre ? "#3A4057" : "#FBF9F6"
                        Kirigami.Icon {
                            anchors.centerIn: parent
                            width: 14
                            height: 14
                            source: element.model.applicationIconName || element.model.iconName || "preferences-desktop-notification"
                        }
                    }
                    Text {
                        Layout.fillWidth: true
                        text: (element.model.applicationName || "Sama")
                              + (element.enTete ? " · " + element.model.groupChildrenCount + " notifications" : "")
                        elide: Text.ElideRight
                        font.pixelSize: 12
                        font.weight: Font.DemiBold
                        color: Kirigami.Theme.textColor
                    }
                    Text { text: centre.source ? centre.source.quand(element.model.updated || element.model.created) : ""; font.pixelSize: 11; color: centre.texte3 }
                    Text {
                        text: "×"
                        font.pixelSize: 15
                        color: centre.texte3
                        MouseArea { anchors.fill: parent; anchors.margins: -6; cursorShape: Qt.PointingHandCursor; onClicked: centre.source.fermer(element.index) }
                    }
                }

                // Contenu (pas sur l'en-tête d'un groupe)
                RowLayout {
                    visible: !element.enTete
                    Layout.fillWidth: true
                    Layout.topMargin: element.dansGroupe ? 6 : 0
                    spacing: 8
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 1
                        Text {
                            Layout.fillWidth: true
                            visible: text.length > 0
                            text: element.model.summary || ""
                            elide: Text.ElideRight
                            font.pixelSize: 13
                            font.weight: Font.Medium
                            color: Kirigami.Theme.textColor
                        }
                        Text {
                            Layout.fillWidth: true
                            visible: text.length > 0
                            text: element.model.body || ""
                            textFormat: Text.StyledText
                            wrapMode: Text.WordWrap
                            maximumLineCount: 2
                            elide: Text.ElideRight
                            font.pixelSize: 12
                            color: centre.texte2
                        }
                    }
                    // Dans un groupe : heure et × sur chaque notification
                    ColumnLayout {
                        visible: element.dansGroupe
                        Layout.alignment: Qt.AlignTop
                        spacing: 2
                        Text {
                            Layout.alignment: Qt.AlignRight
                            text: "×"
                            font.pixelSize: 14
                            color: centre.texte3
                            MouseArea { anchors.fill: parent; anchors.margins: -6; cursorShape: Qt.PointingHandCursor; onClicked: centre.source.fermer(element.index) }
                        }
                    }
                }

                // Actions
                Flow {
                    visible: !element.enTete && element.actions.length > 0
                    Layout.fillWidth: true
                    Layout.topMargin: 4
                    spacing: 6
                    Repeater {
                        model: element.actions.length
                        delegate: BoutonNotification {
                            petit: true
                            principal: index === 0
                            texte: element.libelles[index] || element.actions[index]
                            onClicked: centre.source.action(element.index, element.actions[index])
                        }
                    }
                }

                // « 3 de plus » / « Réduire » à la fin d'un groupe
                Text {
                    visible: element.cachees > 0 || element.groupeOuvert
                    Layout.topMargin: 4
                    text: element.groupeOuvert ? "Réduire" : (element.cachees === 1 ? "1 de plus" : element.cachees + " de plus")
                    font.pixelSize: 12
                    font.weight: Font.Medium
                    color: centre.encre
                    MouseArea {
                        anchors.fill: parent
                        anchors.margins: -6
                        cursorShape: Qt.PointingHandCursor
                        onClicked: centre.source.deplier(element.ligneDuGroupe(), !element.groupeOuvert)
                    }
                }
            }
        }
    }
}
