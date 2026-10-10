package africa.samaos.banco.appli

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Les gestes communs des applis : appuyer, glisser pour supprimer, et le message « Annuler » qui les suit. */

private val ROUGE_SUPPRIMER = Color(0xFFE0705C)

/** Un bouton qui s'enfonce un peu sous le doigt et revient d'un ressort. */
@Composable
fun Modifier.appui(description: String, actif: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val presse by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (presse) 0.92f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "appui")
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .clickable(interactionSource = source, indication = null, enabled = actif, onClickLabel = description, role = role, onClick = onClick)
        .semantics { contentDescription = description }
}

/**
 * Glisser vers la gauche pour supprimer : le fond rouge apparaît avec la corbeille, au-delà de la moitié la ligne part.
 * Les lecteurs d'écran ont l'action « Supprimer ».
 */
@Composable
fun Glissable(supprimer: () -> Unit, modifier: Modifier = Modifier, contenu: @Composable () -> Unit) {
    // La ligne suit le doigt sans délai ; seuls le départ et le retour sont animés.
    var x by remember { mutableFloatStateOf(0f) }
    var large by remember { mutableIntStateOf(1) }
    val part = (-x / large).coerceIn(0f, 1f)
    Box(
        modifier.onSizeChanged { large = it.width.coerceAtLeast(1) }
            .semantics { customActions = listOf(CustomAccessibilityAction("Supprimer") { supprimer(); true }) },
    ) {
        if (part > 0.001f) {
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(26.dp)).background(ROUGE_SUPPRIMER.copy(alpha = (0.35f + part).coerceAtMost(1f))),
                contentAlignment = Alignment.CenterEnd,
            ) {
                IconeTrait(Icones.CORBEILLE, 24.dp, Color.White, Modifier.padding(end = 26.dp).graphicsLayer { scaleX = 0.7f + part; scaleY = 0.7f + part })
            }
        }
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .draggable(
                    rememberDraggableState { d -> x = (x + d).coerceIn(-large.toFloat(), 0f) },
                    Orientation.Horizontal,
                    onDragStopped = { v ->
                        if (x < -large * 0.45f || (v < -1800f && x < -large * 0.15f)) {
                            animate(x, -large.toFloat(), animationSpec = tween(160)) { a, _ -> x = a }
                            supprimer()
                            delay(800)
                            x = 0f
                        } else {
                            animate(x, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)) { a, _ -> x = a }
                        }
                    },
                ),
        ) { contenu() }
    }
}

/** Un message en bas de l'écran (« Alarme supprimée · Annuler »), qui s'en va tout seul. */
class Message(val texte: String, val annuler: (() -> Unit)? = null, val id: Long = System.nanoTime())

object Avis {
    var message by mutableStateOf<Message?>(null)
    fun dire(texte: String, annuler: (() -> Unit)? = null) {
        message = Message(texte, annuler)
    }
}

@Composable
fun BarreAvis(modifier: Modifier = Modifier, bas: Dp = 88.dp) {
    val a = LocalIdentite.current
    val m = Avis.message
    var dernier by remember { mutableStateOf<Message?>(null) }
    if (m != null) dernier = m
    LaunchedEffect(m?.id) {
        if (m != null) {
            delay(if (m.annuler != null) 6000 else 2800)
            if (Avis.message?.id == m.id) Avis.message = null
        }
    }
    AnimatedVisibility(
        m != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it } + fadeIn(),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
    ) {
        val d = dernier ?: return@AnimatedVisibility
        Row(
            Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = bas).fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)).background(a.encre).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(d.texte, modifier = Modifier.weight(1f).padding(vertical = 10.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 20.sp, color = a.fond))
            d.annuler?.let { annuler ->
                BasicText(
                    "Annuler",
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) {
                        annuler()
                        Avis.message = null
                    }.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (a.sombre) lerp(a.accent, Color.Black, 0.35f) else lerp(a.accent, Color.White, 0.45f)),
                )
            }
        }
    }
}

