package africa.samaos.proches

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.Signature

/**
 * Ce que Sugu montre d'une appli : son éditeur, sa description, sa catégorie, si elle marche sans connexion, son
 * icône, ce qu'elle demande. C'est signé par l'éditeur du catalogue, comme la fiche : un point Sama ou un proche
 * ne peut pas changer une description pour faire croire qu'une appli est autre chose.
 */
class Vitrine(
    val paquet: String,
    val version: Long,
    val editeurNom: String,
    val resume: String,
    val description: String,
    val categorie: String,
    val horsLigne: Boolean,
    val sansTraceur: Boolean,
    val icone: String,
    val droits: List<String>,
    val editeur: String,
    val signature: String,
) {
    fun canonique(): ByteArray = listOf(
        "sugu-vitrine-1", paquet, version.toString(), editeurNom, resume, description, categorie,
        if (horsLigne) "1" else "0", if (sansTraceur) "1" else "0", icone, droits.joinToString("|"), editeur,
    ).joinToString("\n").toByteArray(Charsets.UTF_8)

    companion object {
        fun depuis(o: JSONObject): Vitrine? = try {
            val d = o.optJSONArray("droits") ?: JSONArray()
            Vitrine(
                o.getString("paquet"), o.getLong("version"), o.optString("editeurNom"), o.optString("resume"), o.optString("description"),
                o.optString("categorie"), o.optBoolean("horsLigne"), o.optBoolean("sansTraceur"), o.optString("icone").lowercase(),
                (0 until d.length()).map { d.getString(it) }, o.getString("editeur"), o.getString("signature"),
            )
        } catch (_: Exception) {
            null
        }

        fun verifier(c: Context, v: Vitrine): Boolean {
            val cle = Editeurs.cle(c, v.editeur) ?: return false
            return try {
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(cle)
                    update(v.canonique())
                    verify(Base64.decode(v.signature, Base64.DEFAULT))
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}

/** Une appli proposée : sa fiche (ce qui sera vérifié et installé) et, si elle est bien signée, sa vitrine. */
class Offre(val fiche: Manifeste, val vitrine: Vitrine?, val point: Point) {
    val nom get() = fiche.nom
}

/** Les catégories de Sugu (maquette l2-sug-accueil). */
object Categories {
    val TOUTES = listOf(
        "ecole" to "École",
        "commerce" to "Commerce",
        "sante" to "Santé",
        "argent" to "Argent et tontines",
        "agriculture" to "Agriculture",
        "langues" to "Langues",
        "outils" to "Outils",
        "jeux" to "Jeux",
    )

    fun nom(cle: String) = TOUTES.firstOrNull { it.first == cle }?.second ?: "Autres"
}
