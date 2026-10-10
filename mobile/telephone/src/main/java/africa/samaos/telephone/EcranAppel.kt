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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Visage
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ROUGE = Color(0xFFD93B33)
private val VERT = Color(0xFF2F8F66)
private val NUIT = Color(0xFF0F1A16)
private val DOUX = Color(0xB8F1F4F2)

/** L'écran d'un appel (maquettes telephone-2 et 3, l2-tel-en-cours). Il se ferme quand plus rien ne se passe. */
class EcranAppel : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Le fond de l'appel est toujours sombre : icônes claires en haut et en bas.
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
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
    val fiche = remember(numero) { if (personne == null) Identification.chercher(c, numero) else null }
    val sim = remember(appel) { Moteur.simDuCompte(c, appel.details.accountHandle?.id) }
    val entrant = etat == Call.STATE_RINGING
    var clavier by remember { mutableStateOf(false) }
    var reponses by remember { mutableStateOf(false) }
    // Le bouclier : un inconnu qui insiste, ou qui appelle la nuit (maquette i2-appel-suspect).
    val suspect = remember(numero, entrant) {
        if (entrant && fiche == null && africa.samaos.bouclier.Bouclier.actif(c, africa.samaos.bouclier.Bouclier.Garde.APPELS)) {
            africa.samaos.bouclier.Bouclier.appel(personne != null, Moteur.appelsRecents(c, numero) + 1)
        } else null
    }
    val arnaque = fiche?.genre == Identification.Genre.ARNAQUE
    if (entrant && arnaque) {
        LaunchedEffect(numero) { africa.samaos.bouclier.Bouclier.noter(c, "appel", "Arnaque signalée", "Appel du ${Moteur.formater(numero)} · ${fiche?.motif.orEmpty()}") }
    }
    if (suspect != null) {
        LaunchedEffect(numero) { africa.samaos.bouclier.Bouclier.noter(c, "appel", suspect.titre, "Appel du ${Moteur.formater(numero)} · ${suspect.raison}") }
    }
    // La lueur du haut de l'écran dit d'un coup d'œil à qui on a affaire.
    val lueur = when {
        arnaque || suspect != null -> Color(0x73D9542F)
        fiche?.genre == Identification.Genre.ENTREPRISE || personne != null -> Color(0x525FCB8C)
        else -> Color(0x1FFFFFFF)
    }
    Box(
        Modifier.fillMaxSize().background(NUIT).drawBehind {
            drawRect(Brush.radialGradient(listOf(lueur, Color.Transparent), center = Offset(size.width / 2, 0f), radius = size.width * 1.1f))
        },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(36.dp))
            val texte = when (etat) {
                Call.STATE_RINGING -> "Appel entrant"
                Call.STATE_DIALING, Call.STATE_CONNECTING -> "Appel en cours…"
                Call.STATE_HOLDING -> "En attente"
                Call.STATE_DISCONNECTED -> "Appel terminé"
                else -> null
            }
            if (texte != null) {
                val hd = !entrant && appel.details.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO)
                BasicText(
                    listOfNotNull(texte, sim?.let { "SIM ${it.fente} ${it.operateur}" }, if (hd) "voix HD" else null).joinToString(" · "),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DOUX),
                )
            } else {
                Chrono(appel)
            }
            when {
                personne != null -> {
                    Visage(personne.nom, personne.photo, 112.dp, Modifier.padding(top = 26.dp))
                    BasicText(personne.nom, modifier = Modifier.padding(top = 18.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, color = a.encre, textAlign = TextAlign.Center))
                    BasicText("${Moteur.formater(numero)} · ${personne.type.lowercase()}", modifier = Modifier.padding(top = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = DOUX))
                }
                fiche?.genre == Identification.Genre.ENTREPRISE -> {
                    Box(Modifier.padding(top = 22.dp).size(92.dp).clip(RoundedCornerShape(26.dp)).background(VERT_APPEL), contentAlignment = Alignment.Center) {
                        BasicText(fiche.symbole ?: fiche.nom.orEmpty().take(1), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Light, fontSize = 42.sp, color = Color.White))
                    }
                    BasicText(fiche.nom.orEmpty(), modifier = Modifier.padding(top = 16.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, color = a.encre, textAlign = TextAlign.Center))
                    BasicText(
                        listOfNotNull(Moteur.formater(numero), Moteur.operateurDe(numero)?.lowercase()).joinToString(" · "),
                        modifier = Modifier.padding(top = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = DOUX),
                    )
                    Box(Modifier.padding(top = 14.dp)) { Etiquette("Entreprise vérifiée", Color(0x2E5FCB8C), Color(0xFF9FE3BE), icone = Icones.COCHE) }
                    val deja = remember(numero) { Moteur.appelsVers(c, numero) }
                    if (entrant) Carte(Modifier.padding(top = 26.dp)) {
                        BasicText(
                            "Vérifiée par Sama : le numéro est bien celui de cette entreprise." +
                                (if (deja > 0) " Vous l'avez déjà appelée $deja fois." else "") +
                                (if (fiche.essai) " (Liste d'essai de l'émulateur.)" else ""),
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 22.sp, color = DOUX),
                        )
                    }
                }
                else -> {
                    BasicText(
                        Moteur.secours(numero) ?: Moteur.formater(numero).ifBlank { "Numéro masqué" },
                        modifier = Modifier.padding(top = 14.dp),
                        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Light, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = 0.4.sp, color = a.encre, textAlign = TextAlign.Center),
                    )
                    if (Moteur.secours(numero) != null) {
                        BasicText(Moteur.formater(numero), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = DOUX))
                    }
                    if (arnaque && fiche != null) {
                        Box(Modifier.padding(top = 16.dp)) { Etiquette("Arnaque signalée", Color(0xFFD9542F), Color.White, icone = ICONE_ALERTE, grande = true) }
                        Carte(Modifier.padding(top = 26.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconeTrait(ICONE_PERSONNES, 24.dp, Color(0xFFF4A587))
                                BasicText(
                                    if (fiche.parVous != null) buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Vous avez signalé ce numéro") }
                                        append(" le ${dateLisible(fiche.parVous)}.")
                                    } else buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${fiche.signalements} personne${if (fiche.signalements > 1) "s" else ""}") }
                                        append(" ${if (fiche.signalements > 1) "ont" else "a"} signalé ce numéro cette semaine")
                                        append(fiche.motif?.let { " : $it." } ?: ".")
                                        if (fiche.essai) append(" (Liste d'essai de l'émulateur.)")
                                    },
                                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 22.sp, color = a.encre),
                                )
                            }
                        }
                        Carte(Modifier.padding(top = 10.dp), Color(0x24D9542F)) {
                            BasicText(
                                "Si on vous demande un code, un transfert ou de « valider une opération » : raccrochez. Votre opérateur ne demande jamais votre code.",
                                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFFFFD9CB)),
                            )
                        }
                    } else {
                        Moteur.operateurDe(numero)?.let { op ->
                            BasicText(if (op == "Fixe") "Fixe" else "Mobile · $op", modifier = Modifier.padding(top = 4.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = DOUX))
                        }
                        if (suspect != null) {
                            Carte(Modifier.padding(top = 22.dp), Color(0x24D9542F)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BasicText(suspect.titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFFF4A587)))
                                    BasicText(suspect.raison, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre))
                                }
                            }
                        }
                    }
                }
            }
            if (entrant && suspect == null && !arnaque) {
                val manque = remember(numero) { Moteur.dernierManque(c, numero) }
                if (manque != null && System.currentTimeMillis() - manque < 24 * 3600_000) {
                    BasicText("Vous l'avez manqué à ${Moteur.heure(manque)}", modifier = Modifier.padding(top = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.accent))
                }
            }
            Spacer(Modifier.weight(1f))
            if (entrant) {
                if (reponses) {
                    ReponsesRapides(fermer = { reponses = false }) { texte ->
                        appel.reject(true, texte)
                        reponses = false
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        // Pour une arnaque signalée, c'est Refuser qui bat ; sinon Répondre.
                        Rond("Refuser", ICONE_RACCROCHER, ROUGE, ombre = arnaque || suspect != null, pouls = arnaque || suspect != null) { appel.reject(false, null) }
                        Rond("Répondre", Icones.APPEL, VERT, ombre = !(arnaque || suspect != null), pouls = !(arnaque || suspect != null)) { appel.answer(VideoProfile.STATE_AUDIO_ONLY) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                        if (personne == null && numero.isNotBlank() && fiche?.genre != Identification.Genre.ENTREPRISE) {
                            Pilule(ICONE_BLOQUER, if (arnaque && fiche?.parVous != null) "Bloquer" else "Bloquer et signaler", Modifier.weight(1f)) {
                                Moteur.bloquer(c, numero)
                                if (fiche?.parVous == null) Identification.signaler(c, numero)
                                africa.samaos.bouclier.Bouclier.noter(c, "appel", "Numéro bloqué et signalé", "Appel du ${Moteur.formater(numero)}")
                                appel.reject(false, null)
                            }
                        }
                        Pilule(Icones.MESSAGE, if (personne == null) "SMS" else "Répondre par SMS") { reponses = true }
                    }
                }
            } else if (etat != Call.STATE_DISCONNECTED) {
                if (clavier) {
                    PaveDtmf(appel) { clavier = false }
                } else {
                    Commandes(appel, etat, version, numero) { clavier = true }
                }
                Box(Modifier.padding(top = 24.dp, bottom = 32.dp)) {
                    Rond("Raccrocher", ICONE_RACCROCHER, ROUGE, ombre = true, legende = false) { appel.disconnect() }
                }
            } else {
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

private fun dateLisible(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))

/** Une carte translucide sur le fond de l'appel. */
@Composable
private fun Carte(modifier: Modifier = Modifier, fond: Color = Color(0x14FFFFFF), contenu: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(fond).padding(horizontal = 18.dp, vertical = 16.dp)) { contenu() }
}

/** Un bouton rond de l'appel (Refuser, Répondre, Raccrocher), avec sa légende dessous. */
@Composable
private fun Rond(nom: String, icone: String, fond: Color, ombre: Boolean = false, tourne: Boolean = false, legende: Boolean = true, pouls: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
        // Le bouton conseillé bat doucement : un halo qui s'élargit et s'efface.
        if (pouls) {
            val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pouls")
            val k by t.animateFloat(
                0f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearOutSlowInEasing)),
                label = "k",
            )
            Box(Modifier.size(76.dp).graphicsLayer { scaleX = 1f + 0.5f * k; scaleY = 1f + 0.5f * k }.clip(CircleShape).background(fond.copy(alpha = 0.35f * (1f - k))))
        }
        Box(
            Modifier
                .then(if (ombre) Modifier.shadow(18.dp, CircleShape, ambientColor = fond, spotColor = fond) else Modifier)
                .size(76.dp)
                .clip(CircleShape)
                .background(fond)
                .clickable(onClickLabel = nom, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = nom },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(icone, 32.dp, Color.White, if (tourne) Modifier.rotate(135f) else Modifier, epaisseur = 2.2f) }
        }
        if (legende) BasicText(nom, modifier = Modifier.padding(top = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = DOUX))
    }
}

/** Une action secondaire de l'appel entrant : Bloquer et signaler, SMS. */
@Composable
private fun Pilule(icone: String, texte: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color(0x1AFFFFFF)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        IconeTrait(icone, 19.dp, a.encre)
        BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre))
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
                .size(66.dp)
                .clip(CircleShape)
                .background(if (allume) a.encre else Color(0x1AFFFFFF))
                .clickable(enabled = actif, onClickLabel = nom, role = Role.Switch, onClick = onClick)
                .semantics { contentDescription = nom },
            contentAlignment = Alignment.Center,
        ) { IconeTrait(icone, 26.dp, if (allume) NUIT else if (actif) a.encre else a.encre2) }
        BasicText(nom, modifier = Modifier.padding(top = 6.dp), maxLines = 2, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, color = if (actif) DOUX else a.encre2))
    }
}

/** Le clavier pendant un appel : chaque touche envoie son signal (serveurs vocaux, codes). */
@Composable
private fun PaveDtmf(appel: Call, fermer: () -> Unit) {
    val a = LocalIdentite.current
    var tape by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(tape.ifEmpty { " " }, modifier = Modifier.padding(bottom = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Light, fontSize = 30.sp, color = a.encre))
        Pave(compact = true, fond = Color(0x1AFFFFFF)) { t ->
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
private fun ReponsesRapides(fermer: () -> Unit, envoyer: (String) -> Unit) {
    val a = LocalIdentite.current
    val textes = listOf("Je te rappelle tout de suite.", "Je suis occupé, écris-moi.", "J'arrive.", "Je ne peux pas parler, je t'appelle plus tard.")
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        textes.forEach { t ->
            BasicText(
                t,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x1AFFFFFF)).clickable(role = Role.Button) { envoyer(t) }.padding(16.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre),
            )
        }
        BasicText(
            "Retour",
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = fermer).padding(12.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DOUX),
        )
    }
}
