// Historique des notifications, en haut du panneau du Pouls (comme dans la maquette).
import QtQuick
import QtQuick.Layouts
import org.kde.kirigami as Kirigami

ColumnLayout {
    id: liste
    spacing: Kirigami.Units.smallSpacing * 2

    readonly property var source: notifs.item

    RowLayout {
        Layout.fillWidth: true
        Text {
            Layout.fillWidth: true
            text: "Notifications"
            font.pixelSize: 13
            font.weight: Font.DemiBold
            color: Kirigami.Theme.textColor
        }
        Text {
            visible: liste.source && liste.source.nombre > 0
            text: "Tout effacer"
            font.pixelSize: 12
            color: Kirigami.Theme.disabledTextColor
            MouseArea {
                anchors.fill: parent
                anchors.margins: -6
                onClicked: liste.source.toutEffacer()
            }
        }
    }

    Text {
        visible: !liste.source || liste.source.nombre === 0
        text: "Aucune notification"
        font.pixelSize: 12
        color: Kirigami.Theme.disabledTextColor
    }

    Repeater {
        model: liste.source ? liste.source.historique : null

        delegate: Rectangle {
            visible: index < 4
            Layout.fillWidth: true
            Layout.preferredHeight: visible ? ligne.implicitHeight + 20 : 0
            radius: 16
            color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.05)

            RowLayout {
                id: ligne
                anchors.fill: parent
                anchors.leftMargin: 12
                anchors.rightMargin: 12
                anchors.topMargin: 10
                anchors.bottomMargin: 10
                spacing: 10

                Kirigami.Icon {
                    Layout.alignment: Qt.AlignTop
                    Layout.preferredWidth: 20
                    Layout.preferredHeight: Layout.preferredWidth
                    source: model.image ? model.image : (model.iconName || model.applicationIconName || "preferences-desktop-notification")
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 1
                    RowLayout {
                        Layout.fillWidth: true
                        Text {
                            Layout.fillWidth: true
                            text: (model.applicationName || "Sama") + (model.summary ? " · " + model.summary : "")
                            elide: Text.ElideRight
                            font.pixelSize: 12
                            font.weight: Font.DemiBold
                            color: Kirigami.Theme.textColor
                        }
                        Text {
                            text: Qt.formatTime(model.created, "hh:mm")
                            font.pixelSize: 11
                            color: Kirigami.Theme.disabledTextColor
                        }
                    }
                    Text {
                        Layout.fillWidth: true
                        text: model.body
                        visible: text.length > 0
                        textFormat: Text.StyledText
                        elide: Text.ElideRight
                        maximumLineCount: 2
                        wrapMode: Text.WordWrap
                        font.pixelSize: 13
                        color: Kirigami.Theme.textColor
                    }
                }
            }
        }
    }
}
