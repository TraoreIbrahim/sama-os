package africa.samaos.reglages

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import africa.samaos.banco.Icones

/** Les réglages d'accessibilité d'Android, lus et écrits directement (Settings.Secure, Global, System). */
object MoteurAccessibilite {
    fun secure(c: Context, cle: String, defaut: Int = 0) = Settings.Secure.getInt(c.contentResolver, cle, defaut)
    fun ecrireSecure(c: Context, cle: String, v: Int) = try {
        Settings.Secure.putInt(c.contentResolver, cle, v)
    } catch (_: SecurityException) {
        false
    }

    /** Correction des couleurs : la valeur d'Android (0 gris, 11 rouge, 12 rouge et vert, 13 bleu et jaune), ou -1 si aucune. */
    fun correction(c: Context): Int = if (secure(c, "accessibility_display_daltonizer_enabled") == 1) secure(c, "accessibility_display_daltonizer", 12) else -1

    fun reglerCorrection(c: Context, v: Int) {
        if (v < 0) {
            ecrireSecure(c, "accessibility_display_daltonizer_enabled", 0)
        } else {
            ecrireSecure(c, "accessibility_display_daltonizer", v)
            ecrireSecure(c, "accessibility_display_daltonizer_enabled", 1)
        }
    }

    fun echelleLoupe(c: Context): Float = Settings.Secure.getFloat(c.contentResolver, "accessibility_display_magnification_scale", 2f)

    fun reglerEchelleLoupe(c: Context, v: Float) = try {
        Settings.Secure.putFloat(c.contentResolver, "accessibility_display_magnification_scale", v)
    } catch (_: SecurityException) {
        false
    }

    /** Sans animations : les trois vitesses d'animation d'Android à zéro. */
    fun animations(c: Context) = Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    fun reglerAnimations(c: Context, oui: Boolean) {
        val v = if (oui) 1f else 0f
        try {
            listOf(Settings.Global.ANIMATOR_DURATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.WINDOW_ANIMATION_SCALE)
                .forEach { Settings.Global.putFloat(c.contentResolver, it, v) }
        } catch (_: SecurityException) {
        }
    }

    fun mono(c: Context) = Settings.System.getInt(c.contentResolver, "master_mono", 0) == 1

    fun reglerMono(c: Context, oui: Boolean) = try {
        Settings.System.putInt(c.contentResolver, "master_mono", if (oui) 1 else 0)
    } catch (_: Exception) {
        false
    }

    fun appuiLong(c: Context) = secure(c, "long_press_timeout", 400)

    /** Le menu d'accessibilité d'Android (gros boutons), ouvert par un rond flottant. */
    private const val MENU = "com.android.systemui.accessibility.accessibilitymenu/com.android.systemui.accessibility.accessibilitymenu.AccessibilityMenuService"

    private fun services(c: Context) = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':').filter { it.isNotBlank() }

    fun menuActif(c: Context) = MENU in services(c)

    fun reglerMenu(c: Context, oui: Boolean) {
        try {
            val l = services(c).filter { it != MENU } + if (oui) listOf(MENU) else emptyList()
            Settings.Secure.putString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, l.joinToString(":"))
            Settings.Secure.putString(c.contentResolver, "accessibility_button_targets", if (oui) MENU else "")
            if (oui) Settings.Secure.putInt(c.contentResolver, "accessibility_button_mode", 1)
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (l.isEmpty()) 0 else 1)
        } catch (_: SecurityException) {
        }
    }

    fun resume(c: Context): String = listOfNotNull(
        if (secure(c, "accessibility_display_magnification_enabled") == 1) "Loupe" else null,
        if (secure(c, "high_text_contrast_enabled") == 1) "Fort contraste" else null,
        if (secure(c, "accessibility_display_inversion_enabled") == 1) "Couleurs inversées" else null,
        if (correction(c) >= 0) "Correction des couleurs" else null,
        if (!animations(c)) "Sans animations" else null,
        if (menuActif(c)) "Menu d'accessibilité" else null,
    ).joinToString(", ").ifBlank { "Rien d'activé" }
}

/** L'accessibilité (maquettes l5-loupe, l5-contraste…). */
@Composable
fun PageAccessibilite(nav: Nav) {
    val c = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    PageReglages(titre = "Accessibilité", sousTitre = remember(v) { MoteurAccessibilite.resume(c) }, retour = nav.retour) {
        section("Voir", cle = "voir") {
            Ligne("Taille du texte et de l'affichage", icone = Icones.TEXTE) { nav.aller(Page.TailleTexte) }
            Ligne("Couleurs et contraste", detail = remember(v) {
                listOfNotNull(
                    if (MoteurAccessibilite.secure(c, "high_text_contrast_enabled") == 1) "Fort contraste" else null,
                    if (MoteurAccessibilite.secure(c, "accessibility_display_inversion_enabled") == 1) "Inversées" else null,
                    if (MoteurAccessibilite.correction(c) >= 0) "Correction" else null,
                ).joinToString(", ").ifBlank { "Normales" }
            }, icone = Icones.PALETTE) { nav.aller(Page.Couleurs) }
            Ligne("Loupe", detail = if (MoteurAccessibilite.secure(c, "accessibility_display_magnification_enabled") == 1) "Toucher trois fois pour agrandir" else "Désactivée", icone = Icones.RECHERCHE) { nav.aller(Page.Loupe) }
            Ligne("Sans animations", detail = "Moins de mouvement à l'écran", icone = Icones.ROTATION, fin = Fin.Inter(!MoteurAccessibilite.animations(c))) {
                MoteurAccessibilite.reglerAnimations(c, !MoteurAccessibilite.animations(c))
                v++
            }
        }
        section("Toucher", cle = "toucher") {
            val menu = remember(v) { MoteurAccessibilite.menuActif(c) }
            Ligne("Menu d'accessibilité", detail = "Un petit rond flottant : verrouiller, volume, capture… en gros boutons", icone = Icones.APPLI, fin = Fin.Inter(menu)) {
                MoteurAccessibilite.reglerMenu(c, !menu)
                v++
            }
            Ligne("Navigation", detail = "Gestes ou trois boutons", icone = Icones.VERS_ESPACE) { nav.aller(Page.Gestes) }
            val delai = remember(v) { MoteurAccessibilite.appuiLong(c) }
            Ligne(
                "Appui long", detail = when { delai <= 400 -> "Court"; delai <= 1000 -> "Moyen"; else -> "Long" } + " · le temps à tenir le doigt",
                icone = Icones.HORLOGE,
            ) {
                MoteurAccessibilite.ecrireSecure(c, "long_press_timeout", when { delai <= 400 -> 1000; delai <= 1000 -> 1500; else -> 400 })
                v++
            }
        }
        section("Entendre", cle = "entendre") {
            Ligne("Son mono", detail = "Le même son dans les deux écouteurs", icone = Icones.ECOUTEURS, fin = Fin.Inter(remember(v) { MoteurAccessibilite.mono(c) })) {
                MoteurAccessibilite.reglerMono(c, !MoteurAccessibilite.mono(c))
                v++
            }
        }
        section(cle = "lecteur") {
            Explication("Le lecteur d'écran de Sama, qui lira l'écran à voix haute en français et en langues nationales, viendra avec les innovations.")
        }
    }
}

/** Couleurs et contraste (maquette l5-contraste). */
@Composable
fun PageCouleurs(nav: Nav) {
    val c = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    val contraste = remember(v) { MoteurAccessibilite.secure(c, "high_text_contrast_enabled") == 1 }
    val inverse = remember(v) { MoteurAccessibilite.secure(c, "accessibility_display_inversion_enabled") == 1 }
    val correction = remember(v) { MoteurAccessibilite.correction(c) }
    PageReglages(titre = "Couleurs", retour = nav.retour) {
        section(cle = "base") {
            Ligne("Texte à fort contraste", detail = "Le texte en noir ou en blanc, bien détaché", icone = Icones.TEXTE, fin = Fin.Inter(contraste)) {
                MoteurAccessibilite.ecrireSecure(c, "high_text_contrast_enabled", if (contraste) 0 else 1)
                v++
            }
            Ligne("Inverser les couleurs", detail = "Le clair devient sombre, et l'inverse", icone = Icones.NUIT, fin = Fin.Inter(inverse)) {
                MoteurAccessibilite.ecrireSecure(c, "accessibility_display_inversion_enabled", if (inverse) 0 else 1)
                v++
            }
        }
        section("Correction des couleurs", cle = "correction") {
            listOf(
                Triple(-1, "Aucune", null),
                Triple(12, "Rouge et vert", "Deutéranopie, la plus fréquente"),
                Triple(11, "Rouge", "Protanopie"),
                Triple(13, "Bleu et jaune", "Tritanopie"),
                Triple(0, "Nuances de gris", "Tout l'écran en gris"),
            ).forEach { (val_, nom, detail) ->
                Ligne(nom, detail = detail, fin = Fin.Choix(correction == val_)) {
                    MoteurAccessibilite.reglerCorrection(c, val_)
                    v++
                }
            }
        }
    }
}

/** La loupe (maquette l5-loupe) : trois touchers pour agrandir. */
@Composable
fun PageLoupe(nav: Nav) {
    val c = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    val active = remember(v) { MoteurAccessibilite.secure(c, "accessibility_display_magnification_enabled") == 1 }
    val mode = remember(v) { MoteurAccessibilite.secure(c, "accessibility_magnification_mode", 1) }
    val echelle = remember(v) { MoteurAccessibilite.echelleLoupe(c) }
    PageReglages(titre = "Loupe", retour = nav.retour) {
        section(cle = "raccourci") {
            Ligne("Raccourci de la loupe", detail = "Toucher trois fois l'écran pour agrandir", icone = Icones.RECHERCHE, fin = Fin.Inter(active)) {
                MoteurAccessibilite.ecrireSecure(c, "accessibility_display_magnification_enabled", if (active) 0 else 1)
                v++
            }
        }
        section("Agrandir", cle = "mode") {
            Ligne("Une fenêtre qui suit le doigt", fin = Fin.Choix(mode == 2)) {
                MoteurAccessibilite.ecrireSecure(c, "accessibility_magnification_mode", 2)
                v++
            }
            Ligne("Tout l'écran", fin = Fin.Choix(mode != 2)) {
                MoteurAccessibilite.ecrireSecure(c, "accessibility_magnification_mode", 1)
                v++
            }
        }
        section("Zoom", cle = "zoom") {
            Curseur("× ${"%.1f".format(echelle).replace('.', ',')}", Icones.PLUS, ((echelle - 1.5f) / 6.5f).coerceIn(0f, 1f)) { p ->
                MoteurAccessibilite.reglerEchelleLoupe(c, 1.5f + p * 6.5f)
                v++
            }
            Explication("Pour déplacer l'image agrandie : deux doigts. Pour l'agrandir plus : écarter deux doigts.")
        }
    }
}
