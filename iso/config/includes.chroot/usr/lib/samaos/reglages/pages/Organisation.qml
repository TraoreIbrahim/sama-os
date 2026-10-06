// Organisation (maquette reg-13) : un ordinateur peut être géré par une école, une entreprise ou une administration
// (Sama Parc, à venir). Tant qu'il ne l'est pas, la page le dit, montre ce qu'une organisation pourrait faire et ne
// pourrait jamais faire, et met en garde contre les faux informaticiens (l'inscription se fait sur place).
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Organisation"

    component Picto: Canvas {
        property string trace
        property color encre: Couleurs.texte2
        width: 18; height: 18
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(width / 24, height / 24)
            c.strokeStyle = encre; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }
    // Une liste « Elle pourrait » / « Elle ne pourrait jamais »
    component Colonne: ColumnLayout {
        property string titre
        property var elements: []
        property bool permis: true
        Layout.fillWidth: true
        Layout.alignment: Qt.AlignTop
        spacing: 10
        Text { text: titre; font.pixelSize: 12; font.weight: Font.DemiBold; color: permis ? Couleurs.texte2 : "#A3322A" }
        Repeater {
            model: elements
            delegate: RowLayout {
                required property string modelData
                Layout.fillWidth: true
                spacing: 10
                Picto {
                    Layout.alignment: Qt.AlignTop
                    width: 16; height: 16
                    encre: permis ? Couleurs.foret : "#A3322A"
                    trace: permis ? "M5 12.5l4.5 4.5L19 7.5" : "M7 7l10 10 M17 7L7 17"
                }
                Text { Layout.fillWidth: true; text: modelData; wrapMode: Text.WordWrap; font.pixelSize: 13; color: Couleurs.texte }
            }
        }
    }

    // ——— État ———
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: etat.implicitHeight + 36
        radius: 14
        color: Couleurs.carte
        RowLayout {
            id: etat
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            anchors.leftMargin: 18
            anchors.rightMargin: 18
            spacing: 14
            Rectangle {
                Layout.preferredWidth: 44
                Layout.preferredHeight: 44
                radius: 12
                color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.08) : "#F3ECE3"
                Picto { anchors.centerIn: parent; width: 22; height: 22; trace: "M4 20V8l8-4l8 4v12 M9 20v-6h6v6 M4 20h16" }
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 3
                Text { text: "Cet ordinateur n'est géré par aucune organisation"; font.pixelSize: 15; font.weight: Font.Medium; color: Couleurs.texte }
                Text {
                    Layout.fillWidth: true
                    wrapMode: Text.WordWrap
                    text: "C'est vous qui décidez : personne d'autre ne peut y installer d'applications, ni en changer les réglages."
                    font.pixelSize: 12
                    color: Couleurs.texte2
                }
            }
        }
    }

    // ——— Ce qu'une organisation pourrait faire ———
    Groupe {
        titre: "Si une école ou une entreprise gérait cet ordinateur"
        Item {
            Layout.fillWidth: true
            implicitHeight: colonnes.implicitHeight + 36
            RowLayout {
                id: colonnes
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.margins: 18
                spacing: 24
                Colonne {
                    titre: "Elle pourrait"
                    elements: ["Installer et retirer des applications", "Imposer les mises à jour du système",
                               "Régler le Wi-Fi et le mode salle de classe", "Voir le modèle et l'état de l'ordinateur"]
                }
                Colonne {
                    titre: "Elle ne pourrait jamais"
                    permis: false
                    elements: ["Lire vos fichiers personnels", "Voir votre historique de navigation",
                               "Lire vos messages", "Allumer la caméra ou le micro"]
                }
            }
        }
    }

    // ——— S'inscrire ———
    Groupe {
        titre: "Rejoindre une organisation"
        Ligne {
            titre: "Inscrire cet ordinateur"
            detail: "Avec le code que l'informaticien de votre école ou de votre entreprise vous remet sur place"
            derniere: true
            Pastille { text: "Bientôt"; teinte: Couleurs.texte3; encre: Couleurs.texte3 }
        }
    }

    // ——— Mise en garde ———
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: garde.implicitHeight + 32
        radius: 14
        color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, Couleurs.sombre ? 0.18 : 0.08)
        RowLayout {
            id: garde
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            anchors.leftMargin: 18
            anchors.rightMargin: 18
            spacing: 12
            Picto {
                Layout.alignment: Qt.AlignTop
                encre: Couleurs.lateriteEncre
                trace: "M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6z M12 8v5 M12 16h.01"
            }
            Text {
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                lineHeight: 1.2
                text: "Attention aux faux informaticiens : personne ne vous demandera d'inscrire cet ordinateur par téléphone, "
                      + "par message ou contre de l'argent. Une vraie organisation le fait sur place, avec vous."
                font.pixelSize: 13
                color: Couleurs.texte
            }
        }
    }
}
