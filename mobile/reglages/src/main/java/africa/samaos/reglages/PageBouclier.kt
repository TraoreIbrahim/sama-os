package africa.samaos.reglages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import africa.samaos.proches.Cercle
import kotlin.concurrent.thread
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
import africa.samaos.bouclier.Bouclier
import africa.samaos.bouclier.Evenement
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Le bouclier vu des Réglages : ce qu'il surveille, et ce qu'il a évité. */
object MoteurBouclier {
    /** Un même numéro qui rappelle, un même site rouvert : une seule arnaque, vue plusieurs fois. Le plus récent d'abord. */
    fun regrouper(l: List<Evenement>): List<Pair<Evenement, Int>> =
        l.groupBy { it.type to it.detail.substringBefore(" · ") }.values.map { it.first() to it.size }

    /** Les arnaques évitées (les numéros signalés à la main n'en sont pas). */
    fun evitees(l: List<Evenement>) = regrouper(l.filter { it.type != "signalement" }).size

    fun resume(c: Context): String {
        val n = evitees(Bouclier.recents(c))
        val coupees = Bouclier.Garde.entries.count { !Bouclier.actif(c, it) }
        return when {
            coupees == Bouclier.Garde.entries.size -> "Désactivé"
            n > 0 -> "$n arnaque${if (n > 1) "s" else ""} évitée${if (n > 1) "s" else ""} cette semaine"
            coupees > 0 -> "En partie désactivé"
            else -> "Actif"
        }
    }

    fun icone(type: String) = when (type) {
        "sms" -> Icones.MESSAGE
        "appel", "pendant" -> Icones.APPEL
        "lien" -> Icones.GLOBE
        else -> Icones.ALERTE
    }

    /** Ce qu'a fait le bouclier : un SMS ou un appel est signalé, un code attend, une page est bloquée. */
    fun suite(type: String) = when (type) {
        "lien" -> "Bloqué"
        "pendant" -> "Retenu"
        "signalement" -> "Par vous"
        else -> "Signalé"
    }

    /** « demain à 16:40 », « aujourd'hui à 9:05 ». */
    fun quand(t: Long): String {
        val d = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
        val heure = d.format(DateTimeFormatter.ofPattern("H:mm"))
        return when (d.toLocalDate()) {
            LocalDate.now() -> "aujourd'hui à $heure"
            LocalDate.now().plusDays(1) -> "demain à $heure"
            else -> "le " + d.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)) + " à $heure"
        }
    }

    fun jour(quand: Long): String {
        val d = Instant.ofEpochMilli(quand).atZone(ZoneId.systemDefault()).toLocalDate()
        val auj = LocalDate.now()
        return when (d) {
            auj -> "aujourd'hui"
            auj.minusDays(1) -> "hier"
            else -> d.format(DateTimeFormatter.ofPattern(if (d.isAfter(auj.minusDays(7))) "EEEE" else "d MMMM", Locale.FRENCH))
        }
    }
}

/** Le bouclier (maquette i2-reglages). */
@Composable
fun PageBouclier(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var v by remember { mutableIntStateOf(0) }
    var recents by remember { mutableStateOf(emptyList<Evenement>()) }
    LaunchedEffect(reprise) { recents = Bouclier.recents(c) }
    // « Un proche veille sur vous » : choisir un proche reconnu, puis son numéro.
    var ajout by remember { mutableStateOf(false) }
    var choisi by remember { mutableStateOf<ProcheReconnu?>(null) }
    var numero by remember { mutableStateOf("") }
    var arreter by remember { mutableStateOf<MoteurVeille.Gardien?>(null) }
    var essai by remember { mutableStateOf<String?>(null) }
    val contact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val u = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && u != null) {
            c.contentResolver.query(u, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { k -> if (k.moveToFirst()) numero = k.getString(0).orEmpty() }
        }
    }
    PageReglages(titre = "Bouclier", sousTitre = "Contre les arnaques au mobile money. Tout se passe sur le téléphone.", retour = nav.retour) {
        section("Surveiller", cle = "surveiller") {
            Bouclier.Garde.entries.forEach { g ->
                val oui = remember(v) { Bouclier.actif(c, g) }
                Ligne(g.nom, detail = g.detail, fin = Fin.Inter(oui)) {
                    Bouclier.regler(c, g, !oui)
                    v++
                }
            }
        }
        section(cle = "bilan") {
            val n = MoteurBouclier.evitees(recents)
            Ligne(
                "Bilan de la semaine",
                detail = if (n == 0) "Rien à signaler pour l'instant" else "$n arnaque${if (n > 1) "s" else ""} évitée${if (n > 1) "s" else ""}",
                icone = Icones.ALERTE,
            ) { nav.aller(Page.BilanBouclier) }
            Ligne("Les 5 arnaques les plus courantes", detail = "Et comment les reconnaître", icone = Icones.INFO) { nav.aller(Page.Arnaques) }
        }
        section("Un proche veille sur vous", cle = "veille") {
            val reconnus = remember(v) { MoteurReconnus.liste(c) }
            val gardiens = remember(v) { MoteurVeille.gardiens(c) }
            gardiens.forEach { g ->
                Ligne(g.nom, detail = "Prévenu par SMS au ${g.numero}", icone = Icones.PERSONNE, fin = Fin.Valeur("Arrêter")) { arreter = g }
            }
            if (gardiens.isNotEmpty()) {
                Ligne("Envoyer un essai", detail = essai ?: "Pour vérifier que le SMS arrive bien", icone = Icones.ENVOYER, fin = Fin.Rien) {
                    val l = gardiens
                    thread { l.forEach { MoteurVeille.essai(c, it) } }
                    essai = "Essai envoyé à " + l.joinToString(" et ") { it.nom }
                }
            }
            val libres = reconnus.filter { r -> gardiens.none { it.id == Cercle.id(r.cle) } }
            val ch = choisi
            when {
                reconnus.isEmpty() -> {
                    Explication("Un proche peut être prévenu quand le bouclier arrête une arnaque chez vous. Reconnaissez-vous d'abord, téléphones côte à côte.")
                    Ligne("Reconnaître un proche", detail = "Chacun scanne le code de l'autre", icone = Icones.QR) { nav.aller(Page.Reconnaitre()) }
                }
                ch != null -> {
                    Explication("Le numéro ${MoteurReconnus.de(ch.nom)}, où partiront les alertes.")
                    Champ(numero, "Son numéro de téléphone", { numero = it.take(20) }, clavier = KeyboardType.Phone)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        BoutonTexte("Dans les contacts") {
                            try {
                                contact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                            } catch (_: Exception) {
                            }
                        }
                        BoutonTexte("Annuler") {
                            choisi = null
                            ajout = false
                        }
                        if (numero.count { it.isDigit() } >= 8) BoutonTexte("Ajouter") {
                            MoteurVeille.ajouter(c, ch, numero)
                            choisi = null
                            ajout = false
                            numero = ""
                            v++
                        }
                    }
                }
                ajout -> {
                    libres.forEach { r -> Ligne(r.nom, detail = "Proche depuis le ${MoteurReconnus.date(r.quand)}", icone = Icones.PERSONNE) { choisi = r } }
                    if (libres.isEmpty()) Explication("Tous vos proches reconnus sont déjà prévenus.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { BoutonTexte("Annuler") { ajout = false } }
                }
                libres.isNotEmpty() -> Ligne(
                    if (gardiens.isEmpty()) "Choisir un proche à prévenir" else "Prévenir un autre proche",
                    detail = "Quand le bouclier arrête une arnaque grave chez vous", icone = Icones.PLUS, fin = Fin.Rien,
                ) { ajout = true }
            }
            Explication(
                "Seul le type d'arnaque part, par SMS (un seul, trois au plus par jour) : jamais vos messages, vos numéros ni votre solde. " +
                    "Le SMS est signé avec le secret de vos deux téléphones : personne ne peut fabriquer une fausse alerte. Vous arrêtez quand vous voulez.",
            )
            arreter?.let { g ->
                Confirmation("${g.nom} ne sera plus prévenu quand le bouclier arrête une arnaque chez vous.", "Arrêter", annuler = { arreter = null }) {
                    MoteurVeille.retirer(c, g)
                    arreter = null
                    v++
                }
            }
        }
        section(cle = "signaler") {
            val oui = remember(v) { Bouclier.signaler(c) }
            Ligne("Signaler anonymement", detail = "Seuls le numéro et le type d'arnaque partiront", icone = Icones.ENVOYER, fin = Fin.Inter(oui)) {
                Bouclier.reglerSignaler(c, !oui)
                v++
            }
            Explication(
                "La liste commune des numéros signalés viendra avec le service de Sama. D'ici là, rien ne quitte ce téléphone : " +
                    "le bouclier lit les SMS, les appels et les pages ici même.",
            )
        }
    }
}

/** Le bilan (maquette i2-bilan) : ce qui a été évité cette semaine, puis plus tôt. */
@Composable
fun PageBilanBouclier(nav: Nav) {
    val c = LocalContext.current
    val tout = remember { Bouclier.journal(c) }
    val depuis = System.currentTimeMillis() - 7 * 24 * 3600_000L
    val semaine = tout.filter { it.quand >= depuis }
    val avant = tout.filter { it.quand < depuis }.take(20)
    val n = MoteurBouclier.evitees(semaine)
    PageReglages(
        titre = if (n == 0) "Rien à signaler" else "$n arnaque${if (n > 1) "s" else ""} évitée${if (n > 1) "s" else ""}",
        sousTitre = "Le bouclier travaille sur le téléphone, sans rien envoyer",
        retour = nav.retour,
    ) {
        if (semaine.isNotEmpty()) {
            section("Cette semaine", cle = "semaine") {
                MoteurBouclier.regrouper(semaine).forEach { (e, fois) -> LigneEvenement(e, fois) }
            }
        }
        if (avant.isNotEmpty()) {
            section("Plus tôt", cle = "avant") {
                MoteurBouclier.regrouper(avant).forEach { (e, fois) -> LigneEvenement(e, fois) }
            }
        }
        if (tout.isEmpty()) {
            section(cle = "vide") {
                Explication("Quand un SMS imite un opérateur, qu'un inconnu insiste au téléphone ou qu'une page demande votre code secret, vous le verrez ici.")
            }
        }
        section(cle = "guide") {
            Ligne("Les 5 arnaques les plus courantes", detail = "Et comment les reconnaître", icone = Icones.INFO) { nav.aller(Page.Arnaques) }
        }
    }
}

@Composable
private fun LigneEvenement(e: Evenement, fois: Int) {
    val titre = when {
        e.type == "sms" && e.titre == "Prudence" -> "SMS qui demande un code"
        e.type == "sms" && e.titre == "Lien piégé" -> "SMS avec un lien piégé"
        e.type == "sms" -> "SMS d'arnaque"
        else -> e.titre
    }
    Ligne(titre, detail = e.detail.substringBefore(" · ") + (if (fois > 1) " · $fois fois" else "") + " · " + MoteurBouclier.jour(e.quand), icone = MoteurBouclier.icone(e.type), fin = Fin.Valeur(MoteurBouclier.suite(e.type)))
}

/** Les arnaques les plus courantes, et le geste qui les arrête. */
@Composable
fun PageArnaques(nav: Nav) {
    PageReglages(titre = "Les arnaques courantes", sousTitre = "Elles ont toutes un point commun : on vous presse.", retour = nav.retour) {
        section(cle = "liste") {
            Ligne(
                "« Je me suis trompé, renvoyez-moi l'argent »",
                detail = "Un faux reçu de transfert arrive par SMS, puis on vous appelle pour le réclamer. Regardez votre vrai solde : vous n'avez rien reçu.",
                icone = Icones.MESSAGE, fin = Fin.Rien,
            )
            Ligne(
                "Le faux agent au téléphone",
                detail = "Il se dit d'Orange, de MTN, de Moov ou de Wave et demande votre code pour « débloquer » le compte. Aucun agent ne demande votre code secret : raccrochez.",
                icone = Icones.APPEL, fin = Fin.Rien,
            )
            Ligne(
                "Le faux bonus",
                detail = "Un lien promet un bonus ou un remboursement et demande votre code secret. Un vrai bonus n'exige jamais votre code. Griot bloque ces pages.",
                icone = Icones.GLOBE, fin = Fin.Rien,
            )
            Ligne(
                "Le gain ou le colis à frais",
                detail = "Vous auriez gagné un lot ou un colis vous attend, à condition de payer des frais. Personne ne paie pour recevoir un cadeau.",
                icone = Icones.COEUR, fin = Fin.Rien,
            )
            Ligne(
                "L'appli « pour vous aider »",
                detail = "On vous fait installer une appli qui lit vos codes et vide votre compte. N'installez rien à la demande d'un inconnu.",
                icone = Icones.TELECHARGE, fin = Fin.Rien,
            )
        }
        section(cle = "regle") {
            Explication("Votre code secret ne se donne à personne : ni à un agent, ni à un proche, ni à une page web.")
        }
    }
}

/**
 * Autoriser une appli à installer hors de Sugu (maquette i2-delai-installation) : venir ici, c'est demander ;
 * l'autorisation n'est possible que 24 heures plus tard. Les arnaqueurs pressent toujours : le délai les désarme.
 */
@Composable
fun PageDelaiInstallation(paquet: String, nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var v by remember { mutableIntStateOf(0) }
    val op = Acces.INCONNUES.op!!
    val nom = remember(paquet) { MoteurApplis.nom(c, paquet) }
    val icone = remember(paquet) { MoteurApplis.icone(c, paquet, 32) }
    val autorisee = remember(v, reprise) { MoteurApplis.op(c, op, paquet) }
    val pret = remember(v, reprise) {
        if (autorisee) {
            null
        } else {
            // Une nouvelle demande entre au journal du bouclier : un proche qui veille en est prévenu.
            val nouvelle = Bouclier.delaiInstallation(c, paquet) == null
            Bouclier.demanderInstallation(c, paquet).also { if (nouvelle) Bouclier.noter(c, "installation", "Demande d'installation hors de Sugu", nom) }
        }
    }
    val possible = remember(v, reprise) { !autorisee && Bouclier.installationPossible(c, paquet) }
    val heureReseau = remember(reprise) { Bouclier.heureDuReseau(c) }
    fun annuler() {
        Bouclier.annulerInstallation(c, paquet)
        nav.retour()
    }
    when {
        autorisee -> PageReglages(titre = "Autorisée", sousTitre = "$nom peut installer des applis qui ne viennent pas de Sugu", retour = nav.retour) {
            section(cle = "acces") {
                Ligne("Autoriser $nom à installer des applis", image = icone, fin = Fin.Inter(true)) {
                    MoteurApplis.reglerOp(c, op, paquet, false)
                    nav.retour()
                }
                Explication("Retirez cet accès dès que l'installation est faite.")
            }
        }
        possible -> PageReglages(titre = "C'est prêt", sousTitre = "Personne ne vous presse ? Alors vous pouvez l'autoriser.", retour = nav.retour) {
            section(cle = "acces") {
                Ligne("Autoriser $nom à installer des applis", image = icone, fin = Fin.Inter(false)) {
                    MoteurApplis.reglerOp(c, op, paquet, true)
                    Bouclier.annulerInstallation(c, paquet)
                    // Le programme d'installation attend la réponse pour continuer.
                    (c as? Activity)?.setResult(Activity.RESULT_OK)
                    nav.retour()
                }
                Ligne("Annuler la demande", icone = Icones.FERMER, fin = Fin.Rien) { annuler() }
                Explication("Si quelqu'un vous a demandé d'installer cette appli, au téléphone ou par message, n'allez pas plus loin.")
            }
        }
        else -> {
            val reste = ((pret ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
            val heures = (reste + 3599_999L) / 3600_000L
            PageReglages(
                titre = if (heures > 1) "Revenez dans $heures heures" else "Revenez dans une heure",
                sousTitre = "Avant d'installer une appli qui ne vient pas de Sugu",
                retour = nav.retour,
            ) {
                section(cle = "acces") {
                    Ligne(
                        "Autoriser $nom à installer des applis",
                        detail = pret?.let { "Possible ${MoteurBouclier.quand(it)}" },
                        image = icone, fin = Fin.Valeur("En attente"),
                    )
                    Ligne("Annuler la demande", icone = Icones.FERMER, fin = Fin.Rien) { annuler() }
                }
                section(cle = "pourquoi") {
                    Explication(
                        "Les arnaqueurs pressent toujours. Le délai ne se raccourcit pas, même avec le code. " +
                            "Si personne ne vous presse, l'autorisation sera prête demain.",
                    )
                    if (!heureReseau) {
                        Explication(
                            "L'heure du téléphone n'est pas réglée par le réseau : le délai ne comptera pas tant qu'elle ne l'est pas.",
                            LocalBanco.current.lateriteTexte,
                        )
                    }
                }
            }
        }
    }
}
