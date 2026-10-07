package africa.samaos.fichiers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.Filet
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.NavAppli
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import africa.samaos.banco.appli.AvecIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Vue {
    data object Parcourir : Vue
    data object Nettoyer : Vue
    data class Dossier(val chemin: String) : Vue
    data class Sorte(val cat: Categorie) : Vue
    data object Recus : Vue
    data object Corbeille : Vue
    data object Recherche : Vue
}

internal object Ic {
    const val BALAI = "M15 3l-4 8.5 M7 11.5h9l1 3l-1.5 6.5H7.5L6 14.5z M10 21v-3.5 M13 21v-3.5"
    const val TRI = "M7 4v16 M4 17l3 3l3-3 M17 20V4 M14 7l3-3l3 3"
    const val SD = "M7 3h8l4 4v14H7z M10 3v4 M13 3v4"
    const val AUDIO = "M9 18V5l11-2v13 M9 18a3 3 0 1 1-3-3a3 3 0 0 1 3 3z M20 16a3 3 0 1 1-3-3a3 3 0 0 1 3 3z"
    const val DOC = "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M14 3v5h5 M9 13h6 M9 17h4"
    const val TELEPHONE = "M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M10.5 18h3"
    const val NOUVEAU_DOSSIER = "M4 6.5A1.5 1.5 0 0 1 5.5 5H10l2 2h6.5A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z M12 10.5v5 M9.5 13h5"
    const val WIFI = "M5 10a10 10 0 0 1 14 0 M8 13.5a5.5 5.5 0 0 1 8 0 M12 17.5h.01"
}

fun iconeDe(g: Genre) = when (g) {
    Genre.DOSSIER -> Icones.DOSSIER
    Genre.IMAGE -> Icones.IMAGE
    Genre.VIDEO -> Icones.VIDEO
    Genre.AUDIO -> Ic.AUDIO
    Genre.APPLI -> Icones.APPLI
    else -> Ic.DOC
}

/** Les gestes communs à tous les écrans : aller, ouvrir, les options d'un fichier, un message en bas. */
class Actions(
    val aller: (Vue) -> Unit,
    val retour: () -> Unit,
    val ouvrir: (Fichier) -> Unit,
    val options: (Fichier) -> Unit,
    val dire: (String, (() -> Unit)?) -> Unit,
    val onglet: (Int) -> Unit,
    val version: Int,
)

/** Les deux onglets, en bas des écrans Parcourir et Nettoyer (les feuilles passent par-dessus). */
@Composable
fun Onglets(courant: Int) {
    val x = LocalActions.current
    NavAppli(listOf("Parcourir" to Icones.DOSSIER, "Nettoyer" to Ic.BALAI), courant, x.onglet)
}

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions") }

/** Fichiers de Sama (maquettes l2-fic). */
class Fichiers : ComponentActivity() {
    private val pile = mutableStateListOf<Vue>(Vue.Parcourir)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ouvert sur les reçus (depuis Griot : « Téléchargements »).
        if (intent?.getStringExtra("vue") == "recus") pile.add(Vue.Recus)
        if (intent?.getStringExtra("vue") == "nettoyer") {
            pile.clear()
            pile.add(Vue.Nettoyer)
        }
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.FichiersNuit else Identites.Fichiers
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) { Racine(pile) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("vue") == "recus" && pile.last() != Vue.Recus) pile.add(Vue.Recus)
        if (intent.getStringExtra("vue") == "nettoyer") {
            pile.clear()
            pile.add(Vue.Nettoyer)
        }
    }
}

private fun prefs(c: Context) = c.getSharedPreferences("fichiers", Context.MODE_PRIVATE)
fun cachesVisibles(c: Context) = prefs(c).getBoolean("caches", false)

fun uriDe(c: Context, chemin: String): Uri = FileProvider.getUriForFile(c, "africa.samaos.fichiers.partage", File(chemin))

@Composable
private fun Racine(pile: SnapshotStateList<Vue>) {
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf<Fichier?>(null) }
    var renommer by remember { mutableStateOf<Fichier?>(null) }
    var appli by remember { mutableStateOf<Fichier?>(null) }
    var archive by remember { mutableStateOf<Fichier?>(null) }
    var message by remember { mutableStateOf<Pair<String, (() -> Unit)?>?>(null) }
    // Une appli reçue qui se dit mise à jour de Sama : l'écran des Réglages le dit, et « Supprimer » revient ici.
    var faux by remember { mutableStateOf<Fichier?>(null) }
    val fauxFichier = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val f = faux
        faux = null
        if (r.resultCode == android.app.Activity.RESULT_OK && f != null) portee.launch {
            withContext(Dispatchers.IO) { Stockage.jeter(c, f) }
            version++
            message = "« ${f.nom} » est dans la corbeille" to null
        }
    }
    val vue = pile.last()
    val actions = Actions(
        aller = { pile.add(it) },
        retour = { if (pile.size > 1) pile.removeAt(pile.lastIndex) },
        ouvrir = { f ->
            when {
                f.dossier -> pile.add(Vue.Dossier(f.chemin))
                f.genre == Genre.APPLI && seDitMiseAJour(c, f) -> {
                    faux = f
                    try {
                        fauxFichier.launch(
                            Intent().setClassName("africa.samaos.reglages", "africa.samaos.reglages.FauxFichier")
                                .putExtra("nom", f.nom).putExtra("origine", origineDe(f)),
                        )
                    } catch (_: Exception) {
                        faux = null
                        appli = f
                    }
                }
                f.genre == Genre.APPLI -> appli = f
                f.genre == Genre.ARCHIVE && f.extension == "zip" -> archive = f
                f.genre == Genre.ARCHIVE -> message = "Sama n'ouvre que les archives .zip pour l'instant" to null
                else -> ouvrirAvec(c, f, choisir = false) { message = it to null }
            }
        },
        options = { options = it },
        dire = { t, annuler -> message = t to annuler },
        onglet = {
            pile.clear()
            pile.add(if (it == 0) Vue.Parcourir else Vue.Nettoyer)
        },
        version = version,
    )
    BackHandler(enabled = pile.size > 1) { actions.retour() }
    CompositionLocalProvider(LocalActions provides actions) {
        Box(Modifier.fillMaxSize()) {
            when (vue) {
                Vue.Parcourir -> Accueil()
                Vue.Nettoyer -> Nettoyer()
                is Vue.Dossier -> Dossier(File(vue.chemin))
                is Vue.Sorte -> Sorte(vue.cat)
                Vue.Recus -> Recus()
                Vue.Corbeille -> Corbeille()
                Vue.Recherche -> Recherche()
            }
            message?.let { (texte, annuler) ->
                LaunchedEffect(texte) {
                    delay(5000)
                    message = null
                }
                Bandeau(texte, annuler?.let { a -> { a(); message = null } }, Modifier.align(Alignment.BottomCenter))
            }
            options?.let { f ->
                FeuilleAppli(fermer = { options = null }, titre = f.nom) {
                    val details = listOfNotNull(if (f.dossier) "${f.enfants} éléments" else taille(f.taille), quand(f.date), File(f.chemin).parentFile?.let { Stockage.nomDossier(it) })
                    BasicText(details.joinToString(" · "), modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = LocalIdentite.current.encre2))
                    if (!f.dossier) {
                        ActionFeuille(Icones.OUVRIR, "Ouvrir avec…") {
                            options = null
                            ouvrirAvec(c, f, choisir = true) { message = it to null }
                        }
                        ActionFeuille(Icones.PARTAGER, "Partager") {
                            options = null
                            partager(c, f)
                        }
                    }
                    ActionFeuille(Icones.CRAYON, "Renommer") {
                        options = null
                        renommer = f
                    }
                    ActionFeuille(Icones.CORBEILLE, "Mettre à la corbeille", danger = true, second = "Vous pourrez le remettre pendant 30 jours") {
                        options = null
                        portee.launch {
                            val ids = withContext(Dispatchers.IO) { Stockage.jeter(c, f) }
                            version++
                            message = if (ids.isEmpty()) "Impossible de mettre « ${f.nom} » à la corbeille" to null
                            else "« ${f.nom} » est dans la corbeille" to {
                                portee.launch {
                                    withContext(Dispatchers.IO) { ids.forEach { Stockage.remettre(c, it) } }
                                    version++
                                }
                                Unit
                            }
                        }
                    }
                }
            }
            archive?.let { f ->
                FeuilleArchive(f, fermer = { archive = null }, ouvrirDossier = { d ->
                    version++
                    pile.add(Vue.Dossier(d.path))
                }) { message = it to null }
            }
            renommer?.let { f ->
                Renommer(f, fermer = { renommer = null }) { nom ->
                    renommer = null
                    portee.launch {
                        val ok = withContext(Dispatchers.IO) { Stockage.renommer(c, f, nom) }
                        version++
                        message = (if (ok) "Renommé en « $nom »" else "Ce nom est déjà pris ou n'est pas permis") to null
                    }
                }
            }
            appli?.let { f ->
                // Une appli reçue est le premier moyen des arnaqueurs pour prendre la main sur un téléphone.
                DialogueAppli(
                    "Installer une appli reçue ?",
                    "Une appli envoyée par message ou téléchargée sur un site peut lire vos codes et vider votre compte. " +
                        "Installez-la seulement si vous l'avez cherchée vous-même. Personne de sérieux ne vous demandera d'installer une appli pour vous payer ou vous aider.",
                    fermer = { appli = null },
                ) {
                    BoutonTexteAppli("Installer quand même", style = 't') {
                        appli = null
                        ouvrirAvec(c, f, choisir = false) { message = it to null }
                    }
                    BoutonTexteAppli("Ne pas installer") { appli = null }
                }
            }
        }
    }
}

/**
 * Une appli reçue qui se fait passer pour une mise à jour (« Sama_Mise_a_jour_1.3.apk ») ou pour une appli de Sama :
 * les mises à jour de Sama n'arrivent jamais par un fichier (innovation 6, maquette i6-faux-fichier).
 */
fun seDitMiseAJour(c: Context, f: Fichier): Boolean {
    val nom = java.text.Normalizer.normalize(f.nom.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    if (Regex("(mise.?a.?jour|update|upgrade|firmware|\\bmaj\\b|_maj|maj_)").containsMatchIn(nom)) return true
    val paquet = try {
        c.packageManager.getPackageArchiveInfo(f.chemin, 0)?.packageName
    } catch (_: Exception) {
        null
    }
    return paquet?.startsWith("africa.samaos.") == true
}

/** D'où vient un fichier, d'après son dossier : « reçu par Bluetooth », « téléchargé »… */
fun origineDe(f: Fichier): String? {
    val ch = f.chemin.lowercase()
    return when {
        "/bluetooth" in ch -> "reçu par Bluetooth"
        "whatsapp" in ch || "telegram" in ch -> "reçu par message"
        "/download" in ch -> f.source ?: "téléchargé"
        else -> f.source
    }
}

fun ouvrirAvec(c: Context, f: Fichier, choisir: Boolean, erreur: (String) -> Unit) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uriDe(c, f.chemin), f.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        c.startActivity(if (choisir) Intent.createChooser(i, "Ouvrir avec") else i)
    } catch (_: ActivityNotFoundException) {
        erreur("Aucune appli ne sait ouvrir ce fichier")
    }
}

fun partager(c: Context, f: Fichier) {
    val i = Intent(Intent.ACTION_SEND).setType(f.mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uriDe(c, f.chemin)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(i, "Partager « ${f.nom} »"))
}

@Composable
private fun Bandeau(texte: String, annuler: (() -> Unit)?, modifier: Modifier) {
    val a = LocalIdentite.current
    Row(
        modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 160.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(a.encre)
            .padding(start = 18.dp, end = 6.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(texte, modifier = Modifier.weight(1f).padding(vertical = 10.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.fond))
        if (annuler != null) {
            Box(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClickLabel = "Annuler", role = Role.Button, onClick = annuler).padding(horizontal = 14.dp, vertical = 12.dp)) {
                BasicText("Annuler", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (a.sombre) a.surAccent else Color(0xFFE6DAC2)))
            }
        }
    }
}

@Composable
private fun Renommer(f: Fichier, fermer: () -> Unit, valider: (String) -> Unit) {
    val a = LocalIdentite.current
    // Le nom est sélectionné sans l'extension, pour taper le nouveau tout de suite.
    val base = if (f.dossier || !f.nom.contains('.')) f.nom.length else f.nom.lastIndexOf('.')
    var champ by remember { mutableStateOf(TextFieldValue(f.nom, TextRange(0, base))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DialogueAppli("Renommer", null, fermer = fermer, contenu = {
        BasicTextField(
            champ, { champ = it }, singleLine = true,
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(a.champ).padding(14.dp).focusRequester(focus).semantics { contentDescription = "Nouveau nom" },
        )
    }) {
        BoutonTexteAppli("Annuler", style = 't', onClick = fermer)
        BoutonTexteAppli("Renommer", actif = champ.text.isNotBlank() && champ.text != f.nom) { valider(champ.text.trim()) }
    }
}

// ——— Les vignettes ———

private val vignettes = LruCache<String, ImageBitmap>(300)

private fun charger(f: Fichier): ImageBitmap? = try {
    val s = Size(192, 192)
    when (f.genre) {
        Genre.IMAGE -> ThumbnailUtils.createImageThumbnail(File(f.chemin), s, null)
        Genre.VIDEO -> ThumbnailUtils.createVideoThumbnail(File(f.chemin), s, null)
        else -> null
    }?.asImageBitmap()?.also { vignettes.put(f.chemin, it) }
} catch (_: Exception) {
    null
}

@Composable
fun Vignette(f: Fichier, taille: Dp = 44.dp, coin: Dp = 12.dp, modifier: Modifier = Modifier.size(taille)) {
    val a = LocalIdentite.current
    val g = f.genre
    if (g == Genre.IMAGE || g == Genre.VIDEO) {
        val image by produceState(vignettes.get(f.chemin), f.chemin) {
            value = vignettes.get(f.chemin)
            if (value == null) value = withContext(Dispatchers.IO) { charger(f) }
        }
        image?.let {
            Box(modifier.clip(RoundedCornerShape(coin))) {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (g == Genre.VIDEO) Box(Modifier.align(Alignment.BottomStart).padding(4.dp).size(18.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    IconeTrait(Icones.LECTURE, 10.dp, Color.White)
                }
            }
            return
        }
    }
    val e = etiquette(g)
    Box(modifier.clip(RoundedCornerShape(coin)).background(e?.second ?: a.voile), contentAlignment = Alignment.Center) {
        if (e != null) BasicText(e.first, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White))
        else IconeTrait(iconeDe(g), 22.dp, a.accentTexte)
    }
}

/** Une ligne de fichier : vignette, nom, « taille · date » (ou ce qu'on donne), bouton d'options. */
@Composable
fun LigneFichier(f: Fichier, second: String? = null, couleurSecond: Color? = null) {
    val x = LocalActions.current
    LigneAppli(
        f.nom,
        second ?: if (f.dossier) (if (f.enfants == 0) "vide" else if (f.enfants == 1) "1 élément" else "${f.enfants} éléments") else "${taille(f.taille)} · ${quand(f.date)}",
        couleurSecond = couleurSecond,
        debut = { Vignette(f) },
        fin = { if (f.dossier) IconeTrait(Icones.CHEVRON, 20.dp, LocalIdentite.current.encre2) else BoutonAppli(Icones.OPTIONS, "Options du fichier") { x.options(f) } },
        surAppuiLong = { x.options(f) },
    ) { x.ouvrir(f) }
}

// ——— L'accueil (maquette l2-fic-accueil) ———

private val COULEURS = listOf(Color(0xFF6E5038), Color(0xFFA8693C), Color(0xFFC98F62), Color(0xFFB89878), Color(0xFFD9C6B0))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Accueil() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val etat by produceState<EtatStockage?>(null, x.version) { value = withContext(Dispatchers.IO) { Stockage.etat(c) } }
    val recuperable by produceState(0L, x.version) {
        value = withContext(Dispatchers.IO) { Stockage.vieillesVideos(c).sumOf { it.taille } + (Stockage.etat(c)?.caches ?: 0) }
    }
    val jetes by produceState(0, x.version) { value = withContext(Dispatchers.IO) { Stockage.corbeille(c).size } }
    var menu by remember { mutableStateOf(false) }
    EcranAppli {
        Tete("Fichiers") {
            BoutonAppli(Icones.RECHERCHE, "Rechercher") { x.aller(Vue.Recherche) }
            BoutonAppli(Icones.OPTIONS, "Plus d'options") { menu = true }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val e = etat
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.surface)
                    .clickable(onClickLabel = "Parcourir le téléphone") { x.aller(Vue.Dossier(Stockage.racine.absolutePath)) }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText("Ce téléphone", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = a.encre))
                    if (e != null) {
                        BasicText(taille(e.libre), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = a.encre))
                        BasicText(" libres sur ${taille(e.total)}", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
                    }
                }
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(a.champ)) {
                    if (e != null) {
                        listOf(e.videos, e.images, e.applis, e.systeme, e.autres).forEachIndexed { i, o ->
                            if (o > 0) Box(Modifier.weight((o.toFloat() / e.total).coerceAtLeast(0.004f)).height(12.dp).background(COULEURS[i]))
                        }
                        Box(Modifier.weight((e.libre.toFloat() / e.total).coerceAtLeast(0.004f)))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Vidéos", "Images", "Applis", "Système", "Autres").forEachIndexed { i, nom ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(COULEURS[i]))
                            BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                        }
                    }
                }
                BoutonTexteAppli(if (recuperable >= 1_000_000) "Libérer ${taille(recuperable)}" else "Nettoyer", style = 's', icone = Ic.BALAI) { x.aller(Vue.Nettoyer) }
            }
            val tailles = mapOf(
                Categorie.IMAGES to e?.images, Categorie.VIDEOS to e?.videos, Categorie.AUDIO to e?.audio,
                Categorie.DOCUMENTS to e?.documents, Categorie.APPLIS to e?.applis, Categorie.RECUS to e?.recus,
            )
            val icones = mapOf(
                Categorie.IMAGES to Icones.IMAGE, Categorie.VIDEOS to Icones.VIDEO, Categorie.AUDIO to Ic.AUDIO,
                Categorie.DOCUMENTS to Ic.DOC, Categorie.APPLIS to Ic.TELEPHONE, Categorie.RECUS to Icones.TELECHARGE,
            )
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Categorie.entries.chunked(3).forEach { rangee ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rangee.forEach { cat ->
                            Column(
                                Modifier.weight(1f).height(92.dp).clip(RoundedCornerShape(18.dp)).background(a.surface)
                                    .clickable(onClickLabel = cat.nom, role = Role.Button) {
                                        when (cat) {
                                            Categorie.APPLIS -> c.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
                                            Categorie.RECUS -> x.aller(Vue.Recus)
                                            else -> x.aller(Vue.Sorte(cat))
                                        }
                                    }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                IconeTrait(icones.getValue(cat), 22.dp, a.accentTexte)
                                Column {
                                    BasicText(cat.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre))
                                    BasicText(tailles[cat]?.let { if (it == 0L) "vide" else taille(it) } ?: " ", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Rub("Ailleurs")
            LigneAppli("Tous les dossiers", "Téléchargements, Documents, Appareil photo…", debut = { IconeTrait(Icones.DOSSIER, 24.dp, a.encre2) }, fin = { IconeTrait(Icones.CHEVRON, 20.dp, a.encre2) }) {
                x.aller(Vue.Dossier(Stockage.racine.absolutePath))
            }
            Stockage.ailleurs(c).forEach { (nom, d) ->
                val (libre, total) = Stockage.place(d)
                LigneAppli(nom, "${taille(libre)} libres sur ${taille(total)}", debut = { IconeTrait(Ic.SD, 24.dp, a.encre2) }, fin = { IconeTrait(Icones.CHEVRON, 20.dp, a.encre2) }) {
                    x.aller(Vue.Dossier(d.absolutePath))
                }
            }
            LigneAppli(
                "Corbeille", if (jetes == 0) "Vide" else "$jetes fichier${if (jetes > 1) "s" else ""} · gardés 30 jours",
                debut = { IconeTrait(Icones.CORBEILLE, 24.dp, a.encre2) }, fin = { IconeTrait(Icones.CHEVRON, 20.dp, a.encre2) },
            ) { x.aller(Vue.Corbeille) }
            Spacer(Modifier.height(16.dp))
        }
        Onglets(0)
    }
    if (menu) MenuOptions(fermer = { menu = false }, dossier = null)
}

@Composable
private fun MenuOptions(fermer: () -> Unit, dossier: File?, nouveau: (() -> Unit)? = null) {
    val c = LocalContext.current
    val x = LocalActions.current
    var caches by remember { mutableStateOf(cachesVisibles(c)) }
    FeuilleAppli(fermer = fermer) {
        if (nouveau != null) ActionFeuille(Ic.NOUVEAU_DOSSIER, "Nouveau dossier") {
            fermer()
            nouveau()
        }
        LigneAppli("Afficher les fichiers cachés", "Ceux dont le nom commence par un point", fin = { InterAppli(caches) }) {
            caches = !caches
            prefs(c).edit().putBoolean("caches", caches).apply()
            x.dire(if (caches) "Les fichiers cachés sont affichés" else "Les fichiers cachés sont masqués", null)
            fermer()
        }
        if (dossier == null) ActionFeuille(Icones.CORBEILLE, "Corbeille") {
            fermer()
            x.aller(Vue.Corbeille)
        }
    }
}

// ——— Un dossier (maquette l2-fic-dossiers) ———

@Composable
private fun Dossier(d: File) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    var tri by remember { mutableStateOf(Tri.entries[prefs(c).getInt("tri", 0)]) }
    var grille by remember { mutableStateOf(prefs(c).getBoolean("grille", false)) }
    var menu by remember { mutableStateOf(false) }
    var creer by remember { mutableStateOf(false) }
    var maj by remember { mutableIntStateOf(0) }
    val contenu by produceState<List<Fichier>?>(null, d, tri, x.version, maj) { value = withContext(Dispatchers.IO) { Stockage.lister(d, tri, cachesVisibles(c)) } }
    EcranAppli {
        Tete(Stockage.nomDossier(d), retour = x.retour, petit = true) {
            BoutonAppli(Icones.RECHERCHE, "Rechercher") { x.aller(Vue.Recherche) }
            BoutonAppli(Icones.OPTIONS, "Plus d'options") { menu = true }
        }
        // Le fil : Ce téléphone › Documents › École ; chaque étape ramène à ce dossier.
        val etapes = generateSequence(d) { f -> f.parentFile?.takeIf { f.absolutePath != Stockage.racine.absolutePath && it.absolutePath.startsWith("/storage") } }.toList().reversed()
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            etapes.forEachIndexed { i, e ->
                val dernier = i == etapes.lastIndex
                BasicText(
                    Stockage.nomDossier(e),
                    modifier = if (dernier) Modifier else Modifier.clickable(onClickLabel = "Aller à ${Stockage.nomDossier(e)}") { x.aller(Vue.Dossier(e.absolutePath)) },
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, fontWeight = if (dernier) FontWeight.SemiBold else FontWeight.Normal, color = if (dernier) a.encre else a.encre2),
                )
                if (!dernier) IconeTrait(Icones.CHEVRON, 14.dp, a.encre2)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(onClickLabel = "Changer l'ordre") {
                    tri = Tri.entries[(tri.ordinal + 1) % Tri.entries.size]
                    prefs(c).edit().putInt("tri", tri.ordinal).apply()
                },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconeTrait(Ic.TRI, 18.dp, a.encre2)
                BasicText(tri.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.encre2))
            }
            BoutonAppli(if (grille) Icones.LISTE else Icones.GRILLE, if (grille) "Afficher en liste" else "Afficher en grille") {
                grille = !grille
                prefs(c).edit().putBoolean("grille", grille).apply()
            }
        }
        val l = contenu ?: return@EcranAppli
        if (l.isEmpty()) {
            BasicText("Ce dossier est vide.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
        } else if (grille) {
            Grille(l)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                val nbDossiers = l.count { it.dossier }
                items(l, key = { it.chemin }) { f ->
                    LigneFichier(f)
                    if (f.dossier && l.indexOf(f) == nbDossiers - 1 && nbDossiers < l.size) Box(Modifier.padding(vertical = 6.dp)) { Filet() }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
            }
        }
    }
    if (menu) MenuOptions(fermer = { menu = false }, dossier = d, nouveau = { creer = true })
    if (creer) NouveauDossier(fermer = { creer = false }) { nom ->
        creer = false
        if (Stockage.nouveauDossier(d, nom)) maj++ else x.dire("Impossible de créer « $nom »", null)
    }
}

@Composable
fun Grille(l: List<Fichier>) {
    val a = LocalIdentite.current
    val x = LocalActions.current
    LazyVerticalGrid(GridCells.Adaptive(108.dp), Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(l, key = { it.chemin }) { f ->
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).combinedClickable(onClickLabel = f.nom, onLongClick = { x.options(f) }) { x.ouvrir(f) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Vignette(f, coin = 14.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                BasicText(f.nom, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre))
            }
        }
    }
}

@Composable
private fun NouveauDossier(fermer: () -> Unit, creer: (String) -> Unit) {
    val a = LocalIdentite.current
    var nom by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DialogueAppli("Nouveau dossier", null, fermer = fermer, contenu = {
        BasicTextField(
            nom, { nom = it }, singleLine = true,
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(a.champ).padding(14.dp).focusRequester(focus).semantics { contentDescription = "Nom du dossier" },
        )
    }) {
        BoutonTexteAppli("Annuler", style = 't', onClick = fermer)
        BoutonTexteAppli("Créer", actif = nom.isNotBlank()) { creer(nom.trim()) }
    }
}

// ——— Une sorte de fichiers : Images, Vidéos, Audio, Documents ———

@Composable
private fun Sorte(cat: Categorie) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val l by produceState<List<Fichier>?>(null, cat, x.version) { value = withContext(Dispatchers.IO) { Stockage.categorie(c, cat) } }
    EcranAppli {
        Tete(cat.nom, retour = x.retour, petit = true) { BoutonAppli(Icones.RECHERCHE, "Rechercher") { x.aller(Vue.Recherche) } }
        val liste = l ?: return@EcranAppli
        BasicText(
            if (liste.isEmpty()) "Aucun fichier." else "${liste.size} fichier${if (liste.size > 1) "s" else ""} · ${taille(liste.sumOf { it.taille })}",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2),
        )
        if (cat == Categorie.IMAGES || cat == Categorie.VIDEOS) Grille(liste)
        else LazyColumn(Modifier.fillMaxSize()) {
            items(liste, key = { it.chemin }) { f -> LigneFichier(f, "${taille(f.taille)} · ${quand(f.date)} · ${File(f.chemin).parentFile?.let { Stockage.nomDossier(it) }.orEmpty()}") }
        }
    }
}

// ——— Rechercher ———

@Composable
private fun Recherche() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    var texte by remember { mutableStateOf("") }
    val l by produceState(emptyList<Fichier>(), texte, x.version) {
        delay(200)
        value = withContext(Dispatchers.IO) { Stockage.rechercher(c, texte) }
    }
    EcranAppli {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BoutonAppli("M15 6l-6 6l6 6", "Retour", onClick = x.retour)
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(
                Modifier.weight(1f).padding(end = 16.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(a.champ).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconeTrait(Icones.RECHERCHE, 20.dp, a.encre2)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    texte, { texte = it }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
                    modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Chercher un fichier" },
                    decorationBox = { champ ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (texte.isEmpty()) BasicText("Chercher un fichier", style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2))
                            champ()
                        }
                    },
                )
            }
        }
        if (texte.isNotBlank() && l.isEmpty()) {
            BasicText("Aucun fichier ne porte ce nom.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(l, key = { it.chemin }) { f -> LigneFichier(f, "${taille(f.taille)} · ${File(f.chemin).parentFile?.let { Stockage.nomDossier(it) }.orEmpty()}") }
        }
    }
}
