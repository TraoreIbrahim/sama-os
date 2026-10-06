package africa.samaos.accueil

import africa.samaos.banco.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Le passage d'un Espace à l'autre à trois doigts (maquette esp-01) : l'Accueil se réduit en carte,
 * suit les doigts, et l'Espace voisin arrive de côté. Relâché assez loin, on y entre.
 */
@Stable
class Bascule(private val scope: CoroutineScope) {
    /** Le déplacement des doigts, en pixels : négatif vers la gauche, ce qui fait venir l'Espace de droite. */
    var decalage by mutableFloatStateOf(0f)
        private set

    /** 0 : l'Accueil en plein écran ; 1 : réduit en carte. */
    var reduction by mutableFloatStateOf(0f)
        private set

    var largeur = 1f
    var ecart = 48f
    var seuilElan = 1500f

    private val enCoursEtat = derivedStateOf { reduction > 0f }
    val enCours: Boolean get() = enCoursEtat.value

    /** Le voisin vers lequel on glisse : +1 à droite, −1 à gauche, 0 au repos. */
    private val versEtat = derivedStateOf { -sign(decalage).toInt() }
    val vers: Int get() = versEtat.value

    val echelle: Float get() = 1f - 0.14f * reduction

    /** Le pas entre deux cartes, pour placer les voisins. */
    val pas: Float get() = largeur * echelle + ecart

    private var animation: Job? = null

    fun commencer() {
        animation?.cancel()
        animation = scope.launch { animate(reduction, 1f, animationSpec = tween(160)) { v, _ -> reduction = v } }
    }

    fun glisser(dx: Float, aGauche: Boolean, aDroite: Boolean) {
        // Sans voisin de ce côté, la carte résiste comme un élastique.
        val libre = if (decalage + dx < 0f) aDroite else aGauche
        decalage += if (libre) dx else dx * 0.25f
    }

    /** Le doigt se lève : on entre chez le voisin ([arriver] reçoit le sens) ou l'on revient. */
    fun relacher(vx: Float, aGauche: Boolean, aDroite: Boolean, arriver: (Int) -> Unit) {
        val seuil = largeur * 0.22f
        val sens = when {
            decalage < 0f && aDroite && (decalage < -seuil || vx < -seuilElan) -> 1
            decalage > 0f && aGauche && (decalage > seuil || vx > seuilElan) -> -1
            else -> 0
        }
        animation?.cancel()
        animation = scope.launch {
            if (sens == 0) {
                launch { animate(decalage, 0f, animationSpec = tween(220)) { v, _ -> decalage = v } }
                animate(reduction, 0f, animationSpec = tween(240)) { v, _ -> reduction = v }
            } else {
                animate(decalage, -sens * pas, animationSpec = tween(200)) { v, _ -> decalage = v }
                arriver(sens)
                decalage = 0f
                animate(reduction, 0f, animationSpec = tween(260)) { v, _ -> reduction = v }
            }
        }
    }
}

/**
 * Repère trois doigts qui glissent ensemble à l'horizontale. Il regarde passer les événements avant
 * l'Accueil (passe Initial) et ne les prend qu'une fois le geste reconnu : un doigt seul garde la Cour et le Pouls.
 */
fun Modifier.troisDoigts(
    bascule: Bascule,
    permis: () -> Boolean,
    aGauche: () -> Boolean,
    aDroite: () -> Boolean,
    arriver: (Int) -> Unit,
): Modifier = pointerInput(bascule) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var suivi = false
        var cumul = 0f
        val traceur = VelocityTracker()
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val doigts = e.changes.filter { it.pressed && it.previousPressed }
            if (e.changes.none { it.pressed }) break
            val dx = if (doigts.isEmpty()) 0f else doigts.map { it.position.x - it.previousPosition.x }.average().toFloat()
            if (!suivi) {
                if (doigts.size >= 3 && permis()) {
                    cumul += dx
                    if (abs(cumul) > viewConfiguration.touchSlop) {
                        suivi = true
                        bascule.commencer()
                    }
                } else {
                    cumul = 0f
                }
            } else {
                bascule.glisser(dx, aGauche(), aDroite())
            }
            if (suivi) {
                traceur.addPosition(e.changes.first().uptimeMillis, Offset(bascule.decalage, 0f))
                e.changes.forEach { it.consume() }
            }
        }
        if (suivi) bascule.relacher(traceur.calculateVelocity().x, aGauche(), aDroite(), arriver)
    }
}
