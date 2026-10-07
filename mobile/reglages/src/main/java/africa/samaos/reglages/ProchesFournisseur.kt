package africa.samaos.reglages

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import africa.samaos.proches.Cercle

/**
 * Les proches reconnus, pour les autres applis de Sama (Sugu) : les Réglages gardent la clé du téléphone et les
 * secrets partagés, et ne donnent que des étiquettes, des preuves et les clés d'une seule requête, calculées avec
 * eux. Réservé aux applis signées comme le système ; adb (shell ou root) peut seulement lire le code QR du
 * téléphone, qui est public.
 */
class ProchesFournisseur : ContentProvider() {
    private companion object {
        const val SHELL = 2000
        const val ROOT = 0
    }

    override fun onCreate() = true

    override fun call(methode: String, arg: String?, extras: Bundle?): Bundle? {
        val c = context ?: return null
        val uid = Binder.getCallingUid()
        // Le shell d'Android est lui aussi signé comme le système : il est exclu à part.
        val sama = uid != SHELL && (uid == Process.myUid() || c.packageManager.checkSignatures(uid, Process.myUid()) == PackageManager.SIGNATURE_MATCH)
        val jeton = Binder.clearCallingIdentity()
        try {
            if (methode == "code" && (sama || uid == SHELL || uid == ROOT)) return Bundle().apply { putString("texte", MoteurReconnus.monCode(c).texte()) }
            if (!sama) throw SecurityException("Réservé aux applis de Sama")
            val l = MoteurReconnus.liste(c)
            return when (methode) {
                "reconnus" -> Bundle().apply {
                    putStringArray("ids", l.map { Cercle.id(it.cle) }.toTypedArray())
                    putStringArray("noms", l.map { it.nom }.toTypedArray())
                }
                "etiquettes" -> {
                    val n = arg ?: return null
                    Bundle().apply { putStringArray("etiquettes", l.map { Cercle.etiquette(MoteurReconnus.secret(it), n) }.shuffled().toTypedArray()) }
                }
                "trouver" -> {
                    val n = arg ?: return null
                    val annonce = extras?.getStringArray("etiquettes")?.toSet() ?: return null
                    val p = l.firstOrNull { Cercle.etiquette(MoteurReconnus.secret(it), n) in annonce } ?: return Bundle()
                    Bundle().apply { putString("id", Cercle.id(p.cle)) }
                }
                "signer" -> {
                    val p = l.firstOrNull { Cercle.id(it.cle) == arg } ?: return null
                    val m = extras?.getString("message") ?: return null
                    val secret = MoteurReconnus.secret(p)
                    val (demande, reponse) = Cercle.cles(secret, m)
                    Bundle().apply {
                        putString("preuve", Cercle.preuve(secret, m))
                        putByteArray("demande", demande)
                        putByteArray("reponse", reponse)
                    }
                }
                "verifier" -> {
                    val m = extras?.getString("message") ?: return null
                    val preuve = extras.getString("preuve") ?: return null
                    val p = l.firstOrNull { Cercle.egal(Cercle.preuve(MoteurReconnus.secret(it), m), preuve) } ?: return Bundle()
                    val (demande, reponse) = Cercle.cles(MoteurReconnus.secret(p), m)
                    Bundle().apply {
                        putString("id", Cercle.id(p.cle))
                        putByteArray("demande", demande)
                        putByteArray("reponse", reponse)
                    }
                }
                else -> null
            }
        } finally {
            Binder.restoreCallingIdentity(jeton)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
