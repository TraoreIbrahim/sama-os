package africa.samaos.dictaphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

/** Un enregistrement en cours : son nom, sa durée, ses repères, le niveau du son pour l'onde. */
data class EnCours(
    val nom: String,
    val uri: Uri,
    val cumul: Long,
    val reprise: Long?,
    val reperes: List<Long>,
    val niveaux: List<Float>,
) {
    val enPause get() = reprise == null
    fun duree(maintenant: Long = SystemClock.elapsedRealtime()) = cumul + (reprise?.let { maintenant - it } ?: 0)
}

object Dicta {
    val etat = MutableStateFlow<EnCours?>(null)
    /** Le dernier enregistrement fini, pour l'annoncer et rafraîchir la liste. */
    val fini = MutableStateFlow<String?>(null)
    const val DOSSIER = "Recordings/Dictaphone/"
    const val DEBIT = 96_000

    fun reperes(c: Context, id: Long): List<Long> = try {
        val a = JSONArray(c.getSharedPreferences("reperes", Context.MODE_PRIVATE).getString(id.toString(), "[]"))
        (0 until a.length()).map { a.getLong(it) }
    } catch (_: Exception) {
        emptyList()
    }

    fun garderReperes(c: Context, id: Long, l: List<Long>) {
        c.getSharedPreferences("reperes", Context.MODE_PRIVATE).edit().putString(id.toString(), JSONArray(l).toString()).apply()
    }

    fun commander(c: Context, action: String, nom: String? = null) {
        val i = Intent(c, Enregistreur::class.java).setAction(action).putExtra("nom", nom)
        if (action == DEMARRER) c.startForegroundService(i) else c.startService(i)
    }

    const val DEMARRER = "demarrer"
    const val PAUSE = "pause"
    const val REPRENDRE = "reprendre"
    const val REPERE = "repere"
    const val ARRETER = "arreter"
}

/**
 * L'enregistrement tourne dans un service au premier plan : il continue écran éteint ou appli fermée,
 * et la notification montre qu'on enregistre (comme le voyant du micro d'Android).
 */
class Enregistreur : Service() {
    private var micro: MediaRecorder? = null
    private var fichier: ParcelFileDescriptor? = null
    private val boucle = Handler(Looper.getMainLooper())
    private val mesurer = object : Runnable {
        override fun run() {
            val e = Dicta.etat.value ?: return
            if (!e.enPause) {
                val niveau = ((micro?.maxAmplitude ?: 0) / 32767f).coerceIn(0f, 1f)
                Dicta.etat.value = e.copy(niveaux = (e.niveaux + niveau).takeLast(400))
            }
            boucle.postDelayed(this, 80)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Dicta.DEMARRER -> demarrer(intent.getStringExtra("nom") ?: "Enregistrement")
            Dicta.PAUSE -> Dicta.etat.value?.takeIf { !it.enPause }?.let { e ->
                micro?.pause()
                Dicta.etat.value = e.copy(cumul = e.duree(), reprise = null)
                notifier()
            }
            Dicta.REPRENDRE -> Dicta.etat.value?.takeIf { it.enPause }?.let { e ->
                micro?.resume()
                Dicta.etat.value = e.copy(reprise = SystemClock.elapsedRealtime())
                notifier()
            }
            Dicta.REPERE -> Dicta.etat.value?.let { e -> Dicta.etat.value = e.copy(reperes = e.reperes + e.duree()) }
            Dicta.ARRETER -> arreter()
        }
        return START_NOT_STICKY
    }

    private fun demarrer(nom: String) {
        if (Dicta.etat.value != null) return
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$nom.m4a")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Dicta.DOSSIER)
            put(MediaStore.Audio.Media.TITLE, nom)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v)
        } catch (_: Exception) {
            null
        } ?: run { stopSelf(); return }
        try {
            fichier = contentResolver.openFileDescriptor(uri, "rw")
            micro = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(Dicta.DEBIT)
                setAudioChannels(1)
                setOutputFile(fichier!!.fileDescriptor)
                prepare()
                start()
            }
        } catch (_: Exception) {
            contentResolver.delete(uri, null, null)
            stopSelf()
            return
        }
        Dicta.etat.value = EnCours(nom, uri, 0, SystemClock.elapsedRealtime(), emptyList(), emptyList())
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        boucle.post(mesurer)
    }

    private fun arreter() {
        val e = Dicta.etat.value
        boucle.removeCallbacks(mesurer)
        try {
            micro?.stop()
        } catch (_: Exception) {
        }
        micro?.release()
        micro = null
        fichier?.close()
        fichier = null
        if (e != null) {
            contentResolver.update(e.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            if (e.reperes.isNotEmpty()) Dicta.garderReperes(this, ContentUris.parseId(e.uri), e.reperes)
            Dicta.fini.value = e.nom
        }
        Dicta.etat.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifier() = getSystemService(NotificationManager::class.java).notify(1, notification())

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("enregistrement", "Enregistrement en cours", NotificationManager.IMPORTANCE_LOW))
        val e = Dicta.etat.value
        val ouvrir = PendingIntent.getActivity(this, 0, Intent(this, Dictaphone::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun action(nom: String, a: String, code: Int) = Notification.Action.Builder(
            null, nom, PendingIntent.getService(this, code, Intent(this, Enregistreur::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return Notification.Builder(this, "enregistrement")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(e?.nom ?: "Dictaphone")
            .setContentText(if (e?.enPause == true) "En pause" else "Enregistrement en cours")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(ouvrir)
            .apply {
                if (e != null && !e.enPause) {
                    setUsesChronometer(true)
                    setWhen(System.currentTimeMillis() - e.duree())
                    setShowWhen(true)
                }
            }
            .addAction(if (e?.enPause == true) action("Reprendre", Dicta.REPRENDRE, 1) else action("Pause", Dicta.PAUSE, 2))
            .addAction(action("Arrêter", Dicta.ARRETER, 3))
            .build()
    }

    override fun onDestroy() {
        boucle.removeCallbacks(mesurer)
        super.onDestroy()
    }
}
