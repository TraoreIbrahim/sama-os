package africa.samaos.calculatrice

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.BarreAvis
import africa.samaos.banco.appli.Identite
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 1 € = 655,957 F CFA : le taux fixe de la zone franc. */
private val TAUX = BigDecimal("655.957")

/** Les couleurs des touches : la pierre de l'appli, l'orange de son icône pour les opérations. */
private class Teintes(val chiffre: Color, val fonction: Color, val science: Color, val operation: Color, val operationEncre: Color, val egal: Color, val egalEncre: Color)

private val CLAIR = Teintes(
    chiffre = Color(0xFFFBF9F6), fonction = Color(0xFFDDD8D0), science = Color(0xFFE3DFD8),
    operation = Color(0xFFF7DCC4), operationEncre = Color(0xFFBF5F1A), egal = Color(0xFFEE8434), egalEncre = Color.White,
)
private val NUIT = Teintes(
    chiffre = Color(0xFF2B2824), fonction = Color(0xFF403A34), science = Color(0xFF34302B),
    operation = Color(0x3DEE8434), operationEncre = Color(0xFFF6AE70), egal = Color(0xFFEE8434), egalEncre = Color.White,
)

private object Ic {
    const val HISTORIQUE = "M4 12a8 8 0 1 0 2.3-5.6 M4 4v4h4 M12 8v4.5l3 2"
    const val EFFACER = "M9 5h11v14H9l-6-7z M12 9l5 6 M17 9l-5 6"
    const val ECHANGE = "M7 7h11l-3-3 M17 17H6l3 3"
    const val FERMER = "M6 15l6-6l6 6"
}

/** La Calculatrice de Sama : les calculs du quotidien debout, la scientifique couchée, l'historique, le franc CFA. */
class Calculatrice : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Historique.charger(this)
        setContent {
            val sombre = isSystemInDarkTheme()
            val id = if (sombre) Identites.CalculatriceNuit else Identites.Calculatrice
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) { Ecran(if (sombre) NUIT else CLAIR) }
        }
    }
}

private fun Identite.fondCalcul() = if (sombre) Color(0xFF181614) else Color(0xFFEDE9E3)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Ecran(t: Teintes) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val vibreur = LocalHapticFeedback.current
    var expr by rememberSaveable { mutableStateOf("") }
    /** Après « = » : l'expression calculée (en petit) et son résultat (en grand). */
    var calcule by rememberSaveable { mutableStateOf<String?>(null) }
    var resultat by rememberSaveable { mutableStateOf<String?>(null) }
    var erreur by rememberSaveable { mutableStateOf<String?>(null) }
    var degres by rememberSaveable { mutableStateOf(true) }
    var second by rememberSaveable { mutableStateOf(false) }
    var memoire by rememberSaveable { mutableStateOf<String?>(null) }
    var conversion by rememberSaveable { mutableIntStateOf(0) }
    var histo by rememberSaveable { mutableStateOf(false) }

    val apercu = if (calcule == null) Calcul.evaluer(expr, degres) else null
    /** La valeur à l'écran : le résultat, sinon ce que vaut déjà ce qu'on tape. */
    val valeur: BigDecimal? = when {
        calcule != null -> resultat?.let { Calcul.evaluer(it, degres) as? Resultat.Nombre }?.v
        else -> (apercu as? Resultat.Nombre)?.v
    }

    fun taper(touche: String) {
        vibreur.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (calcule != null) {
            // Après « = », un chiffre recommence ; une opération continue avec le résultat.
            val continuer = touche in listOf("+", "−", "×", "÷", "^", "ʸ√", "logᵧ", "²", "³", "!", "%", "⁻¹")
            expr = if (continuer && resultat != null) resultat!! else ""
            calcule = null
            resultat = null
            erreur = null
        }
        expr = Calcul.ajouter(expr, touche)
    }
    fun egal() {
        vibreur.performHapticFeedback(HapticFeedbackType.Confirm)
        if (calcule != null || expr.isBlank()) return
        when (val r = Calcul.evaluer(expr, degres)) {
            is Resultat.Nombre -> {
                calcule = expr
                resultat = Calcul.brut(r.v)
                erreur = null
                if (Calcul.aUneOperation(expr)) Historique.ajouter(c, expr, resultat!!)
            }
            is Resultat.Impossible -> {
                calcule = expr
                resultat = null
                erreur = r.pourquoi
            }
            null -> {}
        }
    }
    fun toutEffacer() {
        vibreur.performHapticFeedback(HapticFeedbackType.LongPress)
        expr = ""
        calcule = null
        resultat = null
        erreur = null
    }
    fun effacer() {
        vibreur.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (calcule != null) toutEffacer() else expr = Calcul.effacer(expr)
    }
    fun signe() {
        vibreur.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        if (calcule != null) {
            expr = resultat ?: ""
            calcule = null
            resultat = null
            erreur = null
        }
        expr = Calcul.signe(expr)
    }
    fun memoriser(sens: Int) {
        vibreur.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        val v = valeur ?: return
        val m = memoire?.let { (Calcul.evaluer(it, degres) as? Resultat.Nombre)?.v } ?: BigDecimal.ZERO
        memoire = Calcul.brut(if (sens > 0) m.add(v) else m.subtract(v)).takeIf { it != "0" }
        Avis.dire("Mémoire : " + Calcul.format(if (sens > 0) m.add(v) else m.subtract(v)))
    }
    /** Une touche : ce qu'elle fait. */
    fun appuyer(k: String) {
        when (k) {
            "C" -> toutEffacer()
            "⌫" -> effacer()
            "=" -> egal()
            "±" -> signe()
            "( )" -> taper(Calcul.parenthese(if (calcule != null) "" else expr))
            "(" -> taper("(")
            ")" -> taper(")")
            "2nd" -> second = !second
            "Rad", "Deg" -> degres = !degres
            "mc" -> memoire = null
            "m+" -> memoriser(1)
            "m−" -> memoriser(-1)
            "mr" -> memoire?.let { taper(it) }
            "x²" -> taper("²")
            "x³" -> taper("³")
            "xʸ" -> taper("^")
            "eˣ" -> { taper("e"); taper("^") }
            "10ˣ" -> { taper("10"); taper("^") }
            "2ˣ" -> { taper("2"); taper("^") }
            "¹/x" -> taper("⁻¹")
            "²√x" -> taper("√(")
            "³√x" -> taper("∛(")
            "ʸ√x" -> taper("ʸ√")
            "ln" -> taper("ln(")
            "logᵧ" -> taper("logᵧ")
            "log₁₀" -> taper("log₁₀(")
            "log₂" -> taper("log₂(")
            "x!" -> taper("!")
            "sin", "cos", "tan", "sinh", "cosh", "tanh", "sin⁻¹", "cos⁻¹", "tan⁻¹", "sinh⁻¹", "cosh⁻¹", "tanh⁻¹" -> taper("$k(")
            "e", "π" -> taper(k)
            "EE" -> taper("E")
            "Rand" -> taper(Calcul.hasard())
            else -> taper(k)
        }
    }
    // L'opération qu'on vient de toucher reste allumée jusqu'au nombre suivant.
    val operationEnCours = if (calcule == null) listOf("+" to "+", "−" to "−", "×" to "×", "÷" to "÷").firstOrNull { (s, _) -> expr.endsWith(s) && expr.length > 1 }?.second else null
    val premiereTouche = if (expr.isEmpty() || calcule != null) "C" else "⌫"
    val converti: String? = valeur?.takeIf { conversion != 0 }?.let { v ->
        if (conversion == 1) "≈ " + Calcul.format(v.divide(TAUX, MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP)) + " €"
        else "≈ " + Calcul.format(v.multiply(TAUX).setScale(0, RoundingMode.HALF_UP)) + " F CFA"
    }
    BackHandler(enabled = histo) { histo = false }

    BoxWithConstraints(Modifier.fillMaxSize().background(a.fondCalcul())) {
        val paysage = maxWidth > maxHeight
        val hauteurEcran = maxHeight
        AnimatedContent(paysage, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "sens") { couche ->
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Haut(
                    paysage = couche, degres = degres, memoire = memoire != null, conversion = conversion, converti = if (couche) converti else null,
                    inverser = { conversion = if (conversion == 1) 2 else 1 },
                    historique = { histo = true },
                    convertir = { conversion = if (conversion == 0) 1 else 0 },
                )
                Affichage(
                    expr = expr, calcule = calcule, resultat = resultat, erreur = erreur, apercu = apercu, valeur = valeur, conversion = conversion,
                    paysage = couche, modifier = if (couche) Modifier.height(hauteurEcran * 0.3f) else Modifier.weight(1f),
                    inverser = { conversion = if (conversion == 1) 2 else 1 },
                    effacer = { effacer() },
                )
                if (couche) {
                    // Couchée : la scientifique à gauche, la calculatrice de tous les jours à droite.
                    Row(Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val science = if (second) SCIENCE_2ND else SCIENCE
                        Grille(science.map { r -> r.map { k -> if (k == "Rad" && !degres) "Deg" else k } }, Modifier.weight(6f).fillMaxHeight(), 6.dp, remplir = true) { k ->
                            Touche(
                                k, Genre.SCIENCE, t,
                                allume = (k == "2nd" && second) || ((k == "Rad" || k == "Deg") && !degres) || (k == "mr" && memoire != null),
                            ) { appuyer(k) }
                        }
                        Grille(standard(premiereTouche), Modifier.weight(4f).fillMaxHeight(), 6.dp, remplir = true) { k ->
                            Touche(k, genreDe(k), t, allume = k == operationEnCours, longClic = if (k == "⌫") ({ toutEffacer() }) else null) { appuyer(k) }
                        }
                    }
                } else {
                    Grille(standard(premiereTouche), Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), 12.dp) { k ->
                        Touche(k, genreDe(k), t, allume = k == operationEnCours, longClic = if (k == "⌫") ({ toutEffacer() }) else null) { appuyer(k) }
                    }
                }
            }
        }
        // L'historique descend du haut, par-dessus.
        AnimatedVisibility(
            histo,
            enter = slideInVertically(spring(dampingRatio = 0.88f, stiffness = 380f)) { -it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(240, easing = FastOutLinearInEasing)) { -it } + fadeOut(tween(200)),
        ) {
            PanneauHistorique(
                fermer = { histo = false },
                reprendre = { r ->
                    histo = false
                    if (calcule != null || expr.isEmpty()) {
                        expr = r
                        calcule = null
                        resultat = null
                        erreur = null
                    } else {
                        expr = Calcul.ajouter(expr, r)
                    }
                },
            )
        }
        BarreAvis(Modifier.align(Alignment.BottomCenter), bas = 24.dp)
    }
}

private enum class Genre { CHIFFRE, FONCTION, OPERATION, EGAL, SCIENCE }

private fun genreDe(k: String) = when (k) {
    "=" -> Genre.EGAL
    "+", "−", "×", "÷" -> Genre.OPERATION
    "C", "⌫", "±", "%", "( )" -> Genre.FONCTION
    else -> Genre.CHIFFRE
}

private fun standard(premiere: String) = listOf(
    listOf(premiere, "±", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("( )", "0", ",", "="),
)

private val SCIENCE = listOf(
    listOf("(", ")", "mc", "m+", "m−", "mr"),
    listOf("2nd", "x²", "x³", "xʸ", "eˣ", "10ˣ"),
    listOf("¹/x", "²√x", "³√x", "ʸ√x", "ln", "log₁₀"),
    listOf("x!", "sin", "cos", "tan", "e", "EE"),
    listOf("Rad", "sinh", "cosh", "tanh", "π", "Rand"),
)

/** Avec « 2nd » : les fonctions réciproques. */
private val SCIENCE_2ND = listOf(
    listOf("(", ")", "mc", "m+", "m−", "mr"),
    listOf("2nd", "x²", "x³", "xʸ", "eˣ", "2ˣ"),
    listOf("¹/x", "²√x", "³√x", "ʸ√x", "logᵧ", "log₂"),
    listOf("x!", "sin⁻¹", "cos⁻¹", "tan⁻¹", "e", "EE"),
    listOf("Rad", "sinh⁻¹", "cosh⁻¹", "tanh⁻¹", "π", "Rand"),
)

/** Ce qu'un lecteur d'écran dit d'une touche. */
private fun nomDe(k: String) = when (k) {
    "+" -> "plus"; "−" -> "moins"; "×" -> "fois"; "÷" -> "divisé par"; "=" -> "égal"
    "C" -> "tout effacer"; "⌫" -> "effacer"; "±" -> "changer le signe"; "%" -> "pour cent"; "," -> "virgule"; "( )" -> "parenthèse"
    "x²" -> "au carré"; "x³" -> "au cube"; "xʸ" -> "puissance"; "eˣ" -> "e puissance"; "10ˣ" -> "10 puissance"; "2ˣ" -> "2 puissance"
    "¹/x" -> "inverse"; "²√x" -> "racine carrée"; "³√x" -> "racine cubique"; "ʸ√x" -> "racine y-ième"
    "ln" -> "logarithme népérien"; "log₁₀" -> "logarithme décimal"; "log₂" -> "logarithme en base 2"; "logᵧ" -> "logarithme en base y"
    "x!" -> "factorielle"; "EE" -> "fois 10 puissance"; "Rand" -> "nombre au hasard"; "π" -> "pi"
    "Rad" -> "passer en radians"; "Deg" -> "passer en degrés"; "2nd" -> "fonctions secondes"
    "mc" -> "effacer la mémoire"; "m+" -> "ajouter à la mémoire"; "m−" -> "retirer de la mémoire"; "mr" -> "rappeler la mémoire"
    else -> k
}

/** Une grille de touches : chaque rangée prend la largeur. */
@Composable
private fun Grille(rangees: List<List<String>>, modifier: Modifier, ecart: Dp, remplir: Boolean = false, touche: @Composable (String) -> Unit) {
    BoxWithConstraints(modifier) {
        val colonnes = rangees.first().size
        val large = (maxWidth - ecart * (colonnes - 1)) / colonnes
        // Debout, la hauteur suit la largeur ; couchée, les rangées se partagent la hauteur.
        val haut = if (remplir) (maxHeight - ecart * (rangees.size - 1)) / rangees.size else large * 0.84f
        Column(verticalArrangement = Arrangement.spacedBy(ecart)) {
            rangees.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(ecart)) {
                    r.forEach { k -> Box(Modifier.width(large).height(haut)) { touche(k) } }
                }
            }
        }
    }
}

/** Une touche : un galet qui s'enfonce et s'éclaire sous le doigt. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Touche(k: String, genre: Genre, t: Teintes, allume: Boolean = false, longClic: (() -> Unit)? = null, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val source = remember { MutableInteractionSource() }
    val presse by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (presse) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "appui")
    val fondBase = when (genre) {
        Genre.CHIFFRE -> t.chiffre
        Genre.FONCTION -> t.fonction
        Genre.SCIENCE -> if (allume) a.encre else t.science
        Genre.OPERATION -> if (allume) t.egal else t.operation
        Genre.EGAL -> t.egal
    }
    val fond by animateColorAsState(if (presse) androidx.compose.ui.graphics.lerp(fondBase, a.encre, 0.12f) else fondBase, tween(if (presse) 40 else 220), label = "fond")
    val encre = when (genre) {
        Genre.OPERATION -> if (allume) t.egalEncre else t.operationEncre
        Genre.EGAL -> t.egalEncre
        Genre.SCIENCE -> if (allume) a.fondCalcul() else a.encre
        else -> a.encre
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(percent = 34)).background(fond)
            .combinedClickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = nomDe(k), onLongClick = longClic, onClick = onClick)
            .semantics { contentDescription = nomDe(k) },
        contentAlignment = Alignment.Center,
    ) {
        val petite = genre == Genre.SCIENCE
        val base = with(LocalDensity.current) { (minOf(maxHeight, maxWidth) * if (petite) 0.36f else 0.42f).toSp() }
        if (k == "⌫") {
            IconeTrait(Ic.EFFACER, maxHeight * 0.36f, encre, epaisseur = 1.8f)
        } else {
            BasicText(
                k, maxLines = 1,
                style = TextStyle(
                    fontFamily = Polices.corps,
                    fontWeight = if (genre == Genre.OPERATION || genre == Genre.EGAL) FontWeight.Medium else if (petite) FontWeight.Medium else FontWeight.Normal,
                    fontSize = if (k.length > 3 && petite) base * 0.82f else base, color = encre, textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

/** En haut : l'historique, ce qui est réglé (radians, mémoire), la conversion F CFA ⇄ €. */
@Composable
private fun Haut(paysage: Boolean, degres: Boolean, memoire: Boolean, conversion: Int, converti: String?, inverser: () -> Unit, historique: () -> Unit, convertir: () -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().height(if (paysage) 44.dp else 56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(a.champ).clickable(onClickLabel = "Historique des calculs", role = Role.Button, onClick = historique).semantics { contentDescription = "Historique" },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(Ic.HISTORIQUE, 21.dp, a.encre, epaisseur = 1.9f) }
        Row(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnimatedVisibility(!degres, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut() + scaleOut(targetScale = 0.7f)) { Marque("Rad") }
            AnimatedVisibility(memoire, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut() + scaleOut(targetScale = 0.7f)) { Marque("M") }
        }
        // Couchée, la conversion se lit ici, sur une ligne (l'écran est trop bas pour l'encadré).
        AnimatedContent(converti, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "converti") { cv ->
            if (cv != null) BasicText(
                cv, maxLines = 1,
                modifier = Modifier.padding(end = 10.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Changer le sens de la conversion", role = Role.Button, onClick = inverser).padding(horizontal = 8.dp, vertical = 6.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color(0xFFBF5F1A)),
            )
        }
        val actif = conversion != 0
        val fond by animateColorAsState(if (actif) Color(0xFFEE8434) else a.champ, label = "conv")
        Row(
            Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(fond).clickable(onClickLabel = if (actif) "Masquer la conversion" else "Convertir F CFA et euros", role = Role.Button, onClick = convertir)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("F CFA", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (actif) Color.White else a.encre))
            IconeTrait(Ic.ECHANGE, 15.dp, if (actif) Color.White else a.encre2, Modifier.padding(horizontal = 6.dp), epaisseur = 2f)
            BasicText("€", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (actif) Color.White else a.encre))
        }
    }
}

@Composable
private fun Marque(texte: String) {
    val a = LocalIdentite.current
    BasicText(texte, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(a.champ).padding(horizontal = 8.dp, vertical = 3.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = a.encre2))
}

/**
 * L'écran : en tapant, l'expression en grand et ce qu'elle vaut déjà, en gris ; après « = », l'expression monte
 * en petit et le résultat prend sa place. Glisser de côté efface le dernier chiffre ; un appui long copie le résultat.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Affichage(
    expr: String,
    calcule: String?,
    resultat: String?,
    erreur: String?,
    apercu: Resultat?,
    valeur: BigDecimal?,
    conversion: Int,
    paysage: Boolean,
    modifier: Modifier,
    inverser: () -> Unit,
    effacer: () -> Unit,
) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val seuil = with(LocalDensity.current) { 44.dp.toPx() }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .pointerInput(Unit) {
                var cumul = 0f
                var fait = false
                detectHorizontalDragGestures(onDragStart = { cumul = 0f; fait = false }) { _, d ->
                    cumul += d
                    if (!fait && kotlin.math.abs(cumul) > seuil) {
                        fait = true
                        effacer()
                    }
                }
            }
            .combinedClickable(interactionSource = null, indication = null, onClick = {}, onLongClickLabel = "Copier le résultat", onLongClick = {
                valeur?.let { v ->
                    c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Résultat", Calcul.brut(v).replace('−', '-')))
                    Avis.dire("Résultat copié : " + Calcul.format(v))
                }
            }),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedContent(
            calcule != null,
            transitionSpec = {
                if (targetState) (slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { it / 2 } + fadeIn(tween(200))) togetherWith (slideOutVertically(tween(220)) { -it / 3 } + fadeOut(tween(160)))
                else fadeIn(tween(160)) togetherWith fadeOut(tween(120))
            },
            contentAlignment = Alignment.BottomEnd,
            label = "egal",
        ) { fini ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                if (fini) {
                    BasicText(Calcul.joli(calcule.orEmpty()) + " =", maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = if (paysage) 18.sp else 22.sp, color = a.encre2, textAlign = TextAlign.End))
                    if (erreur != null) {
                        BasicText(erreur, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = if (paysage) 26.sp else 32.sp, lineHeight = 40.sp, color = Color(0xFFC4573E), textAlign = TextAlign.End))
                    } else {
                        Ajuste(valeur?.let { Calcul.format(it) } ?: resultat.orEmpty(), if (paysage) 46.sp else 88.sp, if (paysage) 26.sp else 34.sp, Polices.fine, a.encre)
                    }
                } else {
                    Ajuste(if (expr.isEmpty()) "0" else Calcul.joli(expr), if (paysage) 44.sp else 80.sp, if (paysage) 24.sp else 30.sp, Polices.fine, a.encre)
                    // Ce que vaut déjà le calcul, en gris : on le voit avant d'appuyer sur « = ».
                    val gris = when {
                        apercu is Resultat.Nombre && Calcul.aUneOperation(expr) -> "= " + Calcul.format(apercu.v)
                        apercu is Resultat.Impossible -> apercu.pourquoi
                        else -> ""
                    }
                    AnimatedContent(gris, transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(80)) }, contentAlignment = Alignment.CenterEnd, label = "apercu") { g ->
                        BasicText(
                            g, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = Polices.corps, fontSize = if (paysage) 17.sp else 26.sp, lineHeight = if (paysage) 21.sp else 32.sp, color = if (apercu is Resultat.Impossible) Color(0xFFC4573E) else a.encre2, textAlign = TextAlign.End),
                        )
                    }
                }
            }
        }
        // F CFA ⇄ € : la valeur à l'écran, convertie au taux fixe ; un toucher change le sens.
        AnimatedVisibility(!paysage && conversion != 0 && valeur != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            val v = valeur ?: BigDecimal.ZERO
            val versEuro = conversion == 1
            val converti = if (versEuro) v.divide(TAUX, MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP) else v.multiply(TAUX).setScale(0, RoundingMode.HALF_UP)
            Row(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x1FEE8434)).clickable(onClickLabel = "Changer le sens de la conversion", role = Role.Button, onClick = inverser)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(
                        (if (versEuro) "F CFA → " else "€ → ") + "≈ " + Calcul.format(converti) + if (versEuro) " €" else " F CFA",
                        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = if (paysage) 17.sp else 20.sp, color = Color(0xFFBF5F1A)),
                    )
                    BasicText("1 € = 655,957 F CFA, taux fixe", style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, color = a.encre2))
                }
                IconeTrait(Ic.ECHANGE, 18.dp, Color(0xFFBF5F1A), Modifier.padding(start = 10.dp), epaisseur = 2f)
            }
        }
        Spacer(Modifier.height(if (paysage) 6.dp else 16.dp))
    }
}

/** Un texte sur une ligne qui rapetisse pour tenir dans la largeur (jusqu'à [min], puis passe à la ligne). */
@Composable
private fun Ajuste(texte: String, max: TextUnit, min: TextUnit, police: androidx.compose.ui.text.font.FontFamily, couleur: Color) {
    val mesure = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val largeur = constraints.maxWidth.toFloat()
        val style = TextStyle(fontFamily = police, fontWeight = FontWeight(250), fontSize = max, lineHeight = max * 1.12f, letterSpacing = (-0.02).em, color = couleur, textAlign = TextAlign.End)
        val w = remember(texte, max) { mesure.measure(texte, style, maxLines = 1, softWrap = false).size.width.toFloat() }
        val taille = if (w <= largeur) max.value else (max.value * largeur / w * 0.98f).coerceAtLeast(min.value)
        val anime by animateFloatAsState(taille, spring(stiffness = 600f), label = "taille")
        BasicText(texte, maxLines = 3, style = style.copy(fontSize = anime.sp, lineHeight = (anime * 1.12f).sp))
    }
}

/** L'historique : les calculs du jour, d'hier, d'avant ; un toucher reprend le résultat. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanneauHistorique(fermer: () -> Unit, reprendre: (String) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var vider by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(a.fondCalcul()).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(a.champ).clickable(onClickLabel = "Fermer l'historique", role = Role.Button, onClick = fermer).semantics { contentDescription = "Fermer" },
                contentAlignment = Alignment.Center,
            ) { IconeTrait(Ic.FERMER, 20.dp, a.encre, epaisseur = 2f) }
            Spacer(Modifier.weight(1f))
            if (Historique.entrees.isNotEmpty()) BasicText(
                if (vider) "Effacer tout ?" else "Effacer",
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) {
                    if (vider) {
                        Historique.vider(c)
                        vider = false
                    } else vider = true
                }.padding(horizontal = 12.dp, vertical = 8.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (vider) Color(0xFFC4573E) else Color(0xFFBF5F1A)),
            )
        }
        LaunchedEffect(vider) {
            if (vider) {
                kotlinx.coroutines.delay(3000)
                vider = false
            }
        }
        BasicText("Historique", modifier = Modifier.padding(start = 22.dp, bottom = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.02).em, color = a.encre))
        if (Historique.entrees.isEmpty()) {
            BasicText("Les calculs faits avec « = » s'écrivent ici, et restent sur le téléphone.", modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            Historique.entrees.groupBy { jour(it.quand) }.forEach { (j, l) ->
                item(key = "j$j") { BasicText(j, modifier = Modifier.animateItem().padding(start = 26.dp, top = 16.dp, bottom = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre)) }
                items(l, key = { "${it.quand}${it.expression}" }) { e ->
                    Column(
                        Modifier.animateItem().padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.surface)
                            .combinedClickable(onClickLabel = "Reprendre ce résultat", role = Role.Button, onLongClickLabel = "Copier", onLongClick = {
                                c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Résultat", e.resultat.replace('−', '-')))
                                Avis.dire("Résultat copié")
                            }) { reprendre(e.resultat) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        BasicText(Calcul.joli(e.expression), maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2, textAlign = TextAlign.End))
                        val v = (Calcul.evaluer(e.resultat, true) as? Resultat.Nombre)?.v
                        BasicText("= " + (v?.let { Calcul.format(it) } ?: e.resultat), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = a.encre))
                    }
                }
            }
            item { Spacer(Modifier.heightIn(min = 24.dp)) }
        }
    }
}

private fun jour(t: Long): String {
    val z = ZoneId.systemDefault()
    val j = Instant.ofEpochMilli(t).atZone(z).toLocalDate()
    val ecart = ChronoUnit.DAYS.between(j, LocalDate.now(z))
    return when {
        ecart <= 0L -> "Aujourd'hui"
        ecart == 1L -> "Hier"
        else -> j.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
    }
}
