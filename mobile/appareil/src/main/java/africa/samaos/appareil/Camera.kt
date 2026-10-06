package africa.samaos.appareil

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.CamcorderProfile
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Surface
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.FileDescriptor
import java.util.concurrent.Executor

enum class Flash { NON, AUTO, OUI }

/** Ce qu'on demande à la caméra : prendre des photos, filmer, ou lire des codes. */
enum class Usage { PHOTO, VIDEO, SCANNER }

/**
 * L'appareil, par Camera2 (l'API de la caméra d'Android) : un fil à part pour la caméra, une session par usage.
 * Les rappels reviennent sur le fil de la caméra ; l'interface les reprend sur le sien.
 */
class Camera(private val c: Context) {
    private val cm = c.getSystemService(CameraManager::class.java)
    private val fil = HandlerThread("camera").apply { start() }
    private val h = Handler(fil.looper)
    private val executeur = Executor { h.post(it) }

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var requete: CaptureRequest.Builder? = null
    private var jpeg: ImageReader? = null
    private var yuv: ImageReader? = null
    private var enregistreur: MediaRecorder? = null
    private var surfaceApercu: Surface? = null
    private var texture: SurfaceTexture? = null

    var id: String = ""
        private set
    lateinit var caracs: CameraCharacteristics
        private set
    var apercu: Size = Size(1440, 1080)
        private set
    private var usage = Usage.PHOTO
    private var zoom = 1f
    private var lampe = false
    @Volatile private var dernierScan = 0L

    /** Les deux caméras : arrière, avant. */
    fun cameras(): Pair<String?, String?> {
        var arriere: String? = null
        var avant: String? = null
        cm.cameraIdList.forEach { i ->
            when (cm.getCameraCharacteristics(i).get(CameraCharacteristics.LENS_FACING)) {
                CameraMetadata.LENS_FACING_BACK -> if (arriere == null) arriere = i
                CameraMetadata.LENS_FACING_FRONT -> if (avant == null) avant = i
            }
        }
        return arriere to avant
    }

    val avant get() = caracs.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
    val orientationCapteur get() = caracs.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    val flashPossible get() = caracs.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

    /** Les agrandissements possibles (CONTROL_ZOOM_RATIO depuis Android 11, sinon le recadrage du capteur). */
    val zooms: Range<Float>
        get() = if (Build.VERSION.SDK_INT >= 30) caracs.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: Range(1f, 1f)
        else Range(1f, caracs.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)

    /** La meilleure taille 4:3 pour l'aperçu, sans dépasser 1440 × 1080. */
    private fun choisirApercu(): Size {
        val carte = caracs.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val tailles = carte.getOutputSizes(SurfaceTexture::class.java)
        return tailles.filter { it.width * 3 == it.height * 4 && it.width <= 1440 }.maxByOrNull { it.width } ?: tailles.first()
    }

    private fun plusGrandJpeg(): Size {
        val carte = caracs.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val t = carte.getOutputSizes(ImageFormat.JPEG)
        return t.filter { it.width * 3 == it.height * 4 }.maxByOrNull { it.width * it.height } ?: t.maxBy { it.width * it.height }
    }

    @SuppressLint("MissingPermission")
    fun ouvrir(idCamera: String, st: SurfaceTexture, u: Usage, pret: (Size) -> Unit, scan: (String) -> Unit, erreur: (String) -> Unit) {
        fermer()
        id = idCamera
        caracs = cm.getCameraCharacteristics(id)
        apercu = choisirApercu()
        usage = u
        zoom = 1f
        texture = st
        st.setDefaultBufferSize(apercu.width, apercu.height)
        surfaceApercu = Surface(st)
        jpeg = plusGrandJpeg().let { ImageReader.newInstance(it.width, it.height, ImageFormat.JPEG, 2) }
        if (u == Usage.SCANNER) {
            yuv = ImageReader.newInstance(1280, 960, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ r -> lireCode(r, scan) }, h)
            }
        }
        try {
            cm.openCamera(id, executeur, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    device = d
                    session(listOfNotNull(surfaceApercu, jpeg?.surface, yuv?.surface)) { pret(apercu) }
                }

                override fun onDisconnected(d: CameraDevice) {
                    d.close()
                    device = null
                }

                override fun onError(d: CameraDevice, e: Int) {
                    d.close()
                    device = null
                    erreur(if (e == ERROR_CAMERA_IN_USE || e == ERROR_MAX_CAMERAS_IN_USE) "La caméra est prise par une autre appli." else "La caméra ne répond pas.")
                }
            })
        } catch (e: Exception) {
            erreur("La caméra ne s'ouvre pas.")
        }
    }

    private fun session(surfaces: List<Surface>, video: Boolean = false, pret: () -> Unit = {}) {
        val d = device ?: return
        val cfg = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR, surfaces.map { OutputConfiguration(it) }, executeur,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    requete = d.createCaptureRequest(if (video) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surfaceApercu!!)
                        if (video) enregistreur?.surface?.let { addTarget(it) }
                        if (usage == Usage.SCANNER) yuv?.surface?.let { addTarget(it) }
                        set(CaptureRequest.CONTROL_AF_MODE, if (video) CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO else CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        if (lampe) set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    }
                    appliquerZoom(requete!!)
                    repeter()
                    pret()
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {}
            },
        )
        d.createCaptureSession(cfg)
    }

    private fun repeter() {
        try {
            session?.setRepeatingRequest(requete?.build() ?: return, null, h)
        } catch (_: Exception) {
        }
    }

    private fun appliquerZoom(b: CaptureRequest.Builder) {
        if (Build.VERSION.SDK_INT >= 30) {
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
        } else {
            val actif = caracs.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
            val w = (actif.width() / zoom).toInt()
            val hh = (actif.height() / zoom).toInt()
            b.set(CaptureRequest.SCALER_CROP_REGION, Rect((actif.width() - w) / 2, (actif.height() - hh) / 2, (actif.width() + w) / 2, (actif.height() + hh) / 2))
        }
    }

    fun zoomer(r: Float) = h.post {
        zoom = r.coerceIn(zooms.lower, zooms.upper)
        requete?.let {
            appliquerZoom(it)
            repeter()
        }
    }

    fun lampe(oui: Boolean) = h.post {
        lampe = oui
        requete?.set(CaptureRequest.FLASH_MODE, if (oui) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
        repeter()
    }

    /** Mise au point là où l'on touche (x, y entre 0 et 1 sur l'aperçu tel qu'on le voit, en portrait). */
    fun mettreAuPoint(x: Float, y: Float) = h.post {
        val r = requete ?: return@post
        val actif = caracs.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return@post
        if ((caracs.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0) == 0) return@post
        // L'aperçu est tourné d'un quart par rapport au capteur : on remet le point dans le repère du capteur.
        val (sx, sy) = if (orientationCapteur == 90) y to 1 - x else if (orientationCapteur == 270) 1 - y to x else x to y
        val cx = (sx * actif.width()).toInt()
        val cy = (sy * actif.height()).toInt()
        val demi = actif.width() / 16
        val zone = MeteringRectangle(
            (cx - demi).coerceAtLeast(0), (cy - demi).coerceAtLeast(0), demi * 2, demi * 2, MeteringRectangle.METERING_WEIGHT_MAX - 1,
        )
        r.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(zone))
        r.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(zone))
        r.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        r.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        try {
            session?.capture(r.build(), null, h)
        } catch (_: Exception) {
        }
        r.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        repeter()
    }

    /** Une photo : le JPEG du capteur, déjà tourné dans le bon sens. */
    fun photo(flash: Flash, recu: (ByteArray) -> Unit) = h.post {
        val d = device ?: return@post
        val s = session ?: return@post
        val j = jpeg ?: return@post
        j.setOnImageAvailableListener({ r ->
            r.acquireNextImage()?.use { im ->
                val b = im.planes[0].buffer
                val octets = ByteArray(b.remaining()).also { b.get(it) }
                recu(octets)
            }
        }, h)
        val q = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(j.surface)
            set(CaptureRequest.JPEG_ORIENTATION, orientationCapteur)
            set(CaptureRequest.JPEG_QUALITY, 92.toByte())
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            if (flashPossible) {
                set(
                    CaptureRequest.CONTROL_AE_MODE,
                    when (flash) {
                        Flash.NON -> CaptureRequest.CONTROL_AE_MODE_ON
                        Flash.AUTO -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                        Flash.OUI -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    },
                )
            }
            appliquerZoom(this)
        }
        try {
            s.capture(q.build(), null, h)
        } catch (_: Exception) {
        }
    }

    // ——— La vidéo ———

    /** La meilleure qualité que la caméra et l'enregistreur savent faire ensemble : 1080p, sinon 720p. */
    fun profil(): CamcorderProfile? {
        val n = id.toIntOrNull() ?: return null
        val carte = caracs.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val tailles = carte.getOutputSizes(MediaRecorder::class.java).toSet()
        return listOf(CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_720P, CamcorderProfile.QUALITY_480P, CamcorderProfile.QUALITY_HIGH)
            .filter { CamcorderProfile.hasProfile(n, it) }
            .map { CamcorderProfile.get(n, it) }
            .firstOrNull { Size(it.videoFrameWidth, it.videoFrameHeight) in tailles }
    }

    fun filmer(fd: FileDescriptor, son: Boolean, demarre: (Boolean) -> Unit) = h.post {
        val p = profil() ?: return@post demarre(false)
        fun preparer(avecSon: Boolean): MediaRecorder = MediaRecorder(c).apply {
            if (avecSon) setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(fd)
            setVideoEncodingBitRate(p.videoBitRate)
            setVideoFrameRate(p.videoFrameRate)
            setVideoSize(p.videoFrameWidth, p.videoFrameHeight)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (avecSon) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(p.audioBitRate)
                setAudioSamplingRate(p.audioSampleRate)
            }
            setOrientationHint(orientationCapteur)
            prepare()
        }
        val r = try {
            preparer(son)
        } catch (_: Exception) {
            // Pas de micro (ou refusé) : on filme sans le son plutôt que pas du tout.
            try {
                preparer(false)
            } catch (_: Exception) {
                null
            }
        } ?: return@post demarre(false)
        enregistreur = r
        session?.close()
        session(listOfNotNull(surfaceApercu, r.surface), video = true) {
            try {
                r.start()
                demarre(true)
            } catch (_: Exception) {
                demarre(false)
            }
        }
    }

    fun arreterFilm(fini: () -> Unit) = h.post {
        try {
            enregistreur?.stop()
        } catch (_: Exception) {
        }
        enregistreur?.release()
        enregistreur = null
        session?.close()
        session(listOfNotNull(surfaceApercu, jpeg?.surface)) { fini() }
    }

    // ——— Les codes ———

    private val lecteur = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    private fun lireCode(r: ImageReader, scan: (String) -> Unit) {
        val im = r.acquireLatestImage() ?: return
        im.use {
            val maintenant = SystemClock.elapsedRealtime()
            if (maintenant - dernierScan < 250) return
            dernierScan = maintenant
            val plan = it.planes[0]
            val buf = plan.buffer
            val largeur = it.width
            val hauteur = it.height
            val ligne = plan.rowStride
            val octets = ByteArray(largeur * hauteur)
            for (y in 0 until hauteur) {
                buf.position(y * ligne)
                buf.get(octets, y * largeur, largeur)
            }
            try {
                val res = lecteur.decodeWithState(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(octets, largeur, hauteur, 0, 0, largeur, hauteur, false))))
                scan(res.text)
            } catch (_: Exception) {
            } finally {
                lecteur.reset()
            }
        }
    }

    /** Un code dans une image (une capture reçue par message, par exemple). */
    fun lireImage(b: android.graphics.Bitmap): String? {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return try {
            MultiFormatReader().decode(
                BinaryBitmap(HybridBinarizer(com.google.zxing.RGBLuminanceSource(b.width, b.height, px))),
                mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true),
            ).text
        } catch (_: Exception) {
            null
        }
    }

    fun fermer() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        device?.close()
        device = null
        jpeg?.close()
        jpeg = null
        yuv?.close()
        yuv = null
        enregistreur?.release()
        enregistreur = null
        surfaceApercu?.release()
        surfaceApercu = null
    }

    fun finir() {
        fermer()
        fil.quitSafely()
    }
}
