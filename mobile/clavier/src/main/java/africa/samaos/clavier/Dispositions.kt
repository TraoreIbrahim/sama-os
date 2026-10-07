package africa.samaos.clavier

/** Ce que fait une touche qui n'écrit pas. */
enum class Fn { MAJ, EFFACER, SYMBOLES, SYMBOLES_2, LETTRES, EMOJI, ESPACE, ENTREE, LANGUE }

/**
 * Une touche : ce qu'elle écrit ([texte]) ou ce qu'elle fait ([fn]). [largeur] : 1 pour une lettre. Comparée par
 * son contenu : un geste en cours (l'espace qui déplace le curseur) survit quand le clavier se redessine.
 */
data class Touche(val texte: String = "", val fn: Fn? = null, val largeur: Float = 1f, val grande: Boolean = false)

private fun lettres(s: String) = s.map { Touche(it.toString()) }

enum class Langue(val code: String, val nom: String, val detail: String) {
    FR("fr", "Français", "AZERTY"),
    DY("dy", "Julakan", "AZERTY avec ɛ ɔ ɲ ŋ et les tons"),
    EN("en", "English", "QWERTY"),
    ;

    companion object {
        fun de(code: String) = entries.firstOrNull { it.code == code }
    }
}

/** Les pages du clavier : les lettres, deux pages de symboles, les chiffres (téléphone, codes), les emoji. */
enum class Page { LETTRES, SYMBOLES, SYMBOLES_2, CHIFFRES, NOMBRES, EMOJI }

object Dispositions {
    private val MAJ = Touche(fn = Fn.MAJ, largeur = 1.5f)
    private val EFFACER = Touche(fn = Fn.EFFACER, largeur = 1.5f)

    /**
     * La dernière rangée : une seule touche de ponctuation, le point (la virgule et les autres à l'appui long).
     * À côté, [seconde] : la langue sur la page des lettres, « @ » ou « / » dans une adresse, la virgule sur les
     * pages de symboles ; sans elle, l'espace s'élargit.
     */
    private fun bas(page: Fn, libelle: String, seconde: Touche?) = listOfNotNull(
        Touche(libelle, fn = page, largeur = 1.5f),
        seconde,
        Touche(fn = Fn.EMOJI),
        Touche(fn = Fn.ESPACE, largeur = if (seconde == null) 5f else 4f),
        Touche("."),
        Touche(fn = Fn.ENTREE, largeur = 1.5f),
    )

    private fun seconde(virgule: String, plusieursLangues: Boolean) = when {
        virgule != "," -> Touche(virgule)
        plusieursLangues -> Touche(fn = Fn.LANGUE)
        else -> null
    }

    fun lettres(langue: Langue, virgule: String = ",", plusieursLangues: Boolean = false): List<List<Touche>> = when (langue) {
        Langue.EN -> listOf(
            lettres("qwertyuiop"),
            lettres("asdfghjkl"),
            listOf(MAJ) + lettres("zxcvbnm") + EFFACER,
            bas(Fn.SYMBOLES, "?123", seconde(virgule, plusieursLangues)),
        )
        // Le julakan garde l'AZERTY ; ɛ ɔ ɲ ŋ et les tons sont dans le bandeau, au-dessus.
        else -> listOf(
            lettres("azertyuiop"),
            lettres("qsdfghjklm"),
            listOf(MAJ) + lettres("wxcvbn") + Touche("'") + EFFACER,
            bas(Fn.SYMBOLES, "?123", seconde(virgule, plusieursLangues)),
        )
    }

    fun symboles(virgule: String = ","): List<List<Touche>> = listOf(
        lettres("1234567890"),
        lettres("@#€&_-()=%"),
        listOf(Touche("=\\<", fn = Fn.SYMBOLES_2, largeur = 1.5f)) + lettres("\"*':/!?") + EFFACER,
        bas(Fn.LETTRES, "ABC", Touche(virgule)),
    )

    fun symboles2(virgule: String = ","): List<List<Touche>> = listOf(
        lettres("~`|•√π÷×¶∆"),
        lettres("£$¥^°+{}[]"),
        listOf(Touche("?123", fn = Fn.SYMBOLES, largeur = 1.5f)) + lettres("™®©¢<>;") + EFFACER,
        bas(Fn.LETTRES, "ABC", Touche(virgule)),
    )

    /** Le pavé des numéros et des codes : *, # et + à portée de doigt (codes de solde, numéros étrangers). */
    fun chiffres(): List<List<Touche>> = listOf(
        listOf(Touche("1", grande = true), Touche("2", grande = true), Touche("3", grande = true), Touche("*", grande = true)),
        listOf(Touche("4", grande = true), Touche("5", grande = true), Touche("6", grande = true), Touche("#", grande = true)),
        listOf(Touche("7", grande = true), Touche("8", grande = true), Touche("9", grande = true), Touche("+", grande = true)),
        listOf(Touche("ABC", fn = Fn.LETTRES), Touche("0", grande = true), Touche(fn = Fn.EFFACER), Touche(fn = Fn.ENTREE)),
    )

    /** Un nombre (montant, quantité) : le signe et les décimales à la place des codes. */
    fun nombres(): List<List<Touche>> = listOf(
        listOf(Touche("1", grande = true), Touche("2", grande = true), Touche("3", grande = true), Touche("-", grande = true)),
        listOf(Touche("4", grande = true), Touche("5", grande = true), Touche("6", grande = true), Touche(",", grande = true)),
        listOf(Touche("7", grande = true), Touche("8", grande = true), Touche("9", grande = true), Touche(".", grande = true)),
        listOf(Touche(" ", fn = Fn.ESPACE), Touche("0", grande = true), Touche(fn = Fn.EFFACER), Touche(fn = Fn.ENTREE)),
    )

    /** Le bandeau du julakan : les lettres et les tons (posés sur la voyelle qui précède). */
    val JULAKAN = listOf("ɛ", "ɔ", "ɲ", "ŋ", "̀", "́")

    /** Ce qu'offre l'appui long d'une touche : les accents, et les lettres d'ici. */
    fun variantes(t: String, langue: Langue): List<String> {
        val l = t.lowercase()
        val base = when (l) {
            "a" -> listOf("à", "â", "æ", "á", "ä")
            "c" -> listOf("ç")
            "e" -> listOf("é", "è", "ê", "ë", "ɛ")
            "i" -> listOf("î", "ï", "í", "ì")
            "n" -> listOf("ñ", "ɲ", "ŋ")
            "o" -> listOf("ô", "œ", "ö", "ó", "ò", "ɔ")
            "u" -> listOf("ù", "û", "ü", "ú")
            "y" -> listOf("ÿ")
            "'" -> listOf("’", "«", "»", "\"")
            "." -> listOf(",", ";", ":", "!", "?", "…", "-")
            "," -> listOf(";", ":", "…")
            "?" -> listOf("¿")
            "!" -> listOf("¡")
            "-" -> listOf("–", "—", "_")
            "€" -> listOf("F CFA", "$", "£")
            else -> emptyList()
        }
        // En julakan, ɛ ɔ ɲ ŋ passent en premier.
        val ordonne = if (langue == Langue.DY) base.sortedBy { it !in listOf("ɛ", "ɔ", "ɲ", "ŋ") } else base
        return if (t != l && t.length == 1) ordonne.map { it.uppercase() } else ordonne
    }
}
