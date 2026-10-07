// Les messages du moteur, à la façon de Sama : une valeur refusée par la liste de la case (« « Mil » n'est pas dans la
// liste » : Choisir dans la liste ramène à la case et ouvre sa liste ; Ajouter « Mil » à la liste : au tableau d'où
// elle vient, ou à ses choix), par une autre règle de la case (un nombre trop
// grand…), ou une question (remplacer ce qui est déjà écrit ?). Sama répond au moteur à la place de sa fenêtre, qu'on
// ne voit pas. Échap : la réponse prudente. (Aucune macro tant que le moteur attend la réponse : il tournerait en rond.)
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Item {
    id: messageMoteur
    readonly property var doc: fenetre.doc
    readonly property var tableaux: fenetre.tableaux
    // { fenetre, texte, detail, boutons, adresse, saisie, enSaisie, liste, f, c, l }
    property var m: null
    readonly property bool refus: m !== null && m.enSaisie && m.boutons.length === 1
    readonly property bool avertissement: m !== null && m.enSaisie && m.boutons.length === 2
    // (le texte que le moteur met quand le fichier n'en donne pas)
    readonly property bool texteParDefaut: m !== null && /^Valeur (incorrecte|non valide)\.?$/i.test(m.texte)

    parent: fenetre.contentItem
    anchors.fill: parent
    z: 100
    visible: m !== null

    Connections {
        target: messageMoteur.doc
        function onMessageMoteur(f, texte, detail, boutons) {
            var cl = grille.listes.caseListe, d = messageMoteur.doc
            messageMoteur.m = { fenetre: f, texte: texte, detail: detail, boutons: boutons,
                                adresse: d.adresse.replace(/\$/g, "").split(":")[0], saisie: d.formule, enSaisie: d.curseurTexteVisible,
                                f: d.partie, c: aideFonction.colonneSaisie, l: aideFonction.ligneSaisie,
                                liste: grille.listes.bouton !== null || (cl !== null && cl.f === d.partie && cl.c === aideFonction.colonneSaisie && cl.l === aideFonction.ligneSaisie) }
        }
        function onMessageFerme(f) { if (messageMoteur.m && messageMoteur.m.fenetre === f) messageMoteur.m = null }
    }

    // Répondre (bouton du moteur), puis revenir à la case (le moteur est passé à la suivante) ; et ouvrir sa liste
    property var suite: null
    function repondre(bouton, retour, liste) {
        var x = m
        m = null
        doc.repondreMessage(x.fenetre, bouton)
        if (retour && x.adresse) { suite = { adresse: x.adresse, liste: liste }; revenir.restart() }
        else doc.forceActiveFocus()
    }
    Timer {
        id: revenir
        interval: 200
        onTriggered: {
            messageMoteur.doc.allerA(messageMoteur.suite.adresse)
            messageMoteur.doc.forceActiveFocus()
            if (messageMoteur.suite.liste) ouvrir.restart()
        }
    }
    Timer { id: ouvrir; interval: 250; onTriggered: grille.listes.ouvrirListe() }
    function boutonMoteur(ids) {
        for (var i = 0; i < (m ? m.boutons.length : 0); i++) if (ids.indexOf(m.boutons[i].id) >= 0) return m.boutons[i].id
        return m && m.boutons.length ? m.boutons[0].id : "ok"
    }
    function prudent() {
        if (!m) return
        if (refus) repondre(m.boutons[0].id, true, false)
        else if (avertissement) repondre(boutonMoteur(["cancel", "no"]), true, false)
        else repondre(boutonMoteur(["cancel", "no", "close", "ok"]), false, false)
    }
    Shortcut { sequence: "Escape"; enabled: messageMoteur.visible; onActivated: messageMoteur.prudent() }
    // (Entrée : l'action en avant)
    Shortcut {
        sequences: ["Return", "Enter"]
        enabled: messageMoteur.visible
        onActivated: {
            var x = messageMoteur.m
            if (messageMoteur.refus) messageMoteur.repondre(x.boutons[0].id, true, x.liste)
            else if (messageMoteur.avertissement) messageMoteur.repondre(messageMoteur.boutonMoteur(["cancel", "no"]), true, false)
            else messageMoteur.repondre(x.boutons.length ? x.boutons[x.boutons.length - 1].id : "ok", false, false)
        }
    }

    // ——— Les mots ———
    readonly property string titre: !m ? ""
        : refus && m.liste ? "« " + m.saisie + " » n'est pas dans la liste"
        : refus ? "Cette valeur n'est pas permise ici"
        : avertissement ? "« " + m.saisie + " » sort de ce qui est prévu"
        : m.texte
    readonly property string explication: !m ? ""
        : refus && m.liste ? (texteParDefaut ? "Cette case prend une valeur de sa liste. Choisissez-la : un clic sur ▾, ou Alt+Bas." : m.texte)
        : refus ? (texteParDefaut ? "Cette case a une règle (un nombre, une date, une longueur…) que cette valeur ne suit pas." : m.texte)
        : avertissement ? (texteParDefaut ? "Le fichier prévoit autre chose pour cette case. Vous pouvez la garder quand même." : m.texte)
        : m.detail
    // Ajouter la valeur refusée à la liste (au tableau d'où elle vient : un nouvel article ; ou à ses choix), puis l'écrire
    // dans la case ; le moteur a d'abord sa réponse
    function ajouterALaListe() {
        var x = m
        m = null
        doc.repondreMessage(x.fenetre, x.boutons[0].id)
        messageMoteur.tableaux.appeler("SamaListes.AjouterChoix", [String(x.f), String(x.c), String(x.l), x.saisie], function (ok, v) {
            if (!messageMoteur.tableaux.verifier(ok, v)) return
            message.montrer("« " + x.saisie + " » ajouté " + (v.indexOf(".") > 0 ? "au tableau " + v.split(".")[0].replace(/_/g, " ") : "à la liste") + " · Ctrl+Z pour revenir")
            messageMoteur.tableaux.rafraichir()
        })
        doc.forceActiveFocus()
    }
    readonly property string picto: m && m.liste ? "M4 5h16v6H4z M15 7l1.5 2 1.5-2 M6 14h12 M6 18h8" : "M12 4l9 16H3z M12 10v4 M12 17v.1"

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, Couleurs.sombre ? 0.45 : 0.22)
        MouseArea { anchors.fill: parent; onClicked: messageMoteur.prudent() }
    }

    Rectangle {
        anchors.centerIn: parent
        width: Math.min(parent.width - 60, 440)
        height: colonne.implicitHeight + 48
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
            anchors.margins: 24
            spacing: 0

            Rectangle {
                Layout.preferredWidth: 44
                Layout.preferredHeight: 44
                Layout.bottomMargin: 14
                radius: 13
                color: fenetre.accentFond
                Picto { anchors.centerIn: parent; width: 22; height: 22; trace: messageMoteur.picto; encre: fenetre.accentEncre }
            }
            Text {
                Layout.fillWidth: true
                text: messageMoteur.titre
                wrapMode: Text.Wrap
                font.pixelSize: 18
                font.weight: Font.DemiBold
                color: Couleurs.texte
            }
            Text {
                visible: text !== ""
                Layout.fillWidth: true
                Layout.topMargin: 8
                text: messageMoteur.explication
                wrapMode: Text.Wrap
                font.pixelSize: 13
                lineHeight: 19
                lineHeightMode: Text.FixedHeight
                color: Couleurs.texte2
            }
            // (un nouvel article ? l'ajouter au tableau d'où vient la liste)
            QQC2.AbstractButton {
                visible: messageMoteur.refus && messageMoteur.m.liste
                Layout.topMargin: 14
                focusPolicy: Qt.NoFocus
                hoverEnabled: true
                onClicked: messageMoteur.ajouterALaListe()
                background: null
                contentItem: RowLayout {
                    spacing: 6
                    Picto { trace: "M12 5v14 M5 12h14"; encre: fenetre.accentEncre }
                    Text {
                        text: messageMoteur.m ? "Ajouter « " + messageMoteur.m.saisie + " » à la liste" : ""
                        font.pixelSize: 13
                        font.weight: Font.DemiBold
                        font.underline: parent.parent.hovered
                        color: fenetre.accentEncre
                    }
                }
            }
            RowLayout {
                Layout.fillWidth: true
                Layout.topMargin: 22
                spacing: 8
                Item { Layout.fillWidth: true }
                // Une valeur refusée : annuler, ou choisir dans la liste
                Bouton {
                    visible: messageMoteur.refus
                    plein: !(messageMoteur.m && messageMoteur.m.liste)
                    text: messageMoteur.m && messageMoteur.m.liste ? "Annuler la saisie" : "Compris"
                    onClicked: messageMoteur.repondre(messageMoteur.m.boutons[0].id, true, false)
                }
                Bouton {
                    visible: messageMoteur.refus && messageMoteur.m.liste
                    plein: true
                    text: "Choisir dans la liste"
                    onClicked: messageMoteur.repondre(messageMoteur.m.boutons[0].id, true, true)
                }
                // Hors de ce qui est prévu : corriger, ou garder
                Bouton {
                    visible: messageMoteur.avertissement
                    text: "Garder quand même"
                    onClicked: messageMoteur.repondre(messageMoteur.boutonMoteur(["ok", "yes"]), false, false)
                }
                Bouton {
                    visible: messageMoteur.avertissement
                    plein: true
                    text: "Corriger"
                    onClicked: messageMoteur.repondre(messageMoteur.boutonMoteur(["cancel", "no"]), true, false)
                }
                // Une question : les boutons du moteur, le dernier en avant
                Repeater {
                    model: messageMoteur.m && !messageMoteur.refus && !messageMoteur.avertissement ? messageMoteur.m.boutons : []
                    delegate: Bouton {
                        required property var modelData
                        required property int index
                        plein: index === messageMoteur.m.boutons.length - 1
                        text: modelData.texte
                        onClicked: messageMoteur.repondre(modelData.id, false, false)
                    }
                }
            }
        }
    }

    // (plein : l'action en avant ; sinon bordé)
    component Bouton: QQC2.AbstractButton {
        id: bouton
        property bool plein: false
        implicitHeight: 32
        implicitWidth: libelle.implicitWidth + 28
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        background: Rectangle {
            radius: 9
            color: bouton.plein ? (bouton.down ? Qt.darker(fenetre.accent, 1.2) : bouton.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent)
                                : bouton.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.06) : Couleurs.champ
            border.width: bouton.plein ? 0 : 1
            border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
        }
        contentItem: Text {
            id: libelle
            text: bouton.text
            font.pixelSize: 13
            font.weight: Font.DemiBold
            color: bouton.plein ? "#FFFFFF" : Couleurs.texte
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
        }
    }
}
