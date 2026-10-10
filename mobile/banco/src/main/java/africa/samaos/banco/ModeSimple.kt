package africa.samaos.banco

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Le mode simplifié (maquettes quo-04-mode-simplifie, dem-06-pret, l5-acces-demarrage) : pour qui découvre le
 * smartphone, un Accueil de trois proches et six grandes tuiles, en Andika, sans geste à deviner. Réglé par Espace
 * (Settings.Secure) : l'Accueil le lit, le premier démarrage et les Réglages l'écrivent.
 */
object ModeSimple {
    const val CLE = "sama_simple"
    /** Les six tuiles dans l'ordre, séparées par des virgules : une action de Sama, ou « appli:<paquet> ». */
    const val CLE_TUILES = "sama_simple_tuiles"
    /** Les Réglages du mode simplifié. */
    const val ACTION_REGLAGES = "africa.samaos.action.MODE_SIMPLE"

    const val PHOTO = "M4 8h3l2-3h6l2 3h3v11H4z M12 10.5a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
    const val ARGENT = "M4 7.5A1.5 1.5 0 0 1 5.5 6H18a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z M4 8l10-4l1.5 2 M15 12.5h5v3h-5a1.5 1.5 0 0 1 0-3z"
    const val METEO = "M9 4v1.5 M3.8 6.3l1 1 M2.5 11.5H4 M7 13a3 3 0 1 1 4.7-2.5 M9 20a3.5 3.5 0 0 1-.5-7A5 5 0 0 1 18 12a3.5 3.5 0 0 1 0 8z"
    const val AIDE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .8-1 1.5v.7 M12 17h.01"

    /** Ce que fait une tuile de Sama. */
    enum class Action(val cle: String, val nom: String, val icone: String) {
        APPELER("appeler", "Appeler", Icones.APPEL),
        MESSAGES("messages", "Messages", Icones.MESSAGE),
        PHOTO("photo", "Photo", ModeSimple.PHOTO),
        ARGENT("argent", "Argent", ModeSimple.ARGENT),
        METEO("meteo", "Météo", ModeSimple.METEO),
        LAMPE("lampe", "Lampe", Icones.LAMPE),
    }

    val DEFAUT = listOf("appeler", "messages", "photo", "argent", "meteo", "lampe")

    /**
     * Les couleurs des six places, celles de la maquette (fond, puis texte) : une tuile prend la couleur de sa
     * place, quoi qu'on y mette, pour que la grille reste équilibrée. null : le sol clair de Banco.
     */
    val COULEURS: List<Pair<Color, Color>?> = listOf(
        Color(0xFF2F6B57) to Color(0xFFFFFFFF),
        Color(0xFF3D5A99) to Color(0xFFFFFFFF),
        Color(0xFF1F1C18) to Color(0xFFF5EEE1),
        Color(0xFFE2A62B) to Color(0xFF1E2740),
        Color(0xFF9A3B3B) to Color(0xFFFFFFFF),
        null,
    )

    fun actif(c: Context) = Settings.Secure.getInt(c.contentResolver, CLE, 0) == 1

    fun regler(c: Context, oui: Boolean) = try {
        Settings.Secure.putInt(c.contentResolver, CLE, if (oui) 1 else 0)
    } catch (_: SecurityException) {
        false
    }

    fun tuiles(c: Context): List<String> {
        val l = Settings.Secure.getString(c.contentResolver, CLE_TUILES)?.split(',')?.map { it.trim() }.orEmpty()
        return if (l.size == DEFAUT.size && l.all { it.isNotEmpty() }) l else DEFAUT
    }

    fun reglerTuile(c: Context, place: Int, valeur: String) = try {
        Settings.Secure.putString(c.contentResolver, CLE_TUILES, tuiles(c).toMutableList().also { it[place] = valeur }.joinToString(","))
    } catch (_: SecurityException) {
        false
    }

    fun action(valeur: String) = Action.entries.firstOrNull { it.cle == valeur }

    /** Le paquet d'une tuile d'appli (« appli:<paquet> »), sinon null. */
    fun paquet(valeur: String) = if (valeur.startsWith("appli:")) valeur.removePrefix("appli:") else null
}

/** Le mode simplifié de cet Espace et ses tuiles, relus dès qu'ils changent (Réglages, premier démarrage). */
class EtatSimple(val actif: Boolean, val tuiles: List<String>)

@Composable
fun rememberModeSimple(): EtatSimple {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                version++
            }
        }
        c.contentResolver.registerContentObserver(Settings.Secure.getUriFor(ModeSimple.CLE), false, obs)
        c.contentResolver.registerContentObserver(Settings.Secure.getUriFor(ModeSimple.CLE_TUILES), false, obs)
        onDispose { c.contentResolver.unregisterContentObserver(obs) }
    }
    return remember(version) { EtatSimple(ModeSimple.actif(c), ModeSimple.tuiles(c)) }
}
