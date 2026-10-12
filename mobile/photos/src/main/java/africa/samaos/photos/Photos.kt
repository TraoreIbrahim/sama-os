package africa.samaos.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.Identite
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PileDoigt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ce qu'on montre dans une liste de photos. */
sealed interface Ensemble {
    data class UnAlbum(val id: Long) : Ensemble
    data object Favoris : Ensemble
    data object Videos : Ensemble
    data object Captures : Ensemble
    data object Documents : Ensemble
    data object CeJour : Ensemble
    data class Resultats(val termes: String) : Ensemble
}

/** Ce qu'on peut faire de la place, et comment. */
enum class Nettoyage(val titre: String, val explication: String) {
    DOUBLONS("Photos en double", "Les mêmes photos, enregistrées plusieurs fois. Dans chaque groupe, la plus ancienne reste ; les autres sont choisies pour partir."),
    VIDEOS("Grosses vidéos", "Les vidéos qui prennent le plus de place, de la plus lourde à la plus légère. Rien n'est choisi d'avance : à vous de voir."),
    CAPTURES("Vieilles captures d'écran", "Les captures de plus de 30 jours : un code, un reçu, une conversation, souvent plus utiles. Elles sont choisies pour partir."),
    WHATSAPP("Reçues par WhatsApp", "Les images et vidéos reçues dans les discussions, gardées par WhatsApp dans le téléphone. Rien n'est choisi d'avance."),
}

sealed interface Vue {
    data object Pellicule : Vue
    data object Collections : Vue
    data class Liste(val titre: String, val filtre: Ensemble) : Vue
    data class Menage(val sorte: Nettoyage) : Vue
    data object Corbeille : Vue
    data object Masquees : Vue
    data object Recherche : Vue
    data class Visionneuse(val liste: List<Media>, val index: Int) : Vue
    data class Externe(val uri: Uri, val mime: String?) : Vue
    data class Retouche(val media: Media) : Vue
}

/** Ce qui se pose par-dessus une page, sur fond noir. */
private val Vue.dessus get() = this is Vue.Visionneuse || this is Vue.Externe || this is Vue.Retouche

internal object Ic {
    const val ALBUMS = "M4 8h16v12H4z M6 5h12 M8 2.5h8"
    const val RETOUCHER = "M5 7h8 M17 7h2 M15 5v4 M5 17h2 M11 17h8 M9 15v4"
    const val LIEU = "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z M12 7a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z"
    const val PELLICULE = "M4 5h16v14H4z M4 15l4.5-4.5l3.5 3.5l2.5-2.5L20 17 M15.5 8.5h.01"
    const val COLLECTIONS = "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z"
    const val PLACE = "M4 7h16 M6 7l1 12h10l1-12 M9.5 7V4.5h5V7 M12 11v5 M9.5 13.5L12 16l2.5-2.5"
    const val LEGER = "M12 4v10 M8 10l4 4l4-4 M5 18h14"
    const val AUTRES = "M6.5 12h.01 M12 12h.01 M17.5 12h.01"
    const val SOUVENIR = "M12 3v3 M12 18v3 M3 12h3 M18 12h3 M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z"
}

class Actions(
    val aller: (Vue) -> Unit,
    val retour: () -> Unit,
    val onglet: (Vue) -> Unit,
    val dire: (String, (() -> Unit)?) -> Unit,
    val partager: (List<Media>) -> Unit,
    /** Masquer, après confirmation ; [fait] quand c'est fait. */
    val masquer: (List<Media>, () -> Unit) -> Unit,
    val version: Int,
    val changer: () -> Unit,
)

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions") }

/** Toute la pellicule, relue quand quelque chose change. */
val LocalTout = staticCompositionLocalOf<List<Media>?> { null }

/** Où se trouvent les vignettes à l'écran : une photo s'ouvre en grandissant depuis la sienne, et y retourne. */
object Cadres {
    val vignettes = HashMap<Long, Rect>()
}

/** La chambre noire de la visionneuse et de la retouche. */
val Noir = Identites.PhotosNuit.copy(fond = Color.Black)

/** Photos de Sama : la pellicule, les collections, faire de la place, partager léger. */
class Photos : ComponentActivity() {
    private val pile = mutableStateListOf<Vue>(Vue.Pellicule)
    private var depuisAilleurs = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lire(intent)
        setContent {
            val noir = pile.last().dessus
            val id = if (isSystemInDarkTheme()) Identites.PhotosNuit else Identites.Photos
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre && !noir
                    isAppearanceLightNavigationBars = !id.sombre && !noir
                }
            }
            AvecIdentite(id) { Racine(pile) { if (depuisAilleurs && pile.size <= 2) finish() else if (pile.size > 1) pile.removeAt(pile.lastIndex) } }
        }
    }

    /** Quitter l'appli referme l'album Masquées ; le souvenir de l'Accueil reprend la pellicule. */
    override fun onStop() {
        Coffre.ouvert = false
        WidgetsPhotos.annoncer(this)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    /** Une photo ou une vidéo ouverte depuis une autre appli (Fichiers, Messages, l'appareil photo). */
    private fun lire(i: Intent?) {
        val uri = i?.data ?: return
        if (i.action != Intent.ACTION_VIEW && i.action != "com.android.camera.action.REVIEW") return
        depuisAilleurs = true
        pile.clear()
        pile.add(Vue.Pellicule)
        val dansLaPellicule = uri.authority == "media" && uri.lastPathSegment?.toLongOrNull() != null
        val m = if (dansLaPellicule) Galerie.tout(this).let { l -> l.indexOfFirst { it.id == uri.lastPathSegment!!.toLong() }.takeIf { it >= 0 }?.let { l to it } } else null
        pile.add(if (m != null) Vue.Visionneuse(m.first, m.second) else Vue.Externe(uri, i.type ?: contentResolver.getType(uri)))
    }
}

@Composable
private fun Racine(pile: SnapshotStateList<Vue>, retour: () -> Unit) {
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<Pair<String, (() -> Unit)?>?>(null) }
    var aPartager by remember { mutableStateOf<List<Media>?>(null) }
    var aMasquer by remember { mutableStateOf<Pair<List<Media>, () -> Unit>?>(null) }
    val tout by produceState<List<Media>?>(null, version) { value = withContext(Dispatchers.IO) { Galerie.tout(c) } }
    val actions = Actions(
        aller = { pile.add(it) },
        retour = retour,
        onglet = {
            pile.clear()
            pile.add(it)
        },
        dire = { t, annuler -> message = t to annuler },
        partager = { aPartager = it },
        masquer = { l, fait -> aMasquer = l to fait },
        version = version,
        changer = { version++ },
    )
    CompositionLocalProvider(LocalActions provides actions, LocalTout provides tout) {
        Box(Modifier.fillMaxSize().background(LocalIdentite.current.fond)) {
            val iPage = pile.indexOfLast { !it.dessus }.coerceAtLeast(0)
            val pages = pile.take(iPage + 1)
            // Une page arrive de la droite ; d'un onglet à l'autre, un fondu ; le retour suit le doigt (ce qui est par-dessus, la visionneuse, gère le sien).
            PileDoigt(
                pages, actions.retour,
                actif = pile.size > 1 && !pile.last().dessus,
                transition = { avant, apres -> if (avant.size == 1 && apres.size == 1) fadeIn(tween(220)) togetherWith fadeOut(tween(160)) else null },
            ) { v ->
                when (v) {
                    Vue.Pellicule -> PagePellicule()
                    Vue.Collections -> PageCollections()
                    is Vue.Liste -> PageListe(v)
                    is Vue.Menage -> PageMenage(v.sorte)
                    Vue.Corbeille -> PageCorbeille()
                    Vue.Masquees -> PageMasquees()
                    Vue.Recherche -> PageRecherche()
                    else -> {}
                }
            }
            // Par-dessus la page : la visionneuse, puis la retouche ; chacune garde son état sous la suivante.
            pile.drop(iPage + 1).forEach { v ->
                key(v) {
                    AvecIdentite(Noir) {
                        when (v) {
                            is Vue.Visionneuse -> Visionneuse(v.liste, v.index)
                            is Vue.Externe -> Externe(v.uri, v.mime)
                            is Vue.Retouche -> Box(Modifier.fillMaxSize().background(Color.Black)) { Retouche(v.media) }
                            else -> {}
                        }
                    }
                }
            }
            message?.let { (texte, annuler) ->
                LaunchedEffect(texte) {
                    delay(5000)
                    message = null
                }
                Bandeau(texte, annuler?.let { a -> { a(); message = null } }, Modifier.align(Alignment.BottomCenter))
            }
            aPartager?.let { l -> FeuillePartage(l) { aPartager = null } }
            aMasquer?.let { (l, fait) ->
                FeuilleMasquer(l, fermer = { aMasquer = null }) {
                    portee.launch {
                        val faites = withContext(Dispatchers.IO) { l.mapNotNull { Coffre.masquer(c, it) } }
                        version++
                        if (faites.isNotEmpty()) fait()
                        val texte = when {
                            faites.size < l.size -> "${l.size - faites.size} élément${if (l.size - faites.size > 1) "s n'ont" else " n'a"} pas pu être masqué${if (l.size - faites.size > 1) "s" else ""}"
                            faites.size > 1 -> "${faites.size} éléments masqués"
                            else -> "Masquée : dans Collections › Masquées"
                        }
                        message = texte to (if (faites.isEmpty()) null else ({
                            portee.launch {
                                withContext(Dispatchers.IO) { faites.forEach { Coffre.afficher(c, it) } }
                                version++
                            }
                        }))
                    }
                }
            }
        }
    }
}

/** Partager : tel quel ou plus léger (moins de data), avec ou sans le lieu de la prise de vue. */
@Composable
private fun FeuillePartage(l: List<Media>, fermer: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val portee = rememberCoroutineScope()
    val photos = l.filter { !it.video }
    val avecLieu by produceState<Boolean?>(null, l) { value = withContext(Dispatchers.IO) { photos.any { Galerie.aUnLieu(c, it.uri) } } }
    var sansLieu by remember { mutableStateOf(true) }
    val poids = l.sumOf { it.taille }
    // Une photo plus légère fait à peu près 1600 pixels de côté : environ 400 Ko.
    val leger = l.sumOf { if (it.video) it.taille else minOf(it.taille, 420_000L) }
    FeuilleAppli(fermer = fermer, titre = if (l.size > 1) "Partager ${l.size} éléments" else if (l.first().video) "Partager la vidéo" else "Partager la photo") {
        val f = LocalFermerFeuille.current
        fun envoyerTel(legere: Boolean) {
            f()
            portee.launch {
                val uris = withContext(Dispatchers.IO) {
                    l.map { m ->
                        when {
                            m.video -> m.uri
                            legere -> Galerie.copieLegere(c, m)?.let { FileProvider.getUriForFile(c, "africa.samaos.photos.partage", it) } ?: m.uri
                            sansLieu && avecLieu == true -> Galerie.copieSansLieu(c, m)?.let { FileProvider.getUriForFile(c, "africa.samaos.photos.partage", it) } ?: m.uri
                            else -> m.uri
                        }
                    }
                }
                envoyer(c, uris, l.map { if (legere && !it.video) "image/jpeg" else it.mime })
            }
        }
        if (photos.isNotEmpty() && poids > leger * 1.3) {
            LignePartage(Ic.LEGER, "Plus léger", "≈ ${taille(leger)} au lieu de ${taille(poids)} : moins de data, pour vous et pour qui reçoit", a.accentTexte) { envoyerTel(true) }
        }
        LignePartage(Icones.PARTAGER, "Tel quel", "${taille(poids)}, la qualité d'origine", a.encre2) { envoyerTel(false) }
        if (avecLieu == true) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { sansLieu = !sansLieu }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconeTrait(Ic.LIEU, 22.dp, a.accentTexte)
                Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
                    BasicText("Enlever le lieu", style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
                    BasicText(
                        if (photos.size > 1) "Ces photos disent où elles ont été prises : votre maison, l'école des enfants." else "Cette photo dit où elle a été prise : votre maison, l'école des enfants.",
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = a.encre2),
                    )
                }
                InterAppli(sansLieu)
            }
        }
    }
}

/** Avant de masquer : ce que ça fait, et si le téléphone n'a pas de code, ce que ça ne protège pas. */
@Composable
private fun FeuilleMasquer(l: List<Media>, fermer: () -> Unit, masquer: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val une = l.size == 1
    FeuilleAppli(fermer = fermer, titre = if (!une) "Masquer ${l.size} éléments ?" else if (l.first().video) "Masquer la vidéo ?" else "Masquer la photo ?") {
        val f = LocalFermerFeuille.current
        BasicText(
            (if (une) "Elle quitte la pellicule et les autres applis ne la voient plus. Elle reste" else "Ils quittent la pellicule et les autres applis ne les voient plus. Ils restent") +
                " dans Collections › Masquées, qui s'ouvre avec le déverrouillage du téléphone.",
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp), style = st(14.5f, a.encre2, interligne = 1.45f),
        )
        if (!Coffre.protege(c)) BasicText(
            "Le téléphone n'a pas de code : l'album pourra s'ouvrir sans rien demander. Mettez un code dans Réglages › Sécurité.",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(a.voile).padding(14.dp),
            style = st(14f, a.accentTexte, interligne = 1.4f),
        )
        LignePartage(Icones.OEIL_BARRE, if (une) "Masquer" else "Masquer les ${l.size}", "Pour la retrouver : Collections › Masquées", a.accentTexte) {
            f()
            masquer()
        }
    }
}

@Composable
private fun LignePartage(icone: String, titre: String, second: String, couleur: Color, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = titre, role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(icone, 21.dp, couleur) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre))
            BasicText(second, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = a.encre2))
        }
    }
}

fun envoyer(c: Context, uris: List<Uri>, mimes: List<String>) {
    val type = mimes.distinct().singleOrNull() ?: if (mimes.all { it.startsWith("image/") }) "image/*" else "*/*"
    val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    i.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(i, if (uris.size > 1) "Partager ${uris.size} éléments" else "Partager"))
}

/** Un message en bas, avec « Annuler » s'il y a lieu. */
@Composable
fun Bandeau(texte: String, annuler: (() -> Unit)?, modifier: Modifier) {
    val a = LocalIdentite.current
    Row(
        modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 96.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.encre)
            .padding(start = 18.dp, end = 6.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(texte, modifier = Modifier.weight(1f).padding(vertical = 10.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.fond))
        if (annuler != null) {
            Box(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClickLabel = "Annuler", role = Role.Button, onClick = annuler).padding(horizontal = 14.dp, vertical = 12.dp)) {
                BasicText("Annuler", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (a.sombre) a.surAccent else Color(0xFFE0A77C)))
            }
        }
    }
}

// ——— Ce qui revient partout ———

fun st(taille: Float, couleur: Color, poids: FontWeight = FontWeight.Normal, interligne: Float = 1.35f) =
    TextStyle(fontFamily = Polices.corps, fontWeight = poids, fontSize = taille.sp, lineHeight = (taille * interligne).sp, color = couleur)

/** Le grand titre d'une page, et une ligne dessous. */
@Composable
fun GrandTitre(texte: String, sous: String? = null, modifier: Modifier = Modifier) {
    val a = LocalIdentite.current
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp)) {
        BasicText(texte, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em, color = a.encre))
        if (sous != null) AnimatedContent(sous, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "sous") { s -> BasicText(s, style = st(14f, a.encre2)) }
    }
}

/** La barre du haut : le retour nommé (« ‹ Collections ») et des boutons. */
@Composable
fun Barre(retour: String?, onRetour: () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (retour != null) Row(
            Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Retour à $retour", role = Role.Button, onClick = onRetour).padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconeTrait(Icones.RETOUR, 24.dp, a.accentTexte, epaisseur = 2f)
            BasicText(retour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp), style = st(17f, a.accentTexte, FontWeight.Medium))
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Un bouton rond voilé, qui s'enfonce un peu sous le doigt. */
@Composable
fun Rond(icone: String, description: String, taille: Dp = 42.dp, plein: Boolean = false, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val source = remember { MutableInteractionSource() }
    val presse by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (presse) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "appui")
    Box(
        Modifier.size(taille).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(if (plein) a.accent else a.voile)
            .clickable(interactionSource = source, indication = null, onClickLabel = description, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, taille * 0.5f, if (plein) a.surAccent else a.accentTexte, epaisseur = 1.9f) }
}

/** Un texte-bouton dans une capsule (« Choisir », « Tout choisir »). */
@Composable
fun Capsule(texte: String, actif: Boolean = false, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val fond by animateColorAsState(if (actif) a.accent else a.voile, label = "capsule")
    BasicText(
        texte, maxLines = 1,
        modifier = Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(fond).clickable(onClickLabel = texte, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        style = st(14.5f, if (actif) a.surAccent else a.accentTexte, FontWeight.SemiBold, 1.2f),
    )
}

/**
 * Les onglets, en capsule flottante sur les photos : Pellicule et Collections ; la loupe à côté.
 * Le galet glisse d'un onglet à l'autre.
 */
@Composable
fun Onglets(courant: Int, modifier: Modifier = Modifier) {
    val a = LocalIdentite.current
    val x = LocalActions.current
    Row(modifier.navigationBarsPadding().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(
            Modifier.height(56.dp).width(260.dp).clip(RoundedCornerShape(28.dp)).background(a.surface.copy(alpha = 0.96f))
                .border(1.dp, a.trait, RoundedCornerShape(28.dp)).padding(5.dp),
        ) {
            val large = maxWidth / 2
            val decalage by animateDpAsState(large * courant, spring(dampingRatio = 0.8f, stiffness = 500f), label = "galet")
            Box(Modifier.offset(x = decalage).width(large).fillMaxHeight().clip(RoundedCornerShape(23.dp)).background(a.voile))
            Row(Modifier.fillMaxSize()) {
                listOf(Triple("Pellicule", Ic.PELLICULE, Vue.Pellicule), Triple("Collections", Ic.COLLECTIONS, Vue.Collections)).forEachIndexed { i, (nom, icone, vue) ->
                    val ici = i == courant
                    val encre by animateColorAsState(if (ici) a.accentTexte else a.encre2, label = "encre")
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(23.dp)).clickable(onClickLabel = nom, role = Role.Tab) { if (!ici) x.onglet(vue) },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeTrait(icone, 19.dp, encre)
                        BasicText(nom, modifier = Modifier.padding(start = 7.dp), style = st(14.5f, encre, FontWeight.SemiBold, 1.1f))
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(a.surface.copy(alpha = 0.96f)).border(1.dp, a.trait, CircleShape)
                .clickable(onClickLabel = "Rechercher", role = Role.Button) { x.aller(Vue.Recherche) }.semantics { contentDescription = "Rechercher" },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(Icones.RECHERCHE, 22.dp, a.encre) }
    }
}

// ——— La vignette d'une photo ———

@Composable
fun Vignette(m: Media, modifier: Modifier, choisie: Boolean? = null, coins: Dp = 0.dp, retenirCadre: Boolean = true) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val image by produceState(Galerie.vignetteEnCache(m), m.id) {
        value = Galerie.vignetteEnCache(m)
        if (value == null) value = withContext(Dispatchers.IO) { Galerie.vignette(c, m) }
    }
    if (retenirCadre) DisposableEffect(m.id) { onDispose { Cadres.vignettes.remove(m.id) } }
    val retrait by animateDpAsState(if (choisie == true) 10.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "retrait")
    Box(modifier.then(if (retenirCadre) Modifier.onGloballyPositioned { Cadres.vignettes[m.id] = it.boundsInRoot() } else Modifier)) {
        // Choisie, la photo se resserre un peu dans sa case.
        Box(Modifier.fillMaxSize().padding(retrait).clip(RoundedCornerShape(coins + retrait / 2)).background(a.surface)) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (m.video) BasicText(
                duree(m.duree), modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 5.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, shadow = androidx.compose.ui.graphics.Shadow(Color(0x80000000), blurRadius = 4f)),
            )
            if (m.favori) Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) { IconeTrait(Icones.COEUR, 15.dp, Color.White) }
        }
        if (choisie != null) {
            val fond by animateColorAsState(if (choisie) a.accent else Color(0x33000000), label = "coche")
            Box(
                Modifier.align(Alignment.BottomEnd).padding(7.dp).size(22.dp).clip(CircleShape).background(fond).border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (choisie) IconeTrait(Icones.COCHE, 14.dp, a.surAccent, epaisseur = 2.4f) }
        }
    }
}

fun nombre(n: Int): String = String.format(java.util.Locale.FRENCH, "%,d", n).replace(' ', ' ').replace(' ', ' ')

/** « 38 photos · 4 vidéos ». */
fun compte(l: List<Media>): String {
    val v = l.count { it.video }
    val p = l.size - v
    return listOfNotNull(
        if (p > 0) "${nombre(p)} photo${if (p > 1) "s" else ""}" else null,
        if (v > 0) "${nombre(v)} vidéo${if (v > 1) "s" else ""}" else null,
    ).joinToString(" · ").ifEmpty { "Rien pour l'instant" }
}
