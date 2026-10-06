package africa.samaos.messages

import android.annotation.SuppressLint
import android.app.IntentService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Une conversation : un fil d'Android, son correspondant, son dernier message. */
class Conversation(val fil: Long, val adresse: String, val nom: String?, val dernier: String, val date: Long, val nonLus: Int, val service: Boolean)

/** Un SMS d'une conversation. */
class Sms(val id: Long, val corps: String, val date: Long, val recu: Boolean, val etat: Int, val sim: Int?)

/** Une SIM qui envoie des SMS. */
class SimSms(val fente: Int, val operateur: String, val id: Int)

/**
 * La boîte des SMS d'Android, tenue par Messages quand il est l'appli de SMS par défaut :
 * il range lui-même ce qu'il reçoit et ce qu'il envoie.
 */
@SuppressLint("MissingPermission")
object Boite {
    /** Un expéditeur qui n'est pas une personne : un nom (ORANGE, MTN MoMo) ou un numéro court. */
    fun estService(adresse: String) = adresse.any { it.isLetter() } || adresse.filter { it.isDigit() }.length in 1..6

    fun conversations(c: Context): List<Conversation> {
        val parFil = LinkedHashMap<Long, Conversation>()
        val nonLus = HashMap<Long, Int>()
        val noms = HashMap<String, String?>()
        try {
            c.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE),
                null, null, "${Telephony.Sms.DATE} DESC",
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val fil = cur.getLong(0)
                    if (cur.getInt(4) == 0 && cur.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX) nonLus[fil] = (nonLus[fil] ?: 0) + 1
                    if (fil in parFil) continue
                    val adresse = cur.getString(1).orEmpty()
                    val nom = noms.getOrPut(adresse) { nomDe(c, adresse) }
                    parFil[fil] = Conversation(fil, adresse, nom, cur.getString(2).orEmpty(), cur.getLong(3), 0, nom == null && estService(adresse))
                }
            }
        } catch (_: SecurityException) {
        }
        return parFil.values.map { Conversation(it.fil, it.adresse, it.nom, it.dernier, it.date, nonLus[it.fil] ?: 0, it.service) }
    }

    fun messages(c: Context, fil: Long): List<Sms> {
        val l = mutableListOf<Sms>()
        val sims = sims(c)
        try {
            c.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS, Telephony.Sms.SUBSCRIPTION_ID),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(fil.toString()), "${Telephony.Sms.DATE} ASC",
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val type = cur.getInt(3)
                    l += Sms(cur.getLong(0), cur.getString(1).orEmpty(), cur.getLong(2), type == Telephony.Sms.MESSAGE_TYPE_INBOX, type, sims.firstOrNull { it.id == cur.getInt(5) }?.fente)
                }
            }
        } catch (_: SecurityException) {
        }
        return l
    }

    fun filDe(c: Context, adresse: String): Long = try {
        Telephony.Threads.getOrCreateThreadId(c, adresse)
    } catch (_: Exception) {
        -1
    }

    fun marquerLu(c: Context, fil: Long) {
        try {
            c.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                },
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(fil.toString()),
            )
        } catch (_: Exception) {
        }
        c.getSystemService(NotificationManager::class.java).cancel(fil.toInt())
    }

    fun supprimer(c: Context, fil: Long) {
        try {
            c.contentResolver.delete(Uri.parse("content://mms-sms/conversations/$fil"), null, null)
        } catch (_: Exception) {
        }
    }

    fun nomDe(c: Context, adresse: String): String? = try {
        if (adresse.isBlank()) null else c.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(adresse)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    fun sims(c: Context): List<SimSms> = try {
        c.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
            .map { SimSms(it.simSlotIndex + 1, (it.carrierName ?: "").toString(), it.subscriptionId) }.sortedBy { it.fente }
    } catch (_: SecurityException) {
        emptyList()
    }

    /** Envoyer un SMS : il est rangé dans la boîte tout de suite, puis marqué envoyé ou en échec. */
    fun envoyer(c: Context, adresse: String, texte: String, sim: SimSms?) {
        val uri = try {
            c.contentResolver.insert(
                Telephony.Sms.Sent.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, adresse)
                    put(Telephony.Sms.BODY, texte)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                    if (sim != null) put(Telephony.Sms.SUBSCRIPTION_ID, sim.id)
                },
            )
        } catch (_: Exception) {
            null
        }
        val sm = c.getSystemService(SmsManager::class.java).let { if (sim != null) it.createForSubscriptionId(sim.id) else it }
        val parties = sm.divideMessage(texte)
        val suivi = PendingIntent.getBroadcast(
            c, (uri?.hashCode() ?: 0), Intent(c, Envoi::class.java).setData(uri),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            sm.sendMultipartTextMessage(adresse, null, parties, ArrayList(parties.map { suivi }), null)
        } catch (_: Exception) {
            uri?.let { marquer(c, it, Telephony.Sms.MESSAGE_TYPE_FAILED) }
        }
    }

    fun marquer(c: Context, uri: Uri, type: Int) {
        try {
            c.contentResolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null)
        } catch (_: Exception) {
        }
    }

    // Notifications

    const val CANAL = "messages"
    const val CANAL_SERVICES = "services"
    const val CLE_REPONSE = "reponse"

    fun canaux(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Conversations", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CANAL_SERVICES, "Opérateurs et services", NotificationManager.IMPORTANCE_LOW))
    }

    /** La notification d'une conversation : ses derniers messages, et « Répondre » sans ouvrir l'appli. */
    fun notifier(c: Context, fil: Long, adresse: String, alerte: africa.samaos.bouclier.Verdict? = null) {
        canaux(c)
        val nom = nomDe(c, adresse)
        val service = nom == null && estService(adresse)
        val moi = Person.Builder().setName("Vous").build()
        val autre = Person.Builder().setName(nom ?: adresse).build()
        val style = Notification.MessagingStyle(moi)
        messages(c, fil).takeLast(5).forEach { m -> style.addMessage(m.corps, m.date, if (m.recu) autre else null) }
        val ouvrir = PendingIntent.getActivity(
            c, fil.toInt(), Intent(c, Messages::class.java).putExtra("fil", fil).putExtra("adresse", adresse),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(c, if (service) CANAL_SERVICES else CANAL)
            .setSmallIcon(R.drawable.ic_notif_message)
            .setStyle(style)
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
        // L'arnaque probable se voit dès la notification, avant même d'ouvrir le message.
        if (alerte != null) {
            b.setStyle(Notification.BigTextStyle().bigText(alerte.raison + " N'envoyez rien et ne rappelez pas ce numéro."))
                .setContentTitle("${alerte.titre} · ${formater(adresse)}")
                .setContentText(alerte.raison)
        }
        if (!service && alerte == null) {
            val repondre = PendingIntent.getBroadcast(
                c, fil.toInt(), Intent(c, ReponseNotif::class.java).putExtra("fil", fil).putExtra("adresse", adresse),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(
                Notification.Action.Builder(null, "Répondre", repondre)
                    .addRemoteInput(RemoteInput.Builder(CLE_REPONSE).setLabel("Répondre à ${nom ?: adresse}").build())
                    .build(),
            )
        }
        c.getSystemService(NotificationManager::class.java).notify(fil.toInt(), b.build())
    }

    // Le bouclier

    /** Le verdict du bouclier sur un SMS reçu (null : rien à signaler, ou bouclier coupé). */
    fun verdict(c: Context, adresse: String, corps: String): africa.samaos.bouclier.Verdict? {
        val b = africa.samaos.bouclier.Bouclier
        val sms = if (b.actif(c, africa.samaos.bouclier.Bouclier.Garde.SMS)) b.sms(adresse, corps, nomDe(c, adresse) != null) else null
        return sms ?: if (b.actif(c, africa.samaos.bouclier.Bouclier.Garde.LIENS)) b.lien(corps) else null
    }

    /** Le vrai solde : le dernier annoncé par l'expéditeur officiel de cet opérateur. */
    fun vraiSolde(c: Context, op: africa.samaos.bouclier.Operateur): String? = try {
        c.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            "${Telephony.Sms.BODY} LIKE '%solde%'", null, "${Telephony.Sms.DATE} DESC",
        )?.use { cur ->
            while (cur.moveToNext()) {
                val a = cur.getString(0).orEmpty()
                val b = cur.getString(1).orEmpty()
                if (africa.samaos.bouclier.Bouclier.officiel(a) && africa.samaos.bouclier.Bouclier.operateurCite(b) == op && !b.contains("reçu de", true)) {
                    return@use africa.samaos.bouclier.Bouclier.solde(b)
                }
            }
            null
        }
    } catch (_: Exception) {
        null
    }

    /** Bloquer un numéro : Android ne laisse plus passer ses appels ni ses SMS (liste du système). */
    fun bloquer(c: Context, adresse: String): Boolean = try {
        c.contentResolver.insert(
            android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            ContentValues().apply { put(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, adresse) },
        ) != null
    } catch (_: Exception) {
        false
    }

    fun estBloque(c: Context, adresse: String): Boolean = try {
        android.provider.BlockedNumberContract.isBlocked(c, adresse)
    } catch (_: Exception) {
        false
    }

    // Dates

    fun quand(ms: Long): String {
        val z = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(ms).atZone(z)
        val auj = LocalDate.now(z)
        return when {
            d.toLocalDate() == auj -> d.format(DateTimeFormatter.ofPattern("HH:mm"))
            d.toLocalDate() == auj.minusDays(1) -> "Hier"
            d.toLocalDate().isAfter(auj.minusDays(7)) -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
            else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
        }
    }

    fun formater(n: String): String {
        val d = n.filter { it.isDigit() || it == '+' }
        return if (n.any { it.isLetter() } || d.isEmpty()) n else if (d.startsWith("+")) d else d.chunked(2).joinToString(" ")
    }
}

/** Un SMS reçu : rangé dans la boîte, puis notifié. */
class RecuSms : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
        if (parts.isEmpty()) return
        val adresse = parts[0].displayOriginatingAddress.orEmpty()
        val corps = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val sub = i.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
        val uri = try {
            c.contentResolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, adresse)
                    put(Telephony.Sms.BODY, corps)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.DATE_SENT, parts[0].timestampMillis)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    if (sub >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, sub)
                },
            )
        } catch (_: Exception) {
            null
        } ?: return
        val fil = c.contentResolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return
        val v = Boite.verdict(c, adresse, corps)
        if (v != null) africa.samaos.bouclier.Bouclier.noter(c, "sms", v.titre, "SMS du ${Boite.formater(adresse)} · ${v.raison}")
        // Un SMS de l'opérateur : crédit, data, fin de forfait (innovation 1).
        if (v == null && africa.samaos.bouclier.Bouclier.officiel(adresse)) AlertesSoldes.recu(c, sub, corps)
        Boite.notifier(c, fil, adresse, v)
    }
}

/** Un MMS annoncé : Sama ne les télécharge pas encore, il prévient seulement. */
class RecuMms : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {}
}

/** Le résultat d'un envoi : envoyé, ou en échec (pas de réseau, crédit épuisé). */
class Envoi : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val uri = i.data ?: return
        Boite.marquer(c, uri, if (resultCode == android.app.Activity.RESULT_OK) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED)
    }
}

/** « Répondre » depuis la notification (ou depuis le Pouls). */
class ReponseNotif : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val texte = RemoteInput.getResultsFromIntent(i)?.getCharSequence(Boite.CLE_REPONSE)?.toString()?.takeIf { it.isNotBlank() } ?: return
        val adresse = i.getStringExtra("adresse") ?: return
        val fil = i.getLongExtra("fil", -1)
        Boite.envoyer(c, adresse, texte, null)
        if (fil >= 0) {
            Boite.marquerLu(c, fil)
            Boite.notifier(c, fil, adresse)
        }
    }
}

/** Le message qui part quand on refuse un appel par un SMS. */
@Suppress("DEPRECATION")
class ReponseAppel : IntentService("ReponseAppel") {
    @Deprecated("IntentService suffit pour un envoi ponctuel")
    override fun onHandleIntent(i: Intent?) {
        val adresse = i?.data?.schemeSpecificPart?.substringBefore('?') ?: return
        val texte = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        Boite.envoyer(this, adresse, texte, null)
    }
}
