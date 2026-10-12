package africa.samaos.horloge

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import africa.samaos.banco.appli.OutilsWidget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.Executors

// Les widgets de l'Horloge pour l'Accueil : la prochaine alarme (ou le minuteur qui tourne), et les horloges du
// monde, dont les aiguilles tournent seules à l'heure de chaque fuseau.

/** « Prochaine alarme » : l'alarme qui sonnera, ou le minuteur en cours, compté par Android sans réveiller l'appli. */
class WidgetReveil : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = WidgetsHorloge.plusTard(context, goAsync())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetsHorloge.MAJ) WidgetsHorloge.plusTard(context, goAsync()) else super.onReceive(context, intent)
    }
}

/** « Horloges du monde » : quatre villes de la liste de l'Horloge. */
class WidgetMonde : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = WidgetsHorloge.plusTard(context, goAsync())
}

object WidgetsHorloge {
    private val fil = Executors.newSingleThreadExecutor()
    internal const val MAJ = "africa.samaos.horloge.action.WIDGETS"
    private const val ACCENT = 0xFFA3D6C1.toInt()
    private const val REVEIL = "M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 9v4l2 2 M5 3L2 6 M19 3l3 3"
    private const val SABLIER = "M10 2h4 M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 13V9"

    /** Une alarme, le minuteur ou la liste des villes a changé : les widgets posés se redessinent. */
    fun annoncer(c: Context) {
        val a = c.applicationContext
        fil.execute {
            try {
                tout(a)
            } catch (_: Exception) {
            }
        }
    }

    internal fun plusTard(c: Context, fin: android.content.BroadcastReceiver.PendingResult) {
        val a = c.applicationContext
        fil.execute {
            try {
                tout(a)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }

    private fun tout(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        val reveils = m.getAppWidgetIds(ComponentName(c, WidgetReveil::class.java))
        val mondes = m.getAppWidgetIds(ComponentName(c, WidgetMonde::class.java))
        if (reveils.isNotEmpty()) {
            val v = vueReveil(c)
            reveils.forEach { m.updateAppWidget(it, v) }
        }
        if (mondes.isNotEmpty()) {
            val v = vueMonde(c)
            mondes.forEach { m.updateAppWidget(it, v) }
        }
        if (reveils.isNotEmpty() || mondes.isNotEmpty()) prochain(c)
    }

    /** Le prochain changement : minuit (« demain » devient « aujourd'hui »), l'heure suivante (le jour et la nuit des villes), l'alarme. */
    private fun prochain(c: Context) {
        val maintenant = ZonedDateTime.now()
        val heure = maintenant.plusHours(1).withMinute(0).withSecond(1).withNano(0)
        val alarme = Alarmes.liste(c).filter { it.active }.minOfOrNull { Alarmes.prochaine(it) }?.plusMinutes(1)
        val quand = listOfNotNull(heure, alarme).min().toInstant().toEpochMilli()
        OutilsWidget.reveil(c, quand, PendingIntent.getBroadcast(c, 7, Intent(c, WidgetReveil::class.java).setAction(MAJ), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun ouvrir(c: Context, action: String, code: Int) =
        PendingIntent.getActivity(c, code, Intent(action).setClass(c, Horloge::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    // ——— La prochaine alarme ———

    private fun vueReveil(c: Context): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_reveil)
        val alarme = Alarmes.liste(c).filter { it.active }.minByOrNull { Alarmes.prochaine(it).toInstant() }
        val quand = alarme?.let { Alarmes.prochaine(it) }
        val fin = Alarmes.finMinuteur(c)
        val pause = Alarmes.pauseRestante(c)
        val texteAlarme = quand?.let { "%02d:%02d".format(it.hour, it.minute) }
        if (fin != null || pause != null) {
            // Le minuteur passe devant : il défile en direct, compté par Android.
            rv.setImageViewBitmap(R.id.picto, OutilsWidget.picto(c, SABLIER, 28f, ACCENT, 1.9f))
            rv.setTextViewText(R.id.titre, Alarmes.nomMinuteur(c).ifBlank { "Minuteur" } + if (pause != null) " · en pause" else "")
            rv.setViewVisibility(R.id.heure, if (pause != null) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.chrono, if (pause != null) View.GONE else View.VISIBLE)
            if (pause != null) {
                val s = pause / 1000
                rv.setTextViewText(R.id.heure, if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60))
            } else {
                rv.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + (fin!! - System.currentTimeMillis()), null, true)
                if (Build.VERSION.SDK_INT >= 24) rv.setChronometerCountDown(R.id.chrono, true)
            }
            rv.setTextViewText(R.id.quand, if (texteAlarme != null) "Réveil $texteAlarme" else "")
            rv.setTextViewText(R.id.detail, quand?.let { Alarmes.jour(it) } ?: "")
            rv.setOnClickPendingIntent(R.id.racine, ouvrir(c, AlarmClock.ACTION_SET_TIMER, 11))
            rv.setContentDescription(R.id.racine, "Minuteur en cours" + (texteAlarme?.let { ". Prochaine alarme à $it, ${Alarmes.jour(quand)}" } ?: ""))
            return rv
        }
        rv.setImageViewBitmap(R.id.picto, OutilsWidget.picto(c, REVEIL, 28f, ACCENT, 1.9f))
        rv.setViewVisibility(R.id.chrono, View.GONE)
        rv.setViewVisibility(R.id.heure, View.VISIBLE)
        rv.setOnClickPendingIntent(R.id.racine, ouvrir(c, AlarmClock.ACTION_SHOW_ALARMS, 10))
        if (alarme == null || quand == null) {
            rv.setTextViewText(R.id.titre, "Prochaine alarme")
            rv.setTextViewText(R.id.heure, "Aucune")
            rv.setTextViewText(R.id.quand, "")
            rv.setTextViewText(R.id.detail, "Touchez pour en mettre une")
            rv.setContentDescription(R.id.racine, "Aucune alarme. Touchez pour en mettre une.")
            return rv
        }
        rv.setTextViewText(R.id.titre, alarme.nom.ifBlank { "Prochaine alarme" })
        rv.setTextViewText(R.id.heure, texteAlarme)
        rv.setTextViewText(R.id.quand, Alarmes.jour(quand).replaceFirstChar { it.uppercase() })
        rv.setTextViewText(R.id.detail, jours(alarme.jours))
        rv.setContentDescription(R.id.racine, "Prochaine alarme : ${alarme.nom.ifBlank { "réveil" }}, $texteAlarme, ${Alarmes.jour(quand)}. ${jours(alarme.jours)}.")
        return rv
    }

    /** « Une seule fois », « Tous les jours », « En semaine », « Le week-end », « Lun, mer, ven ». */
    private fun jours(j: Set<DayOfWeek>): String = when {
        j.isEmpty() -> "Une seule fois"
        j.size == 7 -> "Tous les jours"
        j == setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> "En semaine"
        j == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Le week-end"
        else -> j.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix(".") }.replaceFirstChar { it.uppercase() }
    }

    // ——— Les horloges du monde ———

    private fun vueMonde(c: Context): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_monde)
        rv.removeAllViews(R.id.racine)
        rv.setOnClickPendingIntent(R.id.racine, ouvrir(c, Intent.ACTION_MAIN, 12))
        val ici = ZonedDateTime.now()
        val l = villesDuMonde(c).take(4).ifEmpty { listOf(Ville("Ici", ZoneId.systemDefault().id, "")) }
        l.forEach { v ->
            val zone = try {
                ZoneId.of(v.zone)
            } catch (_: Exception) {
                return@forEach
            }
            val la = ici.withZoneSameInstant(zone)
            val jour = la.hour in 6..17
            val e = RemoteViews(c.packageName, R.layout.widget_monde_ville)
            if (Build.VERSION.SDK_INT >= 31) {
                e.setString(R.id.cadran, "setTimeZone", v.zone)
                e.setIcon(R.id.cadran, "setDial", Icon.createWithResource(c, if (jour) R.drawable.cadran_jour else R.drawable.cadran_nuit))
                e.setIcon(R.id.cadran, "setHourHand", Icon.createWithResource(c, if (jour) R.drawable.aiguille_heure_jour else R.drawable.aiguille_heure_nuit))
                e.setIcon(R.id.cadran, "setMinuteHand", Icon.createWithResource(c, if (jour) R.drawable.aiguille_minute_jour else R.drawable.aiguille_minute_nuit))
            }
            e.setTextViewText(R.id.ville, v.nom)
            e.setTextViewText(R.id.ecart, ecartCourt(ici, la))
            e.setContentDescription(R.id.cadran, "${v.nom} : ${"%02d:%02d".format(la.hour, la.minute)}, ${ecartCourt(ici, la)}")
            rv.addView(R.id.racine, e)
        }
        return rv
    }

    /** « Même heure », « +1 h », « −4 h · hier » : court, pour tenir sous un cadran. */
    private fun ecartCourt(ici: ZonedDateTime, la: ZonedDateTime): String {
        val minutes = (la.offset.totalSeconds - ici.offset.totalSeconds) / 60
        val h = minutes / 60
        val m = Math.abs(minutes % 60)
        val decalage = when {
            minutes == 0 -> "Même heure"
            minutes > 0 -> "+$h h" + if (m > 0) " $m" else ""
            else -> "−${-h} h" + if (m > 0) " $m" else ""
        }
        val d: LocalDate = la.toLocalDate()
        return decalage + when {
            d.isAfter(ici.toLocalDate()) -> " · demain"
            d.isBefore(ici.toLocalDate()) -> " · hier"
            else -> ""
        }
    }
}
