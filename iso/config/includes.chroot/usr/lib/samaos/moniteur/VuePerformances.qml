// Onglet Performances : processeur, mémoire, disque et réseau sur les deux dernières minutes.
import QtQuick
import QtQuick.Layouts
import "../reglages"

ColumnLayout {
    id: vue
    spacing: 0
    readonly property var e: fenetre.etat

    component Grande: Rectangle {
        id: carte
        property string titre
        property string picto
        property string valeur
        property string note
        property var valeurs: []
        property var valeurs2: []
        property real maxi: 0
        property string legende: ""
        property string legende2: ""
        Layout.fillWidth: true
        Layout.fillHeight: true
        radius: 16
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : 0.05)
        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 18
            spacing: 6
            RowLayout {
                spacing: 8
                Picto { trace: carte.picto }
                Text { Layout.fillWidth: true; text: carte.titre; font.pixelSize: 12; font.weight: Font.Medium; color: Couleurs.texte2 }
                RowLayout {
                    visible: carte.legende !== ""
                    spacing: 14
                    RowLayout {
                        spacing: 6
                        Rectangle { width: 8; height: 8; radius: 4; color: Couleurs.laterite }
                        Text { text: carte.legende; font.pixelSize: 11; color: Couleurs.texte2 }
                    }
                    RowLayout {
                        spacing: 6
                        Rectangle { width: 8; height: 8; radius: 4; color: Couleurs.foret }
                        Text { text: carte.legende2; font.pixelSize: 11; color: Couleurs.texte2 }
                    }
                }
            }
            Text { text: carte.valeur; font.pixelSize: 24; font.weight: Font.Medium; color: Couleurs.texte }
            Text { text: carte.note; font.pixelSize: 12; color: Couleurs.texte3 }
            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.topMargin: 8
                // Lignes de repère
                Repeater {
                    model: 4
                    Rectangle { y: index * parent.height / 3; width: parent.width; height: 0.5; color: Couleurs.ligne }
                }
                Courbe { anchors.fill: parent; valeurs: carte.valeurs; valeurs2: carte.valeurs2; maxi: carte.maxi; epaisseur: 2 }
            }
        }
    }

    GridLayout {
        Layout.fillWidth: true
        Layout.fillHeight: true
        Layout.leftMargin: 24
        Layout.rightMargin: 24
        columns: 2
        columnSpacing: 12
        rowSpacing: 12
        Grande {
            titre: "Processeur"
            picto: "M3 12h4l3-7l4 14l3-7h4"
            valeur: vue.e ? fenetre.pourcent(vue.e.processeur.pourcentage) : "–"
            note: !vue.e ? "" : vue.e.processeur.coeurs + " cœurs" + (vue.e.processeur.frequence ? " · " + fenetre.virgule(vue.e.processeur.frequence, 1) + " GHz" : "")
                  + (vue.e.processeur.temperature ? " · " + vue.e.processeur.temperature + " °C" : "")
            valeurs: fenetre.histoCpu
            maxi: 100
        }
        Grande {
            titre: "Mémoire"
            picto: "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z"
            valeur: vue.e ? fenetre.taille(vue.e.memoire.utilisee) + " sur " + fenetre.taille(vue.e.memoire.totale) : "–"
            note: vue.e ? Math.round(100 * vue.e.memoire.utilisee / vue.e.memoire.totale) + " % utilisée · " + fenetre.taille(vue.e.memoire.cache) + " en cache, rendus dès qu'il le faut" : ""
            valeurs: fenetre.histoMemoire
            maxi: vue.e ? vue.e.memoire.totale : 0
        }
        Grande {
            titre: "Disque"
            picto: "M4 5h16v4H4z M5 9v10h14V9 M10 13h4"
            valeur: vue.e ? fenetre.debit(vue.e.disque.lecture) + " · " + fenetre.debit(vue.e.disque.ecriture) : "–"
            note: vue.e ? fenetre.taille(vue.e.disque.libre) + " libres sur " + fenetre.taille(vue.e.disque.total) : ""
            valeurs: fenetre.histoLecture
            valeurs2: fenetre.histoEcriture
            legende: "Lecture"
            legende2: "Écriture"
        }
        Grande {
            titre: "Réseau"
            picto: "M5 10a10 10 0 0 1 14 0 M8 13.5a5.5 5.5 0 0 1 8 0 M12 17.5h.01"
            valeur: vue.e ? fenetre.debit(vue.e.reseau.recu) + " · " + fenetre.debit(vue.e.reseau.envoye) : "–"
            note: fenetre.infosData && fenetre.infosData.connexion ? fenetre.infosData.connexion : "Pas de connexion"
            valeurs: fenetre.histoRecu
            valeurs2: fenetre.histoEnvoye
            legende: "Reçu"
            legende2: "Envoyé"
        }
    }
    Text {
        Layout.preferredHeight: 52
        Layout.leftMargin: 24
        verticalAlignment: Text.AlignVCenter
        text: "Deux dernières minutes, mesurées toutes les 2 secondes"
        font.pixelSize: 12
        color: Couleurs.texte3
    }
}
