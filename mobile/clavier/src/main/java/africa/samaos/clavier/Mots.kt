package africa.samaos.clavier

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/**
 * Les mots du clavier : des mots courants du français, avec leurs accents, et ceux que la personne tape. Les mots
 * appris restent dans le téléphone (le clavier n'a pas accès à Internet) et ne viennent jamais d'un champ secret.
 */
class Mots(private val c: Context) {
    private val fichier get() = File(c.filesDir, "mots.json")
    private val appris = HashMap<String, Int>()
    private var change = false

    fun charger() {
        appris.clear()
        try {
            val o = JSONObject(fichier.readText())
            o.keys().forEach { appris[it] = o.getInt(it) }
        } catch (_: Exception) {
        }
    }

    fun garder() {
        if (!change) return
        change = false
        try {
            fichier.writeText(JSONObject(appris as Map<*, *>).toString())
        } catch (_: Exception) {
        }
    }

    fun nombre() = appris.size

    fun effacer() {
        appris.clear()
        fichier.delete()
    }

    /** Retenir un mot tapé (deux lettres au moins, sans chiffre). */
    fun apprendre(mot: String) {
        val m = mot.trim('\'', '’', '-')
        if (m.length < 2 || m.length > 30 || m.any { it.isDigit() }) return
        // Les mots du début de phrase gardent leur forme courante (« Bonjour » s'apprend « bonjour »).
        val cle = if (m.drop(1).any { it.isUpperCase() }) m else m.lowercase()
        appris[cle] = (appris[cle] ?: 0) + 1
        change = true
        if (appris.size > 5000) appris.entries.sortedBy { it.value }.take(500).forEach { appris.remove(it.key) }
    }

    /** Trois propositions au plus pour le début de mot [debut] : les mots appris d'abord, puis les mots courants. */
    fun propositions(debut: String, langue: Langue): List<String> {
        if (debut.isEmpty()) return emptyList()
        val d = sansAccents(debut.lowercase())
        val courants = if (langue == Langue.FR) COURANTS else emptyList()
        val vus = linkedSetOf<String>()
        appris.entries.filter { sansAccents(it.key.lowercase()).startsWith(d) }.sortedByDescending { it.value }.forEach { vus += it.key }
        courants.filter { sansAccents(it).startsWith(d) }.forEach { vus += it }
        // Le mot tapé tel quel n'est pas une proposition, sauf s'il prend des accents.
        val l = vus.filter { !it.equals(debut, ignoreCase = false) }
        // La majuscule du début est gardée.
        return l.take(3).map { if (debut.first().isUpperCase()) it.replaceFirstChar { ch -> ch.uppercase() } else it }
    }

    /**
     * La correction automatique, prudente : seulement les accents oubliés d'un mot courant (« tres » → « très »),
     * et jamais quand le mot sans accent existe aussi (« a » et « à », « ou » et « où »).
     */
    fun corriger(mot: String, langue: Langue): String? {
        if (langue != Langue.FR || mot.length < 2) return null
        val bas = mot.lowercase()
        if (bas in COURANTS_SET || appris.containsKey(bas)) return null
        val juste = ACCENTS[bas] ?: return null
        return if (mot.first().isUpperCase()) juste.replaceFirstChar { it.uppercase() } else juste
    }

    companion object {
        fun sansAccents(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace('œ', 'o').replace('æ', 'a')

        /** Des mots courants du quotidien (liste de Sama), avec leurs accents. */
        val COURANTS = (
            "bonjour bonsoir merci beaucoup demain aujourd'hui hier maintenant toujours jamais encore aussi déjà très trop bien bon bonne " +
                "oui non peut-être voilà voici ça comment pourquoi quand combien où là-bas ici avec pour dans sans sous sur chez entre après avant " +
                "pendant depuis alors donc mais parce que quelque chose rien tout tous toute toutes chaque même autre autres " +
                "être avoir faire aller venir voir savoir pouvoir vouloir devoir dire prendre donner mettre partir arriver rentrer sortir " +
                "appeler envoyer recevoir payer acheter vendre attendre chercher trouver parler répondre écrire lire manger dormir travailler " +
                "commencer finir préparer réparer passer rester revenir comprendre apprendre connaître croire penser aimer préférer espérer " +
                "est était été sont sera serait fait faite allé allée venu venue vu pris donné reçu envoyé payé appelé arrivé parti rentré " +
                "je tu il elle nous vous ils elles on mon ma mes ton ta tes son sa ses notre nos votre vos leur leurs moi toi lui eux " +
                "maman papa frère sœur enfant enfants bébé famille ami amie amis copain voisin voisine monsieur madame mademoiselle tonton tantie " +
                "maison quartier village ville marché boutique école lycée collège université hôpital pharmacie église mosquée bureau gare " +
                "travail argent crédit solde transfert forfait téléphone message numéro réseau batterie chargeur photo vidéo " +
                "matin midi après-midi soir nuit journée semaine mois année heure minute lundi mardi mercredi jeudi vendredi samedi dimanche " +
                "janvier février mars avril mai juin juillet août septembre octobre novembre décembre " +
                "taxi gbaka woro-woro moto voiture route pluie soleil chaleur saison " +
                "garba attiéké alloco foutou placali kédjénou riz poisson poulet viande sauce pain café thé eau " +
                "problème question réponse idée nouvelle nouvelles santé fête mariage baptême funérailles réunion rendez-vous " +
                "désolé désolée félicitations courage doucement vite lentement ensemble seulement vraiment également surtout " +
                "premier première dernier dernière prochain prochaine petit petite grand grande nouveau nouvelle vieux vieille " +
                "s'il plaît accord d'accord bienvenue bonne-nuit"
            ).split(' ').filter { it.isNotBlank() }.distinct()

        private val COURANTS_SET = COURANTS.toSet()

        /** « tres » → « très » : seulement pour les mots dont la forme sans accent n'existe pas. */
        private val AMBIGUS = setOf("a", "ou", "la", "du", "sur", "des", "peche", "marche", "eleve", "mais", "est", "cote", "pres")
        private val ACCENTS: Map<String, String> = COURANTS.filter { sansAccents(it) != it }
            .groupBy { sansAccents(it) }
            .filter { (k, l) -> l.size == 1 && k !in AMBIGUS && k !in COURANTS_SET }
            .mapValues { it.value.first() }
    }
}
