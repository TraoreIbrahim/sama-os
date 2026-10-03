// Écran de connexion de Sama OS (thème SDDM), d'après l'écran « Connexion » de la maquette :
// date et heure en haut, avatar et nom au centre, salutation en dioula selon l'heure,
// mot de passe en pilule avec flèche latérite, comptes de l'ordinateur en bas,
// veille / redémarrer / éteindre en bas à droite.
// Seuls QtQuick et QtQuick.Controls sont utilisés : pas de dépendance à Plasma.

import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: racine
    width: 1440
    height: 900
    color: "#F3ECE2"

    readonly property color texte: "#1F1C18"
    readonly property color texte2: "#665E54"
    readonly property color laterite: "#B5532F"
    readonly property var teintesAvatars: ["#B5532F", "#2F6B57", "#3D5A99", "#7A5C99", "#8A6A4A"]

    property int compteChoisi: userModel.lastIndex >= 0 ? userModel.lastIndex : 0
    property string nomChoisi: ""
    property string nomComplet: ""
    property string photoChoisie: ""
    property string message: ""

    property date maintenant: new Date()
    Timer {
        interval: 1000
        running: true
        repeat: true
        onTriggered: racine.maintenant = new Date()
    }

    // Salutation en dioula : I ni sogoma (matin), I ni tile (après-midi), I ni wula (soir)
    function salutation(prenom) {
        var h = maintenant.getHours()
        var mot = h < 12 ? "I ni sogoma" : (h < 18 ? "I ni tile" : "I ni wula")
        return prenom ? mot + ", " + prenom : mot
    }
    function initiales(nom) {
        var mots = String(nom).trim().split(/\s+/)
        return (mots.length > 1 ? mots[0].charAt(0) + mots[1].charAt(0) : String(nom).substring(0, 2)).toUpperCase()
    }
    function seConnecter() {
        racine.message = ""
        sddm.login(racine.nomChoisi, motDePasse.text, sessions.indexChoisi)
    }

    Connections {
        target: sddm
        function onLoginFailed() {
            racine.message = "Mot de passe incorrect"
            motDePasse.selectAll()
            motDePasse.forceActiveFocus()
            secousse.start()
        }
        function onLoginSucceeded() { racine.message = "" }
    }

    // Fond : Sama Aube, adouci par un voile ivoire
    Image {
        anchors.fill: parent
        source: config.background
        fillMode: Image.PreserveAspectCrop
        asynchronous: true
    }
    Rectangle {
        anchors.fill: parent
        color: "#F3ECE2"
        opacity: 0.45
    }

    // Date et heure
    Text {
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.top: parent.top
        anchors.topMargin: 36
        text: racine.maintenant.toLocaleDateString(Qt.locale("fr_FR"), "dddd d MMMM") + " · " + Qt.formatTime(racine.maintenant, "hh:mm")
        font.pixelSize: 13
        font.weight: Font.Medium
        color: racine.texte2
    }

    // Comptes de l'ordinateur (modèle invisible, pour connaître le compte choisi)
    Repeater {
        model: userModel
        delegate: Item {
            visible: false
            Component.onCompleted: if (index === racine.compteChoisi) choisir()
            Connections {
                target: racine
                function onCompteChoisiChanged() { if (index === racine.compteChoisi) choisir() }
            }
            function choisir() {
                racine.nomChoisi = model.name
                racine.nomComplet = model.realName || model.name
                racine.photoChoisie = model.icon ? String(model.icon) : ""
            }
        }
    }

    // Avatar, nom, salutation, mot de passe
    ColumnLayout {
        id: bloc
        anchors.horizontalCenter: parent.horizontalCenter
        y: parent.height * 0.25
        spacing: 0
        transform: Translate { id: decalage }
        SequentialAnimation {
            id: secousse
            loops: 2
            NumberAnimation { target: decalage; property: "x"; to: -10; duration: 50 }
            NumberAnimation { target: decalage; property: "x"; to: 10; duration: 100 }
            NumberAnimation { target: decalage; property: "x"; to: 0; duration: 50 }
        }

        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: 96
            Layout.preferredHeight: 96
            radius: 48
            color: racine.teintesAvatars[racine.compteChoisi % racine.teintesAvatars.length]
            border.width: 4
            border.color: Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.8)
            Text {
                anchors.centerIn: parent
                visible: photo.status !== Image.Ready
                text: racine.initiales(racine.nomComplet)
                font.pixelSize: 34
                font.weight: Font.Medium
                color: "#FFFFFF"
            }
            Image {
                id: photo
                anchors.fill: parent
                anchors.margins: 4
                source: racine.photoChoisie
                visible: status === Image.Ready
                fillMode: Image.PreserveAspectCrop
                sourceSize.width: 192
                sourceSize.height: 192
            }
        }
        Text {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 20
            text: racine.nomComplet
            font.pixelSize: 24
            font.weight: Font.Medium
            color: racine.texte
        }
        Text {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 4
            text: racine.salutation(racine.nomComplet.split(" ")[0])
            font.pixelSize: 13
            color: racine.texte2
        }

        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 26
            Layout.preferredWidth: 300
            Layout.preferredHeight: 44
            radius: 22
            color: Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.85)
            border.width: 1
            border.color: motDePasse.activeFocus ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.5) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)

            TextField {
                id: motDePasse
                anchors.left: parent.left
                anchors.right: valider.left
                anchors.verticalCenter: parent.verticalCenter
                anchors.leftMargin: 18
                anchors.rightMargin: 8
                background: null
                echoMode: TextInput.Password
                passwordCharacter: "•"
                placeholderText: "Mot de passe"
                placeholderTextColor: "#8E867B"
                color: racine.texte
                font.pixelSize: 14
                focus: true
                onAccepted: racine.seConnecter()
                Component.onCompleted: forceActiveFocus()
            }
            Rectangle {
                id: valider
                anchors.right: parent.right
                anchors.rightMargin: 6
                anchors.verticalCenter: parent.verticalCenter
                width: 32
                height: 32
                radius: 16
                color: racine.laterite
                Image {
                    anchors.centerIn: parent
                    source: "icones/fleche.svg"
                    sourceSize.width: 32
                    sourceSize.height: 32
                    width: 16
                    height: 16
                }
                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: racine.seConnecter()
                }
            }
        }
        Text {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 14
            Layout.preferredHeight: 18
            text: racine.message || (keyboard.capsLock ? "Verrouillage des majuscules activé" : "")
            font.pixelSize: 13
            font.weight: Font.Medium
            color: "#93401F"
        }
    }

    // Choix du compte, quand l'ordinateur en a plusieurs
    Row {
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 120
        spacing: 18
        visible: userModel.count > 1

        Repeater {
            model: userModel
            delegate: MouseArea {
                width: 72
                height: 72
                cursorShape: Qt.PointingHandCursor
                onClicked: {
                    racine.compteChoisi = index
                    motDePasse.text = ""
                    racine.message = ""
                    motDePasse.forceActiveFocus()
                }
                Rectangle {
                    id: pastille
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 44
                    height: 44
                    radius: 22
                    color: racine.teintesAvatars[index % racine.teintesAvatars.length]
                    border.width: index === racine.compteChoisi ? 2 : 0
                    border.color: "#FBF9F6"
                    Text {
                        anchors.centerIn: parent
                        text: racine.initiales(model.realName || model.name)
                        font.pixelSize: 15
                        font.weight: Font.Medium
                        color: "#FFFFFF"
                    }
                }
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.top: pastille.bottom
                    anchors.topMargin: 8
                    text: String(model.realName || model.name).split(" ")[0]
                    font.pixelSize: 12
                    font.weight: index === racine.compteChoisi ? Font.DemiBold : Font.Normal
                    color: index === racine.compteChoisi ? racine.texte : racine.texte2
                }
            }
        }
    }

    // Session (Plasma par défaut ; choix visible seulement s'il y en a plusieurs)
    Item {
        id: sessions
        property int indexChoisi: sessionModel.lastIndex >= 0 ? sessionModel.lastIndex : 0
    }

    // Veille, redémarrer, éteindre
    Row {
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.rightMargin: 28
        anchors.bottomMargin: 20
        spacing: 4

        component Action: MouseArea {
            id: action
            property string libelle
            property string icone
            width: 96
            height: 70
            hoverEnabled: true
            cursorShape: Qt.PointingHandCursor
            Rectangle {
                id: rond
                anchors.horizontalCenter: parent.horizontalCenter
                width: 44
                height: 44
                radius: 22
                color: Qt.rgba(252 / 255, 250 / 255, 247 / 255, action.containsMouse ? 0.95 : 0.75)
                border.width: 1
                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
                Image {
                    anchors.centerIn: parent
                    source: "icones/" + action.icone + ".svg"
                    sourceSize.width: 36
                    sourceSize.height: 36
                    width: 18
                    height: 18
                }
            }
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.top: rond.bottom
                anchors.topMargin: 6
                text: action.libelle
                font.pixelSize: 11
                color: racine.texte2
            }
        }

        Action { libelle: "Mettre en veille"; icone: "veille"; visible: sddm.canSuspend; onClicked: sddm.suspend() }
        Action { libelle: "Redémarrer"; icone: "redemarrer"; visible: sddm.canReboot; onClicked: sddm.reboot() }
        Action { libelle: "Éteindre"; icone: "eteindre"; visible: sddm.canPowerOff; onClicked: sddm.powerOff() }
    }

    // Logo Sama, en bas à gauche
    Row {
        anchors.left: parent.left
        anchors.bottom: parent.bottom
        anchors.leftMargin: 36
        anchors.bottomMargin: 40
        spacing: 8
        opacity: 0.85
        Image {
            anchors.verticalCenter: parent.verticalCenter
            source: "file:///usr/share/samaos/logo-sama.svg"
            sourceSize.width: 44
            sourceSize.height: 44
            width: 22
            height: 22
        }
        Text {
            anchors.verticalCenter: parent.verticalCenter
            text: "Sama OS"
            font.pixelSize: 13
            font.weight: Font.Medium
            color: racine.texte2
        }
    }
}
