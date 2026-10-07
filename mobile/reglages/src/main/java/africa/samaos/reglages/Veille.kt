package africa.samaos.reglages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import africa.samaos.proches.Cercle
import africa.samaos.proches.Veille
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.concurrent.thread

/**
 * « Un proche veille sur vous » (innovation 2, protéger aussi un proche) : la personne choisit elle-même, sur son
 * téléphone, les proches reconnus à prévenir par SMS quand le bouclier arrête une arnaque grave. Elle peut arrêter
 * quand elle veut ; seul le type d'arnaque part, signé avec le secret partagé (voir Veille).
 */
object MoteurVeille {
    class Gardien(val id: String, val nom: String, val numero: String, val depuis: Long)

    /** Trois SMS par jour au plus, et la même alerte une seule fois par heure : prévenir, pas harceler. */
    private const val PAR_JOUR = 3
    private const val ECART = 3600_000L

    private fun prefs(c: Context) = c.getSharedPreferences("veille", Context.MODE_PRIVATE)

    fun gardiens(c: Context): List<Gardien> = try {
        val a = JSONArray(prefs(c).getString("gardiens", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Gardien(it.getString("id"), it.getString("nom"), it.getString("numero"), it.optLong("depuis")) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun garder(c: Context, l: List<Gardien>) = prefs(c).edit().putString(
        "gardiens",
        JSONArray(l.map { JSONObject().put("id", it.id).put("nom", it.nom).put("numero", it.numero).put("depuis", it.depuis) }).toString(),
    ).apply()

    fun ajouter(c: Context, p: ProcheReconnu, numero: String) =
        garder(c, gardiens(c).filter { it.id != Cercle.id(p.cle) } + Gardien(Cercle.id(p.cle), p.nom, numero.filter { it.isDigit() || it == '+' }, System.currentTimeMillis()))

    fun retirer(c: Context, g: Gardien) = garder(c, gardiens(c).filter { it.id != g.id })

    /** Les alertes parties : quand, quoi, à qui (pour le bilan et le plafond). */
    fun envoyees(c: Context): List<Triple<Long, String, String>> = try {
        val a = JSONArray(prefs(c).getString("envoyees", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Triple(it.getLong("quand"), it.getString("code"), it.getString("nom")) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun noterEnvoi(c: Context, code: String, nom: String) {
        val l = envoyees(c).takeLast(40) + Triple(System.currentTimeMillis(), code, nom)
        prefs(c).edit().putString("envoyees", JSONArray(l.map { JSONObject().put("quand", it.first).put("code", it.second).put("nom", it.third) }).toString()).apply()
    }

    /** Le bouclier a noté une arnaque : prévenir, si elle est grave, si la personne l'a voulu, et sous le plafond. */
    fun prevenir(c: Context, type: String, titre: String) {
        val a = Veille.alerte(type, titre) ?: return
        val l = gardiens(c).ifEmpty { return }
        val avant = envoyees(c).filter { it.second != Veille.Alerte.ESSAI.code }
        val jour = LocalDate.now()
        if (avant.count { Instant.ofEpochMilli(it.first).atZone(ZoneId.systemDefault()).toLocalDate() == jour } >= PAR_JOUR * l.size) return
        if (avant.any { it.second == a.code && System.currentTimeMillis() - it.first < ECART }) return
        l.forEach { envoyer(c, it, a) }
    }

    fun essai(c: Context, g: Gardien) = envoyer(c, g, Veille.Alerte.ESSAI)

    /** Envoyer l'alerte, signée avec le secret partagé avec ce proche. Faux si le proche a été oublié entre-temps. */
    private fun envoyer(c: Context, g: Gardien, a: Veille.Alerte): Boolean {
        val p = MoteurReconnus.liste(c).firstOrNull { Cercle.id(it.cle) == g.id } ?: return false
        val quand = System.currentTimeMillis()
        val t = (quand / 1000).toString(36)
        val preuve = Cercle.preuve(MoteurReconnus.secret(p), Veille.message(t, a.code))
        val texte = Veille.sms(MoteurReconnus.monNom(c), a, quand, preuve)
        return try {
            @Suppress("DEPRECATION")
            val sms = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            sms.sendMultipartTextMessage(g.numero, null, sms.divideMessage(texte), null, null)
            noterEnvoi(c, a.code, g.nom)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** L'annonce du bouclier (Bouclier.noter, dans n'importe quelle appli de Sama). Réservée aux applis signées comme le système. */
class AlerteBouclier : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val type = i.getStringExtra("type") ?: return
        val titre = i.getStringExtra("titre").orEmpty()
        val fin = goAsync()
        val app = c.applicationContext
        thread {
            try {
                MoteurVeille.prevenir(app, type, titre)
            } finally {
                fin.finish()
            }
        }
    }
}
