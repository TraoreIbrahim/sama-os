package africa.samaos.contacts

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Avatar
import africa.samaos.banco.appli.ChampAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Ce qu'une autre appli demande de choisir : un contact, un de ses numéros, ou une adresse e-mail. */
private enum class Quoi(val titre: String, val mime: String) {
    CONTACT("Choisir un contact", ContactsContract.Contacts.CONTENT_ITEM_TYPE),
    NUMERO("Choisir un numéro", ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
    EMAIL("Choisir une adresse", ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
}

private class Choix(val nom: String, val detail: String?, val uri: Uri)

/**
 * Choisir un contact pour une autre appli (ACTION_PICK, GET_CONTENT) : un contact d'urgence dans les
 * Réglages, un numéro à partager… L'appli reçoit seulement ce qui a été touché, rien d'autre du carnet.
 */
class Choisir : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val type = intent.type ?: intent.data?.let { contentResolver.getType(it) }.orEmpty()
        val quoi = when {
            type.contains("phone") -> Quoi.NUMERO
            type.contains("email") -> Quoi.EMAIL
            else -> Quoi.CONTACT
        }
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.ContactsNuit else Identites.Contacts
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            CompositionLocalProvider(LocalIdentite provides id) {
                PageChoisir(quoi, retour = { finish() }) { uri ->
                    // L'appli qui a demandé peut lire ce contact-là, et lui seul.
                    setResult(RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    finish()
                }
            }
        }
    }
}

private fun lire(c: Context, quoi: Quoi): List<Choix> {
    val l = mutableListOf<Choix>()
    try {
        when (quoi) {
            Quoi.CONTACT -> c.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null, null, null,
            )?.use { cur ->
                while (cur.moveToNext()) {
                    l += Choix(cur.getString(2).orEmpty(), null, ContactsContract.Contacts.getLookupUri(cur.getLong(0), cur.getString(1)))
                }
            }
            Quoi.NUMERO -> c.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone._ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE),
                null, null, null,
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val numero = cur.getString(2).orEmpty()
                    val detail = listOfNotNull(Carnet.formater(numero), Carnet.nomType(cur.getInt(3)).ifBlank { null }, Carnet.operateur(numero)).joinToString(" · ")
                    l += Choix(cur.getString(1).orEmpty(), detail, ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, cur.getLong(0)))
                }
            }
            Quoi.EMAIL -> c.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email._ID, ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS),
                null, null, null,
            )?.use { cur ->
                while (cur.moveToNext()) l += Choix(cur.getString(1).orEmpty(), cur.getString(2), ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, cur.getLong(0)))
            }
        }
    } catch (_: SecurityException) {
    }
    val tri = Collator.getInstance(Locale.FRENCH)
    return l.filter { it.nom.isNotBlank() }.sortedWith { a, b -> tri.compare(a.nom, b.nom) }
}

@Composable
private fun PageChoisir(quoi: Quoi, retour: () -> Unit, choisir: (Uri) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var liste by remember { mutableStateOf<List<Choix>?>(null) }
    var recherche by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { liste = withContext(Dispatchers.IO) { lire(c, quoi) } }
    val vus = liste.orEmpty().filter {
        recherche.isBlank() || simplifier(it.nom).contains(simplifier(recherche)) || it.detail?.filter { ch -> ch.isDigit() }?.contains(recherche.filter { ch -> ch.isDigit() }.ifBlank { "§" }) == true
    }
    EcranAppli {
        Tete(quoi.titre, retour = retour)
        ChampAppli(recherche, "Rechercher", { recherche = it })
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(vus, key = { it.uri.toString() }) { ch ->
                LigneAppli(ch.nom, second = ch.detail, debut = { Avatar(ch.nom) }) { choisir(ch.uri) }
            }
            item(key = "bas") { Spacer(Modifier.height(48.dp)) }
        }
        if (liste?.isEmpty() == true) {
            BasicText(
                if (quoi == Quoi.EMAIL) "Aucune adresse e-mail dans vos contacts." else "Aucun contact pour l'instant.",
                modifier = Modifier.padding(20.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2),
            )
        }
    }
}
