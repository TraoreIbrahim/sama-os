package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Les Réglages des Espaces (depuis la vue des Espaces) : un Espace par rangée. */
@Composable
fun ListeReglagesEspaces(espaces: Espaces, retour: () -> Unit, ouvrir: (Espace) -> Unit) {
    val b = LocalBanco.current
    BackHandler(onBack = retour)
    GabaritReglages(titre = "Réglages", pastille = null, sousTitre = "Les Espaces du téléphone", retour = retour) {
        espaces.liste.forEach { e ->
            Rangee(
                titre = e.nom,
                detail = when {
                    e.id == espaces.actif.id -> "Espace actif"
                    e.estMaison -> "Le profil du téléphone"
                    else -> "Profil à part"
                },
                valeur = e.paysage.nom,
                pastille = e.couleur(b),
                surToucher = { ouvrir(e) },
            )
        }
        Note(
            "Le nom, la couleur et le paysage se changent d'ici. Le code d'un Espace se change seulement " +
                "depuis l'Espace lui-même, et un Espace se supprime depuis Maison.",
        )
    }
}

/** Les réglages d'un Espace (maquette esp-06) : son nom, sa couleur, son paysage, son code, sa suppression. */
@Composable
fun ReglagesEspace(espaces: Espaces, e: Espace, retour: () -> Unit, supprimer: () -> Unit) {
    // Les réglages prennent les couleurs de l'Espace qu'on règle : chaque choix se voit aussitôt.
    CompositionLocalProvider(LocalBanco provides palette(e.paysage, LocalNuit.current)) {
        val contexte = LocalContext.current
        var applisOuvertes by remember(e.id) { mutableStateOf(false) }
        var verrouOuvert by remember(e.id) { mutableStateOf(false) }
        ContenuReglagesEspace(espaces, e, retour, supprimer, ouvrirApplis = { applisOuvertes = true }, ouvrirVerrou = { verrouOuvert = true })
        if (applisOuvertes) ApplisDeLEspace(espaces) { applisOuvertes = false }
        if (verrouOuvert) {
            // Un nouveau niveau : on enchaîne sur le choix du code, puisqu'il doit maintenant y répondre.
            EcranChoixVerrou(
                sousTitre = e.nom,
                choisi = e.verrou,
                choisir = { v ->
                    if (v != e.verrou) {
                        espaces.verrou(e, v)
                        choisirLeCode(contexte, v)
                    }
                },
                retour = { verrouOuvert = false },
            )
        }
    }
}

/** Les applis de l'Espace où l'on se trouve : y ajouter une appli du téléphone, ou l'y masquer. */
@Composable
private fun ApplisDeLEspace(espaces: Espaces, fermer: () -> Unit) {
    val contexte = LocalContext.current
    val scope = rememberCoroutineScope()
    val essentielles = remember { Profils.essentielles(contexte) }
    var applis by remember { mutableStateOf(emptyList<Appli>()) }
    var choisies by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        val liste = withContext(Dispatchers.IO) { Profils.applisDuProfil(contexte) }
        applis = liste.map { it.first }
        choisies = liste.filter { it.second }.map { it.first.paquet }.toSet()
    }
    EcranChoixApplis(
        titre = "Applis",
        sousTitre = "De l'Espace ${espaces.actif.nom}",
        applis = applis,
        choisies = choisies,
        essentielles = essentielles,
        changer = { choisies = it },
        retour = fermer,
        action = "Enregistrer",
        valider = {
            scope.launch {
                espaces.reglerApplis(choisies)
                fermer()
            }
        },
    )
}

@Composable
private fun ContenuReglagesEspace(
    espaces: Espaces,
    e: Espace,
    retour: () -> Unit,
    supprimer: () -> Unit,
    ouvrirApplis: () -> Unit,
    ouvrirVerrou: () -> Unit,
) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val estActif = e.id == espaces.actif.id
    var nom by remember(e.id) { mutableStateOf(e.nom) }
    var message by remember(e.id) { mutableStateOf<String?>(null) }
    fun enregistrerNom(): Boolean {
        message = espaces.renommer(e, nom)
        return message == null
    }
    val quitter = { if (enregistrerNom()) retour() }
    BackHandler(onBack = quitter)

    GabaritReglages(
        titre = e.nom,
        pastille = e.couleur(b),
        sousTitre = if (estActif) "Espace actif" else if (e.estMaison) "Le profil du téléphone" else "Profil à part",
        retour = quitter,
    ) {
        if (!e.estMaison) {
            Rubrique("Nom")
            ChampNom(nom, valider = { enregistrerNom() }) {
                nom = it
                message = null
            }
            message?.let {
                BasicText(
                    it,
                    modifier = Modifier.padding(top = 8.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.lateriteTexte),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        Rubrique("Couleur")
        ChoixCouleur(e.teinte) { espaces.apparence(e, it, e.paysage) }
        Spacer(Modifier.height(20.dp))
        Rubrique("Paysage")
        ChoixPaysage(e.paysage) { espaces.apparence(e, e.teinte, it) }
        Spacer(Modifier.height(24.dp))

        Rubrique("Applis")
        if (estActif) {
            Rangee(
                titre = "Applis de l'Espace",
                detail = "Ajouter ou masquer des applis du téléphone",
                icone = Icones.APPLI,
                surToucher = ouvrirApplis,
            )
        } else {
            Note("Les applis d'un Espace se choisissent depuis l'Espace ${e.nom} lui-même.")
        }
        Spacer(Modifier.height(16.dp))

        Rubrique("Déverrouiller l'Espace avec")
        if (estActif) {
            Rangee(
                titre = "Verrouiller avec",
                detail = e.verrou.detail,
                valeur = e.verrou.nom,
                icone = Icones.CADENAS,
                surToucher = ouvrirVerrou,
            )
            Rangee(
                titre = if (e.estMaison) "Code du téléphone" else "Code de l'Espace",
                detail = "L'empreinte s'y ajoute si le téléphone en a une",
                valeur = if (espaces.securise) "Choisi" else "Aucun",
                icone = Icones.CLAVIER,
                surToucher = { choisirLeCode(contexte, e.verrou) },
            )
            Note("Sama ne vous demandera jamais ce code, ni par appel ni par SMS. Ne le donnez à personne.", b.lateriteTexte)
        } else {
            Note("Son code ne se change que depuis l'Espace ${e.nom} : personne ne peut le changer d'ailleurs.")
        }

        if (!e.estMaison) {
            Spacer(Modifier.height(16.dp))
            Rubrique("Supprimer")
            if (espaces.actif.estMaison) {
                Rangee(
                    titre = "Supprimer l'Espace",
                    detail = "Ses applis, ses comptes et ses fichiers seront effacés",
                    icone = Icones.CORBEILLE,
                    couleur = b.danger,
                    surToucher = supprimer,
                )
            } else {
                Note("Un Espace se supprime depuis Maison.")
            }
        }
    }
}

/**
 * La suppression d'un Espace (comme « Tout effacer ? », maquette l4) : ce qui partira du téléphone, puis
 * le code de Maison s'il y en a un. Une fois supprimé, rien ne se récupère.
 */
@Composable
fun SuppressionEspace(espaces: Espaces, e: Espace, annuler: () -> Unit, fait: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val scope = rememberCoroutineScope()
    var enCours by remember(e.id) { mutableStateOf(false) }
    var echec by remember(e.id) { mutableStateOf(false) }
    BackHandler { if (!enCours) annuler() }

    fun effacer() {
        enCours = true
        echec = false
        scope.launch {
            val ok = espaces.supprimer(e)
            enCours = false
            if (ok) fait() else echec = true
        }
    }
    val confirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) effacer()
    }
    fun demander() {
        // Le code de Maison d'abord : on ne supprime pas un Espace sur un téléphone laissé sans surveillance.
        @Suppress("DEPRECATION")
        val intent = contexte.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent("Supprimer ${e.nom}", "Le code de Maison confirme la suppression.")
        if (intent != null) confirmation.launch(intent) else effacer()
    }

    GabaritReglages(
        titre = "Supprimer ${e.nom} ?",
        pastille = null,
        sousTitre = "Tout ce qu'il contient sera effacé",
        retour = { if (!enCours) annuler() },
        pied = {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Annuler",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !enCours, role = Role.Button, onClick = annuler)
                        .padding(vertical = 10.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    if (enCours) "Suppression…" else "Supprimer",
                    modifier = Modifier.padding(end = 14.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.danger),
                )
                BoutonRond(b.danger, Icones.CORBEILLE, actif = !enCours, description = "Supprimer ${e.nom}", onClick = ::demander)
            }
        },
    ) {
        Rubrique("Partiront du téléphone")
        Rangee(titre = "Ses applis", detail = "Celles installées dans ${e.nom}", icone = Icones.APPLI)
        Rangee(titre = "Ses fichiers", detail = "Photos, documents et téléchargements", icone = Icones.DOSSIER)
        Rangee(titre = "Ses comptes", detail = "Les comptes ouverts dans ${e.nom}", icone = Icones.PERSONNE)
        Note(
            "Maison et les autres Espaces ne sont pas touchés." +
                if (espaces.securise) " Le code de Maison vous sera demandé." else "",
        )
        if (echec) Note("L'Espace n'a pas pu être supprimé.", b.lateriteTexte)
    }
}




