package africa.samaos.proches

import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * L'identité de ce téléphone pour Proche en proche : une clé EC P-256 créée une fois dans la puce de sécurité
 * (Android Keystore), qui n'en sort jamais. Sa partie publique va dans le code QR qu'on montre à un proche.
 * Deux téléphones qui se sont scannés l'un l'autre calculent chacun le même secret (ECDH) : personne d'autre ne
 * le peut, même en ayant photographié les deux codes. C'est ce secret qui leur permettra de se reconnaître, sans
 * jamais diffuser de nom ni de numéro.
 */
object Identite {
    private const val ALIAS = "sama-proche"

    private fun magasin() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun creer() {
        val g = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val usages = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or
            (if (Build.VERSION.SDK_INT >= 31) KeyProperties.PURPOSE_AGREE_KEY else 0)
        g.initialize(
            KeyGenParameterSpec.Builder(ALIAS, usages)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        g.generateKeyPair()
    }

    /** La clé publique de ce téléphone (X.509), créée au premier appel. */
    fun publique(): ByteArray {
        val m = magasin()
        if (!m.containsAlias(ALIAS)) creer()
        return magasin().getCertificate(ALIAS).publicKey.encoded
    }

    /** Le secret partagé avec un proche reconnu : SHA-256 de l'accord ECDH entre sa clé publique et la nôtre. */
    fun secretAvec(clePubliqueDuProche: ByteArray): ByteArray {
        publique()
        val prive = magasin().getKey(ALIAS, null) as PrivateKey
        val autre = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(clePubliqueDuProche))
        val a = KeyAgreement.getInstance("ECDH", "AndroidKeyStore")
        a.init(prive)
        a.doPhase(autre, true)
        return MessageDigest.getInstance("SHA-256").digest(a.generateSecret())
    }
}

/**
 * Le code QR d'un téléphone : « samaos:proche?v=1&n=Awa&k=<clé publique> ». Il ne contient ni numéro ni compte :
 * seulement le prénom choisi et la clé publique.
 */
class CodeProche(val nom: String, val cle: ByteArray) {
    fun texte(): String = "samaos:proche?v=1&n=${Uri.encode(nom)}&k=${Base64.encodeToString(cle, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}"

    companion object {
        fun lire(t: String): CodeProche? {
            val s = t.trim()
            if (!s.startsWith("samaos:proche?", ignoreCase = true)) return null
            val params = s.substringAfter('?').split('&').mapNotNull { p ->
                val i = p.indexOf('=')
                if (i <= 0) null else p.substring(0, i) to Uri.decode(p.substring(i + 1))
            }.toMap()
            if (params["v"] != "1") return null
            val cle = try {
                Base64.decode(params["k"] ?: return null, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            } catch (_: Exception) {
                return null
            }
            // Une vraie clé publique EC P-256, sinon rien.
            try {
                KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(cle))
            } catch (_: Exception) {
                return null
            }
            val nom = params["n"].orEmpty().trim().take(40).ifBlank { "Un proche" }
            return CodeProche(nom, cle)
        }
    }
}

/**
 * Le code de vérification d'un appairage : le même sur les deux téléphones (calculé sur les deux clés, dans un
 * ordre fixe). On le compare à voix haute : si quelqu'un a glissé son propre code QR, les chiffres diffèrent.
 */
fun codeVerification(a: ByteArray, b: ByteArray): String {
    val (premier, second) = if (Base64.encodeToString(a, Base64.NO_WRAP) <= Base64.encodeToString(b, Base64.NO_WRAP)) a to b else b to a
    val h = MessageDigest.getInstance("SHA-256").digest(premier + second)
    val n = ((h[0].toLong() and 0xff) shl 24) or ((h[1].toLong() and 0xff) shl 16) or ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)
    return "%08d".format(n % 100_000_000).chunked(4).joinToString(" ")
}
