package africa.samaos.fichiers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val LATERITE = Color(0xFFB5532F)

// ——— Les reçus (maquette l2-fic-telechargements) ———

@Composable
fun Recus() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    var filtre by remember { mutableIntStateOf(0) }
    var attendre by remember { mutableStateOf(Stockage.attendreWifi(c)) }
    val tous by produceState<List<Fichier>?>(null, x.version) { value = withContext(Dispatchers.IO) { Stockage.recus(c) } }
    // Les téléchargements en cours, relus chaque seconde ; la vitesse vient de l'écart entre deux lectures.
    var enCours by remember { mutableStateOf(emptyList<Telechargement>()) }
    var vitesses by remember { mutableStateOf(emptyMap<Long, Long>()) }
    var maj by remember { mutableIntStateOf(0) }
    LaunchedEffect(maj) {
        var avant = emptyMap<Long, Long>()
        while (true) {
            val l = withContext(Dispatchers.IO) { Stockage.enCours(c) }
            vitesses = l.associate { t -> t.id to (t.fait - (avant[t.id] ?: t.fait)).coerceAtLeast(0) }
            avant = l.associate { it.id to it.fait }
            enCours = l
            delay(1000)
        }
    }
    val portee = rememberCoroutineScope()
    EcranAppli {
        Tete("Reçus", retour = x.retour, petit = true) { BoutonAppli(Icones.RECHERCHE, "Rechercher") { x.aller(Vue.Recherche) } }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tout", "Téléchargés", "Reçus à proximité").forEachIndexed { i, nom -> PuceFiltre(nom, filtre == i) { filtre = i } }
        }
        val l = (tous ?: emptyList()).filter {
            when (filtre) {
                1 -> !Stockage.aProximite(it) && it.chemin.contains("/Download/")
                2 -> Stockage.aProximite(it)
                else -> true
            }
        }
        val aujourdhui = LocalDate.now()
        val groupes = l.groupBy { f ->
            val d = Instant.ofEpochMilli(f.date).atZone(ZoneId.systemDefault()).toLocalDate()
            when {
                d == aujourdhui -> "Aujourd'hui"
                d == aujourdhui.minusDays(1) -> "Hier"
                d.isAfter(aujourdhui.minusDays(7)) -> "Cette semaine"
                d.isAfter(aujourdhui.minusDays(31)) -> "Ce mois-ci"
                else -> "Plus ancien"
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(enCours, key = { "t${it.id}" }) { t ->
                CarteTelechargement(t, vitesses[t.id] ?: 0, Stockage.parData(c)) {
                    portee.launch {
                        withContext(Dispatchers.IO) { Stockage.pause(c, t.id, !t.enPause) }
                        maj++
                    }
                }
            }
            item(key = "wifi") {
                LigneAppli(
                    "Attendre le Wi-Fi au-delà de 50 Mo", "Sur data, les gros fichiers se mettent en pause.",
                    fin = { InterAppli(attendre) },
                ) {
                    if (Stockage.reglerAttendreWifi(c, !attendre)) attendre = !attendre
                }
            }
            if (tous != null && l.isEmpty()) item(key = "vide") {
                BasicText(
                    if (filtre == 2) "Rien reçu à proximité pour l'instant." else "Rien de reçu pour l'instant.",
                    modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2),
                )
            }
            groupes.forEach { (titre, fichiers) ->
                item(key = "r$titre") { Rub(titre) }
                items(fichiers, key = { it.chemin }) { f ->
                    // Une appli reçue : on le dit en latérite, c'est le premier piège des arnaqueurs.
                    if (f.genre == Genre.APPLI) LigneFichier(f, "${taille(f.taille)} · appli, vérifiez qui l'envoie", if (a.sombre) Color(0xFFEE9A78) else LATERITE)
                    else LigneFichier(f, listOfNotNull(taille(f.taille), f.source ?: "téléchargé").joinToString(" · "))
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }
    }
}

@Composable
private fun CarteTelechargement(t: Telechargement, parSeconde: Long, data: Boolean, basculer: () -> Unit) {
    val a = LocalIdentite.current
    val f = Fichier(t.titre, t.titre, t.total, 0)
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Vignette(f)
            Column(Modifier.weight(1f)) {
                BasicText(t.titre, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre))
                val etat = when {
                    t.enAttenteWifi -> "attend le Wi-Fi"
                    t.enPause -> "en pause"
                    else -> listOfNotNull(if (data) "par data" else "par Wi-Fi", if (parSeconde > 0) taille(parSeconde) + "/s" else null).joinToString(" · ")
                }
                val fait = if (t.total > 0) "${taille(t.fait)} sur ${taille(t.total)}" else taille(t.fait)
                BasicText("$fait · $etat", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
            }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(a.champ)) {
            if (t.total > 0) Box(Modifier.fillMaxWidth((t.fait.toFloat() / t.total).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(a.accent))
        }
        Row { BoutonTexteAppli(if (t.enPause) "Reprendre" else "Pause", style = 's', icone = if (t.enPause) Icones.LECTURE else Icones.PAUSE, onClick = basculer) }
    }
}

// ——— La corbeille ———

@Composable
fun Corbeille() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    var maj by remember { mutableIntStateOf(0) }
    val l by produceState<List<Stockage.Jete>?>(null, maj, x.version) { value = withContext(Dispatchers.IO) { Stockage.corbeille(c) } }
    var choisi by remember { mutableStateOf<Stockage.Jete?>(null) }
    var vider by remember { mutableStateOf(false) }
    var effacer by remember { mutableStateOf<Stockage.Jete?>(null) }
    val portee = rememberCoroutineScope()
    EcranAppli {
        Tete("Corbeille", retour = x.retour, petit = true) {
            if (!l.isNullOrEmpty()) BoutonTexteAppli("Vider", style = 't') { vider = true }
        }
        val liste = l ?: return@EcranAppli
        BasicText(
            if (liste.isEmpty()) "La corbeille est vide." else "Les fichiers restent 30 jours, puis s'effacent d'eux-mêmes. ${liste.size} fichier${if (liste.size > 1) "s" else ""} · ${taille(liste.sumOf { it.taille })}.",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(liste, key = { it.id }) { j ->
                val jours = ((j.expire - System.currentTimeMillis() + 86_399_999) / 86_400_000).coerceAtLeast(0)
                val f = Fichier(j.nom, j.nom, j.taille, 0)
                LigneAppli(
                    j.nom, "${taille(j.taille)} · ${if (jours <= 1) "s'efface demain" else "encore $jours jours"}",
                    debut = { Vignette(f) },
                    fin = {
                        BoutonAppli(Icones.RESTAURER, "Remettre « ${j.nom} »") {
                            portee.launch {
                                val ok = withContext(Dispatchers.IO) { Stockage.remettre(c, j.id) }
                                maj++
                                x.dire(if (ok) "« ${j.nom} » est revenu dans ${j.dossier.trimEnd('/').ifEmpty { "Ce téléphone" }}" else "Impossible de remettre « ${j.nom} »", null)
                            }
                        }
                    },
                    surAppuiLong = { choisi = j },
                ) { choisi = j }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }
    }
    choisi?.let { j ->
        FeuilleAppli(fermer = { choisi = null }, titre = j.nom) {
            ActionFeuille(Icones.RESTAURER, "Remettre", second = "Dans ${j.dossier.trimEnd('/').ifEmpty { "Ce téléphone" }}") {
                choisi = null
                portee.launch {
                    withContext(Dispatchers.IO) { Stockage.remettre(c, j.id) }
                    maj++
                }
            }
            ActionFeuille(Icones.CORBEILLE, "Effacer pour de bon", danger = true) {
                choisi = null
                effacer = j
            }
        }
    }
    effacer?.let { j ->
        DialogueAppli("Effacer « ${j.nom} » ?", "Il ne pourra plus être remis.", fermer = { effacer = null }) {
            BoutonTexteAppli("Annuler", style = 't') { effacer = null }
            BoutonTexteAppli("Effacer") {
                effacer = null
                portee.launch {
                    withContext(Dispatchers.IO) { Stockage.effacer(c, j.id) }
                    maj++
                }
            }
        }
    }
    if (vider) {
        DialogueAppli("Vider la corbeille ?", "Les ${l?.size ?: 0} fichiers seront effacés pour de bon.", fermer = { vider = false }) {
            BoutonTexteAppli("Annuler", style = 't') { vider = false }
            BoutonTexteAppli("Vider") {
                vider = false
                portee.launch {
                    withContext(Dispatchers.IO) { l?.forEach { Stockage.effacer(c, it.id) } }
                    maj++
                }
            }
        }
    }
}
