// La Natte — barre de navigation de Sama OS (Plasma 6).
// Affiche les Espaces (activités Plasma) en pilules colorées :
// l'Espace actif se déplie et montre ses applications (épinglées + ouvertes),
// les autres restent repliés avec le nombre de fenêtres ouvertes.
// La Cour (lanceur plein écran), la Corbeille et le Pouls sont des widgets voisins dans la barre.
//
// Gestion des Espaces, sans quitter la Natte :
//  - « + » après les Espaces : un champ apparaît, Entrée crée l'Espace (couleur suivante de la palette) ;
//  - clic droit sur un Espace : Renommer (dans la Natte), Couleur, Retirer ;
//  - clic droit sur une application de l'Espace actif : l'épingler à cet Espace ou l'en retirer.
// Couleur et applications épinglées sont gardées par identifiant d'Espace (elles suivent un renommage).

import QtQuick
import QtQuick.Window
import QtQuick.Layouts
import org.kde.plasma.plasmoid
import org.kde.kirigami as Kirigami
import org.kde.activities as Activities
import org.kde.taskmanager as TaskManager
import QtCore
import Qt.labs.folderlistmodel
import org.kde.notificationmanager as NotificationManager
import QtQuick.Controls as QQC2
import org.kde.plasma.core as PlasmaCore
import org.kde.plasma.extras as PlasmaExtras
import org.kde.plasma.plasma5support as P5Support

PlasmoidItem {
    id: racine

    // Couleur et applications épinglées de chaque Espace (par nom d'activité)
    readonly property var espacesSama: ({
        "Travail": { teinte: "#B5532F", encre: "#93401F", fond: "#F2E1D5", fondSombre: "#3DB5532F",
                     epingles: ["applications:samaos-griot.desktop", "applications:samaos-docs.desktop",
                                "applications:samaos-sheet.desktop", "applications:samaos-fichiers.desktop"] },
        "École":   { teinte: "#3D5A99", encre: "#2D4682", fond: "#DFE5F2", fondSombre: "#523D5A99",
                     epingles: ["applications:samaos-griot.desktop", "applications:samaos-sugu.desktop",
                                "applications:samaos-fichiers.desktop", "applications:samaos-docs.desktop"] },
        // Espace unique quand l'utilisateur n'a pas encore choisi ses Espaces (« Plus tard » à l'accueil)
        "Accueil": { teinte: "#B5532F", encre: "#93401F", fond: "#F2E1D5", fondSombre: "#3DB5532F",
                     epingles: ["applications:samaos-griot.desktop", "applications:samaos-docs.desktop",
                                "applications:samaos-fichiers.desktop", "applications:samaos-sugu.desktop"] },
        "Maison":  { teinte: "#2F6B57", encre: "#1F5544", fond: "#DBEAE2", fondSombre: "#522F6B57",
                     epingles: ["applications:samaos-griot.desktop", "applications:samaos-fichiers.desktop",
                                "applications:samaos-photos.desktop", "applications:samaos-sugu.desktop"] }
    })
    // Espaces créés par l'utilisateur (Boutique, Association…) : teinte neutre, applications de base
    readonly property var espaceParDefaut: ({ teinte: "#8A8277", encre: "#665E54", fond: "#EDE6DC", fondSombre: "#338A8277",
                                              epingles: ["applications:samaos-griot.desktop", "applications:samaos-docs.desktop",
                                                         "applications:samaos-fichiers.desktop"] })

    // Palette proposée pour les Espaces (couleurs de Sama)
    readonly property var palette: [
        { nom: "Latérite", teinte: "#B5532F", encre: "#93401F", fond: "#F2E1D5", fondSombre: "#3DB5532F" },
        { nom: "Indigo",   teinte: "#3D5A99", encre: "#2D4682", fond: "#DFE5F2", fondSombre: "#523D5A99" },
        { nom: "Forêt",    teinte: "#2F6B57", encre: "#1F5544", fond: "#DBEAE2", fondSombre: "#522F6B57" },
        { nom: "Or",       teinte: "#C08A1E", encre: "#7A4E0C", fond: "#F6E7CC", fondSombre: "#52C08A1E" },
        { nom: "Violet",   teinte: "#7A5C99", encre: "#59407A", fond: "#E9E2F2", fondSombre: "#527A5C99" },
        { nom: "Terre",    teinte: "#8A6A4A", encre: "#634A31", fond: "#EDE3D8", fondSombre: "#528A6A4A" }
    ]
    function couleurDePalette(teinte) {
        for (var i = 0; i < palette.length; i++) {
            if (palette[i].teinte.toLowerCase() === String(teinte).toLowerCase()) return palette[i]
        }
        return null
    }

    // Personnalisation enregistrée : { "<id d'Espace>": { teinte, epingles } }
    readonly property var perso: {
        try { return JSON.parse(Plasmoid.configuration.personnalisation || "{}") } catch (e) { return {} }
    }
    function personnaliser(id, champ, valeur) {
        var p = JSON.parse(JSON.stringify(perso))
        if (!p[id]) p[id] = {}
        if (valeur === undefined) delete p[id][champ]
        else p[id][champ] = valeur
        Plasmoid.configuration.personnalisation = JSON.stringify(p)
    }
    function oublier(id) {
        var p = JSON.parse(JSON.stringify(perso))
        delete p[id]
        Plasmoid.configuration.personnalisation = JSON.stringify(p)
    }

    function infosEspace(id, nom) {
        var base = espacesSama[nom] || espaceParDefaut
        var p = perso[id]
        if (!p) return base
        var c = p.teinte ? couleurDePalette(p.teinte) : null
        return {
            teinte: c ? c.teinte : base.teinte, encre: c ? c.encre : base.encre,
            fond: c ? c.fond : base.fond, fondSombre: c ? c.fondSombre : base.fondSombre,
            epingles: p.epingles || base.epingles
        }
    }

    // Avant un renommage : on fige couleur et épingles, qui dépendaient peut-être du nom (Travail, École…)
    function figer(id, nom) {
        var i = infosEspace(id, nom)
        var p = JSON.parse(JSON.stringify(perso))
        if (!p[id]) p[id] = {}
        if (!p[id].teinte) p[id].teinte = i.teinte
        if (!p[id].epingles) p[id].epingles = i.epingles
        Plasmoid.configuration.personnalisation = JSON.stringify(p)
    }

    // --- Création, renommage, suppression ---------------------------------------------------
    property string edition: ""          // identifiant de l'Espace renommé, « nouveau » pendant une création
    property string nomEnAttente: ""     // Espace créé, en attente d'apparaître dans la liste
    property string teinteEnAttente: ""

    // Saisie terminée : on rend le clavier au panneau, sinon Plasma laisse un trait de focus au-dessus de la Natte
    onEditionChanged: if (edition === "") Qt.callLater(function () {
        racine.expanded = false
        if (racine.Window.window && racine.Window.window.contentItem) racine.Window.window.contentItem.forceActiveFocus()
    })
    // La Natte n'a pas de panneau à déplier : on ignore l'état « déplié » que Plasma lui donne avec le clavier
    onExpandedChanged: if (expanded && edition === "") Qt.callLater(function () { racine.expanded = false })

    // La Natte doit pouvoir recevoir le clavier pendant la saisie d'un nom
    Plasmoid.status: edition !== "" ? PlasmaCore.Types.AcceptingInputStatus : PlasmaCore.Types.ActiveStatus

    function teinteSuivante() {
        var prises = {}
        for (var i = 0; i < espacesOrdonnes.length; i++) {
            prises[infosEspace(espacesOrdonnes[i].id, espacesOrdonnes[i].nom).teinte.toLowerCase()] = true
        }
        for (var j = 0; j < palette.length; j++) {
            if (!prises[palette[j].teinte.toLowerCase()]) return palette[j].teinte
        }
        return palette[espacesOrdonnes.length % palette.length].teinte
    }

    function creerEspace(nom) {
        nom = String(nom).trim()
        edition = ""
        if (nom.length === 0) return
        nomEnAttente = nom
        teinteEnAttente = teinteSuivante()
        espaces.addActivity(nom, function (id) {
            if (typeof id === "string" && id.length > 0) racine.accueillir(id)
        })
    }
    // Un nouvel Espace est apparu : on lui donne sa couleur, on y va, puis on prépare son bureau
    function accueillir(id) {
        if (!nomEnAttente) return
        nomEnAttente = ""
        personnaliser(id, "teinte", teinteEnAttente)
        personnaliser(id, "prepare", true)
        espaces.setCurrentActivity(id, function () {})
        preparation.restart()
    }
    // Au démarrage de la session : préparer l'Espace actif s'il ne l'a jamais été, sinon remettre en colonne
    // les cartes que Plasma aurait déplacées (il retient leur position par résolution d'écran)
    Timer {
        id: verificationDemarrage
        interval: 6000
        running: true
        onTriggered: {
            var id = infoActivite.currentActivity
            if (id && !(racine.perso[id] && racine.perso[id].prepare)) {
                racine.personnaliser(id, "prepare", true)
                executeur.connectSource("/usr/libexec/samaos/preparer-espace.sh")
            } else {
                executeur.connectSource("/usr/libexec/samaos/preparer-espace.sh ranger")
            }
        }
    }
    // Résolution de l'écran modifiée : les cartes peuvent avoir été déplacées
    readonly property size tailleEcran: Qt.size(Screen.width, Screen.height)
    onTailleEcranChanged: rangement.restart()
    Timer {
        id: rangement
        interval: 3000
        onTriggered: executeur.connectSource("/usr/libexec/samaos/preparer-espace.sh ranger")
    }

    // Première visite d'un Espace (créé ailleurs que dans la Natte, ex. à l'accueil) : on prépare son bureau une fois
    Connections {
        target: infoActivite
        function onCurrentActivityChanged() {
            var id = infoActivite.currentActivity
            if (id && !(racine.perso[id] && racine.perso[id].prepare)) {
                racine.personnaliser(id, "prepare", true)
                preparation.restart()
            }
        }
    }

    // Fond d'écran du mode en cours et cartes du bureau (une fois l'Espace affiché)
    Timer {
        id: preparation
        interval: 900
        onTriggered: executeur.connectSource("/usr/libexec/samaos/preparer-espace.sh")
    }
    P5Support.DataSource {
        id: executeur
        engine: "executable"
        onNewData: source => disconnectSource(source)
    }

    function renommerEspace(id, ancien, nom) {
        nom = String(nom).trim()
        edition = ""
        if (nom.length === 0 || nom === ancien) return
        figer(id, ancien)
        espaces.setActivityName(id, nom, function () {})
    }

    function retirerEspace(id) {
        if (espacesOrdonnes.length < 2) return
        if (id === infoActivite.currentActivity) {
            for (var i = 0; i < espacesOrdonnes.length; i++) {
                if (espacesOrdonnes[i].id !== id) {
                    espaces.setCurrentActivity(espacesOrdonnes[i].id, function () {})
                    break
                }
            }
        }
        espaces.removeActivity(id, function () {})
        oublier(id)
    }

    // Épingler / désépingler une application dans l'Espace actif
    function estEpinglee(url) {
        return infosEspace(infoActivite.currentActivity, nomActif).epingles.indexOf(String(url)) >= 0
    }
    function basculerEpingle(url) {
        url = String(url)
        if (!url) return
        figer(infoActivite.currentActivity, nomActif)
        var liste = infosEspace(infoActivite.currentActivity, nomActif).epingles.slice()
        var i = liste.indexOf(url)
        if (i >= 0) liste.splice(i, 1)
        else liste.push(url)
        personnaliser(infoActivite.currentActivity, "epingles", liste)
    }

    // Mesures de la maquette (px) : éléments de 44, tuiles de 34, 8 d'écart ; × 0,82 (compacte) ou × 1,18 (grande)
    // selon le réglage « Taille de la Natte »
    readonly property real echelle: Plasmoid.configuration.taille === "compacte" ? 0.82
                                  : Plasmoid.configuration.taille === "grande" ? 1.18 : 1
    readonly property int hauteurPilule: Math.round(44 * echelle)
    readonly property int tailleTuile: Math.round(34 * echelle)
    readonly property int ecart: Math.round(8 * echelle)
    readonly property color encreDouce: Kirigami.Theme.disabledTextColor

    // Applications dont l'icône est déjà une tuile Sama (pas de fond à ajouter)
    readonly property var appsAvecTuile: ["samaos-", "konsole", "systemsettings", "khelpcenter", "okular",
                                          "org.kde.ark", "systemmonitor", "spectacle"]
    function aUneTuileSama(url, appId) {
        var texte = (String(url) + " " + String(appId)).toLowerCase()
        for (var i = 0; i < appsAvecTuile.length; i++) {
            if (texte.indexOf(appsAvecTuile[i]) >= 0) return true
        }
        return false
    }

    // Éléments dans la Corbeille (le dossier est surveillé : le compteur suit en direct)
    FolderListModel {
        id: contenuCorbeille
        folder: StandardPaths.writableLocation(StandardPaths.GenericDataLocation) + "/Trash/files"
        showHidden: true
        showDotAndDotDot: false
    }
    // --- Téléchargements --------------------------------------------------------------------
    readonly property url dossierTelechargements: StandardPaths.writableLocation(StandardPaths.DownloadLocation)
    // Extensions des fichiers en cours d'écriture par les navigateurs (Chromium/Griot, Firefox, Safari…)
    readonly property var extensionsPartielles: [".crdownload", ".part", ".partial", ".download", ".tmp"]

    FolderListModel {
        id: contenuTelechargements
        folder: racine.dossierTelechargements
        showHidden: false
        showDotAndDotDot: false
        sortField: FolderListModel.Time
        onCountChanged: racine.examinerTelechargements()
        onStatusChanged: racine.examinerTelechargements()
    }

    property bool nouveauxTelechargements: false
    property bool telechargementPartiel: false

    function examinerTelechargements() {
        var vu = Number(Plasmoid.configuration.dernierCoupDOeilTelechargements || 0)
        var nouveaux = false
        var partiel = false
        for (var i = 0; i < contenuTelechargements.count; i++) {
            var nom = String(contenuTelechargements.get(i, "fileName")).toLowerCase()
            var estPartiel = false
            for (var j = 0; j < extensionsPartielles.length; j++) {
                if (nom.endsWith(extensionsPartielles[j])) estPartiel = true
            }
            if (estPartiel) {
                partiel = true
            } else if (vu > 0 && contenuTelechargements.get(i, "fileModified").getTime() > vu) {
                nouveaux = true
            }
        }
        nouveauxTelechargements = nouveaux
        telechargementPartiel = partiel
    }

    function ouvrirTelechargements() {
        Plasmoid.configuration.dernierCoupDOeilTelechargements = String(Date.now())
        nouveauxTelechargements = false
        Qt.openUrlExternally(dossierTelechargements)
    }

    Component.onCompleted: {
        // Premier lancement : les fichiers déjà présents ne sont pas « nouveaux »
        if (!Plasmoid.configuration.dernierCoupDOeilTelechargements) {
            Plasmoid.configuration.dernierCoupDOeilTelechargements = String(Date.now())
        }
    }

    // Transferts suivis par le système (copies vers Téléchargements, navigateurs compatibles) : pourcentage réel
    NotificationManager.Notifications {
        id: transferts
        showNotifications: false
        showJobs: true
        showExpired: false
        showDismissed: true
        onDataChanged: racine.versionTransferts++
        onRowsInserted: racine.versionTransferts++
        onRowsRemoved: racine.versionTransferts++
    }
    property int versionTransferts: 0
    readonly property int progressionTelechargement: {
        versionTransferts   // recalcul à chaque changement des transferts
        var total = 0
        var n = 0
        var dossier = String(dossierTelechargements)
        for (var i = 0; i < transferts.count; i++) {
            var idx = transferts.index(i, 0)
            if (transferts.data(idx, NotificationManager.Notifications.JobStateRole) !== NotificationManager.Notifications.JobStateRunning) continue
            var details = transferts.data(idx, NotificationManager.Notifications.JobDetailsRole)
            var dest = details ? String(details.effectiveDestUrl || details.destUrl || "") : ""
            if (dest.indexOf(dossier) !== 0) continue
            total += transferts.data(idx, NotificationManager.Notifications.PercentageRole)
            n++
        }
        return n > 0 ? Math.round(total / n) : -1
    }
    readonly property bool telechargementEnCours: progressionTelechargement >= 0 || telechargementPartiel

    // Tant que la Corbeille n'a jamais servi, son dossier n'existe pas : on vérifie que la liste vient bien de lui
    readonly property int nombreDansCorbeille: contenuCorbeille.count > 0
        && String(contenuCorbeille.get(0, "filePath")).indexOf("/Trash/files/") >= 0 ? contenuCorbeille.count : 0

    preferredRepresentation: fullRepresentation

    Activities.ActivityModel {
        id: espaces
        shownStates: "Running"
    }

    TaskManager.ActivityInfo { id: infoActivite }
    TaskManager.VirtualDesktopInfo { id: infoBureau }

    // Liste des Espaces dans l'ordre de Sama (Travail, École, Maison, puis les autres)
    property var espacesOrdonnes: []
    readonly property var rangEspaces: ({ "Accueil": 0, "Travail": 0, "École": 1, "Maison": 2 })

    function reordonner() {
        var liste = []
        for (var i = 0; i < collecte.count; i++) {
            var o = collecte.objectAt(i)
            if (o && o.idEspace) {
                liste.push({ id: o.idEspace, nom: o.nomEspace })
            }
        }
        liste.sort(function (a, b) {
            var ra = (a.nom in rangEspaces) ? rangEspaces[a.nom] : 9
            var rb = (b.nom in rangEspaces) ? rangEspaces[b.nom] : 9
            return ra - rb
        })
        espacesOrdonnes = liste
        if (nomEnAttente) {
            for (var j = 0; j < liste.length; j++) {
                if (liste[j].nom === nomEnAttente && !perso[liste[j].id]) {
                    accueillir(liste[j].id)
                    break
                }
            }
        }
    }

    Instantiator {
        id: collecte
        model: espaces
        delegate: QtObject {
            readonly property string idEspace: model.id
            readonly property string nomEspace: model.name
            onNomEspaceChanged: racine.reordonner()
        }
        onObjectAdded: racine.reordonner()
        onObjectRemoved: racine.reordonner()
    }

    // Nom de l'Espace actif
    readonly property string nomActif: {
        for (var i = 0; i < espacesOrdonnes.length; i++) {
            if (espacesOrdonnes[i].id === infoActivite.currentActivity) {
                return espacesOrdonnes[i].nom
            }
        }
        return ""
    }

    // Applications de l'Espace actif : épinglées + fenêtres ouvertes dans cet Espace
    TaskManager.TasksModel {
        id: applications
        activity: infoActivite.currentActivity
        virtualDesktop: infoBureau.currentDesktop
        filterByActivity: true
        separateLaunchers: false
        launchInPlace: true
        groupMode: TaskManager.TasksModel.GroupApplications
        sortMode: TaskManager.TasksModel.SortManual
        launcherList: racine.infosEspace(infoActivite.currentActivity, racine.nomActif).epingles
    }

    // Champ de saisie d'un nom d'Espace, dans la Natte : Entrée valide, Échap annule
    component ChampNom: QQC2.TextField {
        id: champNom
        signal valide(string nom)
        implicitWidth: Math.max(text.length > 0 ? 90 : 140, contentWidth + 28)
        implicitHeight: 30
        Layout.preferredWidth: implicitWidth
        Layout.preferredHeight: 30
        Layout.alignment: Qt.AlignVCenter
        leftPadding: 12
        rightPadding: 12
        font.pixelSize: 12
        font.weight: Font.DemiBold
        color: Kirigami.Theme.textColor
        maximumLength: 24
        selectByMouse: true
        background: Rectangle {
            radius: height / 2
            color: Kirigami.Theme.backgroundColor
            border.width: 1
            border.color: "#B5532F"
        }
        // La Natte n'a pas forcément le clavier (ex. après un clic dans un menu) : on le demande à sa fenêtre,
        // en réessayant quelques fois le temps que le menu se ferme et que la fenêtre soit activée
        property int essais: 0
        property bool aEuLeClavier: false
        Component.onCompleted: prendreLeClavier.start()
        Timer {
            id: prendreLeClavier
            interval: 80
            repeat: true
            onTriggered: {
                if (champNom.Window.window && !champNom.Window.window.active) champNom.Window.window.requestActivate()
                champNom.forceActiveFocus()
                if (champNom.activeFocus) {
                    champNom.aEuLeClavier = true
                    champNom.selectAll()
                    stop()
                } else if (++champNom.essais > 15) {
                    stop()
                    racine.edition = ""
                }
            }
        }
        // Clic ailleurs (la Natte perd le clavier) : on abandonne la saisie
        Connections {
            target: champNom.Window.window
            function onActiveChanged() {
                if (champNom.aEuLeClavier && champNom.Window.window && !champNom.Window.window.active) racine.edition = ""
            }
        }
        onAccepted: valide(text)
        Keys.onEscapePressed: racine.edition = ""
    }

    // Menu d'un Espace (clic droit)
    PlasmaExtras.Menu {
        id: menuEspace
        placement: PlasmaExtras.Menu.TopPosedLeftAlignedPopup
        property string idCible: ""
        property string nomCible: ""
        readonly property string teinteCible: idCible ? racine.infosEspace(idCible, nomCible).teinte.toLowerCase() : ""

        PlasmaExtras.MenuItem {
            text: "Aperçu des Espaces"
            icon: "view-grid"
            onClicked: racine.basculerVue()
        }
        PlasmaExtras.MenuItem {
            text: "Renommer"
            icon: "edit-rename"
            onClicked: racine.edition = menuEspace.idCible
        }
        PlasmaExtras.MenuItem { separator: true }
        PlasmaExtras.MenuItem { section: true; text: "Couleur" }
        PlasmaExtras.MenuItem {
            text: racine.palette[0].nom; checkable: true
            checked: menuEspace.teinteCible === racine.palette[0].teinte.toLowerCase()
            onClicked: racine.personnaliser(menuEspace.idCible, "teinte", racine.palette[0].teinte)
        }
        PlasmaExtras.MenuItem {
            text: racine.palette[1].nom; checkable: true
            checked: menuEspace.teinteCible === racine.palette[1].teinte.toLowerCase()
            onClicked: racine.personnaliser(menuEspace.idCible, "teinte", racine.palette[1].teinte)
        }
        PlasmaExtras.MenuItem {
            text: racine.palette[2].nom; checkable: true
            checked: menuEspace.teinteCible === racine.palette[2].teinte.toLowerCase()
            onClicked: racine.personnaliser(menuEspace.idCible, "teinte", racine.palette[2].teinte)
        }
        PlasmaExtras.MenuItem {
            text: racine.palette[3].nom; checkable: true
            checked: menuEspace.teinteCible === racine.palette[3].teinte.toLowerCase()
            onClicked: racine.personnaliser(menuEspace.idCible, "teinte", racine.palette[3].teinte)
        }
        PlasmaExtras.MenuItem {
            text: racine.palette[4].nom; checkable: true
            checked: menuEspace.teinteCible === racine.palette[4].teinte.toLowerCase()
            onClicked: racine.personnaliser(menuEspace.idCible, "teinte", racine.palette[4].teinte)
        }
        PlasmaExtras.MenuItem {
            text: racine.palette[5].nom; checkable: true
            checked: menuEspace.teinteCible === racine.palette[5].teinte.toLowerCase()
            onClicked: racine.personnaliser(menuEspace.idCible, "teinte", racine.palette[5].teinte)
        }
        PlasmaExtras.MenuItem { separator: true }
        PlasmaExtras.MenuItem {
            text: "Nouvel Espace…"
            icon: "list-add"
            onClicked: racine.edition = "nouveau"
        }
        PlasmaExtras.MenuItem {
            text: "Retirer cet Espace"
            icon: "edit-delete-remove"
            enabled: racine.espacesOrdonnes.length > 1
            onClicked: racine.retirerEspace(menuEspace.idCible)
        }
    }
    // Vue d'ensemble des Espaces (Méta+Tab, ou menu d'un Espace)
    property var vueEspaces: null
    function basculerVue() {
        if (!vueEspaces) {
            var composant = Qt.createComponent("VueEspaces.qml")
            if (composant.status !== Component.Ready) {
                console.warn("Vue des Espaces :", composant.errorString())
                return
            }
            vueEspaces = composant.createObject(racine, { visualParent: racine })
        }
        vueEspaces.toggle()
    }
    Connections {
        target: Plasmoid
        function onActivated() { racine.basculerVue() }
    }

    function ouvrirMenuEspace(id, nom, element) {
        menuEspace.idCible = id
        menuEspace.nomCible = nom
        menuEspace.visualParent = element
        menuEspace.openRelative()
    }

    // Menu d'une application de l'Espace actif (clic droit)
    PlasmaExtras.Menu {
        id: menuApplication
        placement: PlasmaExtras.Menu.TopPosedLeftAlignedPopup
        property string urlCible: ""
        property int indexCible: -1
        property bool ouverteCible: false

        PlasmaExtras.MenuItem {
            text: racine.estEpinglee(menuApplication.urlCible) ? "Retirer de « " + racine.nomActif + " »"
                                                                : "Épingler dans « " + racine.nomActif + " »"
            icon: racine.estEpinglee(menuApplication.urlCible) ? "window-unpin" : "window-pin"
            enabled: menuApplication.urlCible.length > 0
            onClicked: racine.basculerEpingle(menuApplication.urlCible)
        }
        PlasmaExtras.MenuItem {
            text: "Fermer la fenêtre"
            icon: "window-close"
            visible: menuApplication.ouverteCible
            onClicked: applications.requestClose(applications.makeModelIndex(menuApplication.indexCible))
        }
    }

    component Separateur: Rectangle {
        Layout.preferredWidth: 1
        Layout.preferredHeight: 28
        Layout.leftMargin: 2
        Layout.rightMargin: 2
        color: Kirigami.Theme.textColor
        opacity: 0.12
    }

    fullRepresentation: RowLayout {
        spacing: racine.ecart
        Layout.minimumHeight: racine.hauteurPilule
        Layout.preferredHeight: racine.hauteurPilule
        Layout.maximumHeight: racine.hauteurPilule

        Separateur {}

        Repeater {
            model: racine.espacesOrdonnes

            delegate: Item {
                id: espace
                // Réglage « Afficher les autres Espaces » : l'Espace actif reste toujours là (avec ses applications)
                visible: actif || Plasmoid.configuration.afficherEspaces

                readonly property string idEspace: modelData.id
                readonly property string nomEspace: modelData.nom
                readonly property bool actif: idEspace === infoActivite.currentActivity
                readonly property var infos: racine.infosEspace(idEspace, nomEspace)
                readonly property bool enEdition: racine.edition === idEspace

                Layout.preferredHeight: racine.hauteurPilule
                Layout.preferredWidth: actif ? piluleActive.implicitWidth : piluleRepliee.implicitWidth

                Behavior on Layout.preferredWidth {
                    NumberAnimation { duration: Kirigami.Units.longDuration; easing.type: Easing.OutCubic }
                }

                // Espace actif : nom + applications
                Rectangle {
                    id: piluleActive
                    visible: espace.actif
                    anchors.fill: parent
                    radius: height / 2
                    color: Kirigami.Theme.textColor.hslLightness > 0.5 ? espace.infos.fondSombre : espace.infos.fond
                    implicitWidth: rangeeActive.implicitWidth + 14 + 6

                    // Clic droit sur la pilule (hors applications) : menu de l'Espace
                    MouseArea {
                        anchors.fill: parent
                        acceptedButtons: Qt.RightButton
                        onClicked: racine.ouvrirMenuEspace(espace.idEspace, espace.nomEspace, espace)
                        onDoubleClicked: racine.edition = espace.idEspace
                    }

                    RowLayout {
                        id: rangeeActive
                        anchors.top: parent.top
                        anchors.bottom: parent.bottom
                        anchors.left: parent.left
                        anchors.leftMargin: 14
                        spacing: 6

                        Text {
                            visible: !espace.enEdition
                            text: espace.nomEspace
                            font.weight: Font.DemiBold
                            font.pixelSize: 12
                            color: Kirigami.Theme.textColor.hslLightness > 0.5 ? Kirigami.Theme.textColor : espace.infos.encre
                            Layout.rightMargin: 6
                            // Double-clic sur le nom : renommer
                            MouseArea {
                                anchors.fill: parent
                                acceptedButtons: Qt.LeftButton | Qt.RightButton
                                onClicked: mouse => { if (mouse.button === Qt.RightButton) racine.ouvrirMenuEspace(espace.idEspace, espace.nomEspace, espace) }
                                onDoubleClicked: racine.edition = espace.idEspace
                            }
                        }
                        Loader {
                            active: espace.enEdition
                            visible: active
                            Layout.rightMargin: 6
                            sourceComponent: ChampNom {
                                text: espace.nomEspace
                                onValide: nom => racine.renommerEspace(espace.idEspace, espace.nomEspace, nom)
                            }
                        }

                        Repeater {
                            model: applications

                            delegate: MouseArea {
                                id: tuile
                                // Fenêtres du système (autorisation…) : pas de tuile dans la Natte
                                visible: String(model.AppId).indexOf("samaos-autorisation") < 0
                                readonly property bool ouverte: !model.IsLauncher
                                readonly property bool tuileSama: racine.aUneTuileSama(model.LauncherUrlWithoutIcon, model.AppId)
                                Layout.preferredWidth: racine.tailleTuile
                                Layout.preferredHeight: racine.hauteurPilule
                                hoverEnabled: true
                                acceptedButtons: Qt.LeftButton | Qt.RightButton
                                onClicked: mouse => {
                                    if (mouse.button === Qt.RightButton) {
                                        menuApplication.urlCible = String(model.LauncherUrlWithoutIcon || "")
                                        menuApplication.indexCible = index
                                        menuApplication.ouverteCible = tuile.ouverte
                                        menuApplication.visualParent = tuile
                                        menuApplication.openRelative()
                                    } else {
                                        applications.requestActivate(applications.makeModelIndex(index))
                                    }
                                }

                                Rectangle {
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    anchors.top: parent.top
                                    anchors.topMargin: 4
                                    width: racine.tailleTuile
                                    height: width
                                    radius: Math.round(11 * racine.echelle)
                                    color: tuile.tuileSama ? "transparent" : Kirigami.Theme.backgroundColor
                                    opacity: model.IsStartup === true ? 0.15 : 1
                                    // Survol : la tuile se soulève de 3 px, comme dans la maquette
                                    transform: Translate {
                                        y: tuile.containsMouse ? -3 : 0
                                        Behavior on y { NumberAnimation { duration: 220; easing.type: Easing.OutCubic } }
                                    }

                                    Kirigami.Icon {
                                        anchors.centerIn: parent
                                        width: tuile.tuileSama ? parent.width : parent.width * 0.7
                                        height: width
                                        roundToIconSize: false   // suit la taille de la Natte (sinon arrondi à 32 px)
                                        source: model.decoration
                                    }
                                }

                                // Application en cours d'ouverture : la trompe se balance par-dessus la tuile
                                ChargementSama {
                                    visible: model.IsStartup === true
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    anchors.top: parent.top
                                    anchors.topMargin: 4
                                    width: racine.tailleTuile
                                    height: width
                                    couleur: espace.infos.teinte
                                    fond: Kirigami.Theme.textColor.hslLightness > 0.5 ? "#1E2233" : espace.infos.fond
                                }

                                // Point sous les applications ouvertes
                                Rectangle {
                                    anchors.horizontalCenter: parent.horizontalCenter
                                    anchors.top: parent.top
                                    anchors.topMargin: 4 + racine.tailleTuile + 2
                                    width: 4
                                    height: 4
                                    radius: 2
                                    color: espace.infos.encre
                                    visible: tuile.ouverte
                                }
                            }
                        }
                    }
                }

                // Fenêtres ouvertes dans cet Espace (pour le compteur)
                TaskManager.TasksModel {
                    id: fenetresEspace
                    activity: espace.idEspace
                    filterByActivity: true
                    filterByVirtualDesktop: false
                    groupMode: TaskManager.TasksModel.GroupDisabled
                }

                // Espace replié : point de couleur + nom + nombre de fenêtres
                MouseArea {
                    id: piluleRepliee
                    visible: !espace.actif
                    anchors.fill: parent
                    hoverEnabled: true
                    implicitWidth: rangeeRepliee.implicitWidth + 28
                    acceptedButtons: Qt.LeftButton | Qt.RightButton
                    onClicked: mouse => {
                        if (mouse.button === Qt.RightButton) racine.ouvrirMenuEspace(espace.idEspace, espace.nomEspace, espace)
                        else espaces.setCurrentActivity(espace.idEspace, function () {})
                    }

                    Rectangle {
                        anchors.fill: parent
                        radius: height / 2
                        color: Kirigami.Theme.textColor
                        opacity: piluleRepliee.containsMouse ? 0.08 : 0
                    }

                    Row {
                        id: rangeeRepliee
                        anchors.centerIn: parent
                        spacing: 8

                        Rectangle {
                            anchors.verticalCenter: parent.verticalCenter
                            width: 8
                            height: 8
                            radius: 4
                            color: espace.infos.teinte
                        }
                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            visible: !espace.enEdition
                            text: espace.nomEspace
                            font.pixelSize: 12
                            font.weight: Font.Medium
                            color: racine.encreDouce
                        }
                        Loader {
                            anchors.verticalCenter: parent.verticalCenter
                            active: espace.enEdition
                            visible: active
                            sourceComponent: ChampNom {
                                text: espace.nomEspace
                                onValide: nom => racine.renommerEspace(espace.idEspace, espace.nomEspace, nom)
                            }
                        }
                        Rectangle {
                            anchors.verticalCenter: parent.verticalCenter
                            visible: fenetresEspace.count > 0
                            width: Math.max(18, compteur.implicitWidth + 8)
                            height: 18
                            radius: 9
                            color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.1)
                            Text {
                                id: compteur
                                anchors.centerIn: parent
                                text: fenetresEspace.count
                                font.pixelSize: Kirigami.Theme.smallFont.pixelSize - 1
                                font.weight: Font.DemiBold
                                color: Kirigami.Theme.disabledTextColor
                            }
                        }
                    }
                }
            }
        }

        // Nouvel Espace : « + » discret, ou champ de saisie pendant la création
        MouseArea {
            id: boutonPlus
            visible: racine.edition !== "nouveau" && Plasmoid.configuration.afficherEspaces
            Layout.preferredWidth: 28
            Layout.preferredHeight: 28
            Layout.alignment: Qt.AlignVCenter
            hoverEnabled: true
            onClicked: racine.edition = "nouveau"
            Rectangle {
                anchors.fill: parent
                radius: width / 2
                color: Kirigami.Theme.textColor
                opacity: boutonPlus.containsMouse ? 0.1 : 0
            }
            Text {
                anchors.centerIn: parent
                anchors.verticalCenterOffset: -1
                text: "+"
                font.pixelSize: 18
                font.weight: Font.Light
                color: racine.encreDouce
            }
        }
        Loader {
            active: racine.edition === "nouveau"
            visible: active
            Layout.alignment: Qt.AlignVCenter
            sourceComponent: ChampNom {
                placeholderText: "Nom de l'Espace"
                onValide: nom => racine.creerEspace(nom)
            }
        }

        Separateur { visible: Plasmoid.configuration.afficherTelechargements || Plasmoid.configuration.afficherCorbeille }

        // Téléchargements : ouvre le dossier ; point latérite = nouveaux fichiers ; barre = téléchargement en cours
        MouseArea {
            id: telechargements
            visible: Plasmoid.configuration.afficherTelechargements
            Layout.preferredWidth: racine.hauteurPilule
            Layout.preferredHeight: racine.hauteurPilule
            hoverEnabled: true
            onClicked: racine.ouvrirTelechargements()

            Rectangle {
                anchors.fill: parent
                radius: width / 2
                color: Kirigami.Theme.textColor
                opacity: telechargements.containsMouse ? 0.1 : 0.05
            }
            Kirigami.Icon {
                anchors.centerIn: parent
                anchors.verticalCenterOffset: racine.telechargementEnCours ? -3 : 0
                width: Math.round(24 * racine.echelle)
                height: width
                isMask: true
                color: Kirigami.Theme.textColor
                source: Qt.resolvedUrl("../icons/telechargements.svg")
                Behavior on anchors.verticalCenterOffset { NumberAnimation { duration: 200 } }
            }
            // Nouveaux fichiers
            Rectangle {
                visible: racine.nouveauxTelechargements && !racine.telechargementEnCours
                anchors.top: parent.top
                anchors.right: parent.right
                anchors.topMargin: 7
                anchors.rightMargin: 7
                width: 9
                height: 9
                radius: 4.5
                color: "#B5532F"
            }
            // Téléchargement en cours : pourcentage s'il est connu, sinon barre qui va et vient
            Rectangle {
                id: piste
                visible: racine.telechargementEnCours
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.bottom: parent.bottom
                anchors.bottomMargin: 9
                width: 22
                height: 3
                radius: 1.5
                clip: true
                color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.15)
                // Pourcentage connu
                Rectangle {
                    visible: racine.progressionTelechargement >= 0
                    height: parent.height
                    radius: parent.radius
                    color: "#B5532F"
                    width: parent.width * Math.max(0, racine.progressionTelechargement) / 100
                    Behavior on width { NumberAnimation { duration: 300 } }
                }
                // Pourcentage inconnu (navigateur) : segment qui va et vient
                Rectangle {
                    visible: racine.progressionTelechargement < 0
                    height: parent.height
                    radius: parent.radius
                    color: "#B5532F"
                    width: parent.width * 0.4
                    NumberAnimation on x {
                        running: piste.visible && racine.progressionTelechargement < 0
                        loops: Animation.Infinite
                        from: -piste.width * 0.4
                        to: piste.width
                        duration: 1100
                        easing.type: Easing.InOutQuad
                    }
                }
            }
        }

        // La Corbeille : bouton rond discret
        MouseArea {
            id: corbeille
            visible: Plasmoid.configuration.afficherCorbeille
            Layout.preferredWidth: racine.hauteurPilule
            Layout.preferredHeight: racine.hauteurPilule
            hoverEnabled: true
            // (Corbeille de Fichiers : l'adresse « trash:/ » de KDE arriverait sous forme de dossier temporaire)
            onClicked: executeur.connectSource("sama-fichiers corbeille")

            Rectangle {
                anchors.fill: parent
                radius: width / 2
                color: Kirigami.Theme.textColor
                opacity: corbeille.containsMouse ? 0.1 : 0.05
            }
            Kirigami.Icon {
                anchors.centerIn: parent
                width: Math.round(24 * racine.echelle)
                height: width
                isMask: true
                color: Kirigami.Theme.textColor
                source: Qt.resolvedUrl("../icons/corbeille.svg")
            }
            // Nombre d'éléments dans la Corbeille
            Rectangle {
                visible: racine.nombreDansCorbeille > 0
                anchors.top: parent.top
                anchors.right: parent.right
                anchors.topMargin: 4
                anchors.rightMargin: 4
                width: Math.max(16, nombreCorbeille.implicitWidth + 8)
                height: 16
                radius: 8
                color: "#B5532F"
                Text {
                    id: nombreCorbeille
                    anchors.centerIn: parent
                    text: racine.nombreDansCorbeille > 99 ? "99+" : racine.nombreDansCorbeille
                    font.pixelSize: 10
                    font.weight: Font.DemiBold
                    color: "#FFFFFF"
                }
            }
        }
    }
}
