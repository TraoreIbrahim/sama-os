// Affichage : écran (résolution, mise à l'échelle), luminosité, apparence claire ou sombre, mode nuit, fond d'écran.
import QtQuick
import QtQuick.Layouts
import org.kde.plasma.private.brightnesscontrolplugin
import ".."

PageReglage {
    id: page
    titre: "Affichage"

    property var ecran: null              // sortie principale (kscreen-doctor)
    property var modes: []                // [{id, texte}]
    property bool modeNuit: false
    readonly property var echelles: [1, 1.25, 1.5, 2]

    Commande { id: commande }
    function relire() {
        commande.lancer("kscreen-doctor -j", function (s) {
            try {
                var d = JSON.parse(s)
                var o = d.outputs.filter(function (x) { return x.enabled && x.connected })[0]
                if (!o) return
                // Une entrée par résolution (fréquence la plus haute), de la plus grande à la plus petite
                var parTaille = {}
                o.modes.forEach(function (m) {
                    var cle = m.size.width + "x" + m.size.height
                    if (!parTaille[cle] || m.refreshRate > parTaille[cle].refreshRate) parTaille[cle] = m
                })
                var liste = Object.keys(parTaille).map(function (k) { return parTaille[k] })
                liste.sort(function (a, b) { return b.size.width * b.size.height - a.size.width * a.size.height })
                page.modes = liste.map(function (m) { return { id: m.id, texte: m.size.width + " × " + m.size.height } })
                page.ecran = o
            } catch (e) {}
        })
        commande.lancer("kreadconfig6 --file kwinrc --group NightColor --key Active", function (s) { page.modeNuit = s.trim() === "true" })
    }
    Component.onCompleted: relire()

    readonly property var modeCourant: {
        if (!ecran) return null
        for (var i = 0; i < ecran.modes.length; i++) if (ecran.modes[i].id === ecran.currentModeId) return ecran.modes[i]
        return null
    }

    // Écran
    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 150
        radius: 14
        color: Couleurs.carte
        RowLayout {
            anchors.fill: parent
            anchors.margins: 18
            spacing: 28
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                radius: 12
                color: Couleurs.sombre ? Qt.rgba(1, 1, 1, 0.04) : Qt.rgba(1, 1, 1, 0.55)
                Column {
                    anchors.centerIn: parent
                    spacing: 8
                    Rectangle {
                        width: 168
                        height: 94
                        radius: 8
                        color: Couleurs.sombre ? "#151A2B" : "#F3ECE2"
                        border.width: 3
                        border.color: Couleurs.texte
                        Rectangle { anchors.centerIn: parent; width: 28; height: 28; radius: 14; color: Couleurs.laterite
                            Text { anchors.centerIn: parent; text: "1"; color: "#FFFFFF"; font.pixelSize: 14; font.weight: Font.DemiBold } }
                    }
                    Rectangle { anchors.horizontalCenter: parent.horizontalCenter; width: 60; height: 4; radius: 2; color: Couleurs.bord }
                }
            }
            ColumnLayout {
                Layout.preferredWidth: 240
                spacing: 4
                Text { text: "Écran 1 · principal"; font.pixelSize: 12; color: Couleurs.texte3 }
                Text { text: page.ecran ? page.ecran.name : "…"; font.pixelSize: 16; font.weight: Font.Medium; color: Couleurs.texte }
                Text {
                    text: page.modeCourant ? page.modeCourant.size.width + " × " + page.modeCourant.size.height + " · " + Math.round(page.modeCourant.refreshRate) + " Hz" : ""
                    font.pixelSize: 12
                    color: Couleurs.texte2
                }
            }
        }
    }

    Groupe {
        Ligne {
            titre: "Résolution"
            detail: "La plus grande est la plus nette"
            ListeDeroulante {
                model: page.modes.map(function (m) { return m.texte })
                currentIndex: {
                    if (!page.modeCourant) return -1
                    var t = page.modeCourant.size.width + " × " + page.modeCourant.size.height
                    for (var i = 0; i < page.modes.length; i++) if (page.modes[i].texte === t) return i
                    return -1
                }
                onActivated: index => commande.lancer("kscreen-doctor output." + page.ecran.name + ".mode." + page.modes[index].id, function () { page.relire() })
            }
        }
        Ligne {
            titre: "Mise à l'échelle"
            detail: "Taille du texte et des fenêtres"
            Segments {
                choix: ["100 %", "125 %", "150 %", "200 %"]
                indexChoisi: page.ecran ? Math.max(0, page.echelles.indexOf(page.ecran.scale)) : 0
                onChoisi: index => commande.lancer("kscreen-doctor output." + page.ecran.name + ".scale." + page.echelles[index], function () { page.relire() })
            }
        }
        Ligne {
            id: ligneLuminosite
            titre: "Luminosité"
            detail: Math.round(curseurLuminosite.valeur * 100) + " %"
            visible: luminosite.isBrightnessAvailable
            derniere: true
            Curseur {
                id: curseurLuminosite
                valeur: ecranLum.max > 0 ? ecranLum.source.brightness / ecranLum.max : 0
                onDeplace: v => { if (ecranLum.source) luminosite.setBrightness(ecranLum.source.displayName, Math.max(1, Math.round(v * ecranLum.max))) }
            }
        }
    }
    ScreenBrightnessControl { id: luminosite; isSilent: true }
    Instantiator {
        model: luminosite.displays
        delegate: QtObject {
            required property int index
            required property string displayName
            required property int brightness
            required property int maxBrightness
            Component.onCompleted: if (index === 0) ecranLum.source = this
        }
    }
    QtObject { id: ecranLum; property var source: null; readonly property int max: source ? source.maxBrightness : 0 }

    Groupe {
        titre: "Apparence"
        Ligne {
            titre: "Couleurs des fenêtres et du bureau"
            detail: "Le fond d'écran et les icônes suivent"
            Segments {
                choix: ["Clair", "Sombre"]
                indexChoisi: Couleurs.sombre ? 1 : 0
                onChoisi: index => commande.lancer("/usr/libexec/samaos/apparence.sh " + (index === 1 ? "sombre" : "clair"))
            }
        }
        Ligne {
            titre: "Mode nuit"
            detail: "Couleurs plus chaudes le soir, pour reposer les yeux"
            derniere: true
            Interrupteur {
                actif: page.modeNuit
                onBascule: a => {
                    page.modeNuit = a
                    commande.lancer("kwriteconfig6 --file kwinrc --group NightColor --key Active " + a + " && qdbus6 org.kde.KWin /KWin reconfigure")
                }
            }
        }
    }

    Groupe {
        titre: "Fond d'écran"
        Item {
            Layout.fillWidth: true
            Layout.preferredHeight: 150
            Row {
                anchors.centerIn: parent
                spacing: 18
                Repeater {
                    model: [{ nom: "Sama Aube", dossier: "SamaAube", image: "sama-aube" }, { nom: "Sama Nuit", dossier: "SamaNuit", image: "sama-nuit" }]
                    delegate: MouseArea {
                        width: 180
                        height: 126
                        cursorShape: Qt.PointingHandCursor
                        onClicked: commande.lancer("plasma-apply-wallpaperimage /usr/share/wallpapers/" + modelData.dossier)
                        Column {
                            spacing: 8
                            Image {
                                width: 180
                                height: 100
                                source: "file:///usr/share/samaos/fonds/" + modelData.image + ".svg"
                                sourceSize.width: 360
                                sourceSize.height: 200
                                fillMode: Image.PreserveAspectCrop
                                layer.enabled: true
                            }
                            Text { anchors.horizontalCenter: parent.horizontalCenter; text: modelData.nom; font.pixelSize: 12; color: Couleurs.texte2 }
                        }
                    }
                }
            }
        }
    }
}
