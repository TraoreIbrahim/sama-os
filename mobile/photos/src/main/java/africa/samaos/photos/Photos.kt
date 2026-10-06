package africa.samaos.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.NavAppli
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Vue {
    data object Pellicule : Vue
    data object Albums : Vue
    data class UnAlbum(val id: Long, val nom: String) : Vue
    data object Favoris : Vue
    data object Corbeille : Vue
    data class Visionneuse(val liste: List<Media>, val index: Int) : Vue
    data class Externe(val uri: Uri, val mime: String?) : Vue
    data class Retouche(val media: Media) : Vue
}

internal object Ic {
    const val ALBUMS = "M4 8h16v12H4z M6 5h12 M8 2.5h8"
    const val RETOUCHER = "M5 7h8 M17 7h2 M15 5v4 M5 17h2 M11 17h8 M9 15v4"
    const val LIEU = "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z M12 7a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z"
}

class Actions(
    val aller: (Vue) -> Unit,
    val retour: () -> Unit,
    val onglet: (Int) -> Unit,
    val dire: (String, (() -> Unit)?) -> Unit,
    val partager: (List<Media>) -> Unit,
    val version: Int,
    val changer: () -> Unit,
)

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions") }

/** Photos de Sama (maquettes l2-pho). */
class Photos : ComponentActivity() {
    private val pile = mutableStateListOf<Vue>(Vue.Pellicule)
    private var depuisAilleurs = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lire(intent)
        setContent {
            val vue = pile.last()
            // La visionneuse et la retouche sont toujours sur fond noir, comme une chambre noire.
            val noir = vue is Vue.Visionneuse || vue is Vue.Externe || vue is Vue.Retouche
            val id = if (noir || isSystemInDarkTheme()) Identites.PhotosNuit.copy(fond = if (noir) Color.Black else Identites.PhotosNuit.fond) else Identites.Photos
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            CompositionLocalProvider(LocalIdentite provides id) { Racine(pile) { if (depuisAilleurs && pile.size <= 2) finish() else if (pile.size > 1) pile.removeAt(pile.lastIndex) } }
        }
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
    var lieu by remember { mutableStateOf<List<Media>?>(null) }
    val actions = Actions(
        aller = { pile.add(it) },
        retour = retour,
        onglet = {
            pile.clear()
            pile.add(if (it == 0) Vue.Pellicule else Vue.Albums)
        },
        dire = { t, annuler -> message = t to annuler },
        partager = { l ->
            portee.launch {
                // Le lieu de la prise de vue voyage avec la photo : on le dit avant qu'elle parte.
                val avecLieu = withContext(Dispatchers.IO) { l.filter { !it.video }.any { Galerie.aUnLieu(c, it.uri) } }
                if (avecLieu) lieu = l else envoyer(c, l.map { it.uri }, l.map { it.mime })
            }
        },
        version = version,
        changer = { version++ },
    )
    BackHandler(enabled = pile.size > 1) { actions.retour() }
    CompositionLocalProvider(LocalActions provides actions) {
        Box(Modifier.fillMaxSize()) {
            when (val vue = pile.last()) {
                Vue.Pellicule -> Pellicule()
                Vue.Albums -> Albums()
                is Vue.UnAlbum -> UnAlbum(vue)
                Vue.Favoris -> Favoris()
                Vue.Corbeille -> Corbeille()
                is Vue.Visionneuse -> Visionneuse(vue.liste, vue.index)
                is Vue.Externe -> Externe(vue.uri, vue.mime)
                is Vue.Retouche -> Retouche(vue.media)
            }
            message?.let { (texte, annuler) ->
                LaunchedEffect(texte) {
                    delay(5000)
                    message = null
                }
                Bandeau(texte, annuler?.let { a -> { a(); message = null } }, Modifier.align(Alignment.BottomCenter))
            }
            lieu?.let { l ->
                DialogueAppli(
                    if (l.size > 1) "Ces photos disent où elles ont été prises" else "Cette photo dit où elle a été prise",
                    "Celui qui la reçoit peut retrouver l'endroit : votre maison, l'école des enfants. Partagez-la sans le lieu, sauf si vous voulez le montrer.",
                    fermer = { lieu = null },
                ) {
                    BoutonTexteAppli("Avec le lieu", style = 't') {
                        lieu = null
                        envoyer(c, l.map { it.uri }, l.map { it.mime })
                    }
                    BoutonTexteAppli("Sans le lieu") {
                        lieu = null
                        portee.launch {
                            val uris = withContext(Dispatchers.IO) {
                                l.map { m -> if (m.video) m.uri else Galerie.copieSansLieu(c, m)?.let { FileProvider.getUriForFile(c, "africa.samaos.photos.partage", it) } ?: m.uri }
                            }
                            envoyer(c, uris, l.map { it.mime })
                        }
                    }
                }
            }
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

@Composable
fun Bandeau(texte: String, annuler: (() -> Unit)?, modifier: Modifier) {
    val a = LocalIdentite.current
    Row(
        modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 120.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(a.encre)
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

@Composable
private fun Onglets(courant: Int) {
    val x = LocalActions.current
    NavAppli(listOf("Photos" to Icones.IMAGE, "Albums" to Ic.ALBUMS), courant, x.onglet)
}

// ——— La vignette d'une photo ———

@Composable
fun Vignette(m: Media, modifier: Modifier, choisie: Boolean? = null) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val image by produceState(Galerie.vignetteEnCache(m), m.id) {
        value = Galerie.vignetteEnCache(m)
        if (value == null) value = withContext(Dispatchers.IO) { Galerie.vignette(c, m) }
    }
    Box(modifier.background(a.surface)) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (m.video) BasicText(
            duree(m.duree), modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 5.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, shadow = androidx.compose.ui.graphics.Shadow(Color(0x80000000), blurRadius = 4f)),
        )
        if (m.favori) Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) { IconeTrait(Icones.COEUR, 16.dp, Color.White) }
        if (choisie != null) {
            if (choisie) Box(Modifier.fillMaxSize().background(Color(0x33000000)))
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                    .then(if (choisie) Modifier.background(a.accent) else Modifier.border(2.dp, Color.White, CircleShape)),
                contentAlignment = Alignment.Center,
            ) { if (choisie) IconeTrait("M5 12.5l4.5 4.5L19 7", 14.dp, a.surAccent) }
        }
    }
}

// ——— La grille, jour par jour (maquette l2-pho-grille) ———

@Composable
fun GrilleMedias(l: List<Media>, parJour: Boolean, choix: SnapshotStateList<Long>, entete: LazyGridScope.() -> Unit = {}) {
    val a = LocalIdentite.current
    val x = LocalActions.current
    LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        entete()
        val groupes = if (parJour) l.groupBy { it.jour } else mapOf(null to l)
        groupes.forEach { (jour, medias) ->
            if (jour != null) item(key = "j$jour", span = { GridItemSpan(maxLineSpan) }) {
                BasicText(
                    nomDuJour(jour), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.encre2),
                )
            }
            items(medias, key = { it.id }) { m ->
                val choisie = m.id in choix
                Vignette(
                    m,
                    Modifier.aspectRatio(1f)
                        .semantics { contentDescription = (if (m.video) "Vidéo du " else "Photo du ") + nomDuJour(m.jour); if (choix.isNotEmpty()) selected = choisie }
                        .combinedClickable(
                            onLongClick = { if (choisie) choix.remove(m.id) else choix.add(m.id) },
                        ) {
                            if (choix.isNotEmpty()) {
                                if (choisie) choix.remove(m.id) else choix.add(m.id)
                            } else {
                                x.aller(Vue.Visionneuse(l, l.indexOf(m)))
                            }
                        },
                    if (choix.isEmpty()) null else choisie,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
    }
}

/** La barre de sélection : combien, partager, à la corbeille. */
@Composable
private fun BarreChoix(l: List<Media>, choix: SnapshotStateList<Long>) {
    val c = LocalContext.current
    val x = LocalActions.current
    val portee = rememberCoroutineScope()
    BackHandler { choix.clear() }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BoutonAppli("M6 6l12 12 M18 6L6 18", "Annuler la sélection") { choix.clear() }
        BasicText(
            "${choix.size} sélectionnée${if (choix.size > 1) "s" else ""}", modifier = Modifier.weight(1f).padding(start = 8.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = LocalIdentite.current.encre),
        )
        BoutonAppli(Icones.PARTAGER, "Partager") { x.partager(l.filter { it.id in choix }) }
        BoutonAppli(Icones.CORBEILLE, "Mettre à la corbeille") {
            val parties = l.filter { it.id in choix }
            choix.clear()
            portee.launch {
                withContext(Dispatchers.IO) { parties.forEach { Galerie.jeter(c, it) } }
                x.changer()
                x.dire("${parties.size} élément${if (parties.size > 1) "s" else ""} dans la corbeille") {
                    portee.launch {
                        withContext(Dispatchers.IO) { parties.forEach { Galerie.remettre(c, it) } }
                        x.changer()
                    }
                }
            }
        }
    }
}

@Composable
private fun Pellicule() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val l by produceState<List<Media>?>(null, x.version) { value = withContext(Dispatchers.IO) { Galerie.tout(c) } }
    val choix = remember { mutableStateListOf<Long>() }
    EcranAppli {
        val liste = l
        if (choix.isNotEmpty() && liste != null) BarreChoix(liste, choix) else Tete("Photos")
        Box(Modifier.weight(1f)) {
            when {
                liste == null -> {}
                liste.isEmpty() -> BasicText(
                    "Pas encore de photo. Celles de l'appareil photo et celles que vous recevez viendront ici.",
                    modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
                )
                else -> GrilleMedias(liste, parJour = true, choix = choix)
            }
        }
        Onglets(0)
    }
}

// ——— Les albums (maquette l2-pho-albums) ———

@Composable
private fun Albums() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val donnees by produceState<Triple<List<Album>, Int, Int>?>(null, x.version) {
        value = withContext(Dispatchers.IO) { Triple(Galerie.albums(Galerie.tout(c)), Galerie.favoris(c).size, Galerie.corbeille(c).size) }
    }
    EcranAppli {
        Tete("Albums")
        val (albums, favoris, jetes) = donnees ?: Triple(emptyList(), 0, 0)
        val grands = albums.filter { it.sorte == Sorte.ALBUM }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                grands.chunked(2).forEach { rangee ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        rangee.forEach { al ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = al.nom, role = Role.Button) { x.aller(Vue.UnAlbum(al.id, al.nom)) },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Vignette(al.couverture, Modifier.fillMaxWidth().height(172.dp).clip(RoundedCornerShape(18.dp)))
                                Column {
                                    BasicText(al.nom, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre))
                                    BasicText(nombre(al.nombre), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
                                }
                            }
                        }
                        if (rangee.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            albums.filter { it.sorte != Sorte.ALBUM }.forEach { al ->
                Ligne(al.nom, if (al.sorte == Sorte.CAPTURES) Icones.IMAGE else Icones.DOCUMENT, nombre(al.nombre)) { x.aller(Vue.UnAlbum(al.id, al.nom)) }
            }
            if (favoris > 0) Ligne("Favoris", Icones.COEUR, nombre(favoris)) { x.aller(Vue.Favoris) }
            Ligne("Corbeille", Icones.CORBEILLE, nombre(jetes), "Effacée pour de bon après 30 jours") { x.aller(Vue.Corbeille) }
            Spacer(Modifier.height(16.dp))
        }
        Onglets(1)
    }
}

fun nombre(n: Int): String = String.format(java.util.Locale.FRENCH, "%,d", n).replace(' ', ' ').replace(' ', ' ')

@Composable
private fun Ligne(nom: String, icone: String, valeur: String, second: String? = null, onClick: () -> Unit) {
    val a = LocalIdentite.current
    LigneAppli(
        nom, second,
        debut = { IconeTrait(icone, 22.dp, a.encre2) },
        fin = { BasicText(valeur, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2)) },
        onClick = onClick,
    )
}

@Composable
private fun UnAlbum(v: Vue.UnAlbum) {
    val c = LocalContext.current
    val x = LocalActions.current
    val l by produceState<List<Media>?>(null, v.id, x.version) { value = withContext(Dispatchers.IO) { Galerie.album(c, v.id) } }
    val choix = remember { mutableStateListOf<Long>() }
    EcranAppli {
        val liste = l
        if (choix.isNotEmpty() && liste != null) BarreChoix(liste, choix) else Tete(v.nom, retour = x.retour, petit = true)
        if (liste != null) GrilleMedias(liste, parJour = true, choix = choix)
    }
}

@Composable
private fun Favoris() {
    val c = LocalContext.current
    val x = LocalActions.current
    val l by produceState<List<Media>?>(null, x.version) { value = withContext(Dispatchers.IO) { Galerie.favoris(c) } }
    val choix = remember { mutableStateListOf<Long>() }
    EcranAppli {
        val liste = l
        if (choix.isNotEmpty() && liste != null) BarreChoix(liste, choix) else Tete("Favoris", retour = x.retour, petit = true)
        if (liste != null) GrilleMedias(liste, parJour = false, choix = choix)
    }
}

// ——— La corbeille ———

@Composable
private fun Corbeille() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val l by produceState<List<Media>?>(null, x.version) { value = withContext(Dispatchers.IO) { Galerie.corbeille(c) } }
    var choisi by remember { mutableStateOf<Media?>(null) }
    var effacer by remember { mutableStateOf<List<Media>?>(null) }
    val portee = rememberCoroutineScope()
    EcranAppli {
        Tete("Corbeille", retour = x.retour, petit = true) {
            if (!l.isNullOrEmpty()) BoutonTexteAppli("Vider", style = 't') { effacer = l }
        }
        val liste = l ?: return@EcranAppli
        BasicText(
            if (liste.isEmpty()) "La corbeille est vide." else "Touchez une photo pour la remettre. Elles s'effacent d'elles-mêmes après 30 jours.",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
        )
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(liste, key = { it.id }) { m ->
                Vignette(m, Modifier.aspectRatio(1f).clickable(onClickLabel = "Que faire de cette photo") { choisi = m })
            }
        }
    }
    choisi?.let { m ->
        val reste by produceState(0L, m.id) { value = withContext(Dispatchers.IO) { Galerie.expiration(c, m) } }
        val jours = ((reste - System.currentTimeMillis() + 86_399_999) / 86_400_000).coerceAtLeast(0)
        FeuilleAppli(fermer = { choisi = null }, titre = if (reste > 0) "S'efface dans $jours jour${if (jours > 1) "s" else ""}" else m.nom) {
            ActionFeuille(Icones.RESTAURER, "Remettre dans « ${Galerie.nomAlbum(m.album, m.dossier)} »") {
                choisi = null
                portee.launch {
                    withContext(Dispatchers.IO) { Galerie.remettre(c, m) }
                    x.changer()
                }
            }
            ActionFeuille(Icones.CORBEILLE, "Effacer pour de bon", danger = true) {
                choisi = null
                effacer = listOf(m)
            }
        }
    }
    effacer?.let { l2 ->
        DialogueAppli(
            if (l2.size > 1) "Effacer ces ${l2.size} éléments ?" else "Effacer pour de bon ?", "On ne pourra plus les remettre.",
            fermer = { effacer = null },
        ) {
            BoutonTexteAppli("Annuler", style = 't') { effacer = null }
            BoutonTexteAppli("Effacer") {
                effacer = null
                portee.launch {
                    withContext(Dispatchers.IO) { l2.forEach { Galerie.effacer(c, it) } }
                    x.changer()
                }
            }
        }
    }
}
