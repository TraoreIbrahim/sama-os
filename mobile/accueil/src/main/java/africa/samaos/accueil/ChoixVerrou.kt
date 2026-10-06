package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ouvre le choix du code d'Android pour l'Espace où l'on se trouve, au niveau demandé : Android grise
 * ce qui ne suffit pas (le schéma pour « Code », par exemple) et refuse les codes trop simples.
 */
fun choisirLeCode(contexte: Context, verrou: VerrouEspace) = Systeme.ouvrir(
    contexte,
    Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD).putExtra(DevicePolicyManager.EXTRA_PASSWORD_COMPLEXITY, verrou.complexite),
)

/** « Verrouiller avec » : les trois niveaux, une pastille pour le choisi (comme les Réglages de l'Espace, esp-06). */
@Composable
fun EcranChoixVerrou(sousTitre: String, choisi: VerrouEspace, choisir: (VerrouEspace) -> Unit, retour: () -> Unit) {
    val b = LocalBanco.current
    BackHandler(onBack = retour)
    GabaritReglages(titre = "Verrouiller avec", pastille = null, sousTitre = sousTitre, retour = retour) {
        Rubrique("Déverrouiller l'Espace avec")
        VerrouEspace.entries.forEach { v ->
            RangeeChoix(v.nom, v.detail, choisie = v == choisi) {
                choisir(v)
                retour()
            }
        }
        Note(
            "Le téléphone demandera ce code pour entrer dans l'Espace, et chiffrera ses fichiers avec lui. " +
                "L'empreinte s'y ajoute si le téléphone en a une.",
        )
        Note("Sama ne vous demandera jamais ce code, ni par appel ni par SMS. Ne le donnez à personne.", b.lateriteTexte)
    }
}


