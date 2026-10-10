package africa.samaos.horloge

import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.appui
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ce qui sonne, même téléphone verrouillé : l'heure en grand sur un ciel d'aube, des ondes qui s'élargissent.
 * On arrête en faisant glisser le bouton (une poche ne l'arrête pas par mégarde) ; « Encore » repousse l'alarme.
 */
class Sonnerie : ComponentActivity() {
    private var sonnerie: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val titre = intent.getStringExtra("titre") ?: "Alarme"
        val minuteur = intent.getBooleanExtra("minuteur", false)
        val id = intent.getIntExtra("id", -1)
        val report = intent.getIntExtra("report", 10)
        sonner(intent.getStringExtra("son"), intent.getBooleanExtra("vibrer", true))
        setContent {
            AvecIdentite(Identites.Horloge) {
                val a = LocalIdentite.current
                val maintenant = rememberMaintenant()
                DisposableEffect(Unit) { onDispose { arreterSon() } }
                val ciel = if (minuteur) listOf(Color(0xFF1E2640), Color(0xFF1F3A4A), Color(0xFF2E5A57))
                else listOf(Color(0xFF1E2640), Color(0xFF3A3F6E), Color(0xFF7A5E7A), Color(0xFFD99A7A))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(ciel))) {
                    Ondes(Modifier.align(Alignment.Center).padding(bottom = 120.dp))
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(1f))
                        BasicText(titre, style = texte(22f, Color.White.copy(alpha = 0.85f), FontWeight.SemiBold))
                        BasicText(maintenant.format(DateTimeFormatter.ofPattern("HH:mm")), style = CHIFFRES.copy(fontSize = 112.sp, lineHeight = 118.sp, color = Color.White))
                        BasicText(
                            if (minuteur) "Le minuteur est fini" else maintenant.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() },
                            style = texte(18f, Color.White.copy(alpha = 0.8f)),
                        )
                        Spacer(Modifier.weight(1.3f))
                        if (!minuteur) {
                            Box(
                                Modifier.appui("Encore $report minutes") {
                                    Alarmes.reporter(this@Sonnerie, report, id)
                                    fermer()
                                }.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) { BasicText("Encore $report min", style = texte(18f, Color.White, FontWeight.SemiBold)) }
                            Spacer(Modifier.height(14.dp))
                        }
                        GlisserPourArreter(a.accent, a.surAccent) { fermer() }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    private fun sonner(son: String?, vibrer: Boolean) {
        try {
            val uri = son?.let { Uri.parse(it) } ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            sonnerie = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                isLooping = true
                play()
            }
        } catch (_: Exception) {
        }
        if (vibrer) {
            try {
                getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0))
            } catch (_: Exception) {
            }
        }
    }

    private fun arreterSon() {
        sonnerie?.stop()
        getSystemService(Vibrator::class.java)?.cancel()
    }

    private fun fermer() {
        arreterSon()
        getSystemService(NotificationManager::class.java).cancel(9)
        finish()
    }
}

/** Trois ondes qui partent de l'heure et s'effacent en s'élargissant. */
@Composable
private fun Ondes(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "ondes")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "k")
    Canvas(modifier.size(340.dp)) {
        for (i in 0 until 3) {
            val p = (k + i / 3f) % 1f
            drawCircle(Color.White.copy(alpha = 0.22f * (1 - p)), size.minDimension / 2 * (0.35f + 0.65f * p), center, style = Stroke(2.dp.toPx()))
        }
    }
}

/** Faire glisser le bouton jusqu'au bout pour arrêter ; lâché avant, il revient. Le texte luit doucement. */
@Composable
private fun GlisserPourArreter(fond: Color, encre: Color, arreter: () -> Unit) {
    // La position suit le doigt sans délai ; seules la fin (arrêt ou retour) sont animées.
    var x by remember { mutableFloatStateOf(0f) }
    var large by remember { mutableIntStateOf(1) }
    val d = LocalDensity.current
    val bouton = with(d) { 64.dp.toPx() }
    val marge = with(d) { 6.dp.toPx() }
    val course = (large - bouton - marge * 2).coerceAtLeast(1f)
    val lueur = rememberInfiniteTransition(label = "lueur")
    val clair by lueur.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "clair")
    Box(
        Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(38.dp)).background(Color.White.copy(alpha = 0.14f))
            .onSizeChanged { large = it.width }
            .semantics { onClick("Arrêter") { arreter(); true } },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            "Glisser pour arrêter",
            modifier = Modifier.align(Alignment.Center).padding(start = 40.dp).graphicsLayer { alpha = clair * (1f - x / course) },
            style = texte(17f, Color.White, FontWeight.SemiBold),
        )
        Box(
            Modifier
                .offset { IntOffset((marge + x).roundToInt(), 0) }
                .size(64.dp).clip(CircleShape).background(fond)
                .draggable(
                    rememberDraggableState { delta -> x = (x + delta).coerceIn(0f, course) },
                    Orientation.Horizontal,
                    onDragStopped = { vitesse ->
                        if (x > course * 0.7f || (vitesse > 1500f && x > course * 0.3f)) {
                            animate(x, course, animationSpec = tween(120)) { v, _ -> x = v }
                            arreter()
                        } else {
                            animate(x, 0f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f)) { v, _ -> x = v }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { IconeTrait("M5 12h14 M13 6l6 6l-6 6", 26.dp, encre, epaisseur = 2.4f) }
    }
}
