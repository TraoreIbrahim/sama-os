package africa.samaos.clavier

import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import africa.samaos.banco.Aube
import africa.samaos.banco.CLE_PLEIN_SOLEIL
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Nuit
import africa.samaos.banco.PleinSoleil
import org.json.JSONArray
import org.json.JSONObject

/** Les réglages du clavier, écrits par les Réglages de Sama (réglages globaux partagés). */
class ReglagesClavier(val langues: List<Langue>, val propositions: Boolean, val correction: Boolean, val vibration: Boolean, val effacer: Long) {
    companion object {
        const val CLE = "sama_clavier"
        const val CLE_MOTS = "sama_clavier_mots"

        fun lire(c: Context): ReglagesClavier {
            val o = try {
                JSONObject(Settings.Global.getString(c.contentResolver, CLE) ?: "{}")
            } catch (_: Exception) {
                JSONObject()
            }
            // Par défaut, le français et le julakan.
            val l = o.optJSONArray("langues")?.let { a -> (0 until a.length()).mapNotNull { Langue.de(a.getString(it)) } }.orEmpty().ifEmpty { listOf(Langue.FR, Langue.DY) }
            return ReglagesClavier(l, o.optBoolean("propositions", true), o.optBoolean("correction", true), o.optBoolean("vibration", true), o.optLong("effacer", 0L))
        }

        fun ecrire(c: Context, langues: List<Langue>, propositions: Boolean, correction: Boolean, vibration: Boolean, effacer: Long) {
            val o = JSONObject().put("langues", JSONArray(langues.map { it.code })).put("propositions", propositions).put("correction", correction)
                .put("vibration", vibration).put("effacer", effacer)
            try {
                Settings.Global.putString(c.contentResolver, CLE, o.toString())
            } catch (_: Exception) {
            }
        }
    }
}

/** Ce que montre le clavier : la page, la langue, les majuscules, le bandeau. */
class Etat {
    var page by mutableStateOf(Page.LETTRES)
    var langue by mutableStateOf(Langue.FR)
    var langues by mutableStateOf(listOf(Langue.FR))

    /** 0 : minuscules ; 1 : une majuscule ; 2 : majuscules verrouillées. */
    var maj by mutableIntStateOf(0)

    /** Un champ de mot de passe ou de code : rien n'est retenu, rien n'est proposé. */
    var secret by mutableStateOf(false)
    var virgule by mutableStateOf(",")
    var action by mutableIntStateOf(EditorInfo.IME_ACTION_UNSPECIFIED)
    var propositions by mutableStateOf<List<String>>(emptyList())

    /** L'appui long : la touche, ses variantes, et où les montrer. */
    var variantes by mutableStateOf<Triple<Touche, List<String>, Rect>?>(null)
    var categorie by mutableIntStateOf(-1)
    var dictee by mutableStateOf<String?>(null)

    /** L'espace maintenu : le clavier devient un pavé qui déplace le curseur. */
    var curseur by mutableStateOf(false)

    /** Un champ de plusieurs lignes : le pavé peut aussi monter et descendre. */
    var multiligne by mutableStateOf(false)
}

/**
 * Le clavier de Sama (maquettes l3-clavier, l1-emoji, l1-dictee). Il n'a pas accès à Internet : ce qui est tapé
 * reste sur le téléphone. Il ne retient et ne propose rien dans un champ de mot de passe ou de code.
 */
class Clavier : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val cycle = LifecycleRegistry(this)
    private val memoire = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = cycle
    override val savedStateRegistry: SavedStateRegistry get() = memoire.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    val etat = Etat()
    private lateinit var mots: Mots
    private var reglages = ReglagesClavier(listOf(Langue.FR), true, true, true, 0)
    private var apprendre = true
    private var vue: View? = null
    private var majTouchee = 0L

    /** La dernière correction automatique : un « effacer » juste après la défait. */
    private var corrige: Pair<String, String>? = null

    override fun onCreate() {
        super.onCreate()
        memoire.performRestore(null)
        cycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        mots = Mots(this).also { it.charger() }
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let { d ->
            d.setViewTreeLifecycleOwner(this)
            d.setViewTreeSavedStateRegistryOwner(this)
            d.setViewTreeViewModelStoreOwner(this)
        }
        return ComposeView(this).also { v ->
            v.setViewTreeLifecycleOwner(this)
            v.setViewTreeSavedStateRegistryOwner(this)
            v.setViewTreeViewModelStoreOwner(this)
            v.setContent {
                val nuit = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val soleil = Settings.Secure.getInt(contentResolver, CLE_PLEIN_SOLEIL, 0) == 2
                CompositionLocalProvider(LocalBanco provides if (soleil) PleinSoleil else if (nuit) Nuit else Aube) {
                    VueClavier(etat, this)
                }
            }
            vue = v
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        cycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        reglages = ReglagesClavier.lire(this)
        // Les Réglages ont demandé d'oublier les mots appris.
        val p = getSharedPreferences("clavier", MODE_PRIVATE)
        if (reglages.effacer > p.getLong("efface", 0L)) {
            mots.effacer()
            p.edit().putLong("efface", reglages.effacer).apply()
            noterNombre()
        }
        etat.langues = reglages.langues
        // La dernière langue choisie, gardée d'une fois sur l'autre.
        etat.langue = Langue.de(p.getString("langue", "").orEmpty())?.takeIf { it in etat.langues } ?: etat.langues.first()
        val classe = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        etat.secret = when (classe) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        // Rien n'est appris dans un champ secret, ni quand l'appli le demande (navigation privée…).
        apprendre = !etat.secret && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0 &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0 && classe == InputType.TYPE_CLASS_TEXT &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS && variation != InputType.TYPE_TEXT_VARIATION_URI
        etat.virgule = when (variation) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> "@"
            InputType.TYPE_TEXT_VARIATION_URI -> "/"
            else -> ","
        }
        etat.page = when (classe) {
            InputType.TYPE_CLASS_PHONE -> Page.CHIFFRES
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Page.NOMBRES
            else -> Page.LETTRES
        }
        etat.multiligne = classe == InputType.TYPE_CLASS_TEXT && (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        etat.curseur = false
        etat.action = if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) EditorInfo.IME_ACTION_NONE else info.imeOptions and EditorInfo.IME_MASK_ACTION
        etat.maj = 0
        etat.variantes = null
        etat.dictee = null
        corrige = null
        majAuto()
        proposer()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        mots.garder()
        noterNombre()
        etat.variantes = null
    }

    override fun onDestroy() {
        cycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        mots.garder()
        super.onDestroy()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        proposer()
    }

    /** Le nombre de mots appris, pour les Réglages (jamais les mots eux-mêmes). */
    private fun noterNombre() {
        try {
            Settings.Global.putInt(contentResolver, ReglagesClavier.CLE_MOTS, mots.nombre())
        } catch (_: Exception) {
        }
    }

    fun vibrer() {
        if (reglages.vibration) vue?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // ——— Écrire ———

    private fun motEnCours(): String {
        val avant = currentInputConnection?.getTextBeforeCursor(48, 0)?.toString() ?: return ""
        return Regex("[\\p{L}\\p{M}'’-]+$").find(avant)?.value.orEmpty()
    }

    private fun proposer() {
        etat.propositions = if (etat.secret || !reglages.propositions || etat.page != Page.LETTRES) emptyList() else mots.propositions(motEnCours(), etat.langue)
    }

    /** Une majuscule en début de phrase, si le champ le demande. */
    private fun majAuto() {
        if (etat.maj == 2 || etat.page != Page.LETTRES) return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        etat.maj = if (!etat.secret && info.inputType != 0 && ic.getCursorCapsMode(info.inputType) != 0) 1 else 0
    }

    fun ecrire(t: String) {
        val ic = currentInputConnection ?: return
        val texte = if (etat.maj > 0 && etat.page == Page.LETTRES && t.length == 1) t.uppercase() else t
        corrige = null
        if (t.length == 1 && !t[0].isLetterOrDigit() && t != "'" && t != "’" && t != "-") finirMot(ic)
        ic.commitText(texte, 1)
        if (etat.maj == 1) etat.maj = 0
        if (t.length == 1 && t in ".!?") majAuto()
        proposer()
    }

    /** Un mot vient d'être fini (espace, ponctuation) : la correction prudente, puis l'apprendre. */
    private fun finirMot(ic: android.view.inputmethod.InputConnection) {
        val mot = motEnCours()
        if (mot.isEmpty()) return
        val juste = if (reglages.correction && apprendre) mots.corriger(mot, etat.langue) else null
        if (juste != null) {
            ic.deleteSurroundingText(mot.length, 0)
            ic.commitText(juste, 1)
            corrige = mot to juste
        }
        if (apprendre) mots.apprendre(juste ?: mot)
    }

    fun espace() {
        val ic = currentInputConnection ?: return
        // Deux espaces de suite : un point (« bonjour  » devient « bonjour. »).
        val avant = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        if (avant.length == 2 && avant[1] == ' ' && avant[0].isLetterOrDigit() && !etat.secret) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            majAuto()
            proposer()
            return
        }
        finirMot(ic)
        ic.commitText(" ", 1)
        majAuto()
        proposer()
    }

    fun effacer() {
        val ic = currentInputConnection ?: return
        // Juste après une correction automatique : « effacer » rend le mot tapé.
        corrige?.let { (avant, apres) ->
            if (ic.getTextBeforeCursor(apres.length + 1, 0)?.toString() == "$apres ") {
                ic.deleteSurroundingText(apres.length + 1, 0)
                ic.commitText("$avant ", 1)
                corrige = null
                return
            }
        }
        corrige = null
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        majAuto()
        proposer()
    }

    fun entree() {
        val ic = currentInputConnection ?: return
        finirMot(ic)
        corrige = null
        when (etat.action) {
            EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            else -> ic.performEditorAction(etat.action)
        }
        majAuto()
        proposer()
    }

    fun majuscule() {
        val maintenant = System.currentTimeMillis()
        etat.maj = when {
            etat.maj == 2 -> 0
            maintenant - majTouchee < 350 -> 2
            etat.maj == 1 -> 0
            else -> 1
        }
        majTouchee = maintenant
    }

    fun choisirProposition(p: String) {
        val ic = currentInputConnection ?: return
        val mot = motEnCours()
        ic.deleteSurroundingText(mot.length, 0)
        ic.commitText("$p ", 1)
        if (apprendre) mots.apprendre(p)
        corrige = null
        majAuto()
        proposer()
    }

    fun emoji(e: String) {
        currentInputConnection?.commitText(e, 1)
        Emoji.utiliser(this, e)
    }

    fun changerLangue(l: Langue) {
        etat.langue = l
        getSharedPreferences("clavier", MODE_PRIVATE).edit().putString("langue", l.code).apply()
        etat.page = Page.LETTRES
        proposer()
    }

    /** La touche de langue : la suivante (l'appui long les montre toutes). */
    fun langueSuivante() {
        val l = etat.langues
        if (l.size > 1) changerLangue(l[(l.indexOf(etat.langue) + 1) % l.size])
    }

    /**
     * Le pavé de l'espace : un caractère à gauche ou à droite, une ligne en haut ou en bas (seulement sur plusieurs
     * lignes). Le curseur est posé directement dans le texte : des flèches feraient sortir du champ dans certaines applis.
     */
    fun deplacer(dx: Int, dy: Int) {
        val ic = currentInputConnection ?: return
        val ex = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest().apply { hintMaxChars = 200_000 }, 0)
        val texte = ex?.text?.toString()
        if (ex == null || texte == null) {
            // Le champ ne donne pas son texte : les flèches, de côté seulement.
            if (dx != 0) sendDownUpKeyEvents(if (dx < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
            return
        }
        var n = ex.selectionEnd.coerceIn(0, texte.length)
        if (dx != 0) {
            n = (n + dx).coerceIn(0, texte.length)
            // Ne jamais couper un emoji (ou une lettre faite de deux caractères) en deux.
            if (n in 1 until texte.length && Character.isLowSurrogate(texte[n]) && Character.isHighSurrogate(texte[n - 1])) n += if (dx > 0) 1 else -1
        }
        if (dy != 0) {
            if (!etat.multiligne) return
            val debut = texte.lastIndexOf('\n', n - 1) + 1
            val colonne = n - debut
            n = if (dy < 0) {
                if (debut == 0) return
                val finPrecedente = debut - 1
                val debutPrecedente = texte.lastIndexOf('\n', finPrecedente - 1) + 1
                minOf(debutPrecedente + colonne, finPrecedente)
            } else {
                val fin = texte.indexOf('\n', n).takeIf { it >= 0 } ?: return
                val finSuivante = texte.indexOf('\n', fin + 1).takeIf { it >= 0 } ?: texte.length
                minOf(fin + 1 + colonne, finSuivante)
            }
        }
        if (n == ex.selectionEnd && ex.selectionStart == ex.selectionEnd) return
        ic.setSelection(ex.startOffset + n, ex.startOffset + n)
        vue?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    fun page(p: Page) {
        etat.page = p
        etat.variantes = null
        if (p == Page.LETTRES) majAuto()
        proposer()
    }

    fun reglages() {
        try {
            startActivity(android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
