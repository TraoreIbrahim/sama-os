// Barre latérale de Fichiers (maquette fic-01) : favoris, appareils (disque interne, clés USB), Sama Grenier et Corbeille.
import QtQuick
import QtQuick.Layouts
import "../reglages"
import "Types.js" as Types

Rectangle {
    id: barre
    color: Couleurs.barreLaterale

    component Titre: Text {
        Layout.leftMargin: 12
        Layout.topMargin: 14
        Layout.bottomMargin: 4
        font.pixelSize: 11
        font.weight: Font.DemiBold
        font.letterSpacing: 0.4
        color: Couleurs.texte3
    }
    component Picto: Canvas {
        property string trace
        property color encre: Couleurs.texte2
        width: 16
        height: 16
        onEncreChanged: requestPaint()
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(16 / 24, 16 / 24)
            c.strokeStyle = encre; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }
    component Entree: MouseArea {
        id: entree
        property string nom
        property string picto
        property bool choisie: false
        property string pastille: ""
        property alias depot: zoneDepot.destination      // dossier (ou « corbeille ») qui reçoit ce qu'on lâche dessus
        default property alias fin: coin.data
        Layout.fillWidth: true
        Layout.preferredHeight: 34
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        Depot { id: zoneDepot; anchors.fill: parent }
        Rectangle {
            anchors.fill: parent
            radius: 9
            color: entree.choisie || zoneDepot.containsDrag ? Couleurs.selection : (entree.containsMouse ? Couleurs.carte : "transparent")
            border.width: zoneDepot.containsDrag ? 1.5 : 0
            border.color: Couleurs.laterite
        }
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 12
            anchors.rightMargin: 10
            spacing: 10
            Picto { trace: entree.picto; encre: entree.choisie ? Couleurs.lateriteEncre : Couleurs.texte2 }
            Text {
                Layout.fillWidth: true
                text: entree.nom
                elide: Text.ElideRight
                font.pixelSize: 13
                font.weight: entree.choisie ? Font.DemiBold : Font.Normal
                color: entree.choisie ? Couleurs.texte : Couleurs.texte
            }
            Text { visible: entree.pastille !== ""; text: entree.pastille; font.pixelSize: 11; color: Couleurs.texte3 }
            Row { id: coin; spacing: 4 }
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 10
        anchors.topMargin: 6
        spacing: 2

        Titre { text: "FAVORIS" }
        Repeater {
            model: fenetre.favoris
            delegate: Entree {
                nom: modelData.nom
                picto: modelData.picto
                choisie: fenetre.lieu === "dossier" && fenetre.dossier === modelData.chemin
                depot: modelData.chemin
                onClicked: fenetre.ouvrirDossier(modelData.chemin)
            }
        }

        Titre { text: "APPAREILS" }
        Repeater {
            model: fenetre.appareils
            delegate: ColumnLayout {
                id: appareil
                Layout.fillWidth: true
                spacing: 2
                readonly property bool externe: modelData.type === "externe"
                Entree {
                    nom: modelData.nom
                    picto: appareil.externe ? "M9 3h6v6H9z M7 9h10v8a4 4 0 0 1-4 4h-2a4 4 0 0 1-4-4z" : "M3 6h18v12H3z M7 14h.01"
                    choisie: fenetre.lieu === "dossier" && appareil.externe && modelData.montage !== "" && fenetre.dossier.indexOf(modelData.montage) === 0
                    depot: appareil.externe ? modelData.montage : ""      // (une clé montée reçoit des copies)
                    onClicked: {
                        if (!appareil.externe) { fenetre.ouvrirDossier("/"); return }
                        if (modelData.montage) fenetre.ouvrirDossier(modelData.montage)
                        else commande.lancer(fenetre.moteur + "monter " + commande.q(modelData.chemin), function (s) {
                            if (s.trim()) { fenetre.relireAppareils(); fenetre.ouvrirDossier(s.trim()) }
                        })
                    }
                    // Éjecter la clé (on peut ensuite la retirer)
                    MouseArea {
                        visible: appareil.externe
                        width: 22
                        height: 22
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: {
                            if (fenetre.dossier.indexOf(modelData.montage) === 0 && modelData.montage) fenetre.ouvrirDossier(fenetre.accueil)
                            commande.lancer(fenetre.moteur + "ejecter " + commande.q(modelData.chemin), function () { fenetre.relireAppareils() })
                        }
                        Rectangle { anchors.fill: parent; radius: 6; color: parent.containsMouse ? Couleurs.carte : "transparent" }
                        Picto { anchors.centerIn: parent; trace: "M12 6l6 7H6z M6 17h12"; encre: Couleurs.texte3 }
                    }
                }
                // Jauge : espace utilisé
                Rectangle {
                    visible: modelData.libre >= 0 && modelData.taille > 0
                    Layout.fillWidth: true
                    Layout.leftMargin: 12
                    Layout.rightMargin: 12
                    Layout.preferredHeight: 4
                    radius: 2
                    color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                    Rectangle {
                        width: parent.width * Math.min(1, 1 - modelData.libre / modelData.taille)
                        height: parent.height
                        radius: 2
                        color: modelData.libre / modelData.taille < 0.1 ? "#A3322A" : Couleurs.laterite
                    }
                }
                Text {
                    visible: modelData.libre >= 0 && modelData.taille > 0
                    Layout.leftMargin: 12
                    Layout.bottomMargin: 6
                    text: Types.taille(modelData.libre) + " libres sur " + Types.taille(modelData.taille)
                    font.pixelSize: 11
                    color: Couleurs.texte3
                }
            }
        }

        Titre { text: "Sama" }
        Entree {
            nom: "Sama Grenier"
            picto: "M7 18a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z"
            choisie: fenetre.lieu === "grenier"
            onClicked: { fenetre.lieu = "grenier"; fenetre.selection = [] }
        }
        Entree {
            nom: "Corbeille"
            picto: "M5 7h14 M10 7V5h4v2 M7 7l1 12a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2l1-12"
            pastille: fenetre.nombreCorbeille > 0 ? String(fenetre.nombreCorbeille) : ""
            choisie: fenetre.lieu === "corbeille"
            depot: "corbeille"
            onClicked: { fenetre.lieu = "corbeille"; fenetre.selection = [] }
        }
        Item { Layout.fillHeight: true }
    }
}
