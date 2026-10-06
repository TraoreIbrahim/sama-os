package africa.samaos.reglages

import android.content.Context
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import africa.samaos.banco.PaysageEspace
import org.json.JSONObject

/**
 * L'Espace où l'on est (un profil Android) : son nom et son paysage, tels que l'Accueil les a enregistrés
 * dans les réglages partagés (`sama_espaces`). Les Réglages prennent ses couleurs.
 */
object EspaceActif {
    fun id(): Int = Process.myUid() / 100_000

    fun paysage(c: Context): PaysageEspace = try {
        val json = JSONObject(Settings.Global.getString(c.contentResolver, "sama_espaces") ?: "{}")
        val e = json.optJSONObject(id().toString())
        PaysageEspace.entries.firstOrNull { it.name == e?.optString("paysage") } ?: PaysageEspace.LAGUNE
    } catch (_: Exception) {
        PaysageEspace.LAGUNE
    }

    fun nom(c: Context): String {
        if (id() == 0) return "Maison"
        return try {
            c.getSystemService(UserManager::class.java).userName
        } catch (_: Exception) {
            "cet Espace"
        }
    }

    /** Les Espaces du téléphone (numéro de profil, nom), Maison d'abord. */
    fun liste(c: Context): List<Pair<Int, String>> = try {
        val um = c.getSystemService(UserManager::class.java)
        val users = um.javaClass.getMethod("getUsers").invoke(um) as List<*>
        users.mapNotNull { u ->
            val id = u!!.javaClass.getField("id").getInt(u)
            val nom = u.javaClass.getField("name").get(u) as String?
            val profil = try {
                u.javaClass.getMethod("isProfile").invoke(u) as Boolean
            } catch (_: Exception) {
                false
            }
            if (profil) null else id to (if (id == 0) "Maison" else nom.orEmpty())
        }.sortedBy { it.first }
    } catch (_: Exception) {
        listOf(id() to nom(c))
    }

    /** Les noms des Espaces du téléphone, Maison d'abord. */
    fun tous(c: Context): List<String> = try {
        val um = c.getSystemService(UserManager::class.java)
        val users = um.javaClass.getMethod("getUsers").invoke(um) as List<*>
        users.mapNotNull { u ->
            val id = u!!.javaClass.getField("id").getInt(u)
            val nom = u.javaClass.getField("name").get(u) as String?
            val profil = try {
                u.javaClass.getMethod("isProfile").invoke(u) as Boolean
            } catch (_: Exception) {
                false
            }
            if (profil) null else id to (if (id == 0) "Maison" else nom.orEmpty())
        }.sortedBy { it.first }.map { it.second }
    } catch (_: Exception) {
        listOf(nom(c))
    }
}
