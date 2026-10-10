package africa.samaos.reglages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import africa.samaos.banco.Icones
import africa.samaos.banco.ModeSimple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le mode simplifié (maquettes quo-04-mode-simplifie, l5-acces-demarrage) : l'allumer ou l'éteindre, les proches
 * à appeler, les six tuiles, et ce qu'il change contre les arnaques. Ouvert aussi par « Besoin d'aide ? ».
 */
@Composable
fun PageModeSimple(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var v by remember { mutableIntStateOf(0) }
    val actif = remember(v, reprise) { ModeSimple.actif(c) }
    val tuiles = remember(v, reprise) { ModeSimple.tuiles(c) }
    PageReglages(titre = "Mode simplifié", sousTitre = "Andika, gros boutons, moins de choix", retour = nav.retour) {
        section(cle = "actif") {
            Ligne("Mode simplifié", detail = if (actif) "L'Accueil montre trois proches et six grandes tuiles" else "Éteint", icone = Icones.GRILLE, fin = Fin.Inter(actif)) {
                ModeSimple.regler(c, !actif)
                v++
            }
            Explication(
                "Pour qui découvre le smartphone : l'heure en grand, trois proches à appeler d'un toucher, six grandes tuiles, " +
                    "écrites en Andika, une police faite pour bien lire. Rien à deviner : le reste est derrière « Besoin d'aide ? ».",
            )
        }
        section("Les proches à appeler", cle = "proches") {
            Explication("Les trois premiers contacts marqués d'une étoile dans Contacts. Un toucher sur l'un d'eux l'appelle.")
            Ligne("Choisir dans Contacts", icone = Icones.PERSONNE) {
                val i = c.packageManager.getLaunchIntentForPackage("africa.samaos.contacts")
                if (i != null) nav.android(i)
            }
        }
        section("Les six tuiles", cle = "tuiles") {
            tuiles.forEachIndexed { place, valeur ->
                val action = ModeSimple.action(valeur)
                val paquet = ModeSimple.paquet(valeur)
                Ligne(
                    "${place + 1}. ${action?.nom ?: paquet?.let { MoteurApplis.nom(c, it) } ?: valeur}",
                    icone = action?.icone,
                    image = if (action == null && paquet != null) remember(paquet) { MoteurApplis.icone(c, paquet, 32) } else null,
                    fin = Fin.Valeur("Changer"),
                ) { nav.aller(Page.TuileSimple(place)) }
            }
            if (tuiles != ModeSimple.DEFAUT) {
                Ligne("Remettre les tuiles de départ", icone = Icones.RESTAURER, fin = Fin.Rien) {
                    ModeSimple.DEFAUT.forEachIndexed { i, t -> ModeSimple.reglerTuile(c, i, t) }
                    v++
                }
            }
        }
        section("Contre les arnaques", cle = "arnaques") {
            Explication(
                "En mode simplifié, une appli ne s'installe que depuis Sugu : aucune demande d'installation d'ailleurs n'est possible, " +
                    "même avec le code. Un proche peut aussi être prévenu quand le bouclier arrête une arnaque.",
            )
            Ligne("Un proche veille sur vous", icone = Icones.BOUCLIER) { nav.aller(Page.Bouclier) }
        }
    }
}

/** Ce que l'on met à une place : une tuile de Sama, ou n'importe quelle appli du téléphone. */
@Composable
fun PageTuileSimple(place: Int, nav: Nav) {
    val c = LocalContext.current
    val actuelle = remember { ModeSimple.tuiles(c)[place] }
    var choisie by remember { mutableStateOf(actuelle) }
    val applis by produceState(emptyList<AppliVue>()) { value = withContext(Dispatchers.IO) { MoteurApplis.applis(c).filter { it.active } } }
    fun choisir(valeur: String) {
        choisie = valeur
        ModeSimple.reglerTuile(c, place, valeur)
        nav.retour()
    }
    PageReglages(titre = "Tuile ${place + 1}", sousTitre = "Ce que fait cette grande tuile de l'Accueil", retour = nav.retour) {
        section("Sama", cle = "sama") {
            ModeSimple.Action.entries.forEach { a ->
                Ligne(a.nom, detail = detailAction(a), icone = a.icone, fin = Fin.Choix(choisie == a.cle)) { choisir(a.cle) }
            }
        }
        section("Une appli", cle = "applis") {
            applis.forEach { a ->
                Ligne(a.nom, image = a.icone, fin = Fin.Choix(choisie == "appli:${a.paquet}")) { choisir("appli:${a.paquet}") }
            }
        }
    }
}

private fun detailAction(a: ModeSimple.Action) = when (a) {
    ModeSimple.Action.APPELER -> "Le Téléphone, avec les appels manqués"
    ModeSimple.Action.MESSAGES -> "Les SMS, avec les nouveaux messages"
    ModeSimple.Action.PHOTO -> "L'appareil photo, prêt à prendre"
    ModeSimple.Action.ARGENT -> "Crédit, forfaits et mobile money"
    ModeSimple.Action.METEO -> "Le temps d'aujourd'hui et des jours à venir"
    ModeSimple.Action.LAMPE -> "Allume ou éteint la lampe d'un toucher"
}
