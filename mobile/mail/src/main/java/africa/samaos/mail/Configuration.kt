package africa.samaos.mail

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

/**
 * Trouver les serveurs d'une adresse sans rien demander : la base publique de Thunderbird (ISPDB), puis la
 * configuration que le domaine publie lui-même. Sinon, la personne les saisit (ils sont dans l'aide de son
 * fournisseur). Rien n'est envoyé d'autre que le nom de domaine.
 */
object Configuration {
    class Trouvee(val imap: Serveur, val smtp: Serveur, val identifiant: String, val source: String)

    /** Ce qui empêche une boîte de marcher avec un simple mot de passe, et que la personne doit savoir. */
    class Obstacle(val message: String)

    /** Les conseils propres à quelques grands fournisseurs (le mot de passe à utiliser). */
    fun conseil(domaine: String): String? = when (domaine) {
        "gmail.com", "googlemail.com" ->
            "Gmail demande un « mot de passe d'application » : Compte Google › Sécurité › Validation en deux étapes › Mots de passe des applications."
        "yahoo.com", "yahoo.fr", "ymail.com" ->
            "Yahoo demande un « mot de passe d'application » : Sécurité du compte › Générer un mot de passe d'application."
        else -> null
    }

    fun trouver(adresse: String): Any? {
        val domaine = adresse.substringAfterLast('@').lowercase().trim()
        if (domaine.isEmpty() || !domaine.contains('.')) return null
        val sources = listOf(
            "https://autoconfig.thunderbird.net/v1.1/$domaine" to "Thunderbird",
            "https://autoconfig.$domaine/mail/config-v1.1.xml" to domaine,
            "https://$domaine/.well-known/autoconfig/mail/config-v1.1.xml" to domaine,
        )
        for ((url, source) in sources) {
            val xml = lire(url) ?: continue
            return analyser(xml, adresse, source) ?: continue
        }
        return null
    }

    private fun lire(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.instanceFollowRedirects = true
        c.inputStream.use { e -> if (c.responseCode == 200) e.readBytes().decodeToString().take(200_000) else null }
    } catch (_: Exception) {
        null
    }

    private class Lu(val type: String, var hote: String = "", var port: Int = 0, var socket: String = "", var identifiant: String = "", val auth: MutableList<String> = mutableListOf())

    /** Le format « clientConfig » de Thunderbird : le premier serveur IMAP et le premier SMTP qui acceptent un mot de passe. */
    private fun analyser(xml: String, adresse: String, source: String): Any? {
        val serveurs = mutableListOf<Lu>()
        try {
            val p = Xml.newPullParser()
            p.setInput(xml.reader())
            var courant: Lu? = null
            var balise = ""
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> {
                        balise = p.name
                        if (p.name == "incomingServer" || p.name == "outgoingServer") courant = Lu(p.getAttributeValue(null, "type").orEmpty())
                    }
                    XmlPullParser.TEXT -> courant?.let { s ->
                        val t = p.text.trim()
                        when (balise) {
                            "hostname" -> s.hote = t
                            "port" -> s.port = t.toIntOrNull() ?: 0
                            "socketType" -> s.socket = t
                            "username" -> s.identifiant = t
                            "authentication" -> if (t.isNotEmpty()) s.auth += t
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if ((p.name == "incomingServer" || p.name == "outgoingServer") && courant != null) {
                            serveurs += courant
                            courant = null
                        }
                        balise = ""
                    }
                }
                p.next()
            }
        } catch (_: Exception) {
            return null
        }
        fun motDePasse(s: Lu) = s.auth.isEmpty() || s.auth.any { it.startsWith("password") }
        val imaps = serveurs.filter { it.type == "imap" && it.hote.isNotEmpty() }
        val smtps = serveurs.filter { it.type == "smtp" && it.hote.isNotEmpty() }
        if (imaps.isEmpty() || smtps.isEmpty()) return null
        val imap = imaps.firstOrNull { motDePasse(it) && it.socket != "plain" }
        val smtp = smtps.firstOrNull { motDePasse(it) && it.socket != "plain" }
        if (imap == null || smtp == null) {
            return if (imaps.any { it.auth.contains("OAuth2") } || smtps.any { it.auth.contains("OAuth2") }) {
                Obstacle("Ce fournisseur n'accepte plus les mots de passe : il faut se connecter par son site (OAuth). Mail ne le sait pas encore.")
            } else {
                null
            }
        }
        fun serveur(s: Lu): Serveur {
            val sec = if (s.socket == "STARTTLS") Securite.STARTTLS else Securite.SSL
            val defaut = when (s.type) { "imap" -> if (sec == Securite.SSL) 993 else 143; else -> if (sec == Securite.SSL) 465 else 587 }
            return Serveur(s.hote, s.port.takeIf { it in 1..65535 } ?: defaut, sec)
        }
        val identifiant = when (imap.identifiant) {
            "%EMAILLOCALPART%" -> adresse.substringBefore('@')
            else -> adresse
        }
        return Trouvee(serveur(imap), serveur(smtp), identifiant, source)
    }
}
