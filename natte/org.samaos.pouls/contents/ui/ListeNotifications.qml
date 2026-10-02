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
            font.weight: Font.DemiBold
            color: Kirigami.Theme.textColor
        }
        Text {
            visible: liste.source && liste.source.nombre > 0
            text: "Tout effacer"
            font.pixelSize: Kirigami.Theme.smallFont.pixelSize
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
        font.pixelSize: Kirigami.Theme.smallFont.pixelSize
        color: Kirigami.Theme.disabledTextColor
    }

    Repeater {
        model: liste.source ? liste.source.historique : null

        delegate: Rectangle {
            visible: index < 4
            Layout.fillWidth: true
            Layout.preferredHeight: visible ? ligne.implicitHeight + Kirigami.Units.largeSpacing * 1.5 : 0
            radius: Kirigami.Units.gridUnit * 0.8
            color: Qt.rgba(Kirigami.Theme.textColor.r, Kirigami.Theme.textColor.g, Kirigami.Theme.textColor.b, 0.06)

            RowLayout {
                id: ligne
                anchors.fill: parent
                anchors.margins: Kirigami.Units.largeSpacing * 0.75
                spacing: Kirigami.Units.largeSpacing

                Kirigami.Icon {
                    Layout.alignment: Qt.AlignTop
                    Layout.preferredWidth: Kirigami.Units.iconSizes.smallMedium
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
                            font.weight: Font.DemiBold
                            color: Kirigami.Theme.textColor
                        }
                        Text {
                            text: Qt.formatTime(model.created, "hh:mm")
                            font.pixelSize: Kirigami.Theme.smallFont.pixelSize
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
                        font.pixelSize: Kirigami.Theme.smallFont.pixelSize
                        color: Kirigami.Theme.disabledTextColor
                    }
                }
            }
        }
    }
}
