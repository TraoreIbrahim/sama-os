package africa.samaos.telephone

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * « Savoir qui appelle » (maquettes telephone-2 à 4) : un numéro inconnu qui appelle est cherché dans une liste
 * gardée sur le téléphone (numéros signalés comme arnaque, entreprises vérifiées). Jamais sans l'accord de la
 * personne, et rien ne part quand on l'appelle : la liste arrive avec les mises à jour. Les contacts ne sont
 * jamais envoyés à personne.
 */
object Identification {
    enum class Genre { ARNAQUE, ENTREPRISE }

    /** Ce que la liste sait d'un numéro. [parVous] : signalé par la personne elle-même, depuis ce téléphone. */
    class Fiche(
        val numero: String,
        val genre: Genre,
        val signalements: Int = 0,
        val motif: String? = null,
        val nom: String? = null,
        val categorie: String? = null,
        val symbole: String? = null,
        val parVous: Long? = null,
        val essai: Boolean = false,
    )

    private const val LOCAL = "identifier_local"
    private const val ACCORD_VU = "identifier_accord_vu"
    private const val SIGNALES = "identifier_signales"

    fun actif(c: Context) = Reglages.prefs(c).getBoolean(LOCAL, false)

    fun regler(c: Context, oui: Boolean) = Reglages.prefs(c).edit().putBoolean(LOCAL, oui).putBoolean(ACCORD_VU, true).apply()

    /** L'accord a-t-il déjà été demandé (accepté ou « plus tard ») ? */
    fun accordDemande(c: Context) = Reglages.prefs(c).getBoolean(ACCORD_VU, false)

    fun plusTard(c: Context) = Reglages.prefs(c).edit().putBoolean(ACCORD_VU, true).apply()

    /** Les dix derniers chiffres : 07 08 12 34 56, +225 07 08 12 34 56 et 2250708123456 sont le même numéro. */
    fun cle(numero: String): String {
        var d = numero.filter { it.isDigit() }
        if (d.startsWith("225") && d.length >= 12) d = d.drop(3)
        return d.takeLast(10)
    }

    /**
     * La liste d'essai, seulement sur l'émulateur : la vraie liste viendra des signalements, avec les mises à jour.
     * Ces numéros sont fictifs ; on les fait sonner avec « adb emu gsm call 0759314208 ».
     */
    private val ESSAI = listOf(
        Fiche("0759314208", Genre.ARNAQUE, 37, "faux agent mobile money, demande de code secret", essai = true),
        Fiche("0544332211", Genre.ARNAQUE, 12, "faux gain à une tombola, demande de frais d'envoi", essai = true),
        Fiche("2720000015", Genre.ENTREPRISE, nom = "Pharmacie du Plateau", categorie = "Pharmacie", symbole = "+", essai = true),
    )

    private val emulateur: Boolean get() = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.FINGERPRINT.contains("generic")

    /** Ce qu'on sait de ce numéro, si la personne a donné son accord. */
    fun chercher(c: Context, numero: String): Fiche? {
        if (!actif(c)) return null
        val k = cle(numero)
        if (k.length < 8) return null
        signales(c).firstOrNull { it.numero == k }?.let { return it }
        return if (emulateur) ESSAI.firstOrNull { it.numero == k } else null
    }

    /** Signaler un numéro comme arnaque : il est gardé ici. L'envoi anonyme (numéro et type seulement) viendra avec le service Sama. */
    fun signaler(c: Context, numero: String, motif: String = "signalé comme arnaque") {
        val k = cle(numero)
        if (k.isBlank()) return
        val l = signales(c).filter { it.numero != k } + Fiche(k, Genre.ARNAQUE, 1, motif, parVous = System.currentTimeMillis())
        val a = JSONArray()
        l.takeLast(500).forEach { a.put(JSONObject().put("n", it.numero).put("m", it.motif).put("q", it.parVous)) }
        Reglages.prefs(c).edit().putString(SIGNALES, a.toString()).apply()
    }

    fun retirerSignalement(c: Context, numero: String) {
        val k = cle(numero)
        val a = JSONArray()
        signales(c).filter { it.numero != k }.forEach { a.put(JSONObject().put("n", it.numero).put("m", it.motif).put("q", it.parVous)) }
        Reglages.prefs(c).edit().putString(SIGNALES, a.toString()).apply()
    }

    fun signales(c: Context): List<Fiche> = try {
        val a = JSONArray(Reglages.prefs(c).getString(SIGNALES, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Fiche(it.getString("n"), Genre.ARNAQUE, 1, it.optString("m"), parVous = it.optLong("q")) }
    } catch (_: Exception) {
        emptyList()
    }
}
