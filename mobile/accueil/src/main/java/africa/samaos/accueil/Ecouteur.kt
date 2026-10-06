package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** La réponse directe qu'une appli propose dans sa notification (« Répondre à Aminata »). */
class Reponse(val action: PendingIntent, val entrees: Array<RemoteInput>)

/** Une notification telle que le Pouls l'affiche. */
data class Notif(
    val cle: String,
    val paquet: String,
    val appli: String,
    val titre: String,
    val texte: String,
    /** Les lignes à montrer : les messages d'une conversation, les lignes d'une boîte de réception, ou le texte. */
    val lignes: List<String>,
    val heure: Long,
    val action: PendingIntent?,
    val reponse: Reponse?,
    val canal: String?,
    val effacable: Boolean,
    val effacerApresOuverture: Boolean,
) {
    /** Les notifications d'une même appli et d'un même interlocuteur se regroupent dans une carte. */
    val groupe: String get() = "$paquet|$titre"
}

/** Les notifications actives, publiées par [Ecouteur] dès que l'accès lui est accordé. */
object Notifications {
    private val _liste = MutableStateFlow<List<Notif>>(emptyList())
    val liste: StateFlow<List<Notif>> = _liste.asStateFlow()

    private val _connecte = MutableStateFlow(false)
    val connecte: StateFlow<Boolean> = _connecte.asStateFlow()

    internal var service: Ecouteur? = null

    internal fun publier(liste: List<Notif>) {
        _liste.value = liste
    }

    internal fun connexion(service: Ecouteur?) {
        this.service = service
        _connecte.value = service != null
        if (service == null) _liste.value = emptyList()
    }

    fun ouvrir(n: Notif) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // L'Accueil est au premier plan : il prête ce droit à l'appli pour qu'elle puisse s'ouvrir.
                @Suppress("DEPRECATION")
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
                n.action?.send(options)
            } else {
                n.action?.send()
            }
        } catch (_: PendingIntent.CanceledException) {
            // L'appli a retiré sa notification entre-temps.
        }
        if (n.effacerApresOuverture) effacer(n)
    }

    /** Répond depuis le Pouls, sans ouvrir l'appli : le texte part par l'action de réponse de la notification. */
    fun repondre(contexte: Context, n: Notif, texte: String): Boolean {
        val r = n.reponse ?: return false
        return try {
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            val resultats = Bundle()
            r.entrees.forEach { resultats.putCharSequence(it.resultKey, texte) }
            RemoteInput.addResultsToIntent(r.entrees, intent, resultats)
            r.action.send(contexte, 0, intent)
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }

    /** Reporter : la notification disparaît et revient après [duree] millisecondes. */
    fun reporter(n: Notif, duree: Long) {
        service?.snoozeNotification(n.cle, duree)
    }

    fun effacer(n: Notif) {
        service?.cancelNotification(n.cle)
    }

    fun toutEffacer() {
        service?.cancelAllNotifications()
    }
}

/** Le service qui donne au Pouls l'accès aux notifications des autres applis. */
class Ecouteur : NotificationListenerService() {

    override fun onListenerConnected() {
        Notifications.connexion(this)
        publier()
    }

    override fun onListenerDisconnected() {
        Notifications.connexion(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publier()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publier()

    private fun publier() {
        val actives = try {
            activeNotifications
        } catch (_: SecurityException) {
            null
        } ?: return
        val pm = packageManager
        Notifications.publier(
            actives
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                // Ce qui joue a déjà sa carte en tête du Pouls : sa notification n'y figure pas une seconde fois.
                .filter { !it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }
                .mapNotNull { versNotif(it, pm) }
                .sortedByDescending { it.heure },
        )
    }

    private fun versNotif(sbn: StatusBarNotification, pm: PackageManager): Notif? {
        val n = sbn.notification
        val extras = n.extras
        val titre = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString().orEmpty()
        val texte = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (titre.isBlank() && texte.isBlank()) return null
        val appli = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            sbn.packageName
        }
        return Notif(
            cle = sbn.key,
            paquet = sbn.packageName,
            appli = appli,
            titre = titre,
            texte = texte,
            lignes = lignes(extras, texte, titre),
            heure = sbn.postTime,
            action = n.contentIntent,
            reponse = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
                ?.let { Reponse(it.actionIntent, it.remoteInputs) },
            canal = n.channelId,
            effacable = sbn.isClearable,
            effacerApresOuverture = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }

    private fun lignes(extras: Bundle, texte: String, titre: String): List<String> {
        // Une conversation : chaque message sur sa ligne ; « Vous : » devant les siens, le prénom dans un groupe.
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null) {
            val textes = messages.mapNotNull { m ->
                val message = m as? Bundle ?: return@mapNotNull null
                val t = message.getCharSequence("text")?.toString() ?: return@mapNotNull null
                val auteur = message.getCharSequence("sender")?.toString()
                when {
                    auteur == null -> "Vous : $t"
                    auteur != titre -> "$auteur : $t"
                    else -> t
                }
            }
            if (textes.isNotEmpty()) return textes.takeLast(4)
        }
        // Une boîte de réception : ses lignes.
        val boite = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (boite != null && boite.isNotEmpty()) return boite.map { it.toString() }.take(4)
        val long = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        return listOfNotNull((long ?: texte).takeIf { it.isNotBlank() })
    }
}
