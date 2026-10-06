package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.WindowManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit

/**
 * Le fond d'écran du système : le paysage de l'Espace, de jour ou de nuit.
 * On le voit sur l'écran de verrouillage et pendant le passage entre le démarrage et l'Accueil.
 * Seulement quand Sama est le système : sur un téléphone ordinaire, on ne touche pas au fond choisi par la personne.
 */
object FondEcran {

    fun appliquer(contexte: Context, b: Banco) {
        if (contexte.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) return
        val prefs = reglages(contexte, "sama")
        val voulu = "%08x".format(b.ciel.toArgb()) + VERSION
        if (prefs.getString("fond", null) == voulu) return
        val bornes = contexte.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val image = dessiner(bornes.width(), bornes.height(), b)
        try {
            val gestion = WallpaperManager.getInstance(contexte)
            // Sans cette indication, Android agrandit le fond au carré et le soleil ne tombe plus à sa place.
            gestion.suggestDesiredDimensions(bornes.width(), bornes.height())
            gestion.setBitmap(image, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            prefs.edit { putString("fond", voulu) }
        } catch (_: Exception) {
            // Fond d'écran refusé (politique d'entreprise, par exemple) : on garde l'actuel.
        }
    }

    /** À changer quand le dessin change, pour que le nouveau fond remplace l'ancien. */
    private const val VERSION = "-2"

    private fun dessiner(largeur: Int, hauteur: Int, b: Banco): Bitmap {
        val image = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
        val c = Canvas(image)
        val sx = largeur / 390f
        val sy = hauteur / 844f
        val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(b.ciel.toArgb())
        pinceau.color = b.astre.toArgb()
        c.drawCircle(286 * sx, 430 * sy, 70 * sx, pinceau)
        listOf(480f to b.colline1, 574f to b.colline2, 664f to b.colline3, 754f to b.colline4).forEach { (y, couleur) ->
            pinceau.color = couleur.toArgb()
            c.drawPath(
                Path().apply {
                    moveTo(0f, y * sy)
                    cubicTo(70 * sx, (y - 20) * sy, 140 * sx, (y - 14) * sy, 200 * sx, (y - 4) * sy)
                    cubicTo(260 * sx, (y + 6) * sy, 320 * sx, (y - 22) * sy, 390 * sx, (y - 10) * sy)
                    lineTo(390 * sx, 844 * sy)
                    lineTo(0f, 844 * sy)
                    close()
                },
                pinceau,
            )
        }
        return image
    }
}
