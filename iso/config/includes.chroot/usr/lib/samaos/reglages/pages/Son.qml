// Son : sortie (haut-parleurs, casque…) et entrée (micro), volumes et coupure du son.
import QtQuick
import QtQuick.Layouts
import org.kde.plasma.private.volume
import ".."

PageReglage {
    id: page
    titre: "Son"

    readonly property var sortie: PreferredDevice.sink
    readonly property var entree: PreferredDevice.source

    Groupe {
        titre: "Sortie"
        Ligne {
            titre: "Volume"
            detail: page.sortie ? (page.sortie.muted ? "Son coupé" : Math.round(page.sortie.volume / PulseAudio.NormalVolume * 100) + " %") : "Aucune sortie son"
            Curseur {
                enabled: !!page.sortie
                valeur: page.sortie ? Math.min(1, page.sortie.volume / PulseAudio.NormalVolume) : 0
                onDeplace: v => { if (page.sortie) { page.sortie.muted = false; page.sortie.volume = Math.round(v * PulseAudio.NormalVolume) } }
            }
            Interrupteur {
                visible: !!page.sortie
                actif: page.sortie ? !page.sortie.muted : false
                onBascule: a => { if (page.sortie) page.sortie.muted = !a }
            }
        }
        Repeater {
            model: SinkModel {}
            delegate: Ligne {
                titre: model.Description || model.Name
                detail: model.Default ? "Utilisée maintenant" : "Cliquer pour utiliser cette sortie"
                cliquable: !model.Default
                onClique: model.PulseObject.default = true
                Rectangle {
                    width: 18; height: 18; radius: 9
                    color: model.Default ? Couleurs.laterite : "transparent"
                    border.width: model.Default ? 0 : 1.5
                    border.color: Couleurs.bord
                    Rectangle { anchors.centerIn: parent; width: 6; height: 6; radius: 3; color: "#FFFFFF"; visible: model.Default }
                }
            }
        }
    }

    Groupe {
        titre: "Entrée"
        Ligne {
            titre: "Micro"
            detail: page.entree ? (page.entree.muted ? "Micro coupé" : Math.round(page.entree.volume / PulseAudio.NormalVolume * 100) + " %") : "Aucun micro détecté"
            derniere: !page.entree
            Curseur {
                visible: !!page.entree
                valeur: page.entree ? Math.min(1, page.entree.volume / PulseAudio.NormalVolume) : 0
                onDeplace: v => { if (page.entree) { page.entree.muted = false; page.entree.volume = Math.round(v * PulseAudio.NormalVolume) } }
            }
            Interrupteur {
                visible: !!page.entree
                actif: page.entree ? !page.entree.muted : false
                onBascule: a => { if (page.entree) page.entree.muted = !a }
            }
        }
        Repeater {
            model: SourceModel {}
            delegate: Ligne {
                titre: model.Description || model.Name
                detail: model.Default ? "Utilisé maintenant" : "Cliquer pour utiliser ce micro"
                cliquable: !model.Default
                onClique: model.PulseObject.default = true
                Rectangle {
                    width: 18; height: 18; radius: 9
                    color: model.Default ? Couleurs.laterite : "transparent"
                    border.width: model.Default ? 0 : 1.5
                    border.color: Couleurs.bord
                    Rectangle { anchors.centerIn: parent; width: 6; height: 6; radius: 3; color: "#FFFFFF"; visible: model.Default }
                }
            }
        }
    }

    Groupe {
        LigneAvancee { titre: "Réglages avancés du son"; detail: "Profils des cartes son, sons du système"; module: "kcm_pulseaudio"; derniere: true }
    }
}
