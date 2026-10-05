// « Le courant a été coupé à 11:05 » (maquette ale-04) : à l'ouverture de session qui suit une coupure, ce qui était
// ouvert (applications, documents gardés par Sama Docs) et « Tout rouvrir ». Lancé par courant.py, qui lui passe le
// bilan en JSON : { heure, applis: [{ id, nom, icone, fichiers }], documents: [{ titre, appli, icone, quand, nonEnregistre }] }.
import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtQuick.Effects
import org.kde.kirigami as Kirigami
import org.kde.plasma.plasma5support as P5Support

Window {
    id: fenetre
    visibility: Window.FullScreen
    flags: Qt.FramelessWindowHint
    color: "transparent"
    title: "Le courant a été coupé"

    readonly property var bilan: {
        var a = Qt.application.arguments
        try { return JSON.parse(String(a[a.length - 1])) } catch (e) { return {} }
    }
    readonly property var documents: bilan.documents || []
    readonly property var applis: bilan.applis || []
    readonly property int nonEnregistres: documents.filter(function (d) { return d.nonEnregistre }).length
    readonly property int fenetres: applis.length + (documents.length ? 1 : 0)

    SystemPalette { id: palette }
    readonly property bool sombre: palette.window.hslLightness < 0.5
    readonly property color fond: sombre ? "#1E2233" : "#FBF9F6"
    readonly property color texte: sombre ? "#F1EBE1" : "#1F1C18"
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color texte3: sombre ? "#8C867A" : "#8A8277"
    readonly property color laterite: "#B5532F"
    readonly property color foret: sombre ? "#A3D6C1" : "#2F6B57"

    function heure(s) { return new Date(s * 1000).toLocaleTimeString(Qt.locale(), "HH:mm") }
    // « 3 fenêtres et 2 documents non enregistrés »
    function resume() {
        var morceaux = []
        if (fenetres) morceaux.push(fenetres + (fenetres > 1 ? " fenêtres" : " fenêtre"))
        if (nonEnregistres) morceaux.push(nonEnregistres + (nonEnregistres > 1 ? " documents non enregistrés" : " document non enregistré"))
        return morceaux.join(" et ")
    }
    function detailAppli(a) {
        if (a.fichiers && a.fichiers.length) {
            var noms = a.fichiers.map(function (f) { return f.split("/").filter(function (x) { return x }).pop() || f })
            return noms.slice(0, 3).join(", ") + (noms.length > 3 ? "…" : "")
        }
        return /griot|chromium/.test(a.id) ? "Les onglets ouverts reviennent avec lui" : "Rouvert tel quel"
    }

    P5Support.DataSource {
        id: executeur
        engine: "executable"
        property var rappels: ({})
        function lancer(commande, rappel) { var r = rappels; r[commande] = rappel; rappels = r; connectSource(commande) }
        onNewData: (source, donnees) => {
            var rappel = rappels[source]
            disconnectSource(source)
            if (rappel) rappel()
        }
    }
    property bool enCours: false
    function toutRouvrir() {
        enCours = true
        executeur.lancer("python3 /usr/libexec/samaos/courant.py rouvrir", function () { Qt.quit() })
    }
    function plusTard() {
        executeur.lancer("python3 /usr/libexec/samaos/courant.py vu", function () { Qt.quit() })
    }
    Shortcut { sequence: "Escape"; enabled: !fenetre.enCours; onActivated: fenetre.plusTard() }

    // Voile sur le bureau
    Rectangle {
        anchors.fill: parent
        color: fenetre.sombre ? Qt.rgba(21 / 255, 26 / 255, 43 / 255, 0.55) : Qt.rgba(243 / 255, 236 / 255, 226 / 255, 0.45)
        opacity: 0
        Component.onCompleted: opacity = 1
        Behavior on opacity { NumberAnimation { duration: 300; easing.type: Easing.OutCubic } }
    }

    component Picto: Canvas {
        property string trace
        property color encre: fenetre.texte
        onTraceChanged: requestPaint()
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
            c.strokeStyle = encre; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }
    // Une ligne : tuile de l'application, titre, détail, « Non enregistré », coche verte
    component Rangee: Item {
        property string icone
        property string titre
        property string detail
        property bool nonEnregistre: false
        property bool derniere: false
        Layout.fillWidth: true
        implicitHeight: 64
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 16
            anchors.rightMargin: 16
            spacing: 14
            Kirigami.Icon {
                Layout.preferredWidth: 36
                Layout.preferredHeight: 36
                source: icone.indexOf("/") === 0 ? "file://" + icone : (icone || "application-x-executable")
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text { Layout.fillWidth: true; text: titre; elide: Text.ElideRight; font.pixelSize: 14; font.weight: Font.Medium; color: fenetre.texte }
                Text { Layout.fillWidth: true; text: detail; elide: Text.ElideRight; font.pixelSize: 12; color: fenetre.texte2 }
            }
            Rectangle {
                visible: nonEnregistre
                implicitWidth: pastille.implicitWidth + 18
                implicitHeight: 22
                radius: 11
                color: fenetre.sombre ? Qt.rgba(246 / 255, 231 / 255, 204 / 255, 0.18) : "#F6E7CC"
                Text { id: pastille; anchors.centerIn: parent; text: "Non enregistré"; font.pixelSize: 11; font.weight: Font.DemiBold; color: fenetre.sombre ? "#F2C879" : "#7A4E0C" }
            }
            Picto { Layout.preferredWidth: 18; Layout.preferredHeight: 18; encre: fenetre.foret; trace: "M5 12.5l4.5 4.5L19 7.5" }
        }
        Rectangle { visible: !derniere; anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08) }
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
        anchors.horizontalCenter: parent.horizontalCenter
        y: 88
        width: 560
        height: colonne.implicitHeight + 60
        radius: 22
        color: fenetre.fond
        border.width: 0.5
        border.color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.12)
        opacity: 0
        scale: 0.97
        Component.onCompleted: { opacity = 1; scale = 1 }
        Behavior on opacity { NumberAnimation { duration: 260; easing.type: Easing.OutCubic } }
        Behavior on scale { NumberAnimation { duration: 260; easing.type: Easing.OutCubic } }

        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 30
            spacing: 22

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 14
                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: 60
                    Layout.preferredHeight: 60
                    radius: 30
                    color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.1)
                    Picto { anchors.centerIn: parent; width: 28; height: 28; encre: fenetre.laterite; trace: "M12 3v8 M6.3 6.8a8 8 0 1 0 11.4 0" }
                }
                Text {
                    Layout.alignment: Qt.AlignHCenter
                    text: "Le courant a été coupé à " + fenetre.heure(fenetre.bilan.heure || 0)
                    font.pixelSize: 24
                    font.weight: Font.Medium
                    color: fenetre.texte
                }
                Text {
                    Layout.fillWidth: true
                    Layout.leftMargin: 40
                    Layout.rightMargin: 40
                    horizontalAlignment: Text.AlignHCenter
                    wrapMode: Text.WordWrap
                    lineHeight: 1.2
                    text: "Sama a tout retrouvé : " + fenetre.resume() + "." + (fenetre.documents.length ? " Rien n'a été perdu." : "")
                    font.pixelSize: 14
                    color: fenetre.texte2
                }
            }

            // Ce qui était ouvert
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: liste.implicitHeight
                radius: 14
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, fenetre.sombre ? 0.25 : 0.05)
                clip: true
                ColumnLayout {
                    id: liste
                    width: parent.width
                    spacing: 0
                    Repeater {
                        model: fenetre.documents.slice(0, 4)
                        delegate: Rangee {
                            icone: modelData.icone
                            titre: modelData.appli + " — " + modelData.titre
                            detail: "Récupéré · modifié à " + fenetre.heure(modelData.quand)
                            nonEnregistre: modelData.nonEnregistre
                            derniere: index === Math.min(fenetre.documents.length, 4) - 1 && fenetre.applis.length === 0
                        }
                    }
                    Repeater {
                        model: fenetre.applis.slice(0, Math.max(0, 5 - Math.min(fenetre.documents.length, 4)))
                        delegate: Rangee {
                            icone: modelData.icone
                            titre: modelData.nom
                            detail: fenetre.detailAppli(modelData)
                            derniere: index === Math.min(fenetre.applis.length, 5 - Math.min(fenetre.documents.length, 4)) - 1
                        }
                    }
                }
            }

            RowLayout {
                Layout.fillWidth: true
                spacing: 10
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 6
                    Picto { Layout.preferredWidth: 14; Layout.preferredHeight: 14; encre: fenetre.texte3; trace: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8h.01" }
                    Text { text: "Sauvegarde auto toutes les 30 s"; font.pixelSize: 12; color: fenetre.texte3 }
                }
                QQC2.AbstractButton {
                    id: boutonPlusTard
                    implicitHeight: 38
                    implicitWidth: libellePlusTard.implicitWidth + 42
                    enabled: !fenetre.enCours
                    hoverEnabled: true
                    onClicked: fenetre.plusTard()
                    background: Rectangle { radius: 19; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, boutonPlusTard.hovered ? 0.09 : 0.05) }
                    contentItem: Text {
                        id: libellePlusTard
                        text: "Voir plus tard"
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        font.pixelSize: 13
                        font.weight: Font.Medium
                        color: fenetre.texte
                    }
                }
                QQC2.AbstractButton {
                    id: boutonRouvrir
                    implicitHeight: 38
                    implicitWidth: libelleRouvrir.implicitWidth + 42
                    enabled: !fenetre.enCours
                    hoverEnabled: true
                    focus: true
                    onClicked: fenetre.toutRouvrir()
                    Keys.onReturnPressed: fenetre.toutRouvrir()
                    background: Rectangle { radius: 19; color: fenetre.laterite; opacity: boutonRouvrir.down ? 0.85 : (boutonRouvrir.hovered ? 0.93 : 1) }
                    contentItem: Text {
                        id: libelleRouvrir
                        text: fenetre.enCours ? "Ouverture…" : "Tout rouvrir"
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        font.pixelSize: 13
                        font.weight: Font.DemiBold
                        color: "#FFFFFF"
                    }
                }
            }
        }
    }
}
