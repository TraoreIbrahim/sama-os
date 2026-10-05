/* Installateur de Sama — étape 1, « Choisissez votre langue » (maquette dem-02).
 * Une carte par langue, avec la salutation. Les langues que l'installateur ne connaît pas encore
 * (« En cours ») gardent l'installateur en français, comme l'explique la note en bas.
 */
import io.calamares.core 1.0
import io.calamares.ui 1.0
import QtQuick
import QtQuick.Layouts

Rectangle {
    id: page
    color: "#FBF9F6"
    readonly property var langues: [
        { anglais: "French", salut: "Bonjour", nom: "Français", detail: "par défaut" },
        { anglais: "English", salut: "Hello", nom: "English", detail: "Anglais" },
        { anglais: "Swahili", salut: "Habari", nom: "Kiswahili", detail: "Swahili" },
        { anglais: "", salut: "I ni ce", nom: "Julakan", detail: "Dioula", enCours: true },
        { anglais: "", salut: "Nanga def", nom: "Wolof", detail: "Wolof", enCours: true },
        { anglais: "", salut: "Sannu", nom: "Hausa", detail: "Haoussa", enCours: true },
        { anglais: "", salut: "Ẹ n lẹ", nom: "Yorùbá", detail: "Yoruba", enCours: true },
        { anglais: "", salut: "Mbote", nom: "Lingála", detail: "Lingala", enCours: true }
    ]
    // Index de chaque langue dans la liste de Calamares, d'après son nom anglais
    property var indexParNom: ({})
    property int choisie: 0
    function indexDe(l) {
        if (l.anglais && indexParNom[l.anglais] !== undefined) return indexParNom[l.anglais]
        return indexParNom["French"] !== undefined ? indexParNom["French"] : config.localeIndex
    }
    Repeater {
        model: config.languagesModel
        delegate: Item {
            Component.onCompleted: {
                var m = page.indexParNom
                var nom = String(model.englishLabel || "")
                for (var i = 0; i < page.langues.length; i++) {
                    var a = page.langues[i].anglais
                    if (a && nom.indexOf(a) === 0 && m[a] === undefined) m[a] = index
                }
                page.indexParNom = m
            }
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.leftMargin: 40
        anchors.rightMargin: 40
        anchors.topMargin: 30
        spacing: 0

        Text { text: "Choisissez votre langue"; font.pixelSize: 26; font.weight: Font.Medium; color: "#1F1C18" }
        Text {
            Layout.topMargin: 6
            Layout.fillWidth: true
            wrapMode: Text.WordWrap
            text: "Elle sera utilisée pour l'installation et pour Sama. Vous pourrez la changer à tout moment dans Réglages."
            font.pixelSize: 14
            color: "#665E54"
        }

        // Ordinateur trop petit, pas de réseau… : avertissements de Calamares
        Rectangle {
            visible: !config.requirementsModel.satisfiedMandatory
            Layout.topMargin: 16
            Layout.fillWidth: true
            Layout.preferredHeight: alerte.implicitHeight + 20
            radius: 12
            color: Qt.rgba(163 / 255, 50 / 255, 42 / 255, 0.08)
            Text {
                id: alerte
                anchors.fill: parent
                anchors.margins: 10
                wrapMode: Text.WordWrap
                font.pixelSize: 13
                color: "#A3322A"
                text: "Cet ordinateur ne remplit pas les conditions minimales pour installer Sama (espace disque, mémoire…)."
            }
        }

        GridLayout {
            Layout.topMargin: 26
            Layout.fillWidth: true
            columns: 4
            rowSpacing: 12
            columnSpacing: 12
            Repeater {
                model: page.langues
                delegate: MouseArea {
                    id: carte
                    readonly property bool choisie: page.choisie === index
                    Layout.fillWidth: true
                    Layout.preferredHeight: 118
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: {
                        page.choisie = index
                        config.localeIndex = page.indexDe(modelData)
                    }
                    Rectangle {
                        anchors.fill: parent
                        radius: 16
                        color: carte.choisie ? Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.1) : "#FFFFFF"
                        border.width: carte.choisie ? 1.5 : 1
                        border.color: carte.choisie ? "#B5532F" : (carte.containsMouse ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.3) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16))
                    }
                    Rectangle {
                        visible: !!modelData.enCours
                        x: 16; y: 16
                        width: enCours.implicitWidth + 16
                        height: 20
                        radius: 10
                        color: Qt.rgba(226 / 255, 166 / 255, 43 / 255, 0.16)
                        Text { id: enCours; anchors.centerIn: parent; text: "En cours"; font.pixelSize: 11; font.weight: Font.Medium; color: "#7A4E0C" }
                    }
                    Rectangle {
                        visible: carte.choisie
                        anchors.right: parent.right
                        anchors.top: parent.top
                        anchors.margins: 16
                        width: 20; height: 20; radius: 10
                        color: "#B5532F"
                        Text { anchors.centerIn: parent; text: "✓"; font.pixelSize: 12; font.weight: Font.DemiBold; color: "#FFFFFF" }
                    }
                    Column {
                        anchors.left: parent.left
                        anchors.bottom: parent.bottom
                        anchors.margins: 16
                        anchors.bottomMargin: 14
                        spacing: 2
                        Text { text: modelData.salut; font.pixelSize: 20; font.weight: Font.Medium; color: carte.choisie ? "#93401F" : "#1F1C18" }
                        Text {
                            textFormat: Text.StyledText
                            text: modelData.nom + "<font color='#8A8277'> · " + modelData.detail + "</font>"
                            font.pixelSize: 13
                            font.weight: Font.Medium
                            color: "#1F1C18"
                        }
                    }
                }
            }
        }

        RowLayout {
            Layout.topMargin: 18
            Layout.fillWidth: true
            spacing: 8
            Text { text: "ⓘ"; font.pixelSize: 15; color: "#8A8277" }
            Text {
                Layout.fillWidth: true
                wrapMode: Text.WordWrap
                text: "« En cours » : traduction partielle, les textes manquants s'affichent en français. D'autres langues arrivent avec les mises à jour."
                font.pixelSize: 12
                color: "#665E54"
            }
        }
        Item { Layout.fillHeight: true }
    }
}
