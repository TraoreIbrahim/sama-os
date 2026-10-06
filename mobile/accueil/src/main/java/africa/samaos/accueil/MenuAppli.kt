package africa.samaos.accueil

import africa.samaos.banco.*
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** D'où l'on a tenu l'appli : son menu propose ce qui a du sens à cet endroit. */
sealed interface Origine {
    data object Accueil : Origine
    data object Cour : Origine
    data class Dossier(val dossier: ElemDossier) : Origine
}

/** L'appli tenue du doigt, et la place de son icône à l'écran. */
class Tenue(val appli: Appli, val bornes: Rect, val origine: Origine)

/** Ce que le menu peut changer sur l'Accueil. */
class ActionsAccueil(
    val surAccueil: (Appli) -> Boolean,
    val ajouter: (Appli) -> Boolean,
    val retirer: (Appli) -> Unit,
    val sortir: (ElemDossier, Appli) -> Unit,
)

/** Un raccourci que l'appli propose (« Nouveau message », « Scanner »…), via le lanceur d'Android. */
private class Raccourci(val info: ShortcutInfo, val nom: String, val icone: ImageBitmap?)

private fun raccourcis(contexte: Context, paquet: String): List<Raccourci> {
    val la = contexte.getSystemService(LauncherApps::class.java) ?: return emptyList()
    return try {
        if (!la.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(paquet)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        val densite = contexte.resources.displayMetrics.densityDpi
        val taille = (24 * contexte.resources.displayMetrics.density).toInt()
        la.getShortcuts(q, Process.myUserHandle()).orEmpty()
            .filter { it.isEnabled && (it.isDeclaredInManifest || it.isDynamic) }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(4)
            .map { info ->
                Raccourci(
                    info,
                    (info.shortLabel ?: info.longLabel ?: "").toString(),
                    try {
                        la.getShortcutIconDrawable(info, densite)?.toBitmap(taille, taille)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    },
                )
            }
            .filter { it.nom.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}

/** Une appli qu'on peut désinstaller : pas une appli du système (on ne peut que la masquer d'un Espace). */
private fun desinstallable(contexte: Context, paquet: String): Boolean = try {
    val info = contexte.packageManager.getApplicationInfo(paquet, 0)
    info.flags and ApplicationInfo.FLAG_SYSTEM == 0 || info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
} catch (_: Exception) {
    false
}

private sealed interface VueMenu {
    data object Principal : VueMenu
    data object Espaces : VueMenu
    data class Envoyee(val espace: Espace) : VueMenu
}

/**
 * Le menu d'une appli, à l'appui long (maquette l1-menu-appli) : ses raccourcis, puis Infos,
 * l'Accueil, Vers un Espace et Désinstaller. L'icône tenue reste soulevée sous le menu.
 */
@Composable
fun MenuAppli(t: Tenue, espaces: Espaces, actions: ActionsAccueil, fermer: () -> Unit) {
    val b = LocalBanco.current
    val contexte = LocalContext.current
    val densite = LocalDensity.current
    BackHandler(onBack = fermer)
    var liste by remember(t) { mutableStateOf<List<Raccourci>>(emptyList()) }
    LaunchedEffect(t) { liste = withContext(Dispatchers.IO) { raccourcis(contexte, t.appli.paquet) } }
    var vue by remember(t) { mutableStateOf<VueMenu>(VueMenu.Principal) }
    var hauteurCarte by remember { mutableIntStateOf(0) }
    val autres = espaces.liste.filter { it.id != espaces.actif.id }
    val versUnEspace = espaces.profils && espaces.actif.estMaison && autres.isNotEmpty()

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(b.voile)
            .pointerInput(Unit) { detectTapGestures { fermer() } },
    ) {
        val hautEcran = with(densite) { maxHeight.toPx() }
        val marge = with(densite) { 12.dp.toPx() }
        // Le menu se pose au-dessus de l'icône quand elle est dans le bas de l'écran, dessous sinon.
        val dessus = t.bornes.center.y > hautEcran / 2
        val y = if (dessus) t.bornes.top - marge - hauteurCarte - with(densite) { 20.dp.toPx() } else t.bornes.bottom + marge + with(densite) { 26.dp.toPx() }

        // L'icône tenue, soulevée.
        Column(
            Modifier
                .offset { IntOffset(t.bornes.left.roundToInt(), t.bornes.top.roundToInt()) }
                .width(with(densite) { t.bornes.width.toDp() })
                .graphicsLayer {
                    scaleX = 1.12f
                    scaleY = 1.12f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(t.appli.icone, contentDescription = null, modifier = Modifier.size(with(densite) { t.bornes.width.toDp() }))
            BasicText(
                t.appli.nom, maxLines = 1, overflow = TextOverflow.Visible, softWrap = false,
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = b.encre),
            )
        }

        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .offset { IntOffset(0, y.roundToInt().coerceAtLeast(with(densite) { 40.dp.roundToPx() })) }
                .onSizeChanged { hauteurCarte = it.height }
                .graphicsLayer { alpha = if (hauteurCarte == 0) 0f else 1f }
                .shadow(16.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(b.sol)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(10.dp),
        ) {
            when (val v = vue) {
                VueMenu.Principal -> {
                    liste.forEach { r ->
                        RangeeMenu(r.nom, r.icone, null) {
                            fermer()
                            try {
                                contexte.getSystemService(LauncherApps::class.java)?.startShortcut(r.info, null, null)
                            } catch (_: Exception) {
                                // Le raccourci a disparu entre-temps.
                            }
                        }
                    }
                    if (liste.isNotEmpty()) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(0.5.dp).background(b.encre2.copy(alpha = 0.25f)))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActionMenu(Icones.INFO, "Infos", Modifier.weight(1f)) {
                            fermer()
                            Systeme.ouvrir(
                                contexte,
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", t.appli.paquet, null)),
                            )
                        }
                        when (val o = t.origine) {
                            is Origine.Dossier -> ActionMenu(Icones.DOSSIER, "Sortir du dossier", Modifier.weight(1f)) {
                                fermer()
                                actions.sortir(o.dossier, t.appli)
                            }
                            Origine.Accueil -> ActionMenu(Icones.MOINS, "Retirer de l'Accueil", Modifier.weight(1f)) {
                                fermer()
                                actions.retirer(t.appli)
                            }
                            Origine.Cour -> if (!actions.surAccueil(t.appli)) {
                                ActionMenu(Icones.PLUS, "Sur l'Accueil", Modifier.weight(1f)) {
                                    fermer()
                                    actions.ajouter(t.appli)
                                }
                            }
                        }
                        if (versUnEspace) {
                            ActionMenu(Icones.VERS_ESPACE, "Vers un Espace", Modifier.weight(1f)) { vue = VueMenu.Espaces }
                        }
                        if (desinstallable(contexte, t.appli.paquet)) {
                            ActionMenu(Icones.DESINSTALLER, "Désinstaller", Modifier.weight(1f), danger = true) {
                                fermer()
                                // Android demande confirmation : rien ne part sans un « OK » de la personne.
                                Systeme.ouvrir(contexte, Intent(Intent.ACTION_DELETE, Uri.fromParts("package", t.appli.paquet, null)))
                            }
                        }
                    }
                }
                VueMenu.Espaces -> {
                    BasicText(
                        "Mettre ${t.appli.nom} dans",
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
                        style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = b.encre2),
                    )
                    autres.forEach { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClickLabel = e.nom, role = Role.Button) {
                                    if (Profils.envoyerVers(contexte, e.id, t.appli.paquet)) vue = VueMenu.Envoyee(e)
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(12.dp).background(e.couleur(b), CircleShape))
                            BasicText(
                                e.nom,
                                modifier = Modifier.padding(start = 14.dp),
                                style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre),
                            )
                        }
                    }
                }
                is VueMenu.Envoyee -> {
                    BasicText(
                        "${t.appli.nom} sera dans ${v.espace.nom} la prochaine fois que vous y entrerez.",
                        modifier = Modifier.padding(16.dp),
                        style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, lineHeight = 24.sp, color = b.encre),
                    )
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.End) {
                        BasicText(
                            "OK",
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = fermer)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = b.lateriteTexte),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeeMenu(nom: String, icone: ImageBitmap?, trait: String?, onClick: () -> Unit) {
    val b = LocalBanco.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = nom, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            icone != null -> Image(icone, contentDescription = null, modifier = Modifier.size(24.dp))
            trait != null -> IconeTrait(trait, 22.dp, b.encre)
            else -> Box(Modifier.size(24.dp))
        }
        BasicText(nom, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Polices.corps, fontSize = 17.sp, color = b.encre))
    }
}

@Composable
private fun ActionMenu(icone: String, nom: String, mod: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val b = LocalBanco.current
    val couleur = if (danger) b.danger else b.encre
    Column(
        mod
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = nom, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconeTrait(icone, 22.dp, couleur)
        BasicText(
            nom, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Polices.corps, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, color = couleur, textAlign = TextAlign.Center),
        )
    }
}
