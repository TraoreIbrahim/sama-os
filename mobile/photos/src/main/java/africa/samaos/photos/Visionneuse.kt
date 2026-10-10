package africa.samaos.photos

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * La visionneuse : la photo s'ouvre en grandissant depuis sa vignette et y retourne ; on la tire vers le bas pour
 * la refermer ; on glisse d'une photo à l'autre, ou on touche la bande des vignettes ; deux doigts pour zoomer.
 */
@Composable
fun Visionneuse(liste: List<Media>, index: Int) {
    val c = LocalContext.current
    val x = LocalActions.current
    val densite = LocalDensity.current
    var medias by remember { mutableStateOf(liste) }
    val favoris = remember { mutableStateMapOf<Long, Boolean>() }
    val pager = rememberPagerState(initialPage = index.coerceIn(0, (liste.size - 1).coerceAtLeast(0))) { medias.size }
    var habits by remember { mutableStateOf(true) }
    var zoome by remember { mutableStateOf(false) }
    var infos by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val portee = rememberCoroutineScope()
    val ouverture = remember { Animatable(0f) }
    var cadre by remember { mutableStateOf(liste.getOrNull(index)?.let { Cadres.vignettes[it.id] }) }
    var fermeture by remember { mutableStateOf(false) }
    var tire by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { ouverture.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 320f)) }
    if (medias.isEmpty()) {
        LaunchedEffect(Unit) { x.retour() }
        return
    }
    val m = medias[pager.currentPage.coerceIn(0, medias.size - 1)]
    val favori = favoris[m.id] ?: m.favori
    // Refermer : la photo retourne à sa vignette (si elle est à l'écran), sinon elle s'efface.
    fun fermer() {
        if (fermeture) return
        fermeture = true
        cadre = Cadres.vignettes[m.id]
        portee.launch {
            launch { androidx.compose.animation.core.animate(tire, 0f, animationSpec = tween(260)) { v, _ -> tire = v } }
            ouverture.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            x.retour()
        }
    }
    BackHandler { fermer() }
    val seuil = with(densite) { 130.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val p = ouverture.value
        val glisse = (abs(tire) / h).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (p * (1f - glisse * 1.8f)).coerceIn(0f, 1f))))
        val r = cadre
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val reduit = 1f - glisse * 0.35f
                    if (r != null && r.width > 1f) {
                        val s0 = r.width / l
                        val s = (s0 + (1f - s0) * p) * reduit
                        scaleX = s
                        scaleY = s
                        translationX = (r.center.x + (l / 2 - r.center.x) * p) - l / 2
                        translationY = (r.center.y + (h / 2 - r.center.y) * p) - h / 2 + tire
                        // Au départ, un carré comme la vignette ; il s'ouvre jusqu'à l'écran entier.
                        val haut = l + (h - l) * p
                        shape = RectangleBord((h - haut) / 2, (h + haut) / 2, (1f - p) * 10f * density / s0.coerceAtLeast(0.05f))
                        clip = p < 0.999f
                    } else {
                        alpha = p
                        val s = (0.92f + 0.08f * p) * reduit
                        scaleX = s
                        scaleY = s
                        translationY = tire
                    }
                }
                .draggable(
                    rememberDraggableState { d -> tire += d },
                    Orientation.Vertical,
                    enabled = !zoome && !fermeture,
                    onDragStopped = { v ->
                        if (abs(tire) > seuil || abs(v) > 1600f) fermer()
                        else androidx.compose.animation.core.animate(tire, 0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f)) { a, _ -> tire = a }
                    },
                ),
        ) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoome, key = { medias[it].id }, beyondViewportPageCount = 1) { pg ->
                val mp = medias[pg]
                if (mp.video) PageVideo(mp.uri, Galerie.vignetteEnCache(mp), { habits = !habits })
                else ImageZoom(mp.uri, Galerie.vignetteEnCache(mp), { habits = !habits }, { if (pg == pager.currentPage) zoome = it })
            }
        }
        val visibles = habits && p > 0.9f && abs(tire) < 4f && !fermeture
        androidx.compose.animation.AnimatedVisibility(visibles, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
            Box(Modifier.fillMaxSize()) {
                // En haut : revenir, le jour et l'heure, les informations.
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Verre(Icones.RETOUR, "Revenir") { fermer() }
                    val t = Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault())
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(nomDuJour(m.jour), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFFF4EEE4)))
                        BasicText(
                            t.format(DateTimeFormatter.ofPattern("HH:mm")) + " · " + Galerie.nomAlbum(m.album, m.dossier), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = Color(0xFFB3AA9C)),
                        )
                    }
                    Verre(Icones.INFO, "Informations") { infos = true }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000)))).navigationBarsPadding().padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // La bande des vignettes : celle qu'on regarde est plus large.
                    val bande = rememberLazyListState()
                    LaunchedEffect(pager.currentPage) { bande.animateScrollToItem((pager.currentPage - 4).coerceAtLeast(0)) }
                    LazyRow(state = bande, modifier = Modifier.fillMaxWidth().height(46.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        items(medias.size, key = { medias[it].id }) { i ->
                            val ici = i == pager.currentPage
                            val large by animateDpAsState(if (ici) 46.dp else 30.dp, spring(dampingRatio = 0.8f, stiffness = 600f), label = "bande")
                            Vignette(
                                medias[i],
                                Modifier.width(large).height(if (ici) 46.dp else 40.dp).clip(RoundedCornerShape(6.dp))
                                    .clickable(onClickLabel = "Aller à cette photo") { portee.launch { pager.animateScrollToPage(i) } },
                                retenirCadre = false,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Bouton(Icones.PARTAGER, "Partager") { x.partager(listOf(m)) }
                        Bouton(Icones.COEUR, if (favori) "Retirer des favoris" else "Ajouter aux favoris", allume = favori) {
                            favoris[m.id] = !favori
                            portee.launch {
                                if (!withContext(Dispatchers.IO) { Galerie.favori(c, m, !favori) }) favoris[m.id] = favori
                                x.changer()
                            }
                        }
                        if (!m.video) Bouton(Ic.RETOUCHER, "Retoucher") { x.aller(Vue.Retouche(m)) }
                        Bouton(Ic.AUTRES, "Plus") { options = true }
                        Bouton(Icones.CORBEILLE, "Mettre à la corbeille") {
                            val position = medias.indexOf(m)
                            medias = medias - m
                            portee.launch {
                                withContext(Dispatchers.IO) { Galerie.jeter(c, m) }
                                x.changer()
                                x.dire(if (m.video) "Vidéo dans la corbeille" else "Photo dans la corbeille") {
                                    portee.launch {
                                        withContext(Dispatchers.IO) { Galerie.remettre(c, m) }
                                        medias = medias.toMutableList().apply { add(position.coerceAtMost(size), m) }
                                        x.changer()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (infos) Infos(m) { infos = false }
    if (options) {
        FeuilleAppli(fermer = { options = false }) {
            ActionFeuille(Icones.OUVRIR, "Ouvrir avec…") {
                options = false
                c.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, m.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Ouvrir avec"))
            }
            ActionFeuille(Icones.OEIL_BARRE, if (m.video) "Masquer la vidéo" else "Masquer la photo", second = "Elle ira dans Masquées, qui s'ouvre avec le déverrouillage") {
                options = false
                x.masquer(listOf(m)) { medias = medias - m }
            }
            if (!m.video) ActionFeuille(Icones.IMAGE, "Utiliser comme…", second = "Fond d'écran, photo d'un contact") {
                options = false
                c.startActivity(Intent.createChooser(Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(m.uri, m.mime).putExtra("mimeType", m.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Utiliser comme"))
            }
        }
    }
}

/** Le cadre qui s'ouvre : une bande horizontale de [haut] à [bas], aux coins arrondis de [rayon]. */
private class RectangleBord(val haut: Float, val bas: Float, val rayon: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
        androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(0f, haut, size.width, bas, androidx.compose.ui.geometry.CornerRadius(rayon)))
}

/** Un bouton rond de verre, sur la photo. */
@Composable
private fun Verre(icone: String, nom: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, 21.dp, Color(0xFFF4EEE4), epaisseur = 2f) }
}

/** Un bouton de la capsule du bas ; le cœur s'allume, d'un petit bond. */
@Composable
private fun Bouton(icone: String, nom: String, allume: Boolean = false, onClick: () -> Unit) {
    val bond by animateFloatAsState(if (allume) 1.15f else 1f, spring(dampingRatio = 0.35f, stiffness = 600f), label = "bond")
    Box(
        Modifier.size(50.dp).clip(CircleShape).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, 23.dp, if (allume) Color(0xFFF08A6C) else Color(0xFFF4EEE4), Modifier.graphicsLayer { scaleX = bond; scaleY = bond }, epaisseur = if (icone == Ic.AUTRES) 3.2f else if (allume) 2.6f else 1.8f) }
}

@Composable
private fun Action(icone: String, nom: String, actif: Boolean = false, onClick: () -> Unit) {
    val couleur = if (actif) Color(0xFFE0A77C) else Color(0xFFF4EEE4)
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconeTrait(icone, 24.dp, couleur)
        BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = couleur))
    }
}

/** Une image qu'on agrandit à deux doigts ou d'un double appui ; agrandie, on la promène d'un doigt. */
@Composable
fun ImageZoom(uri: Uri, apercu: androidx.compose.ui.graphics.ImageBitmap?, toucher: () -> Unit, zoom: (Boolean) -> Unit) {
    val c = LocalContext.current
    val image by produceState(apercu, uri) { value = withContext(Dispatchers.IO) { Galerie.image(c, uri)?.asImageBitmap() } ?: apercu }
    var echelle by remember { mutableFloatStateOf(1f) }
    var decalage by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { toucher() },
                    onDoubleTap = { p ->
                        if (echelle > 1f) {
                            echelle = 1f
                            decalage = Offset.Zero
                        } else {
                            echelle = 2.5f
                            decalage = (Offset(size.width / 2f, size.height / 2f) - p) * 1.5f
                        }
                        zoom(echelle > 1f)
                    },
                )
            }
            .pointerInput(uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        // Un doigt sur une image non agrandie : on laisse glisser vers la suivante.
                        if (ev.changes.size > 1 || echelle > 1f) {
                            echelle = (echelle * ev.calculateZoom()).coerceIn(1f, 5f)
                            val maxX = size.width * (echelle - 1) / 2
                            val maxY = size.height * (echelle - 1) / 2
                            val d = decalage + ev.calculatePan()
                            decalage = if (echelle == 1f) Offset.Zero else Offset(d.x.coerceIn(-maxX, maxX), d.y.coerceIn(-maxY, maxY))
                            zoom(echelle > 1f)
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(
                it, null, Modifier.fillMaxSize().graphicsLayer { scaleX = echelle; scaleY = echelle; translationX = decalage.x; translationY = decalage.y },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** Une vidéo : l'aperçu et un bouton de lecture ; lancée, un appui la met en pause. */
@Composable
fun PageVideo(uri: Uri, apercu: androidx.compose.ui.graphics.ImageBitmap?, toucher: () -> Unit) {
    var lancee by remember(uri) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (lancee) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(uri)
                        setOnPreparedListener { start() }
                        setOnCompletionListener { lancee = false }
                        setOnClickListener { if (isPlaying) pause() else start(); toucher() }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            apercu?.let { Image(it, null, Modifier.fillMaxSize().clickable(onClick = toucher), contentScale = ContentScale.Fit) }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color(0x99000000)).clickable(onClickLabel = "Lire la vidéo", role = Role.Button) { lancee = true },
                contentAlignment = Alignment.Center,
            ) { IconeTrait(Icones.LECTURE, 30.dp, Color.White) }
        }
    }
}

@Composable
private fun Infos(m: Media, fermer: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val details by produceState<Pair<String?, Boolean>?>(null, m.id) { value = withContext(Dispatchers.IO) { Galerie.appareil(c, m) to (!m.video && Galerie.aUnLieu(c, m.uri)) } }
    FeuilleAppli(fermer = fermer, titre = m.nom) {
        val t = Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault())
        LigneAppli(nomDuJour(m.jour) + " à " + t.format(DateTimeFormatter.ofPattern("HH:mm")), null, debut = { IconeTrait(Icones.HORLOGE, 22.dp, a.encre2) })
        LigneAppli(
            listOfNotNull(if (m.largeur > 0) "${m.largeur} × ${m.hauteur}" else null, taille(m.taille), if (m.video) duree(m.duree) else null).joinToString(" · "),
            Galerie.nomAlbum(m.album, m.dossier) + " · " + m.dossier.trimEnd('/'),
            debut = { IconeTrait(if (m.video) Icones.VIDEO else Icones.IMAGE, 22.dp, a.encre2) },
        )
        details?.first?.let { LigneAppli(it, "Appareil", debut = { IconeTrait(Icones.APPAREIL, 22.dp, a.encre2) }) }
        if (details?.second == true) {
            LigneAppli(
                "Le lieu est enregistré dans la photo", "Photos vous proposera de l'enlever avant de partager",
                debut = { IconeTrait(Ic.LIEU, 22.dp, a.accentTexte) },
            )
        }
    }
}

/** Une photo ou une vidéo venue d'une autre appli, hors de la pellicule. */
@Composable
fun Externe(uri: Uri, mime: String?) {
    val c = LocalContext.current
    val x = LocalActions.current
    var habits by remember { mutableStateOf(true) }
    val nom by produceState<String?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
            } catch (_: Exception) {
                null
            }
        }
    }
    val video = mime?.startsWith("video/") == true
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (video) PageVideo(uri, null) { habits = !habits } else ImageZoom(uri, null, { habits = !habits }, {})
        if (habits) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))).statusBarsPadding().height(64.dp).padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoutonAppli("M15 6l-6 6l6 6", "Retour", onClick = x.retour)
                BasicText(nom.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFFF4EEE4)))
            }
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
                Action(Icones.PARTAGER, "Partager") { envoyer(c, listOf(uri), listOf(mime ?: "*/*")) }
            }
        }
    }
}
