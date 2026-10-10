package africa.samaos.horloge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.appui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import kotlin.math.roundToInt

/** Le soleil et le lever (abricot), la lune et le coucher (lavande), ce qui arrête (corail). */
val AUBE = Color(0xFFF2B37A)
val LUNE = Color(0xFFB9C4F2)
val CORAIL = Color(0xFFF28B7A)

/** Les chiffres de l'Horloge : fins, étroits, tous de la même largeur (ils ne dansent pas quand ils changent). */
val CHIFFRES = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(220), fontFeatureSettings = "tnum")

fun texte(taille: Float, couleur: Color, poids: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Polices.corps, fontWeight = poids, fontSize = taille.sp, lineHeight = (taille * 1.35f).sp, color = couleur)

val JOURS = listOf(
    DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "M", DayOfWeek.THURSDAY to "J",
    DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S", DayOfWeek.SUNDAY to "D",
)
val SEMAINE = DayOfWeek.entries.take(5).toSet()
val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** « Tous les jours », « En semaine », « Le week-end », « Une fois », ou les jours. */
fun repetition(jours: Set<DayOfWeek>): String = when (jours) {
    emptySet<DayOfWeek>() -> "Une fois"
    DayOfWeek.entries.toSet() -> "Tous les jours"
    SEMAINE -> "En semaine"
    WEEKEND -> "Le week-end"
    else -> DayOfWeek.entries.filter { it in jours }.joinToString(" ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRENCH).removeSuffix(".") }
}

/** Le haut d'une page : son titre, et une ligne qui change en fondu. */
@Composable
fun Entete(titre: String, sous: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 14.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(titre, style = texte(32f, a.encre, FontWeight.Bold).copy(lineHeight = 38.sp))
            if (sous != null) {
                AnimatedContent(sous, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "sous") { s ->
                    BasicText(s, style = texte(15f, a.encre2))
                }
            }
        }
        actions()
    }
}

/** Un bouton rond : une icône ou un mot. */
@Composable
fun BoutonRond(
    description: String,
    fond: Color,
    encre: Color,
    taille: Dp = 64.dp,
    icone: String? = null,
    mot: String? = null,
    actif: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val f by animateColorAsState(if (actif) fond else fond.copy(alpha = fond.alpha * 0.4f), label = "fond")
    Box(
        modifier.appui(description, actif, onClick = onClick).size(taille).clip(CircleShape).background(f),
        contentAlignment = Alignment.Center,
    ) {
        if (icone != null) {
            AnimatedContent(icone, transitionSpec = { (fadeIn(tween(160)) + androidx.compose.animation.scaleIn(tween(200), 0.6f)) togetherWith fadeOut(tween(100)) }, label = "icone") { ic ->
                IconeTrait(ic, taille * 0.4f, encre.copy(alpha = if (actif) 1f else 0.5f), epaisseur = 2.2f)
            }
        }
        if (mot != null) BasicText(mot, style = texte(15f, encre.copy(alpha = if (actif) 1f else 0.5f), FontWeight.SemiBold))
    }
}

/** Un texte dont chaque caractère qui change roule comme un compteur (vers le haut, ou vers le bas pour un décompte). */
@Composable
fun TexteRoulant(valeur: String, style: TextStyle, modifier: Modifier = Modifier, descend: Boolean = false) {
    Row(modifier) {
        valeur.forEachIndexed { i, ch ->
            key(valeur.length - i) {
                AnimatedContent(
                    ch,
                    transitionSpec = {
                        val passage = if (!targetState.isDigit() || !initialState.isDigit()) fadeIn(tween(120)) togetherWith fadeOut(tween(80))
                        else if (descend) (slideInVertically(tween(260)) { -it } + fadeIn(tween(200))) togetherWith (slideOutVertically(tween(260)) { it } + fadeOut(tween(180)))
                        else (slideInVertically(tween(260)) { it } + fadeIn(tween(200))) togetherWith (slideOutVertically(tween(260)) { -it } + fadeOut(tween(180)))
                        passage.using(SizeTransform(clip = true))
                    },
                    label = "chiffre",
                ) { c -> BasicText(c.toString(), style = style) }
            }
        }
    }
}

/** Les jours de la semaine, ronds, qui se remplissent quand on les choisit. */
@Composable
fun ChoixJours(jours: Set<DayOfWeek>, couleur: Color, changer: (Set<DayOfWeek>) -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        JOURS.forEach { (j, l) ->
            val oui = j in jours
            val fond by animateColorAsState(if (oui) couleur else a.champ, label = "jour")
            val encre by animateColorAsState(if (oui) a.surAccent else a.encre, label = "encre")
            Box(
                Modifier.appui(j.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH), role = Role.Checkbox) { changer(if (oui) jours - j else jours + j) }
                    .size(42.dp).clip(CircleShape).background(fond),
                contentAlignment = Alignment.Center,
            ) { BasicText(l, style = texte(15f, encre, FontWeight.Bold)) }
        }
    }
}

/** Ce qui change les listes (une alarme gardée, supprimée, remise) : les pages se relisent. */
object Etat {
    var version by mutableIntStateOf(0)
}

/** La place que prend la barre d'onglets flottante, à garder libre en bas des pages. */
@Composable
fun Pied() = Box(Modifier.navigationBarsPadding().padding(bottom = 96.dp))
