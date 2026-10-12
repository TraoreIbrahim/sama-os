package africa.samaos.lecteur

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import africa.samaos.banco.appli.OutilsWidget
import java.util.concurrent.Executors

// Le widget du Lecteur pour l'Accueil : ce qui joue, et de quoi le commander sans ouvrir l'appli.

/** « En cours » : la pochette, le titre, l'artiste ; précédent, lecture ou pause, suivant. */
class WidgetLecture : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val fin = goAsync()
        WidgetsLecteur.fil.execute {
            try {
                WidgetsLecteur.dessiner(context.applicationContext)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }
}

/**
 * Les boutons du widget : ils passent par ici, dans l'appli, qui commande la platine. Toucher un widget autorise
 * l'appli à reprendre la musique au premier plan, même fermée.
 */
class CommandeLecteur : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val action = i.action ?: return
        if (Platine.etat.value == null) {
            // Plus rien en mémoire (l'appli a été fermée) : le Lecteur s'ouvre.
            c.startActivity(Intent(c, Lecteur::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        try {
            Platine.commander(c, action)
        } catch (_: Exception) {
        }
    }
}

object WidgetsLecteur {
    internal val fil = Executors.newSingleThreadExecutor()
    private const val NOTE = "M9 18V6l10-2v12 M9 18a3 3 0 1 1-6 0a3 3 0 1 1 6 0z M19 16a3 3 0 1 1-6 0a3 3 0 1 1 6 0z"

    /** La pochette du morceau en cours, posée par le service. */
    @Volatile
    internal var pochette: Bitmap? = null

    /** Ce qui joue a changé (morceau, lecture, pause, arrêt) : le widget se redessine. */
    fun annoncer(c: Context) {
        val a = c.applicationContext
        fil.execute {
            try {
                dessiner(a)
            } catch (_: Exception) {
            }
        }
    }

    internal fun dessiner(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        val ids = m.getAppWidgetIds(ComponentName(c, WidgetLecture::class.java))
        if (ids.isEmpty()) return
        val rv = RemoteViews(c.packageName, R.layout.widget_lecture)
        val e = Platine.etat.value
        val morceau = e?.courant
        rv.setOnClickPendingIntent(
            R.id.racine,
            PendingIntent.getActivity(c, 0, Intent(c, Lecteur::class.java).putExtra("platine", morceau != null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        val p = pochette
        val nuit = (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        rv.setImageViewBitmap(
            R.id.pochette,
            if (morceau != null && p != null) OutilsWidget.photo(c, p, 62f, 62f, 12f, voile = false)
            else carre(c, nuit),
        )
        if (morceau == null) {
            rv.setTextViewText(R.id.titre, "Rien en lecture")
            rv.setTextViewText(R.id.artiste, "Touchez pour écouter votre musique")
            listOf(R.id.precedent, R.id.lecture, R.id.suivant).forEach { rv.setViewVisibility(it, View.GONE) }
            rv.setContentDescription(R.id.racine, "Lecteur : rien en lecture. Touchez pour écouter votre musique.")
        } else {
            rv.setTextViewText(R.id.titre, morceau.titre)
            rv.setTextViewText(R.id.artiste, morceau.artiste.ifBlank { morceau.album })
            listOf(R.id.precedent, R.id.lecture, R.id.suivant).forEach { rv.setViewVisibility(it, View.VISIBLE) }
            rv.setImageViewResource(R.id.lecture, if (e.joue) R.drawable.ic_pause else R.drawable.ic_lecture)
            rv.setContentDescription(R.id.lecture, if (e.joue) "Pause" else "Lecture")
            rv.setOnClickPendingIntent(R.id.precedent, commande(c, Musique.PRECEDENT, 1))
            rv.setOnClickPendingIntent(R.id.lecture, commande(c, if (e.joue) Musique.PAUSE else Musique.REPRENDRE, 2))
            rv.setOnClickPendingIntent(R.id.suivant, commande(c, Musique.SUIVANT, 3))
            rv.setContentDescription(R.id.racine, "En cours : ${morceau.titre}, ${morceau.artiste}" + if (e.joue) "" else ", en pause")
        }
        ids.forEach { m.updateAppWidget(it, rv) }
    }

    private fun commande(c: Context, action: String, code: Int) =
        PendingIntent.getBroadcast(c, code, Intent(c, CommandeLecteur::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Sans pochette : un carré doux et une note. */
    private fun carre(c: Context, nuit: Boolean): Bitmap {
        val fond = if (nuit) 0xFF2E2636.toInt() else 0xFFF0E9F3.toInt()
        val encre = if (nuit) 0xFFC3A6D8.toInt() else 0xFF5B3F6E.toInt()
        val d = c.resources.displayMetrics.density
        val cote = (62 * d).toInt()
        val bmp = Bitmap.createBitmap(cote, cote, Bitmap.Config.ARGB_8888)
        val toile = android.graphics.Canvas(bmp)
        toile.drawRoundRect(android.graphics.RectF(0f, 0f, cote.toFloat(), cote.toFloat()), 12 * d, 12 * d, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = fond })
        val note = OutilsWidget.picto(c, NOTE, 28f, encre, 1.8f)
        toile.drawBitmap(note, (cote - note.width) / 2f, (cote - note.height) / 2f, null)
        return bmp
    }
}
