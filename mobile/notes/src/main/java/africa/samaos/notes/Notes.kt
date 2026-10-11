package africa.samaos.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.BarreAvis
import africa.samaos.banco.appli.Identite
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PileDoigt

/** Le papier des Notes : un fond chaud et doux, le miel pour accent. */
internal val Papier = Identite(
    fond = Color(0xFFF7F3EC), surface = Color(0xFFFFFDF9), champ = Color(0x0F2A2418), encre = Color(0xFF2A2620), encre2 = Color(0xFF8C8374),
    trait = Color(0x142A2418), accent = Color(0xFFE0A63A), surAccent = Color(0xFFFFFFFF), voile = Color(0x26E0A63A), accentTexte = Color(0xFFA06C12),
)
internal val PapierNuit = Identite(
    fond = Color(0xFF171511), surface = Color(0xFF221F1A), champ = Color(0x14FFFFFF), encre = Color(0xFFF2ECE2), encre2 = Color(0xFFA39A8B),
    trait = Color(0x1AFFFFFF), accent = Color(0xFFE8B552), surAccent = Color(0xFF1E1A12), voile = Color(0x2EE8B552), accentTexte = Color(0xFFF0C774), sombre = true,
)

/** Les couleurs des dossiers : douces, sans criard. */
internal val TEINTES = listOf(Color(0xFFE0A63A), Color(0xFF7FA27A), Color(0xFF6A9CC4), Color(0xFFD98579), Color(0xFF9A88C9), Color(0xFFB98A5E))
internal val NOMS_TEINTES = listOf("Miel", "Sauge", "Ciel", "Rose", "Lavande", "Terre")

internal fun teinte(i: Int) = TEINTES[Math.floorMod(i, TEINTES.size)]

internal object Ic {
    const val EPINGLE = "M9.5 3.5h5l-.8 6l3.3 3.3v1.2H7v-1.2l3.3-3.3z M12 14v6.5"
    const val SANS_EPINGLE = "M9.5 3.5h5l-.8 6l3.3 3.3v1.2H7v-1.2l3.3-3.3z M12 14v6.5 M4 4l16 16"
    const val CASES = "M4 6.5a2 2 0 1 0 4 0a2 2 0 1 0-4 0 M4 12.5a2 2 0 1 0 4 0a2 2 0 1 0-4 0 M4 18.5a2 2 0 1 0 4 0a2 2 0 1 0-4 0 M11 6.5h9 M11 12.5h9 M11 18.5h9"
    const val NOTE = "M6 3.5h9l3.5 3.5v13.5H6z M14.5 3.5V7.5h4 M9 12h6 M9 16h4"
    const val NOUVELLE = "M12.5 4.5H6.5a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-6 M17 3.5l3.5 3.5l-8 8H9v-3.5z"
    const val TROIS_POINTS = "M6.5 12h.01 M12 12h.01 M17.5 12h.01"
    const val DEPLACER = "M4 6.5A1.5 1.5 0 0 1 5.5 5H10l2 2h6.5A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z M10 13h6 M13.5 10.5L16 13l-2.5 2.5"
    const val NOUVEAU_DOSSIER = "M4 6.5A1.5 1.5 0 0 1 5.5 5H10l2 2h6.5A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z M12 10v6 M9 13h6"
    const val DEVERROUILLER = "M6 11h12v9H6z M8.5 11V8a3.5 3.5 0 0 1 6.8-1.2"
    const val TRIER = "M7 5v14 M4 16l3 3l3-3 M13 7h7 M13 12h5 M13 17h3"
}

internal fun st(taille: Float, couleur: Color, poids: FontWeight = FontWeight.Normal, interligne: Float = 1.35f) =
    TextStyle(fontFamily = Polices.corps, fontWeight = poids, fontSize = taille.sp, lineHeight = (taille * interligne).sp, color = couleur)

internal sealed interface Filtre {
    data object Toutes : Filtre
    data object Listes : Filtre
    data object Rappels : Filtre
    data class Dans(val dossier: Long) : Filtre
}

internal sealed interface Vue {
    data object Dossiers : Vue
    data class Liste(val filtre: Filtre) : Vue
    /** Une note : [id] existante, ou nouvelle (dans [dossier], en liste à cocher si [cases], avec un texte partagé). */
    data class Edition(val id: Long, val dossier: Long = Carnet.NOTES, val cases: Boolean = false, val titre: String? = null, val texte: String? = null) : Vue
    data object Corbeille : Vue
}

/** Les Notes de Sama : des dossiers, des notes où l'on mélange texte, titres et listes, des rappels, un verrou. */
class Notes : ComponentActivity() {
    private var pile by mutableStateOf(listOf<Vue>(Vue.Dossiers))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Carnet.charger(this)
        lire(intent)
        setContent {
            val id = if (isSystemInDarkTheme()) PapierNuit else Papier
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) {
                val aller: (Vue) -> Unit = { pile = pile + it }
                val retour: () -> Unit = { if (pile.size > 1) pile = pile.dropLast(1) else finish() }
                Box(Modifier.fillMaxSize().background(LocalIdentite.current.fond)) {
                    // Une page arrive de la droite, comme on tourne vers la suite ; le retour la renvoie, en suivant le doigt (l'éditeur gère le sien).
                    PileDoigt(pile, retour, actif = pile.size > 1 && pile.last() !is Vue.Edition) { v ->
                        when (v) {
                            Vue.Dossiers -> PageDossiers(aller)
                            is Vue.Liste -> PageListe(v.filtre, aller, retour)
                            is Vue.Edition -> PageEdition(v, retour) { pile = pile.dropLast(1) + it }
                            Vue.Corbeille -> PageCorbeille(retour)
                        }
                    }
                    BarreAvis(Modifier.align(Alignment.BottomCenter), bas = 20.dp)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    /** Quitter l'appli referme les notes verrouillées. */
    override fun onStop() {
        Verrou.ouvert = false
        super.onStop()
    }

    /** Une notification de rappel ouvre sa note ; un texte partagé devient une nouvelle note. */
    private fun lire(i: Intent?) {
        val id = i?.getLongExtra("id", -1) ?: -1
        when {
            id >= 0 && Carnet.note(id) != null -> pile = listOf(Vue.Dossiers, Vue.Liste(Filtre.Dans(Carnet.note(id)!!.dossier)), Vue.Edition(id))
            i?.action == Intent.ACTION_SEND -> pile = listOf(
                Vue.Dossiers,
                Vue.Edition(System.currentTimeMillis(), titre = i.getStringExtra(Intent.EXTRA_SUBJECT), texte = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()),
            )
        }
    }
}

/** Le grand titre d'une page, à la manière d'un carnet. */
@Composable
internal fun GrandTitre(texte: String, sous: String? = null, modifier: Modifier = Modifier) {
    val a = LocalIdentite.current
    Column(modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 10.dp)) {
        BasicText(
            texte, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em, color = a.encre),
        )
        if (sous != null) AnimatedContent(sous, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "sous") { s ->
            BasicText(s, style = st(14f, a.encre2))
        }
    }
}

/** La barre du haut : le retour, nommé d'après la page d'avant (« ‹ Dossiers »), et des boutons au bout. */
@Composable
internal fun Barre(retour: String?, onRetour: () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (retour != null) {
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Retour à $retour", role = Role.Button, onClick = onRetour).padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconeTrait(Icones.RETOUR, 24.dp, a.accentTexte, epaisseur = 2f)
                BasicText(retour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp), style = st(17f, a.accentTexte, FontWeight.Medium))
            }
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Un bouton rond, qui s'enfonce un peu sous le doigt. */
@Composable
internal fun Rond(icone: String, description: String, plein: Boolean = false, taille: Dp = 42.dp, epaisseur: Float = 1.9f, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val source = remember { MutableInteractionSource() }
    val presse by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (presse) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "appui")
    Box(
        Modifier.size(taille).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(if (plein) a.accent else a.voile)
            .clickable(interactionSource = source, indication = null, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, taille * 0.5f, if (plein) a.surAccent else a.accentTexte, epaisseur = epaisseur) }
}

/** Le champ de recherche, doux. */
@Composable
internal fun Recherche(valeur: String, indication: String, changer: (String) -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp)).background(a.champ).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTrait(Icones.RECHERCHE, 18.dp, a.encre2)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = valeur, onValueChange = changer, singleLine = true,
            textStyle = st(16f, a.encre), cursorBrush = SolidColor(a.accent),
            modifier = Modifier.weight(1f).semantics { contentDescription = indication },
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (valeur.isEmpty()) BasicText(indication, style = st(16f, a.encre2))
                    champ()
                }
            },
        )
        AnimatedVisibility(valeur.isNotEmpty(), enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
            Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClickLabel = "Effacer la recherche", role = Role.Button) { changer("") }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(a.encre2.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) { IconeTrait(Icones.FERMER, 11.dp, a.fond, epaisseur = 2.4f) }
            }
        }
    }
}

/** Un groupe de lignes sur le papier : coins doux, pas d'ombre. */
@Composable
internal fun Groupe(modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    val a = LocalIdentite.current
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(a.surface), content = contenu)
}

@Composable
internal fun Separateur(debut: Dp = 16.dp) {
    Box(Modifier.padding(start = debut).fillMaxWidth().height(0.6.dp).background(LocalIdentite.current.trait))
}

/** Le titre d'une section (« Aujourd'hui », « Mes dossiers »). */
@Composable
internal fun Section(texte: String, modifier: Modifier = Modifier) {
    BasicText(texte, modifier = modifier.padding(start = 26.dp, end = 26.dp, top = 20.dp, bottom = 8.dp), style = st(15f, LocalIdentite.current.encre, FontWeight.SemiBold))
}

/** Le pied d'une page : le nombre de notes au milieu, le bouton pour écrire au bout. */
@Composable
internal fun Pied(texte: String, modifier: Modifier = Modifier, gauche: (@Composable () -> Unit)? = null, ecrire: (() -> Unit)?) {
    val a = LocalIdentite.current
    Box(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { gauche?.invoke() }
        AnimatedContent(texte, modifier = Modifier.align(Alignment.Center), transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "pied") { t ->
            BasicText(t, style = st(13f, a.encre2, FontWeight.Medium))
        }
        if (ecrire != null) Box(Modifier.align(Alignment.CenterEnd)) { Rond(Ic.NOUVELLE, "Nouvelle note", plein = true, taille = 52.dp, epaisseur = 2f, onClick = ecrire) }
    }
}
