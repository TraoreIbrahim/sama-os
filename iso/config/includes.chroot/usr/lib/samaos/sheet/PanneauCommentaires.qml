// Le panneau des commentaires, à droite de la grille (le bouton Commentaires de l'en-tête l'ouvre) : tous ceux du
// classeur, feuille par feuille, dans l'ordre des cases ; un clic va à la case. Commenter la case courante, en haut.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: panneau
    readonly property var com: fenetre.commentaires
    readonly property var doc: fenetre.doc
    color: Couleurs.fond
    signal fermer()

    // (par feuille, puis ligne, puis colonne)
    readonly property var tries: com.liste.slice().sort(function (a, b) { return a.tab - b.tab || a.l - b.l || a.c - b.c })
    readonly property bool plusieursFeuilles: {
        for (var i = 1; i < tries.length; i++) if (tries[i].tab !== tries[0].tab) return true
        return false
    }
    function lieu(k) {
        var a = com.adresse(k)
        return plusieursFeuilles || k.tab !== doc.partie ? a + " · " + (doc.nomsParties[k.tab] || "") : a
    }
    function aller(k) {
        if (k.tab !== doc.partie) doc.allerPartie(k.tab)
        doc.allerA(com.adresse(k))
        doc.forceActiveFocus()
    }

    Rectangle { width: 0.5; height: parent.height; color: Couleurs.bord }

    ColumnLayout {
        anchors.fill: parent
        anchors.leftMargin: 1
        spacing: 0

        // ——— En-tête ———
        ColumnLayout {
            Layout.fillWidth: true
            Layout.topMargin: 14
            Layout.leftMargin: 18
            Layout.rightMargin: 12
            Layout.bottomMargin: 12
            spacing: 10
            RowLayout {
                Layout.fillWidth: true
                spacing: 8
                Text { text: "Commentaires"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte }
                Rectangle {
                    visible: panneau.com.liste.length > 0
                    Layout.preferredHeight: 18
                    Layout.preferredWidth: Math.max(18, compte.implicitWidth + 14)
                    radius: 9
                    color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08)
                    Text { id: compte; anchors.centerIn: parent; text: panneau.com.liste.length; font.pixelSize: 12; font.weight: Font.DemiBold; color: Couleurs.texte }
                }
                Item { Layout.fillWidth: true }
                Outil {
                    Layout.preferredWidth: 26
                    Layout.preferredHeight: 26
                    picto: "M7 7l10 10 M17 7L7 17"
                    aide: "Fermer le panneau"
                    onClicked: panneau.fermer()
                }
            }
            Outil {
                Layout.fillWidth: true
                text: panneau.com.courant ? "Modifier le commentaire de " + panneau.doc.adresse.replace(/\$/g, "").split(":")[0]
                                          : "Commenter la case " + panneau.doc.adresse.replace(/\$/g, "").split(":")[0]
                picto: "M4 5h16v11H9l-5 4z M12 8v5 M9.5 10.5h5"
                aide: "Ctrl+Alt+M"
                background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12) }
                onClicked: panneau.com.demanderEcriture()
            }
        }

        // ——— La liste ———
        QQC2.ScrollView {
            id: defilement
            visible: panneau.tries.length > 0
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: availableWidth
            QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff
            ColumnLayout {
                width: defilement.availableWidth
                spacing: 10
                Repeater {
                    model: panneau.tries
                    delegate: ColumnLayout {
                        id: element
                        required property var modelData
                        required property int index
                        readonly property bool nouvelleFeuille: panneau.plusieursFeuilles && (index === 0 || panneau.tries[index - 1].tab !== modelData.tab)
                        Layout.fillWidth: true
                        Layout.leftMargin: 14
                        Layout.rightMargin: 14
                        spacing: 6
                        Text {
                            visible: element.nouvelleFeuille
                            Layout.topMargin: element.index === 0 ? 0 : 6
                            text: panneau.doc.nomsParties[element.modelData.tab] || ""
                            font.pixelSize: 12
                            font.weight: Font.DemiBold
                            color: Couleurs.texte2
                        }
                        CarteCommentaire {
                            Layout.fillWidth: true
                            Layout.preferredHeight: implicitHeight
                            k: element.modelData
                            lieu: panneau.lieu(element.modelData)
                            choisie: panneau.com.courant !== null && panneau.com.courant.id === element.modelData.id
                            actions: choisie
                            onClique: panneau.aller(element.modelData)
                            onModifier: panneau.com.demanderEcriture()
                            onResoudre: {
                                panneau.com.supprimer(element.modelData.id)
                                message.montrer("Commentaire retiré · Ctrl+Z pour le retrouver")
                                panneau.doc.forceActiveFocus()
                            }
                        }
                    }
                }
                Item { Layout.preferredHeight: 6 }
            }
        }

        // ——— Rien encore ———
        ColumnLayout {
            visible: panneau.tries.length === 0
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.leftMargin: 18
            Layout.rightMargin: 18
            spacing: 8
            Picto { Layout.topMargin: 24; Layout.preferredWidth: 28; Layout.preferredHeight: 28; trace: "M4 5h16v11H9l-5 4z"; encre: Couleurs.texte3 }
            Text {
                Layout.fillWidth: true
                text: "Pas encore de commentaire"
                font.pixelSize: 14
                font.weight: Font.DemiBold
                color: Couleurs.texte
            }
            Text {
                Layout.fillWidth: true
                text: "Une question sur un montant, un rappel pour qui ouvrira le fichier : choisissez la case, puis Commenter."
                font.pixelSize: 13
                lineHeight: 18
                lineHeightMode: Text.FixedHeight
                color: Couleurs.texte2
                wrapMode: Text.Wrap
            }
            Item { Layout.fillHeight: true }
        }

        // (où ils vont)
        Text {
            Layout.fillWidth: true
            Layout.leftMargin: 18
            Layout.rightMargin: 18
            Layout.topMargin: 8
            Layout.bottomMargin: 14
            text: "Les commentaires restent dans le fichier (Excel les montre aussi) ; ils ne partent pas avec l'image envoyée."
            font.pixelSize: 11
            lineHeight: 15
            lineHeightMode: Text.FixedHeight
            color: Couleurs.texte3
            wrapMode: Text.Wrap
        }
    }
}
