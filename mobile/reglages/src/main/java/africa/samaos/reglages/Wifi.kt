package africa.samaos.reglages

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Avancer
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.reflect.Proxy

enum class Securite { OUVERT, PROTEGE, ENTREPRISE }

/** Un réseau Wi-Fi tel que les Réglages le montrent. */
data class ReseauWifi(
    val ssid: String,
    val securite: Securite,
    val niveau: Int,
    val connecte: Boolean,
    val enregistre: Int?,
    val frequence: Int,
)

/**
 * Le Wi-Fi, avec les droits des Paramètres d'Android (NETWORK_SETTINGS, accordé à la clé de la plateforme) :
 * voir les réseaux sans demander la localisation, s'y connecter, les oublier.
 */
object MoteurWifi {
    private fun wm(c: Context) = c.applicationContext.getSystemService(WifiManager::class.java)

    fun actif(c: Context) = wm(c).isWifiEnabled

    @Suppress("DEPRECATION")
    fun activer(c: Context, oui: Boolean) = wm(c).setWifiEnabled(oui)

    @Suppress("DEPRECATION")
    fun scanner(c: Context) = wm(c).startScan()

    fun niveau(c: Context, rssi: Int): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            val wm = wm(c)
            (wm.calculateSignalLevel(rssi) * 4 / wm.maxSignalLevel.coerceAtLeast(1)).coerceIn(0, 4)
        } else {
            @Suppress("DEPRECATION")
            WifiManager.calculateSignalLevel(rssi, 5)
        }

    @Suppress("DEPRECATION")
    fun ssidConnecte(c: Context): String? {
        val info = wm(c).connectionInfo ?: return null
        if (info.networkId == -1) return null
        return info.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun enregistres(c: Context): List<WifiConfiguration> = try {
        wm(c).configuredNetworks.orEmpty()
    } catch (_: SecurityException) {
        emptyList()
    }

    fun securiteDe(capacites: String): Securite = when {
        "EAP" in capacites -> Securite.ENTREPRISE
        "WPA" in capacites || "SAE" in capacites || "PSK" in capacites || "WEP" in capacites -> Securite.PROTEGE
        else -> Securite.OUVERT
    }

    @Suppress("DEPRECATION")
    private fun securiteDe(conf: WifiConfiguration): Securite = when {
        conf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP) -> Securite.ENTREPRISE
        conf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE) && conf.wepKeys.all { it == null } -> Securite.OUVERT
        else -> Securite.PROTEGE
    }

    /** Les réseaux à portée et ceux enregistrés, le réseau connecté d'abord, puis du plus fort au plus faible. */
    @SuppressLint("MissingPermission")
    fun reseaux(c: Context): List<ReseauWifi> {
        val wm = wm(c)
        val connecte = ssidConnecte(c)
        val confs = enregistres(c).associateBy { it.SSID.removeSurrounding("\"") }
        val vus = try {
            wm.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList<ScanResult>()
        }
        @Suppress("DEPRECATION")
        val parSsid = vus.filter { !it.SSID.isNullOrBlank() }.groupBy { it.SSID }.mapValues { (_, l) -> l.maxBy { it.level } }
        val liste = parSsid.values.map { r ->
            @Suppress("DEPRECATION")
            val ssid = r.SSID
            ReseauWifi(ssid, securiteDe(r.capabilities.orEmpty()), niveau(c, r.level), ssid == connecte, confs[ssid]?.networkId, r.frequency)
        }.toMutableList()
        if (connecte != null && liste.none { it.ssid == connecte }) {
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            liste += ReseauWifi(connecte, confs[connecte]?.let { securiteDe(it) } ?: Securite.PROTEGE, niveau(c, info.rssi), true, info.networkId, info.frequency)
        }
        return liste.sortedWith(compareBy({ !it.connecte }, { -it.niveau }, { it.ssid.lowercase() }))
    }

    // Les fonctions du Wi-Fi réservées aux Paramètres du système (@SystemApi), appelées par réflexion.

    private fun ecouteur(c: Context, fini: (Boolean) -> Unit): Any {
        val type = Class.forName("android.net.wifi.WifiManager\$ActionListener")
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { soi, m, args ->
            when (m.name) {
                "onSuccess" -> { fini(true); null }
                "onFailure" -> { fini(false); null }
                "hashCode" -> System.identityHashCode(soi)
                "equals" -> soi === args?.firstOrNull()
                "toString" -> "Sama"
                else -> null
            }
        }
    }

    private val typeEcouteur get() = Class.forName("android.net.wifi.WifiManager\$ActionListener")

    fun connecterEnregistre(c: Context, netId: Int, fini: (Boolean) -> Unit = {}) = try {
        wm(c).javaClass.getMethod("connect", Int::class.javaPrimitiveType, typeEcouteur).invoke(wm(c), netId, ecouteur(c, fini))
        true
    } catch (_: Exception) {
        fini(false)
        false
    }

    /** Se connecter à un réseau neuf : Android l'enregistre et s'y connecte. */
    @Suppress("DEPRECATION")
    fun connecter(c: Context, ssid: String, securite: Securite, motDePasse: String?, fini: (Boolean) -> Unit = {}) = try {
        val conf = WifiConfiguration().apply {
            SSID = "\"" + ssid + "\""
            if (securite == Securite.PROTEGE && motDePasse != null) {
                if (Build.VERSION.SDK_INT >= 30) setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK) else allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                preSharedKey = "\"" + motDePasse + "\""
            } else {
                if (Build.VERSION.SDK_INT >= 30) setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN) else allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
        }
        wm(c).javaClass.getMethod("connect", WifiConfiguration::class.java, typeEcouteur).invoke(wm(c), conf, ecouteur(c, fini))
        true
    } catch (_: Exception) {
        fini(false)
        false
    }

    fun oublier(c: Context, netId: Int, fini: (Boolean) -> Unit = {}) = try {
        wm(c).javaClass.getMethod("forget", Int::class.javaPrimitiveType, typeEcouteur).invoke(wm(c), netId, ecouteur(c, fini))
        true
    } catch (_: Exception) {
        false
    }

    fun config(c: Context, ssid: String): WifiConfiguration? = try {
        @Suppress("DEPRECATION")
        (wm(c).javaClass.getMethod("getPrivilegedConfiguredNetworks").invoke(wm(c)) as List<*>)
            .filterIsInstance<WifiConfiguration>()
            .firstOrNull { it.SSID.removeSurrounding("\"") == ssid }
    } catch (_: Exception) {
        enregistres(c).firstOrNull { it.SSID.removeSurrounding("\"") == ssid }
    }

    private fun champ(conf: WifiConfiguration, nom: String) = WifiConfiguration::class.java.getField(nom).apply { isAccessible = true }

    fun connexionAuto(conf: WifiConfiguration): Boolean = try {
        champ(conf, "allowAutojoin").getBoolean(conf)
    } catch (_: Exception) {
        true
    }

    fun reglerConnexionAuto(c: Context, conf: WifiConfiguration, oui: Boolean) = try {
        wm(c).javaClass.getMethod("allowAutojoin", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(wm(c), conf.networkId, oui)
        true
    } catch (_: Exception) {
        false
    }

    /** « Compter comme de la data » : le réseau est mesuré (point d'accès d'un téléphone, forfait limité). */
    fun compteCommeData(conf: WifiConfiguration): Boolean = try {
        champ(conf, "meteredOverride").getInt(conf) == 1
    } catch (_: Exception) {
        false
    }

    fun reglerCompteCommeData(c: Context, conf: WifiConfiguration, oui: Boolean): Boolean = try {
        champ(conf, "meteredOverride").setInt(conf, if (oui) 1 else 0)
        wm(c).javaClass.getMethod("save", WifiConfiguration::class.java, typeEcouteur).invoke(wm(c), conf, ecouteur(c) {})
        true
    } catch (_: Exception) {
        false
    }

    fun adresseAleatoire(conf: WifiConfiguration): Boolean = try {
        champ(conf, "macRandomizationSetting").getInt(conf) != 0
    } catch (_: Exception) {
        true
    }

    fun reglerAdresseAleatoire(c: Context, conf: WifiConfiguration, oui: Boolean): Boolean = try {
        champ(conf, "macRandomizationSetting").setInt(conf, if (oui) 3 else 0)
        wm(c).javaClass.getMethod("save", WifiConfiguration::class.java, typeEcouteur).invoke(wm(c), conf, ecouteur(c) {})
        true
    } catch (_: Exception) {
        false
    }

    /** Le mot de passe d'un réseau enregistré, pour le partager par un code QR (jamais affiché en clair). */
    fun motDePasse(conf: WifiConfiguration): String? = conf.preSharedKey?.takeIf { it.startsWith("\"") }?.removeSurrounding("\"")

    @Suppress("DEPRECATION")
    fun debit(c: Context): Int = wm(c).connectionInfo?.linkSpeed ?: -1

    @Suppress("DEPRECATION")
    fun frequence(c: Context): Int = wm(c).connectionInfo?.frequency ?: -1

    /** Le réseau Wi-Fi a-t-il Internet, ou une page de connexion (café, hôtel) ? */
    fun etatInternet(c: Context): String? {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val n = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true } ?: return null
        val caps = cm.getNetworkCapabilities(n) ?: return null
        return when {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> "page de connexion"
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> null
            else -> "sans Internet"
        }
    }
}

fun forceDuSignal(niveau: Int) = when {
    niveau >= 3 -> "signal fort"
    niveau == 2 -> "signal moyen"
    else -> "signal faible"
}

fun iconeWifi(niveau: Int) = Icones.WIFI

/** Suit le Wi-Fi tant que la page est ouverte : état, réseaux, connexion ; un balayage toutes les 10 secondes. */
@Composable
fun rememberWifi(): Pair<Boolean, List<ReseauWifi>> {
    val c = LocalContext.current
    var actif by remember { mutableStateOf(MoteurWifi.actif(c)) }
    var liste by remember { mutableStateOf(emptyList<ReseauWifi>()) }
    var version by remember { mutableIntStateOf(0) }
    val reprise = LocalReprise.current
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, i: Intent?) {
                version++
            }
        }
        val f = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction("android.net.wifi.CONFIGURED_NETWORKS_CHANGE")
        }
        c.registerReceiver(r, f)
        onDispose { c.unregisterReceiver(r) }
    }
    LaunchedEffect(version, reprise) {
        actif = MoteurWifi.actif(c)
        liste = withContext(Dispatchers.IO) { if (actif) MoteurWifi.reseaux(c) else emptyList() }
    }
    LaunchedEffect(actif) {
        while (actif) {
            MoteurWifi.scanner(c)
            delay(10_000)
        }
    }
    return actif to liste
}

/** Réseau et data : Wi-Fi, SIM, point d'accès, mode avion, VPN (les pages de cette famille). */
@Composable
fun PageReseau(nav: Nav) {
    val c = LocalContext.current
    val (actif, liste) = rememberWifi()
    val connecte = liste.firstOrNull { it.connecte }
    val etat = rememberEtatReseau()
    PageReglages(titre = "Réseau et data", retour = nav.retour) {
        section(cle = "wifi") {
            Ligne(
                "Wi-Fi",
                detail = when {
                    !actif -> "Désactivé"
                    connecte != null -> connecte.ssid
                    else -> "Pas connecté"
                },
                icone = Icones.WIFI,
            ) { nav.aller(Page.Wifi) }
            Ligne("Cartes SIM", detail = etat.resumeSim, icone = Icones.DONNEES) { nav.aller(Page.Sim) }
            Ligne("Partage de connexion", detail = if (etat.pointAcces) "Point d'accès activé" else "Désactivé", icone = Icones.POINT_ACCES) {
                nav.aller(Page.PointAcces)
            }
            Ligne("VPN et DNS privé", detail = etat.resumeVpn, icone = Icones.CADENAS) { nav.aller(Page.Vpn) }
        }
        section(cle = "avion") {
            Ligne(
                "Mode avion",
                detail = "Coupe le téléphone, la data, le Wi-Fi et le Bluetooth",
                icone = Icones.AVION,
                fin = Fin.Inter(etat.avion),
            ) { etat.basculerAvion(c) }
            Ligne(
                "Économie de data",
                detail = "Les applis en arrière-plan n'utilisent plus la data",
                icone = Icones.ECONOMIE,
                fin = Fin.Inter(etat.economieData),
            ) { etat.basculerEconomieData(c) }
        }
    }
}

/** La liste des réseaux Wi-Fi (maquette l3-wifi). */
@Composable
fun PageWifi(nav: Nav) {
    val c = LocalContext.current
    val (actif, liste) = rememberWifi()
    var attente by remember { mutableStateOf<String?>(null) }
    // Après quelques secondes sans autre réseau, on le dit plutôt que de chercher sans fin.
    var cherche by remember { mutableStateOf(true) }
    LaunchedEffect(actif) {
        cherche = true
        delay(6000)
        cherche = false
    }
    PageReglages(titre = "Wi-Fi", retour = nav.retour) {
        section(cle = "inter") {
            Ligne("Wi-Fi", icone = Icones.WIFI, fin = Fin.Inter(actif)) { MoteurWifi.activer(c, !actif) }
        }
        if (!actif) {
            section(cle = "eteint") { Explication("Le Wi-Fi est coupé. Les applis passent par la data mobile.") }
            return@PageReglages
        }
        val connecte = liste.filter { it.connecte }
        if (connecte.isNotEmpty()) {
            section("Connecté", cle = "connecte") {
                connecte.forEach { r ->
                    val internet = MoteurWifi.etatInternet(c)
                    Ligne(
                        r.ssid,
                        detail = listOfNotNull("Connecté", internet, forceDuSignal(r.niveau)).joinToString(" · "),
                        icone = Icones.WIFI,
                        choisie = true,
                    ) { nav.aller(Page.WifiDetail(r.ssid)) }
                }
            }
        }
        section("Réseaux disponibles", cle = "disponibles") {
            val autres = liste.filter { !it.connecte }
            if (autres.isEmpty()) Explication(if (cherche) "Recherche des réseaux…" else "Aucun autre réseau à portée.")
            autres.forEach { r ->
                Ligne(
                    r.ssid,
                    detail = when {
                        attente == r.ssid -> "Connexion…"
                        r.enregistre != null -> "Enregistré · " + forceDuSignal(r.niveau)
                        r.securite == Securite.OUVERT -> "Ouvert · " + forceDuSignal(r.niveau)
                        r.securite == Securite.ENTREPRISE -> "Réseau d'entreprise · " + forceDuSignal(r.niveau)
                        else -> "Protégé · " + forceDuSignal(r.niveau)
                    },
                    icone = Icones.WIFI,
                    fin = if (r.securite == Securite.OUVERT) Fin.Rien else Fin.Valeur(""),
                    debut = { IconeReseau(r) },
                ) {
                    when {
                        r.enregistre != null -> {
                            attente = r.ssid
                            MoteurWifi.connecterEnregistre(c, r.enregistre) { attente = null }
                        }
                        r.securite == Securite.OUVERT -> {
                            attente = r.ssid
                            MoteurWifi.connecter(c, r.ssid, Securite.OUVERT, null) { attente = null }
                        }
                        r.securite == Securite.ENTREPRISE -> nav.android(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                        else -> nav.aller(Page.WifiConnexion(r.ssid, r.niveau))
                    }
                }
            }
            Ligne("Ajouter un réseau", icone = Icones.PLUS, fin = Fin.Rien) { nav.aller(Page.WifiConnexion("", 0)) }
        }
    }
}

/** L'icône d'un réseau : l'onde, et un petit cadenas s'il est protégé. */
@Composable
private fun IconeReseau(r: ReseauWifi) {
    val b = LocalBanco.current
    Box(Modifier.size(24.dp)) {
        IconeTrait(Icones.WIFI, 24.dp, b.encre)
        if (r.securite != Securite.OUVERT) {
            Box(Modifier.align(Alignment.BottomEnd).size(11.dp).background(b.sol, CircleShape), contentAlignment = Alignment.Center) {
                IconeTrait(Icones.CADENAS, 10.dp, b.encre, epaisseur = 2.2f)
            }
        }
    }
}

/**
 * Se connecter à un réseau protégé (maquette l1-wifi-mdp) : le mot de passe, qu'on peut afficher.
 * Pour « Ajouter un réseau », on tape aussi son nom.
 */
@Composable
fun PageWifiConnexion(ssidDonne: String, niveau: Int, nav: Nav) {
    val b = LocalBanco.current
    val c = LocalContext.current
    var ssid by remember { mutableStateOf(ssidDonne) }
    var mdp by remember { mutableStateOf("") }
    var voir by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var envoi by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun connecter() {
        if (ssid.isBlank()) return
        if (mdp.isNotEmpty() && mdp.length < 8) {
            erreur = "Un mot de passe Wi-Fi a au moins 8 caractères."
            return
        }
        envoi = true
        MoteurWifi.connecter(c, ssid.trim(), if (mdp.isEmpty()) Securite.OUVERT else Securite.PROTEGE, mdp.ifEmpty { null }) { ok ->
            envoi = false
            if (ok) nav.retour() else erreur = "Connexion refusée. Vérifiez le mot de passe."
        }
    }
    PageReglages(
        titre = ssidDonne.ifEmpty { "Ajouter un réseau" },
        sousTitre = if (ssidDonne.isEmpty()) "Nom du réseau et mot de passe" else "Wi-Fi protégé · " + forceDuSignal(niveau),
        retour = nav.retour,
    ) {
        section(cle = "champs") {
            if (ssidDonne.isEmpty()) {
                Champ(ssid, "Nom du réseau", { ssid = it }, Modifier.focusRequester(focus))
                Spacer(Modifier.height(12.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(b.sol2)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconeTrait(Icones.CADENAS, 20.dp, b.encre2)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = mdp,
                    onValueChange = {
                        mdp = it
                        erreur = null
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre),
                    cursorBrush = SolidColor(b.laterite),
                    visualTransformation = if (voir) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { connecter() }),
                    modifier = Modifier
                        .weight(1f)
                        .then(if (ssidDonne.isNotEmpty()) Modifier.focusRequester(focus) else Modifier)
                        .semantics { contentDescription = "Mot de passe du Wi-Fi" },
                    decorationBox = { champ ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (mdp.isEmpty()) BasicText(if (ssidDonne.isEmpty()) "Mot de passe (vide si ouvert)" else "Mot de passe", style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre2))
                            champ()
                        }
                    },
                )
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = if (voir) "Masquer" else "Afficher", role = Role.Button) { voir = !voir },
                    contentAlignment = Alignment.Center,
                ) { IconeTrait(if (voir) Icones.OEIL_BARRE else Icones.OEIL, 22.dp, b.encre2) }
            }
            erreur?.let { Explication(it, b.danger) }
            Explication("Le mot de passe est écrit sur la box ou donné par la personne qui partage. Personne de Sama ne vous le demandera.")
        }
        section(cle = "pied") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                BasicText(
                    if (envoi) "Connexion…" else "Se connecter",
                    modifier = Modifier.padding(end = 14.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.encre),
                )
                Avancer(actif = ssid.isNotBlank() && !envoi, description = "Se connecter") { connecter() }
            }
        }
    }
}

@Composable
fun Champ(valeur: String, indication: String, changer: (String) -> Unit, modifier: Modifier = Modifier, clavier: KeyboardType = KeyboardType.Text) {
    val b = LocalBanco.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(b.sol2)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = valeur,
            onValueChange = changer,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre),
            cursorBrush = SolidColor(b.laterite),
            keyboardOptions = KeyboardOptions(keyboardType = clavier),
            modifier = modifier.fillMaxWidth().semantics { contentDescription = indication },
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (valeur.isEmpty()) BasicText(indication, style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre2))
                    champ()
                }
            },
        )
    }
}

/** Les détails du réseau où l'on est, ou d'un réseau enregistré (maquette l3-wifi-detail). */
@Composable
fun PageWifiDetail(ssid: String, nav: Nav) {
    val c = LocalContext.current
    val (_, liste) = rememberWifi()
    val reseau = liste.firstOrNull { it.ssid == ssid }
    var conf by remember { mutableStateOf<WifiConfiguration?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(ssid, version, reseau?.connecte) { conf = withContext(Dispatchers.IO) { MoteurWifi.config(c, ssid) } }
    val connecte = reseau?.connecte == true
    val sousTitre = if (connecte) {
        val f = MoteurWifi.frequence(c)
        val bande = if (f >= 5900) "6 GHz" else if (f >= 4900) "5 GHz" else "2,4 GHz"
        listOfNotNull("Connecté", bande, MoteurWifi.debit(c).takeIf { it > 0 }?.let { "$it Mbit/s" }).joinToString(" · ")
    } else {
        "Enregistré"
    }
    PageReglages(titre = ssid, sousTitre = sousTitre, retour = nav.retour) {
        val cf = conf
        val mdp = cf?.let { MoteurWifi.motDePasse(it) }
        if (cf != null) {
            section(cle = "partage") {
                PartageWifi(ssid, mdp, cf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE) && mdp == null)
            }
            section("Ce réseau", cle = "reglages") {
                Ligne("Connexion automatique", fin = Fin.Inter(MoteurWifi.connexionAuto(cf))) {
                    MoteurWifi.reglerConnexionAuto(c, cf, !MoteurWifi.connexionAuto(cf))
                    version++
                }
                Ligne(
                    "Compter comme de la data",
                    detail = "Pas de grosses mises à jour sur ce réseau",
                    fin = Fin.Inter(MoteurWifi.compteCommeData(cf)),
                ) {
                    MoteurWifi.reglerCompteCommeData(c, cf, !MoteurWifi.compteCommeData(cf))
                    version++
                }
                Ligne(
                    "Adresse aléatoire",
                    detail = if (MoteurWifi.adresseAleatoire(cf)) "Ce réseau ne voit pas l'adresse de votre téléphone" else "Ce réseau voit l'adresse de votre téléphone",
                    fin = Fin.Inter(MoteurWifi.adresseAleatoire(cf)),
                ) {
                    MoteurWifi.reglerAdresseAleatoire(c, cf, !MoteurWifi.adresseAleatoire(cf))
                    version++
                }
            }
            section(cle = "oublier") {
                Ligne("Oublier ce réseau", icone = Icones.CORBEILLE, danger = true, fin = Fin.Rien) {
                    MoteurWifi.oublier(c, cf.networkId) { nav.retour() }
                }
            }
        } else {
            section(cle = "rien") { Explication("Ce réseau n'est pas enregistré.") }
        }
    }
}
