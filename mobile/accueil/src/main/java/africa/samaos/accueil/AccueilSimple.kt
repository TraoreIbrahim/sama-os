package africa.samaos.accueil

import africa.samaos.banco.*
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * L'Accueil du mode simplifié (maquette quo-04-mode-simplifie) : l'heure en grand, trois proches à appeler d'un
 * toucher, six grandes tuiles et « Besoin d'aide ? ». En Andika, sans geste à deviner : toutes les applis sont
 * derrière l'aide, les Espaces ne changent pas à trois doigts. Le Pouls reste là, tiré du haut, pour les
 * notifications.
 */
@Composable
fun AccueilSimple(
    tuiles: List<String>,
    espaces: Espaces,
    applis: List<Appli>,
    cour: Panneau,
    pouls: Panneau,
    nuit: ReglageNuit,
    generation: Int,
    lancer: (Appli) -> Unit,
) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val densite = LocalDensity.current
    val barreEtat = WindowInsets.statusBars.getTop(densite)
    val tuilesPouls = remember { TuilesPouls(contexte) }
    var aide by remember { mutableStateOf(false) }
    var proches by remember { mutableStateOf<List<ProcheSimple>?>(null) }
    LaunchedEffect(generation) { proches = withContext(Dispatchers.IO) { ProcheSimple.etoiles(contexte) } }
    val lampe = rememberLampe()
    val notifs by Notifications.liste.collectAsState()
    val paquetAppel = remember(generation) { paquetDefaut(contexte, Intent(Intent.ACTION_DIAL)) }
    val paquetSms = remember(generation) { Telephony.Sms.getDefaultSmsPackage(contexte) }

    BackHandler(enabled = cour.visible) { cour.fermer() }
    BackHandler(enabled = pouls.visible) { pouls.fermer() }
    BackHandler(enabled = aide) { aide = false }

    // Seul le doigt qui descend a un effet : il tire le Pouls. La Cour ne s'ouvre que par l'aide.
    val glissement = rememberDraggableState { dy -> pouls.glisser(dy) }
    val texte = TextStyle(fontFamily = Polices.andika, color = b.encre)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                with(densite) {
                    cour.course = it.height - (barreEtat + 104.dp.toPx())
                    cour.seuilElan = 600.dp.toPx()
                    pouls.seuilElan = 600.dp.toPx()
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - (cour.progres * 1.6f).coerceAtMost(1f) }
                .draggable(state = glissement, orientation = Orientation.Vertical, onDragStopped = { vy -> pouls.relacher(vy) }),
        ) {
            Paysage(astre = Astre(318f, 120f, 50f), collines = listOf(300f))
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
                    val maintenant = rememberMaintenant()
                    BasicText(maintenant.format(DateTimeFormatter.ofPattern("HH:mm")), style = texte.copy(fontSize = 64.sp, lineHeight = 66.sp))
                    BasicText(dateDuJour(maintenant), modifier = Modifier.padding(top = 6.dp), style = texte.copy(fontSize = 22.sp, color = b.encre2))
                }
                RangeeProches(proches, Modifier.padding(start = 12.dp, end = 12.dp, top = 22.dp))
                Box(Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        Crete()
                        Column(
                            Modifier
                                .fillMaxSize()
                                .background(b.sol)
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 26.dp),
                        ) {
                            tuiles.chunked(2).forEachIndexed { rangee, paire ->
                                Row(Modifier.fillMaxWidth().padding(top = if (rangee == 0) 0.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    paire.forEachIndexed { i, valeur ->
                                        val place = rangee * 2 + i
                                        val paquet = ModeSimple.paquet(valeur)
                                        val action = ModeSimple.action(valeur)
                                        val appli = paquet?.let { p -> applis.firstOrNull { it.paquet == p } }
                                        val nouveaux = when (action) {
                                            ModeSimple.Action.APPELER -> notifs.count { it.paquet == paquetAppel }
                                            ModeSimple.Action.MESSAGES -> notifs.count { it.paquet == paquetSms }
                                            else -> notifs.count { paquet != null && it.paquet == paquet }
                                        }
                                        TuileSimple(
                                            place = place,
                                            nom = when {
                                                action == ModeSimple.Action.LAMPE && lampe.allumee == true -> "Éteindre"
                                                action != null -> action.nom
                                                else -> appli?.nom ?: "—"
                                            },
                                            icone = action?.icone,
                                            image = appli?.icone,
                                            nouveaux = nouveaux,
                                            allumee = action == ModeSimple.Action.LAMPE && lampe.allumee == true,
                                            disponible = when (action) {
                                                null -> appli != null
                                                ModeSimple.Action.LAMPE -> lampe.allumee != null
                                                ModeSimple.Action.METEO -> applis.any { it.paquet == METEO }
                                                else -> true
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            when (action) {
                                                null -> appli?.let(lancer)
                                                ModeSimple.Action.LAMPE -> lampe.basculer()
                                                else -> ouvrirAction(contexte, action, paquetAppel, paquetSms)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Row(
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(b.sol2)
                                    .clickable(onClickLabel = "Besoin d'aide", role = Role.Button) { aide = true }
                                    .padding(horizontal = 28.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                IconeTrait(ModeSimple.AIDE, 26.dp, b.laterite)
                                BasicText("Besoin d'aide ?", style = texte.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
        Cour(applis = applis, espace = espaces.actif, panneau = cour, lancer = lancer, menu = { _, _ -> })
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = pouls.progres }.background(b.voile))
        Pouls(panneau = pouls, applis = applis, nuit = nuit, espaces = espaces, tuiles = tuilesPouls, ouvrirEspaces = {}, modifierTuiles = {})
        if (aide) {
            FeuilleAide(
                fermer = { aide = false },
                toutes = {
                    aide = false
                    cour.ouvrir()
                },
            )
        }
        if (espaces.sansCode) EcranProteger(espaces)
        espaces.passage?.let { EcranPassage(espaces, it) }
    }
}

private const val METEO = "africa.samaos.meteo"

/** Une grande tuile : la couleur de sa place, son picto (ou l'icône de l'appli), son nom en gros. */
@Composable
private fun TuileSimple(
    place: Int,
    nom: String,
    icone: String?,
    image: ImageBitmap?,
    nouveaux: Int,
    allumee: Boolean,
    disponible: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val b = LocalBanco.current
    val (fond, encre) = when {
        // Allumée : un jaune clair d'ampoule, distinct de l'or de la tuile Argent.
        allumee -> Color(0xFFFFE6A0) to Color(0xFF1E2740)
        else -> ModeSimple.COULEURS.getOrNull(place) ?: (b.sol2 to b.encre)
    }
    Box(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(fond)
            .clickable(enabled = disponible, onClickLabel = nom, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (disponible) 1f else 0.45f }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        when {
            icone != null -> IconeTrait(icone, 30.dp, encre)
            image != null -> Image(image, contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)))
        }
        BasicText(
            nom,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart),
            style = TextStyle(fontFamily = Polices.andika, fontWeight = FontWeight.Bold, fontSize = 23.sp, color = encre),
        )
        // Du nouveau (un appel manqué, un message) : un point, sans chiffre à lire ; le lecteur d'écran, lui, dit combien.
        if (nouveaux > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(Color(0xFFFFFFFF))
                    .semantics { contentDescription = if (nouveaux == 1) "1 nouveau" else "$nouveaux nouveaux" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFC2412D))) }
        }
    }
}

/** Les proches marqués d'une étoile dans Contacts : un toucher les appelle. Sans étoile, de quoi en choisir. */
@Composable
private fun RangeeProches(proches: List<ProcheSimple>?, modifier: Modifier = Modifier) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val nom = TextStyle(fontFamily = Polices.andika, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = b.encre)
    Row(modifier.fillMaxWidth().height(110.dp)) {
        if (proches == null) return@Row
        if (proches.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClickLabel = "Choisir mes proches", role = Role.Button) { ouvrirContacts(contexte) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(b.sol2), contentAlignment = Alignment.Center) {
                    IconeTrait(Icones.PLUS, 32.dp, b.laterite)
                }
                BasicText("Mes proches", modifier = Modifier.padding(top = 6.dp), style = nom)
            }
            Column(Modifier.weight(2f).fillMaxHeight().padding(start = 8.dp, end = 12.dp), verticalArrangement = Arrangement.Center) {
                BasicText(
                    "Mettez une étoile à vos proches dans Contacts : ils viennent ici, et un toucher les appelle.",
                    style = TextStyle(fontFamily = Polices.andika, fontSize = 16.sp, lineHeight = 22.sp, color = b.encre2),
                )
            }
            return@Row
        }
        proches.take(3).forEachIndexed { i, p ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClickLabel = "Appeler ${p.nom}", role = Role.Button) { appeler(contexte, p.numero) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(80.dp)) {
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(TEINTES_PROCHES[i % TEINTES_PROCHES.size]),
                        contentAlignment = Alignment.Center,
                    ) {
                        val photo = p.photo
                        if (photo != null) {
                            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            BasicText(
                                p.nom.take(1).uppercase(),
                                style = TextStyle(fontFamily = Polices.andika, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Color(0xFFFFFFFF)),
                            )
                        }
                    }
                    // La pastille du combiné : un toucher appelle.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-2).dp, y = (-2).dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(b.ciel)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(if (i == 2) b.encre else b.foret),
                        contentAlignment = Alignment.Center,
                    ) { IconeTrait(Icones.APPEL, 15.dp, Color(0xFFFFFFFF)) }
                }
                // Le prénom seul, comme on appelle ses proches (« Kofi », « Maman »).
                BasicText(p.nom.substringBefore(' '), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp), style = nom)
            }
        }
        repeat(3 - proches.size.coerceAtMost(3)) { Spacer(Modifier.weight(1f)) }
    }
}

private val TEINTES_PROCHES = listOf(Color(0xFFB5532F), Color(0xFF3D5A99), Color(0xFF2F6B57))

/** « Besoin d'aide ? » : toutes les applis, l'appel d'urgence, et les réglages du mode simplifié. */
@Composable
private fun FeuilleAide(fermer: () -> Unit, toutes: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures { fermer() } },
    ) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }) {
            Crete()
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(b.sol)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(
                    "Besoin d'aide ?",
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    style = TextStyle(fontFamily = Polices.andika, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = b.encre),
                )
                RangeeAide(Icones.GRILLE, "Toutes mes applis", b.sol2, b.encre, toutes)
                RangeeAide(Icones.APPEL, "Appel d'urgence", b.danger, Color(0xFFFFFFFF)) {
                    fermer()
                    ouvrir(contexte, Intent("android.telephony.action.EMERGENCY_ASSISTANCE"))
                }
                RangeeAide(Icones.REGLAGES, "Régler le mode simplifié", b.sol2, b.encre) {
                    fermer()
                    ouvrir(contexte, Intent(ModeSimple.ACTION_REGLAGES))
                }
                RangeeAide(Icones.FERMER, "Fermer", Color.Transparent, b.encre2, fermer)
            }
        }
    }
}

@Composable
private fun RangeeAide(icone: String, titre: String, fond: Color, encre: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fond)
            .clickable(onClickLabel = titre, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconeTrait(icone, 26.dp, encre)
        BasicText(titre, style = TextStyle(fontFamily = Polices.andika, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = encre))
    }
}

/** Un proche marqué d'une étoile, avec le numéro à appeler et sa photo s'il en a une. */
class ProcheSimple(val nom: String, val numero: String, val photo: ImageBitmap?) {
    companion object {
        fun etoiles(c: Context): List<ProcheSimple> = try {
            val cr = c.contentResolver
            val liste = mutableListOf<ProcheSimple>()
            cr.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
                "${ContactsContract.Contacts.STARRED} = 1 AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
                null,
                "${ContactsContract.Contacts.SORT_KEY_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { k ->
                while (k.moveToNext() && liste.size < 3) {
                    val id = k.getLong(0)
                    val numero = numero(c, id) ?: continue
                    val photo = k.getString(2)?.let { u ->
                        try {
                            cr.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    liste += ProcheSimple(k.getString(1).orEmpty().ifBlank { numero }, numero, photo)
                }
            }
            liste
        } catch (_: SecurityException) {
            emptyList()
        }

        /** Le numéro principal du contact, sinon le premier. */
        private fun numero(c: Context, id: Long): String? = c.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(id.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }
}

/** Appeler tout de suite si l'Accueil en a le droit ; sinon, le numéro tout prêt dans le Téléphone. */
private fun appeler(c: Context, numero: String) {
    val uri = Uri.fromParts("tel", numero, null)
    val direct = c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    ouvrir(c, Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
}

private fun ouvrirAction(c: Context, a: ModeSimple.Action, paquetAppel: String?, paquetSms: String?) {
    val pm = c.packageManager
    when (a) {
        ModeSimple.Action.APPELER -> ouvrir(c, paquetAppel?.let { pm.getLaunchIntentForPackage(it) } ?: Intent(Intent.ACTION_DIAL))
        ModeSimple.Action.MESSAGES -> paquetSms?.let { pm.getLaunchIntentForPackage(it) }?.let { ouvrir(c, it) }
        ModeSimple.Action.PHOTO -> ouvrir(c, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        // Les soldes de Sama : crédit, forfaits, et le mobile money après le code du téléphone.
        ModeSimple.Action.ARGENT -> ouvrir(c, Intent("africa.samaos.action.SOLDES"))
        ModeSimple.Action.METEO -> pm.getLaunchIntentForPackage(METEO)?.let { ouvrir(c, it) }
        ModeSimple.Action.LAMPE -> {}
    }
}

private fun ouvrirContacts(c: Context) {
    val i = c.packageManager.getLaunchIntentForPackage("africa.samaos.contacts")
        ?: Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
    ouvrir(c, i)
}

private fun ouvrir(c: Context, i: Intent) {
    try {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
}

private fun paquetDefaut(c: Context, i: Intent): String? =
    c.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != "android" }
