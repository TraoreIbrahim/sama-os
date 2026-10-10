package africa.samaos.notes

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.RoueHeure
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un paragraphe en cours d'écriture. */
private class Para(genre: Genre, coche: Boolean, texte: String, val id: Long = Bloc(Genre.TEXTE, "").id) {
    var genre by mutableStateOf(genre)
    var coche by mutableStateOf(coche)
    var valeur by mutableStateOf(TextFieldValue(texte, TextRange(texte.length)))
    val focus = FocusRequester()
    val texte get() = valeur.text
    fun bloc() = Bloc(genre, texte, coche && genre == Genre.CASE)
}

/** Un code secret écrit dans une note : la mettre sous verrou, et rappeler qu'on ne le donne à personne. */
private val MOTS_SECRETS = Regex("(code\\s*(secret|pin|confidentiel)|\\bpin\\b|mot\\s*de\\s*passe|\\bmdp\\b|password|\\bcode\\b.{0,20}money)", RegexOption.IGNORE_CASE)
private val CHIFFRES = Regex("\\d{4,}")

private fun styleDe(g: Genre, a: africa.samaos.banco.appli.Identite): TextStyle = when (g) {
    Genre.TITRE -> TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp, color = a.encre)
    Genre.INTERTITRE -> TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, color = a.encre)
    else -> TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 25.sp, color = a.encre)
}

/** Une note : verrouillée tant qu'on n'a pas donné le code du téléphone, sinon l'éditeur. */
@Composable
internal fun PageEdition(v: Vue.Edition, retour: () -> Unit, remplacer: (Vue) -> Unit) {
    val n = Carnet.note(v.id)
    if (n != null && n.verrouillee && !Verrou.ouvert) {
        PageVerrouillee(n, retour)
        return
    }
    key(v.id) { Editeur(v, n, retour, remplacer) }
}

@Composable
private fun PageVerrouillee(n: Note, retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> if (r.resultCode == Activity.RESULT_OK) Verrou.ouvert = true }
    BackHandler(onBack = retour)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Barre(Carnet.nomDossier(n.dossier), retour)
        Column(Modifier.fillMaxWidth().weight(1f).padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(Icones.CADENAS, 32.dp, a.accentTexte) }
            BasicText(nomDe(n), modifier = Modifier.padding(top = 20.dp), style = st(21f, a.encre, FontWeight.Bold).copy(textAlign = TextAlign.Center))
            BasicText(
                "Cette note est verrouillée. Elle s'ouvre avec le code du téléphone : quelqu'un qui prend votre téléphone ne peut pas la lire.",
                modifier = Modifier.padding(top = 8.dp),
                style = st(15f, a.encre2, interligne = 1.4f).copy(textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(24.dp))
            BoutonTexteAppli("Ouvrir avec le code", icone = Ic.DEVERROUILLER) {
                val i = Verrou.demande(c)
                if (i != null) demande.launch(i) else Verrou.ouvert = true
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editeur(v: Vue.Edition, existante: Note?, retour: () -> Unit, remplacer: (Vue) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val vibreur = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val depart = remember {
        existante?.let { n -> if (n.verrouillee) n.copy(blocs = Verrou.ouvrir(n.secret!!) ?: emptyList()) else n }
            ?: Note(v.id, v.titre.orEmpty(), listOf(Bloc(if (v.cases) Genre.CASE else Genre.TEXTE, v.texte.orEmpty())), dossier = v.dossier)
    }
    var titre by remember { mutableStateOf(TextFieldValue(depart.titre, TextRange(depart.titre.length))) }
    val paras = remember { mutableStateListOf<Para>().apply { depart.blocs.ifEmpty { listOf(Bloc(Genre.TEXTE, "")) }.forEach { add(Para(it.genre, it.coche, it.texte)) } } }
    var dossier by remember { mutableLongStateOf(depart.dossier) }
    var epinglee by remember { mutableStateOf(depart.epinglee) }
    var rappel by remember { mutableStateOf(depart.rappel) }
    var verrouillee by remember { mutableStateOf(depart.verrouillee) }
    var modifiee by remember { mutableLongStateOf(depart.modifiee) }
    var courant by remember { mutableStateOf<Long?>(null) }
    var aFocaliser by remember { mutableStateOf<Long?>(null) }
    var titreAFocaliser by remember { mutableStateOf(existante == null && !v.cases && v.texte == null) }
    var panneau by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var choixRappel by remember { mutableStateOf(false) }
    var deplacer by remember { mutableStateOf(false) }
    var sansCode by remember { mutableStateOf(false) }
    var conseilEcarte by remember { mutableStateOf(false) }
    val titreFocus = remember { FocusRequester() }
    if (v.cases && existante == null) LaunchedEffect(Unit) { aFocaliser = paras.first().id }

    // Ce qui a été gardé : la date de modification ne change que si le contenu change.
    val empreinte = titre.text + "\u0000" + paras.joinToString("\u0001") { it.genre.name + it.coche + it.texte }
    var gardee by remember { mutableStateOf(empreinte) }
    fun note(): Note {
        val blocs = paras.map { it.bloc() }.dropLastWhile { it.texte.isBlank() && it.genre == Genre.TEXTE }
        val n = Note(depart.id, titre.text.trim(), blocs, dossier, epinglee, rappel, modifiee)
        return if (verrouillee) n.copy(secret = Verrou.fermer(blocs), blocs = emptyList()) else n
    }
    fun garder() {
        if (empreinte != gardee) {
            modifiee = System.currentTimeMillis()
            gardee = empreinte
        }
        Carnet.garder(c, note())
    }
    // Gardée en écrivant (un instant après la dernière touche), et en partant.
    LaunchedEffect(empreinte, dossier, epinglee, rappel, verrouillee) {
        if (empreinte == gardee && existante != null && dossier == depart.dossier && epinglee == depart.epinglee && rappel == depart.rappel && verrouillee == depart.verrouillee) return@LaunchedEffect
        kotlinx.coroutines.delay(700)
        garder()
    }
    val dernier by androidx.compose.runtime.rememberUpdatedState { garder() }
    DisposableEffect(Unit) { onDispose { dernier() } }
    val partir = {
        garder()
        retour()
    }
    BackHandler(onBack = partir)
    BackHandler(enabled = panneau) { panneau = false }

    // Le paragraphe qui doit prendre le clavier (un nouveau paragraphe, celui où l'on revient en effaçant).
    LaunchedEffect(aFocaliser) {
        val id = aFocaliser ?: return@LaunchedEffect
        withFrameNanos { }
        try {
            paras.firstOrNull { it.id == id }?.focus?.requestFocus()
        } catch (_: Exception) {
        }
        aFocaliser = null
    }
    LaunchedEffect(titreAFocaliser) {
        if (!titreAFocaliser) return@LaunchedEffect
        withFrameNanos { }
        try {
            titreFocus.requestFocus()
        } catch (_: Exception) {
        }
        titreAFocaliser = false
    }

    fun focaliser(p: Para, position: Int) {
        p.valeur = p.valeur.copy(selection = TextRange(position.coerceIn(0, p.texte.length)))
        aFocaliser = p.id
    }

    /** Une touche Entrée dans une liste ou un titre : un nouveau paragraphe (une ligne vide de liste termine la liste). */
    fun changer(p: Para, nv: TextFieldValue) {
        if (p.genre == Genre.TEXTE || !nv.text.contains('\n')) {
            p.valeur = nv
            return
        }
        val i = paras.indexOf(p)
        val avant = nv.text.substringBefore('\n')
        val apres = nv.text.substringAfter('\n')
        if (p.genre.liste && avant.isBlank() && apres.isBlank()) {
            p.genre = Genre.TEXTE
            p.coche = false
            p.valeur = TextFieldValue("")
            return
        }
        p.valeur = TextFieldValue(avant, TextRange(avant.length))
        val genre = if (p.genre.liste) p.genre else Genre.TEXTE
        val nouveaux = apres.split('\n').map { Para(genre, false, it) }
        paras.addAll(i + 1, nouveaux)
        focaliser(nouveaux.first(), 0)
    }

    /** Effacer au tout début d'un paragraphe : la puce s'en va ; sinon il rejoint celui d'avant. */
    fun effacerDebut(p: Para) {
        val i = paras.indexOf(p)
        when {
            p.genre != Genre.TEXTE -> {
                p.genre = Genre.TEXTE
                p.coche = false
            }
            i > 0 -> {
                val q = paras[i - 1]
                val pos = q.texte.length
                q.valeur = TextFieldValue(q.texte + p.texte, TextRange(pos))
                paras.removeAt(i)
                aFocaliser = q.id
            }
            p.texte.isEmpty() -> {
                titre = titre.copy(selection = TextRange(titre.text.length))
                titreAFocaliser = true
            }
        }
    }

    /** Donner un genre au paragraphe où l'on écrit ; le même genre de liste une seconde fois revient au texte. */
    fun appliquer(g: Genre) {
        val p = paras.firstOrNull { it.id == courant } ?: paras.last()
        val i = paras.indexOf(p)
        val cible = if (g.liste && p.genre == g) Genre.TEXTE else g
        if (cible.liste && p.genre == Genre.TEXTE && p.texte.contains('\n')) {
            // Un texte de plusieurs lignes devient une ligne par élément.
            val lignes = p.texte.split('\n').filter { it.isNotBlank() }.ifEmpty { listOf("") }
            p.valeur = TextFieldValue(lignes.first(), TextRange(lignes.first().length))
            p.genre = cible
            paras.addAll(i + 1, lignes.drop(1).map { Para(cible, false, it) })
        } else {
            p.genre = cible
            if (cible != Genre.CASE) p.coche = false
        }
        vibreur.performHapticFeedback(HapticFeedbackType.SegmentTick)
        focaliser(p, p.valeur.selection.start)
    }

    val texteEntier = titre.text + "\n" + paras.joinToString("\n") { it.texte }
    val secret = !verrouillee && !conseilEcarte && MOTS_SECRETS.containsMatchIn(texteEntier) && CHIFFRES.containsMatchIn(texteEntier)
    val clavier = WindowInsets.isImeVisible
    val verrou = {
        if (!Verrou.protege(c)) sansCode = true else {
            verrouillee = true
            Verrou.ouvert = true
            garder()
            Avis.dire("Note verrouillée : elle s'ouvrira avec le code du téléphone")
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Barre(Carnet.nomDossier(dossier), partir) {
            AnimatedVisibility(clavier, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
                BasicText(
                    "OK",
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Fermer le clavier", role = Role.Button) { focusManager.clearFocus() }.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = st(17f, a.accentTexte, FontWeight.SemiBold),
                )
            }
            if (!verrouillee) Rond(Icones.PARTAGER, "Partager") { partager(c, note().copy(blocs = paras.map { it.bloc() })) }
            Spacer(Modifier.width(6.dp))
            Rond(Ic.TROIS_POINTS, "Options de la note", epaisseur = 2.8f) {
                focusManager.clearFocus()
                menu = true
            }
        }
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
                BasicText(Dates.longue(modifiee), modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp), style = st(13f, a.encre2).copy(textAlign = TextAlign.Center))
                // Un code secret dans la note : un mot doux, et le verrou à portée.
                AnimatedVisibility(secret, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(a.voile).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
                        IconeTrait(Icones.BOUCLIER, 22.dp, a.accentTexte)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            BasicText(
                                "Un code secret dans cette note ? Verrouillez-la : il faudra le code du téléphone pour la lire. Et ne donnez jamais un code secret, même à quelqu'un qui se dit agent.",
                                style = st(14f, a.encre, interligne = 1.4f),
                            )
                            BasicText(
                                "Verrouiller la note",
                                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { verrou() }.padding(vertical = 4.dp),
                                style = st(15f, a.accentTexte, FontWeight.SemiBold),
                            )
                        }
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "Masquer ce conseil", role = Role.Button) { conseilEcarte = true }, contentAlignment = Alignment.Center) {
                            IconeTrait(Icones.FERMER, 16.dp, a.encre2)
                        }
                    }
                }
                AnimatedVisibility(rappel != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    val r = rappel ?: 0L
                    val passe = r < System.currentTimeMillis()
                    Row(
                        Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(if (passe) a.champ else a.voile).clickable(onClickLabel = "Changer le rappel", role = Role.Button) {
                            focusManager.clearFocus()
                            choixRappel = true
                        }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeTrait(Icones.CLOCHE, 16.dp, if (passe) a.encre2 else a.accentTexte)
                        BasicText(Dates.rappel(r), modifier = Modifier.padding(start = 7.dp), style = st(14f, if (passe) a.encre2 else a.accentTexte, FontWeight.SemiBold))
                    }
                }
                // Le titre de la note.
                BasicTextField(
                    titre,
                    { nv ->
                        if (nv.text.contains('\n')) {
                            // Entrée dans le titre : on passe au premier paragraphe.
                            val apres = nv.text.substringAfter('\n')
                            titre = TextFieldValue(nv.text.substringBefore('\n'))
                            val premier = paras.firstOrNull()
                            if (premier != null && premier.texte.isEmpty() && premier.genre == Genre.TEXTE) {
                                premier.valeur = TextFieldValue(apres)
                                focaliser(premier, 0)
                            } else {
                                val p = Para(Genre.TEXTE, false, apres)
                                paras.add(0, p)
                                focaliser(p, 0)
                            }
                        } else titre = nv
                    },
                    textStyle = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.015).em, color = a.encre),
                    cursorBrush = SolidColor(a.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(titreFocus).onFocusChanged { if (it.isFocused) courant = null }.semantics { contentDescription = "Titre" },
                    decorationBox = { champ -> Box { if (titre.text.isEmpty()) BasicText("Titre", style = st(28f, a.encre2.copy(alpha = 0.55f), FontWeight.Bold, 1.2f)); champ() } },
                )
                Spacer(Modifier.height(10.dp))
                var numero = 0
                paras.forEachIndexed { i, p ->
                    if (p.genre == Genre.NUMERO) numero++ else numero = 0
                    val n = numero
                    key(p.id) {
                        LignePara(
                            p, n, indication = if (paras.size == 1 && p.texte.isEmpty()) (if (p.genre == Genre.CASE) "Un élément" else "Écrire…") else null,
                            espaceAvant = i > 0 && (p.genre == Genre.TITRE || p.genre == Genre.INTERTITRE),
                            changer = { changer(p, it) }, effacerDebut = { effacerDebut(p) }, focalise = { courant = p.id },
                            cocher = {
                                p.coche = !p.coche
                                vibreur.performHapticFeedback(if (p.coche) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            },
                        )
                    }
                }
                // Toucher sous le texte : on continue à la fin.
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 240.dp).clickable(interactionSource = null, indication = null, onClickLabel = "Écrire à la fin") {
                        val p = paras.last()
                        focaliser(p, p.texte.length)
                    },
                )
            }
        }
        // Les genres de paragraphe, au-dessus de la barre d'outils.
        AnimatedVisibility(panneau, enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it / 2 } + fadeIn(), exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(120))) {
            val g = (paras.firstOrNull { it.id == courant } ?: paras.last()).genre
            Panneau(g) { appliquer(it) }
        }
        Outils(
            panneau = panneau, cases = (paras.firstOrNull { it.id == courant } ?: paras.last()).genre == Genre.CASE, rappel = rappel != null,
            styles = { panneau = !panneau }, liste = { appliquer(Genre.CASE) },
            cloche = {
                focusManager.clearFocus()
                choixRappel = true
            },
            nouvelle = {
                garder()
                remplacer(Vue.Edition(System.currentTimeMillis(), dossier = dossier))
            },
        )
    }

    if (menu) {
        FeuilleAppli(fermer = { menu = false }) {
            val f = LocalFermerFeuille.current
            ActionFeuille(if (epinglee) Ic.SANS_EPINGLE else Ic.EPINGLE, if (epinglee) "Désépingler" else "Épingler") {
                epinglee = !epinglee
                f()
            }
            ActionFeuille(Icones.CLOCHE, if (rappel == null) "Me le rappeler" else "Changer le rappel", second = rappel?.let { Dates.rappel(it) }) {
                choixRappel = true
                f()
            }
            ActionFeuille(Ic.DEPLACER, "Déplacer", second = "Dans « ${Carnet.nomDossier(dossier)} »") {
                deplacer = true
                f()
            }
            if (verrouillee) ActionFeuille(Ic.DEVERROUILLER, "Retirer le verrou") {
                verrouillee = false
                garder()
                f()
            } else ActionFeuille(Icones.CADENAS, "Verrouiller", second = "Il faudra le code du téléphone pour la lire") {
                f()
                verrou()
            }
            if (paras.any { it.genre == Genre.CASE }) {
                ActionFeuille(Icones.COCHE, "Mettre les éléments cochés en bas") {
                    val cases = paras.filter { it.genre == Genre.CASE }
                    val ordre = cases.filter { !it.coche } + cases.filter { it.coche }
                    // Chaque élément reprend la place d'une case, les autres paragraphes ne bougent pas.
                    val places = paras.indices.filter { paras[it].genre == Genre.CASE }
                    places.forEachIndexed { k, idx -> paras[idx] = ordre[k] }
                    f()
                }
                if (paras.any { it.coche }) ActionFeuille(Icones.RESTAURER, "Tout décocher") {
                    paras.forEach { it.coche = false }
                    f()
                }
            }
            ActionFeuille(Icones.CORBEILLE, "Supprimer", danger = true) {
                f()
                Carnet.garder(c, note())
                Carnet.note(depart.id)?.let { jeterAvecAnnuler(c, it) }
                gardee = empreinte
                retour()
            }
        }
    }
    if (choixRappel) FeuilleRappel(rappel, { choixRappel = false }) { rappel = it }
    if (deplacer) FeuilleDossiers(dossier, nomDe(note().copy(blocs = paras.map { it.bloc() })), { deplacer = false }) { dossier = it }
    if (sansCode) DialogueAppli(
        "Le téléphone n'a pas de code",
        "Pour verrouiller une note, mettez d'abord un code au téléphone (Réglages › Sécurité). Sans code, n'importe qui pourrait l'ouvrir.",
        { sansCode = false },
    ) {
        val f = LocalFermerFeuille.current
        BoutonTexteAppli("Compris") { f() }
    }
}

/** Un paragraphe : sa puce (rond à cocher, point, numéro) et son texte. */
@Composable
private fun LignePara(p: Para, numero: Int, indication: String?, espaceAvant: Boolean, changer: (TextFieldValue) -> Unit, effacerDebut: () -> Unit, focalise: () -> Unit, cocher: () -> Unit) {
    val a = LocalIdentite.current
    val style = styleDe(p.genre, a)
    val encre by animateColorAsState(if (p.coche && p.genre == Genre.CASE) a.encre2 else a.encre, tween(260), label = "encre")
    Row(Modifier.fillMaxWidth().padding(top = if (espaceAvant) 12.dp else 2.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
        when (p.genre) {
            Genre.CASE -> Box(Modifier.padding(end = 10.dp, top = 0.dp).size(26.dp), contentAlignment = Alignment.Center) { RondCase(p.coche, p.texte, cocher) }
            Genre.PUCE -> Box(Modifier.width(26.dp).padding(end = 6.dp), contentAlignment = Alignment.TopCenter) { BasicText("•", style = style.copy(color = a.accentTexte, fontWeight = FontWeight.Bold)) }
            Genre.NUMERO -> Box(Modifier.width(30.dp).padding(end = 6.dp), contentAlignment = Alignment.TopEnd) { BasicText("$numero.", style = style.copy(color = a.accentTexte, fontFeatureSettings = "tnum")) }
            else -> {}
        }
        BasicTextField(
            p.valeur, changer,
            textStyle = style.copy(color = encre, textDecoration = if (p.coche && p.genre == Genre.CASE) TextDecoration.LineThrough else null),
            cursorBrush = SolidColor(a.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f).focusRequester(p.focus).onFocusChanged { if (it.isFocused) focalise() }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace && p.valeur.selection.collapsed && p.valeur.selection.start == 0) {
                        effacerDebut()
                        true
                    } else false
                }
                .semantics {
                    contentDescription = when (p.genre) {
                        Genre.CASE -> "Élément à cocher"
                        Genre.PUCE -> "Élément de liste"
                        Genre.NUMERO -> "Élément $numero"
                        Genre.TITRE -> "Titre de partie"
                        Genre.INTERTITRE -> "Intertitre"
                        Genre.TEXTE -> "Texte"
                    }
                },
            decorationBox = { champ -> Box { if (p.texte.isEmpty() && indication != null) BasicText(indication, style = style.copy(color = a.encre2.copy(alpha = 0.6f))); champ() } },
        )
    }
}

/** Le rond à cocher : il se remplit de miel, la coche arrive d'un ressort. */
@Composable
private fun RondCase(coche: Boolean, texte: String, cocher: () -> Unit) {
    val a = LocalIdentite.current
    val source = remember { MutableInteractionSource() }
    val presse by source.collectIsPressedAsState()
    val fond by animateColorAsState(if (coche) a.accent else a.accent.copy(alpha = 0f), tween(200), label = "fond")
    val bord by animateColorAsState(if (coche) a.accent else a.encre2.copy(alpha = 0.6f), tween(200), label = "bord")
    val coche1 by animateFloatAsState(if (coche) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "coche")
    val s by animateFloatAsState(if (presse) 0.85f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "appui")
    Box(
        Modifier.size(24.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(fond).border(1.7.dp, bord, CircleShape)
            .clickable(interactionSource = source, indication = null, onClickLabel = if (coche) "Décocher" else "Cocher", role = Role.Checkbox, onClick = cocher)
            .semantics { stateDescription = if (coche) "Coché" else "Pas coché"; contentDescription = texte.ifBlank { "Élément" } },
        contentAlignment = Alignment.Center,
    ) {
        if (coche1 > 0.01f) IconeTrait(Icones.COCHE, 15.dp, Color.White, Modifier.graphicsLayer { scaleX = coche1; scaleY = coche1 }, epaisseur = 2.6f)
    }
}

/** Le panneau « Aa » : les genres de paragraphe, chacun écrit dans son propre style. */
@Composable
private fun Panneau(actuel: Genre, choisir: (Genre) -> Unit) {
    val a = LocalIdentite.current
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(a.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choix("Titre", actuel == Genre.TITRE, Modifier.weight(1f), st(19f, a.encre, FontWeight.Bold, 1.1f)) { choisir(Genre.TITRE) }
            Choix("Intertitre", actuel == Genre.INTERTITRE, Modifier.weight(1f), st(16f, a.encre, FontWeight.SemiBold, 1.1f)) { choisir(Genre.INTERTITRE) }
            Choix("Texte", actuel == Genre.TEXTE, Modifier.weight(1f), st(15f, a.encre, interligne = 1.1f)) { choisir(Genre.TEXTE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choix("•  Puces", actuel == Genre.PUCE, Modifier.weight(1f), st(15f, a.encre, interligne = 1.1f)) { choisir(Genre.PUCE) }
            Choix("1.  Numéros", actuel == Genre.NUMERO, Modifier.weight(1f), st(15f, a.encre, interligne = 1.1f)) { choisir(Genre.NUMERO) }
            Choix("◯  Cases", actuel == Genre.CASE, Modifier.weight(1f), st(15f, a.encre, interligne = 1.1f)) { choisir(Genre.CASE) }
        }
    }
}

@Composable
private fun Choix(nom: String, choisi: Boolean, modifier: Modifier, style: TextStyle, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val fond by animateColorAsState(if (choisi) a.voile else a.champ, label = "fond")
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(fond).clickable(onClickLabel = nom, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { BasicText(nom, maxLines = 1, style = style.copy(color = if (choisi) a.accentTexte else a.encre)) }
}

/** La barre d'outils, posée au-dessus du clavier : le style, la liste à cocher, le rappel, une nouvelle note. */
@Composable
private fun Outils(panneau: Boolean, cases: Boolean, rappel: Boolean, styles: () -> Unit, liste: () -> Unit, cloche: () -> Unit, nouvelle: () -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(26.dp)).background(a.surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Outil(null, "Aa", "Style du paragraphe", panneau, styles)
            Outil(Ic.CASES, null, "Liste à cocher", cases, liste)
            Outil(Icones.CLOCHE, null, "Rappel", rappel, cloche)
        }
        Spacer(Modifier.weight(1f))
        Rond(Ic.NOUVELLE, "Nouvelle note", taille = 48.dp, epaisseur = 2f, onClick = nouvelle)
    }
}

@Composable
private fun Outil(icone: String?, texte: String?, nom: String, actif: Boolean, onClick: () -> Unit) {
    val a = LocalIdentite.current
    val fond by animateColorAsState(if (actif) a.voile else Color.Transparent, label = "fond")
    val encre = if (actif) a.accentTexte else a.encre
    Box(
        Modifier.size(46.dp, 42.dp).clip(RoundedCornerShape(21.dp)).background(fond).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom },
        contentAlignment = Alignment.Center,
    ) {
        if (icone != null) IconeTrait(icone, 21.dp, encre, epaisseur = 1.9f)
        if (texte != null) BasicText(texte, style = st(17f, encre, FontWeight.SemiBold, 1f))
    }
}

/** Ranger la note dans un dossier (ou en créer un). */
@Composable
internal fun FeuilleDossiers(actuel: Long, nom: String, fermer: () -> Unit, choisir: (Long) -> Unit) {
    val c = LocalContext.current
    var nouveau by remember { mutableStateOf(false) }
    FeuilleAppli(fermer = fermer, titre = "Déplacer « $nom »") {
        val f = LocalFermerFeuille.current
        (listOf(Dossier(Carnet.NOTES, "Notes", 0)) + Carnet.dossiers).forEach { d ->
            ActionFeuille(if (d.id == actuel) Icones.COCHE else Icones.DOSSIER, d.nom) {
                if (d.id != actuel) {
                    choisir(d.id)
                    Avis.dire("Déplacée dans « ${d.nom} »")
                }
                f()
            }
        }
        ActionFeuille(Ic.NOUVEAU_DOSSIER, "Nouveau dossier") { nouveau = true }
    }
    if (nouveau) DialogueDossier(null, { nouveau = false }) { n, t ->
        val d = Carnet.creerDossier(c, n, t)
        choisir(d.id)
        Avis.dire("Déplacée dans « ${d.nom} »")
        fermer()
    }
}

/** Me le rappeler : des choix tout faits, ou le jour et l'heure à la molette. */
@Composable
private fun FeuilleRappel(actuel: Long?, fermer: () -> Unit, choisir: (Long?) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun poser(t: LocalDateTime) {
        // Les rappels arrivent en notification : on demande la permission une fois.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        choisir(t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    var precis by remember { mutableStateOf(false) }
    val maintenant = LocalDateTime.now()
    var jour by remember { mutableStateOf(LocalDate.now().let { if (maintenant.hour >= 20) it.plusDays(1) else it }) }
    var heure by remember { mutableIntStateOf(if (maintenant.hour >= 20) 8 else (maintenant.hour + 1).coerceAtMost(23)) }
    var minute by remember { mutableIntStateOf(0) }
    FeuilleAppli(fermer = fermer, titre = "Me le rappeler") {
        val f = LocalFermerFeuille.current
        val choix = listOf(
            Triple("Dans 1 heure", maintenant.plusHours(1).withSecond(0).withNano(0), Icones.HORLOGE),
            Triple("Ce soir", maintenant.withHour(19).withMinute(0).withSecond(0).withNano(0), Icones.NUIT),
            Triple("Demain matin", maintenant.plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0), Icones.SOLEIL),
            Triple("Samedi", maintenant.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY)).withHour(9).withMinute(0).withSecond(0).withNano(0), Icones.SOLEIL),
        ).filter { it.second.isAfter(maintenant) }
        AnimatedVisibility(!precis, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                choix.forEach { (nom, t, icone) ->
                    ActionFeuille(icone, nom, second = Dates.rappel(t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())) {
                        poser(t)
                        f()
                    }
                }
                ActionFeuille(Icones.HORLOGE, "Choisir le jour et l'heure") { precis = true }
                if (actuel != null) ActionFeuille(Icones.FERMER, "Retirer le rappel", danger = true) {
                    choisir(null)
                    f()
                }
            }
        }
        AnimatedVisibility(precis, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(bottom = 6.dp)) {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(21) { k ->
                        val d = LocalDate.now().plusDays(k.toLong())
                        val ici = d == jour
                        val fond by animateColorAsState(if (ici) a.accent else a.champ, label = "jour")
                        Column(
                            Modifier.width(56.dp).clip(RoundedCornerShape(18.dp)).background(fond).clickable(onClickLabel = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)), role = Role.RadioButton) { jour = d }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BasicText(
                                when (k) { 0 -> "Auj."; 1 -> "Dem."; else -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)).replaceFirstChar { it.uppercase() } },
                                style = st(12.5f, if (ici) a.surAccent else a.encre2, FontWeight.Medium),
                            )
                            BasicText("${d.dayOfMonth}", style = st(19f, if (ici) a.surAccent else a.encre, FontWeight.Bold, 1.2f))
                        }
                    }
                }
                RoueHeure(heure, minute, Modifier.padding(top = 10.dp), pas = 5) { h, m ->
                    heure = h
                    minute = m
                }
                val t = jour.atTime(heure, minute)
                val avenir = t.isAfter(LocalDateTime.now())
                androidx.compose.animation.AnimatedContent(
                    if (avenir) Dates.rappel(t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) else "Cette heure est passée",
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                    label = "quand",
                ) { q -> BasicText(q, modifier = Modifier.fillMaxWidth(), style = st(15f, if (avenir) a.accentTexte else a.encre2, FontWeight.SemiBold).copy(textAlign = TextAlign.Center)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    BoutonTexteAppli("Retour", style = 't') { precis = false }
                    BoutonTexteAppli("Me le rappeler", actif = avenir) {
                        poser(t)
                        f()
                    }
                }
            }
        }
    }
}
