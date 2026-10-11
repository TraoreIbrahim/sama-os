package africa.samaos.messages

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.BarreRetour
import africa.samaos.banco.appli.Glisse
import africa.samaos.banco.appli.GrandTitre
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Recherche
import africa.samaos.banco.appli.Rond
import africa.samaos.banco.appli.Segments
import africa.samaos.banco.appli.Visage
import africa.samaos.banco.appli.appui
import africa.samaos.banco.appli.styleDoux
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
fun texte(taille: Float, couleur: Color, poids: FontWeight = FontWeight.Normal): TextStyle = styleDoux(taille, couleur, poids)

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
    // Lu ou non lu d'un glissement : la ligne change tout de suite, avant que la boîte ne se relise.
    var lus by remember { mutableIntStateOf(0) }
    LaunchedEffect(version, lus) { liste = withContext(Dispatchers.IO) { Boite.conversations(c) } }
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
    val sous = when {
        liste == null -> " "
        nonLus == 0 -> "Tout est lu"
        else -> "$nonLus conversation${if (nonLus > 1) "s" else ""} non lue${if (nonLus > 1) "s" else ""}"
    }
    Column(Modifier.fillMaxSize()) {
        if (retour != null) {
            BarreRetour("Messages", retour)
            GrandTitre("Opérateurs et services", sous, sousBarre = true)
        } else {
            GrandTitre("Messages", sous) {
                Rond(Icones.CRAYON, "Nouveau message", plein = true, epaisseur = 2f) { aller(Vue.Nouveau()) }
            }
        }
        Recherche(recherche, if (services) "Un expéditeur, un mot" else "Un nom, un numéro, un mot", { recherche = it }, Modifier.padding(bottom = 6.dp))
        if (!services) {
            Segments(
                listOf("Tout", if (nonLus > 0) "Non lus · $nonLus" else "Non lus", if (inconnus > 0) "Inconnus · $inconnus" else "Inconnus"),
                filtre.ordinal, Modifier.padding(bottom = 2.dp),
            ) { filtre = Filtre.entries[it] }
        }
        // La liste n'apparaît qu'une fois lue : sinon la rangée des proches, arrivée après, resterait au-dessus de l'écran.
        if (liste != null) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp)) {
            if (bord && proches.size >= 2) {
                item(key = "proches") {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        proches.forEach { cv -> Proche(cv) { aller(Vue.Fil(cv.fil, cv.adresse)) } }
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
                val rouge = if (a.sombre) Color(0xFFF08A70) else ARNAQUE
                val nonLu = cv.nonLus > 0
                Glisse(
                    modifier = Modifier.animateItem(),
                    gauche = Triple(Icones.CORBEILLE, "Supprimer", rouge),
                    faireGauche = { Corbeille.jeter(c, cv) },
                    droite = Triple(if (nonLu) Icones.OEIL else Icones.OEIL_BARRE, if (nonLu) "Lu" else "Non lu", a.accent),
                    faireDroite = {
                        if (nonLu) Boite.marquerLu(c, cv.fil) else Boite.marquerNonLu(c, cv.fil)
                        lus++
                    },
                ) { LigneConversation(cv) { aller(Vue.Fil(cv.fil, cv.adresse)) } }
            }
            if (liste != null && vues.isEmpty()) {
                item(key = "vide") {
                    BasicText(
                        when {
                            q.isNotEmpty() -> "Rien ne correspond à « ${recherche.trim()} »."
                            filtre == Filtre.NON_LUS -> "Tout est lu."
                            filtre == Filtre.INCONNUS -> "Aucun message d'un numéro inconnu."
                            services -> "Aucun message d'un opérateur ou d'un service."
                            else -> "Aucun message pour l'instant."
                        },
                        modifier = Modifier.fillMaxWidth().padding(32.dp).animateItem(),
                        style = texte(15f, a.encre2).copy(textAlign = TextAlign.Center),
                    )
                }
            }
            item(key = "pied") {
                BasicText(
                    if (vues.isNotEmpty()) "Glissez une conversation : vers la droite pour la marquer lue ou non lue, vers la gauche pour la supprimer." else "",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp).navigationBarsPadding().padding(bottom = 60.dp),
                    style = texte(13f, a.encre2),
                )
            }
        }
    }
}

/** Un proche en haut de la liste : son visage, cerclé de l'accent s'il a écrit sans être lu ; il s'enfonce un peu sous le doigt. */
@Composable
private fun Proche(cv: Conversation, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val anneau by animateColorAsState(if (cv.nonLus > 0) a.accent else Color.Transparent, label = "anneau")
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val presse by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (presse) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "appui")
    Column(
        Modifier.width(68.dp).graphicsLayer { scaleX = s; scaleY = s }
            .clickable(interactionSource = source, indication = null, onClickLabel = "${cv.nom}, ouvrir la conversation", role = Role.Button, onClick = ouvrir),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(66.dp).border(2.5.dp, anneau, CircleShape).padding(4.dp), contentAlignment = Alignment.Center) {
            Visage(cv.nom, cv.photo, 58.dp)
        }
        BasicText(
            cv.nom.orEmpty().substringBefore(' '), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
            style = texte(13f, a.encre, if (cv.nonLus > 0) FontWeight.Bold else FontWeight.Medium).copy(textAlign = TextAlign.Center),
        )
    }
}

/** Les opérateurs et services, rangés à part dans une seule ligne, comme un dossier. */
@Composable
private fun CarteServices(srv: List<Conversation>, modifier: Modifier, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val d = srv.first()
    val nonLus = srv.count { it.nonLus > 0 }
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(a.surface)
            .clickable(onClickLabel = "Opérateurs et services", role = Role.Button, onClick = ouvrir).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(a.voile), contentAlignment = Alignment.Center) {
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

/** Le visage d'un expéditeur qui n'est pas une personne : ses deux premières lettres sur l'accent voilé. */
@Composable
internal fun VisageService(adresse: String, taille: androidx.compose.ui.unit.Dp) {
    val a = LocalIdentite.current
    Box(Modifier.size(taille).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) {
        BasicText(adresse.filter { it.isLetterOrDigit() }.take(2).uppercase(), style = texte(taille.value * 0.3f, a.accentTexte, FontWeight.Bold))
    }
}

/** Le visage d'un numéro signalé par le bouclier. */
@Composable
internal fun VisageSuspect(taille: androidx.compose.ui.unit.Dp) {
    val a = LocalIdentite.current
    val rouge = if (a.sombre) Color(0xFFF08A70) else ARNAQUE
    Box(Modifier.size(taille).clip(CircleShape).background(rouge.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        IconeTrait(Icones.ALERTE, taille * 0.46f, rouge)
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
                cv.suspect != null -> VisageSuspect(52.dp)
                cv.service -> VisageService(cv.adresse, 52.dp)
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
        Box(Modifier.padding(start = 86.dp).fillMaxWidth().height(0.6.dp).background(a.trait))
    }
}
