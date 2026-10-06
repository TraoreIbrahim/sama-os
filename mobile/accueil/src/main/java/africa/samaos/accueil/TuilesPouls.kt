package africa.samaos.accueil

import africa.samaos.banco.*
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Les tuiles que le Pouls sait montrer. */
enum class TuileId(val nom: String, val icone: String) {
    WIFI("Wi-Fi", Icones.WIFI),
    DONNEES("Données", Icones.DONNEES),
    ECONOMIE("Économie", Icones.ECONOMIE),
    LAMPE("Lampe", Icones.LAMPE),
    NUIT("Nuit", Icones.NUIT),
    ESPACES("Espaces", Icones.CADENAS),
    AVION("Mode avion", Icones.AVION),
    POINT_ACCES("Point d'accès", Icones.POINT_ACCES),
    ECONOMISEUR("Économiseur", Icones.ECONOMISEUR),
    NE_PAS_DERANGER("Ne pas déranger", Icones.NE_PAS_DERANGER),
    ROTATION("Rotation auto", Icones.ROTATION),
}

/** Les tuiles du Pouls et leur ordre, propres à chaque Espace (chaque profil a ses réglages). */
class TuilesPouls(contexte: Context) {
    private val prefs = reglages(contexte, "sama")

    var liste by mutableStateOf(lire())
        private set

    fun changer(nouvelle: List<TuileId>) {
        liste = nouvelle
        prefs.edit().putString(CLE, nouvelle.joinToString(",") { it.name }).apply()
    }

    fun retablir() = changer(PAR_DEFAUT)

    private fun lire(): List<TuileId> =
        prefs.getString(CLE, null)
            ?.split(",")
            ?.mapNotNull { n -> TuileId.entries.firstOrNull { it.name == n } }
            ?.distinct()
            ?: PAR_DEFAUT

    companion object {
        private const val CLE = "tuiles_pouls"
        val PAR_DEFAUT = listOf(TuileId.WIFI, TuileId.DONNEES, TuileId.ECONOMIE, TuileId.LAMPE, TuileId.NUIT, TuileId.ESPACES)

        /** Le Pouls garde toujours une rangée : c'est par un appui long sur une tuile qu'on revient ici. */
        const val MINIMUM = 2
    }
}

/** Une tuile telle qu'elle s'affiche : son état du moment et ce qu'elle fait. */
class Tuile(
    val id: TuileId,
    val etat: String,
    val active: Boolean,
    val disponible: Boolean = true,
    val action: () -> Unit,
)

/** Construit les tuiles demandées avec l'état réel du téléphone. */
@Composable
fun tuilesDuPouls(ids: List<TuileId>, etat: EtatPouls, nuit: ReglageNuit, espaces: Espaces, actionEspaces: () -> Unit): List<Tuile> {
    val contexte = LocalContext.current
    val lampe = rememberLampe()
    fun activee(oui: Boolean, feminin: Boolean = true) = if (oui) (if (feminin) "Activée" else "Activé") else (if (feminin) "Désactivée" else "Désactivé")
    return ids.map { id ->
        when (id) {
            TuileId.WIFI -> Tuile(
                id,
                when {
                    etat.wifiConnecte -> "Connecté"
                    etat.wifiActive -> "Pas de réseau"
                    else -> "Désactivé"
                },
                etat.wifiActive,
            ) { Systeme.panneauWifi(contexte) }
            TuileId.DONNEES -> Tuile(id, if (etat.donnees) etat.operateur.ifBlank { "Activées" } else "Désactivées", etat.donnees) {
                Systeme.panneauDonnees(contexte)
            }
            TuileId.ECONOMIE -> Tuile(id, activee(etat.economie), etat.economie) { Systeme.reglagesEconomie(contexte) }
            TuileId.LAMPE -> Tuile(
                id,
                when (lampe.allumee) {
                    null -> "Absente"
                    true -> "Allumée"
                    false -> "Éteinte"
                },
                lampe.allumee == true,
                disponible = lampe.allumee != null,
            ) { lampe.basculer() }
            TuileId.NUIT -> Tuile(
                id,
                when (nuit.mode) {
                    ModeNuit.AUTO -> "Au coucher"
                    ModeNuit.NUIT -> "Activée"
                    ModeNuit.JOUR -> "Désactivée"
                },
                nuit.active,
            ) { nuit.basculer() }
            // À la Maison, la tuile montre les Espaces ; ailleurs, elle les verrouille tous et ramène à la Maison.
            TuileId.ESPACES -> Tuile(id, if (espaces.actif.estMaison) espaces.actif.nom else "Verrouiller tout", false, action = actionEspaces)
            TuileId.AVION -> Tuile(id, activee(etat.avion, feminin = false), etat.avion) { Systeme.reglageAvion(contexte) }
            TuileId.POINT_ACCES -> Tuile(id, activee(etat.pointAcces, feminin = false), etat.pointAcces) {
                Systeme.basculerPointAcces(contexte, etat.pointAcces)
            }
            TuileId.ECONOMISEUR -> Tuile(id, activee(etat.economiseur, feminin = false), etat.economiseur) {
                Systeme.basculerEconomiseur(contexte)
            }
            TuileId.NE_PAS_DERANGER -> Tuile(id, activee(etat.nePasDeranger, feminin = false), etat.nePasDeranger) {
                Systeme.basculerNePasDeranger(contexte)
            }
            TuileId.ROTATION -> Tuile(id, activee(etat.rotation), etat.rotation) { Systeme.basculerRotation(contexte) }
        }
    }
}

/** Les tuiles du Pouls, deux par rangée ; un appui long sur l'une d'elles ouvre « Modifier le Pouls ». */
@Composable
fun Tuiles(tuiles: List<Tuile>, modifier: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tuiles.chunked(2).forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rangee.forEach { TuileVue(it, modifier, Modifier.weight(1f)) }
                if (rangee.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TuileVue(t: Tuile, modifier: () -> Unit, mod: Modifier) {
    val b = LocalBanco.current
    val encre = if (t.active) b.surLaterite else b.encre
    val encre2 = if (t.active) b.surLaterite else b.encre2
    Row(
        mod
            .height(76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (t.active) b.laterite else b.sol2)
            .combinedClickable(
                onClickLabel = t.id.nom,
                role = Role.Switch,
                onLongClickLabel = "Modifier le Pouls",
                onLongClick = modifier,
                onClick = { if (t.disponible) t.action() },
            )
            .semantics { stateDescription = t.etat }
            .graphicsLayer { alpha = if (t.disponible) 1f else 0.55f }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTrait(t.id.icone, 22.dp, encre)
        Spacer(Modifier.width(12.dp))
        Column {
            BasicText(
                t.id.nom, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = encre),
            )
            BasicText(
                t.etat, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = encre2),
            )
        }
    }
}

/**
 * « Modifier le Pouls » (maquette l1-pouls-tuiles) : les tuiles en place, qu'on range en les tenant
 * puis en les glissant et qu'on retire d'un « − » ; dessous, celles qu'on peut ajouter d'un « + ».
 * Chaque changement est gardé aussitôt ; « Rétablir » remet les six tuiles de départ.
 */
@Composable
fun ModifierPouls(tuiles: TuilesPouls, nuit: ReglageNuit, espaces: Espaces, fermer: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    BackHandler(onBack = fermer)
    var etat by remember { mutableStateOf(EtatPouls()) }
    LaunchedEffect(Unit) { etat = withContext(Dispatchers.IO) { Systeme.lire(contexte) } }
    val actives = tuilesDuPouls(TuileId.entries, etat, nuit, espaces) {}.associateBy { it.id }
    val enPlace = tuiles.liste
    val aAjouter = TuileId.entries.filter { it !in enPlace }
    val peutRetirer = enPlace.size > TuilesPouls.MINIMUM

    Column(
        Modifier
            .fillMaxSize()
            .background(b.sol)
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    "Modifier le Pouls",
                    style = TextStyle(
                        fontFamily = Polices.monument, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 34.sp,
                        letterSpacing = (-0.01).em, color = b.encre,
                    ),
                )
                BasicText(
                    if (peutRetirer) "Glissez une tuile pour la ranger." else "Glissez une tuile pour la ranger. Le Pouls en garde au moins deux.",
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
                )
            }
            GrilleRangee(enPlace, tuiles::changer) { id, i ->
                TuileEnPlace(
                    id = id,
                    active = actives[id]?.active == true,
                    retirer = if (peutRetirer) ({ tuiles.changer(enPlace - id) }) else null,
                    ranger = { vers -> tuiles.changer(enPlace.toMutableList().apply { add(vers, removeAt(i)) }) },
                    position = i,
                    nombre = enPlace.size,
                )
            }
            if (aAjouter.isNotEmpty()) {
                BasicText(
                    "Ajouter une tuile",
                    modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = b.encre2),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    aAjouter.chunked(2).forEach { rangee ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rangee.forEach { id -> TuileAAjouter(id, Modifier.weight(1f)) { tuiles.changer(enPlace + id) } }
                            if (rangee.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "Rétablir",
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enPlace != TuilesPouls.PAR_DEFAUT, role = Role.Button, onClick = tuiles::retablir)
                    .graphicsLayer { alpha = if (enPlace != TuilesPouls.PAR_DEFAUT) 1f else 0.4f }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre2),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                "Terminé",
                modifier = Modifier.padding(end = 14.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.encre),
            )
            BoutonRond(b.laterite, Icones.COCHE, actif = true, description = "Terminé", onClick = fermer)
        }
    }
}

/**
 * Une grille de deux colonnes qu'on range au doigt : on tient une tuile, elle se soulève et suit le doigt,
 * les autres s'écartent pour lui faire place.
 */
@Composable
private fun GrilleRangee(ids: List<TuileId>, changer: (List<TuileId>) -> Unit, tuile: @Composable (TuileId, Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val densite = LocalDensity.current
        val ecart = 8.dp
        val hauteur = 76.dp
        val largeur = (maxWidth - ecart) / 2
        val largeurPx = with(densite) { largeur.toPx() }
        val hauteurPx = with(densite) { hauteur.toPx() }
        val ecartPx = with(densite) { ecart.toPx() }
        fun place(i: Int) = Offset((i % 2) * (largeurPx + ecartPx), (i / 2) * (hauteurPx + ecartPx))
        fun caseSous(centre: Offset, nombre: Int): Int {
            val colonne = if (centre.x > largeurPx + ecartPx / 2) 1 else 0
            val rangee = (centre.y / (hauteurPx + ecartPx)).toInt().coerceAtLeast(0)
            return (rangee * 2 + colonne).coerceIn(0, nombre - 1)
        }
        val idsActuels by rememberUpdatedState(ids)
        val changerActuel by rememberUpdatedState(changer)
        val portee = rememberCoroutineScope()
        var tenu by remember { mutableStateOf<TuileId?>(null) }
        var position by remember { mutableStateOf(Offset.Zero) }
        val rangees = (ids.size + 1) / 2

        Box(Modifier.fillMaxWidth().height(hauteur * rangees + ecart * (rangees - 1).coerceAtLeast(0))) {
            ids.forEachIndexed { i, id ->
                key(id) {
                    val cible = place(i)
                    val glisse = remember { Animatable(cible, Offset.VectorConverter) }
                    LaunchedEffect(cible) { if (tenu != id) glisse.animateTo(cible) }
                    val estTenu = tenu == id
                    Box(
                        Modifier
                            .offset { (if (estTenu) position else glisse.value).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                            .zIndex(if (estTenu) 1f else 0f)
                            .size(largeur, hauteur)
                            .graphicsLayer {
                                val echelle = if (estTenu) 1.05f else 1f
                                scaleX = echelle
                                scaleY = echelle
                            }
                            .pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        position = place(idsActuels.indexOf(id))
                                        tenu = id
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        position += delta
                                        val depuis = idsActuels.indexOf(id)
                                        val vers = caseSous(position + Offset(largeurPx / 2, hauteurPx / 2), idsActuels.size)
                                        if (depuis >= 0 && vers != depuis) {
                                            changerActuel(idsActuels.toMutableList().apply { add(vers, removeAt(depuis)) })
                                        }
                                    },
                                    onDragEnd = {
                                        portee.launch {
                                            glisse.snapTo(position)
                                            tenu = null
                                            glisse.animateTo(place(idsActuels.indexOf(id)))
                                        }
                                    },
                                    onDragCancel = {
                                        portee.launch {
                                            glisse.snapTo(position)
                                            tenu = null
                                            glisse.animateTo(place(idsActuels.indexOf(id)))
                                        }
                                    },
                                )
                            },
                    ) { tuile(id, i) }
                }
            }
        }
    }
}

/** Une tuile en place : son nom, et le « − » qui la retire. Ranger se fait aussi au lecteur d'écran. */
@Composable
private fun TuileEnPlace(id: TuileId, active: Boolean, retirer: (() -> Unit)?, ranger: (Int) -> Unit, position: Int, nombre: Int) {
    val b = LocalBanco.current
    val encre = if (active) b.surLaterite else b.encre
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(if (active) b.laterite else b.sol2)
                .semantics {
                    contentDescription = id.nom
                    customActions = listOfNotNull(
                        if (position > 0) CustomAccessibilityAction("Ranger avant") { ranger(position - 1); true } else null,
                        if (position < nombre - 1) CustomAccessibilityAction("Ranger après") { ranger(position + 1); true } else null,
                    )
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconeTrait(id.icone, 22.dp, encre)
            Spacer(Modifier.width(12.dp))
            BasicText(
                id.nom, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = encre),
            )
        }
        if (retirer != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Retirer ${id.nom}", role = Role.Button, onClick = retirer)
                    .semantics { contentDescription = "Retirer ${id.nom}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).background(b.encre, CircleShape), contentAlignment = Alignment.Center) {
                    IconeTrait(Icones.MOINS, 14.dp, b.sol, epaisseur = 2.6f)
                }
            }
        }
    }
}

/** Une tuile qu'on peut ajouter : son nom, et un « + » de latérite au coin, en miroir du « − » des tuiles en place. */
@Composable
private fun TuileAAjouter(id: TuileId, mod: Modifier, ajouter: () -> Unit) {
    val b = LocalBanco.current
    Box(mod.height(76.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(b.sol2)
                .clickable(onClickLabel = "Ajouter ${id.nom}", role = Role.Button, onClick = ajouter)
                .semantics { contentDescription = "Ajouter ${id.nom}" }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconeTrait(id.icone, 22.dp, b.encre)
            Spacer(Modifier.width(12.dp))
            BasicText(
                id.nom, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = b.encre),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-8).dp)
                .size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(26.dp).background(b.laterite, CircleShape), contentAlignment = Alignment.Center) {
                IconeTrait(Icones.PLUS, 14.dp, b.surLaterite, epaisseur = 2.6f)
            }
        }
    }
}

/** La lampe torche : une appli peut l'allumer sans demander l'accès à l'appareil photo. */
private class EtatLampe(val allumee: Boolean?, val basculer: () -> Unit)

@Composable
private fun rememberLampe(): EtatLampe {
    val contexte = LocalContext.current
    val cm = remember { contexte.getSystemService(CameraManager::class.java) }
    var id by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { id = withContext(Dispatchers.IO) { Systeme.cameraAvecLampe(contexte) } }
    var allumee by remember { mutableStateOf(false) }
    DisposableEffect(id) {
        val camera = id
        val rappel = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == camera) allumee = enabled
            }
        }
        if (camera != null) cm?.registerTorchCallback(rappel, Handler(Looper.getMainLooper()))
        onDispose { cm?.unregisterTorchCallback(rappel) }
    }
    val camera = id
    return EtatLampe(if (camera == null) null else allumee) {
        try {
            if (camera != null) cm?.setTorchMode(camera, !allumee)
        } catch (_: Exception) {
            // La caméra est prise par une autre appli.
        }
    }
}
