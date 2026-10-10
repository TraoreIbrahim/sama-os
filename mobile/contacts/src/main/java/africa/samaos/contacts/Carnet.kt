package africa.samaos.contacts

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.graphics.BitmapFactory
import android.telephony.SubscriptionManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.text.Collator
import java.util.Locale

/** Un contact de la liste : sa vignette (photo ou avatar enregistré) et son lien (« Maman », « Collègue »). */
class Contact(val id: Long, val cle: String, val nom: String, val favori: Boolean, val photo: ImageBitmap? = null, val lien: String? = null) {
    val groupe: String? get() = Carnet.groupeDe(lien)

    /** Comment l'appeler en un mot : le lien de famille (« Maman »), sinon le prénom. */
    val appel: String get() = lien?.takeIf { Carnet.groupeDe(it) == "Famille" && it in Carnet.LIENS_UN_MOT } ?: nom.substringBefore(' ')
}

/** Un numéro d'une fiche : le numéro, son type, et s'il est préféré. */
class Numero(val id: Long, val numero: String, val type: Int, val prefere: Boolean)

/** La fiche d'un contact. */
class Fiche(
    val id: Long,
    val cle: String,
    val nom: String,
    val prenom: String,
    val nomFamille: String,
    val favori: Boolean,
    val numeros: List<Numero>,
    val emails: List<String>,
    val anniversaire: String?,
    val compte: String?,
    val brut: Long?,
    val lien: String? = null,
    val adresse: String? = null,
    val photo: ImageBitmap? = null,
)

/** Le carnet d'adresses d'Android, lu et écrit pour les Contacts de Sama. */
object Carnet {
    private val tri = Collator.getInstance(Locale.FRENCH)

    /** Le lien avec la personne (« Maman », « Collègue ») : une ligne de Sama dans la fiche, que le carnet garde. */
    const val MIME_LIEN = "vnd.android.cursor.item/vnd.africa.samaos.lien"

    val LIENS = listOf(
        "Maman", "Papa", "Sœur", "Frère", "Enfant", "Conjoint·e", "Cousin·e", "Oncle", "Tante", "Grand-parent",
        "Ami·e", "Voisin·e", "Collègue", "Patron·ne", "Client·e", "Professeur·e", "Camarade",
    )

    /** Les liens qu'on dit seuls pour appeler quelqu'un : « Maman », « Papa ». */
    val LIENS_UN_MOT = setOf("Maman", "Papa", "Tonton", "Tata")

    val GROUPES = listOf("Famille", "Travail", "École")

    fun groupeDe(lien: String?): String? = when (lien) {
        null -> null
        "Maman", "Papa", "Sœur", "Frère", "Enfant", "Conjoint·e", "Cousin·e", "Oncle", "Tante", "Grand-parent" -> "Famille"
        "Collègue", "Patron·ne", "Client·e" -> "Travail"
        "Professeur·e", "Camarade" -> "École"
        else -> null
    }

    private fun vignette(c: Context, uri: String?): ImageBitmap? = uri?.let {
        try {
            c.contentResolver.openInputStream(Uri.parse(it))?.use { e -> BitmapFactory.decodeStream(e)?.asImageBitmap() }
        } catch (_: Exception) {
            null
        }
    }

    /** Les liens de tous les contacts, d'une seule lecture. */
    private fun liens(c: Context): Map<Long, String> {
        val m = HashMap<Long, String>()
        try {
            c.contentResolver.query(Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, Data.DATA1), "${Data.MIMETYPE} = ?", arrayOf(MIME_LIEN), null)
                ?.use { cur -> while (cur.moveToNext()) cur.getString(1)?.takeIf { it.isNotBlank() }?.let { m[cur.getLong(0)] = it } }
        } catch (_: Exception) {
        }
        return m
    }

    fun liste(c: Context, vignettes: Boolean = true): List<Contact> {
        val l = mutableListOf<Contact>()
        val liens = liens(c)
        try {
            c.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ),
                null, null, null,
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getLong(0)
                    l += Contact(id, cur.getString(1).orEmpty(), cur.getString(2).orEmpty(), cur.getInt(3) == 1, if (vignettes) vignette(c, cur.getString(4)) else null, liens[id])
                }
            }
        } catch (_: SecurityException) {
        }
        return l.filter { it.nom.isNotBlank() }.sortedWith { a, b -> tri.compare(a.nom, b.nom) }
    }

    /** La lettre de rangement : sans accent, « # » pour ce qui ne commence pas par une lettre. */
    fun lettre(nom: String): String {
        val p = java.text.Normalizer.normalize(nom.take(1), java.text.Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: '#'
        return if (p in 'A'..'Z') p.toString() else "#"
    }

    fun idDepuis(c: Context, uri: Uri?): Long? {
        if (uri == null) return null
        return try {
            val vrai = ContactsContract.Contacts.lookupContact(c.contentResolver, uri) ?: uri
            c.contentResolver.query(vrai, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
        } catch (_: Exception) {
            try {
                ContentUris.parseId(uri)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun fiche(c: Context, id: Long): Fiche? {
        var cle = ""
        var nom = ""
        var favori = false
        try {
            c.contentResolver.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.STARRED),
                null, null, null,
            )?.use { cur ->
                if (!cur.moveToFirst()) return null
                cle = cur.getString(0).orEmpty()
                nom = cur.getString(1).orEmpty()
                favori = cur.getInt(2) == 1
            } ?: return null
        } catch (_: SecurityException) {
            return null
        }
        val numeros = mutableListOf<Numero>()
        val emails = mutableListOf<String>()
        var anniversaire: String? = null
        var prenom = ""
        var famille = ""
        var brut: Long? = null
        var compte: String? = null
        var lien: String? = null
        var adresse: String? = null
        c.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.IS_SUPER_PRIMARY, Data.RAW_CONTACT_ID, RawContacts.ACCOUNT_TYPE),
            "${Data.CONTACT_ID} = ?", arrayOf(id.toString()), null,
        )?.use { cur ->
            while (cur.moveToNext()) {
                brut = brut ?: cur.getLong(6)
                compte = compte ?: cur.getString(7)
                when (cur.getString(1)) {
                    Phone.CONTENT_ITEM_TYPE -> numeros += Numero(cur.getLong(0), cur.getString(2).orEmpty(), cur.getInt(3), cur.getInt(5) == 1)
                    Email.CONTENT_ITEM_TYPE -> cur.getString(2)?.let { emails += it }
                    Event.CONTENT_ITEM_TYPE -> if (cur.getInt(3) == Event.TYPE_BIRTHDAY) anniversaire = cur.getString(2)
                    StructuredName.CONTENT_ITEM_TYPE -> {
                        prenom = cur.getString(3).orEmpty()
                        famille = cur.getString(4).orEmpty()
                    }
                    MIME_LIEN -> lien = cur.getString(2)?.ifBlank { null }
                    StructuredPostal.CONTENT_ITEM_TYPE -> adresse = cur.getString(2)?.ifBlank { null }
                }
            }
        }
        // La grande photo de la fiche (sinon la vignette).
        val photo = try {
            ContactsContract.Contacts.openContactPhotoInputStream(c.contentResolver, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), true)
                ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (_: Exception) {
            null
        }
        return Fiche(
            id, cle, nom, prenom, famille, favori, numeros.distinctBy { it.numero.filter { ch -> ch.isDigit() } }, emails.distinct(),
            anniversaire, compte, brut, lien, adresse, photo,
        )
    }

    fun favori(c: Context, id: Long, oui: Boolean) {
        c.contentResolver.update(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
            android.content.ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (oui) 1 else 0) },
            null, null,
        )
    }

    fun supprimer(c: Context, id: Long) {
        c.contentResolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), null, null)
    }

    /**
     * Enregistre un contact : neuf, ou en remplaçant le nom, les numéros et l'e-mail d'une fiche existante.
     * Les contacts créés par Sama restent sur le téléphone (compte local) tant qu'il n'y a pas de compte Sama.
     */
    fun enregistrer(
        c: Context,
        brut: Long?,
        prenom: String,
        famille: String,
        numeros: List<Pair<String, Int>>,
        email: String?,
        lien: String? = null,
        anniversaire: String? = null,
        adresse: String? = null,
        favori: Boolean? = null,
        photo: ByteArray? = null,
        sansPhoto: Boolean = false,
    ): Long? {
        val ops = ArrayList<ContentProviderOperation>()
        if (brut == null) {
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build()
        } else {
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (?, ?, ?, ?, ?, ?)",
                    arrayOf(brut.toString(), StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, MIME_LIEN, StructuredPostal.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE),
                )
                .build()
            if (sansPhoto) {
                ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection("${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?", arrayOf(brut.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE))
                    .build()
            }
        }
        fun ligne(mime: String): ContentProviderOperation.Builder {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.MIMETYPE, mime)
            return if (brut == null) b.withValueBackReference(Data.RAW_CONTACT_ID, 0) else b.withValue(Data.RAW_CONTACT_ID, brut)
        }
        ops += ligne(StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.GIVEN_NAME, prenom.trim())
            .withValue(StructuredName.FAMILY_NAME, famille.trim())
            .withValue(StructuredName.DISPLAY_NAME, "${prenom.trim()} ${famille.trim()}".trim())
            .build()
        numeros.filter { it.first.isNotBlank() }.forEach { (n, t) -> ops += ligne(Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, n).withValue(Phone.TYPE, t).build() }
        if (!email.isNullOrBlank()) ops += ligne(Email.CONTENT_ITEM_TYPE).withValue(Email.ADDRESS, email.trim()).withValue(Email.TYPE, Email.TYPE_HOME).build()
        if (!lien.isNullOrBlank()) ops += ligne(MIME_LIEN).withValue(Data.DATA1, lien.trim()).build()
        if (!adresse.isNullOrBlank()) {
            ops += ligne(StructuredPostal.CONTENT_ITEM_TYPE).withValue(StructuredPostal.FORMATTED_ADDRESS, adresse.trim()).withValue(StructuredPostal.TYPE, StructuredPostal.TYPE_HOME).build()
        }
        if (!anniversaire.isNullOrBlank()) {
            ops += ligne(Event.CONTENT_ITEM_TYPE).withValue(Event.START_DATE, anniversaire).withValue(Event.TYPE, Event.TYPE_BIRTHDAY).build()
        }
        return try {
            val r = c.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val idBrut = brut ?: ContentUris.parseId(r[0].uri!!)
            if (photo != null) ecrirePhoto(c, idBrut, photo)
            val id = c.contentResolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "${RawContacts._ID} = ?", arrayOf(idBrut.toString()), null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else null }
            if (id != null && favori != null) favori(c, id, favori)
            id
        } catch (_: Exception) {
            null
        }
    }

    /** La grande photo d'un contact (Android en tire aussi la vignette) : une photo prise ou choisie, ou un avatar. */
    private fun ecrirePhoto(c: Context, brut: Long, jpeg: ByteArray) {
        val uri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI, brut), RawContacts.DisplayPhoto.CONTENT_DIRECTORY)
        try {
            c.contentResolver.openAssetFileDescriptor(uri, "rw")?.use { it.createOutputStream().use { o -> o.write(jpeg) } }
        } catch (_: Exception) {
        }
    }

    fun nomType(t: Int) = when (t) {
        Phone.TYPE_MOBILE -> "Mobile"
        Phone.TYPE_HOME -> "Maison"
        Phone.TYPE_WORK, Phone.TYPE_WORK_MOBILE -> "Travail"
        Phone.TYPE_MAIN -> "Fixe"
        else -> "Autre"
    }

    val TYPES = listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_MAIN)

    /** L'opérateur d'un numéro ivoirien, d'après ses deux premiers chiffres. */
    fun operateur(numero: String): String? {
        var d = numero.filter { it.isDigit() }
        if (d.startsWith("225") && d.length >= 12) d = d.drop(3)
        return when (d.take(2)) {
            "07" -> "Orange"
            "05" -> "MTN"
            "01" -> "Moov"
            "27", "21", "25" -> "Fixe"
            else -> null
        }
    }

    fun formater(n: String): String {
        val d = n.filter { it.isDigit() || it == '+' }
        // Un numéro ivoirien avec l'indicatif : +225 07 44 55 66 77.
        if (d.startsWith("+225") && d.length == 14) return "+225 " + d.drop(4).chunked(2).joinToString(" ")
        return if (d.startsWith("+")) d else d.chunked(2).joinToString(" ")
    }

    /** Les SIM du téléphone, pour dire si un numéro est de leur opérateur. */
    fun operateursDesSim(c: Context): List<Pair<Int, String>> = try {
        c.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
            .map { it.simSlotIndex + 1 to (it.carrierName ?: "").toString() }
    } catch (_: SecurityException) {
        emptyList()
    }

    // La SIM : ses contacts (les vieux téléphones les y gardaient).

    class ContactSim(val nom: String, val numero: String, val existe: Boolean)

    fun contactsSim(c: Context): List<ContactSim> {
        val l = mutableListOf<ContactSim>()
        val connus = liste(c).map { it.nom.lowercase() }.toSet()
        try {
            c.contentResolver.query(Uri.parse("content://icc/adn"), null, null, null, null)?.use { cur ->
                val iNom = cur.getColumnIndex("name")
                val iNum = cur.getColumnIndex("number")
                while (cur.moveToNext()) {
                    val nom = casse(cur.getString(iNom).orEmpty())
                    val num = cur.getString(iNum).orEmpty()
                    if (nom.isNotBlank() || num.isNotBlank()) l += ContactSim(nom.ifBlank { num }, num, nom.lowercase() in connus)
                }
            }
        } catch (_: Exception) {
        }
        return l
    }

    /** « MAMAN » devient « Maman » ; les noms écrits normalement restent tels quels. */
    fun casse(nom: String): String =
        if (nom.isNotBlank() && nom == nom.uppercase() && nom.any { it.isLetter() }) {
            nom.lowercase().split(' ').joinToString(" ") { m -> m.replaceFirstChar { it.uppercase() } }
        } else {
            nom
        }
}
