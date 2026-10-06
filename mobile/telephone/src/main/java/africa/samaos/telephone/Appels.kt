package africa.samaos.telephone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallScreeningService
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Les appels en cours, tels que le système les confie au Téléphone de Sama. */
object Appels {
    private val _liste = MutableStateFlow<List<Call>>(emptyList())
    val liste: StateFlow<List<Call>> = _liste.asStateFlow()

    /** Change à chaque évolution d'un appel (sonnerie, décroché, attente…) : l'écran se redessine. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio.asStateFlow()

    internal var service: ServiceAppels? = null

    internal fun maj(liste: List<Call>) {
        _liste.value = liste
        _version.value++
    }

    internal fun majAudio(a: CallAudioState?) {
        _audio.value = a
    }

    fun principal(): Call? = liste.value.firstOrNull { it.details.state == Call.STATE_RINGING }
        ?: liste.value.firstOrNull { it.details.state != Call.STATE_HOLDING && it.details.state != Call.STATE_DISCONNECTED }
        ?: liste.value.firstOrNull()

    fun muet(oui: Boolean) = service?.setMuted(oui)

    fun hautParleur(oui: Boolean) = service?.setAudioRoute(if (oui) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)

    fun numero(appel: Call): String = appel.details.handle?.schemeSpecificPart.orEmpty()

    /** Le dernier appel avec un inconnu : son numéro et l'heure où il s'est terminé (0 s'il dure encore). Observé par la mise en garde. */
    var inconnu by androidx.compose.runtime.mutableStateOf<Pair<String, Long>?>(null)
}

/**
 * Le service d'appels : Android lui confie chaque appel, entrant ou sortant. Un appel qui sonne passe par
 * une notification d'appel (plein écran si le téléphone dort, bandeau sinon) ; un appel décroché ou sortant
 * ouvre l'écran d'appel.
 */
class ServiceAppels : InCallService() {
    private val suivi = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Appels.maj(calls.toList())
            notifier(call)
            if (state == Call.STATE_ACTIVE) ouvrirEcran()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) = Appels.maj(calls.toList())
    }

    override fun onCreate() {
        super.onCreate()
        Appels.service = this
        canaux(this)
    }

    override fun onDestroy() {
        Appels.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(suivi)
        // Le bouclier retient un appel avec un inconnu : le mobile money attendra la fin de l'appel.
        val n = Appels.numero(call)
        if (n.isNotBlank() && Moteur.personne(this, n) == null) Appels.inconnu = n to 0L
        Appels.maj(calls.toList())
        notifier(call)
        // Le composeur par défaut peut ouvrir son écran lui-même (Android l'y autorise pour les appels) :
        // l'appel entrant prend tout l'écran, verrouillé ou non.
        ouvrirEcran()
    }

    override fun onCallRemoved(call: Call) {
        Appels.inconnu?.let { (n, fin) -> if (fin == 0L && n == Appels.numero(call)) Appels.inconnu = n to System.currentTimeMillis() }
        call.unregisterCallback(suivi)
        Appels.maj(calls.toList())
        if (calls.isEmpty()) {
            getSystemService(NotificationManager::class.java).cancel(ID_NOTIF)
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            Appels.principal()?.let { notifier(it) }
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) = Appels.majAudio(audioState)

    private fun ouvrirEcran() {
        startActivity(Intent(this, EcranAppel::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** La notification de l'appel : « appel entrant » avec Répondre et Refuser, ou « appel en cours » avec Raccrocher. */
    private fun notifier(call: Call) {
        val etat = call.details.state
        if (etat == Call.STATE_DISCONNECTED) return
        val numero = Appels.numero(call)
        val nom = Moteur.personne(this, numero)?.nom ?: Moteur.formater(numero).ifBlank { "Numéro masqué" }
        val personne = Person.Builder().setName(nom).setImportant(true).build()
        val ecran = PendingIntent.getActivity(this, 0, Intent(this, EcranAppel::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(quoi: String) = PendingIntent.getBroadcast(this, quoi.hashCode(), Intent(this, ActionAppel::class.java).setAction(quoi), PendingIntent.FLAG_IMMUTABLE)
        val n = if (etat == Call.STATE_RINGING) {
            Notification.Builder(this, CANAL_ENTRANT)
                .setSmallIcon(R.drawable.ic_notif_appel)
                .setContentTitle(nom)
                .setContentText("Appel entrant")
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(ecran, true)
                .setContentIntent(ecran)
                .setOngoing(true)
                .setStyle(Notification.CallStyle.forIncomingCall(personne, action(REFUSER), action(REPONDRE)))
                .build()
        } else {
            Notification.Builder(this, CANAL_EN_COURS)
                .setSmallIcon(R.drawable.ic_notif_appel)
                .setContentTitle(nom)
                .setContentIntent(ecran)
                .setOngoing(true)
                .setUsesChronometer(etat == Call.STATE_ACTIVE)
                .setWhen(call.details.connectTimeMillis.takeIf { it > 0 } ?: System.currentTimeMillis())
                .setStyle(Notification.CallStyle.forOngoingCall(personne, action(RACCROCHER)))
                .build()
        }
        try {
            startForeground(ID_NOTIF, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (_: Exception) {
            getSystemService(NotificationManager::class.java).notify(ID_NOTIF, n)
        }
    }

    companion object {
        const val CANAL_ENTRANT = "appels_entrants_discrets"
        private const val CANAL_ENTRANT_DISCRET = CANAL_ENTRANT
        const val CANAL_EN_COURS = "appels_en_cours"
        const val ID_NOTIF = 1
        const val REPONDRE = "africa.samaos.telephone.REPONDRE"
        const val REFUSER = "africa.samaos.telephone.REFUSER"
        const val RACCROCHER = "africa.samaos.telephone.RACCROCHER"

        fun canaux(c: Context) {
            val nm = c.getSystemService(NotificationManager::class.java)
            // L'écran d'appel s'ouvre tout seul : la notification d'appel entrant n'a pas besoin de bandeau en plus.
            nm.createNotificationChannel(NotificationChannel(CANAL_ENTRANT_DISCRET, "Appels entrants", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CANAL_EN_COURS, "Appel en cours", NotificationManager.IMPORTANCE_LOW))
        }
    }
}

/** Les boutons de la notification d'appel. */
class ActionAppel : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val appel = Appels.principal() ?: return
        when (i.action) {
            ServiceAppels.REPONDRE -> {
                appel.answer(VideoProfile.STATE_AUDIO_ONLY)
                c.startActivity(Intent(c, EcranAppel::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            ServiceAppels.REFUSER -> appel.reject(false, null)
            ServiceAppels.RACCROCHER -> appel.disconnect()
        }
    }
}

/**
 * Le filtrage des appels (maquette l2-tel-filtrage) : les numéros masqués et les inconnus, si on l'a choisi,
 * ne font pas sonner le téléphone. Ils restent dans le journal, marqués « filtré ».
 */
class FiltreAppels : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val entrant = details.callDirection == Call.Details.DIRECTION_INCOMING
        val numero = details.handle?.schemeSpecificPart.orEmpty()
        val prefs = Reglages.prefs(this)
        val filtrer = entrant && (
            (prefs.getBoolean(Reglages.MASQUES, false) && numero.isBlank()) ||
                (prefs.getBoolean(Reglages.INCONNUS, false) && numero.isNotBlank() && Moteur.personne(this, numero) == null)
            )
        val reponse = CallResponse.Builder()
            .setDisallowCall(filtrer)
            .setRejectCall(filtrer)
            .setSkipNotification(filtrer)
            .setSkipCallLog(false)
            .build()
        respondToCall(details, reponse)
    }
}

/** Les réglages du Téléphone, gardés dans l'appli. */
object Reglages {
    const val MASQUES = "bloquer_masques"
    const val INCONNUS = "bloquer_inconnus"
    fun prefs(c: Context) = c.createDeviceProtectedStorageContext().getSharedPreferences("telephone", Context.MODE_PRIVATE)
}
