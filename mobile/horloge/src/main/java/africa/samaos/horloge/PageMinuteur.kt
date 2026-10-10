package africa.samaos.horloge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.appui
import africa.samaos.banco.appli.Roue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private val PRETS = listOf("Thé" to 3, "Œufs" to 9, "Riz" to 20, "Sieste" to 25, "Lessive" to 45, "Pause" to 5)

/** « 1:05:00 », « 04:59 » : le temps qui reste. */
fun duree(ms: Long): String {
    val s = (ms + 999) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** « 3 min », « 1 h 30 », « 45 s ». */
fun dureeCourte(ms: Long): String {
    val s = ms / 1000
    return when {
        s >= 3600 -> "${s / 3600} h" + if (s % 3600 >= 60) " ${"%02d".format((s % 3600) / 60)}" else ""
        s >= 60 -> "${s / 60} min" + if (s % 60 > 0) " ${s % 60} s" else ""
        else -> "$s s"
    }
}

@Composable
fun PageMinuteur() {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val fin = remember(version) { Alarmes.finMinuteur(c) }
    val pause = remember(version) { Alarmes.pauseRestante(c) }
    Column(Modifier.fillMaxSize()) {
        Entete("Minuteur", if (fin != null) "Sonne à " + Instant.ofEpochMilli(fin).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) else if (pause != null) "En pause" else null)
        // Régler, ou suivre le minuteur qui tourne : on passe de l'un à l'autre d'un fondu qui grandit.
        AnimatedContent(
            fin != null || pause != null,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(320), 0.92f)) togetherWith (fadeOut(tween(160)) + scaleOut(tween(200), 1.04f)) },
            label = "minuteur",
        ) { enCours ->
            if (enCours) EnCours(version) { version++ } else Reglage { version++ }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reglage(lance: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var h by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(10) }
    var s by remember { mutableIntStateOf(0) }
    val total = (h * 3600L + m * 60L + s) * 1000
    fun lancer(nom: String, ms: Long, retenir: Boolean) {
        Alarmes.lancerMinuteur(c, nom, ms)
        if (retenir) Alarmes.retenir(c, ms)
        lance()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(28.dp)).background(a.surface).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(60.dp).clip(RoundedCornerShape(16.dp)).background(a.voile))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Colonne((0..23).map { "%02d".format(it) }, h, "h") { h = it }
                Colonne((0..59).map { "%02d".format(it) }, m, "min") { m = it }
                Colonne((0..59).map { "%02d".format(it) }, s, "s") { s = it }
            }
        }
        BoutonRond("Lancer le minuteur", a.accent, a.surAccent, 76.dp, icone = Icones.LECTURE, actif = total > 0, modifier = Modifier.padding(top = 20.dp)) { lancer("Minuteur", total, true) }
        val recents = remember { Alarmes.recents(c) }
        if (recents.isNotEmpty()) {
            Section("Derniers")
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recents.forEach { ms ->
                    Box(Modifier.appui("Relancer ${dureeCourte(ms)}") { lancer("Minuteur", ms, true) }.clip(RoundedCornerShape(20.dp)).background(a.surface).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        BasicText(dureeCourte(ms), style = texte(15f, a.encre, FontWeight.SemiBold))
                    }
                }
            }
        }
        Section("Prêts")
        // Deux par ligne : le nom, la durée ; un toucher lance.
        PRETS.chunked(2).forEach { ligne ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ligne.forEach { (nom, min) ->
                    Column(Modifier.weight(1f).appui("$nom, $min minutes") { lancer(nom, min * 60_000L, false) }.clip(RoundedCornerShape(22.dp)).background(a.surface).padding(horizontal = 18.dp, vertical = 14.dp)) {
                        BasicText(nom, style = texte(16f, a.encre, FontWeight.SemiBold))
                        BasicText("$min min", style = CHIFFRES.copy(fontSize = 28.sp, lineHeight = 32.sp, color = a.accent))
                    }
                }
            }
        }
        Pied()
    }
}

@Composable
private fun Colonne(valeurs: List<String>, index: Int, unite: String, changer: (Int) -> Unit) {
    val a = LocalIdentite.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Roue(valeurs, index, Modifier.width(84.dp), 60.dp, 40.sp, unite, changer)
        BasicText(unite, style = texte(13f, a.encre2, FontWeight.SemiBold))
    }
}

/** Le minuteur qui tourne : un anneau qui se vide, le temps qui reste au milieu. */
@Composable
private fun EnCours(version: Int, changer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val fin = remember(version) { Alarmes.finMinuteur(c) }
    val pause = remember(version) { Alarmes.pauseRestante(c) }
    val total = remember(version) { Alarmes.dureeMinuteur(c) ?: 1L }
    val nom = remember(version) { Alarmes.nomMinuteur(c) }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(fin) { while (fin != null) withFrameMillis { maintenant = System.currentTimeMillis() } }
    val reste = fin?.let { (it - maintenant).coerceAtLeast(0) } ?: pause ?: 0L
    // En pause, le temps clignote doucement.
    val battement = rememberInfiniteTransition(label = "pause")
    val clair by battement.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "clair")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 18.dp).size(290.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Reste ${dureeCourte(reste)}" }) {
                val e = 14.dp.toPx()
                val r = size.minDimension / 2 - e
                val tl = Offset(center.x - r, center.y - r)
                drawCircle(a.surface, r, center, style = Stroke(e))
                val part = (reste.toFloat() / total).coerceIn(0f, 1f)
                drawArc(a.accent, -90f, 360f * part, false, tl, Size(r * 2, r * 2), style = Stroke(e, cap = StrokeCap.Round))
                val ang = Math.toRadians(-90.0 + 360.0 * part)
                drawCircle(a.encre, e * 0.32f, Offset(center.x + r * cos(ang).toFloat(), center.y + r * sin(ang).toFloat()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = if (fin == null) clair else 1f }) {
                BasicText(nom, style = texte(16f, a.encre2, FontWeight.SemiBold))
                TexteRoulant(duree(reste), CHIFFRES.copy(fontSize = if (reste >= 3_600_000) 54.sp else 68.sp, lineHeight = 74.sp, color = a.encre), descend = true)
                BasicText("sur ${dureeCourte(total)}", style = texte(14f, a.encre2))
            }
        }
        Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            BoutonRond("Ajouter une minute", a.surface, a.encre, 64.dp, mot = "+1 min") {
                Alarmes.lancerMinuteur(c, nom, reste + 60_000, total + 60_000)
                if (fin == null) Alarmes.pauseMinuteur(c)
                changer()
            }
            BoutonRond(if (fin != null) "Pause" else "Reprendre", a.accent, a.surAccent, 80.dp, icone = if (fin != null) Icones.PAUSE else Icones.LECTURE) {
                if (fin != null) Alarmes.pauseMinuteur(c) else Alarmes.lancerMinuteur(c, nom, reste, total)
                changer()
            }
            BoutonRond("Arrêter", CORAIL.copy(alpha = 0.18f), CORAIL, 64.dp, icone = Icones.FERMER) {
                Alarmes.arreterMinuteur(c)
                changer()
            }
        }
        Pied()
    }
}
