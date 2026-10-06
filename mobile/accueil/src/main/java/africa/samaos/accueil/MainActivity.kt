package africa.samaos.accueil

import africa.samaos.banco.*
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** L'écran d'accueil de Sama : choisi comme application d'accueil du téléphone. */
class MainActivity : ComponentActivity() {

    /** Augmente à chaque retour sur l'Accueil, pour relire la liste des applis installées. */
    private var generation by mutableIntStateOf(0)

    /** Augmente quand on touche le bouton Accueil alors qu'on y est déjà : les panneaux se referment. */
    private var retourAccueil by mutableIntStateOf(0)

    /** Les Réglages de Sama demandent les réglages des Espaces. */
    private var demandeEspaces by mutableIntStateOf(0)

    /** Augmente quand une appli recouvre l'Accueil : les panneaux se referment sans animation. */
    private var recouvert by mutableIntStateOf(0)

    private var modeNuit by mutableStateOf(ModeNuit.AUTO)

    private lateinit var espaces: Espaces

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        espaces = Espaces(this)
        Chaleur.surveiller(this)
        if (intent?.action == ACTION_REGLAGES_ESPACES) demandeEspaces++
        val reglages = reglages(this, "sama")
        if (espaces.profils) {
            preparerProfil(reglages)
            // Un Espace tout juste créé reçoit ses applis de départ (loin du fil principal).
            Thread { espaces.poserApplisDeDepart() }.start()
        }
        modeNuit = ModeNuit.entries.firstOrNull { it.name == reglages.getString("nuit", null) } ?: ModeNuit.AUTO
        fun choisirNuit(mode: ModeNuit) {
            modeNuit = mode
            reglages.edit { putString("nuit", mode.name) }
        }

        setContent {
            val systemeSombre = isSystemInDarkTheme()
            val heure = rememberMaintenant().hour
            // Sama système : c'est Android qui tient la Nuit (au coucher, réglée par Sama), pour tout le téléphone.
            // Téléphone ordinaire : l'Accueil a sa propre Nuit, au coucher ou quand le téléphone est sombre ;
            // un choix fait d'une touche dans le Pouls dure jusqu'au prochain changement naturel.
            val auto = if (espaces.profils) systemeSombre else systemeSombre || heure >= 19 || heure < 6
            LaunchedEffect(auto) {
                if ((modeNuit == ModeNuit.NUIT && auto) || (modeNuit == ModeNuit.JOUR && !auto)) choisirNuit(ModeNuit.AUTO)
            }
            val estNuit = if (espaces.profils) {
                systemeSombre
            } else {
                when (modeNuit) {
                    ModeNuit.AUTO -> auto
                    ModeNuit.NUIT -> true
                    ModeNuit.JOUR -> false
                }
            }
            val banco = palette(espaces.actif.paysage, estNuit, rememberPleinSoleil())
            LaunchedEffect(banco) {
                withContext(Dispatchers.Default) { FondEcran.appliquer(this@MainActivity, banco) }
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !banco.sombre
                    isAppearanceLightNavigationBars = !banco.sombre
                }
            }

            var applis by remember { mutableStateOf(emptyList<Appli>()) }
            var natte by remember { mutableStateOf(emptyList<Appli>()) }
            LaunchedEffect(generation, espaces.versionApplis) {
                val toutes = withContext(Dispatchers.Default) { chargerApplis(this@MainActivity) }
                applis = toutes
                natte = applisDeLaNatte(this@MainActivity, toutes)
            }

            val scope = rememberCoroutineScope()
            val cour = remember { Panneau(scope, sens = -1f) }
            val pouls = remember { Panneau(scope, sens = 1f) }
            LaunchedEffect(retourAccueil) {
                cour.fermer()
                pouls.fermer()
            }
            LaunchedEffect(recouvert) {
                cour.fermerTout()
                pouls.fermerTout()
            }

            CompositionLocalProvider(LocalBanco provides banco, LocalNuit provides estNuit) {
                Accueil(
                    espaces = espaces,
                    applis = applis,
                    natteMaison = natte,
                    cour = cour,
                    pouls = pouls,
                    retourAccueil = retourAccueil,
                    demandeEspaces = demandeEspaces,
                    nuit = if (espaces.profils) {
                        ReglageNuit(estNuit, ModeNuit.AUTO) { Profils.basculerNuit(this, !estNuit) }
                    } else {
                        // Toucher la tuile inverse ce qu'on voit ; si cela revient à l'automatique, on lui rend la main.
                        ReglageNuit(estNuit, modeNuit) {
                            val voulu = !estNuit
                            choisirNuit(
                                when {
                                    voulu == auto -> ModeNuit.AUTO
                                    voulu -> ModeNuit.NUIT
                                    else -> ModeNuit.JOUR
                                },
                            )
                        }
                    },
                    lancer = { lancer(this, it) },
                )
            }
        }
    }

    /**
     * La première fois dans un profil, Sama règle Android à sa façon : navigation par gestes,
     * couleurs des écrans d'Android tirées de la latérite, thème sombre au coucher.
     */
    private fun preparerProfil(reglages: SharedPreferences) {
        Profils.navigationParGestes(this)
        // Retenu seulement si Android a tout accepté : sinon on réessaiera à la prochaine ouverture.
        if (reglages.getInt("profil_prepare", 0) < PREPARATION &&
            Profils.couleursAndroid(this) && Profils.nuitAuCoucher(this)
        ) {
            reglages.edit { putInt("profil_prepare", PREPARATION) }
        }
    }

    /** Le profil vient d'être déverrouillé : toutes ses applis sont maintenant visibles. */
    private val deverrouille = object : BroadcastReceiver() {
        override fun onReceive(contexte: Context, intent: Intent) {
            generation++
            espaces.rafraichir()
            Thread { espaces.poserApplisDeDepart() }.start()
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, deverrouille, IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Les widgets se mettent à jour tant que l'Accueil est à l'écran.
        try {
            Widgets.hote(this).startListening()
        } catch (_: Exception) {
        }
    }

    /** Le retour de l'écran de réglage d'un widget : il n'est posé que si la personne a validé. */
    @Deprecated("Les widgets passent encore par startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Widgets.DEMANDE_REGLAGE) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            Widgets.finReglage?.invoke(id, resultCode == RESULT_OK)
        }
    }

    override fun onResume() {
        super.onResume()
        // Le geste Accueil (glisser depuis le bas) ne prévient pas l'Accueil quand il est déjà là :
        // Android le met seulement en pause un instant. À la reprise, la Cour et le Pouls se referment.
        if (enPause) retourAccueil++
        enPause = false
        generation++
        espaces.rafraichir()
        // Sur l'Accueil, le Pouls est le seul volet ; dans les applis, Android garde le sien
        // tant que le Pouls ne fait pas partie du système (SystemUI de Sama OS).
        Systeme.voletAndroid(this, ouvert = false)
    }

    /** Vrai entre une pause et la reprise, tant que l'Accueil n'a pas été recouvert. */
    private var enPause = false

    override fun onPause() {
        Systeme.voletAndroid(this, ouvert = true)
        enPause = true
        super.onPause()
    }

    override fun onStop() {
        enPause = false
        unregisterReceiver(deverrouille)
        try {
            Widgets.hote(this).stopListening()
        } catch (_: Exception) {
        }
        super.onStop()
        recouvert++
        espaces.finPassage()
    }

    /** Le bouton Accueil ramène toujours à l'Accueil, panneaux fermés. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_REGLAGES_ESPACES) demandeEspaces++ else retourAccueil++
    }

    private companion object {
        const val ACTION_REGLAGES_ESPACES = "africa.samaos.action.REGLAGES_ESPACES"

        /** À augmenter quand Sama règle Android autrement, pour que chaque profil reprenne les réglages. */
        const val PREPARATION = 2
    }
}
