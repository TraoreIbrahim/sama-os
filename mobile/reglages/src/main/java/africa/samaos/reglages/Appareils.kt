package africa.samaos.reglages

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import africa.samaos.banco.Icones
import kotlinx.coroutines.delay

/** Le Bluetooth, avec les droits des Paramètres (BLUETOOTH_PRIVILEGED, accordé à la clé de la plateforme). */
@SuppressLint("MissingPermission")
object MoteurBluetooth {
    fun adaptateur(c: Context): BluetoothAdapter? = c.getSystemService(BluetoothManager::class.java)?.adapter

    fun actif(c: Context) = adaptateur(c)?.isEnabled == true

    @Suppress("DEPRECATION")
    fun activer(c: Context, oui: Boolean) {
        val a = adaptateur(c) ?: return
        try {
            if (oui) a.enable() else a.disable()
        } catch (_: Exception) {
        }
    }

    fun nom(c: Context): String = try {
        adaptateur(c)?.name.orEmpty()
    } catch (_: SecurityException) {
        ""
    }

    fun renommer(c: Context, nom: String) = try {
        adaptateur(c)?.setName(nom) == true
    } catch (_: SecurityException) {
        false
    }

    fun associes(c: Context): List<BluetoothDevice> = try {
        adaptateur(c)?.bondedDevices.orEmpty().sortedBy { it.alias ?: it.name ?: it.address }
    } catch (_: SecurityException) {
        emptyList()
    }

    fun nomAppareil(d: BluetoothDevice): String = try {
        d.alias ?: d.name ?: d.address
    } catch (_: SecurityException) {
        d.address
    }

    fun connecte(d: BluetoothDevice): Boolean = try {
        d.javaClass.getMethod("isConnected").invoke(d) as Boolean
    } catch (_: Exception) {
        false
    }

    fun batterie(d: BluetoothDevice): Int? = try {
        (d.javaClass.getMethod("getBatteryLevel").invoke(d) as Int).takeIf { it in 0..100 }
    } catch (_: Exception) {
        null
    }

    /** Écouteurs, enceinte, ordinateur… : d'après la classe que l'appareil annonce. */
    fun genre(d: BluetoothDevice): Pair<String, String> {
        val cl = try {
            d.bluetoothClass
        } catch (_: SecurityException) {
            null
        }
        return when (cl?.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> when (cl.deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER, BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
                BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> Icones.ENCEINTE to "Musique"
                BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> Icones.ENCEINTE to "Voiture · musique et appels"
                else -> Icones.ECOUTEURS to "Musique et appels"
            }
            BluetoothClass.Device.Major.COMPUTER -> Icones.TELEPHONE to "Ordinateur"
            BluetoothClass.Device.Major.PHONE -> Icones.TELEPHONE to "Téléphone"
            BluetoothClass.Device.Major.PERIPHERAL -> Icones.CLAVIER to "Clavier ou souris"
            BluetoothClass.Device.Major.WEARABLE -> Icones.HORLOGE to "Montre ou bracelet"
            else -> Icones.BLUETOOTH to "Appareil"
        }
    }

    /** Connecter ou déconnecter un appareil déjà associé (toutes ses fonctions : musique, appels…). */
    fun connecter(d: BluetoothDevice, oui: Boolean): Boolean = try {
        d.javaClass.getMethod(if (oui) "connect" else "disconnect").invoke(d)
        true
    } catch (_: Exception) {
        false
    }

    fun oublier(d: BluetoothDevice): Boolean = try {
        d.javaClass.getMethod("removeBond").invoke(d) as Boolean
    } catch (_: Exception) {
        false
    }

    fun associer(d: BluetoothDevice): Boolean = try {
        d.createBond()
    } catch (_: SecurityException) {
        false
    }

    fun chercher(c: Context, oui: Boolean) {
        try {
            val a = adaptateur(c) ?: return
            if (oui) a.startDiscovery() else a.cancelDiscovery()
        } catch (_: SecurityException) {
        }
    }
}

/** Appareils connectés : Bluetooth, USB, et les appareils Sama associés. */
@Composable
fun PageAppareils(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var resume by remember { mutableStateOf("") }
    LaunchedEffect(reprise) {
        resume = when {
            MoteurBluetooth.adaptateur(c) == null -> "Pas de Bluetooth"
            !MoteurBluetooth.actif(c) -> "Désactivé"
            else -> MoteurBluetooth.associes(c).filter { MoteurBluetooth.connecte(it) }.joinToString(", ") { MoteurBluetooth.nomAppareil(it) }
                .ifBlank { "Aucun appareil connecté" }
        }
    }
    PageReglages(titre = "Appareils connectés", retour = nav.retour) {
        section(cle = "liste") {
            Ligne("Bluetooth", detail = resume, icone = Icones.BLUETOOTH) { nav.aller(Page.Bluetooth) }
            Ligne("USB", detail = "Ce que voit un ordinateur branché", icone = Icones.USB) { nav.aller(Page.Usb) }
            Ligne("Vos autres appareils Sama", detail = "Ordinateur Sama, appels et presse-papiers partagés", icone = Icones.TELEPHONE) {
                nav.aller(Page.AppareilsAssocies)
            }
            Ligne("Impression", detail = "Imprimantes du Wi-Fi", icone = Icones.IMPRIMANTE) { nav.aller(Page.Impression) }
        }
    }
}

/** Le Bluetooth (maquette l3-bluetooth). */
@SuppressLint("MissingPermission")
@Composable
fun PageBluetooth(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var actif by remember { mutableStateOf(MoteurBluetooth.actif(c)) }
    var associes by remember { mutableStateOf(emptyList<BluetoothDevice>()) }
    var trouves by remember { mutableStateOf(emptyList<BluetoothDevice>()) }
    var cherche by remember { mutableStateOf(false) }
    var renommer by remember { mutableStateOf(false) }
    var nom by remember { mutableStateOf(MoteurBluetooth.nom(c)) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, i: Intent) {
                when (i.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                        if (trouves.none { it.address == d.address }) trouves = trouves + d
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> cherche = false
                    else -> version++
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        c.registerReceiver(r, f)
        onDispose {
            c.unregisterReceiver(r)
            MoteurBluetooth.chercher(c, false)
        }
    }
    LaunchedEffect(version, reprise) {
        actif = MoteurBluetooth.actif(c)
        associes = MoteurBluetooth.associes(c)
        nom = MoteurBluetooth.nom(c)
    }
    PageReglages(titre = "Bluetooth", retour = nav.retour) {
        section(cle = "inter") {
            Ligne(
                "Bluetooth",
                detail = if (actif) "Visible sous le nom « $nom »" else "Désactivé",
                icone = Icones.BLUETOOTH,
                fin = Fin.Inter(actif),
            ) {
                MoteurBluetooth.activer(c, !actif)
                actif = !actif
            }
            if (actif) {
                if (renommer) {
                    Champ(nom, "Nom du téléphone", { nom = it.take(30) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        BoutonTexte("Annuler") {
                            renommer = false
                            nom = MoteurBluetooth.nom(c)
                        }
                        BoutonTexte("Garder") {
                            if (nom.isNotBlank()) MoteurBluetooth.renommer(c, nom.trim())
                            renommer = false
                            version++
                        }
                    }
                } else {
                    Ligne("Nom du téléphone", detail = "Changer le nom que voient les autres appareils") { renommer = true }
                }
            }
        }
        if (!actif) return@PageReglages
        val connectes = associes.filter { MoteurBluetooth.connecte(it) }
        if (connectes.isNotEmpty()) {
            section("Connecté", cle = "connectes") {
                connectes.forEach { d ->
                    val (icone, genre) = MoteurBluetooth.genre(d)
                    val batterie = MoteurBluetooth.batterie(d)?.let { " · batterie $it %" }.orEmpty()
                    Ligne(MoteurBluetooth.nomAppareil(d), detail = genre + batterie, icone = icone, choisie = true) {
                        nav.aller(Page.AppareilBluetooth(d.address))
                    }
                }
            }
        }
        val autres = associes.filter { !MoteurBluetooth.connecte(it) }
        if (autres.isNotEmpty()) {
            section("Déjà associés", cle = "associes") {
                autres.forEach { d ->
                    val (icone, _) = MoteurBluetooth.genre(d)
                    Ligne(MoteurBluetooth.nomAppareil(d), icone = icone) { nav.aller(Page.AppareilBluetooth(d.address)) }
                }
            }
        }
        section(if (cherche || trouves.isNotEmpty()) "À portée" else null, cle = "associer") {
            trouves.filter { t -> associes.none { it.address == t.address } }.forEach { d ->
                val (icone, genre) = MoteurBluetooth.genre(d)
                Ligne(MoteurBluetooth.nomAppareil(d), detail = genre, icone = icone, fin = Fin.Rien) {
                    MoteurBluetooth.chercher(c, false)
                    MoteurBluetooth.associer(d)
                }
            }
            if (cherche) Explication("Recherche des appareils à portée… L'appareil doit être en mode association.")
            Ligne(if (cherche) "Arrêter la recherche" else "Associer un appareil", icone = Icones.PLUS, fin = Fin.Rien) {
                if (cherche) {
                    MoteurBluetooth.chercher(c, false)
                    cherche = false
                } else {
                    trouves = emptyList()
                    MoteurBluetooth.chercher(c, true)
                    cherche = true
                }
            }
        }
    }
}

/** Un appareil Bluetooth associé : le connecter, le déconnecter, l'oublier. */
@SuppressLint("MissingPermission")
@Composable
fun PageAppareilBluetooth(adresse: String, nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val appareil = remember(version) { MoteurBluetooth.associes(c).firstOrNull { it.address == adresse } }
    LaunchedEffect(version) {
        delay(1500)
        version++
    }
    val d = appareil
    if (d == null) {
        LaunchedEffect(Unit) { nav.retour() }
        return
    }
    val connecte = MoteurBluetooth.connecte(d)
    val (_, genre) = MoteurBluetooth.genre(d)
    PageReglages(
        titre = MoteurBluetooth.nomAppareil(d),
        sousTitre = listOfNotNull(if (connecte) "Connecté" else "Associé", genre, MoteurBluetooth.batterie(d)?.let { "batterie $it %" }).joinToString(" · "),
        retour = nav.retour,
    ) {
        section(cle = "actions") {
            Ligne(if (connecte) "Déconnecter" else "Connecter", icone = Icones.BLUETOOTH, fin = Fin.Rien) {
                MoteurBluetooth.connecter(d, !connecte)
                version++
            }
            Ligne("Oublier cet appareil", icone = Icones.CORBEILLE, danger = true, fin = Fin.Rien) {
                MoteurBluetooth.oublier(d)
                nav.retour()
            }
        }
        section(cle = "note") {
            Explication("Pour le reconnecter après l'avoir oublié, il faudra l'associer à nouveau.")
        }
    }
}

/** Ce que voit un ordinateur branché en USB (maquette l3-usb). Par défaut : rien, on charge seulement. */
object MoteurUsb {
    const val AUCUNE = 0L
    const val MTP = 1L shl 2
    const val PTP = 1L shl 4
    const val RNDIS = 1L shl 5

    fun branche(c: Context): Boolean {
        val i = c.registerReceiver(null, IntentFilter("android.hardware.usb.action.USB_STATE"))
        return i?.getBooleanExtra("connected", false) == true
    }

    fun fonctions(c: Context): Long = try {
        val um = c.getSystemService(UsbManager::class.java)
        um.javaClass.getMethod("getCurrentFunctions").invoke(um) as Long
    } catch (_: Exception) {
        AUCUNE
    }

    fun regler(c: Context, f: Long): Boolean = try {
        val um = c.getSystemService(UsbManager::class.java)
        um.javaClass.getMethod("setCurrentFunctions", Long::class.javaPrimitiveType).invoke(um, f)
        true
    } catch (_: Exception) {
        false
    }

    /** Ce qu'Android fait quand on branche le téléphone, écran déverrouillé. */
    fun parDefaut(c: Context): Long = try {
        val um = c.getSystemService(UsbManager::class.java)
        um.javaClass.getMethod("getScreenUnlockedFunctions").invoke(um) as Long
    } catch (_: Exception) {
        AUCUNE
    }

    fun reglerParDefaut(c: Context, f: Long): Boolean = try {
        val um = c.getSystemService(UsbManager::class.java)
        um.javaClass.getMethod("setScreenUnlockedFunctions", Long::class.javaPrimitiveType).invoke(um, f)
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun PageUsb(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var branche by remember { mutableStateOf(false) }
    var actuelles by remember { mutableStateOf(MoteurUsb.AUCUNE) }
    LaunchedEffect(version, reprise) {
        branche = MoteurUsb.branche(c)
        actuelles = if (branche) MoteurUsb.fonctions(c) else MoteurUsb.parDefaut(c)
    }
    val choix = listOf(
        Triple(MoteurUsb.AUCUNE, "Charger seulement", "Un ordinateur inconnu ne voit rien du téléphone"),
        Triple(MoteurUsb.MTP, "Transférer des fichiers", "L'ordinateur voit vos documents, photos et musiques"),
        Triple(MoteurUsb.PTP, "Transférer des photos", "L'ordinateur ne voit que les photos"),
        Triple(MoteurUsb.RNDIS, "Partager la connexion", "L'ordinateur utilise votre data mobile"),
    )
    PageReglages(titre = "USB", sousTitre = if (branche) "Câble branché à un ordinateur" else "Aucun câble branché", retour = nav.retour) {
        section(if (branche) "Pour ce branchement" else "Quand un câble est branché", cle = "choix") {
            choix.forEach { (f, titre, detail) ->
                Ligne(titre, detail = detail, fin = Fin.Choix(actuelles == f)) {
                    if (branche) MoteurUsb.regler(c, f) else MoteurUsb.reglerParDefaut(c, f)
                    version++
                }
            }
        }
        section(cle = "note") {
            Explication("Si quelqu'un vous demande de brancher votre téléphone à son ordinateur pour « réparer » ou « débloquer » un compte, refusez : c'est une arnaque courante.")
        }
    }
}

/** Les autres appareils Sama (maquette l3-appareils-associes). L'ordinateur Sama n'est pas encore prêt à s'associer. */
@Composable
fun PageAppareilsAssocies(nav: Nav) {
    PageReglages(titre = "Appareils", sousTitre = "Continuer sur vos autres appareils Sama", retour = nav.retour) {
        section(cle = "aucun") {
            Explication(
                "Aucun appareil associé. Avec un ordinateur Sama, vous prendrez ici les appels et SMS sur l'ordinateur, " +
                    "partagerez le presse-papiers et la connexion en un geste.",
            )
            Explication("L'association arrivera avec la prochaine version de Sama pour ordinateur.")
        }
    }
}
