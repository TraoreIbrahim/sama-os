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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
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

/** Le temps qu'il fait maintenant. [direction] : d'où vient le vent, en degrés ; [pression] en hPa ; [visibilite] en mètres. */
class Actuel(
    val heure: LocalDateTime, val temp: Double, val ressenti: Double, val humidite: Int, val code: Int, val vent: Double, val uv: Double?, val jour: Boolean,
    val direction: Int?, val rafales: Double?, val pression: Double?, val rosee: Double?, val visibilite: Double?,
)

/** Une heure de prévision. [pluie] : la chance de pluie en %, [mm] : la quantité attendue. */
class Heure(val heure: LocalDateTime, val temp: Double, val pluie: Int?, val code: Int, val jour: Boolean, val mm: Double, val vent: Double, val uv: Double?, val humidite: Int?)

/** Un jour de prévision, avec son lever et son coucher du soleil. */
class Jour(
    val date: LocalDate, val code: Int, val max: Double, val min: Double, val pluie: Int?, val mm: Double,
    val lever: LocalDateTime?, val coucher: LocalDateTime?, val uvMax: Double?, val ventMax: Double?, val rafalesMax: Double?, val direction: Int?, val ressentiMax: Double?,
)

/** La qualité de l'air (Open-Meteo, CAMS) : l'indice européen (0 à 100 et plus), les particules fines et la poussière du désert, en µg/m³. */
class Air(val indice: Int, val pm25: Double?, val pm10: Double?, val poussiere: Double?)

/** Les prévisions d'une ville, avec quand elles sont arrivées et ce qu'elles ont coûté en data. */
class Previsions(val actuel: Actuel, val heures: List<Heure>, val jours: List<Jour>, val fuseau: String, val air: Air?, val recues: Long, val octets: Long) {
    /** Les heures d'un jour, dans l'ordre. */
    fun heuresDu(d: LocalDate) = heures.filter { it.heure.toLocalDate() == d }
    val aujourdhui: Jour? get() = jours.firstOrNull { it.date == actuel.heure.toLocalDate() } ?: jours.firstOrNull()
    /** L'heure qu'il est dans la ville (son fuseau, pas celui du téléphone). */
    fun heureLocale(): LocalTime = try {
        LocalTime.now(ZoneId.of(fuseau))
    } catch (_: Exception) {
        LocalTime.now()
    }
}

/**
 * La Météo de Sama : les prévisions d'Open-Meteo (données CC BY 4.0), sans compte ni identifiant. Seule la ville
 * part (ou la position arrondie) ; tout est gardé sur le téléphone, pour être lu sans réseau.
 */
object MoteurMeteo {
    private const val PREVISIONS = "https://api.open-meteo.com/v1/forecast"
    private const val AIR = "https://air-quality-api.open-meteo.com/v1/air-quality"
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
        lire(JSONObject(j.getString("texte")), j.optString("air").takeIf { it.isNotBlank() }?.let { JSONObject(it) }, j.getLong("recues"), j.getLong("octets"))
    } catch (_: Exception) {
        null
    }

    /** Demande des prévisions fraîches à Open-Meteo (10 jours, heure par heure) et la qualité de l'air, et les garde. */
    fun actualiser(c: Context, v: Ville): Previsions {
        val url = PREVISIONS + "?latitude=${v.lat}&longitude=${v.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,uv_index,is_day" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,is_day,wind_speed_10m,uv_index,relative_humidity_2m,dew_point_2m,visibility" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,precipitation_probability_max,precipitation_sum,sunrise,sunset,uv_index_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant" +
            "&timezone=auto&forecast_days=10"
        val (texte, octets) = telecharger(url)
        // L'air : une seconde requête, légère ; si elle échoue, les prévisions restent bonnes.
        val (air, octetsAir) = try {
            telecharger("$AIR?latitude=${v.lat}&longitude=${v.lon}&current=european_aqi,pm10,pm2_5,dust&timezone=auto")
        } catch (_: Exception) {
            null to 0L
        }
        val maintenant = System.currentTimeMillis()
        val p = lire(JSONObject(texte), air?.let { JSONObject(it) }, maintenant, octets + octetsAir)
        prefs(c).edit().putString("cache_${v.cle}", JSONObject().put("texte", texte).put("air", air ?: "").put("recues", maintenant).put("octets", octets + octetsAir).toString()).apply()
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
        cx.setRequestProperty("User-Agent", "SamaMeteo/0.2")
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

    private fun JSONArray.reel(i: Int): Double? = optDouble(i).takeUnless { it.isNaN() }
    private fun JSONObject.serie(nom: String): JSONArray? = optJSONArray(nom)

    private fun lire(j: JSONObject, air: JSONObject?, recues: Long, octets: Long): Previsions {
        val a = j.getJSONObject("current")
        val h = j.getJSONObject("hourly")
        val temps = h.getJSONArray("time")
        val heureActuelle = LocalDateTime.parse(a.getString("time"))
        // Le point de rosée et la visibilité ne sont donnés que par heure : on prend l'heure en cours.
        val iActuel = (0 until temps.length()).firstOrNull { LocalDateTime.parse(temps.getString(it)) >= heureActuelle.truncatedTo(ChronoUnit.HOURS) } ?: 0
        val actuel = Actuel(
            heureActuelle,
            a.getDouble("temperature_2m"), a.getDouble("apparent_temperature"), a.getInt("relative_humidity_2m"),
            a.getInt("weather_code"), a.getDouble("wind_speed_10m"), a.optDouble("uv_index").takeUnless { it.isNaN() }, a.optInt("is_day", 1) == 1,
            a.optDouble("wind_direction_10m").takeUnless { it.isNaN() }?.roundToInt(), a.optDouble("wind_gusts_10m").takeUnless { it.isNaN() },
            a.optDouble("surface_pressure").takeUnless { it.isNaN() },
            h.serie("dew_point_2m")?.reel(iActuel), h.serie("visibility")?.reel(iActuel),
        )
        val heures = (0 until temps.length()).map { i ->
            Heure(
                LocalDateTime.parse(temps.getString(i)),
                h.getJSONArray("temperature_2m").getDouble(i),
                h.getJSONArray("precipitation_probability").optInt(i, -1).takeIf { it >= 0 },
                h.getJSONArray("weather_code").getInt(i),
                h.getJSONArray("is_day").optInt(i, 1) == 1,
                h.serie("precipitation")?.reel(i) ?: 0.0,
                h.serie("wind_speed_10m")?.reel(i) ?: 0.0,
                h.serie("uv_index")?.reel(i),
                h.serie("relative_humidity_2m")?.reel(i)?.roundToInt(),
            )
        }
        val d = j.getJSONObject("daily")
        fun date(nom: String, i: Int): LocalDateTime? = d.serie(nom)?.optString(i)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val jours = (0 until d.getJSONArray("time").length()).map { i ->
            Jour(
                LocalDate.parse(d.getJSONArray("time").getString(i)),
                d.getJSONArray("weather_code").getInt(i),
                d.getJSONArray("temperature_2m_max").getDouble(i),
                d.getJSONArray("temperature_2m_min").getDouble(i),
                d.getJSONArray("precipitation_probability_max").optInt(i, -1).takeIf { it >= 0 },
                d.serie("precipitation_sum")?.reel(i) ?: 0.0,
                date("sunrise", i), date("sunset", i),
                d.serie("uv_index_max")?.reel(i), d.serie("wind_speed_10m_max")?.reel(i), d.serie("wind_gusts_10m_max")?.reel(i),
                d.serie("wind_direction_10m_dominant")?.reel(i)?.roundToInt(), d.serie("apparent_temperature_max")?.reel(i),
            )
        }
        val qualite = air?.optJSONObject("current")?.let { q ->
            val indice = q.optDouble("european_aqi").takeUnless { it.isNaN() }?.roundToInt() ?: return@let null
            Air(indice, q.optDouble("pm2_5").takeUnless { it.isNaN() }, q.optDouble("pm10").takeUnless { it.isNaN() }, q.optDouble("dust").takeUnless { it.isNaN() })
        }
        return Previsions(actuel, heures, jours, j.optString("timezone", "UTC"), qualite, recues, octets)
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
    fun pluie(code: Int) = when {
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
        val prochaine = p.heures.filter { it.heure.isAfter(a.heure) }.take(12).firstOrNull { pluvieux(it.code) && (it.pluie ?: 0) >= 50 }
        return prochaine?.let { "${pluie(it.code)} ${moment(it.heure.hour)}" } ?: description(a.code, a.jour)
    }

    /** « Averses probables de 16 h à 18 h, 8 mm » : la pluie des 12 prochaines heures, ou rien. */
    fun pluieAVenir(p: Previsions): String? {
        val h = p.heures.filter { it.heure.isAfter(p.actuel.heure) }.take(12)
        val mouillees = h.filter { (it.pluie ?: 0) >= 40 || it.mm >= 0.5 }
        if (mouillees.isEmpty()) return null
        val debut = mouillees.first()
        val fin = mouillees.last()
        val mm = h.sumOf { it.mm }
        val quoi = pluie(mouillees.maxBy { it.mm }.code).let { if (it == "Orage") "Orage probable" else "$it probables" }
        val quand = if (debut == fin) "vers ${debut.heure.hour} h" else "de ${debut.heure.hour} h à ${fin.heure.hour + 1} h"
        return "$quoi $quand" + if (mm >= 1) ", ${mm.roundToInt()} mm" else ""
    }

    fun uv(i: Double): String {
        val n = i.roundToInt()
        return "UV $n, " + niveauUv(i)
    }

    fun niveauUv(i: Double): String {
        val n = i.roundToInt()
        return when {
            n <= 2 -> "faible"
            n <= 5 -> "modéré"
            n <= 7 -> "élevé"
            n <= 10 -> "très fort"
            else -> "extrême"
        }
    }

    /** D'où vient le vent, en mots : « du nord-est ». */
    fun direction(deg: Int): String {
        val noms = listOf("du nord", "du nord-est", "de l'est", "du sud-est", "du sud", "du sud-ouest", "de l'ouest", "du nord-ouest")
        return noms[((deg + 22) / 45) % 8]
    }

    /** La force du vent, en mots de tous les jours (d'après Beaufort). */
    fun vent(kmh: Double): String = when {
        kmh < 2 -> "Calme"
        kmh < 12 -> "Brise légère"
        kmh < 29 -> "Brise"
        kmh < 50 -> "Vent soutenu"
        kmh < 75 -> "Vent fort"
        else -> "Tempête"
    }

    /** La qualité de l'air, d'après l'indice européen (0-20 bon, 20-40 correct, 40-60 moyen, 60-80 mauvais, 80-100 très mauvais, au-delà extrême). */
    fun air(indice: Int): String = when {
        indice <= 20 -> "Bon"
        indice <= 40 -> "Correct"
        indice <= 60 -> "Moyen"
        indice <= 80 -> "Mauvais"
        indice <= 100 -> "Très mauvais"
        else -> "Extrême"
    }

    /** Ce que l'air demande : rien, ou prudence pour les enfants, les anciens et les asthmatiques. */
    fun conseilAir(a: Air): String = when {
        (a.poussiere ?: 0.0) >= 100 -> "Poussière du Sahara dans l'air : fermez les fenêtres, un masque pour les enfants et les asthmatiques."
        a.indice > 80 -> "Air très pollué : évitez l'effort dehors ; un masque pour les enfants, les anciens et les asthmatiques."
        a.indice > 60 -> "Air pollué : les asthmatiques et les enfants évitent l'effort dehors."
        a.indice > 40 -> "Air moyen : rien à craindre pour la plupart, prudence pour les asthmatiques."
        else -> "Air sain : respirez."
    }

    /** L'humidité expliquée par le point de rosée : « air lourd », « air sec ». */
    fun rosee(t: Double): String = when {
        t >= 24 -> "Air lourd, moite"
        t >= 21 -> "Air humide"
        t >= 16 -> "Air agréable"
        t >= 10 -> "Air sec"
        else -> "Air très sec"
    }

    fun visibilite(m: Double): String = when {
        m >= 20_000 -> "Très bonne"
        m >= 10_000 -> "Bonne"
        m >= 4_000 -> "Moyenne · brume"
        m >= 1_000 -> "Réduite · brume épaisse"
        else -> "Très réduite · brouillard ou poussière"
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

/** Une alerte de Sama : calculée sur le téléphone d'après les prévisions, jamais envoyée par un serveur. */
class Alerte(val titre: String, val texte: String, val forte: Boolean)

object Alertes {
    /** Les alertes du moment : fortes pluies, forte chaleur, vent fort, air poussiéreux ; la plus grave d'abord. */
    fun pour(p: Previsions): List<Alerte> {
        val l = mutableListOf<Alerte>()
        val auj = p.aujourdhui
        val demain = p.jours.getOrNull(p.jours.indexOf(auj) + 1)
        val prochaines = p.heures.filter { it.heure.isAfter(p.actuel.heure) }.take(24)
        // Fortes pluies : plus de 20 mm dans la journée, ou un orage ou de fortes averses presque certains.
        val pluieJour = listOfNotNull(auj, demain).firstOrNull { j -> j.mm >= 20 || ((j.code == 65 || j.code == 82 || j.code >= 95) && (j.pluie ?: 0) >= 70) }
        if (pluieJour != null) {
            val quand = if (pluieJour == auj) "aujourd'hui" else "demain"
            l += Alerte(
                "Fortes pluies $quand",
                "${pluieJour.mm.roundToInt()} mm attendus. Routes et bas-fonds peuvent s'inonder : prudence en moto et en voiture, et gardez les enfants loin des caniveaux.",
                forte = pluieJour.mm >= 40 || pluieJour.code >= 95,
            )
        }
        // Forte chaleur : un ressenti de 40° ou plus dans la journée.
        val chaud = prochaines.maxOfOrNull { it.temp } ?: p.actuel.temp
        val ressentiMax = auj?.ressentiMax ?: p.actuel.ressenti
        if (ressentiMax >= 40 || p.actuel.ressenti >= 40) {
            l += Alerte(
                "Forte chaleur",
                "Ressenti jusqu'à ${ressentiMax.roundToInt()}° (${chaud.roundToInt()}° à l'ombre). Buvez souvent, évitez le soleil de 11 h à 16 h, veillez sur les anciens et les tout-petits.",
                forte = ressentiMax >= 44,
            )
        }
        // Vent fort : des rafales au-delà de 60 km/h.
        val rafales = listOfNotNull(p.actuel.rafales, auj?.rafalesMax).maxOrNull() ?: 0.0
        if (rafales >= 60) {
            l += Alerte("Vent fort", "Rafales jusqu'à ${rafales.roundToInt()} km/h : tôles, branches et panneaux peuvent tomber ; attention sur la lagune et en mer.", forte = rafales >= 90)
        }
        // L'air : poussière du Sahara ou pollution marquée.
        val air = p.air
        if (air != null && (air.indice > 60 || (air.poussiere ?: 0.0) >= 100)) {
            l += Alerte(if ((air.poussiere ?: 0.0) >= 100) "Air poussiéreux" else "Air pollué", Ciel.conseilAir(air), forte = air.indice > 80 || (air.poussiere ?: 0.0) >= 250)
        }
        return l.sortedByDescending { it.forte }
    }
}

/** La lune, calculée sur le téléphone : sa phase, et le nom qu'on lui donne. */
object Lune {
    private const val MOIS = 29.530588853
    private val REFERENCE: Instant = LocalDateTime.of(2000, 1, 6, 18, 14).toInstant(ZoneOffset.UTC)

    /** La part du cycle écoulée depuis la nouvelle lune, de 0 à 1. */
    fun phase(date: LocalDate = LocalDate.now()): Double {
        val jours = ChronoUnit.MINUTES.between(REFERENCE, date.atTime(12, 0).toInstant(ZoneOffset.UTC)) / 1440.0
        return ((jours % MOIS) + MOIS) % MOIS / MOIS
    }

    /** La part éclairée, de 0 (nouvelle) à 1 (pleine). */
    fun eclairee(date: LocalDate = LocalDate.now()): Double = (1 - Math.cos(phase(date) * 2 * Math.PI)) / 2

    fun nom(date: LocalDate = LocalDate.now()): String {
        val f = phase(date)
        return when {
            f < 0.03 || f > 0.97 -> "Nouvelle lune"
            f < 0.22 -> "Premier croissant"
            f < 0.28 -> "Premier quartier"
            f < 0.47 -> "Lune gibbeuse"
            f < 0.53 -> "Pleine lune"
            f < 0.72 -> "Lune décroissante"
            f < 0.78 -> "Dernier quartier"
            else -> "Dernier croissant"
        }
    }

    /** Dans combien de jours la pleine lune, ou la nouvelle. */
    fun prochaine(date: LocalDate = LocalDate.now()): String {
        val f = phase(date)
        val versPleine = ((0.5 - f + 1) % 1) * MOIS
        val versNouvelle = ((1 - f) % 1) * MOIS
        return if (versPleine < versNouvelle) "Pleine lune dans ${versPleine.roundToInt().coerceAtLeast(1)} jour${if (versPleine >= 1.5) "s" else ""}"
        else "Nouvelle lune dans ${versNouvelle.roundToInt().coerceAtLeast(1)} jour${if (versNouvelle >= 1.5) "s" else ""}"
    }
}
