// « Ouvrir avec… » : fenêtre posée au centre, sur un voile (comme la confirmation de la corbeille). Les applications
// qui savent ouvrir ce type de fichier (fichiers.py applis), celle par défaut en premier ; « Toujours ouvrir les
// fichiers .pdf avec cette application » en fait l'application par défaut.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.kirigami as Kirigami
import "../reglages"

Item {
    id: choix
    parent: fenetre.contentItem
    anchors.fill: parent
    z: 60
    visible: opacity > 0
    opacity: ouvert ? 1 : 0
    Behavior on opacity { NumberAnimation { duration: 160; easing.type: Easing.OutCubic } }

    property bool ouvert: false
    property string chemin: ""
    property var reponse: null           // { type, extension, applis: [{ id, nom, icone, defaut }] }
    property int choisie: 0
    property bool toujours: false
    readonly property var applis: reponse ? reponse.applis : []
    readonly property string nom: chemin.split("/").pop()
    onOuvertChanged: fenetre.dialogue = ouvert

    Commande { id: commande }
    function montrer(p) {
        chemin = p
        reponse = null
        choisie = 0
        toujours = false
        ouvert = true
        commande.lancer(fenetre.moteur + "applis " + commande.q(p), function (s) {
            if (p !== choix.chemin) return
            try { choix.reponse = JSON.parse(s) } catch (e) { choix.reponse = { type: "", extension: "", applis: [] } }
        })
    }
    // Sugu (le magasin d'applications) ouvert sur les applications qui savent lire ce type de fichier
    function chercherDansSugu() {
        if (reponse && reponse.mime) commande.lancer("plasma-discover --mime " + commande.q(reponse.mime) + " >/dev/null 2>&1 &")
        ouvert = false
    }
    function valider() {
        if (!applis.length) return
        var parDefaut = toujours
        commande.lancer(fenetre.moteur + "ouvrir-avec " + commande.q(applis[choisie].id) + " " + commande.q(chemin) + (toujours ? " toujours" : ""),
                        function () { if (parDefaut) fenetre.associationsChangees() })
        ouvert = false
    }

    // Échap : annuler ; Entrée : ouvrir ; flèches : changer d'application
    Shortcut { sequence: "Escape"; enabled: choix.ouvert; onActivated: choix.ouvert = false }
    Shortcut { sequences: ["Return", "Enter"]; enabled: choix.ouvert; onActivated: choix.valider() }
    Shortcut { sequence: "Up"; enabled: choix.ouvert; onActivated: choix.choisie = Math.max(0, choix.choisie - 1) }
    Shortcut { sequence: "Down"; enabled: choix.ouvert; onActivated: choix.choisie = Math.min(choix.applis.length - 1, choix.choisie + 1) }

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.45 : 0.28)
        // Clic à côté de la carte : annuler
        MouseArea { anchors.fill: parent; onClicked: choix.ouvert = false }
    }

    Rectangle {
        anchors.centerIn: parent
        width: Math.min(parent.width - 60, 440)
        height: contenu.implicitHeight + 48
        radius: 20
        color: Couleurs.fond
        border.width: 1
        border.color: Couleurs.bord
        scale: choix.ouvert ? 1 : 0.96
        Behavior on scale { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
        MouseArea { anchors.fill: parent }      // (les clics sur la carte ne la ferment pas)

        ColumnLayout {
            id: contenu
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 24
            spacing: 0

            // Le fichier
            RowLayout {
                Layout.fillWidth: true
                spacing: 14
                IconeFichier {
                    Layout.preferredWidth: 44
                    Layout.preferredHeight: 44
                    nom: choix.nom
                    chemin: choix.chemin
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 3
                    Text {
                        Layout.fillWidth: true
                        text: "Ouvrir « " + choix.nom + " »"
                        elide: Text.ElideMiddle
                        font.pixelSize: 18
                        font.weight: Font.Medium
                        color: Couleurs.texte
                    }
                    Text {
                        Layout.fillWidth: true
                        text: (choix.reponse && choix.reponse.type ? choix.reponse.type + " · " : "") + "avec quelle application ?"
                        elide: Text.ElideRight
                        font.pixelSize: 13
                        color: Couleurs.texte2
                    }
                }
            }

            // Les applications
            ListView {
                id: liste
                visible: choix.applis.length > 0
                Layout.topMargin: 18
                Layout.fillWidth: true
                Layout.preferredHeight: Math.min(contentHeight, 5 * 54 - 2)
                clip: true
                spacing: 2
                boundsBehavior: Flickable.StopAtBounds
                model: choix.applis
                currentIndex: choix.choisie
                QQC2.ScrollBar.vertical: QQC2.ScrollBar { policy: liste.contentHeight > liste.height ? QQC2.ScrollBar.AsNeeded : QQC2.ScrollBar.AlwaysOff }
                onCurrentIndexChanged: positionViewAtIndex(currentIndex, ListView.Contain)
                delegate: MouseArea {
                    id: rangee
                    required property var modelData
                    required property int index
                    readonly property bool active: choix.choisie === index
                    width: liste.width
                    height: 52
                    hoverEnabled: true
                    onClicked: choix.choisie = index
                    onDoubleClicked: { choix.choisie = index; choix.valider() }
                    Rectangle {
                        anchors.fill: parent
                        radius: 12
                        color: rangee.active ? Couleurs.selection : (rangee.containsMouse ? Couleurs.carte : "transparent")
                        border.width: rangee.active ? 1.5 : 0
                        border.color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.45)
                    }
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 12
                        anchors.rightMargin: 14
                        spacing: 12
                        Kirigami.Icon {
                            Layout.preferredWidth: 32
                            Layout.preferredHeight: 32
                            source: rangee.modelData.icone.indexOf("/") === 0 ? "file://" + rangee.modelData.icone : (rangee.modelData.icone || "application-x-executable")
                        }
                        Text {
                            Layout.fillWidth: true
                            text: rangee.modelData.nom
                            elide: Text.ElideRight
                            font.pixelSize: 14
                            font.weight: rangee.active ? Font.DemiBold : Font.Normal
                            color: Couleurs.texte
                        }
                        Rectangle {
                            visible: rangee.modelData.defaut
                            implicitWidth: libelleDefaut.implicitWidth + 16
                            implicitHeight: 22
                            radius: 11
                            color: Couleurs.carte
                            Text { id: libelleDefaut; anchors.centerIn: parent; text: "Par défaut"; font.pixelSize: 11; color: Couleurs.texte2 }
                        }
                    }
                }
            }

            // En attendant la liste, ou aucune application
            Text {
                visible: choix.applis.length === 0
                Layout.topMargin: 18
                Layout.fillWidth: true
                Layout.minimumHeight: 52
                verticalAlignment: Text.AlignVCenter
                wrapMode: Text.WordWrap
                lineHeight: 1.2
                text: choix.reponse === null ? "Recherche des applications…"
                      : "Aucune application installée ne sait ouvrir ce type de fichier. Sugu peut vous en proposer."
                font.pixelSize: 14
                color: Couleurs.texte2
            }

            // « Toujours… »
            QQC2.AbstractButton {
                id: caseToujours
                visible: choix.applis.length > 0
                Layout.topMargin: 16
                Layout.fillWidth: true
                implicitHeight: 24
                hoverEnabled: true
                onClicked: choix.toujours = !choix.toujours
                contentItem: RowLayout {
                    spacing: 10
                    Rectangle {
                        Layout.preferredWidth: 18
                        Layout.preferredHeight: 18
                        radius: 5
                        color: choix.toujours ? Couleurs.laterite : Couleurs.champ
                        border.width: choix.toujours ? 0 : 1.5
                        border.color: caseToujours.hovered ? Couleurs.laterite : Couleurs.bord
                        Canvas {
                            anchors.fill: parent
                            visible: choix.toujours
                            onPaint: {
                                var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
                                c.strokeStyle = "#FFFFFF"; c.lineWidth = 3; c.lineCap = "round"; c.lineJoin = "round"
                                c.path = "M6 12.5l4 4l8-9"; c.stroke()
                            }
                        }
                    }
                    Text {
                        Layout.fillWidth: true
                        text: choix.reponse && choix.reponse.extension
                              ? "Toujours ouvrir les fichiers ." + choix.reponse.extension + " avec cette application"
                              : "Toujours ouvrir ce type de fichier avec cette application"
                        elide: Text.ElideRight
                        font.pixelSize: 13
                        color: Couleurs.texte
                    }
                }
            }

            RowLayout {
                Layout.topMargin: 22
                Layout.fillWidth: true
                spacing: 10
                // D'autres applications, dans Sugu
                QQC2.AbstractButton {
                    id: lienSugu
                    visible: choix.applis.length > 0 && choix.reponse !== null
                    implicitHeight: 38
                    implicitWidth: libelleSugu.implicitWidth
                    hoverEnabled: true
                    onClicked: choix.chercherDansSugu()
                    contentItem: Text {
                        id: libelleSugu
                        text: "Autres applications…"
                        verticalAlignment: Text.AlignVCenter
                        font.pixelSize: 13
                        font.underline: lienSugu.hovered
                        color: Couleurs.lateriteEncre
                    }
                    background: Item {}
                }
                Item { Layout.fillWidth: true }
                BoutonSama { text: choix.applis.length ? "Annuler" : "Fermer"; implicitHeight: 38; onClicked: choix.ouvert = false }
                BoutonSama {
                    visible: choix.applis.length > 0
                    principal: true
                    implicitHeight: 38
                    implicitWidth: Math.max(110, implicitContentWidth + 12)
                    text: "Ouvrir"
                    onClicked: choix.valider()
                }
                BoutonSama {
                    visible: choix.reponse !== null && choix.applis.length === 0
                    principal: true
                    implicitHeight: 38
                    text: "Chercher dans Sugu"
                    onClicked: choix.chercherDansSugu()
                }
            }
        }
    }
}
