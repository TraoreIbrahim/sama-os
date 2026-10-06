package africa.samaos.lecteur

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Un morceau de musique du téléphone. */
data class Morceau(val id: Long, val titre: String, val artiste: String, val album: String, val duree: Long, val dossier: String, val source: Uri? = null) {
    val uri: Uri get() = source ?: ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

/** Une vidéo du téléphone. */
data class Video(val id: Long, val titre: String, val duree: Long, val date: Long, val dossier: String, val largeur: Int, val hauteur: Int) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
}

fun duree(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

object Bibliotheque {
    private fun artiste(a: String?) = a?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING } ?: "Artiste inconnu"

    fun morceaux(c: Context): List<Morceau> {
        val l = mutableListOf<Morceau>()
        try {
            c.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.RELATIVE_PATH,
                ),
                "${MediaStore.Audio.Media.IS_MUSIC} = 1", null, null,
            )?.use { cur ->
                while (cur.moveToNext()) {
                    l += Morceau(cur.getLong(0), cur.getString(1).orEmpty(), artiste(cur.getString(2)), cur.getString(3).orEmpty(), cur.getLong(4), cur.getString(5).orEmpty())
                }
            }
        } catch (_: Exception) {
        }
        // L'ordre de l'alphabet français : « Éclat » avec les E.
        val ordre = java.text.Collator.getInstance(java.util.Locale.FRENCH).apply { strength = java.text.Collator.PRIMARY }
        return l.sortedWith { x, y -> ordre.compare(x.titre, y.titre) }
    }

    fun videos(c: Context): List<Video> {
        val l = mutableListOf<Video>()
        try {
            c.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.RELATIVE_PATH, MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT,
                ),
                null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cur ->
                while (cur.moveToNext()) {
                    l += Video(cur.getLong(0), cur.getString(1).orEmpty().replace('_', ' '), cur.getLong(2), cur.getLong(3) * 1000, cur.getString(4).orEmpty(), cur.getInt(5), cur.getInt(6))
                }
            }
        } catch (_: Exception) {
        }
        return l
    }

    // ——— Où l'on s'était arrêté dans une vidéo ———

    private fun prefs(c: Context) = c.getSharedPreferences("reprises", Context.MODE_PRIVATE)

    fun reprise(c: Context, v: Video): Long = prefs(c).getLong(v.id.toString(), 0)

    fun garderReprise(c: Context, v: Video, ms: Long) {
        // Presque fini : la prochaine fois, on repart du début.
        val e = prefs(c).edit()
        if (ms < 5_000 || ms > v.duree - 10_000) e.remove(v.id.toString()) else e.putLong(v.id.toString(), ms)
        e.putLong("vue_${v.id}", System.currentTimeMillis()).apply()
    }

    fun vueLe(c: Context, v: Video): Long = prefs(c).getLong("vue_${v.id}", 0)

    // ——— Les pochettes ———

    private val pochettes = LruCache<String, ImageBitmap>(200)

    fun pochetteEnCache(uri: Uri) = pochettes.get(uri.toString())

    /** La pochette d'un morceau (l'image dans le fichier) ou l'image d'une vidéo. */
    fun pochette(c: Context, uri: Uri, cote: Int = 256): ImageBitmap? = pochettes.get(uri.toString()) ?: try {
        c.contentResolver.loadThumbnail(uri, Size(cote, cote), null).asImageBitmap().also { pochettes.put(uri.toString(), it) }
    } catch (_: Exception) {
        null
    }
}
