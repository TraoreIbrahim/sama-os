package africa.samaos.accueil

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import org.json.JSONObject

/**
 * Le téléphone qui chauffe (maquette l4-surchauffe). L'Accueil, toujours là, suit l'état thermique d'Android :
 * au-delà de « sévère », il baisse la luminosité et prévient ; l'Appareil photo se ferme de lui-même.
 * Ce qui a été fait est noté dans `sama_chaleur` (lu par la page des Réglages) et défait au retour au frais.
 */
object Chaleur {
    private const val CLE = "sama_chaleur"
    private const val CANAL = "chaleur"
    private const val ID = 4242
    /** La luminosité quand il fait chaud, sur 255. */
    private const val DOUCE = 60
    private var suivi = false

    fun surveiller(c: Context) {
        if (suivi) return
        suivi = true
        val app = c.applicationContext
        try {
            app.getSystemService(PowerManager::class.java).addThermalStatusListener(app.mainExecutor) { s -> changer(app, s) }
        } catch (_: Exception) {
            suivi = false
        }
    }

    private fun lire(c: Context): JSONObject = try {
        JSONObject(Settings.Global.getString(c.contentResolver, CLE) ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun changer(c: Context, statut: Int) {
        val etat = lire(c)
        when {
            statut >= PowerManager.THERMAL_STATUS_SEVERE && etat.optLong("depuis") == 0L -> refroidir(c, etat)
            statut <= PowerManager.THERMAL_STATUS_LIGHT && etat.length() > 0 -> reprendre(c, etat)
        }
    }

    private fun refroidir(c: Context, etat: JSONObject) {
        val cr = c.contentResolver
        try {
            val mode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            val niveau = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC || niveau > DOUCE) {
                etat.put("luminosite", true).put("mode", mode).put("niveau", niveau)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, DOUCE)
            }
        } catch (_: Exception) {
        }
        etat.put("depuis", System.currentTimeMillis())
        try {
            Settings.Global.putString(cr, CLE, etat.toString())
        } catch (_: Exception) {
        }
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Chaleur du téléphone", NotificationManager.IMPORTANCE_HIGH))
        val page = PendingIntent.getActivity(
            c, 0, Intent("africa.samaos.action.SURCHAUFFE").setPackage("africa.samaos.reglages").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            ID,
            Notification.Builder(c, CANAL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Le téléphone chauffe")
                .setContentText(if (etat.optBoolean("luminosite")) "Luminosité baissée. Posez-le à l'ombre, retirez la coque." else "Posez-le à l'ombre, retirez la coque.")
                .setContentIntent(page)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun reprendre(c: Context, etat: JSONObject) {
        val cr = c.contentResolver
        if (etat.optBoolean("luminosite")) {
            try {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, etat.optInt("niveau", 128))
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, etat.optInt("mode", 0))
            } catch (_: Exception) {
            }
        }
        try {
            Settings.Global.putString(cr, CLE, null)
        } catch (_: Exception) {
        }
        c.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
