package africa.samaos.reglages

import android.app.AutomaticZenRule
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import africa.samaos.banco.Icones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Une notification de l'historique. */
class NotifPassee(val paquet: String, val titre: String, val texte: String, val heure: Long)

/**
 * Les notifications, avec les droits de l'interface du système (STATUS_BAR_SERVICE, ACCESS_NOTIFICATIONS,
 * accordés à la clé de la plateforme) : couper une appli, régler ses catégories, lire l'historique.
 */
object MoteurNotifs {
    private fun service(): Any = Class.forName("android.app.NotificationManager").getMethod("getService").invoke(null)!!

    private fun uid(c: Context, paquet: String) = c.packageManager.getApplicationInfo(paquet, 0).uid

    fun activees(c: Context, paquet: String): Boolean = try {
        val s = service()
        s.javaClass.getMethod("areNotificationsEnabledForPackage", String::class.java, Int::class.javaPrimitiveType)
            .invoke(s, paquet, uid(c, paquet)) as Boolean
    } catch (_: Exception) {
        true
    }

    fun activer(c: Context, paquet: String, oui: Boolean) = try {
        val s = service()
        s.javaClass.getMethod("setNotificationsEnabledForPackage", String::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(s, paquet, uid(c, paquet), oui)
        true
    } catch (_: Exception) {
        false
    }

    @Suppress("UNCHECKED_CAST")
    fun categories(c: Context, paquet: String): List<NotificationChannel> = try {
        val s = service()
        val r = s.javaClass.getMethod("getNotificationChannelsForPackage", String::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(s, paquet, uid(c, paquet), false)
        (r!!.javaClass.getMethod("getList").invoke(r) as List<NotificationChannel>)
    } catch (_: Exception) {
        emptyList()
    }

    fun reglerCategorie(c: Context, paquet: String, canal: NotificationChannel, importance: Int) = try {
        val s = service()
        canal.importance = importance
        canal.javaClass.getMethod("lockFields", Int::class.javaPrimitiveType).invoke(canal, 4)
        s.javaClass.getMethod("updateNotificationChannelForPackage", String::class.java, Int::class.javaPrimitiveType, NotificationChannel::class.java)
            .invoke(s, paquet, uid(c, paquet), canal)
        true
    } catch (_: Exception) {
        false
    }

    fun nomImportance(i: Int) = when (i) {
        NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_MAX -> "Son et bandeau"
        NotificationManager.IMPORTANCE_DEFAULT -> "Avec son"
        NotificationManager.IMPORTANCE_LOW -> "Sans son"
        NotificationManager.IMPORTANCE_MIN -> "Sans son, regroupées"
        NotificationManager.IMPORTANCE_NONE -> "Bloquées"
        else -> "Automatique"
    }

    /** Les applis qui ont envoyé des notifications. */
    fun applisAvecNotifs(c: Context): List<String> {
        val pm = c.packageManager
        val lanceur = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(lanceur, 0).map { it.activityInfo.packageName }.distinct()
            .filter { p -> categories(c, p).isNotEmpty() }
    }

    /** L'historique des notifications des dernières 24 h (droit ACCESS_NOTIFICATIONS). */
    fun historique(c: Context): List<NotifPassee> = try {
        val s = service()
        val h = s.javaClass.getMethod("getNotificationHistory", String::class.java, String::class.java).invoke(s, c.packageName, null)!!
        val liste = mutableListOf<NotifPassee>()
        val suivante = h.javaClass.getMethod("hasNextNotification")
        val lire = h.javaClass.getMethod("getNextNotification")
        while (suivante.invoke(h) as Boolean) {
            val n = lire.invoke(h)!!
            fun champ(nom: String) = n.javaClass.getMethod(nom).invoke(n)
            liste += NotifPassee(
                champ("getPackage") as String,
                (champ("getTitle") as String?).orEmpty(),
                (champ("getText") as String?).orEmpty(),
                champ("getPostedTimeMs") as Long,
            )
        }
        liste.sortedByDescending { it.heure }
    } catch (_: Exception) {
        emptyList()
    }

    fun historiqueActif(c: Context) = Settings.Secure.getInt(c.contentResolver, "notification_history_enabled", 0) == 1

    fun reglerHistorique(c: Context, oui: Boolean) = Settings.Secure.putInt(c.contentResolver, "notification_history_enabled", if (oui) 1 else 0)

    // Ne pas déranger : les Réglages s'accordent l'accès comme les Paramètres d'Android.

    fun nm(c: Context): NotificationManager {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            try {
                nm.javaClass.getMethod("setNotificationPolicyAccessGranted", String::class.java, Boolean::class.javaPrimitiveType)
                    .invoke(nm, c.packageName, true)
            } catch (_: Exception) {
            }
        }
        return nm
    }

    fun nePasDeranger(c: Context) = nm(c).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL

    fun reglerNePasDeranger(c: Context, oui: Boolean) = try {
        nm(c).setInterruptionFilter(if (oui) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        true
    } catch (_: Exception) {
        false
    }

    fun politique(c: Context): NotificationManager.Policy? = try {
        nm(c).notificationPolicy
    } catch (_: Exception) {
        null
    }

    fun reglerPolitique(c: Context, p: NotificationManager.Policy) = try {
        nm(c).notificationPolicy = p
        true
    } catch (_: Exception) {
        false
    }

    fun regles(c: Context): Map<String, AutomaticZenRule> = try {
        nm(c).automaticZenRules
    } catch (_: Exception) {
        emptyMap()
    }
}

fun heure(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** Notifications : Ne pas déranger, l'écran verrouillé, l'historique, et chaque appli. */
@Composable
fun PageNotifications(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var applis by remember { mutableStateOf(emptyList<String>()) }
    var npd by remember { mutableStateOf(false) }
    LaunchedEffect(reprise) {
        npd = MoteurNotifs.nePasDeranger(c)
        applis = withContext(Dispatchers.IO) { MoteurNotifs.applisAvecNotifs(c) }
    }
    PageReglages(titre = "Notifications", retour = nav.retour) {
        section(cle = "general") {
            Ligne("Ne pas déranger", detail = if (npd) "Activé" else "Désactivé", icone = Icones.NE_PAS_DERANGER) { nav.aller(Page.NePasDeranger) }
            Ligne("Écran verrouillé", detail = "Ce que montrent les notifications", icone = Icones.CADENAS) { nav.aller(Page.NotifsVerrou) }
            Ligne("Historique", detail = "Les notifications des dernières 24 h", icone = Icones.HORLOGE) { nav.aller(Page.HistoriqueNotifs) }
        }
        section("Par appli", cle = "applis") {
            applis.forEach { p ->
                Ligne(
                    MoteurApplis.nom(c, p),
                    detail = if (MoteurNotifs.activees(c, p)) null else "Coupées",
                    image = MoteurApplis.icone(c, p, 32),
                ) { nav.aller(Page.NotifsAppli(p)) }
            }
        }
    }
}

/** Les notifications d'une appli (maquette l3-notifs-appli). */
@Composable
fun PageNotifsAppli(paquet: String, nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var activees by remember { mutableStateOf(true) }
    var canaux by remember { mutableStateOf(emptyList<NotificationChannel>()) }
    var ouvert by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version) {
        activees = MoteurNotifs.activees(c, paquet)
        canaux = withContext(Dispatchers.IO) { MoteurNotifs.categories(c, paquet) }
    }
    PageReglages(titre = "Notifications", sousTitre = MoteurApplis.nom(c, paquet), retour = nav.retour) {
        section(cle = "toutes") {
            Ligne("Toutes les notifications", image = MoteurApplis.icone(c, paquet, 32), fin = Fin.Inter(activees)) {
                MoteurNotifs.activer(c, paquet, !activees)
                version++
            }
        }
        if (!activees) {
            section(cle = "coupees") { Explication("L'appli ne vous envoie plus rien. Les appels et SMS d'urgence passent toujours.") }
            return@PageReglages
        }
        if (canaux.isNotEmpty()) {
            section("Catégories", cle = "categories") {
                canaux.forEach { k ->
                    Ligne(k.name?.toString() ?: k.id, detail = MoteurNotifs.nomImportance(k.importance)) { ouvert = if (ouvert == k.id) null else k.id }
                    if (ouvert == k.id) {
                        listOf(
                            NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_DEFAULT,
                            NotificationManager.IMPORTANCE_LOW, NotificationManager.IMPORTANCE_MIN, NotificationManager.IMPORTANCE_NONE,
                        ).forEach { i ->
                            Ligne(MoteurNotifs.nomImportance(i), fin = Fin.Choix(k.importance == i)) {
                                MoteurNotifs.reglerCategorie(c, paquet, k, i)
                                ouvert = null
                                version++
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ne pas déranger (maquette l3-ne-pas-deranger). */
@Composable
fun PageNePasDeranger(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var actif by remember { mutableStateOf(false) }
    var p by remember { mutableStateOf<NotificationManager.Policy?>(null) }
    var regles by remember { mutableStateOf(emptyMap<String, AutomaticZenRule>()) }
    LaunchedEffect(version) {
        actif = MoteurNotifs.nePasDeranger(c)
        p = MoteurNotifs.politique(c)
        regles = MoteurNotifs.regles(c)
    }
    fun basculer(categorie: Int) {
        val pol = p ?: return
        val cats = pol.priorityCategories xor categorie
        MoteurNotifs.reglerPolitique(c, NotificationManager.Policy(cats, pol.priorityCallSenders, pol.priorityMessageSenders, pol.suppressedVisualEffects))
        version++
    }
    PageReglages(titre = "Ne pas déranger", retour = nav.retour) {
        section(cle = "inter") {
            Ligne("Ne pas déranger", detail = if (actif) "Activé" else "Le téléphone sonne normalement", icone = Icones.NE_PAS_DERANGER, fin = Fin.Inter(actif)) {
                MoteurNotifs.reglerNePasDeranger(c, !actif)
                version++
            }
        }
        val pol = p
        if (pol != null) {
            section("Qui peut passer", cle = "qui") {
                val appels = pol.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS != 0
                Ligne(
                    "Appels des favoris",
                    detail = "Les contacts marqués d'une étoile",
                    icone = Icones.APPEL,
                    fin = Fin.Inter(appels && pol.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED),
                ) {
                    val cats = if (appels && pol.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED) {
                        pol.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS.inv()
                    } else {
                        pol.priorityCategories or NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
                    }
                    MoteurNotifs.reglerPolitique(c, NotificationManager.Policy(cats, NotificationManager.Policy.PRIORITY_SENDERS_STARRED, pol.priorityMessageSenders, pol.suppressedVisualEffects))
                    version++
                }
                Ligne(
                    "Appels répétés",
                    detail = "Un 2e appel de la même personne en 15 min sonne",
                    icone = Icones.APPEL,
                    fin = Fin.Inter(pol.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS != 0),
                ) { basculer(NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS) }
                Ligne(
                    "Alarmes",
                    icone = Icones.HORLOGE,
                    fin = Fin.Inter(pol.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0),
                ) { basculer(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) }
                Ligne(
                    "Rappels et événements",
                    icone = Icones.CLOCHE,
                    fin = Fin.Inter(pol.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS != 0),
                ) { basculer(NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS) }
            }
        }
        if (regles.isNotEmpty()) {
            section("Moments", cle = "moments") {
                regles.forEach { (id, r) ->
                    Ligne(r.name?.toString() ?: "Moment", detail = if (r.isEnabled) "Activé" else "Désactivé", icone = Icones.NUIT) {
                        nav.android(android.content.Intent("android.settings.AUTOMATIC_ZEN_RULE_SETTINGS").putExtra("android.provider.extra.AUTOMATIC_ZEN_RULE_ID", id))
                    }
                }
            }
        }
    }
}

/** Ce que l'écran verrouillé montre des notifications (maquette l3-notifs-verrou). */
@Composable
fun PageNotifsVerrou(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val cr = c.contentResolver
    val montrer = remember(version) { Settings.Secure.getInt(cr, "lock_screen_show_notifications", 1) == 1 }
    val prive = remember(version) { Settings.Secure.getInt(cr, "lock_screen_allow_private_notifications", 1) == 1 }
    fun regler(m: Boolean, pv: Boolean) {
        Settings.Secure.putInt(cr, "lock_screen_show_notifications", if (m) 1 else 0)
        Settings.Secure.putInt(cr, "lock_screen_allow_private_notifications", if (pv) 1 else 0)
        version++
    }
    PageReglages(titre = "Écran verrouillé", sousTitre = "Ce que montrent les notifications", retour = nav.retour) {
        section("Afficher", cle = "afficher") {
            Ligne("Tout le contenu", fin = Fin.Choix(montrer && prive)) { regler(true, true) }
            Ligne("Le nom de l'appli seulement", detail = "Le texte reste caché jusqu'au déverrouillage", fin = Fin.Choix(montrer && !prive)) { regler(true, false) }
            Ligne("Rien", fin = Fin.Choix(!montrer)) { regler(false, false) }
        }
        section(cle = "note") {
            Explication("Les codes reçus par SMS se lisent sur l'écran verrouillé quand tout le contenu s'affiche. « Le nom de l'appli seulement » les garde cachés, même si quelqu'un prend votre téléphone.")
            Explication("Les Espaces verrouillés ne montrent rien sur l'écran verrouillé.")
        }
    }
}

/** L'historique des notifications (maquette l3-historique-notifs). */
@Composable
fun PageHistoriqueNotifs(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var actif by remember { mutableStateOf(false) }
    var liste by remember { mutableStateOf(emptyList<NotifPassee>()) }
    LaunchedEffect(version) {
        actif = MoteurNotifs.historiqueActif(c)
        liste = withContext(Dispatchers.IO) { if (actif) MoteurNotifs.historique(c) else emptyList() }
    }
    PageReglages(titre = "Historique", sousTitre = "Les notifications des dernières 24 h", retour = nav.retour) {
        section(cle = "inter") {
            Ligne("Garder l'historique", icone = Icones.HORLOGE, fin = Fin.Inter(actif)) {
                MoteurNotifs.reglerHistorique(c, !actif)
                version++
            }
        }
        if (actif) {
            section("Fermées aujourd'hui", cle = "liste") {
                if (liste.isEmpty()) Explication("Rien pour l'instant. Les notifications fermées apparaîtront ici.")
                liste.take(60).forEach { n ->
                    Ligne(n.titre.ifBlank { MoteurApplis.nom(c, n.paquet) }, detail = n.texte.ifBlank { null }, image = MoteurApplis.icone(c, n.paquet, 32), fin = Fin.Valeur(heure(n.heure)))
                }
            }
        }
    }
}
