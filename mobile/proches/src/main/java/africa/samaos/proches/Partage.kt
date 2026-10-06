package africa.samaos.proches

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ce que Sugu et les Réglages partagent (réglages globaux du système, comme le bouclier) : les points Sama
 * connus, ceux dont on ne veut plus rien, la fenêtre ouverte aux proches, ce qu'on accepte de leur donner, et le
 * bilan. Rien de secret : les proches eux-mêmes et leurs secrets restent dans les Réglages.
 */
object Partage {
    private const val CLE_POINTS = "sama_points"
    private const val CLE_BLOQUES = "sama_points_bloques"
    private const val CLE_ECONOMISE = "sama_proches_economise"
    private const val CLE_OUVERT = "sama_proches_ouvert"
    private const val CLE_DON = "sama_proches_don"
    private const val CLE_DONNE = "sama_proches_donne"
    private const val CLE_AIDES = "sama_proches_aides"

    private fun lire(c: Context, cle: String) = Settings.Global.getString(c.contentResolver, cle)

    private fun ecrire(c: Context, cle: String, v: String) {
        try {
            Settings.Global.putString(c.contentResolver, cle, v)
        } catch (_: Exception) {
        }
    }

    fun points(c: Context): List<Point> = try {
        val a = JSONArray(lire(c, CLE_POINTS) ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.map { Point(it.optString("nom", "Point Sama"), it.getString("hote"), it.optInt("port", Protocole.PORT)) }
            .sortedBy { it.nom.lowercase() }
    } catch (_: Exception) {
        emptyList()
    }

    /** Ajouter ou renommer un point (le nom qu'il donne lui-même remplace « Point Sama »). */
    fun ajouterPoint(c: Context, p: Point) {
        val l = points(c).filter { it.adresse != p.adresse } + p
        ecrire(c, CLE_POINTS, JSONArray(l.map { JSONObject().put("nom", it.nom).put("hote", it.hote).put("port", it.port) }).toString())
    }

    fun retirerPoint(c: Context, p: Point) {
        val l = points(c).filter { it.adresse != p.adresse }
        ecrire(c, CLE_POINTS, JSONArray(l.map { JSONObject().put("nom", it.nom).put("hote", it.hote).put("port", it.port) }).toString())
    }

    /** « 10.0.2.2:8765 », « 192.168.1.20 » : une adresse de point lisible, ou null. */
    fun lireAdresse(t: String): Point? {
        val m = Regex("^\\s*([A-Za-z0-9.-]+)(?::(\\d{1,5}))?\\s*$").find(t) ?: return null
        return Point("Point Sama", m.groupValues[1], m.groupValues[2].toIntOrNull() ?: Protocole.PORT)
    }

    /** Les adresses des points dont on ne veut plus rien recevoir. */
    fun bloques(c: Context): Set<String> = try {
        val a = JSONArray(lire(c, CLE_BLOQUES) ?: "[]")
        (0 until a.length()).map { a.getString(it) }.toSet()
    } catch (_: Exception) {
        emptySet()
    }

    fun bloque(c: Context, source: String) = source in bloques(c)

    fun reglerBloque(c: Context, source: String, oui: Boolean) {
        val l = bloques(c).toMutableSet()
        if (oui) l += source else l -= source
        ecrire(c, CLE_BLOQUES, JSONArray(l.toList()).toString())
    }

    fun economise(c: Context): Long = lire(c, CLE_ECONOMISE)?.toLongOrNull() ?: 0L

    fun noterEconomise(c: Context, octets: Long) = ecrire(c, CLE_ECONOMISE, (economise(c) + octets).toString())

    // ——— Entre proches : la fenêtre ouverte (Sugu), ce qu'on accepte de donner (Réglages), le bilan ———

    /** La fin de la fenêtre ouverte aux proches (0 : fermée). */
    fun ouvertJusqua(c: Context): Long = lire(c, CLE_OUVERT)?.toLongOrNull() ?: 0L

    fun reglerOuvert(c: Context, jusqua: Long) = ecrire(c, CLE_OUVERT, jusqua.toString())

    private fun don(c: Context): JSONObject = try {
        JSONObject(lire(c, CLE_DON) ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    /** Partager avec ses proches les applis qu'on a déjà (sinon, seulement celles qu'on leur envoie soi-même). */
    fun donner(c: Context) = don(c).optBoolean("donner", true)

    /** Seulement branché, batterie au-dessus de 50 %. */
    fun brancheSeulement(c: Context) = don(c).optBoolean("branche", true)

    /** Limite par jour, en octets (0 : sans limite). */
    fun limite(c: Context) = don(c).optLong("limite", 2_000_000_000L)

    fun reglerDon(c: Context, donner: Boolean = donner(c), branche: Boolean = brancheSeulement(c), limite: Long = limite(c)) =
        ecrire(c, CLE_DON, JSONObject().put("donner", donner).put("branche", branche).put("limite", limite).toString())

    fun donne(c: Context): Long = lire(c, CLE_DONNE)?.toLongOrNull() ?: 0L

    private fun aides(c: Context): Set<String> = try {
        val a = JSONArray(lire(c, CLE_AIDES) ?: "[]")
        (0 until a.length()).map { a.getString(it) }.toSet()
    } catch (_: Exception) {
        emptySet()
    }

    /** Le nombre de proches à qui ce téléphone a donné quelque chose. */
    fun personnes(c: Context) = aides(c).size

    fun noterDonne(c: Context, octets: Long, proche: String) {
        ecrire(c, CLE_DONNE, (donne(c) + octets).toString())
        val a = aides(c)
        if (proche !in a) ecrire(c, CLE_AIDES, JSONArray((a + proche).toList()).toString())
    }
}
