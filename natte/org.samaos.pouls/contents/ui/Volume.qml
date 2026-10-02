// Curseur de volume de la sortie son principale (PipeWire / PulseAudio via le module de Plasma).
import QtQuick
import QtQuick.Layouts
import QtQuick.Controls as QQC2
import org.kde.kirigami as Kirigami
import org.kde.plasma.private.volume

RowLayout {
    spacing: Kirigami.Units.smallSpacing * 2
    visible: !!PreferredDevice.sink

    Kirigami.Icon {
        Layout.preferredWidth: Kirigami.Units.iconSizes.small
        Layout.preferredHeight: Layout.preferredWidth
        isMask: true
        color: Kirigami.Theme.disabledTextColor
        source: Qt.resolvedUrl("../icons/volume.svg")
    }
    QQC2.Slider {
        Layout.fillWidth: true
        from: 0
        to: PulseAudio.NormalVolume
        value: PreferredDevice.sink ? PreferredDevice.sink.volume : 0
        onMoved: if (PreferredDevice.sink) PreferredDevice.sink.volume = value
    }
    Text {
        text: PreferredDevice.sink ? Math.round(PreferredDevice.sink.volume / PulseAudio.NormalVolume * 100) + " %" : ""
        color: Kirigami.Theme.disabledTextColor
        font.pixelSize: Kirigami.Theme.smallFont.pixelSize
    }
}
