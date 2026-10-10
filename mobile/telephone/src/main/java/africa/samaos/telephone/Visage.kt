package africa.samaos.telephone

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.couleurDe
import africa.samaos.banco.appli.initiales
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Le sol du clavier : blanc sous le jour, un peu plus clair que le fond la nuit ; ses touches, et la crête qui le borde. */
object Sol {
    @Composable fun sol() = if (LocalIdentite.current.sombre) Color(0xFF18241F) else Color.White
    @Composable fun touche() = if (LocalIdentite.current.sombre) Color(0xFF22322B) else Color(0xFFEAF1ED)
    @Composable fun crete() = if (LocalIdentite.current.sombre) Color(0xFF15201B) else Color(0xFFDCE7E1)
}

val VERT_APPEL = Brush.verticalGradient(listOf(Color(0xFF5FCB8C), Color(0xFF2A7F58)))

/** Une petite étiquette arrondie : l'opérateur, « Jamais signalé », « Arnaque signalée »… */
@Composable
fun Etiquette(texte: String, fond: Color, encre: Color, icone: String? = null, point: Color? = null, grande: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(if (grande) 18.dp else 14.dp)).background(fond).padding(horizontal = if (grande) 16.dp else 11.dp, vertical = if (grande) 8.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (point != null) Box(Modifier.size(8.dp).clip(CircleShape).background(point))
        if (icone != null) IconeTrait(icone, if (grande) 18.dp else 14.dp, encre, epaisseur = 2f)
        BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = if (grande) 15.sp else 13.sp, color = encre))
    }
}

/** L'étiquette de l'opérateur d'un numéro, à sa couleur. */
@Composable
fun EtiquetteOperateur(op: String) {
    val a = LocalIdentite.current
    val (fond, encre, point) = when {
        op.startsWith("Orange") -> Triple(Color(0xFFFFF1E2), Color(0xFFB5651D), Color(0xFFF58220))
        op.startsWith("MTN") -> Triple(Color(0xFFFFF6D6), Color(0xFF8A6A00), Color(0xFFFFCB05))
        op.startsWith("Moov") -> Triple(Color(0xFFE6F0FA), Color(0xFF2F5E9E), Color(0xFF1F5AA6))
        else -> Triple(a.champ, a.encre2, null)
    }
    if (a.sombre) Etiquette(op, point?.copy(alpha = 0.18f) ?: a.champ, point?.let { lerp(it, Color.White, 0.35f) } ?: a.encre2, point = point)
    else Etiquette(op, fond, encre, point = point)
}

const val ICONE_ALERTE = "M12 4l9 16H3z M12 10v4 M12 17h.01"
const val ICONE_PERSONNES = "M9 8a3 3 0 1 0 0 6a3 3 0 1 0 0-6z M3 20c1-3 3.5-4.5 6-4.5s5 1.5 6 4.5 M16 6a3 3 0 0 1 0 6 M18 15.5c1.5.6 2.6 2 3 4.5"
const val ICONE_BLOQUER = "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M6.5 6.5l11 11"
const val ICONE_BOUCLIER = "M12 3l7 3v5c0 4.5-3 8.5-7 10c-4-1.5-7-5.5-7-10V6z"
const val ICONE_EFFACER = "M9 6h11v12H9l-5-6z M12.5 9.5l5 5 M17.5 9.5l-5 5"
const val ICONE_RACCROCHER = "M3 14c5-5 13-5 18 0l-2.5 2.5l-3-1.5v-2.5a10 10 0 0 0-7 0v2.5l-3 1.5z"
const val ICONE_MESSAGERIE = "M7 15a4 4 0 1 0 0-8a4 4 0 1 0 0 8z M17 15a4 4 0 1 0 0-8a4 4 0 1 0 0 8z M7 15h10"
