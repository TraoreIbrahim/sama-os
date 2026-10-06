package africa.samaos.accueil

import africa.samaos.banco.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** L'éléphant de Sama, dessiné sur une grille de 100 comme dans le prototype. */
@Composable
fun Elephant(taille: Dp, couleur: Color, fond: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(taille)) {
        val s = size.width / 100f
        drawCircle(couleur, 21 * s, Offset(25 * s, 40 * s))
        drawCircle(couleur, 21 * s, Offset(75 * s, 40 * s))
        drawOval(couleur, topLeft = Offset(31 * s, 17 * s), size = Size(38 * s, 46 * s))
        drawOval(fond, topLeft = Offset(31 * s, 17 * s), size = Size(38 * s, 46 * s), style = Stroke(4 * s))
        val trompe = Path().apply {
            moveTo(50 * s, 58 * s)
            cubicTo(50 * s, 70 * s, 63 * s, 66 * s, 63 * s, 77 * s)
            cubicTo(63 * s, 88 * s, 47 * s, 90 * s, 42 * s, 83 * s)
        }
        drawPath(trompe, couleur, style = Stroke(10 * s, cap = StrokeCap.Round))
        drawCircle(fond, 2.6f * s, Offset(43 * s, 36 * s))
        drawCircle(fond, 2.6f * s, Offset(57 * s, 36 * s))
    }
}
