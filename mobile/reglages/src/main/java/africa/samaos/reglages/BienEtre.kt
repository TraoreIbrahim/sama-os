package africa.samaos.reglages

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PersistableBundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** « 1 h 05 », « 42 min », « moins d'une minute ». */
fun duree(ms: Long): String {
    val min = ms / 60_000
    return when {
        min >= 60 -> "${min / 60} h ${"%02d".format(min % 60)}"
        min >= 1 -> "$min min"
        else -> "moins d'une minute"
    }
}

/**
 * Le temps passé sur le téléphone et ce qu'on décide d'en faire. Les minuteurs et la Concentration
 * passent par Android : il prévient quand une limite est atteinte (registerAppUsageLimitObserver) et
 * met l'appli en pause (setPackagesSuspended) ; elle reste visible, grisée, avec le message de Sama.
 */
object MoteurBienEtre {
    private fun prefs(c: Context) = c.getSharedPreferences("bien_etre", Context.MODE_PRIVATE)

    /** Ce qui ne se met jamais en pause : appeler, écrire, régler, rentrer à l'accueil. */
    private val ESSENTIELLES = setOf(
        "africa.samaos.accueil", "africa.samaos.reglages", "africa.samaos.telephone", "africa.samaos.messages", "africa.samaos.contacts",
        "africa.samaos.horloge", "com.android.dialer", "com.android.phone", "com.android.settings", "com.android.systemui",
        // Les services de l'opérateur (solde, forfaits) restent toujours ouverts.
        "com.android.stk",
    )

    /** Les applis d'Android que Sama remplace (l'Accueil les cache) : rien à mettre en pause. */
    private val REMPLACEES = setOf("com.android.documentsui", "org.chromium.webview_shell", "com.android.gallery3d", "com.android.music", "africa.samaos.essais")

    fun proposable(c: Context, paquet: String) = pausable(c, paquet) && paquet !in REMPLACEES

    fun pausable(c: Context, paquet: String) = paquet !in ESSENTIELLES && paquet != c.packageName

    private fun minuit(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Le temps au premier plan de chaque appli entre deux moments, d'après les événements d'Android. */
    fun parAppli(c: Context, debut: Long, fin: Long): Map<String, Long> {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val total = mutableMapOf<String, Long>()
        val ouvert = mutableMapOf<String, Long>()
        try {
            val ev = usm.queryEvents(debut, fin)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> ouvert[e.packageName] = e.timeStamp
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> ouvert.remove(e.packageName)?.let { t ->
                        total[e.packageName] = (total[e.packageName] ?: 0) + (e.timeStamp - t)
                    }
                }
            }
            ouvert.forEach { (p, t) -> total[p] = (total[p] ?: 0) + (fin - t) }
        } catch (_: Exception) {
        }
        // L'accueil n'est pas du « temps d'écran » qu'on cherche à réduire.
        return total.filterKeys { it != "africa.samaos.accueil" && it != "com.android.launcher3" && it != "com.android.systemui" }.filterValues { it >= 1000 }
    }

    fun aujourdhui(c: Context) = parAppli(c, minuit(LocalDate.now()), System.currentTimeMillis())

    fun hier(c: Context) = parAppli(c, minuit(LocalDate.now().minusDays(1)), minuit(LocalDate.now())).values.sum()

    /** Le temps passé heure par heure aujourd'hui (pour le graphique). */
    fun parHeure(c: Context): LongArray {
        val h = LongArray(24)
        val maintenant = LocalDateTime.now()
        for (i in 0..maintenant.hour) {
            val d = minuit(LocalDate.now()) + i * 3_600_000L
            h[i] = parAppli(c, d, minOf(d + 3_600_000L, System.currentTimeMillis())).values.sum()
        }
        return h
    }

    // ——— Les minuteurs d'applis ———

    fun minuteurs(c: Context): Map<String, Int> = try {
        val o = JSONObject(prefs(c).getString("minuteurs", "{}"))
        o.keys().asSequence().associateWith { o.getInt(it) }
    } catch (_: Exception) {
        emptyMap()
    }

    fun reglerMinuteur(c: Context, paquet: String, minutes: Int?) {
        val m = minuteurs(c).toMutableMap()
        if (minutes == null) m.remove(paquet) else m[paquet] = minutes
        // Un minuteur changé ou retiré repart de zéro pour cette appli.
        val etaitAtteinte = paquet in atteintes(c)
        prefs(c).edit().putString("minuteurs", JSONObject(m as Map<*, *>).toString()).putStringSet("atteintes", atteintes(c) - paquet).apply()
        observer(c, paquet, null)
        if (etaitAtteinte && paquet !in concentration(c)?.second.orEmpty()) suspendre(c, listOf(paquet), false, null)
        if (minutes != null) armer(c)
    }

    fun atteintes(c: Context): Set<String> = prefs(c).getStringSet("atteintes", emptySet()) ?: emptySet()

    /** Arme les minuteurs pour aujourd'hui, et le retour à zéro de minuit. */
    fun armer(c: Context) {
        val aujourd = aujourdhui(c)
        minuteurs(c).forEach { (p, min) ->
            if (p in atteintes(c)) return@forEach limiteAtteinte(c, p)
            val utilise = aujourd[p] ?: 0
            if (utilise >= min * 60_000L) limiteAtteinte(c, p) else observer(c, p, Duration.ofMinutes(min.toLong()) to Duration.ofMillis(utilise))
        }
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, 77, Intent(c, Minuit::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.set(AlarmManager.RTC_WAKEUP, minuit(LocalDate.now().plusDays(1)) + 5_000, pi)
    }

    /** L'observateur d'Android pour une appli (fonction système, que les Réglages ont le droit d'appeler). */
    private fun observer(c: Context, paquet: String, limite: Pair<Duration, Duration>?) {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val id = (paquet.hashCode() and 0x7fff) % 900
        try {
            if (limite == null) {
                usm.javaClass.getMethod("unregisterAppUsageLimitObserver", Int::class.javaPrimitiveType).invoke(usm, id)
                return
            }
            val pi = PendingIntent.getBroadcast(
                c, id, Intent(c, LimiteAtteinte::class.java).putExtra("paquet", paquet),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            usm.javaClass.getMethod(
                "registerAppUsageLimitObserver", Int::class.javaPrimitiveType, Array<String>::class.java,
                Duration::class.java, Duration::class.java, PendingIntent::class.java,
            ).invoke(usm, id, arrayOf(paquet), limite.first, limite.second, pi)
        } catch (_: Exception) {
        }
    }

    fun limiteAtteinte(c: Context, paquet: String) {
        prefs(c).edit().putStringSet("atteintes", atteintes(c) + paquet).apply()
        val min = minuteurs(c)[paquet] ?: return
        suspendre(c, listOf(paquet), true, "Votre minuteur de ${duree(min * 60_000L)} pour ${MoteurApplis.nom(c, paquet)} est atteint. L'appli revient demain, ou plus tôt depuis Réglages › Bien-être.")
    }

    /** À minuit, les applis mises en pause par leur minuteur reviennent. */
    fun aMinuit(c: Context) {
        val a = atteintes(c)
        prefs(c).edit().remove("atteintes").apply()
        val enConcentration = concentration(c)?.second.orEmpty()
        suspendre(c, a.filter { it !in enConcentration }, false, null)
        armer(c)
    }

    // ——— La Concentration ———

    fun pausesChoisies(c: Context): Set<String> = prefs(c).getStringSet("concentration_applis", emptySet()) ?: emptySet()

    fun choisirPause(c: Context, paquet: String, oui: Boolean) {
        val s = pausesChoisies(c).let { if (oui) it + paquet else it - paquet }
        prefs(c).edit().putStringSet("concentration_applis", s).apply()
        concentration(c)?.let { (fin, _) -> demarrerConcentration(c, fin) }
    }

    /** La Concentration en cours : jusqu'à quand, et quelles applis sont en pause. */
    fun concentration(c: Context): Pair<Long, Set<String>>? {
        val fin = prefs(c).getLong("concentration_fin", 0)
        return if (fin > System.currentTimeMillis()) fin to (prefs(c).getStringSet("concentration_en_pause", emptySet()) ?: emptySet()) else null
    }

    fun demarrerConcentration(c: Context, fin: Long) {
        val avant = concentration(c)?.second.orEmpty()
        val applis = pausesChoisies(c).filter { pausable(c, it) }.toSet()
        suspendre(c, (avant - applis).filter { it !in atteintes(c) }, false, null)
        suspendre(c, applis.toList(), true, "En pause pour la Concentration, jusqu'à ${heure(fin)}. Réglages › Bien-être pour l'arrêter.")
        prefs(c).edit().putLong("concentration_fin", fin).putStringSet("concentration_en_pause", applis).apply()
        val pi = PendingIntent.getBroadcast(c, 78, Intent(c, FinConcentration::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fin, pi)
        } catch (_: SecurityException) {
            c.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC_WAKEUP, fin, pi)
        }
    }

    fun arreterConcentration(c: Context) {
        val en = concentration(c)?.second ?: prefs(c).getStringSet("concentration_en_pause", emptySet()).orEmpty()
        prefs(c).edit().remove("concentration_fin").remove("concentration_en_pause").apply()
        suspendre(c, en.filter { it !in atteintes(c) }, false, null)
    }

    fun heure(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

    /** Mettre en pause ou rendre des applis (fonction système réservée, avec un message pour qui l'ouvre). */
    fun suspendre(c: Context, paquets: List<String>, oui: Boolean, message: String?) {
        val l = paquets.filter { pausable(c, it) }.toTypedArray()
        if (l.isEmpty()) return
        val pm = c.packageManager
        try {
            val info = if (oui && message != null) {
                val cl = Class.forName("android.content.pm.SuspendDialogInfo\$Builder")
                val b = cl.getConstructor().newInstance()
                cl.getMethod("setTitle", String::class.java).invoke(b, "Appli en pause")
                cl.getMethod("setMessage", String::class.java).invoke(b, message)
                cl.getMethod("build").invoke(b)
            } else {
                null
            }
            // Selon la version d'Android, la fonction prend ou non des « flags » à la fin (Android 14 et après).
            val m = pm.javaClass.methods.filter { it.name == "setPackagesSuspended" && it.parameterTypes.getOrNull(4)?.name == "android.content.pm.SuspendDialogInfo" }
                .maxByOrNull { it.parameterCount } ?: return
            if (m.parameterCount == 6) m.invoke(pm, l, oui, null, null, info, 0) else m.invoke(pm, l, oui, null, null, info)
        } catch (e: Exception) {
            android.util.Log.w("Sama", "Mise en pause impossible", e)
        }
    }

    fun estEnPause(c: Context, paquet: String): Boolean = try {
        c.packageManager.isPackageSuspended(paquet)
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

class LimiteAtteinte : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        i.getStringExtra("paquet")?.let { MoteurBienEtre.limiteAtteinte(c, it) }
    }
}

class Minuit : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = MoteurBienEtre.aMinuit(c)
}

class FinConcentration : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = MoteurBienEtre.arreterConcentration(c)
}

/** Au démarrage, les minuteurs se réarment (Android oublie les observateurs). */
class RedemarrageBienEtre : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        MoteurBienEtre.armer(c)
        if (MoteurBienEtre.concentration(c) == null) MoteurBienEtre.arreterConcentration(c)
    }
}

// ——— Les pages (maquettes l5-temps-ecran, l5-concentration) ———

/** Le temps d'écran du jour, heure par heure et appli par appli. */
@Composable
fun PageTempsEcran(nav: Nav) {
    val c = LocalContext.current
    val b = LocalBanco.current
    val reprise = LocalReprise.current
    var donnees by remember { mutableStateOf<Triple<Map<String, Long>, Long, LongArray>?>(null) }
    LaunchedEffect(reprise) { donnees = withContext(Dispatchers.IO) { Triple(MoteurBienEtre.aujourdhui(c), MoteurBienEtre.hier(c), MoteurBienEtre.parHeure(c)) } }
    val d = donnees
    val total = d?.first?.values?.sum() ?: 0
    val ecart = d?.let { total - it.second }
    val minuteurs = MoteurBienEtre.minuteurs(c)
    PageReglages(
        titre = if (d == null) "Bien-être" else duree(total),
        sousTitre = when {
            d == null -> null
            ecart == null || d.second == 0L -> "Aujourd'hui"
            ecart < 0 -> "Aujourd'hui · ${duree(-ecart)} de moins qu'hier"
            else -> "Aujourd'hui · ${duree(ecart)} de plus qu'hier"
        },
        retour = nav.retour,
        entete = {
            val h = d?.third ?: LongArray(24)
            val maxi = (h.maxOrNull() ?: 0).coerceAtLeast(1)
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(84.dp)) {
                val pas = size.width / 24
                h.forEachIndexed { i, v ->
                    val haut = (v.toFloat() / maxi) * size.height
                    if (v > 0) drawRoundRect(b.laterite, Offset(i * pas + pas * 0.18f, size.height - haut), Size(pas * 0.64f, haut), CornerRadius(3f))
                    else drawRect(b.encre2.copy(alpha = 0.2f), Offset(i * pas + pas * 0.18f, size.height - 2f), Size(pas * 0.64f, 2f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0 h", "6 h", "12 h", "18 h", "24 h").forEach { BasicText(it, style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, color = b.encre2)) }
            }
        },
    ) {
        if (d == null) return@PageReglages
        section("Par appli", cle = "applis") {
            val liste = d.first.entries.sortedByDescending { it.value }.take(12)
            if (liste.isEmpty()) Explication("Aucune appli ouverte aujourd'hui.")
            liste.forEach { (p, ms) ->
                val limite = minuteurs[p]
                val atteinte = p in MoteurBienEtre.atteintes(c)
                Ligne(
                    MoteurApplis.nom(c, p),
                    detail = limite?.let { "Limite ${duree(it * 60_000L)}" + if (atteinte) " · atteinte" else "" },
                    image = MoteurApplis.icone(c, p),
                    fin = Fin.Valeur(duree(ms)),
                ) { nav.aller(Page.MinuteurAppli(p)) }
            }
        }
        section(cle = "outils") {
            Ligne(
                "Minuteurs d'applis",
                detail = when (minuteurs.size) { 0 -> "Aucun"; 1 -> "1 minuteur actif"; else -> "${minuteurs.size} minuteurs actifs" },
                icone = Icones.HORLOGE,
            ) { nav.aller(Page.Minuteurs) }
            val conc = MoteurBienEtre.concentration(c)
            Ligne(
                "Concentration",
                detail = conc?.let { "En cours jusqu'à ${MoteurBienEtre.heure(it.first)}" } ?: "Mettre des applis en pause pour un moment",
                icone = Icones.NE_PAS_DERANGER,
            ) { nav.aller(Page.Concentration) }
            Ligne("Heure du coucher", detail = "Réglée dans l'Horloge", icone = Icones.NUIT) {
                try {
                    c.startActivity(Intent().setClassName("africa.samaos.horloge", "africa.samaos.horloge.Horloge").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        }
        section(cle = "note") { Explication("Le temps d'écran reste sur ce téléphone. Sama ne l'envoie nulle part.") }
    }
}

private val DUREES = listOf(null, 15, 30, 60, 90, 120, 180)

/** Le minuteur d'une appli : combien de temps par jour avant la pause. */
@Composable
fun PageMinuteurAppli(paquet: String, nav: Nav) {
    val c = LocalContext.current
    var choisi by remember { mutableStateOf(MoteurBienEtre.minuteurs(c)[paquet]) }
    var utilise by remember { mutableStateOf(0L) }
    LaunchedEffect(paquet) { utilise = withContext(Dispatchers.IO) { MoteurBienEtre.aujourdhui(c)[paquet] ?: 0 } }
    val pausable = MoteurBienEtre.pausable(c, paquet)
    PageReglages(titre = MoteurApplis.nom(c, paquet), sousTitre = "${duree(utilise)} aujourd'hui", retour = nav.retour) {
        if (!pausable) {
            section(cle = "essentielle") { Explication("Cette appli sert à appeler, écrire ou régler le téléphone : elle ne se met jamais en pause.") }
            return@PageReglages
        }
        section("Minuteur par jour", cle = "durees") {
            DUREES.forEach { m ->
                Ligne(if (m == null) "Pas de minuteur" else duree(m * 60_000L), fin = Fin.Choix(choisi == m)) {
                    choisi = m
                    MoteurBienEtre.reglerMinuteur(c, paquet, m)
                }
            }
        }
        section(cle = "explication") {
            Explication("Quand le temps est passé, l'appli se met en pause jusqu'à minuit : son icône reste, grisée. Vous pouvez enlever le minuteur à tout moment.")
        }
    }
}

@Composable
fun PageMinuteurs(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var m by remember { mutableStateOf(MoteurBienEtre.minuteurs(c)) }
    LaunchedEffect(reprise) { m = MoteurBienEtre.minuteurs(c) }
    PageReglages(titre = "Minuteurs d'applis", retour = nav.retour) {
        section(cle = "liste") {
            if (m.isEmpty()) Explication("Aucun minuteur. Choisissez une appli dans le temps d'écran pour lui en donner un.")
            m.forEach { (p, min) ->
                val atteinte = p in MoteurBienEtre.atteintes(c)
                Ligne(MoteurApplis.nom(c, p), detail = duree(min * 60_000L) + " par jour" + if (atteinte) " · atteint aujourd'hui" else "", image = MoteurApplis.icone(c, p)) {
                    nav.aller(Page.MinuteurAppli(p))
                }
            }
        }
    }
}

/** La Concentration : des applis en pause jusqu'à une heure choisie. */
@Composable
fun PageConcentration(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val conc = remember(version) { MoteurBienEtre.concentration(c) }
    val choisies = remember(version) { MoteurBienEtre.pausesChoisies(c) }
    var applis by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        applis = withContext(Dispatchers.IO) {
            val lanceur = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            c.packageManager.queryIntentActivities(lanceur, 0).map { it.activityInfo.packageName }.distinct()
                .filter { MoteurBienEtre.proposable(c, it) }
                .sortedBy { MoteurApplis.nom(c, it).lowercase() }
        }
    }
    PageReglages(
        titre = "Concentration",
        sousTitre = conc?.let { "En cours jusqu'à ${MoteurBienEtre.heure(it.first)}" } ?: "Les applis choisies se mettent en pause ; appels et messages passent.",
        retour = nav.retour,
    ) {
        section(cle = "marche") {
            if (conc != null) {
                Ligne("Arrêter la Concentration", detail = "Les applis reviennent tout de suite", icone = Icones.NE_PAS_DERANGER, fin = Fin.Inter(true)) {
                    MoteurBienEtre.arreterConcentration(c)
                    version++
                }
            } else {
                Explication("Commencer pour :")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "30 min", 60 to "1 h", 120 to "2 h").forEach { (m, nom) ->
                        Puce(nom) {
                            MoteurBienEtre.demarrerConcentration(c, System.currentTimeMillis() + m * 60_000L)
                            version++
                        }
                    }
                    val midi = LocalDate.now().atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (midi > System.currentTimeMillis() + 15 * 60_000L) Puce("Jusqu'à midi") {
                        MoteurBienEtre.demarrerConcentration(c, midi)
                        version++
                    }
                }
            }
        }
        section("Mises en pause", cle = "pauses") {
            if (applis.isEmpty()) Explication("…")
            applis.forEach { p ->
                val ici = p in choisies
                Ligne(
                    MoteurApplis.nom(c, p),
                    detail = if (ici && conc != null) "En pause" else null,
                    image = MoteurApplis.icone(c, p),
                    fin = Fin.Inter(ici),
                ) {
                    MoteurBienEtre.choisirPause(c, p, !ici)
                    version++
                }
            }
        }
    }
}
