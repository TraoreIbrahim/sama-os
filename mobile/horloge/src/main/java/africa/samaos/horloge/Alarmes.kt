package africa.samaos.horloge

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Une alarme : l'heure, le nom, les jours (vide : une seule fois), allumée ou non. */
data class Alarme(val id: Int, val heure: Int, val minute: Int, val nom: String, val jours: Set<DayOfWeek>, val active: Boolean)

/**
 * Les alarmes et le minuteur de l'Horloge. Les alarmes passent par setAlarmClock d'Android : elles sonnent
 * à l'heure exacte, téléphone en veille, et le système affiche la prochaine dans le Pouls.
 */
object Alarmes {
    private fun prefs(c: Context) = c.createDeviceProtectedStorageContext().getSharedPreferences("horloge", Context.MODE_PRIVATE)

    fun liste(c: Context): List<Alarme> = try {
        val a = JSONArray(prefs(c).getString("alarmes", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Alarme(
                o.getInt("id"), o.getInt("h"), o.getInt("m"), o.optString("nom"),
                o.optJSONArray("jours")?.let { j -> (0 until j.length()).map { DayOfWeek.of(j.getInt(it)) }.toSet() }.orEmpty(),
                o.optBoolean("active", true),
            )
        }.sortedWith(compareBy({ it.heure }, { it.minute }))
    } catch (_: Exception) {
        emptyList()
    }

    private fun ecrire(c: Context, l: List<Alarme>) {
        val a = JSONArray()
        l.forEach { x ->
            a.put(JSONObject().put("id", x.id).put("h", x.heure).put("m", x.minute).put("nom", x.nom).put("jours", JSONArray(x.jours.map { it.value })).put("active", x.active))
        }
        prefs(c).edit().putString("alarmes", a.toString()).apply()
        programmer(c)
    }

    fun garder(c: Context, a: Alarme) = ecrire(c, liste(c).filter { it.id != a.id } + a)

    fun supprimer(c: Context, id: Int) = ecrire(c, liste(c).filter { it.id != id })

    fun nouvelId(c: Context) = (liste(c).maxOfOrNull { it.id } ?: 0) + 1

    /** Le prochain moment où cette alarme sonne. */
    fun prochaine(a: Alarme, depuis: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        var t = depuis.withHour(a.heure).withMinute(a.minute).withSecond(0).withNano(0)
        if (!t.isAfter(depuis)) t = t.plusDays(1)
        if (a.jours.isNotEmpty()) while (t.dayOfWeek !in a.jours) t = t.plusDays(1)
        return t
    }

    /** « dans 7 h 12 min, demain à 05:30 ». */
    fun dans(t: ZonedDateTime): String {
        val min = ChronoUnit.MINUTES.between(ZonedDateTime.now(), t) + 1
        val h = min / 60
        val m = min % 60
        val duree = if (h > 0) "$h h ${"%02d".format(m)} min" else "$m min"
        val jour = when (t.toLocalDate()) {
            java.time.LocalDate.now() -> "aujourd'hui"
            java.time.LocalDate.now().plusDays(1) -> "demain"
            else -> t.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)
        }
        return "dans $duree, $jour à ${"%02d:%02d".format(t.hour, t.minute)}"
    }

    /** Programme auprès d'Android la plus proche des alarmes allumées. */
    fun programmer(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val suivante = liste(c).filter { it.active }.minByOrNull { prochaine(it).toInstant() }
        val operation = PendingIntent.getBroadcast(c, 1, Intent(c, Reveil::class.java).setAction(ALARME), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (suivante == null) {
            am.cancel(operation)
            return
        }
        prefs(c).edit().putInt("prochaine", suivante.id).apply()
        val montrer = PendingIntent.getActivity(c, 2, Intent(c, Horloge::class.java), PendingIntent.FLAG_IMMUTABLE)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(prochaine(suivante).toInstant().toEpochMilli(), montrer), operation)
        } catch (_: SecurityException) {
        }
    }

    /** Le minuteur : sa fin, gardée pour qu'il sonne même appli fermée. */
    fun lancerMinuteur(c: Context, nom: String, ms: Long) {
        val fin = System.currentTimeMillis() + ms
        prefs(c).edit().putLong("minuteur_fin", fin).putString("minuteur_nom", nom).putLong("minuteur_duree", ms).apply()
        val operation = PendingIntent.getBroadcast(c, 3, Intent(c, Reveil::class.java).setAction(MINUTEUR), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fin, operation)
        } catch (_: SecurityException) {
        }
    }

    fun arreterMinuteur(c: Context) {
        prefs(c).edit().remove("minuteur_fin").remove("minuteur_pause").apply()
        val operation = PendingIntent.getBroadcast(c, 3, Intent(c, Reveil::class.java).setAction(MINUTEUR), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        c.getSystemService(AlarmManager::class.java).cancel(operation)
    }

    fun pauseMinuteur(c: Context) {
        val reste = finMinuteur(c)?.let { it - System.currentTimeMillis() } ?: return
        arreterMinuteur(c)
        prefs(c).edit().putLong("minuteur_pause", reste).apply()
    }

    fun finMinuteur(c: Context) = prefs(c).getLong("minuteur_fin", 0).takeIf { it > 0 }
    fun pauseRestante(c: Context) = prefs(c).getLong("minuteur_pause", 0).takeIf { it > 0 }
    fun nomMinuteur(c: Context) = prefs(c).getString("minuteur_nom", "Minuteur").orEmpty()

    fun reporter(c: Context, minutes: Int) {
        val operation = PendingIntent.getBroadcast(c, 4, Intent(c, Reveil::class.java).setAction(ALARME_REPORTEE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val t = System.currentTimeMillis() + minutes * 60_000L
        val montrer = PendingIntent.getActivity(c, 2, Intent(c, Horloge::class.java), PendingIntent.FLAG_IMMUTABLE)
        try {
            c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(t, montrer), operation)
        } catch (_: SecurityException) {
        }
    }

    fun alarmeProgrammee(c: Context): Alarme? = prefs(c).getInt("prochaine", -1).let { id -> liste(c).firstOrNull { it.id == id } }

    // Le coucher
    fun coucher(c: Context) = prefs(c).getInt("coucher", 22 * 60 + 30)
    fun lever(c: Context) = prefs(c).getInt("lever", 6 * 60)
    fun reglerCoucher(c: Context, minutes: Int) = prefs(c).edit().putInt("coucher", minutes).apply()
    fun reglerLever(c: Context, minutes: Int) = prefs(c).edit().putInt("lever", minutes).apply()

    const val ALARME = "africa.samaos.horloge.ALARME"
    const val ALARME_REPORTEE = "africa.samaos.horloge.REPORTEE"
    const val MINUTEUR = "africa.samaos.horloge.MINUTEUR"
    const val CANAL = "sonneries"

    fun canal(c: Context) {
        c.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CANAL, "Alarmes et minuteurs", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
    }
}

/** L'heure est venue : l'écran de sonnerie s'ouvre (plein écran, même verrouillé). */
class Reveil : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val alarme = if (i.action == Alarmes.ALARME) Alarmes.alarmeProgrammee(c) else null
        // Une alarme d'un seul jour s'éteint après avoir sonné ; les autres sont reprogrammées.
        if (alarme != null && alarme.jours.isEmpty()) Alarmes.garder(c, alarme.copy(active = false)) else Alarmes.programmer(c)
        if (i.action == Alarmes.MINUTEUR) Alarmes.arreterMinuteur(c)
        val titre = when (i.action) {
            Alarmes.MINUTEUR -> Alarmes.nomMinuteur(c)
            else -> alarme?.nom?.ifBlank { null } ?: "Alarme"
        }
        val ecran = Intent(c, Sonnerie::class.java).putExtra("titre", titre).putExtra("minuteur", i.action == Alarmes.MINUTEUR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Alarmes.canal(c)
        val plein = PendingIntent.getActivity(c, 5, ecran, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, Alarmes.CANAL)
            .setSmallIcon(R.drawable.ic_notif_horloge)
            .setContentTitle(titre)
            .setContentText(if (i.action == Alarmes.MINUTEUR) "Le minuteur est fini" else LocalDateTime.now(ZoneId.systemDefault()).toLocalTime().withNano(0).withSecond(0).toString())
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(plein, true)
            .setContentIntent(plein)
            .setOngoing(true)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(9, n)
        try {
            c.startActivity(ecran)
        } catch (_: Exception) {
        }
    }
}

/** Au démarrage du téléphone ou au changement d'heure, les alarmes sont reprogrammées. */
class Demarrage : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Alarmes.programmer(c)
}
