package africa.samaos.meteo

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.BarreRetour
import africa.samaos.banco.appli.GrandTitre
import africa.samaos.banco.appli.Rond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.TextStyle as StyleDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** La Météo de Sama (maquette l2-meteo). */
class Meteo : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val nuit = isSystemInDarkTheme()
            val id = if (nuit) Identites.MeteoNuit else Identites.Meteo
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) { Ecran(nuit) }
        }
    }
}

@Composable
private fun Ecran(nuit: Boolean) {
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var villes by remember { mutableStateOf(MoteurMeteo.villes(c)) }
    var active by remember { mutableIntStateOf(MoteurMeteo.active(c)) }
    var ajout by remember { mutableStateOf(villes.isEmpty()) }
    var menu by remember { mutableStateOf(false) }
    var aPropos by remember { mutableStateOf(false) }
    val ville = villes.getOrNull(active) ?: villes.firstOrNull()
    var prev by remember { mutableStateOf<Previsions?>(null) }
    var chargement by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var demande by remember { mutableIntStateOf(0) }

    fun recharger() {
        villes = MoteurMeteo.villes(c)
        active = MoteurMeteo.active(c)
    }

    LaunchedEffect(ville?.cle, demande) {
        val v = ville ?: return@LaunchedEffect
        val gardee = withContext(Dispatchers.IO) { MoteurMeteo.gardees(c, v) }
        prev = gardee
        erreur = null
        if (gardee != null && demande == 0 && System.currentTimeMillis() - gardee.recues < MoteurMeteo.FRAICHEUR) return@LaunchedEffect
        chargement = true
        try {
            prev = withContext(Dispatchers.IO) { MoteurMeteo.actualiser(c, v) }
        } catch (_: Exception) {
            erreur = if (gardee != null) "Pas de réseau : prévisions gardées" else "Pas de réseau : les prévisions viendront avec la connexion."
        }
        chargement = false
    }

    val t = remember(nuit) { Teintes(nuit) }
    // Les dernières prévisions de chaque ville, pour la page des villes.
    var gardees by remember { mutableStateOf<Map<String, Previsions?>>(emptyMap()) }
    LaunchedEffect(menu, villes, prev) {
        if (menu) gardees = withContext(Dispatchers.IO) { villes.associate { v -> v.cle to ((if (v.cle == ville?.cle) prev else null) ?: MoteurMeteo.gardees(c, v)) } }
    }
    fun partager() {
        val v = ville ?: return
        val p = prev ?: return
        val texte = phrasePartage(v, p)
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texte), "Partager la météo"))
    }
    Box(Modifier.fillMaxSize().ciel(nuit)) {
        if (ville != null) {
            PageVille(
                ville = ville,
                prev = prev,
                chargement = chargement,
                erreur = erreur,
                t = t,
                menu = { menu = true },
            )
        }
        if (menu && ville != null) {
            PageVilles(
                villes = villes, active = active, previsions = gardees, t = t,
                fermer = { menu = false },
                choisir = { i ->
                    MoteurMeteo.choisir(c, i)
                    active = i
                    menu = false
                },
                retirer = { v ->
                    MoteurMeteo.retirer(c, v)
                    recharger()
                },
                ajouter = {
                    menu = false
                    ajout = true
                },
                actualiser = {
                    menu = false
                    demande++
                },
                partager = { partager() },
                aPropos = { aPropos = true },
            )
        }
        if (aPropos) {
            FeuilleAppli(fermer = { aPropos = false }, titre = "D'où viennent les prévisions") {
                val a = LocalIdentite.current
                BasicText(
                    "Les prévisions viennent d'Open-Meteo, un service ouvert, sans compte ni publicité. Seule la ville choisie part, " +
                        "ou votre position arrondie à une dizaine de kilomètres : jamais votre nom ni votre numéro. Elles restent sur le " +
                        "téléphone et se lisent sans réseau. Une mise à jour coûte quelques dizaines de kilo-octets (dix jours heure par heure, et l'air).\n\n" +
                        "Les alertes (fortes pluies, chaleur, vent, air) sont calculées sur le téléphone d'après ces prévisions : ce sont des repères, pas des bulletins officiels.\n\n" +
                        "Données météo : Open-Meteo.com (CC BY 4.0). Qualité de l'air : Open-Meteo, d'après CAMS.",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 22.sp, color = a.encre),
                )
            }
        }
        if (ajout) {
            PageAjout(
                premiere = villes.isEmpty(),
                fermer = { ajout = false },
                choisie = { v ->
                    MoteurMeteo.ajouter(c, v)
                    recharger()
                    ajout = false
                    portee.launch { demande++ }
                },
            )
        }
    }
}

/** La météo en une phrase, à envoyer : la ville, le temps, la pluie qui vient, demain. */
private fun phrasePartage(v: Ville, p: Previsions): String {
    val ac = p.actuel
    val jour = ac.heure.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
    val demain = p.jours.getOrNull(p.jours.indexOf(p.aujourdhui) + 1)
    return listOfNotNull(
        "Météo à ${v.nom}, $jour : ${degres(ac.temp)}, ${Ciel.description(ac.code, ac.jour).lowercase()} (ressenti ${degres(ac.ressenti)}).",
        Ciel.pluieAVenir(p)?.let { "$it." },
        demain?.let { "Demain : ${Ciel.description(it.code).lowercase()}, ${degres(it.min)} à ${degres(it.max)}" + (it.pluie?.takeIf { x -> x >= 40 }?.let { x -> ", $x % de pluie" } ?: "") + "." },
        Alertes.pour(p).firstOrNull()?.let { "${it.titre} : ${it.texte}" },
        "— Sama Météo, d'après Open-Meteo.",
    ).joinToString(" ")
}

/** Le ciel de la maquette : un dégradé d'après-midi et une lueur en haut à droite ; la nuit, bleu et doré. */
internal fun Modifier.ciel(nuit: Boolean) = drawBehind {
    val (haut, milieu, bas, lueur) = if (nuit) {
        listOf(Color(0xFF10152A), Color(0xFF1B2240), Color(0xFF232A44), Color(0x29F2C879))
    } else {
        listOf(Color(0xFFEDB98D), Color(0xFFF3D3B2), Color(0xFFF6E3CF), Color(0xE6FFECC8))
    }
    drawRect(Brush.verticalGradient(0f to haut, 0.32f to milieu, 0.6f to bas))
    drawRect(Brush.radialGradient(listOf(lueur, lueur.copy(alpha = 0f)), center = Offset(size.width * 0.85f, size.height * 0.08f), radius = size.width * 0.9f))
}

@Composable
private fun PageVille(ville: Ville, prev: Previsions?, chargement: Boolean, erreur: String?, t: Teintes, menu: () -> Unit) {
    val a = LocalIdentite.current
    var jourOuvert by remember { mutableStateOf<Jour?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconeTrait(if (ville.position) Icones.BOUSSOLE else EPINGLE, 20.dp, a.encre)
            BasicText(
                ville.nom,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = a.encre),
            )
            Rond("M6.5 12h.01 M12 12h.01 M17.5 12h.01", "Vos villes", epaisseur = 2.6f, onClick = menu)
        }
        if (prev == null) {
            BasicText(
                if (chargement) "Les prévisions arrivent…" else erreur ?: "",
                modifier = Modifier.padding(24.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 22.sp, color = a.encre2),
            )
            return@Column
        }
        val p = prev
        val ac = p.actuel
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    degres(ac.temp),
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontFamily = Polices.fine, fontWeight = FontWeight(200), fontSize = 112.sp, lineHeight = 116.sp, letterSpacing = (-0.04).em, color = a.encre),
                )
                IconeTrait(picto(ac.code, ac.jour), 84.dp, a.encre, epaisseur = 1.4f)
            }
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(Ciel.description(ac.code, ac.jour), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = a.encre))
                val aujourdhui = p.aujourdhui
                BasicText(
                    listOfNotNull(
                        "Ressenti ${degres(ac.ressenti)}",
                        aujourdhui?.let { "${degres(it.max)} au plus, ${degres(it.min)} au moins" },
                    ).joinToString(" · "),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2),
                )
            }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Puce(GOUTTE, "${ac.humidite} %", t.puce)
                Puce(VENT, "${ac.vent.roundToInt()} km/h", t.puce)
                val uv = ac.uv
                // L'indice UV, quand le soleil compte encore (pas « UV 0 » en fin de journée).
                if (ac.jour && uv != null && uv >= 1) Puce(Icones.SOLEIL, Ciel.uv(uv), t.puce)
            }
            // Les alertes de Sama, s'il y en a.
            Alertes.pour(p).forEach { CarteAlerte(it, t) }
            // Les 24 prochaines heures, les 10 jours, la pluie qui vient, puis le détail.
            Heures(p, t)
            Spacer(Modifier.height(10.dp))
            Jours(p, t) { jourOuvert = it }
            CartePluie(p, t)
            Tuiles(p, t)
            val vieille = System.currentTimeMillis() - p.recues
            BasicText(
                listOfNotNull(
                    Ciel.saison(ville, ac.heure.toLocalDate()),
                    if (erreur != null) "Pas de réseau : prévisions d'${depuis(vieille).removePrefix("il y a ")}." else null,
                    "Mis à jour ${depuis(vieille)}, pour ${ceil(p.octets / 1024.0).toInt().coerceAtLeast(1)} Ko de data." + if (chargement) " Mise à jour…" else "",
                    "Prévisions Open-Meteo.",
                ).joinToString(" "),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
            )
        }
    }
    jourOuvert?.let { FeuilleJour(prev ?: return, it, t) { jourOuvert = null } }
}

private fun depuis(ms: Long): String = when {
    ms < 60_000 -> "à l'instant"
    ms < 3600_000 -> "il y a ${ms / 60_000} min"
    ms < 48 * 3600_000L -> "il y a ${ms / 3600_000} h"
    else -> "il y a ${ms / (24 * 3600_000L)} jours"
}

@Composable
private fun Puce(icone: String, texte: String, fond: Color) {
    val a = LocalIdentite.current
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(fond).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconeTrait(icone, 16.dp, a.encre)
        BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = a.encre))
    }
}

/** Choisir une ville : les grandes villes du pays, une recherche, ou « Près de vous ». */
@Composable
private fun PageAjout(premiere: Boolean, fermer: () -> Unit, choisie: (Ville) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var recherche by remember { mutableStateOf("") }
    var resultats by remember { mutableStateOf<List<Ville>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    if (!premiere) BackHandler(onBack = fermer)

    fun chercherPuis(nom: String, prendrePremiere: Boolean) {
        portee.launch {
            message = "Recherche…"
            val r = try {
                withContext(Dispatchers.IO) { MoteurMeteo.chercher(nom) }
            } catch (_: Exception) {
                message = "Pas de réseau : la recherche demande quelques kilo-octets."
                return@launch
            }
            message = if (r.isEmpty()) "Aucune ville de ce nom." else null
            if (prendrePremiere && r.isNotEmpty()) choisie(r.first()) else resultats = r
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accorde ->
        if (!accorde) {
            message = "Sans la position, choisissez une ville."
            return@rememberLauncherForActivityResult
        }
        portee.launch {
            message = "Recherche de la position…"
            val v = MoteurMeteo.position(c)
            if (v == null) message = "Position introuvable pour l'instant : choisissez une ville." else choisie(v)
        }
    }

    LaunchedEffect(recherche) {
        if (recherche.trim().length < 2) {
            resultats = null
            return@LaunchedEffect
        }
        delay(600)
        chercherPuis(recherche, prendrePremiere = false)
    }

    Box(Modifier.fillMaxSize().background(a.fond)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (!premiere) BarreRetour("Météo", fermer, Modifier.padding(top = 0.dp))
            GrandTitre(if (premiere) "Votre ville" else "Ajouter une ville", sousBarre = true)
            ChampAppli(recherche, "Chercher une ville ou un village", { recherche = it })
            message?.let {
                BasicText(it, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 8.dp)) {
                val r = resultats
                if (r != null) {
                    r.forEach { v ->
                        LigneAppli(v.nom, second = listOf(v.region, v.pays).filter { it.isNotBlank() }.joinToString(" · "), debut = { IconeTrait(EPINGLE, 22.dp, a.encre2) }) { choisie(v) }
                    }
                } else {
                    LigneAppli(
                        "Près de vous",
                        second = "Votre position, arrondie à une dizaine de kilomètres",
                        debut = { IconeTrait(Icones.BOUSSOLE, 22.dp, a.accentTexte) },
                    ) { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    Rub("Grandes villes")
                    MoteurMeteo.PROPOSEES.forEach { nom ->
                        LigneAppli(nom, debut = { IconeTrait(EPINGLE, 22.dp, a.encre2) }) { chercherPuis(nom, prendrePremiere = true) }
                    }
                }
                BasicText(
                    "Seul le nom cherché part chez Open-Meteo, pour trouver la ville. Rien d'autre.",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = a.encre2),
                )
            }
        }
    }
}
