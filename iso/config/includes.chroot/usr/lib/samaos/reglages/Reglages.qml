// Réglages de Sama OS (d'après les écrans « Réglages » de la maquette).
// Barre latérale : recherche, compte, 14 sections ; à droite, la page choisie.
// Lancement : sama-reglages [section[/sous-page]]   (ex. sama-reglages data, sama-reglages reseau/vpn)

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.kirigami as Kirigami
import org.kde.coreaddons as KCoreAddons
import "sections.js" as Donnees

Window {
    id: fenetre
    width: 1040
    height: 700
    minimumWidth: 860
    minimumHeight: 560
    visible: false      // montrée une fois l'identité de l'application posée (voir Component.onCompleted)
    title: "Réglages"
    color: Couleurs.fond

    // Sections : identifiant, titre, pictogramme, page, mots-clés, détail (voir sections.js, partagé avec la Cour)
    readonly property var sections: Donnees.sections

    // Section de départ : argument de la ligne de commande, sinon Réseau
    property string sectionCourante: {
        var a = Qt.application.arguments
        var demande = a.length > 0 ? String(a[a.length - 1]).split("/")[0] : ""
        for (var i = 0; i < sections.length; i++) if (sections[i].id === demande) return demande
        return "reseau"
    }
    readonly property var section: {
        for (var i = 0; i < sections.length; i++) if (sections[i].id === sectionCourante) return sections[i]
        return sections[0]
    }

    // Sous-page ouverte dans la section (ex. « Vpn » dans Réseau et Internet) ; vide : la page de la section
    property string sousPage: ""
    property var parametre: null      // ce que la sous-page doit afficher (ex. le compte choisi)
    onSectionCouranteChanged: sousPage = ""
    // « sama-reglages reseau/vpn » : ouvre directement la sous-page
    Component.onCompleted: {
        var a = Qt.application.arguments
        var p = a.length > 0 ? String(a[a.length - 1]).split("/") : []
        if (p.length > 1) sousPage = p[1].charAt(0).toUpperCase() + p[1].slice(1)
        relireComptes()
        // Identité Wayland « samaos-reglages » (comme le fichier .desktop) : la barre de titre et la Natte
        // trouvent ainsi l'icône des Réglages au lieu du « W » générique
        Qt.application.domain = ""
        Qt.application.name = "samaos-reglages"
        visible = true
    }
    function ouvrir(nom, param) { parametre = param === undefined ? null : param; sousPage = nom }

    property string recherche: ""
    readonly property var sectionsVisibles: {
        if (!recherche) return sections
        var q = recherche.toLowerCase()
        return sections.filter(function (s) { return (s.titre + " " + s.mots).toLowerCase().indexOf(q) >= 0 })
    }

    // Comptes de l'ordinateur (lus par compte.sh) ; « moi » : la personne connectée
    KCoreAddons.KUser { id: utilisateur }
    Commande { id: commandeComptes }
    property var comptes: []
    property int revisionPhotos: 0     // change après un changement de photo pour recharger les images
    readonly property var moi: {
        for (var i = 0; i < comptes.length; i++) if (comptes[i].identifiant === utilisateur.loginName) return comptes[i]
        // (session invitée, ou liste pas encore lue : jamais administrateur par défaut)
        return { uid: -1, identifiant: utilisateur.loginName, nom: utilisateur.fullName || utilisateur.loginName, admin: false, photo: "",
                 invite: utilisateur.loginName === "sama-invite" }
    }
    function relireComptes() {
        commandeComptes.lancer("/usr/libexec/samaos/compte.sh liste", function (s) {
            fenetre.comptes = s.trim().split("\n").filter(function (l) { return l }).map(function (l) {
                var p = l.split("|")
                return { uid: Number(p[0]), identifiant: p[1], nom: p[2] || p[1], admin: p[3] === "1", photo: p[4] ? p[4] + "?r=" + fenetre.revisionPhotos : "" }
            })
        })
    }
    function photosModifiees() { revisionPhotos++; relireComptes() }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // Barre latérale
        Rectangle {
            Layout.preferredWidth: 248
            Layout.fillHeight: true
            color: Couleurs.barreLaterale

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 12
                anchors.topMargin: 14
                spacing: 2

                // Recherche
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 34
                    Layout.bottomMargin: 10
                    radius: 10
                    color: Couleurs.carte
                    QQC2.TextField {
                        anchors.fill: parent
                        leftPadding: 12
                        background: null
                        font.pixelSize: 13
                        color: Couleurs.texte
                        placeholderText: "Rechercher un réglage"
                        placeholderTextColor: Couleurs.texte3
                        onTextChanged: fenetre.recherche = text
                        onAccepted: if (fenetre.sectionsVisibles.length > 0) fenetre.sectionCourante = fenetre.sectionsVisibles[0].id
                    }
                }

                // Compte
                MouseArea {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 52
                    Layout.bottomMargin: 6
                    cursorShape: Qt.PointingHandCursor
                    onClicked: fenetre.sectionCourante = "comptes"
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 8
                        spacing: 10
                        Avatar {
                            Layout.preferredWidth: 36
                            Layout.preferredHeight: 36
                            moi: true
                            nom: fenetre.moi.nom
                            photo: fenetre.moi.photo
                        }
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 0
                            Text { Layout.fillWidth: true; text: fenetre.moi.nom; elide: Text.ElideRight; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte }
                            Text { text: "Votre compte"; font.pixelSize: 11; color: Couleurs.texte2 }
                        }
                    }
                }

                // Sections
                ListView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    spacing: 2
                    model: fenetre.sectionsVisibles
                    boundsBehavior: Flickable.StopAtBounds
                    delegate: MouseArea {
                        id: entree
                        readonly property bool choisie: modelData.id === fenetre.sectionCourante
                        width: ListView.view.width
                        height: 34
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: fenetre.sectionCourante = modelData.id
                        Rectangle {
                            anchors.fill: parent
                            radius: 9
                            color: entree.choisie ? Couleurs.selection : (entree.containsMouse ? Couleurs.carte : "transparent")
                        }
                        RowLayout {
                            anchors.fill: parent
                            anchors.leftMargin: 10
                            spacing: 10
                            Canvas {
                                id: picto
                                Layout.preferredWidth: 16
                                Layout.preferredHeight: 16
                                property color teinte: entree.choisie ? Couleurs.laterite : Couleurs.texte2
                                onTeinteChanged: requestPaint()
                                onPaint: {
                                    var c = getContext("2d")
                                    c.reset()
                                    c.scale(16 / 24, 16 / 24)
                                    c.strokeStyle = teinte
                                    c.lineWidth = 1.8
                                    c.lineCap = "round"
                                    c.lineJoin = "round"
                                    c.path = modelData.picto
                                    c.stroke()
                                }
                            }
                            Text {
                                Layout.fillWidth: true
                                text: modelData.titre
                                elide: Text.ElideRight
                                font.pixelSize: 13
                                font.weight: entree.choisie ? Font.DemiBold : Font.Normal
                                color: Couleurs.texte
                            }
                        }
                    }
                }
            }
        }

        // Page
        Loader {
            id: page
            Layout.fillWidth: true
            Layout.fillHeight: true
            source: "pages/" + (fenetre.sousPage || fenetre.section.page) + ".qml"
            onStatusChanged: if (status === Loader.Error) source = "pages/Bientot.qml"
        }
    }
}
