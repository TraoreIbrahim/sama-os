package africa.samaos.horloge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.appui
import africa.samaos.banco.appli.RoueHeure
import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private fun hm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun qualite(duree: Int) = when {
    duree < 360 -> "Une nuit courte"
    duree < 420 -> "Un peu juste"
    duree <= 540 -> "Une bonne nuit"
    else -> "Une longue nuit"
}

@Composable
fun PageCoucher(version: Int) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var coucher by remember { mutableIntStateOf(Alarmes.coucher(c)) }
    var lever by remember { mutableIntStateOf(Alarmes.lever(c)) }
    var relire by remember { mutableIntStateOf(0) }
    val alarmeLever = remember(version, relire) { Alarmes.liste(c).firstOrNull { it.nom == "Lever" } }
    var feuille by remember { mutableStateOf<Boolean?>(null) }
    val duree = (lever - coucher + 1440) % 1440
    fun garder() {
        Alarmes.reglerCoucher(c, coucher)
        Alarmes.reglerLever(c, lever)
        alarmeLever?.let { Alarmes.garder(c, it.copy(heure = lever / 60, minute = lever % 60)) }
        relire++
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        Entete("Coucher", "Glissez la lune et le soleil")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            CadranNuit(coucher, lever, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), { estCoucher, m -> if (estCoucher) coucher = m else lever = m }) { garder() }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CarteHeure("Coucher", Icones.NUIT, LUNE, coucher, Modifier.weight(1f)) { feuille = true }
                CarteHeure("Lever", Icones.SOLEIL, AUBE, lever, Modifier.weight(1f)) { feuille = false }
            }
            // L'alarme au lever : elle suit l'heure du lever, les jours qu'on choisit.
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(a.surface)) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Switch) {
                        if (alarmeLever == null) {
                            Alarmes.garder(c, Alarme(Alarmes.nouvelId(c), lever / 60, lever % 60, "Lever", DayOfWeek.entries.toSet(), true))
                        } else {
                            Alarmes.garder(c, alarmeLever.copy(active = !alarmeLever.active))
                        }
                        relire++
                        Etat.version++
                        if (alarmeLever?.active != true) Avis.dire("Le réveil sonnera à ${hm(lever)}")
                    }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconeTrait(Icones.CLOCHE, 22.dp, AUBE)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        BasicText("Alarme au lever", style = texte(16f, a.encre, FontWeight.Medium))
                        BasicText(if (alarmeLever?.active == true) "${repetition(alarmeLever.jours)} à ${hm(lever)}" else "Éteinte", style = texte(13.5f, a.encre2))
                    }
                    InterAppli(alarmeLever?.active == true)
                }
                AnimatedVisibility(alarmeLever?.active == true, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        ChoixJours(alarmeLever?.jours.orEmpty(), AUBE) { j ->
                            alarmeLever?.let { Alarmes.garder(c, it.copy(jours = j)) }
                            relire++
                            Etat.version++
                        }
                    }
                }
            }
            BasicText(
                "On conseille 7 à 9 heures de sommeil aux adultes. Pour passer en Nuit et en silence au coucher, Sama suit l'heure de la Nuit des Réglages ; vos favoris peuvent toujours appeler.",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp),
                style = texte(13.5f, a.encre2),
            )
            Pied()
        }
        }
        feuille?.let { estCoucher ->
            FeuilleAppli(fermer = { feuille = null; garder() }, titre = if (estCoucher) "Heure du coucher" else "Heure du lever") {
                val fermer = LocalFermerFeuille.current
                val v = if (estCoucher) coucher else lever
                RoueHeure(v / 60, v % 60, Modifier.padding(horizontal = 20.dp), pas = 5, bande = (if (estCoucher) LUNE else AUBE).copy(alpha = 0.18f)) { h, m ->
                    if (estCoucher) coucher = h * 60 + m else lever = h * 60 + m
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    BoutonTexteAppli("Valider", onClick = fermer)
                }
            }
        }
    }
}

@Composable
private fun CarteHeure(nom: String, icone: String, couleur: Color, minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(modifier.appui("$nom à ${hm(minutes)}, changer", onClick = onClick).clip(RoundedCornerShape(24.dp)).background(a.surface).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconeTrait(icone, 18.dp, couleur)
            BasicText(nom, modifier = Modifier.padding(start = 8.dp), style = texte(14.5f, a.encre2, FontWeight.SemiBold))
        }
        TexteRoulant(hm(minutes), CHIFFRES.copy(fontSize = 38.sp, lineHeight = 44.sp, color = a.encre))
    }
}

/**
 * Le cadran de la nuit, sur 24 heures (minuit en haut) : l'arc va du coucher (la lune) au lever (le soleil).
 * On fait glisser l'une ou l'autre, de 5 en 5 minutes, avec un petit « tic ».
 */
@Composable
private fun CadranNuit(coucher: Int, lever: Int, modifier: Modifier, bouger: (Boolean, Int) -> Unit, fini: () -> Unit) {
    val a = LocalIdentite.current
    val vibreur = LocalHapticFeedback.current
    val lune = remember { PathParser().parsePathString(Icones.NUIT).toPath() }
    val soleil = remember { PathParser().parsePathString(Icones.SOLEIL).toPath() }
    val entree = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entree.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    val c by rememberUpdatedState(coucher)
    val l by rememberUpdatedState(lever)
    var quel by remember { mutableStateOf<Boolean?>(null) }
    val duree = (lever - coucher + 1440) % 1440
    Box(modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Coucher à ${hm(coucher)}, lever à ${hm(lever)}" }
                .pointerInput(Unit) {
                    fun minute(p: Offset): Int {
                        val ang = Math.toDegrees(atan2((p.x - size.width / 2f).toDouble(), (size.height / 2f - p.y).toDouble()))
                        val m = (((ang + 360) % 360) / 360 * 1440).toInt()
                        return (Math.round(m / 5f) * 5) % 1440
                    }
                    fun ecart(x: Int, y: Int) = minOf(abs(x - y), 1440 - abs(x - y))
                    detectDragGestures(
                        onDragStart = { p ->
                            val m = minute(p)
                            quel = ecart(m, c) <= ecart(m, l)
                        },
                        onDragEnd = {
                            quel = null
                            fini()
                        },
                        onDragCancel = { quel = null },
                    ) { change, _ ->
                        change.consume()
                        val q = quel ?: return@detectDragGestures
                        val m = minute(change.position)
                        if (m != (if (q) c else l)) {
                            vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            bouger(q, m)
                        }
                    }
                },
        ) {
            val e = 38.dp.toPx()
            val r = size.minDimension / 2 - e / 2 - 4.dp.toPx()
            drawCircle(a.surface, r, center, style = Stroke(e))
            // Les heures : un point par heure, les repères écrits à l'intérieur.
            val crayon = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = a.encre2.toArgb()
                textSize = 12.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for (h in 0 until 24) {
                val ang = Math.toRadians(h * 15.0)
                val rp = r - e / 2 - 10.dp.toPx()
                drawCircle(a.encre2.copy(alpha = if (h % 6 == 0) 0.9f else 0.35f), (if (h % 6 == 0) 2.2f else 1.4f).dp.toPx(), Offset(center.x + rp * sin(ang).toFloat(), center.y - rp * cos(ang).toFloat()))
            }
            listOf(0 to "minuit", 6 to "6 h", 12 to "midi", 18 to "18 h").forEach { (h, t) ->
                val ang = Math.toRadians(h * 15.0)
                val rt = r - e / 2 - 30.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(t, center.x + rt * sin(ang).toFloat(), center.y - rt * cos(ang).toFloat() - (crayon.ascent() + crayon.descent()) / 2, crayon)
            }
            // L'arc de la nuit, de la lune au soleil, qui pâlit vers l'aube.
            val debut = -90f + coucher / 1440f * 360f
            val balayage = duree / 1440f * 360f * entree.value
            val part = (balayage / 360f).coerceIn(0.001f, 0.999f)
            rotate(debut, center) {
                drawArc(
                    Brush.sweepGradient(0f to LUNE, part to AUBE, (part + (1 - part) / 2) to AUBE, 1f to LUNE, center = center),
                    0f, balayage, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(e - 8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            fun poignee(degres: Float, chemin: Path, couleur: Color, prise: Boolean) {
                // Le même repère que l'arc : 0° à trois heures, dans le sens des aiguilles.
                val ang = Math.toRadians(degres.toDouble())
                val p = Offset(center.x + r * cos(ang).toFloat(), center.y + r * sin(ang).toFloat())
                drawCircle(a.fond, (if (prise) 19f else 16f).dp.toPx(), p)
                icone(chemin, p, 18.dp.toPx(), couleur)
            }
            poignee(debut, lune, LUNE, quel == true)
            poignee(debut + balayage, soleil, AUBE, quel == false)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TexteRoulant("${duree / 60} h ${"%02d".format(duree % 60)}", CHIFFRES.copy(fontSize = 46.sp, lineHeight = 52.sp, color = a.encre))
            BasicText(qualite(duree), style = texte(14f, if (duree in 420..540) a.accent else AUBE, FontWeight.SemiBold))
        }
    }
}

/** Une icône Banco (un chemin de 24 unités) dessinée au centre [p], de [taille] pixels. */
private fun DrawScope.icone(chemin: Path, p: Offset, taille: Float, couleur: Color) {
    translate(p.x - taille / 2, p.y - taille / 2) {
        scale(taille / 24f, taille / 24f, pivot = Offset.Zero) {
            drawPath(chemin, couleur, style = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
