package africa.samaos.mail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.concurrent.thread

/** Ajouter une boîte mail : l'adresse et le mot de passe suffisent d'habitude ; sinon, les serveurs à la main. */
@Composable
internal fun PageAjouter(premiere: Boolean, retour: (() -> Unit)?, fini: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val portee = rememberCoroutineScope()
    var adresse by remember { mutableStateOf("") }
    var mdp by remember { mutableStateOf("") }
    var nom by remember { mutableStateOf("") }
    var manuel by remember { mutableStateOf(false) }
    var imapHote by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("993") }
    var imapSec by remember { mutableStateOf(Securite.SSL) }
    var smtpHote by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("465") }
    var smtpSec by remember { mutableStateOf(Securite.SSL) }
    var identifiant by remember { mutableStateOf("") }
    var etat by remember { mutableStateOf<String?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val domaine = adresse.substringAfterLast('@', "").lowercase().trim()
    val conseil = Configuration.conseil(domaine)
    fun continuer() {
        erreur = null
        val adr = adresse.trim()
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(adr)) {
            erreur = "Cette adresse mail est à vérifier."
            return
        }
        if (mdp.isEmpty()) {
            erreur = "Il faut le mot de passe de cette boîte."
            return
        }
        portee.launch {
            val compte = withContext(Dispatchers.IO) {
                if (!manuel) {
                    etat = "Recherche des serveurs de $domaine…"
                    when (val t = Configuration.trouver(adr)) {
                        is Configuration.Trouvee -> Compte(UUID.randomUUID().toString(), adr, nom.trim(), t.identifiant, t.imap, t.smtp)
                        is Configuration.Obstacle -> {
                            erreur = t.message
                            null
                        }
                        else -> {
                            // On propose des noms courants, à vérifier dans l'aide du fournisseur.
                            imapHote = "imap.$domaine"
                            smtpHote = "smtp.$domaine"
                            identifiant = adr
                            manuel = true
                            erreur = "Les serveurs de $domaine n'ont pas été trouvés tout seuls. Vérifiez-les ci-dessous : ils sont dans l'aide de votre fournisseur."
                            null
                        }
                    }
                } else {
                    val ip = imapPort.toIntOrNull()
                    val sp = smtpPort.toIntOrNull()
                    if (imapHote.isBlank() || smtpHote.isBlank() || ip == null || sp == null) {
                        erreur = "Il manque un serveur ou un port."
                        null
                    } else {
                        Compte(UUID.randomUUID().toString(), adr, nom.trim(), identifiant.trim().ifBlank { adr }, Serveur(imapHote.trim(), ip, imapSec), Serveur(smtpHote.trim(), sp, smtpSec))
                    }
                }
            } ?: run {
                etat = null
                return@launch
            }
            etat = "Connexion à ${compte.imap.hote}…"
            val probleme = withContext(Dispatchers.IO) { Courrier.tester(compte, mdp) }
            etat = null
            if (probleme != null) {
                erreur = probleme
                if (!manuel) {
                    imapHote = compte.imap.hote; imapPort = compte.imap.port.toString(); imapSec = compte.imap.securite
                    smtpHote = compte.smtp.hote; smtpPort = compte.smtp.port.toString(); smtpSec = compte.smtp.securite
                    identifiant = compte.identifiant
                }
                return@launch
            }
            Comptes.ajouter(c, compte, mdp)
            Comptes.choisir(c, compte)
            Ecran.dossier = "INBOX"
            Releve.programmer(c)
            thread { runCatching { Courrier.relever(c.applicationContext, compte) } }
            Courrier.changer()
            fini()
        }
    }
    EcranAppli(Modifier.imePadding()) {
        Tete(if (premiere) "Mail" else "Ajouter une boîte", retour = retour)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(
                if (premiere) "Mail marche avec la boîte que vous avez déjà : celle de votre fournisseur, de votre travail ou de votre école."
                else "Une autre boîte : elle aura ses propres dossiers.",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 23.sp, color = a.encre2),
            )
            ChampTexte(adresse, "Adresse mail", { adresse = it.trim() }, clavier = KeyboardType.Email)
            ChampTexte(mdp, "Mot de passe", { mdp = it }, clavier = KeyboardType.Password, secret = true)
            ChampTexte(nom, "Votre nom (vu par vos destinataires)", { nom = it.take(60) })
            conseil?.let { BasicText(it, modifier = Modifier.padding(horizontal = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2)) }
            if (manuel) {
                Rub("Réception (IMAP)")
                ChampTexte(imapHote, "Serveur, par exemple imap.exemple.com", { imapHote = it.trim() }, clavier = KeyboardType.Uri)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChampTexte(imapPort, "Port", { imapPort = it.filter(Char::isDigit).take(5) }, Modifier.width(110.dp), KeyboardType.Number)
                    ChoixSecurite(imapSec) { imapSec = it; imapPort = if (it == Securite.SSL) "993" else "143" }
                }
                Rub("Envoi (SMTP)")
                ChampTexte(smtpHote, "Serveur, par exemple smtp.exemple.com", { smtpHote = it.trim() }, clavier = KeyboardType.Uri)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChampTexte(smtpPort, "Port", { smtpPort = it.filter(Char::isDigit).take(5) }, Modifier.width(110.dp), KeyboardType.Number)
                    ChoixSecurite(smtpSec) { smtpSec = it; smtpPort = if (it == Securite.SSL) "465" else "587" }
                }
                ChampTexte(identifiant, "Identifiant (souvent l'adresse mail)", { identifiant = it.trim() }, clavier = KeyboardType.Email)
            }
            erreur?.let { BasicText(it, modifier = Modifier.padding(horizontal = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.accentTexte)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonTexteAppli(etat ?: "Continuer", actif = etat == null) { continuer() }
                if (!manuel) BoutonTexteAppli("Serveurs à la main", style = 't') { manuel = true; identifiant = adresse }
            }
            Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconeTrait(Icones.CADENAS, 18.dp, a.encre2)
                BasicText(
                    "Votre mot de passe reste sur ce téléphone, chiffré par sa puce de sécurité. Sama ne le voit jamais, et personne de sérieux ne vous le demandera, ni par mail ni au téléphone.",
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 19.sp, color = a.encre2),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChoixSecurite(s: Securite, changer: (Securite) -> Unit) {
    val a = LocalIdentite.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Securite.entries.forEach { x ->
            BasicText(
                x.nom,
                modifier = Modifier.clickable(role = Role.RadioButton) { changer(x) }.padding(horizontal = 8.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = if (x == s) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp, color = if (x == s) a.accentTexte else a.encre2),
            )
        }
    }
}

// ——— Réglages ———

@Composable
internal fun PageReglages(aller: (Vue) -> Unit, retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    var v by remember { mutableIntStateOf(0) }
    var retirer by remember { mutableStateOf<Compte?>(null) }
    val comptes = remember(v) { Comptes.liste(c) }
    EcranAppli {
        Tete("Réglages", retour = retour)
        LazyColumn {
            item { Rub("Relever les mails") }
            item {
                val f = remember(v) { Preferences.frequence(c) }
                LigneAppli(Preferences.nomFrequence(f), second = "Avec un réseau seulement ; les mails en attente partent en même temps", debut = { IconeTrait(Icones.ROTATION, 20.dp, a.encre2) }) {
                    Preferences.frequenceSuivante(c)
                    v++
                }
            }
            item {
                val t = remember(v) { Preferences.texteSurData(c) }
                LigneAppli("Le texte aussi sur data mobile", second = if (t) "Le texte vient avec les en-têtes" else "Sur data, seulement les en-têtes ; le texte à l'ouverture", fin = { InterAppli(t) }) {
                    Preferences.reglerTexteSurData(c, !t)
                    v++
                }
            }
            item { Rub("Lire") }
            item {
                val i = remember(v) { Preferences.imagesToujours(c) }
                LigneAppli(
                    "Images des mails", second = if (i) "Toujours chargées" else "À la demande : moins de data, et l'expéditeur ne sait pas que vous avez ouvert",
                    fin = { InterAppli(i) },
                ) {
                    Preferences.reglerImages(c, !i)
                    v++
                }
            }
            item { Rub("Boîtes") }
            items(comptes, key = { it.id }) { x ->
                LigneAppli(x.adresse, second = "${x.imap.hote} · ${x.imap.securite.nom}", debut = { IconeTrait(IconesMail.ENVELOPPE, 20.dp, a.encre2) }, fin = {
                    BoutonTexteAppli("Retirer", style = 't') { retirer = x }
                })
            }
            item { LigneAppli("Ajouter une boîte", debut = { IconeTrait(Icones.PLUS, 20.dp, a.encre2) }) { aller(Vue.Ajouter) } }
            item {
                Note("Mail ne garde que les derniers mails de chaque dossier sur le téléphone ; tout reste sur le serveur de votre boîte. Une pièce jointe ne se télécharge que si vous la demandez.")
            }
        }
    }
    retirer?.let { x ->
        DialogueAppli("Retirer ${x.adresse} ?", "Les mails restent sur le serveur ; ce téléphone oublie la boîte et son mot de passe.", fermer = { retirer = null }) {
            BoutonTexteAppli("Annuler", style = 't') { retirer = null }
            Spacer(Modifier.width(8.dp))
            BoutonTexteAppli("Retirer") {
                Courrier.deconnecter(x)
                Comptes.retirer(c, x)
                Releve.programmer(c)
                retirer = null
                v++
                Courrier.changer()
            }
        }
    }
}

// ——— Ce qui attend sur ce téléphone ———

@Composable
internal fun PageBrouillons(aller: (Vue) -> Unit, retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val l = remember(Courrier.version) { Base.brouillons(c) }
    EcranAppli {
        Tete("Sur ce téléphone", retour = retour)
        LazyColumn {
            if (l.isEmpty()) item { Note("Rien n'attend ici.") }
            items(l, key = { it.id }) { b ->
                val etat = when (Courrier.envois[b.id] ?: b.etat) {
                    "attente" -> "Partira dès que le téléphone sera connecté"
                    "echec" -> "Pas envoyé : ${b.erreur ?: "erreur"}"
                    "envoi" -> "Envoi…"
                    else -> "Brouillon · ${dateCourte(b.quand)}"
                }
                LigneAppli(
                    b.sujet.ifBlank { "(sans objet)" }, second = (if (b.a.isNotBlank()) "À ${b.a} · " else "") + etat,
                    couleurSecond = if (b.etat == "echec") a.accentTexte else null,
                    debut = { IconeTrait(if (b.etat == "brouillon") Icones.CRAYON else Icones.ENVOYER, 20.dp, a.encre2) },
                ) { aller(Vue.Ecrire(b)) }
            }
        }
    }
}
