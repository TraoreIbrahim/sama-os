// Les couleurs selon la valeur d'une colonne de tableau (dans le panneau du tableau) : « Moins que Seuil · toute la
// ligne » en rouge doux, « Contient « Riz » » en bleu. Une règle se compose en une ligne : la comparaison, une valeur ou
// une autre colonne de la même ligne, la teinte, la case ou toute la ligne. Gardées dans le .xlsx (Excel les montre) ;
// le × en retire une.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

ColumnLayout {
    id: couleurs
    property var t: null               // le tableau
    property var colonne: null         // { nom, nombre, genre… }
    property var colonnes: []          // les colonnes du tableau
    readonly property var tableaux: fenetre.tableaux
    spacing: 8

    // ——— Les règles du tableau, relues avec le panneau ———
    property var regles: []
    function lire() {
        if (!t) { regles = []; return }
        var nom = t.nom
        tableaux.appeler("SamaCouleurs.Regles", [nom], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "[" || !couleurs.t || couleurs.t.nom !== nom) return
            try { couleurs.regles = JSON.parse(v) } catch (e) { }
        })
    }
    onTChanged: lire()
    readonly property var reglesColonne: colonne ? regles.filter(function (r) { return r.colonne === colonne.nom }) : []
    onColonneChanged: if (colonne === null || (choix && choix.colonne !== colonne.nom)) choix = null

    readonly property var teintes: ({ rouge: ["#F6D7D2", "#8C2820"], ocre: ["#F8E8C4", "#6E4800"], vert: ["#DCEFE6", "#265C4A"], bleu: ["#DCE8EE", "#194B62"] })
    readonly property var operations: [["moins", "Moins que"], ["plus", "Plus que"], ["egal", "Égal à"], ["different", "Pas égal à"], ["contient", "Contient"]]
    function enMots(r) {
        var op = { moins: "Moins que", plus: "Plus que", egal: "Égal à", different: "Pas égal à", contient: "Contient" }[r.op] || r.op
        return op + " " + (r.colref ? r.colref : "« " + r.valeur + " »") + (r.ligne ? " · toute la ligne" : "")
    }

    // ——— Une règle en cours de composition : { colonne, op, valeur, colref, teinte, ligne } ———
    property var choix: null
    function commencer() {
        choix = { colonne: colonne.nom, op: colonne.nombre ? "moins" : "egal", valeur: "", colref: "", teinte: "rouge", ligne: false }
    }
    function changer(cle, v) { var c = Object.assign({}, choix); c[cle] = v; if (cle === "colref" && v) c.valeur = ""; choix = c }
    function ajouter() {
        var c = choix
        if (!c || (!c.valeur.trim() && !c.colref)) return
        tableaux.appeler("SamaCouleurs.Ajouter", [t.nom, c.colonne, c.op, c.valeur.trim(), c.colref, c.teinte, c.ligne ? "oui" : "non"], function (ok, v) {
            if (!tableaux.verifier(ok, v)) return
            couleurs.choix = null
            couleurs.lire()
            fenetre.relireRegles()
            fenetre.doc.redessiner()
        })
    }
    function retirer(r) {
        tableaux.appeler("SamaCouleurs.Retirer", [t.nom, String(r.id)], function (ok, v) {
            if (!tableaux.verifier(ok, v)) return
            couleurs.lire()
            fenetre.relireRegles()
            fenetre.doc.redessiner()
        })
    }

    Text { text: "Couleur selon la valeur"; font.pixelSize: 12; color: Couleurs.texte2 }

    // Les règles de la colonne
    Repeater {
        model: couleurs.reglesColonne
        delegate: RowLayout {
            id: regle
            required property var modelData
            Layout.fillWidth: true
            spacing: 8
            Rectangle {
                Layout.preferredWidth: 22
                Layout.preferredHeight: 18
                radius: 5
                color: (couleurs.teintes[regle.modelData.teinte] || couleurs.teintes.bleu)[0]
                Text { anchors.centerIn: parent; text: "A"; font.pixelSize: 11; font.weight: Font.DemiBold; color: (couleurs.teintes[regle.modelData.teinte] || couleurs.teintes.bleu)[1] }
            }
            Text { Layout.fillWidth: true; text: couleurs.enMots(regle.modelData); font.pixelSize: 13; color: Couleurs.texte; elide: Text.ElideRight }
            Outil {
                Layout.preferredWidth: 24
                Layout.preferredHeight: 24
                picto: "M8 8l8 8 M16 8l-8 8"
                aide: "Retirer cette couleur"
                onClicked: couleurs.retirer(regle.modelData)
            }
        }
    }

    // Une de plus
    QQC2.AbstractButton {
        visible: couleurs.choix === null
        focusPolicy: Qt.NoFocus
        hoverEnabled: true
        onClicked: couleurs.commencer()
        background: null
        contentItem: RowLayout {
            spacing: 6
            Picto { trace: "M12 5v14 M5 12h14"; encre: fenetre.accentEncre }
            Text { text: "Une couleur selon la valeur"; font.pixelSize: 13; font.weight: Font.DemiBold; font.underline: parent.parent.hovered; color: fenetre.accentEncre }
        }
    }

    // ——— Composer : la comparaison, à quoi, la teinte, la case ou la ligne ———
    Rectangle {
        visible: couleurs.choix !== null
        Layout.fillWidth: true
        Layout.preferredHeight: composition.implicitHeight + 20
        radius: 10
        color: Couleurs.champ
        border.width: 1
        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
        ColumnLayout {
            id: composition
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 10
            spacing: 8
            Text { text: couleurs.colonne ? "Quand " + couleurs.colonne.nom + " est…" : ""; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte2 }
            Flow {
                Layout.fillWidth: true
                spacing: 5
                Repeater {
                    model: couleurs.operations
                    delegate: PastilleRegle { texte: modelData[1]; choisie: couleurs.choix !== null && couleurs.choix.op === modelData[0]; onClicked: couleurs.changer("op", modelData[0]) }
                }
            }
            QQC2.TextField {
                id: valeur
                Layout.fillWidth: true
                Layout.preferredHeight: 30
                leftPadding: 8
                placeholderText: couleurs.choix && couleurs.choix.colref ? "" : "Une valeur : 5, 10 000, Espèces…"
                placeholderTextColor: Couleurs.texte3
                font.pixelSize: 13
                color: Couleurs.texte
                text: couleurs.choix ? couleurs.choix.valeur : ""
                onTextEdited: { if (couleurs.choix.colref) couleurs.changer("colref", ""); couleurs.changer("valeur", text) }
                onAccepted: couleurs.ajouter()
                background: Rectangle { radius: 7; color: Couleurs.fond; border.width: 1; border.color: valeur.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }
            }
            // (ou une autre colonne de la même ligne : le Stock comparé au Seuil)
            Flow {
                visible: couleurs.choix !== null && couleurs.choix.op !== "contient" && autres.count > 0
                Layout.fillWidth: true
                spacing: 5
                Text { text: "ou la colonne"; font.pixelSize: 12; color: Couleurs.texte2; height: 24; verticalAlignment: Text.AlignVCenter }
                Repeater {
                    id: autres
                    model: couleurs.colonne ? couleurs.colonnes.filter(function (c) { return c.nom !== couleurs.colonne.nom && (!couleurs.colonne.nombre || c.nombre) }) : []
                    delegate: PastilleRegle { texte: modelData.nom; choisie: couleurs.choix !== null && couleurs.choix.colref === modelData.nom; onClicked: couleurs.changer("colref", choisie ? "" : modelData.nom) }
                }
            }
            RowLayout {
                Layout.fillWidth: true
                spacing: 6
                Text { text: "En"; font.pixelSize: 12; color: Couleurs.texte2 }
                Repeater {
                    model: ["rouge", "ocre", "vert", "bleu"]
                    delegate: QQC2.AbstractButton {
                        id: teinte
                        readonly property bool choisie: couleurs.choix !== null && couleurs.choix.teinte === modelData
                        implicitWidth: 30
                        implicitHeight: 24
                        focusPolicy: Qt.NoFocus
                        Accessible.name: modelData
                        onClicked: couleurs.changer("teinte", modelData)
                        background: Rectangle {
                            radius: 6
                            color: couleurs.teintes[modelData][0]
                            border.width: teinte.choisie ? 2 : 0
                            border.color: couleurs.teintes[modelData][1]
                            Text { anchors.centerIn: parent; text: "A"; font.pixelSize: 11; font.weight: Font.DemiBold; color: couleurs.teintes[modelData][1] }
                        }
                    }
                }
                Item { Layout.fillWidth: true }
            }
            RowLayout {
                Layout.fillWidth: true
                spacing: 8
                PastilleRegle { texte: "La case"; choisie: couleurs.choix !== null && !couleurs.choix.ligne; onClicked: couleurs.changer("ligne", false) }
                PastilleRegle { texte: "Toute la ligne"; choisie: couleurs.choix !== null && couleurs.choix.ligne; onClicked: couleurs.changer("ligne", true) }
                Item { Layout.fillWidth: true }
            }
            RowLayout {
                Layout.fillWidth: true
                spacing: 8
                Item { Layout.fillWidth: true }
                Outil { text: "Annuler"; police.pixelSize: 12; onClicked: couleurs.choix = null }
                QQC2.AbstractButton {
                    id: ajouterRegle
                    enabled: couleurs.choix !== null && (couleurs.choix.valeur.trim() !== "" || couleurs.choix.colref !== "")
                    implicitHeight: 28
                    implicitWidth: texteAjouter.implicitWidth + 24
                    focusPolicy: Qt.NoFocus
                    hoverEnabled: true
                    opacity: enabled ? 1 : 0.45
                    onClicked: couleurs.ajouter()
                    background: Rectangle { radius: 8; color: ajouterRegle.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent }
                    contentItem: Text { id: texteAjouter; text: "Ajouter"; font.pixelSize: 12; font.weight: Font.DemiBold; color: "#FFFFFF"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                }
            }
        }
    }

    // (une pastille de choix)
    component PastilleRegle: QQC2.AbstractButton {
        id: pastille
        property string texte: ""
        property bool choisie: false
        height: 24
        implicitHeight: 24
        implicitWidth: textePastille.implicitWidth + 16
        width: implicitWidth
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        background: Rectangle {
            radius: 7
            color: pastille.choisie ? fenetre.accentFond : pastille.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : "transparent"
            border.width: pastille.choisie ? 0 : 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
        }
        contentItem: Text {
            id: textePastille
            text: pastille.texte
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            font.pixelSize: 12
            font.weight: pastille.choisie ? Font.DemiBold : Font.Normal
            color: pastille.choisie ? fenetre.accentEncre : Couleurs.texte
        }
    }
}
