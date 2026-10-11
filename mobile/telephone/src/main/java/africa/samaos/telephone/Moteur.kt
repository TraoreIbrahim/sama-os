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
class Personne(val id: Long, val cle: String, val nom: String, val numero: String, val type: String, val favori: Boolean, val photo: String? = null)

/** Une ligne du journal : un ou plusieurs appels de suite, du même numéro, le même jour, du même genre. */
class Appel(
    val numero: String,
    val nom: String?,
    val genre: Int,
    val nombre: Int,
    val date: Long,
    val duree: Long,
    val sim: Sim?,
    /** Les lignes du journal d'Android qu'elle regroupe (pour les effacer). */
    val ids: List<Long> = emptyList(),
)

/** Une personne trouvée en tapant des chiffres : par les lettres de son nom (2 = ABC…), ou par son numéro. [de] et [a] bornent ce qui correspond dans le nom. */
class Trouvee(val personne: Personne, val de: Int, val a: Int, val parNumero: Boolean)

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
        // +225 07 08 45 12 30 : l'indicatif à part, puis le numéro en paires.
        if (n.startsWith("+225") && n.length == 14) return "+225 " + n.drop(4).chunked(2).joinToString(" ")
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
            arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.TYPE, ContactsContract.PhoneLookup.STARRED, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
            null, null, null,
        )?.use { cur ->
            if (cur.moveToFirst()) Personne(cur.getLong(0), cur.getString(1).orEmpty(), cur.getString(2).orEmpty(), numero, typeNumero(cur.getInt(3)), cur.getInt(4) == 1, cur.getString(5)) else null
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
                COLONNES,
                null, null, null,
            )?.use { cur ->
                while (cur.moveToNext() && liste.size < 3) {
                    liste += lirePersonne(cur)
                }
            }
        } catch (_: Exception) {
        }
        return liste.distinctBy { it.numero.filter { ch -> ch.isDigit() } }
    }

    private val COLONNES = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.STARRED,
        ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
    )

    private fun lirePersonne(cur: android.database.Cursor) = Personne(
        cur.getLong(0), cur.getString(1).orEmpty(), cur.getString(2).orEmpty(), cur.getString(3).orEmpty(), typeNumero(cur.getInt(4)), cur.getInt(5) == 1, cur.getString(6),
    )

    /** Tous les contacts qui ont un numéro, un par personne (son numéro préféré, sinon le premier), dans l'ordre des noms. */
    fun tous(c: Context): List<Personne> {
        val liste = mutableListOf<Personne>()
        try {
            c.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, COLONNES, null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED, ${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
            )?.use { cur -> while (cur.moveToNext()) liste += lirePersonne(cur) }
        } catch (_: Exception) {
        }
        val tri = java.text.Collator.getInstance(Locale.FRENCH)
        return liste.distinctBy { it.id }.sortedWith { x, y -> tri.compare(x.nom, y.nom) }
    }

    fun favoris(c: Context): List<Personne> {
        val liste = mutableListOf<Personne>()
        try {
            c.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                COLONNES,
                "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1", null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            )?.use { cur ->
                while (cur.moveToNext()) liste += lirePersonne(cur)
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
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls._ID),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { cur ->
                while (cur.moveToNext() && bruts.size < limite) {
                    bruts += Appel(
                        cur.getString(0).orEmpty(), cur.getString(1)?.takeIf { it.isNotBlank() }, cur.getInt(2), 1,
                        cur.getLong(3), cur.getLong(4), sims.firstOrNull { it.compte.id == cur.getString(5) }, listOf(cur.getLong(6)),
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
                groupes[groupes.lastIndex] = Appel(dernier.numero, dernier.nom, dernier.genre, dernier.nombre + 1, dernier.date, dernier.duree, dernier.sim, dernier.ids + a.ids)
            } else {
                groupes += a
            }
        }
        return groupes
    }

    /** Les appels échangés avec ce numéro, du plus récent au plus ancien (pour sa fiche dans les récents). */
    fun historique(c: Context, numero: String, limite: Int = 12): List<Appel> {
        val sims = sims(c)
        val liste = mutableListOf<Appel>()
        try {
            c.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls._ID),
                "${CallLog.Calls.NUMBER} = ?", arrayOf(numero), "${CallLog.Calls.DATE} DESC",
            )?.use { cur ->
                while (cur.moveToNext() && liste.size < limite) {
                    liste += Appel(cur.getString(0).orEmpty(), cur.getString(1), cur.getInt(2), 1, cur.getLong(3), cur.getLong(4), sims.firstOrNull { it.compte.id == cur.getString(5) }, listOf(cur.getLong(6)))
                }
            }
        } catch (_: Exception) {
        }
        return liste
    }

    /** Efface ces lignes du journal (glisser une ligne des récents, « Effacer l'historique » d'un numéro). */
    fun effacerAppels(c: Context, ids: List<Long>): Boolean = try {
        ids.chunked(200).forEach { lot ->
            c.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} IN (${lot.joinToString(",")})", null)
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Un numéro de l'étranger : il commence par + ou 00, sans l'indicatif de la Côte d'Ivoire. */
    fun etranger(numero: String): Boolean {
        val n = numero.filter { it.isDigit() || it == '+' }
        return (n.startsWith("+") && !n.startsWith("+225")) || (n.startsWith("00") && !n.startsWith("00225"))
    }

    // Recherche au clavier

    private val TOUCHES = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2', 'd' to '3', 'e' to '3', 'f' to '3', 'g' to '4', 'h' to '4', 'i' to '4',
        'j' to '5', 'k' to '5', 'l' to '5', 'm' to '6', 'n' to '6', 'o' to '6', 'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8', 'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
        'ɛ' to '3', 'ɔ' to '6', 'ɲ' to '6', 'ŋ' to '6',
    )

    private fun sansAccents(s: String) = java.text.Normalizer.normalize(s.lowercase(Locale.FRENCH), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    @Volatile private var cacheTous: Pair<Long, List<Personne>>? = null

    /**
     * Les contacts qui répondent aux chiffres tapés : un mot du nom qui commence par ces touches (« 2547 » : Akissi),
     * le nom entier tapé d'un trait, ou un numéro qui contient ces chiffres. Les noms d'abord.
     */
    fun chercherAuClavier(c: Context, chiffres: String): List<Trouvee> {
        val d = chiffres.filter { it.isDigit() }
        if (d.length < 2 || d.length != chiffres.length) return emptyList()
        // La liste des contacts est relue au plus toutes les 30 secondes, pas à chaque touche.
        val cache = cacheTous
        val tous = if (cache != null && System.currentTimeMillis() - cache.first < 30_000) cache.second else tous(c).also { cacheTous = System.currentTimeMillis() to it }
        val parNom = mutableListOf<Trouvee>()
        val parNumero = mutableListOf<Trouvee>()
        tous.forEach { p ->
            val nom = sansAccents(p.nom)
            // Chaque lettre du nom, sa touche ; les espaces et tirets coupent les mots.
            var debutMot = 0
            var trouve: Trouvee? = null
            for (i in 0..nom.length) {
                if (i == nom.length || nom[i] == ' ' || nom[i] == '-') {
                    if (trouve == null && i > debutMot) {
                        val mot = nom.substring(debutMot, i)
                        val code = mot.map { TOUCHES[it] ?: if (it.isDigit()) it else '·' }.joinToString("")
                        if (code.startsWith(d)) trouve = Trouvee(p, debutMot, debutMot + d.length, false)
                    }
                    debutMot = i + 1
                }
            }
            if (trouve == null) {
                // Le nom entier, tapé d'un trait sans l'espace : « Akissi Kouassi » = 2547745687724.
                val lettres = nom.withIndex().filter { it.value != ' ' && it.value != '-' }
                val code = lettres.map { TOUCHES[it.value] ?: '·' }.joinToString("")
                if (d.length > 3 && code.startsWith(d)) trouve = Trouvee(p, 0, lettres[d.length - 1].index + 1, false)
            }
            when {
                trouve != null -> parNom += trouve
                d.length >= 3 && p.numero.filter { it.isDigit() }.contains(d) -> parNumero += Trouvee(p, 0, 0, true)
            }
        }
        return (parNom.sortedWith(compareByDescending<Trouvee> { it.personne.favori }.thenBy { it.de }) + parNumero).distinctBy { it.personne.id }.take(12)
    }

    /** Marque une personne d'une étoile (ou la retire) : elle apparaît dans les favoris du Téléphone, des Contacts et de l'Accueil simplifié. */
    fun favori(c: Context, p: Personne, oui: Boolean): Boolean = try {
        c.contentResolver.update(
            ContactsContract.Contacts.CONTENT_URI, ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (oui) 1 else 0) },
            "${ContactsContract.Contacts._ID} = ?", arrayOf(p.id.toString()),
        ) > 0
    } catch (_: Exception) {
        false
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

    /** Combien de fois on a appelé ce numéro (pour « Vous l'avez déjà appelée 2 fois »). */
    fun appelsVers(c: Context, numero: String): Int = try {
        c.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID), "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.TYPE} = ?",
            arrayOf(numero, CallLog.Calls.OUTGOING_TYPE.toString()), null,
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
