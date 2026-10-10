package africa.samaos.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

/**
 * Ma fiche : mon nom, mon numéro et ma photo, gardés dans l'appli (pas dans le carnet, qu'on synchronise).
 * Elle ne quitte le téléphone que si on la montre (code QR) ou qu'on la partage.
 */
object MaFiche {
    class Fiche(val prenom: String, val famille: String, val numero: String, val photo: ImageBitmap?) {
        val nom: String get() = "$prenom $famille".trim()

        /** La vCard 3.0 que lit l'appareil photo de n'importe quel téléphone. */
        fun vcard(): String {
            fun e(t: String) = t.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
            return "BEGIN:VCARD\nVERSION:3.0\nN:${e(famille)};${e(prenom)};;;\nFN:${e(nom)}\nTEL;TYPE=CELL:${numero.filter { it.isDigit() || it == '+' }}\nEND:VCARD"
        }
    }

    private fun prefs(c: Context) = c.getSharedPreferences("ma_fiche", Context.MODE_PRIVATE)
    private fun fichierPhoto(c: Context) = File(c.filesDir, "ma_fiche.jpg")

    fun lire(c: Context): Fiche? {
        val p = prefs(c)
        val prenom = p.getString("prenom", null) ?: return null
        val photo = fichierPhoto(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        return Fiche(prenom, p.getString("famille", "").orEmpty(), p.getString("numero", "").orEmpty(), photo)
    }

    fun enregistrer(c: Context, prenom: String, famille: String, numero: String, photo: Bitmap?, sansPhoto: Boolean) {
        prefs(c).edit().putString("prenom", prenom.trim()).putString("famille", famille.trim()).putString("numero", numero.trim()).apply()
        when {
            photo != null -> fichierPhoto(c).writeBytes(Avatars.jpeg(photo))
            sansPhoto -> fichierPhoto(c).delete()
        }
    }

    /** Le code QR, noir sur blanc, avec une petite marge. */
    fun qr(texte: String, taille: Int = 600): Bitmap {
        val m = QRCodeWriter().encode(texte, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"))
        val n = m.width
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        for (y in 0 until n) for (x in 0 until n) b.setPixel(x, y, if (m.get(x, y)) 0xFF1F1C18.toInt() else 0xFFFFFFFF.toInt())
        return Bitmap.createScaledBitmap(b, taille, taille, false)
    }
}
