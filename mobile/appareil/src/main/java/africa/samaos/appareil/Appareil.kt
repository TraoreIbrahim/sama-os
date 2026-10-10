package africa.samaos.appareil

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.media.ExifInterface
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.graphicsLayer
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.BarreAvis
import africa.samaos.banco.appli.LocalFermerFeuille
import kotlin.math.abs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.AvecIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.ln
import kotlin.math.pow
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private enum class Mode(val nom: String) { VIDEO("Vidéo"), PHOTO("Photo"), DOCUMENT("Document"), CODE("Code QR") }
/** « Plein écran » garde exactement ce qu'on voit sur l'écran (son rapport se calcule). */
private enum class Format(val nom: String, val ratio: Float) { PLEIN("Plein écran", 0f), TROIS_QUATRE("3:4", 3f / 4), NEUF_SEIZE("9:16", 9f / 16), CARRE("1:1", 1f) }

private object Ic {
    const val FLASH = "M13 3L5 13.5h6L10 21l8-10.5h-6z"
    const val SANS_FLASH = "M13 3L5 13.5h6L10 21l8-10.5h-6z M4 4l16 16"
    const val RETARDATEUR = "M7 3h10 M7 21h10 M8 3v2.5a4 4 0 0 0 1.6 3.2L12 10.5l2.4-1.8A4 4 0 0 0 16 5.5V3 M8 21v-2.5a4 4 0 0 1 1.6-3.2l2.4-1.8l2.4 1.8a4 4 0 0 1 1.6 3.2V21"
    const val REGLAGES = "M5 7h8 M17 7h2 M15 5v4 M5 17h2 M11 17h8 M9 15v4"
    const val TOURNER = "M4 8h3l2-3h6l2 3h3v11H4z M9 13.5a3 3 0 0 1 5.2-2 M15 13.5a3 3 0 0 1-5.2 2 M14.6 9.6v1.9h-1.9 M9.4 17.4v-1.9h1.9"
    const val QR = "M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h2v2h-2z M18 14h2 M14 18h2 M18 18h2v2"
    const val DOCUMENT = "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M14 3v5h5 M9 13h6 M9 17h4"
    const val LIEN = "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1 M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"
    const val ALERTE = "M12 4l9 16H3z M12 10v4 M12 17h.01"
}

/** L'Appareil photo de Sama (maquettes l2-cam). */
open class Appareil : ComponentActivity() {
    internal var actif by mutableStateOf(false)
    /** Le téléphone est trop chaud (état thermique « sévère » d'Android) : la caméra reste fermée. */
    internal var chaud by mutableStateOf(false)
    private val suiviChaleur = android.os.PowerManager.OnThermalStatusChangedListener { s ->
        val avant = chaud
        chaud = s >= android.os.PowerManager.THERMAL_STATUS_SEVERE
        // Noté pour la page « Le téléphone chauffe » des Réglages.
        if (chaud && !avant) try {
            val cr = contentResolver
            val o = try {
                org.json.JSONObject(android.provider.Settings.Global.getString(cr, "sama_chaleur") ?: "{}")
            } catch (_: Exception) {
                org.json.JSONObject()
            }
            android.provider.Settings.Global.putString(cr, "sama_chaleur", o.put("appareil", true).toString())
        } catch (_: Exception) {
        }
    }
    internal var declencher: (() -> Unit)? = null
    internal var volumePourDeclencher = true
    /** Une autre appli demande une photo (IMAGE_CAPTURE) : on la lui rend, sans la garder dans la pellicule. */
    internal val pourUneAppli get() = intent?.action == MediaStore.ACTION_IMAGE_CAPTURE
    /** Une appli de Sama demande seulement de lire un code (les Réglages, pour reconnaître un proche). */
    internal val pourUnCode get() = intent?.action == ACTION_SCANNER_QR

    internal fun rendreCode(t: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra("texte", t))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Le fond est noir : les icônes du bas restent claires.
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        volumePourDeclencher = getSharedPreferences("appareil", MODE_PRIVATE).getBoolean("volume", true)
        setContent { AvecIdentite(Identites.Appareil) { Ecran(this) } }
    }

    /** Tout l'écran pour l'image : la barre d'état se cache (on la tire du haut pour la revoir). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onResume() {
        super.onResume()
        actif = true
        try {
            getSystemService(android.os.PowerManager::class.java).addThermalStatusListener(mainExecutor, suiviChaleur)
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        actif = false
        try {
            getSystemService(android.os.PowerManager::class.java).removeThermalStatusListener(suiviChaleur)
        } catch (_: Exception) {
        }
        super.onPause()
    }

    /** Les touches de volume déclenchent, comme sur la plupart des appareils. */
    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (volumePourDeclencher && (code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_CAMERA)) {
            if (e.repeatCount == 0) declencher?.invoke()
            return true
        }
        return super.onKeyDown(code, e)
    }

    /** Rendre la photo à l'appli qui l'a demandée : dans le fichier qu'elle a donné, sinon en petite image. */
    internal fun rendre(octets: ByteArray) {
        val sortie = intent?.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
        try {
            if (sortie != null) {
                contentResolver.openOutputStream(sortie)?.use { it.write(octets) }
                setResult(Activity.RESULT_OK)
            } else {
                val b = BitmapFactory.decodeByteArray(octets, 0, octets.size, BitmapFactory.Options().apply { inSampleSize = 8 })
                setResult(Activity.RESULT_OK, Intent().putExtra("data", b))
            }
        } catch (_: Exception) {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
}

/** Une photo demandée par une autre appli (IMAGE_CAPTURE) : même appareil, dans la tâche de cette appli. */
class Capture : Appareil()

/** Lire un code QR pour une appli de Sama, qui reçoit le texte lu (« texte »). */
const val ACTION_SCANNER_QR = "africa.samaos.action.SCANNER_QR"

object Pellicule {
    /** Le JPEG tourné à l'endroit et recadré au format choisi ; nettoyé (gris, contraste) pour un document. */
    fun preparer(octets: ByteArray, ratio: Float, document: Boolean): ByteArray {
        val orientation = try {
            ExifInterface(octets.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        if (ratio == 3f / 4 && !document) return octets
        var b = BitmapFactory.decodeByteArray(octets, 0, octets.size) ?: return octets
        val angle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (angle != 0f) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(angle) }, true)
        // Le format : on garde le milieu de l'image.
        val (w, h) = if (b.width.toFloat() / b.height > ratio) (b.height * ratio).roundToInt() to b.height else b.width to (b.width / ratio).roundToInt()
        b = Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w, h)
        if (document) {
            val sortie = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
            val m = ColorMatrix().apply { setSaturation(0f) }
            val c = 1.45f
            m.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, -60f, 0f, c, 0f, 0f, -60f, 0f, 0f, c, 0f, -60f, 0f, 0f, 0f, 1f, 0f)))
            android.graphics.Canvas(sortie).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
            b = sortie
        }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
    }

    fun nom(prefixe: String, ext: String) = prefixe + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "." + ext

    fun enregistrer(c: Context, octets: ByteArray, document: Boolean): Uri? {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nom(if (document) "DOC" else "IMG", "jpg"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (document) "Pictures/Documents scannés/" else "DCIM/Camera/")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return try {
            val uri = c.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v) ?: return null
            c.contentResolver.openOutputStream(uri)?.use { it.write(octets) }
            c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (_: Exception) {
            null
        }
    }

    fun nouvelleVideo(c: Context): Uri? = try {
        c.contentResolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nom("VID", "mp4"))
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        )
    } catch (_: Exception) {
        null
    }

    fun finirVideo(c: Context, uri: Uri) {
        c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    /** La dernière photo ou vidéo de l'appareil (celles qu'il a prises : il les voit sans autre autorisation). */
    fun derniere(c: Context): Pair<Uri, ImageBitmap?>? {
        fun premiere(col: Uri): Pair<Uri, Long>? = try {
            c.contentResolver.query(
                col, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf("DCIM/Camera/"), "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { k -> if (k.moveToFirst()) android.content.ContentUris.withAppendedId(col, k.getLong(0)) to k.getLong(1) else null }
        } catch (_: Exception) {
            null
        }
        val u = listOfNotNull(
            premiere(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)),
            premiere(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)),
        ).maxByOrNull { it.second }?.first ?: return null
        return u to try {
            c.contentResolver.loadThumbnail(u, android.util.Size(240, 240), null).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun vignette(octets: ByteArray): ImageBitmap? = image(octets, 16)

    /** Le JPEG en image, tourné à l'endroit (l'orientation est dans ses données EXIF). */
    fun image(octets: ByteArray, echantillon: Int): ImageBitmap? = try {
        BitmapFactory.decodeByteArray(octets, 0, octets.size, BitmapFactory.Options().apply { inSampleSize = echantillon })?.let { b ->
            val o = ExifInterface(octets.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            val angle = when (o) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            (if (angle != 0f) Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(angle) }, true) else b).asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

private val OR = Color(0xFFF2C14E)
private val ROUGE = Color(0xFFE5533D)
private val CREME = Color(0xFFF4EEE4)
private val GRIS = Color(0xFFB3AA9C)
private val SOMBRE = Color(0xFF1F1C18)
private val NUIT = Color(0xFF0E0D0B)
private val VOILE_NOIR = Color(0x8C0E0D0B)
private val FILTRE_DOCUMENT = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })

/** L'écart entre deux modes sur la bague, en degrés. */
private const val PAS = 40f

/** La photo qui vient d'être prise : l'image de l'aperçu, qui quitte le cadre et rejoint la vignette. */
private class Vol(val image: ImageBitmap, val de: Rect, val document: Boolean)

private fun melange(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun texte(taille: Float, couleur: Color, poids: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Polices.corps, fontWeight = poids, fontSize = taille.sp, lineHeight = (taille * 1.35f).sp, color = couleur)

@Composable
private fun Ecran(act: Appareil) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    val camera = remember { Camera(c) }
    DisposableEffect(Unit) { onDispose { camera.finir() } }
    val (arriere, avant) = remember { camera.cameras() }
    var permis by remember { mutableStateOf(ContextCompat.checkSelfPermission(c, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val demander = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> permis = r[Manifest.permission.CAMERA] == true }
    LaunchedEffect(Unit) { if (!permis) demander.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }

    val modes = if (act.pourUnCode) listOf(Mode.CODE) else if (act.pourUneAppli) listOf(Mode.PHOTO) else Mode.entries
    var mode by remember { mutableStateOf(if (act.pourUnCode) Mode.CODE else Mode.PHOTO) }
    var idCamera by remember { mutableStateOf(arriere ?: avant) }
    var bascules by remember { mutableIntStateOf(0) }
    var texture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var vue by remember { mutableStateOf<TextureView?>(null) }
    var prete by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(Flash.NON) }
    var retardateur by remember { mutableIntStateOf(0) }
    var format by remember { mutableStateOf(Format.entries.firstOrNull { it.name == c.getSharedPreferences("appareil", 0).getString("format", null) } ?: Format.PLEIN) }
    var ecran by remember { mutableFloatStateOf(9f / 20) }
    var bagueZoom by remember { mutableStateOf(false) }
    var zoomTouche by remember { mutableIntStateOf(0) }
    var grille by remember { mutableStateOf(c.getSharedPreferences("appareil", 0).getBoolean("grille", false)) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pince by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf<Offset?>(null) }
    var focusFois by remember { mutableIntStateOf(0) }
    var ev by remember { mutableIntStateOf(0) }
    var compte by remember { mutableIntStateOf(0) }
    var tirages by remember { mutableIntStateOf(0) }
    var vignette by remember { mutableStateOf<ImageBitmap?>(null) }
    var precedente by remember { mutableStateOf<ImageBitmap?>(null) }
    var derniereUri by remember { mutableStateOf<Uri?>(null) }
    var vol by remember { mutableStateOf<Vol?>(null) }
    var cadreFlux by remember { mutableStateOf(Rect.Zero) }
    var cadreGarde by remember { mutableStateOf(Rect.Zero) }
    var cadreVignette by remember { mutableStateOf(Rect.Zero) }
    val retourne = remember { Animatable(0f) }
    var filme by remember { mutableStateOf<Uri?>(null) }
    var debutFilm by remember { mutableLongStateOf(0L) }
    var maintenant by remember { mutableLongStateOf(0L) }
    var lampe by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    var reglages by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var aGarder by remember { mutableStateOf<ByteArray?>(null) }
    val son = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    // Lire un code dans une image déjà sur le téléphone (le sélecteur de photos d'Android).
    val choisirImage = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) portee.launch {
            val t = withContext(Dispatchers.IO) {
                try {
                    val src = android.graphics.ImageDecoder.createSource(c.contentResolver, uri)
                    val b = android.graphics.ImageDecoder.decodeBitmap(src) { d, info, _ ->
                        d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        val m = maxOf(info.size.width, info.size.height)
                        if (m > 1600) d.setTargetSize(info.size.width * 1600 / m, info.size.height * 1600 / m)
                    }
                    camera.lireImage(b)
                } catch (_: Exception) {
                    null
                }
            }
            if (t != null && act.pourUnCode) act.rendreCode(t) else if (t != null) code = t else erreur = "Pas de code QR lisible dans cette image."
        }
    }

    // La dernière photo ou vidéo de l'appareil : la vignette n'est pas vide en arrivant.
    LaunchedEffect(Unit) {
        if (!act.pourUneAppli && !act.pourUnCode) withContext(Dispatchers.IO) { Pellicule.derniere(c) }?.let { (u, b) ->
            if (derniereUri == null) {
                derniereUri = u
                vignette = b
            }
        }
    }
    // La caméra s'ouvre quand tout est là : l'autorisation, l'écran au premier plan, la surface de l'aperçu.
    LaunchedEffect(permis, act.actif, act.chaud, texture, idCamera, mode == Mode.CODE) {
        prete = false
        focus = null
        val st = texture
        val i = idCamera
        if (permis && act.actif && !act.chaud && st != null && i != null) {
            camera.ouvrir(
                i, st, if (mode == Mode.CODE) Usage.SCANNER else Usage.PHOTO,
                pret = { portee.launch { prete = true; zoom = 1f; ev = 0 } },
                scan = { t -> portee.launch { if (act.pourUnCode) act.rendreCode(t) else if (code == null) code = t } },
                erreur = { e -> portee.launch { erreur = e } },
            )
        } else {
            camera.fermer()
        }
    }
    LaunchedEffect(filme) {
        while (filme != null) {
            maintenant = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    // La bague redevient celle des modes quand on ne touche plus au zoom.
    LaunchedEffect(zoomTouche) {
        if (bagueZoom) {
            delay(4000)
            bagueZoom = false
        }
    }
    // Le cercle de mise au point s'efface seul, sauf pendant qu'on règle la lumière.
    LaunchedEffect(focusFois) {
        if (focus != null) {
            delay(2600)
            focus = null
        }
    }

    fun nouvelleVignette(b: ImageBitmap?) {
        if (b == null || b === vignette) return
        precedente = vignette
        vignette = b
    }

    /** L'image de l'aperçu au moment du déclic, coupée au format choisi. */
    fun instantane(document: Boolean): Vol? {
        val v = vue ?: return null
        val f = cadreFlux
        val g = cadreGarde
        if (v.width <= 0 || f.width <= 0f || g.width <= 0f) return null
        val b = try {
            v.getBitmap(v.width / 3, v.height / 3)
        } catch (_: Exception) {
            null
        } ?: return null
        val w = (b.width * g.width / f.width).roundToInt().coerceIn(1, b.width)
        val h = (b.height * g.height / f.height).roundToInt().coerceIn(1, b.height)
        val coupe = Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w, h)
        return Vol(coupe.asImageBitmap(), g, document)
    }

    fun prendre() {
        if (!prete || compte > 0) return
        val document = mode == Mode.DOCUMENT
        val ratio = if (document) 3f / 4 else if (format == Format.PLEIN) ecran else format.ratio
        portee.launch {
            for (s in retardateur downTo 1) {
                compte = s
                delay(1000)
            }
            compte = 0
            son.play(MediaActionSound.SHUTTER_CLICK)
            tirages++
            // Le tirage s'envole vers la vignette pendant que la photo s'enregistre.
            val tirage = if (act.pourUneAppli) null else instantane(document)
            if (tirage != null) {
                vol?.let { nouvelleVignette(it.image) }
                vol = tirage
            }
            camera.photo(flash) { octets ->
                portee.launch {
                    val pret = withContext(Dispatchers.Default) { Pellicule.preparer(octets, ratio, document) }
                    if (act.pourUneAppli) {
                        aGarder = pret
                        return@launch
                    }
                    val uri = withContext(Dispatchers.IO) { Pellicule.enregistrer(c, pret, document) }
                    if (uri != null) {
                        derniereUri = uri
                        val v = withContext(Dispatchers.Default) { Pellicule.vignette(pret) }
                        // Sans tirage, la vraie vignette arrive ; un tirage déjà posé est remplacé par la même photo, nette.
                        if (tirage == null) nouvelleVignette(v) else if (vol !== tirage && v != null) vignette = v
                    }
                    if (document) Avis.dire("Document enregistré dans Photos › Documents scannés")
                }
            }
        }
    }

    fun filmer() {
        if (filme != null) {
            val u = filme!!
            filme = null
            son.play(MediaActionSound.STOP_VIDEO_RECORDING)
            camera.arreterFilm {
                portee.launch {
                    withContext(Dispatchers.IO) { Pellicule.finirVideo(c, u) }
                    derniereUri = u
                    nouvelleVignette(
                        withContext(Dispatchers.IO) {
                            try {
                                c.contentResolver.loadThumbnail(u, android.util.Size(240, 240), null).asImageBitmap()
                            } catch (_: Exception) {
                                null
                            }
                        },
                    )
                }
            }
            return
        }
        val u = Pellicule.nouvelleVideo(c) ?: return
        val fd = c.contentResolver.openFileDescriptor(u, "rw") ?: return
        val avecSon = ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        camera.filmer(fd.fileDescriptor, avecSon) { ok ->
            portee.launch {
                if (ok) {
                    son.play(MediaActionSound.START_VIDEO_RECORDING)
                    debutFilm = SystemClock.elapsedRealtime()
                    filme = u
                } else {
                    erreur = "La vidéo n'a pas pu démarrer."
                    c.contentResolver.delete(u, null, null)
                }
            }
        }
    }
    fun changerMode(m: Mode) {
        if (m == mode || filme != null || m !in modes) return
        mode = m
        code = null
        bagueZoom = false
        if (m != Mode.CODE && lampe) {
            lampe = false
            camera.lampe(false)
        }
    }
    // L'autre caméra : l'aperçu se retourne comme une carte, et revient quand l'image est là.
    fun basculer() {
        if (avant == null || arriere == null || filme != null || retourne.isRunning) return
        portee.launch {
            retourne.animateTo(90f, tween(170, easing = FastOutLinearInEasing))
            idCamera = if (idCamera == arriere) avant else arriere
            bascules++
            withTimeoutOrNull(1800) {
                snapshotFlow { prete }.first { !it }
                snapshotFlow { prete }.first { it }
            }
            retourne.snapTo(-90f)
            retourne.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 320f))
        }
    }
    act.declencher = { if (mode == Mode.VIDEO) filmer() else if (mode == Mode.PHOTO || mode == Mode.DOCUMENT) prendre() }
    // Un agrandissement choisi d'un toucher arrive en glissant, pas d'un coup.
    fun zoomerVers(cible: Float) {
        portee.launch {
            androidx.compose.animation.core.animate(zoom, cible, animationSpec = tween(260)) { z, _ ->
                zoom = z
                camera.zoomer(z)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(NUIT)) {
        val densite = LocalDensity.current
        val coupe = WindowInsets.displayCutout.getTop(densite)
        val basSysteme = WindowInsets.navigationBars.getBottom(densite)
        val hautBandeau = coupe + with(densite) { 64.dp.toPx() }
        val hautBloc = basSysteme + with(densite) { (BLOC + 8.dp).toPx() }
        val zoomPossible = prete && (mode == Mode.PHOTO || mode == Mode.VIDEO) && camera.zooms.upper > 1f
        val ratioVideo = remember(prete, idCamera) { (if (prete) camera.profil()?.let { it.videoFrameHeight.toFloat() / it.videoFrameWidth } else null) ?: (9f / 16) }
        // ——— L'image, sur tout l'écran ; les autres formats la font glisser jusqu'à leur cadre ———
        BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { rotationY = retourne.value; cameraDistance = 14f * density }) {
            val l = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val r = when (mode) {
                Mode.PHOTO -> if (format == Format.PLEIN) l / h else format.ratio
                Mode.VIDEO -> ratioVideo
                Mode.DOCUMENT -> 3f / 4
                Mode.CODE -> l / h
            }
            val (fluxCible, cadreCible) = cadrage(r, l, h, hautBandeau)
            val flux by animateRectAsState(fluxCible, spring(dampingRatio = 0.86f, stiffness = 240f), label = "flux")
            val cadre by animateRectAsState(cadreCible, spring(dampingRatio = 0.86f, stiffness = 240f), label = "cadre")
            SideEffect {
                ecran = l / h
                cadreFlux = fluxCible
                cadreGarde = cadreCible
            }
            // Ce qu'on voit vraiment de la photo : sous le bandeau, au-dessus du bloc des commandes.
            val champ = Rect(cadre.left, maxOf(cadre.top, hautBandeau), cadre.right, minOf(cadre.bottom, h - hautBloc).coerceAtLeast(maxOf(cadre.top, hautBandeau) + 1f))
            Box(
                // En plein écran, l'image déborde de l'écran sur les côtés : elle n'est pas tenue dans sa largeur.
                Modifier.offset { IntOffset(flux.left.roundToInt(), flux.top.roundToInt()) }
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(with(densite) { flux.width.toDp() }, with(densite) { flux.height.toDp() })
                    .clip(RoundedCornerShape(26.dp)).background(Color(0xFF151412)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            vue = this
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                                    texture = s
                                }

                                override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                                override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                                    texture = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Le déclic : l'image s'assombrit un instant, comme derrière un obturateur.
                val obturation = remember { Animatable(0f) }
                LaunchedEffect(tirages) {
                    if (tirages > 0) {
                        obturation.snapTo(0.9f)
                        obturation.animateTo(0f, tween(320, delayMillis = 60))
                    }
                }
                if (obturation.value > 0f) Box(Modifier.fillMaxSize().background(NUIT.copy(alpha = obturation.value)))
                // La caméra se ferme et se rouvre (changer de caméra, de mode) : un voile, pas une image figée.
                val noir by animateFloatAsState(if (prete || !permis) 0f else 1f, tween(if (prete) 360 else 120), label = "noir")
                if (noir > 0f) Box(Modifier.fillMaxSize().background(Color(0xFF151412).copy(alpha = noir)))
            }
            // Un toucher : la mise au point. Deux doigts : l'agrandissement. Glisser de côté : le mode voisin.
            // Glisser de haut en bas, le cercle de mise au point affiché : la lumière.
            val glisseMode = with(densite) { 70.dp.toPx() }
            val pasLumiere = with(densite) { 34.dp.toPx() }
            Box(
                Modifier.fillMaxSize().pointerInput(prete, mode, filme) {
                    awaitEachGesture {
                        val bas = awaitFirstDown(requireUnconsumed = false)
                        var cumul = Offset.Zero
                        var deuxDoigts = false
                        var tape = true
                        val evDepart = ev
                        val lumiere = focus != null && mode != Mode.CODE
                        do {
                            val e = awaitPointerEvent()
                            if (e.changes.size >= 2) {
                                deuxDoigts = true
                                tape = false
                                val z = e.calculateZoom()
                                if (prete && z != 1f && mode != Mode.CODE && camera.zooms.upper > 1f) {
                                    pince = true
                                    zoom = (zoom * z).coerceIn(camera.zooms.lower, camera.zooms.upper)
                                    camera.zoomer(zoom)
                                    bagueZoom = true
                                    zoomTouche++
                                }
                                e.changes.forEach { it.consume() }
                            } else {
                                cumul += e.calculatePan()
                                if (cumul.getDistance() > viewConfiguration.touchSlop) tape = false
                                if (lumiere && !deuxDoigts && abs(cumul.y) > abs(cumul.x) && prete) {
                                    val r = camera.expositions
                                    val nouveau = (evDepart - (cumul.y / pasLumiere).roundToInt()).coerceIn(r.lower, r.upper)
                                    if (nouveau != ev) {
                                        ev = nouveau
                                        camera.exposer(ev)
                                        focusFois++
                                    }
                                }
                            }
                        } while (e.changes.any { it.pressed })
                        pince = false
                        when {
                            tape && prete && mode != Mode.CODE && flux.contains(bas.position) -> {
                                focus = bas.position
                                focusFois++
                                ev = 0
                                camera.exposer(0)
                                camera.mettreAuPoint(((bas.position.x - flux.left) / flux.width).coerceIn(0f, 1f), ((bas.position.y - flux.top) / flux.height).coerceIn(0f, 1f))
                            }
                            !deuxDoigts && abs(cumul.x) > glisseMode && abs(cumul.x) > abs(cumul.y) * 1.4f -> {
                                val i = modes.indexOf(mode) + if (cumul.x < 0) 1 else -1
                                modes.getOrNull(i)?.let { changerMode(it) }
                            }
                        }
                    }
                },
            )
            // Hors du format choisi, l'image est voilée ; la grille partage ce qui sera gardé.
            Canvas(Modifier.fillMaxSize()) {
                if (cadre.width < flux.width - 1f || cadre.height < flux.height - 1f) {
                    drawPath(
                        Path().apply {
                            addRect(flux)
                            addRect(cadre)
                            fillType = PathFillType.EvenOdd
                        },
                        VOILE_NOIR,
                    )
                }
                if (grille && (mode == Mode.PHOTO || mode == Mode.VIDEO)) {
                    for (i in 1..2) {
                        val x = cadre.left + cadre.width * i / 3
                        val y = cadre.top + cadre.height * i / 3
                        drawLine(Color(0x59FFFFFF), Offset(x, cadre.top), Offset(x, cadre.bottom), 1.dp.toPx())
                        drawLine(Color(0x59FFFFFF), Offset(cadre.left, y), Offset(cadre.right, y), 1.dp.toPx())
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(mode == Mode.CODE || mode == Mode.DOCUMENT, enter = fadeIn(tween(240)), exit = fadeOut(tween(160))) {
                Cadre(document = mode == Mode.DOCUMENT, zone = champ)
            }
            // Le conseil du mode, en haut de ce qu'on voit.
            AnimatedContent(
                when {
                    mode == Mode.CODE -> "Visez un code QR : il se lit tout seul"
                    mode == Mode.DOCUMENT -> "Posez la page à plat, dans le cadre"
                    else -> ""
                },
                modifier = Modifier.fillMaxWidth().offset { IntOffset(0, (champ.top + 12.dp.toPx()).roundToInt()) },
                contentAlignment = Alignment.TopCenter,
                transitionSpec = { (fadeIn(tween(220)) + slideInVertically { -it / 2 }) togetherWith fadeOut(tween(120)) }, label = "conseil",
            ) { t ->
                if (t.isNotEmpty()) BasicText(t, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(VOILE_NOIR).padding(horizontal = 14.dp, vertical = 7.dp), style = texte(14f, CREME, FontWeight.SemiBold))
            }
            focus?.let { f -> Mise(f, ev, camera.expositions) }
            // Pendant qu'on pince : l'agrandissement en grand.
            androidx.compose.animation.AnimatedVisibility(
                pince, modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (hautBandeau + 10.dp.toPx()).roundToInt()) },
                enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut(),
            ) {
                BasicText(etiquetteZoom(zoom) + "×", modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(VOILE_NOIR).padding(horizontal = 16.dp, vertical = 6.dp), style = texte(20f, OR, FontWeight.Bold))
            }
            // Le retardateur : chaque seconde arrive en grandissant, au milieu de ce qu'on voit.
            Box(
                Modifier.offset { IntOffset(champ.left.roundToInt(), champ.top.roundToInt()) }.size(with(densite) { champ.width.toDp() }, with(densite) { champ.height.toDp() }),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(compte, transitionSpec = { (scaleIn(tween(300), 1.6f) + fadeIn(tween(200))) togetherWith (scaleOut(tween(200), 0.6f) + fadeOut(tween(200))) }, label = "compte") { n ->
                    if (n > 0) BasicText("$n", style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(250), fontSize = 140.sp, color = CREME))
                }
            }
            if (!permis) Permission { demander.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
        }
        // ——— En haut : le bandeau des réglages, sur l'image ———
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)).padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
            Bandeau(
                mode = mode, enregistre = filme != null, duree = maintenant - debutFilm,
                flashPossible = prete && camera.flashPossible, flash = flash, retardateur = retardateur, format = format, lampe = lampe,
                infoVideo = if (mode == Mode.VIDEO && prete) remember(prete, idCamera) { camera.profil()?.let { "${it.videoFrameHeight}p · ${it.videoFrameRate} i/s · ${resteVideo(it.videoBitRate + it.audioBitRate)}" } } else null,
                pourUnCode = act.pourUnCode,
                changerFlash = { flash = Flash.entries[(flash.ordinal + 1) % Flash.entries.size] },
                changerRetardateur = { retardateur = when (retardateur) { 0 -> 3; 3 -> 10; else -> 0 } },
                changerFormat = {
                    format = Format.entries[(format.ordinal + 1) % Format.entries.size]
                    c.getSharedPreferences("appareil", 0).edit().putString("format", format.name).apply()
                },
                basculerLampe = { lampe = !lampe; camera.lampe(lampe) },
                fermer = { act.finish() },
                reglages = { reglages = true },
            )
        }
        // ——— En bas : les niveaux d'agrandissement, puis le bloc transparent des commandes ———
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(zoomPossible && filme == null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Niveaux(zoom, camera.zooms, Modifier.padding(bottom = 10.dp)) { z ->
                    zoomerVers(z)
                    bagueZoom = true
                    zoomTouche++
                }
            }
            Box(Modifier.fillMaxWidth().height(BLOC).clip(RoundedCornerShape(34.dp)).background(Color(0x990E0D0B)).border(1.dp, Color(0x1FF4EEE4), RoundedCornerShape(34.dp))) {
                Bague(
                    modes, mode, visible = filme == null && (modes.size > 1 || (bagueZoom && zoomPossible)), centreBas = CENTRE, modifier = Modifier.fillMaxSize(),
                    choisir = { changerMode(it) },
                    enZoom = bagueZoom && zoomPossible, zoom = zoom, plage = if (zoomPossible) camera.zooms else null,
                    zoomer = { z, anime ->
                        if (anime) zoomerVers(z) else {
                            zoom = z
                            camera.zoomer(z)
                        }
                    },
                    toucher = {
                        bagueZoom = true
                        zoomTouche++
                    },
                )
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(CENTRE * 2).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(mode == Mode.CODE, transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f)) }, label = "gauche") { lireImage ->
                            if (lireImage) {
                                Box(
                                    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, CREME.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                        .clickable(onClickLabel = "Lire un code dans une image", role = Role.Button) {
                                            choisirImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }.semantics { contentDescription = "Lire un code dans une image" },
                                    contentAlignment = Alignment.Center,
                                ) { IconeTrait(Icones.IMAGE, 24.dp, CREME) }
                            } else if (!act.pourUneAppli) {
                                Vignette(vignette, precedente, visible = filme == null, modifier = Modifier.onGloballyPositioned { cadreVignette = it.boundsInRoot() }) {
                                    derniereUri?.let { u ->
                                        try {
                                            c.startActivity(Intent("com.android.camera.action.REVIEW", u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                        } catch (_: Exception) {
                                            c.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Declencheur(mode, filme != null, prete, tirages, debutFilm) { if (mode == Mode.VIDEO) filmer() else prendre() }
                    val tour by animateFloatAsState(bascules * 180f, tween(420), label = "tour")
                    val actif = avant != null && arriere != null && filme == null
                    val clair by animateFloatAsState(if (actif) 1f else 0.3f, label = "clair")
                    Box(
                        Modifier.size(64.dp).graphicsLayer { alpha = clair }.clip(CircleShape).border(1.5.dp, CREME.copy(alpha = 0.45f), CircleShape)
                            .clickable(enabled = actif, onClickLabel = "Changer de caméra", role = Role.Button) { basculer() }
                            .semantics { contentDescription = "Changer de caméra" },
                        contentAlignment = Alignment.Center,
                    ) { IconeTrait(Ic.TOURNER, 25.dp, CREME, Modifier.graphicsLayer { rotationY = tour }) }
                }
            }
        }
        erreur?.let { e ->
            LaunchedEffect(e) {
                delay(4000)
                erreur = null
            }
            Carte(e, Ic.ALERTE, Modifier.align(Alignment.Center))
        }
        if (act.chaud) {
            Carte("Le téléphone chauffe : l'appareil photo est fermé le temps qu'il refroidisse. Posez-le à l'ombre.", Ic.ALERTE, Modifier.align(Alignment.Center))
        }
        BarreAvis(Modifier.align(Alignment.BottomCenter), bas = 300.dp)
        // Le tirage en vol, par-dessus tout.
        vol?.let { v ->
            androidx.compose.runtime.key(v) {
                Tirage(v, cadreVignette) {
                    if (vol === v) {
                        nouvelleVignette(v.image)
                        vol = null
                    }
                }
            }
        }
        code?.let { t -> Resultat(t) { code = null } }
        var garde by remember { mutableStateOf<ByteArray?>(null) }
        aGarder?.let { garde = it }
        androidx.compose.animation.AnimatedVisibility(aGarder != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
            garde?.let { octets -> Garder(octets, { aGarder = null }) { act.rendre(octets) } }
        }
    }
    if (reglages) {
        FeuilleAppli(fermer = { reglages = false }, titre = "Appareil photo") {
            LigneAppli("Grille", "Des lignes pour placer le sujet", fin = { InterAppli(grille) }) {
                grille = !grille
                c.getSharedPreferences("appareil", 0).edit().putBoolean("grille", grille).apply()
            }
            LigneAppli("Touches de volume", "Elles déclenchent la photo ou la vidéo", fin = { InterAppli(act.volumePourDeclencher) }) {
                act.volumePourDeclencher = !act.volumePourDeclencher
                c.getSharedPreferences("appareil", 0).edit().putBoolean("volume", act.volumePourDeclencher).apply()
            }
        }
    }
}

/** Le bandeau du haut : les réglages en mots, qu'un toucher fait défiler (flash, retardateur, format) ; le temps de tournage ; la lampe du lecteur de codes. */
@Composable
private fun Bandeau(
    mode: Mode,
    enregistre: Boolean,
    duree: Long,
    flashPossible: Boolean,
    flash: Flash,
    retardateur: Int,
    format: Format,
    lampe: Boolean,
    infoVideo: String?,
    pourUnCode: Boolean,
    changerFlash: () -> Unit,
    changerRetardateur: () -> Unit,
    changerFormat: () -> Unit,
    basculerLampe: () -> Unit,
    fermer: () -> Unit,
    reglages: () -> Unit,
) {
    AnimatedContent(
        when {
            enregistre -> 1
            mode == Mode.CODE -> 2
            else -> 0
        },
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(280), initialScale = 0.88f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.9f)) using SizeTransform(clip = false)
        },
        label = "bandeau",
    ) { etat ->
        when (etat) {
            1 -> {
                // On tourne : le temps, sur une pastille rouge dont le point bat.
                val battement = rememberInfiniteTransition(label = "rec")
                val clair by battement.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "clair")
                Row(Modifier.clip(RoundedCornerShape(20.dp)).background(ROUGE).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).graphicsLayer { alpha = clair }.clip(CircleShape).background(Color.White))
                    BasicText(chrono(duree), modifier = Modifier.padding(start = 9.dp), style = texte(17f, Color.White, FontWeight.Bold).copy(fontFeatureSettings = "tnum"))
                }
            }
            2 -> if (pourUnCode || flashPossible) Capsule {
                if (pourUnCode) Segment("Fermer", null, Icones.FERMER, false, fermer)
                if (pourUnCode && flashPossible) Trait()
                if (flashPossible) Segment("Lampe", if (lampe) "Allumée" else "Éteinte", Icones.LAMPE, lampe, basculerLampe)
            } else Box(Modifier.size(1.dp))
            else -> Capsule {
                AnimatedVisibility(flashPossible && mode != Mode.VIDEO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Segment(
                            "Flash", when (flash) { Flash.NON -> "Non"; Flash.AUTO -> "Auto"; Flash.OUI -> "Oui" },
                            if (flash == Flash.NON) Ic.SANS_FLASH else Ic.FLASH, flash == Flash.OUI, changerFlash,
                        )
                        Trait()
                    }
                }
                AnimatedVisibility(mode != Mode.VIDEO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Segment("Retardateur", if (retardateur == 0) "Non" else "$retardateur s", Ic.RETARDATEUR, retardateur > 0, changerRetardateur, retourner = true)
                        Trait()
                    }
                }
                AnimatedVisibility(mode == Mode.PHOTO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Segment("Format", format.nom, null, false, changerFormat)
                        Trait()
                    }
                }
                AnimatedVisibility(mode == Mode.VIDEO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(infoVideo ?: "Vidéo", modifier = Modifier.padding(horizontal = 14.dp), style = texte(13.5f, GRIS, FontWeight.Medium).copy(fontFeatureSettings = "tnum"))
                        Trait()
                    }
                }
                Segment("Réglages de l'appareil photo", null, Ic.REGLAGES, false, reglages)
            }
        }
    }
}

@Composable
private fun Capsule(contenu: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0x990E0D0B)).border(1.dp, Color(0x1FF4EEE4), RoundedCornerShape(22.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = contenu,
    )
}

@Composable
private fun Trait() {
    Box(Modifier.size(1.dp, 16.dp).background(Color(0x2EF4EEE4)))
}

/** Un réglage du bandeau : son icône et sa valeur, qui défile quand on touche. Le sablier se retourne. */
@Composable
private fun Segment(nom: String, valeur: String?, icone: String?, allume: Boolean, onClick: () -> Unit, retourner: Boolean = false) {
    val encre by animateColorAsState(if (allume) OR else CREME, label = "encre")
    val geste = remember { Animatable(0f) }
    var vu by remember { mutableStateOf(false) }
    LaunchedEffect(valeur) {
        if (!vu) {
            vu = true
            return@LaunchedEffect
        }
        if (retourner) {
            geste.animateTo(geste.targetValue + 180f, spring(dampingRatio = 0.6f, stiffness = 280f))
        } else {
            geste.snapTo(-0.3f)
            geste.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 600f))
        }
    }
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = if (valeur != null) "Changer" else nom, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = if (valeur != null) "$nom : $valeur" else nom }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) IconeTrait(
            icone, 18.dp, encre,
            Modifier.graphicsLayer {
                if (retourner) rotationZ = geste.value else {
                    scaleX = 1f + geste.value
                    scaleY = 1f + geste.value
                }
            },
        )
        if (valeur != null) {
            AnimatedContent(
                valeur,
                modifier = Modifier.padding(start = if (icone != null) 7.dp else 0.dp),
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) using SizeTransform(clip = true) },
                label = "valeur",
            ) { v -> BasicText(v, style = texte(14f, encre, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum")) }
        }
    }
}

/** La hauteur du bloc transparent des commandes, et la place du déclencheur dans ce bloc (depuis le bas). */
private val BLOC = 226.dp
private val CENTRE = 72.dp

/** Degrés de bague pour doubler l'agrandissement : 1× → 2× fait le même tour que 2× → 4×. */
private const val DOUBLE = 55f

private fun angleZoom(z: Float) = DOUBLE * ln(z.coerceAtLeast(0.01f)) / ln(2f)
private fun zoomDeAngle(a: Float) = 2f.pow(a / DOUBLE)

/**
 * Où l'image de la caméra (3:4) se pose sur l'écran, et la part que la photo gardera (le cadre, au rapport [r]) :
 * le cadre est aussi grand que l'écran le permet ; en plein écran, l'image déborde sur les côtés.
 */
private fun cadrage(r: Float, l: Float, h: Float, haut: Float): Pair<Rect, Rect> {
    val (cl, ch) = if (r > l / h) l to l / r else h * r to h
    val (fl, fh) = if (r <= 3f / 4) ch * 3f / 4 to ch else cl to cl * 4f / 3
    val y = minOf(haut, (h - fh) / 2).coerceAtLeast(0f)
    val flux = Rect((l - fl) / 2, y, (l + fl) / 2, y + fh)
    return flux to Rect(l / 2 - cl / 2, flux.center.y - ch / 2, l / 2 + cl / 2, flux.center.y + ch / 2)
}

/**
 * La bague autour du déclencheur, graduée comme celle d'un objectif. Elle choisit le mode : on la tourne du doigt
 * (ou on glisse l'image), le mode s'arrête sous le repère d'or. Après un toucher sur 1×, 2×… ou en pinçant l'image,
 * elle devient la bague du zoom : on la tourne pour agrandir, les repères attirent le doigt ; elle redevient celle des modes seule.
 */
@Composable
private fun Bague(
    modes: List<Mode>,
    mode: Mode,
    visible: Boolean,
    centreBas: Dp,
    modifier: Modifier,
    choisir: (Mode) -> Unit,
    enZoom: Boolean,
    zoom: Float,
    plage: android.util.Range<Float>?,
    zoomer: (Float, Boolean) -> Unit,
    toucher: () -> Unit,
) {
    val i = modes.indexOf(mode).coerceAtLeast(0)
    // En arrivant, la bague tourne jusqu'au mode.
    val angle = remember { Animatable(-i * PAS + 30f) }
    var tire by remember { mutableStateOf<Float?>(null) }
    var vibrer by remember { mutableStateOf(false) }
    val portee = rememberCoroutineScope()
    val vibreur = LocalHapticFeedback.current
    LaunchedEffect(mode) {
        angle.animateTo(-i * PAS, spring(dampingRatio = 0.7f, stiffness = 240f))
        vibrer = true
    }
    val rot = tire ?: angle.value
    // Un cran sous le doigt à chaque mode, à chaque repère de zoom qui passe sous le repère d'or.
    val proche = (-rot / PAS).roundToInt().coerceIn(0, modes.lastIndex)
    LaunchedEffect(proche) { if (vibrer && !enZoom) vibreur.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    val reperes = remember(plage) {
        if (plage == null) emptyList() else listOfNotNull(plage.lower.takeIf { it < 0.95f }, 1f, 2f, 3f, 5f, 10f, 20f).filter { it <= plage.upper + 0.01f }
    }
    val cranZoom = reperes.indexOfLast { it <= zoom + 0.01f }
    LaunchedEffect(cranZoom) { if (vibrer && enZoom) vibreur.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    val zoomActuel by rememberUpdatedState(zoom)
    val bagueZoom by rememberUpdatedState(enZoom && plage != null)
    val k by animateFloatAsState(if (enZoom && plage != null) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 300f), label = "zoom")
    val clair by animateFloatAsState(if (visible) 1f else 0f, tween(260), label = "bague")
    val mesureur = rememberTextMeasurer()
    val style = texte(12f, CREME, FontWeight.Bold).copy(letterSpacing = 1.8.sp)
    val etiquettes = remember(modes) { modes.map { mesureur.measure(it.nom.uppercase(), style) } }
    val etiquettesZoom = remember(reperes) { reperes.map { mesureur.measure(etiquetteZoom(it) + "×", style) } }
    Canvas(
        modifier.graphicsLayer { alpha = clair; val s = 0.94f + 0.06f * clair; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 1f) }
            .semantics {
                if (enZoom && plage != null) {
                    contentDescription = "Agrandissement : ${etiquetteZoom(zoom)} fois"
                    customActions = listOf(
                        CustomAccessibilityAction("Agrandir") { zoomer((zoom * 1.5f).coerceAtMost(plage.upper), true); toucher(); true },
                        CustomAccessibilityAction("Réduire") { zoomer((zoom / 1.5f).coerceAtLeast(plage.lower), true); toucher(); true },
                    )
                } else {
                    contentDescription = "Mode : ${mode.nom}"
                    customActions = modes.filter { it != mode }.map { m -> CustomAccessibilityAction("Passer en mode ${m.nom}") { choisir(m); true } }
                }
            }
            .pointerInput(modes, visible, plage) {
                if (!visible) return@pointerInput
                awaitEachGesture {
                    val rayon = rayonBague(size.height.toFloat(), centreBas.toPx(), density) + 4.dp.toPx()
                    val bas = awaitFirstDown(requireUnconsumed = false)
                    val centre = Offset(size.width / 2f, size.height - centreBas.toPx())
                    val p = if (bagueZoom) plage else null
                    val suivi = VelocityTracker()
                    suivi.addPosition(bas.uptimeMillis, bas.position)
                    val depart = angle.value
                    val departZoom = angleZoom(zoomActuel)
                    // L'angle du zoom posé par ce geste (l'état de l'écran n'est relu qu'à l'image suivante).
                    var angleTourne = departZoom
                    var dx = 0f
                    var glisse = false
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == bas.id } ?: break
                        // Le dernier mouvement compte aussi (celui du doigt qui se lève).
                        suivi.addPosition(ch.uptimeMillis, ch.position)
                        dx += ch.positionChange().x
                        if (!glisse && abs(dx) > viewConfiguration.touchSlop) {
                            glisse = true
                            vibrer = true
                        }
                        if (glisse) {
                            ch.consume()
                            val d = Math.toDegrees((dx / rayon).toDouble()).toFloat()
                            if (p != null) {
                                angleTourne = (departZoom - d).coerceIn(angleZoom(p.lower), angleZoom(p.upper))
                                zoomer(zoomDeAngle(angleTourne), false)
                                toucher()
                            } else {
                                tire = (depart + d).coerceIn(-modes.lastIndex * PAS - 16f, 16f)
                            }
                        }
                        if (!ch.pressed) break
                    }
                    if (glisse && p != null) {
                        // Un repère tout proche attire la bague.
                        val a = angleTourne
                        reperes.minByOrNull { abs(angleZoom(it) - a) }?.takeIf { abs(angleZoom(it) - a) < 4f }?.let { zoomer(it, true) }
                        toucher()
                    } else if (glisse) {
                        val fin = tire ?: angle.value
                        val elan = Math.toDegrees((suivi.calculateVelocity().x * 0.12f / rayon).toDouble()).toFloat()
                        val j = (-(fin + elan) / PAS).roundToInt().coerceIn(0, modes.lastIndex)
                        portee.launch {
                            angle.snapTo(fin)
                            tire = null
                            angle.animateTo(-j * PAS, spring(dampingRatio = 0.7f, stiffness = 260f))
                        }
                        choisir(modes[j])
                    } else {
                        // Un toucher sur un mot (ou un repère) de la bague.
                        val d = bas.position - centre
                        val th = Math.toDegrees(atan2(d.x.toDouble(), (-d.y).toDouble())).toFloat()
                        if (d.getDistance() in rayon - 40.dp.toPx()..rayon + 50.dp.toPx() && abs(th) < 60f) {
                            vibrer = true
                            if (p != null) {
                                val a = angleZoom(zoomActuel)
                                reperes.minByOrNull { abs(angleZoom(it) - a - th) }?.takeIf { abs(angleZoom(it) - a - th) < 18f }?.let { zoomer(it, true) }
                                toucher()
                            } else {
                                val j = ((th - angle.value) / PAS).roundToInt()
                                if (j in modes.indices && abs(th - angle.value - j * PAS) < PAS / 2) choisir(modes[j])
                            }
                        }
                    }
                }
            },
    ) {
        val centre = Offset(size.width / 2f, size.height - centreBas.toPx())
        val rTexte = rayonBague(size.height, centreBas.toPx(), density)
        val rTraits = rTexte - 16.dp.toPx()
        val bord = 74f
        // Un halo très doux derrière le déclencheur : la lumière dans l'objectif.
        drawCircle(Brush.radialGradient(listOf(Color(0x12F4EEE4), Color.Transparent), centre, 150.dp.toPx()), 150.dp.toPx(), centre)
        // Une graduation : plus marquée sur un mode ou un repère ; elles s'effacent vers les bords.
        fun trait(th: Float, majeur: Boolean, visibilite: Float) {
            if (abs(th) > bord) return
            rotate(th, centre) {
                drawLine(
                    CREME.copy(alpha = (if (majeur) 0.8f else 0.3f) * (1f - abs(th) / bord) * visibilite),
                    Offset(centre.x, centre.y - rTraits), Offset(centre.x, centre.y - rTraits - (if (majeur) 8.dp else 4.5.dp).toPx()),
                    1.3.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
        // Un mot écrit le long de la bague, d'or sous le repère.
        fun mot(t: TextLayoutResult, th: Float, visibilite: Float) {
            if (abs(th) > bord) return
            val pres = (1f - abs(th) / (PAS * 0.6f)).coerceIn(0f, 1f)
            rotate(th, centre) {
                drawText(t, lerp(GRIS, OR, pres), Offset(centre.x - t.size.width / 2f, centre.y - rTexte - t.size.height), alpha = (1f - abs(th) / bord).coerceIn(0f, 1f) * visibilite)
            }
        }
        // Les modes (ils s'en vont en tournant quand la bague passe au zoom).
        if (k < 1f) {
            val r0 = rot + 28f * k
            val cran = PAS / 5f
            for (n in floor((-bord - r0) / cran).toInt()..ceil((bord - r0) / cran).toInt()) trait(n * cran + r0, n % 5 == 0 && n / 5 in modes.indices, 1f - k)
            etiquettes.forEachIndexed { j, t -> mot(t, j * PAS + r0, 1f - k) }
        }
        // Le zoom : l'agrandissement actuel sous le repère d'or.
        if (k > 0f && plage != null && reperes.isNotEmpty()) {
            val rz = -angleZoom(zoom) - 28f * (1f - k)
            val cran = DOUBLE / 5f
            for (n in ceil(angleZoom(plage.lower) / cran).toInt()..floor(angleZoom(plage.upper) / cran).toInt()) trait(n * cran + rz, false, k)
            reperes.forEachIndexed { j, v ->
                trait(angleZoom(v) + rz, true, k)
                mot(etiquettesZoom[j], angleZoom(v) + rz, k)
            }
        }
        // Le repère d'or, fixe.
        drawLine(OR, Offset(centre.x, centre.y - rTraits + 5.dp.toPx()), Offset(centre.x, centre.y - rTraits - 10.dp.toPx()), 2.2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Les niveaux d'agrandissement (0,5 · 1× · 2 · 5) : un toucher y glisse et ouvre la bague du zoom. */
@Composable
private fun Niveaux(zoom: Float, plage: android.util.Range<Float>, modifier: Modifier, choisir: (Float) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(24.dp)).background(VOILE_NOIR).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val niveaux = listOfNotNull(plage.lower.takeIf { it < 1f }, 1f, 2f.takeIf { plage.upper >= 2f }, 5f.takeIf { plage.upper >= 5f })
        niveaux.forEachIndexed { i, z ->
            val suivant = niveaux.getOrNull(i + 1) ?: Float.MAX_VALUE
            val ici = zoom >= z - 0.04f && zoom < suivant - 0.04f
            val taille by animateDpAsState(if (ici) 40.dp else 34.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "taille")
            Box(
                Modifier.size(taille).clip(CircleShape).background(if (ici) Color(0x40E6DAC2) else Color.Transparent)
                    .clickable(onClickLabel = "Agrandir ${etiquetteZoom(z)} fois", role = Role.Button) { choisir(z) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(if (ici) etiquetteZoom(zoom) + "×" else etiquetteZoom(z), style = texte(if (ici) 13.5f else 12.5f, if (ici) OR else CREME, FontWeight.Bold))
            }
        }
    }
}

/** Le rayon des mots de la bague : la place au-dessus du déclencheur, entre 104 et 140 dp. */
private fun rayonBague(hauteur: Float, centreBas: Float, densite: Float): Float =
    (hauteur - centreBas - 40f * densite).coerceIn(104f * densite, 140f * densite)

/** Le déclencheur : un disque crème dans un anneau ; à chaque photo, un diaphragme s'y ferme et se rouvre. Rouge pour filmer, carré pendant qu'on filme, l'anneau fait alors le tour en une minute. */
@Composable
private fun Declencheur(mode: Mode, enregistre: Boolean, prete: Boolean, tirages: Int, debutFilm: Long, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val presse by source.collectIsPressedAsState()
    val code = mode == Mode.CODE
    val echelle by animateFloatAsState(if (presse) 0.86f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "presse")
    val couleur by animateColorAsState(if (mode == Mode.VIDEO) ROUGE else CREME, tween(260), label = "couleur")
    val taille by animateDpAsState(if (code) 0.dp else if (enregistre) 30.dp else 70.dp, spring(dampingRatio = 0.72f, stiffness = 480f), label = "taille")
    val coin by animateDpAsState(if (enregistre) 8.dp else 35.dp, spring(dampingRatio = 0.75f, stiffness = 500f), label = "coin")
    val anneau by animateColorAsState(if (code) OR.copy(alpha = 0.5f) else if (prete) CREME else CREME.copy(alpha = 0.4f), label = "anneau")
    val iris = remember { Animatable(0f) }
    LaunchedEffect(tirages) {
        if (tirages > 0) {
            iris.animateTo(1f, tween(110, easing = FastOutLinearInEasing))
            iris.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f))
        }
    }
    val debut by rememberUpdatedState(debutFilm)
    var tour by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enregistre) {
        tour = 0f
        while (enregistre) withFrameMillis { tour = ((SystemClock.elapsedRealtime() - debut) % 60_000L) / 60_000f }
    }
    val nom = when {
        mode == Mode.VIDEO && enregistre -> "Arrêter la vidéo"
        mode == Mode.VIDEO -> "Filmer"
        mode == Mode.DOCUMENT -> "Scanner la page"
        else -> "Prendre une photo"
    }
    Box(
        Modifier.size(90.dp)
            .clickable(interactionSource = source, indication = null, enabled = prete && !code, onClickLabel = nom, role = Role.Button, onClick = onClick)
            .semantics { if (!code) contentDescription = nom },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val e = 3.dp.toPx()
            drawCircle(anneau, size.minDimension / 2 - e, style = Stroke(e))
            if (tour > 0f) drawArc(ROUGE, -90f, 360f * tour, false, topLeft = Offset(e, e), size = Size(size.width - 2 * e, size.height - 2 * e), style = Stroke(e, cap = StrokeCap.Round))
        }
        Box(
            Modifier.size(taille).graphicsLayer { scaleX = echelle; scaleY = echelle }.clip(RoundedCornerShape(coin)).background(couleur)
                .drawWithContent {
                    drawContent()
                    if (iris.value > 0f) diaphragme(iris.value)
                },
            contentAlignment = Alignment.Center,
        ) { if (mode == Mode.DOCUMENT) IconeTrait(Ic.DOCUMENT, 26.dp, SOMBRE) }
        // Le lecteur de codes n'a pas de déclencheur : il lit tout seul.
        androidx.compose.animation.AnimatedVisibility(code, enter = fadeIn(tween(220, delayMillis = 120)) + scaleIn(initialScale = 0.6f), exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f)) { Lecture() }
    }
}

@Composable
private fun Lecture() {
    val t = rememberInfiniteTransition(label = "lecture")
    val s by t.animateFloat(0.9f, 1.05f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Box(Modifier.size(66.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(Color(0x1FF2C14E)), contentAlignment = Alignment.Center) {
        IconeTrait(Ic.QR, 26.dp, OR)
    }
}

/** Le diaphragme : six lames qui se referment en tournant sur le disque du déclencheur. */
private fun DrawScope.diaphragme(k: Float) {
    val r = size.minDimension / 2f
    val c = center
    val ouverture = r * (1.17f - 0.95f * k)
    val tourne = 50f * k
    val pts = List(6) { n ->
        val t = Math.toRadians((tourne + n * 60f).toDouble())
        Offset(c.x + ouverture * cos(t).toFloat(), c.y + ouverture * sin(t).toFloat())
    }
    val trou = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (n in 1 until 6) lineTo(pts[n].x, pts[n].y)
        close()
    }
    val lames = Path().apply {
        addOval(Rect(c, r))
        addPath(trou)
        fillType = PathFillType.EvenOdd
    }
    drawPath(lames, SOMBRE)
    for (n in 0 until 6) {
        val p = pts[n]
        val q = pts[(n + 1) % 6]
        val d = (q - p) / (q - p).getDistance()
        drawLine(Color(0xFF4A443B), q, q + d * (2f * r), 1.dp.toPx())
    }
}

/** La photo prise quitte le cadre, bordée comme un tirage, et file se poser sur la vignette. */
@Composable
private fun Tirage(v: Vol, vers: Rect, arrive: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(680, easing = LinearEasing))
        arrive()
    }
    val d = LocalDensity.current
    val k = p.value
    val sortie = FastOutSlowInEasing.transform((k / 0.25f).coerceIn(0f, 1f))
    val trajet = FastOutSlowInEasing.transform(((k - 0.2f) / 0.8f).coerceIn(0f, 1f))
    val cible = if (vers.width > 0f) vers else Rect(v.de.center, 1f)
    val depart = 1f - 0.08f * sortie
    val w = melange(v.de.width * depart, cible.width, trajet)
    val h = melange(v.de.height * depart, cible.height, trajet)
    val arc = sin(PI * trajet).toFloat()
    val cx = melange(v.de.center.x, cible.center.x, trajet)
    val cy = melange(v.de.center.y, cible.center.y, trajet) - arc * with(d) { 36.dp.toPx() }
    val coin = melange(22f, 14f, trajet).dp
    Box(
        Modifier.offset { IntOffset((cx - w / 2).roundToInt(), (cy - h / 2).roundToInt()) }
            .size(with(d) { w.toDp() }, with(d) { h.toDp() })
            .graphicsLayer { rotationZ = -10f * arc }
            .clip(RoundedCornerShape(coin))
            .border((3f * sortie * (1f - 0.35f * trajet)).coerceAtLeast(0.5f).dp, CREME, RoundedCornerShape(coin)),
    ) { Image(v.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = if (v.document) FILTRE_DOCUMENT else null) }
}

/** Les dernières photos, en pile : la nouvelle se pose dessus, l'ancienne penche derrière. */
@Composable
private fun Vignette(image: ImageBitmap?, precedente: ImageBitmap?, visible: Boolean, modifier: Modifier, ouvrir: () -> Unit) {
    val clair by animateFloatAsState(if (visible) 1f else 0f, label = "clair")
    val penche = remember { Animatable(0f) }
    LaunchedEffect(precedente) {
        if (precedente != null) {
            penche.snapTo(0f)
            penche.animateTo(-11f, spring(dampingRatio = 0.45f, stiffness = 260f))
        }
    }
    val arrivee = remember { Animatable(1f) }
    LaunchedEffect(image) {
        if (image != null) {
            arrivee.snapTo(1.12f)
            arrivee.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
        }
    }
    val forme = RoundedCornerShape(14.dp)
    Box(modifier.size(56.dp).graphicsLayer { alpha = clair }) {
        precedente?.let {
            Image(
                it, null,
                Modifier.matchParentSize().graphicsLayer { rotationZ = penche.value; transformOrigin = TransformOrigin(0.5f, 0.9f) }.clip(forme).border(2.dp, CREME.copy(alpha = 0.45f), forme),
                contentScale = ContentScale.Crop, alpha = 0.7f,
            )
        }
        Box(
            Modifier.matchParentSize().graphicsLayer { scaleX = arrivee.value; scaleY = arrivee.value }.clip(forme).background(SOMBRE).border(2.dp, CREME.copy(alpha = 0.9f), forme)
                .clickable(enabled = image != null && visible, onClickLabel = "Voir la dernière photo", onClick = ouvrir)
                .semantics { contentDescription = "Dernière photo" },
        ) { image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
    }
}

/** Le cadre du lecteur de codes (une ligne d'or le balaie) ou de la page à scanner, au milieu de ce qu'on voit ([zone]). */
@Composable
private fun Cadre(document: Boolean, zone: Rect) {
    val balayage = rememberInfiniteTransition(label = "balayage")
    val k by balayage.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
    Canvas(Modifier.fillMaxSize()) {
        val r = if (document) Rect(zone.left + zone.width * 0.08f, zone.top + zone.height * 0.06f, zone.right - zone.width * 0.08f, zone.bottom - zone.height * 0.06f)
        else minOf(zone.width * 0.66f, zone.height * 0.62f).let { cote -> Rect(zone.center.x - cote / 2, zone.center.y - cote / 2, zone.center.x + cote / 2, zone.center.y + cote / 2) }
        // Hors du cadre, l'image est assombrie.
        val dehors = androidx.compose.ui.graphics.Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
            addRoundRect(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())))
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
        drawPath(dehors, Color(0x660E0D0B))
        val l = 30.dp.toPx()
        val e = 4.dp.toPx()
        listOf(r.topLeft to Offset(1f, 1f), r.topRight to Offset(-1f, 1f), r.bottomLeft to Offset(1f, -1f), r.bottomRight to Offset(-1f, -1f)).forEach { (o, s) ->
            drawLine(CREME, o, o + Offset(l * s.x, 0f), e, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(CREME, o, o + Offset(0f, l * s.y), e, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        if (!document) {
            val y = r.top + 12.dp.toPx() + (r.height - 24.dp.toPx()) * k
            drawLine(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, OR, Color.Transparent), r.left, r.right),
                Offset(r.left + 10.dp.toPx(), y), Offset(r.right - 10.dp.toPx(), y), 2.5.dp.toPx(),
            )
        }
    }
}

/** La mise au point : un cercle d'or qui se resserre là où l'on a touché ; à côté, le soleil de la lumière. */
@Composable
private fun Mise(f: Offset, ev: Int, plage: android.util.Range<Int>) {
    val d = LocalDensity.current
    val rayon = remember(f) { Animatable(1.35f) }
    LaunchedEffect(f) { rayon.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
    val px = with(d) { 36.dp.toPx() }
    Box(Modifier.offset { IntOffset((f.x - px).roundToInt(), (f.y - px).roundToInt()) }) {
        Box(Modifier.size(72.dp).graphicsLayer { scaleX = rayon.value; scaleY = rayon.value }.border(2.dp, OR, CircleShape))
        if (plage.upper > plage.lower) {
            // Le soleil monte quand on éclaircit, descend quand on assombrit.
            val part = (ev - plage.lower).toFloat() / (plage.upper - plage.lower)
            Column(Modifier.offset(x = 84.dp).height(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f - part + 0.001f).width(1.5.dp).background(OR.copy(alpha = 0.7f)))
                IconeTrait(Icones.SOLEIL, 18.dp, OR)
                Box(Modifier.weight(part + 0.001f).width(1.5.dp).background(OR.copy(alpha = 0.7f)))
            }
        }
    }
}

@Composable
private fun Carte(t: String, icone: String, modifier: Modifier) {
    Row(modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF21F1C18)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        IconeTrait(icone, 22.dp, Color(0xFFEE9A78))
        BasicText(t, modifier = Modifier.padding(start = 12.dp), style = texte(15f, CREME))
    }
}

@Composable
private fun Permission(autoriser: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF151412)).padding(30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0x24FFFFFF)), contentAlignment = Alignment.Center) { IconeTrait(Icones.APPAREIL, 38.dp, CREME) }
        BasicText("L'Appareil photo a besoin de la caméra", modifier = Modifier.padding(top = 20.dp), style = texte(19f, CREME, FontWeight.Bold).copy(textAlign = TextAlign.Center))
        BasicText("Les photos et les vidéos restent sur ce téléphone.", modifier = Modifier.padding(top = 8.dp), style = texte(15f, Color(0xFFB3AA9C)).copy(textAlign = TextAlign.Center))
        Box(Modifier.padding(top = 22.dp).clip(RoundedCornerShape(24.dp)).background(CREME).clickable(role = Role.Button, onClick = autoriser).padding(horizontal = 26.dp, vertical = 14.dp)) {
            BasicText("Autoriser la caméra", style = texte(16f, Color(0xFF1F1C18), FontWeight.Bold))
        }
    }
}

private fun etiquetteZoom(z: Float): String = if (z < 1f) String.format(java.util.Locale.FRENCH, "%.1f", z) else if (abs(z - z.roundToInt()) < 0.05f) "${z.roundToInt()}" else String.format(java.util.Locale.FRENCH, "%.1f", z)

private fun chrono(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** « reste 1 h 10 » : la place libre divisée par le débit de la vidéo. */
private fun resteVideo(bitsParSeconde: Int): String {
    val libre = StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
    val s = libre * 8 / bitsParSeconde.coerceAtLeast(1)
    val h = s / 3600
    val m = s / 60 % 60
    return "reste " + if (h > 0) "$h h ${"%02d".format(m)}" else "$m min"
}

/** L'appli qui a demandé une photo la reçoit seulement si on la garde : la photo, puis « Reprendre » ou le disque d'or « Utiliser la photo ». */
@Composable
private fun Garder(octets: ByteArray, reprendre: () -> Unit, garder: () -> Unit) {
    val image = remember(octets) { Pellicule.image(octets, 2) }
    val entree = remember(octets) { Animatable(0f) }
    LaunchedEffect(octets) { entree.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }
    Column(Modifier.fillMaxSize().background(NUIT).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)).navigationBarsPadding()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                .graphicsLayer { val s = 0.92f + 0.08f * entree.value; scaleX = s; scaleY = s; alpha = entree.value }
                .clip(RoundedCornerShape(26.dp)),
        ) { image?.let { Image(it, "La photo prise", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
        Box(Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 26.dp)) {
            Column(
                Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = "Reprendre", role = Role.Button, onClick = reprendre).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(56.dp).border(1.5.dp, CREME.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) { IconeTrait(Icones.RETOUR, 22.dp, CREME) }
                BasicText("Reprendre", modifier = Modifier.padding(top = 8.dp), style = texte(13.5f, CREME, FontWeight.SemiBold))
            }
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = "Utiliser la photo", role = Role.Button, onClick = garder).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(90.dp).border(3.dp, CREME, CircleShape).padding(9.dp).clip(CircleShape).background(OR), contentAlignment = Alignment.Center) {
                    IconeTrait(Icones.COCHE, 30.dp, SOMBRE, epaisseur = 2.2f)
                }
                BasicText("Utiliser la photo", modifier = Modifier.padding(top = 8.dp), style = texte(13.5f, OR, FontWeight.Bold))
            }
        }
    }
}

/** Ce que le code contient, dit clairement, avec ce qu'on peut en faire — et ce qui doit inquiéter. */
@Composable
private fun Resultat(t: String, fermer: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val r = Code.lire(t)
    val rouge = Color(0xFFEE9A78)
    FeuilleAppli(fermer = fermer) {
        val fermerFeuille = LocalFermerFeuille.current
        Column(Modifier.padding(horizontal = 22.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (r.prudence != null) rouge.copy(alpha = 0.18f) else a.champ), contentAlignment = Alignment.Center) {
                    IconeTrait(if (r.prudence != null) Ic.ALERTE else r.icone, 24.dp, if (r.prudence != null) rouge else a.encre)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    BasicText("Lu dans un code QR", style = texte(13f, a.encre2, FontWeight.SemiBold))
                    BasicText(r.titre, maxLines = 2, style = texte(18f, a.encre, FontWeight.Bold))
                }
            }
            if (r.detail.isNotBlank()) {
                BasicText(r.detail, maxLines = 4, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(a.champ).padding(14.dp), style = texte(14.5f, a.encre))
            }
            r.prudence?.let {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(rouge.copy(alpha = 0.14f)).padding(14.dp)) {
                    IconeTrait(Icones.BOUCLIER, 20.dp, rouge)
                    BasicText(it, modifier = Modifier.padding(start = 10.dp), style = texte(14.5f, rouge))
                }
            }
            Row(Modifier.padding(top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                r.action?.let { (nom, intent) ->
                    BoutonTexteAppli(if (r.prudence != null) "$nom quand même" else nom, style = if (r.prudence != null) 's' else ' ') {
                        try {
                            c.startActivity(intent)
                        } catch (_: Exception) {
                        }
                        fermerFeuille()
                    }
                }
                BoutonTexteAppli("Copier", style = 's') {
                    c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Code", t))
                    Avis.dire("Copié")
                    fermerFeuille()
                }
            }
        }
    }
}

/** Ce qu'un QR code veut dire. */
object Code {
    class Sens(val titre: String, val detail: String, val icone: String, val action: Pair<String, Intent>?, val prudence: String? = null)

    private val RACCOURCISSEURS = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "cutt.ly", "rb.gy", "shorturl.at", "tiny.cc")

    fun lire(t: String): Sens {
        val u = t.trim()
        return when {
            u.startsWith("http://", true) || u.startsWith("https://", true) -> {
                val uri = Uri.parse(u)
                val hote = uri.host.orEmpty()
                val ascii = try {
                    java.net.IDN.toASCII(hote)
                } catch (_: Exception) {
                    hote
                }
                val prudence = when {
                    ascii.split('.').any { it.startsWith("xn--") } -> "Ce lien utilise des lettres d'un autre alphabet pour imiter un site connu ($ascii). N'y tapez ni code ni mot de passe."
                    hote.removePrefix("www.") in RACCOURCISSEURS -> "Lien raccourci : on ne voit pas où il mène. Ouvrez-le seulement si vous connaissez la personne qui l'a affiché."
                    u.startsWith("http://", true) -> "Site non chiffré : n'y tapez ni code secret ni mot de passe."
                    else -> null
                }
                Sens("Lien vers ${hote.removePrefix("www.")}", u, Ic.LIEN, "Ouvrir" to Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), prudence)
            }
            u.startsWith("WIFI:", true) -> {
                val nom = Regex("S:((?:\\\\.|[^;])*)").find(u)?.groupValues?.get(1)?.replace("\\", "") ?: "Wi-Fi"
                val mdp = Regex("P:((?:\\\\.|[^;])*)").find(u)?.groupValues?.get(1)?.replace("\\", "")
                val type = Regex("T:([^;]*)").find(u)?.groupValues?.get(1).orEmpty()
                val suggestion = android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(nom).apply {
                    if (!mdp.isNullOrEmpty()) {
                        if (type.equals("SAE", true)) setWpa3Passphrase(mdp) else setWpa2Passphrase(mdp)
                    }
                }.build()
                val i = Intent(android.provider.Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(android.provider.Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Sens("Réseau Wi-Fi « $nom »", if (mdp.isNullOrEmpty()) "Sans mot de passe" else "Avec mot de passe", Icones.WIFI, "Se connecter" to i)
            }
            u.startsWith("tel:", true) -> {
                val n = u.substring(4)
                // Un code d'opérateur (*…#) dans un QR : il peut lancer un transfert d'argent.
                if (n.contains('*') || n.contains('#') || n.contains("%23")) {
                    Sens(
                        "Code d'opérateur", Uri.decode(n), Icones.TELEPHONE,
                        "Voir le code" to Intent(Intent.ACTION_DIAL, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        "Ce code lance une opération chez votre opérateur (transfert, paiement…). Le téléphone l'affiche sans le lancer : vérifiez le montant et le bénéficiaire. Personne ne doit vous demander votre code secret.",
                    )
                } else {
                    Sens("Numéro de téléphone", n, Icones.TELEPHONE, "Appeler" to Intent(Intent.ACTION_DIAL, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            u.startsWith("smsto:", true) || u.startsWith("sms:", true) -> {
                val parties = u.substringAfter(':').split(':', limit = 2)
                Sens(
                    "Message à ${parties[0]}", parties.getOrNull(1) ?: "", Icones.MESSAGE,
                    "Écrire" to Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${parties[0]}")).putExtra("sms_body", parties.getOrNull(1)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            u.startsWith("mailto:", true) -> Sens("Adresse e-mail", u.substring(7), Icones.MESSAGE, "Écrire" to Intent(Intent.ACTION_SENDTO, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // Le code d'un téléphone Sama, pour échanger de proche en proche : les Réglages le reconnaissent.
            u.startsWith("samaos:proche?", true) -> {
                val nom = u.substringAfter("n=", "").substringBefore('&').let { Uri.decode(it) }.ifBlank { "un proche" }
                Sens(
                    "Téléphone " + (if (nom.firstOrNull()?.lowercaseChar() in "aeiouyhéèêàâîôû".toList()) "d'$nom" else "de $nom"),
                    "Proche en proche : échanger des applis sans data", Icones.PROXIMITE,
                    "Reconnaître" to Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage("africa.samaos.reglages").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Ne reconnaissez que le téléphone d'un proche qui est devant vous, jamais un code reçu en photo.",
                )
            }
            u.startsWith("geo:", true) -> Sens("Un lieu", u.substring(4), Icones.BOUSSOLE, "Voir sur la carte" to Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            u.startsWith("BEGIN:VCARD", true) -> {
                val nom = Regex("\\nFN:(.*)").find(u)?.groupValues?.get(1)?.trim() ?: "Un contact"
                Sens(nom, "Carte de visite", Icones.PERSONNE, null)
            }
            else -> Sens("Texte", u, Icones.TEXTE, null)
        }
    }
}
