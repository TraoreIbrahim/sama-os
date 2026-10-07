// Barre du tableau où se trouve la case courante (comme l'onglet « Création de tableau » d'Excel, en plus simple) :
// son nom (qu'on change en cliquant dessus), sa place, ajouter une ligne, la ligne des totaux.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: barre
    readonly property var tableaux: fenetre.tableaux
    readonly property var t: fenetre.tableauVu
    readonly property bool enFiches: fenetre.vue === "fiches"
    readonly property bool enFormulaire: fenetre.vue === "formulaire" || fenetre.vue === "image"
    // (dans la grille, l'onglet posé au-dessus du tableau et son panneau la remplacent)
    readonly property bool utile: t !== null && (fenetre.vue !== "grille" || (!t.libre && !fenetre.panneauOuvert))
    implicitHeight: utile ? 38 : 0
    visible: utile
    color: Qt.rgba(31 / 255, 94 / 255, 122 / 255, Couleurs.sombre ? 0.16 : 0.07)
    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: Couleurs.bord }

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 14
        anchors.rightMargin: 14
        spacing: 10

        Rectangle {
            Layout.preferredHeight: 26
            Layout.preferredWidth: titre.implicitWidth + 20
            radius: 7
            color: fenetre.accent
            RowLayout {
                id: titre
                anchors.centerIn: parent
                spacing: 6
                Picto { width: 14; height: 14; trace: "M4 5h16v14H4z M4 10h16 M10 5v14"; encre: "#FFFFFF"; trait: 2 }
                Text { text: "Tableau"; font.pixelSize: 12; color: "#FFFFFF"; opacity: 0.85 }
            }
        }
        // Le nom : un clic pour le changer, Entrée pour le garder
        TextInput {
            id: nom
            text: barre.t ? barre.t.nom : ""
            font.pixelSize: 14
            font.weight: Font.DemiBold
            color: fenetre.accentEncre
            selectByMouse: true
            Layout.preferredWidth: Math.max(80, contentWidth + 4)
            Accessible.name: "Nom du tableau"
            onAccepted: { if (barre.t && text.trim() && text !== barre.t.nom) barre.tableaux.renommer(barre.t, text); fenetre.doc.forceActiveFocus() }
            Keys.onEscapePressed: { text = barre.t ? barre.t.nom : ""; fenetre.doc.forceActiveFocus() }
            onActiveFocusChanged: if (!activeFocus) text = Qt.binding(function () { return barre.t ? barre.t.nom : "" })
            Rectangle { anchors.fill: parent; anchors.margins: -4; radius: 6; z: -1; color: "transparent"; border.width: nom.activeFocus ? 1.5 : 0; border.color: fenetre.accent }
            QQC2.ToolTip.visible: zoneNom.containsMouse && !nom.activeFocus
            QQC2.ToolTip.text: "Cliquez pour renommer le tableau ; les formules s'en servent : " + (barre.t ? barre.t.nom : "") + "[Colonne]"
            QQC2.ToolTip.delay: 700
            MouseArea { id: zoneNom; anchors.fill: parent; hoverEnabled: true; acceptedButtons: Qt.NoButton; cursorShape: Qt.IBeamCursor }
        }
        Text {
            text: barre.t ? barre.tableaux.adresse(barre.t) + " · " + barre.tableaux.lignesDonnees(barre.t) + " lignes" : ""
            font.pixelSize: 12
            color: Couleurs.texte2
        }
        // Grille, fiches ou formulaire : le même tableau, vu autrement
        ChoixVues {
            modele: [["grille", "Grille", "M4 5h16v14H4z M4 10h16 M10 5v14"], ["fiches", "Fiches", "M4 5h7v6H4z M13 5h7v6h-7z M4 13h7v6H4z M13 13h7v6h-7z"],
                     ["formulaire", "Formulaire", "M5 3h14v18H5z M8 8h8 M8 12h8 M8 16h5"], ["image", "Image", "M4 5h16v14H4z M4 16l5-5 4 4 3-3 4 4 M15.5 8.5a1.5 1.5 0 1 0 0 .1"]]
            courant: fenetre.vue
            onChoisi: cle => fenetre.voir(cle, barre.t)
        }
        Separateur {}
        Outil {
            visible: !barre.enFormulaire
            text: barre.enFiches ? "Nouvelle fiche" : "Ajouter une ligne"
            picto: "M12 5v14 M5 12h14"
            aide: barre.enFiches ? "Une fiche de plus (une ligne du tableau)" : "Une ligne de plus à la fin du tableau, avec ses formules"
            onClicked: barre.enFiches ? fenetre.nouvelleFiche() : barre.tableaux.ajouterLigne(barre.t)
        }
        Outil {
            id: totaux
            visible: !barre.enFormulaire
            text: "Ligne des totaux"
            picto: barre.t && barre.t.totaux ? "M5 12.5l4.5 4.5L19 7.5" : "M4 5h16v14H4z"
            actif: barre.t ? barre.t.totaux : false
            aide: "Une ligne « Total » sous les données : la somme des colonnes de nombres, sans les lignes cachées par un filtre"
            onClicked: barre.tableaux.totaux(barre.t, !barre.t.totaux)
        }
        Item { Layout.fillWidth: true }
        Text {
            visible: fenetre.vue === "grille" || fenetre.vue === "image"
            text: fenetre.vue === "image" ? "Sama propose l'image d'après les colonnes ; vous pouvez en changer." : "Trier et filtrer : ▾ dans les titres"
            font.pixelSize: 12
            color: Couleurs.texte3
        }
    }
}
