package africa.samaos.messages

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Avatar
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Fab
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceSim
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface Vue {
    data object Liste : Vue
    data object Services : Vue
    data class Fil(val fil: Long, val adresse: String) : Vue
    data class Nouveau(val texte: String? = null) : Vue
}

/** Les Messages de Sama (maquettes l2-msg). */
class Messages : ComponentActivity() {
    private var pile by mutableStateOf(listOf<Vue>(Vue.Liste))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Boite.canaux(this)
        lire(intent)
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.MessagesNuit else Identites.Messages
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            CompositionLocalProvider(LocalIdentite provides id) {
                val version = rememberBoite()
                val aller: (Vue) -> Unit = { pile = pile + it }
                val retour: () -> Unit = { if (pile.size > 1) pile = pile.dropLast(1) else finish() }
                BackHandler(enabled = pile.size > 1) { retour() }
                when (val v = pile.last()) {
                    Vue.Liste -> PageListe(version, services = false, aller = aller, retour = null)
                    Vue.Services -> PageListe(version, services = true, aller = aller, retour = retour)
                    is Vue.Fil -> PageFil(v.fil, v.adresse, version, retour)
                    is Vue.Nouveau -> PageNouveau(v.texte, retour) { fil, adresse -> pile = pile.dropLast(1) + Vue.Fil(fil, adresse) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    /** « Écrire un SMS à … », ou une notification : la conversation ; un texte partagé : un nouveau message. */
    private fun lire(i: Intent?) {
        val fil = i?.getLongExtra("fil", -1) ?: -1
        val adresse = i?.getStringExtra("adresse") ?: i?.data?.schemeSpecificPart?.substringBefore('?')?.takeIf { it.isNotBlank() }
        when {
            fil >= 0 && adresse != null -> pile = listOf(Vue.Liste, Vue.Fil(fil, adresse))
            adresse != null -> pile = listOf(Vue.Liste, Vue.Fil(Boite.filDe(this, adresse), adresse))
            i?.action == Intent.ACTION_SEND -> pile = listOf(Vue.Liste, Vue.Nouveau(i.getStringExtra(Intent.EXTRA_TEXT)))
        }
    }
}

/** Change à chaque SMS reçu ou envoyé : les pages se relisent. */
@Composable
private fun rememberBoite(): Int {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                version++
            }
        }
        c.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
        onDispose { c.contentResolver.unregisterContentObserver(obs) }
    }
    return version
}

// ——— Conversations (maquette l2-msg-liste) ———

@Composable
private fun PageListe(version: Int, services: Boolean, aller: (Vue) -> Unit, retour: (() -> Unit)?) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var liste by remember { mutableStateOf(emptyList<Conversation>()) }
    LaunchedEffect(version) { liste = withContext(Dispatchers.IO) { Boite.conversations(c) } }
    val (srv, gens) = liste.partition { it.service }
    Box(Modifier.fillMaxSize()) {
        EcranAppli {
            Tete(if (services) "Opérateurs et services" else "Messages", retour = retour, petit = services)
            LazyColumn(Modifier.fillMaxSize()) {
                if (!services && srv.isNotEmpty()) {
                    item(key = "services") {
                        val d = srv.first()
                        LigneDeConversation(
                            "Opérateurs et services",
                            "${Boite.formater(d.adresse)} : ${d.dernier}",
                            d.date,
                            srv.sumOf { it.nonLus },
                            debut = { Box(Modifier.size(44.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(Icones.DOSSIER, 22.dp, a.accentTexte) } },
                        ) { aller(Vue.Services) }
                    }
                }
                items(if (services) srv else gens, key = { it.fil }) { cv ->
                    LigneDeConversation(cv.nom ?: Boite.formater(cv.adresse), cv.dernier, cv.date, cv.nonLus, debut = { Avatar(cv.nom ?: if (cv.service) cv.adresse else null) }) {
                        aller(Vue.Fil(cv.fil, cv.adresse))
                    }
                }
                if (liste.isEmpty()) item(key = "vide") {
                    BasicText("Aucun message.", modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
                }
                item(key = "bas") { Spacer(Modifier.height(120.dp)) }
            }
        }
        if (!services) {
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding()) {
                Box(Modifier.size(200.dp, 100.dp)) { Fab("Nouveau", Icones.CRAYON) { aller(Vue.Nouveau()) } }
            }
        }
    }
}

@Composable
private fun LigneDeConversation(nom: String, dernier: String, date: Long, nonLus: Int, debut: @Composable () -> Unit, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = nom, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        debut()
        Column(Modifier.weight(1f)) {
            BasicText(nom, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = if (nonLus > 0) FontWeight.Bold else FontWeight.Medium, fontSize = 17.sp, color = a.encre))
            BasicText(dernier.replace(Regex("\\s*#SA1\\.\\S+\\s*$"), "").replace('\n', ' '), maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = if (nonLus > 0) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp, color = if (nonLus > 0) a.encre else a.encre2))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(Boite.quand(date), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = if (nonLus > 0) a.accentTexte else a.encre2))
            if (nonLus > 0) {
                Box(Modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(CircleShape).background(a.accent).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    BasicText("$nonLus", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = a.surAccent))
                }
            }
        }
    }
}

// ——— Une conversation ———

@Composable
private fun PageFil(fil: Long, adresse: String, version: Int, retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var messages by remember { mutableStateOf(emptyList<Sms>()) }
    val nom = remember(adresse) { Boite.nomDe(c, adresse) }
    val service = nom == null && Boite.estService(adresse)
    // Le bouclier : le dernier message suspect de la conversation, et le vrai solde pour comparer.
    var alerte by remember { mutableStateOf<Triple<Long, africa.samaos.bouclier.Verdict, String?>?>(null) }
    var bloque by remember { mutableStateOf(false) }
    // Les alertes du bouclier d'un proche dont la signature est bonne.
    var verifiees by remember { mutableStateOf(emptyMap<Long, String>()) }
    LaunchedEffect(fil, version) {
        messages = withContext(Dispatchers.IO) { Boite.messages(c, fil) }
        verifiees = withContext(Dispatchers.IO) {
            val proches = lazy { africa.samaos.proches.Cercle.reconnus(c).associate { it.id to it.nom } }
            messages.filter { it.recu }.mapNotNull { m ->
                africa.samaos.proches.Veille.lire(m.corps)?.let { r -> africa.samaos.proches.Veille.auteur(c, r)?.let { id -> m.id to (proches.value[id] ?: "un proche") } }
            }.toMap()
        }
        alerte = withContext(Dispatchers.IO) {
            val suspects = messages.filter { it.recu }.mapNotNull { m -> Boite.verdict(c, adresse, m.corps)?.let { m to it } }
            suspects.lastOrNull()?.let { (m, dernier) ->
                // La raison la plus parlante : un message qui imite un opérateur, s'il y en a un.
                val v = suspects.firstOrNull { it.second.operateur != null }?.second ?: dernier
                val op = v.operateur ?: messages.firstNotNullOfOrNull { x -> africa.samaos.bouclier.Bouclier.operateurCite(x.corps) }
                val de = op?.nom?.let { n -> if (n.first().lowercaseChar() in "aeiouy") "d'$n" else "de $n" }
                Triple(m.id, v, op?.let { o -> Boite.vraiSolde(c, o)?.let { "Votre vrai solde, d'après le dernier SMS $de : $it." } })
            }
        }
        bloque = withContext(Dispatchers.IO) { Boite.estBloque(c, adresse) }
        Boite.marquerLu(c, fil)
    }
    val etat = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) etat.scrollToItem(messages.size) }
    EcranAppli(Modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            BoutonAppli("M15 6l-6 6l6 6", "Retour", onClick = retour)
            Avatar(nom ?: if (service) adresse else null, 36.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                BasicText(nom ?: Boite.formater(adresse), maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = a.encre))
                if (nom != null) BasicText(Boite.formater(adresse), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
            }
            if (!service) BoutonAppli(Icones.APPEL, "Appeler") { c.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", adresse, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            if (nom == null && !service) BoutonAppli(Icones.PERSONNE, "Ajouter aux contacts") {
                c.startActivity(Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE, adresse).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        LazyColumn(Modifier.weight(1f), state = etat, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (nom == null && !service) {
                item(key = "inconnu") {
                    BasicText(
                        "Numéro absent de vos contacts. Ne donnez jamais un code reçu par SMS, même à quelqu'un qui dit être de votre opérateur ou de votre banque.",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(a.champ).padding(14.dp),
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
                    )
                }
            }
            items(messages, key = { it.id }) { m ->
                Bulle(m, verifiees[m.id])
                alerte?.takeIf { it.first == m.id }?.let { (_, v, solde) ->
                    AlerteArnaque(v, solde, bloque, bloquer = { if (Boite.bloquer(c, adresse)) bloque = true }) {
                        africa.samaos.bouclier.Bouclier.noter(c, "signalement", "Numéro signalé", Boite.formater(adresse))
                    }
                }
            }
        }
        if (service && Boite.formater(adresse).any { it.isLetter() }) {
            BasicText(
                "On ne répond pas à cet expéditeur.",
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2),
            )
        } else {
            Composer { t, sim -> Boite.envoyer(c, adresse, t, sim) }
        }
    }
}

/** La mise en garde du bouclier (maquette i2-sms-arnaque), sous le message qui imite un opérateur. */
@Composable
private fun AlerteArnaque(v: africa.samaos.bouclier.Verdict, solde: String?, bloque: Boolean, bloquer: () -> Unit, signaler: () -> Unit) {
    val a = LocalIdentite.current
    var signale by remember { mutableStateOf(false) }
    val laterite = if (a.sombre) androidx.compose.ui.graphics.Color(0xFFEE9A78) else androidx.compose.ui.graphics.Color(0xFFB5532F)
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(laterite.copy(alpha = 0.14f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            africa.samaos.banco.IconeTrait("M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6z M12 8v5 M12 16h.01", 22.dp, laterite)
            BasicText(v.titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = laterite))
        }
        BasicText(v.raison, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre))
        if (solde != null) BasicText(solde, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre))
        BasicText("N'envoyez rien et ne rappelez pas ce numéro.", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bloque) BasicText("Numéro bloqué", modifier = Modifier.padding(vertical = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre2))
            else africa.samaos.banco.appli.BoutonTexteAppli("Bloquer ce numéro", onClick = bloquer)
            if (!signale) africa.samaos.banco.appli.BoutonTexteAppli("Signaler", style = 's') {
                signaler()
                signale = true
            }
        }
        if (signale) BasicText(
            "Noté sur ce téléphone. Le signalement anonyme (le numéro et le type d'arnaque, rien d'autre) partira quand le service de Sama sera ouvert.",
            style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = a.encre2),
        )
    }
}

@Composable
private fun Bulle(m: Sms, alerteDe: String? = null) {
    val a = LocalIdentite.current
    // Une alerte du bouclier d'un proche : sans son code technique, avec la mention « vérifiée ».
    val alerte = m.recu && africa.samaos.proches.Veille.lire(m.corps) != null
    val texte = if (alerte) m.corps.replace(Regex("\\s*#SA1\\.\\S+\\s*$"), "") else m.corps
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = if (m.recu) Arrangement.Start else Arrangement.End) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, if (m.recu) 20.dp else 6.dp, if (m.recu) 6.dp else 20.dp))
                .background(if (m.recu) a.surface else a.accent)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            if (alerte && alerteDe != null) Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconeTrait(Icones.BOUCLIER, 16.dp, a.accentTexte)
                BasicText("Alerte vérifiée · bouclier ${if (alerteDe.first().lowercaseChar() in "aeiouyh") "d'" else "de "}$alerteDe", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = a.accentTexte))
            }
            BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 22.sp, color = if (m.recu) a.encre else a.surAccent))
            val etat = when (m.etat) {
                Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> " · envoi…"
                Telephony.Sms.MESSAGE_TYPE_FAILED -> " · pas envoyé"
                else -> ""
            }
            BasicText(
                Boite.quand(m.date) + etat,
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 11.sp, color = if (m.recu) a.encre2 else a.surAccent.copy(alpha = 0.8f)),
            )
        }
    }
}

/** La zone d'écriture : la SIM qui envoie, le message, Envoyer. */
@Composable
private fun Composer(texteInitial: String? = null, envoyer: (String, SimSms?) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val sims = remember { Boite.sims(c) }
    var sim by remember { mutableStateOf(sims.firstOrNull()) }
    var texte by remember { mutableStateOf(texteInitial.orEmpty()) }
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sims.size > 1) {
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(a.champ).clickable(onClickLabel = "Changer de SIM", role = Role.Button) {
                    sim = sims[(sims.indexOf(sim) + 1) % sims.size]
                }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { sim?.let { Row(verticalAlignment = Alignment.CenterVertically) { PuceSim(it.fente, it.operateur); BasicText(" ${it.operateur}", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre)) } } }
        }
        BasicTextField(
            value = texte,
            onValueChange = { texte = it },
            textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre),
            cursorBrush = SolidColor(a.accent),
            maxLines = 5,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(a.champ).padding(horizontal = 16.dp, vertical = 13.dp).semantics { contentDescription = "Message" },
            decorationBox = { champ ->
                Box {
                    if (texte.isEmpty()) BasicText("Message", style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2))
                    champ()
                }
            },
        )
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (texte.isBlank()) a.champ else a.accent)
                .clickable(enabled = texte.isNotBlank(), onClickLabel = "Envoyer", role = Role.Button) {
                    envoyer(texte.trim(), sim)
                    texte = ""
                }
                .semantics { contentDescription = "Envoyer" },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(Icones.ENVOYER, 20.dp, if (texte.isBlank()) a.encre2 else a.surAccent) }
    }
}

// ——— Nouveau message (maquette l2-msg-nouveau) ———

private class Destinataire(val nom: String?, val numero: String)

@Composable
private fun PageNouveau(texte: String?, retour: () -> Unit, ouvrir: (Long, String) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val choisis = remember { mutableStateListOf<Destinataire>() }
    var saisie by remember { mutableStateOf("") }
    var trouves by remember { mutableStateOf(emptyList<Destinataire>()) }
    LaunchedEffect(saisie) {
        trouves = if (saisie.length < 2) emptyList() else withContext(Dispatchers.IO) {
            val l = mutableListOf<Destinataire>()
            try {
                c.contentResolver.query(
                    Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(saisie)),
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null,
                )?.use { cur -> while (cur.moveToNext() && l.size < 8) l += Destinataire(cur.getString(0), cur.getString(1).orEmpty()) }
            } catch (_: Exception) {
            }
            l.distinctBy { it.numero.filter { ch -> ch.isDigit() } }
        }
    }
    EcranAppli(Modifier.imePadding()) {
        Tete("Nouveau message", retour = retour, petit = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("À ", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre2))
            choisis.forEach { d ->
                Row(
                    Modifier.padding(end = 6.dp).clip(RoundedCornerShape(18.dp)).background(a.voile).clickable(onClickLabel = "Retirer") { choisis.remove(d) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(d.nom ?: Boite.formater(d.numero), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.accentTexte))
                    IconeTrait(Icones.FERMER, 14.dp, a.accentTexte, Modifier.padding(start = 4.dp))
                }
            }
            BasicTextField(
                value = saisie,
                onValueChange = { saisie = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre),
                cursorBrush = SolidColor(a.accent),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).padding(vertical = 12.dp).semantics { contentDescription = "Destinataire" },
            )
        }
        Column(Modifier.weight(1f)) {
            val numeroTape = saisie.filter { it.isDigit() || it == '+' }
            if (numeroTape.length >= 8 && trouves.none { it.numero.filter { ch -> ch.isDigit() }.endsWith(numeroTape.takeLast(8)) }) {
                LigneAppli(Boite.formater(numeroTape), "Envoyer à ce numéro", debut = { Avatar(null) }) {
                    choisis += Destinataire(null, numeroTape)
                    saisie = ""
                }
            }
            if (trouves.isNotEmpty()) Rub("Contacts")
            trouves.forEach { d ->
                LigneAppli(d.nom ?: d.numero, Boite.formater(d.numero), debut = { Avatar(d.nom) }) {
                    choisis += d
                    saisie = ""
                }
            }
            if (choisis.size > 1) {
                BasicText(
                    "Plusieurs destinataires : chacun recevra son propre SMS, comme s'il était seul.",
                    modifier = Modifier.padding(20.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
                )
            }
        }
        if (choisis.isNotEmpty()) {
            Composer(texte) { t, sim ->
                // Chacun reçoit son propre SMS, comme s'il était seul.
                choisis.forEach { d -> Boite.envoyer(c, d.numero, t, sim) }
                val premier = choisis.first().numero
                ouvrir(Boite.filDe(c, premier), premier)
            }
        }
    }
}
