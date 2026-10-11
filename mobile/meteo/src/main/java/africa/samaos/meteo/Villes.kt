package africa.samaos.meteo

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BarreRetour
import africa.samaos.banco.appli.Glisse
import africa.samaos.banco.appli.GrandTitre
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Rond

private fun st(taille: Float, couleur: Color, poids: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Polices.corps, fontWeight = poids, fontSize = taille.sp, lineHeight = (taille * 1.3f).sp, color = couleur)

/**
 * Les villes suivies, chacune avec le temps qu'il y fait (d'après ses dernières prévisions gardées) et son heure ;
 * glisser une ville vers la gauche la retire. En bas : ajouter, actualiser, partager, d'où viennent les prévisions.
 */
@Composable
internal fun PageVilles(
    villes: List<Ville>,
    active: Int,
    previsions: Map<String, Previsions?>,
    t: Teintes,
    fermer: () -> Unit,
    choisir: (Int) -> Unit,
    retirer: (Ville) -> Unit,
    ajouter: () -> Unit,
    actualiser: () -> Unit,
    partager: () -> Unit,
    aPropos: () -> Unit,
) {
    val a = LocalIdentite.current
    BackHandler(onBack = fermer)
    Column(Modifier.fillMaxSize().ciel(t.nuit).statusBarsPadding()) {
        BarreRetour("Météo", fermer, Modifier.padding(top = 0.dp)) {
            Rond(Icones.PLUS, "Ajouter une ville", plein = true, epaisseur = 2.2f, onClick = ajouter)
        }
        GrandTitre("Vos villes", "${villes.size} ville${if (villes.size > 1) "s" else ""} · un toucher pour y aller", sousBarre = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            villes.forEachIndexed { i, v ->
                val p = previsions[v.cle]
                val ici = i == active
                Glisse(
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)),
                    gauche = Triple(Icones.CORBEILLE, "Retirer", t.laterite),
                    faireGauche = { if (villes.size > 1) retirer(v) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(if (ici) t.carte.copy(alpha = (t.carte.alpha + 0.25f).coerceAtMost(1f)) else t.carte)
                            .clickable(onClickLabel = "Voir ${v.nom}", role = Role.Button) { choisir(i) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconeTrait(if (v.position) Icones.BOUSSOLE else EPINGLE, 16.dp, a.encre2)
                                BasicText(v.nom, maxLines = 1, overflow = TextOverflow.Ellipsis, style = st(18f, a.encre, FontWeight.SemiBold))
                                if (ici) IconeTrait(Icones.COCHE, 16.dp, a.accentTexte, epaisseur = 2.2f)
                            }
                            BasicText(
                                listOfNotNull(p?.let { heureLocale(it) }, v.region.ifBlank { if (v.position) "Position arrondie à 10 km" else null }).joinToString(" · "),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = st(13f, a.encre2),
                            )
                            if (p != null) {
                                BasicText(
                                    Ciel.description(p.actuel.code, p.actuel.jour) + (p.aujourdhui?.let { " · ${degres(it.min)} à ${degres(it.max)}" } ?: ""),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp), style = st(13.5f, a.encre),
                                )
                            } else {
                                BasicText("Pas encore de prévisions", modifier = Modifier.padding(top = 6.dp), style = st(13.5f, a.encre2))
                            }
                        }
                        if (p != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                BasicText(degres(p.actuel.temp), style = TextStyle(fontFamily = Polices.fine, fontWeight = FontWeight(250), fontSize = 40.sp, lineHeight = 44.sp, color = a.encre))
                                IconeTrait(picto(p.actuel.code, p.actuel.jour), 24.dp, a.encre)
                            }
                        }
                    }
                }
            }
            if (villes.size > 1) BasicText("Glissez une ville vers la gauche pour la retirer.", modifier = Modifier.padding(horizontal = 8.dp), style = st(12.5f, a.encre2))
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.carte)) {
                Action(Icones.MISE_A_JOUR, "Actualiser", "Quelques kilo-octets de data", actualiser)
                Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(0.6.dp).background(t.trait))
                Action(Icones.PARTAGER, "Partager la météo", "Une phrase, à envoyer à un proche", partager)
                Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(0.6.dp).background(t.trait))
                Action(Icones.INFO, "D'où viennent les prévisions", null, aPropos)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Action(icone: String, texte: String, second: String?, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = texte, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconeTrait(icone, 22.dp, a.encre2)
        Column {
            BasicText(texte, style = st(16f, a.encre))
            if (second != null) BasicText(second, style = st(13f, a.encre2))
        }
    }
}
