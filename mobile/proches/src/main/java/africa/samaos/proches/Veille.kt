package africa.samaos.proches

import android.content.Context
import android.os.Bundle

/**
 * « Protéger aussi un proche » (innovation 2) : quand le bouclier arrête une arnaque grave chez une personne qui
 * l'a voulu, un proche qu'elle a choisi est prévenu par SMS. Seul le type d'arnaque part, jamais le contenu des
 * messages, les numéros ni le solde. Le SMS est signé avec le secret que les deux téléphones partagent depuis
 * qu'ils se sont reconnus en personne : un arnaqueur ne peut pas fabriquer une fausse alerte.
 *
 * Le SMS reste lisible sur n'importe quel téléphone ; il finit par « #SA1.<heure>.<code>.<preuve> ».
 * Il n'utilise que l'alphabet des SMS (GSM 7 bits) : un seul SMS, pas trois.
 */
object Veille {
    /** [texte] suit le prénom : « Koffi vient d'éviter un faux SMS… ». */
    enum class Alerte(val code: String, val texte: String) {
        SMS("S", "vient d'éviter un faux SMS de mobile money"),
        MAIL("M", "vient d'éviter un faux mail d'opérateur"),
        APPEL("A", "vient d'avoir un appel suspect"),
        PENDANT("P", "a été retenu avant un transfert, pendant l'appel d'un inconnu"),
        SITE("L", "vient d'éviter un faux site de mobile money"),
        INSTALLATION("I", "a demandé à installer une appli hors de Sugu (24 h d'attente)"),
        FAUX_FICHIER("F", "vient d'éviter une fausse mise à jour"),
        ESSAI("E", "fait un essai : tout marche"),
        ;

        companion object {
            fun de(code: String) = entries.firstOrNull { it.code == code }
        }
    }

    /** Les arnaques graves, d'après le journal du bouclier (type, titre). Une simple prudence ne dérange personne. */
    fun alerte(type: String, titre: String): Alerte? = when (type) {
        "sms" -> if (titre == "Arnaque probable" || titre == "Lien piégé") Alerte.SMS else null
        "mail" -> if (titre == "Faux mail" || titre == "Lien piégé") Alerte.MAIL else null
        "appel" -> Alerte.APPEL
        "pendant" -> Alerte.PENDANT
        "lien" -> Alerte.SITE
        "installation" -> Alerte.INSTALLATION
        "faux-fichier" -> Alerte.FAUX_FICHIER
        else -> null
    }

    /** Ce qui est signé : l'heure (en secondes, base 36) et le code. */
    fun message(t: String, code: String) = "alerte|$t|$code"

    fun sms(nom: String, a: Alerte, quand: Long, preuve: String): String {
        val t = (quand / 1000).toString(36)
        val heure = java.time.Instant.ofEpochMilli(quand).atZone(java.time.ZoneId.systemDefault()).let { "%d:%02d".format(it.hour, it.minute) }
        val qui = sansAccents(nom).take(20)
        val texte = if (a == Alerte.ESSAI) "Sama : essai de $qui. Vous serez prévenu si le bouclier bloque une arnaque chez lui ou chez elle."
        else "Sama : $qui ${a.texte} ($heure). Prenez de ses nouvelles."
        return "$texte #SA1.$t.${a.code}.$preuve"
    }

    /** Une alerte reçue, avant vérification. */
    class Recue(val t: String, val alerte: Alerte, val preuve: String) {
        val quand get() = (t.toLongOrNull(36) ?: 0L) * 1000
    }

    fun lire(corps: String): Recue? {
        val m = Regex("#SA1\\.([0-9a-z]{1,10})\\.([A-Z])\\.([A-Za-z0-9_-]{16,32})\\s*$").find(corps.trim()) ?: return null
        val a = Alerte.de(m.groupValues[2]) ?: return null
        return Recue(m.groupValues[1], a, m.groupValues[3])
    }

    /** Les lettres hors de l'alphabet des SMS (ê, ç…) forceraient un SMS plus long et plus cher. */
    private fun sansAccents(s: String): String {
        val gsm = "éèùìòÇÉàäöüñÄÖÜÑ"
        return s.map { ch ->
            if (ch.code < 128 || ch in gsm) ch.toString()
            else java.text.Normalizer.normalize(ch.toString(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").ifEmpty { "?" }
        }.joinToString("")
    }

    // ——— La signature, par les Réglages (qui ont les secrets) ———

    fun signer(c: Context, proche: String, message: String): String? = try {
        c.contentResolver.call(android.net.Uri.parse("content://${Cercle.AUTORITE}"), "signer", proche, Bundle().apply { putString("message", message) })?.getString("preuve")
    } catch (_: Exception) {
        null
    }

    /** Le proche qui a signé cette alerte, s'il est des nôtres ; null pour une fausse alerte. */
    fun auteur(c: Context, r: Recue): String? = try {
        c.contentResolver.call(
            android.net.Uri.parse("content://${Cercle.AUTORITE}"), "verifier", null,
            Bundle().apply {
                putString("message", message(r.t, r.alerte.code))
                putString("preuve", r.preuve)
            },
        )?.getString("id")
    } catch (_: Exception) {
        null
    }
}
