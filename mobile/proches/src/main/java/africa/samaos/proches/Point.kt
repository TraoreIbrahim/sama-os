package africa.samaos.proches

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Un point Sama (ordinateur Sama d'une école, d'une mairie, d'un cybercafé) ou le téléphone d'un proche.
 * [proche] : l'identifiant du proche reconnu dont c'est le téléphone ; [acces] : à chaque requête, de quoi
 * prouver qu'on est l'un de ses proches (un téléphone ne répond qu'à eux) et chiffrer l'échange.
 */
class Point(val nom: String, val hote: String, val port: Int, val proche: String? = null, val acces: (() -> Cercle.Acces?)? = null) {
    val adresse get() = "$hote:$port"
}

/**
 * Le protocole de Proche en proche : du HTTP tout simple sur le réseau local (Wi-Fi, point d'accès, Wi-Fi Direct).
 * Ce sont la signature des fiches et l'empreinte des fichiers qui garantissent qu'on reçoit ce que l'éditeur a
 * publié. Chez un point Sama (version 1), rien n'est caché :
 *   GET /proches/v1/catalogue        → {"point": {"nom": …}, "paquets": [fiches signées]}
 *   GET /proches/v1/fichier/<sha256> → le fichier ; « Range: bytes=N- » pour reprendre où l'on s'était arrêté
 * Entre téléphones de proches (version 2, PROTOCOLE.md) : l'annonce reste publique ; tout le reste (les mêmes
 * requêtes, plus la proposition et la réponse) part chiffré dans « POST /proches/v2/chiffre », avec l'en-tête
 * « Sama-Proche ».
 */
object Protocole {
    const val SERVICE = "_samapoint._tcp."
    const val PORT = 8765
    /** Les téléphones ouverts à leurs proches. */
    const val SERVICE_PROCHE = "_samaproche._tcp."
    /** La version du protocole entre proches (annonce, mDNS) : 2, l'échange chiffré. */
    const val VERSION_PROCHES = 2
    /** Le seul chemin, entre proches, en dehors de l'annonce : la vraie requête est dans l'enveloppe. */
    const val CHIFFRE = "/proches/v2/chiffre"
    private const val DELAI = 15_000

    private class Reponse(val code: Int, val entetes: Map<String, String>, val corps: InputStream, val socket: Socket)

    private fun demander(p: Point, chemin: String, debut: Long = 0, corps: ByteArray? = null): Reponse {
        val methode = if (corps != null) "POST" else "GET"
        // L'accès se calcule avant d'ouvrir la connexion : chez un proche, pas de preuve, pas de requête.
        val a = p.acces?.let { it() ?: error("Pas d'accès pour ce proche") }
        if (a != null) return demanderChiffre(p, a, methode, chemin, debut, corps)
        val s = connecter(p)
        val requete = buildString {
            append("$methode $chemin HTTP/1.1\r\nHost: ${p.hote}\r\nConnection: close\r\nUser-Agent: SamaProches/1\r\n")
            if (debut > 0) append("Range: bytes=$debut-\r\n")
            if (corps != null) append("Content-Type: application/json\r\nContent-Length: ${corps.size}\r\n")
            append("\r\n")
        }
        s.getOutputStream().apply {
            write(requete.toByteArray(Charsets.US_ASCII))
            if (corps != null) write(corps)
            flush()
        }
        val e = BufferedInputStream(s.getInputStream())
        val (code, entetes) = lireEntete(e, http = true)
        return Reponse(code, entetes, e, s)
    }

    /**
     * Chez un proche : la vraie requête (méthode, chemin, reprise, contenu) part chiffrée dans
     * « POST /proches/v2/chiffre », et la vraie réponse (code, en-têtes, contenu) revient chiffrée. En clair ne
     * passent que la preuve et la taille, à 16 Kio près.
     */
    private fun demanderChiffre(p: Point, a: Cercle.Acces, methode: String, chemin: String, debut: Long, corps: ByteArray?): Reponse {
        val enveloppe = java.io.ByteArrayOutputStream()
        Enveloppe.Ecrivain(a.demande, enveloppe).use { w ->
            w.write(buildString {
                append("$methode $chemin\n")
                if (debut > 0) append("Range: bytes=$debut-\n")
                append("\n")
            }.toByteArray(Charsets.UTF_8))
            if (corps != null) w.write(corps)
        }
        val s = connecter(p)
        s.getOutputStream().apply {
            write(("POST $CHIFFRE HTTP/1.1\r\nHost: ${p.hote}\r\nConnection: close\r\nUser-Agent: SamaProches/2\r\n" +
                "Sama-Proche: ${a.entete}\r\nContent-Type: application/octet-stream\r\nContent-Length: ${enveloppe.size()}\r\n\r\n").toByteArray(Charsets.US_ASCII))
            enveloppe.writeTo(this)
            flush()
        }
        val e = BufferedInputStream(s.getInputStream())
        val (code, entetes) = lireEntete(e, http = true)
        if (code != 200) return Reponse(code, entetes, e, s)
        val dedans = Enveloppe.Lecteur(a.reponse, e)
        val (vraiCode, vraisEntetes) = lireEntete(dedans, http = false)
        return Reponse(vraiCode, vraisEntetes, dedans, s)
    }

    private fun connecter(p: Point) = Socket().apply {
        connect(InetSocketAddress(p.hote, p.port), DELAI)
        soTimeout = DELAI
    }

    /** La ligne d'état (« HTTP/1.1 200 OK », ou « 200 » dans l'enveloppe) et les en-têtes, jusqu'à la ligne vide. */
    private fun lireEntete(e: InputStream, http: Boolean): Pair<Int, Map<String, String>> {
        val statut = Enveloppe.ligne(e) ?: error("Réponse vide")
        val code = (if (http) statut.split(' ').getOrNull(1) else statut.trim()).let { it?.toIntOrNull() } ?: error("Réponse illisible")
        val entetes = mutableMapOf<String, String>()
        while (true) {
            val l = Enveloppe.ligne(e) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) entetes[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            if (entetes.size > 50) error("Trop d'en-têtes")
        }
        return code to entetes
    }

    /**
     * Le nom du point et ce qu'il propose. Les fiches mal signées ou d'un éditeur inconnu sont écartées et comptées ;
     * une vitrine n'est gardée que si elle est signée par le même éditeur et parle de la même version.
     */
    class Catalogue(val nom: String?, val offres: List<Offre>, val ecartes: Int) {
        val paquets get() = offres.map { it.fiche }
    }

    fun catalogue(c: Context, p: Point): Catalogue {
        val r = demander(p, "/proches/v1/catalogue")
        val texte = r.socket.use { if (r.code == 200) r.corps.readBytes().decodeToString() else error("Le point a répondu ${r.code}") }
        val o = JSONObject(texte)
        val nom = o.optJSONObject("point")?.optString("nom")?.ifBlank { null }
        // Le point garde ce qui le rattache à un proche (son accès) : les fichiers se demandent avec.
        val ici = if (nom == null || nom == p.nom) p else Point(nom, p.hote, p.port, p.proche, p.acces)
        val bruts = o.optJSONArray("paquets")
        var ecartes = 0
        val offres = (0 until (bruts?.length() ?: 0)).mapNotNull { i ->
            val j = bruts!!.getJSONObject(i)
            val m = Manifeste.depuis(j)?.takeIf { Verification.signature(c, it) }
            if (m == null) {
                ecartes++
                return@mapNotNull null
            }
            val v = j.optJSONObject("vitrine")?.let { Vitrine.depuis(it) }
                ?.takeIf { it.paquet == m.paquet && it.version == m.version && it.editeur == m.editeur && Vitrine.verifier(c, it) }
            Offre(m, v, ici)
        }
        return Catalogue(nom, offres, ecartes)
    }

    /** L'annonce d'un téléphone ouvert à ses proches : un nombre au hasard et une étiquette par proche. */
    class Annonce(val nombre: String, val etiquettes: List<String>)

    fun annonce(p: Point): Annonce? = try {
        val r = demander(p, "/proches/v1/annonce")
        r.socket.use {
            if (r.code != 200) return null
            val o = JSONObject(lireLimite(r.corps, 64 * 1024).decodeToString())
            val e = o.optJSONArray("e")
            Annonce(o.getString("n"), (0 until (e?.length() ?: 0)).map { e!!.getString(it) }).takeIf { o.optInt("v") == VERSION_PROCHES }
        }
    } catch (_: Exception) {
        null
    }

    /** Envoyer une demande (proposition, réponse) ; renvoie le code de la réponse, ou -1 si personne n'a répondu. */
    fun poster(p: Point, chemin: String, o: JSONObject): Int = try {
        val r = demander(p, chemin, corps = o.toString().toByteArray(Charsets.UTF_8))
        r.socket.use { r.code }
    } catch (_: Exception) {
        -1
    }

    private fun lireLimite(e: InputStream, maximum: Int): ByteArray {
        val b = java.io.ByteArrayOutputStream()
        val t = ByteArray(8 * 1024)
        while (true) {
            val n = e.read(t)
            if (n < 0) break
            b.write(t, 0, n)
            if (b.size() > maximum) error("Réponse trop longue")
        }
        return b.toByteArray()
    }

    /** Une petite image (icône) servie par son empreinte : refusée si elle ne correspond pas, ou si elle est trop grosse. */
    fun image(p: Point, sha256: String, maximum: Int = 256 * 1024): ByteArray? = try {
        val r = demander(p, "/proches/v1/fichier/$sha256")
        r.socket.use {
            if (r.code != 200) return null
            val b = java.io.ByteArrayOutputStream()
            val t = ByteArray(16 * 1024)
            while (true) {
                val n = r.corps.read(t)
                if (n < 0) break
                b.write(t, 0, n)
                if (b.size() > maximum) return null
            }
            b.toByteArray().takeIf { Verification.sha256(it) == sha256 }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Recevoir le fichier d'une fiche dans [partiel], en reprenant après ce qui est déjà là. [avance] reçoit le
     * nombre d'octets présents ; [arret] permet d'interrompre (la suite reprendra plus tard).
     */
    fun recevoir(p: Point, m: Manifeste, partiel: File, avance: (Long) -> Unit, arret: () -> Boolean) {
        var deja = if (partiel.exists()) partiel.length() else 0L
        if (deja > m.taille) {
            partiel.delete()
            deja = 0
        }
        if (deja == m.taille) return avance(deja)
        val r = demander(p, "/proches/v1/fichier/${m.sha256}", deja)
        r.socket.use {
            when (r.code) {
                206 -> {}
                200 -> if (deja > 0) {
                    // Le point ne sait pas reprendre : on repart de zéro.
                    partiel.delete()
                    deja = 0
                }
                else -> error("Le point a répondu ${r.code}")
            }
            FileOutputStream(partiel, deja > 0).use { s ->
                val tampon = ByteArray(64 * 1024)
                var total = deja
                var derniere = 0L
                while (total < m.taille) {
                    if (arret()) return
                    val n = r.corps.read(tampon, 0, minOf(tampon.size.toLong(), m.taille - total).toInt())
                    if (n < 0) break
                    s.write(tampon, 0, n)
                    total += n
                    if (total - derniere > 256 * 1024 || total == m.taille) {
                        derniere = total
                        avance(total)
                    }
                }
                // La connexion s'est coupée avant la fin : ce qui est reçu reste, la suite viendra plus tard.
                if (total < m.taille) throw java.io.IOException("Réception coupée à $total octets sur ${m.taille}")
            }
        }
    }
}

/**
 * Trouver les points Sama du réseau local (mDNS), ou les téléphones ouverts à leurs proches ([service]).
 * Ne tourne que pendant qu'on regarde. [perdu] : un service qui a disparu (son nom mDNS).
 */
class Decouverte(
    private val c: Context,
    private val service: String = Protocole.SERVICE,
    private val perdu: (String) -> Unit = {},
    private val trouve: (Point) -> Unit,
) {
    private val nsd = c.getSystemService(NsdManager::class.java)
    private var ecoute: NsdManager.DiscoveryListener? = null

    /** Le nom mDNS de chaque adresse trouvée. */
    val noms = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun commencer() {
        if (ecoute != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(s: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(r: NsdServiceInfo) {
                        val hote = r.host?.hostAddress ?: return
                        val nom = r.attributes["nom"]?.decodeToString() ?: r.serviceName
                        trouve(Point(nom, hote, r.port).also { noms[it.adresse] = s.serviceName })
                    }

                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                })
            }

            override fun onServiceLost(s: NsdServiceInfo) = perdu(s.serviceName)
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        try {
            nsd.discoverServices(service, NsdManager.PROTOCOL_DNS_SD, l)
            ecoute = l
        } catch (_: Exception) {
        }
    }

    fun arreter() {
        ecoute?.let {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (_: Exception) {
            }
        }
        ecoute = null
    }
}
