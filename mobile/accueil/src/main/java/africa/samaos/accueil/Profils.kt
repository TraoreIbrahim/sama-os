package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.time.LocalTime
import java.util.Locale

/**
 * Ce que Sama règle dans Android quand il est le système : les profils qui portent les Espaces,
 * les couleurs et le thème sombre des écrans d'Android.
 *
 * Ces fonctions du système ne sont pas dans le SDK public : on les appelle par réflexion, ce que permet
 * la configuration de l'émulateur Sama. Dans Sama OS compilé, l'Accueil sera signé avec la clé du système
 * et les appellera directement.
 */
object Profils {

    private const val GERER_PROFILS = "android.permission.MANAGE_USERS"

    /** Les réglages des Espaces (couleur, paysage), lisibles depuis chaque profil. */
    private const val CLE_ESPACES = "sama_espaces"

    /** Vrai quand l'Accueil est une appli du système avec le droit de gérer les profils. */
    fun disponibles(contexte: Context): Boolean =
        contexte.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            contexte.checkSelfPermission(GERER_PROFILS) == PackageManager.PERMISSION_GRANTED &&
            UserManager.supportsMultipleUsers()

    /** Le numéro du profil où tourne cette copie de l'Accueil (0 : le propriétaire, donc Maison). */
    fun monId(): Int = Process.myUid() / 100_000

    /** Le nombre de profils que le téléphone accepte. */
    fun maximum(contexte: Context): Int = try {
        UserManager::class.java.getMethod("getMaxSupportedUsers").invoke(null) as Int
    } catch (_: Exception) {
        4
    }

    /** Les profils du téléphone : numéro et nom. */
    fun liste(contexte: Context): List<Pair<Int, String>> = try {
        val um = contexte.getSystemService(UserManager::class.java)
        val infos = um.javaClass.getMethod("getUsers").invoke(um) as List<*>
        infos.filterNotNull().map { info ->
            val c = info.javaClass
            c.getField("id").getInt(info) to (c.getField("name").get(info) as? String).orEmpty()
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Crée un profil complet (pas un profil de travail rattaché) et rend son numéro. */
    fun creer(contexte: Context, nom: String): Int? = try {
        val um = contexte.getSystemService(UserManager::class.java)
        val info = um.javaClass.getMethod("createUser", String::class.java, Int::class.javaPrimitiveType).invoke(um, nom, 0)
        info?.javaClass?.getField("id")?.getInt(info)
    } catch (_: Exception) {
        null
    }

    /** Passe au profil [id] : Android demande son code s'il en a un. */
    fun basculer(contexte: Context, id: Int): Boolean = try {
        val am = contexte.getSystemService(ActivityManager::class.java)
        am.javaClass.getMethod("switchUser", UserHandle::class.java).invoke(am, UserHandle.getUserHandleForUid(id * 100_000)) as Boolean
    } catch (_: Exception) {
        false
    }

    fun renommer(contexte: Context, id: Int, nom: String): Boolean = try {
        val um = contexte.getSystemService(UserManager::class.java)
        um.javaClass.getMethod("setUserName", Int::class.javaPrimitiveType, String::class.java).invoke(um, id, nom)
        true
    } catch (_: Exception) {
        false
    }

    /** Supprime le profil [id] et tout ce qu'il contient. Jamais Maison, jamais le profil où l'on se trouve. */
    fun supprimer(contexte: Context, id: Int): Boolean {
        if (id == 0 || id == monId()) return false
        val fait = try {
            val um = contexte.getSystemService(UserManager::class.java)
            um.javaClass.getMethod("removeUser", Int::class.javaPrimitiveType).invoke(um, id) as Boolean
        } catch (_: Exception) {
            false
        }
        if (fait) {
            try {
                val cr = contexte.contentResolver
                val json = JSONObject(Settings.Global.getString(cr, CLE_ESPACES) ?: "{}")
                json.remove(id.toString())
                Settings.Global.putString(cr, CLE_ESPACES, json.toString())
            } catch (_: Exception) {
                // Les réglages orphelins ne gênent pas : ils seront écrasés par le prochain Espace de ce numéro.
            }
        }
        return fait
    }

    /** Ce qu'on retient d'un Espace, lisible depuis chaque profil. */
    data class FicheEspace(val teinte: CouleurEspace, val paysage: PaysageEspace, val verrou: VerrouEspace?)

    /** Couleur, paysage et verrou de chaque Espace, par numéro de profil. */
    fun lireEspaces(contexte: Context): Map<Int, FicheEspace> = try {
        val json = JSONObject(Settings.Global.getString(contexte.contentResolver, CLE_ESPACES) ?: "{}")
        json.keys().asSequence().mapNotNull { cle ->
            val e = json.getJSONObject(cle)
            val teinte = CouleurEspace.entries.firstOrNull { it.name == e.optString("couleur") } ?: return@mapNotNull null
            val paysage = PaysageEspace.entries.firstOrNull { it.name == e.optString("paysage") } ?: return@mapNotNull null
            val verrou = VerrouEspace.entries.firstOrNull { it.name == e.optString("verrou") }
            cle.toInt() to FicheEspace(teinte, paysage, verrou)
        }.toMap()
    } catch (_: Exception) {
        emptyMap()
    }

    /** Enregistre la couleur, le paysage et le verrou d'un Espace, et ses applis de départ à sa création. */
    fun ecrireEspace(
        contexte: Context,
        id: Int,
        teinte: CouleurEspace,
        paysage: PaysageEspace,
        verrou: VerrouEspace? = null,
        applis: Collection<String>? = null,
        offertes: Collection<String>? = null,
    ) {
        try {
            val cr = contexte.contentResolver
            val json = JSONObject(Settings.Global.getString(cr, CLE_ESPACES) ?: "{}")
            val e = json.optJSONObject(id.toString()) ?: JSONObject()
            e.put("couleur", teinte.name).put("paysage", paysage.name)
            if (verrou != null) e.put("verrou", verrou.name)
            if (applis != null) e.put("applis", JSONArray(applis))
            if (offertes != null) e.put("offertes", JSONArray(offertes))
            json.put(id.toString(), e)
            Settings.Global.putString(cr, CLE_ESPACES, json.toString())
        } catch (_: Exception) {
            // Sans ce droit, l'Espace garde la couleur et le paysage par défaut.
        }
    }

    /**
     * Les applis choisies à la création de l'Espace [id], et celles qui étaient proposées, tant qu'elles
     * n'ont pas été posées. Seules les proposées sont touchées : le reste, Android le règle lui-même.
     */
    fun applisDeDepart(contexte: Context, id: Int): Pair<Set<String>, Set<String>>? = try {
        val json = JSONObject(Settings.Global.getString(contexte.contentResolver, CLE_ESPACES) ?: "{}")
        val e = json.optJSONObject(id.toString())
        fun liste(cle: String) = e?.optJSONArray(cle)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        val choisies = liste("applis")
        if (choisies == null) null else choisies to (liste("offertes") ?: choisies)
    } catch (_: Exception) {
        null
    }

    /**
     * Les applis qui restent dans tout Espace : les appels et les SMS doivent toujours pouvoir arriver,
     * et les Paramètres servent à choisir le code. L'Accueil lui-même n'est jamais dans la liste.
     */
    fun essentielles(contexte: Context): Set<String> = buildSet {
        add("com.android.settings")
        add(REGLAGES_SAMA)
        contexte.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { add(it) }
        Telephony.Sms.getDefaultSmsPackage(contexte)?.let { add(it) }
    }

    /**
     * Ce qu'on ne propose jamais de masquer : les claviers (sans eux, plus moyen d'écrire dans l'Espace)
     * et l'Accueil lui-même.
     */
    fun intouchables(contexte: Context): Set<String> = buildSet {
        add(contexte.packageName)
        add(REGLAGES_SAMA)
        contexte.getSystemService(InputMethodManager::class.java)?.inputMethodList?.forEach { add(it.packageName) }
    }

    /** Les applis qu'on peut choisir pour un Espace : toutes, sauf les intouchables. */
    fun aChoisir(contexte: Context, applis: List<Appli>): List<Appli> {
        val exclues = intouchables(contexte)
        return applis.filter { it.paquet !in exclues }
    }

    /**
     * Les applis qu'on peut choisir dans ce profil : celles qu'Android y montre, celles que Sama y a masquées,
     * et les applis ordinaires installées dans un autre Espace (qu'on peut copier ici). Avec leur état ici.
     * Ce qu'Android écarte lui-même (alertes d'urgence, outils SIM…) n'est jamais proposé.
     */
    fun applisDuProfil(contexte: Context): List<Pair<Appli, Boolean>> {
        val pm = contexte.packageManager
        val taille = (60 * contexte.resources.displayMetrics.density).toInt()
        val lanceur = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val demarrage = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        val exclues = intouchables(contexte)
        val masqueesParSama = masquees(contexte)
        val visibles = pm.queryIntentActivities(lanceur, demarrage)
            .filter { it.activityInfo.packageName !in exclues }
        val dejaVues = visibles.map { it.activityInfo.packageName }.toSet()
        val absentes = pm.queryIntentActivities(
            lanceur,
            demarrage or PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES,
        ).filter { ri ->
            val info = ri.activityInfo.applicationInfo
            val paquet = info.packageName
            val installee = info.flags and ApplicationInfo.FLAG_INSTALLED != 0
            val duSysteme = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
            paquet !in exclues && paquet !in dejaVues && (paquet in masqueesParSama || (!installee && !duSysteme))
        }
        val tri = Collator.getInstance(Locale.FRENCH)
        fun versAppli(ri: ResolveInfo) = Appli(
            nom = ri.loadLabel(pm).toString(),
            paquet = ri.activityInfo.packageName,
            activite = ri.activityInfo.name,
            icone = ri.loadIcon(pm).toBitmap(taille, taille).asImageBitmap(),
        )
        return (visibles.map { versAppli(it) to true } + absentes.map { versAppli(it) to false })
            .distinctBy { it.first.paquet }
            .sortedWith { a, b -> tri.compare(a.first.nom, b.first.nom) }
    }

    /**
     * Pose les applis de ce profil : celles de [choisies] y sont (copiées depuis le téléphone si elles
     * n'y étaient pas encore), les autres sont masquées, sauf les [essentielles]. Rien n'est désinstallé
     * du téléphone : une appli masquée se rajoute d'une touche dans les réglages de l'Espace.
     */
    fun reglerApplis(contexte: Context, choisies: Set<String>, essentielles: Set<String>, seulement: Set<String>? = null) {
        val pm = contexte.packageManager
        val masqueesParSama = masquees(contexte).toMutableSet()
        applisDuProfil(contexte).forEach { (a, presente) ->
            if (seulement != null && a.paquet !in seulement) return@forEach
            val voulue = a.paquet in choisies || a.paquet in essentielles
            try {
                if (voulue && !presente) {
                    // Copier une appli déjà sur le téléphone dans cet Espace : rien n'est téléchargé.
                    pm.javaClass.getMethod("installExistingPackage", String::class.java).invoke(pm, a.paquet)
                    pm.setApplicationEnabledSetting(a.paquet, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0)
                    masqueesParSama -= a.paquet
                } else if (!voulue && presente) {
                    pm.setApplicationEnabledSetting(a.paquet, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0)
                    masqueesParSama += a.paquet
                }
            } catch (_: Exception) {
                // Une appli qu'Android refuse de masquer ou d'ajouter reste comme elle est.
            }
        }
        reglages(contexte, "espaces").edit().putStringSet(CLE_MASQUEES, masqueesParSama).apply()
    }

    /**
     * Les applis que Sama a masquées dans ce profil. Android en désactive aussi certaines lui-même
     * (alertes d'urgence, outils SIM…) : celles-là, on ne les propose jamais.
     */
    private fun masquees(contexte: Context): Set<String> =
        reglages(contexte, "espaces").getStringSet(CLE_MASQUEES, emptySet()).orEmpty()

    private const val CLE_MASQUEES = "applis_masquees"

    /** Une fois les applis de départ posées dans le profil, on les retire des réglages partagés. */
    fun applisDeDepartPosees(contexte: Context, id: Int) {
        try {
            val cr = contexte.contentResolver
            val json = JSONObject(Settings.Global.getString(cr, CLE_ESPACES) ?: "{}")
            json.optJSONObject(id.toString())?.apply {
                remove("applis")
                remove("offertes")
            }
            Settings.Global.putString(cr, CLE_ESPACES, json.toString())
        } catch (_: Exception) {
            // Sans ce droit, on les reposerait au prochain démarrage : sans effet de plus.
        }
    }

    /**
     * « Vers un Espace » : depuis Maison, on demande qu'une appli du téléphone soit ajoutée à l'Espace [id].
     * Android ne laisse pas Maison toucher aux applis d'un autre profil : l'Accueil de cet Espace la posera
     * lui-même à la prochaine entrée. Rien n'est téléchargé, l'appli est déjà sur le téléphone.
     */
    fun envoyerVers(contexte: Context, id: Int, paquet: String): Boolean = try {
        val cr = contexte.contentResolver
        val json = JSONObject(Settings.Global.getString(cr, CLE_ESPACES) ?: "{}")
        val e = json.optJSONObject(id.toString()) ?: JSONObject()
        val envoyees = e.optJSONArray("envoyees") ?: JSONArray()
        if ((0 until envoyees.length()).none { envoyees.getString(it) == paquet }) envoyees.put(paquet)
        e.put("envoyees", envoyees)
        json.put(id.toString(), e)
        Settings.Global.putString(cr, CLE_ESPACES, json.toString())
        true
    } catch (_: Exception) {
        false
    }

    /** Pose dans ce profil les applis envoyées depuis Maison, puis efface la demande. */
    fun poserApplisEnvoyees(contexte: Context, id: Int): Boolean {
        val cr = contexte.contentResolver
        val json = try {
            JSONObject(Settings.Global.getString(cr, CLE_ESPACES) ?: "{}")
        } catch (_: Exception) {
            return false
        }
        val e = json.optJSONObject(id.toString()) ?: return false
        val envoyees = e.optJSONArray("envoyees") ?: return false
        val paquets = (0 until envoyees.length()).map { envoyees.getString(it) }.toSet()
        // Seules les applis ordinaires ou masquées par Sama sont proposées ici : on ne touche à rien d'autre.
        val presentes = applisDuProfil(contexte).filter { it.second }.map { it.first.paquet }.toSet()
        reglerApplis(contexte, presentes + paquets, essentielles(contexte), seulement = paquets)
        e.remove("envoyees")
        return try {
            Settings.Global.putString(cr, CLE_ESPACES, json.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Les écrans d'Android (Paramètres, verrouillage, clavier du code) tirent leurs couleurs d'une teinte
     * de départ : on leur donne la latérite de Banco plutôt que la couleur du fond d'écran.
     */
    fun couleursAndroid(contexte: Context): Boolean = try {
        run {
            val valeur = JSONObject()
                .put("android.theme.customization.color_source", "preset")
                .put("android.theme.customization.system_palette", "B5532F")
                .put("android.theme.customization.accent_color", "B5532F")
                .put("android.theme.customization.color_index", "-1")
                .put("android.theme.customization.theme_style", "TONAL_SPOT")
                .put("_applied_timestamp", System.currentTimeMillis())
            Settings.Secure.putString(contexte.contentResolver, "theme_customization_overlay_packages", valeur.toString())
        }
    } catch (_: Exception) {
        // Sans ce droit, Android garde les couleurs tirées du fond d'écran.
        false
    }

    /** Le thème sombre d'Android suit la Nuit de Banco : au coucher, de 19 h à 6 h. */
    fun nuitAuCoucher(contexte: Context): Boolean {
        if (contexte.checkSelfPermission("android.permission.MODIFY_DAY_NIGHT_MODE") != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val um = contexte.getSystemService(UiModeManager::class.java)
            um.setNightMode(UiModeManager.MODE_NIGHT_CUSTOM)
            um.javaClass.getMethod("setCustomNightModeStart", LocalTime::class.java).invoke(um, LocalTime.of(19, 0))
            um.javaClass.getMethod("setCustomNightModeEnd", LocalTime::class.java).invoke(um, LocalTime.of(6, 0))
            um.nightMode == UiModeManager.MODE_NIGHT_CUSTOM
        } catch (_: Exception) {
            // Sans ce droit, l'Accueil garde sa propre Nuit et Android la sienne.
            false
        }
    }

    /** La tuile Nuit du Pouls : tout le téléphone passe en Nuit ou en jour, jusqu'au prochain coucher ou lever. */
    fun basculerNuit(contexte: Context, nuit: Boolean) {
        try {
            val um = contexte.getSystemService(UiModeManager::class.java)
            um.javaClass.getMethod("setNightModeActivated", Boolean::class.javaPrimitiveType).invoke(um, nuit)
        } catch (_: Exception) {
            // Sans ce droit, rien ne change.
        }
    }

    /** La navigation par gestes, réglée profil par profil par Android. */
    fun navigationParGestes(contexte: Context) {
        try {
            val om = contexte.getSystemService("overlay") ?: return
            om.javaClass.getMethod("setEnabledExclusiveInCategory", String::class.java, UserHandle::class.java)
                .invoke(om, "com.android.internal.systemui.navbar.gestural", Process.myUserHandle())
        } catch (_: Exception) {
            // Pas le droit ou pas cette surcouche : on garde la navigation actuelle.
        }
    }
}
