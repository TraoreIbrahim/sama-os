// La vue Image d'un tableau, et Envoyer (maquette « Sheet · En image, et Envoyer ») : Sama propose une image d'après
// les colonnes (versé et reste membre par membre, soldé ou pas, jour après jour ; une colonne de nombres ; la
// répartition d'une colonne à choix), avec sa phrase ; on peut en changer. À droite, Envoyer ce bilan : l'aperçu à la
// taille d'un téléphone, la forme (image, PDF à imprimer, fichier Excel), ce qui part (numéros cachés, conseillé
// contre les arnaques ; la date du jour ; le tableau complet), puis Copier l'image ou Enregistrer.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtCore
import "../reglages"
import "Fonctions.js" as Fonctions

Rectangle {
    id: vue
    color: Couleurs.fond
    property var tableau: null
    readonly property var tableaux: fenetre.tableaux
    readonly property var doc: fenetre.doc

    // ——— Lire : les lignes du tableau, et les images qu'on en tire ———
    property var donnees: null
    property var choix: []
    property string cleChoisie: ""
    readonly property var image: {
        for (var i = 0; i < choix.length; i++) if (choix[i].cle === cleChoisie) return choix[i]
        return choix.length ? choix[0] : null
    }
    function ouvrir(t) {
        tableau = t
        cleChoisie = ""
        lire()
    }
    function lire() {
        if (!tableau) return
        var nom = tableau.nom
        tableaux.appeler("Lignes", [nom], function (ok, v) {
            if (!ok || !v || v.charAt(0) !== "{" || !vue.tableau || vue.tableau.nom !== nom) return
            try { vue.donnees = JSON.parse(v) } catch (e) { return }
            vue.choix = Fonctions.images(vue.donnees)
        })
    }
    Timer { id: relire; interval: 600; onTriggered: vue.lire() }
    Connections {
        target: vue.doc
        function onRevisionChanged() { if (vue.visible) relire.restart() }
    }
    readonly property real plusGrande: {
        var m = 0
        if (!image) return 1
        for (var i = 0; i < image.barres.length; i++) {
            var s = 0
            for (var k = 0; k < image.barres[i].parts.length; k++) s += image.barres[i].parts[k].v
            m = Math.max(m, s)
        }
        return m || 1
    }

    // ——— Ce qui part ———
    property string forme: "image"
    property bool cacherNumeros: true
    property bool avecDate: true
    property bool avecTableau: false
    function dateDuJour() { return new Date().toLocaleDateString(Qt.locale("fr_FR"), "d MMMM yyyy") }
    readonly property string titreTableau: tableau ? tableau.nom.replace(/_/g, " ") : ""
    readonly property string entete: titreTableau + " · " + new Date().toLocaleDateString(Qt.locale("fr_FR"), "MMMM yyyy")
    readonly property string pied: (avecDate ? dateDuJour() + " · " : "") + "fait avec Sama Sheet"
    // Le tableau complet, numéros de téléphone masqués si on le demande
    readonly property var tableauJoint: {
        if (!avecTableau || !donnees) return null
        var cols = donnees.colonnes, titres = cols.map(function (c) { return c.nom }), lignes = []
        for (var i = 0; i < donnees.lignes.length; i++) {
            var r = donnees.lignes[i]
            if (r.vide) continue
            lignes.push(r.v.map(function (v, k) { return vue.cacherNumeros && Fonctions.estTelephone(cols[k].nom) && v ? Fonctions.masquer(v) : v }))
        }
        return { titres: titres, lignes: lignes }
    }
    readonly property bool aDesNumeros: donnees !== null && donnees.colonnes.some(function (c) { return Fonctions.estTelephone(c.nom) })
    function nomFichier() {
        return titreTableau + " " + new Date().toLocaleDateString(Qt.locale("fr_FR"), "d MMM yyyy").replace(/\./g, "")
    }

    // ——— Enregistrer, copier ———
    function enregistrer() {
        if (!tableau) return
        if (forme === "excel") {
            var cheminExcel = doc.cheminLibre(StandardPaths.writableLocation(StandardPaths.DocumentsLocation), nomFichier(), "xlsx")
            tableaux.appeler("SamaImage.Copie", [tableau.nom, cheminExcel, cacherNumeros ? "1" : "0"], function (ok, v) {
                if (tableaux.verifier(ok, v)) message.montrer("Enregistré dans Documents : " + cheminExcel.split("/").pop() + (cacherNumeros && aDesNumeros ? " (numéros masqués)" : ""))
            })
            return
        }
        if (!image) return
        affiche.grabToImage(function (r) {
            if (vue.forme === "pdf") {
                var temporaire = vue.doc.cheminLibre(StandardPaths.writableLocation(StandardPaths.TempLocation), "sama-envoi", "png")
                if (!r.saveToFile(temporaire)) { message.montrer("L'image n'a pas pu être faite"); return }
                var cheminPdf = vue.doc.cheminLibre(StandardPaths.writableLocation(StandardPaths.DocumentsLocation), vue.nomFichier(), "pdf")
                if (vue.doc.imageEnPdf(temporaire, cheminPdf, vue.image.titre)) message.montrer("PDF enregistré dans Documents : " + cheminPdf.split("/").pop())
                else message.montrer("Le PDF n'a pas pu être fait")
            } else {
                var cheminImage = vue.doc.cheminLibre(StandardPaths.writableLocation(StandardPaths.PicturesLocation), vue.nomFichier(), "png")
                if (r.saveToFile(cheminImage)) message.montrer("Image enregistrée dans Images : " + cheminImage.split("/").pop())
                else message.montrer("L'image n'a pas pu être enregistrée")
            }
        })
    }
    function copier() {
        if (!image) return
        affiche.grabToImage(function (r) {
            var temporaire = vue.doc.cheminLibre(StandardPaths.writableLocation(StandardPaths.TempLocation), "sama-envoi", "png")
            if (r.saveToFile(temporaire) && vue.doc.copierImage(temporaire)) message.montrer("Image copiée : collez-la dans une discussion (Ctrl+V)")
            else message.montrer("L'image n'a pas pu être copiée")
        })
    }
    Keys.onEscapePressed: fenetre.voirGrille()

    // ——— Pièces ———
    component Case: QQC2.AbstractButton {
        // Case à cocher de Sama (une ligne, et une explication dessous s'il le faut)
        id: caseACocher
        property string detail: ""
        checkable: true
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        Layout.fillWidth: true
        implicitHeight: contenuCase.implicitHeight + 14
        background: null
        contentItem: RowLayout {
            id: contenuCase
            spacing: 10
            Rectangle {
                Layout.alignment: Qt.AlignTop
                Layout.topMargin: 2
                Layout.preferredWidth: 18; Layout.preferredHeight: 18
                radius: 5
                color: caseACocher.checked ? fenetre.accent : Couleurs.champ
                border.width: caseACocher.checked ? 0 : 1.5
                border.color: caseACocher.hovered ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.3)
                Picto { anchors.centerIn: parent; width: 13; height: 13; visible: caseACocher.checked; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: "#FFFFFF"; trait: 2.6 }
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text { Layout.fillWidth: true; text: caseACocher.text; font.pixelSize: 14; color: Couleurs.texte; wrapMode: Text.Wrap }
                Text { visible: caseACocher.detail !== ""; Layout.fillWidth: true; text: caseACocher.detail; font.pixelSize: 12; color: Couleurs.texte2; wrapMode: Text.Wrap }
            }
        }
    }
    component Rond: QQC2.AbstractButton {
        // Bouton radio de Sama
        id: rond
        property bool choisi: false
        property string detail: ""
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        Layout.fillWidth: true
        implicitHeight: contenuRond.implicitHeight + 8
        background: null
        contentItem: RowLayout {
            id: contenuRond
            spacing: 9
            Rectangle {
                Layout.alignment: Qt.AlignTop
                Layout.topMargin: 2
                Layout.preferredWidth: 18; Layout.preferredHeight: 18
                radius: 9
                color: Couleurs.champ
                border.width: rond.choisi ? 5 : 1.5
                border.color: rond.choisi ? fenetre.accent : rond.hovered ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.3)
            }
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text { Layout.fillWidth: true; text: rond.text; font.pixelSize: 14; color: Couleurs.texte }
                Text { visible: rond.detail !== ""; Layout.fillWidth: true; text: rond.detail; font.pixelSize: 12; color: Couleurs.texte2; wrapMode: Text.Wrap }
            }
        }
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // ——— L'image du tableau, avec sa phrase ———
        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: Couleurs.champ
            QQC2.ScrollView {
                id: defilement
                anchors.fill: parent
                contentWidth: availableWidth
                QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff
                ColumnLayout {
                    width: defilement.availableWidth
                    spacing: 18
                    ColumnLayout {
                        Layout.fillWidth: true
                        Layout.leftMargin: 36
                        Layout.rightMargin: 36
                        Layout.topMargin: 28
                        spacing: 6
                        Text {
                            Layout.fillWidth: true
                            text: vue.image ? vue.image.titre : ""
                            font.pixelSize: 30
                            font.weight: Font.DemiBold
                            font.letterSpacing: -0.3
                            color: Couleurs.texte
                            wrapMode: Text.Wrap
                        }
                        Text { Layout.fillWidth: true; text: vue.image ? vue.image.sousTitre : ""; font.pixelSize: 14; color: Couleurs.texte2; wrapMode: Text.Wrap }
                    }
                    Row {
                        Layout.leftMargin: 36
                        visible: vue.image !== null && vue.image.legende.length > 0
                        spacing: 18
                        Repeater {
                            model: vue.image ? vue.image.legende : []
                            delegate: Row {
                                spacing: 6
                                Rectangle { width: 12; height: 12; radius: 3; color: modelData[1]; anchors.verticalCenter: parent.verticalCenter }
                                Text { text: modelData[0]; font.pixelSize: 13; color: Couleurs.texte; anchors.verticalCenter: parent.verticalCenter }
                            }
                        }
                    }
                    // Les barres
                    Column {
                        Layout.leftMargin: 36
                        Layout.rightMargin: 36
                        spacing: 7
                        Repeater {
                            model: vue.image ? vue.image.barres : []
                            delegate: Row {
                                height: 26
                                spacing: 12
                                Text { width: 150; anchors.verticalCenter: parent.verticalCenter; horizontalAlignment: Text.AlignRight; text: modelData.nom; font.pixelSize: 13; color: Couleurs.texte; elide: Text.ElideRight }
                                Rectangle {
                                    readonly property real largeur: Math.max(200, Math.min(440, defilement.availableWidth - 72 - 150 - 24 - 150))
                                    width: largeur
                                    height: 18
                                    radius: 4
                                    anchors.verticalCenter: parent.verticalCenter
                                    color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
                                    clip: true
                                    Row {
                                        Repeater {
                                            model: modelData.parts
                                            delegate: Rectangle { width: parent.parent.largeur * modelData.v / vue.plusGrande; height: 18; color: modelData.c }
                                        }
                                    }
                                }
                                Text { anchors.verticalCenter: parent.verticalCenter; text: modelData.texte; font.pixelSize: 12; font.weight: Font.DemiBold; font.features: { "tnum": 1 }; color: modelData.encre === "#2F6B57" ? fenetre.vertEncre : Couleurs.sombre ? Couleurs.texte : modelData.encre }
                            }
                        }
                    }
                    // Rien à montrer
                    Text {
                        visible: vue.donnees !== null && vue.choix.length === 0
                        Layout.leftMargin: 36
                        Layout.rightMargin: 36
                        Layout.fillWidth: true
                        text: "Pas encore d'image pour ce tableau : il en faut des nombres (des montants, des quantités) ou une colonne dont les réponses reviennent (Espèces, Mobile money…)."
                        font.pixelSize: 14
                        color: Couleurs.texte2
                        wrapMode: Text.Wrap
                    }
                    // Autre façon de voir
                    Flow {
                        visible: vue.choix.length > 1
                        Layout.fillWidth: true
                        Layout.leftMargin: 36
                        Layout.rightMargin: 36
                        Layout.topMargin: 14
                        Layout.bottomMargin: 22
                        spacing: 8
                        Text { text: "Autre façon de voir"; height: 32; verticalAlignment: Text.AlignVCenter; font.pixelSize: 13; color: Couleurs.texte2; rightPadding: 4 }
                        Repeater {
                            model: vue.choix
                            delegate: QQC2.AbstractButton {
                                id: facon
                                readonly property bool choisie: vue.image !== null && vue.image.cle === modelData.cle
                                height: 32
                                leftPadding: 12
                                rightPadding: 12
                                hoverEnabled: true
                                focusPolicy: Qt.NoFocus
                                onClicked: vue.cleChoisie = modelData.cle
                                background: Rectangle {
                                    radius: 8
                                    color: facon.choisie ? fenetre.accentFond : facon.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.04) : Couleurs.champ
                                    border.width: facon.choisie ? 0 : 1
                                    border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                                }
                                contentItem: Text {
                                    text: modelData.nom
                                    verticalAlignment: Text.AlignVCenter
                                    font.pixelSize: 13
                                    font.weight: facon.choisie ? Font.DemiBold : Font.Normal
                                    color: facon.choisie ? fenetre.accentEncre : Couleurs.texte
                                }
                            }
                        }
                    }
                }
            }
        }

        // ——— Envoyer ce bilan ———
        Rectangle {
            Layout.preferredWidth: 400
            Layout.fillHeight: true
            color: Couleurs.fond
            Rectangle { width: 0.5; height: parent.height; color: Couleurs.bord }
            QQC2.ScrollView {
                id: defilementEnvoi
                anchors.fill: parent
                anchors.leftMargin: 1
                contentWidth: availableWidth
                QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff
                ColumnLayout {
                    width: defilementEnvoi.availableWidth
                    spacing: 16
                    Text { Layout.leftMargin: 22; Layout.topMargin: 22; text: "Envoyer ce bilan"; font.pixelSize: 22; font.weight: Font.DemiBold; font.letterSpacing: -0.2; color: Couleurs.texte }
                    RowLayout {
                        Layout.leftMargin: 22
                        Layout.rightMargin: 18
                        spacing: 16
                        // Aperçu à la taille d'un téléphone (la vraie image, réduite)
                        Rectangle {
                            Layout.alignment: Qt.AlignTop
                            Layout.preferredWidth: 150
                            Layout.preferredHeight: 268
                            radius: 16
                            color: "#FFFFFF"
                            border.width: 1
                            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
                            Item {
                                anchors.fill: parent
                                anchors.margins: 1
                                clip: true
                                Item {
                                    width: affiche.width
                                    height: affiche.height
                                    scale: 148 / affiche.width
                                    transformOrigin: Item.TopLeft
                                    Affiche {
                                        id: affiche
                                        image: vue.image
                                        entete: vue.entete
                                        pied: vue.pied
                                        tableau: vue.tableauJoint
                                    }
                                }
                            }
                            // (le bas de l'aperçu s'efface, s'il est plus long que l'écran)
                            Rectangle {
                                visible: affiche.height * 148 / affiche.width > 266
                                anchors.bottom: parent.bottom
                                anchors.bottomMargin: 1
                                x: 1
                                width: parent.width - 2
                                height: 40
                                radius: 15
                                gradient: Gradient {
                                    GradientStop { position: 0; color: Qt.rgba(1, 1, 1, 0) }
                                    GradientStop { position: 1; color: "#FFFFFF" }
                                }
                            }
                        }
                        ColumnLayout {
                            Layout.alignment: Qt.AlignTop
                            Layout.fillWidth: true
                            spacing: 6
                            Text { text: "Sous quelle forme ?"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte2 }
                            Rond { text: "Image"; detail: "Se lit sur un téléphone, dans une discussion"; choisi: vue.forme === "image"; onClicked: vue.forme = "image" }
                            Rond { text: "PDF à imprimer"; choisi: vue.forme === "pdf"; onClicked: vue.forme = "pdf" }
                            Rond { text: "Fichier Excel"; detail: "Le tableau seul, sans le reste du classeur"; choisi: vue.forme === "excel"; onClicked: vue.forme = "excel" }
                        }
                    }
                    ColumnLayout {
                        Layout.leftMargin: 22
                        Layout.rightMargin: 22
                        spacing: 2
                        Text { text: "Ce qui part"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte2; Layout.bottomMargin: 4 }
                        Case {
                            text: "Cacher les numéros de téléphone"
                            detail: "Conseillé : un registre qui circule ne doit pas servir aux arnaqueurs."
                            checked: vue.cacherNumeros
                            onToggled: vue.cacherNumeros = checked
                        }
                        Case {
                            visible: vue.forme !== "excel"
                            text: "Ajouter la date du jour"
                            checked: vue.avecDate
                            onToggled: vue.avecDate = checked
                        }
                        Case {
                            visible: vue.forme !== "excel"
                            text: "Joindre le tableau complet"
                            detail: "Sous l'image, toutes les lignes"
                            checked: vue.avecTableau
                            onToggled: vue.avecTableau = checked
                        }
                    }
                    RowLayout {
                        Layout.leftMargin: 22
                        Layout.rightMargin: 22
                        Layout.topMargin: 8
                        Layout.bottomMargin: 22
                        spacing: 10
                        QQC2.AbstractButton {
                            id: boutonCopier
                            visible: vue.forme !== "excel"
                            Layout.fillWidth: true
                            Layout.preferredHeight: 42
                            enabled: vue.image !== null
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: vue.copier()
                            background: Rectangle { radius: 10; color: boutonCopier.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.04) : Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16) }
                            contentItem: Item {
                                opacity: boutonCopier.enabled ? 1 : 0.4
                                Row {
                                    anchors.centerIn: parent
                                    spacing: 7
                                    Picto { anchors.verticalCenter: parent.verticalCenter; width: 15; height: 15; trace: "M8 8h11v12H8z M5 16V4h11"; encre: Couleurs.texte }
                                    Text { anchors.verticalCenter: parent.verticalCenter; text: "Copier l'image"; font.pixelSize: 14; color: Couleurs.texte }
                                }
                            }
                        }
                        QQC2.AbstractButton {
                            id: boutonEnregistrer
                            Layout.fillWidth: true
                            Layout.preferredHeight: 42
                            enabled: vue.forme === "excel" || vue.image !== null
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            onClicked: vue.enregistrer()
                            background: Rectangle { radius: 10; color: boutonEnregistrer.down ? Qt.darker(fenetre.accent, 1.2) : boutonEnregistrer.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent; opacity: boutonEnregistrer.enabled ? 1 : 0.4 }
                            contentItem: Item {
                                Row {
                                    anchors.centerIn: parent
                                    spacing: 7
                                    Picto { anchors.verticalCenter: parent.verticalCenter; width: 15; height: 15; trace: "M12 4v11 M7 10l5 5 5-5 M5 20h14"; encre: "#FFFFFF"; trait: 1.9 }
                                    Text {
                                        anchors.verticalCenter: parent.verticalCenter
                                        text: vue.forme === "pdf" ? "Enregistrer le PDF" : vue.forme === "excel" ? "Enregistrer le fichier" : "Enregistrer l'image"
                                        font.pixelSize: 14
                                        font.weight: Font.DemiBold
                                        color: "#FFFFFF"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
