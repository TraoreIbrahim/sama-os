// Mes informations (l'accueil de Sama Docs) : nom, adresse, téléphone, courriel, ville, et la signature (dessinée à la
// souris ou importée). Elles remplissent les lettres, le CV, les attestations, le bloc Signature ; gardées sur cet
// ordinateur (~/.config/samaos/moi.ini, la signature dans ~/.local/share/samaos/moi, lisible par vous seul), jamais
// envoyées.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtCore
import QtQuick.Dialogs
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
    readonly property alias signature: reglages.signature
    Settings {
        id: reglages
        location: StandardPaths.writableLocation(StandardPaths.ConfigLocation) + "/samaos/moi.ini"
        category: "Moi"
        property string nom: ""
        property string adresse: ""
        property string telephone: ""
        property string courriel: ""
        property string ville: ""
        property string signature: ""
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
        dessin = false
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

    // ——— La signature : un fichier à vous seul ———
    readonly property string dossierMoi: fenetre.chemin(StandardPaths.writableLocation(StandardPaths.GenericDataLocation)) + "/samaos/moi"
    property int version: 0             // (l'aperçu se recharge)
    property bool dessin: false
    property var traits: []             // [[{x, y}…]…] : les traits dessinés
    onTraitsChanged: toile.requestPaint()
    function garderDessin() {
        if (!traits.length) { dessin = false; return }
        commande.lancer("mkdir -p " + commande.q(dossierMoi) + " && chmod 700 " + commande.q(dossierMoi), function () {
            var c = infos.dossierMoi + "/signature.png"
            if (toile.save(c)) {
                commande.lancer("chmod 600 " + commande.q(c), function () {
                    reglages.signature = c
                    reglages.sync()
                    infos.version++
                    infos.dessin = false
                })
            }
        })
    }
    function importer(chemin) {
        var ext = (chemin.split(".").pop() || "png").toLowerCase()
        var c = dossierMoi + "/signature." + ext
        commande.lancer("mkdir -p " + commande.q(dossierMoi) + " && chmod 700 " + commande.q(dossierMoi) + " && rm -f " + commande.q(dossierMoi) + "/signature.* && cp "
                        + commande.q(chemin) + " " + commande.q(c) + " && chmod 600 " + commande.q(c) + " && echo oui", function (sortie) {
            if (sortie.trim() !== "oui") return
            reglages.signature = c
            reglages.sync()
            infos.version++
        })
    }
    function retirerSignature() {
        if (reglages.signature) commande.lancer("rm -f " + commande.q(reglages.signature), function () {})
        reglages.signature = ""
        reglages.sync()
    }
    FileDialog {
        id: dialogueSignature
        title: "Une image de votre signature"
        fileMode: FileDialog.OpenFile
        nameFilters: ["Images (*.png *.jpg *.jpeg *.webp)"]
        currentFolder: StandardPaths.writableLocation(StandardPaths.PicturesLocation)
        onAccepted: infos.importer(fenetre.chemin(selectedFile))
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
            // La signature
            Text { Layout.topMargin: 2; text: "Signature"; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte2 }
            RowLayout {
                visible: !infos.dessin
                Layout.fillWidth: true
                spacing: 10
                Rectangle {
                    Layout.preferredWidth: 150
                    Layout.preferredHeight: 56
                    radius: 9
                    color: "#FFFFFF"
                    border.width: 1
                    border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                    Image {
                        anchors.fill: parent
                        anchors.margins: 6
                        visible: reglages.signature !== ""
                        source: reglages.signature ? "file://" + reglages.signature + "?" + infos.version : ""
                        cache: false
                        fillMode: Image.PreserveAspectFit
                    }
                    Text { anchors.centerIn: parent; visible: reglages.signature === ""; text: "Pas encore"; font.pixelSize: 12; color: Couleurs.texte3 }
                }
                ColumnLayout {
                    spacing: 2
                    RowLayout {
                        spacing: 4
                        Outil { text: reglages.signature ? "Redessiner" : "Dessiner"; onClicked: { infos.traits = []; toile.requestPaint(); infos.dessin = true } }
                        Outil { text: "Importer…"; onClicked: dialogueSignature.open() }
                        Outil { visible: reglages.signature !== ""; text: "Retirer"; encre: "#A3322A"; onClicked: infos.retirerSignature() }
                    }
                }
            }
            // (dessiner : à la souris ou au doigt, dans le cadre)
            ColumnLayout {
                visible: infos.dessin
                Layout.fillWidth: true
                spacing: 6
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 120
                    radius: 10
                    color: "#FFFFFF"
                    border.width: 1.5
                    border.color: fenetre.accent
                    Rectangle { x: 18; width: parent.width - 36; y: parent.height - 30; height: 1; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.15) }
                    Text { x: 18; y: parent.height - 26; text: "Signez au-dessus du trait"; font.pixelSize: 11; color: Couleurs.texte3; visible: !infos.traits.length }
                    Canvas {
                        id: toile
                        anchors.fill: parent
                        onPaint: {
                            var g = getContext("2d")
                            g.clearRect(0, 0, width, height)
                            g.strokeStyle = "#1F2A44"
                            g.lineWidth = 2.4
                            g.lineCap = "round"
                            g.lineJoin = "round"
                            infos.traits.forEach(function (t) {
                                if (!t.length) return
                                g.beginPath()
                                g.moveTo(t[0].x, t[0].y)
                                for (var i = 1; i < t.length; ++i) g.lineTo(t[i].x, t[i].y)
                                if (t.length === 1) g.lineTo(t[0].x + 0.5, t[0].y + 0.5)
                                g.stroke()
                            })
                        }
                        MouseArea {
                            anchors.fill: parent
                            cursorShape: Qt.CrossCursor
                            onPressed: (m) => { var t = infos.traits.slice(); t.push([{ x: m.x, y: m.y }]); infos.traits = t; toile.requestPaint() }
                            onPositionChanged: (m) => { if (!pressed) return; infos.traits[infos.traits.length - 1].push({ x: m.x, y: m.y }); toile.requestPaint() }
                        }
                    }
                }
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 6
                    Text {
                        Layout.fillWidth: true
                        text: "Elle reste sur cet ordinateur et n'entre que dans les documents où vous la placez."
                        font.pixelSize: 11
                        color: Couleurs.texte3
                        wrapMode: Text.Wrap
                    }
                    Outil { text: "Recommencer"; onClicked: { infos.traits = []; toile.requestPaint() } }
                    Outil { text: "Garder ce dessin"; encre: fenetre.accentEncre; onClicked: infos.garderDessin() }
                }
            }
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
