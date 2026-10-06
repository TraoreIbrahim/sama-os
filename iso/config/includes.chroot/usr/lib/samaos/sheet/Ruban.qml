// Ruban de Sama Sheet : onglets (Accueil, Insertion, Formules, Données) et leurs outils. Chaque outil envoie une commande
// au moteur ; son état (gras appliqué, alignement…) vient du moteur.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

ColumnLayout {
    id: ruban
    spacing: 0
    readonly property var doc: fenetre.doc

    // ——— Couleurs et bordures des cellules ———
    // Couleurs de Sama : rangées du plus clair au plus soutenu
    readonly property var couleursFond: [
        [["#F4F1EC", "Sable"], ["#DDEBE3", "Vert pâle"], ["#FBE3D3", "Latérite pâle"], ["#FCEFC7", "Or pâle"],
         ["#DCE7F3", "Bleu pâle"], ["#EBE2F0", "Prune pâle"], ["#F7DCDC", "Rose pâle"], ["#EAEAEA", "Gris pâle"]],
        [["#D8CFC2", "Sable foncé"], ["#A3D6C1", "Vert tendre"], ["#F0B392", "Latérite claire"], ["#F2D27A", "Or"],
         ["#A8C4E0", "Bleu clair"], ["#C9B5D6", "Prune claire"], ["#E8A9A9", "Rose"], ["#BDBDBD", "Gris"]],
        [["#665E54", "Brun gris"], ["#2F6B57", "Vert forêt"], ["#B5532F", "Latérite"], ["#D9963A", "Ocre"],
         ["#3E6E9E", "Bleu"], ["#7B5E8C", "Prune"], ["#A3322A", "Rouge"], ["#1F1C18", "Noir"]]
    ]
    readonly property var couleursTexte: [
        [["#1F1C18", "Noir"], ["#665E54", "Brun gris"], ["#1F5544", "Vert foncé"], ["#93401F", "Latérite foncée"],
         ["#9A6420", "Ocre foncé"], ["#2B5079", "Bleu foncé"], ["#5B4069", "Prune foncée"], ["#A3322A", "Rouge"]],
        [["#8A8277", "Gris"], ["#2F6B57", "Vert forêt"], ["#B5532F", "Latérite"], ["#D9963A", "Ocre"],
         ["#3E6E9E", "Bleu"], ["#7B5E8C", "Prune"], ["#C98A9B", "Rose"], ["#FFFFFF", "Blanc"]]
    ]
    // Couleur annoncée par le moteur pour la cellule courante (« -1 » : automatique, aucun remplissage)
    function couleurCellule(commande, defaut) {
        var e = fenetre.etat(commande)
        var n = Number(e)
        return e === undefined || e === "" || isNaN(n) || n < 0 ? defaut : "#" + ("000000" + n.toString(16)).slice(-6)
    }
    // (« » : automatique ou aucun remplissage, -1 pour le moteur)
    function valeurCouleur(c) { return c ? parseInt(c.slice(1), 16) : -1 }
    function colorerTexte(c) { doc.commande(".uno:Color", { Color: { type: "long", value: valeurCouleur(c) } }) }
    function colorerFond(c) { doc.commande(".uno:BackgroundColor", { BackgroundColor: { type: "long", value: valeurCouleur(c) } }) }

    // Bordures : épaisseur en centièmes de millimètre (26 : fine, 53 : épaisse ; 0 : aucune) ; « valides » : lignes à
    // changer, les autres restent (1 haut, 2 bas, 4 gauche, 8 droite, 16 lignes intérieures horizontales, 32 verticales)
    function trait(epaisseur) {
        return { type: "com.sun.star.table.BorderLine2", value: {
            Color: { type: "com.sun.star.util.Color", value: 0x1F1C18 },
            InnerLineWidth: { type: "short", value: 0 }, OuterLineWidth: { type: "short", value: epaisseur },
            LineDistance: { type: "short", value: 0 }, LineStyle: { type: "short", value: 0 },
            LineWidth: { type: "unsigned long", value: 0 } } }
    }
    function poserBordures(epaisseurDehors, epaisseurDedans, valides) {
        var zero = { type: "long", value: 0 }
        doc.commande(".uno:SetBorderStyle", {
            OuterBorder: { type: "[]any", value: [trait(epaisseurDehors), trait(epaisseurDehors), trait(epaisseurDehors), trait(epaisseurDehors), zero, zero, zero, zero, zero] },
            InnerBorder: { type: "[]any", value: [trait(epaisseurDedans), trait(epaisseurDedans), { type: "short", value: 0 }, { type: "short", value: valides }, zero] }
        })
    }
    function bordures(genre) {
        if (genre === "toutes") poserBordures(26, 26, 63)
        else if (genre === "contour") poserBordures(26, 0, 15)
        else if (genre === "epais") poserBordures(53, 0, 15)
        else if (genre === "bas") poserBordures(26, 0, 2)
        else poserBordures(0, 0, 63)
    }

    // Graphique : le type choisi, à partir des cellules choisies
    component OutilGraphique: Outil {
        id: outilGraphique
        text: "Graphique"
        picto: "M4 20V10 M10 20V4 M16 20v-7 M3 20h18"
        aide: "Insérer un graphique à partir des cellules choisies"
        ouvert: choixGraphique.opened
        onClicked: choixGraphique.open()
        ChoixGraphique { id: choixGraphique; onChoisi: type => ruban.doc.insererGraphique(type) }
    }

    // ——— Onglets ———
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 38
        color: Couleurs.fond
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 14
            anchors.rightMargin: 14
            spacing: 4
            Repeater {
                model: [["accueil", "Accueil"], ["insertion", "Insertion"], ["formules", "Formules"], ["donnees", "Données"]]
                delegate: QQC2.AbstractButton {
                    id: onglet
                    readonly property bool choisi: fenetre.onglet === modelData[0]
                    Layout.preferredHeight: 28
                    implicitWidth: libelle.implicitWidth + 28
                    focusPolicy: Qt.NoFocus
                    hoverEnabled: true
                    onClicked: fenetre.onglet = modelData[0]
                    background: Rectangle {
                        radius: 14
                        color: onglet.choisi ? fenetre.vertFond : onglet.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                    }
                    contentItem: Text {
                        id: libelle
                        text: modelData[1]
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        font.pixelSize: 13
                        font.weight: onglet.choisi ? Font.DemiBold : Font.Normal
                        color: onglet.choisi ? fenetre.vertEncre : Couleurs.texte2
                    }
                }
            }
            Item { Layout.fillWidth: true }
            Outil { picto: "M4 12h10 M10 6l6 6l-6 6 M20 4v16"; text: "Ouvrir"; aide: "Ouvrir un classeur (Ctrl+O)"; onClicked: dialogueOuvrir.open() }
            Outil { picto: "M5 4h11l3 3v13H5z M8 4v5h7V4 M8 20v-6h8v6"; text: "Enregistrer"; aide: "Enregistrer (Ctrl+S)"; onClicked: fenetre.enregistrer() }
        }
    }

    // ——— Outils de l'onglet ———
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 44
        color: Couleurs.fond
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: Couleurs.bord }
        // (fenêtre étroite : les outils défilent de côté au lieu d'être coupés)
        Flickable {
            id: defileur
            anchors.fill: parent
            anchors.leftMargin: 14
            anchors.rightMargin: 14
            contentWidth: Math.max(width, outils.item ? outils.item.implicitWidth : 0)
            contentHeight: height
            flickableDirection: Flickable.HorizontalFlick
            boundsBehavior: Flickable.StopAtBounds
            interactive: contentWidth > width
            clip: true
            Loader {
                id: outils
                width: defileur.contentWidth
                height: defileur.height
                sourceComponent: fenetre.onglet === "insertion" ? insertion : fenetre.onglet === "formules" ? formules
                                 : fenetre.onglet === "donnees" ? donnees : accueil
            }
        }
    }

    // Annuler et rétablir (communs aux onglets)
    component Historique: RowLayout {
        spacing: 2
        Outil { picto: "M4 12a8 8 0 1 0 2.3-5.6 M4 5v4h4"; aide: "Annuler (Ctrl+Z)"; enabled: fenetre.etat(".uno:Undo") !== "disabled"; onClicked: ruban.doc.commande(".uno:Undo") }
        Outil { picto: "M20 12a8 8 0 1 1-2.3-5.6 M20 5v4h-4"; aide: "Rétablir (Ctrl+Y)"; enabled: fenetre.etat(".uno:Redo") !== "disabled"; onClicked: ruban.doc.commande(".uno:Redo") }
    }

    Component {
        id: accueil
        RowLayout {
            spacing: 4
            Historique {}
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            // Police et taille
            Outil {
                id: choixPolice
                text: (fenetre.etat(".uno:CharFontName") || "Noto Sans") + (Number(fenetre.etat(".uno:FontHeight")) > 0 ? "  ·  " + fenetre.etat(".uno:FontHeight") : "")
                aide: "Police et taille"
                background: Rectangle { radius: 8; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, choixPolice.hovered ? 0.09 : 0.05) }
                onClicked: menuPolice.popup()
                QQC2.Menu {
                    id: menuPolice
                    Repeater {
                        model: ["Noto Sans", "Noto Serif", "Liberation Sans", "Liberation Serif", "Liberation Mono"]
                        QQC2.MenuItem {
                            text: modelData
                            font.family: modelData
                            onTriggered: ruban.doc.commande(".uno:CharFontName", { "CharFontName.FamilyName": { type: "string", value: modelData } })
                        }
                    }
                    QQC2.MenuSeparator {}
                    Repeater {
                        model: [9, 10, 11, 12, 14, 16, 18, 24, 32]
                        QQC2.MenuItem {
                            text: "Taille " + modelData
                            onTriggered: ruban.doc.commande(".uno:FontHeight", { "FontHeight.Height": { type: "float", value: modelData } })
                        }
                    }
                }
            }
            Item { width: 2 }
            Outil { text: "B"; police.bold: true; police.pixelSize: 14; aide: "Gras (Ctrl+B)"; actif: fenetre.actif(".uno:Bold"); onClicked: ruban.doc.commande(".uno:Bold") }
            Outil { text: "I"; police.italic: true; police.pixelSize: 14; aide: "Italique (Ctrl+I)"; actif: fenetre.actif(".uno:Italic"); onClicked: ruban.doc.commande(".uno:Italic") }
            Outil { text: "U"; police.underline: true; police.pixelSize: 14; aide: "Souligné (Ctrl+U)"; actif: fenetre.actif(".uno:Underline"); onClicked: ruban.doc.commande(".uno:Underline") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { picto: "M4 6h16 M4 10h10 M4 14h16 M4 18h10"; aide: "Aligner à gauche"; actif: fenetre.actif(".uno:AlignLeft"); onClicked: ruban.doc.commande(".uno:AlignLeft") }
            Outil { picto: "M4 6h16 M7 10h10 M4 14h16 M7 18h10"; aide: "Centrer"; actif: fenetre.actif(".uno:AlignHorizontalCenter"); onClicked: ruban.doc.commande(".uno:AlignHorizontalCenter") }
            Outil { picto: "M4 6h16 M10 10h10 M4 14h16 M10 18h10"; aide: "Aligner à droite"; actif: fenetre.actif(".uno:AlignRight"); onClicked: ruban.doc.commande(".uno:AlignRight") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            // Couleur du texte, remplissage, bordures (sous chaque outil, la couleur de la cellule courante)
            Outil {
                picto: "M7.5 16L12 5l4.5 11 M9.2 12h5.6"
                aide: "Couleur du texte"
                ouvert: paletteTexte.opened
                onClicked: paletteTexte.open()
                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    y: parent.height - 8
                    width: 14; height: 3; radius: 1.5
                    color: ruban.couleurCellule(".uno:Color", Couleurs.texte)
                    border.width: color.hslLightness > 0.9 ? 0.5 : 0
                    border.color: Couleurs.bord
                }
                Nuancier {
                    id: paletteTexte
                    titre: "Couleur du texte"
                    rangees: ruban.couleursTexte
                    aucune: "Automatique"
                    aucunePicto: "M7.5 16L12 5l4.5 11 M9.2 12h5.6"
                    onChoisie: c => ruban.colorerTexte(c)
                }
            }
            Outil {
                picto: "M5 11.5L10.5 6l5.5 5.5l-5.5 5.5z M5 11.5h11 M18.5 13.5c.7 1 1.2 1.8 1.2 2.3a1.2 1.2 0 0 1-2.4 0c0-.5.5-1.3 1.2-2.3z M8.5 4l2 2"
                aide: "Couleur de remplissage"
                ouvert: paletteFond.opened
                onClicked: paletteFond.open()
                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    y: parent.height - 8
                    width: 14; height: 3; radius: 1.5
                    color: ruban.couleurCellule(".uno:BackgroundColor", "transparent")
                    border.width: 0.5
                    border.color: Couleurs.bord
                }
                Nuancier {
                    id: paletteFond
                    titre: "Remplissage"
                    rangees: ruban.couleursFond
                    aucune: "Aucun remplissage"
                    aucunePicto: "M4 4h16v16H4z M4 20L20 4"
                    onChoisie: c => ruban.colorerFond(c)
                }
            }
            Outil {
                picto: "M4 4h16v16H4z M4 12h16 M12 4v16"
                aide: "Bordures"
                ouvert: choixBordures.opened
                onClicked: choixBordures.open()
                ChoixBordures { id: choixBordures; onChoisie: g => ruban.bordures(g) }
            }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            // Formats de nombre : franc CFA (monnaie par défaut de Sama), pourcentage, décimales
            Outil {
                id: fcfa
                text: "FCFA"
                police.bold: true
                police.pixelSize: 12
                aide: "Montant en francs CFA"
                background: Rectangle { radius: 8; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, fcfa.hovered ? 0.09 : 0.05) }
                // (puis les colonnes s'élargissent pour que les montants ne s'affichent pas « ### »)
                onClicked: { ruban.doc.commande(".uno:NumberFormatCurrency"); ruban.doc.commande(".uno:SetOptimalColumnWidthDirect") }
            }
            Outil { text: "%"; encre: Couleurs.texte2; aide: "Pourcentage"; onClicked: ruban.doc.commande(".uno:NumberFormatPercent") }
            Outil { text: ",00"; police.pixelSize: 12; encre: Couleurs.texte2; aide: "Une décimale de plus"; onClicked: ruban.doc.commande(".uno:NumberFormatIncDecimals") }
            Outil { text: ",0"; police.pixelSize: 12; encre: Couleurs.texte2; aide: "Une décimale de moins"; onClicked: ruban.doc.commande(".uno:NumberFormatDecDecimals") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Somme"; picto: "M17 5H7l6 7l-6 7h10"; aide: "Somme des cellules au-dessus ou à gauche"; onClicked: ruban.doc.commande(".uno:AutoSum") }
            Outil {
                text: "Trier"
                picto: "M4 7h10 M18 7h2 M4 17h4 M12 17h8 M16 5v4 M10 15v4"
                aide: "Trier les lignes"
                onClicked: menuTri.popup()
                QQC2.Menu {
                    id: menuTri
                    QQC2.MenuItem { text: "De A à Z, du plus petit au plus grand"; onTriggered: ruban.doc.commande(".uno:SortAscending") }
                    QQC2.MenuItem { text: "De Z à A, du plus grand au plus petit"; onTriggered: ruban.doc.commande(".uno:SortDescending") }
                }
            }
            Item { Layout.fillWidth: true }
            OutilGraphique { }
        }
    }

    Component {
        id: insertion
        RowLayout {
            spacing: 4
            Historique {}
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            OutilGraphique { }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Ligne au-dessus"; picto: "M4 14h16v6H4z M12 4v6 M9 7h6"; onClicked: ruban.doc.commande(".uno:InsertRowsBefore") }
            Outil { text: "Ligne en dessous"; picto: "M4 4h16v6H4z M12 14v6 M9 17h6"; onClicked: ruban.doc.commande(".uno:InsertRowsAfter") }
            Outil { text: "Colonne à gauche"; picto: "M14 4h6v16h-6z M4 12h6 M7 9v6"; onClicked: ruban.doc.commande(".uno:InsertColumnsBefore") }
            Outil { text: "Colonne à droite"; picto: "M4 4h6v16H4z M14 12h6 M17 9v6"; onClicked: ruban.doc.commande(".uno:InsertColumnsAfter") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil {
                text: "Supprimer"
                picto: "M5 7h14 M10 7V5h4v2 M7 7l1 12h8l1-12"
                encre: "#A3322A"
                aide: "Supprimer la ligne ou la colonne"
                onClicked: menuSupprimer.popup()
                QQC2.Menu {
                    id: menuSupprimer
                    QQC2.MenuItem { text: "Supprimer la ligne"; onTriggered: ruban.doc.commande(".uno:DeleteRows") }
                    QQC2.MenuItem { text: "Supprimer la colonne"; onTriggered: ruban.doc.commande(".uno:DeleteColumns") }
                }
            }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Fusionner"; picto: "M4 6h16v12H4z M9 12h6 M7 10l-2 2l2 2 M17 10l2 2l-2 2"; actif: fenetre.actif(".uno:ToggleMergeCells"); onClicked: ruban.doc.commande(".uno:ToggleMergeCells") }
            Item { Layout.fillWidth: true }
        }
    }

    Component {
        id: formules
        RowLayout {
            spacing: 4
            Historique {}
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Somme"; picto: "M17 5H7l6 7l-6 7h10"; onClicked: ruban.doc.commande(".uno:AutoSum") }
            Repeater {
                model: [["Moyenne", "=MOYENNE()"], ["Minimum", "=MIN()"], ["Maximum", "=MAX()"], ["Nombre", "=NB()"], ["Si…", "=SI(;;)"], ["Arrondi", "=ARRONDI(;0)"]]
                Outil { text: modelData[0]; onClicked: fenetre.commencerFormule(modelData[1]) }
            }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Afficher les formules"; picto: "M4 6h16 M4 12h10 M4 18h7"; actif: fenetre.actif(".uno:ToggleFormula"); onClicked: ruban.doc.commande(".uno:ToggleFormula") }
            Item { Layout.fillWidth: true }
        }
    }

    Component {
        id: donnees
        RowLayout {
            spacing: 4
            Historique {}
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Trier de A à Z"; picto: "M6 4v16 M3 17l3 3l3-3 M13 6h7 M13 12h5 M13 18h3"; onClicked: ruban.doc.commande(".uno:SortAscending") }
            Outil { text: "Trier de Z à A"; picto: "M6 4v16 M3 7l3-3l3 3 M13 6h3 M13 12h5 M13 18h7"; onClicked: ruban.doc.commande(".uno:SortDescending") }
            Separateur { Layout.leftMargin: 6; Layout.rightMargin: 6 }
            Outil { text: "Figer la première ligne"; picto: "M4 4h16v16H4z M4 9h16"; onClicked: ruban.doc.commande(".uno:FreezePanesFirstRow") }
            Outil { text: "Figer jusqu'ici"; picto: "M4 4h16v16H4z M4 10h16 M10 4v16"; actif: fenetre.actif(".uno:FreezePanes"); onClicked: ruban.doc.commande(".uno:FreezePanes") }
            Item { Layout.fillWidth: true }
        }
    }
}
