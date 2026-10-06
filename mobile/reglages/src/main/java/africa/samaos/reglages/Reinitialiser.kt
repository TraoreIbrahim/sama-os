package africa.samaos.reglages

import android.app.Activity
import android.app.KeyguardManager
import android.app.usage.StorageStatsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Process
import android.os.storage.StorageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Remettre à zéro : les réseaux (fonctions système des Paramètres d'Android), ou tout le téléphone. */
object MoteurReinit {
    /**
     * Oublie les Wi-Fi, coupe les VPN et le DNS privé, remet le mode avion et le partage de connexion à l'état
     * de départ, oublie les appareils Bluetooth associés. Comme « Réinitialiser les paramètres réseau » d'Android.
     */
    fun reseaux(c: Context): Boolean {
        var ok = true
        try {
            val wm = c.getSystemService(WifiManager::class.java)
            wm.javaClass.getMethod("factoryReset").invoke(wm)
        } catch (_: Exception) {
            ok = false
        }
        try {
            val cm = c.getSystemService(ConnectivityManager::class.java)
            cm.javaClass.getMethod("factoryReset").invoke(cm)
        } catch (_: Exception) {
            ok = false
        }
        try {
            c.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.forEach { d ->
                d.javaClass.getMethod("removeBond").invoke(d)
            }
        } catch (_: Exception) {
        }
        return ok
    }

    /** Ce qui partira si l'on efface tout, pour le dire avant. */
    class Bilan(val espaces: List<String>, val medias: Long, val comptes: Int)

    fun bilan(c: Context): Bilan {
        val medias = try {
            c.getSystemService(StorageStatsManager::class.java).queryExternalStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle()).let { it.imageBytes + it.videoBytes }
        } catch (_: Exception) {
            0L
        }
        val comptes = try {
            android.accounts.AccountManager.get(c).accounts.size
        } catch (_: Exception) {
            0
        }
        return Bilan(EspaceActif.tous(c), medias, comptes)
    }

    /** Effacer toutes les données : la demande que les Paramètres d'Android envoient au système (droit MASTER_CLEAR). */
    fun toutEffacer(c: Context) {
        c.sendBroadcast(
            Intent("android.intent.action.FACTORY_RESET")
                .setPackage("android")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra("android.intent.extra.REASON", "Réglages de Sama")
                .putExtra("android.intent.extra.WIPE_EXTERNAL_STORAGE", true),
        )
    }
}

/** Réinitialiser (maquette l4-reinit-options). */
@Composable
fun PageReinitialiser(nav: Nav) {
    val c = LocalContext.current
    var confirmer by remember { mutableStateOf(false) }
    var fait by remember { mutableStateOf<String?>(null) }
    PageReglages(titre = "Réinitialiser", retour = nav.retour) {
        section(cle = "options") {
            Ligne("Réseaux et Bluetooth", detail = "Oublie les Wi-Fi, les VPN et les appareils associés", icone = Icones.WIFI) { confirmer = true }
            if (confirmer) {
                Confirmation(
                    "Les Wi-Fi enregistrés, les VPN, le DNS privé et les appareils Bluetooth seront oubliés. Les cartes SIM et vos fichiers ne bougent pas.",
                    "Réinitialiser",
                    annuler = { confirmer = false },
                ) {
                    confirmer = false
                    fait = if (MoteurReinit.reseaux(c)) "Réseaux et Bluetooth remis à zéro." else "Une partie n'a pas pu être remise à zéro."
                }
            }
            fait?.let { Explication(it) }
            Ligne("Supprimer un Espace", detail = "Efface ses applis, ses comptes et ses fichiers", icone = Icones.CADENAS) {
                try {
                    c.startActivity(Intent("africa.samaos.action.REGLAGES_ESPACES").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
            Ligne("Effacer toutes les données", detail = "Remet le téléphone comme au premier jour", icone = Icones.CORBEILLE, danger = true) { nav.aller(Page.ToutEffacer) }
        }
        section(cle = "prudence") {
            Explication("Personne n'a besoin que vous effaciez votre téléphone pour vous aider. Un « agent » qui vous le demande cherche à prendre votre compte.", LocalBanco.current.lateriteTexte)
        }
    }
}

/** Tout effacer ? (maquette l4-reinit-confirmation) : ce qui partira, puis le code. */
@Composable
fun PageToutEffacer(nav: Nav) {
    val c = LocalContext.current
    var bilan by remember { mutableStateOf<MoteurReinit.Bilan?>(null) }
    var derniere by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bilan = withContext(Dispatchers.IO) { MoteurReinit.bilan(c) } }
    val code = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) MoteurReinit.toutEffacer(c)
    }
    PageReglages(titre = "Tout effacer ?", sousTitre = "Le téléphone repartira de zéro.", retour = nav.retour) {
        val b = bilan ?: return@PageReglages
        section("Partiront du téléphone", cle = "partent") {
            Ligne(
                if (b.espaces.size > 1) "Les ${b.espaces.size} Espaces" else "L'Espace ${b.espaces.firstOrNull() ?: "Maison"}",
                detail = b.espaces.joinToString(", ") + ", avec leurs applis",
                icone = Icones.CADENAS, fin = Fin.Rien,
            )
            Ligne("Les photos et vidéos", detail = "${taille(b.medias)} · seulement sur ce téléphone pour l'instant", icone = Icones.PAYSAGE, fin = Fin.Rien)
            Ligne(
                "Les comptes", detail = if (b.comptes == 0) "Aucun compte en ligne sur ce téléphone" else "${b.comptes} compte${if (b.comptes > 1) "s" else ""} à reconnecter ensuite",
                icone = Icones.PERSONNE, fin = Fin.Rien,
            )
            val avecCode = remember { c.getSystemService(KeyguardManager::class.java).isDeviceSecure }
            Explication("Les cartes SIM ne sont pas touchées. " + if (avecCode) "Votre code vous sera demandé." else "Ce téléphone n'a pas de code : une dernière question vous sera posée.")
        }
        section(cle = "faire") {
            if (!derniere) {
                Confirmation("Rien ne pourra être récupéré sans sauvegarde.", "Tout effacer", annuler = nav.retour) {
                    val km = c.getSystemService(KeyguardManager::class.java)
                    if (km.isDeviceSecure) {
                        code.launch(km.createConfirmDeviceCredentialIntent("Tout effacer", "Entrez votre code pour effacer le téléphone."))
                    } else {
                        derniere = true
                    }
                }
            } else {
                Confirmation("Ce téléphone n'a pas de code : dernière chance de changer d'avis.", "Effacer maintenant", annuler = { derniere = false }) {
                    MoteurReinit.toutEffacer(c)
                }
            }
        }
    }
}
