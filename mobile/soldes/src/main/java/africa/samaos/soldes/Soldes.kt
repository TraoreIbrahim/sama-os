package africa.samaos.soldes

import android.content.Context
import android.provider.Settings
import android.provider.Telephony
import africa.samaos.bouclier.Bouclier
import org.json.JSONObject
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ce qu'un SMS (ou une réponse USSD) de l'opérateur apprend : chaque valeur est facultative. */
class Lecture(
    val credit: Long? = null,
    val dataReste: Double? = null,
    val dataTotal: Double? = null,
    val expire: Long? = null,
    val forfait: String? = null,
) {
    val vide get() = credit == null && dataReste == null && dataTotal == null && expire == null
}

/** Ce que l'on sait d'une SIM, chaque valeur avec l'heure où elle a été lue. Data en Mo. */
class Releve(
    val sub: Int,
    val credit: Long?,
    val creditQuand: Long,
    val dataReste: Double?,
    val dataTotal: Double?,
    val dataQuand: Long,
    val expire: Long?,
    val forfait: String?,
) {
    val quand get() = maxOf(creditQuand, dataQuand)
}

/**
 * Les soldes et forfaits (innovation 1, maquettes i1-*) : lus dans les SMS des expéditeurs officiels et dans
 * les réponses USSD. Les SMS restent la source : on les relit à la demande, rien n'est recopié ailleurs ;
 * seule la dernière réponse USSD est notée (`sama_soldes_ussd`). Le solde mobile money n'est jamais gardé.
 */
object Soldes {
    private const val CLE_USSD = "sama_soldes_ussd"

    private fun normaliser(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(' ', ' ').replace(' ', ' ').replace('’', '\'')

    private const val NOMBRE = "([0-9]{1,3}(?:[ .][0-9]{3})+|[0-9]+)(?:,[0-9]{1,2})?"
    private val MONTANT = Regex("$NOMBRE\\s*(?:fcfa|f cfa|xof|f\\b|frs?\\b)")
    private val VOLUME = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(go|gb|mo|mb|ko|kb)\\b")
    private val CREDIT = Regex("(?:solde|credit)(?: principal| de communication| compte| disponible| actuel)?(?: est)?(?: de)?[^0-9]{0,25}?$NOMBRE\\s*(?:fcfa|f cfa|xof|f\\b|frs?\\b)")
    private val RESTE = Regex("(?:il vous reste|reste|restant|disponible|solde (?:internet|data|de data|mobile data))[^0-9]{0,30}?([0-9]+(?:[.,][0-9]+)?)\\s*(go|gb|mo|mb|ko|kb)\\b")
    private val RESTE_APRES = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(go|gb|mo|mb|ko|kb)\\s*(?:restants?|disponibles?)")
    private val SUR = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(go|gb|mo|mb|ko|kb)\\s*sur\\s*(?:votre |vos |les |un |une )?(?:pass |forfait )?([0-9]+(?:[.,][0-9]+)?)\\s*(go|gb|mo|mb|ko|kb)")
    private val ACTIVATION = Regex("(?:activ|souscri|achat|achete|beneficiez|vous avez recu)")
    private val DATE = Regex("(?:valable|valide|expire|expir|jusqu'?au|jusqu a|fin le|avant le|au)\\D{0,20}?(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?(?:\\D{1,8}(\\d{1,2})\\s*[h:]\\s*(\\d{2})?)?")
    private val JOURS = Regex("valable\\s+(\\d{1,3})\\s*(?:jours?|j\\b)")
    private val DEMAIN = Regex("(?:expire|valable|jusqu'?a)[^.]{0,20}demain")
    private val CE_SOIR = Regex("(?:expire|valable|jusqu'?a)[^.]{0,20}(?:aujourd'?hui|ce soir|minuit)")
    /** Un SMS de mobile money : il parle d'argent envoyé ou reçu, pas du crédit de la SIM. */
    private val ARGENT = Regex("(money|momo|flooz|wave|transfert|retrait|depot|recu de|envoye a)")

    private fun nombre(s: String) = s.replace(" ", "").replace(".", "").toLongOrNull()

    private fun mo(n: String, unite: String): Double? {
        val v = n.replace(',', '.').toDoubleOrNull() ?: return null
        return when (unite) {
            "go", "gb" -> v * 1024
            "ko", "kb" -> v / 1024
            else -> v
        }
    }

    /** Lire un texte d'opérateur. */
    fun analyser(texte: String, maintenant: Long = System.currentTimeMillis()): Lecture {
        val t = normaliser(texte)
        if (ARGENT.containsMatchIn(t)) return Lecture()
        val credit = CREDIT.find(t)?.groupValues?.get(1)?.let { nombre(it) }
        var reste: Double? = null
        var total: Double? = null
        SUR.find(t)?.let { m ->
            reste = mo(m.groupValues[1], m.groupValues[2])
            total = mo(m.groupValues[3], m.groupValues[4])
        }
        if (reste == null) {
            (RESTE.find(t) ?: RESTE_APRES.find(t))?.let { m -> reste = mo(m.groupValues[1], m.groupValues[2]) }
        }
        var forfait: String? = null
        // Un forfait qu'on vient d'acheter : tout son volume reste.
        if (total == null && ACTIVATION.containsMatchIn(t)) {
            VOLUME.find(t)?.let { m ->
                total = mo(m.groupValues[1], m.groupValues[2])
                forfait = "Forfait " + texteVolume(total!!)
                if (reste == null) reste = total
            }
        }
        if (forfait == null && total != null) forfait = "Forfait " + texteVolume(total!!)
        val expire = finDe(t, maintenant)
        return Lecture(credit, reste, total, expire, forfait)
    }

    private fun finDe(t: String, maintenant: Long): Long? {
        val zone = ZoneId.systemDefault()
        val auj = Instant.ofEpochMilli(maintenant).atZone(zone).toLocalDate()
        fun minuit(d: LocalDate) = d.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        DATE.find(t)?.let { m ->
            val jour = m.groupValues[1].toIntOrNull() ?: return@let
            val mois = m.groupValues[2].toIntOrNull() ?: return@let
            if (jour !in 1..31 || mois !in 1..12) return@let
            var annee = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: auj.year
            // « jusqu'au 03/01 » lu en décembre : c'est l'an prochain.
            if (m.groupValues[3].isEmpty() && LocalDate.of(annee, mois, 1).plusMonths(1).isBefore(auj.withDayOfMonth(1))) annee++
            val heure = m.groupValues[4].toIntOrNull()?.takeIf { it in 0..23 }
            val minute = m.groupValues[5].toIntOrNull() ?: 0
            return try {
                LocalDateTime.of(LocalDate.of(annee, mois, jour), if (heure != null) LocalTime.of(heure, minute.coerceIn(0, 59)) else LocalTime.of(23, 59))
                    .atZone(zone).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
        JOURS.find(t)?.groupValues?.get(1)?.toLongOrNull()?.let { return minuit(auj.plusDays(it - 1)) }
        if (DEMAIN.containsMatchIn(t)) return minuit(auj.plusDays(1))
        if (CE_SOIR.containsMatchIn(t)) return minuit(auj)
        return null
    }

    // ——— Les sources ———

    /** Les SMS des expéditeurs officiels des 30 derniers jours, du plus récent au plus ancien : (sub, date, texte). */
    private fun smsOfficiels(c: Context): List<Triple<Int, Long, String>> {
        val depuis = System.currentTimeMillis() - 30L * 24 * 3600_000
        val l = mutableListOf<Triple<Int, Long, String>>()
        try {
            c.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY, Telephony.Sms.SUBSCRIPTION_ID),
                "${Telephony.Sms.DATE} > ?", arrayOf(depuis.toString()), "${Telephony.Sms.DATE} DESC LIMIT 400",
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val adresse = cur.getString(0).orEmpty()
                    if (Bouclier.officiel(adresse)) l += Triple(cur.getInt(3), cur.getLong(1), cur.getString(2).orEmpty())
                }
            }
        } catch (_: Exception) {
        }
        return l
    }

    private fun ussd(c: Context): List<Triple<Int, Long, String>> = try {
        val o = JSONObject(Settings.Global.getString(c.contentResolver, CLE_USSD) ?: "{}")
        o.keys().asSequence().mapNotNull { k ->
            val e = o.getJSONObject(k)
            k.toIntOrNull()?.let { Triple(it, e.getLong("quand"), e.getString("texte")) }
        }.toList()
    } catch (_: Exception) {
        emptyList()
    }

    /** Noter la réponse de l'opérateur à un code de solde. */
    fun noterUssd(c: Context, sub: Int, texte: String) {
        try {
            val o = JSONObject(Settings.Global.getString(c.contentResolver, CLE_USSD) ?: "{}")
            o.put(sub.toString(), JSONObject().put("quand", System.currentTimeMillis()).put("texte", texte))
            Settings.Global.putString(c.contentResolver, CLE_USSD, o.toString())
        } catch (_: Exception) {
        }
    }

    /**
     * Ce qu'on sait de chaque SIM : pour chaque valeur, la plus récente lue (SMS ou USSD).
     * Un forfait arrivé à sa date de fin n'a plus de data.
     */
    fun releves(c: Context): Map<Int, Releve> {
        val textes = (smsOfficiels(c) + ussd(c)).sortedByDescending { it.second }
        val m = mutableMapOf<Int, Releve>()
        val maintenant = System.currentTimeMillis()
        textes.groupBy { it.first }.forEach { (sub, l) ->
            var credit: Pair<Long, Long>? = null
            var data: Triple<Double?, Double?, Long>? = null
            var fin: Long? = null
            var forfait: String? = null
            for ((_, quand, texte) in l) {
                val x = analyser(texte, quand)
                if (x.vide) continue
                if (credit == null && x.credit != null) credit = x.credit to quand
                if (data == null && x.dataReste != null) data = Triple(x.dataReste, x.dataTotal, quand)
                // La date de fin du forfait : celle du message de data le plus récent qui en donne une.
                if (fin == null && x.expire != null && (x.dataReste != null || x.dataTotal != null)) fin = x.expire
                if (forfait == null && x.forfait != null) forfait = x.forfait
                if (credit != null && data != null && fin != null) break
            }
            // Le volume total vient souvent du SMS d'achat, plus ancien que le dernier « il vous reste ».
            val total = data?.second ?: l.firstNotNullOfOrNull { analyser(it.third, it.second).dataTotal }
            val expire = fin?.takeIf { it > maintenant - 24 * 3600_000L }
            val reste = data?.first?.let { if (expire != null && expire < maintenant) 0.0 else it }
            if (credit != null || data != null) {
                m[sub] = Releve(sub, credit?.first, credit?.second ?: 0L, reste, total, data?.third ?: 0L, expire, forfait)
            }
        }
        return m
    }

    /**
     * Le vrai solde mobile money de chaque service, d'après son dernier SMS de transaction (« Nouveau solde »).
     * Lu seulement à la demande, après le code : il n'est gardé nulle part.
     */
    fun mobileMoney(c: Context): List<Pair<String, String>> {
        val vus = mutableMapOf<String, String>()
        smsOfficiels(c).forEach { (_, _, texte) ->
            val op = Bouclier.operateurCite(texte) ?: return@forEach
            if (op.nom !in vus) Bouclier.solde(texte)?.let { vus[op.nom] = it }
        }
        return vus.toList()
    }

    /** Les alertes réglées dans Réglages › Soldes et forfaits (« data », « fin », « credit ») ; toutes actives au départ. */
    fun alerte(c: Context, cle: String): Boolean = try {
        JSONObject(Settings.Global.getString(c.contentResolver, "sama_soldes_alertes") ?: "{}").optBoolean(cle, true)
    } catch (_: Exception) {
        true
    }

    // ——— Pour l'affichage ———

    fun texteVolume(mo: Double): String = when {
        mo >= 1024 -> (mo / 1024).let { if (it % 1.0 < 0.05) "${it.toInt()} Go" else String.format(Locale.FRENCH, "%.1f Go", it) }
        mo >= 1 -> "${mo.toInt()} Mo"
        else -> "0 Mo"
    }

    fun texteMontant(f: Long): String = String.format(Locale.FRENCH, "%,d F", f).replace(' ', ' ').replace(' ', ' ')

    /** « expire ce soir à minuit », « expire demain à minuit », « jusqu'au 9 oct. ». */
    fun texteFin(t: Long): String {
        val d = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
        val auj = LocalDate.now()
        val heure = if (d.hour == 23 && d.minute == 59) "à minuit" else "à " + d.format(DateTimeFormatter.ofPattern("H:mm"))
        return when (d.toLocalDate()) {
            auj -> "expire ce soir $heure"
            auj.plusDays(1) -> "expire demain $heure"
            else -> "jusqu'au " + d.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
        }
    }

    /** « lu à 14:29 », « lu hier ». */
    fun texteLu(t: Long): String {
        val d = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
        val minutes = (System.currentTimeMillis() - t) / 60_000
        return when {
            minutes < 1 -> "lu à l'instant"
            minutes < 60 -> "lu il y a $minutes min"
            d.toLocalDate() == LocalDate.now() -> "lu à " + d.format(DateTimeFormatter.ofPattern("H:mm"))
            d.toLocalDate() == LocalDate.now().minusDays(1) -> "lu hier"
            else -> "lu le " + d.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
        }
    }
}
