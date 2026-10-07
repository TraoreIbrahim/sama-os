package africa.samaos.sugu

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import africa.samaos.proches.Cercle
import africa.samaos.proches.Enveloppe
import africa.samaos.proches.Partage
import africa.samaos.proches.Protocole
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
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
 * proche reconnu, chaque preuve ne sert qu'une fois, et la requête comme la réponse sont chiffrées ([Enveloppe]).
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
        // Une requête tient dans quelques blocs chiffrés (une proposition, une réponse : un seul).
        if (n !in 0..4 * Enveloppe.BLOC) return null
        val corps = ByteArray(n)
        var lu = 0
        while (lu < n) {
            val k = e.read(corps, lu, n - lu)
            if (k < 0) return null
            lu += k
        }
        return Requete(premiere[0], premiere[1], entetes, corps)
    }

    /** Une réponse en clair, hors de l'enveloppe : l'annonce, ou un refus (sans preuve, preuve fausse ou déjà servie). */
    private fun repondre(o: OutputStream, code: Int, corps: ByteArray = ByteArray(0)) {
        val texte = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Error"
        }
        o.write("HTTP/1.1 $code $texte\r\nContent-Type: application/json\r\nContent-Length: ${corps.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
        o.write(corps)
        o.flush()
    }

    /**
     * La réponse à un proche : en clair, seulement « 200 » et la taille de l'enveloppe ; le vrai code, les en-têtes
     * et le contenu sont dedans, chiffrés avec la clé de la réponse.
     */
    private class Sortie(private val o: OutputStream, private val cle: ByteArray) {
        /** [contenu] doit écrire exactement [longueur] octets. */
        fun ecrire(code: Int, entetes: String = "", longueur: Long = 0, contenu: (OutputStream) -> Unit = {}) {
            val tete = "$code\n$entetes\n".toByteArray(Charsets.UTF_8)
            o.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${Enveloppe.taille(tete.size + longueur)}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            val w = Enveloppe.Ecrivain(cle, o)
            w.write(tete)
            contenu(w)
            w.close()
        }

        fun json(code: Int, j: JSONObject) {
            val b = j.toString().toByteArray(Charsets.UTF_8)
            ecrire(code, "Content-Type: application/json\nContent-Length: ${b.size}\n", b.size.toLong()) { it.write(b) }
        }
    }

    /** La vraie requête d'un proche, sortie de l'enveloppe. */
    private class Interne(val methode: String, val chemin: String, val entetes: Map<String, String>, val corps: ByteArray)

    private fun ouvrir(e: InputStream): Interne {
        val premiere = Enveloppe.ligne(e)?.split(' ') ?: throw IOException("Requête vide")
        if (premiere.size != 2) throw IOException("Requête illisible")
        val entetes = mutableMapOf<String, String>()
        while (true) {
            val l = Enveloppe.ligne(e) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) entetes[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            if (entetes.size > 20) throw IOException("Trop d'en-têtes")
        }
        return Interne(premiere[0], premiere[1], entetes, e.readBytes())
    }

    private fun traiter(s: Socket) {
        val r = lire(s) ?: return
        val o = s.getOutputStream()
        if (r.methode == "GET" && r.chemin == "/proches/v1/annonce") return repondre(o, 200, Echange.annonceJson().toString().toByteArray(Charsets.UTF_8))
        // Tout le reste : seulement un proche reconnu, avec une preuve neuve, et chiffré.
        if (r.methode != "POST" || r.chemin != Protocole.CHIFFRE) return repondre(o, 403).also { android.util.Log.w("SamaProches", "Requête en clair refusée : ${r.methode} ${r.chemin}") }
        val en = r.entetes["sama-proche"] ?: return repondre(o, 403).also { android.util.Log.w("SamaProches", "Requête sans preuve") }
        val v = Cercle.verifier(c, Echange.nombre, en, "POST ${Protocole.CHIFFRE}")
            ?: return repondre(o, 403).also { android.util.Log.w("SamaProches", "Preuve refusée") }
        if (!vus.add(v.nombre)) return repondre(o, 403).also { android.util.Log.w("SamaProches", "Preuve déjà servie") }
        val q = try {
            ouvrir(Enveloppe.Lecteur(v.demande, r.corps.inputStream()))
        } catch (x: IOException) {
            android.util.Log.w("SamaProches", "Enveloppe illisible : $x")
            return repondre(o, 400)
        }
        val id = v.id
        val so = Sortie(o, v.reponse)
        when {
            q.methode == "GET" && q.chemin == "/proches/v1/catalogue" -> {
                val paquets = JSONArray(offertes(id).map { d -> d.fiche.json().also { j -> d.vitrine?.let { j.put("vitrine", it.json()) } } })
                so.json(200, JSONObject().put("paquets", paquets))
            }
            q.methode == "GET" && q.chemin.startsWith("/proches/v1/fichier/") -> fichier(so, q, id, q.chemin.substringAfterLast('/').lowercase())
            q.methode == "POST" && q.chemin == "/proches/v1/proposition" -> {
                val j = try {
                    JSONObject(q.corps.decodeToString())
                } catch (_: Exception) {
                    return so.ecrire(400)
                }
                val sha = j.optString("sha256").lowercase()
                val port = j.optInt("port")
                if (sha.length != 64 || port !in 1..65535) return so.ecrire(400)
                val hote = s.inetAddress.hostAddress ?: return so.ecrire(400)
                thread { Echange.recevoirProposition(c, id, hote, port, sha) }
                so.json(202, JSONObject())
            }
            q.methode == "POST" && q.chemin == "/proches/v1/reponse" -> {
                val j = try {
                    JSONObject(q.corps.decodeToString())
                } catch (_: Exception) {
                    return so.ecrire(400)
                }
                Echange.reponse(id, j.optString("sha256").lowercase(), j.optString("reponse"))
                so.json(200, JSONObject())
            }
            else -> so.ecrire(404)
        }
    }

    /** Ce qu'on montre à ce proche : ce qu'on peut donner si le partage est permis, sinon seulement ce qu'on lui envoie. */
    private fun offertes(id: String): List<Gardees.Donnable> {
        val tout = Gardees.donnables(c)
        if (Don.permis(c)) return tout
        val envoyees = Echange.envoyeesA(id)
        return tout.filter { it.fiche.sha256 in envoyees }
    }

    private fun fichier(so: Sortie, q: Interne, id: String, sha: String) {
        val offertes = offertes(id)
        val d = offertes.firstOrNull { it.fiche.sha256 == sha }
        val f: File = d?.fichier
            ?: offertes.firstOrNull { it.vitrine?.icone == sha }?.let { Gardees.icone(c, sha) }
            ?: return so.ecrire(404)
        val taille = f.length()
        val debut = Regex("bytes=(\\d+)-").find(q.entetes["range"].orEmpty())?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it in 1 until taille } ?: 0L
        val entetes = buildString {
            append("Content-Type: application/octet-stream\nContent-Length: ${taille - debut}\n")
            if (debut > 0) append("Content-Range: bytes $debut-${taille - 1}/$taille\n")
        }
        // Une icône : petite, toujours entière.
        if (d == null) return so.ecrire(200, "Content-Type: application/octet-stream\nContent-Length: $taille\n", taille) { o -> f.inputStream().use { it.copyTo(o) } }
        envoisEnCours.incrementAndGet()
        var envoye = 0L
        try {
            so.ecrire(if (debut > 0) 206 else 200, entetes, taille - debut) { o ->
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
            }
            Echange.servi(id, sha, debut + envoye, debut + envoye == taille)
        } finally {
            envoisEnCours.decrementAndGet()
            Don.noter(c, envoye, id)
        }
    }
}
