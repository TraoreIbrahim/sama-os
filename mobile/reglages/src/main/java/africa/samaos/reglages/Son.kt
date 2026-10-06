package africa.samaos.reglages

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Un curseur de Banco : un galet qu'on remplit du doigt, l'icône au début, le pourcentage au bout. */
@Composable
fun Curseur(nom: String, icone: String, valeur: Float, changer: (Float) -> Unit) {
    val b = LocalBanco.current
    var largeur by remember { mutableFloatStateOf(1f) }
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(b.sol2)
                .onSizeChanged { largeur = it.width.toFloat() }
                .pointerInput(Unit) { detectTapGestures { changer((it.x / largeur).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> changer((ch.position.x / largeur).coerceIn(0f, 1f)) } }
                .semantics {
                    contentDescription = nom
                    stateDescription = "${(valeur * 100).roundToInt()} %"
                },
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(valeur.coerceIn(0.12f, 1f)).clip(RoundedCornerShape(50)).background(b.laterite).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
                IconeTrait(icone, 20.dp, b.surLaterite)
            }
            BasicText(
                "${(valeur * 100).roundToInt()} %",
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
            )
        }
    }
}

/** Trois ou quatre choix côte à côte, comme « Sonnerie | Vibreur | Silence ». */
@Composable
fun Segments(choix: List<String>, choisi: Int, choisir: (Int) -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(b.sol2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        choix.forEachIndexed { i, nom ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .then(if (i == choisi) Modifier.background(b.laterite) else Modifier)
                    .clickable(onClickLabel = nom, role = Role.RadioButton) { choisir(i) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (i == choisi) b.surLaterite else b.encre))
            }
        }
    }
}

/** Le son (maquette l3-volumes). */
@Composable
fun PageSon(nav: Nav) {
    val c = LocalContext.current
    val am = remember { c.getSystemService(AudioManager::class.java) }
    var version by remember { mutableIntStateOf(0) }
    val mode = remember(version) { am.ringerMode }
    fun lu(flux: Int) = am.getStreamVolume(flux).toFloat() / am.getStreamMaxVolume(flux).coerceAtLeast(1)
    val flux = listOf(AudioManager.STREAM_RING, AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM, AudioManager.STREAM_NOTIFICATION)
    var volumes by remember { mutableStateOf(flux.associateWith { lu(it) }) }
    LaunchedEffect(version) { volumes = flux.associateWith { lu(it) } }
    fun volume(f: Int) = volumes[f] ?: 0f
    fun regler(f: Int, v: Float) {
        try {
            am.setStreamVolume(f, (v * am.getStreamMaxVolume(f)).roundToInt(), 0)
        } catch (_: SecurityException) {
        }
        volumes = volumes + (f to lu(f))
    }
    PageReglages(titre = "Son", retour = nav.retour) {
        section(cle = "mode") {
            Segments(
                listOf("Sonnerie", "Vibreur", "Silence"),
                when (mode) {
                    AudioManager.RINGER_MODE_VIBRATE -> 1
                    AudioManager.RINGER_MODE_SILENT -> 2
                    else -> 0
                },
            ) { i ->
                MoteurNotifs.nm(c)
                try {
                    am.ringerMode = listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT)[i]
                } catch (_: SecurityException) {
                }
                version++
            }
        }
        section("Volumes", cle = "volumes") {
            Curseur("Sonnerie", Icones.APPEL, volume(AudioManager.STREAM_RING)) { regler(AudioManager.STREAM_RING, it) }
            Curseur("Médias", Icones.MUSIQUE, volume(AudioManager.STREAM_MUSIC)) { regler(AudioManager.STREAM_MUSIC, it) }
            Curseur("Alarmes", Icones.HORLOGE, volume(AudioManager.STREAM_ALARM)) { regler(AudioManager.STREAM_ALARM, it) }
            Curseur("Notifications", Icones.CLOCHE, volume(AudioManager.STREAM_NOTIFICATION)) { regler(AudioManager.STREAM_NOTIFICATION, it) }
        }
        section(cle = "plus") {
            Ligne("Sonneries", detail = nomSon(c, RingtoneManager.TYPE_RINGTONE), icone = Icones.MUSIQUE) { nav.aller(Page.Sonneries) }
            Ligne("Vibrations", icone = Icones.VIBREUR) { nav.aller(Page.Vibrations) }
        }
    }
}

fun nomSon(c: Context, type: Int): String = try {
    val uri = RingtoneManager.getActualDefaultRingtoneUri(c, type)
    if (uri == null) "Aucun" else RingtoneManager.getRingtone(c, uri)?.getTitle(c) ?: "Par défaut"
} catch (_: Exception) {
    "Par défaut"
}

/** Les sonneries (maquette l3-sonneries) : pour les appels, les notifications et les alarmes. */
@Composable
fun PageSonneries(nav: Nav) {
    val c = LocalContext.current
    var type by remember { mutableIntStateOf(RingtoneManager.TYPE_RINGTONE) }
    var version by remember { mutableIntStateOf(0) }
    var sons by remember { mutableStateOf(emptyList<Pair<String, Uri>>()) }
    var lecture by remember { mutableStateOf<Ringtone?>(null) }
    LaunchedEffect(type) {
        sons = withContext(Dispatchers.IO) {
            val rm = RingtoneManager(c).apply { setType(type) }
            val curseur = rm.cursor
            val liste = mutableListOf<Pair<String, Uri>>()
            while (curseur.moveToNext()) liste += curseur.getString(RingtoneManager.TITLE_COLUMN_INDEX) to rm.getRingtoneUri(curseur.position)
            liste
        }
    }
    DisposableEffect(Unit) { onDispose { lecture?.stop() } }
    val actuel = remember(type, version) { RingtoneManager.getActualDefaultRingtoneUri(c, type) }
    PageReglages(titre = "Sonneries", retour = nav.retour) {
        section(cle = "type") {
            Segments(listOf("Appels", "Notifications", "Alarmes"), when (type) {
                RingtoneManager.TYPE_NOTIFICATION -> 1
                RingtoneManager.TYPE_ALARM -> 2
                else -> 0
            }) { i ->
                lecture?.stop()
                type = listOf(RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_ALARM)[i]
            }
        }
        section(cle = "sons-$type") {
            Ligne("Aucun son", fin = Fin.Choix(actuel == null)) {
                lecture?.stop()
                RingtoneManager.setActualDefaultRingtoneUri(c, type, null)
                version++
            }
            sons.forEach { (nom, uri) ->
                Ligne(nom, icone = Icones.MUSIQUE, fin = Fin.Choix(actuel?.lastPathSegment == uri.lastPathSegment && actuel != null)) {
                    lecture?.stop()
                    RingtoneManager.setActualDefaultRingtoneUri(c, type, uri)
                    lecture = RingtoneManager.getRingtone(c, uri)?.also { it.play() }
                    version++
                }
            }
        }
    }
}

/** Les vibrations (maquette l3-vibrations) : leur force pour les appels, les notifications et le toucher. */
@Composable
fun PageVibrations(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val cr = c.contentResolver
    val forces = listOf("Coupée", "Faible", "Moyenne", "Forte")
    fun lire(cle: String, defaut: Int = 2) = Settings.System.getInt(cr, cle, defaut).coerceIn(0, 3)
    PageReglages(titre = "Vibrations", retour = nav.retour) {
        val v = version
        section(cle = "appels") {
            val vibrer = lire("ring_vibration_intensity") > 0
            Ligne("Vibrer pour les appels", icone = Icones.VIBREUR, fin = Fin.Inter(vibrer)) {
                Settings.System.putInt(cr, "ring_vibration_intensity", if (vibrer) 0 else 2)
                version++
            }
            val monte = Settings.System.getInt(cr, "apply_ramping_ringer", 0) == 1
            Ligne("Vibrer, puis sonner de plus en plus fort", fin = Fin.Inter(monte)) {
                Settings.System.putInt(cr, "apply_ramping_ringer", if (monte) 0 else 1)
                version++
            }
        }
        listOf(
            Triple("ring_vibration_intensity", "Appels", Icones.APPEL),
            Triple("notification_vibration_intensity", "Notifications", Icones.CLOCHE),
            Triple("haptic_feedback_intensity", "Toucher", Icones.CLAVIER),
        ).forEach { (cle, nom, icone) ->
            section(nom, cle = cle) {
                Segments(forces, if (v >= 0) lire(cle) else 0) { i ->
                    Settings.System.putInt(cr, cle, i)
                    if (cle == "haptic_feedback_intensity") Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (i > 0) 1 else 0)
                    version++
                }
            }
        }
    }
}
