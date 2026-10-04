// Alt+Tab de Sama OS : une rangée de tuiles d'applications, la fenêtre choisie cerclée de latérite,
// son titre en dessous. Alt+Tab avance, Alt+Maj+Tab recule, les flèches aussi ; un clic choisit.

import QtQuick
import QtQuick.Layouts
import org.kde.plasma.core as PlasmaCore
import org.kde.kwin 3.0 as KWin
import org.kde.kirigami 2.20 as Kirigami

KWin.TabBoxSwitcher {
    id: tabBox

    Instantiator {
        active: tabBox.visible
        delegate: PlasmaCore.Dialog {
            location: PlasmaCore.Types.Floating
            visible: true
            flags: Qt.Popup | Qt.X11BypassWindowManagerHint
            x: tabBox.screenGeometry.x + Math.round((tabBox.screenGeometry.width - principal.width) / 2)
            y: tabBox.screenGeometry.y + Math.round((tabBox.screenGeometry.height - principal.height) / 2)

            mainItem: FocusScope {
                id: principal
                focus: true

                readonly property int tailleCase: 96
                readonly property int maxColonnes: Math.max(1, Math.floor(tabBox.screenGeometry.width * 0.8 / tailleCase))
                width: Math.max(260, Math.min(liste.count, maxColonnes) * tailleCase + 24)
                height: tailleCase + 64

                ListView {
                    id: liste
                    anchors.top: parent.top
                    anchors.topMargin: 12
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: Math.min(count, principal.maxColonnes) * principal.tailleCase
                    height: principal.tailleCase
                    orientation: ListView.Horizontal
                    clip: true
                    model: tabBox.model
                    currentIndex: tabBox.currentIndex
                    highlightMoveDuration: 120
                    highlightFollowsCurrentItem: true
                    keyNavigationWraps: true
                    onCurrentIndexChanged: tabBox.currentIndex = currentIndex

                    // La fenêtre choisie : fond doux et contour latérite
                    highlight: Item {
                        Rectangle {
                            anchors.fill: parent
                            anchors.margins: 6
                            radius: 22
                            color: Qt.rgba(181 / 255, 83 / 255, 47 / 255, 0.12)
                            border.width: 2
                            border.color: "#B5532F"
                        }
                    }

                    delegate: MouseArea {
                        readonly property string titre: model.caption || ""
                        width: principal.tailleCase
                        height: principal.tailleCase
                        hoverEnabled: true
                        onClicked: tabBox.model.activate(index)
                        Accessible.name: model.caption
                        Kirigami.Icon {
                            anchors.centerIn: parent
                            width: 64
                            height: 64
                            source: model.icon
                            opacity: model.minimized ? 0.55 : 1
                        }
                    }
                }

                // Titre de la fenêtre choisie
                Text {
                    anchors.top: liste.bottom
                    anchors.topMargin: 10
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: principal.width - 32
                    horizontalAlignment: Text.AlignHCenter
                    elide: Text.ElideMiddle
                    text: liste.currentItem ? liste.currentItem.titre : ""
                    font.pixelSize: 14
                    font.weight: Font.Medium
                    color: Kirigami.Theme.textColor
                }

                Text {
                    anchors.centerIn: parent
                    visible: liste.count === 0
                    text: "Aucune fenêtre ouverte"
                    font.pixelSize: 14
                    color: Kirigami.Theme.disabledTextColor
                }

                Keys.onPressed: event => {
                    if (event.key === Qt.Key_Left) liste.decrementCurrentIndex()
                    else if (event.key === Qt.Key_Right) liste.incrementCurrentIndex()
                    else return
                    tabBox.currentIndex = liste.currentIndex
                }
            }
        }
    }
}
