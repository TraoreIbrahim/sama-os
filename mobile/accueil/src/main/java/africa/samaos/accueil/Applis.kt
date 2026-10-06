package africa.samaos.accueil

import africa.samaos.banco.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale

/** Une appli lançable, telle que la Cour et l'Accueil l'affichent. */
data class Appli(
    val nom: String,
    val paquet: String,
    val activite: String,
    val icone: ImageBitmap,
)

/** Toutes les applis qui ont une entrée dans le lanceur, triées à la française. */
fun chargerApplis(contexte: Context): List<Appli> {
    val pm = contexte.packageManager
    val taille = (60 * contexte.resources.displayMetrics.density).toInt()
    val tri = Collator.getInstance(Locale.FRENCH)
    val toutes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    // Quand une appli de Sama est là, l'appli d'Android qu'elle remplace n'est plus montrée (elle reste installée).
    val presents = toutes.map { it.activityInfo.packageName }.toSet()
    val cachees = REMPLACEES.filterKeys { it in presents }.values.toSet()
    return toutes
        .filter { it.activityInfo.packageName != contexte.packageName }
        .filter { it.activityInfo.packageName !in cachees }
        .map { ri ->
            Appli(
                nom = ri.loadLabel(pm).toString(),
                paquet = ri.activityInfo.packageName,
                activite = ri.activityInfo.name,
                icone = ri.loadIcon(pm).toBitmap(taille, taille).asImageBitmap(),
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
