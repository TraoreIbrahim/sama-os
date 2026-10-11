package africa.samaos.meteo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalIdentite
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as StyleDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Les pièces de la page d'une ville : les heures, les jours, la pluie à venir, les tuiles de détail, les alertes.

/** Les teintes des cartes sur le ciel, de jour et de nuit. */
internal class Teintes(val nuit: Boolean) {
    val carte = if (nuit) Color(0x14FFFFFF) else Color(0x8CFFFFFF)
    val puce = if (nuit) Color(0x1AFFFFFF) else Color(0x80FFFFFF)
    val bleu = if (nuit) Color(0xFF9DB4E8) else Color(0xFF3D5A99)
    val or = Color(0xFFE2A62B)
    val laterite = if (nuit) Color(0xFFF0A080) else Color(0xFFB5532F)
    val trait = if (nuit) Color(0x33FFFFFF) else Color(0x1F2A2018)
}

internal const val EPINGLE = "M12 21s-6-5.6-6-11a6 6 0 0 1 12 0c0 5.4-6 11-6 11z M12 7.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z"
private const val SOLEIL_NUAGE = "M9 4v1.5 M3.8 6.3l1 1 M2.5 11.5H4 M7 13a3 3 0 1 1 4.7-2.5 M9 20a3.5 3.5 0 0 1-.5-7A5 5 0 0 1 18 12a3.5 3.5 0 0 1 0 8z"
private const val NUAGE = "M7 18a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z"
private const val PLUIE = "M7 15a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 6a4.5 4.5 0 0 1 0 9z M9 18l-1 2.5 M13 18l-1 2.5 M17 18l-1 2.5"
private const val ORAGE = "M7 15a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 6a4.5 4.5 0 0 1 0 9z M12.5 14l-2 3.5h3l-2 3.5"
private const val LUNE = "M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z"
private const val BROUILLARD = "M4 9h16 M6 13h12 M4 17h16"
internal const val GOUTTE = "M12 3.5s6 6.5 6 10.5a6 6 0 0 1-12 0c0-4 6-10.5 6-10.5z"
internal const val VENT = "M3 9h11a3 3 0 1 0-3-3 M3 15h15a3 3 0 1 1-3 3"
internal const val LEVER = "M12 4v3 M5 11l1.5 1.5 M19 11l-1.5 1.5 M4 19h16 M7.5 19a4.5 4.5 0 0 1 9 0 M12 3l-2 2 M12 3l2 2"
internal const val COUCHER = "M12 4v3 M5 11l1.5 1.5 M19 11l-1.5 1.5 M4 19h16 M7.5 19a4.5 4.5 0 0 1 9 0 M12 7l-2-2 M12 7l2-2"
private const val OEIL = "M2 12s3.5-6 10-6s10 6 10 6s-3.5 6-10 6S2 12 2 12z M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z"
private const val FEUILLE = "M5 19c0-8 5-13 14-14c-1 9-6 14-14 14z M5 19l7-7"
private const val THERMO = "M10 4a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0z M12 9v5"

internal fun picto(code: Int, jour: Boolean): String = when {
    code >= 95 -> ORAGE
    Ciel.pluvieux(code) -> PLUIE
    code == 45 || code == 48 -> BROUILLARD
    code <= 1 -> if (jour) Icones.SOLEIL else LUNE
    code == 2 -> if (jour) SOLEIL_NUAGE else NUAGE
    else -> NUAGE
}

internal fun degres(t: Double) = "${t.roundToInt()}°"
internal fun heureCourte(t: LocalDateTime) = t.format(DateTimeFormatter.ofPattern("H:mm"))

private fun st(taille: Float, couleur: Color, poids: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Polices.corps, fontWeight = poids, fontSize = taille.sp, lineHeight = (taille * 1.3f).sp, color = couleur)

/** Une carte sur le ciel : un voile blanc le jour, un voile clair la nuit. */
@Composable
internal fun Carte(t: Teintes, modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.carte).padding(16.dp), content = contenu)
}

/** Le petit titre d'une carte : une icône et un mot. */
@Composable
internal fun TitreCarte(icone: String, texte: String) {
    val a = LocalIdentite.current
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconeTrait(icone, 14.dp, a.encre2, epaisseur = 2f)
        BasicText(texte.uppercase(Locale.FRENCH), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.6.sp, color = a.encre2))
    }
}

// ——— Les alertes de Sama ———

@Composable
internal fun CarteAlerte(al: Alerte, t: Teintes) {
    val a = LocalIdentite.current
    val fond = if (al.forte) t.laterite.copy(alpha = if (t.nuit) 0.22f else 0.16f) else t.or.copy(alpha = if (t.nuit) 0.22f else 0.26f)
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(fond).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconeTrait(Icones.ALERTE, 18.dp, if (al.forte) t.laterite else a.encre, epaisseur = 2f)
            BasicText(al.titre, style = st(16f, if (al.forte) t.laterite else a.encre, FontWeight.Bold))
        }
        BasicText(al.texte, modifier = Modifier.padding(top = 4.dp), style = st(14f, a.encre))
        BasicText("Calculé sur le téléphone d'après les prévisions.", modifier = Modifier.padding(top = 4.dp), style = st(12f, a.encre2))
    }
}

// ——— Les heures ———

/** Une case de la bande des heures : une heure de prévision, ou le lever ou le coucher du soleil. */
internal sealed interface Case {
    class H(val h: Heure, val maintenant: Boolean) : Case
    class Soleil(val quand: LocalDateTime, val lever: Boolean) : Case
}

/** Les 24 prochaines heures, qui défilent, avec le lever et le coucher du soleil à leur place. */
@Composable
internal fun Heures(p: Previsions, t: Teintes) {
    val heures = p.heures.filter { !it.heure.isBefore(p.actuel.heure.truncatedTo(ChronoUnit.HOURS)) }.take(25)
    if (heures.isEmpty()) return
    val cases = mutableListOf<Case>()
    val soleils = p.jours.flatMap { listOfNotNull(it.lever?.let { l -> Case.Soleil(l, true) }, it.coucher?.let { c -> Case.Soleil(c, false) }) }
        .filter { it.quand.isAfter(p.actuel.heure) && it.quand.isBefore(heures.last().heure) }
    heures.forEachIndexed { i, h ->
        cases += Case.H(h, i == 0)
        soleils.filter { it.quand.isAfter(h.heure) && !it.quand.isAfter(h.heure.plusHours(1)) }.forEach { cases += it }
    }
    Carte(t, Modifier.padding(horizontal = 16.dp)) {
        BasicText(Ciel.phrase(p), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 10.dp), style = st(14.5f, LocalIdentite.current.encre, FontWeight.Medium))
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(t.trait))
        BandeHeures(cases, t)
    }
}

@Composable
internal fun BandeHeures(cases: List<Case>, t: Teintes) {
    val a = LocalIdentite.current
    LazyRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(cases) { c ->
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (c) {
                    is Case.H -> {
                        BasicText(if (c.maintenant) "Maint." else "${c.h.heure.hour} h", style = st(13f, a.encre, if (c.maintenant) FontWeight.SemiBold else FontWeight.Normal))
                        IconeTrait(picto(c.h.code, c.h.jour), 24.dp, a.encre)
                        BasicText(degres(c.h.temp), style = st(16f, a.encre, FontWeight.SemiBold))
                        BasicText(c.h.pluie?.takeIf { it >= 30 }?.let { "$it %" } ?: "", modifier = Modifier.heightIn(min = 16.dp), style = st(12f, t.bleu, FontWeight.SemiBold))
                    }
                    is Case.Soleil -> {
                        BasicText(heureCourte(c.quand), style = st(13f, a.encre2))
                        IconeTrait(if (c.lever) LEVER else COUCHER, 24.dp, t.or, epaisseur = 2f)
                        BasicText(if (c.lever) "Lever" else "Coucher", style = st(13f, a.encre2, FontWeight.Medium))
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ——— Les dix jours ———

@Composable
internal fun Jours(p: Previsions, t: Teintes, ouvrir: (Jour) -> Unit) {
    val a = LocalIdentite.current
    val jours = p.jours.take(10)
    if (jours.isEmpty()) return
    val bas = jours.minOf { it.min }
    val haut = jours.maxOf { it.max }.coerceAtLeast(bas + 1)
    val auj = p.actuel.heure.toLocalDate()
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.carte).padding(horizontal = 16.dp, vertical = 10.dp)) {
        TitreCarte(Icones.HORLOGE, "${jours.size} jours")
        jours.forEachIndexed { i, j ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(0.6.dp).background(t.trait))
            Row(
                Modifier.fillMaxWidth().height(46.dp).clickable(onClickLabel = "Le détail du jour", role = Role.Button) { ouvrir(j) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(
                    if (j.date == auj) "Auj." else j.date.dayOfWeek.getDisplayName(StyleDate.SHORT, Locale.FRENCH).removeSuffix(".").replaceFirstChar { it.uppercase() },
                    modifier = Modifier.width(44.dp), style = st(16f, a.encre, FontWeight.Medium),
                )
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconeTrait(picto(j.code, true), 22.dp, a.encre)
                        j.pluie?.takeIf { it >= 30 }?.let { BasicText("$it %", style = st(10.5f, t.bleu, FontWeight.SemiBold)) }
                    }
                }
                BasicText(degres(j.min), modifier = Modifier.width(30.dp), style = st(14f, a.encre2).copy(textAlign = TextAlign.End))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(a.encre.copy(alpha = 0.1f))) {
                    val debut = ((j.min - bas) / (haut - bas)).toFloat().coerceIn(0f, 1f)
                    val fin = ((j.max - bas) / (haut - bas)).toFloat().coerceIn(debut, 1f)
                    Row(Modifier.fillMaxSize()) {
                        if (debut > 0f) Spacer(Modifier.weight(debut))
                        Box(Modifier.weight((fin - debut).coerceAtLeast(0.04f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(t.or, t.laterite))))
                        if (fin < 1f) Spacer(Modifier.weight(1f - fin))
                    }
                    // Aujourd'hui : un point à la température qu'il fait.
                    if (j.date == auj) {
                        val x = ((p.actuel.temp - bas) / (haut - bas)).toFloat().coerceIn(0f, 1f)
                        Row(Modifier.fillMaxSize()) {
                            if (x > 0f) Spacer(Modifier.weight(x))
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (t.nuit) Color.White else Color(0xFF2A2018)))
                            if (x < 1f) Spacer(Modifier.weight(1f - x))
                        }
                    }
                }
                BasicText(degres(j.max), modifier = Modifier.width(30.dp), style = st(16f, a.encre, FontWeight.SemiBold))
                IconeTrait(Icones.CHEVRON, 14.dp, a.encre2)
            }
        }
    }
}

// ——— La pluie à venir ———

@Composable
internal fun CartePluie(p: Previsions, t: Teintes) {
    val a = LocalIdentite.current
    val phrase = Ciel.pluieAVenir(p) ?: return
    val h = p.heures.filter { it.heure.isAfter(p.actuel.heure) }.take(12)
    Carte(t, Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) {
        TitreCarte(GOUTTE, "Pluie à venir")
        BasicText(phrase, style = st(16f, a.encre, FontWeight.SemiBold))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            h.forEach { x ->
                val part = (x.pluie ?: 0) / 100f
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(part.coerceAtLeast(0.06f)).clip(RoundedCornerShape(3.dp)).background(t.bleu.copy(alpha = if (x.mm >= 1) 0.95f else 0.5f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            h.forEachIndexed { i, x ->
                BasicText(if (i % 3 == 0) "${x.heure.hour} h" else "", modifier = Modifier.weight(1f), style = st(11f, a.encre2))
            }
        }
        BasicText("Les barres : la chance de pluie, heure par heure ; en plus foncé, une pluie qui mouille vraiment.", modifier = Modifier.padding(top = 6.dp), style = st(12f, a.encre2))
    }
}

// ——— Les tuiles de détail ———

@Composable
private fun Tuile(t: Teintes, modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    Column(modifier.heightIn(min = 150.dp).clip(RoundedCornerShape(22.dp)).background(t.carte).padding(14.dp), content = contenu)
}

@Composable
private fun Grand(texte: String, couleur: Color) = BasicText(texte, maxLines = 1, style = TextStyle(fontFamily = Polices.fine, fontWeight = FontWeight(300), fontSize = 30.sp, lineHeight = 34.sp, color = couleur))

/** Les tuiles, deux par rangée : UV, vent, soleil, humidité, ressenti, visibilité, air, lune. */
@Composable
internal fun Tuiles(p: Previsions, t: Teintes) {
    val a = LocalIdentite.current
    val ac = p.actuel
    val auj = p.aujourdhui
    val tuiles = mutableListOf<@Composable () -> Unit>()
    // UV
    val uv = ac.uv ?: auj?.uvMax
    if (uv != null) tuiles += {
        Tuile(t) {
            TitreCarte(Icones.SOLEIL, "Indice UV")
            Grand("${(if (ac.jour) ac.uv ?: uv else uv).roundToInt()}", a.encre)
            BasicText(Ciel.niveauUv(if (ac.jour) ac.uv ?: uv else uv).replaceFirstChar { it.uppercase() }, style = st(14f, a.encre, FontWeight.Medium))
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF6FBF73), t.or, t.laterite, Color(0xFF7A4FB0))))) {
                val x = ((if (ac.jour) ac.uv ?: uv else uv) / 11.0).toFloat().coerceIn(0f, 1f)
                Row(Modifier.fillMaxSize()) {
                    if (x > 0f) Spacer(Modifier.weight(x))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                    if (x < 1f) Spacer(Modifier.weight(1f - x))
                }
            }
            val fortes = auj?.let { p.heuresDu(it.date).filter { h -> (h.uv ?: 0.0) >= 6 } }.orEmpty()
            BasicText(
                when {
                    fortes.isNotEmpty() && ac.heure.hour <= fortes.last().heure.hour -> "À éviter de ${fortes.first().heure.hour} h à ${fortes.last().heure.hour + 1} h : chapeau, ombre."
                    (auj?.uvMax ?: 0.0) >= 6 -> "Le soleil a tapé fort aujourd'hui ; demain aussi, sans doute."
                    else -> "Pas de danger pour la peau aujourd'hui."
                },
                modifier = Modifier.padding(top = 8.dp), style = st(12.5f, a.encre2),
            )
        }
    }
    // Vent
    tuiles += {
        Tuile(t) {
            TitreCarte(VENT, "Vent")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Grand("${ac.vent.roundToInt()}", a.encre)
                        BasicText("km/h", modifier = Modifier.padding(bottom = 5.dp), style = st(13f, a.encre2))
                    }
                    BasicText(Ciel.vent(ac.vent) + (ac.direction?.let { " ${Ciel.direction(it)}" } ?: ""), style = st(13.5f, a.encre, FontWeight.Medium))
                    ac.rafales?.takeIf { it > ac.vent + 5 }?.let { BasicText("Rafales ${it.roundToInt()} km/h", modifier = Modifier.padding(top = 2.dp), style = st(12.5f, a.encre2)) }
                }
                ac.direction?.let { Boussole(it, t, 64.dp) }
            }
        }
    }
    // Soleil
    val lever = auj?.lever
    val coucher = auj?.coucher
    if (lever != null && coucher != null) tuiles += {
        Tuile(t) {
            TitreCarte(LEVER, "Soleil")
            ArcSoleil(lever, coucher, ac.heure, t, Modifier.fillMaxWidth().height(58.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    BasicText("Lever", style = st(11.5f, a.encre2))
                    BasicText(heureCourte(lever), style = st(15f, a.encre, FontWeight.SemiBold))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    BasicText("Coucher", style = st(11.5f, a.encre2))
                    BasicText(heureCourte(coucher), style = st(15f, a.encre, FontWeight.SemiBold))
                }
            }
            val min = ChronoUnit.MINUTES.between(lever, coucher)
            BasicText("${min / 60} h ${"%02d".format(min % 60)} de jour", modifier = Modifier.padding(top = 4.dp), style = st(12.5f, a.encre2))
        }
    }
    // Humidité
    tuiles += {
        Tuile(t) {
            TitreCarte(GOUTTE, "Humidité")
            Grand("${ac.humidite} %", a.encre)
            ac.rosee?.let {
                BasicText(Ciel.rosee(it), style = st(14f, a.encre, FontWeight.Medium))
                BasicText("Point de rosée ${degres(it)} : au-dessous, l'eau de l'air se dépose.", modifier = Modifier.padding(top = 6.dp), style = st(12.5f, a.encre2))
            }
        }
    }
    // Ressenti
    tuiles += {
        Tuile(t) {
            TitreCarte(THERMO, "Ressenti")
            Grand(degres(ac.ressenti), a.encre)
            BasicText(
                when {
                    ac.ressenti >= ac.temp + 1.5 -> "L'humidité le fait paraître plus chaud que ${degres(ac.temp)}."
                    ac.ressenti <= ac.temp - 1.5 -> "Le vent le fait paraître plus frais que ${degres(ac.temp)}."
                    else -> "Comme la température : ${degres(ac.temp)}."
                },
                modifier = Modifier.padding(top = 2.dp), style = st(13f, a.encre),
            )
        }
    }
    // Visibilité et pression
    tuiles += {
        Tuile(t) {
            TitreCarte(OEIL, "Visibilité")
            val v = ac.visibilite
            if (v != null) {
                Grand(if (v >= 1000) "${(v / 1000).roundToInt()} km" else "${v.roundToInt()} m", a.encre)
                BasicText(Ciel.visibilite(v), style = st(13.5f, a.encre, FontWeight.Medium))
            }
            ac.pression?.let { BasicText("Pression ${it.roundToInt()} hPa", modifier = Modifier.padding(top = 6.dp), style = st(12.5f, a.encre2)) }
        }
    }
    // L'air
    p.air?.let { air ->
        tuiles += {
            Tuile(t) {
                TitreCarte(FEUILLE, "Qualité de l'air")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Grand("${air.indice}", a.encre)
                    BasicText(Ciel.air(air.indice), modifier = Modifier.padding(bottom = 5.dp), style = st(14f, a.encre, FontWeight.Medium))
                }
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF6FBF73), t.or, t.laterite, Color(0xFF7A4FB0))))) {
                    val x = (air.indice / 120f).coerceIn(0f, 1f)
                    Row(Modifier.fillMaxSize()) {
                        if (x > 0f) Spacer(Modifier.weight(x))
                        Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                        if (x < 1f) Spacer(Modifier.weight(1f - x))
                    }
                }
                BasicText(
                    listOfNotNull(air.pm25?.let { "Fines PM2,5 ${it.roundToInt()}" }, air.poussiere?.takeIf { it >= 20 }?.let { "poussière ${it.roundToInt()} µg/m³" }).joinToString(" · "),
                    modifier = Modifier.padding(top = 6.dp), style = st(12f, a.encre2),
                )
                BasicText(Ciel.conseilAir(air), modifier = Modifier.padding(top = 2.dp), style = st(12.5f, a.encre2))
            }
        }
    }
    // La lune
    tuiles += {
        Tuile(t) {
            TitreCarte(LUNE, "Lune")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DessinLune(ac.heure.toLocalDate(), t, 56.dp)
                Column {
                    BasicText(Lune.nom(ac.heure.toLocalDate()), style = st(14.5f, a.encre, FontWeight.SemiBold))
                    BasicText("${(Lune.eclairee(ac.heure.toLocalDate()) * 100).roundToInt()} % éclairée", style = st(12.5f, a.encre2))
                }
            }
            BasicText(Lune.prochaine(ac.heure.toLocalDate()), modifier = Modifier.padding(top = 8.dp), style = st(12.5f, a.encre2))
        }
    }
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tuiles.chunked(2).forEach { rangee ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.ui.unit.Dp.Unspecified).heightIn(min = 150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rangee.forEach { tuile -> Box(Modifier.weight(1f).fillMaxHeight()) { tuile() } }
                if (rangee.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** La rose des vents : d'où vient le vent, par une flèche. */
@Composable
private fun Boussole(direction: Int, t: Teintes, taille: Dp) {
    val a = LocalIdentite.current
    val encre = a.encre
    val encre2 = a.encre2
    Box(Modifier.size(taille), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val centre = Offset(size.width / 2, size.height / 2)
            drawCircle(encre2.copy(alpha = 0.35f), r - 1.dp.toPx(), centre, style = Stroke(1.dp.toPx()))
            for (k in 0 until 16) {
                val ang = k * PI / 8
                val l = if (k % 4 == 0) 5.dp.toPx() else 2.5.dp.toPx()
                drawLine(encre2.copy(alpha = 0.6f), centre + Offset((r - 2.dp.toPx()) * sin(ang).toFloat(), -(r - 2.dp.toPx()) * cos(ang).toFloat()), centre + Offset((r - 2.dp.toPx() - l) * sin(ang).toFloat(), -(r - 2.dp.toPx() - l) * cos(ang).toFloat()), 1.dp.toPx())
            }
            // La flèche pointe vers où le vent va (il vient de « direction »).
            val ang = Math.toRadians(direction.toDouble() + 180)
            val pointe = centre + Offset((r - 9.dp.toPx()) * sin(ang).toFloat(), -(r - 9.dp.toPx()) * cos(ang).toFloat())
            val queue = centre - Offset((r - 9.dp.toPx()) * sin(ang).toFloat(), -(r - 9.dp.toPx()) * cos(ang).toFloat())
            drawLine(encre, queue, pointe, 2.dp.toPx(), StrokeCap.Round)
            val g = ang - 2.6
            val d = ang + 2.6
            val aile = 7.dp.toPx()
            drawLine(encre, pointe, pointe + Offset(aile * sin(g).toFloat(), -aile * cos(g).toFloat()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(encre, pointe, pointe + Offset(aile * sin(d).toFloat(), -aile * cos(d).toFloat()), 2.dp.toPx(), StrokeCap.Round)
        }
        BasicText("N", modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp), style = st(8f, t.laterite, FontWeight.Bold))
    }
}

/** L'arc du soleil : de son lever à son coucher, où il en est maintenant. */
@Composable
private fun ArcSoleil(lever: LocalDateTime, coucher: LocalDateTime, maintenant: LocalDateTime, t: Teintes, modifier: Modifier) {
    val a = LocalIdentite.current
    val encre2 = a.encre2
    val or = t.or
    val part = (ChronoUnit.MINUTES.between(lever, maintenant).toFloat() / ChronoUnit.MINUTES.between(lever, coucher).coerceAtLeast(1)).coerceIn(-0.08f, 1.08f)
    Canvas(modifier) {
        val l = 6.dp.toPx()
        val rayonX = (size.width - 2 * l) / 2
        val rayonY = size.height - 10.dp.toPx()
        val centre = Offset(size.width / 2, size.height - 4.dp.toPx())
        val chemin = Path().apply {
            moveTo(centre.x - rayonX, centre.y)
            for (k in 1..40) {
                val u = k / 40f
                lineTo(centre.x - rayonX + 2 * rayonX * u, centre.y - rayonY * sin(u * PI).toFloat())
            }
        }
        drawPath(chemin, encre2.copy(alpha = 0.4f), style = Stroke(1.5.dp.toPx()))
        drawLine(encre2.copy(alpha = 0.4f), Offset(l, centre.y), Offset(size.width - l, centre.y), 1.dp.toPx())
        if (part in 0f..1f) {
            val x = centre.x - rayonX + 2 * rayonX * part
            val y = centre.y - rayonY * sin(part * PI).toFloat()
            drawCircle(or.copy(alpha = 0.35f), 9.dp.toPx(), Offset(x, y))
            drawCircle(or, 5.dp.toPx(), Offset(x, y))
        }
    }
}

/** La lune telle qu'on la voit : la part éclairée, en clair sur l'ombre. */
@Composable
private fun DessinLune(date: LocalDate, t: Teintes, taille: Dp) {
    val phase = Lune.phase(date).toFloat()
    val clair = if (t.nuit) Color(0xFFF2E8D5) else Color(0xFFFFF6E3)
    val ombre = if (t.nuit) Color(0x33FFFFFF) else Color(0x332A2018)
    Canvas(Modifier.size(taille)) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(ombre, r, c)
        // La part éclairée : un disque clair, dont on retire une ellipse selon la phase.
        val k = cos(phase * 2 * PI).toFloat()
        val chemin = Path().apply {
            moveTo(c.x, c.y - r)
            // Le bord extérieur, à droite avant la pleine lune, à gauche après.
            val droite = phase <= 0.5f
            val bord = if (droite) 1f else -1f
            for (i in 0..30) {
                val ang = -PI / 2 + i * PI / 30
                lineTo(c.x + bord * r * cos(ang).toFloat(), c.y + r * sin(ang).toFloat())
            }
            // Le terminateur : une demi-ellipse de largeur k·r.
            for (i in 0..30) {
                val ang = PI / 2 - i * PI / 30
                lineTo(c.x + bord * k * r * cos(ang).toFloat(), c.y + r * sin(ang).toFloat())
            }
            close()
        }
        drawPath(chemin, clair)
    }
}

// ——— Le détail d'un jour ———

@Composable
internal fun FeuilleJour(p: Previsions, j: Jour, t: Teintes, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val titre = j.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
    FeuilleAppli(fermer = fermer, titre = titre) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconeTrait(picto(j.code, true), 44.dp, a.encre, epaisseur = 1.5f)
                Column(Modifier.weight(1f)) {
                    BasicText(Ciel.description(j.code), style = st(18f, a.encre, FontWeight.SemiBold))
                    BasicText(
                        listOfNotNull("${degres(j.min)} à ${degres(j.max)}", j.pluie?.takeIf { it >= 20 }?.let { "$it % de pluie" }, j.mm.takeIf { it >= 1 }?.let { "${it.roundToInt()} mm" }).joinToString(" · "),
                        style = st(14f, a.encre2),
                    )
                }
            }
            val heures = p.heuresDu(j.date)
            if (heures.isNotEmpty()) {
                val lever = j.lever?.let { Case.Soleil(it, true) }
                val coucher = j.coucher?.let { Case.Soleil(it, false) }
                val cases = mutableListOf<Case>()
                heures.forEach { h ->
                    cases += Case.H(h, false)
                    listOfNotNull(lever, coucher).filter { it.quand.isAfter(h.heure) && !it.quand.isAfter(h.heure.plusHours(1)) }.forEach { cases += it }
                }
                BandeHeures(cases, t)
            }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                j.ventMax?.let { Ligne(VENT, "Vent jusqu'à ${it.roundToInt()} km/h" + (j.direction?.let { d -> " ${Ciel.direction(d)}" } ?: "") + (j.rafalesMax?.takeIf { r -> r > it + 5 }?.let { r -> ", rafales ${r.roundToInt()} km/h" } ?: "")) }
                j.uvMax?.let { Ligne(Icones.SOLEIL, "UV jusqu'à ${it.roundToInt()}, ${Ciel.niveauUv(it)}") }
                if (j.lever != null && j.coucher != null) Ligne(LEVER, "Lever ${heureCourte(j.lever)} · coucher ${heureCourte(j.coucher)}")
                j.ressentiMax?.takeIf { it >= j.max + 2 }?.let { Ligne(THERMO, "Ressenti jusqu'à ${degres(it)}") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Ligne(icone: String, texte: String) {
    val a = LocalIdentite.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconeTrait(icone, 18.dp, a.encre2)
        BasicText(texte, style = st(14.5f, a.encre))
    }
}

/** L'heure dans la ville, « 14:05 », pour la liste des villes. */
internal fun heureLocale(p: Previsions): String = p.heureLocale().format(DateTimeFormatter.ofPattern("H:mm"))

@Suppress("unused")
private val sansAvertissement = LocalTime.MIDNIGHT to Size.Zero
