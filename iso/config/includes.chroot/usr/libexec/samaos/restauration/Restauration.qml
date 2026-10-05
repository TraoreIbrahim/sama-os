// « Sama a été restauré » (maquette ses-06) : à l'ouverture de session qui suit une restauration (mise à jour coupée
// net, retour demandé dans Réglages ou choisi au démarrage). Dit ce qui s'est passé, montre les instantanés :
// continuer avec cet état, revenir à un autre (redémarrage), réessayer la mise à jour, ou ouvrir un terminal.
// Lancé par restauration.sh ; argument : JSON { note (restauration.json), etat (instantanes.py etat), version }.
import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtQuick.Effects
import org.kde.plasma.plasma5support as P5Support

Window {
    id: fenetre
    visibility: Window.FullScreen
    flags: Qt.FramelessWindowHint
    color: "#F3ECE2"
    title: "Sama a été restauré"

    readonly property var infos: {
        var a = Qt.application.arguments
        try { return JSON.parse(String(a[a.length - 1])) } catch (e) { return {} }
    }
    readonly property var note: infos.note || {}
    readonly property var liste: (infos.etat && infos.etat.liste) || []
    readonly property string raison: note.raison || "menu"
    // Les instantanés proposés : celui qui vient d'être restauré en premier ; après un retour demandé, l'état d'avant
    // ce retour (pour l'annuler) ; puis les plus récents
    readonly property var proposes: {
        var l = liste.filter(function (i) { return i.id === note.instantane })
        var avant = raison !== "maj-interrompue" ? liste.filter(function (i) { return i.type === "restauration" }).slice(0, 1) : []
        return l.concat(avant, liste.filter(function (i) { return i.id !== note.instantane && i.type !== "restauration" })).slice(0, 4)
    }
    property int choisi: 0
    readonly property bool autre: proposes.length > 0 && proposes[choisi].id !== note.instantane
    property string etape: ""          // "" | "maj" (mise à jour en cours) | "maj-finie" | "maj-ratee" | "retour"

    readonly property color texte: "#1F1C18"
    readonly property color texte2: "#665E54"
    readonly property color texte3: "#8A8277"
    readonly property color laterite: "#B5532F"
    readonly property color lateriteEncre: "#93401F"
    readonly property color foret: "#2F6B57"

    readonly property var mois: ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]
    readonly property var moisCourts: ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."]
    function heure(d) { return d.toLocaleTimeString(Qt.locale(), "HH:mm") }
    // « 30 septembre, 23:10 » ; « 30 sept. · 23:10 »
    function dateLongue(s) { var d = new Date(s * 1000); return d.getDate() + (d.getDate() === 1 ? "er " : " ") + mois[d.getMonth()] + ", " + heure(d) }
    function dateCourte(s) { var d = new Date(s * 1000); return d.getDate() + " " + moisCourts[d.getMonth()] + " · " + heure(d) }
    function description(i) {
        if (i.type === "restauration") return "Juste avant ce retour en arrière"
        if (i.type === "hebdo") return "Instantané hebdomadaire"
        if (i.type === "manuel") return "Créé à la main" + (i.detail ? " · « " + i.detail + " »" : "")
        return i.detail || i.libelle
    }

    P5Support.DataSource {
        id: executeur
        engine: "executable"
        property var rappels: ({})
        function lancer(commande, rappel) { var r = rappels; r[commande] = rappel; rappels = r; connectSource(commande) }
        function q(t) { return "'" + String(t).replace(/'/g, "'\\''") + "'" }
        onNewData: (source, donnees) => {
            var rappel = rappels[source]
            disconnectSource(source)
            if (rappel) rappel(String(donnees["stdout"] || ""), Number(donnees["exit code"] || 0))
        }
    }
    function fermer() { Qt.quit() }
    function continuer() {
        if (!autre) { fermer(); return }
        etape = "retour"
        executeur.lancer("pkexec /usr/libexec/samaos/restaurer-instantane " + executeur.q(proposes[choisi].id), function (s, code) {
            if (code === 0) executeur.lancer("dbus-send --session --print-reply --dest=org.kde.Shutdown /Shutdown org.kde.Shutdown.logoutAndReboot >/dev/null 2>&1 || systemctl reboot", function () {})
            else fenetre.etape = ""
        })
    }
    function reessayer() {
        etape = "maj"
        executeur.lancer("pkexec /usr/libexec/samaos/mises-a-jour-nuit.sh maintenant >/dev/null 2>&1; echo $?", function (s) {
            var code = Number(s.trim())
            fenetre.etape = code === 0 ? "maj-finie" : code === 126 || code === 127 ? "" : "maj-ratee"
        })
    }
    Shortcut { sequence: "Escape"; enabled: fenetre.etape === "" || fenetre.etape === "maj-finie" || fenetre.etape === "maj-ratee"; onActivated: fenetre.fermer() }

    // Fond : l'aube de Sama, adoucie
    Image {
        id: fond
        anchors.fill: parent
        source: "file:///usr/share/samaos/fonds/sama-aube.svg"
        sourceSize.width: 1440
        sourceSize.height: 900
        fillMode: Image.PreserveAspectCrop
        visible: false
    }
    MultiEffect {
        anchors.fill: parent
        source: fond
        blurEnabled: true
        blurMax: 64
        blur: 1.0
    }
    Rectangle { anchors.fill: parent; color: Qt.rgba(243 / 255, 236 / 255, 226 / 255, 0.35) }

    // L'éléphant de Sama, en haut
    Image {
        anchors.horizontalCenter: parent.horizontalCenter
        y: 36
        source: "file:///usr/share/samaos/logo-sama.svg"
        sourceSize.width: 64
        sourceSize.height: 64
        width: 32
        height: 32
    }

    component Picto: Canvas {
        property string trace
        property color encre: fenetre.texte
        property real epaisseur: 1.8
        onTraceChanged: requestPaint()
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
            c.strokeStyle = encre; c.lineWidth = epaisseur; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }

    // Ombre douce sous la carte
    MultiEffect {
        source: carte
        anchors.fill: carte
        shadowEnabled: true
        shadowColor: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.2)
        shadowBlur: 1.0
        blurMax: 64
        shadowVerticalOffset: 16
        opacity: carte.opacity
    }
    Rectangle {
        id: carte
        anchors.centerIn: parent
        width: 640
        height: colonne.implicitHeight + 74
        radius: 22
        color: Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.96)
        border.width: 0.5
        border.color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.12)

        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.leftMargin: 44
            anchors.rightMargin: 44
            anchors.topMargin: 40
            spacing: 0

            RowLayout {
                spacing: 14
                Rectangle {
                    Layout.preferredWidth: 52
                    Layout.preferredHeight: 52
                    radius: 16
                    color: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.12)
                    Picto { anchors.centerIn: parent; width: 26; height: 26; encre: fenetre.foret; trace: "M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6z M8.8 12.2l2.2 2.2l4.2-4.4" }
                }
                Text { text: "Sama a été restauré"; font.pixelSize: 28; font.weight: Font.Medium; color: fenetre.texte }
            }
            Text {
                Layout.topMargin: 16
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                lineHeight: 1.25
                font.pixelSize: 15
                color: fenetre.texte2
                text: {
                    var quand = fenetre.note.date ? "l'instantané du " + fenetre.dateLongue(fenetre.note.date) : "un instantané"
                    if (fenetre.raison === "maj-interrompue")
                        return "La dernière mise à jour s'est interrompue. Votre système a été ramené à " + quand + ". Vos documents n'ont pas été touchés."
                    if (fenetre.raison === "demande")
                        return "Comme demandé, votre système a été ramené à " + quand + ". Vos documents, vos comptes et vos réseaux Wi-Fi n'ont pas été touchés."
                    return "Votre système a été ramené à " + quand + ", choisi au démarrage. Vos documents n'ont pas été touchés."
                }
            }

            // Mise à jour relancée
            ColumnLayout {
                visible: fenetre.etape.indexOf("maj") === 0
                Layout.topMargin: 24
                Layout.fillWidth: true
                spacing: 10
                Text {
                    text: fenetre.etape === "maj" ? "Mise à jour en cours… Laissez l'ordinateur branché."
                        : fenetre.etape === "maj-finie" ? "La mise à jour s'est bien passée : Sama est à jour."
                        : "La mise à jour n'a pas pu se faire (pas de connexion ?). Elle sera retentée la nuit prochaine."
                    font.pixelSize: 14
                    font.weight: Font.Medium
                    color: fenetre.etape === "maj-ratee" ? fenetre.lateriteEncre : fenetre.texte
                }
                Rectangle {
                    id: piste
                    visible: fenetre.etape === "maj"
                    Layout.fillWidth: true
                    Layout.preferredHeight: 6
                    radius: 3
                    clip: true
                    color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.09)
                    Rectangle {
                        width: piste.width * 0.3
                        height: 6
                        radius: 3
                        color: fenetre.laterite
                        NumberAnimation on x { running: piste.visible; loops: Animation.Infinite; from: -piste.width * 0.3; to: piste.width; duration: 1400; easing.type: Easing.InOutQuad }
                    }
                }
            }

            // Les instantanés
            ColumnLayout {
                visible: fenetre.etape === "" || fenetre.etape === "retour"
                Layout.fillWidth: true
                spacing: 0
                Text {
                    visible: fenetre.proposes.length > 0
                    Layout.topMargin: 26
                    text: "INSTANTANÉS DISPONIBLES"
                    font.pixelSize: 12
                    font.weight: Font.DemiBold
                    font.letterSpacing: 0.4
                    color: fenetre.texte3
                }
                Repeater {
                    model: fenetre.proposes
                    delegate: QQC2.AbstractButton {
                        id: rangee
                        required property var modelData
                        required property int index
                        readonly property bool actif: fenetre.choisi === index
                        Layout.fillWidth: true
                        Layout.topMargin: index === 0 ? 10 : 8
                        implicitHeight: 62
                        hoverEnabled: true
                        leftPadding: 18
                        rightPadding: 18
                        enabled: fenetre.etape === ""
                        onClicked: fenetre.choisi = index
                        background: Rectangle {
                            radius: 14
                            color: rangee.actif ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.1) : Qt.rgba(252 / 255, 250 / 255, 247 / 255, rangee.hovered ? 1 : 0.7)
                            border.width: rangee.actif ? 1.5 : 0.5
                            border.color: rangee.actif ? fenetre.laterite : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                        }
                        contentItem: RowLayout {
                            spacing: 14
                            Rectangle {
                                Layout.preferredWidth: 20
                                Layout.preferredHeight: 20
                                radius: 10
                                color: rangee.actif ? fenetre.laterite : "transparent"
                                border.width: rangee.actif ? 0 : 1.5
                                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.24)
                                Rectangle { anchors.centerIn: parent; width: 8; height: 8; radius: 4; color: "#FFFFFF"; visible: rangee.actif }
                            }
                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 3
                                RowLayout {
                                    spacing: 10
                                    Text { text: fenetre.dateCourte(rangee.modelData.quand); font.pixelSize: 14; font.weight: Font.DemiBold; color: fenetre.texte }
                                    Rectangle {
                                        visible: rangee.modelData.type === "maj" || rangee.modelData.type === "appli"
                                        implicitWidth: pastille.implicitWidth + 18
                                        implicitHeight: 22
                                        radius: 11
                                        color: fenetre.laterite
                                        Text { id: pastille; anchors.centerIn: parent; text: rangee.modelData.libelle; font.pixelSize: 11; font.weight: Font.DemiBold; color: "#FFFFFF" }
                                    }
                                }
                                Text { Layout.fillWidth: true; text: fenetre.description(rangee.modelData); elide: Text.ElideRight; font.pixelSize: 12; color: fenetre.texte2 }
                            }
                            Text {
                                visible: rangee.modelData.id === fenetre.note.instantane
                                text: "État actuel"
                                font.pixelSize: 12
                                font.weight: Font.Medium
                                color: fenetre.foret
                            }
                        }
                    }
                }
            }

            // Actions
            RowLayout {
                Layout.topMargin: 28
                spacing: 10
                QQC2.AbstractButton {
                    id: boutonContinuer
                    implicitHeight: 42
                    implicitWidth: libelleContinuer.implicitWidth + 48
                    enabled: fenetre.etape !== "maj" && fenetre.etape !== "retour"
                    hoverEnabled: true
                    onClicked: fenetre.etape === "" ? fenetre.continuer() : fenetre.fermer()
                    background: Rectangle { radius: 21; color: fenetre.laterite; opacity: !parent.enabled ? 0.6 : parent.down ? 0.85 : parent.hovered ? 0.93 : 1 }
                    contentItem: Text {
                        id: libelleContinuer
                        text: fenetre.etape === "retour" ? "Redémarrage…" : fenetre.etape !== "" ? "Continuer"
                            : fenetre.autre ? "Revenir à cet état" : "Continuer avec cet état"
                        font.pixelSize: 14
                        font.weight: Font.DemiBold
                        color: "#FFFFFF"
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                    }
                }
                QQC2.AbstractButton {
                    id: boutonReessayer
                    visible: fenetre.raison === "maj-interrompue" && fenetre.etape === ""
                    implicitHeight: 42
                    implicitWidth: ligneReessayer.implicitWidth + 44
                    hoverEnabled: true
                    onClicked: fenetre.reessayer()
                    background: Rectangle { radius: 21; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, boutonReessayer.hovered ? 0.1 : 0.06) }
                    contentItem: Item {
                        Row {
                            id: ligneReessayer
                            anchors.centerIn: parent
                            spacing: 8
                            Picto { anchors.verticalCenter: parent.verticalCenter; width: 16; height: 16; trace: "M4 12a8 8 0 1 0 2.3-5.6 M4 5v4h4" }
                            Text { anchors.verticalCenter: parent.verticalCenter; text: "Réessayer la mise à jour"; font.pixelSize: 14; font.weight: Font.Medium; color: fenetre.texte }
                        }
                    }
                }
            }

            // Pied : terminal de secours, version
            Rectangle { Layout.topMargin: 22; Layout.fillWidth: true; Layout.preferredHeight: 1; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08) }
            RowLayout {
                Layout.topMargin: 16
                Layout.fillWidth: true
                QQC2.AbstractButton {
                    id: boutonTerminal
                    implicitHeight: 24
                    implicitWidth: ligneTerminal.implicitWidth
                    hoverEnabled: true
                    onClicked: executeur.lancer("setsid -f konsole >/dev/null 2>&1", function () {})
                    background: Item {}
                    contentItem: Item {
                        Row {
                            id: ligneTerminal
                            anchors.verticalCenter: parent.verticalCenter
                            spacing: 8
                            Picto { anchors.verticalCenter: parent.verticalCenter; width: 16; height: 16; encre: fenetre.lateriteEncre; trace: "M4 5h16v14H4z M7 9l3 3l-3 3 M12 15h5" }
                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                text: "Options avancées (terminal de secours)"
                                font.pixelSize: 13
                                font.weight: Font.Medium
                                font.underline: boutonTerminal.hovered
                                color: fenetre.lateriteEncre
                            }
                        }
                    }
                }
                Item { Layout.fillWidth: true }
                Text { text: "Sama " + (fenetre.infos.version || "") + " · Debian"; font.pixelSize: 12; color: fenetre.texte3 }
            }
        }
    }
}
