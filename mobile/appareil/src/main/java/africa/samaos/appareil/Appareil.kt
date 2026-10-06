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
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class Mode(val nom: String) { PHOTO("Photo"), VIDEO("Vidéo"), SCANNER("Scanner") }
private enum class Format(val nom: String, val ratio: Float) { TROIS_QUATRE("3:4", 3f / 4), NEUF_SEIZE("9:16", 9f / 16), CARRE("1:1", 1f) }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumePourDeclencher = getSharedPreferences("appareil", MODE_PRIVATE).getBoolean("volume", true)
        setContent { CompositionLocalProvider(LocalIdentite provides Identites.Appareil) { Ecran(this) } }
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

    fun vignette(octets: ByteArray): ImageBitmap? = try {
        BitmapFactory.decodeByteArray(octets, 0, octets.size, BitmapFactory.Options().apply { inSampleSize = 16 })?.let { b ->
            val o = ExifInterface(octets.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            val angle = when (o) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            (if (angle != 0f) Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(angle) }, true) else b).asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

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

    var mode by remember { mutableStateOf(Mode.PHOTO) }
    var idCamera by remember { mutableStateOf(arriere ?: avant) }
    var texture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var prete by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(Flash.NON) }
    var retardateur by remember { mutableIntStateOf(0) }
    var format by remember { mutableStateOf(Format.TROIS_QUATRE) }
    var grille by remember { mutableStateOf(c.getSharedPreferences("appareil", 0).getBoolean("grille", false)) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var focus by remember { mutableStateOf<Offset?>(null) }
    var compte by remember { mutableIntStateOf(0) }
    var eclair by remember { mutableStateOf(false) }
    var derniere by remember { mutableStateOf<Pair<Uri, ImageBitmap?>?>(null) }
    var filme by remember { mutableStateOf<Uri?>(null) }
    var debutFilm by remember { mutableLongStateOf(0L) }
    var maintenant by remember { mutableLongStateOf(0L) }
    var document by remember { mutableStateOf(false) }
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
            if (t != null) code = t else erreur = "Pas de code QR lisible dans cette image."
        }
    }

    // La caméra s'ouvre quand tout est là : l'autorisation, l'écran au premier plan, la surface de l'aperçu.
    LaunchedEffect(permis, act.actif, act.chaud, texture, idCamera, mode == Mode.SCANNER) {
        prete = false
        val st = texture
        val i = idCamera
        if (permis && act.actif && !act.chaud && st != null && i != null) {
            camera.ouvrir(
                i, st, if (mode == Mode.SCANNER) Usage.SCANNER else Usage.PHOTO,
                pret = { portee.launch { prete = true; zoom = 1f } },
                scan = { t -> portee.launch { if (code == null) code = t } },
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

    fun prendre() {
        if (!prete) return
        portee.launch {
            for (s in retardateur downTo 1) {
                compte = s
                delay(1000)
            }
            compte = 0
            son.play(MediaActionSound.SHUTTER_CLICK)
            eclair = true
            camera.photo(flash) { octets ->
                portee.launch {
                    eclair = false
                    val pret = withContext(Dispatchers.Default) { Pellicule.preparer(octets, format.ratio, document) }
                    if (act.pourUneAppli) {
                        aGarder = pret
                        return@launch
                    }
                    val uri = withContext(Dispatchers.IO) { Pellicule.enregistrer(c, pret, document) }
                    if (uri != null) derniere = uri to withContext(Dispatchers.Default) { Pellicule.vignette(pret) }
                    if (document) code = "§document"
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
                    derniere = u to withContext(Dispatchers.IO) {
                        try {
                            c.contentResolver.loadThumbnail(u, android.util.Size(200, 200), null).asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
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
                    filme = u
                    debutFilm = SystemClock.elapsedRealtime()
                } else {
                    erreur = "La vidéo n'a pas pu démarrer."
                    c.contentResolver.delete(u, null, null)
                }
            }
        }
    }
    act.declencher = { if (mode == Mode.VIDEO) filmer() else if (mode == Mode.PHOTO || document) prendre() }

    Box(Modifier.fillMaxSize().background(a.fond)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // ——— En haut : les réglages du moment ———
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    mode == Mode.SCANNER -> {
                        Rond(Icones.FERMER, "Fermer le scanner") { mode = Mode.PHOTO; document = false; code = null }
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.clip(RoundedCornerShape(22.dp)).background(a.champ).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Segment("Code QR", Ic.QR, !document) { document = false; code = null }
                            Segment("Document", Ic.DOCUMENT, document) { document = true; code = null }
                        }
                        Spacer(Modifier.weight(1f))
                        if (camera.takeIf { prete }?.flashPossible == true) Rond(if (lampe) Ic.FLASH else Ic.SANS_FLASH, if (lampe) "Éteindre la lampe" else "Allumer la lampe") {
                            lampe = !lampe
                            camera.lampe(lampe)
                        } else Spacer(Modifier.size(48.dp))
                    }
                    filme != null -> {
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE5533D)))
                        Spacer(Modifier.width(8.dp))
                        BasicText(chrono(maintenant - debutFilm), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = a.encre))
                        Spacer(Modifier.weight(1f))
                    }
                    mode == Mode.VIDEO -> {
                        val p = remember(prete, idCamera) { if (prete) camera.profil() else null }
                        Spacer(Modifier.weight(1f))
                        BasicText(
                            p?.let { "${it.videoFrameHeight}p · ${it.videoFrameRate} i/s · ${resteVideo(c, it.videoBitRate + it.audioBitRate)}" } ?: "",
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2),
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    else -> {
                        if (prete && camera.flashPossible) Rond(
                            if (flash == Flash.NON) Ic.SANS_FLASH else Ic.FLASH,
                            when (flash) { Flash.NON -> "Flash désactivé"; Flash.AUTO -> "Flash automatique"; Flash.OUI -> "Flash activé" },
                            badge = if (flash == Flash.AUTO) "A" else null,
                        ) { flash = Flash.entries[(flash.ordinal + 1) % 3] } else Spacer(Modifier.size(48.dp))
                        Spacer(Modifier.weight(1f))
                        Rond(Ic.RETARDATEUR, if (retardateur == 0) "Retardateur désactivé" else "Retardateur $retardateur secondes", badge = if (retardateur > 0) "$retardateur" else null) {
                            retardateur = when (retardateur) { 0 -> 3; 3 -> 10; else -> 0 }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, a.trait, RoundedCornerShape(16.dp))
                                .clickable(onClickLabel = "Format ${format.nom}", role = Role.Button) { format = Format.entries[(format.ordinal + 1) % Format.entries.size] }
                                .semantics { contentDescription = "Format ${format.nom}" }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { BasicText(format.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.encre)) }
                        Spacer(Modifier.weight(1f))
                        Rond(Ic.REGLAGES, "Réglages de l'appareil photo") { reglages = true }
                    }
                }
            }
            // ——— L'aperçu ———
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(3f / 4).clip(RoundedCornerShape(0.dp))) {
                val densite = LocalDensity.current
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
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
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(prete) {
                            detectTapGestures { p ->
                                focus = p
                                camera.mettreAuPoint(p.x / size.width, p.y / size.height)
                            }
                        }
                        .pointerInput(prete) {
                            detectTransformGestures { _, _, z, _ ->
                                if (z != 1f && prete) {
                                    zoom = (zoom * z).coerceIn(camera.zooms.lower, camera.zooms.upper)
                                    camera.zoomer(zoom)
                                }
                            }
                        },
                )
                // Le format choisi : ce qui sortira du cadre est voilé.
                if (mode == Mode.PHOTO && format != Format.TROIS_QUATRE) {
                    val hVisible = maxWidth / format.ratio
                    if (hVisible < maxHeight) {
                        val bande = (maxHeight - hVisible) / 2
                        Box(Modifier.fillMaxWidth().height(bande).background(Color(0xCC0E0D0B)))
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bande).background(Color(0xCC0E0D0B)))
                    } else {
                        val bande = (maxWidth - maxHeight * format.ratio) / 2
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.width(bande).fillMaxSize().background(Color(0xCC0E0D0B)))
                            Box(Modifier.align(Alignment.CenterEnd).width(bande).fillMaxSize().background(Color(0xCC0E0D0B)))
                        }
                    }
                }
                if (grille && mode != Mode.SCANNER) {
                    Canvas(Modifier.fillMaxSize()) {
                        for (i in 1..2) {
                            drawLine(Color(0x66FFFFFF), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1f)
                            drawLine(Color(0x66FFFFFF), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1f)
                        }
                    }
                }
                if (mode == Mode.SCANNER) {
                    // Les coins du cadre où poser le code ou la page.
                    Canvas(Modifier.fillMaxSize()) {
                        val m = size.width * 0.18f
                        val l = 26.dp.toPx()
                        val r = androidx.compose.ui.geometry.Rect(m, size.height * 0.16f, size.width - m, size.height * 0.84f).let { if (document) androidx.compose.ui.geometry.Rect(size.width * 0.08f, size.height * 0.06f, size.width * 0.92f, size.height * 0.94f) else it }
                        listOf(r.topLeft to Offset(1f, 1f), r.topRight to Offset(-1f, 1f), r.bottomLeft to Offset(1f, -1f), r.bottomRight to Offset(-1f, -1f)).forEach { (o, s) ->
                            drawLine(Color(0xFFF4EEE4), o, o + Offset(l * s.x, 0f), 4.dp.toPx())
                            drawLine(Color(0xFFF4EEE4), o, o + Offset(0f, l * s.y), 4.dp.toPx())
                        }
                    }
                }
                focus?.let { f ->
                    LaunchedEffect(f) {
                        delay(1200)
                        focus = null
                    }
                    val px = with(densite) { 36.dp.toPx() }
                    Box(Modifier.offset { IntOffset((f.x - px).roundToInt(), (f.y - px).roundToInt()) }.size(72.dp).border(2.dp, Color(0xFFF4EEE4), CircleShape))
                }
                if (compte > 0) {
                    BasicText("$compte", modifier = Modifier.align(Alignment.Center), style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(250), fontSize = 120.sp, color = Color(0xFFF4EEE4)))
                }
                if (eclair) Box(Modifier.fillMaxSize().background(Color(0x55000000)))
                // Les niveaux d'agrandissement.
                if (prete && mode != Mode.SCANNER && camera.zooms.upper > 1f) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x730E0D0B)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOfNotNull(camera.zooms.lower.takeIf { it < 1f }, 1f, 2f.takeIf { camera.zooms.upper >= 2f }).forEach { z ->
                            val ici = kotlin.math.abs(zoom - z) < 0.05f
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).then(if (ici) Modifier.background(Color(0x40E6DAC2)) else Modifier)
                                    .clickable(onClickLabel = "Agrandir ${etiquetteZoom(z)}", role = Role.Button) { zoom = z; camera.zoomer(z) },
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(
                                    if (ici) etiquetteZoom(zoom) + "×" else etiquetteZoom(z),
                                    style = TextStyle(fontFamily = Polices.corps, fontWeight = if (ici) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp, color = if (ici) a.accent else a.encre),
                                )
                            }
                        }
                    }
                }
                if (!permis) {
                    Column(Modifier.fillMaxSize().background(a.fond).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText("L'Appareil photo a besoin de la caméra.", style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre, textAlign = TextAlign.Center))
                        Spacer(Modifier.height(16.dp))
                        BoutonTexteAppli("Autoriser") { demander.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
                    }
                }
            }
            // ——— Les modes ———
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                if (filme == null && !act.pourUneAppli) Mode.entries.forEach { m ->
                    val ici = m == mode
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).then(if (ici) Modifier.background(a.voile) else Modifier)
                            .clickable(onClickLabel = m.nom, role = Role.Tab) { mode = m; code = null; document = false }
                            .padding(horizontal = if (ici) 14.dp else 8.dp, vertical = 6.dp),
                    ) { BasicText(m.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = if (ici) FontWeight.Bold else FontWeight.SemiBold, fontSize = 15.sp, color = if (ici) a.accent else a.encre2)) }
                }
            }
            Spacer(Modifier.weight(1f))
            // ——— Le déclencheur ———
            Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (mode == Mode.SCANNER && !document) Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(a.champ)
                        .clickable(onClickLabel = "Lire un code dans une image", role = Role.Button) {
                            choisirImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .semantics { contentDescription = "Lire un code dans une image" },
                    contentAlignment = Alignment.Center,
                ) { IconeTrait(Icones.IMAGE, 24.dp, a.encre) } else Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, Color(0x99F4EEE4), RoundedCornerShape(14.dp)).background(a.surface)
                        .clickable(enabled = derniere != null, onClickLabel = "Voir la dernière photo") {
                            derniere?.first?.let { u ->
                                try {
                                    c.startActivity(Intent("com.android.camera.action.REVIEW", u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                } catch (_: Exception) {
                                    c.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }
                            }
                        }
                        .semantics { contentDescription = "Dernière photo" },
                ) { derniere?.second?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                val enregistre = filme != null
                Box(
                    Modifier.size(84.dp).clip(CircleShape).border(4.dp, Color(0xFFF4EEE4), CircleShape)
                        .clickable(enabled = prete && (mode != Mode.SCANNER || document), onClickLabel = if (mode == Mode.VIDEO) (if (enregistre) "Arrêter la vidéo" else "Filmer") else "Prendre une photo", role = Role.Button) {
                            if (mode == Mode.VIDEO) filmer() else prendre()
                        }
                        .semantics { contentDescription = if (mode == Mode.VIDEO) (if (enregistre) "Arrêter la vidéo" else "Filmer") else "Prendre une photo" }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        mode == Mode.VIDEO && enregistre -> Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5533D)))
                        mode == Mode.VIDEO -> Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFE5533D)))
                        mode == Mode.SCANNER && !document -> Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0x33F4EEE4)))
                        else -> Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFF4EEE4)))
                    }
                }
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(a.champ)
                        .clickable(enabled = avant != null && arriere != null && filme == null, onClickLabel = "Changer de caméra", role = Role.Button) {
                            idCamera = if (idCamera == arriere) avant else arriere
                        }
                        .semantics { contentDescription = "Changer de caméra" },
                    contentAlignment = Alignment.Center,
                ) { IconeTrait(Ic.TOURNER, 24.dp, a.encre) }
            }
        }
        erreur?.let { e ->
            LaunchedEffect(e) {
                delay(4000)
                erreur = null
            }
            Box(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(a.surface).padding(18.dp)) {
                BasicText(e, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre))
            }
        }
        if (act.chaud) {
            Box(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(a.surface).padding(18.dp)) {
                BasicText(
                    "Le téléphone chauffe : l'appareil photo est fermé le temps qu'il refroidisse. Posez-le à l'ombre.",
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre),
                )
            }
        }
        code?.let { t -> Resultat(t, { code = null }) }
        aGarder?.let { octets -> Garder(octets, { aGarder = null }) { act.rendre(octets) } }
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
                reglages = false
            }
        }
    }
}

private fun etiquetteZoom(z: Float): String = if (z < 1f) String.format(java.util.Locale.FRENCH, "%.1f", z) else if (z % 1f == 0f) "${z.toInt()}" else String.format(java.util.Locale.FRENCH, "%.1f", z)

private fun chrono(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** « environ 1 h 10 restantes » : la place libre divisée par le débit de la vidéo. */
private fun resteVideo(c: Context, bitsParSeconde: Int): String {
    val libre = StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
    val s = libre * 8 / bitsParSeconde.coerceAtLeast(1)
    val h = s / 3600
    val m = s / 60 % 60
    return "environ " + (if (h > 0) "$h h ${"%02d".format(m)}" else "$m min") + " restantes"
}

@Composable
private fun Rond(icone: String, nom: String, badge: String? = null, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom }, contentAlignment = Alignment.Center) {
        IconeTrait(icone, 24.dp, a.encre)
        if (badge != null) BasicText(
            badge, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = a.accent),
        )
    }
}

@Composable
private fun Segment(nom: String, icone: String, choisi: Boolean, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).then(if (choisi) Modifier.background(a.accent) else Modifier)
            .clickable(onClickLabel = nom, role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconeTrait(icone, 16.dp, if (choisi) a.surAccent else a.encre)
        BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (choisi) a.surAccent else a.encre))
    }
}

/** L'appli qui a demandé une photo la reçoit seulement si on la garde. */
@Composable
private fun Garder(octets: ByteArray, reprendre: () -> Unit, garder: () -> Unit) {
    val a = LocalIdentite.current
    val image = remember(octets) { BitmapFactory.decodeByteArray(octets, 0, octets.size, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }
    Column(Modifier.fillMaxSize().background(a.fond).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { image?.let { Image(it, "La photo prise", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BoutonTexteAppli("Reprendre", style = 's', onClick = reprendre)
            BoutonTexteAppli("Garder", onClick = garder)
        }
    }
}

/** Ce que le code contient, dit clairement, avec ce qu'on peut en faire — et ce qui doit inquiéter. */
@Composable
private fun Resultat(t: String, fermer: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    if (t == "§document") {
        FeuilleAppli(fermer = fermer, titre = "Document enregistré") {
            BasicText("Il est dans Photos, album « Documents scannés ».", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
        }
        return
    }
    val r = Code.lire(t)
    FeuilleAppli(fermer = fermer) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(if (r.prudence != null) Color(0xFFB5532F) else a.champ), contentAlignment = Alignment.Center) {
                IconeTrait(if (r.prudence != null) Ic.ALERTE else r.icone, 24.dp, a.encre)
            }
            Column {
                BasicText(r.titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = a.encre))
                BasicText(r.detail, maxLines = 2, style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
            }
        }
        r.prudence?.let {
            BasicText(it, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFFEE9A78)))
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            r.action?.let { (nom, intent) ->
                BoutonTexteAppli(nom, style = if (r.prudence != null) 's' else ' ') {
                    try {
                        c.startActivity(intent)
                    } catch (_: Exception) {
                    }
                    fermer()
                }
            }
            BoutonTexteAppli("Copier", style = 's') {
                c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Code", t))
                fermer()
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
            u.startsWith("geo:", true) -> Sens("Un lieu", u.substring(4), Icones.BOUSSOLE, "Voir sur la carte" to Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            u.startsWith("BEGIN:VCARD", true) -> {
                val nom = Regex("\\nFN:(.*)").find(u)?.groupValues?.get(1)?.trim() ?: "Un contact"
                Sens(nom, "Carte de visite", Icones.PERSONNE, null)
            }
            else -> Sens("Texte", u, Icones.TEXTE, null)
        }
    }
}
