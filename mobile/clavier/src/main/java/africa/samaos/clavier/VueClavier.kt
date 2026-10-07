package africa.samaos.clavier

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private object Pictos {
    const val MAJ = "M12 4l7 8h-4v7H9v-7H5z"
    const val MAJ_VERROU = "M12 4l7 8h-4v5H9v-5H5z M8 21h8"
    const val EFFACER = "M9 6h11v12H9l-6-6z M12 10l4 4 M16 10l-4 4"
    const val ENTREE = "M19 5v7H6 M10 8l-4 4l4 4"
    const val ALLER = "M5 12h14 M13 6l6 6l-6 6"
    const val CHERCHER = "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z M20 20l-4.8-4.8"
    const val ENVOYER = "M4 12l16-8l-6 16l-3-7z M11 13l9-9"
    const val FINI = "M5 12.5l4.5 4.5L19 7.5"
    const val EMOJI = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M8.5 14a4 4 0 0 0 7 0 M9 9.5h.01 M15 9.5h.01"
    const val RECENTS = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7v5l3 2"
    const val CADENAS = "M6 11h12v9H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3"
    const val REGLAGES = "M4 7h10 M18 7h2 M4 17h4 M12 17h8 M16 5v4 M10 15v4"
    const val LANGUE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9s1.3-6.4 3.8-9z"
    const val CURSEUR = "M7 8l-4 4l4 4 M17 8l4 4l-4 4 M3 12h18"
}

private val HAUTEUR_TOUCHE = 46.dp
private val ECART = 8.dp

@Composable
fun VueClavier(e: Etat, k: Clavier) {
    val b = LocalBanco.current
    Box(Modifier.fillMaxWidth().background(b.sol2)) {
        // Assez d'air sous la dernière rangée : la barre du système (retour, changer de clavier) est juste dessous.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 4.dp, end = 4.dp, bottom = 16.dp)) {
            Bandeau(e, k)
            Box(Modifier.fillMaxWidth().height(HAUTEUR_TOUCHE * 4 + ECART * 3)) {
                when (e.page) {
                    Page.EMOJI -> PanneauEmoji(e, k)
                    Page.CHIFFRES -> Rangees(Dispositions.chiffres(), e, k)
                    Page.NOMBRES -> Rangees(Dispositions.nombres(), e, k)
                    Page.SYMBOLES -> Rangees(Dispositions.symboles(e.virgule), e, k)
                    Page.SYMBOLES_2 -> Rangees(Dispositions.symboles2(e.virgule), e, k)
                    Page.LETTRES -> Rangees(Dispositions.lettres(e.langue, e.virgule, e.langues.size > 1), e, k)
                }
                // L'espace maintenu : les touches s'effacent, le doigt déplace le curseur. Le voile descend un peu plus
                // bas que les touches pour couvrir aussi leur relief (l'ombre d'un point dessous).
                if (e.curseur) Box(
                    Modifier.matchParentSize().drawBehind { drawRect(b.sol2, size = size.copy(height = size.height + 3.dp.toPx())) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconeTrait(Pictos.CURSEUR, 28.dp, b.encre2)
                        BasicText(
                            if (e.multiligne) "Glissez pour déplacer le curseur, aussi de ligne en ligne" else "Glissez pour déplacer le curseur",
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = b.encre2),
                        )
                    }
                }
            }
        }
        e.variantes?.let { Variantes(it.first, it.second, it.third, e, k) }
    }
}

/** Le bandeau : les propositions, les lettres du julakan, ou le rappel qu'un champ secret ne retient rien. */
@Composable
private fun Bandeau(e: Etat, k: Clavier) {
    val b = LocalBanco.current
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            e.secret -> {
                IconeTrait(Pictos.CADENAS, 16.dp, b.encre2)
                Spacer(Modifier.width(8.dp))
                BasicText("Champ secret : rien n'est retenu ni proposé", modifier = Modifier.weight(1f), maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = b.encre2))
            }
            e.propositions.isNotEmpty() -> e.propositions.forEachIndexed { i, p ->
                if (i > 0) Box(Modifier.width(0.5.dp).height(22.dp).background(b.trait))
                BasicText(
                    p, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { k.vibrer(); k.choisirProposition(p) }.padding(vertical = 10.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal, fontSize = 16.sp, color = b.encre, textAlign = TextAlign.Center),
                )
            }
            e.langue == Langue.DY && e.page == Page.LETTRES -> Dispositions.JULAKAN.forEach { t ->
                val ton = t.length == 1 && t[0].code in 0x300..0x36F
                val lettre = if (!ton && e.maj > 0) t.uppercase() else t
                Box(
                    Modifier.weight(1f).fillMaxHeight().padding(2.dp).clip(RoundedCornerShape(10.dp)).background(b.sol)
                        .clickable(onClickLabel = if (ton) (if (t == "̀") "Ton bas" else "Ton haut") else lettre, role = Role.Button) { k.vibrer(); k.ecrire(lettre) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(if (ton) "◌$t" else lettre, style = TextStyle(fontFamily = Polices.corps, fontSize = 19.sp, color = b.encre))
                }
            }
            else -> Spacer(Modifier.weight(1f))
        }
        if (e.propositions.isEmpty() && !(e.langue == Langue.DY && e.page == Page.LETTRES)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClickLabel = "Réglages du clavier", role = Role.Button) { k.reglages() }, contentAlignment = Alignment.Center) {
                IconeTrait(Pictos.REGLAGES, 18.dp, b.encre2)
            }
        }
    }
}

@Composable
private fun Rangees(rangees: List<List<Touche>>, e: Etat, k: Clavier) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ECART)) {
        rangees.forEachIndexed { i, r ->
            // La deuxième rangée de l'AZERTY anglais (9 touches) est centrée, comme sur les claviers.
            val retrait = if (r.size == 9 && r.all { it.fn == null }) 0.5f else 0f
            Row(Modifier.fillMaxWidth().height(HAUTEUR_TOUCHE), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (retrait > 0) Spacer(Modifier.weight(retrait))
                r.forEach { t -> ToucheVue(t, e, k, i) }
                if (retrait > 0) Spacer(Modifier.weight(retrait))
            }
        }
    }
}

@Composable
private fun RowScope.ToucheVue(t: Touche, e: Etat, k: Clavier, rangee: Int) {
    val b = LocalBanco.current
    val portee = rememberCoroutineScope()
    var appuyee by remember { mutableStateOf(false) }
    var bornes by remember { mutableStateOf(Rect.Zero) }
    val fonction = t.fn != null && t.fn != Fn.ESPACE && !t.grande
    val fond = when {
        appuyee -> b.trait.copy(alpha = 0.25f)
        fonction -> Color.Transparent
        else -> b.sol
    }
    val libelle = when {
        t.fn == null && e.maj > 0 && e.page == Page.LETTRES -> t.texte.uppercase()
        else -> t.texte
    }
    val description = when (t.fn) {
        Fn.MAJ -> if (e.maj == 2) "Majuscules verrouillées" else "Majuscule"
        Fn.EFFACER -> "Effacer"
        Fn.ENTREE -> "Valider"
        Fn.EMOJI -> "Emoji"
        Fn.ESPACE -> "Espace ; maintenir pour déplacer le curseur"
        Fn.LANGUE -> "Langue : ${e.langue.nom}"
        else -> libelle
    }
    Box(
        Modifier
            .weight(t.largeur)
            .fillMaxHeight()
            .onGloballyPositioned { bornes = it.boundsInRoot() }
            .drawBehind {
                // Le léger relief des touches de Banco (une ombre d'un point sous la touche).
                if (!fonction && !appuyee) drawRoundRect(b.trait.copy(alpha = 0.35f), topLeft = Offset(0f, 1.5f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
            }
            .clip(RoundedCornerShape(10.dp))
            .background(fond)
            .semantics { contentDescription = description }
            .pointerInput(t, e.page, e.langue) {
                if (t.fn == Fn.ESPACE && t.texte.isBlank()) {
                    espaceOuPave(e, k, { appuyee = it })
                    return@pointerInput
                }
                awaitEachGesture {
                    awaitFirstDown()
                    appuyee = true
                    k.vibrer()
                    var repete: Job? = null
                    if (t.fn == Fn.EFFACER) {
                        // Effacer : tout de suite, puis en continu tant qu'on appuie.
                        k.effacer()
                        repete = portee.launch {
                            delay(420)
                            while (true) {
                                k.effacer()
                                delay(55)
                            }
                        }
                    }
                    val relache = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                    if (relache != null) {
                        repete?.cancel()
                        if (t.fn != Fn.EFFACER) toucher(t, e, k)
                    } else {
                        // Appui long : les accents d'une lettre, les langues sur l'espace.
                        if (t.fn == null) {
                            val v = Dispositions.variantes(libelle, e.langue)
                            if (v.isNotEmpty()) e.variantes = Triple(t, v, bornes)
                        } else if (t.fn == Fn.LANGUE) {
                            e.variantes = Triple(t, e.langues.map { it.nom }, bornes)
                        }
                        waitForUpOrCancellation()
                        repete?.cancel()
                    }
                    appuyee = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (t.fn) {
            Fn.MAJ -> IconeTrait(if (e.maj == 2) Pictos.MAJ_VERROU else Pictos.MAJ, 20.dp, if (e.maj > 0) b.lateriteTexte else b.encre)
            Fn.EFFACER -> IconeTrait(Pictos.EFFACER, 22.dp, b.encre)
            Fn.EMOJI -> IconeTrait(Pictos.EMOJI, 20.dp, b.encre)
            Fn.LANGUE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconeTrait(Pictos.LANGUE, 18.dp, b.encre)
                BasicText(e.langue.code.uppercase(), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = b.encre2))
            }
            Fn.ENTREE -> Box(
                Modifier.fillMaxSize().padding(horizontal = 2.dp).clip(RoundedCornerShape(10.dp)).background(if (e.action != EditorInfo.IME_ACTION_NONE && e.action != EditorInfo.IME_ACTION_UNSPECIFIED) b.laterite else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                val plein = e.action != EditorInfo.IME_ACTION_NONE && e.action != EditorInfo.IME_ACTION_UNSPECIFIED
                IconeTrait(
                    when (e.action) {
                        EditorInfo.IME_ACTION_SEARCH -> Pictos.CHERCHER
                        EditorInfo.IME_ACTION_SEND -> Pictos.ENVOYER
                        EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> Pictos.ALLER
                        EditorInfo.IME_ACTION_DONE -> Pictos.FINI
                        else -> Pictos.ENTREE
                    },
                    20.dp, if (plein) b.surLaterite else b.encre,
                )
            }
            Fn.ESPACE -> BasicText(
                if (t.texte.isNotBlank()) t.texte else if (e.langues.size > 1 && e.page == Page.LETTRES) e.langue.nom else "espace",
                style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = b.encre2),
            )
            Fn.SYMBOLES, Fn.SYMBOLES_2, Fn.LETTRES -> BasicText(t.texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = b.encre))
            else -> BasicText(libelle, style = TextStyle(fontFamily = Polices.corps, fontSize = if (t.grande) 24.sp else 19.sp, color = b.encre))
        }
    }
}

/**
 * L'espace : un toucher écrit une espace ; glisser ou maintenir en fait un pavé qui déplace le curseur, d'un
 * caractère tous les 10 dp de côté, d'une ligne tous les 34 dp en hauteur (dans un champ de plusieurs lignes).
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.espaceOuPave(e: Etat, k: Clavier, appuyee: (Boolean) -> Unit) {
    val pasX = 10.dp.toPx()
    val pasY = 34.dp.toPx()
    awaitEachGesture {
        val bas = awaitFirstDown()
        appuyee(true)
        k.vibrer()
        val debut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ch = awaitPointerEvent().changes.first()
                if (!ch.pressed) return@withTimeoutOrNull "toucher"
                if (kotlin.math.abs(ch.position.x - bas.position.x) > viewConfiguration.touchSlop) return@withTimeoutOrNull "glisser"
            }
            @Suppress("UNREACHABLE_CODE")
            "toucher"
        }
        if (debut == "toucher") {
            k.espace()
        } else {
            e.curseur = true
            var avant = bas.position
            var x = 0f
            var y = 0f
            while (true) {
                val ch = awaitPointerEvent().changes.first()
                if (!ch.pressed) break
                x += ch.position.x - avant.x
                y += ch.position.y - avant.y
                avant = ch.position
                ch.consume()
                while (x >= pasX) { k.deplacer(1, 0); x -= pasX }
                while (x <= -pasX) { k.deplacer(-1, 0); x += pasX }
                while (y >= pasY) { k.deplacer(0, 1); y -= pasY }
                while (y <= -pasY) { k.deplacer(0, -1); y += pasY }
            }
            e.curseur = false
        }
        appuyee(false)
    }
}

private fun toucher(t: Touche, e: Etat, k: Clavier) {
    when (t.fn) {
        null -> k.ecrire(t.texte)
        Fn.MAJ -> k.majuscule()
        Fn.ESPACE -> if (t.texte.isNotBlank()) k.ecrire(t.texte) else k.espace()
        Fn.ENTREE -> k.entree()
        Fn.EMOJI -> k.page(Page.EMOJI)
        Fn.LANGUE -> k.langueSuivante()
        Fn.SYMBOLES -> k.page(Page.SYMBOLES)
        Fn.SYMBOLES_2 -> k.page(Page.SYMBOLES_2)
        Fn.LETTRES -> k.page(Page.LETTRES)
        Fn.EFFACER -> k.effacer()
    }
}

/** Les accents d'une lettre (ou les langues de l'espace), au-dessus de la touche ; on touche celui qu'on veut. */
@Composable
private fun BoxScope.Variantes(t: Touche, choix: List<String>, bornes: Rect, e: Etat, k: Clavier) {
    val b = LocalBanco.current
    val d = LocalDensity.current
    val case = if (t.fn == Fn.LANGUE) 96.dp else 44.dp
    val largeur = with(d) { (case * choix.size + 8.dp).toPx() }
    // Un toucher hors des variantes les ferme.
    Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null) { e.variantes = null })
    val ecran = with(d) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val x = (bornes.center.x - largeur / 2).coerceIn(4f, (ecran - largeur - 4f).coerceAtLeast(4f))
    Box(Modifier.matchParentSize()) {
        Row(
            Modifier.offset { IntOffset(x.roundToInt(), (bornes.top - with(d) { 56.dp.toPx() }).roundToInt().coerceAtLeast(0)) }
                .shadow(10.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(b.sol).padding(4.dp),
        ) {
            choix.forEach { v ->
                Box(
                    Modifier.size(case, 44.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) {
                        k.vibrer()
                        e.variantes = null
                        if (t.fn == Fn.LANGUE) Langue.entries.firstOrNull { it.nom == v }?.let { k.changerLangue(it) } else k.ecrire(v)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(v, style = TextStyle(fontFamily = Polices.corps, fontSize = if (t.fn == Fn.LANGUE) 15.sp else 22.sp, color = b.encre))
                }
            }
        }
    }
}

/** Les emoji (maquette l1-emoji) : récents et catégories, huit par rangée. */
@Composable
private fun PanneauEmoji(e: Etat, k: Clavier) {
    val b = LocalBanco.current
    val recents = remember(e.page, e.categorie) { Emoji.recents(k) }
    val cat = if (e.categorie < 0 && recents.isEmpty()) 0 else e.categorie
    val liste = if (cat < 0) recents else Emoji.CATEGORIES[cat].liste
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Fixed(8), Modifier.weight(1f).fillMaxWidth()) {
            items(liste) { em ->
                Box(Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = em, role = Role.Button) { k.vibrer(); k.emoji(em) }, contentAlignment = Alignment.Center) {
                    BasicText(em, style = TextStyle(fontSize = 26.sp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(HAUTEUR_TOUCHE), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Onglet(Modifier.weight(1.4f), choisi = false, description = "Lettres", texte = "ABC") { k.page(Page.LETTRES) }
            Onglet(Modifier.weight(1f), choisi = cat < 0, description = "Récents", icone = Pictos.RECENTS) { e.categorie = -1 }
            Emoji.CATEGORIES.forEachIndexed { i, c ->
                Onglet(Modifier.weight(1f), choisi = cat == i, description = c.nom, icone = c.icone) { e.categorie = i }
            }
            Onglet(Modifier.weight(1.2f), choisi = false, description = "Effacer", icone = Pictos.EFFACER) { k.vibrer(); k.effacer() }
        }
    }
}

@Composable
private fun Onglet(modifier: Modifier, choisi: Boolean, description: String, icone: String? = null, texte: String? = null, onClick: () -> Unit) {
    val b = LocalBanco.current
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp)).then(if (choisi) Modifier.background(b.sol) else Modifier)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icone != null) IconeTrait(icone, 20.dp, if (choisi) b.lateriteTexte else b.encre)
        if (texte != null) BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = b.encre))
    }
}
