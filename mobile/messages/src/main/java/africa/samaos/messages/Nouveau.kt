package africa.samaos.messages

import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Visage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Destinataire(val nom: String?, val numero: String, val photo: String? = null)

/** Un nouveau message : à qui (contacts, récents ou un numéro tapé), puis le message. Chacun reçoit son propre SMS. */
@Composable
internal fun PageNouveau(texte: String?, retour: () -> Unit, ouvrir: (Long, String) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val choisis = remember { mutableStateListOf<Destinataire>() }
    var saisie by remember { mutableStateOf("") }
    var trouves by remember { mutableStateOf(emptyList<Destinataire>()) }
    var recents by remember { mutableStateOf(emptyList<Destinataire>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        recents = withContext(Dispatchers.IO) { Boite.conversations(c).filter { !it.service && it.nom != null }.take(8).map { Destinataire(it.nom, it.adresse, it.photo) } }
        try {
            focus.requestFocus()
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(saisie) {
        trouves = if (saisie.length < 2) emptyList() else withContext(Dispatchers.IO) {
            val l = mutableListOf<Destinataire>()
            try {
                c.contentResolver.query(
                    Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(saisie)),
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI),
                    null, null, null,
                )?.use { cur -> while (cur.moveToNext() && l.size < 8) l += Destinataire(cur.getString(0), cur.getString(1).orEmpty(), cur.getString(2)) }
            } catch (_: Exception) {
            }
            l.distinctBy { it.numero.filter { ch -> ch.isDigit() } }
        }
    }
    fun choisir(d: Destinataire) {
        if (choisis.none { it.numero.filter { ch -> ch.isDigit() } == d.numero.filter { ch -> ch.isDigit() } }) choisis += d
        saisie = ""
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Annuler", modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = retour).padding(10.dp), style = texte(16f, a.encre2))
            BasicText("Nouveau message", modifier = Modifier.weight(1f), style = texte(17f, a.encre, FontWeight.Bold).copy(textAlign = TextAlign.Center))
            Box(Modifier.size(80.dp, 1.dp))
        }
        // À : les destinataires choisis, puis ce qu'on tape.
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.surface).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("À", style = texte(16f, a.encre2, FontWeight.SemiBold))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                choisis.forEach { d ->
                    Row(
                        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(16.dp)).background(a.voile).clickable(onClickLabel = "Retirer ${d.nom ?: d.numero}") { choisis.remove(d) }.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Visage(d.nom, d.photo, 24.dp)
                        BasicText(d.nom?.substringBefore(' ') ?: Boite.formater(d.numero), modifier = Modifier.padding(start = 6.dp), style = texte(14f, a.accentTexte, FontWeight.SemiBold))
                        IconeTrait(Icones.FERMER, 13.dp, a.accentTexte, Modifier.padding(start = 4.dp))
                    }
                }
                BasicTextField(
                    value = saisie,
                    onValueChange = { saisie = it },
                    singleLine = true,
                    textStyle = texte(16f, a.encre),
                    cursorBrush = SolidColor(a.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = if (saisie.firstOrNull()?.isDigit() == true || saisie.startsWith("+")) KeyboardType.Phone else KeyboardType.Text),
                    modifier = Modifier.heightIn(min = 40.dp).padding(vertical = 9.dp).focusRequester(focus).semantics { contentDescription = "Destinataire" },
                    decorationBox = { champ -> Box { if (saisie.isEmpty() && choisis.isEmpty()) BasicText("Un nom ou un numéro", style = texte(16f, a.encre2)); champ() } },
                )
            }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            val numeroTape = saisie.filter { it.isDigit() || it == '+' }
            if (numeroTape.length >= 8 && trouves.none { it.numero.filter { ch -> ch.isDigit() }.endsWith(numeroTape.takeLast(8)) }) {
                item(key = "numero") {
                    LigneDestinataire(Destinataire(null, numeroTape), "Envoyer à ce numéro" + (Boite.operateurDe(numeroTape)?.let { " · $it" } ?: ""), Modifier.animateItem()) { choisir(Destinataire(null, numeroTape)) }
                }
            }
            val liste = if (saisie.length >= 2) trouves else recents.filter { r -> choisis.none { it.numero == r.numero } }
            if (liste.isNotEmpty()) item(key = "titre") {
                BasicText(
                    if (saisie.length >= 2) "CONTACTS" else "RÉCENTS",
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 6.dp).animateItem(), style = texte(12.5f, a.encre2, FontWeight.Bold),
                )
            }
            items(liste, key = { "d" + it.numero }) { d ->
                LigneDestinataire(d, listOfNotNull(Boite.formater(d.numero), Boite.operateurDe(d.numero)).joinToString(" · "), Modifier.animateItem()) { choisir(d) }
            }
        }
        AnimatedVisibility(choisis.size > 1) {
            BasicText(
                "Plusieurs destinataires : chacun recevra son propre SMS, comme s'il était seul.",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                style = texte(13.5f, a.encre2),
            )
        }
        AnimatedVisibility(choisis.isNotEmpty()) {
            Composer(texte) { t, sim ->
                // Chacun reçoit son propre SMS, comme s'il était seul.
                choisis.forEach { d -> Boite.envoyer(c, d.numero, t, sim) }
                val premier = choisis.first().numero
                ouvrir(Boite.filDe(c, premier), premier)
            }
        }
    }
}

@Composable
private fun LigneDestinataire(d: Destinataire, second: String, modifier: Modifier, choisir: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        modifier.fillMaxWidth().clickable(onClickLabel = "Écrire à ${d.nom ?: d.numero}", role = Role.Button, onClick = choisir).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Visage(d.nom, d.photo, 44.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            BasicText(d.nom ?: Boite.formater(d.numero), maxLines = 1, overflow = TextOverflow.Ellipsis, style = texte(16f, a.encre, FontWeight.Medium))
            BasicText(second, style = texte(13.5f, a.encre2))
        }
        Box(Modifier.size(32.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(Icones.PLUS, 16.dp, a.accentTexte) }
    }
}
