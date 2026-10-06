package africa.samaos.mail

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class Securite(val nom: String) { SSL("SSL/TLS"), STARTTLS("STARTTLS"), AUCUNE("Aucune") }

class Serveur(val hote: String, val port: Int, val securite: Securite) {
    fun json(): JSONObject = JSONObject().put("hote", hote).put("port", port).put("securite", securite.name)

    companion object {
        fun depuis(o: JSONObject) = Serveur(o.getString("hote"), o.getInt("port"), Securite.valueOf(o.optString("securite", "SSL")))
    }
}

/** Une boîte mail : l'adresse, le nom montré aux destinataires, les serveurs. Le mot de passe est à part, chiffré. */
class Compte(val id: String, val adresse: String, val nom: String, val identifiant: String, val imap: Serveur, val smtp: Serveur) {
    val domaine get() = adresse.substringAfterLast('@').lowercase()

    /** Gmail range lui-même les mails envoyés : les y ajouter les doublerait. */
    val envoyesParLeServeur get() = imap.hote.endsWith("gmail.com") || imap.hote.endsWith("googlemail.com")

    fun json(): JSONObject = JSONObject().put("id", id).put("adresse", adresse).put("nom", nom).put("identifiant", identifiant)
        .put("imap", imap.json()).put("smtp", smtp.json())

    companion object {
        fun depuis(o: JSONObject) = Compte(
            o.getString("id"), o.getString("adresse"), o.optString("nom"), o.getString("identifiant"),
            Serveur.depuis(o.getJSONObject("imap")), Serveur.depuis(o.getJSONObject("smtp")),
        )
    }
}

object Comptes {
    private fun prefs(c: Context) = c.getSharedPreferences("comptes", Context.MODE_PRIVATE)

    fun liste(c: Context): List<Compte> = try {
        val a = JSONArray(prefs(c).getString("liste", "[]"))
        (0 until a.length()).map { Compte.depuis(a.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    fun de(c: Context, id: String?) = liste(c).firstOrNull { it.id == id }

    fun ajouter(c: Context, compte: Compte, motDePasse: String) {
        val l = liste(c).filter { it.id != compte.id } + compte
        prefs(c).edit()
            .putString("liste", JSONArray(l.map { it.json() }).toString())
            .putString("mdp_${compte.id}", Coffre.chiffrer(motDePasse))
            .apply()
    }

    fun retirer(c: Context, compte: Compte) {
        prefs(c).edit().putString("liste", JSONArray(liste(c).filter { it.id != compte.id }.map { it.json() }).toString()).remove("mdp_${compte.id}").apply()
        Base.effacerCompte(c, compte.id)
    }

    fun motDePasse(c: Context, compte: Compte): String? = prefs(c).getString("mdp_${compte.id}", null)?.let { Coffre.dechiffrer(it) }

    /** Le compte montré (le dernier choisi). */
    fun courant(c: Context): Compte? = de(c, prefs(c).getString("courant", null)) ?: liste(c).firstOrNull()

    fun choisir(c: Context, compte: Compte) = prefs(c).edit().putString("courant", compte.id).apply()

    /**
     * Sans chiffrement, le mot de passe passerait en clair : permis seulement vers une machine du réseau local
     * (un serveur d'essai, celui d'une école), jamais vers Internet.
     */
    fun sansChiffrementPermis(hote: String): Boolean = try {
        val a = InetAddress.getByName(hote)
        a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress
    } catch (_: Exception) {
        false
    }
}

/**
 * Les mots de passe des boîtes, chiffrés par une clé de la puce de sécurité du téléphone (Android Keystore) :
 * ils ne sont jamais écrits en clair, et la clé ne sort pas de la puce.
 */
object Coffre {
    private const val ALIAS = "sama-mail"

    private fun cle(): SecretKey {
        val m = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (m.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return g.generateKey()
    }

    fun chiffrer(texte: String): String {
        val ch = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, cle()) }
        val donnees = ch.doFinal(texte.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ch.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(donnees, Base64.NO_WRAP)
    }

    fun dechiffrer(s: String): String? = try {
        val (iv, donnees) = s.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
        val ch = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, cle(), GCMParameterSpec(128, iv)) }
        String(ch.doFinal(donnees), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
