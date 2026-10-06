package africa.samaos.mail

import android.content.Context
import android.text.Html
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.mail.imap.IMAPFolder
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Date
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.activation.DataHandler
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import javax.mail.util.ByteArrayDataSource
import javax.net.ssl.SSLException

/**
 * Le courrier : relever (IMAP), lire, ranger, envoyer (SMTP). Sobre en data : on relève les en-têtes et seulement
 * les parties texte des mails ; une pièce jointe ne se télécharge que quand on la demande.
 */
object Courrier {
    /** Change à chaque modification de la base : les écrans se relisent. */
    var version by mutableIntStateOf(0)
        private set

    fun changer() {
        version++
    }

    /** Les comptes en cours de relève. */
    val releves = mutableStateMapOf<String, Boolean>()

    /** La dernière erreur de relève, par compte (null : tout va bien). */
    val erreurs = mutableStateMapOf<String, String>()

    /** Ce qui se passe pour les mails écrits ici (par brouillon) : « envoi », « envoye », « attente », « echec ». */
    val envois = mutableStateMapOf<Long, String>()

    var dernierMessage by mutableStateOf<String?>(null)

    private val magasins = ConcurrentHashMap<String, Store>()
    private val verrous = ConcurrentHashMap<String, Any>()
    private fun verrou(compte: Compte) = verrous.getOrPut(compte.id) { Any() }

    private fun protocoleImap(c: Compte) = if (c.imap.securite == Securite.SSL) "imaps" else "imap"
    private fun protocoleSmtp(c: Compte) = if (c.smtp.securite == Securite.SSL) "smtps" else "smtp"

    private fun session(compte: Compte): Session {
        val p = Properties()
        fun regler(proto: String, s: Serveur) {
            p["mail.$proto.host"] = s.hote
            p["mail.$proto.port"] = s.port.toString()
            p["mail.$proto.connectiontimeout"] = "15000"
            p["mail.$proto.timeout"] = "30000"
            p["mail.$proto.writetimeout"] = "30000"
            // Le certificat doit être valable ET au nom du serveur.
            p["mail.$proto.ssl.checkserveridentity"] = "true"
            if (s.securite == Securite.STARTTLS) {
                p["mail.$proto.starttls.enable"] = "true"
                p["mail.$proto.starttls.required"] = "true"
            }
        }
        regler(protocoleImap(compte), compte.imap)
        regler(protocoleSmtp(compte), compte.smtp)
        p["mail.${protocoleImap(compte)}.peek"] = "true"
        p["mail.${protocoleSmtp(compte)}.auth"] = "true"
        p["mail.mime.charset"] = "UTF-8"
        return Session.getInstance(p)
    }

    private fun magasin(c: Context, compte: Compte): Store {
        magasins[compte.id]?.takeIf { it.isConnected }?.let { return it }
        val mdp = Comptes.motDePasse(c, compte) ?: throw AuthenticationFailedException("mot de passe")
        val s = session(compte).getStore(protocoleImap(compte))
        s.connect(compte.imap.hote, compte.imap.port, compte.identifiant, mdp)
        magasins[compte.id] = s
        return s
    }

    fun deconnecter(compte: Compte) {
        magasins.remove(compte.id)?.let {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Une erreur dite simplement. */
    fun message(e: Throwable): String {
        var x: Throwable? = e
        while (x != null) {
            when (x) {
                is AuthenticationFailedException -> return "L'adresse ou le mot de passe ne sont pas acceptés par le serveur."
                is UnknownHostException -> return "Pas de connexion, ou serveur introuvable."
                is ConnectException, is SocketTimeoutException -> return "Le serveur ne répond pas. Vérifiez la connexion."
                is SSLException -> return "La connexion n'est pas sûre (certificat non valable) : Mail refuse de continuer, pour vous protéger."
            }
            x = x.cause
        }
        return "Le serveur a refusé : ${e.message?.take(120) ?: "erreur inconnue"}"
    }

    private fun reseau(e: Throwable): Boolean {
        var x: Throwable? = e
        while (x != null) {
            if (x is UnknownHostException || x is ConnectException || x is SocketTimeoutException || x is IOException && x !is SSLException) return true
            x = x.cause
        }
        return false
    }

    /** Essayer une boîte avant de l'ajouter : IMAP pour lire, SMTP pour envoyer. Null si tout marche. */
    fun tester(compte: Compte, motDePasse: String): String? {
        listOf(compte.imap, compte.smtp).forEach { s ->
            if (s.securite == Securite.AUCUNE && !Comptes.sansChiffrementPermis(s.hote)) {
                return "Sans chiffrement, le mot de passe passerait en clair sur Internet : choisissez SSL/TLS ou STARTTLS."
            }
        }
        val session = session(compte)
        return try {
            session.getStore(protocoleImap(compte)).use { it.connect(compte.imap.hote, compte.imap.port, compte.identifiant, motDePasse) }
            session.getTransport(protocoleSmtp(compte)).use { it.connect(compte.smtp.hote, compte.smtp.port, compte.identifiant, motDePasse) }
            null
        } catch (e: Exception) {
            message(e)
        }
    }

    // ——— Dossiers ———

    private fun role(f: IMAPFolder): String {
        val attributs = try {
            f.attributes.map { it.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
        val n = f.fullName.lowercase()
        return when {
            n == "inbox" -> "reception"
            "\\sent" in attributs || n.endsWith("sent") || "sent mail" in n || "sent items" in n || "envoy" in n -> "envoyes"
            "\\drafts" in attributs || "draft" in n || "brouillon" in n -> "brouillons"
            "\\trash" in attributs || "trash" in n || "corbeille" in n || "deleted" in n -> "corbeille"
            "\\junk" in attributs || "spam" in n || "junk" in n || "indésirable" in n || "indesirable" in n -> "indesirables"
            "\\archive" in attributs || "\\all" in attributs || "archive" in n -> "archives"
            else -> ""
        }
    }

    fun nomLisible(d: Dossier): String = when (d.role) {
        "reception" -> "Boîte de réception"
        "envoyes" -> "Envoyés"
        "brouillons" -> "Brouillons"
        "corbeille" -> "Corbeille"
        "indesirables" -> "Indésirables"
        "archives" -> "Archives"
        else -> d.nom.substringAfterLast('/').substringAfterLast('.')
    }

    private fun listerDossiers(c: Context, compte: Compte, s: Store) {
        val l = s.defaultFolder.list("*").filterIsInstance<IMAPFolder>().filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }
        val anciens = Base.dossiers(c, compte.id).associateBy { it.nom }
        // Un seul dossier par rôle : le premier trouvé ; les autres restent des dossiers ordinaires.
        val pris = mutableSetOf<String>()
        val dossiers = l.map { f ->
            val r = role(f).takeIf { it.isEmpty() || pris.add(it) } ?: ""
            Dossier(compte.id, f.fullName, r, anciens[f.fullName]?.nonLus ?: 0)
        }
        Base.garderDossiers(c, compte.id, dossiers)
    }

    // ——— Relever ———

    /** Relever un dossier : les [nombre] derniers mails, et le texte de ceux qu'on n'a pas encore. Renvoie les nouveaux. */
    fun relever(c: Context, compte: Compte, dossier: String = "INBOX", nombre: Int = 60, avecCorps: Boolean = true): List<Lettre> {
        releves[compte.id] = true
        try {
            synchronized(verrou(compte)) {
                val s = try {
                    magasin(c, compte)
                } catch (e: Exception) {
                    deconnecter(compte)
                    throw e
                }
                if (dossier == "INBOX" || Base.dossiers(c, compte.id).isEmpty()) listerDossiers(c, compte, s)
                val f = s.getFolder(dossier) as IMAPFolder
                f.open(Folder.READ_ONLY)
                try {
                    // Les numéros (UID) ne valent que pour une « validité » donnée : si le serveur l'a changée,
                    // il a renuméroté ses mails, et ce que le téléphone garde ne correspond plus.
                    val validites = c.getSharedPreferences("validites", Context.MODE_PRIVATE)
                    val cle = "${compte.id}/$dossier"
                    val validite = f.uidValidity
                    if (validites.getLong(cle, validite) != validite) Base.viderDossier(c, compte.id, dossier)
                    validites.edit().putLong(cle, validite).apply()
                    val total = f.messageCount
                    if (total == 0) {
                        Base.retirerAbsents(c, compte.id, dossier, 0, emptySet())
                        Base.reglerNonLus(c, compte.id, dossier, 0)
                        changer()
                        return emptyList()
                    }
                    val msgs = f.getMessages(maxOf(1, total - nombre + 1), total)
                    val fp = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add(FetchProfile.Item.FLAGS)
                        add(FetchProfile.Item.CONTENT_INFO)
                        add(FetchProfile.Item.SIZE)
                        add(UIDFolder.FetchProfileItem.UID)
                        add("References")
                    }
                    f.fetch(msgs, fp)
                    val connus = Base.uids(c, compte.id, dossier)
                    val lettres = msgs.map { entete(compte, dossier, f.getUID(it), it) }
                    Base.garderEntetes(c, lettres)
                    Base.retirerAbsents(c, compte.id, dossier, lettres.minOf { it.uid }, lettres.map { it.uid }.toSet())
                    Base.reglerNonLus(c, compte.id, dossier, Base.nonLus(c, compte.id, dossier))
                    erreurs.remove(compte.id)
                    changer()
                    if (avecCorps) {
                        // Du plus récent au plus ancien : ce qu'on va lire d'abord arrive d'abord.
                        msgs.reversed().forEach { m ->
                            val uid = f.getUID(m)
                            if (Base.lettre(c, compte.id, dossier, uid)?.charge == false) {
                                try {
                                    garderCorps(c, compte, dossier, uid, m)
                                } catch (_: Exception) {
                                }
                            }
                        }
                        changer()
                    }
                    return lettres.filter { it.uid !in connus }
                } finally {
                    try {
                        f.close(false)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            erreurs[compte.id] = message(e)
            throw e
        } finally {
            releves.remove(compte.id)
        }
    }

    private fun adresses(l: Array<javax.mail.Address>?): String =
        l.orEmpty().joinToString(", ") { a -> (a as? InternetAddress)?.let { if (it.personal.isNullOrBlank()) it.address else "${it.personal} <${it.address}>" } ?: a.toString() }

    private fun entete(compte: Compte, dossier: String, uid: Long, m: Message): Lettre {
        val de = m.from?.firstOrNull() as? InternetAddress
        return Lettre(
            compte = compte.id, dossier = dossier, uid = uid,
            de = de?.address.orEmpty(), deNom = de?.personal.orEmpty(),
            a = adresses(m.getRecipients(Message.RecipientType.TO)), cc = adresses(m.getRecipients(Message.RecipientType.CC)),
            sujet = m.subject.orEmpty(), date = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
            lu = m.flags.contains(Flags.Flag.SEEN), etoile = m.flags.contains(Flags.Flag.FLAGGED),
            pj = m.isMimeType("multipart/mixed"), extrait = "", taille = m.size.toLong(),
            idMessage = (m as? MimeMessage)?.messageID, references = m.getHeader("References")?.firstOrNull(),
        )
    }

    private class Contenu {
        var texte: String? = null
        var html: String? = null
        val pieces = mutableListOf<Piece>()
    }

    private fun lireTexte(p: Part): String? {
        if (p.size > 3_000_000) return null
        return try {
            when (val x = p.content) {
                is String -> x
                is java.io.InputStream -> x.use { it.readBytes().decodeToString() }
                else -> null
            }
        } catch (_: java.io.UnsupportedEncodingException) {
            p.inputStream.use { it.readBytes().decodeToString() }
        }
    }

    /** Parcourir un mail : le texte et le HTML pour lire, les pièces jointes pour plus tard (sans les télécharger). */
    private fun parcourir(p: Part, chemin: List<Int>, x: Contenu) {
        val disposition = p.disposition?.lowercase()
        val nom = try {
            p.fileName?.let { MimeUtility.decodeText(it) }
        } catch (_: Exception) {
            p.fileName
        }
        when {
            p.isMimeType("multipart/*") -> {
                val mp = p.content as Multipart
                for (i in 0 until mp.count) parcourir(mp.getBodyPart(i), chemin + (i + 1), x)
            }
            p.isMimeType("message/rfc822") -> x.pieces += Piece(x.pieces.size, nom ?: "Mail joint.eml", "message/rfc822", p.size.toLong(), chemin.joinToString("."))
            disposition == Part.ATTACHMENT || nom != null -> {
                // Une image citée dans le HTML (« inline », sans nom) n'est pas une pièce jointe à montrer.
                if (nom != null || disposition == Part.ATTACHMENT) {
                    val type = p.contentType.substringBefore(';').trim().lowercase()
                    // La taille annoncée est celle du texte encodé (base64) : un peu plus grosse que le fichier.
                    val taille = if (p.size > 0) (p.size * 3L / 4) else 0L
                    x.pieces += Piece(x.pieces.size, nom ?: "Pièce jointe", type, taille, chemin.joinToString("."))
                }
            }
            p.isMimeType("text/plain") -> if (x.texte == null) x.texte = lireTexte(p)
            p.isMimeType("text/html") -> if (x.html == null) x.html = lireTexte(p)
        }
    }

    /** Le texte d'un HTML, pour l'extrait et la recherche. */
    fun texteDe(html: String): String {
        val sans = html.replace(Regex("(?is)<(style|script|head)[^>]*>.*?</\\1>"), " ")
        return Html.fromHtml(sans, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun extrait(texte: String?, html: String?): String {
        val t = texte ?: html?.let { texteDe(it) } ?: return ""
        // Sans les citations (« > … ») ni la signature, l'extrait dit ce qui est nouveau.
        return t.lines().filter { !it.trimStart().startsWith(">") }.joinToString(" ")
            .substringBefore("\n-- ").replace(Regex("[\\s\\u00a0\\u200c]+"), " ").trim().take(200)
    }

    private fun garderCorps(c: Context, compte: Compte, dossier: String, uid: Long, m: Message) {
        val x = Contenu()
        parcourir(m, emptyList(), x)
        Base.garderCorps(c, compte.id, dossier, uid, x.texte, x.html, extrait(x.texte, x.html), x.pieces)
    }

    private fun <T> avecDossier(c: Context, compte: Compte, dossier: String, mode: Int, faire: (IMAPFolder) -> T): T = synchronized(verrou(compte)) {
        val f = try {
            magasin(c, compte).getFolder(dossier) as IMAPFolder
        } catch (e: Exception) {
            deconnecter(compte)
            throw e
        }
        f.open(mode)
        try {
            faire(f)
        } finally {
            try {
                f.close(mode == Folder.READ_WRITE)
            } catch (_: Exception) {
            }
        }
    }

    /** Le texte d'un mail qu'on ouvre, s'il n'a pas encore été relevé. */
    fun chargerCorps(c: Context, compte: Compte, dossier: String, uid: Long) = avecDossier(c, compte, dossier, Folder.READ_ONLY) { f ->
        val m = f.getMessageByUID(uid) ?: throw MessagingException("Ce mail n'est plus sur le serveur.")
        garderCorps(c, compte, dossier, uid, m)
        changer()
    }

    /** Télécharger une pièce jointe (à la demande seulement), dans le cache de Mail. */
    fun telecharger(c: Context, compte: Compte, dossier: String, uid: Long, p: Piece): File = avecDossier(c, compte, dossier, Folder.READ_ONLY) { f ->
        val m = f.getMessageByUID(uid) ?: throw MessagingException("Ce mail n'est plus sur le serveur.")
        var part: Part = m
        if (p.chemin.isNotEmpty()) p.chemin.split('.').map { it.toInt() }.forEach { i -> part = (part.content as Multipart).getBodyPart(i - 1) }
        val dossierLocal = File(c.cacheDir, "pieces/${Integer.toHexString(compte.id.hashCode())}-$uid-${p.n}").apply { mkdirs() }
        val fichier = File(dossierLocal, nomSur(p.nom))
        if (!fichier.exists() || fichier.length() == 0L) {
            part.inputStream.use { entree -> fichier.outputStream().use { entree.copyTo(it) } }
        }
        fichier
    }

    fun nomSur(nom: String) = nom.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().take(120).ifBlank { "piece" }

    fun marquer(c: Context, compte: Compte, dossier: String, uid: Long, lu: Boolean? = null, etoile: Boolean? = null) {
        Base.marquer(c, compte.id, dossier, uid, lu, etoile)
        Base.reglerNonLus(c, compte.id, dossier, Base.nonLus(c, compte.id, dossier))
        changer()
        avecDossier(c, compte, dossier, Folder.READ_WRITE) { f ->
            val m = f.getMessageByUID(uid) ?: return@avecDossier
            lu?.let { m.setFlag(Flags.Flag.SEEN, it) }
            etoile?.let { m.setFlag(Flags.Flag.FLAGGED, it) }
        }
    }

    private fun dossierOuCreer(s: Store, nom: String): Folder {
        val f = s.getFolder(nom)
        if (!f.exists()) f.create(Folder.HOLDS_MESSAGES)
        return f
    }

    /** Déplacer un mail (vers la corbeille, les archives…). Sans corbeille sur le serveur, on en crée une. */
    fun deplacer(c: Context, compte: Compte, dossier: String, uid: Long, role: String) {
        val cible = Base.dossier(c, compte.id, role)?.nom ?: if (role == "corbeille") "Trash" else "Archives"
        Base.retirer(c, compte.id, dossier, uid)
        changer()
        avecDossier(c, compte, dossier, Folder.READ_WRITE) { f ->
            val m = f.getMessageByUID(uid) ?: return@avecDossier
            if (cible != dossier) f.copyMessages(arrayOf(m), dossierOuCreer(f.store, cible))
            m.setFlag(Flags.Flag.DELETED, true)
            f.expunge()
        }
        if (Base.dossier(c, compte.id, role) == null) {
            synchronized(verrou(compte)) { listerDossiers(c, compte, magasin(c, compte)) }
            changer()
        }
    }

    /** Supprimer pour de bon (depuis la corbeille). */
    fun effacer(c: Context, compte: Compte, dossier: String, uid: Long) {
        Base.retirer(c, compte.id, dossier, uid)
        changer()
        avecDossier(c, compte, dossier, Folder.READ_WRITE) { f ->
            f.getMessageByUID(uid)?.setFlag(Flags.Flag.DELETED, true)
            f.expunge()
        }
    }

    // ——— Envoyer ———

    private fun construire(session: Session, compte: Compte, b: Brouillon): MimeMessage {
        val m = MimeMessage(session)
        m.setFrom(InternetAddress(compte.adresse, compte.nom.ifBlank { null }, "UTF-8"))
        m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(b.a, false))
        if (b.cc.isNotBlank()) m.setRecipients(Message.RecipientType.CC, InternetAddress.parse(b.cc, false))
        m.setSubject(b.sujet, "UTF-8")
        b.enReponseA?.let { m.setHeader("In-Reply-To", it) }
        listOfNotNull(b.references, b.enReponseA).joinToString(" ").ifBlank { null }?.let { m.setHeader("References", it) }
        val fichiers = b.pieces.map { File(it) }.filter { it.exists() }
        if (fichiers.isEmpty()) {
            m.setText(b.texte, "UTF-8")
        } else {
            val mp = MimeMultipart()
            mp.addBodyPart(MimeBodyPart().apply { setText(b.texte, "UTF-8") })
            fichiers.forEach { f ->
                val type = java.net.URLConnection.guessContentTypeFromName(f.name) ?: "application/octet-stream"
                mp.addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(ByteArrayDataSource(f.readBytes(), type))
                    fileName = MimeUtility.encodeText(f.name, "UTF-8", null)
                    disposition = Part.ATTACHMENT
                })
            }
            m.setContent(mp)
        }
        m.sentDate = Date()
        m.saveChanges()
        return m
    }

    /** Envoyer un mail écrit ici. Sans réseau, il attend et partira au prochain relevé. */
    fun envoyer(c: Context, b: Brouillon) {
        val compte = Comptes.de(c, b.compte) ?: return
        envois[b.id] = "envoi"
        try {
            val mdp = Comptes.motDePasse(c, compte) ?: throw AuthenticationFailedException("mot de passe")
            val session = session(compte)
            val m = construire(session, compte, b)
            session.getTransport(protocoleSmtp(compte)).use { t ->
                t.connect(compte.smtp.hote, compte.smtp.port, compte.identifiant, mdp)
                t.sendMessage(m, m.allRecipients)
            }
            Base.retirerBrouillon(c, b.id)
            b.pieces.forEach { File(it).parentFile?.deleteRecursively() }
            envois[b.id] = "envoye"
            dernierMessage = "Envoyé à ${b.a.substringBefore(',')}"
            // Ranger une copie dans « Envoyés » (Gmail le fait déjà lui-même).
            if (!compte.envoyesParLeServeur) {
                try {
                    synchronized(verrou(compte)) {
                        val nom = Base.dossier(c, compte.id, "envoyes")?.nom ?: "Sent"
                        val f = dossierOuCreer(magasin(c, compte), nom)
                        m.setFlag(Flags.Flag.SEEN, true)
                        f.appendMessages(arrayOf(m))
                    }
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            val attente = reseau(e)
            Base.garderBrouillon(c, Brouillon(b.id, b.compte, b.a, b.cc, b.sujet, b.texte, b.pieces, b.enReponseA, b.references, if (attente) "attente" else "echec", message(e), b.quand))
            envois[b.id] = if (attente) "attente" else "echec"
            dernierMessage = if (attente) "Pas de connexion : le mail partira dès que possible." else "Pas envoyé : ${message(e)}"
        }
        changer()
    }

    /** Les mails qui attendent le réseau. */
    fun envoyerEnAttente(c: Context) {
        Base.brouillons(c, etats = listOf("attente")).forEach { envoyer(c, it) }
    }
}
