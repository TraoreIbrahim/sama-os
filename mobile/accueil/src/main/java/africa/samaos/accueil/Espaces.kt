package africa.samaos.accueil

import africa.samaos.banco.*
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



/**
 * Le verrou d'un Espace : un niveau qu'Android fait respecter quand on choisit le code (il grise ce qui
 * ne suffit pas). Android ne laisse pas imposer le type exact ; l'empreinte s'ajoute si le téléphone en a une.
 */
enum class VerrouEspace(val nom: String, val detail: String, val complexite: Int, val quoi: String) {
    SCHEMA(
        "Schéma ou code", "Le plus simple : un dessin ou quelques chiffres",
        DevicePolicyManager.PASSWORD_COMPLEXITY_LOW, "le schéma ou le code",
    ),
    CODE(
        "Code", "4 chiffres ou plus, sans suite trop simple comme 1234",
        DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM, "le code",
    ),
    MOT_DE_PASSE(
        "Mot de passe", "6 caractères ou plus, ou un code de 8 chiffres",
        DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH, "le mot de passe",
    ),
}

/**
 * Un Espace : un profil du téléphone, avec ses applis, ses comptes, ses fichiers et ses notifications.
 * Maison est le profil de la personne à qui appartient le téléphone ; les autres, c'est elle qui les crée.
 */
@Immutable
data class Espace(
    val id: Int,
    val nom: String,
    val teinte: CouleurEspace,
    val paysage: PaysageEspace,
    val verrou: VerrouEspace = VerrouEspace.CODE,
) {
    val estMaison: Boolean get() = id == 0

    fun couleur(b: Banco): Color = teinte.couleur(b)
}

val MAISON = Espace(0, "Maison", CouleurEspace.VERT, PaysageEspace.LAGUNE)

/**
 * Les Espaces du téléphone et l'Espace actif.
 *
 * Il n'y a pas d'Espace tout fait : au départ, seulement Maison. La personne crée les siens (nom, couleur,
 * paysage) ; chacun devient un profil Android à part, protégé par son propre code, demandé par le téléphone
 * et qui chiffre ses fichiers. Cela demande que Sama soit le système : sur un téléphone ordinaire,
 * l'Accueil n'a que Maison.
 */
@Stable
class Espaces(private val contexte: Context) {
    private val prefs = reglages(contexte, "espaces")

    /** Vrai quand Sama est le système et peut créer des profils. */
    val profils: Boolean = Profils.disponibles(contexte)

    var liste: List<Espace> by mutableStateOf(listOf(MAISON))
        private set

    var actif: Espace by mutableStateOf(MAISON)
        private set

    /** Le formulaire « Nouvel Espace » est ouvert. */
    var enCreation by mutableStateOf(false)

    /** Cet Accueil tourne dans l'Espace Invité (le téléphone est prêté). */
    val invite: Boolean = profils && Invite.estInvite(contexte)

    /** L'écran « Prêter le téléphone » est ouvert. */
    var enPret by mutableStateOf(false)
        private set

    /** L'Espace vers lequel on passe : l'écran de passage Banco est affiché. */
    var passage: Espace? by mutableStateOf(null)
        private set

    /** Vrai quand l'Espace où l'on se trouve a un code (schéma, code ou mot de passe). */
    var securise by mutableStateOf(false)
        private set

    /** Vrai dans un Espace qui n'a pas encore de code : on propose d'en choisir un. */
    var sansCode by mutableStateOf(false)
        private set

    init {
        rafraichir()
    }

    /** Relit les profils : au retour sur l'Accueil, après une création. */
    fun rafraichir() {
        if (!profils) return
        val reglages = Profils.lireEspaces(contexte)
        liste = Profils.liste(contexte)
            .map { (id, nom) ->
                val r = reglages[id]
                Espace(
                    id = id,
                    nom = if (id == 0) MAISON.nom else nom,
                    teinte = r?.teinte ?: if (id == 0) MAISON.teinte else CouleurEspace.LATERITE,
                    paysage = r?.paysage ?: PaysageEspace.LAGUNE,
                    verrou = r?.verrou ?: VerrouEspace.CODE,
                )
            }
            .sortedBy { it.id }
            .ifEmpty { listOf(MAISON) }
        actif = liste.firstOrNull { it.id == Profils.monId() } ?: MAISON
        securise = contexte.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        sansCode = !invite && !actif.estMaison && !securise && !prefs.getBoolean("plus_tard", false)
        if (invite && !prefs.getBoolean("invite_pret", false)) {
            Invite.poserApplis(contexte)
            prefs.edit().putBoolean("invite_pret", true).apply()
        }
    }

    /** Prêter le téléphone : depuis Maison, tant qu'Android accepte un profil de plus. */
    val peutPreter: Boolean get() = profils && actif.estMaison && liste.size < Profils.maximum(contexte)

    fun ouvrirPret() {
        if (peutPreter) enPret = true
    }

    fun fermerPret() {
        enPret = false
    }

    /** Crée l'Espace Invité et y passe ; rend un message si Android refuse. */
    suspend fun preter(): String? {
        val id = withContext(Dispatchers.IO) { Invite.creer(contexte) } ?: return "Le téléphone n'a pas pu préparer l'Espace Invité."
        enPret = false
        passage = Espace(id, "Invité", CouleurEspace.BLEU, PaysageEspace.LAGUNE)
        return null
    }

    /** L'invité rend le téléphone : retour dans Maison (Android demande le code), l'Espace Invité s'efface. */
    fun rendre() {
        passage = MAISON
    }

    /**
     * On peut créer un Espace : depuis Maison seulement (Android réserve la création de profils au
     * propriétaire du téléphone), et tant qu'Android en accepte encore.
     */
    val peutCreer: Boolean get() = profils && actif.estMaison && liste.size < Profils.maximum(contexte)

    fun voisin(sens: Int): Espace? = liste.getOrNull(liste.indexOfFirst { it.id == actif.id } + sens)

    /** Aller dans un Espace : l'écran de passage s'affiche, puis le téléphone change de profil. */
    fun aller(e: Espace) {
        if (!profils || e.id == actif.id) return
        passage = e
    }

    /** Appelé par l'écran de passage, une fois son animation jouée. */
    fun basculer(e: Espace) {
        if (!Profils.basculer(contexte, e.id)) passage = null
    }

    /** L'Accueil n'est plus à l'écran : l'écran de passage a fait son travail. */
    fun finPassage() {
        passage = null
    }

    fun ouvrirCreation() {
        if (peutCreer) enCreation = true
    }

    fun fermerCreation() {
        enCreation = false
    }

    /** Ce qui empêche d'utiliser ce nom, ou null s'il convient. [pour] : l'Espace qu'on renomme, s'il y en a un. */
    fun probleme(nom: String, pour: Espace? = null): String? {
        val n = nom.trim()
        return when {
            n.isEmpty() -> "Donnez un nom à l'Espace."
            n.length > 24 -> "Un nom plus court, s'il vous plaît (24 lettres au plus)."
            liste.any { it.id != pour?.id && it.nom.equals(n, ignoreCase = true) } -> "Un Espace porte déjà ce nom."
            else -> null
        }
    }

    /** Renomme un Espace (pas Maison). Rend un message si le nom ne convient pas. */
    fun renommer(e: Espace, nom: String): String? {
        if (e.estMaison || nom.trim() == e.nom) return null
        probleme(nom, pour = e)?.let { return it }
        if (!Profils.renommer(contexte, e.id, nom.trim())) return "Le nom n'a pas pu être changé."
        rafraichir()
        return null
    }

    /** Change la couleur ou le paysage d'un Espace : l'Accueil le reprend aussitôt. */
    fun apparence(e: Espace, teinte: CouleurEspace, paysage: PaysageEspace) {
        Profils.ecrireEspace(contexte, e.id, teinte, paysage)
        rafraichir()
    }

    /** Change le niveau de verrou d'un Espace ; le code se choisit ensuite dans l'Espace. */
    fun verrou(e: Espace, verrou: VerrouEspace) {
        Profils.ecrireEspace(contexte, e.id, e.teinte, e.paysage, verrou)
        rafraichir()
    }

    /** Supprime un Espace et tout ce qu'il contient, depuis Maison seulement. */
    suspend fun supprimer(e: Espace): Boolean {
        if (e.estMaison || !actif.estMaison) return false
        val fait = withContext(Dispatchers.IO) { Profils.supprimer(contexte, e.id) }
        rafraichir()
        return fait
    }

    /** Crée l'Espace et y passe. Rend un message si Android a refusé. */
    suspend fun creer(
        nom: String,
        teinte: CouleurEspace,
        paysage: PaysageEspace,
        verrou: VerrouEspace,
        applis: Set<String>,
        offertes: Set<String>,
    ): String? {
        val id = withContext(Dispatchers.IO) { Profils.creer(contexte, nom.trim()) }
            ?: return "L'Espace n'a pas pu être créé. Le téléphone a peut-être atteint son nombre d'Espaces."
        // Les applis de départ seront posées par l'Accueil du nouvel Espace, à son premier démarrage.
        Profils.ecrireEspace(contexte, id, teinte, paysage, verrou, applis, offertes)
        rafraichir()
        enCreation = false
        liste.firstOrNull { it.id == id }?.let { aller(it) }
        return null
    }

    /** Augmente quand les applis de l'Espace changent : l'Accueil les relit. */
    var versionApplis by mutableStateOf(0)
        private set

    /** Dans un Espace tout juste créé : pose ses applis de départ, une seule fois. */
    fun poserApplisDeDepart() {
        if (!profils || actif.estMaison) return
        // Avant le déverrouillage, Android ne montre pas encore les applis : on attendra ce moment.
        if (contexte.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return
        Profils.applisDeDepart(contexte, actif.id)?.let { (choisies, offertes) ->
            Profils.reglerApplis(contexte, choisies, Profils.essentielles(contexte), seulement = offertes)
            Profils.applisDeDepartPosees(contexte, actif.id)
            versionApplis++
        }
        // Les applis envoyées depuis Maison (« Vers un Espace ») arrivent à l'entrée dans l'Espace.
        if (Profils.poserApplisEnvoyees(contexte, actif.id)) versionApplis++
    }

    /** Les applis de l'Espace où l'on se trouve, choisies dans ses réglages. */
    suspend fun reglerApplis(choisies: Set<String>) {
        withContext(Dispatchers.IO) { Profils.reglerApplis(contexte, choisies, Profils.essentielles(contexte)) }
        versionApplis++
    }

    /** « Plus tard » : on ne propose plus de code dans cet Espace (les Paramètres d'Android le permettent toujours). */
    fun plusTard() {
        prefs.edit { putBoolean("plus_tard", true) }
        sansCode = false
    }

    /** « Verrouiller tout » dans le Pouls : retour à Maison ; les autres Espaces redemanderont leur code. */
    fun verrouillerTout() {
        if (!actif.estMaison) liste.firstOrNull { it.estMaison }?.let { aller(it) }
    }
}
