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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import africa.samaos.banco.appli.BarreAvis
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
    data object Boites : Vue
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
    /** On arrive dans la réception ; « ‹ Boîtes » mène aux dossiers et aux autres adresses. */
    private var pile by mutableStateOf(listOf(Vue.Boites, Vue.Liste))

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
                Box(Modifier.fillMaxSize().background(LocalIdentite.current.fond)) {
                    // Une page arrive de la droite ; un mail à écrire monte du bas ; le retour fait l'inverse.
                    AnimatedContent(
                        pile,
                        transitionSpec = {
                            val avance = targetState.size >= initialState.size
                            val vertical = (if (avance) targetState.last() else initialState.last()) is Vue.Ecrire
                            when {
                                vertical && avance -> (slideInVertically(spring(dampingRatio = 0.9f, stiffness = 420f)) { it } + fadeIn(tween(120))) togetherWith fadeOut(tween(260, delayMillis = 100))
                                vertical -> fadeIn(tween(160)) togetherWith (slideOutVertically(tween(260, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(200, delayMillis = 80)))
                                avance -> (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 480f)) { it } + fadeIn(tween(150))) togetherWith (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(240)))
                                else -> (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(250, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(200, delayMillis = 60)))
                            }.apply { targetContentZIndex = if (avance) targetState.size.toFloat() else -1f }
                        },
                        contentKey = { it.size to it.last() },
                        label = "page",
                    ) { p ->
                        Box(Modifier.fillMaxSize().background(LocalIdentite.current.fond)) {
                            when (val v = p.last()) {
                                Vue.Boites -> PageBoites(aller)
                                Vue.Liste -> PageBoite(aller, retour)
                                is Vue.Lecture -> PageLecture(v.compte, v.dossier, v.uid, aller, retour)
                                is Vue.Ecrire -> PageEcrire(v.brouillon, retour)
                                Vue.Ajouter -> PageAjouter(premiere = false, retour = retour) { pile = listOf(Vue.Boites, Vue.Liste) }
                                Vue.Reglages -> PageReglages(aller, retour)
                                Vue.Brouillons -> PageBrouillons(aller, retour)
                            }
                        }
                    }
                    BarreAvis(Modifier.align(Alignment.BottomCenter), bas = 84.dp)
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
                pile = listOf(Vue.Boites, Vue.Liste, Vue.Lecture(compte, dossier, i.getLongExtra("uid", 0)))
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

internal fun iconeDossier(role: String) = when (role) {
    "reception" -> IconesMail.RECEPTION
    "envoyes" -> Icones.ENVOYER
    "brouillons" -> Icones.CRAYON
    "corbeille" -> Icones.CORBEILLE
    "indesirables" -> Icones.ALERTE
    "archives" -> IconesMail.ARCHIVES
    else -> Icones.DOSSIER
}
