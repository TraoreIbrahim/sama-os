package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.telephony.TelephonyManager
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.math.roundToInt

/** Ce que les tuiles du Pouls affichent, relu à chaque ouverture. */
data class EtatPouls(
    val wifiActive: Boolean = false,
    val wifiConnecte: Boolean = false,
    val donnees: Boolean = false,
    val operateur: String = "",
    val economie: Boolean = false,
    val luminosite: Float = 0.5f,
    val avion: Boolean = false,
    val pointAcces: Boolean = false,
    val economiseur: Boolean = false,
    val nePasDeranger: Boolean = false,
    val rotation: Boolean = false,
)

/**
 * Ce que le Pouls lit et règle dans le système. Une appli d'accueil ordinaire n'a pas tous les droits :
 * quand il en manque un, on ouvre l'écran système qui convient. Dans Sama OS, le Pouls fera partie du système.
 */
object Systeme {

    fun lire(contexte: Context): EtatPouls {
        val cm = contexte.getSystemService(ConnectivityManager::class.java)
        val reseau = cm?.getNetworkCapabilities(cm.activeNetwork)
        val tm = contexte.getSystemService(TelephonyManager::class.java)
        val donneesActivees = try {
            tm?.isDataEnabled == true
        } catch (_: SecurityException) {
            reseau?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }
        return EtatPouls(
            wifiActive = contexte.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true,
            wifiConnecte = reseau?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            donnees = donneesActivees,
            operateur = tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: tm?.simOperatorName.orEmpty(),
            economie = cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
            luminosite = Settings.System.getInt(contexte.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f,
            avion = Settings.Global.getInt(contexte.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            pointAcces = pointAccesActif(contexte),
            economiseur = contexte.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true,
            nePasDeranger = contexte.getSystemService(NotificationManager::class.java)?.let {
                it.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
            } == true,
            rotation = Settings.System.getInt(contexte.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1,
        )
    }

    private fun pointAccesActif(contexte: Context): Boolean = try {
        val wm = contexte.applicationContext.getSystemService(WifiManager::class.java)
        wm.javaClass.getMethod("isWifiApEnabled").invoke(wm) as Boolean
    } catch (_: Exception) {
        false
    }

    /** Le mode avion est réservé aux applis signées par le constructeur : on ouvre son réglage. */
    fun reglageAvion(contexte: Context) = ouvrir(contexte, Settings.ACTION_AIRPLANE_MODE_SETTINGS)

    /** Le réglage du point d'accès : son nom, son mot de passe, les appareils connectés. */
    fun reglagePointAcces(contexte: Context) = ouvrir(contexte, Intent("android.settings.TETHER_SETTINGS"))

    /**
     * Le point d'accès Wi-Fi, d'une touche (droit TETHER_PRIVILEGED de Sama système). S'il ne démarre pas
     * (pas de SIM, opérateur qui l'interdit), on ouvre son réglage pour que la personne voie pourquoi.
     */
    fun basculerPointAcces(contexte: Context, actif: Boolean) {
        try {
            val tm = contexte.getSystemService("tethering") ?: error("pas de partage de connexion")
            val classe = Class.forName("android.net.TetheringManager")
            if (actif) {
                classe.getMethod("stopTethering", Int::class.javaPrimitiveType).invoke(tm, TETHERING_WIFI)
                return
            }
            val interfaceRappel = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
            val rappel = Proxy.newProxyInstance(interfaceRappel.classLoader, arrayOf(interfaceRappel)) { soi, methode, args ->
                when (methode.name) {
                    "onTetheringFailed" -> {
                        reglagePointAcces(contexte)
                        null
                    }
                    "hashCode" -> System.identityHashCode(soi)
                    "equals" -> soi === args?.firstOrNull()
                    "toString" -> "Point d'accès de Sama"
                    else -> null
                }
            }
            classe.getMethod("startTethering", Int::class.javaPrimitiveType, Executor::class.java, interfaceRappel)
                .invoke(tm, TETHERING_WIFI, contexte.mainExecutor, rappel)
        } catch (_: Exception) {
            reglagePointAcces(contexte)
        }
    }

    private const val TETHERING_WIFI = 0

    /** L'économiseur de batterie, commandé directement (droit POWER_SAVER de Sama système). */
    fun basculerEconomiseur(contexte: Context): Boolean = try {
        val pm = contexte.getSystemService(PowerManager::class.java)
        pm.javaClass.getMethod("setPowerSaveModeEnabled", Boolean::class.javaPrimitiveType).invoke(pm, !pm.isPowerSaveMode) as Boolean
    } catch (_: Exception) {
        ouvrir(contexte, Settings.ACTION_BATTERY_SAVER_SETTINGS)
        false
    }

    /**
     * Ne pas déranger, d'une touche : par l'accès que le système accorde à Sama, ou à défaut par l'accès
     * aux notifications du Pouls ; sans l'un ni l'autre, le réglage qui l'accorde.
     */
    fun basculerNePasDeranger(contexte: Context) {
        val nm = contexte.getSystemService(NotificationManager::class.java) ?: return
        val voulu = if (nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) NotificationManager.INTERRUPTION_FILTER_ALL
        else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val ecouteur = Notifications.service
        when {
            nm.isNotificationPolicyAccessGranted -> nm.setInterruptionFilter(voulu)
            ecouteur != null -> ecouteur.requestInterruptionFilter(
                if (voulu == NotificationManager.INTERRUPTION_FILTER_ALL) NotificationListenerService.INTERRUPTION_FILTER_ALL
                else NotificationListenerService.INTERRUPTION_FILTER_PRIORITY,
            )
            else -> ouvrir(contexte, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    }

    /** La rotation automatique de l'écran. */
    fun basculerRotation(contexte: Context) {
        if (!Settings.System.canWrite(contexte)) {
            demanderReglageLuminosite(contexte)
            return
        }
        val cr = contexte.contentResolver
        val actuel = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (actuel == 1) 0 else 1)
    }

    fun ouvrir(contexte: Context, intent: Intent) {
        try {
            contexte.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            contexte.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun ouvrir(contexte: Context, action: String) = ouvrir(contexte, Intent(action))

    /** Depuis Android 10, une appli ne peut plus couper le Wi-Fi : on ouvre le panneau du système. */
    /** Les Réglages de Sama sont-ils là ? Les tuiles ouvrent alors leurs pages plutôt que les panneaux d'Android. */
    private fun reglagesSama(contexte: Context) = try {
        contexte.packageManager.getPackageInfo(REGLAGES_SAMA, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun panneauWifi(contexte: Context) = ouvrir(
        contexte,
        if (reglagesSama(contexte) || Build.VERSION.SDK_INT < 29) Settings.ACTION_WIFI_SETTINGS else Settings.Panel.ACTION_WIFI,
    )

    fun panneauDonnees(contexte: Context) = ouvrir(
        contexte,
        when {
            reglagesSama(contexte) -> Settings.ACTION_NETWORK_OPERATOR_SETTINGS
            Build.VERSION.SDK_INT >= 29 -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            else -> Settings.ACTION_DATA_ROAMING_SETTINGS
        },
    )

    fun reglagesEconomie(contexte: Context) = ouvrir(
        contexte,
        if (reglagesSama(contexte) || Build.VERSION.SDK_INT < 28) Settings.ACTION_WIRELESS_SETTINGS else Settings.ACTION_DATA_USAGE_SETTINGS,
    )

    fun peutReglerLuminosite(contexte: Context): Boolean = Settings.System.canWrite(contexte)

    fun demanderReglageLuminosite(contexte: Context) = ouvrir(
        contexte,
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${contexte.packageName}")),
    )

    fun reglerLuminosite(contexte: Context, valeur: Float) {
        val cr = contexte.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (valeur * 255).roundToInt().coerceIn(1, 255))
    }

    /** L'écran où l'on donne au Pouls l'accès aux notifications. */
    fun demanderNotifications(contexte: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(contexte, Ecouteur::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        ouvrir(contexte, intent)
    }

    /**
     * Coupe ou rend le volet d'Android. Seule une appli système de Sama OS en a le droit (STATUS_BAR) :
     * ailleurs, l'appel échoue sans bruit et le volet d'Android reste. Si l'Accueil s'arrête, Android
     * rend le volet de lui-même.
     */
    fun voletAndroid(contexte: Context, ouvert: Boolean) {
        if (contexte.checkSelfPermission("android.permission.STATUS_BAR") != PackageManager.PERMISSION_GRANTED) return
        try {
            val barre = contexte.getSystemService("statusbar") ?: return
            barre.javaClass.getMethod("disable", Int::class.javaPrimitiveType)
                .invoke(barre, if (ouvert) 0 else DESACTIVER_VOLET)
        } catch (_: Exception) {
            // Version d'Android sans cette méthode : on garde le volet d'Android.
        }
    }

    /** StatusBarManager.DISABLE_EXPAND : le volet ne se déroule plus. */
    private const val DESACTIVER_VOLET = 0x00010000

    /** L'identifiant de la caméra qui porte un flash, s'il y en a une. */
    fun cameraAvecLampe(contexte: Context): String? {
        val cm = contexte.getSystemService(CameraManager::class.java) ?: return null
        return try {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }
}
