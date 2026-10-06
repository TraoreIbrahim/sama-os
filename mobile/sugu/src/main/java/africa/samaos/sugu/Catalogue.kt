package africa.samaos.sugu

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import africa.samaos.proches.Decouverte
import africa.samaos.proches.Installations
import africa.samaos.proches.Offre
import africa.samaos.proches.Partage
import africa.samaos.proches.Point
import africa.samaos.proches.Protocole
import africa.samaos.proches.RecepteurInstallation
import africa.samaos.proches.Verification
import java.io.File

/** Le résultat d'une installation lancée par Sugu : réussie, sa fiche est gardée pour pouvoir la redonner. */
class ResultatInstallation : RecepteurInstallation() {
    override fun onReceive(c: Context, i: Intent) {
        super.onReceive(c, i)
        val sha = i.getStringExtra("sha") ?: return
        if (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) != PackageInstaller.STATUS_SUCCESS) return
        val o = Installations.etats[sha]?.offre ?: return
        val app = c.applicationContext
        Thread { Gardees.garder(app, o) }.start()
    }
}

/**
 * Une appli installée, telle que « Mes applis » la montre. [sama] : signée comme le système (les applis de Sama) ;
 * c'est la signature qui compte, pas le nom du paquet, qu'une fausse appli peut copier.
 */
class Installee(val paquet: String, val nom: String, val version: Long, val versionNom: String, val parSugu: Boolean, val systeme: Boolean, val sama: Boolean)

/**
 * Le catalogue de Sugu : ce que proposent les points Sama à portée et les téléphones des proches ouverts (et,
 * bientôt, le catalogue en ligne). Tout ce qui est montré a une fiche signée ; la vitrine (description,
 * catégorie…) aussi.
 */
object Catalogue {
    /** Les points interrogés et ce qu'ils ont répondu (null : pas de réponse). */
    val points = mutableStateMapOf<String, Protocole.Catalogue?>()
    val trouves = mutableStateListOf<Point>()
    var charge by mutableStateOf(false)
    private val icones = mutableStateMapOf<String, ImageBitmap?>()
    private var decouverte: Decouverte? = null

    fun connus(c: Context): List<Point> = (Partage.points(c) + trouves).distinctBy { it.adresse }.filter { !Partage.bloque(c, it.adresse) }

    /** Interroger tous les points connus (à faire hors du fil principal). */
    fun actualiser(c: Context) {
        val l = connus(c)
        l.forEach { p ->
            val r = try {
                Protocole.catalogue(c, p)
            } catch (_: Exception) {
                null
            }
            points[p.adresse] = r
            // Le point a donné son nom : on le garde.
            val nom = r?.nom
            if (nom != null && nom != p.nom) Partage.ajouterPoint(c, Point(nom, p.hote, p.port))
        }
        charge = true
        // Les applis déjà installées depuis un fichier conforme à une fiche peuvent être redonnées aux proches.
        Gardees.adopter(c, offres())
    }

    fun ecouter(c: Context, nouveau: (Point) -> Unit) {
        if (decouverte != null) return
        decouverte = Decouverte(c.applicationContext) { p ->
            if (trouves.none { it.adresse == p.adresse }) {
                trouves += p
                nouveau(p)
            }
        }.also { it.commencer() }
    }

    fun arreterEcoute() {
        decouverte?.arreter()
        decouverte = null
    }

    /** Toutes les offres, une par appli : la version la plus récente, d'abord depuis les points Sama, puis chez les proches. */
    fun offres(): List<Offre> = (points.values.filterNotNull() + Echange.voisins.values.mapNotNull { it.catalogue }).flatMap { it.offres }
        .groupBy { it.fiche.paquet }.values.map { l -> l.maxBy { it.fiche.version } }
        .sortedBy { it.nom.lowercase() }

    fun offre(paquet: String) = offres().firstOrNull { it.fiche.paquet == paquet }

    /** Les applis du téléphone pour lesquelles un point a une version plus récente. */
    fun misesAJour(c: Context): List<Offre> = offres().filter { o ->
        val v = Verification.versionInstallee(c, o.fiche.paquet)
        v != null && o.fiche.version > v
    }

    /** L'icône d'une offre : servie par le point, vérifiée par son empreinte, gardée sur le téléphone. */
    fun icone(c: Context, o: Offre): ImageBitmap? {
        val sha = o.vitrine?.icone?.takeIf { it.length == 64 } ?: return null
        if (icones.containsKey(sha)) return icones[sha]
        icones[sha] = null
        Thread {
            val f = File(File(c.cacheDir, "icones").apply { mkdirs() }, sha)
            val octets = if (f.exists() && Verification.empreinte(f) == sha) f.readBytes()
            else Gardees.icone(c, sha)?.readBytes() ?: Protocole.image(o.point, sha)?.also { f.writeBytes(it) }
            icones[sha] = octets?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }.start()
        return null
    }

    /** Les applis de la personne : celles de Sugu, et celles venues d'ailleurs (« hors Sugu »). */
    fun installees(c: Context): List<Installee> {
        val pm = c.packageManager
        return pm.getInstalledPackages(0).mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            val systeme = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (pm.getLaunchIntentForPackage(p.packageName) == null) return@mapNotNull null
            val source = try {
                pm.getInstallSourceInfo(p.packageName).installingPackageName
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            val sama = pm.checkSignatures(c.packageName, p.packageName) == PackageManager.SIGNATURE_MATCH
            Installee(p.packageName, ai.loadLabel(pm).toString(), p.longVersionCode, p.versionName.orEmpty(), source == c.packageName, systeme, sama)
        }.sortedBy { it.nom.lowercase() }
    }

    fun lancer(c: Context, o: Offre) = Installations.lancer(c, o, ResultatInstallation::class.java)
}
