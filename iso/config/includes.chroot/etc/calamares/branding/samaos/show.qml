/* Diaporama de l'installateur de Sama OS (affiché pendant la copie des fichiers).
 * Trois cartes simples sur fond nuit : les Espaces, l'économie de data, les langues.
 */
import QtQuick 2.0
import calamares.slideshow 1.0

Presentation {
    id: presentation

    Timer {
        interval: 12000
        running: presentation.activatedInCalamares
        repeat: true
        onTriggered: presentation.goToNextSlide()
    }

    function onActivate() { }
    function onLeave() { }

    component Carte: Slide {
        id: carte
        property string titre
        property string texte
        property color teinte: "#B5532F"

        Rectangle {
            anchors.fill: parent
            color: "#151A2B"
        }
        Image {
            id: logo
            source: "logo.png"
            width: 72
            height: 72
            fillMode: Image.PreserveAspectFit
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: titreTexte.top
            anchors.bottomMargin: 28
        }
        Rectangle {
            width: 40
            height: 4
            radius: 2
            color: carte.teinte
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: titreTexte.top
            anchors.bottomMargin: 12
        }
        Text {
            id: titreTexte
            text: carte.titre
            color: "#F1EBE1"
            font.pixelSize: 26
            font.weight: Font.Light
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.verticalCenter: parent.verticalCenter
            anchors.verticalCenterOffset: -10
        }
        Text {
            text: carte.texte
            color: "#ADA698"
            font.pixelSize: 15
            wrapMode: Text.WordWrap
            horizontalAlignment: Text.AlignHCenter
            width: Math.min(parent.width - 80, 560)
            lineHeight: 1.3
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: titreTexte.bottom
            anchors.topMargin: 16
        }
    }

    Carte {
        titre: "Bienvenue sur Sama OS"
        texte: "L'installation se poursuit toute seule et prend quelques minutes. Vous pouvez laisser l'ordinateur travailler."
    }
    Carte {
        titre: "Les Espaces"
        teinte: "#3D5A99"
        texte: "Travail, École, Maison : chaque Espace garde ses applications et ses fenêtres. Passez de l'un à l'autre depuis la Natte, en bas de l'écran."
    }
    Carte {
        titre: "Pensé pour votre connexion"
        teinte: "#2F6B57"
        texte: "Mises à jour la nuit, partage sur le réseau local, économie de data : Sama consomme le moins possible de votre forfait."
    }
    Carte {
        titre: "Dans votre langue"
        teinte: "#E2A62B"
        texte: "Français, English, Kiswahili, et bientôt dioula, wolof et haoussa. I ni ce !"
    }
}
