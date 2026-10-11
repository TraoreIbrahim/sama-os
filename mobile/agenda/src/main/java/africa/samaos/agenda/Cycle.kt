package africa.samaos.agenda

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val ROSE = Color(0xFFC4475E)
private val REGLES = Color(0xFFD9546E)
private val FERTILE = Color(0xFF7FCBB8)
private val OVULATION = Color(0xFF1F8A73)

/** Ce qu'on garde du cycle : les débuts des règles, leur durée habituelle, les symptômes notés jour par jour. */
class DonneesCycle(val debuts: List<LocalDate>, val dureeRegles: Int, val dureeCycle: Int, val symptomes: Map<LocalDate, Set<String>>)

/** Les prévisions du cycle en cours. Des estimations : elles ne remplacent pas une contraception. */
class Previsions(d: DonneesCycle, auj: LocalDate = LocalDate.now()) {
    val dernier: LocalDate = d.debuts.max()
    /** La durée moyenne des derniers cycles (au plus six), sinon celle donnée au départ. */
    val moyenne: Int = d.debuts.sorted().zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.filter { it in 18..45 }.takeLast(6)
        .takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: d.dureeCycle
    val regles = d.dureeRegles
    val jour: Int = ChronoUnit.DAYS.between(dernier, auj).toInt() + 1
    val prochaines: LocalDate = dernier.plusDays(moyenne.toLong())
    val ovulation: LocalDate = prochaines.minusDays(14)
    val fertileDebut: LocalDate = ovulation.minusDays(5)
    val fertileFin: LocalDate = ovulation.plusDays(1)
    val jourOvulation = moyenne - 14

    fun genre(j: Int): Int = when {
        j <= regles -> 0
        j == jourOvulation -> 2
        j in (jourOvulation - 5)..(jourOvulation + 1) -> 1
        else -> 3
    }

    /** La phrase sous « Jour 9 ». */
    fun phrase(auj: LocalDate = LocalDate.now()): String {
        fun dans(n: Long) = if (n == 1L) "dans 1 jour" else "dans $n jours"
        return when {
            jour <= regles -> "Règles · jour $jour"
            auj == ovulation -> "Ovulation probable aujourd'hui"
            !auj.isBefore(fertileDebut) && !auj.isAfter(fertileFin) -> "Période fertile"
            auj.isBefore(fertileDebut) -> "Période fertile ${dans(ChronoUnit.DAYS.between(auj, fertileDebut))}"
            auj.isBefore(prochaines) -> "Règles prévues ${dans(ChronoUnit.DAYS.between(auj, prochaines))}"
            auj == prochaines -> "Règles prévues aujourd'hui"
            else -> "Règles en retard de ${ChronoUnit.DAYS.between(prochaines, auj)} jour${if (ChronoUnit.DAYS.between(prochaines, auj) > 1) "s" else ""}"
        }
    }
}

/**
 * Le cycle est gardé chiffré sur le téléphone (clé du Keystore d'Android, qui ne sort jamais de l'appareil),
 * et ne s'affiche qu'après le code du téléphone, une fois par ouverture de l'appli.
 */
object Cycle {
    private const val CLE = "sama_cycle"
    private fun fichier(c: Context) = File(c.filesDir, "cycle.bin")

    /** Ouvert avec le code depuis que l'appli est au premier plan. */
    var ouvert by mutableStateOf(false)

    private fun cle(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(CLE, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(CLE, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }

    fun lire(c: Context): DonneesCycle? = try {
        val b = fichier(c).takeIf { it.exists() }?.readBytes()
        if (b == null) null else {
            val ch = Cipher.getInstance("AES/GCM/NoPadding")
            ch.init(Cipher.DECRYPT_MODE, cle(), GCMParameterSpec(128, b, 0, 12))
            val o = JSONObject(String(ch.doFinal(b, 12, b.size - 12)))
            val d = o.getJSONArray("d")
            val s = o.optJSONObject("s") ?: JSONObject()
            DonneesCycle(
                (0 until d.length()).map { LocalDate.parse(d.getString(it)) },
                o.optInt("r", 5), o.optInt("c", 28),
                s.keys().asSequence().associate { k -> LocalDate.parse(k) to s.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } },
            ).takeIf { it.debuts.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    }

    fun ecrire(c: Context, d: DonneesCycle) {
        val s = JSONObject()
        d.symptomes.filterValues { it.isNotEmpty() }.forEach { (k, v) -> s.put(k.toString(), JSONArray(v.toList())) }
        val o = JSONObject().put("d", JSONArray(d.debuts.sorted().distinct().map { it.toString() })).put("r", d.dureeRegles).put("c", d.dureeCycle).put("s", s)
        val ch = Cipher.getInstance("AES/GCM/NoPadding")
        ch.init(Cipher.ENCRYPT_MODE, cle())
        fichier(c).writeBytes(ch.iv + ch.doFinal(o.toString().toByteArray()))
    }

    fun effacer(c: Context) {
        fichier(c).delete()
    }

    fun codeDuTelephone(c: Context) = c.getSystemService(KeyguardManager::class.java).isDeviceSecure
}

private val SYMPTOMES = listOf("Crampes", "Fatigue", "Maux de tête", "Humeur", "Ballonnements", "Pertes", "Seins sensibles", "Acné")

/** L'anneau du cycle : un segment par jour ; règles, période fertile, ovulation probable ; aujourd'hui marqué. */
@Composable
fun AnneauCycle(p: Previsions, taille: Dp, epaisseur: Dp, marque: Boolean = true) {
    val a = LocalIdentite.current
    val vide = if (a.sombre) Color(0xFF3A2A30) else Color(0xFFF2DCDF)
    val n = p.moyenne.coerceIn(18, 45)
    val auj = ((p.jour - 1) % n + n) % n
    // L'anneau se dessine d'un tour, puis le jour se pose dessus.
    val trace = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { trace.animateTo(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    Canvas(Modifier.size(taille).semantics { contentDescription = "Jour ${p.jour} du cycle" }) {
        val e = epaisseur.toPx()
        val r = size.minDimension / 2 - e / 2 - (if (marque) e * 0.35f else 0f)
        val centre = Offset(size.width / 2, size.height / 2)
        val pas = 360f / n
        val ecart = if (n > 30) 1.6f else 2.2f
        for (i in 0 until (n * trace.value).toInt().coerceAtMost(n)) {
            val couleur = when (p.genre(i + 1)) {
                0 -> REGLES
                1 -> FERTILE
                2 -> OVULATION
                else -> vide
            }
            drawArc(
                couleur, startAngle = -90f + i * pas + ecart / 2, sweepAngle = pas - ecart, useCenter = false,
                topLeft = Offset(centre.x - r, centre.y - r), size = Size(r * 2, r * 2), style = Stroke(e),
            )
        }
        if (marque && p.jour >= 1 && trace.value > 0.85f) {
            val ang = Math.toRadians((-90.0 + (auj + 0.5) * pas))
            val m = Offset(centre.x + r * cos(ang).toFloat(), centre.y + r * sin(ang).toFloat())
            val pose = ((trace.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
            drawCircle(Color.White, e * 0.78f * pose, m)
            drawCircle(Color(0xFF1C2233), e * 0.78f * pose, m, style = Stroke(e * 0.14f))
            val trait = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1C2233.toInt()
                textSize = e * 0.62f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            if (pose > 0.6f) drawContext.canvas.nativeCanvas.drawText("${p.jour}", m.x, m.y - (trait.ascent() + trait.descent()) / 2, trait)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageCycle(pied: @Composable () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val verrou = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> if (r.resultCode == android.app.Activity.RESULT_OK) Cycle.ouvert = true }
    val protege = remember { Cycle.codeDuTelephone(c) }
    // Sans code sur le téléphone, rien ne peut cacher le cycle : on le dit, et on l'ouvre.
    if (!protege) Cycle.ouvert = true
    var donnees by remember(Cycle.ouvert) { mutableStateOf(if (Cycle.ouvert) Cycle.lire(c) else null) }
    var version by remember { mutableIntStateOf(0) }
    fun garder(d: DonneesCycle) {
        Cycle.ecrire(c, d)
        donnees = d
        version++
    }
    // Les dialogues couvrent toute la page (pas le contenu qui défile) ; Annuler ou Retour les ferment.
    var dialogue by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(if (a.sombre) a.fond else Color(0xFFFFF7F6)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Mon cycle", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = a.encre))
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(if (a.sombre) Color(0x33C4475E) else Color(0xFFF6E4E6)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconeTrait(Icones.CADENAS, 14.dp, if (a.sombre) Color(0xFFF0A3B2) else Color(0xFFA8364D))
                BasicText("Privé", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = if (a.sombre) Color(0xFFF0A3B2) else Color(0xFFA8364D)))
            }
        }
        if (!Cycle.ouvert) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(if (a.sombre) Color(0x33C4475E) else Color(0xFFF6E4E6)), contentAlignment = Alignment.Center) {
                    IconeTrait(Icones.CADENAS, 30.dp, ROSE)
                }
                BasicText("Mon cycle est caché", modifier = Modifier.padding(top = 18.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = a.encre))
                BasicText(
                    "Il s'ouvre avec le code du téléphone. Quelqu'un qui prend votre téléphone ne le voit pas.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2, textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(22.dp))
                BoutonRose("Ouvrir avec mon code") {
                    val km = c.getSystemService(KeyguardManager::class.java)
                    @Suppress("DEPRECATION")
                    val i = km.createConfirmDeviceCredentialIntent("Mon cycle", "Entrez le code du téléphone")
                    if (i != null) verrou.launch(i) else Cycle.ouvert = true
                }
            }
            pied()
            return@Column
        }
        val d = donnees
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (!protege) {
                BasicText(
                    "Le téléphone n'a pas de code : n'importe qui peut ouvrir Mon cycle. Mettez un code dans Réglages › Sécurité.",
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(a.champ).padding(12.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 13.5.sp, lineHeight = 19.sp, color = a.encre2),
                )
            }
            if (d == null) {
                PremierCycle { garder(it) }
            } else {
                CycleEnCours(d, version, { garder(it) }, { dialogue = "autre" }) { dialogue = "effacer" }
            }
            pied()
        }
    }
    val d = donnees
    if (dialogue != null && d != null) {
        AvecIdentite(a.copy(accent = ROSE, accentTexte = if (a.sombre) Color(0xFFF0A3B2) else ROSE, surAccent = Color.White)) {
            // Annuler, Effacer ou un jour choisi : la modale se referme en animation, puis l'action se fait.
            var apres by remember { mutableStateOf<(() -> Unit)?>(null) }
            val fermerDialogue: () -> Unit = {
                dialogue = null
                apres?.invoke()
                apres = null
            }
            if (dialogue == "autre") {
                DialogueAppli("Le premier jour des règles", "Touchez le jour où elles ont commencé.", fermer = fermerDialogue, contenu = {
                    val fermer = LocalFermerFeuille.current
                    ChoixJour(null) { choisi ->
                        // Moins de quinze jours d'écart avec un début noté : c'est ce début qu'on corrige.
                        val proche = d.debuts.firstOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(it, choisi)) < 15 }
                        apres = { garder(DonneesCycle(if (proche != null) d.debuts - proche + choisi else d.debuts + choisi, d.dureeRegles, d.dureeCycle, d.symptomes)) }
                        fermer()
                    }
                }) {
                    val fermer = LocalFermerFeuille.current
                    BoutonTexteAppli("Annuler", style = 't', onClick = fermer)
                }
            } else {
                DialogueAppli("Effacer Mon cycle ?", "Les dates et les symptômes notés disparaissent de ce téléphone. On ne peut pas les retrouver.", fermer = fermerDialogue) {
                    val fermer = LocalFermerFeuille.current
                    BoutonTexteAppli("Annuler", style = 't', onClick = fermer)
                    BoutonTexteAppli("Effacer") {
                        apres = {
                            Cycle.effacer(c)
                            donnees = null
                        }
                        fermer()
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun BoutonRose(texte: String, modifier: Modifier = Modifier, actif: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(if (actif) ROSE else ROSE.copy(alpha = 0.4f))
            .clickable(enabled = actif, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(texte, maxLines = 1, softWrap = false, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)) }
}

/** La première fois : la date des dernières règles, et deux durées qu'on peut laisser telles quelles. */
@Composable
private fun PremierCycle(commencer: (DonneesCycle) -> Unit) {
    val a = LocalIdentite.current
    var debut by remember { mutableStateOf<LocalDate?>(null) }
    var cycle by remember { mutableIntStateOf(28) }
    var regles by remember { mutableIntStateOf(5) }
    BasicText("Quand ont commencé vos dernières règles ?", modifier = Modifier.padding(top = 14.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, color = a.encre))
    BasicText("Touchez le premier jour. Une date approchée suffit : les prévisions s'affinent à chaque cycle.", modifier = Modifier.padding(top = 6.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2))
    ChoixJour(debut, Modifier.padding(top = 14.dp)) { debut = it }
    Reglage("Durée habituelle du cycle", "$cycle jours", { cycle = (cycle - 1).coerceAtLeast(20) }, { cycle = (cycle + 1).coerceAtMost(45) })
    Reglage("Durée des règles", "$regles jours", { regles = (regles - 1).coerceAtLeast(2) }, { regles = (regles + 1).coerceAtMost(10) })
    Spacer(Modifier.height(18.dp))
    BoutonRose("Commencer", Modifier.fillMaxWidth(), actif = debut != null) { debut?.let { commencer(DonneesCycle(listOf(it), regles, cycle, emptyMap())) } }
    Avertissement()
}

/** Les six dernières semaines, pour toucher un jour passé. */
@Composable
private fun ChoixJour(choisi: LocalDate?, modifier: Modifier = Modifier, choisir: (LocalDate) -> Unit) {
    val a = LocalIdentite.current
    val auj = LocalDate.now()
    val lundi = auj.minusDays(41).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.surface).padding(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach { BasicText(it, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = a.encre2, textAlign = TextAlign.Center)) }
        }
        var semaine = lundi
        while (!semaine.isAfter(auj)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for (k in 0 until 7) {
                    val j = semaine.plusDays(k.toLong())
                    val possible = !j.isAfter(auj)
                    Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (j == choisi) ROSE else Color.Transparent)
                                .clickable(enabled = possible, role = Role.RadioButton) { choisir(j) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                if (j.dayOfMonth == 1) "1 ${j.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.FRENCH).removeSuffix(".")}" else "${j.dayOfMonth}",
                                style = TextStyle(
                                    fontFamily = Polices.corps, fontSize = if (j.dayOfMonth == 1) 10.sp else 14.sp, fontWeight = if (j == auj) FontWeight.Bold else FontWeight.Normal,
                                    color = when { j == choisi -> Color.White; !possible -> a.encre2.copy(alpha = 0.35f); else -> a.encre }, textAlign = TextAlign.Center,
                                ),
                            )
                        }
                    }
                }
            }
            semaine = semaine.plusWeeks(1)
        }
    }
}

@Composable
private fun Reglage(nom: String, valeur: String, moins: () -> Unit, plus: () -> Unit) {
    val a = LocalIdentite.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(nom, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.5.sp, color = a.encre))
        Rond("M5 12h14", "Moins", moins)
        BasicText(valeur, modifier = Modifier.width(84.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = a.encre, textAlign = TextAlign.Center))
        Rond(Icones.PLUS, "Plus", plus)
    }
}

@Composable
private fun Rond(icone: String, nom: String, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Box(Modifier.size(38.dp).clip(CircleShape).background(a.champ).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        IconeTrait(icone, 18.dp, a.encre)
    }
}

@Composable
private fun Avertissement() {
    val a = LocalIdentite.current
    BasicText(
        "Ces données restent sur ce téléphone, chiffrées, cachées derrière votre code. Les prévisions sont des estimations : elles ne remplacent pas une contraception, ni l'avis d'un soignant.",
        modifier = Modifier.padding(top = 16.dp),
        style = TextStyle(fontFamily = Polices.corps, fontSize = 12.5.sp, lineHeight = 18.sp, color = a.encre2),
    )
}

private fun dateCourte(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleEnCours(d: DonneesCycle, version: Int, garder: (DonneesCycle) -> Unit, autreJour: () -> Unit, effacer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val auj = LocalDate.now()
    val p = remember(d, version) { Previsions(d) }
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        AnneauCycle(p, 280.dp, 22.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("Aujourd'hui", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (a.sombre) Color(0xFFF0A3B2) else Color(0xFFA8364D)))
            BasicText("Jour ${p.jour}", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Light, fontSize = 46.sp, lineHeight = 50.sp, color = a.encre))
            BasicText(p.phrase(), modifier = Modifier.width(170.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 19.sp, color = a.encre2, textAlign = TextAlign.Center))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
        listOf(REGLES to "Règles", FERTILE to "Fertile", OVULATION to "Ovulation probable").forEach { (coul, nom) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(coul))
                BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 12.5.sp, color = a.encre2))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(20.dp)).background(a.surface).padding(vertical = 14.dp)) {
        listOf(dateCourte(p.prochaines) to "Prochaines règles", "${p.moyenne} jours" to "Cycle moyen", "${p.regles} jours" to "Règles").forEachIndexed { i, (v, n) ->
            if (i > 0) Box(Modifier.width(1.dp).height(38.dp).background(a.trait))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(v, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = a.encre))
                BasicText(n, style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, color = a.encre2))
            }
        }
    }
    val enCours = p.jour <= p.regles
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BoutonRose(if (enCours) "Règles en cours · jour ${p.jour}" else "Mes règles ont commencé", Modifier.weight(1f), actif = !enCours) {
            // Moins de quinze jours après le dernier début, c'est une correction de ce début, pas un nouveau cycle.
            val debuts = if (p.jour < 15) d.debuts - p.dernier + auj else d.debuts + auj
            garder(DonneesCycle(debuts, d.dureeRegles, d.dureeCycle, d.symptomes))
        }
        Box(
            Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(if (a.sombre) Color(0x33C4475E) else Color(0xFFF6E4E6))
                .clickable(role = Role.Button, onClick = autreJour).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { BasicText("Un autre jour", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (a.sombre) Color(0xFFF0A3B2) else Color(0xFFA8364D))) }
    }
    BasicText(
        "Aujourd'hui, je note", modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre),
    )
    val notes = d.symptomes[auj].orEmpty()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SYMPTOMES.forEach { s ->
            val oui = s in notes
            BasicText(
                s,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (oui) ROSE else a.champ)
                    .clickable(role = Role.Checkbox) {
                        val m = d.symptomes.toMutableMap()
                        m[auj] = if (oui) notes - s else notes + s
                        garder(DonneesCycle(d.debuts, d.dureeRegles, d.dureeCycle, m))
                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (oui) Color.White else a.encre),
            )
        }
    }
    // Les derniers cycles, du plus récent au plus ancien.
    val faits = d.debuts.sorted().zipWithNext { x, y -> x to ChronoUnit.DAYS.between(x, y) }.reversed().take(4)
    if (faits.isNotEmpty()) {
        BasicText("Derniers cycles", modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.surface)) {
            faits.forEachIndexed { i, (debut, n) ->
                if (i > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(a.trait))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    BasicText("Depuis le ${dateCourte(debut)}", modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre))
                    BasicText("$n jours", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = a.encre2))
                }
            }
        }
    }
    Avertissement()
    BasicText(
        "Effacer mes données du cycle",
        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = effacer).padding(vertical = 8.dp, horizontal = 4.dp),
        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ROSE),
    )
}
