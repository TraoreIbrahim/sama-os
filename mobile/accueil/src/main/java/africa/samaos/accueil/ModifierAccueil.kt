package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/** Où l'on est dans « Modifier l'Accueil ». */
enum class VueModifier { Principal, Paysage, Widgets, Reglages }

/**
 * « Modifier l'Accueil » (maquette l1-modifier-accueil), ouvert par un appui long sur le paysage :
 * l'Accueil en petit, cerclé de latérite, et dessous Paysage, Widgets, Réglages.
 */
@Composable
fun ModifierAccueil(
    vue: VueModifier,
    espaces: Espaces,
    disposition: Disposition,
    elements: List<Element>,
    applis: Map<String, Appli>,
    natte: List<Appli>,
    changer: (VueModifier?) -> Unit,
) {
    BackHandler { changer(if (vue == VueModifier.Principal) null else VueModifier.Principal) }
    if (vue == VueModifier.Widgets) {
        ChoixWidgets(disposition, retour = { changer(VueModifier.Principal) }, fini = { changer(null) })
        return
    }
    val b = LocalBanco.current
    Column(
        Modifier
            .fillMaxSize()
            .background(b.sol2)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            MiniAccueil(espaces.actif, disposition.widgets.size, elements, applis, natte) { changer(null) }
        }
        Column(Modifier.fillMaxWidth()) {
            Crete()
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(b.sol)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (vue) {
                    VueModifier.Paysage -> {
                        BasicText(
                            "Paysage de ${espaces.actif.nom}",
                            modifier = Modifier.padding(start = 8.dp),
                            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
                        )
                        Box(Modifier.padding(start = 4.dp)) {
                            ChoixPaysage(espaces.actif.paysage) { p -> espaces.apparence(espaces.actif, espaces.actif.teinte, p) }
                        }
                        PiedModifier("Terminé") { changer(VueModifier.Principal) }
                    }
                    VueModifier.Reglages -> {
                        BasicText(
                            "Réglages de l'Accueil",
                            modifier = Modifier.padding(start = 8.dp),
                            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
                        )
                        RangeeSimple(
                            "Rétablir l'Accueil",
                            "Remet les applis de départ, sans dossier. Les widgets restent.",
                            actif = disposition.rangees != null,
                        ) {
                            disposition.retablir()
                            changer(VueModifier.Principal)
                        }
                        Note("Pour ranger, tenez une appli puis glissez-la. Lâchée sur une autre, elle fait un dossier.")
                        PiedModifier("Terminé") { changer(VueModifier.Principal) }
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GrandBouton(Icones.PAYSAGE, "Paysage", Modifier.weight(1f)) { changer(VueModifier.Paysage) }
                        GrandBouton(Icones.WIDGETS, "Widgets", Modifier.weight(1f)) { changer(VueModifier.Widgets) }
                        GrandBouton(Icones.REGLAGES, "Réglages", Modifier.weight(1f)) { changer(VueModifier.Reglages) }
                    }
                }
            }
        }
    }
}

/** L'Accueil en petit : son paysage, l'heure, la place des widgets, les applis et la Natte. */
@Composable
private fun MiniAccueil(espace: Espace, widgets: Int, elements: List<Element>, applis: Map<String, Appli>, natte: List<Appli>, fermer: () -> Unit) {
    CompositionLocalProvider(LocalBanco provides palette(espace.paysage, LocalNuit.current)) {
        val b = LocalBanco.current
        val maintenant = rememberMaintenant()
        BoxWithConstraints(
            Modifier
                .fillMaxHeight()
                .aspectRatio(266f / 556f)
                .shadow(12.dp, RoundedCornerShape(30.dp))
                .border(2.dp, b.laterite, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .clickable(onClickLabel = "Revenir à l'Accueil", role = Role.Button, onClick = fermer),
        ) {
            val e = maxWidth.value / 266f
            Paysage()
            BasicText(
                maintenant.format(DateTimeFormatter.ofPattern("HH:mm")),
                modifier = Modifier.padding(start = (16 * e).dp, top = (44 * e).dp),
                style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(200), fontSize = (76 * e).sp, lineHeight = (76 * e).sp, color = b.encre),
            )
            // La place des widgets : pointillée tant qu'il n'y en a pas.
            Box(
                Modifier
                    .padding(start = (12 * e).dp, end = (12 * e).dp, top = (186 * e).dp)
                    .fillMaxWidth()
                    .height((70 * e).dp),
                contentAlignment = Alignment.Center,
            ) {
                if (widgets == 0) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            b.encre2, cornerRadius = CornerRadius(16.dp.toPx() * e),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        )
                    }
                    BasicText("Déposer un widget ici", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = b.encre2))
                } else {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape((16 * e).dp)).background(b.voile), contentAlignment = Alignment.Center) {
                        BasicText(
                            if (widgets == 1) "1 widget" else "$widgets widgets",
                            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = b.encre2),
                        )
                    }
                }
            }
            Column(
                Modifier
                    .padding(start = (10 * e).dp, end = (10 * e).dp, top = (340 * e).dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy((14 * e).dp),
            ) {
                elements.take(Disposition.MAXIMUM).chunked(4).forEach { rangee ->
                    Row(Modifier.fillMaxWidth()) {
                        rangee.forEach { el ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                when (el) {
                                    is ElemAppli -> applis[el.cle]?.let { Image(it.icone, null, Modifier.size((40 * e).dp)) }
                                    is ElemDossier -> Box(
                                        Modifier.size((40 * e).dp).clip(RoundedCornerShape((12 * e).dp)).background(b.voile),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy((2 * e).dp)) {
                                            el.applis.mapNotNull { applis[it] }.take(4).chunked(2).forEach { r ->
                                                Row(horizontalArrangement = Arrangement.spacedBy((2 * e).dp)) {
                                                    r.forEach { a -> Image(a.icone, null, Modifier.size((15 * e).dp)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        repeat(4 - rangee.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = (10 * e).dp, end = (10 * e).dp, bottom = (18 * e).dp)
                    .fillMaxWidth()
                    .height((52 * e).dp)
                    .clip(RoundedCornerShape(50))
                    .background(b.sol)
                    .padding(horizontal = (10 * e).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.size((34 * e).dp).clip(RoundedCornerShape(50)).background(b.laterite))
                natte.take(4).forEach { Image(it.icone, null, Modifier.size((32 * e).dp)) }
            }
        }
    }
}

@Composable
private fun GrandBouton(icone: String, nom: String, mod: Modifier, onClick: () -> Unit) {
    val b = LocalBanco.current
    Column(
        mod
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(b.sol2)
            .clickable(onClickLabel = nom, role = Role.Button, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        IconeTrait(icone, 26.dp, b.encre)
        BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre, textAlign = TextAlign.Center))
    }
}

@Composable
private fun RangeeSimple(titre: String, detail: String, actif: Boolean, onClick: () -> Unit) {
    val b = LocalBanco.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = actif, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics { if (!actif) contentDescription = "$titre, déjà fait" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = if (actif) b.encre else b.encre2))
        BasicText(detail, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2))
    }
}

@Composable
private fun PiedModifier(action: String, onClick: () -> Unit) {
    val b = LocalBanco.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        BasicText(
            action,
            modifier = Modifier.padding(end = 14.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.encre),
        )
        BoutonRond(b.laterite, Icones.COCHE, actif = true, description = action, onClick = onClick)
    }
}

/** Un widget proposé : son aperçu, son nom, son appli et sa taille. */
private class Propose(val info: AppWidgetProviderInfo, val nom: String, val appli: String, val taille: String, val apercu: ImageBitmap?)

/**
 * Les widgets des applis du téléphone (maquette l1-widgets) : on en touche un pour le poser sous l'heure.
 * Au-delà de trois rangées, il n'y a plus de place : on retire d'abord un widget (appui long dessus).
 */
@Composable
private fun ChoixWidgets(disposition: Disposition, retour: () -> Unit, fini: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    var liste by remember { mutableStateOf<List<Propose>?>(null) }
    LaunchedEffect(Unit) {
        liste = withContext(Dispatchers.IO) {
            val pm = contexte.packageManager
            val densite = contexte.resources.displayMetrics.densityDpi
            Widgets.disponibles(contexte).map { info ->
                val appli = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    ""
                }
                val image = try {
                    (info.loadPreviewImage(contexte, densite) ?: info.loadIcon(contexte, densite))?.let { d ->
                        val l = d.intrinsicWidth.coerceIn(1, 600)
                        val h = d.intrinsicHeight.coerceIn(1, 600)
                        d.toBitmap(l, h).asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
                Propose(info, info.loadLabel(pm), appli, Widgets.taille(info, contexte), image)
            }.sortedWith(compareBy({ it.appli }, { it.nom }))
        }
    }
    val place = Widgets.RANGEES_MAX - disposition.widgets.sumOf { it.rangees }
    var refus by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(b.ciel).pointerInput(Unit) { detectTapGestures { } }) {
        Paysage()
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 96.dp)) {
            Crete()
            Column(Modifier.fillMaxSize().background(b.sol).padding(top = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        "Widgets",
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontFamily = Polices.monument, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em, color = b.encre),
                    )
                    BasicText(
                        "Touchez pour poser",
                        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = b.encre2),
                    )
                }
                if (place <= 0 || refus) {
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Note(
                            if (refus) "Ce widget n'a pas pu être posé." else "Plus de place sous l'heure : tenez un widget de l'Accueil pour le retirer d'abord.",
                            b.lateriteTexte,
                        )
                    }
                }
                val l = liste
                if (l != null && l.isEmpty()) {
                    Box(Modifier.padding(24.dp)) { Note("Aucune appli de ce téléphone ne propose de widget.") }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 16.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(l.orEmpty(), key = { it.info.provider.flattenToString() }) { p ->
                        val possible = Widgets.rangees(contexte, p.info) <= place
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .combinedClickable(
                                    enabled = possible,
                                    onClickLabel = "Poser ${p.nom}",
                                    role = Role.Button,
                                    onLongClick = { if (!Widgets.poser(contexte as Activity, p.info) { disposition.poserWidget(it) }) refus = true else fini() },
                                    onClick = { if (!Widgets.poser(contexte as Activity, p.info) { disposition.poserWidget(it) }) refus = true else fini() },
                                )
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(b.sol2)
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                p.apercu?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                            }
                            BasicText(
                                p.nom, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (possible) b.encre else b.encre2),
                            )
                            BasicText(
                                "${p.appli} · ${p.taille}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp),
                                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = b.encre2),
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 4.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClickLabel = "Revenir", role = Role.Button, onClick = retour)
                .semantics { contentDescription = "Revenir" },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(Icones.RETOUR, 24.dp, b.encre) }
    }
}

/** Le menu d'un widget tenu : le retirer de l'Accueil. */
@Composable
fun MenuWidget(retirer: () -> Unit, fermer: () -> Unit) {
    val b = LocalBanco.current
    BackHandler(onBack = fermer)
    Box(
        Modifier
            .fillMaxSize()
            .background(b.voile)
            .pointerInput(Unit) { detectTapGestures { fermer() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(b.sol)
                .padding(10.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) {
                        retirer()
                        fermer()
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconeTrait(Icones.MOINS, 22.dp, b.danger)
                BasicText("Retirer le widget de l'Accueil", style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.danger))
            }
        }
    }
}
