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

/** Un point Sama (ordinateur Sama d'une école, d'une mairie, d'un cybercafé) ou un téléphone qui donne. */
class Point(val nom: String, val hote: String, val port: Int) {
    val adresse get() = "$hote:$port"
}

/**
 * Le protocole de Proche en proche, version 1 : du HTTP tout simple sur le réseau local (Wi-Fi, point d'accès,
 * Wi-Fi Direct). Rien n'y est secret ni protégé : ce sont la signature des fiches et l'empreinte des fichiers qui
 * garantissent qu'on reçoit ce que l'éditeur a publié.
 *   GET /proches/v1/catalogue        → {"point": {"nom": …}, "paquets": [fiches signées]}
 *   GET /proches/v1/fichier/<sha256> → le fichier ; « Range: bytes=N- » pour reprendre où l'on s'était arrêté
 */
object Protocole {
    const val SERVICE = "_samapoint._tcp."
    const val PORT = 8765
    private const val DELAI = 15_000

    private class Reponse(val code: Int, val entetes: Map<String, String>, val corps: InputStream, val socket: Socket)

    private fun demander(p: Point, chemin: String, debut: Long = 0): Reponse {
        val s = Socket()
        s.connect(InetSocketAddress(p.hote, p.port), DELAI)
        s.soTimeout = DELAI
        val requete = buildString {
            append("GET $chemin HTTP/1.1\r\nHost: ${p.hote}\r\nConnection: close\r\nUser-Agent: SamaProches/1\r\n")
            if (debut > 0) append("Range: bytes=$debut-\r\n")
            append("\r\n")
        }
        s.getOutputStream().apply {
            write(requete.toByteArray(Charsets.US_ASCII))
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

    /** Le nom du point et ses fiches. Les fiches mal signées ou d'un éditeur inconnu sont écartées et comptées. */
    class Catalogue(val nom: String?, val paquets: List<Manifeste>, val ecartes: Int)

    fun catalogue(c: Context, p: Point): Catalogue {
        val r = demander(p, "/proches/v1/catalogue")
        val texte = r.socket.use { if (r.code == 200) r.corps.readBytes().decodeToString() else error("Le point a répondu ${r.code}") }
        val o = JSONObject(texte)
        val bruts = o.optJSONArray("paquets")
        val tous = (0 until (bruts?.length() ?: 0)).mapNotNull { Manifeste.depuis(bruts!!.getJSONObject(it)) }
        val bons = tous.filter { Verification.signature(c, it) }
        return Catalogue(o.optJSONObject("point")?.optString("nom")?.ifBlank { null }, bons, (bruts?.length() ?: 0) - bons.size)
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

/** Trouver les points Sama du réseau local (mDNS). Ne tourne que pendant qu'on regarde. */
class Decouverte(private val c: Context, private val trouve: (Point) -> Unit) {
    private val nsd = c.getSystemService(NsdManager::class.java)
    private var ecoute: NsdManager.DiscoveryListener? = null

    fun commencer() {
        if (ecoute != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(s: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(r: NsdServiceInfo) {
                        val hote = r.host?.hostAddress ?: return
                        val nom = r.attributes["nom"]?.decodeToString() ?: r.serviceName
                        trouve(Point(nom, hote, r.port))
                    }

                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                })
            }

            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        try {
            nsd.discoverServices(Protocole.SERVICE, NsdManager.PROTOCOL_DNS_SD, l)
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
