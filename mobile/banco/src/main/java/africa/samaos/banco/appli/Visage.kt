package africa.samaos.banco.appli

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Le visage d'une personne : la photo de sa fiche, sinon ses initiales sur un dégradé tiré de son nom. */
@Composable
fun Visage(nom: String?, photo: String?, taille: Dp, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    var image by remember(photo) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photo) {
        if (photo != null) {
            image = withContext(Dispatchers.IO) {
                try {
                    c.contentResolver.openInputStream(Uri.parse(photo))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    val img = image
    if (img != null) {
        Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(taille).clip(CircleShape))
        return
    }
    val coul = if (nom.isNullOrBlank()) Color(0xFF8F9A95) else couleurDe(nom)
    Box(modifier.size(taille).clip(CircleShape).background(Brush.verticalGradient(listOf(lerp(coul, Color.White, 0.22f), coul))), contentAlignment = Alignment.Center) {
        if (nom.isNullOrBlank()) {
            IconeTrait(Icones.PERSONNE, taille * 0.46f, Color.White.copy(alpha = 0.92f))
        } else {
            BasicText(initiales(nom), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = (taille.value * 0.36f).sp, color = Color.White))
        }
    }
}

