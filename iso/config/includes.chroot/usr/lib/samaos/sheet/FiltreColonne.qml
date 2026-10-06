// Trier et filtrer une colonne d'un tableau (ouvert par le ▾ de son titre) : trier, puis cocher les valeurs à garder.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Volet {
    id: filtre
    property var tableau: null
    property int colonne: 0              // à partir de 0, dans le tableau
    property var valeurs: []             // toutes les valeurs de la colonne
    property var cochees: ({})           // valeur → gardée
    property bool charge: false
    property int revision: 0             // (pour redessiner les cases après un changement de « cochees »)
    readonly property bool filtree: tableau !== null && tableau.filtres.indexOf(colonne) >= 0
    readonly property var visibles: valeurs.filter(function (v) {
        return !recherche.text || v.toLowerCase().indexOf(recherche.text.toLowerCase()) >= 0
    })
    readonly property int nombreCochees: { revision; return valeurs.filter(function (v) { return cochees[v] }).length }
    titre: tableau ? tableau.colonnes[colonne] : ""
    width: 300

    function ouvrirPour(t, c, px, py) {
        x = px
        y = py
        tableau = t
        colonne = c
        charge = false
        valeurs = []
        recherche.text = ""
        open()
        fenetre.tableaux.valeurs(t, c, function (r) {
            var liste = r.valeurs.slice().sort(function (a, b) { return a.localeCompare(b, "fr", { numeric: true }) })
            var c2 = {}
            liste.forEach(function (v) { c2[v] = r.gardees === null || r.gardees.indexOf(v) >= 0 })
            cochees = c2
            valeurs = liste
            charge = true
            revision++
        })
    }
    function toutCocher(oui) {
        var c2 = {}
        valeurs.forEach(function (v) { c2[v] = oui })
        cochees = c2
        revision++
    }
    function appliquer() {
        var gardees = valeurs.filter(function (v) { return cochees[v] })
        fenetre.tableaux.filtrer(tableau, colonne, gardees.length === valeurs.length ? null : gardees)
        close()
        fenetre.doc.forceActiveFocus()
    }

    component Ligne: QQC2.AbstractButton {
        id: ligne
        property string picto
        Layout.fillWidth: true
        implicitHeight: 32
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        background: Rectangle { radius: 8; color: ligne.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06) : "transparent" }
        contentItem: RowLayout {
            spacing: 10
            Item { Layout.preferredWidth: 2 }
            Picto { trace: ligne.picto; encre: Couleurs.texte }
            Text { Layout.fillWidth: true; text: ligne.text; font.pixelSize: 13; color: Couleurs.texte; elide: Text.ElideRight }
        }
    }

    ColumnLayout {
        width: 272
        spacing: 2
        Ligne {
            text: "Trier de A à Z (croissant)"
            picto: "M7 4v16 M4 17l3 3 3-3 M13 6h3 M13 12h5 M13 18h7"
            onClicked: { fenetre.tableaux.trier(filtre.tableau, filtre.colonne, true); filtre.close(); fenetre.doc.forceActiveFocus() }
        }
        Ligne {
            text: "Trier de Z à A (décroissant)"
            picto: "M7 4v16 M4 17l3 3 3-3 M13 6h7 M13 12h5 M13 18h3"
            onClicked: { fenetre.tableaux.trier(filtre.tableau, filtre.colonne, false); filtre.close(); fenetre.doc.forceActiveFocus() }
        }
        Rectangle { Layout.fillWidth: true; Layout.topMargin: 6; Layout.bottomMargin: 6; height: 0.5; color: Couleurs.bord }
        Text { text: "Garder les lignes où « " + filtre.titre + " » vaut :"; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte2; Layout.bottomMargin: 4; Layout.fillWidth: true; elide: Text.ElideRight }

        // Chercher une valeur
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 32
            radius: 8
            color: Couleurs.champ
            border.width: recherche.activeFocus ? 1.5 : 0.5
            border.color: recherche.activeFocus ? fenetre.vert : Couleurs.bord
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 10
                anchors.rightMargin: 8
                spacing: 8
                Picto { width: 14; height: 14; trace: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4" }
                TextInput {
                    id: recherche
                    Layout.fillWidth: true
                    font.pixelSize: 13
                    color: Couleurs.texte
                    clip: true
                    Accessible.name: "Chercher une valeur"
                    Text { visible: !recherche.text; text: "Chercher une valeur"; font.pixelSize: 13; color: Couleurs.texte3 }
                }
            }
        }
        RowLayout {
            Layout.fillWidth: true
            Layout.topMargin: 4
            QQC2.AbstractButton {
                focusPolicy: Qt.NoFocus
                contentItem: Text { text: "Tout cocher"; font.pixelSize: 12; font.weight: Font.DemiBold; color: fenetre.vertEncre }
                onClicked: filtre.toutCocher(true)
            }
            Text { text: "·"; color: Couleurs.texte3 }
            QQC2.AbstractButton {
                focusPolicy: Qt.NoFocus
                contentItem: Text { text: "Tout décocher"; font.pixelSize: 12; font.weight: Font.DemiBold; color: fenetre.vertEncre }
                onClicked: filtre.toutCocher(false)
            }
            Item { Layout.fillWidth: true }
            Text { text: filtre.nombreCochees + " sur " + filtre.valeurs.length; font.pixelSize: 12; color: Couleurs.texte3 }
        }
        // Les valeurs, à cocher
        ListView {
            Layout.fillWidth: true
            Layout.preferredHeight: Math.min(contentHeight, 220)
            Layout.topMargin: 2
            clip: true
            model: filtre.visibles
            boundsBehavior: Flickable.StopAtBounds
            delegate: QQC2.AbstractButton {
                id: valeur
                width: ListView.view.width
                height: 30
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                readonly property bool cochee: { filtre.revision; return filtre.cochees[modelData] === true }
                onClicked: { filtre.cochees[modelData] = !cochee; filtre.revision++ }
                background: Rectangle { radius: 7; color: valeur.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent" }
                contentItem: RowLayout {
                    spacing: 10
                    Item { Layout.preferredWidth: 2 }
                    Rectangle {
                        width: 18; height: 18; radius: 5
                        color: valeur.cochee ? fenetre.vert : Couleurs.champ
                        border.width: valeur.cochee ? 0 : 1.5
                        border.color: Couleurs.texte3
                        Picto { anchors.centerIn: parent; width: 12; height: 12; visible: valeur.cochee; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: "#FFFFFF"; trait: 3 }
                    }
                    Text {
                        Layout.fillWidth: true
                        text: modelData === "" ? "(vide)" : modelData
                        font.pixelSize: 13
                        font.italic: modelData === ""
                        color: modelData === "" ? Couleurs.texte2 : Couleurs.texte
                        elide: Text.ElideRight
                    }
                }
            }
        }
        Text {
            visible: !filtre.charge
            text: "Lecture des valeurs…"
            font.pixelSize: 12
            color: Couleurs.texte3
        }
        RowLayout {
            Layout.fillWidth: true
            Layout.topMargin: 8
            spacing: 8
            Outil {
                visible: filtre.filtree
                text: "Enlever le filtre"
                encre: Couleurs.texte
                onClicked: { fenetre.tableaux.filtrer(filtre.tableau, filtre.colonne, null); filtre.close(); fenetre.doc.forceActiveFocus() }
            }
            Item { Layout.fillWidth: true }
            Outil {
                text: "Appliquer"
                enabled: filtre.charge && filtre.nombreCochees > 0
                encre: "#FFFFFF"
                background: Rectangle { radius: 8; color: fenetre.vert; opacity: parent.hovered ? 0.9 : 1 }
                onClicked: filtre.appliquer()
            }
        }
    }
}
