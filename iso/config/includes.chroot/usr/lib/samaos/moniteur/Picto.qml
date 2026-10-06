// Pictogramme au trait (tracé SVG 24 × 24).
import QtQuick
import "../reglages"

Canvas {
    property string trace
    property color encre: Couleurs.texte2
    property real trait: 1.7
    width: 16
    height: 16
    onTraceChanged: requestPaint()
    onEncreChanged: requestPaint()
    onPaint: {
        var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
        c.strokeStyle = encre; c.lineWidth = trait; c.lineCap = "round"; c.lineJoin = "round"
        c.path = trace; c.stroke()
    }
}
