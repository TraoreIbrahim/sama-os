package africa.samaos.reglages

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.provider.Settings
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.CLE_PLEIN_SOLEIL
import africa.samaos.banco.ChoixPaysage
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.PaysageEspace
import africa.samaos.banco.Polices
import org.json.JSONObject
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

object MoteurAffichage {
    fun luminosite(c: Context) = Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

    fun reglerLuminosite(c: Context, v: Float) {
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v.coerceIn(0.02f, 1f) * 255).roundToInt())
    }

    fun adaptative(c: Context) = Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1

    fun reglerAdaptative(c: Context, oui: Boolean) =
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, if (oui) 1 else 0)

    fun pleinSoleil(c: Context) = Settings.Secure.getInt(c.contentResolver, CLE_PLEIN_SOLEIL, 0)

    fun reglerPleinSoleil(c: Context, mode: Int) = Settings.Secure.putInt(c.contentResolver, CLE_PLEIN_SOLEIL, mode)

    private fun ui(c: Context) = c.getSystemService(UiModeManager::class.java)

    /** 0 : Aube (jamais la Nuit), 1 : Nuit toujours, 2 : au coucher (19 h – 6 h). */
    fun theme(c: Context): Int = when (ui(c).nightMode) {
        UiModeManager.MODE_NIGHT_YES -> 1
        UiModeManager.MODE_NIGHT_CUSTOM, UiModeManager.MODE_NIGHT_AUTO -> 2
        else -> 0
    }

    fun reglerTheme(c: Context, t: Int) {
        val u = ui(c)
        try {
            when (t) {
                1 -> u.nightMode = UiModeManager.MODE_NIGHT_YES
                2 -> {
                    u.nightMode = UiModeManager.MODE_NIGHT_CUSTOM
                    u.customNightModeStart = LocalTime.of(19, 0)
                    u.customNightModeEnd = LocalTime.of(6, 0)
                }
                else -> u.nightMode = UiModeManager.MODE_NIGHT_NO
            }
        } catch (_: Exception) {
        }
    }

    fun echelleTexte(c: Context) = Settings.System.getFloat(c.contentResolver, Settings.System.FONT_SCALE, 1f)

    fun reglerEchelleTexte(c: Context, e: Float) = Settings.System.putFloat(c.contentResolver, Settings.System.FONT_SCALE, e)

    fun gras(c: Context) = Settings.Secure.getInt(c.contentResolver, "font_weight_adjustment", 0) > 0

    fun reglerGras(c: Context, oui: Boolean) = Settings.Secure.putInt(c.contentResolver, "font_weight_adjustment", if (oui) 300 else 0)

    private fun fenetres(): Any = Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null)!!

    /** La densité de base et l'actuelle : « Taille de l'affichage ». */
    fun densites(): Pair<Int, Int>? = try {
        val wm = fenetres()
        val initiale = wm.javaClass.getMethod("getInitialDisplayDensity", Int::class.javaPrimitiveType).invoke(wm, Display.DEFAULT_DISPLAY) as Int
        val base = wm.javaClass.getMethod("getBaseDisplayDensity", Int::class.javaPrimitiveType).invoke(wm, Display.DEFAULT_DISPLAY) as Int
        initiale to base
    } catch (_: Exception) {
        null
    }

    fun reglerDensite(d: Int) = try {
        val wm = fenetres()
        wm.javaClass.getMethod("setForcedDisplayDensityForUser", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(wm, Display.DEFAULT_DISPLAY, d, -2)
        true
    } catch (_: Exception) {
        false
    }

    fun veille(c: Context) = Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)

    fun reglerVeille(c: Context, ms: Int) = Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms)

    fun rotation(c: Context) = Settings.System.getInt(c.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    fun reglerRotation(c: Context, oui: Boolean) = Settings.System.putInt(c.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (oui) 1 else 0)

    fun possible(nom: String): Boolean {
        val id = Resources.getSystem().getIdentifier(nom, "bool", "android")
        return id != 0 && Resources.getSystem().getBoolean(id)
    }

    // Éclairage nocturne (ColorDisplayManager, réservé au système)

    private fun couleurs(c: Context): Any? = try {
        c.getSystemService(Class.forName("android.hardware.display.ColorDisplayManager"))
    } catch (_: Exception) {
        null
    }

    fun eclairageDisponible(c: Context): Boolean = try {
        Class.forName("android.hardware.display.ColorDisplayManager").getMethod("isNightDisplayAvailable", Context::class.java).invoke(null, c) as Boolean
    } catch (_: Exception) {
        false
    }

    fun eclairage(c: Context): Boolean = try {
        couleurs(c)!!.let { it.javaClass.getMethod("isNightDisplayActivated").invoke(it) as Boolean }
    } catch (_: Exception) {
        false
    }

    fun reglerEclairage(c: Context, oui: Boolean) = try {
        couleurs(c)!!.let { it.javaClass.getMethod("setNightDisplayActivated", Boolean::class.javaPrimitiveType).invoke(it, oui) }
        true
    } catch (_: Exception) {
        false
    }

    /** Les Espaces du téléphone et leur paysage, tels qu'enregistrés par l'Accueil. */
    fun paysages(c: Context): List<Triple<Int, String, PaysageEspace>> {
        val json = try {
            JSONObject(Settings.Global.getString(c.contentResolver, "sama_espaces") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        return EspaceActif.liste(c).map { (id, nom) ->
            val p = PaysageEspace.entries.firstOrNull { it.name == json.optJSONObject(id.toString())?.optString("paysage") } ?: PaysageEspace.LAGUNE
            Triple(id, nom, p)
        }
    }

    fun reglerPaysage(c: Context, id: Int, p: PaysageEspace) {
        try {
            val cr = c.contentResolver
            val json = JSONObject(Settings.Global.getString(cr, "sama_espaces") ?: "{}")
            val e = json.optJSONObject(id.toString()) ?: JSONObject().put("couleur", if (id == 0) "OR" else "VERT")
            e.put("paysage", p.name)
            json.put(id.toString(), e)
            Settings.Global.putString(cr, "sama_espaces", json.toString())
        } catch (_: Exception) {
        }
    }
}

/** L'affichage : luminosité, thème, éclairage nocturne, taille du texte, veille et rotation, Plein soleil. */
@Composable
fun PageAffichage(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var version by remember { mutableIntStateOf(0) }
    var lum by remember { mutableFloatStateOf(MoteurAffichage.luminosite(c)) }
    LaunchedEffect(reprise) { lum = MoteurAffichage.luminosite(c) }
    PageReglages(titre = "Affichage", retour = nav.retour) {
        val v = version
        section(cle = "luminosite") {
            Curseur("Luminosité", Icones.SOLEIL, lum) {
                lum = it
                MoteurAffichage.reglerLuminosite(c, it)
            }
            Ligne(
                "Luminosité adaptative",
                detail = "Suit la lumière autour de vous",
                fin = Fin.Inter(v >= 0 && MoteurAffichage.adaptative(c)),
            ) {
                MoteurAffichage.reglerAdaptative(c, !MoteurAffichage.adaptative(c))
                version++
            }
        }
        section(cle = "pages") {
            val theme = when {
                MoteurAffichage.pleinSoleil(c) == 2 -> "Plein soleil"
                MoteurAffichage.theme(c) == 1 -> "Nuit"
                MoteurAffichage.theme(c) == 2 -> "Nuit au coucher"
                else -> "Aube"
            }
            Ligne("Thème", detail = theme, icone = Icones.NUIT) { nav.aller(Page.ThemeNuit) }
            Ligne("Plein soleil", detail = "Lisible sous le soleil de midi", icone = Icones.SOLEIL) { nav.aller(Page.PleinSoleil) }
            Ligne("Éclairage nocturne", detail = "Des couleurs plus chaudes, le soir", icone = Icones.NUIT) { nav.aller(Page.EclairageNocturne) }
            Ligne("Taille du texte", detail = "${(MoteurAffichage.echelleTexte(c) * 100).roundToInt()} %", icone = Icones.TEXTE) { nav.aller(Page.TailleTexte) }
            Ligne("Écran", detail = "Veille, rotation et toucher", icone = Icones.TELEPHONE) { nav.aller(Page.VeilleRotation) }
        }
    }
}

/** Le thème (maquette l3-theme-nuit). */
@Composable
fun PageThemeNuit(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    PageReglages(titre = "Thème", retour = nav.retour) {
        val v = version
        val soleil = MoteurAffichage.pleinSoleil(c) == 2
        val t = if (v >= 0) MoteurAffichage.theme(c) else 0
        section(cle = "themes") {
            Ligne("Aube", detail = "Clair, les couleurs de la latérite", fin = Fin.Choix(!soleil && t != 1)) {
                MoteurAffichage.reglerPleinSoleil(c, 0)
                if (t == 1) MoteurAffichage.reglerTheme(c, 2)
                version++
            }
            Ligne("Nuit", detail = "Sombre, pour les yeux et la batterie", fin = Fin.Choix(!soleil && t == 1)) {
                MoteurAffichage.reglerPleinSoleil(c, 0)
                MoteurAffichage.reglerTheme(c, 1)
                version++
            }
            Ligne("Plein soleil", detail = "Contraste renforcé pour lire dehors", fin = Fin.Choix(soleil)) {
                MoteurAffichage.reglerPleinSoleil(c, 2)
                MoteurAffichage.reglerTheme(c, 0)
                version++
            }
        }
        if (!soleil && t != 1) {
            section("Passer en Nuit", cle = "passer") {
                Ligne("Au coucher du soleil", detail = "De 19 h à 6 h", fin = Fin.Choix(t == 2)) {
                    MoteurAffichage.reglerTheme(c, 2)
                    version++
                }
                Ligne("Jamais", fin = Fin.Choix(t == 0)) {
                    MoteurAffichage.reglerTheme(c, 0)
                    version++
                }
            }
        }
    }
}

/** L'éclairage nocturne (maquette l3-eclairage-nocturne). */
@Composable
fun PageEclairageNocturne(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val dispo = remember { MoteurAffichage.eclairageDisponible(c) }
    PageReglages(titre = "Éclairage nocturne", retour = nav.retour) {
        val v = version
        if (!dispo) {
            section(cle = "non") { Explication("L'écran de ce téléphone ne sait pas réchauffer ses couleurs. Le thème Nuit repose aussi les yeux le soir.") }
            return@PageReglages
        }
        section(cle = "inter") {
            val actif = v >= 0 && MoteurAffichage.eclairage(c)
            Ligne("Éclairage nocturne", detail = "Des couleurs plus chaudes, le soir", icone = Icones.NUIT, fin = Fin.Inter(actif)) {
                MoteurAffichage.reglerEclairage(c, !actif)
                version++
            }
        }
    }
}

/** La taille du texte et de l'affichage (maquette l3-taille-texte). */
@Composable
fun PageTailleTexte(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val echelles = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)
    PageReglages(titre = "Taille du texte", retour = nav.retour) {
        val v = version
        val e = if (v >= 0) MoteurAffichage.echelleTexte(c) else 1f
        section(cle = "apercu") {
            val b = LocalBanco.current
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(b.sol2).padding(16.dp)) {
                BasicText("Aperçu", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = b.encre2))
                BasicText(
                    "Bonjour Aminata ! Le marché rouvre lundi, on y va ensemble ?",
                    modifier = Modifier.padding(top = 6.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre),
                )
            }
        }
        section("Texte", cle = "texte") {
            Segments(listOf("A", "A", "A", "A", "A"), echelles.indexOfFirst { abs(it - e) < 0.05f }.coerceAtLeast(1)) { i ->
                MoteurAffichage.reglerEchelleTexte(c, echelles[i])
                version++
            }
        }
        MoteurAffichage.densites()?.let { (initiale, actuelle) ->
            val pas = listOf(0.85f, 1f, 1.1f, 1.2f, 1.3f).map { (initiale * it).roundToInt() }
            section("Taille de l'affichage", cle = "affichage") {
                Segments(listOf("−", "Normale", "+", "++", "+++"), pas.indexOfFirst { abs(it - actuelle) <= 2 }.coerceAtLeast(1)) { i ->
                    MoteurAffichage.reglerDensite(pas[i])
                    version++
                }
            }
        }
        section(cle = "gras") {
            Ligne("Texte en gras", fin = Fin.Inter(v >= 0 && MoteurAffichage.gras(c))) {
                MoteurAffichage.reglerGras(c, !MoteurAffichage.gras(c))
                version++
            }
        }
    }
}

/** La veille, la rotation et le toucher (maquette l3-veille-rotation). */
@Composable
fun PageVeilleRotation(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var ouvert by remember { mutableIntStateOf(0) }
    val durees = listOf(15_000 to "15 secondes", 30_000 to "30 secondes", 60_000 to "1 minute", 120_000 to "2 minutes", 300_000 to "5 minutes", 600_000 to "10 minutes")
    PageReglages(titre = "Écran", sousTitre = "Veille, rotation et toucher", retour = nav.retour) {
        val v = version
        section("Mise en veille", cle = "veille") {
            val actuelle = if (v >= 0) MoteurAffichage.veille(c) else 0
            Ligne("Éteindre l'écran après", detail = durees.firstOrNull { it.first == actuelle }?.second ?: "${actuelle / 1000} s", icone = Icones.HORLOGE) {
                ouvert = 1 - ouvert
            }
            if (ouvert == 1) {
                durees.forEach { (ms, nom) ->
                    Ligne(nom, fin = Fin.Choix(ms == actuelle)) {
                        MoteurAffichage.reglerVeille(c, ms)
                        ouvert = 0
                        version++
                    }
                }
            }
        }
        section("Rotation", cle = "rotation") {
            Ligne("Rotation automatique", icone = Icones.ROTATION, fin = Fin.Inter(v >= 0 && MoteurAffichage.rotation(c))) {
                MoteurAffichage.reglerRotation(c, !MoteurAffichage.rotation(c))
                version++
            }
        }
        if (MoteurAffichage.possible("config_supportDoubleTapWake")) {
            section("Toucher", cle = "toucher") {
                val actif = v >= 0 && Settings.Secure.getInt(c.contentResolver, "double_tap_to_wake", 0) == 1
                Ligne("Toucher deux fois pour allumer", fin = Fin.Inter(actif)) {
                    Settings.Secure.putInt(c.contentResolver, "double_tap_to_wake", if (actif) 0 else 1)
                    version++
                }
            }
        }
    }
}

/** Plein soleil (maquette l3-plein-soleil). */
@Composable
fun PagePleinSoleil(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    PageReglages(titre = "Plein soleil", sousTitre = "Lisible sous le soleil de midi", retour = nav.retour) {
        val v = version
        val mode = if (v >= 0) MoteurAffichage.pleinSoleil(c) else 0
        section(cle = "inter") {
            Ligne("Thème Plein soleil", detail = "Contraste renforcé pour lire dehors", icone = Icones.SOLEIL, fin = Fin.Inter(mode == 2)) {
                MoteurAffichage.reglerPleinSoleil(c, if (mode == 2) 0 else 2)
                version++
            }
        }
        section("Se lance", cle = "lance") {
            Ligne("Quand la lumière est très forte", detail = "D'après le capteur de lumière", fin = Fin.Choix(mode == 1)) {
                MoteurAffichage.reglerPleinSoleil(c, 1)
                version++
            }
            Ligne("Toujours", fin = Fin.Choix(mode == 2)) {
                MoteurAffichage.reglerPleinSoleil(c, 2)
                version++
            }
            Ligne("Jamais", fin = Fin.Choix(mode == 0)) {
                MoteurAffichage.reglerPleinSoleil(c, 0)
                version++
            }
        }
        section("Avec", cle = "avec") {
            Ligne("Texte plus gras", fin = Fin.Inter(v >= 0 && MoteurAffichage.gras(c))) {
                MoteurAffichage.reglerGras(c, !MoteurAffichage.gras(c))
                version++
            }
        }
    }
}

/** Le fond d'écran : le paysage de chaque Espace (maquette l3-fond-ecran). */
@Composable
fun PageFondEcran(nav: Nav) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val moi = EspaceActif.id()
    PageReglages(titre = "Fond d'écran", sousTitre = "Des paysages qui suivent le soleil", retour = nav.retour) {
        val v = version
        val espaces = if (v >= 0) MoteurAffichage.paysages(c) else emptyList()
        // Maison règle tous les Espaces ; un autre Espace ne règle que lui-même.
        espaces.filter { moi == 0 || it.first == moi }.forEach { (id, nom, p) ->
            section("Pour l'Espace $nom", cle = "espace-$id") {
                ChoixPaysage(p) { nouveau ->
                    MoteurAffichage.reglerPaysage(c, id, nouveau)
                    version++
                }
            }
        }
        section(cle = "verrou") {
            Ligne("Écran verrouillé", detail = "L'heure et les raccourcis", icone = Icones.CADENAS) { nav.aller(Page.EcranVerrouille) }
        }
    }
}

/** L'écran verrouillé (maquette l3-ecran-verrouille) : l'écran d'Android, réglé par ses pages. */
@Composable
fun PageEcranVerrouille(nav: Nav) {
    PageReglages(titre = "Écran verrouillé", retour = nav.retour) {
        section(cle = "liens") {
            Ligne("Notifications", detail = "Ce que montre l'écran verrouillé", icone = Icones.CLOCHE) { nav.aller(Page.NotifsVerrou) }
            Ligne("Raccourcis et horloge", detail = "Les raccourcis en bas de l'écran", icone = Icones.HORLOGE) {
                nav.android(Intent("android.settings.LOCK_SCREEN_SETTINGS"))
            }
            Ligne("Code de verrouillage", icone = Icones.CADENAS) { nav.aller(Page.Verrouillage) }
        }
    }
}
