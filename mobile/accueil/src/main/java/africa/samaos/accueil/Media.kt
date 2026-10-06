package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Ce qui joue en ce moment, dans n'importe quelle appli : le lecteur du Pouls (maquette l1-media). */
class Lecture(
    val controleur: MediaController,
    val titre: String,
    val sousTitre: String,
    val pochette: ImageBitmap?,
    val duree: Long,
    val etat: PlaybackState?,
) {
    val paquet: String get() = controleur.packageName
    val enCours: Boolean get() = etat?.state == PlaybackState.STATE_PLAYING || etat?.state == PlaybackState.STATE_BUFFERING
    private val actions: Long get() = etat?.actions ?: 0L
    val peutChercher: Boolean get() = duree > 0 && actions and PlaybackState.ACTION_SEEK_TO != 0L
    val peutSauter: Boolean get() = actions and (PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L

    /** La position du moment, recalculée depuis le dernier état donné par l'appli. */
    fun position(): Long {
        val e = etat ?: return 0L
        var p = e.position
        if (e.state == PlaybackState.STATE_PLAYING) p += ((SystemClock.elapsedRealtime() - e.lastPositionUpdateTime) * e.playbackSpeed).toLong()
        return if (duree > 0) p.coerceIn(0L, duree) else p.coerceAtLeast(0L)
    }

    fun lireOuPause() = if (enCours) controleur.transportControls.pause() else controleur.transportControls.play()

    fun chercher(position: Long) = controleur.transportControls.seekTo(position.coerceIn(0L, duree))

    /** Reculer ou avancer : de 15 secondes quand l'appli le permet (leçons, émissions), sinon d'un morceau. */
    fun decaler(sens: Int) {
        val c = controleur.transportControls
        when {
            peutChercher -> chercher(position() + sens * SAUT)
            sens < 0 && actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L -> c.skipToPrevious()
            sens > 0 && actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L -> c.skipToNext()
            sens < 0 -> c.rewind()
            else -> c.fastForward()
        }
    }

    /** Ouvre l'appli qui joue, là où elle montre sa lecture. */
    fun ouvrir(contexte: Context) {
        val activite = controleur.sessionActivity
        try {
            if (activite != null) {
                if (Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    activite.send(
                        ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            .toBundle(),
                    )
                } else {
                    activite.send()
                }
                return
            }
        } catch (_: PendingIntent.CanceledException) {
            // L'appli a retiré son écran de lecture : on l'ouvre simplement.
        }
        contexte.packageManager.getLaunchIntentForPackage(paquet)?.let { Systeme.ouvrir(contexte, it) }
    }

    companion object {
        const val SAUT = 15_000L
    }
}

/**
 * Suit les sessions média du téléphone : celle qui joue, sinon la dernière mise en pause.
 * Sama système a le droit MEDIA_CONTENT_CONTROL ; sinon on passe par l'accès aux notifications du Pouls.
 */
private class SuiviMedia(private val contexte: Context, private val publier: (Lecture?) -> Unit) {
    private val msm = contexte.getSystemService(MediaSessionManager::class.java)
    private val fil = Handler(Looper.getMainLooper())
    private var suivi: MediaController? = null

    private val rappel = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = maj()
        override fun onMetadataChanged(metadata: MediaMetadata?) = maj()
        override fun onSessionDestroyed() = choisir(sessions())
    }

    private val ecoute = MediaSessionManager.OnActiveSessionsChangedListener { choisir(it.orEmpty()) }

    fun demarrer() {
        try {
            msm.addOnActiveSessionsChangedListener(ecoute, null, fil)
        } catch (_: SecurityException) {
            try {
                msm.addOnActiveSessionsChangedListener(ecoute, ComponentName(contexte, Ecouteur::class.java), fil)
            } catch (_: SecurityException) {
                // Ni droit système ni accès aux notifications : pas de lecteur dans le Pouls.
            }
        }
        choisir(sessions())
    }

    fun arreter() {
        msm.removeOnActiveSessionsChangedListener(ecoute)
        suivi?.unregisterCallback(rappel)
        suivi = null
    }

    private fun sessions(): List<MediaController> = try {
        msm.getActiveSessions(null)
    } catch (_: SecurityException) {
        try {
            msm.getActiveSessions(ComponentName(contexte, Ecouteur::class.java))
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun choisir(liste: List<MediaController>) {
        val montrables = liste.filter { c ->
            val etat = c.playbackState?.state
            !c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() &&
                etat != null && etat != PlaybackState.STATE_NONE && etat != PlaybackState.STATE_STOPPED && etat != PlaybackState.STATE_ERROR
        }
        val choisi = montrables.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: montrables.firstOrNull()
        if (choisi?.sessionToken != suivi?.sessionToken) {
            suivi?.unregisterCallback(rappel)
            suivi = choisi
            choisi?.registerCallback(rappel, fil)
        }
        maj()
    }

    private fun maj() {
        val c = suivi
        if (c == null) {
            publier(null)
            return
        }
        val etat = c.playbackState
        if (etat == null || etat.state == PlaybackState.STATE_STOPPED || etat.state == PlaybackState.STATE_NONE) {
            // Cette lecture est finie : on regarde s'il en reste une autre.
            suivi?.unregisterCallback(rappel)
            suivi = null
            choisir(sessions().filter { it.sessionToken != c.sessionToken })
            return
        }
        val m = c.metadata
        val pm = contexte.packageManager
        val appli = try {
            pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            c.packageName
        }
        val artiste = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val image: Bitmap? = m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: try {
                (pm.getApplicationIcon(c.packageName) as? BitmapDrawable)?.bitmap
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        publier(
            Lecture(
                controleur = c,
                titre = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                sousTitre = listOfNotNull(appli, artiste?.takeIf { it.isNotBlank() }).joinToString(" · "),
                pochette = image?.asImageBitmap(),
                duree = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                etat = etat,
            ),
        )
    }
}

@Composable
fun rememberLecture(): Lecture? {
    val contexte = LocalContext.current
    var lecture by remember { mutableStateOf<Lecture?>(null) }
    DisposableEffect(Unit) {
        val suivi = SuiviMedia(contexte) { lecture = it }
        suivi.demarrer()
        onDispose { suivi.arreter() }
    }
    return lecture
}

/** La carte du lecteur, en tête du Pouls : pochette, titre, avancement, sortie du son et commandes. */
@Composable
fun CarteLecture(lecture: Lecture, sortie: String, choisirSortie: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    // L'avancement suit la lecture sans attendre que l'appli redonne sa position.
    var maintenant by remember { mutableLongStateOf(lecture.position()) }
    LaunchedEffect(lecture) {
        while (true) {
            maintenant = lecture.position()
            if (!lecture.enCours) break
            delay(500)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(b.sol2)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = "Ouvrir l'appli", role = Role.Button) { lecture.ouvrir(contexte) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(b.sol), contentAlignment = Alignment.Center) {
                val p = lecture.pochette
                if (p != null) {
                    Image(p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
                } else {
                    IconeTrait(Icones.MUSIQUE, 24.dp, b.encre)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    lecture.titre, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre),
                )
                BasicText(
                    lecture.sousTitre, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
                )
            }
        }
        if (lecture.duree > 0) Avancement(lecture, maintenant) { maintenant = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PuceSortie(sortie, choisirSortie)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val saut = lecture.peutChercher || !lecture.peutSauter
                BoutonLecture(
                    if (saut) Icones.RECULER else Icones.PRECEDENT,
                    if (saut) "Reculer de 15 secondes" else "Morceau précédent",
                ) { lecture.decaler(-1) }
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(b.laterite)
                        .clickable(onClickLabel = if (lecture.enCours) "Pause" else "Lecture", role = Role.Button) { lecture.lireOuPause() }
                        .semantics { contentDescription = if (lecture.enCours) "Pause" else "Lecture" },
                    contentAlignment = Alignment.Center,
                ) {
                    IconeTrait(if (lecture.enCours) Icones.PAUSE else Icones.LECTURE, 28.dp, b.surLaterite, epaisseur = 2.2f)
                }
                BoutonLecture(
                    if (saut) Icones.AVANCER_10 else Icones.SUIVANT,
                    if (saut) "Avancer de 15 secondes" else "Morceau suivant",
                ) { lecture.decaler(1) }
            }
        }
    }
}

/** La barre d'avancement : on y touche ou on y glisse le doigt pour aller ailleurs dans la lecture. */
@Composable
private fun Avancement(lecture: Lecture, position: Long, changer: (Long) -> Unit) {
    val b = LocalBanco.current
    var largeur by remember { mutableFloatStateOf(1f) }
    val part = (position.toFloat() / lecture.duree).coerceIn(0f, 1f)
    fun aller(x: Float) {
        val p = (x / largeur * lecture.duree).toLong().coerceIn(0L, lecture.duree)
        changer(p)
        lecture.chercher(p)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { largeur = it.width.toFloat() }
                .then(
                    if (lecture.peutChercher) {
                        Modifier
                            .pointerInput(lecture) { detectTapGestures { aller(it.x) } }
                            .pointerInput(lecture) { detectHorizontalDragGestures { change, _ -> aller(change.position.x) } }
                    } else {
                        Modifier
                    },
                )
                .semantics {
                    contentDescription = "Avancement"
                    stateDescription = "${duree(position)} sur ${duree(lecture.duree)}"
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(b.sol)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(part).clip(RoundedCornerShape(3.dp)).background(b.laterite))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 0.dp)) {
            val legende = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = b.encre2)
            BasicText(duree(position), style = legende)
            Spacer(Modifier.weight(1f))
            BasicText(duree(lecture.duree), style = legende)
        }
    }
}

@Composable
private fun BoutonLecture(icone: String, description: String, onClick: () -> Unit) {
    val b = LocalBanco.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        IconeTrait(icone, 24.dp, b.encre)
    }
}

/** La puce de la sortie du son : cerclée de latérite, elle ouvre « Écouter sur ». */
@Composable
private fun PuceSortie(nom: String, onClick: () -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(b.sol)
            .border(2.dp, b.laterite, RoundedCornerShape(50))
            .clickable(onClickLabel = "Choisir où écouter", role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTrait(if (nom == CE_TELEPHONE) Icones.APPAREIL else Icones.ECOUTEURS, 18.dp, b.encre)
        Spacer(Modifier.width(6.dp))
        BasicText(nom, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = b.encre))
    }
}

/** « 12:40 », « 1:02:05 ». */
private fun duree(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// ——— Écouter sur ———

private const val CE_TELEPHONE = "Ce téléphone"

/** Un endroit où envoyer le son : le haut-parleur, des écouteurs, une enceinte. */
class Sortie(val id: String, val nom: String, val detail: String?, val icone: String, val choisie: Boolean, internal val route: MediaRoute2Info?)

/**
 * Les sorties du son pour l'appli qui joue. Sama système voit et change la sortie de n'importe quelle appli,
 * comme le sélecteur d'Android (MediaRouter2 en mandataire, droit MEDIA_CONTENT_CONTROL) ; sinon, on montre
 * seulement où le son part.
 */
class Sorties(private val contexte: Context, paquet: String) {
    private val routeur: MediaRouter2? = if (Build.VERSION.SDK_INT >= 34) {
        try {
            MediaRouter2::class.java.getMethod("getInstance", Context::class.java, String::class.java)
                .invoke(null, contexte, paquet) as MediaRouter2
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }

    var liste by mutableStateOf(lire())
        private set

    private val rappelRoutes = if (Build.VERSION.SDK_INT >= 34) {
        object : MediaRouter2.RouteCallback() {
            override fun onRoutesUpdated(routes: List<MediaRoute2Info>) {
                liste = lire()
            }
        }
    } else {
        null
    }

    private val rappelControleur = if (Build.VERSION.SDK_INT >= 34) {
        object : MediaRouter2.ControllerCallback() {
            override fun onControllerUpdated(controller: MediaRouter2.RoutingController) {
                liste = lire()
            }
        }
    } else {
        null
    }

    private var balayage: Any? = null

    fun demarrer() {
        val r = routeur ?: return
        if (Build.VERSION.SDK_INT < 34) return
        try {
            r.registerRouteCallback(contexte.mainExecutor, rappelRoutes!!, RouteDiscoveryPreference.Builder(emptyList(), false).build())
            r.registerControllerCallback(contexte.mainExecutor, rappelControleur!!)
            balayage = if (Build.VERSION.SDK_INT >= 35) {
                r.requestScan(MediaRouter2.ScanRequest.Builder().build())
            } else {
                r.javaClass.getMethod("startScan").invoke(r)
                null
            }
        } catch (_: Exception) {
            // Le système refuse le mandat : on garde la sortie actuelle seulement.
        }
        liste = lire()
    }

    fun arreter() {
        val r = routeur ?: return
        if (Build.VERSION.SDK_INT < 34) return
        try {
            if (Build.VERSION.SDK_INT >= 35) {
                (balayage as? MediaRouter2.ScanToken)?.let { r.cancelScanRequest(it) }
            } else {
                r.javaClass.getMethod("stopScan").invoke(r)
            }
            r.unregisterRouteCallback(rappelRoutes!!)
            r.unregisterControllerCallback(rappelControleur!!)
        } catch (_: Exception) {
        }
    }

    fun choisir(s: Sortie) {
        val r = routeur
        val route = s.route
        if (r != null && route != null && Build.VERSION.SDK_INT >= 34) {
            try {
                r.transferTo(route)
                return
            } catch (_: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT >= 34) MediaRouter2.getInstance(contexte).showSystemOutputSwitcher()
    }

    private fun lire(): List<Sortie> {
        val r = routeur
        if (r != null && Build.VERSION.SDK_INT >= 34) {
            try {
                val choisies = r.systemController.selectedRoutes.map { it.id }.toSet() +
                    r.controllers.filter { !it.isReleased }.flatMap { c -> c.selectedRoutes.map { it.id } }
                val routes = (r.systemController.selectedRoutes + r.routes).distinctBy { it.id }
                    .filter { it.isSystemRoute || it.id in choisies || it.connectionState != MediaRoute2Info.CONNECTION_STATE_DISCONNECTED || it.type != MediaRoute2Info.TYPE_UNKNOWN }
                if (routes.isNotEmpty()) {
                    return routes.map { versSortie(it, it.id in choisies) }
                        .sortedWith(compareBy({ it.nom != CE_TELEPHONE }, { !it.choisie }))
                }
            } catch (_: Exception) {
            }
        }
        return listOf(sortieActuelle())
    }

    private fun versSortie(route: MediaRoute2Info, choisie: Boolean): Sortie {
        val type = if (Build.VERSION.SDK_INT >= 34) route.type else MediaRoute2Info.TYPE_UNKNOWN
        val telephone = type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER
        val ecouteurs = type in setOf(
            MediaRoute2Info.TYPE_WIRED_HEADSET, MediaRoute2Info.TYPE_WIRED_HEADPHONES, MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
            MediaRoute2Info.TYPE_BLE_HEADSET, MediaRoute2Info.TYPE_HEARING_AID, MediaRoute2Info.TYPE_USB_HEADSET,
        )
        return Sortie(
            id = route.id,
            nom = if (telephone) CE_TELEPHONE else route.name.toString(),
            detail = when {
                telephone -> null
                choisie -> "Le son part ici"
                route.connectionState == MediaRoute2Info.CONNECTION_STATE_CONNECTED -> "Connecté"
                else -> "À portée"
            },
            icone = when {
                telephone -> Icones.APPAREIL
                ecouteurs -> Icones.ECOUTEURS
                else -> Icones.ENCEINTE
            },
            choisie = choisie,
            route = route,
        )
    }

    /** Sans le mandat : où le son de la musique part, d'après le système audio. */
    private fun sortieActuelle(): Sortie {
        val am = contexte.getSystemService(AudioManager::class.java)
        val appareil = if (Build.VERSION.SDK_INT >= 33) {
            am.getAudioDevicesForAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).firstOrNull()
        } else {
            null
        }
        val ecouteurs = appareil?.type in setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID, AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        return if (appareil == null || appareil.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            Sortie("telephone", CE_TELEPHONE, null, Icones.APPAREIL, true, null)
        } else {
            Sortie("actuelle", appareil.productName.toString(), "Le son part ici", if (ecouteurs) Icones.ECOUTEURS else Icones.ENCEINTE, true, null)
        }
    }

    /** Le nom court de la puce : « Ce téléphone », « Écouteurs » ou le nom de l'enceinte. */
    val nomCourt: String
        get() {
            val s = liste.firstOrNull { it.choisie } ?: return CE_TELEPHONE
            return if (s.icone == Icones.ECOUTEURS) "Écouteurs" else s.nom
        }
}

@Composable
fun rememberSorties(paquet: String?): Sorties? {
    val contexte = LocalContext.current
    val sorties = remember(paquet) { paquet?.let { Sorties(contexte, it) } }
    DisposableEffect(sorties) {
        sorties?.demarrer()
        onDispose { sorties?.arreter() }
    }
    return sorties
}

/** « Écouter sur » : un sol qui monte du bas, sous le Pouls ; la sortie choisie porte la pastille. */
@Composable
fun FeuilleSorties(sorties: Sorties, fermer: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    Column(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }) {
        Crete()
        Column(
            Modifier
                .fillMaxWidth()
                .background(b.sol)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                "Écouter sur",
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = b.encre2),
            )
            sorties.liste.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(if (s.choisie) Modifier.background(b.sol2) else Modifier)
                        .clickable(onClickLabel = s.nom, role = Role.RadioButton) {
                            if (!s.choisie) sorties.choisir(s)
                            fermer()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconeTrait(s.icone, 24.dp, b.encre)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(s.nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre))
                        s.detail?.let {
                            BasicText(it, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2))
                        }
                    }
                    Pastille(s.choisie)
                }
            }
            if (sorties.liste.size < 2) {
                Box(Modifier.padding(horizontal = 12.dp)) {
                    Note("Branchez des écouteurs ou connectez un appareil Bluetooth pour écouter ailleurs.")
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) {
                            fermer()
                            Systeme.ouvrir(contexte, Settings.ACTION_BLUETOOTH_SETTINGS)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconeTrait(Icones.PLUS, 24.dp, b.lateriteTexte)
                    BasicText(
                        "Connecter un appareil Bluetooth",
                        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, color = b.lateriteTexte),
                    )
                }
            }
        }
    }
}
