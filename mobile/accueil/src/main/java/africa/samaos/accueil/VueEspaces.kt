package africa.samaos.accueil

import africa.samaos.banco.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

/**
 * La vue des Espaces (maquette esp-02) : chaque Espace en carte, l'actif cerclé de sa couleur,
 * puis « Nouvel Espace ». On l'ouvre en touchant le galet de l'Espace sous l'heure.
 */
@Composable
fun VueEspaces(espaces: Espaces, fermer: () -> Unit, reglages: () -> Unit) {
    val b = LocalBanco.current
    BackHandler(onBack = fermer)
    Box(
        Modifier
            .fillMaxSize()
            .background(b.ciel)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Le soleil se lève sous les cartes, pour ne pas passer entre elles.
        Paysage(astre = Astre(300f, 600f, 50f), collines = listOf(612f, 676f))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BasicText(
                "Espaces",
                modifier = Modifier.padding(start = 24.dp, top = 44.dp),
                style = TextStyle(
                    fontFamily = Polices.monument,
                    fontWeight = FontWeight(600),
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    letterSpacing = (-0.02).em,
                    color = b.encre,
                ),
            )
            BasicText(
                if (espaces.liste.size > 1) "Glissez à trois doigts sur l'Accueil pour passer de l'un à l'autre."
                else "Chaque Espace est un profil à part du téléphone : ses applis, ses comptes, ses fichiers.",
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
            )
            Row(
                Modifier
                    .padding(top = 24.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                espaces.liste.forEach { e ->
                    CarteEspace(e, actif = e.id == espaces.actif.id) {
                        fermer()
                        espaces.aller(e)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Crete()
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(b.sol)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
            ) {
                if (espaces.invite) {
                    // Le téléphone est prêté : le seul geste utile est de le rendre.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) {
                                fermer()
                                espaces.rendre()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeTrait(Icones.RETOUR, 22.dp, b.lateriteTexte)
                        Spacer(Modifier.width(14.dp))
                        BasicText(
                            "Rendre le téléphone",
                            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.lateriteTexte),
                        )
                    }
                    NoteVue("Ce que vous avez fait ici sera effacé.")
                } else when {
                    espaces.peutCreer -> Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) {
                                fermer()
                                espaces.ouvrirCreation()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeTrait(Icones.PLUS, 22.dp, b.lateriteTexte)
                        Spacer(Modifier.width(14.dp))
                        BasicText(
                            "Nouvel Espace",
                            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.lateriteTexte),
                        )
                    }
                    espaces.profils && !espaces.actif.estMaison -> NoteVue("Un nouvel Espace se crée depuis Maison.")
                    espaces.profils -> NoteVue("Le téléphone a atteint son nombre d'Espaces.")
                    else -> NoteVue("Les Espaces arrivent avec Sama OS : chacun y est un profil à part du téléphone.")
                }
                if (espaces.peutPreter && !espaces.invite) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) {
                                fermer()
                                espaces.ouvrirPret()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeTrait(Icones.PERSONNE, 22.dp, b.encre)
                        Spacer(Modifier.width(14.dp))
                        BasicText("Prêter le téléphone", style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre))
                    }
                }
                if (espaces.profils && !espaces.invite) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button, onClick = reglages)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeTrait(Icones.REGLAGES, 22.dp, b.encre)
                        Spacer(Modifier.width(14.dp))
                        BasicText(
                            "Réglages des Espaces",
                            modifier = Modifier.weight(1f),
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre),
                        )
                        IconeTrait(Icones.CHEVRON, 18.dp, b.encre2)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteVue(texte: String) {
    val b = LocalBanco.current
    BasicText(
        texte,
        modifier = Modifier.padding(12.dp),
        style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
    )
}

@Composable
private fun CarteEspace(e: Espace, actif: Boolean, ouvrir: () -> Unit) {
    val b = LocalBanco.current
    val forme = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClickLabel = "Ouvrir l'Espace ${e.nom}", role = Role.Button, onClick = ouvrir),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(forme)
                .then(if (actif) Modifier.border(2.dp, e.couleur(b), forme) else Modifier),
        ) {
            ApercuEspace(e, ouvert = actif || e.estMaison, echelle = maxWidth.value / 390f)
        }
        Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(e.couleur(b), CircleShape))
            Spacer(Modifier.width(6.dp))
            BasicText(e.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre))
        }
        BasicText(
            if (actif) "Espace actif" else "Profil à part",
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = b.encre2),
        )
    }
}

/**
 * Un Espace vu de loin, dans son propre paysage : son heure s'il est ouvert, son cadenas sinon.
 * [echelle] vaut 1 à la taille de l'écran ; les cartes de la vue des Espaces sont plus petites.
 */
@Composable
fun ApercuEspace(e: Espace, ouvert: Boolean, echelle: Float, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalBanco provides palette(e.paysage, LocalNuit.current)) {
        val b = LocalBanco.current
        val maintenant = rememberMaintenant()
        Box(modifier.fillMaxSize()) {
            Paysage()
            if (ouvert) {
                BasicText(
                    maintenant.format(DateTimeFormatter.ofPattern("HH:mm")),
                    modifier = Modifier.padding(start = (22 * echelle).dp, top = (82 * echelle).dp),
                    style = TextStyle(
                        fontFamily = Polices.horloge,
                        fontWeight = FontWeight(200),
                        fontSize = (112 * echelle).sp,
                        lineHeight = (104 * echelle).sp,
                        color = b.encre,
                    ),
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconeTrait(Icones.CADENAS, (36 * echelle).coerceAtLeast(18f).dp, b.encre)
                    if (echelle > 0.6f) {
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(e.couleur(b), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            BasicText(e.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = b.encre))
                        }
                        BasicText("Profil à part", style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = b.encre2))
                    }
                }
            }
        }
    }
}
