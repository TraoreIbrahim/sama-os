package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.ceil
import kotlin.math.hypot

/** Les widgets des applis, tels qu'Android les fournit, posés sous l'heure de l'Accueil (maquette l1-widgets). */
object Widgets {
    private const val ID_HOTE = 0x5A4A
    const val DEMANDE_REGLAGE = 0x5A4B

    /** Le plus de rangées de widgets qui tiennent entre l'heure et les applis. */
    const val RANGEES_MAX = 3
    val HAUTEUR_RANGEE = 100.dp

    private var hote: HoteSama? = null

    fun hote(contexte: Context): HoteSama = hote ?: HoteSama(contexte.applicationContext, ID_HOTE).also { hote = it }

    /** Ce qu'on fait au retour de l'écran de réglage d'un widget (posé si la personne a validé). */
    var finReglage: ((Int, Boolean) -> Unit)? = null

    fun disponibles(contexte: Context): List<AppWidgetProviderInfo> =
        AppWidgetManager.getInstance(contexte).getInstalledProvidersForProfile(Process.myUserHandle())
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }

    fun info(contexte: Context, id: Int): AppWidgetProviderInfo? = AppWidgetManager.getInstance(contexte).getAppWidgetInfo(id)

    /** Combien de rangées un widget prend : ce que son appli demande, sinon d'après sa hauteur minimale. */
    fun rangees(contexte: Context, info: AppWidgetProviderInfo): Int {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) return info.targetCellHeight.coerceIn(1, RANGEES_MAX)
        val dp = info.minHeight / contexte.resources.displayMetrics.density
        return ceil((dp + 16) / HAUTEUR_RANGEE.value).toInt().coerceIn(1, RANGEES_MAX)
    }

    fun taille(info: AppWidgetProviderInfo, contexte: Context): String {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) return "${info.targetCellWidth} × ${info.targetCellHeight}"
        val d = contexte.resources.displayMetrics.density
        val l = ceil((info.minWidth / d + 16) / 90f).toInt().coerceIn(1, 4)
        return "$l × ${rangees(contexte, info)}"
    }

    /**
     * Pose un widget : Sama système a le droit de le lier sans demander ; si l'appli veut qu'on le règle
     * d'abord (ville de la météo, contact…), son écran de réglage s'ouvre, et le widget n'est posé qu'après.
     */
    fun poser(activite: Activity, info: AppWidgetProviderInfo, poser: (WidgetPose) -> Unit): Boolean {
        val h = hote(activite)
        val id = h.allocateAppWidgetId()
        val lie = try {
            AppWidgetManager.getInstance(activite).bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        } catch (_: Exception) {
            false
        }
        if (!lie) {
            h.deleteAppWidgetId(id)
            return false
        }
        val pose = WidgetPose(id, rangees(activite, info))
        if (info.configure == null) {
            poser(pose)
            return true
        }
        finReglage = { rendu, ok ->
            if (rendu == id || rendu == -1) {
                if (ok) poser(pose) else h.deleteAppWidgetId(id)
                finReglage = null
            }
        }
        return try {
            h.startAppWidgetConfigureActivityForResult(activite, id, 0, DEMANDE_REGLAGE, null)
            true
        } catch (_: Exception) {
            finReglage = null
            h.deleteAppWidgetId(id)
            false
        }
    }

    fun retirer(contexte: Context, id: Int) = hote(contexte).deleteAppWidgetId(id)
}

/** L'hôte des widgets de Sama : ses vues reconnaissent l'appui long, pour retirer un widget. */
class HoteSama(contexte: Context, id: Int) : AppWidgetHost(contexte, id) {
    var appuiLong: ((Int) -> Unit)? = null

    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        VueWidget(context) { appuiLong?.invoke(appWidgetId) }
}

/** Un widget à l'écran : ses boutons marchent comme dans l'appli ; tenu longtemps, il ouvre son menu. */
class VueWidget(contexte: Context, private val tenu: () -> Unit) : AppWidgetHostView(contexte) {
    private var declenche = false
    private var departX = 0f
    private var departY = 0f
    private val seuil = ViewConfiguration.get(contexte).scaledTouchSlop
    private val attente = Runnable {
        declenche = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        tenu()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                declenche = false
                departX = ev.x
                departY = ev.y
                postDelayed(attente, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (hypot(ev.x - departX, ev.y - departY) > seuil) removeCallbacks(attente)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(attente)
        }
        return declenche
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) removeCallbacks(attente)
        return declenche || super.onTouchEvent(event)
    }
}

/** Les widgets posés, l'un sous l'autre, sous l'heure. */
@Composable
fun ZoneWidgets(widgets: List<WidgetPose>, retirerAbsent: (Int) -> Unit, modifier: Modifier = Modifier) {
    val contexte = LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        widgets.forEach { w ->
            val info = Widgets.info(contexte, w.id)
            if (info == null) {
                // L'appli du widget a été désinstallée.
                LaunchedEffect(w.id) { retirerAbsent(w.id) }
                return@forEach
            }
            androidx.compose.runtime.key(w.id) {
                val hauteur = Widgets.HAUTEUR_RANGEE * w.rangees - 8.dp
                AndroidView(
                    factory = { ctx ->
                        Widgets.hote(ctx).createView(ctx.applicationContext, w.id, info).apply {
                            setPadding(0, 0, 0, 0)
                            // Le widget se dessine d'après la place qu'on lui donne (taille du texte d'une horloge…) :
                            // on la lui dit dès qu'elle est connue, et à chaque changement.
                            addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or_, ob ->
                                if (r - l != or_ - ol || b - t != ob - ot) direLaTaille(v as AppWidgetHostView, r - l, b - t)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(hauteur),
                )
            }
        }
    }
}

/** Donne au widget sa taille en dp, par les deux voies qu'Android connaît (anciennes options et tailles exactes). */
private fun direLaTaille(vue: AppWidgetHostView, largeurPx: Int, hauteurPx: Int) {
    if (largeurPx <= 0 || hauteurPx <= 0) return
    val d = vue.resources.displayMetrics.density
    val l = largeurPx / d
    val h = hauteurPx / d
    if (Build.VERSION.SDK_INT >= 31) {
        vue.updateAppWidgetSize(Bundle(), listOf(SizeF(l, h)))
    } else {
        @Suppress("DEPRECATION")
        vue.updateAppWidgetSize(Bundle(), l.toInt(), h.toInt(), l.toInt(), h.toInt())
    }
}
