package africa.samaos.reglages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Avancer
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import africa.samaos.proches.CodeProche
import africa.samaos.proches.Identite
import africa.samaos.proches.codeVerification
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un proche reconnu : le prénom qu'on lui a donné, la clé publique de son téléphone, la date. */
class ProcheReconnu(val nom: String, val cle: ByteArray, val quand: Long) {
    val id get() = Base64.encodeToString(cle, Base64.NO_WRAP)
}

/**
 * Les proches reconnus (Proche en proche) : deux téléphones se reconnaissent en scannant chacun le code QR de
 * l'autre, devant soi. Seuls ceux qui se sont reconnus des deux côtés pourront se voir et échanger.
 */
object MoteurReconnus {
    private fun prefs(c: Context) = c.getSharedPreferences("proches", Context.MODE_PRIVATE)

    fun liste(c: Context): List<ProcheReconnu> = try {
        val a = JSONArray(prefs(c).getString("reconnus", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            ProcheReconnu(it.getString("nom"), Base64.decode(it.getString("cle"), Base64.NO_WRAP), it.optLong("quand"))
        }.sortedBy { it.nom.lowercase() }
    } catch (_: Exception) {
        emptyList()
    }

    private fun garder(c: Context, l: List<ProcheReconnu>) = prefs(c).edit().putString(
        "reconnus",
        JSONArray(l.map { JSONObject().put("nom", it.nom).put("cle", it.id).put("quand", it.quand) }).toString(),
    ).apply()

    fun reconnaitre(c: Context, nom: String, cle: ByteArray) =
        garder(c, liste(c).filter { !it.cle.contentEquals(cle) } + ProcheReconnu(nom.trim().ifBlank { "Un proche" }, cle, System.currentTimeMillis()))

    fun oublier(c: Context, p: ProcheReconnu) = garder(c, liste(c).filter { !it.cle.contentEquals(p.cle) })

    fun connu(c: Context, cle: ByteArray) = liste(c).firstOrNull { it.cle.contentEquals(cle) }

    /** Le prénom écrit sur le code de ce téléphone : celui de la fiche médicale, sinon « Mon téléphone ». */
    fun monNom(c: Context): String = prefs(c).getString("mon_nom", null)
        ?: MoteurUrgence.fiche(c)["nom"]?.trim()?.substringBefore(' ')?.ifBlank { null } ?: "Mon téléphone"

    fun reglerMonNom(c: Context, nom: String) = prefs(c).edit().putString("mon_nom", nom.trim().take(30)).apply()

    fun monCode(c: Context): CodeProche = CodeProche(monNom(c), Identite.publique())

    fun qr(texte: String, taille: Int = 720): Bitmap {
        val m = QRCodeWriter().encode(texte, BarcodeFormat.QR_CODE, taille, taille, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1))
        val b = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
        for (y in 0 until m.height) for (x in 0 until m.width) b.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        return b
    }

    /** « de Koffi », « d'Awa ». */
    fun de(nom: String) = if (nom.firstOrNull()?.lowercaseChar() in "aeiouyhéèêàâîôû".toList()) "d'$nom" else "de $nom"

    fun date(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))
}

/** Reconnaître un proche : mon code, puis scanner le sien. [vientDe] : le proche qu'on vient de reconnaître. */
@Composable
fun PageReconnaitre(vientDe: String?, nav: Nav) {
    val c = LocalContext.current
    val b = LocalBanco.current
    var v by remember { mutableIntStateOf(0) }
    var renommer by remember { mutableStateOf(false) }
    var nom by remember { mutableStateOf(MoteurReconnus.monNom(c)) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val code = remember(v) { MoteurReconnus.monCode(c) }
    val image = remember(code.texte()) { MoteurReconnus.qr(code.texte()) }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringExtra("texte")
        if (r.resultCode == Activity.RESULT_OK && t != null) {
            if (CodeProche.lire(t) != null) nav.aller(Page.ConfirmerProche(t)) else erreur = "Ce code n'est pas celui d'un téléphone Sama."
        }
    }
    PageReglages(
        titre = if (vientDe != null) "À votre tour" else "Reconnaître un proche",
        sousTitre = if (vientDe != null) "$vientDe est parmi vos proches. Montrez-lui maintenant votre code." else "Chacun scanne le code de l'autre, téléphones côte à côte",
        retour = nav.retour,
    ) {
        section(cle = "code") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(260.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp)) {
                    Image(image.asImageBitmap(), contentDescription = "Code de ce téléphone", modifier = Modifier.fillMaxWidth(), filterQuality = FilterQuality.None)
                }
                Spacer(Modifier.height(10.dp))
                BasicText("Le code de « ${code.nom} »", style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = b.encre2))
            }
            Spacer(Modifier.height(8.dp))
            if (renommer) {
                Champ(nom, "Votre prénom sur ce code", { nom = it.take(30) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BoutonTexte("Annuler") { renommer = false }
                    BoutonTexte("Garder") {
                        MoteurReconnus.reglerMonNom(c, nom)
                        renommer = false
                        v++
                    }
                }
            } else {
                Ligne("Votre prénom sur ce code", detail = code.nom, icone = Icones.CRAYON) { renommer = true }
            }
        }
        section(cle = "scanner") {
            Ligne("Scanner le code de mon proche", detail = "Avec l'Appareil photo", icone = Icones.QR, fin = Fin.Bouton("Scanner", plein = true)) {
                erreur = null
                try {
                    scanner.launch(Intent("africa.samaos.action.SCANNER_QR").setPackage("africa.samaos.appareil"))
                } catch (_: Exception) {
                    erreur = "L'Appareil photo de Sama n'est pas là pour scanner."
                }
            }
            erreur?.let { Explication(it, b.lateriteTexte) }
            Explication(
                "Ce code ne contient ni votre numéro ni votre compte. Ne scannez que le code d'un proche qui est devant vous : " +
                    "un code reçu en photo ou par message ne se scanne jamais.",
            )
        }
    }
}

/** « Reconnaître le téléphone d'Awa ? » : le prénom, et le code à comparer à voix haute. */
@Composable
fun PageConfirmerProche(texte: String, nav: Nav) {
    val c = LocalContext.current
    val b = LocalBanco.current
    val code = remember(texte) { CodeProche.lire(texte) }
    val moi = remember { Identite.publique() }
    if (code == null) {
        PageReglages(titre = "Code illisible", retour = nav.retour) {
            section(cle = "x") { Explication("Ce code n'est pas celui d'un téléphone Sama.") }
        }
        return
    }
    var nom by remember { mutableStateOf(code.nom) }
    val verification = remember(texte) { codeVerification(moi, code.cle) }
    val deja = remember(texte) { MoteurReconnus.connu(c, code.cle) }
    when {
        code.cle.contentEquals(moi) -> PageReglages(titre = "C'est votre code", retour = nav.retour) {
            section(cle = "x") { Explication("Ce code est celui de ce téléphone. Scannez celui de votre proche.") }
        }
        deja != null -> PageReglages(titre = "Déjà parmi vos proches", sousTitre = "${deja.nom} · depuis le ${MoteurReconnus.date(deja.quand)}", retour = nav.retour) {
            section(cle = "x") { Explication("Ce téléphone est déjà parmi vos proches reconnus. Code de vérification : $verification.") }
        }
        else -> PageReglages(titre = "Reconnaître le téléphone ${MoteurReconnus.de(code.nom)} ?", retour = nav.retour) {
            section("Code à comparer", cle = "verif") {
                BasicText(
                    verification,
                    modifier = Modifier.semantics { contentDescription = "Code de vérification ${verification.replace(" ", "")}" },
                    style = TextStyle(fontFamily = Polices.monument, fontSize = 44.sp, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp), color = b.encre),
                )
                Explication("Le téléphone ${MoteurReconnus.de(code.nom)} affichera les mêmes chiffres quand il aura scanné le vôtre. S'ils sont différents, ce n'est pas son téléphone : annulez.")
            }
            section("Son prénom chez vous", cle = "nom") {
                Champ(nom, "Prénom", { nom = it.take(30) })
            }
            section(cle = "prudence") {
                Explication("Ne reconnaissez qu'un proche qui est devant vous. Personne de sérieux ne vous demandera de scanner un code à distance.", b.lateriteTexte)
            }
            section(cle = "faire") {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    BoutonTexte("Annuler") { nav.retour() }
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        "Reconnaître",
                        modifier = Modifier.padding(end = 4.dp).clickable(role = Role.Button) {
                            MoteurReconnus.reconnaitre(c, nom, code.cle)
                            nav.aller(Page.Reconnaitre(nom.trim().ifBlank { code.nom }))
                        }.padding(horizontal = 8.dp, vertical = 12.dp),
                        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.encre),
                    )
                    Avancer(true, "Reconnaître") {
                        MoteurReconnus.reconnaitre(c, nom, code.cle)
                        nav.aller(Page.Reconnaitre(nom.trim().ifBlank { code.nom }))
                    }
                }
            }
        }
    }
}
