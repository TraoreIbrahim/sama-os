package africa.samaos.agenda

import android.content.Context
import android.provider.ContactsContract
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/** À quoi sert l'Agenda (maquette agenda-1) : on en choisit un ou plusieurs, l'Agenda s'organise autour. */
enum class Usage(val nom: String, val texte: String) {
    ECOLE("École et études", "L'emploi du temps de la semaine, les devoirs et les contrôles, les dates de l'année scolaire."),
    TRAVAIL("Travail", "Les évènements, les tâches à cocher, la journée organisée heure par heure."),
    CYCLE("Mon cycle", "Les règles, la période fertile, les symptômes. Gardé sur ce téléphone, caché derrière votre code."),
}

object Usages {
    private fun prefs(c: Context) = c.getSharedPreferences("agenda", Context.MODE_PRIVATE)

    /** Les usages choisis, ou null si la question n'a pas encore été posée. */
    fun lire(c: Context): Set<Usage>? {
        val s = prefs(c).getString("usages", null) ?: return null
        return s.split(',').mapNotNull { n -> Usage.entries.firstOrNull { it.name == n } }.toSet()
    }

    fun regler(c: Context, u: Set<Usage>) = prefs(c).edit().putString("usages", u.joinToString(",") { it.name }).apply()
}

/** Un cours de l'emploi du temps : chaque semaine, le même jour, aux mêmes heures. */
class Cours(val id: Long, val matiere: String, val jour: DayOfWeek, val debut: LocalTime, val fin: LocalTime, val salle: String, val prof: String) {
    val couleur: Color get() = Matieres.couleur(matiere)
}

/** Un devoir à rendre, ou un contrôle à préparer. */
class Devoir(val id: Long, val titre: String, val matiere: String?, val pour: LocalDate, val controle: Boolean, val faitLe: Long?)

/** Une tâche du travail : pour un jour, ou sans date ; urgente ou non. */
class Tache(val id: Long, val titre: String, val pour: LocalDate?, val urgente: Boolean, val faiteLe: Long?)

object Matieres {
    /** Les matières qu'on propose en premier ; on peut en écrire d'autres. */
    val COURANTES = listOf("Maths", "Français", "Anglais", "SVT", "Phys.-chimie", "Hist.-géo", "EPS", "Espagnol", "Allemand", "Philo", "Arabe", "Info", "EDHC", "Arts")

    private val PALETTE = listOf(
        Color(0xFF3D5A99), Color(0xFFC4475E), Color(0xFFE2A62B), Color(0xFF2E8A6E), Color(0xFF5D4CC0), Color(0xFF8C6A50),
        Color(0xFFE9805A), Color(0xFF3FB6A8), Color(0xFF7A8B2E), Color(0xFF1C2233), Color(0xFFB0558C), Color(0xFF4F8FCB),
    )
    private val FIXES = mapOf(
        "maths" to 0, "francais" to 1, "anglais" to 2, "svt" to 3, "phys.-chimie" to 4, "physique" to 4, "hist.-geo" to 5, "histoire" to 5,
        "eps" to 6, "espagnol" to 7, "allemand" to 8, "philo" to 9, "arabe" to 10, "info" to 11,
    )

    fun simple(s: String) = Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    fun couleur(matiere: String): Color = PALETTE[FIXES[simple(matiere)] ?: Math.floorMod(simple(matiere).hashCode(), PALETTE.size)]
}

/** L'école et le travail, gardés dans l'appli (sur ce téléphone, dans cet Espace). */
object Carnet {
    private fun prefs(c: Context) = c.getSharedPreferences("agenda", Context.MODE_PRIVATE)
    private fun lire(c: Context, cle: String): List<JSONObject> = try {
        val a = JSONArray(prefs(c).getString(cle, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }
    } catch (_: Exception) {
        emptyList()
    }
    private fun ecrire(c: Context, cle: String, l: List<JSONObject>) = prefs(c).edit().putString(cle, JSONArray(l).toString()).apply()
    private fun nouvelId() = System.currentTimeMillis() * 10 + (0..9).random()

    // L'emploi du temps

    fun classe(c: Context): String = prefs(c).getString("classe", "").orEmpty()
    fun reglerClasse(c: Context, nom: String) = prefs(c).edit().putString("classe", nom.trim()).apply()

    fun cours(c: Context): List<Cours> = lire(c, "cours").map {
        Cours(it.getLong("id"), it.getString("m"), DayOfWeek.of(it.getInt("j")), LocalTime.parse(it.getString("d")), LocalTime.parse(it.getString("f")), it.optString("s"), it.optString("p"))
    }.sortedWith(compareBy({ it.jour }, { it.debut }))

    fun garderCours(c: Context, id: Long?, matiere: String, jour: DayOfWeek, debut: LocalTime, fin: LocalTime, salle: String, prof: String) {
        val o = JSONObject().put("id", id ?: nouvelId()).put("m", matiere.trim()).put("j", jour.value).put("d", debut.toString()).put("f", fin.toString()).put("s", salle.trim()).put("p", prof.trim())
        ecrire(c, "cours", lire(c, "cours").filter { it.getLong("id") != id } + o)
    }

    fun supprimerCours(c: Context, id: Long) = ecrire(c, "cours", lire(c, "cours").filter { it.getLong("id") != id })

    /** Le prochain cours de cette matière après aujourd'hui : « pour le prochain cours de maths ». */
    fun prochainCours(c: Context, matiere: String, apres: LocalDate = LocalDate.now()): LocalDate? {
        val jours = cours(c).filter { Matieres.simple(it.matiere) == Matieres.simple(matiere) }.map { it.jour }.toSet()
        if (jours.isEmpty()) return null
        return (1..14).map { apres.plusDays(it.toLong()) }.firstOrNull { it.dayOfWeek in jours }
    }

    // Les devoirs

    fun devoirs(c: Context): List<Devoir> = lire(c, "devoirs").map {
        Devoir(it.getLong("id"), it.getString("t"), it.optString("m").ifBlank { null }, LocalDate.parse(it.getString("pour")), it.optBoolean("ctrl"), it.optLong("fait").takeIf { f -> f > 0 })
    }.sortedBy { it.pour }

    fun garderDevoir(c: Context, id: Long?, titre: String, matiere: String?, pour: LocalDate, controle: Boolean) {
        val ancien = lire(c, "devoirs").firstOrNull { it.getLong("id") == id }
        val o = JSONObject().put("id", id ?: nouvelId()).put("t", titre.trim()).put("m", matiere.orEmpty()).put("pour", pour.toString()).put("ctrl", controle).put("fait", ancien?.optLong("fait") ?: 0)
        ecrire(c, "devoirs", lire(c, "devoirs").filter { it.getLong("id") != id } + o)
    }

    fun cocherDevoir(c: Context, id: Long, fait: Boolean) = ecrire(c, "devoirs", lire(c, "devoirs").map { if (it.getLong("id") == id) it.put("fait", if (fait) System.currentTimeMillis() else 0) else it })

    fun supprimerDevoir(c: Context, id: Long) = ecrire(c, "devoirs", lire(c, "devoirs").filter { it.getLong("id") != id })

    // Les tâches

    fun taches(c: Context): List<Tache> = lire(c, "taches").map {
        Tache(it.getLong("id"), it.getString("t"), it.optString("pour").ifBlank { null }?.let { d -> LocalDate.parse(d) }, it.optBoolean("urg"), it.optLong("fait").takeIf { f -> f > 0 })
    }.sortedWith(compareBy<Tache> { it.faiteLe != null }.thenBy { it.pour ?: LocalDate.MAX }.thenByDescending { it.urgente })

    fun garderTache(c: Context, id: Long?, titre: String, pour: LocalDate?, urgente: Boolean) {
        val ancien = lire(c, "taches").firstOrNull { it.getLong("id") == id }
        val o = JSONObject().put("id", id ?: nouvelId()).put("t", titre.trim()).put("pour", pour?.toString().orEmpty()).put("urg", urgente).put("fait", ancien?.optLong("fait") ?: 0)
        ecrire(c, "taches", lire(c, "taches").filter { it.getLong("id") != id } + o)
    }

    fun cocherTache(c: Context, id: Long, faite: Boolean) = ecrire(c, "taches", lire(c, "taches").map { if (it.getLong("id") == id) it.put("fait", if (faite) System.currentTimeMillis() else 0) else it })

    fun supprimerTache(c: Context, id: Long) = ecrire(c, "taches", lire(c, "taches").filter { it.getLong("id") != id })
}

/** Les anniversaires des contacts (lus dans le carnet, jamais copiés). */
object Anniversaires {
    class Anniversaire(val nom: String, val jour: MonthDay)

    fun tous(c: Context): List<Anniversaire> {
        val l = mutableListOf<Anniversaire>()
        try {
            c.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
                null,
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val d = cur.getString(1).orEmpty().removePrefix("--").split("-")
                    val md = try {
                        if (d.size == 3) MonthDay.of(d[1].toInt(), d[2].toInt()) else MonthDay.of(d[0].toInt(), d[1].toInt())
                    } catch (_: Exception) {
                        null
                    }
                    if (md != null) l += Anniversaire(cur.getString(0).orEmpty(), md)
                }
            }
        } catch (_: Exception) {
        }
        return l.distinctBy { it.nom + it.jour }
    }

    /** Ceux d'un jour donné. */
    fun du(l: List<Anniversaire>, d: LocalDate) = l.filter { it.jour == MonthDay.from(d) }
}
