package africa.samaos.horloge

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Glissable
import africa.samaos.banco.appli.Avis
import java.text.Normalizer
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Une ville de l'horloge du monde : son nom, son fuseau, sa région (pour la liste d'ajout). */
class Ville(val nom: String, val zone: String, val region: String)

private val CATALOGUE = listOf(
    Ville("Abidjan", "Africa/Abidjan", "Afrique"), Ville("Yamoussoukro", "Africa/Abidjan", "Afrique"), Ville("Dakar", "Africa/Dakar", "Afrique"),
    Ville("Bamako", "Africa/Bamako", "Afrique"), Ville("Ouagadougou", "Africa/Ouagadougou", "Afrique"), Ville("Niamey", "Africa/Niamey", "Afrique"),
    Ville("Accra", "Africa/Accra", "Afrique"), Ville("Lomé", "Africa/Lome", "Afrique"), Ville("Cotonou", "Africa/Porto-Novo", "Afrique"),
    Ville("Conakry", "Africa/Conakry", "Afrique"), Ville("Nouakchott", "Africa/Nouakchott", "Afrique"), Ville("Lagos", "Africa/Lagos", "Afrique"),
    Ville("Abuja", "Africa/Lagos", "Afrique"), Ville("Yaoundé", "Africa/Douala", "Afrique"), Ville("Douala", "Africa/Douala", "Afrique"),
    Ville("Libreville", "Africa/Libreville", "Afrique"), Ville("Kinshasa", "Africa/Kinshasa", "Afrique"), Ville("Brazzaville", "Africa/Brazzaville", "Afrique"),
    Ville("Kigali", "Africa/Kigali", "Afrique"), Ville("Kampala", "Africa/Kampala", "Afrique"), Ville("Nairobi", "Africa/Nairobi", "Afrique"),
    Ville("Addis-Abeba", "Africa/Addis_Ababa", "Afrique"), Ville("Le Caire", "Africa/Cairo", "Afrique"), Ville("Casablanca", "Africa/Casablanca", "Afrique"),
    Ville("Tunis", "Africa/Tunis", "Afrique"), Ville("Alger", "Africa/Algiers", "Afrique"), Ville("Johannesburg", "Africa/Johannesburg", "Afrique"),
    Ville("Paris", "Europe/Paris", "Europe"), Ville("Bruxelles", "Europe/Brussels", "Europe"), Ville("Genève", "Europe/Zurich", "Europe"),
    Ville("Londres", "Europe/London", "Europe"), Ville("Lisbonne", "Europe/Lisbon", "Europe"), Ville("Rome", "Europe/Rome", "Europe"),
    Ville("Istanbul", "Europe/Istanbul", "Europe"), Ville("Montréal", "America/Toronto", "Amériques"), Ville("New York", "America/New_York", "Amériques"),
    Ville("Washington", "America/New_York", "Amériques"), Ville("São Paulo", "America/Sao_Paulo", "Amériques"), Ville("Port-au-Prince", "America/Port-au-Prince", "Amériques"),
    Ville("Dubaï", "Asia/Dubai", "Asie"), Ville("Riyad", "Asia/Riyadh", "Asie"), Ville("Beyrouth", "Asia/Beirut", "Asie"),
    Ville("Pékin", "Asia/Shanghai", "Asie"), Ville("Tokyo", "Asia/Tokyo", "Asie"), Ville("Bombay", "Asia/Kolkata", "Asie"),
)

private const val PAR_DEFAUT = "Dakar|Africa/Dakar,Lagos|Africa/Lagos,Paris|Europe/Paris,Nairobi|Africa/Nairobi,Montréal|America/Toronto"

private fun villes(c: Context): List<Ville> {
    val p = c.getSharedPreferences("horloge", 0)
    // Les anciennes listes ne gardaient que le fuseau : le nom vient du catalogue.
    val ancien = p.getString("villes", null)?.split(',')?.filter { it.isNotBlank() }?.joinToString(",") { z ->
        val nom = CATALOGUE.firstOrNull { it.zone == z }?.nom ?: z.substringAfter('/').replace('_', ' ')
        "$nom|${if (z == "America/Montreal") "America/Toronto" else z}"
    }
    return (p.getString("villes2", null) ?: ancien ?: PAR_DEFAUT).split(',').mapNotNull { e ->
        val (nom, zone) = e.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
        Ville(nom, zone, CATALOGUE.firstOrNull { it.zone == zone }?.region.orEmpty())
    }
}

private fun garderVilles(c: Context, l: List<Ville>) {
    c.getSharedPreferences("horloge", 0).edit().putString("villes2", l.joinToString(",") { "${it.nom}|${it.zone}" }).apply()
    WidgetsHorloge.annoncer(c)
}

/** Les villes de l'horloge du monde, pour les widgets. */
internal fun villesDuMonde(c: Context) = villes(c)

private fun simple(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

/** « Même heure », « +1 h · demain », « −4 h ». */
private fun ecart(ici: ZonedDateTime, la: ZonedDateTime): String {
    val minutes = (la.offset.totalSeconds - ici.offset.totalSeconds) / 60
    val h = minutes / 60
    val m = Math.abs(minutes % 60)
    val decalage = when {
        minutes == 0 -> "Même heure"
        minutes > 0 -> "+$h h" + if (m > 0) " $m" else ""
        else -> "−${-h} h" + if (m > 0) " $m" else ""
    }
    val jour = when {
        la.toLocalDate().isAfter(ici.toLocalDate()) -> " · demain"
        la.toLocalDate().isBefore(ici.toLocalDate()) -> " · hier"
        else -> ""
    }
    return decalage + jour
}

@Composable
fun PageMonde() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var liste by remember { mutableStateOf(villes(c)) }
    var ajout by remember { mutableStateOf(false) }
    val maintenant = rememberMaintenant(1000)
    fun garder(l: List<Ville>) {
        liste = l
        garderVilles(c, l)
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        Entete("Horloge") { BoutonRond("Ajouter une ville", a.surface, a.accent, 52.dp, icone = Icones.PLUS) { ajout = true } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "cadran") {
                Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cadran(240.dp)
                    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
                        TexteRoulant(maintenant.format(DateTimeFormatter.ofPattern("HH:mm")), CHIFFRES.copy(fontSize = 64.sp, lineHeight = 68.sp, color = a.encre))
                        TexteRoulant(maintenant.format(DateTimeFormatter.ofPattern("ss")), CHIFFRES.copy(fontSize = 26.sp, lineHeight = 40.sp, color = a.accent), Modifier.padding(start = 6.dp, bottom = 8.dp))
                    }
                    BasicText(
                        ZoneId.systemDefault().id.substringAfter('/').replace('_', ' ') + " · " + maintenant.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)),
                        style = texte(15f, a.encre2),
                    )
                }
            }
            items(liste, key = { it.nom + it.zone }) { v ->
                Glissable(
                    supprimer = {
                        val avant = liste
                        garder(liste - v)
                        Avis.dire("${v.nom} retirée") { garder(avant) }
                    },
                    modifier = Modifier.animateItem(),
                ) { CarteVille(v, maintenant) }
            }
            if (liste.isNotEmpty()) item(key = "aide") {
                BasicText("Glissez une ville vers la gauche pour la retirer.", modifier = Modifier.padding(6.dp).animateItem(), style = texte(13f, a.encre2))
            }
            item(key = "pied") { Pied() }
        }
        }
        if (ajout) {
            FeuilleVilles(liste, fermer = { ajout = false }) { v ->
                garder(liste + v)
                Avis.dire("${v.nom} ajoutée")
            }
        }
    }
}

@Composable
private fun CarteVille(v: Ville, ici: ZonedDateTime) {
    val a = LocalIdentite.current
    val la = ici.withZoneSameInstant(ZoneId.of(v.zone))
    val jour = la.hour in 6..17
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(a.surface).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(v.nom, style = texte(18f, a.encre, FontWeight.SemiBold))
            BasicText(ecart(ici, la), style = texte(14f, a.encre2))
        }
        IconeTrait(if (jour) Icones.SOLEIL else Icones.NUIT, 20.dp, if (jour) AUBE else LUNE, Modifier.padding(end = 10.dp))
        TexteRoulant(la.format(DateTimeFormatter.ofPattern("HH:mm")), CHIFFRES.copy(fontSize = 40.sp, lineHeight = 44.sp, color = a.encre))
    }
}

/** Le cadran : les aiguilles arrivent de midi jusqu'à l'heure, puis la trotteuse tourne sans à-coups. */
@Composable
fun Cadran(taille: Dp, modifier: Modifier = Modifier) {
    val a = LocalIdentite.current
    var nanos by remember { mutableLongStateOf(LocalTime.now().toNanoOfDay()) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { nanos = LocalTime.now().toNanoOfDay() }
    }
    val entree = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entree.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    val clair = lerp(a.surface, a.encre, 0.08f)
    Canvas(modifier.size(taille).semantics { contentDescription = "Cadran de l'horloge" }) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(Brush.radialGradient(listOf(clair, a.surface), c, r), r, c)
        drawCircle(a.trait, r, c, style = Stroke(1.dp.toPx()))
        for (i in 0 until 60) {
            val gros = i % 5 == 0
            val ang = Math.toRadians(i * 6.0)
            val r1 = r * 0.91f
            val r0 = if (gros) r * 0.80f else r * 0.87f
            drawLine(
                if (gros) a.encre else a.encre2.copy(alpha = 0.35f),
                Offset(c.x + r0 * sin(ang).toFloat(), c.y - r0 * cos(ang).toFloat()),
                Offset(c.x + r1 * sin(ang).toFloat(), c.y - r1 * cos(ang).toFloat()),
                strokeWidth = if (gros) 3.dp.toPx() else 1.3.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        val t = nanos / 1e9
        val p = entree.value
        fun aiguille(deg: Double, long: Float, epais: Dp, couleur: androidx.compose.ui.graphics.Color, queue: Float = 0f) {
            val ang = Math.toRadians(deg * p)
            val dx = sin(ang).toFloat()
            val dy = -cos(ang).toFloat()
            drawLine(couleur, Offset(c.x - dx * queue, c.y - dy * queue), Offset(c.x + dx * long, c.y + dy * long), strokeWidth = epais.toPx(), cap = StrokeCap.Round)
        }
        aiguille((t / 3600) % 12 * 30, r * 0.5f, 6.dp, a.encre)
        aiguille((t / 60) % 60 * 6, r * 0.72f, 4.dp, a.encre)
        aiguille(t % 60 * 6, r * 0.82f, 1.8.dp, a.accent, r * 0.16f)
        drawCircle(a.accent, 6.dp.toPx(), c)
        drawCircle(a.surface, 2.5.dp.toPx(), c)
    }
}

/** Ajouter une ville : on la cherche par son nom, on la touche. */
@Composable
private fun FeuilleVilles(deja: List<Ville>, fermer: () -> Unit, choisir: (Ville) -> Unit) {
    val a = LocalIdentite.current
    var recherche by remember { mutableStateOf("") }
    val instant = rememberMaintenant(10_000)
    FeuilleAppli(fermer = fermer, titre = "Ajouter une ville") {
        val fermerFeuille = LocalFermerFeuille.current
        ChampAppli(recherche, "Nom de la ville", { recherche = it })
        val ici = ZoneId.systemDefault().id.substringAfter('/').replace('_', ' ')
        val dispo = CATALOGUE.filter { v -> v.nom != ici && deja.none { it.nom == v.nom } && (recherche.isBlank() || simple(v.nom).contains(simple(recherche.trim()))) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
            dispo.groupBy { it.region }.forEach { (region, l) ->
                item(key = "r$region") { BasicText(region, modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp), style = texte(14f, a.encre2, FontWeight.SemiBold)) }
                items(l, key = { it.nom }) { v ->
                    val la = instant.withZoneSameInstant(ZoneId.of(v.zone))
                    Row(
                        Modifier.fillMaxWidth().clickable(onClickLabel = "Ajouter ${v.nom}", role = Role.Button) {
                            choisir(v)
                            fermerFeuille()
                        }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            BasicText(v.nom, style = texte(16.5f, a.encre))
                            BasicText(ecart(instant, la), style = texte(13f, a.encre2))
                        }
                        BasicText(la.format(DateTimeFormatter.ofPattern("HH:mm")), style = CHIFFRES.copy(fontSize = 26.sp, color = a.encre2))
                    }
                }
            }
            if (dispo.isEmpty()) item {
                BasicText("Aucune ville de ce nom dans la liste.", modifier = Modifier.padding(24.dp), style = texte(15f, a.encre2))
            }
        }
    }
}
