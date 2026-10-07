// Résumer un tableau (le bouton Résumer du panneau du tableau, Données › Résumer le tableau) : une ligne par valeur
// d'une colonne (chaque article ; chaque mois pour une date), la somme, le nombre de lignes ou la moyenne d'une autre,
// séparée en colonnes par une troisième si l'on veut (Entrée, Sortie, et le Total). Sama crée une feuille « Résumé »
// (macro SamaResume) et y va ; ses nombres suivent le tableau, ses lignes aussi (Sama les refait en y revenant).
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: resumer
    readonly property var tableaux: fenetre.tableaux
    property var t: null
    property var colonnes: []
    property bool ouvert: false
    // Le résumé voulu
    property string par: ""
    property string calcul: "somme"
    property string valeur: ""
    property string separer: ""

    function ouvrir(tableau) {
        if (!tableau) return
        t = tableau
        colonnes = []
        tableaux.appeler("Colonnes", [t.nom], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{") return
            try { resumer.colonnes = JSON.parse(v).colonnes } catch (e) { return }
            // (pour commencer : la première colonne de texte, le premier nombre)
            var p = resumer.categories, n = resumer.nombres
            resumer.par = p.length ? p[0].nom : ""
            resumer.valeur = n.length ? n[0].nom : ""
            resumer.calcul = n.length ? "somme" : "nombre"
            resumer.separer = ""
        })
        ouvert = true
    }
    // (une ligne par : un texte, une liste de choix, une date ; de : un nombre)
    readonly property var categories: colonnes.filter(function (c) { return !c.nombre || c.genre === "date" })
    readonly property var nombres: colonnes.filter(function (c) { return c.nombre && c.genre !== "date" })
    readonly property var separations: colonnes.filter(function (c) { return (!c.nombre || c.genre === "date") && c.nom !== resumer.par })
    readonly property string phrase: {
        if (!par) return ""
        var col = colonnes.filter(function (c) { return c.nom === par })[0]
        var s = "Une ligne par " + (col && col.genre === "date" ? "mois (" + par + ")" : par) + " : "
        s += calcul === "nombre" ? "le nombre de lignes" : (calcul === "moyenne" ? "la moyenne de " : "la somme de ") + (calcul === "nombre" ? "" : valeur)
        if (separer) s += ", en colonnes selon " + separer + ", et le total"
        return s + "."
    }
    readonly property bool possible: par !== "" && (calcul === "nombre" || valeur !== "")
    function creer() {
        if (!possible) return
        var nom = t.nom, avant = fenetre.doc.nomsParties.length
        ouvert = false
        tableaux.appeler("SamaResume.Creer", [nom, par, calcul, calcul === "nombre" ? "" : valeur, separer], function (ok, v) {
            if (!tableaux.verifier(ok, v)) return
            fenetre.allerFeuillePlusTard(avant)
            message.montrer("Résumé de " + nom.replace(/_/g, " ") + " · ses nombres suivent le tableau")
            tableaux.rafraichir()
        })
    }

    parent: fenetre.contentItem
    anchors.fill: parent
    z: 90
    visible: ouvert
    Shortcut { sequence: "Escape"; enabled: resumer.ouvert; onActivated: resumer.ouvert = false }

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.45 : 0.22)
        MouseArea { anchors.fill: parent; onClicked: resumer.ouvert = false }
    }
    Rectangle {
        anchors.centerIn: parent
        width: Math.min(parent.width - 60, 520)
        height: colonne.implicitHeight + 48
        radius: 18
        color: Couleurs.fond
        border.width: 1
        border.color: Couleurs.bord
        MouseArea { anchors.fill: parent }

        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 24
            spacing: 10

            Text {
                Layout.fillWidth: true
                text: resumer.t ? "Résumer « " + resumer.t.nom.replace(/_/g, " ") + " »" : ""
                font.pixelSize: 18
                font.weight: Font.DemiBold
                color: Couleurs.texte
                elide: Text.ElideRight
            }
            Text {
                Layout.fillWidth: true
                text: "Comme un tableau croisé d'Excel, en plus simple : sur une nouvelle feuille, des formules qui suivent le tableau."
                font.pixelSize: 13
                lineHeight: 18
                lineHeightMode: Text.FixedHeight
                color: Couleurs.texte2
                wrapMode: Text.Wrap
            }

            Etiquette { text: "Une ligne par" }
            Flow {
                Layout.fillWidth: true
                spacing: 6
                Repeater {
                    model: resumer.categories
                    delegate: Choix { texte: modelData.nom + (modelData.genre === "date" ? " (par mois)" : ""); choisi: resumer.par === modelData.nom; onClicked: { resumer.par = modelData.nom; if (resumer.separer === modelData.nom) resumer.separer = "" } }
                }
                Text { visible: resumer.categories.length === 0; text: "Il faut une colonne de texte ou de dates."; font.pixelSize: 13; color: Couleurs.texte3 }
            }

            Etiquette { text: "Ce qu'on calcule" }
            Flow {
                Layout.fillWidth: true
                spacing: 6
                Choix { texte: "La somme"; visible: resumer.nombres.length > 0; choisi: resumer.calcul === "somme"; onClicked: resumer.calcul = "somme" }
                Choix { texte: "Le nombre de lignes"; choisi: resumer.calcul === "nombre"; onClicked: resumer.calcul = "nombre" }
                Choix { texte: "La moyenne"; visible: resumer.nombres.length > 0; choisi: resumer.calcul === "moyenne"; onClicked: resumer.calcul = "moyenne" }
            }
            Flow {
                visible: resumer.calcul !== "nombre"
                Layout.fillWidth: true
                spacing: 6
                Text { text: "de"; font.pixelSize: 13; color: Couleurs.texte2; height: 28; verticalAlignment: Text.AlignVCenter }
                Repeater {
                    model: resumer.nombres
                    delegate: Choix { texte: modelData.nom; choisi: resumer.valeur === modelData.nom; onClicked: resumer.valeur = modelData.nom }
                }
            }

            Etiquette { text: "En colonnes selon (facultatif)" }
            Flow {
                Layout.fillWidth: true
                spacing: 6
                Choix { texte: "Rien"; choisi: resumer.separer === ""; onClicked: resumer.separer = "" }
                Repeater {
                    model: resumer.separations
                    delegate: Choix { texte: modelData.nom; choisi: resumer.separer === modelData.nom; onClicked: resumer.separer = modelData.nom }
                }
            }

            Rectangle {
                visible: resumer.phrase !== ""
                Layout.fillWidth: true
                Layout.topMargin: 4
                Layout.preferredHeight: textePhrase.implicitHeight + 18
                radius: 9
                color: fenetre.accentFond
                Text {
                    id: textePhrase
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.margins: 12
                    text: resumer.phrase
                    font.pixelSize: 13
                    color: fenetre.accentEncre
                    wrapMode: Text.Wrap
                }
            }

            RowLayout {
                Layout.fillWidth: true
                Layout.topMargin: 8
                spacing: 8
                Item { Layout.fillWidth: true }
                Outil { text: "Annuler"; onClicked: resumer.ouvert = false }
                QQC2.AbstractButton {
                    id: creer
                    enabled: resumer.possible
                    implicitHeight: 32
                    implicitWidth: texteCreer.implicitWidth + 28
                    focusPolicy: Qt.NoFocus
                    hoverEnabled: true
                    opacity: enabled ? 1 : 0.45
                    onClicked: resumer.creer()
                    background: Rectangle { radius: 9; color: creer.down ? Qt.darker(fenetre.accent, 1.2) : creer.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent }
                    contentItem: Text { id: texteCreer; text: "Créer le résumé"; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#FFFFFF"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                }
            }
        }
    }

    component Etiquette: Text {
        Layout.topMargin: 6
        font.pixelSize: 12
        font.weight: Font.DemiBold
        color: Couleurs.texte2
    }
    component Choix: QQC2.AbstractButton {
        id: choix
        property string texte: ""
        property bool choisi: false
        implicitHeight: 28
        implicitWidth: texteChoix.implicitWidth + 20
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        background: Rectangle {
            radius: 8
            color: choix.choisi ? fenetre.accentFond : choix.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : Couleurs.champ
            border.width: choix.choisi ? 1.5 : 1
            border.color: choix.choisi ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
        }
        contentItem: Text {
            id: texteChoix
            text: choix.texte
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            font.pixelSize: 13
            font.weight: choix.choisi ? Font.DemiBold : Font.Normal
            color: choix.choisi ? fenetre.accentEncre : Couleurs.texte
        }
    }
}
