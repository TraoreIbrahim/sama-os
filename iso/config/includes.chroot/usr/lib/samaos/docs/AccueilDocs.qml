// L'accueil de Sama Docs (maquette « Docs · Nouveau document ») : à gauche, les documents récents (Documents,
// Téléchargements, Bureau) et Mes informations (nom, adresse, téléphone : elles remplissent les lettres et le CV) ; à
// droite, une page blanche ou un genre guidé, chacun montré par sa page en miniature, et « Ouvrir un fichier Word ».
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "../sheet"

Rectangle {
    id: accueil
    color: Couleurs.fond
    focus: visible
    property var recents: []
    property string preparation: ""
    property alias aRecuperer: listeARecuperer.copies
    signal recuperer(var copie)
    signal oublier(var copie)
    signal vierge()
    signal modele(string genre, string nom)
    signal ouvrir(string chemin)
    signal ouvrirFichier()
    signal modifierInfos()

    Keys.onEscapePressed: vierge()
    Keys.onReturnPressed: vierge()

    // Documents récents : Documents, Téléchargements et Bureau, du plus récent au plus ancien
    Commande { id: commande }
    function lireRecents() {
        commande.lancer("for d in \"$(xdg-user-dir DOCUMENTS)\" \"$(xdg-user-dir DOWNLOAD)\" \"$(xdg-user-dir DESKTOP)\"; do "
                        + "[ -d \"$d\" ] && find \"$d\" -maxdepth 2 -type f \\( -iname '*.docx' -o -iname '*.doc' -o -iname '*.odt' -o -iname '*.rtf' \\) "
                        + "-printf '%T@\\t%h\\t%p\\n'; done 2>/dev/null | sort -rn | head -7", function (sortie) {
            var maintenant = new Date()
            accueil.recents = sortie.split("\n").filter(function (l) { return l.indexOf("\t") > 0 }).map(function (l) {
                var p = l.split("\t")
                var quand = new Date(Number(p[0]) * 1000)
                var jours = Math.floor((new Date(maintenant.getFullYear(), maintenant.getMonth(), maintenant.getDate()) - new Date(quand.getFullYear(), quand.getMonth(), quand.getDate())) / 86400000)
                var texte = jours <= 0 ? "Aujourd'hui, " + Qt.formatTime(quand, "hh:mm") : jours === 1 ? "Hier"
                            : jours < 7 ? Qt.locale("fr_FR").dayName(quand.getDay()) : Qt.formatDate(quand, "d MMMM")
                return { chemin: p[2], nom: p[2].split("/").pop().replace(/\.(docx|doc|odt|rtf)$/i, ""), quand: texte }
            })
        })
    }
    Component.onCompleted: lireRecents()

    // ——— Les genres, et leur page en miniature (des traits : x, y, largeur, hauteur, couleur, arrondi, bordure) ———
    readonly property color fort: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.55)
    readonly property color doux: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
    function teinte(a) { return Qt.rgba(fenetre.accent.r, fenetre.accent.g, fenetre.accent.b, a) }
    readonly property var genres: [
        { cle: "vierge", nom: "Document vierge", tag: "Libre", texte: "Écrire librement : styles, plan, tableaux, images, comme dans Word.",
          traits: [[39, 55, 10, 2, doux], [43, 51, 2, 10, doux]] },
        { cle: "lettre", nom: "Lettre officielle", tag: "Guidé", texte: "Demande, réclamation, candidature : la disposition officielle est prête.",
          traits: [[8, 10, 30, 3, fort], [8, 15, 24, 2, doux], [8, 19, 28, 2, doux], [52, 28, 28, 2, doux], [46, 36, 34, 2, fort], [46, 40, 28, 2, doux], [8, 52, 46, 3, teinte(0.35)], [8, 62, 72, 2, doux], [8, 66, 70, 2, doux], [8, 70, 64, 2, doux], [8, 78, 72, 2, doux], [8, 82, 58, 2, doux], [52, 98, 26, 3, fort]] },
        { cle: "cv", nom: "CV", tag: "Guidé", texte: "Sur une page, clair, prêt à imprimer ou à envoyer.",
          traits: [[8, 10, 18, 22, teinte(0.35), 3], [30, 12, 40, 4, fort], [30, 20, 34, 2, doux], [30, 25, 28, 2, doux], [8, 42, 30, 3, teinte(0.35)], [8, 49, 72, 2, doux], [8, 53, 66, 2, doux], [8, 57, 70, 2, doux], [8, 68, 34, 3, teinte(0.35)], [8, 75, 72, 2, doux], [8, 79, 60, 2, doux], [8, 90, 26, 3, teinte(0.35)], [8, 97, 50, 2, doux]] },
        { cle: "compte-rendu", nom: "Compte rendu", tag: "Guidé", texte: "Réunion, assemblée : présents, points discutés, décisions.",
          traits: [[8, 10, 58, 5, fort], [8, 19, 40, 2, doux], [8, 27, 72, 16, teinte(0.14), 2], [8, 50, 30, 3, teinte(0.35)], [8, 57, 72, 2, doux], [8, 61, 64, 2, doux], [8, 72, 38, 3, teinte(0.35)], [12, 79, 60, 2, doux], [12, 84, 54, 2, doux], [8, 96, 72, 12, teinte(0.1), 2]] },
        { cle: "attestation", nom: "Attestation", tag: "Guidé", texte: "De travail, d'hébergement, de présence : la formule exacte.",
          traits: [[20, 22, 48, 5, fort], [14, 40, 60, 2, doux], [10, 45, 68, 2, doux], [12, 50, 64, 2, doux], [10, 58, 68, 2, doux], [16, 63, 56, 2, doux], [50, 82, 30, 2, doux], [58, 90, 20, 20, "transparent", 10, teinte(0.35)]] },
        { cle: "annonce", nom: "Annonce", tag: "Guidé", texte: "À afficher : un grand titre, la date, le lieu, lisibles de loin.",
          traits: [[8, 12, 72, 14, fenetre.accent, 2], [8, 34, 50, 6, fort], [8, 46, 72, 2, doux], [8, 50, 66, 2, doux], [8, 54, 70, 2, doux], [8, 92, 72, 16, teinte(0.16), 2]] },
        { cle: "devoir", nom: "Devoir ou exposé", tag: "Guidé", texte: "Page de garde, plan, pages numérotées.",
          traits: [[8, 10, 40, 2, doux], [8, 14, 30, 2, doux], [14, 46, 60, 6, fort], [24, 56, 40, 3, teinte(0.35)], [26, 96, 36, 2, doux], [32, 101, 24, 2, doux]] },
        { cle: "ouvrir", nom: "Ouvrir un fichier Word", tag: "", texte: "Un .docx reçu s'ouvre tel quel, sa mise en page avec.", vide: true,
          traits: [[18, 34, 52, 44, "transparent", 8, Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.3)], [41, 46, 6, 20, doux], [34, 53, 20, 6, doux]] }
    ]
    function choisir(g) {
        if (g.cle === "vierge") vierge()
        else if (g.cle === "ouvrir") ouvrirFichier()
        else modele(g.cle, g.nom)
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // ——— Récents et Mes informations ———
        Item {
            Layout.preferredWidth: 300
            Layout.fillHeight: true
            Rectangle { anchors.right: parent.right; width: 0.5; height: parent.height; color: Couleurs.bord }
            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 16
                anchors.topMargin: 24
                spacing: 4
                ARecuperer {
                    id: listeARecuperer
                    Layout.fillWidth: true
                    onRecuperer: copie => accueil.recuperer(copie)
                    onOublier: copie => accueil.oublier(copie)
                }
                Text { text: "Récents"; font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte2; Layout.leftMargin: 10; Layout.bottomMargin: 8 }
                Repeater {
                    model: accueil.recents
                    delegate: QQC2.AbstractButton {
                        id: recent
                        required property var modelData
                        Layout.fillWidth: true
                        implicitHeight: 58
                        hoverEnabled: true
                        focusPolicy: Qt.NoFocus
                        onClicked: accueil.ouvrir(modelData.chemin)
                        background: Rectangle { radius: 12; color: recent.hovered ? accueil.teinte(0.09) : "transparent" }
                        contentItem: RowLayout {
                            spacing: 12
                            // (une page en miniature)
                            Rectangle {
                                Layout.leftMargin: 4
                                Layout.preferredWidth: 30
                                Layout.preferredHeight: 38
                                radius: 4
                                color: "#FFFFFF"
                                border.width: 1
                                border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                                Column {
                                    x: 5; y: 7
                                    spacing: 3
                                    Rectangle { width: 12; height: 2; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.35) }
                                    Rectangle { width: 20; height: 2; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.18) }
                                    Rectangle { width: 20; height: 2; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.18) }
                                    Rectangle { width: 14; height: 2; color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.18) }
                                }
                            }
                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2
                                Text { Layout.fillWidth: true; text: recent.modelData.nom; font.pixelSize: 14; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideRight }
                                Text { Layout.fillWidth: true; text: recent.modelData.quand; font.pixelSize: 12; color: Couleurs.texte2; elide: Text.ElideRight }
                            }
                        }
                    }
                }
                Text {
                    visible: accueil.recents.length === 0
                    text: "Les documents de vos Documents et Téléchargements apparaîtront ici."
                    font.pixelSize: 12
                    color: Couleurs.texte3
                    wrapMode: Text.Wrap
                    Layout.fillWidth: true
                    Layout.leftMargin: 10
                }
                Item { Layout.fillHeight: true }
                // Mes informations
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: infos.implicitHeight + 32
                    radius: 14
                    color: Couleurs.champ
                    border.width: 1
                    border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                    ColumnLayout {
                        id: infos
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.top: parent.top
                        anchors.margins: 16
                        spacing: 10
                        RowLayout {
                            spacing: 10
                            Rectangle {
                                Layout.preferredWidth: 36
                                Layout.preferredHeight: 36
                                radius: 18
                                color: fenetre.accentFond
                                Text { anchors.centerIn: parent; text: fenetre.initiales(fenetre.moi.nom); font.pixelSize: 13; font.weight: Font.Bold; color: fenetre.accentEncre }
                            }
                            ColumnLayout {
                                spacing: 0
                                Text { text: "Mes informations"; font.pixelSize: 14; font.weight: Font.DemiBold; color: Couleurs.texte }
                                Text { text: fenetre.moi.nom || "À remplir"; font.pixelSize: 12; color: Couleurs.texte2 }
                            }
                        }
                        Text {
                            Layout.fillWidth: true
                            text: "Votre nom, votre adresse et votre téléphone remplissent les lettres et le CV. Ils restent sur cet ordinateur."
                            font.pixelSize: 13
                            lineHeight: 18
                            lineHeightMode: Text.FixedHeight
                            color: Couleurs.texte2
                            wrapMode: Text.Wrap
                        }
                        Outil {
                            text: "Modifier"
                            background: Rectangle { radius: 8; color: parent.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05) : Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14) }
                            onClicked: accueil.modifierInfos()
                        }
                    }
                }
            }
        }

        // ——— Que voulez-vous écrire ? ———
        QQC2.ScrollView {
            id: droite
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: availableWidth
            QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff
            ColumnLayout {
                width: droite.availableWidth
                spacing: 26
                ColumnLayout {
                    Layout.leftMargin: 48
                    Layout.rightMargin: 48
                    Layout.topMargin: 40
                    spacing: 10
                    Text { text: "Nouveau document"; font.pixelSize: 42; font.weight: Font.DemiBold; font.letterSpacing: -0.8; color: Couleurs.texte }
                    RowLayout {
                        spacing: 16
                        Text {
                            Layout.maximumWidth: 620
                            text: "Une page blanche comme dans Word, ou un modèle guidé dont la mise en page est déjà faite, dans les règles du genre."
                            font.pixelSize: 16
                            lineHeight: 23
                            lineHeightMode: Text.FixedHeight
                            color: Couleurs.texte2
                            wrapMode: Text.Wrap
                        }
                        Text {
                            visible: accueil.preparation !== ""
                            text: "Préparation de « " + accueil.preparation + " »…"
                            font.pixelSize: 13
                            color: fenetre.accentEncre
                        }
                    }
                }
                GridLayout {
                    Layout.leftMargin: 48
                    Layout.rightMargin: 48
                    Layout.bottomMargin: 28
                    Layout.fillWidth: true
                    columns: Math.max(2, Math.min(4, Math.floor((droite.availableWidth - 96) / 200)))
                    columnSpacing: 16
                    rowSpacing: 16
                    Repeater {
                        model: accueil.genres
                        delegate: QQC2.AbstractButton {
                            id: carte
                            required property var modelData
                            Layout.fillWidth: true
                            // (toutes de la même largeur)
                            Layout.preferredWidth: 100
                            Layout.preferredHeight: 250
                            hoverEnabled: true
                            focusPolicy: Qt.NoFocus
                            enabled: accueil.preparation === ""
                            opacity: enabled || accueil.preparation === modelData.nom ? 1 : 0.5
                            onClicked: accueil.choisir(modelData)
                            background: Rectangle { radius: 16; color: Couleurs.champ; border.width: carte.hovered ? 1.5 : 1; border.color: carte.hovered ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1) }
                            contentItem: ColumnLayout {
                                spacing: 6
                                // La page en miniature
                                Rectangle {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 150
                                    Layout.bottomMargin: 6
                                    radius: 10
                                    color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.06) : "#F3ECE2"
                                    Rectangle {
                                        anchors.centerIn: parent
                                        width: 88
                                        height: 118
                                        radius: 3
                                        color: carte.modelData.vide ? "transparent" : "#FFFFFF"
                                        // (ombre douce)
                                        Rectangle { visible: !carte.modelData.vide; z: -1; x: 0; y: 2; width: parent.width; height: parent.height; radius: 4; color: Qt.rgba(60 / 255, 40 / 255, 20 / 255, 0.12) }
                                        Repeater {
                                            model: carte.modelData.traits
                                            delegate: Rectangle {
                                                required property var modelData
                                                x: modelData[0]; y: modelData[1]; width: modelData[2]; height: modelData[3]
                                                color: modelData[4]
                                                radius: modelData[5] || 1
                                                border.width: modelData[6] ? 1.5 : 0
                                                border.color: modelData[6] || "transparent"
                                            }
                                        }
                                    }
                                }
                                RowLayout {
                                    Layout.leftMargin: 4
                                    Layout.rightMargin: 4
                                    spacing: 6
                                    Text { Layout.fillWidth: true; text: carte.modelData.nom; font.pixelSize: 15; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideRight }
                                    Rectangle {
                                        visible: carte.modelData.tag !== ""
                                        Layout.preferredWidth: tag.implicitWidth + 14
                                        Layout.preferredHeight: 18
                                        radius: 6
                                        color: carte.modelData.tag === "Guidé" ? fenetre.accentFond : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06)
                                        Text { id: tag; anchors.centerIn: parent; text: carte.modelData.tag; font.pixelSize: 11; font.weight: Font.DemiBold; color: carte.modelData.tag === "Guidé" ? fenetre.accentEncre : Couleurs.texte2 }
                                    }
                                }
                                Text {
                                    Layout.fillWidth: true
                                    Layout.leftMargin: 4
                                    Layout.rightMargin: 4
                                    text: carte.modelData.texte
                                    font.pixelSize: 13
                                    lineHeight: 17
                                    lineHeightMode: Text.FixedHeight
                                    color: Couleurs.texte2
                                    wrapMode: Text.Wrap
                                    maximumLineCount: 3
                                }
                                Item { Layout.fillHeight: true }
                            }
                        }
                    }
                }
            }
        }
    }
}
