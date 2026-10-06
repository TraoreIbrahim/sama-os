package africa.samaos.accueil

import africa.samaos.banco.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * La Cour : toutes les applis de l'Espace, sur le sol qui monte du bas de l'écran en suivant le doigt.
 * Le ciel est celui de l'Accueil, dessiné en dessous. Le doigt qui redescend la referme,
 * dès que la liste est revenue en haut.
 */
@Composable
fun Cour(applis: List<Appli>, espace: Espace, panneau: Panneau, lancer: (Appli) -> Unit, menu: (Appli, Rect) -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    var recherche by remember { mutableStateOf("") }
    // Les résultats arrivent un court instant après la frappe, cherchés hors du fil de l'écran.
    var resultats by remember { mutableStateOf<Resultats?>(null) }
    LaunchedEffect(recherche, applis) {
        if (recherche.isBlank()) {
            resultats = null
            return@LaunchedEffect
        }
        delay(120)
        resultats = withContext(Dispatchers.IO) { Chercheur.chercher(contexte, recherche, applis) }
    }
    val listeResultats = rememberLazyListState()

    // Le clavier part dès que la Cour commence à redescendre.
    val clavier = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val descend = !panneau.ouvert
    LaunchedEffect(descend) {
        if (descend) {
            focus.clearFocus()
            clavier?.hide()
        }
    }
    // Refermée, la Cour oublie la recherche et revient en haut de la liste.
    val grille = rememberLazyGridState()
    LaunchedEffect(panneau.visible) {
        if (!panneau.visible) {
            recherche = ""
            grille.scrollToItem(0)
            listeResultats.scrollToItem(0)
        }
    }

    Box(Modifier.fillMaxSize().then(panneau.gestes())) {
        BasicText(
            text = "La Cour",
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, top = 52.dp)
                .graphicsLayer { alpha = ((panneau.progres - 0.45f) / 0.55f).coerceIn(0f, 1f) },
            style = TextStyle(
                fontFamily = Polices.monument,
                fontWeight = FontWeight(600),
                fontSize = 44.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.02).em,
                color = b.encre,
            ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (1f - panneau.progres) * panneau.course }
                .statusBarsPadding()
                .padding(top = 104.dp),
        ) {
            Crete()
            Column(
                Modifier
                    .fillMaxSize()
                    .background(b.sol)
                    .padding(top = 16.dp),
            ) {
                ChampRecherche(
                    valeur = recherche,
                    changer = { recherche = it },
                    valider = {
                        // Entrée : la première appli trouvée, sinon le web.
                        val r = resultats
                        val premiere = r?.applis?.firstOrNull()
                        when {
                            premiere != null -> lancer(premiere)
                            recherche.isNotBlank() -> Ouvrir.web(contexte, recherche.trim())
                        }
                    },
                )
                val basDePage = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val r = resultats
                if (recherche.isNotBlank()) {
                    if (r != null) ResultatsRecherche(r, listeResultats, basDePage, lancer, menu)
                } else {
                BasicText(
                    text = "Toutes les applications de ${espace.nom}",
                    modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 10.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = grille,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = basDePage + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(applis, key = { it.paquet + "/" + it.activite }) { a ->
                        IconeAppli(a, { lancer(a) }, menu = { r -> menu(a, r) })
                    }
                }
                }
            }
        }
    }
}

/** Le galet de recherche de la Cour : cerclé de latérite quand on y écrit, avec de quoi l'effacer. */
@Composable
private fun ChampRecherche(valeur: String, changer: (String) -> Unit, valider: () -> Unit) {
    val b = LocalBanco.current
    val style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre)
    var actif by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(56.dp)
            .background(b.sol2, RoundedCornerShape(50))
            .then(if (actif) Modifier.border(2.dp, b.laterite, RoundedCornerShape(50)) else Modifier)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val s = size.width / 24f
            drawCircle(b.encre2, 6.5f * s, Offset(10.5f * s, 10.5f * s), style = Stroke(1.75f * s))
            drawLine(b.encre2, Offset(15.2f * s, 15.2f * s), Offset(20f * s, 20f * s), 1.75f * s, cap = StrokeCap.Round)
        }
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = valeur,
            onValueChange = changer,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(b.laterite),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { valider() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { actif = it.isFocused }
                .semantics { contentDescription = "Rechercher dans la Cour" },
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (valeur.isEmpty()) BasicText("Applis, contacts, fichiers, réglages", style = style.copy(color = b.encre2))
                    champ()
                }
            },
        )
        if (valeur.isNotEmpty()) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(b.sol)
                    .clickable(onClickLabel = "Effacer", role = Role.Button) { changer("") }
                    .semantics { contentDescription = "Effacer" },
                contentAlignment = Alignment.Center,
            ) {
                IconeTrait(Icones.FERMER, 20.dp, b.encre)
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}
