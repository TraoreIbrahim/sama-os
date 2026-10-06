package africa.samaos.fichiers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.CaseAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Analyse(
    val videos: List<Fichier>,
    val doublons: List<Fichier>,
    val applis: List<Stockage.AppliInutile>,
    val caches: Long,
    val libre: Long,
    val total: Long,
)

/** Nettoyer (maquette l2-fic-nettoyage) : ce qui peut partir sans regret, coché d'office sauf ce qui se discute. */
@Composable
fun Nettoyer() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    var maj by remember { mutableIntStateOf(0) }
    var analyse by remember { mutableStateOf<Analyse?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val coches = remember { mutableStateOf(setOf("videos", "doublons", "caches")) }
    var voir by remember { mutableStateOf<Pair<String, List<Fichier>>?>(null) }
    LaunchedEffect(maj, x.version) {
        analyse = withContext(Dispatchers.IO) {
            val e = Stockage.etat(c)
            Analyse(Stockage.vieillesVideos(c), Stockage.doublons(c), Stockage.applisInutiles(c), e?.caches ?: 0, e?.libre ?: 0, e?.total ?: 1)
        }
    }
    val portee = rememberCoroutineScope()
    EcranAppli {
        Tete("Nettoyer")
        val an = analyse
        if (an == null) {
            BasicText("Recherche de ce qui peut partir…", modifier = Modifier.weight(1f).padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
            Onglets(1)
            return@EcranAppli
        }
        val parts = mapOf(
            "videos" to an.videos.sumOf { it.taille },
            "doublons" to an.doublons.sumOf { it.taille },
            "applis" to an.applis.sumOf { it.taille },
            "caches" to an.caches,
        )
        val recup = parts.filterKeys { it in coches.value }.values.sum()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(taille(recup), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em, color = a.encre))
                BasicText("à récupérer · ${taille(an.libre)} déjà libres", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(a.champ)) {
                    val occupe = (an.total - an.libre - recup).coerceAtLeast(0)
                    Box(Modifier.weight((occupe.toFloat() / an.total).coerceAtLeast(0.004f)).height(12.dp).background(androidx.compose.ui.graphics.Color(0xFFB89878)))
                    if (recup > 0) Box(Modifier.weight((recup.toFloat() / an.total).coerceAtLeast(0.01f)).height(12.dp).background(a.accent))
                    Box(Modifier.weight((an.libre.toFloat() / an.total).coerceAtLeast(0.004f)))
                }
            }
            fun basculer(cle: String) {
                coches.value = if (cle in coches.value) coches.value - cle else coches.value + cle
            }
            if (an.videos.isNotEmpty()) {
                LigneCase("Vidéos reçues il y a plus de 3 mois", "${an.videos.size} vidéo${if (an.videos.size > 1) "s" else ""} · ${taille(parts.getValue("videos"))}", "videos" in coches.value, { basculer("videos") }) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        an.videos.take(3).forEach { Vignette(it, 44.dp, 8.dp) }
                        if (an.videos.size > 3) Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(a.champ), contentAlignment = Alignment.Center) {
                            BasicText("+${an.videos.size - 3}", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.encre2))
                        }
                    }
                    Voir { voir = "Les vidéos qui partiraient" to an.videos }
                }
            }
            if (an.doublons.isNotEmpty()) {
                LigneCase("Fichiers en double", "${an.doublons.size} fichier${if (an.doublons.size > 1) "s" else ""} · ${taille(parts.getValue("doublons"))} · on garde un exemplaire", "doublons" in coches.value, { basculer("doublons") }) {
                    Voir { voir = "Les copies qui partiraient" to an.doublons }
                }
            }
            if (an.applis.isNotEmpty()) {
                LigneCase(
                    "Applis inutilisées depuis 2 mois",
                    "${an.applis.joinToString(", ") { it.nom }} · ${taille(parts.getValue("applis"))} · Android demandera pour chacune",
                    "applis" in coches.value, { basculer("applis") },
                )
            }
            if (an.caches > 0) {
                LigneCase("Fichiers temporaires", "${taille(an.caches)} · les applis les refont au besoin", "caches" in coches.value, { basculer("caches") })
            }
            if (parts.values.all { it == 0L }) {
                BasicText("Rien à nettoyer pour l'instant : le téléphone est bien rangé.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(a.trait))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText("Les fichiers passent 30 jours par la corbeille.", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 19.sp, color = a.encre2))
            BoutonTexteAppli(if (enCours) "Un instant…" else "Libérer ${taille(recup)}", actif = recup > 0 && !enCours) {
                enCours = true
                portee.launch {
                    val av = withContext(Dispatchers.IO) { Stockage.etat(c)?.libre ?: 0 }
                    val jetes = withContext(Dispatchers.IO) {
                        var n = 0
                        if ("videos" in coches.value) an.videos.forEach { n += Stockage.jeter(c, it).size }
                        if ("doublons" in coches.value) an.doublons.forEach { n += Stockage.jeter(c, it).size }
                        if ("caches" in coches.value) Stockage.viderCaches(c, an.caches)
                        n
                    }
                    // Désinstaller reste une décision par appli : Android pose la question pour chacune.
                    if ("applis" in coches.value) an.applis.forEach { app ->
                        c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.paquet}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    val ap = withContext(Dispatchers.IO) { Stockage.etat(c)?.libre ?: 0 }
                    enCours = false
                    maj++
                    val gagne = taille((ap - av).coerceAtLeast(0))
                    x.dire(if (jetes > 0) "$gagne libérés. $jetes fichier${if (jetes > 1) "s sont" else " est"} dans la corbeille pour 30 jours." else "$gagne libérés", null)
                }
            }
        }
        Onglets(1)
    }
    voir?.let { (titre, l) ->
        FeuilleAppli(fermer = { voir = null }, titre = titre) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                l.forEach { f -> LigneFichier(f, "${taille(f.taille)} · dans ${java.io.File(f.chemin).parentFile?.let { Stockage.nomDossier(it) }.orEmpty()}") }
            }
        }
    }
}

@Composable
private fun Voir(onClick: () -> Unit) {
    Box(Modifier.padding(top = 4.dp)) { BoutonTexteAppli("Voir lesquels", style = 't', onClick = onClick) }
}

@Composable
private fun LigneCase(nom: String, second: String, cochee: Boolean, basculer: () -> Unit, plus: @Composable () -> Unit = {}) {
    val a = LocalIdentite.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClickLabel = if (cochee) "Ne pas libérer" else "Libérer", role = Role.Checkbox, onClick = basculer)
            .semantics { stateDescription = if (cochee) "coché" else "non coché" }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.padding(top = 2.dp)) { CaseAppli(cochee) }
        Column(Modifier.weight(1f)) {
            BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
            BasicText(second, style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 19.sp, color = a.encre2))
            plus()
        }
    }
}
