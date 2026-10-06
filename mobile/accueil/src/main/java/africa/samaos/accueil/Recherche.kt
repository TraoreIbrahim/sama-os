package africa.samaos.accueil

import africa.samaos.banco.*
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.app.SearchManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Sans accents ni majuscules, lettre à lettre : les positions restent celles du texte d'origine. */
fun simplifier(s: String): String = buildString(s.length) {
    s.forEach { c ->
        val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
        append(base.lowercaseChar())
    }
}

class ContactTrouve(val id: Long, val cle: String, val nom: String, val numero: String?)
class MessageTrouve(val adresse: String, val auteur: String, val extrait: String, val date: Long)
class FichierTrouve(val uri: Uri, val nom: String, val type: String?, val dossier: String, val date: Long)
class ReglageTrouve(val nom: String, val icone: String, val intent: Intent)

class Resultats(
    val mot: String,
    val applis: List<Appli> = emptyList(),
    val contacts: List<ContactTrouve> = emptyList(),
    val reglages: List<ReglageTrouve> = emptyList(),
    val messages: List<MessageTrouve> = emptyList(),
    val fichiers: List<FichierTrouve> = emptyList(),
    /** Sans l'accès à tous les fichiers, seuls les fichiers de Sama seraient trouvés : on propose de l'accorder. */
    val accesFichiers: Boolean = true,
    val navigateur: String? = null,
) {
    val vide: Boolean get() = applis.isEmpty() && contacts.isEmpty() && reglages.isEmpty() && messages.isEmpty() && fichiers.isEmpty()
}

/** Les réglages qu'on trouve par leur nom ou par les mots qu'on emploie pour eux. */
private class EntreeReglage(val nom: String, val mots: String, val icone: String, val action: String)

private val REGLAGES = listOf(
    EntreeReglage("Wi-Fi", "wifi wi-fi internet reseau box", Icones.WIFI, Settings.ACTION_WIFI_SETTINGS),
    EntreeReglage("Données mobiles et SIM", "donnees data internet forfait sim 4g 5g operateur", Icones.DONNEES, Settings.ACTION_WIRELESS_SETTINGS),
    EntreeReglage("Point d'accès", "point d'acces partage connexion hotspot", Icones.POINT_ACCES, "android.settings.TETHER_SETTINGS"),
    EntreeReglage("Mode avion", "avion vol", Icones.AVION, Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    EntreeReglage("Bluetooth", "bluetooth ecouteurs enceinte casque", Icones.ECOUTEURS, Settings.ACTION_BLUETOOTH_SETTINGS),
    EntreeReglage("Affichage et luminosité", "affichage ecran luminosite theme sombre police taille veille", Icones.SOLEIL, Settings.ACTION_DISPLAY_SETTINGS),
    EntreeReglage("Son et vibreur", "son sonnerie volume vibreur silencieux", Icones.MUSIQUE, Settings.ACTION_SOUND_SETTINGS),
    EntreeReglage("Notifications", "notifications alertes", Icones.CLOCHE, "android.settings.NOTIFICATION_SETTINGS"),
    EntreeReglage("Batterie", "batterie charge economiseur autonomie", Icones.BATTERIE, Intent.ACTION_POWER_USAGE_SUMMARY),
    EntreeReglage("Stockage", "stockage memoire espace libre", Icones.DOSSIER, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    EntreeReglage("Applications", "applications applis desinstaller autorisations", Icones.APPLI, Settings.ACTION_APPLICATION_SETTINGS),
    EntreeReglage("Sécurité et verrouillage", "securite verrouillage code schema mot de passe empreinte", Icones.CADENAS, Settings.ACTION_SECURITY_SETTINGS),
    EntreeReglage("Localisation", "localisation position gps confidentialite", Icones.BOUSSOLE, Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    EntreeReglage("Langue et clavier", "langue francais anglais clavier saisie", Icones.CLAVIER, Settings.ACTION_LOCALE_SETTINGS),
    EntreeReglage("Date et heure", "date heure fuseau horaire", Icones.NUIT, Settings.ACTION_DATE_SETTINGS),
    EntreeReglage("Accessibilité", "accessibilite lecteur ecran agrandir", Icones.ACCESSIBILITE, Settings.ACTION_ACCESSIBILITY_SETTINGS),
    EntreeReglage("À propos du téléphone", "a propos telephone imei numero version modele", Icones.INFO, Settings.ACTION_DEVICE_INFO_SETTINGS),
)

/** La recherche de la Cour : applis, contacts, réglages, messages et fichiers, sans accents ni majuscules. */
object Chercheur {
    private const val PAR_RUBRIQUE = 4

    fun chercher(contexte: Context, mot: String, applis: List<Appli>): Resultats {
        val m = simplifier(mot.trim())
        if (m.isEmpty()) return Resultats(mot)
        val mots = m.split(' ').filter { it.isNotBlank() }
        return Resultats(
            mot = mot.trim(),
            applis = applis.filter { a -> mots.all { simplifier(a.nom).contains(it) } }
                .sortedBy { !simplifier(it.nom).startsWith(m) }
                .take(PAR_RUBRIQUE),
            contacts = contacts(contexte, mots),
            reglages = REGLAGES.filter { r -> mots.all { simplifier(r.nom).contains(it) || r.mots.split(' ').any { w -> w.startsWith(it) } } }
                .take(PAR_RUBRIQUE)
                .map { ReglageTrouve(it.nom, it.icone, Intent(it.action)) },
            messages = messages(contexte, mot.trim()),
            fichiers = fichiers(contexte, mot.trim()),
            accesFichiers = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(),
            navigateur = navigateur(contexte),
        )
    }

    private fun permis(contexte: Context, droit: String) = contexte.checkSelfPermission(droit) == PackageManager.PERMISSION_GRANTED

    private fun contacts(contexte: Context, mots: List<String>): List<ContactTrouve> {
        if (!permis(contexte, Manifest.permission.READ_CONTACTS)) return emptyList()
        val chiffres = mots.joinToString("").filter { it.isDigit() }
        val trouves = LinkedHashMap<Long, ContactTrouve>()
        try {
            contexte.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            )?.use { c ->
                while (c.moveToNext() && trouves.size < PAR_RUBRIQUE) {
                    val id = c.getLong(0)
                    if (id in trouves) continue
                    val nom = c.getString(2) ?: continue
                    val numero = c.getString(3)
                    val parNom = mots.all { simplifier(nom).contains(it) }
                    val parNumero = chiffres.length >= 3 && numero?.filter { it.isDigit() }?.contains(chiffres) == true
                    if (parNom || parNumero) trouves[id] = ContactTrouve(id, c.getString(1).orEmpty(), nom, numero)
                }
            }
        } catch (_: Exception) {
        }
        return trouves.values.toList()
    }

    private fun messages(contexte: Context, mot: String): List<MessageTrouve> {
        if (!permis(contexte, Manifest.permission.READ_SMS)) return emptyList()
        val liste = mutableListOf<MessageTrouve>()
        val noms = HashMap<String, String>()
        val vus = HashSet<String>()
        try {
            contexte.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.BODY} LIKE ?", arrayOf("%$mot%"),
                "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && liste.size < PAR_RUBRIQUE) {
                    val adresse = c.getString(0).orEmpty()
                    val corps = c.getString(1) ?: continue
                    // Le même message reçu plusieurs fois (relances d'opérateur) ne compte qu'une fois.
                    if (!vus.add("$adresse|$corps")) continue
                    val auteur = noms.getOrPut(adresse) { nomDuNumero(contexte, adresse) ?: adresse }
                    liste += MessageTrouve(adresse, auteur, extrait(corps, mot), c.getLong(2))
                }
            }
        } catch (_: Exception) {
        }
        return liste
    }

    private fun nomDuNumero(contexte: Context, numero: String): String? = try {
        contexte.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(numero)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /** Le passage du message autour du mot cherché, pas plus d'une soixantaine de lettres. */
    private fun extrait(corps: String, mot: String): String {
        val texte = corps.replace('\n', ' ')
        val i = simplifier(texte).indexOf(simplifier(mot))
        if (texte.length <= 60 || i < 0) return texte.take(60) + if (texte.length > 60) "…" else ""
        val debut = (i - 20).coerceAtLeast(0)
        val fin = (debut + 60).coerceAtMost(texte.length)
        return (if (debut > 0) "…" else "") + texte.substring(debut, fin) + if (fin < texte.length) "…" else ""
    }

    private fun fichiers(contexte: Context, mot: String): List<FichierTrouve> {
        val liste = mutableListOf<FichierTrouve>()
        val colonnes = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        if (Build.VERSION.SDK_INT >= 29) colonnes += MediaStore.Files.FileColumns.RELATIVE_PATH
        val source = MediaStore.Files.getContentUri("external")
        try {
            contexte.contentResolver.query(
                source,
                colonnes.toTypedArray(),
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL",
                arrayOf("%$mot%"),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                while (c.moveToNext() && liste.size < PAR_RUBRIQUE) {
                    val chemin = if (colonnes.size > 4) c.getString(4).orEmpty() else ""
                    liste += FichierTrouve(
                        uri = ContentUris.withAppendedId(source, c.getLong(0)),
                        nom = c.getString(1).orEmpty(),
                        type = c.getString(2),
                        dossier = nomDuDossier(chemin),
                        date = c.getLong(3) * 1000,
                    )
                }
            }
        } catch (_: Exception) {
        }
        return liste
    }

    private fun nomDuDossier(chemin: String): String {
        val premier = chemin.trim('/').substringBefore('/')
        return when (premier) {
            "Download" -> "Téléchargements"
            "Documents" -> "Documents"
            "DCIM" -> "Appareil photo"
            "Pictures" -> "Images"
            "Music" -> "Musique"
            "Movies" -> "Vidéos"
            "" -> "Stockage"
            else -> premier
        }
    }

    private fun navigateur(contexte: Context): String? {
        val pm = contexte.packageManager
        val voir = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
        val info = pm.resolveActivity(voir, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?.takeIf { it.packageName != "android" }
            ?: pm.queryIntentActivities(voir, 0).firstOrNull()?.activityInfo
            ?: return null
        return info.applicationInfo.loadLabel(pm).toString()
    }
}

/** Ce que chaque résultat ouvre. Rien ne compose ni n'envoie tout seul : Appeler prépare l'appel, Message ouvre la conversation. */
object Ouvrir {
    fun contact(contexte: Context, c: ContactTrouve) = Systeme.ouvrir(
        contexte,
        Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(c.id, c.cle)),
    )

    fun appeler(contexte: Context, numero: String) = Systeme.ouvrir(contexte, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null)))

    fun ecrire(contexte: Context, numero: String) = Systeme.ouvrir(contexte, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", numero, null)))

    fun fichier(contexte: Context, f: FichierTrouve) = Systeme.ouvrir(
        contexte,
        Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, f.type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )

    /** Sur le web, avec le navigateur du téléphone et le moteur qu'il a choisi. */
    fun web(contexte: Context, mot: String) {
        val pm = contexte.packageManager
        val navigateur = pm.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://")), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
        val recherche = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, mot).setPackage(navigateur)
        if (navigateur != null && pm.resolveActivity(recherche, 0) != null) {
            Systeme.ouvrir(contexte, recherche)
        } else {
            val adresse = Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(mot))
            Systeme.ouvrir(contexte, Intent(Intent.ACTION_VIEW, adresse).setPackage(navigateur))
        }
    }

    fun accesFichiers(contexte: Context) = Systeme.ouvrir(
        contexte,
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", contexte.packageName, null)),
    )
}

/** Les résultats sous le galet de recherche (maquette l1-recherche). */
@Composable
fun ResultatsRecherche(r: Resultats, etat: LazyListState, basDePage: Dp, lancer: (Appli) -> Unit, menu: (Appli, Rect) -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val icones = remember { IconesDesApplis(contexte) }
    LazyColumn(
        state = etat,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 18.dp, bottom = basDePage + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (r.applis.isNotEmpty()) {
            item(key = "applis") {
                Rubrique2("Applis") {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        r.applis.forEach { a -> IconeAppli(a, { lancer(a) }, Modifier.weight(1f), menu = { b -> menu(a, b) }) }
                        repeat(4 - r.applis.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (r.contacts.isNotEmpty()) {
            item(key = "contacts") {
                Rubrique2("Contacts") {
                    r.contacts.forEach { c -> LigneContact(c, r.mot) }
                }
            }
        }
        if (r.reglages.isNotEmpty()) {
            item(key = "reglages") {
                Rubrique2("Réglages") {
                    r.reglages.forEach { g ->
                        LigneResultat(
                            vignette = { Vignette(icone = g.icone) },
                            titre = AnnotatedString(g.nom),
                            detail = "Réglages",
                        ) { Systeme.ouvrir(contexte, g.intent) }
                    }
                }
            }
        }
        if (r.messages.isNotEmpty() || r.fichiers.isNotEmpty() || !r.accesFichiers) {
            item(key = "applis-contenu") {
                Rubrique2("Dans vos applis") {
                    r.messages.forEach { m ->
                        LigneResultat(
                            vignette = { Vignette(image = icones.messages) },
                            titre = surligner("« ${m.extrait} »", r.mot),
                            detail = "Messages · ${m.auteur} · ${jour(m.date)}",
                        ) { Ouvrir.ecrire(contexte, m.adresse) }
                    }
                    r.fichiers.forEach { f ->
                        LigneResultat(
                            vignette = { Vignette(image = icones.fichiers, icone = Icones.DOCUMENT) },
                            titre = surligner(f.nom, r.mot),
                            detail = "Fichiers · ${f.dossier}",
                        ) { Ouvrir.fichier(contexte, f) }
                    }
                    if (!r.accesFichiers) {
                        LigneResultat(
                            vignette = { Vignette(icone = Icones.DOSSIER) },
                            titre = AnnotatedString("Chercher aussi dans vos fichiers"),
                            detail = "Sama lit seulement les noms des fichiers, sur ce téléphone",
                        ) { Ouvrir.accesFichiers(contexte) }
                    }
                }
            }
        }
        if (r.navigateur != null) {
            item(key = "web") {
                LigneResultat(
                    vignette = { Vignette(icone = Icones.BOUSSOLE) },
                    titre = AnnotatedString("Chercher « ${r.mot} » sur le web"),
                    detail = "Avec ${r.navigateur}",
                    chevron = true,
                ) { Ouvrir.web(contexte, r.mot) }
            }
        }
        if (r.vide && r.navigateur == null) {
            item(key = "rien") {
                BasicText(
                    "Rien ne correspond à « ${r.mot} ».",
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
                )
            }
        }
    }
}

@Composable
private fun Rubrique2(titre: String, contenu: @Composable () -> Unit) {
    val b = LocalBanco.current
    Column {
        BasicText(
            titre,
            modifier = Modifier.padding(bottom = 6.dp),
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = b.encre2),
        )
        contenu()
    }
}

/** Un contact : ses initiales, son nom (la partie trouvée en gras), son numéro en partie masqué, Appeler et Message. */
@Composable
private fun LigneContact(c: ContactTrouve, mot: String) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Ouvrir la fiche de ${c.nom}", role = Role.Button) { Ouvrir.contact(contexte, c) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).background(couleurDe(c.nom), CircleShape), contentAlignment = Alignment.Center) {
                BasicText(
                    initiales(c.nom),
                    style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White),
                )
            }
            Column(Modifier.weight(1f)) {
                BasicText(
                    surligner(c.nom, mot), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre),
                )
                c.numero?.let {
                    BasicText(
                        numeroDiscret(it),
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
                    )
                }
            }
        }
        c.numero?.let { numero ->
            Row(Modifier.padding(start = 58.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuceSol("Appeler", Icones.APPEL) { Ouvrir.appeler(contexte, numero) }
                PuceSol("Message", Icones.MESSAGE) { Ouvrir.ecrire(contexte, numero) }
            }
        }
    }
}

/** La puce de Banco posée sur le sol : un galet un ton plus clair. */
@Composable
private fun PuceSol(texte: String, icone: String, onClick: () -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(b.sol2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconeTrait(icone, 18.dp, b.encre)
        BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = b.encre))
    }
}

@Composable
private fun LigneResultat(
    vignette: @Composable () -> Unit,
    titre: AnnotatedString,
    detail: String,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    val b = LocalBanco.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vignette()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                titre, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre),
            )
            BasicText(
                detail, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = b.encre2),
            )
        }
        if (chevron) IconeTrait(Icones.CHEVRON, 20.dp, b.encre2)
    }
}

/** La vignette d'un résultat : l'icône de l'appli qui le garde, ou un trait de Banco sur un galet. */
@Composable
private fun Vignette(image: ImageBitmap? = null, icone: String? = null) {
    val b = LocalBanco.current
    if (image != null) {
        Image(image, contentDescription = null, modifier = Modifier.size(32.dp))
    } else {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(b.sol2), contentAlignment = Alignment.Center) {
            if (icone != null) IconeTrait(icone, 18.dp, b.encre)
        }
    }
}

/** Les icônes des applis qui gardent les messages et les fichiers, chacune avec sa propre identité. */
private class IconesDesApplis(contexte: Context) {
    private val pm = contexte.packageManager
    val messages: ImageBitmap? = icone(Telephony.Sms.getDefaultSmsPackage(contexte))
    val fichiers: ImageBitmap? = icone(
        pm.resolveActivity(Intent(Intent.ACTION_VIEW).setType("application/pdf"), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
            ?: listOf("com.google.android.documentsui", "com.android.documentsui").firstOrNull { p ->
                try {
                    pm.getApplicationInfo(p, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            },
    )

    private fun icone(paquet: String?): ImageBitmap? = try {
        paquet?.let { pm.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap() }
    } catch (_: Exception) {
        null
    }
}

/** Le texte, la partie qui correspond à la recherche en gras (sans tenir compte des accents ni des majuscules). */
private fun surligner(texte: String, mot: String): AnnotatedString {
    val i = simplifier(texte).indexOf(simplifier(mot.trim()))
    if (i < 0 || mot.isBlank()) return AnnotatedString(texte)
    return buildAnnotatedString {
        append(texte.substring(0, i))
        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
        append(texte.substring(i, i + mot.trim().length))
        pop()
        append(texte.substring(i + mot.trim().length))
    }
}

private fun initiales(nom: String): String =
    nom.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

private val TEINTES = listOf(Color(0xFF3D5A99), Color(0xFF8A4F7D), Color(0xFF2E7D6B), Color(0xFFB0602F), Color(0xFF5B6B2E), Color(0xFF7A5230))

private fun couleurDe(nom: String): Color = TEINTES[Math.floorMod(nom.hashCode(), TEINTES.size)]

/**
 * « 07 •• •• 45 12 · Orange » : le numéro se reconnaît à ses derniers chiffres sans s'afficher en entier
 * à qui regarde l'écran par-dessus l'épaule. L'opérateur se déduit des numéros ivoiriens à dix chiffres.
 */
private fun numeroDiscret(numero: String): String {
    var n = numero.filter { it.isDigit() }
    if (n.startsWith("225") && n.length == 13) n = n.drop(3)
    if (n.length != 10) return numero
    val operateur = when (n.take(2)) {
        "01" -> "Moov Africa"
        "05" -> "MTN"
        "07" -> "Orange"
        else -> null
    }
    val masque = "${n.take(2)} •• •• ${n.substring(6, 8)} ${n.substring(8)}"
    return if (operateur != null) "$masque · $operateur" else masque
}

private fun jour(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val aujourdhui = LocalDate.now(zone)
    return when (d) {
        aujourdhui -> "aujourd'hui"
        aujourdhui.minusDays(1) -> "hier"
        else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
    }
}
