// Capture d'écran de Sama (maquette ale-07) : l'écran figé, une zone à tracer (poignées, lignes des tiers, taille),
// ou la fenêtre active, ou l'écran entier ; minuteur ; vidéo. Lancé par capture.py avec { image, sortie } ; rend son
// choix sur la sortie d'erreur : « SAMA_CAPTURE={…} ». Avec { decoupe: [x, y, l, h] } (sans écran) : découpe l'image.
import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2

Window {
    id: fenetre
    readonly property var infos: {
        var a = Qt.application.arguments
        try { return JSON.parse(String(a[a.length - 1])) } catch (e) { return {} }
    }
    readonly property bool decoupe: infos.decoupe !== undefined
    visibility: decoupe ? Window.Windowed : Window.FullScreen
    flags: Qt.FramelessWindowHint
    width: decoupe ? infos.decoupe[2] : 800
    height: decoupe ? infos.decoupe[3] : 600
    color: "#000000"
    title: "Capture d'écran"
    visible: true

    property string mode: "zone"                  // zone | fenetre | ecran
    property int delai: 0
    readonly property var delais: [0, 3, 5, 10]
    // Zone choisie, en coordonnées de la fenêtre (vide : pas encore tracée)
    property real zx: 0
    property real zy: 0
    property real zl: 0
    property real zh: 0
    readonly property bool zoneTracee: zl >= 8 && zh >= 8
    // Pixels de l'image par point de l'écran (écrans à haute densité)
    readonly property real echelle: ecran.sourceSize.width > 0 && width > 0 ? ecran.sourceSize.width / width : 1

    function rendre(objet) { console.log("SAMA_CAPTURE=" + JSON.stringify(objet)); Qt.quit() }
    function capturer() {
        if (mode === "zone") {
            if (!zoneTracee) return
            var z = [Math.round(zx * echelle), Math.round(zy * echelle), Math.round(zl * echelle), Math.round(zh * echelle)]
            if (delai > 0) { rendre({ mode: "zone", zone: z, delai: delai }); return }
            enregistreur.zone = z
            enregistreur.enregistrer()
        } else rendre({ mode: mode, delai: delai })
    }

    // ——— Découpe d'une zone de l'image (l'image entière est chargée, seule la zone est dessinée) ———
    Item {
        id: enregistreur
        property var zone: [0, 0, 1, 1]
        x: fenetre.decoupe ? 0 : -20000          // (hors de la vue, mais dessinée : grabToImage en a besoin)
        width: zone[2]
        height: zone[3]
        Image {
            id: morceau
            anchors.fill: parent
            source: enregistreur.zone[2] > 1 || fenetre.decoupe ? "file://" + fenetre.infos.image : ""
            sourceClipRect: Qt.rect(enregistreur.zone[0], enregistreur.zone[1], enregistreur.zone[2], enregistreur.zone[3])
            cache: false
            onStatusChanged: if (status === Image.Ready && (fenetre.decoupe || enregistreur.attente)) enregistreur.enregistrer()
        }
        property bool attente: false
        function enregistrer() {
            if (morceau.status !== Image.Ready) { attente = true; return }
            attente = false
            grabToImage(function (resultat) {
                if (resultat.saveToFile(fenetre.infos.sortie)) fenetre.rendre({ mode: "zone", fait: fenetre.infos.sortie })
                else fenetre.rendre({})
            }, Qt.size(zone[2], zone[3]))
        }
        Component.onCompleted: if (fenetre.decoupe) zone = fenetre.infos.decoupe
    }

    // ——— L'écran figé ———
    Image {
        id: ecran
        visible: !fenetre.decoupe
        anchors.fill: parent
        source: fenetre.decoupe ? "" : "file://" + fenetre.infos.image
        fillMode: Image.Stretch
        cache: false
    }
    Item {
        visible: !fenetre.decoupe
        anchors.fill: parent

        // Voile autour de la zone (ou partout tant qu'elle n'est pas tracée ; nulle part pour l'écran entier)
        readonly property color voile: Qt.rgba(24 / 255, 19 / 255, 14 / 255, 0.38)
        Rectangle { visible: fenetre.mode !== "ecran"; x: 0; y: 0; width: parent.width; height: fenetre.mode === "zone" && fenetre.zoneTracee ? fenetre.zy : parent.height; color: parent.voile }
        Rectangle { visible: fenetre.mode === "zone" && fenetre.zoneTracee; x: 0; y: fenetre.zy + fenetre.zh; width: parent.width; height: parent.height - y; color: parent.voile }
        Rectangle { visible: fenetre.mode === "zone" && fenetre.zoneTracee; x: 0; y: fenetre.zy; width: fenetre.zx; height: fenetre.zh; color: parent.voile }
        Rectangle { visible: fenetre.mode === "zone" && fenetre.zoneTracee; x: fenetre.zx + fenetre.zl; y: fenetre.zy; width: parent.width - x; height: fenetre.zh; color: parent.voile }

        // Tracer, déplacer la zone
        MouseArea {
            id: traceur
            anchors.fill: parent
            enabled: fenetre.mode === "zone"
            cursorShape: Qt.CrossCursor
            property real ox: 0
            property real oy: 0
            property bool deplace: false
            property real dx: 0
            property real dy: 0
            onPressed: souris => {
                deplace = fenetre.zoneTracee && souris.x > fenetre.zx && souris.x < fenetre.zx + fenetre.zl
                          && souris.y > fenetre.zy && souris.y < fenetre.zy + fenetre.zh
                if (deplace) { dx = souris.x - fenetre.zx; dy = souris.y - fenetre.zy }
                else { ox = souris.x; oy = souris.y; fenetre.zx = ox; fenetre.zy = oy; fenetre.zl = 0; fenetre.zh = 0 }
            }
            onPositionChanged: souris => {
                if (deplace) {
                    fenetre.zx = Math.max(0, Math.min(width - fenetre.zl, souris.x - dx))
                    fenetre.zy = Math.max(0, Math.min(height - fenetre.zh, souris.y - dy))
                } else {
                    var x = Math.max(0, Math.min(width, souris.x)), y = Math.max(0, Math.min(height, souris.y))
                    fenetre.zx = Math.min(ox, x); fenetre.zy = Math.min(oy, y)
                    fenetre.zl = Math.abs(x - ox); fenetre.zh = Math.abs(y - oy)
                }
            }
            onDoubleClicked: fenetre.capturer()
        }

        // La zone : bord blanc, lignes des tiers, poignées, taille
        Item {
            id: cadre
            visible: fenetre.mode === "zone" && fenetre.zoneTracee
            x: fenetre.zx; y: fenetre.zy; width: fenetre.zl; height: fenetre.zh
            Rectangle { anchors.fill: parent; anchors.margins: -1; color: "transparent"; border.width: 1; border.color: "#FFFFFF" }
            Repeater {
                model: 2
                delegate: Rectangle { x: cadre.width * (index + 1) / 3; width: 0.5; height: cadre.height; color: Qt.rgba(1, 1, 1, 0.35) }
            }
            Repeater {
                model: 2
                delegate: Rectangle { y: cadre.height * (index + 1) / 3; height: 0.5; width: cadre.width; color: Qt.rgba(1, 1, 1, 0.35) }
            }
            Rectangle {
                y: -36
                height: 26
                width: taille.implicitWidth + 20
                radius: 13
                color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.82)
                Text {
                    id: taille
                    anchors.centerIn: parent
                    text: Math.round(fenetre.zl * fenetre.echelle) + " × " + Math.round(fenetre.zh * fenetre.echelle)
                    font.pixelSize: 12
                    font.weight: Font.DemiBold
                    color: "#FFFFFF"
                }
            }
            // Poignées : coins et milieux des côtés (fx, fy : quels bords elles tirent)
            Repeater {
                model: [[0, 0], [0.5, 0], [1, 0], [0, 0.5], [1, 0.5], [0, 1], [0.5, 1], [1, 1]]
                delegate: Rectangle {
                    id: poignee
                    required property var modelData
                    x: modelData[0] * cadre.width - 6
                    y: modelData[1] * cadre.height - 6
                    width: 12; height: 12; radius: 6
                    color: "#FFFFFF"
                    border.width: 1.5
                    border.color: "#B5532F"
                    MouseArea {
                        anchors.fill: parent
                        anchors.margins: -6
                        cursorShape: Qt.SizeAllCursor
                        onPositionChanged: souris => {
                            var p = mapToItem(traceur, souris.x, souris.y)
                            var g = fenetre.zx, h = fenetre.zy, d = fenetre.zx + fenetre.zl, b = fenetre.zy + fenetre.zh
                            if (poignee.modelData[0] === 0) g = Math.min(p.x, d - 8)
                            if (poignee.modelData[0] === 1) d = Math.max(p.x, g + 8)
                            if (poignee.modelData[1] === 0) h = Math.min(p.y, b - 8)
                            if (poignee.modelData[1] === 1) b = Math.max(p.y, h + 8)
                            fenetre.zx = Math.max(0, g); fenetre.zy = Math.max(0, h)
                            fenetre.zl = Math.min(traceur.width, d) - fenetre.zx; fenetre.zh = Math.min(traceur.height, b) - fenetre.zy
                        }
                    }
                }
            }
        }

        // Consigne (zone pas encore tracée, ou fenêtre active)
        Rectangle {
            visible: (fenetre.mode === "zone" && !fenetre.zoneTracee && !traceur.pressed) || fenetre.mode === "fenetre"
            anchors.centerIn: parent
            width: consigne.implicitWidth + 36
            height: 40
            radius: 20
            color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.82)
            Text {
                id: consigne
                anchors.centerIn: parent
                text: fenetre.mode === "fenetre" ? "La fenêtre active sera capturée" : "Tracez la zone à capturer"
                font.pixelSize: 14
                color: "#FFFFFF"
            }
        }

        component Picto: Canvas {
            property string trace
            property color encre: "#1F1C18"
            property bool plein: false
            width: 18; height: 18
            onTraceChanged: requestPaint()
            onEncreChanged: requestPaint()
            onPaint: {
                var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
                c.strokeStyle = encre; c.lineWidth = 1.7; c.lineCap = "round"; c.lineJoin = "round"
                c.path = trace; c.stroke()
                if (plein) { c.beginPath(); c.arc(12, 12, 3, 0, 2 * Math.PI); c.fillStyle = encre; c.fill() }
            }
        }
        component Choix: QQC2.AbstractButton {
            id: bouton
            property string trace
            property bool actif: false
            implicitHeight: 44
            implicitWidth: ligne.implicitWidth + 24
            hoverEnabled: true
            background: Rectangle { radius: 14; color: bouton.actif ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.1) : bouton.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent" }
            contentItem: Item {
                Row {
                    id: ligne
                    anchors.centerIn: parent
                    spacing: 8
                    Picto { anchors.verticalCenter: parent.verticalCenter; trace: bouton.trace; encre: bouton.actif ? "#B5532F" : "#1F1C18" }
                    Text { anchors.verticalCenter: parent.verticalCenter; text: bouton.text; font.pixelSize: 13; font.weight: bouton.actif ? Font.DemiBold : Font.Medium; color: bouton.actif ? "#B5532F" : "#1F1C18" }
                }
            }
        }

        // Barre d'outils, au-dessus de la Natte
        Rectangle {
            id: barre
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 96
            width: outils.implicitWidth + 16
            height: 60
            radius: 22
            color: Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.96)
            border.width: 0.5
            border.color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.12)
            MouseArea { anchors.fill: parent }          // (un clic sur la barre ne trace pas de zone)
            RowLayout {
                id: outils
                anchors.centerIn: parent
                spacing: 4
                Choix { text: "Zone"; trace: "M4 8V4h4 M16 4h4v4 M20 16v4h-4 M8 20H4v-4"; actif: fenetre.mode === "zone"; onClicked: fenetre.mode = "zone" }
                Choix { text: "Fenêtre"; trace: "M4 5h16v14H4z M4 9h16"; actif: fenetre.mode === "fenetre"; onClicked: fenetre.mode = "fenetre" }
                Choix { text: "Écran entier"; trace: "M4 5h16v11H4z M9 20h6 M12 16v4"; actif: fenetre.mode === "ecran"; onClicked: fenetre.mode = "ecran" }
                Rectangle { Layout.preferredWidth: 1; Layout.preferredHeight: 28; Layout.leftMargin: 4; Layout.rightMargin: 4; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16) }
                QQC2.AbstractButton {
                    id: video
                    implicitHeight: 44
                    implicitWidth: ligneVideo.implicitWidth + 24
                    hoverEnabled: true
                    onClicked: fenetre.rendre({ mode: "video", cadre: fenetre.mode, delai: fenetre.delai })
                    background: Rectangle { radius: 14; color: video.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent" }
                    contentItem: Item {
                        Row {
                            id: ligneVideo
                            anchors.centerIn: parent
                            spacing: 8
                            Picto { anchors.verticalCenter: parent.verticalCenter; trace: "M12 6a6 6 0 1 0 0 12a6 6 0 1 0 0-12z"; encre: "#A3322A"; plein: true }
                            Text { anchors.verticalCenter: parent.verticalCenter; text: "Enregistrer une vidéo"; font.pixelSize: 13; font.weight: Font.Medium; color: "#1F1C18" }
                        }
                    }
                }
                Rectangle { Layout.preferredWidth: 1; Layout.preferredHeight: 28; Layout.leftMargin: 4; Layout.rightMargin: 4; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16) }
                // Minuteur : aucun, 3, 5 ou 10 secondes (un clic passe au suivant)
                QQC2.AbstractButton {
                    id: minuteur
                    implicitHeight: 36
                    implicitWidth: ligneMinuteur.implicitWidth + 24
                    hoverEnabled: true
                    onClicked: fenetre.delai = fenetre.delais[(fenetre.delais.indexOf(fenetre.delai) + 1) % fenetre.delais.length]
                    background: Rectangle { radius: 12; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, minuteur.hovered ? 0.09 : 0.05) }
                    contentItem: Item {
                        Row {
                            id: ligneMinuteur
                            anchors.centerIn: parent
                            spacing: 7
                            Picto { anchors.verticalCenter: parent.verticalCenter; width: 16; height: 16; trace: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7v5l3 2"; encre: "#665E54" }
                            Text { anchors.verticalCenter: parent.verticalCenter; text: "Minuteur · " + (fenetre.delai ? fenetre.delai + " s" : "aucun"); font.pixelSize: 13; color: "#1F1C18" }
                            Picto { anchors.verticalCenter: parent.verticalCenter; width: 14; height: 14; trace: "M6 9l6 6l6-6"; encre: "#8A8277" }
                        }
                    }
                }
                Item { Layout.preferredWidth: 6 }
                QQC2.AbstractButton {
                    id: boutonCapturer
                    implicitHeight: 44
                    implicitWidth: libelleCapturer.implicitWidth + 44
                    enabled: fenetre.mode !== "zone" || fenetre.zoneTracee
                    hoverEnabled: true
                    onClicked: fenetre.capturer()
                    background: Rectangle { radius: 22; color: "#B5532F"; opacity: !boutonCapturer.enabled ? 0.5 : boutonCapturer.down ? 0.85 : (boutonCapturer.hovered ? 0.93 : 1) }
                    contentItem: Text { id: libelleCapturer; text: "Capturer"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#FFFFFF" }
                }
                QQC2.AbstractButton {
                    id: fermer
                    implicitWidth: 40
                    implicitHeight: 40
                    hoverEnabled: true
                    onClicked: fenetre.rendre({})
                    background: Rectangle { radius: 20; color: fermer.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06) : "transparent" }
                    contentItem: Item { Picto { anchors.centerIn: parent; width: 16; height: 16; trace: "M7 7l10 10 M17 7L7 17"; encre: "#665E54" } }
                }
            }
        }
    }

    Shortcut { sequence: "Escape"; enabled: !fenetre.decoupe; onActivated: fenetre.rendre({}) }
    Shortcut { sequences: ["Return", "Enter"]; enabled: !fenetre.decoupe; onActivated: fenetre.capturer() }
}
