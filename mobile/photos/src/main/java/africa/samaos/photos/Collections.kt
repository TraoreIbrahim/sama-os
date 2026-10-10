package africa.samaos.photos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.LocalIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Ce qu'on peut libérer, par sorte : les éléments, ceux qu'on propose de jeter, la place gagnée. */
class Place(val sorte: Nettoyage, val elements: List<Media>, val proposes: Set<Long>, val groupes: List<List<Media>> = emptyList()) {
    val poids get() = elements.filter { it.id in proposes }.sumOf { it.taille }.takeIf { it > 0 } ?: elements.sumOf { it.taille }
}

private const val TRENTE_JOURS = 30L * 24 * 3600 * 1000

/** Ce que la pellicule permet de libérer (les doublons se reconnaissent à leur contenu : un peu plus long). */
fun place(c: android.content.Context, tout: List<Media>): List<Place> {
    val maintenant = System.currentTimeMillis()
    val doublons = Galerie.doublons(c, tout)
    val grosses = tout.filter { it.video && it.taille > 0 }.sortedByDescending { it.taille }.take(40)
    val captures = tout.filter { Galerie.estCapture(it) && it.date < maintenant - TRENTE_JOURS }
    val whatsapp = tout.filter { Galerie.estWhatsApp(it) }.sortedByDescending { it.taille }
    return listOf(
        Place(Nettoyage.DOUBLONS, doublons.flatten(), doublons.flatMap { g -> g.drop(1).map { it.id } }.toSet(), doublons),
        Place(Nettoyage.VIDEOS, grosses, emptySet()),
        Place(Nettoyage.CAPTURES, captures, captures.map { it.id }.toSet()),
        Place(Nettoyage.WHATSAPP, whatsapp, emptySet()),
    ).filter { it.elements.isNotEmpty() }
}

/** Les collections : ce jour-là, les albums, les sortes de photos, faire de la place, la corbeille. */
@Composable
fun PageCollections() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val tout = LocalTout.current.orEmpty()
    val albums = remember(tout) { Galerie.albums(tout).filter { it.sorte == Sorte.ALBUM } }
    val auj = LocalDate.now()
    val ceJour = remember(tout) { tout.filter { it.jour.monthValue == auj.monthValue && it.jour.dayOfMonth == auj.dayOfMonth && it.jour.year < auj.year } }
    val jetes by produceState(0, x.version) { value = withContext(Dispatchers.IO) { Galerie.corbeille(c).size } }
    val places by produceState<List<Place>?>(null, tout) { value = withContext(Dispatchers.IO) { place(c, tout) } }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { Spacer(Modifier.height(8.dp)); GrandTitre("Collections", compte(tout)) }
            // Ce jour-là, les années d'avant.
            if (ceJour.isNotEmpty()) item {
                Section("Ce jour-là")
                val annees = auj.year - ceJour.first().jour.year
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(210.dp).clip(RoundedCornerShape(26.dp))
                        .clickable(onClickLabel = "Voir ce jour-là", role = Role.Button) { x.aller(Vue.Liste("Ce jour-là", Ensemble.CeJour)) },
                ) {
                    Vignette(ceJour.first(), Modifier.fillMaxSize(), retenirCadre = false)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        BasicText(if (annees == 1) "Il y a un an" else "Il y a $annees ans", style = st(22f, Color.White, FontWeight.Bold))
                        BasicText(compte(ceJour), style = st(14f, Color(0xE6FFFFFF)))
                    }
                }
            }
            if (albums.isNotEmpty()) item {
                Section("Albums")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(albums, key = { it.id }) { al ->
                        Column(Modifier.width(150.dp).clickable(onClickLabel = al.nom, role = Role.Button) { x.aller(Vue.Liste(al.nom, Ensemble.UnAlbum(al.id))) }.padding(bottom = 4.dp)) {
                            Vignette(al.couverture, Modifier.size(150.dp).clip(RoundedCornerShape(20.dp)), retenirCadre = false)
                            BasicText(al.nom, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 2.dp), style = st(15f, a.encre, FontWeight.SemiBold))
                            BasicText(nombre(al.nombre), modifier = Modifier.padding(start = 2.dp), style = st(13.5f, a.encre2))
                        }
                    }
                }
            }
            // Les sortes de photos.
            val sortes = listOf(
                Triple("Vidéos", Icones.VIDEO, Ensemble.Videos) to tout.count { it.video },
                Triple("Favoris", Icones.COEUR, Ensemble.Favoris) to tout.count { it.favori },
                Triple("Captures d'écran", Icones.IMAGE, Ensemble.Captures) to tout.count { Galerie.estCapture(it) },
                Triple("Documents scannés", Icones.DOCUMENT, Ensemble.Documents) to tout.count { Galerie.estDocument(it) },
            ).filter { it.second > 0 }
            if (sortes.isNotEmpty()) item {
                Section("Par sorte")
                Groupe {
                    sortes.forEachIndexed { i, (s, n) ->
                        LigneGroupe(s.second, s.first, nombre(n), i < sortes.lastIndex) { x.aller(Vue.Liste(s.first, s.third)) }
                    }
                }
            }
            item {
                Section("Faire de la place")
                CartePlace(tout, places)
            }
            item {
                Spacer(Modifier.height(16.dp))
                Groupe {
                    LigneGroupe(Icones.CADENAS, "Masquées", "", true, "S'ouvre avec le déverrouillage du téléphone") { x.aller(Vue.Masquees) }
                    LigneGroupe(Icones.CORBEILLE, "Corbeille", if (jetes > 0) nombre(jetes) else "", false, "30 jours pour changer d'avis") { x.aller(Vue.Corbeille) }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) { Onglets(1) }
    }
}

@Composable
fun Section(texte: String) {
    BasicText(texte, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp), style = st(19f, LocalIdentite.current.encre, FontWeight.Bold))
}

@Composable
fun Groupe(contenu: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LocalIdentite.current.surface)) { contenu() }
}

@Composable
fun LigneGroupe(icone: String, nom: String, valeur: String, filet: Boolean, second: String? = null, couleur: Color? = null, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = nom, role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconeTrait(icone, 22.dp, couleur ?: a.accentTexte)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                BasicText(nom, style = st(16.5f, a.encre))
                if (second != null) BasicText(second, style = st(13f, a.encre2))
            }
            BasicText(valeur, style = st(15f, a.encre2))
            IconeTrait(Icones.CHEVRON, 15.dp, a.encre2.copy(alpha = 0.7f), Modifier.padding(start = 6.dp), epaisseur = 2f)
        }
        if (filet) Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(0.6.dp).background(a.trait))
    }
}

/** La place que prennent les photos et les vidéos, et ce qu'on peut libérer. */
@Composable
private fun CartePlace(tout: List<Media>, places: List<Place>?) {
    val a = LocalIdentite.current
    val x = LocalActions.current
    val photos = tout.filter { !it.video }.sumOf { it.taille }
    val videos = tout.filter { it.video }.sumOf { it.taille }
    val total = (photos + videos).coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(a.surface)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            BasicText("${taille(photos + videos)} sur ce téléphone", style = st(17f, a.encre, FontWeight.SemiBold))
            val part by animateFloatAsState(photos.toFloat() / total, spring(stiffness = 200f), label = "part")
            // La barre : les photos, puis les vidéos.
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(a.champ)) {
                if (part > 0f) Box(Modifier.weight(part.coerceAtLeast(0.001f)).fillMaxHeight().background(a.accent))
                if (part < 1f) Box(Modifier.weight((1f - part).coerceAtLeast(0.001f)).fillMaxHeight().background(a.accentTexte.copy(alpha = 0.45f)))
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(a.accent))
                BasicText("Photos · ${taille(photos)}", modifier = Modifier.padding(start = 6.dp, end = 14.dp), style = st(13f, a.encre2))
                Box(Modifier.size(8.dp).clip(CircleShape).background(a.accentTexte.copy(alpha = 0.45f)))
                BasicText("Vidéos · ${taille(videos)}", modifier = Modifier.padding(start = 6.dp), style = st(13f, a.encre2))
            }
        }
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(a.trait))
        when {
            places == null -> BasicText("On regarde ce qui se répète…", modifier = Modifier.padding(16.dp), style = st(14f, a.encre2))
            places.isEmpty() -> BasicText("Rien à libérer : pas de doublon, pas de grosse vidéo, pas de vieille capture.", modifier = Modifier.padding(16.dp), style = st(14f, a.encre2))
            else -> places.forEachIndexed { i, p ->
                val icone = when (p.sorte) {
                    Nettoyage.DOUBLONS -> Icones.IMAGE
                    Nettoyage.VIDEOS -> Icones.VIDEO
                    Nettoyage.CAPTURES -> Icones.IMAGE
                    Nettoyage.WHATSAPP -> Icones.MESSAGE
                }
                val second = when (p.sorte) {
                    Nettoyage.DOUBLONS -> "${p.groupes.size} groupe${if (p.groupes.size > 1) "s" else ""} · libère ${taille(p.poids)}"
                    Nettoyage.CAPTURES -> "${p.elements.size} de plus de 30 jours · ${taille(p.poids)}"
                    else -> "${p.elements.size} · ${taille(p.elements.sumOf { it.taille })}"
                }
                LigneGroupe(icone, p.sorte.titre, "", i < places.lastIndex, second) { x.aller(Vue.Menage(p.sorte)) }
            }
        }
    }
}

/** Faire de la place : ce qu'on propose de jeter est déjà choisi ; on décoche ce qu'on garde, puis à la corbeille (30 jours). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageMenage(sorte: Nettoyage) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val tout = LocalTout.current.orEmpty()
    val portee = rememberCoroutineScope()
    val p by produceState<Place?>(null, tout) { value = withContext(Dispatchers.IO) { place(c, tout).firstOrNull { it.sorte == sorte } ?: Place(sorte, emptyList(), emptySet()) } }
    val choix = remember { mutableStateListOf<Long>() }
    var pret by remember { mutableStateOf(false) }
    LaunchedEffect(p) {
        val pl = p ?: return@LaunchedEffect
        if (!pret) {
            choix.clear()
            choix.addAll(pl.proposes)
            pret = true
        }
    }
    val l = p?.elements.orEmpty()
    val poids = l.filter { it.id in choix }.sumOf { it.taille }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Barre("Collections", x.retour) {
                if (l.isNotEmpty()) Capsule(if (choix.size == l.size) "Tout décocher" else "Tout choisir") {
                    if (choix.size == l.size) choix.clear() else {
                        choix.clear()
                        choix.addAll(l.map { it.id })
                    }
                }
            }
            if (p != null && l.isEmpty()) {
                GrandTitre(sorte.titre)
                Vide(Ic.PLACE, "Rien à libérer ici.")
            } else if (p != null) {
                GrilleMedias(
                    l, choix, enChoix = true, colonnesDepart = 3, parJour = false, basVide = 120.dp,
                    badge = if (sorte == Nettoyage.VIDEOS || sorte == Nettoyage.WHATSAPP || sorte == Nettoyage.DOUBLONS) ({ m -> taille(m.taille) }) else null,
                ) {
                    item(key = "tete", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            GrandTitre(sorte.titre, compte(l))
                            BasicText(sorte.explication, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp), style = st(14.5f, a.encre2, interligne = 1.45f))
                        }
                    }
                }
            }
        }
        // Le bouton qui libère la place, en bas : tout part à la corbeille, on peut annuler.
        if (choix.isNotEmpty()) Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp).height(56.dp).clip(RoundedCornerShape(28.dp)).background(a.accent)
                .clickable(role = Role.Button) {
                    val partis = l.filter { it.id in choix }
                    portee.launch {
                        withContext(Dispatchers.IO) { partis.forEach { Galerie.jeter(c, it) } }
                        x.changer()
                        x.dire("${partis.size} élément${if (partis.size > 1) "s" else ""} à la corbeille · ${taille(partis.sumOf { it.taille })} libérés") {
                            portee.launch {
                                withContext(Dispatchers.IO) { partis.forEach { Galerie.remettre(c, it) } }
                                x.changer()
                            }
                        }
                        x.retour()
                    }
                }.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconeTrait(Icones.CORBEILLE, 20.dp, a.surAccent)
            BasicText("Libérer ${taille(poids)} · ${choix.size}", modifier = Modifier.padding(start = 10.dp), style = st(16f, a.surAccent, FontWeight.SemiBold, 1.1f))
        }
    }
}
