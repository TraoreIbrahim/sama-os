// Fenêtres côte à côte (maquette bur-05) : pendant qu'on déplace une fenêtre, Sama montre en haut de l'écran
// « Glissez une fenêtre vers un bord pour la ranger » et les dispositions (moitiés, tiers, deux tiers, quarts) ; la
// fenêtre lâchée sur une case prend cette place. Les bords de l'écran gardent le rangement de KWin (Super + ← →).
import QtQuick
import org.kde.plasma.core as PlasmaCore
import org.kde.kwin

Item {
    id: racine

    // Dispositions : chaque case en fractions de la zone utile de l'écran (x, y, largeur, hauteur)
    readonly property var dispositions: [
        { nom: "Moitiés", cases: [[0, 0, 1/2, 1], [1/2, 0, 1/2, 1]] },
        { nom: "Tiers", cases: [[0, 0, 1/3, 1], [1/3, 0, 1/3, 1], [2/3, 0, 1/3, 1]] },
        { nom: "Deux tiers", cases: [[0, 0, 2/3, 1], [2/3, 0, 1/3, 1]] },
        { nom: "Quarts", cases: [[0, 0, 1/2, 1/2], [1/2, 0, 1/2, 1/2], [0, 1/2, 1/2, 1/2], [1/2, 1/2, 1/2, 1/2]] }
    ]
    property var fenetreDeplacee: null
    property var caseVisee: null          // { disposition, case } sous le pointeur

    // Zone utile de l'écran, sans la Natte (KWin::MaximizeArea vaut 2 : l'énumération n'est pas offerte en QML)
    function zoneUtile() {
        return Workspace.clientArea(2, Workspace.activeScreen, Workspace.currentDesktop)
    }
    function rectangleDe(c) {
        var z = zoneUtile()
        return Qt.rect(Math.round(z.x + c[0] * z.width), Math.round(z.y + c[1] * z.height),
                       Math.round(c[2] * z.width), Math.round(c[3] * z.height))
    }

    // Chaque fenêtre : début, avancée et fin d'un déplacement à la souris
    function brancher(w) {
        w.interactiveMoveResizeStarted.connect(function () { racine.debut(w) })
        w.interactiveMoveResizeStepped.connect(function () { if (racine.fenetreDeplacee === w) racine.suivre(Workspace.cursorPos) })
        w.interactiveMoveResizeFinished.connect(function () { if (racine.fenetreDeplacee === w) racine.fin() })
    }
    Component.onCompleted: {
        var l = Workspace.windows
        for (var i = 0; i < l.length; i++) brancher(l[i])
        Workspace.windowAdded.connect(brancher)
    }
    function debut(w) {
        if (!w.move || !w.normalWindow || !w.resizeable || w.fullScreen) return
        fenetreDeplacee = w
        caseVisee = null
        var g = Workspace.activeScreen.geometry
        calque.x = g.x + Math.round((g.width - contenu.width) / 2)
        calque.y = g.y + 58
        calque.visible = true
    }
    function fin() {
        var w = fenetreDeplacee, c = caseVisee
        fenetreDeplacee = null
        calque.visible = false
        apercu.visible = false
        if (w && c) {
            // (une application qui ne peut pas être si étroite reste entière à l'écran, collée au même bord)
            var r = rectangleDe(c.zone), z = zoneUtile()
            var l = Math.max(r.width, w.minSize.width), h = Math.max(r.height, w.minSize.height)
            var x = Math.min(r.x, z.x + z.width - l), y = Math.min(r.y, z.y + z.height - h)
            w.frameGeometry = Qt.rect(x, y, l, h)
        }
        caseVisee = null
    }

    // Le pointeur au-dessus d'une case du choix ? (aperçu de la place que prendra la fenêtre)
    function suivre(p) {
        var trouve = null
        for (var i = 0; i < cartes.count; i++) {
            var carte = cartes.itemAt(i)
            for (var j = 0; j < carte.nombre; j++) {
                var r = carte.caseEcran(j)
                if (p.x >= r.x && p.x < r.x + r.width && p.y >= r.y && p.y < r.y + r.height)
                    trouve = { disposition: i, numero: j, zone: racine.dispositions[i].cases[j] }
            }
        }
        caseVisee = trouve
        if (!trouve) {
            apercu.visible = false
            return
        }
        var r = rectangleDe(trouve.zone)
        apercu.x = r.x + 8; apercu.y = r.y + 8
        zoneApercu.width = r.width - 16; zoneApercu.height = r.height - 16
        apercu.visible = true
    }

    // Aperçu de la place (le contour de KWin est effacé par KWin à chaque pas du déplacement)
    PlasmaCore.Dialog {
        id: apercu
        visible: false
        type: PlasmaCore.Dialog.OnScreenDisplay
        flags: Qt.X11BypassWindowManagerHint | Qt.FramelessWindowHint
        backgroundHints: PlasmaCore.Types.NoBackground
        outputOnly: true
        mainItem: Rectangle {
            id: zoneApercu
            radius: 16
            color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.16)
            border.width: 2
            border.color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.6)
        }
    }

    // Bulle et dispositions, en haut de l'écran (ne prennent ni la souris ni le clavier)
    PlasmaCore.Dialog {
        id: calque
        visible: false
        type: PlasmaCore.Dialog.OnScreenDisplay
        flags: Qt.X11BypassWindowManagerHint | Qt.FramelessWindowHint
        backgroundHints: PlasmaCore.Types.NoBackground
        outputOnly: true

        mainItem: Column {
            id: contenu
            spacing: 10

            // Bulle
            Rectangle {
                anchors.horizontalCenter: parent.horizontalCenter
                width: ligneBulle.implicitWidth + 32
                height: 36
                radius: 18
                color: "#1E2740"
                Row {
                    id: ligneBulle
                    anchors.centerIn: parent
                    spacing: 8
                    Canvas {
                        anchors.verticalCenter: parent.verticalCenter
                        width: 15; height: 15
                        onPaint: {
                            var c = getContext("2d"); c.reset(); c.scale(15 / 24, 15 / 24)
                            c.strokeStyle = "#F2C879"; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                            c.path = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8h.01"; c.stroke()
                        }
                    }
                    Text { anchors.verticalCenter: parent.verticalCenter; text: "Glissez une fenêtre vers un bord pour la ranger"; font.pixelSize: 13; color: "#F1EBE1" }
                }
            }

            // Dispositions
            Rectangle {
                anchors.horizontalCenter: parent.horizontalCenter
                width: choix.implicitWidth + 28
                height: choix.implicitHeight + 24
                radius: 18
                color: Qt.rgba(252 / 255, 250 / 255, 247 / 255, 0.96)
                border.width: 0.5
                border.color: Qt.rgba(70 / 255, 45 / 255, 20 / 255, 0.12)
                Column {
                    id: choix
                    anchors.centerIn: parent
                    spacing: 10
                    Item {
                        width: rangee.implicitWidth
                        height: 14
                        Text { text: "DISPOSITIONS"; font.pixelSize: 11; font.weight: Font.DemiBold; font.letterSpacing: 0.3; color: "#8A8277" }
                        Text { anchors.right: parent.right; text: "Super + ← →"; font.pixelSize: 11; color: "#8A8277" }
                    }
                    Row {
                        id: rangee
                        spacing: 10
                        Repeater {
                            id: cartes
                            model: racine.dispositions
                            delegate: Column {
                                id: carte
                                required property var modelData
                                required property int index
                                readonly property int nombre: modelData.cases.length
                                readonly property bool active: racine.caseVisee !== null && racine.caseVisee.disposition === index
                                spacing: 6
                                // Rectangle d'une case, à l'écran
                                function caseEcran(j) {
                                    var c = modelData.cases[j]
                                    var p = miniature.mapToGlobal(4 + c[0] * (miniature.width - 8), 4 + c[1] * (miniature.height - 8))
                                    return Qt.rect(p.x, p.y, c[2] * (miniature.width - 8), c[3] * (miniature.height - 8))
                                }
                                Rectangle {
                                    id: miniature
                                    width: 76; height: 50
                                    radius: 9
                                    color: carte.active ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.1) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
                                    border.width: carte.active ? 1.5 : 0
                                    border.color: "#B5532F"
                                    Repeater {
                                        model: carte.modelData.cases
                                        delegate: Rectangle {
                                            required property var modelData
                                            required property int index
                                            readonly property bool visee: carte.active && racine.caseVisee.numero === index
                                            x: 4 + modelData[0] * (miniature.width - 8) + 1.5
                                            y: 4 + modelData[1] * (miniature.height - 8) + 1.5
                                            width: modelData[2] * (miniature.width - 8) - 3
                                            height: modelData[3] * (miniature.height - 8) - 3
                                            radius: 4
                                            color: visee ? "#B5532F" : carte.active ? "#D99A7F" : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                                        }
                                    }
                                }
                                Text {
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    text: carte.modelData.nom
                                    font.pixelSize: 11
                                    font.weight: carte.active ? Font.DemiBold : Font.Normal
                                    color: carte.active ? "#93401F" : "#665E54"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
