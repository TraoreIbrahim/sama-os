package africa.samaos.accueil

import africa.samaos.banco.CLE_PLEIN_SOLEIL
import android.content.Context
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Plein soleil, réglé dans les Réglages de Sama : jamais, toujours, ou quand la lumière est très forte
 * (le capteur de lumière, écouté seulement tant que l'Accueil est à l'écran). Vrai : la palette Plein soleil.
 */
@Composable
fun rememberPleinSoleil(): Boolean {
    val c = LocalContext.current
    var mode by remember { mutableIntStateOf(lireMode(c)) }
    var fort by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                mode = lireMode(c)
            }
        }
        c.contentResolver.registerContentObserver(Settings.Secure.getUriFor(CLE_PLEIN_SOLEIL), false, obs)
        onDispose { c.contentResolver.unregisterContentObserver(obs) }
    }
    DisposableEffect(mode) {
        val sm = c.getSystemService(SensorManager::class.java)
        val capteur = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val ecoute = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // Un peu d'hystérésis : on entre au-dessus de 25 000 lux, on sort sous 15 000.
                val lux = e.values.firstOrNull() ?: return
                fort = if (fort) lux > 15_000f else lux > 25_000f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (mode == 1 && capteur != null) sm.registerListener(ecoute, capteur, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm?.unregisterListener(ecoute) }
    }
    return mode == 2 || (mode == 1 && fort)
}

private fun lireMode(c: Context) = Settings.Secure.getInt(c.contentResolver, CLE_PLEIN_SOLEIL, 0)

/** Le geste à trois doigts pour changer d'Espace, qu'on peut couper dans Réglages › Gestes. */
fun troisDoigtsPermis(c: Context) = Settings.Secure.getInt(c.contentResolver, "sama_trois_doigts", 1) == 1
