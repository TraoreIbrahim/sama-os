package africa.samaos.horloge

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.BarreAvis
import africa.samaos.banco.appli.NavFlottante

private enum class Onglet(val nom: String, val icone: String) {
    ALARMES("Alarmes", "M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 9v4l2 2 M5 3L2 6 M19 3l3 3"),
    MONDE("Horloge", Icones.GLOBE),
    MINUTEUR("Minuteur", "M10 2h4 M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 13V9"),
    CHRONO("Chrono", "M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 13l3-3 M10 2h4"),
    COUCHER("Coucher", Icones.NUIT),
}

/** L'Horloge de Sama : alarmes, heure du monde, minuteur, chrono, coucher. */
class Horloge : ComponentActivity() {
    private var onglet by mutableStateOf(Onglet.ALARMES)
    /** L'alarme qu'on règle (et si elle est nouvelle) : sa page monte par-dessus les onglets. */
    private var edition by mutableStateOf<Pair<Alarme, Boolean>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Le fond de l'Horloge est toujours sombre : les icônes du haut et du bas restent claires.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        Alarmes.canal(this)
        Chrono.charger(this)
        lire(intent)
        setContent {
            AvecIdentite(Identites.Horloge) {
                var derniere by remember { mutableStateOf<Pair<Alarme, Boolean>?>(null) }
                edition?.let { derniere = it }
                // Retour ramène d'abord aux Alarmes, puis quitte l'Horloge.
                BackHandler(enabled = onglet != Onglet.ALARMES && edition == null) { onglet = Onglet.ALARMES }
                Box(Modifier.fillMaxSize().background(LocalIdentite.current.fond)) {
                    AnimatedContent(
                        onglet,
                        transitionSpec = { (fadeIn(tween(240, delayMillis = 60)) + scaleIn(tween(240, delayMillis = 60), 0.98f)) togetherWith fadeOut(tween(110)) },
                        label = "onglet",
                    ) { o ->
                        when (o) {
                            Onglet.ALARMES -> PageAlarmes(Etat.version, { Etat.version++ }) { al, nouvelle -> edition = al to nouvelle }
                            Onglet.MONDE -> PageMonde()
                            Onglet.MINUTEUR -> PageMinuteur()
                            Onglet.CHRONO -> PageChrono()
                            Onglet.COUCHER -> PageCoucher(Etat.version)
                        }
                    }
                    NavFlottante(Onglet.entries.map { it.nom to it.icone }, onglet.ordinal, { onglet = Onglet.entries[it] }, Modifier.align(Alignment.BottomCenter))
                    AnimatedVisibility(
                        edition != null,
                        enter = slideInVertically(spring(dampingRatio = 0.88f, stiffness = 420f)) { it },
                        exit = slideOutVertically(tween(240, easing = FastOutLinearInEasing)) { it },
                    ) {
                        derniere?.let { (al, nouvelle) ->
                            EditeurAlarme(al, nouvelle) {
                                edition = null
                                Etat.version++
                            }
                        }
                    }
                    BarreAvis(Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    override fun onResume() {
        super.onResume()
        Etat.version++
    }

    /** « Mettre une alarme à 6 h 30 » ou « un minuteur de 5 minutes » demandés par une autre appli. */
    private fun lire(i: Intent?) {
        when (i?.action) {
            AlarmClock.ACTION_SET_ALARM -> {
                onglet = Onglet.ALARMES
                val h = i.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
                if (h >= 0) {
                    val al = Alarme(Alarmes.nouvelId(this), h, i.getIntExtra(AlarmClock.EXTRA_MINUTES, 0), i.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty(), emptySet(), true)
                    if (i.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
                        Alarmes.garder(this, al)
                        Avis.dire("Sonne dans ${Alarmes.duree(Alarmes.prochaine(al))}")
                    } else {
                        // L'appli qui demande ne décide pas seule : on montre l'alarme, la personne la garde.
                        edition = al to true
                    }
                }
            }
            AlarmClock.ACTION_SHOW_ALARMS -> onglet = Onglet.ALARMES
            AlarmClock.ACTION_SET_TIMER -> {
                onglet = Onglet.MINUTEUR
                val s = i.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
                if (s > 0) Alarmes.lancerMinuteur(this, i.getStringExtra(AlarmClock.EXTRA_MESSAGE) ?: "Minuteur", s * 1000L)
            }
        }
    }
}
