package africa.samaos.reglages

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.samaos.banco.Icones
import africa.samaos.banco.LocalBanco
import africa.samaos.banco.Polices
import africa.samaos.soldes.Releve
import africa.samaos.soldes.Soldes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Les soldes vus des Réglages : les codes de solde, les alertes, la data des derniers jours. */
object MoteurSoldes {
    private const val CLE_CODES = "sama_soldes_codes"
    private const val CLE_ALERTES = "sama_soldes_alertes"

    /** Un code de solde simple (#123#, *124#) : un code avec des étoiles peut acheter quelque chose. */
    fun codeValable(code: String) = Regex("^[*#][0-9]{2,4}#$").matches(code)

    private fun json(c: Context, cle: String) = try {
        JSONObject(Settings.Global.getString(c.contentResolver, cle) ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun ecrire(c: Context, cle: String, o: JSONObject) {
        try {
            Settings.Global.putString(c.contentResolver, cle, o.toString())
        } catch (_: Exception) {
        }
    }

    fun code(c: Context, sub: Int): String? = json(c, CLE_CODES).optString(sub.toString()).ifBlank { null }

    fun reglerCode(c: Context, sub: Int, code: String?) = ecrire(c, CLE_CODES, json(c, CLE_CODES).apply { if (code == null) remove(sub.toString()) else put(sub.toString(), code) })

    enum class Alerte(val cle: String, val nom: String, val detail: String?) {
        DATA("data", "La data passe sous 20 %", null),
        FIN("fin", "Un forfait va expirer avec du reste", "Et proposer de le dépenser utilement"),
        CREDIT("credit", "Le crédit passe sous 200 F", null),
    }

    fun alerte(c: Context, a: Alerte) = json(c, CLE_ALERTES).optBoolean(a.cle, true)

    fun reglerAlerte(c: Context, a: Alerte, oui: Boolean) = ecrire(c, CLE_ALERTES, json(c, CLE_ALERTES).put(a.cle, oui))

    /** Demander le solde à l'opérateur (USSD), sans écran d'opérateur : la réponse arrive ici. */
    fun lire(c: Context, sub: Int, code: String, fini: (String?) -> Unit) {
        try {
            val tm = c.getSystemService(TelephonyManager::class.java).createForSubscriptionId(sub)
            tm.sendUssdRequest(
                code,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(t: TelephonyManager, requete: String, reponse: CharSequence) {
                        Soldes.noterUssd(c, sub, reponse.toString())
                        fini(reponse.toString())
                    }

                    override fun onReceiveUssdResponseFailed(t: TelephonyManager, requete: String, echec: Int) = fini(null)
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: Exception) {
            fini(null)
        }
    }

    /** La data mobile de chacun des 7 derniers jours (toutes SIM), en octets. */
    fun septJours(c: Context): List<Pair<LocalDate, Long>> {
        val nsm = c.getSystemService(NetworkStatsManager::class.java)
        val zone = ZoneId.systemDefault()
        return (6 downTo 0).map { i ->
            val jour = LocalDate.now().minusDays(i.toLong())
            val debut = jour.atStartOfDay(zone).toInstant().toEpochMilli()
            val fin = jour.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val octets = try {
                @Suppress("DEPRECATION")
                val b: NetworkStats.Bucket = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, debut, fin)
                b.rxBytes + b.txBytes
            } catch (_: Exception) {
                0L
            }
            jour to octets
        }
    }

    fun resume(c: Context): String {
        val r = Soldes.releves(c).values.maxByOrNull { it.quand } ?: return "Lus dans les SMS de vos opérateurs"
        return listOfNotNull(r.credit?.let { Soldes.texteMontant(it) }, r.dataReste?.let { Soldes.texteVolume(it) + " de data" }).joinToString(" · ")
    }
}

/** Soldes et forfaits (maquettes i1-reglages, i1-detail-sim). */
@Composable
fun PageSoldes(nav: Nav) {
    val c = LocalContext.current
    val reprise = LocalReprise.current
    var v by remember { mutableIntStateOf(0) }
    var sims by remember { mutableStateOf(emptyList<SubscriptionInfo>()) }
    var releves by remember { mutableStateOf<Map<Int, Releve>>(emptyMap()) }
    var jours by remember { mutableStateOf<List<Pair<LocalDate, Long>>>(emptyList()) }
    var edition by remember { mutableStateOf<Int?>(null) }
    var saisie by remember { mutableStateOf("") }
    var lecture by remember { mutableStateOf<Pair<Int, String>?>(null) }
    LaunchedEffect(v, reprise) {
        sims = withContext(Dispatchers.IO) { MoteurReseau.sims(c) }
        releves = withContext(Dispatchers.IO) { Soldes.releves(c) }
        jours = withContext(Dispatchers.IO) { MoteurSoldes.septJours(c) }
    }
    PageReglages(titre = "Soldes et forfaits", sousTitre = "Plus besoin de taper #…# pour savoir ce qui reste", retour = nav.retour) {
        if (sims.isEmpty()) {
            section(cle = "aucune") { Explication("Aucune carte SIM. Les soldes s'affichent dès qu'une SIM est dans le téléphone.") }
        }
        sims.forEach { s ->
            val sub = s.subscriptionId
            val r = releves[sub]
            section("SIM ${s.simSlotIndex + 1} · ${MoteurReseau.nomSim(s)}", cle = "sim-$sub") {
                if (r?.credit != null) Ligne("Crédit", detail = Soldes.texteLu(r.creditQuand), icone = Icones.SIM, fin = Fin.Valeur(Soldes.texteMontant(r.credit!!)))
                val reste = r?.dataReste
                if (r != null && reste != null) {
                    val total = r.dataTotal
                    Ligne(
                        "Internet",
                        detail = listOfNotNull(
                            Soldes.texteVolume(reste) + (total?.let { " sur " + Soldes.texteVolume(it) } ?: ""),
                            r.expire?.let { Soldes.texteFin(it) },
                            Soldes.texteLu(r.dataQuand),
                        ).joinToString(" · "),
                        icone = Icones.DONNEES,
                        fin = if (total != null && total > 0) Fin.Valeur("${(reste * 100 / total).toInt()} %") else Fin.Rien,
                    )
                    if (total != null && total > 0) Jauge((reste / total).toFloat(), modifier = Modifier.padding(start = 40.dp, bottom = 8.dp))
                }
                if (r == null) Explication("Rien de lu pour l'instant. Sama lit les SMS de l'opérateur après un achat, une recharge ou un code de solde.")
                val code = remember(v) { MoteurSoldes.code(c, sub) }
                if (edition == sub) {
                    Champ(saisie, "Code de solde, par exemple #123#", { saisie = it.filter { ch -> ch.isDigit() || ch == '*' || ch == '#' }.take(8) }, clavier = androidx.compose.ui.text.input.KeyboardType.Phone)
                    val bon = MoteurSoldes.codeValable(saisie)
                    Explication(
                        if (saisie.isBlank() || bon) "Seulement un code qui montre le solde. Un code avec plusieurs étoiles peut acheter un forfait."
                        else "Ce code n'est pas un simple code de solde : Sama ne le composera pas tout seul.",
                        if (saisie.isNotBlank() && !bon) LocalBanco.current.lateriteTexte else null,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (code != null) BoutonTexte("Retirer") {
                            MoteurSoldes.reglerCode(c, sub, null)
                            edition = null
                            v++
                        }
                        BoutonTexte("Annuler") { edition = null }
                        if (bon) BoutonTexte("Garder") {
                            MoteurSoldes.reglerCode(c, sub, saisie)
                            edition = null
                            v++
                        }
                    }
                } else {
                    Ligne("Code de solde", detail = code ?: "À renseigner : le code que vous tapez pour voir votre crédit", icone = Icones.CLAVIER) {
                        saisie = code.orEmpty()
                        edition = sub
                    }
                }
                if (code != null && edition != sub) {
                    Ligne("Lire mon solde maintenant", detail = lecture?.takeIf { it.first == sub }?.second, icone = Icones.ROTATION) {
                        lecture = sub to "Demande envoyée à l'opérateur…"
                        MoteurSoldes.lire(c, sub, code) { rep ->
                            lecture = sub to (rep?.let { "Réponse : ${it.take(140)}" } ?: "L'opérateur n'a pas répondu. Réessayez plus tard.")
                            v++
                        }
                    }
                }
            }
        }
        if (jours.any { it.second > 0 }) {
            section("Data utilisée, 7 derniers jours", cle = "jours") {
                Barres(jours)
            }
        }
        section("Prévenir quand", cle = "alertes") {
            MoteurSoldes.Alerte.entries.forEach { a ->
                val oui = remember(v) { MoteurSoldes.alerte(c, a) }
                Ligne(a.nom, detail = a.detail, fin = Fin.Inter(oui)) {
                    MoteurSoldes.reglerAlerte(c, a, !oui)
                    v++
                }
            }
        }
        section(cle = "comment") {
            Explication(
                "Sama lit les SMS de vos opérateurs et leurs réponses aux codes de solde, ici, sur le téléphone : rien n'est envoyé. " +
                    "Le solde mobile money n'est jamais demandé par code (il faudrait le code secret) : il vient du dernier SMS de transaction.",
            )
        }
    }
}

/** Sept barres, une par jour, en latérite ; aujourd'hui en plein. */
@Composable
private fun Barres(jours: List<Pair<LocalDate, Long>>) {
    val b = LocalBanco.current
    val max = (jours.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        jours.forEachIndexed { i, (jour, octets) ->
            val part = (octets.toFloat() / max).coerceIn(0.02f, 1f)
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (part < 0.99f) Spacer(Modifier.weight(1f - part))
                BasicText(if (octets > 0) taille(octets) else "", maxLines = 1, style = TextStyle(fontFamily = Polices.corps, fontSize = 10.sp, color = b.encre2, textAlign = TextAlign.Center))
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().weight(part)
                        .clip(RoundedCornerShape(6.dp)).background(if (i == jours.lastIndex) b.laterite else b.laterite.copy(alpha = 0.45f)),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    if (i == jours.lastIndex) "auj." else jour.format(DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)),
                    style = TextStyle(fontFamily = Polices.corps, fontSize = 12.sp, color = b.encre2),
                )
            }
        }
    }
}
