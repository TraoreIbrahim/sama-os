package africa.samaos.radio

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.BarreAvis
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.Glisse
import africa.samaos.banco.appli.GrandTitre
import africa.samaos.banco.appli.Groupe
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Rond
import africa.samaos.banco.appli.Section
import africa.samaos.banco.appli.Separateur
import africa.samaos.banco.appli.styleDoux
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ETOILE = "M12 4l2.4 5l5.4.6l-4 3.7l1.1 5.3L12 16l-4.9 2.6l1.1-5.3l-4-3.7l5.4-.6z"
private const val ANTENNE = "M12 20V9 M12 9l-6-5 M12 9l6-5 M7 20h10 M3.5 8.5a9 9 0 0 1 17 0"
private const val ECOUTEURS = "M4 14v-2a8 8 0 0 1 16 0v2 M4 14h3v6H4z M17 14h3v6h-3z"
private const val SABLIER = "M7 3h10 M7 21h10 M8 3c0 5 4 6 4 9s-4 4-4 9 M16 3c0 5-4 6-4 9s4 4 4 9"
private const val CHERCHER_BAS = "M18 6l-6 6l6 6 M11 6l-6 6l6 6"
private const val CHERCHER_HAUT = "M6 6l6 6l-6 6 M13 6l6 6l-6 6"
private const val ARRET = "M7 7h10v10H7z"

/** La Radio FM de Sama : la bande sur un cadran qu'on tourne du doigt, les stations gardées, la minuterie ; sans data. */
class Radio : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.RadioNuit else Identites.Radio
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) { Ecran() }
        }
    }
}

@Composable
private fun Ecran() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    val etat by Poste.etat.collectAsState()
    var stations by remember { mutableStateOf(Stations.liste(c)) }
    var minuterie by remember { mutableStateOf(false) }
    var renommer by remember { mutableStateOf<Station?>(null) }
    var balayage by remember { mutableStateOf(false) }
    // Le poste se présente dès l'ouverture, sans jouer : l'état vient du service, qui connaît la puce et les écouteurs.
    LaunchedEffect(Unit) { if (!etat.joue) Poste.commander(c, Poste.REGLER, Stations.derniere(c)) }
    val gardee = stations.firstOrNull { it.kHz == etat.kHz }
    val nomAffiche = gardee?.nom?.takeIf { it.isNotBlank() } ?: etat.nom

    fun balayer() {
        if (balayage) return
        balayage = true
        portee.launch {
            val s = PuceFm.syntoniseur(c)
            val trouvees = mutableListOf<Int>()
            var k = Bande.MIN
            var garde = 0
            while (garde++ < 60) {
                val n = s.chercher(k, montant = true) ?: break
                if (n <= k || n in trouvees) break
                trouvees += n
                k = n
            }
            val nouvelles = trouvees.filter { f -> stations.none { it.kHz == f } }
            nouvelles.forEach { f -> Stations.ajouter(c, Station(f, s.nom(f).orEmpty())) }
            stations = Stations.liste(c)
            balayage = false
            Avis.dire(
                when {
                    trouvees.isEmpty() -> "Aucune station captée. Branchez des écouteurs : ils servent d'antenne."
                    nouvelles.isEmpty() -> "${trouvees.size} station${if (trouvees.size > 1) "s" else ""} captée${if (trouvees.size > 1) "s" else ""}, déjà gardées"
                    else -> "${nouvelles.size} nouvelle${if (nouvelles.size > 1) "s" else ""} station${if (nouvelles.size > 1) "s" else ""} gardée${if (nouvelles.size > 1) "s" else ""}"
                },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(a.fond)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            GrandTitre(
                "Radio FM",
                when {
                    etat.essai -> "Syntoniseur d'essai · six stations imaginaires"
                    !etat.ecouteurs -> "Branchez des écouteurs : ils servent d'antenne"
                    else -> "Sans data · les écouteurs servent d'antenne"
                },
                sousBarre = true,
            ) {
                Rond(SABLIER, if (etat.minuterieFin != null) "Minuterie en cours" else "S'arrêter plus tard", plein = etat.minuterieFin != null) { minuterie = true }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                // La fréquence en grand, le nom de la station, la force du signal.
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText(mhz(etat.kHz), style = TextStyle(fontFamily = Polices.fine, fontWeight = FontWeight(200), fontSize = 96.sp, lineHeight = 100.sp, letterSpacing = (-0.03).em, color = a.encre))
                        BasicText("MHz", modifier = Modifier.padding(bottom = 18.dp), style = styleDoux(18f, a.encre2, FontWeight.Medium))
                    }
                    AnimatedContent(nomAffiche to etat.enRecherche, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "nom") { (nom, cherche) ->
                        BasicText(
                            when {
                                cherche -> "Recherche…"
                                nom != null -> nom
                                etat.signal > 0 -> "Station sans nom"
                                else -> "Rien ici"
                            },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = styleDoux(18f, if (nom != null) a.encre else a.encre2, FontWeight.SemiBold),
                        )
                    }
                    Signal(etat.signal, Modifier.padding(top = 8.dp))
                }
                // Le cadran : la bande FM, qu'on fait glisser du doigt ; les stations gardées marquées d'un point.
                Cadran(etat.kHz, stations.map { it.kHz }, Modifier.padding(top = 18.dp)) { Poste.commander(c, Poste.REGLER, it) }
                // Les commandes : chercher vers le bas, écouter ou arrêter, chercher vers le haut ; garder la station.
                Row(Modifier.fillMaxWidth().padding(top = 22.dp, start = 32.dp, end = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Rond(CHERCHER_BAS, "Chercher la station d'avant", taille = 52.dp, actif = !etat.enRecherche) { Poste.commander(c, Poste.CHERCHER, 0) }
                    Rond(if (etat.joue) ARRET else Icones.LECTURE, if (etat.joue) "Arrêter" else "Écouter", plein = true, taille = 80.dp, epaisseur = 2.2f) {
                        Poste.commander(c, if (etat.joue) Poste.ARRETER else Poste.JOUER)
                    }
                    Rond(CHERCHER_HAUT, "Chercher la station d'après", taille = 52.dp, actif = !etat.enRecherche) { Poste.commander(c, Poste.CHERCHER, 1) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                    val dedans = gardee != null
                    Row(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(if (dedans) a.voile else a.champ)
                            .clickable(onClickLabel = if (dedans) "Retirer de mes stations" else "Garder cette station", role = Role.Button) {
                                if (dedans) Stations.retirer(c, etat.kHz) else Stations.ajouter(c, Station(etat.kHz, etat.nom.orEmpty()))
                                stations = Stations.liste(c)
                                Avis.dire(if (dedans) "Station retirée" else "Station gardée : ${mhz(etat.kHz)}")
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconeTrait(ETOILE, 18.dp, a.accentTexte, epaisseur = if (dedans) 2.4f else 1.9f)
                        BasicText(if (dedans) "Dans mes stations" else "Garder cette station", style = styleDoux(14.5f, a.accentTexte, FontWeight.SemiBold))
                    }
                }
                // Les écouteurs, l'antenne.
                if (!etat.ecouteurs && !etat.essai) {
                    Row(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(a.voile).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconeTrait(ECOUTEURS, 26.dp, a.accentTexte)
                        BasicText("La radio FM capte par le fil des écouteurs : branchez-en, même sans les mettre, et le son peut sortir du haut-parleur.", style = styleDoux(14f, a.encre))
                    }
                }
                if (etat.essai) {
                    Row(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(a.champ).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconeTrait(ANTENNE, 26.dp, a.encre2)
                        BasicText("Ce téléphone n'a pas de puce FM reconnue : un syntoniseur d'essai fait entendre six stations imaginaires, pour voir l'appli. Sur un vrai téléphone, le pilote de la puce se branche ici.", style = styleDoux(13.5f, a.encre2))
                    }
                }
                // Mes stations.
                Section("Mes stations", droite = if (stations.isEmpty()) null else "${stations.size}")
                if (stations.isEmpty()) {
                    BasicText(
                        "Gardez une station d'une étoile, ou laissez la radio chercher toutes celles qu'elle capte. Un appui long sur une station la renomme ; glissée vers la gauche, elle part.",
                        modifier = Modifier.padding(horizontal = 26.dp), style = styleDoux(14f, a.encre2),
                    )
                } else {
                    Groupe {
                        stations.forEachIndexed { i, s ->
                            if (i > 0) Separateur(16.dp)
                            val rouge = Color(0xFFC2412D)
                            Glisse(gauche = Triple(Icones.CORBEILLE, "Retirer", rouge), faireGauche = {
                                Stations.retirer(c, s.kHz)
                                stations = Stations.liste(c)
                                Avis.dire("${mhz(s.kHz)} retirée") {
                                    Stations.ajouter(c, s)
                                    stations = Stations.liste(c)
                                }
                            }) {
                                Row(
                                    Modifier.fillMaxWidth().background(a.surface)
                                        .combinedClickable(onClickLabel = "Écouter ${mhz(s.kHz)}", onLongClickLabel = "Renommer", role = Role.Button, onLongClick = { renommer = s }) {
                                            Poste.commander(c, Poste.REGLER, s.kHz)
                                            if (!etat.joue) Poste.commander(c, Poste.JOUER)
                                        }
                                        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BasicText(mhz(s.kHz), modifier = Modifier.width(62.dp), style = TextStyle(fontFamily = Polices.fine, fontWeight = FontWeight(300), fontSize = 24.sp, color = if (s.kHz == etat.kHz) a.accentTexte else a.encre))
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        BasicText(s.nom.ifBlank { "Sans nom · appui long pour nommer" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = styleDoux(15.5f, if (s.nom.isBlank()) a.encre2 else a.encre, FontWeight.Medium, 1.25f))
                                        if (s.kHz == etat.kHz && etat.joue) BasicText("À l'écoute", style = styleDoux(12.5f, a.accentTexte, FontWeight.SemiBold, 1.25f))
                                    }
                                    if (s.kHz == etat.kHz && etat.joue) Ondes(a.accentTexte)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                    BoutonTexteAppli(if (balayage) "Recherche en cours…" else "Chercher toutes les stations", style = 's', icone = ANTENNE, actif = !balayage) { balayer() }
                }
                BasicText(
                    "La radio FM ne consomme pas de data et marche sans réseau. Le volume se règle avec les touches du téléphone.",
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 16.dp), style = styleDoux(13f, a.encre2),
                )
            }
        }
        BarreAvis(Modifier.align(Alignment.BottomCenter), bas = 20.dp)
        if (minuterie) FeuilleMinuterie(etat.minuterieFin, fermer = { minuterie = false }) { min -> Poste.commander(c, Poste.MINUTERIE, min) }
        renommer?.let { s ->
            DialogueRenommer(s, fermer = { renommer = null }) { nom ->
                Stations.renommer(c, s.kHz, nom)
                stations = Stations.liste(c)
                renommer = null
            }
        }
    }
}

/** Quatre barres pour la force du signal. */
@Composable
private fun Signal(signal: Int, modifier: Modifier = Modifier) {
    val a = LocalIdentite.current
    Row(modifier.semantics { contentDescription = "Signal $signal sur 100" }, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (1..4).forEach { i ->
            val allumee = signal >= i * 25 - 12
            val couleur by animateFloatAsState(if (allumee) 1f else 0.18f, label = "barre")
            Box(Modifier.width(6.dp).height((6 + i * 4).dp).clip(RoundedCornerShape(2.dp)).background(a.accentTexte.copy(alpha = couleur)))
        }
    }
}

/** Trois petites ondes qui dansent à côté de la station qu'on écoute. */
@Composable
private fun Ondes(couleur: Color) {
    val tour = androidx.compose.animation.core.rememberInfiniteTransition(label = "ondes")
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(0, 160, 320).forEach { decalage ->
            val h by tour.animateFloat(
                0.3f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(520, delayMillis = decalage), androidx.compose.animation.core.RepeatMode.Reverse),
                label = "onde",
            )
            Box(Modifier.width(3.dp).height((16 * h).dp).clip(RoundedCornerShape(1.5.dp)).background(couleur))
        }
    }
}

/**
 * Le cadran : la bande FM graduée, qui glisse sous une aiguille fixe. Un chiffre tous les mégahertz, une graduation
 * tous les 0,1 ; les stations gardées marquées d'un point ; un cran sous le doigt à chaque 0,1 MHz ; lâché, il s'arrête net.
 */
@Composable
private fun Cadran(kHz: Int, gardees: List<Int>, modifier: Modifier = Modifier, regler: (Int) -> Unit) {
    val a = LocalIdentite.current
    val retour = LocalHapticFeedback.current
    val densite = LocalDensity.current
    val mesure = rememberTextMeasurer()
    // 1 MHz = 72 dp sur le cadran.
    val parMhz = with(densite) { 72.dp.toPx() }
    var glisse by remember { mutableFloatStateOf(0f) }
    var dernierCran by remember { mutableIntStateOf(kHz) }
    val portee = rememberCoroutineScope()
    // Pendant le doigt, la fréquence affichée suit sans attendre le service.
    val affichee = if (glisse == 0f) kHz else Bande.borne(kHz - (glisse / parMhz * 1000).roundToInt())
    val encre = a.encre
    val encre2 = a.encre2
    val accent = a.accentTexte
    val styleChiffre = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = encre2)
    Box(
        modifier.fillMaxWidth().height(110.dp)
            .draggable(
                rememberDraggableState { d ->
                    glisse += d
                    val k = Bande.borne(kHz - (glisse / parMhz * 1000).roundToInt())
                    if (k != dernierCran) {
                        dernierCran = k
                        retour.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                },
                Orientation.Horizontal,
                onDragStopped = {
                    val k = affichee
                    glisse = 0f
                    if (k != kHz) regler(k)
                },
            )
            .semantics { contentDescription = "Cadran, ${mhz(affichee)} mégahertz" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val milieu = size.width / 2
            val base = size.height - 30.dp.toPx()
            // De la fréquence à sa place sur le cadran.
            fun x(k: Int) = milieu + (k - affichee) / 1000f * parMhz
            for (k in Bande.MIN..Bande.MAX step 100) {
                val px = x(k)
                if (px < -20 || px > size.width + 20) continue
                val entier = k % 1000 == 0
                val demi = k % 500 == 0
                val h = if (entier) 26.dp.toPx() else if (demi) 16.dp.toPx() else 9.dp.toPx()
                val loin = (abs(px - milieu) / (size.width / 2)).coerceIn(0f, 1f)
                drawLine(encre2.copy(alpha = (if (entier) 0.9f else 0.45f) * (1f - 0.6f * loin)), Offset(px, base), Offset(px, base - h), if (entier) 2.dp.toPx() else 1.dp.toPx(), StrokeCap.Round)
                if (entier && k % 2000 == 0) {
                    val texte = mesure.measure("${k / 1000}", styleChiffre)
                    drawText(texte, topLeft = Offset(px - texte.size.width / 2, base + 8.dp.toPx()), alpha = 1f - 0.6f * loin)
                }
            }
            gardees.forEach { k ->
                val px = x(k)
                if (px >= 0 && px <= size.width) drawCircle(accent, 3.5.dp.toPx(), Offset(px, base - 34.dp.toPx()))
            }
            // L'aiguille, fixe au milieu.
            drawLine(accent, Offset(milieu, base + 4.dp.toPx()), Offset(milieu, base - 40.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(accent, 5.dp.toPx(), Offset(milieu, base - 40.dp.toPx()))
            drawCircle(encre.copy(alpha = 0.0f), 1f, Offset(0f, 0f), style = Stroke(1f))
        }
    }
    LaunchedEffect(kHz) { dernierCran = kHz }
    // Un ressort inutile mais honnête : rien à animer ici, le service répond tout de suite.
    @Suppress("UNUSED_VARIABLE") val rien = portee
}

/** S'arrêter plus tard : pour s'endormir avec la radio. */
@Composable
private fun FeuilleMinuterie(fin: Long?, fermer: () -> Unit, choisir: (Int) -> Unit) {
    val a = LocalIdentite.current
    FeuilleAppli(fermer = fermer, titre = "S'arrêter plus tard") {
        val f = LocalFermerFeuille.current
        if (fin != null) {
            BasicText(
                "La radio s'arrêtera à " + Instant.ofEpochMilli(fin).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) + ".",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = styleDoux(15f, a.encre),
            )
        }
        listOf(15, 30, 45, 60, 90).forEach { m ->
            ActionFeuille(SABLIER, if (m < 60) "Dans $m min" else if (m == 60) "Dans 1 h" else "Dans 1 h 30") {
                choisir(m)
                f()
            }
        }
        if (fin != null) ActionFeuille(Icones.FERMER, "Ne plus s'arrêter", danger = true) {
            choisir(0)
            f()
        }
    }
}

/** Nommer une station : « Radio Côte d'Ivoire », « La radio de Tonton »… */
@Composable
private fun DialogueRenommer(s: Station, fermer: () -> Unit, valider: (String) -> Unit) {
    val a = LocalIdentite.current
    var nom by remember { mutableStateOf(s.nom) }
    DialogueAppli("Nommer ${mhz(s.kHz)} MHz", "Le nom que vous lui donnez reste sur ce téléphone.", fermer = fermer, contenu = {
        BasicTextField(
            nom, { nom = it }, singleLine = true,
            textStyle = styleDoux(17f, a.encre), cursorBrush = SolidColor(a.accent),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(a.champ).padding(14.dp).semantics { contentDescription = "Nom de la station" },
            decorationBox = { champ -> Box { if (nom.isEmpty()) BasicText("Nom de la station", style = styleDoux(17f, a.encre2)); champ() } },
        )
    }) {
        BoutonTexteAppli("Annuler", style = 't', onClick = fermer)
        BoutonTexteAppli("Garder") { valider(nom.trim()) }
    }
}
