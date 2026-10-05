/*
    Fenêtre « Éteindre, redémarrer, se déconnecter » de Sama OS (maquette ses-04).
    Voile sur le bureau, salutation, grands boutons ronds comme sur l'écran de connexion de la maquette.
    Quand une action précise est demandée (ex. « Redémarrer »), elle se lance seule au bout de 30 s, sauf annulation.
    Des mises à jour attendent le redémarrage (/usr/libexec/samaos/maj-redemarrage.py) : « 2 mises à jour seront
    installées au redémarrage », case « Installer maintenant » ; cochée, Éteindre installe puis éteint (ses-05).

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
import org.kde.plasma.plasma5support as P5Support

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
            return () => root.redemarrer();
        case ShutdownType.ShutdownTypeHalt:
            return () => root.eteindre();
        default:
            return () => logoutRequested();
        }
    }

    // ——— Mises à jour de Sama prévues au redémarrage ———
    property int majPrevues: 0
    property bool installerMaj: true
    readonly property bool majSama: majPrevues > 0
    P5Support.DataSource {
        id: executeur
        engine: "executable"
        property var rappels: ({})
        function lancer(commande, rappel) { var r = rappels; r[commande] = rappel; rappels = r; connectSource(commande) }
        onNewData: (source, donnees) => {
            var rappel = rappels[source]
            disconnectSource(source)
            if (rappel) rappel(String(donnees["stdout"] || ""))
        }
        Component.onCompleted: lancer("python3 /usr/libexec/samaos/maj-redemarrage.py etat", function (s) {
            try { root.majPrevues = JSON.parse(s).nombre || 0 } catch (e) {}
        })
    }
    // Installer : redémarrage sur l'installation (qui éteint à la fin si on éteignait) ; sinon, à une autre fois
    function avecMaj(choix, ensuite) {
        executeur.lancer("pkexec /usr/libexec/samaos/maj-redemarrage.py " + choix + " >/dev/null 2>&1; echo fini", ensuite)
    }
    function redemarrer() {
        if (majSama) avecMaj(installerMaj ? "redemarrer" : "plus-tard", function () { root.rebootRequested() })
        else if (softwareUpdatePending) rebootUpdateRequested()
        else rebootRequested()
    }
    function eteindre() {
        if (majSama) avecMaj(installerMaj ? "eteindre" : "plus-tard", function () { installerMaj ? root.rebootRequested() : root.haltRequested() })
        else if (softwareUpdatePending) haltUpdateRequested()
        else haltRequested()
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

    // Changer d'utilisateur : la session reste ouverte, l'écran de connexion s'affiche pour une autre personne
    SessionManagement { id: gestionSessions }

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
            Kirigami.ShadowedImage {
                id: avatar
                radius: width / 2   // photo découpée en rond (Image seule laisse dépasser les coins)
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
                    width: 104
                    horizontalAlignment: Text.AlignHCenter
                    wrapMode: Text.WordWrap
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
                libelle: softwareUpdatePending && !root.majSama ? "Mettre à jour et redémarrer" : "Redémarrer"
                icone: "redemarrer"
                principal: sdtype === ShutdownType.ShutdownTypeReboot
                focus: sdtype === ShutdownType.ShutdownTypeReboot
                visible: maysd && (sdtype === ShutdownType.ShutdownTypeReboot || root.showAllOptions)
                onClicked: root.redemarrer()
            }
            Bouton {
                libelle: softwareUpdatePending && !root.majSama ? "Mettre à jour et éteindre" : "Éteindre"
                icone: "eteindre"
                principal: sdtype === ShutdownType.ShutdownTypeHalt || root.showAllOptions
                focus: sdtype === ShutdownType.ShutdownTypeHalt || root.showAllOptions
                visible: maysd && (sdtype === ShutdownType.ShutdownTypeHalt || root.showAllOptions)
                onClicked: root.eteindre()
            }
            Bouton {
                libelle: "Changer d'utilisateur"
                icone: "changer-utilisateur"
                visible: gestionSessions.canSwitchUser && root.showAllOptions
                onClicked: {
                    root.cancelRequested()
                    gestionSessions.switchUser()
                }
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

        // Mises à jour prévues au redémarrage
        Rectangle {
            visible: root.majSama && maysd && sdtype !== ShutdownType.ShutdownTypeNone
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 40
            implicitWidth: ligneMaj.implicitWidth + 36
            implicitHeight: 48
            radius: 14
            color: root.sombre ? Qt.rgba(1, 1, 1, 0.06) : Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.7)
            border.width: 0.5
            border.color: root.sombre ? Qt.rgba(1, 1, 1, 0.1) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
            RowLayout {
                id: ligneMaj
                anchors.centerIn: parent
                spacing: 16
                Canvas {
                    Layout.preferredWidth: 18
                    Layout.preferredHeight: 18
                    onPaint: {
                        var c = getContext("2d"); c.reset(); c.scale(18 / 24, 18 / 24)
                        c.strokeStyle = root.sombre ? "#A3D6C1" : "#2F6B57"; c.lineWidth = 2; c.lineCap = "round"; c.lineJoin = "round"
                        c.path = "M12 4v11 M7 10l5 5l5-5 M5 20h14"; c.stroke()
                    }
                }
                Text {
                    text: (root.majPrevues > 1 ? root.majPrevues + " mises à jour seront installées" : "1 mise à jour sera installée")
                          + (sdtype === ShutdownType.ShutdownTypeHalt ? " avant l'extinction" : " au redémarrage")
                    font.pixelSize: 14
                    color: root.texte
                }
                Rectangle { Layout.preferredWidth: 1; Layout.preferredHeight: 20; color: root.sombre ? Qt.rgba(1, 1, 1, 0.16) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16) }
                MouseArea {
                    id: caseMaj
                    Layout.preferredWidth: rangeeCase.implicitWidth
                    Layout.preferredHeight: 24
                    cursorShape: Qt.PointingHandCursor
                    hoverEnabled: true
                    onClicked: root.installerMaj = !root.installerMaj
                    RowLayout {
                        id: rangeeCase
                        anchors.verticalCenter: parent.verticalCenter
                        spacing: 8
                        Rectangle {
                            Layout.preferredWidth: 18
                            Layout.preferredHeight: 18
                            radius: 5
                            color: root.installerMaj ? root.laterite : "transparent"
                            border.width: root.installerMaj ? 0 : 1.5
                            border.color: caseMaj.containsMouse ? root.laterite : (root.sombre ? Qt.rgba(1, 1, 1, 0.3) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.3))
                            Canvas {
                                anchors.fill: parent
                                visible: root.installerMaj
                                onPaint: {
                                    var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
                                    c.strokeStyle = "#FFFFFF"; c.lineWidth = 3; c.lineCap = "round"; c.lineJoin = "round"
                                    c.path = "M6 12.5l4 4l8-9"; c.stroke()
                                }
                            }
                        }
                        Text { text: "Installer maintenant"; font.pixelSize: 14; font.weight: Font.Medium; color: root.texte }
                    }
                }
            }
        }

        // Annuler
        QQC2.AbstractButton {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: root.majSama ? 24 : 18
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
