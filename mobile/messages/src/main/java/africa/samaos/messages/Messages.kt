package africa.samaos.messages

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.BarreAvis
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PileDoigt
import africa.samaos.banco.appli.Sens

internal sealed interface Vue {
    data object Liste : Vue
    data object Services : Vue
    data class Fil(val fil: Long, val adresse: String) : Vue
    data class Nouveau(val texte: String? = null) : Vue
}

/** Les Messages de Sama : les conversations, une conversation, un nouveau message. */
class Messages : ComponentActivity() {
    private var pile by mutableStateOf(listOf<Vue>(Vue.Liste))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Boite.canaux(this)
        lire(intent)
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.MessagesNuit else Identites.Messages
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) {
                val version = rememberBoite()
                val aller: (Vue) -> Unit = { pile = pile + it }
                val retour: () -> Unit = { if (pile.size > 1) pile = pile.dropLast(1) else finish() }
                Box(Modifier.fillMaxSize().background(LocalIdentite.current.fond)) {
                    // Une conversation arrive de la droite ; un nouveau message monte du bas ; le retour fait l'inverse, et suit le doigt.
                    PileDoigt(pile, retour, sens = { if (it is Vue.Nouveau) Sens.BAS else Sens.DROITE }) { v ->
                        when (v) {
                            Vue.Liste -> PageListe(version, services = false, aller = aller, retour = null)
                            Vue.Services -> PageListe(version, services = true, aller = aller, retour = retour)
                            is Vue.Fil -> PageFil(v.fil, v.adresse, version, retour)
                            is Vue.Nouveau -> PageNouveau(v.texte, retour) { fil, adresse -> pile = pile.dropLast(1) + Vue.Fil(fil, adresse) }
                        }
                    }
                    BarreAvis(Modifier.align(Alignment.BottomCenter), bas = 20.dp)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    /** « Écrire un SMS à … », ou une notification : la conversation ; un texte partagé : un nouveau message. */
    private fun lire(i: Intent?) {
        val fil = i?.getLongExtra("fil", -1) ?: -1
        val adresse = i?.getStringExtra("adresse") ?: i?.data?.schemeSpecificPart?.substringBefore('?')?.takeIf { it.isNotBlank() }
        when {
            fil >= 0 && adresse != null -> pile = listOf(Vue.Liste, Vue.Fil(fil, adresse))
            adresse != null -> pile = listOf(Vue.Liste, Vue.Fil(Boite.filDe(this, adresse), adresse))
            i?.action == Intent.ACTION_SEND -> pile = listOf(Vue.Liste, Vue.Nouveau(i.getStringExtra(Intent.EXTRA_TEXT)))
        }
    }
}

/** Change à chaque SMS reçu ou envoyé : les pages se relisent. */
@Composable
private fun rememberBoite(): Int {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                version++
            }
        }
        c.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
        onDispose { c.contentResolver.unregisterContentObserver(obs) }
    }
    return version
}
