package africa.samaos.reglages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import org.json.JSONArray
import org.json.JSONObject

/** Un numéro d'urgence de Côte d'Ivoire. */
data class NumeroUrgence(val nom: String, val numero: String, val quand: String)

val NUMEROS_URGENCE = listOf(
    NumeroUrgence("SAMU", "185", "Malaise, accident, blessure"),
    NumeroUrgence("Pompiers", "180", "Incendie, inondation, noyade"),
    NumeroUrgence("Police", "170", "Agression, vol, danger"),
)

data class ContactUrgence(val nom: String, val numero: String)

/**
 * La fiche médicale et les contacts d'urgence. Ils sont gardés dans le stockage lisible avant le premier
 * déverrouillage : les secours doivent pouvoir les lire sur un téléphone verrouillé.
 */
object MoteurUrgence {
    private fun prefs(c: Context) = c.createDeviceProtectedStorageContext().getSharedPreferences("urgence", Context.MODE_PRIVATE)

    fun fiche(c: Context): Map<String, String> = try {
        val o = JSONObject(prefs(c).getString("fiche", "{}"))
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (_: Exception) {
        emptyMap()
    }

    fun garderFiche(c: Context, f: Map<String, String>) {
        prefs(c).edit().putString("fiche", JSONObject(f.filterValues { it.isNotBlank() } as Map<*, *>).toString()).apply()
    }

    fun ficheVisible(c: Context) = prefs(c).getBoolean("visible", true)
    fun reglerFicheVisible(c: Context, oui: Boolean) = prefs(c).edit().putBoolean("visible", oui).apply()

    fun contacts(c: Context): List<ContactUrgence> = try {
        val a = JSONArray(prefs(c).getString("contacts", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> ContactUrgence(o.getString("nom"), o.getString("numero")) } }
    } catch (_: Exception) {
        emptyList()
    }

    private fun garderContacts(c: Context, l: List<ContactUrgence>) {
        prefs(c).edit().putString("contacts", JSONArray(l.map { JSONObject().put("nom", it.nom).put("numero", it.numero) }).toString()).apply()
    }

    fun ajouter(c: Context, k: ContactUrgence) = garderContacts(c, (contacts(c).filter { it.numero != k.numero } + k).take(5))
    fun retirer(c: Context, k: ContactUrgence) = garderContacts(c, contacts(c).filter { it.numero != k.numero })

    /** Appeler, même téléphone verrouillé (droit réservé du système : CALL_PRIVILEGED). */
    fun appeler(c: Context, numero: String) {
        try {
            c.startActivity(Intent("android.intent.action.CALL_PRIVILEGED", Uri.fromParts("tel", numero, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            c.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun resume(c: Context): String {
        val k = contacts(c)
        val f = fiche(c)
        return listOfNotNull(
            if (f.isEmpty()) "Pas de fiche médicale" else "Fiche médicale remplie",
            when (k.size) { 0 -> null; 1 -> "1 contact"; else -> "${k.size} contacts" },
        ).joinToString(" · ")
    }
}

private val CHAMPS = listOf(
    "nom" to "Nom",
    "groupe" to "Groupe sanguin",
    "allergies" to "Allergies",
    "traitement" to "Traitement",
    "savoir" to "À savoir",
)

/** Urgence (maquette l4-contacts-urgence) : contacts, fiche médicale, numéros. */
@Composable
fun PageUrgence(nav: Nav) {
    val c = LocalContext.current
    var v by remember { mutableIntStateOf(0) }
    val contacts = remember(v) { MoteurUrgence.contacts(c) }
    val choisir = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { uri ->
            c.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) MoteurUrgence.ajouter(c, ContactUrgence(cur.getString(0).orEmpty(), cur.getString(1).orEmpty()))
            }
            v++
        }
    }
    PageReglages(titre = "Urgence", sousTitre = "Ce que les secours et vos proches voient, même téléphone verrouillé.", retour = nav.retour) {
        section("Contacts d'urgence", cle = "contacts") {
            contacts.forEach { k ->
                Ligne(k.nom, detail = k.numero, debut = { Initiales(k.nom) }, fin = Fin.Rien) {
                    MoteurUrgence.retirer(c, k)
                    v++
                }
            }
            if (contacts.size < 5) Ligne("Ajouter un contact", icone = Icones.PLUS, fin = Fin.Rien) {
                try {
                    choisir.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                } catch (_: android.content.ActivityNotFoundException) {
                }
            }
            if (contacts.isNotEmpty()) Explication("Toucher un contact le retire de la liste.")
        }
        section("Fiche médicale", cle = "fiche") {
            val f = remember(v) { MoteurUrgence.fiche(c) }
            Ligne(
                if (f.isEmpty()) "Remplir la fiche" else (f["nom"] ?: "Fiche médicale"),
                detail = if (f.isEmpty()) "Groupe sanguin, allergies, traitement" else listOfNotNull(f["groupe"]?.let { "Groupe $it" }, f["allergies"]?.let { "allergie : $it" }).joinToString(", "),
                icone = Icones.PERSONNE,
            ) { nav.aller(Page.FicheMedicale) }
            Ligne("Visible sans déverrouiller", detail = "Depuis « Urgence » sur l'écran verrouillé", icone = Icones.CADENAS, fin = Fin.Inter(remember(v) { MoteurUrgence.ficheVisible(c) })) {
                MoteurUrgence.reglerFicheVisible(c, !MoteurUrgence.ficheVisible(c))
                v++
            }
        }
        section("Numéros d'urgence", cle = "numeros") {
            NUMEROS_URGENCE.forEach { n -> Ligne("${n.nom} · ${n.numero}", detail = n.quand, icone = Icones.APPEL, fin = Fin.Rien) }
            Explication("Ils s'appellent depuis l'écran verrouillé, sans le code.")
        }
        section("SOS", cle = "sos") {
            val geste = remember(v) { MoteurSos.gesteActif(c) }
            Ligne("Cinq appuis sur le bouton marche", detail = "Lance un SOS, avec cinq secondes pour l'annuler", icone = Icones.ALERTE, fin = Fin.Inter(geste)) {
                MoteurSos.reglerGeste(c, !geste)
                v++
            }
            val position = remember(v) { MoteurSos.envoyerPosition(c) }
            Ligne(
                "Envoyer ma position à mes contacts",
                detail = if (contacts.isEmpty()) "Ajoutez d'abord un contact d'urgence" else "Par SMS, sans data, toutes les 15 minutes pendant 2 heures",
                icone = Icones.BOUSSOLE, fin = Fin.Inter(position && contacts.isNotEmpty(), actif = contacts.isNotEmpty()),
            ) {
                if (contacts.isNotEmpty()) {
                    MoteurSos.reglerEnvoyerPosition(c, !position)
                    v++
                }
            }
            Ligne("Essayer sans rien envoyer", detail = "Pour voir comment ça se passe", icone = Icones.LECTURE) {
                c.startActivity(Intent(c, Sos::class.java).putExtra(Sos.ESSAI, true))
            }
        }
        section("Pendant un SOS, appeler", cle = "sos-appel") {
            val choisi = remember(v) { MoteurSos.numero(c) }
            NUMEROS_URGENCE.forEach { n ->
                Ligne("${n.nom} · ${n.numero}", fin = Fin.Choix(choisi == n.numero)) {
                    MoteurSos.reglerNumero(c, n.numero)
                    v++
                }
            }
            Ligne("Personne", detail = "Seulement envoyer la position", fin = Fin.Choix(choisi.isBlank())) {
                MoteurSos.reglerNumero(c, "")
                v++
            }
        }
    }
}

@Composable
private fun Initiales(nom: String) {
    val b = LocalBanco.current
    Box(Modifier.size(40.dp).background(b.sol2, CircleShape), contentAlignment = Alignment.Center) {
        BasicText(
            nom.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre),
        )
    }
}

/** La fiche médicale (maquette l4-fiche-urgence), remplie champ par champ. */
@Composable
fun PageFicheMedicale(nav: Nav) {
    val c = LocalContext.current
    var f by remember { mutableStateOf(MoteurUrgence.fiche(c)) }
    PageReglages(titre = "Fiche médicale", sousTitre = "Pour les secours, si vous ne pouvez pas parler.", retour = nav.retour) {
        section(cle = "champs") {
            CHAMPS.forEach { (cle, nom) ->
                if (cle == "groupe") {
                    BasicText(nom, style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = LocalBanco.current.encre2))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("O+", "O−", "A+", "A−", "B+", "AB+").forEach { g ->
                            Puce(if (f["groupe"] == g) "✓ $g" else g) {
                                f = f + ("groupe" to if (f["groupe"] == g) "" else g)
                                MoteurUrgence.garderFiche(c, f)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    ChampFiche(nom, f[cle].orEmpty(), lignes = if (cle == "savoir" || cle == "traitement") 3 else 1) {
                        f = f + (cle to it)
                        MoteurUrgence.garderFiche(c, f)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            Explication("Elle reste sur ce téléphone. Personne d'autre ne la reçoit.")
        }
    }
}

/** Un champ de la fiche : son nom au-dessus, la saisie dessous (sur une ou plusieurs lignes). */
@Composable
private fun ChampFiche(nom: String, valeur: String, lignes: Int = 1, changer: (String) -> Unit) {
    val b = LocalBanco.current
    val style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 23.sp, color = b.encre)
    BasicText(nom, modifier = Modifier.padding(bottom = 6.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = b.encre2))
    androidx.compose.foundation.text.BasicTextField(
        value = valeur,
        onValueChange = changer,
        singleLine = lignes == 1,
        minLines = 1,
        maxLines = lignes,
        textStyle = style,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(b.laterite),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = nom },
        decorationBox = { champ ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).background(b.sol2, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) { champ() }
        },
    )
}

/**
 * « Urgence » depuis l'écran verrouillé (maquette l4-urgence-pave) : les numéros, la fiche, les proches.
 * Elle remplace les informations d'urgence d'Android (EMERGENCY_ASSISTANCE) et s'affiche sans déverrouiller.
 */
class UrgenceVerrouillee : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        enableEdgeToEdge()
        setContent {
            val nuit = androidx.compose.foundation.isSystemInDarkTheme()
            CompositionLocalProvider(LocalBanco provides africa.samaos.banco.palette(africa.samaos.banco.PaysageEspace.LAGUNE, nuit), africa.samaos.banco.LocalNuit provides nuit) {
                PageUrgenceVerrouillee { finish() }
            }
        }
    }
}

@Composable
private fun PageUrgenceVerrouillee(fermer: () -> Unit) {
    val c = LocalContext.current
    val f = remember { if (MoteurUrgence.ficheVisible(c)) MoteurUrgence.fiche(c) else emptyMap() }
    val contacts = remember { MoteurUrgence.contacts(c) }
    PageReglages(titre = "Urgence", retour = fermer) {
        section("Appeler, même sans code", cle = "numeros") {
            NUMEROS_URGENCE.forEach { n ->
                Ligne("${n.nom} · ${n.numero}", detail = n.quand, icone = Icones.APPEL, danger = n.numero == "185") { MoteurUrgence.appeler(c, n.numero) }
            }
        }
        if (f.isNotEmpty()) section("Fiche médicale${f["nom"]?.let { " de $it" } ?: ""}", cle = "fiche") {
            CHAMPS.filter { it.first != "nom" }.forEach { (cle, nom) ->
                f[cle]?.let { Ligne(it, detail = nom, fin = Fin.Rien) }
            }
        }
        if (contacts.isNotEmpty()) section("Contacts d'urgence", cle = "contacts") {
            contacts.forEach { k -> Ligne(k.nom, detail = k.numero, debut = { Initiales(k.nom) }, fin = Fin.Rien) { MoteurUrgence.appeler(c, k.numero) } }
        }
        section(cle = "autre") {
            Ligne("Composer un autre numéro", icone = Icones.CLAVIER) {
                try {
                    c.startActivity(Intent("com.android.phone.EmergencyDialer.DIAL").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        }
    }
}
