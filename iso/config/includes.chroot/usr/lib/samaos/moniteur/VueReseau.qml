// Onglet Réseau : débits du moment, connexion, data du jour et des 7 derniers jours (data.py), forfait.
import QtQuick
import QtQuick.Layouts
import "../reglages"

ColumnLayout {
    id: vue
    spacing: 12
    readonly property var e: fenetre.etat
    readonly property var d: fenetre.infosData
    readonly property var jours: d && d.jours ? d.jours : []
    readonly property real plusHaut: Math.max.apply(null, [1].concat(jours.map(function (j) { return j.mo })))

    function mo(x) { return x >= 1024 ? fenetre.virgule(x / 1024, 1) + " Go" : Math.round(x) + " Mo" }

    component Debit: Rectangle {
        id: carte
        property string titre
        property string picto
        property real valeur
        property var valeurs: []
        property color teinte
        Layout.fillWidth: true
        Layout.preferredHeight: 170
        radius: 16
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.3 : 0.05)
        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 18
            spacing: 6
            RowLayout {
                spacing: 8
                Picto { trace: carte.picto; encre: carte.teinte }
                Text { text: carte.titre; font.pixelSize: 12; font.weight: Font.Medium; color: Couleurs.texte2 }
            }
            Text { text: vue.e ? fenetre.debit(carte.valeur) : "–"; font.pixelSize: 26; font.weight: Font.Medium; color: Couleurs.texte }
            Courbe { Layout.fillWidth: true; Layout.fillHeight: true; valeurs: carte.valeurs; teinte: carte.teinte; epaisseur: 2 }
        }
    }

    RowLayout {
        Layout.fillWidth: true
        Layout.leftMargin: 24
        Layout.rightMargin: 24
        spacing: 12
        Debit { titre: "Réception"; picto: "M12 4v14 M6 12l6 6l6-6"; valeur: vue.e ? vue.e.reseau.recu : 0; valeurs: fenetre.histoRecu; teinte: Couleurs.laterite }
        Debit { titre: "Envoi"; picto: "M12 20V6 M6 12l6-6l6 6"; valeur: vue.e ? vue.e.reseau.envoye : 0; valeurs: fenetre.histoEnvoye; teinte: Couleurs.foret }
    }

    Rectangle {
        Layout.fillWidth: true
        Layout.fillHeight: true
        Layout.leftMargin: 24
        Layout.rightMargin: 24
        radius: 16
        color: Couleurs.champ
        border.width: 0.5
        border.color: Couleurs.ligne

        RowLayout {
            anchors.fill: parent
            anchors.margins: 22
            spacing: 32

            // Connexion et data
            ColumnLayout {
                Layout.preferredWidth: 300
                Layout.alignment: Qt.AlignTop
                spacing: 16
                ColumnLayout {
                    spacing: 3
                    Text { text: "CONNEXION"; font.pixelSize: 11; font.weight: Font.DemiBold; font.letterSpacing: 0.3; color: Couleurs.texte3 }
                    Text { text: vue.d && vue.d.connexion ? vue.d.connexion : "Pas de connexion"; font.pixelSize: 15; font.weight: Font.Medium; color: Couleurs.texte }
                }
                ColumnLayout {
                    spacing: 3
                    Text {
                        text: (vue.d && vue.d.source === "vnstat" ? "DATA AUJOURD'HUI" : "DATA DEPUIS L'ALLUMAGE")
                        font.pixelSize: 11; font.weight: Font.DemiBold; font.letterSpacing: 0.3; color: Couleurs.texte3
                    }
                    Text { text: vue.d ? vue.mo(vue.d.mo) : "–"; font.pixelSize: 15; font.weight: Font.Medium; color: Couleurs.texte }
                }
                ColumnLayout {
                    visible: vue.d !== null && vue.d.forfaitMo > 0
                    spacing: 3
                    Text { text: "FORFAIT"; font.pixelSize: 11; font.weight: Font.DemiBold; font.letterSpacing: 0.3; color: Couleurs.texte3 }
                    Text { text: vue.d ? vue.mo(vue.d.utiliseMo) + " utilisés sur " + vue.mo(vue.d.forfaitMo) : ""; font.pixelSize: 15; font.weight: Font.Medium; color: Couleurs.texte }
                }
                RowLayout {
                    spacing: 8
                    Rectangle { width: 8; height: 8; radius: 4; color: vue.d && vue.d.economie ? Couleurs.foret : Couleurs.texte3 }
                    Text {
                        text: vue.d && vue.d.economie ? "Économie de data activée" : "Économie de data désactivée"
                        font.pixelSize: 13
                        color: Couleurs.texte2
                    }
                }
                Bouton {
                    text: "Réglages de la data"
                    picto: "M5 19c0-8 5-13 14-14c-1 9-6 14-14 14z M5 19l7-7"
                    // (dans sa propre unité : les Réglages restent ouverts si le Moniteur se ferme)
                    onClicked: commande.lancer("systemd-run --user --quiet --collect --slice=app.slice "
                                               + commande.q("--unit=app-samaos\\x2dreglages@moniteur" + Date.now() + ".service") + " sama-reglages data")
                }
            }

            // 7 derniers jours
            ColumnLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 10
                Text { text: "7 DERNIERS JOURS"; font.pixelSize: 11; font.weight: Font.DemiBold; font.letterSpacing: 0.3; color: Couleurs.texte3 }
                RowLayout {
                    visible: vue.jours.length > 0
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    spacing: 12
                    Repeater {
                        model: vue.jours
                        delegate: ColumnLayout {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            spacing: 6
                            Text { Layout.alignment: Qt.AlignHCenter; text: vue.mo(modelData.mo); font.pixelSize: 11; color: Couleurs.texte2 }
                            Item {
                                Layout.fillWidth: true
                                Layout.fillHeight: true
                                Rectangle {
                                    anchors.bottom: parent.bottom
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    width: Math.min(parent.width, 36)
                                    height: Math.max(3, parent.height * modelData.mo / vue.plusHaut)
                                    radius: 6
                                    color: modelData.aujourdhui ? Couleurs.laterite : Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.25)
                                }
                            }
                            Text {
                                Layout.alignment: Qt.AlignHCenter
                                text: modelData.aujourdhui ? "auj." : modelData.nom
                                font.pixelSize: 12
                                font.weight: modelData.aujourdhui ? Font.DemiBold : Font.Normal
                                color: modelData.aujourdhui ? Couleurs.texte : Couleurs.texte2
                            }
                        }
                    }
                }
                Text {
                    visible: vue.jours.length === 0
                    Layout.fillWidth: true
                    wrapMode: Text.WordWrap
                    text: "L'historique jour par jour apparaît après la première journée de mesure."
                    font.pixelSize: 13
                    color: Couleurs.texte3
                }
                Item { visible: vue.jours.length === 0; Layout.fillHeight: true }
            }
        }
    }
    Item { Layout.preferredHeight: 12 }
}
