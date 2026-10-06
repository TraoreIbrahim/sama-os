package africa.samaos.reglages

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Avancer
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Le téléphone presque plein (maquette l4-stockage-plein). Android prévient lui-même quand la place manque ;
 * sa notification ouvre ici (ACTION_MANAGE_STORAGE) au lieu des Paramètres d'Android.
 */
@Composable
fun PageStockagePlein(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var o by remember { mutableStateOf<Occupation?>(null) }
    LaunchedEffect(version) { o = withContext(Dispatchers.IO) { MoteurStockage.lire(c) } }
    val carte = remember {
        c.getSystemService(StorageManager::class.java).storageVolumes.firstOrNull { it.isRemovable && it.state == android.os.Environment.MEDIA_MOUNTED }
    }
    val occ = o
    PageReglages(
        titre = occ?.let { "Plus que " + taille(it.libre) } ?: "Stockage",
        sousTitre = "Le téléphone est presque plein",
        retour = nav.retour,
    ) {
        if (occ == null) return@PageReglages
        section("Faire de la place", cle = "place") {
            if (occ.caches > 20_000_000) {
                Ligne("Libérer ${taille(occ.caches)} tout de suite", detail = "Les fichiers temporaires des applis : rien de personnel", icone = Icones.CORBEILLE) {
                    MoteurStockage.viderCaches(c, occ.caches)
                    version++
                }
            }
            Ligne("Trier ce qui peut partir", detail = "Vidéos reçues, doublons, applis inutilisées : Fichiers › Nettoyer", icone = Icones.DOSSIER) {
                try {
                    c.startActivity(Intent().setClassName("africa.samaos.fichiers", "africa.samaos.fichiers.Fichiers").putExtra("vue", "nettoyer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
            if (carte != null) {
                val libre = remember { carte.directory?.freeSpace ?: 0L }
                Ligne("Ranger sur la carte SD", detail = "${taille(libre)} libres", icone = Icones.SIM) { nav.aller(Page.CarteSd) }
            }
            Explication("Sous 200 Mo, les photos et les mises à jour risquent d'échouer.")
        }
        section(cle = "plus-tard") {
            BoutonTexte("Plus tard", onClick = nav.retour)
        }
    }
}

/**
 * Le téléphone qui chauffe (maquette l4-surchauffe). L'Accueil suit l'état thermique d'Android : au-delà de
 * « sévère », il baisse la luminosité (l'Appareil photo se ferme de lui-même) et note ce qui a été fait dans
 * `sama_chaleur` ; tout reprend quand le téléphone a refroidi.
 */
object MoteurChaleur {
    class Etat(val depuis: Long, val luminosite: Boolean, val appareil: Boolean)

    fun etat(c: Context): Etat? = try {
        JSONObject(Settings.Global.getString(c.contentResolver, "sama_chaleur") ?: "").let {
            Etat(it.optLong("depuis"), it.optBoolean("luminosite"), it.optBoolean("appareil"))
        }
    } catch (_: Exception) {
        null
    }

    /** La température de la batterie, en degrés. */
    fun temperature(c: Context): Float? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }

    fun chaud(c: Context) = c.getSystemService(PowerManager::class.java).currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
}

@Composable
fun PageSurchauffe(nav: Nav) {
    val c = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            v++
        }
    }
    val etat = remember(v) { MoteurChaleur.etat(c) }
    val chaud = remember(v) { MoteurChaleur.chaud(c) }
    val degres = remember(v) { MoteurChaleur.temperature(c) }
    val heure = etat?.depuis?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("H:mm")) }
    PageReglages(
        titre = if (chaud) "Le téléphone chauffe" else "Le téléphone a refroidi",
        sousTitre = listOfNotNull(degres?.let { "${it.toInt()} °C" }, heure?.let { if (chaud) "depuis $it" else "chaud depuis $it" }).joinToString(" · ").ifBlank { null },
        retour = nav.retour,
    ) {
        section(if (chaud) "Pour le refroidir, Sama a" else "Pour le refroidir, Sama avait", cle = "fait") {
            if (etat?.luminosite == true) Ligne("Baissé la luminosité", icone = Icones.SOLEIL, fin = Fin.Rien)
            if (etat?.appareil == true) Ligne("Fermé l'appareil photo", icone = Icones.APPAREIL, fin = Fin.Rien)
            Ligne("Laissé Android ralentir le téléphone", detail = "Les jeux et les vidéos peuvent saccader un moment", icone = Icones.ECONOMISEUR, fin = Fin.Rien)
            Explication(
                if (chaud) "Posez-le à l'ombre, retirez la coque, arrêtez la charge un moment. Tout reprend seul quand il a refroidi."
                else if (etat?.luminosite == true) "Tout a repris : la luminosité est revenue comme avant." else "Tout a repris.",
            )
        }
        section(cle = "compris") {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Compris", modifier = Modifier.padding(end = 12.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = LocalBanco.current.encre),
                )
                Avancer(true, "Compris", nav.retour)
            }
        }
    }
}
