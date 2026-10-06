package africa.samaos.accueil

import africa.samaos.banco.*
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Les notifications du Pouls (maquette l1-pouls-notifs) : regroupées par appli et par interlocuteur,
 * avec la réponse directe quand l'appli la propose. Un appui long ouvre les options : reporter,
 * rendre silencieux, régler.
 */
@Composable
fun ListeNotifications(applis: List<Appli>) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val connecte by Notifications.connecte.collectAsState()
    val liste by Notifications.liste.collectAsState()
    var options by remember { mutableStateOf<String?>(null) }
    if (!connecte) {
        CarteNotif(
            icone = { IconePlate(Icones.CLOCHE) },
            titre = "Vos notifications ici",
            legende = null,
            texte = "Autorisez le Pouls à les afficher. Touchez pour ouvrir le réglage.",
            onClick = { Systeme.demanderNotifications(contexte) },
        )
        return
    }
    if (liste.isEmpty()) {
        BasicText(
            "Aucune notification",
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = b.encre2, textAlign = TextAlign.Center),
        )
        return
    }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            "Notifications",
            modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
        )
        if (liste.any { it.effacable }) {
            BasicText(
                "Tout effacer",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button) { Notifications.toutEffacer() }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.lateriteTexte),
            )
        }
    }
    val parPaquet = remember(applis) { applis.associateBy { it.paquet } }
    // Les groupes, du plus récent au plus ancien ; dans un groupe, la plus récente notification sert de modèle.
    val groupes = liste.groupBy { it.groupe }.values.toList()
    groupes.forEach { g ->
        val tete = g.first()
        key(tete.groupe) {
            Balayable(actif = g.all { it.effacable }, effacer = { g.forEach { Notifications.effacer(it) } }) {
                CarteGroupe(
                    g = g,
                    appli = parPaquet[tete.paquet],
                    ouvert = options == tete.groupe,
                    basculer = { options = if (options == tete.groupe) null else tete.groupe },
                )
            }
        }
    }
}

@Composable
private fun CarteGroupe(g: List<Notif>, appli: Appli?, ouvert: Boolean, basculer: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val tete = g.first()
    // Les lignes de tout le groupe, des plus anciennes aux plus récentes, sans doublon.
    val lignes = g.reversed().flatMap { it.lignes }.distinct().takeLast(4)
    val forme = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forme)
            .background(b.sol2)
            .then(if (ouvert) Modifier.border(2.dp, b.laterite, forme) else Modifier)
            .combinedClickable(onClick = { Notifications.ouvrir(tete) }, onLongClick = basculer, onLongClickLabel = "Options")
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (appli != null) {
                Image(appli.icone, null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                IconePlate(Icones.MESSAGE)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    if (tete.titre.isBlank() || tete.titre == tete.appli) tete.appli else "${tete.appli} · ${tete.titre}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = b.encre),
                )
                BasicText(
                    heureCourte(tete.heure),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = b.encre2),
                )
            }
            if (lignes.size > 1) {
                Box(
                    Modifier.size(24.dp).background(b.sol, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        "${lignes.size}",
                        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = b.encre2),
                    )
                }
            }
        }
        lignes.forEach { l ->
            BasicText(
                l,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 22.sp, color = b.encre),
            )
        }
        val avecReponse = g.firstOrNull { it.reponse != null }
        if (avecReponse != null) ChampReponse(avecReponse)
        if (ouvert) {
            BasicText(
                "Reporter cette notification",
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = b.encre2),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Puce("15 min") { g.forEach { Notifications.reporter(it, 15 * MINUTE) } }
                Puce("1 heure") { g.forEach { Notifications.reporter(it, 60 * MINUTE) } }
                val (libelle, duree) = plusTard()
                Puce(libelle) { g.forEach { Notifications.reporter(it, duree) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Puce("Silencieux", Icones.SILENCIEUX) { reglerCanal(contexte, tete) }
                Puce("Réglages", Icones.REGLAGES) { reglerAppli(contexte, tete) }
            }
        }
    }
}

/** La réponse directe : un galet de saisie et le rond d'envoi. */
@Composable
private fun ChampReponse(n: Notif) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val focus = LocalFocusManager.current
    var texte by remember(n.cle) { mutableStateOf("") }
    val prenom = n.titre.substringBefore(' ').ifBlank { n.appli }
    fun envoyer() {
        if (texte.isNotBlank() && Notifications.repondre(contexte, n, texte.trim())) {
            texte = ""
            // Le message est parti : le clavier se range.
            focus.clearFocus()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(b.sol, RoundedCornerShape(50))
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = b.encre)
        BasicTextField(
            value = texte,
            onValueChange = { texte = it },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(b.laterite),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { envoyer() }),
            modifier = Modifier.weight(1f),
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (texte.isEmpty()) BasicText("Répondre à $prenom", style = style.copy(color = b.encre2))
                    champ()
                }
            },
        )
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(b.laterite)
                .clickable(enabled = texte.isNotBlank(), onClickLabel = "Envoyer", role = Role.Button) { envoyer() }
                .semantics { contentDescription = "Envoyer" },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(Icones.ENVOYER, 18.dp, b.surLaterite) }
    }
}


private const val MINUTE = 60_000L

/** « Ce soir » (19 h) dans la journée ; passé 18 h, « Demain matin » (8 h). */
private fun plusTard(): Pair<String, Long> {
    val maintenant = LocalDateTime.now()
    val cible = if (maintenant.hour < 18) maintenant.withHour(19).withMinute(0) else maintenant.plusDays(1).withHour(8).withMinute(0)
    val libelle = if (maintenant.hour < 18) "Ce soir" else "Demain matin"
    val ms = cible.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
    return libelle to ms
}

/** « Silencieux » : le réglage du canal de cette notification, où l'on choisit qu'elle n'émette plus de son. */
private fun reglerCanal(contexte: Context, n: Notif) {
    val intent = if (n.canal != null) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, n.paquet)
            .putExtra(Settings.EXTRA_CHANNEL_ID, n.canal)
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, n.paquet)
    }
    Systeme.ouvrir(contexte, intent)
}

private fun reglerAppli(contexte: Context, n: Notif) =
    Systeme.ouvrir(contexte, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, n.paquet))
