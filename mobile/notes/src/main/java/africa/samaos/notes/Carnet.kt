package africa.samaos.notes

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Le genre d'un paragraphe : comme dans les Notes de l'iPhone, on mélange titres, texte et listes dans une même note. */
enum class Genre { TITRE, INTERTITRE, TEXTE, PUCE, NUMERO, CASE }

val Genre.liste get() = this == Genre.PUCE || this == Genre.NUMERO || this == Genre.CASE

private val compteur = AtomicLong(System.currentTimeMillis() * 1000)

/** Un paragraphe de note (un élément de liste, un titre, un texte de plusieurs lignes). */
data class Bloc(val genre: Genre, val texte: String, val coche: Boolean = false, val id: Long = compteur.incrementAndGet())

data class Note(
    /** L'heure de création. */
    val id: Long,
    val titre: String,
    val blocs: List<Bloc>,
    val dossier: Long = Carnet.NOTES,
    val epinglee: Boolean = false,
    val rappel: Long? = null,
    val modifiee: Long = id,
    /** Le contenu chiffré d'une note verrouillée (il ne s'ouvre qu'avec le code du téléphone). */
    val secret: String? = null,
    /** Dans « Supprimées récemment » depuis. */
    val supprimee: Long? = null,
) {
    val verrouillee get() = secret != null
    val cases get() = blocs.filter { it.genre == Genre.CASE }

    /** Le texte, ligne à ligne : la recherche, le partage, la notification. */
    fun enTexte(puces: Boolean = true): String {
        var n = 0
        return blocs.joinToString("\n") { b ->
            if (b.genre == Genre.NUMERO) n++ else n = 0
            if (!puces) b.texte else when (b.genre) {
                Genre.PUCE -> "• " + b.texte
                Genre.NUMERO -> "$n. " + b.texte
                Genre.CASE -> (if (b.coche) "☑ " else "☐ ") + b.texte
                else -> b.texte
            }
        }
    }

    /** La première ligne écrite après le titre. */
    fun apercu(): String = if (verrouillee) "" else blocs.asSequence().flatMap { it.texte.lineSequence() }.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    fun vide() = titre.isBlank() && blocs.all { it.texte.isBlank() } && secret == null
}

data class Dossier(val id: Long, val nom: String, val teinte: Int)

/** Les notes et les dossiers, gardés sur le téléphone (dans cet Espace). */
object Carnet {
    /** Le dossier « Notes », toujours là. */
    const val NOTES = 0L
    private const val TRENTE_JOURS = 30L * 24 * 3600 * 1000

    var notes by mutableStateOf(emptyList<Note>())
        private set
    var dossiers by mutableStateOf(emptyList<Dossier>())
        private set

    private fun fichier(c: Context) = File(c.filesDir, "notes.json")

    /** Relit le carnet ; les notes jetées depuis plus de 30 jours partent pour de bon. */
    fun charger(c: Context) {
        val (n, d) = lire(c)
        val limite = System.currentTimeMillis() - TRENTE_JOURS
        notes = n.filter { (it.supprimee ?: Long.MAX_VALUE) > limite }
        dossiers = d
        if (notes.size != n.size) ecrire(c)
    }

    /** Le fichier : l'ancien format (une liste de notes, texte ou liste à cocher) est repris tel quel. */
    fun lire(c: Context): Pair<List<Note>, List<Dossier>> = try {
        val t = fichier(c).readText().trim()
        if (t.startsWith("[")) {
            JSONArray(t).let { a -> (0 until a.length()).map { ancienne(a.getJSONObject(it)) } } to emptyList()
        } else {
            val o = JSONObject(t)
            val n = o.getJSONArray("notes").let { a -> (0 until a.length()).map { note(a.getJSONObject(it)) } }
            val d = o.optJSONArray("dossiers")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { x -> Dossier(x.getLong("id"), x.getString("nom"), x.optInt("teinte")) } } }.orEmpty()
            n to d
        }
    } catch (_: Exception) {
        emptyList<Note>() to emptyList()
    }

    private fun ancienne(o: JSONObject): Note {
        val liste = o.optJSONArray("liste")
        val blocs = if (liste != null) {
            (0 until liste.length()).map { liste.getJSONObject(it).let { e -> Bloc(Genre.CASE, e.getString("t"), e.getBoolean("c")) } }
        } else {
            listOf(Bloc(Genre.TEXTE, o.optString("texte")))
        }
        val id = o.getLong("id")
        return Note(id, o.optString("titre"), blocs, rappel = o.optLong("rappel").takeIf { it > 0 }, modifiee = o.optLong("modifiee", id))
    }

    fun blocsDe(a: JSONArray): List<Bloc> = (0 until a.length()).map { i ->
        a.getJSONObject(i).let { b -> Bloc(runCatching { Genre.valueOf(b.getString("g")) }.getOrDefault(Genre.TEXTE), b.optString("t"), b.optBoolean("c")) }
    }

    fun json(blocs: List<Bloc>) = JSONArray(blocs.map { b -> JSONObject().put("g", b.genre.name).put("t", b.texte).apply { if (b.coche) put("c", true) } })

    private fun note(o: JSONObject): Note {
        val id = o.getLong("id")
        return Note(
            id, o.optString("titre"), o.optJSONArray("blocs")?.let { blocsDe(it) }.orEmpty(),
            dossier = o.optLong("dossier", NOTES), epinglee = o.optBoolean("epinglee"), rappel = o.optLong("rappel").takeIf { it > 0 },
            modifiee = o.optLong("modifiee", id), secret = o.optString("secret").takeIf { it.isNotEmpty() }, supprimee = o.optLong("supprimee").takeIf { it > 0 },
        )
    }

    private fun ecrire(c: Context) {
        val o = JSONObject().put("v", 2)
            .put("dossiers", JSONArray(dossiers.map { JSONObject().put("id", it.id).put("nom", it.nom).put("teinte", it.teinte) }))
            .put(
                "notes",
                JSONArray(
                    notes.map { n ->
                        JSONObject().put("id", n.id).put("titre", n.titre).put("blocs", json(n.blocs)).put("dossier", n.dossier).put("modifiee", n.modifiee)
                            .apply {
                                if (n.epinglee) put("epinglee", true)
                                n.rappel?.let { put("rappel", it) }
                                n.secret?.let { put("secret", it) }
                                n.supprimee?.let { put("supprimee", it) }
                            }
                    },
                ),
            )
        // Écrit à côté puis renommé : une coupure au milieu ne perd pas le carnet.
        val f = fichier(c)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(f)
    }

    fun note(id: Long) = notes.firstOrNull { it.id == id }

    /** Garde une note (une note vide n'est pas gardée). */
    fun garder(c: Context, n: Note) {
        val reste = notes.filter { it.id != n.id }
        notes = if (n.vide()) reste else reste + n
        ecrire(c)
        if (n.vide()) annuler(c, n.id) else programmer(c, n)
    }

    /** Change une note sans la dater de nouveau (épingler, déplacer). */
    fun changer(c: Context, id: Long, f: (Note) -> Note) {
        notes = notes.map { if (it.id == id) f(it) else it }
        ecrire(c)
        note(id)?.let { programmer(c, it) }
    }

    /** Vers « Supprimées récemment » : elle y reste 30 jours. */
    fun jeter(c: Context, id: Long) {
        changer(c, id) { it.copy(supprimee = System.currentTimeMillis(), epinglee = false) }
        annuler(c, id)
    }

    fun recuperer(c: Context, id: Long) {
        // Son dossier a pu disparaître entre-temps : elle revient dans « Notes ».
        changer(c, id) { n -> n.copy(supprimee = null, dossier = if (dossiers.any { it.id == n.dossier }) n.dossier else NOTES) }
    }

    fun effacer(c: Context, id: Long) {
        notes = notes.filter { it.id != id }
        ecrire(c)
        annuler(c, id)
    }

    fun viderCorbeille(c: Context) {
        notes = notes.filter { it.supprimee == null }
        ecrire(c)
    }

    fun creerDossier(c: Context, nom: String, teinte: Int): Dossier {
        val d = Dossier(System.currentTimeMillis(), nom.trim(), teinte)
        dossiers = dossiers + d
        ecrire(c)
        return d
    }

    fun changerDossier(c: Context, d: Dossier) {
        dossiers = dossiers.map { if (it.id == d.id) d else it }
        ecrire(c)
    }

    /** Le dossier part ; ses notes vont dans « Supprimées récemment » (on peut les récupérer). */
    fun supprimerDossier(c: Context, id: Long) {
        val maintenant = System.currentTimeMillis()
        notes = notes.map { if (it.dossier == id && it.supprimee == null) it.copy(supprimee = maintenant, dossier = NOTES, epinglee = false) else if (it.dossier == id) it.copy(dossier = NOTES) else it }
        dossiers = dossiers.filter { it.id != id }
        ecrire(c)
    }

    fun nomDossier(id: Long) = if (id == NOTES) "Notes" else dossiers.firstOrNull { it.id == id }?.nom ?: "Notes"

    fun teinteDossier(id: Long) = if (id == NOTES) 0 else dossiers.firstOrNull { it.id == id }?.teinte ?: 0

    private fun intention(c: Context, id: Long) =
        PendingIntent.getBroadcast(c, id.toInt(), Intent(c, Rappel::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun programmer(c: Context, n: Note) {
        val r = n.rappel
        if (r == null || r < System.currentTimeMillis() || n.supprimee != null) {
            annuler(c, n.id)
            return
        }
        try {
            c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r, intention(c, n.id))
        } catch (_: SecurityException) {
            c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r, intention(c, n.id))
        }
    }

    fun annuler(c: Context, id: Long) = c.getSystemService(AlarmManager::class.java).cancel(intention(c, id))
}

/** Le verrou des notes : leur contenu est chiffré (clé du coffre d'Android) et ne s'ouvre qu'avec le code du téléphone. */
object Verrou {
    private const val CLE = "sama_notes"

    /** Ouvert avec le code depuis que l'appli est au premier plan ; il se referme quand on la quitte. */
    var ouvert by mutableStateOf(false)

    fun protege(c: Context) = c.getSystemService(KeyguardManager::class.java).isDeviceSecure

    @Suppress("DEPRECATION")
    fun demande(c: Context): Intent? = c.getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent("Note verrouillée", "Entrez le code du téléphone pour l'ouvrir")

    private fun cle(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(CLE, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(CLE, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }

    fun fermer(blocs: List<Bloc>): String {
        val ch = Cipher.getInstance("AES/GCM/NoPadding")
        ch.init(Cipher.ENCRYPT_MODE, cle())
        return Base64.encodeToString(ch.iv + ch.doFinal(Carnet.json(blocs).toString().toByteArray()), Base64.NO_WRAP)
    }

    fun ouvrir(secret: String): List<Bloc>? = try {
        val b = Base64.decode(secret, Base64.NO_WRAP)
        val ch = Cipher.getInstance("AES/GCM/NoPadding")
        ch.init(Cipher.DECRYPT_MODE, cle(), GCMParameterSpec(128, b, 0, 12))
        Carnet.blocsDe(JSONArray(String(ch.doFinal(b, 12, b.size - 12))))
    } catch (_: Exception) {
        null
    }
}

/** Les dates à la manière d'un carnet : l'heure aujourd'hui, « Hier », le jour de la semaine, puis la date. */
object Dates {
    private val zone get() = ZoneId.systemDefault()
    private fun jour(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun courte(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        val ecart = ChronoUnit.DAYS.between(d.toLocalDate(), LocalDate.now())
        return when {
            ecart == 0L -> d.format(DateTimeFormatter.ofPattern("HH:mm"))
            ecart == 1L -> "Hier"
            ecart in 2..6 -> d.format(DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH))
            else -> d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }

    /** « 8 octobre 2026 à 02:40 », en tête d'une note. */
    fun longue(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH))

    /** La section d'une note dans la liste. */
    fun section(ms: Long): String {
        val j = jour(ms)
        val auj = LocalDate.now()
        val ecart = ChronoUnit.DAYS.between(j, auj)
        return when {
            ecart <= 0L -> "Aujourd'hui"
            ecart == 1L -> "Hier"
            ecart < 7 -> "7 jours précédents"
            ecart < 30 -> "30 jours précédents"
            j.year == auj.year -> j.format(DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
            else -> j.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
        }
    }

    /** Un rappel : « Aujourd'hui 19:00 », « Demain 08:00 », « Sam. 11 oct. 09:00 ». */
    fun rappel(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        val auj = LocalDate.now()
        val j = when (d.toLocalDate()) {
            auj -> "Aujourd'hui"
            auj.plusDays(1) -> "Demain"
            else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
        }
        return "$j ${d.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }

    /** « il y a 3 jours », pour la corbeille, et ce qui reste avant qu'elle parte. */
    fun restant(supprimee: Long): String {
        val reste = 30 - ChronoUnit.DAYS.between(jour(supprimee), LocalDate.now())
        return if (reste <= 1) "part demain" else "encore $reste jours"
    }
}

/** Le rappel d'une note : une notification qui l'ouvre. Une note verrouillée ne montre que son titre. */
class Rappel : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", -1)
        val n = Carnet.lire(c).first.firstOrNull { it.id == id && it.supprimee == null } ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rappels", "Rappels", NotificationManager.IMPORTANCE_HIGH))
        val ouvrir = PendingIntent.getActivity(c, id.toInt(), Intent(c, Notes::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE)
        val texte = if (n.verrouillee) "Note verrouillée" else n.enTexte().lines().filter { it.isNotBlank() && !it.startsWith("☑") }.take(4).joinToString(" · ")
        nm.notify(
            id.toInt(),
            Notification.Builder(c, "rappels")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(n.titre.ifBlank { "Rappel" })
                .setContentText(texte)
                .setContentIntent(ouvrir)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class Redemarrage : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Carnet.lire(c).first.forEach { Carnet.programmer(c, it) }
}
