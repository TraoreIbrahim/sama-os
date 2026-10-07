package africa.samaos.clavier

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/** « Réglages du clavier » ouvre la page Clavier des Réglages de Sama. */
class VersReglages : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
        finish()
    }
}
