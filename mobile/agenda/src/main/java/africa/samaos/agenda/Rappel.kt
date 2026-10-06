package africa.samaos.agenda

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * L'heure d'un rappel : le fournisseur d'agendas d'Android l'annonce (EVENT_REMINDER) ; l'Agenda affiche
 * la notification de chaque événement concerné, puis le marque comme prévenu.
 */
class Rappel : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != CalendarContract.ACTION_EVENT_REMINDER) return
        val attente = goAsync()
        Thread {
            try {
                prevenir(c, i.getLongExtra(CalendarAlerts.ALARM_TIME, 0))
            } finally {
                attente.finish()
            }
        }.start()
    }

    private fun prevenir(c: Context, quand: Long) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Rappels de l'agenda", NotificationManager.IMPORTANCE_HIGH))
        val choix = if (quand > 0) "${CalendarAlerts.ALARM_TIME} <= ? AND ${CalendarAlerts.STATE} = ?" else "${CalendarAlerts.STATE} = ?"
        val args = if (quand > 0) arrayOf(quand.toString(), CalendarAlerts.STATE_SCHEDULED.toString()) else arrayOf(CalendarAlerts.STATE_SCHEDULED.toString())
        try {
            c.contentResolver.query(
                CalendarAlerts.CONTENT_URI,
                arrayOf(CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.TITLE, CalendarAlerts.BEGIN, CalendarAlerts.EVENT_LOCATION, CalendarAlerts.ALL_DAY),
                choix, args, null,
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val alerte = cur.getLong(0)
                    val evenement = cur.getLong(1)
                    val debut = cur.getLong(3)
                    val journee = cur.getInt(5) == 1
                    val heure = if (journee) "Toute la journée"
                    else Instant.ofEpochMilli(debut).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    val texte = listOfNotNull(heure, cur.getString(4)?.ifBlank { null }).joinToString(" · ")
                    val ouvrir = PendingIntent.getActivity(
                        c, evenement.toInt(),
                        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, evenement)).setClass(c, Agenda::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    nm.notify(
                        "rappel", evenement.toInt(),
                        Notification.Builder(c, CANAL)
                            .setSmallIcon(R.drawable.ic_notif)
                            .setContentTitle(cur.getString(2)?.ifBlank { null } ?: "Événement")
                            .setContentText(texte)
                            .setCategory(Notification.CATEGORY_EVENT)
                            .setWhen(debut)
                            .setShowWhen(!journee)
                            .setContentIntent(ouvrir)
                            .setAutoCancel(true)
                            .build(),
                    )
                    c.contentResolver.update(
                        ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, alerte),
                        ContentValues().apply { put(CalendarAlerts.STATE, CalendarAlerts.STATE_FIRED) }, null, null,
                    )
                }
            }
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CANAL = "rappels"
    }
}
