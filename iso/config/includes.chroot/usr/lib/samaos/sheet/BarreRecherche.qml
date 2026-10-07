// La barre de recherche (Ctrl+F) et de remplacement (Ctrl+H), sous la barre de formule : posée au-dessus de la
// grille, elle ne cache aucune case. Ce qu'on cherche, combien de cases (« 2 sur 9 »), la précédente et la suivante,
// les options ; Remplacer par, Remplacer, Tout remplacer. La recherche elle-même : RechercheCases, dans la grille.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: barre
    readonly property var r: grille.recherche
    visible: r.ouverte && fenetre.vue === "grille"
    implicitHeight: colonne.implicitHeight + 12
    color: Couleurs.sombre ? "#1B1F2E" : "#FFFFFF"
    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: Couleurs.bord }

    Connections {
        target: barre.r
        function onFocaliser() { champ.entree.forceActiveFocus(); champ.entree.selectAll() }
    }

    component Option: QQC2.AbstractButton {
        id: option
        property bool choisie: false
        property string aide: ""
        height: 26
        leftPadding: 9
        rightPadding: 9
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        QQC2.ToolTip.visible: hovered && aide !== ""
        QQC2.ToolTip.text: aide
        QQC2.ToolTip.delay: 500
        background: Rectangle {
            radius: 7
            color: option.choisie ? fenetre.accentFond : option.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
            border.width: option.choisie ? 0 : 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
        }
        contentItem: Text {
            text: option.text
            verticalAlignment: Text.AlignVCenter
            font.pixelSize: 12
            font.weight: option.choisie ? Font.DemiBold : Font.Normal
            color: option.choisie ? fenetre.accentEncre : Couleurs.texte2
        }
    }
    component Champ: Rectangle {
        id: cadreChamp
        property alias entree: saisie
        property string indication: ""
        signal edite(string t)
        signal valide(bool maj)
        signal echap()
        Layout.preferredWidth: 280
        Layout.preferredHeight: 30
        radius: 8
        color: Couleurs.champ
        border.width: saisie.activeFocus ? 1.5 : 1
        border.color: saisie.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
        TextInput {
            id: saisie
            anchors.fill: parent
            anchors.leftMargin: 10
            anchors.rightMargin: 10
            verticalAlignment: TextInput.AlignVCenter
            font.pixelSize: 14
            color: Couleurs.texte
            clip: true
            selectByMouse: true
            selectionColor: fenetre.accentFond
            selectedTextColor: Couleurs.texte
            onTextEdited: cadreChamp.edite(text)
            Keys.onPressed: e => {
                if (e.key === Qt.Key_Return || e.key === Qt.Key_Enter) { cadreChamp.valide((e.modifiers & Qt.ShiftModifier) !== 0); e.accepted = true }
                else if (e.key === Qt.Key_Escape) { cadreChamp.echap(); e.accepted = true }
            }
            Text { visible: !parent.text && !parent.preeditText; anchors.verticalCenter: parent.verticalCenter; text: cadreChamp.indication; font: parent.font; color: Couleurs.texte3 }
        }
    }

    ColumnLayout {
        id: colonne
        x: 14
        y: 6
        width: parent.width - 24
        spacing: 6
        RowLayout {
            Layout.fillWidth: true
            spacing: 6
            Picto { width: 16; height: 16; trace: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4"; encre: Couleurs.texte2 }
            Champ {
                id: champ
                Layout.leftMargin: 4
                indication: barre.r.toutes ? "Chercher dans le classeur" : "Chercher dans la feuille"
                onEdite: t => barre.r.texte = t
                onValide: maj => { if (maj) barre.r.precedent(); else barre.r.suivant() }
                onEchap: barre.r.fermer()
            }
            Text {
                Layout.minimumWidth: 78
                text: !barre.r.texte.trim() ? "" : barre.r.enCours && !barre.r.resultats.length ? "…"
                      : barre.r.total === 0 ? "Aucune case"
                      : (barre.r.courant + 1) + " sur " + barre.r.total
                font.pixelSize: 12
                font.features: { "tnum": 1 }
                color: Couleurs.texte2
            }
            Outil { Layout.preferredWidth: 28; Layout.preferredHeight: 28; picto: "M6 15l6-6 6 6"; aide: "Précédente (Maj+Entrée)"; enabled: barre.r.resultats.length > 0; onClicked: barre.r.precedent() }
            Outil { Layout.preferredWidth: 28; Layout.preferredHeight: 28; picto: "M6 9l6 6 6-6"; aide: "Suivante (Entrée)"; enabled: barre.r.resultats.length > 0; onClicked: barre.r.suivant() }
            Rectangle { Layout.preferredWidth: 1; Layout.preferredHeight: 18; Layout.leftMargin: 4; Layout.rightMargin: 4; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }
            Option { text: "Case entière"; choisie: barre.r.entiere; aide: "La case doit contenir exactement ce qu'on cherche"; onClicked: barre.r.entiere = !barre.r.entiere }
            Option { text: "Majuscules et accents"; choisie: barre.r.exact; aide: "Les respecter : « Kone » ne trouve plus « Koné »"; onClicked: barre.r.exact = !barre.r.exact }
            Option { text: "Formules"; choisie: barre.r.formules; aide: "Chercher dans les formules plutôt que dans ce qu'elles affichent"; onClicked: barre.r.formules = !barre.r.formules }
            Option { text: "Tout le classeur"; visible: fenetre.doc.nomsParties.length > 1; choisie: barre.r.toutes; aide: "Chercher dans toutes les feuilles"; onClicked: barre.r.toutes = !barre.r.toutes }
            Item { Layout.fillWidth: true }
            Outil {
                text: "Remplacer"
                picto: "M4 7h11 M12 4l3 3-3 3 M20 17H9 M12 14l-3 3 3 3"
                actif: barre.r.avecRemplacer
                aide: "Remplacer par autre chose (Ctrl+H)"
                onClicked: { barre.r.avecRemplacer = !barre.r.avecRemplacer; if (barre.r.avecRemplacer) champPar.entree.forceActiveFocus() }
            }
            Outil { Layout.preferredWidth: 28; Layout.preferredHeight: 28; picto: "M7 7l10 10 M17 7L7 17"; aide: "Fermer (Échap)"; onClicked: barre.r.fermer() }
        }
        // Remplacer
        RowLayout {
            visible: barre.r.avecRemplacer
            Layout.fillWidth: true
            Layout.bottomMargin: 2
            spacing: 6
            Item { Layout.preferredWidth: 16 }
            Champ {
                id: champPar
                Layout.leftMargin: 4
                indication: "Remplacer par"
                onEdite: t => barre.r.par = t
                onValide: maj => barre.r.remplacerIci()
                onEchap: barre.r.fermer()
            }
            Outil {
                text: "Remplacer"
                aide: "Cette case, puis la suivante (Entrée)"
                enabled: barre.r.courant >= 0
                background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }
                onClicked: barre.r.remplacerIci()
            }
            Outil {
                text: "Tout remplacer"
                enabled: barre.r.total > 0
                encre: fenetre.accentEncre
                background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.2) : fenetre.accentFond }
                onClicked: barre.r.remplacerTout()
            }
            Text {
                Layout.leftMargin: 6
                Layout.fillWidth: true
                text: barre.r.formules ? "Dans les formules aussi" : "Seulement dans les textes, jamais dans une formule"
                font.pixelSize: 12
                color: Couleurs.texte3
                elide: Text.ElideRight
            }
        }
    }
}
