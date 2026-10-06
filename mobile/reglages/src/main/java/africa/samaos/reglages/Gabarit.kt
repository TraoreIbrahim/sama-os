package africa.samaos.reglages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Astre
import africa.samaos.banco.Crete
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Interrupteur
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Pastille
import africa.samaos.banco.Paysage
import africa.samaos.banco.Polices
import africa.samaos.banco.deborder

/**
 * Une page des Réglages (maquettes du lot 3) : le titre dans le ciel, puis le sol à crête où sont
 * les réglages. Le titre part avec le défilement ; le bouton retour reste, sur un voile.
 */
@Composable
fun PageReglages(
    titre: String,
    retour: (() -> Unit)?,
    sousTitre: String? = null,
    entete: (@Composable ColumnScope.() -> Unit)? = null,
    contenu: LazyListScope.() -> Unit,
) {
    val b = LocalBanco.current
    val etat = rememberLazyListState()
    val bas = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().background(b.ciel)) {
        Paysage(astre = Astre(322f, 54f, 26f), collines = listOf(176f))
        // Le sol continue sous la liste jusqu'en bas de l'écran, même quand la page est courte.
        Canvas(Modifier.fillMaxSize()) {
            val crete = etat.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "§crete" }
            val debut = when {
                crete != null -> (crete.offset + crete.size).toFloat()
                etat.firstVisibleItemIndex > 1 -> 0f
                else -> size.height
            }
            if (debut < size.height) drawRect(b.sol, topLeft = Offset(0f, debut), size = Size(size.width, size.height - debut))
        }
        LazyColumn(Modifier.fillMaxSize(), state = etat) {
            item(key = "§titre") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = if (retour != null) 74.dp else 56.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(
                        titre,
                        style = TextStyle(
                            fontFamily = Polices.monument, fontWeight = FontWeight(600), fontSize = 44.sp, lineHeight = 48.sp,
                            letterSpacing = (-0.02).em, color = b.encre, lineBreak = LineBreak.Heading,
                        ),
                    )
                    if (sousTitre != null) {
                        BasicText(sousTitre, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2))
                    }
                    if (entete != null) entete()
                }
            }
            item(key = "§crete") { Crete() }
            item(key = "§haut") { Spacer(Modifier.fillMaxWidth().height(8.dp).background(b.sol)) }
            contenu()
            item(key = "§bas") { Spacer(Modifier.fillMaxWidth().height(bas + 32.dp).background(b.sol)) }
        }
        // Sous la barre d'état, une bande de sol dès que le contenu y passe.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .graphicsLayer {
                    val crete = etat.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "§crete" }
                    alpha = if (etat.firstVisibleItemIndex > 1 || (crete != null && crete.offset < 0)) 1f else 0f
                }
                .background(b.sol),
        )
        if (retour != null) {
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(b.voile)
                    .clickable(onClickLabel = "Revenir", role = Role.Button, onClick = retour)
                    .semantics { contentDescription = "Retour" },
                contentAlignment = Alignment.Center,
            ) { IconeTrait(Icones.RETOUR, 24.dp, b.encre) }
        }
    }
}

/** Un bloc de réglages sur le sol, avec sa rubrique. */
fun LazyListScope.section(titre: String? = null, cle: Any? = null, contenu: @Composable ColumnScope.() -> Unit) {
    item(key = cle ?: titre) {
        val b = LocalBanco.current
        Column(
            Modifier
                .fillMaxWidth()
                .background(b.sol)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        ) {
            if (titre != null) {
                BasicText(
                    titre,
                    modifier = Modifier.padding(bottom = 6.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = b.encre2),
                )
            }
            contenu()
        }
    }
}

/** Ce qu'une rangée montre au bout : un chevron, un interrupteur, une valeur, une pastille, ou rien. */
sealed interface Fin {
    data object Chevron : Fin
    data object Rien : Fin
    data class Valeur(val texte: String) : Fin
    data class Inter(val allume: Boolean, val actif: Boolean = true) : Fin
    data class Choix(val choisie: Boolean) : Fin
    /** Une action au bout de la rangée (« Recevoir », « Ouvrir ») ; [plein] : l'action principale de l'écran. */
    data class Bouton(val texte: String, val plein: Boolean = false) : Fin
}

/** Une rangée de Banco : icône, titre, détail, et ce qu'il y a au bout. */
@Composable
fun Ligne(
    titre: String,
    detail: String? = null,
    icone: String? = null,
    image: ImageBitmap? = null,
    fin: Fin = Fin.Chevron,
    danger: Boolean = false,
    choisie: Boolean = false,
    actif: Boolean = true,
    debut: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val b = LocalBanco.current
    val encre = if (danger) b.danger else b.encre
    Row(
        Modifier
            .deborder(16.dp)
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (choisie) Modifier.background(b.sol2) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = actif,
                        onClickLabel = titre,
                        role = when (fin) {
                            is Fin.Inter -> Role.Switch
                            is Fin.Choix -> Role.RadioButton
                            else -> Role.Button
                        },
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                when (fin) {
                    is Fin.Inter -> stateDescription = if (fin.allume) "Activé" else "Désactivé"
                    is Fin.Choix -> stateDescription = if (fin.choisie) "Choisi" else "Non choisi"
                    else -> {}
                }
            }
            .graphicsLayer { alpha = if (actif) 1f else 0.45f }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            debut != null -> debut()
            image != null -> Image(image, contentDescription = null, modifier = Modifier.size(32.dp))
            icone != null -> IconeTrait(icone, 24.dp, encre)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                titre, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = encre),
            )
            if (detail != null) {
                BasicText(detail, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2))
            }
        }
        when (fin) {
            Fin.Chevron -> if (onClick != null) IconeTrait(Icones.CHEVRON, 20.dp, b.encre2)
            Fin.Rien -> {}
            is Fin.Valeur -> BasicText(fin.texte, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = b.encre2))
            is Fin.Inter -> Interrupteur(fin.allume, fin.actif)
            is Fin.Choix -> Pastille(fin.choisie)
            is Fin.Bouton -> BasicText(
                fin.texte,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (fin.plein) b.laterite else b.sol2).padding(horizontal = 14.dp, vertical = 8.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (fin.plein) b.surLaterite else b.encre),
            )
        }
    }
}

/** Un texte d'explication sous les réglages, en encre douce. */
@Composable
fun Explication(texte: String, couleur: Color? = null) {
    val b = LocalBanco.current
    BasicText(
        texte,
        modifier = Modifier.padding(vertical = 6.dp),
        style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = couleur ?: b.encre2),
    )
}

/** Une jauge de Banco : un galet rempli de latérite (ou d'une autre couleur). */
@Composable
fun Jauge(part: Float, couleur: Color? = null, modifier: Modifier = Modifier) {
    val b = LocalBanco.current
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(b.sol2)) {
        Box(Modifier.fillMaxWidth(part.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(couleur ?: b.laterite))
    }
}

/** Un bouton de texte de Banco (« Oublier », « Annuler »), en latérite. */
@Composable
fun BoutonTexte(texte: String, danger: Boolean = false, onClick: () -> Unit) {
    val b = LocalBanco.current
    BasicText(
        texte,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = if (danger) b.danger else b.lateriteTexte),
    )
}

/** Un bloc d'écart sur le sol. */
fun LazyListScope.ecart(cle: String, hauteur: Int = 12) {
    item(key = cle) { Spacer(Modifier.fillMaxWidth().height(hauteur.dp).background(LocalBanco.current.sol)) }
}

@Composable
fun Puce(texte: String, onClick: () -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(b.sol2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = b.encre))
    }
}
