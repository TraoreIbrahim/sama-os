// Onglet Applications : processeur, mémoire, disque et data en haut ; ce que consomme chaque application ;
// « Détails » (ses processus) et « Forcer à quitter ».
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

ColumnLayout {
    id: vue
    spacing: 0
    readonly property var e: fenetre.etat

    component Jauge: Rectangle {
        id: jauge
        property string titre
        property string picto
        property string valeur
        property string unite
        property string note
        property var courbe: []
        property real maxi: 0
        Layout.fillWidth: true
        Layout.preferredHeight: 132
        radius: 16
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : 0.05)
        ColumnLayout {
            anchors.fill: parent
            anchors.leftMargin: 18
            anchors.rightMargin: 18
            anchors.topMargin: 16
            anchors.bottomMargin: 14
            spacing: 6
            RowLayout {
                spacing: 8
                Picto { trace: picto; encre: Couleurs.texte2 }
                Text { text: titre; font.pixelSize: 12; font.weight: Font.Medium; color: Couleurs.texte2 }
            }
            RowLayout {
                spacing: 6
                Text { text: valeur; font.pixelSize: 26; font.weight: Font.Medium; font.letterSpacing: -0.3; color: Couleurs.texte }
                Text { Layout.alignment: Qt.AlignBaseline; text: unite; font.pixelSize: 13; color: Couleurs.texte2 }
            }
            Courbe { Layout.fillWidth: true; Layout.preferredHeight: 22; valeurs: jauge.courbe; maxi: jauge.maxi }
            Text { Layout.fillWidth: true; text: note; elide: Text.ElideRight; font.pixelSize: 11; color: Couleurs.texte3 }
        }
    }

    // ——— Ordinateur en entier ———
    RowLayout {
        Layout.fillWidth: true
        Layout.leftMargin: 24
        Layout.rightMargin: 24
        spacing: 12
        Jauge {
            titre: "Processeur"
            picto: "M3 12h4l3-7l4 14l3-7h4"
            valeur: vue.e ? Math.round(vue.e.processeur.pourcentage) : "–"
            unite: "%"
            courbe: fenetre.histoCpu
            maxi: 100
            note: !vue.e ? "" : vue.e.processeur.coeurs + " cœurs" + (vue.e.processeur.frequence ? " · " + fenetre.virgule(vue.e.processeur.frequence, 1) + " GHz" : "")
                  + (vue.e.processeur.temperature ? " · " + vue.e.processeur.temperature + " °C" : "")
        }
        Jauge {
            titre: "Mémoire"
            picto: "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z"
            valeur: vue.e ? fenetre.taille(vue.e.memoire.utilisee) : "–"
            unite: vue.e ? "/ " + fenetre.taille(vue.e.memoire.totale) : ""
            courbe: fenetre.histoMemoire
            maxi: vue.e ? vue.e.memoire.totale : 0
            note: vue.e ? Math.round(100 * vue.e.memoire.utilisee / vue.e.memoire.totale) + " % utilisée · " + fenetre.taille(vue.e.memoire.cache) + " en cache" : ""
        }
        Jauge {
            titre: "Disque"
            picto: "M4 5h16v4H4z M5 9v10h14V9 M10 13h4"
            valeur: vue.e ? fenetre.taille(vue.e.disque.total - vue.e.disque.libre) : "–"
            unite: vue.e ? "/ " + fenetre.taille(vue.e.disque.total) : ""
            courbe: fenetre.histoLecture
            note: vue.e ? fenetre.taille(vue.e.disque.libre) + " libres · lecture " + fenetre.debit(vue.e.disque.lecture) : ""
        }
        Jauge {
            readonly property var d: fenetre.infosData
            titre: d && d.source === "vnstat" ? "Data aujourd'hui" : "Data depuis l'allumage"
            picto: "M5 19c0-8 5-13 14-14c-1 9-6 14-14 14z M5 19l7-7"
            valeur: d ? (d.mo >= 1024 ? fenetre.virgule(d.mo / 1024, 1) : Math.round(d.mo)) : "–"
            unite: d && d.mo >= 1024 ? "Go" : "Mo"
            courbe: fenetre.histoRecu
            note: !d ? "" : (d.economie ? "Économie de data activée" : "Économie de data désactivée") + (d.connexion ? " · " + d.connexion : "")
        }
    }

    // ——— Applications ———
    Rectangle {
        Layout.fillWidth: true
        Layout.fillHeight: true
        Layout.topMargin: 18
        Layout.leftMargin: 24
        Layout.rightMargin: 24
        radius: 16
        color: Couleurs.champ
        border.width: 0.5
        border.color: Couleurs.ligne
        clip: true

        ColumnLayout {
            anchors.fill: parent
            spacing: 0
            // En-têtes : un clic trie la colonne
            RowLayout {
                Layout.fillWidth: true
                Layout.fillHeight: false
                Layout.preferredHeight: 36
                Layout.leftMargin: 22
                Layout.rightMargin: 22
                spacing: 0
                Repeater {
                    model: [["nom", "Nom", -1], ["cpu", "Processeur", 120], ["memoire", "Mémoire", 120], ["energie", "Énergie", 110]]
                    delegate: MouseArea {
                        Layout.fillWidth: modelData[2] < 0
                        Layout.preferredWidth: modelData[2]
                        Layout.fillHeight: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: fenetre.tri = modelData[0]
                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            anchors.left: modelData[2] < 0 ? parent.left : undefined
                            anchors.right: modelData[2] < 0 ? undefined : parent.right
                            text: modelData[1].toUpperCase() + (fenetre.tri === modelData[0] ? (modelData[0] === "nom" ? " ↑" : " ↓") : "")
                            font.pixelSize: 11
                            font.weight: Font.DemiBold
                            font.letterSpacing: 0.3
                            color: fenetre.tri === modelData[0] ? Couleurs.texte2 : Couleurs.texte3
                        }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 0.5; color: Couleurs.ligne }
            ListView {
                id: liste
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.margins: 6
                clip: true
                spacing: 2
                model: fenetre.lignes
                QQC2.ScrollBar.vertical: QQC2.ScrollBar {}
                HoverHandler { onHoveredChanged: fenetre.survolListe = hovered }
                delegate: MouseArea {
                    id: ligne
                    readonly property bool choisie: modelData.cle === fenetre.selection
                    width: ListView.view.width
                    height: 40
                    hoverEnabled: true
                    onClicked: fenetre.selection = choisie ? "" : modelData.cle
                    onDoubleClicked: { fenetre.selection = modelData.cle; fenetre.ouvrirDetails() }
                    Rectangle {
                        anchors.fill: parent
                        radius: 10
                        color: ligne.choisie ? Couleurs.selection
                               : ligne.containsMouse ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.2 : 0.035) : "transparent"
                    }
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 16
                        anchors.rightMargin: 16
                        spacing: 0
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 12
                            Icone { appli: modelData }
                            Text {
                                text: modelData.nom
                                font.pixelSize: 13
                                font.weight: ligne.choisie ? Font.DemiBold : Font.Medium
                                color: Couleurs.texte
                            }
                            Text {
                                Layout.fillWidth: true
                                Layout.leftMargin: -4
                                text: modelData.detail
                                elide: Text.ElideRight
                                font.pixelSize: 12
                                color: Couleurs.texte3
                            }
                        }
                        Text { Layout.preferredWidth: 120; horizontalAlignment: Text.AlignRight; text: fenetre.pourcent(modelData.cpu); font.pixelSize: 13; font.features: { "tnum": 1 }; color: Couleurs.texte }
                        Text { Layout.preferredWidth: 120; horizontalAlignment: Text.AlignRight; text: fenetre.taille(modelData.memoire); font.pixelSize: 13; font.features: { "tnum": 1 }; color: Couleurs.texte }
                        Text { Layout.preferredWidth: 110; horizontalAlignment: Text.AlignRight; text: modelData.energie; font.pixelSize: 12; color: fenetre.couleurEnergie(modelData.energie) }
                    }
                }
                Text {
                    anchors.centerIn: parent
                    visible: fenetre.etat !== null && liste.count === 0
                    text: "Aucun processus ne correspond à « " + fenetre.recherche.trim() + " »"
                    font.pixelSize: 13
                    color: Couleurs.texte3
                }
            }
        }
    }

    // ——— Pied : résumé et actions sur l'application choisie ———
    RowLayout {
        Layout.fillWidth: true
        Layout.preferredHeight: 64
        Layout.leftMargin: 24
        Layout.rightMargin: 24
        spacing: 14
        Text {
            text: !vue.e ? "" : vue.e.applications.length + (vue.e.applications.length > 1 ? " applications · " : " application · ")
                  + vue.e.processus + " processus · en marche depuis " + fenetre.duree(vue.e.depuis)
            font.pixelSize: 12
            color: Couleurs.texte2
        }
        Item { Layout.fillWidth: true }
        Text {
            visible: fenetre.choisie !== null
            text: fenetre.choisie ? (fenetre.choisie.cle === "services" ? "Services système sélectionnés" : fenetre.choisie.nom + " sélectionné") : ""
            font.pixelSize: 12
            color: Couleurs.texte3
        }
        Bouton {
            text: "Détails"
            picto: "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8h.01"
            enabled: fenetre.choisie !== null
            onClicked: fenetre.ouvrirDetails()
        }
        Bouton {
            text: "Forcer à quitter"
            picto: "M12 3v8 M6.3 6.8a8 8 0 1 0 11.4 0"
            fond: fenetre.rouge
            encre: "#FFFFFF"
            enabled: fenetre.choisie !== null && fenetre.choisie.cle !== "services"
            onClicked: fenetre.forcerAQuitter()
        }
    }
}
