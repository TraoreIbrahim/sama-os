package africa.samaos.mail

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.Avatar
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import africa.samaos.bouclier.Bouclier
import africa.samaos.bouclier.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val URL = Regex("(?i)\\b(https?://[^\\s<>\"')]+|www\\.[^\\s<>\"')]+)")

@Composable
internal fun PageLecture(compteId: String, dossier: String, uid: Long, aller: (Vue) -> Unit, retour: () -> Unit) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val portee = rememberCoroutineScope()
    val compte = remember { Comptes.de(c, compteId) }
    if (compte == null) {
        LaunchedEffect(Unit) { retour() }
        return
    }
    var l by remember { mutableStateOf<Lettre?>(null) }
    var pieces by remember { mutableStateOf<List<Piece>>(emptyList()) }
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var images by remember { mutableStateOf(Preferences.imagesToujours(c)) }
    var lienPiege by remember { mutableStateOf<Pair<String, Verdict>?>(null) }
    var effacer by remember { mutableStateOf(false) }
    var confirmerData by remember { mutableStateOf<Piece?>(null) }
    val etatsPieces = remember { mutableStateMapOf<Int, String>() }
    var roles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Courrier.version) {
        withContext(Dispatchers.IO) {
            val x = Base.lettre(c, compte.id, dossier, uid)
            pieces = Base.pieces(c, compte.id, dossier, uid)
            if (x != null) verdict = verdictDe(c, x, pieces)
            roles = Base.dossiers(c, compte.id).associate { it.role to it.nom }
            l = x
        }
    }
    LaunchedEffect(uid) {
        withContext(Dispatchers.IO) {
            val x = Base.lettre(c, compte.id, dossier, uid) ?: return@withContext
            if (!x.charge) {
                try {
                    Courrier.chargerCorps(c, compte, dossier, uid)
                } catch (e: Exception) {
                    erreur = Courrier.message(e)
                }
            }
            if (!x.lu) {
                try {
                    Courrier.marquer(c, compte, dossier, uid, lu = true)
                } catch (_: Exception) {
                }
            }
            // Le bouclier note ce qu'il a vu (une fois par mail) dans son bilan.
            val v = verdictDe(c, Base.lettre(c, compte.id, dossier, uid) ?: x, Base.pieces(c, compte.id, dossier, uid))
            if (v != null && !x.lu) Bouclier.noter(c, "mail", v.titre, "${x.nomAffiche} · ${x.sujet}")
        }
    }
    val lettre = l
    fun ouvrirLien(url: String) {
        if (url.startsWith("mailto:", true)) {
            c.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(url)).setPackage(c.packageName))
            return
        }
        val complet = if (url.startsWith("www.", true)) "https://$url" else url
        val hote = Uri.parse(complet).host.orEmpty()
        // Dans un mail que le bouclier a signalé, aucun lien ne s'ouvre sans mise en garde.
        val v = Bouclier.lien(complet) ?: if (Bouclier.hoteSuspect(hote)) Verdict("Lien piégé", "Ce lien se fait passer pour un service de mobile money. N'y tapez jamais votre code secret.") else verdict
        if (v != null) {
            lienPiege = complet to v
            return
        }
        try {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(complet)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
    fun ouvrirPiece(p: Piece) {
        if (Bouclier.pieceJointe(p.nom) != null) {
            etatsPieces[p.n] = "Bloquée par le bouclier"
            return
        }
        etatsPieces[p.n] = "Téléchargement…"
        portee.launch {
            try {
                val f = withContext(Dispatchers.IO) { Courrier.telecharger(c, compte, dossier, uid, p) }
                etatsPieces[p.n] = "Sur le téléphone"
                val u = FileProvider.getUriForFile(c, "${c.packageName}.fichiers", f)
                val type = p.type.ifBlank { MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream" }
                try {
                    c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    etatsPieces[p.n] = "Aucune appli ne sait ouvrir ce fichier"
                }
            } catch (e: Exception) {
                etatsPieces[p.n] = Courrier.message(e)
            }
        }
    }
    fun demanderPiece(p: Piece) {
        val data = c.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        if (data && p.taille > 5_000_000 && etatsPieces[p.n] != "Sur le téléphone") confirmerData = p else ouvrirPiece(p)
    }
    fun enregistrer(p: Piece) {
        if (Bouclier.pieceJointe(p.nom) != null) return
        etatsPieces[p.n] = "Téléchargement…"
        portee.launch {
            etatsPieces[p.n] = try {
                withContext(Dispatchers.IO) { enregistrerDansTelechargements(c, Courrier.telecharger(c, compte, dossier, uid, p), p.type) }
                "Enregistré dans Téléchargements"
            } catch (e: Exception) {
                Courrier.message(e)
            }
        }
    }
    val corbeille = roles["corbeille"]
    EcranAppli {
        Tete("", retour = retour, petit = true) {
            val archives = roles["archives"]
            if (archives != null && dossier != archives) BoutonAppli(IconesMail.ARCHIVES, "Archiver") {
                portee.launch { withContext(Dispatchers.IO) { runCatching { Courrier.deplacer(c, compte, dossier, uid, "archives") } } }
                retour()
            }
            BoutonAppli(Icones.CORBEILLE, if (dossier == corbeille) "Supprimer définitivement" else "Mettre à la corbeille") {
                if (dossier == corbeille) {
                    effacer = true
                } else {
                    portee.launch { withContext(Dispatchers.IO) { runCatching { Courrier.deplacer(c, compte, dossier, uid, "corbeille") } } }
                    retour()
                }
            }
            BoutonAppli(IconesMail.ENVELOPPE, "Marquer comme non lu") {
                portee.launch { withContext(Dispatchers.IO) { runCatching { Courrier.marquer(c, compte, dossier, uid, lu = false) } } }
                retour()
            }
        }
        if (lettre == null) {
            Note("Ce mail n'est plus là.")
            return@EcranAppli
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BasicText(
                lettre.sujet.ifBlank { "(sans objet)" }, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, color = a.encre),
            )
            verdict?.let { v ->
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.voile).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconeTrait(Icones.BOUCLIER, 20.dp, a.accentTexte)
                        BasicText(v.titre, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = a.accentTexte))
                    }
                    BasicText(v.raison, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre))
                    BasicText(
                        "Ne répondez pas, n'ouvrez ni ses liens ni ses pièces jointes, et ne donnez jamais de code.",
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, lineHeight = 20.sp, color = a.encre2),
                    )
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(lettre.nomAffiche, 44.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(lettre.nomAffiche, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre))
                    BasicText(lettre.de, maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
                }
                BasicText(dateCourte(lettre.date), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = a.encre2))
            }
            val destinataires = listOf("À : " to lettre.a, "Cc : " to lettre.cc).filter { it.second.isNotBlank() }.joinToString("\n") { it.first + it.second }
            if (destinataires.isNotBlank()) BasicText(destinataires, modifier = Modifier.padding(horizontal = 20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = a.encre2))
            Spacer(Modifier.height(12.dp))
            when {
                !lettre.charge && erreur != null -> Note("Le texte n'a pas pu être relevé : $erreur", a.accentTexte)
                !lettre.charge -> Note("Relève du texte…")
                lettre.html != null && (lettre.texte.isNullOrBlank() || imagesDistantes(lettre.html)) -> {
                    if (imagesDistantes(lettre.html) && !images) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(a.champ).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(
                                "Images non chargées : elles coûtent de la data et diraient à l'expéditeur que vous avez ouvert ce mail.",
                                modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = a.encre2),
                            )
                            BoutonTexteAppli("Afficher", style = 't') { images = true }
                        }
                    }
                    CorpsHtml(lettre.html, images, ::ouvrirLien)
                }
                !lettre.texte.isNullOrBlank() -> CorpsTexte(lettre.texte, ::ouvrirLien)
                lettre.html != null -> CorpsHtml(lettre.html, images, ::ouvrirLien)
                else -> Note("Ce mail n'a pas de texte.")
            }
            if (pieces.isNotEmpty()) {
                Rub(if (pieces.size == 1) "Pièce jointe" else "Pièces jointes · ${pieces.size}")
                pieces.forEach { p ->
                    val dangereuse = Bouclier.pieceJointe(p.nom) != null
                    LigneAppli(
                        p.nom,
                        second = etatsPieces[p.n] ?: if (dangereuse) "Bloquée : une appli ne s'installe jamais depuis un mail" else "${taille(p.taille)} · touchez pour ouvrir",
                        couleurSecond = if (dangereuse) a.accentTexte else null,
                        debut = { IconeTrait(if (dangereuse) Icones.ALERTE else if (p.type.startsWith("image/")) Icones.IMAGE else Icones.DOCUMENT, 22.dp, if (dangereuse) a.accentTexte else a.encre2) },
                        fin = { if (!dangereuse) BoutonAppli(Icones.TELECHARGE, "Enregistrer dans Téléchargements", taille = 44.dp) { enregistrer(p) } },
                    ) { if (!dangereuse) demanderPiece(p) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoutonTexteAppli("Répondre", style = 's', icone = IconesMail.REPONDRE) { aller(Vue.Ecrire(reponse(compte, lettre, tous = false))) }
            val plusieurs = (lettre.a + "," + lettre.cc).split(',').count { it.contains('@') } > 1
            if (plusieurs) BoutonTexteAppli("À tous", style = 's', icone = IconesMail.REPONDRE_TOUS) { aller(Vue.Ecrire(reponse(compte, lettre, tous = true))) }
            BoutonTexteAppli("Transférer", style = 's', icone = IconesMail.TRANSFERER) {
                portee.launch {
                    val joints = withContext(Dispatchers.IO) {
                        pieces.filter { Bouclier.pieceJointe(it.nom) == null }.mapNotNull { p ->
                            runCatching { copierFichier(c, Courrier.telecharger(c, compte, dossier, uid, p)) }.getOrNull()
                        }
                    }
                    aller(Vue.Ecrire(transfert(compte, lettre, joints)))
                }
            }
        }
    }
    lienPiege?.let { (url, v) ->
        DialogueAppli(v.titre, v.raison + "\n\n" + url.take(120), fermer = { lienPiege = null }) {
            // Une simple prudence laisse le choix ; un faux mail ou un lien piégé, non.
            if (v.titre == "Prudence") {
                BoutonTexteAppli("Ouvrir quand même", style = 't') {
                    lienPiege = null
                    try {
                        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            BoutonTexteAppli("Ne pas ouvrir") { lienPiege = null }
        }
    }
    if (effacer) DialogueAppli("Supprimer pour de bon ?", "Ce mail est dans la corbeille : il sera effacé du serveur.", fermer = { effacer = false }) {
        BoutonTexteAppli("Annuler", style = 't') { effacer = false }
        Spacer(Modifier.width(8.dp))
        BoutonTexteAppli("Supprimer") {
            effacer = false
            portee.launch { withContext(Dispatchers.IO) { runCatching { Courrier.effacer(c, compte, dossier, uid) } } }
            retour()
        }
    }
    confirmerData?.let { p ->
        DialogueAppli("Télécharger ${taille(p.taille)} sur data ?", "Vous n'êtes pas sur un Wi-Fi : ce fichier sera pris sur votre forfait.", fermer = { confirmerData = null }) {
            BoutonTexteAppli("Plus tard", style = 't') { confirmerData = null }
            Spacer(Modifier.width(8.dp))
            BoutonTexteAppli("Télécharger") {
                confirmerData = null
                ouvrirPiece(p)
            }
        }
    }
}

private fun imagesDistantes(html: String) = Regex("(?i)<img[^>]+src\\s*=\\s*[\"']?https?:").containsMatchIn(html)

@Composable
private fun CorpsTexte(texte: String, ouvrirLien: (String) -> Unit) {
    val a = LocalIdentite.current
    val annote = remember(texte, a) {
        buildAnnotatedString {
            var i = 0
            URL.findAll(texte).forEach { m ->
                append(texte.substring(i, m.range.first))
                val url = m.value.trimEnd('.', ',', ';', ':', '!', '?')
                withLink(LinkAnnotation.Clickable(url, TextLinkStyles(SpanStyle(color = a.accentTexte, textDecoration = TextDecoration.Underline))) { ouvrirLien(url) }) { append(url) }
                append(m.value.substring(url.length))
                i = m.range.last + 1
            }
            append(texte.substring(i))
        }
    }
    BasicText(annote, modifier = Modifier.padding(horizontal = 20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 24.sp, color = a.encre))
}

/** Un mail en HTML : sans JavaScript, sans rien charger d'Internet tant qu'on ne l'a pas demandé ; les liens passent par le bouclier. */
@Composable
private fun CorpsHtml(html: String, images: Boolean, ouvrirLien: (String) -> Unit) {
    val page = remember(html) {
        "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>body{margin:0;padding:0 16px;font-family:sans-serif;font-size:16px;line-height:1.45;color:#1F1C18;background:#FFFFFF;overflow-wrap:anywhere}" +
            "img{max-width:100%;height:auto}table{max-width:100%!important}pre{white-space:pre-wrap}</style></head><body>$html</body></html>"
    }
    Box(Modifier.padding(horizontal = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.White).padding(vertical = 12.dp)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    settings.setGeolocationEnabled(false)
                    isVerticalScrollBarEnabled = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                            ouvrirLien(r.url.toString())
                            return true
                        }
                    }
                }
            },
            update = { w ->
                w.settings.blockNetworkImage = !images
                w.settings.blockNetworkLoads = !images
                w.settings.loadsImagesAutomatically = images
                if (w.tag != "$images${page.hashCode()}") {
                    w.tag = "$images${page.hashCode()}"
                    w.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun citer(l: Lettre): String {
    val corps = l.texte ?: l.html?.let { Courrier.texteDe(it) } ?: ""
    return "\n\nLe ${dateLongue(l.date)}, ${l.nomAffiche} a écrit :\n" + corps.trimEnd().lines().joinToString("\n") { "> $it" }
}

private fun reponse(compte: Compte, l: Lettre, tous: Boolean): Brouillon {
    val envoye = l.de.equals(compte.adresse, true)
    val a = if (envoye) l.a else l.de
    val cc = if (!tous) "" else (l.a + "," + l.cc).split(',').map { it.trim() }.filter { it.contains('@') && !it.contains(compte.adresse, true) && !it.contains(l.de, true) }.joinToString(", ")
    val sujet = if (Regex("(?i)^(re|ré)\\s*:").containsMatchIn(l.sujet)) l.sujet else "Re : ${l.sujet}"
    return Brouillon(compte = compte.id, a = a, cc = cc, sujet = sujet, texte = citer(l), enReponseA = l.idMessage, references = l.references)
}

private fun transfert(compte: Compte, l: Lettre, pieces: List<String>): Brouillon {
    val corps = l.texte ?: l.html?.let { Courrier.texteDe(it) } ?: ""
    val entete = "\n\n---------- Message transféré ----------\nDe : ${l.nomAffiche} <${l.de}>\nDate : ${dateLongue(l.date)}\nObjet : ${l.sujet}\nÀ : ${l.a}\n\n"
    return Brouillon(compte = compte.id, a = "", sujet = "Tr : ${l.sujet}", texte = entete + corps, pieces = pieces)
}

/** Une copie d'une pièce jointe téléchargée, qui partira avec un mail transféré. */
private fun copierFichier(c: Context, f: File): String {
    val d = File(c.filesDir, "joints/${java.util.UUID.randomUUID()}").apply { mkdirs() }
    return f.copyTo(File(d, f.name)).path
}

/** Enregistrer une pièce jointe dans Téléchargements (Fichiers la retrouve là). */
private fun enregistrerDansTelechargements(c: Context, f: File, type: String) {
    if (Build.VERSION.SDK_INT < 29) throw IllegalStateException("Android trop ancien")
    val v = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, f.name)
        put(MediaStore.Downloads.MIME_TYPE, type.ifBlank { "application/octet-stream" })
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val u = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw IllegalStateException("Téléchargements indisponible")
    c.contentResolver.openOutputStream(u)!!.use { s -> f.inputStream().use { it.copyTo(s) } }
    c.contentResolver.update(u, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
}
