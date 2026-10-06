// Accueil de Sama Sheet (maquette « Sheet · Nouveau classeur ») : au lancement sans fichier. Deux départs à égalité,
// grille vierge et tableau vierge ; des modèles (tableaux et grilles), par catégorie ; les classeurs récents des
// Documents et des Téléchargements ; ouvrir un fichier. Échap ou Entrée : la grille vierge.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Rectangle {
    id: accueil
    color: Couleurs.fond
    focus: visible
    property string categorie: "Tous"
    property var recents: []
    readonly property string filtre: recherche.text.toLowerCase()

    signal grilleVierge()
    signal modele(string cle, string nom)
    signal ouvrir(string chemin)

    readonly property var modeles: [
        { cle: "cotisations", nom: "Cotisations", tableau: true, categorie: "Association", texte: "Tontine, association : qui a versé, qui doit encore.", colonnes: "Membre · Part · Versé · Reste · Date" },
        { cle: "stock", nom: "Ventes et stock", tableau: true, categorie: "Commerce", texte: "Ce qui entre, ce qui sort, ce qui reste en boutique.", colonnes: "Article · Prix · Entrées · Sorties · Stock" },
        { cle: "facture", nom: "Facture", tableau: false, categorie: "Commerce", texte: "En francs CFA, le total et le net à payer déjà calculés.", colonnes: "Désignation · Quantité · Prix · Montant" },
        { cle: "revient", nom: "Prix de revient", tableau: false, categorie: "Commerce", texte: "Ce que coûte ce qu'on fabrique, et le prix à demander.", colonnes: "Postes · montants · marge · prix conseillé" },
        { cle: "budget", nom: "Budget du mois", tableau: true, categorie: "Maison", texte: "Entrées et dépenses ; le solde se calcule tout seul.", colonnes: "Date · Libellé · Catégorie · Entrée · Dépense" },
        { cle: "notes", nom: "Notes de classe", tableau: true, categorie: "École", texte: "Moyennes et rangs, prêts pour le bulletin.", colonnes: "Élève · Devoirs · Composition · Moyenne · Rang" },
        { cle: "planning", nom: "Planning de la semaine", tableau: false, categorie: "Association", texte: "Qui fait quoi, jour par jour.", colonnes: "Lundi à dimanche · matin, midi, soir" },
        { cle: "personnes", nom: "Liste de personnes", tableau: true, categorie: "Association", texte: "Noms, téléphones, quartiers : un annuaire qui se trie.", colonnes: "Nom · Téléphone · Quartier · Rôle" }
    ]
    readonly property var modelesVisibles: modeles.filter(function (m) {
        return (categorie === "Tous" || m.categorie === categorie)
               && (!filtre || (m.nom + " " + m.texte + " " + m.colonnes).toLowerCase().indexOf(filtre) >= 0)
    })
    readonly property var recentsVisibles: recents.filter(function (r) { return !filtre || r.nom.toLowerCase().indexOf(filtre) >= 0 })

    Keys.onEscapePressed: grilleVierge()
    Keys.onReturnPressed: grilleVierge()

    // Classeurs récents : Documents, Téléchargements et Bureau, du plus récent au plus ancien
    Commande { id: commande }
    function lireRecents() {
        commande.lancer("for d in \"$(xdg-user-dir DOCUMENTS)\" \"$(xdg-user-dir DOWNLOAD)\" \"$(xdg-user-dir DESKTOP)\"; do "
                        + "[ -d \"$d\" ] && find \"$d\" -maxdepth 2 -type f \\( -iname '*.xlsx' -o -iname '*.xls' -o -iname '*.ods' -o -iname '*.csv' \\) "
                        + "-printf '%T@\\t%h\\t%p\\n'; done 2>/dev/null | sort -rn | head -8", function (sortie) {
            var maintenant = new Date()
            accueil.recents = sortie.split("\n").filter(function (l) { return l.indexOf("\t") > 0 }).map(function (l) {
                var p = l.split("\t")
                var quand = new Date(Number(p[0]) * 1000)
                var jours = Math.floor((new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate()) - new Date(quand.getFullYear(), quand.getMonth(), quand.getDate())) / 86400000)
                var texte = jours <= 0 ? "Aujourd'hui, " + Qt.formatTime(quand, "hh:mm") : jours === 1 ? "Hier"
                            : jours < 7 ? Qt.locale("fr_FR").dayName(quand.getDay()) : Qt.formatDate(quand, "d/MM/yyyy")
                return { chemin: p[2], nom: p[2].split("/").pop(), quand: texte + " · " + p[1].split("/").pop() }
            })
        })
    }
    Component.onCompleted: lireRecents()

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // ——— Récents ———
        Rectangle {
            Layout.preferredWidth: 290
            Layout.fillHeight: true
            color: "transparent"
            Rectangle { anchors.right: parent.right; width: 0.5; height: parent.height; color: Couleurs.bord }
            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 16
                anchors.topMargin: 22
                spacing: 2
                Text { text: "Récents"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte2; Layout.leftMargin: 10; Layout.bottomMargin: 8 }
                Repeater {
                    model: accueil.recentsVisibles
                    delegate: QQC2.AbstractButton {
                        id: recent
                        Layout.fillWidth: true
                        implicitHeight: 52
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        onClicked: accueil.ouvrir(modelData.chemin)
                        background: Rectangle { radius: 12; color: recent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent" }
                        contentItem: RowLayout {
                            spacing: 12
                            Item { Layout.preferredWidth: 2 }
                            Rectangle {
                                Layout.preferredWidth: 32; Layout.preferredHeight: 32
                                radius: 9
                                color: Couleurs.champ
                                border.width: 0.5
                                border.color: Couleurs.bord
                                Picto { anchors.centerIn: parent; trace: "M5 4h14v16H5z M5 9h14 M5 14h14 M10 4v16"; encre: fenetre.vert }
                            }
                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 1
                                Text { Layout.fillWidth: true; text: modelData.nom; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideMiddle }
                                Text { Layout.fillWidth: true; text: modelData.quand; font.pixelSize: 11; color: Couleurs.texte2; elide: Text.ElideRight }
                            }
                        }
                    }
                }
                Text {
                    visible: accueil.recents.length === 0
                    text: "Les classeurs de vos Documents et Téléchargements apparaîtront ici."
                    font.pixelSize: 12
                    color: Couleurs.texte3
                    wrapMode: Text.Wrap
                    Layout.fillWidth: true
                    Layout.leftMargin: 10
                }
                Item { Layout.fillHeight: true }
                Outil {
                    Layout.fillWidth: true
                    implicitHeight: 40
                    text: "Ouvrir un fichier…"
                    picto: "M3 7h6l2 2h10v10H3z"
                    aide: "Un classeur Excel (.xlsx, .xls), OpenDocument (.ods) ou CSV (Ctrl+O)"
                    background: Rectangle { radius: 10; color: Couleurs.champ; border.width: 0.5; border.color: parent.hovered ? fenetre.vert : Couleurs.bord }
                    onClicked: fenetre.ouvrirDialogue("ouvrir")
                }
            }
        }

        // ——— Nouveau classeur ———
        QQC2.ScrollView {
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: availableWidth
            ColumnLayout {
                width: parent.width
                spacing: 22
                Item { Layout.preferredHeight: 10 }
                RowLayout {
                    Layout.leftMargin: 44
                    Layout.rightMargin: 44
                    spacing: 16
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 6
                        Text { text: "Nouveau classeur"; font.pixelSize: 32; font.weight: Font.DemiBold; color: Couleurs.texte }
                        Text {
                            Layout.fillWidth: true
                            text: "Une grille libre pour calculer, un tableau prêt à remplir, ou un modèle. On peut toujours passer de l'un à l'autre."
                            font.pixelSize: 14
                            color: Couleurs.texte2
                            wrapMode: Text.Wrap
                        }
                    }
                    // Chercher
                    Rectangle {
                        Layout.preferredWidth: 300
                        Layout.preferredHeight: 34
                        Layout.alignment: Qt.AlignTop
                        radius: 9
                        color: Couleurs.champ
                        border.width: recherche.activeFocus ? 1.5 : 0.5
                        border.color: recherche.activeFocus ? fenetre.vert : Couleurs.bord
                        RowLayout {
                            anchors.fill: parent
                            anchors.leftMargin: 10
                            anchors.rightMargin: 10
                            spacing: 8
                            Picto { width: 15; height: 15; trace: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4" }
                            TextInput {
                                id: recherche
                                Layout.fillWidth: true
                                font.pixelSize: 13
                                color: Couleurs.texte
                                clip: true
                                Accessible.name: "Chercher un classeur ou un modèle"
                                Keys.onEscapePressed: { if (text) text = ""; else accueil.grilleVierge() }
                                Text { visible: !recherche.text; text: "Chercher un classeur ou un modèle"; font.pixelSize: 13; color: Couleurs.texte3 }
                            }
                        }
                    }
                }

                // Deux départs, à égalité
                GridLayout {
                    Layout.leftMargin: 44
                    Layout.rightMargin: 44
                    Layout.fillWidth: true
                    columns: 2
                    columnSpacing: 16
                    Depart {
                        Layout.fillWidth: true
                        titre: "Grille vierge"
                        texte: "Des cases et des formules, comme dans Excel : on calcule librement, où l'on veut."
                        indice: "Entrée"
                        grille: true
                        onClicked: accueil.grilleVierge()
                    }
                    Depart {
                        Layout.fillWidth: true
                        titre: "Tableau vierge"
                        texte: "Des colonnes nommées, des filtres et une ligne des totaux. Ctrl+T en fait autant dans une grille."
                        indice: "Tableau"
                        grille: false
                        onClicked: accueil.modele("tableau", "Nouveau tableau")
                    }
                }

                // Modèles
                RowLayout {
                    Layout.leftMargin: 44
                    Layout.rightMargin: 44
                    spacing: 6
                    Text { text: "Partir d'un modèle"; font.pixelSize: 15; font.weight: Font.DemiBold; color: Couleurs.texte; Layout.rightMargin: 10 }
                    Item { Layout.fillWidth: true }
                    Repeater {
                        model: ["Tous", "Commerce", "Association", "École", "Maison"]
                        delegate: QQC2.AbstractButton {
                            id: puce
                            readonly property bool choisie: accueil.categorie === modelData
                            implicitHeight: 28
                            implicitWidth: nomPuce.implicitWidth + 24
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: accueil.categorie = modelData
                            background: Rectangle { radius: 8; color: puce.choisie ? fenetre.vertFond : puce.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent" }
                            contentItem: Text { id: nomPuce; text: modelData; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; font.pixelSize: 12; font.weight: puce.choisie ? Font.DemiBold : Font.Normal; color: puce.choisie ? fenetre.vertEncre : Couleurs.texte2 }
                        }
                    }
                }
                GridLayout {
                    Layout.leftMargin: 44
                    Layout.rightMargin: 44
                    Layout.fillWidth: true
                    columns: Math.max(2, Math.min(4, Math.floor((accueil.width - 290 - 88) / 230)))
                    columnSpacing: 12
                    rowSpacing: 12
                    Repeater {
                        model: accueil.modelesVisibles
                        delegate: QQC2.AbstractButton {
                            id: carte
                            Layout.fillWidth: true
                            Layout.preferredHeight: 128
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: accueil.modele(modelData.cle, modelData.nom)
                            background: Rectangle { radius: 14; color: Couleurs.champ; border.width: carte.hovered ? 1.5 : 0.5; border.color: carte.hovered ? fenetre.vert : Couleurs.bord }
                            contentItem: ColumnLayout {
                                spacing: 6
                                RowLayout {
                                    Layout.fillWidth: true
                                    Layout.topMargin: 4
                                    Layout.leftMargin: 4
                                    Layout.rightMargin: 4
                                    Text { Layout.fillWidth: true; text: modelData.nom; font.pixelSize: 14; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideRight }
                                    Rectangle {
                                        Layout.preferredHeight: 20
                                        Layout.preferredWidth: etiquette.implicitWidth + 14
                                        radius: 6
                                        color: modelData.tableau ? fenetre.vertFond : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06)
                                        Text { id: etiquette; anchors.centerIn: parent; text: modelData.tableau ? "Tableau" : "Grille"; font.pixelSize: 11; font.weight: Font.DemiBold; color: modelData.tableau ? fenetre.vertEncre : Couleurs.texte2 }
                                    }
                                }
                                Text { Layout.fillWidth: true; Layout.leftMargin: 4; Layout.rightMargin: 4; text: modelData.texte; font.pixelSize: 12; color: Couleurs.texte2; wrapMode: Text.Wrap; maximumLineCount: 2; elide: Text.ElideRight }
                                Item { Layout.fillHeight: true }
                                Text { Layout.fillWidth: true; Layout.leftMargin: 4; Layout.rightMargin: 4; Layout.bottomMargin: 2; text: modelData.colonnes; font.pixelSize: 11; color: Couleurs.texte3; elide: Text.ElideRight }
                            }
                            padding: 12
                        }
                    }
                }
                Text {
                    visible: accueil.modelesVisibles.length === 0
                    Layout.leftMargin: 44
                    text: "Aucun modèle ne correspond."
                    font.pixelSize: 13
                    color: Couleurs.texte3
                }
                RowLayout {
                    Layout.leftMargin: 44
                    Layout.rightMargin: 44
                    Layout.bottomMargin: 24
                    spacing: 8
                    Picto { width: 15; height: 15; trace: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8v0" }
                    Text { text: "Un fichier Excel reçu s'ouvre tel quel, ses tableaux avec."; font.pixelSize: 12; color: Couleurs.texte2 }
                }
            }
        }
    }

    // Carte d'un départ : un petit dessin (grille ou tableau), un titre, un texte
    component Depart: QQC2.AbstractButton {
        id: depart
        property string titre
        property string texte
        property string indice
        property bool grille: true
        implicitHeight: 112
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        background: Rectangle { radius: 16; color: Couleurs.champ; border.width: depart.hovered ? 1.5 : 0.5; border.color: depart.hovered ? fenetre.vert : Couleurs.bord }
        contentItem: RowLayout {
            spacing: 18
            Item { Layout.preferredWidth: 2 }
            // Dessin
            Rectangle {
                Layout.preferredWidth: 120
                Layout.preferredHeight: 80
                radius: 10
                color: "#FFFFFF"
                border.width: 0.5
                border.color: Couleurs.bord
                clip: true
                Grid {
                    visible: depart.grille
                    anchors.fill: parent
                    columns: 4
                    Repeater {
                        model: 20
                        Rectangle { width: 30; height: 16; color: "transparent"; border.width: 0.5; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.15) }
                    }
                }
                Rectangle { visible: depart.grille; x: 30; y: 32; width: 31; height: 17; color: "transparent"; border.width: 2; border.color: fenetre.vert }
                Column {
                    visible: !depart.grille
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 4
                    Rectangle { width: parent.width; height: 11; radius: 3; color: fenetre.vert }
                    Repeater {
                        model: 3
                        Rectangle { width: parent.width; height: 8; radius: 2; color: index % 2 ? fenetre.vertFond : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.07) }
                    }
                    Rectangle { width: parent.width; height: 10; radius: 2; color: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.22) }
                }
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4
                Text { text: depart.titre; font.pixelSize: 17; font.weight: Font.DemiBold; color: Couleurs.texte }
                Text { Layout.fillWidth: true; text: depart.texte; font.pixelSize: 12; color: Couleurs.texte2; wrapMode: Text.Wrap }
                Text { text: depart.indice; font.pixelSize: 11; font.weight: Font.DemiBold; color: fenetre.vertEncre; Layout.topMargin: 2 }
            }
            Item { Layout.preferredWidth: 4 }
        }
    }
}
