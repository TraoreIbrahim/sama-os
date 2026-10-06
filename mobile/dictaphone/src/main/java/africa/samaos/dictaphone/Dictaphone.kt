package africa.samaos.dictaphone

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un enregistrement fini, dans Recordings/ du téléphone. */
data class Piste(val id: Long, val nom: String, val duree: Long, val date: Long, val taille: Long) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

private const val REPERE = "M6 4h12v17l-6-4l-6 4z"

fun taille(o: Long): String = when {
    o >= 1_000_000_000 -> String.format(Locale.FRENCH, "%.1f Go", o / 1e9)
    o >= 10_000_000 -> "${o / 1_000_000} Mo"
    o >= 1_000_000 -> String.format(Locale.FRENCH, "%.1f Mo", o / 1e6)
    o >= 1_000 -> "${o / 1_000} Ko"
    else -> "$o octets"
}

fun chrono(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** « 48 min », « 3 min », « 40 s ». */
fun longueur(ms: Long): String = when {
    ms >= 3_600_000 -> "${ms / 3_600_000} h ${"%02d".format(ms / 60_000 % 60)}"
    ms >= 60_000 -> "${(ms + 30_000) / 60_000} min"
    else -> "${(ms / 1000).coerceAtLeast(1)} s"
}

fun jour(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val auj = LocalDate.now()
    return when {
        d == auj -> "aujourd'hui"
        d == auj.minusDays(1) -> "hier"
        d.isAfter(auj.minusDays(7)) -> d.format(DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH))
        else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
    }
}

object Pistes {
    fun liste(c: Context): List<Piste> {
        val l = mutableListOf<Piste>()
        try {
            c.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.SIZE),
                "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE 'Recordings/%'", null, "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cur ->
                while (cur.moveToNext()) l += Piste(cur.getLong(0), cur.getString(1).orEmpty().substringBeforeLast('.'), cur.getLong(2), cur.getLong(3) * 1000, cur.getLong(4))
            }
        } catch (_: Exception) {
        }
        return l
    }

    fun renommer(c: Context, p: Piste, nom: String) = try {
        c.contentResolver.update(p.uri, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$nom.m4a")
            put(MediaStore.Audio.Media.TITLE, nom)
        }, null, null) > 0
    } catch (_: Exception) {
        false
    }

    fun jeter(c: Context, p: Piste, oui: Boolean = true) = try {
        c.contentResolver.update(p.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, if (oui) 1 else 0) }, null, null) > 0
    } catch (_: Exception) {
        false
    }
}

/** Le Dictaphone de Sama (maquette l2-dictaphone). */
class Dictaphone : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.DictaphoneNuit else Identites.Dictaphone
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            CompositionLocalProvider(LocalIdentite provides id) { Ecran() }
        }
    }
}

private fun nomParDefaut(): String =
    "Enregistrement du " + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM 'à' HH'h'mm", Locale.FRENCH))

@Composable
private fun Ecran() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    val enCours by Dicta.etat.collectAsState()
    val fini by Dicta.fini.collectAsState()
    var nom by remember { mutableStateOf(nomParDefaut()) }
    var renommer by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val pistes by produceState(emptyList<Piste>(), version, fini) { value = withContext(Dispatchers.IO) { Pistes.liste(c) } }
    var maintenant by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(enCours != null) {
        while (Dicta.etat.value != null) {
            maintenant = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    LaunchedEffect(fini) {
        fini?.let {
            message = "« $it » est enregistré"
            nom = nomParDefaut()
            Dicta.fini.value = null
            delay(4000)
            message = null
        }
    }
    val demander = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) Dicta.commander(c, Dicta.DEMARRER, nom.trim().ifBlank { nomParDefaut() })
        else message = "Sans accès au micro, le Dictaphone ne peut pas enregistrer"
    }
    fun enregistrer() {
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Dicta.commander(c, Dicta.DEMARRER, nom.trim().ifBlank { nomParDefaut() })
        } else {
            demander.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
    }
    val e = enCours
    Box(Modifier.fillMaxSize()) {
        EcranAppli {
            Tete("Dictaphone")
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = e == null, onClickLabel = "Renommer") { renommer = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText(e?.nom ?: nom, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = a.encre))
                    if (e == null) IconeTrait(Icones.CRAYON, 18.dp, a.encre2)
                }
                val duree = e?.duree(maintenant) ?: 0
                BasicText(chrono(duree), style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(250), fontSize = 80.sp, lineHeight = 86.sp, color = a.encre))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e != null && !e.enPause) Box(Modifier.size(8.dp).clip(CircleShape).background(a.accent))
                    BasicText(
                        when {
                            e == null -> "Prêt · environ ${taille(Dicta.DEBIT / 8L * 60)} par minute"
                            e.enPause -> "En pause · ${taille(duree * Dicta.DEBIT / 8000)}"
                            else -> "Enregistrement · ${taille(duree * Dicta.DEBIT / 8000)}"
                        },
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2),
                    )
                }
            }
            Onde(e, maintenant)
            Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Rond("Repère", REPERE, actif = e != null && !e.enPause) { Dicta.commander(c, Dicta.REPERE) }
                Box(
                    Modifier.size(84.dp).clip(CircleShape).border(4.dp, a.accent, CircleShape)
                        .clickable(onClickLabel = if (e == null) "Enregistrer" else "Arrêter et enregistrer", role = Role.Button) {
                            if (e == null) enregistrer() else Dicta.commander(c, Dicta.ARRETER)
                        }
                        .semantics { contentDescription = if (e == null) "Enregistrer" else "Arrêter et enregistrer" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (e == null) Box(Modifier.size(62.dp).clip(CircleShape).background(a.accent))
                    else Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(a.accent))
                }
                Rond(if (e?.enPause == true) "Reprendre" else "Pause", if (e?.enPause == true) Icones.LECTURE else Icones.PAUSE, actif = e != null, libelle = false) {
                    Dicta.commander(c, if (e?.enPause == true) Dicta.REPRENDRE else Dicta.PAUSE)
                }
            }
            Spacer(Modifier.height(18.dp))
            Rub("Enregistrements")
            Liste(pistes, { version++ }) { message = it }
        }
        message?.let {
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(a.encre).padding(horizontal = 18.dp, vertical = 14.dp),
            ) { BasicText(it, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.fond)) }
        }
    }
    if (renommer) Renommer(nom, fermer = { renommer = false }) {
        nom = it
        renommer = false
    }
}

@Composable
private fun Rond(nom: String, icone: String, actif: Boolean, libelle: Boolean = true, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val encre = if (actif) a.encre2 else a.encre2.copy(alpha = 0.4f)
    Column(
        Modifier.size(64.dp).clip(CircleShape).background(a.champ).clickable(enabled = actif, onClickLabel = nom, role = Role.Button, onClick = onClick)
            .semantics { if (!libelle) contentDescription = nom },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        IconeTrait(icone, if (libelle) 20.dp else 24.dp, encre)
        if (libelle) BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = encre))
    }
}

/** L'onde : le niveau du son, le plus récent au milieu ; les repères marqués au-dessus. */
@Composable
private fun Onde(e: EnCours?, maintenant: Long) {
    val a = LocalIdentite.current
    Canvas(Modifier.fillMaxWidth().padding(top = 22.dp).height(96.dp).semantics { contentDescription = "Niveau du son" }) {
        val milieu = size.width / 2
        val pas = 6.dp.toPx()
        val large = 3.dp.toPx()
        val haut = size.height - 8.dp.toPx()
        val niveaux = e?.niveaux.orEmpty()
        niveaux.asReversed().forEachIndexed { i, n ->
            val x = milieu - i * pas - large
            if (x < -large) return@forEachIndexed
            val h = (6.dp.toPx() + n * (haut - 6.dp.toPx())).coerceAtMost(haut)
            drawRoundRect(a.accent, Offset(x, (size.height - h) / 2), Size(large, h), CornerRadius(large / 2))
        }
        // Les points d'attente, à droite : ce qui reste à enregistrer.
        var x = milieu + pas
        while (x < size.width) {
            drawCircle(a.trait, 1.5.dp.toPx(), Offset(x, size.height / 2))
            x += pas
        }
        drawRect(a.encre, Offset(milieu - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        // Un repère : un petit trait au-dessus de l'onde, à sa place dans le temps (80 ms par barre).
        e?.reperes?.forEach { r ->
            val recul = (e.duree(maintenant) - r) / 80f * pas
            val rx = milieu - recul
            if (rx > 0) drawRect(a.encre2, Offset(rx - 1.dp.toPx(), 0f), Size(2.dp.toPx(), 10.dp.toPx()))
        }
    }
}

@Composable
private fun Liste(pistes: List<Piste>, changer: () -> Unit, dire: (String) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var lecteur by remember { mutableStateOf<MediaPlayer?>(null) }
    var joue by remember { mutableStateOf<Long?>(null) }
    var enLecture by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var options by remember { mutableStateOf<Piste?>(null) }
    var renommer by remember { mutableStateOf<Piste?>(null) }
    DisposableEffect(Unit) { onDispose { lecteur?.release() } }
    LaunchedEffect(joue) {
        while (joue != null) {
            position = (lecteur?.currentPosition ?: 0).toLong()
            delay(200)
        }
    }
    fun ecouter(p: Piste) {
        if (joue == p.id) {
            lecteur?.let { if (it.isPlaying) it.pause() else it.start() }
            enLecture = lecteur?.isPlaying == true
            position = (lecteur?.currentPosition ?: 0).toLong()
            return
        }
        lecteur?.release()
        lecteur = MediaPlayer().apply {
            setDataSource(c, p.uri)
            setOnCompletionListener {
                joue = null
                enLecture = false
            }
            prepare()
            start()
        }
        joue = p.id
        enLecture = true
    }
    if (pistes.isEmpty()) {
        BasicText("Vos enregistrements viendront ici. Ils restent sur le téléphone.", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(pistes, key = { it.id }) { p ->
            val ici = joue == p.id
            val reperes = remember(p.id) { Dicta.reperes(c, p.id) }
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BoutonAppli(if (ici && enLecture) Icones.PAUSE else Icones.LECTURE, if (ici && enLecture) "Pause" else "Écouter", style = 'v') { ecouter(p) }
                    Column(Modifier.weight(1f)) {
                        BasicText(p.nom, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
                        BasicText(
                            listOfNotNull(longueur(p.duree), jour(p.date), taille(p.taille), if (reperes.isNotEmpty()) "${reperes.size} repère${if (reperes.size > 1) "s" else ""}" else null).joinToString(" · "),
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2),
                        )
                    }
                    BoutonAppli(Icones.OPTIONS, "Options de l'enregistrement") { options = p }
                }
                if (ici) {
                    // La barre de lecture : un appui y place la lecture ; les repères pour sauter au bon moment.
                    Column(Modifier.padding(start = 72.dp, end = 20.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Canvas(
                            Modifier.fillMaxWidth().height(20.dp).pointerInput(p.id) {
                                detectTapGestures { o -> lecteur?.seekTo((o.x / size.width * p.duree).toInt()); position = (lecteur?.currentPosition ?: 0).toLong() }
                            },
                        ) {
                            val y = size.height / 2
                            drawLine(a.trait, Offset(0f, y), Offset(size.width, y), 4f)
                            val px = size.width * (position.toFloat() / p.duree.coerceAtLeast(1))
                            drawLine(a.accent, Offset(0f, y), Offset(px, y), 4f)
                            reperes.forEach { r -> val rx = size.width * r / p.duree.coerceAtLeast(1); drawRect(a.encre2, Offset(rx - 1f, 0f), Size(2f, 6.dp.toPx())) }
                            drawCircle(a.accent, 7.dp.toPx(), Offset(px, y))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText("${chrono(position)} / ${chrono(p.duree)}", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                            Spacer(Modifier.weight(1f))
                            reperes.take(4).forEach { r ->
                                Row(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(a.voile).clickable(onClickLabel = "Aller au repère ${chrono(r)}") { lecteur?.seekTo(r.toInt()); position = r }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    IconeTrait(REPERE, 12.dp, a.accentTexte)
                                    BasicText(chrono(r), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = a.accentTexte))
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
    }
    options?.let { p ->
        FeuilleAppli(fermer = { options = null }, titre = p.nom) {
            ActionFeuille(Icones.CRAYON, "Renommer") {
                options = null
                renommer = p
            }
            ActionFeuille(Icones.PARTAGER, "Partager") {
                options = null
                c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, p.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Partager « ${p.nom} »"))
            }
            ActionFeuille(Icones.CORBEILLE, "Mettre à la corbeille", danger = true, second = "Vous pourrez le remettre pendant 30 jours, depuis Fichiers") {
                options = null
                if (joue == p.id) {
                    lecteur?.release()
                    lecteur = null
                    joue = null
                }
                portee.launch {
                    val ok = withContext(Dispatchers.IO) { Pistes.jeter(c, p) }
                    changer()
                    dire(if (ok) "« ${p.nom} » est dans la corbeille" else "Impossible de le mettre à la corbeille")
                }
            }
        }
    }
    renommer?.let { p ->
        Renommer(p.nom, fermer = { renommer = null }) { n ->
            renommer = null
            portee.launch {
                withContext(Dispatchers.IO) { Pistes.renommer(c, p, n) }
                changer()
            }
        }
    }
}

@Composable
private fun Renommer(actuel: String, fermer: () -> Unit, valider: (String) -> Unit) {
    val a = LocalIdentite.current
    var champ by remember { mutableStateOf(TextFieldValue(actuel, TextRange(0, actuel.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DialogueAppli("Nom de l'enregistrement", null, fermer = fermer, contenu = {
        BasicTextField(
            champ, { champ = it }, singleLine = true,
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(a.champ).padding(14.dp).focusRequester(focus).semantics { contentDescription = "Nom" },
        )
    }) {
        BoutonTexteAppli("Annuler", style = 't', onClick = fermer)
        BoutonTexteAppli("Garder", actif = champ.text.isNotBlank() && !champ.text.contains('/')) { valider(champ.text.trim()) }
    }
}
