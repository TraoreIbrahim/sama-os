/* Installateur de Sama — « Créez votre compte » (maquette dem-05) : nom complet (l'identifiant et le nom de
 * l'ordinateur s'en déduisent), mot de passe avec sa solidité, connexion automatique.
 * Les autres comptes s'ajoutent après l'installation, dans Réglages › Comptes.
 */
import io.calamares.core 1.0
import io.calamares.ui 1.0
import QtQuick
import QtQuick.Layouts
import "composants"

Rectangle {
    id: page
    color: "#FBF9F6"

    property bool identifiantModifie: false      // la personne a choisi son identifiant elle-même
    property bool ordinateurModifie: false       // … ou le nom de l'ordinateur
    property bool modifierIdentifiant: false

    // « Aïssata Koné » → « aissata » (identifiant, nom de l'ordinateur)
    function simplifier(s) {
        s = String(s || "").toLowerCase()
        if (typeof s.normalize === "function") s = s.normalize("NFD").replace(/[̀-ͯ]/g, "")
        return s.replace(/ɛ/g, "e").replace(/ɔ/g, "o").replace(/ɲ/g, "ny").replace(/ŋ/g, "ng").replace(/[^a-z0-9]+/g, "")
    }
    readonly property var mots: String(config.fullName || "").trim().split(/\s+/).filter(function (m) { return m.length > 0 })
    readonly property string initiales: mots.length === 0 ? ""
        : (mots[0].charAt(0) + (mots.length > 1 ? mots[mots.length - 1].charAt(0) : "")).toUpperCase()

    function nomComplet(texte) {
        config.setFullName(texte)
        var prenom = simplifier(texte.trim().split(/\s+/)[0])
        if (!identifiantModifie) {
            var id = prenom
            if (/^[0-9]/.test(id)) id = "u" + id
            config.setLoginName(id.substring(0, 31))
        }
        if (!ordinateurModifie)
            config.setHostName(prenom ? ("pc-" + prenom).substring(0, 63) : "")
    }

    // Solidité du mot de passe : 1 faible … 4 très solide (le système n'impose rien, c'est un conseil)
    function solidite(m) {
        if (!m) return 0
        var familles = [/[a-z]/, /[A-Z]/, /[0-9]/, /[^a-zA-Z0-9]/].filter(function (r) { return r.test(m) }).length
        var n = (m.length >= 8 ? 1 : 0) + (m.length >= 12 ? 1 : 0) + (familles >= 2 ? 1 : 0) + (familles >= 3 ? 1 : 0)
        if (m.length < 6) n = Math.min(n, 1)
        return Math.max(1, n)
    }
    readonly property int force: solidite(motDePasse.text)
    readonly property var niveaux: [
        { nom: "", couleur: "#8A8277" },
        { nom: "Faible", couleur: "#A3322A" },
        { nom: "Moyen", couleur: "#B7791F" },
        { nom: "Solide", couleur: "#2F6B57" },
        { nom: "Très solide", couleur: "#2F6B57" }
    ]

    ColumnLayout {
        anchors.fill: parent
        anchors.leftMargin: 40
        anchors.rightMargin: 40
        anchors.topMargin: 30
        spacing: 0

        Text { text: "Créez votre compte"; font.pixelSize: 26; font.weight: Font.Medium; color: "#1F1C18" }
        Text {
            Layout.topMargin: 6
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            text: "Ce compte vous servira à ouvrir une session et à administrer cet ordinateur."
            font.pixelSize: 14
            color: "#665E54"
        }

        RowLayout {
            Layout.topMargin: 24
            Layout.fillWidth: true
            spacing: 36

            // ——— Formulaire ———
            ColumnLayout {
                Layout.fillWidth: true
                Layout.alignment: Qt.AlignTop
                spacing: 18

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 18
                    // Initiales (la photo se choisit ensuite dans Réglages › Mon compte)
                    Rectangle {
                        Layout.preferredWidth: 72
                        Layout.preferredHeight: 72
                        Layout.alignment: Qt.AlignBottom
                        radius: 36
                        color: page.initiales ? "#B5532F" : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
                        Behavior on color { ColorAnimation { duration: 160 } }
                        Text {
                            anchors.centerIn: parent
                            visible: page.initiales !== ""
                            text: page.initiales
                            font.pixelSize: 24
                            font.weight: Font.Medium
                            color: "#FFFFFF"
                        }
                        Canvas {
                            anchors.centerIn: parent
                            visible: page.initiales === ""
                            width: 30
                            height: 30
                            onPaint: {
                                var c = getContext("2d"); c.reset(); c.scale(30 / 24, 30 / 24)
                                c.strokeStyle = "#8A8277"; c.lineWidth = 1.5; c.lineCap = "round"; c.lineJoin = "round"
                                c.path = "M12 12a4 4 0 1 0 0-8a4 4 0 1 0 0 8z M4.5 20c0-4 3.4-6.5 7.5-6.5s7.5 2.5 7.5 6.5"
                                c.stroke()
                            }
                        }
                    }
                    Champ {
                        id: nom
                        Layout.fillWidth: true
                        Layout.alignment: Qt.AlignBottom
                        libelle: "Nom complet"
                        Component.onCompleted: { text = config.fullName; saisie.forceActiveFocus() }
                        onModifie: texte => page.nomComplet(texte)
                        // Identifiant déduit du nom, modifiable
                        Text {
                            visible: !page.modifierIdentifiant && config.loginName !== ""
                            text: "Identifiant : " + config.loginName
                            font.pixelSize: 12
                            color: "#8A8277"
                        }
                        Text {
                            visible: !page.modifierIdentifiant && config.loginName !== ""
                            text: "Modifier"
                            font.pixelSize: 12
                            font.weight: Font.DemiBold
                            color: "#93401F"
                            MouseArea {
                                anchors.fill: parent
                                anchors.margins: -4
                                cursorShape: Qt.PointingHandCursor
                                onClicked: { page.modifierIdentifiant = true; identifiant.saisie.forceActiveFocus() }
                            }
                        }
                    }
                }

                Champ {
                    id: identifiant
                    // Ouvert à la demande, ou d'office si l'identifiant déduit ne convient pas (« root »…)
                    visible: page.modifierIdentifiant || (config.fullName !== "" && config.loginNameStatus !== "")
                    Layout.fillWidth: true
                    libelle: "Identifiant de connexion"
                    text: config.loginName
                    validateur: RegularExpressionValidator { regularExpression: /[a-z_][a-z0-9_-]*/ }
                    aide: "Lettres minuscules sans accent, chiffres, - et _. Il nomme aussi votre dossier personnel."
                    erreur: config.fullName !== "" ? config.loginNameStatus : ""
                    onModifie: texte => { page.identifiantModifie = true; config.setLoginName(texte) }
                }

                Champ {
                    id: ordinateur
                    Layout.fillWidth: true
                    libelle: "Nom de l'ordinateur"
                    text: config.hostname
                    validateur: RegularExpressionValidator { regularExpression: /[a-zA-Z0-9][-a-zA-Z0-9]*/ }
                    aide: "Visible par les autres appareils du réseau local."
                    erreur: (text !== "" || config.fullName !== "") ? config.hostnameStatus : ""
                    onModifie: texte => { page.ordinateurModifie = texte !== ""; config.setHostName(texte) }
                }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 14
                    ColumnLayout {
                        Layout.fillWidth: true
                        Layout.preferredWidth: 100
                        Layout.alignment: Qt.AlignTop
                        spacing: 8
                        Champ {
                            id: motDePasse
                            Layout.fillWidth: true
                            libelle: "Mot de passe"
                            motDePasse: true
                            // Règle du système non remplie (longueur…) : la raison donnée par Calamares, quand les deux
                            // saisies concordent (sinon c'est la confirmation qui l'explique)
                            erreur: text !== "" && confirmation.text === text && !ViewManager.nextEnabled
                                    ? String(config.userPasswordMessage || "") : ""
                            onModifie: texte => config.setUserPassword(texte)
                        }
                        // Barre de solidité
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 10
                            opacity: page.force > 0 ? 1 : 0
                            Behavior on opacity { NumberAnimation { duration: 160 } }
                            Repeater {
                                model: 4
                                delegate: Rectangle {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 4
                                    Layout.rightMargin: -6
                                    radius: 2
                                    color: index < page.force ? page.niveaux[page.force].couleur : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                                    Behavior on color { ColorAnimation { duration: 160 } }
                                }
                            }
                            Text {
                                Layout.leftMargin: 6
                                text: page.niveaux[page.force].nom
                                font.pixelSize: 12
                                font.weight: Font.DemiBold
                                color: page.niveaux[page.force].couleur
                            }
                        }
                    }
                    Champ {
                        id: confirmation
                        Layout.fillWidth: true
                        Layout.preferredWidth: 100
                        Layout.alignment: Qt.AlignTop
                        libelle: "Confirmer le mot de passe"
                        motDePasse: true
                        erreur: text !== "" && text !== motDePasse.text ? "Les deux mots de passe ne sont pas identiques." : ""
                        onModifie: texte => config.setUserPasswordSecondary(texte)
                    }
                }

                // Connexion automatique
                MouseArea {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 38
                    cursorShape: Qt.PointingHandCursor
                    onClicked: config.setAutoLogin(!config.doAutoLogin)
                    Rectangle {
                        id: caseAuto
                        anchors.left: parent.left
                        anchors.verticalCenter: parent.verticalCenter
                        width: 20
                        height: 20
                        radius: 6
                        color: config.doAutoLogin ? "#B5532F" : "#FFFFFF"
                        border.width: config.doAutoLogin ? 0 : 1.5
                        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                        Canvas {
                            anchors.centerIn: parent
                            visible: config.doAutoLogin
                            width: 14
                            height: 14
                            onPaint: {
                                var c = getContext("2d"); c.reset(); c.scale(14 / 24, 14 / 24)
                                c.strokeStyle = "#FFFFFF"; c.lineWidth = 3; c.lineCap = "round"; c.lineJoin = "round"
                                c.path = "M5 12.5l4.5 4.5L19 7.5"; c.stroke()
                            }
                        }
                    }
                    Column {
                        anchors.left: caseAuto.right
                        anchors.leftMargin: 12
                        anchors.verticalCenter: parent.verticalCenter
                        spacing: 1
                        Text { text: "Se connecter automatiquement"; font.pixelSize: 14; color: "#1F1C18" }
                        Text { text: "Déconseillé si l'ordinateur est partagé."; font.pixelSize: 12; color: "#8A8277" }
                    }
                }
            }

            // ——— Plusieurs personnes ———
            Rectangle {
                Layout.preferredWidth: 236
                Layout.alignment: Qt.AlignTop
                Layout.preferredHeight: carte.implicitHeight + 40
                radius: 18
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
                ColumnLayout {
                    id: carte
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.top: parent.top
                    anchors.margins: 20
                    spacing: 12
                    Rectangle {
                        Layout.preferredWidth: 36
                        Layout.preferredHeight: 36
                        radius: 11
                        color: Qt.rgba(61 / 255, 90 / 255, 153 / 255, 0.12)
                        Canvas {
                            anchors.centerIn: parent
                            width: 18
                            height: 18
                            onPaint: {
                                var c = getContext("2d"); c.reset(); c.scale(18 / 24, 18 / 24)
                                c.strokeStyle = "#3D5A99"; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                                c.path = "M9 11a3.5 3.5 0 1 0 0-7a3.5 3.5 0 1 0 0 7z M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6 M16 4.5a3.5 3.5 0 0 1 0 6.5 M18 14.5c1.8 0.8 3 2.6 3 4.5"
                                c.stroke()
                            }
                        }
                    }
                    Text {
                        Layout.fillWidth: true
                        wrapMode: Text.WordWrap
                        text: "D'autres personnes utilisent cet ordinateur ?"
                        font.pixelSize: 14
                        font.weight: Font.Medium
                        lineHeight: 1.15
                        color: "#1F1C18"
                    }
                    Text {
                        Layout.fillWidth: true
                        wrapMode: Text.WordWrap
                        text: "Chacun peut avoir son propre compte, avec ses fichiers et ses réglages. Ajoutez-les après l'installation, dans Réglages › Comptes."
                        font.pixelSize: 12
                        lineHeight: 1.25
                        color: "#665E54"
                    }
                }
            }
        }
        Item { Layout.fillHeight: true }
    }
}
