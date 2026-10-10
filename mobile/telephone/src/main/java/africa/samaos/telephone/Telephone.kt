package africa.samaos.telephone

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.BlocAppli
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.FiletBloc
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Visage
import africa.samaos.banco.appli.NavFlottante
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.PuceSim
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Onglet(val nom: String, val icone: String) {
    FAVORIS("Favoris", "M12 4l2.4 5l5.4.6l-4 3.7l1.1 5.3L12 16l-4.9 2.6l1.1-5.3l-4-3.7l5.4-.6z"),
    RECENTS("Récents", Icones.HORLOGE),
    CLAVIER("Clavier", "M6 6h.01 M12 6h.01 M18 6h.01 M6 12h.01 M12 12h.01 M18 12h.01 M6 18h.01 M12 18h.01 M18 18h.01"),
    CONTACTS("Contacts", "M12 8a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z M5 20c1.5-3 4-4.5 7-4.5s5.5 1.5 7 4.5"),
}

/** Le Téléphone de Sama (maquettes l2-tel, refonte du 7 octobre 2026 : telephone-1 à 4). */
class Telephone : ComponentActivity() {
    private var onglet by mutableStateOf(Onglet.CLAVIER)
    private var numero by mutableStateOf("")
    private var page by mutableStateOf<String?>(null)
    private var reprise by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // La première fois, Sama demande s'il peut dire qui appelle (maquette telephone-4).
        if (!Identification.accordDemande(this)) page = "accord"
        lire(intent)
        setContent {
            val nuit = isSystemInDarkTheme()
            val id = if (nuit) Identites.TelephoneNuit else Identites.Telephone
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) {
                BackHandler(enabled = page != null) { page = if (page == "accord-filtrage") "filtrage" else null }
                // Une page ouverte depuis un onglet (filtrage, messagerie…) arrive de la droite ; elle repart vers la droite.
                AnimatedContent(
                    page,
                    transitionSpec = {
                        val profondeur = { x: String? -> when { x == null -> 0; x == "accord-filtrage" -> 2; else -> 1 } }
                        if (profondeur(targetState) >= profondeur(initialState)) {
                            (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(tween(150))) togetherWith (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(220)))
                        } else {
                            (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(240, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(200, delayMillis = 60)))
                        }
                    },
                    label = "page",
                ) { p ->
                    Box(Modifier.fillMaxSize().background(LocalIdentite.current.fond)) {
                        when {
                            p == "accord" -> PageAccord { page = null }
                            p == "accord-filtrage" -> PageAccord { page = "filtrage" }
                            p == "filtrage" -> PageFiltrage(reprise, { page = "accord-filtrage" }) { page = null }
                            p == "messagerie" -> PageMessagerie { page = null }
                            p?.startsWith("ussd:") == true -> PageUssd(p.removePrefix("ussd:")) { page = null }
                            else -> {
                                // D'un onglet à l'autre : un fondu.
                                AnimatedContent(onglet, transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(110)) }, label = "onglet") { o ->
                                    when (o) {
                                        Onglet.FAVORIS -> PageFavoris(reprise) { numero = it; onglet = Onglet.CLAVIER }
                                        Onglet.RECENTS -> PageRecents(reprise, { page = "filtrage" }) { page = "messagerie" }
                                        Onglet.CLAVIER -> PageClavier(numero, reprise, { numero = it }) { code -> page = "ussd:$code" }
                                        Onglet.CONTACTS -> PageContacts(reprise)
                                    }
                                }
                                NavFlottante(Onglet.entries.map { it.nom to it.icone }, onglet.ordinal, { onglet = Onglet.entries[it] }, Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    override fun onResume() {
        super.onResume()
        reprise++
    }

    /** « Composer ce numéro » depuis une autre appli : le clavier, prérempli. Les récents depuis une notification. */
    private fun lire(i: Intent?) {
        when {
            i?.data?.scheme == "tel" -> {
                numero = i.data?.schemeSpecificPart.orEmpty()
                onglet = Onglet.CLAVIER
                if (page != "accord") page = null
            }
            i?.type == CallLog.Calls.CONTENT_TYPE -> onglet = Onglet.RECENTS
        }
    }
}

/** La place que prend la barre d'onglets flottante, à garder libre en bas des pages. */
@Composable
private fun PiedOnglets() = Spacer(Modifier.navigationBarsPadding().height(88.dp))

@Composable
private fun TitrePage(texte: String, actions: @Composable () -> Unit = {}) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(texte, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, color = a.encre))
        actions()
    }
}

@Composable
private fun Rubrique(texte: String) {
    val a = LocalIdentite.current
    BasicText(
        texte, modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.4.sp, color = a.encre2),
    )
}

// ——— Clavier (maquette telephone-1) ———

private val LETTRES = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+")

/** Le pavé de touches rondes : chiffres et lettres ; un appui long sur 0 donne « + ». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Pave(compact: Boolean = false, fond: Color? = null, taper: (Char) -> Unit) {
    val a = LocalIdentite.current
    val taille = if (compact) 64.dp else 72.dp
    val touches = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)) {
        touches.chunked(3).forEach { rangee ->
            Row(Modifier.fillMaxWidth()) {
                rangee.forEach { t ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier
                                .size(taille)
                                .clip(CircleShape)
                                .background(fond ?: a.champ)
                                .combinedClickable(onClickLabel = "$t", role = Role.Button, onLongClick = { if (t == '0') taper('+') else taper(t) }) { taper(t) }
                                .semantics { contentDescription = "$t" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            BasicText("$t", style = TextStyle(fontFamily = Polices.corps, fontSize = if (t == '*') 34.sp else 30.sp, lineHeight = 32.sp, color = a.encre))
                            BasicText(LETTRES[t] ?: "", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.4.sp, color = a.encre2))
                        }
                    }
                }
            }
        }
    }
}

/** La crête qui borde le sol du clavier, comme une dune. */
@Composable
private fun Crete(sol: Color, arriere: Color) {
    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
        val x = size.width / 390f
        val y = size.height / 40f
        val p1 = Path().apply {
            moveTo(0f, 16 * y); cubicTo(60 * x, 4 * y, 140 * x, 2 * y, 210 * x, 12 * y); cubicTo(280 * x, 22 * y, 330 * x, 24 * y, 390 * x, 8 * y)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        val p2 = Path().apply {
            moveTo(0f, 30 * y); cubicTo(80 * x, 16 * y, 160 * x, 14 * y, 230 * x, 24 * y); cubicTo(300 * x, 34 * y, 340 * x, 36 * y, 390 * x, 20 * y)
            lineTo(size.width, size.height + 1); lineTo(0f, size.height + 1); close()
        }
        drawPath(p1, arriere)
        drawPath(p2, sol)
    }
}

/** L'étiquette de ce que Sama sait du numéro tapé : jamais signalé, signalé, entreprise vérifiée. */
@Composable
private fun EtiquetteIdentite(fiche: Identification.Fiche?) {
    val a = LocalIdentite.current
    when (fiche?.genre) {
        Identification.Genre.ARNAQUE -> Etiquette("Signalé · arnaque", if (a.sombre) Color(0x40D9542F) else Color(0xFFFBE3DC), if (a.sombre) Color(0xFFF4A587) else Color(0xFFB4412A), icone = ICONE_ALERTE)
        Identification.Genre.ENTREPRISE -> Etiquette(fiche.nom ?: "Entreprise vérifiée", if (a.sombre) Color(0x335FCB8C) else Color(0xFFE3EFE9), if (a.sombre) Color(0xFF9FE3BE) else Color(0xFF2F6B57), icone = Icones.COCHE)
        null -> Etiquette("Jamais signalé", if (a.sombre) Color(0x297CC2A3) else Color(0xFFE3EFE9), if (a.sombre) Color(0xFF9FE3BE) else Color(0xFF2F6B57), icone = ICONE_BOUCLIER)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageClavier(numero: String, reprise: Int, changer: (String) -> Unit, ussd: (String) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val sims = remember { Moteur.sims(c) }
    var suggestions by remember { mutableStateOf(emptyList<Personne>()) }
    LaunchedEffect(numero) { suggestions = withContext(Dispatchers.IO) { Moteur.chercher(c, numero) } }
    val code = Moteur.estUssd(numero)
    val operateur = if (code) null else Moteur.operateurDe(numero)
    val identifier = remember(reprise) { Identification.actif(c) }
    val fiche = remember(numero, reprise) { if (!code && Identification.cle(numero).length == 10) Identification.chercher(c, numero) else null }
    // Avec deux SIM : celle du même opérateur que le numéro (l'appel coûte moins cher), qu'on peut changer d'un toucher.
    val conseillee = sims.firstOrNull { s -> operateur != null && s.operateur.contains(operateur.substringBefore(' '), ignoreCase = true) }
    var simChoisie by remember(conseillee?.fente) { mutableStateOf(conseillee ?: sims.firstOrNull()) }
    // Le bouclier : un code de mobile money tapé pendant (ou juste après) un appel avec un inconnu.
    var garde by remember { mutableStateOf<Pair<String, Sim?>?>(null) }
    fun appeler(sim: Sim?) {
        if (numero.isBlank()) return
        val inconnu = Appels.inconnu
        val enLigne = inconnu != null && (inconnu.second == 0L || System.currentTimeMillis() - inconnu.second < 30_000)
        if (code && enLigne && africa.samaos.bouclier.Bouclier.codeMobileMoney(numero) != null &&
            africa.samaos.bouclier.Bouclier.actif(c, africa.samaos.bouclier.Bouclier.Garde.PENDANT_APPEL)
        ) {
            africa.samaos.bouclier.Bouclier.noter(c, "pendant", "Mobile money pendant un appel", "${numero} pendant un appel avec le ${Moteur.formater(inconnu!!.first)}")
            garde = numero to sim
            return
        }
        if (code) ussd(numero + "|" + (sim?.fente ?: 0)) else Moteur.appeler(c, numero, sim)
    }
    garde?.let { (g, sim) ->
        GardePendantAppel(g, fermer = { garde = null }) {
            garde = null
            ussd(g + "|" + (sim?.fente ?: 0))
        }
        return
    }
    val compact = LocalConfiguration.current.screenHeightDp < 760
    val sol = Sol.sol()
    Column(Modifier.fillMaxSize().background(a.fond)) {
        Column(Modifier.fillMaxWidth().weight(1f).statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // Le numéro, en grand ; un appui long colle un numéro copié.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 52.dp).combinedClickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                    onLongClickLabel = "Coller un numéro",
                    onLongClick = {
                        val copie = c.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        val chiffres = copie.filter { it.isDigit() || it in "+*#" }
                        if (chiffres.length >= 3) changer(chiffres)
                    },
                ) {},
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    Moteur.formater(numero),
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = "Numéro ${numero}" },
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Light, fontSize = if (numero.length > 12) 30.sp else 40.sp, letterSpacing = 0.6.sp, color = a.encre),
                )
                if (numero.isNotEmpty()) Box(Modifier.padding(start = 2.dp).size(2.dp, 34.dp).background(a.accent))
            }
            Row(Modifier.padding(top = 10.dp).height(30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    code -> Etiquette("Code opérateur · la réponse s'affiche ici", a.champ, a.encre2)
                    operateur != null -> {
                        EtiquetteOperateur(operateur)
                        if (identifier && Identification.cle(numero).length == 10) EtiquetteIdentite(fiche)
                    }
                }
            }
            val s = suggestions.firstOrNull()
            Box(Modifier.height(78.dp).padding(top = 12.dp)) {
                if (s != null) {
                    Row(
                        Modifier.padding(horizontal = 22.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(sol).border(1.dp, a.trait, RoundedCornerShape(18.dp))
                            .clickable(onClickLabel = "Composer le numéro de ${s.nom}", role = Role.Button) { changer(s.numero.filter { it.isDigit() || it == '+' }) }
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Visage(s.nom, s.photo, 40.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            BasicText(s.nom, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = a.encre))
                            BasicText("${Moteur.formater(s.numero)} · ${s.type.lowercase()}", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                        }
                        BoutonAppli(Icones.APPEL, "Appeler ${s.nom}") { Moteur.appeler(c, s.numero, simChoisie) }
                    }
                }
            }
        }
        Crete(sol, Sol.crete())
        Column(Modifier.fillMaxWidth().background(sol).padding(top = if (compact) 6.dp else 14.dp)) {
            Pave(compact, Sol.touche()) { t -> changer(numero + t) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(top = if (compact) 10.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val sc = simChoisie
                    if (sims.size > 1 && sc != null) {
                        Column(
                            Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Changer de SIM", role = Role.Button) {
                                simChoisie = sims[(sims.indexOf(sc) + 1) % sims.size]
                            }.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                PuceSim(sc.fente, sc.operateur)
                                BasicText(sc.operateur, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.encre))
                            }
                            if (sc == conseillee) BasicText("moins cher", style = TextStyle(fontFamily = Polices.corps, fontSize = 11.sp, color = a.encre2))
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .shadow(14.dp, CircleShape, ambientColor = Color(0xFF2A7F58), spotColor = Color(0xFF2A7F58))
                            .size(if (compact) 68.dp else 74.dp)
                            .clip(CircleShape)
                            .background(VERT_APPEL)
                            .clickable(onClickLabel = "Appeler", role = Role.Button) { appeler(simChoisie) }
                            .semantics { contentDescription = "Appeler" },
                        contentAlignment = Alignment.Center,
                    ) { IconeTrait(Icones.APPEL, 30.dp, Color.White, epaisseur = 2.2f) }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (numero.isNotEmpty()) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape)
                                .combinedClickable(onClickLabel = "Effacer", role = Role.Button, onLongClick = { changer("") }) { changer(numero.dropLast(1)) },
                            contentAlignment = Alignment.Center,
                        ) { IconeTrait(ICONE_EFFACER, 28.dp, a.encre2) }
                    }
                }
            }
            PiedOnglets()
        }
    }
}

// ——— Récents ———

/** Ce qu'on sait d'un numéro des récents : la personne des contacts, ou la fiche de l'identification. */
private class Qui(val personne: Personne?, val fiche: Identification.Fiche?)

@Composable
private fun PageRecents(reprise: Int, filtrage: () -> Unit, messagerie: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var appels by remember { mutableStateOf(emptyList<Appel>()) }
    var qui by remember { mutableStateOf(emptyMap<String, Qui>()) }
    var filtre by remember { mutableIntStateOf(0) }
    val sims = remember { Moteur.sims(c) }
    LaunchedEffect(reprise) {
        withContext(Dispatchers.IO) {
            val l = Moteur.journal(c)
            qui = l.map { it.numero }.filter { it.isNotBlank() }.distinct().take(150).associateWith { n ->
                val p = Moteur.personne(c, n)
                Qui(p, if (p == null) Identification.chercher(c, n) else null)
            }
            appels = l
        }
        // On a vu les appels manqués : Android retire sa notification.
        try {
            Moteur.telecom(c).cancelMissedCallsNotification()
        } catch (_: Exception) {
        }
    }
    val vus = appels.filter { ap ->
        when (filtre) {
            0 -> true
            1 -> ap.genre == CallLog.Calls.MISSED_TYPE
            else -> ap.sim?.fente == filtre - 1
        }
    }
    val parJour = remember(vus) { vus.groupBy { Moteur.titreJour(it.date) } }
    Column(Modifier.fillMaxSize()) {
        TitrePage("Récents") {
            BoutonAppli(ICONE_MESSAGERIE, "Messagerie", onClick = messagerie)
            BoutonAppli(ICONE_BOUCLIER, "Filtrage et identification des appels", onClick = filtrage)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PuceFiltre("Tous", filtre == 0) { filtre = 0 }
            PuceFiltre("Manqués", filtre == 1) { filtre = 1 }
            if (sims.size > 1) sims.forEach { s -> PuceFiltre(s.operateur, filtre == s.fente + 1, debut = { PuceSim(s.fente, s.operateur) }) { filtre = s.fente + 1 } }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (vus.isEmpty()) {
                item { BasicText(if (filtre == 1) "Aucun appel manqué." else "Aucun appel.", modifier = Modifier.padding(vertical = 20.dp, horizontal = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2)) }
            }
            parJour.forEach { (jour, l) ->
                item(key = "j$jour") { Rubrique(jour) }
                item(key = "b$jour") {
                    BlocAppli {
                        l.forEachIndexed { i, ap ->
                            if (i > 0) FiletBloc(70.dp)
                            LigneRecent(ap, qui[ap.numero], sims.size > 1)
                        }
                    }
                }
            }
            item { PiedOnglets() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneRecent(ap: Appel, qui: Qui?, plusieursSims: Boolean) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val masque = ap.numero.isBlank()
    val p = qui?.personne
    val f = qui?.fiche
    val nom = p?.nom ?: f?.nom ?: ap.nom ?: Moteur.secours(ap.numero) ?: if (masque) "Numéro masqué" else Moteur.formater(ap.numero)
    val manque = ap.genre == CallLog.Calls.MISSED_TYPE
    val rouge = if (a.sombre) Color(0xFFF08A70) else Color(0xFFC2412D)
    val genre = when (ap.genre) {
        CallLog.Calls.MISSED_TYPE -> "Manqué"
        CallLog.Calls.INCOMING_TYPE -> "Entrant"
        CallLog.Calls.OUTGOING_TYPE -> "Sortant"
        CallLog.Calls.REJECTED_TYPE -> "Refusé"
        CallLog.Calls.BLOCKED_TYPE -> "Filtré"
        CallLog.Calls.VOICEMAIL_TYPE -> "Messagerie"
        else -> "Appel"
    }
    val second = listOfNotNull(
        when (f?.genre) {
            Identification.Genre.ARNAQUE -> "Arnaque signalée"
            Identification.Genre.ENTREPRISE -> "Entreprise vérifiée"
            null -> null
        },
        genre,
        if (plusieursSims) ap.sim?.let { "SIM ${it.fente}" } else null,
        Moteur.heure(ap.date),
        Moteur.duree(ap.duree).ifBlank { null },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp)
            .combinedClickable(
                enabled = !masque,
                onClickLabel = if (p != null) "Voir la fiche" else "Rappeler",
                onLongClickLabel = "Ajouter aux contacts",
                onLongClick = {
                    c.startActivity(Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE, ap.numero).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            ) {
                if (p != null) c.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(p.id, p.cle)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else Moteur.appeler(c, ap.numero, ap.sim)
            }
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (f?.genre == Identification.Genre.ARNAQUE) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(rouge.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { IconeTrait(ICONE_ALERTE, 20.dp, rouge) }
        } else if (f?.genre == Identification.Genre.ENTREPRISE) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(VERT_APPEL), contentAlignment = Alignment.Center) {
                BasicText(f.symbole ?: f.nom.orEmpty().take(1), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Light, fontSize = 22.sp, color = Color.White))
            }
        } else {
            Visage(p?.nom ?: f?.nom, p?.photo, 42.dp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            BasicText(
                nom + if (ap.nombre > 1) " (${ap.nombre})" else "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = if (manque || f?.genre == Identification.Genre.ARNAQUE) rouge else a.encre),
            )
            BasicText(second, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
        }
        if (!masque) BoutonAppli(Icones.APPEL, "Rappeler") { Moteur.appeler(c, ap.numero, ap.sim) }
    }
}

// ——— Favoris ———

@Composable
private fun PageFavoris(reprise: Int, composer: (String) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var favoris by remember { mutableStateOf(emptyList<Personne>()) }
    var souvent by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    var qui by remember { mutableStateOf(emptyMap<String, Qui>()) }
    LaunchedEffect(reprise) {
        withContext(Dispatchers.IO) {
            favoris = Moteur.favoris(c)
            val s = Moteur.souvent(c, favoris.map { it.numero }.toSet())
            qui = s.associate { (n, _) -> n to Moteur.personne(c, n).let { p -> Qui(p, if (p == null) Identification.chercher(c, n) else null) } }
            // Un numéro signalé comme arnaque n'est jamais proposé, même s'il a beaucoup appelé.
            souvent = s.filter { (n, _) -> qui[n]?.fiche?.genre != Identification.Genre.ARNAQUE }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TitrePage("Favoris")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (favoris.isEmpty()) {
                BasicText(
                    "Marquez vos proches d'une étoile dans Contacts : ils apparaîtront ici, à une touche.",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
                )
            }
            favoris.chunked(3).forEach { rangee ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    rangee.forEach { p ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable(onClickLabel = "Appeler ${p.nom}", role = Role.Button) { Moteur.appeler(c, p.numero) }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Visage(p.nom, p.photo, 74.dp)
                            BasicText(p.nom.substringBefore(' '), modifier = Modifier.padding(top = 8.dp), maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = a.encre))
                            BasicText(p.type.lowercase(), style = TextStyle(fontFamily = Polices.corps, fontSize = 12.5.sp, color = a.encre2))
                        }
                    }
                    repeat(3 - rangee.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (souvent.isNotEmpty()) {
                Rubrique("Appelés souvent")
                BlocAppli {
                    souvent.forEachIndexed { i, (numero, n) ->
                        if (i > 0) FiletBloc(70.dp)
                        val p = qui[numero]?.personne
                        val f = qui[numero]?.fiche
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { composer(numero) }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Visage(p?.nom, p?.photo, 42.dp)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                BasicText(p?.nom ?: f?.nom ?: Moteur.secours(numero) ?: Moteur.formater(numero), maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = a.encre))
                                BasicText(listOfNotNull(p?.type ?: f?.categorie ?: Moteur.operateurDe(numero), "$n appel${if (n > 1) "s" else ""} ce mois-ci").joinToString(" · "), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                            }
                            BoutonAppli(Icones.APPEL, "Appeler") { Moteur.appeler(c, numero) }
                        }
                    }
                }
            }
            PiedOnglets()
        }
    }
}

// ——— Contacts ———

@Composable
private fun PageContacts(reprise: Int) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var tous by remember { mutableStateOf(emptyList<Personne>()) }
    var recherche by remember { mutableStateOf("") }
    LaunchedEffect(reprise) { tous = withContext(Dispatchers.IO) { Moteur.tous(c) } }
    fun simple(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
    val chiffres = recherche.filter { it.isDigit() }
    val vus = tous.filter {
        recherche.isBlank() || simple(it.nom).contains(simple(recherche)) || (chiffres.length >= 3 && it.numero.filter { ch -> ch.isDigit() }.contains(chiffres))
    }
    val parLettre = remember(vus) { vus.groupBy { simple(it.nom).firstOrNull()?.takeIf { ch -> ch.isLetter() }?.uppercase() ?: "#" } }
    Column(Modifier.fillMaxSize()) {
        TitrePage("Contacts") {
            BoutonAppli(Icones.PLUS, "Nouveau contact") {
                c.startActivity(Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        ChampAppli(recherche, "Nom ou numéro", { recherche = it })
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (tous.isEmpty()) {
                item { BasicText("Aucun contact avec un numéro.", modifier = Modifier.padding(vertical = 20.dp, horizontal = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2)) }
            }
            parLettre.forEach { (lettre, l) ->
                item(key = "l$lettre") { Rubrique(lettre) }
                item(key = "b$lettre") {
                    BlocAppli {
                        l.forEachIndexed { i, p ->
                            if (i > 0) FiletBloc(68.dp)
                            Row(
                                Modifier.fillMaxWidth().clickable(onClickLabel = "Voir la fiche de ${p.nom}", role = Role.Button) {
                                    c.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(p.id, p.cle)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Visage(p.nom, p.photo, 40.dp)
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    BasicText(p.nom, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = a.encre))
                                    BasicText("${Moteur.formater(p.numero)} · ${p.type.lowercase()}", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                                }
                                BoutonAppli(Icones.APPEL, "Appeler ${p.nom}") { Moteur.appeler(c, p.numero) }
                            }
                        }
                    }
                }
            }
            item { PiedOnglets() }
        }
    }
}

// ——— Messagerie ———

@Composable
private fun PageMessagerie(retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val sims = remember { Moteur.sims(c) }
    EcranAppli {
        Tete("Messagerie", retour = retour, petit = true)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (sims.isEmpty()) {
                BasicText("Aucune SIM.", modifier = Modifier.padding(4.dp, 12.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
            } else {
                BlocAppli(Modifier.padding(top = 8.dp)) {
                    sims.forEachIndexed { i, s ->
                        if (i > 0) FiletBloc()
                        val numero = remember(s) { Moteur.numeroMessagerie(c, s) }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PuceSim(s.fente, s.operateur)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                BasicText(s.operateur, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = a.encre))
                                BasicText(if (numero != null) "Messagerie au ${Moteur.formater(numero)}" else "Pas de numéro de messagerie", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                            }
                            BoutonTexteAppli("Écouter", style = 's') { Moteur.appelerMessagerie(c, s) }
                        }
                    }
                }
            }
            BasicText(
                "On écoute ses messages en appelant la messagerie de l'opérateur. Si l'opérateur propose la messagerie visuelle, les messages s'afficheront ici, un par un.",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
            )
            Rubrique("Renvoyer vers la messagerie")
            BlocAppli {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { c.startActivity(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText("Renvois d'appels", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = a.encre))
                        BasicText("Si vous êtes en ligne, ne répondez pas ou êtes injoignable", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                    }
                    IconeTrait(Icones.CHEVRON, 18.dp, a.encre2)
                }
            }
            BasicText(
                "Un « agent » qui vous demande de composer un code de renvoi (commençant par ** ou *21*) veut recevoir vos appels et vos codes : ne le faites pas.",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp).navigationBarsPadding(),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = if (a.sombre) Color(0xFFF08A70) else Color(0xFFC2412D)),
            )
        }
    }
}

// ——— Filtrage et identification ———

@Composable
private fun PageFiltrage(reprise: Int, accord: () -> Unit, retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val prefs = remember { Reglages.prefs(c) }
    var version by remember { mutableIntStateOf(0) }
    var bloques by remember { mutableStateOf(emptyList<Pair<String, Long>>()) }
    var filtres by remember { mutableIntStateOf(0) }
    var ajout by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version, reprise) {
        withContext(Dispatchers.IO) {
            bloques = Moteur.bloques(c)
            filtres = Moteur.filtresSemaine(c)
        }
    }
    val v = version
    val identifier = v >= 0 && Identification.actif(c)
    val masques = v >= 0 && prefs.getBoolean(Reglages.MASQUES, false)
    val inconnus = v >= 0 && prefs.getBoolean(Reglages.INCONNUS, false)
    val signales = remember(v, reprise) { Identification.signales(c) }
    EcranAppli {
        Tete("Filtrage et identification", retour = retour, petit = true)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            BlocAppli(Modifier.padding(top = 8.dp)) {
                LigneReglage("Savoir qui appelle", "Numéros signalés comme arnaque et entreprises vérifiées, d'après une liste gardée sur le téléphone", identifier) {
                    if (identifier) Identification.regler(c, false) else accord()
                    version++
                }
                FiletBloc()
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = accord).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("Comment ça marche", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.accentTexte))
                    IconeTrait(Icones.CHEVRON, 16.dp, a.encre2)
                }
            }
            Rubrique("Ne pas faire sonner")
            BlocAppli {
                LigneReglage("Les numéros masqués", null, masques) {
                    prefs.edit().putBoolean(Reglages.MASQUES, !masques).apply()
                    version++
                }
                FiletBloc()
                LigneReglage("Les inconnus", "Numéros absents de vos contacts", inconnus) {
                    prefs.edit().putBoolean(Reglages.INCONNUS, !inconnus).apply()
                    version++
                }
            }
            Rubrique("Numéros bloqués")
            BlocAppli {
                bloques.forEachIndexed { i, (n, _) ->
                    if (i > 0) FiletBloc()
                    val signale = signales.any { it.numero == Identification.cle(n) }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(Moteur.formater(n), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = a.encre))
                            BasicText(if (signale) "Bloqué et signalé par vous" else "Bloqué par vous", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                        }
                        BoutonTexteAppli("Débloquer", style = 't') {
                            Moteur.debloquer(c, n)
                            Identification.retirerSignalement(c, n)
                            version++
                        }
                    }
                }
                if (bloques.isNotEmpty()) FiletBloc()
                val saisie = ajout
                if (saisie == null) {
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { ajout = "" }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconeTrait(Icones.PLUS, 18.dp, a.accentTexte)
                        BasicText("Bloquer un numéro", modifier = Modifier.padding(start = 10.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.accentTexte))
                    }
                } else {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = saisie,
                            onValueChange = { ajout = it.filter { ch -> ch.isDigit() || ch == '+' } },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 18.sp, color = a.encre),
                            cursorBrush = SolidColor(a.accent),
                            modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(a.champ).padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        BoutonTexteAppli("Bloquer", actif = saisie.length >= 6) {
                            Moteur.bloquer(c, saisie)
                            ajout = null
                            version++
                        }
                    }
                }
            }
            BasicText(
                (if (filtres == 0) "Aucun appel filtré cette semaine." else "$filtres appel${if (filtres > 1) "s filtrés" else " filtré"} cette semaine : aucun n'a fait sonner le téléphone.") +
                    " Les numéros bloqués le sont pour tout le téléphone ; ils restent dans les récents, marqués « filtré ».",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp).navigationBarsPadding(),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
            )
        }
    }
}

@Composable
private fun LigneReglage(titre: String, detail: String?, allume: Boolean, basculer: () -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().clickable(role = Role.Switch, onClick = basculer).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = a.encre))
            if (detail != null) BasicText(detail, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = a.encre2))
        }
        InterAppli(allume)
    }
}

// ——— L'accord, avant d'identifier les appels (maquette telephone-4) ———

@Composable
private fun PageAccord(fermer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var local by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(a.fond).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(30.dp))
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(VERT_APPEL), contentAlignment = Alignment.Center) {
                IconeTrait(ICONE_BOUCLIER, 32.dp, Color.White, epaisseur = 2f)
            }
            BasicText(
                "Savoir qui appelle,\navant de décrocher",
                modifier = Modifier.padding(top = 20.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp, color = a.encre),
            )
            BasicText(
                "Quand un numéro inconnu vous appelle, Sama vous dit s'il a été signalé comme arnaque, ou s'il s'agit d'une entreprise vérifiée.",
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 22.sp, color = a.encre2),
            )
            BlocAppli(Modifier.padding(top = 20.dp)) {
                LigneAccord(Icones.TELEPHONE, if (a.sombre) Color(0x297CC2A3) else Color(0xFFE3EFE9), if (a.sombre) Color(0xFF9FE3BE) else Color(0xFF2F6B57), "Sur le téléphone",
                    "La liste des numéros signalés arrive avec les mises à jour. Rien ne part quand on vous appelle.", local, true) { local = !local }
                FiletBloc(68.dp)
                LigneAccord(Icones.NUAGE, if (a.sombre) Color(0x299DB2E3) else Color(0xFFE8EEF9), if (a.sombre) Color(0xFF9DB2E3) else Color(0xFF3D5A99), "En plus, en ligne",
                    "Arrivera avec le service Sama : le numéro qui appelle sera vérifié pour les signalements du jour. Seul ce numéro partira.", false, false) {}
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconeTrait(Icones.OEIL, 18.dp, a.accentTexte, Modifier.padding(top = 1.dp))
                BasicText(
                    "Vos contacts ne sont jamais envoyés à personne. Un numéro que vous signalez reste sur ce téléphone ; quand le service Sama ouvrira, il partira de façon anonyme : seuls le numéro et le type d'arnaque.",
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 13.5.sp, lineHeight = 19.sp, color = a.encre2),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Plus tard",
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) {
                    Identification.plusTard(c)
                    fermer()
                }.padding(10.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre2),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(a.encre)
                    .clickable(role = Role.Button) {
                        Identification.regler(c, local)
                        fermer()
                    }.padding(horizontal = 30.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(if (local) "Activer" else "Continuer", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.fond)) }
        }
    }
}

@Composable
private fun LigneAccord(icone: String, fond: Color, encre: Color, titre: String, texte: String, allume: Boolean, actif: Boolean, basculer: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = actif, role = Role.Switch, onClick = basculer).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(fond), contentAlignment = Alignment.Center) { IconeTrait(icone, 20.dp, encre) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = if (actif) a.encre else a.encre2))
            BasicText(texte, modifier = Modifier.padding(top = 2.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.5.sp, lineHeight = 19.sp, color = a.encre2))
        }
        Box(Modifier.then(if (actif) Modifier else Modifier.alpha(0.4f).semantics { contentDescription = "Pas encore disponible" })) { InterAppli(allume) }
    }
}

// ——— Le bouclier : mobile money pendant un appel (maquette i2-pendant-appel) ———

@Composable
private fun GardePendantAppel(code: String, fermer: () -> Unit, composer: () -> Unit) {
    val a = LocalIdentite.current
    val op = remember(code) { africa.samaos.bouclier.Bouclier.codeMobileMoney(code) }
    var maintenant by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            maintenant = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }
    val inconnu = Appels.inconnu
    val enCours = inconnu?.second == 0L
    val reste = if (inconnu == null || enCours) 30 else (30 - (maintenant - inconnu.second) / 1000).toInt().coerceAtLeast(0)
    androidx.activity.compose.BackHandler(onBack = fermer)
    africa.samaos.banco.appli.CacherBarreFlottante()
    val laterite = androidx.compose.ui.graphics.Color(0xFFEE9A78)
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF14211C)).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        BasicText(op?.nom ?: "Mobile money", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = androidx.compose.ui.graphics.Color(0xFFA9B8B1)))
        BasicText(
            if (enCours) "En ligne · ${Moteur.formater(inconnu?.first.orEmpty())}" else "Appel terminé avec le ${Moteur.formater(inconnu?.first.orEmpty())}",
            style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = androidx.compose.ui.graphics.Color(0xFFA9B8B1)),
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconeTrait("M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6z M12 8v5 M12 16h.01", 26.dp, laterite)
            BasicText("Attention", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = laterite))
        }
        Spacer(Modifier.height(12.dp))
        BasicText(
            if (enCours) "Vous êtes en ligne avec un inconnu" else "Vous venez de parler avec un inconnu",
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, color = androidx.compose.ui.graphics.Color(0xFFF1F4F2)),
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            "Un vrai agent ne vous demandera jamais d'envoyer de l'argent ni votre code secret pendant un appel.",
            style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = androidx.compose.ui.graphics.Color(0xFFD9E2DD)),
        )
        Spacer(Modifier.weight(1f))
        if (enCours) {
            BoutonTexteAppli("Raccrocher", icone = Icones.APPEL) { Appels.principal()?.disconnect() }
            Spacer(Modifier.height(12.dp))
            BasicText(
                "Le code $code attendra 30 secondes après la fin de l'appel. Personne de sérieux ne vous presse.",
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = androidx.compose.ui.graphics.Color(0xFFA9B8B1)),
            )
        } else if (reste > 0) {
            BasicText("Le code $code sera possible dans $reste s.", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = androidx.compose.ui.graphics.Color(0xFFF1F4F2)))
        } else {
            BoutonTexteAppli("Composer $code", onClick = composer)
        }
        Spacer(Modifier.height(12.dp))
        BoutonTexteAppli("Ne rien faire", style = 't', onClick = fermer)
    }
}

// ——— USSD (maquette l2-tel-ussd) ———

@Composable
private fun PageUssd(demande: String, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val code = demande.substringBefore('|')
    val sim = remember { Moteur.sims(c).firstOrNull { it.fente == demande.substringAfter('|').toIntOrNull() } ?: Moteur.sims(c).firstOrNull() }
    var reponse by remember { mutableStateOf<String?>(null) }
    var attente by remember { mutableStateOf(true) }
    LaunchedEffect(code) {
        Moteur.ussd(c, code, sim) {
            reponse = it
            attente = false
        }
    }
    EcranAppli {
        Tete(code, retour = fermer, petit = true)
        Column(Modifier.padding(20.dp).clip(RoundedCornerShape(24.dp)).background(a.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("Réponse de l'opérateur", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = a.encre))
            if (sim != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PuceSim(sim.fente, sim.operateur)
                    BasicText(" ${sim.operateur} · $code", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
                }
            }
            BasicText(
                when {
                    attente -> "En attente de l'opérateur…"
                    reponse == null -> "L'opérateur n'a pas répondu. Vérifiez le code et le réseau, puis réessayez."
                    else -> reponse!!
                },
                style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 25.sp, color = a.encre),
            )
            val menu = reponse?.lines()?.any { it.trim().matches(Regex("^\\d+[.).]?\\s.*")) } == true
            if (menu) {
                BasicText(
                    "Pour choisir dans ce menu, composez le code complet (par exemple ${code.dropLast(1)}*1#).",
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { BoutonTexteAppli("Fermer", style = 's', onClick = fermer) }
        }
        BasicText(
            "Votre code secret de mobile money ne se tape jamais pour quelqu'un d'autre, même un « agent ».",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
        )
    }
}
