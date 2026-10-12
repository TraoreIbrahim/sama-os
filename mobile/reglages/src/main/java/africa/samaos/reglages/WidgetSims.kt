package africa.samaos.reglages

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import africa.samaos.soldes.Releve
import africa.samaos.soldes.Soldes
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// Le widget « Mes SIM » pour l'Accueil : le crédit, la data et la fin du forfait de chaque SIM, lus dans les SMS
// des opérateurs (comme dans le Pouls). Le solde mobile money n'y paraît jamais : il ne se lit qu'après le code.

/** « Mes SIM » : une colonne par SIM. */
class WidgetSims : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val fin = goAsync()
        WidgetsSims.fil.execute {
            try {
                WidgetsSims.dessiner(context.applicationContext)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }
}

/**
 * Messages vient de recevoir un SMS d'opérateur (crédit, data, forfait) : le widget se redessine. Seules les applis
 * du système (droit d'écrire les réglages sécurisés) peuvent l'annoncer.
 */
class SoldesChanges : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val fin = goAsync()
        WidgetsSims.fil.execute {
            try {
                WidgetsSims.dessiner(c.applicationContext)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }
}

object WidgetsSims {
    internal val fil = Executors.newSingleThreadExecutor()

    /** Un solde a été lu ici (réponse USSD, page des soldes) : le widget se redessine. */
    fun annoncer(c: Context) {
        val a = c.applicationContext
        fil.execute {
            try {
                dessiner(a)
            } catch (_: Exception) {
            }
        }
    }

    private fun couleurOperateur(nom: String): Int = when {
        nom.contains("orange", true) -> 0xFFE8752A.toInt()
        nom.contains("mtn", true) -> 0xFFE2B42B.toInt()
        nom.contains("moov", true) -> 0xFF2F6FB5.toInt()
        else -> 0xFF8F8476.toInt()
    }

    internal fun dessiner(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        val ids = m.getAppWidgetIds(ComponentName(c, WidgetSims::class.java))
        if (ids.isEmpty()) return
        val nuit = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val releves = Soldes.releves(c)
        val sims = MoteurReseau.sims(c)
        val rv = RemoteViews(c.packageName, R.layout.widget_sims)
        rv.removeAllViews(R.id.racine)
        rv.setOnClickPendingIntent(
            R.id.racine,
            PendingIntent.getActivity(c, 0, Intent("africa.samaos.action.SOLDES").setPackage(c.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE),
        )
        // Les SIM du téléphone qui ont un relevé ; sinon les relevés seuls (« SIM 1 »).
        val colonnes: List<Triple<Int, String, Releve>> = sims.mapNotNull { s -> releves[s.subscriptionId]?.let { Triple(s.simSlotIndex + 1, MoteurReseau.nomSim(s).ifBlank { "SIM" }, it) } }
            .ifEmpty { releves.values.mapIndexed { i, r -> Triple(i + 1, "SIM ${i + 1}", r) } }
        if (colonnes.isEmpty()) {
            rv.addView(R.id.racine, RemoteViews(c.packageName, R.layout.widget_sims_vide))
            rv.setContentDescription(R.id.racine, "Mes SIM : vos soldes viendront des SMS de vos opérateurs.")
        }
        val dits = mutableListOf<String>()
        colonnes.take(2).forEach { (place, operateur, r) ->
            val e = RemoteViews(c.packageName, R.layout.widget_sim)
            e.setImageViewBitmap(R.id.badge, badge(c, place, couleurOperateur(operateur)))
            e.setTextViewText(R.id.operateur, operateur)
            e.setTextViewText(R.id.credit, r.credit?.let { Soldes.texteMontant(it) } ?: "—")
            val reste = r.dataReste
            val data = when {
                reste == null -> Soldes.texteLu(r.quand)
                reste < 1 -> "Plus de data"
                else -> Soldes.texteVolume(reste) + " de data" + (r.expire?.let { " · " + Soldes.texteFin(it).removePrefix("expire ") } ?: "")
            }
            e.setTextViewText(R.id.data, data)
            val part = if (reste != null) r.dataTotal?.takeIf { it > 0 }?.let { (reste / it).toFloat().coerceIn(0f, 1f) } else null
            if (part != null) {
                e.setViewVisibility(R.id.barre, View.VISIBLE)
                e.setImageViewBitmap(R.id.barre, barre(c, part, nuit))
            } else {
                e.setViewVisibility(R.id.barre, View.GONE)
            }
            dits += "$operateur : crédit ${r.credit?.let { Soldes.texteMontant(it) } ?: "inconnu"}, $data"
            rv.addView(R.id.racine, e)
        }
        if (dits.isNotEmpty()) rv.setContentDescription(R.id.racine, "Mes SIM. " + dits.joinToString(". "))
        ids.forEach { m.updateAppWidget(it, rv) }
    }

    /** Le numéro de la SIM sur la couleur de l'opérateur, comme dans le Pouls. */
    private fun badge(c: Context, place: Int, couleur: Int): Bitmap {
        val d = c.resources.displayMetrics.density
        val p = (18 * d).roundToInt()
        val bmp = Bitmap.createBitmap(p, p, Bitmap.Config.ARGB_8888)
        val toile = Canvas(bmp)
        toile.drawRoundRect(RectF(0f, 0f, p.toFloat(), p.toFloat()), 5 * d, 5 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = couleur })
        val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 11 * d
            typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            textAlign = Paint.Align.CENTER
        }
        toile.drawText("$place", p / 2f, p / 2f - (texte.descent() + texte.ascent()) / 2f, texte)
        return bmp
    }

    /** La data qui reste, sur le forfait : la latérite sur la piste. */
    private fun barre(c: Context, part: Float, nuit: Boolean): Bitmap {
        val d = c.resources.displayMetrics.density
        val l = (140 * d).roundToInt()
        val h = (4 * d).roundToInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(l, h, Bitmap.Config.ARGB_8888)
        val toile = Canvas(bmp)
        val r = h / 2f
        toile.drawRoundRect(RectF(0f, 0f, l.toFloat(), h.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (nuit) 0xFF3A3129.toInt() else 0xFFEFE6DA.toInt() })
        if (part > 0f) toile.drawRoundRect(RectF(0f, 0f, (l * part).coerceAtLeast(h.toFloat()), h.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (nuit) 0xFFF0A080.toInt() else 0xFFB5532F.toInt() })
        return bmp
    }
}
