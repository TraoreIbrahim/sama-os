package africa.samaos.proches

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Recevoir, vérifier et installer une appli (Sugu et Proche en proche). L'ordre ne change jamais : la fiche
 * signée et la version d'abord, puis le fichier entier, puis l'appli ; rien n'est installé sans les trois.
 * Une réception coupée garde ce qui est reçu et reprend plus tard ; un fichier qui ne correspond pas est supprimé.
 */
object Installations {
    enum class Etape { RECEPTION, VERIFICATION, INSTALLATION, INSTALLEE, REFUSEE, ECHEC }

    class Etat(val offre: Offre, val versionAvant: Long?) {
        val fiche get() = offre.fiche
        val point get() = offre.point
        var recu by mutableLongStateOf(0L)
        var etape by mutableStateOf(Etape.RECEPTION)
        var intact by mutableStateOf<Boolean?>(null)
        var message by mutableStateOf<String?>(null)
        val debut = System.currentTimeMillis()
        @Volatile var arret = false
        val enCours get() = etape == Etape.RECEPTION || etape == Etape.VERIFICATION || etape == Etape.INSTALLATION
    }

    /** Les réceptions, par empreinte de fichier. */
    val etats = mutableStateMapOf<String, Etat>()

    /** Change à chaque installation terminée : ce qui dépend des applis du téléphone se relit. */
    var fins by mutableIntStateOf(0)
        private set

    fun de(paquet: String): Etat? = etats.values.lastOrNull { it.fiche.paquet == paquet }

    private fun fichier(c: Context, m: Manifeste) = File(File(c.filesDir, "proches").apply { mkdirs() }, "${m.sha256}.part")

    /**
     * Lancer (ou reprendre) une réception. [recepteur] : la classe qui reçoit le résultat de l'installation
     * (une sous-classe de [RecepteurInstallation] déclarée par l'appli).
     */
    fun lancer(c: Context, o: Offre, recepteur: Class<out RecepteurInstallation>): Etat {
        val m = o.fiche
        etats[m.sha256]?.takeIf { it.enCours }?.let { return it }
        val e = Etat(o, Verification.versionInstallee(c, m.paquet))
        etats[m.sha256] = e
        val app = c.applicationContext
        Thread {
            val f = fichier(app, m)
            try {
                if (!Verification.signature(app, m) || !Verification.plusRecente(app, m)) {
                    android.util.Log.w("SamaProches", "Fiche de ${m.paquet} refusée (signature ou version)")
                    e.etape = Etape.REFUSEE
                    return@Thread
                }
                Protocole.recevoir(o.point, m, f, { e.recu = it }) { e.arret }
                if (e.arret) return@Thread
                // Seul un fichier complet est vérifié : un morceau attend la suite.
                if (f.length() < m.taille) throw java.io.IOException("incomplet")
                e.etape = Etape.VERIFICATION
                val bon = Verification.intact(f, m) && Verification.appli(app, f, m)
                e.intact = bon
                if (!bon) {
                    f.delete()
                    e.etape = Etape.REFUSEE
                    africa.samaos.bouclier.Bouclier.noter(app, "proches", "Fichier refusé", "${m.nom} · ${o.point.nom}")
                    return@Thread
                }
                e.etape = Etape.INSTALLATION
                installer(app, f, m, recepteur)
            } catch (x: Exception) {
                android.util.Log.w("SamaProches", "Réception de ${m.paquet} depuis ${o.point.adresse} : $x")
                e.message = "La connexion s'est coupée. Rapprochez-vous du point : la réception reprendra où elle s'est arrêtée."
                e.etape = Etape.ECHEC
            }
        }.start()
        return e
    }

    fun arreter(sha: String) {
        etats[sha]?.let {
            it.arret = true
            it.message = "Réception arrêtée. Elle reprendra où elle s'est arrêtée."
            it.etape = Etape.ECHEC
        }
    }

    /** Installer une appli vérifiée, avec le droit du système (INSTALL_PACKAGES) : pas de seconde question. */
    private fun installer(c: Context, f: File, m: Manifeste, recepteur: Class<out RecepteurInstallation>) {
        val pi = c.packageManager.packageInstaller
        val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(m.paquet)
            setSize(f.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(p)
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, f.length()).use { o ->
                f.inputStream().use { it.copyTo(o) }
                s.fsync(o)
            }
            val i = Intent(c, recepteur).putExtra("sha", m.sha256)
            s.commit(PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).intentSender)
        }
    }

    fun fini(c: Context, sha: String, ok: Boolean, message: String?) {
        val e = etats[sha] ?: return
        fichier(c, e.fiche).delete()
        if (ok) {
            e.etape = Etape.INSTALLEE
            Partage.noterEconomise(c, e.fiche.taille)
        } else {
            e.message = message ?: "Android n'a pas pu installer l'appli."
            e.etape = Etape.ECHEC
        }
        fins++
    }
}

/** Le résultat d'une installation, envoyé par Android. Chaque appli qui installe en déclare une sous-classe. */
abstract class RecepteurInstallation : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val sha = i.getStringExtra("sha") ?: return
        when (val statut = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Sans le droit du système, Android demande confirmation : on montre sa question.
                @Suppress("DEPRECATION")
                (i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Installations.fini(c, sha, true, null)
            else -> Installations.fini(c, sha, false, "Android a refusé l'installation ($statut).")
        }
    }
}
