package africa.samaos.meteo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Une ville suivie. [position] : « Près de vous », la position du téléphone arrondie à 0,1° (une dizaine de
 * kilomètres) avant de partir chez Open-Meteo.
 */
data class Ville(val nom: String, val region: String, val pays: String, val lat: Double, val lon: Double, val position: Boolean = false) {
    val cle: String get() = if (position) "position" else String.format(Locale.ROOT, "%.3f,%.3f", lat, lon)

    fun json(): JSONObject = JSONObject().put("nom", nom).put("region", region).put("pays", pays).put("lat", lat).put("lon", lon).put("position", position)

    /** En Côte d'Ivoire : là où Sama connaît les saisons. */
    val ivoirienne: Boolean get() = pays == "CI" || (position && lat in 4.3..10.8 && lon in -8.7..-2.4)

    companion object {
        fun depuis(j: JSONObject) = Ville(j.optString("nom"), j.optString("region"), j.optString("pays"), j.optDouble("lat"), j.optDouble("lon"), j.optBoolean("position"))
    }
}

class Actuel(val heure: LocalDateTime, val temp: Double, val ressenti: Double, val humidite: Int, val code: Int, val vent: Double, val uv: Double?, val jour: Boolean)
class Heure(val heure: LocalDateTime, val temp: Double, val pluie: Int?, val code: Int, val jour: Boolean)
class Jour(val date: LocalDate, val code: Int, val max: Double, val min: Double, val pluie: Int?)

/** Les prévisions d'une ville, avec quand elles sont arrivées et ce qu'elles ont coûté en data. */
class Previsions(val actuel: Actuel, val heures: List<Heure>, val jours: List<Jour>, val recues: Long, val octets: Long)

/**
 * La Météo de Sama : les prévisions d'Open-Meteo (données CC BY 4.0), sans compte ni identifiant. Seule la ville
 * part (ou la position arrondie) ; tout est gardé sur le téléphone, pour être lu sans réseau.
 */
object MoteurMeteo {
    private const val PREVISIONS = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODAGE = "https://geocoding-api.open-meteo.com/v1/search"

    /** Au-delà, on demande des prévisions fraîches à l'ouverture. */
    const val FRAICHEUR = 30 * 60_000L

    /** Les grandes villes du pays, proposées d'abord (leurs coordonnées viennent d'Open-Meteo au choix). */
    val PROPOSEES = listOf("Abidjan", "Bouaké", "Yamoussoukro", "San-Pédro", "Korhogo", "Daloa", "Man")

    private fun prefs(c: Context) = c.getSharedPreferences("meteo", Context.MODE_PRIVATE)

    fun villes(c: Context): List<Ville> = try {
        val a = JSONArray(prefs(c).getString("villes", "[]"))
        (0 until a.length()).map { Ville.depuis(a.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun enregistrer(c: Context, l: List<Ville>) = prefs(c).edit().putString("villes", JSONArray(l.map { it.json() }).toString()).apply()

    fun active(c: Context): Int = prefs(c).getInt("active", 0)

    fun choisir(c: Context, i: Int) = prefs(c).edit().putInt("active", i).apply()

    /** Ajoute la ville (ou la remplace si elle y est déjà) et la rend active. */
    fun ajouter(c: Context, v: Ville) {
        val l = villes(c).filterNot { it.cle == v.cle }
        val nouvelle = if (v.position) listOf(v) + l else l + v
        enregistrer(c, nouvelle)
        choisir(c, nouvelle.indexOfFirst { it.cle == v.cle })
    }

    fun retirer(c: Context, v: Ville) {
        val l = villes(c).filterNot { it.cle == v.cle }
        enregistrer(c, l)
        prefs(c).edit().remove("cache_${v.cle}").apply()
        choisir(c, 0)
    }

    /** Les dernières prévisions gardées pour cette ville, même anciennes. */
    fun gardees(c: Context, v: Ville): Previsions? = try {
        val j = JSONObject(prefs(c).getString("cache_${v.cle}", null) ?: return null)
        lire(JSONObject(j.getString("texte")), j.getLong("recues"), j.getLong("octets"))
    } catch (_: Exception) {
        null
    }

    /** Demande des prévisions fraîches à Open-Meteo (6 jours, 24 heures) et les garde. */
    fun actualiser(c: Context, v: Ville): Previsions {
        val url = PREVISIONS + "?latitude=${v.lat}&longitude=${v.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,uv_index,is_day" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=6&forecast_hours=24"
        val (texte, octets) = telecharger(url)
        val maintenant = System.currentTimeMillis()
        val p = lire(JSONObject(texte), maintenant, octets)
        prefs(c).edit().putString("cache_${v.cle}", JSONObject().put("texte", texte).put("recues", maintenant).put("octets", octets).toString()).apply()
        return p
    }

    /** Les villes qui portent ce nom (en Côte d'Ivoire d'abord). */
    fun chercher(nom: String): List<Ville> {
        val q = URLEncoder.encode(nom.trim(), "UTF-8")
        val (texte, _) = telecharger("$GEOCODAGE?name=$q&count=10&language=fr&format=json")
        val r = JSONObject(texte).optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).map { r.getJSONObject(it) }.map {
            Ville(it.optString("name"), it.optString("admin1"), it.optString("country_code"), it.getDouble("latitude"), it.getDouble("longitude"))
        }.sortedBy { if (it.pays == "CI") 0 else 1 }
    }

    /** La position du téléphone, arrondie à 0,1° (une dizaine de kilomètres), ou null. */
    @SuppressLint("MissingPermission")
    suspend fun position(c: Context): Ville? {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = c.getSystemService(LocationManager::class.java) ?: return null
        val recente = lm.getProviders(true).mapNotNull { p ->
            try {
                lm.getLastKnownLocation(p)
            } catch (_: Exception) {
                null
            }
        }.maxByOrNull { it.time }?.takeIf { System.currentTimeMillis() - it.time < 3 * 3600_000L }
        val l: Location = recente ?: withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { suite ->
                val fournisseur = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                if (Build.VERSION.SDK_INT >= 30) {
                    lm.getCurrentLocation(fournisseur, null, c.mainExecutor) { suite.resume(it) }
                } else {
                    suite.resume(null)
                }
            }
        } ?: return null
        fun arrondi(x: Double) = (x * 10).roundToInt() / 10.0
        return Ville("Près de vous", "", "", arrondi(l.latitude), arrondi(l.longitude), position = true)
    }

    /** Le texte de la réponse, et ce que l'échange a coûté en data (compté par Android pour l'appli). */
    private fun telecharger(url: String): Pair<String, Long> {
        val uid = Process.myUid()
        val avant = TrafficStats.getUidRxBytes(uid) + TrafficStats.getUidTxBytes(uid)
        val cx = URL(url).openConnection() as HttpURLConnection
        cx.connectTimeout = 15_000
        cx.readTimeout = 15_000
        cx.setRequestProperty("User-Agent", "SamaMeteo/0.1")
        try {
            if (cx.responseCode != 200) error("Open-Meteo a répondu ${cx.responseCode}")
            val texte = cx.inputStream.use { it.readBytes().decodeToString() }
            val apres = TrafficStats.getUidRxBytes(uid) + TrafficStats.getUidTxBytes(uid)
            val octets = if (avant >= 0 && apres > avant) apres - avant else texte.length.toLong()
            return texte to octets
        } finally {
            cx.disconnect()
        }
    }

    private fun lire(j: JSONObject, recues: Long, octets: Long): Previsions {
        val a = j.getJSONObject("current")
        val actuel = Actuel(
            LocalDateTime.parse(a.getString("time")),
            a.getDouble("temperature_2m"), a.getDouble("apparent_temperature"), a.getInt("relative_humidity_2m"),
            a.getInt("weather_code"), a.getDouble("wind_speed_10m"), a.optDouble("uv_index").takeUnless { it.isNaN() }, a.optInt("is_day", 1) == 1,
        )
        val h = j.getJSONObject("hourly")
        val heures = (0 until h.getJSONArray("time").length()).map { i ->
            Heure(
                LocalDateTime.parse(h.getJSONArray("time").getString(i)),
                h.getJSONArray("temperature_2m").getDouble(i),
                h.getJSONArray("precipitation_probability").optInt(i, -1).takeIf { it >= 0 },
                h.getJSONArray("weather_code").getInt(i),
                h.getJSONArray("is_day").optInt(i, 1) == 1,
            )
        }
        val d = j.getJSONObject("daily")
        val jours = (0 until d.getJSONArray("time").length()).map { i ->
            Jour(
                LocalDate.parse(d.getJSONArray("time").getString(i)),
                d.getJSONArray("weather_code").getInt(i),
                d.getJSONArray("temperature_2m_max").getDouble(i),
                d.getJSONArray("temperature_2m_min").getDouble(i),
                d.getJSONArray("precipitation_probability_max").optInt(i, -1).takeIf { it >= 0 },
            )
        }
        return Previsions(actuel, heures, jours, recues, octets)
    }
}

/** Ce que disent les codes météo (WMO) d'Open-Meteo, en mots de tous les jours. */
object Ciel {
    fun pluvieux(code: Int) = code in 51..67 || code in 80..82 || code >= 95

    fun description(code: Int, jour: Boolean = true): String = when (code) {
        0 -> if (jour) "Ciel dégagé" else "Nuit claire"
        1 -> if (jour) "Plutôt ensoleillé" else "Nuit plutôt claire"
        2 -> "Quelques nuages"
        3 -> "Ciel couvert"
        45, 48 -> "Brouillard"
        51, 53, 55 -> "Bruine"
        56, 57 -> "Bruine verglaçante"
        61 -> "Petite pluie"
        63 -> "Pluie"
        65 -> "Forte pluie"
        66, 67 -> "Pluie verglaçante"
        in 71..77 -> "Neige"
        80 -> "Quelques averses"
        81 -> "Averses"
        82 -> "Fortes averses"
        85, 86 -> "Averses de neige"
        95 -> "Orage"
        96, 99 -> "Orage et grêle"
        else -> "Temps variable"
    }

    /** Le nom court d'une pluie à venir : « Averses », « Orage », « Pluie ». */
    private fun pluie(code: Int) = when {
        code >= 95 -> "Orage"
        code in 80..82 -> "Averses"
        code in 51..57 -> "Bruine"
        else -> "Pluie"
    }

    private fun moment(h: Int) = when (h) {
        in 5..11 -> "dans la matinée"
        in 12..14 -> "en début d'après-midi"
        in 15..18 -> "en fin d'après-midi"
        in 19..22 -> "dans la soirée"
        else -> "cette nuit"
    }

    /** La phrase du jour : la pluie qui arrive (« Averses en fin d'après-midi »), sinon le temps qu'il fait. */
    fun phrase(p: Previsions): String {
        val a = p.actuel
        if (pluvieux(a.code)) return description(a.code, a.jour)
        val prochaine = p.heures.take(12).firstOrNull { it.heure.isAfter(a.heure) && pluvieux(it.code) && (it.pluie ?: 0) >= 50 }
        return prochaine?.let { "${pluie(it.code)} ${moment(it.heure.hour)}" } ?: description(a.code, a.jour)
    }

    fun uv(i: Double): String {
        val n = i.roundToInt()
        return "UV $n, " + when {
            n <= 2 -> "faible"
            n <= 5 -> "modéré"
            n <= 7 -> "élevé"
            n <= 10 -> "très fort"
            else -> "extrême"
        }
    }

    /**
     * Les saisons de Côte d'Ivoire, en général : au sud, deux saisons des pluies ; au nord, une seule. Rien pour
     * le centre ni hors du pays, où Sama ne veut pas se tromper.
     */
    fun saison(v: Ville, date: LocalDate): String? {
        if (!v.ivoirienne) return null
        val m = date.monthValue
        val j = date.dayOfMonth
        return when {
            v.lat < 7.0 -> when {
                m == 12 || m <= 3 -> "Grande saison sèche : peu de pluie jusqu'en mars."
                m in 4..6 || (m == 7 && j <= 15) -> "Grande saison des pluies : pluies fréquentes jusqu'à mi-juillet."
                m in 7..9 -> "Petite saison sèche : ciel souvent couvert, peu de pluie jusqu'en septembre."
                else -> "Petite saison des pluies : averses fréquentes jusqu'à fin novembre."
            }
            v.lat >= 8.5 -> when (m) {
                in 5..10 -> "Saison des pluies : averses fréquentes jusqu'en octobre."
                12, 1, 2 -> "Saison sèche : l'harmattan souffle souvent jusqu'en février."
                else -> "Saison sèche : chaleur, peu de pluie."
            }
            else -> null
        }
    }
}
