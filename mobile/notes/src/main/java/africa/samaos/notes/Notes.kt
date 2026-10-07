package africa.samaos.notes

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.CaseAppli
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Fab
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.Tete
import africa.samaos.banco.appli.AvecIdentite
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class Element(val texte: String, val coche: Boolean)

data class Note(val id: Long, val titre: String, val texte: String, val liste: List<Element>?, val rappel: Long?, val modifiee: Long)

/** Les notes, gardées sur le téléphone, dans cet Espace. */
object Carnet {
    private fun fichier(c: Context) = File(c.filesDir, "notes.json")

    fun liste(c: Context): List<Note> = try {
        val a = JSONArray(fichier(c).readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Note(
                o.getLong("id"), o.optString("titre"), o.optString("texte"),
                o.optJSONArray("liste")?.let { l -> (0 until l.length()).map { Element(l.getJSONObject(it).getString("t"), l.getJSONObject(it).getBoolean("c")) } },
                o.optLong("rappel").takeIf { it > 0 }, o.optLong("modifiee"),
            )
        }.sortedByDescending { it.modifiee }
    } catch (_: Exception) {
        emptyList()
    }

    private fun ecrire(c: Context, l: List<Note>) {
        val a = JSONArray()
        l.forEach { n ->
            a.put(
                JSONObject().put("id", n.id).put("titre", n.titre).put("texte", n.texte).put("rappel", n.rappel ?: 0).put("modifiee", n.modifiee)
                    .apply { n.liste?.let { put("liste", JSONArray(it.map { e -> JSONObject().put("t", e.texte).put("c", e.coche) })) } },
            )
        }
        fichier(c).writeText(a.toString())
    }

    fun garder(c: Context, n: Note) {
        val vide = n.titre.isBlank() && n.texte.isBlank() && n.liste.orEmpty().all { it.texte.isBlank() }
        val reste = liste(c).filter { it.id != n.id }
        ecrire(c, if (vide) reste else reste + n.copy(modifiee = System.currentTimeMillis()))
        programmer(c, n.takeUnless { vide })
    }

    fun supprimer(c: Context, id: Long) {
        ecrire(c, liste(c).filter { it.id != id })
        annuler(c, id)
    }

    private fun intention(c: Context, id: Long) =
        PendingIntent.getBroadcast(c, id.toInt(), Intent(c, Rappel::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun programmer(c: Context, n: Note?) {
        if (n == null) return
        val r = n.rappel
        if (r == null || r < System.currentTimeMillis()) {
            annuler(c, n.id)
            return
        }
        try {
            c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r, intention(c, n.id))
        } catch (_: SecurityException) {
            c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r, intention(c, n.id))
        }
    }

    fun annuler(c: Context, id: Long) = c.getSystemService(AlarmManager::class.java).cancel(intention(c, id))

    fun quand(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        val auj = java.time.LocalDate.now()
        val jour = when (d.toLocalDate()) {
            auj -> "Aujourd'hui"
            auj.plusDays(1) -> "Demain"
            else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
        }
        return "$jour ${d.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
}

/** Le rappel d'une note : une notification qui l'ouvre. */
class Rappel : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", -1)
        val n = Carnet.liste(c).firstOrNull { it.id == id } ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rappels", "Rappels", NotificationManager.IMPORTANCE_HIGH))
        val ouvrir = PendingIntent.getActivity(c, id.toInt(), Intent(c, Notes::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            id.toInt(),
            Notification.Builder(c, "rappels")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(n.titre.ifBlank { "Rappel" })
                .setContentText(n.texte.ifBlank { n.liste?.filter { !it.coche }?.joinToString(", ") { it.texte }.orEmpty() })
                .setContentIntent(ouvrir)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class Redemarrage : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Carnet.liste(c).forEach { Carnet.programmer(c, it) }
}

/** Les Notes de Sama (maquette l2-notes). */
class Notes : ComponentActivity() {
    private var ouverte by mutableStateOf<Note?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lire(intent)
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.NotesNuit else Identites.Notes
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) {
                val n = ouverte
                if (n != null) Editeur(n) { ouverte = null } else PageNotes { ouverte = it }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    private fun lire(i: Intent?) {
        val id = i?.getLongExtra("id", -1) ?: -1
        when {
            id >= 0 -> ouverte = Carnet.liste(this).firstOrNull { it.id == id }
            i?.action == Intent.ACTION_SEND -> ouverte = Note(System.currentTimeMillis(), i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(), i.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), null, null, 0)
        }
    }
}

@Composable
private fun PageNotes(ouvrir: (Note) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var filtre by remember { mutableIntStateOf(0) }
    var recherche by remember { mutableStateOf("") }
    val notes = remember { Carnet.liste(c) }
    val vues = notes.filter { n ->
        when (filtre) {
            1 -> n.liste != null
            2 -> n.rappel != null
            else -> true
        } && (recherche.isBlank() || (n.titre + " " + n.texte + " " + n.liste.orEmpty().joinToString(" ") { it.texte }).contains(recherche, ignoreCase = true))
    }
    Box(Modifier.fillMaxSize()) {
        EcranAppli {
            Tete("Notes")
            ChampAppli(recherche, "Rechercher dans les notes", { recherche = it })
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Toutes", "Listes", "Rappels").forEachIndexed { i, nom -> PuceFiltre(nom, i == filtre) { filtre = i } }
            }
            if (vues.isEmpty()) {
                BasicText(
                    if (notes.isEmpty()) "Pas encore de note. Une liste de courses, un numéro, une idée : touchez « Nouvelle note »." else "Aucune note ici.",
                    modifier = Modifier.padding(20.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
                )
            }
            LazyVerticalStaggeredGrid(
                StaggeredGridCells.Fixed(2),
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vues, key = { it.id }) { n -> CarteNote(n) { ouvrir(n) } }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding()) {
            Box(Modifier.size(240.dp, 100.dp)) { Fab("Nouvelle note", Icones.PLUS) { ouvrir(Note(System.currentTimeMillis(), "", "", null, null, 0)) } }
        }
    }
}

@Composable
private fun CarteNote(n: Note, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).background(a.surface).clickable(onClickLabel = n.titre.ifBlank { "Note" }, role = Role.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (n.titre.isNotBlank()) BasicText(n.titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, color = a.encre))
        n.liste?.take(6)?.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconeTrait(if (e.coche) "M5 12.5l4.5 4.5L19 7" else "M6 6h12v12H6z", 14.dp, if (e.coche) a.accentTexte else a.encre2)
                BasicText(
                    " " + e.texte, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = if (e.coche) a.encre2 else a.encre, textDecoration = if (e.coche) TextDecoration.LineThrough else null),
                )
            }
        }
        if (n.texte.isNotBlank()) BasicText(n.texte, maxLines = 8, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre))
        n.rappel?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconeTrait(Icones.CLOCHE, 14.dp, a.accentTexte)
                BasicText(" " + Carnet.quand(r), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.accentTexte))
            }
        }
    }
}

@Composable
private fun Editeur(depart: Note, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var titre by remember { mutableStateOf(depart.titre) }
    var texte by remember { mutableStateOf(depart.texte) }
    val elements = remember { mutableStateListOf<Element>().apply { depart.liste?.let { addAll(it) } } }
    var enListe by remember { mutableStateOf(depart.liste != null) }
    var rappel by remember { mutableStateOf(depart.rappel) }
    var choixRappel by remember { mutableStateOf(false) }
    fun garder() = Carnet.garder(c, Note(depart.id, titre, if (enListe) "" else texte, if (enListe) elements.filter { it.texte.isNotBlank() } else null, rappel, 0))
    // Le nouvel élément d'une liste prend le clavier ; Entrée en ajoute un autre.
    var aFocaliser by remember { mutableIntStateOf(-1) }
    BackHandler {
        garder()
        fermer()
    }
    EcranAppli(Modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BoutonAppli("M15 6l-6 6l6 6", "Retour") {
                garder()
                fermer()
            }
            Spacer(Modifier.weight(1f))
            BoutonAppli("M5 7h14 M5 12h14 M5 17h14", if (enListe) "Texte" else "Liste à cocher", style = if (enListe) 'v' else ' ') {
                if (enListe) {
                    texte = elements.joinToString("\n") { it.texte }
                } else {
                    elements.clear()
                    texte.lines().filter { it.isNotBlank() }.forEach { elements += Element(it.trim(), false) }
                    if (elements.isEmpty()) elements += Element("", false)
                }
                enListe = !enListe
            }
            BoutonAppli(Icones.CLOCHE, "Rappel", style = if (rappel != null) 'v' else ' ') { choixRappel = !choixRappel }
            BoutonAppli(Icones.CORBEILLE, "Supprimer") {
                Carnet.supprimer(c, depart.id)
                fermer()
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (choixRappel) {
                val maintenant = LocalDateTime.now()
                val choix = listOf(
                    "Ce soir 19:00" to maintenant.withHour(19).withMinute(0),
                    "Demain 08:00" to maintenant.plusDays(1).withHour(8).withMinute(0),
                    "Dans 1 heure" to maintenant.plusHours(1),
                    "Samedi 09:00" to maintenant.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY)).withHour(9).withMinute(0),
                ).filter { it.second.isAfter(maintenant) }
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choix.take(3).forEach { (nom, t) ->
                        PuceFiltre(nom, false) {
                            rappel = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            choixRappel = false
                        }
                    }
                }
                if (rappel != null) BoutonTexteAppli("Retirer le rappel", style = 't') {
                    rappel = null
                    choixRappel = false
                }
            }
            rappel?.let { BasicText("Rappel : " + Carnet.quand(it), modifier = Modifier.padding(bottom = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.accentTexte)) }
            BasicTextField(
                value = titre, onValueChange = { titre = it },
                textStyle = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Titre" },
                decorationBox = { champ -> Box { if (titre.isEmpty()) BasicText("Titre", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = a.encre2)); champ() } },
            )
            Spacer(Modifier.height(12.dp))
            if (enListe) {
                elements.forEachIndexed { i, e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clickable(role = Role.Checkbox) { elements[i] = Element(e.texte, !e.coche) }) { CaseAppli(e.coche) }
                        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                        androidx.compose.runtime.LaunchedEffect(aFocaliser) { if (aFocaliser == i) focus.requestFocus() }
                        BasicTextField(
                            value = e.texte, onValueChange = { elements[i] = Element(it, e.coche) }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = {
                                elements.add(i + 1, Element("", false))
                                aFocaliser = i + 1
                            }),
                            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = if (e.coche) a.encre2 else a.encre, textDecoration = if (e.coche) TextDecoration.LineThrough else null),
                            cursorBrush = SolidColor(a.accent),
                            modifier = Modifier.weight(1f).padding(start = 12.dp).focusRequester(focus).semantics { contentDescription = "Élément ${i + 1}" },
                        )
                        BoutonAppli(Icones.FERMER, "Retirer", taille = 36.dp) { elements.removeAt(i) }
                    }
                }
                BoutonTexteAppli("Ajouter un élément", style = 't', icone = Icones.PLUS) {
                    elements += Element("", false)
                    aFocaliser = elements.lastIndex
                }
            } else {
                BasicTextField(
                    value = texte, onValueChange = { texte = it },
                    textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 25.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Note" },
                    decorationBox = { champ -> Box { if (texte.isEmpty()) BasicText("Écrire…", style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre2)); champ() } },
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
