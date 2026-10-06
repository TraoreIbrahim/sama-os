// Courbe des dernières mesures (la plus récente à droite), remplie en dégradé ; une seconde courbe facultative
// (ex. envoyé sous reçu). maxi : 0 = d'après les valeurs.
import QtQuick
import "../reglages"

Canvas {
    id: courbe
    property var valeurs: []
    property var valeurs2: []
    property real maxi: 0
    property int points: 60
    property color teinte: Couleurs.laterite
    property color teinte2: Couleurs.foret
    property real epaisseur: 1.6
    onValeursChanged: requestPaint()
    onValeurs2Changed: requestPaint()
    onWidthChanged: requestPaint()
    onHeightChanged: requestPaint()

    function tracer(c, liste, haut, couleur) {
        if (!liste || liste.length < 2) return
        var pas = width / (points - 1)
        var x0 = width - (liste.length - 1) * pas
        function y(v) { return height - 2 - (height - 4) * Math.min(1, v / haut) }
        c.beginPath()
        c.moveTo(x0, y(liste[0]))
        for (var i = 1; i < liste.length; i++) c.lineTo(x0 + i * pas, y(liste[i]))
        c.lineWidth = epaisseur
        c.strokeStyle = couleur
        c.lineJoin = "round"
        c.stroke()
        c.lineTo(width, height)
        c.lineTo(x0, height)
        c.closePath()
        var g = c.createLinearGradient(0, 0, 0, height)
        g.addColorStop(0, Qt.rgba(couleur.r, couleur.g, couleur.b, 0.22))
        g.addColorStop(1, Qt.rgba(couleur.r, couleur.g, couleur.b, 0))
        c.fillStyle = g
        c.fill()
    }
    onPaint: {
        var c = getContext("2d")
        c.reset()
        var haut = maxi
        if (!haut) {
            haut = 1
            for (var i = 0; i < valeurs.length; i++) haut = Math.max(haut, valeurs[i] * 1.2)
            for (var j = 0; j < valeurs2.length; j++) haut = Math.max(haut, valeurs2[j] * 1.2)
        }
        tracer(c, valeurs2, haut, teinte2)
        tracer(c, valeurs, haut, teinte)
    }
}
