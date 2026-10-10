package africa.samaos.banco.appli

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Polices
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Une molette : on la fait tourner du doigt, la valeur du milieu est choisie, avec un petit « tic » à chaque cran.
 * Elle boucle (après 23 vient 00) ; toucher une valeur voisine y amène la molette.
 */
@Composable
fun Roue(
    valeurs: List<String>,
    index: Int,
    modifier: Modifier = Modifier,
    cran: Dp = 52.dp,
    taille: TextUnit = 32.sp,
    description: String = "",
    changer: (Int) -> Unit,
) {
    val a = LocalIdentite.current
    val n = valeurs.size
    val tours = 400
    val etat = rememberLazyListState(initialFirstVisibleItemIndex = tours / 2 * n + index)
    val portee = rememberCoroutineScope()
    val vibreur = LocalHapticFeedback.current
    val rappel by rememberUpdatedState(changer)
    val demi = with(LocalDensity.current) { cran.toPx() / 2 }
    val centre by remember { derivedStateOf { etat.firstVisibleItemIndex + if (etat.firstVisibleItemScrollOffset > demi) 1 else 0 } }
    LaunchedEffect(centre) { if (etat.isScrollInProgress) vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    LaunchedEffect(etat.isScrollInProgress) {
        if (!etat.isScrollInProgress && Math.floorMod(centre, n) != index) rappel(Math.floorMod(centre, n))
    }
    // La valeur change d'ailleurs (un raccourci, une autre molette) : la molette tourne jusqu'à elle, par le plus court.
    LaunchedEffect(index) {
        if (!etat.isScrollInProgress && Math.floorMod(centre, n) != index) {
            var d = index - Math.floorMod(centre, n)
            if (d > n / 2) d -= n
            if (d < -n / 2) d += n
            etat.animateScrollToItem(centre + d)
        }
    }
    LazyColumn(
        state = etat,
        flingBehavior = rememberSnapFlingBehavior(etat),
        modifier = modifier.height(cran * 3).semantics {
            contentDescription = "$description ${valeurs[index]}".trim()
            customActions = listOf(
                CustomAccessibilityAction("Plus") { rappel((index + 1) % n); true },
                CustomAccessibilityAction("Moins") { rappel((index + n - 1) % n); true },
            )
        },
        contentPadding = PaddingValues(vertical = cran),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(n * tours) { i ->
            val distance = abs(i - centre)
            val clair by animateFloatAsState(when (distance) { 0 -> 1f; 1 -> 0.38f; else -> 0.12f }, label = "clair")
            val echelle by animateFloatAsState(if (distance == 0) 1f else 0.82f, label = "echelle")
            Box(
                Modifier.height(cran).fillMaxWidth().clickable(interactionSource = null, indication = null) { portee.launch { etat.animateScrollToItem(i) } },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    valeurs[i % n],
                    modifier = Modifier.graphicsLayer {
                        alpha = clair
                        scaleX = echelle
                        scaleY = echelle
                    },
                    style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(if (distance == 0) 320 else 220), fontSize = taille, color = a.encre, fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}

/** L'heure à la molette : les heures, puis les minutes (de [pas] en [pas]), sur une bande qui marque le choix. */
@Composable
fun RoueHeure(
    heure: Int,
    minute: Int,
    modifier: Modifier = Modifier,
    pas: Int = 1,
    bande: Color = LocalIdentite.current.voile,
    cran: Dp = 52.dp,
    taille: TextUnit = 34.sp,
    changer: (Int, Int) -> Unit,
) {
    val a = LocalIdentite.current
    Box(modifier.fillMaxWidth().height(cran * 3), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(cran).clip(RoundedCornerShape(16.dp)).background(bande))
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Roue((0..23).map { "%02d".format(it) }, heure, Modifier.width(96.dp), cran, taille, "Heures") { changer(it, minute) }
            BasicText(":", style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(300), fontSize = taille, color = a.encre))
            Roue((0 until 60 step pas).map { "%02d".format(it) }, (minute / pas).coerceIn(0, 60 / pas - 1), Modifier.width(96.dp), cran, taille, "Minutes") { changer(heure, it * pas) }
        }
    }
}
