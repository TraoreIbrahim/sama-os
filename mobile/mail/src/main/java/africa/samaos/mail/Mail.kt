package africa.samaos.mail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.Avatar
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Fab
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.bouclier.Bouclier
import africa.samaos.bouclier.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal sealed interface Vue {
    data object Liste : Vue
    data class Lecture(val compte: String, val dossier: String, val uid: Long) : Vue
    data class Ecrire(val brouillon: Brouillon) : Vue
    data object Ajouter : Vue
    data object Reglages : Vue
    data object Brouillons : Vue
}

/** Ce que l'écran de la boîte garde quand on lit un mail et qu'on revient. */
internal object Ecran {
    var dossier by mutableStateOf("INBOX")
    var filtre by mutableStateOf("")
    var recherche by mutableStateOf("")
}

internal object IconesMail {
    const val MENU = "M4 7h16 M4 12h16 M4 17h16"
    const val TROMBONE = "M8 12.5l6.5-6.5a3 3 0 0 1 4.2 4.2l-8 8a5 5 0 0 1-7-7L12 3.6"
    const val RECEPTION = "M4 13h4l2 3h4l2-3h4 M4 13l2.5-7h11L20 13v6H4z"
    const val ARCHIVES = "M4 5h16v4H4z M5 9v10h14V9 M10 13h4"
    const val ENVELOPPE = "M4 6h16v12H4z M4 7l8 6l8-6"
    const val REPONDRE = "M9 7L4 12l5 5 M4 12h10a6 6 0 0 1 6 6"
    const val REPONDRE_TOUS = "M12 7l-5 5l5 5 M7 7l-5 5l5 5 M7 12h7a6 6 0 0 1 6 6"
    const val TRANSFERER = "M15 7l5 5l-5 5 M20 12H10a6 6 0 0 0-6 6"
    const val ETOILE = "M12 4l2.4 5 5.6.6-4.2 3.8 1.2 5.5L12 16.2 7 18.9l1.2-5.5L4 9.6l5.6-.6z"
}

/** Le Mail de Sama (maquette l2-mail). */
class Mail : ComponentActivity() {
    private var pile by mutableStateOf(listOf<Vue>(Vue.Liste))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Releve.canaux(this)
        lire(intent)
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.MailNuit else Identites.Mail
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) {
                val c = LocalContext.current
                val aller: (Vue) -> Unit = { pile = pile + it }
                val retour: () -> Unit = { if (pile.size > 1) pile = pile.dropLast(1) else finish() }
                BackHandler(enabled = pile.size > 1) { retour() }
                // Sans boîte, on commence par en ajouter une (le mail à écrire attend derrière).
                val sansCompte = remember(Courrier.version, pile) { Comptes.liste(c).isEmpty() }
                if (sansCompte && pile.last() !is Vue.Ajouter) {
                    PageAjouter(premiere = true, retour = null) { pile = pile.toList() }
                    return@AvecIdentite
                }
                when (val v = pile.last()) {
                    Vue.Liste -> PageListe(aller)
                    is Vue.Lecture -> PageLecture(v.compte, v.dossier, v.uid, aller, retour)
                    is Vue.Ecrire -> PageEcrire(v.brouillon, retour)
                    Vue.Ajouter -> PageAjouter(premiere = false, retour = retour) { pile = listOf(Vue.Liste) }
                    Vue.Reglages -> PageReglages(aller, retour)
                    Vue.Brouillons -> PageBrouillons(aller, retour)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    /** Ouvert par une notification (un mail), un lien « mailto: », ou un partage (« Envoyer par mail »). */
    private fun lire(i: Intent?) {
        i ?: return
        when (i.action) {
            "africa.samaos.mail.LIRE" -> {
                val compte = i.getStringExtra("compte") ?: return
                val dossier = i.getStringExtra("dossier") ?: "INBOX"
                Ecran.dossier = dossier
                Comptes.de(this, compte)?.let { Comptes.choisir(this, it) }
                pile = listOf(Vue.Liste, Vue.Lecture(compte, dossier, i.getLongExtra("uid", 0)))
            }
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> i.data?.takeIf { it.scheme == "mailto" }?.let { pile = pile + Vue.Ecrire(depuisMailto(it)) }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val flux = if (i.action == Intent.ACTION_SEND) {
                    @Suppress("DEPRECATION")
                    listOfNotNull(i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                } else {
                    @Suppress("DEPRECATION")
                    i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                }
                val pieces = flux.mapNotNull { copierPiece(this, it) }
                pile = pile + Vue.Ecrire(
                    Brouillon(
                        compte = Comptes.courant(this)?.id.orEmpty(),
                        a = i.getStringArrayExtra(Intent.EXTRA_EMAIL).orEmpty().joinToString(", "),
                        cc = i.getStringArrayExtra(Intent.EXTRA_CC).orEmpty().joinToString(", "),
                        sujet = i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(),
                        texte = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
                        pieces = pieces,
                    ),
                )
            }
        }
    }

    private fun depuisMailto(u: Uri): Brouillon {
        val s = u.toString().removePrefix("mailto:")
        val a = Uri.decode(s.substringBefore('?'))
        val params = s.substringAfter('?', "").split('&').mapNotNull { p ->
            val k = p.substringBefore('=', "").lowercase()
            if (k.isEmpty()) null else k to Uri.decode(p.substringAfter('='))
        }.toMap()
        return Brouillon(
            compte = Comptes.courant(this)?.id.orEmpty(),
            a = listOf(a, params["to"].orEmpty()).filter { it.isNotBlank() }.joinToString(", "),
            cc = params["cc"].orEmpty(),
            sujet = params["subject"].orEmpty(),
            texte = params["body"].orEmpty(),
        )
    }
}

/** Copier un fichier partagé ou choisi dans Mail : il pourra partir plus tard, même sans le droit de le relire. */
internal fun copierPiece(c: Context, u: Uri): String? = try {
    val nom = c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { k -> if (k.moveToFirst()) k.getString(0) else null }
        ?: u.lastPathSegment ?: "piece"
    val d = File(c.filesDir, "joints/${UUID.randomUUID()}").apply { mkdirs() }
    val f = File(d, Courrier.nomSur(nom))
    c.contentResolver.openInputStream(u)!!.use { e -> f.outputStream().use { e.copyTo(it) } }
    f.path
} catch (_: Exception) {
    null
}

// ——— Petits outils ———

internal fun dateCourte(t: Long): String {
    if (t == 0L) return ""
    val z = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(t).atZone(z)
    val auj = LocalDate.now(z)
    return when {
        d.toLocalDate() == auj -> d.format(DateTimeFormatter.ofPattern("HH:mm"))
        d.toLocalDate() == auj.minusDays(1) -> "Hier"
        d.toLocalDate().isAfter(auj.minusDays(6)) -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
        d.year == auj.year -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
        else -> d.format(DateTimeFormatter.ofPattern("d/MM/yyyy"))
    }
}

internal fun dateLongue(t: Long): String =
    Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRENCH))

internal fun taille(o: Long): String = when {
    o >= 1_000_000 -> String.format(Locale.FRENCH, "%.1f Mo", o / 1e6)
    else -> "${(o / 1000).coerceAtLeast(1)} Ko"
}

/** Les adresses des contacts du téléphone (pour savoir si un expéditeur est connu, et proposer des destinataires). */
internal object Carnet {
    @Volatile private var connues: Set<String>? = null

    fun permis(c: Context) = c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun connue(c: Context, adresse: String): Boolean {
        if (!permis(c)) return false
        val l = connues ?: try {
            c.contentResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null)?.use { k ->
                buildSet { while (k.moveToNext()) k.getString(0)?.lowercase()?.let { add(it) } }
            }.orEmpty()
        } catch (_: Exception) {
            emptySet()
        }.also { connues = it }
        return adresse.lowercase() in l
    }

    fun chercher(c: Context, terme: String): List<Pair<String, String>> {
        if (!permis(c) || terme.length < 2) return emptyList()
        return try {
            val u = ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI.buildUpon().appendPath(terme).build()
            c.contentResolver.query(u, arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null)?.use { k ->
                buildList { while (k.moveToNext() && size < 5) add((k.getString(0) ?: "") to k.getString(1)) }
            }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Ce que le bouclier pense d'un mail (expéditeur, objet, texte, liens, pièces jointes). */
internal fun verdictDe(c: Context, l: Lettre, pieces: List<Piece> = emptyList()): Verdict? {
    val corps = l.texte ?: l.html?.let { Courrier.texteDe(it) } ?: l.extrait
    Bouclier.mail(l.de, l.deNom, l.sujet, corps + " " + (l.html ?: ""), Carnet.connue(c, l.de))?.let { return it }
    l.html?.let { h ->
        Regex("(?is)<a\\s[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>").findAll(h).forEach { m ->
            val affiche = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            Bouclier.lienTrompeur(affiche, m.groupValues[1])?.let { return it }
        }
    }
    return pieces.firstNotNullOfOrNull { Bouclier.pieceJointe(it.nom) }
}

/** Un champ de saisie de Mail : l'indication dedans, le texte en dessous. */
@Composable
internal fun ChampTexte(
    valeur: String,
    indication: String,
    changer: (String) -> Unit,
    modifier: Modifier = Modifier,
    clavier: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    uneLigne: Boolean = true,
) {
    val a = LocalIdentite.current
    Box(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(a.champ).padding(horizontal = 16.dp, vertical = 15.dp)) {
        if (valeur.isEmpty()) BasicText(indication, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2))
        BasicTextField(
            valeur, changer, singleLine = uneLigne, modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre),
            cursorBrush = SolidColor(a.accent),
            keyboardOptions = KeyboardOptions(keyboardType = clavier, autoCorrectEnabled = clavier == KeyboardType.Text),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}

@Composable
internal fun Note(texte: String, couleur: androidx.compose.ui.graphics.Color? = null) {
    val a = LocalIdentite.current
    BasicText(texte, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = couleur ?: a.encre2))
}

// ——— La boîte (maquette l2-mail) ———

@Composable
private fun PageListe(aller: (Vue) -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val version = Courrier.version
    val compte = remember(version) { Comptes.courant(c) } ?: return
    var liste by remember { mutableStateOf<List<Lettre>>(emptyList()) }
    var premieres by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var verdicts by remember { mutableStateOf<Map<Long, Verdict>>(emptyMap()) }
    var nonLus by remember { mutableStateOf(0) }
    var dossiers by remember { mutableStateOf<List<Dossier>>(emptyList()) }
    var feuille by remember { mutableStateOf(false) }
    val dossier = Ecran.dossier
    LaunchedEffect(version, compte.id, dossier, Ecran.filtre, Ecran.recherche) {
        withContext(Dispatchers.IO) {
            val l = Base.lettres(c, compte.id, dossier, Ecran.filtre, Ecran.recherche)
            premieres = Base.premieresPieces(c, compte.id, dossier)
            verdicts = l.mapNotNull { x -> verdictDe(c, x, if (x.pj) Base.pieces(c, x.compte, x.dossier, x.uid) else emptyList())?.let { x.uid to it } }.toMap()
            nonLus = Base.nonLus(c, compte.id, dossier)
            dossiers = Base.dossiers(c, compte.id)
            liste = l
        }
    }
    // Relever à l'ouverture, puis toutes les deux minutes tant que la boîte est à l'écran.
    LaunchedEffect(compte.id, dossier) {
        while (true) {
            withContext(Dispatchers.IO) {
                try {
                    Courrier.envoyerEnAttente(c)
                    Courrier.relever(c, compte, dossier)
                } catch (_: Exception) {
                }
            }
            delay(120_000)
        }
    }
    LaunchedEffect(Courrier.dernierMessage) {
        if (Courrier.dernierMessage != null) {
            delay(4000)
            Courrier.dernierMessage = null
        }
    }
    val d = dossiers.firstOrNull { it.nom == dossier }
    Box(Modifier.fillMaxSize()) {
        EcranAppli {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(a.champ).padding(start = 4.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoutonAppli(IconesMail.MENU, "Dossiers", taille = 40.dp) { feuille = true }
                    Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        if (Ecran.recherche.isEmpty()) BasicText("Rechercher dans le courrier", maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2))
                        BasicTextField(
                            Ecran.recherche, { Ecran.recherche = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
                        )
                    }
                    if (Ecran.recherche.isNotEmpty()) BoutonAppli(Icones.FERMER, "Effacer la recherche", taille = 36.dp) { Ecran.recherche = "" }
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClickLabel = "Comptes et dossiers", role = Role.Button) { feuille = true }) {
                        Avatar(compte.nom.ifBlank { compte.adresse }, 34.dp)
                    }
                }
            }
            if (d != null && d.role != "reception") Rub(Courrier.nomLisible(d))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuceFiltre(if (d == null || d.role == "reception") "Principale" else "Tous", Ecran.filtre == "") { Ecran.filtre = "" }
                PuceFiltre(if (nonLus > 0) "Non lus · $nonLus" else "Non lus", Ecran.filtre == "nonlus") { Ecran.filtre = "nonlus" }
                PuceFiltre("Pièces jointes", Ecran.filtre == "pj") { Ecran.filtre = "pj" }
            }
            val etat = Courrier.dernierMessage ?: Courrier.erreurs[compte.id]?.let { "Pas de relève : $it" }
            if (etat != null) Note(etat, if (Courrier.dernierMessage == null) a.accentTexte else null)
            LazyColumn(Modifier.fillMaxSize()) {
                if (liste.isEmpty()) item {
                    Note(
                        when {
                            Courrier.releves[compte.id] == true -> "Relève du courrier…"
                            Ecran.recherche.isNotBlank() -> "Aucun mail ne correspond."
                            Ecran.filtre == "nonlus" -> "Tout est lu."
                            else -> "Aucun mail ici."
                        },
                    )
                }
                items(liste, key = { it.uid }) { l -> LigneMail(l, premieres[l.uid], verdicts[l.uid]) { aller(Vue.Lecture(l.compte, l.dossier, l.uid)) } }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
        Fab("Écrire", "M4 20h4L19 9l-4-4L4 16z M13.5 6.5l4 4") {
            aller(Vue.Ecrire(Brouillon(compte = compte.id, a = "", sujet = "", texte = "")))
        }
    }
    if (feuille) FeuilleDossiers(compte, dossiers, aller) { feuille = false }
}

@Composable
private fun LigneMail(l: Lettre, piece: String?, verdict: Verdict?, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val gras = !l.lu
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Lire", role = Role.Button, onClick = ouvrir).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(l.nomAffiche, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    l.nomAffiche, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = if (gras) FontWeight.Bold else FontWeight.Medium, fontSize = 17.sp, lineHeight = 23.sp, color = a.encre),
                )
                if (l.etoile) IconeTrait(IconesMail.ETOILE, 14.dp, a.accentTexte)
                BasicText(
                    dateCourte(l.date),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = if (gras) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, color = if (gras) a.accentTexte else a.encre2),
                )
            }
            BasicText(
                l.sujet.ifBlank { "(sans objet)" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontWeight = if (gras) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.sp, color = a.encre),
            )
            if (l.extrait.isNotBlank()) BasicText(l.extrait, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 19.sp, color = a.encre2))
            if (verdict != null || piece != null) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (verdict != null) Pastille(Icones.ALERTE, verdict.titre, a.accentTexte, a.voile)
                if (piece != null) Pastille(IconesMail.TROMBONE, piece, a.encre, null)
            }
        }
    }
}

@Composable
internal fun Pastille(icone: String, texte: String, couleur: androidx.compose.ui.graphics.Color, fond: androidx.compose.ui.graphics.Color?) {
    val a = LocalIdentite.current
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).then(if (fond != null) Modifier.background(fond) else Modifier.background(a.champ)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconeTrait(icone, 14.dp, couleur)
        BasicText(texte, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = couleur))
    }
}

internal fun iconeDossier(role: String) = when (role) {
    "reception" -> IconesMail.RECEPTION
    "envoyes" -> Icones.ENVOYER
    "brouillons" -> Icones.CRAYON
    "corbeille" -> Icones.CORBEILLE
    "indesirables" -> Icones.ALERTE
    "archives" -> IconesMail.ARCHIVES
    else -> Icones.DOSSIER
}

/** Les dossiers de la boîte, les autres boîtes, et les réglages. */
@Composable
private fun FeuilleDossiers(compte: Compte, dossiers: List<Dossier>, aller: (Vue) -> Unit, fermer: () -> Unit) {
    val c = LocalContext.current
    val comptes = remember { Comptes.liste(c) }
    val brouillons = remember { Base.brouillons(c).size }
    FeuilleAppli(fermer, titre = compte.adresse) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            val l = dossiers.ifEmpty { listOf(Dossier(compte.id, "INBOX", "reception", 0)) }
            l.forEach { d ->
                ActionFeuille(iconeDossier(d.role), Courrier.nomLisible(d), second = if (d.nonLus > 0 && d.role != "envoyes" && d.role != "corbeille") "${d.nonLus} non lu${if (d.nonLus > 1) "s" else ""}" else null) {
                    Ecran.dossier = d.nom
                    Ecran.filtre = ""
                    fermer()
                }
            }
            if (brouillons > 0) ActionFeuille(Icones.CRAYON, "Sur ce téléphone", second = "$brouillons brouillon${if (brouillons > 1) "s" else ""} ou envoi${if (brouillons > 1) "s" else ""} en attente") {
                fermer()
                aller(Vue.Brouillons)
            }
            comptes.filter { it.id != compte.id }.forEach { autre ->
                ActionFeuille(IconesMail.ENVELOPPE, autre.adresse, second = "Passer à cette boîte") {
                    Comptes.choisir(c, autre)
                    Ecran.dossier = "INBOX"
                    Courrier.changer()
                    fermer()
                }
            }
            ActionFeuille(Icones.PLUS, "Ajouter une boîte mail") {
                fermer()
                aller(Vue.Ajouter)
            }
            ActionFeuille(Icones.REGLAGES, "Réglages de Mail") {
                fermer()
                aller(Vue.Reglages)
            }
        }
    }
}
