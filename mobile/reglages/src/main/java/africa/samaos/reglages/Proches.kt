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
import africa.samaos.proches.Partage
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
    // ——— La fenêtre ouverte aux proches : c'est Sugu qui l'ouvre, 10 minutes, à la demande ———
    fun ouvertJusqua(c: Context) = Partage.ouvertJusqua(c)
    fun ouvert(c: Context) = System.currentTimeMillis() < ouvertJusqua(c)
    fun minutes(c: Context) = (ouvertJusqua(c) - System.currentTimeMillis()) / 60_000 + 1

    // ——— Donner (lu par Sugu, qui sert les applis aux proches) ———
    fun donner(c: Context) = Partage.donner(c)
    fun reglerDonner(c: Context, oui: Boolean) = Partage.reglerDon(c, donner = oui)
    fun brancheSeulement(c: Context) = Partage.brancheSeulement(c)
    fun reglerBranche(c: Context, oui: Boolean) = Partage.reglerDon(c, branche = oui)
    val LIMITES = listOf(500_000_000L, 1_000_000_000L, 2_000_000_000L, 0L)
    fun limite(c: Context) = Partage.limite(c)
    fun limiteSuivante(c: Context) = Partage.reglerDon(c, limite = LIMITES[(LIMITES.indexOf(limite(c)) + 1) % LIMITES.size])

    // ——— Le bilan (compté par Sugu, dans les réglages partagés) ———
    fun economise(c: Context) = Partage.economise(c)
    fun donne(c: Context) = Partage.donne(c)
    fun personnes(c: Context) = Partage.personnes(c)

    fun resume(c: Context): String = when {
        ouvert(c) -> "Ouvert à vos proches · encore ${minutes(c)} min"
        economise(c) > 0 -> taille(economise(c)) + " économisés grâce à vos proches"
        else -> "Recevoir sans data, entre proches"
    }

    /** Ouvrir Sugu sur un point Sama : c'est Sugu qui reçoit, vérifie et installe. */
    fun ouvrirSugu(c: Context, p: Point?) {
        try {
            val i = Intent("africa.samaos.action.SUGU_AUTOUR").setPackage("africa.samaos.sugu").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (p != null) i.putExtra("nom", p.nom).putExtra("hote", p.hote).putExtra("port", p.port)
            c.startActivity(i)
        } catch (_: Exception) {
        }
    }

    /** Ouvrir Sugu sur « Chez vos proches », en ouvrant le téléphone à ses proches pour 10 minutes. */
    fun ouvrirAuxProches(c: Context) {
        try {
            c.startActivity(Intent("africa.samaos.action.SUGU_PROCHES").setPackage("africa.samaos.sugu").putExtra("ouvrir", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

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
    val ouvert = remember(v) { MoteurProches.ouvert(c) }
    PageReglages(titre = "Proche en proche", sousTitre = "Recevoir et donner sans data, entre proches", retour = nav.retour) {
        section("Recevoir d'un point Sama", cle = "points") {
            val connus = remember(v) { Partage.points(c) }
            val tous = (trouves + connus).distinctBy { it.adresse }
            tous.forEach { p ->
                Ligne(p.nom, detail = "${p.adresse} · ouvrir dans Sugu", icone = Icones.TELECHARGE) { MoteurProches.ouvrirSugu(c, p) }
            }
            if (tous.isEmpty()) Explication("Aucun point Sama sur ce réseau. Les points sont des ordinateurs Sama d'écoles, de mairies ou de cybercafés.")
            if (ajout) {
                Champ(saisie, "Adresse, par exemple 192.168.1.20", { saisie = it.trim().take(40) }, clavier = KeyboardType.Uri)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BoutonTexte("Annuler") { ajout = false }
                    Partage.lireAdresse(saisie)?.let { p ->
                        BoutonTexte("Ajouter") {
                            Partage.ajouterPoint(c, p)
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
        section("Entre proches", cle = "recevoir") {
            val reconnus = remember(v) { MoteurReconnus.liste(c) }
            if (reconnus.isNotEmpty()) Ligne(
                if (ouvert) "Ouvert à vos proches" else "Ouvrir à mes proches",
                detail = if (ouvert) "Encore ${MoteurProches.minutes(c)} min · dans Sugu" else "Éteint · 10 minutes, à votre demande, dans Sugu",
                icone = Icones.PROXIMITE,
                fin = Fin.Bouton(if (ouvert) "Voir" else "Ouvrir", plein = !ouvert),
            ) { MoteurProches.ouvrirAuxProches(c) }
            Ligne("Reconnaître un proche", detail = "Chacun scanne le code de l'autre, téléphones côte à côte", icone = Icones.QR) { nav.aller(Page.Reconnaitre()) }
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
            Explication("Seuls vos proches reconnus des deux côtés peuvent voir votre téléphone, et seulement quand vous l'ouvrez. Rien n'est diffusé : ni nom, ni numéro.")
        }
        section("Donner", cle = "donner") {
            val donner = remember(v) { MoteurProches.donner(c) }
            Ligne("Partager avec mes proches", detail = "Les applis et mises à jour de Sugu que vous avez déjà", fin = Fin.Inter(donner)) {
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
            Explication("Quand vous envoyez vous-même une appli à un proche, elle part même si le partage est coupé.")
        }
        section("Bilan", cle = "bilan") {
            Ligne("Économisé sans data", icone = Icones.DONNEES, fin = Fin.Valeur(MoteurProches.economise(c).let { if (it == 0L) "Rien encore" else taille(it) }))
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
