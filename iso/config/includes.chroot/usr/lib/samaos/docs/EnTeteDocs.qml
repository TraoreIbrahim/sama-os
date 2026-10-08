// L'en-tête de Sama Docs (maquette « En-tête Docs ») : les menus, « Que voulez-vous faire ? », le mode (Modification ou
// Suggestion) et Enregistrer ; puis la barre d'outils sur une ligne : annuler, rétablir, reproduire la mise en forme,
// le zoom, le style du paragraphe, la police et sa taille, gras, italique, souligné, la couleur du texte, le surlignage,
// le commentaire, l'image, l'alignement, les listes. (Les pièces sont celles de Sama Sheet, au vert de Docs.)
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "../sheet"

Rectangle {
    id: entete
    readonly property var doc: fenetre.doc
    readonly property var actions: fenetre.actions
    implicitHeight: 42 + 46
    color: Couleurs.fond
    function actif(c) { return doc.etats[c] === "true" }
    function ouvrirRecherche() { champ.forceActiveFocus(); champ.selectAll() }

    component GroupeOutils: Rectangle {
        default property alias contenu: rangee.data
        implicitWidth: rangee.implicitWidth + 12
        implicitHeight: 36
        radius: 12
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.2 : 0.045)
        RowLayout { id: rangee; anchors.centerIn: parent; spacing: 1 }
    }
    component TraitOutils: Rectangle { width: 1; height: 18; color: Couleurs.bord; Layout.leftMargin: 4; Layout.rightMargin: 4 }
    component ListeOutils: Outil {
        id: liste
        property real largeur: 100
        property font policeTexte
        implicitWidth: largeur
        implicitHeight: 28
        background: Rectangle { radius: 7; color: Couleurs.champ; border.width: 0.5; border.color: liste.hovered ? fenetre.accent : Couleurs.bord }
        contentItem: RowLayout {
            spacing: 4
            Text {
                Layout.fillWidth: true
                Layout.leftMargin: 8
                text: liste.text
                font.family: liste.policeTexte.family
                font.pixelSize: 13
                color: Couleurs.texte
                elide: Text.ElideRight
            }
            Text { Layout.rightMargin: 7; text: "▾"; font.pixelSize: 9; color: Couleurs.texte2 }
        }
    }

    // Couleurs de Sama (celles de Sheet) ; pour surligner, les plus claires
    readonly property var couleursTexte: [
        [["#1F1C18", "Noir"], ["#665E54", "Brun gris"], ["#1F5544", "Vert foncé"], ["#93401F", "Latérite foncée"],
         ["#9A6420", "Ocre foncé"], ["#2B5079", "Bleu foncé"], ["#5B4069", "Prune foncée"], ["#A3322A", "Rouge"]],
        [["#8A8277", "Gris"], ["#2F6B57", "Vert forêt"], ["#B5532F", "Latérite"], ["#D9963A", "Ocre"],
         ["#3E6E9E", "Bleu"], ["#7B5E8C", "Prune"], ["#C98A9B", "Rose"], ["#FFFFFF", "Blanc"]]
    ]
    readonly property var couleursSurligner: [
        [["#FCEFC7", "Or pâle"], ["#F2D27A", "Or"], ["#DDEBE3", "Vert pâle"], ["#A3D6C1", "Vert tendre"],
         ["#DCE7F3", "Bleu pâle"], ["#FBE3D3", "Latérite pâle"], ["#EBE2F0", "Prune pâle"], ["#F7DCDC", "Rose pâle"]]
    ]
    function couleur(commande, defaut) {
        var e = doc.etats[commande], n = Number(e)
        return e === undefined || e === "" || isNaN(n) || n < 0 ? defaut : "#" + ("000000" + n.toString(16)).slice(-6)
    }

    // ——— Première ligne : les menus, la recherche des commandes, le mode, Enregistrer ———
    RowLayout {
        id: ligneMenus
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        height: 42
        anchors.leftMargin: 8
        anchors.rightMargin: 12
        spacing: 8

        QQC2.MenuBar {
            Layout.alignment: Qt.AlignVCenter
            background: null
            delegate: QQC2.MenuBarItem {
                id: titreMenu
                implicitHeight: 28
                leftPadding: 9
                rightPadding: 9
                background: Rectangle { radius: 7; color: titreMenu.highlighted || titreMenu.down ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.25 : 0.07) : "transparent" }
                contentItem: Text { text: titreMenu.text; font.pixelSize: 13; color: Couleurs.texte; verticalAlignment: Text.AlignVCenter }
            }
            MenuSama {
                title: "Fichier"
                ElementMenu { cle: "nouveau" }
                ElementMenu { cle: "modeles" }
                ElementMenu { cle: "ouvrir" }
                TitreMenu { text: "Enregistrer" }
                ElementMenu { cle: "enregistrer" }
                ElementMenu { cle: "enregistrerSous" }
                ElementMenu { cle: "pdf" }
                TitreMenu { text: ""; implicitHeight: 9 }
                ElementMenu { cle: "fermer" }
            }
            MenuSama {
                id: menuEdition
                title: "Édition"
                TuilesMenu { menu: menuEdition; cles: ["annuler", "retablir", "couper", "copier", "coller"] }
                ElementMenu { cle: "collerTexte" }
                TitreMenu { text: ""; implicitHeight: 9 }
                ElementMenu { cle: "toutSelectionner" }
            }
            MenuSama {
                title: "Affichage"
                ElementMenu { cle: "plan"; coche: fenetre.planOuvert }
                TitreMenu { text: "Zoom" }
                ElementMenu { cle: "zoomPlus" }
                ElementMenu { cle: "zoomMoins" }
                ElementMenu { cle: "zoom100" }
            }
            MenuSama {
                title: "Insertion"
                ElementMenu { cle: "tableau" }
                ElementMenu { cle: "image" }
                ElementMenu { cle: "sautPage" }
                TitreMenu { text: "" ; implicitHeight: 9 }
                ElementMenu { cle: "date" }
                ElementMenu { cle: "numeroPage" }
                ElementMenu { cle: "commentaire" }
            }
            MenuSama {
                id: menuFormat
                title: "Format"
                TuilesMenu { menu: menuFormat; sansNom: true; cles: ["gras", "italique", "souligne", "barre"] }
                TitreMenu { text: "Style du paragraphe" }
                ElementMenu { cle: "texteNormal" }
                ElementMenu { cle: "titre" }
                ElementMenu { cle: "titre1" }
                ElementMenu { cle: "titre2" }
                ElementMenu { cle: "citation" }
                TitreMenu { text: "Alignement" }
                TuilesMenu { menu: menuFormat; sansNom: true; cles: ["gauche", "centre", "droite", "justifier"] }
                TitreMenu { text: "Listes et interligne" }
                TuilesMenu { menu: menuFormat; sansNom: true; cles: ["puces", "numeros"] }
                ElementMenu { cle: "interligne1" }
                ElementMenu { cle: "interligne15" }
                ElementMenu { cle: "interligne2" }
                TitreMenu { text: ""; implicitHeight: 9 }
                ElementMenu { cle: "effacerFormat" }
            }
            MenuSama {
                title: "Outils"
                ElementMenu { cle: "suggestion"; coche: fenetre.mode === "Suggestion" }
                ElementMenu { cle: "orthographe"; coche: fenetre.orthographe }
            }
            MenuSama {
                title: "Aide"
                ElementMenu { cle: "chercher" }
                ElementMenu { cle: "aide" }
            }
        }

        Item { Layout.fillWidth: true }
        // « Que voulez-vous faire ? » : les commandes, par leurs mots
        Rectangle {
            id: recherche
            Layout.preferredWidth: Math.min(380, entete.width * 0.32)
            Layout.preferredHeight: 32
            radius: 9
            color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.2 : 0.055)
            border.width: champ.activeFocus ? 1.5 : 0
            border.color: fenetre.accent
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 10
                anchors.rightMargin: 6
                spacing: 8
                Picto { trace: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4"; encre: Couleurs.texte2 }
                TextInput {
                    id: champ
                    Layout.fillWidth: true
                    font.pixelSize: 13
                    color: Couleurs.texte
                    clip: true
                    selectByMouse: true
                    property var resultats: []
                    property int choisi: 0
                    onTextChanged: { resultats = entete.actions.chercher(text); choisi = 0 }
                    onActiveFocusChanged: if (activeFocus) { resultats = entete.actions.chercher(text); liste.open() } else liste.close()
                    Keys.onDownPressed: choisi = Math.min(choisi + 1, resultats.length - 1)
                    Keys.onUpPressed: choisi = Math.max(choisi - 1, 0)
                    Keys.onEscapePressed: { text = ""; fenetre.doc.forceActiveFocus() }
                    Keys.onReturnPressed: entete.lancer(resultats[choisi])
                    Keys.onEnterPressed: entete.lancer(resultats[choisi])
                    Text {
                        visible: !parent.text
                        anchors.verticalCenter: parent.verticalCenter
                        text: "Que voulez-vous faire ? Une commande, un mot…"
                        font: parent.font
                        color: Couleurs.texte3
                        elide: Text.ElideRight
                        width: parent.width
                    }
                }
                Rectangle {
                    Layout.preferredWidth: raccourci.implicitWidth + 12
                    Layout.preferredHeight: 18
                    radius: 5
                    color: Couleurs.champ
                    Text { id: raccourci; anchors.centerIn: parent; text: "Ctrl+K"; font.pixelSize: 11; font.weight: Font.DemiBold; color: Couleurs.texte2 }
                }
            }
            QQC2.Popup {
                id: liste
                y: recherche.height + 6
                width: recherche.width
                padding: 6
                closePolicy: QQC2.Popup.CloseOnPressOutsideParent
                background: Rectangle { radius: 12; color: Couleurs.champ; border.width: 0.5; border.color: Couleurs.bord }
                contentItem: Column {
                    spacing: 2
                    Repeater {
                        model: champ.resultats
                        delegate: QQC2.AbstractButton {
                            id: resultat
                            required property var modelData
                            required property int index
                            width: liste.width - 12
                            height: 36
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: entete.lancer(modelData)
                            background: Rectangle { radius: 8; color: resultat.index === champ.choisi || resultat.hovered ? fenetre.accentFond : "transparent" }
                            contentItem: RowLayout {
                                spacing: 10
                                Picto { Layout.leftMargin: 6; trace: entete.actions.pictos[resultat.modelData.cle] || ""; encre: fenetre.accentEncre }
                                Text { Layout.fillWidth: true; text: resultat.modelData.nom; font.pixelSize: 13; color: Couleurs.texte; elide: Text.ElideRight }
                                Text { Layout.rightMargin: 8; text: resultat.modelData.raccourci || resultat.modelData.menu; font.pixelSize: 11; color: Couleurs.texte3 }
                            }
                        }
                    }
                    Text {
                        visible: champ.resultats.length === 0
                        leftPadding: 10
                        topPadding: 8
                        bottomPadding: 8
                        text: "Aucune commande pour « " + champ.text + " »"
                        font.pixelSize: 13
                        color: Couleurs.texte2
                    }
                }
            }
        }
        Item { Layout.fillWidth: true }

        // Le mode : Modification, ou Suggestion (vos changements sont proposés, pas imposés)
        Outil {
            id: boutonMode
            Layout.preferredHeight: 32
            text: fenetre.mode + "  ▾"
            picto: "M14 4l6 6L10 20H4v-6z"
            aide: fenetre.mode === "Suggestion" ? "Vos changements sont proposés, pas imposés" : "Vos changements s'écrivent directement"
            background: Rectangle {
                radius: 9
                color: fenetre.mode === "Suggestion" ? Qt.rgba(226 / 255, 166 / 255, 43 / 255, 0.24)
                       : parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.09) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.2 : 0.055)
            }
            onClicked: menuMode.popup(boutonMode, 0, boutonMode.height + 4)
            MenuSama {
                id: menuMode
                ElementMenu { text: "Modification"; picto: "M14 4l6 6L10 20H4v-6z"; coche: fenetre.mode === "Modification"; onTriggered: fenetre.choisirMode("Modification") }
                ElementMenu { text: "Suggestion"; picto: "M4 5h16v11H9l-5 4z"; coche: fenetre.mode === "Suggestion"; onTriggered: fenetre.choisirMode("Suggestion") }
            }
        }
        Outil {
            text: "Enregistrer"
            picto: "M5 4h11l3 3v13H5z M8 4v5h7V4 M8 20v-6h8v6"
            aide: "Enregistrer (Ctrl+S)"
            encre: fenetre.accentEncre
            background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.2) : fenetre.accentFond }
            onClicked: fenetre.enregistrer()
        }
    }
    function lancer(a) {
        if (!a) return
        champ.text = ""
        liste.close()
        fenetre.doc.forceActiveFocus()
        if (a.faire && (!a.actif || a.actif())) a.faire()
    }

    // ——— Seconde ligne : la barre d'outils ———
    Flickable {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: ligneMenus.bottom
        height: 46
        anchors.leftMargin: 10
        anchors.rightMargin: 10
        contentWidth: Math.max(width, outils.implicitWidth)
        contentHeight: height
        flickableDirection: Flickable.HorizontalFlick
        boundsBehavior: Flickable.StopAtBounds
        clip: true
        RowLayout {
            id: outils
            height: parent.height
            spacing: 8
            GroupeOutils {
                Outil { picto: "M9 14L4 9l5-5 M4 9h10a6 6 0 0 1 0 12h-3"; aide: "Annuler (Ctrl+Z)"; enabled: entete.actions.peutAnnuler; onClicked: entete.actions.lancer("annuler") }
                Outil { picto: "M15 14l5-5-5-5 M20 9H10a6 6 0 0 0 0 12h3"; aide: "Rétablir (Ctrl+Y)"; enabled: entete.actions.peutRetablir; onClicked: entete.actions.lancer("retablir") }
                Outil { picto: "M5 4h11v5H5z M16 6h3v5h-7v3 M12 14v6"; aide: "Reproduire la mise en forme"; actif: entete.actif(".uno:FormatPaintbrush"); onClicked: entete.doc.commande(".uno:FormatPaintbrush") }
                TraitOutils {}
                ListeOutils {
                    id: choixZoom
                    largeur: 70
                    text: Math.round(entete.doc.zoom * 100) + " %"
                    aide: "Zoom"
                    onClicked: menuZoom.popup(choixZoom, 0, choixZoom.height + 4)
                    MenuSama {
                        id: menuZoom
                        implicitWidth: 140
                        Instantiator {
                            model: [75, 100, 125, 150, 200]
                            onObjectAdded: (i, o) => menuZoom.insertItem(i, o)
                            onObjectRemoved: (i, o) => menuZoom.removeItem(o)
                            ElementMenu { text: modelData + " %"; onTriggered: entete.doc.zoom = modelData / 100 }
                        }
                    }
                }
                TraitOutils {}
                ListeOutils {
                    id: choixStyle
                    largeur: 132
                    text: entete.actions.styleActuel()
                    aide: "Style du paragraphe : les titres font le plan"
                    onClicked: menuStyle.popup(choixStyle, 0, choixStyle.height + 4)
                    MenuSama {
                        id: menuStyle
                        ElementMenu { cle: "texteNormal" }
                        ElementMenu { cle: "titre" }
                        ElementMenu { cle: "titre1" }
                        ElementMenu { cle: "titre2" }
                        ElementMenu { cle: "titre3" }
                        ElementMenu { cle: "citation" }
                    }
                }
                ListeOutils {
                    id: choixPolice
                    largeur: 118
                    Layout.leftMargin: 4
                    text: entete.doc.etats[".uno:CharFontName"] || "Noto Serif"
                    policeTexte.family: text
                    aide: "Police"
                    onClicked: menuPolice.popup(choixPolice, 0, choixPolice.height + 4)
                    MenuSama {
                        id: menuPolice
                        implicitWidth: 220
                        Instantiator {
                            model: ["Noto Serif", "Noto Sans", "Liberation Serif", "Liberation Sans", "Carlito", "Liberation Mono"]
                            onObjectAdded: (i, o) => menuPolice.insertItem(i, o)
                            onObjectRemoved: (i, o) => menuPolice.removeItem(o)
                            ElementMenu { text: modelData; onTriggered: entete.actions.police(modelData) }
                        }
                    }
                }
                ListeOutils {
                    id: choixTaille
                    largeur: 52
                    Layout.leftMargin: 4
                    text: Number(entete.doc.etats[".uno:FontHeight"]) > 0 ? entete.doc.etats[".uno:FontHeight"] : "12"
                    aide: "Taille"
                    onClicked: menuTaille.popup(choixTaille, 0, choixTaille.height + 4)
                    MenuSama {
                        id: menuTaille
                        implicitWidth: 120
                        Instantiator {
                            model: [9, 10, 11, 12, 14, 16, 18, 22, 28, 36]
                            onObjectAdded: (i, o) => menuTaille.insertItem(i, o)
                            onObjectRemoved: (i, o) => menuTaille.removeItem(o)
                            ElementMenu { text: String(modelData); onTriggered: entete.actions.taille(modelData) }
                        }
                    }
                }
                TraitOutils {}
                Outil { text: "B"; police.bold: true; police.pixelSize: 14; aide: "Gras (Ctrl+B)"; actif: entete.actif(".uno:Bold"); onClicked: entete.actions.lancer("gras") }
                Outil { text: "I"; police.italic: true; police.family: "Noto Serif"; police.pixelSize: 15; aide: "Italique (Ctrl+I)"; actif: entete.actif(".uno:Italic"); onClicked: entete.actions.lancer("italique") }
                Outil { text: "U"; police.underline: true; police.pixelSize: 14; aide: "Souligné (Ctrl+U)"; actif: entete.actif(".uno:Underline"); onClicked: entete.actions.lancer("souligne") }
                Outil {
                    picto: "M7.5 16L12 5l4.5 11 M9.2 12h5.6"
                    aide: "Couleur du texte"
                    ouvert: nuancierTexte.opened
                    onClicked: nuancierTexte.open()
                    Rectangle {
                        anchors.horizontalCenter: parent.horizontalCenter
                        y: parent.height - 8
                        width: 14; height: 3; radius: 1.5
                        color: entete.couleur(".uno:Color", Couleurs.texte)
                    }
                    Nuancier {
                        id: nuancierTexte
                        titre: "Couleur du texte"
                        rangees: entete.couleursTexte
                        aucune: "Automatique"
                        aucunePicto: "M7.5 16L12 5l4.5 11 M9.2 12h5.6"
                        onChoisie: c => entete.actions.colorerTexte(c)
                    }
                }
                Outil {
                    picto: "M9 15l-3 5h6l1-2 M9 15l8-8 3 3-8 8z"
                    aide: "Surligner"
                    ouvert: nuancierSurligner.opened
                    onClicked: nuancierSurligner.open()
                    Rectangle {
                        anchors.horizontalCenter: parent.horizontalCenter
                        y: parent.height - 7
                        width: 14; height: 3; radius: 1.5
                        color: "#E2A62B"
                    }
                    Nuancier {
                        id: nuancierSurligner
                        titre: "Surligner"
                        rangees: entete.couleursSurligner
                        aucune: "Sans surlignage"
                        aucunePicto: "M4 4h16v16H4z M4 20L20 4"
                        onChoisie: c => entete.actions.surligner(c)
                    }
                }
                TraitOutils {}
                Outil { picto: "M4 5h16v11H9l-5 4z M12 8v5 M9.5 10.5h5"; aide: "Commentaire (Ctrl+Alt+M)"; onClicked: entete.actions.lancer("commentaire") }
                Outil { picto: "M4 5h16v14H4z M4 16l5-5 4 4 3-3 4 4 M15 9v.1"; aide: "Image"; onClicked: entete.actions.lancer("image") }
                Outil { picto: "M4 5h16v14H4z M4 10h16 M10 5v14"; aide: "Tableau"; onClicked: entete.actions.lancer("tableau") }
                TraitOutils {}
                Outil {
                    id: boutonAligner
                    picto: entete.actif(".uno:CenterPara") ? entete.actions.pictos.centre : entete.actif(".uno:RightPara") ? entete.actions.pictos.droite
                           : entete.actif(".uno:JustifyPara") ? entete.actions.pictos.justifier : entete.actions.pictos.gauche
                    text: "▾"
                    police.pixelSize: 9
                    aide: "Alignement"
                    onClicked: menuAligner.popup(boutonAligner, 0, boutonAligner.height + 4)
                    MenuSama {
                        id: menuAligner
                        ElementMenu { cle: "gauche" }
                        ElementMenu { cle: "centre" }
                        ElementMenu { cle: "droite" }
                        ElementMenu { cle: "justifier" }
                    }
                }
                Outil { picto: entete.actions.pictos.puces; aide: "ListeOutils à puces"; actif: entete.actif(".uno:DefaultBullet"); onClicked: entete.actions.lancer("puces") }
                Outil { picto: entete.actions.pictos.numeros; aide: "ListeOutils numérotée"; actif: entete.actif(".uno:DefaultNumbering"); onClicked: entete.actions.lancer("numeros") }
            }
        }
    }
    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 0.5; color: Couleurs.bord }
}
