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
 * [proche] : l'identifiant du proche reconnu dont c'est le téléphone ; [preuve] : de quoi prouver, à chaque
 * requête, qu'on est l'un de ses proches (un téléphone ne répond qu'à eux).
 */
class Point(val nom: String, val hote: String, val port: Int, val proche: String? = null, val preuve: ((String) -> String?)? = null) {
    val adresse get() = "$hote:$port"
}

/**
 * Le protocole de Proche en proche, version 1 : du HTTP tout simple sur le réseau local (Wi-Fi, point d'accès,
 * Wi-Fi Direct). Rien n'y est secret ni protégé : ce sont la signature des fiches et l'empreinte des fichiers qui
 * garantissent qu'on reçoit ce que l'éditeur a publié.
 *   GET /proches/v1/catalogue        → {"point": {"nom": …}, "paquets": [fiches signées]}
 *   GET /proches/v1/fichier/<sha256> → le fichier ; « Range: bytes=N- » pour reprendre où l'on s'était arrêté
 * Entre téléphones de proches (PROTOCOLE.md), en plus : l'annonce, la proposition et la réponse, et l'en-tête
 * « Sama-Proche » sur chaque requête.
 */
object Protocole {
    const val SERVICE = "_samapoint._tcp."
    const val PORT = 8765
    /** Les téléphones ouverts à leurs proches. */
    const val SERVICE_PROCHE = "_samaproche._tcp."
    private const val DELAI = 15_000

    private class Reponse(val code: Int, val entetes: Map<String, String>, val corps: InputStream, val socket: Socket)

    private fun demander(p: Point, chemin: String, debut: Long = 0, corps: ByteArray? = null): Reponse {
        val methode = if (corps != null) "POST" else "GET"
        // La preuve se calcule avant d'ouvrir la connexion : chez un proche, pas de preuve, pas de requête.
        val preuve = p.preuve?.let { it("$methode $chemin") ?: error("Pas de preuve pour ce proche") }
        val s = Socket()
        s.connect(InetSocketAddress(p.hote, p.port), DELAI)
        s.soTimeout = DELAI
        val requete = buildString {
            append("$methode $chemin HTTP/1.1\r\nHost: ${p.hote}\r\nConnection: close\r\nUser-Agent: SamaProches/1\r\n")
            if (preuve != null) append("Sama-Proche: $preuve\r\n")
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
        fun ligne(): String {
            val b = StringBuilder()
            while (true) {
                val x = e.read()
                if (x < 0 || x == '\n'.code) break
                if (x != '\r'.code) b.append(x.toChar())
                if (b.length > 8192) error("En-tête trop long")
            }
            return b.toString()
        }
        val statut = ligne()
        val code = statut.split(' ').getOrNull(1)?.toIntOrNull() ?: error("Réponse illisible")
        val entetes = mutableMapOf<String, String>()
        while (true) {
            val l = ligne()
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) entetes[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        return Reponse(code, entetes, e, s)
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
        // Le point garde ce qui le rattache à un proche (sa preuve) : les fichiers se demandent avec.
        val ici = if (nom == null || nom == p.nom) p else Point(nom, p.hote, p.port, p.proche, p.preuve)
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
            Annonce(o.getString("n"), (0 until (e?.length() ?: 0)).map { e!!.getString(it) }).takeIf { o.optInt("v") == 1 }
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
