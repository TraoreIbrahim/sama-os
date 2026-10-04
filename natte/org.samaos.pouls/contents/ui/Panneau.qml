// Centre de contrôle de Sama (moitié gauche du Pouls : langue, réseau, data, batterie), d'après l'écran
// « Centre de contrôle » de la maquette ; les notifications ont leur propre centre (CentreNotifications.qml) :
//   tuiles : Wi-Fi, Économie de data, Mode sombre, Mises à jour la nuit, Bluetooth, Ne pas déranger ;
//   curseurs de luminosité et de volume ; data du jour ; langue.
// Chaque source (réseau, Bluetooth, luminosité, son…) est chargée à part : si l'une manque, le reste fonctionne.
// Économie de data et mises à jour la nuit sont affichées comme « bientôt » tant que le service n'existe pas.

import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.plasma5support as P5Support

Item {
    id: panneau
    implicitHeight: colonne.implicitHeight + 4

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color champ: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, sombre ? 0.07 : 0.05)
    readonly property color texte2: Kirigami.Theme.disabledTextColor
    readonly property bool wifiActif: reseau.item ? reseau.item.wifiActive : false

    P5Support.DataSource {
        id: executeur
        engine: "executable"
        connectedSources: []
        onNewData: (source) => disconnectSource(source)
        function lancer(commande) { connectSource(commande) }
    }

    Loader { id: wifi; source: "Wifi.qml" }
    Loader { id: bluetooth; source: "Bluetooth.qml" }
    Loader { id: silence; source: "NePasDeranger.qml" }
    Loader { id: data; source: "Data.qml" }

    // Relire le nom du réseau à chaque ouverture
    Connections {
        target: racine
        function onExpandedChanged() { if (racine.expanded && wifi.item) wifi.item.relire() }
    }

    // Tuile de la maquette : 62 px, coins de 16, latérite quand elle est active
    component Tuile: MouseArea {
        id: tuile
        property string titre
        property string detail
        property string nomIcone
        property bool active: false
        property bool bientot: false

        Layout.fillWidth: true
        Layout.preferredWidth: 1
        Layout.preferredHeight: 62
        hoverEnabled: !bientot
        cursorShape: bientot ? Qt.ArrowCursor : Qt.PointingHandCursor

        Rectangle {
            anchors.fill: parent
            radius: 16
            color: tuile.active ? "#B5532F" : panneau.champ
            opacity: tuile.active && tuile.containsMouse ? 0.9 : 1
            Rectangle {
                anchors.fill: parent
                radius: parent.radius
                color: Kirigami.Theme.textColor
                opacity: !tuile.active && tuile.containsMouse ? 0.05 : 0
            }
        }
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 14
            anchors.rightMargin: 10
            spacing: 10
            opacity: tuile.bientot ? 0.55 : 1

            Kirigami.Icon {
                Layout.preferredWidth: 18
                Layout.preferredHeight: 18
                isMask: true
                color: tuile.active ? "#FFFFFF" : Kirigami.Theme.textColor
                source: Qt.resolvedUrl("../icons/" + tuile.nomIcone + ".svg")
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 1
                Text {
                    Layout.fillWidth: true
                    text: tuile.titre
                    elide: Text.ElideRight
                    wrapMode: Text.WordWrap
                    maximumLineCount: 2
                    font.pixelSize: 13
                    font.weight: Font.DemiBold
                    lineHeight: 0.92
                    color: tuile.active ? "#FFFFFF" : Kirigami.Theme.textColor
                }
                Text {
                    Layout.fillWidth: true
                    text: tuile.detail
                    elide: Text.ElideRight
                    font.pixelSize: 11
                    color: tuile.active ? Qt.rgba(1, 1, 1, 0.85) : panneau.texte2
                }
            }
        }
    }

// Ligne cliquable (langue, réglages)
    component Ligne: MouseArea {
        id: ligne
        property string titre
        property string nomIcone
        Layout.fillWidth: true
        Layout.preferredHeight: 40
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        Rectangle {
            anchors.fill: parent
            radius: 12
            color: panneau.champ
            Rectangle {
                anchors.fill: parent
                radius: parent.radius
                color: Kirigami.Theme.textColor
                opacity: ligne.containsMouse ? 0.05 : 0
            }
        }
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 14
            anchors.rightMargin: 12
            spacing: 10
            Kirigami.Icon {
                Layout.preferredWidth: 16
                Layout.preferredHeight: 16
                isMask: true
                color: panneau.texte2
                source: Qt.resolvedUrl("../icons/" + ligne.nomIcone + ".svg")
            }
            Text {
                Layout.fillWidth: true
                text: ligne.titre
                font.pixelSize: 13
                color: Kirigami.Theme.textColor
            }
            Kirigami.Icon {
                Layout.preferredWidth: 14
                Layout.preferredHeight: 14
                isMask: true
                color: panneau.texte2
                source: Qt.resolvedUrl("../icons/chevron.svg")
            }
        }
    }

    ColumnLayout {
        id: colonne
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.margins: 2
        spacing: 12

        // Tuiles
        GridLayout {
            Layout.fillWidth: true
            columns: 2
            columnSpacing: 8
            rowSpacing: 8

            Tuile {
                readonly property string type: wifi.item ? wifi.item.type : ""
                titre: type === "filaire" ? "Réseau" : "Wi-Fi"
                nomIcone: type === "filaire" ? "filaire" : (panneau.wifiActif ? "wifi" : "wifi-coupe")
                active: type !== "" || panneau.wifiActif
                detail: {
                    var nom = wifi.item ? wifi.item.nom : ""
                    if (type === "filaire") return nom ? "Filaire · " + nom : "Filaire"
                    if (nom) return nom
                    return panneau.wifiActif ? "Non connecté" : "Désactivé"
                }
                // Filaire : ouvre les réglages réseau ; Wi-Fi : active ou coupe
                onClicked: {
                    if (type === "filaire" || !reseau.item) executeur.lancer("sama-reglages reseau")
                    else reseau.item.activerWifi(!panneau.wifiActif)
                }
                onPressAndHold: executeur.lancer("sama-reglages reseau")
            }
            Tuile {
                titre: "Économie de data"
                detail: "Bientôt disponible"
                nomIcone: "feuille"
                bientot: true
            }
            Tuile {
                titre: "Mode sombre"
                detail: panneau.sombre ? "Activé" : "Désactivé"
                nomIcone: "contraste"
                active: panneau.sombre
                // Couleurs, icônes et fonds d'écran basculent ensemble
                onClicked: executeur.lancer("/usr/libexec/samaos/apparence.sh " + (panneau.sombre ? "clair" : "sombre"))
            }
            Tuile {
                titre: "Mises à jour la nuit"
                detail: "Bientôt disponible"
                nomIcone: "mises-a-jour"
                bientot: true
            }
            Tuile {
                readonly property bool dispo: bluetooth.item ? bluetooth.item.disponible : false
                titre: "Bluetooth"
                nomIcone: "bluetooth"
                active: bluetooth.item ? bluetooth.item.actif : false
                bientot: !dispo
                detail: !dispo ? "Aucun adaptateur" : !active ? "Désactivé"
                      : (bluetooth.item.appareil || "Activé")
                onClicked: if (dispo) bluetooth.item.basculer()
                onPressAndHold: executeur.lancer("sama-reglages bluetooth")
            }
            Tuile {
                titre: "Ne pas déranger"
                nomIcone: "ne-pas-deranger"
                active: silence.item ? silence.item.actif : false
                detail: active ? "Activé" : "Désactivé"
                onClicked: if (silence.item) silence.item.basculer()
            }
        }

        // Luminosité et volume
        ColumnLayout {
            Layout.fillWidth: true
            Layout.leftMargin: 6
            Layout.rightMargin: 6
            spacing: 10
            Loader {
                Layout.fillWidth: true
                source: "Luminosite.qml"
                visible: !!item && item.disponible
            }
            Loader {
                Layout.fillWidth: true
                source: "Volume.qml"
                visible: !!item && item.disponible
            }
        }

        // Data consommée
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: blocData.implicitHeight + 24
            radius: 16
            color: panneau.champ
            visible: !!data.item

            ColumnLayout {
                id: blocData
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                anchors.leftMargin: 14
                anchors.rightMargin: 14
                spacing: 8

                readonly property real mo: data.item ? data.item.megaOctets : 0
                readonly property real forfait: data.item ? data.item.forfait : 1024

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 6
                    Text {
                        Layout.fillWidth: true
                        text: "Data depuis l'allumage"
                        font.pixelSize: 12
                        color: panneau.texte2
                    }
                    Text {
                        text: blocData.mo >= 1024 ? (blocData.mo / 1024).toFixed(1).replace(".", ",") + " Go"
                                                  : Math.round(blocData.mo) + " Mo"
                        font.pixelSize: 15
                        font.weight: Font.DemiBold
                        color: Kirigami.Theme.textColor
                    }
                    Text {
                        text: "sur " + (blocData.forfait >= 1024 ? Math.round(blocData.forfait / 1024) + " Go" : blocData.forfait + " Mo")
                        font.pixelSize: 12
                        color: panneau.texte2
                    }
                }
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 6
                    radius: 3
                    color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.12)
                    Rectangle {
                        height: parent.height
                        radius: 3
                        width: parent.width * Math.min(1, blocData.mo / blocData.forfait)
                        color: blocData.mo / blocData.forfait > 0.9 ? "#B5532F" : "#2F6B57"
                    }
                }
            }
        }

        // Langue, puis tous les réglages
        RowLayout {
            Layout.fillWidth: true
            spacing: 8
            Ligne {
                titre: "Langue : " + ({ "FR": "Français", "EN": "English", "SW": "Kiswahili" }[racine.langue] || racine.langue)
                nomIcone: "globe"
                onClicked: executeur.lancer("sama-reglages langue")
            }
            Ligne {
                Layout.fillWidth: false
                Layout.preferredWidth: 132
                titre: "Réglages"
                nomIcone: "reglages"
                onClicked: executeur.lancer("sama-reglages")
            }
        }
    }
}
