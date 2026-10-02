// Indicateur de chargement de Sama OS : l'éléphant dont la trompe se balance.
// Composant réutilisable (Natte, Cour, installateur, futures applications Sama).
// Usage : ChargementSama { width: 48; height: 48; couleur: "#B5532F"; fond: "#F3ECE2" }

import QtQuick
import QtQuick.Shapes

Item {
    id: chargement

    property color couleur: "#B5532F"   // couleur de l'éléphant
    property color fond: "#F3ECE2"      // couleur derrière (sert à détacher la tête des oreilles)
    property bool actif: visible
    property int duree: 1800            // durée d'un aller-retour de la trompe, en ms

    implicitWidth: 48
    implicitHeight: 48

    // Dessin à l'échelle 100 × 100, comme le logo
    Item {
        anchors.centerIn: parent
        width: 100
        height: 100
        scale: Math.min(chargement.width, chargement.height) / 100

        Shape {
            anchors.fill: parent
            layer.enabled: true
            layer.samples: 4

            // Oreilles
            ShapePath {
                fillColor: chargement.couleur
                strokeColor: "transparent"
                PathAngleArc { centerX: 25; centerY: 40; radiusX: 21; radiusY: 21; startAngle: 0; sweepAngle: 360 }
            }
            ShapePath {
                fillColor: chargement.couleur
                strokeColor: "transparent"
                PathAngleArc { centerX: 75; centerY: 40; radiusX: 21; radiusY: 21; startAngle: 0; sweepAngle: 360 }
            }
            // Tête, détachée des oreilles par un trait de la couleur du fond
            ShapePath {
                fillColor: chargement.couleur
                strokeColor: chargement.fond
                strokeWidth: 4
                PathAngleArc { centerX: 50; centerY: 40; radiusX: 19; radiusY: 23; startAngle: 0; sweepAngle: 360 }
            }
            // Yeux
            ShapePath {
                fillColor: chargement.fond
                strokeColor: "transparent"
                PathAngleArc { centerX: 43; centerY: 36; radiusX: 2.6; radiusY: 2.6; startAngle: 0; sweepAngle: 360 }
            }
            ShapePath {
                fillColor: chargement.fond
                strokeColor: "transparent"
                PathAngleArc { centerX: 57; centerY: 36; radiusX: 2.6; radiusY: 2.6; startAngle: 0; sweepAngle: 360 }
            }
        }

        // Trompe : pivote autour de sa base (50, 58)
        Shape {
            id: trompe
            anchors.fill: parent
            layer.enabled: true
            layer.samples: 4
            transform: Rotation { id: rotation; origin.x: 50; origin.y: 58; angle: 0 }

            ShapePath {
                fillColor: "transparent"
                strokeColor: chargement.couleur
                strokeWidth: 10
                capStyle: ShapePath.RoundCap
                PathSvg { path: "M50 58 C50 70 63 66 63 77 C63 88 47 90 42 83" }
            }
        }

        SequentialAnimation {
            running: chargement.actif
            loops: Animation.Infinite
            NumberAnimation { target: rotation; property: "angle"; from: -9; to: 9; duration: chargement.duree / 2; easing.type: Easing.InOutSine }
            NumberAnimation { target: rotation; property: "angle"; from: 9; to: -9; duration: chargement.duree / 2; easing.type: Easing.InOutSine }
        }
    }
}
