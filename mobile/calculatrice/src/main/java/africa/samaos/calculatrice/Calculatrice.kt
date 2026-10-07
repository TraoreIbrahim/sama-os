package africa.samaos.calculatrice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.AvecIdentite
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 1 € = 655,957 F CFA : le taux fixe de la zone franc. */
private val TAUX = BigDecimal("655.957")

private enum class Conversion(val nom: String) { AUCUNE("Calcul"), VERS_EURO("F CFA → €"), VERS_CFA("€ → F CFA") }

/** La Calculatrice de Sama (maquette l2-calc). */
class Calculatrice : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.CalculatriceNuit else Identites.Calculatrice
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) { Ecran() }
        }
    }
}

@Composable
private fun Ecran() {
    val a = LocalIdentite.current
    var expr by rememberSaveable { mutableStateOf("") }
    var resultatAffiche by rememberSaveable { mutableStateOf<String?>(null) }
    var conversion by rememberSaveable { mutableIntStateOf(0) }
    val conv = Conversion.entries[conversion]
    val valeur = Calcul.evaluer(expr)
    fun taper(t: String) {
        if (resultatAffiche != null) {
            // Après « = », un chiffre recommence ; une opération continue avec le résultat.
            expr = if (t in listOf("+", "−", "×", "÷", "%")) resultatAffiche!! else ""
            resultatAffiche = null
        }
        expr = Calcul.ajouter(expr, t)
    }
    Column(Modifier.fillMaxSize().background(a.fond).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Conversion.entries.forEachIndexed { i, cv -> PuceFiltre(cv.nom, i == conversion) { conversion = i } }
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
            BasicText(
                Calcul.joli(expr).ifEmpty { "0" },
                maxLines = 2,
                style = TextStyle(fontFamily = Polices.corps, fontSize = if (resultatAffiche != null) 28.sp else 40.sp, color = if (resultatAffiche != null) a.encre2 else a.encre, textAlign = TextAlign.End),
            )
            if (valeur != null && (resultatAffiche != null || Calcul.aUneOperation(expr))) {
                BasicText(
                    Calcul.format(valeur),
                    modifier = Modifier.semantics { contentDescription = "Résultat" },
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Light, fontSize = 64.sp, lineHeight = 70.sp, color = a.encre, textAlign = TextAlign.End),
                )
            }
            if (conv != Conversion.AUCUNE && valeur != null) {
                val converti = if (conv == Conversion.VERS_EURO) valeur.divide(TAUX, MathContext.DECIMAL64) else valeur.multiply(TAUX)
                BasicText(
                    "≈ " + Calcul.format(converti.setScale(if (conv == Conversion.VERS_EURO) 2 else 0, RoundingMode.HALF_UP)) + if (conv == Conversion.VERS_EURO) " €" else " F CFA",
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = a.accentTexte),
                )
                BasicText("1 € = 655,957 F CFA, taux fixe", style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
            }
            Spacer(Modifier.height(16.dp))
        }
        val touches = listOf(
            listOf("C", "( )", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ",", "⌫", "="),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            touches.forEach { rangee ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rangee.forEach { t ->
                        val operation = t in listOf("÷", "×", "−", "+", "=")
                        Box(
                            Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    when {
                                        t == "=" -> a.accent
                                        operation -> a.voile
                                        t in listOf("C", "( )", "%") -> a.champ
                                        else -> a.surface
                                    },
                                )
                                .clickable(onClickLabel = t, role = Role.Button) {
                                    when (t) {
                                        "C" -> {
                                            expr = ""
                                            resultatAffiche = null
                                        }
                                        "⌫" -> {
                                            resultatAffiche = null
                                            expr = expr.dropLast(1)
                                        }
                                        "=" -> valeur?.let { resultatAffiche = Calcul.brut(it) }
                                        "( )" -> taper(Calcul.parenthese(expr))
                                        else -> taper(t)
                                    }
                                }
                                .semantics { contentDescription = if (t == "⌫") "Effacer" else t },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (t == "⌫") {
                                IconeTrait("M9 5h11v14H9l-6-7z M12 9l5 6 M17 9l-5 6", 26.dp, a.encre)
                            } else {
                                BasicText(t, style = TextStyle(fontFamily = Polices.corps, fontWeight = if (operation) FontWeight.SemiBold else FontWeight.Normal, fontSize = 28.sp, color = if (t == "=") a.surAccent else if (operation) a.accentTexte else a.encre))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Le calcul : l'expression tapée, évaluée avec la priorité des opérations, en nombres décimaux exacts. */
object Calcul {
    private val symboles = DecimalFormatSymbols(Locale.FRENCH).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }

    fun format(v: BigDecimal): String {
        val f = DecimalFormat("#,##0.##########", symboles)
        return f.format(v.round(MathContext(15)))
    }

    /** Le résultat tel qu'on le reprend pour continuer un calcul. */
    fun brut(v: BigDecimal): String = v.round(MathContext(15)).stripTrailingZeros().toPlainString().replace('.', ',')

    fun aUneOperation(e: String) = e.drop(1).any { it in "+−×÷%" }

    /** L'expression lisible : les milliers séparés. */
    fun joli(e: String): String = Regex("\\d+").replace(e) { m ->
        val avant = e.getOrNull(m.range.first - 1)
        if (avant == ',') m.value else m.value.reversed().chunked(3).joinToString(" ").reversed()
    }.replace(Regex("(?<=[\\d)%])([+−×÷])"), " $1 ")

    fun ajouter(e: String, t: String): String {
        val dernier = e.lastOrNull()
        return when {
            t in listOf("+", "×", "÷", "%") && (dernier == null || dernier in "+−×÷(") -> if (dernier != null && dernier in "+−×÷") e.dropLast(1) + t else e
            t == "−" && dernier == '−' -> e
            t == "," && e.takeLastWhile { it.isDigit() || it == ',' }.contains(',') -> e
            t == "," && (dernier == null || !dernier.isDigit()) -> e + "0,"
            else -> e + t
        }
    }

    fun parenthese(e: String): String {
        val ouvertes = e.count { it == '(' } - e.count { it == ')' }
        val dernier = e.lastOrNull()
        return if (ouvertes > 0 && dernier != null && (dernier.isDigit() || dernier == ')' || dernier == '%')) ")" else if (dernier != null && (dernier.isDigit() || dernier == ')')) "×(" else "("
    }

    fun evaluer(e: String): BigDecimal? {
        if (e.isBlank()) return null
        return try {
            var s = e
            // Ce qui n'est pas fini ne compte pas encore : « 12 + » vaut 12.
            while (s.isNotEmpty() && s.last() in "+−×÷(,") s = s.dropLast(1)
            s += ")".repeat((s.count { it == '(' } - s.count { it == ')' }).coerceAtLeast(0))
            val l = Lecteur(s.replace(',', '.'))
            val v = l.somme()
            if (l.i < l.s.length) null else v
        } catch (_: Exception) {
            null
        }
    }

    private class Lecteur(val s: String) {
        var i = 0

        fun somme(): BigDecimal {
            var v = produit()
            while (i < s.length && (s[i] == '+' || s[i] == '−')) {
                val op = s[i++]
                val d = produit()
                v = if (op == '+') v + d else v - d
            }
            return v
        }

        fun produit(): BigDecimal {
            var v = facteur()
            while (i < s.length && (s[i] == '×' || s[i] == '÷')) {
                val op = s[i++]
                val d = facteur()
                v = if (op == '×') v * d else v.divide(d, MathContext.DECIMAL64)
            }
            return v
        }

        fun facteur(): BigDecimal {
            var v = when {
                s[i] == '−' -> {
                    i++
                    facteur().negate()
                }
                s[i] == '(' -> {
                    i++
                    val x = somme()
                    if (i < s.length && s[i] == ')') i++
                    x
                }
                else -> {
                    val debut = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    BigDecimal(s.substring(debut, i))
                }
            }
            // « 15 % » vaut 0,15 ; « 200 + 15 % » ajoute 15 % de 200 dans la plupart des calculatrices : ici, 0,15.
            while (i < s.length && s[i] == '%') {
                i++
                v = v.divide(BigDecimal(100))
            }
            return v
        }
    }
}
