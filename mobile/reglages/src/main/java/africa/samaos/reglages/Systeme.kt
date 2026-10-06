package africa.samaos.reglages

import android.accounts.AccountManager
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.inputmethod.InputMethodManager
import android.view.autofill.AutofillManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

object MoteurSysteme {
    /** Les langues proposées : celles des usagers de Sama d'abord. */
    val LANGUES = listOf(
        "fr-CI" to "Français (Côte d'Ivoire)",
        "fr-FR" to "Français (France)",
        "en-GB" to "English",
        "dyu" to "Julakan",
        "ar" to "العربية",
        "pt-PT" to "Português",
        "es-ES" to "Español",
    )

    fun langues(): List<Locale> {
        val l = LocaleList.getDefault()
        return (0 until l.size()).map { l[it] }
    }

    fun nomLangue(l: Locale) = LANGUES.firstOrNull { Locale.forLanguageTag(it.first).language == l.language && (Locale.forLanguageTag(it.first).country.isEmpty() || it.first.endsWith(l.country)) }?.second
        ?: l.getDisplayName(l).replaceFirstChar { it.uppercase() }

    /** Changer les langues du téléphone (droit CHANGE_CONFIGURATION, comme les Paramètres). */
    fun reglerLangues(liste: List<Locale>) = try {
        Class.forName("com.android.internal.app.LocalePicker").getMethod("updateLocales", LocaleList::class.java)
            .invoke(null, LocaleList(*liste.toTypedArray()))
        true
    } catch (_: Exception) {
        false
    }

    fun claviers(c: Context) = c.getSystemService(InputMethodManager::class.java).enabledInputMethodList

    fun clavierParDefaut(c: Context) = Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)

    fun reglerClavier(c: Context, id: String) = Settings.Secure.putString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, id)

    /** La navigation : gestes (true) ou trois boutons, par les surcouches d'Android. */
    fun gestes(c: Context): Boolean = try {
        val r = c.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        c.resources.getInteger(r) == 2
    } catch (_: Exception) {
        true
    }

    fun reglerGestes(c: Context, oui: Boolean) = try {
        val om = c.getSystemService(Class.forName("android.content.om.OverlayManager"))
        val paquet = if (oui) "com.android.internal.systemui.navbar.gestural" else "com.android.internal.systemui.navbar.threebutton"
        om.javaClass.getMethod("setEnabledExclusiveInCategory", String::class.java, android.os.UserHandle::class.java)
            .invoke(om, paquet, android.os.Process.myUserHandle())
        true
    } catch (_: Exception) {
        false
    }

    fun heureAuto(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.AUTO_TIME, 1) == 1
    fun fuseauAuto(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.AUTO_TIME_ZONE, 1) == 1
    fun reglerHeureAuto(c: Context, oui: Boolean) = Settings.Global.putInt(c.contentResolver, Settings.Global.AUTO_TIME, if (oui) 1 else 0)
    fun reglerFuseauAuto(c: Context, oui: Boolean) = Settings.Global.putInt(c.contentResolver, Settings.Global.AUTO_TIME_ZONE, if (oui) 1 else 0)

    fun format24(c: Context) = Settings.System.getString(c.contentResolver, Settings.System.TIME_12_24) != "12"
    fun reglerFormat24(c: Context, oui: Boolean) = Settings.System.putString(c.contentResolver, Settings.System.TIME_12_24, if (oui) "24" else "12")

    val FUSEAUX = listOf(
        "Africa/Abidjan" to "Abidjan, Dakar, Bamako (GMT+0)",
        "Africa/Lagos" to "Lagos, Douala, Kinshasa (GMT+1)",
        "Africa/Johannesburg" to "Johannesburg, Lubumbashi (GMT+2)",
        "Africa/Nairobi" to "Nairobi, Addis-Abeba (GMT+3)",
        "Europe/Paris" to "Paris, Bruxelles",
        "Europe/London" to "Londres",
        "America/New_York" to "New York",
    )

    fun reglerFuseau(c: Context, id: String) = try {
        c.getSystemService(AlarmManager::class.java).setTimeZone(id)
        true
    } catch (_: Exception) {
        false
    }

    fun nomAppareil(c: Context) = Settings.Global.getString(c.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL

    fun renommerAppareil(c: Context, nom: String) = Settings.Global.putString(c.contentResolver, Settings.Global.DEVICE_NAME, nom)

    fun ram(c: Context): Long = ActivityManager.MemoryInfo().also { c.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem

    /** « 35 •• 4821 » : l'IMEI reconnaissable sans s'afficher en entier (droit READ_PRIVILEGED_PHONE_STATE). */
    fun imei(c: Context, fente: Int): String? = try {
        val v = c.getSystemService(TelephonyManager::class.java).getImei(fente) ?: return null
        "${v.take(2)} •• ${v.takeLast(4)}"
    } catch (_: Exception) {
        null
    }

    fun versionSama(c: Context): String = try {
        c.packageManager.getPackageInfo("africa.samaos.accueil", 0).versionName ?: "0.1"
    } catch (_: Exception) {
        "0.1"
    }
}

/** Système : langues, clavier, gestes, date et heure, mises à jour, à propos. */
@Composable
fun PageSysteme(nav: Nav) {
    val c = LocalContext.current
    PageReglages(titre = "Système", retour = nav.retour) {
        section(cle = "liste") {
            Ligne("Langues et région", detail = MoteurSysteme.langues().joinToString(", ") { MoteurSysteme.nomLangue(it) }, icone = Icones.LANGUE) { nav.aller(Page.Langues) }
            Ligne("Clavier", icone = Icones.CLAVIER) { nav.aller(Page.Clavier) }
            Ligne("Gestes", detail = if (MoteurSysteme.gestes(c)) "Gestes de la Natte" else "Trois boutons", icone = Icones.APPLI) { nav.aller(Page.Gestes) }
            Ligne("Date et heure", detail = TimeZone.getDefault().id, icone = Icones.HORLOGE) { nav.aller(Page.DateHeure) }
            Ligne("Mises à jour", detail = "Sama ${MoteurSysteme.versionSama(c)}", icone = Icones.MISE_A_JOUR) { nav.aller(Page.MajSysteme) }
            Ligne("À propos du téléphone", detail = MoteurSysteme.nomAppareil(c), icone = Icones.INFO) { nav.aller(Page.APropos) }
        }
        section(cle = "reinit") {
            Ligne("Réinitialiser", detail = "Les réseaux, un Espace, ou tout le téléphone", icone = Icones.CORBEILLE) { nav.aller(Page.Reinitialiser) }
            Explication("Personne n'a besoin que vous effaciez votre téléphone pour vous aider. Un « agent » qui vous le demande cherche à prendre votre compte.", LocalBanco.current.lateriteTexte)
        }
    }
}

/** Les langues et la région (maquette l3-langues). */
@Composable
fun PageLangues(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var ajout by remember { mutableStateOf(false) }
    PageReglages(titre = "Langues", retour = nav.retour) {
        val v = version
        val langues = if (v >= 0) MoteurSysteme.langues() else emptyList()
        section("Langues du système", cle = "langues") {
            langues.forEachIndexed { i, l ->
                Ligne(
                    MoteurSysteme.nomLangue(l),
                    detail = if (i == 0) "Langue principale" else "Pour les applis qui la proposent",
                    fin = if (i == 0) Fin.Rien else Fin.Valeur("Principale"),
                ) {
                    if (i > 0) {
                        MoteurSysteme.reglerLangues(listOf(l) + langues.filterIndexed { j, _ -> j != i })
                        version++
                    }
                }
            }
            Ligne("Ajouter une langue", icone = Icones.PLUS, fin = Fin.Rien) { ajout = !ajout }
            if (ajout) {
                MoteurSysteme.LANGUES.filter { (tag, _) -> langues.none { it.toLanguageTag() == tag } }.forEach { (tag, nom) ->
                    Ligne(nom, fin = Fin.Rien) {
                        MoteurSysteme.reglerLangues(langues + Locale.forLanguageTag(tag))
                        ajout = false
                        version++
                    }
                }
            }
            Explication("Sama est en français. Les applis qui parlent julakan ou d'autres langues les utiliseront.")
        }
        section("Région", cle = "region") {
            val l = langues.firstOrNull() ?: Locale.FRENCH
            Ligne("Pays", detail = l.getDisplayCountry(Locale.FRENCH).ifBlank { "—" }, fin = Fin.Rien)
            Ligne("Nombres", detail = NumberFormat.getNumberInstance(l).format(12500.50), fin = Fin.Rien)
            Ligne("Monnaie", detail = if (l.country == "CI" || l.country == "SN" || l.country == "ML" || l.country == "BF") "Franc CFA (F)" else runCatching { java.util.Currency.getInstance(l).displayName }.getOrDefault("—"), fin = Fin.Rien)
        }
    }
}

/** Le clavier (maquette l3-clavier). */
@Composable
fun PageClavier(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    PageReglages(titre = "Clavier", retour = nav.retour) {
        val v = version
        val defaut = if (v >= 0) MoteurSysteme.clavierParDefaut(c) else null
        section("Claviers", cle = "claviers") {
            MoteurSysteme.claviers(c).forEach { im ->
                Ligne(im.loadLabel(c.packageManager).toString(), image = MoteurApplis.icone(c, im.packageName, 32), fin = Fin.Choix(im.id == defaut)) {
                    MoteurSysteme.reglerClavier(c, im.id)
                    version++
                }
            }
        }
        section(cle = "plus") {
            Ligne("Langues et réglages du clavier", icone = Icones.CLAVIER) { nav.android(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
    }
}

/** Les gestes (maquette l3-gestes). */
@Composable
fun PageGestes(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    PageReglages(titre = "Gestes", retour = nav.retour) {
        val v = version
        val gestes = v >= 0 && MoteurSysteme.gestes(c)
        section("Navigation", cle = "navigation") {
            Ligne("Gestes de la Natte", detail = "Glisser depuis le bas", fin = Fin.Choix(gestes)) {
                MoteurSysteme.reglerGestes(c, true)
                version++
            }
            Ligne("Trois boutons", detail = "Retour, accueil, applis récentes", fin = Fin.Choix(!gestes)) {
                MoteurSysteme.reglerGestes(c, false)
                version++
            }
        }
        section("Raccourcis", cle = "raccourcis") {
            val trois = v >= 0 && Settings.Secure.getInt(c.contentResolver, "sama_trois_doigts", 1) == 1
            Ligne("Trois doigts pour changer d'Espace", icone = Icones.VERS_ESPACE, fin = Fin.Inter(trois)) {
                Settings.Secure.putInt(c.contentResolver, "sama_trois_doigts", if (trois) 0 else 1)
                version++
            }
            val camera = v >= 0 && Settings.Secure.getInt(c.contentResolver, "camera_double_tap_power_gesture_disabled", 0) == 0
            Ligne("Deux appuis sur le bouton pour la caméra", icone = Icones.APPAREIL, fin = Fin.Inter(camera)) {
                Settings.Secure.putInt(c.contentResolver, "camera_double_tap_power_gesture_disabled", if (camera) 1 else 0)
                version++
            }
        }
    }
}

/** La date et l'heure (maquette l3-date-heure). */
@Composable
fun PageDateHeure(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var fuseaux by remember { mutableStateOf(false) }
    val maintenant = ZonedDateTime.now()
    PageReglages(
        titre = maintenant.format(DateTimeFormatter.ofPattern(if (MoteurSysteme.format24(c)) "HH:mm" else "h:mm a", Locale.FRENCH)),
        sousTitre = maintenant.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)).replaceFirstChar { it.uppercase() },
        retour = nav.retour,
    ) {
        val v = version
        section(cle = "auto") {
            val auto = v >= 0 && MoteurSysteme.heureAuto(c)
            Ligne("Régler automatiquement", detail = "Par le réseau", fin = Fin.Inter(auto)) {
                MoteurSysteme.reglerHeureAuto(c, !auto)
                version++
            }
            val fAuto = v >= 0 && MoteurSysteme.fuseauAuto(c)
            Ligne("Fuseau horaire automatique", fin = Fin.Inter(fAuto)) {
                MoteurSysteme.reglerFuseauAuto(c, !fAuto)
                version++
            }
            val id = TimeZone.getDefault().id
            Ligne("Fuseau horaire", detail = MoteurSysteme.FUSEAUX.firstOrNull { it.first == id }?.second ?: id, icone = Icones.GLOBE, actif = !fAuto) {
                fuseaux = !fuseaux
            }
            if (fuseaux && !fAuto) {
                MoteurSysteme.FUSEAUX.forEach { (zid, nom) ->
                    Ligne(nom, fin = Fin.Choix(zid == id)) {
                        MoteurSysteme.reglerFuseau(c, zid)
                        fuseaux = false
                        version++
                    }
                }
            }
        }
        section("Format", cle = "format") {
            val h24 = v >= 0 && MoteurSysteme.format24(c)
            Ligne("Format 24 heures", fin = Fin.Inter(h24)) {
                MoteurSysteme.reglerFormat24(c, !h24)
                version++
            }
        }
    }
}

/** Les mises à jour du système (maquette l3-maj-systeme). */
@Composable
fun PageMajSysteme(nav: Nav) {
    val c = LocalContext.current
    PageReglages(titre = "Sama ${MoteurSysteme.versionSama(c)}", sousTitre = "À jour", retour = nav.retour) {
        section(cle = "etat") {
            Ligne("Android ${Build.VERSION.RELEASE}", detail = "Sécurité du ${MoteurSecurite2.correctif()}", icone = Icones.MISE_A_JOUR, fin = Fin.Rien)
            Explication("Les mises à jour de Sama arriveront ici avec leurs nouveautés. Elles s'installent la nuit, en Wi-Fi, quand la batterie le permet.")
        }
    }
}

/** À propos du téléphone (maquette l3-a-propos). */
@Composable
fun PageAPropos(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var renommer by remember { mutableStateOf(false) }
    var nom by remember { mutableStateOf(MoteurSysteme.nomAppareil(c)) }
    var numeros by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        numeros = withContext(Dispatchers.IO) { MoteurReseau.sims(c).mapNotNull { MoteurReseau.numero(c, it) }.joinToString(" · ") }
    }
    PageReglages(titre = if (version >= 0) MoteurSysteme.nomAppareil(c) else "", retour = nav.retour) {
        section(cle = "nom") {
            if (renommer) {
                Champ(nom, "Nom de l'appareil", { nom = it.take(40) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BoutonTexte("Annuler") { renommer = false }
                    BoutonTexte("Garder") {
                        if (nom.isNotBlank()) MoteurSysteme.renommerAppareil(c, nom.trim())
                        renommer = false
                        version++
                    }
                }
            } else {
                Ligne("Nom de l'appareil", detail = "Ce que voient le Bluetooth et le point d'accès", fin = Fin.Valeur("Modifier")) { renommer = true }
            }
        }
        section(cle = "infos") {
            Ligne("Version de Sama", detail = "Sama ${MoteurSysteme.versionSama(c)} · Android ${Build.VERSION.RELEASE} · sécurité du ${MoteurSecurite2.correctif()}", fin = Fin.Rien)
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            Ligne("Mémoire", detail = "${taille(stat.totalBytes)} de stockage, ${taille(MoteurSysteme.ram(c))} de RAM", fin = Fin.Rien)
            if (numeros.isNotBlank()) Ligne("Numéros", detail = numeros, fin = Fin.Rien)
            val imeis = (0 until 2).mapNotNull { f -> MoteurSysteme.imei(c, f)?.let { "SIM ${f + 1} : $it" } }
            if (imeis.isNotEmpty()) Ligne("IMEI", detail = imeis.joinToString(" · "), fin = Fin.Rien)
            Ligne("Modèle", detail = "${Build.MANUFACTURER} ${Build.MODEL}", fin = Fin.Rien)
        }
        section(cle = "legal") {
            Ligne("Informations légales", icone = Icones.DOCUMENT) { nav.android(Intent("android.settings.LICENSE")) }
            Explication("Votre IMEI identifie le téléphone. Ne le donnez qu'à votre opérateur ou à la police, en cas de vol.")
        }
    }
}

/** Mots de passe et comptes : le compte Sama, les comptes de cet Espace, le remplissage automatique, la sauvegarde. */
@Composable
fun PageComptes(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var comptes by remember { mutableStateOf(emptyList<android.accounts.Account>()) }
    LaunchedEffect(version, reprise) { comptes = withContext(Dispatchers.IO) { AccountManager.get(c).accounts.toList() } }
    PageReglages(titre = "Comptes", sousTitre = "Chaque Espace garde ses comptes", retour = nav.retour) {
        val v = version
        section(cle = "sama") {
            Ligne("Compte Sama", detail = "Pas encore ouvert", icone = Icones.PERSONNE) { nav.aller(Page.CompteSama) }
        }
        section("Espace ${EspaceActif.nom(c)}", cle = "comptes") {
            if (comptes.isEmpty()) Explication("Aucun compte dans cet Espace.")
            comptes.forEach { a -> Ligne(a.name, detail = a.type, icone = Icones.PERSONNE, fin = Fin.Rien) }
            Ligne("Ajouter un compte", icone = Icones.PLUS) { nav.android(Intent(Settings.ACTION_ADD_ACCOUNT)) }
            val sync = v >= 0 && ContentResolver.getMasterSyncAutomatically()
            Ligne("Synchroniser automatiquement", detail = "Les comptes se mettent à jour seuls", fin = Fin.Inter(sync)) {
                ContentResolver.setMasterSyncAutomatically(!sync)
                version++
            }
        }
        section(cle = "plus") {
            Ligne("Mots de passe", detail = "Le remplissage automatique", icone = Icones.CLE) { nav.aller(Page.MotsDePasse) }
            Ligne("Sauvegarde", icone = Icones.NUAGE) { nav.aller(Page.Sauvegarde) }
        }
    }
}

/** Le compte Sama (maquette l3-compte-sama) : il n'existe pas encore. */
@Composable
fun PageCompteSama(nav: Nav) {
    PageReglages(titre = "Compte Sama", sousTitre = "Pas encore ouvert", retour = nav.retour) {
        section(cle = "explication") {
            Explication("Le compte Sama gardera vos photos et vos réglages dans Sama Grenier, vos mots de passe dans le trousseau, et vous retrouverez vos Espaces sur un autre téléphone.")
            Explication("Il ouvrira avec Sama Grenier. D'ici là, tout reste sur ce téléphone.")
        }
    }
}

/** Les mots de passe (maquette l3-mots-de-passe) : le service qui remplit les formulaires. */
@Composable
fun PageMotsDePasse(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val services = remember { c.packageManager.queryIntentServices(Intent("android.service.autofill.AutofillService"), 0) }
    PageReglages(titre = "Mots de passe", sousTitre = "Remplir les formulaires tout seul", retour = nav.retour) {
        val v = version
        val actuel = if (v >= 0) Settings.Secure.getString(c.contentResolver, "autofill_service") else null
        section("Service de remplissage", cle = "service") {
            Ligne("Aucun", fin = Fin.Choix(actuel.isNullOrBlank())) {
                Settings.Secure.putString(c.contentResolver, "autofill_service", "")
                version++
            }
            services.forEach { s ->
                val id = "${s.serviceInfo.packageName}/${s.serviceInfo.name}"
                Ligne(s.loadLabel(c.packageManager).toString(), image = MoteurApplis.icone(c, s.serviceInfo.packageName, 32), fin = Fin.Choix(actuel == id)) {
                    Settings.Secure.putString(c.contentResolver, "autofill_service", id)
                    version++
                }
            }
        }
        section(cle = "note") {
            Explication("Le trousseau du compte Sama arrivera avec le compte. Un service de remplissage voit ce que vous tapez dans les formulaires : choisissez-le avec soin.")
        }
    }
}

/** La sauvegarde (maquette l3-sauvegarde). */
@Composable
fun PageSauvegarde(nav: Nav) {
    PageReglages(titre = "Sauvegarde", sousTitre = "Pas encore de sauvegarde", retour = nav.retour) {
        section(cle = "explication") {
            Explication("Avec le compte Sama, photos, SMS, contacts, applis et Espaces partiront chaque nuit dans Sama Grenier, en Wi-Fi seulement.")
        }
    }
}
