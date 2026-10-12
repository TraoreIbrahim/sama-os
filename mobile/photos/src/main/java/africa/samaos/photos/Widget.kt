package africa.samaos.photos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Size
import android.widget.RemoteViews
import africa.samaos.banco.appli.OutilsWidget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors

// Le widget de Photos pour l'Accueil : un souvenir par jour, tiré de la pellicule du téléphone (rien ne part).

/** « Souvenir » : une photo de ce jour une autre année, sinon une favorite, sinon une photo récente. */
class WidgetSouvenir : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = plusTard(context)

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) = plusTard(context)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetsPhotos.JOUR) plusTard(context) else super.onReceive(context, intent)
    }

    private fun plusTard(c: Context) {
        val fin = goAsync()
        WidgetsPhotos.fil.execute {
            try {
                WidgetsPhotos.dessiner(c.applicationContext)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }
}

object WidgetsPhotos {
    internal val fil = Executors.newSingleThreadExecutor()
    internal const val JOUR = "africa.samaos.photos.action.SOUVENIR"
    private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

    /** La pellicule a pu changer (une favorite, une photo jetée) : le souvenir est choisi de nouveau. */
    fun annoncer(c: Context) {
        val a = c.applicationContext
        fil.execute {
            try {
                dessiner(a)
            } catch (_: Exception) {
            }
        }
    }

    /** Le souvenir du jour : la photo, ce qu'on en dit (« Il y a 2 ans »), et le détail (la date, l'album). */
    private class Souvenir(val m: Media, val titre: String, val detail: String)

    private fun choisir(c: Context, auj: LocalDate): Souvenir? {
        // Les photos seulement (une vidéo n'a pas de bonne vignette fixe), sans captures d'écran ni documents.
        val l = Galerie.tout(c).filter { !it.video && !Galerie.estCapture(it) && !Galerie.estDocument(it) }
        if (l.isEmpty()) return null
        // Le même tirage toute la journée : la photo ne change qu'au jour suivant.
        val graine = auj.toEpochDay().toInt()
        fun <T> tirer(x: List<T>) = x[Math.floorMod(graine * 31 + 7, x.size)]
        fun date(m: Media) = m.jour.format(DATE)
        fun album(m: Media) = Galerie.nomAlbum(m.album, m.dossier).takeIf { it.isNotBlank() && it != "Autres" && it != "Appareil photo" }
        val ceJour = l.filter { it.jour.monthValue == auj.monthValue && it.jour.dayOfMonth == auj.dayOfMonth && it.jour.year < auj.year }
        if (ceJour.isNotEmpty()) {
            val m = tirer(ceJour)
            val ans = auj.year - m.jour.year
            return Souvenir(m, if (ans == 1) "Il y a un an" else "Il y a $ans ans", listOfNotNull(date(m), album(m)).joinToString(" · "))
        }
        val favoris = l.filter { it.favori }
        if (favoris.isNotEmpty()) {
            val m = tirer(favoris)
            return Souvenir(m, "Une favorite", listOfNotNull(date(m), album(m)).joinToString(" · "))
        }
        val m = tirer(l.take(60))
        val jours = ChronoUnit.DAYS.between(m.jour, auj)
        val titre = when {
            jours == 0L -> "Aujourd'hui"
            jours == 1L -> "Hier"
            jours < 7 -> "Cette semaine"
            jours < 31 -> "Il y a ${jours / 7} semaine${if (jours >= 14) "s" else ""}"
            jours < 365 -> "Il y a ${jours / 30} mois"
            else -> "Il y a ${jours / 365} an${if (jours >= 730) "s" else ""}"
        }
        return Souvenir(m, titre, listOfNotNull(date(m), album(m)).joinToString(" · "))
    }

    internal fun dessiner(c: Context) {
        val gestion = AppWidgetManager.getInstance(c)
        val ids = gestion.getAppWidgetIds(ComponentName(c, WidgetSouvenir::class.java))
        if (ids.isEmpty()) return
        val auj = LocalDate.now()
        val s = choisir(c, auj)
        val source = s?.let {
            try {
                c.contentResolver.loadThumbnail(it.m.uri, Size(1024, 1024), null)
            } catch (_: Exception) {
                null
            }
        }
        ids.forEach { id ->
            val rv = RemoteViews(c.packageName, R.layout.widget_souvenir)
            val t = OutilsWidget.taille(gestion, id)
            if (s != null && source != null) {
                // Coins droits : c'est le cadre du widget qui arrondit (sinon son fond dépasse sous l'arrondi de l'image).
                rv.setImageViewBitmap(R.id.photo, OutilsWidget.photo(c, source, t?.width ?: 380f, t?.height ?: 192f, 0f, voile = true))
                rv.setTextViewText(R.id.titre, s.titre)
                rv.setTextViewText(R.id.detail, s.detail)
                rv.setOnClickPendingIntent(
                    R.id.racine,
                    PendingIntent.getActivity(c, 1, Intent(Intent.ACTION_VIEW, s.m.uri).setClass(c, Photos::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
                rv.setContentDescription(R.id.racine, "Souvenir : ${s.titre}, ${s.detail}. Touchez pour voir la photo.")
            } else {
                rv.setTextViewText(R.id.titre, "Vos souvenirs")
                rv.setTextViewText(R.id.detail, "Vos photos viendront ici, une par jour")
                rv.setOnClickPendingIntent(R.id.racine, PendingIntent.getActivity(c, 0, Intent(c, Photos::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
                rv.setContentDescription(R.id.racine, "Souvenir : vos photos viendront ici.")
            }
            gestion.updateAppWidget(id, rv)
        }
        // Un nouveau souvenir le lendemain, au premier coup d'œil après minuit.
        val minuit = LocalDateTime.of(auj.plusDays(1), java.time.LocalTime.of(0, 0, 30)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        OutilsWidget.reveil(c, minuit, PendingIntent.getBroadcast(c, 3, Intent(c, WidgetSouvenir::class.java).setAction(JOUR), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }
}
