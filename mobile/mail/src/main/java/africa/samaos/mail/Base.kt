package africa.samaos.mail

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/** Un mail tel que le téléphone le garde : l'en-tête toujours, le texte une fois chargé. */
class Lettre(
    val compte: String,
    val dossier: String,
    val uid: Long,
    val de: String,
    val deNom: String,
    val a: String,
    val cc: String,
    val sujet: String,
    val date: Long,
    val lu: Boolean,
    val etoile: Boolean,
    val pj: Boolean,
    val extrait: String,
    val taille: Long,
    val texte: String? = null,
    val html: String? = null,
    val charge: Boolean = false,
    val idMessage: String? = null,
    val references: String? = null,
) {
    val nomAffiche get() = deNom.ifBlank { de }
}

/** Une pièce jointe : [chemin] la retrouve dans le mail (« 2 », « 1.3 ») pour la télécharger à la demande. */
class Piece(val n: Int, val nom: String, val type: String, val taille: Long, val chemin: String)

class Dossier(val compte: String, val nom: String, val role: String, val nonLus: Int)

/** Un mail écrit ici : brouillon, en attente d'envoi (pas de réseau), ou en échec. */
class Brouillon(
    val id: Long = 0,
    val compte: String,
    val a: String,
    val cc: String = "",
    val sujet: String,
    val texte: String,
    val pieces: List<String> = emptyList(),
    val enReponseA: String? = null,
    val references: String? = null,
    val etat: String = "brouillon",
    val erreur: String? = null,
    val quand: Long = System.currentTimeMillis(),
)

object Base {
    private class Aide(c: Context) : SQLiteOpenHelper(c, "mail.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE dossiers (compte TEXT, nom TEXT, role TEXT, nonLus INTEGER, PRIMARY KEY (compte, nom))")
            db.execSQL(
                "CREATE TABLE lettres (compte TEXT, dossier TEXT, uid INTEGER, de TEXT, deNom TEXT, a TEXT, cc TEXT, sujet TEXT, date INTEGER, " +
                    "lu INTEGER, etoile INTEGER, pj INTEGER, extrait TEXT, taille INTEGER, texte TEXT, html TEXT, charge INTEGER, idMessage TEXT, refs TEXT, " +
                    "PRIMARY KEY (compte, dossier, uid))",
            )
            db.execSQL("CREATE INDEX lettres_date ON lettres (compte, dossier, date)")
            db.execSQL("CREATE TABLE pieces (compte TEXT, dossier TEXT, uid INTEGER, n INTEGER, nom TEXT, type TEXT, taille INTEGER, chemin TEXT, PRIMARY KEY (compte, dossier, uid, n))")
            db.execSQL(
                "CREATE TABLE brouillons (id INTEGER PRIMARY KEY AUTOINCREMENT, compte TEXT, a TEXT, cc TEXT, sujet TEXT, texte TEXT, pieces TEXT, " +
                    "enReponseA TEXT, refs TEXT, etat TEXT, erreur TEXT, quand INTEGER)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, ancienne: Int, nouvelle: Int) {}
    }

    @Volatile private var aide: Aide? = null

    private fun db(c: Context): SQLiteDatabase = (aide ?: synchronized(this) { aide ?: Aide(c.applicationContext).also { aide = it } }).writableDatabase

    // ——— Dossiers ———

    fun dossiers(c: Context, compte: String): List<Dossier> =
        db(c).rawQuery("SELECT compte, nom, role, nonLus FROM dossiers WHERE compte = ?", arrayOf(compte)).use { k ->
            buildList { while (k.moveToNext()) add(Dossier(k.getString(0), k.getString(1), k.getString(2), k.getInt(3))) }
        }.sortedWith(compareBy({ ORDRE.indexOf(it.role).let { i -> if (i < 0) ORDRE.size else i } }, { it.nom.lowercase() }))

    private val ORDRE = listOf("reception", "envoyes", "brouillons", "archives", "indesirables", "corbeille", "")

    fun dossier(c: Context, compte: String, role: String) = dossiers(c, compte).firstOrNull { it.role == role }

    fun garderDossiers(c: Context, compte: String, l: List<Dossier>) {
        val d = db(c)
        d.beginTransaction()
        try {
            d.delete("dossiers", "compte = ?", arrayOf(compte))
            l.forEach { x ->
                d.insert("dossiers", null, ContentValues().apply {
                    put("compte", compte); put("nom", x.nom); put("role", x.role); put("nonLus", x.nonLus)
                })
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    fun reglerNonLus(c: Context, compte: String, dossier: String, n: Int) {
        db(c).update("dossiers", ContentValues().apply { put("nonLus", n) }, "compte = ? AND nom = ?", arrayOf(compte, dossier))
    }

    // ——— Mails ———

    private const val COLONNES = "compte, dossier, uid, de, deNom, a, cc, sujet, date, lu, etoile, pj, extrait, taille, texte, html, charge, idMessage, refs"

    private fun lettre(k: Cursor) = Lettre(
        k.getString(0), k.getString(1), k.getLong(2), k.getString(3).orEmpty(), k.getString(4).orEmpty(), k.getString(5).orEmpty(), k.getString(6).orEmpty(),
        k.getString(7).orEmpty(), k.getLong(8), k.getInt(9) == 1, k.getInt(10) == 1, k.getInt(11) == 1, k.getString(12).orEmpty(), k.getLong(13),
        k.getString(14), k.getString(15), k.getInt(16) == 1, k.getString(17), k.getString(18),
    )

    /** Les mails d'un dossier, du plus récent au plus ancien. [filtre] : « nonlus », « pj » ; [recherche] : dans l'expéditeur, l'objet et le texte. */
    fun lettres(c: Context, compte: String, dossier: String, filtre: String = "", recherche: String = "", limite: Int = 300): List<Lettre> {
        val conditions = mutableListOf("compte = ?", "dossier = ?")
        val args = mutableListOf(compte, dossier)
        if (filtre == "nonlus") conditions += "lu = 0"
        if (filtre == "pj") conditions += "pj = 1"
        if (recherche.isNotBlank()) {
            conditions += "(de LIKE ? OR deNom LIKE ? OR sujet LIKE ? OR extrait LIKE ? OR texte LIKE ?)"
            repeat(5) { args += "%${recherche.trim()}%" }
        }
        return db(c).rawQuery("SELECT $COLONNES FROM lettres WHERE ${conditions.joinToString(" AND ")} ORDER BY date DESC LIMIT $limite", args.toTypedArray()).use { k ->
            buildList { while (k.moveToNext()) add(lettre(k)) }
        }
    }

    fun lettre(c: Context, compte: String, dossier: String, uid: Long): Lettre? =
        db(c).rawQuery("SELECT $COLONNES FROM lettres WHERE compte = ? AND dossier = ? AND uid = ?", arrayOf(compte, dossier, uid.toString())).use { k ->
            if (k.moveToFirst()) lettre(k) else null
        }

    fun nonLus(c: Context, compte: String, dossier: String): Int =
        db(c).rawQuery("SELECT COUNT(*) FROM lettres WHERE compte = ? AND dossier = ? AND lu = 0", arrayOf(compte, dossier)).use { k -> if (k.moveToFirst()) k.getInt(0) else 0 }

    fun uids(c: Context, compte: String, dossier: String): Set<Long> =
        db(c).rawQuery("SELECT uid FROM lettres WHERE compte = ? AND dossier = ?", arrayOf(compte, dossier)).use { k -> buildSet { while (k.moveToNext()) add(k.getLong(0)) } }

    /** Garder des en-têtes : un mail déjà connu garde son texte ; seuls ses drapeaux (lu, étoile) changent. */
    fun garderEntetes(c: Context, l: List<Lettre>) {
        val d = db(c)
        d.beginTransaction()
        try {
            l.forEach { x ->
                val v = ContentValues().apply {
                    put("compte", x.compte); put("dossier", x.dossier); put("uid", x.uid); put("de", x.de); put("deNom", x.deNom); put("a", x.a); put("cc", x.cc)
                    put("sujet", x.sujet); put("date", x.date); put("lu", if (x.lu) 1 else 0); put("etoile", if (x.etoile) 1 else 0); put("pj", if (x.pj) 1 else 0)
                    put("extrait", x.extrait); put("taille", x.taille); put("charge", 0); put("idMessage", x.idMessage); put("refs", x.references)
                }
                if (d.insertWithOnConflict("lettres", null, v, SQLiteDatabase.CONFLICT_IGNORE) == -1L) {
                    d.update(
                        "lettres", ContentValues().apply { put("lu", if (x.lu) 1 else 0); put("etoile", if (x.etoile) 1 else 0) },
                        "compte = ? AND dossier = ? AND uid = ?", arrayOf(x.compte, x.dossier, x.uid.toString()),
                    )
                }
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    fun garderCorps(c: Context, compte: String, dossier: String, uid: Long, texte: String?, html: String?, extrait: String, pieces: List<Piece>) {
        val d = db(c)
        d.beginTransaction()
        try {
            d.update(
                "lettres",
                ContentValues().apply { put("texte", texte); put("html", html); put("extrait", extrait); put("charge", 1); put("pj", if (pieces.isNotEmpty()) 1 else 0) },
                "compte = ? AND dossier = ? AND uid = ?", arrayOf(compte, dossier, uid.toString()),
            )
            d.delete("pieces", "compte = ? AND dossier = ? AND uid = ?", arrayOf(compte, dossier, uid.toString()))
            pieces.forEach { p ->
                d.insert("pieces", null, ContentValues().apply {
                    put("compte", compte); put("dossier", dossier); put("uid", uid); put("n", p.n); put("nom", p.nom); put("type", p.type); put("taille", p.taille); put("chemin", p.chemin)
                })
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    fun pieces(c: Context, compte: String, dossier: String, uid: Long): List<Piece> =
        db(c).rawQuery("SELECT n, nom, type, taille, chemin FROM pieces WHERE compte = ? AND dossier = ? AND uid = ? ORDER BY n", arrayOf(compte, dossier, uid.toString())).use { k ->
            buildList { while (k.moveToNext()) add(Piece(k.getInt(0), k.getString(1), k.getString(2), k.getLong(3), k.getString(4))) }
        }

    /** La première pièce jointe de chaque mail d'une liste (pour la montrer sous l'extrait). */
    fun premieresPieces(c: Context, compte: String, dossier: String): Map<Long, String> =
        db(c).rawQuery("SELECT uid, nom FROM pieces WHERE compte = ? AND dossier = ? AND n = (SELECT MIN(n) FROM pieces p2 WHERE p2.compte = pieces.compte AND p2.dossier = pieces.dossier AND p2.uid = pieces.uid)", arrayOf(compte, dossier)).use { k ->
            buildMap { while (k.moveToNext()) put(k.getLong(0), k.getString(1)) }
        }

    /** Retirer les mails qui ne sont plus sur le serveur (dans la fenêtre relevée : à partir de [uidMin]). */
    fun retirerAbsents(c: Context, compte: String, dossier: String, uidMin: Long, presents: Set<Long>) {
        val partis = uids(c, compte, dossier).filter { it >= uidMin && it !in presents }
        partis.forEach { retirer(c, compte, dossier, it) }
    }

    fun retirer(c: Context, compte: String, dossier: String, uid: Long) {
        val a = arrayOf(compte, dossier, uid.toString())
        db(c).delete("lettres", "compte = ? AND dossier = ? AND uid = ?", a)
        db(c).delete("pieces", "compte = ? AND dossier = ? AND uid = ?", a)
    }

    fun marquer(c: Context, compte: String, dossier: String, uid: Long, lu: Boolean? = null, etoile: Boolean? = null) {
        val v = ContentValues().apply {
            lu?.let { put("lu", if (it) 1 else 0) }
            etoile?.let { put("etoile", if (it) 1 else 0) }
        }
        if (v.size() > 0) db(c).update("lettres", v, "compte = ? AND dossier = ? AND uid = ?", arrayOf(compte, dossier, uid.toString()))
    }

    /** Oublier tout un dossier (le serveur a renuméroté ses mails). */
    fun viderDossier(c: Context, compte: String, dossier: String) {
        db(c).delete("lettres", "compte = ? AND dossier = ?", arrayOf(compte, dossier))
        db(c).delete("pieces", "compte = ? AND dossier = ?", arrayOf(compte, dossier))
    }

    fun effacerCompte(c: Context, compte: String) {
        listOf("dossiers", "lettres", "pieces", "brouillons").forEach { db(c).delete(it, "compte = ?", arrayOf(compte)) }
    }

    // ——— Brouillons et envois ———

    fun garderBrouillon(c: Context, b: Brouillon): Long {
        val v = ContentValues().apply {
            put("compte", b.compte); put("a", b.a); put("cc", b.cc); put("sujet", b.sujet); put("texte", b.texte); put("pieces", JSONArray(b.pieces).toString())
            put("enReponseA", b.enReponseA); put("refs", b.references); put("etat", b.etat); put("erreur", b.erreur); put("quand", b.quand)
        }
        return if (b.id > 0) {
            db(c).update("brouillons", v, "id = ?", arrayOf(b.id.toString()))
            b.id
        } else {
            db(c).insert("brouillons", null, v)
        }
    }

    fun brouillons(c: Context, compte: String? = null, etats: List<String> = listOf("brouillon", "attente", "echec")): List<Brouillon> {
        val cond = "etat IN (${etats.joinToString(",") { "?" }})" + if (compte != null) " AND compte = ?" else ""
        val args = (etats + listOfNotNull(compte)).toTypedArray()
        return db(c).rawQuery("SELECT id, compte, a, cc, sujet, texte, pieces, enReponseA, refs, etat, erreur, quand FROM brouillons WHERE $cond ORDER BY quand DESC", args).use { k ->
            buildList {
                while (k.moveToNext()) {
                    val p = try {
                        JSONArray(k.getString(6) ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) } }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    add(Brouillon(k.getLong(0), k.getString(1), k.getString(2).orEmpty(), k.getString(3).orEmpty(), k.getString(4).orEmpty(), k.getString(5).orEmpty(), p, k.getString(7), k.getString(8), k.getString(9), k.getString(10), k.getLong(11)))
                }
            }
        }
    }

    fun brouillon(c: Context, id: Long) = brouillons(c, etats = listOf("brouillon", "attente", "echec", "envoi")).firstOrNull { it.id == id }

    fun retirerBrouillon(c: Context, id: Long) = db(c).delete("brouillons", "id = ?", arrayOf(id.toString()))
}
