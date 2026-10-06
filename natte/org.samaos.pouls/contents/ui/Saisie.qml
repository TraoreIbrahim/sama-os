// Méthode de saisie (maquette bur-06) : ouvert d'un clic sur le code de langue du Pouls (FR, DYU…). Les dispositions
// de clavier installées (Réglages › Langue et région › Clavier), la courante en latérite ; un clic en change.
// Méta+Espace passe à la suivante. Les lettres propres à chaque langue sont rappelées (julakan : AltGr + R → ɛ…).

import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.plasma5support as P5Support
import org.kde.plasma.workspace.keyboardlayout as KeyboardLayouts

Item {
    id: saisie
    implicitHeight: colonne.implicitHeight + 8

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color encre: Kirigami.Theme.textColor
    readonly property color texte2: Kirigami.Theme.disabledTextColor
    readonly property color champ: Qt.rgba(encre.r, encre.g, encre.b, sombre ? 0.07 : 0.05)
    readonly property color laterite: "#B5532F"

    KeyboardLayouts.KeyboardLayout { id: disposition }
    P5Support.DataSource {
        id: executeur
        engine: "executable"
        onNewData: (source) => disconnectSource(source)
        function lancer(commande) { connectSource(commande) }
    }

    // Nom clair et lettres de chaque disposition (code court de Sama, sinon code de la disposition)
    readonly property var langues: ({
        "fr": ["Français", "AZERTY"],
        "dyu": ["Julakan (dioula)", "Latin étendu · ɛ ɔ ɲ ŋ avec AltGr + R, P, J, M"],
        "wo": ["Wolof", "Latin · à é ë ñ ŋ"],
        "sw": ["Kiswahili", "QWERTY"],
        "us": ["Anglais", "QWERTY"],
        "gb": ["Anglais (Royaume-Uni)", "QWERTY"],
        "be": ["Belge", "AZERTY"],
        "ara": ["Arabe", ""],
        "pt": ["Portugais", "QWERTY"]
    })
    function code(d) { return String(d.displayName || d.shortName || "").toLowerCase() }
    function nom(d) { var l = langues[code(d)]; return l ? l[0] : String(d.longName) }
    function detail(d) { var l = langues[code(d)]; return l ? l[1] : "" }

    component Touche: Rectangle {
        property alias text: libelle.text
        implicitWidth: libelle.implicitWidth + 16
        implicitHeight: 22
        radius: 6
        color: saisie.sombre ? Qt.rgba(1, 1, 1, 0.1) : "#FFFFFF"
        border.width: 0.5
        border.color: Qt.rgba(saisie.encre.r, saisie.encre.g, saisie.encre.b, 0.16)
        Text { id: libelle; anchors.centerIn: parent; font.pixelSize: 11; font.weight: Font.DemiBold; color: saisie.encre }
    }

    ColumnLayout {
        id: colonne
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.margins: 4
        spacing: 4

        RowLayout {
            Layout.leftMargin: 6
            Layout.bottomMargin: 6
            spacing: 8
            Kirigami.Icon {
                Layout.preferredWidth: 16
                Layout.preferredHeight: 16
                isMask: true
                color: saisie.encre
                source: Qt.resolvedUrl("../icons/clavier.svg")
            }
            Text { text: "Méthode de saisie"; font.pixelSize: 13; font.weight: Font.DemiBold; color: saisie.encre }
        }

        Repeater {
            model: disposition.layoutsList
            delegate: MouseArea {
                id: rangee
                readonly property bool active: index === disposition.layout
                Layout.fillWidth: true
                Layout.preferredHeight: 52
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onClicked: { disposition.layout = index; racine.expanded = false }
                Rectangle {
                    anchors.fill: parent
                    radius: 12
                    color: parent.active ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.1) : parent.containsMouse ? saisie.champ : "transparent"
                }
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 10
                    anchors.rightMargin: 12
                    spacing: 12
                    Rectangle {
                        Layout.preferredWidth: 36
                        Layout.preferredHeight: 24
                        radius: 6
                        color: rangee.active ? saisie.laterite : saisie.champ
                        Text {
                            anchors.centerIn: parent
                            text: saisie.code(modelData).toUpperCase()
                            font.pixelSize: 11
                            font.weight: Font.DemiBold
                            font.letterSpacing: 0.4
                            color: rangee.active ? "#FFFFFF" : saisie.texte2
                        }
                    }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 0
                        Text {
                            Layout.fillWidth: true
                            text: saisie.nom(modelData)
                            elide: Text.ElideRight
                            font.pixelSize: 14
                            font.weight: rangee.active ? Font.DemiBold : Font.Medium
                            color: saisie.encre
                        }
                        Text {
                            Layout.fillWidth: true
                            visible: text !== ""
                            text: saisie.detail(modelData)
                            elide: Text.ElideRight
                            font.pixelSize: 12
                            color: saisie.texte2
                        }
                    }
                    Kirigami.Icon {
                        visible: rangee.active
                        Layout.preferredWidth: 16
                        Layout.preferredHeight: 16
                        isMask: true
                        color: saisie.laterite
                        source: Qt.resolvedUrl("../icons/coche.svg")
                    }
                }
            }
        }

        Rectangle { Layout.fillWidth: true; Layout.margins: 4; Layout.topMargin: 8; Layout.bottomMargin: 8; height: 0.5; color: Qt.rgba(saisie.encre.r, saisie.encre.g, saisie.encre.b, 0.08) }

        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 36
            Layout.leftMargin: 10
            Layout.rightMargin: 10
            spacing: 8
            Text { Layout.fillWidth: true; text: "Changer de méthode"; font.pixelSize: 13; color: saisie.texte2 }
            Touche { text: "Super" }
            Text { text: "+"; font.pixelSize: 11; color: saisie.texte2 }
            Touche { text: "Espace" }
        }

        MouseArea {
            Layout.fillWidth: true
            Layout.preferredHeight: 40
            Layout.topMargin: 4
            hoverEnabled: true
            cursorShape: Qt.PointingHandCursor
            onClicked: { executeur.lancer("sama-reglages langue/clavier"); racine.expanded = false }
            Rectangle {
                anchors.fill: parent
                radius: 12
                color: saisie.champ
                opacity: parent.containsMouse ? 1.6 : 1
            }
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 14
                anchors.rightMargin: 12
                spacing: 10
                Text { text: "+"; font.pixelSize: 16; color: saisie.encre }
                Text { Layout.fillWidth: true; text: "Ajouter une langue de saisie"; font.pixelSize: 13; color: saisie.encre }
                Text { text: "›"; font.pixelSize: 16; color: saisie.texte2 }
            }
        }
    }
}
