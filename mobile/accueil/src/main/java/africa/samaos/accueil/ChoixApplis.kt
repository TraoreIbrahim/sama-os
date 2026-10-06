package africa.samaos.accueil

import africa.samaos.banco.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Les applis qu'on propose de cocher d'emblée dans un nouvel Espace, quand elles sont sur le téléphone. */
private val SUGGEREES = listOf(
    "com.android.camera2", "com.google.android.GoogleCamera",
    "com.android.deskclock", "com.google.android.deskclock",
    "com.android.calculator2", "com.google.android.calculator",
    "com.android.documentsui", "com.google.android.documentsui", "com.google.android.apps.nbu.files",
    "com.android.contacts", "com.google.android.contacts",
)

/** Les applis de départ proposées : les essentielles et quelques usuelles, parmi celles du téléphone. */
fun applisSuggerees(applis: List<Appli>, essentielles: Set<String>): Set<String> =
    applis.map { it.paquet }.filter { it in essentielles || it in SUGGEREES }.toSet()

/** « Téléphone, SMS/MMS, Paramètres et 3 autres » : les applis choisies, en une ligne. */
fun resumeApplis(applis: List<Appli>, choisies: Set<String>): String {
    val noms = applis.filter { it.paquet in choisies }.map { it.nom }
    return when {
        noms.isEmpty() -> "Aucune"
        noms.size <= 3 -> noms.joinToString(", ")
        else -> noms.take(3).joinToString(", ") + " et ${noms.size - 3} autre" + if (noms.size - 3 > 1) "s" else ""
    }
}

/**
 * Le choix des applis d'un Espace : chacune se coche ; les essentielles (appels, SMS, Paramètres) sont
 * toujours là. Rien n'est désinstallé du téléphone : les applis non cochées restent dans les autres Espaces.
 */
@Composable
fun EcranChoixApplis(
    titre: String,
    sousTitre: String,
    applis: List<Appli>,
    choisies: Set<String>,
    essentielles: Set<String>,
    changer: (Set<String>) -> Unit,
    retour: () -> Unit,
    action: String,
    valider: () -> Unit,
) {
    val b = LocalBanco.current
    BackHandler(onBack = retour)
    GabaritReglages(
        titre = titre,
        pastille = null,
        sousTitre = sousTitre,
        retour = retour,
        pied = {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "${choisies.size} appli" + if (choisies.size > 1) "s" else "",
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = b.encre2),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    action,
                    modifier = Modifier.padding(end = 14.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.encre),
                )
                Avancer(actif = true, description = action, onClick = valider)
            }
        },
    ) {
        Note("Les applis cochées seront dans l'Espace. Les autres restent sur le téléphone, dans les autres Espaces.")
        applis.forEach { a ->
            val essentielle = a.paquet in essentielles
            val choisie = essentielle || a.paquet in choisies
            RangeeAppli(a, choisie, essentielle) {
                changer(if (a.paquet in choisies) choisies - a.paquet else choisies + a.paquet)
            }
        }
    }
}

@Composable
private fun RangeeAppli(a: Appli, choisie: Boolean, essentielle: Boolean, basculer: () -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .deborder(12.dp)
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (!essentielle) Modifier.clickable(onClickLabel = a.nom, role = Role.Checkbox, onClick = basculer) else Modifier)
            .semantics { stateDescription = if (essentielle) "Toujours là" else if (choisie) "Cochée" else "Non cochée" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(a.icone, contentDescription = null, modifier = Modifier.size(40.dp))
        BasicText(
            a.nom,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre),
        )
        if (essentielle) {
            BasicText("Toujours là", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = b.encre2))
        } else {
            Case(choisie)
        }
    }
}

