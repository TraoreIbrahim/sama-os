package africa.samaos.accueil

import android.content.Intent
import africa.samaos.banco.*
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Le réglage Nuit de l'Accueil, tel que la tuile du Pouls le montre et le change. */
class ReglageNuit(val active: Boolean, val mode: ModeNuit, val basculer: () -> Unit)

enum class ModeNuit { AUTO, NUIT, JOUR }

/**
 * Le Pouls : il pend du haut de l'écran comme un sol retourné, la crête en bas.
 * L'heure, les tuiles choisies, la luminosité, puis les notifications. Il descend sous le doigt
 * et remonte de la même façon, ou d'une touche sous la crête.
 */
@Composable
fun Pouls(
    panneau: Panneau,
    applis: List<Appli>,
    nuit: ReglageNuit,
    espaces: Espaces,
    tuiles: TuilesPouls,
    ouvrirEspaces: () -> Unit,
    modifierTuiles: () -> Unit,
) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    var etat by remember { mutableStateOf(EtatPouls()) }
    val lecture = rememberLecture()
    val sorties = rememberSorties(lecture?.paquet)
    var ecouterSur by remember { mutableStateOf(false) }
    if (!panneau.visible || lecture == null) ecouterSur = false
    // Relu en arrière-plan tant que le Pouls est à l'écran.
    LaunchedEffect(panneau.visible) {
        while (panneau.visible) {
            etat = withContext(Dispatchers.IO) { Systeme.lire(contexte) }
            delay(1500)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(if (panneau.visible) Modifier.pointerInput(Unit) { detectTapGestures { panneau.fermer() } } else Modifier)
            .then(panneau.gestes()),
    ) {
        val haut = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // On laisse toujours une bande de paysage sous la crête, pour voir où l'on est et refermer d'une touche.
        val hauteurListe = maxHeight - haut - 132.dp - 40.dp - 120.dp
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { panneau.course = it.height.toFloat() }
                .graphicsLayer { translationY = -(1f - panneau.progres) * size.height }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(b.sol)
                    .statusBarsPadding()
                    .padding(top = 6.dp, bottom = 14.dp),
            ) {
                EnTete()
                Column(
                    Modifier
                        .padding(top = 20.dp)
                        .heightIn(max = hauteurListe)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (lecture != null) CarteLecture(lecture, sorties?.nomCourt ?: "Ce téléphone") { ecouterSur = true }
                    Tuiles(
                        tuilesDuPouls(tuiles.liste, etat, nuit, espaces) {
                            panneau.fermer()
                            if (espaces.actif.estMaison) ouvrirEspaces() else espaces.verrouillerTout()
                        },
                    ) {
                        panneau.fermer()
                        modifierTuiles()
                    }
                    Curseur(etat.luminosite) { etat = etat.copy(luminosite = it) }
                    CarteSoldes(panneau.visible) {
                        panneau.fermer()
                        Systeme.ouvrir(contexte, Intent("africa.samaos.action.SOLDES").setPackage("africa.samaos.reglages"))
                    }
                    ListeNotifications(applis)
                }
            }
            Crete(Modifier.graphicsLayer { scaleY = -1f })
        }
        if (ecouterSur && sorties != null) {
            Box(Modifier.align(Alignment.BottomCenter)) { FeuilleSorties(sorties) { ecouterSur = false } }
        }
    }
}

/** L'heure plus petite qu'à l'Accueil, la date, et le bouton des Réglages. */
@Composable
private fun EnTete() {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val maintenant = rememberMaintenant()
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            BasicText(
                text = maintenant.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TextStyle(
                    fontFamily = Polices.horloge,
                    fontWeight = FontWeight(200),
                    fontSize = 72.sp,
                    lineHeight = 72.sp,
                    letterSpacing = (-0.01).em,
                    color = b.encre,
                ),
            )
            BasicText(
                text = dateDuJour(maintenant),
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
            )
        }
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(b.sol2)
                .clickable(onClickLabel = "Ouvrir les Réglages", role = Role.Button) {
                    Systeme.ouvrir(contexte, Settings.ACTION_SETTINGS)
                }
                .semantics { contentDescription = "Réglages" },
            contentAlignment = Alignment.Center,
        ) {
            IconeTrait(Icones.REGLAGES, 20.dp, b.encre)
        }
    }
}

/** La luminosité : un galet que l'on remplit du doigt. */
@Composable
private fun Curseur(valeur: Float, changer: (Float) -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    var largeur by remember { mutableFloatStateOf(1f) }
    var demande by remember { mutableStateOf(false) }
    fun regler(x: Float) {
        if (!Systeme.peutReglerLuminosite(contexte)) {
            if (!demande) {
                demande = true
                Systeme.demanderReglageLuminosite(contexte)
            }
            return
        }
        val v = (x / largeur).coerceIn(0.02f, 1f)
        Systeme.reglerLuminosite(contexte, v)
        changer(v)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(b.sol2)
            .onSizeChanged { largeur = it.width.toFloat() }
            .pointerInput(Unit) { detectTapGestures { regler(it.x) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> regler(change.position.x) } }
            .semantics {
                contentDescription = "Luminosité"
                stateDescription = "${(valeur * 100).roundToInt()} %"
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(valeur.coerceIn(0.2f, 1f))
                .clip(RoundedCornerShape(50))
                .background(b.laterite)
                .padding(start = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            IconeTrait(Icones.SOLEIL, 22.dp, b.surLaterite)
        }
        BasicText(
            "${(valeur * 100).roundToInt()} %",
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
        )
    }
}

@Composable
fun IconePlate(chemin: String) {
    val b = LocalBanco.current
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(b.sol), contentAlignment = Alignment.Center) {
        IconeTrait(chemin, 20.dp, b.encre2)
    }
}

@Composable
fun CarteNotif(
    icone: @Composable () -> Unit,
    titre: String,
    legende: String?,
    texte: String,
    onClick: () -> Unit,
) {
    val b = LocalBanco.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(b.sol2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        icone()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                titre, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre),
            )
            if (legende != null) {
                BasicText(
                    legende, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = b.encre2),
                )
            }
            if (texte.isNotBlank()) {
                BasicText(
                    texte, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 22.sp, color = b.encre),
                )
            }
        }
    }
}

/** Une notification se balaie d'un côté ou de l'autre pour l'effacer. */
@Composable
fun Balayable(actif: Boolean, effacer: () -> Unit, contenu: @Composable () -> Unit) {
    var decalage by remember { mutableFloatStateOf(0f) }
    var largeur by remember { mutableFloatStateOf(1f) }
    Box(
        Modifier
            .onSizeChanged { largeur = it.width.toFloat() }
            .graphicsLayer {
                translationX = decalage
                alpha = 1f - (abs(decalage) / largeur).coerceIn(0f, 1f) * 0.8f
            }
            .draggable(
                state = rememberDraggableState { decalage += it },
                orientation = Orientation.Horizontal,
                enabled = actif,
                onDragStopped = { vx ->
                    val part = abs(decalage) > largeur * 0.4f || abs(vx) > 1800f
                    val cible = if (part) sign(if (decalage != 0f) decalage else vx) * largeur * 1.1f else 0f
                    animate(decalage, cible, vx) { v, _ -> decalage = v }
                    if (part) effacer()
                },
            ),
    ) { contenu() }
}

fun heureCourte(ms: Long): String {
    val moment = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val jour = moment.toLocalDate()
    val aujourdhui = LocalDate.now()
    return when (jour) {
        aujourdhui -> moment.format(DateTimeFormatter.ofPattern("HH:mm"))
        aujourdhui.minusDays(1) -> "hier"
        else -> moment.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
    }
}

