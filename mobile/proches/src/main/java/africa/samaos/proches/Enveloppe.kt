package africa.samaos.proches

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Le chiffrement entre téléphones de proches (PROTOCOLE.md, version 2) : AES-256-GCM, une clé par sens et par
 * requête, tirée du secret partagé par les Réglages (Sugu ne voit jamais le secret). Le texte part en blocs de
 * même taille (16 Kio de contenu), numérotés, le dernier marqué : un bloc changé, retiré, déplacé ou ajouté fait
 * échouer la lecture, et la longueur ne se devine qu'à 16 Kio près.
 */
object Enveloppe {
    const val CONTENU = 16 * 1024
    /** Devant le contenu : 1 octet (dernier bloc ou non), 2 octets (longueur utile). */
    private const val TETE = 3
    private const val SCEAU = 16
    const val BLOC = TETE + CONTENU + SCEAU

    /** La taille chiffrée de [clair] octets. */
    fun taille(clair: Long): Long = maxOf(1L, (clair + CONTENU - 1) / CONTENU) * BLOC

    /** Le numéro du bloc fait le nonce : jamais deux fois le même avec une clé (une clé ne sert qu'une fois). */
    private fun aes(cle: ByteArray, mode: Int, numero: Long): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(cle, "AES"), GCMParameterSpec(SCEAU * 8, ByteBuffer.allocate(12).putInt(0).putLong(numero).array()))
    }

    /** Chiffre ce qu'on y écrit vers [sortie]. [close] écrit le dernier bloc, marqué, sans fermer [sortie]. */
    class Ecrivain(private val cle: ByteArray, private val sortie: OutputStream) : OutputStream() {
        private val tampon = ByteArray(CONTENU)
        private var rempli = 0
        private var numero = 0L
        private var ferme = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (ferme) throw IOException("Enveloppe déjà fermée")
            var i = off
            var reste = len
            while (reste > 0) {
                // Un bloc plein ne part qu'à l'arrivée de la suite : le dernier doit rester à marquer.
                if (rempli == CONTENU) bloc(false)
                val n = minOf(reste, CONTENU - rempli)
                System.arraycopy(b, i, tampon, rempli, n)
                rempli += n
                i += n
                reste -= n
            }
        }

        private fun bloc(dernier: Boolean) {
            val clair = ByteArray(TETE + CONTENU)
            clair[0] = if (dernier) 1 else 0
            clair[1] = (rempli shr 8).toByte()
            clair[2] = rempli.toByte()
            System.arraycopy(tampon, 0, clair, TETE, rempli)
            sortie.write(aes(cle, Cipher.ENCRYPT_MODE, numero++).doFinal(clair))
            rempli = 0
        }

        override fun flush() = sortie.flush()

        override fun close() {
            if (ferme) return
            ferme = true
            bloc(true)
            sortie.flush()
        }
    }

    /** Déchiffre [entree] ; ne rend un bloc qu'une fois son sceau vérifié. Une enveloppe sans dernier bloc est une erreur. */
    class Lecteur(private val cle: ByteArray, private val entree: InputStream) : InputStream() {
        private var clair = ByteArray(0)
        private var pos = 0
        private var fin = false
        private var numero = 0L

        private fun suivant(): Boolean {
            if (fin) return false
            val c = ByteArray(BLOC)
            var lu = 0
            while (lu < BLOC) {
                val n = entree.read(c, lu, BLOC - lu)
                if (n < 0) throw IOException("Échange coupé avant la fin")
                lu += n
            }
            val d = try {
                aes(cle, Cipher.DECRYPT_MODE, numero++).doFinal(c)
            } catch (x: GeneralSecurityException) {
                throw IOException("Bloc altéré ou mauvaise clé", x)
            }
            val n = ((d[1].toInt() and 0xff) shl 8) or (d[2].toInt() and 0xff)
            if (n > CONTENU || d[0].toInt() !in 0..1) throw IOException("Bloc illisible")
            fin = d[0].toInt() == 1
            clair = d.copyOfRange(TETE, TETE + n)
            pos = 0
            return true
        }

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos == clair.size) if (!suivant()) return -1
            val n = minOf(len, clair.size - pos)
            System.arraycopy(clair, pos, b, off, n)
            pos += n
            return n
        }
    }

    /** Une ligne (jusqu'au retour à la ligne), pour les en-têtes de la requête ou de la réponse chiffrées. */
    fun ligne(e: InputStream): String? {
        val b = java.io.ByteArrayOutputStream()
        while (true) {
            val x = e.read()
            if (x < 0) return if (b.size() == 0) null else b.toString(Charsets.UTF_8.name())
            if (x == '\n'.code) break
            if (x != '\r'.code) b.write(x)
            if (b.size() > 8192) throw IOException("Ligne trop longue")
        }
        return b.toString(Charsets.UTF_8.name())
    }
}
