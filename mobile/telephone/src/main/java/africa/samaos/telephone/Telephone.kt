package africa.samaos.telephone

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Avatar
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Filet
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.NavAppli
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.PuceSim
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import africa.samaos.banco.appli.couleursOperateur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Onglet(val nom: String, val icone: String) {
    FAVORIS("Favoris", "M12 3l2.7 5.6l6.1.9l-4.4 4.3l1 6.1L12 17l-5.4 2.9l1-6.1l-4.4-4.3l6.1-.9z"),
    JOURNAL("Journal", Icones.HORLOGE),
    CLAVIER("Clavier", "M6 5h.01 M12 5h.01 M18 5h.01 M6 11h.01 M12 11h.01 M18 11h.01 M6 17h.01 M12 17h.01 M18 17h.01 M12 21h.01"),
    MESSAGERIE("Messagerie", "M7 15a4 4 0 1 0 0-8a4 4 0 1 0 0 8z M17 15a4 4 0 1 0 0-8a4 4 0 1 0 0 8z M7 15h10"),
}

/** Le Téléphone de Sama (maquettes l2-tel). */
class Telephone : ComponentActivity() {
    private var onglet by mutableStateOf(Onglet.CLAVIER)
    private var numero by mutableStateOf("")
    private var page by mutableStateOf<String?>(null)
    private var reprise by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            CompositionLocalProvider(LocalIdentite provides id) {
                BackHandler(enabled = page != null) { page = null }
                when (page) {
                    "filtrage" -> PageFiltrage(reprise) { page = null }
                    else -> if (page?.startsWith("ussd:") == true) {
                        PageUssd(page!!.removePrefix("ussd:")) { page = null }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                when (onglet) {
                                    Onglet.FAVORIS -> PageFavoris(reprise) { numero = it; onglet = Onglet.CLAVIER }
                                    Onglet.JOURNAL -> PageJournal(reprise) { page = "filtrage" }
                                    Onglet.CLAVIER -> PageClavier(numero, { numero = it }) { code -> page = "ussd:$code" }
                                    Onglet.MESSAGERIE -> PageMessagerie()
                                }
                            }
                            NavAppli(Onglet.entries.map { it.nom to it.icone }, onglet.ordinal) { onglet = Onglet.entries[it] }
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

    /** « Composer ce numéro » depuis une autre appli : le clavier, prérempli. Le journal depuis une notification. */
    private fun lire(i: Intent?) {
        when {
            i?.data?.scheme == "tel" -> {
                numero = i.data?.schemeSpecificPart.orEmpty()
                onglet = Onglet.CLAVIER
                page = null
            }
            i?.type == CallLog.Calls.CONTENT_TYPE -> onglet = Onglet.JOURNAL
        }
    }
}

// ——— Clavier (maquette l2-tel-clavier) ———

private val LETTRES = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+")

/** Le pavé de touches : chiffres et lettres ; un appui long sur 0 donne « + ». */
@Composable
fun Pave(compact: Boolean = false, taper: (Char) -> Unit) {
    val a = LocalIdentite.current
    val touches = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')
    Column(Modifier.padding(horizontal = 36.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
        touches.chunked(3).forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                rangee.forEach { t ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(if (compact) 58.dp else 68.dp)
                            .clip(RoundedCornerShape(34.dp))
                            .background(a.champ)
                            .combinedClickable(onClickLabel = "$t", role = Role.Button, onLongClick = { if (t == '0') taper('+') else taper(t) }) { taper(t) }
                            .semantics { contentDescription = "$t" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BasicText("$t", style = TextStyle(fontFamily = Polices.corps, fontSize = 30.sp, lineHeight = 34.sp, color = a.encre))
                        BasicText(LETTRES[t] ?: "", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.9.sp, color = a.encre2))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageClavier(numero: String, changer: (String) -> Unit, ussd: (String) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val sims = remember { Moteur.sims(c) }
    var suggestions by remember { mutableStateOf(emptyList<Personne>()) }
    LaunchedEffect(numero) { suggestions = withContext(Dispatchers.IO) { Moteur.chercher(c, numero) } }
    val operateur = Moteur.operateurDe(numero)
    // Le bouclier : un code de mobile money tapé pendant (ou juste après) un appel avec un inconnu.
    var garde by remember { mutableStateOf<Pair<String, Sim?>?>(null) }
    fun appeler(sim: Sim?) {
        if (numero.isBlank()) return
        val inconnu = Appels.inconnu
        val enLigne = inconnu != null && (inconnu.second == 0L || System.currentTimeMillis() - inconnu.second < 30_000)
        if (Moteur.estUssd(numero) && enLigne && africa.samaos.bouclier.Bouclier.codeMobileMoney(numero) != null &&
            africa.samaos.bouclier.Bouclier.actif(c, africa.samaos.bouclier.Bouclier.Garde.PENDANT_APPEL)
        ) {
            africa.samaos.bouclier.Bouclier.noter(c, "pendant", "Mobile money pendant un appel", "${numero} pendant un appel avec le ${Moteur.formater(inconnu!!.first)}")
            garde = numero to sim
            return
        }
        if (Moteur.estUssd(numero)) ussd(numero + "|" + (sim?.fente ?: 0)) else Moteur.appeler(c, numero, sim)
    }
    garde?.let { (code, sim) ->
        GardePendantAppel(code, fermer = { garde = null }) {
            garde = null
            ussd(code + "|" + (sim?.fente ?: 0))
        }
        return
    }
    Column(Modifier.fillMaxSize().background(a.fond), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp))
            BasicTextField(
                value = Moteur.formater(numero),
                onValueChange = { changer(it.filter { ch -> ch.isDigit() || ch in "+*#" }) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Polices.corps, fontSize = if (numero.length > 12) 28.sp else 36.sp, color = a.encre, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(a.accent),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Numéro" },
            )
            if (numero.isNotEmpty()) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClickLabel = "Effacer", role = Role.Button, onLongClick = { changer("") }) { changer(numero.dropLast(1)) },
                    contentAlignment = Alignment.Center,
                ) { IconeTrait("M9 5h11v14H9l-6-7z M12 9l5 6 M17 9l-5 6", 24.dp, a.encre2) }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
        BasicText(
            when {
                Moteur.estUssd(numero) -> "Code opérateur : la réponse s'affiche ici"
                operateur == "Fixe" -> "Fixe · Côte d'Ivoire"
                operateur != null -> "Mobile · $operateur Côte d'Ivoire"
                else -> " "
            },
            style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2),
        )
        Spacer(Modifier.height(10.dp))
        suggestions.firstOrNull()?.let { p ->
            LigneAppli(p.nom, "${Moteur.formater(p.numero)} · ${p.type}", debut = { Avatar(p.nom) }) { changer(p.numero.filter { it.isDigit() || it == '+' }) }
        }
        Spacer(Modifier.height(10.dp))
        Pave { t -> changer(numero + t) }
        Spacer(Modifier.height(16.dp))
        if (sims.size > 1) {
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                sims.forEach { s ->
                    val (fond, encre) = couleursOperateur(s.operateur)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(a.accent)
                            .clickable(onClickLabel = "Appeler avec la SIM ${s.fente}", role = Role.Button) { appeler(s) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconeTrait(Icones.APPEL, 20.dp, a.surAccent)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(fond), contentAlignment = Alignment.Center) {
                            BasicText("${s.fente}", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = encre))
                        }
                        BasicText(" ${s.operateur}", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.surAccent))
                    }
                }
            }
            // Le conseil : un numéro Orange s'appelle moins cher avec la SIM Orange.
            val meme = sims.firstOrNull { operateur != null && it.operateur.contains(operateur.substringBefore(' '), ignoreCase = true) }
            if (meme != null && numero.length >= 2) {
                BasicText(
                    "Un ${numero.take(2)} est un numéro ${operateur} : la SIM ${meme.fente} coûte moins cher.",
                    modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2, textAlign = TextAlign.Center),
                )
            }
        } else {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(a.accent)
                    .clickable(onClickLabel = "Appeler", role = Role.Button) { appeler(sims.firstOrNull()) }
                    .semantics { contentDescription = "Appeler" },
                contentAlignment = Alignment.Center,
            ) { IconeTrait(Icones.APPEL, 30.dp, a.surAccent, epaisseur = 2f) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ——— Journal (maquette l2-tel-journal) ———

@Composable
private fun PageJournal(reprise: Int, filtrage: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var appels by remember { mutableStateOf(emptyList<Appel>()) }
    var filtre by remember { mutableIntStateOf(0) }
    val sims = remember { Moteur.sims(c) }
    LaunchedEffect(reprise) {
        appels = withContext(Dispatchers.IO) { Moteur.journal(c) }
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
    EcranAppli {
        Tete("Journal") {
            BoutonAppli(Icones.BOUCLIER, "Filtrage des appels", onClick = filtrage)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PuceFiltre("Tous", filtre == 0) { filtre = 0 }
            PuceFiltre("Manqués", filtre == 1) { filtre = 1 }
            if (sims.size > 1) sims.forEach { s -> PuceFiltre(s.operateur, filtre == s.fente + 1, debut = { PuceSim(s.fente, s.operateur) }) { filtre = s.fente + 1 } }
        }
        if (vus.isEmpty()) {
            BasicText("Aucun appel.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            var jour = ""
            vus.forEach { ap ->
                val j = Moteur.titreJour(ap.date)
                if (j != jour) {
                    jour = j
                    item(key = "j$j${ap.date}") { Rub(j) }
                }
                item(key = "a${ap.date}${ap.numero}") { LigneJournal(ap, sims.size > 1) }
            }
        }
    }
}

@Composable
private fun LigneJournal(ap: Appel, plusieursSims: Boolean) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val masque = ap.numero.isBlank()
    val nom = ap.nom ?: if (masque) "Numéro masqué" else Moteur.formater(ap.numero)
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
        genre,
        if (plusieursSims) ap.sim?.fente?.toString() else null,
        Moteur.heure(ap.date),
        Moteur.duree(ap.duree).ifBlank { null },
        if (ap.genre == CallLog.Calls.BLOCKED_TYPE) "vous n'avez pas été dérangé" else null,
    ).joinToString(" · ")
    LigneAppli(
        nom + if (ap.nombre > 1) " (${ap.nombre})" else "",
        second,
        couleurSecond = if (ap.genre == CallLog.Calls.MISSED_TYPE) Color(0xFFC2412D) else null,
        debut = { Avatar(ap.nom, icone = if (ap.nom == null) null else null) },
        fin = {
            if (!masque) BoutonAppli(Icones.APPEL, "Rappeler", style = 'v') { Moteur.appeler(c, ap.numero, ap.sim) }
        },
        surAppuiLong = if (masque) null else ({
            c.startActivity(Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE, ap.numero).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }),
    ) {
        if (!masque) {
            val p = Moteur.personne(c, ap.numero)
            if (p != null) {
                c.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(p.id, p.cle)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                Moteur.appeler(c, ap.numero, ap.sim)
            }
        }
    }
}

// ——— Favoris (maquette l2-tel-favoris) ———

@Composable
private fun PageFavoris(reprise: Int, composer: (String) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var favoris by remember { mutableStateOf(emptyList<Personne>()) }
    var souvent by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    LaunchedEffect(reprise) {
        withContext(Dispatchers.IO) {
            favoris = Moteur.favoris(c)
            souvent = Moteur.souvent(c, favoris.map { it.numero }.toSet())
        }
    }
    EcranAppli {
        Tete("Favoris")
        if (favoris.isEmpty()) {
            BasicText(
                "Marquez vos proches d'une étoile dans Contacts : ils apparaîtront ici, à une touche.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
            )
        } else {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().height(((favoris.size + 2) / 3 * 112).dp), userScrollEnabled = false) {
                items(favoris, key = { it.id }) { p ->
                    Column(
                        Modifier.padding(6.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Appeler ${p.nom}", role = Role.Button) { Moteur.appeler(c, p.numero) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Avatar(p.nom, 64.dp)
                        BasicText(p.nom.substringBefore(' '), modifier = Modifier.padding(top = 6.dp), maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = a.encre))
                    }
                }
            }
        }
        if (souvent.isNotEmpty()) {
            Rub("Appelés souvent")
            souvent.forEach { (numero, n) ->
                val p = remember(numero) { Moteur.personne(c, numero) }
                LigneAppli(
                    p?.nom ?: Moteur.formater(numero),
                    listOfNotNull(p?.type ?: Moteur.operateurDe(numero), "$n appel${if (n > 1) "s" else ""}").joinToString(" · "),
                    debut = { Avatar(p?.nom) },
                    fin = { BoutonAppli(Icones.APPEL, "Appeler", style = 'v') { Moteur.appeler(c, numero) } },
                ) { composer(numero) }
            }
        }
    }
}

// ——— Messagerie (maquette l2-tel-messagerie) ———

@Composable
private fun PageMessagerie() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val sims = remember { Moteur.sims(c) }
    EcranAppli {
        Tete("Messagerie")
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (sims.isEmpty()) {
                BasicText("Aucune SIM.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
            }
            sims.forEach { s ->
                val numero = remember(s) { Moteur.numeroMessagerie(c, s) }
                LigneAppli(
                    "${s.operateur}",
                    if (numero != null) "Messagerie au ${Moteur.formater(numero)}" else "Pas de numéro de messagerie",
                    debut = { PuceSim(s.fente, s.operateur) },
                    fin = { BoutonTexteAppli("Écouter", style = 's') { Moteur.appelerMessagerie(c, s) } },
                )
            }
            BasicText(
                "On écoute ses messages en appelant la messagerie de l'opérateur. Si l'opérateur propose la messagerie visuelle, les messages s'afficheront ici, un par un.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
            )
            Rub("Renvoyer vers la messagerie")
            LigneAppli("Renvois d'appels", "Si vous êtes en ligne, ne répondez pas ou êtes injoignable", fin = { IconeTrait(Icones.CHEVRON, 20.dp, a.encre2) }) {
                c.startActivity(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            BasicText(
                "Un « agent » qui vous demande de composer un code de renvoi (commençant par ** ou *21*) veut recevoir vos appels et vos codes : ne le faites pas.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFFC2412D)),
            )
        }
    }
}

// ——— Filtrage (maquette l2-tel-filtrage) ———

@Composable
private fun PageFiltrage(reprise: Int, retour: () -> Unit) {
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
    EcranAppli {
        Tete("Filtrage des appels", retour = retour, petit = true)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val v = version
            val masques = v >= 0 && prefs.getBoolean(Reglages.MASQUES, false)
            val inconnus = v >= 0 && prefs.getBoolean(Reglages.INCONNUS, false)
            LigneAppli("Bloquer les numéros masqués", "Ils ne font pas sonner le téléphone", fin = { InterAppli(masques) }) {
                prefs.edit().putBoolean(Reglages.MASQUES, !masques).apply()
                version++
            }
            LigneAppli("Bloquer les inconnus", "Numéros absents de vos contacts", fin = { InterAppli(inconnus) }) {
                prefs.edit().putBoolean(Reglages.INCONNUS, !inconnus).apply()
                version++
            }
            Rub("Numéros bloqués")
            bloques.forEach { (n, _) ->
                LigneAppli(Moteur.formater(n), "Bloqué par vous", debut = { Avatar(null) }, fin = {
                    BoutonTexteAppli("Débloquer", style = 't') {
                        Moteur.debloquer(c, n)
                        version++
                    }
                })
            }
            val saisie = ajout
            if (saisie == null) {
                LigneAppli("Bloquer un numéro", debut = { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { IconeTrait(Icones.PLUS, 22.dp, a.accentTexte) } }) { ajout = "" }
            } else {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Rub("Filtrés cette semaine")
            LigneAppli(
                if (filtres == 0) "Aucun appel filtré" else "$filtres appel${if (filtres > 1) "s" else ""}",
                if (filtres == 0) null else "Aucun n'a fait sonner le téléphone",
            )
            Filet()
            BasicText(
                "Les numéros bloqués le sont pour tout le téléphone. Ils restent dans le journal, marqués « filtré ».",
                modifier = Modifier.padding(20.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
            )
        }
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
