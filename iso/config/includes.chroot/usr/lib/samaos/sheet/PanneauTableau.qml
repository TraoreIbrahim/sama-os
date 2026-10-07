// Le panneau du tableau (maquette « Sheet · Un tableau dans la grille »), à droite de la grille quand la case courante
// est dans un tableau : comme l'onglet « Création de tableau » d'Excel, en plus clair. Son nom (un clic pour le
// changer) ; la colonne de la case courante (son genre, sa formule en mots, comment l'afficher) ; le calcul de chaque
// colonne dans la ligne des totaux ; le style ; convertir en cases ordinaires.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtQml.Models
import "../reglages"
import "Fonctions.js" as Fonctions

Rectangle {
    id: panneau
    readonly property var tableaux: fenetre.tableaux
    readonly property var t: tableaux.courant
    readonly property var doc: fenetre.doc
    color: Couleurs.fond
    signal fermer()

    // ——— Les colonnes du tableau (genre, formule, calcul du total), relues après chaque changement ———
    property var colonnes: []
    function lire() {
        if (!t || !visible) return
        var nom = t.nom
        tableaux.appeler("Colonnes", [nom], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{" || !panneau.t || panneau.t.nom !== nom) return
            try { colonnes = JSON.parse(v).colonnes } catch (e) { }
        })
    }
    Timer { id: relire; interval: 300; onTriggered: panneau.lire() }
    Connections {
        target: panneau.doc
        function onRevisionChanged() { if (panneau.visible) relire.restart() }
    }
    readonly property string signature: t ? t.nom + ":" + t.c1 + ":" + t.l1 + ":" + t.c2 + ":" + t.l2 + ":" + t.totaux : ""
    onSignatureChanged: { colonnes = []; relire.restart() }
    onVisibleChanged: if (visible) relire.restart()

    // La colonne de la case courante (rang dans le tableau), et la colonne « reste » d'un calcul A − B
    readonly property int rang: t ? doc.colonne - t.c1 : -1
    readonly property var colonne: rang >= 0 && rang < colonnes.length ? colonnes[rang] : null
    readonly property var progression: Fonctions.progression(colonnes)
    // Ce que la colonne contient, en mots
    function genreEnMots(c) {
        if (!c) return ""
        var g = { montant: "montant en F", date: "date", heure: "heure", pourcent: "pourcentage", texte: "texte" }[c.genre] || (c.nombre ? "nombre" : "")
        if (c.calcul) return "Calcul" + (g ? " · " + g : "")
        if (/t[ée]l[ée]?phone|^t[ée]l\b|portable|whatsapp/i.test(c.nom)) return "Téléphone"
        return g ? g.charAt(0).toUpperCase() + g.slice(1) : "Texte"
    }
    readonly property var nomsCalculs: ({ somme: "Somme", moyenne: "Moyenne", nombre: "Nombre", min: "Minimum", max: "Maximum", aucun: "Aucun", autre: "Formule", texte: "Texte" })

    // ——— Petites pièces ———
    component Section: Text {
        font.pixelSize: 13
        font.weight: Font.DemiBold
        color: Couleurs.texte2
    }
    component Interrupteur: QQC2.AbstractButton {
        id: inter
        checkable: true
        implicitWidth: 34
        implicitHeight: 20
        focusPolicy: Qt.NoFocus
        background: Rectangle {
            radius: 10
            color: inter.checked ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.2)
            Behavior on color { ColorAnimation { duration: 120 } }
            Rectangle {
                width: 14; height: 14; radius: 7
                y: 3
                x: inter.checked ? parent.width - width - 3 : 3
                color: "#FFFFFF"
                Behavior on x { NumberAnimation { duration: 120 } }
            }
        }
    }

    Rectangle { width: 0.5; height: parent.height; color: Couleurs.bord }

    QQC2.ScrollView {
        id: defilement
        anchors.fill: parent
        anchors.leftMargin: 1
        contentWidth: availableWidth
        QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff

        ColumnLayout {
            width: defilement.availableWidth
            spacing: 18

            // ——— Le tableau : sa place, son nom ———
            ColumnLayout {
                Layout.fillWidth: true
                Layout.topMargin: 16
                Layout.leftMargin: 18
                Layout.rightMargin: 12
                spacing: 4
                RowLayout {
                    Layout.fillWidth: true
                    Text {
                        Layout.fillWidth: true
                        text: panneau.t ? "Tableau · " + panneau.tableaux.adresse(panneau.t) + " · " + panneau.tableaux.lignesDonnees(panneau.t) + " lignes" : ""
                        font.pixelSize: 12
                        color: Couleurs.texte2
                        elide: Text.ElideRight
                    }
                    Outil {
                        Layout.preferredWidth: 26
                        Layout.preferredHeight: 26
                        picto: "M7 7l10 10 M17 7L7 17"
                        aide: "Fermer le panneau (l'onglet du tableau le rouvre)"
                        onClicked: panneau.fermer()
                    }
                }
                TextInput {
                    id: nom
                    Layout.fillWidth: true
                    text: panneau.t ? panneau.t.nom.replace(/_/g, " ") : ""
                    font.pixelSize: 24
                    font.weight: Font.DemiBold
                    font.letterSpacing: -0.2
                    color: Couleurs.texte
                    clip: true
                    selectByMouse: true
                    Accessible.name: "Nom du tableau"
                    onAccepted: { if (panneau.t && text.trim() && text !== panneau.t.nom.replace(/_/g, " ")) panneau.tableaux.renommer(panneau.t, text); panneau.doc.forceActiveFocus() }
                    Keys.onEscapePressed: panneau.doc.forceActiveFocus()
                    onActiveFocusChanged: if (!activeFocus) text = Qt.binding(function () { return panneau.t ? panneau.t.nom.replace(/_/g, " ") : "" })
                    QQC2.ToolTip.visible: zoneNom.containsMouse && !activeFocus
                    QQC2.ToolTip.text: "Cliquez pour renommer ; les formules s'en servent : " + (panneau.t ? panneau.t.nom : "") + "[Colonne]"
                    QQC2.ToolTip.delay: 700
                    MouseArea { id: zoneNom; anchors.fill: parent; hoverEnabled: true; acceptedButtons: Qt.NoButton; cursorShape: Qt.IBeamCursor }
                }
                // Le voir en fiches, lui ajouter une ligne
                Flow {
                    Layout.topMargin: 6
                    Layout.fillWidth: true
                    spacing: 6
                    Outil {
                        text: "Fiches"
                        picto: "M4 5h7v6H4z M13 5h7v6h-7z M4 13h7v6H4z M13 13h7v6h-7z"
                        aide: "Une carte par ligne, et la fiche ouverte pour saisir sans se tromper de case"
                        background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12) }
                        onClicked: fenetre.voirFiches(panneau.t)
                    }
                    Outil {
                        text: "Formulaire"
                        picto: "M5 3h14v18H5z M8 8h8 M8 12h8 M8 16h5"
                        aide: "Une page claire pour saisir une ligne de plus, question après question"
                        background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12) }
                        onClicked: fenetre.voirFormulaire(panneau.t)
                    }
                    Outil {
                        text: "Ajouter une ligne"
                        picto: "M12 5v14 M5 12h14"
                        aide: "Une ligne de plus à la fin du tableau, avec ses formules"
                        background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12) }
                        onClicked: panneau.tableaux.ajouterLigne(panneau.t)
                    }
                }
            }

            // ——— La colonne de la case courante ———
            ColumnLayout {
                visible: panneau.colonne !== null
                Layout.fillWidth: true
                Layout.leftMargin: 18
                Layout.rightMargin: 18
                spacing: 8
                Section { text: panneau.colonne ? "Colonne " + panneau.colonne.nom : "" }
                Rectangle {
                    Layout.preferredHeight: 24
                    Layout.preferredWidth: genre.implicitWidth + 16
                    radius: 6
                    color: fenetre.accentFond
                    Text { id: genre; anchors.centerIn: parent; text: panneau.genreEnMots(panneau.colonne); font.pixelSize: 12; font.weight: Font.DemiBold; color: fenetre.accentEncre }
                }
                // Une colonne calculée : sa formule en mots, et de quoi la changer
                RowLayout {
                    visible: panneau.colonne !== null && panneau.colonne.calcul
                    Layout.fillWidth: true
                    spacing: 6
                    Repeater {
                        model: {
                            if (!panneau.colonne || !panneau.colonne.calcul) return []
                            var m = Fonctions.morceaux(panneau.colonne.formule)
                            return m ? m.filter(function (x) { return x.texte !== "=" }) : [{ texte: panneau.colonne.formule, puce: false }]
                        }
                        delegate: Rectangle {
                            Layout.preferredHeight: 26
                            Layout.preferredWidth: morceau.implicitWidth + (modelData.puce ? 18 : 2)
                            radius: 7
                            color: modelData.puce ? Couleurs.champ : "transparent"
                            border.width: modelData.puce ? 1 : 0
                            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                            Text {
                                id: morceau
                                anchors.centerIn: parent
                                text: String(modelData.texte).replace(/^=/, "").replace(/-/g, "−")
                                font.pixelSize: 13
                                font.weight: modelData.puce ? Font.DemiBold : Font.Normal
                                color: modelData.puce ? Couleurs.texte : Couleurs.texte2
                            }
                        }
                    }
                    Item { Layout.fillWidth: true }
                    QQC2.AbstractButton {
                        focusPolicy: Qt.NoFocus
                        hoverEnabled: true
                        contentItem: Text { text: "Modifier"; font.pixelSize: 13; font.weight: Font.DemiBold; font.underline: parent.hovered; color: fenetre.accentEncre }
                        background: null
                        // (la première case de la colonne, la formule prête à écrire dans la barre)
                        onClicked: {
                            panneau.doc.allerA(panneau.tableaux.lettres(panneau.t.c1 + panneau.rang) + (panneau.t.l1 + 2))
                            Qt.callLater(function () { champFormule.forceActiveFocus(); champFormule.cursorPosition = champFormule.text.length })
                        }
                    }
                }
                // Le reste à payer : en ocre, ou pas
                Rectangle {
                    visible: panneau.progression !== null && panneau.progression.reste === panneau.rang
                    Layout.fillWidth: true
                    Layout.preferredHeight: ocre.implicitHeight + 18
                    radius: 10
                    color: Couleurs.champ
                    border.width: 1
                    border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                    RowLayout {
                        id: ocre
                        anchors.fill: parent
                        anchors.leftMargin: 10
                        anchors.rightMargin: 10
                        spacing: 10
                        Rectangle {
                            Layout.preferredHeight: 20
                            Layout.preferredWidth: exemple.implicitWidth + 14
                            radius: 5
                            color: Qt.rgba(226 / 255, 166 / 255, 43 / 255, 0.3)
                            Text { id: exemple; anchors.centerIn: parent; text: "5 000 F"; font.pixelSize: 12; font.weight: Font.DemiBold; color: "#1E2740" }
                        }
                        Text { Layout.fillWidth: true; text: "En ocre s'il reste quelque chose"; font.pixelSize: 13; color: Couleurs.texte; wrapMode: Text.Wrap }
                        Interrupteur {
                            checked: panneau.t ? panneau.t.ocre !== false : true
                            Accessible.name: "En ocre s'il reste quelque chose"
                            onToggled: panneau.tableaux.regler(panneau.t, "ocre", checked ? "oui" : "non")
                        }
                    }
                }
                // Comment afficher les valeurs
                Flow {
                    Layout.fillWidth: true
                    spacing: 6
                    Text { text: "Afficher en"; font.pixelSize: 12; color: Couleurs.texte2; height: 26; verticalAlignment: Text.AlignVCenter; rightPadding: 2 }
                    Repeater {
                        model: [["texte", "Texte"], ["montant", "Montant"], ["nombre", "Nombre"], ["date", "Date"], ["pourcent", "%"]]
                        delegate: QQC2.AbstractButton {
                            id: format
                            readonly property bool choisi: panneau.colonne !== null && (panneau.colonne.genre === modelData[0]
                                                           || (modelData[0] === "nombre" && panneau.colonne.genre === "" && panneau.colonne.nombre)
                                                           || (modelData[0] === "texte" && panneau.colonne.genre === "" && !panneau.colonne.nombre))
                            height: 26
                            width: texteFormat.implicitWidth + 18
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: panneau.tableaux.formaterColonne(panneau.t, panneau.colonne.nom, modelData[0], panneau.lire)
                            background: Rectangle {
                                radius: 7
                                color: format.choisi ? fenetre.accentFond : format.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) : "transparent"
                                border.width: format.choisi ? 0 : 1
                                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                            }
                            contentItem: Text {
                                id: texteFormat
                                text: modelData[1]
                                horizontalAlignment: Text.AlignHCenter
                                verticalAlignment: Text.AlignVCenter
                                font.pixelSize: 12
                                font.weight: format.choisi ? Font.DemiBold : Font.Normal
                                color: format.choisi ? fenetre.accentEncre : Couleurs.texte
                            }
                        }
                    }
                }
            }

            // ——— La ligne des totaux : le calcul de chaque colonne ———
            ColumnLayout {
                Layout.fillWidth: true
                Layout.leftMargin: 18
                Layout.rightMargin: 18
                spacing: 6
                RowLayout {
                    Layout.fillWidth: true
                    Section { Layout.fillWidth: true; text: "Ligne des totaux" }
                    Interrupteur {
                        checked: panneau.t ? panneau.t.totaux : false
                        Accessible.name: "Ligne des totaux"
                        onToggled: panneau.tableaux.totaux(panneau.t, checked)
                    }
                }
                Repeater {
                    model: panneau.t && panneau.t.totaux ? panneau.colonnes.slice(1) : []
                    delegate: RowLayout {
                        id: total
                        Layout.fillWidth: true
                        Layout.preferredHeight: 30
                        spacing: 8
                        // (une colonne de texte ne se compte qu'en nombre de valeurs)
                        readonly property var choix: modelData.nombre ? ["somme", "moyenne", "nombre", "min", "max", "aucun"] : ["nombre", "aucun"]
                        Text { Layout.fillWidth: true; text: modelData.nom; font.pixelSize: 13; color: Couleurs.texte; elide: Text.ElideRight }
                        QQC2.AbstractButton {
                            id: liste
                            Layout.preferredWidth: 120
                            Layout.preferredHeight: 28
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: menuCalcul.popup(liste, 0, liste.height + 2)
                            background: Rectangle {
                                radius: 7
                                color: Couleurs.champ
                                border.width: 1
                                border.color: liste.hovered ? Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.45) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                            }
                            contentItem: RowLayout {
                                spacing: 4
                                Text {
                                    Layout.fillWidth: true
                                    Layout.leftMargin: 9
                                    text: panneau.nomsCalculs[modelData.total] || "Aucun"
                                    font.pixelSize: 13
                                    color: Couleurs.texte
                                    elide: Text.ElideRight
                                }
                                Text { Layout.rightMargin: 9; text: "▾"; font.pixelSize: 9; color: Couleurs.texte2 }
                            }
                            MenuSama {
                                id: menuCalcul
                                implicitWidth: 220
                                Instantiator {
                                    model: total.choix
                                    delegate: ElementMenu {
                                        required property var modelData
                                        text: panneau.nomsCalculs[modelData]
                                        coche: modelData === (total.calculActuel || "aucun")
                                        onTriggered: panneau.tableaux.totaliser(panneau.t, total.nomColonne, modelData, panneau.lire)
                                    }
                                    onObjectAdded: (i, o) => menuCalcul.insertItem(i, o)
                                    onObjectRemoved: (i, o) => menuCalcul.removeItem(o)
                                }
                            }
                        }
                        readonly property string nomColonne: modelData.nom
                        readonly property string calculActuel: modelData.total
                    }
                }
                Text {
                    visible: panneau.t !== null && panneau.t.totaux
                    Layout.fillWidth: true
                    text: "Les lignes cachées par un filtre ne comptent pas."
                    font.pixelSize: 12
                    color: Couleurs.texte3
                    wrapMode: Text.Wrap
                }
            }

            // ——— Le style ———
            ColumnLayout {
                Layout.fillWidth: true
                Layout.leftMargin: 18
                Layout.rightMargin: 18
                spacing: 8
                Section { text: "Style" }
                Row {
                    spacing: 8
                    Repeater {
                        // [style, nom, titre, bande]
                        model: [["lagune", "Lagune", fenetre.accent, Qt.rgba(31 / 255, 94 / 255, 122 / 255, 0.08)],
                                ["sable", "Sable", "#D8CFC2", "#F3ECE2"],
                                ["sobre", "Sobre", "#1F1C18", "#FFFFFF"]]
                        delegate: QQC2.AbstractButton {
                            id: style
                            readonly property bool choisi: panneau.t !== null && (panneau.t.style === modelData[0] || (modelData[0] === "lagune" && panneau.t.style === "autre"))
                            width: 56
                            height: 40
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            Accessible.name: "Style " + modelData[1]
                            onClicked: panneau.tableaux.styler(panneau.t, modelData[0])
                            QQC2.ToolTip.visible: hovered
                            QQC2.ToolTip.delay: 500
                            QQC2.ToolTip.text: modelData[1]
                            background: Rectangle {
                                radius: 8
                                color: Couleurs.champ
                                border.width: style.choisi ? 2 : 1
                                border.color: style.choisi ? fenetre.accent : style.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.3) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
                            }
                            contentItem: Column {
                                padding: 5
                                spacing: 3
                                Rectangle { width: 46 - 2; height: 6; radius: 2; color: modelData[2] }
                                Rectangle { width: 46 - 2; height: 4; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08) }
                                Rectangle { width: 46 - 2; height: 4; color: modelData[3] }
                                Rectangle { width: 46 - 2; height: 4; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.08) }
                            }
                        }
                    }
                }
            }

            Item { Layout.fillHeight: true; Layout.preferredHeight: 6 }

            // ——— Convertir en cases ordinaires ———
            QQC2.AbstractButton {
                Layout.leftMargin: 18
                Layout.bottomMargin: 16
                focusPolicy: Qt.NoFocus
                hoverEnabled: true
                background: null
                contentItem: Text { text: "Convertir en cases ordinaires"; font.pixelSize: 13; font.weight: Font.DemiBold; font.underline: parent.hovered; color: fenetre.accentEncre }
                QQC2.ToolTip.visible: hovered
                QQC2.ToolTip.delay: 500
                QQC2.ToolTip.text: "Le tableau redevient des cases : la mise en forme reste, les filtres partent, les formules gardent leurs résultats"
                onClicked: panneau.tableaux.convertir(panneau.t)
            }
        }
    }
}
