// Aide hors ligne de Sama (maquette app-10) : accueil (recherche, thèmes, articles à lire en premier, aide humaine),
// thèmes, articles, résultats de recherche. Les articles sont des fichiers Markdown (/usr/share/samaos/aide/<langue>),
// lus par /usr/libexec/samaos/aide.py ; leurs liens « sama:… » ouvrent un article, un réglage ou une application.
// Lancement : sama-aide [thème/article]  (ex. sama-aide securite/arnaques)

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"

Window {
    id: fenetre
    width: 1180
    height: 760
    minimumWidth: 820
    minimumHeight: 560
    visible: false      // montrée une fois l'identité de l'application posée
    title: page.type === "article" && article ? article.titre + " — Aide" : "Aide"
    color: Couleurs.fond

    property var index: null                    // { langue, langues, themes: [{ id, titre, resume, picto, fond, encre, articles }] }
    property string prenom: ""
    property string demande: ""                 // article demandé au lancement
    readonly property var articles: {
        var l = []
        if (index) index.themes.forEach(function (t) { t.articles.forEach(function (a) { l.push(Object.assign({ theme: t }, a)) }) })
        return l
    }
    function articleDe(id) { for (var i = 0; i < articles.length; i++) if (articles[i].id === id) return articles[i]; return null }
    function themeDe(id) { if (index) for (var i = 0; i < index.themes.length; i++) if (index.themes[i].id === id) return index.themes[i]; return null }

    // ——— Navigation : accueil | theme | article | recherche, avec précédent et suivant ———
    property var historique: [{ type: "accueil" }]
    property int position: 0
    readonly property var page: historique[position]
    readonly property var article: page.type === "article" ? articleDe(page.id) : null
    function aller(p) {
        historique = historique.slice(0, position + 1).concat([p])
        position = historique.length - 1
    }
    function ouvrirArticle(id) { if (articleDe(id)) aller({ type: "article", id: id }) }
    function chercher(q) { if (q.trim()) aller({ type: "recherche", q: q.trim() }) }

    // ——— Recherche : tous les mots doivent se trouver (sans tenir compte des accents) ———
    function simple(s) {
        s = String(s).toLowerCase()
        try { s = s.normalize("NFD").replace(/[̀-ͯ]/g, "") } catch (e) {}
        return s.replace(/[’']/g, " ")
    }
    function resultats(q) {
        var mots = simple(q).split(/\s+/).filter(function (m) { return m.length > 1 })
        if (!mots.length) return []
        var res = []
        articles.forEach(function (a) {
            var titre = simple(a.titre), cles = simple(a.mots + " " + a.resume), corps = simple(a.corps)
            var score = 0
            for (var i = 0; i < mots.length; i++) {
                var m = mots[i], s = 0
                if (titre.indexOf(m) >= 0) s += 6
                if (cles.indexOf(m) >= 0) s += 3
                if (corps.indexOf(m) >= 0) s += 1
                if (!s) return
                score += s
            }
            res.push({ a: a, score: score })
        })
        res.sort(function (x, y) { return y.score - x.score })
        return res.map(function (r) { return r.a })
    }

    // ——— Liens des articles ———
    function suivreLien(lien) {
        var m
        if ((m = /^sama:article\/(.+)$/.exec(lien))) ouvrirArticle(m[1])
        else if ((m = /^sama:reglages\/(.+)$/.exec(lien))) lancer("samaos\\x2dreglages", "sama-reglages " + commande.q(m[1]))
        else if (lien === "sama:moniteur") lancer("samaos\\x2dmoniteur", "sama-moniteur")
        else if (lien === "sama:fichiers") lancer("samaos\\x2dfichiers", "sama-fichiers")
        else if (lien === "sama:capture") lancer("samaos\\x2dcapture", "sama-capture")
    }
    // (dans sa propre unité : l'application reste ouverte si l'Aide se ferme)
    function lancer(unite, ligne) {
        commande.lancer("systemd-run --user --quiet --collect --slice=app.slice "
                        + commande.q("--unit=app-" + unite + "@aide" + Date.now() + ".service") + " " + ligne)
    }

    Commande { id: commande }
    Component.onCompleted: {
        var a = Qt.application.arguments
        var dernier = a.length > 0 ? String(a[a.length - 1]) : ""
        if (/^[a-z-]+\/[a-z0-9-]+$/.test(dernier)) demande = dernier
        commande.lancer("python3 /usr/libexec/samaos/aide.py index fr", function (s) {
            try { fenetre.index = JSON.parse(s) } catch (e) { return }
            if (fenetre.demande) fenetre.historique = [{ type: "accueil" }, { type: "article", id: fenetre.demande }], fenetre.position = 1
        })
        commande.lancer("getent passwd \"$(id -un)\" | cut -d: -f5 | cut -d, -f1", function (s) { fenetre.prenom = s.trim().split(" ")[0] })
        // Identité Wayland « samaos-aide » (comme le fichier .desktop) : icône dans la barre de titre et la Natte
        Qt.application.domain = ""
        Qt.application.name = "samaos-aide"
        visible = true
    }
    Shortcut { sequences: ["Alt+Left", StandardKey.Back]; onActivated: if (fenetre.position > 0) fenetre.position-- }
    Shortcut { sequences: ["Alt+Right", StandardKey.Forward]; onActivated: if (fenetre.position < fenetre.historique.length - 1) fenetre.position++ }
    Shortcut { sequence: "Alt+Home"; onActivated: fenetre.aller({ type: "accueil" }) }

    component BoutonRond: MouseArea {
        property string picto
        width: 30
        height: 30
        hoverEnabled: true
        cursorShape: enabled ? Qt.PointingHandCursor : Qt.ArrowCursor
        opacity: enabled ? 1 : 0.45
        Rectangle { anchors.fill: parent; radius: 15; color: Couleurs.texte; opacity: parent.containsMouse && parent.enabled ? 0.07 : 0 }
        Picto { anchors.centerIn: parent; trace: parent.picto; encre: Couleurs.texte }
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // ——— Barre : précédent, suivant, accueil de l'aide, langue ———
        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 52
            Layout.leftMargin: 20
            Layout.rightMargin: 20
            spacing: 6
            BoutonRond { picto: "M15 6l-6 6l6 6"; enabled: fenetre.position > 0; onClicked: fenetre.position-- }
            BoutonRond { picto: "M9 6l6 6l-6 6"; enabled: fenetre.position < fenetre.historique.length - 1; onClicked: fenetre.position++ }
            MouseArea {
                Layout.preferredHeight: 30
                Layout.preferredWidth: accueil.implicitWidth + 20
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onClicked: if (fenetre.page.type !== "accueil") fenetre.aller({ type: "accueil" })
                Rectangle { anchors.fill: parent; radius: 15; color: Couleurs.texte; opacity: parent.containsMouse ? 0.07 : 0 }
                RowLayout {
                    id: accueil
                    anchors.centerIn: parent
                    spacing: 6
                    Picto { trace: "M4 11l8-7l8 7 M6 9.5V20h12V9.5"; encre: Couleurs.texte }
                    Text { text: "Accueil de l'aide"; font.pixelSize: 13; font.weight: Font.Medium; color: Couleurs.texte }
                }
            }
            Item { Layout.fillWidth: true }
            Text {
                text: "Langue de l'aide : Français"
                font.pixelSize: 12
                color: Couleurs.texte2
            }
            Text {
                Layout.leftMargin: 4
                text: "· julakan, wolof et kiswahili à venir"
                font.pixelSize: 12
                color: Couleurs.texte3
            }
        }
        Rectangle { Layout.fillWidth: true; height: 0.5; color: Couleurs.ligne }

        // ——— Contenu ———
        Flickable {
            id: defilement
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            contentWidth: width
            contentHeight: contenu.height
            boundsBehavior: Flickable.StopAtBounds
            QQC2.ScrollBar.vertical: QQC2.ScrollBar {}
            Loader {
                id: contenu
                width: defilement.width
                source: !fenetre.index ? "" : fenetre.page.type === "theme" ? "VueTheme.qml" : fenetre.page.type === "article" ? "VueArticle.qml"
                        : fenetre.page.type === "recherche" ? "VueRecherche.qml" : "VueAccueil.qml"
            }
        }
    }
    // (même page, autre article : revenir en haut)
    onPageChanged: defilement.contentY = 0
}
