// Code de la disposition de clavier active (FR, EN, DYU…), lu depuis Plasma.
import QtQuick
import org.kde.plasma.workspace.keyboardlayout as KeyboardLayouts

Item {
    readonly property string code: {
        var liste = disposition.layoutsList
        if (liste && disposition.layout >= 0 && disposition.layout < liste.length) {
            // (nom court de Sama s'il y en a un : DYU pour le julakan, WO, SW…)
            var d = liste[disposition.layout]
            return String(d.displayName || d.shortName).toUpperCase()
        }
        return ""
    }

    KeyboardLayouts.KeyboardLayout { id: disposition }
}
