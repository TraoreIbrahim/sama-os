package africa.samaos.meteo

import android.Manifest
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
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
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

// Les pictos de la maquette, au trait.
private const val EPINGLE = "M12 21s-6-5.6-6-11a6 6 0 0 1 12 0c0 5.4-6 11-6 11z M12 7.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z"
private const val SOLEIL_NUAGE = "M9 4v1.5 M3.8 6.3l1 1 M2.5 11.5H4 M7 13a3 3 0 1 1 4.7-2.5 M9 20a3.5 3.5 0 0 1-.5-7A5 5 0 0 1 18 12a3.5 3.5 0 0 1 0 8z"
private const val NUAGE = "M7 18a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z"
private const val PLUIE = "M7 15a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 6a4.5 4.5 0 0 1 0 9z M9 18l-1 2.5 M13 18l-1 2.5 M17 18l-1 2.5"
private const val ORAGE = "M7 15a4 4 0 0 1-.6-7.95A6 6 0 0 1 18 6a4.5 4.5 0 0 1 0 9z M12.5 14l-2 3.5h3l-2 3.5"
private const val LUNE = "M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z"
private const val BROUILLARD = "M4 9h16 M6 13h12 M4 17h16"
private const val GOUTTE = "M12 3.5s6 6.5 6 10.5a6 6 0 0 1-12 0c0-4 6-10.5 6-10.5z"
private const val VENT = "M3 9h11a3 3 0 1 0-3-3 M3 15h15a3 3 0 1 1-3 3"

private fun picto(code: Int, jour: Boolean): String = when {
    code >= 95 -> ORAGE
    Ciel.pluvieux(code) -> PLUIE
    code == 45 || code == 48 -> BROUILLARD
    code <= 1 -> if (jour) Icones.SOLEIL else LUNE
    code == 2 -> if (jour) SOLEIL_NUAGE else NUAGE
    else -> NUAGE
}

private fun degres(t: Double) = "${t.roundToInt()}°"

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

    Box(Modifier.fillMaxSize().ciel(nuit)) {
        if (ville != null) {
            PageVille(
                ville = ville,
                prev = prev,
                chargement = chargement,
                erreur = erreur,
                nuit = nuit,
                menu = { menu = true },
            )
        }
        if (menu && ville != null) {
            FeuilleAppli(fermer = { menu = false }, titre = "Vos villes") {
                villes.forEachIndexed { i, v ->
                    LigneAppli(
                        v.nom,
                        second = v.region.ifBlank { if (v.position) "Position arrondie à 10 km" else null },
                        debut = { IconeTrait(if (v.position) Icones.BOUSSOLE else EPINGLE, 22.dp, LocalIdentite.current.encre2) },
                        fin = if (i == active) {
                            { IconeTrait(Icones.COCHE, 22.dp, LocalIdentite.current.accentTexte) }
                        } else {
                            null
                        },
                        surAppuiLong = if (villes.size > 1) {
                            {
                                MoteurMeteo.retirer(c, v)
                                recharger()
                            }
                        } else {
                            null
                        },
                    ) {
                        MoteurMeteo.choisir(c, i)
                        active = i
                        menu = false
                    }
                }
                ActionFeuille(Icones.PLUS, "Ajouter une ville") {
                    menu = false
                    ajout = true
                }
                ActionFeuille(Icones.MISE_A_JOUR, "Actualiser", second = "Quelques kilo-octets de data") {
                    menu = false
                    demande++
                }
                ActionFeuille(Icones.INFO, "D'où viennent les prévisions") {
                    menu = false
                    aPropos = true
                }
            }
        }
        if (aPropos) {
            FeuilleAppli(fermer = { aPropos = false }, titre = "D'où viennent les prévisions") {
                val a = LocalIdentite.current
                BasicText(
                    "Les prévisions viennent d'Open-Meteo, un service ouvert, sans compte ni publicité. Seule la ville choisie part, " +
                        "ou votre position arrondie à une dizaine de kilomètres : jamais votre nom ni votre numéro. Elles restent sur le " +
                        "téléphone et se lisent sans réseau. Une mise à jour coûte quelques kilo-octets.\n\nDonnées météo : Open-Meteo.com (CC BY 4.0).",
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

/** Le ciel de la maquette : un dégradé d'après-midi et une lueur en haut à droite ; la nuit, bleu et doré. */
private fun Modifier.ciel(nuit: Boolean) = drawBehind {
    val (haut, milieu, bas, lueur) = if (nuit) {
        listOf(Color(0xFF10152A), Color(0xFF1B2240), Color(0xFF232A44), Color(0x29F2C879))
    } else {
        listOf(Color(0xFFEDB98D), Color(0xFFF3D3B2), Color(0xFFF6E3CF), Color(0xE6FFECC8))
    }
    drawRect(Brush.verticalGradient(0f to haut, 0.32f to milieu, 0.6f to bas))
    drawRect(Brush.radialGradient(listOf(lueur, lueur.copy(alpha = 0f)), center = Offset(size.width * 0.85f, size.height * 0.08f), radius = size.width * 0.9f))
}

@Composable
private fun PageVille(ville: Ville, prev: Previsions?, chargement: Boolean, erreur: String?, nuit: Boolean, menu: () -> Unit) {
    val a = LocalIdentite.current
    val carte = if (nuit) Color(0x14FFFFFF) else Color(0x8CFFFFFF)
    val puce = if (nuit) Color(0x1AFFFFFF) else Color(0x80FFFFFF)
    val bleu = if (nuit) Color(0xFF9DB4E8) else Color(0xFF3D5A99)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconeTrait(if (ville.position) Icones.BOUSSOLE else EPINGLE, 20.dp, a.encre)
            BasicText(
                ville.nom,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = a.encre),
            )
            BoutonAppli(Icones.OPTIONS, "Autres villes", onClick = menu)
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
                BasicText(Ciel.phrase(p), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = a.encre))
                val aujourdhui = p.jours.firstOrNull()
                val demain = p.jours.getOrNull(1)
                BasicText(
                    listOfNotNull(
                        "Ressenti ${degres(ac.ressenti)}",
                        listOfNotNull(aujourdhui?.let { "${degres(it.max)} au plus" }, demain?.let { "${degres(it.min)} cette nuit" }).joinToString(", ").ifBlank { null },
                    ).joinToString(" · "),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2),
                )
            }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Puce(GOUTTE, "${ac.humidite} %", puce)
                Puce(VENT, "${ac.vent.roundToInt()} km/h", puce)
                val uv = ac.uv
                // L'indice UV, quand le soleil compte encore (pas « UV 0 » en fin de journée).
                if (ac.jour && uv != null && uv >= 1) Puce(Icones.SOLEIL, Ciel.uv(uv), puce)
            }
            // Les six prochaines heures, avec la chance de pluie quand elle compte.
            val heures = p.heures.filter { it.heure.isAfter(ac.heure) }.take(6)
            if (heures.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(carte).padding(horizontal = 6.dp, vertical = 14.dp)) {
                    heures.forEach { h ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicText("${h.heure.hour} h", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre))
                            IconeTrait(picto(h.code, h.jour), 24.dp, a.encre)
                            BasicText(degres(h.temp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre))
                            BasicText(
                                h.pluie?.takeIf { it >= 30 }?.let { "$it %" } ?: "",
                                modifier = Modifier.heightIn(min = 16.dp),
                                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = bleu),
                            )
                        }
                    }
                }
            }
            // Les cinq jours suivants, et l'écart du plus frais au plus chaud.
            val jours = p.jours.drop(1).take(5)
            if (jours.isNotEmpty()) {
                val bas = jours.minOf { it.min }
                val haut = jours.maxOf { it.max }.coerceAtLeast(bas + 1)
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(carte).padding(horizontal = 18.dp, vertical = 6.dp)) {
                    jours.forEach { j ->
                        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BasicText(
                                j.date.dayOfWeek.getDisplayName(StyleDate.FULL, Locale.FRENCH).replaceFirstChar { it.uppercase() },
                                modifier = Modifier.width(84.dp),
                                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = a.encre),
                            )
                            IconeTrait(picto(j.code, true), 22.dp, a.encre)
                            BasicText(degres(j.min), modifier = Modifier.width(30.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2, textAlign = TextAlign.End))
                            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(a.encre.copy(alpha = 0.1f))) {
                                val debut = ((j.min - bas) / (haut - bas)).toFloat().coerceIn(0f, 1f)
                                val fin = ((j.max - bas) / (haut - bas)).toFloat().coerceIn(debut, 1f)
                                Row(Modifier.fillMaxSize()) {
                                    if (debut > 0f) Spacer(Modifier.weight(debut))
                                    Box(
                                        Modifier
                                            .weight((fin - debut).coerceAtLeast(0.04f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Brush.horizontalGradient(listOf(Color(0xFFE2A62B), Color(0xFFB5532F)))),
                                    )
                                    if (fin < 1f) Spacer(Modifier.weight(1f - fin))
                                }
                            }
                            BasicText(degres(j.max), modifier = Modifier.width(30.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre))
                        }
                    }
                }
            }
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
            Tete(if (premiere) "Votre ville" else "Ajouter une ville", retour = if (premiere) null else fermer)
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
