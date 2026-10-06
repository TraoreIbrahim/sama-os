package africa.samaos.banco.appli

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Polices

/**
 * Les applis natives de Sama : une ossature commune, une identité par appli (maquettes du lot 2).
 * Elles ne reprennent ni le paysage ni la crête de Banco ; chacune pose les couleurs de son icône.
 */
@Immutable
data class Identite(
    val fond: Color,
    val surface: Color = Color(0xFFFFFFFF),
    val champ: Color,
    val encre: Color,
    val encre2: Color,
    val trait: Color,
    val accent: Color,
    val surAccent: Color = Color(0xFFFFFFFF),
    val voile: Color,
    val accentTexte: Color = accent,
    val sombre: Boolean = false,
)

object Identites {
    val Telephone = Identite(
        fond = Color(0xFFF4F7F5), champ = Color(0x0F14211C), encre = Color(0xFF14211C), encre2 = Color(0xFF56635D),
        trait = Color(0x1A14211C), accent = Color(0xFF2F6B57), voile = Color(0x1F2F6B57),
    )
    val TelephoneNuit = Identite(
        fond = Color(0xFF111A16), surface = Color(0xFF1A2621), champ = Color(0x1AFFFFFF), encre = Color(0xFFE9F0EC), encre2 = Color(0xFF9DB0A7),
        trait = Color(0x1FFFFFFF), accent = Color(0xFF7CC2A3), surAccent = Color(0xFF0E2A20), voile = Color(0x297CC2A3), sombre = true,
    )
    val Contacts = Identite(
        fond = Color(0xFFFAF6F1), champ = Color(0x0F231B14), encre = Color(0xFF231B14), encre2 = Color(0xFF6B5E52),
        trait = Color(0x1A231B14), accent = Color(0xFF7D624B), voile = Color(0x217D624B),
    )
    val ContactsNuit = Identite(
        fond = Color(0xFF1A1511), surface = Color(0xFF241D17), champ = Color(0x1AFFFFFF), encre = Color(0xFFF2EAE1), encre2 = Color(0xFFB3A597),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFC9A383), surAccent = Color(0xFF2A1E14), voile = Color(0x29C9A383), sombre = true,
    )
    val Messages = Identite(
        fond = Color(0xFFF7F4EE), champ = Color(0x0F1F1C18), encre = Color(0xFF1F1C18), encre2 = Color(0xFF665E54),
        trait = Color(0x1A1F1C18), accent = Color(0xFF3D5A99), voile = Color(0x1F3D5A99),
    )
    val MessagesNuit = Identite(
        fond = Color(0xFF14171F), surface = Color(0xFF1D2230), champ = Color(0x1AFFFFFF), encre = Color(0xFFEDEFF4), encre2 = Color(0xFFA6ACBD),
        trait = Color(0x1FFFFFFF), accent = Color(0xFF9DB2E3), surAccent = Color(0xFF14213F), voile = Color(0x299DB2E3), sombre = true,
    )
    /** L'Horloge : la nuit bleue, le vert d'eau des aiguilles (toujours sombre). */
    val Horloge = Identite(
        fond = Color(0xFF1E2640), surface = Color(0xFF2B3555), champ = Color(0x1AFFFFFF), encre = Color(0xFFEEF1F6), encre2 = Color(0xFFA9B2C7),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFA3D6C1), surAccent = Color(0xFF15302A), voile = Color(0x29A3D6C1), sombre = true,
    )
    val Calculatrice = Identite(
        fond = Color(0xFFECE8E2), surface = Color(0xFFF8F6F2), champ = Color(0x0F2F2B26), encre = Color(0xFF2F2B26), encre2 = Color(0xFF6F675D),
        trait = Color(0x1A2F2B26), accent = Color(0xFF3A342D), voile = Color(0x1F5B544B),
    )
    val CalculatriceNuit = Identite(
        fond = Color(0xFF1C1A17), surface = Color(0xFF2A2723), champ = Color(0x1AFFFFFF), encre = Color(0xFFF0ECE6), encre2 = Color(0xFFB0A89D),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFE6DAC2), surAccent = Color(0xFF2A2723), voile = Color(0x29E6DAC2), sombre = true,
    )
    val Notes = Identite(
        fond = Color(0xFFFBF3DF), surface = Color(0xFFFFFCF3), champ = Color(0x122B2316), encre = Color(0xFF2B2316), encre2 = Color(0xFF6F6350),
        trait = Color(0x1A2B2316), accent = Color(0xFFB5532F), voile = Color(0x1FB5532F), accentTexte = Color(0xFF93401F),
    )
    val NotesNuit = Identite(
        fond = Color(0xFF221D14), surface = Color(0xFF2E271B), champ = Color(0x1AFFFFFF), encre = Color(0xFFF3EBDD), encre2 = Color(0xFFB8A88E),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFE07A52), surAccent = Color(0xFF221D14), voile = Color(0x29E07A52), accentTexte = Color(0xFFEE9A78), sombre = true,
    )

    val Agenda = Identite(
        fond = Color(0xFFFCFAF6), champ = Color(0x0F1F1C18), encre = Color(0xFF1F1C18), encre2 = Color(0xFF665E54),
        trait = Color(0x1A1F1C18), accent = Color(0xFF3D5A99), voile = Color(0x1A3D5A99),
    )
    val AgendaNuit = Identite(
        fond = Color(0xFF15171D), surface = Color(0xFF1F222B), champ = Color(0x1AFFFFFF), encre = Color(0xFFEDEFF4), encre2 = Color(0xFFA6ACBD),
        trait = Color(0x1FFFFFFF), accent = Color(0xFF9DB2E3), surAccent = Color(0xFF14213F), voile = Color(0x299DB2E3), sombre = true,
    )

    /** Fichiers : le carton et le papier kraft. */
    val Fichiers = Identite(
        fond = Color(0xFFF2ECE2), surface = Color(0xFFFBF8F3), champ = Color(0x122A2018), encre = Color(0xFF2A2018), encre2 = Color(0xFF6E6154),
        trait = Color(0x1A2A2018), accent = Color(0xFF6E5038), voile = Color(0x1F6E5038),
    )
    val FichiersNuit = Identite(
        fond = Color(0xFF1B1612), surface = Color(0xFF26201A), champ = Color(0x1AFFFFFF), encre = Color(0xFFF2EAE0), encre2 = Color(0xFFB5A796),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFC9A787), surAccent = Color(0xFF2A1E14), voile = Color(0x29C9A787), sombre = true,
    )
    /** Mail : le papier à lettres et la terre cuite du timbre. */
    val Mail = Identite(
        fond = Color(0xFFFBF8F3), champ = Color(0x0F1F1C18), encre = Color(0xFF1F1C18), encre2 = Color(0xFF665E54),
        trait = Color(0x1A1F1C18), accent = Color(0xFFB5532F), voile = Color(0x1AB5532F), accentTexte = Color(0xFF93401F),
    )
    val MailNuit = Identite(
        fond = Color(0xFF181513), surface = Color(0xFF241F1C), champ = Color(0x1AFFFFFF), encre = Color(0xFFF4EEE8), encre2 = Color(0xFFB7ACA2),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFE07A52), surAccent = Color(0xFF2A1A12), voile = Color(0x29E07A52), accentTexte = Color(0xFFF0A27E), sombre = true,
    )
    /** Sugu : le marché, sa poussière claire et la latérite. */
    val Sugu = Identite(
        fond = Color(0xFFFFF7EF), surface = Color(0xFFFFFFFF), champ = Color(0x0F2A1A12), encre = Color(0xFF2A1A12), encre2 = Color(0xFF6E5A4E),
        trait = Color(0x1A2A1A12), accent = Color(0xFFB5532F), surAccent = Color(0xFFFFF4EA), voile = Color(0x1FB5532F), accentTexte = Color(0xFF93401F),
    )
    val SuguNuit = Identite(
        fond = Color(0xFF1C1512), surface = Color(0xFF2A201B), champ = Color(0x1AFFFFFF), encre = Color(0xFFF6ECE4), encre2 = Color(0xFFBFAA9C),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFE07A52), surAccent = Color(0xFF2A1A12), voile = Color(0x29E07A52), accentTexte = Color(0xFFF0A27E), sombre = true,
    )
    /** Photos : le blanc qui laisse la place aux images, l'ocre. */
    val Photos = Identite(
        fond = Color(0xFFFFFFFF), surface = Color(0xFFF6F1EC), champ = Color(0x0F221A14), encre = Color(0xFF221A14), encre2 = Color(0xFF6E6259),
        trait = Color(0x1A221A14), accent = Color(0xFFA8693C), voile = Color(0x2EC98F62), accentTexte = Color(0xFF8E5630),
    )
    val PhotosNuit = Identite(
        fond = Color(0xFF121010), surface = Color(0xFF1E1A17), champ = Color(0x1AFFFFFF), encre = Color(0xFFF4EEE8), encre2 = Color(0xFFB5A89C),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFE0A574), surAccent = Color(0xFF2A1A0E), voile = Color(0x29E0A574), accentTexte = Color(0xFFE8B48A), sombre = true,
    )
    /** Lecteur : le violet des pochettes. */
    val Lecteur = Identite(
        fond = Color(0xFFF7F3F8), champ = Color(0x0F231A2B), encre = Color(0xFF231A2B), encre2 = Color(0xFF665A70),
        trait = Color(0x1A231A2B), accent = Color(0xFF5B3F6E), voile = Color(0x1F5B3F6E),
    )
    val LecteurNuit = Identite(
        fond = Color(0xFF18131C), surface = Color(0xFF221C28), champ = Color(0x1AFFFFFF), encre = Color(0xFFF1ECF5), encre2 = Color(0xFFB3A8BE),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFC3A6D8), surAccent = Color(0xFF2A1A36), voile = Color(0x29C3A6D8), sombre = true,
    )
    /** Dictaphone : le rouge de l'enregistrement. */
    val Dictaphone = Identite(
        fond = Color(0xFFFBF6F4), champ = Color(0x0F2A1515), encre = Color(0xFF2A1515), encre2 = Color(0xFF6E5656),
        trait = Color(0x1A2A1515), accent = Color(0xFF9A3B3B), voile = Color(0x1A9A3B3B),
    )
    val DictaphoneNuit = Identite(
        fond = Color(0xFF1C1414), surface = Color(0xFF281D1D), champ = Color(0x1AFFFFFF), encre = Color(0xFFF5ECEC), encre2 = Color(0xFFBBA5A5),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFE59C9C), surAccent = Color(0xFF3A1515), voile = Color(0x29E59C9C), sombre = true,
    )

    /** Griot : la nuit bleue de la barre, l'or des liens qui comptent. La page, elle, garde ses couleurs. */
    val Griot = Identite(
        fond = Color(0xFFFFFFFF), surface = Color(0xFF1E2740), champ = Color(0x0F1E2740), encre = Color(0xFF1E2740), encre2 = Color(0xFF5A627A),
        trait = Color(0x1A1E2740), accent = Color(0xFF1E2740), surAccent = Color(0xFFF2C879), voile = Color(0x141E2740),
    )
    val GriotNuit = Identite(
        fond = Color(0xFF12172A), surface = Color(0xFF1E2740), champ = Color(0x1AFFFFFF), encre = Color(0xFFEEF0F6), encre2 = Color(0xFFA8AFC4),
        trait = Color(0x1FFFFFFF), accent = Color(0xFFF2C879), surAccent = Color(0xFF1E2740), voile = Color(0x29F2C879), sombre = true,
    )

    /** L'Appareil photo : toujours sombre, pour que l'image soit seule à briller. */
    val Appareil = Identite(
        fond = Color(0xFF0E0D0B), surface = Color(0xFF1F1C18), champ = Color(0x24FFFFFF), encre = Color(0xFFF4EEE4), encre2 = Color(0xFFB3AA9C),
        trait = Color(0x24FFFFFF), accent = Color(0xFFE6DAC2), surAccent = Color(0xFF1F1C18), voile = Color(0x2EE6DAC2), sombre = true,
    )

    /** L'appel en cours : un fond sombre, la latérite pour raccrocher. */
    val Appel = Identite(
        fond = Color(0xFF14211C), surface = Color(0xFF1E2E28), champ = Color(0x24FFFFFF), encre = Color(0xFFF1F4F2), encre2 = Color(0xFFA9B8B1),
        trait = Color(0x24FFFFFF), accent = Color(0xFF7CC2A3), surAccent = Color(0xFF0E2A20), voile = Color(0x2E7CC2A3), sombre = true,
    )
}

val LocalIdentite = staticCompositionLocalOf { Identites.Telephone }

private val titre = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em)

/** L'écran d'une appli : son fond, sous la barre d'état. */
@Composable
fun EcranAppli(modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    val a = LocalIdentite.current
    Column(modifier.fillMaxSize().background(a.fond).statusBarsPadding(), content = contenu)
}

/** La tête d'un écran : le titre, et des boutons ronds au bout. */
@Composable
fun Tete(texte: String, retour: (() -> Unit)? = null, petit: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    val a = LocalIdentite.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (retour != null) 4.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (retour != null) BoutonAppli("M15 6l-6 6l6 6", "Retour", onClick = retour)
        BasicText(
            texte, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            style = if (petit) titre.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = a.encre) else titre.copy(color = a.encre),
        )
        actions()
    }
}

/** Un bouton rond de 48 dp, nu, voilé ou plein d'accent. */
@Composable
fun BoutonAppli(icone: String, description: String, style: Char = ' ', taille: Dp = 48.dp, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Box(
        Modifier
            .size(taille)
            .clip(CircleShape)
            .then(
                when (style) {
                    'v' -> Modifier.background(a.voile)
                    'a' -> Modifier.background(a.accent)
                    else -> Modifier
                },
            )
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        IconeTrait(icone, 24.dp, if (style == 'a') a.surAccent else if (style == 'v') a.accentTexte else a.encre)
    }
}

/** Le champ de recherche d'une appli : un galet. */
@Composable
fun ChampAppli(valeur: String, indication: String, changer: (String) -> Unit, modifier: Modifier = Modifier) {
    val a = LocalIdentite.current
    Row(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(a.champ).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTrait("M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z M20 20l-4.8-4.8", 20.dp, a.encre2)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = valeur,
            onValueChange = changer,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre),
            cursorBrush = SolidColor(a.accent),
            modifier = Modifier.weight(1f).semantics { contentDescription = indication },
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (valeur.isEmpty()) BasicText(indication, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2))
                    champ()
                }
            },
        )
    }
}

/** Une rubrique : un petit titre en encre douce. */
@Composable
fun Rub(texte: String) {
    val a = LocalIdentite.current
    BasicText(
        texte,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, color = a.encre2),
    )
}

/** Une ligne d'appli : un début (avatar, icône), le nom, une seconde ligne, et une fin. */
@Composable
fun LigneAppli(
    nom: String,
    second: String? = null,
    couleurSecond: Color? = null,
    debut: (@Composable () -> Unit)? = null,
    fin: (@Composable () -> Unit)? = null,
    surAppuiLong: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val a = LocalIdentite.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (onClick != null || surAppuiLong != null) {
                    Modifier.combinedClickable(onClickLabel = nom, role = Role.Button, onLongClick = surAppuiLong, onClick = { onClick?.invoke() })
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        debut?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(nom, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 23.sp, color = a.encre))
            if (second != null) BasicText(second, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 19.sp, color = couleurSecond ?: a.encre2))
        }
        fin?.invoke()
    }
}

private val TEINTES = listOf(Color(0xFF3D5A99), Color(0xFF8A4F7D), Color(0xFF2E7D6B), Color(0xFFB0602F), Color(0xFF5B6B2E), Color(0xFF7A5230), Color(0xFF9A3B3B))

fun couleurDe(nom: String): Color = TEINTES[Math.floorMod(nom.hashCode(), TEINTES.size)]

fun initiales(nom: String): String {
    val mots = nom.split(' ', '-').filter { it.isNotBlank() && it.first().isLetter() }
    return if (mots.isEmpty()) "#" else mots.take(2).joinToString("") { it.first().uppercase() }
}

/** L'avatar d'une personne : ses initiales sur une couleur tirée de son nom, ou une icône pour un numéro. */
@Composable
fun Avatar(nom: String?, taille: Dp = 44.dp, icone: String? = null) {
    Box(Modifier.size(taille).background(if (nom != null) couleurDe(nom) else Color(0xFF8F8476), CircleShape), contentAlignment = Alignment.Center) {
        if (nom != null && icone == null) {
            BasicText(initiales(nom), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = (taille.value * 0.34f).sp, color = Color.White))
        } else {
            IconeTrait(icone ?: "M12 12a4 4 0 1 0 0-8a4 4 0 1 0 0 8z M4 20c1-4 4-6 8-6s7 2 8 6", taille * 0.5f, Color.White)
        }
    }
}

/** La pastille d'une SIM : son numéro de fente, sur la couleur de l'opérateur. */
@Composable
fun PuceSim(fente: Int, operateur: String?) {
    val (fond, encre) = couleursOperateur(operateur)
    Box(Modifier.heightIn(min = 18.dp).clip(RoundedCornerShape(5.dp)).background(fond).padding(horizontal = 5.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        BasicText("$fente", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = encre))
    }
}

/** Les couleurs des opérateurs ivoiriens, pour reconnaître une SIM d'un coup d'œil. */
fun couleursOperateur(nom: String?): Pair<Color, Color> {
    val n = nom.orEmpty().lowercase()
    return when {
        "orange" in n -> Color(0xFFE07B2E) to Color.White
        "mtn" in n -> Color(0xFFC9A20A) to Color(0xFF1F1C18)
        "moov" in n -> Color(0xFF1F5AA6) to Color.White
        else -> Color(0xFF5A6B66) to Color.White
    }
}

/** La barre du bas d'une appli : ses pages, la courante voilée de son accent. */
@Composable
fun NavAppli(pages: List<Pair<String, String>>, courante: Int, choisir: (Int) -> Unit) {
    val a = LocalIdentite.current
    Column(Modifier.fillMaxWidth().background(a.fond)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(a.trait))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 10.dp, start = 8.dp, end = 8.dp)) {
            pages.forEachIndexed { i, (nom, icone) ->
                val ici = i == courante
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = nom, role = Role.Tab) { choisir(i) }
                        .semantics { selected = ici }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(60.dp, 32.dp).clip(RoundedCornerShape(16.dp)).then(if (ici) Modifier.background(a.voile) else Modifier), contentAlignment = Alignment.Center) {
                        IconeTrait(icone, 22.dp, if (ici) a.accentTexte else a.encre2)
                    }
                    BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (ici) a.encre else a.encre2))
                }
            }
        }
    }
}

/** Le gros bouton flottant d'une appli (« Nouveau message », « Ajouter »). */
@Composable
fun BoxScope.Fab(texte: String?, icone: String, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp)
            .heightIn(min = 60.dp)
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(a.accent)
            .clickable(onClickLabel = texte ?: "Ajouter", role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconeTrait(icone, 22.dp, a.surAccent)
        if (texte != null) BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.surAccent))
    }
}

/** Une puce de filtre (« Tous », « Manqués »). */
@Composable
fun PuceFiltre(texte: String, choisie: Boolean, debut: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (choisie) Modifier.background(a.voile) else Modifier.border(1.dp, a.trait, RoundedCornerShape(18.dp)))
            .clickable(onClickLabel = texte, role = Role.RadioButton, onClick = onClick)
            .semantics { selected = choisie }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        debut?.invoke()
        BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (choisie) a.accentTexte else a.encre))
    }
}

/** Le bouton d'action d'une appli : plein d'accent, voilé (s) ou texte (t). */
@Composable
fun BoutonTexteAppli(texte: String, style: Char = ' ', icone: String? = null, actif: Boolean = true, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .then(
                when (style) {
                    's' -> Modifier.background(a.voile)
                    't' -> Modifier
                    else -> Modifier.background(if (actif) a.accent else a.champ)
                },
            )
            .clickable(enabled = actif, onClickLabel = texte, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (style == 't') 10.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val encre = when (style) {
            's', 't' -> a.accentTexte
            else -> if (actif) a.surAccent else a.encre2
        }
        if (icone != null) IconeTrait(icone, 20.dp, encre)
        BasicText(texte, maxLines = 1, softWrap = false, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = encre))
    }
}

/** Un filet qui sépare. */
@Composable
fun Filet() {
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(0.5.dp).background(LocalIdentite.current.trait))
}

/** Une case à cocher d'appli. */
@Composable
fun CaseAppli(cochee: Boolean) {
    val a = LocalIdentite.current
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).then(if (cochee) Modifier.background(a.accent) else Modifier.border(2.dp, a.encre2, RoundedCornerShape(7.dp))),
        contentAlignment = Alignment.Center,
    ) { if (cochee) IconeTrait("M5 12.5l4.5 4.5L19 7", 18.dp, a.surAccent, epaisseur = 2.25f) }
}

/** Un interrupteur d'appli, à l'accent de l'appli. */
@Composable
fun InterAppli(allume: Boolean) {
    val a = LocalIdentite.current
    Box(Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(50)).background(if (allume) a.accent else Color(0xFF8F8476)).padding(4.dp), contentAlignment = if (allume) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(24.dp).background(if (allume) a.surAccent else Color.White, CircleShape))
    }
}

/** Une feuille d'actions posée en bas de l'écran, sur un voile ; toucher le voile la ferme. */
@Composable
fun FeuilleAppli(fermer: () -> Unit, titre: String? = null, contenu: @Composable ColumnScope.() -> Unit) {
    val a = LocalIdentite.current
    androidx.activity.compose.BackHandler(onBack = fermer)
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = null, indication = null, onClickLabel = "Fermer", onClick = fermer),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(a.fond)
                .clickable(interactionSource = null, indication = null) {}
                .navigationBarsPadding().padding(top = 10.dp, bottom = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(a.trait))
            if (titre != null) {
                BasicText(
                    titre, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = a.encre),
                )
            }
            contenu()
        }
    }
}

/** Une ligne de feuille d'actions. `danger` : en latérite (jeter, effacer). */
@Composable
fun ActionFeuille(icone: String, texte: String, danger: Boolean = false, second: String? = null, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val encre = if (danger) Color(0xFFB5532F).let { if (a.sombre) Color(0xFFEE9A78) else it } else a.encre
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = texte, role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconeTrait(icone, 22.dp, if (danger) encre else a.encre2)
        Column {
            BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = encre))
            if (second != null) BasicText(second, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
        }
    }
}

/** Une question avant d'agir : un titre, une explication, puis les boutons (le dernier est l'action). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DialogueAppli(titre: String, texte: String?, fermer: () -> Unit, contenu: @Composable ColumnScope.() -> Unit = {}, boutons: @Composable RowScope.() -> Unit) {
    val a = LocalIdentite.current
    androidx.activity.compose.BackHandler(onBack = fermer)
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickable(interactionSource = null, indication = null, onClickLabel = "Fermer", onClick = fermer).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(a.fond).clickable(interactionSource = null, indication = null) {}.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = a.encre))
            if (texte != null) BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2))
            contenu()
            // Les boutons passent à la ligne plutôt que de s'écraser.
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { boutons() }
        }
    }
}
