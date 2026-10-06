package africa.samaos.reglages

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.Locale

/** L'état de la batterie, tel qu'Android le donne aux Paramètres. */
class EtatBatterie(
    val niveau: Int,
    val enCharge: Boolean,
    val temperature: Float?,
    val cycles: Int?,
    val sante: Int,
    val capacite: Int?,
    val restant: Long?,
)

object MoteurBatterie {
    fun lire(c: Context): EtatBatterie {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = c.getSystemService(BatteryManager::class.java)
        val niveau = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val cycles = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it >= 0 }
        val capacite = try {
            bm.getIntProperty(10).takeIf { it in 1..100 } // BATTERY_PROPERTY_STATE_OF_HEALTH
        } catch (_: Exception) {
            null
        }
        val restant = try {
            val pm = c.getSystemService(PowerManager::class.java)
            (pm.javaClass.getMethod("getBatteryDischargePrediction").invoke(pm) as java.time.Duration?)?.toMillis()
        } catch (_: Exception) {
            null
        }
        return EtatBatterie(
            niveau = niveau,
            enCharge = bm.isCharging,
            temperature = temp,
            cycles = cycles,
            sante = i?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN,
            capacite = capacite,
            restant = restant,
        )
    }

    fun economiseur(c: Context) = c.getSystemService(PowerManager::class.java).isPowerSaveMode

    fun reglerEconomiseur(c: Context, oui: Boolean) = try {
        val pm = c.getSystemService(PowerManager::class.java)
        pm.javaClass.getMethod("setPowerSaveModeEnabled", Boolean::class.javaPrimitiveType).invoke(pm, oui) as Boolean
    } catch (_: Exception) {
        false
    }

    /** Le seuil où l'économiseur se lance seul (0 : jamais). */
    fun seuil(c: Context) = Settings.Global.getInt(c.contentResolver, "low_power_trigger_level", 0)

    fun reglerSeuil(c: Context, pourcent: Int) {
        Settings.Global.putInt(c.contentResolver, "automatic_power_save_mode", 0)
        Settings.Global.putInt(c.contentResolver, "low_power_trigger_level", pourcent)
    }

    fun pourcentageVisible(c: Context) = Settings.System.getInt(c.contentResolver, "status_bar_show_battery_percent", 0) == 1

    fun reglerPourcentage(c: Context, oui: Boolean) = Settings.System.putInt(c.contentResolver, "status_bar_show_battery_percent", if (oui) 1 else 0)

    /** « Environ 1 jour, jusqu'à demain 15 h ». */
    fun resumeRestant(ms: Long?): String? {
        if (ms == null || ms <= 0) return null
        val h = ms / 3_600_000
        val fin = LocalDateTime.now().plusSeconds(ms / 1000)
        val quand = when (fin.toLocalDate()) {
            java.time.LocalDate.now() -> "jusqu'à ${fin.hour} h"
            java.time.LocalDate.now().plusDays(1) -> "jusqu'à demain ${fin.hour} h"
            else -> null
        }
        val duree = if (h >= 20) "Environ 1 jour" else "Environ $h h"
        return listOfNotNull(duree, quand).joinToString(", ")
    }
}

/** La batterie (maquette l3-batterie). */
@Composable
fun PageBatterie(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var e by remember { mutableStateOf<EtatBatterie?>(null) }
    var eco by remember { mutableStateOf(false) }
    var pourcent by remember { mutableStateOf(false) }
    LaunchedEffect(version) {
        while (true) {
            e = MoteurBatterie.lire(c)
            eco = MoteurBatterie.economiseur(c)
            pourcent = MoteurBatterie.pourcentageVisible(c)
            delay(5000)
        }
    }
    val etat = e
    PageReglages(
        titre = etat?.let { "${it.niveau} %" } ?: "Batterie",
        sousTitre = etat?.let { if (it.enCharge) "En charge" else MoteurBatterie.resumeRestant(it.restant) ?: "Batterie" },
        retour = nav.retour,
    ) {
        if (etat != null) {
            section(cle = "jauge") {
                val b = LocalBanco.current
                Jauge(etat.niveau / 100f, if (etat.niveau <= 15) b.danger else if (etat.enCharge) b.foret else b.laterite, Modifier.padding(vertical = 8.dp))
            }
        }
        section(cle = "reglages") {
            Ligne("Économiseur de batterie", detail = if (eco) "Activé" else "Désactivé", icone = Icones.ECONOMISEUR) { nav.aller(Page.Economiseur) }
            Ligne("Santé de la batterie", detail = etat?.let { nomSante(it.sante) }, icone = Icones.BATTERIE) { nav.aller(Page.SanteBatterie) }
            Ligne("Pourcentage dans la barre d'état", icone = Icones.INFO, fin = Fin.Inter(pourcent)) {
                MoteurBatterie.reglerPourcentage(c, !pourcent)
                version++
            }
        }
        section("Ce qui consomme", cle = "conso") {
            Explication("Le thème Nuit, l'économiseur et la pause des applis en arrière-plan font gagner le plus.")
            Ligne("Usage par appli", detail = "Depuis la dernière charge complète", icone = Icones.APPLI) {
                nav.android(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            }
        }
    }
}

fun nomSante(s: Int) = when (s) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Bonne santé"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Trop chaude"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Usée"
    BatteryManager.BATTERY_HEALTH_COLD -> "Trop froide"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Tension trop forte"
    else -> "Inconnue"
}

/** L'économiseur de batterie (maquette l3-economiseur). */
@Composable
fun PageEconomiseur(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var actif by remember { mutableStateOf(false) }
    var seuil by remember { mutableIntStateOf(0) }
    var niveau by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) {
        actif = MoteurBatterie.economiseur(c)
        seuil = MoteurBatterie.seuil(c)
        niveau = MoteurBatterie.lire(c).niveau
    }
    PageReglages(titre = "Économiseur", sousTitre = "Pour tenir la journée", retour = nav.retour) {
        section(cle = "inter") {
            Ligne(
                "Économiseur de batterie",
                detail = (if (actif) "Allumé" else "Éteint") + " · $niveau %",
                icone = Icones.ECONOMISEUR,
                fin = Fin.Inter(actif),
            ) {
                MoteurBatterie.reglerEconomiseur(c, !actif)
                version++
            }
            Explication("Il assombrit l'écran, met les applis en arrière-plan au repos et retarde les mises à jour.")
        }
        section("Se lance tout seul", cle = "seuil") {
            listOf(0 to "Jamais", 10 to "À 10 %", 20 to "À 20 %", 30 to "À 30 %").forEach { (v, nom) ->
                Ligne(nom, fin = Fin.Choix(seuil == v)) {
                    MoteurBatterie.reglerSeuil(c, v)
                    version++
                }
            }
        }
        section(cle = "note") {
            Explication("Le thème Nuit et la pause des applis en arrière-plan font gagner jusqu'à plusieurs heures.")
        }
    }
}

/** La santé de la batterie (maquette l3-sante-batterie). */
@Composable
fun PageSanteBatterie(nav: Nav) {
    val c = LocalContext.current
    val e = remember { MoteurBatterie.lire(c) }
    PageReglages(titre = nomSante(e.sante), sousTitre = "La batterie de votre téléphone", retour = nav.retour) {
        section("Capacité", cle = "capacite") {
            if (e.capacite != null) {
                Ligne("${e.capacite} % de la capacité d'origine", detail = "Mesurée par la batterie", fin = Fin.Rien)
                Jauge(e.capacite / 100f, LocalBanco.current.foret)
            } else {
                Explication("Cette batterie ne dit pas sa capacité restante.")
            }
        }
        section("Repères", cle = "reperes") {
            e.cycles?.let { Ligne("Cycles de charge", icone = Icones.MISE_A_JOUR, fin = Fin.Valeur("$it")) }
            e.temperature?.let { t ->
                val etat = when {
                    t >= 45 -> "trop chaude"
                    t >= 38 -> "chaude"
                    else -> "normale"
                }
                Ligne("Température", detail = String.format(Locale.FRENCH, "%.0f °C, %s", t, etat), icone = Icones.SOLEIL, fin = Fin.Rien)
            }
        }
        section(cle = "conseils") {
            Explication("Pour garder la batterie plus d'années : évitez de la laisser au soleil et de la vider complètement.")
        }
    }
}

/** Ce qui occupe le téléphone, par sorte de fichiers. */
class Occupation(val libre: Long, val total: Long, val parts: List<Triple<String, Long, Color>>, val caches: Long)

object MoteurStockage {
    fun lire(c: Context): Occupation? = try {
        val ssm = c.getSystemService(StorageStatsManager::class.java)
        val uuid = StorageManager.UUID_DEFAULT
        val total = ssm.getTotalBytes(uuid)
        val libre = ssm.getFreeBytes(uuid)
        val ext = ssm.queryExternalStatsForUser(uuid, Process.myUserHandle())
        val user = ssm.queryStatsForUser(uuid, Process.myUserHandle())
        val applis = user.appBytes + user.dataBytes - ext.totalBytes.coerceAtMost(user.dataBytes)
        val connus = ext.videoBytes + ext.imageBytes + ext.audioBytes + applis.coerceAtLeast(0)
        val autres = (ext.totalBytes - ext.videoBytes - ext.imageBytes - ext.audioBytes).coerceAtLeast(0)
        val systeme = (total - libre - connus - autres).coerceAtLeast(0)
        Occupation(
            libre, total,
            listOf(
                Triple("Vidéos", ext.videoBytes, Color(0xFFB5532F)),
                Triple("Images", ext.imageBytes, Color(0xFFE2A62B)),
                Triple("Applis", applis.coerceAtLeast(0), Color(0xFF3D5A99)),
                Triple("Audio", ext.audioBytes, Color(0xFF5B3F6E)),
                Triple("Documents et autres", autres, Color(0xFF6E5038)),
                Triple("Système Sama", systeme, Color(0xFF8F8476)),
            ),
            user.cacheBytes,
        )
    } catch (_: Exception) {
        null
    }

    /** Vider les caches de toutes les applis (comme « Libérer de l'espace » des Paramètres). */
    fun viderCaches(c: Context, octets: Long) = try {
        val pm = c.packageManager
        val type = Class.forName("android.content.pm.IPackageDataObserver")
        // Android libère jusqu'à atteindre la place libre demandée : la place actuelle plus les fichiers temporaires.
        val libre = c.getSystemService(StorageStatsManager::class.java).getFreeBytes(StorageManager.UUID_DEFAULT)
        pm.javaClass.getMethod("freeStorageAndNotify", String::class.java, Long::class.javaPrimitiveType, type)
            .invoke(pm, null, libre + octets, null)
        true
    } catch (_: Exception) {
        false
    }
}

/** Le stockage (maquette l3-stockage). */
@Composable
fun PageStockage(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var o by remember { mutableStateOf<Occupation?>(null) }
    LaunchedEffect(version) { o = withContext(Dispatchers.IO) { MoteurStockage.lire(c) } }
    val occ = o
    PageReglages(
        titre = occ?.let { taille(it.libre) + " libres" } ?: "Stockage",
        sousTitre = occ?.let { "sur " + taille(it.total) },
        retour = nav.retour,
    ) {
        if (occ == null) return@PageReglages
        section(cle = "barre") {
            BarreStockage(occ)
            Spacer(Modifier.height(8.dp))
            if (occ.caches > 50_000_000) {
                Ligne("Libérer de l'espace", detail = "${taille(occ.caches)} de fichiers temporaires des applis", icone = Icones.CORBEILLE) {
                    MoteurStockage.viderCaches(c, occ.caches)
                    version++
                }
            }
        }
        section("Ce qui occupe", cle = "parts") {
            occ.parts.filter { it.second > 0 }.forEach { (nom, octets, couleur) ->
                Ligne(nom, debut = { Box(Modifier.size(12.dp).background(couleur, CircleShape)) }, fin = Fin.Valeur(taille(octets)))
            }
        }
        section(cle = "place") {
            // Le téléphone presque plein (maquette l4-stockage-plein) : Fichiers sait ce qui peut partir.
            Ligne("Faire de la place", detail = "Vidéos reçues, doublons, fichiers temporaires : Fichiers › Nettoyer", icone = Icones.DOSSIER) {
                try {
                    c.startActivity(Intent().setClassName("africa.samaos.fichiers", "africa.samaos.fichiers.Fichiers").putExtra("vue", "nettoyer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
            if (occ.libre < 1_000_000_000L) Explication("Sous 200 Mo, les photos et les mises à jour risquent d'échouer.", LocalBanco.current.lateriteTexte)
        }
        section(cle = "carte") {
            Ligne("Carte SD", detail = "Où ranger photos et téléchargements", icone = Icones.SIM) { nav.aller(Page.CarteSd) }
        }
    }
}

@Composable
private fun BarreStockage(o: Occupation) {
    val b = LocalBanco.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(b.sol2, androidx.compose.foundation.shape.RoundedCornerShape(7.dp)),
    ) {
        o.parts.filter { it.second > 0 }.forEach { (_, octets, couleur) ->
            val part = (octets.toFloat() / o.total).coerceIn(0.005f, 1f)
            Box(Modifier.weight(part).height(14.dp).background(couleur))
        }
        val libre = (o.libre.toFloat() / o.total).coerceIn(0.005f, 1f)
        Box(Modifier.weight(libre).height(14.dp))
    }
}

/** La carte SD (maquette l3-carte-sd). */
@Composable
fun PageCarteSd(nav: Nav) {
    val c = LocalContext.current
    val volumes = remember { c.getSystemService(StorageManager::class.java).storageVolumes.filter { it.isRemovable } }
    PageReglages(titre = "Carte SD", sousTitre = if (volumes.isEmpty()) "Aucune carte" else volumes.first().getDescription(c), retour = nav.retour) {
        if (volumes.isEmpty()) {
            section(cle = "aucune") { Explication("Glissez une carte microSD dans le téléphone pour y ranger photos, vidéos et téléchargements.") }
            return@PageReglages
        }
        section(cle = "actions") {
            Ligne("Éjecter", detail = "Avant de retirer la carte", icone = Icones.SIM) { nav.android(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            Ligne("Formater", detail = "Efface tout ce qu'elle contient", icone = Icones.CORBEILLE, danger = true) { nav.android(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
        }
    }
}
