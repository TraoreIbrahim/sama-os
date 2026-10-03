// Luminosité de l'écran (portables, écrans compatibles). Masquée quand l'écran ne la gère pas (ex. machine virtuelle).
import QtQuick
import org.kde.plasma.private.brightnesscontrolplugin

Curseur {
    id: luminosite
    nomIcone: "soleil"
    disponible: controle.isBrightnessAvailable && ecran.max > 0

    ScreenBrightnessControl {
        id: controle
        isSilent: false
    }
    // Premier écran de la liste
    Instantiator {
        model: controle.displays
        delegate: QtObject {
            required property int index
            required property string displayName
            required property int brightness
            required property int maxBrightness
            Component.onCompleted: if (index === 0) ecran.lier(this)
        }
    }
    QtObject {
        id: ecran
        property var source: null
        readonly property int max: source ? source.maxBrightness : 0
        function lier(s) { source = s }
    }
    valeur: ecran.max > 0 ? ecran.source.brightness / ecran.max : 0
    onDeplace: v => { if (ecran.source) controle.setBrightness(ecran.source.displayName, Math.max(1, Math.round(v * ecran.max))) }
}
