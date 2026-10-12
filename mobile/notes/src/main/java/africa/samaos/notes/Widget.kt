package africa.samaos.notes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import africa.samaos.banco.appli.OutilsWidget
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.Executors

// Le widget de Notes pour l'Accueil : la note qu'on garde sous la main. Une note verrouillée n'y montre que son titre.

/** « Note » : la note épinglée la plus récente, sinon la dernière écrite. */
class WidgetNote : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val fin = goAsync()
        WidgetsNotes.fil.execute {
            try {
                WidgetsNotes.dessiner(context.applicationContext)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }

    /** Une autre taille : plus ou moins de lignes. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) = WidgetsNotes.annoncer(context)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetsNotes.JOUR) WidgetsNotes.annoncer(context) else super.onReceive(context, intent)
    }
}

object WidgetsNotes {
    internal val fil = Executors.newSingleThreadExecutor()
    internal const val JOUR = "africa.samaos.notes.action.WIDGET"

    /** Le carnet a changé : le widget se redessine. */
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
        val ids = m.getAppWidgetIds(ComponentName(c, WidgetNote::class.java))
        if (ids.isEmpty()) return
        val notes = Carnet.lire(c).first.filter { it.supprimee == null && !it.vide() }
        val n = notes.filter { it.epinglee }.maxByOrNull { it.modifiee } ?: notes.maxByOrNull { it.modifiee }
        // Autant de lignes que la hauteur en tient (l'en-tête et le titre prennent 74 dp, une ligne 21,5).
        ids.forEach { id ->
            val h = OutilsWidget.taille(m, id)?.height ?: 192f
            m.updateAppWidget(id, vue(c, n, ((h - 74f) / 21.5f).toInt().coerceIn(1, 8)))
        }
        // À minuit, « 14:30 » devient « Hier ».
        val minuit = LocalDate.now().plusDays(1).atTime(LocalTime.of(0, 0, 30)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        OutilsWidget.reveil(c, minuit, PendingIntent.getBroadcast(c, 2, Intent(c, WidgetNote::class.java).setAction(JOUR), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun vue(c: Context, n: Note?, maxLignes: Int): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_note)
        rv.removeAllViews(R.id.lignes)
        if (n == null) {
            rv.setTextViewText(R.id.rubrique, "Notes")
            rv.setTextViewText(R.id.date, "")
            rv.setTextViewText(R.id.titre, "Aucune note")
            rv.addView(R.id.lignes, ligne(c, "Touchez pour en écrire une.", attenue = true))
            rv.setOnClickPendingIntent(R.id.racine, PendingIntent.getActivity(c, 0, Intent(c, Notes::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
            rv.setContentDescription(R.id.racine, "Notes : aucune note. Touchez pour en écrire une.")
        } else {
            rv.setTextViewText(R.id.rubrique, if (n.epinglee) "Épinglée" else "Dernière note")
            rv.setTextViewText(R.id.date, Dates.courte(n.modifiee))
            // Sans titre, la première ligne en tient lieu.
            val lignes = if (n.verrouillee) emptyList() else n.blocs.filter { it.texte.isNotBlank() }
            val titre = n.titre.ifBlank { if (n.verrouillee) "Note verrouillée" else lignes.firstOrNull()?.texte?.lineSequence()?.firstOrNull().orEmpty() }
            rv.setTextViewText(R.id.titre, titre)
            if (n.verrouillee) {
                rv.addView(R.id.lignes, ligne(c, "Verrouillée · s'ouvre avec le code du téléphone", attenue = true))
            } else {
                var numero = 0
                lignes.drop(if (n.titre.isBlank()) 1 else 0).flatMap { b -> b.texte.lines().filter { it.isNotBlank() }.map { b to it } }.take(maxLignes).forEach { (b, t) ->
                    if (b.genre == Genre.NUMERO) numero++ else numero = 0
                    val texte = when (b.genre) {
                        Genre.CASE -> (if (b.coche) "☑  " else "☐  ") + t
                        Genre.PUCE -> "•  $t"
                        Genre.NUMERO -> "$numero.  $t"
                        else -> t
                    }
                    rv.addView(R.id.lignes, ligne(c, texte, attenue = b.genre == Genre.CASE && b.coche))
                }
            }
            rv.setOnClickPendingIntent(
                R.id.racine,
                PendingIntent.getActivity(c, 1, Intent(c, Notes::class.java).putExtra("id", n.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            rv.setContentDescription(R.id.racine, "Note : $titre" + if (n.verrouillee) ", verrouillée." else ". " + n.apercu())
        }
        return rv
    }

    private fun ligne(c: Context, texte: String, attenue: Boolean) =
        RemoteViews(c.packageName, if (attenue) R.layout.widget_note_ligne_attenue else R.layout.widget_note_ligne).apply { setTextViewText(R.id.ligne, texte) }
}
