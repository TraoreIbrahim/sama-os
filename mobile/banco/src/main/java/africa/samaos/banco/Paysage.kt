package africa.samaos.banco

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** Position de l'astre dans le repère 390 × 844 des maquettes. */
data class Astre(val x: Float, val y: Float, val rayon: Float)

/**
 * Le ciel de Banco : l'astre et jusqu'à quatre collines, dessinés dans le repère
 * 390 × 844 du prototype puis étirés à la taille de l'écran.
 */
@Composable
fun Paysage(
    modifier: Modifier = Modifier,
    astre: Astre = Astre(286f, 430f, 70f),
    collines: List<Float> = listOf(480f, 574f, 664f, 754f),
) {
    val b = LocalBanco.current
    Canvas(modifier.fillMaxSize()) { dessinerPaysage(b, astre, collines) }
}

/**
 * Le paysage de l'Accueil, qui bouge avec la Cour : le soleil monte, les collines passent derrière la crête.
 * [progres] n'est lu qu'au dessin, pour ne pas recomposer l'écran à chaque image du geste.
 */
@Composable
fun PaysageMobile(astre: Astre, progres: () -> Float, modifier: Modifier = Modifier) {
    val b = LocalBanco.current
    Canvas(modifier.fillMaxSize()) {
        val c = progres()
        dessinerPaysage(
            b,
            Astre(melange(astre.x, 296f, c), melange(astre.y, 120f, c), melange(astre.rayon, 52f, c)),
            listOf(480f, 574f, 664f, 754f).mapIndexed { i, y -> melange(y, 150f + 24f * i, c) },
        )
    }
}

private fun DrawScope.dessinerPaysage(b: Banco, astre: Astre, collines: List<Float>) {
    val couleurs = listOf(b.colline1, b.colline2, b.colline3, b.colline4)
    val sx = size.width / 390f
    val sy = size.height / 844f
    drawRect(b.ciel)
    drawCircle(b.astre, radius = astre.rayon * sx, center = Offset(astre.x * sx, astre.y * sy))
    collines.forEachIndexed { i, y -> drawPath(colline(y, sx, sy), couleurs[i]) }
}

/** Une colline : « M0 y C 70 y-20, 140 y-14, 200 y-4 S 320 y-22, 390 y-10 » fermée vers le bas. */
private fun colline(y: Float, sx: Float, sy: Float) = Path().apply {
    moveTo(0f, y * sy)
    cubicTo(70 * sx, (y - 20) * sy, 140 * sx, (y - 14) * sy, 200 * sx, (y - 4) * sy)
    cubicTo(260 * sx, (y + 6) * sy, 320 * sx, (y - 22) * sy, 390 * sx, (y - 10) * sy)
    lineTo(390 * sx, 844 * sy)
    lineTo(0f, 844 * sy)
    close()
}

/** La crête du sol : la strate colline-2 qui dépasse, puis le bord ondulé du sol. */
@Composable
fun Crete(modifier: Modifier = Modifier) {
    val b = LocalBanco.current
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val sx = size.width / 390f
        val sy = size.height / 40f
        drawPath(Path().apply {
            moveTo(0f, 16 * sy)
            cubicTo(60 * sx, 4 * sy, 140 * sx, 2 * sy, 210 * sx, 12 * sy)
            cubicTo(280 * sx, 22 * sy, 330 * sx, 24 * sy, 390 * sx, 8 * sy)
            lineTo(390 * sx, 40 * sy)
            lineTo(0f, 40 * sy)
            close()
        }, b.colline2)
        drawPath(Path().apply {
            moveTo(0f, 30 * sy)
            cubicTo(80 * sx, 16 * sy, 160 * sx, 14 * sy, 230 * sx, 24 * sy)
            cubicTo(300 * sx, 34 * sy, 340 * sx, 36 * sy, 390 * sx, 20 * sy)
            lineTo(390 * sx, 40 * sy)
            lineTo(0f, 40 * sy)
            close()
        }, b.sol)
    }
}

fun melange(a: Float, b: Float, t: Float) = a + (b - a) * t
