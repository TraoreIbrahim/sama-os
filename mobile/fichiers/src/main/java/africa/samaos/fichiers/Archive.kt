package africa.samaos.fichiers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Les archives .zip : voir ce qu'elles contiennent, les extraire dans un dossier à côté. Une archive piégée ne
 * peut ni écrire hors de ce dossier (« zip slip »), ni remplir le téléphone (« bombe » qui gonfle à l'extraction).
 */
object Archive {
    /**
     * Ce que contient l'archive. [racine] : le dossier qui contient tout, quand il y en a un seul (« Cours/… ») ;
     * on l'extrait alors tel quel, sans l'emboîter dans un dossier du même nom. [piegee] : des chemins qui sortent
     * du dossier (Android refuse de les lire).
     */
    class Contenu(val noms: List<String>, val fichiers: Int, val total: Long, val applis: Int, val racine: String? = null, val piegee: Boolean = false)

    fun lire(c: Context, uri: Uri): Contenu? = try {
        val noms = mutableListOf<String>()
        var total = 0L
        var fichiers = 0
        var applis = 0
        c.contentResolver.openInputStream(uri)?.use { e ->
            ZipInputStream(e).use { z ->
                while (true) {
                    val x = z.nextEntry ?: break
                    if (!x.isDirectory) {
                        fichiers++
                        if (noms.size < 200) noms += x.name
                        if (x.size > 0) total += x.size
                        if (x.name.lowercase().endsWith(".apk")) applis++
                    }
                }
            }
        }
        val premiers = noms.map { it.substringBefore('/', "") }.toSet()
        val racine = premiers.singleOrNull()?.takeIf { it.isNotBlank() && noms.all { n -> n.contains('/') } }
        if (fichiers == 0 && noms.isEmpty()) null else Contenu(noms, fichiers, total, applis, racine)
    } catch (e: java.util.zip.ZipException) {
        if (e.message?.contains("path", true) == true) Contenu(emptyList(), 0, 0, 0, piegee = true) else null
    } catch (_: Exception) {
        null
    }

    /** Un dossier libre à côté de l'archive : « Photos », sinon « Photos (2) »… */
    fun destination(parent: File, nomArchive: String): File {
        val base = nomArchive.substringBeforeLast('.').ifBlank { "Archive" }
        var d = File(parent, base)
        var n = 2
        while (d.exists()) d = File(parent, "$base (${n++})")
        return d
    }

    /** Extraire ; renvoie le nombre de fichiers écrits, ou un message d'échec. */
    fun extraire(c: Context, uri: Uri, dossier: File, racineArchive: String?, avance: (Int) -> Unit): Result<Int> {
        val racine = dossier.canonicalFile
        // Jamais plus que la place libre (moins 200 Mo pour que le téléphone respire), ni plus de 4 Go.
        val plafond = minOf(StatFs(Environment.getExternalStorageDirectory().path).availableBytes - 200_000_000L, 4_000_000_000L)
        var ecrits = 0L
        var n = 0
        return try {
            racine.mkdirs()
            c.contentResolver.openInputStream(uri)?.use { e ->
                ZipInputStream(e).use { z ->
                    val tampon = ByteArray(64 * 1024)
                    while (true) {
                        val x = z.nextEntry ?: break
                        // Le dossier racine de l'archive devient le dossier d'arrivée lui-même.
                        val nom = racineArchive?.let { r -> x.name.removePrefix("$r/") } ?: x.name
                        if (nom.isEmpty()) continue
                        val cible = File(racine, nom).canonicalFile
                        if (!cible.path.startsWith(racine.path + File.separator)) error("Cette archive essaie d'écrire hors de son dossier : elle n'est pas extraite.")
                        if (x.isDirectory) {
                            cible.mkdirs()
                            continue
                        }
                        cible.parentFile?.mkdirs()
                        FileOutputStream(cible).use { s ->
                            while (true) {
                                val lu = z.read(tampon)
                                if (lu < 0) break
                                ecrits += lu
                                if (ecrits > plafond) error("L'archive est trop grosse pour la place libre du téléphone.")
                                s.write(tampon, 0, lu)
                            }
                        }
                        n++
                        avance(n)
                    }
                }
            } ?: error("L'archive ne s'ouvre pas.")
            // Les photos, musiques et documents extraits apparaissent aussitôt dans les autres applis.
            val chemins = racine.walkTopDown().filter { it.isFile }.map { it.path }.toList().toTypedArray()
            android.media.MediaScannerConnection.scanFile(c, chemins, null, null)
            Result.success(n)
        } catch (ex: Exception) {
            // Rien d'à moitié : ce qui a été écrit repart.
            racine.deleteRecursively()
            Result.failure(ex)
        }
    }
}

/** La feuille d'une archive : ce qu'elle contient, puis « Extraire ici ». */
@Composable
fun FeuilleArchive(f: Fichier, fermer: () -> Unit, ouvrirDossier: (File) -> Unit, dire: (String) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    val uri = remember(f) { Uri.fromFile(File(f.chemin)) }
    var contenu by remember { mutableStateOf<Archive.Contenu?>(null) }
    var lu by remember { mutableStateOf(false) }
    var enCours by remember { mutableStateOf(false) }
    var faits by remember { mutableIntStateOf(0) }
    LaunchedEffect(f) {
        contenu = withContext(Dispatchers.IO) { Archive.lire(c, uri) }
        lu = true
    }
    FeuilleAppli(fermer = { if (!enCours) fermer() }, titre = f.nom) {
        val x = contenu
        val texte = when {
            !lu -> "Lecture de l'archive…"
            x == null -> "Cette archive est abîmée ou n'est pas un .zip : elle ne s'ouvre pas."
            x.piegee -> "Cette archive essaie d'écrire des fichiers hors de son dossier, ce que font les archives piégées. Sama ne l'ouvre pas."
            enCours -> "Extraction… $faits sur ${x.fichiers} fichiers"
            else -> buildString {
                append("${x.fichiers} fichier${if (x.fichiers > 1) "s" else ""}")
                if (x.total > 0) append(" · ${taille(x.total)} une fois extraits")
                append("\n")
                append(x.noms.take(6).joinToString("\n") { "· " + it.substringAfterLast('/') })
                if (x.fichiers > 6) append("\n· et ${x.fichiers - 6} autres")
                if (x.applis > 0) append("\n\nElle contient une appli : son installation passera par la mise en garde habituelle.")
            }
        }
        BasicText(texte, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 22.sp, color = a.encre2))
        if (x != null && !x.piegee && !enCours) {
            val nomDossier = x.racine ?: f.nom.substringBeforeLast('.')
            ActionFeuille(Icones.DOSSIER, "Extraire ici", second = "Dans un dossier « $nomDossier » à côté de l'archive") {
                enCours = true
                portee.launch {
                    val parent = File(f.chemin).parentFile ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val dossier = Archive.destination(parent, (x.racine ?: f.nom.substringBeforeLast('.')) + ".")
                    val r = withContext(Dispatchers.IO) { Archive.extraire(c, uri, dossier, x.racine) { n -> faits = n } }
                    enCours = false
                    r.onSuccess { n ->
                        fermer()
                        dire("$n fichier${if (n > 1) "s" else ""} extrait${if (n > 1) "s" else ""} dans « ${dossier.name} »")
                        ouvrirDossier(dossier)
                    }.onFailure { e ->
                        fermer()
                        dire(e.message ?: "L'extraction n'a pas abouti.")
                    }
                }
            }
        }
    }
}
