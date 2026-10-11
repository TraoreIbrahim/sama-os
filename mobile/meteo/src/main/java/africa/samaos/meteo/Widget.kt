package africa.samaos.meteo

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.PathParser
import africa.samaos.banco.Icones
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// Les widgets de la Météo pour l'Accueil (maquette l1-widgets), en trois tailles comme sur l'iPhone, et le résumé
// que lit la tuile Météo du mode simplifié. Tout se dessine d'après les prévisions gardées : dix jours heure par
// heure, de quoi suivre l'heure qu'il est sans data ; des prévisions fraîches ne sont demandées qu'au-delà de
// 3 heures (6 heures sur les données mobiles), quand il y a du réseau.

/** Ce qu'un widget montre : en bref (une rangée), heure par heure (deux), les jours qui viennent (trois). */
internal enum class Sorte { BREF, HEURES, SEMAINE }

/** « Heure par heure » : le widget proposé d'abord. */
open class WidgetMeteo : AppWidgetProvider() {
    internal open val sorte = Sorte.HEURES

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = plusTard(context, appWidgetIds)

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        plusTard(context, intArrayOf(appWidgetId))

    override fun onReceive(context: Context, intent: Intent) {
        // Une nouvelle heure : « Maint. » passe à l'heure suivante dans tous les widgets.
        if (intent.action != WidgetsMeteo.HEURE) return super.onReceive(context, intent)
        val fin = goAsync()
        WidgetsMeteo.fil.execute {
            try {
                WidgetsMeteo.toutDessiner(context.applicationContext, planifier = true)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }

    private fun plusTard(c: Context, ids: IntArray) {
        val fin = goAsync()
        WidgetsMeteo.fil.execute {
            try {
                WidgetsMeteo.dessiner(c.applicationContext, ids, sorte)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }
}

class WidgetMeteoBref : WidgetMeteo() {
    override val sorte = Sorte.BREF
}

class WidgetMeteoSemaine : WidgetMeteo() {
    override val sorte = Sorte.SEMAINE
}

/** Le temps qu'il fait à l'heure qu'il est dans la ville : celui de la dernière mesure, ou celui prévu pour cette heure. */
internal class Maintenant(val ici: LocalDateTime, val temp: Double, val code: Int, val jour: Boolean)

internal fun Previsions.maintenant(): Maintenant? {
    val zone = try {
        ZoneId.of(fuseau)
    } catch (_: Exception) {
        ZoneId.systemDefault()
    }
    val ici = LocalDateTime.now(zone)
    if (Duration.between(actuel.heure, ici).abs() < Duration.ofMinutes(60)) return Maintenant(ici, actuel.temp, actuel.code, actuel.jour)
    val h = heures.firstOrNull { it.heure == ici.truncatedTo(ChronoUnit.HOURS) } ?: return null
    return Maintenant(ici, h.temp, h.code, h.jour)
}

/** « Averses vers 17 h » : la pluie qui vient dans les 12 heures, sinon le temps qu'il fait. */
internal fun phraseCourte(p: Previsions, m: Maintenant): String {
    if (Ciel.pluvieux(m.code)) return Ciel.description(m.code, m.jour)
    val prochaine = p.heures.filter { it.heure.isAfter(m.ici) }.take(12).firstOrNull { Ciel.pluvieux(it.code) && (it.pluie ?: 0) >= 50 }
    return prochaine?.let { "${Ciel.pluie(it.code)} vers ${it.heure.hour} h" } ?: Ciel.description(m.code, m.jour)
}

/** La ville suivie (celle qu'on regarde dans l'appli) et ses dernières prévisions. */
internal class EtatMeteo(val ville: Ville?, val prev: Previsions?)

object WidgetsMeteo {
    internal val fil = Executors.newSingleThreadExecutor()
    private const val TACHE = 0x4D45
    internal const val HEURE = "africa.samaos.meteo.action.HEURE"
    private val SORTES = listOf(WidgetMeteoBref::class.java to Sorte.BREF, WidgetMeteo::class.java to Sorte.HEURES, WidgetMeteoSemaine::class.java to Sorte.SEMAINE)

    /** Au-delà, le widget demande des prévisions fraîches : 3 heures en Wi-Fi, 6 heures sur les données mobiles. */
    fun seuil(c: Context): Long {
        val payant = try {
            c.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        } catch (_: Exception) {
            true
        }
        return if (payant) 6 * 3600_000L else 3 * 3600_000L
    }

    internal fun etat(c: Context): EtatMeteo {
        val villes = MoteurMeteo.villes(c)
        val v = villes.getOrNull(MoteurMeteo.active(c)) ?: villes.firstOrNull()
        return EtatMeteo(v, v?.let { MoteurMeteo.gardees(c, it) })
    }

    /** La ville ou les prévisions ont changé : tous les widgets posés se redessinent, la tuile du mode simplifié aussi. */
    fun annoncer(c: Context) {
        val a = c.applicationContext
        try {
            a.contentResolver.notifyChange(ResumeMeteo.ADRESSE, null)
        } catch (_: Exception) {
        }
        fil.execute {
            try {
                toutDessiner(a, planifier = false)
            } catch (_: Exception) {
            }
        }
    }

    internal fun toutDessiner(c: Context, planifier: Boolean) {
        val m = AppWidgetManager.getInstance(c)
        SORTES.forEach { (classe, sorte) ->
            val ids = m.getAppWidgetIds(ComponentName(c, classe))
            if (ids.isNotEmpty()) dessiner(c, ids, sorte, planifier)
        }
    }

    internal fun dessiner(c: Context, ids: IntArray, sorte: Sorte, planifier: Boolean = true) {
        val m = AppWidgetManager.getInstance(c)
        val e = etat(c)
        ids.forEach { id ->
            val options = m.getAppWidgetOptions(id)
            val vue = if (Build.VERSION.SDK_INT >= 31) {
                // Chaque taille annoncée par l'hôte a son dessin, à sa largeur (le nombre d'heures, la longueur des barres).
                @Suppress("DEPRECATION")
                val tailles = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
                if (tailles.size > 1) RemoteViews(tailles.associateWith { vue(c, sorte, e, it.width) }) else vue(c, sorte, e, tailles.firstOrNull()?.width ?: largeur(options))
            } else {
                vue(c, sorte, e, largeur(options))
            }
            m.updateAppWidget(id, vue)
        }
        if (planifier) planifierSiBesoin(c, e)
        prochaineHeure(c)
    }

    /**
     * Au début de chaque heure, les widgets se redessinent. Le réveil ne réveille pas le téléphone : écran éteint,
     * il attend qu'on le rallume.
     */
    private fun prochaineHeure(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(c, 1, Intent(c, WidgetMeteo::class.java).setAction(HEURE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val maintenant = System.currentTimeMillis()
        val suivante = maintenant - maintenant % 3600_000L + 3600_000L + 5_000L
        am.setWindow(AlarmManager.RTC, suivante, 60_000L, pi)
    }

    private fun largeur(options: Bundle): Float = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 }?.toFloat() ?: 340f

    /** Des prévisions trop vieilles (ou aucune) : une relève, dès qu'il y a du réseau. */
    internal fun planifierSiBesoin(c: Context, e: EtatMeteo) {
        if (e.ville == null) return
        val p = e.prev
        if (p != null && System.currentTimeMillis() - p.recues < seuil(c)) return
        val js = c.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(TACHE) != null) return
        val b = JobInfo.Builder(TACHE, ComponentName(c, ReleveMeteo::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(15 * 60_000L, JobInfo.BACKOFF_POLICY_LINEAR)
        if (Build.VERSION.SDK_INT >= 28) b.setEstimatedNetworkBytes(30_000, 1_000)
        try {
            js.schedule(b.build())
        } catch (_: Exception) {
        }
    }

    // ——— Le dessin ———

    private class Couleurs(nuit: Boolean) {
        val encre = if (nuit) 0xFFF3EBDF.toInt() else 0xFF3A1D0F.toInt()
        val encre2 = if (nuit) 0xFFB9AE9E.toInt() else 0xFF7A5340.toInt()
        val bleu = if (nuit) 0xFF9DB4E8.toInt() else 0xFF3D5A99.toInt()
        val or = 0xFFE2A62B.toInt()
        val laterite = if (nuit) 0xFFF0A080.toInt() else 0xFFB5532F.toInt()
        val trait = if (nuit) 0x33FFFFFF else 0x1F2A2018
        val piste = if (nuit) 0x26FFFFFF else 0x1A3A1D0F
    }

    private fun vue(c: Context, sorte: Sorte, e: EtatMeteo, largeurDp: Float): RemoteViews {
        val p = e.prev
        val m = p?.maintenant()
        val nuit = m?.let { !it.jour } ?: false
        val k = Couleurs(nuit)
        val rv = RemoteViews(c.packageName, if (sorte == Sorte.BREF) R.layout.widget_meteo_bref else R.layout.widget_meteo)
        rv.setInt(R.id.racine, "setBackgroundResource", if (nuit) R.drawable.widget_ciel_nuit else R.drawable.widget_ciel_jour)
        rv.setOnClickPendingIntent(
            R.id.racine,
            PendingIntent.getActivity(c, 0, Intent(c, Meteo::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        listOf(R.id.ville, R.id.temperature, R.id.phrase).forEach { rv.setTextColor(it, k.encre) }
        rv.setTextColor(R.id.bornes, k.encre2)
        val v = e.ville
        rv.setImageViewBitmap(R.id.epingle, icone(c, if (v?.position == true) Icones.BOUSSOLE else EPINGLE, 14f, k.encre, 2f))
        rv.setTextViewText(R.id.ville, v?.nom ?: "Météo")
        if (v == null || p == null || m == null) {
            rv.setTextViewText(R.id.temperature, "—")
            rv.setTextViewText(
                R.id.phrase,
                when {
                    v == null -> "Touchez pour choisir votre ville"
                    p == null -> "Les prévisions viendront avec le réseau"
                    else -> "Prévisions trop anciennes"
                },
            )
            rv.setTextViewText(R.id.bornes, if (v != null && p != null) "Touchez pour actualiser" else "")
            rv.setImageViewBitmap(R.id.picto, icone(c, picto(2, true), 30f, k.encre))
            if (sorte != Sorte.BREF) {
                rv.setViewVisibility(R.id.heures, View.GONE)
                rv.setViewVisibility(R.id.trait, View.GONE)
                rv.setViewVisibility(R.id.jours, View.GONE)
            }
            rv.setContentDescription(R.id.racine, "Météo : " + (if (v == null) "touchez pour choisir votre ville" else "pas encore de prévisions pour ${v.nom}"))
            return rv
        }
        val auj = p.jours.firstOrNull { it.date == m.ici.toLocalDate() }
        // Une alerte de Sama passe avant la phrase, en latérite.
        val alerte = Alertes.pour(p).firstOrNull()
        val phrase = alerte?.titre ?: phraseCourte(p, m)
        val age = System.currentTimeMillis() - p.recues
        val bornes = when {
            age > 12 * 3600_000L -> "Prévisions d'${depuis(age).removePrefix("il y a ")}"
            auj != null -> "Max. ${degres(auj.max)} · Min. ${degres(auj.min)}"
            else -> ""
        }
        rv.setTextViewText(R.id.temperature, degres(m.temp))
        rv.setTextViewText(R.id.phrase, phrase)
        if (alerte != null) rv.setTextColor(R.id.phrase, k.laterite)
        rv.setTextViewText(R.id.bornes, bornes)
        rv.setImageViewBitmap(R.id.picto, icone(c, picto(m.code, m.jour), if (sorte == Sorte.BREF) 36f else 28f, k.encre, 1.6f))
        rv.setContentDescription(
            R.id.racine,
            listOfNotNull("Météo à ${v.nom} : ${degres(m.temp)}, ${phrase.lowercase()}", auj?.let { "${degres(it.max)} au plus, ${degres(it.min)} au moins" }).joinToString(". ") + ".",
        )
        if (sorte == Sorte.BREF) return rv

        // Les prochaines heures, avec le lever et le coucher du soleil à leur place.
        val combien = if (largeurDp < 300) 5 else 6
        rv.removeAllViews(R.id.heures)
        rv.setViewVisibility(R.id.heures, View.VISIBLE)
        casesHeures(p, m).take(combien).forEach { cas -> rv.addView(R.id.heures, caseHeure(c, cas, k)) }

        if (sorte == Sorte.SEMAINE) {
            rv.setViewVisibility(R.id.trait, View.VISIBLE)
            rv.setInt(R.id.trait, "setBackgroundColor", k.trait)
            rv.setViewVisibility(R.id.jours, View.VISIBLE)
            rv.removeAllViews(R.id.jours)
            val jours = p.jours.filter { it.date.isAfter(m.ici.toLocalDate()) }.take(4)
            if (jours.isNotEmpty()) {
                val bas = jours.minOf { it.min }
                val haut = jours.maxOf { it.max }.coerceAtLeast(bas + 1)
                // La barre : la largeur du widget moins le jour, le temps, la pluie, les deux températures et les marges.
                val barre = (largeurDp - 36 - 46 - 20 - 45 - 32 - 32 - 20).coerceIn(40f, 260f)
                jours.forEachIndexed { i, j -> rv.addView(R.id.jours, ligneJour(c, j, i == 0, bas, haut, barre, k)) }
            }
        } else {
            rv.setViewVisibility(R.id.trait, View.GONE)
            rv.setViewVisibility(R.id.jours, View.GONE)
        }
        return rv
    }

    private fun casesHeures(p: Previsions, m: Maintenant): List<Case> {
        val depart = m.ici.truncatedTo(ChronoUnit.HOURS)
        val heures = p.heures.filter { !it.heure.isBefore(depart) }.take(8)
        if (heures.isEmpty()) return emptyList()
        val soleils = p.jours.flatMap { listOfNotNull(it.lever?.let { l -> Case.Soleil(l, true) }, it.coucher?.let { c -> Case.Soleil(c, false) }) }
            .filter { it.quand.isAfter(m.ici) && it.quand.isBefore(heures.last().heure) }
        val cases = mutableListOf<Case>()
        heures.forEachIndexed { i, h ->
            // L'heure en cours prend le temps qu'il fait (la dernière mesure, si elle est récente).
            cases += if (i == 0) Case.H(Heure(h.heure, m.temp, h.pluie, m.code, m.jour, h.mm, h.vent, h.uv, h.humidite), true) else Case.H(h, false)
            soleils.filter { it.quand.isAfter(h.heure) && !it.quand.isAfter(h.heure.plusHours(1)) }.forEach { cases += it }
        }
        return cases
    }

    private fun caseHeure(c: Context, cas: Case, k: Couleurs): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_meteo_heure)
        rv.setTextColor(R.id.etiquette, k.encre)
        rv.setTextColor(R.id.valeur, k.encre)
        rv.setTextColor(R.id.pluie, k.bleu)
        when (cas) {
            is Case.H -> {
                rv.setTextViewText(R.id.etiquette, if (cas.maintenant) "Maint." else "${cas.h.heure.hour} h")
                rv.setImageViewBitmap(R.id.icone, icone(c, picto(cas.h.code, cas.h.jour), 22f, k.encre))
                rv.setTextViewText(R.id.valeur, degres(cas.h.temp))
                rv.setTextViewText(R.id.pluie, cas.h.pluie?.takeIf { it >= 30 }?.let { "$it %" } ?: " ")
            }
            is Case.Soleil -> {
                rv.setTextViewText(R.id.etiquette, heureCourte(cas.quand))
                rv.setTextColor(R.id.etiquette, k.encre2)
                rv.setImageViewBitmap(R.id.icone, icone(c, if (cas.lever) LEVER else COUCHER, 22f, k.or, 2f))
                rv.setTextViewText(R.id.valeur, if (cas.lever) "Lever" else "Coucher")
                rv.setTextColor(R.id.valeur, k.encre2)
                rv.setTextViewTextSize(R.id.valeur, android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                rv.setTextViewText(R.id.pluie, " ")
            }
        }
        return rv
    }

    private fun ligneJour(c: Context, j: Jour, demain: Boolean, bas: Double, haut: Double, barreDp: Float, k: Couleurs): RemoteViews {
        val rv = RemoteViews(c.packageName, R.layout.widget_meteo_jour)
        rv.setTextViewText(R.id.jour, if (demain) "Dem." else j.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix(".").replaceFirstChar { it.uppercase() })
        rv.setTextColor(R.id.jour, k.encre)
        rv.setImageViewBitmap(R.id.icone, icone(c, picto(j.code, true), 20f, k.encre))
        rv.setTextViewText(R.id.pluie, j.pluie?.takeIf { it >= 30 }?.let { "$it %" } ?: "")
        rv.setTextColor(R.id.pluie, k.bleu)
        rv.setTextViewText(R.id.min, degres(j.min))
        rv.setTextColor(R.id.min, k.encre2)
        rv.setTextViewText(R.id.max, degres(j.max))
        rv.setTextColor(R.id.max, k.encre)
        rv.setImageViewBitmap(R.id.barre, barre(c, barreDp, ((j.min - bas) / (haut - bas)).toFloat(), ((j.max - bas) / (haut - bas)).toFloat(), k))
        return rv
    }

    /** Un picto de Banco (tracé SVG sur 24 unités) dessiné au trait, à la taille voulue. */
    private fun icone(c: Context, chemin: String, dp: Float, couleur: Int, epaisseur: Float = 1.75f): Bitmap {
        val px = (dp * c.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val toile = Canvas(bmp)
        toile.scale(px / 24f, px / 24f)
        val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = epaisseur
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = couleur
        }
        toile.drawPath(PathParser.createPathFromPathData(chemin), trait)
        return bmp
    }

    /** L'écart du plus frais au plus chaud d'un jour, sur l'échelle des jours montrés : de l'or à la latérite. */
    private fun barre(c: Context, dp: Float, debut: Float, fin: Float, k: Couleurs): Bitmap {
        val d = c.resources.displayMetrics.density
        val l = (dp * d).roundToInt().coerceAtLeast(8)
        val h = (6 * d).roundToInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(l, h, Bitmap.Config.ARGB_8888)
        val toile = Canvas(bmp)
        val r = h / 2f
        toile.drawRoundRect(RectF(0f, 0f, l.toFloat(), h.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = k.piste })
        val x0 = (debut.coerceIn(0f, 1f) * l)
        val x1 = (fin.coerceIn(debut.coerceIn(0f, 1f), 1f) * l).coerceAtLeast(x0 + h)
        val plein = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, 0f, x1, 0f, k.or, k.laterite, Shader.TileMode.CLAMP) }
        toile.drawRoundRect(RectF(x0, 0f, x1.coerceAtMost(l.toFloat()), h.toFloat()), r, r, plein)
        return bmp
    }
}

/** Relève les prévisions de la ville suivie pour les widgets, dès qu'il y a du réseau. */
class ReleveMeteo : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val c = applicationContext
        WidgetsMeteo.fil.execute {
            var echec = false
            try {
                val e = WidgetsMeteo.etat(c)
                val v = e.ville
                val p = e.prev
                // actualiser() garde les prévisions et redessine les widgets.
                if (v != null && (p == null || System.currentTimeMillis() - p.recues >= WidgetsMeteo.seuil(c))) MoteurMeteo.actualiser(c, v)
            } catch (_: Exception) {
                echec = true
            }
            jobFinished(params, echec)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}

/**
 * Le temps qu'il fait dans la ville suivie, pour l'Accueil (la tuile Météo du mode simplifié) : la ville, la
 * température, le picto, la phrase. Réservé aux applis du système et à celles signées comme la Météo.
 */
class ResumeMeteo : ContentProvider() {
    companion object {
        val ADRESSE: Uri = Uri.parse("content://africa.samaos.meteo.maintenant")
        private const val SHELL = 2000
        private const val ROOT = 0
    }

    override fun onCreate() = true

    private fun autorise(c: Context): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid() || uid == SHELL || uid == ROOT) return true
        val pm = c.packageManager
        if (pm.checkSignatures(uid, Process.myUid()) == PackageManager.SIGNATURE_MATCH) return true
        return pm.getPackagesForUid(uid).orEmpty().any {
            try {
                pm.getApplicationInfo(it, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val c = context ?: return null
        if (!autorise(c)) throw SecurityException("Réservé aux applis de Sama")
        val jeton = Binder.clearCallingIdentity()
        try {
            val r = MatrixCursor(arrayOf("ville", "temperature", "picto", "phrase", "jour", "recues"))
            val e = WidgetsMeteo.etat(c)
            val v = e.ville
            val p = e.prev
            val m = p?.maintenant()
            if (v != null && p != null && m != null) {
                r.addRow(arrayOf<Any>(v.nom, degres(m.temp), picto(m.code, m.jour), Alertes.pour(p).firstOrNull()?.titre ?: phraseCourte(p, m), if (m.jour) 1 else 0, p.recues))
            }
            WidgetsMeteo.planifierSiBesoin(c, e)
            return r
        } finally {
            Binder.restoreCallingIdentity(jeton)
        }
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.africa.samaos.meteo"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
