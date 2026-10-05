/*
    Écran de verrouillage de Sama OS (remplace l'interface de Plasma, même mécanique d'authentification).
    Au repos : grande horloge, date, logo Sama, « Appuyez sur une touche pour déverrouiller ».
    Au premier geste : avatar, nom et champ du mot de passe en pilule, comme l'écran de connexion de la maquette.
    Suit le mode clair ou sombre (fond Sama Aube ou Sama Nuit, choisi par apparence.sh).

    Basé sur LockScreenUi.qml de Plasma — SPDX-FileCopyrightText: 2014 Aleix Pol Gonzalez <aleixpol@blue-systems.com>
    SPDX-License-Identifier: GPL-2.0-or-later
*/

import QtQml
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

import org.kde.plasma.workspace.components 2.0 as PW
import org.kde.plasma.private.keyboardindicator as KeyboardIndicator
import org.kde.kirigami 2.20 as Kirigami
import org.kde.kscreenlocker 1.0 as ScreenLocker
import org.kde.plasma.private.sessions 2.0

Item {
    id: lockScreenUi

    // Clair ou sombre, d'après le jeu de couleurs de Sama
    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color texte: sombre ? "#F1EBE1" : "#1F1C18"
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color laterite: "#B5532F"

    function handleMessage(msg) {
        if (!root.notification) {
            root.notification += msg;
        } else if (root.notification.includes(msg)) {
            root.notificationRepeated();
        } else {
            root.notification += "\n" + msg
        }
    }

    Connections {
        target: authenticator
        function onFailed(kind) {
            if (kind != 0) {
                return;
            }
            lockScreenUi.handleMessage("Mot de passe incorrect");
            graceLockTimer.restart();
            notificationRemoveTimer.restart();
            secousse.start();
        }
        function onSucceeded() {
            Qt.quit();
        }
        function onInfoMessageChanged() { lockScreenUi.handleMessage(authenticator.infoMessage); }
        function onErrorMessageChanged() { lockScreenUi.handleMessage(authenticator.errorMessage); }
        function onPromptChanged(msg) { lockScreenUi.handleMessage(authenticator.prompt); }
        function onPromptForSecretChanged(msg) { champ.forceActiveFocus(); }
    }

    Connections {
        target: root
        function onClearPassword() { champ.text = ""; }
    }

    SessionManagement { id: sessionManagement }
    Connections {
        target: sessionManagement
        function onAboutToSuspend() { root.clearPassword(); }
    }

    KeyboardIndicator.KeyState {
        id: capsLockState
        key: Qt.Key_CapsLock
    }

    property date maintenant: new Date()
    Timer {
        interval: 1000
        running: true
        repeat: true
        onTriggered: lockScreenUi.maintenant = new Date()
    }

    MouseArea {
        id: lockScreenRoot

        property bool uiVisible: false
        property bool seenPositionChange: false
        readonly property bool blockUI: containsMouse && champ.text.length > 0

        anchors.fill: parent
        hoverEnabled: true
        cursorShape: uiVisible ? Qt.ArrowCursor : Qt.BlankCursor
        onPressed: uiVisible = true
        onPositionChanged: {
            uiVisible = seenPositionChange;
            seenPositionChange = true;
        }
        onUiVisibleChanged: {
            if (blockUI) {
                fadeoutTimer.running = false;
            } else if (uiVisible) {
                fadeoutTimer.restart();
            }
            if (uiVisible) champ.forceActiveFocus();
            authenticator.startAuthenticating();
        }
        onBlockUIChanged: {
            if (blockUI) {
                fadeoutTimer.running = false;
                uiVisible = true;
            } else {
                fadeoutTimer.restart();
            }
        }
        onExited: uiVisible = false
        Keys.onEscapePressed: {
            if (uiVisible) {
                uiVisible = false;
                root.clearPassword();
            }
        }
        Keys.onPressed: event => {
            uiVisible = true;
            event.accepted = false;
        }

        Timer {
            id: fadeoutTimer
            interval: 10000
            onTriggered: if (!lockScreenRoot.blockUI) lockScreenRoot.uiVisible = false
        }
        Timer {
            id: notificationRemoveTimer
            interval: 3000
            onTriggered: root.notification = ""
        }
        Timer {
            id: graceLockTimer
            interval: 3000
            onTriggered: {
                root.clearPassword();
                authenticator.startAuthenticating();
            }
        }

        // Voile sur le fond d'écran : léger au repos, plus dense quand on saisit le mot de passe
        Rectangle {
            anchors.fill: parent
            color: lockScreenUi.sombre ? "#151A2B" : "#F3ECE2"
            opacity: lockScreenRoot.uiVisible ? 0.72 : 0.18
            Behavior on opacity { NumberAnimation { duration: 400; easing.type: Easing.OutCubic } }
        }

        // Horloge et date
        Column {
            id: horloge
            anchors.horizontalCenter: parent.horizontalCenter
            y: lockScreenRoot.uiVisible ? parent.height * 0.06 : parent.height * 0.12
            spacing: lockScreenRoot.uiVisible ? 2 : 14
            Behavior on y { NumberAnimation { duration: 400; easing.type: Easing.OutCubic } }

            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                visible: !lockScreenRoot.uiVisible
                text: Qt.formatTime(lockScreenUi.maintenant, "hh:mm")
                font.pixelSize: Math.min(140, lockScreenRoot.height * 0.15)
                font.weight: Font.Light
                font.letterSpacing: -2
                color: lockScreenUi.texte
            }
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: {
                    var d = lockScreenUi.maintenant.toLocaleDateString(Qt.locale(), "dddd d MMMM")
                    return lockScreenRoot.uiVisible ? d + " · " + Qt.formatTime(lockScreenUi.maintenant, "hh:mm") : d
                }
                font.pixelSize: lockScreenRoot.uiVisible ? 13 : 22
                font.weight: lockScreenRoot.uiVisible ? Font.Medium : Font.Normal
                color: lockScreenUi.texte2
            }
        }

        // Déverrouillage : avatar, nom, mot de passe
        ColumnLayout {
            id: bloc
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.verticalCenter: parent.verticalCenter
            anchors.verticalCenterOffset: -parent.height * 0.04
            spacing: 0
            opacity: lockScreenRoot.uiVisible ? 1 : 0
            visible: opacity > 0
            enabled: !graceLockTimer.running
            Behavior on opacity { NumberAnimation { duration: 300 } }

            transform: Translate { id: decalage }
            SequentialAnimation {
                id: secousse
                loops: 2
                NumberAnimation { target: decalage; property: "x"; to: -10; duration: 50 }
                NumberAnimation { target: decalage; property: "x"; to: 10; duration: 100 }
                NumberAnimation { target: decalage; property: "x"; to: 0; duration: 50 }
            }

            // Avatar : photo du compte, sinon initiales sur fond latérite
            Rectangle {
                Layout.alignment: Qt.AlignHCenter
                Layout.preferredWidth: 96
                Layout.preferredHeight: 96
                radius: 48
                color: lockScreenUi.laterite
                border.width: 4
                border.color: lockScreenUi.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.8)
                Text {
                    anchors.centerIn: parent
                    visible: photo.status !== Image.Ready
                    text: kscreenlocker_userName.substring(0, 2).toUpperCase()
                    font.pixelSize: 34
                    font.weight: Font.Medium
                    color: "#FFFFFF"
                }
                Kirigami.ShadowedImage {
                    id: photo
                    radius: width / 2   // photo découpée en rond (Image seule laisse dépasser les coins)
                    anchors.fill: parent
                    anchors.margins: 4
                    visible: status === Image.Ready && kscreenlocker_userImage !== ""
                    source: kscreenlocker_userImage !== ""
                            ? "file://" + kscreenlocker_userImage.split("/").map(encodeURIComponent).join("/") : ""
                    fillMode: Image.PreserveAspectCrop
                    sourceSize.width: 192
                    sourceSize.height: 192
                }
            }
            Text {
                Layout.alignment: Qt.AlignHCenter
                Layout.topMargin: 20
                text: kscreenlocker_userName
                font.pixelSize: 24
                font.weight: Font.Medium
                color: lockScreenUi.texte
            }

            // Mot de passe en pilule, flèche latérite pour valider
            Rectangle {
                Layout.alignment: Qt.AlignHCenter
                Layout.topMargin: 26
                Layout.preferredWidth: 300
                Layout.preferredHeight: 44
                radius: 22
                color: lockScreenUi.sombre ? Qt.rgba(1, 1, 1, 0.08) : Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.85)
                border.width: 1
                border.color: lockScreenUi.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)

                TextField {
                    id: champ
                    anchors.left: parent.left
                    anchors.right: valider.left
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.leftMargin: 18
                    anchors.rightMargin: 8
                    background: null
                    echoMode: TextInput.Password
                    passwordCharacter: "•"
                    placeholderText: "Mot de passe"
                    placeholderTextColor: lockScreenUi.texte2
                    color: lockScreenUi.texte
                    font.pixelSize: 14
                    focus: true
                    enabled: !authenticator.graceLocked
                    onAccepted: if (lockScreenRoot.uiVisible) authenticator.respond(text)
                    Keys.onEscapePressed: lockScreenRoot.uiVisible = false
                }
                Rectangle {
                    id: valider
                    anchors.right: parent.right
                    anchors.rightMargin: 6
                    anchors.verticalCenter: parent.verticalCenter
                    width: 32
                    height: 32
                    radius: 16
                    color: lockScreenUi.laterite
                    opacity: champ.text.length > 0 ? 1 : 0.55
                    Text {
                        anchors.centerIn: parent
                        text: "→"
                        color: "#FFFFFF"
                        font.pixelSize: 16
                        font.weight: Font.DemiBold
                    }
                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        onClicked: authenticator.respond(champ.text)
                    }
                }
            }

            // Messages : verrouillage majuscules, mot de passe incorrect…
            Text {
                Layout.alignment: Qt.AlignHCenter
                Layout.topMargin: 14
                Layout.preferredHeight: 18
                text: {
                    var parts = [];
                    if (capsLockState.locked) parts.push("Verrouillage des majuscules activé");
                    if (root.notification) parts.push(root.notification);
                    return parts.join(" · ");
                }
                font.pixelSize: 13
                font.weight: Font.Medium
                color: lockScreenUi.sombre ? "#F0B392" : "#93401F"
            }
        }

        // Invitation au repos
        Column {
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 44
            spacing: 12
            opacity: lockScreenRoot.uiVisible ? 0 : 1
            Behavior on opacity { NumberAnimation { duration: 300 } }
            Kirigami.Icon {
                anchors.horizontalCenter: parent.horizontalCenter
                width: 18
                height: 18
                isMask: true
                color: lockScreenUi.texte2
                source: "file:///usr/libexec/samaos/verrouillage/cadenas.svg"
            }
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: "Appuyez sur une touche ou cliquez pour déverrouiller"
                font.pixelSize: 14
                color: lockScreenUi.texte2
            }
        }

        // Logo Sama, en bas à gauche
        Row {
            anchors.left: parent.left
            anchors.bottom: parent.bottom
            anchors.leftMargin: 40
            anchors.bottomMargin: 44
            spacing: 8
            opacity: 0.8
            Image {
                anchors.verticalCenter: parent.verticalCenter
                source: lockScreenUi.sombre ? "file:///usr/share/samaos/logo-cour.svg" : "file:///usr/share/samaos/logo-sama.svg"
                sourceSize.width: 44
                sourceSize.height: 44
                width: 22
                height: 22
            }
            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: "Sama"
                font.pixelSize: 13
                font.weight: Font.Medium
                color: lockScreenUi.texte2
            }
        }

        // Actions, en bas à droite : disposition du clavier, veille, changer d'utilisateur
        Row {
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.rightMargin: 40
            anchors.bottomMargin: 40
            spacing: 10
            opacity: lockScreenRoot.uiVisible ? 1 : 0
            visible: opacity > 0
            Behavior on opacity { NumberAnimation { duration: 300 } }

            component Action: Rectangle {
                id: action
                property string libelle
                signal declenche()
                height: 40
                width: etiquette.implicitWidth + 32
                radius: 20
                color: lockScreenUi.sombre ? Qt.rgba(1, 1, 1, survol.containsMouse ? 0.12 : 0.06)
                                           : Qt.rgba(252 / 255, 250 / 255, 247 / 255, survol.containsMouse ? 0.95 : 0.75)
                Text {
                    id: etiquette
                    anchors.centerIn: parent
                    text: action.libelle
                    font.pixelSize: 13
                    font.weight: Font.Medium
                    color: lockScreenUi.texte
                }
                MouseArea {
                    id: survol
                    anchors.fill: parent
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: action.declenche()
                }
            }

            PW.KeyboardLayoutSwitcher {
                id: clavier
                width: 0
                height: 0
                acceptedButtons: Qt.NoButton
            }
            Action {
                visible: clavier.hasMultipleKeyboardLayouts
                libelle: clavier.layoutNames.longName
                onDeclenche: clavier.keyboardLayout.switchToNextLayout()
            }
            Action {
                visible: root.suspendToRamSupported
                libelle: "Mettre en veille"
                onDeclenche: root.suspendToRam()
            }
            Action {
                visible: sessionManagement.canSwitchUser
                libelle: "Changer d'utilisateur"
                onDeclenche: sessionManagement.switchUser()
            }
        }

        Component.onCompleted: apparition.start()
        NumberAnimation {
            id: apparition
            target: lockScreenRoot
            property: "opacity"
            from: 0
            to: 1
            duration: 600
        }
    }
}
