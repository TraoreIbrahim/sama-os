package africa.samaos.photos

import android.app.Activity
import android.app.KeyguardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.AvecIdentite
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.LocalFermerFeuille
import africa.samaos.banco.appli.LocalIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Une photo ou une vidéo masquée : sortie de la pellicule, gardée dans l'espace privé de Photos. */
class Masquee(
    val id: String,
    val fichier: String,
    val nom: String,
    val mime: String,
    val video: Boolean,
    val date: Long,
    val duree: Long,
    val largeur: Int,
    val hauteur: Int,
    val taille: Long,
    /** Le dossier d'où elle vient (« DCIM/Camera/ ») : elle y retourne quand on l'affiche de nouveau. */
    val dossier: String,
    val album: String,
) {
    val uri: Uri get() = Uri.fromFile(File(fichier))
    val jour: LocalDate get() = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()
}

/**
 * Le coffre des Masquées. Une photo masquée quitte la pellicule (les autres applis ne la voient plus) et va dans
 * l'espace privé de Photos ; l'album ne s'ouvre qu'avec le déverrouillage du téléphone et se referme dès qu'on le quitte.
 */
object Coffre {
    /** Ouvert avec le code depuis qu'on est dans l'album. */
    var ouvert by mutableStateOf(false)
    val entrees = mutableStateListOf<Masquee>()
    private var charge = false
    private val vignettes = LruCache<String, ImageBitmap>(200)

    private fun dossier(c: Context) = File(c.filesDir, "masquees").apply { mkdirs() }
    private fun index(c: Context) = File(dossier(c), "index.json")

    fun protege(c: Context) = c.getSystemService(KeyguardManager::class.java).isDeviceSecure

    @Suppress("DEPRECATION")
    fun demande(c: Context): Intent? = c.getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent("Masquées", "Déverrouillez pour ouvrir l'album")

    fun charger(c: Context) {
        if (charge) return
        charge = true
        try {
            val a = JSONArray(index(c).readText())
            entrees.clear()
            for (i in 0 until a.length()) a.getJSONObject(i).let { o ->
                val x = Masquee(
                    o.getString("id"), o.getString("fichier"), o.getString("nom"), o.getString("mime"), o.getBoolean("video"), o.getLong("date"), o.optLong("duree"),
                    o.optInt("largeur"), o.optInt("hauteur"), o.optLong("taille"), o.optString("dossier"), o.optString("album"),
                )
                if (File(x.fichier).exists()) entrees += x
            }
        } catch (_: Exception) {
        }
    }

    private fun ecrire(c: Context) {
        val a = JSONArray(
            entrees.map { x ->
                JSONObject().put("id", x.id).put("fichier", x.fichier).put("nom", x.nom).put("mime", x.mime).put("video", x.video).put("date", x.date).put("duree", x.duree)
                    .put("largeur", x.largeur).put("hauteur", x.hauteur).put("taille", x.taille).put("dossier", x.dossier).put("album", x.album)
            },
        )
        val f = index(c)
        val tmp = File(f.parentFile, "index.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(f)
    }

    /** Masquer : copier dans l'espace privé, vérifier la copie, puis seulement retirer la photo de la pellicule. */
    fun masquer(c: Context, m: Media): Masquee? {
        charger(c)
        val id = UUID.randomUUID().toString()
        val d = File(dossier(c), id).apply { mkdirs() }
        val f = File(d, m.nom.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { if (m.video) "video.mp4" else "photo.jpg" })
        try {
            c.contentResolver.openInputStream(m.uri)!!.use { e -> FileOutputStream(f).use { s -> e.copyTo(s); s.fd.sync() } }
            if (f.length() == 0L || (m.taille > 0 && f.length() != m.taille)) throw IOException("Copie incomplète")
            if (m.date > 0) f.setLastModified(m.date)
            if (!Galerie.effacer(c, m)) throw IOException("Pas retirée de la pellicule")
        } catch (_: Exception) {
            d.deleteRecursively()
            return null
        }
        val x = Masquee(id, f.path, m.nom, m.mime, m.video, m.date, m.duree, m.largeur, m.hauteur, f.length(), m.dossier, Galerie.nomAlbum(m.album, m.dossier))
        entrees.add(0, x)
        entrees.sortByDescending { it.date }
        ecrire(c)
        return x
    }

    /** Remettre dans la pellicule, dans son dossier d'avant ; la copie privée part ensuite. */
    fun afficher(c: Context, x: Masquee): Boolean {
        val col = if (x.video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, x.nom)
            put(MediaStore.MediaColumns.MIME_TYPE, x.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, x.dossier.ifBlank { if (x.video) "Movies/" else "Pictures/" })
            put(MediaStore.MediaColumns.DATE_TAKEN, x.date)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            c.contentResolver.insert(col, v)
        } catch (_: Exception) {
            null
        } ?: return false
        try {
            c.contentResolver.openOutputStream(uri)!!.use { s -> File(x.fichier).inputStream().use { it.copyTo(s) } }
            // En publiant le fichier, Android recalcule l'heure de la photo d'après la date du fichier (celle de la copie,
            // donc « maintenant ») : on redonne au fichier sa date d'avant pour qu'elle reprenne sa place dans la pellicule.
            dateDuFichier(c, uri, x.date)
            c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (_: Exception) {
            try {
                c.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            return false
        }
        File(x.fichier).parentFile?.deleteRecursively()
        entrees.remove(x)
        ecrire(c)
        return true
    }

    @Suppress("DEPRECATION")
    private fun dateDuFichier(c: Context, uri: Uri, date: Long) {
        if (date <= 0) return
        try {
            c.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { k ->
                if (k.moveToFirst()) k.getString(0)?.let { File(it).setLastModified(date) }
            }
        } catch (_: Exception) {
        }
    }

    /** Supprimer pour de bon (les Masquées n'ont pas de corbeille : on le demande avant). */
    fun supprimer(c: Context, x: Masquee) {
        File(x.fichier).parentFile?.deleteRecursively()
        entrees.remove(x)
        vignettes.remove(x.id)
        ecrire(c)
    }

    fun vignetteEnCache(x: Masquee): ImageBitmap? = vignettes.get(x.id)

    fun vignette(c: Context, x: Masquee): ImageBitmap? = vignettes.get(x.id) ?: try {
        val f = File(x.fichier)
        val b = if (x.video) {
            if (Build.VERSION.SDK_INT >= 29) android.media.ThumbnailUtils.createVideoThumbnail(f, android.util.Size(320, 320), null)
            else MediaMetadataRetriever().run {
                try {
                    setDataSource(f.path)
                    getFrameAtTime(0)
                } finally {
                    release()
                }
            }
        } else {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { d, info, _ ->
                val r = maxOf(info.size.width, info.size.height) / 320f
                if (r > 1) d.setTargetSize((info.size.width / r).toInt().coerceAtLeast(1), (info.size.height / r).toInt().coerceAtLeast(1))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        b?.asImageBitmap()?.also { vignettes.put(x.id, it) }
    } catch (_: Exception) {
        null
    }
}

private fun Context.activite(): Activity? = this as? Activity ?: (this as? ContextWrapper)?.baseContext?.activite()

/**
 * L'album Masquées : fermé, il demande le déverrouillage du téléphone ; ouvert, les photos masquées, qu'on
 * regarde, qu'on remet dans la pellicule ou qu'on supprime. Il se referme dès qu'on le quitte.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageMasquees() {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val x = LocalActions.current
    val portee = rememberCoroutineScope()
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> if (r.resultCode == Activity.RESULT_OK) Coffre.ouvert = true }
    fun ouvrir() {
        val i = Coffre.demande(c)
        if (i != null) demande.launch(i) else Coffre.ouvert = true
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Coffre.charger(c) }
        if (!Coffre.ouvert) ouvrir()
    }
    // L'album ne se montre pas dans les applis récentes, et se referme en sortant.
    DisposableEffect(Unit) {
        val act = c.activite()
        if (Build.VERSION.SDK_INT >= 33) act?.setRecentsScreenshotEnabled(false)
        onDispose {
            Coffre.ouvert = false
            if (Build.VERSION.SDK_INT >= 33) act?.setRecentsScreenshotEnabled(true)
        }
    }
    val choix = remember { mutableStateListOf<String>() }
    var enChoix by remember { mutableStateOf(false) }
    var regarde by remember { mutableStateOf<Int?>(null) }
    var aSupprimer by remember { mutableStateOf<List<Masquee>?>(null) }
    val finir = { enChoix = false; choix.clear() }
    BackHandler(enabled = enChoix) { finir() }
    LaunchedEffect(choix.size) { if (choix.isNotEmpty()) enChoix = true }
    fun afficher(l: List<Masquee>) {
        finir()
        portee.launch {
            val ok = withContext(Dispatchers.IO) { l.count { Coffre.afficher(c, it) } }
            x.changer()
            x.dire(if (ok == l.size) (if (ok > 1) "$ok éléments remis dans la pellicule" else "Remise dans la pellicule") else "${l.size - ok} n'ont pas pu être remis", null)
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        AnimatedContent(Coffre.ouvert, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) }, label = "coffre") { ouvert ->
            if (!ouvert) {
                Column(Modifier.fillMaxSize()) {
                    Barre("Collections", x.retour)
                    Column(Modifier.fillMaxSize().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(84.dp).clip(CircleShape).background(a.voile), contentAlignment = Alignment.Center) { IconeTrait(Icones.CADENAS, 36.dp, a.accentTexte) }
                        BasicText("Masquées", modifier = Modifier.padding(top = 20.dp), style = st(24f, a.encre, FontWeight.Bold))
                        BasicText(
                            "L'album s'ouvre avec le déverrouillage du téléphone. Ce qu'il contient n'apparaît ni dans la pellicule ni dans les autres applis.",
                            modifier = Modifier.padding(top = 8.dp), style = st(15f, a.encre2, interligne = 1.45f).copy(textAlign = TextAlign.Center),
                        )
                        Spacer(Modifier.height(24.dp))
                        BoutonTexteAppli("Déverrouiller", icone = Icones.CADENAS) { ouvrir() }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Barre("Collections", x.retour) { if (Coffre.entrees.isNotEmpty()) Capsule(if (enChoix) "Fini" else "Choisir", actif = enChoix) { if (enChoix) finir() else enChoix = true } }
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                GrandTitre("Masquées", if (Coffre.entrees.isEmpty()) "Rien de masqué" else compteMasquees(Coffre.entrees))
                                if (!Coffre.protege(c)) BasicText(
                                    "Le téléphone n'a pas de code : n'importe qui peut ouvrir cet album. Mettez un code dans Réglages › Sécurité.",
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(a.voile).padding(14.dp),
                                    style = st(14f, a.accentTexte, interligne = 1.4f),
                                )
                                if (Coffre.entrees.isEmpty()) Vide(Icones.OEIL_BARRE, "Pour masquer une photo : ouvrez-la, touchez ⋯ puis « Masquer » ; ou choisissez-en plusieurs dans la pellicule.")
                            }
                        }
                        items(Coffre.entrees, key = { it.id }) { m ->
                            val choisie = m.id in choix
                            VignetteMasquee(
                                m, enChoix, choisie,
                                Modifier.animateItem().aspectRatio(1f).combinedClickable(onLongClick = { if (choisie) choix.remove(m.id) else choix.add(m.id) }) {
                                    if (enChoix) { if (choisie) choix.remove(m.id) else choix.add(m.id) } else regarde = Coffre.entrees.indexOf(m)
                                },
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.navigationBarsPadding().height(110.dp)) }
                    }
                }
            }
        }
        // En choisissant : remettre dans la pellicule, partager, supprimer.
        AnimatedVisibility(Coffre.ouvert && enChoix, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut()) {
            val choisies = Coffre.entrees.filter { it.id in choix }
            Row(
                Modifier.navigationBarsPadding().padding(bottom = 10.dp).height(60.dp).clip(RoundedCornerShape(30.dp)).background(a.surface).border(1.dp, a.trait, RoundedCornerShape(30.dp)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(if (choisies.isEmpty()) "Touchez des photos" else "${choisies.size} choisie${if (choisies.size > 1) "s" else ""}", modifier = Modifier.padding(horizontal = 14.dp), style = st(15f, a.encre, FontWeight.SemiBold))
                BoutonCoffre(Icones.OEIL, "Afficher dans la pellicule", choisies.isNotEmpty()) { afficher(choisies) }
                BoutonCoffre(Icones.PARTAGER, "Partager", choisies.isNotEmpty()) { partagerMasquees(c, choisies) }
                BoutonCoffre(Icones.CORBEILLE, "Supprimer", choisies.isNotEmpty()) { aSupprimer = choisies }
            }
        }
        regarde?.let { i ->
            VisionneuseMasquee(i, fermer = { regarde = null }, afficher = { afficher(listOf(it)) }, supprimer = { aSupprimer = listOf(it) })
        }
    }
    aSupprimer?.let { l ->
        DialogueAppli(
            if (l.size > 1) "Supprimer ces ${l.size} éléments ?" else "Supprimer pour de bon ?",
            "Les Masquées n'ont pas de corbeille : on ne pourra plus les retrouver.",
            fermer = { aSupprimer = null },
        ) {
            val f = LocalFermerFeuille.current
            BoutonTexteAppli("Annuler", style = 't') { f() }
            BoutonTexteAppli("Supprimer") {
                f()
                finir()
                l.forEach { Coffre.supprimer(c, it) }
                if (Coffre.entrees.isEmpty()) regarde = null else regarde?.let { regarde = it.coerceAtMost(Coffre.entrees.lastIndex) }
            }
        }
    }
}

private fun compteMasquees(l: List<Masquee>): String {
    val v = l.count { it.video }
    val p = l.size - v
    return listOfNotNull(if (p > 0) "$p photo${if (p > 1) "s" else ""}" else null, if (v > 0) "$v vidéo${if (v > 1) "s" else ""}" else null).joinToString(" · ")
}

private fun partagerMasquees(c: Context, l: List<Masquee>) {
    val uris = l.map { FileProvider.getUriForFile(c, "africa.samaos.photos.partage", File(it.fichier)) }
    envoyer(c, uris, l.map { it.mime })
}

@Composable
private fun BoutonCoffre(icone: String, nom: String, actif: Boolean, onClick: () -> Unit) {
    val a = LocalIdentite.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = actif, onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, 22.dp, if (actif) a.encre else a.encre2.copy(alpha = 0.45f)) }
}

@Composable
private fun VignetteMasquee(m: Masquee, enChoix: Boolean, choisie: Boolean, modifier: Modifier) {
    val c = LocalContext.current
    val a = LocalIdentite.current
    val image by produceState(Coffre.vignetteEnCache(m), m.id) {
        if (value == null) value = withContext(Dispatchers.IO) { Coffre.vignette(c, m) }
    }
    val retrait by animateDpAsState(if (choisie) 10.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "retrait")
    Box(modifier) {
        Box(Modifier.fillMaxSize().padding(retrait).clip(RoundedCornerShape(4.dp + retrait / 2)).background(a.surface)) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (m.video) BasicText(duree(m.duree), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp), style = st(12f, Color.White, FontWeight.Bold))
        }
        if (enChoix) Box(
            Modifier.align(Alignment.BottomEnd).padding(7.dp).size(22.dp).clip(CircleShape).background(if (choisie) a.accent else Color(0x33000000)).border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (choisie) IconeTrait(Icones.COCHE, 14.dp, a.surAccent, epaisseur = 2.4f) }
    }
}

/** Regarder les Masquées, une à une : on les remet dans la pellicule, on les partage, on les supprime. */
@Composable
private fun VisionneuseMasquee(depart: Int, fermer: () -> Unit, afficher: (Masquee) -> Unit, supprimer: (Masquee) -> Unit) {
    val c = LocalContext.current
    val l = Coffre.entrees
    if (l.isEmpty()) {
        LaunchedEffect(Unit) { fermer() }
        return
    }
    val pager = rememberPagerState(initialPage = depart.coerceIn(0, l.lastIndex)) { l.size }
    var habits by remember { mutableStateOf(true) }
    var zoome by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    BackHandler { fermer() }
    val m = l[pager.currentPage.coerceIn(0, l.lastIndex)]
    AvecIdentite(Noir) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 400f), initialScale = 0.92f), exit = fadeOut() + scaleOut(targetScale = 0.92f)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoome, key = { l[it].id }) { p ->
                    val mp = l[p]
                    if (mp.video) PageVideo(mp.uri, Coffre.vignetteEnCache(mp), { habits = !habits })
                    else ImageZoom(mp.uri, Coffre.vignetteEnCache(mp), { habits = !habits }, { if (p == pager.currentPage) zoome = it })
                }
                AnimatedVisibility(habits, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))).statusBarsPadding().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClickLabel = "Revenir", role = Role.Button, onClick = fermer), contentAlignment = Alignment.Center) {
                                IconeTrait(Icones.RETOUR, 21.dp, Color(0xFFF4EEE4), epaisseur = 2f)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                BasicText(nomDuJour(m.jour), style = st(16f, Color(0xFFF4EEE4), FontWeight.SemiBold))
                                BasicText(
                                    Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) + " · masquée, venue de « ${m.album} »",
                                    style = st(13f, Color(0xFFB3AA9C)),
                                )
                            }
                            Spacer(Modifier.size(42.dp))
                        }
                        Row(
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp).clip(RoundedCornerShape(30.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ActionNoire(Icones.OEIL, "Afficher dans la pellicule") {
                                fermer()
                                afficher(m)
                            }
                            ActionNoire(Icones.PARTAGER, "Partager") { partagerMasquees(c, listOf(m)) }
                            ActionNoire(Icones.CORBEILLE, "Supprimer") { supprimer(m) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionNoire(icone: String, nom: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).clickable(onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, 23.dp, Color(0xFFF4EEE4)) }
}
