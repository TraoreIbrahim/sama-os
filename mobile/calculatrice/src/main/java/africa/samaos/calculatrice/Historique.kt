package africa.samaos.calculatrice

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** Un calcul fait : l'expression, son résultat (à reprendre tel quel), l'heure. */
class Entree(val expression: String, val resultat: String, val quand: Long)

/** L'historique des calculs, gardé sur le téléphone (les 200 derniers). */
object Historique {
    val entrees = mutableStateListOf<Entree>()
    private var charge = false

    private fun prefs(c: Context) = c.getSharedPreferences("historique", Context.MODE_PRIVATE)

    fun charger(c: Context) {
        if (charge) return
        charge = true
        try {
            val a = JSONArray(prefs(c).getString("liste", "[]"))
            entrees.clear()
            for (i in 0 until a.length()) a.getJSONObject(i).let { entrees += Entree(it.getString("e"), it.getString("r"), it.getLong("t")) }
        } catch (_: Exception) {
        }
    }

    fun ajouter(c: Context, expression: String, resultat: String) {
        // Le même calcul refait aussitôt ne s'écrit pas deux fois.
        if (entrees.firstOrNull()?.let { it.expression == expression && it.resultat == resultat } == true) return
        entrees.add(0, Entree(expression, resultat, System.currentTimeMillis()))
        while (entrees.size > 200) entrees.removeAt(entrees.lastIndex)
        ecrire(c)
    }

    fun vider(c: Context) {
        entrees.clear()
        ecrire(c)
    }

    private fun ecrire(c: Context) {
        val a = JSONArray(entrees.map { JSONObject().put("e", it.expression).put("r", it.resultat).put("t", it.quand) })
        prefs(c).edit().putString("liste", a.toString()).apply()
    }
}
