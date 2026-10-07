package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Le premier démarrage de Sama (maquettes dem-01 à dem-06), à l'allumage d'un téléphone neuf.
 *
 * Il passe avant l'Accueil (priorité plus haute sur l'écran d'accueil) tant que le téléphone n'est pas
 * configuré ; une fois fini, il le marque configuré et se désactive, comme l'assistant d'Android.
 * Dans un nouvel Espace, il n'y a rien à refaire : il s'efface aussitôt.
 */
class DemarrageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val revoir = intent.getBooleanExtra(REVOIR, false)
        if (Profils.monId() != 0 || (!revoir && dejaConfigure())) {
            terminer()
            return
        }
        enableEdgeToEdge()
        setContent {
            val nuit = isSystemInDarkTheme()
            // Plein soleil, choisi dès l'accessibilité du démarrage, se voit tout de suite (barre d'état comprise).
            val banco = palette(PaysageEspace.LAGUNE, nuit, rememberPleinSoleil())
            androidx.compose.runtime.SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !banco.sombre
                    isAppearanceLightNavigationBars = !banco.sombre
                }
            }
            AvecBanco(banco, LocalNuit provides nuit) {
                Demarrage(terminer = ::terminer)
            }
        }
    }

    private fun dejaConfigure(): Boolean =
        Settings.Global.getInt(contentResolver, Settings.Global.DEVICE_PROVISIONED, 0) == 1 &&
            Settings.Secure.getInt(contentResolver, "user_setup_complete", 0) == 1

    /** Le téléphone est prêt : on le dit à Android, on s'efface, on ouvre l'Accueil. */
    private fun terminer() {
        try {
            Settings.Global.putInt(contentResolver, Settings.Global.DEVICE_PROVISIONED, 1)
            Settings.Secure.putInt(contentResolver, "user_setup_complete", 1)
        } catch (_: Exception) {
            // Sans le droit d'écrire ces réglages, Android garde son état ; l'Accueil fonctionne quand même.
        }
        packageManager.setComponentEnabledSetting(
            ComponentName(this, DemarrageActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        /** Pour revoir le premier démarrage sur un téléphone déjà configuré (démonstration). */
        const val REVOIR = "revoir"
    }
}

private enum class Etape { BIENVENUE, LANGUE, RESEAU, COMPTE, CODE, ACCES }

@Composable
private fun Demarrage(terminer: () -> Unit) {
    var etape by rememberSaveable { mutableStateOf(Etape.BIENVENUE) }
    fun aller(e: Etape) {
        etape = e
    }
    // On ne quitte pas le premier démarrage par Retour : on revient à l'étape d'avant.
    BackHandler {
        when {
            etape == Etape.ACCES -> aller(Etape.BIENVENUE)
            etape.ordinal > 0 -> aller(Etape.entries[etape.ordinal - 1])
        }
    }
    when (etape) {
        Etape.BIENVENUE -> Bienvenue(accessibilite = { aller(Etape.ACCES) }) { aller(Etape.LANGUE) }
        Etape.ACCES -> AccesDemarrage(retour = { aller(Etape.BIENVENUE) }) { aller(Etape.LANGUE) }
        Etape.LANGUE -> Langue(retour = { aller(Etape.BIENVENUE) }) { aller(Etape.RESEAU) }
        Etape.RESEAU -> Reseau(retour = { aller(Etape.LANGUE) }) { aller(Etape.COMPTE) }
        Etape.COMPTE -> Compte(retour = { aller(Etape.RESEAU) }) { aller(Etape.CODE) }
        Etape.CODE -> CodeDeverrouillage(retour = { aller(Etape.COMPTE) }, terminer = terminer)
    }
}

/** dem-01 : « Bonjour » dans les langues du pays, l'appel d'urgence et l'accessibilité toujours à portée. */
@Composable
private fun Bienvenue(accessibilite: () -> Unit, commencer: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    Box(Modifier.fillMaxSize().background(b.ciel).pointerInput(Unit) { detectTapGestures { } }) {
        Paysage(astre = Astre(292f, 360f, 84f), collines = listOf(440f, 520f, 600f))
        Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 28.dp)) {
            Elephant(taille = 44.dp, couleur = b.laterite, fond = b.ciel)
            BasicText(
                "Bonjour",
                modifier = Modifier.padding(top = 40.dp),
                style = TextStyle(
                    fontFamily = Polices.monument,
                    fontWeight = FontWeight(600),
                    fontSize = 64.sp,
                    lineHeight = 68.sp,
                    letterSpacing = (-0.02).em,
                    color = b.encre,
                ),
            )
            BasicText(
                "Hello · Habari · I ni ce · Nanga def · Sannu",
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = b.encre2),
            )
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            Crete()
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(b.sol)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            ) {
                BasicText(
                    "Appel d'urgence",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Button) {
                            Systeme.ouvrir(contexte, Intent("com.android.phone.EmergencyDialer.DIAL"))
                        }
                        .padding(vertical = 10.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.lateriteTexte),
                )
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(b.sol2)
                            .clickable(onClickLabel = "Accessibilité", role = Role.Button, onClick = accessibilite)
                            .semantics { contentDescription = "Accessibilité" },
                        contentAlignment = Alignment.Center,
                    ) { IconeTrait(Icones.ACCESSIBILITE, 22.dp, b.encre) }
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        "Commencer",
                        modifier = Modifier.padding(end = 14.dp),
                        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.encre),
                    )
                    Avancer(actif = true, description = "Commencer", onClick = commencer)
                }
            }
        }
    }
}

/**
 * L'accessibilité avant de commencer (maquette l5-acces-demarrage) : ce qui aide à lire et à toucher,
 * réglé d'un geste et appliqué tout de suite. Le reste est dans Réglages › Accessibilité.
 */
@Composable
private fun AccesDemarrage(retour: () -> Unit, continuer: () -> Unit) {
    val contexte = LocalContext.current
    val cr = contexte.contentResolver
    var grand by remember { mutableStateOf(Settings.System.getFloat(cr, Settings.System.FONT_SCALE, 1f) > 1.05f) }
    var loupe by remember { mutableStateOf(Settings.Secure.getInt(cr, "accessibility_display_magnification_enabled", 0) == 1) }
    var soleil by remember { mutableStateOf(Settings.Secure.getInt(cr, CLE_PLEIN_SOLEIL, 0) == 2) }
    GabaritDemarrage(
        titre = "Accessibilité",
        retour = retour,
        pied = { PiedDemarrage(gauche = "Toutes les options", surGauche = { Systeme.ouvrir(contexte, Settings.ACTION_ACCESSIBILITY_SETTINGS) }, action = "Continuer", surAction = continuer) },
    ) {
        Note("Réglez le téléphone avant de commencer. Tout se change ensuite dans Réglages › Accessibilité.")
        Spacer(Modifier.height(8.dp))
        RangeeInter("Texte plus grand", "Tout le texte du téléphone, un tiers plus grand", Icones.TEXTE, grand) {
            grand = !grand
            try {
                Settings.System.putFloat(cr, Settings.System.FONT_SCALE, if (grand) 1.3f else 1f)
            } catch (_: Exception) {
            }
        }
        RangeeInter("Loupe", "Toucher trois fois l'écran pour agrandir", Icones.RECHERCHE, loupe) {
            loupe = !loupe
            try {
                Settings.Secure.putInt(cr, "accessibility_display_magnification_enabled", if (loupe) 1 else 0)
            } catch (_: Exception) {
            }
        }
        RangeeInter("Plein soleil", "Contraste renforcé, lisible dehors", Icones.SOLEIL, soleil) {
            soleil = !soleil
            try {
                Settings.Secure.putInt(cr, CLE_PLEIN_SOLEIL, if (soleil) 2 else 0)
            } catch (_: Exception) {
            }
        }
    }
}

/** Une rangée avec un interrupteur : toute la rangée bascule. */
@Composable
private fun RangeeInter(titre: String, detail: String, icone: String, allume: Boolean, basculer: () -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .deborder(12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = titre, role = Role.Switch, onClick = basculer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTrait(icone, 22.dp, b.encre)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp)) {
            BasicText(titre, style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre))
            BasicText(detail, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = b.encre2))
        }
        Interrupteur(allume)
    }
}

/** Une langue du premier démarrage : celles qu'Android ne traduit pas encore retombent sur le français. */
private class LangueTel(val nom: String, val salut: String, val locale: Locale, val traduite: Boolean)

private val LANGUES = listOf(
    LangueTel("Français", "Bonjour", Locale.forLanguageTag("fr-CI"), true),
    LangueTel("English", "Hello", Locale.forLanguageTag("en-GB"), true),
    LangueTel("Kiswahili", "Habari", Locale.forLanguageTag("sw"), true),
    LangueTel("Julakan", "I ni ce", Locale.forLanguageTag("dyu-CI"), false),
    LangueTel("Wolof", "Nanga def", Locale.forLanguageTag("wo"), false),
    LangueTel("Hausa", "Sannu", Locale.forLanguageTag("ha"), false),
)

/** dem-02 : la langue du téléphone, appliquée tout de suite. */
@Composable
private fun Langue(retour: () -> Unit, suivant: () -> Unit) {
    val contexte = LocalContext.current
    var choisie by rememberSaveable {
        val actuelle = contexte.resources.configuration.locales[0]
        mutableStateOf(LANGUES.indexOfFirst { it.locale.language == actuelle.language }.coerceAtLeast(0))
    }
    GabaritDemarrage(
        titre = "Choisissez\nvotre langue",
        retour = retour,
        pied = { PiedDemarrage(gauche = "Modifiable dans Réglages", action = "Suivant", surAction = suivant) },
    ) {
        LANGUES.forEachIndexed { i, l ->
            val detail = when {
                i == 0 -> "${l.salut} · par défaut"
                !l.traduite -> "${l.salut} · traduction en cours"
                else -> l.salut
            }
            RangeeChoix(l.nom, detail, choisie = i == choisie) {
                choisie = i
                langueDuTelephone(contexte, l)
            }
        }
        if (!LANGUES[choisie].traduite) {
            Note("En attendant sa traduction, le téléphone s'affichera en français.")
        }
    }
}

/** Change la langue de tout le téléphone ; le français suit en secours. */
private fun langueDuTelephone(contexte: Context, l: LangueTel) {
    val francais = Locale.forLanguageTag("fr-CI")
    val liste = if (l.locale == francais) LocaleList(francais) else LocaleList(l.locale, francais)
    try {
        val lm = contexte.getSystemService("locale") ?: return
        lm.javaClass.getMethod("setSystemLocales", LocaleList::class.java).invoke(lm, liste)
    } catch (_: Exception) {
        // Sans ce droit, la langue se change dans les Réglages.
    }
}

/** dem-03 : la SIM pour Internet, et le Wi-Fi conseillé pour la suite. */
@Composable
private fun Reseau(retour: () -> Unit, suivant: () -> Unit) {
    val contexte = LocalContext.current
    val sims = remember { Telephonie.sims(contexte) }
    var donnees by remember { mutableStateOf(Telephonie.simDesDonnees(contexte)) }
    val etat = remember { Systeme.lire(contexte) }
    GabaritDemarrage(
        titre = "Réseau",
        retour = retour,
        pied = { PiedDemarrage(gauche = "Passer", surGauche = suivant, action = "Suivant", surAction = suivant) },
    ) {
        Rubrique("Internet mobile")
        if (sims.isEmpty()) {
            Note("Pas de carte SIM pour l'instant. Vous pourrez en ajouter une plus tard.")
        } else {
            sims.forEach { sim ->
                RangeeChoix("SIM ${sim.place} · ${sim.operateur}", "Pour Internet hors Wi-Fi", choisie = sim.id == donnees) {
                    donnees = sim.id
                    Telephonie.choisirSimDesDonnees(contexte, sim.id)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Rubrique("Wi-Fi")
        Rangee(
            titre = if (etat.wifiConnecte) "Connecté" else "Choisir un Wi-Fi",
            detail = if (etat.wifiConnecte) "Touchez pour en choisir un autre" else null,
            icone = Icones.WIFI,
            surToucher = { Systeme.panneauWifi(contexte) },
        )
        Note("Un Wi-Fi est conseillé : la suite se télécharge sans toucher à votre forfait.")
    }
}

/** dem-04 : le compte Sama. Le service n'est pas encore ouvert : on continue sans compte. */
@Composable
private fun Compte(retour: () -> Unit, suivant: () -> Unit) {
    val b = LocalBanco.current
    var numero by rememberSaveable { mutableStateOf("") }
    var pasEncore by remember { mutableStateOf(false) }
    GabaritDemarrage(
        titre = "Votre compte\nSama",
        retour = retour,
        pied = {
            PiedDemarrage(
                gauche = "Sans compte",
                surGauche = suivant,
                action = "Recevoir le code",
                actif = numero.length >= 8,
                surAction = { pasEncore = true },
            )
        },
    ) {
        Paragraphe("Le même compte sur ce téléphone, votre ordinateur Sama, Sugu et Sama Grenier.")
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(b.sol2, RoundedCornerShape(16.dp))
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.background(b.sol, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            ) { BasicText("+225", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre)) }
            Spacer(Modifier.width(12.dp))
            val style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre)
            BasicTextField(
                value = numero,
                onValueChange = { v -> numero = v.filter { it.isDigit() }.take(10) },
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(b.laterite),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
                decorationBox = { champ ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (numero.isEmpty()) BasicText("Numéro de téléphone", style = style.copy(color = b.encre2))
                        champ()
                    }
                },
            )
        }
        Note("Un code vous sera envoyé par SMS. Vos données sont hébergées en Afrique.")
        Note("Ne donnez ce code à personne : Sama ne vous le demandera jamais, ni par appel ni par SMS.", b.lateriteTexte)
        if (pasEncore) {
            Note(
                "Le compte Sama n'est pas encore ouvert. Continuez sans compte : vous pourrez l'ajouter plus tard dans Réglages.",
                b.encre,
            )
        }
    }
}

/** dem-06 : le code du téléphone, choisi avec l'écran d'Android, au niveau « Code ». */
@Composable
private fun CodeDeverrouillage(retour: () -> Unit, terminer: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val choix = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (contexte.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true) terminer()
    }
    fun choisir(v: VerrouEspace) = choix.launch(
        Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD).putExtra(DevicePolicyManager.EXTRA_PASSWORD_COMPLEXITY, v.complexite),
    )
    GabaritDemarrage(
        titre = "Code de\ndéverrouillage",
        retour = retour,
        pied = { PiedDemarrage(gauche = "Plus tard", surGauche = terminer, action = "Choisir le code", surAction = { choisir(VerrouEspace.CODE) }) },
    ) {
        Paragraphe("Il protège le téléphone et chiffre aussi son contenu. 4 chiffres ou plus, sans suite trop simple comme 1234.")
        Note("Sama ne vous demandera jamais ce code, ni par appel ni par SMS. Ne le donnez à personne.", b.lateriteTexte)
        Spacer(Modifier.height(8.dp))
        BasicText(
            "Schéma ou mot de passe",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button) { choisir(VerrouEspace.SCHEMA) }
                .padding(vertical = 10.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.lateriteTexte),
        )
    }
}

/** Le gabarit des étapes : le retour, un titre sur deux lignes et son soleil ; le sol qui défile. */
@Composable
private fun GabaritDemarrage(
    titre: String,
    retour: () -> Unit,
    pied: @Composable () -> Unit,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val b = LocalBanco.current
    Box(Modifier.fillMaxSize().background(b.ciel).pointerInput(Unit) { detectTapGestures { } }) {
        Paysage(astre = Astre(318f, 118f, 42f), collines = listOf(214f))
        Column(Modifier.statusBarsPadding().padding(start = 12.dp, end = 24.dp, top = 4.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Revenir", role = Role.Button, onClick = retour)
                    .semantics { contentDescription = "Revenir" },
                contentAlignment = Alignment.Center,
            ) { IconeTrait(Icones.RETOUR, 24.dp, b.encre) }
            BasicText(
                titre,
                modifier = Modifier.padding(start = 12.dp, top = 16.dp),
                style = TextStyle(
                    fontFamily = Polices.monument,
                    fontWeight = FontWeight(600),
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    letterSpacing = (-0.02).em,
                    color = b.encre,
                ),
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 176.dp)) {
            Crete()
            Column(
                Modifier
                    .fillMaxSize()
                    .background(b.sol)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    content = contenu,
                )
                Box(Modifier.padding(horizontal = 12.dp)) { pied() }
            }
        }
    }
}

/** Le bas d'une étape : un lien ou une mention à gauche, l'action et le bouton Avancer à droite. */
@Composable
private fun PiedDemarrage(
    gauche: String,
    surGauche: (() -> Unit)? = null,
    action: String,
    actif: Boolean = true,
    surAction: () -> Unit,
) {
    val b = LocalBanco.current
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            gauche,
            modifier = if (surGauche != null) {
                Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = surGauche).padding(vertical = 10.dp)
            } else {
                Modifier
            },
            style = TextStyle(
                fontFamily = Polices.corps,
                fontWeight = if (surGauche != null) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = if (surGauche != null) 15.sp else 13.sp,
                color = if (surGauche != null) b.lateriteTexte else b.encre2,
            ),
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            action,
            modifier = Modifier.padding(end = 14.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.encre),
        )
        Avancer(actif = actif, description = action, onClick = surAction)
    }
}

/** Les SIM du téléphone, pour l'étape Réseau et les soldes du Pouls. */
internal object Telephonie {
    class Sim(val id: Int, val place: Int, val operateur: String)

    fun sims(contexte: Context): List<Sim> = try {
        contexte.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty().map {
            Sim(it.subscriptionId, it.simSlotIndex + 1, it.carrierName?.toString().orEmpty().ifBlank { "Opérateur" })
        }
    } catch (_: SecurityException) {
        emptyList()
    }

    fun simDesDonnees(contexte: Context): Int = SubscriptionManager.getDefaultDataSubscriptionId()

    fun choisirSimDesDonnees(contexte: Context, id: Int) {
        try {
            val sm = contexte.getSystemService(SubscriptionManager::class.java) ?: return
            sm.javaClass.getMethod("setDefaultDataSubId", Int::class.javaPrimitiveType).invoke(sm, id)
        } catch (_: Exception) {
            // Sans ce droit, la SIM des données se choisit dans les Réglages.
        }
    }
}
