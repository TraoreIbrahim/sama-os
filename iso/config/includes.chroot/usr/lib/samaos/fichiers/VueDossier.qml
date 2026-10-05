// Contenu d'un dossier (maquette fic-01) : en-tête (retour, fil d'Ariane, filtres, grille ou liste), éléments,
// détails du fichier choisi, barre d'état ; menu du clic droit, renommage sur place.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "Types.js" as Types

ColumnLayout {
    id: vue
    spacing: 0
    readonly property bool recents: fenetre.filtre === "recents"
    readonly property var modeleCourant: recents ? fenetre.recents : fenetre.modele
    readonly property int nombre: recents ? fenetre.recents.length : fenetre.modele.count

    // Données d'un élément, quel que soit le modèle (dossier : FolderListModel ; récents : liste)
    function element(m) {
        if (m.fileName !== undefined) return { nom: m.fileName, chemin: m.filePath, dossier: m.fileIsDir, taille: m.fileSize, modifie: m.fileModified, adresse: m.fileUrl }
        var d = m.modelData
        return { nom: d.nom, chemin: d.chemin, dossier: false, taille: -1, modifie: d.quand * 1000, adresse: fenetre.adresse(d.chemin) }
    }
    function cliquer(el, souris) {
        if (souris.modifiers & Qt.ControlModifier) fenetre.choisir(el.chemin, "ajout")
        else fenetre.choisir(el.chemin)
    }
    function menuSur(el, objet) {
        if (fenetre.selection.indexOf(el.chemin) < 0) fenetre.choisir(el.chemin)
        menuElement.estDossier = el.dossier
        menuElement.chemin = el.chemin
        menuElement.popup()
    }

    component Picto: Canvas {
        property string trace
        property color encre: Couleurs.texte2
        width: 16
        height: 16
        onEncreChanged: requestPaint()
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(16 / 24, 16 / 24)
            c.strokeStyle = encre; c.lineWidth = 1.9; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }
    component Filtre: MouseArea {
        id: filtre
        property string libelle
        property bool choisie: false
        implicitWidth: texte.implicitWidth + 30
        implicitHeight: 28
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        Rectangle {
            anchors.fill: parent
            radius: 14
            color: filtre.choisie ? Couleurs.texte : (filtre.containsMouse ? Couleurs.carte : "transparent")
        }
        Text {
            id: texte
            anchors.centerIn: parent
            text: filtre.libelle
            font.pixelSize: 13
            font.weight: filtre.choisie ? Font.DemiBold : Font.Medium
            color: filtre.choisie ? Couleurs.fond : Couleurs.texte2
        }
    }
    // Clic dans le vide : désélectionner ; clic droit : menu du dossier (placé dans chaque vue, derrière
    // les éléments : la vue défilante garde pour elle les clics qui ne tombent sur aucun élément)
    component Fond: MouseArea {
        required property Flickable vueDefilante
        z: -1
        x: 0
        y: 0
        width: vueDefilante.width
        height: Math.max(vueDefilante.contentHeight, vueDefilante.height)
        acceptedButtons: Qt.LeftButton | Qt.RightButton
        onClicked: souris => {
            fenetre.selection = []
            if (souris.button === Qt.RightButton && !vue.recents) menuDossier.popup()
        }
    }
    // Nom modifiable sur place (F2, « Renommer », nouveau dossier)
    component ChampRenommer: QQC2.TextField {
        id: champ
        property string ancien
        horizontalAlignment: Text.AlignHCenter
        font.pixelSize: 12
        selectByMouse: true
        Component.onCompleted: {
            forceActiveFocus()
            var n = text.lastIndexOf(".")
            select(0, n > 0 ? n : text.length)      // le nom sans l'extension, comme partout
        }
        onAccepted: fenetre.renommer(ancien, text.trim())
        onActiveFocusChanged: if (!activeFocus && fenetre.renommage === ancien) fenetre.renommer(ancien, text.trim())
        Keys.onEscapePressed: fenetre.renommage = ""
    }

    // ——— En-tête ———
    Item {
        Layout.fillWidth: true
        Layout.preferredHeight: 56
        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 14
            anchors.rightMargin: 14
            spacing: 6
            MouseArea {
                Layout.preferredWidth: 30
                Layout.preferredHeight: 30
                enabled: fenetre.position > 0
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onClicked: fenetre.precedent()
                Rectangle { anchors.fill: parent; radius: 15; color: parent.containsMouse ? Couleurs.carte : "transparent" }
                Picto { anchors.centerIn: parent; trace: "M15 6l-6 6l6 6"; encre: parent.enabled ? Couleurs.texte : Couleurs.texte3 }
            }
            // Fil d'Ariane
            Row {
                Layout.fillWidth: true
                Layout.leftMargin: 4
                clip: true
                spacing: 4
                Repeater {
                    model: vue.recents ? [{ nom: "Récents", chemin: "" }] : fenetre.ariane
                    delegate: Row {
                        spacing: 4
                        Text {
                            visible: index > 0
                            anchors.verticalCenter: parent.verticalCenter
                            text: "›"
                            font.pixelSize: 15
                            color: Couleurs.texte3
                        }
                        Text {
                            readonly property bool dernier: index === (vue.recents ? 0 : fenetre.ariane.length - 1)
                            anchors.verticalCenter: parent.verticalCenter
                            text: modelData.nom
                            font.pixelSize: 15
                            font.weight: dernier ? Font.DemiBold : Font.Normal
                            color: dernier ? Couleurs.texte : Couleurs.texte2
                            MouseArea {
                                anchors.fill: parent
                                enabled: !parent.dernier && modelData.chemin !== ""
                                cursorShape: Qt.PointingHandCursor
                                onClicked: fenetre.ouvrirDossier(modelData.chemin)
                            }
                        }
                    }
                }
            }
            Repeater {
                model: [{ id: "tous", nom: "Tous" }, { id: "documents", nom: "Documents" }, { id: "images", nom: "Images" }, { id: "recents", nom: "Récents" }]
                delegate: Filtre {
                    libelle: modelData.nom
                    choisie: fenetre.filtre === modelData.id
                    onClicked: { fenetre.filtre = modelData.id; fenetre.selection = [] }
                }
            }
            // Grille ou liste
            Rectangle {
                Layout.leftMargin: 8
                Layout.preferredWidth: 66
                Layout.preferredHeight: 30
                radius: 9
                color: Couleurs.carte
                Row {
                    anchors.centerIn: parent
                    spacing: 2
                    Repeater {
                        model: [{ id: "grille", trace: "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z" },
                                { id: "liste", trace: "M8 6h12 M8 12h12 M8 18h12 M4 6h.01 M4 12h.01 M4 18h.01" }]
                        delegate: MouseArea {
                            width: 30
                            height: 26
                            cursorShape: Qt.PointingHandCursor
                            onClicked: fenetre.vue = modelData.id
                            Rectangle { anchors.fill: parent; radius: 7; color: fenetre.vue === modelData.id ? Couleurs.champ : "transparent" }
                            Picto { anchors.centerIn: parent; trace: modelData.trace; encre: fenetre.vue === modelData.id ? Couleurs.texte : Couleurs.texte3 }
                        }
                    }
                }
            }
        }
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Couleurs.ligne }
    }

    RowLayout {
        Layout.fillWidth: true
        Layout.fillHeight: true
        spacing: 0

        // ——— Éléments ———
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true


            GridView {
                id: grille
                visible: fenetre.vue === "grille"
                anchors.fill: parent
                anchors.margins: 12
                cellWidth: 112
                cellHeight: 128
                clip: true
                boundsBehavior: Flickable.StopAtBounds
                model: vue.modeleCourant
                QQC2.ScrollBar.vertical: QQC2.ScrollBar {}
                Fond { vueDefilante: grille }
                delegate: MouseArea {
                    id: tuile
                    readonly property var el: vue.element(model)
                    readonly property bool choisi: fenetre.selection.indexOf(el.chemin) >= 0
                    readonly property bool coupe: fenetre.presse.operation === "deplacer" && fenetre.presse.chemins.indexOf(el.chemin) >= 0
                    width: grille.cellWidth - 6
                    height: grille.cellHeight - 6
                    hoverEnabled: true
                    acceptedButtons: Qt.LeftButton | Qt.RightButton
                    onClicked: souris => { if (souris.button === Qt.RightButton) vue.menuSur(el, tuile); else vue.cliquer(el, souris) }
                    onDoubleClicked: souris => { if (souris.button === Qt.LeftButton) fenetre.ouvrir(el.chemin, el.dossier) }
                    Rectangle {
                        anchors.fill: parent
                        radius: 12
                        color: tuile.choisi ? Couleurs.selection : (tuile.containsMouse ? Couleurs.carte : "transparent")
                        border.width: tuile.choisi ? 1.5 : 0
                        border.color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.45)
                    }
                    IconeFichier {
                        id: iconeTuile
                        anchors.horizontalCenter: parent.horizontalCenter
                        y: 10
                        width: 56
                        height: 56
                        nom: tuile.el.nom
                        dossier: tuile.el.dossier
                        apercu: tuile.el.adresse
                        opacity: tuile.coupe ? 0.45 : 1
                    }
                    Text {
                        visible: fenetre.renommage !== tuile.el.chemin
                        anchors.top: iconeTuile.bottom
                        anchors.topMargin: 8
                        anchors.horizontalCenter: parent.horizontalCenter
                        width: parent.width - 10
                        horizontalAlignment: Text.AlignHCenter
                        text: tuile.el.nom
                        wrapMode: Text.WrapAnywhere
                        maximumLineCount: 2
                        elide: Text.ElideRight
                        lineHeight: 1.05
                        font.pixelSize: 12
                        color: Couleurs.texte
                    }
                    Loader {
                        active: fenetre.renommage === tuile.el.chemin
                        anchors.top: iconeTuile.bottom
                        anchors.topMargin: 6
                        anchors.horizontalCenter: parent.horizontalCenter
                        width: parent.width + 20
                        sourceComponent: ChampRenommer { text: tuile.el.nom; ancien: tuile.el.chemin }
                    }
                }
            }

            ListView {
                id: liste
                visible: fenetre.vue === "liste"
                anchors.fill: parent
                anchors.margins: 10
                clip: true
                boundsBehavior: Flickable.StopAtBounds
                model: vue.modeleCourant
                QQC2.ScrollBar.vertical: QQC2.ScrollBar {}
                Fond { vueDefilante: liste }
                header: RowLayout {
                    width: liste.width
                    height: 30
                    spacing: 12
                    Text { Layout.leftMargin: 46; Layout.fillWidth: true; text: "Nom"; font.pixelSize: 11; font.weight: Font.DemiBold; color: Couleurs.texte3 }
                    Text { Layout.preferredWidth: 150; text: vue.recents ? "Ouvert" : "Modifié"; font.pixelSize: 11; font.weight: Font.DemiBold; color: Couleurs.texte3 }
                    Text { Layout.preferredWidth: 110; text: "Type"; font.pixelSize: 11; font.weight: Font.DemiBold; color: Couleurs.texte3 }
                    Text { Layout.preferredWidth: 70; Layout.rightMargin: 10; horizontalAlignment: Text.AlignRight; text: "Taille"; font.pixelSize: 11; font.weight: Font.DemiBold; color: Couleurs.texte3 }
                }
                delegate: MouseArea {
                    id: ligne
                    readonly property var el: vue.element(model)
                    readonly property bool choisi: fenetre.selection.indexOf(el.chemin) >= 0
                    width: liste.width
                    height: 38
                    hoverEnabled: true
                    acceptedButtons: Qt.LeftButton | Qt.RightButton
                    onClicked: souris => { if (souris.button === Qt.RightButton) vue.menuSur(el, ligne); else vue.cliquer(el, souris) }
                    onDoubleClicked: souris => { if (souris.button === Qt.LeftButton) fenetre.ouvrir(el.chemin, el.dossier) }
                    Rectangle {
                        anchors.fill: parent
                        radius: 9
                        color: ligne.choisi ? Couleurs.selection : (ligne.containsMouse ? Couleurs.carte : "transparent")
                    }
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 10
                        spacing: 12
                        IconeFichier { Layout.preferredWidth: 24; Layout.preferredHeight: 24; nom: ligne.el.nom; dossier: ligne.el.dossier; apercu: ligne.el.adresse }
                        Text {
                            visible: fenetre.renommage !== ligne.el.chemin
                            Layout.fillWidth: true
                            text: ligne.el.nom
                            elide: Text.ElideMiddle
                            font.pixelSize: 13
                            color: Couleurs.texte
                        }
                        Loader {
                            active: fenetre.renommage === ligne.el.chemin
                            visible: fenetre.renommage === ligne.el.chemin
                            Layout.fillWidth: true
                            sourceComponent: ChampRenommer { text: ligne.el.nom; ancien: ligne.el.chemin; horizontalAlignment: Text.AlignLeft }
                        }
                        Text { Layout.preferredWidth: 150; text: Types.date(ligne.el.modifie); font.pixelSize: 12; color: Couleurs.texte2 }
                        Text {
                            Layout.preferredWidth: 110
                            text: Types.familles[Types.famille(ligne.el.nom, ligne.el.dossier)].type
                            elide: Text.ElideRight
                            font.pixelSize: 12
                            color: Couleurs.texte2
                        }
                        Text {
                            Layout.preferredWidth: 70
                            Layout.rightMargin: 10
                            horizontalAlignment: Text.AlignRight
                            text: ligne.el.dossier || ligne.el.taille < 0 ? "" : Types.taille(ligne.el.taille)
                            font.pixelSize: 12
                            color: Couleurs.texte2
                        }
                    }
                }
            }

            // Dossier vide
            Column {
                visible: vue.nombre === 0
                anchors.centerIn: parent
                spacing: 6
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    text: vue.recents ? "Aucun fichier ouvert récemment" : fenetre.filtre === "tous" ? "Ce dossier est vide" : "Aucun fichier de ce type ici"
                    font.pixelSize: 15
                    font.weight: Font.Medium
                    color: Couleurs.texte2
                }
                Text {
                    visible: !vue.recents && fenetre.filtre === "tous"
                    anchors.horizontalCenter: parent.horizontalCenter
                    text: "Glissez-y des fichiers, ou collez-les avec Ctrl+V"
                    font.pixelSize: 12
                    color: Couleurs.texte3
                }
            }
        }

        // ——— Détails ———
        Rectangle {
            visible: vue.width > 820
            Layout.preferredWidth: 1
            Layout.fillHeight: true
            color: Couleurs.ligne
        }
        Details {
            visible: vue.width > 820
            Layout.preferredWidth: 250
            Layout.fillHeight: true
        }
    }

    // ——— Barre d'état ———
    Item {
        Layout.fillWidth: true
        Layout.preferredHeight: 30
        Rectangle { anchors.top: parent.top; width: parent.width; height: 1; color: Couleurs.ligne }
        Text {
            anchors.verticalCenter: parent.verticalCenter
            anchors.left: parent.left
            anchors.leftMargin: 16
            text: vue.nombre + (vue.nombre > 1 ? " éléments" : " élément")
                  + (fenetre.selection.length ? " · " + fenetre.selection.length + (fenetre.selection.length > 1 ? " sélectionnés" : " sélectionné") : "")
                  + (fenetre.disqueCourant && fenetre.disqueCourant.libre >= 0 ? " · " + Types.taille(fenetre.disqueCourant.libre) + " libres" : "")
            font.pixelSize: 11
            color: Couleurs.texte3
        }
    }

    // ——— Menus du clic droit ———
    QQC2.Menu {
        id: menuElement
        property bool estDossier: false
        property string chemin: ""
        QQC2.MenuItem { text: "Ouvrir"; onTriggered: fenetre.ouvrir(menuElement.chemin, menuElement.estDossier) }
        QQC2.MenuSeparator {}
        QQC2.MenuItem { text: "Couper"; onTriggered: fenetre.copier(true) }
        QQC2.MenuItem { text: "Copier"; onTriggered: fenetre.copier(false) }
        QQC2.MenuItem { text: "Coller dans ce dossier"; enabled: fenetre.presse.chemins.length > 0 && menuElement.estDossier
                        onTriggered: { carteCopie.lancer(fenetre.presse.operation, menuElement.chemin, fenetre.presse.chemins)
                                       if (fenetre.presse.operation === "deplacer") fenetre.presse = { operation: "", chemins: [] } } }
        QQC2.MenuSeparator {}
        QQC2.MenuItem { text: "Renommer"; enabled: fenetre.selection.length === 1; onTriggered: fenetre.renommage = menuElement.chemin }
        QQC2.MenuItem { text: "Mettre à la corbeille"; onTriggered: fenetre.jeter(fenetre.selection) }
    }
    QQC2.Menu {
        id: menuDossier
        QQC2.MenuItem { text: "Nouveau dossier"; onTriggered: fenetre.nouveauDossier() }
        QQC2.MenuItem { text: "Coller"; enabled: fenetre.presse.chemins.length > 0; onTriggered: fenetre.coller() }
        QQC2.MenuSeparator {}
        QQC2.MenuItem { text: fenetre.caches ? "Masquer les fichiers cachés" : "Afficher les fichiers cachés"; onTriggered: fenetre.caches = !fenetre.caches }
    }
}
