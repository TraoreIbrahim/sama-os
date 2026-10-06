package africa.samaos.telephone

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Une SIM qui peut appeler : sa fente, son opérateur, son compte d'appel pour Android. */
class Sim(val fente: Int, val operateur: String, val compte: PhoneAccountHandle, val idAbonnement: Int)

/** Une personne trouvée dans les contacts. */
class Personne(val id: Long, val cle: String, val nom: String, val numero: String, val type: String, val favori: Boolean)

/** Une ligne du journal : un ou plusieurs appels de suite, du même numéro, le même jour, du même genre. */
class Appel(
    val numero: String,
    val nom: String?,
    val genre: Int,
    val nombre: Int,
    val date: Long,
    val duree: Long,
    val sim: Sim?,
)

@SuppressLint("MissingPermission")
object Moteur {
    fun telecom(c: Context) = c.getSystemService(TelecomManager::class.java)

    /** Les SIM qui peuvent appeler, dans l'ordre des fentes. */
    fun sims(c: Context): List<Sim> = try {
        val sm = c.getSystemService(SubscriptionManager::class.java)
        val infos = sm.activeSubscriptionInfoList.orEmpty()
        val tm = c.getSystemService(TelephonyManager::class.java)
        telecom(c).callCapablePhoneAccounts.mapNotNull { h ->
            val id = try {
                tm.getSubscriptionId(h)
            } catch (_: Exception) {
                -1
            }
            val info: SubscriptionInfo? = infos.firstOrNull { it.subscriptionId == id }
            info?.let { Sim(it.simSlotIndex + 1, (it.carrierName ?: it.displayName ?: "").toString(), h, id) }
        }.sortedBy { it.fente }
    } catch (_: SecurityException) {
        emptyList()
    }

    fun simDuCompte(c: Context, idCompte: String?): Sim? = sims(c).firstOrNull { it.compte.id == idCompte }

    /** Appeler : Android passe l'appel par la SIM choisie, ou demande laquelle s'il y en a plusieurs. */
    fun appeler(c: Context, numero: String, sim: Sim? = null) {
        val extras = Bundle()
        if (sim != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim.compte)
        try {
            telecom(c).placeCall(Uri.fromParts("tel", numero, null), extras)
        } catch (_: SecurityException) {
        }
    }

    fun appelerMessagerie(c: Context, sim: Sim?) {
        val extras = Bundle()
        if (sim != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim.compte)
        try {
            telecom(c).placeCall(Uri.fromParts("voicemail", "", null), extras)
        } catch (_: SecurityException) {
        }
    }

    fun numeroMessagerie(c: Context, sim: Sim): String? = try {
        c.getSystemService(TelephonyManager::class.java).createForSubscriptionId(sim.idAbonnement).voiceMailNumber?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun estUssd(numero: String) = (numero.startsWith("*") || numero.startsWith("#")) && numero.endsWith("#")

    /** Un code USSD (#111#, *133#) : la réponse de l'opérateur arrive dans [reponse]. */
    fun ussd(c: Context, code: String, sim: Sim?, reponse: (String?) -> Unit) {
        try {
            val tm0 = c.getSystemService(TelephonyManager::class.java)
            val tm = if (sim != null) tm0.createForSubscriptionId(sim.idAbonnement) else tm0
            tm.sendUssdRequest(
                code,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(t: TelephonyManager, req: String, r: CharSequence) = reponse(r.toString())
                    override fun onReceiveUssdResponseFailed(t: TelephonyManager, req: String, code: Int) = reponse(null)
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: Exception) {
            reponse(null)
        }
    }

    // Numéros

    /** « 07 08 45 12 30 » : un numéro ivoirien en paires. */
    fun formater(numero: String): String {
        val n = numero.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (n.startsWith("+") || n.any { it == '*' || it == '#' }) return n
        // Les numéros courts (secours, services) se lisent d'un bloc : 185, pas « 18 5 ».
        if (n.length <= 5) return n
        return n.chunked(2).joinToString(" ")
    }

    /** Les numéros des secours en Côte d'Ivoire, nommés sur l'écran d'appel et dans le journal. */
    fun secours(numero: String): String? = when (numero.filter { it.isDigit() }) {
        "185" -> "SAMU"
        "180" -> "Pompiers"
        "170", "111" -> "Police"
        "112" -> "Urgences"
        else -> null
    }

    /** L'opérateur d'un numéro ivoirien à dix chiffres, d'après ses deux premiers chiffres. */
    fun operateurDe(numero: String): String? {
        var d = numero.filter { it.isDigit() }
        if (d.startsWith("225") && d.length >= 12) d = d.drop(3)
        if (d.length < 2) return null
        return when (d.take(2)) {
            "07" -> "Orange"
            "05" -> "MTN"
            "01" -> "Moov Africa"
            "27", "21", "25" -> "Fixe"
            else -> null
        }
    }

    // Contacts

    fun personne(c: Context, numero: String): Personne? = try {
        c.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(numero)),
            arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.TYPE, ContactsContract.PhoneLookup.STARRED),
            null, null, null,
        )?.use { cur ->
            if (cur.moveToFirst()) Personne(cur.getLong(0), cur.getString(1).orEmpty(), cur.getString(2).orEmpty(), numero, typeNumero(cur.getInt(3)), cur.getInt(4) == 1) else null
        }
    } catch (_: Exception) {
        null
    }

    fun typeNumero(t: Int) = when (t) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Maison"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK, ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> "Travail"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Fixe"
        else -> "Téléphone"
    }

    /** Les contacts dont le numéro contient ces chiffres, ou dont le nom commence par ces lettres. */
    fun chercher(c: Context, chiffres: String): List<Personne> {
        if (chiffres.length < 2) return emptyList()
        val liste = mutableListOf<Personne>()
        try {
            c.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(chiffres)),
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.STARRED,
                ),
                null, null, null,
            )?.use { cur ->
                while (cur.moveToNext() && liste.size < 3) {
                    liste += Personne(cur.getLong(0), cur.getString(1).orEmpty(), cur.getString(2).orEmpty(), cur.getString(3).orEmpty(), typeNumero(cur.getInt(4)), cur.getInt(5) == 1)
                }
            }
        } catch (_: Exception) {
        }
        return liste.distinctBy { it.numero.filter { ch -> ch.isDigit() } }
    }

    fun favoris(c: Context): List<Personne> {
        val liste = mutableListOf<Personne>()
        try {
            c.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.STARRED,
                ),
                "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1", null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            )?.use { cur ->
                while (cur.moveToNext()) liste += Personne(cur.getLong(0), cur.getString(1).orEmpty(), cur.getString(2).orEmpty(), cur.getString(3).orEmpty(), typeNumero(cur.getInt(4)), true)
            }
        } catch (_: Exception) {
        }
        return liste.distinctBy { it.id }
    }

    // Journal

    fun journal(c: Context, limite: Int = 300): List<Appel> {
        val sims = sims(c)
        val bruts = mutableListOf<Appel>()
        try {
            c.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { cur ->
                while (cur.moveToNext() && bruts.size < limite) {
                    bruts += Appel(
                        cur.getString(0).orEmpty(), cur.getString(1)?.takeIf { it.isNotBlank() }, cur.getInt(2), 1,
                        cur.getLong(3), cur.getLong(4), sims.firstOrNull { it.compte.id == cur.getString(5) },
                    )
                }
            }
        } catch (_: Exception) {
        }
        // Les appels de suite, du même numéro, du même genre et du même jour, se regroupent.
        val groupes = mutableListOf<Appel>()
        bruts.forEach { a ->
            val dernier = groupes.lastOrNull()
            if (dernier != null && dernier.numero == a.numero && dernier.genre == a.genre && jour(dernier.date) == jour(a.date)) {
                groupes[groupes.lastIndex] = Appel(dernier.numero, dernier.nom, dernier.genre, dernier.nombre + 1, dernier.date, dernier.duree, dernier.sim)
            } else {
                groupes += a
            }
        }
        return groupes
    }

    /** Les numéros appelés le plus souvent ces 30 derniers jours (hors favoris). */
    fun souvent(c: Context, sauf: Set<String>): List<Pair<String, Int>> {
        val depuis = System.currentTimeMillis() - 30L * 24 * 3600_000
        val compte = HashMap<String, Int>()
        try {
            c.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER), "${CallLog.Calls.DATE} > ? AND ${CallLog.Calls.TYPE} != ?",
                arrayOf(depuis.toString(), CallLog.Calls.BLOCKED_TYPE.toString()), null,
            )?.use { cur -> while (cur.moveToNext()) cur.getString(0)?.takeIf { it.isNotBlank() }?.let { compte[it] = (compte[it] ?: 0) + 1 } }
        } catch (_: Exception) {
        }
        return compte.entries.filter { e -> sauf.none { s -> s.filter { it.isDigit() }.endsWith(e.key.filter { it.isDigit() }.takeLast(8)) } }
            .sortedByDescending { it.value }.take(5).map { it.key to it.value }
    }

    /** Combien de fois ce numéro a appelé depuis hier (manqués, refusés ou décrochés). */
    fun appelsRecents(c: Context, numero: String): Int = try {
        c.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
            "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} > ? AND ${CallLog.Calls.TYPE} != ?",
            arrayOf(numero, (System.currentTimeMillis() - 24 * 3600_000L).toString(), CallLog.Calls.OUTGOING_TYPE.toString()), null,
        )?.use { it.count } ?: 0
    } catch (_: Exception) {
        0
    }

    fun dernierManque(c: Context, numero: String): Long? = try {
        c.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.DATE),
            "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.TYPE} = ?", arrayOf(numero, CallLog.Calls.MISSED_TYPE.toString()), "${CallLog.Calls.DATE} DESC",
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    } catch (_: Exception) {
        null
    }

    fun filtresSemaine(c: Context): Int = try {
        c.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID), "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?",
            arrayOf(CallLog.Calls.BLOCKED_TYPE.toString(), (System.currentTimeMillis() - 7L * 24 * 3600_000).toString()), null,
        )?.use { it.count } ?: 0
    } catch (_: Exception) {
        0
    }

    // Numéros bloqués (le composeur par défaut y a droit)

    fun bloques(c: Context): List<Pair<String, Long>> {
        val liste = mutableListOf<Pair<String, Long>>()
        try {
            c.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, BlockedNumberContract.BlockedNumbers.COLUMN_ID),
                null, null, null,
            )?.use { cur -> while (cur.moveToNext()) liste += cur.getString(0) to cur.getLong(1) }
        } catch (_: Exception) {
        }
        return liste
    }

    fun bloquer(c: Context, numero: String) = try {
        c.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, numero) })
        true
    } catch (_: Exception) {
        false
    }

    fun debloquer(c: Context, numero: String) = try {
        BlockedNumberContract.unblock(c, numero)
        true
    } catch (_: Exception) {
        false
    }

    fun estBloque(c: Context, numero: String) = try {
        BlockedNumberContract.isBlocked(c, numero)
    } catch (_: Exception) {
        false
    }

    // Dates

    private fun jour(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun titreJour(ms: Long): String {
        val j = jour(ms)
        val auj = LocalDate.now()
        return when (j) {
            auj -> "Aujourd'hui"
            auj.minusDays(1) -> "Hier"
            else -> j.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
        }
    }

    fun heure(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun duree(s: Long): String = when {
        s <= 0 -> ""
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} min"
        else -> "${s / 3600} h ${(s % 3600) / 60} min"
    }
}
