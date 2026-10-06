package africa.samaos.bouclier

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/** Un service de mobile money : son nom, les mots qui le citent, ses expéditeurs et ses sites officiels, ses codes. */
class Operateur(val nom: String, val mots: List<String>, val expediteurs: Set<String>, val domaines: Set<String>, val codes: List<String>)

/** Ce que le bouclier a vu : un mot pour le titre, une phrase pour comprendre. */
class Verdict(val titre: String, val raison: String, val operateur: Operateur? = null)

class Evenement(val quand: Long, val type: String, val titre: String, val detail: String)

/**
 * Le bouclier anti-arnaques (innovation 2, maquettes i2-*). Les règles tournent sur le téléphone :
 * le SMS, l'appel ou la page ne quittent jamais l'appareil. Réglages et journal sont partagés entre
 * les applis de Sama par les réglages globaux du système (comme les Espaces).
 */
object Bouclier {
    val OPERATEURS = listOf(
        Operateur("Orange Money", listOf("orange money", "orangemoney"), setOf("orangemoney", "orange", "om"), setOf("orange.ci", "orangemoney.orange.ci"), listOf("#144", "*144")),
        Operateur("MTN MoMo", listOf("mtn momo", "momo", "mobile money mtn"), setOf("mtnmomo", "momo", "mtn"), setOf("mtn.ci"), listOf("*133")),
        Operateur("Moov Money", listOf("moov money", "flooz"), setOf("moovmoney", "moov", "flooz"), setOf("moov-africa.ci"), listOf("*155")),
        Operateur("Wave", listOf("wave"), setOf("wave"), setOf("wave.com"), emptyList()),
    )

    enum class Garde(val cle: String, val nom: String, val detail: String) {
        SMS("sms", "SMS qui imitent un opérateur", "Comparés à l'expéditeur officiel et à votre vrai solde"),
        APPELS("appels", "Appels inconnus répétés", "Surtout la nuit"),
        PENDANT_APPEL("pendant", "Mobile money pendant un appel", "Mise en garde et transfert différé"),
        LIENS("liens", "Pages qui demandent un code secret", "Dans Griot et dans les liens des SMS"),
    }

    private const val CLE = "sama_bouclier"
    private const val CLE_JOURNAL = "sama_bouclier_journal"

    private fun lire(c: Context): JSONObject = try {
        JSONObject(Settings.Global.getString(c.contentResolver, CLE) ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun ecrire(c: Context, cle: String, oui: Boolean) {
        try {
            Settings.Global.putString(c.contentResolver, CLE, lire(c).put(cle, oui).toString())
        } catch (_: Exception) {
        }
    }

    fun actif(c: Context, g: Garde): Boolean = lire(c).optBoolean(g.cle, true)

    fun regler(c: Context, g: Garde, oui: Boolean) = ecrire(c, g.cle, oui)

    /** Signaler anonymement (le numéro et le type d'arnaque) à la liste commune de Sama. Jamais sans accord : désactivé au départ. */
    fun signaler(c: Context): Boolean = lire(c).optBoolean("signaler", false)

    fun reglerSignaler(c: Context, oui: Boolean) = ecrire(c, "signaler", oui)

    /** Un expéditeur officiel : un nom (ORANGE, MTN…) ou un numéro court d'opérateur, pas un numéro de téléphone. */
    fun officiel(adresse: String) = adresse.any { it.isLetter() } || adresse.filter { it.isDigit() }.length in 1..6

    private fun normaliser(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    fun operateurCite(texte: String): Operateur? {
        val t = normaliser(texte)
        return OPERATEURS.firstOrNull { o -> o.mots.any { t.contains(it) } }
    }

    /** Un SMS reçu : arnaque probable, ou null. */
    fun sms(adresse: String, corps: String, contactConnu: Boolean): Verdict? {
        if (officiel(adresse)) return null
        val t = normaliser(corps)
        val op = operateurCite(corps)
        val transaction = Regex("(recu|transfert|envoye|depot|credit)").containsMatchIn(t) && Regex("(solde|ref|fcfa|\\d\\s?f\\b)").containsMatchIn(t)
        // Un opérateur n'écrit jamais depuis le numéro d'une personne, même connue (son téléphone a pu être volé).
        if (op != null && transaction) {
            return Verdict("Arnaque probable", "Ce message imite ${op.nom} mais vient d'un numéro ordinaire" + if (contactConnu) ", même s'il est dans vos contacts." else ".", op)
        }
        if (contactConnu) return null
        return when {
            Regex("(tromp|erreur).{0,60}(renvo|retourn|rembours)|(renvo|retourn|rembours).{0,60}(tromp|erreur)").containsMatchIn(t) ->
                Verdict("Arnaque probable", "« Je me suis trompé, renvoyez-moi l'argent » : c'est l'arnaque la plus courante. Regardez votre vrai solde avant tout.")
            Regex("(code secret|code pin|votre code|ton code|code de confirmation)").containsMatchIn(t) && Regex("(envoi|donne|communique|transmet|dis)").containsMatchIn(t) ->
                Verdict("Prudence", "On vous demande un code. Personne de sérieux, ni votre opérateur ni votre banque, ne le demande.")
            else -> null
        }
    }

    /**
     * Un lien dans un SMS qui se fait passer pour un opérateur (orange-bonus.ci, momo-remboursement.com…).
     * Vérifié quel que soit l'expéditeur : un nom d'expéditeur comme « ORANGE » s'imite aussi.
     */
    fun lien(corps: String): Verdict? {
        val h = Regex("(?i)\\b(?:https?://)?((?:[a-z0-9-]+\\.)+[a-z]{2,})(?:[/?#]\\S*)?").findAll(corps)
            .map { it.groupValues[1].lowercase() }.firstOrNull { hoteSuspect(it) } ?: return null
        val op = operateurDuHote(h.removePrefix("www."))
        return Verdict(
            "Lien piégé",
            "Ce lien se fait passer pour ${op?.nom ?: "un service de mobile money"} mais ne mène pas à un site officiel. N'y tapez jamais votre code secret.",
            op,
        )
    }

    /** Le dernier solde annoncé par l'expéditeur officiel d'un opérateur (« Nouveau solde : 350 F »). */
    fun solde(corps: String): String? = Regex("solde[^0-9]{0,20}([0-9][0-9 .\\u202f\\u00a0]*)\\s*(f\\b|fcfa)", RegexOption.IGNORE_CASE)
        .find(corps)?.groupValues?.get(1)?.trim()?.let { "$it F" }

    /** Un appel entrant : suspect s'il vient d'un inconnu qui insiste, ou qui rappelle la nuit. [appelsRecents] compte celui-ci. */
    fun appel(contactConnu: Boolean, appelsRecents: Int, maintenant: LocalTime = LocalTime.now()): Verdict? {
        if (contactConnu) return null
        val nuit = maintenant.hour < 6 || maintenant.hour >= 22
        return when {
            appelsRecents >= 3 -> Verdict("Appel suspect", "A appelé $appelsRecents fois depuis hier. Aucun service ne vous demandera votre code mobile money au téléphone.")
            nuit && appelsRecents >= 2 -> Verdict("Appel suspect", "Rappelle en pleine nuit. Aucun service ne vous demandera votre code mobile money au téléphone.")
            else -> null
        }
    }

    /** Un code tapé au clavier qui ouvre le mobile money (#144#, *133#…). */
    fun codeMobileMoney(code: String): Operateur? {
        val c = code.replace(" ", "")
        return OPERATEURS.firstOrNull { o -> o.codes.any { c.startsWith(it) } }
    }

    private fun operateurDuHote(h: String) = OPERATEURS.firstOrNull { o -> o.mots.any { h.contains(it.replace(" ", "")) } || h.contains(o.nom.lowercase().replace(" ", "")) }

    private fun hoteOfficiel(h: String) = OPERATEURS.any { o -> o.domaines.any { d -> h == d || h.endsWith(".$d") } }

    /** Une adresse qui se réclame d'un service de mobile money sans en être un site officiel : Griot la cache le temps de la lire. */
    fun hoteSuspect(hote: String): Boolean {
        val h = hote.lowercase().removePrefix("www.")
        return (operateurDuHote(h) != null || h.contains("mobilemoney") || h.contains("mobile-money")) && !hoteOfficiel(h)
    }

    /**
     * Une page web : elle imite un service de mobile money et demande un code secret, hors des sites officiels.
     * [texte] : le titre et le texte autour du champ ; [champSecret] : la page a un champ de mot de passe ou de code.
     * Une page de connexion ordinaire ne suffit pas : il faut l'imitation ET la demande du code.
     */
    fun page(hote: String, texte: String, champSecret: Boolean): Verdict? {
        if (!champSecret) return null
        val h = hote.lowercase().removePrefix("www.")
        if (hoteOfficiel(h)) return null
        val t = normaliser(texte)
        val op = operateurCite(texte) ?: operateurDuHote(h)
        val imite = op != null || h.contains("mobilemoney") || h.contains("mobile-money") || t.contains("mobile money")
        val demandeCode = Regex("(code secret|code pin|\\bpin\\b|code de retrait|code confidentiel|code de validation)").containsMatchIn(t)
        if (!imite || !demandeCode) return null
        return Verdict("Page dangereuse", "Cette page imite ${op?.nom ?: "un service de mobile money"} et demande votre code secret. Ce n'est pas un site officiel.", op)
    }

    // ——— Le délai avant d'autoriser une appli hors de Sugu (maquette i2-delai-installation) ———

    private const val CLE_DELAIS = "sama_bouclier_delais"
    const val DELAI_INSTALLATION = 24 * 3600_000L
    /** Une autorisation prête mais pas utilisée retombe au bout d'une semaine. */
    private const val PEREMPTION = 7 * 24 * 3600_000L

    private fun delais(c: Context): JSONObject = try {
        JSONObject(Settings.Global.getString(c.contentResolver, CLE_DELAIS) ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun ecrireDelais(c: Context, o: JSONObject) {
        try {
            Settings.Global.putString(c.contentResolver, CLE_DELAIS, o.toString())
        } catch (_: Exception) {
        }
    }

    /** L'heure où [paquet] pourra être autorisé à installer des applis ; null sans demande en cours. */
    fun delaiInstallation(c: Context, paquet: String): Long? {
        val t = delais(c).optLong(paquet, 0L)
        if (t == 0L) return null
        if (System.currentTimeMillis() > t + PEREMPTION) {
            annulerInstallation(c, paquet)
            return null
        }
        return t
    }

    /** Demander l'autorisation : elle sera possible dans 24 heures. Redemander ne raccourcit ni ne rallonge rien. */
    fun demanderInstallation(c: Context, paquet: String): Long =
        delaiInstallation(c, paquet) ?: (System.currentTimeMillis() + DELAI_INSTALLATION).also { ecrireDelais(c, delais(c).put(paquet, it)) }

    fun annulerInstallation(c: Context, paquet: String) = ecrireDelais(c, delais(c).apply { remove(paquet) })

    /**
     * Le délai est-il écoulé ? Il faut aussi l'heure réglée par le réseau : sinon, avancer l'horloge du
     * téléphone suffirait à le sauter (ce qu'un arnaqueur au téléphone ferait faire).
     */
    fun installationPossible(c: Context, paquet: String): Boolean {
        val t = delaiInstallation(c, paquet) ?: return false
        return System.currentTimeMillis() >= t && heureDuReseau(c)
    }

    fun heureDuReseau(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.AUTO_TIME, 1) == 1

    // ——— Le journal : ce qui a été évité, pour le bilan ———

    fun noter(c: Context, type: String, titre: String, detail: String) {
        try {
            val a = JSONArray(Settings.Global.getString(c.contentResolver, CLE_JOURNAL) ?: "[]")
            val l = (0 until a.length()).map { a.getJSONObject(it) }.toMutableList()
            // Le même événement deux fois de suite ne compte qu'une fois.
            if (l.lastOrNull()?.let { it.optString("detail") == detail && it.optString("type") == type && it.optString("titre") == titre } == true) return
            l += JSONObject().put("quand", System.currentTimeMillis()).put("type", type).put("titre", titre).put("detail", detail)
            Settings.Global.putString(c.contentResolver, CLE_JOURNAL, JSONArray(l.takeLast(60)).toString())
        } catch (_: Exception) {
        }
    }

    /** Ce que le bouclier a vu ces [jours] derniers jours, le plus récent d'abord. */
    fun recents(c: Context, jours: Int = 7): List<Evenement> {
        val depuis = System.currentTimeMillis() - jours * 24 * 3600_000L
        return journal(c).filter { it.quand >= depuis }
    }

    fun journal(c: Context): List<Evenement> = try {
        val a = JSONArray(Settings.Global.getString(c.contentResolver, CLE_JOURNAL) ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.map { Evenement(it.getLong("quand"), it.optString("type"), it.optString("titre"), it.optString("detail")) }.reversed()
    } catch (_: Exception) {
        emptyList()
    }
}
