package africa.samaos.accueil

import africa.samaos.banco.*
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Ce qu'on pose sur l'Accueil : une appli, ou un dossier d'applis. */
sealed interface Element {
    val id: String
}

/** Une appli, désignée par « paquet/activité ». */
data class ElemAppli(val cle: String) : Element {
    override val id: String get() = "a:$cle"
}

data class ElemDossier(val ident: String, val nom: String, val applis: List<String>) : Element {
    override val id: String get() = "d:$ident"
}

/** Un widget posé sous l'heure, avec sa hauteur en rangées. */
data class WidgetPose(val id: Int, val rangees: Int)

fun cleDe(a: Appli) = "${a.paquet}/${a.activite}"

/**
 * L'Accueil de l'Espace tel que la personne l'a rangé : ses applis et dossiers, ses widgets.
 * Chaque Espace a le sien (chaque profil a ses réglages). Tant qu'on n'y a pas touché, l'Accueil
 * se remplit tout seul des applis usuelles.
 */
class Disposition(contexte: Context) {
    private val prefs = reglages(contexte, "accueil")

    /** null : jamais rangé, l'Accueil choisit lui-même. */
    var rangees by mutableStateOf(lireElements())
        private set

    var widgets by mutableStateOf(lireWidgets())
        private set

    /** Les éléments à montrer, les applis disparues retirées. */
    fun elements(applis: List<Appli>, natte: List<Appli>): List<Element> {
        val presentes = applis.map { cleDe(it) }.toSet()
        // Les Paramètres d'Android posés sur l'Accueil deviennent les Réglages de Sama quand ceux-ci arrivent.
        // Une appli d'Android posée sur l'Accueil devient l'appli de Sama qui la remplace.
        val parPaquet = REMPLACEES.entries.associate { (sama, android) -> android to applis.firstOrNull { it.paquet == sama }?.let { cleDe(it) } }
        fun remplacer(cle: String) = if (cle in presentes) cle else parPaquet[cle.substringBefore('/')] ?: cle
        val liste = rangees ?: applisDeLAccueil(applis, natte, PREFEREES_MAISON).map { ElemAppli(cleDe(it)) }
        return liste.map { e ->
            when (e) {
                is ElemAppli -> ElemAppli(remplacer(e.cle))
                is ElemDossier -> e.copy(applis = e.applis.map { remplacer(it) }.distinct())
            }
        }.mapNotNull { e ->
            when (e) {
                is ElemAppli -> e.takeIf { it.cle in presentes }
                is ElemDossier -> {
                    val restantes = e.applis.filter { it in presentes }
                    when (restantes.size) {
                        0 -> null
                        1 -> ElemAppli(restantes.first())
                        else -> e.copy(applis = restantes)
                    }
                }
            }
        }
    }

    fun changer(liste: List<Element>) {
        rangees = liste
        prefs.edit().putString(CLE_ELEMENTS, versJson(liste)).apply()
    }

    fun contient(liste: List<Element>, cle: String) = liste.any { e ->
        (e is ElemAppli && e.cle == cle) || (e is ElemDossier && cle in e.applis)
    }

    fun ajouter(liste: List<Element>, cle: String): Boolean {
        if (contient(liste, cle) || liste.size >= MAXIMUM) return false
        changer(liste + ElemAppli(cle))
        return true
    }

    fun retirer(liste: List<Element>, cle: String) = changer(
        liste.mapNotNull { e ->
            when (e) {
                is ElemAppli -> e.takeIf { it.cle != cle }
                is ElemDossier -> {
                    val restantes = e.applis - cle
                    if (restantes.size == 1) ElemAppli(restantes.first()) else e.copy(applis = restantes)
                }
            }
        },
    )

    fun deplacer(liste: List<Element>, depuis: Int, vers: Int) {
        if (depuis == vers || depuis !in liste.indices) return
        changer(liste.toMutableList().apply { add(vers.coerceIn(0, size - 1), removeAt(depuis)) })
    }

    /** Une appli lâchée sur une autre fait un dossier ; lâchée sur un dossier, elle y entre. */
    fun regrouper(liste: List<Element>, depuis: Int, sur: Int): Boolean {
        val tenu = liste.getOrNull(depuis) as? ElemAppli ?: return false
        val cible = liste.getOrNull(sur) ?: return false
        if (depuis == sur) return false
        val nouveau = when (cible) {
            is ElemAppli -> ElemDossier(UUID.randomUUID().toString().take(8), "Dossier", listOf(cible.cle, tenu.cle))
            is ElemDossier -> cible.copy(applis = cible.applis + tenu.cle)
        }
        changer(liste.toMutableList().apply { set(sur, nouveau) }.filterIndexed { i, _ -> i != depuis })
        return true
    }

    fun renommer(liste: List<Element>, dossier: ElemDossier, nom: String) =
        changer(liste.map { if (it.id == dossier.id) dossier.copy(nom = nom.ifBlank { "Dossier" }) else it })

    /** Sortir une appli d'un dossier : elle se pose juste après lui ; un dossier d'une seule appli se défait. */
    fun sortir(liste: List<Element>, dossier: ElemDossier, cle: String) {
        val i = liste.indexOfFirst { it.id == dossier.id }
        if (i < 0) return
        val restantes = dossier.applis - cle
        val resultat = liste.toMutableList()
        resultat[i] = if (restantes.size == 1) ElemAppli(restantes.first()) else dossier.copy(applis = restantes)
        if (resultat.size < MAXIMUM) resultat.add(i + 1, ElemAppli(cle))
        changer(resultat)
    }

    fun retablir() {
        rangees = null
        prefs.edit().remove(CLE_ELEMENTS).apply()
    }

    // ——— Widgets ———

    fun poserWidget(w: WidgetPose) {
        widgets = listOf(w) + widgets
        ecrireWidgets()
    }

    fun retirerWidget(id: Int) {
        widgets = widgets.filter { it.id != id }
        ecrireWidgets()
    }

    private fun ecrireWidgets() =
        prefs.edit().putString(CLE_WIDGETS, widgets.joinToString(",") { "${it.id}:${it.rangees}" }).apply()

    private fun lireWidgets(): List<WidgetPose> =
        prefs.getString(CLE_WIDGETS, null)?.split(",")?.mapNotNull { p ->
            val (id, r) = p.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            WidgetPose(id.toIntOrNull() ?: return@mapNotNull null, r.toIntOrNull() ?: 1)
        }.orEmpty()

    // ——— Enregistrement ———

    private fun lireElements(): List<Element>? = try {
        prefs.getString(CLE_ELEMENTS, null)?.let { texte ->
            val a = JSONArray(texte)
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                when (o.optString("t")) {
                    "a" -> ElemAppli(o.getString("c"))
                    "d" -> ElemDossier(
                        o.getString("i"),
                        o.optString("n", "Dossier"),
                        o.getJSONArray("a").let { l -> (0 until l.length()).map { l.getString(it) } },
                    )
                    else -> null
                }
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun versJson(liste: List<Element>): String = JSONArray(
        liste.map { e ->
            when (e) {
                is ElemAppli -> JSONObject().put("t", "a").put("c", e.cle)
                is ElemDossier -> JSONObject().put("t", "d").put("i", e.ident).put("n", e.nom).put("a", JSONArray(e.applis))
            }
        },
    ).toString()

    companion object {
        private const val CLE_ELEMENTS = "elements"
        private const val CLE_WIDGETS = "widgets"

        /** Trois rangées de quatre : au-delà, l'Accueil mangerait le ciel. La Cour garde toutes les applis. */
        const val MAXIMUM = 12
    }
}
