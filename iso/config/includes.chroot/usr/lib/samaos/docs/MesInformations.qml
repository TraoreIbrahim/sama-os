// Mes informations (l'accueil de Sama Docs) : nom, adresse, téléphone, courriel, ville. Elles remplissent les lettres, le
// CV, les attestations ; gardées sur cet ordinateur (~/.config/samaos/moi.ini), jamais envoyées.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtCore
import "../reglages"
import "../sheet"

Item {
    id: infos
    property bool ouverte: false
    // Ce qui est gardé
    readonly property alias nom: reglages.nom
    readonly property alias adresse: reglages.adresse
    readonly property alias telephone: reglages.telephone
    readonly property alias courriel: reglages.courriel
    readonly property alias ville: reglages.ville
    Settings {
        id: reglages
        location: StandardPaths.writableLocation(StandardPaths.ConfigLocation) + "/samaos/moi.ini"
        category: "Moi"
        property string nom: ""
        property string adresse: ""
        property string telephone: ""
        property string courriel: ""
        property string ville: ""
    }
    // (au premier jour : le nom du compte)
    Commande { id: commande }
    Component.onCompleted: if (!reglages.nom) commande.lancer("getent passwd \"$(id -un)\"", function (sortie) {
        var p = sortie.trim().split(":")
        var n = (p[4] || "").split(",")[0].trim()
        if (n && !reglages.nom) reglages.nom = n
    })

    function ouvrir() {
        champNom.text = reglages.nom
        champAdresse.text = reglages.adresse
        champTelephone.text = reglages.telephone
        champCourriel.text = reglages.courriel
        champVille.text = reglages.ville
        ouverte = true
        champNom.forceActiveFocus()
    }
    function garder() {
        reglages.nom = champNom.text.trim()
        reglages.adresse = champAdresse.text.trim()
        reglages.telephone = champTelephone.text.trim()
        reglages.courriel = champCourriel.text.trim()
        reglages.ville = champVille.text.trim()
        reglages.sync()
        ouverte = false
    }

    parent: fenetre.contentItem
    anchors.fill: parent
    z: 100
    visible: ouverte
    Shortcut { sequence: "Escape"; enabled: infos.ouverte; onActivated: infos.ouverte = false }

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.45 : 0.22)
        MouseArea { anchors.fill: parent; onClicked: infos.ouverte = false }
    }
    component Champ: ColumnLayout {
        property alias text: saisie.text
        property alias etiquette: libelle.text
        property alias indice: saisie.placeholderText
        function forceActiveFocus() { saisie.forceActiveFocus() }
        Layout.fillWidth: true
        spacing: 4
        Text { id: libelle; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte2 }
        QQC2.TextField {
            id: saisie
            Layout.fillWidth: true
            Layout.preferredHeight: 36
            leftPadding: 10
            font.pixelSize: 14
            color: Couleurs.texte
            placeholderTextColor: Couleurs.texte3
            background: Rectangle { radius: 9; color: Couleurs.champ; border.width: saisie.activeFocus ? 1.5 : 1; border.color: saisie.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16) }
            onAccepted: infos.garder()
        }
    }
    Rectangle {
        anchors.centerIn: parent
        width: Math.min(parent.width - 60, 460)
        height: colonne.implicitHeight + 48
        radius: 18
        color: Couleurs.fond
        border.width: 1
        border.color: Couleurs.bord
        MouseArea { anchors.fill: parent }
        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 24
            spacing: 12
            Text { text: "Mes informations"; font.pixelSize: 18; font.weight: Font.DemiBold; color: Couleurs.texte }
            Text {
                Layout.fillWidth: true
                text: "Elles remplissent les lettres, le CV et les attestations. Elles restent sur cet ordinateur : rien n'est envoyé."
                font.pixelSize: 13
                lineHeight: 18
                lineHeightMode: Text.FixedHeight
                color: Couleurs.texte2
                wrapMode: Text.Wrap
            }
            Champ { id: champNom; etiquette: "Nom complet"; indice: "Aya Koné" }
            Champ { id: champAdresse; etiquette: "Adresse"; indice: "Cocody, rue des Jardins, 08 BP 123 Abidjan 08" }
            RowLayout {
                Layout.fillWidth: true
                spacing: 10
                Champ { id: champTelephone; etiquette: "Téléphone"; indice: "07 00 00 00 00" }
                Champ { id: champVille; etiquette: "Ville"; indice: "Abidjan" }
            }
            Champ { id: champCourriel; etiquette: "Courriel"; indice: "aya.kone@exemple.ci" }
            RowLayout {
                Layout.fillWidth: true
                Layout.topMargin: 8
                spacing: 8
                Item { Layout.fillWidth: true }
                Outil { text: "Annuler"; onClicked: infos.ouverte = false }
                QQC2.AbstractButton {
                    id: garder
                    implicitHeight: 32
                    implicitWidth: texteGarder.implicitWidth + 28
                    focusPolicy: Qt.NoFocus
                    hoverEnabled: true
                    onClicked: infos.garder()
                    background: Rectangle { radius: 9; color: garder.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent }
                    contentItem: Text { id: texteGarder; text: "Garder"; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#FFFFFF"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                }
            }
        }
    }
}
