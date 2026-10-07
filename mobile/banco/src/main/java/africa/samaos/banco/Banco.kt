package africa.samaos.banco

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Jetons de couleur de Banco, le système de design des surfaces système de Sama. */
@Immutable
data class Banco(
    val ciel: Color,
    val astre: Color,
    val colline1: Color,
    val colline2: Color,
    val colline3: Color,
    val colline4: Color,
    val sol: Color,
    val sol2: Color,
    val voile: Color,
    val encre: Color,
    val encre2: Color,
    val laterite: Color,
    val surLaterite: Color,
    val lateriteTexte: Color,
    /** Les actions qu'on ne peut pas défaire : supprimer, effacer. */
    val danger: Color,
    val ombre: Color,
    /** Vrai pour la Nuit : les couleurs d'Espace prennent alors leur teinte claire. */
    val sombre: Boolean,
    /** Le fond d'un interrupteur éteint, et son bouton. */
    val interFond: Color = Color(0xFF8F8476),
    val interBouton: Color = Color(0xFFFFFFFF),
    /** Les filets qui séparent. */
    val trait: Color = Color(0x1A1F1C18),
    /** Ce qui va bien (sécurité, batterie en bonne santé), et l'or des alertes douces. */
    val foret: Color = Color(0xFF2F6B57),
    val or: Color = Color(0xFFE2A62B),
)

val Aube = Banco(
    ciel = Color(0xFFF3ECE2),
    astre = Color(0xFFEDC6A2),
    colline1 = Color(0xFFE7D5C1),
    colline2 = Color(0xFFDDC2A6),
    colline3 = Color(0xFFD0AC8B),
    colline4 = Color(0xFFC39876),
    sol = Color(0xFFFBF8F3),
    sol2 = Color(0xFFF0E7DB),
    voile = Color(0xD9F3ECE2),
    encre = Color(0xFF1F1C18),
    encre2 = Color(0xFF665E54),
    laterite = Color(0xFFB5532F),
    surLaterite = Color(0xFFFFFFFF),
    lateriteTexte = Color(0xFF93401F),
    danger = Color(0xFFA3322A),
    ombre = Color(0x29462D14),
    sombre = false,
)

val Nuit = Banco(
    ciel = Color(0xFF151A2B),
    astre = Color(0xFFE6DAC2),
    colline1 = Color(0xFF1B2135),
    colline2 = Color(0xFF20273F),
    colline3 = Color(0xFF252E4A),
    colline4 = Color(0xFF2B3555),
    sol = Color(0xFF1E2233),
    sol2 = Color(0xFF2A2F44),
    voile = Color(0xD9151A2B),
    encre = Color(0xFFF1EBE1),
    encre2 = Color(0xFFADA698),
    laterite = Color(0xFFE07A52),
    surLaterite = Color(0xFF1A1E2E),
    lateriteTexte = Color(0xFFEE9A78),
    danger = Color(0xFFF08A80),
    ombre = Color(0x73000000),
    sombre = true,
    interFond = Color(0xFF6B7190),
    interBouton = Color(0xFFF1EBE1),
    trait = Color(0x1FF1EBE1),
    foret = Color(0xFF7CC2A3),
    or = Color(0xFFE9B84D),
)

/** Le paysage Savane : l'Aube aux collines dorées (maquette du Nouvel Espace). */
val Savane = Aube.copy(
    ciel = Color(0xFFF6E7C8),
    astre = Color(0xFFF2C879),
    colline1 = Color(0xFFE8C47A),
    colline2 = Color(0xFFD4A85A),
    colline3 = Color(0xFFB98A42),
    colline4 = Color(0xFF9C7036),
    voile = Color(0xD9F6E7C8),
)

/** La Savane le soir : une nuit chaude, une lune dorée, pour reconnaître l'Espace même la nuit. */
val SavaneNuit = Nuit.copy(
    ciel = Color(0xFF211A13),
    astre = Color(0xFFF0D59A),
    colline1 = Color(0xFF2B2117),
    colline2 = Color(0xFF35291C),
    colline3 = Color(0xFF403121),
    colline4 = Color(0xFF4C3A27),
    sol = Color(0xFF261E16),
    sol2 = Color(0xFF352A1F),
    voile = Color(0xD9211A13),
    encre = Color(0xFFF3EBDD),
    encre2 = Color(0xFFB8A88E),
    surLaterite = Color(0xFF211A13),
)

/** Plein soleil : contraste renforcé pour lire dehors, sous le soleil de midi (thème de Banco). */
val PleinSoleil = Aube.copy(
    ciel = Color(0xFFFFFFFF),
    sol = Color(0xFFFFFFFF),
    sol2 = Color(0xFFEDEDED),
    voile = Color(0xF5FFFFFF),
    encre = Color(0xFF000000),
    encre2 = Color(0xFF1F1C18),
    laterite = Color(0xFF93401F),
    lateriteTexte = Color(0xFF7A3418),
    danger = Color(0xFF8A241D),
    interFond = Color(0xFF595959),
    trait = Color(0xFF1F1C18),
    foret = Color(0xFF1F4D3D),
    or = Color(0xFFC98A12),
)

/** Le réglage Plein soleil de Sama (réglages sécurisés du profil) : 0 jamais, 1 quand la lumière est très forte, 2 toujours. */
const val CLE_PLEIN_SOLEIL = "sama_plein_soleil"

/**
 * La palette d'un Espace, selon son paysage et l'heure : Lagune passe de l'Aube à la Nuit,
 * Savane de l'or du jour à sa nuit chaude, et le paysage Nuit l'est toujours.
 */
fun palette(paysage: PaysageEspace, nuit: Boolean, pleinSoleil: Boolean = false): Banco = if (pleinSoleil) PleinSoleil else when (paysage) {
    PaysageEspace.LAGUNE -> if (nuit) Nuit else Aube
    PaysageEspace.SAVANE -> if (nuit) SavaneNuit else Savane
    PaysageEspace.NUIT -> Nuit
}

/** Vrai le soir : c'est l'heure, pas la palette, qui le dit (le paysage Nuit est sombre même le jour). */
val LocalNuit = staticCompositionLocalOf { false }

val LocalBanco = staticCompositionLocalOf { Aube }

/**
 * Les gouttes du curseur et de la sélection, et le surlignage du texte choisi, à la couleur du curseur [c] (Compose
 * les ferait bleus).
 */
fun selectionTexte(c: Color) = TextSelectionColors(handleColor = c, backgroundColor = c.copy(alpha = 0.35f))

/**
 * La palette [b] pour ce qui est dedans, la sélection du texte à la couleur de sa latérite, comme le curseur des champs.
 * [autres] : ce qu'on pose en même temps (l'heure du soir…).
 */
@Composable
fun AvecBanco(b: Banco, vararg autres: ProvidedValue<*>, contenu: @Composable () -> Unit) =
    CompositionLocalProvider(LocalBanco provides b, LocalTextSelectionColors provides selectionTexte(b.laterite), *autres, content = contenu)

/**
 * Noto Sans variable, jouée en largeur : l'heure fine et étroite (62,5 %),
 * les monuments serrés (87,5 %), le texte courant en largeur normale.
 */
object Polices {
    // L'axe de largeur (wdth) est encore marqué expérimental dans Compose.
    @OptIn(ExperimentalTextApi::class)
    private fun noto(poids: Int, largeur: Float) = Font(
        resId = R.font.noto_sans,
        weight = FontWeight(poids),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(poids),
            FontVariation.width(largeur),
        ),
    )

    val horloge = FontFamily(noto(200, 62.5f))
    val monument = FontFamily(noto(600, 87.5f))
    val corps = FontFamily(noto(300, 100f), noto(400, 100f), noto(500, 100f), noto(600, 100f))
}
