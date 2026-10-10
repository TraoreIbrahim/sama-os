package africa.samaos.mail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.LocalFermerFeuille
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
        if (retour != null) Barre("Retour", retour) else Spacer(Modifier.height(24.dp))
        GrandTitre(if (premiere) "Mail" else "Ajouter une adresse mail")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(
                if (premiere) "Mail marche avec la boîte que vous avez déjà : celle de votre fournisseur, de votre travail ou de votre école."
                else "Une autre adresse : elle aura ses propres dossiers. On passe de l'une à l'autre d'un toucher sur l'adresse, en haut de la Réception.",
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

/** Les réglages de Mail : les adresses (vérifier, mot de passe, nom, retirer), la relève, les images. */
@Composable
internal fun PageReglages(aller: (Vue) -> Unit, retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    var v by remember { mutableIntStateOf(0) }
    var choisi by remember { mutableStateOf<Compte?>(null) }
    var retirer by remember { mutableStateOf<Compte?>(null) }
    val comptes = remember(v, Courrier.version) { Comptes.liste(c) }
    Column(Modifier.fillMaxSize().background(a.fond).statusBarsPadding()) {
        Barre("Retour", retour)
        LazyColumn(Modifier.weight(1f)) {
            item { GrandTitre("Réglages") }
            item { Section("Adresses mail") }
            itemsIndexed(comptes, key = { _, x -> x.id }) { i, x ->
                LigneReglage(IconesMail.ENVELOPPE, a.accentTexte, x.adresse, listOf(x.nom, x.imap.hote).filter { it.isNotBlank() }.joinToString(" · "), i, comptes.size + 1) { choisi = x }
            }
            item { LigneReglage(Icones.PLUS, a.accentTexte, "Ajouter une adresse mail", null, comptes.size, comptes.size + 1) { aller(Vue.Ajouter) } }
            item { Section("Relève") }
            item {
                val f = remember(v) { Preferences.frequence(c) }
                LigneReglage(Icones.ROTATION, a.encre2, "Relever les mails", Preferences.nomFrequence(f) + ", avec un réseau ; les mails en attente partent en même temps", 0, 2, fin = {
                    BasicText("Changer", style = st(14.5f, a.accentTexte, FontWeight.SemiBold))
                }) {
                    Preferences.frequenceSuivante(c)
                    v++
                }
                val t = remember(v) { Preferences.texteSurData(c) }
                LigneReglage(Icones.DONNEES, a.encre2, "Le texte aussi sur data mobile", if (t) "Le texte vient avec les en-têtes" else "Sur data, seulement les en-têtes ; le texte à l'ouverture", 1, 2, fin = { InterAppli(t) }) {
                    Preferences.reglerTexteSurData(c, !t)
                    v++
                }
            }
            item { Section("Lecture") }
            item {
                val i = remember(v) { Preferences.imagesToujours(c) }
                LigneReglage(Icones.IMAGE, a.encre2, "Images des mails", if (i) "Toujours chargées" else "À la demande : moins de data, et l'expéditeur ne sait pas que vous avez ouvert", 0, 1, fin = { InterAppli(i) }) {
                    Preferences.reglerImages(c, !i)
                    v++
                }
            }
            item {
                Note("Mail ne garde que les derniers mails de chaque dossier sur le téléphone ; tout reste sur le serveur de votre boîte. Une pièce jointe ne se télécharge que si vous la demandez.")
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
    choisi?.let { x -> FeuilleCompte(x, fermer = { choisi = null }, retirer = { retirer = x }) { v++ } }
    retirer?.let { x ->
        DialogueAppli("Retirer ${x.adresse} ?", "Les mails restent sur le serveur ; ce téléphone oublie l'adresse et son mot de passe.", fermer = { retirer = null }) {
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

/** Une ligne de réglage dans un groupe aux coins doux. */
@Composable
private fun LigneReglage(icone: String, couleur: Color, titre: String, second: String?, i: Int, n: Int, fin: @Composable () -> Unit = { IconeTrait(Icones.CHEVRON, 15.dp, LocalIdentite.current.encre2.copy(alpha = 0.7f), epaisseur = 2f) }, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(forme(i, n)).background(a.surface).clickable(onClickLabel = titre, role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconeTrait(icone, 22.dp, couleur)
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
                BasicText(titre, maxLines = 1, overflow = TextOverflow.Ellipsis, style = st(16.5f, a.encre))
                if (!second.isNullOrBlank()) BasicText(second, style = st(13f, a.encre2, interligne = 1.35f))
            }
            fin()
        }
        if (i < n - 1) Separateur(52.dp)
    }
}

/** Une adresse : où sont ses serveurs, vérifier qu'elle répond, changer son mot de passe ou le nom affiché, la retirer. */
@Composable
private fun FeuilleCompte(x: Compte, fermer: () -> Unit, retirer: () -> Unit, change: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val portee = rememberCoroutineScope()
    var etat by remember { mutableStateOf<String?>(null) }
    var motDePasse by remember { mutableStateOf(false) }
    var nom by remember { mutableStateOf(false) }
    FeuilleAppli(fermer = fermer, titre = x.adresse) {
        val f = LocalFermerFeuille.current
        BasicText(
            "Réception : ${x.imap.hote} · ${x.imap.securite.nom}\nEnvoi : ${x.smtp.hote} · ${x.smtp.securite.nom}",
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp), style = st(13f, a.encre2, interligne = 1.45f),
        )
        ActionFeuille(Icones.ROTATION, "Vérifier la connexion", second = etat) {
            etat = "Connexion…"
            portee.launch {
                val probleme = withContext(Dispatchers.IO) { Courrier.tester(x, Comptes.motDePasse(c, x).orEmpty()) }
                etat = probleme ?: "Tout marche : la boîte répond."
            }
        }
        ActionFeuille(Icones.CLE, "Changer le mot de passe", second = "Si vous l'avez changé chez votre fournisseur") { motDePasse = true }
        ActionFeuille(Icones.PERSONNE, "Le nom vu par vos destinataires", second = x.nom.ifBlank { "Aucun nom" }) { nom = true }
        ActionFeuille(Icones.CORBEILLE, "Retirer cette adresse", danger = true, second = "Les mails restent sur le serveur") {
            f()
            retirer()
        }
    }
    if (motDePasse) {
        var mdp by remember { mutableStateOf("") }
        var erreur by remember { mutableStateOf<String?>(null) }
        var encours by remember { mutableStateOf(false) }
        DialogueAppli("Nouveau mot de passe", "Celui de ${x.adresse}. Il reste sur ce téléphone, chiffré.", fermer = { motDePasse = false }, contenu = {
            ChampTexte(mdp, "Mot de passe", { mdp = it }, clavier = KeyboardType.Password, secret = true)
            erreur?.let { BasicText(it, style = st(14f, a.accentTexte, interligne = 1.4f)) }
        }) {
            val fd = LocalFermerFeuille.current
            BoutonTexteAppli("Annuler", style = 't') { fd() }
            BoutonTexteAppli(if (encours) "Vérification…" else "Vérifier et garder", actif = mdp.isNotEmpty() && !encours) {
                encours = true
                portee.launch {
                    val probleme = withContext(Dispatchers.IO) { Courrier.tester(x, mdp) }
                    encours = false
                    if (probleme != null) {
                        erreur = probleme
                    } else {
                        Courrier.deconnecter(x)
                        Comptes.ajouter(c, x, mdp)
                        Courrier.erreurs.remove(x.id)
                        Courrier.changer()
                        etat = "Mot de passe mis à jour."
                        fd()
                    }
                }
            }
        }
    }
    if (nom) {
        var texte by remember { mutableStateOf(x.nom) }
        DialogueAppli("Votre nom", "Ce que voient vos destinataires à côté de ${x.adresse}.", fermer = { nom = false }, contenu = {
            ChampTexte(texte, "Votre nom", { texte = it.take(60) })
        }) {
            val fd = LocalFermerFeuille.current
            BoutonTexteAppli("Annuler", style = 't') { fd() }
            BoutonTexteAppli("Garder") {
                Comptes.motDePasse(c, x)?.let { m -> Comptes.ajouter(c, Compte(x.id, x.adresse, texte.trim(), x.identifiant, x.imap, x.smtp), m) }
                change()
                Courrier.changer()
                fd()
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
        Barre("Boîtes", retour)
        GrandTitre("Sur ce téléphone", "Brouillons et mails qui attendent le réseau")
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
