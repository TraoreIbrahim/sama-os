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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Fab
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import africa.samaos.banco.appli.AvecIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as StyleDate
import java.util.Locale

class Evenement(val id: Long, val titre: String, val debut: Long, val fin: Long, val journee: Boolean, val lieu: String?, val notes: String?, val regle: String?)

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
                arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.EVENT_LOCATION, Instances.DESCRIPTION, Instances.RRULE),
                null, null, "${Instances.BEGIN} ASC",
            )?.use { cur ->
                while (cur.moveToNext()) {
                    l += Evenement(cur.getLong(0), cur.getString(1).orEmpty(), cur.getLong(2), cur.getLong(3), cur.getInt(4) == 1, cur.getString(5), cur.getString(6), cur.getString(7))
                }
            }
        } catch (_: SecurityException) {
        }
        return l
    }

    fun evenement(c: Context, id: Long): Evenement? = try {
        c.contentResolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, id),
            arrayOf(Events._ID, Events.TITLE, Events.DTSTART, Events.DTEND, Events.ALL_DAY, Events.EVENT_LOCATION, Events.DESCRIPTION, Events.RRULE),
            null, null, null,
        )?.use { cur -> if (cur.moveToFirst()) Evenement(cur.getLong(0), cur.getString(1).orEmpty(), cur.getLong(2), cur.getLong(3), cur.getInt(4) == 1, cur.getString(5), cur.getString(6), cur.getString(7)) else null }
    } catch (_: Exception) {
        null
    }

    fun rappel(c: Context, id: Long): Int? = try {
        c.contentResolver.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()), null)
            ?.use { if (it.moveToFirst()) it.getInt(0) else null }
    } catch (_: Exception) {
        null
    }

    fun garder(c: Context, id: Long?, titre: String, debut: Long, fin: Long, journee: Boolean, lieu: String, notes: String, regle: String?, rappelMin: Int?): Long? {
        val agenda = agenda(c) ?: return null
        val v = ContentValues().apply {
            put(Events.CALENDAR_ID, agenda)
            put(Events.TITLE, titre)
            put(Events.EVENT_LOCATION, lieu)
            put(Events.DESCRIPTION, notes)
            put(Events.ALL_DAY, if (journee) 1 else 0)
            put(Events.EVENT_TIMEZONE, if (journee) "UTC" else ZoneId.systemDefault().id)
            put(Events.DTSTART, debut)
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
    data object Mois : Vue
    data class Edition(
        val id: Long?, val jour: LocalDate, val titre: String? = null, val debut: Long? = null,
        val fin: Long? = null, val lieu: String? = null, val notes: String? = null, val journee: Boolean = false,
    ) : Vue
}

/** L'Agenda de Sama (maquettes l2-agd). */
class Agenda : ComponentActivity() {
    private var vue by mutableStateOf<Vue>(Vue.Mois)
    private var jour by mutableStateOf(LocalDate.now())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                when (val v = vue) {
                    Vue.Mois -> PageMois(jour, { jour = it }) { vue = it }
                    is Vue.Edition -> Edition(v) { vue = Vue.Mois }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
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

// ——— Le mois et le jour (maquette l2-agd-mois) ———

@Composable
private fun PageMois(jour: LocalDate, choisir: (LocalDate) -> Unit, aller: (Vue) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var mois by remember { mutableStateOf(YearMonth.from(jour)) }
    // Changer de mois garde le même jour du mois, pour que la liste suive ce qu'on regarde.
    fun versMois(m: YearMonth) {
        mois = m
        choisir(m.atDay(jour.dayOfMonth.coerceAtMost(m.lengthOfMonth())))
    }
    var evenements by remember { mutableStateOf(emptyList<Evenement>()) }
    LaunchedEffect(mois) {
        evenements = withContext(Dispatchers.IO) {
            Agendas.agenda(c)
            Agendas.entre(c, ms(mois.atDay(1).minusDays(7)), ms(mois.atEndOfMonth().plusDays(8)))
        }
    }
    val parJour = evenements.groupBy { Instant.ofEpochMilli(it.debut).atZone(if (it.journee) ZoneId.of("UTC") else ZoneId.systemDefault()).toLocalDate() }
    Box(Modifier.fillMaxSize()) {
        EcranAppli {
            Tete(mois.month.getDisplayName(StyleDate.FULL_STANDALONE, Locale.FRENCH).replaceFirstChar { it.uppercase() } + " " + mois.year) {
                if (mois != YearMonth.now() || jour != LocalDate.now()) {
                    BoutonTexteAppli("Aujourd'hui", style = 's') {
                        mois = YearMonth.now()
                        choisir(LocalDate.now())
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { l ->
                    BasicText(l, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.encre2, textAlign = TextAlign.Center))
                }
            }
            val premier = mois.atDay(1)
            val debut = premier.minusDays((premier.dayOfWeek.value - 1).toLong())
            // On change de mois en glissant la grille ; les lecteurs d'écran ont « Mois précédent », « Mois suivant ».
            var glisse by remember { mutableStateOf(0f) }
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    .pointerInput(jour) {
                        detectHorizontalDragGestures(
                            onDragStart = { glisse = 0f },
                            onDragEnd = {
                                if (glisse < -120f) versMois(mois.plusMonths(1)) else if (glisse > 120f) versMois(mois.minusMonths(1))
                            },
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
                            Column(
                                Modifier.weight(1f).aspectRatio(1.1f).clip(CircleShape)
                                    .then(if (choisi) Modifier.background(a.accent) else if (aujourdhui) Modifier.background(a.voile) else Modifier)
                                    .clickable(onClickLabel = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)), role = Role.Button) { choisir(d) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                BasicText(
                                    "${d.dayOfMonth}",
                                    style = TextStyle(
                                        fontFamily = Polices.corps, fontWeight = if (aujourdhui || choisi) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp,
                                        color = when {
                                            choisi -> a.surAccent
                                            d.month != mois.month -> a.encre2.copy(alpha = 0.5f)
                                            else -> a.encre
                                        },
                                    ),
                                )
                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (parJour[d].isNullOrEmpty()) androidx.compose.ui.graphics.Color.Transparent else if (choisi) a.surAccent else a.accent))
                            }
                        }
                    }
                }
            }
            Rub(jour.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() })
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val duJour = parJour[jour].orEmpty()
                if (duJour.isEmpty()) {
                    BasicText("Rien de prévu.", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
                }
                duJour.forEach { e ->
                    val h = { t: Long -> Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }
                    LigneAppli(
                        e.titre.ifBlank { "Sans titre" },
                        listOfNotNull(e.lieu?.ifBlank { null }, if (e.regle != null) "se répète" else null).joinToString(" · ").ifBlank { null },
                        debut = {
                            Column(Modifier.width(52.dp)) {
                                if (e.journee) BasicText("Journée", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.accentTexte))
                                else {
                                    BasicText(h(e.debut), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.encre))
                                    BasicText(h(e.fin), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                                }
                            }
                        },
                    ) { aller(Vue.Edition(e.id, jour)) }
                }
                Spacer(Modifier.height(120.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding()) {
            Box(Modifier.size(220.dp, 100.dp)) { Fab("Événement", Icones.PLUS) { aller(Vue.Edition(null, jour)) } }
        }
    }
}

// ——— Un événement (maquette l2-agd-evenement) ———

private val RAPPELS = listOf(null to "Aucun", 10 to "10 min avant", 60 to "1 heure avant", 24 * 60 to "La veille")
private val REPETITIONS = listOf(null to "Une seule fois", "FREQ=WEEKLY" to "Chaque semaine", "FREQ=MONTHLY" to "Chaque mois", "FREQ=YEARLY" to "Chaque année")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Edition(v: Vue.Edition, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var titre by remember { mutableStateOf(v.titre.orEmpty()) }
    var jour by remember { mutableStateOf(v.jour) }
    var debut by remember { mutableStateOf(v.debut?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() } ?: LocalTime.of((LocalTime.now().hour + 1).coerceAtMost(23), 0)) }
    var duree by remember { mutableIntStateOf(if (v.debut != null && v.fin != null) ((v.fin - v.debut) / 60_000).toInt() else 60) }
    var journee by remember { mutableStateOf(v.journee) }
    var lieu by remember { mutableStateOf(v.lieu.orEmpty()) }
    var notes by remember { mutableStateOf(v.notes.orEmpty()) }
    var rappel by remember { mutableStateOf<Int?>(60) }
    var regle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(v.id) {
        val id = v.id ?: return@LaunchedEffect
        val e = withContext(Dispatchers.IO) { Agendas.evenement(c, id) } ?: return@LaunchedEffect
        val z = Instant.ofEpochMilli(e.debut).atZone(if (e.journee) ZoneId.of("UTC") else ZoneId.systemDefault())
        titre = e.titre
        jour = z.toLocalDate()
        debut = z.toLocalTime()
        if (e.fin > e.debut) duree = ((e.fin - e.debut) / 60_000).toInt()
        journee = e.journee
        lieu = e.lieu.orEmpty()
        notes = e.notes.orEmpty()
        regle = e.regle?.substringBefore(';')?.takeIf { r -> REPETITIONS.any { it.first == r } }
        rappel = Agendas.rappel(c, id)
    }
    BackHandler(onBack = fermer)
    EcranAppli(Modifier.imePadding()) {
        Tete(if (v.id == null) "Nouvel événement" else "Événement", retour = fermer, petit = true) {
            BoutonTexteAppli("Enregistrer", style = 't', actif = titre.isNotBlank()) {
                val d = if (journee) jour.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() else ms(jour, debut)
                val f = if (journee) d + 24 * 3600_000L else d + duree * 60_000L
                Agendas.garder(c, v.id, titre.trim(), d, f, journee, lieu.trim(), notes.trim(), regle, rappel)
                fermer()
            }
        }
        val bord = Modifier.padding(horizontal = 20.dp)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicTextField(
                value = titre, onValueChange = { titre = it },
                textStyle = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
                modifier = bord.fillMaxWidth().semantics { contentDescription = "Titre" },
                decorationBox = { champ -> Box { if (titre.isEmpty()) BasicText("Titre", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = a.encre2)); champ() } },
            )
            BasicText("Visible seulement dans cet Espace", modifier = bord, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
            Row(bord, verticalAlignment = Alignment.CenterVertically) {
                BoutonAppli("M15 6l-6 6l6 6", "Jour précédent") { jour = jour.minusDays(1) }
                BasicText(
                    jour.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = a.encre, textAlign = TextAlign.Center),
                )
                BoutonAppli(Icones.CHEVRON, "Jour suivant") { jour = jour.plusDays(1) }
            }
            LigneAppli("Toute la journée", fin = { InterAppli(journee) }) { journee = !journee }
            if (!journee) {
                Row(bord, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonAppli("M5 12h14", "30 minutes plus tôt", style = 'v') { debut = debut.minusMinutes(30) }
                    BasicText(
                        debut.format(DateTimeFormatter.ofPattern("HH:mm")) + " – " + debut.plusMinutes(duree.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")),
                        modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(300), fontSize = 40.sp, color = a.encre, textAlign = TextAlign.Center),
                    )
                    BoutonAppli(Icones.PLUS, "30 minutes plus tard", style = 'v') { debut = debut.plusMinutes(30) }
                }
                FlowRow(bord, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "30 min", 60 to "1 h", 120 to "2 h", 180 to "3 h").forEach { (m, nom) -> PuceFiltre(nom, duree == m) { duree = m } }
                }
            }
            Champ("Lieu", lieu, Icones.BOUSSOLE, bord) { lieu = it }
            Rub("Rappel")
            FlowRow(bord, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { RAPPELS.forEach { (m, nom) -> PuceFiltre(nom, rappel == m) { rappel = m } } }
            Rub("Répétition")
            FlowRow(bord, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { REPETITIONS.forEach { (r, nom) -> PuceFiltre(nom, regle == r) { regle = r } } }
            Champ("Notes", notes, Icones.DOCUMENT, bord) { notes = it }
            if (v.id != null) Box(bord) {
                BoutonTexteAppli("Supprimer l'événement", style = 't', icone = Icones.CORBEILLE) {
                    Agendas.supprimer(c, v.id)
                    fermer()
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun Champ(nom: String, valeur: String, icone: String, modifier: Modifier, changer: (String) -> Unit) {
    val a = LocalIdentite.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(a.champ).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconeTrait(icone, 20.dp, a.encre2)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = valeur, onValueChange = changer,
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
            modifier = Modifier.weight(1f).semantics { contentDescription = nom },
            decorationBox = { champ -> Box { if (valeur.isEmpty()) BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2)); champ() } },
        )
    }
}
