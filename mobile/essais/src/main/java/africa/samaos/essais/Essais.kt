package africa.samaos.essais

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock

private const val CANAL_LECTURE = "lecture"
private const val CANAL_MESSAGES = "messages"
private const val ID_LECTURE = 1
private const val ID_MESSAGE = 2

private fun canaux(contexte: Context) {
    val nm = contexte.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CANAL_LECTURE, "Lecture", NotificationManager.IMPORTANCE_LOW))
    nm.createNotificationChannel(NotificationChannel(CANAL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH))
}

/** Lance un essai puis s'efface : « lecture » (une leçon de 25 min) ou « message » (Aminata écrit). */
class Essais : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        canaux(this)
        when (intent.getStringExtra("essai") ?: "lecture") {
            "lecture" -> startForegroundService(Intent(this, Lecteur::class.java))
            "arret" -> stopService(Intent(this, Lecteur::class.java))
            "message" -> Reponse.message(this, listOf("Aminata" to "Tu passes à la boutique ce soir ?"))
        }
        finish()
    }
}

/** La leçon, jouée avec une session média comme le ferait n'importe quel lecteur. */
class Lecteur : Service() {
    private lateinit var lecteur: MediaPlayer
    private lateinit var session: MediaSession

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        canaux(this)
        lecteur = MediaPlayer.create(this, R.raw.lecon).apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        }
        session = MediaSession(this, "Essais").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = jouer()
                override fun onPause() = pause()
                override fun onSeekTo(pos: Long) {
                    lecteur.seekTo(pos.toInt())
                    etat()
                }
                override fun onRewind() = onSeekTo((lecteur.currentPosition - 15_000L).coerceAtLeast(0))
                override fun onFastForward() = onSeekTo((lecteur.currentPosition + 15_000L).coerceAtMost(lecteur.duration.toLong()))
                override fun onStop() = stopSelf()
            })
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Leçon de julakan · épisode 12")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Radio Sama")
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, "Julakan pour tous")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, lecteur.duration.toLong())
                    .putBitmap(MediaMetadata.METADATA_KEY_ART, pochette())
                    .build(),
            )
            setSessionActivity(PendingIntent.getActivity(this@Lecteur, 0, Intent(this@Lecteur, Essais::class.java).putExtra("essai", "rien"), PendingIntent.FLAG_IMMUTABLE))
            isActive = true
        }
        lecteur.setOnCompletionListener { pause() }
        startForeground(ID_LECTURE, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        lecteur.seekTo(760_000)
        jouer()
    }

    private fun jouer() {
        lecteur.start()
        etat()
    }

    private fun pause() {
        lecteur.pause()
        etat()
    }

    private fun etat() {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (lecteur.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    lecteur.currentPosition.toLong(),
                    if (lecteur.isPlaying) 1f else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build(),
        )
        getSystemService(NotificationManager::class.java).notify(ID_LECTURE, notification())
    }

    private fun notification(): Notification =
        Notification.Builder(this, CANAL_LECTURE)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Leçon de julakan · épisode 12")
            .setContentText("Radio Sama")
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .build()

    private fun pochette(): Bitmap {
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(0x2B, 0x35, 0x55))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE0, 0x7B, 0x53) }
        c.drawCircle(128f, 128f, 64f, p)
        return b
    }

    override fun onDestroy() {
        session.release()
        lecteur.release()
        super.onDestroy()
    }
}

/** Un message d'Aminata avec « Répondre » : la réponse s'ajoute à la conversation, comme dans une messagerie. */
class Reponse : BroadcastReceiver() {
    override fun onReceive(contexte: Context, intent: Intent) {
        val texte = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CLE)?.toString() ?: return
        message(contexte, listOf("Aminata" to "Tu passes à la boutique ce soir ?", "" to texte))
    }

    companion object {
        private const val CLE = "reponse"

        fun message(contexte: Context, messages: List<Pair<String, String>>) {
            canaux(contexte)
            val moi = Person.Builder().setName("Moi").build()
            val aminata = Person.Builder().setName("Aminata").build()
            val style = Notification.MessagingStyle(moi)
            messages.forEach { (qui, texte) -> style.addMessage(texte, System.currentTimeMillis(), if (qui.isEmpty()) null else aminata) }
            val repondre = Notification.Action.Builder(
                null,
                "Répondre",
                PendingIntent.getBroadcast(
                    contexte, 0, Intent(contexte, Reponse::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            ).addRemoteInput(RemoteInput.Builder(CLE).setLabel("Répondre à Aminata").build()).build()
            contexte.getSystemService(NotificationManager::class.java).notify(
                ID_MESSAGE,
                Notification.Builder(contexte, CANAL_MESSAGES)
                    .setSmallIcon(android.R.drawable.sym_action_chat)
                    .setStyle(style)
                    .addAction(repondre)
                    .build(),
            )
        }
    }
}
