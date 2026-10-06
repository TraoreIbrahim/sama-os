package africa.samaos.griot

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import africa.samaos.bouclier.Bouclier
import africa.samaos.bouclier.Verdict
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URL
import java.util.Collections

/** Un onglet : sa page (le WebView du système) et ce que la barre en montre. */
class Onglet(val id: Long, val web: WebView) {
    var titre by mutableStateOf("Nouvel onglet")
    var url by mutableStateOf<String?>(null)
    var progres by mutableIntStateOf(100)
    var peutReculer by mutableStateOf(false)
    var peutAvancer by mutableStateOf(false)
    var erreur by mutableStateOf<String?>(null)
    var imagesEvitees by mutableIntStateOf(0)
    /** La page pour laquelle la personne a demandé toutes les images. */
    @Volatile var toutCharger: String? = null
    var apercu: Bitmap? = null
    /** Les images que la personne a voulu voir malgré le mode léger. */
    val chargees: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    /** Les images remplacées sur cette page (chacune comptée une fois). */
    val evitees: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    /** Le bouclier : la page imite un service de mobile money et demande le code secret. */
    var danger by mutableStateOf<Verdict?>(null)
    /** Une adresse qui se réclame d'un opérateur reste cachée le temps que le bouclier lise la page. */
    var voile by mutableStateOf(false)

    fun capturer() {
        if (web.width == 0 || web.height == 0) return
        apercu = try {
            Bitmap.createBitmap(web.width / 3, web.height / 3, Bitmap.Config.RGB_565).also { b ->
                val c = Canvas(b)
                c.scale(1 / 3f, 1 / 3f)
                web.draw(c)
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Ce que l'interface doit savoir faire pour les pages (ouvrir un onglet, télécharger, choisir un fichier…). */
interface Hote {
    fun nouvelOnglet(): Onglet
    fun telecharger(url: String, agent: String, disposition: String?, mime: String?, taille: Long)
    fun choisirFichier(rappel: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun pleinEcran(vue: View?)
    fun visite(o: Onglet)
    fun dire(texte: String)
}

object Griot {
    val onglets = mutableStateListOf<Onglet>()
    var courant by mutableStateOf<Onglet?>(null)
    var leger by mutableStateOf(false)
    var ordinateur by mutableStateOf(false)
    private var suivant = 1L

    /** Les moteurs de recherche proposés ; DuckDuckGo d'abord : pas de compte, pas de profil publicitaire. */
    val moteurs = listOf(
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Google" to "https://www.google.com/search?q=",
        "Qwant" to "https://www.qwant.com/?q=",
        "Wikipédia" to "https://fr.wikipedia.org/w/index.php?search=",
    )
    var moteur by mutableStateOf(0)
    /** Les sites que la personne a dit sûrs après une alerte du bouclier (« Signaler une erreur »). */
    val surs: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** Une adresse tapée, ou une recherche. */
    fun adresse(texte: String): String {
        val t = texte.trim()
        val ressembleAUneAdresse = !t.contains(' ') && (t.startsWith("http://") || t.startsWith("https://") || Regex("^[\\w.-]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$").matches(t) || t.startsWith("localhost"))
        return when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            ressembleAUneAdresse -> if (t.startsWith("localhost")) "http://$t" else "https://$t"
            else -> moteurs[moteur].second + Uri.encode(t)
        }
    }

    /**
     * Une position venue d'une autre appli (geo:5.35,-4.02 ou geo:0,0?q=Adjamé) : la carte d'OpenStreetMap,
     * libre et sans compte.
     */
    fun carte(geo: String): String? {
        val u = Uri.parse(geo.replace("geo:", "geo://"))
        val q = u.getQueryParameter("q")
        val coords = geo.removePrefix("geo:").substringBefore('?').split(',')
        val lat = coords.getOrNull(0)?.toDoubleOrNull()
        val lon = coords.getOrNull(1)?.toDoubleOrNull()
        // « q » peut porter des coordonnées avec un nom : geo:0,0?q=5.35,-4.02(Marché)
        val qCoords = q?.substringBefore('(')?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 2 }
        return when {
            qCoords != null -> "https://www.openstreetmap.org/?mlat=${qCoords[0]}&mlon=${qCoords[1]}#map=17/${qCoords[0]}/${qCoords[1]}"
            !q.isNullOrBlank() -> "https://www.openstreetmap.org/search?query=" + Uri.encode(q)
            lat != null && lon != null && !(lat == 0.0 && lon == 0.0) -> "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon"
            else -> null
        }
    }

    /**
     * Le domaine tel qu'il faut le lire. Un domaine écrit avec des lettres d'autres alphabets peut imiter
     * un vrai (« оrange.ci » avec un o cyrillique) : on le montre alors sous sa forme réelle (xn--…).
     */
    fun domaine(url: String?): Pair<String, Boolean> {
        val h = url?.let { Uri.parse(it).host } ?: return "" to false
        val ascii = try {
            IDN.toASCII(h)
        } catch (_: Exception) {
            h
        }
        val trompeur = ascii.split('.').any { it.startsWith("xn--") }
        return (if (trompeur) ascii else h.removePrefix("www.")) to trompeur
    }

    fun creer(c: Context, hote: Hote): Onglet {
        val o = Onglet(suivant++, WebView(c))
        configurer(o, hote)
        onglets += o
        return o
    }

    fun fermer(o: Onglet) {
        onglets.remove(o)
        o.web.stopLoading()
        o.web.destroy()
        if (courant == o) courant = onglets.lastOrNull()
    }

    private val EXTENSIONS_IMAGES = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "heic")

    /** L'image de remplacement du mode léger : légère, et elle dit quoi faire. */
    private val ALLEGEE = """<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180" viewBox="0 0 320 180" preserveAspectRatio="xMidYMid slice"><rect width="320" height="180" fill="#E9E4DA"/><rect x="88" y="74" width="144" height="32" rx="16" fill="#1E2740" fill-opacity="0.82"/><text x="160" y="95" font-family="sans-serif" font-size="12" font-weight="600" fill="#F2C879" text-anchor="middle">Image allégée · toucher</text></svg>""".toByteArray()

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurer(o: Onglet, hote: Hote) {
        val w = o.web
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
        }
        agent(o)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, false)
        w.addJavascriptInterface(object {
            @JavascriptInterface
            fun charger(url: String) {
                o.chargees += url
            }

            /** Un champ de code est apparu après le chargement (pages qui se construisent en direct) : on relit. */
            @JavascriptInterface
            fun secret() {
                w.post { w.url?.let { analyser(w.context, o, w, it) } }
            }
        }, "SamaGriot")
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                if (u.scheme == "http" || u.scheme == "https") return false
                // tel:, mailto:, sms:, intent:… : l'appli qui sait faire, jamais sans un geste de la personne.
                if (!r.hasGesture() && u.scheme != "intent") return true
                return ouvrirAilleurs(v.context, u.toString(), hote)
            }

            override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                o.url = url
                o.erreur = null
                o.imagesEvitees = 0
                o.evitees.clear()
                o.chargees.clear()
                o.danger = null
                val h = Uri.parse(url).host.orEmpty()
                o.voile = h !in surs && Bouclier.hoteSuspect(h) && Bouclier.actif(v.context, Bouclier.Garde.LIENS)
            }

            override fun onPageFinished(v: WebView, url: String) {
                o.url = url
                o.peutReculer = v.canGoBack()
                o.peutAvancer = v.canGoForward()
                if (leger) v.evaluateJavascript(TOUCHER_POUR_CHARGER, null)
                analyser(v.context, o, v, url)
                hote.visite(o)
            }

            override fun doUpdateVisitedHistory(v: WebView, url: String, recharge: Boolean) {
                o.url = url
                o.peutReculer = v.canGoBack()
                o.peutAvancer = v.canGoForward()
            }

            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) o.voile = false
                if (r.isForMainFrame) o.erreur = when (e.errorCode) {
                    ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_TIMEOUT -> "Pas de connexion, ou le site ne répond pas."
                    else -> e.description?.toString() ?: "La page ne s'est pas ouverte."
                }
            }

            override fun onReceivedSslError(v: WebView, h: android.webkit.SslErrorHandler, e: android.net.http.SslError) {
                // Un certificat douteux : on n'ouvre pas, et on dit pourquoi (pas de « continuer quand même »).
                h.cancel()
                o.erreur = "Ce site ne prouve pas qui il est. Griot ne l'ouvre pas : quelqu'un pourrait lire ce que vous y tapez."
            }

            override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
                o.erreur = "La page a trop demandé au téléphone et s'est fermée."
                return true
            }

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (!leger || r.isForMainFrame || (o.toutCharger != null && o.toutCharger == o.url)) return null
                val u = r.url.toString()
                // Une image que la personne a touchée : on va la chercher nous-mêmes, sans le marqueur.
                if (u.contains("sama_charger=1")) return chercher(u.replace(Regex("[?&]sama_charger=1"), ""), r)
                val ext = r.url.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
                val image = ext in EXTENSIONS_IMAGES || r.requestHeaders["Accept"]?.startsWith("image/") == true
                // L'icône du site reste : elle est minuscule et sert à reconnaître l'onglet.
                if (!image || u in o.chargees || r.url.lastPathSegment == "favicon.ico") return null
                if (o.evitees.add(u)) o.imagesEvitees = o.evitees.size
                return WebResourceResponse("image/svg+xml", "utf-8", ALLEGEE.inputStream())
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                o.progres = p
            }

            override fun onReceivedTitle(v: WebView, t: String?) {
                o.titre = t?.takeIf { it.isNotBlank() } ?: domaine(o.url).first
            }

            override fun onCreateWindow(v: WebView, dialogue: Boolean, geste: Boolean, msg: Message): Boolean {
                if (!geste) return false
                val n = hote.nouvelOnglet()
                (msg.obj as WebView.WebViewTransport).webView = n.web
                msg.sendToTarget()
                return true
            }

            override fun onCloseWindow(v: WebView) {
                onglets.firstOrNull { it.web == v }?.let { fermer(it) }
            }

            override fun onShowFileChooser(v: WebView, rappel: ValueCallback<Array<Uri>>, p: FileChooserParams) = hote.choisirFichier(rappel, p)

            override fun onShowCustomView(vue: View, rappel: CustomViewCallback) = hote.pleinEcran(vue)

            override fun onHideCustomView() = hote.pleinEcran(null)

            override fun onGeolocationPermissionsShowPrompt(origine: String, rappel: android.webkit.GeolocationPermissions.Callback) {
                // La position ne part pas sans qu'on le décide ; Griot ne la donne pas aux sites pour l'instant.
                rappel.invoke(origine, false, false)
                hote.dire("${domaine(origine).first} voulait savoir où vous êtes : Griot ne l'a pas dit.")
            }

            override fun onPermissionRequest(r: android.webkit.PermissionRequest) {
                r.deny()
                hote.dire("${domaine(r.origin.toString()).first} voulait la caméra ou le micro : refusé.")
            }
        }
        w.setDownloadListener { url, agent, disposition, mime, taille -> hote.telecharger(url, agent, disposition, mime, taille) }
    }

    fun agent(o: Onglet) {
        val s = o.web.settings
        s.userAgentString = null
        if (ordinateur) s.userAgentString = s.userAgentString.replace(Regex("\\(Linux; Android [^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
    }

    private fun chercher(url: String, r: WebResourceRequest): WebResourceResponse? = try {
        val cx = URL(url).openConnection() as HttpURLConnection
        r.requestHeaders.forEach { (k, v) -> cx.setRequestProperty(k, v) }
        CookieManager.getInstance().getCookie(url)?.let { cx.setRequestProperty("Cookie", it) }
        val type = cx.contentType?.substringBefore(';') ?: "image/*"
        WebResourceResponse(type, null, cx.inputStream)
    } catch (_: Exception) {
        null
    }

    /** Un lien vers une autre appli : appeler, écrire, une appli installée. */
    fun ouvrirAilleurs(c: Context, lien: String, hote: Hote): Boolean {
        return try {
            val i = if (lien.startsWith("intent:")) {
                // Un lien « intent: » ne choisit jamais lui-même l'appli interne à ouvrir.
                Intent.parseUri(lien, Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(lien)).addCategory(Intent.CATEGORY_BROWSABLE)
            }
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            if (lien.startsWith("intent:")) {
                // Le site a prévu une page de secours si l'appli manque.
                Intent.parseUri(lien, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")?.let { f ->
                    if (f.startsWith("http")) courant?.web?.loadUrl(f)
                    return true
                }
            }
            hote.dire("Aucune appli ne sait ouvrir ce lien.")
            true
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Le bouclier dans Griot (maquette i2-lien-piege) : la page demande-t-elle un code secret en imitant un
     * opérateur ? Tout se lit sur le téléphone ; rien n'est envoyé.
     */
    fun analyser(c: Context, o: Onglet, v: WebView, url: String) {
        val hote = Uri.parse(url).host
        if (hote == null || hote in surs || !Bouclier.actif(c, Bouclier.Garde.LIENS)) {
            o.voile = false
            return
        }
        v.evaluateJavascript(LIRE_LA_PAGE) { r ->
            val j = try {
                JSONObject(r)
            } catch (_: Exception) {
                null
            }
            val verdict = j?.let { Bouclier.page(hote, it.optString("t"), it.optBoolean("s")) }
            if (verdict != null && v.url == url && o.danger == null) {
                o.danger = verdict
                v.evaluateJavascript(GELER, null)
                Bouclier.noter(c, "lien", "Faux site " + (verdict.operateur?.nom?.let { if (it.first().lowercaseChar() in "aeiouy") "d'$it" else "de $it" } ?: "de mobile money"), hote)
            }
            o.voile = false
        }
    }

    /** Le champ de code, le titre et le texte qui l'entoure ; puis on guette un champ ajouté plus tard. */
    private const val LIRE_LA_PAGE = """
        (function(){
          var Q = 'input[type=password], input[autocomplete="one-time-code"], input[name*="pin" i], input[id*="pin" i], input[name*="code" i], input[id*="code" i]';
          var champ = document.querySelector(Q);
          var autour = champ ? (champ.closest('form') || champ.parentElement.parentElement || champ.parentElement) : null;
          var t = (document.title || '') + '\n' + (autour ? autour.innerText : '') + '\n' + (document.body ? document.body.innerText.slice(0, 1500) : '');
          if (!champ && !window.__samaGuet && window.MutationObserver && document.body) {
            window.__samaGuet = new MutationObserver(function(){
              if (document.querySelector(Q)) { window.__samaGuet.disconnect(); SamaGriot.secret(); }
            });
            window.__samaGuet.observe(document.body, {childList: true, subtree: true});
          }
          return {s: !!champ, t: t.slice(0, 4000)};
        })()
    """

    /** Une page dangereuse : plus rien ne se tape ni ne s'envoie. */
    private const val GELER = """
        (function(){
          if (document.activeElement) document.activeElement.blur();
          document.querySelectorAll('input, textarea, select, button').forEach(function(e){ e.disabled = true; });
          document.querySelectorAll('form').forEach(function(f){ f.onsubmit = function(){ return false; }; });
        })();
    """

    /** Mode léger : toucher une image allégée la charge, elle seule. */
    private const val TOUCHER_POUR_CHARGER = """
        (function(){
          if (window.__samaLeger) return; window.__samaLeger = 1;
          document.addEventListener('click', function(e){
            var t = e.target;
            if (!t || t.tagName !== 'IMG' || t.dataset.samaLeger === '0') return;
            // Seulement les images allégées (l'image de remplacement fait 320 × 180).
            if (!(t.naturalWidth === 320 && t.naturalHeight === 180)) return;
            var u = t.currentSrc || t.src;
            if (!u || u.indexOf('http') !== 0) return;
            e.preventDefault(); e.stopPropagation();
            SamaGriot.charger(u);
            t.removeAttribute('srcset'); t.dataset.samaLeger = '0';
            t.src = u + (u.indexOf('?') < 0 ? '?' : '&') + 'sama_charger=1';
          }, true);
        })();
    """
}
