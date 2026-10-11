package africa.samaos.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import kotlinx.coroutines.launch
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BlocAppli
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.Rond
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.FiletBloc
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as StyleDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private val HEURE = DateTimeFormatter.ofPattern("HH:mm")
fun heure(t: LocalTime): String = t.format(HEURE)
fun jourCourt(d: DayOfWeek): String = d.getDisplayName(StyleDate.SHORT, Locale.FRENCH).removeSuffix(".").replaceFirstChar { it.uppercase() }

/** La couleur d'une matière, posée sur la surface (le jour) ou sur le fond sombre (la nuit). */
@Composable
fun teinte(c: Color, part: Float = 0.14f): Color {
    val a = LocalIdentite.current
    return lerp(if (a.sombre) a.surface else Color.White, c, if (a.sombre) part + 0.12f else part)
}

/** La couleur d'une matière pour le texte : plus claire la nuit. */
@Composable
fun encreDe(c: Color): Color = if (LocalIdentite.current.sombre) lerp(c, Color.White, 0.45f) else c

@Composable
fun EnTete(sous: String, titre: String, modifier: Modifier = Modifier, surSous: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    val a = LocalIdentite.current
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            // Le sous-titre peut être un lien (« Revenir à aujourd'hui »).
            BasicText(
                sous + if (surSous != null) "  ›" else "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (surSous != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = surSous) else Modifier,
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = if (surSous != null) a.accentTexte else a.encre2),
            )
            BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp, color = a.encre))
        }
        actions()
    }
}

@Composable
fun Section(texte: String, modifier: Modifier = Modifier) {
    val a = LocalIdentite.current
    BasicText(
        texte, modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre),
    )
}

val ROUGE_HEURE = Color(0xFFD9542F)

/** L'heure qu'il est, dans la marge des heures, au bout du trait rouge. */
@Composable
fun EtiquetteHeure(t: LocalTime, modifier: Modifier = Modifier) {
    BasicText(
        heure(t), modifier = modifier.clip(RoundedCornerShape(9.dp)).background(ROUGE_HEURE).padding(horizontal = 5.dp, vertical = 2.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color.White),
    )
}

// ——— L'emploi du temps de la semaine (maquette agenda-3) ———

@Composable
fun PageSemaine(version: Int, changer: () -> Unit, pied: @Composable () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val cours = remember(version) { Carnet.cours(c) }
    var classe by remember(version) { mutableStateOf(Carnet.classe(c)) }
    var edition by remember { mutableStateOf<Triple<Cours?, DayOfWeek, LocalTime>?>(null) }
    var nomClasse by remember { mutableStateOf(false) }
    val auj = LocalDate.now()
    val lundiAuj = auj.minusDays((auj.dayOfWeek.value - 1).toLong())
    val centre = 520
    val etat = androidx.compose.foundation.pager.rememberPagerState(initialPage = centre) { centre * 2 }
    val portee = androidx.compose.runtime.rememberCoroutineScope()
    val lundiVu = lundiAuj.plusWeeks((etat.currentPage - centre).toLong())
    val jours = if (cours.any { it.jour == DayOfWeek.SUNDAY }) DayOfWeek.entries else if (cours.any { it.jour == DayOfWeek.SATURDAY } || cours.isEmpty()) DayOfWeek.entries.take(6) else DayOfWeek.entries.take(5)
    val h0 = (cours.minOfOrNull { it.debut.hour } ?: 7).coerceAtMost(7)
    val h1 = (cours.maxOfOrNull { if (it.fin.minute > 0) it.fin.hour + 1 else it.fin.hour } ?: 17).coerceAtLeast(17)
    val hauteur = 58.dp
    // L'heure avance toute seule : le trait rouge bouge sans qu'on touche à rien.
    var maintenant by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(20_000)
            maintenant = LocalTime.now()
        }
    }
    Column(Modifier.fillMaxSize()) {
        val semaine = "semaine du ${lundiVu.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))}"
        EnTete(
            if (etat.currentPage == centre) "${classe.ifBlank { "Ma classe" }} · $semaine" else "${semaine.replaceFirstChar { it.uppercase() }} · revenir à celle-ci",
            "Emploi du temps",
            Modifier.clickable(onClickLabel = "Nommer la classe", role = Role.Button) { nomClasse = true },
            surSous = if (etat.currentPage == centre) null else ({ portee.launch { etat.animateScrollToPage(centre) } }),
        ) {
            Rond(Icones.PLUS, "Ajouter un cours", plein = true, epaisseur = 2.2f) { edition = Triple(null, auj.dayOfWeek.takeIf { it in jours } ?: DayOfWeek.MONDAY, LocalTime.of(8, 0)) }
        }
        // Une page par semaine : on glisse pour voir les dates des autres semaines (les cours reviennent chaque semaine).
        HorizontalPager(etat, Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 1) { page ->
            val lundi = lundiAuj.plusWeeks((page - centre).toLong())
            val cetteSemaine = page == centre
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val gouttiere = 44.dp
                val large = (maxWidth - gouttiere - 10.dp) / jours.size
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.padding(start = gouttiere, top = 8.dp, bottom = 4.dp)) {
                        jours.forEach { j ->
                            val ici = cetteSemaine && j == auj.dayOfWeek
                            Column(Modifier.width(large), horizontalAlignment = Alignment.CenterHorizontally) {
                                Column(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (ici) a.encre else Color.Transparent).padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    BasicText(jourCourt(j), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = if (ici) a.fond.copy(alpha = 0.75f) else a.encre2))
                                    BasicText("${lundi.plusDays((j.value - 1).toLong()).dayOfMonth}", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (ici) a.fond else a.encre))
                                }
                            }
                        }
                    }
                    val densite = LocalDensity.current
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                        val trait = a.trait
                        val hachure = a.encre.copy(alpha = 0.05f)
                        Box(
                            Modifier.fillMaxWidth().height(hauteur * (h1 - h0) + 16.dp).padding(top = 8.dp)
                                .pointerInput(jours, h0) {
                                    // Toucher une case vide ajoute un cours ce jour-là, à cette heure.
                                    detectTapGestures { p ->
                                        val x = p.x - with(densite) { gouttiere.toPx() }
                                        if (x < 0) return@detectTapGestures
                                        val k = (x / with(densite) { large.toPx() }).toInt().coerceIn(0, jours.lastIndex)
                                        val h = (p.y / with(densite) { hauteur.toPx() }).toInt() + h0
                                        edition = Triple(null, jours[k], LocalTime.of(h.coerceIn(0, 22), 0))
                                    }
                                },
                        ) {
                            // Les heures
                            for (h in h0..h1) {
                                Box(Modifier.offset(y = hauteur * (h - h0)).fillMaxWidth().height(1.dp).padding(start = gouttiere - 4.dp).background(trait))
                                BasicText(
                                    "$h h", modifier = Modifier.offset(x = 6.dp, y = hauteur * (h - h0) - 8.dp),
                                    style = TextStyle(fontFamily = Polices.corps, fontSize = 10.5.sp, color = a.encre2.copy(alpha = 0.8f)),
                                )
                            }
                            // La pause de midi : le plus long moment sans cours, chaque jour, entre 11 h et 15 h 30.
                            pauseDeMidi(cours)?.let { (d, f) ->
                                val y = hauteur * ((d.toSecondOfDay() / 3600f) - h0)
                                val haut = hauteur * ((f.toSecondOfDay() - d.toSecondOfDay()) / 3600f)
                                Box(
                                    Modifier.offset(x = gouttiere, y = y).width(large * jours.size).height(haut).padding(2.dp).clip(RoundedCornerShape(12.dp)).clipToBounds()
                                        .drawBehind {
                                            val pas = 16.dp.toPx()
                                            var x = -size.height
                                            while (x < size.width) {
                                                drawLine(hachure, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 8.dp.toPx())
                                                x += pas
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { BasicText("Pause de midi", style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, color = a.encre2)) }
                            }
                            cours.filter { it.jour in jours }.forEach { co ->
                                val k = jours.indexOf(co.jour)
                                val y = hauteur * ((co.debut.toSecondOfDay() / 3600f) - h0)
                                val haut = hauteur * ((co.fin.toSecondOfDay() - co.debut.toSecondOfDay()) / 3600f)
                                Column(
                                    Modifier.offset(x = gouttiere + large * k + 2.dp, y = y + 1.dp).width(large - 4.dp).height(haut - 3.dp)
                                        .clip(RoundedCornerShape(10.dp)).background(teinte(co.couleur))
                                        .clickable(onClickLabel = "${co.matiere}, ${heure(co.debut)}", role = Role.Button) { edition = Triple(co, co.jour, co.debut) }
                                        .padding(start = 5.dp, end = 3.dp, top = 6.dp),
                                ) {
                                    Box(Modifier.size(16.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(co.couleur))
                                    // Une colonne étroite coupe « Phys.-chimie » après le trait d'union, pas au milieu d'un mot.
                                    BasicText(co.matiere.replace("-", "-\u200B"), modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 11.5.sp, color = encreDe(co.couleur)))
                                    if (co.salle.isNotBlank()) BasicText(co.salle, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 10.sp, color = a.encre2))
                                }
                            }
                            // Maintenant : un trait rouge sur toute la largeur, l'heure écrite dans la marge, un point sur le jour.
                            if (cetteSemaine && maintenant.hour in h0 until h1) {
                                val y = hauteur * ((maintenant.toSecondOfDay() / 3600f) - h0)
                                Box(Modifier.offset(y = y - 1.dp).padding(start = gouttiere - 4.dp).fillMaxWidth().height(2.dp).background(ROUGE_HEURE))
                                if (auj.dayOfWeek in jours) {
                                    Box(Modifier.offset(x = gouttiere + large * jours.indexOf(auj.dayOfWeek) - 5.dp, y = y - 5.dp).size(10.dp).clip(CircleShape).background(ROUGE_HEURE))
                                }
                                EtiquetteHeure(maintenant, Modifier.offset(x = 2.dp, y = y - 9.dp))
                            }
                        }
                    if (cours.isEmpty()) {
                            BasicText(
                                "Touchez une case pour y mettre un cours : il revient chaque semaine.",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2, textAlign = TextAlign.Center),
                            )
                        }
                        pied()
                    }
                }
            }
        }
    }
    edition?.let { (co, j, h) ->
        FeuilleCours(co, j, h, cours, fermer = { edition = null }) {
            edition = null
            changer()
        }
    }
    if (nomClasse) {
        africa.samaos.banco.appli.DialogueAppli("Votre classe", "Elle s'affiche au-dessus de l'emploi du temps.", fermer = { nomClasse = false }, contenu = {
            Champ("3e B, Terminale D, L1 Droit…", classe) { classe = it }
        }) {
            BoutonTexteAppli("Annuler", style = 't') { nomClasse = false }
            BoutonTexteAppli("Enregistrer") {
                Carnet.reglerClasse(c, classe)
                nomClasse = false
                changer()
            }
        }
    }
}

/** Le plus long moment libre commun à tous les jours de cours, entre 11 h et 15 h 30 (au moins une heure). */
private fun pauseDeMidi(cours: List<Cours>): Pair<LocalTime, LocalTime>? {
    if (cours.isEmpty()) return null
    val pas = 15
    val debut = 11 * 60
    val fin = 15 * 60 + 30
    val libre = (debut until fin step pas).map { m -> cours.none { co -> co.debut.toSecondOfDay() / 60 < m + pas && co.fin.toSecondOfDay() / 60 > m } }
    var meilleur: Pair<Int, Int>? = null
    var i = 0
    while (i < libre.size) {
        if (libre[i]) {
            var j = i
            while (j < libre.size && libre[j]) j++
            if (meilleur == null || j - i > meilleur.second - meilleur.first) meilleur = i to j
            i = j
        } else i++
    }
    val m = meilleur ?: return null
    if ((m.second - m.first) * pas < 60) return null
    return LocalTime.of(0, 0).plusMinutes((debut + m.first * pas).toLong()) to LocalTime.of(0, 0).plusMinutes((debut + m.second * pas).toLong())
}

@Composable
fun Champ(indication: String, valeur: String, modifier: Modifier = Modifier, changer: (String) -> Unit) {
    val a = LocalIdentite.current
    BasicTextField(
        value = valeur, onValueChange = changer, singleLine = true,
        textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(a.champ).padding(horizontal = 16.dp, vertical = 13.dp).semantics { contentDescription = indication },
        decorationBox = { champ -> Box { if (valeur.isEmpty()) BasicText(indication, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2)); champ() } },
    )
}

@Composable
private fun Pas(icone: String, nom: String, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Box(Modifier.size(40.dp).clip(CircleShape).background(a.champ).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        IconeTrait(icone, 18.dp, a.encre)
    }
}

/** Ajouter ou modifier un cours : la matière, le jour, les heures, la salle, le professeur. */
@Composable
private fun FeuilleCours(co: Cours?, jour0: DayOfWeek, heure0: LocalTime, tous: List<Cours>, fermer: () -> Unit, fini: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var matiere by remember { mutableStateOf(co?.matiere.orEmpty()) }
    var jour by remember { mutableStateOf(co?.jour ?: jour0) }
    var debut by remember { mutableStateOf(co?.debut ?: heure0) }
    var duree by remember { mutableIntStateOf(co?.let { ((it.fin.toSecondOfDay() - it.debut.toSecondOfDay()) / 60) } ?: 60) }
    var salle by remember { mutableStateOf(co?.salle.orEmpty()) }
    var prof by remember { mutableStateOf(co?.prof.orEmpty()) }
    // Les matières déjà dans l'emploi du temps d'abord, avec leur salle et leur professeur habituels.
    val deja = tous.map { it.matiere }.distinct()
    val proposees = (deja + Matieres.COURANTES.filter { m -> deja.none { Matieres.simple(it) == Matieres.simple(m) } })
    // Enregistrer ou supprimer fait redescendre la feuille, puis l'écran se met à jour.
    var fait by remember { mutableStateOf(false) }
    FeuilleAppli(fermer = { if (fait) fini() else fermer() }, titre = if (co == null) "Nouveau cours" else "Le cours") {
        val fermerFeuille = LocalFermerFeuille.current
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Champ("Matière", matiere) { matiere = it }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                proposees.forEach { m ->
                    PuceFiltre(m, Matieres.simple(m) == Matieres.simple(matiere), debut = { Box(Modifier.size(8.dp).clip(CircleShape).background(Matieres.couleur(m))) }) {
                        matiere = m
                        tous.lastOrNull { Matieres.simple(it.matiere) == Matieres.simple(m) }?.let { ancien ->
                            if (salle.isBlank()) salle = ancien.salle
                            if (prof.isBlank()) prof = ancien.prof
                        }
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.take(6).forEach { j -> PuceFiltre(jourCourt(j), j == jour) { jour = j } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pas("M5 12h14", "15 minutes plus tôt") { debut = debut.minusMinutes(15) }
                BasicText(
                    "${heure(debut)} – ${heure(debut.plusMinutes(duree.toLong()))}", modifier = Modifier.weight(1f),
                    style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(300), fontSize = 32.sp, color = a.encre, textAlign = TextAlign.Center),
                )
                Pas(Icones.PLUS, "15 minutes plus tard") { debut = debut.plusMinutes(15) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(60 to "1 h", 90 to "1 h 30", 120 to "2 h", 180 to "3 h").forEach { (m, n) -> PuceFiltre(n, duree == m) { duree = m } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Champ("Salle", salle, Modifier.weight(1f)) { salle = it }
                Champ("Professeur", prof, Modifier.weight(1.4f)) { prof = it }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (co != null) {
                    BoutonTexteAppli("Supprimer", style = 't', icone = Icones.CORBEILLE) {
                        Carnet.supprimerCours(c, co.id)
                        fait = true; fermerFeuille()
                    }
                    Spacer(Modifier.weight(1f))
                }
                BoutonTexteAppli("Enregistrer", actif = matiere.isNotBlank()) {
                    Carnet.garderCours(c, co?.id, matiere, jour, debut, debut.plusMinutes(duree.toLong()), salle, prof)
                    fait = true; fermerFeuille()
                }
            }
        }
    }
}

// ——— À faire : les devoirs de l'école, les tâches du travail ———

/** Quand, en mots : « Pour demain », « Pour vendredi », « En retard de 2 jours ». */
fun quand(d: LocalDate, prefixe: String = "Pour"): String {
    val auj = LocalDate.now()
    val n = ChronoUnit.DAYS.between(auj, d)
    return when {
        n < 0 -> "En retard de ${-n} jour${if (n < -1) "s" else ""}"
        n == 0L -> "$prefixe aujourd'hui"
        n == 1L -> "$prefixe demain"
        n < 7 -> "$prefixe ${d.dayOfWeek.getDisplayName(StyleDate.FULL, Locale.FRENCH)}"
        else -> "$prefixe le ${d.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))}"
    }
}

@Composable
fun CaseACocher(faite: Boolean, couleur: Color, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClickLabel = if (faite) "Décocher" else "Cocher", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Cochée : la case se remplit et la coche apparaît d'un petit rebond.
        val fond by androidx.compose.animation.animateColorAsState(if (faite) couleur else couleur.copy(alpha = 0f), label = "case")
        val echelle by androidx.compose.animation.core.animateFloatAsState(if (faite) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 700f), label = "coche")
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(fond).then(if (faite) Modifier else Modifier.border(2.dp, a.encre2.copy(alpha = 0.45f), RoundedCornerShape(7.dp))),
            contentAlignment = Alignment.Center,
        ) { if (echelle > 0.01f) IconeTrait(Icones.COCHE, 15.dp, Color.White, Modifier.graphicsLayer { scaleX = echelle; scaleY = echelle }, epaisseur = 3f) }
    }
}

@Composable
fun Etiquette(texte: String, couleur: Color) {
    BasicText(
        texte, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(teinte(couleur, 0.13f)).padding(horizontal = 9.dp, vertical = 3.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = encreDe(couleur)),
    )
}

@Composable
fun LigneDevoir(d: Devoir, cocher: () -> Unit, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val fait = d.faitLe != null
    Row(Modifier.fillMaxWidth().clickable(onClick = ouvrir).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CaseACocher(fait, Color(0xFF2E8A6E), cocher)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            BasicText(
                (if (d.controle) "Contrôle · " else "") + d.titre,
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = if (fait) a.encre2 else a.encre, textDecoration = if (fait) TextDecoration.LineThrough else null),
            )
            BasicText(
                if (fait) "Fait" else quand(d.pour),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 12.5.sp, color = if (!fait && d.pour.isBefore(LocalDate.now())) Color(0xFFC2412D) else a.encre2),
            )
        }
        d.matiere?.let { Etiquette(it, Matieres.couleur(it)) }
    }
}

@Composable
fun LigneTache(t: Tache, cocher: () -> Unit, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val faite = t.faiteLe != null
    Row(Modifier.fillMaxWidth().clickable(onClick = ouvrir).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CaseACocher(faite, Color(0xFF2E8A6E), cocher)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            BasicText(t.titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = if (faite) a.encre2 else a.encre, textDecoration = if (faite) TextDecoration.LineThrough else null))
            BasicText(
                when {
                    faite -> if (t.faiteLe!! > LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) "Faite aujourd'hui" else "Faite"
                    t.pour == null -> "Sans date"
                    else -> quand(t.pour, "")
                        .replace(" aujourd'hui", "Aujourd'hui").replace(" demain", "Demain").trim().replaceFirstChar { it.uppercase() }
                },
                style = TextStyle(fontFamily = Polices.corps, fontSize = 12.5.sp, color = if (!faite && t.pour?.isBefore(LocalDate.now()) == true) Color(0xFFC2412D) else a.encre2),
            )
        }
        if (t.urgente && !faite) Etiquette("Urgent", Color(0xFFB5532F))
    }
}

@Composable
fun PageAFaire(usages: Set<Usage>, version: Int, changer: () -> Unit, pied: @Composable () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val ecole = Usage.ECOLE in usages
    val travail = Usage.TRAVAIL in usages
    val devoirs = remember(version) { if (ecole) Carnet.devoirs(c) else emptyList() }
    val taches = remember(version) { if (travail) Carnet.taches(c) else emptyList() }
    var ajout by remember { mutableStateOf<Any?>(null) }
    val auj = LocalDate.now()
    Column(Modifier.fillMaxSize()) {
        EnTete(
            listOfNotNull(
                devoirs.count { it.faitLe == null }.takeIf { ecole }?.let { "$it devoir${if (it > 1) "s" else ""}" },
                taches.count { it.faiteLe == null }.takeIf { travail }?.let { "$it tâche${if (it > 1) "s" else ""}" },
            ).joinToString(" · ") + " à faire",
            if (ecole && travail) "À faire" else if (ecole) "Devoirs" else "Tâches",
        ) {
            Rond(Icones.PLUS, if (ecole && !travail) "Ajouter un devoir" else "Ajouter une tâche", plein = true, epaisseur = 2.2f) { ajout = if (ecole && !travail) "devoir" else "tache" }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (ecole) {
                val aFaire = devoirs.filter { it.faitLe == null }
                val groupes = listOf(
                    "En retard" to aFaire.filter { it.pour.isBefore(auj) },
                    "Pour demain" to aFaire.filter { it.pour == auj || it.pour == auj.plusDays(1) },
                    "Cette semaine" to aFaire.filter { it.pour.isAfter(auj.plusDays(1)) && it.pour.isBefore(auj.plusDays(8)) },
                    "Plus tard" to aFaire.filter { it.pour.isAfter(auj.plusDays(7)) },
                    "Faits" to devoirs.filter { it.faitLe != null }.sortedByDescending { it.faitLe }.take(8),
                ).filter { it.second.isNotEmpty() }
                if (travail) Section("Devoirs")
                if (groupes.isEmpty()) Vide("Aucun devoir. Ajoutez-en un avec + : pour le prochain cours, en une touche.") { ajout = "devoir" }
                groupes.forEach { (titre, l) ->
                    if (!travail || groupes.size > 1) BasicText(titre, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (titre == "En retard") Color(0xFFC2412D) else a.encre2))
                    BlocAppli {
                        l.forEachIndexed { i, d ->
                            if (i > 0) FiletBloc(52.dp)
                            LigneDevoir(d, { Carnet.cocherDevoir(c, d.id, d.faitLe == null); changer() }) { ajout = d }
                        }
                    }
                }
            }
            if (travail) {
                if (ecole) Section("Tâches")
                val aFaire = taches.filter { it.faiteLe == null }
                val faites = taches.filter { it.faiteLe != null }.sortedByDescending { it.faiteLe }.take(8)
                if (aFaire.isEmpty() && faites.isEmpty()) Vide("Aucune tâche. Ajoutez-en une avec + ; cochez-la quand c'est fait.") { ajout = "tache" }
                if (aFaire.isNotEmpty()) {
                    BlocAppli(Modifier.padding(top = if (ecole) 0.dp else 8.dp)) {
                        aFaire.forEachIndexed { i, t ->
                            if (i > 0) FiletBloc(52.dp)
                            LigneTache(t, { Carnet.cocherTache(c, t.id, true); changer() }) { ajout = t }
                        }
                    }
                }
                if (faites.isNotEmpty()) {
                    BasicText("Faites", modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.encre2))
                    BlocAppli {
                        faites.forEachIndexed { i, t ->
                            if (i > 0) FiletBloc(52.dp)
                            LigneTache(t, { Carnet.cocherTache(c, t.id, false); changer() }) { ajout = t }
                        }
                    }
                }
            }
            pied()
        }
    }
    when (val x = ajout) {
        null -> {}
        "devoir" -> FeuilleDevoir(null, fermer = { ajout = null }) { ajout = null; changer() }
        "tache" -> FeuilleTache(null, ecole, fermer = { ajout = null }, versDevoir = { ajout = "devoir" }) { ajout = null; changer() }
        is Devoir -> FeuilleDevoir(x, fermer = { ajout = null }) { ajout = null; changer() }
        is Tache -> FeuilleTache(x, false, fermer = { ajout = null }, versDevoir = {}) { ajout = null; changer() }
    }
}

@Composable
private fun Vide(texte: String, ajouter: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = ajouter).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(texte, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.5.sp, lineHeight = 20.sp, color = a.encre2))
        IconeTrait(Icones.PLUS, 20.dp, a.accentTexte)
    }
}

/** Un devoir : quoi, en quelle matière, pour quand (le prochain cours de la matière est proposé). */
@Composable
fun FeuilleDevoir(d: Devoir?, fermer: () -> Unit, fini: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val cours = remember { Carnet.cours(c) }
    var titre by remember { mutableStateOf(d?.titre.orEmpty()) }
    var matiere by remember { mutableStateOf(d?.matiere) }
    var pour by remember { mutableStateOf(d?.pour ?: LocalDate.now().plusDays(1)) }
    var controle by remember { mutableStateOf(d?.controle ?: false) }
    val prochain = matiere?.let { Carnet.prochainCours(c, it) }
    LaunchedEffect(matiere) { if (d == null && prochain != null) pour = prochain }
    val matieres = cours.map { it.matiere }.distinct().ifEmpty { Matieres.COURANTES.take(8) }
    // Enregistrer ou supprimer fait redescendre la feuille, puis l'écran se met à jour.
    var fait by remember { mutableStateOf(false) }
    FeuilleAppli(fermer = { if (fait) fini() else fermer() }, titre = if (d == null) "Nouveau devoir" else "Le devoir") {
        val fermerFeuille = LocalFermerFeuille.current
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Champ("Exercices page 54, exposé…", titre) { titre = it }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                matieres.forEach { m -> PuceFiltre(m, matiere == m, debut = { Box(Modifier.size(8.dp).clip(CircleShape).background(Matieres.couleur(m))) }) { matiere = if (matiere == m) null else m } }
            }
            val auj = LocalDate.now()
            val choix = listOfNotNull(
                prochain?.let { it to "Prochain cours · ${jourCourt(it.dayOfWeek).lowercase()}" },
                auj.plusDays(1) to "Demain",
                auj.plusDays(2) to "Après-demain",
                auj.plusDays(7) to "Dans une semaine",
            ).distinctBy { it.first }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                choix.forEach { (j, n) -> PuceFiltre(n, pour == j) { pour = j } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pas("M15 6l-6 6l6 6", "Un jour plus tôt") { pour = pour.minusDays(1) }
                BasicText(quand(pour), modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre, textAlign = TextAlign.Center))
                Pas(Icones.CHEVRON, "Un jour plus tard") { pour = pour.plusDays(1) }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Switch) { controle = !controle }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText("C'est un contrôle", style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
                    BasicText("Interrogation, devoir surveillé, examen", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                }
                InterAppli(controle)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (d != null) {
                    BoutonTexteAppli("Supprimer", style = 't', icone = Icones.CORBEILLE) { Carnet.supprimerDevoir(c, d.id); fait = true; fermerFeuille() }
                    Spacer(Modifier.weight(1f))
                }
                BoutonTexteAppli("Enregistrer", actif = titre.isNotBlank()) {
                    Carnet.garderDevoir(c, d?.id, titre, matiere, pour, controle)
                    fait = true; fermerFeuille()
                }
            }
        }
    }
}

/** Une tâche : quoi, pour quand (ou sans date), urgente ou non. */
@Composable
fun FeuilleTache(t: Tache?, aussiDevoir: Boolean, fermer: () -> Unit, versDevoir: () -> Unit, fini: () -> Unit) {
    val c = LocalContext.current
    var titre by remember { mutableStateOf(t?.titre.orEmpty()) }
    var pour by remember { mutableStateOf(if (t == null) LocalDate.now() else t.pour) }
    var urgente by remember { mutableStateOf(t?.urgente ?: false) }
    val a = LocalIdentite.current
    // Enregistrer ou supprimer fait redescendre la feuille, puis l'écran se met à jour.
    var fait by remember { mutableStateOf(false) }
    FeuilleAppli(fermer = { if (fait) fini() else fermer() }, titre = if (t == null) "Nouvelle tâche" else "La tâche") {
        val fermerFeuille = LocalFermerFeuille.current
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (aussiDevoir) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PuceFiltre("Tâche", true) {}
                    PuceFiltre("Devoir", false, onClick = versDevoir)
                }
            }
            Champ("Envoyer le devis, appeler…", titre) { titre = it }
            val auj = LocalDate.now()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(auj to "Aujourd'hui", auj.plusDays(1) to "Demain", auj.plusDays(7) to "Dans une semaine", null to "Sans date").forEach { (j, n) -> PuceFiltre(n, pour == j) { pour = j } }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Switch) { urgente = !urgente }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Urgent", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
                InterAppli(urgente)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (t != null) {
                    BoutonTexteAppli("Supprimer", style = 't', icone = Icones.CORBEILLE) { Carnet.supprimerTache(c, t.id); fait = true; fermerFeuille() }
                    Spacer(Modifier.weight(1f))
                }
                BoutonTexteAppli("Enregistrer", actif = titre.isNotBlank()) {
                    Carnet.garderTache(c, t?.id, titre, pour, urgente)
                    fait = true; fermerFeuille()
                }
            }
        }
    }
}
