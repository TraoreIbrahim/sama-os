package africa.samaos.radio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** La bande FM, en kilohertz : de 87,5 à 108,0 MHz, par pas de 0,1 MHz. */
object Bande {
    const val MIN = 87_500
    const val MAX = 108_000
    const val PAS = 100

    fun borne(kHz: Int) = ((kHz.coerceIn(MIN, MAX) + PAS / 2) / PAS) * PAS
}

/** « 95,3 » : une fréquence en mégahertz, à la française. */
fun mhz(kHz: Int): String = String.format(Locale.FRENCH, "%.1f", kHz / 1000.0)

/** Une station gardée : sa fréquence et le nom qu'on lui donne. */
data class Station(val kHz: Int, val nom: String)

/** Où en est le poste : la fréquence, si ça joue, le nom reçu par la radio (RDS), la force du signal, les écouteurs, la minuterie. */
data class EtatPoste(
    val kHz: Int = 95_300,
    val joue: Boolean = false,
    val nom: String? = null,
    val signal: Int = 0,
    val enRecherche: Boolean = false,
    val ecouteurs: Boolean = false,
    val essai: Boolean = false,
    val minuterieFin: Long? = null,
)

/** Ce que sait faire un syntoniseur : se régler, chercher la station d'après, jouer, se taire. */
interface Syntoniseur {
    /** Vrai pour le syntoniseur d'essai de l'émulateur. */
    val essai: Boolean
    fun ouvrir(): Boolean

    /** Se règle sur [kHz] et dit la force du signal, de 0 à 100. */
    fun regler(kHz: Int): Int

    /** Le nom que la station annonce (RDS), s'il y en a un. */
    fun nom(kHz: Int): String?

    /** Depuis [kHz], la prochaine station vers le haut ou vers le bas (en faisant le tour de la bande), ou rien. */
    suspend fun chercher(kHz: Int, montant: Boolean): Int?
    fun jouer(oui: Boolean)
    fun fermer()
}

/**
 * La puce FM du téléphone. Son pilote dépend du fabricant (MediaTek, Qualcomm…) et se branchera à la compilation
 * de Sama OS pour les modèles de référence : ici, on sait seulement si le système en déclare une.
 */
object PuceFm {
    val emulateur: Boolean get() = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.FINGERPRINT.contains("generic")

    fun declaree(c: Context): Boolean = try {
        !emulateur && c.getSystemService("broadcastradio") != null
    } catch (_: Exception) {
        false
    }

    /** Le syntoniseur à employer : celui d'essai tant qu'aucun pilote n'est branché. */
    fun syntoniseur(c: Context): Syntoniseur = SyntoniseurEssai()
}

/**
 * Le syntoniseur d'essai de l'émulateur : six stations imaginaires sur la bande, un son synthétique sur chacune,
 * du souffle entre elles. Il sert à voir et à entendre l'appli sans puce FM.
 */
class SyntoniseurEssai : Syntoniseur {
    override val essai = true

    private class Imaginaire(val kHz: Int, val nom: String, val note: Double)

    private val stations = listOf(
        Imaginaire(88_100, "ESSAI 1", 220.0), Imaginaire(91_700, "ESSAI 2", 261.6), Imaginaire(95_300, "ESSAI 3", 293.7),
        Imaginaire(99_000, "ESSAI 4", 329.6), Imaginaire(102_600, "ESSAI 5", 392.0), Imaginaire(106_400, "ESSAI 6", 440.0),
    )

    @Volatile private var kHz = 95_300
    @Volatile private var enMarche = false
    private var fil: Thread? = null

    override fun ouvrir() = true

    private fun plusProche(k: Int) = stations.minBy { abs(it.kHz - k) }

    /** Le signal décroît en s'éloignant de la station : plein à sa fréquence, rien à 0,4 MHz. */
    private fun signalDe(k: Int): Int {
        val s = plusProche(k)
        val d = abs(s.kHz - k) / 100.0
        return (100 * exp(-d * d / 1.2)).toInt().coerceIn(0, 100)
    }

    override fun regler(kHz: Int): Int {
        this.kHz = Bande.borne(kHz)
        return signalDe(this.kHz)
    }

    override fun nom(kHz: Int): String? = plusProche(kHz).takeIf { abs(it.kHz - kHz) <= 100 }?.nom

    override suspend fun chercher(kHz: Int, montant: Boolean): Int? {
        // Comme une vraie puce : on balaie, un peu de temps par pas.
        delay(350)
        val tri = stations.sortedBy { it.kHz }
        return if (montant) (tri.firstOrNull { it.kHz > kHz } ?: tri.first()).kHz else (tri.lastOrNull { it.kHz < kHz } ?: tri.last()).kHz
    }

    override fun jouer(oui: Boolean) {
        if (oui == enMarche) return
        enMarche = oui
        if (oui) {
            fil = Thread({ synthese() }, "radio-essai").apply { isDaemon = true; start() }
        } else {
            fil?.join(500)
            fil = null
        }
    }

    /** Un son doux (trois notes) sur la station, du souffle à côté : généré au fil de l'eau. */
    private fun synthese() {
        val debit = 22_050
        val taille = AudioTrack.getMinBufferSize(debit, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4410)
        val piste = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(debit).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(taille * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val tampon = ShortArray(1102)
        var t = 0L
        val alea = java.util.Random()
        piste.play()
        try {
            while (enMarche) {
                val k = kHz
                val s = plusProche(k)
                val force = signalDe(k) / 100.0
                for (i in tampon.indices) {
                    val x = (t + i).toDouble() / debit
                    val chant = (sin(2 * PI * s.note * x) + 0.6 * sin(2 * PI * s.note * 1.5 * x) + 0.4 * sin(2 * PI * s.note * 2 * x)) / 2
                    val enveloppe = 0.55 + 0.45 * sin(2 * PI * 0.25 * x)
                    val souffle = (alea.nextDouble() * 2 - 1) * 0.22
                    val v = chant * enveloppe * force * 0.5 + souffle * (1 - force)
                    tampon[i] = (v.coerceIn(-1.0, 1.0) * 12_000).toInt().toShort()
                }
                t += tampon.size
                piste.write(tampon, 0, tampon.size)
            }
        } finally {
            piste.stop()
            piste.release()
        }
    }

    override fun fermer() = jouer(false)
}

/** Les stations gardées et la dernière fréquence, sur le téléphone. */
object Stations {
    private fun prefs(c: Context) = c.getSharedPreferences("radio", Context.MODE_PRIVATE)

    fun liste(c: Context): List<Station> = try {
        val a = JSONArray(prefs(c).getString("stations", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Station(it.getInt("kHz"), it.optString("nom")) }.sortedBy { it.kHz }
    } catch (_: Exception) {
        emptyList()
    }

    fun garder(c: Context, l: List<Station>) {
        val a = JSONArray()
        l.sortedBy { it.kHz }.forEach { a.put(JSONObject().put("kHz", it.kHz).put("nom", it.nom)) }
        prefs(c).edit().putString("stations", a.toString()).apply()
    }

    fun ajouter(c: Context, s: Station) = garder(c, liste(c).filterNot { it.kHz == s.kHz } + s)
    fun retirer(c: Context, kHz: Int) = garder(c, liste(c).filterNot { it.kHz == kHz })
    fun renommer(c: Context, kHz: Int, nom: String) = garder(c, liste(c).map { if (it.kHz == kHz) it.copy(nom = nom) else it })

    fun derniere(c: Context): Int = prefs(c).getInt("derniere", 95_300)
    fun noterDerniere(c: Context, kHz: Int) = prefs(c).edit().putInt("derniere", kHz).apply()
}

/** Les écouteurs filaires : ils servent d'antenne à la radio FM. */
fun ecouteursBranches(c: Context): Boolean = try {
    val am = c.getSystemService(AudioManager::class.java)
    am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
    }
} catch (_: Exception) {
    false
}
