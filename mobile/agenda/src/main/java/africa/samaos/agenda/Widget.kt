package africa.samaos.agenda

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract.Events
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.Executors

// Les widgets de l'Agenda pour l'Accueil, en trois tailles comme sur l'iPhone : ce qui vient (les rendez-vous, les
// cours de l'emploi du temps, les anniversaires), et ce qui reste à faire (devoirs, tâches). Le cycle n'y paraît
// jamais : il reste caché derrière le code du téléphone.

/** Ce qu'un widget montre : ce qui vient (une rangée), ma journée (deux), les prochains jours (trois). */
internal enum class SorteWidget { BREF, HEURES, SEMAINE }

/** « Ma journée » : le widget proposé d'abord. */
open class WidgetAgenda : AppWidgetProvider() {
    internal open val sorte = SorteWidget.HEURES

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = plusTard(context) { WidgetsAgenda.dessiner(it, appWidgetIds, sorte) }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        plusTard(context) { WidgetsAgenda.dessiner(it, intArrayOf(appWidgetId), sorte) }

    override fun onReceive(context: Context, intent: Intent) {
        // Un moment commence ou finit, ou un nouveau jour : tous les widgets se redessinent.
        if (intent.action == WidgetsAgenda.MOMENT) plusTard(context) { WidgetsAgenda.toutDessiner(it) } else super.onReceive(context, intent)
    }

    private fun plusTard(c: Context, travail: (Context) -> Unit) {
        val fin = goAsync()
        WidgetsAgenda.fil.execute {
            try {
                travail(c.applicationContext)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }
}

class WidgetAgendaBref : WidgetAgenda() {
    override val sorte = SorteWidget.BREF
}

class WidgetAgendaSemaine : WidgetAgenda() {
    override val sorte = SorteWidget.SEMAINE
}

/** Ce qui est prévu : un évènement, un cours ou un anniversaire, avec sa couleur. [evenement] : pour l'ouvrir d'une touche. */
internal class Moment(
    val jour: LocalDate, val titre: String, val debut: LocalDateTime?, val fin: LocalDateTime?,
    val lieu: String?, val couleur: Int, val evenement: Long? = null,
) {
    val journee: Boolean get() = debut == null
}

object WidgetsAgenda {
    internal val fil = Executors.newSingleThreadExecutor()
    internal const val MOMENT = "africa.samaos.agenda.action.MOMENT"
    private val SORTES = listOf(WidgetAgendaBref::class.java to SorteWidget.BREF, WidgetAgenda::class.java to SorteWidget.HEURES, WidgetAgendaSemaine::class.java to SorteWidget.SEMAINE)
    private const val JOURS = 7

    /** L'Agenda a changé (un évènement, un cours, un devoir, une tâche) : les widgets posés se redessinent. */
    fun annoncer(c: Context) {
        val a = c.applicationContext
        fil.execute {
            try {
                toutDessiner(a)
            } catch (_: Exception) {
            }
        }
    }

    internal fun toutDessiner(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        SORTES.forEach { (classe, sorte) ->
            val ids = m.getAppWidgetIds(ComponentName(c, classe))
            if (ids.isNotEmpty()) dessiner(c, ids, sorte)
        }
    }

    internal fun dessiner(c: Context, ids: IntArray, sorte: SorteWidget) {
        val m = AppWidgetManager.getInstance(c)
        val maintenant = LocalDateTime.now()
        val moments = moments(c, maintenant)
        val resume = resume(c, maintenant.toLocalDate())
        ids.forEach { id ->
            @Suppress("DEPRECATION")
            val taille = m.getAppWidgetOptions(id).getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.firstOrNull()
            val hauteur = taille?.height ?: m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).toFloat()
            m.updateAppWidget(id, vue(c, sorte, maintenant, moments, resume, hauteur))
        }
        prochainMoment(c, maintenant, moments)
    }

    // ——— Ce qui vient ———

    /** Les sept prochains jours : évènements, cours (si l'Agenda sert à l'école), anniversaires ; ce qui est fini est laissé. */
    internal fun moments(c: Context, maintenant: LocalDateTime): List<Moment> {
        val auj = maintenant.toLocalDate()
        val zone = ZoneId.systemDefault()
        val l = mutableListOf<Moment>()
        val debut = auj.atStartOfDay(zone).toInstant().toEpochMilli()
        val fin = auj.plusDays(JOURS.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        Agendas.entre(c, debut, fin).forEach { e ->
            val couleur = couleurEvenement(e).toArgb()
            if (e.journee) {
                // Une journée entière est notée à minuit UTC ; plusieurs jours : elle compte encore aujourd'hui.
                val d1 = Instant.ofEpochMilli(e.debut).atZone(ZoneId.of("UTC")).toLocalDate()
                val d2 = Instant.ofEpochMilli(e.fin).atZone(ZoneId.of("UTC")).toLocalDate().minusDays(1)
                if (d2.isBefore(auj)) return@forEach
                l += Moment(maxOf(d1, auj), e.titre.ifBlank { "Sans titre" }, null, null, e.lieu?.ifBlank { null }, couleur, e.id)
            } else {
                val d = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.debut), zone)
                val f = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.fin), zone)
                if (!f.isAfter(maintenant)) return@forEach
                // Commencé avant maintenant (même hier soir) : il compte pour aujourd'hui.
                l += Moment(if (d.isBefore(maintenant)) auj else d.toLocalDate(), e.titre.ifBlank { "Sans titre" }, d, f, e.lieu?.ifBlank { null }, couleur, e.id)
            }
        }
        val usages = Usages.lire(c).orEmpty()
        if (Usage.ECOLE in usages) {
            val cours = Carnet.cours(c)
            for (k in 0 until JOURS) {
                val d = auj.plusDays(k.toLong())
                cours.filter { it.jour == d.dayOfWeek }.forEach { co ->
                    val f = d.atTime(co.fin)
                    if (f.isAfter(maintenant)) l += Moment(d, co.matiere, d.atTime(co.debut), f, co.salle.ifBlank { null }, co.couleur.toArgb())
                }
            }
        }
        val anniversaires = Anniversaires.tous(c)
        for (k in 0 until JOURS) {
            val d = auj.plusDays(k.toLong())
            Anniversaires.du(anniversaires, d).forEach { l += Moment(d, "Anniversaire de ${it.nom}", null, null, null, OR_ANNIVERSAIRE) }
        }
        return l.sortedWith(compareBy<Moment> { it.jour }.thenBy { !it.journee }.thenBy { it.debut })
    }

    /** Ce qui reste à faire : les devoirs en retard, pour aujourd'hui et demain, les contrôles de la semaine, les tâches. */
    internal fun resume(c: Context, auj: LocalDate): List<String> {
        val usages = Usages.lire(c).orEmpty()
        val l = mutableListOf<String>()
        if (Usage.ECOLE in usages) {
            val devoirs = Carnet.devoirs(c).filter { it.faitLe == null }
            fun compte(n: Int, quoi: String) = "$n $quoi${if (n > 1) "s" else ""}"
            devoirs.count { it.pour.isBefore(auj) }.takeIf { it > 0 }?.let { l += compte(it, "devoir") + " en retard" }
            devoirs.count { it.pour == auj && !it.controle }.takeIf { it > 0 }?.let { l += compte(it, "devoir") + " pour aujourd'hui" }
            devoirs.count { it.pour == auj.plusDays(1) && !it.controle }.takeIf { it > 0 }?.let { l += compte(it, "devoir") + " pour demain" }
            devoirs.filter { it.controle && !it.pour.isBefore(auj) && it.pour.isBefore(auj.plusDays(7)) }.minByOrNull { it.pour }?.let { d ->
                // « de maths », mais « de SVT » : les sigles gardent leurs capitales.
                l += "Contrôle " + (d.matiere?.let { m -> "de ${if (m == m.uppercase(Locale.FRENCH)) m else m.lowercase(Locale.FRENCH)} " } ?: "") + quand(d.pour, auj)
            }
        }
        if (Usage.TRAVAIL in usages) {
            val taches = Carnet.taches(c).filter { it.faiteLe == null && (it.pour == null || !it.pour.isAfter(auj)) }
            if (taches.isNotEmpty()) {
                val urgentes = taches.count { it.urgente }
                l += "${taches.size} tâche${if (taches.size > 1) "s" else ""}" + if (urgentes > 0) ", dont $urgentes urgente${if (urgentes > 1) "s" else ""}" else ""
            }
        }
        return l
    }

    /** « aujourd'hui », « demain », « mardi ». */
    private fun quand(d: LocalDate, auj: LocalDate) = when (d) {
        auj -> "aujourd'hui"
        auj.plusDays(1) -> "demain"
        else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH)
    }

    /** « 10:00 – 11:00 · S. 4 », « Maintenant, jusqu'à 11:00 », « Toute la journée ». */
    private fun detail(m: Moment, maintenant: LocalDateTime): String {
        val heures = when {
            m.journee -> if (m.titre.startsWith("Anniversaire")) null else "Toute la journée"
            m.debut!!.isAfter(maintenant) -> "${heure(m.debut.toLocalTime())} – ${heure(m.fin!!.toLocalTime())}"
            else -> "Maintenant, jusqu'à ${heure(m.fin!!.toLocalTime())}"
        }
        return listOfNotNull(heures, m.lieu).joinToString(" · ")
    }

    private fun nomJour(d: LocalDate, auj: LocalDate) = when (d) {
        auj -> "Aujourd'hui"
        auj.plusDays(1) -> "Demain"
        else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar { it.uppercase() } + " ${d.dayOfMonth}"
    }

    // ——— Le dessin ———

    private fun vue(c: Context, sorte: SorteWidget, maintenant: LocalDateTime, moments: List<Moment>, resume: List<String>, hauteurDp: Float): RemoteViews {
        val auj = maintenant.toLocalDate()
        val rv = RemoteViews(c.packageName, if (sorte == SorteWidget.BREF) R.layout.widget_agenda_bref else R.layout.widget_agenda)
        rv.setOnClickPendingIntent(R.id.racine, ouvrirAgenda(c))
        rv.setTextViewText(R.id.semaine, auj.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).uppercase(Locale.FRENCH))
        rv.setTextViewText(R.id.numero, auj.dayOfMonth.toString())
        val titreDate = auj.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
        if (sorte == SorteWidget.BREF) {
            val premier = moments.firstOrNull()
            if (premier == null) {
                rv.setViewVisibility(R.id.barre, View.GONE)
                rv.setTextViewText(R.id.titre, "Rien de prévu")
                rv.setTextViewText(R.id.detail, resume.firstOrNull() ?: "Touchez pour ajouter un évènement")
                rv.setTextViewText(R.id.puis, resume.getOrNull(1) ?: "")
                rv.setContentDescription(R.id.racine, "Agenda, $titreDate : rien de prévu cette semaine.")
                return rv
            }
            rv.setViewVisibility(R.id.barre, View.VISIBLE)
            rv.setInt(R.id.barre, "setColorFilter", premier.couleur)
            rv.setTextViewText(R.id.titre, premier.titre)
            rv.setTextViewText(R.id.detail, listOfNotNull(nomJour(premier.jour, auj).takeIf { premier.jour != auj }, detail(premier, maintenant).ifBlank { null }).joinToString(" · "))
            val suivant = moments.getOrNull(1)
            val puis = when {
                suivant == null -> resume.firstOrNull() ?: ""
                suivant.journee -> "Puis : ${suivant.titre}" + if (suivant.jour != premier.jour) ", ${nomJour(suivant.jour, auj).lowercase(Locale.FRENCH)}" else ""
                else -> "Puis : ${suivant.titre} à ${heure(suivant.debut!!.toLocalTime())}" + if (suivant.jour != premier.jour) ", ${nomJour(suivant.jour, auj).lowercase(Locale.FRENCH)}" else ""
            }
            rv.setTextViewText(R.id.puis, puis)
            premier.evenement?.let { rv.setOnClickPendingIntent(R.id.racine, ouvrirEvenement(c, it)) }
            rv.setContentDescription(R.id.racine, "Agenda, $titreDate. ${premier.titre}, ${detail(premier, maintenant)}. $puis")
            return rv
        }

        rv.setTextViewText(R.id.mois, auj.month.getDisplayName(TextStyle.FULL, Locale.FRENCH))
        rv.removeAllViews(R.id.resume)
        resume.take(3).forEach { r -> rv.addView(R.id.resume, RemoteViews(c.packageName, R.layout.widget_agenda_rappel).apply { setTextViewText(R.id.rappel, r) }) }
        rv.removeAllViews(R.id.liste)
        // La place de la liste : la hauteur du widget, moins ses marges ; chaque moment prend 38 dp, un nom de jour 22.
        var place = (if (hauteurDp > 0) hauteurDp else if (sorte == SorteWidget.SEMAINE) 292f else 192f) - 26f
        var jourMontre: LocalDate? = null
        var montres = 0
        if (moments.none { it.jour == auj }) {
            rv.addView(R.id.liste, texte(c, if (moments.isEmpty()) "Rien de prévu cette semaine." else "Rien d'autre aujourd'hui."))
            place -= 30f
            jourMontre = auj
        }
        for (m in moments) {
            val enTete = m.jour != jourMontre && m.jour != auj
            val besoin = 38f + if (enTete) 22f else 0f
            if (besoin > place) break
            if (enTete) rv.addView(R.id.liste, RemoteViews(c.packageName, R.layout.widget_agenda_jour).apply { setTextViewText(R.id.jour, nomJour(m.jour, auj)) })
            jourMontre = m.jour
            rv.addView(R.id.liste, ligne(c, m, maintenant))
            place -= besoin
            montres++
        }
        val reste = moments.size - montres
        rv.setContentDescription(
            R.id.racine,
            "Agenda, $titreDate. " + moments.take(montres).joinToString(". ") { "${nomJour(it.jour, auj)}, ${it.titre}, ${detail(it, maintenant)}" } +
                (if (reste > 0) ". Et $reste autres" else "") + resume.joinToString("") { ". $it" },
        )
        return rv
    }

    private fun ligne(c: Context, m: Moment, maintenant: LocalDateTime): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_agenda_moment)
        rv.setInt(R.id.barre, "setColorFilter", m.couleur)
        rv.setTextViewText(R.id.titre, m.titre)
        val d = detail(m, maintenant)
        rv.setTextViewText(R.id.detail, d)
        rv.setViewVisibility(R.id.detail, if (d.isBlank()) View.GONE else View.VISIBLE)
        // Un évènement s'ouvre d'une touche ; un cours ou un anniversaire ouvre l'Agenda.
        rv.setOnClickPendingIntent(R.id.moment, m.evenement?.let { ouvrirEvenement(c, it) } ?: ouvrirAgenda(c))
        return rv
    }

    private fun texte(c: Context, t: String) = RemoteViews(c.packageName, R.layout.widget_agenda_texte).apply { setTextViewText(R.id.texte, t) }

    private fun ouvrirAgenda(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, Agenda::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun ouvrirEvenement(c: Context, id: Long): PendingIntent = PendingIntent.getActivity(
        c, (id % 100_000).toInt() + 1,
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(Events.CONTENT_URI, id)).setClass(c, Agenda::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Le prochain changement : un moment qui commence ou finit, ou minuit. Le réveil ne réveille pas le téléphone ;
     * l'Agenda, appli d'agenda, a le droit de le régler à la minute près.
     */
    private fun prochainMoment(c: Context, maintenant: LocalDateTime, moments: List<Moment>) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val minuit = maintenant.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT)
        val suivant = (moments.flatMap { listOfNotNull(it.debut, it.fin) }.filter { it.isAfter(maintenant) } + minuit).min()
        val quand = suivant.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000
        val pi = PendingIntent.getBroadcast(c, 2, Intent(c, WidgetAgenda::class.java).setAction(MOMENT), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, quand, pi) else am.setWindow(AlarmManager.RTC, quand, 60_000L, pi)
    }

    private val OR_ANNIVERSAIRE = 0xFFE2A62B.toInt()
}
