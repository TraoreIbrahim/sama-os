package africa.samaos.reglages

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.LocalNuit
import africa.samaos.banco.PaysageEspace
import africa.samaos.banco.Polices
import africa.samaos.banco.palette
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Le SOS (maquettes l4-sos et l4-partage-position) : cinq appuis sur le bouton marche, cinq secondes pour
 * annuler, puis l'appel des secours et la position envoyée par SMS aux contacts d'urgence (sans data).
 * Android détecte le geste ; l'interface système de Sama (surcouche SystemUI) l'envoie ici.
 */
object MoteurSos {
    /** Gardé avec la fiche et les contacts d'urgence : lisible avant le premier déverrouillage. */
    private fun prefs(c: Context) = c.createDeviceProtectedStorageContext().getSharedPreferences("urgence", Context.MODE_PRIVATE)

    const val DUREE_PARTAGE = 2 * 3600_000L
    const val INTERVALLE = 15 * 60_000L

    fun gesteActif(c: Context) = Settings.Secure.getInt(c.contentResolver, "emergency_gesture_enabled", 1) == 1

    fun reglerGeste(c: Context, oui: Boolean) {
        try {
            Settings.Secure.putInt(c.contentResolver, "emergency_gesture_enabled", if (oui) 1 else 0)
        } catch (_: SecurityException) {
        }
    }

    /** Le numéro appelé pendant un SOS ; vide : n'appeler personne. */
    fun numero(c: Context): String = prefs(c).getString("sos_numero", "185").orEmpty()

    fun reglerNumero(c: Context, n: String) = prefs(c).edit().putString("sos_numero", n).apply()

    fun envoyerPosition(c: Context) = prefs(c).getBoolean("sos_position", true)

    fun reglerEnvoyerPosition(c: Context, oui: Boolean) = prefs(c).edit().putBoolean("sos_position", oui).apply()

    fun nomNumero(n: String) = NUMEROS_URGENCE.firstOrNull { it.numero == n }?.let { "${it.nom} (${it.numero})" } ?: n

    /** « Maman et Awa », « Maman, Awa et Kofi ». */
    fun noms(c: Context): String {
        val l = MoteurUrgence.contacts(c).map { it.nom.ifBlank { it.numero } }
        return if (l.size <= 1) l.joinToString() else l.dropLast(1).joinToString(", ") + " et " + l.last()
    }

    /** Ce que le SOS va faire, dit avant qu'il le fasse. */
    fun annonce(c: Context): String {
        val appel = numero(c).takeIf { it.isNotBlank() }?.let { "Appel ${if (it == "185") "du SAMU (185)" else "du ${nomNumero(it)}"}" }
        val position = if (envoyerPosition(c) && MoteurUrgence.contacts(c).isNotEmpty()) "position envoyée à ${noms(c)}" else null
        return listOfNotNull(appel, position).joinToString(" et ").replaceFirstChar { it.uppercase() }
            .ifBlank { "Rien n'est réglé : choisissez qui appeler dans Réglages › Urgence." }
    }

    // ——— Le partage en cours ———

    fun jusqua(c: Context): Long = prefs(c).getLong("partage_jusqua", 0L)

    fun enCours(c: Context) = System.currentTimeMillis() < jusqua(c)

    fun repeter(c: Context) = prefs(c).getBoolean("partage_repeter", true)

    fun reglerRepeter(c: Context, oui: Boolean) = prefs(c).edit().putBoolean("partage_repeter", oui).apply()

    fun commencer(c: Context) = prefs(c).edit().putLong("partage_jusqua", System.currentTimeMillis() + DUREE_PARTAGE).putBoolean("partage_repeter", true).apply()

    fun arreter(c: Context) = prefs(c).edit().putLong("partage_jusqua", 0L).apply()

    /** Le dernier SMS parti : l'heure et le texte. */
    fun dernier(c: Context): Pair<Long, String>? = try {
        JSONObject(prefs(c).getString("partage_dernier", null) ?: "").let { it.getLong("quand") to it.getString("texte") }
    } catch (_: Exception) {
        null
    }

    fun message(c: Context, l: Location?): String {
        val qui = MoteurUrgence.fiche(c)["nom"]?.trim()?.substringBefore(' ')?.ifBlank { null } ?: "Votre proche"
        if (l == null) return "$qui a lancé un SOS depuis ce numéro. Sa position n'est pas encore trouvée : elle suivra par SMS."
        val coord = String.format(
            Locale.FRENCH, "%.4f %s · %.4f %s",
            abs(l.latitude), if (l.latitude >= 0) "N" else "S", abs(l.longitude), if (l.longitude >= 0) "E" else "O",
        )
        val precision = if (l.hasAccuracy()) ", à ${l.accuracy.roundToInt()} m près" else ""
        val carte = String.format(Locale.US, "https://osm.org/?mlat=%.5f&mlon=%.5f", l.latitude, l.longitude)
        return "$qui a lancé un SOS. Position : $coord$precision. Carte : $carte"
    }

    /** Par SMS : ça passe sans data, et le message reste dans les Messages envoyés. */
    fun envoyer(c: Context, texte: String) {
        val sms = c.getSystemService(SmsManager::class.java) ?: return
        MoteurUrgence.contacts(c).forEach { k ->
            try {
                sms.sendMultipartTextMessage(k.numero, null, sms.divideMessage(texte), null, null)
            } catch (_: Exception) {
            }
        }
        prefs(c).edit().putString("partage_dernier", JSONObject().put("quand", System.currentTimeMillis()).put("texte", texte).toString()).apply()
    }

    /** La position : la dernière connue si elle a moins de 2 minutes, sinon une nouvelle (20 secondes au plus). */
    fun position(c: Context, rappel: (Location?) -> Unit) {
        val lm = c.getSystemService(LocationManager::class.java)
        val recente = try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)
                .mapNotNull { p -> lm.getLastKnownLocation(p) }
                .filter { System.currentTimeMillis() - it.time < 120_000 }
                .minByOrNull { if (it.hasAccuracy()) it.accuracy else 9999f }
        } catch (_: SecurityException) {
            null
        }
        if (recente != null) return rappel(recente)
        val main = Handler(Looper.getMainLooper())
        var fini = false
        fun une(l: Location?) {
            if (fini) return
            fini = true
            rappel(l)
        }
        main.postDelayed({ une(null) }, 20_000)
        listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
            try {
                if (lm.isProviderEnabled(p)) lm.getCurrentLocation(p, null, c.mainExecutor) { l -> if (l != null) une(l) }
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Le partage de position : un premier SMS tout de suite, puis toutes les 15 minutes pendant 2 heures.
 * Un service au premier plan, pour qu'Android ne l'arrête pas et que la position reste lisible.
 */
class PartagePosition : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var sansPosition = false
    private var trouvee = false

    private val tour = object : Runnable {
        override fun run() {
            if (!MoteurSos.enCours(this@PartagePosition)) return stopSelf()
            // « Renvoyer toutes les 15 minutes » coupé entre-temps : la position est déjà partie, on s'arrête.
            if (trouvee && !MoteurSos.repeter(this@PartagePosition)) return stopSelf()
            MoteurSos.position(this@PartagePosition) { l ->
                if (!MoteurSos.enCours(this@PartagePosition)) return@position
                MoteurSos.envoyer(this@PartagePosition, MoteurSos.message(this@PartagePosition, l))
                sansPosition = l == null
                if (l != null) trouvee = true
                notifier()
                // Sans position, on réessaie vite ; sinon toutes les 15 minutes, si la personne le veut.
                val suivant = if (sansPosition) 60_000L else MoteurSos.INTERVALLE
                if (sansPosition || MoteurSos.repeter(this@PartagePosition)) main.postDelayed(this, suivant) else stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ARRETER) {
            MoteurSos.arreter(this)
            stopSelf()
            return START_NOT_STICKY
        }
        notifier()
        if (intent?.action == COMMENCER) {
            main.removeCallbacks(tour)
            main.post(tour)
        } else if (intent?.action == REPRENDRE) {
            main.removeCallbacks(tour)
            main.postDelayed(tour, MoteurSos.INTERVALLE)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tour)
        super.onDestroy()
    }

    private fun notifier() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "SOS", NotificationManager.IMPORTANCE_HIGH))
        val ouvrir = PendingIntent.getActivity(this, 0, Intent(this, Sos::class.java).putExtra(Sos.PARTAGE, true), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, PartagePosition::class.java).setAction(ARRETER), PendingIntent.FLAG_IMMUTABLE)
        val heure = Instant.ofEpochMilli(MoteurSos.jusqua(this)).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("H:mm"))
        val n = Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Position partagée avec ${MoteurSos.noms(this)}")
            .setContentText(if (sansPosition) "Position pas encore trouvée · nouvel essai dans une minute" else "Par SMS · jusqu'à $heure")
            .setOngoing(true)
            .setContentIntent(ouvrir)
            .addAction(Notification.Action.Builder(null, "Arrêter le partage", stop).build())
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    companion object {
        const val CANAL = "sos"
        const val COMMENCER = "africa.samaos.reglages.SOS_COMMENCER"
        const val REPRENDRE = "africa.samaos.reglages.SOS_REPRENDRE"
        const val ARRETER = "africa.samaos.reglages.SOS_ARRETER"
    }
}

/** L'écran du SOS : il s'allume et passe devant l'écran verrouillé. */
class Sos : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val essai = intent.getBooleanExtra(ESSAI, false)
        val partage = intent.getBooleanExtra(PARTAGE, false) && MoteurSos.enCours(this)
        setContent {
            val nuit = androidx.compose.foundation.isSystemInDarkTheme()
            CompositionLocalProvider(LocalBanco provides palette(PaysageEspace.LAGUNE, nuit), LocalNuit provides nuit) {
                EcranSos(essai, partage) { finish() }
            }
        }
    }

    companion object {
        const val ESSAI = "essai"
        const val PARTAGE = "partage"
    }
}

private val ROUGE_SOS = Color(0xFF8E2A23)

@Composable
private fun EcranSos(essai: Boolean, partage: Boolean, fermer: () -> Unit) {
    val c = LocalContext.current
    var lance by remember { mutableStateOf(partage) }
    if (!lance) {
        CompteARebours(essai, annuler = fermer) {
            lance = true
            if (essai) return@CompteARebours
            val position = MoteurSos.envoyerPosition(c) && MoteurUrgence.contacts(c).isNotEmpty()
            if (position) {
                MoteurSos.commencer(c)
                c.startForegroundService(Intent(c, PartagePosition::class.java).setAction(PartagePosition.COMMENCER))
            }
            MoteurSos.numero(c).takeIf { it.isNotBlank() }?.let { MoteurUrgence.appeler(c, it) }
            if (!position) fermer()
        }
    } else {
        PagePartage(essai, fermer)
    }
}

/** Cinq secondes pour annuler (maquette l4-sos). Le téléphone vibre à chaque seconde. */
@Composable
private fun CompteARebours(essai: Boolean, annuler: () -> Unit, lancer: () -> Unit) {
    val c = LocalContext.current
    var reste by remember { mutableIntStateOf(5) }
    BackHandler(onBack = annuler)
    LaunchedEffect(Unit) {
        val vibreur = c.getSystemService(VibratorManager::class.java)?.defaultVibrator
        while (reste > 0) {
            try {
                vibreur?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
            }
            delay(1000)
            reste--
        }
        lancer()
    }
    Box(Modifier.fillMaxSize().background(ROUGE_SOS)) {
        Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 52.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(if (essai) "SOS · essai" else "SOS", style = TextStyle(fontFamily = Polices.monument, fontSize = 44.sp, lineHeight = 48.sp, color = Color.White))
            BasicText(
                if (essai) "Rien ne sera appelé ni envoyé. Pour de vrai : ${MoteurSos.annonce(c).replaceFirstChar { it.lowercase() }}" else MoteurSos.annonce(c),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = Color.White.copy(alpha = 0.9f)),
            )
        }
        Box(Modifier.align(Alignment.Center).padding(bottom = 80.dp).size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = 0.25f), style = Stroke(10.dp.toPx()))
                drawArc(Color.White, -90f, 360f * reste / 5f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            }
            BasicText("$reste", style = TextStyle(fontFamily = Polices.horloge, fontSize = 120.sp, color = Color.White))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(128.dp).clip(CircleShape).background(Color.White).clickable(onClickLabel = "Annuler le SOS", role = Role.Button, onClick = annuler),
                contentAlignment = Alignment.Center,
            ) { BasicText("Annuler", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ROUGE_SOS)) }
            BasicText(
                if (essai) "Essai lancé depuis Réglages › Urgence" else "Lancé par 5 appuis sur le bouton marche",
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center),
            )
        }
    }
}

/** La position envoyée (maquette l4-partage-position). */
@Composable
private fun PagePartage(essai: Boolean, fermer: () -> Unit) {
    val c = LocalContext.current
    val b = LocalBanco.current
    var v by remember { mutableIntStateOf(0) }
    // Le service envoie les SMS : on relit où il en est.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            v++
        }
    }
    val dernier = remember(v) { if (essai) null else MoteurSos.dernier(c) }
    val enCours = remember(v) { !essai && MoteurSos.enCours(c) }
    val repeter = remember(v) { MoteurSos.repeter(c) }
    val contacts = remember { MoteurUrgence.contacts(c) }
    fun heure(t: Long) = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("H:mm"))
    PageReglages(
        titre = when {
            essai -> "Voilà ce qui part"
            dernier != null -> "Position envoyée"
            else -> "Position en route"
        },
        sousTitre = if (essai) "Pendant un vrai SOS, par SMS, sans Internet" else "Par SMS, sans Internet",
        retour = fermer,
    ) {
        section(cle = "sms") {
            val texte = if (essai) MoteurSos.message(c, Location("essai").apply { latitude = 5.3576; longitude = -4.0213; accuracy = 15f }) else dernier?.second
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)).background(b.sol2).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(
                    when {
                        essai -> "SMS pour ${MoteurSos.noms(c).ifBlank { "vos contacts d'urgence" }} · exemple"
                        dernier != null -> "SMS envoyé à ${MoteurSos.noms(c)} · ${heure(dernier.first)}"
                        else -> "Recherche de la position…"
                    },
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = b.encre2),
                )
                if (texte != null) BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre))
            }
            Spacer(Modifier.height(16.dp))
        }
        section(cle = "actions") {
            if (enCours) {
                Ligne(
                    "Renvoyer toutes les 15 minutes", detail = "Jusqu'à ${heure(MoteurSos.jusqua(c))}", icone = Icones.ROTATION, fin = Fin.Inter(repeter),
                ) {
                    MoteurSos.reglerRepeter(c, !repeter)
                    if (!repeter) c.startForegroundService(Intent(c, PartagePosition::class.java).setAction(PartagePosition.REPRENDRE))
                    v++
                }
            }
            contacts.firstOrNull()?.let { k ->
                Ligne("Appeler ${k.nom.ifBlank { k.numero }}", icone = Icones.APPEL) { if (!essai) MoteurUrgence.appeler(c, k.numero) }
            }
            if (enCours) {
                Ligne("Arrêter le partage", icone = Icones.FERMER, danger = true, fin = Fin.Rien) {
                    c.startService(Intent(c, PartagePosition::class.java).setAction(PartagePosition.ARRETER))
                    v++
                }
            } else if (!essai && dernier != null) {
                Explication("Le partage est arrêté.")
            }
            Explication("Le SMS marche sans data. Le lien de la carte s'ouvre quand le destinataire a Internet.")
        }
    }
}
