package africa.samaos.sugu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import africa.samaos.proches.Cercle
import africa.samaos.proches.Decouverte
import africa.samaos.proches.Editeurs
import africa.samaos.proches.Manifeste
import africa.samaos.proches.Offre
import africa.samaos.proches.Partage
import africa.samaos.proches.Point
import africa.samaos.proches.Protocole
import africa.samaos.proches.Verification
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Ce téléphone ouvert à ses proches (innovation 6, maquettes i6-envoyer-appli, i6-emetteur, i6-maj-voisin,
 * i6-sugu-proches) : 10 minutes, à la demande. Pendant ce temps, il annonce un nombre au hasard et une étiquette
 * par proche (seuls ses proches reconnus s'y retrouvent), cherche les téléphones de ses proches ouverts eux aussi,
 * leur montre les applis qu'il peut donner et reçoit leurs propositions. Rien d'autre n'est diffusé.
 */
object Echange {
    const val DUREE = 10 * 60_000L

    /** La fin de la fenêtre (0 : fermée). */
    var jusqua by mutableLongStateOf(0L)
        private set

    fun ouvert() = System.currentTimeMillis() < jusqua

    /** Le téléphone d'un proche reconnu, ouvert lui aussi, et ce qu'il propose. */
    class Voisin(val id: String, val nom: String, val nombre: String, val point: Point) {
        var catalogue by mutableStateOf<Protocole.Catalogue?>(null)
        @Volatile var echecs = 0
    }

    val voisins = mutableStateMapOf<String, Voisin>()

    /** Une appli qu'un proche nous envoie : on choisit de la recevoir ou non. */
    class Proposition(val de: Voisin, val offre: Offre)

    val propositions = mutableStateListOf<Proposition>()

    enum class EtatEnvoi { PROPOSEE, ENVOI, ENVOYEE, REFUSEE, DEJA, ECHEC }

    /** Une appli qu'on envoie à un proche, et où en est l'envoi. */
    class Envoi(val id: String, val nom: String, val fiche: Manifeste) {
        val quand = System.currentTimeMillis()
        var etat by mutableStateOf(EtatEnvoi.PROPOSEE)
        var envoye by mutableLongStateOf(0L)
    }

    val envois = mutableStateMapOf<String, Envoi>()

    @Volatile var nombre: String = ""
        private set
    @Volatile private var etiquettes: List<String> = emptyList()
    @Volatile private var noms: Map<String, String> = emptyMap()
    private var serveur: Serveur? = null
    private var annonce: NsdManager.RegistrationListener? = null
    private var decouverte: Decouverte? = null

    fun annonceJson(): JSONObject = JSONObject().put("v", 1).put("n", nombre).put("e", JSONArray(etiquettes))

    fun envoisEnCours() = (serveur?.envoisEnCours?.get() ?: 0) > 0

    /** Ouvrir (ou prolonger) la fenêtre : 10 minutes. */
    fun ouvrir(c: Context) {
        jusqua = System.currentTimeMillis() + DUREE
        Partage.reglerOuvert(c, jusqua)
        c.startForegroundService(Intent(c, ServiceEchange::class.java))
    }

    fun fermer(c: Context) {
        c.stopService(Intent(c, ServiceEchange::class.java))
    }

    /** Par le service : le serveur, l'annonce et la recherche des proches. */
    internal fun demarrer(c: Context) {
        if (serveur != null) return
        val app = c.applicationContext
        if (!ouvert()) {
            jusqua = System.currentTimeMillis() + DUREE
            Partage.reglerOuvert(app, jusqua)
        }
        nombre = Cercle.nombre()
        val s = Serveur(app).also { serveur = it }
        s.demarrer()
        thread {
            noms = Cercle.reconnus(app).associate { it.id to it.nom }
            etiquettes = Cercle.etiquettes(app, nombre)
            annoncer(app, s.port)
            val d = Decouverte(app, Protocole.SERVICE_PROCHE, perdu = { nom -> perdu(nom) }) { p -> thread { examiner(app, p) } }
            decouverte = d
            d.commencer()
        }
    }

    internal fun arreter(c: Context) {
        jusqua = 0L
        Partage.reglerOuvert(c, 0L)
        decouverte?.arreter()
        decouverte = null
        annonce?.let {
            try {
                c.getSystemService(NsdManager::class.java).unregisterService(it)
            } catch (_: Exception) {
            }
        }
        annonce = null
        serveur?.arreter()
        serveur = null
        voisins.clear()
        propositions.clear()
        envois.values.filter { it.etat == EtatEnvoi.PROPOSEE || it.etat == EtatEnvoi.ENVOI }.forEach { it.etat = EtatEnvoi.ECHEC }
        c.getSystemService(NotificationManager::class.java).cancel(NOTIF_PROPOSITION)
    }

    /** Annoncer le téléphone sous un nom de hasard : rien qui désigne la personne. */
    private fun annoncer(c: Context, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "sama-" + Cercle.nombre().take(8)
            serviceType = Protocole.SERVICE_PROCHE
            this.port = port
            setAttribute("v", "1")
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {}
            override fun onRegistrationFailed(s: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, e: Int) {}
        }
        try {
            c.getSystemService(NsdManager::class.java).registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            annonce = l
        } catch (_: Exception) {
        }
    }

    private fun perdu(nomService: String) {
        android.util.Log.i("SamaProches", "Service perdu : $nomService")
        val adresses = decouverte?.noms?.filterValues { it == nomService }?.keys.orEmpty()
        voisins.values.filter { it.point.adresse in adresses }.forEach { voisins.remove(it.id) }
    }

    /** Un téléphone ouvert sur le réseau : est-ce l'un de nos proches ? On ne le sait que par son annonce. */
    fun examiner(c: Context, p: Point): Voisin? {
        val a = Protocole.annonce(p) ?: return null
        if (a.nombre == nombre) return null
        val id = Cercle.trouver(c, a) ?: return null
        voisins[id]?.takeIf { it.nombre == a.nombre && it.point.adresse == p.adresse }?.let { return it }
        val nom = noms[id] ?: Cercle.reconnus(c).firstOrNull { it.id == id }?.nom?.also { noms = noms + (id to it) } ?: "Un proche"
        val point = Point(nom, p.hote, p.port, proche = id) { requete -> Cercle.entete(c, id, a.nombre, requete) }
        val v = Voisin(id, nom, a.nombre, point)
        voisins[id] = v
        actualiser(c, v)
        return v
    }

    fun actualiser(c: Context, v: Voisin) {
        try {
            v.catalogue = Protocole.catalogue(c, v.point)
            v.echecs = 0
        } catch (x: Exception) {
            android.util.Log.w("SamaProches", "Catalogue de ${v.nom} (${v.point.adresse}) : $x")
            if (++v.echecs >= 2 && voisins[v.id] === v) voisins.remove(v.id)
        }
    }

    fun actualiserTous(c: Context) = voisins.values.toList().forEach { actualiser(c, it) }

    // ——— Envoyer ———

    fun envoi(id: String, sha: String) = envois["$id/$sha"]

    /** Proposer une appli à un proche à portée ; s'il accepte, son téléphone vient la prendre ici. */
    fun proposer(v: Voisin, fiche: Manifeste) {
        val port = serveur?.port ?: return
        val e = Envoi(v.id, v.nom, fiche)
        envois["${v.id}/${fiche.sha256}"] = e
        thread {
            val code = Protocole.poster(v.point, "/proches/v1/proposition", JSONObject().put("sha256", fiche.sha256).put("port", port))
            if (code != 202) e.etat = EtatEnvoi.ECHEC
        }
    }

    /** Les fichiers qu'on a soi-même envoyés à ce proche : ils lui sont montrés même si le partage est coupé. */
    fun envoyeesA(id: String): Set<String> = envois.values.filter { it.id == id && it.etat != EtatEnvoi.ECHEC }.map { it.fiche.sha256 }.toSet()

    internal fun servi(id: String, sha: String, total: Long, complet: Boolean) {
        val e = envois["$id/$sha"] ?: return
        e.envoye = total
        e.etat = if (complet) EtatEnvoi.ENVOYEE else EtatEnvoi.ENVOI
    }

    internal fun reponse(id: String, sha: String, r: String) {
        val e = envois["$id/$sha"] ?: return
        e.etat = if (r == "deja") EtatEnvoi.DEJA else EtatEnvoi.REFUSEE
    }

    // ——— Recevoir ———

    /** Par le serveur : un proche nous propose une appli. On va lire sa fiche chez lui, et elle doit être signée. */
    internal fun recevoirProposition(c: Context, id: String, hote: String, port: Int, sha: String) {
        if (!ouvert() || propositions.size >= 5) return
        val v = voisins[id]?.takeIf { it.point.hote == hote } ?: examiner(c, Point("", hote, port))?.takeIf { it.id == id } ?: return
        actualiser(c, v)
        val o = v.catalogue?.offres?.firstOrNull { it.fiche.sha256 == sha } ?: return
        val installee = Verification.versionInstallee(c, o.fiche.paquet)
        if (installee != null && installee >= o.fiche.version) {
            Protocole.poster(v.point, "/proches/v1/reponse", JSONObject().put("sha256", sha).put("reponse", "deja"))
            return
        }
        if (propositions.any { it.de.id == id && it.offre.fiche.sha256 == sha }) return
        propositions += Proposition(v, o)
        notifier(c, v, o)
    }

    fun accepter(c: Context, p: Proposition) {
        propositions.remove(p)
        c.getSystemService(NotificationManager::class.java).cancel(NOTIF_PROPOSITION)
        Catalogue.lancer(c, p.offre)
    }

    fun refuser(c: Context, p: Proposition) {
        propositions.remove(p)
        c.getSystemService(NotificationManager::class.java).cancel(NOTIF_PROPOSITION)
        thread { Protocole.poster(p.de.point, "/proches/v1/reponse", JSONObject().put("sha256", p.offre.fiche.sha256).put("reponse", "non")) }
    }

    // ——— Notifications ———

    const val NOTIF_OUVERT = 1
    const val NOTIF_PROPOSITION = 2

    internal fun canaux(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ouvert", "Ouvert à vos proches", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("propositions", "Applis envoyées par vos proches", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun ouvrirSugu(c: Context, action: String) = PendingIntent.getActivity(
        c, action.hashCode(), Intent(action).setClass(c, Sugu::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notifier(c: Context, v: Voisin, o: Offre) {
        canaux(c)
        val n = Notification.Builder(c, "propositions")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("${v.nom} vous envoie ${o.nom}")
            .setContentText("${taille(o.fiche.taille)} · vérifiée par ${Editeurs.nomCourt(o.fiche.editeur)} · sans data")
            .setContentIntent(ouvrirSugu(c, "africa.samaos.action.SUGU_PROCHES"))
            .setAutoCancel(true)
            .build()
        try {
            c.getSystemService(NotificationManager::class.java).notify(NOTIF_PROPOSITION, n)
        } catch (_: SecurityException) {
        }
    }

    internal fun notificationOuvert(c: Context): Notification {
        val minutes = ((jusqua - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)
        val fermer = PendingIntent.getService(c, 1, Intent(c, ServiceEchange::class.java).setAction(ServiceEchange.FERMER), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(c, "ouvert")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Ouvert à vos proches")
            .setContentText("Encore $minutes min · seuls vos proches reconnus vous voient")
            .setContentIntent(ouvrirSugu(c, "africa.samaos.action.SUGU_PROCHES"))
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Fermer", fermer).build())
            .build()
    }
}

/**
 * La fenêtre ouverte aux proches, au premier plan : la notification montre que le téléphone est visible d'eux,
 * et permet de fermer. Elle se ferme seule au bout de 10 minutes (après la fin d'un envoi en cours).
 */
class ServiceEchange : Service() {
    companion object {
        const val FERMER = "africa.samaos.sugu.FERMER"
    }

    private val h = Handler(Looper.getMainLooper())
    private val tic = object : Runnable {
        override fun run() {
            if (!Echange.ouvert() && !Echange.envoisEnCours()) {
                stopSelf()
                return
            }
            if (Echange.ouvert()) getSystemService(NotificationManager::class.java).notify(Echange.NOTIF_OUVERT, Echange.notificationOuvert(this@ServiceEchange))
            h.postDelayed(this, 15_000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        Echange.canaux(this)
        startForeground(Echange.NOTIF_OUVERT, Echange.notificationOuvert(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        if (i?.action == FERMER) {
            stopSelf()
            return START_NOT_STICKY
        }
        Echange.demarrer(this)
        h.removeCallbacks(tic)
        h.postDelayed(tic, 15_000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(tic)
        Echange.arreter(this)
        super.onDestroy()
    }
}
