// « Que voulez-vous faire ? » (Ctrl+K, ou Alt+/) : on écrit ce qu'on cherche (« trier », « fcfa », « somme si »…), Sama
// propose les commandes et les fonctions ; et, d'après le tableau (maquette « Sheet · Que voulez-vous faire ? »), ce qui
// se rapporte à la colonne qu'on nomme ou à ce qu'on veut (« trier par reste », « qui doivent encore ») : ranger,
// calculer, mettre en forme, apprendre, avec ce que ça concerne (« 3 membres ») et ce qui est déjà fait. Flèches pour
// choisir, Entrée pour faire, Échap pour revenir à la grille.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "Fonctions.js" as Fonctions

Item {
    id: recherche
    implicitWidth: 380
    implicitHeight: 32
    property int choisi: 0
    // Le tableau dont on parle : celui de la case, sinon le premier de la feuille ; ses lignes, lues à l'ouverture
    readonly property var tableau: fenetre.tableaux.courant || (fenetre.tableaux.visibles.length ? fenetre.tableaux.visibles[0] : null)
    property var donnees: null
    function lireTableau() {
        donnees = null
        var t = tableau
        if (!t) return
        fenetre.tableaux.appeler("Lignes", [t.nom], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{" || !recherche.tableau || recherche.tableau.nom !== t.nom) return
            try { recherche.donnees = JSON.parse(v) } catch (e) { }
        })
    }
    readonly property var propositions: champ.activeFocus && tableau && donnees
                                        ? Fonctions.propositions(champ.text, tableau, donnees,
                                                                 fenetre.tableaux.courant ? fenetre.doc.colonne - tableau.c1 : -1).slice(0, 9)
                                        : []
    readonly property var commandes: champ.activeFocus ? fenetre.actions.chercher(champ.text).slice(0, propositions.length ? 4 : 9) : []
    // (tout, dans l'ordre : les propositions du tableau, puis les commandes)
    readonly property var resultats: propositions.concat(commandes.map(function (c) { return Object.assign({ groupe: propositions.length ? "Commandes" : "" }, c) }))
    onResultatsChanged: choisi = 0

    function ouvrir() { champ.forceActiveFocus(); champ.selectAll() }
    function fermer() { champ.text = ""; fenetre.doc.forceActiveFocus() }
    function faire(r) {
        if (!r) return
        fermer()
        if (r.action) executer(r)
        else r.faire()
    }
    // Une proposition du tableau
    function executer(p) {
        var t = tableau, tb = fenetre.tableaux, a = p.args
        if (!t) return
        if (p.deja) { message.montrer("C'est déjà fait"); return }
        if (p.action === "trier") tb.trier(t, a.rang, a.croissant)
        else if (p.action === "filtrerValeur") tb.filtrer(t, a.rang, [a.valeur])
        else if (p.action === "toutAfficher") tb.toutAfficher(t)
        else if (p.action === "ocre") tb.regler(t, "ocre", "oui")
        else if (p.action === "formater") tb.formaterColonne(t, a.nom, a.genre, function () { tb.rafraichir() })
        else if (p.action === "aide") fenetre.ouvrirAide(a.article)
        else if (p.action === "filtrerSuperieur")
            tb.appeler("SamaPropositions.FiltrerSuperieur", [t.nom, String(a.rang), "0"], function (ok, v) {
                if (!tb.verifier(ok, v)) return
                tb.apresFiltre()
                message.montrer("Seulement ceux qui doivent encore · clic droit › Afficher toutes les lignes pour revenir")
            })
        else if (p.action === "bilan")
            tb.appeler("SamaPropositions.AjouterBilan", [t.nom, a.libelle, a.formule], function (ok, v) {
                if (!tb.verifier(ok, v)) return
                fenetre.doc.allerA(v)
                message.montrer("Ajouté sous le tableau, en " + v + " · Ctrl+Z pour revenir")
            })
        else if (p.action === "colonne")
            tb.appeler("SamaPropositions.ColonneCalculee", [t.nom, a.titre, a.formule, a.genre], function (ok, v) {
                if (!tb.verifier(ok, v)) return
                tb.rafraichir()
                fenetre.doc.allerA(v)
                message.montrer("Colonne « " + a.titre + " » ajoutée au bout du tableau · Ctrl+Z pour revenir")
            })
    }
    // (les pictogrammes des propositions)
    readonly property var pictos: ({ trier: "M6 4v15 M3 16l3 3 3-3 M12 7h8 M12 12h6 M12 17h4", filtrerSuperieur: "M4 5h16l-6 7.5V18l-4 2v-7.5z",
                                     filtrerValeur: "M4 5h16l-6 7.5V18l-4 2v-7.5z", toutAfficher: "M4 5h16l-6 7.5V18l-4 2v-7.5z M17 15l4 4 M21 15l-4 4",
                                     colonne: "M4 5h16v14H4z M14 5v14 M17 10v4 M15 12h4", ocre: "M12 4a8 8 0 1 0 0 16c1 0 1.5-.8 1.5-1.5 0-1-1-1.3-1-2.3 0-.8.7-1.2 1.5-1.2H16a4 4 0 0 0 4-4c0-3.9-3.6-7-8-7z",
                                     formater: "M12 4a8 8 0 1 0 0 16c1 0 1.5-.8 1.5-1.5 0-1-1-1.3-1-2.3 0-.8.7-1.2 1.5-1.2H16a4 4 0 0 0 4-4c0-3.9-3.6-7-8-7z",
                                     aide: "M5 5a2 2 0 0 1 2-2h12v16H7a2 2 0 0 0-2 2z M5 19V5 M9 7h6" })

    Rectangle {
        anchors.fill: parent
        radius: 9
        color: champ.activeFocus ? Couleurs.champ : Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.25 : 0.055)
        border.width: champ.activeFocus ? 1.5 : 0
        border.color: fenetre.accent
    }
    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 10
        anchors.rightMargin: 6
        spacing: 8
        Picto { width: 15; height: 15; trace: "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M16 16l4 4"; encre: Couleurs.texte2 }
        TextInput {
            id: champ
            Layout.fillWidth: true
            verticalAlignment: TextInput.AlignVCenter
            font.pixelSize: 13
            color: Couleurs.texte
            clip: true
            selectByMouse: true
            Accessible.name: "Que voulez-vous faire ?"
            Text {
                anchors.verticalCenter: parent.verticalCenter
                visible: !champ.text
                text: "Que voulez-vous faire ? Une commande, une fonction…"
                font.pixelSize: 13
                color: Couleurs.texte3
            }
            Keys.onDownPressed: recherche.choisi = Math.min(recherche.choisi + 1, recherche.resultats.length - 1)
            Keys.onUpPressed: recherche.choisi = Math.max(recherche.choisi - 1, 0)
            Keys.onReturnPressed: recherche.faire(recherche.resultats[recherche.choisi])
            Keys.onEnterPressed: recherche.faire(recherche.resultats[recherche.choisi])
            Keys.onEscapePressed: recherche.fermer()
            onActiveFocusChanged: if (activeFocus) recherche.lireTableau()
        }
        Text {
            visible: !champ.activeFocus
            text: "Ctrl+K"
            font.pixelSize: 11
            font.weight: Font.DemiBold
            color: Couleurs.texte2
            Rectangle { anchors.fill: parent; anchors.margins: -3; anchors.leftMargin: -5; anchors.rightMargin: -5; radius: 5; color: Couleurs.champ; z: -1 }
        }
    }

    // Les propositions
    QQC2.Popup {
        y: recherche.height + 6
        x: 0
        width: Math.max(recherche.width, recherche.propositions.length ? 560 : 460)
        visible: champ.activeFocus && recherche.resultats.length > 0
        closePolicy: QQC2.Popup.NoAutoClose
        focus: false
        padding: 6
        background: Item {
            Rectangle { anchors.fill: parent; anchors.topMargin: 3; anchors.margins: -1; radius: 13; color: Qt.rgba(40 / 255, 30 / 255, 20 / 255, 0.06) }
            Rectangle { anchors.fill: parent; radius: 12; color: Couleurs.champ; border.width: 0.5; border.color: Couleurs.bord }
        }
        contentItem: ColumnLayout {
            spacing: 0
            Text {
                visible: !champ.text
                text: "Souvent utilisées"
                font.pixelSize: 11
                font.weight: Font.DemiBold
                color: Couleurs.texte2
                Layout.margins: 6
            }
            Repeater {
                model: recherche.resultats
                delegate: ColumnLayout {
                    id: ligne
                    required property var modelData
                    required property int index
                    readonly property bool choisie: index === recherche.choisi
                    readonly property bool nouveauGroupe: modelData.groupe && (index === 0 || recherche.resultats[index - 1].groupe !== modelData.groupe)
                    Layout.fillWidth: true
                    spacing: 0
                    // (le titre du groupe : Ranger, Calculer, Mettre en forme, Apprendre, Commandes)
                    Text {
                        visible: ligne.nouveauGroupe
                        Layout.leftMargin: 10
                        Layout.topMargin: ligne.index === 0 ? 4 : 10
                        Layout.bottomMargin: 4
                        text: ligne.modelData.groupe || ""
                        font.pixelSize: 11
                        font.weight: Font.DemiBold
                        color: Couleurs.texte3
                    }
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 36
                        radius: 8
                        color: ligne.choisie ? fenetre.accentFond : zone.containsMouse ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : "transparent"
                        RowLayout {
                            anchors.fill: parent
                            anchors.leftMargin: 10
                            anchors.rightMargin: 10
                            spacing: 10
                            // (pictogramme d'une proposition ; Σ pour un bilan)
                            Item {
                                visible: !!ligne.modelData.action
                                Layout.preferredWidth: 16
                                Layout.preferredHeight: 16
                                Picto {
                                    visible: ligne.modelData.action !== "bilan"
                                    anchors.fill: parent
                                    trace: recherche.pictos[ligne.modelData.action] || ""
                                    encre: ligne.choisie ? fenetre.accentEncre : Couleurs.texte2
                                }
                                Text {
                                    visible: ligne.modelData.action === "bilan"
                                    anchors.centerIn: parent
                                    text: "Σ"
                                    font.pixelSize: 14
                                    font.weight: Font.DemiBold
                                    color: ligne.choisie ? fenetre.accentEncre : Couleurs.texte2
                                }
                            }
                            Text {
                                Layout.fillWidth: true
                                text: ligne.modelData.action ? ligne.modelData.html : ligne.modelData.nom
                                textFormat: ligne.modelData.action ? Text.StyledText : Text.PlainText
                                font.pixelSize: 13
                                font.weight: !ligne.modelData.action && ligne.choisie ? Font.DemiBold : Font.Normal
                                color: ligne.choisie ? fenetre.accentEncre : Couleurs.texte
                                elide: Text.ElideRight
                            }
                            // Déjà fait
                            RowLayout {
                                visible: !!ligne.modelData.deja
                                spacing: 4
                                Picto { width: 13; height: 13; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: fenetre.vertEncre; trait: 2.2 }
                                Text { text: "Déjà fait"; font.pixelSize: 11; font.weight: Font.DemiBold; color: fenetre.vertEncre }
                            }
                            // Ce que ça concerne (« 3 membres »), le menu ou le raccourci d'une commande
                            Text {
                                visible: !ligne.modelData.deja && text !== ""
                                Layout.maximumWidth: 200
                                text: ligne.modelData.action ? ligne.modelData.badge : ligne.modelData.raccourci ? ligne.modelData.raccourci : ligne.modelData.menu
                                font.pixelSize: 11
                                font.weight: ligne.modelData.action ? Font.DemiBold : Font.Normal
                                color: ligne.modelData.action ? Couleurs.texte2 : Couleurs.texte3
                                elide: Text.ElideRight
                            }
                            // (Entrée, sur celle qu'on a choisie)
                            Rectangle {
                                visible: ligne.choisie && champ.text !== ""
                                Layout.preferredHeight: 18
                                Layout.preferredWidth: entree.implicitWidth + 10
                                radius: 5
                                color: "transparent"
                                border.width: 0.5
                                border.color: Couleurs.bord
                                Text { id: entree; anchors.centerIn: parent; text: "Entrée"; font.pixelSize: 10; color: Couleurs.texte2 }
                            }
                        }
                        MouseArea {
                            id: zone
                            anchors.fill: parent
                            hoverEnabled: true
                            onClicked: recherche.faire(ligne.modelData)
                        }
                    }
                }
            }
            // En bas : comment s'en servir, et que rien ne sort de l'ordinateur
            Rectangle { Layout.fillWidth: true; Layout.topMargin: 6; Layout.preferredHeight: 0.5; color: Couleurs.bord }
            RowLayout {
                Layout.fillWidth: true
                Layout.leftMargin: 10
                Layout.rightMargin: 10
                Layout.topMargin: 6
                Layout.bottomMargin: 2
                spacing: 12
                Text { text: "↑ ↓ pour choisir"; font.pixelSize: 11; color: Couleurs.texte3 }
                Text { text: "Entrée pour faire"; font.pixelSize: 11; color: Couleurs.texte3 }
                Text { text: "Échap pour fermer"; font.pixelSize: 11; color: Couleurs.texte3 }
                Item { Layout.fillWidth: true }
                Text { text: "Hors connexion, sur cet ordinateur"; font.pixelSize: 11; color: Couleurs.texte3 }
            }
        }
    }
}
