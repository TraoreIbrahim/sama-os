package africa.samaos.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite

/** La première page : la recherche dans toutes les notes, trois tuiles (Toutes, Listes, Rappels), les dossiers, la corbeille. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PageDossiers(aller: (Vue) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var recherche by remember { mutableStateOf("") }
    var nouveau by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<Dossier?>(null) }
    var modifier by remember { mutableStateOf<Dossier?>(null) }
    var aSupprimer by remember { mutableStateOf<Dossier?>(null) }
    val vivantes = Carnet.notes.filter { it.supprimee == null }
    val jetees = Carnet.notes.count { it.supprimee != null }
    val trouvees = if (recherche.isBlank()) emptyList() else vivantes.filter { correspond(it, recherche) }.sortedByDescending { it.modifiee }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barre(null) { Rond(Ic.NOUVEAU_DOSSIER, "Nouveau dossier") { nouveau = true } }
        LazyColumn(Modifier.weight(1f)) {
            item(key = "titre") { GrandTitre("Notes") }
            item(key = "recherche") {
                Recherche(recherche, "Rechercher dans toutes les notes") { recherche = it }
                Spacer(Modifier.height(16.dp))
            }
            if (recherche.isNotBlank()) {
                item(key = "nb") { Section(if (trouvees.isEmpty()) "Aucune note ne contient « ${recherche.trim()} »" else if (trouvees.size == 1) "1 note" else "${trouvees.size} notes", Modifier.animateItem()) }
                items(trouvees.size, key = { "r" + trouvees[it].id }) { i ->
                    val n = trouvees[i]
                    LigneNote(n, i, trouvees.size, Modifier.animateItem(), contexte = true, surligner = recherche.trim(), ouvrir = { aller(Vue.Edition(n.id)) })
                }
            } else {
                item(key = "tuiles") {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tuile(Ic.NOTE, teinte(0), "Toutes", vivantes.size, Modifier.weight(1f)) { aller(Vue.Liste(Filtre.Toutes)) }
                        Tuile(Ic.CASES, teinte(1), "Listes", vivantes.count { n -> n.cases.isNotEmpty() }, Modifier.weight(1f)) { aller(Vue.Liste(Filtre.Listes)) }
                        Tuile(Icones.CLOCHE, teinte(2), "Rappels", vivantes.count { (it.rappel ?: 0) > System.currentTimeMillis() }, Modifier.weight(1f)) { aller(Vue.Liste(Filtre.Rappels)) }
                    }
                }
                item(key = "mes") { Section("Mes dossiers", Modifier.animateItem()) }
                val tous = listOf(Dossier(Carnet.NOTES, "Notes", 0)) + Carnet.dossiers
                items(tous.size, key = { "d" + tous[it].id }) { i ->
                    val d = tous[i]
                    LigneDossier(
                        Icones.DOSSIER, teinte(d.teinte), d.nom, vivantes.count { it.dossier == d.id }, forme(i, tous.size), Modifier.animateItem(),
                        appuiLong = if (d.id == Carnet.NOTES) null else ({ options = d }),
                    ) { aller(Vue.Liste(Filtre.Dans(d.id))) }
                    if (i < tous.lastIndex) Box(Modifier.padding(horizontal = 16.dp).background(a.surface)) { Separateur(54.dp) }
                }
                if (jetees > 0) item(key = "corbeille") {
                    Spacer(Modifier.height(14.dp))
                    LigneDossier(Icones.CORBEILLE, a.encre2, "Supprimées récemment", jetees, RoundedCornerShape(22.dp), Modifier.animateItem()) { aller(Vue.Corbeille) }
                }
            }
            item(key = "fin") { Spacer(Modifier.height(24.dp)) }
        }
        Pied(
            when (vivantes.size) { 0 -> "Aucune note"; 1 -> "1 note"; else -> "${vivantes.size} notes" },
            Modifier.navigationBarsPadding(),
        ) { aller(Vue.Edition(System.currentTimeMillis())) }
    }
    if (nouveau || modifier != null) {
        val d = modifier
        DialogueDossier(d, fermer = { nouveau = false; modifier = null }) { nom, t ->
            if (d == null) Carnet.creerDossier(c, nom, t) else Carnet.changerDossier(c, d.copy(nom = nom, teinte = t))
        }
    }
    options?.let { d ->
        FeuilleAppli(fermer = { options = null }, titre = d.nom) {
            val fermer = LocalFermerFeuille.current
            ActionFeuille(Icones.CRAYON, "Renommer, changer la couleur") {
                modifier = d
                fermer()
            }
            ActionFeuille(Icones.CORBEILLE, "Supprimer le dossier", danger = true) {
                aSupprimer = d
                fermer()
            }
        }
    }
    aSupprimer?.let { d -> DialogueSupprimerDossier(d, fermer = { aSupprimer = null }) }
}

/** Supprimer un dossier : ses notes vont dans « Supprimées récemment ». */
@Composable
internal fun DialogueSupprimerDossier(d: Dossier, fermer: () -> Unit, apres: () -> Unit = {}) {
    val c = LocalContext.current
    val n = Carnet.notes.count { it.dossier == d.id && it.supprimee == null }
    DialogueAppli(
        "Supprimer « ${d.nom} » ?",
        if (n == 0) "Le dossier est vide." else (if (n == 1) "Sa note ira" else "Ses $n notes iront") + " dans « Supprimées récemment » : on peut les récupérer pendant 30 jours.",
        fermer,
    ) {
        val f = LocalFermerFeuille.current
        BoutonTexteAppli("Annuler", style = 't') { f() }
        BoutonTexteAppli("Supprimer") {
            Carnet.supprimerDossier(c, d.id)
            apres()
            f()
        }
    }
}

/** Créer ou changer un dossier : son nom, sa couleur. */
@Composable
internal fun DialogueDossier(d: Dossier?, fermer: () -> Unit, valider: (String, Int) -> Unit) {
    val a = LocalIdentite.current
    var nom by remember { mutableStateOf(d?.nom.orEmpty()) }
    var t by remember { mutableIntStateOf(d?.teinte ?: (Carnet.dossiers.size + 1) % TEINTES.size) }
    val focus = remember { FocusRequester() }
    // Le champ prend le clavier dès que le dialogue est posé (il arrive en s'animant).
    LaunchedEffect(Unit) {
        repeat(30) {
            androidx.compose.runtime.withFrameNanos { }
            try {
                focus.requestFocus()
                return@LaunchedEffect
            } catch (_: Exception) {
            }
        }
    }
    DialogueAppli(if (d == null) "Nouveau dossier" else "Le dossier", null, fermer, contenu = {
        Row(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(a.champ).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val couleur by animateColorAsState(teinte(t), label = "teinte")
            IconeTrait(Icones.DOSSIER, 22.dp, couleur)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                nom, { nom = it.take(40) }, singleLine = true, textStyle = st(17f, a.encre), cursorBrush = SolidColor(a.accent),
                modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Nom du dossier" },
                decorationBox = { champ -> Box(contentAlignment = Alignment.CenterStart) { if (nom.isEmpty()) BasicText("Nom", style = st(17f, a.encre2)); champ() } },
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TEINTES.indices.forEach { i ->
                val choisi = i == t
                val bord by animateDpAsState(if (choisi) 3.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "bord")
                Box(
                    Modifier.size(38.dp).clip(CircleShape).border(bord, a.fond, CircleShape).padding(0.dp).background(teinte(i).copy(alpha = if (choisi) 1f else 0.85f))
                        .clickable(onClickLabel = NOMS_TEINTES[i], role = Role.RadioButton) { t = i },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.AnimatedVisibility(choisi, enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.4f), exit = fadeOut()) {
                        IconeTrait(Icones.COCHE, 18.dp, Color.White, epaisseur = 2.4f)
                    }
                }
            }
        }
    }) {
        val f = LocalFermerFeuille.current
        BoutonTexteAppli("Annuler", style = 't') { f() }
        BoutonTexteAppli(if (d == null) "Créer" else "Garder", actif = nom.isNotBlank()) {
            valider(nom, t)
            f()
        }
    }
}

/** Une tuile : une pastille de couleur avec son icône, le nombre, le nom. */
@Composable
private fun Tuile(icone: String, couleur: Color, nom: String, nombre: Int, modifier: Modifier, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val source = remember { MutableInteractionSource() }
    val presse by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (presse) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "appui")
    Column(
        modifier.graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(22.dp)).background(a.surface)
            .clickable(interactionSource = source, indication = null, onClickLabel = nom, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$nom, $nombre" }
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(couleur), contentAlignment = Alignment.Center) { IconeTrait(icone, 18.dp, Color.White, epaisseur = 2f) }
            Spacer(Modifier.weight(1f))
            AnimatedContent(nombre, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "nombre") { n ->
                BasicText("$n", style = st(24f, a.encre, FontWeight.Bold, 1.1f))
            }
        }
        BasicText(nom, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp), style = st(15f, a.encre2, FontWeight.SemiBold))
    }
}

/** La forme d'une ligne dans un groupe : la première et la dernière ont les coins arrondis. */
internal fun forme(i: Int, n: Int): RoundedCornerShape {
    val r = 22.dp
    return RoundedCornerShape(topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp, bottomStart = if (i == n - 1) r else 0.dp, bottomEnd = if (i == n - 1) r else 0.dp)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneDossier(icone: String, couleur: Color, nom: String, nombre: Int, forme: RoundedCornerShape, modifier: Modifier, appuiLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(forme).background(a.surface)
            .combinedClickable(onClickLabel = nom, role = Role.Button, onLongClickLabel = if (appuiLong != null) "Options du dossier" else null, onLongClick = appuiLong, onClick = onClick)
            .heightIn(min = 54.dp).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTrait(icone, 24.dp, couleur)
        BasicText(nom, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp), style = st(17f, a.encre))
        BasicText("$nombre", style = st(16f, a.encre2))
        IconeTrait(Icones.CHEVRON, 16.dp, a.encre2.copy(alpha = 0.7f), Modifier.padding(start = 6.dp), epaisseur = 2f)
    }
}

/** La note contient-elle ce qu'on cherche ? (le contenu d'une note verrouillée reste caché : seul son titre compte). */
internal fun correspond(n: Note, q: String): Boolean {
    val t = q.trim()
    if (t.isEmpty()) return true
    return n.titre.contains(t, ignoreCase = true) || (!n.verrouillee && n.enTexte(puces = false).contains(t, ignoreCase = true))
}
