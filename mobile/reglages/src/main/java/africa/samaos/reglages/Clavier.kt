package africa.samaos.reglages

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Les réglages du clavier de Sama, partagés avec lui (réglages globaux) : langues, propositions, correction,
 * vibration. Le clavier n'écrit que le nombre de mots appris (jamais les mots).
 */
object MoteurClavier {
    const val SAMA = "africa.samaos.clavier/.Clavier"
    private const val CLE = "sama_clavier"

    val LANGUES = listOf(
        Triple("fr", "Français", "AZERTY"),
        Triple("dy", "Julakan", "AZERTY avec ɛ ɔ ɲ ŋ et les tons"),
        Triple("en", "English", "QWERTY"),
    )

    private fun lire(c: Context): JSONObject = try {
        JSONObject(Settings.Global.getString(c.contentResolver, CLE) ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun ecrire(c: Context, o: JSONObject) {
        try {
            Settings.Global.putString(c.contentResolver, CLE, o.toString())
        } catch (_: Exception) {
        }
    }

    fun langues(c: Context): List<String> =
        lire(c).optJSONArray("langues")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().ifEmpty { listOf("fr", "dy") }

    /** Une langue de plus ou de moins ; il en reste toujours une. */
    fun reglerLangue(c: Context, code: String, oui: Boolean) {
        val l = langues(c).toMutableList()
        if (oui && code !in l) l += code
        if (!oui && l.size > 1) l -= code
        ecrire(c, lire(c).put("langues", JSONArray(LANGUES.map { it.first }.filter { it in l })))
    }

    fun option(c: Context, cle: String) = lire(c).optBoolean(cle, true)

    fun reglerOption(c: Context, cle: String, oui: Boolean) = ecrire(c, lire(c).put(cle, oui))

    fun mots(c: Context) = Settings.Global.getInt(c.contentResolver, "sama_clavier_mots", 0)

    /** Le clavier oublie ses mots appris à sa prochaine ouverture. */
    fun effacer(c: Context) {
        ecrire(c, lire(c).put("effacer", System.currentTimeMillis()))
        try {
            Settings.Global.putInt(c.contentResolver, "sama_clavier_mots", 0)
        } catch (_: Exception) {
        }
    }
}
