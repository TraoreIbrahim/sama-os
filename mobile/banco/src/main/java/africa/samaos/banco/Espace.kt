package africa.samaos.banco

import androidx.compose.ui.graphics.Color

// Ce que Banco sait des Espaces : leurs paysages et leurs couleurs.

/** Les trois paysages qu'on choisit pour un Espace (maquette du Nouvel Espace). */
enum class PaysageEspace(val nom: String) { LAGUNE("Lagune"), SAVANE("Savane"), NUIT("Nuit") }

/** Les six couleurs d'Espace, pour reconnaître d'un coup d'œil où l'on est : teinte de jour, teinte de nuit. */
enum class CouleurEspace(val nom: String, private val jour: Long, private val nuit: Long) {
    VERT("Vert", 0xFF2F6B57, 0xFF7CC2A3),
    BLEU("Bleu", 0xFF3D5A99, 0xFF9DB2E3),
    LATERITE("Latérite", 0xFFB5532F, 0xFFE07A52),
    OR("Or", 0xFFE2A62B, 0xFFE9B84D),
    VIOLET("Violet", 0xFF5B3F6E, 0xFFB79BCB),
    BRUN("Brun", 0xFF6E5038, 0xFFC9A383);

    fun couleur(b: Banco) = Color(if (b.sombre) nuit else jour)
}
