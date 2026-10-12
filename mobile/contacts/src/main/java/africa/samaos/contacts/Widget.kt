package africa.samaos.contacts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.ContactsContract
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.OutilsWidget
import africa.samaos.banco.appli.couleurDe
import africa.samaos.banco.appli.initiales
import java.util.concurrent.Executors

// Le widget de Contacts pour l'Accueil : les proches marqués d'une étoile, qu'un toucher appelle.

/** « Mes proches » : jusqu'à cinq contacts étoilés ; un toucher appelle leur numéro principal. */
class WidgetProches : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val fin = goAsync()
        WidgetsContacts.fil.execute {
            try {
                WidgetsContacts.dessiner(context.applicationContext)
            } catch (_: Exception) {
            } finally {
                fin.finish()
            }
        }
    }
}

object WidgetsContacts {
    internal val fil = Executors.newSingleThreadExecutor()

    private class Proche(val id: Long, val nom: String, val photo: Bitmap?, val numero: String?)

    /** Les étoiles ou les photos ont pu changer : le widget se redessine. */
    fun annoncer(c: Context) {
        val a = c.applicationContext
        fil.execute {
            try {
                dessiner(a)
            } catch (_: Exception) {
            }
        }
    }

    private fun proches(c: Context): List<Proche> {
        val l = mutableListOf<Proche>()
        try {
            c.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
                "${ContactsContract.Contacts.STARRED} = 1", null, "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { cur ->
                while (cur.moveToNext() && l.size < 5) {
                    val id = cur.getLong(0)
                    val photo = cur.getString(2)?.let { u ->
                        try {
                            c.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it) }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    l += Proche(id, cur.getString(1).orEmpty(), photo, numero(c, id))
                }
            }
        } catch (_: SecurityException) {
        }
        return l
    }

    /** Le numéro principal du contact, sinon le premier. */
    private fun numero(c: Context, id: Long): String? = c.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id.toString()),
        "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
    )?.use { if (it.moveToFirst()) it.getString(0) else null }

    internal fun dessiner(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        val ids = m.getAppWidgetIds(ComponentName(c, WidgetProches::class.java))
        if (ids.isEmpty()) return
        val nuit = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val rv = RemoteViews(c.packageName, R.layout.widget_proches)
        rv.removeAllViews(R.id.racine)
        val l = proches(c)
        val ouvrirContacts = PendingIntent.getActivity(c, 0, Intent(c, Contacts::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.racine, ouvrirContacts)
        if (l.isEmpty()) {
            rv.addView(R.id.racine, RemoteViews(c.packageName, R.layout.widget_proches_vide))
            rv.setContentDescription(R.id.racine, "Mes proches : mettez une étoile à vos proches dans Contacts.")
        }
        l.forEach { p ->
            val e = RemoteViews(c.packageName, R.layout.widget_proche)
            e.setImageViewBitmap(R.id.rond, rond(c, p, nuit))
            e.setTextViewText(R.id.nom, p.nom.substringBefore(' ').ifBlank { p.nom })
            // Un toucher appelle ; sans numéro, la fiche s'ouvre.
            val action = p.numero?.let { Intent(Intent.ACTION_CALL, Uri.fromParts("tel", it, null)) }
                ?: Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, p.id)).setClass(c, Contacts::class.java)
            e.setOnClickPendingIntent(R.id.proche, PendingIntent.getActivity(c, (p.id % 100_000).toInt() + 1, action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            e.setContentDescription(R.id.proche, if (p.numero != null) "Appeler ${p.nom}" else "Voir la fiche de ${p.nom}")
            rv.addView(R.id.racine, e)
        }
        ids.forEach { m.updateAppWidget(it, rv) }
    }

    /** Le rond d'un proche, aux couleurs de Contacts, avec le combiné vert en bas à droite. */
    private fun rond(c: Context, p: Proche, nuit: Boolean): Bitmap {
        val coul = couleurDe(p.nom)
        val fond = if (nuit) lerp(Color(0xFF1C2421), coul, 0.42f) else lerp(Color.White, coul, 0.17f)
        val encre = if (nuit) lerp(coul, Color.White, 0.62f) else lerp(coul, Color.Black, 0.12f)
        val base = OutilsWidget.rond(c, 54f, p.photo, initiales(p.nom), fond.toArgb(), encre.toArgb())
        if (p.numero == null) return base
        val bmp = base.copy(Bitmap.Config.ARGB_8888, true)
        val toile = Canvas(bmp)
        val d = c.resources.displayMetrics.density
        val r = 10 * d
        val cx = bmp.width - r
        val cy = bmp.height - r
        toile.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (nuit) 0xFF261F19.toInt() else 0xFFFFFCF8.toInt() })
        toile.drawCircle(cx, cy, r - 2 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E8A6E.toInt() })
        val combine = OutilsWidget.picto(c, Icones.APPEL, 11f, 0xFFFFFFFF.toInt(), 2f)
        toile.drawBitmap(combine, cx - combine.width / 2f, cy - combine.height / 2f, null)
        return bmp
    }
}
