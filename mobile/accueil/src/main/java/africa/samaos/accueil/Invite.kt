package africa.samaos.accueil

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import africa.samaos.banco.Avancer
import africa.samaos.banco.GabaritReglages
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Paragraphe
import africa.samaos.banco.Rangee
import africa.samaos.banco.Rubrique
import kotlinx.coroutines.launch

/**
 * Prêter le téléphone : un utilisateur invité d'Android, éphémère (effacé dès qu'on revient dans Maison).
 * Il ne voit rien des Espaces ; on lui laisse appeler, écrire et aller sur Internet.
 */
object Invite {
    private const val TYPE_INVITE = "android.os.usertype.full.GUEST"
    private const val EPHEMERE = 0x100

    fun creer(c: Context): Int? = try {
        val um = c.getSystemService(UserManager::class.java)
        val info = um.javaClass.getMethod("createUser", String::class.java, String::class.java, Int::class.javaPrimitiveType)
            .invoke(um, "Invité", TYPE_INVITE, EPHEMERE)
        val id = info?.javaClass?.getField("id")?.getInt(info) ?: return null
        // Android interdit d'office appels et SMS aux invités : prêter son téléphone, c'est d'abord pour appeler.
        val restreindre = um.javaClass.getMethod("setUserRestriction", String::class.java, Boolean::class.javaPrimitiveType, UserHandle::class.java)
        val qui = UserHandle.getUserHandleForUid(id * 100_000)
        listOf(UserManager.DISALLOW_OUTGOING_CALLS, UserManager.DISALLOW_SMS).forEach { restreindre.invoke(um, it, false, qui) }
        id
    } catch (_: Exception) {
        null
    }

    fun estInvite(c: Context): Boolean = try {
        val um = c.getSystemService(UserManager::class.java)
        um.javaClass.getMethod("isGuestUser").invoke(um) as Boolean
    } catch (_: Exception) {
        false
    }

    /** Dans l'Espace Invité, les applis de Sama utiles à un invité (déjà sur le téléphone, rien n'est téléchargé). */
    fun poserApplis(c: Context) {
        val pm = c.packageManager
        listOf("africa.samaos.griot", "africa.samaos.calculatrice", "africa.samaos.horloge").forEach { p ->
            try {
                pm.javaClass.getMethod("installExistingPackage", String::class.java).invoke(pm, p)
            } catch (_: Exception) {
            }
        }
    }
}

/** Prêter le téléphone (maquette l5-invite). */
@Composable
fun EcranPreter(espaces: Espaces) {
    val c = LocalContext.current
    val b = LocalBanco.current
    val scope = rememberCoroutineScope()
    var enCours by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler { if (!enCours) espaces.fermerPret() }
    GabaritReglages(
        titre = "Prêter le téléphone",
        pastille = null,
        sousTitre = "Un Espace Invité, effacé à la sortie",
        retour = { if (!enCours) espaces.fermerPret() },
        pied = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Avancer(!enCours, "Prêter le téléphone") {
                    enCours = true
                    message = null
                    scope.launch {
                        message = espaces.preter()
                        enCours = false
                    }
                }
            }
        },
    ) {
        Rubrique("L'invité pourra")
        Rangee("Appeler et envoyer des SMS", icone = Icones.APPEL)
        Rangee("Aller sur Internet avec Griot", icone = Icones.GLOBE)
        Rangee("Utiliser la data", icone = Icones.DONNEES)
        Spacer(Modifier.height(16.dp))
        Rubrique("Il ne verra jamais")
        Rangee("Vos Espaces et vos comptes", detail = "Ni vos photos, ni vos messages, ni vos appels", icone = Icones.CADENAS)
        Spacer(Modifier.height(16.dp))
        if (espaces.securise) {
            Paragraphe("Tout ce qu'il fait est effacé quand vous reprenez le téléphone avec votre code.")
        } else {
            // Sans code, l'invité pourrait revenir dans Maison d'un geste : on le dit, et on propose d'en mettre un.
            Paragraphe("Maison n'a pas de code : l'invité pourrait y revenir d'un geste. Mettez un code avant de prêter le téléphone.", b.lateriteTexte)
            Rangee("Mettre un code", icone = Icones.CADENAS, surToucher = {
                try {
                    c.startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            })
        }
        message?.let {
            Spacer(Modifier.height(12.dp))
            Paragraphe(it, b.lateriteTexte)
        }
    }
}
