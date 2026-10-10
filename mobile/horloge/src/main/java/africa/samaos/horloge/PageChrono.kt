package africa.samaos.horloge

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.LocalIdentite
import kotlin.math.cos
import kotlin.math.sin

/** Le chronomètre : il continue de tourner quand on change d'onglet ou qu'on ferme l'appli. */
object Chrono {
    var depart by mutableLongStateOf(0L)
    var cumul by mutableLongStateOf(0L)
    var marche by mutableStateOf(false)
    /** Le temps total au moment de chaque tour, le plus récent d'abord. */
    val tours = mutableStateListOf<Long>()
    private var charge = false

    private fun prefs(c: Context) = c.getSharedPreferences("chrono", Context.MODE_PRIVATE)

    fun charger(c: Context) {
        if (charge) return
        val p = prefs(c)
        depart = p.getLong("depart", 0)
        cumul = p.getLong("cumul", 0)
        marche = p.getBoolean("marche", false)
        tours.clear()
        tours.addAll(p.getString("tours", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() })
        charge = true
    }

    private fun sauver(c: Context) = prefs(c).edit().putLong("depart", depart).putLong("cumul", cumul).putBoolean("marche", marche).putString("tours", tours.joinToString(",")).apply()

    fun total(maintenant: Long = System.currentTimeMillis()) = cumul + if (marche) maintenant - depart else 0

    fun demarrer(c: Context) {
        depart = System.currentTimeMillis()
        marche = true
        sauver(c)
    }

    fun arreter(c: Context) {
        cumul += System.currentTimeMillis() - depart
        marche = false
        sauver(c)
    }

    fun tour(c: Context) {
        tours.add(0, total())
        sauver(c)
    }

    fun zero(c: Context) {
        cumul = 0
        tours.clear()
        sauver(c)
    }
}

/** « 01:23,45 », ou « 1:02:03,45 » au-delà d'une heure. */
fun chrono(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    val s = (ms / 1000) % 60
    val c = (ms / 10) % 100
    return if (h > 0) "%d:%02d:%02d,%02d".format(h, m, s, c) else "%02d:%02d,%02d".format(m, s, c)
}

@Composable
fun PageChrono() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    LaunchedEffect(Unit) { Chrono.charger(c) }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Chrono.marche) { while (Chrono.marche) withFrameMillis { maintenant = System.currentTimeMillis() } }
    val total = Chrono.total(maintenant)
    val tours = Chrono.tours
    // La durée de chaque tour (le plus récent d'abord) ; le plus rapide et le plus lent, dès deux tours.
    val durees = tours.mapIndexed { i, t -> t - (tours.getOrNull(i + 1) ?: 0L) }
    val rapide = if (durees.size >= 2) durees.min() else null
    val lent = if (durees.size >= 2) durees.max() else null
    val boutonFond by animateColorAsState(if (Chrono.marche) CORAIL else a.accent, tween(260), label = "bouton")
    Column(Modifier.fillMaxSize()) {
    Entete("Chrono", if (tours.isEmpty()) null else "${tours.size} tour${if (tours.size > 1) "s" else ""}")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp)) {
        item(key = "cadran") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 10.dp).size(290.dp), contentAlignment = Alignment.Center) {
                    // La lunette : soixante traits, et un point qui fait le tour en une minute.
                    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Chronomètre ${chrono(total)}" }) {
                        val r = size.minDimension / 2 - 6.dp.toPx()
                        for (i in 0 until 60) {
                            val gros = i % 5 == 0
                            val ang = Math.toRadians(i * 6.0)
                            val r0 = if (gros) r - 14.dp.toPx() else r - 8.dp.toPx()
                            drawLine(
                                if (gros) a.encre2 else a.encre2.copy(alpha = 0.35f),
                                Offset(center.x + r0 * sin(ang).toFloat(), center.y - r0 * cos(ang).toFloat()),
                                Offset(center.x + r * sin(ang).toFloat(), center.y - r * cos(ang).toFloat()),
                                strokeWidth = if (gros) 2.5.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round,
                            )
                        }
                        val part = (total % 60_000) / 60_000f
                        val rr = r - 26.dp.toPx()
                        drawArc(a.accent.copy(alpha = 0.3f), -90f, 360f * part, false, Offset(center.x - rr, center.y - rr), Size(rr * 2, rr * 2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                        val ang = Math.toRadians(360.0 * part)
                        drawCircle(a.accent, 7.dp.toPx(), Offset(center.x + rr * sin(ang).toFloat(), center.y - rr * cos(ang).toFloat()))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val t = chrono(total)
                        Row(verticalAlignment = Alignment.Bottom) {
                            BasicText(t.substringBefore(','), style = CHIFFRES.copy(fontSize = if (total >= 3_600_000) 50.sp else 64.sp, lineHeight = 70.sp, color = a.encre))
                            BasicText("," + t.substringAfter(','), modifier = Modifier.padding(bottom = 6.dp), style = CHIFFRES.copy(fontSize = 32.sp, lineHeight = 40.sp, color = a.accent))
                        }
                        if (tours.isNotEmpty()) {
                            BasicText("Tour ${tours.size + 1} · ${chrono(total - tours.first())}", style = texte(14f, a.encre2).copy(fontFeatureSettings = "tnum"))
                        }
                    }
                }
                Row(Modifier.padding(top = 18.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    BoutonRond(
                        if (Chrono.marche) "Tour" else "Remettre à zéro", a.surface, a.encre, 70.dp,
                        mot = if (Chrono.marche || total == 0L) "Tour" else "Zéro", actif = Chrono.marche || total > 0,
                    ) { if (Chrono.marche) Chrono.tour(c) else Chrono.zero(c) }
                    BoutonRond(if (Chrono.marche) "Arrêter" else "Démarrer", boutonFond, a.surAccent, 84.dp, icone = if (Chrono.marche) Icones.PAUSE else Icones.LECTURE) {
                        if (Chrono.marche) Chrono.arreter(c) else Chrono.demarrer(c)
                    }
                }
            }
        }
        items(tours.size, key = { tours.size - it }) { i ->
            val d = durees[i]
            val couleur = when (d) {
                rapide -> a.accent
                lent -> CORAIL
                else -> a.encre
            }
            Row(
                Modifier.animateItem().fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(a.surface).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText("Tour ${tours.size - i}", style = texte(15f, a.encre, FontWeight.SemiBold))
                    AnimatedContent(
                        when (d) { rapide -> "le plus rapide"; lent -> "le plus lent"; else -> "" },
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "rang",
                    ) { r -> if (r.isNotEmpty()) BasicText(r, style = texte(12.5f, couleur)) }
                }
                BasicText(chrono(d), modifier = Modifier.padding(end = 16.dp), style = CHIFFRES.copy(fontSize = 24.sp, color = couleur))
                BasicText(chrono(tours[i]), style = CHIFFRES.copy(fontSize = 18.sp, color = a.encre2))
            }
        }
        item(key = "pied") { Pied() }
    }
    }
}
