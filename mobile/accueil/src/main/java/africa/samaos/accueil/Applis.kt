package africa.samaos.accueil

import africa.samaos.banco.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter as FiltreCompose
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Une appli lançable, telle que la Cour et l'Accueil l'affichent. */
data class Appli(
    val nom: String,
    val paquet: String,
    val activite: String,
    val icone: ImageBitmap,
    val ombre: Ombre? = null,
)

/** L'ombre douce d'une icône, tirée de sa forme (le masque d'Android) : [x], [y], où la poser ; [taille] de l'icône. */
class Ombre(val image: ImageBitmap, val x: Int, val y: Int, val taille: Int)

/** Pose sous l'icône son ombre douce, un peu plus bas, comme un objet sur le sol. */
fun Modifier.ombre(a: Appli): Modifier {
    val o = a.ombre ?: return this
    return drawBehind {
        val k = size.width / o.taille
        scale(k, pivot = Offset.Zero) {
            drawImage(o.image, topLeft = Offset(o.x.toFloat(), o.y + 2.dp.toPx() / k), colorFilter = FiltreCompose.tint(Color(0x333C2814)))
        }
    }
}

private fun ombreDe(b: Bitmap, rayon: Float): Ombre {
    val decalage = IntArray(2)
    val a = b.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(rayon, BlurMaskFilter.Blur.NORMAL) }, decalage)
    return Ombre(a.asImageBitmap(), decalage[0], decalage[1], b.width)
}

/** Les applis d'Android que la Cour ne montre jamais : la recherche d'Android (la Cour cherche déjà). */
private val JAMAIS = setOf("com.android.quicksearchbox")

/** Le menu de l'opérateur sur la SIM : montré sous le nom et l'icône de Sama. */
private const val SERVICES_SIM = "com.android.stk"
private const val AGENDA = "africa.samaos.agenda"

/** L'icône de l'Agenda, comme une page de calendrier : le jour de la semaine et la date du jour. */
private class DateDuJour(private val c: Context) : Drawable() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        val u = b.width() / 108f
        val jour = LocalDate.now()
        val police = ResourcesCompat.getFont(c, africa.samaos.banco.R.font.noto_sans)
        fun poids(p: Int) = if (Build.VERSION.SDK_INT >= 28) Typeface.create(police, p, false) else police
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = poids(600)
            color = 0xFFD9542F.toInt()
            textSize = 10f * u
            letterSpacing = 0.04f
        }
        canvas.drawText(jour.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).uppercase(Locale.FRENCH), b.left + 54f * u, b.top + 43.3f * u, p)
        p.typeface = poids(300)
        p.color = 0xFF1F1C18.toInt()
        p.textSize = 31f * u
        p.letterSpacing = 0f
        canvas.drawText(jour.dayOfMonth.toString(), b.left + 54f * u, b.top + 71.5f * u, p)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Toujours translucide", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** L'icône d'une appli : celle d'Android, sauf pour l'Agenda (la date du jour) et le menu de la SIM. */
private fun iconeDe(c: Context, paquet: String, android: () -> Drawable): Drawable = when (paquet) {
    AGENDA -> AdaptiveIconDrawable(c.getDrawable(R.drawable.icone_agenda_fond), DateDuJour(c))
    SERVICES_SIM -> AdaptiveIconDrawable(c.getDrawable(R.drawable.icone_services_sim_fond), c.getDrawable(R.drawable.icone_services_sim_dessin))
    else -> android()
}

/** Toutes les applis qui ont une entrée dans le lanceur, triées à la française. */
fun chargerApplis(contexte: Context): List<Appli> {
    val pm = contexte.packageManager
    val taille = (60 * contexte.resources.displayMetrics.density).toInt()
    val tri = Collator.getInstance(Locale.FRENCH)
    val toutes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    // Quand une appli de Sama est là, l'appli d'Android qu'elle remplace n'est plus montrée (elle reste installée).
    val presents = toutes.map { it.activityInfo.packageName }.toSet()
    val cachees = REMPLACEES.filterKeys { it in presents }.values.toSet()
    val rayon = 3f * contexte.resources.displayMetrics.density
    return toutes
        .filter { it.activityInfo.packageName != contexte.packageName }
        .filter { it.activityInfo.packageName !in cachees && it.activityInfo.packageName !in JAMAIS }
        .map { ri ->
            val paquet = ri.activityInfo.packageName
            val image = iconeDe(contexte, paquet) { ri.loadIcon(pm) }.toBitmap(taille, taille, Bitmap.Config.ARGB_8888)
            Appli(
                nom = if (paquet == SERVICES_SIM) "Services SIM" else ri.loadLabel(pm).toString(),
                paquet = paquet,
                activite = ri.activityInfo.name,
                icone = image.asImageBitmap(),
                ombre = ombreDe(image, rayon),
            )
        }
        .sortedWith { a, b -> tri.compare(a.nom, b.nom) }
}

/** Les quatre applis de la Natte : téléphone, SMS, navigateur et appareil photo par défaut. */
fun applisDeLaNatte(contexte: Context, applis: List<Appli>): List<Appli> {
    val intents = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://samaos.africa")),
        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
    )
    return intents
        .mapNotNull { paquetParDefaut(contexte, it) }
        .mapNotNull { paquet -> applis.firstOrNull { it.paquet == paquet } }
        .distinct()
}

/** Les applis que l'Accueil de Maison montre d'abord, quand elles sont installées. */
val PREFEREES_MAISON = listOf(
    "com.google.android.apps.photos",
    "com.google.android.deskclock",
    "com.google.android.calendar",
    "com.google.android.contacts",
    "com.google.android.apps.nbu.files",
    "com.google.android.apps.maps",
    "com.android.vending",
    REGLAGES_SAMA,
    "com.android.settings",
)

/** Les applis de l'Accueil : d'abord celles que l'Espace préfère, complétées par ordre alphabétique. */
fun applisDeLAccueil(applis: List<Appli>, natte: List<Appli>, preferees: List<String>, nombre: Int = 8): List<Appli> {
    val restantes = applis.filterNot { it in natte }
    val choisies = preferees.mapNotNull { p -> restantes.firstOrNull { it.paquet == p } }
    return (choisies + restantes.filterNot { it in choisies }).take(nombre)
}

private fun paquetParDefaut(contexte: Context, intent: Intent): String? =
    contexte.packageManager
        .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
        ?.takeIf { it != "android" }

fun lancer(contexte: Context, appli: Appli) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(appli.paquet, appli.activite)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try {
        contexte.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // L'appli vient d'être désinstallée : la liste se met à jour au prochain retour sur l'Accueil.
    }
}

const val REGLAGES_SAMA = "africa.samaos.reglages"
const val PARAMETRES_ANDROID = "com.android.settings"

/** Les applis de Sama et celles d'Android qu'elles remplacent. */
val REMPLACEES = mapOf(
    REGLAGES_SAMA to PARAMETRES_ANDROID,
    "africa.samaos.telephone" to "com.android.dialer",
    "africa.samaos.contacts" to "com.android.contacts",
    "africa.samaos.messages" to "com.android.messaging",
    "africa.samaos.horloge" to "com.android.deskclock",
    "africa.samaos.calculatrice" to "com.android.calculator2",
    "africa.samaos.agenda" to "com.android.calendar",
    "africa.samaos.fichiers" to "com.android.documentsui",
    "africa.samaos.photos" to "com.android.gallery3d",
    "africa.samaos.lecteur" to "com.android.music",
    "africa.samaos.griot" to "org.chromium.webview_shell",
    "africa.samaos.appareil" to "com.android.camera2",
)
