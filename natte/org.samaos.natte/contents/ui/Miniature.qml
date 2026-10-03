// Miniature en direct d'une fenêtre (capture Wayland par PipeWire, comme les infobulles de KDE).
// Chargée à part : sans PipeWire, la carte de la fenêtre affiche simplement l'icône de l'application.
import QtQuick
import org.kde.pipewire as PipeWire
import org.kde.taskmanager as TaskManager

PipeWire.PipeWireSourceItem {
    id: source
    property var winId
    readonly property bool prete: source.ready
    nodeId: requete.nodeId
    TaskManager.ScreencastingRequest {
        id: requete
        uuid: source.winId
    }
}
