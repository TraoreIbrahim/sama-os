package africa.samaos.photos

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Une photo ou une vidéo de la pellicule. */
data class Media(
    val id: Long,
    val video: Boolean,
    val nom: String,
    val date: Long,
    val duree: Long,
    val album: String,
    val albumId: Long,
    val dossier: String,
    val favori: Boolean,
    val largeur: Int,
    val hauteur: Int,
    val taille: Long,
    val mime: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    val jour: LocalDate get() = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()
}

class Album(val id: Long, val nom: String, val nombre: Int, val couverture: Media, val sorte: Sorte)

enum class Sorte { ALBUM, CAPTURES, SCANS }

fun taille(o: Long): String = when {
    o >= 1_000_000_000 -> String.format(Locale.FRENCH, "%.1f Go", o / 1e9)
    o >= 10_000_000 -> "${o / 1_000_000} Mo"
    o >= 1_000_000 -> String.format(Locale.FRENCH, "%.1f Mo", o / 1e6)
    o >= 1_000 -> "${o / 1_000} Ko"
    else -> "$o octets"
}

/** « Aujourd'hui », « Hier », « Samedi 3 octobre », « Samedi 3 octobre 2025 ». */
fun nomDuJour(d: LocalDate): String {
    val auj = LocalDate.now()
    return when {
        d == auj -> "Aujourd'hui"
        d == auj.minusDays(1) -> "Hier"
        d.year == auj.year -> d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
        else -> d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
    }
}

fun duree(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

object Galerie {
    private val FICHIERS = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val COLONNES = arrayOf(
        MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.DATE_TAKEN, MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.MediaColumns.DURATION,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.RELATIVE_PATH,
        MediaStore.MediaColumns.IS_FAVORITE, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
        MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE,
    )
    private const val PHOTOS_ET_VIDEOS =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    private fun lire(c: Context, choix: String, args: Array<String>? = null, corbeille: Boolean = false): List<Media> {
        val l = mutableListOf<Media>()
        try {
            val q = Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, choix)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "COALESCE(${MediaStore.MediaColumns.DATE_TAKEN}, ${MediaStore.Files.FileColumns.DATE_ADDED} * 1000) DESC")
                if (corbeille) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
            c.contentResolver.query(FICHIERS, COLONNES, q, null)?.use { cur ->
                while (cur.moveToNext()) {
                    val prise = if (cur.isNull(3)) cur.getLong(4) * 1000 else cur.getLong(3)
                    l += Media(
                        cur.getLong(0), cur.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                        cur.getString(2).orEmpty().replace(Regex("^\\.trashed-\\d+-"), ""), prise, cur.getLong(5),
                        cur.getString(6).orEmpty(), cur.getLong(7), cur.getString(8).orEmpty(), cur.getInt(9) == 1,
                        cur.getInt(10), cur.getInt(11), cur.getLong(12), cur.getString(13) ?: "image/jpeg",
                    )
                }
            }
        } catch (_: Exception) {
        }
        return l
    }

    fun tout(c: Context): List<Media> = lire(c, PHOTOS_ET_VIDEOS)

    fun album(c: Context, id: Long): List<Media> = lire(c, "$PHOTOS_ET_VIDEOS AND ${MediaStore.MediaColumns.BUCKET_ID} = ?", arrayOf(id.toString()))

    fun favoris(c: Context): List<Media> = lire(c, "$PHOTOS_ET_VIDEOS AND ${MediaStore.MediaColumns.IS_FAVORITE} = 1")

    fun corbeille(c: Context): List<Media> = lire(c, PHOTOS_ET_VIDEOS, corbeille = true)

    /** Le nom d'un album tel qu'on le dit : « Appareil photo » plutôt que « Camera ». */
    fun nomAlbum(nom: String, dossier: String): String = when {
        dossier.startsWith("DCIM/Camera") || nom == "Camera" -> "Appareil photo"
        nom == "Screenshots" -> "Captures d'écran"
        nom == "Download" -> "Téléchargements"
        nom == "Pictures" -> "Images"
        nom == "Movies" -> "Vidéos"
        nom == "Scans" || nom == "Documents scannés" -> "Documents scannés"
        else -> nom
    }

    fun albums(tout: List<Media>): List<Album> = tout.groupBy { it.albumId }.map { (id, l) ->
        val nom = nomAlbum(l.first().album, l.first().dossier)
        Album(
            id, nom, l.size, l.first(),
            when (nom) {
                "Captures d'écran" -> Sorte.CAPTURES
                "Documents scannés" -> Sorte.SCANS
                else -> Sorte.ALBUM
            },
        )
    }.sortedWith(compareBy<Album>({ it.nom != "Appareil photo" }).thenByDescending { it.couverture.date })

    // ——— Changer ———

    private fun maj(c: Context, m: Media, v: ContentValues) = try {
        c.contentResolver.update(ContentUris.withAppendedId(FICHIERS, m.id), v, null, null) > 0
    } catch (_: Exception) {
        false
    }

    fun favori(c: Context, m: Media, oui: Boolean) = maj(c, m, ContentValues().apply { put(MediaStore.MediaColumns.IS_FAVORITE, if (oui) 1 else 0) })

    /** À la corbeille d'Android : 30 jours pour changer d'avis. */
    fun jeter(c: Context, m: Media) = maj(c, m, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) })

    fun remettre(c: Context, m: Media) = maj(c, m, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) })

    fun effacer(c: Context, m: Media) = try {
        c.contentResolver.delete(ContentUris.withAppendedId(FICHIERS, m.id), null, null) > 0
    } catch (_: Exception) {
        false
    }

    fun expiration(c: Context, m: Media): Long = try {
        val q = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
        c.contentResolver.query(ContentUris.withAppendedId(FICHIERS, m.id), arrayOf(MediaStore.MediaColumns.DATE_EXPIRES), q, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) * 1000 else 0 } ?: 0
    } catch (_: Exception) {
        0
    }

    // ——— Les images ———

    private val vignettes = LruCache<Long, ImageBitmap>(400)

    fun vignetteEnCache(m: Media) = vignettes.get(m.id)

    fun vignette(c: Context, m: Media, cote: Int = 256): ImageBitmap? = vignettes.get(m.id) ?: try {
        c.contentResolver.loadThumbnail(m.uri, Size(cote, cote), null).asImageBitmap().also { vignettes.put(m.id, it) }
    } catch (_: Exception) {
        null
    }

    /** L'image entière, réduite au plus à `maxi` pixels de côté (assez pour l'écran, sans épuiser la mémoire). */
    fun image(c: Context, uri: Uri, maxi: Int = 2400): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { d, info, _ ->
            val s = info.size
            val r = maxOf(s.width, s.height).toFloat() / maxi
            if (r > 1) d.setTargetSize((s.width / r).toInt(), (s.height / r).toInt())
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            d.isMutableRequired = false
        }
    } catch (_: Exception) {
        null
    }

    // ——— Le lieu dans la photo ———

    /** La photo garde-t-elle l'endroit où elle a été prise (coordonnées GPS dans ses données) ? */
    fun aUnLieu(c: Context, uri: Uri): Boolean = try {
        val original = try {
            MediaStore.setRequireOriginal(uri)
        } catch (_: Exception) {
            uri
        }
        c.contentResolver.openInputStream(original)?.use { ExifInterface(it).getLatLong(FloatArray(2)) } ?: false
    } catch (_: Exception) {
        false
    }

    /** Une copie sans le lieu (ni l'appareil), à partager. */
    fun copieSansLieu(c: Context, m: Media): File? = try {
        val dossier = File(c.cacheDir, "partage").apply { mkdirs() }
        val f = File(dossier, m.nom)
        c.contentResolver.openInputStream(m.uri)?.use { e -> f.outputStream().use { e.copyTo(it) } }
        val exif = ExifInterface(f.absolutePath)
        listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ).forEach { exif.setAttribute(it, null) }
        exif.saveAttributes()
        f
    } catch (_: Exception) {
        null
    }

    fun appareil(c: Context, m: Media): String? = try {
        c.contentResolver.openInputStream(m.uri)?.use { e ->
            val x = ExifInterface(e)
            listOfNotNull(x.getAttribute(ExifInterface.TAG_MAKE), x.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" ").ifBlank { null }
        }
    } catch (_: Exception) {
        null
    }

    /** Enregistre une retouche comme une nouvelle photo, à côté de l'originale. */
    fun enregistrerCopie(c: Context, m: Media, b: Bitmap): Uri? {
        val base = m.nom.substringBeforeLast('.')
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${base}_retouche_${System.currentTimeMillis() / 1000}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, m.dossier.ifBlank { "Pictures/" })
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return try {
            val uri = c.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v) ?: return null
            c.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (_: Exception) {
            null
        }
    }
}
