package africa.samaos.accueil

import africa.samaos.banco.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Le Nouvel Espace (maquette l5) : un nom, une couleur, un paysage. Le code se choisit dans l'Espace créé. */
@Composable
fun EcranNouvelEspace(espaces: Espaces, applis: List<Appli>) {
    val scope = rememberCoroutineScope()
    val contexte = LocalContext.current
    val essentielles = remember { Profils.essentielles(contexte) }
    val proposees = remember(applis) { Profils.aChoisir(contexte, applis) }
    var applisChoisies by remember(proposees) { mutableStateOf(applisSuggerees(proposees, essentielles)) }
    var choixApplis by remember { mutableStateOf(false) }
    var verrou by remember { mutableStateOf(VerrouEspace.CODE) }
    var choixVerrou by remember { mutableStateOf(false) }
    var nom by remember { mutableStateOf("") }
    // Par défaut, une couleur qu'aucun Espace ne porte encore.
    var teinte by remember {
        mutableStateOf(CouleurEspace.entries.firstOrNull { c -> espaces.liste.none { it.teinte == c } } ?: CouleurEspace.LATERITE)
    }
    var paysage by remember { mutableStateOf(PaysageEspace.LAGUNE) }
    var enCours by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler { if (!enCours) espaces.fermerCreation() }

    fun creer() {
        val probleme = espaces.probleme(nom)
        if (probleme != null) {
            message = probleme
            return
        }
        enCours = true
        message = null
        scope.launch {
            message = espaces.creer(nom, teinte, paysage, verrou, applisChoisies + essentielles, proposees.map { it.paquet }.toSet())
            enCours = false
        }
    }

    // L'écran prend en direct les couleurs du paysage choisi, à l'heure qu'il est.
    AvecBanco(palette(paysage, LocalNuit.current)) {
        val b = LocalBanco.current
        Box(
            Modifier
                .fillMaxSize()
                .background(b.ciel)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Paysage(astre = Astre(322f, 54f, 26f), collines = listOf(176f))
            Column(Modifier.statusBarsPadding().padding(start = 12.dp, top = 4.dp)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Revenir", role = Role.Button) { if (!enCours) espaces.fermerCreation() }
                        .semantics { contentDescription = "Revenir" },
                    contentAlignment = Alignment.Center,
                ) { IconeTrait(Icones.RETOUR, 24.dp, b.encre) }
                BasicText(
                    "Nouvel Espace",
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                    style = TextStyle(
                        fontFamily = Polices.monument,
                        fontWeight = FontWeight(600),
                        fontSize = 44.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.02).em,
                        color = b.encre,
                    ),
                )
            }
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 128.dp)) {
                Crete()
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(b.sol)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                ) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        ChampNom(nom) {
                            nom = it
                            message = null
                        }
                        Column {
                            Rubrique("Couleur")
                            ChoixCouleur(teinte) { teinte = it }
                        }
                        Column {
                            Rubrique("Paysage")
                            ChoixPaysage(paysage) { paysage = it }
                        }
                        Column {
                            Rangee(
                                titre = "Verrouiller avec",
                                valeur = verrou.nom,
                                icone = Icones.CADENAS,
                                surToucher = { choixVerrou = true },
                            )
                            Rangee(
                                titre = "Applis de départ",
                                detail = resumeApplis(proposees, applisChoisies + essentielles),
                                icone = Icones.APPLI,
                                surToucher = { choixApplis = true },
                            )
                        }
                        BasicText(
                            "Un Espace est un profil à part du téléphone : ses applis, ses comptes, ses fichiers et ses notifications. " +
                                "Vous choisirez ${verrou.quoi} juste après, et pourrez changer ses applis dans ses réglages.",
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
                        )
                        message?.let {
                            BasicText(it, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.lateriteTexte))
                        }
                    }
                    Actions(
                        retour = "Annuler",
                        couleurRetour = b.encre2,
                        surRetour = { if (!enCours) espaces.fermerCreation() },
                        action = if (enCours) "Création…" else "Créer",
                        actif = !enCours && nom.isNotBlank(),
                        surAction = ::creer,
                    )
                }
            }
        }
        if (choixVerrou) {
            EcranChoixVerrou(
                sousTitre = nom.trim().ifEmpty { "Nouvel Espace" },
                choisi = verrou,
                choisir = { verrou = it },
                retour = { choixVerrou = false },
            )
        }
        if (choixApplis) {
            EcranChoixApplis(
                titre = "Applis de départ",
                sousTitre = nom.trim().ifEmpty { "Nouvel Espace" },
                applis = proposees,
                choisies = applisChoisies,
                essentielles = essentielles,
                changer = { applisChoisies = it },
                retour = { choixApplis = false },
                action = "Valider",
                valider = { choixApplis = false },
            )
        }
    }
}






/**
 * Le passage d'un Espace à l'autre, en Banco : le paysage de l'Espace où l'on va, son nom, le soleil qui monte.
 * Puis le téléphone change de profil ; si l'Espace a un code, Android le demande.
 */
@Composable
fun EcranPassage(espaces: Espaces, espace: Espace) {
    val nuit = LocalNuit.current
    var montee by remember(espace) { mutableFloatStateOf(0f) }
    LaunchedEffect(espace) {
        animate(0f, 1f, animationSpec = tween(460, easing = FastOutSlowInEasing)) { v, _ -> montee = v }
        espaces.basculer(espace)
    }
    AvecBanco(palette(espace.paysage, nuit)) {
        val b = LocalBanco.current
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (montee * 2.5f).coerceAtMost(1f) }
                .background(b.ciel)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            PaysageMobile(astre = Astre(286f, 430f + 50f * (1f - montee), 70f), progres = { 0f })
            Column(Modifier.statusBarsPadding().padding(start = 24.dp, top = 52.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(espace.couleur(b), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    BasicText(
                        espace.nom,
                        style = TextStyle(
                            fontFamily = Polices.monument,
                            fontWeight = FontWeight(600),
                            fontSize = 44.sp,
                            lineHeight = 48.sp,
                            letterSpacing = (-0.02).em,
                            color = b.encre,
                        ),
                    )
                }
                BasicText(
                    "Ouverture de l'Espace",
                    modifier = Modifier.padding(top = 4.dp, start = 24.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = b.encre2),
                )
            }
        }
    }
}

/** Dans un Espace sans code : on propose d'en choisir un, avec l'écran d'Android qui le gardera. */
@Composable
fun EcranProteger(espaces: Espaces) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val espace = espaces.actif
    EcranEspace(espace, "Protéger l'Espace") {
        Paragraphe(
            "Choisissez ${espace.verrou.quoi} de l'Espace ${espace.nom}. Le téléphone le demandera pour y entrer, " +
                "et ses fichiers seront chiffrés avec lui.",
        )
        if (espace.verrou != VerrouEspace.SCHEMA) Paragraphe("Le téléphone refuse les codes trop simples, comme 1234.")
        Paragraphe(
            "Sama ne vous demandera jamais ce code, ni par appel ni par SMS. Ne le donnez à personne.",
            couleur = b.lateriteTexte,
        )
        Spacer(Modifier.weight(1f))
        Actions(retour = "Plus tard", surRetour = { espaces.plusTard() }, action = "Choisir", actif = true) {
            choisirLeCode(contexte, espace.verrou)
        }
    }
}

/** Le gabarit des écrans d'Espace : le nom dans le ciel, le texte et les actions sur le sol. */
@Composable
private fun EcranEspace(espace: Espace, sousTitre: String, contenu: @Composable ColumnScope.() -> Unit) {
    val b = LocalBanco.current
    Box(
        Modifier
            .fillMaxSize()
            .background(b.ciel)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Paysage(astre = Astre(322f, 54f, 26f), collines = listOf(176f))
        Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 44.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(espace.couleur(b), CircleShape))
                Spacer(Modifier.width(12.dp))
                BasicText(
                    espace.nom,
                    style = TextStyle(
                        fontFamily = Polices.monument,
                        fontWeight = FontWeight(600),
                        fontSize = 44.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.02).em,
                        color = b.encre,
                    ),
                )
            }
            BasicText(
                sousTitre,
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 150.dp)) {
            Crete()
            Column(
                Modifier
                    .fillMaxSize()
                    .background(b.sol)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
                content = contenu,
            )
        }
    }
}


@Composable
private fun Actions(
    retour: String,
    surRetour: () -> Unit,
    action: String,
    actif: Boolean,
    couleurRetour: Color? = null,
    surAction: () -> Unit,
) {
    val b = LocalBanco.current
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            retour,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = surRetour)
                .padding(vertical = 10.dp),
            style = TextStyle(
                fontFamily = Polices.corps,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = couleurRetour ?: b.lateriteTexte,
            ),
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            action,
            modifier = Modifier.padding(end = 14.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.encre),
        )
        Avancer(actif = actif, description = action, onClick = surAction)
    }
}


