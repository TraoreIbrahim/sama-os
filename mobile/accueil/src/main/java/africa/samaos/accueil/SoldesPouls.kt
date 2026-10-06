package africa.samaos.accueil

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import africa.samaos.soldes.Releve
import africa.samaos.soldes.Soldes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** La couleur de la puce d'une SIM, d'après son opérateur (comme dans les applis natives). */
private fun couleurOperateur(nom: String): Color = when {
    nom.contains("orange", true) -> Color(0xFFE8752A)
    nom.contains("mtn", true) -> Color(0xFFE2B42B)
    nom.contains("moov", true) -> Color(0xFF2F6FB5)
    else -> Color(0xFF8F8476)
}

/**
 * « Mes SIM » dans le Pouls (maquette i1-pouls-soldes) : crédit, data et fin du forfait de chaque SIM, lus dans
 * les SMS des opérateurs ; le mobile money reste masqué jusqu'au code du téléphone. Rien si rien n'a été lu.
 */
@Composable
fun CarteSoldes(visible: Boolean, ouvrirReglages: () -> Unit) {
    val b = LocalBanco.current
    val c = LocalContext.current
    var releves by remember { mutableStateOf<Map<Int, Releve>>(emptyMap()) }
    var sims by remember { mutableStateOf<List<Telephonie.Sim>>(emptyList()) }
    var argent by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var aMontrer by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) {
            // Le Pouls se referme : le solde mobile money se cache de nouveau.
            argent = null
            return@LaunchedEffect
        }
        releves = withContext(Dispatchers.IO) { Soldes.releves(c) }
        sims = Telephonie.sims(c)
        aMontrer = withContext(Dispatchers.IO) { Soldes.mobileMoney(c).isNotEmpty() }
    }
    val code = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) argent = Soldes.mobileMoney(c)
    }
    if (releves.isEmpty() && !aMontrer) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val lu = releves.values.maxOfOrNull { it.quand }
        BasicText(
            "Mes SIM" + (lu?.let { " · " + Soldes.texteLu(it) } ?: ""),
            modifier = Modifier.padding(start = 8.dp, top = 10.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
        )
        if (releves.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val connues = sims.filter { it.id in releves }.ifEmpty { releves.keys.map { Telephonie.Sim(it, 1, "SIM") } }
            connues.forEach { s ->
                val r = releves[s.id] ?: return@forEach
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(b.sol2).clickable(onClickLabel = "Soldes et forfaits", role = Role.Button, onClick = ouvrirReglages)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(couleurOperateur(s.operateur)), contentAlignment = Alignment.Center) {
                            BasicText("${s.place}", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White))
                        }
                        BasicText(s.operateur, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre))
                    }
                    r.credit?.let { Valeur("Crédit", Soldes.texteMontant(it)) }
                    r.dataReste?.let { reste ->
                        Valeur("Data", Soldes.texteVolume(reste) + (r.dataTotal?.let { " sur " + Soldes.texteVolume(it) } ?: ""))
                        val part = r.dataTotal?.takeIf { it > 0 }?.let { (reste / it).toFloat().coerceIn(0f, 1f) }
                        if (part != null) Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(b.sol)) {
                            Box(Modifier.fillMaxWidth(part).fillMaxHeight().background(b.laterite))
                        }
                        val fin = r.expire
                        when {
                            reste < 1 -> Legende("Plus de data", b.lateriteTexte)
                            fin != null -> Legende(Soldes.texteFin(fin).replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        }
        if (aMontrer) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(b.sol2).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFE2A62B)), contentAlignment = Alignment.Center) {
                IconeTrait("M4 7h16v10H4z M4 11h16 M8 15h3", 20.dp, Color.White)
            }
            Column(Modifier.weight(1f)) {
                BasicText("Mobile money", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.encre))
                val montre = argent
                BasicText(
                    if (montre == null) "Solde masqué" else montre.joinToString(" · ") { (op, s) -> "$op : $s" },
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(b.sol)
                    .clickable(onClickLabel = if (argent == null) "Afficher le solde" else "Masquer le solde", role = Role.Button) {
                        if (argent != null) {
                            argent = null
                        } else {
                            val km = c.getSystemService(KeyguardManager::class.java)
                            // Avec un code sur le téléphone, il faut le donner pour voir l'argent ; sinon, on montre.
                            if (km.isDeviceSecure) code.launch(km.createConfirmDeviceCredentialIntent("Mobile money", "Votre code pour voir le solde"))
                            else argent = Soldes.mobileMoney(c)
                        }
                    }
                    .semantics { contentDescription = if (argent == null) "Afficher le solde" else "Masquer le solde" },
                contentAlignment = Alignment.Center,
            ) { IconeTrait(if (argent == null) Icones.OEIL else Icones.OEIL_BARRE, 22.dp, b.encre) }
        }
    }
}

@Composable
private fun Valeur(nom: String, valeur: String) {
    val b = LocalBanco.current
    Column {
        BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = b.encre2))
        BasicText(valeur, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, color = b.encre))
    }
}

@Composable
private fun Legende(texte: String, couleur: Color? = null) {
    BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = couleur ?: LocalBanco.current.encre2))
}
