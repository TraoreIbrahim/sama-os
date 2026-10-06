package africa.samaos.sugu

import android.content.Context
import africa.samaos.proches.Manifeste
import africa.samaos.proches.Offre
import africa.samaos.proches.Verification
import africa.samaos.proches.Vitrine
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Les applis que ce téléphone peut donner à ses proches : celles dont le fichier installé est exactement celui
 * d'une fiche signée, reçue d'un point Sama ou d'un proche. On garde la fiche (et sa vitrine) à l'installation ;
 * le proche qui reçoit refait de toute façon toutes les vérifications.
 */
object Gardees {
    class Donnable(val fiche: Manifeste, val vitrine: Vitrine?, val fichier: File)

    private fun dossier(c: Context) = File(c.filesDir, "fiches").apply { mkdirs() }

    /** Les empreintes des fichiers installés, calculées une fois par fichier (chemin, taille, date). */
    private val empreintes = ConcurrentHashMap<String, String>()

    private fun installe(c: Context, paquet: String): Pair<File, String>? {
        val ai = try {
            c.packageManager.getApplicationInfo(paquet, 0)
        } catch (_: Exception) {
            return null
        }
        // Une appli livrée en plusieurs morceaux ne correspond à aucune fiche : elle ne se donne pas.
        if (!ai.splitSourceDirs.isNullOrEmpty()) return null
        val f = File(ai.sourceDir)
        val sha = empreintes.getOrPut("${f.path}|${f.length()}|${f.lastModified()}") { Verification.empreinte(f) }
        return f to sha
    }

    fun garder(c: Context, o: Offre) {
        val j = o.fiche.json()
        o.vitrine?.let { j.put("vitrine", it.json()) }
        File(dossier(c), o.fiche.paquet + ".json").writeText(j.toString())
        // L'icône aussi, pour la montrer aux proches.
        o.vitrine?.icone?.takeIf { it.length == 64 }?.let { sha ->
            val cache = File(File(c.cacheDir, "icones"), sha)
            val garde = File(dossier(c), sha)
            if (!garde.exists() && cache.exists() && Verification.empreinte(cache) == sha) cache.copyTo(garde)
        }
    }

    /** Garder la fiche des applis déjà installées dont le fichier est exactement celui de la fiche. */
    fun adopter(c: Context, offres: List<Offre>) = offres.forEach { o ->
        val f = File(dossier(c), o.fiche.paquet + ".json")
        if (f.exists() && Manifeste.depuis(JSONObject(f.readText()))?.sha256 == o.fiche.sha256) return@forEach
        if (Verification.versionInstallee(c, o.fiche.paquet) != o.fiche.version) return@forEach
        if (installe(c, o.fiche.paquet)?.second == o.fiche.sha256) garder(c, o)
    }

    fun donnables(c: Context): List<Donnable> = dossier(c).listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
        val j = try {
            JSONObject(f.readText())
        } catch (_: Exception) {
            return@mapNotNull null
        }
        val m = Manifeste.depuis(j)?.takeIf { Verification.signature(c, it) } ?: return@mapNotNull null
        if (Verification.versionInstallee(c, m.paquet) != m.version) return@mapNotNull null
        val (fichier, sha) = installe(c, m.paquet) ?: return@mapNotNull null
        if (sha != m.sha256) return@mapNotNull null
        val v = j.optJSONObject("vitrine")?.let { Vitrine.depuis(it) }
            ?.takeIf { it.paquet == m.paquet && it.version == m.version && it.editeur == m.editeur && Vitrine.verifier(c, it) }
        Donnable(m, v, fichier)
    }.sortedBy { it.fiche.nom.lowercase() }

    fun donnable(c: Context, paquet: String) = donnables(c).firstOrNull { it.fiche.paquet == paquet }

    fun icone(c: Context, sha: String): File? = File(dossier(c), sha).takeIf { sha.length == 64 && it.exists() }
}
