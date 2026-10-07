package africa.samaos.horloge

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.NavAppli
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import africa.samaos.banco.appli.AvecIdentite
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Onglet(val nom: String, val icone: String) {
    ALARMES("Alarmes", "M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 9v4l2 2 M5 3L2 6 M19 3l3 3"),
    HORLOGE("Horloge", Icones.GLOBE),
    MINUTEUR("Minuteur", "M10 2h4 M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 13V9"),
    CHRONO("Chrono", "M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 13l3-3 M10 2h4"),
    COUCHER("Coucher", Icones.NUIT),
}

private val heureGrande = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(250))

/** L'Horloge de Sama (maquettes l2-hor). */
class Horloge : ComponentActivity() {
    private var onglet by mutableStateOf(Onglet.ALARMES)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Alarmes.canal(this)
        lire(intent)
        setContent {
            AvecIdentite(Identites.Horloge) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (onglet) {
                            Onglet.ALARMES -> PageAlarmes()
                            Onglet.HORLOGE -> PageMonde()
                            Onglet.MINUTEUR -> PageMinuteur()
                            Onglet.CHRONO -> PageChrono()
                            Onglet.COUCHER -> PageCoucher()
                        }
                    }
                    NavAppli(Onglet.entries.map { it.nom to it.icone }, onglet.ordinal) { onglet = Onglet.entries[it] }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    /** « Mettre une alarme à 6 h 30 » ou « un minuteur de 5 minutes » demandés par une autre appli. */
    private fun lire(i: Intent?) {
        when (i?.action) {
            AlarmClock.ACTION_SET_ALARM -> {
                onglet = Onglet.ALARMES
                val h = i.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
                if (h >= 0) {
                    Alarmes.garder(this, Alarme(Alarmes.nouvelId(this), h, i.getIntExtra(AlarmClock.EXTRA_MINUTES, 0), i.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty(), emptySet(), true))
                }
            }
            AlarmClock.ACTION_SHOW_ALARMS -> onglet = Onglet.ALARMES
            AlarmClock.ACTION_SET_TIMER -> {
                onglet = Onglet.MINUTEUR
                val s = i.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
                if (s > 0) Alarmes.lancerMinuteur(this, i.getStringExtra(AlarmClock.EXTRA_MESSAGE) ?: "Minuteur", s * 1000L)
            }
        }
    }
}

private val JOURS = listOf(DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "M", DayOfWeek.THURSDAY to "J", DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S", DayOfWeek.SUNDAY to "D")

// ——— Alarmes (maquette l2-hor-alarmes) ———

@Composable
private fun PageAlarmes() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var edition by remember { mutableStateOf<Alarme?>(null) }
    val alarmes = remember(version) { Alarmes.liste(c) }
    val e = edition
    if (e != null) {
        EditeurAlarme(e, fermer = { edition = null; version++ })
        return
    }
    EcranAppli {
        Tete("Alarmes") {
            BoutonAppli(Icones.PLUS, "Nouvelle alarme", style = 'v') {
                edition = Alarme(Alarmes.nouvelId(c), 6, 0, "", emptySet(), true)
            }
        }
        val prochaine = alarmes.filter { it.active }.minByOrNull { Alarmes.prochaine(it).toInstant() }
        BasicText(
            prochaine?.let { "Prochaine " + Alarmes.dans(Alarmes.prochaine(it)) } ?: "Aucune alarme allumée",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2),
        )
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(alarmes, key = { it.id }) { al ->
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(a.surface)
                        .clickable(onClickLabel = "Modifier l'alarme", role = Role.Button) { edition = al }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText("%02d:%02d".format(al.heure, al.minute), style = heureGrande.copy(fontSize = 52.sp, lineHeight = 56.sp, color = if (al.active) a.encre else a.encre2))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (al.nom.isNotBlank()) BasicText(al.nom + "  ", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
                            JOURS.forEach { (j, l) ->
                                BasicText(l, style = TextStyle(fontFamily = Polices.corps, fontWeight = if (j in al.jours) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, color = if (j in al.jours) a.accent else a.encre2.copy(alpha = 0.6f)))
                            }
                        }
                    }
                    Box(Modifier.clickable(onClickLabel = if (al.active) "Éteindre" else "Allumer", role = Role.Switch) {
                        Alarmes.garder(c, al.copy(active = !al.active))
                        version++
                    }) { InterAppli(al.active) }
                }
            }
            if (alarmes.isEmpty()) item {
                BasicText("Touchez + pour mettre une alarme.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
            }
        }
    }
}

/** Choisir une heure : les heures et les minutes, chacune avec ses flèches. */
@Composable
fun ChoixHeure(heure: Int, minute: Int, pasMinutes: Int = 5, changer: (Int, Int) -> Unit) {
    val a = LocalIdentite.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Molette("%02d".format(heure), "heures", { changer((heure + 1) % 24, minute) }) { changer((heure + 23) % 24, minute) }
        BasicText(":", style = heureGrande.copy(fontSize = 72.sp, color = a.encre))
        Molette("%02d".format(minute), "minutes", { changer(heure, (minute + pasMinutes) % 60) }) { changer(heure, (minute + 60 - pasMinutes) % 60) }
    }
}

@Composable
private fun Molette(valeur: String, quoi: String, plus: () -> Unit, moins: () -> Unit) {
    val a = LocalIdentite.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoutonAppli("M6 15l6-6l6 6", "Plus de $quoi", onClick = plus)
        BasicText(valeur, style = heureGrande.copy(fontSize = 72.sp, lineHeight = 76.sp, color = a.encre))
        BoutonAppli("M6 9l6 6l6-6", "Moins de $quoi", onClick = moins)
    }
}

@Composable
private fun EditeurAlarme(depart: Alarme, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var al by remember { mutableStateOf(depart) }
    BackHandler(onBack = fermer)
    EcranAppli {
        Tete(if (Alarmes.liste(c).any { it.id == al.id }) "Modifier l'alarme" else "Nouvelle alarme", retour = fermer, petit = true) {
            BoutonTexteAppli("Garder", style = 't') {
                Alarmes.garder(c, al.copy(active = true))
                fermer()
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoixHeure(al.heure, al.minute) { h, m -> al = al.copy(heure = h, minute = m) }
            BasicText(Alarmes.dans(Alarmes.prochaine(al)).replaceFirstChar { it.uppercase() }, modifier = Modifier.fillMaxWidth(), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2, textAlign = TextAlign.Center))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                JOURS.forEach { (j, l) ->
                    val choisi = j in al.jours
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(if (choisi) a.accent else a.champ)
                            .clickable(onClickLabel = j.getDisplayName(java.time.format.TextStyle.FULL, Locale.FRENCH), role = Role.Checkbox) {
                                al = al.copy(jours = if (choisi) al.jours - j else al.jours + j)
                            },
                        contentAlignment = Alignment.Center,
                    ) { BasicText(l, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (choisi) a.surAccent else a.encre)) }
                }
            }
            BasicText(if (al.jours.isEmpty()) "Une seule fois" else if (al.jours.size == 7) "Tous les jours" else "Les jours choisis", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(a.champ).padding(horizontal = 16.dp, vertical = 10.dp)) {
                BasicText("Nom", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = a.encre2))
                BasicTextField(
                    value = al.nom, onValueChange = { al = al.copy(nom = it.take(40)) }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nom de l'alarme" },
                )
            }
            if (Alarmes.liste(c).any { it.id == al.id }) {
                BoutonTexteAppli("Supprimer l'alarme", style = 't', icone = Icones.CORBEILLE) {
                    Alarmes.supprimer(c, al.id)
                    fermer()
                }
            }
        }
    }
}

// ——— Horloge du monde (maquette l2-hor-monde) ———

private val VILLES = listOf(
    "Africa/Dakar" to "Dakar", "Africa/Lagos" to "Lagos", "Africa/Accra" to "Accra", "Africa/Bamako" to "Bamako",
    "Europe/Paris" to "Paris", "Africa/Nairobi" to "Nairobi", "America/Montreal" to "Montréal", "America/New_York" to "New York",
    "Asia/Dubai" to "Dubaï", "Asia/Shanghai" to "Pékin", "Africa/Casablanca" to "Casablanca", "Africa/Johannesburg" to "Johannesburg",
)

@Composable
private fun PageMonde() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val prefs = remember { c.getSharedPreferences("horloge", 0) }
    var villes by remember { mutableStateOf(prefs.getString("villes", "Africa/Dakar,Africa/Lagos,Europe/Paris,Africa/Nairobi,America/Montreal")!!.split(',').filter { it.isNotBlank() }) }
    var ajout by remember { mutableStateOf(false) }
    val maintenant = rememberMaintenant()
    fun garder(l: List<String>) {
        villes = l
        prefs.edit().putString("villes", l.joinToString(",")).apply()
    }
    EcranAppli {
        Tete("Horloge") { BoutonAppli(Icones.PLUS, "Ajouter une ville", style = 'v') { ajout = !ajout } }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                BasicText(maintenant.format(DateTimeFormatter.ofPattern("HH:mm")), style = heureGrande.copy(fontSize = 88.sp, lineHeight = 92.sp, color = a.encre))
                BasicText(
                    ZoneId.systemDefault().id.substringAfter('/').replace('_', ' ') + " · " + maintenant.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2),
                )
            }
            if (ajout) {
                Rub("Ajouter")
                VILLES.filter { it.first !in villes }.forEach { (z, nom) ->
                    LigneAppli(nom, fin = { IconeTrait(Icones.PLUS, 20.dp, a.accent) }) {
                        garder(villes + z)
                        ajout = false
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            villes.forEach { z ->
                val nom = VILLES.firstOrNull { it.first == z }?.second ?: z.substringAfter('/')
                val la = maintenant.withZoneSameInstant(ZoneId.of(z))
                val ecart = (la.offset.totalSeconds - maintenant.offset.totalSeconds) / 3600
                val jour = when {
                    la.toLocalDate().isAfter(maintenant.toLocalDate()) -> " · demain"
                    la.toLocalDate().isBefore(maintenant.toLocalDate()) -> " · hier"
                    else -> ""
                }
                LigneAppli(
                    nom,
                    (if (ecart == 0) "Même heure" else if (ecart > 0) "+$ecart h" else "−${-ecart} h") + jour,
                    fin = { BasicText(la.format(DateTimeFormatter.ofPattern("HH:mm")), style = heureGrande.copy(fontSize = 36.sp, color = a.encre)) },
                    surAppuiLong = { garder(villes - z) },
                )
            }
            BasicText("Un appui long sur une ville la retire.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
        }
    }
}

@Composable
private fun rememberMaintenant(): ZonedDateTime {
    var t by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            t = ZonedDateTime.now()
            delay(1000)
        }
    }
    return t
}

// ——— Minuteur (maquette l2-hor-minuteur) ———

private val PRETS = listOf("Thé" to 3, "Riz" to 20, "Œufs" to 9, "Sieste" to 25, "Lessive" to 45, "Pause" to 5)

@Composable
private fun PageMinuteur() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            maintenant = System.currentTimeMillis()
            delay(250)
        }
    }
    val fin = remember(version) { Alarmes.finMinuteur(c) }
    val pause = remember(version) { Alarmes.pauseRestante(c) }
    val reste = fin?.let { (it - maintenant).coerceAtLeast(0) } ?: pause
    var minutes by remember { mutableIntStateOf(10) }
    EcranAppli {
        Tete("Minuteur")
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (reste != null) {
                val s = (reste + 999) / 1000
                BasicText(if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60), style = heureGrande.copy(fontSize = 96.sp, lineHeight = 100.sp, color = a.encre))
                BasicText(
                    Alarmes.nomMinuteur(c) + (fin?.let { " sonne à " + java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: " · en pause"),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2),
                )
                Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BoutonTexteAppli("+1 min", style = 's') {
                        Alarmes.lancerMinuteur(c, Alarmes.nomMinuteur(c), reste + 60_000)
                        version++
                    }
                    BoutonAppli(if (fin != null) Icones.PAUSE else Icones.LECTURE, if (fin != null) "Pause" else "Reprendre", style = 'a', taille = 64.dp) {
                        if (fin != null) Alarmes.pauseMinuteur(c) else Alarmes.lancerMinuteur(c, Alarmes.nomMinuteur(c), reste)
                        version++
                    }
                    BoutonAppli(Icones.FERMER, "Arrêter", style = 'v') {
                        Alarmes.arreterMinuteur(c)
                        version++
                    }
                }
            } else {
                Molette("$minutes min", "minutes", { minutes = (minutes + 1).coerceAtMost(180) }) { minutes = (minutes - 1).coerceAtLeast(1) }
                Spacer(Modifier.height(12.dp))
                BoutonTexteAppli("Lancer", icone = Icones.LECTURE) {
                    Alarmes.lancerMinuteur(c, "Minuteur", minutes * 60_000L)
                    version++
                }
            }
        }
        Rub("Minuteurs prêts")
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRETS.forEach { (nom, m) -> PuceFiltre("$nom · $m min", false) { Alarmes.lancerMinuteur(c, nom, m * 60_000L); version++ } }
        }
    }
}

// ——— Chrono ———

@Composable
private fun PageChrono() {
    val a = LocalIdentite.current
    var depart by remember { mutableLongStateOf(0L) }
    var cumul by remember { mutableLongStateOf(0L) }
    var marche by remember { mutableStateOf(false) }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val tours = remember { mutableStateListOf<Long>() }
    LaunchedEffect(marche) {
        while (marche) {
            maintenant = System.currentTimeMillis()
            delay(30)
        }
    }
    val total = cumul + if (marche) maintenant - depart else 0
    fun texte(ms: Long) = "%02d:%02d,%02d".format(ms / 60_000, (ms / 1000) % 60, (ms / 10) % 100)
    EcranAppli {
        Tete("Chrono")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(texte(total), style = heureGrande.copy(fontSize = 84.sp, lineHeight = 90.sp, color = a.encre))
            Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BoutonTexteAppli(if (marche) "Tour" else "Remettre à zéro", style = 's', actif = marche || total > 0) {
                    if (marche) tours.add(0, total) else {
                        cumul = 0
                        tours.clear()
                    }
                }
                BoutonAppli(if (marche) Icones.PAUSE else Icones.LECTURE, if (marche) "Arrêter" else "Démarrer", style = 'a', taille = 64.dp) {
                    if (marche) {
                        cumul += System.currentTimeMillis() - depart
                        marche = false
                    } else {
                        depart = System.currentTimeMillis()
                        maintenant = depart
                        marche = true
                    }
                }
            }
        }
        LazyColumn {
            items(tours.size) { i ->
                val t = tours[i]
                val precedent = tours.getOrNull(i + 1) ?: 0
                LigneAppli("Tour ${tours.size - i}", "+" + texte(t - precedent), fin = { BasicText(texte(t), style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre)) })
            }
        }
    }
}

// ——— Coucher (maquette l2-hor-coucher) ———

@Composable
private fun PageCoucher() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var coucher by remember { mutableIntStateOf(Alarmes.coucher(c)) }
    var lever by remember { mutableIntStateOf(Alarmes.lever(c)) }
    var edition by remember { mutableStateOf<String?>(null) }
    val duree = ((lever - coucher + 24 * 60) % (24 * 60))
    val alarmeLever = Alarmes.liste(c).firstOrNull { it.nom == "Lever" }
    EcranAppli {
        Tete("Coucher")
        Column(Modifier.verticalScroll(rememberScrollState())) {
            BasicText(
                "${duree / 60} h ${"%02d".format(duree % 60)} de sommeil",
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = a.encre, textAlign = TextAlign.Center),
            )
            if (edition != null) {
                val v = if (edition == "coucher") coucher else lever
                ChoixHeure(v / 60, v % 60) { h, m ->
                    if (edition == "coucher") {
                        coucher = h * 60 + m
                        Alarmes.reglerCoucher(c, coucher)
                    } else {
                        lever = h * 60 + m
                        Alarmes.reglerLever(c, lever)
                        alarmeLever?.let { Alarmes.garder(c, it.copy(heure = h, minute = m)) }
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BoutonTexteAppli("Terminé", style = 's') { edition = null } }
            }
            LigneAppli("Coucher", "%02d:%02d".format(coucher / 60, coucher % 60), debut = { IconeTrait(Icones.NUIT, 24.dp, a.accent) }) { edition = "coucher" }
            LigneAppli("Lever", "%02d:%02d".format(lever / 60, lever % 60) + if (alarmeLever?.active == true) " · alarme" else "", debut = { IconeTrait(Icones.SOLEIL, 24.dp, a.accent) }) { edition = "lever" }
            LigneAppli("Alarme au lever", "Tous les jours", fin = { InterAppli(alarmeLever?.active == true) }) {
                if (alarmeLever == null) {
                    Alarmes.garder(c, Alarme(Alarmes.nouvelId(c), lever / 60, lever % 60, "Lever", DayOfWeek.entries.toSet(), true))
                } else {
                    Alarmes.garder(c, alarmeLever.copy(active = !alarmeLever.active))
                }
                edition = null
            }
            BasicText(
                "Pour passer en Nuit et en silence au coucher, Sama suit l'heure de la Nuit des Réglages. Les favoris peuvent toujours appeler.",
                modifier = Modifier.padding(20.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
            )
        }
    }
}

// ——— Ce qui sonne (maquette l2-hor-sonne) ———

/** L'alarme ou le minuteur qui sonne : Arrêter, ou Encore 10 min. */
class Sonnerie : ComponentActivity() {
    private var sonnerie: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val titre = intent.getStringExtra("titre") ?: "Alarme"
        val minuteur = intent.getBooleanExtra("minuteur", false)
        sonner()
        setContent {
            AvecIdentite(Identites.Horloge) {
                val a = LocalIdentite.current
                val maintenant = rememberMaintenant()
                DisposableEffect(Unit) { onDispose { arreterSon() } }
                Column(Modifier.fillMaxSize().background(a.fond).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = a.encre2))
                    BasicText(maintenant.format(DateTimeFormatter.ofPattern("HH:mm")), style = heureGrande.copy(fontSize = 120.sp, lineHeight = 124.sp, color = a.encre))
                    BasicText(maintenant.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }, style = TextStyle(fontFamily = Polices.corps, fontSize = 18.sp, color = a.encre2))
                    Spacer(Modifier.weight(1.4f))
                    Box(
                        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(a.accent).clickable(onClickLabel = "Arrêter", role = Role.Button) { fermer() },
                        contentAlignment = Alignment.Center,
                    ) { BasicText("Arrêter", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = a.surAccent)) }
                    Spacer(Modifier.height(14.dp))
                    if (!minuteur) {
                        Box(
                            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(a.champ).clickable(onClickLabel = "Encore 10 minutes", role = Role.Button) {
                                Alarmes.reporter(this@Sonnerie, 10)
                                fermer()
                            },
                            contentAlignment = Alignment.Center,
                        ) { BasicText("Encore 10 min", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = a.encre)) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    private fun sonner() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            sonnerie = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                isLooping = true
                play()
            }
        } catch (_: Exception) {
        }
        try {
            getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0))
        } catch (_: Exception) {
        }
    }

    private fun arreterSon() {
        sonnerie?.stop()
        getSystemService(Vibrator::class.java)?.cancel()
    }

    private fun fermer() {
        arreterSon()
        getSystemService(NotificationManager::class.java).cancel(9)
        finish()
    }
}
