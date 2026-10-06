package africa.samaos.proches

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Ce qu'un éditeur (Sama, Sugu) dit d'un fichier qu'il publie, et qu'il signe : le paquet, sa version, sa taille,
 * son empreinte et le certificat qui signe l'appli. Le fichier peut ensuite passer par n'importe qui (point Sama,
 * téléphone d'un proche) : s'il ne correspond pas exactement à cette fiche, il est refusé.
 */
class Manifeste(
    val type: String,
    val paquet: String,
    val nom: String,
    val version: Long,
    val versionNom: String,
    val taille: Long,
    val sha256: String,
    val certificat: String,
    val editeur: String,
    val signature: String,
) {
    /** Les octets signés : toujours les mêmes champs, dans le même ordre. */
    fun canonique(): ByteArray =
        listOf("sama-proches-1", type, paquet, nom, version.toString(), versionNom, taille.toString(), sha256, certificat, editeur)
            .joinToString("\n").toByteArray(Charsets.UTF_8)

    fun json(): JSONObject = JSONObject()
        .put("type", type).put("paquet", paquet).put("nom", nom).put("version", version).put("versionNom", versionNom)
        .put("taille", taille).put("sha256", sha256).put("certificat", certificat).put("editeur", editeur).put("signature", signature)

    companion object {
        fun depuis(o: JSONObject): Manifeste? = try {
            Manifeste(
                o.getString("type"), o.getString("paquet"), o.getString("nom"), o.getLong("version"), o.optString("versionNom"),
                o.getLong("taille"), o.getString("sha256").lowercase(), o.getString("certificat").lowercase().replace(":", ""),
                o.getString("editeur"), o.getString("signature"),
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Les éditeurs auxquels le téléphone fait confiance : leurs clés publiques sont dans le système
 * (assets/editeurs/<nom>.pem), personne ne peut en ajouter. « test-emulateur » ne sert qu'à l'émulateur.
 */
object Editeurs {
    private val cles = mutableMapOf<String, PublicKey?>()

    fun cle(c: Context, nom: String): PublicKey? = synchronized(cles) {
        cles.getOrPut(nom) {
            if (!Regex("^[a-z0-9-]{1,40}$").matches(nom)) return@getOrPut null
            try {
                val pem = c.assets.open("editeurs/$nom.pem").bufferedReader().readText()
                val b64 = pem.lines().filter { !it.startsWith("-----") }.joinToString("")
                KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(b64, Base64.DEFAULT)))
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Le nom à dire dans une phrase : « celui que Sugu a publié ». */
    fun nomCourt(nom: String) = if (nom == "sugu") "Sugu" else "Sama"

    fun nomLisible(nom: String) = when (nom) {
        "sama" -> "Sama"
        "sugu" -> "Sugu"
        "test-emulateur" -> "Sama (clé de test)"
        else -> nom
    }
}

/** Les vérifications, dans l'ordre où l'écran de réception les montre. */
object Verification {
    /** La fiche est bien signée par un éditeur connu. */
    fun signature(c: Context, m: Manifeste): Boolean {
        val cle = Editeurs.cle(c, m.editeur) ?: return false
        return try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(cle)
                update(m.canonique())
                verify(Base64.decode(m.signature, Base64.DEFAULT))
            }
        } catch (_: Exception) {
            false
        }
    }

    fun versionInstallee(c: Context, paquet: String): Long? = try {
        c.packageManager.getPackageInfo(paquet, 0).longVersionCode
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Une appli absente, ou plus récente que celle du téléphone : jamais une version plus ancienne. */
    fun plusRecente(c: Context, m: Manifeste): Boolean = versionInstallee(c, m.paquet)?.let { m.version > it } ?: true

    fun empreinte(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { e ->
            val tampon = ByteArray(256 * 1024)
            while (true) {
                val n = e.read(tampon)
                if (n < 0) break
                md.update(tampon, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Le fichier reçu est exactement celui de la fiche : même taille, même empreinte. */
    fun intact(f: File, m: Manifeste): Boolean = f.length() == m.taille && empreinte(f) == m.sha256

    /** L'appli dans le fichier est bien celle annoncée, signée par le certificat annoncé. */
    fun appli(c: Context, f: File, m: Manifeste): Boolean = try {
        val pi = c.packageManager.getPackageArchiveInfo(f.path, PackageManager.GET_SIGNING_CERTIFICATES)
        val signataires = pi?.signingInfo?.apkContentsSigners.orEmpty()
        pi != null && pi.packageName == m.paquet && pi.longVersionCode == m.version &&
            signataires.isNotEmpty() && signataires.all { s -> sha256(s.toByteArray()) == m.certificat }
    } catch (_: Exception) {
        false
    }

    fun sha256(octets: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(octets).joinToString("") { "%02x".format(it) }
}
