// Son coupé sur la sortie principale ? (lu par la capsule du Pouls, qui affiche alors un haut-parleur barré)
import QtQuick
import org.kde.plasma.private.volume

QtObject {
    readonly property bool coupe: PreferredDevice.sink ? PreferredDevice.sink.muted : false
}
