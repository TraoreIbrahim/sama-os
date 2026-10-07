package africa.samaos.reglages

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import africa.samaos.banco.LocalNuit
import africa.samaos.banco.PaysageEspace
import africa.samaos.banco.palette
import africa.samaos.banco.AvecBanco

/**
 * Les Réglages de Sama. Une seule activité : les pages s'empilent comme dans les maquettes du lot 3,
 * le geste retour les dépile. Ouverts par une action d'Android (« Wi-Fi », « infos de l'appli »…),
 * ils arrivent directement sur la bonne page ; ce que Sama ne règle pas encore repart vers Android.
 */
class Reglages : ComponentActivity() {
    private var pile by mutableStateOf(listOf<Page>(Page.Accueil))
    private var paysage by mutableStateOf(PaysageEspace.LAGUNE)
    private var pleinSoleil by mutableStateOf(false)

    /** Le paysage ou Plein soleil changent : la palette suit aussitôt, sans attendre de revenir. */
    private val observateur = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            paysage = EspaceActif.paysage(this@Reglages)
            pleinSoleil = Settings.Secure.getInt(contentResolver, africa.samaos.banco.CLE_PLEIN_SOLEIL, 0) == 2
        }
    }

    /** Incrémenté à chaque retour sur les Réglages : les pages relisent l'état du téléphone. */
    private var reprise by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!ouvrir(intent)) return
        setContent {
            val nuit = isSystemInDarkTheme()
            val banco = palette(paysage, nuit, pleinSoleil)
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !banco.sombre
                    isAppearanceLightNavigationBars = !banco.sombre
                }
            }
            AvecBanco(banco, LocalNuit provides nuit, LocalReprise provides reprise) {
                val nav = Nav(
                    aller = { p -> if (p.faite) pile = pile + p else versAndroid(p.intentAndroid) },
                    retour = { if (pile.size > 1) pile = pile.dropLast(1) else finish() },
                    android = { versAndroid(it) },
                )
                BackHandler(enabled = pile.size > 1) { nav.retour() }
                val profondeur = androidx.compose.runtime.remember { intArrayOf(pile.size) }
                AnimatedContent(
                    targetState = pile.last(),
                    transitionSpec = {
                        val avance = pile.size >= profondeur[0]
                        profondeur[0] = pile.size
                        val sens = if (avance) 1 else -1
                        (slideInHorizontally(tween(260)) { it * sens / 4 } + fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally(tween(260)) { -it * sens / 6 } + fadeOut(tween(160)))
                    },
                    label = "pages",
                ) { page -> Afficher(page, nav) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ouvrir(intent)
    }

    override fun onResume() {
        super.onResume()
        observateur.onChange(false)
        reprise++
    }

    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(Settings.Global.getUriFor("sama_espaces"), false, observateur)
        contentResolver.registerContentObserver(Settings.Secure.getUriFor(africa.samaos.banco.CLE_PLEIN_SOLEIL), false, observateur)
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(observateur)
        super.onStop()
    }

    /** La page demandée par l'intention ; faux si elle part vers les Paramètres d'Android. */
    private fun ouvrir(intent: Intent?): Boolean {
        val page = Page.depuis(intent)
        if (page == null) {
            versAndroid(intent ?: Intent(Settings.ACTION_SETTINGS))
            finish()
            return false
        }
        pile = if (page == Page.Accueil) listOf(Page.Accueil) else listOf(page)
        return true
    }

    /** Ce que Sama ne règle pas encore : les Paramètres d'Android, à la même page. */
    private fun versAndroid(intent: Intent) {
        try {
            startActivity(Intent(intent).setPackage(PARAMETRES_ANDROID).setComponent(null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).setPackage(PARAMETRES_ANDROID).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val PARAMETRES_ANDROID = "com.android.settings"
    }
}

/** Aller à une page, revenir, ou passer la main aux Paramètres d'Android. */
class Nav(val aller: (Page) -> Unit, val retour: () -> Unit, val android: (Intent) -> Unit)

/** Change à chaque retour sur les Réglages : de quoi relire l'état du téléphone. */
val LocalReprise = androidx.compose.runtime.compositionLocalOf { 0 }
