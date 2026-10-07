// La liste de choix d'une colonne de tableau (dans le panneau du tableau) : aucune ; des choix écrits ici (ceux déjà
// dans la colonne pour commencer) ; ou la colonne d'un autre tableau (les articles du tableau Articles : la liste suit
// ce tableau quand il grandit). Une valeur hors de la liste est refusée (Sama le dit et rouvre la liste), ou acceptée.
// Dans la grille, un ▾ au bord de la case ; dans le formulaire, les choix. Ctrl+Z revient en arrière.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

ColumnLayout {
    id: reglage
    property var t: null               // le tableau
    property var colonne: null         // { nom, liste: null ou { genre, choix, tableau, colonne, refuser } }
    property int rang: -1              // la colonne dans le tableau (à partir de 0)
    signal change()
    readonly property var tableaux: fenetre.tableaux
    readonly property var liste: colonne ? colonne.liste || null : null
    // (la façon montrée : celle de la colonne, ou « tableau » le temps de choisir lequel)
    property string voulue: ""
    readonly property string facon: voulue !== "" ? voulue : !liste ? "aucune" : liste.genre === "tableau" ? "tableau" : "choix"
    onColonneChanged: voulue = ""
    spacing: 8

    function poser(genre, valeurs, refuser) {
        if (!t || !colonne) return
        var r = refuser === undefined ? (liste ? liste.refuser : true) : refuser
        tableaux.appeler("SamaListes.Poser", [t.nom, colonne.nom, genre, valeurs.join("\u001f"), r ? "oui" : "non"], function (ok, v) {
            if (tableaux.verifier(ok, v)) { reglage.voulue = ""; reglage.change() }
        })
    }
    function choisir(f) {
        if (f === facon) return
        if (f === "aucune") poser("aucune", [])
        else if (f === "choix") {
            // (pour commencer : ce qui est déjà écrit dans la colonne)
            tableaux.valeurs(t, rang, function (d) { reglage.poser("choix", (d.valeurs || []).filter(function (x) { return x !== "" }).slice(0, 40)) })
        } else voulue = "tableau"
    }
    // Les colonnes des autres tableaux du classeur
    readonly property var sources: {
        var r = []
        for (var i = 0; i < tableaux.liste.length; i++) {
            var x = tableaux.liste[i]
            if (t && x.nom === t.nom) continue
            for (var k = 0; k < (x.colonnes || []).length; k++) r.push({ tableau: x.nom, colonne: x.colonnes[k] })
        }
        return r
    }

    RowLayout {
        Layout.fillWidth: true
        Text { text: "Liste de choix"; font.pixelSize: 12; color: Couleurs.texte2 }
        Item { Layout.fillWidth: true }
        Text {
            text: !reglage.liste ? "Aucune" : reglage.liste.genre === "tableau" ? reglage.liste.tableau.replace(/_/g, " ") + " › " + reglage.liste.colonne
                  : reglage.liste.choix.length + (reglage.liste.choix.length > 1 ? " choix" : " choix")
            font.pixelSize: 12
            color: Couleurs.texte3
            elide: Text.ElideRight
            Layout.maximumWidth: reglage.width * 0.55
        }
    }
    // Les trois façons
    Flow {
        Layout.fillWidth: true
        spacing: 6
        Repeater {
            model: [["aucune", "Aucune"], ["choix", "Ces choix"], ["tableau", "D'un tableau"]]
            delegate: QQC2.AbstractButton {
                id: facon
                readonly property bool choisi: reglage.facon === modelData[0]
                height: 26
                width: texteFacon.implicitWidth + 18
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                onClicked: reglage.choisir(modelData[0])
                background: Rectangle {
                    radius: 7
                    color: facon.choisi ? fenetre.accentFond : facon.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : "transparent"
                    border.width: facon.choisi ? 0 : 1
                    border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                }
                contentItem: Text {
                    id: texteFacon
                    text: modelData[1]
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                    font.pixelSize: 12
                    font.weight: facon.choisi ? Font.DemiBold : Font.Normal
                    color: facon.choisi ? fenetre.accentEncre : Couleurs.texte
                }
            }
        }
    }

    // (une liste venue d'Excel, faite de cases du classeur : on la montre telle quelle)
    Text {
        visible: reglage.liste !== null && reglage.liste.genre === "autre"
        Layout.fillWidth: true
        text: reglage.liste ? "Les choix viennent de cases du classeur : " + reglage.liste.choix.join(", ") : ""
        font.pixelSize: 12
        lineHeight: 17
        lineHeightMode: Text.FixedHeight
        color: Couleurs.texte2
        wrapMode: Text.Wrap
        maximumLineCount: 4
        elide: Text.ElideRight
    }
    // ——— Ces choix : des étiquettes (× pour en retirer un), un champ pour en ajouter ———
    Flow {
        visible: reglage.facon === "choix" && reglage.liste !== null && reglage.liste.genre === "choix"
        Layout.fillWidth: true
        spacing: 6
        Repeater {
            model: reglage.liste && reglage.liste.genre === "choix" ? reglage.liste.choix : []
            delegate: Rectangle {
                id: etiquette
                required property string modelData
                required property int index
                height: 26
                width: mot.implicitWidth + croix.width + 18
                radius: 7
                color: Couleurs.champ
                border.width: 1
                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                Text { id: mot; x: 9; anchors.verticalCenter: parent.verticalCenter; text: etiquette.modelData; font.pixelSize: 12; color: Couleurs.texte }
                Outil {
                    id: croix
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    width: 22
                    height: 22
                    picto: "M8 8l8 8 M16 8l-8 8"
                    aide: "Retirer ce choix"
                    onClicked: {
                        var c = reglage.liste.choix.slice()
                        c.splice(etiquette.index, 1)
                        reglage.poser(c.length ? "choix" : "aucune", c)
                    }
                }
            }
        }
        QQC2.TextField {
            id: nouveau
            width: 140
            height: 26
            topPadding: 0
            bottomPadding: 0
            leftPadding: 8
            placeholderText: "Un choix de plus"
            placeholderTextColor: Couleurs.texte3
            font.pixelSize: 12
            color: Couleurs.texte
            background: Rectangle { radius: 7; color: Couleurs.fond; border.width: 1; border.color: nouveau.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }
            onAccepted: {
                var v = text.trim()
                if (!v) return
                var c = reglage.liste.choix.slice()
                if (c.indexOf(v) < 0) c.push(v)
                text = ""
                reglage.poser("choix", c)
            }
        }
    }

    // ——— D'un tableau : la colonne d'un autre tableau ———
    ColumnLayout {
        visible: reglage.facon === "tableau"
        Layout.fillWidth: true
        spacing: 2
        Text {
            visible: reglage.sources.length === 0
            Layout.fillWidth: true
            text: "Il n'y a pas d'autre tableau dans ce classeur. Faites d'abord le tableau des choix (vos articles, vos membres…) : Ctrl+T."
            font.pixelSize: 12
            lineHeight: 17
            lineHeightMode: Text.FixedHeight
            color: Couleurs.texte2
            wrapMode: Text.Wrap
        }
        Repeater {
            model: reglage.sources
            delegate: QQC2.AbstractButton {
                id: source
                required property var modelData
                readonly property bool choisie: reglage.liste !== null && reglage.liste.genre === "tableau"
                                                && reglage.liste.tableau === modelData.tableau && reglage.liste.colonne === modelData.colonne
                Layout.fillWidth: true
                implicitHeight: 30
                hoverEnabled: true
                focusPolicy: Qt.NoFocus
                onClicked: reglage.poser("tableau", [modelData.tableau, modelData.colonne])
                background: Rectangle {
                    radius: 7
                    color: source.choisie ? fenetre.accentFond : source.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                }
                contentItem: RowLayout {
                    spacing: 6
                    Text { leftPadding: 8; text: source.modelData.tableau.replace(/_/g, " "); font.pixelSize: 12; color: Couleurs.texte2 }
                    Text { text: "›"; font.pixelSize: 12; color: Couleurs.texte3 }
                    Text {
                        Layout.fillWidth: true
                        text: source.modelData.colonne
                        font.pixelSize: 13
                        font.weight: source.choisie ? Font.DemiBold : Font.Normal
                        color: source.choisie ? fenetre.accentEncre : Couleurs.texte
                        elide: Text.ElideRight
                    }
                    Picto { visible: source.choisie; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: fenetre.accentEncre; Layout.rightMargin: 8 }
                }
            }
        }
    }

    // ——— Refuser une autre valeur ———
    RowLayout {
        visible: reglage.liste !== null
        Layout.fillWidth: true
        spacing: 10
        Text {
            Layout.fillWidth: true
            text: "Refuser une valeur hors de la liste"
            font.pixelSize: 13
            color: Couleurs.texte
            wrapMode: Text.Wrap
        }
        QQC2.AbstractButton {
            id: refuser
            checkable: true
            checked: reglage.liste ? reglage.liste.refuser : true
            implicitWidth: 34
            implicitHeight: 20
            focusPolicy: Qt.NoFocus
            Accessible.name: "Refuser une valeur hors de la liste"
            onToggled: {
                var l = reglage.liste
                reglage.poser(l.genre === "tableau" ? "tableau" : "choix", l.genre === "tableau" ? [l.tableau, l.colonne] : l.choix, checked)
            }
            background: Rectangle {
                radius: 10
                color: refuser.checked ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.2)
                Behavior on color { ColorAnimation { duration: 120 } }
                Rectangle {
                    width: 14; height: 14; radius: 7
                    y: 3
                    x: refuser.checked ? parent.width - width - 3 : 3
                    color: "#FFFFFF"
                    Behavior on x { NumberAnimation { duration: 120 } }
                }
            }
        }
    }
}
