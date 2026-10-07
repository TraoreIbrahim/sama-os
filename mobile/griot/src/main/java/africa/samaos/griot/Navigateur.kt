package africa.samaos.griot

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import africa.samaos.banco.IconeTrait
import africa.samaos.bouclier.Verdict
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.selectionTexte
import africa.samaos.banco.appli.ActionFeuille
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.DialogueAppli
import africa.samaos.banco.appli.EcranAppli
import africa.samaos.banco.appli.FeuilleAppli
import africa.samaos.banco.appli.Identites
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LigneAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Rub
import africa.samaos.banco.appli.Tete
import africa.samaos.banco.appli.AvecIdentite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NUIT = Color(0xFF1E2740)
private val OR = Color(0xFFF2C879)
private val BLANC = Color(0xFFFFFFFF)
/** Le rouge du danger de Banco, version nuit (lisible sur la barre bleue). */
private val ROUGE = Color(0xFFF08A80)

private object Ic {
    const val CADENAS = "M7 11V8a5 5 0 0 1 10 0v3 M5 11h14v10H5z"
    const val ACTUALISER = "M20 12a8 8 0 1 1-2.3-5.7 M20 4v4h-4"
    const val LEGER = "M5 19c0-8 6-14 14-14c0 8-6 14-14 14z M5 19l7-7"
    const val ETOILE = "M12 3.5l2.6 5.4l5.9.8l-4.3 4.1l1 5.9L12 16.9l-5.2 2.8l1-5.9l-4.3-4.1l5.9-.8z"
    const val HISTORIQUE = "M4 12a8 8 0 1 0 2.3-5.6 M4 4v4h4 M12 8v4l3 2"
    const val ORDINATEUR = "M4 5h16v11H4z M9 20h6 M12 16v4"
    const val ALERTE = "M12 4l9 16H3z M12 10v4 M12 17h.01"
    const val BOUCLIER = "M12 3l7 3v5c0 4.5-3 8.3-7 10c-4-1.7-7-5.5-7-10V6z M12 8.5v4 M12 15.5h.01"
}

/** Griot, le navigateur de Sama (maquette l2-griot). */
class Navigateur : ComponentActivity(), Hote {
    private lateinit var carnet: Carnet
    private var rappelFichier: ValueCallback<Array<Uri>>? = null
    private val choixFichier = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        rappelFichier?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        rappelFichier = null
    }
    internal var message by mutableStateOf<String?>(null)
    internal var pleinEcran by mutableStateOf<View?>(null)
    internal var appli by mutableStateOf<Triple<String, String, String?>?>(null)
    internal var version by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        carnet = Carnet(this)
        val p = getSharedPreferences("griot", MODE_PRIVATE)
        Griot.leger = p.getBoolean("leger", false)
        Griot.moteur = p.getInt("moteur", 0)
        Griot.surs += p.getStringSet("surs", emptySet()).orEmpty()
        if (Griot.onglets.isEmpty()) restaurer()
        lire(intent)
        setContent {
            val id = if (isSystemInDarkTheme()) Identites.GriotNuit else Identites.Griot
            AvecIdentite(id) { Ecran(this, carnet) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lire(intent)
    }

    /** Un lien ouvert depuis une autre appli, ou une recherche. */
    private fun lire(i: Intent?) {
        val cible = when (i?.action) {
            Intent.ACTION_VIEW -> i.dataString?.let { if (it.startsWith("geo:")) Griot.carte(it) else it.takeIf { u -> u.startsWith("http") } }
            Intent.ACTION_WEB_SEARCH -> i.getStringExtra("query")?.let { Griot.adresse(it) }
            else -> null
        }
        if (cible != null) {
            val vide = Griot.courant?.takeIf { it.url == null }
            val o = vide ?: nouvelOnglet()
            o.web.loadUrl(cible)
            Griot.courant = o
        } else if (Griot.courant == null) {
            Griot.courant = Griot.onglets.lastOrNull() ?: nouvelOnglet()
        }
    }

    override fun onPause() {
        super.onPause()
        Griot.courant?.web?.onPause()
        garder()
    }

    override fun onResume() {
        super.onResume()
        Griot.courant?.web?.onResume()
    }

    /** Les onglets ouverts restent d'un lancement à l'autre ; seul l'onglet en cours se recharge tout de suite. */
    private fun garder() {
        val a = JSONArray()
        Griot.onglets.forEach { o -> o.url?.let { a.put(JSONObject().put("url", it).put("titre", o.titre)) } }
        getSharedPreferences("griot", MODE_PRIVATE).edit()
            .putString("onglets", a.toString())
            .putInt("courant", Griot.onglets.indexOf(Griot.courant).coerceAtLeast(0))
            .apply()
    }

    private fun restaurer() {
        val p = getSharedPreferences("griot", MODE_PRIVATE)
        val a = try {
            JSONArray(p.getString("onglets", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        for (i in 0 until a.length()) {
            val j = a.getJSONObject(i)
            val o = Griot.creer(this, this)
            o.url = j.getString("url")
            o.titre = j.optString("titre", Griot.domaine(o.url).first)
        }
        Griot.courant = Griot.onglets.getOrNull(p.getInt("courant", 0))?.also { o -> o.url?.let { o.web.loadUrl(it) } }
    }

    override fun nouvelOnglet(): Onglet {
        Griot.courant?.capturer()
        return Griot.creer(this, this).also { Griot.courant = it }
    }

    override fun visite(o: Onglet) {
        val u = o.url ?: return
        Thread { carnet.visite(u, o.titre) }.start()
    }

    /** Une page bloquée par le bouclier ne reste ni dans l'historique ni dans « Vos sites ». */
    internal fun oublier(url: String) {
        Thread {
            Thread.sleep(500)
            carnet.effacerPage(url)
            runOnUiThread { version++ }
        }.start()
    }

    override fun dire(texte: String) {
        message = texte
    }

    override fun pleinEcran(vue: View?) {
        pleinEcran = vue
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (vue != null) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun choisirFichier(rappel: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        rappelFichier?.onReceiveValue(null)
        rappelFichier = rappel
        return try {
            choixFichier.launch(params.createIntent())
            true
        } catch (_: Exception) {
            rappelFichier = null
            false
        }
    }

    override fun telecharger(url: String, agent: String, disposition: String?, mime: String?, taille: Long) {
        val nom = URLUtil.guessFileName(url, disposition, mime)
        // Une appli téléchargée sur un site : on prévient avant (premier moyen des arnaqueurs).
        if (nom.endsWith(".apk", true) || mime == "application/vnd.android.package-archive") {
            appli = Triple(url, agent, mime)
            return
        }
        lancer(url, agent, mime, nom)
    }

    internal fun lancer(url: String, agent: String, mime: String?, nom: String = URLUtil.guessFileName(url, null, mime)) {
        try {
            val r = DownloadManager.Request(Uri.parse(url))
                .addRequestHeader("User-Agent", agent)
                .setTitle(nom)
                .setMimeType(mime)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nom)
            CookieManager.getInstance().getCookie(url)?.let { r.addRequestHeader("Cookie", it) }
            getSystemService(DownloadManager::class.java).enqueue(r)
            message = "« $nom » se télécharge. Il sera dans Fichiers › Reçus."
        } catch (_: Exception) {
            message = "Le téléchargement n'a pas pu commencer."
        }
    }

    internal fun ouvrirRecus() {
        try {
            startActivity(Intent().setClassName("africa.samaos.fichiers", "africa.samaos.fichiers.Fichiers").putExtra("vue", "recus"))
        } catch (_: Exception) {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        }
    }

    internal fun effacerTout() {
        carnet.effacerHistorique()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        Griot.onglets.forEach { it.web.clearCache(true); it.web.clearHistory() }
        message = "Historique, cookies et données des sites effacés."
        version++
    }
}

private enum class Panneau { AUCUN, SAISIE, ONGLETS, MENU, HISTORIQUE, FAVORIS, RECHERCHE }

@Composable
private fun Ecran(n: Navigateur, carnet: Carnet) {
    val c = LocalContext.current
    var panneau by remember { mutableStateOf(Panneau.AUCUN) }
    val o = Griot.courant
    // Une page ouverte : barre d'état sur blanc (icônes sombres) ; nouvel onglet, onglets : sur la nuit de Griot.
    val clair = when (panneau) {
        Panneau.ONGLETS -> false
        Panneau.AUCUN, Panneau.RECHERCHE -> o?.url != null && ((o.danger == null && !o.voile) || !LocalIdentite.current.sombre)
        else -> !LocalIdentite.current.sombre
    }
    SideEffect {
        WindowCompat.getInsetsController(n.window, n.window.decorView).apply {
            isAppearanceLightStatusBars = clair
            isAppearanceLightNavigationBars = false
        }
    }
    BackHandler(enabled = panneau != Panneau.AUCUN || o?.peutReculer == true || n.pleinEcran != null) {
        when {
            n.pleinEcran != null -> n.pleinEcran(null)
            panneau != Panneau.AUCUN -> panneau = Panneau.AUCUN
            else -> o?.web?.goBack()
        }
    }
    Box(Modifier.fillMaxSize().background(NUIT)) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .background(if (o?.url == null) NUIT else if (o.danger != null || o.voile) LocalIdentite.current.fond else BLANC).statusBarsPadding(),
            ) {
                if (o != null) {
                    // Un onglet sans page n'attache pas son WebView (vide, il peindrait la barre d'état en blanc).
                    if (o.url != null) key(o.id) {
                        AndroidView(
                            factory = { o.web.also { w -> (w.parent as? ViewGroup)?.removeView(w); if (w.url == null) o.url?.let { w.loadUrl(it) } } },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (o.url == null) NouvelOnglet(carnet, n.version) { panneau = Panneau.SAISIE }
                    o.erreur?.let { PageErreur(it) { o.erreur = null; o.web.reload() } }
                    o.danger?.let { PageDangereuse(n, o, it) }
                    if (o.voile && o.danger == null && o.url != null) Voile(o)
                    if (o.progres < 100 && o.url != null) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x221E2740))) {
                            Box(Modifier.fillMaxWidth(o.progres / 100f).fillMaxHeight().background(OR))
                        }
                    }
                    if (panneau == Panneau.RECHERCHE) TrouverDansLaPage(o) { panneau = Panneau.AUCUN }
                    if (Griot.leger && o.imagesEvitees > 0 && o.url != null) {
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).shadow(10.dp, RoundedCornerShape(17.dp)).clip(RoundedCornerShape(17.dp)).background(NUIT)
                                .clickable(onClickLabel = "Charger toutes les images de cette page") {
                                    o.toutCharger = o.url
                                    o.web.reload()
                                    n.message = "Images chargées pour cette page. Le mode léger reste pour les suivantes."
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconeTrait(Ic.LEGER, 16.dp, OR)
                            BasicText(
                                "Mode léger · ${o.imagesEvitees} image${if (o.imagesEvitees > 1) "s" else ""} non chargée${if (o.imagesEvitees > 1) "s" else ""}",
                                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OR),
                            )
                        }
                    }
                }
            }
            Barre(o, { panneau = it })
        }
        when (panneau) {
            Panneau.SAISIE -> Saisie(o, carnet) { panneau = Panneau.AUCUN }
            Panneau.ONGLETS -> VueOnglets(n) { panneau = Panneau.AUCUN }
            Panneau.MENU -> Menu(n, carnet, o, { panneau = it })
            Panneau.HISTORIQUE -> ListePages("Historique", carnet, true, n) { panneau = Panneau.AUCUN }
            Panneau.FAVORIS -> ListePages("Favoris", carnet, false, n) { panneau = Panneau.AUCUN }
            else -> {}
        }
        n.pleinEcran?.let { v -> AndroidView(factory = { (v.parent as? ViewGroup)?.removeView(v); v }, modifier = Modifier.fillMaxSize().background(Color.Black)) }
        n.message?.let { t ->
            LaunchedEffect(t) {
                delay(4500)
                n.message = null
            }
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 92.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xFF2B3555)).padding(horizontal = 18.dp, vertical = 14.dp),
            ) { BasicText(t, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = BLANC)) }
        }
        n.appli?.let { (url, agent, mime) ->
            DialogueAppli(
                "Télécharger une appli ?",
                "Ce site propose d'installer une appli. Les arnaqueurs s'en servent pour lire vos codes et vider votre compte. " +
                    "Ne la téléchargez que si vous êtes allé la chercher vous-même chez quelqu'un de sûr.",
                fermer = { n.appli = null },
            ) {
                BoutonTexteAppli("Télécharger quand même", style = 't') {
                    n.appli = null
                    n.lancer(url, agent, mime)
                }
                BoutonTexteAppli("Ne pas télécharger") { n.appli = null }
            }
        }
    }
}

// ——— La barre du bas ———

@Composable
private fun Barre(o: Onglet?, ouvrir: (Panneau) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(NUIT).padding(start = 10.dp, end = 10.dp, top = 12.dp).navigationBarsPadding().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Bouton("M15 6l-6 6l6 6", "Page précédente", actif = o?.peutReculer == true) { o?.web?.goBack() }
        val (domaine, trompeur) = Griot.domaine(o?.url)
        val sur = o?.url?.startsWith("https://") == true
        val dangereuse = o?.danger != null
        Row(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (dangereuse) Color(0x40F08A80) else Color(0x1AFFFFFF))
                .clickable(onClickLabel = "Taper une adresse ou chercher") { ouvrir(Panneau.SAISIE) }
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                o?.url == null -> IconeTrait(Icones.RECHERCHE, 18.dp, Color(0xB3FFFFFF))
                dangereuse -> IconeTrait(Ic.ALERTE, 18.dp, ROUGE)
                trompeur -> IconeTrait(Ic.ALERTE, 18.dp, OR)
                sur -> IconeTrait(Ic.CADENAS, 18.dp, BLANC)
                else -> IconeTrait(Ic.ALERTE, 18.dp, Color(0xB3FFFFFF))
            }
            Column(Modifier.weight(1f)) {
                BasicText(
                    if (o?.url == null) "Chercher ou taper une adresse" else domaine,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = if (o?.url == null) Color(0xB3FFFFFF) else BLANC),
                )
                // Ce qu'il faut savoir du site, en petit sous son nom.
                val avis = when {
                    o?.url == null -> null
                    dangereuse -> "page dangereuse · bloquée"
                    trompeur -> "adresse trompeuse ? autre alphabet"
                    !sur -> "non sécurisé · pas de code ici"
                    else -> null
                }
                if (avis != null) BasicText(avis, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 11.sp, color = if (dangereuse) ROUGE else OR))
            }
            if (o?.url != null) {
                if (o.progres < 100) Bouton("M6 6l12 12 M18 6L6 18", "Arrêter", taille = 40) { o.web.stopLoading() }
                else Bouton(Ic.ACTUALISER, "Actualiser", taille = 40) { o.web.reload() }
            }
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Onglets", role = Role.Button) { o?.capturer(); ouvrir(Panneau.ONGLETS) }
                .semantics { contentDescription = "${Griot.onglets.size} onglets ouverts" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(24.dp).border(2.dp, OR, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                BasicText("${Griot.onglets.size}", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OR))
            }
        }
        Bouton(Icones.OPTIONS, "Menu de Griot") { ouvrir(Panneau.MENU) }
    }
}

@Composable
private fun Bouton(icone: String, nom: String, actif: Boolean = true, taille: Int = 48, onClick: () -> Unit) {
    Box(
        Modifier.size(taille.dp).clip(CircleShape).clickable(enabled = actif, onClickLabel = nom, role = Role.Button, onClick = onClick).semantics { contentDescription = nom },
        contentAlignment = Alignment.Center,
    ) { IconeTrait(icone, 22.dp, if (actif) BLANC else Color(0x55FFFFFF)) }
}

// ——— Un nouvel onglet ———

@Composable
private fun NouvelOnglet(carnet: Carnet, version: Int, saisir: () -> Unit) {
    val frequents by produceState(emptyList<Page>(), version) { value = withContext(Dispatchers.IO) { carnet.frequents() } }
    val favoris by produceState(emptyList<Page>(), version) { value = withContext(Dispatchers.IO) { carnet.favoris().take(6) } }
    Column(Modifier.fillMaxSize().background(NUIT).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(48.dp))
        BasicText("Griot", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = OR))
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color(0x1AFFFFFF)).clickable(onClickLabel = "Chercher", onClick = saisir).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconeTrait(Icones.RECHERCHE, 20.dp, Color(0xB3FFFFFF))
            BasicText("Chercher avec ${Griot.moteurs[Griot.moteur].first} ou taper une adresse", maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = Color(0xB3FFFFFF)))
        }
        if (frequents.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("Vos sites", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xB3FFFFFF)))
                frequents.chunked(4).forEach { rangee ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rangee.forEach { p -> Raccourci(p, Modifier.weight(1f)) }
                        repeat(4 - rangee.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (favoris.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("Favoris", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xB3FFFFFF)))
                favoris.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = p.titre) { Griot.courant?.web?.loadUrl(p.url) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconeTrait(Ic.ETOILE, 18.dp, OR)
                        BasicText(p.titre.ifBlank { p.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = BLANC))
                    }
                }
            }
        }
        BasicText(
            "Griot ne garde votre historique que sur ce téléphone. Mode léger, moteur de recherche : dans le menu ⋮.",
            style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0x80FFFFFF)),
        )
    }
}

@Composable
private fun Raccourci(p: Page, modifier: Modifier) {
    val d = Griot.domaine(p.url).first
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = d) { Griot.courant?.web?.loadUrl(p.url) }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x1AFFFFFF)), contentAlignment = Alignment.Center) {
            BasicText(d.take(1).uppercase(), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = OR))
        }
        BasicText(d.substringBefore('.'), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, color = BLANC))
    }
}

// ——— Le bouclier : page dangereuse (maquette i2-lien-piege) ———

/** Rien ne passe à la page en dessous : ni toucher, ni défilement. */
private fun Modifier.etanche() = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}

/** Le temps que le bouclier lise une page qui se réclame d'un opérateur. */
@Composable
private fun Voile(o: Onglet) {
    val a = LocalIdentite.current
    // Au pire, la page se montre après 8 s : l'analyse de fin de chargement garde le dernier mot.
    LaunchedEffect(o.id, o.url) {
        delay(8000)
        o.voile = false
    }
    Column(
        Modifier.fillMaxSize().background(a.fond).etanche().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconeTrait(Ic.BOUCLIER, 32.dp, a.encre2)
        BasicText("Griot vérifie cette page…", style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2))
    }
}

@Composable
private fun PageDangereuse(n: Navigateur, o: Onglet, d: Verdict) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val rouge = if (a.sombre) ROUGE else Color(0xFFA3322A)
    val hote = Uri.parse(o.url.orEmpty()).host.orEmpty()
    var signaler by remember(o.id) { mutableStateOf(false) }
    LaunchedEffect(o.url) { o.url?.let { n.oublier(it) } }
    fun revenir() {
        signaler = false
        if (o.web.canGoBack()) {
            // La page d'avant reste cachée jusqu'à ce que le bouclier l'ait lue.
            o.voile = true
            o.danger = null
            o.web.goBack()
        } else {
            Griot.fermer(o)
            n.nouvelOnglet()
        }
    }
    Column(
        Modifier.fillMaxSize().background(a.fond).etanche().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconeTrait(Ic.BOUCLIER, 64.dp, rouge, epaisseur = 1.6f)
        BasicText("Page dangereuse", style = TextStyle(fontFamily = Polices.monument, fontSize = 40.sp, lineHeight = 44.sp, color = a.encre))
        BasicText(
            d.raison + " Griot l'a bloquée avant que vous n'y tapiez quoi que ce soit.",
            style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = a.encre),
        )
        BasicText(Griot.domaine(o.url).first, style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, lineHeight = 21.sp, color = a.encre2))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Signaler une erreur",
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Dire que cette page est sûre", role = Role.Button) { signaler = true }.padding(vertical = 12.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = a.encre2),
            )
            Spacer(Modifier.weight(1f))
            BasicText("Revenir", Modifier.padding(end = 12.dp), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = a.encre))
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(a.accent).clickable(onClickLabel = "Revenir en lieu sûr", role = Role.Button) { revenir() }
                    .semantics { contentDescription = "Revenir en lieu sûr" },
                contentAlignment = Alignment.Center,
            ) { IconeTrait("M15 6l-6 6l6 6", 28.dp, a.surAccent, epaisseur = 2f) }
        }
    }
    if (signaler) {
        // Un arnaqueur au téléphone dira de toucher ici : pendant un appel, la page reste fermée.
        val enAppel = remember { c.getSystemService(android.media.AudioManager::class.java).mode.let { it == android.media.AudioManager.MODE_IN_CALL || it == android.media.AudioManager.MODE_IN_COMMUNICATION } }
        DialogueAppli(
            "Cette page est sûre ?",
            if (enAppel) "Vous êtes en ligne. Si quelqu'un vous demande d'ouvrir cette page, c'est une arnaque. Raccrochez d'abord." else
                "Si quelqu'un vous a demandé de toucher ici, c'est une arnaque. Sinon, Griot retiendra que $hote est sûr sur ce téléphone et ouvrira la page.",
            fermer = { signaler = false },
        ) {
            if (!enAppel) BoutonTexteAppli("Ouvrir la page", style = 't') {
                signaler = false
                Griot.surs += hote
                c.getSharedPreferences("griot", 0).edit().putStringSet("surs", Griot.surs.toSet()).apply()
                o.danger = null
                o.web.reload()
            }
            BoutonTexteAppli("Revenir") { revenir() }
        }
    }
}

@Composable
private fun PageErreur(texte: String, reessayer: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(BLANC).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.Start,
    ) {
        IconeTrait(Ic.ALERTE, 36.dp, NUIT)
        BasicText("La page ne s'ouvre pas", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = NUIT))
        BasicText(texte, style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, lineHeight = 23.sp, color = Color(0xFF5A627A)))
        BoutonTexteAppli("Réessayer", onClick = reessayer)
    }
}

// ——— Taper une adresse ———

@Composable
private fun Saisie(o: Onglet?, carnet: Carnet, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val actuel = o?.url.orEmpty()
    var champ by remember { mutableStateOf(TextFieldValue(actuel, TextRange(0, actuel.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val suggestions by produceState(emptyList<Page>(), champ.text) {
        value = withContext(Dispatchers.IO) { if (champ.text.isBlank() || champ.text == actuel) carnet.frequents(6) else carnet.chercher(champ.text) }
    }
    fun aller(t: String) {
        if (t.isBlank()) return
        val cible = Griot.adresse(t)
        (o ?: Griot.courant)?.web?.loadUrl(cible)
        fermer()
    }
    EcranAppli {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BoutonAppli("M15 6l-6 6l6 6", "Retour", onClick = fermer)
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(a.champ).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    champ, { champ = it }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre), cursorBrush = SolidColor(a.encre),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { aller(champ.text) }),
                    modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Adresse ou recherche" },
                    decorationBox = { f ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (champ.text.isEmpty()) BasicText("Chercher ou taper une adresse", style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = a.encre2))
                            f()
                        }
                    },
                )
                if (champ.text.isNotEmpty()) BoutonAppli("M6 6l12 12 M18 6L6 18", "Effacer", taille = 40.dp) { champ = TextFieldValue("") }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            val t = champ.text.trim()
            if (t.isNotEmpty() && t != actuel) item {
                val adresse = Griot.adresse(t)
                val recherche = adresse.startsWith(Griot.moteurs[Griot.moteur].second)
                LigneAppli(
                    if (recherche) "Chercher « $t »" else "Aller sur ${Griot.domaine(adresse).first}",
                    if (recherche) "avec ${Griot.moteurs[Griot.moteur].first}" else adresse,
                    debut = { IconeTrait(if (recherche) Icones.RECHERCHE else Icones.AVANCER, 22.dp, a.encre2) },
                ) { aller(t) }
            }
            items(suggestions, key = { it.url }) { p ->
                LigneAppli(
                    p.titre.ifBlank { Griot.domaine(p.url).first }, Griot.domaine(p.url).first,
                    debut = { IconeTrait(Ic.HISTORIQUE, 22.dp, a.encre2) },
                ) {
                    (o ?: Griot.courant)?.web?.loadUrl(p.url)
                    fermer()
                }
            }
        }
    }
}

// ——— Les onglets ———

@Composable
private fun VueOnglets(n: Navigateur, fermer: () -> Unit) {
    Column(Modifier.fillMaxSize().background(NUIT).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Bouton("M15 6l-6 6l6 6", "Retour", onClick = fermer)
            BasicText(
                "${Griot.onglets.size} onglet${if (Griot.onglets.size > 1) "s" else ""}", modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = BLANC),
            )
            Row(
                Modifier.clip(RoundedCornerShape(22.dp)).background(OR).clickable(onClickLabel = "Nouvel onglet") { n.nouvelOnglet(); fermer() }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconeTrait(Icones.PLUS, 18.dp, NUIT)
                BasicText("Nouvel onglet", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = NUIT))
            }
        }
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Griot.onglets.toList(), key = { it.id }) { o ->
                val ici = o == Griot.courant
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF2B3555))
                        .then(if (ici) Modifier.border(2.dp, OR, RoundedCornerShape(16.dp)) else Modifier)
                        .clickable(onClickLabel = o.titre) { Griot.courant = o; fermer() },
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(o.titre, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BLANC))
                        Bouton("M6 6l12 12 M18 6L6 18", "Fermer l'onglet ${o.titre}", taille = 40) {
                            Griot.fermer(o)
                            if (Griot.onglets.isEmpty()) {
                                n.nouvelOnglet()
                                fermer()
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(0.75f).background(BLANC)) {
                        o.apercu?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) }
                            ?: Box(Modifier.fillMaxSize().background(NUIT), contentAlignment = Alignment.Center) {
                                BasicText(Griot.domaine(o.url).first.ifBlank { "Nouvel onglet" }, style = TextStyle(fontFamily = Polices.corps, fontSize = 13.sp, color = Color(0xB3FFFFFF)))
                            }
                    }
                }
            }
        }
    }
}

// ——— Le menu ———

@Composable
private fun Menu(n: Navigateur, carnet: Carnet, o: Onglet?, ouvrir: (Panneau) -> Unit) {
    val c = LocalContext.current
    val fermer = { ouvrir(Panneau.AUCUN) }
    var favori by remember { mutableStateOf(o?.url?.let { carnet.estFavori(it) } == true) }
    var effacer by remember { mutableStateOf(false) }
    FeuilleAppli(fermer = fermer) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            ActionFeuille(Icones.PLUS, "Nouvel onglet") { n.nouvelOnglet(); fermer() }
            if (o?.url != null) {
                ActionFeuille(Ic.ETOILE, if (favori) "Retirer des favoris" else "Ajouter aux favoris") {
                    favori = carnet.basculerFavori(o.url!!, o.titre)
                    n.message = if (favori) "Ajouté aux favoris" else "Retiré des favoris"
                    n.version++
                    fermer()
                }
                ActionFeuille(Icones.PARTAGER, "Partager la page") {
                    fermer()
                    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, o.url).putExtra(Intent.EXTRA_SUBJECT, o.titre), "Partager la page"))
                }
                ActionFeuille(Icones.RECHERCHE, "Trouver dans la page") { ouvrir(Panneau.RECHERCHE) }
            }
            ActionFeuille(Ic.ETOILE, "Favoris") { ouvrir(Panneau.FAVORIS) }
            ActionFeuille(Ic.HISTORIQUE, "Historique") { ouvrir(Panneau.HISTORIQUE) }
            ActionFeuille(Icones.TELECHARGE, "Téléchargements", second = "Dans Fichiers › Reçus") { fermer(); n.ouvrirRecus() }
            LigneAppli("Mode léger", "Les images se chargent quand on les touche : moins de data", debut = { IconeTrait(Ic.LEGER, 22.dp, LocalIdentite.current.encre2) }, fin = { InterAppli(Griot.leger) }) {
                Griot.leger = !Griot.leger
                c.getSharedPreferences("griot", 0).edit().putBoolean("leger", Griot.leger).apply()
                o?.web?.reload()
                fermer()
            }
            LigneAppli("Version ordinateur", "Le site comme sur un ordinateur", debut = { IconeTrait(Ic.ORDINATEUR, 22.dp, LocalIdentite.current.encre2) }, fin = { InterAppli(Griot.ordinateur) }) {
                Griot.ordinateur = !Griot.ordinateur
                Griot.onglets.forEach { Griot.agent(it) }
                o?.web?.reload()
                fermer()
            }
            LigneAppli("Moteur de recherche", Griot.moteurs[Griot.moteur].first, debut = { IconeTrait(Icones.RECHERCHE, 22.dp, LocalIdentite.current.encre2) }) {
                Griot.moteur = (Griot.moteur + 1) % Griot.moteurs.size
                c.getSharedPreferences("griot", 0).edit().putInt("moteur", Griot.moteur).apply()
            }
            ActionFeuille(Icones.CORBEILLE, "Effacer l'historique et les cookies", danger = true) { effacer = true }
        }
    }
    if (effacer) DialogueAppli("Tout effacer ?", "L'historique, les cookies et les données des sites (vous serez déconnecté des sites). Les favoris restent.", fermer = { effacer = false }) {
        BoutonTexteAppli("Annuler", style = 't') { effacer = false }
        BoutonTexteAppli("Effacer") {
            effacer = false
            n.effacerTout()
            fermer()
        }
    }
}

// ——— Trouver dans la page ———

@Composable
private fun TrouverDansLaPage(o: Onglet, fermer: () -> Unit) {
    var texte by remember { mutableStateOf("") }
    var resultat by remember { mutableStateOf(0 to 0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        o.web.setFindListener { actif, total, _ -> resultat = (if (total == 0) 0 else actif + 1) to total }
    }
    LaunchedEffect(texte) { o.web.findAllAsync(texte) }
    BackHandler {
        o.web.clearMatches()
        fermer()
    }
    Row(Modifier.fillMaxWidth().background(NUIT).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // Sur la barre de nuit, la sélection prend l'or du curseur (l'accent de Griot ne s'y verrait pas).
        CompositionLocalProvider(LocalTextSelectionColors provides selectionTexte(OR)) {
            BasicTextField(
                texte, { texte = it }, singleLine = true,
                textStyle = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = BLANC), cursorBrush = SolidColor(OR),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focus).semantics { contentDescription = "Mot à trouver" },
                decorationBox = { f -> Box { if (texte.isEmpty()) BasicText("Trouver dans la page", style = TextStyle(fontFamily = Polices.corps, fontSize = 16.sp, color = Color(0x80FFFFFF))); f() } },
            )
        }
        if (texte.isNotEmpty()) BasicText("${resultat.first}/${resultat.second}", style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = Color(0xB3FFFFFF)))
        Bouton("M6 15l6-6l6 6", "Précédent", taille = 44) { o.web.findNext(false) }
        Bouton("M6 9l6 6l6-6", "Suivant", taille = 44) { o.web.findNext(true) }
        Bouton("M6 6l12 12 M18 6L6 18", "Fermer", taille = 44) { o.web.clearMatches(); fermer() }
    }
}

// ——— Historique, favoris ———

@Composable
private fun ListePages(titre: String, carnet: Carnet, historique: Boolean, n: Navigateur, fermer: () -> Unit) {
    val a = LocalIdentite.current
    var maj by remember { mutableIntStateOf(0) }
    val pages by produceState(emptyList<Page>(), maj, n.version) { value = withContext(Dispatchers.IO) { if (historique) carnet.historique() else carnet.favoris() } }
    var choisie by remember { mutableStateOf<Page?>(null) }
    BackHandler(onBack = fermer)
    Box(Modifier.fillMaxSize()) {
        EcranAppli {
            Tete(titre, retour = fermer, petit = true)
            if (pages.isEmpty()) BasicText(
                if (historique) "Rien pour l'instant. L'historique reste sur ce téléphone." else "Pas encore de favori : ⋮ › Ajouter aux favoris, sur une page.",
                modifier = Modifier.padding(20.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 15.sp, color = a.encre2),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                val parJour = if (historique) pages.groupBy { Instant.ofEpochMilli(it.quand).atZone(ZoneId.systemDefault()).toLocalDate() } else mapOf(null to pages)
                parJour.forEach { (jour, l) ->
                    if (jour != null) item(key = "j$jour") {
                        Rub(
                            when (jour) {
                                LocalDate.now() -> "Aujourd'hui"
                                LocalDate.now().minusDays(1) -> "Hier"
                                else -> jour.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).replaceFirstChar { it.uppercase() }
                            },
                        )
                    }
                    items(l, key = { it.url }) { p ->
                        val heure = Instant.ofEpochMilli(p.quand).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                        LigneAppli(
                            p.titre.ifBlank { Griot.domaine(p.url).first },
                            Griot.domaine(p.url).first + if (historique) " · $heure" else "",
                            debut = {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(a.voile), contentAlignment = Alignment.Center) {
                                    BasicText(Griot.domaine(p.url).first.take(1).uppercase(), style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = a.encre))
                                }
                            },
                            surAppuiLong = { choisie = p },
                        ) {
                            Griot.courant?.web?.loadUrl(p.url)
                            fermer()
                        }
                    }
                }
            }
        }
    }
    choisie?.let { p ->
        FeuilleAppli(fermer = { choisie = null }, titre = p.titre.ifBlank { p.url }) {
            ActionFeuille(Icones.PLUS, "Ouvrir dans un nouvel onglet") {
                n.nouvelOnglet().web.loadUrl(p.url)
                choisie = null
                fermer()
            }
            ActionFeuille(Icones.CORBEILLE, if (historique) "Retirer de l'historique" else "Retirer des favoris", danger = true) {
                if (historique) carnet.effacerPage(p.url) else carnet.basculerFavori(p.url, p.titre)
                choisie = null
                maj++
            }
        }
    }
}
