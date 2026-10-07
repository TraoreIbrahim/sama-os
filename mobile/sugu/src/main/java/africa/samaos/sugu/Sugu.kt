package africa.samaos.sugu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.NavAppli
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import africa.samaos.banco.appli.couleurDe
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.proches.Categories
import africa.samaos.proches.Cercle
import africa.samaos.proches.Editeurs
import africa.samaos.proches.Installations
import africa.samaos.proches.Offre
import africa.samaos.proches.Partage
import africa.samaos.proches.Point
import africa.samaos.proches.Verification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private sealed interface Vue {
    data class Onglet(val i: Int) : Vue
    data class Fiche(val paquet: String) : Vue
    data class Envoyer(val paquet: String) : Vue
}

private const val DECOUVRIR = 0
private const val AUTOUR = 1
private const val MISES_A_JOUR = 2
private const val MES_APPLIS = 3

/** Sugu, le magasin d'applis de Sama (maquettes l2-sug-accueil, l2-sug-fiche, l2-sug-maj, i6-sugu-proches). */
class Sugu : ComponentActivity() {
    private val pile = mutableStateListOf<Vue>(Vue.Onglet(DECOUVRIR))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Une fenêtre restée « ouverte » après l'arrêt de Sugu ne l'est plus.
        if (!Echange.ouvert() && Partage.ouvertJusqua(this) != 0L) Partage.reglerOuvert(this, 0L)
        lire(intent)
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.SuguNuit else Identites.Sugu
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) { Racine(pile) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    /** Ouvert depuis les Réglages (un point Sama, les proches), une notification, ou sur la fiche d'une appli. */
    private fun lire(i: Intent?) {
        when (i?.action) {
            "africa.samaos.action.SUGU_AUTOUR" -> {
                val hote = i.getStringExtra("hote")
                if (hote != null) Partage.ajouterPoint(this, Point(i.getStringExtra("nom") ?: "Point Sama", hote, i.getIntExtra("port", africa.samaos.proches.Protocole.PORT)))
                pile.clear()
                pile.add(Vue.Onglet(AUTOUR))
            }
            "africa.samaos.action.SUGU_PROCHES" -> {
                if (i.getBooleanExtra("ouvrir", false) && !Echange.ouvert()) Echange.ouvrir(this)
                pile.clear()
                pile.add(Vue.Onglet(AUTOUR))
            }
            "africa.samaos.action.SUGU_FICHE" -> i.getStringExtra("paquet")?.let { pile.add(Vue.Fiche(it)) }
        }
    }
}

@Composable
private fun Racine(pile: MutableList<Vue>) {
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var reprise by remember { mutableIntStateOf(0) }
    // Le catalogue se relit à l'ouverture, puis chaque minute tant que Sugu est ouvert ; les points du réseau
    // annoncés en mDNS s'ajoutent au fur et à mesure.
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { Catalogue.actualiser(c) }
            reprise++
            delay(60_000)
        }
    }
    DisposableEffect(Unit) {
        Catalogue.ecouter(c) { portee.launch(Dispatchers.IO) { Catalogue.actualiser(c) } }
        onDispose { Catalogue.arreterEcoute() }
    }
    // Chez les proches, plus souvent : ils ne restent ouverts que 10 minutes.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            if (Echange.voisins.isNotEmpty()) withContext(Dispatchers.IO) { Echange.actualiserTous(c) }
        }
    }
    val aller: (Vue) -> Unit = { pile.add(it) }
    val retour: () -> Unit = { if (pile.size > 1) pile.removeAt(pile.lastIndex) }
    BackHandler(enabled = pile.size > 1) { retour() }
    Box(Modifier.fillMaxSize()) {
        when (val v = pile.last()) {
            is Vue.Onglet -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (v.i) {
                        DECOUVRIR -> PageDecouvrir(aller) { pile.clear(); pile.add(Vue.Onglet(AUTOUR)) }
                        AUTOUR -> PageAutour(aller) { portee.launch(Dispatchers.IO) { Catalogue.actualiser(c) } }
                        MISES_A_JOUR -> PageMisesAJour(aller)
                        else -> PageMesApplis(aller)
                    }
                }
                val maj = remember(reprise, Catalogue.points.size, Installations.fins) { Catalogue.misesAJour(c).size }
                NavAppli(
                    listOf(
                        "Découvrir" to SAC,
                        "Autour" to Icones.PROXIMITE,
                        (if (maj > 0) "Mises à jour · $maj" else "Mises à jour") to Icones.ROTATION,
                        "Mes applis" to Icones.TELEPHONE,
                    ),
                    v.i,
                ) { i -> pile.clear(); pile.add(Vue.Onglet(i)) }
            }
            is Vue.Fiche -> PageFiche(v.paquet, aller, retour)
            is Vue.Envoyer -> PageEnvoyer(v.paquet, retour)
        }
        Refus()
        PropositionRecue(aller)
    }
}

private const val SAC = "M5.5 8h13l-1.2 12H6.7z M9 8V7a3 3 0 0 1 6 0v1"

fun taille(o: Long): String = when {
    o >= 1_000_000_000 -> String.format(Locale.FRENCH, "%.1f Go", o / 1e9)
    o >= 10_000_000 -> "${o / 1_000_000} Mo"
    o >= 1_000_000 -> String.format(Locale.FRENCH, "%.1f Mo", o / 1e6)
    else -> "${(o / 1000).coerceAtLeast(1)} Ko"
}

/** L'icône d'une appli : la sienne quand elle est installée, celle de la vitrine sinon, ou ses initiales. */
@Composable
private fun IconeAppli(o: Offre?, paquet: String, nom: String, taille: Dp) {
    val c = LocalContext.current
    // L'image de l'icône est faite une fois, pas à chaque dessin.
    val installee = remember(paquet, Installations.fins) {
        try {
            c.packageManager.getApplicationIcon(paquet).toBitmap(144, 144).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    val forme = RoundedCornerShape(taille * 0.28f)
    when {
        installee != null -> Image(installee, contentDescription = null, modifier = Modifier.size(taille).clip(forme))
        o != null && Catalogue.icone(c, o) != null -> Image(Catalogue.icone(c, o)!!, contentDescription = null, modifier = Modifier.size(taille).clip(forme))
        else -> Box(Modifier.size(taille).clip(forme).background(couleurDe(nom)), contentAlignment = Alignment.Center) {
            BasicText(nom.take(1).uppercase(), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = (taille.value * 0.42f).sp, color = Color.White))
        }
    }
}

/** Le bouton d'une appli : Installer, Mettre à jour, Ouvrir, ou l'avancement. */
@Composable
private fun Action(o: Offre, plein: Boolean = false) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val e = Installations.de(o.fiche.paquet)
    val installee = Verification.versionInstallee(c, o.fiche.paquet)
    when {
        e != null && e.enCours -> BasicText(
            when (e.etape) {
                Installations.Etape.RECEPTION -> "${(e.recu * 100 / o.fiche.taille.coerceAtLeast(1)).toInt()} %"
                Installations.Etape.VERIFICATION -> "Vérification"
                else -> "Installation"
            },
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.accentTexte),
        )
        installee != null && installee >= o.fiche.version -> BoutonTexteAppli("Ouvrir", style = 's') {
            c.packageManager.getLaunchIntentForPackage(o.fiche.paquet)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        installee != null -> BoutonTexteAppli("Mettre à jour", style = if (plein) ' ' else 's') { Catalogue.lancer(c, o) }
        else -> BoutonTexteAppli("Installer", style = if (plein) ' ' else 's') { Catalogue.lancer(c, o) }
    }
}

// ——— Découvrir (maquette l2-sug-accueil) ———

@Composable
private fun PageDecouvrir(aller: (Vue) -> Unit, autour: () -> Unit) {
    val a = LocalIdentite.current
    var recherche by remember { mutableStateOf("") }
    var filtre by remember { mutableStateOf("tout") }
    val toutes = Catalogue.offres()
    val categories = toutes.mapNotNull { it.vitrine?.categorie?.ifBlank { null } }.distinct()
    val vues = toutes.filter { o ->
        val v = o.vitrine
        (recherche.isBlank() || listOfNotNull(o.nom, v?.resume, v?.editeurNom).any { it.contains(recherche, true) }) &&
            when (filtre) {
                "tout" -> true
                "sansdata" -> v?.horsLigne == true
                "legeres" -> o.fiche.taille < 10_000_000
                else -> v?.categorie == filtre
            }
    }
    EcranAppli {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(a.accent), contentAlignment = Alignment.Center) { IconeTrait(SAC, 20.dp, a.surAccent) }
            Spacer(Modifier.width(12.dp))
            BasicText("Sugu", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = a.encre))
        }
        ChampAppli(recherche, "Rechercher une appli", { recherche = it })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PuceFiltre("Pour vous", filtre == "tout") { filtre = "tout" }
            PuceFiltre("Sans data", filtre == "sansdata") { filtre = "sansdata" }
            PuceFiltre("Légères", filtre == "legeres") { filtre = "legeres" }
            categories.forEach { k -> PuceFiltre(Categories.nom(k), filtre == k) { filtre = k } }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (toutes.isEmpty()) {
                item {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BasicText(
                            if (Catalogue.charge) "Aucun point Sama à portée" else "Recherche des points Sama…",
                            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = a.encre),
                        )
                        BasicText(
                            "Sugu se remplit sans data sur le Wi-Fi d'une école, d'une mairie ou d'un cybercafé qui a un point Sama. " +
                                "Le catalogue en ligne de Sugu arrive bientôt.",
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
                        )
                        BoutonTexteAppli("Ajouter un point Sama", style = 's', onClick = autour)
                    }
                }
                return@LazyColumn
            }
            val sansData = toutes.count { it.vitrine?.horsLigne == true }
            if (filtre == "tout" && recherche.isBlank() && sansData > 0) item {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(a.accent)
                        .clickable(role = Role.Button) { filtre = "sansdata" }.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText("Elles marchent sans connexion", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, color = a.surAccent))
                    BasicText(
                        "$sansData appli${if (sansData > 1) "s" else ""} du catalogue ${if (sansData > 1) "marchent" else "marche"} sans Internet, une fois installée${if (sansData > 1) "s" else ""}.",
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.surAccent.copy(alpha = 0.9f)),
                    )
                }
            }
            if (filtre == "tout" && recherche.isBlank()) {
                item { Rub("Près de vous, sans data") }
                item {
                    LazyRow(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(toutes.take(10), key = { it.fiche.paquet }) { o ->
                            Column(Modifier.width(76.dp).clickable(role = Role.Button) { aller(Vue.Fiche(o.fiche.paquet)) }) {
                                IconeAppli(o, o.fiche.paquet, o.nom, 76.dp)
                                Spacer(Modifier.height(6.dp))
                                BasicText(o.nom, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, color = a.encre))
                                BasicText(taille(o.fiche.taille), style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, color = a.encre2))
                            }
                        }
                    }
                }
            }
            // Par catégorie (ou la liste filtrée).
            val groupes = vues.groupBy { it.vitrine?.categorie?.ifBlank { null } ?: "autres" }
            groupes.forEach { (k, l) ->
                item(key = "rub-$k") { Rub(if (k == "ecole") "Pour l'école" else Categories.nom(k)) }
                items(l, key = { "o-" + it.fiche.paquet }) { o ->
                    LigneAppli(
                        o.nom,
                        second = listOfNotNull(o.vitrine?.resume?.ifBlank { null }, taille(o.fiche.taille)).joinToString(" · "),
                        debut = { IconeAppli(o, o.fiche.paquet, o.nom, 48.dp) },
                        fin = { Action(o) },
                    ) { aller(Vue.Fiche(o.fiche.paquet)) }
                }
            }
            if (vues.isEmpty()) item {
                BasicText("Rien ne correspond.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ——— La fiche d'une appli (maquette l2-sug-fiche) ———

/** L'offre d'une appli que ce téléphone a déjà, et peut donner. */
private val ICI = Point("Ce téléphone", "", 0)

@Composable
private fun PageFiche(paquet: String, aller: (Vue) -> Unit, retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    // Ce téléphone peut-il la donner ? Oui si son fichier installé est exactement celui d'une fiche signée.
    var donnable by remember { mutableStateOf<Gardees.Donnable?>(null) }
    LaunchedEffect(paquet, Installations.fins) { donnable = withContext(Dispatchers.IO) { Gardees.donnable(c, paquet) } }
    val o = Catalogue.offre(paquet) ?: donnable?.let { Offre(it.fiche, it.vitrine, ICI) }
    EcranAppli {
        Tete("", retour = retour, petit = true) {
            if (donnable != null) BoutonAppli(Icones.PARTAGER, "Envoyer à un proche") { aller(Vue.Envoyer(paquet)) }
        }
        if (o == null) {
            BasicText("Cette appli n'est plus proposée à portée.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
            return@EcranAppli
        }
        val v = o.vitrine
        val e = Installations.de(paquet)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconeAppli(o, paquet, o.nom, 88.dp)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(o.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, color = a.encre))
                    v?.editeurNom?.ifBlank { null }?.let { BasicText(it, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.accentTexte)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconeTrait(Icones.BOUCLIER, 16.dp, a.encre2)
                        BasicText("Vérifiée par ${Editeurs.nomCourt(o.fiche.editeur)}", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
                    }
                }
            }
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(0.5.dp).background(a.trait))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Chiffre(o.fiche.versionNom.ifBlank { "—" }, "Version", Modifier.weight(1f))
                Chiffre(taille(o.fiche.taille), "Taille", Modifier.weight(1f))
                Chiffre(if (v?.horsLigne == true) "Hors ligne" else "En ligne", if (v?.horsLigne == true) "Sans data" else "Demande Internet", Modifier.weight(1f))
            }
            Box(Modifier.padding(horizontal = 20.dp)) {
                if (e != null && e.enCours) {
                    val part = (e.recu.toFloat() / o.fiche.taille.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(a.voile), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.fillMaxWidth(part.coerceAtLeast(0.02f)).fillMaxHeight().background(a.accent))
                            BasicText(
                                when (e.etape) {
                                    Installations.Etape.RECEPTION -> "Installation · ${(part * 100).toInt()} %"
                                    Installations.Etape.VERIFICATION -> "Vérification du fichier"
                                    else -> "Installation"
                                },
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp),
                                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (part > 0.4f) a.surAccent else a.accentTexte),
                            )
                        }
                        if (e.etape == Installations.Etape.RECEPTION) BasicText(
                            "Annuler",
                            modifier = Modifier.clickable(role = Role.Button) { Installations.arreter(o.fiche.sha256) }.padding(horizontal = 20.dp, vertical = 14.dp),
                            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.accentTexte),
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Action(o, plein = true) }
                }
            }
            if (e?.etape == Installations.Etape.ECHEC) e.message?.let {
                BasicText(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.accentTexte))
            }
            Spacer(Modifier.height(10.dp))
            if (donnable != null) Box(Modifier.padding(horizontal = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(a.voile).clickable(role = Role.Button) { aller(Vue.Envoyer(paquet)) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconeTrait(Icones.PROXIMITE, 18.dp, a.accentTexte)
                    Spacer(Modifier.width(8.dp))
                    BasicText("Envoyer à un proche, sans data", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.accentTexte))
                }
            }
            v?.description?.ifBlank { null }?.let {
                BasicText(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 23.sp, color = a.encre))
            }
            Rub("Ce que l'appli demande")
            val droits = v?.droits.orEmpty()
            if (droits.isEmpty()) LigneAppli("Rien de particulier", debut = { IconeTrait(Icones.COCHE, 20.dp, a.encre2) })
            droits.forEach { d -> LigneAppli(d, debut = { IconeTrait(Icones.CADENAS, 20.dp, a.encre2) }) }
            if (v?.sansTraceur == true) LigneAppli("Aucun traceur publicitaire", debut = { IconeTrait(Icones.BOUCLIER, 20.dp, a.encre2) })
            Rub("D'où elle vient")
            when {
                o.point === ICI -> LigneAppli("Ce téléphone", second = "Installée depuis un fichier vérifié", debut = { IconeTrait(Icones.TELEPHONE, 20.dp, a.encre2) })
                o.point.proche != null -> LigneAppli("Chez ${o.point.nom}", second = "Téléphone d'un proche reconnu · sans data", debut = { IconeTrait(Icones.PERSONNE, 20.dp, a.encre2) })
                else -> LigneAppli(o.point.nom, second = "Point Sama · ${o.point.adresse} · sans data", debut = { IconeTrait(Icones.PROXIMITE, 20.dp, a.encre2) })
            }
            BasicText(
                "Avant d'installer, Sugu vérifie que le fichier est exactement celui que ${Editeurs.nomCourt(o.fiche.editeur)} a publié. Sinon, rien n'est installé.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Chiffre(valeur: String, legende: String, modifier: Modifier) {
    val a = LocalIdentite.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(valeur, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = a.encre))
        BasicText(legende, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, color = a.encre2))
    }
}

// ——— Autour de vous (maquette i6-sugu-proches) ———

@Composable
private fun PageAutour(aller: (Vue) -> Unit, actualiser: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    var saisie by remember { mutableStateOf("") }
    var v by remember { mutableIntStateOf(0) }
    val points = remember(v, Catalogue.points.size, Catalogue.trouves.size) { Catalogue.connus(c) }
    val bloques = remember(v) { Partage.bloques(c).sorted() }
    var reconnus by remember { mutableStateOf<List<Cercle.Reconnu>?>(null) }
    LaunchedEffect(Unit) { reconnus = withContext(Dispatchers.IO) { Cercle.reconnus(c) } }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            maintenant = System.currentTimeMillis()
        }
    }
    EcranAppli {
        Tete("Autour de vous")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                BasicText(
                    "Ces applis sont à portée, sans data : sur les points Sama du quartier et chez vos proches. Sugu vérifie que c'est exactement le même fichier.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
                )
            }
            if (points.isEmpty()) item {
                Note("Aucun point Sama à portée. Sur le Wi-Fi d'un point, il apparaît tout seul ; sinon, ajoutez-le par son adresse.")
            }
            points.forEach { p ->
                val cat = Catalogue.points[p.adresse]
                item(key = "p-" + p.adresse) { Rub(p.nom + " · " + p.hote) }
                when {
                    !Catalogue.points.containsKey(p.adresse) -> item(key = "a-" + p.adresse) { Note("Recherche…") }
                    cat == null -> item(key = "a-" + p.adresse) { Note("Ne répond pas : il faut être sur le même Wi-Fi que ce point.") }
                    cat.offres.isEmpty() -> item(key = "a-" + p.adresse) { Note("Rien à proposer pour l'instant.") }
                    else -> items(cat.offres, key = { p.adresse + it.fiche.paquet }) { o ->
                        LigneAppli(o.nom, second = "${taille(o.fiche.taille)} · ${p.nom}", debut = { IconeAppli(o, o.fiche.paquet, o.nom, 48.dp) }, fin = { Action(o) }) {
                            aller(Vue.Fiche(o.fiche.paquet))
                        }
                    }
                }
                if (cat != null && cat.ecartes > 0) item(key = "e-" + p.adresse) {
                    Note("${cat.ecartes} fichier${if (cat.ecartes > 1) "s" else ""} écarté${if (cat.ecartes > 1) "s" else ""} : pas de signature valable de Sama ou de Sugu.")
                }
            }
            item { Rub("Chez vos proches") }
            item { CarteProches(reconnus, maintenant) }
            if (Echange.ouvert() && !reconnus.isNullOrEmpty()) {
                val voisins = Echange.voisins.values.sortedBy { it.nom.lowercase() }
                if (voisins.isEmpty()) item {
                    Note("Personne pour l'instant. Vos proches doivent ouvrir aussi Sugu › Autour, près de vous, sur le même Wi-Fi.")
                }
                voisins.forEach { vo ->
                    val cat = vo.catalogue
                    item(key = "v-" + vo.id) { Rub("Chez ${vo.nom}") }
                    when {
                        cat == null -> item(key = "va-" + vo.id) { Note("Recherche de ce qu'il partage…") }
                        cat.offres.isEmpty() -> item(key = "va-" + vo.id) { Note("${vo.nom} est là, mais ne partage rien pour l'instant.") }
                        else -> items(cat.offres, key = { "vo-" + vo.id + it.fiche.paquet }) { o ->
                            LigneAppli(o.nom, second = "${taille(o.fiche.taille)} · version ${o.fiche.versionNom}", debut = { IconeAppli(o, o.fiche.paquet, o.nom, 48.dp) }, fin = { Action(o) }) {
                                aller(Vue.Fiche(o.fiche.paquet))
                            }
                        }
                    }
                }
            }
            item { Rub("Ajouter un point par son adresse") }
            item {
                Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ChampAppli(saisie, "Par exemple 192.168.1.20", { saisie = it.trim().take(40) }) }
                    Partage.lireAdresse(saisie)?.let { p ->
                        BoutonTexteAppli("Ajouter", style = 's') {
                            // L'ajouter soi-même, c'est aussi le débloquer.
                            Partage.reglerBloque(c, p.adresse, false)
                            Partage.ajouterPoint(c, p)
                            saisie = ""
                            v++
                            actualiser()
                        }
                    }
                }
            }
            if (bloques.isNotEmpty()) {
                item { Rub("Points bloqués") }
                items(bloques, key = { "b-$it" }) { adr ->
                    val nom = Partage.points(c).firstOrNull { it.adresse == adr }?.nom
                    LigneAppli(nom ?: adr, second = if (nom != null) adr else null, fin = {
                        BoutonTexteAppli("Débloquer", style = 's') {
                            Partage.reglerBloque(c, adr, false)
                            v++
                            actualiser()
                        }
                    })
                }
            }
            item {
                val eco = Partage.economise(c)
                if (eco > 0) Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.voile).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconeTrait(Icones.BOUCLIER, 20.dp, a.accentTexte)
                    BasicText("${taille(eco)} de data économisés grâce aux points Sama et à vos proches.", style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Ouvrir le téléphone à ses proches, 10 minutes (ou d'abord les reconnaître). */
@Composable
private fun CarteProches(reconnus: List<Cercle.Reconnu>?, maintenant: Long) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val titre = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = a.encre)
    val texte = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2)
    if (reconnus == null) return
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(a.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            reconnus.isEmpty() -> {
                BasicText("Reconnaissez d'abord vos proches", style = titre)
                BasicText("Chacun scanne le code de l'autre, téléphones côte à côte. Ensuite, vous pourrez vous envoyer des applis sans data.", style = texte)
                BoutonTexteAppli("Reconnaître un proche", style = 's') {
                    try {
                        c.startActivity(Intent("africa.samaos.action.PROCHES").setPackage("africa.samaos.reglages").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
            }
            !Echange.ouvert() -> {
                BasicText("Ouvrir à mes proches", style = titre)
                BasicText(
                    "Pendant 10 minutes, vos proches reconnus voient votre téléphone et ce que vous pouvez leur donner ; vous voyez les leurs s'ils sont ouverts aussi. Personne d'autre.",
                    style = texte,
                )
                BoutonTexteAppli("Ouvrir · 10 min") { Echange.ouvrir(c) }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("Ouvert à vos proches", style = titre)
                    val minutes = ((Echange.jusqua - maintenant) / 60_000 + 1).coerceAtLeast(1)
                    BasicText("Encore $minutes min · ${reconnus.size} proche${if (reconnus.size > 1) "s" else ""} reconnu${if (reconnus.size > 1) "s" else ""}", style = texte)
                }
                BoutonTexteAppli("Fermer", style = 's') { Echange.fermer(c) }
            }
        }
        if (Echange.ouvert() && reconnus.isNotEmpty()) {
            val raison = remember(maintenant) { Don.pourquoiPas(c) }
            if (raison != null) BasicText(raison, style = texte)
        }
    }
}

@Composable
private fun Note(texte: String) {
    BasicText(texte, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = LocalIdentite.current.encre2))
}

// ——— Mises à jour (maquette l2-sug-maj) ———

@Composable
private fun PageMisesAJour(aller: (Vue) -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val prefs = remember { c.getSharedPreferences("sugu", 0) }
    var wifi by remember { mutableStateOf(prefs.getBoolean("wifi", true)) }
    val maj = remember(Catalogue.points.toMap(), Installations.fins) { Catalogue.misesAJour(c) }
    // Celles mises à jour pendant cette visite restent dans la liste, avec « Ouvrir ».
    val faites = Installations.etats.values.filter { it.etape == Installations.Etape.INSTALLEE && it.versionAvant != null && maj.none { m -> m.fiche.paquet == it.fiche.paquet } }
    EcranAppli {
        Tete("Mises à jour")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(a.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                when (maj.size) { 0 -> "Tout est à jour"; 1 -> "1 mise à jour"; else -> "${maj.size} mises à jour" },
                                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = a.encre),
                            )
                            val sousTitre = when {
                                maj.isNotEmpty() -> "${taille(maj.sumOf { it.fiche.taille })} en tout, sans data"
                                faites.size == 1 -> "1 appli mise à jour, sans data"
                                faites.isNotEmpty() -> "${faites.size} applis mises à jour, sans data"
                                else -> null
                            }
                            if (sousTitre != null) BasicText(sousTitre, style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
                        }
                        if (maj.size > 1) BoutonTexteAppli("Tout mettre à jour") { maj.forEach { Catalogue.lancer(c, it) } }
                    }
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { wifi = !wifi; prefs.edit().putBoolean("wifi", wifi).apply() }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText("Attendre le Wi-Fi", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre))
                            BasicText("Les points Sama et vos proches ne coûtent rien. Pour le catalogue en ligne, sur data, Sugu demandera avant de dépasser 20 Mo.", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = a.encre2))
                        }
                        Spacer(Modifier.width(12.dp))
                        InterAppli(wifi)
                    }
                }
            }
            items(maj + faites.map { it.offre }, key = { it.fiche.paquet }) { o ->
                LigneAppli(
                    o.nom, second = "${taille(o.fiche.taille)} · version ${o.fiche.versionNom}",
                    debut = { IconeAppli(o, o.fiche.paquet, o.nom, 48.dp) }, fin = { Action(o) },
                ) { aller(Vue.Fiche(o.fiche.paquet)) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ——— Envoyer à un proche (maquettes i6-envoyer-appli, i6-emetteur) ———

@Composable
private fun PageEnvoyer(paquet: String, retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    var donnable by remember { mutableStateOf<Gardees.Donnable?>(null) }
    var reconnus by remember { mutableStateOf<List<Cercle.Reconnu>?>(null) }
    LaunchedEffect(paquet) {
        donnable = withContext(Dispatchers.IO) { Gardees.donnable(c, paquet) }
        val l = withContext(Dispatchers.IO) { Cercle.reconnus(c) }
        reconnus = l
        // Pour envoyer, on s'ouvre aussi : le proche viendra prendre le fichier ici.
        if (!Echange.ouvert() && l.isNotEmpty() && donnable != null) Echange.ouvrir(c)
    }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            maintenant = System.currentTimeMillis()
        }
    }
    val d = donnable
    EcranAppli {
        Tete("Envoyer à un proche", retour = retour)
        val l = reconnus ?: return@EcranAppli
        if (d == null) {
            Note("Cette appli ne peut pas être envoyée : son fichier n'est pas celui d'une fiche signée par Sama ou Sugu.")
            return@EcranAppli
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconeAppli(Offre(d.fiche, d.vitrine, ICI), paquet, d.fiche.nom, 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(d.fiche.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = a.encre))
                        BasicText(
                            "${taille(d.fiche.taille)} · version ${d.fiche.versionNom} · vérifiée par ${Editeurs.nomCourt(d.fiche.editeur)}",
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2),
                        )
                    }
                }
            }
            if (l.isEmpty()) {
                item { Note("Vous n'avez pas encore de proche reconnu. Reconnaissez-vous d'abord, téléphones côte à côte : Réglages › Proche en proche.") }
                return@LazyColumn
            }
            item { Rub("Vos proches") }
            val tries = l.sortedWith(compareBy({ Echange.voisins[it.id] == null }, { it.nom.lowercase() }))
            items(tries, key = { it.id }) { r ->
                val vo = Echange.voisins[r.id]
                val e = Echange.envoi(r.id, d.fiche.sha256)
                LigneAppli(
                    r.nom,
                    second = when (e?.etat) {
                        Echange.EtatEnvoi.PROPOSEE -> "Proposée · ${r.nom} doit accepter"
                        Echange.EtatEnvoi.ENVOI -> "Envoi · ${(e.envoye * 100 / d.fiche.taille.coerceAtLeast(1)).toInt()} %"
                        Echange.EtatEnvoi.ENVOYEE -> "Envoyée, sans data"
                        Echange.EtatEnvoi.REFUSEE -> "${r.nom} n'en veut pas pour l'instant"
                        Echange.EtatEnvoi.DEJA -> "${r.nom} l'a déjà"
                        Echange.EtatEnvoi.ECHEC -> "Pas de réponse · réessayez"
                        null -> if (vo != null) "À portée, sans data" else "Pas à portée"
                    },
                    couleurSecond = if (vo != null && e == null) a.accentTexte else null,
                    debut = { Initiales(r.nom) },
                    fin = {
                        when {
                            vo != null && (e == null || e.etat == Echange.EtatEnvoi.REFUSEE || e.etat == Echange.EtatEnvoi.ECHEC) ->
                                BoutonTexteAppli(if (e == null) "Envoyer" else "Réessayer", style = 's') { Echange.proposer(vo, d.fiche) }
                            // Pas de réponse au bout de 20 secondes : le proche a pu la manquer.
                            vo != null && e?.etat == Echange.EtatEnvoi.PROPOSEE && maintenant - e.quand > 20_000 ->
                                BoutonTexteAppli("Renvoyer", style = 's') { Echange.proposer(vo, d.fiche) }
                            e?.etat == Echange.EtatEnvoi.ENVOYEE -> IconeTrait(Icones.COCHE, 22.dp, a.accentTexte)
                        }
                    },
                )
            }
            item {
                val minutes = ((Echange.jusqua - maintenant) / 60_000 + 1).coerceAtLeast(1)
                Note(
                    (if (Echange.ouvert()) "Votre téléphone est ouvert à vos proches encore $minutes min. " else "") +
                        "Pour apparaître ici, votre proche ouvre Sugu › Autour › « Ouvrir à mes proches », près de vous. " +
                        "Il choisit de recevoir, et son téléphone vérifie le fichier avant d'installer.",
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Initiales(nom: String) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(couleurDe(nom)), contentAlignment = Alignment.Center) {
        BasicText(nom.take(1).uppercase(), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White))
    }
}

/** Une appli qu'un proche nous envoie (maquette i6-maj-voisin) : on choisit de la recevoir ; Sugu vérifie avant d'installer. */
@Composable
private fun PropositionRecue(aller: (Vue) -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val p = Echange.propositions.firstOrNull() ?: return
    val o = p.offre
    val maj = Verification.versionInstallee(c, o.fiche.paquet) != null
    DialogueAppli(
        "${p.de.nom} vous envoie ${o.nom}",
        "${if (maj) "Une mise à jour" else "Une appli"} vérifiée par ${Editeurs.nomCourt(o.fiche.editeur)}, de téléphone à téléphone : rien ne passe par Internet. " +
            "Sugu vérifie le fichier avant d'installer.",
        fermer = { Echange.refuser(c, p) },
        contenu = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconeAppli(o, o.fiche.paquet, o.nom, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    BasicText(o.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre))
                    BasicText("${taille(o.fiche.taille)} · version ${o.fiche.versionNom}", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
                }
            }
        },
    ) {
        BoutonTexteAppli("Non merci", style = 's') { Echange.refuser(c, p) }
        Spacer(Modifier.width(8.dp))
        BoutonTexteAppli("Recevoir") {
            Echange.accepter(c, p)
            aller(Vue.Fiche(o.fiche.paquet))
        }
    }
}

// ——— Mes applis ———

@Composable
private fun PageMesApplis(aller: (Vue) -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    var liste by remember { mutableStateOf<List<Installee>>(emptyList()) }
    LaunchedEffect(Installations.fins) { liste = withContext(Dispatchers.IO) { Catalogue.installees(c) } }
    val sugu = liste.filter { it.parSugu }
    // Les applis de Sama ne sont pas « d'ailleurs » (sur l'émulateur, elles sont posées à la main, mais signées
    // comme le système).
    val ailleurs = liste.filter { !it.parSugu && !it.systeme && !it.sama }
    EcranAppli {
        Tete("Mes applis")
        LazyColumn(Modifier.fillMaxSize()) {
            item { Rub("Installées par Sugu") }
            if (sugu.isEmpty()) item { Note("Aucune pour l'instant.") }
            items(sugu, key = { "s" + it.paquet }) { i ->
                val o = Catalogue.offre(i.paquet)
                LigneAppli(
                    i.nom, second = "Version ${i.versionNom}" + if (o != null && o.fiche.version > i.version) " · mise à jour possible" else "",
                    debut = { IconeAppli(o, i.paquet, i.nom, 44.dp) },
                ) { aller(Vue.Fiche(i.paquet)) }
            }
            if (ailleurs.isNotEmpty()) {
                item { Rub("Venues d'ailleurs") }
                item { Note("Elles n'ont pas été vérifiées par Sugu. Gardez seulement celles que vous connaissez.") }
                items(ailleurs, key = { "a" + it.paquet }) { i ->
                    LigneAppli(i.nom, second = "Hors Sugu · version ${i.versionNom}", couleurSecond = a.accentTexte, debut = { IconeAppli(null, i.paquet, i.nom, 44.dp) }) { infos(c, i.paquet) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun infos(c: android.content.Context, paquet: String) {
    try {
        c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", paquet, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

/** Le fichier refusé (maquette i6-verif-echec) : supprimé, rien d'installé ; on peut ne plus rien recevoir de sa source. */
@Composable
private fun Refus() {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val e = Installations.etats.values.firstOrNull { it.etape == Installations.Etape.REFUSEE } ?: return
    var bloquer by remember(e) { mutableStateOf(false) }
    val raison = when {
        !Verification.signature(c, e.fiche) -> "n'est pas signé par Sama ni par Sugu"
        e.intact == false -> "n'est pas celui que ${Editeurs.nomCourt(e.fiche.editeur)} a publié"
        else -> "n'est pas plus récent que celui du téléphone"
    }
    val proche = e.point.proche != null
    fun fermer() {
        if (bloquer && !proche) {
            Partage.reglerBloque(c, e.point.adresse, true)
            Catalogue.points.remove(e.point.adresse)
        }
        Installations.etats.remove(e.fiche.sha256)
    }
    DialogueAppli(
        "Fichier refusé",
        "${e.fiche.nom} reçu ${if (proche) "du téléphone ${Cercle.de(e.point.nom)}" else "de ${e.point.nom}"} $raison. Sugu l'a supprimé : rien n'a été installé.",
        fermer = { fermer() },
        contenu = {
            if (!proche) Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { bloquer = !bloquer }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText("Ne plus rien recevoir de ce point", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre))
                    BasicText(e.point.nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
                }
                Spacer(Modifier.width(12.dp))
                InterAppli(bloquer)
            }
        },
    ) { BoutonTexteAppli("Compris") { fermer() } }
}
