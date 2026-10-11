package africa.samaos.agenda

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.BlocAppli
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.Rond
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.FiletBloc
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.NavFlottante
import africa.samaos.banco.appli.PageDoigt
import africa.samaos.banco.appli.Sens
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as StyleDate
import java.time.temporal.ChronoUnit
import java.util.Locale

class Evenement(val id: Long, val titre: String, val debut: Long, val fin: Long, val journee: Boolean, val lieu: String?, val notes: String?, val regle: String?, val couleur: Int? = null)

/** Les agendas d'Android : Sama garde ses événements dans un agenda sur le téléphone, dans cet Espace. */
object Agendas {
    private const val COMPTE = "Sama"

    /** L'agenda du téléphone, créé la première fois comme le font les applis d'agenda (agenda local). */
    fun agenda(c: Context): Long? {
        try {
            c.contentResolver.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID), "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?", arrayOf(COMPTE, CalendarContract.ACCOUNT_TYPE_LOCAL), null)
                ?.use { if (it.moveToFirst()) return it.getLong(0) }
            val uri = Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, COMPTE)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
            val v = ContentValues().apply {
                put(Calendars.ACCOUNT_NAME, COMPTE)
                put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(Calendars.NAME, "Sama")
                put(Calendars.CALENDAR_DISPLAY_NAME, "Agenda de ce téléphone")
                put(Calendars.CALENDAR_COLOR, 0xFF3D5A99.toInt())
                put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                put(Calendars.OWNER_ACCOUNT, COMPTE)
                put(Calendars.VISIBLE, 1)
                put(Calendars.SYNC_EVENTS, 1)
                put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
            }
            return c.contentResolver.insert(uri, v)?.let { ContentUris.parseId(it) }
        } catch (_: SecurityException) {
            return null
        }
    }

    /** Les événements entre deux moments (les répétitions comprises). */
    fun entre(c: Context, debut: Long, fin: Long): List<Evenement> {
        val l = mutableListOf<Evenement>()
        try {
            val uri = Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, debut); ContentUris.appendId(it, fin) }.build()
            c.contentResolver.query(
                uri,
                arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.EVENT_LOCATION, Instances.DESCRIPTION, Instances.RRULE, Instances.EVENT_COLOR),
                null, null, "${Instances.BEGIN} ASC",
            )?.use { cur ->
                while (cur.moveToNext()) {
                    l += Evenement(cur.getLong(0), cur.getString(1).orEmpty(), cur.getLong(2), cur.getLong(3), cur.getInt(4) == 1, cur.getString(5), cur.getString(6), cur.getString(7), if (cur.isNull(8)) null else cur.getInt(8))
                }
            }
        } catch (_: SecurityException) {
        }
        return l
    }

    fun evenement(c: Context, id: Long): Evenement? = try {
        c.contentResolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, id),
            arrayOf(Events._ID, Events.TITLE, Events.DTSTART, Events.DTEND, Events.ALL_DAY, Events.EVENT_LOCATION, Events.DESCRIPTION, Events.RRULE, Events.EVENT_COLOR, Events.DURATION),
            null, null, null,
        )?.use { cur ->
            if (cur.moveToFirst()) {
                // Un évènement qui se répète n'a pas de fin, mais une durée (« P3600S »).
                val debut = cur.getLong(2)
                val fin = if (cur.isNull(3)) debut + (Regex("P(\\d+)S").find(cur.getString(9).orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: 3600) * 1000 else cur.getLong(3)
                Evenement(cur.getLong(0), cur.getString(1).orEmpty(), debut, fin, cur.getInt(4) == 1, cur.getString(5), cur.getString(6), cur.getString(7), if (cur.isNull(8)) null else cur.getInt(8))
            } else null
        }
    } catch (_: Exception) {
        null
    }

    fun rappel(c: Context, id: Long): Int? = try {
        c.contentResolver.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()), null)
            ?.use { if (it.moveToFirst()) it.getInt(0) else null }
    } catch (_: Exception) {
        null
    }

    fun garder(c: Context, id: Long?, titre: String, debut: Long, fin: Long, journee: Boolean, lieu: String, notes: String, regle: String?, rappelMin: Int?, couleur: Int? = null): Long? {
        val agenda = agenda(c) ?: return null
        val v = ContentValues().apply {
            put(Events.CALENDAR_ID, agenda)
            put(Events.TITLE, titre)
            put(Events.EVENT_LOCATION, lieu)
            put(Events.DESCRIPTION, notes)
            put(Events.ALL_DAY, if (journee) 1 else 0)
            put(Events.EVENT_TIMEZONE, if (journee) "UTC" else ZoneId.systemDefault().id)
            put(Events.DTSTART, debut)
            if (couleur != null) put(Events.EVENT_COLOR, couleur)
            if (regle != null) {
                put(Events.RRULE, regle)
                put(Events.DURATION, "P${(fin - debut) / 1000}S")
                putNull(Events.DTEND)
            } else {
                put(Events.DTEND, fin)
                putNull(Events.RRULE)
                putNull(Events.DURATION)
            }
        }
        return try {
            val eid = if (id == null) {
                ContentUris.parseId(c.contentResolver.insert(Events.CONTENT_URI, v)!!)
            } else {
                c.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), v, null, null)
                id
            }
            c.contentResolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eid.toString()))
            if (rappelMin != null) {
                c.contentResolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                    put(Reminders.EVENT_ID, eid)
                    put(Reminders.MINUTES, rappelMin)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                })
            }
            eid
        } catch (_: Exception) {
            null
        }
    }

    fun supprimer(c: Context, id: Long) {
        try {
            c.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null)
        } catch (_: Exception) {
        }
    }
}

private sealed interface Vue {
    data object Onglets : Vue
    data object Usages : Vue
    data class Edition(
        val id: Long?, val jour: LocalDate, val titre: String? = null, val debut: Long? = null,
        val fin: Long? = null, val lieu: String? = null, val notes: String? = null, val journee: Boolean = false,
    ) : Vue
}

private const val SOLEIL = "M12 7.5a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0-9z"
private const val ICONE_CALENDRIER = "M5 6h14v14H5z M5 10h14 M9 3v5 M15 3v5"
const val GOUTTE = "M12 4c3 4 6 7 6 10a6 6 0 0 1-12 0c0-3 3-6 6-10z"
private const val ECOLE = "M3 9l9-5l9 5l-9 5z M7 11.5V16c3 2 7 2 10 0v-4.5 M21 9v5"
private const val MALLETTE = "M4 8h16v11H4z M9 8V6a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2 M4 13h16"
private const val GATEAU = "M5 12h14v8H5z M5 15c2 1 3-1 5 0s3 1 5 0s3-1 4 0 M12 6v3 M12 4.5h.01"

private enum class Onglet(val icone: String) { AUJOURDHUI(SOLEIL), SEMAINE(Icones.GRILLE), AFAIRE(Icones.LISTE), CALENDRIER(ICONE_CALENDRIER), CYCLE(GOUTTE) }

private fun onglets(u: Set<Usage>) = listOfNotNull(
    Onglet.AUJOURDHUI,
    Onglet.SEMAINE.takeIf { Usage.ECOLE in u },
    Onglet.AFAIRE.takeIf { Usage.ECOLE in u || Usage.TRAVAIL in u },
    Onglet.CALENDRIER.takeIf { Usage.TRAVAIL in u || Usage.ECOLE !in u },
    Onglet.CYCLE.takeIf { Usage.CYCLE in u },
)

private fun nom(o: Onglet, u: Set<Usage>) = when (o) {
    Onglet.AUJOURDHUI -> "Aujourd'hui"
    Onglet.SEMAINE -> "Semaine"
    Onglet.AFAIRE -> if (Usage.ECOLE in u && Usage.TRAVAIL in u) "À faire" else if (Usage.ECOLE in u) "Devoirs" else "Tâches"
    Onglet.CALENDRIER -> "Calendrier"
    Onglet.CYCLE -> "Cycle"
}

/** L'Agenda de Sama (maquettes agenda-1 à 5) : il s'organise autour de ce qu'on en fait (école, travail, cycle). */
class Agenda : ComponentActivity() {
    private var vue by mutableStateOf<Vue>(Vue.Onglets)
    private var onglet by mutableStateOf(Onglet.AUJOURDHUI)
    private var jour by mutableStateOf(LocalDate.now())
    private var usages by mutableStateOf<Set<Usage>?>(null)
    private var version by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        usages = Usages.lire(this)
        if (usages == null) vue = Vue.Usages
        lire(intent)
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.AgendaNuit else Identites.Agenda
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) {
                val u = usages.orEmpty()
                // L'évènement en cours d'écriture reste affiché pendant qu'il redescend.
                var derniere by remember { mutableStateOf<Vue.Edition?>(null) }
                (vue as? Vue.Edition)?.let { derniere = it }
                Box(Modifier.fillMaxSize().background(LocalIdentite.current.fond)) {
                    if (usages != null) {
                        val liste = onglets(u)
                        // Retour ramène d'abord à Aujourd'hui, puis quitte l'Agenda.
                        BackHandler(enabled = onglet != Onglet.AUJOURDHUI) { onglet = Onglet.AUJOURDHUI }
                        val pied: @Composable () -> Unit = { Spacer(Modifier.navigationBarsPadding().height(96.dp)) }
                        // D'un onglet à l'autre : un fondu, la nouvelle page s'approche à peine.
                        AnimatedContent(
                            onglet,
                            transitionSpec = {
                                (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.985f)) togetherWith fadeOut(tween(110))
                            },
                            label = "onglet",
                        ) { o ->
                            when (o) {
                                Onglet.AUJOURDHUI -> PageAujourdhui(u, version, { onglet = it }, { vue = it }, { vue = Vue.Usages }, { version++ }, pied)
                                Onglet.SEMAINE -> PageSemaine(version, { version++ }, pied)
                                Onglet.AFAIRE -> PageAFaire(u, version, { version++ }, pied)
                                Onglet.CALENDRIER -> PageMois(jour, version, { jour = it }, { vue = it }, pied)
                                Onglet.CYCLE -> PageCycle(pied)
                            }
                        }
                        if (liste.size > 1) NavFlottante(liste.map { nom(it, u) to it.icone }, liste.indexOf(onglet).coerceAtLeast(0), { onglet = liste[it] }, Modifier.align(Alignment.BottomCenter))
                    }
                    // Écrire un évènement : la page monte du bas, par-dessus l'agenda, et redescend quand on a fini, ou avec le doigt.
                    PageDoigt(vue is Vue.Edition, fermer = { vue = Vue.Onglets; version++ }, sens = Sens.BAS) {
                        derniere?.let { e ->
                            Edition(e) {
                                vue = Vue.Onglets
                                version++
                            }
                        }
                    }
                    AnimatedVisibility(vue == Vue.Usages, enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 10 }, exit = fadeOut(tween(160))) {
                        PageUsages(usages, premiere = usages == null, fermer = { vue = Vue.Onglets }) {
                            Usages.regler(this@Agenda, it)
                            usages = it
                            if (onglet !in onglets(it)) onglet = Onglet.AUJOURDHUI
                            vue = Vue.Onglets
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    override fun onResume() {
        super.onResume()
        version++
    }

    /** Mon cycle se referme dès qu'on quitte l'appli. */
    override fun onStop() {
        super.onStop()
        Cycle.ouvert = false
    }

    /** « Ajouter à l'agenda » (titre et heure donnés), ou un événement à voir. */
    private fun lire(i: Intent?) {
        when (i?.action) {
            Intent.ACTION_INSERT -> {
                val debut = i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0).takeIf { it > 0 }
                vue = Vue.Edition(
                    null, debut?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now(),
                    i.getStringExtra(Events.TITLE), debut,
                    i.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0).takeIf { debut != null && it > debut },
                    i.getStringExtra(Events.EVENT_LOCATION), i.getStringExtra(Events.DESCRIPTION),
                    i.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false),
                )
            }
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> i.data?.lastPathSegment?.toLongOrNull()?.let { id ->
                Agendas.evenement(this, id)?.let { e ->
                    jour = Instant.ofEpochMilli(e.debut).atZone(ZoneId.systemDefault()).toLocalDate()
                    vue = Vue.Edition(id, jour)
                }
            }
        }
    }
}

private fun ms(d: LocalDate, t: LocalTime = LocalTime.MIDNIGHT) = d.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun jourDe(e: Evenement): LocalDate = Instant.ofEpochMilli(e.debut).atZone(if (e.journee) ZoneId.of("UTC") else ZoneId.systemDefault()).toLocalDate()
private fun heureDe(t: Long): LocalTime = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalTime()
private fun titreJour(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }

private val VERT = Color(0xFF2E8A6E)
private val BLEU = Color(0xFF3D5A99)
private val OR = Color(0xFFE2A62B)
/** La couleur d'un évènement : celle qu'on lui a donnée, sinon une couleur tirée de son titre, toujours la même. */
private fun couleurEvenement(e: Evenement) = e.couleur?.let { Color(it) } ?: listOf(VERT, BLEU, OR, Color(0xFF5D4CC0), Color(0xFFE9805A))[Math.floorMod(e.titre.hashCode(), 5)]

/** Les couleurs qu'on peut donner à un évènement. */
private val COULEURS_EVENEMENT = listOf(BLEU, VERT, OR, Color(0xFFE9805A), ROSE, Color(0xFF5D4CC0), Color(0xFF3FB6A8), Color(0xFF6B6F7E))

// ——— À quoi sert l'Agenda (maquette agenda-1) ———

@Composable
private fun PageUsages(actuels: Set<Usage>?, premiere: Boolean, fermer: () -> Unit, choisir: (Set<Usage>) -> Unit) {
    val a = LocalIdentite.current
    var choix by remember { mutableStateOf(actuels ?: emptySet()) }
    if (!premiere) BackHandler(onBack = fermer)
    val auj = LocalDate.now()
    Column(Modifier.fillMaxSize().background(a.fond).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(30.dp))
            Column(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(if (a.sombre) Brush.verticalGradient(listOf(a.surface, a.surface)) else Brush.verticalGradient(listOf(Color.White, Color(0xFFEFE9E1))))
                    ,
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                BasicText(auj.dayOfWeek.getDisplayName(StyleDate.SHORT, Locale.FRENCH).uppercase(), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFD9542F)))
                BasicText("${auj.dayOfMonth}", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Light, fontSize = 24.sp, lineHeight = 26.sp, color = a.encre))
            }
            BasicText(
                if (premiere) "Votre Agenda,\npour quoi faire ?" else "Ce que fait\nvotre Agenda",
                modifier = Modifier.padding(top = 22.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp, color = a.encre),
            )
            BasicText(
                "Choisissez un ou plusieurs usages : l'Agenda s'organise autour. Vous pourrez changer plus tard.",
                modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
            )
            Usage.entries.forEach { u ->
                val (coul, clair, icone) = when (u) {
                    Usage.ECOLE -> Triple(BLEU, Color(0xFF6C8FDB), ECOLE)
                    Usage.TRAVAIL -> Triple(VERT, Color(0xFF4FB08F), MALLETTE)
                    Usage.CYCLE -> Triple(ROSE, Color(0xFFE27A8E), GOUTTE)
                }
                val oui = u in choix
                Row(
                    Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(if (oui) lerp(if (a.sombre) a.surface else Color.White, coul, 0.06f) else if (a.sombre) a.surface else Color.White)
                        .border(if (oui) 2.dp else 1.dp, if (oui) coul else a.trait, RoundedCornerShape(22.dp))
                        .clickable(role = Role.Checkbox) { choix = if (oui) choix - u else choix + u }
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(clair, coul))), contentAlignment = Alignment.Center) {
                        IconeTrait(icone, 24.dp, Color.White, epaisseur = 2f)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        BasicText(u.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = a.encre))
                        BasicText(u.texte, modifier = Modifier.padding(top = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.5.sp, lineHeight = 19.sp, color = a.encre2))
                    }
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).then(if (oui) Modifier.background(coul) else Modifier.border(2.dp, a.encre2.copy(alpha = 0.35f), CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) { if (oui) IconeTrait(Icones.COCHE, 15.dp, Color.White, epaisseur = 3f) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                when (choix.size) { 0 -> "Juste le calendrier"; 1 -> "1 usage choisi"; else -> "${choix.size} usages choisis" },
                modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2),
            )
            Row(
                Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(a.encre).clickable(role = Role.Button) { choisir(choix) }.padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(if (premiere) "Continuer" else "Enregistrer", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.fond))
                if (premiere) IconeTrait("M5 12h14 M13 6l6 6l-6 6", 18.dp, a.fond)
            }
        }
    }
}

// ——— Aujourd'hui (maquettes agenda-2 et 4) ———

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageAujourdhui(
    u: Set<Usage>, version: Int, versOnglet: (Onglet) -> Unit, aller: (Vue) -> Unit, reglerUsages: () -> Unit, changer: () -> Unit, pied: @Composable () -> Unit,
) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val auj = LocalDate.now()
    var jour by remember { mutableStateOf(auj) }
    val lundi = jour.minusDays((jour.dayOfWeek.value - 1).toLong())
    val lundiAuj = auj.minusDays((auj.dayOfWeek.value - 1).toLong())
    var lundiVu by remember { mutableStateOf(lundi) }
    var revenir by remember { mutableIntStateOf(0) }
    var evenements by remember { mutableStateOf(emptyList<Evenement>()) }
    var anniversaires by remember { mutableStateOf(emptyList<Anniversaires.Anniversaire>()) }
    LaunchedEffect(version, lundi, lundiVu) {
        withContext(Dispatchers.IO) {
            Agendas.agenda(c)
            // La semaine du jour choisi et celle qu'on regarde dans la bande (avec leurs voisines).
            evenements = Agendas.entre(c, ms(minOf(lundi, lundiVu).minusDays(8)), ms(maxOf(lundi, lundiVu).plusDays(15)))
            anniversaires = Anniversaires.tous(c)
        }
    }
    val ecole = Usage.ECOLE in u
    val travail = Usage.TRAVAIL in u
    val cours = remember(version) { if (ecole) Carnet.cours(c) else emptyList() }
    val devoirs = remember(version) { if (ecole) Carnet.devoirs(c) else emptyList() }
    val taches = remember(version) { if (travail) Carnet.taches(c) else emptyList() }
    val cycle = remember(version, Cycle.ouvert) { if (Usage.CYCLE in u && Cycle.ouvert) Cycle.lire(c)?.let { Previsions(it) } else null }
    var ajout by remember { mutableStateOf<String?>(null) }
    var apres by remember { mutableStateOf<(() -> Unit)?>(null) }
    val duJour = evenements.filter { jourDe(it) == jour }
    Column(Modifier.fillMaxSize()) {
        val ailleurs = jour != auj || lundiVu != lundiAuj
        EnTete(
            if (!ailleurs) (if (java.time.LocalTime.now().hour >= 18) "Bonsoir" else "Bonjour") else "Revenir à aujourd'hui",
            titreJour(jour),
            surSous = if (!ailleurs) null else ({
                jour = auj
                revenir++
            }),
        ) {
            Rond(Icones.REGLAGES, "Ce que fait l'Agenda", onClick = reglerUsages)
            Rond(Icones.PLUS, "Ajouter", plein = true, epaisseur = 2.2f) { ajout = "menu" }
        }
        // La semaine : un point par chose prévue (cours, évènement, devoir, règles prévues) ; on glisse pour changer de semaine.
        BandeSemaines(jour, revenir, { jour = it }, { lundiVu = it }) { d ->
            listOfNotNull(
                BLEU.takeIf { ecole && cours.any { it.jour == d.dayOfWeek } },
                VERT.takeIf { evenements.any { e -> jourDe(e) == d } || Anniversaires.du(anniversaires, d).isNotEmpty() },
                OR.takeIf { devoirs.any { it.faitLe == null && it.pour == d } || taches.any { it.faiteLe == null && it.pour == d } },
                ROSE.takeIf { cycle != null && (!d.isBefore(cycle.prochaines) && d.isBefore(cycle.prochaines.plusDays(cycle.regles.toLong()))) },
            )
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (ecole) CoursDuJour(jour, cours) { versOnglet(Onglet.SEMAINE) }
            if (travail) {
                MaJournee(jour, duJour.filter { !it.journee }, taches) { e -> aller(Vue.Edition(e.id, jour)) }
            }
            val autres = if (travail) duJour.filter { it.journee } else duJour
            if (autres.isNotEmpty()) {
                Section(if (travail) "Toute la journée" else "Évènements")
                BlocAppli {
                    autres.forEachIndexed { i, e ->
                        if (i > 0) FiletBloc(16.dp)
                        LigneEvenement(e) { aller(Vue.Edition(e.id, jour)) }
                    }
                }
            }
            // Les anniversaires des contacts : ce jour-là, et les six suivants si c'est aujourd'hui.
            val proches = (0..(if (jour == auj) 6 else 0)).flatMap { k -> Anniversaires.du(anniversaires, jour.plusDays(k.toLong())).map { it to k } }
            if (proches.isNotEmpty()) {
                Section("Anniversaires")
                BlocAppli {
                    proches.forEachIndexed { i, (an, k) ->
                        if (i > 0) FiletBloc(56.dp)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(teinte(OR, 0.2f)), contentAlignment = Alignment.Center) { IconeTrait(GATEAU, 16.dp, encreDe(Color(0xFFB07A10))) }
                            BasicText(an.nom, modifier = Modifier.weight(1f).padding(start = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = a.encre))
                            BasicText(if (k == 0) "aujourd'hui" else if (k == 1) "demain" else "dans $k jours", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                        }
                    }
                }
            }
            if (ecole) {
                val aFaire = devoirs.filter { it.faitLe == null && !it.pour.isAfter(jour.plusDays(7)) }.take(5)
                Section("Devoirs")
                if (aFaire.isEmpty()) {
                    BasicText("Rien pour les jours qui viennent.", modifier = Modifier.padding(start = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.5.sp, color = a.encre2))
                } else BlocAppli {
                    aFaire.forEachIndexed { i, d ->
                        if (i > 0) FiletBloc(52.dp)
                        LigneDevoir(d, { Carnet.cocherDevoir(c, d.id, true); changer() }) { versOnglet(Onglet.AFAIRE) }
                    }
                }
            }
            if (travail) {
                val debutJour = ms(auj)
                val aFaire = taches.filter { t -> (t.faiteLe == null && (t.pour == null || !t.pour.isAfter(jour))) || (jour == auj && (t.faiteLe ?: 0) >= debutJour) }.take(6)
                Section("Tâches")
                if (aFaire.isEmpty()) {
                    BasicText("Aucune tâche pour ce jour.", modifier = Modifier.padding(start = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.5.sp, color = a.encre2))
                } else BlocAppli {
                    aFaire.forEachIndexed { i, t ->
                        if (i > 0) FiletBloc(52.dp)
                        LigneTache(t, { Carnet.cocherTache(c, t.id, t.faiteLe == null); changer() }) { versOnglet(Onglet.AFAIRE) }
                    }
                }
            }
            if (Usage.CYCLE in u) {
                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (a.sombre) a.surface else Color.White)
                        .clickable(role = Role.Button) { versOnglet(Onglet.CYCLE) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cycle != null) AnneauCycle(cycle, 44.dp, 5.dp, marque = false)
                    else Box(Modifier.size(44.dp).clip(CircleShape).background(teinte(ROSE, 0.14f)), contentAlignment = Alignment.Center) { IconeTrait(GOUTTE, 20.dp, ROSE) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        BasicText("Mon cycle", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = a.encre))
                        // Rien du cycle ne s'affiche ici tant qu'il n'a pas été ouvert avec le code.
                        BasicText(cycle?.phrase() ?: "Caché · s'ouvre avec votre code", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                    }
                    IconeTrait(Icones.CADENAS, 18.dp, a.encre2)
                }
            }
            if (!ecole && !travail && duJour.isEmpty() && proches.isEmpty()) {
                BasicText("Rien de prévu.", modifier = Modifier.padding(start = 4.dp, top = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
            }
            pied()
        }
    }
    when (ajout) {
        // Le menu redescend, puis ce qu'on a choisi s'ouvre.
        "menu" -> FeuilleAppli(fermer = { ajout = null; apres?.invoke(); apres = null }, titre = "Ajouter") {
            val fermerFeuille = LocalFermerFeuille.current
            fun puis(action: () -> Unit) {
                apres = action
                fermerFeuille()
            }
            ActionFeuille(ICONE_CALENDRIER, "Un évènement", second = titreJour(jour)) { puis { aller(Vue.Edition(null, jour)) } }
            if (ecole) ActionFeuille(Icones.LISTE, "Un devoir", second = "Pour le prochain cours, en une touche") { puis { ajout = "devoir" } }
            if (ecole) ActionFeuille(Icones.GRILLE, "Un cours", second = "Dans l'emploi du temps") { puis { versOnglet(Onglet.SEMAINE) } }
            if (travail) ActionFeuille(Icones.COCHE, "Une tâche", second = "À cocher quand c'est fait") { puis { ajout = "tache" } }
        }
        "devoir" -> FeuilleDevoir(null, fermer = { ajout = null }) { ajout = null; changer() }
        "tache" -> FeuilleTache(null, false, fermer = { ajout = null }, versDevoir = {}) { ajout = null; changer() }
    }
}

/**
 * La bande des jours, une semaine par page : glisser parcourt les semaines sans rien choisir ; on choisit un jour
 * en le touchant. [semaineVue] reçoit le lundi de la semaine affichée, [revenir] ramène la bande au jour choisi.
 */
@Composable
private fun BandeSemaines(jour: LocalDate, revenir: Int, choisir: (LocalDate) -> Unit, semaineVue: (LocalDate) -> Unit, points: (LocalDate) -> List<Color>) {
    val a = LocalIdentite.current
    val auj = LocalDate.now()
    val lundiAuj = auj.minusDays((auj.dayOfWeek.value - 1).toLong())
    val centre = 520
    fun lundiDe(page: Int): LocalDate = lundiAuj.plusWeeks((page - centre).toLong())
    fun pageDe(d: LocalDate) = centre + ChronoUnit.WEEKS.between(lundiAuj, d.minusDays((d.dayOfWeek.value - 1).toLong())).toInt()
    val etat = rememberPagerState(initialPage = pageDe(jour)) { centre * 2 }
    LaunchedEffect(etat.settledPage) { semaineVue(lundiDe(etat.settledPage)) }
    // « Revenir à aujourd'hui » ramène aussi la bande.
    LaunchedEffect(jour, revenir) { pageDe(jour).let { if (it != etat.currentPage) etat.animateScrollToPage(it) } }
    HorizontalPager(etat, Modifier.fillMaxWidth(), beyondViewportPageCount = 1) { page ->
        val lundi = lundiDe(page)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            (0 until 7).forEach { k ->
                val d = lundi.plusDays(k.toLong())
                val ici = d == jour
                val fond by androidx.compose.animation.animateColorAsState(if (ici) a.encre else a.encre.copy(alpha = 0f), label = "jour")
                Column(
                    Modifier.width(46.dp).height(66.dp).clip(RoundedCornerShape(16.dp)).background(fond)
                        .clickable(onClickLabel = titreJour(d), role = Role.Button) { choisir(d) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    BasicText(
                        if (d.dayOfMonth == 1) d.month.getDisplayName(StyleDate.SHORT, Locale.FRENCH).removeSuffix(".") else d.dayOfWeek.getDisplayName(StyleDate.NARROW, Locale.FRENCH),
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, fontWeight = if (d.dayOfMonth == 1) FontWeight.Bold else FontWeight.Normal, color = if (ici) a.fond.copy(alpha = 0.7f) else a.encre2),
                    )
                    BasicText("${d.dayOfMonth}", style = TextStyle(fontFamily = Polices.corps, fontWeight = if (d == auj) FontWeight.Bold else FontWeight.SemiBold, fontSize = 18.sp, color = if (ici) a.fond else if (d == auj) a.accentTexte else a.encre))
                    Row(Modifier.height(7.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        points(d).take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(if (ici) lerp(it, Color.White, 0.3f) else it)) }
                    }
                }
            }
        }
    }
}

/** Les cours du jour : « Maintenant » avec ce qu'il reste, puis le suivant (aujourd'hui) ; tous les cours (un autre jour). */
@Composable
private fun CoursDuJour(jour: LocalDate, cours: List<Cours>, remplir: () -> Unit) {
    val a = LocalIdentite.current
    val l = cours.filter { it.jour == jour.dayOfWeek }
    if (cours.isEmpty()) {
        Section("Emploi du temps")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = remplir).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Remplissez votre emploi du temps une fois : chaque jour, l'Agenda montre le cours en cours et le suivant.", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.5.sp, lineHeight = 20.sp, color = a.encre2))
            IconeTrait(Icones.CHEVRON, 18.dp, a.accentTexte)
        }
        return
    }
    if (l.isEmpty()) return
    val maintenant = LocalTime.now()
    val aujourdhui = jour == LocalDate.now()
    val enCours = if (aujourdhui) l.firstOrNull { !maintenant.isBefore(it.debut) && maintenant.isBefore(it.fin) } else null
    val aVenir = if (aujourdhui) l.filter { it.debut.isAfter(maintenant) } else l
    if (aujourdhui && enCours == null && aVenir.isEmpty()) {
        Section("Cours")
        BasicText("Les cours sont finis pour aujourd'hui.", modifier = Modifier.padding(start = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.5.sp, color = a.encre2))
        return
    }
    Section(if (enCours != null) "Maintenant" else if (aujourdhui) "Tout à l'heure" else "Cours")
    val montres = listOfNotNull(enCours) + aVenir.take(if (aujourdhui) (if (enCours != null) 1 else 2) else aVenir.size)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        montres.forEachIndexed { i, co ->
            val actuel = co == enCours
            val suivant = l.getOrNull(l.indexOf(co) + 1)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (a.sombre) a.surface else Color.White).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(5.dp).height(44.dp).clip(RoundedCornerShape(3.dp)).background(co.couleur))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    BasicText(co.matiere, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = a.encre))
                    val reste = ChronoUnit.MINUTES.between(maintenant, co.fin)
                    BasicText(
                        listOfNotNull(
                            co.salle.ifBlank { null }, co.prof.ifBlank { null },
                            if (actuel) "encore $reste min" else if (aujourdhui && i == montres.lastIndex && suivant == null) "dernier cours" else null,
                            if (!actuel && aujourdhui && suivant != null && ChronoUnit.MINUTES.between(co.fin, suivant.debut) >= 60) "ensuite, pause" else null,
                        ).joinToString(" · "),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2),
                    )
                    if (actuel) {
                        val vise = (ChronoUnit.SECONDS.between(co.debut, maintenant).toFloat() / ChronoUnit.SECONDS.between(co.debut, co.fin)).coerceIn(0f, 1f)
                        val remplie = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(vise) { remplie.animateTo(vise, tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                        val part = remplie.value
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(a.trait)) {
                            Box(Modifier.fillMaxWidth(part).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(co.couleur))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(heure(co.debut), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.encre2))
                    BasicText(heure(co.fin), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.encre2))
                }
            }
        }
    }
}

/** Le travail : le résumé du jour, puis les évènements heure par heure (maquette agenda-4). */
@Composable
private fun MaJournee(jour: LocalDate, evenements: List<Evenement>, taches: List<Tache>, ouvrir: (Evenement) -> Unit) {
    val a = LocalIdentite.current
    val aFaire = taches.count { it.faiteLe == null && (it.pour == null || !it.pour.isAfter(jour)) }
    val fin = evenements.maxOfOrNull { it.fin }?.let { heureDe(it) }
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (evenements.isNotEmpty()) Pastille("${evenements.size} évènement${if (evenements.size > 1) "s" else ""}", VERT)
        if (aFaire > 0) Pastille("$aFaire tâche${if (aFaire > 1) "s" else ""}", Color(0xFFB07A10))
        if (fin != null && (jour != LocalDate.now() || fin.isAfter(LocalTime.now()))) Pastille("Libre à ${heure(fin)}", a.encre)
    }
    if (evenements.isEmpty()) return
    val h0 = minOf(8, evenements.minOf { heureDe(it.debut).hour })
    val h1 = maxOf(17, evenements.maxOf { heureDe(it.fin).let { t -> if (t.minute > 0) t.hour + 1 else t.hour } }).coerceAtMost(24)
    val pas = 42.dp
    Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(pas * (h1 - h0) + 10.dp)) {
        for (h in h0..h1) {
            Box(Modifier.offset(y = pas * (h - h0)).fillMaxWidth().height(1.dp).padding(start = 46.dp).background(a.trait))
            BasicText("%02d:00".format(h), modifier = Modifier.offset(y = pas * (h - h0) - 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 10.5.sp, color = a.encre2.copy(alpha = 0.8f)))
        }
        evenements.forEach { e ->
            val d = heureDe(e.debut)
            val minutes = ChronoUnit.MINUTES.between(d, heureDe(e.fin)).coerceAtLeast(30)
            val coul = couleurEvenement(e)
            Row(
                Modifier.offset(x = 52.dp, y = pas * ((d.toSecondOfDay() / 3600f) - h0) + 1.dp).fillMaxWidth().padding(end = 52.dp)
                    .height(maxOf(pas * (minutes / 60f) - 3.dp, 30.dp)).clip(RoundedCornerShape(12.dp)).background(teinte(coul, 0.12f))
                    .clickable(onClickLabel = e.titre, role = Role.Button) { ouvrir(e) }.padding(horizontal = 12.dp, vertical = if (minutes < 60) 5.dp else 8.dp),
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(coul))
                Column(Modifier.padding(start = 10.dp)) {
                    BasicText(e.titre.ifBlank { "Sans titre" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = a.encre))
                    if (minutes >= 60) BasicText(listOfNotNull("${heure(d)} – ${heure(heureDe(e.fin))}", e.lieu?.ifBlank { null }).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, color = a.encre2))
                }
            }
        }
        if (jour == LocalDate.now()) {
            var m by remember { mutableStateOf(LocalTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(20_000)
                    m = LocalTime.now()
                }
            }
            if (m.hour in h0 until h1) {
                val y = pas * ((m.toSecondOfDay() / 3600f) - h0)
                Box(Modifier.offset(y = y - 1.dp).padding(start = 40.dp).fillMaxWidth().height(2.dp).background(ROUGE_HEURE))
                EtiquetteHeure(m, Modifier.offset(y = y - 9.dp))
            }
        }
    }
}

@Composable
private fun Pastille(texte: String, couleur: Color) {
    BasicText(
        texte, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(teinte(couleur, 0.12f)).padding(horizontal = 11.dp, vertical = 6.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = encreDe(couleur)),
    )
}

@Composable
private fun LigneEvenement(e: Evenement, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().clickable(onClick = ouvrir).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(couleurEvenement(e)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            BasicText(e.titre.ifBlank { "Sans titre" }, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = a.encre))
            val second = listOfNotNull(e.lieu?.ifBlank { null }, if (e.regle != null) "se répète" else null).joinToString(" · ")
            if (second.isNotBlank()) BasicText(second, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
        }
        Column(horizontalAlignment = Alignment.End) {
            if (e.journee) BasicText("Journée", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.accentTexte))
            else {
                BasicText(heure(heureDe(e.debut)), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.encre))
                BasicText(heure(heureDe(e.fin)), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
            }
        }
    }
}

// ——— Le calendrier du mois ———

@Composable
private fun PageMois(jour: LocalDate, version: Int, choisir: (LocalDate) -> Unit, aller: (Vue) -> Unit, pied: @Composable () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var mois by remember { mutableStateOf(YearMonth.from(jour)) }
    // Changer de mois garde le même jour du mois, pour que la liste suive ce qu'on regarde.
    fun versMois(m: YearMonth) {
        mois = m
        choisir(m.atDay(jour.dayOfMonth.coerceAtMost(m.lengthOfMonth())))
    }
    var evenements by remember { mutableStateOf(emptyList<Evenement>()) }
    var anniversaires by remember { mutableStateOf(emptyList<Anniversaires.Anniversaire>()) }
    LaunchedEffect(mois, version) {
        withContext(Dispatchers.IO) {
            Agendas.agenda(c)
            evenements = Agendas.entre(c, ms(mois.atDay(1).minusDays(7)), ms(mois.atEndOfMonth().plusDays(8)))
            anniversaires = Anniversaires.tous(c)
        }
    }
    val parJour = evenements.groupBy { jourDe(it) }
    Column(Modifier.fillMaxSize()) {
        EnTete("${mois.year}", mois.month.getDisplayName(StyleDate.FULL_STANDALONE, Locale.FRENCH).replaceFirstChar { it.uppercase() }) {
            if (mois != YearMonth.now() || jour != LocalDate.now()) {
                BoutonTexteAppli("Aujourd'hui", style = 's') {
                    mois = YearMonth.now()
                    choisir(LocalDate.now())
                }
            }
            Rond(Icones.PLUS, "Nouvel évènement", plein = true, epaisseur = 2.2f) { aller(Vue.Edition(null, jour)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach { l ->
                BasicText(l, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.encre2, textAlign = TextAlign.Center))
            }
        }
        val premier = mois.atDay(1)
        val debut = premier.minusDays((premier.dayOfWeek.value - 1).toLong())
        // On change de mois en glissant la grille ; les lecteurs d'écran ont « Mois précédent », « Mois suivant ».
        var glisse by remember { mutableStateOf(0f) }
        Column(
            Modifier.padding(horizontal = 12.dp)
                .pointerInput(jour) {
                    detectHorizontalDragGestures(
                        onDragStart = { glisse = 0f },
                        onDragEnd = { if (glisse < -120f) versMois(mois.plusMonths(1)) else if (glisse > 120f) versMois(mois.minusMonths(1)) },
                    ) { _, d -> glisse += d }
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Mois précédent") { versMois(mois.minusMonths(1)); true },
                        CustomAccessibilityAction("Mois suivant") { versMois(mois.plusMonths(1)); true },
                    )
                },
        ) {
            for (semaine in 0 until 6) {
                val lundi = debut.plusWeeks(semaine.toLong())
                if (semaine == 5 && lundi.month != mois.month) break
                Row {
                    for (j in 0 until 7) {
                        val d = lundi.plusDays(j.toLong())
                        val choisi = d == jour
                        val aujourdhui = d == LocalDate.now()
                        val marque = !parJour[d].isNullOrEmpty() || Anniversaires.du(anniversaires, d).isNotEmpty()
                        Column(
                            Modifier.weight(1f).aspectRatio(1.1f).clip(CircleShape)
                                .then(if (choisi) Modifier.background(a.encre) else if (aujourdhui) Modifier.background(a.voile) else Modifier)
                                .clickable(onClickLabel = titreJour(d), role = Role.Button) { choisir(d) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            BasicText(
                                "${d.dayOfMonth}",
                                style = TextStyle(
                                    fontFamily = Polices.corps, fontWeight = if (aujourdhui || choisi) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp,
                                    color = when {
                                        choisi -> a.fond
                                        d.month != mois.month -> a.encre2.copy(alpha = 0.5f)
                                        aujourdhui -> a.accentTexte
                                        else -> a.encre
                                    },
                                ),
                            )
                            Box(Modifier.size(5.dp).clip(CircleShape).background(if (!marque) Color.Transparent else if (choisi) a.fond else a.accent))
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Section(titreJour(jour))
            val duJour = parJour[jour].orEmpty()
            val ans = Anniversaires.du(anniversaires, jour)
            if (duJour.isEmpty() && ans.isEmpty()) {
                BasicText("Rien de prévu.", modifier = Modifier.padding(start = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
            } else BlocAppli {
                ans.forEachIndexed { i, an ->
                    if (i > 0) FiletBloc(16.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconeTrait(GATEAU, 20.dp, encreDe(Color(0xFFB07A10)))
                        BasicText("Anniversaire de ${an.nom}", modifier = Modifier.padding(start = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = a.encre))
                    }
                }
                duJour.forEachIndexed { i, e ->
                    if (i > 0 || ans.isNotEmpty()) FiletBloc(16.dp)
                    LigneEvenement(e) { aller(Vue.Edition(e.id, jour)) }
                }
            }
            pied()
        }
    }
}

// ——— Un évènement : le créer ou le modifier ———

private val RAPPELS = listOf(null to "Aucun", 10 to "10 min avant", 60 to "1 h avant", 24 * 60 to "La veille")
private val REPETITIONS = listOf(null to "Jamais", "FREQ=WEEKLY" to "Chaque semaine", "FREQ=MONTHLY" to "Chaque mois", "FREQ=YEARLY" to "Chaque année")
private const val EPINGLE = "M12 21s-6-5.6-6-11a6 6 0 0 1 12 0c0 5.4-6 11-6 11z M12 8.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z"
private const val NOTE = "M6 4h9l3 3v13H6z M9 10h6 M9 14h6 M9 18h3"
private const val CLOCHE = "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z M10 20a2 2 0 0 0 4 0"
private const val BOUCLE = "M4 12a8 8 0 0 1 14-5.3 M20 4v4h-4 M20 12a8 8 0 0 1-14 5.3 M4 20v-4h4"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Edition(v: Vue.Edition, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var titre by remember { mutableStateOf(v.titre.orEmpty()) }
    var jour by remember { mutableStateOf(v.jour) }
    val prochaineHeure = LocalTime.of((LocalTime.now().hour + 1).coerceAtMost(23), 0)
    var debut by remember { mutableStateOf(v.debut?.let { heureDe(it) } ?: prochaineHeure) }
    var fin by remember { mutableStateOf(if (v.debut != null && v.fin != null) heureDe(v.fin) else (v.debut?.let { heureDe(it) } ?: prochaineHeure).plusHours(1)) }
    var journee by remember { mutableStateOf(v.journee) }
    var lieu by remember { mutableStateOf(v.lieu.orEmpty()) }
    var notes by remember { mutableStateOf(v.notes.orEmpty()) }
    var rappel by remember { mutableStateOf<Int?>(60) }
    var regle by remember { mutableStateOf<String?>(null) }
    var couleurChoisie by remember { mutableStateOf(COULEURS_EVENEMENT.first()) }
    // Tout l'écran passe en douceur à la couleur choisie.
    val couleur by androidx.compose.animation.animateColorAsState(couleurChoisie, tween(320), label = "couleur")
    // Quelle heure on règle à la molette : le début, la fin, ou aucune (molettes repliées).
    var reglage by remember { mutableStateOf<Int?>(if (v.id == null && v.debut == null) 0 else null) }
    var supprimer by remember { mutableStateOf(false) }
    LaunchedEffect(v.id) {
        val id = v.id ?: return@LaunchedEffect
        val e = withContext(Dispatchers.IO) { Agendas.evenement(c, id) } ?: return@LaunchedEffect
        val z = Instant.ofEpochMilli(e.debut).atZone(if (e.journee) ZoneId.of("UTC") else ZoneId.systemDefault())
        titre = e.titre
        jour = z.toLocalDate()
        debut = z.toLocalTime()
        fin = Instant.ofEpochMilli(e.fin).atZone(if (e.journee) ZoneId.of("UTC") else ZoneId.systemDefault()).toLocalTime()
        journee = e.journee
        lieu = e.lieu.orEmpty()
        notes = e.notes.orEmpty()
        regle = e.regle?.substringBefore(';')?.takeIf { r -> REPETITIONS.any { it.first == r } }
        rappel = Agendas.rappel(c, id)
        couleurChoisie = couleurEvenement(e)
    }
    val duree = ChronoUnit.MINUTES.between(debut, fin).let { if (it <= 0) it + 24 * 60 else it }
    fun enregistrer() {
        val d = if (journee) jour.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() else ms(jour, debut)
        val f = if (journee) d + 24 * 3600_000L else d + duree * 60_000L
        Agendas.garder(c, v.id, titre.trim(), d, f, journee, lieu.trim(), notes.trim(), regle, rappel, couleurChoisie.toArgb())
        fermer()
    }
    Box(Modifier.fillMaxSize().background(a.fond)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // La barre : Annuler, le titre, Ajouter (actif dès qu'il y a un titre).
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Annuler", modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = fermer).padding(10.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2),
                )
                BasicText(
                    if (v.id == null) "Nouvel évènement" else "L'évènement", modifier = Modifier.weight(1f),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = a.encre, textAlign = TextAlign.Center),
                )
                val peut = titre.isNotBlank()
                BasicText(
                    if (v.id == null) "Ajouter" else "Enregistrer",
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = peut, role = Role.Button) { enregistrer() }.padding(10.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (peut) a.accentTexte else a.encre2.copy(alpha = 0.45f)),
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // Le titre, sur la couleur de l'évènement.
                BlocAppli(Modifier.padding(top = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(5.dp).height(34.dp).clip(RoundedCornerShape(3.dp)).background(couleur))
                        BasicTextField(
                            value = titre, onValueChange = { titre = it }, singleLine = true,
                            textStyle = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 23.sp, color = a.encre), cursorBrush = SolidColor(couleur),
                            modifier = Modifier.weight(1f).padding(start = 12.dp).semantics { contentDescription = "Titre" },
                            decorationBox = { champ ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (titre.isEmpty()) BasicText("Réunion, visite…", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 23.sp, color = a.encre2.copy(alpha = 0.55f)))
                                    champ()
                                }
                            },
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        COULEURS_EVENEMENT.forEach { k ->
                            val oui = k == couleurChoisie
                            val creux by androidx.compose.animation.core.animateDpAsState(if (oui) 4.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 700f), label = "creux")
                            Box(
                                // Le ressort dépasse un peu sa cible : jamais une marge négative.
                                Modifier.size(30.dp).clip(CircleShape).border(2.dp, if (oui) k else Color.Transparent, CircleShape).padding(creux.coerceAtLeast(0.dp)).clip(CircleShape).background(k)
                                    .clickable(onClickLabel = "Cette couleur", role = Role.RadioButton) { couleurChoisie = k },
                            )
                        }
                    }
                }
                // Quand : le jour (une bande qu'on fait défiler), puis l'heure (à la molette).
                Section("Quand")
                BlocAppli {
                    BandeJours(jour, couleur) { jour = it }
                    FiletBloc()
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { journee = !journee; reglage = null }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("Toute la journée", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
                        InterAppli(journee)
                    }
                    if (!journee) {
                        FiletBloc()
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CaseHeure("Début", debut, reglage == 0, couleur, Modifier.weight(1f)) { reglage = if (reglage == 0) null else 0 }
                            IconeTrait("M5 12h14 M13 6l6 6l-6 6", 18.dp, a.encre2)
                            CaseHeure("Fin", fin, reglage == 1, couleur, Modifier.weight(1f)) { reglage = if (reglage == 1) null else 1 }
                        }
                        // La molette se déplie sous l'heure touchée, et se replie quand on la touche à nouveau.
                        var derniereReglee by remember { mutableIntStateOf(0) }
                        reglage?.let { derniereReglee = it }
                        AnimatedVisibility(reglage != null, enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) + fadeIn(tween(160)), exit = shrinkVertically(tween(200)) + fadeOut(tween(120))) {
                            val r = derniereReglee
                            // Changer le début déplace la fin d'autant : la durée reste la même.
                            key(r) {
                                RoueHeure(if (r == 0) debut else fin, couleur) { t ->
                                    if (r == 0) {
                                        fin = t.plusMinutes(duree)
                                        debut = t
                                    } else fin = t
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15L to "15 min", 30L to "30 min", 60L to "1 h", 90L to "1 h 30", 120L to "2 h", 180L to "3 h").forEach { (m, n) ->
                                val oui = duree == m
                                BasicText(
                                    n, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (oui) teinte(couleur, 0.16f) else Color.Transparent)
                                        
                                        .clickable(onClickLabel = "Durée $n", role = Role.RadioButton) { fin = debut.plusMinutes(m) }.padding(horizontal = 13.dp, vertical = 7.dp),
                                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (oui) encreDe(couleur) else a.encre),
                                )
                            }
                        }
                    }
                }
                Section("Où et quoi")
                BlocAppli {
                    ChampBloc(EPINGLE, "Lieu ou adresse", lieu, couleur) { lieu = it }
                    FiletBloc(52.dp)
                    ChampBloc(NOTE, "Notes", notes, couleur, uneLigne = false) { notes = it }
                }
                Section("Me le rappeler")
                BlocAppli {
                    ChoixLigne(CLOCHE, RAPPELS.map { it.second }, RAPPELS.indexOfFirst { it.first == rappel }.coerceAtLeast(0), couleur) { rappel = RAPPELS[it].first }
                }
                Section("Se répète")
                BlocAppli {
                    ChoixLigne(BOUCLE, REPETITIONS.map { it.second }, REPETITIONS.indexOfFirst { it.first == regle }.coerceAtLeast(0), couleur) { regle = REPETITIONS[it].first }
                }
                BasicText(
                    "Visible seulement dans cet Espace, sur ce téléphone.",
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2),
                )
                if (v.id != null) {
                    BlocAppli(Modifier.padding(top = 16.dp)) {
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { supprimer = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconeTrait(Icones.CORBEILLE, 20.dp, Color(0xFFC2412D))
                            BasicText("Supprimer l'évènement", modifier = Modifier.padding(start = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFFC2412D)))
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(40.dp))
            }
        }
        if (supprimer && v.id != null) {
            africa.samaos.banco.appli.DialogueAppli(
                "Supprimer « ${titre.ifBlank { "cet évènement" }} » ?",
                if (regle != null) "Toutes ses répétitions disparaissent aussi." else null,
                fermer = { supprimer = false },
            ) {
                BoutonTexteAppli("Annuler", style = 't') { supprimer = false }
                BoutonTexteAppli("Supprimer") {
                    Agendas.supprimer(c, v.id)
                    fermer()
                }
            }
        }
    }
}

/** Le jour d'un évènement : une bande de jours qu'on fait glisser, le mois au-dessus. */
@Composable
private fun BandeJours(jour: LocalDate, couleur: Color, choisir: (LocalDate) -> Unit) {
    val a = LocalIdentite.current
    val auj = LocalDate.now()
    val premier = auj.minusDays(60)
    val n = 60 + 400
    val etat = rememberLazyListState(initialFirstVisibleItemIndex = (ChronoUnit.DAYS.between(premier, jour).toInt() - 3).coerceIn(0, n - 1))
    val portee = rememberCoroutineScope()
    // Le mois du jour visible au milieu de la bande.
    val milieu by remember { androidx.compose.runtime.derivedStateOf { premier.plusDays((etat.firstVisibleItemIndex + 3).toLong()) } }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            milieu.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)).replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = a.encre),
        )
        if (jour != auj) {
            BasicText(
                "Aujourd'hui", modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                    choisir(auj)
                    portee.launch { etat.animateScrollToItem((ChronoUnit.DAYS.between(premier, auj).toInt() - 3).coerceAtLeast(0)) }
                }.padding(horizontal = 8.dp, vertical = 4.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.accentTexte),
            )
        }
    }
    LazyRow(state = etat, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(n) { i ->
            val d = premier.plusDays(i.toLong())
            val ici = d == jour
            Column(
                Modifier.width(46.dp).height(64.dp).clip(RoundedCornerShape(16.dp)).background(if (ici) couleur else if (d == auj) a.voile else Color.Transparent)
                    .clickable(onClickLabel = titreJour(d), role = Role.RadioButton) { choisir(d) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    if (d.dayOfMonth == 1) d.month.getDisplayName(StyleDate.SHORT, Locale.FRENCH).removeSuffix(".") else d.dayOfWeek.getDisplayName(StyleDate.SHORT, Locale.FRENCH).removeSuffix(".").take(3),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = if (d.dayOfMonth == 1) FontWeight.Bold else FontWeight.Medium, fontSize = 11.5.sp, color = if (ici) Color.White.copy(alpha = 0.8f) else a.encre2),
                )
                BasicText("${d.dayOfMonth}", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = if (ici) Color.White else if (d == auj) a.accentTexte else a.encre))
            }
        }
    }
}

/** Une heure de l'évènement (début ou fin) : touchée, la molette s'ouvre dessous. */
@Composable
private fun CaseHeure(nom: String, t: LocalTime, choisie: Boolean, couleur: Color, modifier: Modifier, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val fond by androidx.compose.animation.animateColorAsState(if (choisie) teinte(couleur, 0.14f) else a.champ, label = "case")
    val bord by androidx.compose.animation.animateColorAsState(if (choisie) couleur else couleur.copy(alpha = 0f), label = "bord")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(fond).border(1.5.dp, bord, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Régler l'heure de ${nom.lowercase()}", role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = if (choisie) encreDe(couleur) else a.encre2))
        BasicText(heure(t), style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(300), fontSize = 34.sp, lineHeight = 38.sp, color = a.encre))
    }
}

/** L'heure à la molette commune de Banco (minutes de 5 en 5), sur une bande à la couleur de l'évènement. */
@Composable
private fun RoueHeure(t: LocalTime, couleur: Color, changer: (LocalTime) -> Unit) {
    africa.samaos.banco.appli.RoueHeure(
        t.hour, t.minute, Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp), pas = 5, bande = teinte(couleur, 0.12f), cran = 50.dp, taille = 30.sp,
    ) { h, m -> changer(LocalTime.of(h, m)) }
}

/** Un champ dans un bloc, avec son icône (le lieu, les notes). */
@Composable
private fun ChampBloc(icone: String, nom: String, valeur: String, couleur: Color, uneLigne: Boolean = true, changer: (String) -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = if (uneLigne) Alignment.CenterVertically else Alignment.Top) {
        IconeTrait(icone, 20.dp, if (valeur.isEmpty()) a.encre2 else encreDe(couleur))
        BasicTextField(
            value = valeur, onValueChange = changer, singleLine = uneLigne, minLines = if (uneLigne) 1 else 2,
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 22.sp, color = a.encre), cursorBrush = SolidColor(couleur),
            modifier = Modifier.weight(1f).padding(start = 16.dp).semantics { contentDescription = nom },
            decorationBox = { champ -> Box { if (valeur.isEmpty()) BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2)); champ() } },
        )
    }
}

/** Un choix parmi quelques-uns (rappel, répétition), sur une ligne qu'on fait glisser. */
@Composable
private fun ChoixLigne(icone: String, choix: List<String>, choisi: Int, couleur: Color, choisir: (Int) -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconeTrait(icone, 20.dp, if (choisi > 0) encreDe(couleur) else a.encre2)
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choix.forEachIndexed { i, n ->
                val oui = i == choisi
                BasicText(
                    n, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (oui) teinte(couleur, 0.16f) else Color.Transparent)
                        
                        .clickable(role = Role.RadioButton) { choisir(i) }.padding(horizontal = 13.dp, vertical = 7.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (oui) encreDe(couleur) else a.encre),
                )
            }
        }
    }
}
