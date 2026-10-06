package africa.samaos.messages

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import africa.samaos.soldes.Soldes

/**
 * Les alertes de soldes (innovation 1) : à la réception d'un SMS de l'opérateur, Messages prévient si la data
 * passe sous 20 % ou le crédit sous 200 F, et programme un rappel avant la fin d'un forfait qui a du reste
 * (maquette i1-alerte-expiration). Chaque alerte se coupe dans Réglages › Soldes et forfaits.
 */
object AlertesSoldes {
    private const val CANAL = "soldes"
    private const val AVANT_FIN = 150 * 60_000L

    private fun notifier(c: Context, id: Int, titre: String, texte: String, action: Pair<String, Intent>? = null) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Soldes et forfaits", NotificationManager.IMPORTANCE_DEFAULT))
        val soldes = PendingIntent.getActivity(
            c, id, Intent("africa.samaos.action.SOLDES").setPackage("africa.samaos.reglages").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(c, CANAL)
            .setSmallIcon(R.drawable.ic_notif_message)
            .setContentTitle(titre)
            .setContentText(texte)
            .setStyle(Notification.BigTextStyle().bigText(texte))
            .setContentIntent(soldes)
            .setAutoCancel(true)
        action?.let { (nom, i) -> n.addAction(Notification.Action.Builder(null, nom, PendingIntent.getActivity(c, id + 1, i, PendingIntent.FLAG_IMMUTABLE)).build()) }
        nm.notify(id, n.build())
    }

    /** Un SMS d'un expéditeur officiel vient d'arriver sur la SIM [sub]. */
    fun recu(c: Context, sub: Int, corps: String) {
        val x = Soldes.analyser(corps)
        if (x.vide) return
        val reste = x.dataReste
        val total = x.dataTotal
        if (Soldes.alerte(c, "data") && reste != null && total != null && total > 0 && reste > 0 && reste / total < 0.2) {
            notifier(c, 7100 + sub, "Plus que ${Soldes.texteVolume(reste)} de data", "Sur un forfait de ${Soldes.texteVolume(total)}" + (x.expire?.let { ", " + Soldes.texteFin(it) } ?: "") + ".")
        }
        x.credit?.let { cr ->
            if (Soldes.alerte(c, "credit") && cr < 200) notifier(c, 7200 + sub, "Crédit : ${Soldes.texteMontant(cr)}", "Votre crédit passe sous 200 F.")
        }
        val fin = x.expire
        if (Soldes.alerte(c, "fin") && fin != null && (reste ?: total ?: 0.0) > 50) {
            val quand = fin - AVANT_FIN
            if (quand > System.currentTimeMillis()) {
                val i = PendingIntent.getBroadcast(
                    c, 7300 + sub, Intent(c, FinForfait::class.java).putExtra("sub", sub).putExtra("fin", fin),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                c.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC_WAKEUP, quand, i)
            }
        }
    }

    /** Le rappel : il reste de la data, le forfait finit dans peu de temps. */
    fun avantLaFin(c: Context, sub: Int, fin: Long) {
        if (!Soldes.alerte(c, "fin")) return
        val r = Soldes.releves(c)[sub] ?: return
        val reste = r.dataReste ?: return
        // Un autre forfait a pris la suite, ou la data est déjà presque toute partie : rien à dire.
        if (r.expire != fin || reste < 50) return
        val recus = Intent().setClassName("africa.samaos.fichiers", "africa.samaos.fichiers.Fichiers").putExtra("vue", "recus").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        notifier(
            c, 7400 + sub, "${Soldes.texteVolume(reste)} perdus " + Soldes.texteFin(fin).removePrefix("expire ").replace("ce soir à minuit", "à minuit"),
            "Ils peuvent servir aux téléchargements et aux mises à jour qui attendaient le Wi-Fi.",
            "Voir ce qui attend" to recus,
        )
    }
}

class FinForfait : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val r = goAsync()
        Thread {
            try {
                AlertesSoldes.avantLaFin(c, i.getIntExtra("sub", -1), i.getLongExtra("fin", 0L))
            } finally {
                r.finish()
            }
        }.start()
    }
}
