package africa.samaos.fichiers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as CouleurAndroid
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Tete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Un PDF ouvert : ses pages, dessinées par le moteur PDF d'Android (rien à télécharger, rien qui sorte).
 * Les pages se rendent à la demande ; les dernières vues restent en mémoire.
 */
class Pdf(c: Context, val uri: Uri) {
    val nom: String = try {
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"

    private val fd: ParcelFileDescriptor = ouvrir(c, uri)
    private val rendu = PdfRenderer(fd)
    private val verrou = Mutex()
    private val cache = object : LruCache<Int, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
    val pages = rendu.pageCount
    /** Largeur sur hauteur de chaque page, pour réserver leur place avant de les dessiner. */
    val formats: List<Float> = (0 until pages).map { i -> rendu.openPage(i).use { it.width.toFloat() / it.height } }

    suspend fun page(i: Int, largeur: Int): Bitmap? = verrou.withLock {
        cache.get(i)?.takeIf { it.width >= largeur }?.let { return it }
        withContext(Dispatchers.IO) {
            try {
                rendu.openPage(i).use { p ->
                    val l = largeur.coerceIn(200, 2400)
                    val b = Bitmap.createBitmap(l, (l * p.height.toFloat() / p.width).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    b.eraseColor(CouleurAndroid.WHITE)
                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(i, b)
                    b
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun fermer() {
        try {
            rendu.close()
            fd.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        /** Le moteur veut un fichier qu'on peut parcourir : sinon, une copie dans le cache. */
        private fun ouvrir(c: Context, uri: Uri): ParcelFileDescriptor {
            val direct = try {
                c.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) {
                null
            }
            if (direct != null && direct.statSize > 0) return direct
            direct?.close()
            val copie = File(c.cacheDir, "lecture.pdf")
            c.contentResolver.openInputStream(uri)?.use { e -> FileOutputStream(copie).use { e.copyTo(it) } }
            return ParcelFileDescriptor.open(copie, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }
}

/** Lire un PDF, depuis Fichiers ou depuis n'importe quelle appli (ACTION_VIEW application/pdf). */
class LirePdf : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent.data
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.FichiersNuit else Identites.Fichiers
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !id.sombre
                    isAppearanceLightNavigationBars = !id.sombre
                }
            }
            CompositionLocalProvider(LocalIdentite provides id) { PageLirePdf(uri) { finish() } }
        }
    }
}

@Composable
private fun PageLirePdf(uri: Uri?, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val ouvert by produceState<Result<Pdf>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                Result.success(Pdf(c, uri ?: error("pas de fichier")))
            } catch (e: SecurityException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    val pdf = ouvert?.getOrNull()
    DisposableEffect(pdf) { onDispose { pdf?.fermer() } }
    var loupe by remember { mutableStateOf<Int?>(null) }
    val etat = rememberLazyListState()
    // La page qui occupe le milieu de l'écran.
    val courante by remember {
        derivedStateOf {
            val info = etat.layoutInfo
            val milieu = (info.viewportStartOffset + info.viewportEndOffset) / 2
            (info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - milieu) }?.index ?: 0) + 1
        }
    }
    Box(Modifier.fillMaxSize()) {
        EcranAppli {
            Tete(pdf?.nom?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: "PDF", retour = fermer, petit = true) {
                if (pdf != null && uri != null) {
                    BoutonAppli(Icones.PARTAGER, "Partager") {
                        val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        c.startActivity(Intent.createChooser(i, "Partager « ${pdf.nom} »"))
                    }
                    BoutonAppli("M7 9V4h10v5 M6 18H4v-7h16v7h-2 M7 14h10v6H7z", "Imprimer") { imprimer(c, uri, pdf.nom) }
                }
            }
            val echec = ouvert?.exceptionOrNull()
            when {
                echec != null -> BasicText(
                    if (echec is SecurityException) "Ce PDF est protégé par un mot de passe : Sama ne sait pas encore l'ouvrir."
                    else "Ce PDF est abîmé ou incomplet : il ne s'ouvre pas.",
                    modifier = Modifier.padding(20.dp),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 22.sp, color = a.encre2),
                )
                pdf != null -> LazyColumn(
                    Modifier.fillMaxSize().background(a.champ), state = etat,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(pdf.pages) { i -> PagePdf(pdf, i) { loupe = i } }
                }
            }
        }
        if (pdf != null && pdf.pages > 1) {
            BasicText(
                "Page $courante sur ${pdf.pages}",
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp).clip(RoundedCornerShape(16.dp))
                    .background(a.encre).padding(horizontal = 14.dp, vertical = 8.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = a.fond),
            )
        }
        loupe?.let { i -> if (pdf != null) Loupe(pdf, i) { loupe = null } }
    }
}

@Composable
private fun PagePdf(pdf: Pdf, i: Int, ouvrir: () -> Unit) {
    val c = LocalContext.current
    val largeur = c.resources.displayMetrics.widthPixels
    val image by produceState<Bitmap?>(null, i) { value = pdf.page(i, largeur) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(pdf.formats[i]).clip(RoundedCornerShape(4.dp)).background(Color.White)
            .clickable(onClickLabel = "Agrandir la page ${i + 1}", role = Role.Button, onClick = ouvrir),
    ) {
        image?.let { Image(it.asImageBitmap(), contentDescription = "Page ${i + 1}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

/** Une page en grand : deux doigts pour agrandir et déplacer. */
@Composable
private fun Loupe(pdf: Pdf, i: Int, fermer: () -> Unit) {
    val c = LocalContext.current
    BackHandler(onBack = fermer)
    var echelle by remember { mutableFloatStateOf(1f) }
    var decalage by remember { mutableStateOf(Offset.Zero) }
    // Une image plus fine pour le zoom : deux fois la largeur de l'écran.
    val image by produceState<Bitmap?>(null, i) { value = pdf.page(i, c.resources.displayMetrics.widthPixels * 2) }
    LaunchedEffect(i) {
        echelle = 1f
        decalage = Offset.Zero
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF141210)).pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                echelle = (echelle * zoom).coerceIn(1f, 5f)
                decalage = if (echelle == 1f) Offset.Zero else decalage + pan
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(
                it.asImageBitmap(), contentDescription = "Page ${i + 1} en grand",
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = echelle
                    scaleY = echelle
                    translationX = decalage.x
                    translationY = decalage.y
                },
                contentScale = ContentScale.FillWidth,
            )
        }
        Box(Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 8.dp)) {
            CompositionLocalProvider(LocalIdentite provides Identites.FichiersNuit) { BoutonAppli("M6 6l12 12 M18 6L6 18", "Fermer", onClick = fermer) }
        }
    }
}

/** Imprimer le PDF tel quel (Réglages › Impression). */
private fun imprimer(c: Context, uri: Uri, nom: String) {
    val pm = c.getSystemService(PrintManager::class.java) ?: return
    pm.print(
        nom,
        object : PrintDocumentAdapter() {
            override fun onLayout(ancien: PrintAttributes?, nouveau: PrintAttributes, annule: CancellationSignal?, rappel: LayoutResultCallback, extras: Bundle?) {
                if (annule?.isCanceled == true) return rappel.onLayoutCancelled()
                rappel.onLayoutFinished(PrintDocumentInfo.Builder(nom).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }

            override fun onWrite(pages: Array<out PageRange>, sortie: ParcelFileDescriptor, annule: CancellationSignal?, rappel: WriteResultCallback) {
                try {
                    c.contentResolver.openInputStream(uri)?.use { e -> FileOutputStream(sortie.fileDescriptor).use { e.copyTo(it) } }
                    rappel.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    rappel.onWriteFailed(e.message)
                }
            }
        },
        null,
    )
}
