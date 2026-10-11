package africa.samaos.contacts

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.BarreRetour
import africa.samaos.banco.appli.GrandTitre
import africa.samaos.banco.appli.Recherche
import africa.samaos.banco.appli.Rond
import africa.samaos.banco.appli.Segments
import africa.samaos.banco.appli.styleDoux
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.CaseAppli
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.FiletBloc
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.PuceSim
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import africa.samaos.banco.appli.initiales
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.MonthDay

private sealed interface Vue {
    data object Liste : Vue
    data class Fiche(val id: Long) : Vue
    data class Modifier(val id: Long?, val numero: String? = null, val nom: String? = null) : Vue
    data object Import : Vue
    data object MaFiche : Vue
    data object ModifierMaFiche : Vue
}

/** Les Contacts de Sama (maquettes l2-con, refonte du 7 octobre 2026 : contacts-1 à 3). */
class Contacts : ComponentActivity() {
    private var pile by mutableStateOf(listOf<Vue>(Vue.Liste))
    private var reprise by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lire(intent)
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.ContactsNuit else Identites.Contacts
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) {
                val aller: (Vue) -> Unit = { pile = pile + it }
                val retour: () -> Unit = { if (pile.size > 1) pile = pile.dropLast(1) else finish() }
                BackHandler(enabled = pile.size > 1) { retour() }
                // Une page qui s'ouvre arrive de la droite, celle qu'on quitte recule un peu ; au retour, l'inverse.
                AnimatedContent(
                    pile,
                    transitionSpec = {
                        if (targetState.size >= initialState.size) {
                            (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(tween(150))) togetherWith
                                (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(220)))
                        } else {
                            (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(240, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(200, delayMillis = 60)))
                        }.apply { targetContentZIndex = targetState.size.toFloat() }
                    },
                    contentKey = { it.size to it.last() },
                    label = "page",
                ) { p ->
                    Box(Modifier.fillMaxSize().background(LocalIdentite.current.fond)) {
                        when (val v = p.last()) {
                            Vue.Liste -> PageListe(reprise, aller)
                            is Vue.Fiche -> PageFiche(v.id, reprise, aller, retour)
                            is Vue.Modifier -> PageModifier(v.id, v.numero, v.nom, retour) { nouvel ->
                                pile = pile.dropLast(1).let { q -> if (q.lastOrNull() is Vue.Fiche || nouvel == null) q else q + Vue.Fiche(nouvel) }
                            }
                            Vue.Import -> PageImport(retour)
                            Vue.MaFiche -> PageMaFiche(reprise, aller, retour)
                            Vue.ModifierMaFiche -> PageModifierMaFiche(retour)
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

    /** Une fiche, une création (avec le numéro donné par le Téléphone), une modification. */
    private fun lire(i: Intent?) {
        when (i?.action) {
            Intent.ACTION_VIEW -> Carnet.idDepuis(this, i.data)?.let { pile = listOf(Vue.Fiche(it)) }
            Intent.ACTION_INSERT, Intent.ACTION_INSERT_OR_EDIT ->
                pile = listOf(Vue.Modifier(null, i.getStringExtra(ContactsContract.Intents.Insert.PHONE), i.getStringExtra(ContactsContract.Intents.Insert.NAME)))
            Intent.ACTION_EDIT -> Carnet.idDepuis(this, i.data)?.let { pile = listOf(Vue.Modifier(it)) }
        }
    }
}

private val ETOILE = "M12 3l2.7 5.6l6.1.9l-4.4 4.3l1 6.1L12 17l-5.4 2.9l1-6.1l-4.4-4.3l6.1-.9z"
private val QR = "M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h2v2h-2z M18 18h2v2h-2z M18 14h2"
private val APPAREIL_PHOTO = "M4 8h3l2-3h6l2 3h3v11H4z M12 10.5a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
private val IMAGE = "M4 5h16v14H4z M4 16l5-5l4 4l3-3l4 4 M15 9h.01"
private val GATEAU = "M5 12h14v8H5z M5 15c2 1 3-1 5 0s3 1 5 0s3-1 4 0 M12 6v3 M12 4.5h.01"
private val EPINGLE = "M12 21s-6-5.6-6-11a6 6 0 0 1 12 0c0 5.4-6 11-6 11z M12 8.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z"
private val ENVELOPPE = "M4 6h16v12H4z M4 7l8 6l8-6"
private val COEUR = "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"

/** Un groupe de lignes sur le fond : coins doux, ni bord ni ombre. */
@Composable
private fun Bloc(modifier: Modifier = Modifier, contenu: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LocalIdentite.current.surface), content = contenu)
}

/** Un bouton rond avec son nom dessous (Appeler, Message, Partager). */
@Composable
private fun Action(icone: String, texte: String, plein: Boolean = false, actif: Boolean = true, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Rond(icone, texte, plein = plein, taille = 54.dp, actif = actif, onClick = onClick)
        BasicText(texte, maxLines = 1, style = styleDoux(12.5f, if (actif) a.encre2 else a.encre2.copy(alpha = 0.5f), FontWeight.Medium, 1.1f))
    }
}

// ——— Liste (maquette contacts-1) ———

@Composable
private fun PageListe(reprise: Int, aller: (Vue) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }
    var recherche by remember { mutableStateOf("") }
    var groupe by remember { mutableStateOf<String?>(null) }
    var moi by remember { mutableStateOf<MaFiche.Fiche?>(null) }
    LaunchedEffect(reprise) {
        contacts = withContext(Dispatchers.IO) { Carnet.liste(c) }
        moi = withContext(Dispatchers.IO) { MaFiche.lire(c) }
    }
    val groupes = Carnet.GROUPES.filter { g -> contacts.any { it.groupe == g } }
    val vus = contacts
        .filter { groupe == null || it.groupe == groupe }
        .filter { recherche.isBlank() || simplifier(it.nom).contains(simplifier(recherche)) || simplifier(it.lien.orEmpty()).contains(simplifier(recherche)) }
    val parLettre = remember(vus) { vus.groupBy { Carnet.lettre(it.nom) } }
    val etat = rememberLazyListState()
    val portee = rememberCoroutineScope()
    // Les éléments de tête (ma fiche, favoris, groupes), puis une rubrique et un bloc par lettre.
    val tete = if (recherche.isBlank()) 3 else 0
    val positions = remember(parLettre, tete) {
        val m = LinkedHashMap<String, Int>()
        parLettre.keys.forEachIndexed { i, l -> m[l] = tete + i * 2 }
        m
    }
    EcranAppli {
        GrandTitre("Contacts", if (contacts.isEmpty()) " " else "${contacts.size} contact${if (contacts.size > 1) "s" else ""}", sousBarre = true) {
            Rond(Icones.SIM, "Importer depuis la SIM") { aller(Vue.Import) }
            Rond(Icones.PLUS, "Nouveau contact", plein = true, epaisseur = 2.2f) { aller(Vue.Modifier(null)) }
        }
        Recherche(recherche, "Un nom, un lien (Maman, Collègue)", { recherche = it }, Modifier.padding(bottom = 4.dp))
        Box(Modifier.fillMaxSize()) {
            val avecIndex = recherche.isBlank() && positions.size > 3
            LazyColumn(Modifier.fillMaxSize().padding(start = 16.dp, end = if (avecIndex) 24.dp else 16.dp), state = etat) {
                if (recherche.isBlank()) {
                    item(key = "moi") {
                        Bloc(Modifier.padding(top = 14.dp)) {
                            val f = moi
                            Row(
                                Modifier.fillMaxWidth().clickable(onClickLabel = "Ma fiche", role = Role.Button) { aller(if (f == null) Vue.ModifierMaFiche else Vue.MaFiche) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PhotoContact(f?.nom, f?.photo, 48.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    BasicText(f?.nom ?: "Ma fiche", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = a.encre))
                                    BasicText(
                                        if (f == null) "Votre nom et votre numéro, à partager d'un code QR" else "Ma fiche · ${Carnet.formater(f.numero)}",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2),
                                    )
                                }
                                IconeTrait(if (f == null) Icones.PLUS else QR, 22.dp, a.accentTexte)
                            }
                        }
                    }
                    item(key = "favoris") {
                        val favoris = contacts.filter { it.favori }
                        if (favoris.isNotEmpty()) {
                            Column {
                                Rubrique("Favoris")
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    favoris.forEach { f ->
                                        Column(
                                            Modifier.width(66.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = f.nom, role = Role.Button) { aller(Vue.Fiche(f.id)) },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            PhotoContact(f.nom, f.photo, 62.dp)
                                            BasicText(
                                                f.appel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
                                                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = a.encre, textAlign = TextAlign.Center),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "groupes") {
                        if (groupes.isNotEmpty()) {
                            val choix = listOf("Tous") + groupes
                            Segments(choix, choix.indexOf(groupe ?: "Tous").coerceAtLeast(0), Modifier.padding(top = 16.dp), bord = 0.dp) { groupe = if (it == 0) null else choix[it] }
                        }
                    }
                }
                parLettre.forEach { (lettre, l) ->
                    item(key = "l$lettre") { Rubrique(lettre, lettre = true) }
                    item(key = "b$lettre") {
                        Bloc {
                            l.forEachIndexed { i, ct ->
                                if (i > 0) FiletBloc(70.dp)
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(onClickLabel = ct.nom, role = Role.Button) { aller(Vue.Fiche(ct.id)) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PhotoContact(ct.nom, ct.photo, 42.dp)
                                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                        BasicText(ct.nom, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = a.encre))
                                        ct.lien?.let { BasicText(listOfNotNull(ct.groupe?.takeIf { g -> g != "Famille" }, it).joinToString(" · "), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2)) }
                                    }
                                    if (ct.favori) IconeTrait(ETOILE, 17.dp, a.accentTexte, epaisseur = 2f)
                                }
                            }
                        }
                    }
                }
                item(key = "bas") {
                    if (contacts.isEmpty()) {
                        BasicText(
                            "Aucun contact pour l'instant. Ajoutez-en un avec +, ou importez ceux de la SIM.",
                            modifier = Modifier.padding(vertical = 20.dp),
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
                        )
                    }
                    Spacer(Modifier.navigationBarsPadding().height(40.dp))
                }
            }
            if (avecIndex) {
                IndexLettres(positions.keys.toList()) { l -> positions[l]?.let { portee.launch { etat.scrollToItem(it) } } }
            }
        }
    }
}

@Composable
private fun Rubrique(texte: String, lettre: Boolean = false) {
    BasicText(texte, modifier = Modifier.padding(start = 10.dp, top = 20.dp, bottom = 8.dp), style = styleDoux(15f, LocalIdentite.current.encre, FontWeight.SemiBold))
}

/** L'index des lettres, sur le bord droit : on y glisse le doigt pour sauter. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.IndexLettres(lettres: List<String>, aller: (String) -> Unit) {
    val a = LocalIdentite.current
    var hauteur by remember { mutableIntStateOf(1) }
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 2.dp)
            .onSizeChanged { hauteur = it.height }
            .pointerInput(lettres) {
                detectVerticalDragGestures { ch, _ ->
                    val i = (ch.position.y / hauteur * lettres.size).toInt().coerceIn(0, lettres.lastIndex)
                    aller(lettres[i])
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lettres.forEach { l ->
            BasicText(
                l,
                modifier = Modifier.clip(CircleShape).clickable(onClickLabel = "Aller à $l") { aller(l) }.padding(horizontal = 5.dp, vertical = 1.dp),
                style = styleDoux(11f, a.encre2, FontWeight.SemiBold, 1.15f),
            )
        }
    }
}

fun simplifier(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

// ——— Fiche ———

@Composable
private fun PageFiche(id: Long, reprise: Int, aller: (Vue) -> Unit, retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var fiche by remember { mutableStateOf<Fiche?>(null) }
    var supprimer by remember { mutableStateOf(false) }
    LaunchedEffect(id, reprise, version) { fiche = withContext(Dispatchers.IO) { Carnet.fiche(c, id) } }
    val f = fiche ?: return
    val sims = remember { Carnet.operateursDesSim(c) }
    val principal = f.numeros.firstOrNull { it.prefere } ?: f.numeros.firstOrNull()
    fun appeler(n: String) = c.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", n, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun ecrire(n: String) = c.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", n, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    EcranAppli {
        BarreRetour("Contacts", retour, Modifier.padding(top = 0.dp)) {
            Rond(ETOILE, if (f.favori) "Retirer des favoris" else "Ajouter aux favoris", plein = f.favori, epaisseur = 2f) {
                Carnet.favori(c, f.id, !f.favori)
                version++
            }
            Rond(Icones.CRAYON, "Modifier") { aller(Vue.Modifier(f.id)) }
            Rond(Icones.CORBEILLE, "Supprimer") { supprimer = true }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PhotoContact(f.nom, f.photo, 120.dp)
                BasicText(f.nom, modifier = Modifier.padding(top = 14.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, color = a.encre, textAlign = TextAlign.Center))
                BasicText(
                    listOfNotNull(f.lien, Carnet.groupeDe(f.lien)?.takeIf { it != f.lien }).joinToString(" · ").ifBlank {
                        if (f.compte.isNullOrBlank() || f.compte == "Local") "Sur ce téléphone" else f.compte!!
                    },
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2),
                )
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Action(Icones.APPEL, "Appeler", plein = true, actif = principal != null) { principal?.let { appeler(it.numero) } }
                Action(Icones.MESSAGE, "Message", actif = principal != null) { principal?.let { ecrire(it.numero) } }
                Action("M12 4v12 M7 9l5-5l5 5 M5 14v5h14v-5", "Partager") {
                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, f.cle)
                    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Partager ${f.nom}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            if (f.numeros.isNotEmpty()) {
                Bloc {
                    f.numeros.forEachIndexed { i, n ->
                        if (i > 0) FiletBloc(16.dp)
                        val op = Carnet.operateur(n.numero)
                        val sim = sims.firstOrNull { op != null && it.second.contains(op, ignoreCase = true) }
                        Row(
                            Modifier.fillMaxWidth().clickable(onClickLabel = "Appeler", role = Role.Button) { appeler(n.numero) }.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                BasicText(listOfNotNull(Carnet.nomType(n.type), if (n.prefere) "préféré" else null).joinToString(" · "), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = a.encre2))
                                BasicText(Carnet.formater(n.numero), style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre))
                            }
                            if (op != null && op != "Fixe") PuceOperateur(op)
                            if (sim != null) Box(Modifier.padding(start = 6.dp)) { PuceSim(sim.first, sim.second) }
                            Rond(Icones.MESSAGE, "Message", taille = 38.dp) { ecrire(n.numero) }
                        }
                    }
                }
            }
            val infos = listOfNotNull(
                f.anniversaire?.let { Triple(GATEAU, "Anniversaire", anniversaireLisible(it)) },
                f.adresse?.let { Triple(EPINGLE, "Où la trouver", it) },
            ) + f.emails.map { Triple(ENVELOPPE, "E-mail", it) }
            if (infos.isNotEmpty()) {
                Bloc(Modifier.padding(top = 12.dp)) {
                    infos.forEachIndexed { i, (icone, titre, valeur) ->
                        if (i > 0) FiletBloc(52.dp)
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = titre == "E-mail", role = Role.Button) {
                                c.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", valeur, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconeTrait(icone, 20.dp, a.encre2)
                            Column(Modifier.padding(start = 16.dp)) {
                                BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = a.encre2))
                                BasicText(valeur, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(40.dp))
        }
    }
    if (supprimer) {
        DialogueAppli("Supprimer ${f.nom} ?", "Le contact disparaît de ce téléphone, dans cet Espace.", fermer = { supprimer = false }) {
            BoutonTexteAppli("Annuler", style = 't') { supprimer = false }
            BoutonTexteAppli("Supprimer") {
                Carnet.supprimer(c, f.id)
                retour()
            }
        }
    }
}

@Composable
private fun PuceOperateur(op: String) {
    val (fond, encre) = when (op) {
        "Orange" -> Color(0xFFFFF1E2) to Color(0xFFB5651D)
        "MTN" -> Color(0xFFFFF6D6) to Color(0xFF8A6A00)
        else -> Color(0xFFE6F0FA) to Color(0xFF2F5E9E)
    }
    BasicText(
        op, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(fond).padding(horizontal = 9.dp, vertical = 3.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = encre),
    )
}

/** « 14 mars · dans 5 mois », ou « aujourd'hui ». */
private fun anniversaireLisible(d: String): String = try {
    val p = d.removePrefix("--").split("-")
    val (m, j) = if (p.size == 3) p[1].toInt() to p[2].toInt() else p[0].toInt() to p[1].toInt()
    val texte = "$j ${java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)}"
    val auj = LocalDate.now()
    var prochain = MonthDay.of(m, j).atYear(auj.year)
    if (prochain.isBefore(auj)) prochain = prochain.plusYears(1)
    val jours = java.time.temporal.ChronoUnit.DAYS.between(auj, prochain)
    texte + " · " + when {
        jours == 0L -> "aujourd'hui"
        jours == 1L -> "demain"
        jours < 31 -> "dans $jours jours"
        else -> "dans ${(jours / 30.4).toInt()} mois"
    }
} catch (_: Exception) {
    d
}

// ——— Créer ou modifier (maquette contacts-2) ———

@Composable
private fun BarreFormulaire(titre: String, peut: Boolean, annuler: () -> Unit, enregistrer: () -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            "Annuler", modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = annuler).padding(10.dp),
            style = TextStyle(fontFamily = Polices.corps, fontSize = 16.5.sp, color = a.accentTexte),
        )
        BasicText(titre, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = a.encre, textAlign = TextAlign.Center))
        BasicText(
            "Enregistrer", modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = peut, role = Role.Button, onClick = enregistrer).padding(10.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (peut) a.accentTexte else a.encre2.copy(alpha = 0.5f)),
        )
    }
}

/** Une photo en attente d'enregistrement : prise, choisie ou fabriquée (avatar) ; ou à retirer. */
private class PhotoEnAttente(val image: Bitmap?, val retirer: Boolean = false)

@Composable
private fun EnTetePhoto(nom: String, photo: ImageBitmap?, changer: () -> Unit) {
    val a = LocalIdentite.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(122.dp).clickable(onClickLabel = "Photo", role = Role.Button, onClick = changer)) {
            PhotoContact(nom.ifBlank { null }, photo, 118.dp)
            Box(
                Modifier.align(Alignment.BottomEnd).size(40.dp).clip(CircleShape).background(a.fond).padding(3.dp).clip(CircleShape).background(a.accent),
                contentAlignment = Alignment.Center,
            ) { IconeTrait(APPAREIL_PHOTO, 19.dp, a.surAccent) }
        }
        BasicText(
            if (photo == null) "Ajouter une photo" else "Changer la photo",
            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = changer).padding(6.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.accentTexte),
        )
    }
}

@Composable
private fun PageModifier(id: Long?, numeroDonne: String?, nomDonne: String?, retour: () -> Unit, fini: (Long?) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var prenom by remember { mutableStateOf(nomDonne?.substringBefore(' ').orEmpty()) }
    var famille by remember { mutableStateOf(nomDonne?.substringAfter(' ', "").orEmpty()) }
    val numeros = remember { mutableStateListOf(Pair(numeroDonne.orEmpty(), Carnet.TYPES.first())) }
    var email by remember { mutableStateOf("") }
    var lien by remember { mutableStateOf<String?>(null) }
    var adresse by remember { mutableStateOf("") }
    var anniversaire by remember { mutableStateOf<String?>(null) }
    var favori by remember { mutableStateOf(false) }
    var brut by remember { mutableStateOf<Long?>(null) }
    var photoActuelle by remember { mutableStateOf<ImageBitmap?>(null) }
    var attente by remember { mutableStateOf<PhotoEnAttente?>(null) }
    var feuillePhoto by remember { mutableStateOf(false) }
    var feuilleDate by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id != null) {
            withContext(Dispatchers.IO) { Carnet.fiche(c, id) }?.let { f ->
                prenom = f.prenom.ifBlank { f.nom }
                famille = f.nomFamille
                numeros.clear()
                f.numeros.forEach { numeros += it.numero to it.type }
                if (numeros.isEmpty()) numeros += "" to Carnet.TYPES.first()
                email = f.emails.firstOrNull().orEmpty()
                lien = f.lien
                adresse = f.adresse.orEmpty()
                anniversaire = f.anniversaire
                favori = f.favori
                brut = f.brut
                photoActuelle = f.photo
            }
        }
    }
    val nom = "$prenom $famille".trim()
    val apercu: ImageBitmap? = attente?.let { p -> if (p.retirer) null else p.image?.asImageBitmap() } ?: photoActuelle
    val peut = nom.isNotBlank() && numeros.any { it.first.isNotBlank() }
    EcranAppli {
        BarreFormulaire(if (id == null) "Nouveau contact" else "Modifier", peut, retour) {
            val p = attente
            val nouvel = Carnet.enregistrer(
                c, brut, prenom, famille, numeros.toList(), email, lien, anniversaire, adresse, favori,
                photo = p?.image?.let { Avatars.jpeg(it) }, sansPhoto = p?.retirer == true,
            )
            fini(nouvel ?: id)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EnTetePhoto(nom, apercu) { feuillePhoto = true }
            Bloc {
                ChampLigne("Prénom", prenom) { prenom = it }
                FiletBloc()
                ChampLigne("Nom", famille) { famille = it }
                FiletBloc()
                Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
                    BasicText("Lien", modifier = Modifier.padding(start = 16.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = a.encre2))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Carnet.LIENS.forEach { l -> PuceFiltre(l, lien == l) { lien = if (lien == l) null else l } }
                    }
                }
            }
            Bloc {
                numeros.forEachIndexed { i, (n, t) ->
                    if (i > 0) FiletBloc()
                    val op = Carnet.operateur(n)
                    Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            ChampLigne(Carnet.nomType(t), n, KeyboardType.Phone, surNom = {
                                // Toucher le type passe au suivant : Mobile, Maison, Travail, Fixe.
                                numeros[i] = n to Carnet.TYPES[(Carnet.TYPES.indexOf(t) + 1) % Carnet.TYPES.size]
                            }) { numeros[i] = it.filter { ch -> ch.isDigit() || ch in "+ " } to t }
                        }
                        if (op != null && op != "Fixe") PuceOperateur(op)
                    }
                }
                FiletBloc()
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { numeros += "" to Carnet.TYPES.first() }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconeTrait(Icones.PLUS, 18.dp, a.accentTexte)
                    BasicText("Ajouter un numéro", modifier = Modifier.padding(start = 10.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.accentTexte))
                }
            }
            Bloc {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { feuilleDate = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconeTrait(GATEAU, 20.dp, a.encre2)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        BasicText("Anniversaire", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = a.encre2))
                        BasicText(
                            anniversaire?.let { anniversaireLisible(it).substringBefore(" · ") } ?: "Ajouter",
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 16.5.sp, color = if (anniversaire == null) a.encre2.copy(alpha = 0.7f) else a.encre),
                        )
                    }
                    if (anniversaire != null) BasicText("Rappel dans l'Agenda", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = a.accentTexte))
                }
                FiletBloc(50.dp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                    IconeTrait(EPINGLE, 20.dp, a.encre2)
                    Box(Modifier.weight(1f)) { ChampLigne("Où la trouver", adresse, indication = "Quartier, repère…") { adresse = it } }
                }
                FiletBloc(50.dp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                    IconeTrait(ENVELOPPE, 20.dp, a.encre2)
                    Box(Modifier.weight(1f)) { ChampLigne("E-mail", email, KeyboardType.Email, indication = "facultatif") { email = it } }
                }
            }
            Bloc {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Switch) { favori = !favori }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconeTrait(ETOILE, 20.dp, Color(0xFFD9A11E))
                    BasicText("Favori", modifier = Modifier.weight(1f).padding(start = 14.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
                    InterAppli(favori)
                }
                FiletBloc(50.dp)
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        try {
                            c.startActivity(Intent("africa.samaos.action.PROCHES").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                        }
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconeTrait(COEUR, 20.dp, a.encre2)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        BasicText("Reconnaître comme proche", style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
                        BasicText("En personne, d'un code QR : pour s'échanger des applis sans data", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                    }
                    IconeTrait(Icones.CHEVRON, 16.dp, a.encre2)
                }
            }
            BasicText(
                "Le contact reste sur ce téléphone, dans cet Espace.",
                modifier = Modifier.padding(start = 4.dp, bottom = 40.dp).navigationBarsPadding(),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2),
            )
        }
    }
    if (feuillePhoto) {
        FeuillePhoto(
            nom = nom,
            aUnePhoto = apercu != null,
            fermer = { feuillePhoto = false },
            choisie = { attente = PhotoEnAttente(it) },
            retirer = { attente = PhotoEnAttente(null, retirer = true) },
        )
    }
    if (feuilleDate) {
        FeuilleAnniversaire(anniversaire, fermer = { feuilleDate = false }) { anniversaire = it }
    }
}

/** Un champ de formulaire dans un bloc : son nom au-dessus, la valeur en dessous. [surNom] : toucher le nom. */
@Composable
private fun ChampLigne(
    nom: String,
    valeur: String,
    clavier: KeyboardType = KeyboardType.Text,
    indication: String? = null,
    surNom: (() -> Unit)? = null,
    changer: (String) -> Unit,
) {
    val a = LocalIdentite.current
    val focus = remember { FocusRequester() }
    Column(
        Modifier.fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focus.requestFocus() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        BasicText(
            nom + if (surNom != null) " ▾" else "",
            modifier = if (surNom != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Changer le type", role = Role.Button, onClick = surNom) else Modifier,
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (surNom != null) a.accentTexte else a.encre2),
        )
        BasicTextField(
            value = valeur,
            onValueChange = changer,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = clavier),
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.5.sp, color = a.encre),
            cursorBrush = SolidColor(a.accent),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).focusRequester(focus).semantics { contentDescription = nom },
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (valeur.isEmpty() && indication != null) BasicText(indication, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.5.sp, color = a.encre2.copy(alpha = 0.7f)))
                    champ()
                }
            },
        )
    }
}

// ——— La photo, ou un avatar (maquette contacts-3) ———

@Composable
private fun FeuillePhoto(nom: String, aUnePhoto: Boolean, fermer: () -> Unit, choisie: (Bitmap) -> Unit, retirer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var genre by remember { mutableStateOf(Avatars.Genre.INITIALES) }
    var couleur by remember { mutableIntStateOf(4) }
    var emoji by remember { mutableStateOf(Avatars.EMOJIS.first()) }
    var motif by remember { mutableIntStateOf(0) }
    val texte = if (nom.isBlank()) "?" else initiales(nom)
    val apercu = remember(genre, couleur, emoji, motif, texte) { Avatars.rendre(c, genre, Avatars.COULEURS[couleur], texte, emoji, motif, 240) }
    // La photo prise par l'Appareil photo arrive dans un fichier que l'appli lui prête.
    val fichier = remember { File(c.cacheDir, "photos/contact.jpg").also { it.parentFile?.mkdirs() } }
    val uriPhoto = remember { FileProvider.getUriForFile(c, "${c.packageName}.photos", fichier) }
    // La feuille redescend en animation : ses boutons passent par fermerFeuille (donné par la feuille).
    var fermerFeuille by remember { mutableStateOf(fermer) }
    fun depuis(uri: Uri) {
        portee.launch {
            val b = withContext(Dispatchers.IO) { Avatars.depuisPhoto(c, uri) } ?: return@launch
            choisie(b)
            fermerFeuille()
        }
    }
    val prendre = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) depuis(uriPhoto) }
    val choisir = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) depuis(uri) }
    FeuilleAppli(fermer = fermer, titre = if (nom.isBlank()) "La photo du contact" else "La photo de ${nom.substringBefore(' ')}") {
        val f = LocalFermerFeuille.current
        SideEffect { fermerFeuille = f }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                TuileChoix(APPAREIL_PHOTO, "Prendre une photo", Modifier.weight(1f)) { prendre.launch(uriPhoto) }
                TuileChoix(IMAGE, "Choisir dans Photos", Modifier.weight(1f)) { choisir.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            Rubrique("Ou un avatar")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(apercu.asImageBitmap(), contentDescription = "Aperçu de l'avatar", modifier = Modifier.size(96.dp).clip(CircleShape))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Avatars.Genre.entries.forEach { g -> PuceFiltre(g.nom, genre == g) { genre = g } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Avatars.COULEURS.chunked(4).forEachIndexed { r, ligne ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            ligne.forEachIndexed { i, coul ->
                                val k = r * 4 + i
                                Box(
                                    Modifier.size(34.dp).then(if (k == couleur) Modifier.border(2.dp, coul, CircleShape).padding(4.dp) else Modifier)
                                        .clip(CircleShape).background(coul).clickable(onClickLabel = "Couleur ${k + 1}", role = Role.RadioButton) { couleur = k },
                                )
                            }
                        }
                    }
                }
            }
            if (genre == Avatars.Genre.MOTIF) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                    (0 until Avatars.MOTIFS).forEach { m ->
                        val v = remember(couleur, m) { Avatars.rendre(c, Avatars.Genre.MOTIF, Avatars.COULEURS[couleur], "", "", m, 120) }
                        Image(
                            v.asImageBitmap(), contentDescription = "Motif ${m + 1}",
                            modifier = Modifier.size(56.dp).clip(CircleShape).then(if (m == motif) Modifier.border(3.dp, a.accent, CircleShape) else Modifier)
                                .clickable(role = Role.RadioButton) { motif = m },
                        )
                    }
                }
            }
            if (genre == Avatars.Genre.EMOJI) {
                Avatars.EMOJIS.chunked(6).forEach { ligne ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        ligne.forEach { e ->
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(if (e == emoji) a.voile else Color.Transparent)
                                    .clickable(onClickLabel = e, role = Role.RadioButton) { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) { BasicText(e, style = TextStyle(fontSize = 24.sp)) }
                        }
                    }
                }
            }
            BoutonTexteAppli("Utiliser cet avatar", modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                choisie(Avatars.rendre(c, genre, Avatars.COULEURS[couleur], texte, emoji, motif))
                fermerFeuille()
            }
        }
        if (aUnePhoto) {
            Spacer(Modifier.height(6.dp))
            ActionFeuille(Icones.CORBEILLE, "Retirer la photo", danger = true) {
                retirer()
                fermerFeuille()
            }
        }
    }
}

@Composable
private fun TuileChoix(icone: String, texte: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(a.champ)
            .clickable(onClickLabel = texte, role = Role.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(icone, 22.dp, a.accentTexte) }
        BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre))
    }
}

/** Le jour et le mois de l'anniversaire, sans l'année (« --03-14 », comme Android le garde). */
@Composable
private fun FeuilleAnniversaire(actuel: String?, fermer: () -> Unit, choisi: (String?) -> Unit) {
    val p = actuel?.removePrefix("--")?.split("-")
    var mois by remember { mutableIntStateOf(p?.let { if (it.size == 3) it[1].toIntOrNull() else it.getOrNull(0)?.toIntOrNull() } ?: LocalDate.now().monthValue) }
    var jour by remember { mutableIntStateOf(p?.let { if (it.size == 3) it[2].toIntOrNull() else it.getOrNull(1)?.toIntOrNull() } ?: 1) }
    val max = java.time.Month.of(mois).maxLength()
    FeuilleAppli(fermer = fermer, titre = "Anniversaire") {
        val fermerFeuille = LocalFermerFeuille.current
        Column(Modifier.padding(horizontal = 20.dp)) {
            val a = LocalIdentite.current
            // Les douze mois, tous visibles ; puis les jours du mois choisi.
            (1..12).chunked(6).forEach { rangee ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rangee.forEach { m ->
                        val choisi = m == mois
                        BasicText(
                            java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRENCH).removeSuffix("."),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .then(if (choisi) Modifier.background(a.voile) else Modifier.border(1.dp, a.trait, RoundedCornerShape(12.dp)))
                                .clickable(role = Role.RadioButton) {
                                    mois = m
                                    jour = jour.coerceAtMost(java.time.Month.of(m).maxLength())
                                }
                                .padding(vertical = 9.dp),
                            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (choisi) a.accentTexte else a.encre, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            (1..max).chunked(7).forEach { semaine ->
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    (0 until 7).forEach { k ->
                        val j = semaine.getOrNull(k)
                        Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                            if (j != null) {
                                Box(
                                    Modifier.size(42.dp).clip(CircleShape).background(if (j == jour) a.accent else Color.Transparent).clickable(role = Role.RadioButton) { jour = j },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BasicText(j.toString(), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = if (j == jour) a.surAccent else a.encre))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                if (actuel != null) {
                    BoutonTexteAppli("Retirer", style = 't') {
                        choisi(null)
                        fermerFeuille()
                    }
                }
                BoutonTexteAppli("Choisir") {
                    choisi("--%02d-%02d".format(mois, jour))
                    fermerFeuille()
                }
            }
        }
    }
}

// ——— Ma fiche : mon nom, mon numéro, à partager d'un code QR ———

@Composable
private fun PageMaFiche(reprise: Int, aller: (Vue) -> Unit, retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var f by remember { mutableStateOf<MaFiche.Fiche?>(null) }
    LaunchedEffect(reprise) { f = withContext(Dispatchers.IO) { MaFiche.lire(c) } }
    val fiche = f ?: return
    val qr = remember(fiche.vcard()) { MaFiche.qr(fiche.vcard()) }
    EcranAppli {
        BarreRetour("Contacts", retour, Modifier.padding(top = 0.dp), milieu = { BasicText("Ma fiche", style = styleDoux(17f, a.encre, FontWeight.SemiBold)) }) {
            Rond(Icones.CRAYON, "Modifier") { aller(Vue.ModifierMaFiche) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PhotoContact(fiche.nom, fiche.photo, 104.dp)
            BasicText(fiche.nom, modifier = Modifier.padding(top = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = a.encre))
            BasicText(Carnet.formater(fiche.numero), style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre2))
            Bloc(Modifier.padding(top = 20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(qr.asImageBitmap(), contentDescription = "Code QR de ma fiche", modifier = Modifier.size(220.dp))
                    BasicText(
                        "Montrez ce code : l'appareil photo de l'autre ajoute votre fiche à ses contacts.",
                        modifier = Modifier.padding(top = 12.dp),
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2, textAlign = TextAlign.Center),
                    )
                }
            }
            BoutonTexteAppli("Partager ma fiche", style = 's', modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                c.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_TEXT, fiche.vcard()), "Partager ma fiche")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Spacer(Modifier.navigationBarsPadding().height(30.dp))
        }
    }
}

@Composable
private fun PageModifierMaFiche(retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    var prenom by remember { mutableStateOf("") }
    var famille by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var attente by remember { mutableStateOf<PhotoEnAttente?>(null) }
    var feuille by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { MaFiche.lire(c) }?.let {
            prenom = it.prenom
            famille = it.famille
            numero = it.numero
            photo = it.photo
        }
    }
    val apercu = attente?.let { if (it.retirer) null else it.image?.asImageBitmap() } ?: photo
    val nom = "$prenom $famille".trim()
    EcranAppli {
        BarreFormulaire("Ma fiche", nom.isNotBlank() && numero.isNotBlank(), retour) {
            MaFiche.enregistrer(c, prenom, famille, numero, attente?.image, attente?.retirer == true)
            retour()
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EnTetePhoto(nom, apercu) { feuille = true }
            Bloc {
                ChampLigne("Prénom", prenom) { prenom = it }
                FiletBloc()
                ChampLigne("Nom", famille) { famille = it }
                FiletBloc()
                ChampLigne("Mon numéro", numero, KeyboardType.Phone) { numero = it.filter { ch -> ch.isDigit() || ch in "+ " } }
            }
            BasicText(
                "Ma fiche reste sur ce téléphone. Elle ne part que si vous la montrez ou la partagez.",
                modifier = Modifier.padding(start = 4.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2),
            )
        }
    }
    if (feuille) {
        FeuillePhoto(nom, apercu != null, fermer = { feuille = false }, choisie = { attente = PhotoEnAttente(it) }, retirer = { attente = PhotoEnAttente(null, true) })
    }
}

// ——— Importer depuis la SIM (maquette l2-con-import) ———

@Composable
private fun PageImport(retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var liste by remember { mutableStateOf<List<Carnet.ContactSim>?>(null) }
    val choisis = remember { mutableStateListOf<Int>() }
    LaunchedEffect(Unit) {
        val l = withContext(Dispatchers.IO) { Carnet.contactsSim(c) }
        liste = l
        choisis.clear()
        l.forEachIndexed { i, s -> if (!s.existe) choisis += i }
    }
    val l = liste
    EcranAppli {
        BarreRetour("Contacts", retour, Modifier.padding(top = 0.dp))
        GrandTitre("Importer depuis la SIM", sousBarre = true)
        if (l == null) return@EcranAppli
        if (l.isEmpty()) {
            BasicText(
                "Aucun contact sur la SIM. Les téléphones d'aujourd'hui gardent les contacts dans le téléphone : il n'y a rien à importer.",
                modifier = Modifier.padding(20.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
            )
            return@EcranAppli
        }
        val deja = l.count { it.existe }
        if (deja > 0) {
            BasicText(
                "$deja contact${if (deja > 1) "s existent" else " existe"} déjà : ${if (deja > 1) "ils ne seront pas" else "il ne sera pas"} importé${if (deja > 1) "s" else ""} en double. Les noms en capitales passent en casse normale : MAMAN devient Maman.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
            )
        }
        LigneAppli("Tout sélectionner", "${choisis.size} sur ${l.size}", debut = { CaseAppli(choisis.size == l.count { !it.existe }) }) {
            if (choisis.size == l.count { !it.existe }) choisis.clear() else {
                choisis.clear()
                l.forEachIndexed { i, s -> if (!s.existe) choisis += i }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(l.indices.toList()) { i ->
                val s = l[i]
                LigneAppli(
                    s.nom,
                    if (s.existe) "Déjà dans vos contacts" else Carnet.formater(s.numero),
                    debut = { if (s.existe) Spacer(Modifier.size(24.dp)) else CaseAppli(i in choisis) },
                ) { if (!s.existe) { if (i in choisis) choisis.remove(i) else choisis += i } }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.End) {
            BoutonTexteAppli("Importer ${choisis.size}", actif = choisis.isNotEmpty()) {
                choisis.forEach { i ->
                    val s = l[i]
                    Carnet.enregistrer(c, null, s.nom.substringBefore(' '), s.nom.substringAfter(' ', ""), listOf(s.numero to Carnet.TYPES.first()), null)
                }
                retour()
            }
        }
    }
}
