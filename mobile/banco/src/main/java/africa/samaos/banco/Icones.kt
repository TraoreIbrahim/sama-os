package africa.samaos.banco

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Les icônes système de Banco, reprises des maquettes : un trait de 1,75 sur une grille de 24. */
object Icones {
    const val REGLAGES = "M4 7h10 M18 7h2 M4 17h4 M12 17h8 M16 5v4 M10 15v4"
    const val WIFI = "M5 10a10 10 0 0 1 14 0 M8 13.5a5.5 5.5 0 0 1 8 0 M12 17.5h.01"
    const val DONNEES = "M7 17l-3-3l3-3 M4 14h11 M17 7l3 3l-3 3 M20 10H9"
    const val ECONOMIE = "M5 19c0-8 5-13 14-14c-1 9-6 14-14 14z M5 19l7-7"
    const val LAMPE = "M8 3h8v4l-2 3v11h-4V10L8 7z M12 13v2"
    const val NUIT = "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"
    const val CADENAS = "M6 11h12v9H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3"
    const val SOLEIL = "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4"
    const val MESSAGE = "M5 5h14a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-8l-4 3.5V16H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"
    const val CLOCHE = "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z M10 20a2 2 0 0 0 4 0"
    const val AVANCER = "M5 12h14 M13 6l6 6l-6 6"
    const val RETOUR = "M15 6l-6 6l6 6"
    const val PLUS = "M12 5v14 M5 12h14"
    const val OEIL = "M2 12s3.5-7 10-7s10 7 10 7s-3.5 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
    const val OEIL_BARRE = "M2 12s3.5-7 10-7s10 7 10 7s-3.5 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z M4 4l16 16"
    const val SIM = "M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M9 12h6v5H9z"
    const val GLOBE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c3 3 3 15 0 18 M12 3c-3 3-3 15 0 18"
    const val BLUETOOTH = "M7 7l10 10l-5 4V3l5 4L7 17"
    const val USB = "M12 3v14 M9 6l3-3l3 3 M7 10v3l5 3 M17 9v3l-5 3 M12 17a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"
    const val HORLOGE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7v5l3 2"
    const val LANGUE = "M4 5h8 M8 3v2 M5 9c1 3 4 5 7 6 M11 5c-1 4-3 7-7 9 M13 21l4-10l4 10 M14.5 17h5"
    const val TELEPHONE = "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M11 18h2"
    const val MISE_A_JOUR = "M4 12a8 8 0 0 1 14-5.3 M18 3v4h-4 M12 8v5 M12 16h.01 M20 12a8 8 0 0 1-14 5.3 M6 21v-4h4"
    const val BOUCLIER = "M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6z M9 12l2 2l4-4"
    const val ALERTE = "M12 3l7 3v5c0 4.5-3 8.3-7 10c-4-1.7-7-5.5-7-10V6z M12 8.5v4 M12 15.5h.01"
    const val CLE = "M8 14a4 4 0 1 0 0-8a4 4 0 1 0 0 8z M11 11l9 9 M16 16l2-2 M18 18l2-2"
    const val IMPRIMANTE = "M7 9V3h10v6 M7 17H5a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1h-2 M7 14h10v7H7z"
    const val NUAGE = "M7 18a4 4 0 0 1-.5-8a6 6 0 0 1 11.5 1.5a3.5 3.5 0 0 1-.5 6.5z"
    const val VIBREUR = "M8 4h8v16H8z M4 8v8 M20 8v8 M2 10v4 M22 10v4"
    const val TEXTE = "M5 6h14 M12 6v13 M9 19h6"
    const val PALETTE = "M12 3a9 9 0 1 0 0 18c1 0 2-1 1.5-2s-.5-2 .5-2.5S17 17 19 16s2-3 2-4a9 9 0 0 0-9-9z M7.5 11h.01 M10 7.5h.01 M14.5 7.5h.01"
    const val FERMER = "M7 7l10 10 M17 7L7 17"
    const val CHEVRON = "M9 6l6 6l-6 6"
    const val COCHE = "M5 12.5l4.5 4.5L19 7"
    const val ENVOYER = "M4 12l16-8l-6 16l-2-7z M12 13l8-9"
    const val SILENCIEUX = "M5 9h3l5-4v14l-5-4H5z M17 9l4 6 M21 9l-4 6"
    const val MUSIQUE = "M9 18V5l12-2v13 M6 21a3 3 0 1 0 0-6a3 3 0 1 0 0 6z M18 19a3 3 0 1 0 0-6a3 3 0 1 0 0 6z"
    const val ECOUTEURS = "M5 12a7 7 0 0 1 14 0v5 M5 12v5 M3 14h4v5H3z M17 14h4v5h-4z"
    const val APPAREIL = "M8 4h8v16H8z M12 17h.01"
    const val ENCEINTE = "M7 3h10v18H7z M12 14a3 3 0 1 0 0 .01 M12 7h.01"
    const val PAUSE = "M8 5v14 M16 5v14"
    const val LECTURE = "M7 5l12 7l-12 7z"
    const val RECULER = "M4 12a8 8 0 1 0 2.3-5.7 M4 4v4h4"
    const val PRECEDENT = "M7 5v14 M18 5l-9 7l9 7z"
    const val SUIVANT = "M17 5v14 M6 5l9 7l-9 7z"
    const val AVANCER_10 = "M20 12a8 8 0 1 1-2.3-5.7 M20 4v4h-4"
    const val AVION = "M10.5 20l1.5-6l-7 1v-2l7-4V4.5a1.5 1.5 0 0 1 3 0V9l7 4v2l-7-1l1.5 6"
    const val POINT_ACCES = "M12 13h.01 M9 16a4 4 0 1 1 6 0 M6.3 18.7a8 8 0 1 1 11.4 0 M12 13v8"
    const val BATTERIE = "M4 8h14a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z M21 11v2 M6 11v2 M9 11v2 M12 11v2"
    const val ECONOMISEUR = "M4 8h14a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z M21 11v2 M11 10l-2 2h4l-2 2"
    const val ROTATION = "M4 12a8 8 0 0 1 14-5.3 M18 3v4h-4 M20 12a8 8 0 0 1-14 5.3 M6 21v-4h4"
    const val NE_PAS_DERANGER = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M8 12h8"
    const val QR = "M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h2v2h-2z M18 14h2 M14 18h2 M18 18h2v2"
    const val PROXIMITE = "M12 12h.01 M8.5 8.5a5 5 0 0 0 0 7 M15.5 8.5a5 5 0 0 1 0 7 M5.6 5.6a9 9 0 0 0 0 12.8 M18.4 5.6a9 9 0 0 1 0 12.8"
    const val MOINS = "M6 12h12"
    const val INFO = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v5 M12 8h.01"
    const val VERS_ESPACE = "M4 7h11 M12 4l3 3l-3 3 M20 17H9 M12 14l-3 3l3 3"
    const val DESINSTALLER = "M5 7h14 M10 7V5h4v2 M7 7l1 12a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2l1-12"
    const val WIDGETS = "M4 4h7v7H4z M13 4h7v4h-7z M13 10h7v10h-7z M4 13h7v7H4z"
    const val CRAYON = "M4 20h4L19 9l-4-4L4 16z M13.5 6.5l4 4"
    const val APPEL = "M6.6 4h2.8l1.4 3.6l-1.9 1.3a10.5 10.5 0 0 0 6.2 6.2l1.3-1.9l3.6 1.4v2.8a1.6 1.6 0 0 1-1.7 1.6A15.5 15.5 0 0 1 5 5.7A1.6 1.6 0 0 1 6.6 4z"
    const val DOCUMENT = "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M14 3v5h5"
    const val BOUSSOLE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M15.5 8.5l-2 5l-5 2l2-5z"
    const val PAYSAGE = "M3 17c3-4 6-4 9-1s6 3 9-1 M3 21h18 M16 7a2.5 2.5 0 1 0 0 .01"
    const val ACCESSIBILITE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7.5h.01 M8 10l4 1l4-1 M12 11v3 M10 17.5l2-3.5l2 3.5"
    const val CORBEILLE = "M5 7h14 M10 7V4.5h4V7 M7 7l1 13h8l1-13 M10.5 11v5 M13.5 11v5"
    const val CLAVIER = "M3 6h18v12H3z M7 10h.01 M11 10h.01 M15 10h.01 M7 14h10"
    const val APPLI = "M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M10.5 18h3"
    const val DOSSIER = "M4 6.5A1.5 1.5 0 0 1 5.5 5H10l2 2h6.5A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z"
    const val PERSONNE = "M12 4a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z M5 20c.8-3.8 3.6-6 7-6s6.2 2.2 7 6"

    // Fichiers, photos, musique
    const val IMAGE = "M4 5h16v14H4z M4 16l5-5l4 4l3-3l4 4 M15.5 8.5h.01"
    const val VIDEO = "M4 7h11v10H4z M15 10.5l5-3v9l-5-3z"
    const val RECHERCHE = "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z M20 20l-4.8-4.8"
    const val OPTIONS = "M12 5.5h.01 M12 12h.01 M12 18.5h.01"
    const val PARTAGER = "M18 8a3 3 0 1 0 0-6a3 3 0 0 0 0 6z M6 15a3 3 0 1 0 0-6a3 3 0 0 0 0 6z M18 22a3 3 0 1 0 0-6a3 3 0 0 0 0 6z M8.6 13.5l6.8 4 M15.4 6.5l-6.8 4"
    const val GRILLE = "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z"
    const val LISTE = "M9 6h11 M9 12h11 M9 18h11 M4.5 6h.01 M4.5 12h.01 M4.5 18h.01"
    const val TELECHARGE = "M12 4v11 M7 10l5 5l5-5 M5 20h14"
    const val RESTAURER = "M4 12a8 8 0 1 0 2.3-5.6 M4 4v4h4"
    const val OUVRIR = "M14 4h6v6 M20 4l-9 9 M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"
    const val COEUR = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
    const val MICRO = "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z M5.5 11a6.5 6.5 0 0 0 13 0 M12 17.5V21"
}

@Composable
fun IconeTrait(chemin: String, taille: Dp, couleur: Color, modifier: Modifier = Modifier, epaisseur: Float = 1.75f) {
    val trace = remember(chemin) { PathParser().parsePathString(chemin).toPath() }
    Canvas(modifier.size(taille)) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawPath(trace, couleur, style = Stroke(width = epaisseur, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
