package africa.samaos.messages

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.Glissable
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.Visage
import africa.samaos.banco.appli.appui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Le rouge des arnaques probables. */
val ARNAQUE = Color(0xFFD9542F)

/** Les textes de Messages. */
fun texte(taille: Float, couleur: Color, poids: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Polices.corps, fontWeight = poids, fontSize = taille.sp, lineHeight = (taille * 1.35f).sp, color = couleur)

/** Un message tel qu'on l'affiche en aperçu : sans le code des alertes du bouclier, sur une ligne. */
fun apercu(corps: String) = corps.replace(Regex("\\s*#SA1\\.\\S+\\s*$"), "").replace('\n', ' ')

/**
 * Une conversation supprimée disparaît tout de suite mais n'est effacée que 6 secondes et demie plus tard :
 * « Annuler » la fait revenir. Si l'appli se ferme avant, rien n'est effacé.
 */
object Corbeille {
    val enAttente = mutableStateListOf<Long>()
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun jeter(c: Context, cv: Conversation) {
        val fil = cv.fil
        enAttente += fil
        Avis.dire("Conversation avec ${cv.nom ?: Boite.formater(cv.adresse)} supprimée") { enAttente.remove(fil) }
        portee.launch {
            // Un peu après la fin du message « Annuler », pour qu'un toucher tardif ne trouve jamais une conversation déjà effacée.
            delay(6500)
            if (fil in enAttente) {
                withContext(Dispatchers.IO) { Boite.supprimer(c.applicationContext, fil) }
                enAttente.remove(fil)
            }
        }
    }
}

private enum class Filtre(val nom: String) { TOUT("Tout"), NON_LUS("Non lus"), INCONNUS("Inconnus") }

private fun simple(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

@Composable
internal fun PageListe(version: Int, services: Boolean, aller: (Vue) -> Unit, retour: (() -> Unit)?) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var liste by remember { mutableStateOf<List<Conversation>?>(null) }
    var recherche by remember { mutableStateOf("") }
    var filtre by remember { mutableStateOf(Filtre.TOUT) }
    LaunchedEffect(version) { liste = withContext(Dispatchers.IO) { Boite.conversations(c) } }
    val toutes = liste.orEmpty().filter { it.fil !in Corbeille.enAttente }
    val (srv, gens) = toutes.partition { it.service }
    val base = if (services) srv else gens
    val q = simple(recherche.trim())
    val vues = base
        .filter { q.isEmpty() || simple(it.nom ?: it.adresse).contains(q) || simple(it.dernier).contains(q) || it.adresse.filter { ch -> ch.isDigit() }.contains(q.filter { ch -> ch.isDigit() }.ifEmpty { "§" }) }
        .filter {
            when (filtre) {
                Filtre.TOUT -> true
                Filtre.NON_LUS -> it.nonLus > 0
                Filtre.INCONNUS -> it.nom == null
            }
        }
    val nonLus = base.count { it.nonLus > 0 }
    val inconnus = gens.count { it.nom == null }
    // Les proches en haut : les favoris d'abord, puis ceux à qui on écrit le plus récemment.
    val proches = gens.filter { it.nom != null }.sortedWith(compareByDescending<Conversation> { it.favori }.thenByDescending { it.date }).take(8)
    val bord = !services && q.isEmpty() && filtre == Filtre.TOUT
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (retour != null) 4.dp else 22.dp, end = 14.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (retour != null) BoutonAppli("M15 6l-6 6l6 6", "Retour", onClick = retour)
            Column(Modifier.weight(1f)) {
                BasicText(if (services) "Opérateurs et services" else "Messages", style = texte(if (services) 24f else 32f, a.encre, FontWeight.Bold).copy(lineHeight = 38.sp))
                BasicText(
                    when {
                        liste == null -> " "
                        nonLus == 0 -> "Tout est lu"
                        else -> "$nonLus conversation${if (nonLus > 1) "s" else ""} non lue${if (nonLus > 1) "s" else ""}"
                    },
                    style = texte(15f, a.encre2),
                )
            }
            if (!services) {
                Box(
                    Modifier.appui("Nouveau message") { aller(Vue.Nouveau()) }.size(52.dp).clip(CircleShape).background(a.accent),
                    contentAlignment = Alignment.Center,
                ) { IconeTrait(Icones.CRAYON, 22.dp, a.surAccent, epaisseur = 2f) }
            }
        }
        ChampAppli(recherche, if (services) "Rechercher un expéditeur" else "Rechercher un nom, un numéro, un mot", { recherche = it }, Modifier.padding(top = 6.dp))
        // La liste n'apparaît qu'une fois lue : sinon la rangée des proches, arrivée après, resterait au-dessus de l'écran.
        if (liste != null) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp)) {
            if (bord && proches.size >= 2) {
                item(key = "proches") {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        proches.forEach { cv -> Proche(cv) { aller(Vue.Fil(cv.fil, cv.adresse)) } }
                    }
                }
            }
            if (!services) {
                item(key = "filtres") {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PuceFiltre("Tout", filtre == Filtre.TOUT) { filtre = Filtre.TOUT }
                        PuceFiltre(if (nonLus > 0) "Non lus · $nonLus" else "Non lus", filtre == Filtre.NON_LUS) { filtre = Filtre.NON_LUS }
                        PuceFiltre(if (inconnus > 0) "Inconnus · $inconnus" else "Inconnus", filtre == Filtre.INCONNUS) { filtre = Filtre.INCONNUS }
                    }
                }
            }
            if (bord && srv.isNotEmpty()) {
                item(key = "services") { CarteServices(srv, Modifier.animateItem()) { aller(Vue.Services) } }
            }
            if (filtre == Filtre.INCONNUS && vues.isNotEmpty()) {
                item(key = "conseil") {
                    BasicText(
                        "Les numéros absents de vos contacts. Une arnaque commence souvent ici : un « agent », un gain, une erreur de transfert.",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp).animateItem(),
                        style = texte(13.5f, a.encre2),
                    )
                }
            }
            items(vues, key = { it.fil }) { cv ->
                Glissable(supprimer = { Corbeille.jeter(c, cv) }, modifier = Modifier.animateItem()) {
                    LigneConversation(cv) { aller(Vue.Fil(cv.fil, cv.adresse)) }
                }
            }
            if (liste != null && vues.isEmpty()) {
                item(key = "vide") {
                    BasicText(
                        when {
                            q.isNotEmpty() -> "Rien ne correspond à « ${recherche.trim()} »."
                            filtre == Filtre.NON_LUS -> "Tout est lu."
                            filtre == Filtre.INCONNUS -> "Aucun message d'un numéro inconnu."
                            else -> "Aucun message pour l'instant."
                        },
                        modifier = Modifier.fillMaxWidth().padding(32.dp).animateItem(),
                        style = texte(15f, a.encre2).copy(textAlign = TextAlign.Center),
                    )
                }
            }
            item(key = "pied") {
                BasicText(
                    if (vues.isNotEmpty()) "Glissez une conversation vers la gauche pour la supprimer." else "",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp).navigationBarsPadding().padding(bottom = 60.dp),
                    style = texte(13f, a.encre2),
                )
            }
        }
    }
}

/** Un proche en haut de la liste : son visage, cerclé de l'accent s'il a écrit sans être lu. */
@Composable
private fun Proche(cv: Conversation, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val anneau by animateColorAsState(if (cv.nonLus > 0) a.accent else Color.Transparent, label = "anneau")
    Column(Modifier.width(68.dp).appui("${cv.nom}, ouvrir la conversation", onClick = ouvrir), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(66.dp).border(2.5.dp, anneau, CircleShape).padding(4.dp), contentAlignment = Alignment.Center) {
            Visage(cv.nom, cv.photo, 58.dp)
        }
        BasicText(
            cv.nom.orEmpty().substringBefore(' '), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
            style = texte(13f, a.encre, if (cv.nonLus > 0) FontWeight.Bold else FontWeight.Medium).copy(textAlign = TextAlign.Center),
        )
    }
}

/** Les opérateurs et services, rangés à part dans une seule carte. */
@Composable
private fun CarteServices(srv: List<Conversation>, modifier: Modifier, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val d = srv.first()
    val nonLus = srv.count { it.nonLus > 0 }
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(a.surface)
            .clickable(onClickLabel = "Opérateurs et services", role = Role.Button, onClick = ouvrir).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(a.voile), contentAlignment = Alignment.Center) {
            IconeTrait(Icones.DOSSIER, 24.dp, a.accentTexte)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Opérateurs et services", modifier = Modifier.weight(1f), style = texte(16f, a.encre, if (nonLus > 0) FontWeight.Bold else FontWeight.SemiBold))
                BasicText(Boite.quand(d.date), style = texte(13f, if (nonLus > 0) a.accentTexte else a.encre2))
            }
            BasicText(
                "${srv.size} expéditeur${if (srv.size > 1) "s" else ""}" + if (nonLus > 0) " · $nonLus non lu${if (nonLus > 1) "s" else ""}" else " · ${Boite.formater(d.adresse)} : ${apercu(d.dernier)}",
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = texte(14f, a.encre2),
            )
        }
        IconeTrait(Icones.CHEVRON, 16.dp, a.encre2)
    }
}

/** Une conversation : le point de non-lu, le visage, le nom et l'heure, l'aperçu ; une arnaque probable se voit d'emblée. */
@Composable
private fun LigneConversation(cv: Conversation, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val nonLu = cv.nonLus > 0
    val rouge = if (a.sombre) Color(0xFFF08A70) else ARNAQUE
    Column(Modifier.fillMaxWidth().background(a.fond)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Ouvrir", role = Role.Button, onClick = ouvrir).padding(start = 4.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(nonLu, enter = scaleIn(), exit = scaleOut()) { Box(Modifier.size(9.dp).clip(CircleShape).background(a.accent)) }
            }
            when {
                cv.suspect != null -> Box(Modifier.size(52.dp).clip(CircleShape).background(rouge.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    IconeTrait(Icones.ALERTE, 24.dp, rouge)
                }
                cv.service -> Box(Modifier.size(52.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) {
                    BasicText(cv.adresse.filter { it.isLetterOrDigit() }.take(2).uppercase(), style = texte(16f, a.accentTexte, FontWeight.Bold))
                }
                else -> Visage(cv.nom, cv.photo, 52.dp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        cv.nom ?: Boite.formater(cv.adresse), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        style = texte(16.5f, a.encre, if (nonLu) FontWeight.Bold else FontWeight.SemiBold),
                    )
                    BasicText(Boite.quand(cv.date), modifier = Modifier.padding(start = 8.dp), style = texte(13f, if (nonLu) a.accentTexte else a.encre2, if (nonLu) FontWeight.SemiBold else FontWeight.Normal))
                }
                if (cv.suspect != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                        IconeTrait(Icones.BOUCLIER, 14.dp, rouge)
                        BasicText(cv.suspect.titre, modifier = Modifier.padding(start = 5.dp), style = texte(13.5f, rouge, FontWeight.SemiBold))
                    }
                }
                BasicText(
                    (if (!cv.recu) "Vous : " else "") + apercu(cv.dernier), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = texte(14.5f, if (nonLu) a.encre else a.encre2),
                )
            }
        }
        Box(Modifier.padding(start = 86.dp).fillMaxWidth().height(1.dp).background(a.trait))
    }
}
