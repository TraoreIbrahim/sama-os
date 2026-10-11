package africa.samaos.banco.appli

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Le retour qui suit le doigt : pendant le geste de retour (le bord de l'écran), la page qu'on quitte glisse avec le
// doigt et la page d'avant apparaît dessous ; lâchée assez loin, elle part ; sinon elle revient à sa place.

/** D'où arrive une page : de la droite (une page après l'autre) ou du bas (une page qui surgit, comme un formulaire). */
enum class Sens { DROITE, BAS }

/** Où en est le geste : la part du chemin parcourue par le doigt (0 à 1), et si une sortie commise doit se faire sans autre animation. */
class GesteRetour internal constructor() {
    var tire by mutableFloatStateOf(0f)
        internal set
    var enCours by mutableStateOf(false)
        internal set
    var commis by mutableStateOf(false)
        internal set
}

/**
 * Suit le geste de retour d'Android et appelle [fermer] quand il est commis. Entre temps, [GesteRetour.tire] dit où en
 * est le doigt. Si le doigt revient, tout reprend sa place.
 */
@Composable
fun rememberGesteRetour(actif: Boolean, fermer: () -> Unit): GesteRetour {
    val g = remember { GesteRetour() }
    val portee = rememberCoroutineScope()
    PredictiveBackHandler(enabled = actif) { progres ->
        try {
            progres.collect { e ->
                g.enCours = true
                g.tire = e.progress
            }
            // Lâché : la page finit son chemin, puis elle est retirée sans autre animation.
            animate(g.tire, 1f, animationSpec = tween(150, easing = FastOutLinearInEasing)) { v, _ -> g.tire = v }
            g.commis = true
            fermer()
            withFrameNanos {}
            g.enCours = false
            g.tire = 0f
            withFrameNanos {}
            g.commis = false
        } catch (e: CancellationException) {
            // Le doigt est revenu : la page reprend sa place d'un ressort.
            portee.launch {
                animate(g.tire, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ -> g.tire = v }
                g.enCours = false
            }
        }
    }
    return g
}

/**
 * Une pile de pages : la dernière est à l'écran. Une page qui s'ouvre arrive de la droite, celle qu'on quitte recule un
 * peu ; le retour fait l'inverse, et suit le doigt. [actif] : le geste de retour est pris par la pile (sinon il sort de
 * l'appli) ; par défaut dès qu'il y a une page sous la page courante.
 */
@Composable
fun <T : Any> PileDoigt(
    pile: List<T>,
    retour: () -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = pile.size > 1,
    cle: (T) -> Any = { it },
    sens: (T) -> Sens = { Sens.DROITE },
    fond: Color? = null,
    transition: ((avant: List<T>, apres: List<T>) -> ContentTransform?)? = null,
    page: @Composable (T) -> Unit,
) {
    val a = LocalIdentite.current
    val couleurFond = fond ?: a.fond
    val g = rememberGesteRetour(actif && pile.size > 1, retour)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val largeur = constraints.maxWidth.toFloat()
        val hauteur = constraints.maxHeight.toFloat()
        val duBas = sens(pile.last()) == Sens.BAS
        // Pendant le geste, la page d'avant est dessous, un peu en retrait et voilée ; elle se rapproche avec le doigt.
        if (g.enCours && pile.size > 1) {
            Box(Modifier.fillMaxSize().graphicsLayer { if (!duBas) translationX = -largeur * 0.25f * (1f - g.tire) }.background(couleurFond)) { page(pile[pile.size - 2]) }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.3f * (1f - g.tire) }.background(Color.Black))
        }
        AnimatedContent(
            pile,
            modifier = Modifier.fillMaxSize().graphicsLayer { if (g.enCours) { if (duBas) translationY = hauteur * g.tire else translationX = largeur * g.tire } },
            transitionSpec = {
                val avance = targetState.size >= initialState.size
                val vertical = sens(if (avance) targetState.last() else initialState.last()) == Sens.BAS
                val voulue = transition?.invoke(initialState, targetState)
                when {
                    g.commis -> EnterTransition.None togetherWith ExitTransition.None
                    voulue != null -> voulue
                    vertical && avance -> (slideInVertically(spring(dampingRatio = 0.9f, stiffness = 450f)) { it } + fadeIn(tween(120))) togetherWith fadeOut(tween(250, delayMillis = 100))
                    vertical -> fadeIn(tween(160)) togetherWith (slideOutVertically(tween(260, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(200, delayMillis = 80)))
                    avance ->
                        (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(tween(150))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(220)))
                    else ->
                        (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(240, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(200, delayMillis = 60)))
                }.apply { targetContentZIndex = if (avance) targetState.size.toFloat() else -1f }
            },
            contentKey = { it.size to cle(it.last()) },
            label = "pile",
        ) { p ->
            Box(Modifier.fillMaxSize().background(couleurFond)) { page(p.last()) }
        }
    }
}

/**
 * Une page seule, par-dessus l'écran : elle arrive de la droite ou monte du bas, et repart en suivant le doigt.
 * Son contenu reste affiché pendant qu'elle s'en va.
 */
@Composable
fun PageDoigt(visible: Boolean, fermer: () -> Unit, sens: Sens = Sens.BAS, modifier: Modifier = Modifier, actif: Boolean = visible, contenu: @Composable () -> Unit) {
    val g = rememberGesteRetour(visible && actif, fermer)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val largeur = constraints.maxWidth.toFloat()
        val hauteur = constraints.maxHeight.toFloat()
        AnimatedVisibility(
            visible,
            enter = if (sens == Sens.BAS) slideInVertically(spring(dampingRatio = 0.88f, stiffness = 420f)) { it } + fadeIn(tween(120))
            else slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(tween(150)),
            exit = when {
                g.commis -> ExitTransition.None
                sens == Sens.BAS -> slideOutVertically(tween(240, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(200, delayMillis = 80))
                else -> slideOutHorizontally(tween(240, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(200, delayMillis = 60))
            },
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    if (g.enCours) {
                        if (sens == Sens.BAS) translationY = hauteur * g.tire else translationX = largeur * g.tire
                    }
                },
            ) { contenu() }
        }
    }
}
