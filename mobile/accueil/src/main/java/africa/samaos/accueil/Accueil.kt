package africa.samaos.accueil

import africa.samaos.banco.*
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * L'Accueil de l'Espace actif : l'heure dans le ciel, huit applis, la Natte en bas.
 * Le doigt qui monte fait monter le sol de la Cour ; le doigt qui descend fait descendre le Pouls ;
 * trois doigts à l'horizontale passent d'un Espace à l'autre.
 */
@Composable
fun Accueil(
    espaces: Espaces,
    applis: List<Appli>,
    natteMaison: List<Appli>,
    cour: Panneau,
    pouls: Panneau,
    nuit: ReglageNuit,
    retourAccueil: Int,
    demandeEspaces: Int = 0,
    lancer: (Appli) -> Unit,
) {
    val b = LocalBanco.current
    val densite = LocalDensity.current
    val barreEtat = WindowInsets.statusBars.getTop(densite)
    val espace = espaces.actif
    // La vue des Espaces et ses réglages, l'un sur l'autre ; null : l'Accueil.
    var nav by remember { mutableStateOf<NavEspaces?>(null) }
    val contexte = LocalContext.current
    val tuilesPouls = remember { TuilesPouls(contexte) }
    var modifierPouls by remember { mutableStateOf(false) }
    // L'Accueil rangé par la personne : applis, dossiers, widgets.
    val disposition = remember { Disposition(contexte) }
    val parCle = remember(applis) { applis.associateBy { cleDe(it) } }
    val elements = remember(applis, natteMaison, disposition.rangees) { disposition.elements(applis, natteMaison) }
    var tenue by remember { mutableStateOf<Tenue?>(null) }
    var dossierOuvert by remember { mutableStateOf<String?>(null) }
    var modifierAccueil by remember { mutableStateOf<VueModifier?>(null) }
    var widgetTenu by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) {
        val hote = Widgets.hote(contexte)
        hote.appuiLong = { widgetTenu = it }
        onDispose { hote.appuiLong = null }
    }
    // Le geste de l'accueil referme ce qui est ouvert par-dessus (ce qui est rangé est déjà gardé).
    LaunchedEffect(retourAccueil) {
        modifierPouls = false
        tenue = null
        dossierOuvert = null
        modifierAccueil = null
        widgetTenu = null
    }
    // Depuis les Réglages de Sama : les réglages des Espaces.
    LaunchedEffect(demandeEspaces) {
        if (demandeEspaces > 0) {
            cour.fermerTout()
            pouls.fermerTout()
            nav = NavEspaces.Reglages
        }
    }
    val actionsAccueil = ActionsAccueil(
        surAccueil = { a -> disposition.contient(elements, cleDe(a)) },
        ajouter = { a -> disposition.ajouter(elements, cleDe(a)) },
        retirer = { a -> disposition.retirer(elements, cleDe(a)) },
        sortir = { d, a -> disposition.sortir(elements, d, cleDe(a)) },
    )
    val scope = rememberCoroutineScope()
    val bascule = remember { Bascule(scope) }

    BackHandler(enabled = cour.visible) { cour.fermer() }
    BackHandler(enabled = pouls.visible) { pouls.fermer() }

    // Le geste part d'un sens, puis il appartient au panneau qu'il a choisi jusqu'au lever du doigt.
    var guide by remember { mutableStateOf<Panneau?>(null) }
    val glissement = rememberDraggableState { dy ->
        val p = guide ?: (if (dy < 0f) cour else pouls).also { guide = it }
        p.glisser(dy)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                with(densite) {
                    // Le sol de la Cour part du bas de l'écran et s'arrête sous le titre : c'est la course du doigt.
                    cour.course = it.height - (barreEtat + 104.dp.toPx())
                    cour.seuilElan = 600.dp.toPx()
                    pouls.seuilElan = 600.dp.toPx()
                    bascule.largeur = it.width.toFloat()
                    bascule.ecart = 20.dp.toPx()
                    bascule.seuilElan = 900.dp.toPx()
                }
            }
            .troisDoigts(
                bascule = bascule,
                permis = {
                    espaces.profils && troisDoigtsPermis(contexte) && !cour.visible && !pouls.visible && nav == null && !modifierPouls &&
                        tenue == null && dossierOuvert == null && modifierAccueil == null && widgetTenu == null &&
                        !espaces.enCreation && espaces.passage == null && !espaces.sansCode
                },
                aGauche = { espaces.voisin(-1) != null },
                aDroite = { espaces.voisin(1) != null },
                arriver = { sens -> espaces.voisin(sens)?.let { espaces.aller(it) } },
            ),
    ) {
        // Pendant le geste à trois doigts, les Espaces voisins attendent de part et d'autre, en cartes.
        if (bascule.enCours) {
            Box(Modifier.fillMaxSize().background(b.sol2))
            listOf(-1, 1).forEach { sens ->
                espaces.voisin(sens)?.let { v ->
                    ApercuEspace(
                        v,
                        ouvert = v.estMaison,
                        echelle = 1f,
                        modifier = Modifier.graphicsLayer {
                            scaleX = bascule.echelle
                            scaleY = bascule.echelle
                            translationX = bascule.decalage + sens * bascule.pas
                            shape = RoundedCornerShape(44.dp)
                            clip = true
                        },
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (bascule.reduction > 0f) {
                        scaleX = bascule.echelle
                        scaleY = bascule.echelle
                        translationX = bascule.decalage
                        shape = RoundedCornerShape(44.dp)
                        clip = true
                    }
                },
        ) {
            // Quand la Cour monte, le soleil monte avec elle et les collines passent derrière la crête.
            PaysageMobile(astre = Astre(286f, 430f, 70f), progres = { cour.progres })
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val c = cour.progres
                        alpha = 1f - (c * 1.6f).coerceAtMost(1f)
                        translationY = -c * 64.dp.toPx()
                        // Sous le Pouls, l'Accueil se floute. Dans le calque, pour ne pas toucher au geste en cours.
                        val flou = pouls.progres * 18.dp.toPx()
                        renderEffect = if (Build.VERSION.SDK_INT >= 31 && flou > 0f) BlurEffect(flou, flou, TileMode.Decal) else null
                    }
                    .draggable(
                        state = glissement,
                        orientation = Orientation.Vertical,
                        onDragStarted = { guide = null },
                        onDragStopped = { vy ->
                            guide?.relacher(vy)
                            guide = null
                        },
                    )
                    // Un appui long sur le paysage : « Modifier l'Accueil ».
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { modifierAccueil = VueModifier.Principal }) },
            ) {
                Column(Modifier.statusBarsPadding()) {
                    Horloge(
                        espace = espace,
                        ouvrirEspaces = { nav = NavEspaces.Vue },
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 38.dp),
                    )
                    // Les widgets posés, sous l'heure.
                    ZoneWidgets(
                        disposition.widgets,
                        retirerAbsent = { disposition.retirerWidget(it) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
                    )
                }
                GrilleAccueil(
                    elements = elements,
                    applis = parCle,
                    gestes = GestesGrille(
                        lancer = lancer,
                        ouvrirDossier = { dossierOuvert = it.ident },
                        menu = { a, r -> tenue = Tenue(a, r, Origine.Accueil) },
                        fermerMenu = { tenue = null },
                        deplacer = { depuis, vers -> disposition.deplacer(elements, depuis, vers) },
                        regrouper = { depuis, sur -> disposition.regrouper(elements, depuis, sur) },
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 132.dp),
                )
                Natte(
                    applis = natteMaison,
                    ouvrirCour = { cour.ouvrir() },
                    lancer = lancer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 30.dp),
                )
            }
        }
        if (bascule.enCours) RepereBascule(espaces, bascule)
        // La Cour et le Pouls restent prêts hors de l'écran : rien à construire quand le doigt les appelle.
        Cour(applis = applis, espace = espace, panneau = cour, lancer = lancer, menu = { a, r -> tenue = Tenue(a, r, Origine.Cour) })
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = pouls.progres }.background(b.voile))
        Pouls(
            panneau = pouls,
            applis = applis,
            nuit = nuit,
            espaces = espaces,
            tuiles = tuilesPouls,
            ouvrirEspaces = { nav = NavEspaces.Vue },
            modifierTuiles = { modifierPouls = true },
        )
        if (modifierPouls) ModifierPouls(tuilesPouls, nuit, espaces, fermer = { modifierPouls = false })
        dossierOuvert?.let { ident -> elements.firstOrNull { it is ElemDossier && it.ident == ident } as? ElemDossier }?.let { d ->
            FenetreDossier(
                d,
                parCle,
                renommer = { nom -> disposition.renommer(elements, d, nom) },
                lancer = lancer,
                menu = { a, r -> tenue = Tenue(a, r, Origine.Dossier(d)) },
                fermer = { dossierOuvert = null },
            )
        }
        modifierAccueil?.let { v ->
            ModifierAccueil(
                vue = v,
                espaces = espaces,
                disposition = disposition,
                elements = elements,
                applis = parCle,
                natte = natteMaison,
                changer = { modifierAccueil = it },
            )
        }
        tenue?.let { MenuAppli(it, espaces, actionsAccueil, fermer = { tenue = null }) }
        widgetTenu?.let { id ->
            MenuWidget(
                retirer = {
                    disposition.retirerWidget(id)
                    Widgets.retirer(contexte, id)
                },
                fermer = { widgetTenu = null },
            )
        }
        when (val n = nav) {
            null -> {}
            NavEspaces.Vue -> VueEspaces(espaces, fermer = { nav = null }, reglages = { nav = NavEspaces.Reglages })
            NavEspaces.Reglages -> ListeReglagesEspaces(
                espaces,
                retour = { nav = NavEspaces.Vue },
                ouvrir = { nav = NavEspaces.Reglage(it.id) },
            )
            is NavEspaces.Reglage -> espaces.liste.firstOrNull { it.id == n.id }?.let { e ->
                ReglagesEspace(
                    espaces,
                    e,
                    retour = { nav = NavEspaces.Reglages },
                    supprimer = { nav = NavEspaces.Suppression(e.id) },
                )
            }
            is NavEspaces.Suppression -> espaces.liste.firstOrNull { it.id == n.id }?.let { e ->
                SuppressionEspace(espaces, e, annuler = { nav = NavEspaces.Reglage(e.id) }, fait = { nav = NavEspaces.Reglages })
            }
        }
        if (espaces.sansCode) EcranProteger(espaces)
        if (espaces.enCreation) EcranNouvelEspace(espaces, applis)
        if (espaces.enPret) EcranPreter(espaces)
        espaces.passage?.let { EcranPassage(espaces, it) }
    }
}

/** En haut, d'où l'on part et où l'on va ; en bas, ce qui se passera si l'on relâche. */
@Composable
private fun RepereBascule(espaces: Espaces, bascule: Bascule) {
    val b = LocalBanco.current
    val cible = if (bascule.vers == 0) null else espaces.voisin(bascule.vers)
    val style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = b.encre)
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
                .height(30.dp)
                .background(b.voile, RoundedCornerShape(50))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(espaces.actif.couleur(b), CircleShape))
            Spacer(Modifier.width(6.dp))
            BasicText(espaces.actif.nom, style = style)
            if (cible != null) {
                IconeTrait(Icones.AVANCER, 14.dp, b.encre2, Modifier.padding(horizontal = 8.dp))
                Box(Modifier.size(7.dp).background(cible.couleur(b), CircleShape))
                Spacer(Modifier.width(6.dp))
                BasicText(cible.nom, style = style)
            }
        }
        if (cible != null) {
            BasicText(
                "Relâchez pour ouvrir ${cible.nom}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .graphicsLayer {
                        alpha = ((kotlin.math.abs(bascule.decalage) / (bascule.largeur * 0.22f)) - 0.5f).coerceIn(0f, 1f) * 2f
                    },
                style = style.copy(fontSize = 15.sp),
            )
        }
    }
}

/** Où l'on est dans la vue des Espaces et ses réglages. */
sealed interface NavEspaces {
    data object Vue : NavEspaces
    data object Reglages : NavEspaces
    data class Reglage(val id: Int) : NavEspaces
    data class Suppression(val id: Int) : NavEspaces
}


/** L'heure qui change à chaque minute pile. */
@Composable
fun rememberMaintenant(): LocalDateTime {
    var maintenant by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            maintenant = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    return maintenant
}

fun dateDuJour(maintenant: LocalDateTime): String =
    maintenant.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
        .replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** L'heure fine et haute, la date, et le galet de l'Espace actif, qui ouvre la vue des Espaces. */
@Composable
private fun Horloge(espace: Espace, ouvrirEspaces: () -> Unit, modifier: Modifier = Modifier) {
    val b = LocalBanco.current
    val maintenant = rememberMaintenant()
    Column(modifier) {
        BasicText(
            text = maintenant.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = TextStyle(
                fontFamily = Polices.horloge,
                fontWeight = FontWeight(200),
                fontSize = 112.sp,
                lineHeight = 104.sp,
                letterSpacing = (-0.01).em,
                color = b.encre,
            ),
        )
        BasicText(
            text = dateDuJour(maintenant),
            modifier = Modifier.padding(top = 12.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = b.encre),
        )
        EspaceActif(espace, ouvrirEspaces, Modifier.padding(top = 10.dp))
    }
}

/** Le galet de l'Espace actif : dans Sama OS, il vivra dans la barre d'état du système. */
@Composable
fun EspaceActif(espace: Espace, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val b = LocalBanco.current
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(b.voile)
            .clickable(onClickLabel = "Voir les Espaces", role = Role.Button, onClick = onClick)
            .height(28.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(espace.couleur(b), CircleShape))
        Spacer(Modifier.width(6.dp))
        BasicText(espace.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = b.encre))
    }
}

/** Une icône d'appli et son nom, comme dans la Cour et sur l'Accueil. */
@Composable
fun IconeAppli(appli: Appli, onClick: () -> Unit, modifier: Modifier = Modifier, menu: ((Rect) -> Unit)? = null) {
    val b = LocalBanco.current
    var bornes by remember { mutableStateOf(Rect.Zero) }
    Column(
        modifier.then(
            if (menu != null) {
                Modifier.combinedClickable(
                    onClickLabel = "Ouvrir ${appli.nom}",
                    role = Role.Button,
                    onLongClickLabel = "Menu de l'appli",
                    onLongClick = { menu(bornes) },
                    onClick = onClick,
                )
            } else {
                Modifier.clickable(onClickLabel = "Ouvrir ${appli.nom}", role = Role.Button, onClick = onClick)
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(appli.icone, contentDescription = null, modifier = Modifier.size(60.dp).onGloballyPositioned { bornes = it.boundsInRoot() })
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = appli.nom,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = TextStyle(
                fontFamily = Polices.corps,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = b.encre,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** La Natte : le bouton de la Cour et quatre applis, sur un galet qui flotte au-dessus du paysage. */
@Composable
private fun Natte(applis: List<Appli>, ouvrirCour: () -> Unit, lancer: (Appli) -> Unit, modifier: Modifier = Modifier) {
    val b = LocalBanco.current
    val forme = RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .shadow(18.dp, forme, ambientColor = b.ombre, spotColor = b.ombre)
            .background(b.sol, forme)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(b.laterite)
                .clickable(onClickLabel = "Ouvrir la Cour", role = Role.Button, onClick = ouvrirCour),
            contentAlignment = Alignment.Center,
        ) {
            Elephant(taille = 32.dp, couleur = b.surLaterite, fond = b.laterite)
        }
        applis.take(4).forEach { a ->
            Image(
                bitmap = a.icone,
                contentDescription = a.nom,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .clickable(onClickLabel = "Ouvrir ${a.nom}", role = Role.Button) { lancer(a) },
            )
        }
    }
}
