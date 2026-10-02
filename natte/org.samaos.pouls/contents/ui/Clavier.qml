// Code de la disposition de clavier active (FR, EN, DYU…), lu depuis Plasma.
import QtQuick
import org.kde.plasma.workspace.keyboardlayout as KeyboardLayouts

Item {
    readonly property string code: {
        var liste = disposition.layoutsList
        if (liste && disposition.layout >= 0 && disposition.layout < liste.length) {
            return String(liste[disposition.layout].shortName).toUpperCase()
        }
        return ""
    }

    KeyboardLayouts.KeyboardLayout { id: disposition }
}
