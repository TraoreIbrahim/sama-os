// Imprimer (Ctrl+P) : à gauche, les pages telles qu'elles sortiront (le vrai PDF du moteur, en aperçu) ; à droite, ce
// qu'on imprime (la feuille, le tableau, la sélection, tout le classeur), la mise en page que Sama propose d'après la
// taille de ce qu'on imprime (portrait ou paysage, ajusté à la largeur de la page), les titres du tableau répétés en
// haut de chaque page, les numéros de téléphone masqués (une liste affichée ou qui circule ne doit pas servir aux
// arnaqueurs), l'imprimante, les copies, les pages. Ou un PDF, enregistré dans Documents.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtCore
import "../reglages"

Rectangle {
    id: vue
    color: Couleurs.fond
    readonly property var doc: fenetre.doc
    readonly property var tableaux: fenetre.tableaux

    // ——— Ce qu'on imprime, et comment ———
    property string quoi: "feuille"
    property string orientation: "portrait"
    property bool orientationChoisie: false
    property string ajuster: "largeur"
    property bool titres: true
    property bool quadrillage: false
    property bool numeros: true
    property bool cacher: true
    property bool rectoVerso: false
    property int copies: 1
    property string plage: ""
    // (ce que Sama sait de ce qu'on imprime : sa taille en mm, un tableau dedans, des numéros de téléphone)
    property var infos: null
    // Le tableau de la case, la sélection : ce qu'on peut imprimer d'autre que la feuille
    property var tableau: null
    property string selection: ""

    function ouvrir() {
        tableau = tableaux.courant
        // (une plage de cases ; pas des colonnes ou des lignes entières, qui iraient jusqu'au bout de la feuille)
        var a = doc.adresse.replace(/\$/g, "")
        selection = a.indexOf(":") > 0 && !/1048576|:XFD|:AMJ/.test(a) ? a : ""
        quoi = "feuille"
        orientationChoisie = false
        copies = 1
        plage = ""
        pages = []
        nbPages = 0
        lireImprimantes()
        decrire()
    }
    function choisir(q) {
        if (quoi === q) return
        quoi = q
        plage = ""
        decrire()
    }
    function decrire() {
        var q = quoi
        tableaux.appeler("SamaImpression.Decrire", [q], function (ok, v) {
            if (vue.quoi !== q || !ok || !v || v.charAt(0) !== "{") { vue.generer(); return }
            try { vue.infos = JSON.parse(v) } catch (e) { vue.infos = null }
            if (vue.infos) {
                // (plus large que la page en portrait, 18 cm entre les marges : en paysage)
                if (!vue.orientationChoisie) vue.orientation = vue.infos.largeur > 180 ? "paysage" : "portrait"
                vue.titres = vue.infos.tableau
                vue.quadrillage = !vue.infos.tableau
                vue.cacher = vue.infos.telephones
            }
            vue.generer()
        })
    }

    // ——— L'aperçu : le PDF du moteur, ses pages en images ———
    readonly property string dossier: String(StandardPaths.writableLocation(StandardPaths.TempLocation)).replace(/^file:\/\//, "") + "/sama-impression-" + Date.now()
    property int jeton: 0
    property bool enCours: false
    property string pdf: ""
    property var pages: []
    property int nbPages: 0
    property string erreur: ""
    function generer() { attente.restart() }
    Timer { id: attente; interval: 250; onTriggered: vue.faire() }
    function faire() {
        if (!vue.visible) return
        var j = ++jeton, chemin = dossier + "/apercu-" + j + ".pdf"
        doc.cheminLibre(dossier, "apercu", "pdf")   // (crée le dossier s'il le faut)
        enCours = true
        erreur = ""
        tableaux.appeler("SamaImpression.Exporter", [chemin, quoi, orientation, ajuster, titres ? "1" : "0", quadrillage ? "1" : "0",
                                                     numeros ? "1" : "0", cacher && infos && infos.telephones ? "1" : "0"], function (ok, v) {
            if (j !== vue.jeton) return
            if (!ok || (v && v.charAt(0) === "!")) {
                vue.enCours = false
                vue.erreur = "L'aperçu n'a pas pu être fait" + (v && v.charAt(0) === "!" ? " : " + v.slice(1) : "")
                return
            }
            var d = commande.q(vue.dossier), p = commande.q(chemin)
            // (les pages de l'aperçu ; les anciennes s'en vont)
            commande.lancer("cd " + d + " && find . -name 'p*.png' -delete && find . -name 'apercu-*.pdf' ! -name 'apercu-" + j + ".pdf' -delete;"
                            + " LC_ALL=C pdfinfo " + p + " | sed -n 's/^Pages: *//p'; pdftoppm -r 90 -l 40 -png " + p + " " + d + "/p" + j
                            + " && ls -1 " + d + "/p" + j + "-*.png", function (sortie) {
                if (j !== vue.jeton) return
                var l = sortie.trim().split("\n")
                vue.nbPages = Number(l.shift()) || 0
                vue.pages = l.filter(function (x) { return x.indexOf(".png") > 0 }).sort()
                vue.pdf = chemin
                vue.enCours = false
            })
        })
    }
    onOrientationChanged: generer()
    onAjusterChanged: generer()
    onTitresChanged: generer()
    onQuadrillageChanged: generer()
    onNumerosChanged: generer()
    onCacherChanged: generer()
    onVisibleChanged: if (!visible) commande.lancer("rm -rf " + commande.q(dossier) + " #" + Date.now())

    // ——— Les imprimantes (CUPS) ———
    property var imprimantes: []      // [{ id, nom, defaut }]
    property string imprimante: ""    // son id, ou « pdf »
    function lireImprimantes() {
        commande.lancer("LC_ALL=C lpstat -d 2>/dev/null; echo '--'; LC_ALL=C lpstat -e 2>/dev/null #" + Date.now(), function (s) {
            var parts = s.split("--\n"), defaut = (/destination: *(\S+)/.exec(parts[0] || "") || [])[1] || ""
            vue.imprimantes = (parts[1] || "").trim().split("\n").filter(function (x) { return x.trim() }).map(function (x) {
                return { id: x.trim(), nom: x.trim().replace(/_/g, " "), defaut: x.trim() === defaut }
            }).sort(function (a, b) { return b.defaut - a.defaut })
            if (!vue.imprimantes.some(function (i) { return i.id === vue.imprimante }) && vue.imprimante !== "pdf")
                vue.imprimante = vue.imprimantes.length ? vue.imprimantes[0].id : "pdf"
        })
    }
    function ajouterImprimante() {
        commande.lancer("systemd-run --user --quiet --collect --slice=app.slice "
                        + commande.q("--unit=app-samaos\\x2dreglages@sheet" + Date.now() + ".service") + " sama-reglages imprimantes")
    }

    // ——— Imprimer, ou enregistrer le PDF ———
    readonly property string nomDocument: {
        var n = fenetre.nomFichier.replace(/\.[^.]+$/, "")
        if (quoi.indexOf("tableau:") === 0) return n + " – " + quoi.slice(8).replace(/_/g, " ")
        if (quoi === "feuille" && doc.nomsParties.length > 1) return n + " – " + (doc.nomsParties[doc.partie] || "")
        return n
    }
    readonly property bool plageValide: /^\s*(\d+(\s*-\s*\d+)?)(\s*,\s*\d+(\s*-\s*\d+)?)*\s*$/.test(plage) || plage.trim() === ""
    function imprimer() {
        if (!pdf || enCours) return
        if (imprimante === "pdf") { enregistrerPdf(); return }
        var o = "lp -d " + commande.q(imprimante) + " -n " + copies + " -t " + commande.q(nomDocument.replace(/–/g, "-")) + " -o media=A4"
        if (plage.trim()) o += " -o page-ranges=" + commande.q(plage.replace(/\s+/g, ""))
        if (rectoVerso) o += " -o sides=" + (orientation === "paysage" ? "two-sided-short-edge" : "two-sided-long-edge")
        var nom = (imprimantes.filter(function (i) { return i.id === vue.imprimante })[0] || { nom: imprimante }).nom
        commande.lancer(o + " " + commande.q(pdf) + " 2>&1; echo \"|$?\"", function (s) {
            var code = Number((/\|(\d+)\s*$/.exec(s) || [])[1])
            if (code === 0) {
                message.montrer("Envoyé à « " + nom + " »" + (copies > 1 ? " · " + copies + " exemplaires" : ""))
                fenetre.voirGrille()
            } else {
                message.montrer("L'impression n'a pas pu partir : " + s.replace(/\|\d+\s*$/, "").trim().split("\n").pop())
            }
        })
    }
    function enregistrerPdf() {
        if (!pdf) return
        var cible = doc.cheminLibre(StandardPaths.writableLocation(StandardPaths.DocumentsLocation), nomDocument, "pdf")
        commande.lancer("cp " + commande.q(pdf) + " " + commande.q(cible) + " && echo ok", function (s) {
            message.montrer(s.trim() === "ok" ? "PDF enregistré dans Documents : " + cible.split("/").pop() : "Le PDF n'a pas pu être enregistré")
        })
    }
    Keys.onEscapePressed: fenetre.voirGrille()

    // ——— Pièces ———
    component Titre: Text {
        font.pixelSize: 13
        font.weight: Font.DemiBold
        color: Couleurs.texte2
        Layout.topMargin: 6
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // ——— Les pages ———
        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: Couleurs.sombre ? "#171A26" : "#EDE6DB"
            // En haut : revenir, et ce qu'on voit
            RowLayout {
                id: hautApercu
                x: 14
                y: 10
                width: parent.width - 28
                height: 34
                spacing: 10
                Outil {
                    text: "Revenir à la grille"
                    picto: "M15 6l-6 6 6 6"
                    aide: "Échap"
                    onClicked: fenetre.voirGrille()
                }
                Item { Layout.fillWidth: true }
                Text {
                    text: vue.enCours ? "On prépare l'aperçu…"
                          : vue.nbPages ? vue.nbPages + (vue.nbPages > 1 ? " pages" : " page") + " · A4 " + vue.orientation : ""
                    font.pixelSize: 12
                    color: Couleurs.texte2
                }
            }
            QQC2.ScrollView {
                id: defilementPages
                anchors.fill: parent
                anchors.topMargin: 50
                contentWidth: availableWidth
                QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff
                ColumnLayout {
                    width: defilementPages.availableWidth
                    spacing: 22
                    opacity: vue.enCours ? 0.45 : 1
                    Behavior on opacity { NumberAnimation { duration: 150 } }
                    Repeater {
                        model: vue.pages
                        delegate: ColumnLayout {
                            Layout.alignment: Qt.AlignHCenter
                            spacing: 8
                            Rectangle {
                                id: feuillePapier
                                readonly property real largeur: Math.min(defilementPages.availableWidth - 64, vue.orientation === "paysage" ? 860 : 620)
                                Layout.preferredWidth: largeur
                                Layout.preferredHeight: page.sourceSize.width > 0 ? largeur * page.sourceSize.height / page.sourceSize.width : largeur * 1.414
                                color: "#FFFFFF"
                                // (l'ombre d'une feuille posée)
                                Rectangle { z: -1; anchors.fill: parent; anchors.margins: -1; anchors.topMargin: 2; anchors.bottomMargin: -3; color: Qt.rgba(60 / 255, 40 / 255, 20 / 255, 0.12); radius: 2 }
                                Image {
                                    id: page
                                    anchors.fill: parent
                                    source: "file://" + modelData
                                    cache: false
                                    smooth: true
                                    mipmap: true
                                    fillMode: Image.PreserveAspectFit
                                }
                            }
                            Text {
                                Layout.alignment: Qt.AlignHCenter
                                text: (index + 1) + " / " + vue.nbPages
                                font.pixelSize: 11
                                color: Couleurs.texte2
                            }
                        }
                    }
                    Text {
                        visible: vue.nbPages > vue.pages.length && vue.pages.length > 0
                        Layout.alignment: Qt.AlignHCenter
                        text: "… et " + (vue.nbPages - vue.pages.length) + " pages de plus"
                        font.pixelSize: 12
                        color: Couleurs.texte2
                    }
                    Text {
                        visible: vue.erreur !== ""
                        Layout.alignment: Qt.AlignHCenter
                        Layout.topMargin: 40
                        text: vue.erreur
                        font.pixelSize: 14
                        color: Couleurs.texte2
                    }
                    Item { Layout.preferredHeight: 14 }
                }
            }
        }

        // ——— Les réglages ———
        Rectangle {
            Layout.preferredWidth: 380
            Layout.fillHeight: true
            color: Couleurs.fond
            Rectangle { width: 0.5; height: parent.height; color: Couleurs.bord }
            ColumnLayout {
                anchors.fill: parent
                anchors.leftMargin: 1
                spacing: 0
                QQC2.ScrollView {
                    id: defilementReglages
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    contentWidth: availableWidth
                    QQC2.ScrollBar.horizontal.policy: QQC2.ScrollBar.AlwaysOff
                    ColumnLayout {
                        width: defilementReglages.availableWidth - 44
                        x: 22
                        spacing: 6
                        Text { Layout.topMargin: 22; text: "Imprimer"; font.pixelSize: 22; font.weight: Font.DemiBold; font.letterSpacing: -0.2; color: Couleurs.texte }
                        Text {
                            Layout.fillWidth: true
                            Layout.bottomMargin: 4
                            text: vue.nomDocument
                            font.pixelSize: 13
                            color: Couleurs.texte2
                            elide: Text.ElideRight
                        }

                        // Ce qu'on imprime
                        Titre { text: "Ce qu'on imprime" }
                        Rond {
                            text: "Cette feuille"
                            detail: vue.doc.nomsParties[vue.doc.partie] || ""
                            choisi: vue.quoi === "feuille"
                            onClicked: vue.choisir("feuille")
                        }
                        Rond {
                            visible: vue.tableau !== null
                            text: vue.tableau ? "Le tableau « " + vue.tableau.nom.replace(/_/g, " ") + " »" : ""
                            detail: "Seulement ses lignes, sans le reste de la feuille"
                            choisi: vue.tableau !== null && vue.quoi === "tableau:" + vue.tableau.nom
                            onClicked: vue.choisir("tableau:" + vue.tableau.nom)
                        }
                        Rond {
                            visible: vue.selection !== ""
                            text: "Les cases choisies"
                            detail: vue.selection
                            choisi: vue.quoi === "selection"
                            onClicked: vue.choisir("selection")
                        }
                        Rond {
                            visible: vue.doc.nomsParties.length > 1
                            text: "Tout le classeur"
                            detail: vue.doc.nomsParties.length + " feuilles"
                            choisi: vue.quoi === "classeur"
                            onClicked: vue.choisir("classeur")
                        }

                        // Mise en page
                        Titre { text: "Mise en page" }
                        ChoixVues {
                            modele: [["portrait", "Portrait", "M7 3h10v18H7z"], ["paysage", "Paysage", "M3 7h18v10H3z"]]
                            courant: vue.orientation
                            onChoisi: cle => { vue.orientationChoisie = true; vue.orientation = cle }
                        }
                        Flow {
                            Layout.fillWidth: true
                            Layout.topMargin: 6
                            spacing: 6
                            Repeater {
                                model: [["largeur", "Ajusté à la largeur"], ["page", "Sur une seule page"], ["reelle", "Taille réelle"]]
                                delegate: QQC2.AbstractButton {
                                    id: taille
                                    readonly property bool choisie: vue.ajuster === modelData[0]
                                    height: 32
                                    leftPadding: 12
                                    rightPadding: 12
                                    hoverEnabled: true
                                    focusPolicy: Qt.NoFocus
                                    onClicked: vue.ajuster = modelData[0]
                                    background: Rectangle {
                                        radius: 8
                                        color: taille.choisie ? fenetre.accentFond : taille.hovered ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.04) : Couleurs.champ
                                        border.width: taille.choisie ? 0 : 1
                                        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12)
                                    }
                                    contentItem: Text {
                                        text: modelData[1]
                                        verticalAlignment: Text.AlignVCenter
                                        font.pixelSize: 13
                                        font.weight: taille.choisie ? Font.DemiBold : Font.Normal
                                        color: taille.choisie ? fenetre.accentEncre : Couleurs.texte
                                    }
                                }
                            }
                        }
                        Text {
                            Layout.fillWidth: true
                            text: vue.ajuster === "largeur" ? "Toutes les colonnes tiennent dans la largeur ; les lignes continuent sur les pages suivantes."
                                  : vue.ajuster === "page" ? "Tout est réduit pour tenir sur une page : à éviter pour une longue liste."
                                  : "Rien n'est réduit : les colonnes de trop passent sur d'autres pages."
                            font.pixelSize: 12
                            color: Couleurs.texte2
                            wrapMode: Text.Wrap
                        }
                        Case {
                            visible: vue.infos !== null && vue.infos.tableau
                            text: "Répéter les titres du tableau en haut de chaque page"
                            checked: vue.titres
                            onToggled: vue.titres = checked
                        }
                        Case {
                            text: "Imprimer le quadrillage"
                            detail: "Les traits gris entre les cases"
                            checked: vue.quadrillage
                            onToggled: vue.quadrillage = checked
                        }
                        Case {
                            text: "Numéroter les pages"
                            detail: "« Page 1 sur 3 » en bas"
                            checked: vue.numeros
                            onToggled: vue.numeros = checked
                        }
                        Case {
                            visible: vue.infos !== null && vue.infos.telephones
                            text: "Cacher les numéros de téléphone"
                            detail: "Conseillé : une liste affichée ou qui circule ne doit pas servir aux arnaqueurs."
                            checked: vue.cacher
                            onToggled: vue.cacher = checked
                        }

                        // L'imprimante
                        Titre { text: "Imprimante" }
                        Repeater {
                            model: vue.imprimantes
                            delegate: Rond {
                                text: modelData.nom
                                detail: modelData.defaut ? "Par défaut" : ""
                                choisi: vue.imprimante === modelData.id
                                onClicked: vue.imprimante = modelData.id
                            }
                        }
                        Rond {
                            text: "Enregistrer en PDF"
                            detail: "Dans Documents, pour l'envoyer ou l'imprimer ailleurs"
                            choisi: vue.imprimante === "pdf"
                            onClicked: vue.imprimante = "pdf"
                        }
                        RowLayout {
                            visible: vue.imprimantes.length === 0
                            Layout.fillWidth: true
                            spacing: 8
                            Text {
                                Layout.fillWidth: true
                                text: "Aucune imprimante installée."
                                font.pixelSize: 12
                                color: Couleurs.texte2
                                wrapMode: Text.Wrap
                            }
                            Outil {
                                text: "Ajouter une imprimante"
                                encre: fenetre.accentEncre
                                onClicked: vue.ajouterImprimante()
                            }
                        }

                        // Copies, pages, recto verso
                        ColumnLayout {
                            visible: vue.imprimante !== "pdf"
                            Layout.fillWidth: true
                            spacing: 6
                            Titre { text: "Copies et pages" }
                            RowLayout {
                                Layout.fillWidth: true
                                spacing: 10
                                Text { Layout.preferredWidth: 70; text: "Copies"; font.pixelSize: 14; color: Couleurs.texte }
                                Outil { Layout.preferredWidth: 30; picto: "M6 12h12"; aide: "Une de moins"; enabled: vue.copies > 1; onClicked: vue.copies-- }
                                Text { Layout.minimumWidth: 22; horizontalAlignment: Text.AlignHCenter; text: vue.copies; font.pixelSize: 15; font.weight: Font.DemiBold; font.features: { "tnum": 1 }; color: Couleurs.texte }
                                Outil { Layout.preferredWidth: 30; picto: "M12 6v12 M6 12h12"; aide: "Une de plus"; enabled: vue.copies < 99; onClicked: vue.copies++ }
                                Item { Layout.fillWidth: true }
                            }
                            RowLayout {
                                visible: vue.nbPages > 1
                                Layout.fillWidth: true
                                spacing: 10
                                Text { Layout.preferredWidth: 70; text: "Pages"; font.pixelSize: 14; color: Couleurs.texte }
                                Rectangle {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 32
                                    radius: 8
                                    color: Couleurs.champ
                                    border.width: champPages.activeFocus ? 1.5 : 1
                                    border.color: !vue.plageValide ? "#A3322A" : champPages.activeFocus ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.16)
                                    TextInput {
                                        id: champPages
                                        anchors.fill: parent
                                        anchors.leftMargin: 10
                                        anchors.rightMargin: 10
                                        verticalAlignment: TextInput.AlignVCenter
                                        font.pixelSize: 14
                                        color: Couleurs.texte
                                        clip: true
                                        selectByMouse: true
                                        text: vue.plage
                                        onTextEdited: vue.plage = text
                                        Text { visible: !parent.text; anchors.verticalCenter: parent.verticalCenter; text: "Toutes (ou 1-2, 4)"; font: parent.font; color: Couleurs.texte3 }
                                    }
                                }
                            }
                            Case {
                                text: "Recto verso"
                                detail: "Si l'imprimante sait le faire"
                                checked: vue.rectoVerso
                                onToggled: vue.rectoVerso = checked
                            }
                        }
                        Item { Layout.preferredHeight: 12 }
                    }
                }
                // Le bouton, toujours visible
                Rectangle { Layout.fillWidth: true; Layout.preferredHeight: 0.5; color: Couleurs.bord }
                QQC2.AbstractButton {
                    id: boutonImprimer
                    Layout.fillWidth: true
                    Layout.leftMargin: 22
                    Layout.rightMargin: 22
                    Layout.topMargin: 14
                    Layout.bottomMargin: 16
                    Layout.preferredHeight: 44
                    enabled: vue.pdf !== "" && !vue.enCours && vue.plageValide
                    hoverEnabled: true
                    focusPolicy: Qt.NoFocus
                    onClicked: vue.imprimer()
                    background: Rectangle { radius: 10; color: boutonImprimer.down ? Qt.darker(fenetre.accent, 1.2) : boutonImprimer.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent; opacity: boutonImprimer.enabled ? 1 : 0.4 }
                    contentItem: Item {
                        Row {
                            anchors.centerIn: parent
                            spacing: 8
                            Picto {
                                anchors.verticalCenter: parent.verticalCenter
                                width: 16
                                height: 16
                                trace: vue.imprimante === "pdf" ? "M12 4v11 M7 10l5 5 5-5 M5 20h14" : "M7 9V4h10v5 M5 9h14v7h-3 M8 16H5V9 M8 13h8v7H8z"
                                encre: "#FFFFFF"
                                trait: 1.9
                            }
                            Text {
                                anchors.verticalCenter: parent.verticalCenter
                                text: vue.imprimante === "pdf" ? "Enregistrer le PDF"
                                      : "Imprimer" + (vue.nbPages ? " · " + vue.nbPages + (vue.nbPages > 1 ? " pages" : " page") : "")
                                      + (vue.copies > 1 ? " × " + vue.copies : "")
                                font.pixelSize: 14
                                font.weight: Font.DemiBold
                                color: "#FFFFFF"
                            }
                        }
                    }
                }
            }
        }
    }
}
