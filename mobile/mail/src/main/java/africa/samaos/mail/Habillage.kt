package africa.samaos.mail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.LocalIdentite
import kotlin.math.abs
import kotlin.math.roundToInt

/** Le texte de Mail. */
internal fun st(taille: Float, couleur: Color, poids: FontWeight = FontWeight.Normal, interligne: Float = 1.35f) =
    TextStyle(fontFamily = Polices.corps, fontWeight = poids, fontSize = taille.sp, lineHeight = (taille * interligne).sp, color = couleur)

/** Le grand titre d'une page, avec une ligne dessous (la boîte, le nombre de non lus). */
@Composable
internal fun GrandTitre(texte: String, sous: String? = null, modifier: Modifier = Modifier) {
    val a = LocalIdentite.current
    Column(modifier.padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 10.dp)) {
        BasicText(
            texte, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em, color = a.encre),
        )
        if (sous != null) AnimatedContent(sous, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "sous") { s ->
            BasicText(s, maxLines = 1, overflow = TextOverflow.Ellipsis, style = st(14f, a.encre2))
        }
    }
}

/** La barre du haut : le retour, nommé d'après la page d'avant (« ‹ Boîtes »), et des boutons au bout. */
@Composable
internal fun Barre(retour: String?, onRetour: () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (retour != null) {
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Retour à $retour", role = Role.Button, onClick = onRetour).padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconeTrait(Icones.RETOUR, 24.dp, a.accentTexte, epaisseur = 2f)
                BasicText(retour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp), style = st(17f, a.accentTexte, FontWeight.Medium))
            }
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Un bouton rond, voilé ou plein, qui s'enfonce un peu sous le doigt. */
@Composable
internal fun Rond(icone: String, description: String, plein: Boolean = false, taille: Dp = 42.dp, epaisseur: Float = 1.9f, actif: Boolean = true, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val source = remember { MutableInteractionSource() }
    val presse by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (presse) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "appui")
    Box(
        Modifier.size(taille).graphicsLayer { scaleX = s; scaleY = s; alpha = if (actif) 1f else 0.4f }.clip(CircleShape).background(if (plein) a.accent else a.voile)
            .clickable(interactionSource = source, indication = null, enabled = actif, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, taille * 0.5f, if (plein) a.surAccent else a.accentTexte, epaisseur = epaisseur) }
}

/** Le champ de recherche, doux. */
@Composable
internal fun Recherche(valeur: String, indication: String, changer: (String) -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp)).background(a.champ).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTrait(Icones.RECHERCHE, 18.dp, a.encre2)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = valeur, onValueChange = changer, singleLine = true,
            textStyle = st(16f, a.encre), cursorBrush = SolidColor(a.accent),
            modifier = Modifier.weight(1f).semantics { contentDescription = indication },
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (valeur.isEmpty()) BasicText(indication, style = st(16f, a.encre2))
                    champ()
                }
            },
        )
        AnimatedVisibility(valeur.isNotEmpty(), enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
            Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClickLabel = "Effacer la recherche", role = Role.Button) { changer("") }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(a.encre2.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) { IconeTrait(Icones.FERMER, 11.dp, a.fond, epaisseur = 2.4f) }
            }
        }
    }
}

/** Des choix côte à côte dans une capsule ; le choisi est posé sur un galet qui glisse de l'un à l'autre. */
@Composable
internal fun Segments(choix: List<String>, choisi: Int, modifier: Modifier = Modifier, changer: (Int) -> Unit) {
    val a = LocalIdentite.current
    BoxWithConstraints(modifier.padding(horizontal = 16.dp).fillMaxWidth().height(38.dp).clip(RoundedCornerShape(19.dp)).background(a.champ).padding(3.dp)) {
        val large = maxWidth / choix.size
        val x by animateDpAsState(large * choisi, spring(dampingRatio = 0.8f, stiffness = 520f), label = "galet")
        Box(Modifier.offset(x = x).width(large).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(a.surface))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            choix.forEachIndexed { i, nom ->
                val ici = i == choisi
                val encre by animateColorAsState(if (ici) a.encre else a.encre2, label = "encre")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = nom, role = Role.Tab) { changer(i) },
                    contentAlignment = Alignment.Center,
                ) { BasicText(nom, maxLines = 1, overflow = TextOverflow.Ellipsis, style = st(14f, encre, if (ici) FontWeight.SemiBold else FontWeight.Medium, 1.1f)) }
            }
        }
    }
}

/** Un groupe de lignes sur le papier : coins doux, pas d'ombre. */
@Composable
internal fun Groupe(modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    val a = LocalIdentite.current
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(a.surface), content = contenu)
}

@Composable
internal fun Separateur(debut: Dp = 16.dp) {
    Box(Modifier.padding(start = debut).fillMaxWidth().height(0.6.dp).background(LocalIdentite.current.trait))
}

/** Le titre d'une section (« Aujourd'hui », une boîte). */
@Composable
internal fun Section(texte: String, modifier: Modifier = Modifier) {
    BasicText(texte, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.padding(start = 26.dp, end = 26.dp, top = 20.dp, bottom = 8.dp), style = st(15f, LocalIdentite.current.encre, FontWeight.SemiBold))
}

/** La forme d'une ligne dans un groupe : la première et la dernière ont les coins arrondis. */
internal fun forme(i: Int, n: Int): RoundedCornerShape {
    val r = 22.dp
    return RoundedCornerShape(topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp, bottomStart = if (i == n - 1) r else 0.dp, bottomEnd = if (i == n - 1) r else 0.dp)
}

/**
 * Une ligne qu'on glisse : vers la droite, l'action de [droite] (lu / non lu) ; vers la gauche, celle de [gauche]
 * (archiver, jeter). Le fond se colore avec la distance ; lâchée trop tôt, la ligne revient d'un ressort.
 */
@Composable
internal fun Glisse(
    modifier: Modifier,
    droite: Triple<String, String, Color>,
    gauche: Triple<String, String, Color>,
    faireDroite: () -> Unit,
    faireGauche: () -> Unit,
    contenu: @Composable () -> Unit,
) {
    var x by remember { mutableFloatStateOf(0f) }
    var large by remember { mutableIntStateOf(1) }
    val seuil = large * 0.28f
    val part = (abs(x) / seuil).coerceIn(0f, 1f)
    Box(
        modifier.onSizeChanged { large = it.width.coerceAtLeast(1) }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(droite.second) { faireDroite(); true },
                    CustomAccessibilityAction(gauche.second) { faireGauche(); true },
                )
            },
    ) {
        if (x != 0f) {
            val (icone, nom, couleur) = if (x > 0) droite else gauche
            Box(Modifier.matchParentSize().background(couleur.copy(alpha = 0.35f + 0.65f * part)), contentAlignment = if (x > 0) Alignment.CenterStart else Alignment.CenterEnd) {
                Column(Modifier.padding(horizontal = 24.dp).graphicsLayer { val s = 0.75f + 0.25f * part; scaleX = s; scaleY = s }, horizontalAlignment = Alignment.CenterHorizontally) {
                    IconeTrait(icone, 22.dp, Color.White, epaisseur = 2f)
                    BasicText(nom, modifier = Modifier.padding(top = 3.dp), style = st(11.5f, Color.White, FontWeight.SemiBold, 1.1f))
                }
            }
        }
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), 0) }.draggable(
                rememberDraggableState { d -> x = (x + d).coerceIn(-large * 0.85f, large * 0.45f) },
                Orientation.Horizontal,
                onDragStopped = { v ->
                    when {
                        x > seuil || (v > 1600f && x > large * 0.1f) -> {
                            animate(x, 0f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f)) { b, _ -> x = b }
                            faireDroite()
                        }
                        x < -seuil * 1.3f || (v < -1600f && x < -large * 0.12f) -> {
                            animate(x, -large.toFloat(), animationSpec = tween(170)) { b, _ -> x = b }
                            faireGauche()
                        }
                        else -> animate(x, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)) { b, _ -> x = b }
                    }
                },
            ),
        ) { contenu() }
    }
}
