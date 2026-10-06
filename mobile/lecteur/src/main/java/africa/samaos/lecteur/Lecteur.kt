package africa.samaos.lecteur

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.NavAppli
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

sealed interface Vue {
    data class Onglet(val n: Int) : Vue
    data object Platine : Vue
    data class Film(val v: Video) : Vue
    data class Dossier(val chemin: String) : Vue
}

private object Ic {
    const val ACCUEIL = "M4 11l8-7l8 7 M6 9.5V20h12V9.5"
    const val MUSIQUE = "M9 18V5l11-2v13 M9 18a3 3 0 1 1-3-3a3 3 0 0 1 3 3z M20 16a3 3 0 1 1-3-3a3 3 0 0 1 3 3z"
    const val SUIVANT = "M6 6l9 6l-9 6z M18 6v12"
    const val PRECEDENT = "M18 6l-9 6l9 6z M6 6v12"
    const val ALEATOIRE = "M4 7h3l10 10h3 M4 17h3l3-3 M14 10l3-3h3 M18 5l2 2l-2 2 M18 15l2 2l-2 2"
    const val REPETER = "M5 10V8a2 2 0 0 1 2-2h11 M15 3l3 3l-3 3 M19 14v2a2 2 0 0 1-2 2H6 M9 21l-3-3l3-3"
    const val RECULER = "M11 7l-5 5l5 5 M18 7l-5 5l5 5"
    const val AVANCER = "M13 7l5 5l-5 5 M6 7l5 5l-5 5"
}

/** Le Lecteur de Sama (maquette l2-lecteur). */
class Lecteur : ComponentActivity() {
    private val pile = mutableStateListOf<Vue>(Vue.Onglet(0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lire(intent)
        setContent {
            val vue = pile.last()
            val noir = vue is Vue.Film
            val id = if (isSystemInDarkTheme() || noir) Identites.LecteurNuit else Identites.Lecteur
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            CompositionLocalProvider(LocalIdentite provides id) { Racine(pile) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    private fun lire(i: Intent?) {
        if (i?.getBooleanExtra("platine", false) == true && pile.last() != Vue.Platine) pile.add(Vue.Platine)
        // Un fichier audio ouvert depuis Fichiers, Messages… : il joue tout de suite.
        val uri = i?.data
        if (i?.action == Intent.ACTION_VIEW && uri != null) {
            val nom = try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
            } catch (_: Exception) {
                null
            } ?: "Fichier audio"
            val d = try {
                android.media.MediaMetadataRetriever().use { r -> r.setDataSource(this, uri); r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() }
            } catch (_: Exception) {
                null
            } ?: 0
            Platine.lire(this, listOf(Morceau(-1, nom.substringBeforeLast('.'), "Fichier ouvert", "", d, "", source = uri)), 0)
            if (pile.last() != Vue.Platine) pile.add(Vue.Platine)
        }
    }
}

@Composable
private fun Racine(pile: SnapshotStateList<Vue>) {
    val c = LocalContext.current
    var permis by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(c, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val demander = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> permis = r.values.all { it } }
    LaunchedEffect(Unit) {
        if (!permis) demander.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS))
    }
    val aller: (Vue) -> Unit = { pile.add(it) }
    val retour: () -> Unit = { if (pile.size > 1) pile.removeAt(pile.lastIndex) }
    BackHandler(enabled = pile.size > 1, onBack = retour)
    val morceaux by produceState(emptyList<Morceau>(), permis) { value = withContext(Dispatchers.IO) { Bibliotheque.morceaux(c) } }
    var version by remember { mutableIntStateOf(0) }
    val videos by produceState(emptyList<Video>(), permis, version) { value = withContext(Dispatchers.IO) { Bibliotheque.videos(c) } }
    when (val vue = pile.last()) {
        is Vue.Onglet -> Onglets(vue.n, morceaux, videos, aller) { pile.clear(); pile.add(Vue.Onglet(it)) }
        Vue.Platine -> PagePlatine(retour)
        is Vue.Film -> Film(vue.v) { version++; retour() }
        is Vue.Dossier -> DossierMedias(vue.chemin, morceaux, videos, aller, retour)
    }
}

@Composable
private fun Onglets(n: Int, morceaux: List<Morceau>, videos: List<Video>, aller: (Vue) -> Unit, choisir: (Int) -> Unit) {
    val a = LocalIdentite.current
    EcranAppli {
        Tete(listOf("Lecteur", "Musique", "Vidéos", "Dossiers")[n])
        Box(Modifier.weight(1f)) {
            when (n) {
                0 -> AccueilLecteur(morceaux, videos, aller, choisir)
                1 -> ListeMorceaux(morceaux)
                2 -> GrilleVideos(videos, aller)
                3 -> Dossiers(morceaux, videos, aller)
            }
            if (morceaux.isEmpty() && videos.isEmpty()) {
                BasicText(
                    "La musique et les vidéos du téléphone viendront ici : celles que vous recevez, celles de la carte SD. Le Lecteur marche sans connexion.",
                    modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
                )
            }
        }
        MiniLecteur { aller(Vue.Platine) }
        NavAppli(listOf("Accueil" to Ic.ACCUEIL, "Musique" to Ic.MUSIQUE, "Vidéos" to Icones.VIDEO, "Dossiers" to Icones.DOSSIER), n, choisir)
    }
}

// ——— L'accueil du Lecteur ———

@Composable
private fun AccueilLecteur(morceaux: List<Morceau>, videos: List<Video>, aller: (Vue) -> Unit, choisir: (Int) -> Unit) {
    val c = LocalContext.current
    // Les vidéos commencées d'abord, puis les plus récentes.
    val recentes = remember(videos) { videos.sortedByDescending { maxOf(Bibliotheque.vueLe(c, it), it.date) }.take(2) }
    LazyColumn(Modifier.fillMaxSize()) {
        if (recentes.isNotEmpty()) {
            item { Rub("Vidéos récentes") }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recentes.forEach { v -> CarteVideo(v, Modifier.weight(1f)) { aller(Vue.Film(v)) } }
                    if (recentes.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (morceaux.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Rub("Musique") }
                    BoutonTexteAppli("Tout voir", style = 't') { choisir(1) }
                }
            }
            items(morceaux.take(6), key = { it.id }) { m -> LigneMorceau(m, morceaux) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CarteVideo(v: Video, modifier: Modifier, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val image by produceState(Bibliotheque.pochetteEnCache(v.uri), v.id) {
        // Une autre vidéo : on repart de son image à elle, jamais de la précédente.
        value = Bibliotheque.pochetteEnCache(v.uri)
        if (value == null) value = withContext(Dispatchers.IO) { Bibliotheque.pochette(c, v.uri, 384) }
    }
    val reprise = remember(v.id) { Bibliotheque.reprise(c, v) }
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Regarder ${v.titre}", role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(98.dp).clip(RoundedCornerShape(14.dp)).background(a.champ)) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            BasicText(
                duree(v.duree),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x8C000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
            )
            // Où l'on s'était arrêté.
            if (reprise > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(reprise.toFloat() / v.duree.coerceAtLeast(1)).height(3.dp).background(a.accent))
        }
        BasicText(v.titre, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, color = a.encre))
    }
}

@Composable
private fun Pochette(uri: Uri, taille: Dp, coin: Dp = 10.dp, modifier: Modifier = Modifier.size(taille)) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val image by produceState(Bibliotheque.pochetteEnCache(uri), uri) {
        // Un autre morceau : sa pochette à lui, ou rien (pas celle du précédent).
        value = Bibliotheque.pochetteEnCache(uri)
        if (value == null) value = withContext(Dispatchers.IO) { Bibliotheque.pochette(c, uri, 512) }
    }
    Box(modifier.clip(RoundedCornerShape(coin)).background(a.voile), contentAlignment = Alignment.Center) {
        val i = image
        if (i != null) Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else IconeTrait(Ic.MUSIQUE, taille / 2.4f, a.accentTexte)
    }
}

@Composable
private fun LigneMorceau(m: Morceau, file: List<Morceau>) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val etat by Platine.etat.collectAsState()
    val ici = etat?.courant?.id == m.id
    // Le morceau en cours se signale dans la couleur de l'appli.
    LigneAppli(
        m.titre, if (ici) (if (etat?.joue == true) "En cours · " else "En pause · ") + m.artiste else m.artiste,
        couleurSecond = if (ici) a.accentTexte else null,
        debut = { Pochette(m.uri, 48.dp) },
        fin = { BasicText(duree(m.duree), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2)) },
    ) {
        if (ici) Platine.commander(c, Musique.BASCULER) else Platine.lire(c, file, file.indexOf(m))
    }
}

@Composable
private fun ListeMorceaux(morceaux: List<Morceau>) {
    val c = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        if (morceaux.isNotEmpty()) item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonTexteAppli("Tout lire", icone = Icones.LECTURE) { Platine.lire(c, morceaux, 0) }
                BoutonTexteAppli("Aléatoire", style = 's', icone = Ic.ALEATOIRE) { Platine.lire(c, morceaux, 0, aleatoire = true) }
            }
        }
        items(morceaux, key = { it.id }) { m -> LigneMorceau(m, morceaux) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GrilleVideos(videos: List<Video>, aller: (Vue) -> Unit) {
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(videos, key = { it.id }) { v -> CarteVideo(v, Modifier) { aller(Vue.Film(v)) } }
    }
}

/** Les dossiers où se trouvent musique et vidéos (Téléchargements, WhatsApp, la carte SD…). */
@Composable
private fun Dossiers(morceaux: List<Morceau>, videos: List<Video>, aller: (Vue) -> Unit) {
    val a = LocalIdentite.current
    val dossiers = remember(morceaux, videos) {
        (morceaux.map { it.dossier } + videos.map { it.dossier }).groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(dossiers, key = { it.first }) { (chemin, n) ->
            LigneAppli(
                chemin.trimEnd('/').substringAfterLast('/').ifBlank { "Téléphone" }, chemin.trimEnd('/') + " · $n élément${if (n > 1) "s" else ""}",
                debut = { IconeTrait(Icones.DOSSIER, 24.dp, a.encre2) }, fin = { IconeTrait(Icones.CHEVRON, 20.dp, a.encre2) },
            ) { aller(Vue.Dossier(chemin)) }
        }
    }
}

@Composable
private fun DossierMedias(chemin: String, morceaux: List<Morceau>, videos: List<Video>, aller: (Vue) -> Unit, retour: () -> Unit) {
    val ms = morceaux.filter { it.dossier == chemin }
    val vs = videos.filter { it.dossier == chemin }
    EcranAppli {
        Tete(chemin.trimEnd('/').substringAfterLast('/'), retour = retour, petit = true)
        LazyColumn(Modifier.weight(1f)) {
            items(vs, key = { "v${it.id}" }) { v ->
                LigneAppli(v.titre, duree(v.duree), debut = { Pochette(v.uri, 48.dp) }) { aller(Vue.Film(v)) }
            }
            items(ms, key = { "m${it.id}" }) { m -> LigneMorceau(m, ms) }
        }
        MiniLecteur { aller(Vue.Platine) }
    }
}

// ——— Le mini-lecteur, au-dessus des onglets ———

@Composable
private fun MiniLecteur(ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val etat by Platine.etat.collectAsState()
    val m = etat?.courant ?: return
    var position by remember { mutableLongStateOf(Platine.position()) }
    LaunchedEffect(m.id, etat?.joue) {
        while (true) {
            position = Platine.position()
            delay(500)
        }
    }
    Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().height(68.dp).clip(RoundedCornerShape(20.dp)).background(a.accent).clickable(onClickLabel = "Ouvrir la lecture", onClick = ouvrir)) {
        Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pochette(m.uri, 48.dp)
            Column(Modifier.weight(1f)) {
                BasicText(m.titre, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.surAccent))
                BasicText("${m.artiste} · ${duree(position)}", maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.surAccent.copy(alpha = 0.8f)))
            }
            BoutonRond(if (etat?.joue == true) Icones.PAUSE else Icones.LECTURE, if (etat?.joue == true) "Pause" else "Lecture", a.surAccent) { Platine.commander(c, Musique.BASCULER) }
            BoutonRond(Ic.SUIVANT, "Titre suivant", a.surAccent) { Platine.commander(c, Musique.SUIVANT) }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((position.toFloat() / m.duree.coerceAtLeast(1)).coerceIn(0f, 1f)).height(3.dp).background(a.surAccent.copy(alpha = 0.8f)))
    }
}

@Composable
private fun BoutonRond(icone: String, nom: String, couleur: Color, taille: Dp = 48.dp, fond: Color = Color.Transparent, onClick: () -> Unit) {
    Box(
        Modifier.size(taille).clip(CircleShape).background(fond).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, taille * 0.46f, couleur) }
}

// ——— La platine, en grand ———

@Composable
private fun PagePlatine(retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val etat by Platine.etat.collectAsState()
    val e = etat
    if (e?.courant == null) {
        LaunchedEffect(Unit) { retour() }
        return
    }
    val m = e.courant!!
    var position by remember { mutableLongStateOf(Platine.position()) }
    var glisse by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(m.id) {
        while (true) {
            if (glisse == null) position = Platine.position()
            delay(300)
        }
    }
    EcranAppli {
        Tete("", retour = retour)
        Column(Modifier.weight(1f).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Pochette(m.uri, 96.dp, 28.dp, Modifier.fillMaxWidth().aspectRatio(1f))
            Column(Modifier.fillMaxWidth()) {
                BasicText(m.titre, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = a.encre))
                BasicText(listOf(m.artiste, m.album).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2))
            }
            // La barre : on glisse pour aller plus loin dans le morceau.
            Column(Modifier.fillMaxWidth()) {
                val part = glisse ?: (position.toFloat() / m.duree.coerceAtLeast(1))
                Canvas(
                    Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "Position dans le morceau" }
                        .pointerInput(m.id) {
                            detectDragGestures(
                                onDragStart = { o -> glisse = (o.x / size.width).coerceIn(0f, 1f) },
                                onDragEnd = { glisse?.let { Platine.commander(c, Musique.ALLER, (it * m.duree).toLong()); position = (it * m.duree).toLong() }; glisse = null },
                            ) { ch, _ -> glisse = (ch.position.x / size.width).coerceIn(0f, 1f) }
                        }
                        .pointerInput(m.id) { detectTapGestures { o -> val p = (o.x / size.width).coerceIn(0f, 1f); Platine.commander(c, Musique.ALLER, (p * m.duree).toLong()); position = (p * m.duree).toLong() } },
                ) {
                    val y = size.height / 2
                    drawLine(a.trait, Offset(0f, y), Offset(size.width, y), 6f)
                    drawLine(a.accent, Offset(0f, y), Offset(size.width * part.coerceIn(0f, 1f), y), 6f)
                    drawCircle(a.accent, 8.dp.toPx(), Offset(size.width * part.coerceIn(0f, 1f), y))
                }
                Row {
                    BasicText(duree((part * m.duree).toLong()), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                    Spacer(Modifier.weight(1f))
                    BasicText(duree(m.duree), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BoutonRond(Ic.ALEATOIRE, if (e.aleatoire) "Lecture dans l'ordre" else "Lecture aléatoire", if (e.aleatoire) a.accentTexte else a.encre2) { Platine.aleatoire() }
                BoutonRond(Ic.PRECEDENT, "Précédent", a.encre, 56.dp) { Platine.commander(c, Musique.PRECEDENT) }
                BoutonRond(if (e.joue) Icones.PAUSE else Icones.LECTURE, if (e.joue) "Pause" else "Lecture", a.surAccent, 76.dp, a.accent) { Platine.commander(c, Musique.BASCULER) }
                BoutonRond(Ic.SUIVANT, "Suivant", a.encre, 56.dp) { Platine.commander(c, Musique.SUIVANT) }
                Box {
                    BoutonRond(Ic.REPETER, when (e.repeter) { Repeter.NON -> "Répéter tout"; Repeter.TOUT -> "Répéter ce morceau"; Repeter.UN -> "Ne plus répéter" }, if (e.repeter == Repeter.NON) a.encre2 else a.accentTexte) { Platine.repeter() }
                    if (e.repeter == Repeter.UN) BasicText("1", modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = a.accentTexte))
                }
            }
            e.file.getOrNull(e.index + 1)?.let { s ->
                BasicText("Ensuite : ${s.titre} · ${s.artiste}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

// ——— Une vidéo, en grand ———

@Composable
private fun Film(v: Video, fermer: () -> Unit) {
    val c = LocalContext.current
    val activite = c as? ComponentActivity
    var vue by remember { mutableStateOf<VideoView?>(null) }
    var commandes by remember { mutableStateOf(true) }
    var joue by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(Bibliotheque.reprise(c, v)) }
    // Plein écran : les barres d'Android se cachent, un glissement les fait revenir.
    DisposableEffect(Unit) {
        val fenetre = activite?.window
        val ctl = fenetre?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            // La vidéo est déjà arrêtée à ce moment : on garde la dernière position relevée.
            Bibliotheque.garderReprise(c, v, position)
            ctl?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(commandes, joue) {
        // Les commandes s'effacent seules pendant la lecture.
        if (commandes && joue) {
            delay(3500)
            commandes = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            vue?.takeIf { it.isPlaying }?.let { position = it.currentPosition.toLong() }
            delay(500)
        }
    }
    BackHandler(onBack = fermer)
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { commandes = !commandes } }) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    keepScreenOn = true
                    setVideoURI(v.uri)
                    setOnPreparedListener { mp ->
                        seekTo(Bibliotheque.reprise(ctx, v).toInt())
                        start()
                        mp.setOnCompletionListener {
                            Bibliotheque.garderReprise(ctx, v, v.duree)
                            joue = false
                            commandes = true
                        }
                    }
                    vue = this
                }
            },
            onRelease = { it.stopPlayback() },
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
        )
        if (commandes) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                BoutonAppli("M15 6l-6 6l6 6", "Retour", onClick = fermer)
                BasicText(v.titre, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White))
            }
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                BoutonRond(Ic.RECULER, "Reculer de 10 secondes", Color.White, 56.dp) { vue?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }; commandes = true }
                BoutonRond(if (joue) Icones.PAUSE else Icones.LECTURE, if (joue) "Pause" else "Lecture", Color.White, 76.dp, Color(0x33FFFFFF)) {
                    vue?.let { if (it.isPlaying) it.pause() else it.start(); joue = it.isPlaying }
                    vue?.let { Bibliotheque.garderReprise(c, v, it.currentPosition.toLong()) }
                }
                BoutonRond(Ic.AVANCER, "Avancer de 10 secondes", Color.White, 56.dp) { vue?.let { it.seekTo((it.currentPosition + 10_000).coerceAtMost(v.duree.toInt())) }; commandes = true }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Canvas(
                    Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "Position dans la vidéo" }
                        .pointerInput(v.id) { detectTapGestures { o -> vue?.seekTo((o.x / size.width * v.duree).toInt()) } },
                ) {
                    val y = size.height / 2
                    val part = (position.toFloat() / v.duree.coerceAtLeast(1)).coerceIn(0f, 1f)
                    drawLine(Color(0x55FFFFFF), Offset(0f, y), Offset(size.width, y), 6f)
                    drawLine(Color(0xFFC3A6D8), Offset(0f, y), Offset(size.width * part, y), 6f)
                    drawCircle(Color(0xFFC3A6D8), 8.dp.toPx(), Offset(size.width * part, y))
                }
                Row {
                    BasicText(duree(position), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = Color.White))
                    Spacer(Modifier.weight(1f))
                    BasicText(duree(v.duree), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = Color.White))
                }
            }
        }
    }
}
