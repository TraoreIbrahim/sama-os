// Bluetooth (BluezQt) : disponible s'il y a un adaptateur ; activer / désactiver.
import QtQuick
import org.kde.bluezqt as BluezQt

QtObject {
    readonly property bool disponible: BluezQt.Manager.adapters.length > 0
    readonly property bool actif: disponible && !BluezQt.Manager.bluetoothBlocked
    readonly property string appareil: BluezQt.Manager.connectedDevices.length > 0 ? BluezQt.Manager.connectedDevices[0].name : ""
    function basculer() { BluezQt.Manager.bluetoothBlocked = !BluezQt.Manager.bluetoothBlocked }
}
