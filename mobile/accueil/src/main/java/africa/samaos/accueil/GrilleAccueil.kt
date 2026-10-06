package africa.samaos.accueil

import africa.samaos.banco.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

private val HAUTEUR_CASE = 84.dp
private val ECART_RANGEES = 18.dp
private val ICONE = 60.dp

/** Ce que fait la grille de l'Accueil quand on la touche. */
class GestesGrille(
    val lancer: (Appli) -> Unit,
    val ouvrirDossier: (ElemDossier) -> Unit,
    val menu: (Appli, Rect) -> Unit,
    val fermerMenu: () -> Unit,
    val deplacer: (Int, Int) -> Unit,
    val regrouper: (Int, Int) -> Unit,
)

/**
 * Les applis et dossiers de l'Accueil, quatre par rangée, posés au-dessus de la Natte.
 * On touche pour ouvrir ; on tient pour le menu ; on tient puis on glisse pour ranger :
 * lâchée sur une autre appli, une appli fait un dossier avec elle.
 */
@Composable
fun GrilleAccueil(elements: List<Element>, applis: Map<String, Appli>, gestes: GestesGrille, modifier: Modifier = Modifier) {
    val b = LocalBanco.current
    val densite = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val largeurCase = with(densite) { (maxWidth / 4).toPx() }
        val pasRangee = with(densite) { (HAUTEUR_CASE + ECART_RANGEES).toPx() }
        val icone = with(densite) { ICONE.toPx() }
        fun place(i: Int) = Offset((i % 4) * largeurCase, (i / 4) * pasRangee)
        val rangees = ((elements.size + 3) / 4).coerceAtLeast(1)

        val elementsActuels by rememberUpdatedState(elements)
        val gestesActuels by rememberUpdatedState(gestes)
        val portee = rememberCoroutineScope()
        var coordonnees by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var tenu by remember { mutableStateOf<String?>(null) }
        var position by remember { mutableStateOf(Offset.Zero) }
        var glisse by remember { mutableStateOf(false) }
        // Où l'élément tenu irait si on le lâchait : une place, ou une autre appli (pour un dossier).
        var vers by remember { mutableStateOf<Int?>(null) }
        var sur by remember { mutableStateOf<Int?>(null) }

        // Pendant qu'on range, les autres s'écartent pour montrer la place qui l'attend.
        val apercu = remember(elements, tenu, vers, sur) {
            val depuis = elements.indexOfFirst { it.id == tenu }
            val v = vers
            if (depuis < 0 || v == null || sur != null) elements
            else elements.toMutableList().apply { add(v.coerceIn(0, size - 1), removeAt(depuis)) }
        }

        fun bornesIcone(i: Int): Rect {
            val origine = coordonnees?.positionInRoot() ?: Offset.Zero
            val p = place(i)
            return Rect(Offset(origine.x + p.x + (largeurCase - icone) / 2, origine.y + p.y), Size(icone, icone))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(HAUTEUR_CASE * rangees + ECART_RANGEES * (rangees - 1))
                .onGloballyPositioned { coordonnees = it },
        ) {
            elements.forEach { e ->
                key(e.id) {
                    val i = apercu.indexOfFirst { it.id == e.id }
                    val cible = place(i)
                    val glissement = remember { Animatable(cible, Offset.VectorConverter) }
                    LaunchedEffect(cible) { if (tenu != e.id) glissement.animateTo(cible) }
                    val estTenu = tenu == e.id && glisse
                    val appli = (e as? ElemAppli)?.let { applis[it.cle] }
                    Box(
                        Modifier
                            .offset {
                                val p = if (estTenu) position else glissement.value
                                IntOffset(p.x.roundToInt(), p.y.roundToInt())
                            }
                            .zIndex(if (tenu == e.id) 1f else 0f)
                            .size(with(densite) { largeurCase.toDp() }, HAUTEUR_CASE)
                            .graphicsLayer {
                                val echelle = if (estTenu) 1.1f else 1f
                                scaleX = echelle
                                scaleY = echelle
                            }
                            .semantics {
                                role = Role.Button
                                contentDescription = when (e) {
                                    is ElemAppli -> appli?.nom.orEmpty()
                                    is ElemDossier -> "Dossier ${e.nom}"
                                }
                                onClick(label = "Ouvrir") {
                                    when (e) {
                                        is ElemAppli -> appli?.let(gestesActuels.lancer)
                                        is ElemDossier -> gestesActuels.ouvrirDossier(e)
                                    }
                                    true
                                }
                                if (appli != null) {
                                    onLongClick(label = "Menu de l'appli") {
                                        gestesActuels.menu(appli, bornesIcone(elementsActuels.indexOfFirst { it.id == e.id }))
                                        true
                                    }
                                }
                            }
                            .pointerInput(e.id) {
                                // Un appui long ne lance rien : il ouvre le menu ou commence le rangement.
                                detectTapGestures(onLongPress = { }) {
                                    when (e) {
                                        is ElemAppli -> appli?.let(gestesActuels.lancer)
                                        is ElemDossier -> gestesActuels.ouvrirDossier(e)
                                    }
                                }
                            }
                            .pointerInput(e.id) {
                                var parcours = Offset.Zero
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val depuis = elementsActuels.indexOfFirst { it.id == e.id }
                                        position = place(depuis)
                                        parcours = Offset.Zero
                                        glisse = false
                                        vers = null
                                        sur = null
                                        tenu = e.id
                                        if (appli != null) gestesActuels.menu(appli, bornesIcone(depuis))
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        position += delta
                                        parcours += delta
                                        if (!glisse && hypot(parcours.x, parcours.y) > 12.dp.toPx()) {
                                            glisse = true
                                            gestesActuels.fermerMenu()
                                        }
                                        if (glisse) {
                                            val liste = elementsActuels
                                            val depuis = liste.indexOfFirst { it.id == e.id }
                                            val centre = position + Offset(largeurCase / 2, icone / 2)
                                            val colonne = (centre.x / largeurCase).toInt().coerceIn(0, 3)
                                            val rangee = (centre.y / pasRangee).toInt().coerceAtLeast(0)
                                            val case = rangee * 4 + colonne
                                            val centreCase = place(case) + Offset(largeurCase / 2, icone / 2)
                                            val proche = hypot(centre.x - centreCase.x, centre.y - centreCase.y) < icone * 0.45f
                                            if (centre.y < -pasRangee / 2) {
                                                vers = null
                                                sur = null
                                            } else if (e is ElemAppli && proche && case in liste.indices && case != depuis) {
                                                sur = case
                                                vers = null
                                            } else {
                                                sur = null
                                                vers = case.coerceIn(0, liste.size - 1)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val liste = elementsActuels
                                        val depuis = liste.indexOfFirst { it.id == e.id }
                                        val s = sur
                                        val v = vers
                                        portee.launch {
                                            if (glisse) {
                                                when {
                                                    s != null -> gestesActuels.regrouper(depuis, s)
                                                    v != null -> {
                                                        glissement.snapTo(position)
                                                        gestesActuels.deplacer(depuis, v)
                                                    }
                                                    else -> glissement.snapTo(position)
                                                }
                                            } else if (e is ElemDossier) {
                                                gestesActuels.ouvrirDossier(e)
                                            }
                                            tenu = null
                                            glisse = false
                                            vers = null
                                            sur = null
                                        }
                                    },
                                    onDragCancel = {
                                        tenu = null
                                        glisse = false
                                        vers = null
                                        sur = null
                                    },
                                )
                            },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val cibleDossier = sur != null && apercu.getOrNull(sur!!)?.id == e.id && tenu != e.id
                        when (e) {
                            is ElemAppli -> if (appli != null) VueIcone(appli.nom, cibleDossier) {
                                Image(appli.icone, contentDescription = null, modifier = Modifier.size(ICONE))
                            }
                            is ElemDossier -> VueIcone(e.nom, cibleDossier) { IconeDossier(e, applis) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VueIcone(nom: String, cible: Boolean, image: @Composable () -> Unit) {
    val b = LocalBanco.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(ICONE + 8.dp)
                .then(if (cible) Modifier.border(2.dp, b.laterite, RoundedCornerShape(22.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) { image() }
        BasicText(
            text = nom,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-4).dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = b.encre, textAlign = TextAlign.Center),
        )
    }
}

/** Un dossier : un galet où l'on voit ses quatre premières applis. */
@Composable
fun IconeDossier(d: ElemDossier, applis: Map<String, Appli>) {
    val b = LocalBanco.current
    Box(
        Modifier
            .size(ICONE)
            .clip(RoundedCornerShape(18.dp))
            .background(b.voile),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            d.applis.mapNotNull { applis[it] }.take(4).chunked(2).forEach { rangee ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rangee.forEach { a -> Image(a.icone, contentDescription = null, modifier = Modifier.size(22.dp)) }
                    if (rangee.size == 1) Spacer(Modifier.size(22.dp))
                }
            }
        }
    }
}

/**
 * Un dossier ouvert (maquette l1-dossier) : son nom qu'on change d'une touche, ses applis,
 * et « Touchez en dehors pour fermer ». On tient une appli pour son menu (« Sortir du dossier »).
 */
@Composable
fun FenetreDossier(
    d: ElemDossier,
    applis: Map<String, Appli>,
    renommer: (String) -> Unit,
    lancer: (Appli) -> Unit,
    menu: (Appli, Rect) -> Unit,
    fermer: () -> Unit,
) {
    val b = LocalBanco.current
    val focus = LocalFocusManager.current
    BackHandler(onBack = fermer)
    var nom by remember(d.ident) { mutableStateOf(d.nom) }
    var ecrit by remember { mutableStateOf(false) }
    fun garder() {
        if (nom.trim() != d.nom) renommer(nom.trim())
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(b.voile)
            .pointerInput(Unit) {
                detectTapGestures {
                    garder()
                    fermer()
                }
            },
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(start = 20.dp, end = 20.dp, top = 230.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(b.sol)
                .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = nom,
                    onValueChange = { nom = it.take(30) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = Polices.monument, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 34.sp,
                        letterSpacing = (-0.01).em, color = b.encre,
                    ),
                    cursorBrush = SolidColor(b.laterite),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        garder()
                        focus.clearFocus()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            if (ecrit && !it.isFocused) garder()
                            ecrit = it.isFocused
                        }
                        .semantics { contentDescription = "Nom du dossier" },
                )
                IconeTrait(Icones.CRAYON, 20.dp, b.encre2)
            }
            d.applis.mapNotNull { applis[it] }.chunked(4).forEach { rangee ->
                Row(Modifier.fillMaxWidth()) {
                    rangee.forEach { a ->
                        IconeAppli(a, { lancer(a) }, Modifier.weight(1f), menu = { r -> menu(a, r) })
                    }
                    repeat(4 - rangee.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        BasicText(
            "Touchez en dehors pour fermer",
            modifier = Modifier.padding(top = 18.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = b.encre2),
        )
        }
    }
}
