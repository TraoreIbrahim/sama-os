package africa.samaos.telephone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Avatar
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceSim
import africa.samaos.banco.appli.AvecIdentite
import kotlinx.coroutines.delay

private val ROUGE = Color(0xFFC2412D)
private val VERT = Color(0xFF2F8F68)

/** L'écran d'un appel (maquettes l2-tel-entrant et l2-tel-en-cours). Il se ferme quand plus rien ne se passe. */
class EcranAppel : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AvecIdentite(Identites.Appel) {
                val appels by Appels.liste.collectAsState()
                val version by Appels.version.collectAsState()
                LaunchedEffect(appels.isEmpty()) {
                    if (appels.isEmpty()) {
                        delay(600)
                        finish()
                    }
                }
                val appel = remember(appels, version) { Appels.principal() }
                // L'état est passé à part : l'objet Call reste le même d'un état à l'autre.
                if (appel != null) EcranDunAppel(appel, appel.details.state, version)
            }
        }
    }
}

@Composable
private fun EcranDunAppel(appel: Call, etat: Int, version: Int) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val numero = Appels.numero(appel)
    val personne = remember(numero) { Moteur.personne(c, numero) }
    val sim = remember(appel) { Moteur.simDuCompte(c, appel.details.accountHandle?.id) }
    val entrant = etat == Call.STATE_RINGING
    var clavier by remember { mutableStateOf(false) }
    var reponses by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(a.fond).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val texte = when (etat) {
                Call.STATE_RINGING -> "Appel entrant"
                Call.STATE_DIALING, Call.STATE_CONNECTING -> "Appel en cours…"
                Call.STATE_HOLDING -> "En attente"
                Call.STATE_DISCONNECTED -> "Appel terminé"
                else -> null
            }
            if (texte != null) {
                BasicText(texte + if (sim != null && entrant) " sur la " else "", style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
                if (sim != null && entrant) {
                    PuceSim(sim.fente, sim.operateur)
                    BasicText(" ${sim.operateur}", style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
                }
            } else {
                Chrono(appel)
            }
        }
        Spacer(Modifier.height(36.dp))
        Avatar(personne?.nom, 112.dp, if (personne == null) null else null)
        Spacer(Modifier.height(20.dp))
        BasicText(
            personne?.nom ?: Moteur.secours(numero) ?: Moteur.formater(numero).ifBlank { "Numéro masqué" },
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, color = a.encre, textAlign = TextAlign.Center),
        )
        val detail = listOfNotNull(
            if (personne != null || Moteur.secours(numero) != null) Moteur.formater(numero) else null,
            personne?.type ?: Moteur.operateurDe(numero),
        ).joinToString(" · ")
        if (detail.isNotBlank()) BasicText(detail, modifier = Modifier.padding(top = 6.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2))
        if (!entrant && sim != null) {
            val hd = appel.details.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PuceSim(sim.fente, sim.operateur)
                BasicText(" ${sim.operateur}" + if (hd) " · voix HD" else "", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = a.encre2))
            }
        }
        // Le bouclier : un inconnu qui insiste, ou qui appelle la nuit (maquette i2-appel-suspect).
        val suspect = remember(numero, entrant) {
            if (entrant && africa.samaos.bouclier.Bouclier.actif(c, africa.samaos.bouclier.Bouclier.Garde.APPELS)) {
                africa.samaos.bouclier.Bouclier.appel(personne != null, Moteur.appelsRecents(c, numero) + 1)
            } else null
        }
        if (suspect != null) {
            LaunchedEffect(numero) { africa.samaos.bouclier.Bouclier.noter(c, "appel", suspect.titre, "Appel du ${Moteur.formater(numero)} · ${suspect.raison}") }
            val laterite = androidx.compose.ui.graphics.Color(0xFFEE9A78)
            Column(
                Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(laterite.copy(alpha = 0.16f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(suspect.titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = laterite))
                BasicText(suspect.raison, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre))
            }
        }
        if (entrant && suspect == null) {
            val manque = remember(numero) { Moteur.dernierManque(c, numero) }
            if (manque != null && System.currentTimeMillis() - manque < 24 * 3600_000) {
                BasicText("Vous l'avez manqué à ${Moteur.heure(manque)}", modifier = Modifier.padding(top = 10.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.accent))
            }
        }
        Spacer(Modifier.weight(1f))
        if (entrant) {
            if (reponses) {
                ReponsesRapides { texte ->
                    appel.reject(true, texte)
                    reponses = false
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    GrosBouton("Refuser", "M6.6 4h2.8l1.4 3.6l-1.9 1.3a10.5 10.5 0 0 0 6.2 6.2l1.3-1.9l3.6 1.4v2.8a1.6 1.6 0 0 1-1.7 1.6A15.5 15.5 0 0 1 5 5.7A1.6 1.6 0 0 1 6.6 4z", ROUGE) { appel.reject(false, null) }
                    if (suspect != null) GrosBouton("Bloquer", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M5.6 5.6l12.8 12.8", a.champ) {
                        Moteur.bloquer(c, numero)
                        appel.reject(false, null)
                    } else GrosBouton("Message", Icones.MESSAGE, a.champ) { reponses = true }
                    GrosBouton("Répondre", Icones.APPEL, VERT) { appel.answer(VideoProfile.STATE_AUDIO_ONLY) }
                }
            }
        } else if (etat != Call.STATE_DISCONNECTED) {
            if (clavier) {
                PaveDtmf(appel) { clavier = false }
            } else {
                Commandes(appel, etat, version, numero) { clavier = true }
            }
            Box(
                Modifier
                    .padding(top = 24.dp, bottom = 32.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ROUGE)
                    .clickable(onClickLabel = "Raccrocher", role = Role.Button) { appel.disconnect() }
                    .semantics { contentDescription = "Raccrocher" },
                contentAlignment = Alignment.Center,
            ) {
                IconeTrait("M3 14c5-5 13-5 18 0l-2.5 2.5l-3-1.5v-2.5a10 10 0 0 0-7 0v2.5l-3 1.5z", 30.dp, Color.White, epaisseur = 2f)
            }
        } else {
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun Chrono(appel: Call) {
    val a = LocalIdentite.current
    var secondes by remember { mutableLongStateOf(0) }
    LaunchedEffect(appel) {
        while (true) {
            val debut = appel.details.connectTimeMillis
            secondes = if (debut > 0) (System.currentTimeMillis() - debut) / 1000 else 0
            delay(500)
        }
    }
    BasicText(
        "%02d:%02d".format(secondes / 60, secondes % 60),
        style = TextStyle(fontFamily = Polices.horloge, fontWeight = FontWeight(250), fontSize = 40.sp, color = a.encre),
    )
}

@Composable
private fun GrosBouton(nom: String, icone: String, fond: Color, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(fond)
                .clickable(onClickLabel = nom, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = nom },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(icone, 30.dp, Color.White, epaisseur = 2f) }
        BasicText(nom, modifier = Modifier.padding(top = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = a.encre))
    }
}

/** Les commandes d'un appel en cours. */
@Composable
private fun Commandes(appel: Call, etat: Int, version: Int, numero: String, ouvrirClavier: () -> Unit) {
    val c = LocalContext.current
    val audio by Appels.audio.collectAsState()
    val muet = audio?.isMuted == true
    val hautParleur = audio?.route == CallAudioState.ROUTE_SPEAKER
    val attente = etat == Call.STATE_HOLDING
    val details = if (version >= 0) appel.details else appel.details
    val peutAttente = details.can(Call.Details.CAPABILITY_HOLD) || details.can(Call.Details.CAPABILITY_SUPPORT_HOLD)
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Commande("Couper le micro", "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z M5 11a7 7 0 0 0 14 0 M12 18v3 M4 4l16 16", muet) { Appels.muet(!muet) }
            Commande("Clavier", Icones.CLAVIER, false, onClick = ouvrirClavier)
            Commande("Haut-parleur", Icones.ENCEINTE, hautParleur) { Appels.hautParleur(!hautParleur) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Commande("Attente", Icones.PAUSE, attente, actif = peutAttente) { if (attente) appel.unhold() else appel.hold() }
            Commande("Ajouter", Icones.PLUS, false) {
                c.startActivity(Intent(c, Telephone::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Commande("Message", Icones.MESSAGE, false) {
                c.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", numero, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

@Composable
private fun Commande(nom: String, icone: String, allume: Boolean, actif: Boolean = true, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(100.dp, 104.dp)) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (allume) a.encre else a.champ)
                .clickable(enabled = actif, onClickLabel = nom, role = Role.Switch, onClick = onClick)
                .semantics { contentDescription = nom },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(icone, 26.dp, if (allume) a.fond else if (actif) a.encre else a.encre2) }
        BasicText(nom, modifier = Modifier.padding(top = 6.dp), maxLines = 2, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, color = if (actif) a.encre else a.encre2))
    }
}

/** Le clavier pendant un appel : chaque touche envoie son signal (serveurs vocaux, codes). */
@Composable
private fun PaveDtmf(appel: Call, fermer: () -> Unit) {
    val a = LocalIdentite.current
    var tape by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(tape.ifEmpty { " " }, style = TextStyle(fontFamily = Polices.corps, fontSize = 28.sp, color = a.encre))
        Pave(compact = true) { t ->
            tape += t
            appel.playDtmfTone(t)
            appel.stopDtmfTone()
        }
        BasicText(
            "Masquer le clavier",
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = fermer).padding(12.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.accent),
        )
    }
}

/** Refuser par un message : l'appli de SMS par défaut l'envoie. */
@Composable
private fun ReponsesRapides(envoyer: (String) -> Unit) {
    val a = LocalIdentite.current
    val textes = listOf("Je te rappelle tout de suite.", "Je suis occupé, écris-moi.", "J'arrive.", "Je ne peux pas parler, je t'appelle plus tard.")
    Column(Modifier.fillMaxWidth().padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        textes.forEach { t ->
            BasicText(
                t,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(a.champ).clickable(role = Role.Button) { envoyer(t) }.padding(16.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre),
            )
        }
    }
}
