// Fenêtres côte à côte (maquette bur-05) : pendant qu'on déplace une fenêtre, Sama montre en haut de l'écran
// « Glissez une fenêtre vers un bord pour la ranger » et les dispositions (moitiés, tiers, deux tiers, quarts) ; la
// fenêtre lâchée sur une case prend cette place, à 12 px de ses voisines. Les bords de l'écran gardent le rangement de
// KWin (Super + ← →). Entre deux fenêtres côte à côte, une poignée commune les redimensionne ensemble.
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
    // Rectangle d'une case : 12 px au bord de l'écran, 12 px entre deux fenêtres (6 de chaque côté)
    readonly property int ecart: 12
    function rectangleDe(c) {
        var z = zoneUtile()
        var g = Math.round(z.x + c[0] * z.width), d = Math.round(z.x + (c[0] + c[2]) * z.width)
        var h = Math.round(z.y + c[1] * z.height), b = Math.round(z.y + (c[1] + c[3]) * z.height)
        g += c[0] === 0 ? ecart : ecart / 2
        d -= c[0] + c[2] >= 0.999 ? ecart : ecart / 2
        h += c[1] === 0 ? ecart : ecart / 2
        b -= c[1] + c[3] >= 0.999 ? ecart : ecart / 2
        return Qt.rect(g, h, d - g, b - h)
    }

    // Chaque fenêtre : début, avancée et fin d'un déplacement à la souris
    function brancher(w) {
        // (les poignées sont des fenêtres de KWin ordinaires : ni barre des tâches, ni Alt+Tab, ni Espaces)
        if (w.caption === "samaos-poignee") {
            w.skipTaskbar = true; w.skipSwitcher = true; w.skipPager = true
            return
        }
        w.interactiveMoveResizeStarted.connect(function () { racine.debut(w) })
        w.interactiveMoveResizeStepped.connect(function () { if (racine.fenetreDeplacee === w) racine.suivre(Workspace.cursorPos) })
        w.interactiveMoveResizeFinished.connect(function () { if (racine.fenetreDeplacee === w) racine.fin() })
        w.frameGeometryChanged.connect(function () { racine.revoir() })
        w.minimizedChanged.connect(function () { racine.revoir(); attenteFin.restart() })
    }
    Component.onCompleted: {
        var l = Workspace.windows
        for (var i = 0; i < l.length; i++) brancher(l[i])
        Workspace.windowAdded.connect(function (w) { racine.brancher(w); racine.revoir() })
        Workspace.windowRemoved.connect(function () { racine.revoir(); attenteFin.restart() })
        Workspace.windowActivated.connect(function () { racine.revoir() })
        Workspace.currentDesktopChanged.connect(function () { racine.revoir() })
        Workspace.currentActivityChanged.connect(function () { racine.revoir() })
        revoir()
    }

    // ——— Poignée commune entre deux fenêtres côte à côte ———
    // Deux fenêtres de l'Espace et du bureau courants, à 12 px l'une de l'autre (à 4 px près), qui se font face sur
    // au moins les trois quarts de leur hauteur. La poignée se cache si une autre fenêtre passe par-dessus.
    property var frontieres: []           // [{ gauche, droite, x, y }]
    property var glissement: null         // frontière en cours de glissement
    function revoir() { if (!glissement) attenteRevoir.restart() }
    Timer { id: attenteRevoir; interval: 60; onTriggered: racine.chercherFrontieres() }
    // (une fenêtre fermée ou réduite reste dans la pile le temps de son animation : on regarde de nouveau après)
    Timer { id: attenteFin; interval: 600; onTriggered: racine.revoir() }
    function montree(w) {
        if (!w.normalWindow || w.minimized || w.fullScreen || !w.resizeable || w.skipSwitcher || w.caption === "samaos-poignee") return false
        if (!w.onAllDesktops && w.desktops.indexOf(Workspace.currentDesktop) < 0) return false
        return w.activities.length === 0 || w.activities.indexOf(Workspace.currentActivity) >= 0
    }
    function chercherFrontieres() {
        var pile = Workspace.stackingOrder, liste = [], res = []
        for (var i = 0; i < pile.length; i++) if (montree(pile[i])) liste.push({ w: pile[i], rang: i })
        for (var a = 0; a < liste.length; a++) {
            for (var b = 0; b < liste.length; b++) {
                var A = liste[a].w.frameGeometry, B = liste[b].w.frameGeometry
                if (a === b || Math.abs(B.x - (A.x + A.width) - ecart) > 4) continue
                var haut = Math.max(A.y, B.y), bas = Math.min(A.y + A.height, B.y + B.height)
                if (bas - haut < 0.75 * Math.min(A.height, B.height)) continue
                var x = A.x + A.width + (B.x - A.x - A.width) / 2, y = (haut + bas) / 2
                // (une autre fenêtre, au-dessus des deux, recouvre l'endroit de la poignée ?)
                var cachee = false, dessus = Math.max(liste[a].rang, liste[b].rang)
                for (var k = dessus + 1; k < pile.length && !cachee; k++) {
                    var w = pile[k], r = w.frameGeometry
                    if (w.minimized || w.dock || r.width <= 20 && r.height <= 90) continue    // (la Natte, les poignées elles-mêmes)
                    cachee = x >= r.x && x <= r.x + r.width && y - 40 <= r.y + r.height && y + 40 >= r.y
                }
                if (!cachee) res.push({ gauche: liste[a].w, droite: liste[b].w, x: x, y: y })
            }
        }
        frontieres = res
    }

    // Glisser la poignée : la frontière suit la souris, chaque fenêtre garde au moins sa largeur minimale
    function glisser(f, xSouris) {
        var A = f.gauche.frameGeometry, B = f.droite.frameGeometry
        var mini = A.x + Math.max(f.gauche.minSize.width, 200) + ecart / 2
        var maxi = B.x + B.width - Math.max(f.droite.minSize.width, 200) - ecart / 2
        var x = Math.round(Math.max(mini, Math.min(maxi, xSouris)))
        f.gauche.frameGeometry = Qt.rect(A.x, A.y, x - ecart / 2 - A.x, A.height)
        f.droite.frameGeometry = Qt.rect(x + ecart / 2, B.y, B.x + B.width - x - ecart / 2, B.height)
        return x
    }
    // Trois poignées au plus (tiers), créées une fois pour toutes, cachées : une fenêtre de KWin créée visible ne se
    // dessine pas, et la recréer à chaque changement en laisserait d'anciennes à l'écran
    component Poignee: PlasmaCore.Dialog {
        id: poignee
        property int numero
        readonly property var frontiere: racine.frontieres.length > numero ? racine.frontieres[numero] : null
        property real centre: frontiere ? frontiere.x : 0
        onFrontiereChanged: if (frontiere && !racine.glissement) centre = frontiere.x
        x: Math.round(centre - 8)
        y: frontiere ? Math.round(frontiere.y - 40) : 0
        visible: false
        title: "samaos-poignee"
        flags: Qt.FramelessWindowHint | Qt.WindowDoesNotAcceptFocus
        backgroundHints: PlasmaCore.Types.NoBackground
        mainItem: MouseArea {
            width: 16
            height: 80
            hoverEnabled: true
            cursorShape: Qt.SplitHCursor
            onPressed: racine.glissement = poignee.frontiere
            onPositionChanged: if (pressed && racine.glissement) poignee.centre = racine.glisser(racine.glissement, Workspace.cursorPos.x)
            onReleased: { racine.glissement = null; racine.revoir() }
            Rectangle {
                anchors.centerIn: parent
                width: 6
                height: 56
                radius: 3
                color: "#FFFFFF"
                border.width: 0.5
                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, parent.containsMouse || parent.pressed ? 0.35 : 0.16)
                Column {
                    anchors.centerIn: parent
                    spacing: 4
                    Repeater { model: 3; Rectangle { width: 2; height: 2; radius: 1; color: "#8A8277" } }
                }
            }
        }
    }
    Poignee { id: poignee0; numero: 0 }
    Poignee { id: poignee1; numero: 1 }
    Poignee { id: poignee2; numero: 2 }
    function montrerPoignees() {
        var p = [poignee0, poignee1, poignee2]
        for (var i = 0; i < p.length; i++) p[i].visible = i < frontieres.length && fenetreDeplacee === null
    }
    onFrontieresChanged: montrerPoignees()
    onFenetreDeplaceeChanged: montrerPoignees()

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
        apercu.x = r.x; apercu.y = r.y
        zoneApercu.width = r.width; zoneApercu.height = r.height
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
