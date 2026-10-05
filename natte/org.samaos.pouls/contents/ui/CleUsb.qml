// Carte « Clé USB détectée » (maquette fic-05), en bas à droite au-dessus de la Natte : nom et place de la clé,
// ce qu'elle contient (photos, documents…), « Ouvrir » dans Fichiers, « Importer les photos » dans Images.
// Les clés sont relues toutes les 3 s (/usr/libexec/samaos/fichiers.py appareils) ; une clé déjà branchée à
// l'ouverture de session ne montre pas de carte.

import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami
import org.kde.plasma.core as PlasmaCore
import org.kde.plasma.plasmoid
import org.kde.plasma.plasma5support as P5Support

Item {
    id: cle

    readonly property string moteur: "python3 /usr/libexec/samaos/fichiers.py "
    property var connues: null            // chemins des clés déjà vues (null : première lecture)
    property var carte: null              // clé montrée : { chemin, nom, taille }
    property var analyse: null            // résultat de « analyser »
    property string tache: ""             // importation en cours
    property var importation: null        // avancement de l'importation

    readonly property bool sombre: Kirigami.Theme.backgroundColor.hslLightness < 0.5
    readonly property color texte2: sombre ? "#ADA698" : "#665E54"
    readonly property color texte3: sombre ? "#8C867A" : "#8A8277"
    readonly property color fondDouce: sombre ? Qt.rgba(1, 1, 1, 0.06) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.05)
    readonly property rect zoneEcran: Plasmoid.containment ? Plasmoid.containment.availableScreenRect : Qt.rect(0, 0, 1280, 800)

    function taille(o) {
        if (o >= 1073741824) return (o / 1073741824).toLocaleString(Qt.locale(), "f", 1).replace(/[,.]0$/, "") + " Go"
        if (o >= 1048576) return Math.round(o / 1048576) + " Mo"
        return Math.max(1, Math.round(o / 1024)) + " Ko"
    }
    function pluriel(n, mot) { return n + " " + mot + (n > 1 ? "s" : "") }
    function resume(a) {
        if (!a) return "Lecture de la clé…"
        if (a.erreur) return a.erreur
        var l = []
        if (a.photos) l.push(pluriel(a.photos, "photo"))
        if (a.documents) l.push(pluriel(a.documents, "document"))
        if (a.videos) l.push(pluriel(a.videos, "vidéo"))
        if (a.musiques) l.push(a.musiques + (a.musiques > 1 ? " morceaux" : " morceau") + " de musique")
        return l.length ? l.join(" · ") : "Ni photos ni documents sur cette clé"
    }

    // Commandes ponctuelles
    P5Support.DataSource {
        id: executeur
        engine: "executable"
        property var rappels: ({})
        function lancer(commande, rappel) {
            var r = rappels; r[commande] = rappel || null; rappels = r
            connectSource(commande)
        }
        onNewData: (source, donnees) => {
            var rappel = rappels[source]
            disconnectSource(source)
            if (rappel) rappel(String(donnees["stdout"] || ""))
        }
    }
    function q(t) { return "'" + String(t).replace(/'/g, "'\\''") + "'" }

    // Clés branchées
    P5Support.DataSource {
        engine: "executable"
        connectedSources: [cle.moteur + "appareils"]
        interval: 3000
        onNewData: (source, donnees) => {
            var liste
            try { liste = JSON.parse(String(donnees["stdout"])) } catch (e) { return }
            var externes = liste.filter(function (a) { return a.type === "externe" })
            var chemins = externes.map(function (a) { return a.chemin })
            if (cle.connues !== null) {
                var nouvelles = externes.filter(function (a) { return cle.connues.indexOf(a.chemin) < 0 })
                if (nouvelles.length) cle.montrer(nouvelles[0])
            }
            // Clé retirée : sa carte s'en va (sauf importation en cours)
            if (cle.carte && chemins.indexOf(cle.carte.chemin) < 0 && !cle.tache) cle.cacher()
            cle.connues = chemins
        }
    }

    function montrer(a) {
        carte = { chemin: a.chemin, nom: a.nom, taille: a.taille }
        analyse = null
        importation = null
        tache = ""
        executeur.lancer(moteur + "analyser " + q(a.chemin), function (s) { try { cle.analyse = JSON.parse(s) } catch (e) {} })
        fermeture.restart()
    }
    function cacher() { carte = null; analyse = null; importation = null; tache = "" }

    // Sans action, la carte se retire au bout de 30 s (pas pendant le survol ni pendant une importation)
    Timer { id: fermeture; interval: 30000; running: cle.carte !== null && !survol.containsMouse && !cle.tache; onTriggered: cle.cacher() }

    function importer() {
        tache = "import" + Date.now()
        importation = { etat: "preparation", fait: 0, total: 0 }
        executeur.lancer("setsid " + moteur + "importer " + tache + " " + q(carte.chemin) + " >/dev/null 2>&1 &")
    }
    Timer {
        interval: 500
        running: cle.tache !== "" && (!cle.importation || (cle.importation.etat !== "termine" && cle.importation.etat !== "erreur"))
        repeat: true
        onTriggered: executeur.lancer("cat \"${XDG_RUNTIME_DIR:-/tmp}/samaos-fichiers/" + cle.tache + ".json\" 2>/dev/null; echo", function (s) {
            try { cle.importation = JSON.parse(s.trim()) } catch (e) {}
        })
    }

    component Bouton: MouseArea {
        id: bouton
        property string libelle
        property string trace
        property bool principal: false
        implicitWidth: rangee.implicitWidth + 26
        implicitHeight: 32
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        Rectangle {
            anchors.fill: parent
            radius: 16
            color: bouton.principal ? "#B5532F" : (cle.sombre ? Qt.rgba(1, 1, 1, bouton.containsMouse ? 0.12 : 0.07) : (bouton.containsMouse ? "#FFFFFF" : Qt.rgba(1, 1, 1, 0.7)))
            border.width: bouton.principal ? 0 : 1
            border.color: cle.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.14)
            opacity: bouton.pressed ? 0.85 : 1
        }
        Row {
            id: rangee
            anchors.centerIn: parent
            spacing: 7
            Canvas {
                visible: bouton.trace !== ""
                anchors.verticalCenter: parent.verticalCenter
                width: 14
                height: 14
                onPaint: {
                    var c = getContext("2d"); c.reset(); c.scale(14 / 24, 14 / 24)
                    c.strokeStyle = bouton.principal ? "#FFFFFF" : Kirigami.Theme.textColor; c.lineWidth = 2; c.lineCap = "round"; c.lineJoin = "round"
                    c.path = bouton.trace; c.stroke()
                }
            }
            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: bouton.libelle
                font.pixelSize: 12
                font.weight: Font.Medium
                color: bouton.principal ? "#FFFFFF" : Kirigami.Theme.textColor
            }
        }
    }

    PlasmaCore.Dialog {
        id: fenetreCle
        type: PlasmaCore.Dialog.Notification
        flags: Qt.WindowDoesNotAcceptFocus | Qt.WindowStaysOnTopHint
        location: PlasmaCore.Types.Floating
        backgroundHints: PlasmaCore.Types.StandardBackground
        visible: cle.carte !== null
        x: cle.zoneEcran.x + cle.zoneEcran.width - width - 20
        y: cle.zoneEcran.y + cle.zoneEcran.height - height - 16

        mainItem: MouseArea {
            id: survol
            width: 404
            height: colonne.implicitHeight + 8
            hoverEnabled: true

            ColumnLayout {
                id: colonne
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.margins: 4
                spacing: 12

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 12
                    Rectangle {
                        Layout.alignment: Qt.AlignTop
                        Layout.preferredWidth: 40
                        Layout.preferredHeight: 40
                        radius: 12
                        color: cle.sombre ? "#2B3044" : "#F3ECE3"
                        Canvas {
                            anchors.centerIn: parent
                            width: 20
                            height: 20
                            onPaint: {
                                var c = getContext("2d"); c.reset(); c.scale(20 / 24, 20 / 24)
                                c.strokeStyle = cle.sombre ? "#F1EBE1" : "#1F1C18"; c.lineWidth = 1.7; c.lineCap = "round"; c.lineJoin = "round"
                                c.path = "M9 3h6v6H9z M7 9h10v8a4 4 0 0 1-4 4h-2a4 4 0 0 1-4-4z"; c.stroke()
                            }
                        }
                    }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 2
                        Text { text: "Clé USB détectée"; font.pixelSize: 14; font.weight: Font.DemiBold; color: Kirigami.Theme.textColor }
                        Text {
                            Layout.fillWidth: true
                            elide: Text.ElideRight
                            text: cle.carte ? cle.carte.nom + " " + cle.taille(cle.carte.taille)
                                              + (cle.analyse && cle.analyse.utilise !== undefined ? " · " + cle.taille(cle.analyse.utilise) + " utilisés" : "") : ""
                            font.pixelSize: 12
                            color: cle.texte2
                        }
                    }
                    Text { Layout.alignment: Qt.AlignTop; text: "À l'instant"; font.pixelSize: 11; color: cle.texte3 }
                    MouseArea {
                        Layout.alignment: Qt.AlignTop
                        Layout.preferredWidth: 20
                        Layout.preferredHeight: 20
                        cursorShape: Qt.PointingHandCursor
                        onClicked: cle.cacher()
                        Text { anchors.centerIn: parent; text: "×"; font.pixelSize: 16; color: cle.texte3 }
                    }
                }

                // Contenu de la clé, ou avancement de l'importation
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: cle.tache ? 52 : 32
                    radius: 10
                    color: cle.fondDouce
                    RowLayout {
                        visible: !cle.tache
                        anchors.fill: parent
                        anchors.leftMargin: 12
                        anchors.rightMargin: 12
                        spacing: 8
                        Text { text: "ⓘ"; font.pixelSize: 12; color: cle.texte3 }
                        Text { Layout.fillWidth: true; elide: Text.ElideRight; text: cle.resume(cle.analyse); font.pixelSize: 12; color: cle.texte2 }
                    }
                    ColumnLayout {
                        visible: cle.tache !== ""
                        anchors.fill: parent
                        anchors.margins: 10
                        anchors.leftMargin: 12
                        anchors.rightMargin: 12
                        spacing: 6
                        Text {
                            Layout.fillWidth: true
                            elide: Text.ElideRight
                            readonly property var i: cle.importation
                            text: !i ? "" : i.etat === "termine" ? cle.pluriel(i.photos || 0, "photo") + " importée" + ((i.photos || 0) > 1 ? "s" : "") + " dans Images"
                                : i.etat === "erreur" ? "L'importation n'a pas pu se terminer"
                                : "Importation des photos… " + (i.total ? Math.round(100 * i.fait / i.total) + " %" : "")
                            font.pixelSize: 12
                            font.weight: Font.Medium
                            color: Kirigami.Theme.textColor
                        }
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: 5
                            radius: 2.5
                            color: cle.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.1)
                            Rectangle {
                                width: parent.width * (cle.importation && cle.importation.total ? Math.min(1, cle.importation.fait / cle.importation.total) : 0)
                                height: parent.height
                                radius: 2.5
                                color: cle.importation && cle.importation.etat === "termine" ? "#2F6B57" : "#B5532F"
                                Behavior on width { NumberAnimation { duration: 300 } }
                            }
                        }
                    }
                }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8
                    Bouton {
                        libelle: "Ouvrir"
                        trace: "M3 7h6l2 2h10v10H3z"
                        onClicked: {
                            if (cle.analyse && cle.analyse.montage) executeur.lancer("sama-fichiers " + cle.q(cle.analyse.montage) + " >/dev/null 2>&1 &")
                            cle.cacher()
                        }
                    }
                    Bouton {
                        visible: cle.analyse !== null && cle.analyse.antivirus === true
                        libelle: "Analyser (antivirus)"
                        trace: "M12 3l7 3v5c0 4.5-3 8.5-7 10c-4-1.5-7-5.5-7-10V6z"
                        onClicked: executeur.lancer("konsole -e clamscan -r " + cle.q(cle.analyse.montage) + " >/dev/null 2>&1 &")
                    }
                    Item { Layout.fillWidth: true }
                    Bouton {
                        visible: !cle.tache && cle.analyse !== null && cle.analyse.photos > 0
                        principal: true
                        libelle: "Importer les photos"
                        trace: "M4 5h16v14H4z M4 15l4-4l5 5 M14 13l2-2l4 4"
                        onClicked: cle.importer()
                    }
                    Bouton {
                        visible: cle.importation !== null && cle.importation.etat === "termine"
                        principal: true
                        libelle: "Voir les photos"
                        onClicked: {
                            executeur.lancer("sama-fichiers " + cle.q(cle.importation.destination) + " >/dev/null 2>&1 &")
                            cle.cacher()
                        }
                    }
                }
            }
        }
    }
}
