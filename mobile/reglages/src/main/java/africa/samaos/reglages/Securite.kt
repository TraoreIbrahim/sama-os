package africa.samaos.reglages

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un usage sensible : quelle appli, quoi, quand. */
class Usage(val paquet: String, val quoi: String, val heure: Long)

object MoteurSecurite2 {
    /** -1 : rien ; 1 : schéma ; 3 : code ; 4 : mot de passe (LockPatternUtils, réservé au système). */
    fun typeVerrou(c: Context): Int = try {
        val lpu = Class.forName("com.android.internal.widget.LockPatternUtils").getConstructor(Context::class.java).newInstance(c)
        lpu.javaClass.getMethod("getCredentialTypeForUser", Int::class.javaPrimitiveType).invoke(lpu, EspaceActif.id()) as Int
    } catch (_: Exception) {
        if (MoteurSecurite.codeEnPlace(c)) 3 else -1
    }

    fun nomVerrou(t: Int) = when (t) {
        1 -> "Schéma"
        3 -> "Code"
        4 -> "Mot de passe"
        else -> "Aucun"
    }

    fun debogageUsb(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    fun optionsDev(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    fun couperDebogage(c: Context) {
        Settings.Global.putInt(c.contentResolver, Settings.Global.ADB_ENABLED, 0)
    }

    fun couperOptionsDev(c: Context) {
        Settings.Global.putInt(c.contentResolver, Settings.Global.ADB_ENABLED, 0)
        Settings.Global.putInt(c.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
    }

    /** Les applis qui ont le droit d'en installer d'autres hors de Sugu. */
    fun installeuses(c: Context) = MoteurApplis.quiDemande(c, "android.permission.REQUEST_INSTALL_PACKAGES")
        .filter { MoteurApplis.op(c, "android:request_install_packages", it) }

    fun correctif(): String = try {
        LocalDate.parse(Build.VERSION.SECURITY_PATCH).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))
    } catch (_: Exception) {
        Build.VERSION.SECURITY_PATCH
    }

    fun delaiVerrou(c: Context) = Settings.Secure.getLong(c.contentResolver, "lock_screen_lock_after_timeout", 5000)

    fun reglerDelaiVerrou(c: Context, ms: Long) = Settings.Secure.putLong(c.contentResolver, "lock_screen_lock_after_timeout", ms)

    /** Qui a utilisé la position, la caméra, le micro, les contacts (droit GET_APP_OPS_STATS du système). */
    fun usages(c: Context, depuis: Long): List<Usage> = try {
        val aom = c.getSystemService(AppOpsManager::class.java)
        val ops = mapOf(
            "android:fine_location" to "Position", "android:coarse_location" to "Position",
            "android:camera" to "Appareil photo", "android:record_audio" to "Micro", "android:read_contacts" to "Contacts",
        )
        val paquets = aom.javaClass.getMethod("getPackagesForOps", Array<String>::class.java).invoke(aom, ops.keys.toTypedArray()) as List<*>?
        val liste = mutableListOf<Usage>()
        paquets.orEmpty().forEach { po ->
            val nom = po!!.javaClass.getMethod("getPackageName").invoke(po) as String
            (po.javaClass.getMethod("getOps").invoke(po) as List<*>).forEach { e ->
                val op = e!!.javaClass.getMethod("getOpStr").invoke(e) as String
                val t = e.javaClass.getMethod("getLastAccessTime", Int::class.javaPrimitiveType).invoke(e, 0x1F) as Long
                if (t >= depuis && nom != c.packageName && nom != "android") liste += Usage(nom, ops[op] ?: op, t)
            }
        }
        liste.distinctBy { it.paquet + it.quoi }.sortedByDescending { it.heure }
    } catch (_: Exception) {
        emptyList()
    }

    // Caméra et micro coupés pour tout le téléphone (SensorPrivacyManager, droit MANAGE_SENSOR_PRIVACY).

    private fun capteurs(c: Context): Any? = try {
        c.getSystemService(Class.forName("android.hardware.SensorPrivacyManager"))
    } catch (_: Exception) {
        null
    }

    fun coupe(c: Context, capteur: Int): Boolean? = try {
        val s = capteurs(c)!!
        if (!(s.javaClass.getMethod("supportsSensorToggle", Int::class.javaPrimitiveType).invoke(s, capteur) as Boolean)) {
            null
        } else {
            s.javaClass.getMethod("isSensorPrivacyEnabled", Int::class.javaPrimitiveType).invoke(s, capteur) as Boolean
        }
    } catch (_: Exception) {
        null
    }

    fun couper(c: Context, capteur: Int, oui: Boolean) = try {
        val s = capteurs(c)!!
        s.javaClass.getMethod("setSensorPrivacy", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).invoke(s, capteur, oui)
        true
    } catch (_: Exception) {
        false
    }

    const val MICRO = 1
    const val CAMERA = 2

    fun pinSim(c: Context, idAbonnement: Int): Boolean? = try {
        val tm = c.getSystemService(TelephonyManager::class.java).createForSubscriptionId(idAbonnement)
        tm.javaClass.getMethod("isIccLockEnabled").invoke(tm) as Boolean
    } catch (_: Exception) {
        null
    }

    fun certificats(): Pair<Int, Int> = try {
        val ks = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val noms = ks.aliases().toList()
        noms.count { it.startsWith("system:") } to noms.count { it.startsWith("user:") }
    } catch (_: Exception) {
        0 to 0
    }

    fun position(c: Context) = c.getSystemService(LocationManager::class.java).isLocationEnabled

    fun reglerPosition(c: Context, oui: Boolean) = try {
        val lm = c.getSystemService(LocationManager::class.java)
        lm.javaClass.getMethod("setLocationEnabledForUser", Boolean::class.javaPrimitiveType, android.os.UserHandle::class.java)
            .invoke(lm, oui, Process.myUserHandle())
        true
    } catch (_: Exception) {
        false
    }
}

/** L'état de la sécurité (maquette l3-securite) : ce qui est à faire, puis ce qui a été vérifié. */
@Composable
fun PageSecurite(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var type by remember { mutableIntStateOf(-1) }
    var installeuses by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(version, reprise) {
        type = MoteurSecurite2.typeVerrou(c)
        installeuses = withContext(Dispatchers.IO) { MoteurSecurite2.installeuses(c) }
    }
    val debogage = MoteurSecurite2.debogageUsb(c)
    val dev = MoteurSecurite2.optionsDev(c)
    val aFaire = (if (type == -1) 1 else 0) + (if (debogage) 1 else 0) + (if (dev && !debogage) 1 else 0) + installeuses.size
    PageReglages(
        titre = if (aFaire == 0) "Tout va bien" else "$aFaire chose${if (aFaire > 1) "s" else ""} à voir",
        sousTitre = "La sécurité de ce téléphone",
        retour = nav.retour,
    ) {
        if (aFaire > 0) {
            section("À faire", cle = "afaire") {
                if (type == -1) {
                    Ligne("Choisir un code de verrouillage", detail = "Sans code, qui prend le téléphone ouvre tout : mobile money, messages, photos.", icone = Icones.CADENAS) {
                        nav.aller(Page.Verrouillage)
                    }
                }
                if (debogage) {
                    Ligne(
                        "Couper le débogage USB",
                        detail = "Un ordinateur branché peut tout contrôler. Personne d'honnête ne vous demandera de l'activer.",
                        icone = Icones.USB,
                        fin = Fin.Valeur("Couper"),
                    ) {
                        MoteurSecurite2.couperDebogage(c)
                        version++
                    }
                }
                if (dev && !debogage) {
                    Ligne("Désactiver les options pour développeurs", detail = "Elles ne servent qu'à ceux qui créent des applis.", icone = Icones.REGLAGES, fin = Fin.Valeur("Désactiver")) {
                        MoteurSecurite2.couperOptionsDev(c)
                        version++
                    }
                }
                installeuses.forEach { p ->
                    Ligne(
                        "${MoteurApplis.nom(c, p)} peut installer des applis",
                        detail = "Hors de Sugu, sans vérification. Retirez cet accès si vous ne savez pas pourquoi il l'a.",
                        image = MoteurApplis.icone(c, p, 32),
                    ) { nav.aller(Page.AccesSpecial(Acces.INCONNUES)) }
                }
            }
        }
        section("Vérifié", cle = "verifie") {
            Ligne("Mise à jour de sécurité", detail = MoteurSecurite2.correctif(), icone = Icones.MISE_A_JOUR, fin = Fin.Rien)
            if (type != -1) Ligne("Verrouillage de l'écran", detail = MoteurSecurite2.nomVerrou(type), icone = Icones.CADENAS) { nav.aller(Page.Verrouillage) }
            if (!debogage) Ligne("Débogage USB", detail = "Coupé", icone = Icones.USB, fin = Fin.Rien)
        }
        section("Confidentialité", cle = "conf") {
            Ligne("Autorisations", detail = "Par type d'accès", icone = Icones.BOUCLIER) { nav.aller(Page.GestionnaireAutorisations) }
            Ligne("Qui a utilisé quoi", detail = "Position, caméra, micro, contacts", icone = Icones.OEIL) { nav.aller(Page.TableauConfidentialite) }
            Ligne("Caméra et micro", detail = "Les couper pour toutes les applis", icone = Icones.APPAREIL) { nav.aller(Page.CameraMicro) }
            Ligne("Localisation", icone = Icones.BOUSSOLE) { nav.aller(Page.Localisation) }
        }
        section("Prêter, montrer", cle = "epingle") {
            val epingle = Settings.System.getInt(c.contentResolver, "lock_to_app_enabled", 0) == 1
            Ligne(
                "Épingler une appli", detail = "Pour montrer une seule appli : on ne peut pas en sortir sans votre geste", icone = Icones.CADENAS,
                fin = Fin.Inter(epingle),
            ) {
                try {
                    Settings.System.putInt(c.contentResolver, "lock_to_app_enabled", if (epingle) 0 else 1)
                    if (!epingle) Settings.Secure.putInt(c.contentResolver, "lock_to_app_exit_locked", 1)
                } catch (_: Exception) {
                }
                version++
            }
            if (epingle) {
                val code = Settings.Secure.getInt(c.contentResolver, "lock_to_app_exit_locked", 1) == 1
                Ligne("Demander le code pour en sortir", icone = Icones.CLE, fin = Fin.Inter(code)) {
                    try {
                        Settings.Secure.putInt(c.contentResolver, "lock_to_app_exit_locked", if (code) 0 else 1)
                    } catch (_: Exception) {
                    }
                    version++
                }
                Explication("Pour épingler : dans les applis récentes, toucher l'icône de l'appli, puis « Épingler ».")
            }
        }
        section("Téléphone", cle = "tel") {
            Ligne("Code PIN des SIM", icone = Icones.SIM) { nav.aller(Page.PinSim) }
            Ligne("Chiffrement et identifiants", icone = Icones.CLE) { nav.aller(Page.Chiffrement) }
        }
    }
}

/** Le verrouillage (maquette l3-verrouillage). Le code se choisit dans l'écran d'Android, qui demande l'ancien d'abord. */
@Composable
fun PageVerrouillage(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var type by remember { mutableIntStateOf(-1) }
    LaunchedEffect(version, reprise) { type = MoteurSecurite2.typeVerrou(c) }
    fun choisir(complexite: Int) {
        c.startActivity(
            Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD).putExtra(DevicePolicyManager.EXTRA_PASSWORD_COMPLEXITY, complexite)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    PageReglages(titre = "Verrouillage", sousTitre = "Espace ${EspaceActif.nom(c)}", retour = nav.retour) {
        section("Déverrouiller avec", cle = "avec") {
            Ligne("Code", detail = "Au moins 6 chiffres, pas 123456", icone = Icones.CLAVIER, fin = Fin.Choix(type == 3)) { choisir(DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM) }
            Ligne("Schéma", detail = "Facile à voir par-dessus l'épaule", icone = Icones.APPLI, fin = Fin.Choix(type == 1)) { choisir(DevicePolicyManager.PASSWORD_COMPLEXITY_LOW) }
            Ligne("Mot de passe", detail = "Le plus sûr", icone = Icones.CLE, fin = Fin.Choix(type == 4)) { choisir(DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH) }
            Explication("Sama ne vous demandera jamais votre code, ni par appel ni par SMS. Ne le donnez à personne.", LocalBanco.current.lateriteTexte)
        }
        section("Empreintes", cle = "empreintes") {
            Ligne("Ajouter une empreinte", icone = Icones.PLUS) {
                val i = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_BIOMETRIC_ENROLL) else Intent("android.settings.FINGERPRINT_ENROLL")
                nav.android(i)
            }
        }
        if (type != -1) {
            section(cle = "delai") {
                val v = version
                val d = if (v >= 0) MoteurSecurite2.delaiVerrou(c) else 0
                val choix = listOf(0L to "Tout de suite", 5_000L to "5 secondes", 30_000L to "30 secondes", 60_000L to "1 minute", 300_000L to "5 minutes")
                Ligne("Verrouiller après la veille", detail = choix.firstOrNull { it.first == d }?.second ?: "${d / 1000} s", icone = Icones.HORLOGE, fin = Fin.Rien)
                Segments(listOf("0 s", "5 s", "30 s", "1 min", "5 min"), choix.indexOfFirst { it.first == d }.coerceAtLeast(0)) { i ->
                    MoteurSecurite2.reglerDelaiVerrou(c, choix[i].first)
                    version++
                }
            }
        }
        section(cle = "espaces") {
            Ligne("Verrous des Espaces", detail = "Chaque Espace a son code", icone = Icones.CADENAS) {
                try {
                    c.startActivity(Intent("africa.samaos.action.REGLAGES_ESPACES").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        }
    }
}

/** Les autorisations par type d'accès (maquette l3-gestionnaire-autorisations). */
@Composable
fun PageGestionnaireAutorisations(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var comptes by remember { mutableStateOf(emptyMap<Groupe, Int>()) }
    LaunchedEffect(reprise) {
        comptes = withContext(Dispatchers.IO) {
            val applis = MoteurApplis.applis(c).map { it.paquet }
            val parAppli = applis.associateWith { MoteurApplis.autorisations(c, it) }
            Groupe.entries.associateWith { g -> parAppli.values.count { l -> l.any { it.first == g && it.second } } }
        }
    }
    PageReglages(titre = "Autorisations", sousTitre = "Par type d'accès, dans l'Espace ${EspaceActif.nom(c)}", retour = nav.retour) {
        section(cle = "groupes") {
            Groupe.entries.forEach { g ->
                val n = comptes[g]
                Ligne(g.nom, detail = n?.let { if (it == 0) "Aucune appli" else if (it == 1) "1 appli" else "$it applis" }, icone = g.icone) {
                    nav.aller(Page.AutorisationGroupe(g))
                }
            }
        }
    }
}

@Composable
fun PageAutorisationGroupe(g: Groupe, nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var liste by remember { mutableStateOf(emptyList<Pair<String, Boolean>>()) }
    LaunchedEffect(version) {
        liste = withContext(Dispatchers.IO) {
            MoteurApplis.applis(c).mapNotNull { a -> MoteurApplis.autorisations(c, a.paquet).firstOrNull { it.first == g }?.let { a.paquet to it.second } }
        }
    }
    PageReglages(titre = g.nom, retour = nav.retour) {
        section("Autorisées", cle = "oui") {
            val oui = liste.filter { it.second }
            if (oui.isEmpty()) Explication("Aucune appli.")
            oui.forEach { (p, _) ->
                Ligne(MoteurApplis.nom(c, p), image = MoteurApplis.icone(c, p, 32), fin = Fin.Inter(true)) {
                    MoteurApplis.reglerGroupe(c, p, g, false)
                    version++
                }
            }
        }
        section("Refusées", cle = "non") {
            liste.filter { !it.second }.forEach { (p, _) ->
                Ligne(MoteurApplis.nom(c, p), image = MoteurApplis.icone(c, p, 32), fin = Fin.Inter(false)) {
                    MoteurApplis.reglerGroupe(c, p, g, true)
                    version++
                }
            }
        }
    }
}

/** Qui a utilisé quoi, et quand (maquette l3-tableau-confidentialite). */
@Composable
fun PageTableauConfidentialite(nav: Nav) {
    val c = LocalContext.current
    var semaine by remember { mutableStateOf(false) }
    var usages by remember { mutableStateOf(emptyList<Usage>()) }
    LaunchedEffect(semaine) {
        val depuis = if (semaine) System.currentTimeMillis() - 7L * 24 * 3600_000 else LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        usages = withContext(Dispatchers.IO) { MoteurSecurite2.usages(c, depuis) }
    }
    PageReglages(titre = "Confidentialité", sousTitre = "Qui a utilisé quoi, et quand", retour = nav.retour) {
        section(if (semaine) "Ces 7 derniers jours" else "Aujourd'hui", cle = "liste") {
            if (usages.isEmpty()) Explication("Aucune appli n'a utilisé la position, la caméra, le micro ou les contacts.")
            usages.groupBy { it.quoi }.forEach { (quoi, l) ->
                Ligne(
                    quoi,
                    detail = l.take(3).joinToString(", ") { "${MoteurApplis.nom(c, it.paquet)} à ${heure(it.heure)}" },
                    icone = Groupe.entries.firstOrNull { it.nom == quoi }?.icone ?: Icones.OEIL,
                    fin = Fin.Rien,
                )
            }
            if (!semaine) Ligne("Voir les 7 derniers jours", icone = Icones.HORLOGE) { semaine = true }
        }
        section(cle = "note") { Explication("Pendant qu'une appli utilise la caméra ou le micro, un point vert s'allume dans la barre d'état.") }
    }
}

/** Caméra et micro (maquette l3-camera-micro). */
@Composable
fun PageCameraMicro(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var recents by remember { mutableStateOf(emptyList<Usage>()) }
    LaunchedEffect(Unit) {
        recents = withContext(Dispatchers.IO) {
            MoteurSecurite2.usages(c, System.currentTimeMillis() - 24 * 3600_000).filter { it.quoi == "Appareil photo" || it.quoi == "Micro" }
        }
    }
    PageReglages(titre = "Caméra et micro", retour = nav.retour) {
        val v = version
        val camera = if (v >= 0) MoteurSecurite2.coupe(c, MoteurSecurite2.CAMERA) else null
        val micro = if (v >= 0) MoteurSecurite2.coupe(c, MoteurSecurite2.MICRO) else null
        section(cle = "inter") {
            if (camera == null && micro == null) {
                Explication("Ce téléphone ne permet pas de couper la caméra et le micro pour toutes les applis. Retirez l'autorisation appli par appli.")
            }
            camera?.let { coupe ->
                Ligne("Accès à la caméra", detail = "Pour toutes les applis", icone = Icones.APPAREIL, fin = Fin.Inter(!coupe)) {
                    MoteurSecurite2.couper(c, MoteurSecurite2.CAMERA, !coupe)
                    version++
                }
            }
            micro?.let { coupe ->
                Ligne("Accès au micro", detail = "Pour toutes les applis", icone = Icones.MUSIQUE, fin = Fin.Inter(!coupe)) {
                    MoteurSecurite2.couper(c, MoteurSecurite2.MICRO, !coupe)
                    version++
                }
            }
            if (camera != null || micro != null) Explication("Coupés, ils bloquent tout, même une appli autorisée. Les appels d'urgence gardent toujours le micro.")
        }
        if (recents.isNotEmpty()) {
            section("Utilisés récemment", cle = "recents") {
                recents.forEach { u -> Ligne(MoteurApplis.nom(c, u.paquet), detail = "${u.quoi} · ${heure(u.heure)}", image = MoteurApplis.icone(c, u.paquet, 32), fin = Fin.Rien) }
            }
        }
    }
}

/** Le code PIN des SIM (maquette l3-pin-sim). La saisie du code se fait dans l'écran d'Android. */
@Composable
fun PagePinSim(nav: Nav) {
    val c = LocalContext.current
    val sims = remember { MoteurReseau.sims(c) }
    PageReglages(titre = "Code PIN", sousTitre = "Verrouillage des cartes SIM", retour = nav.retour) {
        if (sims.isEmpty()) section(cle = "aucune") { Explication("Aucune carte SIM.") }
        sims.forEach { s ->
            section("SIM ${s.simSlotIndex + 1} · ${MoteurReseau.nomSim(s)}", cle = "sim-${s.subscriptionId}") {
                val actif = MoteurSecurite2.pinSim(c, s.subscriptionId)
                Ligne(
                    "Demander le code au démarrage",
                    detail = when (actif) {
                        true -> "Activé : qui vole le téléphone ne peut pas utiliser la SIM"
                        false -> "Désactivé"
                        null -> null
                    },
                    icone = Icones.SIM,
                ) { nav.android(Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$IccLockSettingsActivity"))) }
            }
        }
        section(cle = "note") { Explication("Après 3 codes faux, la SIM se bloque. Le code PUK est sur le support de la carte : ne le donnez qu'à votre opérateur, en agence.") }
    }
}

/** Le chiffrement et les identifiants (maquette l3-chiffrement). */
@Composable
fun PageChiffrement(nav: Nav) {
    val c = LocalContext.current
    val (systeme, ajoutes) = remember { MoteurSecurite2.certificats() }
    val code = MoteurSecurite.codeEnPlace(c)
    PageReglages(titre = "Chiffrement", sousTitre = "Vos données restent illisibles sans vous", retour = nav.retour) {
        section(cle = "memoire") {
            Ligne(
                "Mémoire du téléphone",
                detail = if (code) "Chiffrée · la clé dépend de votre code" else "Chiffrée, mais sans code : choisissez-en un pour la protéger vraiment",
                icone = Icones.CADENAS,
                fin = Fin.Rien,
            )
        }
        section("Identifiants", cle = "identifiants") {
            Ligne("Certificats de confiance", detail = "Système $systeme · ajoutés $ajoutes", icone = Icones.BOUCLIER) {
                nav.android(Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$TrustedCredentialsSettingsActivity")))
            }
            Ligne("Installer un certificat", icone = Icones.PLUS) {
                nav.android(Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$EncryptionAndCredentialActivity")))
            }
            if (ajoutes > 0) {
                Explication("Un certificat ajouté peut permettre de lire vos échanges chiffrés. N'en installez que si votre organisation vous le demande en personne.", LocalBanco.current.lateriteTexte)
            }
        }
    }
}

/** La position (maquette l3-localisation). */
@Composable
fun PageLocalisation(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var liste by remember { mutableStateOf(emptyList<Pair<String, String?>>()) }
    LaunchedEffect(version, reprise) {
        liste = withContext(Dispatchers.IO) {
            MoteurApplis.applis(c).mapNotNull { a ->
                MoteurApplis.autorisations(c, a.paquet).firstOrNull { it.first == Groupe.POSITION && it.second }?.let { a.paquet to it.third }
            }
        }
    }
    PageReglages(titre = "Position", retour = nav.retour) {
        val v = version
        val actif = v >= 0 && MoteurSecurite2.position(c)
        section(cle = "inter") {
            Ligne("Utiliser la position", detail = if (actif) "Les applis autorisées savent où vous êtes" else "Aucune appli ne sait où vous êtes", icone = Icones.BOUSSOLE, fin = Fin.Inter(actif)) {
                MoteurSecurite2.reglerPosition(c, !actif)
                version++
            }
        }
        section("Applis autorisées", cle = "applis") {
            if (liste.isEmpty()) Explication("Aucune appli n'a accès à la position.")
            liste.forEach { (p, d) -> Ligne(MoteurApplis.nom(c, p), detail = d, image = MoteurApplis.icone(c, p, 32)) { nav.aller(Page.AppliAutorisations(p)) } }
        }
        section(cle = "note") { Explication("Les appels d'urgence envoient toujours votre position aux secours, même coupée.") }
    }
}
