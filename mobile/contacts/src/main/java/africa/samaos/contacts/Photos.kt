package africa.samaos.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.couleurDe
import africa.samaos.banco.appli.initiales
import androidx.core.content.res.ResourcesCompat
import java.io.ByteArrayOutputStream

/** Le visage d'un contact : sa photo, sinon ses initiales sur un dégradé tiré de son nom. */
@Composable
fun PhotoContact(nom: String?, photo: ImageBitmap?, taille: Dp, modifier: Modifier = Modifier) {
    if (photo != null) {
        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(taille).clip(CircleShape))
        return
    }
    val c = if (nom.isNullOrBlank()) Color(0xFF8F8476) else couleurDe(nom)
    Box(
        modifier.size(taille).clip(CircleShape).background(Brush.verticalGradient(listOf(lerp(c, Color.White, 0.22f), c))),
        contentAlignment = Alignment.Center,
    ) {
        if (nom.isNullOrBlank()) {
            IconeTrait(Icones.PERSONNE, taille * 0.46f, Color.White.copy(alpha = 0.9f))
            return@Box
        }
        BasicText(
            initiales(nom),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = (taille.value * 0.36f).sp, color = Color.White),
        )
    }
}

/** Les avatars qu'on fabrique quand on n'a pas de photo : des initiales, un motif ou un emoji, sur une couleur. */
object Avatars {
    enum class Genre(val nom: String) { INITIALES("Initiales"), MOTIF("Motif"), EMOJI("Emoji") }

    val COULEURS = listOf(
        Color(0xFFC2774A), Color(0xFFB5532F), Color(0xFF8C4F7D), Color(0xFF3D5A99),
        Color(0xFF2E8A6E), Color(0xFF5B6B2E), Color(0xFFD9A11E), Color(0xFF1C2233),
    )

    val EMOJIS = listOf("🌺", "🌻", "⭐", "❤️", "👑", "🎓", "⚽", "🎵", "🌴", "🐘", "🦁", "🌙")

    /** Le motif : 0 rayures, 1 cercles, 2 points. */
    const val MOTIFS = 3

    /** L'avatar, en image carrée (Android l'arrondit où il le montre). */
    fun rendre(c: Context, genre: Genre, couleur: Color, texte: String, emoji: String, motif: Int, taille: Int = 512): Bitmap {
        val b = Bitmap.createBitmap(taille, taille, Bitmap.Config.ARGB_8888)
        val k = Canvas(b)
        val t = taille.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val clair = lerp(couleur, Color.White, 0.25f).toArgb()
        p.shader = LinearGradient(0f, 0f, 0f, t, clair, couleur.toArgb(), Shader.TileMode.CLAMP)
        k.drawRect(0f, 0f, t, t, p)
        p.shader = null
        if (genre == Genre.MOTIF) {
            p.color = lerp(couleur, Color.White, 0.35f).toArgb()
            p.alpha = 90
            when (motif % MOTIFS) {
                0 -> {
                    p.strokeWidth = t / 14f
                    var x = -t
                    while (x < t * 2) {
                        k.drawLine(x, t, x + t, 0f, p)
                        x += t / 7f
                    }
                }
                1 -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = t / 22f
                    var r = t / 9f
                    while (r < t) {
                        k.drawCircle(t / 2, t / 2, r, p)
                        r += t / 8f
                    }
                    p.style = Paint.Style.FILL
                }
                else -> {
                    val pas = t / 7f
                    for (i in 0..7) for (j in 0..7) k.drawCircle(i * pas + (if (j % 2 == 0) 0f else pas / 2), j * pas, t / 40f, p)
                }
            }
            p.alpha = 255
        }
        p.textAlign = Paint.Align.CENTER
        if (genre == Genre.EMOJI) {
            p.textSize = t * 0.48f
            val m = p.fontMetrics
            k.drawText(emoji, t / 2, t / 2 - (m.ascent + m.descent) / 2, p)
        } else {
            p.color = android.graphics.Color.WHITE
            p.textSize = t * 0.36f
            p.typeface = try {
                val noto = ResourcesCompat.getFont(c, africa.samaos.banco.R.font.noto_sans)
                if (Build.VERSION.SDK_INT >= 28) Typeface.create(noto, 600, false) else Typeface.create(noto, Typeface.BOLD)
            } catch (_: Exception) {
                Typeface.DEFAULT_BOLD
            }
            val m = p.fontMetrics
            k.drawText(texte, t / 2, t / 2 - (m.ascent + m.descent) / 2, p)
        }
        return b
    }

    fun jpeg(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()

    /** Une photo prise ou choisie : recadrée au carré, au centre, et réduite à 720 points de côté. */
    fun depuisPhoto(c: Context, uri: Uri): Bitmap? = try {
        val image = if (Build.VERSION.SDK_INT >= 28) decoder(c, uri) else c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        if (image == null) null else {
            val cote = minOf(image.width, image.height)
            val carre = Bitmap.createBitmap(image, (image.width - cote) / 2, (image.height - cote) / 2, cote, cote)
            if (cote > 720) Bitmap.createScaledBitmap(carre, 720, 720, true) else carre
        }
    } catch (_: Exception) {
        null
    }

    @androidx.annotation.RequiresApi(28)
    private fun decoder(c: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(c.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decodeur, info, _ ->
            decodeur.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val cote = minOf(info.size.width, info.size.height)
            val echelle = (720f / cote).coerceAtMost(1f)
            decodeur.setTargetSize((info.size.width * echelle).toInt().coerceAtLeast(1), (info.size.height * echelle).toInt().coerceAtLeast(1))
        }
    }
}
