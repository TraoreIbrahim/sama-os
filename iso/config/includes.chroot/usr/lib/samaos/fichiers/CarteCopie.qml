// Copies et déplacements en cours (maquette fic-04) : une carte par opération (avancement, temps restant, pause,
// annulation), et la fenêtre « Conflit de fichiers » quand un fichier du même nom existe déjà.
// Le travail est fait par « fichiers.py copier|deplacer » en arrière-plan ; son avancement est relu toutes les 0,4 s.
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import "../reglages"
import "Types.js" as Types

Column {
    id: copies
    spacing: 10
    width: 380
    property var taches: []          // { id, operation, etat (lu dans le fichier d'avancement) }

    Commande { id: commande }
    readonly property string dossierTaches: "\"${XDG_RUNTIME_DIR:-/tmp}/samaos-fichiers/"

    function lancer(operation, destination, sources) {
        var id = "t" + Date.now()
        commande.lancer("setsid " + fenetre.moteur + operation + " " + id + " " + commande.q(destination) + " "
                        + sources.map(commande.q).join(" ") + " >/dev/null 2>&1 &")
        taches = taches.concat([{ id: id, operation: operation, etat: { etat: "preparation", elements: sources.length,
                                                                          nomDestination: destination.split("/").pop(), total: 0, fait: 0 } }])
    }
    function ecrire(id, suffixe, texte) { commande.lancer("echo " + commande.q(texte) + " > " + dossierTaches + id + "." + suffixe + "\"") }
    function retirer(id) {
        taches = taches.filter(function (t) { return t.id !== id })
        commande.lancer("rm -f " + dossierTaches + id + ".json\"")
    }
    Timer {
        interval: 400
        running: copies.taches.length > 0
        repeat: true
        onTriggered: copies.taches.forEach(function (t) {
            commande.lancer("cat " + copies.dossierTaches + t.id + ".json\" 2>/dev/null; echo", function (s) {
                try {
                    var e = JSON.parse(s.trim())
                    copies.taches = copies.taches.map(function (x) { return x.id === t.id ? { id: x.id, operation: x.operation, etat: e } : x })
                    if (e.etat === "annule") copies.retirer(t.id)
                } catch (err) {}
            })
        })
    }
    // Conflit à résoudre : celui de la première opération qui attend
    readonly property var enConflit: {
        for (var i = 0; i < taches.length; i++) if (taches[i].etat.etat === "conflit" && taches[i].etat.conflit) return taches[i]
        return null
    }

    // « 2 min restantes »
    function restant(e) {
        if (!e.debut || !e.fait || e.fait >= e.total) return ""
        var secondes = (e.total - e.fait) / (e.fait / Math.max(0.5, Date.now() / 1000 - e.debut))
        if (secondes < 60) return "moins d'une minute"
        return Math.round(secondes / 60) + " min restantes"
    }

    component Picto: Canvas {
        property string trace
        property color encre: Couleurs.texte2
        property real taillePicto: 16
        width: taillePicto
        height: taillePicto
        onPaint: {
            var c = getContext("2d"); c.reset(); c.scale(taillePicto / 24, taillePicto / 24)
            c.strokeStyle = encre; c.lineWidth = 1.9; c.lineCap = "round"; c.lineJoin = "round"
            c.path = trace; c.stroke()
        }
    }

    Repeater {
        model: copies.taches
        delegate: Rectangle {
            id: carte
            readonly property var e: modelData.etat
            readonly property bool fini: e.etat === "termine" || e.etat === "erreur"
            width: copies.width
            height: colonne.implicitHeight + 32
            radius: 16
            color: Couleurs.fond
            border.width: 1
            border.color: Couleurs.bord
            // Terminé : la carte s'efface toute seule
            Timer { running: carte.e.etat === "termine"; interval: 2500; onTriggered: copies.retirer(modelData.id) }
            ColumnLayout {
                id: colonne
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.margins: 16
                spacing: 10
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 12
                    Rectangle {
                        Layout.preferredWidth: 36
                        Layout.preferredHeight: 36
                        radius: 10
                        color: Couleurs.carte
                        Picto {
                            anchors.centerIn: parent
                            trace: carte.e.etat === "termine" ? "M5 12.5l4.5 4.5L19 7.5" : "M8 8h11v11H8z M5 16V5h11"
                            encre: carte.e.etat === "termine" ? Couleurs.foret : Couleurs.texte2
                        }
                    }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 2
                        Text {
                            Layout.fillWidth: true
                            elide: Text.ElideRight
                            text: (carte.e.etat === "termine" ? (modelData.operation === "deplacer" ? "Déplacement terminé" : "Copie terminée")
                                   : (modelData.operation === "deplacer" ? "Déplacement de " : "Copie de ") + carte.e.elements
                                     + (carte.e.elements > 1 ? " éléments" : " élément")) + " vers " + carte.e.nomDestination
                            font.pixelSize: 14
                            font.weight: Font.Medium
                            color: Couleurs.texte
                        }
                        Text {
                            Layout.fillWidth: true
                            elide: Text.ElideRight
                            text: carte.e.etat === "erreur" ? (carte.e.message || "Une erreur a interrompu la copie")
                                : carte.e.total > 0 ? Types.taille(carte.e.fait) + " sur " + Types.taille(carte.e.total)
                                                      + (copies.restant(carte.e) ? " — " + copies.restant(carte.e) : "")
                                : "Préparation…"
                            font.pixelSize: 12
                            color: carte.e.etat === "erreur" ? "#A3322A" : Couleurs.texte2
                        }
                    }
                    MouseArea {
                        visible: carte.fini
                        Layout.preferredWidth: 24
                        Layout.preferredHeight: 24
                        cursorShape: Qt.PointingHandCursor
                        onClicked: copies.retirer(modelData.id)
                        Picto { anchors.centerIn: parent; taillePicto: 14; trace: "M6 6l12 12 M18 6L6 18"; encre: Couleurs.texte3 }
                    }
                }
                Rectangle {
                    visible: !carte.fini
                    Layout.fillWidth: true
                    Layout.preferredHeight: 6
                    radius: 3
                    color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.09)
                    Rectangle {
                        width: parent.width * (carte.e.total > 0 ? Math.min(1, carte.e.fait / carte.e.total) : 0)
                        height: parent.height
                        radius: 3
                        color: Couleurs.laterite
                        Behavior on width { NumberAnimation { duration: 350 } }
                    }
                }
                RowLayout {
                    visible: !carte.fini
                    Layout.fillWidth: true
                    spacing: 8
                    Text {
                        Layout.fillWidth: true
                        elide: Text.ElideRight
                        text: carte.e.etat === "conflit" ? "⚠  Conflit à résoudre" : carte.e.etat === "pause" ? "En pause" : ""
                        font.pixelSize: 12
                        color: carte.e.etat === "conflit" ? "#8A5A0B" : Couleurs.texte2
                    }
                    BoutonSama {
                        text: carte.e.etat === "pause" ? "Reprendre" : "Pause"
                        onClicked: copies.ecrire(modelData.id, "ordre", carte.e.etat === "pause" ? "reprendre" : "pause")
                    }
                    BoutonSama { text: "Annuler"; onClicked: copies.ecrire(modelData.id, "ordre", "annuler") }
                }
            }
        }
    }

    component Version: Rectangle {
        id: version
        property string titre
        property var fichier
        property bool recent: false
        Layout.fillWidth: true
        Layout.preferredWidth: 1
        Layout.preferredHeight: 150
        radius: 14
        color: Couleurs.carte
        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 16
            spacing: 10
            RowLayout {
                Layout.fillWidth: true
                Text { Layout.fillWidth: true; text: version.titre; elide: Text.ElideRight; font.pixelSize: 11; font.weight: Font.DemiBold; font.letterSpacing: 0.4; color: Couleurs.texte3 }
                Rectangle {
                    visible: version.recent
                    implicitWidth: recentTexte.implicitWidth + 14
                    implicitHeight: 20
                    radius: 10
                    color: Qt.rgba(47 / 255, 107 / 255, 87 / 255, 0.14)
                    Text { id: recentTexte; anchors.centerIn: parent; text: "Plus récent"; font.pixelSize: 11; font.weight: Font.DemiBold; color: Couleurs.foret }
                }
            }
            RowLayout {
                spacing: 10
                IconeFichier { Layout.preferredWidth: 40; Layout.preferredHeight: 40; nom: version.fichier ? version.fichier.nom : "" }
                Text { Layout.fillWidth: true; text: version.fichier ? version.fichier.nom : ""; elide: Text.ElideMiddle; font.pixelSize: 13; font.weight: Font.Medium; color: Couleurs.texte }
            }
            RowLayout {
                Layout.fillWidth: true
                Text { text: "Modifié"; font.pixelSize: 12; color: Couleurs.texte3 }
                Item { Layout.fillWidth: true }
                Text { text: version.fichier ? Types.date(version.fichier.modifie * 1000) : ""; font.pixelSize: 12; font.weight: Font.Medium; color: Couleurs.texte }
            }
            RowLayout {
                Layout.fillWidth: true
                Text { text: "Taille"; font.pixelSize: 12; color: Couleurs.texte3 }
                Item { Layout.fillWidth: true }
                Text { text: version.fichier ? Types.taille(version.fichier.taille) : ""; font.pixelSize: 12; font.weight: Font.Medium; color: Couleurs.texte }
            }
        }
    }

    // ——— Conflit de fichiers ———
    Rectangle {
        id: voile
        parent: fenetre.contentItem
        anchors.fill: parent
        z: 50
        visible: copies.enConflit !== null
        color: Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.25)
        MouseArea { anchors.fill: parent }
        property bool tous: false
        readonly property var c: copies.enConflit ? copies.enConflit.etat.conflit : null
        function repondre(choix) {
            copies.ecrire(copies.enConflit.id, "choix", choix + (voile.tous ? " tous" : ""))
            voile.tous = false
        }

        Rectangle {
            anchors.centerIn: parent
            width: Math.min(parent.width - 60, 640)
            height: dialogue.implicitHeight + 48
            radius: 18
            color: Couleurs.fond
            border.width: 1
            border.color: Couleurs.bord
            ColumnLayout {
                id: dialogue
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.margins: 24
                spacing: 0
                RowLayout {
                    spacing: 14
                    Rectangle {
                        Layout.preferredWidth: 40
                        Layout.preferredHeight: 40
                        Layout.alignment: Qt.AlignTop
                        radius: 20
                        color: Qt.rgba(201 / 255, 138 / 255, 27 / 255, 0.16)
                        Picto { anchors.centerIn: parent; trace: "M12 4l9 16H3z M12 10v4 M12 17h.01"; encre: "#B7791F" }
                    }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 4
                        Text {
                            Layout.fillWidth: true
                            wrapMode: Text.WordWrap
                            text: voile.c ? "Un fichier nommé " + voile.c.cible.nom + " existe déjà" : ""
                            font.pixelSize: 19
                            font.weight: Font.Medium
                            color: Couleurs.texte
                        }
                        Text {
                            text: voile.c ? "Dans " + voile.c.cible.ou + ". Choisissez la version à garder." : ""
                            font.pixelSize: 13
                            color: Couleurs.texte2
                        }
                    }
                }
                RowLayout {
                    Layout.topMargin: 20
                    Layout.fillWidth: true
                    spacing: 12
                    Version {
                        titre: voile.c ? "À COPIER · " + voile.c.source.ou.toUpperCase() : ""
                        fichier: voile.c ? voile.c.source : null
                        recent: voile.c !== null && voile.c.source.modifie > voile.c.cible.modifie
                    }
                    Text { text: "›"; font.pixelSize: 20; color: Couleurs.texte3 }
                    Version {
                        titre: voile.c ? "DÉJÀ DANS " + voile.c.cible.ou.toUpperCase() : ""
                        fichier: voile.c ? voile.c.cible : null
                        recent: voile.c !== null && voile.c.cible.modifie > voile.c.source.modifie
                    }
                }
                Rectangle {
                    Layout.topMargin: 14
                    Layout.fillWidth: true
                    Layout.preferredHeight: 36
                    radius: 10
                    color: Couleurs.carte
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        anchors.left: parent.left
                        anchors.leftMargin: 14
                        text: voile.c ? "« Garder les deux » enregistrera la copie sous le nom " + voile.c.copie : ""
                        font.pixelSize: 12
                        color: Couleurs.texte2
                    }
                }
                RowLayout {
                    Layout.topMargin: 20
                    Layout.fillWidth: true
                    spacing: 10
                    MouseArea {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 30
                        cursorShape: Qt.PointingHandCursor
                        onClicked: voile.tous = !voile.tous
                        Rectangle {
                            id: caseTous
                            anchors.verticalCenter: parent.verticalCenter
                            width: 18; height: 18; radius: 5
                            color: voile.tous ? Couleurs.laterite : Couleurs.champ
                            border.width: voile.tous ? 0 : 1.5
                            border.color: Couleurs.bord
                            Picto { anchors.centerIn: parent; visible: voile.tous; taillePicto: 12; trace: "M5 12.5l4.5 4.5L19 7.5"; encre: "#FFFFFF" }
                        }
                        Text { anchors.left: caseTous.right; anchors.leftMargin: 10; anchors.verticalCenter: parent.verticalCenter
                               text: "Appliquer aux conflits suivants"; font.pixelSize: 13; color: Couleurs.texte }
                    }
                    BoutonSama { text: "Ignorer"; implicitHeight: 36; onClicked: voile.repondre("ignorer") }
                    BoutonSama { text: "Remplacer"; implicitHeight: 36; onClicked: voile.repondre("remplacer") }
                    BoutonSama { text: "Garder les deux"; implicitHeight: 36; principal: true; onClicked: voile.repondre("garder") }
                }
            }
        }
    }
}
