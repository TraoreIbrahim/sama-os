// Fichiers, l'explorateur de Sama (maquettes fic-01, fic-02, fic-04) : favoris et appareils à gauche, contenu du
// dossier au centre (grille ou liste, filtres Tous / Documents / Images / Récents), détails du fichier choisi à droite,
// corbeille, copies avec avancement et conflits. Les opérations passent par /usr/libexec/samaos/fichiers.py.
// Lancement : sama-fichiers [dossier ou adresse file://…]

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import QtCore
import Qt.labs.folderlistmodel
import "../reglages"
import "Types.js" as Types

Window {
    id: fenetre
    width: 1080
    height: 680
    minimumWidth: 760
    minimumHeight: 460
    visible: false      // montrée une fois l'identité de l'application posée
    title: "Fichiers"
    color: Couleurs.fond

    readonly property string moteur: "python3 /usr/libexec/samaos/fichiers.py "
    function chemin(url) { return decodeURIComponent(String(url).replace(/^file:\/\//, "")) }
    function adresse(p) { return "file://" + encodeURI(p).replace(/#/g, "%23").replace(/\?/g, "%3F") }
    readonly property string accueil: chemin(StandardPaths.writableLocation(StandardPaths.HomeLocation))
    readonly property var favoris: [
        { nom: "Accueil", chemin: accueil, picto: "M4 11l8-7l8 7v9h-5v-6h-6v6H4z" },
        { nom: "Bureau", chemin: chemin(StandardPaths.writableLocation(StandardPaths.DesktopLocation)), picto: "M3 4h18v12H3z M8 20h8 M12 16v4" },
        { nom: "Documents", chemin: chemin(StandardPaths.writableLocation(StandardPaths.DocumentsLocation)), picto: "M7 3h7l5 5v13H7z M14 3v5h5 M10 13h6 M10 17h4" },
        { nom: "Téléchargements", chemin: chemin(StandardPaths.writableLocation(StandardPaths.DownloadLocation)), picto: "M12 4v11 M7 10l5 5l5-5 M5 20h14" },
        { nom: "Images", chemin: chemin(StandardPaths.writableLocation(StandardPaths.PicturesLocation)), picto: "M4 5h16v14H4z M4 15l4-4l5 5 M14 13l2-2l4 4 M15 8.5h.01" }
    ]

    // ——— Où l'on est ———
    property string lieu: "dossier"          // dossier | corbeille | grenier
    property string dossier: accueil
    property var historique: [accueil]
    property int position: 0
    property string filtre: "tous"           // tous | documents | images | recents
    property string vue: "grille"            // grille | liste
    property bool caches: false              // fichiers cachés affichés
    property var selection: []               // chemins choisis
    property var presse: ({ operation: "", chemins: [] })
    property var appareils: []
    property string renommage: ""            // chemin en cours de renommage
    property bool dialogue: false            // une fenêtre de confirmation est ouverte (Échap lui revient)
    property bool saisieActive: false        // on écrit dans le champ de recherche : les touches d'édition lui reviennent

    // ——— Recherche dans le dossier et ses sous-dossiers (fichiers.py chercher) ———
    property string recherche: ""
    property var resultats: []
    property bool rechercheEnCours: false
    onRechercheChanged: { selection = []; if (recherche.trim()) attenteRecherche.restart(); else { resultats = []; rechercheEnCours = false } }
    Timer {
        id: attenteRecherche
        interval: 250        // (on attend la fin de la frappe)
        onTriggered: {
            var demandee = fenetre.recherche, ou = fenetre.dossier
            fenetre.rechercheEnCours = true
            commande.lancer(fenetre.moteur + "chercher " + commande.q(ou) + " " + commande.q(demandee.trim()), function (s) {
                if (demandee !== fenetre.recherche || ou !== fenetre.dossier) return     // (réponse d'une recherche dépassée)
                try { fenetre.resultats = JSON.parse(s) } catch (e) { fenetre.resultats = [] }
                fenetre.rechercheEnCours = false
            })
        }
    }

    readonly property string titreDossier: {
        var f = favoris.filter(function (x) { return x.chemin === dossier })
        if (f.length) return f[0].nom
        return dossier.split("/").filter(function (s) { return s }).pop() || "Ordinateur"
    }
    // Fil d'Ariane : Accueil › Documents › Cours (ou le nom de la clé USB)
    readonly property var ariane: {
        var morceaux = []
        var racine = "", nomRacine = "Ordinateur"
        var cles = appareils.filter(function (a) { return a.type === "externe" && a.montage && dossier.indexOf(a.montage) === 0 })
        if (cles.length) { racine = cles[0].montage; nomRacine = cles[0].nom }
        else if (dossier.indexOf(accueil) === 0) { racine = accueil; nomRacine = "Accueil" }
        morceaux.push({ nom: nomRacine, chemin: racine || "/" })
        var reste = dossier.substring(racine.length).split("/").filter(function (s) { return s })
        var p = racine
        reste.forEach(function (s) { p += "/" + s; morceaux.push({ nom: s, chemin: p }) })
        return morceaux
    }

    function ouvrirDossier(p, sansHistorique) {
        lieu = "dossier"
        recherche = ""
        if (filtre === "recents") filtre = "tous"
        selection = []
        renommage = ""
        dossier = p
        if (!sansHistorique) {
            historique = historique.slice(0, position + 1).concat([p])
            position = historique.length - 1
        }
    }
    function precedent() { if (position > 0) { position--; ouvrirDossier(historique[position], true) } }
    function suivant() { if (position < historique.length - 1) { position++; ouvrirDossier(historique[position], true) } }
    function remonter() { if (dossier !== "/") ouvrirDossier(dossier.substring(0, dossier.lastIndexOf("/")) || "/") }

    // Ouvrir : un dossier s'ouvre ici, un fichier dans son application
    function ouvrir(p, estDossier) {
        if (estDossier) ouvrirDossier(p)
        else Qt.openUrlExternally(adresse(p))
    }
    function choisir(p, mode) {
        if (mode === "ajout") {
            var s = selection.slice(), i = s.indexOf(p)
            if (i >= 0) s.splice(i, 1); else s.push(p)
            selection = s
        } else selection = p ? [p] : []
    }

    // ——— Opérations ———
    Commande { id: commande }
    function copier(couper) { if (selection.length) presse = { operation: couper ? "deplacer" : "copier", chemins: selection.slice() } }
    function coller() {
        if (!presse.chemins.length || lieu !== "dossier") return
        carteCopie.lancer(presse.operation, dossier, presse.chemins)
        if (presse.operation === "deplacer") presse = { operation: "", chemins: [] }
    }
    // Glisser-déposer : « Ctrl » (copie demandée) copie ; sinon déplace sur le même disque et copie vers un autre
    function deposer(adresses, destination, action) {
        var chemins = []
        for (var i = 0; i < adresses.length; i++) {
            var a = String(adresses[i])
            if (a.indexOf("file://") === 0) chemins.push(chemin(a))
        }
        // (rien à faire si on lâche des éléments dans le dossier où ils sont déjà)
        chemins = chemins.filter(function (c) { return c.substring(0, c.lastIndexOf("/")) !== destination && c !== destination })
        if (!chemins.length) return
        carteCopie.lancer(action === Qt.CopyAction ? "copier" : "deposer", destination, chemins)
    }
    function jeter(chemins) {
        if (!chemins.length) return
        commande.lancer(moteur + "jeter " + chemins.map(commande.q).join(" "), function () { relireCorbeille() })
        selection = []
    }
    function nouveauDossier() {
        commande.lancer(moteur + "nouveau-dossier " + commande.q(dossier), function (s) {
            var p = s.trim()
            if (p) { selection = [p]; renommage = p }
        })
    }
    function renommer(ancien, nom) {
        renommage = ""
        if (!nom || nom === ancien.split("/").pop()) return
        commande.lancer(moteur + "renommer " + commande.q(ancien) + " " + commande.q(nom), function (s) { if (s.trim()) selection = [s.trim()] })
    }

    // Appareils (clés USB branchées ou retirées) et corbeille : relus régulièrement
    function relireAppareils() { commande.lancer(moteur + "appareils", function (s) { try { fenetre.appareils = JSON.parse(s) } catch (e) {} }) }
    property int nombreCorbeille: 0
    function relireCorbeille() { commande.lancer("ls -1 \"${XDG_DATA_HOME:-$HOME/.local/share}/Trash/info\" 2>/dev/null | grep -c trashinfo", function (s) { fenetre.nombreCorbeille = Number(s.trim()) || 0 }) }
    Timer { interval: 3000; running: true; repeat: true; triggeredOnStart: true; onTriggered: { fenetre.relireAppareils(); fenetre.relireCorbeille() } }

    // Fichiers ouverts récemment (filtre « Récents »)
    property var recents: []
    onFiltreChanged: if (filtre === "recents") commande.lancer(moteur + "recents", function (s) { try { fenetre.recents = JSON.parse(s) } catch (e) {} })

    // Dossier courant
    FolderListModel {
        id: contenu
        folder: fenetre.adresse(fenetre.dossier)
        showDirsFirst: true
        showDotAndDotDot: false
        showHidden: fenetre.caches
        showDirs: fenetre.filtre === "tous"
        nameFilters: Types.filtres(fenetre.filtre)
        caseSensitive: false
        sortCaseSensitive: false
    }
    readonly property alias modele: contenu

    // Espace libre du disque où l'on est
    readonly property var disqueCourant: {
        var a = appareils.filter(function (x) { return x.montage && dossier.indexOf(x.montage) === 0 && x.type === "externe" })
        return a.length ? a[0] : (appareils.length ? appareils[0] : null)
    }

    Component.onCompleted: {
        // Dossier demandé au lancement (chemin ou adresse file://)
        var a = Qt.application.arguments
        var demande = a.length > 0 ? String(a[a.length - 1]) : ""
        if (demande.indexOf("file://") === 0) demande = chemin(demande)
        // Corbeille : « corbeille », l'adresse trash:/ de KDE, ou le dossier temporaire par lequel KDE la fait passer
        if (demande === "corbeille" || demande.indexOf("trash:") === 0 || /\/kio-fuse-[^/]+\/trash/.test(demande)) lieu = "corbeille"
        else if (demande.indexOf("/") === 0 && demande.indexOf(".qml") < 0) { dossier = demande; historique = [demande] }
        // Identité Wayland « samaos-fichiers » (comme le fichier .desktop) : icône dans la barre de titre et la Natte
        Qt.application.domain = ""
        Qt.application.name = "samaos-fichiers"
        visible = true
    }

    // ——— Raccourcis clavier ———
    Shortcut { sequences: [StandardKey.Copy]; enabled: !fenetre.saisieActive; onActivated: fenetre.copier(false) }
    Shortcut { sequences: [StandardKey.Cut]; enabled: !fenetre.saisieActive; onActivated: fenetre.copier(true) }
    Shortcut { sequences: [StandardKey.Paste]; enabled: !fenetre.saisieActive; onActivated: fenetre.coller() }
    Shortcut { sequences: [StandardKey.Delete]; enabled: fenetre.renommage === "" && !fenetre.saisieActive; onActivated: fenetre.jeter(fenetre.selection) }
    Shortcut { sequence: "F2"; onActivated: if (fenetre.selection.length === 1) fenetre.renommage = fenetre.selection[0] }
    Shortcut { sequences: [StandardKey.SelectAll]; enabled: fenetre.renommage === "" && !fenetre.saisieActive
               onActivated: { var l = []; for (var i = 0; i < contenu.count; i++) l.push(contenu.get(i, "filePath")); fenetre.selection = l } }
    Shortcut { sequences: ["Alt+Left", StandardKey.Back]; onActivated: fenetre.precedent() }
    Shortcut { sequences: ["Alt+Right", StandardKey.Forward]; onActivated: fenetre.suivant() }
    Shortcut { sequences: ["Alt+Up", "Backspace"]; enabled: fenetre.renommage === "" && !fenetre.saisieActive; onActivated: fenetre.remonter() }
    Shortcut { sequence: "Ctrl+Shift+N"; onActivated: fenetre.nouveauDossier() }
    Shortcut { sequence: "Ctrl+H"; onActivated: fenetre.caches = !fenetre.caches }
    Shortcut { sequence: "Escape"; enabled: !fenetre.dialogue
               onActivated: { if (fenetre.renommage) fenetre.renommage = ""; else if (fenetre.recherche) fenetre.recherche = ""; else fenetre.selection = [] } }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        BarreLaterale {
            Layout.preferredWidth: 220
            Layout.fillHeight: true
        }
        Rectangle { Layout.preferredWidth: 1; Layout.fillHeight: true; color: Couleurs.ligne }

        ColumnLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0

            Loader {
                Layout.fillWidth: true
                Layout.fillHeight: true
                sourceComponent: fenetre.lieu === "corbeille" ? vueCorbeille : fenetre.lieu === "grenier" ? vueGrenier : vueDossier
            }
        }
    }
    Component { id: vueDossier; VueDossier {} }
    Component { id: vueCorbeille; VueCorbeille {} }
    Component { id: vueGrenier; VueGrenier {} }

    // Copies et déplacements en cours (en bas à droite), conflits
    CarteCopie {
        id: carteCopie
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.margins: 18
    }
}
