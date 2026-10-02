// Présence et niveau de la batterie, lus depuis la gestion d'énergie de Plasma.
import QtQuick
import org.kde.plasma.plasma5support as P5Support

Item {
    readonly property bool presente: source.data["Battery"] ? !!source.data["Battery"]["Has Battery"] : false
    readonly property int niveau: source.data["Battery"] ? Number(source.data["Battery"]["Percent"] || 100) : 100

    P5Support.DataSource {
        id: source
        engine: "powermanagement"
        connectedSources: ["Battery"]
    }
}
