package africa.samaos.contacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Avatar
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.CaseAppli
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Fab
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.PuceSim
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Vue {
    data object Liste : Vue
    data class Fiche(val id: Long) : Vue
    data class Modifier(val id: Long?, val numero: String? = null, val nom: String? = null) : Vue
    data object Import : Vue
}

/** Les Contacts de Sama (maquettes l2-con). */
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
            CompositionLocalProvider(LocalIdentite provides id) {
                val aller: (Vue) -> Unit = { pile = pile + it }
                val retour: () -> Unit = { if (pile.size > 1) pile = pile.dropLast(1) else finish() }
                BackHandler(enabled = pile.size > 1) { retour() }
                when (val v = pile.last()) {
                    Vue.Liste -> PageListe(reprise, aller)
                    is Vue.Fiche -> PageFiche(v.id, reprise, aller, retour)
                    is Vue.Modifier -> PageModifier(v.id, v.numero, v.nom, retour) { nouvel ->
                        pile = pile.dropLast(1).let { p -> if (p.lastOrNull() is Vue.Fiche || nouvel == null) p else p + Vue.Fiche(nouvel) }
                    }
                    Vue.Import -> PageImport(retour)
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

// ——— Liste (maquette l2-con-liste) ———

@Composable
private fun PageListe(reprise: Int, aller: (Vue) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }
    var recherche by remember { mutableStateOf("") }
    LaunchedEffect(reprise) { contacts = withContext(Dispatchers.IO) { Carnet.liste(c) } }
    val vus = if (recherche.isBlank()) contacts else contacts.filter { simplifier(it.nom).contains(simplifier(recherche)) }
    val etat = rememberLazyListState()
    val portee = rememberCoroutineScope()
    // La position de chaque lettre dans la liste, pour l'index sur le côté.
    val positions = remember(vus) {
        val m = LinkedHashMap<String, Int>()
        var i = 0
        var derniere = ""
        vus.forEach { ct ->
            val l = Carnet.lettre(ct.nom)
            if (l != derniere) {
                m[l] = i
                i++
                derniere = l
            }
            i++
        }
        m
    }
    Box(Modifier.fillMaxSize()) {
        EcranAppli {
            Tete("Contacts") {
                BoutonAppli(Icones.SIM, "Importer depuis la SIM") { aller(Vue.Import) }
            }
            ChampAppli(recherche, "Rechercher parmi ${contacts.size} contacts", { recherche = it })
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = etat) {
                    var derniere = ""
                    vus.forEach { ct ->
                        val l = Carnet.lettre(ct.nom)
                        if (l != derniere) {
                            derniere = l
                            item(key = "l$l") { Rub(l) }
                        }
                        item(key = ct.id) {
                            LigneAppli(ct.nom, debut = { Avatar(ct.nom) }, fin = { if (ct.favori) IconeTrait("M12 3l2.7 5.6l6.1.9l-4.4 4.3l1 6.1L12 17l-5.4 2.9l1-6.1l-4.4-4.3l6.1-.9z", 18.dp, a.accentTexte) }) {
                                aller(Vue.Fiche(ct.id))
                            }
                        }
                    }
                    item(key = "bas") { Spacer(Modifier.height(120.dp)) }
                }
                if (recherche.isBlank() && positions.size > 3) {
                    IndexLettres(positions.keys.toList()) { l -> positions[l]?.let { portee.launch { etat.scrollToItem(it) } } }
                }
                if (contacts.isEmpty()) {
                    BasicText(
                        "Aucun contact pour l'instant. Ajoutez-en un, ou importez ceux de la SIM.",
                        modifier = Modifier.padding(20.dp),
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding()) {
            Box(Modifier.size(240.dp, 100.dp)) { Fab("Nouveau contact", Icones.PLUS) { aller(Vue.Modifier(null)) } }
        }
    }
}

/** L'index des lettres, sur le bord droit : on y glisse le doigt pour sauter. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.IndexLettres(lettres: List<String>, aller: (String) -> Unit) {
    val a = LocalIdentite.current
    var hauteur by remember { mutableIntStateOf(1) }
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 4.dp)
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
                modifier = Modifier.clip(CircleShape).clickable(onClickLabel = "Aller à $l") { aller(l) }.padding(horizontal = 6.dp, vertical = 1.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = a.accentTexte),
            )
        }
    }
}

fun simplifier(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

// ——— Fiche (maquette l2-con-fiche) ———

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
    EcranAppli {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BoutonAppli("M15 6l-6 6l6 6", "Retour", onClick = retour)
            Spacer(Modifier.weight(1f))
            BoutonAppli(
                if (f.favori) "M12 3l2.7 5.6l6.1.9l-4.4 4.3l1 6.1L12 17l-5.4 2.9l1-6.1l-4.4-4.3l6.1-.9z" else "M12 3l2.7 5.6l6.1.9l-4.4 4.3l1 6.1L12 17l-5.4 2.9l1-6.1l-4.4-4.3l6.1-.9z",
                if (f.favori) "Retirer des favoris" else "Ajouter aux favoris",
                style = if (f.favori) 'v' else ' ',
            ) {
                Carnet.favori(c, f.id, !f.favori)
                version++
            }
            BoutonAppli(Icones.CRAYON, "Modifier") { aller(Vue.Modifier(f.id)) }
            BoutonAppli(Icones.CORBEILLE, "Supprimer") { supprimer = true }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(f.nom, 112.dp)
                BasicText(f.nom, modifier = Modifier.padding(top = 14.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, color = a.encre, textAlign = TextAlign.Center))
                BasicText(
                    if (f.compte.isNullOrBlank() || f.compte == "Local") "Sur ce téléphone" else f.compte!!,
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2),
                )
            }
            if (supprimer) {
                Column(Modifier.padding(20.dp).clip(RoundedCornerShape(20.dp)).background(a.surface).padding(16.dp)) {
                    BasicText("Supprimer ${f.nom} de vos contacts ?", style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        BoutonTexteAppli("Annuler", style = 't') { supprimer = false }
                        BoutonTexteAppli("Supprimer") {
                            Carnet.supprimer(c, f.id)
                            retour()
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionFiche("Appeler", Icones.APPEL, principal != null) { principal?.let { c.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", it.numero, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                ActionFiche("Message", Icones.MESSAGE, principal != null) { principal?.let { c.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", it.numero, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                ActionFiche("Partager", "M12 4v12 M7 9l5-5l5 5 M5 14v5h14v-5", true) {
                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, f.cle)
                    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Partager ${f.nom}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            f.numeros.forEach { n ->
                val op = Carnet.operateur(n.numero)
                val sim = sims.firstOrNull { op != null && it.second.contains(op, ignoreCase = true) }
                LigneAppli(
                    Carnet.formater(n.numero),
                    listOfNotNull(Carnet.nomType(n.type), op).joinToString(" · "),
                    debut = { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { IconeTrait(Icones.APPEL, 22.dp, a.accentTexte) } },
                    fin = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (sim != null) PuceSim(sim.first, sim.second)
                            if (n.prefere) BasicText("Préféré", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                            BoutonAppli(Icones.MESSAGE, "Message", style = 'v') { c.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", n.numero, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    },
                ) { c.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", n.numero, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            f.emails.forEach { e ->
                LigneAppli(e, "E-mail", debut = { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { IconeTrait("M4 6h16v12H4z M4 7l8 6l8-6", 22.dp, a.accentTexte) } }) {
                    c.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", e, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            f.anniversaire?.let { d ->
                LigneAppli(dateLisible(d), "Anniversaire", debut = { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { IconeTrait(Icones.HORLOGE, 22.dp, a.accentTexte) } })
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun dateLisible(d: String): String = try {
    val p = d.removePrefix("--").split("-")
    val (m, j) = if (p.size == 3) p[1].toInt() to p[2].toInt() else p[0].toInt() to p[1].toInt()
    "$j ${java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)}"
} catch (_: Exception) {
    d
}

@Composable
private fun ActionFiche(nom: String, icone: String, actif: Boolean, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(a.voile).clickable(enabled = actif, onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(icone, 24.dp, if (actif) a.accentTexte else a.encre2) }
        BasicText(nom, modifier = Modifier.padding(top = 6.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.encre))
    }
}

// ——— Créer ou modifier (maquette l2-con-modifier) ———

@Composable
private fun PageModifier(id: Long?, numeroDonne: String?, nomDonne: String?, retour: () -> Unit, fini: (Long?) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var prenom by remember { mutableStateOf(nomDonne?.substringBefore(' ').orEmpty()) }
    var famille by remember { mutableStateOf(nomDonne?.substringAfter(' ', "").orEmpty()) }
    val numeros = remember { mutableStateListOf(Pair(numeroDonne.orEmpty(), Carnet.TYPES.first())) }
    var email by remember { mutableStateOf("") }
    var brut by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(id) {
        if (id != null) {
            withContext(Dispatchers.IO) { Carnet.fiche(c, id) }?.let { f ->
                prenom = f.prenom.ifBlank { f.nom }
                famille = f.nomFamille
                numeros.clear()
                f.numeros.forEach { numeros += it.numero to it.type }
                if (numeros.isEmpty()) numeros += "" to Carnet.TYPES.first()
                email = f.emails.firstOrNull().orEmpty()
                brut = f.brut
            }
        }
    }
    val sims = remember { Carnet.operateursDesSim(c) }
    val peut = (prenom + famille).isNotBlank() && numeros.any { it.first.isNotBlank() }
    EcranAppli {
        Tete(if (id == null) "Nouveau contact" else "Modifier", retour = retour, petit = true) {
            BoutonTexteAppli("Enregistrer", style = 't', actif = peut) {
                val nouvel = Carnet.enregistrer(c, brut, prenom, famille, numeros.toList(), email)
                fini(nouvel ?: id)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Avatar("$prenom $famille".trim().ifBlank { null }, 96.dp)
            }
            ChampFiche("Prénom", prenom) { prenom = it }
            ChampFiche("Nom", famille) { famille = it }
            numeros.forEachIndexed { i, (n, t) ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Carnet.TYPES.forEach { ty -> PuceFiltre(Carnet.nomType(ty), ty == t) { numeros[i] = n to ty } }
                }
                ChampFiche("Téléphone", n, KeyboardType.Phone) { numeros[i] = it.filter { ch -> ch.isDigit() || ch in "+ " } to t }
                val op = Carnet.operateur(n)
                if (op != null && op != "Fixe" && sims.isNotEmpty() && sims.none { it.second.contains(op, ignoreCase = true) }) {
                    BasicText("Numéro $op : aucune de vos SIM n'est $op.", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                }
            }
            BoutonTexteAppli("Ajouter un numéro", style = 't', icone = Icones.PLUS) { numeros += "" to Carnet.TYPES.first() }
            ChampFiche("E-mail (facultatif)", email, KeyboardType.Email) { email = it }
            BasicText(
                "Le contact reste sur ce téléphone, dans cet Espace.",
                modifier = Modifier.padding(top = 6.dp, bottom = 40.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2),
            )
        }
    }
}

@Composable
private fun ChampFiche(nom: String, valeur: String, clavier: KeyboardType = KeyboardType.Text, changer: (String) -> Unit) {
    val a = LocalIdentite.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(a.champ).padding(horizontal = 16.dp, vertical = 10.dp)) {
        BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = a.encre2))
        BasicTextField(
            value = valeur,
            onValueChange = changer,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = clavier),
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre),
            cursorBrush = SolidColor(a.accent),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).semantics { contentDescription = nom },
        )
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
        Tete("Importer depuis la SIM", retour = retour, petit = true)
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
