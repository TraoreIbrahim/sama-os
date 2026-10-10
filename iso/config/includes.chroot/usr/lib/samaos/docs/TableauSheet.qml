// Le bloc « Tableau de Sheet » (maquette « Docs · Le document libre ») : un classeur choisi, puis l'un de ses tableaux
// (un seul : directement) ; ses cases entrent dans le document comme un tableau ordinaire (Word le lit), d'où il vient
// est noté dans le document (SamaDocs.InsererDonnees). Quand le classeur change, le bandeau de Docs le dit. Le classeur
// est lu à part (lire-classeur.py : un LibreOffice invisible), le moteur de Docs ne sachant pas en ouvrir un.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtQuick.Dialogs
import QtCore
import "../reglages"
import "../sheet"

Item {
    id: lie
    property string fichier: ""
    property var tableaux: null        // [{ nom, lignes, colonnes, feuille }] : le choix, s'il y en a plusieurs
    signal annonce(string texte)
    signal insere()

    parent: fenetre.contentItem
    anchors.fill: parent
    z: 100
    visible: tableaux !== null

    Commande { id: commande }
    readonly property string outil: "/usr/libexec/samaos/lire-classeur.py"
    // (les cases, pour les macros : lignes séparées par Chr(30), cases par Chr(31))
    function enChaine(d) { return [d.lignes.map(function (l) { return l.join("\x1f") }).join("\x1e"), d.nombres.join("\x1e"), d.totaux ? "true" : "false"] }
    // Relire un tableau ; suite(donnees) ou un message
    function relire(chemin, nom, suite) {
        commande.lancer(outil + " lire " + commande.q(chemin) + " " + commande.q(nom), function (sortie) {
            var d = null
            try { d = JSON.parse(sortie) } catch (e) { }
            if (!d) { lie.annonce("Ce classeur n'a pas pu être lu"); return }
            if (d.erreur) { lie.annonce(d.erreur); return }
            if (!d.lignes.length) { lie.annonce("Ce tableau est vide"); return }
            suite(d)
        })
    }
    function choisir() { dialogue.open() }
    FileDialog {
        id: dialogue
        title: "Un tableau de Sama Sheet"
        fileMode: FileDialog.OpenFile
        nameFilters: ["Classeurs (*.xlsx *.ods *.xls)", "Tous les fichiers (*)"]
        currentFolder: StandardPaths.writableLocation(StandardPaths.DocumentsLocation)
        onAccepted: lie.lire(fenetre.chemin(selectedFile))
        onRejected: fenetre.doc.forceActiveFocus()
    }
    function lire(c) {
        fichier = c
        lie.annonce("Lecture de « " + c.split("/").pop() + " »…")
        commande.lancer(outil + " liste " + commande.q(c), function (sortie) {
            var l = null
            try { l = JSON.parse(sortie) } catch (e) { }
            if (!l) { lie.annonce("Ce classeur n'a pas pu être lu"); return }
            if (l.erreur) { lie.annonce(l.erreur); return }
            if (!l.length) lie.annonce("Ce classeur est vide")
            else if (l.length === 1) lie.inserer(l[0].nom)
            else lie.tableaux = l
        })
    }
    function inserer(nom) {
        tableaux = null
        relire(fichier, nom, function (d) {
            fenetre.appeler("SamaDocs.InsererDonnees", [fichier, nom].concat(lie.enChaine(d)), function (ok, v) {
                fenetre.doc.forceActiveFocus()
                if (!ok) { lie.annonce("Le tableau n'a pas pu être inséré"); return }
                if (v && v.charAt(0) === "!") { lie.annonce(v.slice(1)); return }
                lie.annonce("« " + nom + " » inséré : si le classeur change, Sama proposera de le mettre à jour")
                lie.insere()
            })
        })
    }
    // Mettre à jour un tableau lié (le bandeau de Docs)
    function mettreAJour(lien, suite) {
        relire(lien.fichier, lien.nom, function (d) {
            fenetre.appeler("SamaDocs.MettreAJourDonnees", [lien.table].concat(lie.enChaine(d)), function (ok, v) {
                if (!ok || (v && v.charAt(0) === "!")) lie.annonce(v ? v.slice(1) : "La mise à jour n'a pas pu se faire")
                else lie.annonce("« " + lien.nom + " » mis à jour d'après " + lien.fichier.split("/").pop() + " (Ctrl+Z pour revenir)")
                if (suite) suite()
            })
        })
    }
    function fermer() { tableaux = null; fenetre.doc.forceActiveFocus() }
    Shortcut { sequence: "Escape"; enabled: lie.visible; onActivated: lie.fermer() }

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.45 : 0.22)
        MouseArea { anchors.fill: parent; onClicked: lie.fermer() }
    }
    Rectangle {
        anchors.centerIn: parent
        width: Math.min(parent.width - 60, 440)
        height: colonne.implicitHeight + 40
        radius: 18
        color: Couleurs.fond
        border.width: 1
        border.color: Couleurs.bord
        MouseArea { anchors.fill: parent }
        ColumnLayout {
            id: colonne
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 20
            spacing: 6
            Text {
                Layout.fillWidth: true
                text: "Quel tableau de « " + lie.fichier.split("/").pop() + " » ?"
                font.pixelSize: 17
                font.weight: Font.DemiBold
                color: Couleurs.texte
                elide: Text.ElideMiddle
            }
            Text {
                Layout.fillWidth: true
                Layout.bottomMargin: 8
                text: "Il entre dans le document tel qu'on le voit dans Sheet (les lignes filtrées en moins). Quand le classeur change, Sama propose de le mettre à jour."
                font.pixelSize: 13
                lineHeight: 18
                lineHeightMode: Text.FixedHeight
                color: Couleurs.texte2
                wrapMode: Text.Wrap
            }
            Repeater {
                model: lie.tableaux || []
                delegate: QQC2.AbstractButton {
                    id: choix
                    required property var modelData
                    Layout.fillWidth: true
                    implicitHeight: 56
                    hoverEnabled: true
                    focusPolicy: Qt.NoFocus
                    onClicked: lie.inserer(modelData.nom)
                    background: Rectangle { radius: 12; color: choix.hovered ? fenetre.accentFond : Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.12 : 0.035) }
                    contentItem: RowLayout {
                        spacing: 12
                        Rectangle {
                            Layout.leftMargin: 12
                            Layout.preferredWidth: 30
                            Layout.preferredHeight: 30
                            radius: 8
                            color: Couleurs.champ
                            border.width: 1
                            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                            Picto { anchors.centerIn: parent; width: 16; height: 16; trace: choix.modelData.feuille ? "M5 4h14v16H5z M5 9h14 M10 4v16" : "M5 4h14v16H5z M5 9h14 M5 14h14 M10 4v16"; encre: fenetre.accentEncre }
                        }
                        ColumnLayout {
                            Layout.fillWidth: true
                            Layout.rightMargin: 12
                            spacing: 1
                            Text { Layout.fillWidth: true; text: (choix.modelData.feuille ? "Feuille " : "") + choix.modelData.nom; font.pixelSize: 14; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideRight }
                            Text {
                                Layout.fillWidth: true
                                text: choix.modelData.lignes + (choix.modelData.lignes > 1 ? " lignes" : " ligne") + " · " + choix.modelData.colonnes.filter(function (c) { return c }).join(", ")
                                font.pixelSize: 12
                                color: Couleurs.texte2
                                elide: Text.ElideRight
                            }
                        }
                    }
                }
            }
            RowLayout {
                Layout.fillWidth: true
                Layout.topMargin: 8
                Item { Layout.fillWidth: true }
                Outil { text: "Annuler"; onClicked: lie.fermer() }
            }
        }
    }
}
