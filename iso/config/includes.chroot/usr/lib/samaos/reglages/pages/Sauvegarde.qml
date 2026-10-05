// Sauvegarde (maquette reg-11) : vos dossiers copiés sur une clé USB ou un disque externe, maintenant ou
// automatiquement quand il est branché ; les versions précédentes des fichiers restent sur le disque.
// Moteur : /usr/libexec/samaos/sauvegarde.py (et le minuteur samaos-sauvegarde.timer). Sama Grenier, le nuage de
// Sama, viendra s'ajouter comme destination.
import QtQuick
import QtQuick.Layouts
import ".."

PageReglage {
    id: page
    titre: "Sauvegarde"
    readonly property string script: "python3 /usr/libexec/samaos/sauvegarde.py "

    property var etat: ({ disque: "", nomDisque: "", branche: false, libre: -1, auto: true, frequence: "jour", derniere: 0,
                          volume: 0, erreur: "", enCours: false, dossiers: [], disques: [] })
    readonly property var frequences: ["heure", "jour", "semaine"]

    // Lecture complète (avec la taille des dossiers) à l'ouverture et après un changement ; rapide ensuite
    // (la page n'est mise à jour que si quelque chose a changé : sinon la liste ouverte se refermerait)
    property string dernierEtat: ""
    function relire(rapide) {
        commande.lancer(script + "etat" + (rapide === true ? " rapide" : ""), function (s) {
            try {
                var e = JSON.parse(s)
                e.dossiers.forEach(function (d, i) {
                    if (d.taille === null) d.taille = page.etat.dossiers[i] ? page.etat.dossiers[i].taille : 0
                })
                var texte = JSON.stringify(e)
                if (texte !== page.dernierEtat) { page.dernierEtat = texte; page.etat = e }
            } catch (err) {}
        })
    }
    function reglage(cle, valeur, ensuite) {
        commande.lancer("kwriteconfig6 --file samaosrc --group Sauvegarde --key " + cle + " " + commande.q(valeur), ensuite || function () { page.relire() })
    }
    function lisible(octets) {
        if (octets >= 1073741824) return (octets / 1073741824).toLocaleString(Qt.locale(), "f", 1).replace(/[,.]0$/, "") + " Go"
        if (octets >= 1048576) return Math.round(octets / 1048576) + " Mo"
        return octets > 0 ? Math.ceil(octets / 1024) + " Ko" : "Vide"
    }
    // « aujourd'hui à 13:20 », « hier à 22:00 », « le 3 octobre »
    function quand(secondes) {
        var d = new Date(secondes * 1000)
        var minuitJour = new Date(d.getTime()); minuitJour.setHours(0, 0, 0, 0)
        var minuit = new Date(); minuit.setHours(0, 0, 0, 0)
        var jours = Math.round((minuit.getTime() - minuitJour.getTime()) / 86400000)
        var heure = d.toLocaleTimeString(Qt.locale(), "HH:mm")
        if (jours === 0) return "aujourd'hui à " + heure
        if (jours === 1) return "hier à " + heure
        return "le " + d.toLocaleDateString(Qt.locale(), "d MMMM")
    }
    function sauvegarder() {
        var e = JSON.parse(JSON.stringify(etat)); e.enCours = true; e.erreur = ""; etat = e; dernierEtat = ""
        commande.lancer(script + "sauvegarder; echo fini", function () { page.relire() })
    }

    Commande { id: commande }
    Component.onCompleted: relire(false)
    // Clés branchées ou retirées, sauvegarde en cours : la page suit
    Timer { interval: page.etat.enCours ? 2000 : 5000; running: true; repeat: true; onTriggered: page.relire(true) }

    // ——— État ———
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: entete.implicitHeight + 32
        radius: 14
        color: Couleurs.carte
        ColumnLayout {
            id: entete
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 16
            anchors.leftMargin: 18
            anchors.rightMargin: 18
            spacing: 0
            RowLayout {
                Layout.fillWidth: true
                spacing: 12
                Rectangle {
                    Layout.preferredWidth: 34
                    Layout.preferredHeight: 34
                    radius: 10
                    color: Couleurs.sombre ? Qt.rgba(61 / 255, 90 / 255, 153 / 255, 0.3) : "#D9E3EA"
                    Canvas {
                        anchors.centerIn: parent
                        width: 18
                        height: 18
                        onPaint: {
                            var c = getContext("2d"); c.reset(); c.scale(18 / 24, 18 / 24)
                            c.strokeStyle = Couleurs.sombre ? "#B4C6EE" : "#2D4A63"; c.lineWidth = 1.8; c.lineCap = "round"; c.lineJoin = "round"
                            c.path = "M7 18a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z M12 11v5 M9.5 13.5L12 11l2.5 2.5"; c.stroke()
                        }
                    }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 2
                    Text {
                        text: page.etat.enCours ? "Sauvegarde en cours…"
                            : page.etat.derniere ? "Dernière sauvegarde : " + page.quand(page.etat.derniere)
                            : "Aucune sauvegarde pour l'instant"
                        font.pixelSize: 14
                        font.weight: Font.Medium
                        color: Couleurs.texte
                    }
                    Text {
                        Layout.fillWidth: true
                        wrapMode: Text.WordWrap
                        text: page.etat.erreur ? page.etat.erreur
                            : !page.etat.disque ? "Choisissez ci-dessous la clé USB ou le disque externe où copier vos documents"
                            : page.etat.volume ? page.lisible(page.etat.volume) + " protégés sur « " + page.etat.nomDisque + " »"
                                                 + (page.etat.auto ? " · chaque " + page.etat.frequence + " quand il est branché" : "")
                            : "Sur « " + page.etat.nomDisque + " »" + (page.etat.branche ? "" : ", à brancher")
                        font.pixelSize: 12
                        color: page.etat.erreur ? Couleurs.lateriteEncre : Couleurs.texte2
                    }
                }
                BoutonSama {
                    principal: true
                    text: "Sauvegarder maintenant"
                    enabled: page.etat.branche && !page.etat.enCours
                    onClicked: page.sauvegarder()
                }
            }
            // Barre qui va et vient pendant la sauvegarde
            Rectangle {
                id: piste
                visible: page.etat.enCours
                Layout.topMargin: 14
                Layout.fillWidth: true
                Layout.preferredHeight: 6
                radius: 3
                clip: true
                color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.12) : Qt.rgba(31 / 255, 28 / 255, 24 / 255, 0.09)
                Rectangle {
                    width: piste.width * 0.3
                    height: piste.height
                    radius: 3
                    color: Couleurs.laterite
                    NumberAnimation on x {
                        running: piste.visible
                        loops: Animation.Infinite
                        from: -piste.width * 0.3
                        to: piste.width
                        duration: 1400
                        easing.type: Easing.InOutQuad
                    }
                }
            }
        }
    }

    // ——— Destinations ———
    Groupe {
        titre: "Destinations"
        Ligne {
            titre: "Sama Grenier"
            detail: "Copie chiffrée de vos documents, en ligne"
            Pastille { text: "Bientôt"; teinte: Couleurs.texte3; encre: Couleurs.texte3 }
        }
        Ligne {
            titre: "Clé USB ou disque externe"
            detail: page.etat.disque
                    ? "« " + page.etat.nomDisque + " » · " + (page.etat.branche ? (page.etat.libre >= 0 ? page.lisible(page.etat.libre) + " libres" : "branché") : "débranché")
                    : page.etat.disques.length > 0 ? "Choisissez le disque où copier vos documents"
                    : "Branchez une clé USB ou un disque externe"
            ListeDeroulante {
                visible: page.etat.disques.length > 0 || page.etat.disque !== ""
                implicitWidth: 200
                readonly property var choix: {
                    var l = page.etat.disques.map(function (d) { return { uuid: d.uuid, texte: d.nom + " (" + page.lisible(d.taille) + ")" } })
                    if (page.etat.disque && !l.some(function (d) { return d.uuid === page.etat.disque }))
                        l.unshift({ uuid: page.etat.disque, texte: page.etat.nomDisque + " (débranché)" })
                    l.push({ uuid: "", texte: "Aucun" })
                    return l
                }
                model: choix.map(function (d) { return d.texte })
                currentIndex: choix.map(function (d) { return d.uuid }).indexOf(page.etat.disque)
                displayText: page.etat.disque ? currentText : "Choisir…"
                onActivated: index => commande.lancer(page.script + "choisir " + commande.q(choix[index].uuid), function () { page.relire(true) })
            }
        }
        Ligne {
            titre: "Sauvegarder quand il est branché"
            detail: "Automatiquement, à la fréquence choisie ci-dessous ; une notification confirme chaque sauvegarde"
            derniere: true
            Interrupteur { actif: page.etat.auto; onBascule: a => page.reglage("auto", a) }
        }
    }

    // ——— Ce qui est sauvegardé ———
    Groupe {
        titre: "Ce qui est sauvegardé"
        Repeater {
            model: page.etat.dossiers
            delegate: Ligne {
                titre: modelData.nom
                detail: page.lisible(modelData.taille)
                derniere: index === page.etat.dossiers.length - 1
                Interrupteur {
                    actif: modelData.choisi
                    onBascule: a => {
                        var noms = page.etat.dossiers.filter(function (d) { return d.nom === modelData.nom ? a : d.choisi })
                                                     .map(function (d) { return d.nom })
                        page.reglage("dossiers", noms.length > 0 ? noms.join(",") : "aucun")
                    }
                }
            }
        }
    }

    // ——— Fréquence ———
    Groupe {
        titre: "Fréquence"
        Ligne {
            titre: "Sauvegarde automatique"
            detail: "Seulement quand le disque est branché"
            derniere: true
            Segments {
                choix: ["Chaque heure", "Chaque jour", "Chaque semaine"]
                indexChoisi: Math.max(0, page.frequences.indexOf(page.etat.frequence))
                onChoisi: index => page.reglage("frequence", page.frequences[index])
            }
        }
    }

    // ——— Restaurer ———
    Groupe {
        titre: "Retrouver un fichier"
        Ligne {
            titre: "Ouvrir la sauvegarde"
            detail: "Dans Fichiers : « Actuelle » contient la dernière copie, « Versions précédentes » les fichiers modifiés ou effacés depuis"
            derniere: true
            BoutonSama {
                text: "Ouvrir"
                enabled: page.etat.branche
                onClicked: commande.lancer(page.script + "ouvrir", function (s) { if (s.trim()) Qt.openUrlExternally("file://" + s.trim()) })
            }
        }
    }
}
