package africa.samaos.reglages

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable

/**
 * Les pages des Réglages de Sama (maquettes du lot 3). Chacune sait quelle page des Paramètres d'Android
 * lui correspond : tant que Sama ne la fait pas, c'est celle-là qui s'ouvre.
 */
sealed class Page(val android: String? = null) {
    open val intentAndroid: Intent get() = Intent(android ?: Settings.ACTION_SETTINGS)

    data object Accueil : Page(Settings.ACTION_SETTINGS)

    // Réseau et data
    data object Reseau : Page(Settings.ACTION_WIRELESS_SETTINGS)
    data object Wifi : Page(Settings.ACTION_WIFI_SETTINGS)
    data class WifiDetail(val ssid: String) : Page(Settings.ACTION_WIFI_SETTINGS)
    data class WifiConnexion(val ssid: String, val niveau: Int) : Page(Settings.ACTION_WIFI_SETTINGS)
    data object Sim : Page("android.settings.MANAGE_ALL_SIM_PROFILES_SETTINGS")
    data class SimReglages(val id: Int) : Page("android.settings.NETWORK_OPERATOR_SETTINGS")
    data object PointAcces : Page("android.settings.TETHER_SETTINGS")
    data object Vpn : Page(Settings.ACTION_VPN_SETTINGS)

    // Appareils
    data object Appareils : Page("android.settings.CONNECTED_DEVICES_SETTINGS")
    data object Bluetooth : Page(Settings.ACTION_BLUETOOTH_SETTINGS)
    data object Usb : Page("android.settings.USB_SETTINGS")
    data class AppareilBluetooth(val adresse: String) : Page(Settings.ACTION_BLUETOOTH_SETTINGS)

    // Applications
    data object Applis : Page(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
    data class AppliInfos(val paquet: String) : Page(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
        override val intentAndroid get() = Intent(android, Uri.fromParts("package", paquet, null))
    }
    data class AppliAutorisations(val paquet: String) : Page(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
        override val intentAndroid get() = Intent(android, Uri.fromParts("package", paquet, null))
    }
    data object ApplisDefaut : Page(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    data object AccesSpeciaux : Page("android.settings.SPECIAL_APP_ACCESS_SETTINGS")
    data class AccesSpecial(val acces: Acces) : Page("android.settings.SPECIAL_APP_ACCESS_SETTINGS")
    data object ApplisInutilisees : Page("android.intent.action.MANAGE_UNUSED_APPS")

    // Notifications
    data object Notifications : Page("android.settings.NOTIFICATION_SETTINGS")
    data class NotifsAppli(val paquet: String) : Page(Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
        override val intentAndroid get() = Intent(android).putExtra(Settings.EXTRA_APP_PACKAGE, paquet)
    }
    data object NePasDeranger : Page("android.settings.ZEN_MODE_SETTINGS")
    data object NotifsVerrou : Page("android.settings.LOCK_SCREEN_SETTINGS")
    data object HistoriqueNotifs : Page("android.settings.NOTIFICATION_HISTORY")

    // Batterie, stockage
    data object Batterie : Page(Intent.ACTION_POWER_USAGE_SUMMARY)
    data object Economiseur : Page(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    data object SanteBatterie : Page(Intent.ACTION_POWER_USAGE_SUMMARY)
    data object Stockage : Page(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
    data object CarteSd : Page(Settings.ACTION_MEMORY_CARD_SETTINGS)

    // Son
    data object Son : Page(Settings.ACTION_SOUND_SETTINGS)
    data object Sonneries : Page(Settings.ACTION_SOUND_SETTINGS)
    data object Vibrations : Page(Settings.ACTION_SOUND_SETTINGS)

    // Affichage, style
    data object Affichage : Page(Settings.ACTION_DISPLAY_SETTINGS)
    data object ThemeNuit : Page("android.settings.DARK_THEME_SETTINGS")
    data object EclairageNocturne : Page(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)
    data object TailleTexte : Page("android.settings.TEXT_READING_SETTINGS")
    data object VeilleRotation : Page(Settings.ACTION_DISPLAY_SETTINGS)
    data object PleinSoleil : Page(Settings.ACTION_DISPLAY_SETTINGS)
    data object FondEcran : Page(Intent.ACTION_SET_WALLPAPER)
    data object EcranVerrouille : Page("android.settings.LOCK_SCREEN_SETTINGS")

    // Sécurité, confidentialité
    data object Securite : Page(Settings.ACTION_SECURITY_SETTINGS)
    data object Verrouillage : Page(Settings.ACTION_SECURITY_SETTINGS)
    data object GestionnaireAutorisations : Page("android.intent.action.MANAGE_PERMISSIONS")
    data class AutorisationGroupe(val groupe: Groupe) : Page("android.intent.action.MANAGE_PERMISSIONS")
    data object TableauConfidentialite : Page("android.intent.action.REVIEW_PERMISSION_USAGE")
    data object CameraMicro : Page(Settings.ACTION_PRIVACY_SETTINGS)
    data object PinSim : Page(Settings.ACTION_SECURITY_SETTINGS)
    data object Chiffrement : Page(Settings.ACTION_SECURITY_SETTINGS)
    data object Localisation : Page(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    // Comptes, services
    data object Comptes : Page(Settings.ACTION_SYNC_SETTINGS)
    data object CompteSama : Page(Settings.ACTION_SYNC_SETTINGS)
    data object AutresComptes : Page(Settings.ACTION_SYNC_SETTINGS)
    data object MotsDePasse : Page("android.settings.REQUEST_SET_AUTOFILL_SERVICE")
    data object Sauvegarde : Page("android.settings.BACKUP_AND_RESET_SETTINGS")
    data object AppareilsAssocies : Page("android.settings.CONNECTED_DEVICES_SETTINGS")

    // Système
    data object Systeme : Page("android.settings.SYSTEM_SETTINGS")
    data object Langues : Page(Settings.ACTION_LOCALE_SETTINGS)
    data object Clavier : Page(Settings.ACTION_INPUT_METHOD_SETTINGS)
    data object Gestes : Page("android.settings.GESTURE_SETTINGS")
    data object DateHeure : Page(Settings.ACTION_DATE_SETTINGS)
    data object MajSysteme : Page("android.settings.SYSTEM_UPDATE_SETTINGS")
    data object APropos : Page(Settings.ACTION_DEVICE_INFO_SETTINGS)
    data object Impression : Page(Settings.ACTION_PRINT_SETTINGS)

    // Bien-être (lot 5)
    data object TempsEcran : Page(Settings.ACTION_SETTINGS)
    data class MinuteurAppli(val paquet: String) : Page(Settings.ACTION_SETTINGS)
    data object Minuteurs : Page(Settings.ACTION_SETTINGS)
    data object Concentration : Page(Settings.ACTION_SETTINGS)

    // Accessibilité (lot 5)
    data object Accessibilite : Page(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    data object Couleurs : Page(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    data object Loupe : Page(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    // Urgence (lot 4)
    data object Urgence : Page(Settings.ACTION_SETTINGS)
    data object FicheMedicale : Page(Settings.ACTION_SETTINGS)
    data object Reinitialiser : Page("android.settings.BACKUP_AND_RESET_SETTINGS")
    data object ToutEffacer : Page("android.settings.BACKUP_AND_RESET_SETTINGS")

    // Bouclier anti-arnaques (innovation 2)
    data object Bouclier : Page(Settings.ACTION_SETTINGS)
    data object BilanBouclier : Page(Settings.ACTION_SETTINGS)
    data object Arnaques : Page(Settings.ACTION_SETTINGS)

    // Alertes du téléphone (lot 4)
    data object StockagePlein : Page(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
    data object Surchauffe : Page(Settings.ACTION_SETTINGS)

    // Soldes et forfaits (innovation 1)
    data object Soldes : Page(Settings.ACTION_SETTINGS)

    // Proche en proche (innovation 6)
    data object Proches : Page(Settings.ACTION_SETTINGS)
    data class PointSama(val nom: String, val hote: String, val port: Int) : Page(Settings.ACTION_SETTINGS)
    data class Reception(val sha: String) : Page(Settings.ACTION_SETTINGS)
    data class Refuse(val sha: String) : Page(Settings.ACTION_SETTINGS)
    data class DelaiInstallation(val paquet: String) : Page("android.settings.MANAGE_UNKNOWN_APP_SOURCES") {
        override val intentAndroid get() = Intent(android, Uri.fromParts("package", paquet, null))
    }

    /** Vrai quand Sama dessine cette page lui-même. */
    val faite: Boolean
        get() = this in FAITES || this is WifiDetail || this is WifiConnexion || this is SimReglages || this is AppareilBluetooth ||
            this is AppliInfos || this is AppliAutorisations || this is AccesSpecial || this is NotifsAppli || this is AutorisationGroupe || this is MinuteurAppli ||
            this is DelaiInstallation || this is PointSama || this is Reception || this is Refuse

    companion object {
        private val FAITES: Set<Page> by lazy {
            setOf(
                Accueil, Reseau, Wifi, Sim, PointAcces, Vpn,
                Appareils, Bluetooth, Usb, AppareilsAssocies,
                Applis, ApplisDefaut, AccesSpeciaux, ApplisInutilisees,
                Notifications, NePasDeranger, NotifsVerrou, HistoriqueNotifs,
                Batterie, Economiseur, SanteBatterie, Stockage, CarteSd,
                Son, Sonneries, Vibrations,
                Affichage, ThemeNuit, EclairageNocturne, TailleTexte, VeilleRotation, PleinSoleil, FondEcran, EcranVerrouille,
                Securite, Verrouillage, GestionnaireAutorisations, TableauConfidentialite, CameraMicro, PinSim, Chiffrement, Localisation,
                Systeme, Langues, Clavier, Gestes, DateHeure, MajSysteme, APropos,
                Comptes, CompteSama, AutresComptes, MotsDePasse, Sauvegarde,
                TempsEcran, Minuteurs, Concentration, Accessibilite, Couleurs, Loupe, Urgence, FicheMedicale, Reinitialiser, ToutEffacer,
                Bouclier, BilanBouclier, Arnaques, StockagePlein, Surchauffe, Soldes, Proches,
            )
        }

        /** La page qu'une intention demande ; null si les Paramètres d'Android doivent s'en charger. */
        fun depuis(intent: Intent?): Page? {
            val page = when (intent?.action) {
                null, Intent.ACTION_MAIN, Settings.ACTION_SETTINGS -> Accueil
                Settings.ACTION_WIFI_SETTINGS -> Wifi
                Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_AIRPLANE_MODE_SETTINGS, Settings.ACTION_DATA_ROAMING_SETTINGS -> Reseau
                Settings.ACTION_NETWORK_OPERATOR_SETTINGS -> Sim
                "android.settings.TETHER_SETTINGS" -> PointAcces
                Settings.ACTION_VPN_SETTINGS -> Vpn
                Settings.ACTION_BLUETOOTH_SETTINGS -> Bluetooth
                Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS -> Applis
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> intent.data?.schemeSpecificPart?.let { AppliInfos(it) }
                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS -> ApplisDefaut
                "android.settings.NOTIFICATION_SETTINGS" -> Notifications
                Settings.ACTION_APP_NOTIFICATION_SETTINGS -> intent.getStringExtra(Settings.EXTRA_APP_PACKAGE)?.let { NotifsAppli(it) }
                "android.settings.ZEN_MODE_SETTINGS" -> NePasDeranger
                Intent.ACTION_POWER_USAGE_SUMMARY -> Batterie
                Settings.ACTION_BATTERY_SAVER_SETTINGS -> Economiseur
                Settings.ACTION_INTERNAL_STORAGE_SETTINGS -> Stockage
                Settings.ACTION_SOUND_SETTINGS -> Son
                Settings.ACTION_DISPLAY_SETTINGS -> Affichage
                Intent.ACTION_SET_WALLPAPER -> FondEcran
                Settings.ACTION_NIGHT_DISPLAY_SETTINGS -> EclairageNocturne
                "android.settings.DARK_THEME_SETTINGS" -> ThemeNuit
                Settings.ACTION_SECURITY_SETTINGS -> Securite
                Settings.ACTION_PRIVACY_SETTINGS -> TableauConfidentialite
                Settings.ACTION_LOCATION_SOURCE_SETTINGS -> Localisation
                Settings.ACTION_LOCALE_SETTINGS -> Langues
                Settings.ACTION_INPUT_METHOD_SETTINGS -> Clavier
                Settings.ACTION_DATE_SETTINGS -> DateHeure
                Settings.ACTION_DEVICE_INFO_SETTINGS -> APropos
                Settings.ACTION_SYNC_SETTINGS -> Comptes
                Settings.ACTION_PRINT_SETTINGS -> Impression
                Settings.ACTION_ACCESSIBILITY_SETTINGS -> Accessibilite
                // Le programme d'installation d'Android envoie ici avant d'installer une appli hors de Sugu.
                // La notification d'Android « stockage bientôt saturé » ouvre cette action.
                android.os.storage.StorageManager.ACTION_MANAGE_STORAGE -> StockagePlein
                "africa.samaos.action.SURCHAUFFE" -> Surchauffe
                "africa.samaos.action.SOLDES" -> Soldes
                "africa.samaos.action.PROCHES" -> Proches
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES -> intent.data?.schemeSpecificPart?.let { DelaiInstallation(it) } ?: AccesSpecial(Acces.INCONNUES)
                else -> null
            }
            return page?.takeIf { it.faite }
        }
    }
}

/** Les pages de second niveau qu'on trouve aussi par la recherche. */
fun sousEntrees(): List<Entree> = listOf(
    Entree("Wi-Fi", africa.samaos.banco.Icones.WIFI, "wifi internet box reseau", Page.Wifi),
    Entree("Cartes SIM", africa.samaos.banco.Icones.SIM, "sim carte operateur orange mtn moov data appels sms", Page.Sim),
    Entree("Partage de connexion", africa.samaos.banco.Icones.POINT_ACCES, "point acces partage connexion hotspot", Page.PointAcces),
    Entree("VPN et DNS privé", africa.samaos.banco.Icones.CADENAS, "vpn dns prive", Page.Vpn),
    Entree("Bluetooth", africa.samaos.banco.Icones.BLUETOOTH, "bluetooth ecouteurs enceinte", Page.Bluetooth),
    Entree("Ne pas déranger", africa.samaos.banco.Icones.NE_PAS_DERANGER, "ne pas deranger silence nuit", Page.NePasDeranger),
    Entree("Économiseur de batterie", africa.samaos.banco.Icones.ECONOMISEUR, "economiseur batterie", Page.Economiseur),
    Entree("Mode sombre", africa.samaos.banco.Icones.NUIT, "mode sombre nuit theme", Page.ThemeNuit),
    Entree("Taille du texte", africa.samaos.banco.Icones.TEXTE, "taille texte police grand", Page.TailleTexte),
    Entree("Langues et région", africa.samaos.banco.Icones.LANGUE, "langue francais anglais dioula region", Page.Langues),
    Entree("Date et heure", africa.samaos.banco.Icones.HORLOGE, "date heure fuseau", Page.DateHeure),
    Entree("À propos du téléphone", africa.samaos.banco.Icones.INFO, "a propos telephone imei numero version modele", Page.APropos),
    Entree("Applications par défaut", africa.samaos.banco.Icones.APPLI, "defaut navigateur sms telephone accueil", Page.ApplisDefaut),
    Entree("Minuteurs d'applis", africa.samaos.banco.Icones.HORLOGE, "minuteur limite temps appli", Page.Minuteurs),
    Entree("Concentration", africa.samaos.banco.Icones.NE_PAS_DERANGER, "concentration pause applis travail ecole", Page.Concentration),
    Entree("Couleurs et contraste", africa.samaos.banco.Icones.PALETTE, "couleurs contraste daltonien inverser gris", Page.Couleurs),
    Entree("Loupe", africa.samaos.banco.Icones.RECHERCHE, "loupe agrandir zoom vue", Page.Loupe),
    Entree("Réinitialiser", africa.samaos.banco.Icones.CORBEILLE, "reinitialiser effacer remise zero usine reseau", Page.Reinitialiser),
    Entree("Fiche médicale", africa.samaos.banco.Icones.PERSONNE, "fiche medicale sang allergie urgence", Page.FicheMedicale),
)

object MoteurSecurite {
    fun codeEnPlace(c: Context) = c.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
}

@Composable
fun Afficher(page: Page, nav: Nav) {
    when (page) {
        Page.Accueil -> PageAccueil(nav)
        Page.Reseau -> PageReseau(nav)
        Page.Wifi -> PageWifi(nav)
        is Page.WifiDetail -> PageWifiDetail(page.ssid, nav)
        is Page.WifiConnexion -> PageWifiConnexion(page.ssid, page.niveau, nav)
        Page.Sim -> PageSim(nav)
        is Page.SimReglages -> PageSimReglages(page.id, nav)
        Page.PointAcces -> PagePointAcces(nav)
        Page.Vpn -> PageVpn(nav)
        Page.Appareils -> PageAppareils(nav)
        Page.Bluetooth -> PageBluetooth(nav)
        is Page.AppareilBluetooth -> PageAppareilBluetooth(page.adresse, nav)
        Page.Usb -> PageUsb(nav)
        Page.AppareilsAssocies -> PageAppareilsAssocies(nav)
        Page.Applis -> PageApplis(nav)
        is Page.AppliInfos -> PageAppliInfos(page.paquet, nav)
        is Page.AppliAutorisations -> PageAppliAutorisations(page.paquet, nav)
        Page.ApplisDefaut -> PageApplisDefaut(nav)
        Page.AccesSpeciaux -> PageAccesSpeciaux(nav)
        is Page.AccesSpecial -> PageAccesSpecial(page.acces, nav)
        Page.ApplisInutilisees -> PageApplisInutilisees(nav)
        Page.Notifications -> PageNotifications(nav)
        is Page.NotifsAppli -> PageNotifsAppli(page.paquet, nav)
        Page.NePasDeranger -> PageNePasDeranger(nav)
        Page.NotifsVerrou -> PageNotifsVerrou(nav)
        Page.HistoriqueNotifs -> PageHistoriqueNotifs(nav)
        Page.Batterie -> PageBatterie(nav)
        Page.Economiseur -> PageEconomiseur(nav)
        Page.SanteBatterie -> PageSanteBatterie(nav)
        Page.Stockage -> PageStockage(nav)
        Page.CarteSd -> PageCarteSd(nav)
        Page.Son -> PageSon(nav)
        Page.Sonneries -> PageSonneries(nav)
        Page.Vibrations -> PageVibrations(nav)
        Page.Affichage -> PageAffichage(nav)
        Page.ThemeNuit -> PageThemeNuit(nav)
        Page.EclairageNocturne -> PageEclairageNocturne(nav)
        Page.TailleTexte -> PageTailleTexte(nav)
        Page.VeilleRotation -> PageVeilleRotation(nav)
        Page.PleinSoleil -> PagePleinSoleil(nav)
        Page.FondEcran -> PageFondEcran(nav)
        Page.EcranVerrouille -> PageEcranVerrouille(nav)
        Page.Securite -> PageSecurite(nav)
        Page.Verrouillage -> PageVerrouillage(nav)
        Page.GestionnaireAutorisations -> PageGestionnaireAutorisations(nav)
        is Page.AutorisationGroupe -> PageAutorisationGroupe(page.groupe, nav)
        Page.TableauConfidentialite -> PageTableauConfidentialite(nav)
        Page.CameraMicro -> PageCameraMicro(nav)
        Page.PinSim -> PagePinSim(nav)
        Page.Chiffrement -> PageChiffrement(nav)
        Page.Localisation -> PageLocalisation(nav)
        Page.Systeme -> PageSysteme(nav)
        Page.Langues -> PageLangues(nav)
        Page.Clavier -> PageClavier(nav)
        Page.Gestes -> PageGestes(nav)
        Page.DateHeure -> PageDateHeure(nav)
        Page.MajSysteme -> PageMajSysteme(nav)
        Page.APropos -> PageAPropos(nav)
        Page.Comptes, Page.AutresComptes -> PageComptes(nav)
        Page.CompteSama -> PageCompteSama(nav)
        Page.MotsDePasse -> PageMotsDePasse(nav)
        Page.Sauvegarde -> PageSauvegarde(nav)
        Page.TempsEcran -> PageTempsEcran(nav)
        is Page.MinuteurAppli -> PageMinuteurAppli(page.paquet, nav)
        Page.Minuteurs -> PageMinuteurs(nav)
        Page.Concentration -> PageConcentration(nav)
        Page.Accessibilite -> PageAccessibilite(nav)
        Page.Couleurs -> PageCouleurs(nav)
        Page.Loupe -> PageLoupe(nav)
        Page.Urgence -> PageUrgence(nav)
        Page.FicheMedicale -> PageFicheMedicale(nav)
        Page.Reinitialiser -> PageReinitialiser(nav)
        Page.ToutEffacer -> PageToutEffacer(nav)
        Page.Bouclier -> PageBouclier(nav)
        Page.BilanBouclier -> PageBilanBouclier(nav)
        Page.Arnaques -> PageArnaques(nav)
        is Page.DelaiInstallation -> PageDelaiInstallation(page.paquet, nav)
        Page.StockagePlein -> PageStockagePlein(nav)
        Page.Surchauffe -> PageSurchauffe(nav)
        Page.Soldes -> PageSoldes(nav)
        Page.Proches -> PageProches(nav)
        is Page.PointSama -> PagePointSama(page.nom, page.hote, page.port, nav)
        is Page.Reception -> PageReception(page.sha, nav)
        is Page.Refuse -> PageRefuse(page.sha, nav)
        else -> {}
    }
}
