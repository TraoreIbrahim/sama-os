// Envoyer à toute une liste (maquette « Docs · Envoyer à toute une liste ») : le document devient un modèle (une
// convocation, un rappel de cotisation) ; la liste vient d'un tableau de Sama Sheet (les membres) ; ses colonnes sont
// des champs à mettre dans le texte (un clic au curseur, ou glissés à leur place) ; qui la reçoit (tout le monde, ceux
// qui doivent encore, ou au choix) ; l'aperçu, personne par personne ; un seul document à imprimer ou un PDF par
// personne. Les numéros de téléphone ne sont jamais proposés ni recopiés (contre les arnaques : un document circule).
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtCore
import Sama.Moteur
import "../reglages"
import "../sheet"

Rectangle {
    id: envoi
    color: Couleurs.fond
    signal annonce(string texte)
    signal fermer()
    readonly property var doc: fenetre.doc

    // ——— La liste ———
    property var source: null          // { fichier, nom }
    property var donnees: null         // { lignes, nombres, brut, totaux, titre } (lire-classeur.py)
    property bool lecture: false
    Commande { id: commande }
    readonly property string outil: "/usr/libexec/samaos/lire-classeur.py"
    function ouvrir() {
        resultat = null
        fenetre.appeler("SamaDocs.SourceEnvoi", [], function (ok, v) {
            if (ok && v && v.charAt(0) === "{") { envoi.source = JSON.parse(v); envoi.charger() }
        })
        relireChamps()
    }
    function definir(fichier, nom) {
        source = { fichier: fichier, nom: nom }
        fenetre.appeler("SamaDocs.NoterSourceEnvoi", [fichier, nom])
        charger()
    }
    function charger() {
        if (!source) return
        lecture = true
        commande.lancer(outil + " lire " + commande.q(source.fichier) + " " + commande.q(source.nom), function (sortie) {
            envoi.lecture = false
            var d = null
            try { d = JSON.parse(sortie) } catch (e) { }
            if (!d || d.erreur) { envoi.donnees = null; envoi.annonce(d && d.erreur ? d.erreur : "La liste n'a pas pu être lue"); return }
            envoi.donnees = d
            envoi.qui = envoi.colonneReste >= 0 && envoi.doivent.length > 0 && envoi.doivent.length < envoi.personnes.length ? "doivent" : "tous"
            envoi.apercu = 0
        })
    }
    readonly property var colonnes: donnees ? donnees.lignes[0] : []
    // Les personnes : les lignes du tableau, sans la ligne des totaux
    readonly property var personnes: {
        if (!donnees) return []
        var l = []
        for (var i = 1; i < donnees.lignes.length; ++i) {
            var v = donnees.lignes[i]
            if (donnees.totaux && i === donnees.lignes.length - 1) continue
            if (/^total/i.test(String(v[0]).trim())) continue
            l.push({ rang: i, valeurs: v, brut: donnees.brut[i] || [] })
        }
        return l
    }
    // (une colonne de téléphones : par son nom, ou parce que ses valeurs en ont l'air)
    function estTelephone(k) {
        if (/t[ée]l[ée]?phone|\btél\b|\btel\b|portable|mobile|whats\s?app|num[ée]ro|contact/i.test(colonnes[k])) return true
        var vals = personnes.map(function (p) { return String(p.valeurs[k] || "") }).filter(function (x) { return x })
        var tels = vals.filter(function (x) { return /^\+?[\d\s.\-()]{8,}$/.test(x) && x.replace(/\D/g, "").length >= 8 })
        return vals.length > 0 && tels.length * 2 >= vals.length
    }
    readonly property var champs: colonnes.map(function (c, k) { return { nom: c, rang: k } })
                                          .filter(function (c) { return c.nom && !envoi.estTelephone(c.rang) })
    readonly property int telephones: colonnes.filter(function (c, k) { return c && envoi.estTelephone(k) }).length
    // Le reste à payer : « Reste », « Doit », « Solde »… (nombres)
    readonly property int colonneReste: {
        for (var k = 0; k < colonnes.length; ++k)
            if (/reste|doit|d[ûu]\b|solde|impay/i.test(colonnes[k]) && personnes.some(function (p) { return typeof p.brut[k] === "number" })) return k
        return -1
    }
    readonly property var doivent: colonneReste < 0 ? [] : personnes.filter(function (p) { return Number(p.brut[colonneReste]) > 0 })
    // Le nom de chacun : Prénom et Nom, sinon Membre, Nom, Élève, Client…, sinon la première colonne de texte
    readonly property var colonnesNom: {
        var p = colonnes.findIndex(function (c) { return /pr[ée]nom/i.test(c) })
        var n = colonnes.findIndex(function (c) { return /^nom( de famille)?$/i.test(String(c).trim()) })
        if (p >= 0 || n >= 0) return [p, n].filter(function (k) { return k >= 0 })
        var m = colonnes.findIndex(function (c) { return /membre|nom|[ée]l[èe]ve|client|personne|b[ée]n[ée]ficiaire/i.test(c) })
        return [m >= 0 ? m : 0]
    }
    function nomDe(p) { return colonnesNom.map(function (k) { return String(p.valeurs[k] || "").trim() }).filter(function (x) { return x }).join(" ") || "Ligne " + p.rang }

    // ——— Les champs du document ———
    property var utilises: []           // [{ champ, paragraphe, marque }]
    readonly property var nomsUtilises: utilises.map(function (u) { return u.champ }).filter(function (c, i, t) { return t.indexOf(c) === i })
    function relireChamps() {
        fenetre.appeler("SamaDocs.ChampsUtilises", [], function (ok, v) {
            if (ok && v && v.charAt(0) === "[") envoi.utilises = JSON.parse(v)
        })
    }
    Timer { id: relire; interval: 600; onTriggered: envoi.relireChamps() }
    Connections {
        target: envoi.doc
        enabled: envoi.visible
        function onRevisionChanged() { relire.restart() }
    }
    function placer(nom) {
        if (!source) return
        fenetre.appeler("SamaDocs.InsererChamp", [source.nom, nom], function () { envoi.doc.forceActiveFocus(); relire.restart() })
    }

    // ——— Qui la reçoit ———
    property string qui: "tous"         // « tous », « doivent », « choix »
    property var exclus: ({})           // (au choix : les rangs décochés)
    readonly property var liste: qui === "doivent" ? doivent : qui === "choix" ? personnes.filter(function (p) { return !envoi.exclus[p.rang] }) : personnes
    property int apercu: 0
    onListeChanged: apercu = Math.max(0, Math.min(apercu, liste.length - 1))

    // L'aperçu : la phrase d'un champ (de préférence un montant), avec les valeurs de la personne
    function phrasePour(p) {
        var u = utilises.filter(function (x) { return envoi.colonnesNom.indexOf(envoi.colonnes.indexOf(x.champ)) < 0 })
        u.sort(function (a, b) { return (b.champ === envoi.colonnes[envoi.colonneReste] ? 1 : 0) - (a.champ === envoi.colonnes[envoi.colonneReste] ? 1 : 0) })
        if (!u.length || !p) return ""
        var phrases = String(u[0].paragraphe).match(/[^.!?]+[.!?]?/g) || []
        var ph = phrases.find(function (x) { return x.indexOf(u[0].marque) >= 0 }) || u[0].paragraphe
        utilises.forEach(function (x) {
            var k = envoi.colonnes.indexOf(x.champ)
            if (x.marque) ph = ph.split(x.marque).join(k >= 0 ? String(p.valeurs[k]) : "")
        })
        ph = ph.trim()
        return ph.length > 70 ? ph.slice(0, 68) + "…" : ph
    }
    readonly property var personneVue: liste.length ? liste[Math.min(apercu, liste.length - 1)] : null
    readonly property string detailVu: {
        var p = personneVue
        if (!p) return ""
        var autre = nomsUtilises.map(function (c) { return envoi.colonnes.indexOf(c) })
                                .find(function (k) { return k >= 0 && envoi.colonnesNom.indexOf(k) < 0 && k !== envoi.colonneReste && !(donnees.nombres[p.rang] || "").charAt(k).match(/1/) })
        return autre !== undefined && p.valeurs[autre] ? " · " + p.valeurs[autre] : ""
    }

    // ——— Sous quelle forme ———
    property string forme: "un"         // « un » : un seul document ; « pdf » : un PDF par personne
    readonly property int pagesModele: { var m = /(\d+)\s*$/.exec(String(doc.etats[".uno:StatePageNumber"] || "").replace(/\s*\(.*\)\s*$/, "")); return m ? Number(m[1]) : 1 }
    readonly property string titreModele: fenetre.nomFichier.replace(/\.(docx|odt|doc|rtf)$/i, "")
    // (« Préparer les 3 convocations » : le premier mot du titre, au pluriel ; sinon « documents »)
    readonly property string genre: {
        var m = /^([A-Za-zÀ-ÿ'-]{3,})/.exec(titreModele)
        if (!m || /^document$/i.test(m[1])) return "documents"
        var g = m[1].toLowerCase()
        return /[sxz]$/.test(g) ? g : g + "s"
    }

    // ——— Préparer ———
    property bool preparation: false
    property var resultat: null         // { forme, fichiers, dossier }
    function nettoyer(t) { return String(t).replace(/[\/\\:*?"<>|\x00-\x1f]/g, " ").replace(/\s+/g, " ").trim() }
    function preparer() {
        if (preparation || !liste.length || !nomsUtilises.length) return
        preparation = true
        resultat = null
        var documents = fenetre.chemin(StandardPaths.writableLocation(StandardPaths.DocumentsLocation))
        var temp = fenetre.chemin(StandardPaths.writableLocation(StandardPaths.RuntimeLocation)) + "/samaos-envoi-" + Date.now()
        var base = nettoyer(titreModele) || "Document"
        // (seulement les colonnes des champs du texte : les téléphones ne quittent jamais le classeur)
        var cols = nomsUtilises.filter(function (c) { var k = envoi.colonnes.indexOf(c); return k >= 0 && !envoi.estTelephone(k) })
        var lignes = liste.map(function (p) { return cols.map(function (c) { return String(p.valeurs[envoi.colonnes.indexOf(c)] || "") }).join("\x1f") }).join("\x1e")
        var vus = {}
        var noms = liste.map(function (p) {
            var n = base + " — " + (nettoyer(envoi.nomDe(p)) || "ligne " + p.rang)
            var u = n, i = 2
            while (vus[u]) u = n + " (" + i++ + ")"
            vus[u] = true
            return u
        }).join("\x1e")
        var fichier = doc.cheminLibre(documents, base + " — " + liste.length + (liste.length > 1 ? " personnes" : " personne"), "docx")
        // (les PDF : un dossier à eux, d'un nom libre — jamais dans un dossier qui existe déjà)
        var libre = forme !== "pdf" ? "echo " + commande.q(fichier)
                  : "d=" + commande.q(documents + "/" + base + " — PDF") + "; b=\"$d\"; n=2; while [ -e \"$d\" ]; do d=\"$b ($n)\"; n=$((n+1)); done; mkdir -p \"$d\" && echo \"$d\""
        commande.lancer("mkdir -p " + commande.q(temp) + " && " + libre, function (r) {
            var sortie = r.trim()
            if (!sortie) { envoi.preparation = false; envoi.annonce("Le dossier n'a pas pu être créé"); return }
            fenetre.appeler("SamaDocs.Fusionner", [envoi.forme, sortie, temp, cols.join("\x1f"), lignes, noms], function (ok, v) {
                envoi.preparation = false
                commande.lancer("rm -rf " + commande.q(temp), function () {})
                if (!ok || !v || v.charAt(0) === "!") { envoi.annonce(v && v.charAt(0) === "!" ? v.slice(1) : "La préparation n'a pas abouti"); return }
                var d = JSON.parse(v)
                envoi.resultat = { forme: envoi.forme, fichiers: d.fichiers, dossier: envoi.forme === "pdf" ? sortie : sortie.replace(/\/[^\/]*$/, ""), nombre: envoi.liste.length }
            })
        })
    }
    function ouvrirResultat() {
        if (!resultat) return
        if (resultat.forme === "pdf") commande.lancer("xdg-open " + commande.q(resultat.dossier), function () {})
        else fenetre.ouvrirFichier(resultat.fichiers[0])
    }

    // ——— L'écran ———
    Rectangle { anchors.left: parent.left; width: 0.5; height: parent.height; color: Couleurs.bord }
    component Titre: Text { font.pixelSize: 13; font.weight: Font.DemiBold; color: Couleurs.texte2 }
    component Choix: QQC2.AbstractButton {
        id: choix
        property bool coche: false
        property string compte: ""
        Layout.fillWidth: true
        implicitHeight: 30
        hoverEnabled: true
        focusPolicy: Qt.NoFocus
        contentItem: RowLayout {
            spacing: 10
            Rectangle {
                Layout.preferredWidth: 16
                Layout.preferredHeight: 16
                radius: 8
                color: Couleurs.champ
                border.width: choix.coche ? 5 : 1.5
                border.color: choix.coche ? fenetre.accent : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.3)
            }
            Text { Layout.fillWidth: true; text: choix.text; font.pixelSize: 14; color: Couleurs.texte; elide: Text.ElideRight }
            Text { text: choix.compte; font.pixelSize: 13; color: Couleurs.texte2 }
        }
    }
    component Carte: Rectangle { radius: 12; color: Couleurs.champ; border.width: 1; border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1) }

    ColumnLayout {
        anchors.fill: parent
        anchors.leftMargin: 20
        anchors.rightMargin: 20
        anchors.topMargin: 16
        anchors.bottomMargin: 16
        spacing: 12

        RowLayout {
            Layout.fillWidth: true
            Text { Layout.fillWidth: true; text: "Envoyer à toute une liste"; font.pixelSize: 21; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideRight }
            Outil { Layout.preferredWidth: 28; Layout.preferredHeight: 28; picto: "M7 7l10 10 M17 7L7 17"; aide: "Fermer"; onClicked: envoi.fermer() }
        }

        // La liste : un tableau de Sama Sheet
        Carte {
            Layout.fillWidth: true
            Layout.preferredHeight: 58
            RowLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 12
                Rectangle {
                    Layout.preferredWidth: 34
                    Layout.preferredHeight: 34
                    radius: 9
                    color: "#1F5E7A"
                    Picto { anchors.centerIn: parent; width: 17; height: 17; trace: "M5 4h14v16H5z M5 9h14 M5 14h14 M10 4v16"; encre: "#E8F3F7"; trait: 2 }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 1
                    Text {
                        Layout.fillWidth: true
                        text: !envoi.source ? "Pas encore de liste" : envoi.donnees && envoi.donnees.titre ? envoi.donnees.titre : envoi.source.nom
                        font.pixelSize: 14
                        font.weight: Font.DemiBold
                        color: Couleurs.texte
                        elide: Text.ElideRight
                    }
                    Text {
                        Layout.fillWidth: true
                        text: !envoi.source ? "Un tableau de Sama Sheet : membres, élèves, clients…"
                              : envoi.lecture ? "Lecture de " + envoi.source.fichier.split("/").pop() + "…"
                              : "Tableau " + envoi.source.nom + " · " + envoi.personnes.length + (envoi.personnes.length > 1 ? " lignes" : " ligne") + " · Sama Sheet"
                        font.pixelSize: 12
                        color: Couleurs.texte2
                        elide: Text.ElideRight
                    }
                }
                QQC2.AbstractButton {
                    id: changer
                    hoverEnabled: true
                    focusPolicy: Qt.NoFocus
                    onClicked: fenetre.tableauSheet.choisirPour(function (f, nom) { envoi.definir(f, nom) })
                    contentItem: Text { text: envoi.source ? "Changer" : "Choisir…"; font.pixelSize: 13; font.weight: Font.DemiBold; font.underline: changer.hovered; color: fenetre.accentEncre }
                }
            }
        }

        // Les champs : un clic les place au curseur ; on peut aussi les glisser dans le texte
        ColumnLayout {
            visible: envoi.champs.length > 0
            Layout.fillWidth: true
            spacing: 8
            Titre { text: "Champs à glisser dans le texte" }
            Flow {
                Layout.fillWidth: true
                spacing: 6
                Repeater {
                    model: envoi.champs
                    delegate: Rectangle {
                        id: pastille
                        required property var modelData
                        readonly property bool utilise: envoi.nomsUtilises.indexOf(modelData.nom) >= 0
                        width: nomChamp.implicitWidth + 18
                        height: 26
                        radius: 6
                        color: utilise ? fenetre.accentFond : Couleurs.champ
                        border.width: utilise ? 0 : 1
                        border.color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
                        opacity: zone.pressed && zone.glisse ? 0.5 : 1
                        Text { id: nomChamp; anchors.centerIn: parent; text: pastille.modelData.nom; font.pixelSize: 13; font.weight: Font.DemiBold; color: pastille.utilise ? fenetre.accentEncre : Couleurs.texte }
                        MouseArea {
                            id: zone
                            anchors.fill: parent
                            cursorShape: glisse ? Qt.ClosedHandCursor : Qt.PointingHandCursor
                            property bool glisse: false
                            property point depart
                            onPressed: (m) => { depart = Qt.point(m.x, m.y); glisse = false }
                            onPositionChanged: (m) => {
                                if (!pressed) return
                                if (!glisse && Math.abs(m.x - depart.x) + Math.abs(m.y - depart.y) > 6) glisse = true
                                if (glisse) {
                                    var p = mapToItem(fenetre.contentItem, m.x, m.y)
                                    fantome.texte = pastille.modelData.nom
                                    fantome.x = p.x - fantome.width / 2
                                    fantome.y = p.y - fantome.height / 2
                                    fantome.visible = true
                                }
                            }
                            onReleased: (m) => {
                                fantome.visible = false
                                if (!glisse) { envoi.placer(pastille.modelData.nom); return }
                                // (lâché sur la page : le curseur va là, puis le champ)
                                var d = mapToItem(envoi.doc, m.x, m.y)
                                if (d.x >= 0 && d.y >= 0 && d.x <= envoi.doc.width && d.y <= envoi.doc.height) {
                                    envoi.doc.forceActiveFocus()
                                    envoi.doc.placerCurseur(d.x, d.y)
                                    envoi.placer(pastille.modelData.nom)
                                }
                                glisse = false
                            }
                        }
                    }
                }
            }
            Text {
                visible: envoi.source !== null && envoi.nomsUtilises.length === 0
                Layout.fillWidth: true
                text: "Placez au moins un champ dans le texte : « Chère Prénom », « il vous reste Reste »…"
                font.pixelSize: 12
                color: Couleurs.texte3
                wrapMode: Text.Wrap
            }
        }

        // Qui la reçoit ?
        ColumnLayout {
            visible: envoi.personnes.length > 0
            Layout.fillWidth: true
            spacing: 2
            Titre { text: "Qui la reçoit ?"; Layout.bottomMargin: 4 }
            Choix { text: "Toute la liste"; compte: String(envoi.personnes.length); coche: envoi.qui === "tous"; onClicked: envoi.qui = "tous" }
            Choix {
                visible: envoi.colonneReste >= 0 && envoi.doivent.length > 0
                text: "Ceux qui doivent encore (" + envoi.colonnes[envoi.colonneReste] + ")"
                compte: String(envoi.doivent.length)
                coche: envoi.qui === "doivent"
                onClicked: envoi.qui = "doivent"
            }
            Choix { text: "Au choix"; compte: envoi.qui === "choix" ? String(envoi.liste.length) : ""; coche: envoi.qui === "choix"; onClicked: envoi.qui = "choix" }
            // (au choix : une case par personne)
            QQC2.ScrollView {
                visible: envoi.qui === "choix"
                Layout.fillWidth: true
                Layout.preferredHeight: Math.min(150, envoi.personnes.length * 28 + 4)
                Layout.leftMargin: 26
                clip: true
                Column {
                    width: parent ? parent.width : 0
                    Repeater {
                        model: envoi.qui === "choix" ? envoi.personnes : []
                        delegate: QQC2.CheckBox {
                            required property var modelData
                            text: envoi.nomDe(modelData)
                            checked: !envoi.exclus[modelData.rang]
                            font.pixelSize: 13
                            onToggled: { var e = Object.assign({}, envoi.exclus); e[modelData.rang] = !checked; envoi.exclus = e }
                        }
                    }
                }
            }
        }

        // L'aperçu, personne par personne
        ColumnLayout {
            visible: envoi.liste.length > 0 && envoi.nomsUtilises.length > 0
            Layout.fillWidth: true
            spacing: 8
            Titre { text: "Aperçu" }
            Carte {
                Layout.fillWidth: true
                Layout.preferredHeight: 58
                RowLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 8
                    Outil { Layout.preferredWidth: 28; Layout.preferredHeight: 28; picto: "M15 6l-6 6 6 6"; aide: "Précédente"; enabled: envoi.apercu > 0; onClicked: envoi.apercu-- }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 1
                        Text { Layout.fillWidth: true; text: envoi.personneVue ? envoi.nomDe(envoi.personneVue) + envoi.detailVu : ""; font.pixelSize: 14; font.weight: Font.DemiBold; color: Couleurs.texte; elide: Text.ElideRight }
                        Text {
                            Layout.fillWidth: true
                            readonly property string phrase: envoi.phrasePour(envoi.personneVue)
                            text: (envoi.apercu + 1) + " sur " + envoi.liste.length + (phrase ? " · « " + phrase + " »" : "")
                            font.pixelSize: 12
                            color: Couleurs.texte2
                            elide: Text.ElideRight
                        }
                    }
                    Outil { Layout.preferredWidth: 28; Layout.preferredHeight: 28; picto: "M9 6l6 6-6 6"; aide: "Suivante"; enabled: envoi.apercu < envoi.liste.length - 1; onClicked: envoi.apercu++ }
                }
            }
        }

        // Sous quelle forme ?
        ColumnLayout {
            visible: envoi.liste.length > 0
            Layout.fillWidth: true
            spacing: 2
            Titre { text: "Sous quelle forme ?"; Layout.bottomMargin: 4 }
            Choix { text: "Un seul document à imprimer"; compte: (envoi.liste.length * envoi.pagesModele) + " pages"; coche: envoi.forme === "un"; onClicked: envoi.forme = "un" }
            Choix { text: "Un PDF par personne"; compte: ""; coche: envoi.forme === "pdf"; onClicked: envoi.forme = "pdf" }
        }

        Item { Layout.fillHeight: true }

        // Fait : où sont les documents
        Carte {
            visible: envoi.resultat !== null
            Layout.fillWidth: true
            Layout.preferredHeight: faits.implicitHeight + 24
            color: Qt.tint(Couleurs.champ, fenetre.accentFond)
            border.width: 0
            ColumnLayout {
                id: faits
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                anchors.margins: 12
                spacing: 6
                Text {
                    Layout.fillWidth: true
                    text: envoi.resultat ? (envoi.resultat.forme === "pdf" ? envoi.resultat.nombre + " PDF prêts" : "Un document de " + envoi.resultat.nombre + (envoi.resultat.nombre > 1 ? " personnes" : " personne") + " est prêt")
                                           + ", dans Documents › " + (envoi.resultat.forme === "pdf" ? envoi.resultat.dossier.split("/").pop() : envoi.resultat.fichiers[0].split("/").pop()) : ""
                    font.pixelSize: 13
                    color: Couleurs.texte
                    wrapMode: Text.Wrap
                }
                Outil {
                    text: envoi.resultat && envoi.resultat.forme === "pdf" ? "Ouvrir le dossier" : "Ouvrir le document"
                    encre: fenetre.accentEncre
                    onClicked: envoi.ouvrirResultat()
                }
            }
        }

        Text {
            Layout.fillWidth: true
            text: envoi.telephones > 0 ? "Les numéros de téléphone du tableau ne sont jamais ajoutés au document (" + envoi.telephones + (envoi.telephones > 1 ? " colonnes écartées)." : " colonne écartée).")
                                       : "Les numéros de téléphone du tableau ne sont jamais ajoutés au document."
            font.pixelSize: 12
            lineHeight: 17
            lineHeightMode: Text.FixedHeight
            color: Couleurs.texte2
            wrapMode: Text.Wrap
        }
        QQC2.AbstractButton {
            id: preparer
            Layout.fillWidth: true
            implicitHeight: 44
            enabled: !envoi.preparation && envoi.liste.length > 0 && envoi.nomsUtilises.length > 0
            hoverEnabled: true
            focusPolicy: Qt.NoFocus
            onClicked: envoi.preparer()
            background: Rectangle { radius: 11; color: !preparer.enabled && !envoi.preparation ? Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.12) : preparer.hovered ? Qt.darker(fenetre.accent, 1.1) : fenetre.accent }
            contentItem: Item {
                Row {
                    anchors.centerIn: parent
                    spacing: 8
                    Picto { anchors.verticalCenter: parent.verticalCenter; width: 16; height: 16; trace: "M7 9V4h10v5 M6 18H4v-7h16v7h-2 M7 14h10v6H7z"; encre: "#FFFFFF"; trait: 1.9 }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: envoi.preparation ? "Préparation de " + envoi.liste.length + " " + envoi.genre + "…"
                              : "Préparer " + (envoi.liste.length > 1 ? "les " + envoi.liste.length + " " + envoi.genre : envoi.liste.length === 1 ? "le document" : "")
                        font.pixelSize: 15
                        font.weight: Font.DemiBold
                        color: preparer.enabled || envoi.preparation ? "#FFFFFF" : Couleurs.texte3
                    }
                }
            }
        }
    }

    // (le champ qu'on glisse, sous la souris)
    Rectangle {
        id: fantome
        property string texte: ""
        parent: fenetre.contentItem
        z: 200
        visible: false
        width: texteFantome.implicitWidth + 18
        height: 26
        radius: 6
        color: fenetre.accentFond
        border.width: 1
        border.color: fenetre.accent
        Text { id: texteFantome; anchors.centerIn: parent; text: fantome.texte; font.pixelSize: 13; font.weight: Font.DemiBold; color: fenetre.accentEncre }
    }
}
