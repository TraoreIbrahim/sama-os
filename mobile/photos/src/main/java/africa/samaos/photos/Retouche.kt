package africa.samaos.photos

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.Polices
import africa.samaos.banco.appli.BoutonAppli
import africa.samaos.banco.appli.BoutonTexteAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.PuceFiltre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class Filtre(val nom: String) { ORIGINAL("Original"), ECLAT("Éclat"), CHAUD("Chaud"), FRAIS("Frais"), DOUX("Doux"), NOIR_BLANC("Noir et blanc") }

/** Un trait d'annotation, en coordonnées 0..1 du cadre de la photo. */
class Trait(val couleur: Int, val epaisseur: Float, val points: MutableList<Offset> = mutableListOf())

/** Ce que la retouche change ; tout est appliqué ensemble, à l'aperçu comme à l'enregistrement. */
class EtatRetouche {
    var quart by mutableIntStateOf(0)
    var angle by mutableFloatStateOf(0f)
    var cadre by mutableStateOf(Rect(0f, 0f, 1f, 1f))
    var ratio by mutableStateOf<Float?>(null)
    var lumiere by mutableFloatStateOf(0f)
    var contraste by mutableFloatStateOf(0f)
    var saturation by mutableFloatStateOf(0f)
    var filtre by mutableStateOf(Filtre.ORIGINAL)
    val traits = mutableStateListOf<Trait>()
    var version by mutableIntStateOf(0)

    fun cadreW(b: Bitmap) = if (quart % 2 == 0) b.width else b.height
    fun cadreH(b: Bitmap) = if (quart % 2 == 0) b.height else b.width

    val change get() = quart != 0 || angle != 0f || cadre != Rect(0f, 0f, 1f, 1f) || lumiere != 0f || contraste != 0f || saturation != 0f || filtre != Filtre.ORIGINAL || traits.isNotEmpty()
}

fun matrice(lumiere: Float, contraste: Float, saturation: Float, filtre: Filtre): android.graphics.ColorMatrix {
    val m = android.graphics.ColorMatrix()
    when (filtre) {
        Filtre.ORIGINAL -> {}
        Filtre.ECLAT -> m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(1.3f) }).also { m.postConcat(contrasteDe(0.12f)) }
        Filtre.CHAUD -> m.postConcat(android.graphics.ColorMatrix().apply { setScale(1.08f, 1.0f, 0.88f, 1f) })
        Filtre.FRAIS -> m.postConcat(android.graphics.ColorMatrix().apply { setScale(0.92f, 1.0f, 1.08f, 1f) })
        Filtre.DOUX -> {
            m.postConcat(contrasteDe(-0.18f))
            m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0.85f) })
        }
        Filtre.NOIR_BLANC -> m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) }).also { m.postConcat(contrasteDe(0.1f)) }
    }
    if (saturation != 0f) m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(1f + saturation) })
    if (contraste != 0f) m.postConcat(contrasteDe(contraste * 0.6f))
    if (lumiere != 0f) {
        val d = lumiere * 70f
        m.postConcat(android.graphics.ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, d, 0f, 1f, 0f, 0f, d, 0f, 0f, 1f, 0f, d, 0f, 0f, 0f, 1f, 0f)))
    }
    return m
}

private fun contrasteDe(k: Float): android.graphics.ColorMatrix {
    val c = 1f + k
    val t = 128f * (1f - c)
    return android.graphics.ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
}

/** L'agrandissement qui remplit le cadre une fois l'image redressée (pas de coins vides). */
private fun couvrir(angle: Float, w: Float, h: Float): Float {
    val t = Math.toRadians(abs(angle).toDouble())
    val c = cos(t).toFloat()
    val s = sin(t).toFloat()
    return maxOf((w * c + h * s) / w, (w * s + h * c) / h)
}

/** Dessine la photo retouchée dans le repère du cadre (0..W, 0..H), recadrée ou entière. */
fun dessiner(canvas: android.graphics.Canvas, b: Bitmap, e: EtatRetouche, recadrer: Boolean) {
    val w = e.cadreW(b).toFloat()
    val h = e.cadreH(b).toFloat()
    canvas.save()
    if (recadrer) {
        // Le coin du cadre devient l'origine ; rien ne dépasse du cadre.
        canvas.translate(-e.cadre.left * w, -e.cadre.top * h)
        canvas.clipRect(e.cadre.left * w, e.cadre.top * h, e.cadre.right * w, e.cadre.bottom * h)
    } else {
        canvas.clipRect(0f, 0f, w, h)
    }
    canvas.save()
    canvas.translate(w / 2, h / 2)
    canvas.rotate(e.quart * 90f + e.angle)
    val s = couvrir(e.angle, w, h)
    canvas.scale(s, s)
    val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrice(e.lumiere, e.contraste, e.saturation, e.filtre)) }
    canvas.drawBitmap(b, -b.width / 2f, -b.height / 2f, p)
    canvas.restore()
    e.traits.forEach { t ->
        if (t.points.isEmpty()) return@forEach
        val chemin = Path()
        t.points.forEachIndexed { i, o -> if (i == 0) chemin.moveTo(o.x * w, o.y * h) else chemin.lineTo(o.x * w, o.y * h) }
        if (t.points.size == 1) chemin.lineTo(t.points[0].x * w + 0.1f, t.points[0].y * h)
        canvas.drawPath(chemin, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = t.couleur
            style = Paint.Style.STROKE
            strokeWidth = t.epaisseur * minOf(w, h)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
    }
    canvas.restore()
}

private enum class Outil(val nom: String, val icone: String) {
    RECADRER("Recadrer", "M7 3v14h14 M3 7h14v14"),
    LUMIERE("Lumière", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4"),
    FILTRES("Filtres", "M4 20L15 9 M14 4v2 M19 9h2 M17.5 5.5l1.5-1.5 M19 12l1.5 1.5 M11 5.5L9.5 4"),
    ANNOTER("Annoter", Icones.CRAYON),
}

private val COULEURS = listOf(Color.White, Color(0xFF1F1C18), Color(0xFFB5532F), Color(0xFFE2A62B), Color(0xFF3D5A99))

/** La retouche (maquette l2-pho-retouche) : l'originale n'est jamais touchée, on enregistre une copie. */
@Composable
fun Retouche(m: Media) {
    val c = LocalContext.current
    val x = LocalActions.current
    val a = LocalIdentite.current
    val image by produceState<Bitmap?>(null, m.id) { value = withContext(Dispatchers.IO) { Galerie.image(c, m.uri, 2400) } }
    val e = remember { EtatRetouche() }
    var outil by remember { mutableStateOf(Outil.RECADRER) }
    var couleur by remember { mutableStateOf(COULEURS[2]) }
    var enCours by remember { mutableStateOf(false) }
    val portee = rememberCoroutineScope()
    val clair = Color(0xFFF4EEE4)
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClickLabel = "Annuler la retouche", role = Role.Button, onClick = x.retour).padding(horizontal = 12.dp, vertical = 12.dp)) {
                BasicText("Annuler", style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = clair))
            }
            Spacer(Modifier.weight(1f))
            BoutonTexteAppli(if (enCours) "Un instant…" else "Enregistrer une copie", actif = e.change && !enCours) {
                enCours = true
                portee.launch {
                    val ok = withContext(Dispatchers.IO) {
                        val grande = Galerie.image(c, m.uri, 4000) ?: return@withContext null
                        val w = e.cadreW(grande) * e.cadre.width
                        val h = e.cadreH(grande) * e.cadre.height
                        val sortie = Bitmap.createBitmap(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        dessiner(android.graphics.Canvas(sortie), grande, e, recadrer = true)
                        Galerie.enregistrerCopie(c, m, sortie)
                    }
                    enCours = false
                    x.changer()
                    x.retour()
                    x.dire(if (ok != null) "Copie enregistrée dans « ${Galerie.nomAlbum(m.album, m.dossier)} »" else "La copie n'a pas pu être enregistrée", null)
                }
            }
        }
        val b = image
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (b == null) return@BoxWithConstraints
            val densite = LocalDensity.current
            val boiteW = with(densite) { maxWidth.toPx() }
            val boiteH = with(densite) { maxHeight.toPx() }
            val w = e.cadreW(b).toFloat()
            val h = e.cadreH(b).toFloat()
            val recadre = outil != Outil.RECADRER
            val contenuW = if (recadre) w * e.cadre.width else w
            val contenuH = if (recadre) h * e.cadre.height else h
            val k = minOf(boiteW / contenuW, boiteH / contenuH)
            val origine = Offset((boiteW - contenuW * k) / 2, (boiteH - contenuH * k) / 2)
            val poignee = with(densite) { 36.dp.toPx() }
            var prise by remember { mutableIntStateOf(-1) }
            val gestes = when (outil) {
                Outil.RECADRER -> Modifier.pointerInput(outil, b, e.ratio, e.quart) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val r = Rect(origine.x + e.cadre.left * w * k, origine.y + e.cadre.top * h * k, origine.x + e.cadre.right * w * k, origine.y + e.cadre.bottom * h * k)
                            val coins = listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight)
                            prise = coins.indexOfFirst { (it - p).getDistance() < poignee }.takeIf { it >= 0 } ?: if (r.contains(p)) 4 else -1
                        },
                        onDragEnd = { prise = -1 },
                    ) { ch, d ->
                        ch.consume()
                        e.cadre = deplacer(e.cadre, prise, d.x / (w * k), d.y / (h * k), e.ratio?.let { it * h / w })
                    }
                }
                Outil.ANNOTER -> Modifier.pointerInput(outil, b, couleur) {
                    fun versCadre(p: Offset) = Offset(((p.x - origine.x) / k) / w + e.cadre.left, ((p.y - origine.y) / k) / h + e.cadre.top)
                    detectDragGestures(
                        onDragStart = { p -> e.traits.add(Trait(couleur.toArgb(), 0.012f, mutableListOf(versCadre(p)))) },
                    ) { ch, _ ->
                        ch.consume()
                        e.traits.lastOrNull()?.points?.add(versCadre(ch.position))
                        e.version++
                    }
                }
                else -> Modifier
            }
            Canvas(Modifier.fillMaxSize().then(gestes).semantics { contentDescription = "Aperçu de la retouche" }) {
                e.version
                drawIntoCanvas { cv ->
                    val n = cv.nativeCanvas
                    n.save()
                    n.translate(origine.x, origine.y)
                    n.scale(k, k)
                    dessiner(n, b, e, recadrer = recadre)
                    n.restore()
                }
                if (!recadre) {
                    // Le cadre : l'extérieur assombri, les tiers, les quatre coins.
                    val r = Rect(origine.x + e.cadre.left * w * k, origine.y + e.cadre.top * h * k, origine.x + e.cadre.right * w * k, origine.y + e.cadre.bottom * h * k)
                    val voile = Color(0x8C000000)
                    val tout = Rect(origine, Size(w * k, h * k))
                    drawRect(voile, tout.topLeft, Size(tout.width, r.top - tout.top))
                    drawRect(voile, Offset(tout.left, r.bottom), Size(tout.width, tout.bottom - r.bottom))
                    drawRect(voile, Offset(tout.left, r.top), Size(r.left - tout.left, r.height))
                    drawRect(voile, Offset(r.right, r.top), Size(tout.right - r.right, r.height))
                    drawRect(Color.White, r.topLeft, r.size, style = Stroke(1.5f))
                    for (i in 1..2) {
                        drawLine(Color(0x99FFFFFF), Offset(r.left + r.width * i / 3, r.top), Offset(r.left + r.width * i / 3, r.bottom), 1f)
                        drawLine(Color(0x99FFFFFF), Offset(r.left, r.top + r.height * i / 3), Offset(r.right, r.top + r.height * i / 3), 1f)
                    }
                    val l = 24.dp.toPx()
                    val ep = 4.dp.toPx()
                    listOf(r.topLeft to Offset(1f, 1f), r.topRight to Offset(-1f, 1f), r.bottomLeft to Offset(1f, -1f), r.bottomRight to Offset(-1f, -1f)).forEach { (o, s) ->
                        drawLine(Color.White, o, o + Offset(l * s.x, 0f), ep)
                        drawLine(Color.White, o, o + Offset(0f, l * s.y), ep)
                    }
                }
            }
        }
        // Les réglages de l'outil choisi
        Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
            when (outil) {
                Outil.RECADRER -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf<Pair<String, Float?>>("Libre" to null, "1:1" to 1f, "4:3" to 4f / 3, "16:9" to 16f / 9, "9:16" to 9f / 16).forEach { (nom, r) ->
                            PuceFiltre(nom, e.ratio == r) {
                                e.ratio = r
                                b?.let { bm -> e.cadre = cadreAuFormat(r, e.cadreW(bm).toFloat(), e.cadreH(bm).toFloat()) }
                            }
                        }
                        BoutonAppli("M4 12a8 8 0 1 1 2.3 5.6 M4 18v-5h5", "Tourner d'un quart") {
                            e.quart = (e.quart + 1) % 4
                            e.traits.clear()
                            b?.let { bm -> e.cadre = cadreAuFormat(e.ratio, e.cadreW(bm).toFloat(), e.cadreH(bm).toFloat()) }
                        }
                    }
                    Redresser(e)
                }
                Outil.LUMIERE -> Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Reglette("Luminosité", e.lumiere) { e.lumiere = it }
                    Reglette("Contraste", e.contraste) { e.contraste = it }
                    Reglette("Couleurs", e.saturation) { e.saturation = it }
                }
                Outil.FILTRES -> {
                    val petite = remember(b) { b?.let { Bitmap.createScaledBitmap(it, 160, (160f * it.height / it.width).roundToInt().coerceAtLeast(1), true).asImageBitmap() } }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Filtre.entries.forEach { f ->
                            Column(
                                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = f.nom, role = Role.Button) { e.filtre = f },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                petite?.let {
                                    Image(
                                        it, null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).then(if (e.filtre == f) Modifier.border(2.dp, a.accent, RoundedCornerShape(12.dp)) else Modifier),
                                        contentScale = ContentScale.Crop,
                                        colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrice(0f, 0f, 0f, f).array)),
                                    )
                                }
                                BasicText(f.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (e.filtre == f) a.accent else Color(0xFFB3AA9C)))
                            }
                        }
                    }
                }
                Outil.ANNOTER -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    COULEURS.forEach { co ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(co)
                                .then(if (co == couleur) Modifier.border(3.dp, a.accent, CircleShape) else Modifier.border(1.dp, Color(0x66FFFFFF), CircleShape))
                                .clickable(onClickLabel = "Choisir cette couleur", role = Role.Button) { couleur = co },
                        )
                    }
                    BoutonAppli("M4 12a8 8 0 1 0 2.3-5.6 M4 4v4h4", "Effacer le dernier trait") { if (e.traits.isNotEmpty()) e.traits.removeAt(e.traits.lastIndex) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Outil.entries.forEach { o ->
                val ici = o == outil
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = o.nom, role = Role.Tab) { outil = o }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(56.dp, 36.dp).clip(RoundedCornerShape(18.dp)).then(if (ici) Modifier.background(a.voile) else Modifier), contentAlignment = Alignment.Center) {
                        IconeTrait(o.icone, 22.dp, if (ici) a.accent else Color(0xFFB3AA9C))
                    }
                    BasicText(o.nom, style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (ici) a.accent else Color(0xFFB3AA9C)))
                }
            }
        }
    }
}

/** Le plus grand cadre centré au format demandé (largeur / hauteur en pixels). */
private fun cadreAuFormat(r: Float?, w: Float, h: Float): Rect {
    if (r == null) return Rect(0f, 0f, 1f, 1f)
    val (cw, ch) = if (w / h > r) (r * h / w) to 1f else 1f to (w / (r * h))
    return Rect((1 - cw) / 2, (1 - ch) / 2, (1 + cw) / 2, (1 + ch) / 2)
}

/** Déplacer un coin (0 à 3) ou tout le cadre (4) ; `r` : le format voulu, en unités du cadre (largeur / hauteur). */
private fun deplacer(c: Rect, prise: Int, dx: Float, dy: Float, r: Float?): Rect {
    val mini = 0.12f
    if (prise == 4) {
        val nx = (c.left + dx).coerceIn(0f, 1f - c.width)
        val ny = (c.top + dy).coerceIn(0f, 1f - c.height)
        return Rect(nx, ny, nx + c.width, ny + c.height)
    }
    if (prise !in 0..3) return c
    var g = c.left
    var hh = c.top
    var d = c.right
    var b = c.bottom
    if (prise == 0 || prise == 2) g = (g + dx).coerceIn(0f, d - mini) else d = (d + dx).coerceIn(g + mini, 1f)
    if (prise == 0 || prise == 1) hh = (hh + dy).coerceIn(0f, b - mini) else b = (b + dy).coerceIn(hh + mini, 1f)
    if (r != null) {
        // On garde le format : la hauteur suit la largeur, sans sortir de l'image.
        val haut = (d - g) / r
        if (prise == 0 || prise == 1) hh = (b - haut).coerceAtLeast(0f) else b = (hh + haut).coerceAtMost(1f)
        val large = (b - hh) * r
        if (prise == 0 || prise == 2) g = d - large else d = g + large
    }
    return Rect(g, hh, d, b)
}

/** Redresser : une règle qu'on fait glisser, de −45° à +45°. */
@Composable
private fun Redresser(e: EtatRetouche) {
    val a = LocalIdentite.current
    val densite = LocalDensity.current
    val parDegre = with(densite) { 8.dp.toPx() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val v = e.angle.roundToInt()
        BasicText(
            if (v == 0) "0°" else (if (v < 0) "−" else "+") + abs(v) + "°",
            modifier = Modifier.clickable(onClickLabel = "Remettre droit") { e.angle = 0f },
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = a.accent),
        )
        Canvas(
            Modifier.width(300.dp).height(26.dp)
                .semantics { contentDescription = "Redresser" }
                .pointerInput(Unit) { detectHorizontalDragGestures { ch, dx -> ch.consume(); e.angle = (e.angle - dx / parDegre).coerceIn(-45f, 45f) } },
        ) {
            val milieu = size.width / 2
            for (deg in -45..45) {
                val px = milieu + (deg - e.angle) * parDegre
                if (px < 0 || px > size.width) continue
                val long = deg % 5 == 0
                drawLine(if (deg == 0) a.accent else Color(0x99FFFFFF), Offset(px, if (long) 4f else 9f), Offset(px, size.height - if (long) 4f else 9f), if (long) 2f else 1f)
            }
            drawLine(a.accent, Offset(milieu, 0f), Offset(milieu, size.height), 3f)
        }
    }
}

/** Une réglette de −100 à +100, zéro au milieu. */
@Composable
private fun Reglette(nom: String, valeur: Float, changer: (Float) -> Unit) {
    val a = LocalIdentite.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(nom, modifier = Modifier.width(92.dp), style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = Color(0xFFF4EEE4)))
        Canvas(
            Modifier.weight(1f).height(32.dp)
                .semantics { contentDescription = "$nom : ${(valeur * 100).roundToInt()}" }
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { p -> changer(((p.x / size.width) * 2 - 1).coerceIn(-1f, 1f)) }) { ch, _ ->
                        ch.consume()
                        changer(((ch.position.x / size.width) * 2 - 1).coerceIn(-1f, 1f))
                    }
                },
        ) {
            val y = size.height / 2
            drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(size.width, y), 4f)
            val milieu = size.width / 2
            val px = milieu + valeur * milieu
            drawLine(a.accent, Offset(milieu, y), Offset(px, y), 4f)
            drawCircle(Color(0x66FFFFFF), 3f, Offset(milieu, y))
            drawCircle(a.accent, 9.dp.toPx(), Offset(px, y))
        }
        BasicText(
            (valeur * 100).roundToInt().let { if (it > 0) "+$it" else if (it < 0) "−${-it}" else "0" },
            modifier = Modifier.width(40.dp),
            style = TextStyle(fontFamily = Polices.corps, fontSize = 14.sp, color = Color(0xFFB3AA9C)),
        )
    }
}
