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
 * retrouve la sienne. Chaque requête porte ensuite une preuve, liée au nombre de l'autre et à un nombre neuf, et
 * part chiffrée ([Enveloppe]) avec deux clés tirées des mêmes nombres : une pour la requête, une pour la réponse.
 * Sugu ne reçoit que ces clés, qui ne servent qu'une fois, jamais le secret.
 */
object Cercle {
    const val AUTORITE = "africa.samaos.reglages.proches"
    private val URI = Uri.parse("content://$AUTORITE")
    private val hasard = SecureRandom()

    class Reconnu(val id: String, val nom: String)

    /** De quoi faire une requête chez un proche : l'en-tête « Sama-Proche » et les clés de la requête et de la réponse. */
    class Acces(val entete: String, val demande: ByteArray, val reponse: ByteArray)

    /** Une requête reçue d'un proche, sa preuve vérifiée : qui, son nombre neuf, et les clés pour la lire et lui répondre. */
    class Verifie(val id: String, val nombre: String, val demande: ByteArray, val reponse: ByteArray)

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

    /** Les clés d'une requête et de sa réponse (AES-256), tirées du même message que la preuve. */
    fun cles(secret: ByteArray, message: String): Pair<ByteArray, ByteArray> =
        hmac(secret, "sama-proches-2|chiffre|demande|$message") to hmac(secret, "sama-proches-2|chiffre|reponse|$message")

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

    /** L'accès pour une requête vers le téléphone du proche [id], qui a annoncé [nombre]. */
    fun acces(c: Context, id: String, nombre: String, requete: String): Acces? {
        val n = nombre()
        val b = appel(c, "signer", id, Bundle().apply { putString("message", "$nombre|$n|$requete") }) ?: return null
        val p = b.getString("preuve") ?: return null
        return Acces("$n.$p", b.getByteArray("demande") ?: return null, b.getByteArray("reponse") ?: return null)
    }

    /** Le proche qui a envoyé une requête, si sa preuve est bonne ([monNombre] : celui de notre annonce). */
    fun verifier(c: Context, monNombre: String, entete: String, requete: String): Verifie? {
        val n = entete.substringBefore('.', "")
        val p = entete.substringAfter('.', "")
        if (n.length !in 16..32 || p.isEmpty()) return null
        val b = appel(c, "verifier", null, Bundle().apply {
            putString("message", "$monNombre|$n|$requete")
            putString("preuve", p)
        }) ?: return null
        val id = b.getString("id") ?: return null
        return Verifie(id, n, b.getByteArray("demande") ?: return null, b.getByteArray("reponse") ?: return null)
    }

    /** « d'Awa », « de Koffi ». */
    fun de(nom: String) = if (nom.firstOrNull()?.lowercaseChar() in "aeiouyhéèêàâîôû".toList()) "d'$nom" else "de $nom"
}
