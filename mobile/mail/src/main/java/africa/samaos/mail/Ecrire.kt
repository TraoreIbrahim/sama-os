package africa.samaos.mail

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Avatar
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Filet
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.mail.internet.InternetAddress
import kotlin.concurrent.thread

/** Les adresses d'un champ « À » ou « Cc », si elles sont toutes valables ; sinon la première qui ne l'est pas. */
private fun verifier(liste: String): String? {
    if (liste.isBlank()) return null
    return liste.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.firstOrNull { a ->
        try {
            InternetAddress(a, true)
            !a.substringAfterLast('@').contains('.')
        } catch (_: Exception) {
            true
        }
    }
}

@Composable
internal fun PageEcrire(depart: Brouillon, retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val portee = rememberCoroutineScope()
    val comptes = remember { Comptes.liste(c) }
    var compte by remember { mutableStateOf(comptes.firstOrNull { it.id == depart.compte } ?: Comptes.courant(c) ?: comptes.first()) }
    var dest by remember { mutableStateOf(depart.a) }
    var cc by remember { mutableStateOf(depart.cc) }
    var avecCc by remember { mutableStateOf(depart.cc.isNotBlank()) }
    var sujet by remember { mutableStateOf(depart.sujet) }
    var texte by remember { mutableStateOf(depart.texte) }
    var pieces by remember { mutableStateOf(depart.pieces) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var choixCompte by remember { mutableStateOf(false) }
    var demande by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val joindre = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        portee.launch {
            val nouveaux = withContext(Dispatchers.IO) { uris.mapNotNull { copierPiece(c, it) } }
            pieces = pieces + nouveaux
        }
    }
    // Les destinataires proposés à partir des Contacts (le mot en cours de frappe).
    LaunchedEffect(dest) {
        delay(200)
        val terme = dest.substringAfterLast(',').trim()
        suggestions = if (terme.length >= 2 && !terme.contains('@')) withContext(Dispatchers.IO) { Carnet.chercher(c, terme) } else emptyList()
    }
    val vide = dest.isBlank() && sujet.isBlank() && texte.isBlank() && pieces.isEmpty()
    fun garderEtQuitter() {
        if (!vide && (dest != depart.a || sujet != depart.sujet || texte != depart.texte || pieces != depart.pieces || cc != depart.cc)) {
            Base.garderBrouillon(c, Brouillon(depart.id, compte.id, dest, cc, sujet, texte, pieces, depart.enReponseA, depart.references, "brouillon"))
            Courrier.dernierMessage = "Brouillon gardé sur ce téléphone"
            Courrier.changer()
        } else if (vide && depart.id > 0) {
            Base.retirerBrouillon(c, depart.id)
        }
        retour()
    }
    fun envoyer() {
        val fausse = verifier(dest) ?: verifier(cc)
        erreur = when {
            dest.isBlank() -> "À qui envoyer ce mail ?"
            fausse != null -> "Cette adresse est à vérifier : $fausse"
            pieces.sumOf { File(it).length() } > 20_000_000 -> "Plus de 20 Mo de pièces jointes : la plupart des serveurs refusent. Retirez-en une partie."
            else -> null
        }
        if (erreur != null) return
        val b = Brouillon(depart.id, compte.id, dest.trim().trimEnd(','), cc.trim().trimEnd(','), sujet, texte, pieces, depart.enReponseA, depart.references, "attente")
        val id = Base.garderBrouillon(c, b)
        val pret = Brouillon(id, b.compte, b.a, b.cc, b.sujet, b.texte, b.pieces, b.enReponseA, b.references, "attente")
        Courrier.dernierMessage = "Envoi…"
        thread { Courrier.envoyer(c.applicationContext, pret) }
        retour()
    }
    BackHandler { garderEtQuitter() }
    // Le curseur va où l'on écrit : le message pour une réponse, le destinataire pour un nouveau mail.
    val focusCorps = remember { FocusRequester() }
    val focusDest = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(300)
        runCatching { if (depart.a.isBlank()) focusDest.requestFocus() else focusCorps.requestFocus() }
    }
    EcranAppli(Modifier.imePadding()) {
        Tete(if (depart.enReponseA != null) "Répondre" else "Nouveau mail", retour = { garderEtQuitter() }, petit = true) {
            BoutonAppli(IconesMail.TROMBONE, "Joindre un fichier") { joindre.launch(arrayOf("*/*")) }
            BoutonAppli(Icones.ENVOYER, "Envoyer", style = 'a') { envoyer() }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Ligne("De") {
                BasicText(
                    compte.adresse, modifier = Modifier.clickable(enabled = comptes.size > 1, role = Role.Button) { choixCompte = !choixCompte },
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre),
                )
            }
            if (choixCompte) comptes.filter { it.id != compte.id }.forEach { autre ->
                LigneAppli(autre.adresse, debut = { IconeTrait(IconesMail.ENVELOPPE, 20.dp, a.encre2) }) {
                    compte = autre
                    choixCompte = false
                }
            }
            Filet()
            Ligne("À") {
                Saisie(dest, Modifier.focusRequester(focusDest), { v ->
                    dest = v
                    if (!demande && !Carnet.permis(c)) {
                        demande = true
                        permission.launch(Manifest.permission.READ_CONTACTS)
                    }
                }, KeyboardType.Email)
                if (!avecCc) BasicText("Cc", modifier = Modifier.clickable(role = Role.Button) { avecCc = true }.padding(8.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.accentTexte))
            }
            suggestions.forEach { (nom, adresse) ->
                LigneAppli(nom.ifBlank { adresse }, second = adresse, debut = { Avatar(nom.ifBlank { adresse }, 36.dp) }) {
                    val avant = dest.substringBeforeLast(',', "").trim()
                    dest = (if (avant.isEmpty()) "" else "$avant, ") + adresse + ", "
                    suggestions = emptyList()
                }
            }
            Filet()
            if (avecCc) {
                Ligne("Cc") { Saisie(cc, Modifier, { cc = it }, KeyboardType.Email) }
                Filet()
            }
            Ligne("Objet") { Saisie(sujet, Modifier, { sujet = it }, KeyboardType.Text) }
            Filet()
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (texte.isEmpty()) BasicText("Votre message", style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2))
                // Tout le cadre est le champ : on touche n'importe où pour écrire.
                BasicTextField(
                    texte, { texte = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).focusRequester(focusCorps),
                    textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 24.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
                )
            }
            pieces.forEach { p ->
                val f = File(p)
                LigneAppli(f.name, second = taille(f.length()), debut = { IconeTrait(IconesMail.TROMBONE, 20.dp, a.encre2) }, fin = {
                    BoutonAppli(Icones.FERMER, "Retirer ${f.name}", taille = 40.dp) { pieces = pieces - p }
                })
            }
            erreur?.let { Note(it, a.accentTexte) }
            Spacer(Modifier.width(1.dp).heightIn(min = 40.dp))
        }
    }
}

@Composable
private fun Ligne(etiquette: String, contenu: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(etiquette, modifier = Modifier.width(52.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { contenu() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Saisie(valeur: String, modifier: Modifier, changer: (String) -> Unit, clavier: KeyboardType) {
    val a = LocalIdentite.current
    BasicTextField(
        valeur, changer, singleLine = true, modifier = modifier.weight(1f).padding(vertical = 14.dp),
        textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre), cursorBrush = SolidColor(a.accent),
        keyboardOptions = KeyboardOptions(keyboardType = clavier, autoCorrectEnabled = clavier == KeyboardType.Text),
    )
}
