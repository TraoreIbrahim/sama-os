package africa.samaos.sugu

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import africa.samaos.proches.Cercle
import africa.samaos.proches.Partage
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Ce qu'on accepte de donner (Réglages › Proche en proche › Donner), et ce qu'on a donné aujourd'hui. */
object Don {
    private fun prefs(c: Context) = c.getSharedPreferences("sugu", Context.MODE_PRIVATE)

    fun aujourdhui(c: Context): Long = if (prefs(c).getString("don_jour", "") == LocalDate.now().toString()) prefs(c).getLong("don_octets", 0L) else 0L

    fun noter(c: Context, octets: Long, proche: String) {
        if (octets <= 0) return
        prefs(c).edit().putString("don_jour", LocalDate.now().toString()).putLong("don_octets", aujourdhui(c) + octets).apply()
        Partage.noterDonne(c, octets, proche)
    }

    /** Montrer ses applis aux proches : permis, branché si demandé, sous la limite du jour. */
    fun permis(c: Context) = pourquoiPas(c) == null

    /** Pourquoi les applis ne sont pas montrées aux proches (null : elles le sont). */
    fun pourquoiPas(c: Context): String? {
        if (!Partage.donner(c)) return "Le partage de vos applis est coupé (Réglages › Proche en proche). Ce que vous envoyez vous-même part quand même."
        if (Partage.brancheSeulement(c)) {
            val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val branche = b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val niveau = if (b == null) 0 else b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (!branche || niveau < 50) return "Vos proches ne voient vos applis que téléphone branché, batterie au-dessus de 50 %. Ce que vous envoyez vous-même part quand même."
        }
        val l = Partage.limite(c)
        if (l != 0L && aujourdhui(c) >= l) return "La limite du jour est atteinte : vos proches ne voient plus vos applis jusqu'à demain."
        return null
    }
}

/**
 * Le petit serveur du téléphone ouvert à ses proches : le protocole des points Sama (PROTOCOLE.md), plus
 * l'annonce, la proposition et la réponse. Seule l'annonce est publique ; tout le reste exige la preuve d'un
 * proche reconnu, et chaque preuve ne sert qu'une fois.
 */
class Serveur(private val c: Context) {
    private val ecoute = ServerSocket(0)
    val port: Int get() = ecoute.localPort
    private val vus = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    @Volatile private var fini = false

    /** Les fichiers en cours d'envoi : la fenêtre ne se ferme pas au milieu. */
    val envoisEnCours = AtomicInteger(0)

    fun demarrer() = thread(name = "sama-proches") {
        while (!fini) {
            val s = try {
                ecoute.accept()
            } catch (_: Exception) {
                break
            }
            thread {
                try {
                    s.use { traiter(it) }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun arreter() {
        fini = true
        try {
            ecoute.close()
        } catch (_: Exception) {
        }
    }

    private class Requete(val methode: String, val chemin: String, val entetes: Map<String, String>, val corps: ByteArray)

    private fun lire(s: Socket): Requete? {
        s.soTimeout = 15_000
        val e = BufferedInputStream(s.getInputStream())
        fun ligne(): String? {
            val b = StringBuilder()
            while (true) {
                val x = e.read()
                if (x < 0) return if (b.isEmpty()) null else b.toString()
                if (x == '\n'.code) break
                if (x != '\r'.code) b.append(x.toChar())
                if (b.length > 8192) return null
            }
            return b.toString()
        }
        val premiere = ligne()?.split(' ') ?: return null
        if (premiere.size < 3) return null
        val entetes = mutableMapOf<String, String>()
        while (true) {
            val l = ligne() ?: return null
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) entetes[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            if (entetes.size > 50) return null
        }
        val n = entetes["content-length"]?.toIntOrNull() ?: 0
        if (n !in 0..16 * 1024) return null
        val corps = ByteArray(n)
        var lu = 0
        while (lu < n) {
            val k = e.read(corps, lu, n - lu)
            if (k < 0) return null
            lu += k
        }
        return Requete(premiere[0], premiere[1], entetes, corps)
    }

    private fun repondre(o: OutputStream, code: Int, corps: ByteArray = ByteArray(0), type: String = "application/json", plus: String = "") {
        val texte = when (code) {
            200 -> "OK"; 202 -> "Accepted"; 206 -> "Partial Content"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Error"
        }
        o.write("HTTP/1.1 $code $texte\r\nContent-Type: $type\r\nContent-Length: ${corps.size}\r\nConnection: close\r\n$plus\r\n".toByteArray(Charsets.US_ASCII))
        o.write(corps)
        o.flush()
    }

    private fun json(o: OutputStream, code: Int, j: JSONObject) = repondre(o, code, j.toString().toByteArray(Charsets.UTF_8))

    private fun traiter(s: Socket) {
        val r = lire(s) ?: return
        val o = s.getOutputStream()
        if (r.methode == "GET" && r.chemin == "/proches/v1/annonce") return json(o, 200, Echange.annonceJson())
        // Tout le reste : seulement un proche reconnu, avec une preuve neuve.
        val en = r.entetes["sama-proche"] ?: return repondre(o, 403).also { android.util.Log.w("SamaProches", "Requête sans preuve : ${r.methode} ${r.chemin}") }
        val (id, n) = Cercle.verifier(c, Echange.nombre, en, "${r.methode} ${r.chemin}")
            ?: return repondre(o, 403).also { android.util.Log.w("SamaProches", "Preuve refusée : ${r.methode} ${r.chemin}") }
        if (!vus.add(n)) return repondre(o, 403).also { android.util.Log.w("SamaProches", "Preuve déjà servie : ${r.chemin}") }
        when {
            r.methode == "GET" && r.chemin == "/proches/v1/catalogue" -> {
                val paquets = JSONArray(offertes(id).map { d -> d.fiche.json().also { j -> d.vitrine?.let { j.put("vitrine", it.json()) } } })
                json(o, 200, JSONObject().put("paquets", paquets))
            }
            r.methode == "GET" && r.chemin.startsWith("/proches/v1/fichier/") -> fichier(o, r, id, r.chemin.substringAfterLast('/').lowercase())
            r.methode == "POST" && r.chemin == "/proches/v1/proposition" -> {
                val j = try {
                    JSONObject(r.corps.decodeToString())
                } catch (_: Exception) {
                    return repondre(o, 400)
                }
                val sha = j.optString("sha256").lowercase()
                val port = j.optInt("port")
                if (sha.length != 64 || port !in 1..65535) return repondre(o, 400)
                val hote = s.inetAddress.hostAddress ?: return repondre(o, 400)
                thread { Echange.recevoirProposition(c, id, hote, port, sha) }
                json(o, 202, JSONObject())
            }
            r.methode == "POST" && r.chemin == "/proches/v1/reponse" -> {
                val j = try {
                    JSONObject(r.corps.decodeToString())
                } catch (_: Exception) {
                    return repondre(o, 400)
                }
                Echange.reponse(id, j.optString("sha256").lowercase(), j.optString("reponse"))
                json(o, 200, JSONObject())
            }
            else -> repondre(o, 404)
        }
    }

    /** Ce qu'on montre à ce proche : ce qu'on peut donner si le partage est permis, sinon seulement ce qu'on lui envoie. */
    private fun offertes(id: String): List<Gardees.Donnable> {
        val tout = Gardees.donnables(c)
        if (Don.permis(c)) return tout
        val envoyees = Echange.envoyeesA(id)
        return tout.filter { it.fiche.sha256 in envoyees }
    }

    private fun fichier(o: OutputStream, r: Requete, id: String, sha: String) {
        val offertes = offertes(id)
        val d = offertes.firstOrNull { it.fiche.sha256 == sha }
        val f: File = d?.fichier
            ?: offertes.firstOrNull { it.vitrine?.icone == sha }?.let { Gardees.icone(c, sha) }
            ?: return repondre(o, 404)
        val taille = f.length()
        val debut = Regex("bytes=(\\d+)-").find(r.entetes["range"].orEmpty())?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it in 1 until taille } ?: 0L
        val entete = buildString {
            append(if (debut > 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/octet-stream\r\nContent-Length: ${taille - debut}\r\nConnection: close\r\n")
            if (debut > 0) append("Content-Range: bytes $debut-${taille - 1}/$taille\r\n")
            append("\r\n")
        }
        o.write(entete.toByteArray(Charsets.US_ASCII))
        if (d == null) {
            f.inputStream().use { it.copyTo(o) }
            return o.flush()
        }
        envoisEnCours.incrementAndGet()
        var envoye = 0L
        try {
            f.inputStream().use { e ->
                e.skip(debut)
                val t = ByteArray(64 * 1024)
                var dernier = 0L
                while (true) {
                    val n = e.read(t)
                    if (n < 0) break
                    o.write(t, 0, n)
                    envoye += n
                    if (envoye - dernier > 256 * 1024) {
                        dernier = envoye
                        Echange.servi(id, sha, debut + envoye, false)
                    }
                }
            }
            o.flush()
            Echange.servi(id, sha, debut + envoye, debut + envoye == taille)
        } finally {
            envoisEnCours.decrementAndGet()
            Don.noter(c, envoye, id)
        }
    }
}
