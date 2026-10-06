package africa.samaos.lecteur

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.util.Size
import kotlinx.coroutines.flow.MutableStateFlow

/** Ce qui joue : la file, la place dans la file, en lecture ou non, aléatoire, répétition. */
data class EtatLecture(val file: List<Morceau>, val index: Int, val joue: Boolean, val aleatoire: Boolean = false, val repeter: Repeter = Repeter.NON) {
    val courant: Morceau? get() = file.getOrNull(index)
}

enum class Repeter { NON, TOUT, UN }

/** La platine : ce que l'interface lit et commande ; la musique elle-même tourne dans le service. */
object Platine {
    val etat = MutableStateFlow<EtatLecture?>(null)
    internal var lecteur: MediaPlayer? = null

    fun position(): Long = try {
        (lecteur?.currentPosition ?: 0).toLong()
    } catch (_: Exception) {
        0
    }

    fun lire(c: Context, file: List<Morceau>, index: Int, aleatoire: Boolean = false) {
        val f = if (aleatoire) file.shuffled() else file
        val i = if (aleatoire) 0 else index
        etat.value = EtatLecture(f, i, true, aleatoire, etat.value?.repeter ?: Repeter.NON)
        commander(c, Musique.JOUER)
    }

    fun commander(c: Context, action: String, valeur: Long = 0) {
        val i = Intent(c, Musique::class.java).setAction(action).putExtra("valeur", valeur)
        if (action == Musique.JOUER || action == Musique.REPRENDRE) c.startForegroundService(i) else c.startService(i)
    }

    fun repeter() {
        etat.value = etat.value?.let { it.copy(repeter = Repeter.entries[(it.repeter.ordinal + 1) % Repeter.entries.size]) }
    }

    fun aleatoire() {
        val e = etat.value ?: return
        val courant = e.courant ?: return
        etat.value = if (!e.aleatoire) {
            // Le morceau en cours reste en tête ; le reste est mélangé.
            e.copy(file = listOf(courant) + (e.file - courant).shuffled(), index = 0, aleatoire = true)
        } else {
            val ordre = e.file.sortedBy { it.titre.lowercase() }
            e.copy(file = ordre, index = ordre.indexOf(courant), aleatoire = false)
        }
    }
}

/**
 * La musique continue écran éteint : un service au premier plan avec une MediaSession, que la notification,
 * le Pouls de Sama, les écouteurs et la voiture savent commander.
 */
class Musique : Service() {
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private var focus: AudioFocusRequest? = null
    private var reprendreApresPerte = false
    private var pochette: Bitmap? = null

    private val debranche = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = pause()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        session = MediaSession(this, "Lecteur").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = reprendre()
                override fun onPause() = pause()
                override fun onSkipToNext() = suivant(true)
                override fun onSkipToPrevious() = precedent()
                override fun onSeekTo(pos: Long) = aller(pos)
                override fun onStop() = arreter()
            })
            setSessionActivity(PendingIntent.getActivity(this@Musique, 0, Intent(this@Musique, Lecteur::class.java).putExtra("platine", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            isActive = true
        }
        registerReceiver(debranche, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            JOUER -> jouer()
            REPRENDRE -> reprendre()
            PAUSE -> pause()
            BASCULER -> if (Platine.lecteur?.isPlaying == true) pause() else reprendre()
            SUIVANT -> suivant(true)
            PRECEDENT -> precedent()
            ALLER -> aller(intent.getLongExtra("valeur", 0))
            ARRETER -> arreter()
        }
        return START_NOT_STICKY
    }

    private fun jouer() {
        val e = Platine.etat.value ?: return arreter()
        val m = e.courant ?: return arreter()
        Platine.lecteur?.release()
        Platine.lecteur = null
        val p = MediaPlayer()
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        try {
            p.setDataSource(this, m.uri)
            p.prepare()
        } catch (_: Exception) {
            // Un fichier illisible : on passe au suivant plutôt que de bloquer.
            p.release()
            return suivant(false)
        }
        p.setOnCompletionListener { fini() }
        // Écran éteint, le processeur reste éveillé tant que la musique joue.
        p.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK)
        Platine.lecteur = p
        pochette = try {
            contentResolver.loadThumbnail(m.uri, Size(512, 512), null)
        } catch (_: Exception) {
            null
        }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, m.titre)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, m.artiste)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, m.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, m.duree)
                .apply { pochette?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
        reprendre()
    }

    private fun demanderFocus(): Boolean {
        val r = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { f ->
                when (f) {
                    // Un appel, un message vocal : on se tait, et on reprend après.
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        reprendreApresPerte = Platine.lecteur?.isPlaying == true
                        pause(garderFocus = true)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Platine.lecteur?.setVolume(0.3f, 0.3f)
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        Platine.lecteur?.setVolume(1f, 1f)
                        if (reprendreApresPerte) reprendre()
                        reprendreApresPerte = false
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> pause()
                }
            }
            .build().also { focus = it }
        return audio.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun reprendre() {
        val p = Platine.lecteur ?: return jouer()
        if (!demanderFocus()) return
        p.start()
        Platine.etat.value = Platine.etat.value?.copy(joue = true)
        publier()
        startForeground(2, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun pause(garderFocus: Boolean = false) {
        Platine.lecteur?.takeIf { it.isPlaying }?.pause()
        if (!garderFocus) focus?.let { audio.abandonAudioFocusRequest(it) }
        Platine.etat.value = Platine.etat.value?.copy(joue = false)
        publier()
        // En pause, la notification reste mais le service n'est plus au premier plan.
        stopForeground(STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java).notify(2, notification())
    }

    private fun aller(ms: Long) {
        Platine.lecteur?.seekTo(ms.toInt())
        publier()
    }

    private fun fini() {
        val e = Platine.etat.value ?: return
        when {
            e.repeter == Repeter.UN -> {
                Platine.lecteur?.seekTo(0)
                reprendre()
            }
            e.index < e.file.lastIndex || e.repeter == Repeter.TOUT -> suivant(false)
            else -> {
                // Fin de la file : on s'arrête sur le dernier morceau, prêt à rejouer.
                Platine.lecteur?.seekTo(0)
                pause()
            }
        }
    }

    private fun suivant(geste: Boolean) {
        val e = Platine.etat.value ?: return
        if (e.file.isEmpty()) return arreter()
        val i = if (e.index < e.file.lastIndex) e.index + 1 else if (e.repeter == Repeter.TOUT || geste) 0 else return
        Platine.etat.value = e.copy(index = i)
        jouer()
    }

    private fun precedent() {
        val e = Platine.etat.value ?: return
        // Après quelques secondes, « précédent » revient au début du morceau, comme partout.
        if (Platine.position() > 4000) return aller(0)
        Platine.etat.value = e.copy(index = if (e.index > 0) e.index - 1 else e.file.lastIndex)
        jouer()
    }

    private fun arreter() {
        Platine.lecteur?.release()
        Platine.lecteur = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        Platine.etat.value = null
        session.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publier() {
        val joue = Platine.lecteur?.isPlaying == true
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP,
                )
                .setState(if (joue) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, Platine.position(), if (joue) 1f else 0f)
                .build(),
        )
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("lecture", "Lecture en cours", NotificationManager.IMPORTANCE_LOW))
        val m = Platine.etat.value?.courant
        val joue = Platine.lecteur?.isPlaying == true
        fun action(icone: Int, nom: String, a: String, code: Int) = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icone), nom,
            PendingIntent.getService(this, code, Intent(this, Musique::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return Notification.Builder(this, "lecture")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(m?.titre ?: "Lecteur")
            .setContentText(m?.artiste)
            .setLargeIcon(pochette)
            .setContentIntent(session.controller.sessionActivity)
            .setDeleteIntent(PendingIntent.getService(this, 9, Intent(this, Musique::class.java).setAction(ARRETER), PendingIntent.FLAG_IMMUTABLE))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(R.drawable.ic_precedent, "Précédent", PRECEDENT, 4))
            .addAction(if (joue) action(R.drawable.ic_pause, "Pause", PAUSE, 5) else action(R.drawable.ic_lecture, "Lecture", REPRENDRE, 6))
            .addAction(action(R.drawable.ic_suivant, "Suivant", SUIVANT, 7))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    override fun onDestroy() {
        unregisterReceiver(debranche)
        session.release()
        Platine.lecteur?.release()
        Platine.lecteur = null
        Platine.etat.value = null
        super.onDestroy()
    }

    companion object {
        const val JOUER = "jouer"
        const val REPRENDRE = "reprendre"
        const val PAUSE = "pause"
        const val BASCULER = "basculer"
        const val SUIVANT = "suivant"
        const val PRECEDENT = "precedent"
        const val ALLER = "aller"
        const val ARRETER = "arreter"
    }
}
