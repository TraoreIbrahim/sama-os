package africa.samaos.messages

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceSim
import africa.samaos.banco.appli.Visage
import africa.samaos.banco.appli.appui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun jour(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

private fun titreJour(d: LocalDate): String {
    val auj = LocalDate.now()
    return when (d) {
        auj -> "Aujourd'hui"
        auj.minusDays(1) -> "Hier"
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == auj.year) "EEEE d MMMM" else "EEEE d MMMM yyyy", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
    }
}

private fun heure(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
internal fun PageFil(fil: Long, adresse: String, version: Int, retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var messages by remember { mutableStateOf(emptyList<Sms>()) }
    var qui by remember { mutableStateOf<Correspondant?>(null) }
    var charge by remember { mutableStateOf(false) }
    // Le bouclier : le dernier message suspect de la conversation, et le vrai solde pour comparer.
    var alerte by remember { mutableStateOf<Triple<Long, africa.samaos.bouclier.Verdict, String?>?>(null) }
    var bloque by remember { mutableStateOf(false) }
    // Les alertes du bouclier d'un proche dont la signature est bonne.
    var verifiees by remember { mutableStateOf(emptyMap<Long, String>()) }
    LaunchedEffect(fil, version) {
        withContext(Dispatchers.IO) {
            qui = Boite.correspondant(c, adresse)
            messages = Boite.messages(c, fil)
        }
        charge = true
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
    val service = qui == null && Boite.estService(adresse)
    val etat = rememberLazyListState()
    // Les éléments du fil : un séparateur à chaque nouveau jour, puis les messages (groupés quand ils se suivent).
    val elements = remember(messages) {
        val l = mutableListOf<Any>()
        messages.forEachIndexed { i, m ->
            val avant = messages.getOrNull(i - 1)
            val apres = messages.getOrNull(i + 1)
            if (avant == null || jour(avant.date) != jour(m.date)) l += jour(m.date)
            fun suit(x: Sms?, y: Sms?) = x != null && y != null && x.recu == y.recu && jour(x.date) == jour(y.date) && kotlin.math.abs(y.date - x.date) < 5 * 60_000
            l += Triple(m, !suit(avant, m), !suit(m, apres))
        }
        l
    }
    LaunchedEffect(elements.size) { if (elements.isNotEmpty()) etat.animateScrollToItem(elements.size + 1) }
    Column(Modifier.fillMaxSize().imePadding()) {
        EnTeteFil(adresse, qui, service, alerte != null, retour)
        LazyColumn(Modifier.weight(1f), state = etat, contentPadding = PaddingValues(bottom = 12.dp)) {
            if (charge && qui == null && !service) {
                item(key = "inconnu") {
                    BanniereInconnu(adresse, bloque, Modifier.animateItem(), bloquer = { if (Boite.bloquer(c, adresse)) bloque = true }) {
                        c.startActivity(Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE, adresse).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            elements.forEach { e ->
                if (e is LocalDate) {
                    item(key = "j$e") {
                        BasicText(titreJour(e), modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp).animateItem(), style = texte(12.5f, a.encre2, FontWeight.SemiBold).copy(textAlign = TextAlign.Center))
                    }
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val t = e as Triple<Sms, Boolean, Boolean>
                    val (m, premier, dernier) = t
                    item(key = m.id) {
                        Column(Modifier.animateItem()) {
                            Bulle(m, premier, dernier, verifiees[m.id]) {
                                Boite.supprimerMessage(c, m.id)
                                Boite.envoyer(c, adresse, m.corps, null)
                            }
                            alerte?.takeIf { it.first == m.id }?.let { (_, v, solde) ->
                                AlerteArnaque(v, solde, bloque, bloquer = { if (Boite.bloquer(c, adresse)) bloque = true }) {
                                    africa.samaos.bouclier.Bouclier.noter(c, "signalement", "Numéro signalé", Boite.formater(adresse))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (service && Boite.formater(adresse).any { it.isLetter() }) {
            BasicText(
                "On ne répond pas à cet expéditeur : il n'a pas de numéro.",
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                style = texte(14f, a.encre2).copy(textAlign = TextAlign.Center),
            )
        } else {
            Composer { t, sim -> Boite.envoyer(c, adresse, t, sim) }
        }
    }
}

/** Le haut d'une conversation : qui c'est, d'où ça vient, et l'appeler. */
@Composable
private fun EnTeteFil(adresse: String, qui: Correspondant?, service: Boolean, suspect: Boolean, retour: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val rouge = if (a.sombre) Color(0xFFF08A70) else ARNAQUE
    Column(Modifier.fillMaxWidth().background(a.fond).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            BoutonAppli("M15 6l-6 6l6 6", "Retour", onClick = retour)
            when {
                suspect && qui == null -> Box(Modifier.size(42.dp).clip(CircleShape).background(rouge.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { IconeTrait(Icones.ALERTE, 20.dp, rouge) }
                service -> Box(Modifier.size(42.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) {
                    BasicText(adresse.filter { it.isLetterOrDigit() }.take(2).uppercase(), style = texte(14f, a.accentTexte, FontWeight.Bold))
                }
                else -> Visage(qui?.nom, qui?.photo, 42.dp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                BasicText(qui?.nom ?: Boite.formater(adresse), maxLines = 1, overflow = TextOverflow.Ellipsis, style = texte(17f, a.encre, FontWeight.SemiBold))
                val op = Boite.operateurDe(adresse)
                BasicText(
                    when {
                        qui != null -> listOfNotNull(Boite.formater(adresse), op).joinToString(" · ")
                        service -> "Opérateur ou service"
                        else -> listOfNotNull("Pas dans vos contacts", op).joinToString(" · ")
                    },
                    style = texte(13f, if (suspect && qui == null) rouge else a.encre2),
                )
            }
            if (!service) BoutonAppli(Icones.APPEL, "Appeler") { c.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", adresse, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(a.trait))
    }
}

/** Un numéro inconnu : ce qu'il faut savoir avant de répondre, et l'ajouter ou le bloquer d'un toucher. */
@Composable
private fun BanniereInconnu(adresse: String, bloque: Boolean, modifier: Modifier, bloquer: () -> Unit, ajouter: () -> Unit) {
    val a = LocalIdentite.current
    val rouge = if (a.sombre) Color(0xFFF08A70) else ARNAQUE
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(a.surface).border(1.dp, a.trait, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(Icones.BOUCLIER, 18.dp, a.accentTexte) }
            BasicText("${Boite.formater(adresse)} n'est pas dans vos contacts", modifier = Modifier.padding(start = 12.dp), style = texte(15f, a.encre, FontWeight.SemiBold))
        }
        BasicText("Ne donnez jamais un code reçu par SMS, même à quelqu'un qui dit être de votre opérateur ou de votre banque.", style = texte(14f, a.encre2))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pilule("Ajouter aux contacts", a.voile, a.accentTexte, onClick = ajouter)
            if (bloque) Pilule("Numéro bloqué", a.champ, a.encre2, actif = false) {}
            else Pilule("Bloquer", rouge.copy(alpha = 0.12f), rouge, onClick = bloquer)
        }
    }
}

@Composable
private fun Pilule(texte: String, fond: Color, encre: Color, actif: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.appui(texte, actif, onClick = onClick).clip(RoundedCornerShape(18.dp)).background(fond).padding(horizontal = 14.dp, vertical = 9.dp)) {
        BasicText(texte, style = texte(14f, encre, FontWeight.SemiBold))
    }
}

/** La mise en garde du bouclier, sous le message qui imite un opérateur. */
@Composable
private fun AlerteArnaque(v: africa.samaos.bouclier.Verdict, solde: String?, bloque: Boolean, bloquer: () -> Unit, signaler: () -> Unit) {
    val a = LocalIdentite.current
    var signale by remember { mutableStateOf(false) }
    val rouge = if (a.sombre) Color(0xFFF08A70) else ARNAQUE
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(rouge.copy(alpha = 0.1f))
            .border(1.dp, rouge.copy(alpha = 0.25f), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconeTrait("M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6z M12 8v5 M12 16h.01", 22.dp, rouge)
            BasicText(v.titre, style = texte(17f, rouge, FontWeight.Bold))
        }
        BasicText(v.raison, style = texte(15f, a.encre))
        if (solde != null) BasicText(solde, style = texte(15f, a.encre, FontWeight.SemiBold))
        BasicText("N'envoyez rien et ne rappelez pas ce numéro.", style = texte(14f, a.encre2))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bloque) Pilule("Numéro bloqué", a.champ, a.encre2, actif = false) {} else Pilule("Bloquer ce numéro", rouge, Color.White, onClick = bloquer)
            if (!signale) Pilule("Signaler", rouge.copy(alpha = 0.14f), rouge) {
                signaler()
                signale = true
            }
        }
        AnimatedVisibility(signale) {
            BasicText(
                "Noté sur ce téléphone. Le signalement anonyme (le numéro et le type d'arnaque, rien d'autre) partira quand le service de Sama sera ouvert.",
                style = texte(13f, a.encre2),
            )
        }
    }
}

/**
 * Une bulle : les messages qui se suivent se collent (coins resserrés), l'heure ne s'écrit que sous le dernier.
 * Un envoi raté se renvoie d'un toucher.
 */
@Composable
private fun Bulle(m: Sms, premier: Boolean, dernier: Boolean, alerteDe: String?, renvoyer: () -> Unit) {
    val a = LocalIdentite.current
    // Une alerte du bouclier d'un proche : sans son code technique, avec la mention « vérifiée ».
    val alerte = m.recu && africa.samaos.proches.Veille.lire(m.corps) != null
    val texteBulle = if (alerte) m.corps.replace(Regex("\\s*#SA1\\.\\S+\\s*$"), "") else m.corps
    val rate = m.etat == Telephony.Sms.MESSAGE_TYPE_FAILED
    val envoi = m.etat == Telephony.Sms.MESSAGE_TYPE_OUTBOX || m.etat == Telephony.Sms.MESSAGE_TYPE_QUEUED
    val grand = 20.dp
    val petit = 6.dp
    val forme = if (m.recu) RoundedCornerShape(if (premier) grand else petit, grand, grand, if (dernier) grand else petit)
    else RoundedCornerShape(grand, if (premier) grand else petit, if (dernier) grand else petit, grand)
    val recue = if (a.sombre) a.surface else Color.White
    val envoyee = Brush.verticalGradient(listOf(lerp(a.accent, Color.White, if (a.sombre) 0.05f else 0.12f), a.accent))
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = if (premier) 8.dp else 2.dp),
        horizontalAlignment = if (m.recu) Alignment.Start else Alignment.End,
    ) {
        Column(
            Modifier.widthIn(max = 296.dp).clip(forme)
                .then(if (m.recu) Modifier.background(recue).border(1.dp, a.trait, forme) else Modifier.background(envoyee))
                .then(if (rate) Modifier.clickable(onClickLabel = "Renvoyer", role = Role.Button, onClick = renvoyer) else Modifier)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            if (alerte && alerteDe != null) {
                Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconeTrait(Icones.BOUCLIER, 16.dp, a.accentTexte)
                    BasicText("Alerte vérifiée · bouclier ${if (alerteDe.first().lowercaseChar() in "aeiouyh") "d'" else "de "}$alerteDe", style = texte(13f, a.accentTexte, FontWeight.SemiBold))
                }
            }
            BasicText(texteBulle, style = texte(16f, if (m.recu) a.encre else a.surAccent).copy(lineHeight = 22.sp))
        }
        if (dernier || rate || envoi) {
            val rouge = if (a.sombre) Color(0xFFF08A70) else ARNAQUE
            BasicText(
                when {
                    rate -> "Pas envoyé · touchez pour réessayer"
                    envoi -> "Envoi…"
                    else -> heure(m.date) + if (!m.recu) " · envoyé" else ""
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = texte(11.5f, if (rate) rouge else a.encre2, if (rate) FontWeight.SemiBold else FontWeight.Normal),
            )
        }
    }
}

/** La zone d'écriture : la SIM qui envoie, le message, et Envoyer qui apparaît dès qu'il y a du texte. */
@Composable
internal fun Composer(texteInitial: String? = null, envoyer: (String, SimSms?) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val sims = remember { Boite.sims(c) }
    var sim by remember { mutableStateOf(sims.firstOrNull()) }
    var texte by remember { mutableStateOf(texteInitial.orEmpty()) }
    // Un SMS long part en plusieurs morceaux, payés chacun : on le dit avant d'envoyer.
    val parts = remember(texte) { if (texte.isBlank()) null else android.telephony.SmsMessage.calculateLength(texte, false) }
    Column(Modifier.fillMaxWidth().background(a.fond).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
        AnimatedVisibility(parts != null && (parts[0] > 1 || parts[2] < 20)) {
            val p = parts ?: intArrayOf(1, 0, 0, 1)
            BasicText(
                "${p[0]} SMS · encore ${p[2]} caractère${if (p[2] > 1) "s" else ""} avant le ${p[0] + 1}ᵉ",
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                style = texte(12.5f, if (p[0] > 1) a.accentTexte else a.encre2, FontWeight.SemiBold),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sims.size > 1) {
                Box(
                    Modifier.appui("Envoyer avec une autre SIM") { sim = sims[(sims.indexOf(sim) + 1) % sims.size] }.size(48.dp).clip(CircleShape).background(a.champ),
                    contentAlignment = Alignment.Center,
                ) { sim?.let { PuceSim(it.fente, it.operateur) } }
            }
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(if (a.sombre) a.surface else Color.White)
                    .border(1.dp, a.trait, RoundedCornerShape(24.dp)).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicTextField(
                    value = texte,
                    onValueChange = { texte = it },
                    textStyle = texte(16f, a.encre),
                    cursorBrush = SolidColor(a.accent),
                    maxLines = 6,
                    modifier = Modifier.weight(1f).padding(vertical = 9.dp).semantics { contentDescription = "Message" },
                    decorationBox = { champ ->
                        Box {
                            if (texte.isEmpty()) BasicText(if (sims.size > 1 && sim != null) "Message · SIM ${sim?.fente} ${sim?.operateur}" else "Message", maxLines = 1, style = texte(16f, a.encre2))
                            champ()
                        }
                    },
                )
                AnimatedVisibility(texte.isNotBlank(), enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 700f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Box(
                        Modifier.appui("Envoyer") {
                            envoyer(texte.trim(), sim)
                            texte = ""
                        }.size(40.dp).clip(CircleShape).background(a.accent),
                        contentAlignment = Alignment.Center,
                    ) { IconeTrait(Icones.ENVOYER, 18.dp, a.surAccent, epaisseur = 2f) }
                }
            }
        }
    }
}
