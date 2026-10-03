/*
    Fenêtre « Éteindre, redémarrer, se déconnecter » de Sama OS.
    Voile sur le bureau, salutation, grands boutons ronds comme sur l'écran de connexion de la maquette.
    Quand une action précise est demandée (ex. « Redémarrer »), elle se lance seule au bout de 30 s, sauf annulation.

    Mêmes signaux que l'écran de déconnexion de Plasma (Breeze) —
    SPDX-FileCopyrightText: 2014 Aleix Pol Gonzalez <aleixpol@blue-systems.com>
    SPDX-License-Identifier: GPL-2.0-or-later
*/

import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2

import org.kde.coreaddons 1.0 as KCoreAddons
import org.kde.kirigami 2.20 as Kirigami
import org.kde.plasma.private.sessions

Item {
    id: root
    height: screenGeometry.height
    width: screenGeometry.width

    signal logoutRequested()
    signal haltRequested()
    signal haltUpdateRequested()
    signal suspendRequested(int spdMethod)
    signal rebootRequested()
    signal rebootRequested2(int opt)
    signal rebootUpdateRequested()
    signal cancelRequested()
    signal lockScreenRequested()
    signal cancelSoftwareUpdateRequested()

    property alias backgroundColor: voile.color

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color texte: sombre ? "#F1EBE1" : "#1F1C18"
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color laterite: "#B5532F"

    property real timeout: 30
    property real remainingTime: root.timeout

    readonly property bool showAllOptions: sdtype === ShutdownType.ShutdownTypeDefault

    property var currentAction: {
        switch (sdtype) {
        case ShutdownType.ShutdownTypeReboot:
            return () => softwareUpdatePending ? rebootUpdateRequested() : rebootRequested();
        case ShutdownType.ShutdownTypeHalt:
            return () => softwareUpdatePending ? haltUpdateRequested() : haltRequested();
        default:
            return () => logoutRequested();
        }
    }

    onRemainingTimeChanged: if (remainingTime <= 0) (currentAction)()

    Timer {
        id: compteARebours
        running: !root.showAllOptions
        repeat: true
        interval: 1000
        onTriggered: root.remainingTime--
    }

    KCoreAddons.KUser { id: utilisateur }

    // Autres sessions ouvertes : on prévient avant d'éteindre
    SessionsModel {
        id: sessions
        includeUnusedSessions: false
    }

    QQC2.Action {
        onTriggered: root.cancelRequested()
        shortcut: "Escape"
    }

    Rectangle {
        id: voile
        anchors.fill: parent
        color: root.sombre ? "#151A2B" : "#F3ECE2"
        opacity: 0
        Component.onCompleted: opacity = 0.94
        Behavior on opacity { NumberAnimation { duration: 300; easing.type: Easing.OutCubic } }
    }
    MouseArea {
        anchors.fill: parent
        onClicked: root.cancelRequested()
    }

    ColumnLayout {
        anchors.centerIn: parent
        spacing: 0

        // Avatar et salutation
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: 80
            Layout.preferredHeight: 80
            radius: 40
            color: root.laterite
            border.width: 4
            border.color: root.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.8)
            Text {
                anchors.centerIn: parent
                visible: avatar.status !== Image.Ready
                text: (utilisateur.fullName || utilisateur.loginName).substring(0, 2).toUpperCase()
                font.pixelSize: 28
                font.weight: Font.Medium
                color: "#FFFFFF"
            }
            Image {
                id: avatar
                anchors.fill: parent
                anchors.margins: 4
                source: utilisateur.faceIconUrl
                visible: status === Image.Ready
                fillMode: Image.PreserveAspectCrop
                sourceSize.width: 160
                sourceSize.height: 160
            }
        }
        Text {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 18
            text: {
                var prenom = (utilisateur.fullName || utilisateur.loginName).split(" ")[0]
                switch (sdtype) {
                case ShutdownType.ShutdownTypeReboot: return "Redémarrer l'ordinateur ?"
                case ShutdownType.ShutdownTypeHalt: return "Éteindre l'ordinateur ?"
                case ShutdownType.ShutdownTypeNone: return "Fermer la session ?"
                default: return "À bientôt, " + prenom
                }
            }
            font.pixelSize: 26
            font.weight: Font.Light
            color: root.texte
        }
        Text {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 6
            text: {
                if (compteARebours.running) {
                    var s = Math.max(0, Math.round(root.remainingTime))
                    switch (sdtype) {
                    case ShutdownType.ShutdownTypeReboot: return "Redémarrage dans " + s + " s"
                    case ShutdownType.ShutdownTypeHalt: return "Arrêt dans " + s + " s"
                    default: return "Fermeture de la session dans " + s + " s"
                    }
                }
                return sessions.count > 1 ? "D'autres personnes ont une session ouverte sur cet ordinateur."
                                          : "Pensez à enregistrer vos documents."
            }
            font.pixelSize: 14
            color: root.texte2
        }

        // Grands boutons ronds
        RowLayout {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 36
            spacing: 22

            component Bouton: MouseArea {
                id: bouton
                property string libelle
                property string icone
                property bool principal: false
                Layout.preferredWidth: 104
                Layout.preferredHeight: 104
                hoverEnabled: true
                activeFocusOnTab: true
                cursorShape: Qt.PointingHandCursor
                Keys.onReturnPressed: clicked(null)
                Keys.onEnterPressed: clicked(null)

                Rectangle {
                    id: rond
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.top: parent.top
                    width: 64
                    height: 64
                    radius: 32
                    color: bouton.principal ? root.laterite
                         : root.sombre ? Qt.rgba(1, 1, 1, bouton.containsMouse ? 0.14 : 0.08)
                         : Qt.rgba(252 / 255, 250 / 255, 247 / 255, bouton.containsMouse ? 1 : 0.85)
                    border.width: bouton.activeFocus ? 2 : (bouton.principal ? 0 : 1)
                    border.color: bouton.activeFocus ? root.laterite
                                : root.sombre ? Qt.rgba(1, 1, 1, 0.1) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                    scale: bouton.pressed ? 0.95 : 1
                    Behavior on scale { NumberAnimation { duration: 100 } }
                    Kirigami.Icon {
                        anchors.centerIn: parent
                        width: 24
                        height: 24
                        isMask: true
                        color: bouton.principal ? "#FFFFFF" : root.texte
                        source: Qt.resolvedUrl("icones/" + bouton.icone + ".svg")
                    }
                }
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.top: rond.bottom
                    anchors.topMargin: 10
                    text: bouton.libelle
                    font.pixelSize: 13
                    font.weight: Font.Medium
                    color: root.texte
                }
            }

            Bouton {
                libelle: "Verrouiller"
                icone: "verrouiller"
                visible: root.showAllOptions
                onClicked: root.lockScreenRequested()
            }
            Bouton {
                libelle: "Mettre en veille"
                icone: "veille"
                visible: spdMethods.SuspendState && root.showAllOptions
                onClicked: root.suspendRequested(2)
            }
            Bouton {
                libelle: "Hiberner"
                icone: "hiberner"
                visible: spdMethods.HibernateState && root.showAllOptions
                onClicked: root.suspendRequested(4)
            }
            Bouton {
                libelle: softwareUpdatePending ? "Mettre à jour et redémarrer" : "Redémarrer"
                icone: "redemarrer"
                principal: sdtype === ShutdownType.ShutdownTypeReboot
                focus: sdtype === ShutdownType.ShutdownTypeReboot
                visible: maysd && (sdtype === ShutdownType.ShutdownTypeReboot || root.showAllOptions)
                onClicked: softwareUpdatePending ? root.rebootUpdateRequested() : root.rebootRequested()
            }
            Bouton {
                libelle: softwareUpdatePending ? "Mettre à jour et éteindre" : "Éteindre"
                icone: "eteindre"
                principal: sdtype === ShutdownType.ShutdownTypeHalt || root.showAllOptions
                focus: sdtype === ShutdownType.ShutdownTypeHalt || root.showAllOptions
                visible: maysd && (sdtype === ShutdownType.ShutdownTypeHalt || root.showAllOptions)
                onClicked: softwareUpdatePending ? root.haltUpdateRequested() : root.haltRequested()
            }
            Bouton {
                libelle: "Se déconnecter"
                icone: "deconnecter"
                principal: sdtype === ShutdownType.ShutdownTypeNone
                focus: sdtype === ShutdownType.ShutdownTypeNone
                visible: canLogout && (sdtype === ShutdownType.ShutdownTypeNone || root.showAllOptions)
                onClicked: root.logoutRequested()
            }
        }

        // Annuler
        QQC2.AbstractButton {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 18
            contentItem: Text {
                text: "Annuler"
                font.pixelSize: 14
                font.weight: Font.Medium
                color: root.sombre ? "#F0B392" : "#93401F"
                leftPadding: 16
                rightPadding: 16
                topPadding: 8
                bottomPadding: 8
            }
            background: Rectangle {
                radius: 18
                color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, parent.hovered ? 0.12 : 0)
            }
            onClicked: root.cancelRequested()
        }
    }
}
