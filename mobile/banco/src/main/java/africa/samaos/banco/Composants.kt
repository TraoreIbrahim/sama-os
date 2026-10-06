package africa.samaos.banco

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Les composants de Banco partagés par les surfaces de Sama (Accueil, Réglages…).

/** Une rangée de Banco : une icône ou une pastille, un titre, un détail, une valeur, un chevron si elle mène ailleurs. */
@Composable
fun Rangee(
    titre: String,
    detail: String? = null,
    valeur: String? = null,
    icone: String? = null,
    pastille: Color? = null,
    couleur: Color? = null,
    surToucher: (() -> Unit)? = null,
) {
    val b = LocalBanco.current
    val encre = couleur ?: b.encre
    Row(
        Modifier
            .deborder(12.dp)
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (surToucher != null) Modifier.clickable(role = Role.Button, onClick = surToucher) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            pastille != null -> Box(Modifier.padding(horizontal = 6.dp).size(10.dp).background(pastille, CircleShape))
            icone != null -> IconeTrait(icone, 22.dp, encre)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                titre, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = encre),
            )
            if (detail != null) {
                BasicText(detail, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = b.encre2))
            }
        }
        if (valeur != null) {
            BasicText(valeur, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = b.encre2))
        }
        if (surToucher != null) IconeTrait(Icones.CHEVRON, 18.dp, b.encre2)
    }
}

/** Comme la marge négative des listes de Banco : la rangée déborde pour que ses icônes s'alignent sur le texte. */
fun Modifier.deborder(marge: Dp) = layout { mesurable, contraintes ->
    val m = marge.roundToPx()
    val place = mesurable.measure(contraintes.copy(maxWidth = contraintes.maxWidth + 2 * m, minWidth = contraintes.minWidth + 2 * m))
    layout(contraintes.maxWidth, place.height) { place.place(-m, 0) }
}

@Composable
fun Note(texte: String, couleur: Color? = null) {
    val b = LocalBanco.current
    BasicText(
        texte,
        modifier = Modifier.padding(vertical = 10.dp),
        style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 19.sp, color = couleur ?: b.encre2),
    )
}

/** Le gabarit des réglages : le retour, le titre et son soleil dans le ciel ; les réglages sur le sol, qui défile. */
@Composable
fun GabaritReglages(
    titre: String,
    pastille: Color?,
    sousTitre: String,
    retour: () -> Unit,
    pied: (@Composable () -> Unit)? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val b = LocalBanco.current
    Box(
        Modifier
            .fillMaxSize()
            .background(b.ciel)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Paysage(astre = Astre(322f, 54f, 26f), collines = listOf(176f))
        Column(Modifier.statusBarsPadding().padding(start = 12.dp, end = 24.dp, top = 4.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Revenir", role = Role.Button, onClick = retour)
                    .semantics { contentDescription = "Revenir" },
                contentAlignment = Alignment.Center,
            ) { IconeTrait(Icones.RETOUR, 24.dp, b.encre) }
            Row(Modifier.padding(start = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pastille != null) {
                    Box(Modifier.size(12.dp).background(pastille, CircleShape))
                    Spacer(Modifier.width(12.dp))
                }
                BasicText(
                    titre,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = Polices.monument,
                        fontWeight = FontWeight(600),
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        letterSpacing = (-0.02).em,
                        color = b.encre,
                    ),
                )
            }
            BasicText(
                sousTitre,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 150.dp)) {
            Crete()
            Column(
                Modifier
                    .fillMaxSize()
                    .background(b.sol)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (pied != null) 24.dp else 0.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 24.dp),
                    content = contenu,
                )
                if (pied != null) Box(Modifier.padding(horizontal = 12.dp)) { pied() }
            }
        }
    }
}

/** Les six couleurs d'Espace, la choisie cerclée d'encre. */
@Composable
fun ChoixCouleur(teinte: CouleurEspace, choisir: (CouleurEspace) -> Unit) {
    val b = LocalBanco.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CouleurEspace.entries.forEach { c ->
            val choisie = c == teinte
            Box(
                Modifier
                    .size(36.dp)
                    .then(if (choisie) Modifier.border(2.dp, b.encre, CircleShape).padding(5.dp) else Modifier)
                    .clip(CircleShape)
                    .background(c.couleur(b))
                    .clickable(onClickLabel = c.nom, role = Role.RadioButton) { choisir(c) }
                    .semantics {
                        contentDescription = c.nom
                        selected = choisie
                    },
            )
        }
    }
}

/** Les trois paysages en vignettes. */
@Composable
fun ChoixPaysage(paysage: PaysageEspace, choisir: (PaysageEspace) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PaysageEspace.entries.forEach { p -> MiniPaysage(p, choisi = p == paysage) { choisir(p) } }
    }
}

@Composable
fun ChampNom(valeur: String, valider: () -> Unit = {}, changer: (String) -> Unit) {
    val b = LocalBanco.current
    val style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre)
    BasicTextField(
        value = valeur,
        onValueChange = { changer(it.take(30)) },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(b.laterite),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { valider() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { champ ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(b.sol2, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (valeur.isEmpty()) BasicText("Nom de l'Espace", style = style.copy(color = b.encre2))
                champ()
            }
        },
    )
}

@Composable
fun Rubrique(texte: String) {
    val b = LocalBanco.current
    BasicText(
        texte,
        modifier = Modifier.padding(bottom = 10.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
    )
}

/** Un paysage en vignette, dans les couleurs qu'il aura le jour. */
@Composable
private fun MiniPaysage(p: PaysageEspace, choisi: Boolean, choisir: () -> Unit) {
    val b = LocalBanco.current
    val c = palette(p, nuit = false)
    val forme = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = p.nom, role = Role.RadioButton, onClick = choisir)
            .semantics { selected = choisi },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .padding(3.dp)
                .size(62.dp, 93.dp)
                .then(if (choisi) Modifier.border(3.dp, b.laterite, forme) else Modifier.border(1.dp, b.sol2, forme))
                .clip(forme),
        ) {
            val sx = size.width / 100f
            val sy = size.height / 150f
            drawRect(c.ciel)
            drawCircle(c.astre, 14 * sx, Offset(72 * sx, 44 * sy))
            listOf(78f to c.colline1, 98f to c.colline2, 118f to c.colline3, 136f to c.colline4).forEach { (y, couleur) ->
                drawPath(
                    Path().apply {
                        moveTo(0f, y * sy)
                        cubicTo(25 * sx, (y - 8) * sy, 50 * sx, (y - 4) * sy, 70 * sx, y * sy)
                        cubicTo(90 * sx, (y + 4) * sy, 92 * sx, (y - 6) * sy, 100 * sx, (y - 2) * sy)
                        lineTo(100 * sx, 150 * sy)
                        lineTo(0f, 150 * sy)
                        close()
                    },
                    couleur,
                )
            }
        }
        BasicText(
            p.nom,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = b.encre),
        )
    }
}

@Composable
fun Paragraphe(texte: String, couleur: Color? = null) {
    val b = LocalBanco.current
    BasicText(
        texte,
        modifier = Modifier.padding(bottom = 14.dp),
        style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = couleur ?: b.encre),
    )
}

/** Le bouton Avancer de Banco : un rond de latérite et une flèche. */
@Composable
fun Avancer(actif: Boolean, description: String, onClick: () -> Unit) =
    BoutonRond(LocalBanco.current.laterite, Icones.AVANCER, actif, description, onClick)

/** Un rond d'action de 64 dp : latérite pour avancer, danger pour ce qu'on ne peut pas défaire. */
@Composable
fun BoutonRond(fond: Color, icone: String, actif: Boolean, description: String, onClick: () -> Unit) {
    val b = LocalBanco.current
    Box(
        Modifier
            .size(64.dp)
            .graphicsLayer { alpha = if (actif) 1f else 0.4f }
            .clip(CircleShape)
            .background(fond)
            .clickable(enabled = actif, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        IconeTrait(icone, 28.dp, b.surLaterite, epaisseur = 2f)
    }
}

@Composable
fun RangeeChoix(titre: String, detail: String, choisie: Boolean, choisir: () -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .deborder(12.dp)
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (choisie) Modifier.background(b.sol2) else Modifier)
            .clickable(onClickLabel = titre, role = Role.RadioButton, onClick = choisir)
            .semantics { selected = choisie }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre))
            BasicText(detail, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = b.encre2))
        }
        Pastille(choisie)
    }
}

/** La pastille de Banco : un rond cerclé, plein de latérite avec un point quand il est choisi. */
@Composable
fun Pastille(choisie: Boolean) {
    val b = LocalBanco.current
    Box(
        Modifier
            .size(24.dp)
            .then(if (choisie) Modifier.background(b.laterite, CircleShape) else Modifier.border(1.75.dp, b.encre2, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (choisie) Box(Modifier.size(8.dp).background(b.surLaterite, CircleShape))
    }
}

/** La case de Banco : un carré arrondi, plein de latérite et coché quand on la choisit. */
@Composable
fun Case(cochee: Boolean) {
    val b = LocalBanco.current
    val forme = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .size(24.dp)
            .then(if (cochee) Modifier.background(b.laterite, forme) else Modifier.border(1.75.dp, b.encre2, forme)),
        contentAlignment = Alignment.Center,
    ) {
        if (cochee) IconeTrait(Icones.COCHE, 18.dp, b.surLaterite, epaisseur = 2.25f)
    }
}

/** Une puce de Banco : un galet de texte, avec une icône s'il le faut. */
@Composable
fun Puce(texte: String, icone: String? = null, choisie: Boolean = false, onClick: () -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (choisie) b.laterite else b.sol)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val encre = if (choisie) b.surLaterite else b.encre
        if (icone != null) {
            IconeTrait(icone, 18.dp, encre)
            Spacer(Modifier.width(6.dp))
        }
        BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = encre))
    }
}

/** L'interrupteur de Banco : un galet qui passe à la latérite quand il est allumé. */
@Composable
fun Interrupteur(allume: Boolean, actif: Boolean = true, basculer: (() -> Unit)? = null) {
    val b = LocalBanco.current
    val decalage by androidx.compose.animation.core.animateDpAsState(if (allume) 20.dp else 0.dp, label = "interrupteur")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .graphicsLayer { alpha = if (actif) 1f else 0.4f }
            .clip(RoundedCornerShape(50))
            .background(if (allume) b.laterite else b.interFond)
            .then(if (basculer != null) Modifier.clickable(enabled = actif, role = Role.Switch, onClick = basculer) else Modifier)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = decalage)
                .size(24.dp)
                .background(if (allume) b.surLaterite else b.interBouton, CircleShape),
        )
    }
}
