package africa.samaos.accueil

import africa.samaos.banco.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Un panneau qui suit le doigt : la Cour monte du bas, le Pouls descend du haut.
 * [progres] vaut 0 quand il est fermé et 1 quand il est ouvert.
 *
 * [sens] dit dans quel sens le doigt l'ouvre : −1 vers le haut (la Cour), +1 vers le bas (le Pouls).
 */
@Stable
class Panneau(private val scope: CoroutineScope, private val sens: Float) {

    var progres by mutableFloatStateOf(0f)
        private set

    /** La distance, en pixels, que le doigt parcourt pour l'ouvrir en entier. */
    var course = 1f

    /** L'élan, en pixels par seconde, au-delà duquel un geste ouvre ou ferme quelle que soit la position. */
    var seuilElan = 1000f

    // Dérivés : ceux qui les lisent ne sont prévenus que lorsqu'ils basculent, pas à chaque image.
    private val estVisible = derivedStateOf { progres > 0f }
    private val estOuvert = derivedStateOf { progres >= 1f }

    /** Un bout du panneau est à l'écran. */
    val visible: Boolean get() = estVisible.value

    /** Le panneau est ouvert en entier. */
    val ouvert: Boolean get() = estOuvert.value

    private var animation: Job? = null

    /** Où en était le panneau quand le doigt s'est posé : un tiers du chemin suffit pour changer d'état. */
    private var depart: Float? = null

    /** Le doigt a bougé de [dy] pixels (vers le bas quand c'est positif). */
    fun glisser(dy: Float) {
        animation?.cancel()
        if (depart == null) depart = progres
        progres = (progres + sens * dy / course).coerceIn(0f, 1f)
    }

    /** Le doigt se lève avec un élan de [vy] pixels par seconde : on ouvre ou on ferme. */
    fun relacher(vy: Float) {
        val elan = sens * vy
        val seuil = if ((depart ?: progres) < 0.5f) 0.3f else 0.7f
        depart = null
        val cible = when {
            elan > seuilElan -> 1f
            elan < -seuilElan -> 0f
            progres > seuil -> 1f
            else -> 0f
        }
        aller(cible, elan / course)
    }

    fun ouvrir() = aller(1f, 0f)

    fun fermer() = aller(0f, 0f)

    /** Sans animation : quand une appli vient recouvrir l'Accueil. */
    fun fermerTout() {
        animation?.cancel()
        progres = 0f
    }

    private fun aller(cible: Float, vitesse: Float) {
        animation?.cancel()
        depart = null
        animation = scope.launch {
            animate(progres, cible, vitesse, spring(dampingRatio = 1f, stiffness = 400f)) { valeur, _ ->
                progres = valeur
            }
        }
    }

    /** Les gestes du panneau, seulement quand il est là : fermé, il laisse passer le doigt vers l'Accueil. */
    fun gestes(): Modifier =
        if (!visible) Modifier
        else Modifier
            .nestedScroll(defilement)
            .draggable(glissement, Orientation.Vertical, onDragStopped = { relacher(it) })

    /** Pour les zones du panneau qui ne défilent pas. */
    val glissement = DraggableState { glisser(it) }

    /**
     * Pour une liste qui défile dans le panneau : arrivée en butée, elle passe la main au panneau,
     * qui se referme sous le doigt ; et tant qu'il n'est pas rouvert en entier, c'est lui qui bouge.
     */
    val defilement = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || progres >= 1f) return Offset.Zero
            glisser(available.y)
            return Offset(0f, available.y)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || sens * available.y >= 0f) return Offset.Zero
            glisser(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (progres >= 1f) return Velocity.Zero
            relacher(available.y)
            return available
        }
    }
}
