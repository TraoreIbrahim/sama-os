package africa.samaos.dictaphone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

// Le widget du Dictaphone pour l'Accueil : enregistrer d'un toucher, et voir l'enregistrement qui tourne.

/** « Enregistrer » : le grand bouton ; pendant l'enregistrement, le temps, pause et arrêter. */
class WidgetDictaphone : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val fin = goAsync()
        WidgetsDictaphone.fil.execute {
            try {
                WidgetsDictaphone.dessiner(context.applicationContext)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }
}

/** Une demande d'enregistrer venue du widget : l'écran du Dictaphone la prend en charge (le micro, l'accord). */
object DemandeEnregistrer {
    val flot = MutableStateFlow(false)
    const val EXTRA = "enregistrer"
}

object WidgetsDictaphone {
    internal val fil = Executors.newSingleThreadExecutor()

    /** L'enregistrement a commencé, s'est mis en pause ou s'est arrêté : le widget se redessine. */
    fun annoncer(c: Context) {
        val a = c.applicationContext
        fil.execute {
            try {
                dessiner(a)
            } catch (_: Exception) {
            }
        }
    }

    private fun service(c: Context, action: String, code: Int) =
        PendingIntent.getService(c, code, Intent(c, Enregistreur::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    internal fun dessiner(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        val ids = m.getAppWidgetIds(ComponentName(c, WidgetDictaphone::class.java))
        if (ids.isEmpty()) return
        val rv = RemoteViews(c.packageName, R.layout.widget_dictaphone)
        val ouvrir = PendingIntent.getActivity(c, 0, Intent(c, Dictaphone::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.racine, ouvrir)
        val e = Dicta.etat.value
        if (e == null) {
            rv.setImageViewResource(R.id.bouton, R.drawable.widget_enregistrer)
            rv.setContentDescription(R.id.bouton, "Enregistrer")
            rv.setOnClickPendingIntent(
                R.id.bouton,
                PendingIntent.getActivity(c, 1, Intent(c, Dictaphone::class.java).putExtra(DemandeEnregistrer.EXTRA, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            rv.setTextViewText(R.id.titre, "Enregistrer")
            rv.setViewVisibility(R.id.chrono, View.GONE)
            rv.setViewVisibility(R.id.pause, View.GONE)
            val derniere = Pistes.liste(c).firstOrNull()
            rv.setTextViewText(R.id.detail, derniere?.let { "Dernier : ${it.nom} · ${longueur(it.duree)}, ${jour(it.date)}" } ?: "Un toucher, et le Dictaphone écoute")
            rv.setContentDescription(R.id.racine, "Dictaphone" + (derniere?.let { ". Dernier enregistrement : ${it.nom}, ${longueur(it.duree)}, ${jour(it.date)}" } ?: ""))
        } else {
            rv.setImageViewResource(R.id.bouton, R.drawable.widget_arreter)
            rv.setContentDescription(R.id.bouton, "Arrêter et garder l'enregistrement")
            rv.setOnClickPendingIntent(R.id.bouton, service(c, Dicta.ARRETER, 2))
            rv.setTextViewText(R.id.titre, e.nom)
            rv.setViewVisibility(R.id.chrono, View.VISIBLE)
            // Le temps qui passe, compté par Android ; arrêté pendant la pause.
            rv.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() - e.duree(), null, !e.enPause)
            rv.setTextViewText(R.id.detail, if (e.enPause) "En pause" else "Enregistrement en cours")
            rv.setViewVisibility(R.id.pause, View.VISIBLE)
            rv.setImageViewResource(R.id.pause, if (e.enPause) R.drawable.widget_reprendre else R.drawable.widget_pause)
            rv.setContentDescription(R.id.pause, if (e.enPause) "Reprendre" else "Pause")
            rv.setOnClickPendingIntent(R.id.pause, service(c, if (e.enPause) Dicta.REPRENDRE else Dicta.PAUSE, 3))
            rv.setContentDescription(R.id.racine, "Dictaphone : ${e.nom}, " + if (e.enPause) "en pause" else "enregistrement en cours")
        }
        ids.forEach { m.updateAppWidget(it, rv) }
    }
}
