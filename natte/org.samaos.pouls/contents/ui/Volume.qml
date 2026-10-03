// Volume de la sortie son principale (PipeWire / PulseAudio via le module de Plasma).
import QtQuick
import org.kde.plasma.private.volume

Curseur {
    disponible: !!PreferredDevice.sink
    nomIcone: "volume"
    valeur: PreferredDevice.sink ? Math.min(1, PreferredDevice.sink.volume / PulseAudio.NormalVolume) : 0
    onDeplace: v => { if (PreferredDevice.sink) PreferredDevice.sink.volume = Math.round(v * PulseAudio.NormalVolume) }
}
