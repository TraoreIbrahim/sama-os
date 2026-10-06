package africa.samaos.reglages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** L'état du réseau que montrent les pages « Réseau et data ». */
class EtatReseau(
    val avion: Boolean,
    val economieData: Boolean,
    val pointAcces: Boolean,
    val resumeSim: String,
    val resumeVpn: String,
    private val relire: () -> Unit,
) {
    /** Le mode avion, avec le droit des Paramètres (ConnectivityManager.setAirplaneMode, réservé au système). */
    fun basculerAvion(c: Context) {
        try {
            val cm = c.getSystemService(ConnectivityManager::class.java)
            cm.javaClass.getMethod("setAirplaneMode", Boolean::class.javaPrimitiveType).invoke(cm, !avion)
        } catch (_: Exception) {
        }
        relire()
    }

    /** L'économie de data d'Android (les applis en arrière-plan n'ont plus la data). */
    fun basculerEconomieData(c: Context) {
        try {
            val npm = Class.forName("android.net.NetworkPolicyManager")
            val m = npm.getMethod("from", Context::class.java).invoke(null, c)
            npm.getMethod("setRestrictBackground", Boolean::class.javaPrimitiveType).invoke(m, !economieData)
        } catch (_: Exception) {
        }
        relire()
    }
}

object MoteurReseau {
    fun avion(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun economieData(c: Context): Boolean = try {
        val npm = Class.forName("android.net.NetworkPolicyManager")
        val m = npm.getMethod("from", Context::class.java).invoke(null, c)
        npm.getMethod("getRestrictBackground").invoke(m) as Boolean
    } catch (_: Exception) {
        false
    }

    fun pointAcces(c: Context): Boolean = try {
        val wm = c.applicationContext.getSystemService(WifiManager::class.java)
        wm.javaClass.getMethod("isWifiApEnabled").invoke(wm) as Boolean
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun sims(c: Context): List<SubscriptionInfo> = try {
        c.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }
    } catch (_: SecurityException) {
        emptyList()
    }

    fun nomSim(s: SubscriptionInfo) = s.displayName?.toString()?.ifBlank { null } ?: s.carrierName?.toString().orEmpty()

    @SuppressLint("MissingPermission")
    fun numero(c: Context, s: SubscriptionInfo): String? = try {
        val sm = c.getSystemService(SubscriptionManager::class.java)
        (if (android.os.Build.VERSION.SDK_INT >= 33) sm.getPhoneNumber(s.subscriptionId) else @Suppress("DEPRECATION") s.number)
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /** « 07 08 •• •• 30 » : le numéro reconnaissable sans s'afficher en entier. */
    fun numeroDiscret(n: String?): String? {
        if (n == null) return null
        var d = n.filter { it.isDigit() }
        if (d.startsWith("225") && d.length == 13) d = d.drop(3)
        if (d.length == 11 && d.startsWith("1")) d = d.drop(1)
        if (d.length < 8) return n
        return "${d.take(2)} ${d.substring(2, 4)} •• •• ${d.takeLast(2)}"
    }

    @SuppressLint("MissingPermission")
    fun generation(c: Context, s: SubscriptionInfo): String = try {
        val tm = c.getSystemService(TelephonyManager::class.java).createForSubscriptionId(s.subscriptionId)
        when (tm.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> ""
        }
    } catch (_: Exception) {
        ""
    }

    fun vpnActif(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    /** Les applis de VPN installées dans cet Espace. */
    fun applisVpn(c: Context) = c.packageManager.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), 0)

    fun dnsPrive(c: Context): Pair<String, String?> =
        (Settings.Global.getString(c.contentResolver, "private_dns_mode") ?: "opportunistic") to
            Settings.Global.getString(c.contentResolver, "private_dns_specifier")

    fun reglerDnsPrive(c: Context, mode: String, serveur: String? = null) {
        Settings.Global.putString(c.contentResolver, "private_dns_mode", mode)
        if (serveur != null) Settings.Global.putString(c.contentResolver, "private_dns_specifier", serveur)
    }

    @SuppressLint("MissingPermission")
    fun donneesActives(c: Context): Boolean = try {
        c.getSystemService(TelephonyManager::class.java).isDataEnabled
    } catch (_: Exception) {
        false
    }

    fun reglerDonnees(c: Context, oui: Boolean) = try {
        val tm = c.getSystemService(TelephonyManager::class.java)
        tm.javaClass.getMethod("setDataEnabled", Boolean::class.javaPrimitiveType).invoke(tm, oui)
        true
    } catch (_: Exception) {
        false
    }

    /** Choisir la SIM des appels, des SMS ou de la data (fonctions des Paramètres, réservées au système). */
    fun choisirSim(c: Context, usage: String, id: Int) = try {
        val sm = c.getSystemService(SubscriptionManager::class.java)
        val nom = when (usage) {
            "appels" -> "setDefaultVoiceSubscriptionId"
            "sms" -> "setDefaultSmsSubId"
            else -> "setDefaultDataSubId"
        }
        sm.javaClass.getMethod(nom, Int::class.javaPrimitiveType).invoke(sm, id)
        true
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun itinerance(c: Context, id: Int): Boolean = try {
        c.getSystemService(TelephonyManager::class.java).createForSubscriptionId(id).isDataRoamingEnabled
    } catch (_: Exception) {
        false
    }

    fun reglerItinerance(c: Context, id: Int, oui: Boolean) = try {
        val tm = c.getSystemService(TelephonyManager::class.java).createForSubscriptionId(id)
        tm.javaClass.getMethod("setDataRoamingEnabled", Boolean::class.javaPrimitiveType).invoke(tm, oui)
        true
    } catch (_: Exception) {
        false
    }

    fun renommerSim(c: Context, id: Int, nom: String) = try {
        val sm = c.getSystemService(SubscriptionManager::class.java)
        sm.javaClass.getMethod("setDisplayName", String::class.java, Int::class.javaPrimitiveType).invoke(sm, nom, id)
        true
    } catch (_: Exception) {
        false
    }

    /** Le point d'accès : son nom, et s'il s'arrête tout seul quand personne n'est connecté. */
    fun configPointAcces(c: Context): Any? = try {
        val wm = c.applicationContext.getSystemService(WifiManager::class.java)
        wm.javaClass.getMethod("getSoftApConfiguration").invoke(wm)
    } catch (_: Exception) {
        null
    }

    fun nomPointAcces(conf: Any?): String? = try {
        val ssid = conf!!.javaClass.getMethod("getWifiSsid").invoke(conf)
        ssid?.toString()?.removeSurrounding("\"") ?: conf.javaClass.getMethod("getSsid").invoke(conf) as String?
    } catch (_: Exception) {
        try {
            @Suppress("DEPRECATION")
            conf?.javaClass?.getMethod("getSsid")?.invoke(conf) as String?
        } catch (_: Exception) {
            null
        }
    }

    fun arretAuto(conf: Any?): Boolean = try {
        conf!!.javaClass.getMethod("isAutoShutdownEnabled").invoke(conf) as Boolean
    } catch (_: Exception) {
        true
    }

    fun reglerArretAuto(c: Context, conf: Any, oui: Boolean) = try {
        val b = Class.forName("android.net.wifi.SoftApConfiguration\$Builder").getConstructor(conf.javaClass).newInstance(conf)
        b.javaClass.getMethod("setAutoShutdownEnabled", Boolean::class.javaPrimitiveType).invoke(b, oui)
        val nouvelle = b.javaClass.getMethod("build").invoke(b)
        val wm = c.applicationContext.getSystemService(WifiManager::class.java)
        wm.javaClass.getMethod("setSoftApConfiguration", conf.javaClass).invoke(wm, nouvelle) as Boolean
    } catch (_: Exception) {
        false
    }
}

@Composable
fun rememberEtatReseau(): EtatReseau {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val reprise = LocalReprise.current
    var etat by remember { mutableStateOf<EtatReseau?>(null) }
    LaunchedEffect(version, reprise) {
        while (true) {
            etat = withContext(Dispatchers.IO) {
                val sims = MoteurReseau.sims(c)
                EtatReseau(
                    avion = MoteurReseau.avion(c),
                    economieData = MoteurReseau.economieData(c),
                    pointAcces = MoteurReseau.pointAcces(c),
                    resumeSim = when (sims.size) {
                        0 -> "Aucune SIM"
                        1 -> MoteurReseau.nomSim(sims[0])
                        else -> sims.joinToString(" et ") { "SIM ${it.simSlotIndex + 1}" }
                    },
                    resumeVpn = if (MoteurReseau.vpnActif(c)) "VPN connecté" else when (MoteurReseau.dnsPrive(c).first) {
                        "off" -> "DNS privé désactivé"
                        "hostname" -> "DNS privé : " + MoteurReseau.dnsPrive(c).second.orEmpty()
                        else -> "DNS privé automatique"
                    },
                    relire = { version++ },
                )
            }
            delay(2000)
        }
    }
    return etat ?: EtatReseau(false, false, false, "", "", relire = { version++ })
}

/** Le code QR qui partage un Wi-Fi : on le scanne avec l'appareil photo ; le mot de passe reste caché. */
@Composable
fun PartageWifi(ssid: String, motDePasse: String?, ouvert: Boolean) {
    val b = LocalBanco.current
    if (motDePasse == null && !ouvert) return
    fun echapper(t: String) = t.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
    val texte = if (ouvert) "WIFI:T:nopass;S:${echapper(ssid)};;" else "WIFI:T:WPA;S:${echapper(ssid)};P:${echapper(motDePasse!!)};;"
    val matrice = remember(texte) {
        QRCodeWriter().encode(texte, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(6.dp)
                .semantics { contentDescription = "Code QR du réseau $ssid" },
        ) {
            Canvas(Modifier.size(96.dp)) {
                val n = matrice.width
                val m = size.width / n
                for (y in 0 until n) for (x in 0 until n) {
                    if (matrice.get(x, y)) drawRect(Color(0xFF1F1C18), Offset(x * m, y * m), Size(m + 0.5f, m + 0.5f))
                }
            }
        }
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("Partager ce réseau", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre))
            BasicText(
                "Vos proches scannent ce code avec l'appareil photo. Le mot de passe reste caché.",
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
            )
        }
    }
}

/** Les cartes SIM et à quoi chacune sert (maquette l3-sim). */
@Composable
fun PageSim(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var sims by remember { mutableStateOf(emptyList<SubscriptionInfo>()) }
    var donnees by remember { mutableStateOf(false) }
    LaunchedEffect(version, reprise) {
        sims = withContext(Dispatchers.IO) { MoteurReseau.sims(c) }
        donnees = MoteurReseau.donneesActives(c)
    }
    var choix by remember { mutableStateOf<String?>(null) }
    PageReglages(titre = "Cartes SIM", retour = nav.retour) {
        if (sims.isEmpty()) {
            section(cle = "aucune") { Explication("Aucune carte SIM. Glissez une SIM dans le téléphone pour appeler et utiliser la data.") }
            return@PageReglages
        }
        section(cle = "sims") {
            sims.forEach { s ->
                val numero = MoteurReseau.numeroDiscret(MoteurReseau.numero(c, s))
                Ligne(
                    "SIM ${s.simSlotIndex + 1} · ${MoteurReseau.nomSim(s)}",
                    detail = listOfNotNull(numero, MoteurReseau.generation(c, s).ifBlank { null }).joinToString(" · ").ifBlank { null },
                    debut = { PuceSim(s.simSlotIndex + 1, s.iconTint) },
                ) { nav.aller(Page.SimReglages(s.subscriptionId)) }
            }
        }
        section("Utiliser pour", cle = "usages") {
            fun nomDe(id: Int) = sims.firstOrNull { it.subscriptionId == id }?.let { "SIM ${it.simSlotIndex + 1}" }
            Ligne("Appels", detail = nomDe(SubscriptionManager.getDefaultVoiceSubscriptionId()) ?: "Selon le numéro", icone = Icones.APPEL) { choix = "appels" }
            Ligne("SMS", detail = nomDe(SubscriptionManager.getDefaultSmsSubscriptionId()) ?: "Demander à chaque fois", icone = Icones.MESSAGE) { choix = "sms" }
            Ligne("Data mobile", detail = nomDe(SubscriptionManager.getDefaultDataSubscriptionId()) ?: "Aucune", icone = Icones.DONNEES) { choix = "data" }
            Ligne(
                "Données mobiles",
                detail = if (donnees) "Les applis peuvent utiliser la data" else "Coupées : seulement le Wi-Fi",
                fin = Fin.Inter(donnees),
            ) {
                MoteurReseau.reglerDonnees(c, !donnees)
                version++
            }
        }
        val u = choix
        if (u != null) {
            section(if (u == "appels") "SIM des appels" else if (u == "sms") "SIM des SMS" else "SIM de la data", cle = "choix-$u") {
                val actuel = when (u) {
                    "appels" -> SubscriptionManager.getDefaultVoiceSubscriptionId()
                    "sms" -> SubscriptionManager.getDefaultSmsSubscriptionId()
                    else -> SubscriptionManager.getDefaultDataSubscriptionId()
                }
                sims.forEach { s ->
                    Ligne("SIM ${s.simSlotIndex + 1} · ${MoteurReseau.nomSim(s)}", fin = Fin.Choix(s.subscriptionId == actuel)) {
                        MoteurReseau.choisirSim(c, u, s.subscriptionId)
                        choix = null
                        version++
                    }
                }
            }
        }
    }
}

@Composable
private fun PuceSim(numero: Int, teinte: Int) {
    Box(Modifier.size(28.dp).background(Color(teinte or 0xFF000000.toInt()), CircleShape), contentAlignment = Alignment.Center) {
        BasicText("$numero", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White))
    }
}

/** Les réglages d'une SIM (maquette l3-sim-reglages). */
@Composable
fun PageSimReglages(id: Int, nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var sim by remember { mutableStateOf<SubscriptionInfo?>(null) }
    var itinerance by remember { mutableStateOf(false) }
    var renommer by remember { mutableStateOf(false) }
    var nom by remember { mutableStateOf("") }
    LaunchedEffect(version, reprise) {
        sim = withContext(Dispatchers.IO) { MoteurReseau.sims(c).firstOrNull { it.subscriptionId == id } }
        itinerance = MoteurReseau.itinerance(c, id)
        nom = sim?.let { MoteurReseau.nomSim(it) }.orEmpty()
    }
    val s = sim
    PageReglages(
        titre = s?.let { "SIM ${it.simSlotIndex + 1}" } ?: "SIM",
        sousTitre = s?.let { listOfNotNull(MoteurReseau.nomSim(it), MoteurReseau.numero(c, it)).joinToString(" · ") },
        retour = nav.retour,
    ) {
        if (s == null) return@PageReglages
        section("Repérer la SIM", cle = "nom") {
            if (renommer) {
                Champ(nom, "Nom de la SIM", { nom = it.take(20) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BoutonTexte("Annuler") { renommer = false }
                    BoutonTexte("Garder") {
                        MoteurReseau.renommerSim(c, id, nom.trim())
                        renommer = false
                        version++
                    }
                }
            } else {
                Ligne("Nom", detail = MoteurReseau.nomSim(s)) { renommer = true }
            }
        }
        section("Réseau", cle = "reseau") {
            Ligne("Réseau préféré", detail = MoteurReseau.generation(c, s).ifBlank { "Automatique" }) {
                nav.android(Intent("android.settings.NETWORK_OPERATOR_SETTINGS").putExtra("android.provider.extra.SUB_ID", id))
            }
            Ligne("Points d'accès (APN)", detail = "Réglés par l'opérateur") {
                nav.android(Intent("android.settings.APN_SETTINGS").putExtra("sub_id", id))
            }
            Ligne(
                "Itinérance",
                detail = if (itinerance) "La data marche à l'étranger : attention aux frais" else "Coupée hors du pays de la SIM",
                fin = Fin.Inter(itinerance),
            ) {
                MoteurReseau.reglerItinerance(c, id, !itinerance)
                version++
            }
        }
    }
}

/** Le partage de connexion (maquette l3-point-acces). */
@Composable
fun PagePointAcces(nav: Nav) {
    val c = LocalContext.current
    val etat = rememberEtatReseau()
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var conf by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(version, reprise) { conf = withContext(Dispatchers.IO) { MoteurReseau.configPointAcces(c) } }
    val nom = MoteurReseau.nomPointAcces(conf)
    PageReglages(titre = "Partage de connexion", retour = nav.retour) {
        section(cle = "inter") {
            Ligne(
                "Point d'accès Wi-Fi",
                detail = listOfNotNull(nom, if (etat.pointAcces) "activé" else null).joinToString(" · ").ifBlank { null },
                icone = Icones.POINT_ACCES,
                fin = Fin.Inter(etat.pointAcces),
            ) {
                basculerPointAcces(c, etat.pointAcces)
            }
            Explication("Les appareils connectés utilisent votre data mobile.")
        }
        section("Réglages", cle = "reglages") {
            Ligne("Nom et mot de passe", detail = nom ?: "Choisis par le téléphone") {
                nav.android(Intent("android.settings.TETHER_SETTINGS"))
            }
            conf?.let { cf ->
                Ligne(
                    "S'arrêter si personne n'est connecté",
                    detail = "Après quelques minutes, pour garder la batterie et la data",
                    fin = Fin.Inter(MoteurReseau.arretAuto(cf)),
                ) {
                    MoteurReseau.reglerArretAuto(c, cf, !MoteurReseau.arretAuto(cf))
                    version++
                }
            }
        }
    }
}

/** Allume ou coupe le point d'accès Wi-Fi (TETHER_PRIVILEGED, accordé aux Réglages du système). */
fun basculerPointAcces(c: Context, actif: Boolean) {
    try {
        val tm = c.getSystemService("tethering") ?: return
        val classe = Class.forName("android.net.TetheringManager")
        if (actif) {
            classe.getMethod("stopTethering", Int::class.javaPrimitiveType).invoke(tm, 0)
            return
        }
        val type = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        val rappel = java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { soi, m, args ->
            when (m.name) {
                "hashCode" -> System.identityHashCode(soi)
                "equals" -> soi === args?.firstOrNull()
                "toString" -> "Sama"
                else -> null
            }
        }
        classe.getMethod("startTethering", Int::class.javaPrimitiveType, java.util.concurrent.Executor::class.java, type)
            .invoke(tm, 0, c.mainExecutor, rappel)
    } catch (_: Exception) {
    }
}

/** VPN et DNS privé (maquette l3-vpn). Un VPN ne vaut que pour l'Espace où il est réglé. */
@Composable
fun PageVpn(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var dns by remember { mutableStateOf("opportunistic" to (null as String?)) }
    var vpnActif by remember { mutableStateOf(false) }
    var serveur by remember { mutableStateOf("") }
    var saisie by remember { mutableStateOf(false) }
    LaunchedEffect(version, reprise) {
        dns = MoteurReseau.dnsPrive(c)
        serveur = dns.second.orEmpty()
        vpnActif = MoteurReseau.vpnActif(c)
    }
    val applis = remember { MoteurReseau.applisVpn(c) }
    val espace = EspaceActif.nom(c)
    PageReglages(titre = "VPN et DNS privé", retour = nav.retour) {
        section("VPN", cle = "vpn") {
            applis.forEach { ri ->
                val nom = ri.loadLabel(c.packageManager).toString()
                Ligne(nom, detail = if (vpnActif) "Connecté · seulement dans l'Espace $espace" else "Pas connecté", icone = Icones.CADENAS) {
                    c.packageManager.getLaunchIntentForPackage(ri.serviceInfo.packageName)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            Ligne(
                "Ajouter un VPN",
                detail = "Un VPN ne vaut que pour l'Espace $espace : les autres Espaces passent en direct.",
                icone = Icones.PLUS,
            ) { nav.android(Intent(Settings.ACTION_VPN_SETTINGS)) }
        }
        section("DNS privé", cle = "dns") {
            Ligne("Automatique", detail = "Chiffré quand le réseau le permet", fin = Fin.Choix(dns.first == "opportunistic")) {
                MoteurReseau.reglerDnsPrive(c, "opportunistic")
                saisie = false
                version++
            }
            Ligne("Désactivé", fin = Fin.Choix(dns.first == "off")) {
                MoteurReseau.reglerDnsPrive(c, "off")
                saisie = false
                version++
            }
            Ligne("Serveur choisi", detail = dns.second?.takeIf { dns.first == "hostname" } ?: "Le nom d'un serveur DNS sur TLS", fin = Fin.Choix(dns.first == "hostname")) {
                saisie = true
            }
            if (saisie) {
                Spacer(Modifier.height(8.dp))
                Champ(serveur, "dns.exemple.ci", { serveur = it.trim() })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BoutonTexte("Annuler") { saisie = false }
                    BoutonTexte("Garder") {
                        if (serveur.contains('.')) {
                            MoteurReseau.reglerDnsPrive(c, "hostname", serveur)
                            saisie = false
                            version++
                        }
                    }
                }
            }
        }
    }
}
