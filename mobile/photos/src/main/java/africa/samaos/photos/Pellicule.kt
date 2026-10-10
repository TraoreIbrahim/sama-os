package africa.samaos.photos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Les tailles de la grille : on pince pour passer de l'une à l'autre. */
private val NIVEAUX = listOf(2, 3, 5, 7)

/**
 * Une grille de photos par jour (par mois quand elle est très serrée). Pincer resserre ou desserre la grille,
 * les vignettes glissent à leur nouvelle place. En mode choix, un toucher coche ; sinon il ouvre.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GrilleMedias(l: List<Media>, choix: SnapshotStateList<Long>?, enChoix: Boolean, colonnesDepart: Int = 3, parJour: Boolean = true, basVide: Dp = 110.dp, badge: ((Media) -> String?)? = null, ouvrir: ((Media) -> Unit)? = null, entete: LazyGridScope.() -> Unit = {}) {
    val a = LocalIdentite.current
    val x = LocalActions.current
    val vibreur = LocalHapticFeedback.current
    var colonnes by rememberSaveable { mutableIntStateOf(colonnesDepart) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var cumul = 1f
                do {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.changes.count { it.pressed } >= 2) {
                        cumul *= e.calculateZoom()
                        e.changes.forEach { it.consume() }
                        val i = NIVEAUX.indexOf(colonnes).coerceAtLeast(0)
                        if (cumul > 1.22f && i > 0) {
                            colonnes = NIVEAUX[i - 1]
                            cumul = 1f
                            vibreur.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        } else if (cumul < 0.82f && i < NIVEAUX.lastIndex) {
                            colonnes = NIVEAUX[i + 1]
                            cumul = 1f
                            vibreur.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    }
                } while (e.changes.any { it.pressed })
            }
        },
    ) {
        val ecart = if (colonnes <= 3) 3.dp else if (colonnes == 5) 2.dp else 1.dp
        LazyVerticalGrid(GridCells.Fixed(colonnes), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ecart), verticalArrangement = Arrangement.spacedBy(ecart)) {
            entete()
            val groupes: Map<String?, List<Media>> = when {
                !parJour -> mapOf(null to l)
                colonnes >= 7 -> l.groupBy { it.jour.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)).replaceFirstChar { c -> c.uppercase() } }
                else -> l.groupBy { nomDuJour(it.jour) }
            }
            groupes.forEach { (titre, medias) ->
                if (titre != null) item(key = "t$titre", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.animateItem().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                        BasicText(titre, modifier = Modifier.weight(1f), style = st(16f, a.encre, FontWeight.SemiBold))
                        BasicText("${medias.size}", style = st(13f, a.encre2))
                    }
                }
                items(medias, key = { it.id }) { m ->
                    val choisie = choix != null && m.id in choix
                    Box(Modifier.animateItem().aspectRatio(1f)) {
                    Vignette(
                        m,
                        Modifier.fillMaxSize()
                            .semantics { contentDescription = (if (m.video) "Vidéo du " else "Photo du ") + nomDuJour(m.jour); if (enChoix) selected = choisie }
                            .combinedClickable(
                                onLongClick = {
                                    vibreur.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (choix != null) { if (choisie) choix.remove(m.id) else choix.add(m.id) }
                                },
                            ) {
                                if (enChoix && choix != null) {
                                    if (choisie) choix.remove(m.id) else choix.add(m.id)
                                } else if (ouvrir != null) ouvrir(m) else x.aller(Vue.Visionneuse(l, l.indexOf(m)))
                            },
                        if (enChoix) choisie else null,
                        coins = if (colonnes <= 3) 4.dp else 2.dp,
                    )
                    badge?.invoke(m)?.let { b ->
                        BasicText(b, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp), style = st(11.5f, Color.White, FontWeight.Bold, 1.2f))
                    }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.navigationBarsPadding().height(basVide)) }
        }
    }
}

/** Les actions sur les photos choisies : partager, favori, corbeille ; posées en bas, en capsule. */
@Composable
fun ActionsChoix(l: List<Media>, choix: SnapshotStateList<Long>, finir: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val portee = rememberCoroutineScope()
    val choisies = l.filter { it.id in choix }
    Row(
        Modifier.navigationBarsPadding().padding(bottom = 10.dp).height(60.dp).clip(RoundedCornerShape(30.dp)).background(a.surface.copy(alpha = 0.97f))
            .border(1.dp, a.trait, RoundedCornerShape(30.dp)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(if (choisies.isEmpty()) "Touchez des photos" else "${choisies.size} choisie${if (choisies.size > 1) "s" else ""}", modifier = Modifier.padding(horizontal = 14.dp), style = st(15f, a.encre, FontWeight.SemiBold))
        val actif = choisies.isNotEmpty()
        ActionCapsule(Icones.PARTAGER, "Partager", actif) { x.partager(choisies) }
        val tousFavoris = choisies.isNotEmpty() && choisies.all { it.favori }
        ActionCapsule(Icones.COEUR, if (tousFavoris) "Retirer des favoris" else "Favoris", actif) {
            portee.launch {
                withContext(Dispatchers.IO) { choisies.forEach { Galerie.favori(c, it, !tousFavoris) } }
                x.changer()
                finir()
            }
        }
        ActionCapsule(Icones.OEIL_BARRE, "Masquer", actif) { x.masquer(choisies) { finir() } }
        ActionCapsule(Icones.CORBEILLE, "Corbeille", actif) {
            finir()
            portee.launch {
                withContext(Dispatchers.IO) { choisies.forEach { Galerie.jeter(c, it) } }
                x.changer()
                x.dire("${choisies.size} élément${if (choisies.size > 1) "s" else ""} dans la corbeille") {
                    portee.launch {
                        withContext(Dispatchers.IO) { choisies.forEach { Galerie.remettre(c, it) } }
                        x.changer()
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCapsule(icone: String, nom: String, actif: Boolean, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = actif, onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, 22.dp, if (actif) a.encre else a.encre2.copy(alpha = 0.45f)) }
}

/** Le bas d'une page : les onglets, ou les actions quand on choisit. */
@Composable
fun BasDePage(onglet: Int?, enChoix: Boolean, l: List<Media>?, choix: SnapshotStateList<Long>, finir: () -> Unit, modifier: Modifier) {
    AnimatedContent(
        enChoix, modifier = modifier,
        transitionSpec = { (slideInVertically { it / 2 } + fadeIn(tween(200))) togetherWith (slideOutVertically { it / 2 } + fadeOut(tween(150))) },
        label = "bas",
    ) { choisir ->
        if (choisir && l != null) ActionsChoix(l, choix, finir) else if (onglet != null) Onglets(onglet) else Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun EnTete(titre: String, sous: String?, enChoix: Boolean, peutChoisir: Boolean, basculer: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        GrandTitre(titre, sous, Modifier.weight(1f))
        if (peutChoisir) Capsule(if (enChoix) "Fini" else "Choisir", actif = enChoix, onClick = basculer)
    }
}

@Composable
fun Vide(icone: String, texte: String) {
    val a = LocalIdentite.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(icone, 28.dp, a.accentTexte) }
        BasicText(texte, modifier = Modifier.padding(top = 14.dp), style = st(15f, a.encre2).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
    }
}

/** La pellicule : toutes les photos et vidéos, du jour au plus ancien. */
@Composable
fun PagePellicule() {
    val tout = LocalTout.current
    val choix = remember { mutableStateListOf<Long>() }
    var enChoix by remember { mutableStateOf(false) }
    val finir = { enChoix = false; choix.clear() }
    BackHandler(enabled = enChoix) { finir() }
    LaunchedEffect(choix.size) { if (choix.isNotEmpty()) enChoix = true }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            tout == null -> {}
            tout.isEmpty() -> Column {
                EnTete("Pellicule", null, false, false) {}
                Vide(Ic.PELLICULE, "Pas encore de photo. Celles de l'appareil photo et celles que vous recevez viendront ici.")
            }
            else -> GrilleMedias(tout, choix, enChoix) {
                item(key = "tete", span = { GridItemSpan(maxLineSpan) }) { EnTete("Pellicule", compte(tout), enChoix, true) { if (enChoix) finir() else enChoix = true } }
            }
        }
        BasDePage(0, enChoix, tout, choix, finir, Modifier.align(Alignment.BottomCenter))
    }
}

/** Une liste de photos (un album, les favoris, les vidéos…), avec le retour vers la page d'avant. */
@Composable
fun PageListe(v: Vue.Liste) {
    val tout = LocalTout.current
    val x = LocalActions.current
    val choix = remember { mutableStateListOf<Long>() }
    var enChoix by remember { mutableStateOf(false) }
    val finir = { enChoix = false; choix.clear() }
    BackHandler(enabled = enChoix) { finir() }
    LaunchedEffect(choix.size) { if (choix.isNotEmpty()) enChoix = true }
    val auj = LocalDate.now()
    val l = tout?.filter { m ->
        when (val f = v.filtre) {
            is Ensemble.UnAlbum -> m.albumId == f.id
            Ensemble.Favoris -> m.favori
            Ensemble.Videos -> m.video
            Ensemble.Captures -> Galerie.estCapture(m)
            Ensemble.Documents -> Galerie.estDocument(m)
            Ensemble.CeJour -> m.jour.monthValue == auj.monthValue && m.jour.dayOfMonth == auj.dayOfMonth && m.jour.year < auj.year
            is Ensemble.Resultats -> Recherche.correspond(m, f.termes)
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Barre(if (v.filtre is Ensemble.Resultats) "Recherche" else "Collections", x.retour)
            if (l != null) {
                if (l.isEmpty()) {
                    GrandTitre(v.titre)
                    Vide(Icones.IMAGE, "Rien ici pour l'instant.")
                } else {
                    GrilleMedias(l, choix, enChoix, parJour = v.filtre != Ensemble.Favoris) {
                        item(key = "tete", span = { GridItemSpan(maxLineSpan) }) { EnTete(v.titre, compte(l), enChoix, true) { if (enChoix) finir() else enChoix = true } }
                    }
                }
            }
        }
        BasDePage(null, enChoix, l, choix, finir, Modifier.align(Alignment.BottomCenter))
    }
}

/** La corbeille : on remet une photo, ou on l'efface pour de bon ; elle s'efface seule après 30 jours. */
@Composable
fun PageCorbeille() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val l by produceState<List<Media>?>(null, x.version) { value = withContext(Dispatchers.IO) { Galerie.corbeille(c) } }
    var choisi by remember { mutableStateOf<Media?>(null) }
    var effacer by remember { mutableStateOf<List<Media>?>(null) }
    val portee = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barre("Collections", x.retour) { if (!l.isNullOrEmpty()) Capsule("Tout effacer") { effacer = l } }
        val liste = l ?: return@Column
        GrandTitre("Corbeille", if (liste.isEmpty()) "Vide" else compte(liste))
        BasicText(
            if (liste.isEmpty()) "Ce qu'on met à la corbeille reste ici 30 jours." else "Touchez une photo pour la remettre. Elles s'effacent d'elles-mêmes après 30 jours.",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp), style = st(14f, a.encre2),
        )
        GrilleMedias(liste, null, false, colonnesDepart = 3, parJour = false, basVide = 24.dp, ouvrir = { choisi = it })
    }
    choisi?.let { m ->
        val reste by produceState(0L, m.id) { value = withContext(Dispatchers.IO) { Galerie.expiration(c, m) } }
        val jours = ((reste - System.currentTimeMillis() + 86_399_999) / 86_400_000).coerceAtLeast(0)
        FeuilleAppli(fermer = { choisi = null }, titre = if (reste > 0) "S'efface dans $jours jour${if (jours > 1) "s" else ""}" else m.nom) {
            val f = LocalFermerFeuille.current
            ActionFeuille(Icones.RESTAURER, "Remettre dans « ${Galerie.nomAlbum(m.album, m.dossier)} »") {
                f()
                portee.launch {
                    withContext(Dispatchers.IO) { Galerie.remettre(c, m) }
                    x.changer()
                }
            }
            ActionFeuille(Icones.CORBEILLE, "Effacer pour de bon", danger = true) {
                f()
                effacer = listOf(m)
            }
        }
    }
    effacer?.let { l2 ->
        DialogueAppli(if (l2.size > 1) "Effacer ces ${l2.size} éléments ?" else "Effacer pour de bon ?", "On ne pourra plus les remettre.", fermer = { effacer = null }) {
            val f = LocalFermerFeuille.current
            BoutonTexteAppli("Annuler", style = 't') { f() }
            BoutonTexteAppli("Effacer") {
                f()
                portee.launch {
                    withContext(Dispatchers.IO) { l2.forEach { Galerie.effacer(c, it) } }
                    x.changer()
                }
            }
        }
    }
}

/** Chercher avec des mots simples : « vidéos », « octobre », « 2025 », « hier », le nom d'un album. */
object Recherche {
    private val MOIS = listOf("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre", "novembre", "decembre")

    fun sansAccents(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    fun correspond(m: Media, termes: String): Boolean {
        val mots = sansAccents(termes).split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
        if (mots.isEmpty()) return false
        val auj = LocalDate.now()
        return mots.all { mot ->
            val mois = if (mot.length >= 3) MOIS.indexOfFirst { it.startsWith(mot) } else -1
            when {
                mot.matches(Regex("\\d{4}")) -> m.jour.year == mot.toInt()
                mois >= 0 -> m.jour.monthValue == mois + 1
                mot.startsWith("aujourd") -> m.jour == auj
                mot == "hier" -> m.jour == auj.minusDays(1)
                mot.startsWith("video") -> m.video
                mot.startsWith("photo") -> !m.video
                mot.startsWith("capture") -> Galerie.estCapture(m)
                mot.startsWith("doc") || mot.startsWith("scan") -> Galerie.estDocument(m)
                mot.startsWith("favori") -> m.favori
                mot.startsWith("whatsapp") -> Galerie.estWhatsApp(m)
                else -> sansAccents(Galerie.nomAlbum(m.album, m.dossier)).contains(mot) || sansAccents(m.nom).contains(mot)
            }
        }
    }
}

/** La recherche : un champ, des idées toutes prêtes, les résultats en direct. */
@Composable
fun PageRecherche() {
    val a = LocalIdentite.current
    val x = LocalActions.current
    val tout = LocalTout.current.orEmpty()
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(20) {
            androidx.compose.runtime.withFrameNanos { }
            try {
                focus.requestFocus()
                return@LaunchedEffect
            } catch (_: Exception) {
            }
        }
    }
    val idees = remember(tout) {
        buildList {
            if (tout.any { it.video }) add("Vidéos")
            if (tout.any { it.favori }) add("Favoris")
            if (tout.any { Galerie.estCapture(it) }) add("Captures")
            if (tout.any { Galerie.estDocument(it) }) add("Documents")
            if (tout.any { Galerie.estWhatsApp(it) }) add("WhatsApp")
            addAll(tout.map { it.jour.withDayOfMonth(1) }.distinct().take(6).map { it.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)).replaceFirstChar { c -> c.uppercase() } })
        }
    }
    val resultats = if (q.isBlank()) emptyList() else tout.filter { Recherche.correspond(it, q) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(16.dp)).background(a.champ).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconeTrait(Icones.RECHERCHE, 19.dp, a.encre2)
                BasicTextField(
                    q, { q = it }, singleLine = true, textStyle = st(16.5f, a.encre), cursorBrush = SolidColor(a.accent),
                    modifier = Modifier.weight(1f).padding(start = 10.dp).focusRequester(focus).semantics { contentDescription = "Rechercher dans les photos" },
                    decorationBox = { champ -> Box { if (q.isEmpty()) BasicText("Vidéos, octobre, 2025, un album…", style = st(16.5f, a.encre2)); champ() } },
                )
            }
            BasicText("Fermer", modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = x.retour).padding(horizontal = 12.dp, vertical = 10.dp), style = st(16.5f, a.accentTexte, FontWeight.Medium))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            idees.forEach { i -> Capsule(i, actif = Recherche.sansAccents(q).trim() == Recherche.sansAccents(i)) { q = i } }
        }
        when {
            q.isBlank() -> Vide(Icones.RECHERCHE, "Cherchez avec des mots simples : « vidéos », « favoris », un mois, une année, « hier », le nom d'un album.")
            resultats.isEmpty() -> Vide(Icones.RECHERCHE, "Rien ne correspond à « ${q.trim()} ».")
            else -> GrilleMedias(resultats, null, false, parJour = true, basVide = 24.dp) {
                item(key = "nb", span = { GridItemSpan(maxLineSpan) }) { BasicText(compte(resultats), modifier = Modifier.padding(start = 20.dp, top = 10.dp), style = st(14f, a.encre2)) }
            }
        }
    }
}
