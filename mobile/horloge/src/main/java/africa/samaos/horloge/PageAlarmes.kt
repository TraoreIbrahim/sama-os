package africa.samaos.horloge

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.IconeTrait
import africa.samaos.banco.Icones
import africa.samaos.banco.appli.BlocAppli
import africa.samaos.banco.appli.FiletBloc
import africa.samaos.banco.appli.InterAppli
import africa.samaos.banco.appli.LocalIdentite
import africa.samaos.banco.appli.Glissable
import africa.samaos.banco.appli.Avis
import africa.samaos.banco.appli.appui
import africa.samaos.banco.appli.PuceFiltre
import africa.samaos.banco.appli.RoueHeure
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.ZonedDateTime

/** L'heure qu'il est, rafraîchie toutes les [ms] millisecondes. */
@Composable
fun rememberMaintenant(ms: Long = 1000): ZonedDateTime {
    var t by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(ms) {
        while (true) {
            t = ZonedDateTime.now()
            delay(ms)
        }
    }
    return t
}

// ——— Les alarmes ———

@Composable
fun PageAlarmes(version: Int, changer: () -> Unit, ouvrir: (Alarme, Boolean) -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    val alarmes = remember(version) { Alarmes.liste(c) }
    val maintenant = rememberMaintenant(5000)
    val prochaine = alarmes.filter { it.active }.minByOrNull { Alarmes.prochaine(it, maintenant).toInstant() }
    fun nouvelle() {
        val h = (maintenant.hour + 1) % 24
        ouvrir(Alarme(Alarmes.nouvelId(c), h, 0, "", emptySet(), true), true)
    }
    Column(Modifier.fillMaxSize()) {
        Entete("Alarmes", if (alarmes.isEmpty()) null else if (prochaine == null) "Aucune alarme allumée" else "${alarmes.count { it.active }} allumée${if (alarmes.count { it.active } > 1) "s" else ""}") {
            BoutonRond("Nouvelle alarme", a.accent, a.surAccent, 52.dp, icone = Icones.PLUS) { nouvelle() }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (prochaine != null) {
                item(key = "prochaine") { CarteProchaine(prochaine, maintenant, Modifier.animateItem()) { ouvrir(prochaine, false) } }
            }
            items(alarmes, key = { it.id }) { al ->
                Glissable(
                    supprimer = {
                        Alarmes.supprimer(c, al.id)
                        changer()
                        Avis.dire("Alarme de ${"%02d:%02d".format(al.heure, al.minute)} supprimée") {
                            Alarmes.garder(c, al)
                            changer()
                        }
                    },
                    modifier = Modifier.animateItem(),
                ) {
                    CarteAlarme(al, maintenant, basculer = {
                        val allumee = !al.active
                        Alarmes.garder(c, al.copy(active = allumee))
                        changer()
                        if (allumee) Avis.dire("Sonne dans ${Alarmes.duree(Alarmes.prochaine(al))}")
                    }) { ouvrir(al, false) }
                }
            }
            if (alarmes.isEmpty()) {
                item(key = "vide") { Vide(Modifier.animateItem()) { nouvelle() } }
            } else {
                item(key = "aide") {
                    BasicText("Glissez une alarme vers la gauche pour la supprimer.", modifier = Modifier.padding(6.dp).animateItem(), style = texte(13f, a.encre2))
                }
            }
            item(key = "pied") { Pied() }
        }
    }
}

/** La prochaine alarme, en grand : le temps qui reste, sur un ciel qui pâlit vers l'aube. */
@Composable
private fun CarteProchaine(al: Alarme, maintenant: ZonedDateTime, modifier: Modifier, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val quand = Alarmes.prochaine(al, maintenant)
    Box(
        modifier.fillMaxWidth().appui("Prochaine alarme, ${Alarmes.dans(quand)}", onClick = ouvrir)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF34406E), Color(0xFF5A4E78), Color(0xFF8C6A78)))),
    ) {
        // Le soleil qui se lève, à droite.
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width * 0.86f, size.height * 1.02f)
            drawCircle(Brush.radialGradient(listOf(AUBE.copy(alpha = 0.55f), Color.Transparent), c, size.height * 0.95f), size.height * 0.95f, c)
            drawCircle(AUBE, size.height * 0.24f, c)
        }
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 20.dp)) {
            BasicText("Prochaine alarme", style = texte(14f, Color.White.copy(alpha = 0.75f), FontWeight.SemiBold))
            TexteRoulant(
                Alarmes.duree(quand),
                CHIFFRES.copy(fontSize = 46.sp, lineHeight = 52.sp, color = Color.White, fontWeight = FontWeight(260)),
                Modifier.padding(top = 2.dp), descend = true,
            )
            BasicText(
                Alarmes.jour(quand).replaceFirstChar { it.uppercase() } + " à " + "%02d:%02d".format(al.heure, al.minute) + (if (al.nom.isNotBlank()) " · ${al.nom}" else ""),
                style = texte(15f, Color.White.copy(alpha = 0.85f)),
            )
        }
    }
}

@Composable
private fun CarteAlarme(al: Alarme, maintenant: ZonedDateTime, basculer: () -> Unit, ouvrir: () -> Unit) {
    val a = LocalIdentite.current
    val clarte by animateFloatAsState(if (al.active) 1f else 0.5f, tween(260), label = "clarte")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(a.surface)
            .clickable(onClickLabel = "Régler l'alarme", role = Role.Button, onClick = ouvrir)
            .padding(start = 22.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).graphicsLayer { alpha = clarte }) {
            BasicText("%02d:%02d".format(al.heure, al.minute), style = CHIFFRES.copy(fontSize = 54.sp, lineHeight = 58.sp, color = a.encre))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (al.nom.isNotBlank()) {
                    BasicText(al.nom, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false), style = texte(14.5f, a.encre, FontWeight.Medium))
                    BasicText("  ·  ", style = texte(14f, a.encre2))
                }
                BasicText(
                    if (al.jours.isEmpty() && al.active) Alarmes.jour(Alarmes.prochaine(al, maintenant)).replaceFirstChar { it.uppercase() } else repetition(al.jours),
                    style = texte(14f, a.encre2),
                )
            }
        }
        Box(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClickLabel = if (al.active) "Éteindre" else "Allumer", role = Role.Switch, onClick = basculer).padding(6.dp)) {
            InterAppli(al.active)
        }
    }
}

@Composable
private fun Vide(modifier: Modifier, ajouter: () -> Unit) {
    val a = LocalIdentite.current
    Column(modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(a.surface), contentAlignment = Alignment.Center) {
            IconeTrait("M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z M12 9v4l2 2 M5 3L2 6 M19 3l3 3", 42.dp, a.accent)
        }
        BasicText("Aucune alarme", modifier = Modifier.padding(top = 18.dp), style = texte(20f, a.encre, FontWeight.Bold))
        BasicText("Elles sonnent même téléphone en veille ou en silence.", modifier = Modifier.padding(top = 6.dp, start = 30.dp, end = 30.dp), style = texte(15f, a.encre2).copy(textAlign = TextAlign.Center))
        Box(
            Modifier.padding(top = 22.dp).appui("Mettre une alarme", onClick = ajouter).clip(RoundedCornerShape(24.dp)).background(a.accent).padding(horizontal = 26.dp, vertical = 14.dp),
        ) { BasicText("Mettre une alarme", style = texte(16f, a.surAccent, FontWeight.Bold)) }
    }
}

// ——— Régler une alarme ———

@Composable
fun EditeurAlarme(depart: Alarme, nouvelle: Boolean, fermer: () -> Unit) {
    val a = LocalIdentite.current
    val c = LocalContext.current
    var al by remember(depart.id) { mutableStateOf(depart) }
    val maintenant = rememberMaintenant(5000)
    BackHandler(onBack = fermer)
    val choixSon = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        @Suppress("DEPRECATION")
        val uri = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            val parDefaut = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            al = al.copy(son = uri?.takeIf { it != parDefaut }?.toString())
        }
    }
    val nomSon = remember(al.son) {
        try {
            val uri = al.son?.let { Uri.parse(it) } ?: RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM)
            (if (al.son == null) "Par défaut · " else "") + (RingtoneManager.getRingtone(c, uri)?.getTitle(c) ?: "Sonnerie")
        } catch (_: Exception) {
            "Par défaut"
        }
    }
    fun garder() {
        val g = al.copy(active = true)
        Alarmes.garder(c, g)
        Avis.dire("Sonne dans ${Alarmes.duree(Alarmes.prochaine(g))}")
        fermer()
    }
    Box(Modifier.fillMaxSize().background(a.fond)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Annuler", modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = fermer).padding(10.dp), style = texte(16f, a.encre2))
                BasicText(if (nouvelle) "Nouvelle alarme" else "L'alarme", modifier = Modifier.weight(1f), style = texte(17f, a.encre, FontWeight.Bold).copy(textAlign = TextAlign.Center))
                BasicText("Garder", modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { garder() }.padding(10.dp), style = texte(16f, a.accent, FontWeight.Bold))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // L'heure, à la molette, et ce qui reste avant qu'elle sonne.
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(a.surface).padding(vertical = 14.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RoueHeure(al.heure, al.minute, cran = 62.dp, taille = 46.sp) { h, m -> al = al.copy(heure = h, minute = m) }
                    val quand = Alarmes.prochaine(al, maintenant)
                    AnimatedContent(
                        "Sonne dans ${Alarmes.duree(quand)} · ${Alarmes.jour(quand)}",
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "dans",
                    ) { t -> BasicText(t, modifier = Modifier.padding(top = 8.dp), style = texte(15f, a.accent, FontWeight.SemiBold)) }
                }
                Section("Répéter")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Une fois" to emptySet(), "En semaine" to SEMAINE, "Le week-end" to WEEKEND, "Tous les jours" to DayOfWeek.entries.toSet()).forEach { (n, j) ->
                        PuceFiltre(n, al.jours == j) { al = al.copy(jours = j) }
                    }
                }
                ChoixJours(al.jours, a.accent) { al = al.copy(jours = it) }
                Section("Nom")
                BasicTextField(
                    value = al.nom, onValueChange = { al = al.copy(nom = it.take(40)) }, singleLine = true,
                    textStyle = texte(17f, a.encre), cursorBrush = SolidColor(a.accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(a.surface).padding(horizontal = 18.dp, vertical = 16.dp).semantics { contentDescription = "Nom de l'alarme" },
                    decorationBox = { champ -> Box { if (al.nom.isEmpty()) BasicText("Réveil, médicament, réunion…", style = texte(17f, a.encre2)); champ() } },
                )
                Section("Quand elle sonne")
                BlocAppli {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonnerie de l'alarme")
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, al.son?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                            try {
                                choixSon.launch(i)
                            } catch (_: Exception) {
                                Avis.dire("Ce téléphone ne propose pas d'autre sonnerie.")
                            }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeTrait(Icones.MUSIQUE, 20.dp, a.accent)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            BasicText("Sonnerie", style = texte(16f, a.encre))
                            BasicText(nomSon, maxLines = 1, overflow = TextOverflow.Ellipsis, style = texte(13.5f, a.encre2))
                        }
                        IconeTrait(Icones.CHEVRON, 16.dp, a.encre2)
                    }
                    FiletBloc(50.dp)
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Switch) { al = al.copy(vibrer = !al.vibrer) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeTrait(Icones.VIBREUR, 20.dp, a.accent)
                        BasicText("Vibrer", modifier = Modifier.weight(1f).padding(horizontal = 14.dp), style = texte(16f, a.encre))
                        InterAppli(al.vibrer)
                    }
                    FiletBloc(50.dp)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconeTrait(Icones.CLOCHE, 20.dp, a.accent)
                            BasicText("« Encore » la repousse de", modifier = Modifier.padding(start = 14.dp), style = texte(16f, a.encre))
                        }
                        Row(Modifier.padding(start = 34.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 20).forEach { m -> PuceFiltre("$m min", al.report == m) { al = al.copy(report = m) } }
                        }
                    }
                }
                if (!nouvelle) {
                    Box(
                        Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(a.surface)
                            .clickable(role = Role.Button) {
                                val gardee = depart
                                Alarmes.supprimer(c, al.id)
                                fermer()
                                Avis.dire("Alarme supprimée") {
                                    Alarmes.garder(c, gardee)
                                    Etat.version++
                                }
                            }.padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { BasicText("Supprimer l'alarme", style = texte(16f, CORAIL, FontWeight.SemiBold)) }
                }
                Spacer(Modifier.navigationBarsPadding().height(30.dp))
            }
        }
    }
}

@Composable
fun Section(titre: String) {
    val a = LocalIdentite.current
    BasicText(titre.uppercase(), modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 22.dp, bottom = 10.dp), style = texte(13f, a.encre2, FontWeight.Bold).copy(letterSpacing = 0.6.sp))
}
