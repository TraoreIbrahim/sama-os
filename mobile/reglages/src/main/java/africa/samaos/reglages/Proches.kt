package africa.samaos.reglages

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Avancer
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.LocalNuit
import africa.samaos.banco.PaysageEspace
import africa.samaos.banco.Polices
import africa.samaos.banco.palette
import africa.samaos.proches.Decouverte
import africa.samaos.proches.Editeurs
import africa.samaos.proches.Manifeste
import africa.samaos.proches.Point
import africa.samaos.proches.Protocole
import africa.samaos.proches.Verification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Proche en proche (innovation 6, maquettes i6-*) : recevoir sans data des applis et des mises à jour signées,
 * depuis un point Sama ou le téléphone d'un contact. Rien n'est installé sans que la fiche soit signée par un
 * éditeur connu, que la version soit plus récente et que le fichier soit exactement celui de la fiche.
 */
object MoteurProches {
    private fun prefs(c: Context) = c.getSharedPreferences("proches", Context.MODE_PRIVATE)
    const val FENETRE = 10 * 60_000L

    // ——— La réception : éteinte, sauf quand la personne l'ouvre, 10 minutes ———
    fun receptionJusqua(c: Context) = prefs(c).getLong("reception", 0L)
    fun receptionOuverte(c: Context) = System.currentTimeMillis() < receptionJusqua(c)
    fun ouvrir(c: Context) = prefs(c).edit().putLong("reception", System.currentTimeMillis() + FENETRE).apply()
    fun fermer(c: Context) = prefs(c).edit().putLong("reception", 0L).apply()

    /** « contacts » : contacts et points Sama ; « points » : seulement les points Sama. */
    fun depuis(c: Context) = prefs(c).getString("depuis", "contacts").orEmpty()
    fun reglerDepuis(c: Context, v: String) = prefs(c).edit().putString("depuis", v).apply()

    // ——— Donner ———
    fun donner(c: Context) = prefs(c).getBoolean("donner", true)
    fun reglerDonner(c: Context, oui: Boolean) = prefs(c).edit().putBoolean("donner", oui).apply()
    fun brancheSeulement(c: Context) = prefs(c).getBoolean("branche", true)
    fun reglerBranche(c: Context, oui: Boolean) = prefs(c).edit().putBoolean("branche", oui).apply()
    val LIMITES = listOf(500_000_000L, 1_000_000_000L, 2_000_000_000L, 0L)
    fun limite(c: Context) = prefs(c).getLong("limite", 2_000_000_000L)
    fun limiteSuivante(c: Context) = prefs(c).edit().putLong("limite", LIMITES[(LIMITES.indexOf(limite(c)) + 1) % LIMITES.size]).apply()

    // ——— Le bilan ———
    fun economise(c: Context) = prefs(c).getLong("economise", 0L)
    fun noterEconomise(c: Context, octets: Long) = prefs(c).edit().putLong("economise", economise(c) + octets).apply()
    fun donne(c: Context) = prefs(c).getLong("donne", 0L)
    fun personnes(c: Context) = prefs(c).getInt("personnes", 0)

    // ——— Les points Sama ajoutés à la main (quand le réseau de l'école ne les annonce pas) ———
    fun points(c: Context): List<Point> = prefs(c).getStringSet("points", emptySet()).orEmpty().mapNotNull { e ->
        val (nom, adresse) = e.split('\t').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: return@mapNotNull null) }
        val hote = adresse.substringBeforeLast(':')
        val port = adresse.substringAfterLast(':').toIntOrNull() ?: Protocole.PORT
        Point(nom.ifBlank { "Point Sama" }, hote, port)
    }.sortedBy { it.nom }

    fun ajouterPoint(c: Context, p: Point) {
        val l = prefs(c).getStringSet("points", emptySet()).orEmpty().filter { !it.endsWith("\t" + p.adresse) } + "${p.nom}\t${p.adresse}"
        prefs(c).edit().putStringSet("points", l.toSet()).apply()
    }

    /** « 10.0.2.2:8765 », « 192.168.1.20 » : une adresse de point lisible, ou null. */
    fun lireAdresse(t: String): Point? {
        val m = Regex("^\\s*([A-Za-z0-9.-]+)(?::(\\d{1,5}))?\\s*$").find(t) ?: return null
        return Point("Point Sama", m.groupValues[1], m.groupValues[2].toIntOrNull() ?: Protocole.PORT)
    }

    // ——— Les sources dont on ne veut plus rien ———
    fun bloque(c: Context, source: String) = source in prefs(c).getStringSet("bloques", emptySet()).orEmpty()
    fun reglerBloque(c: Context, source: String, oui: Boolean) {
        val l = prefs(c).getStringSet("bloques", emptySet()).orEmpty().toMutableSet()
        if (oui) l += source else l -= source
        prefs(c).edit().putStringSet("bloques", l).apply()
    }

    fun resume(c: Context): String = when {
        receptionOuverte(c) -> "Réception ouverte · encore ${((receptionJusqua(c) - System.currentTimeMillis()) / 60_000 + 1)} min"
        economise(c) > 0 -> taille(economise(c)) + " économisés grâce à vos proches"
        else -> "Recevoir sans data, entre proches"
    }

    /** Installer une appli vérifiée. Les Réglages ont le droit du système (INSTALL_PACKAGES) : pas de seconde question. */
    fun installer(c: Context, f: File, m: Manifeste) {
        val pi = c.packageManager.packageInstaller
        val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(m.paquet)
            setSize(f.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(p)
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, f.length()).use { o ->
                f.inputStream().use { it.copyTo(o) }
                s.fsync(o)
            }
            val i = Intent(c, ResultatInstallation::class.java).putExtra("sha", m.sha256)
            s.commit(PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).intentSender)
        }
    }
}

/** Les réceptions en cours ou finies, suivies par l'écran de réception. */
object Receptions {
    enum class Etape { RECEPTION, VERIFICATION, INSTALLATION, INSTALLEE, REFUSEE, ECHEC }

    class Etat(val m: Manifeste, val point: Point, val versionAvant: Long?) {
        var recu by mutableLongStateOf(0L)
        var etape by mutableStateOf(Etape.RECEPTION)
        var intact by mutableStateOf<Boolean?>(null)
        var message by mutableStateOf<String?>(null)
        val debut = System.currentTimeMillis()
        @Volatile var arret = false
    }

    val etats = mutableStateMapOf<String, Etat>()

    private fun fichier(c: Context, m: Manifeste) = File(File(c.filesDir, "proches").apply { mkdirs() }, "${m.sha256}.part")

    fun lancer(c: Context, p: Point, m: Manifeste): Etat {
        etats[m.sha256]?.takeIf { it.etape == Etape.RECEPTION || it.etape == Etape.INSTALLATION || it.etape == Etape.VERIFICATION }?.let { return it }
        val e = Etat(m, p, Verification.versionInstallee(c, m.paquet))
        etats[m.sha256] = e
        val app = c.applicationContext
        Thread {
            val f = fichier(app, m)
            try {
                // Avant même de recevoir : fiche signée, version plus récente.
                if (!Verification.signature(app, m) || !Verification.plusRecente(app, m)) {
                    e.etape = Etape.REFUSEE
                    return@Thread
                }
                Protocole.recevoir(p, m, f, { e.recu = it }) { e.arret }
                if (e.arret) return@Thread
                // Seul un fichier complet est vérifié : un morceau ne prouve rien, il attend la suite.
                if (f.length() < m.taille) throw java.io.IOException("incomplet")
                e.etape = Etape.VERIFICATION
                val bon = Verification.intact(f, m) && Verification.appli(app, f, m)
                e.intact = bon
                if (!bon) {
                    // Un fichier qui n'est pas celui de la fiche ne reste pas sur le téléphone.
                    f.delete()
                    e.etape = Etape.REFUSEE
                    africa.samaos.bouclier.Bouclier.noter(app, "proches", "Fichier refusé", "${m.nom} · ${p.nom}")
                    return@Thread
                }
                e.etape = Etape.INSTALLATION
                MoteurProches.installer(app, f, m)
            } catch (ex: Exception) {
                e.message = "La connexion s'est coupée. Rapprochez-vous du point : la réception reprendra où elle s'est arrêtée."
                e.etape = Etape.ECHEC
            }
        }.start()
        return e
    }

    fun fini(c: Context, sha: String, ok: Boolean, message: String?) {
        val e = etats[sha] ?: return
        fichier(c, e.m).delete()
        if (ok) {
            e.etape = Etape.INSTALLEE
            MoteurProches.noterEconomise(c, e.m.taille)
        } else {
            e.message = message ?: "Android n'a pas pu installer l'appli."
            e.etape = Etape.ECHEC
        }
    }
}

/** Le résultat de l'installation, envoyé par Android. */
class ResultatInstallation : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val sha = i.getStringExtra("sha") ?: return
        when (val statut = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Sans le droit du système, Android demande confirmation : on montre sa question.
                @Suppress("DEPRECATION")
                (i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Receptions.fini(c, sha, true, null)
            else -> Receptions.fini(c, sha, false, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { "Android a refusé l'installation ($statut)." })
        }
    }
}

// ——— Les écrans ———

/** Proche en proche (maquette i6-reglages). */
@Composable
fun PageProches(nav: Nav) {
    val c = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            v++
        }
    }
    // Les points Sama du réseau, cherchés seulement pendant qu'on regarde.
    val trouves = remember { mutableStateListOf<Point>() }
    DisposableEffect(Unit) {
        val d = Decouverte(c) { p -> if (trouves.none { it.adresse == p.adresse }) trouves += p }
        d.commencer()
        onDispose { d.arreter() }
    }
    var ajout by remember { mutableStateOf(false) }
    var oublier by remember { mutableStateOf<ProcheReconnu?>(null) }
    var saisie by remember { mutableStateOf("") }
    val ouverte = remember(v) { MoteurProches.receptionOuverte(c) }
    PageReglages(titre = "Proche en proche", sousTitre = "Recevoir et donner sans data, entre proches", retour = nav.retour) {
        section("Recevoir d'un point Sama", cle = "points") {
            val connus = remember(v) { MoteurProches.points(c) }
            val tous = (trouves + connus).distinctBy { it.adresse }
            tous.forEach { p ->
                Ligne(p.nom, detail = p.adresse, icone = Icones.TELECHARGE) { nav.aller(Page.PointSama(p.nom, p.hote, p.port)) }
            }
            if (tous.isEmpty()) Explication("Aucun point Sama sur ce réseau. Les points sont des ordinateurs Sama d'écoles, de mairies ou de cybercafés.")
            if (ajout) {
                Champ(saisie, "Adresse, par exemple 192.168.1.20", { saisie = it.trim().take(40) }, clavier = KeyboardType.Uri)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BoutonTexte("Annuler") { ajout = false }
                    MoteurProches.lireAdresse(saisie)?.let { p ->
                        BoutonTexte("Ajouter") {
                            MoteurProches.ajouterPoint(c, p)
                            ajout = false
                            saisie = ""
                            v++
                        }
                    }
                }
            } else {
                Ligne("Ajouter un point par son adresse", detail = "Quand le réseau de l'école ne l'annonce pas", icone = Icones.PLUS, fin = Fin.Rien) { ajout = true }
            }
        }
        section("Depuis vos contacts", cle = "recevoir") {
            Ligne(
                if (ouverte) "Réception ouverte" else "Ouvrir la réception",
                detail = if (ouverte) "Encore ${((MoteurProches.receptionJusqua(c) - System.currentTimeMillis()) / 60_000 + 1)} min · vos contacts proches peuvent vous voir"
                else "Éteinte · 10 minutes, à votre demande seulement",
                icone = Icones.PROXIMITE,
                fin = Fin.Bouton(if (ouverte) "Fermer" else "Ouvrir", plein = !ouverte),
            ) {
                if (ouverte) MoteurProches.fermer(c) else MoteurProches.ouvrir(c)
                v++
            }
            Explication("L'échange entre téléphones arrive bientôt : pour l'instant, on reçoit des points Sama.")
            Ligne("Reconnaître un proche", detail = "Chacun scanne le code de l'autre, téléphones côte à côte", icone = Icones.QR) { nav.aller(Page.Reconnaitre()) }
            val reconnus = remember(v) { MoteurReconnus.liste(c) }
            reconnus.forEach { p ->
                Ligne(p.nom, detail = "Proche depuis le ${MoteurReconnus.date(p.quand)}", icone = Icones.PERSONNE, fin = Fin.Valeur("Oublier")) { oublier = p }
            }
            oublier?.let { p ->
                Confirmation("${p.nom} ne pourra plus vous voir ni vous envoyer d'applis. Pour recommencer, il faudra se scanner de nouveau.", "Oublier", annuler = { oublier = null }) {
                    MoteurReconnus.oublier(c, p)
                    oublier = null
                    v++
                }
            }
            val depuis = remember(v) { MoteurProches.depuis(c) }
            Ligne("Depuis", detail = if (depuis == "points") "Seulement les points Sama" else "Vos contacts et les points Sama", icone = Icones.PERSONNE) {
                MoteurProches.reglerDepuis(c, if (depuis == "points") "contacts" else "points")
                v++
            }
        }
        section("Donner", cle = "donner") {
            val donner = remember(v) { MoteurProches.donner(c) }
            Ligne("Partager avec mes contacts", detail = "Les applis et mises à jour de Sugu que vous avez déjà", fin = Fin.Inter(donner)) {
                MoteurProches.reglerDonner(c, !donner)
                v++
            }
            val branche = remember(v) { MoteurProches.brancheSeulement(c) }
            Ligne("Seulement branché, batterie au-dessus de 50 %", fin = Fin.Inter(branche), actif = donner) {
                MoteurProches.reglerBranche(c, !branche)
                v++
            }
            val limite = remember(v) { MoteurProches.limite(c) }
            Ligne("Limite par jour", fin = Fin.Valeur(if (limite == 0L) "Sans limite" else taille(limite)), actif = donner) {
                MoteurProches.limiteSuivante(c)
                v++
            }
            Explication("Ces réglages serviront dès que l'échange entre téléphones sera là.")
        }
        section("Bilan", cle = "bilan") {
            Ligne("Économisé grâce à vos proches", icone = Icones.DONNEES, fin = Fin.Valeur(MoteurProches.economise(c).let { if (it == 0L) "Rien encore" else taille(it) }))
            val n = MoteurProches.personnes(c)
            Ligne(if (n == 0) "Donné" else "Donné à $n personne${if (n > 1) "s" else ""}", icone = Icones.PARTAGER, fin = Fin.Valeur(MoteurProches.donne(c).let { if (it == 0L) "Rien encore" else taille(it) }))
        }
        section(cle = "comment") {
            Explication(
                "Seuls les fichiers publiés par Sama et Sugu passent, signés : le téléphone vérifie la signature, la version " +
                    "et le fichier entier avant d'installer quoi que ce soit. Une mise à jour n'arrive jamais par un fichier qu'on vous envoie.",
            )
        }
    }
}

/** Un point Sama et ce qu'il propose (maquette i6-point-partage). */
@Composable
fun PagePointSama(nom: String, hote: String, port: Int, nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    val p = remember(hote, port) { Point(nom, hote, port) }
    var catalogue by remember { mutableStateOf<Protocole.Catalogue?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var v by remember { mutableIntStateOf(0) }
    LaunchedEffect(v, reprise) {
        erreur = null
        try {
            catalogue = withContext(Dispatchers.IO) { Protocole.catalogue(c, p) }
            // Le point a donné son nom : on le garde pour la prochaine fois.
            catalogue?.nom?.let { MoteurProches.ajouterPoint(c, Point(it, hote, port)) }
        } catch (_: Exception) {
            erreur = "Le point ne répond pas. Il faut être sur le même réseau (Wi-Fi de l'école, de la mairie…)."
        }
    }
    val cat = catalogue
    val bloque = remember(v) { MoteurProches.bloque(c, p.adresse) }
    PageReglages(titre = "Point Sama", sousTitre = listOfNotNull(cat?.nom ?: nom, hote).joinToString(" · "), retour = nav.retour) {
        if (bloque) {
            section(cle = "bloque") {
                Explication("Vous avez choisi de ne plus rien recevoir de ce point.", LocalBanco.current.lateriteTexte)
                Ligne("Recevoir de nouveau de ce point", fin = Fin.Rien) {
                    MoteurProches.reglerBloque(c, p.adresse, false)
                    v++
                }
            }
            return@PageReglages
        }
        erreur?.let { section(cle = "erreur") { Explication(it, LocalBanco.current.lateriteTexte) } }
        if (cat != null) {
            section("Disponible ici", cle = "paquets") {
                if (cat.paquets.isEmpty()) Explication("Ce point n'a rien à proposer pour l'instant.")
                cat.paquets.forEach { m ->
                    val installee = remember(m.paquet, reprise, v) { Verification.versionInstallee(c, m.paquet) }
                    val etat = Receptions.etats[m.sha256]
                    val fin = when {
                        etat != null && etat.etape in setOf(Receptions.Etape.RECEPTION, Receptions.Etape.VERIFICATION, Receptions.Etape.INSTALLATION) -> Fin.Valeur("En cours")
                        installee != null && installee >= m.version -> Fin.Valeur("À jour")
                        installee != null -> Fin.Bouton("Mettre à jour", plein = true)
                        else -> Fin.Bouton("Recevoir")
                    }
                    Ligne(
                        m.nom,
                        detail = "${taille(m.taille)} · ${if (m.versionNom.isNotBlank()) "version ${m.versionNom} · " else ""}signée par ${Editeurs.nomLisible(m.editeur)}",
                        icone = Icones.APPLI, fin = fin,
                    ) {
                        if (fin is Fin.Bouton || fin == Fin.Valeur("En cours")) {
                            Receptions.lancer(c, Point(cat.nom ?: nom, hote, port), m)
                            nav.aller(Page.Reception(m.sha256))
                        }
                    }
                }
                if (cat.ecartes > 0) {
                    Explication(
                        "${cat.ecartes} fichier${if (cat.ecartes > 1) "s" else ""} écarté${if (cat.ecartes > 1) "s" else ""} : pas de signature valable de Sama ou de Sugu.",
                        LocalBanco.current.lateriteTexte,
                    )
                }
            }
        }
        section(cle = "note") {
            Explication("Les points Sama gardent une copie des applis et des mises à jour pour tout le quartier. Ils ne peuvent rien installer à votre place : c'est vous qui choisissez, et le téléphone vérifie tout.")
        }
    }
}

/** La réception d'un fichier (maquette i6-reception), puis son installation. */
@Composable
fun PageReception(sha: String, nav: Nav) {
    val c = LocalContext.current
    val e = Receptions.etats[sha] ?: run {
        LaunchedEffect(Unit) { nav.retour() }
        return
    }
    val m = e.m
    // Le fichier refusé a son propre écran.
    LaunchedEffect(e.etape) { if (e.etape == Receptions.Etape.REFUSEE) nav.aller(Page.Refuse(sha)) }
    val part = (e.recu.toFloat() / m.taille.coerceAtLeast(1)).coerceIn(0f, 1f)
    val titre = when (e.etape) {
        Receptions.Etape.RECEPTION -> "Réception · ${(part * 100).toInt()} %"
        Receptions.Etape.VERIFICATION -> "Vérification"
        Receptions.Etape.INSTALLATION -> "Installation"
        Receptions.Etape.INSTALLEE -> if (e.versionAvant != null) "Mise à jour faite" else "Installée"
        Receptions.Etape.REFUSEE -> "Fichier refusé"
        Receptions.Etape.ECHEC -> "Réception arrêtée"
    }
    PageReglages(titre = titre, sousTitre = "${m.nom} · depuis ${e.point.nom}", retour = nav.retour) {
        section(cle = "avance") {
            Jauge(part, modifier = Modifier.padding(top = 8.dp))
            val ecoule = (System.currentTimeMillis() - e.debut).coerceAtLeast(1)
            val reste = if (e.recu > 0) ((m.taille - e.recu) * ecoule / e.recu / 60_000).coerceAtLeast(0) else null
            Explication(
                when (e.etape) {
                    Receptions.Etape.RECEPTION -> "${taille(e.recu)} sur ${taille(m.taille)}" + (reste?.let { if (it < 1) " · moins d'une minute" else " · encore $it min" } ?: "") +
                        ". Restez près du point : la réception reprend où elle s'est arrêtée."
                    Receptions.Etape.INSTALLEE -> "${taille(m.taille)} reçus sans toucher à votre forfait."
                    else -> e.message ?: "${taille(e.recu)} sur ${taille(m.taille)}"
                },
            )
        }
        section("Vérifications", cle = "verifs") {
            Ligne("Signature de ${Editeurs.nomLisible(m.editeur)}", detail = "Le fichier vient bien de son éditeur", icone = Icones.BOUCLIER, fin = Fin.Valeur("✓"))
            Ligne(
                if (e.versionAvant != null) "Plus récente que la vôtre" else "Nouvelle appli",
                detail = if (e.versionAvant != null) "Version ${m.versionNom} : remplace la vôtre" else "Elle n'est pas encore sur ce téléphone",
                icone = Icones.MISE_A_JOUR, fin = Fin.Valeur("✓"),
            )
            Ligne(
                "Fichier intact", detail = "Contrôlé à la fin de la réception", icone = Icones.COCHE,
                fin = Fin.Valeur(when (e.intact) { null -> "À venir"; true -> "✓"; false -> "✗" }),
            )
        }
        section(cle = "actions") {
            when (e.etape) {
                Receptions.Etape.ECHEC -> Ligne("Reprendre", icone = Icones.ROTATION, fin = Fin.Bouton("Reprendre", plein = true)) {
                    Receptions.etats.remove(sha)
                    Receptions.lancer(c, e.point, m)
                }
                Receptions.Etape.INSTALLEE -> {
                    val ouvrir = remember { c.packageManager.getLaunchIntentForPackage(m.paquet) }
                    if (ouvrir != null) Ligne("Ouvrir ${m.nom}", icone = Icones.OUVRIR, fin = Fin.Bouton("Ouvrir", plein = true)) {
                        c.startActivity(ouvrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                Receptions.Etape.RECEPTION -> Ligne("Arrêter", icone = Icones.FERMER, fin = Fin.Rien) {
                    e.arret = true
                    e.message = "Réception arrêtée. Elle reprendra où elle s'est arrêtée."
                    e.etape = Receptions.Etape.ECHEC
                }
                else -> {}
            }
        }
    }
}

/** Le fichier refusé (maquette i6-verif-echec) : supprimé, rien d'installé. */
@Composable
fun PageRefuse(sha: String, nav: Nav) {
    val c = LocalContext.current
    val e = Receptions.etats[sha] ?: run {
        LaunchedEffect(Unit) { nav.retour() }
        return
    }
    var v by remember { mutableIntStateOf(0) }
    val source = e.point.adresse
    val bloque = remember(v) { MoteurProches.bloque(c, source) }
    val raison = when {
        !Verification.signature(c, e.m) -> "n'est pas signé par Sama ni par Sugu"
        e.intact == false -> "n'est pas celui que ${Editeurs.nomCourt(e.m.editeur)} a publié"
        else -> "n'est pas plus récent que celui du téléphone"
    }
    PageReglages(titre = "Fichier refusé", sousTitre = "${e.m.nom} reçu de ${e.point.nom} $raison. Sama l'a supprimé.", retour = { nav.retour(); nav.retour() }) {
        section(cle = "choix") {
            Ligne("Ne plus rien recevoir de ${e.point.nom}", icone = Icones.CADENAS, fin = Fin.Inter(bloque)) {
                MoteurProches.reglerBloque(c, source, !bloque)
                v++
            }
            Explication("Rien n'a été installé. Si c'est le téléphone d'un proche, il est peut-être piraté : prévenez-le par un autre moyen.")
        }
        section(cle = "compris") {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Compris",
                    modifier = Modifier.padding(end = 4.dp).clickable(role = Role.Button) {
                        Receptions.etats.remove(sha)
                        nav.retour()
                        nav.retour()
                    }.padding(horizontal = 8.dp, vertical = 12.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = LocalBanco.current.encre),
                )
                Avancer(true, "Compris") {
                    Receptions.etats.remove(sha)
                    nav.retour()
                    nav.retour()
                }
            }
        }
    }
}

/**
 * « Ce n'est pas une mise à jour » (maquette i6-faux-fichier) : Fichiers l'ouvre quand on touche une appli reçue
 * qui se dit mise à jour de Sama, ou qui porte un nom d'appli de Sama. « Supprimer » rend la main à Fichiers.
 */
class FauxFichier : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val nom = intent.getStringExtra("nom").orEmpty()
        val origine = intent.getStringExtra("origine")
        setContent {
            val nuit = androidx.compose.foundation.isSystemInDarkTheme()
            CompositionLocalProvider(LocalBanco provides palette(PaysageEspace.LAGUNE, nuit), LocalNuit provides nuit) {
                PageFauxFichier(nom, origine, fermer = { finish() }) {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun PageFauxFichier(nom: String, origine: String?, fermer: () -> Unit, supprimer: () -> Unit) {
    val c = LocalContext.current
    var signale by remember { mutableStateOf(false) }
    PageReglages(titre = "Ce n'est pas une mise à jour", sousTitre = listOfNotNull(nom, origine).joinToString(" · "), retour = fermer) {
        section(cle = "signaler") {
            Ligne(
                if (signale) "Expéditeur signalé" else "Signaler l'expéditeur",
                detail = if (signale) "Noté dans le bilan du bouclier" else "Anonyme, pour protéger les autres",
                icone = Icones.ALERTE, fin = if (signale) Fin.Valeur("✓") else Fin.Chevron,
            ) {
                if (!signale) {
                    africa.samaos.bouclier.Bouclier.noter(c, "faux-fichier", "Fausse mise à jour", nom)
                    signale = true
                }
            }
            Explication(
                "Les mises à jour de Sama arrivent seules, dans Réglages. Un fichier qu'on vous envoie n'en est jamais une. " +
                    "Si quelqu'un insiste pour « mettre la mise à jour » sur votre téléphone, c'est une arnaque.",
            )
        }
        section(cle = "supprimer") {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Supprimer", modifier = Modifier.padding(end = 4.dp).clickable(role = Role.Button, onClick = supprimer).padding(horizontal = 8.dp, vertical = 12.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = LocalBanco.current.encre),
                )
                Avancer(true, "Supprimer", supprimer)
            }
        }
    }
}
