package africa.samaos.accueil

import africa.samaos.banco.*
import android.content.Context
import android.content.SharedPreferences

/**
 * Les réglages de l'Accueil, gardés dans le stockage disponible avant le déverrouillage du profil :
 * l'Accueil démarre ainsi avec l'Espace, sans écran d'attente d'Android. Rien de secret n'y est gardé.
 */
fun reglages(contexte: Context, nom: String): SharedPreferences {
    val avantDeverrouillage = contexte.createDeviceProtectedStorageContext()
    // Les réglages d'avant ce changement sont repris, quand le profil est déverrouillé.
    try {
        avantDeverrouillage.moveSharedPreferencesFrom(contexte, nom)
    } catch (_: Exception) {
        // Profil encore verrouillé : on les reprendra à la prochaine ouverture.
    }
    return avantDeverrouillage.getSharedPreferences(nom, Context.MODE_PRIVATE)
}
