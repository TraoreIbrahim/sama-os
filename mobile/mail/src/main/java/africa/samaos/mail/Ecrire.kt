package africa.samaos.mail

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    val titre = when {
        depart.enReponseA != null -> "Répondre"
        depart.sujet.startsWith("Tr :") -> "Transférer"
        else -> "Nouveau mail"
    }
    Column(Modifier.fillMaxSize().background(a.fond).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // La tête : « Annuler » (le brouillon reste sur le téléphone), le titre, joindre et envoyer.
        Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp)) {
            BasicText(
                "Annuler",
                modifier = Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Annuler et garder le brouillon", role = Role.Button) { garderEtQuitter() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = st(17f, a.accentTexte),
            )
            BasicText(titre, modifier = Modifier.align(Alignment.Center), style = st(17f, a.encre, FontWeight.SemiBold))
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                Rond(IconesMail.TROMBONE, "Joindre un fichier") { joindre.launch(arrayOf("*/*")) }
                Spacer(Modifier.width(8.dp))
                Rond(Icones.ENVOYER, "Envoyer", plein = true) { envoyer() }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Ligne("À") {
                Saisie(dest, Modifier.focusRequester(focusDest), { v ->
                    dest = v
                    if (!demande && !Carnet.permis(c)) {
                        demande = true
                        permission.launch(Manifest.permission.READ_CONTACTS)
                    }
                }, KeyboardType.Email)
                if (!avecCc) BasicText("Cc", modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Ajouter des copies", role = Role.Button) { avecCc = true }.padding(horizontal = 10.dp, vertical = 8.dp), style = st(15f, a.accentTexte, FontWeight.SemiBold))
            }
            // Les contacts qui correspondent à ce qu'on tape.
            suggestions.forEachIndexed { i, (nom, adresse) ->
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(forme(i, suggestions.size)).background(a.surface)
                        .clickable(onClickLabel = "Écrire à ${nom.ifBlank { adresse }}", role = Role.Button) {
                            val avant = dest.substringBeforeLast(',', "").trim()
                            dest = (if (avant.isEmpty()) "" else "$avant, ") + adresse + ", "
                            suggestions = emptyList()
                        }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(Icones.PERSONNE, 18.dp, a.accentTexte) }
                    Column(Modifier.padding(start = 12.dp)) {
                        BasicText(nom.ifBlank { adresse }, maxLines = 1, style = st(15.5f, a.encre, FontWeight.Medium))
                        if (nom.isNotBlank()) BasicText(adresse, maxLines = 1, style = st(13f, a.encre2))
                    }
                }
            }
            Separateur(20.dp)
            if (avecCc) {
                Ligne("Cc") { Saisie(cc, Modifier, { cc = it }, KeyboardType.Email) }
                Separateur(20.dp)
            }
            if (comptes.size > 1) {
                Ligne("De") {
                    BasicText(
                        compte.adresse, modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { choixCompte = !choixCompte }.padding(vertical = 14.dp),
                        style = st(16f, a.encre),
                    )
                    IconeTrait(Icones.CHEVRON, 14.dp, a.encre2, Modifier.padding(end = 8.dp), epaisseur = 2f)
                }
                AnimatedVisibility(choixCompte, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        comptes.filter { it.id != compte.id }.forEach { autre ->
                            BasicText(
                                autre.adresse,
                                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                    compte = autre
                                    choixCompte = false
                                }.padding(start = 82.dp, top = 12.dp, bottom = 12.dp),
                                style = st(16f, a.accentTexte),
                            )
                        }
                    }
                }
                Separateur(20.dp)
            }
            Ligne("Objet") { Saisie(sujet, Modifier, { sujet = it }, KeyboardType.Text) }
            Separateur(20.dp)
            Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
                if (texte.isEmpty()) BasicText("Votre message", style = st(16.5f, a.encre2))
                // Tout le cadre est le champ : on touche n'importe où pour écrire.
                BasicTextField(
                    texte, { texte = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).focusRequester(focusCorps),
                    textStyle = st(16.5f, a.encre, interligne = 1.5f), cursorBrush = SolidColor(a.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            if (pieces.isNotEmpty()) {
                Section(if (pieces.size == 1) "Pièce jointe" else "Pièces jointes · ${pieces.size}")
                pieces.forEachIndexed { i, p ->
                    val f = File(p)
                    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(forme(i, pieces.size)).background(a.surface)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconeTrait(IconesMail.TROMBONE, 19.dp, a.encre2)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                BasicText(f.name, maxLines = 1, style = st(15.5f, a.encre, FontWeight.Medium))
                                BasicText(taille(f.length()), style = st(13f, a.encre2))
                            }
                            BoutonAppli(Icones.FERMER, "Retirer ${f.name}", taille = 40.dp) { pieces = pieces - p }
                        }
                        if (i < pieces.size - 1) Separateur(46.dp)
                    }
                }
            }
            erreur?.let { Note(it, a.accentTexte) }
            Spacer(Modifier.width(1.dp).heightIn(min = 40.dp))
        }
    }
}

@Composable
private fun Ligne(etiquette: String, contenu: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 22.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(etiquette, modifier = Modifier.width(50.dp), style = st(15.5f, a.encre2))
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
