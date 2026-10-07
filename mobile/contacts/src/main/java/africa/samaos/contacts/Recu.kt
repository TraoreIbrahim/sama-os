package africa.samaos.contacts

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Avatar
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.CaseAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Tete
import africa.samaos.banco.appli.AvecIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/** Un contact lu dans une fiche (.vcf) : nom, numéros avec leur type, adresse e-mail. */
class FicheRecue(val nom: String, val prenom: String, val famille: String, val numeros: List<Pair<String, Int>>, val email: String?) {
    /** Une fiche qui se fait passer pour un opérateur, une banque ou un service client. */
    val officielle: Boolean
        get() = Regex("(orange|mtn|moov|wave|money|momo|banque|bank|service client|agent|support|assistance)", RegexOption.IGNORE_CASE).containsMatchIn(nom)
}

/**
 * Lire une fiche contact (vCard 2.1, 3.0 ou 4.0) : plusieurs contacts possibles, lignes repliées,
 * noms encodés « quoted-printable » des vieux téléphones.
 */
object Vcard {
    fun lire(texte: String): List<FicheRecue> {
        // Les lignes repliées commencent par une espace ; en 2.1, une ligne quoted-printable finit par « = ».
        val brutes = texte.replace("\r\n", "\n").split("\n")
        val lignes = mutableListOf<String>()
        for (l in brutes) {
            when {
                (l.startsWith(" ") || l.startsWith("\t")) && lignes.isNotEmpty() -> lignes[lignes.lastIndex] += l.substring(1)
                lignes.isNotEmpty() && lignes.last().endsWith("=") && lignes.last().contains("QUOTED-PRINTABLE", true) -> lignes[lignes.lastIndex] = lignes.last().dropLast(1) + l
                else -> lignes += l
            }
        }
        val fiches = mutableListOf<FicheRecue>()
        var fn: String? = null
        var n: List<String> = emptyList()
        var numeros = mutableListOf<Pair<String, Int>>()
        var email: String? = null
        for (l in lignes) {
            val deuxPoints = l.indexOf(':')
            if (deuxPoints < 0) continue
            val tete = l.substring(0, deuxPoints)
            val valeur = decoder(tete, l.substring(deuxPoints + 1))
            val nom = tete.substringBefore(';').substringAfter('.').uppercase()
            when (nom) {
                "BEGIN" -> {
                    fn = null; n = emptyList(); numeros = mutableListOf(); email = null
                }
                "FN" -> fn = valeur.trim()
                "N" -> n = valeur.split(';').map { it.trim() }
                "TEL" -> {
                    val t = tete.uppercase()
                    val type = when {
                        "HOME" in t -> Phone.TYPE_HOME
                        "WORK" in t -> Phone.TYPE_WORK
                        else -> Phone.TYPE_MOBILE
                    }
                    val num = valeur.removePrefix("tel:").trim()
                    if (num.isNotBlank()) numeros += num to type
                }
                "EMAIL" -> if (email == null) email = valeur.trim()
                "END" -> {
                    val famille = n.getOrNull(0).orEmpty()
                    val prenom = n.getOrNull(1).orEmpty()
                    val affiche = fn?.ifBlank { null } ?: "$prenom $famille".trim()
                    if (affiche.isNotBlank() || numeros.isNotEmpty()) {
                        val (p, f) = if (prenom.isBlank() && famille.isBlank()) affiche to "" else prenom to famille
                        fiches += FicheRecue(affiche.ifBlank { numeros.first().first }, p, f, numeros.toList(), email)
                    }
                }
            }
        }
        return fiches
    }

    private fun decoder(tete: String, valeur: String): String {
        val t = tete.uppercase()
        val v = valeur.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ")
        if ("QUOTED-PRINTABLE" !in t) return v
        val jeu = Regex("CHARSET=([^;:]+)").find(t)?.groupValues?.get(1) ?: "UTF-8"
        val octets = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < v.length) {
            val ch = v[i]
            if (ch == '=' && i + 2 < v.length) {
                val hex = v.substring(i + 1, i + 3)
                val b = hex.toIntOrNull(16)
                if (b != null && hex.length == 2) {
                    octets.write(b)
                    i += 3
                    continue
                }
            }
            // Un caractère laissé en clair (accent compris) : ses octets dans le jeu de caractères annoncé.
            octets.write(ch.toString().toByteArray(try { Charset.forName(jeu) } catch (_: Exception) { Charsets.UTF_8 }))
            i++
        }
        return try {
            String(octets.toByteArray(), Charset.forName(jeu))
        } catch (_: Exception) {
            String(octets.toByteArray())
        }
    }

    /** Ce numéro est-il déjà dans le carnet ? */
    fun connu(c: Context, numero: String): Boolean = try {
        c.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(numero)),
            arrayOf(ContactsContract.PhoneLookup._ID), null, null, null,
        )?.use { it.count > 0 } == true
    } catch (_: Exception) {
        false
    }
}

/** Une fiche contact reçue (.vcf) : la lire, puis l'ajouter au carnet d'un geste. */
class ContactRecu : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent.data
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.ContactsNuit else Identites.Contacts
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            AvecIdentite(id) { PageRecu(uri) { finish() } }
        }
    }
}

@Composable
private fun PageRecu(uri: Uri?, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var fiches by remember { mutableStateOf<List<FicheRecue>?>(null) }
    val choisies = remember { mutableStateListOf<Int>() }
    val connues = remember { mutableStateListOf<Int>() }
    var ajoutes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(uri) {
        val l = withContext(Dispatchers.IO) {
            try {
                uri?.let { u -> c.contentResolver.openInputStream(u)?.use { Vcard.lire(it.readBytes().decodeToString()) } }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        }
        val deja = withContext(Dispatchers.IO) { l.indices.filter { i -> l[i].numeros.any { Vcard.connu(c, it.first) } } }
        connues += deja
        // Cochées d'avance : les fiches nouvelles qui ne se font pas passer pour un service officiel.
        choisies += l.indices.filter { it !in deja && !l[it].officielle }
        fiches = l
    }
    val l = fiches
    EcranAppli {
        Tete(if ((l?.size ?: 0) > 1) "${l!!.size} contacts reçus" else "Contact reçu", retour = fermer)
        when {
            l == null -> {}
            l.isEmpty() -> BasicText(
                "Ce fichier ne contient aucun contact lisible.",
                modifier = Modifier.padding(20.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2),
            )
            ajoutes != null -> BasicText(
                if (ajoutes == 1) "Le contact est dans votre carnet." else "$ajoutes contacts sont dans votre carnet.",
                modifier = Modifier.padding(20.dp),
                style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = a.encre),
            )
            else -> {
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(l) { i, f ->
                        val detail = listOfNotNull(
                            f.numeros.joinToString(", ") { Carnet.formater(it.first) }.ifBlank { null },
                            f.email,
                            if (i in connues) "déjà dans vos contacts" else null,
                        ).joinToString(" · ")
                        LigneAppli(f.nom, second = detail, debut = { Avatar(f.nom) }, fin = { CaseAppli(i in choisies) }) {
                            if (i in choisies) choisies.remove(i) else choisies.add(i)
                        }
                        if (f.officielle) BasicText(
                            "Ce nom se fait passer pour un opérateur ou un service. Un vrai service client n'envoie pas sa fiche : " +
                                "vérifiez le numéro sur son site officiel avant de l'ajouter.",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp).clip(RoundedCornerShape(14.dp))
                                .background(a.champ).padding(12.dp),
                            style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    BoutonTexteAppli("Annuler", style = 't', onClick = fermer)
                    Spacer(Modifier.padding(4.dp))
                    BoutonTexteAppli(if (choisies.size > 1) "Ajouter les ${choisies.size}" else "Ajouter", actif = choisies.isNotEmpty()) {
                        var n = 0
                        choisies.sorted().forEach { i ->
                            val f = l[i]
                            if (Carnet.enregistrer(c, null, f.prenom, f.famille, f.numeros, f.email) != null) n++
                        }
                        ajoutes = n
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
