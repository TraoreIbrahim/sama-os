package africa.samaos.banco.appli

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.SizeF
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Les outils des widgets de l'Accueil, communs aux applis de Sama : un widget n'a pas Compose, il se dessine avec
 * les vues d'Android (RemoteViews) et des images faites ici.
 */
object OutilsWidget {
    private fun px(c: Context, dp: Float) = (dp * c.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    /** Un picto de Banco (tracé SVG sur 24 unités) dessiné au trait. */
    fun picto(c: Context, chemin: String, dp: Float, couleur: Int, epaisseur: Float = 1.75f): Bitmap {
        val p = px(c, dp)
        val bmp = Bitmap.createBitmap(p, p, Bitmap.Config.ARGB_8888)
        val toile = Canvas(bmp)
        toile.scale(p / 24f, p / 24f)
        val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = epaisseur
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = couleur
        }
        toile.drawPath(PathParser().parsePathString(chemin).toPath().asAndroidPath(), trait)
        return bmp
    }

    /** Un rond : la photo (recadrée au centre), sinon les initiales sur la teinte. */
    fun rond(c: Context, dp: Float, photo: Bitmap?, initiales: String, teinte: Int, encre: Int): Bitmap {
        val p = px(c, dp)
        val bmp = Bitmap.createBitmap(p, p, Bitmap.Config.ARGB_8888)
        val toile = Canvas(bmp)
        val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
        if (photo != null) {
            val cote = min(photo.width, photo.height).toFloat()
            val m = Matrix().apply {
                setScale(p / cote, p / cote)
                postTranslate(-(photo.width - cote) / 2f * p / cote, -(photo.height - cote) / 2f * p / cote)
            }
            pinceau.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            toile.drawCircle(p / 2f, p / 2f, p / 2f, pinceau)
        } else {
            pinceau.color = teinte
            toile.drawCircle(p / 2f, p / 2f, p / 2f, pinceau)
            val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = encre
                textSize = p * 0.38f
                typeface = Typeface.create(Typeface.DEFAULT, 600, false)
                textAlign = Paint.Align.CENTER
            }
            toile.drawText(initiales, p / 2f, p / 2f - (texte.descent() + texte.ascent()) / 2f, texte)
        }
        return bmp
    }

    /**
     * Une photo pour un widget : recadrée pour remplir [l]×[h] dp, coins arrondis, et un voile sombre en bas
     * pour que le texte posé dessus se lise.
     */
    fun photo(c: Context, source: Bitmap, l: Float, h: Float, rayonDp: Float, voile: Boolean): Bitmap {
        val pl = px(c, l)
        val ph = px(c, h)
        val bmp = Bitmap.createBitmap(pl, ph, Bitmap.Config.ARGB_8888)
        val toile = Canvas(bmp)
        val echelle = maxOf(pl / source.width.toFloat(), ph / source.height.toFloat())
        val m = Matrix().apply {
            setScale(echelle, echelle)
            postTranslate((pl - source.width * echelle) / 2f, (ph - source.height * echelle) / 2f)
        }
        val r = px(c, rayonDp).toFloat()
        val pinceau = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
        }
        toile.drawRoundRect(RectF(0f, 0f, pl.toFloat(), ph.toFloat()), r, r, pinceau)
        if (voile) {
            val ombre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, ph * 0.45f, 0f, ph.toFloat(), 0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP)
            }
            toile.drawRoundRect(RectF(0f, 0f, pl.toFloat(), ph.toFloat()), r, r, ombre)
        }
        return bmp
    }

    /** La taille que l'Accueil donne au widget, en dp (largeur, hauteur), ou null s'il ne l'a pas dite. */
    fun taille(m: AppWidgetManager, id: Int): SizeF? {
        val o = m.getAppWidgetOptions(id)
        @Suppress("DEPRECATION")
        val t = o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.firstOrNull()
        if (t != null) return t
        val l = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return if (l > 0 && h > 0) SizeF(l.toFloat(), h.toFloat()) else null
    }

    /**
     * Un réveil qui ne réveille pas le téléphone (écran éteint, il attend qu'on le rallume) : à la minute près si
     * l'appli en a le droit, sinon dans les dix minutes.
     */
    fun reveil(c: Context, quand: Long, pi: PendingIntent) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, quand, pi) else am.setWindow(AlarmManager.RTC, quand, 60_000L, pi)
        } catch (_: SecurityException) {
            am.setWindow(AlarmManager.RTC, quand, 60_000L, pi)
        }
    }
}
