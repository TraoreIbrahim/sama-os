package africa.samaos.mail

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.bouclier.Verdict
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal object IconesBoite {
    const val RELEVER = "M20 12a8 8 0 1 1-2.3-5.6 M20 4v4h-4"
    const val ECRIRE = "M12.5 4.5H6.5a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-6 M17 3.5l3.5 3.5l-8 8H9v-3.5z"
    const val LU = "M4 6h16v12H4z M4 7l8 6l8-6"
    const val NON_LU = "M4 6h16v12H4z M4 7l8 6l8-6 M19 4.5a2.5 2.5 0 1 0 0.01 0"
}

/** Ranger un mail (archives, corbeille) un peu plus tard, le temps d'« Annuler » : la ligne disparaît tout de suite. */
internal object Rangement {
    val enAttente = mutableStateListOf<String>()

    fun cle(l: Lettre) = cle(l.compte, l.dossier, l.uid)
    fun cle(compte: String, dossier: String, uid: Long) = "$compte/$dossier/$uid"

    @OptIn(DelicateCoroutinesApi::class)
    fun ranger(c: Context, compte: Compte, dossier: String, uid: Long, role: String) {
        val k = cle(compte.id, dossier, uid)
        enAttente += k
        Avis.dire(if (role == "archives") "Mail archivé" else "Mail mis à la corbeille") { enAttente -= k }
        val app = c.applicationContext
        // Hors de l'écran : quitter la boîte n'empêche pas le rangement.
        GlobalScope.launch(Dispatchers.Main) {
            delay(6500)
            if (k !in enAttente) return@launch
            withContext(Dispatchers.IO) { runCatching { Courrier.deplacer(app, compte, dossier, uid, role) } }
            enAttente -= k
        }
    }
}

/** La section d'un mail dans la boîte. */
private fun sectionDe(t: Long): String {
    val z = ZoneId.systemDefault()
    val j = Instant.ofEpochMilli(t).atZone(z).toLocalDate()
    val auj = LocalDate.now(z)
    val ecart = ChronoUnit.DAYS.between(j, auj)
    return when {
        ecart <= 0L -> "Aujourd'hui"
        ecart == 1L -> "Hier"
        ecart < 7 -> "Cette semaine"
        j.year == auj.year -> j.format(DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
        else -> j.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
    }
}

private val FILTRES = listOf("" to "Tous", "nonlus" to "Non lus", "pj" to "Pièces jointes")

/** La boîte : le dossier en grand titre, la recherche, les mails par jour ; on glisse un mail pour le marquer ou le ranger. */
@Composable
internal fun PageBoite(aller: (Vue) -> Unit, retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val portee = rememberCoroutineScope()
    val version = Courrier.version
    val compte = remember(version) { Comptes.courant(c) } ?: return
    val plusieurs = remember(version) { Comptes.liste(c).size > 1 }
    var liste by remember { mutableStateOf<List<Lettre>?>(null) }
    var premieres by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var verdicts by remember { mutableStateOf<Map<Long, Verdict>>(emptyMap()) }
    var nonLus by remember { mutableIntStateOf(0) }
    var dossiers by remember { mutableStateOf<List<Dossier>>(emptyList()) }
    var releveA by remember { mutableStateOf<Long?>(null) }
    var relancer by remember { mutableIntStateOf(0) }
    val dossier = Ecran.dossier
    LaunchedEffect(version, compte.id, dossier, Ecran.filtre, Ecran.recherche) {
        withContext(Dispatchers.IO) {
            val l = Base.lettres(c, compte.id, dossier, Ecran.filtre, Ecran.recherche)
            premieres = Base.premieresPieces(c, compte.id, dossier)
            verdicts = l.mapNotNull { x -> verdictDe(c, x, if (x.pj) Base.pieces(c, x.compte, x.dossier, x.uid) else emptyList())?.let { x.uid to it } }.toMap()
            nonLus = Base.nonLus(c, compte.id, dossier)
            dossiers = Base.dossiers(c, compte.id)
            liste = l
        }
    }
    // Relever à l'ouverture, puis toutes les deux minutes tant que la boîte est à l'écran (ou d'un toucher).
    LaunchedEffect(compte.id, dossier, relancer) {
        while (true) {
            withContext(Dispatchers.IO) {
                try {
                    Courrier.envoyerEnAttente(c)
                    Courrier.relever(c, compte, dossier)
                } catch (_: Exception) {
                }
            }
            if (Courrier.erreurs[compte.id] == null) releveA = System.currentTimeMillis()
            delay(120_000)
        }
    }
    LaunchedEffect(Courrier.dernierMessage) {
        if (Courrier.dernierMessage != null) {
            delay(4000)
            Courrier.dernierMessage = null
        }
    }
    val d = dossiers.firstOrNull { it.nom == dossier }
    val nom = d?.let { Courrier.nomLisible(it) }?.let { if (it == "Boîte de réception") "Réception" else it } ?: "Réception"
    val roles = dossiers.associate { it.role to it.nom }
    // Glisser vers la gauche : aux archives s'il y en a, sinon à la corbeille (rien depuis la corbeille).
    val rangerVers = when {
        dossier == roles["corbeille"] -> null
        roles["archives"] != null && dossier != roles["archives"] -> "archives"
        else -> "corbeille"
    }
    val visibles = liste?.filter { Rangement.cle(it) !in Rangement.enAttente }
    val sous = listOfNotNull(
        compte.adresse.takeIf { plusieurs },
        when (nonLus) { 0 -> if (d?.role == "envoyes") null else "Tout est lu"; 1 -> "1 non lu"; else -> "$nonLus non lus" },
    ).joinToString(" · ").ifEmpty { null }
    var adresses by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barre("Boîtes", retour) { CapsuleAdresse(compte.adresse) { adresses = true } }
        LazyColumn(Modifier.weight(1f)) {
            item(key = "tete") {
                GrandTitre(nom, sous)
                Recherche(Ecran.recherche, "Rechercher dans « $nom »") { Ecran.recherche = it }
                Spacer(Modifier.height(10.dp))
                Segments(FILTRES.map { it.second }, FILTRES.indexOfFirst { it.first == Ecran.filtre }.coerceAtLeast(0)) { Ecran.filtre = FILTRES[it].first }
            }
            if (visibles != null && visibles.isEmpty()) item(key = "vide") {
                Vide(
                    when {
                        Courrier.releves[compte.id] == true -> "Relève du courrier…"
                        Ecran.recherche.isNotBlank() -> "Aucun mail ne correspond à « ${Ecran.recherche.trim()} »."
                        Ecran.filtre == "nonlus" -> "Tout est lu."
                        Ecran.filtre == "pj" -> "Aucun mail avec une pièce jointe."
                        else -> "Aucun mail ici."
                    },
                    Modifier.animateItem(),
                )
            }
            visibles.orEmpty().groupBy { sectionDe(it.date) }.forEach { (titre, l) ->
                item(key = "s$titre") { Section(titre, Modifier.animateItem()) }
                itemsIndexed(l, key = { _, x -> x.uid }) { i, x ->
                    val ranger: () -> Unit = { rangerVers?.let { Rangement.ranger(c, compte, dossier, x.uid, it) } }
                    Glisse(
                        Modifier.animateItem().padding(horizontal = 16.dp).clip(forme(i, l.size)),
                        droite = Triple(if (x.lu) IconesBoite.NON_LU else IconesBoite.LU, if (x.lu) "Non lu" else "Lu", a.accent),
                        gauche = if (rangerVers == "archives") Triple(IconesMail.ARCHIVES, "Archiver", Color(0xFF6E8B74)) else Triple(Icones.CORBEILLE, "Corbeille", Color(0xFFC4573E)),
                        faireDroite = { portee.launch(Dispatchers.IO) { runCatching { Courrier.marquer(c, compte, dossier, x.uid, lu = !x.lu) } } },
                        faireGauche = ranger,
                    ) {
                        LigneMail(x, premieres[x.uid], verdicts[x.uid], derniere = i == l.lastIndex) { aller(Vue.Lecture(x.compte, x.dossier, x.uid)) }
                    }
                }
            }
            item(key = "fin") { Spacer(Modifier.height(20.dp)) }
        }
        PiedBoite(
            etat = when {
                Courrier.dernierMessage != null -> Courrier.dernierMessage!! to false
                Courrier.releves[compte.id] == true -> "Relève du courrier…" to false
                Courrier.erreurs[compte.id] != null -> "Hors ligne · ${Courrier.erreurs[compte.id]}" to true
                releveA != null -> "Relevé à " + DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(releveA!!).atZone(ZoneId.systemDefault())) to false
                else -> "" to false
            },
            enCours = Courrier.releves[compte.id] == true,
            relever = { relancer++ },
        ) { aller(Vue.Ecrire(Brouillon(compte = compte.id, a = "", sujet = "", texte = ""))) }
    }
    if (adresses) FeuilleAdresses(compte, aller) { adresses = false }
}

/** L'adresse dont on lit le courrier, en haut à droite : un toucher pour en changer, en ajouter une, régler Mail. */
@Composable
private fun CapsuleAdresse(adresse: String, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier.padding(end = 4.dp).height(38.dp).clip(RoundedCornerShape(19.dp)).background(a.voile)
            .clickable(onClickLabel = "Vos adresses mail et les réglages", role = Role.Button, onClick = onClick).padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTrait(IconesMail.ENVELOPPE, 16.dp, a.accentTexte)
        BasicText(adresse, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 7.dp).widthIn(max = 170.dp), style = st(14f, a.accentTexte, FontWeight.SemiBold, 1.1f))
        IconeTrait(Icones.CHEVRON, 12.dp, a.accentTexte, Modifier.padding(start = 4.dp).graphicsLayer { rotationZ = 90f }, epaisseur = 2.2f)
    }
}

/** Vos adresses mail : passer de l'une à l'autre, en ajouter une, ouvrir les réglages. */
@Composable
internal fun FeuilleAdresses(courant: Compte, aller: (Vue) -> Unit, fermer: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    var comptes by remember { mutableStateOf<List<Pair<Compte, Int>>>(emptyList()) }
    LaunchedEffect(Unit) {
        comptes = withContext(Dispatchers.IO) {
            Comptes.liste(c).map { x -> x to Base.nonLus(c, x.id, Base.dossier(c, x.id, "reception")?.nom ?: "INBOX") }
        }
    }
    FeuilleAppli(fermer = fermer, titre = "Vos adresses mail") {
        val f = LocalFermerFeuille.current
        comptes.forEach { (x, n) ->
            val ici = x.id == courant.id
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (ici) x.adresse else "Lire le courrier de ${x.adresse}", role = Role.Button) {
                    if (!ici) {
                        Comptes.choisir(c, x)
                        Ecran.dossier = "INBOX"
                        Ecran.filtre = ""
                        Ecran.recherche = ""
                        Courrier.changer()
                    }
                    f()
                }.padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (ici) a.accent else a.voile), contentAlignment = Alignment.Center) {
                    IconeTrait(IconesMail.ENVELOPPE, 19.dp, if (ici) a.surAccent else a.accentTexte)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    BasicText(x.adresse, maxLines = 1, overflow = TextOverflow.Ellipsis, style = st(16f, a.encre, if (ici) FontWeight.SemiBold else FontWeight.Normal))
                    BasicText(
                        listOfNotNull(x.nom.ifBlank { null }, if (n > 0) "$n non lu${if (n > 1) "s" else ""}" else "Tout est lu").joinToString(" · "),
                        maxLines = 1, style = st(13f, a.encre2),
                    )
                }
                if (ici) IconeTrait(Icones.COCHE, 20.dp, a.accentTexte, epaisseur = 2.2f)
            }
        }
        Separateur(24.dp)
        ActionFeuille(Icones.PLUS, "Ajouter une adresse mail", second = "Gmail, Yahoo, Orange, celle du travail ou de l'école…") {
            f()
            aller(Vue.Ajouter)
        }
        ActionFeuille(Icones.REGLAGES, "Réglages de Mail", second = "Gérer les adresses, la relève, les images") {
            f()
            aller(Vue.Reglages)
        }
    }
}

@Composable
private fun Vide(texte: String, modifier: Modifier) {
    val a = LocalIdentite.current
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(IconesMail.ENVELOPPE, 28.dp, a.accentTexte) }
        BasicText(texte, modifier = Modifier.padding(top = 14.dp), style = st(15f, a.encre2).copy(textAlign = TextAlign.Center))
    }
}

/** Un mail dans la boîte : le point des non lus, l'expéditeur et l'heure, l'objet, deux lignes du texte ; le mot du bouclier s'il le faut. */
@Composable
private fun LigneMail(l: Lettre, piece: String?, verdict: Verdict?, derniere: Boolean, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val nonLu = !l.lu
    val point by animateFloatAsState(if (nonLu) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "point")
    Column(Modifier.fillMaxWidth().background(a.surface).clickable(onClickLabel = "Lire", role = Role.Button, onClick = ouvrir)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(start = 8.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Box(Modifier.width(22.dp).padding(top = 7.dp), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.size(9.dp).graphicsLayer { scaleX = point; scaleY = point; alpha = point }.clip(CircleShape).background(a.accent))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(l.nomAffiche, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = st(16.5f, a.encre, if (nonLu) FontWeight.Bold else FontWeight.SemiBold))
                    if (l.etoile) IconeTrait(IconesMail.ETOILE, 13.dp, a.accentTexte, Modifier.padding(start = 6.dp))
                    if (l.pj) IconeTrait(IconesMail.TROMBONE, 13.dp, a.encre2, Modifier.padding(start = 6.dp))
                    BasicText(dateCourte(l.date), modifier = Modifier.padding(start = 8.dp), style = st(13.5f, a.encre2))
                    IconeTrait(Icones.CHEVRON, 12.dp, a.encre2.copy(alpha = 0.6f), Modifier.padding(start = 4.dp), epaisseur = 2.2f)
                }
                BasicText(l.sujet.ifBlank { "(sans objet)" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp), style = st(15f, a.encre, if (nonLu) FontWeight.Medium else FontWeight.Normal))
                if (l.extrait.isNotBlank()) BasicText(l.extrait, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp), style = st(14f, a.encre2, interligne = 1.38f))
                // Le bouclier parle dans la ligne, sans pastille : un mot en latérite.
                if (verdict != null) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconeTrait(Icones.BOUCLIER, 15.dp, a.accentTexte)
                    BasicText(verdict.titre, modifier = Modifier.padding(start = 6.dp), style = st(13.5f, a.accentTexte, FontWeight.SemiBold))
                    BasicText(" · ne répondez pas", maxLines = 1, overflow = TextOverflow.Ellipsis, style = st(13.5f, a.accentTexte))
                } else if (piece != null) Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconeTrait(IconesMail.TROMBONE, 13.dp, a.encre2)
                    BasicText(piece, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp), style = st(13f, a.encre2))
                }
            }
        }
        if (!derniere) Separateur(30.dp)
    }
}

/** Le pied de la boîte : relever, où en est la relève, et écrire. */
@Composable
private fun PiedBoite(etat: Pair<String, Boolean>, enCours: Boolean, relever: () -> Unit, ecrire: () -> Unit) {
    val a = LocalIdentite.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        val tour = rememberInfiniteTransition(label = "relève")
        val angle by tour.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
        Box(Modifier.align(Alignment.CenterStart).graphicsLayer { rotationZ = if (enCours) angle else 0f }) {
            Rond(IconesBoite.RELEVER, "Relever le courrier", actif = !enCours, onClick = relever)
        }
        AnimatedContent(
            etat, modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp),
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(140)) + slideOutVertically { -it / 3 }) },
            label = "état",
        ) { (t, alerte) ->
            BasicText(t, maxLines = 2, overflow = TextOverflow.Ellipsis, style = st(12.5f, if (alerte) a.accentTexte else a.encre2, FontWeight.Medium, 1.3f).copy(textAlign = TextAlign.Center))
        }
        Box(Modifier.align(Alignment.CenterEnd)) { Rond(IconesBoite.ECRIRE, "Écrire un mail", plein = true, taille = 52.dp, epaisseur = 2f, onClick = ecrire) }
    }
}

private val ORDRE = listOf("reception", "brouillons", "envoyes", "archives", "indesirables", "corbeille")

/** Les boîtes : pour chaque adresse, ses dossiers ; ce qui attend sur le téléphone ; ajouter une boîte. */
@Composable
internal fun PageBoites(aller: (Vue) -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val version = Courrier.version
    var comptes by remember { mutableStateOf<List<Compte>>(emptyList()) }
    var dossiers by remember { mutableStateOf<Map<String, List<Dossier>>>(emptyMap()) }
    var enAttente by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) {
        withContext(Dispatchers.IO) {
            val l = Comptes.liste(c)
            dossiers = l.associate { x ->
                x.id to Base.dossiers(c, x.id).ifEmpty { listOf(Dossier(x.id, "INBOX", "reception", 0)) }
                    .sortedWith(compareBy<Dossier> { ORDRE.indexOf(it.role).let { i -> if (i < 0) ORDRE.size else i } }.thenBy { Courrier.nomLisible(it).lowercase() })
            }
            enAttente = Base.brouillons(c).size
            comptes = l
        }
    }
    val totalNonLus = dossiers.values.flatten().filter { it.role == "reception" }.sumOf { it.nonLus }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Barre(null)
        LazyColumn(Modifier.weight(1f)) {
            item(key = "titre") { GrandTitre("Boîtes") }
            comptes.forEach { compte ->
                val l = dossiers[compte.id].orEmpty()
                item(key = "c" + compte.id) { Section(if (compte.nom.isNotBlank()) "${compte.nom} · ${compte.adresse}" else compte.adresse, Modifier.animateItem()) }
                itemsIndexed(l, key = { _, d -> compte.id + "/" + d.nom }) { i, d ->
                    val compter = d.role != "envoyes" && d.role != "corbeille" && d.role != "brouillons"
                    LigneBoite(
                        iconeDossier(d.role), if (d.role == "reception") a.accentTexte else a.encre2,
                        Courrier.nomLisible(d).let { if (it == "Boîte de réception") "Réception" else it },
                        if (compter && d.nonLus > 0) "${d.nonLus}" else "", i, l.size, Modifier.animateItem(),
                    ) {
                        Comptes.choisir(c, compte)
                        Ecran.dossier = d.nom
                        Ecran.filtre = ""
                        Ecran.recherche = ""
                        Courrier.changer()
                        aller(Vue.Liste)
                    }
                }
            }
            if (enAttente > 0) {
                item(key = "tel") { Section("Sur ce téléphone", Modifier.animateItem()) }
                item(key = "attente") {
                    LigneBoite(Icones.CRAYON, a.encre2, "Brouillons et envois en attente", "$enAttente", 0, 1, Modifier.animateItem()) { aller(Vue.Brouillons) }
                }
            }
            item(key = "ajouter") {
                Spacer(Modifier.height(18.dp))
                LigneBoite(Icones.PLUS, a.accentTexte, "Ajouter une adresse mail", "", 0, 2, Modifier.animateItem()) { aller(Vue.Ajouter) }
                LigneBoite(Icones.REGLAGES, a.encre2, "Réglages de Mail", "", 1, 2, Modifier.animateItem()) { aller(Vue.Reglages) }
                Spacer(Modifier.height(20.dp))
            }
        }
        PiedBoite(
            etat = (if (totalNonLus == 0) "Tout est lu" else if (totalNonLus == 1) "1 non lu" else "$totalNonLus non lus") to false,
            enCours = comptes.any { Courrier.releves[it.id] == true },
            relever = {
                comptes.forEach { x -> kotlin.concurrent.thread { runCatching { Courrier.relever(c.applicationContext, x) } } }
            },
        ) { aller(Vue.Ecrire(Brouillon(compte = Comptes.courant(c)?.id.orEmpty(), a = "", sujet = "", texte = ""))) }
    }
}

@Composable
private fun LigneBoite(icone: String, couleur: Color, nom: String, compte: String, i: Int, n: Int, modifier: Modifier, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(forme(i, n)).background(a.surface).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconeTrait(icone, 22.dp, couleur)
            BasicText(nom, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = st(17f, a.encre))
            if (compte.isNotEmpty()) BasicText(compte, style = st(16f, a.encre2))
            IconeTrait(Icones.CHEVRON, 15.dp, a.encre2.copy(alpha = 0.7f), epaisseur = 2f)
        }
        if (i < n - 1) Separateur(52.dp)
    }
}
