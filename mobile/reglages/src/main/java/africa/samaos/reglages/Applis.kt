package africa.samaos.reglages

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import africa.samaos.banco.Icones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.Executor
import java.util.function.Consumer

/** Une appli de l'Espace, telle que les Réglages la montrent. */
class AppliVue(val paquet: String, val nom: String, val icone: ImageBitmap, val systeme: Boolean, val active: Boolean)

/** Les groupes d'autorisations qu'on montre, avec leurs mots à nous. */
enum class Groupe(val nom: String, val icone: String, val droits: List<String>) {
    APPAREIL_PHOTO("Appareil photo", Icones.APPAREIL, listOf("android.permission.CAMERA")),
    MICRO("Micro", Icones.MUSIQUE, listOf("android.permission.RECORD_AUDIO")),
    POSITION("Position", Icones.BOUSSOLE, listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION")),
    CONTACTS("Contacts", Icones.PERSONNE, listOf("android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS", "android.permission.GET_ACCOUNTS")),
    SMS("SMS", Icones.MESSAGE, listOf("android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_MMS")),
    TELEPHONE("Téléphone et appels", Icones.APPEL, listOf("android.permission.CALL_PHONE", "android.permission.READ_PHONE_STATE", "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG", "android.permission.ANSWER_PHONE_CALLS", "android.permission.READ_PHONE_NUMBERS")),
    AGENDA("Agenda", Icones.HORLOGE, listOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR")),
    PHOTOS("Photos et vidéos", Icones.PAYSAGE, listOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE")),
    MUSIQUE("Musique et audio", Icones.MUSIQUE, listOf("android.permission.READ_MEDIA_AUDIO")),
    PROXIMITE("Appareils à proximité", Icones.BLUETOOTH, listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT", "android.permission.NEARBY_WIFI_DEVICES")),
    NOTIFICATIONS("Notifications", Icones.CLOCHE, listOf("android.permission.POST_NOTIFICATIONS")),
    ACTIVITE("Activité physique", Icones.HORLOGE, listOf("android.permission.ACTIVITY_RECOGNITION", "android.permission.BODY_SENSORS")),
}

object MoteurApplis {
    private fun pm(c: Context) = c.packageManager

    fun applis(c: Context): List<AppliVue> {
        val pm = pm(c)
        val taille = (40 * c.resources.displayMetrics.density).toInt()
        val lanceur = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val tri = Collator.getInstance(Locale.FRENCH)
        return pm.queryIntentActivities(lanceur, PackageManager.MATCH_DISABLED_COMPONENTS)
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val info = ri.activityInfo.applicationInfo
                AppliVue(
                    ri.activityInfo.packageName,
                    info.loadLabel(pm).toString(),
                    info.loadIcon(pm).toBitmap(taille, taille).asImageBitmap(),
                    info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    info.enabled,
                )
            }
            .sortedWith { a, b -> tri.compare(a.nom, b.nom) }
    }

    /** Quand chaque appli a servi pour la dernière fois (droit PACKAGE_USAGE_STATS du système). */
    fun derniersUsages(c: Context): Map<String, Long> = try {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val fin = System.currentTimeMillis()
        usm.queryAndAggregateUsageStats(fin - 30L * 24 * 3600_000, fin).mapValues { it.value.lastTimeUsed }.filterValues { it > 0 }
    } catch (_: Exception) {
        emptyMap()
    }

    fun quand(ms: Long): String {
        val d = System.currentTimeMillis() - ms
        val zone = ZoneId.systemDefault()
        val jour = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val auj = LocalDate.now(zone)
        return when {
            d < 60_000 -> "À l'instant"
            d < 3600_000 -> "Il y a ${d / 60_000} min"
            jour == auj -> "Il y a ${d / 3600_000} h"
            jour == auj.minusDays(1) -> "Hier"
            jour.isAfter(auj.minusDays(7)) -> jour.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar { it.uppercase() }
            else -> "Le ${jour.dayOfMonth} ${jour.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)}"
        }
    }

    fun infos(c: Context, paquet: String): PackageInfo? = try {
        pm(c).getPackageInfo(paquet, PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun nom(c: Context, paquet: String): String = try {
        pm(c).getApplicationLabel(pm(c).getApplicationInfo(paquet, PackageManager.MATCH_DISABLED_COMPONENTS)).toString()
    } catch (_: Exception) {
        paquet
    }

    fun icone(c: Context, paquet: String, dp: Int = 72): ImageBitmap? = try {
        val t = (dp * c.resources.displayMetrics.density).toInt()
        pm(c).getApplicationIcon(paquet).toBitmap(t, t).asImageBitmap()
    } catch (_: Exception) {
        null
    }

    /** D'où vient l'appli : avec Sama, depuis Sugu, ou d'ailleurs (fichier, autre boutique). */
    fun provenance(c: Context, info: PackageInfo): String {
        val app = info.applicationInfo ?: return ""
        if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0 && app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0) return "installée avec Sama"
        val source = try {
            pm(c).getInstallSourceInfo(info.packageName).installingPackageName
        } catch (_: Exception) {
            null
        }
        return when (source) {
            null, "com.android.shell" -> "installée depuis un fichier"
            "africa.samaos.sugu" -> "installée depuis Sugu"
            "com.android.packageinstaller", "com.google.android.packageinstaller" -> "installée depuis un fichier"
            else -> "installée depuis " + nom(c, source)
        }
    }

    fun systeme(info: PackageInfo) = (info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0

    /** Les applis qu'on ne laisse pas désactiver : sans elles, le téléphone ne marche plus. */
    fun indispensable(c: Context, paquet: String): Boolean {
        val tm = c.getSystemService(android.telecom.TelecomManager::class.java)
        return paquet in setOf(c.packageName, "africa.samaos.accueil", "com.android.settings", "com.android.systemui", "com.android.phone", "android") ||
            paquet == tm?.defaultDialerPackage || paquet == android.provider.Telephony.Sms.getDefaultSmsPackage(c) ||
            c.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.inputMethodList?.any { it.packageName == paquet } == true
    }

    fun forcerArret(c: Context, paquet: String) = try {
        val am = c.getSystemService(ActivityManager::class.java)
        am.javaClass.getMethod("forceStopPackage", String::class.java).invoke(am, paquet)
        true
    } catch (_: Exception) {
        false
    }

    fun activer(c: Context, paquet: String, oui: Boolean) = try {
        pm(c).setApplicationEnabledSetting(
            paquet,
            if (oui) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            0,
        )
        true
    } catch (_: Exception) {
        false
    }

    /** Ce que l'appli occupe : l'appli, ses données, son cache. */
    fun stockage(c: Context, paquet: String): Triple<Long, Long, Long>? = try {
        val s = c.getSystemService(StorageStatsManager::class.java).queryStatsForPackage(StorageManager.UUID_DEFAULT, paquet, Process.myUserHandle())
        Triple(s.appBytes, s.dataBytes, s.cacheBytes)
    } catch (_: Exception) {
        null
    }

    fun viderCache(c: Context, paquet: String) = try {
        val pm = pm(c)
        val type = Class.forName("android.content.pm.IPackageDataObserver")
        pm.javaClass.getMethod("deleteApplicationCacheFiles", String::class.java, type).invoke(pm, paquet, null)
        true
    } catch (_: Exception) {
        false
    }

    fun effacerDonnees(c: Context, paquet: String) = try {
        val am = c.getSystemService(ActivityManager::class.java)
        val type = Class.forName("android.content.pm.IPackageDataObserver")
        am.javaClass.getMethod("clearApplicationUserData", String::class.java, type).invoke(am, paquet, null) as Boolean
    } catch (_: Exception) {
        false
    }

    /** La data mobile de l'appli ce mois-ci (droit READ_NETWORK_USAGE_HISTORY du système). */
    fun dataDuMois(c: Context, paquet: String): Long? = try {
        val uid = pm(c).getApplicationInfo(paquet, 0).uid
        val debut = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nsm = c.getSystemService(NetworkStatsManager::class.java)
        @Suppress("DEPRECATION")
        val stats = nsm.queryDetailsForUid(ConnectivityManager.TYPE_MOBILE, null, debut, System.currentTimeMillis(), uid)
        var total = 0L
        val b = NetworkStats.Bucket()
        while (stats.hasNextBucket()) {
            stats.getNextBucket(b)
            total += b.rxBytes + b.txBytes
        }
        stats.close()
        total
    } catch (_: Exception) {
        null
    }

    // Autorisations

    /** Les groupes que l'appli demande, et pour chacun : accordé ou non, en arrière-plan ou seulement ouverte. */
    fun autorisations(c: Context, paquet: String): List<Triple<Groupe, Boolean, String?>> {
        val info = infos(c, paquet) ?: return emptyList()
        val demandes = info.requestedPermissions?.toSet().orEmpty()
        val pm = pm(c)
        return Groupe.entries.mapNotNull { g ->
            val siens = g.droits.filter { it in demandes && estDangereux(pm, it) }
            if (siens.isEmpty()) return@mapNotNull null
            val accordes = siens.filter { pm.checkPermission(it, paquet) == PackageManager.PERMISSION_GRANTED }
            val detail = when {
                accordes.isEmpty() -> null
                g == Groupe.POSITION -> if ("android.permission.ACCESS_BACKGROUND_LOCATION" in accordes) "Toujours, même fermée" else "Seulement quand l'appli est ouverte"
                g == Groupe.APPAREIL_PHOTO || g == Groupe.MICRO -> "Seulement quand l'appli est ouverte"
                g == Groupe.SMS -> "Lire vos SMS, y compris les codes de confirmation"
                else -> "Autorisé"
            }
            Triple(g, accordes.isNotEmpty(), detail)
        }
    }

    private fun estDangereux(pm: PackageManager, droit: String) = try {
        pm.getPermissionInfo(droit, 0).protection == PermissionInfo.PROTECTION_DANGEROUS
    } catch (_: Exception) {
        false
    }

    /** Accorder ou retirer un groupe entier (GRANT/REVOKE_RUNTIME_PERMISSIONS, droits du système). */
    fun reglerGroupe(c: Context, paquet: String, g: Groupe, oui: Boolean) {
        val pm = pm(c)
        val demandes = infos(c, paquet)?.requestedPermissions?.toSet().orEmpty()
        val methode = pm.javaClass.getMethod(
            if (oui) "grantRuntimePermission" else "revokeRuntimePermission",
            String::class.java, String::class.java, UserHandle::class.java,
        )
        g.droits.filter { it in demandes }.forEach { d ->
            // La position « toujours » ne s'accorde pas d'office : seulement quand l'appli est ouverte.
            if (oui && d == "android.permission.ACCESS_BACKGROUND_LOCATION") return@forEach
            try {
                methode.invoke(pm, paquet, d, Process.myUserHandle())
            } catch (_: Exception) {
            }
        }
    }

    private const val OP_RETRAIT = "android:auto_revoke_permissions_if_unused"

    fun retraitSiEndormie(c: Context, paquet: String): Boolean = try {
        val uid = pm(c).getApplicationInfo(paquet, 0).uid
        c.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(OP_RETRAIT, uid, paquet) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        true
    }

    fun reglerRetraitSiEndormie(c: Context, paquet: String, oui: Boolean) = reglerOp(c, OP_RETRAIT, paquet, oui)

    fun reglerOp(c: Context, op: String, paquet: String, oui: Boolean) = try {
        val uid = pm(c).getApplicationInfo(paquet, 0).uid
        val aom = c.getSystemService(AppOpsManager::class.java)
        aom.javaClass.getMethod("setMode", String::class.java, Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType)
            .invoke(aom, op, uid, paquet, if (oui) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED)
        true
    } catch (_: Exception) {
        false
    }

    fun op(c: Context, op: String, paquet: String): Boolean = try {
        val uid = pm(c).getApplicationInfo(paquet, 0).uid
        c.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(op, uid, paquet) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }

    /** Les applis qui demandent un droit (pour les accès spéciaux). */
    fun quiDemande(c: Context, droit: String): List<String> =
        pm(c).getPackagesHoldingPermissions(arrayOf(droit), 0).map { it.packageName }
            .filter { it != c.packageName && !(infos(c, it)?.let { i -> systeme(i) } ?: true) }

    // Rôles (applis par défaut)

    fun titulaire(c: Context, role: String): String? = try {
        val rm = c.getSystemService(RoleManager::class.java)
        (rm.javaClass.getMethod("getRoleHolders", String::class.java).invoke(rm, role) as List<*>).firstOrNull() as String?
    } catch (_: Exception) {
        null
    }

    fun choisirTitulaire(c: Context, role: String, paquet: String, fini: (Boolean) -> Unit) {
        try {
            val rm = c.getSystemService(RoleManager::class.java)
            rm.javaClass.getMethod(
                "addRoleHolderAsUser", String::class.java, String::class.java, Int::class.javaPrimitiveType,
                UserHandle::class.java, Executor::class.java, Consumer::class.java,
            ).invoke(rm, role, paquet, 0, Process.myUserHandle(), c.mainExecutor, Consumer<Boolean> { fini(it) })
        } catch (_: Exception) {
            fini(false)
        }
    }

    /** Les applis qui peuvent tenir un rôle, d'après ce qu'elles savent ouvrir. */
    fun candidats(c: Context, role: String): List<String> {
        val pm = pm(c)
        val intents = when (role) {
            RoleManager.ROLE_BROWSER -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://sama.ci")).addCategory(Intent.CATEGORY_BROWSABLE))
            RoleManager.ROLE_DIALER -> listOf(Intent(Intent.ACTION_DIAL))
            RoleManager.ROLE_SMS -> listOf(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            RoleManager.ROLE_HOME -> listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            RoleManager.ROLE_ASSISTANT -> listOf(Intent(Intent.ACTION_ASSIST))
            else -> emptyList()
        }
        return intents.flatMap { pm.queryIntentActivities(it, PackageManager.MATCH_ALL) }
            .map { it.activityInfo.packageName }
            .filter { it != "com.android.settings" && it != "com.android.fallbackhome" }
            .distinct()
    }

    // Applis en pause (hibernation d'Android)

    fun enPause(c: Context): List<String> = try {
        val ahm = c.getSystemService(Class.forName("android.apphibernation.AppHibernationManager"))
        @Suppress("UNCHECKED_CAST")
        (ahm.javaClass.getMethod("getHibernatingPackagesForUser").invoke(ahm) as List<String>)
    } catch (_: Exception) {
        emptyList()
    }

    fun reveiller(c: Context, paquet: String) = try {
        val ahm = c.getSystemService(Class.forName("android.apphibernation.AppHibernationManager"))
        ahm.javaClass.getMethod("setHibernatingForUser", String::class.java, Boolean::class.javaPrimitiveType).invoke(ahm, paquet, false)
        true
    } catch (_: Exception) {
        false
    }
}

fun taille(o: Long): String = when {
    o >= 1_000_000_000 -> String.format(Locale.FRENCH, "%.1f Go", o / 1e9)
    o >= 1_000_000 -> "${o / 1_000_000} Mo"
    o >= 1_000 -> "${o / 1_000} Ko"
    else -> "$o octets"
}

/** Les applications (maquette l3-applis). */
@Composable
fun PageApplis(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var applis by remember { mutableStateOf(emptyList<AppliVue>()) }
    var usages by remember { mutableStateOf(emptyMap<String, Long>()) }
    var pause by remember { mutableIntStateOf(0) }
    var recherche by remember { mutableStateOf("") }
    var parDefaut by remember { mutableStateOf("") }
    LaunchedEffect(reprise) {
        withContext(Dispatchers.IO) {
            applis = MoteurApplis.applis(c)
            usages = MoteurApplis.derniersUsages(c)
            pause = MoteurApplis.enPause(c).size
            parDefaut = listOf(RoleManager.ROLE_BROWSER, RoleManager.ROLE_SMS, RoleManager.ROLE_DIALER)
                .mapNotNull { MoteurApplis.titulaire(c, it)?.let { p -> MoteurApplis.nom(c, p) } }.joinToString(", ")
        }
    }
    PageReglages(titre = "Applications", sousTitre = "${applis.size} dans ${EspaceActif.nom(c)}", retour = nav.retour) {
        section(cle = "haut") {
            Ligne("Applis par défaut", detail = parDefaut.ifBlank { null }, icone = Icones.APPLI) { nav.aller(Page.ApplisDefaut) }
            Ligne("Applis en pause", detail = if (pause == 0) "Aucune appli ne dort" else "$pause appli${if (pause > 1) "s dorment" else " dort"}", icone = Icones.NUIT) {
                nav.aller(Page.ApplisInutilisees)
            }
            Ligne("Accès spéciaux", detail = "Des pouvoirs que peu d'applis doivent avoir", icone = Icones.BOUCLIER) { nav.aller(Page.AccesSpeciaux) }
        }
        section(cle = "recherche") { ChampRecherche(recherche, "Rechercher une appli") { recherche = it } }
        if (recherche.isNotBlank()) {
            val m = simplifier(recherche)
            section(cle = "trouvees") {
                applis.filter { simplifier(it.nom).contains(m) }.forEach { a -> LigneAppli(a, usages[a.paquet], nav) }
            }
            return@PageReglages
        }
        val recentes = applis.filter { usages[it.paquet] != null && it.paquet != c.packageName }.sortedByDescending { usages[it.paquet] }.take(6)
        if (recentes.isNotEmpty()) {
            section("Utilisées récemment", cle = "recentes") { recentes.forEach { a -> LigneAppli(a, usages[a.paquet], nav) } }
        }
        section("Toutes les applications", cle = "toutes") { applis.forEach { a -> LigneAppli(a, null, nav) } }
    }
}

@Composable
private fun LigneAppli(a: AppliVue, usage: Long?, nav: Nav) {
    Ligne(
        a.nom,
        detail = when {
            !a.active -> "Désactivée"
            usage != null -> MoteurApplis.quand(usage)
            else -> null
        },
        image = a.icone,
    ) { nav.aller(Page.AppliInfos(a.paquet)) }
}

/** Les infos d'une appli (maquette l3-appli-infos). */
@Composable
fun PageAppliInfos(paquet: String, nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf<PackageInfo?>(null) }
    var stockage by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var data by remember { mutableStateOf<Long?>(null) }
    var autorisations by remember { mutableStateOf(emptyList<Triple<Groupe, Boolean, String?>>()) }
    var confirmer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version, reprise) {
        withContext(Dispatchers.IO) {
            info = MoteurApplis.infos(c, paquet)
            stockage = MoteurApplis.stockage(c, paquet)
            data = MoteurApplis.dataDuMois(c, paquet)
            autorisations = MoteurApplis.autorisations(c, paquet)
        }
    }
    val i = info
    val icone = remember(paquet) { MoteurApplis.icone(c, paquet) }
    val active = i?.applicationInfo?.enabled != false
    PageReglages(
        titre = MoteurApplis.nom(c, paquet),
        sousTitre = i?.let { listOfNotNull(it.versionName?.let { v -> "Version $v" }, MoteurApplis.provenance(c, it)).joinToString(" · ") },
        retour = nav.retour,
        entete = if (icone != null) {
            { Image(icone, null, Modifier.padding(top = 12.dp).size(56.dp)) }
        } else {
            null
        },
    ) {
        if (i == null) {
            section(cle = "absente") { Explication("Cette appli n'est plus sur le téléphone.") }
            return@PageReglages
        }
        section(cle = "actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.packageManager.getLaunchIntentForPackage(paquet)?.let { lancer ->
                    if (active) Puce("Ouvrir") { c.startActivity(lancer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                Puce("Forcer l'arrêt") { confirmer = "arret" }
                if (!MoteurApplis.indispensable(c, paquet)) {
                    if (MoteurApplis.systeme(i)) {
                        Puce(if (active) "Désactiver" else "Activer") { if (active) confirmer = "desactiver" else { MoteurApplis.activer(c, paquet, true); version++ } }
                    } else {
                        Puce("Désinstaller") { c.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", paquet, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
            when (confirmer) {
                "arret" -> Confirmation("Arrêter l'appli de force ? Elle peut mal se comporter.", "Forcer l'arrêt", { confirmer = null }) {
                    MoteurApplis.forcerArret(c, paquet)
                    confirmer = null
                }
                "desactiver" -> Confirmation("Désactiver l'appli ? Elle disparaît de cet Espace, sans être effacée du téléphone.", "Désactiver", { confirmer = null }) {
                    MoteurApplis.activer(c, paquet, false)
                    confirmer = null
                    version++
                }
                "donnees" -> Confirmation("Effacer toutes les données de l'appli ? Comptes, réglages et fichiers de l'appli seront perdus.", "Effacer", { confirmer = null }) {
                    MoteurApplis.effacerDonnees(c, paquet)
                    confirmer = null
                    version++
                }
            }
        }
        section("Utilisation", cle = "usage") {
            Ligne("Notifications", detail = if (autorisations.firstOrNull { it.first == Groupe.NOTIFICATIONS }?.second == false) "Bloquées" else "Activées", icone = Icones.CLOCHE) {
                nav.aller(Page.NotifsAppli(paquet))
            }
            val accordees = autorisations.filter { it.second }.map { it.first.nom.lowercase() }
            Ligne(
                "Autorisations",
                detail = if (accordees.isEmpty()) "Aucune" else accordees.joinToString(", ").replaceFirstChar { it.uppercase() },
                icone = Icones.BOUCLIER,
            ) { nav.aller(Page.AppliAutorisations(paquet)) }
            data?.let { Ligne("Data mobile", detail = "${taille(it)} ce mois", icone = Icones.DONNEES, fin = Fin.Rien) }
            stockage?.let { (app, donnees, cache) ->
                Ligne("Stockage", detail = "${taille(app + donnees)}, dont ${taille(cache)} de cache", icone = Icones.DOSSIER, fin = Fin.Rien)
                Row(Modifier.fillMaxWidth().padding(start = 38.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Puce("Vider le cache") {
                        MoteurApplis.viderCache(c, paquet)
                        version++
                    }
                    if (!MoteurApplis.indispensable(c, paquet)) Puce("Effacer les données") { confirmer = "donnees" }
                }
            }
        }
        section("Par défaut", cle = "defaut") {
            Ligne("Ouvrir les liens", detail = "Les liens que l'appli sait ouvrir", icone = Icones.GLOBE) {
                nav.android(Intent("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS", Uri.fromParts("package", paquet, null)))
            }
        }
    }
}

/** Une demande de confirmation sur le sol, avant ce qu'on ne peut pas défaire. */
@Composable
fun Confirmation(texte: String, action: String, annuler: () -> Unit, faire: () -> Unit) {
    Explication(texte)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        BoutonTexte("Annuler", onClick = annuler)
        BoutonTexte(action, danger = true, onClick = faire)
    }
}

/** Les autorisations d'une appli (maquette l3-appli-autorisations). */
@Composable
fun PageAppliAutorisations(paquet: String, nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var liste by remember { mutableStateOf(emptyList<Triple<Groupe, Boolean, String?>>()) }
    var retrait by remember { mutableStateOf(true) }
    LaunchedEffect(version, reprise) {
        liste = withContext(Dispatchers.IO) { MoteurApplis.autorisations(c, paquet) }
        retrait = MoteurApplis.retraitSiEndormie(c, paquet)
    }
    PageReglages(titre = "Autorisations", sousTitre = MoteurApplis.nom(c, paquet), retour = nav.retour) {
        val oui = liste.filter { it.second }
        val non = liste.filter { !it.second }
        if (liste.isEmpty()) section(cle = "rien") { Explication("Cette appli ne demande aucune autorisation.") }
        if (oui.isNotEmpty()) {
            section("Autorisées", cle = "oui") {
                oui.forEach { (g, _, detail) ->
                    Ligne(g.nom, detail = detail, icone = g.icone, fin = Fin.Inter(true)) {
                        MoteurApplis.reglerGroupe(c, paquet, g, false)
                        version++
                    }
                }
            }
        }
        if (non.isNotEmpty()) {
            section("Refusées", cle = "non") {
                non.forEach { (g, _, _) ->
                    Ligne(g.nom, icone = g.icone, fin = Fin.Inter(false)) {
                        MoteurApplis.reglerGroupe(c, paquet, g, true)
                        version++
                    }
                }
            }
        }
        section(cle = "retrait") {
            Ligne("Retirer les autorisations si l'appli dort", detail = "Après 3 mois sans usage", fin = Fin.Inter(retrait)) {
                MoteurApplis.reglerRetraitSiEndormie(c, paquet, !retrait)
                version++
            }
            if (oui.any { it.first == Groupe.SMS }) {
                Explication("Une appli qui lit vos SMS voit aussi les codes de confirmation de votre banque et de mobile money. Ne laissez cet accès qu'aux applis de confiance.", africa.samaos.banco.LocalBanco.current.lateriteTexte)
            }
        }
    }
}

/** Les applis par défaut (maquette l3-applis-defaut). */
@Composable
fun PageApplisDefaut(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var ouvert by remember { mutableStateOf<String?>(null) }
    val roles = listOf(
        Triple(RoleManager.ROLE_BROWSER, "Navigateur", Icones.GLOBE),
        Triple(RoleManager.ROLE_DIALER, "Téléphone", Icones.APPEL),
        Triple(RoleManager.ROLE_SMS, "SMS", Icones.MESSAGE),
        Triple(RoleManager.ROLE_HOME, "Accueil", Icones.APPLI),
        Triple(RoleManager.ROLE_ASSISTANT, "Assistant vocal", Icones.MUSIQUE),
    )
    var titulaires by remember { mutableStateOf(emptyMap<String, String?>()) }
    LaunchedEffect(version, reprise) { titulaires = withContext(Dispatchers.IO) { roles.associate { it.first to MoteurApplis.titulaire(c, it.first) } } }
    PageReglages(titre = "Par défaut", sousTitre = "Les applis qui s'ouvrent d'office", retour = nav.retour) {
        section(cle = "roles") {
            roles.forEach { (role, nom, icone) ->
                val t = titulaires[role]
                Ligne(nom, detail = t?.let { MoteurApplis.nom(c, it) } ?: "Aucune", icone = icone) { ouvert = if (ouvert == role) null else role }
                if (ouvert == role) {
                    val candidats = remember(role) { MoteurApplis.candidats(c, role) }
                    candidats.forEach { p ->
                        Ligne(MoteurApplis.nom(c, p), image = MoteurApplis.icone(c, p, 32), fin = Fin.Choix(p == t)) {
                            MoteurApplis.choisirTitulaire(c, role, p) { version++ }
                            ouvert = null
                        }
                    }
                    if (role == RoleManager.ROLE_HOME) Explication("Sans l'Accueil de Sama, les Espaces, la Cour et le Pouls ne sont plus là.")
                }
            }
        }
        section("Liens", cle = "liens") {
            Ligne("Ouvrir les liens dans les applis", detail = "Les liens d'un site s'ouvrent dans son appli", icone = Icones.GLOBE) {
                nav.android(Intent("android.settings.MANAGE_DOMAIN_URLS"))
            }
        }
    }
}

/** Les accès spéciaux (maquette l3-acces-speciaux) et, pour chacun, les applis qui l'ont. */
enum class Acces(val nom: String, val droit: String, val op: String?, val icone: String, val explication: String) {
    PAR_DESSUS("Afficher par-dessus les autres applis", "android.permission.SYSTEM_ALERT_WINDOW", "android:system_alert_window", Icones.APPLI,
        "Une appli qui s'affiche par-dessus les autres peut imiter l'écran de votre banque. Les arnaqueurs s'en servent."),
    INCONNUES("Installer des applis inconnues", "android.permission.REQUEST_INSTALL_PACKAGES", "android:request_install_packages", Icones.DESINSTALLER,
        "Une appli autorisée ici peut installer d'autres applis, hors de Sugu. N'accordez cet accès que si vous savez pourquoi."),
    REGLAGES("Modifier les réglages du système", "android.permission.WRITE_SETTINGS", "android:write_settings", Icones.REGLAGES,
        "L'appli peut changer la luminosité, la sonnerie et d'autres réglages."),
    TOUS_FICHIERS("Accéder à tous les fichiers", "android.permission.MANAGE_EXTERNAL_STORAGE", "android:manage_external_storage", Icones.DOSSIER,
        "L'appli voit tous vos documents, photos et téléchargements."),
}

@Composable
fun PageAccesSpeciaux(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var comptes by remember { mutableStateOf(emptyMap<Acces, Int>()) }
    LaunchedEffect(reprise) {
        comptes = withContext(Dispatchers.IO) {
            Acces.entries.associateWith { a -> MoteurApplis.quiDemande(c, a.droit).count { a.op == null || MoteurApplis.op(c, a.op, it) } }
        }
    }
    fun compte(n: Int?) = when (n) {
        null -> null
        0 -> "Aucune"
        1 -> "1 appli"
        else -> "$n applis"
    }
    PageReglages(titre = "Accès spéciaux", sousTitre = "Des pouvoirs que peu d'applis doivent avoir", retour = nav.retour) {
        section(cle = "liste") {
            Acces.entries.forEach { a -> Ligne(a.nom, detail = compte(comptes[a]), icone = a.icone) { nav.aller(Page.AccesSpecial(a)) } }
            Ligne("Ignorer l'économie de batterie", icone = Icones.BATTERIE) { nav.android(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            Ligne("Lire les notifications", icone = Icones.CLOCHE) { nav.android(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        section(cle = "note") {
            Explication("Une appli de Sugu qui demande l'un de ces accès doit dire pourquoi, avant l'installation.")
        }
    }
}

@Composable
fun PageAccesSpecial(a: Acces, nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var liste by remember { mutableStateOf(emptyList<Pair<String, Boolean>>()) }
    LaunchedEffect(version) {
        liste = withContext(Dispatchers.IO) { MoteurApplis.quiDemande(c, a.droit).map { it to (a.op != null && MoteurApplis.op(c, a.op, it)) } }
    }
    PageReglages(titre = a.nom, retour = nav.retour) {
        section(cle = "explication") { Explication(a.explication) }
        section(cle = "applis") {
            if (liste.isEmpty()) Explication("Aucune appli de cet Espace ne demande cet accès.")
            liste.forEach { (p, oui) ->
                // Installer hors de Sugu : on retire l'accès tout de suite, mais on ne l'accorde qu'après 24 heures.
                val attente = if (a == Acces.INCONNUES && !oui) africa.samaos.bouclier.Bouclier.delaiInstallation(c, p) else null
                Ligne(
                    MoteurApplis.nom(c, p), detail = attente?.let { "En attente · possible ${MoteurBouclier.quand(it)}" },
                    image = MoteurApplis.icone(c, p, 32), fin = Fin.Inter(oui),
                ) {
                    if (a == Acces.INCONNUES && !oui) {
                        nav.aller(Page.DelaiInstallation(p))
                    } else {
                        a.op?.let { MoteurApplis.reglerOp(c, it, p, !oui) }
                        version++
                    }
                }
            }
        }
    }
}

/** Les applis en pause (maquette l3-applis-inutilisees). */
@Composable
fun PageApplisInutilisees(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var liste by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(version) { liste = withContext(Dispatchers.IO) { MoteurApplis.enPause(c) } }
    PageReglages(titre = "En pause", sousTitre = "Pas ouvertes depuis 3 mois", retour = nav.retour) {
        section(if (liste.isEmpty()) null else "Endormies", cle = "liste") {
            if (liste.isEmpty()) Explication("Aucune appli ne dort.")
            liste.forEach { p ->
                Ligne(MoteurApplis.nom(c, p), image = MoteurApplis.icone(c, p, 32), fin = Fin.Valeur("Réveiller")) {
                    MoteurApplis.reveiller(c, p)
                    version++
                }
            }
        }
        section(cle = "note") {
            Explication("Une appli en pause garde vos données, perd ses autorisations et se tait. Elle se réveille dès que vous l'ouvrez.")
        }
    }
}
