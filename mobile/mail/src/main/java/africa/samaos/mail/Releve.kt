package africa.samaos.mail

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import africa.samaos.bouclier.Bouclier
import kotlin.concurrent.thread

/** Les réglages de Mail. */
object Preferences {
    private fun prefs(c: Context) = c.getSharedPreferences("mail", Context.MODE_PRIVATE)

    /** Relever toutes les N minutes (0 : seulement quand Mail est ouvert). */
    val FREQUENCES = listOf(15, 60, 0)

    fun frequence(c: Context) = prefs(c).getInt("frequence", 15)

    fun frequenceSuivante(c: Context) {
        prefs(c).edit().putInt("frequence", FREQUENCES[(FREQUENCES.indexOf(frequence(c)) + 1) % FREQUENCES.size]).apply()
        Releve.programmer(c)
    }

    fun nomFrequence(m: Int) = when (m) {
        0 -> "Seulement Mail ouvert"
        60 -> "Toutes les heures"
        else -> "Toutes les $m minutes"
    }

    /** Les images à distance : à la demande (elles coûtent de la data et disent à l'expéditeur qu'on a ouvert son mail). */
    fun imagesToujours(c: Context) = prefs(c).getBoolean("images", false)

    fun reglerImages(c: Context, oui: Boolean) = prefs(c).edit().putBoolean("images", oui).apply()

    /** Sur data mobile, la relève de fond ne prend que les en-têtes : le texte vient à l'ouverture. */
    fun texteSurData(c: Context) = prefs(c).getBoolean("texte_data", false)

    fun reglerTexteSurData(c: Context, oui: Boolean) = prefs(c).edit().putBoolean("texte_data", oui).apply()
}

/** La relève en arrière-plan : nouveaux mails, mails en attente d'envoi. Seulement avec un réseau. */
class Releve : JobService() {
    companion object {
        private const val ID = 4201

        fun programmer(c: Context) {
            val js = c.getSystemService(JobScheduler::class.java)
            val m = Preferences.frequence(c)
            if (m == 0 || Comptes.liste(c).isEmpty()) {
                js.cancel(ID)
                return
            }
            js.schedule(
                JobInfo.Builder(ID, ComponentName(c, Releve::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(m * 60_000L)
                    .setPersisted(true)
                    .build(),
            )
        }

        fun canaux(c: Context) {
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("mails", "Nouveaux mails", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        /** Prévenir des nouveaux mails non lus (pas au tout premier relevé d'une boîte : ce serait une avalanche). */
        fun notifier(c: Context, compte: Compte, nouveaux: List<Lettre>) {
            canaux(c)
            val nm = c.getSystemService(NotificationManager::class.java)
            nouveaux.filter { !it.lu }.sortedByDescending { it.date }.take(5).forEach { l ->
                val complet = Base.lettre(c, l.compte, l.dossier, l.uid) ?: l
                val verdict = Bouclier.mail(complet.de, complet.deNom, complet.sujet, complet.texte ?: complet.extrait, false)
                val ouvrir = Intent(c, Mail::class.java).setAction("africa.samaos.mail.LIRE")
                    .putExtra("compte", l.compte).putExtra("dossier", l.dossier).putExtra("uid", l.uid)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val n = Notification.Builder(c, "mails")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(if (verdict != null) "${verdict.titre} · ${l.nomAffiche}" else l.nomAffiche)
                    .setContentText(l.sujet.ifBlank { "(sans objet)" })
                    .setSubText(compte.adresse)
                    .setStyle(Notification.BigTextStyle().bigText(listOf(l.sujet, complet.extrait).filter { it.isNotBlank() }.joinToString("\n")))
                    .setContentIntent(PendingIntent.getActivity(c, l.uid.toInt(), ouvrir, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    .setAutoCancel(true)
                    .setWhen(l.date)
                    .setShowWhen(true)
                    .setGroup("mails-${compte.id}")
                    .build()
                try {
                    nm.notify("${l.compte}/${l.dossier}".hashCode() + l.uid.toInt(), n)
                } catch (_: SecurityException) {
                }
            }
        }
    }

    override fun onStartJob(p: JobParameters): Boolean {
        thread {
            try {
                Courrier.envoyerEnAttente(this)
                val data = getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
                Comptes.liste(this).forEach { compte ->
                    try {
                        val premier = Base.uids(this, compte.id, "INBOX").isEmpty()
                        val nouveaux = Courrier.relever(this, compte, "INBOX", nombre = 30, avecCorps = !data || Preferences.texteSurData(this))
                        if (!premier) notifier(this, compte, nouveaux)
                    } catch (_: Exception) {
                    }
                }
            } finally {
                jobFinished(p, false)
            }
        }
        return true
    }

    override fun onStopJob(p: JobParameters) = true
}
