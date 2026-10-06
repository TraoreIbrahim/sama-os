package africa.samaos.griot

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri

/** Une page visitée ou gardée en favori. */
data class Page(val url: String, val titre: String, val quand: Long, val visites: Int = 1)

/** L'historique et les favoris de Griot, sur le téléphone seulement. */
class Carnet(c: Context) : SQLiteOpenHelper(c, "carnet.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE historique (url TEXT PRIMARY KEY, titre TEXT, quand INTEGER, visites INTEGER)")
        db.execSQL("CREATE TABLE favoris (url TEXT PRIMARY KEY, titre TEXT, quand INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, avant: Int, apres: Int) {}

    fun visite(url: String, titre: String) {
        if (!url.startsWith("http")) return
        val db = writableDatabase
        val n = db.rawQuery("SELECT visites FROM historique WHERE url = ?", arrayOf(url)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        db.insertWithOnConflict(
            "historique", null,
            ContentValues().apply {
                put("url", url)
                put("titre", titre)
                put("quand", System.currentTimeMillis())
                put("visites", n + 1)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun historique(limite: Int = 300): List<Page> = lire("SELECT url, titre, quand, visites FROM historique ORDER BY quand DESC LIMIT $limite")

    /** Les sites les plus visités, un par domaine, pour la page d'un nouvel onglet. */
    fun frequents(n: Int = 8): List<Page> =
        lire("SELECT url, titre, quand, visites FROM historique ORDER BY visites DESC, quand DESC LIMIT 200")
            .distinctBy { Uri.parse(it.url).host?.removePrefix("www.") }
            .take(n)

    fun chercher(texte: String, n: Int = 6): List<Page> {
        val t = "%${texte.trim()}%"
        return lireArgs("SELECT url, titre, quand, 0 FROM favoris WHERE url LIKE ? OR titre LIKE ? UNION SELECT url, titre, quand, visites FROM historique WHERE url LIKE ? OR titre LIKE ? LIMIT 40", arrayOf(t, t, t, t))
            .distinctBy { it.url }
            .sortedByDescending { it.visites }
            .take(n)
    }

    fun effacerPage(url: String) = writableDatabase.delete("historique", "url = ?", arrayOf(url))

    fun effacerHistorique() = writableDatabase.delete("historique", null, null)

    fun favoris(): List<Page> = lire("SELECT url, titre, quand, 0 FROM favoris ORDER BY quand DESC")

    fun estFavori(url: String) = readableDatabase.rawQuery("SELECT 1 FROM favoris WHERE url = ?", arrayOf(url)).use { it.moveToFirst() }

    fun basculerFavori(url: String, titre: String): Boolean {
        val db = writableDatabase
        return if (estFavori(url)) {
            db.delete("favoris", "url = ?", arrayOf(url))
            false
        } else {
            db.insertWithOnConflict("favoris", null, ContentValues().apply {
                put("url", url)
                put("titre", titre)
                put("quand", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE)
            true
        }
    }

    private fun lire(sql: String) = lireArgs(sql, null)

    private fun lireArgs(sql: String, args: Array<String>?): List<Page> {
        val l = mutableListOf<Page>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) l += Page(c.getString(0), c.getString(1).orEmpty(), c.getLong(2), c.getInt(3))
        }
        return l
    }
}
