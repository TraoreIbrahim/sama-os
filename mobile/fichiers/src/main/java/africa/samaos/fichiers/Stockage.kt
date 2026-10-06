package africa.samaos.fichiers

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.Color
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Un fichier ou un dossier. `source` dit d'où il vient (« depuis Messages »). */
data class Fichier(
    val chemin: String,
    val nom: String,
    val taille: Long,
    val date: Long,
    val dossier: Boolean = false,
    val enfants: Int = 0,
    val id: Long? = null,
    val source: String? = null,
) {
    val extension get() = nom.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mime: String? get() = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    val genre: Genre get() = Genre.de(this)
}

enum class Genre { DOSSIER, IMAGE, VIDEO, AUDIO, PDF, TEXTE, TABLEUR, PRESENTATION, APPLI, ARCHIVE, AUTRE;
    companion object {
        fun de(f: Fichier): Genre {
            if (f.dossier) return DOSSIER
            val m = f.mime.orEmpty()
            return when {
                m.startsWith("image/") -> IMAGE
                m.startsWith("video/") -> VIDEO
                m.startsWith("audio/") -> AUDIO
                f.extension == "pdf" -> PDF
                f.extension in setOf("doc", "docx", "odt", "rtf", "txt", "md") -> TEXTE
                f.extension in setOf("xls", "xlsx", "ods", "csv") -> TABLEUR
                f.extension in setOf("ppt", "pptx", "odp") -> PRESENTATION
                f.extension in setOf("apk", "apks", "xapk") -> APPLI
                f.extension in setOf("zip", "rar", "7z", "tar", "gz") -> ARCHIVE
                else -> AUTRE
            }
        }
    }
}

/** L'étiquette d'un document (« PDF » sur fond rouge…), comme dans les maquettes. */
fun etiquette(g: Genre): Pair<String, Color>? = when (g) {
    Genre.PDF -> "PDF" to Color(0xFFA3322A)
    Genre.TEXTE -> "DOC" to Color(0xFF3D5A99)
    Genre.TABLEUR -> "XLS" to Color(0xFF2F6B57)
    Genre.PRESENTATION -> "PPT" to Color(0xFFB5532F)
    Genre.APPLI -> "APK" to Color(0xFF5B3F6E)
    Genre.ARCHIVE -> "ZIP" to Color(0xFF6E6154)
    else -> null
}

enum class Categorie(val nom: String) { IMAGES("Images"), VIDEOS("Vidéos"), AUDIO("Audio"), DOCUMENTS("Documents"), APPLIS("Applis"), RECUS("Reçus") }

class EtatStockage(
    val total: Long, val libre: Long,
    val videos: Long, val images: Long, val audio: Long, val applis: Long, val systeme: Long, val autres: Long,
    val documents: Long, val recus: Long, val caches: Long,
)

enum class Tri(val nom: String) { NOM("Nom · dossiers d'abord"), DATE("Plus récents d'abord"), TAILLE("Plus gros d'abord") }

class Telechargement(val id: Long, val titre: String, val fait: Long, val total: Long, val enPause: Boolean, val enAttenteWifi: Boolean)

fun taille(o: Long): String = when {
    o >= 1_000_000_000 -> String.format(Locale.FRENCH, "%.1f Go", o / 1e9)
    o >= 10_000_000 -> "${o / 1_000_000} Mo"
    o >= 1_000_000 -> String.format(Locale.FRENCH, "%.1f Mo", o / 1e6)
    o >= 1_000 -> "${o / 1_000} Ko"
    else -> "$o octets"
}

/** « aujourd'hui », « hier », « 14 sept. », « 14 sept. 2025 ». */
fun quand(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val auj = LocalDate.now()
    return when {
        d == auj -> "aujourd'hui"
        d == auj.minusDays(1) -> "hier"
        d.year == auj.year -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
        else -> d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH))
    }
}

object Stockage {
    val racine: File get() = Environment.getExternalStorageDirectory()
    private val FICHIERS = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun etat(c: Context): EtatStockage? = try {
        val ssm = c.getSystemService(StorageStatsManager::class.java)
        val uuid = StorageManager.UUID_DEFAULT
        val total = ssm.getTotalBytes(uuid)
        val libre = ssm.getFreeBytes(uuid)
        val ext = ssm.queryExternalStatsForUser(uuid, Process.myUserHandle())
        val user = ssm.queryStatsForUser(uuid, Process.myUserHandle())
        val applis = (user.appBytes + user.dataBytes - ext.totalBytes.coerceAtMost(user.dataBytes)).coerceAtLeast(0)
        val autres = (ext.totalBytes - ext.videoBytes - ext.imageBytes).coerceAtLeast(0)
        val systeme = (total - libre - ext.videoBytes - ext.imageBytes - applis - autres).coerceAtLeast(0)
        EtatStockage(
            total, libre, ext.videoBytes, ext.imageBytes, ext.audioBytes, applis, systeme, autres,
            somme(c, DOCUMENTS, null), dossierTaille(File(racine, Environment.DIRECTORY_DOWNLOADS)), user.cacheBytes,
        )
    } catch (_: Exception) {
        null
    }

    private const val DOCUMENTS = "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/pdf' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/msword' " +
        "OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/vnd.%' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%')"

    private fun somme(c: Context, choix: String, args: Array<String>?): Long = try {
        c.contentResolver.query(FICHIERS, arrayOf("SUM(${MediaStore.Files.FileColumns.SIZE})"), choix, args, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else 0 } ?: 0
    } catch (_: Exception) {
        // Les fonctions SQL ne passent plus dans la projection : on additionne à la main.
        var s = 0L
        c.contentResolver.query(FICHIERS, arrayOf(MediaStore.Files.FileColumns.SIZE), choix, args, null)?.use { while (it.moveToNext()) s += it.getLong(0) }
        s
    }

    private fun dossierTaille(d: File): Long = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // ——— Parcourir ———

    fun lister(d: File, tri: Tri, caches: Boolean): List<Fichier> {
        val l = (d.listFiles() ?: emptyArray())
            .filter { caches || !it.name.startsWith(".") }
            .map { f ->
                Fichier(
                    f.absolutePath, f.name, if (f.isDirectory) 0 else f.length(), f.lastModified(), f.isDirectory,
                    if (f.isDirectory) (f.list()?.count { caches || !it.startsWith(".") } ?: 0) else 0,
                )
            }
        return when (tri) {
            Tri.NOM -> {
                // L'ordre de l'alphabet français : « École » avec les E, pas après le Z.
                val ordre = java.text.Collator.getInstance(Locale.FRENCH).apply { strength = java.text.Collator.PRIMARY }
                l.sortedWith(compareBy<Fichier> { !it.dossier }.thenComparator { x, y -> ordre.compare(x.nom, y.nom) })
            }
            Tri.DATE -> l.sortedWith(compareBy<Fichier>({ !it.dossier }).thenByDescending { it.date })
            Tri.TAILLE -> l.sortedWith(compareBy<Fichier>({ !it.dossier }).thenByDescending { it.taille })
        }
    }

    /** Le nom lisible d'un dossier du téléphone. */
    fun nomDossier(d: File): String = when (d.absolutePath) {
        racine.absolutePath -> "Ce téléphone"
        else -> when (d.name) {
            Environment.DIRECTORY_DOWNLOADS -> "Téléchargements"
            Environment.DIRECTORY_DOCUMENTS -> "Documents"
            Environment.DIRECTORY_PICTURES -> "Images"
            Environment.DIRECTORY_MUSIC -> "Musique"
            Environment.DIRECTORY_MOVIES -> "Films"
            Environment.DIRECTORY_DCIM -> "Appareil photo"
            Environment.DIRECTORY_RECORDINGS -> "Enregistrements"
            else -> d.name
        }
    }

    // ——— Les sortes de fichiers, par MediaStore ———

    private val COLONNES = arrayOf(
        MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.OWNER_PACKAGE_NAME,
        MediaStore.Files.FileColumns.DATE_ADDED,
    )

    private fun requete(c: Context, choix: String, args: Array<String>? = null, ordre: String = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC", source: Boolean = false): List<Fichier> {
        val l = mutableListOf<Fichier>()
        try {
            c.contentResolver.query(FICHIERS, COLONNES, choix, args, ordre)?.use { cur ->
                while (cur.moveToNext()) {
                    val chemin = cur.getString(1) ?: continue
                    l += Fichier(
                        chemin, cur.getString(2) ?: File(chemin).name, cur.getLong(3), cur.getLong(4) * 1000, id = cur.getLong(0),
                        source = if (source) cur.getString(5) else null,
                    )
                }
            }
        } catch (_: Exception) {
        }
        return l
    }

    fun categorie(c: Context, cat: Categorie): List<Fichier> = when (cat) {
        Categorie.IMAGES -> requete(c, "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}")
        Categorie.VIDEOS -> requete(c, "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}")
        Categorie.AUDIO -> requete(c, "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}")
        Categorie.DOCUMENTS -> requete(c, DOCUMENTS)
        else -> emptyList()
    }

    fun rechercher(c: Context, texte: String): List<Fichier> =
        if (texte.isBlank()) emptyList()
        else requete(c, "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL", arrayOf("%${texte.trim()}%")).take(200)

    // ——— Les reçus : téléchargés, reçus à proximité, reçus par les messageries ———

    fun recus(c: Context): List<Fichier> {
        val sites = sitesDe(c)
        val p = MediaStore.Files.FileColumns.RELATIVE_PATH
        return requete(
            c,
            "($p LIKE 'Download/%' OR $p LIKE 'Bluetooth/%' OR $p LIKE 'Android/media/%') AND ${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL",
            ordre = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC", source = true,
        ).map { f -> f.copy(source = sites[f.id]?.let { "depuis $it" } ?: origine(c, f)) }
    }

    /** Pour chaque téléchargement, le site d'où il vient (seule la collection Téléchargements le garde). */
    private fun sitesDe(c: Context): Map<Long, String> {
        val m = mutableMapOf<Long, String>()
        try {
            c.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DOWNLOAD_URI), null, null, null,
            )?.use { cur ->
                while (cur.moveToNext()) cur.getString(1)?.let { Uri.parse(it).host }?.let { m[cur.getLong(0)] = it.removePrefix("www.") }
            }
        } catch (_: Exception) {
        }
        return m
    }

    fun aProximite(f: Fichier) = f.source == PROXIMITE

    private const val PROXIMITE = "reçu à proximité"

    /** D'où vient un fichier : l'appli qui l'a posé, ou le Bluetooth. */
    private fun origine(c: Context, f: Fichier): String? {
        val paquet = f.source
        return when {
            paquet?.startsWith("site:") == true -> "depuis " + paquet.removePrefix("site:").removePrefix("www.")
            paquet == "com.android.bluetooth" || f.chemin.contains("/Bluetooth/") -> PROXIMITE
            paquet == null || paquet == "com.android.providers.downloads" || paquet == "com.android.providers.media.module" -> null
            else -> try {
                "depuis " + c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(paquet, 0))
            } catch (_: Exception) {
                null
            }
        }
    }

    // ——— Les téléchargements en cours (fournisseur de téléchargements d'Android) ———

    private val TOUS = Uri.parse("content://downloads/all_downloads")

    fun enCours(c: Context): List<Telechargement> {
        val l = mutableListOf<Telechargement>()
        try {
            c.contentResolver.query(TOUS, arrayOf("_id", "title", "current_bytes", "total_bytes", "status", "control"), "status < 200 AND deleted = 0 AND visibility != 2 AND is_visible_in_downloads_ui != 0", null, "_id DESC")
                ?.use { cur ->
                    while (cur.moveToNext()) {
                        val statut = cur.getInt(4)
                        if (cur.getString(1).isNullOrBlank()) continue
                        l += Telechargement(cur.getLong(0), cur.getString(1).orEmpty(), cur.getLong(2), cur.getLong(3), cur.getInt(5) == 1 || statut == 193, statut == 196)
                    }
                }
        } catch (_: Exception) {
        }
        return l
    }

    /** Mettre en pause ou reprendre (la colonne « control » que DownloadManager laisse changer). */
    fun pause(c: Context, id: Long, oui: Boolean) = try {
        c.contentResolver.update(ContentUris.withAppendedId(TOUS, id), ContentValues().apply { put("control", if (oui) 1 else 0) }, null, null)
    } catch (_: Exception) {
        0
    }

    fun parData(c: Context) = try {
        c.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
    } catch (_: SecurityException) {
        false
    }

    /** « Attendre le Wi-Fi au-delà de 50 Mo » : la taille recommandée sur data, que le gestionnaire de téléchargements respecte. */
    private const val MAX_DATA = "download_manager_recommended_max_bytes_over_mobile"
    const val SEUIL_WIFI = 50_000_000L

    fun attendreWifi(c: Context) = Settings.Global.getLong(c.contentResolver, MAX_DATA, Long.MAX_VALUE) <= SEUIL_WIFI

    fun reglerAttendreWifi(c: Context, oui: Boolean) = try {
        Settings.Global.putString(c.contentResolver, MAX_DATA, if (oui) SEUIL_WIFI.toString() else null)
    } catch (_: SecurityException) {
        false
    }

    // ——— Changer, jeter, retrouver ———

    suspend fun indexer(c: Context, chemins: Array<String>) = suspendCancellableCoroutine { k ->
        var reste = chemins.size
        if (reste == 0) k.resume(Unit) else MediaScannerConnection.scanFile(c, chemins, null) { _, _ -> if (--reste == 0 && k.isActive) k.resume(Unit) }
    }

    private fun idDe(c: Context, chemin: String): Long? =
        c.contentResolver.query(FICHIERS, arrayOf(MediaStore.Files.FileColumns._ID), "${MediaStore.Files.FileColumns.DATA} = ?", arrayOf(chemin), null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /** Met à la corbeille d'Android : le fichier y reste 30 jours, on peut le remettre. Un dossier : tout ce qu'il contient. */
    suspend fun jeter(c: Context, f: Fichier): List<Long> {
        val fichiers = if (f.dossier) File(f.chemin).walkTopDown().filter { it.isFile && !it.name.startsWith(".trashed-") }.map { it.absolutePath }.toList() else listOf(f.chemin)
        val jetes = mutableListOf<Long>()
        val inconnus = fichiers.filter { idDe(c, it) == null }
        if (inconnus.isNotEmpty()) indexer(c, inconnus.toTypedArray())
        fichiers.forEach { chemin ->
            val id = idDe(c, chemin) ?: return@forEach
            try {
                if (c.contentResolver.update(ContentUris.withAppendedId(FICHIERS, id), ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null) > 0) jetes += id
            } catch (_: Exception) {
            }
        }
        return jetes
    }

    class Jete(val id: Long, val nom: String, val taille: Long, val expire: Long, val dossier: String)

    fun corbeille(c: Context): List<Jete> {
        val l = mutableListOf<Jete>()
        try {
            val q = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_EXPIRES} ASC")
            }
            c.contentResolver.query(
                FICHIERS,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_EXPIRES, MediaStore.MediaColumns.RELATIVE_PATH),
                q, null,
            )?.use { cur ->
                while (cur.moveToNext()) {
                    // Le nom en corbeille commence par « .trashed-<date>- » : on montre le vrai.
                    val nom = cur.getString(1).orEmpty().replace(Regex("^\\.trashed-\\d+-"), "")
                    l += Jete(cur.getLong(0), nom, cur.getLong(2), cur.getLong(3) * 1000, cur.getString(4).orEmpty())
                }
            }
        } catch (_: Exception) {
        }
        return l
    }

    fun remettre(c: Context, id: Long) = try {
        c.contentResolver.update(ContentUris.withAppendedId(FICHIERS, id), ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }, null, null) > 0
    } catch (_: Exception) {
        false
    }

    fun effacer(c: Context, id: Long) = try {
        c.contentResolver.delete(ContentUris.withAppendedId(FICHIERS, id), null, null) > 0
    } catch (_: Exception) {
        false
    }

    suspend fun renommer(c: Context, f: Fichier, nom: String): Boolean {
        val avant = File(f.chemin)
        val apres = File(avant.parentFile, nom.trim())
        if (nom.isBlank() || nom.contains('/') || apres.exists() || !avant.renameTo(apres)) return false
        indexer(c, arrayOf(avant.absolutePath, apres.absolutePath))
        return true
    }

    fun nouveauDossier(d: File, nom: String): Boolean = nom.isNotBlank() && !nom.contains('/') && File(d, nom.trim()).mkdir()

    // ——— Nettoyer ———

    /** Les vidéos reçues (hors appareil photo) il y a plus de 3 mois. */
    fun vieillesVideos(c: Context): List<Fichier> {
        val limite = (System.currentTimeMillis() - 90L * 86_400_000) / 1000
        return requete(
            c,
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO} AND ${MediaStore.Files.FileColumns.DATE_ADDED} < ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} NOT LIKE 'DCIM/%'",
            arrayOf(limite.toString()),
        )
    }

    /**
     * Les fichiers en double : même taille, puis même contenu (empreinte SHA-256). On garde le plus ancien
     * de chaque groupe ; les autres sont proposés.
     */
    fun doublons(c: Context): List<Fichier> {
        val tous = requete(c, "${MediaStore.Files.FileColumns.SIZE} > 20000 AND ${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL", ordre = "${MediaStore.Files.FileColumns.DATE_ADDED} ASC")
        val enTrop = mutableListOf<Fichier>()
        tous.groupBy { it.taille }.values.filter { it.size > 1 }.forEach { meme ->
            meme.groupBy { empreinte(it.chemin) }.forEach { (cle, groupe) -> if (cle != null && groupe.size > 1) enTrop += groupe.drop(1) }
        }
        return enTrop
    }

    private fun empreinte(chemin: String): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        File(chemin).inputStream().use { e ->
            val b = ByteArray(64 * 1024)
            while (true) {
                val n = e.read(b)
                if (n < 0) break
                md.update(b, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    class AppliInutile(val paquet: String, val nom: String, val taille: Long)

    /** Les applis installées par la personne et pas ouvertes depuis 2 mois. */
    fun applisInutiles(c: Context): List<AppliInutile> = try {
        val deux = 60L * 86_400_000
        val maintenant = System.currentTimeMillis()
        val usage = c.getSystemService(UsageStatsManager::class.java).queryAndAggregateUsageStats(maintenant - 365L * 86_400_000, maintenant)
        val ssm = c.getSystemService(StorageStatsManager::class.java)
        c.packageManager.getInstalledPackages(0)
            .filter { p -> val ai = p.applicationInfo ?: return@filter false; ai.flags and ApplicationInfo.FLAG_SYSTEM == 0 && !p.packageName.startsWith("africa.samaos.") }
            .filter { p -> p.firstInstallTime < maintenant - deux && (usage[p.packageName]?.lastTimeUsed ?: 0) < maintenant - deux }
            .map { p ->
                val s = try {
                    ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, p.packageName, Process.myUserHandle()).let { it.appBytes + it.dataBytes }
                } catch (_: Exception) {
                    0L
                }
                AppliInutile(p.packageName, c.packageManager.getApplicationLabel(p.applicationInfo!!).toString(), s)
            }
    } catch (_: Exception) {
        emptyList()
    }

    /** Vider les fichiers temporaires de toutes les applis (comme « Libérer de l'espace » d'Android). */
    fun viderCaches(c: Context, octets: Long) = try {
        val pm = c.packageManager
        val type = Class.forName("android.content.pm.IPackageDataObserver")
        // Android libère jusqu'à atteindre la place libre demandée : la place actuelle plus les fichiers temporaires.
        val libre = c.getSystemService(StorageStatsManager::class.java).getFreeBytes(StorageManager.UUID_DEFAULT)
        pm.javaClass.getMethod("freeStorageAndNotify", String::class.java, Long::class.javaPrimitiveType, type)
            .invoke(pm, null, libre + octets, null)
        true
    } catch (_: Exception) {
        false
    }

    /** Les cartes SD et clés USB branchées. */
    fun ailleurs(c: Context): List<Pair<String, File>> =
        c.getSystemService(StorageManager::class.java).storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { v -> v.directory?.let { (if (v.isPrimary) "Ce téléphone" else v.getDescription(c)) to it } }

    fun place(d: File): Pair<Long, Long> = try {
        val s = StatFs(d.absolutePath)
        s.availableBytes to s.totalBytes
    } catch (_: Exception) {
        0L to 0L
    }
}
