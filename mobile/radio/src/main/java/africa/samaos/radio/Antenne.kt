package africa.samaos.radio

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
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Le poste, vu de l'appli : son état, et les ordres qu'on lui donne (ils passent par le service). */
object Poste {
    val etat = MutableStateFlow(EtatPoste())

    const val JOUER = "jouer"
    const val ARRETER = "arreter"
    const val REGLER = "regler"
    const val CHERCHER = "chercher"
    const val SUIVANTE = "suivante"
    const val PRECEDENTE = "precedente"
    const val MINUTERIE = "minuterie"

    fun commander(c: Context, action: String, valeur: Int = 0) {
        val i = Intent(c, Antenne::class.java).setAction(action).putExtra("valeur", valeur)
        if (action == JOUER || action == SUIVANTE || action == PRECEDENTE || action == CHERCHER) c.startForegroundService(i) else c.startService(i)
    }
}

/**
 * La radio continue écran éteint : un service au premier plan avec une MediaSession, que la notification, le Pouls
 * et les écouteurs commandent. Il tient le syntoniseur, veille aux écouteurs (l'antenne) et à la minuterie.
 */
class Antenne : Service() {
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private lateinit var syntoniseur: Syntoniseur
    private var focus: AudioFocusRequest? = null
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var minuterie: Job? = null
    private var recherche: Job? = null

    private val ecouteurs = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Poste.etat.value = Poste.etat.value.copy(ecouteurs = ecouteursBranches(c))
            notifier()
        }
    }
    private val bruit = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = arreter()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        syntoniseur = PuceFm.syntoniseur(this)
        syntoniseur.ouvrir()
        val k = Stations.derniere(this)
        Poste.etat.value = EtatPoste(kHz = k, signal = syntoniseur.regler(k), nom = syntoniseur.nom(k), ecouteurs = ecouteursBranches(this), essai = syntoniseur.essai)
        session = MediaSession(this, "Radio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = jouer()
                override fun onPause() = arreter()
                override fun onStop() = arreter()
                override fun onSkipToNext() = station(true)
                override fun onSkipToPrevious() = station(false)
            })
            isActive = true
        }
        registerReceiver(ecouteurs, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))
        registerReceiver(bruit, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        canal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val v = intent?.getIntExtra("valeur", 0) ?: 0
        when (intent?.action) {
            Poste.JOUER -> jouer()
            Poste.ARRETER -> arreter()
            Poste.REGLER -> regler(v)
            Poste.CHERCHER -> chercher(v > 0)
            Poste.SUIVANTE -> station(true)
            Poste.PRECEDENTE -> station(false)
            Poste.MINUTERIE -> minuterie(v)
        }
        // Un ordre reçu par startForegroundService doit donner une notification : s'il n'y a rien à jouer, elle part aussitôt.
        if (!Poste.etat.value.joue) {
            startForeground(3, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        return START_NOT_STICKY
    }

    private fun regler(kHz: Int) {
        val k = Bande.borne(kHz)
        val signal = syntoniseur.regler(k)
        Stations.noterDerniere(this, k)
        Poste.etat.value = Poste.etat.value.copy(kHz = k, signal = signal, nom = syntoniseur.nom(k))
        notifier()
    }

    /** La station d'après sur la bande, dans le sens demandé : la puce balaie. */
    private fun chercher(montant: Boolean) {
        if (Poste.etat.value.enRecherche) return
        recherche = portee.launch {
            Poste.etat.value = Poste.etat.value.copy(enRecherche = true)
            val trouvee = syntoniseur.chercher(Poste.etat.value.kHz, montant)
            Poste.etat.value = Poste.etat.value.copy(enRecherche = false)
            if (trouvee != null) regler(trouvee)
        }
    }

    /** La station gardée d'après (ou d'avant) ; sans station gardée, la puce cherche. */
    private fun station(suivante: Boolean) {
        val l = Stations.liste(this)
        if (l.isEmpty()) return chercher(suivante)
        val k = Poste.etat.value.kHz
        val cible = if (suivante) (l.firstOrNull { it.kHz > k } ?: l.first()) else (l.lastOrNull { it.kHz < k } ?: l.last())
        regler(cible.kHz)
    }

    private fun jouer() {
        if (!prendreFocus()) return
        syntoniseur.jouer(true)
        Poste.etat.value = Poste.etat.value.copy(joue = true)
        startForeground(3, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        etatSession()
    }

    private fun arreter() {
        syntoniseur.jouer(false)
        minuterie?.cancel()
        minuterie = null
        Poste.etat.value = Poste.etat.value.copy(joue = false, minuterieFin = null)
        etatSession()
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** S'arrêter dans [minutes] minutes (0 : annuler) ; pour s'endormir avec la radio. */
    private fun minuterie(minutes: Int) {
        minuterie?.cancel()
        if (minutes <= 0) {
            minuterie = null
            Poste.etat.value = Poste.etat.value.copy(minuterieFin = null)
            notifier()
            return
        }
        val fin = System.currentTimeMillis() + minutes * 60_000L
        Poste.etat.value = Poste.etat.value.copy(minuterieFin = fin)
        notifier()
        minuterie = portee.launch {
            delay(minutes * 60_000L)
            arreter()
        }
    }

    private fun prendreFocus(): Boolean {
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { f -> if (f == AudioManager.AUDIOFOCUS_LOSS || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) arreter() }
            .build()
        focus = r
        return audio.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun etatSession() {
        val e = Poste.etat.value
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(if (e.joue) PlaybackState.STATE_PLAYING else PlaybackState.STATE_STOPPED, 0, 1f)
                .build(),
        )
        notifier()
    }

    private fun notifier() {
        if (Poste.etat.value.joue) getSystemService(NotificationManager::class.java).notify(3, notification())
    }

    private fun canal() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("radio", "Radio en cours", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(): Notification {
        val e = Poste.etat.value
        val nomStation = Stations.liste(this).firstOrNull { it.kHz == e.kHz }?.nom?.takeIf { it.isNotBlank() } ?: e.nom
        fun action(icone: String, nom: String, a: String, code: Int) = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_play).let { if (icone == "prev") Icon.createWithResource(this, android.R.drawable.ic_media_previous) else if (icone == "next") Icon.createWithResource(this, android.R.drawable.ic_media_next) else if (icone == "stop") Icon.createWithResource(this, android.R.drawable.ic_media_pause) else it },
            nom,
            PendingIntent.getService(this, code, Intent(this, Antenne::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        ).build()
        val ouvrir = PendingIntent.getActivity(this, 0, Intent(this, Radio::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "radio")
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setContentTitle(listOfNotNull("${mhz(e.kHz)} MHz", nomStation).joinToString(" · "))
            .setContentText(
                listOfNotNull(
                    if (e.essai) "Syntoniseur d'essai" else "Radio FM",
                    e.minuterieFin?.let { "s'arrête à " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) },
                ).joinToString(" · "),
            )
            .setContentIntent(ouvrir)
            .setOngoing(e.joue)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action("prev", "Station d'avant", Poste.PRECEDENTE, 1))
            .addAction(if (e.joue) action("stop", "Arrêter", Poste.ARRETER, 2) else action("play", "Écouter", Poste.JOUER, 2))
            .addAction(action("next", "Station d'après", Poste.SUIVANTE, 3))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    override fun onDestroy() {
        syntoniseur.fermer()
        portee.cancel()
        runCatching { unregisterReceiver(ecouteurs) }
        runCatching { unregisterReceiver(bruit) }
        session.release()
        Poste.etat.value = Poste.etat.value.copy(joue = false, enRecherche = false, minuterieFin = null)
        super.onDestroy()
    }
}
