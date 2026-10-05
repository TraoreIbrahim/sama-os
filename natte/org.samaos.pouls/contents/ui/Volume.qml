// Volume de la sortie son principale (PipeWire / PulseAudio via le module de Plasma).
// Un clic sur le haut-parleur coupe ou remet le son.
import QtQuick
import org.kde.plasma.private.volume

Curseur {
    disponible: !!PreferredDevice.sink
    nomIcone: "volume"
    coupe: PreferredDevice.sink ? PreferredDevice.sink.muted : false
    valeur: PreferredDevice.sink ? Math.min(1, PreferredDevice.sink.volume / PulseAudio.NormalVolume) : 0
    onDeplace: v => {
        if (!PreferredDevice.sink) return
        PreferredDevice.sink.volume = Math.round(v * PulseAudio.NormalVolume)
        if (PreferredDevice.sink.muted && v > 0) PreferredDevice.sink.muted = false   // monter le son le remet
    }
    onBasculeIcone: if (PreferredDevice.sink) PreferredDevice.sink.muted = !PreferredDevice.sink.muted
}
