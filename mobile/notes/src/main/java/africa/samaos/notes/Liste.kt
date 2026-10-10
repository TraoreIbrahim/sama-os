package africa.samaos.notes

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Tri(val nom: String) { MODIFIEE("Date de modification"), CREEE("Date de création"), TITRE("Titre") }

/** Les notes d'un dossier (ou de toutes, des listes, des rappels) : les épinglées, puis par date ; en liste ou en galerie. */
@Composable
internal fun PageListe(filtre: Filtre, aller: (Vue) -> Unit, retour: () -> Unit) {
    val c = LocalContext.current
    val prefs = remember { c.getSharedPreferences("notes", Context.MODE_PRIVATE) }
    var galerie by remember { mutableStateOf(prefs.getBoolean("galerie", false)) }
    var tri by remember { mutableStateOf(Tri.entries.firstOrNull { it.name == prefs.getString("tri", null) } ?: Tri.MODIFIEE) }
    var recherche by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<Note?>(null) }
    var deplacer by remember { mutableStateOf<Note?>(null) }
    var modifierDossier by remember { mutableStateOf(false) }
    var supprimerDossier by remember { mutableStateOf(false) }
    val dossier = (filtre as? Filtre.Dans)?.dossier
    val maintenant = System.currentTimeMillis()
    val titre = when (filtre) {
        Filtre.Toutes -> "Toutes les notes"
        Filtre.Listes -> "Listes"
        Filtre.Rappels -> "Rappels"
        is Filtre.Dans -> Carnet.nomDossier(filtre.dossier)
    }
    val toutes = Carnet.notes.filter { n ->
        n.supprimee == null && when (filtre) {
            Filtre.Toutes -> true
            Filtre.Listes -> n.cases.isNotEmpty()
            Filtre.Rappels -> n.rappel != null
            is Filtre.Dans -> n.dossier == filtre.dossier
        }
    }
    val vues = toutes.filter { correspond(it, recherche) }
    // Les rappels : ceux à venir d'abord, du plus proche au plus lointain.
    val sections: List<Pair<String, List<Note>>> = if (filtre == Filtre.Rappels) {
        listOf(
            "À venir" to vues.filter { (it.rappel ?: 0) >= maintenant }.sortedBy { it.rappel },
            "Passés" to vues.filter { (it.rappel ?: 0) < maintenant }.sortedByDescending { it.rappel },
        ).filter { it.second.isNotEmpty() }
    } else {
        val triees = when (tri) {
            Tri.MODIFIEE -> vues.sortedByDescending { it.modifiee }
            Tri.CREEE -> vues.sortedByDescending { it.id }
            Tri.TITRE -> vues.sortedBy { nomDe(it).lowercase() }
        }
        val epinglees = triees.filter { it.epinglee }
        val autres = triees.filter { !it.epinglee }
        listOfNotNull("Épinglées" to epinglees) .filter { it.second.isNotEmpty() } +
            if (tri == Tri.TITRE) listOf("Notes" to autres).filter { it.second.isNotEmpty() }
            else autres.groupBy { Dates.section(if (tri == Tri.CREEE) it.id else it.modifiee) }.toList()
    }
    val ouvrir: (Note) -> Unit = { aller(Vue.Edition(it.id)) }
    val ecrire = { aller(Vue.Edition(System.currentTimeMillis(), dossier = dossier ?: Carnet.NOTES, cases = filtre == Filtre.Listes)) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barre("Dossiers", retour) { Rond(Ic.TROIS_POINTS, "Options de la liste", epaisseur = 2.8f) { menu = true } }
        val tete: @Composable () -> Unit = {
            GrandTitre(titre)
            Recherche(recherche, "Rechercher") { recherche = it }
            Spacer(Modifier.height(6.dp))
            if (vues.isEmpty()) Vide(filtre, recherche)
        }
        AnimatedContent(galerie, Modifier.weight(1f), transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "vue") { enGalerie ->
            if (enGalerie) {
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    item(key = "tete", span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.deborde(16.dp)) { tete() } }
                    sections.forEach { (nom, liste) ->
                        item(key = "s$nom", span = { GridItemSpan(maxLineSpan) }) { Section(nom, Modifier.animateItem().deborde(16.dp)) }
                        items(liste, key = { it.id }) { n -> CarteNote(n, Modifier.animateItem(), { ouvrir(n) }) { actions = n } }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "tete") { tete() }
                    sections.forEach { (nom, liste) ->
                        item(key = "s$nom") { Section(nom, Modifier.animateItem()) }
                        items(liste.size, key = { liste[it].id }) { i ->
                            val n = liste[i]
                            GlisseNote(
                                n, i, liste.size, Modifier.animateItem(),
                                epingler = { Carnet.changer(c, n.id) { it.copy(epinglee = !it.epinglee) } },
                                jeter = { jeterAvecAnnuler(c, n) },
                            ) { LigneNote(n, i, liste.size, Modifier, encadre = false, contexte = dossier == null, surligner = recherche.trim().takeIf { it.isNotEmpty() }, ouvrir = { ouvrir(n) }, options = { actions = n }) }
                        }
                    }
                    item(key = "fin") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
        Pied(
            when (toutes.size) { 0 -> "Aucune note"; 1 -> "1 note"; else -> "${toutes.size} notes" },
            Modifier.navigationBarsPadding(),
            ecrire = ecrire,
        )
    }
    if (menu) {
        FeuilleAppli(fermer = { menu = false }, titre = titre) {
            val fermer = LocalFermerFeuille.current
            ActionFeuille(if (galerie) Icones.LISTE else Icones.GRILLE, if (galerie) "Voir en liste" else "Voir en galerie") {
                galerie = !galerie
                prefs.edit().putBoolean("galerie", galerie).apply()
                fermer()
            }
            if (filtre != Filtre.Rappels) {
                BasicText("Trier par", modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 2.dp), style = st(13f, LocalIdentite.current.encre2, FontWeight.SemiBold))
                Tri.entries.forEach { t ->
                    ActionFeuille(if (t == tri) Icones.COCHE else "M0 0", t.nom) {
                        tri = t
                        prefs.edit().putString("tri", t.name).apply()
                        fermer()
                    }
                }
            }
            if (dossier != null && dossier != Carnet.NOTES) {
                Separateur(24.dp)
                ActionFeuille(Icones.CRAYON, "Renommer, changer la couleur") {
                    modifierDossier = true
                    fermer()
                }
                ActionFeuille(Icones.CORBEILLE, "Supprimer le dossier", danger = true) {
                    supprimerDossier = true
                    fermer()
                }
            }
        }
    }
    actions?.let { n -> FeuilleNote(n, fermer = { actions = null }, deplacer = { deplacer = n }) }
    deplacer?.let { n -> FeuilleDossiers(n.dossier, nomDe(n), { deplacer = null }) { d -> Carnet.changer(c, n.id) { it.copy(dossier = d) } } }
    val d = Carnet.dossiers.firstOrNull { it.id == dossier }
    if (modifierDossier && d != null) DialogueDossier(d, { modifierDossier = false }) { nom, t -> Carnet.changerDossier(c, d.copy(nom = nom, teinte = t)) }
    if (supprimerDossier && d != null) DialogueSupprimerDossier(d, { supprimerDossier = false }) { retour() }
}

@Composable
private fun Vide(filtre: Filtre, recherche: String) {
    val a = LocalIdentite.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) {
            IconeTrait(if (filtre == Filtre.Rappels) Icones.CLOCHE else if (filtre == Filtre.Listes) Ic.CASES else Ic.NOTE, 28.dp, a.accentTexte)
        }
        BasicText(
            when {
                recherche.isNotBlank() -> "Rien ne correspond à « ${recherche.trim()} »."
                filtre == Filtre.Rappels -> "Aucun rappel. Dans une note, la cloche vous prévient à l'heure dite."
                filtre == Filtre.Listes -> "Aucune liste. Les courses, les choses à faire : touchez le crayon pour en commencer une."
                else -> "Pas encore de note. Une idée, une adresse, une liste : touchez le crayon."
            },
            modifier = Modifier.padding(top = 14.dp),
            style = st(15f, a.encre2).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        )
    }
}

/** Un en-tête qui prend toute la largeur dans une grille à marges. */
private fun Modifier.deborde(marge: androidx.compose.ui.unit.Dp) = this.layout { m, k ->
    val e = marge.roundToPx()
    val p = m.measure(k.copy(minWidth = k.maxWidth + 2 * e, maxWidth = k.maxWidth + 2 * e))
    layout(k.maxWidth, p.height) { p.place(-e, 0) }
}

/** Le nom d'une note dans les listes : son titre, sinon sa première ligne. */
internal fun nomDe(n: Note) = n.titre.ifBlank { n.apercu() }.ifBlank { "Nouvelle note" }

/** Jeter une note, avec « Annuler » le temps de se raviser. */
internal fun jeterAvecAnnuler(c: Context, n: Note) {
    Carnet.jeter(c, n.id)
    Avis.dire("Note supprimée") { Carnet.recuperer(c, n.id) }
}

/** Glisser une ligne vers la droite : l'épingler ; vers la gauche : la supprimer. */
@Composable
private fun GlisseNote(n: Note, i: Int, total: Int, modifier: Modifier, epingler: () -> Unit, jeter: () -> Unit, contenu: @Composable () -> Unit) {
    val a = LocalIdentite.current
    var x by remember { mutableFloatStateOf(0f) }
    var large by remember { mutableIntStateOf(1) }
    val portee = rememberCoroutineScope()
    val seuil = large * 0.3f
    val part = (abs(x) / seuil).coerceIn(0f, 1f)
    Box(
        modifier.padding(horizontal = 16.dp).clip(forme(i, total)).onSizeChanged { large = it.width.coerceAtLeast(1) }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (n.epinglee) "Désépingler" else "Épingler") { epingler(); true },
                    CustomAccessibilityAction("Supprimer") { jeter(); true },
                )
            },
    ) {
        if (x != 0f) {
            val droite = x > 0
            Box(
                Modifier.matchParentSize().background(if (droite) a.accent.copy(alpha = 0.35f + 0.65f * part) else Color(0xFFE0705C).copy(alpha = 0.35f + 0.65f * part)),
                contentAlignment = if (droite) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                IconeTrait(
                    if (droite) (if (n.epinglee) Ic.SANS_EPINGLE else Ic.EPINGLE) else Icones.CORBEILLE, 24.dp, Color.White,
                    Modifier.padding(horizontal = 26.dp).graphicsLayer { val s = 0.7f + 0.3f * part; scaleX = s; scaleY = s },
                )
            }
        }
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), 0) }.draggable(
                rememberDraggableState { d -> x = (x + d).coerceIn(-large * 0.8f, large * 0.45f) },
                Orientation.Horizontal,
                onDragStopped = { v ->
                    when {
                        x > seuil || (v > 1800f && x > large * 0.1f) -> {
                            animate(x, 0f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f)) { b, _ -> x = b }
                            epingler()
                        }
                        x < -seuil * 1.4f || (v < -1800f && x < -large * 0.12f) -> {
                            animate(x, -large.toFloat(), animationSpec = tween(170)) { b, _ -> x = b }
                            jeter()
                            portee.launch {
                                kotlinx.coroutines.delay(700)
                                x = 0f
                            }
                        }
                        else -> animate(x, 0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)) { b, _ -> x = b }
                    }
                },
            ),
        ) { contenu() }
    }
}

/** Une note dans la liste : son nom, l'heure et la première ligne ; le dossier, le rappel, le verrou ; l'avancement d'une liste. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LigneNote(n: Note, i: Int, total: Int, modifier: Modifier, encadre: Boolean = true, contexte: Boolean = false, surligner: String? = null, ouvrir: () -> Unit, options: (() -> Unit)? = null, second: String? = null) {
    val a = LocalIdentite.current
    // Dans un groupe aux coins doux (sauf quand la ligne glissante s'en charge).
    val pad = if (encadre) Modifier.padding(horizontal = 16.dp).clip(forme(i, total)) else Modifier
    Column(
        modifier.then(pad).fillMaxWidth().background(a.surface)
            .combinedClickable(onClickLabel = "Ouvrir", role = Role.Button, onLongClickLabel = "Options", onLongClick = options, onClick = ouvrir),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(surligne(nomDe(n), surligner, a.voile), maxLines = 1, overflow = TextOverflow.Ellipsis, style = st(16.5f, a.encre, FontWeight.SemiBold))
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(second ?: Dates.courte(n.modifiee), style = st(14f, a.encre2, FontWeight.Medium))
                    // En cherchant : la ligne où se trouve le mot, si ce n'est pas le titre.
                    val trouvee = surligner?.takeIf { q -> !n.verrouillee && !nomDe(n).contains(q, ignoreCase = true) }?.let { q ->
                        n.blocs.asSequence().flatMap { it.texte.lineSequence() }.firstOrNull { it.contains(q, ignoreCase = true) }?.trim()
                    }
                    val ligne = when {
                        trouvee != null -> trouvee
                        n.verrouillee -> "Verrouillée"
                        n.titre.isBlank() -> n.blocs.asSequence().flatMap { it.texte.lineSequence() }.filter { it.isNotBlank() }.drop(1).firstOrNull()?.trim().orEmpty()
                        else -> n.apercu()
                    }.ifBlank { "Pas d'autre texte" }
                    BasicText(surligne(ligne, surligner, a.voile), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp), style = st(14f, a.encre2))
                }
                val dossier = contexte && (n.dossier != Carnet.NOTES || Carnet.dossiers.isNotEmpty())
                if (dossier || n.rappel != null) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (dossier) {
                            IconeTrait(Icones.DOSSIER, 14.dp, teinte(Carnet.teinteDossier(n.dossier)))
                            BasicText(Carnet.nomDossier(n.dossier), modifier = Modifier.padding(start = 5.dp, end = 10.dp), style = st(13f, a.encre2))
                        }
                        n.rappel?.let { r ->
                            val passe = r < System.currentTimeMillis()
                            IconeTrait(Icones.CLOCHE, 14.dp, if (passe) a.encre2 else a.accentTexte)
                            BasicText(Dates.rappel(r), modifier = Modifier.padding(start = 5.dp), style = st(13f, if (passe) a.encre2 else a.accentTexte, FontWeight.Medium))
                        }
                    }
                }
            }
            when {
                n.verrouillee -> Box(Modifier.padding(start = 10.dp).size(34.dp).clip(CircleShape).background(a.champ), contentAlignment = Alignment.Center) { IconeTrait(Icones.CADENAS, 17.dp, a.encre2) }
                n.cases.isNotEmpty() -> Avancement(n.cases.count { it.coche }, n.cases.size, Modifier.padding(start = 10.dp))
                n.epinglee -> IconeTrait(Ic.EPINGLE, 16.dp, a.accentTexte, Modifier.padding(start = 10.dp))
            }
        }
        if (i < total - 1) Separateur(16.dp)
    }
}

/** L'avancement d'une liste : un anneau qui se remplit, et « 3/5 ». */
@Composable
private fun Avancement(faits: Int, total: Int, modifier: Modifier) {
    val a = LocalIdentite.current
    val part by androidx.compose.animation.core.animateFloatAsState(if (total == 0) 0f else faits.toFloat() / total, spring(stiffness = 300f), label = "avancement")
    Box(modifier.size(38.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val e = 3.dp.toPx()
            drawCircle(a.trait.copy(alpha = 0.6f), size.minDimension / 2 - e / 2, style = Stroke(e))
            drawArc(if (faits == total) Color(0xFF7FA27A) else a.accent, -90f, 360f * part, false, topLeft = androidx.compose.ui.geometry.Offset(e / 2, e / 2), size = androidx.compose.ui.geometry.Size(size.width - e, size.height - e), style = Stroke(e, cap = StrokeCap.Round))
        }
        if (faits == total) IconeTrait(Icones.COCHE, 16.dp, Color(0xFF7FA27A), epaisseur = 2.4f)
        else BasicText("$faits/$total", style = st(10.5f, a.encre2, FontWeight.SemiBold))
    }
}

/** Le mot cherché, surligné de miel. */
internal fun surligne(t: String, q: String?, couleur: Color) = buildAnnotatedString {
    if (q.isNullOrEmpty()) {
        append(t)
        return@buildAnnotatedString
    }
    var debut = 0
    while (true) {
        val i = t.indexOf(q, debut, ignoreCase = true)
        if (i < 0) break
        append(t.substring(debut, i))
        withStyle(SpanStyle(background = couleur)) { append(t.substring(i, i + q.length)) }
        debut = i + q.length
    }
    append(t.substring(debut))
}

/** Une note en galerie : un aperçu de la page, puis son nom et sa date. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarteNote(n: Note, modifier: Modifier, ouvrir: () -> Unit, options: () -> Unit) {
    val a = LocalIdentite.current
    Column(modifier.padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.82f).clip(RoundedCornerShape(18.dp)).background(a.surface).border(0.6.dp, a.trait, RoundedCornerShape(18.dp))
                .combinedClickable(onClickLabel = "Ouvrir ${nomDe(n)}", role = Role.Button, onLongClickLabel = "Options", onLongClick = options, onClick = ouvrir),
        ) {
            if (n.verrouillee) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconeTrait(Icones.CADENAS, 30.dp, a.encre2)
                    BasicText("Verrouillée", modifier = Modifier.padding(top = 6.dp), style = st(12f, a.encre2))
                }
            } else {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (n.titre.isNotBlank()) BasicText(n.titre, maxLines = 2, overflow = TextOverflow.Ellipsis, style = st(12.5f, a.encre, FontWeight.Bold))
                    var numero = 0
                    n.blocs.take(12).forEach { b ->
                        if (b.genre == Genre.NUMERO) numero++ else numero = 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (b.genre) {
                                Genre.CASE -> Box(Modifier.size(9.dp).clip(CircleShape).then(if (b.coche) Modifier.background(a.accent) else Modifier.border(1.dp, a.encre2, CircleShape)))
                                Genre.PUCE -> BasicText("•", style = st(10.5f, a.encre2))
                                Genre.NUMERO -> BasicText("$numero.", style = st(10.5f, a.encre2))
                                else -> {}
                            }
                            if (b.genre.liste) Spacer(Modifier.width(5.dp))
                            BasicText(
                                b.texte, maxLines = if (b.genre == Genre.TEXTE) 4 else 1, overflow = TextOverflow.Ellipsis,
                                style = when (b.genre) {
                                    Genre.TITRE -> st(12f, a.encre, FontWeight.Bold)
                                    Genre.INTERTITRE -> st(11f, a.encre, FontWeight.SemiBold)
                                    else -> st(10.5f, if (b.coche) a.encre2 else a.encre).copy(textDecoration = if (b.coche) TextDecoration.LineThrough else null)
                                },
                            )
                        }
                    }
                }
                // La page s'efface doucement vers le bas.
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp).background(Brush.verticalGradient(listOf(a.surface.copy(alpha = 0f), a.surface))))
            }
            if (n.epinglee) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) {
                IconeTrait(Ic.EPINGLE, 13.dp, a.accentTexte)
            }
        }
        BasicText(nomDe(n), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), style = st(14f, a.encre, FontWeight.SemiBold))
        BasicText(Dates.courte(n.modifiee), style = st(12.5f, a.encre2))
    }
}

/** Ce qu'on fait d'une note sans l'ouvrir : l'épingler, la déplacer, la partager, la supprimer. */
@Composable
internal fun FeuilleNote(n: Note, fermer: () -> Unit, deplacer: () -> Unit) {
    val c = LocalContext.current
    FeuilleAppli(fermer = fermer, titre = nomDe(n)) {
        val f = LocalFermerFeuille.current
        ActionFeuille(if (n.epinglee) Ic.SANS_EPINGLE else Ic.EPINGLE, if (n.epinglee) "Désépingler" else "Épingler") {
            Carnet.changer(c, n.id) { it.copy(epinglee = !it.epinglee) }
            f()
        }
        ActionFeuille(Ic.DEPLACER, "Déplacer", second = "Dans « ${Carnet.nomDossier(n.dossier)} »") {
            deplacer()
            f()
        }
        if (!n.verrouillee) ActionFeuille(Icones.PARTAGER, "Partager") {
            partager(c, n)
            f()
        }
        ActionFeuille(Icones.CORBEILLE, "Supprimer", danger = true) {
            jeterAvecAnnuler(c, n)
            f()
        }
    }
}

internal fun partager(c: Context, n: Note) {
    val texte = listOf(n.titre, n.enTexte()).filter { it.isNotBlank() }.joinToString("\n\n")
    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, n.titre).putExtra(Intent.EXTRA_TEXT, texte), "Partager la note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** « Supprimées récemment » : on récupère une note, ou on la supprime pour de bon. */
@Composable
internal fun PageCorbeille(retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    var choisie by remember { mutableStateOf<Note?>(null) }
    var vider by remember { mutableStateOf(false) }
    val jetees = Carnet.notes.filter { it.supprimee != null }.sortedByDescending { it.supprimee }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barre("Dossiers", retour) {
            if (jetees.isNotEmpty()) BasicText(
                "Tout supprimer",
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Tout supprimer", role = Role.Button) { vider = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                style = st(16f, Color(0xFFC4573E), FontWeight.Medium),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            item(key = "titre") {
                GrandTitre("Supprimées récemment")
                BasicText("Les notes restent ici 30 jours, puis partent pour de bon. Touchez une note pour la récupérer.", modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 12.dp), style = st(14f, a.encre2))
            }
            if (jetees.isEmpty()) item(key = "vide") { Vide(Filtre.Toutes, "") }
            items(jetees.size, key = { jetees[it].id }) { i ->
                val n = jetees[i]
                LigneNote(n, i, jetees.size, Modifier.animateItem(), contexte = false, ouvrir = { choisie = n }, second = Dates.restant(n.supprimee ?: 0))
            }
            item(key = "fin") { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
    choisie?.let { n ->
        FeuilleAppli(fermer = { choisie = null }, titre = nomDe(n)) {
            val f = LocalFermerFeuille.current
            ActionFeuille(Icones.RESTAURER, "Récupérer", second = "Elle revient dans « ${Carnet.nomDossier(n.dossier)} »") {
                Carnet.recuperer(c, n.id)
                Avis.dire("Note récupérée")
                f()
            }
            ActionFeuille(Icones.CORBEILLE, "Supprimer pour de bon", danger = true) {
                Carnet.effacer(c, n.id)
                f()
            }
        }
    }
    if (vider) DialogueAppli("Tout supprimer pour de bon ?", "${jetees.size} ${if (jetees.size == 1) "note partira" else "notes partiront"}. On ne pourra plus les récupérer.", { vider = false }) {
        val f = LocalFermerFeuille.current
        BoutonTexteAppli("Annuler", style = 't') { f() }
        BoutonTexteAppli("Tout supprimer") {
            Carnet.viderCorbeille(c)
            f()
        }
    }
}
