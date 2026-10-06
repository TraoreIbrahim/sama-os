package africa.samaos.proches

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Le cercle des proches reconnus. Les secrets partagés (ECDH, voir [Identite]) restent dans les Réglages, qui ont
 * la clé du téléphone : les autres applis de Sama (Sugu) leur demandent les étiquettes d'annonce et les preuves,
 * par le fournisseur `africa.samaos.reglages.proches`, réservé aux applis signées comme le système.
 *
 * Deux téléphones de proches se reconnaissent sans rien diffuser : celui qui s'ouvre annonce un nombre au hasard
 * et, pour chacun de ses proches, une étiquette (HMAC de ce nombre par leur secret). Seul un proche reconnu y
 * retrouve la sienne. Chaque requête porte ensuite une preuve, liée au nombre de l'autre, à un nombre neuf et à
 * la requête elle-même.
 */
object Cercle {
    const val AUTORITE = "africa.samaos.reglages.proches"
    private val URI = Uri.parse("content://$AUTORITE")
    private val hasard = SecureRandom()

    class Reconnu(val id: String, val nom: String)

    // ——— Les calculs (faits dans les Réglages, qui ont les secrets) ———

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** Un nombre au hasard, neuf à chaque fois (128 bits). */
    fun nombre(): String = b64(ByteArray(16).also { hasard.nextBytes(it) })

    /** L'identifiant d'un proche : tiré de sa clé publique, il ne dit rien de lui. */
    fun id(cle: ByteArray): String = b64(MessageDigest.getInstance("SHA-256").digest(cle).copyOf(9))

    private fun hmac(secret: ByteArray, message: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret, "HmacSHA256"))
            doFinal(message.toByteArray(Charsets.UTF_8))
        }

    fun etiquette(secret: ByteArray, nombre: String) = b64(hmac(secret, "sama-proches-1|annonce|$nombre").copyOf(16))

    /** La preuve d'une requête : « <nombre de l'autre>|<nombre neuf>|<méthode et chemin> ». */
    fun preuve(secret: ByteArray, message: String) = b64(hmac(secret, "sama-proches-1|acces|$message").copyOf(16))

    fun egal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    // ——— Côté applis (Sugu) ———

    private fun appel(c: Context, methode: String, arg: String? = null, extras: Bundle? = null): Bundle? = try {
        c.contentResolver.call(URI, methode, arg, extras)
    } catch (_: Exception) {
        null
    }

    fun reconnus(c: Context): List<Reconnu> {
        val b = appel(c, "reconnus") ?: return emptyList()
        val ids = b.getStringArray("ids") ?: return emptyList()
        val noms = b.getStringArray("noms") ?: return emptyList()
        return ids.indices.map { Reconnu(ids[it], noms.getOrElse(it) { "Un proche" }) }
    }

    /** Les étiquettes à annoncer avec [nombre] (une par proche, dans le désordre). */
    fun etiquettes(c: Context, nombre: String): List<String> = appel(c, "etiquettes", nombre)?.getStringArray("etiquettes")?.toList().orEmpty()

    /** Le proche qui a annoncé [a], s'il est des nôtres. */
    fun trouver(c: Context, a: Protocole.Annonce): String? =
        appel(c, "trouver", a.nombre, Bundle().apply { putStringArray("etiquettes", a.etiquettes.take(200).toTypedArray()) })?.getString("id")

    /** L'en-tête « Sama-Proche » d'une requête vers le téléphone du proche [id], qui a annoncé [nombre]. */
    fun entete(c: Context, id: String, nombre: String, requete: String): String? {
        val n = nombre()
        val p = appel(c, "signer", id, Bundle().apply { putString("message", "$nombre|$n|$requete") })?.getString("preuve") ?: return null
        return "$n.$p"
    }

    /** Le proche qui a envoyé une requête, si sa preuve est bonne ([monNombre] : celui de notre annonce). */
    fun verifier(c: Context, monNombre: String, entete: String, requete: String): Pair<String, String>? {
        val n = entete.substringBefore('.', "")
        val p = entete.substringAfter('.', "")
        if (n.length !in 16..32 || p.isEmpty()) return null
        val id = appel(c, "verifier", null, Bundle().apply {
            putString("message", "$monNombre|$n|$requete")
            putString("preuve", p)
        })?.getString("id") ?: return null
        return id to n
    }

    /** « d'Awa », « de Koffi ». */
    fun de(nom: String) = if (nom.firstOrNull()?.lowercaseChar() in "aeiouyhéèêàâîôû".toList()) "d'$nom" else "de $nom"
}
