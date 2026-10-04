// Réglages de Sama OS (d'après les écrans « Réglages » de la maquette).
// Barre latérale : recherche, compte, 14 sections ; à droite, la page choisie.
// Lancement : sama-reglages [section[/sous-page]]   (ex. sama-reglages data, sama-reglages reseau/vpn)

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.kirigami as Kirigami
import org.kde.coreaddons as KCoreAddons

Window {
    id: fenetre
    width: 1040
    height: 700
    minimumWidth: 860
    minimumHeight: 560
    visible: true
    title: "Réglages"
    color: Couleurs.fond

    // Sections : identifiant, titre, pictogramme (grille 24, trait 1.7), page, mots-clés pour la recherche
    readonly property var sections: [
        { id: "reseau", titre: "Réseau et Internet", picto: "M5 10a10 10 0 0 1 14 0 M8 13.5a5.5 5.5 0 0 1 8 0 M12 17.5h.01", page: "Reseau", mots: "wifi wi-fi internet vpn proxy connexion" },
        { id: "bluetooth", titre: "Bluetooth et appareils", picto: "M7 7l10 10l-5 5V2l5 5L7 17", page: "Bluetooth", mots: "bluetooth casque souris clavier appareils" },
        { id: "affichage", titre: "Affichage", picto: "M3 5h18v11H3z M8 20h8 M12 16v4", page: "Affichage", mots: "écran résolution échelle luminosité sombre clair apparence nuit fond" },
        { id: "son", titre: "Son", picto: "M4 9h4l5-4v14l-5-4H4z M16 9a4 4 0 0 1 0 6 M18.5 6.5a8 8 0 0 1 0 11", page: "Son", mots: "son volume haut-parleur micro casque" },
        { id: "langue", titre: "Langue et région", picto: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z", page: "Langue", mots: "langue français anglais swahili clavier heure date fuseau région" },
        { id: "comptes", titre: "Comptes", picto: "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M4 20a8 8 0 0 1 16 0", page: "Comptes", mots: "compte utilisateur mot de passe photo" },
        { id: "confidentialite", titre: "Confidentialité", picto: "M12 3l7 3v5c0 4.5-3 8.5-7 10c-4-1.5-7-5.5-7-10V6z", page: "Confidentialite", mots: "confidentialité vie privée historique localisation" },
        { id: "data", titre: "Data et mises à jour", picto: "M5 19c0-8 5-13 14-14c-1 9-6 14-14 14z M5 19l7-7", page: "Data", mots: "data forfait économie mises à jour mobile" },
        { id: "energie", titre: "Énergie", picto: "M13 3L5 14h6l-1 7l8-11h-6z", page: "Energie", mots: "énergie batterie veille performance économie" },
        { id: "imprimantes", titre: "Imprimantes", picto: "M7 9V4h10v5 M5 9h14v7h-3 M8 16H5V9 M8 13h8v7H8z", page: "Imprimantes", mots: "imprimante imprimer scanner" },
        { id: "sauvegarde", titre: "Sauvegarde", picto: "M7 18a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z M12 11v5 M9.5 13.5L12 11l2.5 2.5", page: "Sauvegarde", mots: "sauvegarde grenier cloud copie" },
        { id: "accessibilite", titre: "Accessibilité", picto: "M12 4.5a1.5 1.5 0 1 0 0 .01 M5 8h14 M12 8v6 M9 21l3-7l3 7", page: "Accessibilite", mots: "accessibilité texte contraste lecteur loupe" },
        { id: "organisation", titre: "Organisation", picto: "M4 20V8l8-4l8 4v12 M9 20v-6h6v6 M4 20h16", page: "Organisation", mots: "organisation école entreprise administration gestion parc" },
        { id: "apropos", titre: "À propos", picto: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8h.01", page: "APropos", mots: "à propos version système ordinateur processeur mémoire mises à jour" }
    ]

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
    onSectionCouranteChanged: sousPage = ""
    // « sama-reglages reseau/vpn » : ouvre directement la sous-page
    Component.onCompleted: {
        var a = Qt.application.arguments
        var p = a.length > 0 ? String(a[a.length - 1]).split("/") : []
        if (p.length > 1) sousPage = p[1].charAt(0).toUpperCase() + p[1].slice(1)
    }
    function ouvrir(nom) { sousPage = nom }

    property string recherche: ""
    readonly property var sectionsVisibles: {
        if (!recherche) return sections
        var q = recherche.toLowerCase()
        return sections.filter(function (s) { return (s.titre + " " + s.mots).toLowerCase().indexOf(q) >= 0 })
    }

    KCoreAddons.KUser { id: utilisateur }

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
                        Rectangle {
                            Layout.preferredWidth: 36
                            Layout.preferredHeight: 36
                            radius: 18
                            color: Couleurs.laterite
                            Image {
                                anchors.fill: parent
                                source: utilisateur.faceIconUrl
                                visible: status === Image.Ready
                                fillMode: Image.PreserveAspectCrop
                                sourceSize.width: 72
                                sourceSize.height: 72
                            }
                        }
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 0
                            Text { Layout.fillWidth: true; text: utilisateur.fullName || utilisateur.loginName; elide: Text.ElideRight; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte }
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
