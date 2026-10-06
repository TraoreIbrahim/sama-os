package africa.samaos.reglages

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

/** Une entrée de l'accueil des Réglages : où elle mène, et les mots qui la font trouver. */
class Entree(val titre: String, val icone: String, val mots: String, val page: Page?, val intent: Intent? = null)

/** Ce que l'accueil des Réglages résume sous chaque entrée, lu hors du fil de l'écran. */
private class Resumes(
    val reseau: String = "",
    val appareils: String = "",
    val applis: String = "",
    val notifications: String = "",
    val batterie: String = "",
    val stockage: String = "",
    val son: String = "",
    val affichage: String = "",
    val paysage: String = "",
    val espaces: String = "",
    val securite: String = "",
    val bienEtre: String = "",
    val accessibilite: String = "",
    val urgence: String = "",
    val bouclier: String = "",
    val soldes: String = "",
    val proches: String = "",
)

private fun lireResumes(c: Context): Resumes {
    val wifi = MoteurWifi.ssidConnecte(c)
    val sims = MoteurReseau.sims(c)
    val reseau = listOfNotNull(
        if (MoteurReseau.avion(c)) "Mode avion" else wifi,
        when (sims.size) {
            0 -> null
            1 -> MoteurReseau.nomSim(sims[0])
            else -> sims.joinToString(" et ") { "SIM ${it.simSlotIndex + 1}" }
        },
    ).joinToString(" · ").ifBlank { "Pas de réseau" }
    val bt = c.getSystemService(BluetoothManager::class.java)?.adapter
    val appareils = when {
        bt == null -> "Pas de Bluetooth"
        !bt.isEnabled -> "Bluetooth désactivé"
        else -> "Bluetooth activé"
    }
    val lanceur = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val nbApplis = c.packageManager.queryIntentActivities(lanceur, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet().size
    val nm = c.getSystemService(NotificationManager::class.java)
    val notifications = if (nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) "Ne pas déranger activé" else "Toutes les notifications"
    val bm = c.getSystemService(BatteryManager::class.java)
    val niveau = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val batterie = "$niveau %" + if (bm.isCharging) " · en charge" else ""
    val stat = StatFs(Environment.getDataDirectory().path)
    val stockage = String.format(Locale.FRENCH, "%.1f Go libres", stat.availableBytes / 1e9)
    val am = c.getSystemService(AudioManager::class.java)
    val son = when (am.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> "Silencieux"
        AudioManager.RINGER_MODE_VIBRATE -> "Vibreur"
        else -> "Sonnerie"
    }
    val auto = Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1
    val sombre = (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val affichage = (if (sombre) "Thème Nuit" else "Thème Aube") + if (auto) ", luminosité auto" else ""
    val securite = if (MoteurSecurite.codeEnPlace(c)) "Tout va bien" else "Pas de code de verrouillage"
    return Resumes(
        reseau = reseau,
        appareils = appareils,
        applis = "$nbApplis dans ${EspaceActif.nom(c)}",
        notifications = notifications,
        batterie = batterie,
        stockage = stockage,
        son = son,
        affichage = affichage,
        paysage = EspaceActif.paysage(c).nom,
        espaces = EspaceActif.tous(c).joinToString(", "),
        securite = securite,
        bienEtre = duree(MoteurBienEtre.aujourdhui(c).values.sum()) + " aujourd'hui",
        accessibilite = MoteurAccessibilite.resume(c),
        urgence = MoteurUrgence.resume(c),
        bouclier = MoteurBouclier.resume(c),
        soldes = MoteurSoldes.resume(c),
        proches = MoteurProches.resume(c),
    )
}

/** Toutes les entrées des Réglages, pour l'accueil et la recherche. */
fun entrees(): List<Entree> = listOf(
    Entree("Réseau et data", Icones.WIFI, "reseau data wifi internet sim mobile avion point acces vpn dns", Page.Reseau),
    Entree("Soldes et forfaits", Icones.DONNEES, "soldes solde forfait credit data internet pass recharge mobile money", Page.Soldes),
    Entree("Proche en proche", Icones.PROXIMITE, "proche en proche partage sans data point sama applis mises a jour hors ligne wifi direct", Page.Proches),
    Entree("Appareils connectés", Icones.BLUETOOTH, "appareils bluetooth ecouteurs enceinte usb", Page.Appareils),
    Entree("Applications", Icones.APPLI, "applications applis autorisations defaut desinstaller", Page.Applis),
    Entree("Notifications", Icones.CLOCHE, "notifications ne pas deranger alertes historique", Page.Notifications),
    Entree("Batterie", Icones.BATTERIE, "batterie economiseur charge autonomie", Page.Batterie),
    Entree("Stockage", Icones.DOSSIER, "stockage memoire espace libre carte sd", Page.Stockage),
    Entree("Son et vibrations", Icones.MUSIQUE, "son volume sonnerie vibreur silencieux", Page.Son),
    Entree("Affichage", Icones.SOLEIL, "affichage luminosite theme sombre nuit texte taille veille rotation plein soleil", Page.Affichage),
    Entree("Fond d'écran et style", Icones.PAYSAGE, "fond ecran style paysage couleur verrouille", Page.FondEcran),
    Entree("Espaces", Icones.CADENAS, "espaces profils maison travail", null, Intent("africa.samaos.action.REGLAGES_ESPACES")),
    Entree("Sécurité", Icones.BOUCLIER, "securite code verrouillage empreinte autorisations confidentialite chiffrement sim", Page.Securite),
    Entree("Bouclier anti-arnaques", Icones.ALERTE, "bouclier arnaque arnaques escroquerie mobile money sms faux agent lien code secret", Page.Bouclier),
    Entree("Localisation", Icones.BOUSSOLE, "localisation position gps", Page.Localisation),
    Entree("Mots de passe et comptes", Icones.CLE, "mots de passe comptes saisie automatique compte sama", Page.Comptes),
    Entree("Bien-être", Icones.HORLOGE, "bien etre temps ecran minuteur concentration pause", Page.TempsEcran),
    Entree("Accessibilité", Icones.ACCESSIBILITE, "accessibilite loupe contraste couleurs texte mono appui", Page.Accessibilite),
    Entree("Urgence", Icones.APPEL, "urgence sos samu pompiers police fiche medicale contacts", Page.Urgence),
    Entree("Système", Icones.TELEPHONE, "systeme langue clavier gestes date heure mise a jour a propos", Page.Systeme),
)

fun simplifier(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/** L'accueil des Réglages (maquette l3-accueil) : le compte Sama, puis les familles de réglages. */
@Composable
fun PageAccueil(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var resumes by remember { mutableStateOf(Resumes()) }
    LaunchedEffect(reprise) { resumes = withContext(Dispatchers.IO) { lireResumes(c) } }
    var recherche by remember { mutableStateOf("") }
    fun detailDe(e: Entree): String? = when (e.page) {
        Page.Reseau -> resumes.reseau
        Page.Appareils -> resumes.appareils
        Page.Applis -> resumes.applis
        Page.Notifications -> resumes.notifications
        Page.Batterie -> resumes.batterie
        Page.Stockage -> resumes.stockage
        Page.Son -> resumes.son
        Page.Affichage -> resumes.affichage
        Page.FondEcran -> resumes.paysage
        Page.Securite -> resumes.securite
        Page.TempsEcran -> resumes.bienEtre
        Page.Accessibilite -> resumes.accessibilite
        Page.Urgence -> resumes.urgence
        Page.Bouclier -> resumes.bouclier
        Page.Soldes -> resumes.soldes
        Page.Proches -> resumes.proches
        null -> if (e.titre == "Espaces") resumes.espaces else null
        else -> null
    }?.ifBlank { null }
    fun ouvrir(e: Entree) {
        val p = e.page
        if (p != null) nav.aller(p) else e.intent?.let { i ->
            try {
                c.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }
    PageReglages(titre = "Réglages", retour = null) {
        section(cle = "recherche") {
            ChampRecherche(recherche) { recherche = it }
        }
        if (recherche.isNotBlank()) {
            val mots = simplifier(recherche).split(' ').filter { it.isNotBlank() }
            val trouvees = (entrees() + sousEntrees()).filter { e -> mots.all { m -> simplifier(e.titre).contains(m) || e.mots.split(' ').any { it.startsWith(m) } } }
            section(cle = "resultats") {
                if (trouvees.isEmpty()) Explication("Aucun réglage ne correspond à « $recherche ».")
                trouvees.forEach { e -> Ligne(e.titre, icone = e.icone) { ouvrir(e) } }
            }
            return@PageReglages
        }
        section(cle = "compte") {
            Ligne(
                "Compte Sama",
                detail = "Pas encore ouvert · sauvegarde et Espaces sur un autre téléphone",
                debut = {
                    val b = LocalBanco.current
                    Box(Modifier.size(44.dp).background(b.laterite, CircleShape), contentAlignment = Alignment.Center) {
                        IconeTrait(Icones.PERSONNE, 22.dp, b.surLaterite)
                    }
                },
            ) { nav.aller(Page.CompteSama) }
        }
        section(cle = "familles") {
            entrees().forEach { e -> Ligne(e.titre, detail = detailDe(e), icone = e.icone) { ouvrir(e) } }
        }
    }
}

/** Le galet de recherche des Réglages. */
@Composable
fun ChampRecherche(valeur: String, indication: String = "Rechercher un réglage", changer: (String) -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(b.sol2)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTrait("M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z M20 20l-4.8-4.8", 20.dp, b.encre2)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = valeur,
            onValueChange = changer,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre),
            cursorBrush = SolidColor(b.laterite),
            modifier = Modifier.weight(1f).semantics { contentDescription = indication },
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (valeur.isEmpty()) BasicText(indication, style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre2))
                    champ()
                }
            },
        )
    }
}
