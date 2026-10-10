package africa.samaos.calculatrice

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Ce que vaut un calcul : un nombre, ou ce qui l'en empêche (« Division par zéro »). */
sealed interface Resultat {
    class Nombre(val v: BigDecimal) : Resultat
    class Impossible(val pourquoi: String) : Resultat
}

private class Erreur(message: String) : Exception(message)

/**
 * Le calcul : l'expression tapée, évaluée avec la priorité des opérations. Les quatre opérations, les puissances
 * entières et le pourcentage restent en décimal exact (pas de 0,30000000000000004) ; les fonctions scientifiques
 * passent par les flottants, arrondis à 13 chiffres.
 */
object Calcul {
    /** Les fonctions, de la plus longue à la plus courte (« sinh⁻¹ » avant « sinh » avant « sin »). */
    val FONCTIONS = listOf("sinh⁻¹", "cosh⁻¹", "tanh⁻¹", "sin⁻¹", "cos⁻¹", "tan⁻¹", "log₁₀", "sinh", "cosh", "tanh", "log₂", "sin", "cos", "tan", "ln", "√", "∛")
    private val OPERATEURS_BINAIRES = listOf("logᵧ", "ʸ√", "+", "−", "×", "÷", "^")
    private val POSTFIXES = listOf("⁻¹", "²", "³", "!", "%")

    private val symboles = DecimalFormatSymbols(Locale.FRENCH).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }

    // ——— Afficher ———

    private val EXPOSANTS = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻')

    /** Un nombre lisible : « 1 234 567,89 » ; très grand ou très petit : « 1,234 × 10¹⁸ ». */
    fun format(v: BigDecimal): String {
        val r = v.round(MathContext(15))
        if (r.signum() == 0) return "0"
        val a = r.abs()
        if (a >= BigDecimal("1e15") || a < BigDecimal("1e-9")) {
            val exposant = a.precision() - a.scale() - 1
            val mantisse = r.movePointLeft(exposant).round(MathContext(10)).stripTrailingZeros()
            return mantisse.toPlainString().replace('.', ',').replace('-', '−') + " × 10" + exposant.toString().map { EXPOSANTS[it] ?: it }.joinToString("")
        }
        // Jusqu'à 15 chiffres en tout (12 après la virgule au plus), le signe moins typographique.
        val decimales = (15 - (a.precision() - a.scale()).coerceAtLeast(1)).coerceIn(0, 12)
        return DecimalFormat("#,##0." + "#".repeat(decimales), symboles).format(r).replace('-', '−').removeSuffix(",")
    }

    /** Le résultat tel qu'on le reprend dans un calcul (« 1234,5 »). */
    fun brut(v: BigDecimal): String {
        val r = v.round(MathContext(15)).stripTrailingZeros()
        val a = r.abs()
        return if (r.signum() != 0 && (a >= BigDecimal("1e15") || a < BigDecimal("1e-9"))) {
            val exposant = a.precision() - a.scale() - 1
            r.movePointLeft(exposant).round(MathContext(14)).stripTrailingZeros().toPlainString().replace('.', ',') + "E" + exposant.toString().replace('-', '−')
        } else {
            r.toPlainString().replace('.', ',').replace('-', '−')
        }
    }

    /** L'expression lisible : les milliers séparés, de l'air autour des opérations. */
    fun joli(e: String): String {
        val avecMilliers = Regex("\\d+").replace(e) { m ->
            val avant = e.getOrNull(m.range.first - 1)
            if (avant == ',' || avant == 'E') m.value else m.value.reversed().chunked(3).joinToString(" ").reversed()
        }
        // Un « − » après une opération ou une parenthèse est un signe, pas une soustraction.
        return Regex("(?<=[\\d)%πe²³!¹])([+−×÷])").replace(avecMilliers) { " ${it.value} " }
            .replace("ʸ√", " ʸ√ ").replace("logᵧ", " logᵧ ").replace("E", "ᴇ")
    }

    // ——— Taper ———

    private fun finitUnFacteur(e: String) = e.isNotEmpty() && (e.last().isDigit() || e.last() in ")πe²³!%¹,")
    private fun finitUneOperation(e: String) = OPERATEURS_BINAIRES.any { e.endsWith(it) } || e.endsWith("E")
    private fun ouvre(e: String) = e.isEmpty() || e.endsWith("(")

    /** Le nombre en cours de frappe, à la fin de l'expression. */
    private fun dernierNombre(e: String): String {
        val m = Regex("[\\d,]+(E−?\\d*)?$").find(e) ?: return ""
        return m.value
    }

    /** Ajouter une touche à l'expression, en ignorant ce qui n'a pas de sens (« ×× », « ,, »). */
    fun ajouter(e: String, t: String): String {
        val dernier = e.lastOrNull()
        return when {
            t.length == 1 && t[0].isDigit() -> when {
                e.endsWith(")") || e.endsWith("π") || (e.endsWith("e") && !e.endsWith("E")) || (dernier != null && dernier in "²³!%¹") -> "$e×$t"
                // « 0 » puis « 5 » : 5, pas 05.
                dernierNombre(e) == "0" -> e.dropLast(1) + t
                else -> e + t
            }
            t == "," -> when {
                dernierNombre(e).contains(',') || dernierNombre(e).contains('E') -> e
                dernier == null || !dernier.isDigit() -> (if (finitUnFacteur(e)) "$e×" else e) + "0,"
                else -> "$e,"
            }
            t == "E" -> if (dernier != null && dernier.isDigit() && !dernierNombre(e).contains('E')) "${e}E" else e
            t == "−" -> when {
                e.endsWith("E") -> "$e−"
                dernier == '−' -> e
                dernier == '+' -> e.dropLast(1) + "−"
                e.endsWith(",") -> e.dropLast(1) + "−"
                else -> "$e−"
            }
            t in OPERATEURS_BINAIRES -> when {
                ouvre(e) -> e
                e.endsWith("−") && (e.length == 1 || e.dropLast(1).let { ouvre(it) || finitUneOperation(it) }) -> e
                finitUneOperation(e) -> OPERATEURS_BINAIRES.first { e.endsWith(it) }.let { e.dropLast(it.length) } + t
                e.endsWith(",") -> e.dropLast(1) + t
                else -> e + t
            }
            t in POSTFIXES -> if (finitUnFacteur(e) && !e.endsWith(",")) e + t else e
            t == ")" -> if (e.count { it == '(' } > e.count { it == ')' } && finitUnFacteur(e)) "$e)" else e
            // Ce qui commence un facteur : une parenthèse, une fonction, une constante, un nombre repris.
            else -> if (finitUnFacteur(e)) "$e×$t" else e + t
        }
    }

    /** La touche « ( ) » : ferme s'il y a une parenthèse ouverte et un nombre avant, sinon ouvre. */
    fun parenthese(e: String): String {
        val ouvertes = e.count { it == '(' } - e.count { it == ')' }
        return if (ouvertes > 0 && finitUnFacteur(e)) ")" else "("
    }

    /** Effacer le dernier élément : un chiffre, ou une fonction entière (« sin( »). */
    fun effacer(e: String): String {
        FONCTIONS.firstOrNull { e.endsWith("$it(") }?.let { return e.dropLast(it.length + 1) }
        listOf("logᵧ", "ʸ√", "⁻¹").firstOrNull { e.endsWith(it) }?.let { return e.dropLast(it.length) }
        return e.dropLast(1)
    }

    /** « ± » : change le signe du nombre en cours. */
    fun signe(e: String): String {
        val n = dernierNombre(e)
        if (n.isEmpty()) return if (e.isEmpty()) "−" else e
        val k = e.length - n.length
        val avant = e.substring(0, k)
        // Un « − » de signe juste avant (au début, après une opération ou une parenthèse) : on l'enlève.
        if (avant.endsWith("−") && avant.dropLast(1).let { it.isEmpty() || ouvre(it) || finitUneOperation(it) }) return avant.dropLast(1) + n
        return "$avant−$n"
    }

    fun aUneOperation(e: String) = e.drop(1).any { it in "+−×÷^%²³!√" } || FONCTIONS.any { e.contains(it) } || e.contains('π') || e.contains("⁻¹") ||
        e.contains('e') || e.contains('E') || e.contains("logᵧ")

    // ——— Évaluer ———

    /** Ce que vaut l'expression ; une fin pas encore tapée (« 12 + ») ne compte pas encore. */
    fun evaluer(e: String, degres: Boolean): Resultat? {
        var s = e
        while (true) {
            val avant = s
            s = s.trimEnd(',')
            OPERATEURS_BINAIRES.firstOrNull { s.endsWith(it) }?.let { s = s.dropLast(it.length) }
            if (s.endsWith("E")) s = s.dropLast(1)
            FONCTIONS.firstOrNull { s.endsWith("$it(") }?.let { s = s.dropLast(it.length + 1) }
            if (s.endsWith("(")) s = s.dropLast(1)
            if (s == avant) break
        }
        if (s.isEmpty()) return null
        s += ")".repeat((s.count { it == '(' } - s.count { it == ')' }).coerceAtLeast(0))
        return try {
            val l = Lecteur(s, degres)
            val v = l.somme()
            if (l.i < s.length) null else Resultat.Nombre(v)
        } catch (x: Erreur) {
            Resultat.Impossible(x.message ?: "Impossible")
        } catch (_: ArithmeticException) {
            Resultat.Impossible("Impossible")
        } catch (_: Exception) {
            null
        }
    }

    private val MC = MathContext.DECIMAL128

    private fun depuis(d: Double): BigDecimal {
        if (d.isNaN() || d.isInfinite()) throw Erreur("Résultat trop grand")
        if (abs(d) < 1e-12) return BigDecimal.ZERO
        return BigDecimal(d).round(MathContext(13)).stripTrailingZeros()
    }

    private class Terme(val v: BigDecimal, val pourcent: Boolean)

    private class Lecteur(val s: String, val degres: Boolean) {
        var i = 0

        private fun prend(t: String): Boolean {
            if (!s.startsWith(t, i)) return false
            i += t.length
            return true
        }

        fun somme(): BigDecimal {
            var v = produit().v
            while (i < s.length && (s[i] == '+' || s[i] == '−')) {
                val plus = s[i++] == '+'
                val t = produit()
                // « 200 + 15 % » : 230 (15 % de 200), comme dans les calculatrices de commerce.
                val d = if (t.pourcent) v.multiply(t.v, MC) else t.v
                v = if (plus) v.add(d, MC) else v.subtract(d, MC)
            }
            return v
        }

        fun produit(): Terme {
            val premier = unaire()
            var v = premier.v
            var seul = true
            while (i < s.length && (s[i] == '×' || s[i] == '÷')) {
                seul = false
                val fois = s[i++] == '×'
                val d = unaire().v
                v = if (fois) v.multiply(d, MC) else {
                    if (d.signum() == 0) throw Erreur("Division par zéro")
                    v.divide(d, MC)
                }
            }
            return Terme(v, seul && premier.pourcent)
        }

        fun unaire(): Terme {
            if (i < s.length && s[i] == '−') {
                i++
                val t = unaire()
                return Terme(t.v.negate(), t.pourcent)
            }
            return puissance()
        }

        fun puissance(): Terme {
            val base = postfixe()
            return when {
                prend("^") -> Terme(puissance(base.v, unaire().v), false)
                prend("ʸ√") -> Terme(racine(base.v, unaire().v), false)
                prend("logᵧ") -> {
                    val b = unaire().v.toDouble()
                    val x = base.v.toDouble()
                    if (x <= 0 || b <= 0 || b == 1.0) throw Erreur("Logarithme impossible")
                    Terme(depuis(kotlin.math.ln(x) / kotlin.math.ln(b)), false)
                }
                else -> base
            }
        }

        fun postfixe(): Terme {
            var v = primaire()
            var pourcent = false
            while (i < s.length) {
                v = when {
                    prend("⁻¹") -> {
                        if (v.signum() == 0) throw Erreur("Division par zéro")
                        BigDecimal.ONE.divide(v, MC)
                    }
                    prend("²") -> v.multiply(v, MC)
                    prend("³") -> v.multiply(v, MC).multiply(v, MC)
                    prend("!") -> factorielle(v)
                    prend("%") -> {
                        pourcent = true
                        v.divide(BigDecimal(100), MC)
                    }
                    else -> break
                }
            }
            return Terme(v, pourcent)
        }

        fun primaire(): BigDecimal {
            if (i >= s.length) throw Erreur("Incomplet")
            when {
                prend("π") -> return BigDecimal("3.14159265358979323846264338327950288")
                prend("(") -> {
                    val v = somme()
                    prend(")")
                    return v
                }
            }
            FONCTIONS.firstOrNull { s.startsWith("$it(", i) }?.let { f ->
                i += f.length + 1
                val x = somme()
                prend(")")
                return fonction(f, x)
            }
            // « e » seul est la constante (« E » marque l'exposant d'un nombre).
            if (prend("e")) return BigDecimal("2.71828182845904523536028747135266250")
            val debut = i
            while (i < s.length && (s[i].isDigit() || s[i] == ',')) i++
            if (i == debut) throw Erreur("Incomplet")
            var n = BigDecimal(s.substring(debut, i).replace(',', '.').let { if (it.endsWith(".")) it.dropLast(1) else it })
            if (i < s.length && s[i] == 'E') {
                i++
                val neg = prend("−")
                val d = i
                while (i < s.length && s[i].isDigit()) i++
                if (i > d) n = n.scaleByPowerOfTen((if (neg) -1 else 1) * s.substring(d, i).toInt())
            }
            return n
        }

        private fun angle(x: BigDecimal) = if (degres) Math.toRadians(x.toDouble()) else x.toDouble()
        private fun versAngle(r: Double) = if (degres) Math.toDegrees(r) else r

        private fun fonction(f: String, x: BigDecimal): BigDecimal {
            val d = x.toDouble()
            return when (f) {
                "sin" -> depuis(kotlin.math.sin(angle(x)))
                "cos" -> depuis(kotlin.math.cos(angle(x)))
                "tan" -> {
                    // tan 90° n'existe pas.
                    if (degres && x.remainder(BigDecimal(180)).abs().compareTo(BigDecimal(90)) == 0) throw Erreur("Tangente impossible")
                    depuis(kotlin.math.tan(angle(x)))
                }
                "sin⁻¹" -> if (d < -1 || d > 1) throw Erreur("Hors de −1 à 1") else depuis(versAngle(kotlin.math.asin(d)))
                "cos⁻¹" -> if (d < -1 || d > 1) throw Erreur("Hors de −1 à 1") else depuis(versAngle(kotlin.math.acos(d)))
                "tan⁻¹" -> depuis(versAngle(kotlin.math.atan(d)))
                "sinh" -> depuis(kotlin.math.sinh(d))
                "cosh" -> depuis(kotlin.math.cosh(d))
                "tanh" -> depuis(kotlin.math.tanh(d))
                "sinh⁻¹" -> depuis(kotlin.math.asinh(d))
                "cosh⁻¹" -> if (d < 1) throw Erreur("Impossible sous 1") else depuis(kotlin.math.acosh(d))
                "tanh⁻¹" -> if (d <= -1 || d >= 1) throw Erreur("Hors de −1 à 1") else depuis(kotlin.math.atanh(d))
                "ln" -> if (d <= 0) throw Erreur("Logarithme d'un nombre positif seulement") else depuis(kotlin.math.ln(d))
                "log₁₀" -> if (d <= 0) throw Erreur("Logarithme d'un nombre positif seulement") else depuis(kotlin.math.log10(d))
                "log₂" -> if (d <= 0) throw Erreur("Logarithme d'un nombre positif seulement") else depuis(kotlin.math.log2(d))
                "√" -> if (x.signum() < 0) throw Erreur("Racine d'un nombre négatif") else depuis(kotlin.math.sqrt(d))
                "∛" -> depuis(kotlin.math.cbrt(d))
                else -> throw Erreur("Inconnu")
            }
        }

        private fun puissance(a: BigDecimal, b: BigDecimal): BigDecimal {
            val entier = b.stripTrailingZeros().scale() <= 0
            if (entier && b.abs() <= BigDecimal(9999)) {
                val n = b.toInt()
                if (n >= 0) return a.pow(n, MC)
                if (a.signum() == 0) throw Erreur("Division par zéro")
                return BigDecimal.ONE.divide(a.pow(-n, MC), MC)
            }
            if (a.signum() < 0) throw Erreur("Puissance impossible")
            return depuis(Math.pow(a.toDouble(), b.toDouble()))
        }

        private fun racine(x: BigDecimal, y: BigDecimal): BigDecimal {
            if (y.signum() == 0) throw Erreur("Racine d'ordre zéro")
            val impair = y.stripTrailingZeros().scale() <= 0 && y.toBigInteger().testBit(0)
            if (x.signum() < 0 && !impair) throw Erreur("Racine d'un nombre négatif")
            val r = Math.pow(abs(x.toDouble()), 1 / y.toDouble())
            return depuis(if (x.signum() < 0) -r else r)
        }

        private fun factorielle(v: BigDecimal): BigDecimal {
            if (v.signum() < 0 || v.stripTrailingZeros().scale() > 0) throw Erreur("Factorielle d'un entier positif seulement")
            val n = v.toInt()
            if (n > 3000) throw Erreur("Résultat trop grand")
            var f = BigInteger.ONE
            for (k in 2..n) f = f.multiply(BigInteger.valueOf(k.toLong()))
            return BigDecimal(f)
        }
    }

    /** Le nombre tiré au sort par « Rand ». */
    fun hasard(): String = BigDecimal(Math.random()).setScale(10, RoundingMode.DOWN).stripTrailingZeros().toPlainString().replace('.', ',')
}
