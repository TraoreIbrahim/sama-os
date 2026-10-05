// Détails (maquette fic-01, à droite) : aperçu, type, taille, date, emplacement et application qui ouvre le
// fichier ; sans sélection, le dossier courant ; plusieurs éléments choisis : leur nombre.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "Types.js" as Types

Item {
    id: details
    readonly property string cible: fenetre.selection.length === 1 ? fenetre.selection[0]
                                  : fenetre.selection.length === 0 && fenetre.filtre !== "recents" ? fenetre.dossier : ""
    property var info: null

    Commande { id: commande }
    onCibleChanged: lire()
    Component.onCompleted: lire()
    // Le dossier change (copie, suppression…) : son contenu et ses dates aussi
    Connections {
        target: fenetre.modele
        function onCountChanged() { if (details.cible === fenetre.dossier) relecture.restart() }
    }
    Timer { id: relecture; interval: 300; onTriggered: details.lire() }
    function lire() {
        info = null
        if (!cible) return
        var demandee = cible
        commande.lancer(fenetre.moteur + "info " + commande.q(cible), function (s) {
            try { var i = JSON.parse(s); if (demandee === details.cible) details.info = i } catch (e) {}
        })
    }
    readonly property bool estDossier: info !== null && info.famille === "dossier"
    readonly property bool plusieurs: fenetre.selection.length > 1

    component Propriete: RowLayout {
        property string libelle
        property string valeur
        Layout.fillWidth: true
        spacing: 10
        visible: valeur !== ""
        Text { text: parent.libelle; font.pixelSize: 12; color: Couleurs.texte3 }
        Text { Layout.fillWidth: true; horizontalAlignment: Text.AlignRight; text: parent.valeur; elide: Text.ElideMiddle; font.pixelSize: 12; color: Couleurs.texte }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 18
        spacing: 0
        visible: details.info !== null || details.plusieurs

        // Aperçu
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 130
            radius: 14
            color: Couleurs.carte
            clip: true
            Image {
                id: vignette
                anchors.fill: parent
                visible: status === Image.Ready
                source: details.info && details.info.famille === "image" ? fenetre.adresse(details.info.chemin) : ""
                sourceSize.width: 440
                sourceSize.height: 260
                fillMode: Image.PreserveAspectCrop
                asynchronous: true
            }
            // Tuile de l'application qui ouvre le fichier (comme dans la Natte), sinon l'icône du fichier
            Image {
                anchors.centerIn: parent
                visible: !vignette.visible && !details.plusieurs && source != ""
                width: 64
                height: 64
                sourceSize.width: 128
                sourceSize.height: 128
                source: details.info && details.info.icone && details.info.icone.indexOf("/") === 0 ? "file://" + details.info.icone : ""
            }
            IconeFichier {
                anchors.centerIn: parent
                visible: !vignette.visible && (details.plusieurs || !(details.info && details.info.icone && details.info.icone.indexOf("/") === 0))
                width: 64
                height: 64
                nom: details.plusieurs ? "" : (details.info ? details.info.nom : "")
                dossier: details.plusieurs || details.estDossier
            }
        }

        Text {
            Layout.topMargin: 14
            Layout.fillWidth: true
            text: details.plusieurs ? fenetre.selection.length + " éléments sélectionnés"
                  : details.cible === fenetre.dossier ? fenetre.titreDossier : (details.info ? details.info.nom : "")
            wrapMode: Text.WrapAnywhere
            maximumLineCount: 3
            elide: Text.ElideRight
            font.pixelSize: 15
            font.weight: Font.DemiBold
            color: Couleurs.texte
        }

        ColumnLayout {
            visible: !details.plusieurs && details.info !== null
            Layout.topMargin: 12
            Layout.fillWidth: true
            spacing: 9
            Propriete { libelle: "Type"; valeur: details.info ? details.info.type : "" }
            Propriete {
                libelle: details.estDossier ? "Contenu" : "Taille"
                valeur: !details.info ? "" : details.estDossier
                        ? (details.info.elements === 0 ? "Vide" : details.info.elements + (details.info.elements > 1 ? " éléments" : " élément"))
                        : Types.taille(details.info.taille)
            }
            Propriete { libelle: "Modifié"; valeur: details.info ? Types.date(details.info.modifie * 1000) : "" }
            Propriete { libelle: "Emplacement"; valeur: details.info && details.cible !== fenetre.dossier ? details.info.emplacement : "" }
        }

        Item { Layout.fillHeight: true }

        // Actions (sur la sélection seulement, pas sur le dossier où l'on est)
        BoutonSama {
            visible: fenetre.selection.length === 1 && details.info !== null
            Layout.fillWidth: true
            implicitHeight: 38
            principal: true
            text: details.estDossier ? "Ouvrir" : (details.info && details.info.appli ? "Ouvrir avec " + details.info.appli : "Ouvrir")
            onClicked: fenetre.ouvrir(details.info.chemin, details.estDossier)
        }
        // « Supprimer » : à la corbeille (30 jours pour changer d'avis)
        QQC2.AbstractButton {
            visible: fenetre.selection.length > 0
            Layout.topMargin: 8
            Layout.fillWidth: true
            implicitHeight: 34
            hoverEnabled: true
            onClicked: fenetre.jeter(fenetre.selection)
            background: Rectangle { radius: 17; color: Qt.rgba(163 / 255, 50 / 255, 42 / 255, parent.hovered ? 0.16 : 0.1) }
            contentItem: Item {
                Row {
                    anchors.centerIn: parent
                    spacing: 7
                    Canvas {
                        anchors.verticalCenter: parent.verticalCenter
                        width: 14
                        height: 14
                        onPaint: {
                            var c = getContext("2d"); c.reset(); c.scale(14 / 24, 14 / 24)
                            c.strokeStyle = "#A3322A"; c.lineWidth = 2; c.lineCap = "round"; c.lineJoin = "round"
                            c.path = "M5 7h14 M10 7V5h4v2 M7 7l1 12a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2l1-12"; c.stroke()
                        }
                    }
                    Text { anchors.verticalCenter: parent.verticalCenter; text: "Supprimer"; font.pixelSize: 13; font.weight: Font.DemiBold; color: "#A3322A" }
                }
            }
        }
    }
}
