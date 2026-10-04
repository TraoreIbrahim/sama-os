// Curseur Sama : piste de 4 px, remplissage latérite, bouton blanc de 18 px. Valeur de 0 à 1.
import QtQuick

MouseArea {
    id: curseur
    property real valeur: 0
    signal deplace(real valeur)
    width: 170
    height: 20
    preventStealing: true
    function choisir(x) { curseur.deplace(Math.max(0, Math.min(1, (x - 9) / (width - 18)))) }
    onPressed: mouse => choisir(mouse.x)
    onPositionChanged: mouse => { if (pressed) choisir(mouse.x) }
    Rectangle { x: 0; y: 8; width: parent.width; height: 4; radius: 2; color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.15) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12) }
    Rectangle { x: 0; y: 8; width: 9 + (parent.width - 18) * curseur.valeur; height: 4; radius: 2; color: Couleurs.laterite }
    Rectangle {
        x: (parent.width - 18) * curseur.valeur
        y: 1
        width: 18
        height: 18
        radius: 9
        color: "#FFFFFF"
        border.width: 1
        border.color: Couleurs.bord
    }
}
